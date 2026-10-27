package org.telegram.ui.web;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CommunitySettingsUi;
import org.telegram.utils.proxy.TorProxyController;
import java.io.ByteArrayInputStream;
import java.lang.ref.WeakReference;
import java.net.URI;

/** At most one interactive WebView, created only by a tap and destroyed on leaving the cell. */
public final class AmpInteractiveCell extends LinearLayout {
    private static WeakReference<AmpInteractiveCell> active = new WeakReference<>(null);
    private AmpInteractiveBlock block;
    private WebView web;
    private final TextView heading, action;
    public AmpInteractiveCell(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setPadding(AndroidUtilities.dp(18), AndroidUtilities.dp(12), AndroidUtilities.dp(18), AndroidUtilities.dp(12));
        heading = new TextView(context);
        heading.setTextSize(15);
        heading.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        addView(heading, new LayoutParams(-1, -2));
        action = new TextView(context);
        action.setTextSize(16);
        action.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
        CommunitySettingsUi.button(action);
        addView(action, new LayoutParams(-1, -2));
        action.setOnClickListener(v -> { if (web == null) load(); else release(); });
    }
    public void bind(AmpInteractiveBlock value) {
        if (block != value) release();
        block = value;
        heading.setText(value.label + "\n" + URI.create(value.url).getHost() + "\n" + LocaleController.getString(R.string.AmpInteractiveNotice));
        updateAction();
    }
    private void updateAction() {
        if (action == null) return;
        action.setText(LocaleController.getString(web == null ? R.string.AmpInteractiveLoad : R.string.AmpInteractiveClose));
    }
    private boolean routeAllowed() {
        // WebView cannot inherit our custom SOCKS/DoH transport. Never silently bypass it.
        return !TorProxyController.isEnabled() && !SharedConfig.isProxyEnabled()
                && org.telegram.utils.proxy.DnsConfig.selected().isEmpty();
    }
    @android.annotation.SuppressLint("SetJavaScriptEnabled")
    private void load() {
        if (block == null || !AmpEmbedPolicy.allows(block.url)) return;
        if (!routeAllowed()) {
            heading.setText(LocaleController.getString(R.string.AmpInteractiveRoute));
            return;
        }
        AmpInteractiveCell previous = active.get();
        if (previous != null && previous != this) previous.release();
        active = new WeakReference<>(this);
        try {
            web = new WebView(getContext());
            WebSettings settings = web.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(false);
            settings.setAllowFileAccess(false);
            settings.setAllowContentAccess(false);
            settings.setAllowFileAccessFromFileURLs(false);
            settings.setAllowUniversalAccessFromFileURLs(false);
            settings.setJavaScriptCanOpenWindowsAutomatically(false);
            settings.setSupportMultipleWindows(false);
            settings.setGeolocationEnabled(false);
            settings.setMediaPlaybackRequiresUserGesture(true);
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
            settings.setLoadWithOverviewMode(true);
            settings.setUseWideViewPort(true);
            CookieManager.getInstance().setAcceptThirdPartyCookies(web, false);
            web.setWebChromeClient(new WebChromeClient());
            web.setDownloadListener((u, agent, disposition, mime, length) -> { });
            web.setWebViewClient(new WebViewClient() {
                @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                    return !routeAllowed() || !AmpEmbedPolicy.allows(url);
                }
                @Override public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                    if (routeAllowed() && AmpEmbedPolicy.allows(url)) return null;
                    return new WebResourceResponse("text/plain", "UTF-8", new ByteArrayInputStream(new byte[0]));
                }
                @Override public void onReceivedError(WebView view, int code, String description, String url) {
                    heading.setText(LocaleController.getString(R.string.AmpInteractiveError));
                }
            });
            web.setOnTouchListener((v, event) -> {
                v.getParent().requestDisallowInterceptTouchEvent(event.getActionMasked() != MotionEvent.ACTION_UP && event.getActionMasked() != MotionEvent.ACTION_CANCEL);
                return false;
            });
            addView(web, new LayoutParams(-1, AndroidUtilities.dp(360)));
            web.loadUrl(block.url);
            updateAction();
        } catch (Exception ignored) {
            release();
            heading.setText(LocaleController.getString(R.string.AmpInteractiveError));
        }
    }
    private void release() {
        if (web != null) {
            web.stopLoading(); web.onPause(); removeView(web); web.destroy(); web = null;
        }
        if (active.get() == this) active.clear();
        updateAction();
    }
    @Override protected void onDetachedFromWindow() { release(); super.onDetachedFromWindow(); }
    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility != View.VISIBLE && action != null) release();
    }
    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (!focused) release();
    }
}
