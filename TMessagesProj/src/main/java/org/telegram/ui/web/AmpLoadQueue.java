package org.telegram.ui.web;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Small cancellable queue: recycled cells must not occupy pending download slots. */
final class AmpLoadQueue {
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(4), runnable -> {
                Thread thread = new Thread(runnable, "AMP-chart-data");
                thread.setDaemon(true);
                return thread;
            });

    AmpLoadQueue() { executor.allowCoreThreadTimeOut(true); }

    Future<?> submit(Runnable work) { return executor.submit(work); }

    void cancel(Future<?> work) {
        if (work == null) return;
        // Do not interrupt an active shared transport operation. Generation checks discard its result.
        work.cancel(false);
        if (work instanceof Runnable) executor.remove((Runnable) work);
    }
}
