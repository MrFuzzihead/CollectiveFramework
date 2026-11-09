package com.austinv11.collectiveframework.utils.math;

import org.junit.Assert;
import org.junit.Test;

public class TwoDimensionalVectorTest {

    private static final double EPSILON = 1.0E-9;

    @Test
    public void gettersReturnComponents() {
        TwoDimensionalVector vector = new TwoDimensionalVector(1.5, -2.5);
        Assert.assertEquals(1.5, vector.getX(), 0d);
        Assert.assertEquals(-2.5, vector.getY(), 0d);
    }

    @Test
    public void setComponentsMutates() {
        TwoDimensionalVector vector = new TwoDimensionalVector(0, 0);
        vector.setComponents(3, 4);
        Assert.assertEquals(3, vector.getX(), 0d);
        Assert.assertEquals(4, vector.getY(), 0d);
    }

    /**
     * Despite the name, {@code getRoundedX/Y} truncate towards zero. Locked in so a rename or a real
     * rounding change is a deliberate decision.
     */
    @Test
    public void getRoundedComponentsTruncateTowardsZero() {
        TwoDimensionalVector vector = new TwoDimensionalVector(2.9, -2.9);
        Assert.assertEquals(2, vector.getRoundedX());
        Assert.assertEquals(-2, vector.getRoundedY());
    }

    @Test
    public void addAndSubtract() {
        TwoDimensionalVector a = new TwoDimensionalVector(1, 2);
        TwoDimensionalVector b = new TwoDimensionalVector(3, 5);
        Assert.assertEquals(new TwoDimensionalVector(4, 7), a.add(b));
        Assert.assertEquals(new TwoDimensionalVector(2, 3), a.subtract(b));
    }

    @Test
    public void normalizeScalesToUnitLength() {
        TwoDimensionalVector normalized = new TwoDimensionalVector(3, 4).normalize();
        Assert.assertEquals(0.6, normalized.getX(), EPSILON);
        Assert.assertEquals(0.8, normalized.getY(), EPSILON);
        Assert.assertEquals(1.0, normalized.length(), EPSILON);
    }

    @Test
    public void normalizeOfZeroVectorIsZero() {
        Assert.assertEquals(new TwoDimensionalVector(0, 0), new TwoDimensionalVector(0, 0).normalize());
    }

    @Test
    public void dotProductWithSelfIsSquaredLength() {
        Assert.assertEquals(25d, new TwoDimensionalVector(3, 4).dotProduct(), EPSILON);
    }

    @Test
    public void dotProductWithOther() {
        Assert.assertEquals(11d, new TwoDimensionalVector(1, 2).dotProduct(new TwoDimensionalVector(3, 4)), EPSILON);
    }

    @Test
    public void distanceAndLength() {
        Assert.assertEquals(5d, new TwoDimensionalVector(0, 0).distanceTo(new TwoDimensionalVector(3, 4)), EPSILON);
        Assert.assertEquals(5d, new TwoDimensionalVector(3, 4).length(), EPSILON);
    }

    @Test
    public void midpoint() {
        Assert.assertEquals(
            new TwoDimensionalVector(2, 3),
            new TwoDimensionalVector(0, 0).midpoint(new TwoDimensionalVector(4, 6)));
    }

    @Test
    public void rotateAboutOrigin() {
        TwoDimensionalVector rotated = new TwoDimensionalVector(1, 0).rotate(90);
        Assert.assertEquals(0d, rotated.getX(), EPSILON);
        Assert.assertEquals(1d, rotated.getY(), EPSILON);
    }

    @Test
    public void rotateAboutArbitraryCenter() {
        TwoDimensionalVector rotated = new TwoDimensionalVector(2, 1).rotate(new TwoDimensionalVector(1, 1), 90);
        Assert.assertEquals(1d, rotated.getX(), EPSILON);
        Assert.assertEquals(2d, rotated.getY(), EPSILON);
    }

    @Test
    public void to3DPlacesVectorOnZEqualsZero() {
        Assert.assertEquals(new ThreeDimensionalVector(1, 2, 0), new TwoDimensionalVector(1, 2).to3D());
    }

    @Test
    public void equalsIsComponentWise() {
        Assert.assertEquals(new TwoDimensionalVector(1, 2), new TwoDimensionalVector(1, 2));
        Assert.assertNotEquals(new TwoDimensionalVector(1, 2), new TwoDimensionalVector(2, 1));
        Assert.assertNotEquals(new TwoDimensionalVector(1, 2), "not a vector");
    }

    @Test
    public void compareToIsConsistentWithEquals() {
        TwoDimensionalVector vector = new TwoDimensionalVector(1, 2);
        Assert.assertEquals(0, vector.compareTo(new TwoDimensionalVector(1, 2)));
        Assert.assertEquals(-1, vector.compareTo(new TwoDimensionalVector(2, 2)));
        Assert.assertEquals(1, vector.compareTo(new TwoDimensionalVector(0, 2)));
    }

    /**
     * CODE_REVIEW §3.15 - {@code equals} is overridden without {@code hashCode}, so value-equal vectors are
     * distinct hash keys. {@code Variable2DShape} and {@code ArrayUtils.removeRepeats} rely on both.
     */
    @Test
    public void equalVectorsShouldHaveEqualHashCodes() {
        Assert.assertEquals(new TwoDimensionalVector(1, 2).hashCode(), new TwoDimensionalVector(1, 2).hashCode());
    }

    @Test
    public void toStringIsStable() {
        Assert.assertEquals("TwoDimensionalVector(X:1.0 Y:2.0)", new TwoDimensionalVector(1, 2).toString());
    }
}
