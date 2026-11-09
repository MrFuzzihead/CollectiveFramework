package com.austinv11.collectiveframework.multithreading;

import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Runnable class to ease the management of threads.
 * <p>
 * Work is handed to a shared pool of daemon threads. Two ways to run a task:
 * <ul>
 * <li>{@link #start()} - repeats {@link #run()} until the runnable is disabled. This is the historical
 * behaviour and is what {@link #setTicking(long)} adjusts.</li>
 * <li>{@link #startOnce()} - runs {@link #run()} exactly once. Use this for fire-and-forget work.</li>
 * </ul>
 * The previous implementation created a {@code SimpleThread} per runnable, kept them in a static map keyed by
 * boolean, and looped on a shared {@code RESTRICT_THREAD_USAGE} flag that could never be false at the same time
 * for both use cases. The result was a 100% CPU spin loop for anything that had been disabled, a
 * {@code NoSuchMethodError} / {@code InaccessibleObjectException} on Java 17+ from reflecting into
 * {@code java.lang.Thread}, and an {@code IllegalMonitorStateException} from {@link #delay(int)}
 * (CODE_REVIEW §1.11 - §1.15).
 */
public abstract class SimpleRunnable implements Runnable {

    private static final Logger LOG = LogManager.getLogger("CollectiveFramework");

    /**
     * Kept for source compatibility only.
     *
     * @deprecated Threads are always reused now, so this flag has no effect. Assigning it is harmless and
     *             reading it still reports the configured value.
     */
    @Deprecated
    public static volatile boolean RESTRICT_THREAD_USAGE = true;

    /**
     * Pause between iterations when no ticking interval is set, so a runnable whose {@link #run()} does nothing
     * does not peg a core. The old implementation spun with no pause at all.
     */
    private static final long LOOP_PAUSE_MS = 1L;

    private static final AtomicInteger TASK_IDS = new AtomicInteger();
    private static final AtomicInteger THREAD_IDS = new AtomicInteger();

    private static final ScheduledExecutorService EXECUTOR = Executors.newScheduledThreadPool(
        Math.max(
            4,
            Runtime.getRuntime()
                .availableProcessors()),
        new ThreadFactory() {

            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "CollectiveFramework-worker-" + THREAD_IDS.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            }
        });

    private final int id = TASK_IDS.incrementAndGet();

    private volatile boolean isActive = true;
    private volatile boolean isCleaned = false;
    private volatile long delay = -1;
    private volatile Future<?> future;

    /**
     * Disables this runnable, stopping any further iterations.
     *
     * @param clean Whether to permanently clean the runnable. A cleaned runnable cannot be started or enabled
     *              again.
     * @throws IllegalThreadStateException if this runnable is already cleaned
     */
    public final void disable(boolean clean) throws IllegalThreadStateException {
        if (isCleaned) throw new IllegalThreadStateException("Thread " + this.getName() + " is cleaned already!");
        isActive = false;
        cancelFuture();
        if (clean) isCleaned = true;
    }

    /**
     * Attempts to re-enable this runnable, restarting it if it is not currently running.
     *
     * @throws IllegalThreadStateException if this runnable is already cleaned
     */
    public final void enable() throws IllegalThreadStateException {
        if (isCleaned) throw new IllegalThreadStateException("Thread " + this.getName() + " is cleaned already!");
        isActive = true;
        Future<?> current = future;
        if (current == null || current.isDone() || current.isCancelled()) scheduleRepeating();
    }

    /**
     * Starts this runnable, repeating {@link #run()} until it is disabled.
     *
     * @throws IllegalThreadStateException if this runnable is cleaned
     */
    public final void start() {
        if (isCleaned) throw new IllegalThreadStateException("Thread " + this.getName() + " is cleaned already!");
        isActive = true;
        scheduleRepeating();
    }

    /**
     * Runs {@link #run()} exactly once on a worker thread.
     *
     * @throws IllegalThreadStateException if this runnable is cleaned
     */
    public final void startOnce() {
        if (isCleaned) throw new IllegalThreadStateException("Thread " + this.getName() + " is cleaned already!");
        isActive = true;
        synchronized (this) {
            cancelFutureLocked();
            future = EXECUTOR.submit(new Runnable() {

                @Override
                public void run() {
                    runGuarded();
                }
            });
        }
    }

    /**
     * Called by the executor to run this runnable.
     */
    @Override
    public abstract void run();

    /**
     * Gets the name of the thread
     *
     * @return The name
     */
    public String getName() {
        return this.getClass()
            .getSimpleName();
    }

    /**
     * Sleeps for the given number of milliseconds.
     * <p>
     * This used to call {@code Object.wait()} on the backing thread without owning its monitor, which always
     * threw {@link IllegalMonitorStateException} (CODE_REVIEW §1.11).
     *
     * @param delay The length of the delay (in milliseconds)
     * @throws InterruptedException if the calling thread is interrupted
     */
    public final void delay(int delay) throws InterruptedException {
        Thread.sleep(Math.max(0L, delay));
    }

    /**
     * Gets whether is runnable is cleaned
     *
     * @return IsCleaned
     */
    public boolean isCleaned() {
        return isCleaned;
    }

    /**
     * Gets whether the runnable is enabled
     *
     * @return IsEnabled
     */
    public boolean isEnabled() {
        return isActive;
    }

    /**
     * Makes the runnable loop every x milliseconds. Takes effect immediately if the runnable is already running.
     *
     * @param delay The delay for each loop, in milliseconds; a negative value means "as fast as the loop pause
     *              allows"
     */
    public void setTicking(long delay) {
        this.delay = delay;
        Future<?> current = future;
        if (current != null && !current.isDone() && !current.isCancelled()) scheduleRepeating();
    }

    /**
     * @return The configured ticking interval in milliseconds, or a negative value if none is set
     */
    public long getTicking() {
        return delay;
    }

    private void scheduleRepeating() {
        synchronized (this) {
            cancelFutureLocked();
            long interval = delay >= 0 ? delay : LOOP_PAUSE_MS;
            future = EXECUTOR.scheduleWithFixedDelay(new Runnable() {

                @Override
                public void run() {
                    runGuarded();
                }
            }, 0, interval, TimeUnit.MILLISECONDS);
        }
    }

    private void cancelFuture() {
        synchronized (this) {
            cancelFutureLocked();
        }
    }

    private void cancelFutureLocked() {
        Future<?> current = future;
        if (current != null) current.cancel(false);
        future = null;
    }

    /**
     * Runs {@link #run()}, keeping the loop alive and logging anything it throws. Without this a single throwing
     * iteration would silently kill a repeating runnable, because {@code scheduleWithFixedDelay} suppresses a
     * task after it throws.
     */
    /**
     * Number of worker threads currently executing a task. Exposed so tests can assert that a disabled runnable
     * stops occupying a worker (the old implementation spun forever, burning a core, without calling
     * {@link #run()} again).
     *
     * @return The number of active workers
     */
    public static int getActiveTaskCount() {
        return ((java.util.concurrent.ThreadPoolExecutor) EXECUTOR).getActiveCount();
    }

    private void runGuarded() {
        if (!isActive) return;
        try {
            run();
        } catch (Throwable t) {
            LOG.error("SimpleRunnable " + getName() + " threw", t);
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SimpleRunnable)) return false;
        return ((SimpleRunnable) other).id == id;
    }

    @Override
    public int hashCode() {
        return id;
    }

    @Override
    public String toString() {
        return "SimpleRunnable(Name: " + getName() + " ID:" + id + " Is Cleaned: " + isCleaned + ")";
    }
}
