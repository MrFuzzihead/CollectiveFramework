package com.austinv11.collectiveframework.utils;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Regression tests for {@link LogicUtils}.
 * <p>
 * The 2-input gates are correct today and are locked in below. {@link LogicUtils#xor} is only correct for
 * 0, 1 and 2 inputs; see CODE_REVIEW §2.16.
 */
public class LogicUtilsTest {

    @Test
    public void bufferIsIdentity() {
        Assert.assertTrue(LogicUtils.buffer(true));
        Assert.assertFalse(LogicUtils.buffer(false));
    }

    @Test
    public void notInverts() {
        Assert.assertFalse(LogicUtils.not(true));
        Assert.assertTrue(LogicUtils.not(false));
    }

    @Test
    public void orIsTrueWhenAnyInputIsTrue() {
        Assert.assertFalse(LogicUtils.or());
        Assert.assertFalse(LogicUtils.or(false, false));
        Assert.assertTrue(LogicUtils.or(false, true));
        Assert.assertTrue(LogicUtils.or(true, true));
        Assert.assertTrue(LogicUtils.or(false, false, true));
    }

    @Test
    public void norIsNegatedOr() {
        Assert.assertTrue(LogicUtils.nor(false, false));
        Assert.assertFalse(LogicUtils.nor(false, true));
    }

    @Test
    public void andIsTrueOnlyWhenAllInputsAreTrue() {
        Assert.assertTrue(LogicUtils.and());
        Assert.assertTrue(LogicUtils.and(true, true));
        Assert.assertFalse(LogicUtils.and(true, false));
        Assert.assertFalse(LogicUtils.and(false, false));
    }

    @Test
    public void nandIsNegatedAnd() {
        Assert.assertFalse(LogicUtils.nand(true, true));
        Assert.assertTrue(LogicUtils.nand(true, false));
    }

    @Test
    public void xorOfTwoInputsIsParity() {
        Assert.assertFalse(LogicUtils.xor());
        Assert.assertTrue(LogicUtils.xor(true));
        Assert.assertFalse(LogicUtils.xor(true, true));
        Assert.assertFalse(LogicUtils.xor(false, false));
        Assert.assertTrue(LogicUtils.xor(true, false));
        Assert.assertTrue(LogicUtils.xor(false, true));
    }

    @Test
    public void xnorIsNegatedXor() {
        Assert.assertTrue(LogicUtils.xnor(true, true));
        Assert.assertFalse(LogicUtils.xnor(true, false));
    }

    /**
     * CODE_REVIEW §2.16 - {@code xor} is implemented with a two-input short circuit, so it reports
     * "no odd number of trues" for 3+ inputs.
     */
    @Ignore("CODE_REVIEW §2.16: xor is not parity for 3+ inputs")
    @Test
    public void xorOfThreeInputsShouldBeParity() {
        Assert.assertTrue(LogicUtils.xor(true, true, true));
        Assert.assertFalse(LogicUtils.xor(true, true, false));
        Assert.assertTrue(LogicUtils.xor(true, false, false));
    }
}
