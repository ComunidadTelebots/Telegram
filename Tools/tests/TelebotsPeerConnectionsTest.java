package org.telegram.messenger;

import java.net.*;
import java.io.*;
import java.util.concurrent.atomic.AtomicInteger;

public class TelebotsPeerConnectionsTest {
    static class Tracked extends Socket {
        final AtomicInteger closes = new AtomicInteger();
        @Override public void close() { closes.incrementAndGet(); }
    }
    public static void main(String[] args) throws Exception {
        TelebotsPeerConnections bounded = new TelebotsPeerConnections(2, 5000);
        Tracked first = new Tracked(), second = new Tracked(), excess = new Tracked();
        TelebotsPeerConnections.Lease a = bounded.track(first), b = bounded.track(second);
        try { bounded.track(excess); throw new AssertionError("Unbounded connections"); } catch (IOException expected) { }
        if (excess.closes.get() != 1) throw new AssertionError("Rejected socket leaked");
        a.close(); a.close();
        Tracked third = new Tracked(); bounded.track(third);
        bounded.closeAll();
        if (first.closes.get() != 1 || second.closes.get() != 1 || third.closes.get() != 1) throw new AssertionError("Stop/finish cleanup");
        Tracked restarted = new Tracked(); bounded.track(restarted); bounded.closeAll();
        if (restarted.closes.get() != 1) throw new AssertionError("Restart");

        // A peer makes continuous progress below SO_TIMEOUT; only the absolute deadline ends it.
        TelebotsPeerConnections deadlines = new TelebotsPeerConnections(1, 250);
        try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
             Socket sender = new Socket(InetAddress.getLoopbackAddress(), listener.getLocalPort());
             Socket receiver = listener.accept()) {
            receiver.setSoTimeout(200);
            Thread writer = new Thread(() -> {
                try {
                    for (int i = 0; i < 200; i++) { sender.getOutputStream().write('x'); sender.getOutputStream().flush(); Thread.sleep(20); }
                } catch (Exception expected) { }
            });
            writer.setDaemon(true); writer.start();
            long started = System.nanoTime(); int bytes = 0;
            try (TelebotsPeerConnections.Lease lease = deadlines.track(receiver)) {
                try { while (receiver.getInputStream().read() >= 0) bytes++; } catch (IOException closed) { }
                if (!lease.isClosed()) throw new AssertionError("Idle timeout substituted for deadline");
            }
            long elapsed = (System.nanoTime() - started) / 1000000;
            if (bytes < 2 || elapsed > 2000 || !receiver.isClosed()) throw new AssertionError("Slow-progress peer survived: " + elapsed);
            sender.close(); writer.join(1000);
        } finally { deadlines.closeAll(); }
        System.out.println("PASS: bounded sockets, rejected/finished/stop cleanup, restart and progressing loopback peer closed by independent deadline");
    }
}
