package org.telegram.ui.web;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_iv;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Converts fetched public AMP HTML into the existing native Instant View blocks. */
public final class AmpArticleReader {
    private AmpArticleReader() { }
    private static final ThreadPoolExecutor photos = new ThreadPoolExecutor(0, 2, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(16));

    /** Callback runs on the UI thread; cancellation suppresses it and releases the parser. */
    public static Runnable read(AmpResolver.Result result, Utilities.Callback<WebInstantView> callback) {
        final AtomicBoolean cancelled = new AtomicBoolean();
        final AtomicBoolean delivered = new AtomicBoolean();
        final Utilities.Callback<WebInstantView> complete = value -> {
            if (callback != null && !cancelled.get() && delivered.compareAndSet(false, true)) callback.run(value);
        };
        final Runnable[] stop = new Runnable[1];
        AndroidUtilities.runOnUIThread(() -> {
            if (cancelled.get()) return;
            if (result == null || result.html == null || callback == null) {
                complete.run(null);
                return;
            }
            if (!result.readerAllowed) {
                // Native notice only: never feed access-controlled HTML into Readability.
                complete.run(accessNotice(result));
                return;
            }
            final WebInstantView instant = new WebInstantView();
            instant.url = result.originalUrl;
            instant.photoLoader = (url, done) -> loadPhoto(instant, url, done);
            // The resolver has already decoded the response into a Java String.
            final byte[] html = normalizeHtml(result.html).getBytes(StandardCharsets.UTF_8);
            final ByteArrayInputStream input = new ByteArrayInputStream(html);
            try {
                stop[0] = instant.readHTML(result.contentUrl, input, json -> {
                    if (cancelled.get()) return;
                    WebInstantView ready = null;
                    try {
                        if (json == null || !hasArticleBody(json)) {
                            throw new IllegalArgumentException("No readable article body");
                        }
                        normalizeLinks(json, result.contentUrl, 0, new int[] { 20000 });
                        instant.webpage = instant.parseJSON(result.originalUrl, json);
                        if (instant.webpage.cached_page == null || instant.webpage.cached_page.blocks.isEmpty()) {
                            throw new IllegalArgumentException("No readable article body");
                        }
                        String host = new URI(result.originalUrl).getHost();
                        instant.webpage.site_name = host + " · AMP · " + result.provider;
                        instant.webpage.flags |= 2;
                        if (instant.webpage.author != null && !instant.webpage.author.trim().isEmpty()) {
                            TL_iv.pageBlockAuthorDate author = new TL_iv.pageBlockAuthorDate();
                            author.author = WebInstantView.parseRichText(instant.webpage.author);
                            instant.webpage.cached_page.blocks.add(Math.min(1, instant.webpage.cached_page.blocks.size()), author);
                        }
                        TL_iv.pageBlockFooter source = new TL_iv.pageBlockFooter();
                        source.text = WebInstantView.parseRichText(instant.webpage.site_name + " · " + result.originalUrl);
                        instant.webpage.cached_page.blocks.add(source);
                        // Register before opening the article so native image receivers can resolve photos.
                        WebInstantView.instants.put(instant.webpage, instant);
                        ready = instant;
                    } catch (Exception e) {
                        instant.recycle();
                        FileLog.e(e);
                    }
                    complete.run(ready);
                });
            } catch (Exception e) {
                try { input.close(); } catch (Exception ignored) { }
                instant.recycle();
                FileLog.e(e);
                complete.run(null);
            }
        });
        return () -> {
            cancelled.set(true);
            AndroidUtilities.runOnUIThread(() -> { if (stop[0] != null) stop[0].run(); });
        };
    }

    private static WebInstantView accessNotice(AmpResolver.Result result) {
        WebInstantView instant = new WebInstantView();
        instant.url = result.originalUrl;
        TLRPC.TL_webPage page = new TLRPC.TL_webPage();
        page.url = result.originalUrl;
        page.display_url = result.originalUrl;
        page.site_name = "AMP · " + URI.create(result.ampUrl).getHost();
        page.title = LocaleController.getString(R.string.AmpLocalAccessTitle);
        page.flags = 2 | 4 | 1024;
        page.cached_page = new TL_iv.TL_page();
        page.cached_page.web = true;
        page.cached_page.url = result.originalUrl;
        TL_iv.pageBlockTitle title = new TL_iv.pageBlockTitle();
        title.text = WebInstantView.parseRichText(page.title);
        page.cached_page.blocks.add(title);
        TL_iv.pageBlockParagraph notice = new TL_iv.pageBlockParagraph();
        notice.text = WebInstantView.parseRichText(LocaleController.getString(R.string.AmpPublisherAccess));
        page.cached_page.blocks.add(notice);
        TL_iv.pageBlockParagraph original = new TL_iv.pageBlockParagraph();
        TL_iv.textUrl link = new TL_iv.textUrl();
        link.url = result.ampUrl;
        link.text = WebInstantView.parseRichText(LocaleController.getString(R.string.AmpOpenPublisher));
        original.text = link;
        page.cached_page.blocks.add(original);
        instant.webpage = page;
        WebInstantView.instants.put(page, instant);
        return instant;
    }

    private static void loadPhoto(WebInstantView instant, String url, Utilities.Callback<Bitmap> done) {
        if (instant.isRecycled() || AmpUrls.normalize(url) == null) { done.run(null); return; }
        try {
            photos.execute(() -> {
                Bitmap bitmap = null;
                try {
                    if (!instant.isRecycled()) {
                        byte[] bytes = AmpResolver.fetchImage(url);
                        BitmapFactory.Options bounds = new BitmapFactory.Options();
                        bounds.inJustDecodeBounds = true;
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
                        if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                            BitmapFactory.Options options = new BitmapFactory.Options();
                            options.inSampleSize = 1;
                            while (Math.max(bounds.outWidth, bounds.outHeight) / options.inSampleSize > 1280) options.inSampleSize *= 2;
                            bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
                        }
                    }
                } catch (Exception ignored) { }
                final Bitmap result = bitmap;
                AndroidUtilities.runOnUIThread(() -> {
                    if (instant.isRecycled()) { AndroidUtilities.recycleBitmap(result); done.run(null); }
                    else done.run(result);
                });
            });
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
            done.run(null);
        }
    }

    static String normalizeHtml(String html) {
        // Only rename AMP's image element; the bundled DOMPurify sanitizes all attributes/scripts later.
        // Do not strip paywall/login nodes or fetch additional content.
        return html.replaceAll("(?i)<(/?)amp-img(?=[\\s/>])", "<$1img")
            .replaceAll("(?is)<meta\\b[^>]*charset[^>]*>", "")
            .replaceAll("(?is)<meta\\b[^>]*http-equiv\\s*=\\s*(['\\\"]?)content-type\\1[^>]*>", "");
    }

    private static boolean hasArticleBody(JSONObject json) {
        JSONArray content = json.optJSONArray("content");
        return content != null && content.length() > 0;
    }

    static void normalizeLinks(Object value, String base, int depth, int[] budget) throws Exception {
        if (depth > 128 || --budget[0] < 0) throw new IllegalArgumentException("Article structure exceeds limits");
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = array.length() - 1; i >= 0; i--) {
                Object child = array.opt(i);
                normalizeLinks(child, base, depth + 1, budget);
                if (child instanceof JSONObject) {
                    JSONObject node = (JSONObject) child;
                    String tag = node.optString("tag");
                    if ("iframe".equals(tag) || "script".equals(tag) || "object".equals(tag) || "embed".equals(tag)
                            || ("img".equals(tag) && node.optString("src").isEmpty())) array.remove(i);
                }
            }
        } else if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            for (String key : new String[] { "href", "src", "poster" }) {
                if (!object.has(key)) continue;
                String safe = safeUrl(base, object.optString(key));
                if (safe == null) {
                    object.remove(key);
                    if ("href".equals(key)) object.put("tag", "span");
                } else object.put(key, safe);
            }
            // A canonical sanitized src is sufficient; drop unparsed srcset alternatives.
            object.remove("srcset");
            normalizeLinks(object.opt("content"), base, depth + 1, budget);
        }
    }

    private static String safeUrl(String base, String target) {
        try {
            if (target == null || target.trim().isEmpty()) return null;
            URI uri = new URI(base).resolve(target.trim());
            String scheme = uri.getScheme();
            if ("https".equalsIgnoreCase(scheme)) return AmpUrls.normalize(uri.toASCIIString());
        } catch (Exception ignored) { }
        return null;
    }
}
