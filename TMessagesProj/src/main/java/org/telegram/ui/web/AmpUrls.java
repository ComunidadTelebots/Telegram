package org.telegram.ui.web;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/** URL validation shared by AMP eligibility and WebView navigation. */
public final class AmpUrls {
    private AmpUrls() {}

    public static boolean isCacheHost(String host) {
        if (host == null) return false;
        host = host.toLowerCase(Locale.ROOT);
        return host.equals("ampproject.org") || host.endsWith(".ampproject.org")
                || host.equals("amp.cloudflare.com") || host.endsWith(".amp.cloudflare.com");
    }

    public static boolean isEligible(String url) {
        if (url == null) return false;
        try {
            URI uri = new URI(url);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                    && uri.getRawUserInfo() == null && uri.getPort() == -1
                    && !uri.getHost().contains(":") && !isCacheHost(uri.getHost());
        } catch (URISyntaxException e) {
            return false;
        }
    }
}
