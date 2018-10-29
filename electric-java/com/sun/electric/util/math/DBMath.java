/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: DBMath.java
 *
 * Copyright (c) 2003, Static Free Software. All rights reserved.
 *
 * Electric(tm) is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * Electric(tm) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sun.electric.util.math;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

/**
 * This class is a collection of math utilities used for Database Units. It
 * overrides several important methods from GenMath used when comparing doubles.
 */
public class DBMath extends GenMath {

    /**
     * Number of grid points per unit
     */
    public static final double GRID = 400;
    /**
     * epsilon is the largest amount of absolute difference between two numbers
     * in the database for which those numbers will still be regarded as
     * "equal".
     */
    private static final double EPSILON = 1 / GRID;
    private static final double HALF_EPSILON = 0.5 / GRID;
    private static final double TINYDELTA = EPSILON * 1.01;

    /**
     * To return private epsilon used for calculation. This might problably be
     * removed
     *
     * @return epsilon
     */
    public static double getEpsilon() {
        return EPSILON;
    }

    /**
     * Method to tell whether a point is inside of a bounds, compensating for
     * possible database precision errors. The reason that this is necessary is
     * that Rectangle2D.contains requires that the point be INSIDE of the
     * bounds, whereas this method accepts a point that is ON the bounds.
     *
     * @param pt the point in question
     * @param bounds the bounds being tested
     * @return true if the point is basically within the bounds, within some
     * epsilon.
     */
    public static boolean pointInRect(Point2D pt, Rectangle2D bounds) {
        if (pt instanceof AbstractFixpPoint && bounds instanceof AbstractFixpRectangle) {
            return pointInRect((AbstractFixpPoint) pt, (AbstractFixpRectangle) bounds);
        }
        if (pt.getX() < (bounds.getMinX() - TINYDELTA)) {
            return false;
        }
        if (pt.getX() > (bounds.getMaxX() + TINYDELTA)) {
            return false;
        }
        if (pt.getY() < (bounds.getMinY() - TINYDELTA)) {
            return false;
        }
        if (pt.getY() > (bounds.getMaxY() + TINYDELTA)) {
            return false;
        }
        return true;
    }

    public static boolean pointInRect(AbstractFixpPoint pt, AbstractFixpRectangle bounds) {
        long fixpX = pt.getFixpX();
        long fixpY = pt.getFixpY();
        return bounds.getFixpMinX() <= fixpX && fixpX <= bounds.getFixpMaxX()
                && bounds.getFixpMinY() <= fixpY && fixpY <= bounds.getFixpMaxY();
    }

    /**
     * Method to determine if point is completely inside a bound and not along
     * its boundary.
     *
     * @param pt the Point in question.
     * @param bounds the bounds to test.
     * @return true if the point is inside the bounds.
     */
    public static boolean pointInsideRect(Point2D pt, Rectangle2D bounds) {
        double ptX = pt.getX();
        double ptY = pt.getY();
        boolean newV = (isGreaterThan(ptX, bounds.getMinX())
                && isGreaterThan(bounds.getMaxX(), ptX)
                && isGreaterThan(ptY, bounds.getMinY())
                && isGreaterThan(bounds.getMaxY(), ptY));
        return newV;
    }

    /**
     * Method to compare two double-precision database values.
     *
     * @param a the first number.
     * @param b the second number.
     * @return true if the numbers are approximately equal (to a few decimal
     * places).
     */
    public static boolean areEquals(double a, double b) {
        if (Math.abs(a - b) < TINYDELTA) {
            return true;
        }
        return false;
    }

    /**
     * Method to determine if a value is between two given values including the
     * boundary.
     *
     * @param x the value to test.
     * @param a one end of the boundary.
     * @param b the other end of the boundary.
     * @return true if the value is inside of the boundary.
     */
    public static boolean isInBetween(double x, double a, double b) {
        if (isGreaterThan(a, b)) {
            double c = a;
            a = b;
            b = c;
        }
        boolean tooSmall = isGreaterThan(a, x);
        boolean tooBig = isGreaterThan(x, b);
        return !tooSmall && !tooBig;
    }

    /**
     * Method to determine if a value is between two given values without the
     * boundary.
     *
     * @param x the value to test.
     * @param a one end of the boundary.
     * @param b the other end of the boundary.
     * @return true if the value is inside of the boundary.
     */
    public static boolean isInBetweenExclusive(double x, double a, double b) {
        if (isGreaterThan(a, b)) {
            double c = a;
            a = b;
            b = c;
        }
        boolean tooSmall = isGreaterThanOrEqualTo(a, x); // it must exclude the boundaries
        boolean tooBig = isGreaterThanOrEqualTo(x, b);
        return !tooSmall && !tooBig;
    }

    /**
     * Method to determine if one value is greater than another, but counting
     * for rounding error
     *
     * @param a the first number.
     * @param b the second number.
     * @return true if first number is greater than the second number.
     */
    public static boolean isGreaterThan(double a, double b) {
        return a - b > HALF_EPSILON;
    }

    /**
     * Method to determine if one value is less than another, but counting for
     * rounding error
     *
     * @param a the first number.
     * @param b the second number.
     * @return true if first number is less than the second number.
     */
    public static boolean isLessThan(double a, double b) {
        return b - a > HALF_EPSILON;
    }

    /**
     * Method to determine if one value is greater than or equal to another, but
     * counting for rounding error
     *
     * @param a the first number.
     * @param b the second number.
     * @return true if first number is greater than or equal to the second
     * number.
     */
    public static boolean isGreaterThanOrEqualTo(double a, double b) {
        return a - b > -HALF_EPSILON;
    }

    /**
     * Method to determine if one value is less than or equal to another, but
     * counting for rounding error
     *
     * @param a the first number.
     * @param b the second number.
     * @return true if first number is less than or equal to the second number.
     */
    public static boolean isLessThanOrEqualTo(double a, double b) {
        return b - a > -HALF_EPSILON;
    }

    /**
     * Method to round a database value to database precision.
     *
     * @param lambdaValue the value to round in lambda units.
     * @return the return value in lambda units is an approximation of x rounded
     * to GRID.
     */
    public static double round(double lambdaValue) {
        double x = lambdaValue * GRID;
        long l = (long) (x >= 0 ? x + HALF : x - HALF);
        return l / GRID;
    }

    /**
     * Method to convert a database value from lambda units to grid units.
     *
     * @param lambdaValue the value to round in lambda unit.
     * @return the return value in grid units.
     */
    public static long lambdaToGrid(double lambdaValue) {
        double x = lambdaValue * GRID;
        return (long) (x >= 0 ? x + HALF : x - HALF);
    }

    /**
     * Method to convert a database size value from lambda units to grid units.
     * Result is always even number.
     *
     * @param lambdaValue the value to round in lambda unit.
     * @return the return value in grid units which is even number.
     */
    public static long lambdaToSizeGrid(double lambdaValue) {
        double x = lambdaValue * (GRID / 2);
        long l = (long) (x >= 0 ? x + HALF : x - HALF);
        return l << 1;
    }

    /**
     * Method to convert a database value from grid units to lambda units.
     *
     * @param gridValue the value in grid unit.
     * @return the return value in lambda units.
     */
    public static double gridToLambda(double gridValue) {
        return gridValue / GRID;
    }

    /**
     * Method to snap a point to the nearest database-space grid unit.
     *
     * @param pt the point to be snapped.
     * @param alignment the alignment values to use in X and Y.
     */
    public static void gridAlign(Point2D pt, EDimension alignment) {
        gridAlign(pt, alignment, -1);
    }

    /**
     * Method to snap the X, Y or both coordinates of a point to the nearest
     * database-space grid unit
     *
     * @param pt the point to be snapped.
     * @param alignment the alignment values to use in X and Y.
     * @param direction -1 if X and Y coordinates, 0 if only X and 1 if only Y
     */
    public static void gridAlign(Point2D pt, EDimension alignment, int direction) {
        switch (direction) {
            case -1:
                if (alignment.getWidth() <= 0 || alignment.getHeight() <= 0) {
                    return;
                }
                long x = Math.round(pt.getX() / alignment.getWidth());
                long y = Math.round(pt.getY() / alignment.getHeight());
                pt.setLocation(x * alignment.getWidth(), y * alignment.getHeight());
                break;
            case 0: // X only
                if (alignment.getWidth() <= 0) {
                    return;
                }
                x = Math.round(pt.getX() / alignment.getWidth());
                pt.setLocation(x * alignment.getWidth(), pt.getY());
                break;
            case 1: // y only
                if (alignment.getHeight() <= 0) {
                    return;
                }
                y = Math.round(pt.getY() / alignment.getHeight());
                pt.setLocation(pt.getX(), y * alignment.getHeight());
                break;
            default:
                assert (false); // it should not reach this point.
        }
    }

    /**
     * Method to compare two double-precision database coordinates within an
     * approximate epsilon.
     *
     * @param a the first point.
     * @param b the second point.
     * @return true if the points are approximately equal.
     */
    public static boolean areEquals(Point2D a, Point2D b) {
        if (areEquals(a.getX(), b.getX())
                && areEquals(a.getY(), b.getY())) {
            return true;
        }
        return false;
    }

    /**
     * Method to tell whether a point is on a given line segment.
     *
     * @param end1 the first end of the line segment.
     * @param end2 the second end of the line segment.
     * @param pt the point in question.
     * @return true if the point is on the line segment.
     */
    public static boolean isOnLine(Point2D end1, Point2D end2, Point2D pt) {
        Point2D closestPointOnSegment = closestPointToSegment(end1, end2, pt);
        return areEquals(closestPointOnSegment, pt);
    }

    /**
     * Method to calculate remainder for doubles and avoid rounding errors by
     * calculating the remainder for integers instead.
     *
     * @param a the numerator
     * @param divisor the denominator.
     * @return the remainder from the division.
     */
    public static boolean hasRemainder(double a, double divisor) {
        double val = round(a / divisor);
        return val % 1 != 0;
    }

    /**
     * Method to transform an AbstractFixpRectangle by a given transformation.
     *
     * @param bounds the AbstractFixpRectangle to transform. It is transformed
     * "in place" (its coordinates are overwritten).
     * @param xform the transformation matrix.
     */
    public static void transformRect(AbstractFixpRectangle bounds, FixpTransform xform) {
        if (!xform.isIdentity()) {
            xform.transform(bounds, bounds);
        }
    }

    /**
     * Method to transform a Rectangle2D by a given transformation.
     *
     * @param bounds the Rectangle to transform. It is transformed "in place"
     * (its coordinates are overwritten).
     * @param xform the transformation matrix.
     */
    public static void transformRect(Rectangle2D bounds, FixpTransform xform) {
        if (!xform.isIdentity()) {
            xform.transform(bounds, bounds);
        }
    }

    /**
     * Method to tell whether two Rectangle2D objects intersect. If one of the
     * rectangles has zero size, then standard "intersect()" fails.
     *
     * @param r1 the first rectangle.
     * @param r2 the second rectangle.
     * @return true if they overlap.
     */
    public static boolean rectsIntersect(Rectangle2D r1, Rectangle2D r2) {
        if (r2.getMaxX() < r1.getMinX()) {
            return false;
        }
        if (r2.getMinX() > r1.getMaxX()) {
            return false;
        }
        if (r2.getMaxY() < r1.getMinY()) {
            return false;
        }
        if (r2.getMinY() > r1.getMaxY()) {
            return false;
        }
        return true;
    }
}
