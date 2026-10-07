package org.telegram.messenger;

import android.content.SharedPreferences;
import android.os.SystemClock;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.utils.proxy.TorProxyController;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Opt-in failover of saved proxies. Tor always owns the route while enabled. */
public class ProxyRotationController implements NotificationCenter.NotificationCenterDelegate {
    private static final ProxyRotationController INSTANCE = new ProxyRotationController();
    public static final int DEFAULT_TIMEOUT_INDEX = 1;
    public static final List<Integer> ROTATION_TIMEOUTS = Arrays.asList(5, 10, 15, 30, 60);
    private static final long COOLDOWN = 60000, FRESHNESS = 120000;
    private boolean initialized, scheduled, isCurrentlyChecking;
    private long lastSwitch;
    private Runnable cancelCheck;
    private final Runnable checkProxyAndSwitchRunnable = () -> {
        scheduled = false;
        if (!canRotate()) return;
        isCurrentlyChecking = true;
        cancelCheck = checkNow(UserConfig.selectedAccount, count -> {
            cancelCheck = null; isCurrentlyChecking = false;
            switchToAvailable();
        });
    };
    public static void init() { INSTANCE.initInternal(); }

    /** UI-thread entry. Checks saved entries with at most four native probes in flight.
     * Returns available count, or -1 when Tor owns the route. Does not enable/switch proxies.
     * Cancellation stops queued probes and callback; already-started native checks may finish.
     */
    public static Runnable checkNow(int account, Utilities.Callback<Integer> callback) {
        final class Batch {
            final ArrayList<SharedConfig.ProxyInfo> queue = new ArrayList<>(SharedConfig.proxyList);
            int cursor, active;
            boolean stopped;
            final Runnable deadline = () -> finish();
            void finish() {
                if (stopped) return;
                stopped = true; AndroidUtilities.cancelRunOnUIThread(deadline);
                int available = 0;
                for (SharedConfig.ProxyInfo info : queue) if (info.available && !info.checking
                        && SystemClock.elapsedRealtime() - info.availableCheckTime < FRESHNESS) available++;
                callback.run(TorProxyController.isEnabled() ? -1 : available);
            }
            void pump() {
                if (stopped) return;
                if (TorProxyController.isEnabled()) { finish(); return; }
                while (active < 4 && cursor < queue.size()) {
                    SharedConfig.ProxyInfo info = queue.get(cursor++);
                    if (info.checking || !info.settings.isValid()) continue;
                    if (info.availableCheckTime != 0 && SystemClock.elapsedRealtime() - info.availableCheckTime < FRESHNESS) continue;
                    info.checking = true; active++;
                    ConnectionsManager.getInstance(account).checkProxy(info.settings, time -> AndroidUtilities.runOnUIThread(() -> {
                        info.checking = false;
                        // A route change invalidates this measurement instead of marking the proxy down.
                        if (!TorProxyController.isEnabled()) {
                            info.availableCheckTime = SystemClock.elapsedRealtime();
                            info.available = time >= 0; info.ping = Math.max(0, time);
                        }
                        active--;
                        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxyCheckDone, info);
                        pump();
                    }));
                }
                if (active == 0 && cursor >= queue.size()) finish();
            }
            void cancel() { stopped = true; AndroidUtilities.cancelRunOnUIThread(deadline); }
        }
        Batch batch = new Batch();
        // Always async, including empty lists and Tor rejection.
        AndroidUtilities.runOnUIThread(batch::pump);
        AndroidUtilities.runOnUIThread(batch.deadline, 45000);
        return batch::cancel;
    }
    private boolean canRotate() {
        return !TorProxyController.isEnabled() && SharedConfig.isProxyEnabled() && SharedConfig.proxyRotationEnabled
                && SharedConfig.proxyList.size() > 1
                && ConnectionsManager.getInstance(UserConfig.selectedAccount).getConnectionState() == ConnectionsManager.ConnectionStateConnectingToProxy;
    }
    private void switchToAvailable() {
        if (!canRotate() || (lastSwitch != 0 && SystemClock.elapsedRealtime() - lastSwitch < COOLDOWN)) return;
        SharedConfig.ProxyInfo best = null;
        for (SharedConfig.ProxyInfo info : SharedConfig.proxyList) {
            if (info == SharedConfig.currentProxy || info.checking || !info.available
                    || SystemClock.elapsedRealtime() - info.availableCheckTime >= FRESHNESS) continue;
            if (!org.telegram.utils.proxy.ProxyReliability.eligible(info.settings)) continue;
            if (best == null || info.ping < best.ping) best = info;
        }
        if (best == null) return;
        lastSwitch = SystemClock.elapsedRealtime();
        SharedPreferences.Editor editor = MessagesController.getGlobalMainSettings().edit();
        editor.putBoolean("proxy_enabled", true); best.settings.toSharedPreferences(editor); editor.apply();
        SharedConfig.currentProxy = best;
        ConnectionsManager.setProxySettings(true, best.settings);
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxyChangedByRotation);
    }
    private void initInternal() {
        if (initialized) return; initialized = true;
        for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++) NotificationCenter.getInstance(i).addObserver(this, NotificationCenter.didUpdateConnectionState);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.proxySettingsChanged);
    }
    @Override public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.proxySettingsChanged || (id == NotificationCenter.didUpdateConnectionState && account == UserConfig.selectedAccount)) {
            AndroidUtilities.cancelRunOnUIThread(checkProxyAndSwitchRunnable); scheduled = false;
            if (!canRotate()) {
                if (cancelCheck != null) { cancelCheck.run(); cancelCheck = null; }
                isCurrentlyChecking = false;
                return;
            }
            if (!isCurrentlyChecking && !scheduled) {
                int index = Math.max(0, Math.min(ROTATION_TIMEOUTS.size() - 1, SharedConfig.proxyRotationTimeout));
                long delay = ROTATION_TIMEOUTS.get(index) * 1000L;
                if (lastSwitch != 0) delay = Math.max(delay, COOLDOWN - (SystemClock.elapsedRealtime() - lastSwitch));
                scheduled = true; AndroidUtilities.runOnUIThread(checkProxyAndSwitchRunnable, delay);
            }
        }
    }
}
