import org.telegram.ui.web.OfflineArticlePolicy;
public class OfflineArticlePolicyTest {
    private static void reject(String html) { if (OfflineArticlePolicy.isPublicDocument(html)) throw new AssertionError("Accepted restricted document: " + html); }
    public static void main(String[] args) {
        if (!OfflineArticlePolicy.isPublicDocument("<html><body><article>Public article</article></body></html>")) throw new AssertionError("Rejected public article");
        reject(null);reject("<script>{\"isAccessibleForFree\":false}</script>");reject("<script>{'isAccessibleForFree': 'false'}</script>");
        reject("<div amp-access='subscriber'>Private</div>");reject("<div subscriptions-section='content'>Private</div>");
        reject("<input type=password>");reject("<INPUT TYPE = 'PASSWORD'>");reject("<div class=paywall>Subscribe</div>");
        reject(new String(new char[1024*1024+1]));
        if (!OfflineArticlePolicy.isPublicDocument("<article>Documentation about amp-access, amp-subscriptions and paywall implementations.</article>")) throw new AssertionError("Rejected public documentation");
        reject("<script id='amp-access' type='application/json'>{}</script>");
        System.out.println("OfflineArticlePolicyTest: 12 checks passed");
    }
}
