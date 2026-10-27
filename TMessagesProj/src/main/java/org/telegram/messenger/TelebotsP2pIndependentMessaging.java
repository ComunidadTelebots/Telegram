package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Telebots P2P Independent Messaging Controller.
 * Enables zero-server, offline mesh communication between nearby peers using
 * local Wi-Fi, direct TCP sockets, and Store-and-Forward Gossip protocol.
 */
public class TelebotsP2pIndependentMessaging {

    public static final String BROADCAST_CHANNEL = "MESH_BROADCAST";
    public static final int P2P_TCP_PORT = 8989;
    public static final int P2P_UDP_BEACON_PORT = 4455;

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

    private final String localNodeId;
    private final CopyOnWriteArrayList<P2pMessage> messageHistory = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<MessageListener> listeners = new CopyOnWriteArrayList<>();
    private final java.util.concurrent.ThreadPoolExecutor networkPool = new java.util.concurrent.ThreadPoolExecutor(4, 4, 30, TimeUnit.SECONDS, new java.util.concurrent.ArrayBlockingQueue<>(16));
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private ServerSocket serverSocket;
    private volatile boolean isRunning;
    private DatagramSocket receiver;
    private java.util.concurrent.ScheduledFuture<?> beaconTask;

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
        String savedNodeId = prefs.getString("local_node_id", null);
        if (savedNodeId == null) {
            savedNodeId = "Peer_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(java.util.Locale.ROOT);
            prefs.edit().putString("local_node_id", savedNodeId).apply();
        }
        localNodeId = savedNodeId;

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

        // Disperse message to discovered peers via TCP and UDP Mesh
        try { networkPool.execute(() -> broadcastMessageToPeers(msg)); }
        catch (java.util.concurrent.RejectedExecutionException full) { FileLog.e("P2P send queue full"); }
    }

    private void broadcastMessageToPeers(P2pMessage msg) {
        if (!isRunning || !ReleaseAccess.allowed() || org.telegram.utils.proxy.TorProxyController.isEnabled()
                || SharedConfig.isProxyEnabled()) return;
        try {
            JSONObject payload = msg.toJson();
            byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);

            // 1. Send via local network UDP broadcast
            DatagramSocket udpSocket = new DatagramSocket();
            udpSocket.setBroadcast(true);
            DatagramPacket packet = new DatagramPacket(bytes, bytes.length, InetAddress.getByName("255.255.255.255"), P2P_UDP_BEACON_PORT);
            udpSocket.send(packet);
            udpSocket.close();

            // 2. Transmit to registered active peers in Routing Mesh
            for (String peerAddress : TelebotsP2pRoutingMesh.getInstance().getActivePeers().keySet()) {
                if (!peerAddress.contains(".") && !peerAddress.contains(":")) continue; // skip non-IPs
                try (Socket socket = new Socket()) {
                    socket.connect(new java.net.InetSocketAddress(peerAddress, P2P_TCP_PORT), 3000);
                    socket.setSoTimeout(3000);
                    OutputStream os = socket.getOutputStream();
                    os.write((payload.toString() + "\n").getBytes(StandardCharsets.UTF_8));
                    os.flush();
                } catch (Exception ignored) {}
            }

            TelebotsP2pHubController.getInstance(UserConfig.selectedAccount).addBandwidthServed(bytes.length);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public boolean isRunning() { return isRunning; }

    public synchronized boolean start() {
        if (isRunning) return true;
        // Direct LAN transport cannot be used while the user requires Tor/proxy routing.
        if (org.telegram.utils.proxy.TorProxyController.isEnabled() || SharedConfig.isProxyEnabled()
                || !ReleaseAccess.allowed()) return false;
        try {
            serverSocket = new ServerSocket(P2P_TCP_PORT, 4);
            receiver = new DatagramSocket(P2P_UDP_BEACON_PORT);
            isRunning = true;
            final ServerSocket tcp = serverSocket;
            final DatagramSocket udp = receiver;
            networkPool.execute(() -> {
                try {
                    while (!tcp.isClosed()) {
                        Socket client = tcp.accept();
                        try { networkPool.execute(() -> handleClientSocket(client)); }
                        catch (java.util.concurrent.RejectedExecutionException full) { client.close(); }
                    }
                } catch (Exception ignored) { }
            });
            networkPool.execute(() -> {
                byte[] buffer = new byte[8193];
                try {
                    while (!udp.isClosed()) {
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        udp.receive(packet);
                        if (packet.getLength() > 8192) continue;
                        handleIncomingPacket(new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8), packet.getAddress().getHostAddress());
                    }
                } catch (Exception ignored) { }
            });
            startBeaconLoop();
            return true;
        } catch (Exception e) { stop(); return false; }
    }

    public synchronized void stop() {
        isRunning = false;
        if (beaconTask != null) { beaconTask.cancel(false); beaconTask = null; }
        try { if (serverSocket != null) serverSocket.close(); } catch (Exception ignored) { }
        if (receiver != null) receiver.close();
    }

    private void handleClientSocket(Socket socket) {
        try (Socket client = socket) {
            client.setSoTimeout(3000);
            String line = TelebotsP2pFrame.read(client.getInputStream());
            if (line != null) {
                handleIncomingPacket(line, socket.getInetAddress().getHostAddress());
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private synchronized void handleIncomingPacket(String payload, String sourceIp) {
        try {
            if (!isRunning || !ReleaseAccess.allowed() || org.telegram.utils.proxy.TorProxyController.isEnabled() || SharedConfig.isProxyEnabled()) { stop(); return; }
            if (payload == null || payload.length() > 8192 || payload.trim().isEmpty()) return;

            JSONObject json = new JSONObject(payload);
            if (json.has("beacon_node")) {
                // Presence beacon
                String peerId = json.optString("beacon_node");
                if (peerId.length() <= 64 && !peerId.isEmpty() && !peerId.equals(localNodeId)
                        && TelebotsP2pRoutingMesh.getInstance().getActivePeers().size() < 64) {
                    TelebotsP2pRoutingMesh.getInstance().registerPeer(sourceIp, "WiFi-Mesh");
                    notifyPeer(peerId, sourceIp);
                }
                return;
            }

            P2pMessage msg = P2pMessage.fromJson(json, false);
            if (msg.text.isEmpty() || msg.text.length() > 2000 || msg.id.length() > 64
                    || msg.senderId.length() > 64 || msg.recipientId.length() > 64
                    || msg.hops < 1 || msg.hops > 8
                    || (!BROADCAST_CHANNEL.equals(msg.recipientId) && !localNodeId.equals(msg.recipientId))) return;
            if (msg.senderId.equals(localNodeId)) return; // ignore echo

            // Check if already in history
            for (P2pMessage m : messageHistory) {
                if (m.id.equals(msg.id)) return;
            }

            msg.hops++;
            messageHistory.add(msg);
            while (messageHistory.size() > 200) messageHistory.remove(0);
            saveMessages();
            notifyMessage(msg);

            // Receiving a packet is not evidence of bytes served to another peer.
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void startBeaconLoop() {
        beaconTask = scheduler.scheduleAtFixedRate(() -> {
            if (!isRunning || !ReleaseAccess.allowed() || org.telegram.utils.proxy.TorProxyController.isEnabled() || SharedConfig.isProxyEnabled()) { stop(); return; }
            try {
                JSONObject beacon = new JSONObject();
                beacon.put("beacon_node", localNodeId);
                beacon.put("timestamp", System.currentTimeMillis());

                byte[] bytes = beacon.toString().getBytes(StandardCharsets.UTF_8);
                DatagramSocket socket = new DatagramSocket();
                socket.setBroadcast(true);
                DatagramPacket packet = new DatagramPacket(bytes, bytes.length, InetAddress.getByName("255.255.255.255"), P2P_UDP_BEACON_PORT);
                socket.send(packet);
                socket.close();
            } catch (Exception ignored) {}
        }, 2, 12, TimeUnit.SECONDS);
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
                messageHistory.add(P2pMessage.fromJson(o, o.optBoolean("is_outgoing", false)));
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
                arr.put(o);
            }
            SharedPreferences prefs = ApplicationLoader.applicationContext.getSharedPreferences("telebots_p2p_messaging", Context.MODE_PRIVATE);
            prefs.edit().putString("messages_json", arr.toString()).apply();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }
}
