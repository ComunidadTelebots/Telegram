package org.telegram.ui;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.core.app.NotificationManagerCompat;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.CommunityCompatibility;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CommunitySettingsUi;
import org.telegram.ui.Components.LayoutHelper;

/** Read-only local capability report. No identifiers or device information are uploaded. */
public final class CommunityCompatibilityActivity extends BaseFragment {
    private LinearLayout rows;

    @Override public View createView(Context context) {
        actionBar.setTitle("Compatibilidad del dispositivo");
        actionBar.setBackButtonImage(org.telegram.messenger.R.drawable.ic_ab_back);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) { if (id == -1) finishFragment(); }
        });
        ScrollView scroll = new ScrollView(context);
        scroll.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        rows = new LinearLayout(context);
        rows.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(rows);
        fragmentView = scroll;
        render();
        return scroll;
    }

    private void render() {
        if (rows == null) return;
        rows.removeAllViews();
        Context context = rows.getContext();
        int sdk = Build.VERSION.SDK_INT;
        row("Sistema", "Android " + Build.VERSION.RELEASE + " · API " + sdk);
        row("Alcance", "Este panel comprueba requisitos locales. No garantiza conexión, entrega push ni compatibilidad completa de la app. La adaptación a Android 5.0 sigue en pruebas.");
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (manager != null) {
            ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
            manager.getMemoryInfo(memory);
            row("Memoria", (memory.totalMem / (1024L * 1024L)) + " MiB de RAM · "
                    + ((manager.isLowRamDevice() || (memory.totalMem > 0 && memory.totalMem <= 2L * 1024 * 1024 * 1024))
                    ? "Protecciones de poca memoria aplicables" : "Sin clasificación de poca memoria"));
        }
        row("Chat P2P cifrado", CommunityCompatibility.supportsPeerIdentity(sdk)
                ? "Requisito del sistema disponible. Necesita identidad Keystore válida, autorización mutua y red local; no funciona con Tor o proxy activos."
                : "No disponible: la identidad actual requiere Android 6.0. No se usará transporte sin cifrado como alternativa.");
        row("Tor integrado", CommunityCompatibility.supportsBundledTor(sdk)
                ? "Requisito del sistema disponible. Requiere permiso VPN e inicio correcto del servicio."
                : "No disponible: el binario integrado requiere Android 7.0. Puedes configurar un proxy externo en Conexiones.");
        row("Notificaciones", NotificationManagerCompat.from(context).areNotificationsEnabled()
                ? "Permitidas por Android. Esto no comprueba el registro FCM ni la entrega del servidor; cada chat o canal puede estar silenciado."
                : "Bloqueadas por Android. Revisa el permiso en los ajustes de la aplicación.");
        row("Controles de notificación", CommunityCompatibility.requiresNotificationPermission(sdk)
                ? "Este Android requiere permiso de notificaciones y admite canales."
                : CommunityCompatibility.supportsNotificationChannels(sdk)
                ? "Este Android admite canales de notificación."
                : "Este Android usa los controles generales de notificación, sin canales del sistema.");
        TextView settings = row("Abrir ajustes de la aplicación", "Permisos, notificaciones, batería y almacenamiento");
        CommunitySettingsUi.button(settings);
        settings.setOnClickListener(v -> {
            try {
                getParentActivity().startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + context.getPackageName())));
            } catch (Exception ignored) {
                android.widget.Toast.makeText(context, "No se pudieron abrir los ajustes del sistema", android.widget.Toast.LENGTH_SHORT).show();
            }
        });
        row("Privacidad", "La información de este panel se consulta localmente y no se envía a Alltech.");
    }

    private TextView row(String title, String detail) {
        TextView view = new TextView(rows.getContext());
        view.setText(title + "\n" + detail);
        view.setTextSize(16);
        view.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        view.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(14), AndroidUtilities.dp(20), AndroidUtilities.dp(14));
        rows.addView(view, LayoutHelper.createLinear(-1, -2));
        return view;
    }

    @Override public void onResume() { super.onResume(); render(); }
}
