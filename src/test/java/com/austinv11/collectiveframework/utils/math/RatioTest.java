package com.austinv11.collectiveframework.utils.math;

import org.junit.Assert;
import org.junit.Test;

/**
 * CODE_REVIEW §3.14 - {@code Ratio} divided by whatever
 * {@code MathUtils.findLowestCommonDenominator} returned, which was the <em>smallest</em> common divisor, so
 * {@code Ratio(1920, 1080)} reduced to 960:540 instead of 16:9. On the un-reduced path it also aliased the
 * caller's array, and both getters handed out the internal arrays.
 */
public class RatioTest {

    private static final double EPSILON = 1.0E-9;

    @Test
    public void reducesToTheSimplestForm() {
        Ratio ratio = new Ratio(1920, 1080);
        Assert.assertEquals(120d, ratio.getLCD(), 0d);
        Assert.assertArrayEquals(new double[] { 16, 9 }, ratio.getReducedRatio(), EPSILON);
    }

    @Test
    public void keepsTheOriginalValues() {
        Ratio ratio = new Ratio(1920, 1080);
        Assert.assertArrayEquals(new double[] { 1920, 1080 }, ratio.getOriginalRatio(), EPSILON);
    }

    @Test
    public void coprimeRatiosReduceByOne() {
        Ratio ratio = new Ratio(17, 19);
        Assert.assertEquals(1d, ratio.getLCD(), 0d);
        Assert.assertArrayEquals(new double[] { 17, 19 }, ratio.getReducedRatio(), EPSILON);
    }

    /** §3.14 - a divisor above the old 1024-iteration cap was never found, so the ratio stayed unreduced. */
    @Test
    public void largeRatiosStillReduce() {
        Ratio ratio = new Ratio(4096, 2048);
        Assert.assertArrayEquals(new double[] { 2, 1 }, ratio.getReducedRatio(), EPSILON);
    }

    @Test
    public void reducesThreePartRatios() {
        Ratio ratio = new Ratio(4, 8, 16);
        Assert.assertArrayEquals(new double[] { 1, 2, 4 }, ratio.getReducedRatio(), EPSILON);
    }

    /** §3.14 - the un-reduced path did {@code reducedValues = originalValues}, aliasing the caller's array. */
    @Test
    public void reducedRatioDoesNotAliasTheOriginalArray() {
        double[] values = { 0, 19 };
        Ratio ratio = new Ratio(values);
        values[0] = 999;

        Assert.assertArrayEquals(new double[] { 0, 19 }, ratio.getOriginalRatio(), EPSILON);
        Assert.assertArrayEquals(new double[] { 0, 19 }, ratio.getReducedRatio(), EPSILON);
    }

    @Test
    public void constructorCopiesTheCallersArray() {
        double[] values = { 1920, 1080 };
        Ratio ratio = new Ratio(values);
        values[0] = 1;

        Assert.assertArrayEquals(new double[] { 1920, 1080 }, ratio.getOriginalRatio(), EPSILON);
        Assert.assertArrayEquals(new double[] { 16, 9 }, ratio.getReducedRatio(), EPSILON);
    }

    /** §3.14 - the getters handed out the internal arrays, so a caller could mutate the ratio. */
    @Test
    public void gettersReturnCopies() {
        Ratio ratio = new Ratio(1920, 1080);

        ratio.getOriginalRatio()[0] = -1;
        ratio.getReducedRatio()[0] = -1;

        Assert.assertArrayEquals(new double[] { 1920, 1080 }, ratio.getOriginalRatio(), EPSILON);
        Assert.assertArrayEquals(new double[] { 16, 9 }, ratio.getReducedRatio(), EPSILON);
    }

    @Test
    public void aZeroComponentIsNotReduced() {
        Ratio ratio = new Ratio(0, 5);
        Assert.assertTrue(Double.isNaN(ratio.getLCD()));
        Assert.assertArrayEquals(new double[] { 0, 5 }, ratio.getReducedRatio(), EPSILON);
    }

    @Test
    public void aFractionalComponentIsNotReduced() {
        Ratio ratio = new Ratio(1.5, 3);
        Assert.assertTrue(Double.isNaN(ratio.getLCD()));
        Assert.assertArrayEquals(new double[] { 1.5, 3 }, ratio.getReducedRatio(), EPSILON);
    }
}
