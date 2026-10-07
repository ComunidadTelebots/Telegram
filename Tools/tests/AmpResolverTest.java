package org.telegram.ui.web;

import java.io.*;
import java.net.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.telegram.utils.proxy.TorProxyController;

public final class AmpResolverTest {
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    static byte[] bytes(String s) { return s.getBytes(StandardCharsets.UTF_8); }
    static void fails(Map<String,String> headers, String payload) throws Exception {
        try { AmpResolver.body(new ByteArrayInputStream(bytes(payload)), headers); throw new AssertionError("Accepted malformed framing"); }
        catch (IOException expected) {}
    }
    static String amp = "<!doctype html><html amp><head><script async src=\"https://cdn.ampproject.org/v0.js\"></script></head><body><p>Article</p></body></html>";
    public static void main(String[] args) throws Exception {
        check(AmpResolver.isAmpHtml(amp), "AMP marker and runtime");
        check(AmpResolver.isAmpHtml(amp.replace("<p>", "<p title=\"" + "x".repeat(30000) + "\">")), "Long bounded attribute");
        check(AmpResolver.isAmpHtml(amp.replace("src=\"https://cdn.ampproject.org/v0.js\"", "src=https://cdn.ampproject.org/rtv/012345/v0.mjs")), "Transformed cache runtime");
        check(!AmpResolver.isAmpHtml(amp.replace("<html amp>", "<html>")), "No marker");
        check(!AmpResolver.isAmpHtml("<!--" + amp + "-->"), "Commented markup");
        check(!AmpResolver.isAmpHtml(amp.replace("<p>", "<p amp-access='paid'>")), "Access controlled AMP not reader");
        String protectedAmp = amp.replace("<p>", "<p amp-access='paid'>");
        check(AmpResolver.isAmpDocument(protectedAmp), "Protected AMP remains discoverable for publisher browser");
        check(!new AmpResolver.Result("https://publisher.com/a", "https://publisher.com/amp", "https://publisher.com/amp", protectedAmp, "Origen").readerAllowed, "Protected AMP never reaches extracted reader");
        check(!AmpResolver.isAmpHtml(amp.replace("<p>", "<script type='application/ld+json'>{\"isAccessibleForFree\" : false}</script><p>")), "Paid structured data");
        String base = "https://publisher.com/a";
        check("https://amp.publisher.com/a?x=1&y=2".equals(AmpResolver.discoverAmpUrl(base, "<link href='https://amp.publisher.com/a?x=1&amp;y=2' rel='alternate amphtml'>")), "Cross-host explicit amphtml");
        check("https://publisher.com/amp/a".equals(AmpResolver.discoverAmpUrl(base, "<LINK REL=amphtml HREF=/amp/a>")), "Relative unquoted href");
        check(AmpResolver.discoverAmpUrl(base, "<!--<link rel='amphtml' href='/amp'>-->") == null, "Comment not link");
        check(AmpResolver.discoverAmpUrl(base, "<script>var x=\"<link rel='amphtml' href='/amp'>\"</script>") == null, "Script not link");
        for (String bad : new String[] {"http://publisher.com/a", "https://127.0.0.1/a", "https://publisher-com.cdn.ampproject.org/c/s/publisher.com/a", "javascript:alert(1)"})
            check(AmpResolver.discoverAmpUrl(base, "<link rel=amphtml href='" + bad + "'>") == null, "Unsafe amphtml");
        for (String ip : new String[] {"127.0.0.1", "10.0.0.1", "100.64.0.1", "169.254.169.254", "192.168.1.1", "198.18.0.1", "203.0.113.2", "::1", "fc00::1", "2001:db8::1", "2002:7f00:1::"})
            check(!AmpUrls.isPublicAddress(InetAddress.getByName(ip)), "Private/reserved IP " + ip);
        check(AmpUrls.isPublicAddress(InetAddress.getByName("1.1.1.1")), "Public IPv4");
        check(AmpUrls.isPublicAddress(InetAddress.getByName("2606:4700:4700::1111")), "Public IPv6");
        check(Arrays.equals(bytes("hello"), AmpResolver.body(new ByteArrayInputStream(bytes("5\r\nhello\r\n0\r\n\r\n")), Map.of("transfer-encoding", "chunked"))), "Chunked body");
        fails(Map.of("content-length", "1048577"), "");
        fails(Map.of("content-length", "6"), "short");
        fails(Map.of("transfer-encoding", "chunked", "content-length", "5"), "hello");
        fails(Map.of("transfer-encoding", "chunked"), "100001\r\n");
        fails(Map.of("transfer-encoding", "gzip"), "hello");
        fails(Map.of("transfer-encoding", "chunked"), "-1\r\n");
        try { AmpResolver.fetchImage("http://publisher.com/a"); throw new AssertionError("HTTP accepted"); } catch (IOException expected) {}
        org.telegram.messenger.MessagesController.proxy = true;
        try { AmpResolver.measureTls("amp.dev"); throw new AssertionError("Diagnostic bypassed configured proxy"); } catch (IOException expected) {}
        try { AmpResolver.fetchPublicText("https://amp.dev/"); throw new AssertionError("Catalog bypassed configured proxy"); } catch (IOException expected) {}
        org.telegram.messenger.MessagesController.proxy = false;
        CountDownLatch denied = new CountDownLatch(1);
        AmpResolver.discover("https://127.0.0.1/", result -> { check(result == null, "Private URL callback"); denied.countDown(); });
        check(denied.await(2, TimeUnit.SECONDS), "Callback delivered");
        check(AmpResolver.peek("https://publisher.com/a") == null, "No unverified result cache");
        // Test Tor DNS uses SOCKS domain RESOLVE. A nonexistent public hostname must never need system DNS.
        AtomicReference<Throwable> failure = new AtomicReference<>();
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            TorProxyController.enabled = true; TorProxyController.port = server.getLocalPort();
            Thread tor = new Thread(() -> {
                try (Socket client = server.accept()) {
                    client.setSoTimeout(3000);
                    DataInputStream in = new DataInputStream(client.getInputStream());
                    check(in.readUnsignedByte() == 5 && in.readUnsignedByte() == 1 && in.readUnsignedByte() == 0, "SOCKS greeting");
                    client.getOutputStream().write(new byte[] {5, 0});
                    check(in.readUnsignedByte() == 5 && in.readUnsignedByte() == 240 && in.readUnsignedByte() == 0 && in.readUnsignedByte() == 3, "Tor RESOLVE domain command");
                    byte[] name = new byte[in.readUnsignedByte()]; in.readFully(name); in.readUnsignedShort();
                    check("not-resolved-locally.publisher.com".equals(new String(name, StandardCharsets.US_ASCII)), "Tor hostname");
                    client.getOutputStream().write(new byte[] {5, 0, 0, 1, 1, 1, 1, 1, 0, 0});
                } catch (Throwable e) { failure.set(e); }
            }); tor.setDaemon(true); tor.start();
            Class<?> routeClass = Class.forName("org.telegram.ui.web.AmpResolver$Route");
            Constructor<?> constructor = routeClass.getDeclaredConstructor(); constructor.setAccessible(true);
            Method resolve = AmpResolver.class.getDeclaredMethod("resolveAddress", String.class, routeClass); resolve.setAccessible(true);
            InetAddress address = (InetAddress) resolve.invoke(null, "not-resolved-locally.publisher.com", constructor.newInstance());
            check("1.1.1.1".equals(address.getHostAddress()), "Tor resolved IP");
            tor.join(4000); if (failure.get() != null) throw new AssertionError(failure.get());
        } finally { TorProxyController.enabled = false; }
        System.out.println("AMP discovery, framing, SSRF and Tor DNS tests passed");
        if (args.length > 0 && args[0].equals("--live")) {
            System.out.println("LIVE amp.dev DNS+TCP+TLS ms=" + AmpResolver.measureTls("amp.dev"));
            Class<?> routeClass = Class.forName("org.telegram.ui.web.AmpResolver$Route");
            Constructor<?> constructor = routeClass.getDeclaredConstructor(); constructor.setAccessible(true);
            Method fetch = AmpResolver.class.getDeclaredMethod("fetch", String.class, routeClass); fetch.setAccessible(true);
            String original = "https://amp.dev/documentation/guides-and-tutorials/start/create/basic_markup/";
            for (String url : new String[] {original, AmpUrls.cacheUrl(original, "Google"), AmpUrls.cacheUrl(original, "Bing")}) {
                try {
                    Object page = fetch.invoke(null, url, constructor.newInstance());
                    Field html = page.getClass().getDeclaredField("html"); html.setAccessible(true);
                    System.out.println("LIVE " + URI.create(url).getHost() + " HTTP200 AMP=" + AmpResolver.isAmpHtml((String)html.get(page)));
                } catch (InvocationTargetException e) { System.out.println("LIVE " + URI.create(url).getHost() + " failed " + e.getCause().getClass().getSimpleName() + ": " + e.getCause().getMessage()); }
            }
            CountDownLatch done = new CountDownLatch(1);
            AmpResolver.resolve("https://amp.dev/documentation/guides-and-tutorials/start/create/basic_markup/", result -> {
                System.out.println(result == null ? "LIVE no validated AMP result" : "LIVE validated provider=" + result.provider + " bytes=" + result.html.length()); done.countDown();
            });
            check(done.await(60, TimeUnit.SECONDS), "Live bounded completion");
        }
    }
}
