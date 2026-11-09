package com.austinv11.collectiveframework.minecraft.books.simple;

import org.junit.Assert;
import org.junit.Test;

/**
 * CODE_REVIEW §2.23 - {@code ImageEntry#onRender} applied {@code glScaled} before translating to the entry, so
 * the entry's own position was scaled as well and a scaled image landed somewhere else entirely.
 * <p>
 * The fix is the translate-then-scale-then-draw-at-origin order in {@code onRender}; the arithmetic is factored
 * out into {@link ImageEntry#computeScale} so it can be checked without a GL context. The render path itself is
 * covered by the client game test.
 * <p>
 * Note that nothing extending {@code Entry} is unit-testable directly: {@code Entry extends GuiScreen}, whose
 * static initialiser constructs {@code RenderItem}, which needs LWJGL on the classpath. Keeping the maths pure
 * is what makes this testable at all.
 */
public class ImageEntryScaleTest {

    private static final double EPSILON = 1.0E-9;

    @Test
    public void aSquareSourceInASquareBoxScalesByTheRatioOfTheSides() {
        Assert.assertArrayEquals(new double[] { 2, 2 }, ImageEntry.computeScale(10, 10, 20, 20, true), EPSILON);
    }

    /** The uniform scale is the smaller of the two, so the image fits inside the box without distortion. */
    @Test
    public void retainingTheRatioUsesTheSmallerScale() {
        // 100x50 into 200x100: scaleX 2, scaleY 2.
        Assert.assertArrayEquals(new double[] { 2, 2 }, ImageEntry.computeScale(100, 50, 200, 100, true), EPSILON);
        // 100x50 into 200x40: scaleX 2, scaleY 0.8, so the uniform scale is 0.8.
        Assert.assertArrayEquals(new double[] { 0.8, 0.8 }, ImageEntry.computeScale(100, 50, 200, 40, true), EPSILON);
    }

    @Test
    public void notRetainingTheRatioScalesEachAxisIndependently() {
        Assert.assertArrayEquals(new double[] { 2, 0.8 }, ImageEntry.computeScale(100, 50, 200, 40, false), EPSILON);
    }

    @Test
    public void scalingDownWorks() {
        Assert.assertArrayEquals(new double[] { 0.5, 0.5 }, ImageEntry.computeScale(200, 200, 100, 100, true), EPSILON);
    }

    @Test
    public void aDegenerateSourceFallsBackToUnity() {
        Assert.assertArrayEquals(new double[] { 1, 1 }, ImageEntry.computeScale(0, 10, 100, 100, true), EPSILON);
        Assert.assertArrayEquals(new double[] { 1, 1 }, ImageEntry.computeScale(10, 0, 100, 100, false), EPSILON);
        Assert.assertArrayEquals(new double[] { 1, 1 }, ImageEntry.computeScale(-5, -5, 100, 100, true), EPSILON);
    }

    @Test
    public void aZeroSizedTargetCollapsesTheScale() {
        Assert.assertArrayEquals(new double[] { 0, 0 }, ImageEntry.computeScale(10, 10, 0, 0, true), EPSILON);
    }
}
