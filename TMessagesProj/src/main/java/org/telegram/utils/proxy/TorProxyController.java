package org.telegram.utils.proxy;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.tgnet.ConnectionsManager;

/** A separate opt-in route. Never replaces the user's saved conventional proxy. */
public final class TorProxyController {
    private static volatile int port;
    private static volatile int status = R.string.TorStopped;
    private static volatile int progress;
    private static boolean requested;

    private TorProxyController() {}

    private static SharedPreferences preferences() {
        return ApplicationLoader.applicationContext.getSharedPreferences("embedded_tor", Context.MODE_PRIVATE);
    }

    public static boolean isSupported() { return Build.VERSION.SDK_INT >= 24; }
    public static boolean isEnabled() {
        return isSupported() && ApplicationLoader.applicationContext != null && preferences().getBoolean("enabled", false);
    }
    public static int getStatus() { return status; }
    public static int getProgress() { return progress; }

    public static ProxySettings getProxy() {
        // The closed loopback route stays in force before bootstrap and after failure.
        return ProxySettings.builder().setAddress("127.0.0.1").setPort(port > 0 ? port : 9).build();
    }

    public static void setEnabled(boolean enabled) {
        if (enabled && !isSupported()) return;
        if (enabled && android.net.VpnService.prepare(ApplicationLoader.applicationContext) != null) return;
        if (!preferences().edit().putBoolean("enabled", enabled).commit()) return;
        port = 0;
        progress = 0;
        status = enabled ? R.string.TorStarting : R.string.TorStopped;
        applyRoute();
        if (enabled) {
            ensureRunning();
        } else {
            requested = false;
            // Android binds the VPN service while its TUN is open. stopService alone
            // cannot destroy a bound service: deliver a command that closes the TUN.
            ApplicationLoader.applicationContext.startService(serviceIntent());
        }
        notifyUi();
    }

    private static Intent serviceIntent() {
        // Use a class name so API 21-23 never have to load the API 24 native service.
        return new Intent().setClassName(ApplicationLoader.applicationContext,
                "org.telegram.utils.proxy.TorVpnService");
    }

    /** Called from a visible activity, not a background push receiver. */
    public static void ensureRunning() {
        if (!isEnabled() || !isSupported() || requested) return;
        if (android.net.VpnService.prepare(ApplicationLoader.applicationContext) != null) {
            updateRuntime(0, R.string.TorVpnRevoked, 0);
            return;
        }
        requested = true;
        status = R.string.TorStarting;
        try {
            System.loadLibrary("tor");
            if (Build.VERSION.SDK_INT >= 26) {
                ApplicationLoader.applicationContext.startForegroundService(serviceIntent());
            } else {
                ApplicationLoader.applicationContext.startService(serviceIntent());
            }
        } catch (RuntimeException | LinkageError e) {
            requested = false;
            updateRuntime(0, R.string.TorFailed, 0);
        }
    }

    static void updateRuntime(int newPort, int newStatus, int percent) {
        AndroidUtilities.runOnUIThread(() -> {
            if (!isEnabled()) return;
            boolean routeChanged = port != newPort;
            boolean changed = routeChanged || status != newStatus || progress != percent;
            port = newPort;
            status = newStatus;
            progress = percent;
            if (routeChanged) applyRoute();
            if (changed) notifyUi();
        });
    }

    static void stopped() {
        AndroidUtilities.runOnUIThread(() -> {
            requested = false;
            port = 0;
            status = isEnabled() ? R.string.TorFailed : R.string.TorStopped;
            progress = 0;
            if (isEnabled()) applyRoute();
            notifyUi();
        });
    }

    private static void applyRoute() {
        SharedPreferences prefs = MessagesController.getGlobalMainSettings();
        ConnectionsManager.setProxySettings(prefs.getBoolean("proxy_enabled", false),
                ProxySettings.fromSharedPreferences(prefs));
    }

    private static void notifyUi() {
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);
    }
}
