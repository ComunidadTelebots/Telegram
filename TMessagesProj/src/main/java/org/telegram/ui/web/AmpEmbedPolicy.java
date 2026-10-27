package org.telegram.ui.web;

import java.net.URI;
import java.util.Locale;

/** Deliberately bounded interactive providers; no arbitrary page or local-network embeds. */
public final class AmpEmbedPolicy {
    private AmpEmbedPolicy() { }
    public static boolean allows(String url) {
        try {
            URI uri = new URI(url);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443)) return false;
            String host = uri.getHost();
            if (host == null) return false;
            host = host.toLowerCase(Locale.ROOT);
            return host.equals("datawrapper.dwcdn.net") || host.equals("static.dwcdn.net")
                    || host.equals("flo.uri.sh") || host.equals("public.flourish.studio");
        } catch (Exception ignored) { return false; }
    }
}
