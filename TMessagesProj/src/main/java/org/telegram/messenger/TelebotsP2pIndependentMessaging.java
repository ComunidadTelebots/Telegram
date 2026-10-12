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
    private final ExecutorService networkPool = Executors.newCachedThreadPool();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private ServerSocket serverSocket;
    private boolean isRunning;

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
        startReceiverServer();
        startBeaconLoop();
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

    public void sendMessage(String recipient, String text) {
        if (text == null || text.trim().isEmpty()) return;

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
        saveMessages();
        notifyMessage(msg);

        // Disperse message to discovered peers via TCP and UDP Mesh
        networkPool.execute(() -> broadcastMessageToPeers(msg));
    }

    private void broadcastMessageToPeers(P2pMessage msg) {
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
                try (Socket socket = new Socket(peerAddress, P2P_TCP_PORT)) {
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

    private void startReceiverServer() {
        if (isRunning) return;
        isRunning = true;

        // TCP Server
        networkPool.execute(() -> {
            try {
                serverSocket = new ServerSocket(P2P_TCP_PORT);
                while (isRunning && !serverSocket.isClosed()) {
                    Socket client = serverSocket.accept();
                    networkPool.execute(() -> handleClientSocket(client));
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        });

        // UDP Beacon & Broadcast Listener
        networkPool.execute(() -> {
            try (DatagramSocket udp = new DatagramSocket(P2P_UDP_BEACON_PORT)) {
                byte[] buffer = new byte[8192];
                while (isRunning) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    udp.receive(packet);
                    String raw = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                    handleIncomingPacket(raw, packet.getAddress().getHostAddress());
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    private void handleClientSocket(Socket socket) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
            String line = reader.readLine();
            if (line != null) {
                handleIncomingPacket(line, socket.getInetAddress().getHostAddress());
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void handleIncomingPacket(String payload, String sourceIp) {
        try {
            if (payload == null || payload.trim().isEmpty()) return;

            JSONObject json = new JSONObject(payload);
            if (json.has("beacon_node")) {
                // Presence beacon
                String peerId = json.optString("beacon_node");
                if (!peerId.equals(localNodeId)) {
                    TelebotsP2pRoutingMesh.getInstance().getActivePeers().put(sourceIp, "WiFi-Mesh");
                    notifyPeer(peerId, sourceIp);
                }
                return;
            }

            P2pMessage msg = P2pMessage.fromJson(json, false);
            if (msg.senderId.equals(localNodeId)) return; // ignore echo

            // Check if already in history
            for (P2pMessage m : messageHistory) {
                if (m.id.equals(msg.id)) return;
            }

            msg.hops++;
            messageHistory.add(msg);
            saveMessages();
            notifyMessage(msg);

            TelebotsP2pHubController.getInstance(UserConfig.selectedAccount).addBandwidthServed(payload.length());
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void startBeaconLoop() {
        scheduler.scheduleAtFixedRate(() -> {
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
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
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
