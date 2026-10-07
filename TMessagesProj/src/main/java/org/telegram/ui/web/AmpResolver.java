package org.telegram.ui.web;

import android.os.Build;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.MessagesController;
import org.telegram.utils.proxy.NetworkDomainDiagnostics;
import org.telegram.utils.proxy.TorProxyController;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;
import javax.net.ssl.*;

/** Opt-in discovery/fetch. No cookies, credentials, JavaScript, private hosts or cleartext. */
public final class AmpResolver {
    public static final class Result {
        public final String originalUrl, ampUrl, contentUrl, html, provider;
        public final boolean readerAllowed;
        public Result(String originalUrl, String ampUrl, String contentUrl, String html, String provider) {
            this.originalUrl = originalUrl; this.ampUrl = ampUrl; this.contentUrl = contentUrl;
            this.html = html; this.provider = provider;
            this.readerAllowed = isAmpHtml(html);
        }
    }
    private static final int LIMIT = 1024 * 1024, TIMEOUT = 10000, MAX_CACHE = 8;
    private static final long TTL = 5 * 60 * 1000L;
    private static final ThreadPoolExecutor WORK = new ThreadPoolExecutor(2, 2, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(16), runnable -> { Thread t = new Thread(runnable, "AMP-fetch"); t.setDaemon(true); return t; });
    private static final ScheduledExecutorService TIMER = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread t = new Thread(runnable, "AMP-timeout"); t.setDaemon(true); return t;
    });
    private static final Map<String, Entry> CACHE = new LinkedHashMap<>();
    private static final Map<String, List<Utilities.Callback<Result>>> PENDING = new HashMap<>();
    private static final Pattern TAG = Pattern.compile("<([a-zA-Z][a-zA-Z0-9:-]*)\\b((?:[^>\"']++|\"[^\"]*\"|'[^']*')*+)>");
    private static final Pattern ATTR = Pattern.compile("([^\\s=<>/]+)(?:\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+)))?");
    private static final class Entry {
        final Result result; final long at = now(); final boolean resolved;
        Entry(Result result, boolean resolved) { this.result = result; this.resolved = resolved; }
    }
    private AmpResolver() {}
    private static long now() { return System.nanoTime() / 1000000L; }

    public static synchronized Result peek(String url) {
        Entry entry = CACHE.get(AmpUrls.normalize(url));
        return entry != null && now() - entry.at < TTL ? entry.result : null;
    }
    public static void discover(String url, Utilities.Callback<Result> callback) { submit(url, callback, false); }
    public static void resolve(String url, Utilities.Callback<Result> callback) { submit(url, callback, true); }

    private static void submit(String url, Utilities.Callback<Result> callback, boolean providers) {
        String normalized = AmpUrls.normalize(url);
        if (!AmpUrls.isEligible(normalized)) { deliver(callback, null); return; }
        String key = (providers ? "resolve:" : "discover:") + normalized;
        synchronized (AmpResolver.class) {
            Entry entry = CACHE.get(normalized);
            if (entry != null && now() - entry.at < TTL && (!providers || entry.resolved || entry.result == null)) {
                deliver(callback, entry.result); return;
            }
            List<Utilities.Callback<Result>> waiting = PENDING.get(key);
            if (waiting != null) { if (waiting.size() < 32) waiting.add(callback); else deliver(callback, null); return; }
            waiting = new ArrayList<>(); waiting.add(callback); PENDING.put(key, waiting);
        }
        try {
            WORK.execute(() -> {
                Result result = null;
                try {
                    Route route = new Route();
                    Result known = peek(normalized);
                    result = known == null ? discoverOrigin(normalized, route) : known;
                    if (result != null && providers) result = providers(result, route);
                } catch (Exception ignored) { /* Never log URLs or response bodies. */ }
                complete(key, normalized, result, providers);
            });
        } catch (RejectedExecutionException e) { complete(key, normalized, null, providers); }
    }
    private static void complete(String key, String url, Result result, boolean resolved) {
        List<Utilities.Callback<Result>> waiting;
        synchronized (AmpResolver.class) {
            CACHE.remove(url); CACHE.put(url, new Entry(result, resolved));
            while (CACHE.size() > MAX_CACHE) CACHE.remove(CACHE.keySet().iterator().next());
            waiting = PENDING.remove(key);
        }
        if (waiting != null) for (Utilities.Callback<Result> callback : waiting) deliver(callback, result);
    }
    private static void deliver(Utilities.Callback<Result> callback, Result result) {
        if (callback != null) AndroidUtilities.runOnUIThread(() -> callback.run(result));
    }
    private static Result discoverOrigin(String original, Route route) throws IOException {
        Page origin = fetch(original, route);
        if (isAmpDocument(origin.html)) return new Result(original, origin.url, origin.url, origin.html, "Origen");
        String amp = discoverAmpUrl(origin.url, origin.html);
        if (amp == null) return null;
        Page page = fetch(amp, route);
        return isAmpDocument(page.html) ? new Result(original, page.url, page.url, page.html, "Origen") : null;
    }
    private static Result providers(Result origin, Route route) throws IOException {
        if (!origin.readerAllowed) return origin;
        for (String provider : new String[] {"Google", "Bing"}) {
            String url = AmpUrls.cacheUrl(origin.ampUrl, provider);
            if (url == null) continue;
            try {
                Page page = fetch(url, route);
                // A cache redirecting to another site is not labelled as that provider.
                if (isAmpHtml(page.html) && URI.create(page.url).getHost().equals(URI.create(url).getHost()))
                    return new Result(origin.originalUrl, origin.ampUrl, page.url, page.html, provider);
            } catch (IOException ignored) { route.check(); }
        }
        route.check();
        return origin;
    }

    private static Map<String, String> attributes(String source) {
        Map<String, String> result = new HashMap<>();
        Matcher matcher = ATTR.matcher(source);
        while (matcher.find()) {
            String value = matcher.group(2) != null ? matcher.group(2) : matcher.group(3) != null ? matcher.group(3) : matcher.group(4);
            result.put(matcher.group(1).toLowerCase(Locale.ROOT), value == null ? "" : entities(value));
        }
        return result;
    }
    private static String entities(String value) {
        return value.replace("&amp;", "&").replace("&#38;", "&").replace("&#x26;", "&")
                .replace("&quot;", "\"").replace("&#39;", "'");
    }
    private static String markup(String html) {
        return html.replaceAll("(?s)<!--.*?(?:-->|$)", "")
                .replaceAll("(?is)<(script|style|textarea|title)\\b[^>]*>.*?</\\1\\s*>", "");
    }
    static String discoverAmpUrl(String base, String html) {
        Matcher tags = TAG.matcher(markup(html));
        while (tags.find()) {
            if (!"link".equalsIgnoreCase(tags.group(1))) continue;
            Map<String, String> a = attributes(tags.group(2));
            String rel = a.get("rel"), href = a.get("href");
            if (rel != null && href != null && Arrays.asList(rel.toLowerCase(Locale.ROOT).split("\\s+")).contains("amphtml")) {
                try {
                    String url = AmpUrls.normalize(URI.create(base).resolve(href).toString());
                    // Follow the publisher's explicit AMP link (including an AMP subdomain), never guess a cache URL.
                    return AmpUrls.isEligible(url) ? url : null;
                } catch (Exception ignored) { return null; }
            }
        }
        return null;
    }
    static boolean isAmpHtml(String html) {
        return isAmpMarkup(html, true);
    }
    static boolean isAmpDocument(String html) {
        return isAmpMarkup(html, false);
    }
    private static boolean isAmpMarkup(String html, boolean enforceAccess) {
        if (html == null || html.length() > LIMIT) return false;
        // Do not turn access-controlled AMP documents into an unrestricted reader.
        String lower = html.toLowerCase(Locale.ROOT);
        if (enforceAccess && Pattern.compile("[\"']isaccessibleforfree[\"']\\s*:\\s*(?:false|[\"']false[\"'])").matcher(lower).find()) return false;
        Matcher tags = TAG.matcher(markup(html));
        boolean root = false, body = false;
        while (tags.find()) {
            Map<String, String> attrs = attributes(tags.group(2));
            for (String attribute : attrs.keySet()) {
                if (enforceAccess && (attribute.equals("amp-access") || attribute.startsWith("amp-access-")
                        || attribute.equals("subscriptions-section") || attribute.startsWith("amp-subscriptions"))) return false;
            }
            if ("html".equalsIgnoreCase(tags.group(1))) {
                root = attrs.containsKey("amp") || attrs.containsKey("⚡");
            }
            if ("body".equalsIgnoreCase(tags.group(1))) body = true;
        }
        // Cache transformations may use unquoted src and a versioned /rtv/<id>/ runtime.
        boolean runtime = false;
        Matcher scripts = TAG.matcher(html.replaceAll("(?s)<!--.*?(?:-->|$)", ""));
        while (scripts.find()) {
            if (!"script".equalsIgnoreCase(scripts.group(1))) continue;
            Map<String, String> attrs = attributes(scripts.group(2));
            if (enforceAccess && ("amp-access".equals(attrs.get("custom-element")) || "amp-subscriptions".equals(attrs.get("custom-element")))) return false;
            String src = attrs.get("src");
            if (src != null && src.matches("https://cdn\\.ampproject\\.org/(?:rtv/[0-9]+/)?v0\\.m?js(?:[?][^#]*)?")) runtime = true;
        }
        // Detect an AMP document, not full AMP specification conformance.
        return root && body && runtime;
    }

    /** Diagnostic latency: DNS + route/TCP + TLS handshake. No HTTP path/query/body is sent. */
    public static long measureTls(String host) throws IOException {
        String safe = AmpUrls.normalize("https://" + host + "/");
        if (safe == null || !URI.create(safe).getHost().equals(host)) throw new IOException("Unsafe hostname");
        Route route = new Route();
        long start = now();
        diagnosticRoute(route);
        InetAddress address = resolveAddress(host, route);
        if (!AmpUrls.isPublicAddress(address)) throw new IOException("Private address");
        diagnosticRoute(route);
        try (Socket plain = connect(address, route)) {
            ScheduledFuture<?> timeout = TIMER.schedule(() -> close(plain), TIMEOUT, TimeUnit.MILLISECONDS);
            try (SSLSocket tls = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault()).createSocket(plain, host, 443, true)) {
                tls.setSoTimeout(TIMEOUT);
                if (Build.VERSION.SDK_INT >= 24) {
                    SSLParameters parameters = tls.getSSLParameters();
                    parameters.setEndpointIdentificationAlgorithm("HTTPS"); tls.setSSLParameters(parameters);
                }
                tls.startHandshake();
                if (Build.VERSION.SDK_INT < 24 && !HttpsURLConnection.getDefaultHostnameVerifier().verify(host, tls.getSession())) throw new IOException("TLS hostname");
                diagnosticRoute(route);
                return now() - start;
            } finally { timeout.cancel(false); }
        }
    }
    private static void diagnosticRoute(Route route) throws IOException {
        route.check();
        if (!route.tor && MessagesController.getGlobalMainSettings().getBoolean("proxy_enabled", false))
            throw new IOException("Selected proxy does not expose a general HTTPS route");
    }

    /** Bounded public catalog fetch. No credentials, cookies or selected-proxy bypass. */
    public static String fetchPublicText(String url) throws IOException {
        return fetch(url, new Route(true), false, true).html;
    }

    /** Approved community catalog only; never follow redirects out of its configured endpoint. */
    public static String fetchCommunityThemeText(String url, String catalogBase) throws IOException {
        if (!CommunityThemePalette.isCatalogUrl(url, catalogBase)) throw new IOException("Theme catalog URL");
        return fetch(url, new Route(true), false, true, false, true).html;
    }

    /** RFC 8484 diagnostic request, with the same bounded, route-aware HTTPS transport. */
    public static byte[] fetchDnsMessage(String url) throws IOException {
        return fetch(url, new Route(true, false), false, false, true).bytes;
    }

    /** Fixed-port DNS over TLS (RFC 7858), with public address pinning and Tor routing. */
    public static byte[] fetchDnsTls(String host, byte[] query) throws IOException {
        String safe = AmpUrls.normalize("https://" + host + "/");
        if (safe == null || !URI.create(safe).getHost().equals(host) || query == null || query.length > 4096) throw new IOException("DNS input");
        Route route = new Route(true, false); route.check();
        InetAddress address = resolveAddress(host, route);
        if (!AmpUrls.isPublicAddress(address)) throw new IOException("Private address");
        NetworkDomainDiagnostics.observe(safe, "dns_tls_probe");
        try (Socket plain = connect(address, route, 853)) {
            ScheduledFuture<?> timeout = TIMER.schedule(() -> close(plain), TIMEOUT, TimeUnit.MILLISECONDS);
            try (SSLSocket tls = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault()).createSocket(plain, host, 853, true)) {
                tls.setSoTimeout(TIMEOUT);
                if (Build.VERSION.SDK_INT >= 24) {
                    SSLParameters parameters = tls.getSSLParameters();
                    parameters.setEndpointIdentificationAlgorithm("HTTPS"); tls.setSSLParameters(parameters);
                }
                tls.startHandshake();
                if (Build.VERSION.SDK_INT < 24 && !HttpsURLConnection.getDefaultHostnameVerifier().verify(host, tls.getSession())) throw new IOException("TLS hostname");
                route.check();
                java.io.DataOutputStream out = new java.io.DataOutputStream(tls.getOutputStream());
                out.writeShort(query.length); out.write(query); out.flush();
                DataInputStream in = new DataInputStream(tls.getInputStream());
                int length = in.readUnsignedShort();
                if (length < 12 || length > 4096) throw new IOException("DNS message size");
                byte[] response = new byte[length]; in.readFully(response); route.check(); return response;
            } finally { timeout.cancel(false); }
        }
    }

    public static byte[] fetchImage(String url) throws IOException {
        return fetch(url, new Route(), true).bytes;
    }
    private static final class Page {
        final String url, html;
        final byte[] bytes;
        Page(String url, byte[] bytes, boolean image) { this.url = url; this.bytes = bytes; this.html = image ? null : new String(bytes, StandardCharsets.UTF_8); }
    }
    private static final class Route {
        final boolean respectSelectedProxy;
        Route() { this(false); }
        final boolean useSelectedDns;
        final String dnsPolicy = org.telegram.utils.proxy.DnsConfig.policy();
        Route(boolean respectSelectedProxy) { this(respectSelectedProxy, true); }
        Route(boolean respectSelectedProxy, boolean useSelectedDns) { this.respectSelectedProxy = respectSelectedProxy; this.useSelectedDns = useSelectedDns; }
        final boolean tor = TorProxyController.isEnabled();
        final int port = tor ? TorProxyController.getProxy().getPort() : 0;
        final long deadline = now() + 45000;
        void check() throws IOException {
            if (useSelectedDns && !dnsPolicy.equals(org.telegram.utils.proxy.DnsConfig.policy())) throw new IOException("DNS selection changed");
            if (respectSelectedProxy && !tor && MessagesController.getGlobalMainSettings().getBoolean("proxy_enabled", false)) throw new IOException("Selected proxy has no general HTTPS route");
            if (now() >= deadline || tor != TorProxyController.isEnabled()
                    || (tor && (port <= 9 || port != TorProxyController.getProxy().getPort()))) throw new IOException("Route unavailable");
        }
    }
    private static Page fetch(String initial, Route route) throws IOException { return fetch(initial, route, false); }
    private static Page fetch(String initial, Route route, boolean image) throws IOException { return fetch(initial, route, image, false); }
    private static Page fetch(String initial, Route route, boolean image, boolean publicText) throws IOException {
        return fetch(initial, route, image, publicText, false);
    }
    private static Page fetch(String initial, Route route, boolean image, boolean publicText, boolean dns) throws IOException {
        return fetch(initial, route, image, publicText, dns, false);
    }
    private static Page fetch(String initial, Route route, boolean image, boolean publicText, boolean dns, boolean communityTheme) throws IOException {
        String url = initial;
        for (int redirects = 0; redirects <= 4; redirects++) {
            route.check();
            String safe = AmpUrls.normalize(url);
            if (safe == null) throw new IOException("Unsafe URL");
            URI uri = URI.create(safe);
            NetworkDomainDiagnostics.observe(safe, dns ? "dns_probe" : publicText ? "proxy_catalog" : image ? "amp_image" : "amp_document");
            InetAddress address = resolveAddress(uri.getHost(), route);
            if (!AmpUrls.isPublicAddress(address)) throw new IOException("Private address");
            route.check();
            try (Socket plain = connect(address, route)) {
                ScheduledFuture<?> timeout = TIMER.schedule(() -> close(plain), Math.min(TIMEOUT, route.deadline - now()), TimeUnit.MILLISECONDS);
                try (SSLSocket tls = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault()).createSocket(plain, uri.getHost(), 443, true)) {
                    tls.setSoTimeout(TIMEOUT);
                    if (Build.VERSION.SDK_INT >= 24) {
                        SSLParameters parameters = tls.getSSLParameters();
                        parameters.setEndpointIdentificationAlgorithm("HTTPS");
                        tls.setSSLParameters(parameters);
                    }
                    tls.startHandshake();
                    if (Build.VERSION.SDK_INT < 24 && !HttpsURLConnection.getDefaultHostnameVerifier().verify(uri.getHost(), tls.getSession())) throw new IOException("TLS hostname");
                    route.check();
                    String target = uri.getRawPath() + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
                    tls.getOutputStream().write(("GET " + target + " HTTP/1.1\r\nHost: " + uri.getHost()
                            + "\r\nUser-Agent: Alltech-AMP/1.0\r\nAccept: " + (dns ? "application/dns-message" : image ? "image/png,image/jpeg,image/webp,image/gif" : publicText ? "application/json,text/plain,text/html" : "text/html") + "\r\nAccept-Encoding: identity\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                    BufferedInputStream in = new BufferedInputStream(tls.getInputStream());
                    String status = line(in);
                    if (!status.matches("HTTP/1\\.[01] [0-9]{3}.*")) throw new IOException("HTTP status");
                    int code = Integer.parseInt(status.substring(9, 12));
                    Map<String, String> headers = headers(in);
                    if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                        if (dns || communityTheme) throw new IOException("Redirect rejected");
                        String location = headers.get("location");
                        if (location == null) throw new IOException("Redirect location");
                        try { url = uri.resolve(location).toString(); } catch (Exception e) { throw new IOException("Redirect URL"); }
                        continue;
                    }
                    String mime = headers.getOrDefault("content-type", "").toLowerCase(Locale.ROOT).split(";", 2)[0].trim();
                    if (communityTheme && !"application/json".equals(mime)) throw new IOException("Theme catalog content type");
                    if (code != 200 || (dns ? !"application/dns-message".equals(mime) : image ? !Arrays.asList("image/png", "image/jpeg", "image/webp", "image/gif").contains(mime) : publicText ? !Arrays.asList("application/json", "text/plain", "text/html").contains(mime) : !"text/html".equals(mime))) throw new IOException("Content type");
                    if (headers.containsKey("content-encoding") && !"identity".equalsIgnoreCase(headers.get("content-encoding"))) throw new IOException("Encoding");
                    byte[] bytes = body(in, headers);
                    route.check();
                    return new Page(safe, bytes, image);
                } finally { timeout.cancel(false); }
            }
        }
        throw new IOException("Redirect limit");
    }
    private static void close(Socket socket) { try { socket.close(); } catch (IOException ignored) {} }
    private static InetAddress resolveAddress(String host, Route route) throws IOException {
        if (route.tor) {
            // Tor RESOLVE extension (0xF0): DNS is performed by the exit, never locally.
            try (Socket socket = socks(route)) {
                byte[] name = host.getBytes(StandardCharsets.US_ASCII);
                ByteArrayOutputStream request = new ByteArrayOutputStream();
                request.write(new byte[] {5, (byte) 0xf0, 0, 3, (byte) name.length}); request.write(name); request.write(new byte[] {0, 0});
                socket.getOutputStream().write(request.toByteArray());
                return socksReply(socket.getInputStream());
            }
        }
        InetAddress[] addresses = route.useSelectedDns && !org.telegram.utils.proxy.DnsConfig.selected().isEmpty()
                ? org.telegram.utils.proxy.DnsConfig.resolve(host) : InetAddress.getAllByName(host);
        if (addresses.length == 0) throw new IOException("No address");
        for (InetAddress address : addresses) if (!AmpUrls.isPublicAddress(address)) throw new IOException("Mixed/private DNS");
        return addresses[0]; // Pin the checked address; TLS still validates the publisher hostname.
    }
    private static Socket socks(Route route) throws IOException {
        route.check();
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), route.port), TIMEOUT);
            socket.setSoTimeout(TIMEOUT);
            socket.getOutputStream().write(new byte[] {5, 1, 0});
            if (socket.getInputStream().read() != 5 || socket.getInputStream().read() != 0) throw new IOException("SOCKS auth");
            return socket;
        } catch (IOException e) { close(socket); throw e; }
    }
    private static InetAddress socksReply(InputStream in) throws IOException {
        if (in.read() != 5 || in.read() != 0 || in.read() != 0) throw new IOException("SOCKS failed");
        int kind = in.read();
        if (kind != 1 && kind != 4) throw new IOException("SOCKS address");
        byte[] address = new byte[kind == 1 ? 4 : 16];
        new DataInputStream(in).readFully(address);
        new DataInputStream(in).readUnsignedShort();
        return InetAddress.getByAddress(address);
    }
    private static Socket connect(InetAddress address, Route route) throws IOException {
        return connect(address, route, 443);
    }
    private static Socket connect(InetAddress address, Route route, int port) throws IOException {
        route.check();
        Socket socket = route.tor ? socks(route) : new Socket();
        try {
            if (route.tor) {
                ByteArrayOutputStream request = new ByteArrayOutputStream();
                request.write(new byte[] {5, 1, 0, (byte) (address.getAddress().length == 4 ? 1 : 4)});
                request.write(address.getAddress()); request.write(new byte[] {(byte) (port >> 8), (byte) port});
                socket.getOutputStream().write(request.toByteArray()); socksReply(socket.getInputStream());
            } else socket.connect(new InetSocketAddress(address, port), TIMEOUT);
            socket.setSoTimeout(TIMEOUT); return socket;
        } catch (IOException e) { close(socket); throw e; }
    }
    static String line(InputStream in) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        for (int i = 0; i < 8192; i++) {
            int c = in.read();
            if (c == -1) throw new EOFException();
            if (c == '\n') return new String(bytes.toByteArray(), StandardCharsets.US_ASCII).replaceFirst("\\r$", "");
            bytes.write(c);
        }
        throw new IOException("Header limit");
    }
    private static Map<String, String> headers(InputStream in) throws IOException {
        Map<String, String> headers = new HashMap<>();
        int total = 0;
        while (true) {
            String line = line(in); total += line.length();
            if (total > 32768) throw new IOException("Headers limit");
            if (line.isEmpty()) return headers;
            int colon = line.indexOf(':');
            if (colon <= 0) throw new IOException("Header");
            String name = line.substring(0, colon).toLowerCase(Locale.ROOT);
            if (headers.put(name, line.substring(colon + 1).trim()) != null
                    && (name.equals("content-length") || name.equals("transfer-encoding") || name.equals("location"))) throw new IOException("Duplicate header");
        }
    }
    static byte[] body(InputStream in, Map<String, String> headers) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String transfer = headers.get("transfer-encoding"), length = headers.get("content-length");
        if (transfer != null && (!"chunked".equalsIgnoreCase(transfer) || length != null)) throw new IOException("HTTP framing");
        if (transfer != null) {
            while (true) {
                String chunk = line(in).split(";", 2)[0].trim();
                long count;
                try { count = Long.parseLong(chunk, 16); } catch (NumberFormatException e) { throw new IOException("Chunk size"); }
                if (count == 0) return out.toByteArray();
                if (count < 0 || count > LIMIT - out.size()) throw new IOException("Body limit");
                copy(in, out, (int) count);
                if (!line(in).isEmpty()) throw new IOException("Chunk framing");
            }
        }
        if (length != null) {
            int count;
            try { count = Integer.parseInt(length); } catch (NumberFormatException e) { throw new IOException("Content length"); }
            if (count < 0 || count > LIMIT) throw new IOException("Body limit");
            copy(in, out, count);
        } else {
            byte[] buffer = new byte[8192]; int read;
            while ((read = in.read(buffer)) != -1) {
                if (out.size() + read > LIMIT) throw new IOException("Body limit");
                out.write(buffer, 0, read);
            }
        }
        return out.toByteArray();
    }
    private static void copy(InputStream in, ByteArrayOutputStream out, int count) throws IOException {
        byte[] buffer = new byte[8192];
        while (count > 0) {
            int read = in.read(buffer, 0, Math.min(count, buffer.length));
            if (read == -1) throw new EOFException();
            out.write(buffer, 0, read); count -= read;
        }
    }
}
