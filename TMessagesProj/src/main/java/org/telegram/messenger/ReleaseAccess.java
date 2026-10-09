package org.telegram.messenger;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.content.Intent;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import org.json.JSONObject;
import org.telegram.ui.ReleaseAccessActivity;
import org.telegram.utils.proxy.TorProxyController;
import java.net.URL;
import java.net.Proxy;
import java.net.InetSocketAddress;
import javax.net.ssl.HttpsURLConnection;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Server-issued, memory-only release leases. No Telegram credentials are collected. */
public final class ReleaseAccess {
    public static final String ORIGIN = "https://chat.todosobreall.tech";
    private static final Handler UI = new Handler(Looper.getMainLooper());
    public static final ExecutorService NETWORK = Executors.newSingleThreadExecutor();
    private static final WeakHashMap<Activity, View> shields = new WeakHashMap<>();
    private static volatile String token;
    private static volatile long deadline;
    private static long nextCheck;
    private static boolean checking;
    private static Activity foreground;

    public static String channel() {
        String name = ApplicationLoader.applicationContext.getPackageName();
        return name.substring(name.lastIndexOf('.') + 1);
    }
    public static boolean restricted() {
        return true; // Forzar Moonbot Dev SIEMPRE (incluso en dev/emulador)
    }
    private static boolean emulatorPreview(String channel) {
        return false;
    }
    public static boolean allowed() { return !restricted() || token != null && SystemClock.elapsedRealtime() < deadline; }
    public static void approve(String session, int seconds) {
        if (session == null || session.isEmpty() || seconds <= 0) return;
        token = session;
        deadline = SystemClock.elapsedRealtime() + Math.min(seconds, 300) * 1000L;
        nextCheck = SystemClock.elapsedRealtime() + 60000;
    }
    public static void revokeAccessAndLock(String reason) {
        token = null;
        deadline = 0;
        if (foreground != null) {
            UI.post(() -> {
                android.widget.Toast.makeText(foreground, "Acceso Revocado: " + reason, android.widget.Toast.LENGTH_LONG).show();
                protect(foreground);
            });
        }
    }
    public static void install(Application app) {
        if (!restricted()) return;
        ((android.app.NotificationManager) app.getSystemService(android.content.Context.NOTIFICATION_SERVICE)).cancelAll();
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            public void onActivityCreated(Activity a, Bundle b) { protect(a); }
            public void onActivityStarted(Activity a) { protect(a); }
            public void onActivityResumed(Activity a) { foreground = a; protect(a); }
            public void onActivityPaused(Activity a) { if (foreground == a) foreground = null; }
            public void onActivityStopped(Activity a) {}
            public void onActivitySaveInstanceState(Activity a, Bundle b) {}
            public void onActivityDestroyed(Activity a) { shields.remove(a); if (foreground == a) foreground = null; }
        });
        UI.post(new Runnable() {
            public void run() {
                if (foreground != null) protect(foreground);
                if (foreground != null && !(foreground instanceof ReleaseAccessActivity)
                        && token != null && !checking && SystemClock.elapsedRealtime() >= nextCheck) {
                    checking = true;
                    final String current = token;
                    NETWORK.execute(() -> {
                        JSONObject result = null;
                        try { result = request("session/check", current, new JSONObject()); } catch (Exception ignored) {}
                        final JSONObject response = result;
                        UI.post(() -> {
                            checking = false;
                            if (!current.equals(token)) return;
                            nextCheck = SystemClock.elapsedRealtime() + 60000;
                            if (response != null && response.optBoolean("allowed")) approve(current, response.optInt("expires_in"));
                            else { token = null; deadline = 0; }
                            if (foreground != null) protect(foreground);
                        });
                    });
                }
                UI.postDelayed(this, 1000);
            }
        });
    }
    private static void protect(Activity a) {
        // The login screen contains no chats; all other windows must prohibit capture.
        if (a instanceof ReleaseAccessActivity) return;
        a.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        View shield = shields.get(a);
        if (shield != null && shield.getParent() == null) { shields.remove(a); shield = null; }
        if (allowed()) {
            if (shield != null) { ((ViewGroup) shield.getParent()).removeView(shield); shields.remove(a); a.findViewById(android.R.id.content).setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO); }
            return;
        }
        if (shield == null) {
            FrameLayout cover = new FrameLayout(a);
            cover.setBackgroundColor(Color.rgb(13, 20, 33));
            cover.setClickable(true);
            cover.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            cover.setContentDescription("Acceso restringido. Inicia sesión con Moonbot.");
            ViewGroup content = a.findViewById(android.R.id.content);
            content.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            a.addContentView(cover, new ViewGroup.LayoutParams(-1, -1));
            shields.put(a, cover);
        }
        if (a == foreground) a.startActivity(new Intent(a, ReleaseAccessActivity.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP));
    }
    public static JSONObject checkAndroidUpdate(long versionCode) throws Exception {
        if (!allowed()) throw new java.io.IOException("Release access required");
        return request("android/update", restricted() ? token : null, new JSONObject()
                .put("channel", channel())
                .put("package", ApplicationLoader.applicationContext.getPackageName())
                .put("version_code", versionCode));
    }

    public static JSONObject request(String route, String bearer, JSONObject body) throws Exception {
        Proxy proxy = Proxy.NO_PROXY;
        if (TorProxyController.isEnabled()) {
            proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("127.0.0.1", TorProxyController.getProxy().getPort()));
        }
        org.telegram.utils.proxy.NetworkDomainDiagnostics.observe(ORIGIN, "access_api");
        HttpsURLConnection connection = (HttpsURLConnection) new URL(ORIGIN + "/access/api/" + route).openConnection(proxy);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(15000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        if (bearer != null) connection.setRequestProperty("Authorization", "Bearer " + bearer);
        connection.setDoOutput(true);
        try {
            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            try (java.io.OutputStream output = connection.getOutputStream()) { output.write(bytes); }
            if (connection.getResponseCode() != 200) throw new java.io.IOException("Access service rejected request");
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096]; int count;
                while ((count = input.read(buffer)) != -1) {
                    if (output.size() + count > 32768) throw new java.io.IOException("Response too large");
                    output.write(buffer, 0, count);
                }
                return new JSONObject(output.toString("UTF-8"));
            }
        } finally { connection.disconnect(); }
    }
}
