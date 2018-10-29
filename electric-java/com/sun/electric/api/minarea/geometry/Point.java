/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Point.java
 *
 * Copyright (c) 2011, Static Free Software. All rights reserved.
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
package com.sun.electric.api.minarea.geometry;

import com.sun.electric.api.minarea.LayoutCell;
import com.sun.electric.api.minarea.ManhattanOrientation;
import java.io.Serializable;

/**
 * Immutable point implementation
 * 
 * @author Felix Schmidt
 * 
 */
@SuppressWarnings("serial")
public class Point implements Serializable {

    protected final int xCoord;
    protected final int yCoord;

    /**
     * Constructor to build a Point from two values.
     * @param x [-LayoutCell.MAX_COORD, LayoutCell.MAX_COORD]
     * @param y [-LayoutCell.MAX_COORD, LayoutCell.MAX_COORD]
     */
    public Point(int x, int y) {
        if (x < -LayoutCell.MAX_COORD || x > LayoutCell.MAX_COORD) {
            throw new IllegalArgumentException(
                    "x has to be in range [-LayoutCell.MAX_COORD, LayoutCell.MAX_COORD]");
        }

        if (y < -LayoutCell.MAX_COORD || y > LayoutCell.MAX_COORD) {
            throw new IllegalArgumentException(
                    "y has to be in range [-LayoutCell.MAX_COORD, LayoutCell.MAX_COORD]");
        }

        this.xCoord = x;
        this.yCoord = y;
    }

    /**
     * Method to return the X coordinate of this Point.
     * @return the xCoord
     */
    public int getX() {
        return xCoord;
    }

    /**
     * Method to build a new Point with a different X coordinate.
     * @param xCoord the new X coordinate.
     * @return a new Point with a different X coordinate.
     */
    public Point withX(int xCoord) {
        return new Point(xCoord, yCoord);
    }

    /**
     * Method to return the Y coordinate of this Point.
     * @return the yCoord
     */
    public int getY() {
        return yCoord;
    }

    /**
     * Method to build a new Point with a different Y coordinate.
     * @param yCoord the new Y coordinate.
     * @return a new Point with a different Y coordinate.
     */
    public Point withY(int yCoord) {
        return new Point(xCoord, yCoord);
    }

    public int[] toArray() {
        return new int[]{xCoord, yCoord};
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();

        builder.append("Point = (");
        builder.append(xCoord);
        builder.append(", ");
        builder.append(yCoord);
        builder.append(")");

        return builder.toString();
    }

    // ********************* Some helper functions **********************

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + xCoord;
        result = prime * result + yCoord;
        return result;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        Point other = (Point) obj;
        if (xCoord != other.xCoord) {
            return false;
        }
        if (yCoord != other.yCoord) {
            return false;
        }
        return true;
    }

    /**
     * Method to add another Point to this.
     * @param other the other Point to add.
     * @return this Point, with another added to it.
     */
    public Point add(Point other) {
        return new Point(this.xCoord + other.xCoord, this.yCoord + other.yCoord);
    }

    /**
     * Method to scale this Point by a given factor.
     * @param scaleFactor the factor to apply to X and Y.
     * @return this Point, scale3d appropriately.
     */
    public Point scale(int scaleFactor) {
        return scale(scaleFactor, scaleFactor);
    }

    /**
     * Method to scale this Point by X and Y factors.
     * @param scaleFactorX the X factor for scaling.
     * @param scaleFactorY the Y factor for scaling.
     * @return this Point, scaled appropriately.
     */
    public Point scale(int scaleFactorX, int scaleFactorY) {
        return new Point(this.xCoord * scaleFactorX, this.yCoord * scaleFactorY);
    }

    public Point mirror() {
        return new Point(this.yCoord, this.xCoord);
    }

    /**
     * Method to transform this by a Manhattan orientation.
     * @param orientation the Manhattan orientation to transform by.
     * @return the result of this, transformed as requested.
     */
    public Point transform(ManhattanOrientation orientation) {
        switch (orientation) {
            case R0:
                return this;
            case R90:
                return new Point(-getY(), getX());
            case R180:
                return new Point(-getX(), -getY());
            case R270:
                return new Point(getY(), -getX());
            case MY:
                return new Point(-getX(), getY());
            case MYR90:
                return new Point(-getY(), -getX());
            case MX:
                return new Point(getX(), -getY());
            case MXR90:
                return new Point(getY(), getX());
            default:
                throw new AssertionError();
        }
    }

    // ********************* Some helper classes **********************
    /**
     * Use objects of type NullPoint as an equivalent to null
     */
    public static final class NullPoint extends Point {

        /**
         * Constructor to return a NullPoint.
         */
        public NullPoint() {
            super(0, 0);
        }
    }

    public static final class Vector extends Point {

        /**
         * Constructor to build a Vector from coordinates.
         * @param x the X coordinate.
         * @param y the Y coordinate.
         */
        public Vector(int x, int y) {
            super(x, y);
            // TODO Auto-generated constructor stub
        }

        public Vector(Point head, Point tail) {
            super(head.xCoord - tail.xCoord, head.yCoord - tail.yCoord);
        }

        /**
         * Method to return the determinant of this and another Vector.
         * @param other the other Vector
         * @return the determinant.
         */
        public long determinant(Vector other) {
            return (long) this.xCoord * (long) other.yCoord - (long) this.yCoord * (long) other.xCoord;
        }
    }
}
