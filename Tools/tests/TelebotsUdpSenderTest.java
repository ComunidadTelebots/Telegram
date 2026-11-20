import org.telegram.messenger.TelebotsUdpSender;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class TelebotsUdpSenderTest {
    private static void check(boolean condition, String reason) {
        if (!condition) throw new AssertionError(reason);
    }

    public static void main(String[] args) throws Exception {
        InetAddress loopback = InetAddress.getLoopbackAddress();
        try (DatagramSocket receiver = new DatagramSocket(0, loopback)) {
            receiver.setSoTimeout(2000);
            byte[] message = "Telebots UDP loopback".getBytes(StandardCharsets.UTF_8);
            TelebotsUdpSender.send(message, loopback, receiver.getLocalPort());
            byte[] buffer = new byte[100];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            receiver.receive(packet);
            check(Arrays.equals(message, Arrays.copyOf(packet.getData(), packet.getLength())),
                    "The datagram must arrive unchanged");
        }
        try {
            TelebotsUdpSender.send(new byte[]{1}, loopback, -1);
            throw new AssertionError("Invalid destination port must be rejected");
        } catch (IllegalArgumentException expected) {
            // The send helper uses try-with-resources to close the socket on this path.
        }
        System.out.println("Telebots UDP sender: loopback delivery and failure path passed");
    }
}
