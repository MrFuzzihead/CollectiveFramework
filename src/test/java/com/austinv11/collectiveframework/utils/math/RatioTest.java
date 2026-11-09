package com.austinv11.collectiveframework.utils.math;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

public class RatioTest {

    private static final double EPSILON = 1.0E-9;

    /**
     * Locked-in description of today's behaviour: {@code MathUtils.findLowestCommonDenominator} returns the
     * smallest common divisor (2), so a 1920x1080 ratio reduces to 960:540 instead of 16:9.
     * CODE_REVIEW §3.14.
     */
    @Test
    public void reducesByTheSmallestCommonDivisor() {
        Ratio ratio = new Ratio(1920, 1080);
        Assert.assertEquals(2d, ratio.getLCD(), 0d);
        Assert.assertArrayEquals(new double[] { 960, 540 }, ratio.getReducedRatio(), EPSILON);
    }

    /**
     * CODE_REVIEW §3.14 - {@code Ratio} divides by whatever {@code findLowestCommonDenominator} returns, so
     * the reduced ratio is not actually reduced.
     */
    @Ignore("CODE_REVIEW §3.14: Ratio divides by the smallest common divisor, not the GCD")
    @Test
    public void reducesToTheSimplestForm() {
        Ratio ratio = new Ratio(1920, 1080);
        Assert.assertArrayEquals(new double[] { 16, 9 }, ratio.getReducedRatio(), EPSILON);
    }

    @Test
    public void keepsTheOriginalValues() {
        Ratio ratio = new Ratio(1920, 1080);
        Assert.assertArrayEquals(new double[] { 1920, 1080 }, ratio.getOriginalRatio(), EPSILON);
    }

    @Test
    public void coprimeRatiosAreNotReduced() {
        Ratio ratio = new Ratio(17, 19);
        Assert.assertTrue(Double.isNaN(ratio.getLCD()));
        Assert.assertArrayEquals(new double[] { 17, 19 }, ratio.getReducedRatio(), EPSILON);
    }

    /**
     * CODE_REVIEW §3.14 - on the un-reduced path {@code reducedValues = originalValues} aliases the caller's
     * array, so {@code getReducedRatio() == getOriginalRatio()}. Mutating one silently mutates the other.
     */
    @Ignore("CODE_REVIEW §3.14: the un-reduced path aliases the original array")
    @Test
    public void reducedRatioShouldNotAliasTheOriginalArray() {
        double[] values = { 17, 19 };
        Ratio ratio = new Ratio(values);
        values[0] = 999;
        Assert.assertEquals(17d, ratio.getOriginalRatio()[0], EPSILON);
    }

    /**
     * CODE_REVIEW §3.14 - a divisor above {@code MathUtils.MAX_PASSES} is never found, so a 4096x2048
     * texture ratio stays unreduced.
     */
    @Ignore("CODE_REVIEW §3.14: the 1024 pass cap skips large divisors")
    @Test
    public void largeRatiosShouldStillReduce() {
        Ratio ratio = new Ratio(4096, 2048);
        Assert.assertArrayEquals(new double[] { 2, 1 }, ratio.getReducedRatio(), EPSILON);
    }
}
