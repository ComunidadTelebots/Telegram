import org.telegram.ui.web.AmpUrls;

public class AmpUrlsTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        check(AmpUrls.isEligible("https://example.com/article?q=ampproject.org"), "Query is not hostname");
        check(AmpUrls.isEligible("HTTPS://example.com/article"), "Scheme is case insensitive");
        for (String url : new String[] {null, "", "https://", "http://example.com", "file:///tmp/a",
                "https://user:pass@example.com/a", "https://example.com:8443/a", "https://[::1]/a",
                "https://example.com/bad path", "https://example-com.cdn.ampproject.org/c/s/example.com/a"}) {
            check(!AmpUrls.isEligible(url), "Ineligible URL accepted: " + url);
        }
        check(AmpUrls.isCacheHost("EXAMPLE.cdn.ampproject.org"), "Case insensitive cache host");
        check(!AmpUrls.isCacheHost("example.amp.cloudflare.com"), "Retired Cloudflare is not a supported cache");
        check(AmpUrls.isCacheHost("example-com.www.bing-amp.com"), "Bing cache host");
        for (String host : new String[] {null, "evilampproject.org", "ampproject.org.evil.com",
                "evilamp.cloudflare.com", "amp.cloudflare.com.evil.com"}) {
            check(!AmpUrls.isCacheHost(host), "Spoofed cache host accepted: " + host);
        }
        for (String url : new String[] {"https://127.0.0.1/a", "https://2130706433/a", "https://0x7f000001/a", "https://127.1/a", "https://host.local/a", "https://host.internal/a", "https://localhost/a", "https://a.com./a", "https://a.com%0d/a"}) {
            check(!AmpUrls.isEligible(url), "Private/ambiguous host: " + url);
        }
        check("https://example-com.cdn.ampproject.org/c/s/example.com/a?q=x".equals(AmpUrls.cacheUrl("https://example.com/a?q=x#f", "Google")), "Google cache path");
        check("https://0-en--us-example-com-0.www.bing-amp.com/c/s/en-us.example.com/".equals(AmpUrls.cacheUrl("https://en-us.example.com/", "Bing")), "Bing escaped label");
        check(AmpUrls.cacheUrl("https://example.com/a", "Cloudflare") == null, "No retired cache");
        check(AmpUrls.cacheUrl("https://" + "a".repeat(60) + ".example.com/", "Google") == null, "Long unsupported label rejected");
        check(AmpUrls.normalize("https://bücher.de/a") != null, "IDN normalized");
        System.out.println("AMP URL regression tests passed");
    }
}
