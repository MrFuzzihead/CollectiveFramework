package com.austinv11.collectiveframework.multithreading;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

/**
 * CODE_REVIEW §1.12. The old {@code FutureImpl} busy-waited in {@code get()}, threw
 * {@link IllegalMonitorStateException} from {@code get(timeout, unit)} because it called {@code wait(1)} without
 * owning the monitor, never threw {@link TimeoutException}, and hung forever if {@link HeavyCalculations#kill()}
 * had been called. It is now backed by a real {@link java.util.concurrent.ExecutorService}.
 */
public class HeavyCalculationsTest {

    private static class CountingCalculation implements ICalculations {

        final AtomicInteger runs = new AtomicInteger();
        final CountDownLatch latch;
        volatile long sleepMs;

        CountingCalculation(int expected) {
            latch = new CountDownLatch(expected);
        }

        @Override
        public void doCalculation() {
            runs.incrementAndGet();
            latch.countDown();
            if (sleepMs > 0) {
                try {
                    Thread.sleep(sleepMs);
                } catch (InterruptedException e) {
                    Thread.currentThread()
                        .interrupt();
                }
            }
        }
    }

    /** Method target for the reflective overloads. */
    public static class Receiver {

        static final AtomicInteger staticCalls = new AtomicInteger();
        final AtomicInteger calls = new AtomicInteger();
        volatile String lastArgument;

        public void accept(String argument) {
            calls.incrementAndGet();
            lastArgument = argument;
        }

        public static void acceptStatic(String argument) {
            staticCalls.incrementAndGet();
        }
    }

    private final List<HeavyCalculations> created = new ArrayList<HeavyCalculations>();

    private HeavyCalculations pool(int threads) {
        HeavyCalculations calculations = new HeavyCalculations(threads);
        created.add(calculations);
        return calculations;
    }

    @After
    public void shutdown() {
        for (HeavyCalculations calculations : created) {
            if (!calculations.isDead()) calculations.kill();
        }
        created.clear();
    }

    // ------------------------------------------------------------------------------ construction

    @Test(expected = IllegalArgumentException.class)
    public void rejectsZeroThreads() {
        new HeavyCalculations(0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNegativeThreads() {
        new HeavyCalculations(-3);
    }

    // ----------------------------------------------------------------------------------- running

    @Test
    public void runsTheCalculation() throws Exception {
        CountingCalculation calculation = new CountingCalculation(1);
        Future<CountingCalculation> future = pool(2).addCalculation(calculation);

        Assert.assertSame("get() must return the object that was passed", calculation, future.get());
        Assert.assertEquals(1, calculation.runs.get());
    }

    @Test
    public void runsManyCalculationsAcrossThreads() throws Exception {
        int count = 24;
        HeavyCalculations calculations = pool(4);
        List<Future<CountingCalculation>> futures = new ArrayList<Future<CountingCalculation>>();
        for (int i = 0; i < count; i++) futures.add(calculations.addCalculation(new CountingCalculation(1)));

        for (Future<CountingCalculation> future : futures) future.get();
        Assert.assertEquals(count, futures.size());
    }

    @Test
    public void getWithTimeoutReturnsWhenDone() throws Exception {
        CountingCalculation calculation = new CountingCalculation(1);
        Future<CountingCalculation> future = pool(1).addCalculation(calculation);

        Assert.assertSame(calculation, future.get(10, TimeUnit.SECONDS));
    }

    /** §1.12 - {@code get(timeout, unit)} used to throw {@link IllegalMonitorStateException}. */
    @Test(expected = TimeoutException.class)
    public void getWithTimeoutThrowsTimeoutException() throws Exception {
        CountingCalculation calculation = new CountingCalculation(1);
        calculation.sleepMs = 2000L;
        Future<CountingCalculation> future = pool(1).addCalculation(calculation);

        try {
            future.get(50, TimeUnit.MILLISECONDS);
        } finally {
            calculation.latch.countDown();
        }
    }

    @Test
    public void isDoneReflectsCompletion() throws Exception {
        CountingCalculation calculation = new CountingCalculation(1);
        calculation.sleepMs = 300L;
        Future<CountingCalculation> future = pool(1).addCalculation(calculation);

        Assert.assertFalse(future.isDone());
        future.get(10, TimeUnit.SECONDS);
        Assert.assertTrue(future.isDone());
    }

    // ------------------------------------------------------------------------- reflective overload

    @Test
    public void invokesAnInstanceMethodWithParameters() throws Exception {
        Receiver receiver = new Receiver();
        Future<Receiver> future = pool(1).addCalculation(receiver, "accept", "hello");

        Assert.assertSame(receiver, future.get());
        Assert.assertEquals(1, receiver.calls.get());
        Assert.assertEquals("hello", receiver.lastArgument);
    }

    @Test
    public void invokesAStaticMethodWithParameters() throws Exception {
        int before = Receiver.staticCalls.get();
        Future<ICalculations> future = pool(1).addCalculation(Receiver.class, "acceptStatic", "ignored");

        Assert.assertNotNull("the wrapper is returned", future.get());
        Assert.assertEquals(before + 1, Receiver.staticCalls.get());
    }

    @Test
    public void aMissingMethodSurfacesAsExecutionException() throws Exception {
        Future<Receiver> future = pool(1).addCalculation(new Receiver(), "noSuchMethod");
        try {
            future.get(10, TimeUnit.SECONDS);
            Assert.fail("expected ExecutionException");
        } catch (ExecutionException expected) {
            Assert.assertTrue(
                "cause should explain the missing method: " + expected.getCause(),
                expected.getCause() instanceof IllegalStateException);
        }
    }

    @Test
    public void aThrowingMethodSurfacesAsExecutionException() throws Exception {
        Future<ThrowingReceiver> future = pool(1).addCalculation(new ThrowingReceiver(), "explode");
        try {
            future.get(10, TimeUnit.SECONDS);
            Assert.fail("expected ExecutionException");
        } catch (ExecutionException expected) {
            Assert.assertTrue(expected.getCause() instanceof IllegalStateException);
        }
    }

    public static class ThrowingReceiver {

        public void explode() {
            throw new IllegalArgumentException("boom");
        }
    }

    @Test
    public void rejectsANonCalculationObject() {
        HeavyCalculations calculations = pool(1);
        try {
            calculations.addCalculation("not a calculation");
            Assert.fail("expected ClassCastException");
        } catch (ClassCastException expected) {
            // preserved from the original cast
        }
    }

    // --------------------------------------------------------------------------------------- kill

    @Test
    public void killMarksThePoolDead() {
        HeavyCalculations calculations = pool(1);
        Assert.assertFalse(calculations.isDead());
        calculations.kill();
        Assert.assertTrue(calculations.isDead());
    }

    /** §1.12 - the old implementation queued the work and left {@code get()} hanging forever. */
    @Test
    public void addingAfterKillIsRejected() {
        HeavyCalculations calculations = pool(1);
        calculations.kill();
        try {
            calculations.addCalculation(new CountingCalculation(1));
            Assert.fail("expected RejectedExecutionException");
        } catch (RejectedExecutionException expected) {
            // documented contract
        }
    }

    @Test
    public void aSubmittedFutureStillCompletesAfterKill() throws Exception {
        HeavyCalculations calculations = pool(1);
        CountingCalculation calculation = new CountingCalculation(1);
        Future<CountingCalculation> future = calculations.addCalculation(calculation);

        calculations.kill();
        Assert.assertSame(calculation, future.get(10, TimeUnit.SECONDS));
    }

    @Test
    public void awaitTerminationReportsCompletion() throws Exception {
        HeavyCalculations calculations = pool(1);
        calculations.addCalculation(new CountingCalculation(1));
        calculations.kill();
        Assert.assertTrue(calculations.awaitTermination(10, TimeUnit.SECONDS));
    }

    @Test
    public void killIsIdempotent() {
        HeavyCalculations calculations = pool(1);
        calculations.kill();
        calculations.kill();
        Assert.assertTrue(calculations.isDead());
    }
}
