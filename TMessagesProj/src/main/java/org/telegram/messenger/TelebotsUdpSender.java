package org.telegram.messenger;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

/** Sends one UDP datagram, closing the socket even if the network operation fails. */
public final class TelebotsUdpSender {
    private TelebotsUdpSender() {}

    public static void send(byte[] payload, InetAddress destination, int port) throws IOException {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            socket.send(new DatagramPacket(payload, payload.length, destination, port));
        }
    }
}
