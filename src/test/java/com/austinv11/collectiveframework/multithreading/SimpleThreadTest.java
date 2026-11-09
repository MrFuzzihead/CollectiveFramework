package com.austinv11.collectiveframework.multithreading;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.junit.Test;

/**
 * {@link SimpleThread} is deprecated: {@link SimpleRunnable} no longer uses it. It is kept working so that
 * existing subclasses still compile and run, and these tests pin that contract.
 * <p>
 * The important regression here is CODE_REVIEW §1.14: the old {@code setTarget} wrote
 * {@code Thread.class.getDeclaredField("target")} reflectively, which throws
 * {@link java.lang.reflect.InaccessibleObjectException} on Java 17+. These tests run on the Java 8 toolchain, so
 * the modern-JVM proof lives in {@code GameTestMod}; what is asserted here is that the target is honoured at all
 * and that the thread stops when told to.
 */
@SuppressWarnings("deprecation")
public class SimpleThreadTest {

    private static class NamedThread extends SimpleThread {

        NamedThread(String name) {
            super(name);
        }
    }

    private static void await(CountDownLatch latch) throws InterruptedException {
        Assert.assertTrue("timed out waiting for the thread", latch.await(10, TimeUnit.SECONDS));
    }

    @Test
    public void runsTheTarget() throws Exception {
        CountDownLatch ran = new CountDownLatch(1);
        SimpleThread thread = new SimpleThread(ran::countDown, "target-test");
        thread.start();

        await(ran);
        thread.isActive = false;
        thread.join(5000);
        Assert.assertFalse(thread.isAlive());
    }

    /** §1.14 - {@code setTarget} used to require reflective access to {@code java.lang.Thread}. */
    @Test
    public void setTargetSwapsTheTargetWithoutReflection() throws Exception {
        CountDownLatch second = new CountDownLatch(1);
        SimpleThread thread = new SimpleThread(new Runnable() {

            @Override
            public void run() {
                // idle until re-targeted
            }
        }, "retarget-test");
        thread.setTarget(second::countDown);
        thread.start();

        await(second);
        thread.isActive = false;
        thread.join(5000);
        Assert.assertFalse(thread.isAlive());
    }

    @Test
    public void startIsSafeToCallTwice() throws Exception {
        CountDownLatch ran = new CountDownLatch(1);
        SimpleThread thread = new SimpleThread(ran::countDown, "double-start");
        thread.start();
        // The old override reflected into Thread#threadStatus to decide this; now it just re-arms.
        thread.start();

        await(ran);
        thread.isActive = false;
        thread.join(5000);
        Assert.assertFalse(thread.isAlive());
    }

    @Test
    public void clearingIsActiveStopsTheLoop() throws Exception {
        final AtomicInteger invocations = new AtomicInteger();
        SimpleThread thread = new SimpleThread(new Runnable() {

            @Override
            public void run() {
                invocations.incrementAndGet();
            }
        }, "stop-test");
        thread.start();

        long deadline = System.currentTimeMillis() + 5000L;
        while (invocations.get() < 3 && System.currentTimeMillis() < deadline) Thread.sleep(5);

        thread.isActive = false;
        thread.join(5000);
        Assert.assertFalse("the loop did not stop", thread.isAlive());

        int settled = invocations.get();
        Thread.sleep(100);
        Assert.assertEquals(settled, invocations.get());
    }

    /**
     * §1.13 - the old loop condition was {@code SimpleRunnable.RESTRICT_THREAD_USAGE}, so setting that flag to
     * false made the thread exit after a single iteration. The loop is now driven by {@code isActive} only.
     */
    @Test
    public void restrictThreadUsageNoLongerControlsTheLoop() throws Exception {
        final AtomicInteger invocations = new AtomicInteger();
        SimpleThread thread = new SimpleThread(new Runnable() {

            @Override
            public void run() {
                invocations.incrementAndGet();
            }
        }, "flag-test");

        boolean previous = SimpleRunnable.RESTRICT_THREAD_USAGE;
        SimpleRunnable.RESTRICT_THREAD_USAGE = false;
        try {
            thread.start();
            long deadline = System.currentTimeMillis() + 5000L;
            while (invocations.get() < 3 && System.currentTimeMillis() < deadline) Thread.sleep(5);
            Assert.assertTrue("the loop stopped because of RESTRICT_THREAD_USAGE", invocations.get() >= 3);
        } finally {
            SimpleRunnable.RESTRICT_THREAD_USAGE = previous;
            thread.isActive = false;
            thread.join(5000);
        }
    }

    @Test
    public void aThrowingTargetDoesNotKillTheThread() throws Exception {
        final AtomicInteger invocations = new AtomicInteger();
        SimpleThread thread = new SimpleThread(new Runnable() {

            @Override
            public void run() {
                if (invocations.incrementAndGet() == 1) throw new IllegalStateException("boom");
            }
        }, "throw-test");
        thread.start();

        long deadline = System.currentTimeMillis() + 5000L;
        while (invocations.get() < 3 && System.currentTimeMillis() < deadline) Thread.sleep(5);

        thread.isActive = false;
        thread.join(5000);
        Assert.assertTrue("the thread died on the first throw", invocations.get() >= 3);
    }

    @Test
    public void delayIsHonoured() throws Exception {
        final CountDownLatch ran = new CountDownLatch(1);
        SimpleThread thread = new SimpleThread(ran::countDown, "delay-test");
        thread.delay = 5L;
        thread.start();
        await(ran);
        thread.isActive = false;
        thread.join(5000);
        Assert.assertFalse(thread.isAlive());
    }

    @Test
    public void stateReflectsTheLifecycle() throws Exception {
        CountDownLatch ran = new CountDownLatch(1);
        SimpleThread thread = new SimpleThread(ran::countDown, "state-test");
        Assert.assertEquals(Thread.State.NEW, thread.getState());
        thread.start();
        await(ran);
        thread.isActive = false;
        thread.join(5000);
        Assert.assertEquals(Thread.State.TERMINATED, thread.getState());
    }

    @Test
    public void equalityIsByName() {
        SimpleThread first = new NamedThread("same");
        SimpleThread second = new NamedThread("same");
        SimpleThread other = new NamedThread("other");

        Assert.assertEquals(first, first);
        Assert.assertEquals(first, second);
        Assert.assertEquals(first.hashCode(), second.hashCode());
        Assert.assertNotEquals(first, other);
        Assert.assertNotEquals(first, "not a thread");
    }

    @Test
    public void toStringMentionsTheName() {
        SimpleThread thread = new NamedThread("stringy");
        Assert.assertTrue(
            thread.toString(),
            thread.toString()
                .contains("stringy"));
    }
}
