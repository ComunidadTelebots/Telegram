package org.telegram.ui;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Button;
import org.json.JSONObject;
import org.telegram.messenger.ReleaseAccess;
import org.telegram.utils.proxy.TorProxyController;

/** Native entry panel. The account is verified in the trusted HTTPS browser flow. */
public final class ReleaseAccessActivity extends Activity {
    private final Handler handler = new Handler();
    private TextView status;
    private TextView code;
    private Button action;
    private String deviceToken;
    private String verificationUri;
    private long expires;
    private boolean busy;
    private boolean destroyed;
    private int generation;
    private int interval = 5000;
    private int dp(int size) { return Math.round(size * getResources().getDisplayMetrics().density); }
    private TextView label(String text, int size, int color) {
        TextView view = new TextView(this); view.setText(text); view.setTextSize(size); view.setTextColor(color);
        view.setPadding(0, dp(10), 0, dp(10)); return view;
    }
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        if (ReleaseAccess.allowed()) { finish(); return; }
        getWindow().setStatusBarColor(Color.rgb(13,20,33));
        getWindow().setNavigationBarColor(Color.rgb(13,20,33));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL); root.setGravity(Gravity.CENTER_VERTICAL);
        root.setPadding(dp(28), dp(28), dp(28), dp(28)); root.setBackgroundColor(Color.rgb(13,20,33));
        android.widget.ImageView brandIcon = new android.widget.ImageView(this);
        brandIcon.setImageResource(org.telegram.messenger.R.drawable.comunidad_telebots_icon);
        brandIcon.setContentDescription(getString(org.telegram.messenger.R.string.AppName));
        root.addView(brandIcon, new LinearLayout.LayoutParams(dp(64), dp(64)));
        TextView brand = label("COMUNIDAD TELEBOTS  /  ANDROID", 12, 0xff71d6c2); root.addView(brand);
        TextView badge = label("ACCESO PRIVADO · " + ReleaseAccess.channel().toUpperCase(java.util.Locale.ROOT), 13, 0xffa6b8ff);
        root.addView(badge);
        root.addView(label(getString(org.telegram.messenger.R.string.ForkIndependentClient), 13, 0xffb7c3d8));
        TextView title = label("Tu acceso empieza aquí", 32, Color.WHITE); title.setTypeface(null, Typeface.BOLD); root.addView(title);
        root.addView(label("Esta versión está reservada a usuarios autorizados. Verifica tu cuenta con Moonbot para continuar.", 16, 0xffb7c3d8));
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(20), dp(12), dp(20), dp(12));
        GradientDrawable background = new GradientDrawable(); background.setColor(0xff192638); background.setCornerRadius(dp(20)); card.setBackground(background);
        card.addView(label("1  Solicita tu código de acceso", 16, Color.WHITE));
        card.addView(label("2  Verifica tu cuenta en la web", 16, Color.WHITE));
        card.addView(label("3  Espera la autorización de esta rama", 16, Color.WHITE));
        root.addView(card);
        root.addView(label("Todosobrealltech recibe tu identidad de Moonbot, la rama y versión y las comprobaciones de acceso mientras esta app está visible. El administrador puede consultar tu última conexión y gestionar permisos. Este control no envía tu ubicación GPS ni tus chats.", 13, 0xffb7c3d8));
        TextView privacy = label("Privacidad y datos del acceso", 14, 0xffa6b8ff);
        privacy.setOnClickListener(v -> openLegal("privacy")); root.addView(privacy);
        TextView terms = label("Condiciones de Comunidad Telebots", 14, 0xffa6b8ff);
        terms.setOnClickListener(v -> openLegal("terms")); root.addView(terms);
        code = label("", 26, 0xff71d6c2); code.setGravity(Gravity.CENTER); root.addView(code);
        action = new Button(this); action.setText("Continuar con Moonbot"); action.setAllCaps(false);
        GradientDrawable buttonBackground = new GradientDrawable(); buttonBackground.setColor(0xffa6b8ff); buttonBackground.setCornerRadius(dp(16));
        action.setBackground(buttonBackground); action.setTextColor(0xff0d1421); action.setTypeface(null, Typeface.BOLD);
        action.setOnClickListener(v -> { if (verificationUri == null) begin(); else openVerification(); }); root.addView(action, new LinearLayout.LayoutParams(-1, dp(56)));
        status = label("El acceso se verifica en todosobrealltech. No te pediremos la contraseña de Telegram en esta aplicación.", 13, 0xff93a3bd); root.addView(status);
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.setFillViewport(true); scroll.addView(root); setContentView(scroll);
        TorProxyController.ensureRunning();
    }
    private void begin() {
        if (busy) return; busy = true; action.setEnabled(false); status.setText("Conectando de forma segura…");
        final int requestGeneration = ++generation;
        ReleaseAccess.NETWORK.execute(() -> {
            try {
                JSONObject body = new JSONObject().put("product", "android").put("channel", ReleaseAccess.channel()).put("package", getPackageName()).put("version", getPackageManager().getPackageInfo(getPackageName(), 0).versionName);
                JSONObject result = ReleaseAccess.request("device/start", null, body);
                handler.post(() -> {
                    if (destroyed || requestGeneration != generation) return;
                    busy = false; action.setEnabled(true);
                    deviceToken = result.optString("device_token"); verificationUri = result.optString("verification_uri");
                    Uri uri = Uri.parse(verificationUri);
                    if (deviceToken.isEmpty() || !"https".equals(uri.getScheme()) || !"chat.todosobreall.tech".equals(uri.getHost()) || uri.getPort() != -1 || uri.getUserInfo() != null) { reset("Respuesta de acceso no válida."); return; }
                    expires = android.os.SystemClock.elapsedRealtime() + Math.min(300, result.optInt("expires_in", 300)) * 1000L;
                    interval = Math.max(5, result.optInt("interval", 5)) * 1000;
                    code.setText(result.optString("user_code")); action.setText("Abrir verificación segura");
                    status.setText("Verifica el código en la web. El permiso debe estar aprobado para Android · " + ReleaseAccess.channel() + ".");
                    handler.postDelayed(this::poll, interval);
                });
            } catch (Exception ignored) { handler.post(() -> { if (!destroyed && requestGeneration == generation) reset("No se pudo verificar el acceso. Revisa la conexión e inténtalo de nuevo."); }); }
        });
    }
    private void openLegal(String page) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(ReleaseAccess.ORIGIN + "/access/" + page))); }
        catch (Exception ignored) { status.setText("Abre " + ReleaseAccess.ORIGIN + "/access/" + page + " en un navegador."); }
    }
    private void openVerification() {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(verificationUri))); }
        catch (Exception ignored) { status.setText("Abre chat.todosobreall.tech/access/ en un navegador e introduce el código."); }
    }
    private void poll() {
        if (destroyed || deviceToken == null) return;
        if (android.os.SystemClock.elapsedRealtime() >= expires) { reset("El código ha caducado. Solicita uno nuevo."); return; }
        final String current = deviceToken;
        ReleaseAccess.NETWORK.execute(() -> {
            try {
                JSONObject result = ReleaseAccess.request("device/poll", current, new JSONObject());
                handler.post(() -> {
                    if (destroyed || !current.equals(deviceToken)) return;
                    String state = result.optString("status");
                    if ("approved".equals(state)) {
                        ReleaseAccess.approve(result.optString("session_token"), result.optInt("expires_in"));
                        if (ReleaseAccess.allowed()) {
                            startActivity(new Intent(this, LaunchActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK)); finish();
                        } else reset("La autorización recibida no es válida.");
                    } else if ("pending".equals(state)) handler.postDelayed(this::poll, interval);
                    else reset("No tienes permiso para esta rama. Contacta con el administrador.");
                });
            } catch (Exception ignored) { handler.post(() -> { if (!destroyed && current.equals(deviceToken)) reset("No se pudo confirmar tu permiso. Solicita un nuevo código."); }); }
        });
    }
    private void reset(String message) { generation++; busy = false; deviceToken = null; verificationUri = null; code.setText(""); action.setEnabled(true); action.setText("Continuar con Moonbot"); status.setText(message); }
    @Override public void onBackPressed() { moveTaskToBack(true); }
    @Override public void onDestroy() { destroyed = true; handler.removeCallbacksAndMessages(null); super.onDestroy(); }
}
