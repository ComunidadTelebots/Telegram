package org.telegram.utils.proxy;

import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import java.util.*;

/** Session-only CDN inventory. Never retains file tokens, paths, keys or message IDs. */
public final class TelegramCdnDiagnostics {
    public static final class Entry {
        public final int dcId;
        public final String endpoints, route;
        public final boolean observed, success;
        public final long responseMs;
        Entry(int id, String endpoints, String route, boolean observed, boolean success, long ms) {
            dcId = id; this.endpoints = endpoints; this.route = route;
            this.observed = observed; this.success = success; responseMs = ms;
        }
    }
    private static final Map<Integer, LinkedHashMap<Integer, Entry>> DATA = new HashMap<>();
    private static final Map<Integer, Long> OWNERS = new HashMap<>();
    private TelegramCdnDiagnostics() {}
    private static LinkedHashMap<Integer, Entry> data(int account) {
        long owner = UserConfig.getInstance(account).getClientUserId();
        if (!Objects.equals(OWNERS.get(account), owner)) {
            OWNERS.put(account, owner); DATA.put(account, new LinkedHashMap<>());
        }
        return DATA.get(account);
    }
    public static String route() { return TorProxyController.isEnabled() ? "TOR" : SharedConfig.isProxyEnabled() ? "PROXY" : "DIRECT"; }
    public static synchronized void configure(int account, List<TLRPC.TL_dcOption> options) {
        Map<Integer, LinkedHashSet<String>> endpoints = new LinkedHashMap<>();
        for (TLRPC.TL_dcOption option : options) {
            if (!option.cdn || option.id <= 0 || endpoints.size() >= 32) continue;
            LinkedHashSet<String> addresses = endpoints.computeIfAbsent(option.id, id -> new LinkedHashSet<>());
            if (addresses.size() < 8) addresses.add(option.ip_address + ":" + option.port);
        }
        Map<Integer, Entry> entries = data(account);
        for (Map.Entry<Integer, LinkedHashSet<String>> item : endpoints.entrySet()) {
            Entry old = entries.get(item.getKey());
            entries.put(item.getKey(), new Entry(item.getKey(), android.text.TextUtils.join(" · ", item.getValue()), old == null ? "" : old.route,
                    old != null && old.observed, old != null && old.success, old == null ? -1 : old.responseMs));
        }
    }
    public static synchronized void record(int account, int dcId, long elapsed, boolean success, String route) {
        if (dcId <= 0 || !route.equals(route())) return;
        LinkedHashMap<Integer, Entry> entries = data(account);
        Entry old = entries.get(dcId);
        entries.put(dcId, new Entry(dcId, old == null ? "" : old.endpoints, route, true, success, success ? elapsed : -1));
        while (entries.size() > 32) entries.remove(entries.keySet().iterator().next());
    }
    public static synchronized List<Entry> snapshot(int account) { return new ArrayList<>(data(account).values()); }
    public static synchronized void clear(int account) { data(account).clear(); }
}
