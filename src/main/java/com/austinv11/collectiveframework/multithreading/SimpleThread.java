package com.austinv11.collectiveframework.multithreading;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Simple thread instance for use alongside the {@link SimpleRunnable}.
 *
 * @deprecated {@link SimpleRunnable} no longer uses this class: it runs its tasks on a shared
 *             {@link java.util.concurrent.ScheduledExecutorService}, which needs no {@link Thread} subclass and no
 *             reflection into {@code java.lang.Thread} (CODE_REVIEW §1.13 - §1.15). This class is kept, and kept
 *             working, so that existing subclasses still compile and run. It will be removed in a future major
 *             version.
 */
@Deprecated
public class SimpleThread extends Thread {

    private static final Logger LOG = LogManager.getLogger("CollectiveFramework");

    public volatile boolean isActive = true;
    public volatile long delay = -1;
    public volatile boolean started = false;

    /**
     * Replacement for the old reflective write into {@code Thread.target}.
     */
    private volatile Runnable targetOverride;

    protected SimpleThread() {
        super();
    }

    protected SimpleThread(Runnable target) {
        super(target);
        this.targetOverride = target;
    }

    protected SimpleThread(ThreadGroup group, Runnable target) {
        super(group, target);
        this.targetOverride = target;
    }

    protected SimpleThread(String name) {
        super(name);
    }

    protected SimpleThread(ThreadGroup group, String name) {
        super(group, name);
    }

    protected SimpleThread(Runnable target, String name) {
        super(target, name);
        this.targetOverride = target;
    }

    protected SimpleThread(ThreadGroup group, Runnable target, String name) {
        super(group, target, name);
        this.targetOverride = target;
    }

    protected SimpleThread(ThreadGroup group, Runnable target, String name, long stackSize) {
        super(group, target, name, stackSize);
        this.targetOverride = target;
    }

    /**
     * Runs the target repeatedly until the thread is interrupted or {@link #isActive} is cleared.
     * <p>
     * The old implementation spun with no pause whenever {@code delay} was negative and kept spinning after
     * {@code disable()} had cleared {@code started}, which is a 100% CPU leak (CODE_REVIEW §1.13). It also read
     * {@code SimpleRunnable.RESTRICT_THREAD_USAGE} as its loop condition, so setting that flag to false made the
     * thread exit after a single iteration instead of looping.
     */
    @Override
    public void run() {
        while (!isInterrupted() && isActive) {
            if (!started) {
                // Waiting to be started: yield instead of spinning.
                Thread.yield();
                continue;
            }
            try {
                Runnable target = targetOverride;
                if (target != null) target.run();
                else super.run();
            } catch (Throwable t) {
                LOG.error("SimpleThread " + getName() + " threw", t);
            }
            try {
                if (delay >= 0) sleep(delay);
                else Thread.yield();
            } catch (InterruptedException e) {
                Thread.currentThread()
                    .interrupt();
                return;
            }
        }
    }

    /**
     * Starts the thread. Safe to call on an already-started thread: it only re-arms {@link #isActive} instead of
     * throwing, which is what the previous override did by reflecting into {@code java.lang.Thread}.
     */
    @Override
    public synchronized void start() {
        started = true;
        if (getState() == State.NEW) super.start();
        else isActive = true;
    }

    /**
     * Sets the runnable that the thread executes.
     * <p>
     * The checked exceptions are declared purely for source compatibility: the old implementation reflected into
     * {@code java.lang.Thread#target} and could throw them, this one cannot. They are never thrown.
     *
     * @param runnable The runnable to execute
     * @throws NoSuchFieldException   never thrown; declared for source compatibility
     * @throws IllegalAccessException never thrown; declared for source compatibility
     */
    public void setTarget(Runnable runnable) throws NoSuchFieldException, IllegalAccessException {
        this.targetOverride = runnable;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SimpleThread)) return false;
        return getName().equals(((SimpleThread) other).getName());
    }

    @Override
    public int hashCode() {
        return getName().hashCode();
    }

    @Override
    public String toString() {
        return "SimpleThread(Name: " + getName() + " Is Active: " + isActive + ")";
    }
}
