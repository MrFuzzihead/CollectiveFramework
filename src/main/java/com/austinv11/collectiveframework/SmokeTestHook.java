package com.austinv11.collectiveframework;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;

/**
 * Development/CI only. When the {@code collectiveframework.smokeTest} system property is set, the game logs a
 * machine-readable marker after FML has finished loading and then exits. This lets a CI job boot the real
 * dedicated server (or the client under Xvfb) without needing an operator to type {@code stop}.
 * <p>
 * The hook is a no-op in normal gameplay: the property is only set by the {@code -PsmokeTest} Gradle property.
 */
public class SmokeTestHook {

    public static final String ENABLE_PROPERTY = "collectiveframework.smokeTest";
    public static final String DELAY_PROPERTY = "collectiveframework.smokeTest.delay";
    public static final String SUCCESS_MARKER = "COLLECTIVEFRAMEWORK_SMOKE_TEST_OK";

    // Fired by CollectiveFramework on FMLServerStartedEvent. On the dedicated server we must wait for this
    // before exiting: loadComplete fires before the world finishes generating, so a fixed timer exits while
    // the spawn area is still being prepared and the server never prints "Done".
    private static final CountDownLatch SERVER_STARTED = new CountDownLatch(1);

    private SmokeTestHook() {}

    public static void notifyServerStarted() {
        SERVER_STARTED.countDown();
    }

    public static void scheduleExitIfRequested() {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) return;

        final long delay = Long.getLong(DELAY_PROPERTY, 2000L);
        final boolean serverSide = isServerSide();
        final Thread thread = new Thread(new Runnable() {

            @Override
            public void run() {
                try {
                    if (serverSide) {
                        // A generous window: a missing server start should fail the CI job via its own timeout
                        // rather than exiting early and masking the real problem.
                        SERVER_STARTED.await(10, TimeUnit.MINUTES);
                    }
                    Thread.sleep(Math.max(0L, delay));
                } catch (InterruptedException e) {
                    Thread.currentThread()
                        .interrupt();
                    return;
                }

                System.out.println(SUCCESS_MARKER + ": FML load complete, shutting down");
                FMLCommonHandler.instance()
                    .exitJava(0, false);
            }
        }, "CollectiveFramework-smoke-test");
        thread.setDaemon(true);
        thread.start();
    }

    private static boolean isServerSide() {
        try {
            return FMLCommonHandler.instance()
                .getSide() == Side.SERVER;
        } catch (Throwable t) {
            // Outside FML (unit tests, early class loading): treat as a client so no latch wait is introduced.
            return false;
        }
    }
}
