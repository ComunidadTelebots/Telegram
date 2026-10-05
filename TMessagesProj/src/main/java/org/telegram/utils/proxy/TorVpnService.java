package org.telegram.utils.proxy;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import androidx.core.app.NotificationCompat;
import org.telegram.messenger.R;
import org.telegram.ui.LaunchActivity;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import hev.htproxy.TProxyService;

/** Captures this package only. Retains the TUN when Tor fails, with no direct fallback. */
public final class TorVpnService extends VpnService {
    private static volatile TorVpnService instance;
    private ParcelFileDescriptor tunnel;
    private TorEgress egress;
    private int tunnelPort;

    static String egressConfiguration() {
        TorVpnService service = instance;
        if (service == null || service.egress == null || service.tunnel == null) return null;
        return service.egress.configuration();
    }

    static synchronized boolean route(int port) {
        TorVpnService service = instance;
        return service != null && service.startTunnel(port);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (!TorProxyController.isEnabled()) {
            closeTunnel();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        NotificationManager manager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(new NotificationChannel("tor_vpn", getString(R.string.TorTitle), NotificationManager.IMPORTANCE_LOW));
        PendingIntent open = PendingIntent.getActivity(this, 8392, new Intent(this, LaunchActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        startForeground(8392, new NotificationCompat.Builder(this, "tor_vpn").setSmallIcon(R.drawable.notification)
                .setContentTitle(getString(R.string.TorTitle)).setContentText(getString(R.string.TorVpnActive))
                .setContentIntent(open).setOngoing(true).build());
        if (tunnel != null) {
            if (egress != null) startTor();
            return START_STICKY;
        }
        try {
            // Both IP families captured; no disallowed apps, bypass flag, or external DNS.
            tunnel = new Builder().setSession(getString(R.string.TorTitle)).setMtu(1500)
                    .addAddress("198.18.0.1", 32).addAddress("fd00:746f:7200::1", 128)
                    .addRoute("0.0.0.0", 0).addRoute("::", 0).addDnsServer("198.18.0.2")
                    .addAllowedApplication(getPackageName()).establish();
            if (tunnel == null) throw new IllegalStateException("VPN consent missing");
            instance = this;
            egress = new TorEgress(this);
            startTor();
        } catch (Exception | LinkageError error) {
            TorProxyController.updateRuntime(0, R.string.TorFailed, 0);
            // If established, keep the interface open and blocked for explicit user recovery.
            if (tunnel == null) stopSelf();
        }
        return START_STICKY;
    }

    private void startTor() {
        Intent tor = new Intent(this, EmbeddedTorService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(tor); else startService(tor);
    }

    private synchronized boolean startTunnel(int port) {
        if (tunnel == null || port <= 0) return false;
        try {
            if (tunnelPort == port && TProxyService.TProxyIsRunning()) return true;
            TProxyService.TProxyStopService();
            File config = new File(getFilesDir(), "tor-tunnel.yml");
            String text = "tunnel:\n  mtu: 1500\n  ipv4: 198.18.0.1\n  ipv6: 'fd00:746f:7200::1'\n  icmp: 'off'\n"
                    + "socks5:\n  address: 127.0.0.1\n  port: " + port + "\n  udp: 'udp'\n"
                    + "mapdns:\n  address: 198.18.0.2\n  port: 53\n  network: 100.64.0.0\n  netmask: 255.192.0.0\n  cache-size: 512\n"
                    + "misc:\n  task-stack-size: 24576\n  tcp-buffer-size: 4096\n  max-session-count: 128\n  connect-timeout: 30000\n  log-level: error\n";
            try (FileOutputStream output = new FileOutputStream(config)) { output.write(text.getBytes(StandardCharsets.UTF_8)); }
            // UDP attempts stay inside the SOCKS protocol and are rejected by Tor.
            boolean running = TProxyService.TProxyStartService(config.getAbsolutePath(), tunnel.getFd());
            tunnelPort = running ? port : 0;
            return running;
        } catch (Exception | LinkageError error) { return false; }
    }

    @Override public void onRevoke() {
        TorProxyController.updateRuntime(0, R.string.TorVpnRevoked, 0);
        closeTunnel();
        stopSelf();
    }

    private synchronized void closeTunnel() {
        instance = null;
        stopService(new Intent(this, EmbeddedTorService.class));
        try { TProxyService.TProxyStopService(); } catch (LinkageError ignored) {}
        if (egress != null) egress.close();
        egress = null;
        if (tunnel != null) try { tunnel.close(); } catch (Exception ignored) {}
        tunnel = null;
        tunnelPort = 0;
    }

    @Override public void onDestroy() {
        closeTunnel();
        TorProxyController.stopped();
        super.onDestroy();
    }
}
