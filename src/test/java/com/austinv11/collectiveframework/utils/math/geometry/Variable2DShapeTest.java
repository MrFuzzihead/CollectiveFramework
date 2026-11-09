package com.austinv11.collectiveframework.utils.math.geometry;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import com.austinv11.collectiveframework.utils.math.TwoDimensionalVector;

public class Variable2DShapeTest {

    private static final double EPSILON = 1.0E-6;

    /**
     * A 3-4-5 right triangle with the right angle at (4,0). The constructor sorts its vertices, so the
     * resulting side order is (0,0)-(4,0), (4,0)-(4,3), (4,3)-(0,0).
     */
    private static Variable2DShape rightTriangle() throws IncompatibleDimensionsException {
        return new Variable2DShape(
            new TwoDimensionalVector(0, 0),
            new TwoDimensionalVector(4, 0),
            new TwoDimensionalVector(4, 3));
    }

    /**
     * A scalene triangle with no vertical edge, so {@link Line#isPointValid} cannot cheat by ignoring the x
     * coordinate.
     */
    private static Variable2DShape scaleneTriangle() throws IncompatibleDimensionsException {
        return new Variable2DShape(
            new TwoDimensionalVector(0, 0),
            new TwoDimensionalVector(5, 0),
            new TwoDimensionalVector(2, 4));
    }

    @Test(expected = IncompatibleDimensionsException.class)
    public void rejectsFewerThanThreeVectors() throws IncompatibleDimensionsException {
        new Variable2DShape(new TwoDimensionalVector(0, 0), new TwoDimensionalVector(1, 1));
    }

    @Test(expected = IncompatibleDimensionsException.class)
    public void rejectsFewerThanThreeLines() throws IncompatibleDimensionsException {
        new Variable2DShape(
            new Line(new TwoDimensionalVector(0, 0), new TwoDimensionalVector(1, 0)),
            new Line(new TwoDimensionalVector(1, 0), new TwoDimensionalVector(0, 0)));
    }

    @Test
    public void buildsOneSidePerVertex() throws IncompatibleDimensionsException {
        Assert.assertEquals(3, rightTriangle().getNumberOfSides());
        Assert.assertEquals(
            4,
            new Variable2DShape(
                new TwoDimensionalVector(0, 0),
                new TwoDimensionalVector(0, 4),
                new TwoDimensionalVector(4, 4),
                new TwoDimensionalVector(4, 0)).getNumberOfSides());
    }

    @Test
    public void computesPerimeter() throws IncompatibleDimensionsException {
        Assert.assertEquals(12d, rightTriangle().getPerimeter(), EPSILON);
        Assert.assertEquals(5 + 5 + Math.sqrt(20), scaleneTriangle().getPerimeter(), EPSILON);
    }

    @Test
    public void computesTriangleArea() throws IncompatibleDimensionsException {
        Assert.assertEquals(6d, rightTriangle().getArea(), EPSILON);
        Assert.assertEquals(10d, scaleneTriangle().getArea(), EPSILON);
    }

    @Test
    public void computesCentroid() throws IncompatibleDimensionsException {
        TwoDimensionalVector centroid = rightTriangle().getCentroid();
        Assert.assertEquals(8d / 3d, centroid.getX(), EPSILON);
        Assert.assertEquals(1d, centroid.getY(), EPSILON);
    }

    @Test
    public void findsTheTwoSidesMeetingAtAVertex() throws IncompatibleDimensionsException {
        Assert.assertEquals(2, rightTriangle().findSidesWithPoint(new TwoDimensionalVector(4, 0)).length);
    }

    @Test
    public void findsNoSidesForAnInteriorPoint() throws IncompatibleDimensionsException {
        Assert.assertEquals(0, scaleneTriangle().findSidesWithPoint(new TwoDimensionalVector(2, 1)).length);
    }

    @Test
    public void detectsPointsOnThePerimeter() throws IncompatibleDimensionsException {
        Assert.assertTrue(scaleneTriangle().isPointOnPerimeter(new TwoDimensionalVector(2, 0)));
        Assert.assertFalse(scaleneTriangle().isPointOnPerimeter(new TwoDimensionalVector(2, 1)));
    }

    @Test
    public void rotationPreservesAreaAndPerimeter() throws IncompatibleDimensionsException {
        Variable2DShape rotated = rightTriangle().rotate(90);
        Assert.assertEquals(6d, rotated.getArea(), EPSILON);
        Assert.assertEquals(12d, rotated.getPerimeter(), EPSILON);
    }

    @Test
    public void setCentroidMovesTheShape() throws IncompatibleDimensionsException {
        Variable2DShape moved = rightTriangle().setCentroid(new TwoDimensionalVector(0, 0));
        Assert.assertEquals(
            0d,
            moved.getCentroid()
                .getX(),
            EPSILON);
        Assert.assertEquals(
            0d,
            moved.getCentroid()
                .getY(),
            EPSILON);
        Assert.assertEquals(6d, moved.getArea(), EPSILON);
    }

    /**
     * CODE_REVIEW §2.12 - the varargs constructor calls {@code Arrays.sort(vectors)}, which sorts the
     * caller's array in place.
     */
    @Ignore("CODE_REVIEW §2.12: the varargs constructor mutates the caller's array")
    @Test
    public void constructorShouldNotMutateTheCallersArray() throws IncompatibleDimensionsException {
        TwoDimensionalVector[] vertices = { new TwoDimensionalVector(4, 3), new TwoDimensionalVector(0, 0),
            new TwoDimensionalVector(4, 0) };
        new Variable2DShape(vertices);
        Assert.assertEquals(new TwoDimensionalVector(4, 3), vertices[0]);
    }

    /**
     * CODE_REVIEW §2.12 - {@code getArea} runs the shoelace formula over {@code getVertices()}, which is
     * ordered by x (the constructor sorts, and {@code getVertices} preserves side order). For any polygon
     * with more than three vertices that is not convex-and-monotonic in x, the resulting traversal is
     * self-intersecting and the area is wrong. A 4x4 square reports 0.
     */
    @Ignore("CODE_REVIEW §2.12: getArea depends on vertex order, which the constructor destroys")
    @Test
    public void areaOfASquareShouldBeSixteen() throws IncompatibleDimensionsException {
        Variable2DShape square = new Variable2DShape(
            new TwoDimensionalVector(0, 0),
            new TwoDimensionalVector(0, 4),
            new TwoDimensionalVector(4, 4),
            new TwoDimensionalVector(4, 0));
        Assert.assertEquals(16d, square.getArea(), EPSILON);
    }

    /**
     * CODE_REVIEW §3.15 - {@code equals} compares floating-point area and perimeter with {@code ==} and has
     * no {@code hashCode}.
     */
    @Ignore("CODE_REVIEW §3.15: equals compares doubles with == and has no hashCode")
    @Test
    public void equalShapesShouldBeEqual() throws IncompatibleDimensionsException {
        Assert.assertEquals(rightTriangle(), rightTriangle());
    }
}
