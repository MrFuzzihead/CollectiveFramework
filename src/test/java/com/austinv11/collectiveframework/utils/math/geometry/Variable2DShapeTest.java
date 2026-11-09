package com.austinv11.collectiveframework.utils.math.geometry;

import org.junit.Assert;
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
    @Test
    public void equalShapesShouldBeEqual() throws IncompatibleDimensionsException {
        Assert.assertEquals(rightTriangle(), rightTriangle());
    }

    @Test
    public void equalShapesShareAHashCode() throws IncompatibleDimensionsException {
        Assert.assertEquals(rightTriangle().hashCode(), rightTriangle().hashCode());
    }

    @Test
    public void shapesWithADifferentAreaAreNotEqual() throws IncompatibleDimensionsException {
        Assert.assertNotEquals(rightTriangle(), scaleneTriangle());
    }

    // ------------------------------------------------------------------------------ vertex angles

    @Test
    public void angleOfARightAngleVertexIsHalfPi() throws IncompatibleDimensionsException {
        // Edges from (4,0): (-4,0) and (0,3), which are perpendicular.
        Assert.assertEquals(
            (float) (Math.PI / 2),
            rightTriangle().getAngleForVertex(new TwoDimensionalVector(4, 0)),
            1.0E-5f);
    }

    /**
     * CODE_REVIEW §2.12 (fixed) - the slope-based version used {@code Math.atan}, which has no quadrant, so an
     * obtuse interior angle came back acute.
     */
    @Test
    public void angleOfAnObtuseVertexIsReflexFreeButObtuse() throws IncompatibleDimensionsException {
        // Vertex (2,1) of the triangle (0,0), (2,1), (4,0): edges (-2,-1) and (2,-1), cos = -3/5.
        Variable2DShape shape = new Variable2DShape(
            new TwoDimensionalVector(0, 0),
            new TwoDimensionalVector(4, 0),
            new TwoDimensionalVector(2, 1));
        float angle = shape.getAngleForVertex(new TwoDimensionalVector(2, 1));

        Assert.assertTrue("expected an obtuse angle, got " + angle, angle > Math.PI / 2);
        Assert.assertEquals((float) Math.acos(-0.6), angle, 1.0E-5f);
    }

    /**
     * CODE_REVIEW §2.12 (fixed) - a vertical edge was faked with {@code Double.MAX_VALUE}, which made the
     * denominator infinite and the angle 0.
     */
    @Test
    public void angleWithAVerticalEdgeIsWellDefined() throws IncompatibleDimensionsException {
        Variable2DShape shape = new Variable2DShape(
            new TwoDimensionalVector(0, 0),
            new TwoDimensionalVector(0, 4),
            new TwoDimensionalVector(4, 0));
        Assert.assertEquals((float) (Math.PI / 2), shape.getAngleForVertex(new TwoDimensionalVector(0, 0)), 1.0E-5f);
    }

    @Test(expected = IncompatibleDimensionsException.class)
    public void angleOfANonVertexIsRejected() throws IncompatibleDimensionsException {
        rightTriangle().getAngleForVertex(new TwoDimensionalVector(1, 1));
    }

    @Test
    public void orderedVerticesTraceThePolygon() throws IncompatibleDimensionsException {
        Variable2DShape square = new Variable2DShape(
            new TwoDimensionalVector(0, 0),
            new TwoDimensionalVector(0, 4),
            new TwoDimensionalVector(4, 4),
            new TwoDimensionalVector(4, 0));
        TwoDimensionalVector[] ordered = square.getOrderedVertices();

        Assert.assertEquals(4, ordered.length);
        // Consecutive vertices must be adjacent corners of the square, i.e. one coordinate apart.
        for (int i = 0; i < ordered.length; i++) {
            TwoDimensionalVector a = ordered[i];
            TwoDimensionalVector b = ordered[(i + 1) % ordered.length];
            double delta = Math.abs(a.getX() - b.getX()) + Math.abs(a.getY() - b.getY());
            Assert.assertEquals("vertices " + i + " and " + (i + 1) + " are not adjacent", 4d, delta, EPSILON);
        }
    }
}
