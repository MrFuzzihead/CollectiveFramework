package com.austinv11.collectiveframework;

import org.junit.Assert;
import org.junit.Test;

/**
 * The smoke-test hook is the only production code that can terminate the JVM, so its "off" path is worth
 * pinning down. The "on" path is exercised by the {@code smoke-test} CI workflow, not here.
 */
public class SmokeTestHookTest {

    @Test
    public void hookIsInertWithoutTheProperty() {
        String previous = System.getProperty(SmokeTestHook.ENABLE_PROPERTY);
        System.clearProperty(SmokeTestHook.ENABLE_PROPERTY);
        try {
            SmokeTestHook.scheduleExitIfRequested();
            // Reaching this line proves the hook returned instead of scheduling an exit.
        } finally {
            if (previous != null) System.setProperty(SmokeTestHook.ENABLE_PROPERTY, previous);
        }
    }

    @Test
    public void propertyNamesAreNamespaced() {
        Assert.assertEquals("collectiveframework.smokeTest", SmokeTestHook.ENABLE_PROPERTY);
        Assert.assertEquals("collectiveframework.smokeTest.delay", SmokeTestHook.DELAY_PROPERTY);
    }

    @Test
    public void successMarkerIsMachineReadable() {
        Assert.assertEquals("COLLECTIVEFRAMEWORK_SMOKE_TEST_OK", SmokeTestHook.SUCCESS_MARKER);
        Assert.assertFalse(SmokeTestHook.SUCCESS_MARKER.contains(" "));
    }
}
