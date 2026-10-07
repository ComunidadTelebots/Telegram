package org.telegram.utils.proxy;

import org.telegram.messenger.MessagesController;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;

/** Local availability evidence, never a certificate of trust or a ban verdict. */
public final class ProxyReliability {
    private static final LinkedHashMap<String, Sample> samples = new LinkedHashMap<>();
    private static final long EXPIRY = 30 * 60 * 1000L;
    private static class Sample { int good, bad, consecutive; long at; }
    public static String key(ProxySettings p) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String part : new String[]{p.getType().name(), p.getAddress(), "" + p.getPort(), p.getUser(), p.getPassword(), p.getSecret()}) {
                digest.update(part.getBytes(StandardCharsets.UTF_8)); digest.update((byte) 0);
            }
            StringBuilder out = new StringBuilder();
            for (byte b : digest.digest()) out.append(String.format(java.util.Locale.US, "%02x", b & 255));
            return out.toString();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    public static boolean excluded(ProxySettings p) {
        return MessagesController.getGlobalMainSettings().getBoolean("proxy_excluded_" + key(p), false);
    }
    public static void exclude(ProxySettings p, boolean value) {
        MessagesController.getGlobalMainSettings().edit().putBoolean("proxy_excluded_" + key(p), value).apply();
    }
    public static synchronized void record(ProxySettings p, boolean good) {
        String key = key(p); long now = System.currentTimeMillis();
        Sample s = samples.get(key);
        if (s == null || now - s.at > EXPIRY) { s = new Sample(); samples.put(key, s); }
        // Bound evidence so an old history cannot overwhelm recent results.
        if (s.good + s.bad >= 20) { s.good /= 2; s.bad /= 2; }
        if (good) { s.good++; s.consecutive = 0; } else { s.bad++; s.consecutive++; }
        s.at = now;
        while (samples.size() > 256) samples.remove(samples.keySet().iterator().next());
    }
    /** 0 unknown, 1 responding, 2 repeatedly responding, 3 unstable, 4 repeated failures. */
    public static synchronized int rating(ProxySettings p) {
        Sample s = samples.get(key(p));
        if (s == null || System.currentTimeMillis() - s.at > EXPIRY) return 0;
        if (s.consecutive >= 3) return 4;
        if (s.bad > 0) return 3;
        return s.good >= 3 ? 2 : 1;
    }
    public static boolean eligible(ProxySettings p) { return !excluded(p) && rating(p) != 4; }
}
