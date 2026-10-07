package org.telegram.utils.proxy;

import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;
import org.telegram.ui.web.AmpResolver;
import org.telegram.ui.web.AmpUrls;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Locale;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/** Public, explicitly requested discovery. Never saves, activates, or exports local proxies. */
public final class AlltechProxySource {
    public static final String SOURCE_URL = "https://todosobreall.tech/proxies";
    public static final String API_URL = "https://todosobreall.tech/hcgi/api/proxies";
    private static final ExecutorService WORK = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean BUSY = new AtomicBoolean();
    public static final class Result {
        public final List<ProxySettings> proxies;
        public final String error;
        public final Map<ProxySettings, String> countries;
        Result(List<ProxySettings> proxies, String error) {
            this(proxies, error, new HashMap<>());
        }
        Result(List<ProxySettings> proxies, String error, Map<ProxySettings, String> countries) {
            this.proxies = Collections.unmodifiableList(proxies); this.error = error;
            this.countries = Collections.unmodifiableMap(countries);
        }
    }
    private AlltechProxySource() {}
    public static Runnable discover(Utilities.Callback<Result> callback) {
        AtomicBoolean cancelled = new AtomicBoolean();
        if (!BUSY.compareAndSet(false, true)) {
            AndroidUtilities.runOnUIThread(() -> { if (!cancelled.get()) callback.run(new Result(new ArrayList<>(), "busy")); });
            return () -> cancelled.set(true);
        }
        WORK.execute(() -> {
            Result result;
            try { result = parseCatalog(AmpResolver.fetchPublicText(API_URL)); }
            catch (Exception e) { result = new Result(new ArrayList<>(), "unavailable"); }
            BUSY.set(false);
            Result delivered = result;
            AndroidUtilities.runOnUIThread(() -> { if (!cancelled.get()) callback.run(delivered); });
        });
        return () -> cancelled.set(true);
    }
    /** Matches the public API schema observed at SOURCE_URL, not arbitrary HTML/JavaScript. */
    public static List<ProxySettings> parse(String json) throws Exception {
        return parseCatalog(json).proxies;
    }
    public static Result parseCatalog(String json) throws Exception {
        if (json == null || json.length() > 1024 * 1024) throw new IllegalArgumentException("Size");
        JSONObject root = new JSONObject(json);
        if (!root.optBoolean("success", false)) throw new IllegalArgumentException("Response");
        JSONArray entries = root.getJSONArray("proxies");
        List<ProxySettings> output = new ArrayList<>();
        Map<ProxySettings, String> countries = new HashMap<>();
        for (int i = 0; i < Math.min(entries.length(), 1000); i++) {
            JSONObject item = entries.optJSONObject(i);
            if (item == null) continue;
            String link = item.optString("link", "");
            if (link.isEmpty()) {
                link = "https://t.me/proxy?server=" + Uri.encode(item.optString("server", ""))
                        + "&port=" + Uri.encode(item.optString("port", ""))
                        + "&secret=" + Uri.encode(item.optString("secret", ""));
            }
            ProxySettings settings = parseLink(link);
            if (settings != null && !output.contains(settings)) {
                output.add(settings);
                String country = item.optString("country", "").trim().toUpperCase(Locale.ROOT);
                if (Arrays.asList(Locale.getISOCountries()).contains(country)) countries.put(settings, country);
            }
            if (output.size() >= 64) break;
        }
        return new Result(output, null, countries);
    }
    public static ProxySettings parseLink(String link) {
        if (link == null || link.length() > 4096) return null;
        try {
            Uri uri = Uri.parse(link.replace("&amp;", "&"));
            String scheme = uri.getScheme(), action;
            if ("tg".equalsIgnoreCase(scheme)) action = uri.getHost();
            else if ("https".equalsIgnoreCase(scheme) && uri.getUserInfo() == null && uri.getPort() == -1
                    && ("t.me".equalsIgnoreCase(uri.getHost()) || "telegram.me".equalsIgnoreCase(uri.getHost()))) {
                action = uri.getPath();
                if (action != null && action.startsWith("/")) action = action.substring(1);
            } else return null;
            if (!("proxy".equals(action) || "socks".equals(action)) || uri.getFragment() != null) return null;
            for (String key : uri.getQueryParameterNames()) if (uri.getQueryParameters(key).size() != 1) return null;
            ProxySettings result = ProxySettings.fromUri(uri);
            if (result == null || !result.isValid() || result.getPort() > 65535 || !publicServer(result.getAddress())) return null;
            if (result.getUser().length() > 255 || result.getPassword().length() > 255) return null;
            if (result.getType() == ProxySettings.Type.MTPROTO) {
                String secret = result.getSecret();
                if (!ProxySecretValidator.isValid(secret)) return null;
            }
            return result;
        } catch (Exception ignored) { return null; }
    }
    private static boolean publicServer(String host) throws Exception {
        // Strict literal IPv4 parsing avoids legacy numeric/hex forms; no DNS during parsing.
        if (host.matches("[0-9]+(?:\\.[0-9]+){3}")) {
            String[] parts = host.split("\\."); byte[] bytes = new byte[4];
            for (int i = 0; i < 4; i++) {
                if (parts[i].length() > 1 && parts[i].startsWith("0")) return false;
                int n = Integer.parseInt(parts[i]); if (n > 255) return false; bytes[i] = (byte) n;
            }
            return AmpUrls.isPublicAddress(InetAddress.getByAddress(bytes));
        }
        return AmpUrls.normalize("https://" + host + "/") != null;
    }
}
