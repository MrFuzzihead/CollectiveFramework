package com.austinv11.collectiveframework.utils.math;

import org.junit.Assert;
import org.junit.Test;

/**
 * CODE_REVIEW §3.14.
 * <ul>
 * <li>{@code isWholeNumber} compared {@code intValue()} with {@code longValue()}, which is true for every value
 * that fits in an {@code int} - including fractions.</li>
 * <li>{@code findLowestCommonDenominator} returned the <em>smallest</em> common divisor at or above 2, not the
 * greatest one, so {@code Ratio(1920, 1080)} reduced to 960:540 instead of 16:9. It also gave up after
 * {@code MAX_PASSES} iterations, so a 4096x2048 texture never reduced at all.</li>
 * <li>{@code getMin}/{@code getMax} threw {@code ArrayIndexOutOfBoundsException} on empty input.</li>
 * </ul>
 */
@SuppressWarnings("deprecation")
public class MathUtilsTest {

    // -------------------------------------------------------------------------- isWholeNumber

    @Test
    public void isWholeNumberAcceptsIntegers() {
        Assert.assertTrue(MathUtils.isWholeNumber(3));
        Assert.assertTrue(MathUtils.isWholeNumber(3.0d));
        Assert.assertTrue(MathUtils.isWholeNumber(-4L));
    }

    @Test
    public void isWholeNumberRejectsFractions() {
        Assert.assertFalse(MathUtils.isWholeNumber(3.5d));
        Assert.assertFalse(MathUtils.isWholeNumber(0.1d));
    }

    @Test
    public void isWholeNumberRejectsNaNInfinityAndNull() {
        Assert.assertFalse(MathUtils.isWholeNumber(Double.NaN));
        Assert.assertFalse(MathUtils.isWholeNumber(Double.POSITIVE_INFINITY));
        Assert.assertFalse(MathUtils.isWholeNumber(null));
    }

    // ------------------------------------------------------------------------------ min and max

    @Test
    public void getMinAndGetMax() {
        Assert.assertEquals(1d, MathUtils.getMin(3d, 1d, 2d), 0d);
        Assert.assertEquals(3d, MathUtils.getMax(3d, 1d, 2d), 0d);
        Assert.assertEquals(7d, MathUtils.getMin(7d), 0d);
        Assert.assertEquals(-1d, MathUtils.getMin(-1d, 5d), 0d);
    }

    /** §3.14 - this used to throw {@code ArrayIndexOutOfBoundsException}. */
    @Test(expected = IllegalArgumentException.class)
    public void getMinRejectsEmptyInput() {
        MathUtils.getMin();
    }

    @Test(expected = IllegalArgumentException.class)
    public void getMaxRejectsEmptyInput() {
        MathUtils.getMax();
    }

    @Test
    public void getMinRejectsNull() {
        try {
            MathUtils.getMin((double[]) null);
            Assert.fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // documented contract
        }
    }

    // ------------------------------------------------------------------------------------ ranges

    @Test
    public void isBetweenHonoursInclusivity() {
        Assert.assertTrue(MathUtils.isBetween(0d, 10d, 0d, true));
        Assert.assertTrue(MathUtils.isBetween(0d, 10d, 10d, true));
        Assert.assertFalse(MathUtils.isBetween(0d, 10d, 0d, false));
        Assert.assertFalse(MathUtils.isBetween(0d, 10d, 10d, false));
        Assert.assertTrue(MathUtils.isBetween(0d, 10d, 5d, false));
    }

    @Test
    public void isStringNumberParsesDoubles() {
        Assert.assertTrue(MathUtils.isStringNumber("1"));
        Assert.assertTrue(MathUtils.isStringNumber("1.5"));
        Assert.assertTrue(MathUtils.isStringNumber("-2e3"));
        Assert.assertFalse(MathUtils.isStringNumber("abc"));
        Assert.assertFalse(MathUtils.isStringNumber(""));
    }

    @Test
    public void isEvenNumber() {
        Assert.assertTrue(MathUtils.isEvenNumber(0));
        Assert.assertTrue(MathUtils.isEvenNumber(4));
        Assert.assertFalse(MathUtils.isEvenNumber(3));
    }

    // --------------------------------------------------------------------- greatest common divisor

    @Test
    public void greatestCommonDivisorReducesCommonRatios() {
        Assert.assertEquals(120d, MathUtils.findGreatestCommonDivisor(1920d, 1080d), 0d);
        Assert.assertEquals(2d, MathUtils.findGreatestCommonDivisor(4d, 6d), 0d);
        Assert.assertEquals(3d, MathUtils.findGreatestCommonDivisor(9d, 15d), 0d);
    }

    /** §3.14 - the old search started at 2, so coprime values produced NaN instead of 1. */
    @Test
    public void greatestCommonDivisorOfCoprimeValuesIsOne() {
        Assert.assertEquals(1d, MathUtils.findGreatestCommonDivisor(17d, 19d), 0d);
    }

    /** §3.14 - the old 1024-iteration cap meant a large divisor was never found. */
    @Test
    public void greatestCommonDivisorHandlesLargeValues() {
        Assert.assertEquals(2048d, MathUtils.findGreatestCommonDivisor(4096d, 2048d), 0d);
        Assert.assertEquals(1_000_000d, MathUtils.findGreatestCommonDivisor(1_000_000d, 3_000_000d), 0d);
    }

    @Test
    public void greatestCommonDivisorOfOneValueIsThatValue() {
        Assert.assertEquals(12d, MathUtils.findGreatestCommonDivisor(12d), 0d);
    }

    @Test
    public void greatestCommonDivisorRejectsNonPositiveValues() {
        Assert.assertTrue(Double.isNaN(MathUtils.findGreatestCommonDivisor(0d, 5d)));
        Assert.assertTrue(Double.isNaN(MathUtils.findGreatestCommonDivisor(-4d, 6d)));
    }

    @Test
    public void greatestCommonDivisorRejectsFractions() {
        Assert.assertTrue(Double.isNaN(MathUtils.findGreatestCommonDivisor(1.5d, 3d)));
    }

    @Test
    public void greatestCommonDivisorRejectsEmptyInput() {
        Assert.assertTrue(Double.isNaN(MathUtils.findGreatestCommonDivisor()));
    }

    /** The old name still works and now returns the same value. */
    @Test
    public void findLowestCommonDenominatorDelegatesToTheGcd() {
        Assert.assertEquals(120d, MathUtils.findLowestCommonDenominator(1920d, 1080d), 0d);
        Assert.assertEquals(1d, MathUtils.findLowestCommonDenominator(17d, 19d), 0d);
    }
}
