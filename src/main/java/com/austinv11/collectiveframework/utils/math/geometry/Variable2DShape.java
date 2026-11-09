package com.austinv11.collectiveframework.utils.math.geometry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.austinv11.collectiveframework.utils.ArrayUtils;
import com.austinv11.collectiveframework.utils.math.MathUtils;
import com.austinv11.collectiveframework.utils.math.TwoDimensionalVector;

/**
 * Class which represents a 2D shape with an indefinite amount of sides (until instantiation)
 */
public class Variable2DShape {

    /** Tolerance for area and perimeter comparisons. */
    public static final double EPSILON = 1.0E-6D;

    private Line[] sides;

    /**
     * Constructor for a 2D shape
     *
     * @param vectors The vectors to form the shape with
     * @throws IncompatibleDimensionsException
     */
    public Variable2DShape(TwoDimensionalVector... vectors) throws IncompatibleDimensionsException {
        if (vectors.length < 3) throw new IncompatibleDimensionsException("Not enough vectors!");
        // Sort a copy: this used to sort the caller's array in place (CODE_REVIEW §2.12).
        TwoDimensionalVector[] sorted = vectors.clone();
        Arrays.sort(sorted);
        Line[] lines = new Line[sorted.length];
        for (int i = 0; i < sorted.length; i++)
            lines[i] = new Line(sorted[i], ArrayUtils.wrappedRetrieve(sorted, i + 1));
        sides = lines;
        init();
    }

    /**
     * Constructor for a 2D shape
     *
     * @param lines The lines to form the shape with
     * @throws IncompatibleDimensionsException
     */
    public Variable2DShape(Line... lines) throws IncompatibleDimensionsException {
        if (lines.length < 3) throw new IncompatibleDimensionsException("Not enough sides!");
        sides = lines;
        init();
    }

    private void init() throws IncompatibleDimensionsException { // This ensures that a proper polygon was actually
                                                                 // formed
        int numOfSides = getNumberOfSides();
        TwoDimensionalVector[] nonRepeatingVectors = getVertices();
        if (!(nonRepeatingVectors.length == numOfSides))
            throw new IncompatibleDimensionsException("The dimensions do not connect to form a polygon!");
    }

    /**
     * Gets the number of sides for this shape
     *
     * @return The number of sides
     */
    public int getNumberOfSides() {
        return sides.length;
    }

    /**
     * Retrieves all points representing vertices on the shape
     *
     * @return The vertices
     */
    public TwoDimensionalVector[] getVertices() {
        List<TwoDimensionalVector> vectors = new ArrayList<TwoDimensionalVector>();
        for (Line line : sides) {
            vectors.add(line.get2DStart());
            vectors.add(line.get2DEnd());
        }
        TwoDimensionalVector[] vectorArray = vectors.toArray(new TwoDimensionalVector[vectors.size()]);
        return ArrayUtils.removeRepeats(vectorArray);
    }

    /**
     * Gets the perimeter of the shape
     *
     * @return The perimeter
     */
    public double getPerimeter() {
        double perimeter = 0;
        for (Line line : sides) perimeter += line.getLength();
        return perimeter;
    }

    /**
     * Calculates the area of the shape
     *
     * @return The area
     */
    public double getArea() {
        // The shoelace formula needs the vertices in polygon order. getVertices() returns them in side order,
        // which the sorting constructor has already destroyed for anything with more than three corners, so a
        // 4x4 square reported an area of 0 (CODE_REVIEW §2.12).
        TwoDimensionalVector[] vertices = getOrderedVertices();
        double area = 0;
        for (int i = 0; i < vertices.length; i++) {
            TwoDimensionalVector vertex1 = ArrayUtils.wrappedRetrieve(vertices, i);
            TwoDimensionalVector vertex2 = ArrayUtils.wrappedRetrieve(vertices, i + 1);
            area += (vertex1.getX() * vertex2.getY()) - (vertex1.getY() * vertex2.getX());
        }
        return Math.abs(area / 2);
    }

    /**
     * The shape's vertices ordered counter-clockwise around its centroid, which is the order the shoelace
     * formula and any other polygon traversal needs.
     *
     * @return The ordered vertices
     */
    public TwoDimensionalVector[] getOrderedVertices() {
        TwoDimensionalVector[] vertices = getVertices();
        if (vertices.length < 3) return vertices;
        final TwoDimensionalVector centre = centroidOf(vertices);
        List<TwoDimensionalVector> ordered = new ArrayList<TwoDimensionalVector>(Arrays.asList(vertices));
        java.util.Collections.sort(ordered, new java.util.Comparator<TwoDimensionalVector>() {

            @Override
            public int compare(TwoDimensionalVector a, TwoDimensionalVector b) {
                return Double.compare(
                    Math.atan2(a.getY() - centre.getY(), a.getX() - centre.getX()),
                    Math.atan2(b.getY() - centre.getY(), b.getX() - centre.getX()));
            }
        });
        return ordered.toArray(new TwoDimensionalVector[ordered.size()]);
    }

    private static TwoDimensionalVector centroidOf(TwoDimensionalVector[] vertices) {
        double x = 0;
        double y = 0;
        for (TwoDimensionalVector vertex : vertices) {
            x += vertex.getX();
            y += vertex.getY();
        }
        return new TwoDimensionalVector(x / vertices.length, y / vertices.length);
    }

    /**
     * Creates an array of sides from the shape where the side touches the given coord
     * This should only ever return 0-2 lines
     *
     * @param coord The coordinate point
     * @return The sides
     */
    public Line[] findSidesWithPoint(TwoDimensionalVector coord) {
        List<Line> lines = new ArrayList<Line>();
        for (Line line : sides) if (line.isPointValid(coord)) lines.add(line);
        return lines.toArray(new Line[lines.size()]);
    }

    /**
     * Finds the (internal) angle of the given vertex
     *
     * @param vertex The vertex
     * @return The angle
     * @throws IncompatibleDimensionsException
     */
    public float getAngleForVertex(TwoDimensionalVector vertex) throws IncompatibleDimensionsException {
        Line[] sides = findSidesWithPoint(vertex);
        if (sides.length != 2) throw new IncompatibleDimensionsException(vertex.toString() + " is not a valid vertex!");
        // Computed from the two edge vectors. The slope-based version used Math.atan, which has no quadrant, so
        // an obtuse interior angle came back acute; a vertical edge was faked with Double.MAX_VALUE, which made
        // the denominator infinite and the result 0 (CODE_REVIEW §2.12).
        TwoDimensionalVector first = otherEnd(sides[0], vertex).subtract(vertex);
        TwoDimensionalVector second = otherEnd(sides[1], vertex).subtract(vertex);
        double denominator = first.length() * second.length();
        if (denominator == 0) throw new IncompatibleDimensionsException(vertex + " has a zero-length side");
        double cosine = first.dotProduct(second) / denominator;
        return (float) Math.acos(Math.max(-1.0D, Math.min(1.0D, cosine)));
    }

    private static TwoDimensionalVector otherEnd(Line line, TwoDimensionalVector vertex) {
        return line.get2DStart()
            .equals(vertex) ? line.get2DEnd() : line.get2DStart();
    }

    /**
     * Gets the lines representing the sides of the shape
     *
     * @return The sides
     */
    public Line[] getSides() {
        return sides;
    }

    /**
     * Calculates the approximate center of the polygon
     *
     * @return The center coordinates
     */
    public TwoDimensionalVector getCentroid() {
        // Divided by the number of *sides* rather than the number of vertices, which differ for a degenerate
        // shape (CODE_REVIEW §2.12).
        return centroidOf(getVertices());
    }

    /**
     * Translates the whole shape to make a new shape with a matching centroid
     *
     * @param centroid New centroid
     * @return Modified shape
     */
    public Variable2DShape setCentroid(TwoDimensionalVector centroid) {
        TwoDimensionalVector oldCentroid = getCentroid();
        double xDiff = centroid.getX() - oldCentroid.getX();
        double yDiff = centroid.getY() - oldCentroid.getY();
        TwoDimensionalVector toAdd = new TwoDimensionalVector(xDiff, yDiff);
        TwoDimensionalVector[] vertices = getVertices();
        List<TwoDimensionalVector> newVertices = new ArrayList<TwoDimensionalVector>();
        for (TwoDimensionalVector vertex : vertices) newVertices.add(vertex.add(toAdd));
        try {
            return new Variable2DShape(newVertices.toArray(new TwoDimensionalVector[vertices.length]));
        } catch (IncompatibleDimensionsException e) {
            e.printStackTrace();
        }
        return null; // This should never be reached
    }

    /**
     * Rotates the shape around a point
     *
     * @param point The point to rotate around
     * @param angle The angle to rotate by
     * @return The new shape
     */
    public Variable2DShape rotate(TwoDimensionalVector point, double angle) {
        TwoDimensionalVector[] vertices = getVertices();
        List<TwoDimensionalVector> newVertices = new ArrayList<TwoDimensionalVector>();
        for (TwoDimensionalVector vertex : vertices) newVertices.add(vertex.rotate(point, angle));
        try {
            return new Variable2DShape(newVertices.toArray(new TwoDimensionalVector[vertices.length]));
        } catch (IncompatibleDimensionsException e) {
            e.printStackTrace();
        }
        return null; // This should never be reached
    }

    /**
     * Rotates the shape about its centroid
     *
     * @param angle The angle to rotate by
     * @return The new shape
     */
    public Variable2DShape rotate(double angle) {
        return rotate(getCentroid(), angle);
    }

    /**
     * Checks if the provided point is on the shape's perimeter
     *
     * @param coord The point
     * @return If the point is on the perimeter
     */
    public boolean isPointOnPerimeter(TwoDimensionalVector coord) {
        for (Line l : sides) if (l.isPointValid(coord)) return true;
        return false;
    }

    /**
     * Checks if a point is inside the shape.
     * DO NOT TRUST THIS- it is not always accurate, it is only 100% accurate with rectangles
     *
     * @param coord The point
     * @return If the point is in the shape
     */
    @Deprecated
    public boolean isPointValid(TwoDimensionalVector coord) { // FIXME
        if (isPointOnPerimeter(coord)) return true;
        TwoDimensionalVector[] vertices = getVertices();
        double[] xVals = new double[vertices.length];
        double[] yVals = new double[vertices.length];
        for (int i = 0; i < vertices.length; i++) {
            xVals[i] = vertices[i].getX();
            yVals[i] = vertices[i].getY();
        }
        double minX, maxX, minY, maxY;
        minX = MathUtils.getMin(xVals);
        maxX = MathUtils.getMax(xVals);
        minY = MathUtils.getMin(yVals);
        maxY = MathUtils.getMax(yVals);
        return MathUtils.isBetween(minX, maxX, coord.getX(), true)
            && MathUtils.isBetween(minY, maxY, coord.getY(), true);
    }

    /**
     * Gets all the points in the shape (with whole number coords)
     * WARNING- Deprecated because it uses isPointValid
     *
     * @return The points
     */
    @Deprecated
    public TwoDimensionalVector[] getAllPoints() {
        List<TwoDimensionalVector> points = new ArrayList<TwoDimensionalVector>();
        TwoDimensionalVector[] vertices = getVertices();
        double[] xVals = new double[vertices.length];
        double[] yVals = new double[vertices.length];
        for (int i = 0; i < vertices.length; i++) {
            xVals[i] = vertices[i].getX();
            yVals[i] = vertices[i].getY();
        }
        double minX, maxX, minY, maxY;
        minX = MathUtils.getMin(xVals);
        maxX = MathUtils.getMax(xVals);
        minY = MathUtils.getMin(yVals);
        maxY = MathUtils.getMax(yVals);
        for (int x = (int) minX; x <= maxX; x++) for (int y = (int) minY; y <= maxY; y++)
            if (isPointValid(new TwoDimensionalVector(x, y))) points.add(new TwoDimensionalVector(x, y));
        return points.toArray(new TwoDimensionalVector[points.size()]);
    }

    /**
     * Two shapes are equal when they have the same number of sides and the same area and perimeter, compared
     * with a tolerance.
     * <p>
     * This used exact double equality on computed values, which is essentially never true, and there was no
     * {@code hashCode} (CODE_REVIEW §3.15).
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Variable2DShape)) return false;
        Variable2DShape shape = (Variable2DShape) other;
        return shape.getNumberOfSides() == getNumberOfSides() && Math.abs(shape.getArea() - getArea()) <= EPSILON
            && Math.abs(shape.getPerimeter() - getPerimeter()) <= EPSILON;
    }

    @Override
    public int hashCode() {
        // Consistent with equals: equal shapes necessarily have the same number of sides.
        return getNumberOfSides();
    }

    @Override
    public String toString() {
        return "Variable2DShape(Sides:" + getNumberOfSides() + ")";
    }
}
