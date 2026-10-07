package org.telegram.ui.web;

import java.net.IDN;
import java.net.InetAddress;
import java.net.URI;
import java.util.Locale;

/** Strict public HTTPS URLs; this is eligibility, never evidence that a page has AMP. */
public final class AmpUrls {
    private AmpUrls() {}

    public static boolean isCacheHost(String host) {
        if (host == null) return false;
        host = host.toLowerCase(Locale.ROOT);
        return host.equals("cdn.ampproject.org") || host.endsWith(".cdn.ampproject.org")
                || host.equals("www.bing-amp.com") || host.endsWith(".www.bing-amp.com");
    }

    public static String normalize(String value) {
        if (value == null || value.length() > 8192) return null;
        try {
            URI uri = new URI(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getRawUserInfo() != null
                    || uri.getRawAuthority() == null || uri.getRawAuthority().contains("@")
                    || uri.getRawAuthority().contains(":") || uri.getRawAuthority().contains("%")) return null;
            String host = IDN.toASCII(uri.getRawAuthority(), IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
            if (!publicHost(host)) return null;
            String path = uri.getRawPath();
            if (path == null || path.isEmpty()) path = "/";
            return new URI("https://" + host + path + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery())).toASCIIString();
        } catch (Exception e) { return null; }
    }

    public static boolean isEligible(String url) {
        String normalized = normalize(url);
        return normalized != null && !isCacheHost(URI.create(normalized).getHost());
    }

    private static boolean publicHost(String host) {
        if (host.length() > 253 || !host.contains(".") || host.endsWith(".")) return false;
        for (String suffix : new String[] {".localhost", ".local", ".internal", ".lan", ".home", ".onion", ".test", ".invalid", ".example"}) {
            if (host.endsWith(suffix)) return false;
        }
        // Reject all numeric IP forms, including legacy octal/hex/short notation.
        String last = host.substring(host.lastIndexOf('.') + 1);
        if (!last.matches("[a-z][a-z0-9-]*") || host.matches("[0-9xXa-fA-F.]+")) return false;
        for (String label : host.split("\\.")) {
            if (!label.matches("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?")) return false;
        }
        return true;
    }

    public static boolean isPublicAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) return false;
        byte[] b = address.getAddress();
        int a = b[0] & 255, c = b[1] & 255;
        if (b.length == 4) {
            return a != 0 && a != 10 && a != 127 && a < 224
                    && !(a == 100 && c >= 64 && c <= 127) && !(a == 169 && c == 254)
                    && !(a == 172 && c >= 16 && c <= 31) && !(a == 192 && (c == 168 || c == 0 || c == 2))
                    && !(a == 198 && (c == 18 || c == 19 || c == 51)) && !(a == 203 && c == 0);
        }
        // Conservative global unicast only; reject transition and documentation ranges.
        return b.length == 16 && (a & 0xe0) == 0x20
                && !(a == 0x20 && c == 0x02)
                && !(a == 0x20 && c == 0x01 && ((b[2] & 255) < 2 || ((b[2] & 255) == 0x0d && (b[3] & 255) == 0xb8)));
    }

    public static String cacheUrl(String url, String provider) {
        String normalized = normalize(url);
        if (normalized == null || !isEligible(normalized)) return null;
        String suffix = "Google".equals(provider) ? "cdn.ampproject.org" : "Bing".equals(provider) ? "www.bing-amp.com" : null;
        if (suffix == null) return null; // Cloudflare AMP was retired.
        try {
            URI uri = URI.create(normalized);
            String prefix = IDN.toUnicode(uri.getHost()).replace("-", "--").replace(".", "-");
            if (prefix.length() >= 4 && prefix.charAt(2) == '-' && prefix.charAt(3) == '-') prefix = "0-" + prefix + "-0";
            prefix = IDN.toASCII(prefix, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
            // Unsupported (long/bidi) publisher labels fall back to origin, never invent a cache URL.
            if (prefix.length() > 63 || !prefix.matches("[a-z0-9](?:[a-z0-9-]*[a-z0-9])?")) return null;
            return "https://" + prefix + "." + suffix + "/c/s/" + uri.getHost() + uri.getRawPath()
                    + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
        } catch (Exception e) { return null; }
    }
}
