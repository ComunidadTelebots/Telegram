package org.telegram.utils.proxy;

import android.net.VpnService;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;

/** Authenticated loopback transport for Tor's OR connections only. No DNS or UDP. */
final class TorEgress implements Closeable {
    private final VpnService vpn;
    private final ServerSocket listener;
    private final String password = UUID.randomUUID().toString();
    private final Set<Socket> sockets = ConcurrentHashMap.newKeySet();
    private final ExecutorService workers = Executors.newCachedThreadPool();
    private final Semaphore slots = new Semaphore(32);

    TorEgress(VpnService vpn) throws IOException {
        this.vpn = vpn;
        listener = new ServerSocket(0, 16, InetAddress.getByAddress(new byte[]{127,0,0,1}));
        workers.execute(() -> {
            while (!listener.isClosed()) {
                try {
                    Socket client = listener.accept();
                    if (!slots.tryAcquire()) { client.close(); continue; }
                    sockets.add(client);
                    workers.execute(() -> serve(client));
                } catch (IOException | RejectedExecutionException e) { close(); }
            }
        });
    }

    String configuration() {
        return "Socks5Proxy 127.0.0.1:" + listener.getLocalPort() + "\nSocks5ProxyUsername tor\nSocks5ProxyPassword " + password + "\n";
    }

    private void serve(Socket client) {
        Socket remote = new Socket(Proxy.NO_PROXY);
        sockets.add(remote);
        try {
            client.setSoTimeout(10000);
            DataInputStream in = new DataInputStream(client.getInputStream());
            OutputStream out = client.getOutputStream();
            if (in.readUnsignedByte() != 5) return;
            int count = in.readUnsignedByte();
            boolean auth = false;
            for (int i = 0; i < count; i++) auth |= in.readUnsignedByte() == 2;
            out.write(new byte[]{5, (byte)(auth ? 2 : 255)});
            if (!auth || in.readUnsignedByte() != 1) return;
            byte[] user = new byte[in.readUnsignedByte()]; in.readFully(user);
            byte[] pass = new byte[in.readUnsignedByte()]; in.readFully(pass);
            boolean valid = MessageDigest.isEqual(user, "tor".getBytes(StandardCharsets.UTF_8))
                    && MessageDigest.isEqual(pass, password.getBytes(StandardCharsets.UTF_8));
            out.write(new byte[]{1, (byte)(valid ? 0 : 1)});
            if (!valid || in.readUnsignedByte() != 5 || in.readUnsignedByte() != 1 || in.readUnsignedByte() != 0) return;
            int type = in.readUnsignedByte();
            InetAddress address;
            if (type == 3) {
                byte[] host = new byte[in.readUnsignedByte()]; in.readFully(host);
                String numeric = new String(host, StandardCharsets.US_ASCII);
                // Tor may encode a numeric relay address as a SOCKS domain. Never resolve names here.
                if (!(numeric.matches("[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+")
                        || (numeric.contains(":") && numeric.matches("[0-9a-fA-F:.]+")))) return;
                address = InetAddress.getByName(numeric);
            } else if (type == 1 || type == 4) {
                byte[] ip = new byte[type == 1 ? 4 : 16]; in.readFully(ip);
                address = InetAddress.getByAddress(ip);
            } else return;
            int port = in.readUnsignedShort();
            if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress() || address.isMulticastAddress() || port == 0) return;
            // The sole permitted direct socket. Protect BEFORE connect, never allowBypass().
            // Android lazily creates Socket's descriptor; bind allocates it before protect().
            remote.bind(new InetSocketAddress(0));
            if (!vpn.protect(remote)) throw new IOException("Cannot protect Tor transport");
            remote.connect(new InetSocketAddress(address, port), 20000);
            remote.setTcpNoDelay(true);
            client.setSoTimeout(0);
            out.write(new byte[]{5,0,0,1,0,0,0,0,0,0});
            Future<?> incoming = workers.submit(() -> copy(remote, client));
            copy(client, remote);
            incoming.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException | RejectedExecutionException | ExecutionException error) {
            System.err.println("Tor egress transport failed: " + error.getClass().getSimpleName());
        } finally {
            closeSocket(client); closeSocket(remote);
            slots.release();
        }
    }

    private static void copy(Socket from, Socket to) {
        try {
            byte[] buffer = new byte[8192];
            InputStream in = from.getInputStream();
            OutputStream out = to.getOutputStream();
            int length;
            while ((length = in.read(buffer)) != -1) out.write(buffer, 0, length);
            to.shutdownOutput();
        } catch (IOException ignored) {
            try { from.close(); } catch (IOException ignored2) {}
            try { to.close(); } catch (IOException ignored2) {}
        }
    }

    private void closeSocket(Socket socket) {
        sockets.remove(socket);
        try { socket.close(); } catch (IOException ignored) {}
    }

    @Override public void close() {
        try { listener.close(); } catch (IOException ignored) {}
        for (Socket socket : sockets) closeSocket(socket);
        workers.shutdownNow();
    }
}
