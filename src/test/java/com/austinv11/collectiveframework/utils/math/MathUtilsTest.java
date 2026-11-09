package com.austinv11.collectiveframework.utils.math;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

public class MathUtilsTest {

    @Test
    public void isWholeNumberAcceptsIntegers() {
        Assert.assertTrue(MathUtils.isWholeNumber(3));
        Assert.assertTrue(MathUtils.isWholeNumber(3.0d));
    }

    /**
     * Locked-in description of today's behaviour: {@code isWholeNumber} compares {@code intValue()} with
     * {@code longValue()}, which is true for every value that fits in an int - fractional or not.
     * CODE_REVIEW §3.14.
     */
    @Test
    public void isWholeNumberCurrentlyAcceptsFractionsToo() {
        Assert.assertTrue(MathUtils.isWholeNumber(3.5d));
    }

    /**
     * CODE_REVIEW §3.14 - {@code isWholeNumber} should compare against the value, not against a narrower
     * truncation of it.
     */
    @Ignore("CODE_REVIEW §3.14: isWholeNumber compares intValue() to longValue()")
    @Test
    public void isWholeNumberShouldRejectFractions() {
        Assert.assertFalse(MathUtils.isWholeNumber(3.5d));
    }

    @Test
    public void getMinAndGetMax() {
        Assert.assertEquals(1d, MathUtils.getMin(3d, 1d, 2d), 0d);
        Assert.assertEquals(3d, MathUtils.getMax(3d, 1d, 2d), 0d);
        Assert.assertEquals(7d, MathUtils.getMin(7d), 0d);
    }

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

    /**
     * Locked-in description of today's behaviour: despite the name, the method returns the *smallest*
     * common divisor at or above 2, not the greatest one. CODE_REVIEW §3.14.
     */
    @Test
    public void findLowestCommonDenominatorReturnsTheSmallestCommonDivisor() {
        Assert.assertEquals(2d, MathUtils.findLowestCommonDenominator(1920d, 1080d), 0d);
        Assert.assertEquals(2d, MathUtils.findLowestCommonDenominator(4d, 6d), 0d);
        Assert.assertEquals(3d, MathUtils.findLowestCommonDenominator(9d, 15d), 0d);
    }

    /**
     * CODE_REVIEW §3.14 - the method is documented as an LCD helper and is used by {@code Ratio} to reduce
     * ratios, which requires the *greatest* common divisor.
     */
    @Ignore("CODE_REVIEW §3.14: returns the smallest common divisor instead of the GCD")
    @Test
    public void findLowestCommonDenominatorShouldReturnTheGreatestCommonDivisor() {
        Assert.assertEquals(120d, MathUtils.findLowestCommonDenominator(1920d, 1080d), 0d);
        Assert.assertEquals(2d, MathUtils.findLowestCommonDenominator(4d, 6d), 0d);
    }

    @Test
    public void findLowestCommonDenominatorReturnsNaNWhenNothingUnderTheCapDivides() {
        // 17 and 19 are coprime, and the search starts at 2, so 1 is never returned.
        Assert.assertTrue(Double.isNaN(MathUtils.findLowestCommonDenominator(17d, 19d)));
    }

    /**
     * CODE_REVIEW §3.14 - the method computes the GCD (not the LCD) and starts at 2, so coprime values
     * return NaN instead of 1. A ratio such as 4096:2048 therefore never reduces.
     */
    @Ignore("CODE_REVIEW §3.14: findLowestCommonDenominator is really a capped GCD search")
    @Test
    public void findLowestCommonDenominatorShouldReturnOneForCoprimeValues() {
        Assert.assertEquals(1d, MathUtils.findLowestCommonDenominator(17d, 19d), 0d);
    }

    /**
     * CODE_REVIEW §3.14 - {@code MAX_PASSES = 1024} caps the search, so large common divisors are missed.
     */
    @Ignore("CODE_REVIEW §3.14: the 1024 pass cap skips large divisors")
    @Test
    public void findLowestCommonDenominatorShouldHandleLargeDivisors() {
        Assert.assertEquals(2048d, MathUtils.findLowestCommonDenominator(4096d, 2048d), 0d);
    }

    @Test
    public void getMinAndGetMaxThrowOnEmptyInput() {
        try {
            MathUtils.getMin();
            Assert.fail("expected an exception for empty input");
        } catch (ArrayIndexOutOfBoundsException expected) {
            // CODE_REVIEW §3.14: no empty-input guard.
        }
    }
}
