package org.telegram.utils.proxy;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.ReleaseAccess;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.ui.web.AmpResolver;
import org.telegram.ui.web.AmpUrls;

import java.net.URI;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Partial, session-only hostname inventory. Never retains URLs, paths, query parameters or account IDs. */
public final class NetworkDomainDiagnostics {
    public enum FailureReason { NONE, OFFLINE, DNS, TLS, TIMEOUT, CONNECTION, ROUTE_CHANGED, PROXY_UNSUPPORTED, UNKNOWN }
    public enum Status { UNTESTED, CHECKING, OK, FAILED, OFFLINE, UNSUPPORTED_ROUTE }
    public static final class Entry {
        public final String host, domain, source, route;
        public final boolean observed, configured;
        public final long lastSeenAt, latencyMs;
        public final Status status;
        public final FailureReason failureReason;
        Entry(String host, String source, boolean observed, boolean configured, long lastSeenAt,
              long latencyMs, Status status, String route) {
            this(host, source, observed, configured, lastSeenAt, latencyMs, status, route, FailureReason.NONE);
        }
        Entry(String host, String source, boolean observed, boolean configured, long lastSeenAt,
              long latencyMs, Status status, String route, FailureReason reason) {
            failureReason = reason;
            this.host = host; domain = host; this.source = source; this.observed = observed;
            this.configured = configured; this.lastSeenAt = lastSeenAt; this.latencyMs = latencyMs;
            this.status = status; this.route = route;
        }
    }
    private static final int LIMIT = 64;
    private static final long RETENTION_MS = 30 * 60 * 1000L;
    private static final LinkedHashMap<String, Entry> ENTRIES = new LinkedHashMap<>();
    private static final Map<String, Long> COMPLETED = new HashMap<>();
    private static final Map<String, Integer> FAILURES = new HashMap<>();
    private static final Map<String, Object> CHECKS = new HashMap<>();
    private static final ThreadPoolExecutor WORK = new ThreadPoolExecutor(2, 2, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(8), r -> { Thread t = new Thread(r, "Domain-diagnostics"); t.setDaemon(true); return t; });
    private NetworkDomainDiagnostics() {}

    /** Called at a connection/navigation attempt. Parsing happens immediately; the input URL is not retained. */
    public static void observe(String url, String source) {
        String safe = AmpUrls.normalize(url);
        if (safe == null) return;
        String host = URI.create(safe).getHost();
        if (AmpUrls.isCacheHost(host)) source = host.endsWith("bing-amp.com") ? "amp_cdn_bing" : "amp_cdn_google";
        synchronized (NetworkDomainDiagnostics.class) {
            prune();
            Entry old = ENTRIES.remove(host);
            ENTRIES.put(host, new Entry(host, source == null ? "app" : source.substring(0, Math.min(24, source.length())),
                    true, old != null && old.configured, System.currentTimeMillis(), old == null ? -1 : old.latencyMs,
                    old == null ? Status.UNTESTED : old.status, old == null ? "" : old.route, old == null ? FailureReason.NONE : old.failureReason));
            trim();
        }
    }
    private static void configured(String url, String source) {
        String safe = AmpUrls.normalize(url);
        if (safe == null) return;
        String host = URI.create(safe).getHost();
        Entry old = ENTRIES.get(host);
        if (old == null) ENTRIES.put(host, new Entry(host, source, false, true, 0, -1, Status.UNTESTED, ""));
        else if (!old.configured) ENTRIES.put(host, new Entry(host, old.source, old.observed, true, old.lastSeenAt, old.latencyMs, old.status, old.route, old.failureReason));
    }
    private static void prune() {
        long cutoff = System.currentTimeMillis() - RETENTION_MS;
        COMPLETED.entrySet().removeIf(e -> System.currentTimeMillis() - e.getValue() > 10 * 60 * 1000L);
        FAILURES.keySet().removeIf(key -> !ENTRIES.containsKey(key.substring(0, key.indexOf(":"))));
        ENTRIES.entrySet().removeIf(e -> e.getValue().observed && e.getValue().lastSeenAt < cutoff && !CHECKS.containsKey(e.getKey()));
    }
    private static void trim() {
        while (ENTRIES.size() > LIMIT) ENTRIES.remove(ENTRIES.keySet().iterator().next());
    }
    public static synchronized List<Entry> snapshot() {
        prune(); configured(ReleaseAccess.ORIGIN, "access_configured");
        // Explicit endpoints in AlltechProxySource, ConnectionsManager, AndroidUtilities,
        // LocationController, Browser and AppUpdatesActivity. Configured does not mean used.
        configured("https://todosobreall.tech/", "alltech");
        configured("https://www.google.com/", "dns");
        configured("https://dns.google.com/", "dns");
        configured("https://mozilla.cloudflare-dns.com/", "dns");
        configured("https://static-maps.yandex.ru/", "maps");
        configured("https://maps.googleapis.com/", "maps");
        configured("https://ss3.4sqi.net/", "maps");
        configured("https://t.me/", "telegram_web");
        configured("https://telegram.org/", "telegram_web");
        configured("https://play.google.com/", "store");
        configured("https://cdn.ampproject.org/", "amp_cdn_google");
        configured("https://www.bing-amp.com/", "amp_cdn_bing"); trim();
        return new ArrayList<>(ENTRIES.values());
    }
    /** Clears inventory and invalidates in-flight results. Does not alter connections or proxy settings. */
    public static synchronized void clear() { ENTRIES.clear(); CHECKS.clear(); FAILURES.clear(); COMPLETED.clear(); }
    private static String route() {
        if (TorProxyController.isEnabled()) return "TOR";
        return MessagesController.getGlobalMainSettings().getBoolean("proxy_enabled", false) ? "PROXY_UNSUPPORTED" : "DIRECT";
    }
    private static Entry state(Entry old, Status status, long elapsed, String route) {
        return new Entry(old.host, old.source, old.observed, old.configured, old.lastSeenAt, elapsed, status, route);
    }
    public static FailureReason classifyFailure(Throwable error, boolean online, boolean routeChanged) {
        if (!online) return FailureReason.OFFLINE;
        if (routeChanged) return FailureReason.ROUTE_CHANGED;
        for (int depth = 0; error != null && depth < 8; depth++, error = error.getCause()) {
            if (error instanceof java.net.UnknownHostException) return FailureReason.DNS;
            if (error instanceof javax.net.ssl.SSLException) return FailureReason.TLS;
            if (error instanceof java.net.SocketTimeoutException) return FailureReason.TIMEOUT;
            if (error instanceof java.net.ConnectException) return FailureReason.CONNECTION;
        }
        return FailureReason.UNKNOWN;
    }
    private static void callback(AtomicBoolean cancelled, Utilities.Callback<Entry> callback, Entry entry) {
        if (callback != null) AndroidUtilities.runOnUIThread(() -> { if (!cancelled.get()) callback.run(entry); });
    }
    /** Explicit user diagnostic only. Measures DNS + route/TCP + verified TLS setup to port 443, not ICMP or MTProto ping. */
    public static Runnable check(String host, int account, Utilities.Callback<Entry> callback) {
        AtomicBoolean cancelled = new AtomicBoolean();
        final String chosenRoute = route();
        final Entry initial;
        final Object operation = new Object();
        synchronized (NetworkDomainDiagnostics.class) {
            initial = ENTRIES.get(host);
            if (initial == null || CHECKS.containsKey(host)) return () -> cancelled.set(true);
            CHECKS.put(host, operation);
            ENTRIES.put(host, state(initial, Status.CHECKING, -1, chosenRoute));
        }
        callback(cancelled, callback, state(initial, Status.CHECKING, -1, chosenRoute));
        try {
            WORK.execute(() -> {
                Status status; FailureReason reason = FailureReason.NONE; long elapsed = -1;
                if (!ApplicationLoader.isNetworkOnline()) { status = Status.OFFLINE; reason = FailureReason.OFFLINE; }
                else if ("PROXY_UNSUPPORTED".equals(chosenRoute)) { status = Status.UNSUPPORTED_ROUTE; reason = FailureReason.PROXY_UNSUPPORTED; }
                else {
                    try {
                        if (!chosenRoute.equals(route())) throw new java.io.IOException("Route changed");
                        if (cancelled.get()) return;
                        elapsed = AmpResolver.measureTls(host);
                        status = chosenRoute.equals(route()) ? Status.OK : Status.FAILED;
                        if (status != Status.OK) { elapsed = -1; reason = FailureReason.ROUTE_CHANGED; }
                    } catch (Exception error) {
                        boolean online = ApplicationLoader.isNetworkOnline();
                        status = online ? Status.FAILED : Status.OFFLINE;
                        reason = classifyFailure(error, online, !chosenRoute.equals(route()));
                    }
                }
                Entry result;
                synchronized (NetworkDomainDiagnostics.class) {
                    if (CHECKS.get(host) != operation) return;
                    CHECKS.remove(host);
                    Entry latest = ENTRIES.get(host);
                    if (latest == null) return;
                    result = new Entry(latest.host, latest.source, latest.observed, latest.configured, latest.lastSeenAt, elapsed, status, chosenRoute, reason);
                    ENTRIES.put(host, result);
                    String failureKey = host + ":" + chosenRoute;
                    if (System.currentTimeMillis() - COMPLETED.getOrDefault(failureKey, 0L) > 10 * 60 * 1000L) FAILURES.remove(failureKey);
                    COMPLETED.put(failureKey, System.currentTimeMillis());
                    if (status == Status.FAILED) FAILURES.put(failureKey, Math.min(3, FAILURES.getOrDefault(failureKey, 0) + 1));
                    else FAILURES.remove(failureKey);
                }
                callback(cancelled, callback, result);
            });
        } catch (RejectedExecutionException e) {
            synchronized (NetworkDomainDiagnostics.class) {
                CHECKS.remove(host); ENTRIES.put(host, initial);
            }
            callback(cancelled, callback, initial);
        }
        return () -> {
            cancelled.set(true);
            synchronized (NetworkDomainDiagnostics.class) {
                if (CHECKS.get(host) == operation) {
                    CHECKS.remove(host);
                    Entry latest = ENTRIES.get(host);
                    if (latest != null) ENTRIES.put(host, state(latest, Status.UNTESTED, -1, ""));
                }
            }
        };
    }
    /** Codes for localized UI. Failure is not evidence of censorship: no automatic proxy/Tor activation. */
    public static synchronized String recommendation(int account) {
        if (!ApplicationLoader.isNetworkOnline()) return "offline";
        int state = ConnectionsManager.getInstance(account).getConnectionState();
        if (state == ConnectionsManager.ConnectionStateWaitingForNetwork) return "offline";
        int failed = 0, ok = 0;
        String currentRoute = route();
        for (Entry entry : snapshot()) {
            if (!currentRoute.equals(entry.route) || System.currentTimeMillis() - COMPLETED.getOrDefault(entry.host + ":" + currentRoute, 0L) > 10 * 60 * 1000L) continue;
            if (entry.status == Status.OK) ok++;
            else if (entry.status == Status.FAILED) {
                failed++;
                boolean essential = entry.host.equals("chat.todosobreall.tech") || entry.host.equals("todosobreall.tech")
                        || entry.host.equals("t.me") || entry.host.equals("telegram.org")
                        || entry.host.equals("dns.google.com") || entry.host.equals("www.google.com")
                        || entry.host.equals("mozilla.cloudflare-dns.com");
                if (essential && FAILURES.getOrDefault(entry.host + ":" + currentRoute, 0) >= 2) return "essential_failure";
            }
        }
        if (failed > 1) return "multiple_failures";
        if (failed == 1 && ok > 0) return "isolated_failure";
        if (state == ConnectionsManager.ConnectionStateConnecting || state == ConnectionsManager.ConnectionStateConnectingToProxy) return "telegram_connecting";
        if (ok > 0 && failed == 0) return "healthy";
        return "unknown";
    }
}
