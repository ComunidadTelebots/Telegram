package org.telegram.ui;

import android.content.Context;
import android.content.Intent;
import android.app.Activity;
import android.net.Uri;
import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ExecutorService;
import org.telegram.messenger.UpdateApkVerifier;
import org.telegram.ui.Components.CommunitySettingsUi;
import androidx.core.content.FileProvider;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.ReleaseAccess;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;

/** Checks our own release channel, never Telegram's upstream updater. */
public final class AppUpdatesActivity extends BaseFragment {
    private TextView status, check, download, details, verify, install;
    private static final int PICK_APK = 7251;
    private static final ExecutorService VERIFY = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(1), r -> { Thread t = new Thread(r, "APK-verifier"); t.setDaemon(true); return t; });
    private JSONObject release;
    private AtomicBoolean verificationCancelled;
    private File verifiedApk;
    private boolean verifying, installerOpened;
    private long installedCode;
    private boolean checking, destroyed;
    private String releaseUrl;
    private boolean fromPlay;

    private TextView text(Context context, String value, int size, int colorKey) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(getThemedColor(colorKey));
        view.setPadding(0, AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12));
        return view;
    }

    private TextView button(Context context, String value) {
        TextView view = text(context, value, 16, Theme.key_featuredStickers_buttonText);
        view.setGravity(Gravity.CENTER);
        view.setMinHeight(AndroidUtilities.dp(52));
        view.setTypeface(AndroidUtilities.bold());
        view.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(12), getThemedColor(Theme.key_featuredStickers_addButton)));
        CommunitySettingsUi.button(view);
        return view;
    }

    @Override public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(LocaleController.getString(R.string.TelebotsUpdates));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) { if (id == -1) finishFragment(); }
        });
        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = AndroidUtilities.dp(24);
        content.setPadding(padding, padding, padding, padding);
        scroll.addView(content);
        String version = "—";
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            installedCode = Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
            version = info.versionName + " (" + installedCode + ")";
            fromPlay = "com.android.vending".equals(context.getPackageManager().getInstallerPackageName(context.getPackageName()));
        } catch (Exception ignored) { }
        content.addView(text(context, LocaleController.getString(R.string.TelebotsInstalledVersion), 14, Theme.key_windowBackgroundWhiteGrayText));
        content.addView(text(context, version, 24, Theme.key_windowBackgroundWhiteBlackText));
        content.addView(text(context, LocaleController.formatString("TelebotsUpdateChannel", R.string.TelebotsUpdateChannel, ReleaseAccess.channel()), 16, Theme.key_windowBackgroundWhiteBlackText));
        status = text(context, LocaleController.getString(fromPlay ? R.string.TelebotsUpdatePlayInfo : R.string.TelebotsUpdateInfo), 15, Theme.key_windowBackgroundWhiteGrayText);
        content.addView(status);
        check = button(context, LocaleController.getString(fromPlay ? R.string.TelebotsUpdatePlay : R.string.TelebotsCheckUpdates));
        content.addView(check, new LinearLayout.LayoutParams(-1, -2));
        check.setOnClickListener(v -> {
            if (fromPlay) Browser.openUrl(context, "https://play.google.com/store/apps/details?id=" + context.getPackageName());
            else checkUpdate();
        });
        download = button(context, LocaleController.getString(R.string.TelebotsDownloadUpdate));
        LinearLayout.LayoutParams margins = new LinearLayout.LayoutParams(-1, -2);
        margins.topMargin = AndroidUtilities.dp(16);
        content.addView(download, margins);
        download.setVisibility(View.GONE);
        download.setOnClickListener(v -> { if (releaseUrl != null) Browser.openUrl(context, releaseUrl); });
        details = text(context, "", 15, Theme.key_windowBackgroundWhiteBlackText);
        content.addView(details);
        content.addView(text(context, LocaleController.getString(R.string.TelebotsUpdateVerificationInfo), 14, Theme.key_windowBackgroundWhiteGrayText));
        verify = button(context, LocaleController.getString(R.string.TelebotsUpdateSelectApk));
        content.addView(verify, new LinearLayout.LayoutParams(-1, -2));
        verify.setVisibility(View.GONE);
        verify.setOnClickListener(v -> {
            if (verifying || release == null) return;
            Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/vnd.android.package-archive");
            try { startActivityForResult(picker, PICK_APK); } catch (Exception ignored) { status.setText(LocaleController.getString(R.string.TelebotsUpdateError)); }
        });
        install = button(context, LocaleController.getString(R.string.TelebotsUpdateInstallVerified));
        LinearLayout.LayoutParams installMargins = new LinearLayout.LayoutParams(-1, -2); installMargins.topMargin = AndroidUtilities.dp(16);
        content.addView(install, installMargins); install.setVisibility(View.GONE);
        install.setOnClickListener(v -> {
            if (verifiedApk == null || !verifiedApk.isFile() || getParentActivity() == null) return;
            try {
                Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".provider", verifiedApk);
                Intent intent = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                getParentActivity().startActivity(intent); installerOpened = true;
            } catch (Exception ignored) { status.setText(LocaleController.getString(R.string.TelebotsUpdateError)); }
        });
        fragmentView = scroll;
        return fragmentView;
    }

    private void checkUpdate() {
        if (checking || verifying || destroyed) return;
        if (installedCode <= 0) { status.setText(LocaleController.getString(R.string.TelebotsUpdateError)); return; }
        checking = true;
        check.setEnabled(false);
        check.setAlpha(0.5f);
        download.setVisibility(View.GONE);
        releaseUrl = null; release = null; details.setText(""); verify.setVisibility(View.GONE); clearVerified();
        status.setText(LocaleController.getString(R.string.TelebotsUpdateChecking));
        ReleaseAccess.NETWORK.execute(() -> {
            JSONObject result = null;
            try { result = ReleaseAccess.checkAndroidUpdate(installedCode); } catch (Exception ignored) { }
            final JSONObject response = result;
            AndroidUtilities.runOnUIThread(() -> {
                if (destroyed) return;
                checking = false;
                check.setEnabled(true);
                check.setAlpha(1f);
                String state = response == null ? "error" : response.optString("status");
                if ("available".equals(state) && response.optLong("version_code") > installedCode) {
                    String url = response.optString("url");
                    // Only a concrete message in our release channels, not arbitrary downloads.
                    if (!url.matches("https://t\\.me/(?:c/[1-9][0-9]*|[A-Za-z][A-Za-z0-9_]{4,31})/[1-9][0-9]*")) {
                        status.setText(LocaleController.getString(R.string.TelebotsUpdateError));
                        return;
                    }
                    releaseUrl = url;
                    release = response;
                    showDetails(response);
                    boolean metadata = UpdateApkVerifier.hasIntegrityMetadata(response, getParentActivity().getPackageName());
                    verify.setVisibility(metadata && UpdateApkVerifier.compatible(response, Build.VERSION.SDK_INT, Build.SUPPORTED_ABIS) ? View.VISIBLE : View.GONE);
                    status.setText(LocaleController.formatString("TelebotsUpdateAvailable", R.string.TelebotsUpdateAvailable, response.optString("version_name")));
                    download.setVisibility(View.VISIBLE);
                } else if ("current".equals(state)) {
                    showDetails(response);
                    status.setText(LocaleController.getString(R.string.TelebotsUpdateCurrent));
                } else if ("unavailable".equals(state)) {
                    status.setText(LocaleController.getString(R.string.TelebotsUpdateUnavailable));
                } else status.setText(LocaleController.getString(R.string.TelebotsUpdateError));
            });
        });
    }

    private void showDetails(JSONObject response) {
        String unknown = LocaleController.getString(R.string.TelebotsUpdateMetadataUnknown);
        String size = response.optLong("size_bytes") > 0 ? AndroidUtilities.formatFileSize(response.optLong("size_bytes")) : unknown;
        String compatibility = response.optInt("min_sdk") > 0 && response.optJSONArray("abis") != null
                ? LocaleController.formatString("TelebotsUpdateCompatibility", R.string.TelebotsUpdateCompatibility, response.optInt("min_sdk"), response.optJSONArray("abis").length() == 0 ? LocaleController.getString(R.string.TelebotsUpdateAnyAbi) : response.optJSONArray("abis").toString()) : unknown;
        String notes = response.optString("release_notes", "").trim();
        if (notes.isEmpty()) notes = LocaleController.getString(R.string.TelebotsUpdateNotesUnknown);
        details.setText(LocaleController.formatString("TelebotsUpdateDetails", R.string.TelebotsUpdateDetails, ReleaseAccess.channel(), size, compatibility, notes));
        if (!UpdateApkVerifier.hasIntegrityMetadata(response, getParentActivity().getPackageName())) details.append("\n\n" + LocaleController.getString(R.string.TelebotsUpdateVerificationMissing));
    }
    private void clearVerified() {
        if (verifiedApk != null && !installerOpened) verifiedApk.delete();
        verifiedApk = null; installerOpened = false;
        if (install != null) install.setVisibility(View.GONE);
    }
    @Override public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        super.onActivityResultFragment(requestCode, resultCode, data);
        if (requestCode != PICK_APK || resultCode != Activity.RESULT_OK || data == null || data.getData() == null || release == null || verifying || destroyed) return;
        clearVerified(); verifying = true; verify.setEnabled(false); check.setEnabled(false);
        status.setText(LocaleController.getString(R.string.TelebotsUpdateVerifying));
        final AtomicBoolean cancellation = verificationCancelled = new AtomicBoolean();
        final JSONObject manifest = release;
        final Context context = getParentActivity().getApplicationContext();
        final Uri selected = data.getData();
        try { VERIFY.execute(() -> {
            UpdateApkVerifier.Result result = UpdateApkVerifier.verify(context, selected, manifest, cancellation);
            AndroidUtilities.runOnUIThread(() -> {
                if (destroyed || cancellation.get()) { if (result.apk != null) result.apk.delete(); return; }
                verifying = false; verify.setEnabled(true); check.setEnabled(true);
                if (result.apk != null) {
                    verifiedApk = result.apk;
                    status.setText(LocaleController.getString(R.string.TelebotsUpdateVerified));
                    install.setVisibility(View.VISIBLE);
                } else {
                    int message = R.string.TelebotsUpdateVerificationFailed;
                    if (result.failure == UpdateApkVerifier.Failure.SIGNATURE) message = R.string.TelebotsUpdateSignatureMismatch;
                    else if (result.failure == UpdateApkVerifier.Failure.HASH) message = R.string.TelebotsUpdateHashMismatch;
                    else if (result.failure == UpdateApkVerifier.Failure.PACKAGE) message = R.string.TelebotsUpdatePackageMismatch;
                    else if (result.failure == UpdateApkVerifier.Failure.COMPATIBILITY) message = R.string.TelebotsUpdateIncompatible;
                    status.setText(LocaleController.getString(message));
                }
            });
        }); } catch (RejectedExecutionException ignored) {
            verifying = false; verify.setEnabled(true); check.setEnabled(true);
            status.setText(LocaleController.getString(R.string.TelebotsUpdateVerificationFailed));
        }
    }
    @Override public void onFragmentDestroy() {
        destroyed = true;
        if (verificationCancelled != null) verificationCancelled.set(true);
        clearVerified();
        super.onFragmentDestroy();
    }
}
