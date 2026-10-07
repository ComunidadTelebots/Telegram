package org.telegram.ui.web;

import java.util.Locale;
import java.util.regex.Pattern;

/** Conservative access screening of a fresh, anonymous public response. */
public final class OfflineArticlePolicy {
    private static final Pattern RESTRICTED = Pattern.compile("[\"']isaccessibleforfree[\"']\\s*:\\s*(?:false|[\"']false[\"'])|type\\s*=\\s*[\"']?password", Pattern.CASE_INSENSITIVE);
    private static final Pattern TAG = Pattern.compile("<[a-z][^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ACCESS_ATTRIBUTE = Pattern.compile("\\s(?:amp-access(?:-[a-z-]+)?|amp-subscriptions(?:-[a-z-]+)?|subscriptions-section)(?:\\s*=|\\s|/?>)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ACCESS_ID = Pattern.compile("\\s(?:id|class)\\s*=\\s*(?:[\"'][^\"']*\\b(?:paywall|amp-access|amp-subscriptions)\\b[^\"']*[\"']|(?:paywall|amp-access|amp-subscriptions)(?:\\s|>))", Pattern.CASE_INSENSITIVE);
    private OfflineArticlePolicy() { }
    public static boolean isPublicDocument(String html) {
        if (html == null || html.length() > 1024 * 1024) return false;
        if (RESTRICTED.matcher(html).find()) return false;
        java.util.regex.Matcher tags = TAG.matcher(html.replaceAll("(?s)<!--.*?(?:-->|$)", ""));
        while (tags.find()) if (ACCESS_ATTRIBUTE.matcher(tags.group()).find() || ACCESS_ID.matcher(tags.group()).find()) return false;
        return true;
    }
}
