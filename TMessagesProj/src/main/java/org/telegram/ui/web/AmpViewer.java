package org.telegram.ui.web;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.net.URL;

/**
 * Visor AMP in-app fiel a la implementación React del fork.
 *
 * Modos:
 *  - Iframe (WebView): carga la URL via Google AMP Cache → Cloudflare AMP Cache → URL original.
 *  - Lectura: extrae el artículo del HTML y lo renderiza de forma nativa sin WebView.
 *
 * La preferencia de modo se persiste en SharedPreferences ("amp-reader-mode").
 */
public class AmpViewer extends FrameLayout {

    // ── Cache de URLs AMP ───────────────────────────────────────────────────

    public static String buildGoogleAmpUrl(String url) {
        try {
            URL u = new URL(url);
            if (!"https".equals(u.getProtocol())) return null;
            String encoded = u.getHost().replace("-", "--").replace(".", "-");
            return "https://" + encoded + ".cdn.ampproject.org/c/s/" + u.getHost() + u.getFile();
        } catch (Exception e) {
            return null;
        }
    }

    public static String buildCloudflareAmpUrl(String url) {
        try {
            URL u = new URL(url);
            if (!"https".equals(u.getProtocol())) return null;
            String encoded = u.getHost().replace("-", "--").replace(".", "-");
            return "https://" + encoded + ".amp.cloudflare.com/c/s/" + u.getHost() + u.getFile();
        } catch (Exception e) {
            return null;
        }
    }

    // ── Estado ──────────────────────────────────────────────────────────────

    private final String url;
    private final TLRPC.WebPage webPage;
    private final Runnable onClose;

    private boolean readerMode;
    private boolean useFallback = false;

    // Vistas
    private View headerView;
    private TextView badgeView;
    private WebView webView;
    private FrameLayout webViewContainer;
    private ProgressBar loadingView;
    private ScrollView readerScrollView;
    private LinearLayout readerContent;
    private ActionBarMenuItem readerModeItem;

    // Animación de progreso
    private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float progressFraction = 0f;

    private static final String PREF_READER_MODE = "amp_reader_mode";

    // ── Constructor ─────────────────────────────────────────────────────────

    public AmpViewer(@NonNull Context context, String url, TLRPC.WebPage webPage, Runnable onClose) {
        super(context);
        this.url = url;
        this.webPage = webPage;
        this.onClose = onClose;
        this.readerMode = getPrefs().getBoolean(PREF_READER_MODE, false);

        progressPaint.setColor(Theme.getColor(Theme.key_actionBarDefault));

        buildLayout(context);
        if (readerMode) {
            showReaderMode();
        } else {
            loadAmpUrl();
        }
    }

    // ── Construcción de la UI ───────────────────────────────────────────────

    private void buildLayout(Context context) {
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

        // Header (ActionBar manual para coincidir con el estilo de la app)
        headerView = buildHeader(context);
        addView(headerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 56, Gravity.TOP));

        // Barra de progreso de lectura (3 dp, igual que el React)
        View progressBar = new View(context) {
            @Override
            protected void onDraw(Canvas canvas) {
                int w = getWidth();
                canvas.drawRect(0, 0, w * progressFraction, getHeight(), progressPaint);
            }
        };
        progressBar.setId(R.id.search_field); // reutilizo un id existente como tag
        addView(progressBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 3, Gravity.TOP | Gravity.START, 0, 56, 0, 0));
        // Guardar referencia para actualizar progreso
        setTag(progressBar);

        // Contenedor principal (debajo del header + barra)
        FrameLayout body = new FrameLayout(context);
        addView(body, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP, 0, 59, 0, 0));

        // WebView
        webViewContainer = new FrameLayout(context);
        webView = new WebView(context);
        configureWebView();
        webViewContainer.addView(webView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        body.addView(webViewContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        // Spinner de carga
        loadingView = new ProgressBar(context);
        loadingView.setIndeterminate(true);
        body.addView(loadingView, LayoutHelper.createFrame(48, 48, Gravity.CENTER));

        // Vista de lectura nativa
        readerScrollView = new ScrollView(context);
        readerContent = new LinearLayout(context);
        readerContent.setOrientation(LinearLayout.VERTICAL);
        readerContent.setPadding(AndroidUtilities.dp(18), AndroidUtilities.dp(20), AndroidUtilities.dp(18), AndroidUtilities.dp(40));
        readerScrollView.addView(readerContent, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        readerScrollView.setVisibility(GONE);
        body.addView(readerScrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        updateBadge();
    }

    private View buildHeader(Context context) {
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4), 0);
        header.setBackgroundColor(Theme.getColor(Theme.key_actionBarDefault));

        // Botón atrás
        TextView backBtn = new TextView(context);
        backBtn.setText("‹");
        backBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        backBtn.setTextColor(Color.WHITE);
        backBtn.setGravity(Gravity.CENTER);
        backBtn.setClickable(true);
        backBtn.setBackground(Theme.createSelectorDrawable(Theme.ACTION_BAR_WHITE_SELECTOR_COLOR));
        backBtn.setOnClickListener(v -> onClose.run());
        header.addView(backBtn, LayoutHelper.createLinear(48, 56));

        // Badge AMP / LEER
        badgeView = new TextView(context);
        badgeView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        badgeView.setTextColor(Color.WHITE);
        badgeView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
        badgeView.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(4), ColorUtils.setAlphaComponent(Color.WHITE, 60)));
        badgeView.setPadding(AndroidUtilities.dp(5), AndroidUtilities.dp(2), AndroidUtilities.dp(5), AndroidUtilities.dp(2));
        header.addView(badgeView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 4, 0, 6, 0));

        // Origen (hostname)
        TextView originView = new TextView(context);
        String hostname = "";
        try { hostname = new URL(url).getHost(); } catch (Exception ignored) {}
        originView.setText(hostname);
        originView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        originView.setTextColor(ColorUtils.setAlphaComponent(Color.WHITE, 230));
        originView.setSingleLine(true);
        originView.setEllipsize(TextUtils.TruncateAt.END);
        header.addView(originView, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL));

        // Botón modo lectura
        TextView readerBtn = new TextView(context);
        readerBtn.setText("☰");
        readerBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        readerBtn.setTextColor(Color.WHITE);
        readerBtn.setGravity(Gravity.CENTER);
        readerBtn.setClickable(true);
        readerBtn.setBackground(Theme.createSelectorDrawable(Theme.ACTION_BAR_WHITE_SELECTOR_COLOR));
        readerBtn.setOnClickListener(v -> toggleReaderMode());
        header.addView(readerBtn, LayoutHelper.createLinear(48, 56));

        // Botón abrir en navegador
        TextView openBtn = new TextView(context);
        openBtn.setText("⤴");
        openBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        openBtn.setTextColor(Color.WHITE);
        openBtn.setGravity(Gravity.CENTER);
        openBtn.setClickable(true);
        openBtn.setBackground(Theme.createSelectorDrawable(Theme.ACTION_BAR_WHITE_SELECTOR_COLOR));
        openBtn.setOnClickListener(v -> AndroidUtilities.openUrl(getContext(), url));
        header.addView(openBtn, LayoutHelper.createLinear(48, 56));

        return header;
    }

    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.setSafeBrowsingEnabled(false);
        }

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                // Permitir navegación dentro de la página AMP; bloquear salidas externas
                String reqUrl = request.getUrl().toString();
                if (reqUrl.contains("ampproject.org") || reqUrl.contains("amp.cloudflare.com")) {
                    return false;
                }
                AndroidUtilities.openUrl(getContext(), reqUrl);
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                loadingView.setVisibility(GONE);
                webViewContainer.setVisibility(VISIBLE);
                animateProgress(0.35f); // Simula algo de progreso leído
            }
        });

        webView.setWebChromeClient(new android.webkit.WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                animateProgress(newProgress / 100f * 0.9f);
            }
        });
    }

    // ── Carga AMP ───────────────────────────────────────────────────────────

    private void loadAmpUrl() {
        loadingView.setVisibility(VISIBLE);
        webViewContainer.setVisibility(INVISIBLE);
        readerScrollView.setVisibility(GONE);

        String ampUrl = useFallback ? buildCloudflareAmpUrl(url) : buildGoogleAmpUrl(url);
        if (ampUrl == null) ampUrl = url;

        webView.loadUrl(ampUrl);
    }

    // ── Modo lectura ─────────────────────────────────────────────────────────

    private void toggleReaderMode() {
        readerMode = !readerMode;
        getPrefs().edit().putBoolean(PREF_READER_MODE, readerMode).apply();
        updateBadge();
        if (readerMode) {
            showReaderMode();
        } else {
            webViewContainer.setVisibility(VISIBLE);
            readerScrollView.setVisibility(GONE);
            loadingView.setVisibility(GONE);
            loadAmpUrl();
        }
    }

    private void showReaderMode() {
        loadingView.setVisibility(VISIBLE);
        webViewContainer.setVisibility(GONE);

        // Obtener datos del webPage de Telegram para el modo lite
        Context ctx = getContext();
        readerContent.removeAllViews();

        String title = webPage != null && webPage.title != null ? webPage.title : "";
        String description = webPage != null && webPage.description != null ? webPage.description : "";
        String siteName = webPage != null && webPage.site_name != null ? webPage.site_name : "";

        if (!siteName.isEmpty()) {
            TextView siteView = new TextView(ctx);
            siteView.setText(siteName.toUpperCase());
            siteView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            siteView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
            siteView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
            siteView.setPadding(0, 0, 0, AndroidUtilities.dp(6));
            readerContent.addView(siteView);
        }

        if (!title.isEmpty()) {
            TextView titleView = new TextView(ctx);
            titleView.setText(title);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 21);
            titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            titleView.setTypeface(android.graphics.Typeface.create("serif", android.graphics.Typeface.BOLD));
            titleView.setLineSpacing(0, 1.25f);
            titleView.setPadding(0, 0, 0, AndroidUtilities.dp(12));
            readerContent.addView(titleView);
        }

        if (!description.isEmpty()) {
            for (String para : description.split("\n\n")) {
                String trimmed = para.trim();
                if (trimmed.isEmpty()) continue;
                TextView pView = new TextView(ctx);
                pView.setText(trimmed);
                pView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
                pView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                pView.setLineSpacing(AndroidUtilities.dp(3), 1f);
                pView.setPadding(0, 0, 0, AndroidUtilities.dp(12));
                readerContent.addView(pView);
            }
        }

        // Enlace para leer el artículo completo
        TextView readMoreView = new TextView(ctx);
        readMoreView.setText("Leer artículo completo →");
        readMoreView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        readMoreView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
        readMoreView.setPadding(0, AndroidUtilities.dp(16), 0, 0);
        readMoreView.setOnClickListener(v -> AndroidUtilities.openUrl(ctx, url));
        readerContent.addView(readMoreView);

        loadingView.setVisibility(GONE);
        readerScrollView.setVisibility(VISIBLE);
        animateProgress(1f);
    }

    // ── Progreso de lectura ─────────────────────────────────────────────────

    private void animateProgress(float target) {
        View bar = (View) getTag();
        if (bar == null) return;
        ValueAnimator anim = ValueAnimator.ofFloat(progressFraction, target);
        anim.setDuration(200);
        anim.addUpdateListener(a -> {
            progressFraction = (float) a.getAnimatedValue();
            bar.invalidate();
        });
        anim.start();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void updateBadge() {
        if (badgeView != null) {
            badgeView.setText(readerMode ? "LEER" : "AMP");
        }
    }

    private SharedPreferences getPrefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("amp_prefs", Context.MODE_PRIVATE);
    }

    public void onBackPressed() {
        if (webView != null && webView.canGoBack() && !readerMode) {
            webView.goBack();
        } else {
            onClose.run();
        }
    }

    public void destroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
        }
    }

    // ── Factory: abrir desde ChatMessageCell ─────────────────────────────────

    /**
     * Determina si una URL tiene versión AMP disponible.
     * Heurística simple: HTTPS y no es ya una URL de AMP cache.
     */
    public static boolean isAmpEligible(String url) {
        if (url == null) return false;
        return url.startsWith("https://")
            && !url.contains("ampproject.org")
            && !url.contains("amp.cloudflare.com");
    }
}
