package org.telegram.ui.web;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** UI-thread-only, presentation metadata. Never replaces a message's photo/media or sends a message. */
public final class MessageLinkPreview {
    public static final class Result {
        public final String url;
        public final TLRPC.WebPage webpage;
        public final AmpResolver.Result amp;
        Result(String url, TLRPC.WebPage webpage, AmpResolver.Result amp) {
            this.url = url; this.webpage = webpage; this.amp = amp;
        }
    }
    private static final int MAX_CACHE = 48, MAX_PENDING = 12;
    private static final long TTL = 5 * 60 * 1000L;
    private static final Pattern URL = Pattern.compile("https://[^\\s<>\\\"\\u0000-\\u001f]+", Pattern.CASE_INSENSITIVE);
    private static final Map<String, Entry> CACHE = new LinkedHashMap<>();
    private static final Map<String, Pending> PENDING = new LinkedHashMap<>();
    private static final class Entry {
        final Result result; final boolean ampChecked; final long at = now();
        Entry(Result result, boolean ampChecked) { this.result = result; this.ampChecked = ampChecked; }
    }
    private static final class Listener {
        final MessageObject message; final String key; final Utilities.Callback<Result> callback;
        boolean cancelled;
        Listener(MessageObject message, String key, Utilities.Callback<Result> callback) {
            this.message = message; this.key = key; this.callback = callback;
        }
        void deliver(Result result) {
            // Queue even on the UI thread, allowing the caller to install its cancellation handle first.
            AndroidUtilities.runOnUIThread(() -> {
                if (!cancelled && permitted(message) && key.equals(key(message, findUrl(message)))) callback.run(result);
            }, 1);
        }
    }
    private static final class Pending {
        final String key, url; final int account; final long user;
        final ArrayList<Listener> listeners = new ArrayList<>();
        final boolean publicPreview; boolean ampAllowed, fullPageRequested; int requestId; Runnable timeout;
        Pending(String key, String url, MessageObject message, boolean ampAllowed) {
            this.key = key; this.url = url; account = message.currentAccount;
            user = UserConfig.getInstance(account).getClientUserId(); this.ampAllowed = ampAllowed; publicPreview = !DialogObject.isEncryptedDialog(message.getDialogId());
        }
    }
    private MessageLinkPreview() {}
    private static long now() { return System.nanoTime() / 1000000L; }
    private static boolean permitted(MessageObject message) {
        if (message == null || message.deleted || message.messageOwner == null) return false;
        if (DialogObject.isEncryptedDialog(message.getDialogId())
                && MessagesController.getInstance(message.currentAccount).secretWebpagePreview != 1) return false;
        // Do not reveal links hidden by text spoilers, or extend ephemeral media with a separate preview.
        if (!message.isSpoilersRevealed && message.messageOwner.entities != null) {
            for (TLRPC.MessageEntity entity : message.messageOwner.entities)
                if (entity instanceof TLRPC.TL_messageEntitySpoiler) return false;
        }
        return message.messageOwner.media == null || message.messageOwner.media.ttl_seconds == 0;
    }
    /** First explicit HTTPS link; no network or entity mutation. Secret preview preference is enforced. */
    public static String findUrl(MessageObject message) {
        if (!permitted(message)) return null;
        TLRPC.WebPage page = existingPage(message);
        if (page != null && AmpUrls.isEligible(page.url)) return AmpUrls.normalize(page.url);
        String text = message.messageOwner.message;
        if (text == null || text.length() > 65536) return null;
        if (message.messageOwner.entities != null) {
            for (TLRPC.MessageEntity entity : message.messageOwner.entities) {
                String value = null;
                if (entity instanceof TLRPC.TL_messageEntityTextUrl) value = entity.url;
                else if (entity instanceof TLRPC.TL_messageEntityUrl && entity.offset >= 0 && entity.length > 0
                        && entity.offset <= text.length() - entity.length) value = text.substring(entity.offset, entity.offset + entity.length);
                if (AmpUrls.isEligible(value)) return AmpUrls.normalize(value);
            }
        }
        Matcher matcher = URL.matcher(text);
        while (matcher.find()) {
            String value = matcher.group().replaceFirst("[.,;!?)}\\]]+$", "");
            if (AmpUrls.isEligible(value)) return AmpUrls.normalize(value);
        }
        return null;
    }
    private static TLRPC.WebPage existingPage(MessageObject message) {
        return message.messageOwner.media != null && message.messageOwner.media.webpage instanceof TLRPC.TL_webPage
                ? message.messageOwner.media.webpage : null;
    }
    private static String key(MessageObject message, String url) {
        if (url == null) return "";
        return message.currentAccount + ":" + UserConfig.getInstance(message.currentAccount).getClientUserId() + ":"
                + message.getDialogId() + ":" + message.getId() + ":" + message.messageOwner.edit_date + ":"
                + (message.messageOwner.message == null ? 0 : message.messageOwner.message.hashCode()) + ":" + url;
    }
    public static Result peek(MessageObject message) {
        String url = findUrl(message);
        if (url == null) return null;
        Entry entry = CACHE.get(key(message, url));
        if (entry != null && now() - entry.at < TTL) return entry.result;
        TLRPC.WebPage page = existingPage(message);
        return page == null ? null : new Result(url, page, null);
    }
    /**
     * Visible cells only. Callback may receive metadata first, then verified AMP availability.
     * Call the returned cancellation handle on detach/rebind. Automatic AMP is never fetched for secrets.
     */
    public static Runnable request(MessageObject message, boolean visible, boolean allowAmpDiscovery, Utilities.Callback<Result> callback) {
        String url = findUrl(message);
        if (url == null || callback == null) return () -> {};
        String key = key(message, url);
        Listener listener = new Listener(message, key, callback);
        Entry cached = CACHE.get(key);
        if (cached != null && now() - cached.at >= TTL) { CACHE.remove(key); cached = null; }
        boolean ampAllowed = allowAmpDiscovery && org.telegram.utils.proxy.PreviewNetworkPolicy.allowAutomaticAmp() && !DialogObject.isEncryptedDialog(message.getDialogId());
        if (!visible || cached != null && (!ampAllowed || cached.ampChecked)) {
            listener.deliver(cached == null ? peek(message) : cached.result);
            return () -> listener.cancelled = true;
        }
        Pending pending = PENDING.get(key);
        if (pending != null) {
            if (pending.listeners.size() < 16) { pending.listeners.add(listener); pending.ampAllowed |= ampAllowed; }
            return () -> cancel(key, listener);
        }
        if (PENDING.size() >= MAX_PENDING) {
            listener.deliver(peek(message)); return () -> listener.cancelled = true;
        }
        Pending request = new Pending(key, url, message, ampAllowed);
        request.listeners.add(listener); PENDING.put(key, request);
        request.timeout = () -> {
            if (PENDING.get(key) != request) return;
            if (request.requestId != 0) ConnectionsManager.getInstance(request.account).cancelRequest(request.requestId, true);
            finish(request, new Result(url, existingPage(message), null), false);
        };
        AndroidUtilities.runOnUIThread(request.timeout, 55000);
        if (cached != null || existingPage(message) != null) {
            metadata(request, cached != null ? cached.result.webpage : existingPage(message));
        } else {
            TL_account.getWebPagePreview rpc = new TL_account.getWebPagePreview();
            rpc.message = url; // Only the selected URL, never the caption or the rest of a secret message.
            request.requestId = ConnectionsManager.getInstance(request.account).sendRequestTyped(rpc, AndroidUtilities::runOnUIThread, (response, error) -> {
                if (!active(request)) return;
                request.requestId = 0;
                TLRPC.WebPage page = null;
                if (response != null && response.media != null && response.media.webpage instanceof TLRPC.TL_webPage) {
                    MessagesController.getInstance(request.account).putUsers(response.users, false);
                    MessagesController.getInstance(request.account).putChats(response.chats, false);
                    page = response.media.webpage;
                }
                metadata(request, page);
            });
        }
        return () -> cancel(key, listener);
    }
    private static boolean active(Pending request) {
        if (PENDING.get(request.key) != request) return false;
        boolean visible = false;
        for (Listener listener : request.listeners)
            if (!listener.cancelled && permitted(listener.message) && listener.key.equals(key(listener.message, findUrl(listener.message)))) visible = true;
        if (!visible || request.user != UserConfig.getInstance(request.account).getClientUserId()) {
            PENDING.remove(request.key); AndroidUtilities.cancelRunOnUIThread(request.timeout); return false;
        }
        return true;
    }
    private static void metadata(Pending request, TLRPC.WebPage page) {
        if (!active(request)) return;
        if (!request.fullPageRequested && page instanceof TLRPC.TL_webPage && page.cached_page == null && request.publicPreview) {
            request.fullPageRequested = true;
            TLRPC.TL_messages_getWebPage rpc = new TLRPC.TL_messages_getWebPage();
            rpc.url = AmpUrls.isEligible(page.url) ? page.url : request.url;
            rpc.hash = 0;
            request.requestId = ConnectionsManager.getInstance(request.account).sendRequest(rpc, (response, error) -> {
                final TLRPC.WebPage full = response instanceof TLRPC.TL_messages_webPage ? ((TLRPC.TL_messages_webPage) response).webpage : null;
                AndroidUtilities.runOnUIThread(() -> metadata(request, full instanceof TLRPC.TL_webPage ? full : page));
            });
            return;
        }
        Result result = new Result(request.url, page, null);
        if (!request.ampAllowed) { finish(request, result, false); return; }
        put(request.key, result, false);
        for (Listener listener : request.listeners) listener.deliver(result);
        AmpResolver.discover(request.url, amp -> {
            if (active(request)) {
                TLRPC.WebPage display = page;
                if (!(display instanceof TLRPC.TL_webPage) && amp != null) {
                    display = new TLRPC.TL_webPage();
                    display.url = request.url;
                    display.display_url = java.net.URI.create(request.url).getHost();
                    display.site_name = display.display_url;
                }
                finish(request, new Result(request.url, display, amp), true);
            }
        });
    }
    private static void finish(Pending request, Result result, boolean checked) {
        if (!active(request)) return;
        PENDING.remove(request.key); AndroidUtilities.cancelRunOnUIThread(request.timeout);
        put(request.key, result, checked);
        for (Listener listener : request.listeners) listener.deliver(result);
    }
    private static void put(String key, Result result, boolean checked) {
        CACHE.remove(key); CACHE.put(key, new Entry(result, checked));
        while (CACHE.size() > MAX_CACHE) CACHE.remove(CACHE.keySet().iterator().next());
        // AMP includes up to 1 MiB of HTML. Keep at most eight such payloads on low-memory devices.
        int ampPayloads = 0;
        for (Entry entry : CACHE.values()) if (entry.result.amp != null) ampPayloads++;
        java.util.Iterator<Map.Entry<String, Entry>> iterator = CACHE.entrySet().iterator();
        while (ampPayloads > 8 && iterator.hasNext()) {
            if (iterator.next().getValue().result.amp != null) { iterator.remove(); ampPayloads--; }
        }
    }
    private static void cancel(String key, Listener listener) {
        listener.cancelled = true;
        Pending request = PENDING.get(key);
        if (request == null) return;
        request.listeners.remove(listener);
        if (request.listeners.isEmpty()) {
            PENDING.remove(key); AndroidUtilities.cancelRunOnUIThread(request.timeout);
            if (request.requestId != 0) ConnectionsManager.getInstance(request.account).cancelRequest(request.requestId, true);
        }
    }
}
