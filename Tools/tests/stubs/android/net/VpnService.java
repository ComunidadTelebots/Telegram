package android.net;
import java.net.Socket;
/** JVM-only fake: never grants a direct socket. */
public class VpnService {
    public volatile int protectCalls;
    public boolean protect(Socket socket) {
        if (!socket.isBound() || socket.isConnected()) throw new AssertionError("Protect requires a created, unconnected descriptor");
        protectCalls++;
        return false;
    }
}
