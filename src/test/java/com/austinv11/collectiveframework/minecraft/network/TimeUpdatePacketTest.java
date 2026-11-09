package com.austinv11.collectiveframework.minecraft.network;

import org.junit.Assert;
import org.junit.Test;

/**
 * CODE_REVIEW §1.16 - the time-change handler authorised requests against a {@code GameProfile} carried inside
 * the packet, so any client could claim to be an operator, and it applied the requested absolute time to every
 * loaded world with no bound. {@link TimeUpdatePacket#resolveRequestedTime} is the validation half; the
 * authorisation half now reads the player from the connection.
 */
public class TimeUpdatePacketTest {

    private static final long NOON = 6000L;

    @Test
    public void acceptsASmallForwardChange() {
        Long requested = TimeUpdatePacket.resolveRequestedTime(NOON, NOON, 600);
        Assert.assertNotNull(requested);
        Assert.assertEquals(NOON + 600L, requested.longValue());
    }

    @Test
    public void acceptsASmallBackwardChange() {
        Long requested = TimeUpdatePacket.resolveRequestedTime(NOON, NOON, -600);
        Assert.assertNotNull(requested);
        Assert.assertEquals(NOON - 600L, requested.longValue());
    }

    @Test
    public void acceptsAZeroChange() {
        Long requested = TimeUpdatePacket.resolveRequestedTime(NOON, NOON, 0);
        Assert.assertNotNull(requested);
        Assert.assertEquals(NOON, requested.longValue());
    }

    @Test
    public void acceptsAChangeExactlyAtTheLimit() {
        Long requested = TimeUpdatePacket.resolveRequestedTime(NOON, NOON + TimeUpdatePacket.MAX_TIME_CHANGE_TICKS, 0);
        Assert.assertNotNull(requested);
    }

    @Test
    public void rejectsAChangeBeyondTheLimit() {
        Assert.assertNull(
            TimeUpdatePacket.resolveRequestedTime(NOON, NOON + TimeUpdatePacket.MAX_TIME_CHANGE_TICKS + 1L, 0));
    }

    /**
     * A spoofed absolute time far from the server's clock is rejected. Note that any value <em>within</em> the
     * window is accepted regardless of the delta, which is unavoidable and harmless: only operators get this far,
     * and an operator can already use {@code /time set}.
     */
    @Test
    public void rejectsASpoofedAbsoluteTime() {
        long far = TimeUpdatePacket.MAX_TIME_CHANGE_TICKS + 1L;
        Assert.assertNull(TimeUpdatePacket.resolveRequestedTime(0L, far, 0));
        Assert.assertNull(TimeUpdatePacket.resolveRequestedTime(far, 0L, 0));
    }

    @Test
    public void rejectsANegativeResultingTime() {
        Assert.assertNull(TimeUpdatePacket.resolveRequestedTime(NOON, 0L, -1));
    }

    @Test
    public void rejectsOverflow() {
        Assert.assertNull(TimeUpdatePacket.resolveRequestedTime(0L, Long.MAX_VALUE, 1));
        Assert.assertNull(TimeUpdatePacket.resolveRequestedTime(0L, Long.MIN_VALUE, -1));
    }

    @Test
    public void theLimitIsOneInGameDay() {
        Assert.assertEquals(24_000L * 72L, TimeUpdatePacket.MAX_TIME_CHANGE_TICKS);
    }

    @Test
    public void defaultConstructorLeavesThePacketEmpty() {
        TimeUpdatePacket packet = new TimeUpdatePacket();
        Assert.assertEquals(0L, packet.startTime);
        Assert.assertEquals(0, packet.time);
        Assert.assertNull(packet.profile);
    }
}
