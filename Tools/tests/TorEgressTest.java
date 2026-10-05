package org.telegram.utils.proxy;
import android.net.VpnService;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public final class TorEgressTest {
    private static void require(boolean value) { if (!value) throw new AssertionError(); }
    public static void main(String[] args) throws Exception {
        VpnService vpn = new VpnService();
        try (TorEgress egress = new TorEgress(vpn)) {
            String[] lines = egress.configuration().split("\n");
            int port = Integer.parseInt(lines[0].substring(lines[0].lastIndexOf(':') + 1));
            String password = lines[2].substring("Socks5ProxyPassword ".length());
            // Authentication is mandatory; unrelated app components cannot use the exit.
            try (Socket socket = connect(port)) {
                socket.getOutputStream().write(new byte[]{5,1,0});
                require(socket.getInputStream().read() == 5);
                require(socket.getInputStream().read() == 255);
            }
            try (Socket socket = connect(port)) { require(!authenticate(socket, "wrong")); }
            require(vpn.protectCalls == 0);
            // Refusing protect must stop BEFORE any connection to the requested address.
            try (Socket socket = connect(port)) {
                require(authenticate(socket, password));
                socket.getOutputStream().write(new byte[]{5,1,0,1,8,8,8,8,1,(byte)187});
                require(socket.getInputStream().read() == -1);
            }
            require(vpn.protectCalls == 1);
            // UDP and hostnames cannot trigger a direct connection or local DNS lookup.
            for (byte[] request : new byte[][]{{5,3,0,1,8,8,8,8,0,53}, {5,1,0,3,1,120,1,(byte)187}, {5,1,0,1,127,0,0,1,0,80}}) {
                try (Socket socket = connect(port)) {
                    require(authenticate(socket, password));
                    socket.getOutputStream().write(request);
                    try { require(socket.getInputStream().read() == -1); } catch (SocketException expected) {}
                }
            }
            require(vpn.protectCalls == 1);
        }
        System.out.println("TorEgress: authentication, protected-only connect, no UDP/DNS/private-address bypass passed");
    }
    private static Socket connect(int port) throws Exception {
        Socket socket = new Socket("127.0.0.1", port);
        socket.setSoTimeout(3000);
        return socket;
    }
    private static boolean authenticate(Socket socket, String password) throws Exception {
        DataInputStream input = new DataInputStream(socket.getInputStream());
        DataOutputStream output = new DataOutputStream(socket.getOutputStream());
        output.write(new byte[]{5,1,2});
        require(input.readUnsignedByte() == 5 && input.readUnsignedByte() == 2);
        byte[] secret = password.getBytes(StandardCharsets.UTF_8);
        output.write(new byte[]{1,3,116,111,114});
        output.writeByte(secret.length); output.write(secret);
        require(input.readUnsignedByte() == 1);
        return input.readUnsignedByte() == 0;
    }
}
