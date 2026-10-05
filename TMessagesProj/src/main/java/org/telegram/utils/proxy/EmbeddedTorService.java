package org.telegram.utils.proxy;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.LaunchActivity;
import org.torproject.jni.TorService;

import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Tor runs in the application's process using the packaged libtor.so. */
@RequiresApi(24)
public final class EmbeddedTorService extends TorService {
    private static final String CHANNEL = "embedded_tor";
    private static final int NOTIFICATION_ID = 8391;
    private static final Pattern PROGRESS = Pattern.compile("PROGRESS=(\\d+)");
    private final ScheduledExecutorService monitor = Executors.newSingleThreadScheduledExecutor();
    private boolean started;
    private volatile boolean destroyed;
    private int lastStatus = -1;
    private int lastProgress = -1;
    private final BroadcastReceiver errors = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            TorProxyController.updateRuntime(0, R.string.TorFailed, 0);
            stopSelf();
        }
    };

    @Override public void onCreate() {
        // Do not start a disabled Tor instance after a stale service restart.
        if (!TorProxyController.isEnabled()) { stopSelf(); return; }
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(new NotificationChannel(CHANNEL,
                    LocaleController.getString(R.string.TorTitle), NotificationManager.IMPORTANCE_LOW));
        }
        startForeground(NOTIFICATION_ID, notification(R.string.TorStarting, 0));
        String egress = TorVpnService.egressConfiguration();
        if (egress == null) { stopSelf(); return; }
        try (FileOutputStream out = new FileOutputStream(TorService.getTorrc(this))) {
            // Explicit loopback binding, random per-instance port, no relay or HTTP listener.
            out.write(("SocksPort 127.0.0.1:auto\nHTTPTunnelPort 0\nClientOnly 1\nAvoidDiskWrites 1\n" + egress).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            TorProxyController.updateRuntime(0, R.string.TorFailed, 0);
            stopSelf();
            return;
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(errors, new IntentFilter(TorService.ACTION_ERROR));
        started = true;
        super.onCreate();
        monitor.scheduleWithFixedDelay(this::checkBootstrap, 1, 2, TimeUnit.SECONDS);
    }

    private void checkBootstrap() {
        if (destroyed || getTorControlConnection() == null) return;
        try {
            boolean ready = "1".equals(getInfo("status/circuit-established"));
            String bootstrap = getInfo("status/bootstrap-phase");
            Matcher matcher = PROGRESS.matcher(bootstrap == null ? "" : bootstrap);
            int progress = matcher.find() ? Math.min(100, Integer.parseInt(matcher.group(1))) : 0;
            int port = ready ? getSocksPort() : 0;
            if (port > 0 && !TorVpnService.route(port)) port = 0;
            int status = port > 0 ? R.string.TorConnected : R.string.TorStarting;
            if (destroyed) return;
            TorProxyController.updateRuntime(port, status, progress);
            if (status != lastStatus || progress != lastProgress) {
                if (org.telegram.messenger.BuildConfig.DEBUG_VERSION) android.util.Log.d("TorBootstrap", bootstrap);
                lastStatus = status;
                lastProgress = progress;
                ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(NOTIFICATION_ID, notification(status, progress));
            }
        } catch (RuntimeException e) {
            TorProxyController.updateRuntime(0, R.string.TorFailed, 0);
        }
    }

    private Notification notification(int status, int progress) {
        Intent open = new Intent(this, LaunchActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pending = PendingIntent.getActivity(this, 8391, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String text = LocaleController.getString(status);
        if (status == R.string.TorStarting) text += " " + progress + "%";
        return new NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.notification).setContentTitle(LocaleController.getString(R.string.TorTitle))
                .setContentText(text).setContentIntent(pending).setOngoing(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE).setOnlyAlertOnce(true).build();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (!TorProxyController.isEnabled()) { stopSelf(); return START_NOT_STICKY; }
        return START_STICKY;
    }

    @Override public void onDestroy() {
        destroyed = true;
        monitor.shutdownNow();
        if (started) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(errors);
            super.onDestroy();
        }
        stopForeground(true);
        TorProxyController.stopped();
    }
}
