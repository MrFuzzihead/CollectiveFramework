package com.austinv11.collectiveframework.utils.math.geometry;

import org.junit.Assert;
import org.junit.Test;

import com.austinv11.collectiveframework.utils.math.TwoDimensionalVector;

public class CircleTest {

    private static final double EPSILON = 1.0E-9;

    @Test(expected = IncompatibleDimensionsException.class)
    public void rejectsNonPositiveRadius() throws IncompatibleDimensionsException {
        new Circle(0);
    }

    @Test(expected = IncompatibleDimensionsException.class)
    public void rejectsNegativeRadius() throws IncompatibleDimensionsException {
        new Circle(-1);
    }

    @Test
    public void defaultsToTheOrigin() throws IncompatibleDimensionsException {
        Assert.assertEquals(new TwoDimensionalVector(0, 0), new Circle(2).getCenter());
    }

    @Test
    public void computesDiameterCircumferenceAndArea() throws IncompatibleDimensionsException {
        Circle circle = new Circle(2);
        Assert.assertEquals(4d, circle.getDiameter(), EPSILON);
        Assert.assertEquals(4 * Math.PI, circle.getCircumference(), EPSILON);
        Assert.assertEquals(4 * Math.PI, circle.getArea(), EPSILON);
    }

    @Test
    public void detectsPointsOnTheCircumferenceWhenTheArithmeticIsExact() throws IncompatibleDimensionsException {
        Circle circle = new Circle(5);
        Assert.assertTrue(circle.isPointOnCircumference(new TwoDimensionalVector(3, 4)));
        Assert.assertTrue(circle.isPointOnCircumference(new TwoDimensionalVector(5, 0)));
        Assert.assertFalse(circle.isPointOnCircumference(new TwoDimensionalVector(1, 1)));
    }

    @Test
    public void detectsPointsInsideTheCircle() throws IncompatibleDimensionsException {
        Circle circle = new Circle(new TwoDimensionalVector(10, 10), 5);
        Assert.assertTrue(circle.isPointInCircle(new TwoDimensionalVector(10, 10)));
        Assert.assertTrue(circle.isPointInCircle(new TwoDimensionalVector(12, 10)));
        Assert.assertFalse(circle.isPointInCircle(new TwoDimensionalVector(20, 10)));
    }

    @Test
    public void setRadiusAndSetCenterReturnNewCircles() throws IncompatibleDimensionsException {
        Circle circle = new Circle(1);
        Assert.assertEquals(
            3d,
            circle.setRadius(3)
                .getRadius(),
            0d);
        Assert.assertEquals(
            new TwoDimensionalVector(4, 5),
            circle.setCenter(new TwoDimensionalVector(4, 5))
                .getCenter());
        // The receiver is left untouched (the setters are misnamed; they are copy-with).
        Assert.assertEquals(1d, circle.getRadius(), 0d);
        Assert.assertEquals(new TwoDimensionalVector(0, 0), circle.getCenter());
    }

    /**
     * CODE_REVIEW §2.13 - {@code isPointOnCircumference} compares doubles with {@code ==}, so a point that
     * is mathematically on the circumference but not exactly representable is reported as outside.
     */
    @Test
    public void isPointOnCircumferenceShouldTolerateFloatingPointError() throws IncompatibleDimensionsException {
        Circle circle = new Circle(2);
        // sqrt(2^2) computed as sqrt(1.4142135623730951^2 * 2) == 2.0000000000000004
        double component = Math.sqrt(2);
        Assert.assertTrue(circle.isPointOnCircumference(new TwoDimensionalVector(component, component)));
    }
}
