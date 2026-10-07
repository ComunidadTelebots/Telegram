package org.telegram.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.telegram.messenger.*;
import org.telegram.ui.ActionBar.*;
import org.telegram.ui.Components.CommunitySettingsUi;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.skins.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Explicit transfer of four own settings only; never traverses SharedPreferences or account data. */
public final class CommunitySettingsBackupActivity extends BaseFragment {
    private static final int EXPORT = 8641, IMPORT = 8642;
    private static final ThreadPoolExecutor IO = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(1), r -> { Thread t = new Thread(r, "Own-settings-transfer"); t.setDaemon(true); return t; });
    private byte[] pendingExport;
    private boolean busy, destroyed;
    private TextView status, exportButton, importButton;
    private String text(int id) { return LocaleController.getString(id); }
    @Override public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back); actionBar.setTitle(text(R.string.TelebotsBackupTitle));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) { if (id == -1) finishFragment(); }
        });
        ScrollView scroll = new ScrollView(context); scroll.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        LinearLayout body = new LinearLayout(context); body.setOrientation(LinearLayout.VERTICAL); scroll.addView(body);
        fragmentView = scroll;
        label(body, text(R.string.TelebotsBackupScope));
        exportButton = button(body, text(R.string.TelebotsBackupExport), () -> {
            if (busy) return;
            try { preview(capture(), false); } catch (Exception error) { notice(false); }
        });
        importButton = button(body, text(R.string.TelebotsBackupImport), () -> {
            if (busy) return;
            Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
            launch(picker, IMPORT);
        });
        status = label(body, "");
        return scroll;
    }
    private TextView label(LinearLayout parent, String value) {
        TextView view = new TextView(parent.getContext()); view.setText(value); view.setTextSize(16);
        view.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        view.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(12), AndroidUtilities.dp(20), AndroidUtilities.dp(12));
        parent.addView(view, LayoutHelper.createLinear(-1, -2)); return view;
    }
    private TextView button(LinearLayout parent, String value, Runnable click) {
        TextView view = label(parent, value); CommunitySettingsUi.button(view);
        view.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
        view.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 2));
        view.setOnClickListener(v -> click.run()); return view;
    }
    private CommunitySettingsBackup.Values capture() {
        android.content.SharedPreferences p = MessagesController.getGlobalMainSettings();
        SkinManager manager = SkinManager.get();
        return new CommunitySettingsBackup.Values(MotionController.getStyle().id,
                manager.hasSelectedSkin() ? manager.getActiveSkin().getId() : "keep_current",
                p.getBoolean("amp_unmetered_only", false), p.getInt("community_preload_mode", -1));
    }
    private Set<String> skins() {
        Set<String> result = new HashSet<>(); for (SkinBase skin : SkinManager.get().getSkins()) result.add(skin.getId()); return result;
    }
    private void preview(CommunitySettingsBackup.Values values, boolean importing) {
        if (destroyed || getParentActivity() == null) return;
        String name = text(R.string.TelebotsBackupKeepSkin);
        if (!"keep_current".equals(values.skin)) {
            name = null;
            for (SkinBase skin : SkinManager.get().getSkins()) if (skin.getId().equals(values.skin)) name = skin.getDisplayName() + " (" + skin.getId() + ")";
            if (name == null) { notice(false); return; }
        }
        int[] preloadLabels = {R.string.TelebotsBackupPreloadDefault, R.string.TelebotsBackupPreloadOff,
                R.string.TelebotsBackupPreloadUnmetered, R.string.TelebotsBackupPreloadNormal};
        int[] motionLabels = {R.string.SkinMotionOriginal, R.string.SkinMotionSmooth,
                R.string.SkinMotionQuick, R.string.SkinMotionFade, R.string.SkinMotionNone};
        String description = text(R.string.TelebotsBackupSkin) + ": " + name
                + "\n" + text(R.string.TelebotsBackupMotion) + ": " + text(motionLabels[MotionStyle.fromId(values.motion).ordinal()])
                + "\n" + text(R.string.TelebotsBackupAmp) + ": " + text(values.ampUnmetered ? R.string.TelebotsBackupYes : R.string.TelebotsBackupNo)
                + "\n" + text(R.string.TelebotsBackupPreload) + ": " + text(preloadLabels[values.preload + 1]);
        showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(text(R.string.TelebotsBackupPreview))
                .setMessage(description).setNegativeButton(text(R.string.Cancel), null)
                .setPositiveButton(text(importing ? R.string.TelebotsBackupApply : R.string.TelebotsBackupExport), (dialog, which) -> {
                    if (busy || destroyed) return;
                    if (importing) apply(values);
                    else {
                        pendingExport = CommunitySettingsBackup.encode(values);
                        launch(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("text/plain")
                                .putExtra(Intent.EXTRA_TITLE, "community.telebots-settings.txt"), EXPORT);
                    }
                }).create());
    }
    private void apply(CommunitySettingsBackup.Values values) {
        SkinBase selected = null;
        if (!"keep_current".equals(values.skin)) {
            for (SkinBase skin : SkinManager.get().getSkins()) if (skin.getId().equals(values.skin)) selected = skin;
            if (selected == null) { notice(false); return; }
        }
        // Official setters for own appearance; no replacement or import of arbitrary preference files.
        MessagesController.getGlobalMainSettings().edit().putBoolean("amp_unmetered_only", values.ampUnmetered)
                .putInt("community_preload_mode", values.preload).apply();
        MotionController.setStyle(MotionStyle.fromId(values.motion));
        if (selected != null) SkinManager.get().applySkin(selected);
        notice(true);
    }
    private void launch(Intent picker, int code) {
        busy = true; buttons();
        try { startActivityForResult(picker, code); }
        catch (Exception error) { busy = false; pendingExport = null; buttons(); notice(false); }
    }
    private void buttons() { if (exportButton != null) exportButton.setEnabled(!busy); if (importButton != null) importButton.setEnabled(!busy); }
    private void notice(boolean success) { if (!destroyed && status != null) status.setText(text(success ? R.string.TelebotsBackupDone : R.string.TelebotsBackupError)); }
    @Override public void onActivityResultFragment(int request, int result, Intent data) {
        super.onActivityResultFragment(request, result, data);
        if (request != EXPORT && request != IMPORT) return;
        final byte[] export = pendingExport; pendingExport = null;
        if (destroyed || result != Activity.RESULT_OK || data == null || data.getData() == null) { busy = false; buttons(); return; }
        final Uri uri = data.getData();
        if (!"content".equals(uri.getScheme()) || request == EXPORT && export == null) { busy = false; buttons(); notice(false); return; }
        final Set<String> allowedSkins = skins();
        try { IO.execute(() -> {
            CommunitySettingsBackup.Values loaded = null; boolean ok = false;
            try {
                if (request == IMPORT) {
                    try (InputStream stream = ApplicationLoader.applicationContext.getContentResolver().openInputStream(uri)) {
                        loaded = CommunitySettingsBackup.read(stream, allowedSkins); ok = true;
                    }
                } else {
                    try (OutputStream stream = ApplicationLoader.applicationContext.getContentResolver().openOutputStream(uri, "wt")) {
                        if (stream != null) { stream.write(export); stream.flush(); ok = true; }
                    }
                }
            } catch (Exception error) {}
            final CommunitySettingsBackup.Values values = loaded; final boolean success = ok;
            AndroidUtilities.runOnUIThread(() -> {
                if (destroyed) return;
                busy = false; buttons();
                if (success && request == IMPORT) preview(values, true); else notice(success);
            });
        }); } catch (RejectedExecutionException error) { busy = false; buttons(); notice(false); }
    }
    @Override public void onFragmentDestroy() { destroyed = true; pendingExport = null; super.onFragmentDestroy(); }
}
