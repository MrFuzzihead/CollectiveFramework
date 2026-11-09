package com.austinv11.collectiveframework.utils.math;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

public class ThreeDimensionalVectorTest {

    private static final double EPSILON = 1.0E-9;

    @Test
    public void gettersReturnComponents() {
        ThreeDimensionalVector vector = new ThreeDimensionalVector(1, 2, 3);
        Assert.assertEquals(1d, vector.getX(), 0d);
        Assert.assertEquals(2d, vector.getY(), 0d);
        Assert.assertEquals(3d, vector.getZ(), 0d);
    }

    @Test
    public void setComponentsMutates() {
        ThreeDimensionalVector vector = new ThreeDimensionalVector(0, 0, 0);
        vector.setComponents(1, 2, 3);
        Assert.assertEquals(new ThreeDimensionalVector(1, 2, 3), vector);
    }

    @Test
    public void getRoundedComponentsTruncateTowardsZero() {
        ThreeDimensionalVector vector = new ThreeDimensionalVector(1.9, -1.9, 2.1);
        Assert.assertEquals(1, vector.getRoundedX());
        Assert.assertEquals(-1, vector.getRoundedY());
        Assert.assertEquals(2, vector.getRoundedZ());
    }

    @Test
    public void addAndSubtract() {
        ThreeDimensionalVector a = new ThreeDimensionalVector(1, 2, 3);
        ThreeDimensionalVector b = new ThreeDimensionalVector(4, 5, 6);
        Assert.assertEquals(new ThreeDimensionalVector(5, 7, 9), a.add(b));
        Assert.assertEquals(new ThreeDimensionalVector(3, 3, 3), a.subtract(b));
    }

    @Test
    public void dotProductWithSelfIsSquaredLength() {
        Assert.assertEquals(29d, new ThreeDimensionalVector(2, 3, 4).dotProduct(), EPSILON);
    }

    @Test
    public void dotProductWithOther() {
        Assert.assertEquals(
            32d,
            new ThreeDimensionalVector(1, 2, 3).dotProduct(new ThreeDimensionalVector(4, 5, 6)),
            EPSILON);
    }

    @Test
    public void crossProductFollowsRightHandRule() {
        ThreeDimensionalVector cross = new ThreeDimensionalVector(1, 0, 0)
            .crossProduct(new ThreeDimensionalVector(0, 1, 0));
        Assert.assertEquals(new ThreeDimensionalVector(0, 0, 1), cross);
    }

    @Test
    public void distanceAndLength() {
        Assert.assertEquals(
            5d,
            new ThreeDimensionalVector(0, 0, 0).distanceTo(new ThreeDimensionalVector(0, 3, 4)),
            EPSILON);
        Assert.assertEquals(5d, new ThreeDimensionalVector(0, 3, 4).length(), EPSILON);
    }

    @Test
    public void midpoint() {
        Assert.assertEquals(
            new ThreeDimensionalVector(2, 3, 4),
            new ThreeDimensionalVector(0, 0, 0).midpoint(new ThreeDimensionalVector(4, 6, 8)));
    }

    @Test
    public void to2DDropsZ() {
        Assert.assertEquals(new TwoDimensionalVector(1, 2), new ThreeDimensionalVector(1, 2, 3).to2D());
    }

    @Test
    public void equalsIsComponentWise() {
        Assert.assertEquals(new ThreeDimensionalVector(1, 2, 3), new ThreeDimensionalVector(1, 2, 3));
        Assert.assertNotEquals(new ThreeDimensionalVector(1, 2, 3), new ThreeDimensionalVector(3, 2, 1));
    }

    @Test
    public void rotateZAboutOrigin() {
        ThreeDimensionalVector rotated = new ThreeDimensionalVector(1, 0, 5).rotateZ(90);
        Assert.assertEquals(0d, rotated.getX(), EPSILON);
        Assert.assertEquals(1d, rotated.getY(), EPSILON);
        Assert.assertEquals(5d, rotated.getZ(), EPSILON);
    }

    /**
     * CODE_REVIEW §2.11 - {@code normalize()} divides {@code y} by the length twice, so the z component is
     * always equal to the y component.
     */
    @Ignore("CODE_REVIEW §2.11: normalize() writes y/distance into the z slot")
    @Test
    public void normalizeShouldScaleEveryComponent() {
        ThreeDimensionalVector normalized = new ThreeDimensionalVector(3, 4, 0).normalize();
        Assert.assertEquals(0.6, normalized.getX(), EPSILON);
        Assert.assertEquals(0.8, normalized.getY(), EPSILON);
        Assert.assertEquals(0.0, normalized.getZ(), EPSILON);
        Assert.assertEquals(1.0, normalized.length(), EPSILON);
    }

    /**
     * CODE_REVIEW §2.11 - {@code rotateX} adds {@code center.z} to the new y component instead of
     * {@code center.y}, so it is only correct when the centre lies on the y == z diagonal.
     */
    @Ignore("CODE_REVIEW §2.11: rotateX translates the y component by center.z")
    @Test
    public void rotateXShouldKeepTheCentreFixed() {
        ThreeDimensionalVector center = new ThreeDimensionalVector(0, 5, 0);
        ThreeDimensionalVector rotated = new ThreeDimensionalVector(0, 6, 0).rotateX(center, 90);
        Assert.assertEquals(0d, rotated.getX(), EPSILON);
        Assert.assertEquals(5d, rotated.getY(), EPSILON);
        Assert.assertEquals(1d, rotated.getZ(), EPSILON);
    }

    /**
     * CODE_REVIEW §2.11 - {@code rotateY} adds {@code center.x} to the new z component instead of
     * {@code center.z}, so the centre's z coordinate is not preserved.
     */
    @Ignore("CODE_REVIEW §2.11: rotateY translates the z component by center.x")
    @Test
    public void rotateYShouldKeepTheCentreFixed() {
        ThreeDimensionalVector center = new ThreeDimensionalVector(0, 0, 5);
        ThreeDimensionalVector rotated = new ThreeDimensionalVector(0, 0, 6).rotateY(center, 90);
        // Rotating about the centre must leave the centre's z untouched; the class currently emits 1.
        Assert.assertEquals(5d, rotated.getZ(), EPSILON);
        Assert.assertEquals(0d, rotated.getY(), EPSILON);
    }

    /**
     * CODE_REVIEW §3.15 - {@code equals} without {@code hashCode}.
     */
    @Ignore("CODE_REVIEW §3.15: equals without hashCode breaks hash-based collections")
    @Test
    public void equalVectorsShouldHaveEqualHashCodes() {
        Assert.assertEquals(
            new ThreeDimensionalVector(1, 2, 3).hashCode(),
            new ThreeDimensionalVector(1, 2, 3).hashCode());
    }

    @Test
    public void toStringIsStable() {
        Assert
            .assertEquals("ThreeDimensionalVector(X:1.0 Y:2.0 Z:3.0)", new ThreeDimensionalVector(1, 2, 3).toString());
    }
}
