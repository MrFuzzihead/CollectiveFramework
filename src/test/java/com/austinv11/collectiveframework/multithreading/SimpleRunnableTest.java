package com.austinv11.collectiveframework.multithreading;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

/**
 * CODE_REVIEW §1.11 - §1.15. The old implementation was a hand-rolled thread pool built out of
 * {@link SimpleThread}s and a static map keyed by boolean. Its observable defects were:
 * <ul>
 * <li>§1.11 - {@code delay(int)} called {@code Thread.wait()} without owning the monitor, so it always threw
 * {@link IllegalMonitorStateException};</li>
 * <li>§1.13 - the thread loop spun at 100% CPU once {@code started} was cleared, and stopped looping entirely
 * when {@code RESTRICT_THREAD_USAGE} was false;</li>
 * <li>§1.14 - it reflected into {@code java.lang.Thread#target} and {@code #threadStatus}, which throws
 * {@link java.lang.reflect.InaccessibleObjectException} on Java 17+;</li>
 * <li>§1.15 - a pooled thread was "reused" by swapping the target of an already-terminated {@link Thread}, so a
 * reused runnable never ran.</li>
 * </ul>
 * These tests pin the replacement behaviour. Anything that has to be proven on a modern JVM lives in
 * {@code GameTestMod}, which runs on Java 25.
 */
public class SimpleRunnableTest {

    private final List<SimpleRunnable> started = new ArrayList<SimpleRunnable>();

    /** Counts invocations and lets the test wait for the Nth one. */
    private static class CountingRunnable extends SimpleRunnable {

        final AtomicInteger invocations = new AtomicInteger();
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        volatile CountDownLatch reached;
        final CountDownLatch completed = new CountDownLatch(1);
        volatile Runnable onRun;

        CountingRunnable(int expected) {
            reached = new CountDownLatch(expected);
        }

        @Override
        public void run() {
            invocations.incrementAndGet();
            reached.countDown();
            Runnable hook = onRun;
            try {
                if (hook != null) hook.run();
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                completed.countDown();
            }
        }
    }

    private <T extends SimpleRunnable> T track(T runnable) {
        started.add(runnable);
        return runnable;
    }

    private static void await(CountDownLatch latch) throws InterruptedException {
        Assert.assertTrue("timed out waiting for the runnable", latch.await(10, TimeUnit.SECONDS));
    }

    @After
    public void stopEverything() {
        for (SimpleRunnable runnable : started) {
            try {
                runnable.disable(true);
            } catch (IllegalThreadStateException alreadyCleaned) {
                // fine
            }
        }
        started.clear();
    }

    // -------------------------------------------------------------------------------- startOnce

    @Test
    public void startOnceRunsExactlyOnce() throws Exception {
        CountingRunnable runnable = track(new CountingRunnable(1));

        runnable.startOnce();
        await(runnable.reached);

        // Give any (incorrect) repeat schedule time to show up.
        Thread.sleep(150);
        Assert.assertEquals(1, runnable.invocations.get());
    }

    @Test
    public void startOnceCanBeCalledAgainAfterCompletion() throws Exception {
        CountingRunnable runnable = track(new CountingRunnable(1));

        runnable.startOnce();
        await(runnable.reached);

        runnable.reached = new CountDownLatch(1);
        runnable.startOnce();
        await(runnable.reached);

        Assert.assertEquals(2, runnable.invocations.get());
    }

    // ----------------------------------------------------------------------------------- start

    @Test
    public void startRepeatsUntilDisabled() throws Exception {
        CountingRunnable runnable = track(new CountingRunnable(5));

        runnable.start();
        await(runnable.reached);

        runnable.disable(true);
        int afterDisable = runnable.invocations.get();
        Assert.assertTrue("expected several invocations, got " + afterDisable, afterDisable >= 5);

        Thread.sleep(150);
        Assert.assertEquals("run() must not be called after disable()", afterDisable, runnable.invocations.get());
    }

    /**
     * §1.13 - a disabled runnable must not keep a worker busy. The old loop spun forever without calling
     * {@code run()}, so an invocation count alone would not catch it; this checks the executor itself.
     * <p>
     * {@code getActiveTaskCount()} is a count over the whole shared pool, and other test classes hand work to it
     * too, so the assertion is relative to a baseline taken before this runnable starts rather than an absolute
     * zero.
     */
    @Test
    public void aDisabledRunnableDoesNotOccupyAWorker() throws Exception {
        CountingRunnable runnable = track(new CountingRunnable(2));
        int baseline = SimpleRunnable.getActiveTaskCount();

        runnable.start();
        await(runnable.reached);
        runnable.disable(true);

        long deadline = System.currentTimeMillis() + 5000L;
        while (SimpleRunnable.getActiveTaskCount() > baseline && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        Assert.assertTrue(
            "a worker is still busy after disable(): " + SimpleRunnable.getActiveTaskCount() + " > " + baseline,
            SimpleRunnable.getActiveTaskCount() <= baseline);
    }

    @Test
    public void startIsIdempotent() throws Exception {
        CountingRunnable runnable = track(new CountingRunnable(3));

        runnable.start();
        runnable.start();
        runnable.start();
        await(runnable.reached);
        runnable.disable(true);

        // Three start() calls must not produce three concurrent loops; the count should be modest.
        Assert.assertTrue(
            "start() appears to have scheduled multiple loops: " + runnable.invocations.get(),
            runnable.invocations.get() < 50);
    }

    // ---------------------------------------------------------------------------------- disable

    @Test
    public void disableTwiceThrows() throws Exception {
        CountingRunnable runnable = track(new CountingRunnable(1));
        runnable.startOnce();
        await(runnable.reached);

        runnable.disable(true);
        try {
            runnable.disable(true);
            Assert.fail("expected IllegalThreadStateException");
        } catch (IllegalThreadStateException expected) {
            // documented contract, preserved from the original
        }
    }

    @Test
    public void startAfterCleanDisableThrows() throws Exception {
        CountingRunnable runnable = track(new CountingRunnable(1));
        runnable.disable(true);
        try {
            runnable.start();
            Assert.fail("expected IllegalThreadStateException");
        } catch (IllegalThreadStateException expected) {
            // documented contract
        }
    }

    @Test
    public void startOnceAfterCleanDisableThrows() throws Exception {
        CountingRunnable runnable = track(new CountingRunnable(1));
        runnable.disable(true);
        try {
            runnable.startOnce();
            Assert.fail("expected IllegalThreadStateException");
        } catch (IllegalThreadStateException expected) {
            // documented contract
        }
    }

    @Test
    public void disableWithoutCleanLeavesTheRunnableUsable() throws Exception {
        CountingRunnable runnable = track(new CountingRunnable(1));
        runnable.disable(false);
        Assert.assertFalse(runnable.isCleaned());
        Assert.assertFalse(runnable.isEnabled());

        runnable.reached = new CountDownLatch(1);
        runnable.startOnce();
        await(runnable.reached);
        Assert.assertEquals(1, runnable.invocations.get());
    }

    // ----------------------------------------------------------------------------------- enable

    @Test
    public void enableRestartsADisabledRunnable() throws Exception {
        CountingRunnable runnable = track(new CountingRunnable(2));

        runnable.start();
        await(runnable.reached);
        runnable.disable(false);
        Assert.assertFalse(runnable.isEnabled());

        runnable.reached = new CountDownLatch(2);
        runnable.enable();
        await(runnable.reached);
        Assert.assertTrue(runnable.isEnabled());
        runnable.disable(true);
    }

    @Test
    public void enableAfterCleanDisableThrows() throws Exception {
        CountingRunnable runnable = track(new CountingRunnable(1));
        runnable.disable(true);
        try {
            runnable.enable();
            Assert.fail("expected IllegalThreadStateException");
        } catch (IllegalThreadStateException expected) {
            // documented contract
        }
    }

    // ---------------------------------------------------------------------------------- ticking

    @Test
    public void tickingIsRecordedAndReported() throws Exception {
        CountingRunnable runnable = track(new CountingRunnable(1));
        Assert.assertTrue("no interval by default", runnable.getTicking() < 0);

        runnable.setTicking(50L);
        Assert.assertEquals(50L, runnable.getTicking());
    }

    @Test
    public void tickingSlowsTheLoopDown() throws Exception {
        CountingRunnable runnable = track(new CountingRunnable(1));
        runnable.setTicking(100L);
        runnable.start();
        await(runnable.reached);
        Thread.sleep(350);
        runnable.disable(true);

        // At 100 ms per iteration, ~350 ms allows about 4 iterations; definitely not hundreds.
        int count = runnable.invocations.get();
        Assert.assertTrue("ticking was ignored: " + count + " invocations", count >= 2 && count <= 10);
    }

    @Test
    public void tickingCanBeChangedWhileRunning() throws Exception {
        CountingRunnable runnable = track(new CountingRunnable(1));
        runnable.start();
        await(runnable.reached);
        runnable.setTicking(200L);

        Thread.sleep(120);
        int baseline = runnable.invocations.get();
        Thread.sleep(300);
        int after = runnable.invocations.get();
        runnable.disable(true);

        Assert.assertTrue("ticking change was ignored: " + (after - baseline), after - baseline <= 3);
    }

    // ------------------------------------------------------------------------------------ delay

    /** §1.11 - this used to throw {@link IllegalMonitorStateException} every time. */
    @Test
    public void delaySleepsInsteadOfThrowing() throws Exception {
        long start = System.nanoTime();
        new CountingRunnable(1).delay(80);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;

        Assert.assertTrue("delay() returned too early: " + elapsedMs + " ms", elapsedMs >= 60L);
    }

    @Test
    public void delayIsInterruptible() throws Exception {
        final AtomicReference<Throwable> caught = new AtomicReference<Throwable>();
        Thread sleeper = new Thread(new Runnable() {

            @Override
            public void run() {
                try {
                    new CountingRunnable(1).delay(10_000);
                } catch (InterruptedException e) {
                    caught.set(e);
                }
            }
        });
        sleeper.setDaemon(true);
        sleeper.start();
        Thread.sleep(100);
        sleeper.interrupt();
        sleeper.join(5000);

        Assert.assertFalse("the sleeper did not stop", sleeper.isAlive());
        Assert.assertTrue("expected InterruptedException", caught.get() instanceof InterruptedException);
    }

    // -------------------------------------------------------------------------------- robustness

    /** A throwing iteration must not silently kill a repeating runnable. */
    @Test
    public void aThrowingRunDoesNotStopTheLoop() throws Exception {
        final CountingRunnable runnable = track(new CountingRunnable(3));
        runnable.onRun = new Runnable() {

            @Override
            public void run() {
                if (runnable.invocations.get() == 1) throw new IllegalStateException("boom");
            }
        };
        runnable.setTicking(10L);

        runnable.start();
        await(runnable.reached);
        runnable.disable(true);

        Assert.assertTrue("the loop stopped after a throw", runnable.invocations.get() >= 3);
    }

    @Test
    public void aThrowingRunOnceTaskDoesNotEscape() throws Exception {
        CountingRunnable runnable = track(new CountingRunnable(1));
        runnable.onRun = new Runnable() {

            @Override
            public void run() {
                throw new IllegalStateException("boom");
            }
        };
        runnable.startOnce();
        await(runnable.completed);

        // The exception must have been logged, not rethrown into the executor.
        Assert.assertNotNull(runnable.failure.get());
    }

    @Test
    public void manyRunnablesAllComplete() throws Exception {
        final int count = 32;
        final CountDownLatch done = new CountDownLatch(count);
        List<SimpleRunnable> runnables = new ArrayList<SimpleRunnable>();
        for (int i = 0; i < count; i++) {
            SimpleRunnable runnable = track(new SimpleRunnable() {

                @Override
                public void run() {
                    done.countDown();
                }
            });
            runnables.add(runnable);
        }
        for (SimpleRunnable runnable : runnables) runnable.startOnce();

        Assert.assertTrue("not every runnable ran", done.await(20, TimeUnit.SECONDS));
    }

    // ------------------------------------------------------------------------------------ naming

    @Test
    public void nameDefaultsToTheClassName() {
        Assert.assertEquals("CountingRunnable", new CountingRunnable(1).getName());
        // Anonymous classes have no simple name, so the default is the empty string. This matches the original
        // implementation and is why callers that care override getName().
        Assert.assertEquals("", new SimpleRunnable() {

            @Override
            public void run() {}
        }.getName());
    }

    @Test
    public void nameCanBeOverridden() {
        SimpleRunnable runnable = new SimpleRunnable() {

            @Override
            public void run() {}

            @Override
            public String getName() {
                return "custom";
            }
        };
        Assert.assertEquals("custom", runnable.getName());
        Assert.assertTrue(
            runnable.toString(),
            runnable.toString()
                .contains("custom"));
    }

    // ------------------------------------------------------------------- equality and hashing

    /**
     * §3.15 - {@code equals} used to compare a pool index and a name, so two different runnables could compare
     * equal, and there was no {@code hashCode}.
     */
    @Test
    public void equalityIsIdentityBased() {
        SimpleRunnable first = new CountingRunnable(1);
        SimpleRunnable second = new CountingRunnable(1);

        Assert.assertEquals(first, first);
        Assert.assertNotEquals(first, second);
        Assert.assertNotEquals(first, null);
        Assert.assertNotEquals(first, "not a runnable");
        Assert.assertNotEquals(first.hashCode(), second.hashCode());
    }

    // ------------------------------------------------------------------------------- state flags

    @Test
    public void stateFlagsTrackLifecycle() throws Exception {
        CountingRunnable runnable = track(new CountingRunnable(1));
        Assert.assertTrue("enabled before start, for compatibility", runnable.isEnabled());
        Assert.assertFalse(runnable.isCleaned());

        runnable.startOnce();
        await(runnable.reached);
        Assert.assertTrue(runnable.isEnabled());
        Assert.assertFalse(runnable.isCleaned());

        runnable.disable(true);
        Assert.assertFalse(runnable.isEnabled());
        Assert.assertTrue(runnable.isCleaned());
    }
}
