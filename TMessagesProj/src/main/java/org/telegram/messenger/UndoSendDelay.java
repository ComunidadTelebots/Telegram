package org.telegram.messenger;

/** UI-thread-only, in-memory gate before sending. Never persists, retries or resumes a message. */
public final class UndoSendDelay {
    public interface Clock { long now(); }
    public interface Scheduler { void post(Runnable task, long delay); void remove(Runnable task); }
    public interface Gate { boolean valid(); }
    private final Clock clock;
    private final Scheduler scheduler;
    private Runnable task, cancelled;
    private long generation;

    public UndoSendDelay(Clock clock, Scheduler scheduler) { this.clock = clock; this.scheduler = scheduler; }
    public boolean isPending() { return task != null; }

    public void start(long delay, Gate valid, Runnable send, Runnable onCancelled) {
        if (delay != 3000 && delay != 5000 && delay != 10000) throw new IllegalArgumentException("Undo window");
        cancel();
        final long ticket = ++generation, deadline = clock.now() + delay;
        cancelled = onCancelled;
        task = new Runnable() {
            public void run() {
                if (ticket != generation || task != this) return;
                long remaining = deadline - clock.now();
                boolean allowed;
                try { allowed = valid.valid(); } catch (RuntimeException ignored) { allowed = false; }
                // A sleeping, stalled or resumed process must not dispatch an unexpectedly late message.
                if (!allowed || remaining < -1000) { cancel(); return; }
                if (remaining > 0) { scheduler.post(this, remaining); return; }
                task = null; cancelled = null; generation++;
                send.run();
            }
        };
        scheduler.post(task, delay);
    }

    public void cancel() {
        if (task == null) return;
        Runnable old = task, callback = cancelled;
        task = null; cancelled = null; generation++;
        scheduler.remove(old);
        if (callback != null) callback.run();
    }
}
