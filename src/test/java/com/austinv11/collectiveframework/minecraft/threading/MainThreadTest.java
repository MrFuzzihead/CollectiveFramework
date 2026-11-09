package com.austinv11.collectiveframework.minecraft.threading;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * CODE_REVIEW §3.1 - network message handlers run on the netty event loop in 1.7.10, and touching a world, a
 * chunk or a tile entity from there is unsafe. 1.7.10 has no {@code MinecraftServer} task queue (only
 * {@code Minecraft#func_152344_a} on the client), so the framework keeps its own and drains it from
 * {@link MainThreadTickHandler}.
 * <p>
 * {@link MainThread#drainServer()} and {@link MainThread#drainClient()} are package private, which is why this
 * test lives in the same package: it exercises the real drain, not a copy.
 */
public class MainThreadTest {

    @Before
    public void clearQueues() {
        MainThread.clear();
    }

    @After
    public void clearQueuesAgain() {
        MainThread.clear();
    }

    // ---------------------------------------------------------------------------- scheduling

    @Test
    public void schedulingDoesNotRunTheTaskImmediately() {
        final AtomicInteger runs = new AtomicInteger();
        MainThread.scheduleOnServer(new Runnable() {

            @Override
            public void run() {
                runs.incrementAndGet();
            }
        });

        Assert.assertEquals("the task must not run on the scheduling thread", 0, runs.get());
        Assert.assertEquals(1, MainThread.pendingServerCount());
    }

    @Test
    public void drainRunsTheTask() {
        final AtomicInteger runs = new AtomicInteger();
        MainThread.scheduleOnServer(new Runnable() {

            @Override
            public void run() {
                runs.incrementAndGet();
            }
        });

        MainThread.drainServer();

        Assert.assertEquals(1, runs.get());
        Assert.assertEquals(0, MainThread.pendingServerCount());
    }

    @Test
    public void tasksRunInSubmissionOrder() {
        final List<Integer> order = new ArrayList<Integer>();
        for (int i = 0; i < 20; i++) {
            final int value = i;
            MainThread.scheduleOnServer(new Runnable() {

                @Override
                public void run() {
                    order.add(value);
                }
            });
        }

        MainThread.drainServer();

        List<Integer> expected = new ArrayList<Integer>();
        for (int i = 0; i < 20; i++) expected.add(i);
        Assert.assertEquals(expected, order);
    }

    @Test
    public void theServerAndClientQueuesAreIndependent() {
        final AtomicInteger serverRuns = new AtomicInteger();
        final AtomicInteger clientRuns = new AtomicInteger();
        MainThread.scheduleOnServer(new Runnable() {

            @Override
            public void run() {
                serverRuns.incrementAndGet();
            }
        });
        MainThread.scheduleOnClient(new Runnable() {

            @Override
            public void run() {
                clientRuns.incrementAndGet();
            }
        });

        MainThread.drainServer();
        Assert.assertEquals(1, serverRuns.get());
        Assert.assertEquals("draining the server queue must not run client tasks", 0, clientRuns.get());
        Assert.assertEquals(1, MainThread.pendingClientCount());

        MainThread.drainClient();
        Assert.assertEquals(1, clientRuns.get());
        Assert.assertEquals(0, MainThread.pendingClientCount());
    }

    @Test
    public void clearDiscardsPendingTasks() {
        MainThread.scheduleOnServer(new Runnable() {

            @Override
            public void run() {
                Assert.fail("a cleared task must not run");
            }
        });
        MainThread.scheduleOnClient(new Runnable() {

            @Override
            public void run() {
                Assert.fail("a cleared task must not run");
            }
        });

        MainThread.clear();

        Assert.assertEquals(0, MainThread.pendingServerCount());
        Assert.assertEquals(0, MainThread.pendingClientCount());
        MainThread.drainServer();
        MainThread.drainClient();
    }

    @Test
    public void rejectsNullTasks() {
        try {
            MainThread.scheduleOnServer(null);
            Assert.fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // documented contract
        }
        try {
            MainThread.scheduleOnClient(null);
            Assert.fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // documented contract
        }
    }

    // -------------------------------------------------------------------------------- draining

    @Test
    public void aThrowingTaskDoesNotStopTheQueue() {
        final AtomicInteger runs = new AtomicInteger();
        MainThread.scheduleOnServer(new Runnable() {

            @Override
            public void run() {
                throw new IllegalStateException("boom");
            }
        });
        MainThread.scheduleOnServer(new Runnable() {

            @Override
            public void run() {
                runs.incrementAndGet();
            }
        });

        MainThread.drainServer();

        Assert.assertEquals("the second task was skipped after the first threw", 1, runs.get());
        Assert.assertEquals(0, MainThread.pendingServerCount());
    }

    @Test
    public void aTaskMayScheduleAnotherTask() {
        final AtomicInteger runs = new AtomicInteger();
        MainThread.scheduleOnServer(new Runnable() {

            @Override
            public void run() {
                runs.incrementAndGet();
                MainThread.scheduleOnServer(new Runnable() {

                    @Override
                    public void run() {
                        runs.incrementAndGet();
                    }
                });
            }
        });

        MainThread.drainServer();

        Assert.assertEquals("the chained task should have been drained in the same pass", 2, runs.get());
    }

    /** A self-rescheduling task must not stall the tick; the surplus is left for the next drain. */
    @Test
    public void theDrainIsBounded() {
        final AtomicInteger runs = new AtomicInteger();
        final AtomicReference<Runnable> self = new AtomicReference<Runnable>();
        Runnable task = new Runnable() {

            @Override
            public void run() {
                runs.incrementAndGet();
                MainThread.scheduleOnServer(self.get());
            }
        };
        self.set(task);
        MainThread.scheduleOnServer(task);

        MainThread.drainServer();

        Assert.assertEquals(MainThread.MAX_TASKS_PER_DRAIN, runs.get());
        Assert.assertEquals("the surplus must be left for the next tick", 1, MainThread.pendingServerCount());

        // Drain again to leave the queue clean.
        MainThread.clear();
    }

    @Test
    public void drainingAnEmptyQueueIsANoOp() {
        MainThread.drainServer();
        MainThread.drainClient();
        Assert.assertEquals(0, MainThread.pendingServerCount());
        Assert.assertEquals(0, MainThread.pendingClientCount());
    }

    // --------------------------------------------------------------------------- concurrency

    @Test
    public void tasksScheduledFromManyThreadsAreAllDrained() throws Exception {
        final int threads = 8;
        final int perThread = 100;
        final CountDownLatch scheduled = new CountDownLatch(threads);
        final AtomicInteger runs = new AtomicInteger();
        List<Thread> workers = new ArrayList<Thread>();

        for (int t = 0; t < threads; t++) {
            Thread worker = new Thread(new Runnable() {

                @Override
                public void run() {
                    for (int i = 0; i < perThread; i++) {
                        MainThread.scheduleOnServer(new Runnable() {

                            @Override
                            public void run() {
                                runs.incrementAndGet();
                            }
                        });
                    }
                    scheduled.countDown();
                }
            }, "scheduler-" + t);
            worker.setDaemon(true);
            workers.add(worker);
            worker.start();
        }

        Assert.assertTrue(scheduled.await(20, TimeUnit.SECONDS));
        // Drain until empty; MAX_TASKS_PER_DRAIN caps a single pass.
        for (int pass = 0; pass < 10 && MainThread.pendingServerCount() > 0; pass++) MainThread.drainServer();

        Assert.assertEquals(threads * perThread, runs.get());
        Assert.assertEquals(0, MainThread.pendingServerCount());
    }

    // ------------------------------------------------------------------------------------ side

    /**
     * Outside a running game FML has no side, so {@link MainThread#schedule(Runnable)} must fall back to the
     * server queue rather than throwing.
     */
    @Test
    public void scheduleFallsBackToTheServerQueueWithoutFml() {
        final AtomicInteger runs = new AtomicInteger();
        MainThread.schedule(new Runnable() {

            @Override
            public void run() {
                runs.incrementAndGet();
            }
        });

        MainThread.drainServer();
        Assert.assertEquals(1, runs.get());
    }
}
