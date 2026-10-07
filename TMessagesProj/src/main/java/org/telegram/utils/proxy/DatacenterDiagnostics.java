package org.telegram.utils.proxy;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.util.*;

/** Explicit, foreground-owned MTProto RPC diagnostics. No sockets, DC migration or account authorization export. */
public final class DatacenterDiagnostics {
    public enum Status { UNTESTED, CHECKING, OK, FAILED, TIMEOUT, OFFLINE, CANCELLED }
    public static final class Entry {
        public final int dcId;
        public final boolean current, media;
        public final long latencyMs;
        public final Status status;
        public final String route;
        Entry(int dcId, boolean current, boolean media, long latencyMs, Status status, String route) {
            this.dcId = dcId; this.current = current; this.media = media;
            this.latencyMs = latencyMs; this.status = status; this.route = route;
        }
        Entry with(Status status, long ms) { return new Entry(dcId, current, media, ms, status, route); }
    }
    // tgnet/Defines.h ConnectionTypeGenericMedia. Use the existing native route, never an override proxy.
    private static final int GENERIC_MEDIA = 64;
    private static final int FLAGS = ConnectionsManager.RequestFlagEnableUnauthorized
            | ConnectionsManager.RequestFlagWithoutLogin | ConnectionsManager.RequestFlagFailOnServerErrors
            | ConnectionsManager.RequestFlagDoNotWaitFloodWait;
    private static final Map<Integer, Run> RUNS = new HashMap<>();
    private static final class Route {
        final boolean tor = TorProxyController.isEnabled();
        final boolean enabled = MessagesController.getGlobalMainSettings().getBoolean("proxy_enabled", false);
        final ProxySettings settings = tor ? TorProxyController.getProxy() : ProxySettings.fromSharedPreferences(MessagesController.getGlobalMainSettings());
        final String label = tor ? "TOR" : enabled ? "PROXY" : "DIRECT";
        boolean same() {
            if (tor != TorProxyController.isEnabled() || enabled != MessagesController.getGlobalMainSettings().getBoolean("proxy_enabled", false)) return false;
            ProxySettings other = tor ? TorProxyController.getProxy() : ProxySettings.fromSharedPreferences(MessagesController.getGlobalMainSettings());
            return settings.getPort() == other.getPort() && settings.getType() == other.getType()
                    && Objects.equals(settings.getAddress(), other.getAddress()) && Objects.equals(settings.getUser(), other.getUser())
                    && Objects.equals(settings.getPassword(), other.getPassword()) && Objects.equals(settings.getSecret(), other.getSecret());
        }
    }
    private static final class Probe {
        final Entry entry; final long start = now();
        int token; Runnable timeout;
        Probe(Entry entry) { this.entry = entry; }
    }
    private static final class Run {
        final int account, currentDc;
        final long owner, started = now();
        final Route route = new Route();
        Utilities.Callback<List<Entry>> callback;
        final LinkedHashMap<String, Entry> entries = new LinkedHashMap<>();
        final LinkedHashMap<String, Probe> probes = new LinkedHashMap<>();
        final ArrayDeque<Entry> waiting = new ArrayDeque<>();
        boolean checking = true, cancelled;
        Runnable watchdog;
        Run(int account, Utilities.Callback<List<Entry>> callback) {
            this.account = account; this.callback = callback;
            owner = UserConfig.getInstance(account).getClientUserId();
            currentDc = ConnectionsManager.getInstance(account).getCurrentDatacenterId();
        }
    }
    private DatacenterDiagnostics() {}
    private static long now() { return System.nanoTime() / 1000000L; }
    private static String key(Entry entry) { return entry.dcId + (entry.media ? ":media" : ":generic"); }
    /** All API entry points are UI-thread-only. Cached results belong to this signed-in account only. */
    public static List<Entry> snapshot(int account) {
        Run run = RUNS.get(account);
        if (run != null && run.owner == UserConfig.getInstance(account).getClientUserId()) return new ArrayList<>(run.entries.values());
        int dc = ConnectionsManager.getInstance(account).getCurrentDatacenterId();
        return dc > 0 ? Collections.singletonList(new Entry(dc, true, false, -1, Status.UNTESTED, "")) : Collections.emptyList();
    }
    public static boolean isChecking(int account) {
        Run run = RUNS.get(account);
        return run != null && run.checking && !run.cancelled && run.owner == UserConfig.getInstance(account).getClientUserId();
    }
    public static Runnable measure(int account, Utilities.Callback<List<Entry>> callback) {
        Run previous = RUNS.get(account);
        if (previous != null && previous.checking) stop(previous, Status.CANCELLED, false);
        Run run = new Run(account, callback);
        RUNS.put(account, run);
        if (run.currentDc <= 0) { run.checking = false; publish(run); return () -> {}; }
        Entry initial = new Entry(run.currentDc, true, false, -1, Status.UNTESTED, run.route.label);
        run.entries.put(key(initial), initial);
        if (!ApplicationLoader.isNetworkOnline()) { stop(run, Status.OFFLINE, true); return () -> {}; }
        run.watchdog = new Runnable() {
            @Override public void run() {
                if (!valid(run)) return;
                if (now() - run.started >= 45000) { stop(run, Status.TIMEOUT, true); return; }
                AndroidUtilities.runOnUIThread(this, 500);
            }
        };
        AndroidUtilities.runOnUIThread(run.watchdog, 500);
        send(run, initial, true);
        return () -> stop(run, Status.CANCELLED, false);
    }
    private static boolean valid(Run run) {
        if (RUNS.get(run.account) != run || run.cancelled || !run.checking) return false;
        if (run.owner != UserConfig.getInstance(run.account).getClientUserId() || !run.route.same()
                || run.currentDc != ConnectionsManager.getInstance(run.account).getCurrentDatacenterId()) {
            stop(run, Status.CANCELLED, true); return false;
        }
        if (!ApplicationLoader.isNetworkOnline()) { stop(run, Status.OFFLINE, true); return false; }
        return true;
    }
    private static void send(Run run, Entry entry, boolean discover) {
        if (!valid(run)) return;
        Probe probe = new Probe(entry);
        run.probes.put(key(entry), probe);
        run.entries.put(key(entry), entry.with(Status.CHECKING, -1));
        publish(run);
        probe.timeout = () -> {
            if (!valid(run) || run.probes.remove(key(entry)) != probe) return;
            ConnectionsManager.getInstance(run.account).cancelRequest(probe.token, true);
            run.entries.put(key(entry), entry.with(Status.TIMEOUT, -1));
            pump(run);
        };
        AndroidUtilities.runOnUIThread(probe.timeout, 15000);
        // EnableUnauthorized excludes the native authorization-export branch for other DCs.
        // No TryDifferentDc flag: never silently substitute a different target DC.
        probe.token = ConnectionsManager.getInstance(run.account).sendRequest(new TLRPC.TL_help_getConfig(), (response, error) -> {
            final long elapsed = now() - probe.start;
            final boolean success = error == null && response instanceof TLRPC.TL_config && ((TLRPC.TL_config) response).this_dc == entry.dcId;
            final ArrayList<Entry> targets = new ArrayList<>();
            if (success && discover) {
                if (run.owner == UserConfig.getInstance(run.account).getClientUserId()) TelegramCdnDiagnostics.configure(run.account, ((TLRPC.TL_config) response).dc_options);
                // Copy only IDs/kind on the request callback thread, before TL response resources are released.
                Set<String> seen = new HashSet<>();
                for (TLRPC.TL_dcOption dc : ((TLRPC.TL_config) response).dc_options) {
                    if (dc.id <= 0 || dc.cdn) continue;
                    Entry item = new Entry(dc.id, dc.id == run.currentDc && !dc.media_only, dc.media_only, -1, Status.UNTESTED, run.route.label);
                    if (seen.add(key(item)) && targets.size() < 20) targets.add(item);
                }
                targets.sort(Comparator.comparingInt((Entry e) -> e.dcId).thenComparing(e -> e.media));
            }
            AndroidUtilities.runOnUIThread(() -> {
                if (!valid(run) || run.probes.remove(key(entry)) != probe) return;
                AndroidUtilities.cancelRunOnUIThread(probe.timeout);
                run.entries.put(key(entry), entry.with(success ? Status.OK : Status.FAILED, success ? elapsed : -1));
                if (success && discover) {
                    for (Entry item : targets) {
                        if (run.entries.containsKey(key(item))) continue;
                        run.entries.put(key(item), item); run.waiting.add(item);
                    }
                }
                pump(run);
            });
        }, null, null, FLAGS, entry.dcId, entry.media ? GENERIC_MEDIA : ConnectionsManager.ConnectionTypeGeneric, true);
    }
    private static void pump(Run run) {
        if (!valid(run)) return;
        while (run.probes.size() < 2 && !run.waiting.isEmpty()) send(run, run.waiting.remove(), false);
        if (run.probes.isEmpty() && run.waiting.isEmpty()) {
            run.checking = false;
            AndroidUtilities.cancelRunOnUIThread(run.watchdog);
        }
        publish(run);
    }
    private static void publish(Run run) {
        if (run.callback == null || run.cancelled || RUNS.get(run.account) != run) return;
        List<Entry> copy = new ArrayList<>(run.entries.values());
        AndroidUtilities.runOnUIThread(() -> { if (!run.cancelled && RUNS.get(run.account) == run && run.owner == UserConfig.getInstance(run.account).getClientUserId()) run.callback.run(copy); }, 1);
    }
    private static void stop(Run run, Status reason, boolean notify) {
        // Retain numeric results only when the foreground owner leaves, never its activity.
        if (!notify) run.callback = null;
        if (!run.checking) { if (!notify) run.cancelled = true; return; }
        run.checking = false;
        if (run.watchdog != null) AndroidUtilities.cancelRunOnUIThread(run.watchdog);
        for (Probe probe : run.probes.values()) {
            AndroidUtilities.cancelRunOnUIThread(probe.timeout);
            ConnectionsManager.getInstance(run.account).cancelRequest(probe.token, true);
        }
        run.probes.clear(); run.waiting.clear();
        for (Map.Entry<String, Entry> item : run.entries.entrySet()) {
            Entry entry = item.getValue();
            if (entry.status == Status.CHECKING || entry.status == Status.UNTESTED || reason == Status.CANCELLED)
                item.setValue(entry.with(reason, -1));
        }
        if (notify) publish(run); else run.cancelled = true;
    }
}
