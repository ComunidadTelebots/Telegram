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
        check(AmpUrls.isCacheHost("example.amp.cloudflare.com"), "Cloudflare cache host");
        for (String host : new String[] {null, "evilampproject.org", "ampproject.org.evil.com",
                "evilamp.cloudflare.com", "amp.cloudflare.com.evil.com"}) {
            check(!AmpUrls.isCacheHost(host), "Spoofed cache host accepted: " + host);
        }
        System.out.println("AMP URL regression tests passed");
    }
}
