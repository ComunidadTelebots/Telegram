package org.telegram.messenger;

import java.io.IOException;
import java.net.Socket;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Absolute socket deadlines, independent of the bounded network workers. */
final class TelebotsPeerConnections {
    private final ScheduledThreadPoolExecutor deadlines;
    private final Set<Lease> active = new HashSet<>();
    private final int limit;
    private final long timeoutMillis;

    TelebotsPeerConnections(int limit, long timeoutMillis) {
        if (limit < 1 || timeoutMillis < 1) throw new IllegalArgumentException();
        this.limit = limit;
        this.timeoutMillis = timeoutMillis;
        deadlines = new ScheduledThreadPoolExecutor(1, runnable -> {
            Thread thread = new Thread(runnable, "P2P-deadlines");
            thread.setDaemon(true);
            return thread;
        });
        deadlines.setRemoveOnCancelPolicy(true);
        deadlines.setKeepAliveTime(30, TimeUnit.SECONDS);
        deadlines.allowCoreThreadTimeOut(true);
    }

    synchronized Lease track(Socket socket) throws IOException {
        if (active.size() >= limit) {
            socket.close();
            throw new IOException("P2P connection limit");
        }
        Lease lease = new Lease(socket);
        active.add(lease);
        try {
            lease.deadline = deadlines.schedule(lease::close, timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (RuntimeException unavailable) {
            lease.close();
            throw new IOException("P2P deadline unavailable", unavailable);
        }
        return lease;
    }

    synchronized void closeAll() {
        for (Lease lease : new HashSet<>(active)) lease.close();
    }

    final class Lease implements AutoCloseable {
        final Socket socket;
        private ScheduledFuture<?> deadline;
        private volatile boolean closed;

        private Lease(Socket socket) { this.socket = socket; }
        boolean isClosed() { return closed; }

        @Override public void close() {
            synchronized (TelebotsPeerConnections.this) {
                if (closed) return;
                closed = true;
                active.remove(this);
                if (deadline != null) deadline.cancel(false);
            }
            try { socket.close(); } catch (IOException ignored) { }
        }
    }
}
