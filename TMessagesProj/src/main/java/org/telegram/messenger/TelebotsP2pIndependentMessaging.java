package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * Telebots P2P Independent Messaging Controller.
 * Direct LAN messaging with mutually authenticated TLS and manually verified pins.
 * No discovery-based trust, plaintext fallback or multi-hop forwarding.
 */
public class TelebotsP2pIndependentMessaging {

    public static final String BROADCAST_CHANNEL = "MESH_BROADCAST";
    public static final int P2P_TCP_PORT = 8989;

    private static volatile TelebotsP2pIndependentMessaging instance;

    public interface MessageListener {
        void onMessageReceived(P2pMessage message);
        void onPeerDiscovered(String peerId, String address);
    }

    public static class P2pMessage {
        public String id;
        public String senderId;
        public String recipientId;
        public String text;
        public long timestamp;
        public int hops;
        public boolean isOutgoing;
        public boolean authenticated;

        public P2pMessage(String id, String senderId, String recipientId, String text, long timestamp, int hops, boolean isOutgoing) {
            this.id = id;
            this.senderId = senderId;
            this.recipientId = recipientId;
            this.text = text;
            this.timestamp = timestamp;
            this.hops = hops;
            this.isOutgoing = isOutgoing;
        }

        public JSONObject toJson() {
            try {
                JSONObject obj = new JSONObject();
                obj.put("id", id);
                obj.put("sender", senderId);
                obj.put("recipient", recipientId);
                obj.put("text", text);
                obj.put("timestamp", timestamp);
                obj.put("hops", hops);
                return obj;
            } catch (Exception e) {
                return new JSONObject();
            }
        }

        public static P2pMessage fromJson(JSONObject obj, boolean isOutgoing) {
            return new P2pMessage(
                obj.optString("id", UUID.randomUUID().toString()),
                obj.optString("sender", "Peer_Anon"),
                obj.optString("recipient", BROADCAST_CHANNEL),
                obj.optString("text", ""),
                obj.optLong("timestamp", System.currentTimeMillis()),
                obj.optInt("hops", 1),
                isOutgoing
            );
        }
    }

    private volatile String localNodeId = "Inicializando identidad";
    private volatile TelebotsPeerIdentity identity;
    private volatile long session;
    private final java.util.Map<String, String> trustedPeers = new java.util.concurrent.ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<P2pMessage> messageHistory = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<MessageListener> listeners = new CopyOnWriteArrayList<>();
    private final java.util.concurrent.ThreadPoolExecutor networkPool = new java.util.concurrent.ThreadPoolExecutor(4, 4, 30, TimeUnit.SECONDS, new java.util.concurrent.ArrayBlockingQueue<>(16));


    private ServerSocket serverSocket;
    private volatile boolean isRunning;


    public static TelebotsP2pIndependentMessaging getInstance() {
        if (instance == null) {
            synchronized (TelebotsP2pIndependentMessaging.class) {
                if (instance == null) {
                    instance = new TelebotsP2pIndependentMessaging();
                }
            }
        }
        return instance;
    }

    private TelebotsP2pIndependentMessaging() {
        SharedPreferences prefs = ApplicationLoader.applicationContext.getSharedPreferences("telebots_p2p_messaging", Context.MODE_PRIVATE);
        try {
            JSONObject peers = new JSONObject(prefs.getString("trusted_tls_peers", "{}"));
            java.util.Iterator<String> keys = peers.keys();
            while (keys.hasNext() && trustedPeers.size() < 16) {
                String host = keys.next(); String pin = peers.getString(host);
                if (validHost(host) && pin.matches("[a-f0-9]{64}")) trustedPeers.put(host, pin);
            }
        } catch (Exception ignored) { }
        networkPool.execute(() -> {
            if (!CommunityCompatibility.supportsPeerIdentity(android.os.Build.VERSION.SDK_INT)) {
                localNodeId = "P2P cifrado requiere Android 6.0";
                return;
            }
            try { TelebotsPeerIdentity ready = new TelebotsPeerIdentity(); localNodeId = ready.fingerprint(); identity = ready; }
            catch (Exception ignored) { localNodeId = "Identidad no disponible"; }
        });

        loadSavedMessages();
        networkPool.allowCoreThreadTimeOut(true);
    }

    public String getLocalNodeId() {
        return localNodeId;
    }

    public void addListener(MessageListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(MessageListener listener) {
        listeners.remove(listener);
    }

    public List<P2pMessage> getMessages() {
        return new ArrayList<>(messageHistory);
    }

    public synchronized void sendMessage(String recipient, String text) {
        if (!isRunning || text == null || text.trim().isEmpty() || text.length() > 2000) return;

        P2pMessage msg = new P2pMessage(
            UUID.randomUUID().toString(),
            localNodeId,
            recipient != null ? recipient : BROADCAST_CHANNEL,
            text.trim(),
            System.currentTimeMillis(),
            1,
            true
        );

        messageHistory.add(msg);
        while (messageHistory.size() > 200) messageHistory.remove(0);
        saveMessages();
        notifyMessage(msg);

        // Send independently to explicitly authorized TLS peers.
        final long generation = session;
        try { networkPool.execute(() -> broadcastMessageToPeers(msg, generation)); }
        catch (java.util.concurrent.RejectedExecutionException full) { FileLog.e("P2P send queue full"); }
    }

    private void broadcastMessageToPeers(P2pMessage msg, long generation) {
        if (!isRunning || generation != session || !ReleaseAccess.allowed()
                || org.telegram.utils.proxy.TorProxyController.isEnabled() || SharedConfig.isProxyEnabled()) return;
        for (java.util.Map.Entry<String, String> peer : trustedPeers.entrySet()) {
            if (!isRunning || generation != session) return;
            if (!BROADCAST_CHANNEL.equals(msg.recipientId) && !peer.getValue().equals(msg.recipientId)) continue;
            try (javax.net.ssl.SSLSocket socket = (javax.net.ssl.SSLSocket) identity.context(
                    java.util.Collections.singleton(peer.getValue())).getSocketFactory().createSocket()) {
                socket.setEnabledProtocols(TelebotsPeerIdentity.PROTOCOLS);
                socket.setEnabledCipherSuites(TelebotsPeerIdentity.CIPHERS);
                socket.connect(new java.net.InetSocketAddress(peer.getKey(), P2P_TCP_PORT), 3000);
                socket.setSoTimeout(3000);
                socket.startHandshake();
                if (!isRunning || generation != session) return;
                socket.getOutputStream().write((msg.toJson().toString() + "\n").getBytes(StandardCharsets.UTF_8));
                socket.getOutputStream().flush();
            } catch (Exception ignored) { /* Never retry through plaintext or a different identity. */ }
        }
    }

    private static boolean validHost(String host) {
        // Literal private IPv4 only: no DNS lookup, redirects or discovery-based trust.
        if (host == null || !host.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}")) return false;
        try {
            for (String part : host.split("\\.")) if (Integer.parseInt(part) > 255) return false;
            java.net.InetAddress address = java.net.InetAddress.getByName(host);
            return address.isSiteLocalAddress() || address.isLoopbackAddress();
        } catch (Exception ignored) { return false; }
    }
    public synchronized boolean trustPeer(String host, String pin) {
        if (host == null || pin == null) return false;
        pin = pin.replace(":", "").replace(" ", "").toLowerCase(java.util.Locale.ROOT);
        if (!validHost(host) || !pin.matches("[a-f0-9]{64}") || pin.equals(localNodeId)
                || (!trustedPeers.containsKey(host) && trustedPeers.size() >= 16)) return false;
        stop(); trustedPeers.put(host, pin); savePeers(); return true;
    }
    public synchronized void clearTrustedPeers() { stop(); trustedPeers.clear(); savePeers(); }
    public int trustedPeerCount() { return trustedPeers.size(); }
    private void savePeers() {
        ApplicationLoader.applicationContext.getSharedPreferences("telebots_p2p_messaging", Context.MODE_PRIVATE)
                .edit().putString("trusted_tls_peers", new JSONObject(trustedPeers).toString()).apply();
    }

    public boolean isRunning() { return isRunning; }

    public synchronized boolean start() {
        if (isRunning) return true;
        // Direct LAN transport cannot be used while the user requires Tor/proxy routing.
        if (org.telegram.utils.proxy.TorProxyController.isEnabled() || SharedConfig.isProxyEnabled()
                || !ReleaseAccess.allowed()) return false;
        try {
            if (identity == null || trustedPeers.isEmpty()) return false;
            javax.net.ssl.SSLServerSocket tls = (javax.net.ssl.SSLServerSocket) identity.context(
                    new java.util.HashSet<>(trustedPeers.values())).getServerSocketFactory().createServerSocket(P2P_TCP_PORT, 4);
            tls.setEnabledProtocols(TelebotsPeerIdentity.PROTOCOLS);
            tls.setEnabledCipherSuites(TelebotsPeerIdentity.CIPHERS);
            tls.setNeedClientAuth(true);
            serverSocket = tls;
            isRunning = true;
            final long generation = ++session;
            networkPool.execute(() -> {
                try {
                    while (!tls.isClosed()) {
                        Socket client = tls.accept();
                        try { networkPool.execute(() -> handleClientSocket(client, generation)); }
                        catch (java.util.concurrent.RejectedExecutionException full) { client.close(); }
                    }
                } catch (Exception ignored) { }
            });
            return true;
        } catch (Exception e) { stop(); return false; }
    }

    public synchronized void stop() {
        isRunning = false;
        session++;
        try { if (serverSocket != null) serverSocket.close(); } catch (Exception ignored) { }
    }

    private void handleClientSocket(Socket socket, long generation) {
        try (Socket client = socket) {
            client.setSoTimeout(3000);
            javax.net.ssl.SSLSocket tls = (javax.net.ssl.SSLSocket) client;
            tls.startHandshake();
            String authenticatedId = TelebotsPeerTrust.fingerprint((java.security.cert.X509Certificate) tls.getSession().getPeerCertificates()[0]);
            String line = TelebotsP2pFrame.read(new java.io.BufferedInputStream(client.getInputStream()));
            if (line != null) {
                handleIncomingPacket(line, authenticatedId, generation);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private synchronized void handleIncomingPacket(String payload, String authenticatedId, long generation) {
        try {
            if (!isRunning || !ReleaseAccess.allowed() || org.telegram.utils.proxy.TorProxyController.isEnabled() || SharedConfig.isProxyEnabled()) { stop(); return; }
            if (generation != session || !trustedPeers.containsValue(authenticatedId)
                    || payload == null || payload.length() > 8192 || payload.trim().isEmpty()) return;

            JSONObject json = new JSONObject(payload);
            P2pMessage msg = P2pMessage.fromJson(json, false);
            if (!authenticatedId.equals(msg.senderId) || !json.has("id") || !json.has("timestamp")
                    || msg.timestamp < System.currentTimeMillis() - 300000L || msg.timestamp > System.currentTimeMillis() + 60000L) return;
            if (msg.text.isEmpty() || msg.text.length() > 2000 || msg.id.length() > 64
                    || msg.senderId.length() > 64 || msg.recipientId.length() > 64
                    || msg.hops < 1 || msg.hops > 8
                    || (!BROADCAST_CHANNEL.equals(msg.recipientId) && !localNodeId.equals(msg.recipientId))) return;
            if (msg.senderId.equals(localNodeId)) return; // ignore echo

            // Check if already in history
            for (P2pMessage m : messageHistory) {
                if (m.id.equals(msg.id)) return;
            }

            msg.authenticated = true;
            messageHistory.add(msg);
            while (messageHistory.size() > 200) messageHistory.remove(0);
            saveMessages();
            notifyMessage(msg);

            // Receiving a packet is not evidence of bytes served to another peer.
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void notifyMessage(P2pMessage msg) {
        AndroidUtilities.runOnUIThread(() -> {
            for (MessageListener l : listeners) {
                l.onMessageReceived(msg);
            }
        });
    }

    private void notifyPeer(String peerId, String address) {
        AndroidUtilities.runOnUIThread(() -> {
            for (MessageListener l : listeners) {
                l.onPeerDiscovered(peerId, address);
            }
        });
    }

    private void loadSavedMessages() {
        try {
            SharedPreferences prefs = ApplicationLoader.applicationContext.getSharedPreferences("telebots_p2p_messaging", Context.MODE_PRIVATE);
            String raw = prefs.getString("messages_json", "[]");
            if (raw.length() > 2 * 1024 * 1024) return;
            JSONArray arr = new JSONArray(raw);
            for (int i = Math.max(0, arr.length() - 200); i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                P2pMessage saved = P2pMessage.fromJson(o, o.optBoolean("is_outgoing", false));
                saved.authenticated = o.optBoolean("authenticated", false);
                messageHistory.add(saved);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void saveMessages() {
        try {
            JSONArray arr = new JSONArray();
            int start = Math.max(0, messageHistory.size() - 200); // keep recent 200
            for (int i = start; i < messageHistory.size(); i++) {
                JSONObject o = messageHistory.get(i).toJson();
                o.put("is_outgoing", messageHistory.get(i).isOutgoing);
                o.put("authenticated", messageHistory.get(i).authenticated);
                arr.put(o);
            }
            SharedPreferences prefs = ApplicationLoader.applicationContext.getSharedPreferences("telebots_p2p_messaging", Context.MODE_PRIVATE);
            prefs.edit().putString("messages_json", arr.toString()).apply();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }
}
