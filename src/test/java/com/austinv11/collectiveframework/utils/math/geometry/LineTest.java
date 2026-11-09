package com.austinv11.collectiveframework.utils.math.geometry;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import com.austinv11.collectiveframework.utils.math.ThreeDimensionalVector;
import com.austinv11.collectiveframework.utils.math.TwoDimensionalVector;

public class LineTest {

    private static final double EPSILON = 1.0E-9;

    private static Line diagonal() {
        return new Line(new TwoDimensionalVector(0, 0), new TwoDimensionalVector(2, 2));
    }

    @Test
    public void exposesStartAndEndCoordinates() {
        Line line = diagonal();
        Assert.assertEquals(0d, line.getStartX(), 0d);
        Assert.assertEquals(0d, line.getStartY(), 0d);
        Assert.assertEquals(0d, line.getStartZ(), 0d);
        Assert.assertEquals(2d, line.getEndX(), 0d);
        Assert.assertEquals(2d, line.getEndY(), 0d);
        Assert.assertEquals(0d, line.getEndZ(), 0d);
    }

    @Test
    public void exposesTwoAndThreeDimensionalPoints() {
        Line line = diagonal();
        Assert.assertEquals(new TwoDimensionalVector(0, 0), line.get2DStart());
        Assert.assertEquals(new TwoDimensionalVector(2, 2), line.get2DEnd());
        Assert.assertEquals(new ThreeDimensionalVector(0, 0, 0), line.get3DStart());
        Assert.assertEquals(new ThreeDimensionalVector(2, 2, 0), line.get3DEnd());
    }

    @Test
    public void computesSlope() {
        Assert.assertEquals(1d, diagonal().get2DSlope(), EPSILON);
        Assert.assertEquals(
            0d,
            new Line(new TwoDimensionalVector(0, 3), new TwoDimensionalVector(4, 3)).get2DSlope(),
            EPSILON);
    }

    @Test
    public void verticalLinesHaveUndefinedSlope() {
        Assert.assertTrue(
            Double.isNaN(new Line(new TwoDimensionalVector(0, 0), new TwoDimensionalVector(0, 5)).get2DSlope()));
    }

    @Test
    public void find2DPointFollowsTheLineEquation() {
        Assert.assertEquals(new TwoDimensionalVector(1, 1), diagonal().find2DPoint(1));
        Assert.assertEquals(
            new TwoDimensionalVector(2, 3),
            new Line(new TwoDimensionalVector(0, 3), new TwoDimensionalVector(4, 3)).find2DPoint(2));
    }

    @Test
    public void find2DPointOnAVerticalLineReturnsTheLowestY() {
        Line line = new Line(new TwoDimensionalVector(2, 0), new TwoDimensionalVector(2, 5));
        Assert.assertEquals(new TwoDimensionalVector(2, 0), line.find2DPoint(2));
    }

    @Test
    public void find3DPointKeepsZAtZeroForPlanarLines() {
        ThreeDimensionalVector point = diagonal().find3DPoint(1);
        Assert.assertEquals(1d, point.getX(), EPSILON);
        Assert.assertEquals(1d, point.getY(), EPSILON);
        Assert.assertEquals(0d, point.getZ(), EPSILON);
    }

    @Test
    public void isPointValidAcceptsPointsOnTheSegment() {
        Assert.assertTrue(diagonal().isPointValid(new TwoDimensionalVector(0, 0)));
        Assert.assertTrue(diagonal().isPointValid(new TwoDimensionalVector(1, 1)));
        Assert.assertTrue(diagonal().isPointValid(new TwoDimensionalVector(2, 2)));
    }

    @Test
    public void isPointValidRejectsPointsOffTheSegment() {
        Assert.assertFalse(diagonal().isPointValid(new TwoDimensionalVector(3, 3)));
        Assert.assertFalse(diagonal().isPointValid(new TwoDimensionalVector(1, 2)));
    }

    @Test
    public void getLengthUsesEuclideanDistance() {
        Assert.assertEquals(Math.sqrt(8), diagonal().getLength(), EPSILON);
        Assert.assertEquals(
            5d,
            new Line(new TwoDimensionalVector(0, 0), new TwoDimensionalVector(3, 4)).getLength(),
            EPSILON);
    }

    @Test
    public void setStartReturnsACopyWithANewStart() {
        Line updated = diagonal().setStart(new TwoDimensionalVector(-1, -1));
        Assert.assertEquals(-1d, updated.getStartX(), 0d);
        Assert.assertEquals(2d, updated.getEndX(), 0d);
        // The receiver is untouched.
        Assert.assertEquals(0d, diagonal().getStartX(), 0d);
    }

    @Test
    public void rotationPreservesLength() {
        Line rotated = diagonal().rotate2D(90);
        Assert.assertEquals(diagonal().getLength(), rotated.getLength(), EPSILON);
    }

    @Test
    public void equalsComparesBothEndpoints() {
        Assert.assertEquals(diagonal(), diagonal());
        Assert.assertNotEquals(diagonal(), new Line(new TwoDimensionalVector(0, 0), new TwoDimensionalVector(3, 3)));
    }

    /**
     * CODE_REVIEW §2.10 - {@code setEnd(ThreeDimensionalVector)} builds {@code new Line(coord, end)}, which
     * replaces the *start* instead of the end.
     */
    @Test
    public void setEndShouldReplaceTheEndPoint() {
        Line updated = diagonal().setEnd(new TwoDimensionalVector(4, 4));
        Assert.assertEquals(0d, updated.getStartX(), 0d);
        Assert.assertEquals(4d, updated.getEndX(), 0d);
    }

    /**
     * CODE_REVIEW §2.13 / Line: {@code getAll2DPoints} increments x before adding, so it emits one point
     * past the end of the segment.
     */
    @Test
    public void getAll2DPointsShouldStopAtTheEnd() {
        List<TwoDimensionalVector> points = diagonal().getAll2DPoints();
        Assert.assertEquals(3, points.size());
        Assert.assertEquals(new TwoDimensionalVector(2, 2), points.get(points.size() - 1));
    }

    /**
     * CODE_REVIEW §3.15 - {@code equals} without {@code hashCode}.
     */
    @Test
    public void equalLinesShouldHaveEqualHashCodes() {
        Assert.assertEquals(diagonal().hashCode(), diagonal().hashCode());
    }
}
