/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: EPoint.java
 * Written by: Dmitry Nadezhin.
 *
 * Copyright (c) 2005, Static Free Software. All rights reserved.
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
package com.sun.electric.database.geometry;

import com.sun.electric.util.math.*;
import java.awt.geom.Point2D;
import java.io.Serializable;

/**
 * The
 * <code>EPoint</code> immutable class defines a point representing a location
 * in (x,&nbsp;y) coordinate space. This class extends abstract class Point2D.
 * This class is used in Electric database. Coordiates are snapped to grid
 * according to
 * <code>DBMath.round</code> method.
 */
public abstract class EPoint extends AbstractFixpPoint implements Serializable {

    /**
     * EPoint with both zero coordinates.
     */
    public static final EPoint ORIGIN = new EPointInt(0, 0);

    @Override
    public void setFixpLocation(long fixpX, long fixpY) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected AbstractFixpPoint create(long fixpX, long fixpY) {
        return fromFixp(fixpX, fixpY);
    }

    /**
     * Returns
     * <code>EPoint</code> with specified grid coordinates.
     *
     * @param lambdaX the x-coordinate in lambda units.
     * @param lambdaY the y-coordinate in lambda units.
     * @return EPoint with specified grid coordinates.
     */
    public static EPoint fromLambda(double lambdaX, double lambdaY) {
        return lambdaX == 0 && lambdaY == 0 ? ORIGIN : fromGrid(DBMath.lambdaToGrid(lambdaX), DBMath.lambdaToGrid(lambdaY));
    }

    /**
     * Returns
     * <code>EPoint</code> with specified fixed-point coordinates.
     *
     * @param fixpX the x-coordinate in fixed-point units.
     * @param fixpY the y-coordinate in fixed-point units.
     * @return EPoint with specified grid coordinates.
     */
    public static EPoint fromFixp(long fixpX, long fixpY) {
        long gridX = GenMath.roundToMultiple(fixpX, 1L << FixpCoord.FRACTION_BITS) >> FixpCoord.FRACTION_BITS;
        long gridY = GenMath.roundToMultiple(fixpY, 1L << FixpCoord.FRACTION_BITS) >> FixpCoord.FRACTION_BITS;
        return fromGrid(gridX, gridY);
    }

    /**
     * Returns
     * <code>EPoint</code> with specified grid coordinates.
     *
     * @param gridX the x-coordinate in grid units.
     * @param gridY the y-coordinate in grid units.
     * @return EPoint with specified grid coordinates.
     */
    public static EPoint fromGrid(long gridX, long gridY) {
        int intX = (int) gridX;
        int intY = (int) gridY;
        return gridX == 0 && gridY == 0 ? ORIGIN : intX == gridX && intY == gridY ? new EPointInt(intX, intY) : new EPointLong(gridX, gridY);
    }

    /**
     * Returns
     * <code>EPoint</code> from specified
     * <code>Point2D</code> snapped to the grid.
     *
     * @param p specified Point2D
     * @return Snapped EPoint
     */
    public static EPoint snap(Point2D p) {
        return (p instanceof EPoint) ? (EPoint) p : fromLambda(p.getX(), p.getY());
    }

    /**
     * Returns the X coordinate of this
     * <code>EPoint</code> in lambda units in
     * <code>double</code> precision.
     *
     * @return the X coordinate of this
     * <code>EPoint</code>.
     */
    @Override
    public double getX() {
        return DBMath.gridToLambda(getGridX());
    }

    /**
     * Returns the Y coordinate of this
     * <code>EPoint</code> in lambda unuts in
     * <code>double</code> precision.
     *
     * @return the Y coordinate of this
     * <code>EPoint</code>.
     */
    @Override
    public double getY() {
        return DBMath.gridToLambda(getGridY());
    }

    /**
     * Returns the X coordinate of this
     * <code>EPoint</code> as ECoord object.
     *
     * @return the X coordinate of this
     * <code>EPoint</code>.
     */
    public ECoord getCoordX() {
        return ECoord.fromGrid(getGridX());
    }

    /**
     * Returns the Y coordinate of this
     * <code>EPoint</code> as ECoord object.
     *
     * @return the Y coordinate of this
     * <code>EPoint</code>.
     */
    public ECoord getCoordY() {
        return ECoord.fromGrid(getGridY());
    }

    /**
     * Returns the X coordinate of this
     * <code>EPoint</code> in lambda units in
     * <code>double</code> precision.
     *
     * @return the X coordinate of this
     * <code>EPoint</code>.
     */
    public double getLambdaX() {
        return getX();
    }

    /**
     * Returns the Y coordinate of this
     * <code>EPoint</code> in lambda units in
     * <code>double</code> precision.
     *
     * @return the Y coordinate of this
     * <code>EPoint</code>.
     */
    public double getLambdaY() {
        return getY();
    }

    /**
     * Returns the X coordinate of this
     * <code>EPoint</code> in fixed-point units in
     * <code>long</code> precision.
     *
     * @return the X coordinate of this
     * <code>EPoint</code>.
     */
    @Override
    public long getFixpX() {
        return ((long) getGridX()) << FixpCoord.FRACTION_BITS;
    }

    /**
     * Returns the Y coordinate of this
     * <code>EPoint</code> in fixed-point units in
     * <code>long</code> precision.
     *
     * @return the Y coordinate of this
     * <code>EPoint</code>.
     */
    @Override
    public long getFixpY() {
        return ((long) getGridY()) << FixpCoord.FRACTION_BITS;
    }

    /**
     * Returns the X coordinate of this
     * <code>EPoint</code> in grid units in
     * <code>long</code> precision.
     *
     * @return the X coordinate of this
     * <code>EPoint</code>.
     */
    public abstract long getGridX();

    /**
     * Returns the Y coordinate of this
     * <code>EPoint</code> in grid units in
     * <code>long</code> precision.
     *
     * @return the Y coordinate of this
     * <code>EPoint</code>.
     */
    public abstract long getGridY();

    /**
     * This method overrides
     * <code>Point2D.setLocation</code> method. It throws
     * UnsupportedOperationException.
     *
     * @param x the x-coordinate to which to set this
     * <code>EPoint</code>
     * @param y the y-coordinate to which to set this
     * <code>EPoint</code>
     * @throws UnsupportedOperationException
     */
    @Override
    public void setLocation(double x, double y) {
        throw new UnsupportedOperationException();
    }

    /**
     * Creates mutable
     * <code>Point2D.Double</code> from the
     * <code>EPoint</code> in lambda units.
     *
     * @return mutable Point2D in lambda units
     */
    public Point2D.Double lambdaMutable() {
        return new Point2D.Double(getLambdaX(), getLambdaY());
    }

    /**
     * Creates mutable
     * <code>Point2D.Double</code> from the
     * <code>EPoint</code> in grid units.
     *
     * @return mutable Point2D in grid units
     */
    public Point2D.Double gridMutable() {
        return new Point2D.Double(getGridX(), getGridY());
    }

    /**
     * Returns the distance from this
     * <code>EPoint</code> to a specified
     * <code>EPoint</code> in lambda units.
     *
     * @param pt the specified
     * <code>EPoint</code>
     * @return the distance between this
     * <code>EPoint</code> and the specified
     * <code>Point</code> in lambdaUnits.
     */
    public double lambdaDistance(EPoint pt) {
        return DBMath.gridToLambda(gridDistance(pt));
    }

    /**
     * Returns the distance from this
     * <code>EPoint</code> to a specified
     * <code>EPoint</code> in grid units.
     *
     * @param pt the specified
     * <code>EPoint</code>
     * @return the distance between this
     * <code>EPoint</code> and the specified
     * <code>Point</code> in gridUnits.
     */
    public double gridDistance(EPoint pt) {
        long PX = pt.getGridX() - this.getGridX();
        long PY = pt.getGridY() - this.getGridY();
        return PY == 0 ? Math.abs(PX) : PX == 0 ? Math.abs(PY) : Math.hypot(PX, PY);
    }

    /**
     * Returns the distance from this
     * <code>EPoint</code> to a specified
     * <code>EPoint</code> in fixed-point units.
     *
     * @param pt the specified
     * <code>EPoint</code>
     * @return the distance between this
     * <code>EPoint</code> and the specified
     * <code>Point</code> in gridUnits.
     */
    public double fixpDistance(EPoint pt) {
        return gridDistance(pt) * FixpCoord.FIXP_SCALE;
    }

    /**
     * Returns true if this EPoint is equal to the other EPoint. This method
     * returns the same result as general
     * <code>equals</code>, but it could be a little faster, because no virtual
     * method dispatching is required.
     *
     * @return true if this EPoint is equal to the other EPoint.
     * @see java.awt.geom.Point2D#equals
     */
    public boolean equals(EPoint that) {
        return this.getGridX() == that.getGridX() && this.getGridY() == that.getGridY();
    }

    private static class EPointInt extends EPoint {

        /**
         * The X coordinate of this
         * <code>EPoint</code> in grid unuts.
         */
        private final int gridX;
        /**
         * The Y coordinate of this
         * <code>EPoint</code> in grid units.
         */
        private final int gridY;

        /**
         * Constructs and initializes a
         * <code>EPoint</code> with the specified coordinates in grid units.
         *
         * @param gridX the x-coordinate to which to set the newly constructed
         * <code>EPoint</code> in grid units.
         * @param gridX the y-coordinate to which to set the newly constructed
         * <code>EPoint</code> in grid units.
         */
        private EPointInt(int gridX, int gridY) {
            this.gridX = gridX;
            this.gridY = gridY;
        }

        /**
         * Returns the X coordinate of this
         * <code>EPoint</code> in grid units in
         * <code>long</code> precision.
         *
         * @return the X coordinate of this
         * <code>EPoint</code>.
         */
        @Override
        public long getGridX() {
            return gridX;
        }

        /**
         * Returns the Y coordinate of this
         * <code>EPoint</code> in grid units in
         * <code>long</code> precision.
         *
         * @return the Y coordinate of this
         * <code>EPoint</code>.
         */
        @Override
        public long getGridY() {
            return gridY;
        }
    }

    private static class EPointLong extends EPoint {

        private final long gridX;
        private final long gridY;

        private EPointLong(long gridX, long gridY) {
            this.gridX = gridX;
            this.gridY = gridY;
        }

        /**
         * Returns the X coordinate of this
         * <code>EPoint</code> in fixed-point units in
         * <code>long</code> precision.
         *
         * @return the X coordinate of this
         * <code>EPoint</code>.
         */
        @Override
        public long getFixpX() {
            return gridX << FixpCoord.FRACTION_BITS;
        }

        /**
         * Returns the Y coordinate of this
         * <code>EPoint</code> in fixed-point units in
         * <code>long</code> precision.
         *
         * @return the Y coordinate of this
         * <code>EPoint</code>.
         */
        @Override
        public long getFixpY() {
            return gridY << FixpCoord.FRACTION_BITS;
        }

        /**
         * Returns the X coordinate of this
         * <code>EPoint</code> in grid units in
         * <code>long</code> precision.
         *
         * @return the X coordinate of this
         * <code>EPoint</code>.
         */
        @Override
        public long getGridX() {
            return gridX;
        }

        /**
         * Returns the Y coordinate of this
         * <code>EPoint</code> in grid units in
         * <code>long</code> precision.
         *
         * @return the Y coordinate of this
         * <code>EPoint</code>.
         */
        @Override
        public long getGridY() {
            return gridY;
        }
    }
}
