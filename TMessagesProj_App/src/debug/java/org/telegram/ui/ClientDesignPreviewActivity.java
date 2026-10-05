package org.telegram.ui;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.TextView;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.ui.skins.SkinBase;
import org.telegram.ui.skins.SkinManager;
import org.telegram.ui.skins.SkinPreviewDialog;

/** Debug-only host for screenshots of the existing native design preview. */
public final class ClientDesignPreviewActivity extends Activity {
    private ProxyListActivity proxyFragment;
    @Override protected void onActivityResult(int request, int result, android.content.Intent data) {
        super.onActivityResult(request, result, data);
        if (proxyFragment != null) proxyFragment.onActivityResultFragment(request, result, data);
    }
    @Override protected void onResume() {
        super.onResume();
        if (proxyFragment != null) proxyFragment.onResume();
    }
    @Override protected void onPause() {
        if (proxyFragment != null) proxyFragment.onPause();
        super.onPause();
    }
    @Override protected void onDestroy() {
        if (proxyFragment != null) proxyFragment.onFragmentDestroy();
        super.onDestroy();
    }
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        ApplicationLoader.postInitApplication();
        if (getIntent().getBooleanExtra("proxy", false)) {
            org.telegram.ui.ActionBar.ActionBarLayout layout = new org.telegram.ui.ActionBar.ActionBarLayout(this, false);
            layout.setFragmentStack(new java.util.ArrayList<>());
            setContentView(layout);
            proxyFragment = new ProxyListActivity();
            layout.addFragmentToStack(proxyFragment, -1);
            layout.showLastFragment();
            String action = getIntent().getStringExtra("torAction");
            if ("enable".equals(action)) org.telegram.utils.proxy.TorProxyController.setEnabled(true);
            if ("disable".equals(action)) org.telegram.utils.proxy.TorProxyController.setEnabled(false);
            Runnable probe = () -> new Thread(() -> {
                javax.net.ssl.HttpsURLConnection connection = null;
                try {
                    connection = (javax.net.ssl.HttpsURLConnection)new java.net.URL("https://check.torproject.org/api/ip").openConnection();
                    connection.setConnectTimeout(20000); connection.setReadTimeout(20000);
                    java.io.ByteArrayOutputStream body = new java.io.ByteArrayOutputStream();
                    try (java.io.InputStream input = connection.getInputStream()) {
                        byte[] buffer = new byte[1024]; int length;
                        while ((length = input.read(buffer)) > 0 && body.size() < 4096) body.write(buffer, 0, length);
                    }
                    org.json.JSONObject response = new org.json.JSONObject(body.toString("UTF-8"));
                    android.util.Log.i("TorVpnProbe", "HTTPS IsTor=" + response.optBoolean("IsTor"));
                } catch (Exception e) { android.util.Log.i("TorVpnProbe", "HTTPS failed: " + e.getClass().getSimpleName()); }
                finally { if (connection != null) connection.disconnect(); }
            }, "TorVpnProbe").start();
            if ("probe".equals(action)) probe.run();
            if ("failProbe".equals(action)) new android.os.Handler(getMainLooper()).postDelayed(() -> {
                stopService(new android.content.Intent(this, org.telegram.utils.proxy.EmbeddedTorService.class));
                new android.os.Handler(getMainLooper()).postDelayed(probe, 1000);
            }, 1000);
            if ("cleartext".equals(action)) new Thread(() -> {
                java.net.HttpURLConnection connection = null;
                try {
                    connection = (java.net.HttpURLConnection)new java.net.URL("http://example.com").openConnection();
                    connection.setConnectTimeout(5000); connection.setReadTimeout(5000);
                    connection.getInputStream().close();
                    android.util.Log.e("TorVpnProbe", "Cleartext unexpectedly allowed");
                } catch (java.io.IOException expected) { android.util.Log.i("TorVpnProbe", "Cleartext rejected: " + expected.getMessage()); }
                finally { if (connection != null) connection.disconnect(); }
            }, "CleartextProbe").start();
            return;
        }
        TextView label = new TextView(this);
        label.setText("VISTA PREVIA NATIVA · DATOS DE EJEMPLO");
        label.setTextSize(12);
        label.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        label.setPadding(12, 110, 12, 12);
        label.setTextColor(0xff46566a);
        label.setBackgroundColor(0xffedf1f5);
        setContentView(label);
        String id = getIntent().getStringExtra("skin");
        for (SkinBase skin : SkinManager.get().getSkins()) {
            if (skin.getId().equals(id)) {
                label.post(() -> SkinPreviewDialog.show(this, skin, this::finish));
                return;
            }
        }
        finish();
    }
}
