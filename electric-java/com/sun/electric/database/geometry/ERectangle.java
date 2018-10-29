/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: ERectangle.java
 * Written by: Dmitry Nadezhin.
 *
 * Copyright (c) 2006, Static Free Software. All rights reserved.
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

import com.sun.electric.util.collections.ImmutableArrayList;
import com.sun.electric.util.math.AbstractFixpRectangle;
import com.sun.electric.util.math.DBMath;
import com.sun.electric.util.math.ECoord;
import com.sun.electric.util.math.FixpCoord;
import com.sun.electric.util.math.GenMath;

import java.awt.geom.Rectangle2D;
import java.io.Serializable;

/**
 * The
 * <code>ERectangle</code> immutable class defines a point representing
 * defined by a location (x,&nbsp;y) and dimension (w&nbsp;x&nbsp;h).
 * <p>
 * This class is used in Electric database.
 */
public class ERectangle extends AbstractFixpRectangle implements Serializable {

    public static final ERectangle ORIGIN = new ERectangle(0, 0, 0, 0);
    public static final ERectangle[] NULL_ARRAY = {};
    public static final ImmutableArrayList<ERectangle> EMPTY_LIST = ImmutableArrayList.of();
    private final long gridMinX;
    private final long gridMinY;
    private final long gridMaxX;
    private final long gridMaxY;

    /**
     * Constructs and initializes a
     * <code>ERectangle</code>
     * from the specified grid coordinates.
     * @param gridX,&nbsp;gridY the coordinates of the upper left corner
     * of the newly constructed <code>ERectangle</code> in grid units.
     * @param gridWidth the width of the
     * newly constructed <code>ERectangle</code> in grid units.
     * @param gridHeight the height of the
     * newly constructed <code>ERectangle</code> in grid units.
     */
    private ERectangle(long gridX, long gridY, long gridWidth, long gridHeight) {
        gridMinX = gridX;
        gridMinY = gridY;
        gridMaxX = gridX + gridWidth;
        gridMaxY = gridY + gridHeight;
    }

    /**
     * Constructs and initializes a
     * <code>ERectangle</code>
     * from the specified long coordinates in lambda units.
     * @param x the X coordinates of the upper left corner
     * of the newly constructed <code>ERectangle</code>
     * @param y the Y coordinates of the upper left corner
     * of the newly constructed <code>ERectangle</code>
     * @param w the width of the newly constructed <code>ERectangle</code>
     * @param h the height of the newly constructed <code>ERectangle</code>
     */
    public static ERectangle fromLambda(double x, double y, double w, double h) {
        return new ERectangle(DBMath.lambdaToGrid(x), DBMath.lambdaToGrid(y), DBMath.lambdaToGrid(w), DBMath.lambdaToGrid(h));
    }

    /**
     * Constructs and initializes a
     * <code>ERectangle</code>
     * from the specified long coordinates in fixed-point units.
     * @param x the X coordinates of the upper left corner
     * of the newly constructed <code>ERectangle</code>
     * @param y the Y coordinates of the upper left corner
     * of the newly constructed <code>ERectangle</code>
     * @param w the width of the newly constructed <code>ERectangle</code>
     * @param h the height of the newly constructed <code>ERectangle</code>
     */
    public static ERectangle fromFixp(long x, long y, long w, long h) {
        long gridMinX = GenMath.roundToMultipleFloor(x, 1L << FixpCoord.FRACTION_BITS) >> FixpCoord.FRACTION_BITS;
        long gridMinY = GenMath.roundToMultipleFloor(y, 1L << FixpCoord.FRACTION_BITS) >> FixpCoord.FRACTION_BITS;
        long gridMaxX = GenMath.roundToMultipleCeiling(x + w, 1L << FixpCoord.FRACTION_BITS) >> FixpCoord.FRACTION_BITS;
        long gridMaxY = GenMath.roundToMultipleCeiling(y + w, 1L << FixpCoord.FRACTION_BITS) >> FixpCoord.FRACTION_BITS;
        return fromGrid(gridMinX, gridMinY, gridMaxX - gridMinX, gridMaxY - gridMinY);
    }

    /**
     * Constructs and initializes a
     * <code>ERectangle</code>
     * from the specified long coordinates in grid units.
     * @param x the X coordinates of the upper left corner
     * of the newly constructed <code>ERectangle</code>
     * @param y the Y coordinates of the upper left corner
     * of the newly constructed <code>ERectangle</code>
     * @param w the width of the newly constructed <code>ERectangle</code>
     * @param h the height of the newly constructed <code>ERectangle</code>
     */
    public static ERectangle fromGrid(long x, long y, long w, long h) {
        return new ERectangle(x, y, w, h);
    }

    /**
     * Returns
     * <code>ERectangle</code> from specified
     * <code>Rectangle2D</code> in lambda units
     * snapped to the grid.
     * @param r specified ERectangle
     * @return Snapped ERectangle
     */
    public static ERectangle fromLambda(Rectangle2D r) {
        if (r instanceof ERectangle) {
            return (ERectangle) r;
        }
        return fromLambda(r.getX(), r.getY(), r.getWidth(), r.getHeight());
    }

    /**
     * Returns
     * <code>ERectangle</code> from specified
     * <code>Rectangle2D</code> in fixed-point units
     * snapped to the grid.
     * @param r specified ERectangle
     * @return Snapped ERectangle
     */
    public static ERectangle fromFixp(Rectangle2D r) {
        long x1 = (long) Math.floor(r.getMinX());
        long y1 = (long) Math.floor(r.getMinY());
        long x2 = (long) Math.ceil(r.getMaxX());
        long y2 = (long) Math.ceil(r.getMaxY());
        return fromFixp(x1, y1, x2 - x1, y2 - y1);
    }

    /**
     * Returns
     * <code>ERectangle</code> from specified
     * <code>Rectangle2D</code> in grid units
     * snapped to the grid.
     * @param r specified ERectangle
     * @return Snapped ERectangle
     */
    public static ERectangle fromGrid(Rectangle2D r) {
        long x1 = (long) Math.floor(r.getMinX());
        long y1 = (long) Math.floor(r.getMinY());
        long x2 = (long) Math.ceil(r.getMaxX());
        long y2 = (long) Math.ceil(r.getMaxY());
        return fromGrid(x1, y1, x2 - x1, y2 - y1);
    }

    /**
     * Returns the width of this
     * <code>ERectangle</code> as ECoord object.
     * @return the width of this <code>ERectangle</code> as ECoord object.
     */
    @Override
    public ECoord getCoordWidth() {
        return ECoord.fromGrid(gridMaxX - gridMinX);
    }

    /**
     * Returns the heigth of this
     * <code>ERectangle</code> as ECoord object.
     * @return the heigth of this <code>ERectangle</code> as ECoord object.
     */
    @Override
    public ECoord getCoordHeight() {
        return ECoord.fromGrid(gridMaxY - gridMinY);
    }

    /**
     * Returns the smallest X coordinate of this
     * <code>ERectangle</code> as ECoord object.
     * @return the smallest x coordinate of this <code>ERectangle</code> as ECoord object.
     */
    @Override
    public ECoord getCoordMinX() {
        return ECoord.fromGrid(gridMinX);
    }

    /**
     * Returns the smallest Y coordinate of this
     * <code>ERectangle</code> as ECoord object.
     * @return the smallest y coordinate of this <code>ERectangle</code> as ECoord object.
     */
    @Override
    public ECoord getCoordMinY() {
        return ECoord.fromGrid(gridMinY);
    }

    /**
     * Returns the largest X coordinate of this
     * <code>ERectangle</code> as ECoord object.
     * @return the largest x coordinate of this <code>ERectangle</code> as ECoord object.
     */
    @Override
    public ECoord getCoordMaxX() {
        return ECoord.fromGrid(gridMaxX);
    }

    /**
     * Returns the largest Y coordinate of this
     * <code>ERectangle</code> as ECoord object.
     * @return the largest y coordinate of this <code>ERectangle</code> as ECoord object.
     */
    @Override
    public ECoord getCoordMaxY() {
        return ECoord.fromGrid(gridMaxY);
    }

    /**
     * Returns the X coordinate of the center of this
     * <code>ERectangle</code> as FixpCoord object.
     * @return the x coordinate of this <code>ERectangle</code> object's center.
     */
    @Override
    public FixpCoord getCoordCenterX() {
        return FixpCoord.fromFixp((gridMinX + gridMaxX) << (FixpCoord.FRACTION_BITS - 1));
    }

    /**
     * Returns the Y coordinate of the center of this
     * <code>ERectangle</code> as FixpCoord object.
     * @return the y coordinate of this <code>ERectangle</code> object's center.
     */
    @Override
    public FixpCoord getCoordCenterY() {
        return FixpCoord.fromFixp((gridMinY + gridMaxY) << (FixpCoord.FRACTION_BITS - 1));
    }

    /**
     * Returns the X coordinate of this
     * <code>ERectangle</code>
     * in fixed-point units in long precision.
     * @return the X coordinate of this <code>ERectangle</code>.
     */
    @Override
    public long getFixpX() {
        return gridMinX << FixpCoord.FRACTION_BITS;
    }

    /**
     * Returns the Y coordinate of this
     * <code>ERectangle</code>
     * in fixed-point units in long precision.
     * @return the X coordinate of this <code>ERectangle</code>.
     */
    @Override
    public long getFixpY() {
        return gridMinY << FixpCoord.FRACTION_BITS;
    }

    /**
     * Returns the width of this
     * <code>ERectangle</code>
     * in fixed-point units in long precision.
     * @return the width of this <code>ERectangle</code>.
     */
    @Override
    public long getFixpWidth() {
        return (gridMaxX - gridMinX) << FixpCoord.FRACTION_BITS;
    }

    /**
     * Returns the heigth of this
     * <code>ERectangle</code>
     * in fixed-point units in long precision.
     * @return the heigth of this <code>ERectangle</code>.
     */
    @Override
    public long getFixpHeight() {
        return (gridMaxY - gridMinY) << FixpCoord.FRACTION_BITS;
    }

    /**
     * Returns the smallest X coordinate of this
     * <code>ERectangle</code>
     * in fixed-point units in
     * <code>long</code> precision.
     * @return the smallest x coordinate of this <code>ERectangle</code>.
     */
    @Override
    public long getFixpMinX() {
        return gridMinX << FixpCoord.FRACTION_BITS;
    }

    /**
     * Returns the smallest Y coordinate of this
     * <code>ERectangle</code>
     * in fixed-point units in
     * <code>long</code> precision.
     * @return the smallest y coordinate of this <code>ERectangle</code>.
     */
    @Override
    public long getFixpMinY() {
        return gridMinY << FixpCoord.FRACTION_BITS;
    }

    /**
     * Returns the largest X coordinate of this
     * <code>ERectangle</code>
     * in fixed-point units in
     * <code>long</code> precision.
     * @return the largest x coordinate of this <code>ERectangle</code>.
     */
    @Override
    public long getFixpMaxX() {
        return gridMaxX << FixpCoord.FRACTION_BITS;
    }

    /**
     * Returns the largest Y coordinate of this
     * <code>ERectangle</code>
     * in fixed-point units in
     * <code>long</code> precision.
     * @return the largest y coordinate of this <code>ERectangle</code>.
     */
    @Override
    public long getFixpMaxY() {
        return gridMaxY << FixpCoord.FRACTION_BITS;
    }

    /**
     * Returns the X coordinate of the center of this
     * <code>ERectangle</code>
     * in fixed-point units in
     * <code>long</code> precision.
     * @return the x coordinate of this <code>ERectangle</code> object's center.
     */
    @Override
    public long getFixpCenterX() {
        return (gridMinX + gridMaxX) << (FixpCoord.FRACTION_BITS - 1);
    }

    /**
     * Returns the Y coordinate of the center of this
     * <code>ERectangle</code>
     * in fixed-point units in
     * <code>long</code> precision.
     * @return the y coordinate of this <code>ERectangle</code> object's center.
     */
    @Override
    public long getFixpCenterY() {
        return (gridMinY + gridMaxY) << (FixpCoord.FRACTION_BITS - 1);
    }

    /**
     * Returns the X coordinate of this
     * <code>ERectangle</code>
     * in grid units in long precision.
     * @return the X coordinate of this <code>ERectangle</code>.
     */
    public long getGridX() {
        return gridMinX;
    }

    /**
     * Returns the Y coordinate of this
     * <code>ERectangle</code>
     * in grid units in long precision.
     * @return the X coordinate of this <code>ERectangle</code>.
     */
    public long getGridY() {
        return gridMinY;
    }

    /**
     * Returns the width of this
     * <code>ERectangle</code>
     * in grid units in long precision.
     * @return the width of this <code>ERectangle</code>.
     */
    public long getGridWidth() {
        return gridMaxX - gridMinX;
    }

    /**
     * Returns the heigth of this
     * <code>ERectangle</code>
     * in grid units in long precision.
     * @return the heigth of this <code>ERectangle</code>.
     */
    public long getGridHeight() {
        return gridMaxY - gridMinY;
    }

    /**
     * Returns the smallest X coordinate of this
     * <code>ERectangle</code>
     * in grid units in
     * <code>long</code> precision.
     * @return the smallest x coordinate of this <code>ERectangle</code>.
     */
    public long getGridMinX() {
        return gridMinX;
    }

    /**
     * Returns the smallest Y coordinate of this
     * <code>ERectangle</code>
     * in grid units in
     * <code>long</code> precision.
     * @return the smallest y coordinate of this <code>ERectangle</code>.
     */
    public long getGridMinY() {
        return gridMinY;
    }

    /**
     * Returns the largest X coordinate of this
     * <code>ERectangle</code>
     * in grid units in
     * <code>long</code> precision.
     * @return the largest x coordinate of this <code>ERectangle</code>.
     */
    public long getGridMaxX() {
        return gridMaxX;
    }

    /**
     * Returns the largest Y coordinate of this
     * <code>ERectangle</code>
     * in grid units in
     * <code>long</code> precision.
     * @return the largest y coordinate of this <code>ERectangle</code>.
     */
    public long getGridMaxY() {
        return gridMaxY;
    }

    /**
     * Returns the X coordinate of the center of this
     * <code>ERectangle</code>
     * in grid units in
     * <code>long</code> precision.
     * @return the x coordinate of this <code>ERectangle</code> object's center.
     */
    public double getGridCenterX() {
        return (gridMinX + gridMaxX) >> 1;
    }

    /**
     * Returns the Y coordinate of the center of this
     * <code>ERectangle</code>
     * in grid units in
     * <code>long</code> precision.
     * @return the y coordinate of this <code>ERectangle</code> object's center.
     */
    public double getGridCenterY() {
        return (gridMinY + gridMaxY) >> 1;
    }

    @Override
    public boolean isEmpty() {
        return gridMinX >= gridMaxX || gridMinY >= gridMaxY;
    }

    @Override
    public void setRect(double x, double y, double w, double h) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setFixp(long fixpMinX, long fixpMinY, long fixpMaxX, long fixpMaxY) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ERectangle createFixp(long fixpMinX, long fixpMinY, long fixpMaxX, long fixpMaxY) {
        return fromFixp(fixpMinX, fixpMinY, fixpMaxX - fixpMinX, fixpMaxY - fixpMinY);
    }

    @Override
    public int outcode(double x, double y) {
        int out = 0;
        if (gridMinX >= gridMaxX) {
            out |= OUT_LEFT | OUT_RIGHT;
        } else if (x * DBMath.GRID < gridMinX) {
            out |= OUT_LEFT;
        } else if (x * DBMath.GRID > gridMaxX) {
            out |= OUT_RIGHT;
        }
        if (gridMinY >= gridMaxY) {
            out |= OUT_TOP | OUT_BOTTOM;
        } else if (y * DBMath.GRID < gridMinY) {
            out |= OUT_TOP;
        } else if (y * DBMath.GRID > gridMaxY) {
            out |= OUT_BOTTOM;
        }
        return out;
    }

    @Override
    public Rectangle2D getBounds2D() {
        return this;
    }

    @Override
    public Rectangle2D createIntersection(Rectangle2D r) {
        if (r instanceof ERectangle) {
            ERectangle src = (ERectangle) r;
            long x1 = Math.max(gridMinX, src.gridMinX);
            long y1 = Math.max(gridMinY, src.gridMinY);
            long x2 = Math.min(gridMaxX, src.gridMaxX);
            long y2 = Math.min(gridMaxY, src.gridMaxY);
            if (x1 == gridMinX && y1 == gridMinY && x2 == gridMaxX && y2 == gridMaxY) {
                return this;
            }
            if (x1 == src.gridMinX && y1 == src.gridMinY && x2 == src.gridMaxX && y2 == src.gridMaxY) {
                return src;
            }
            return new ERectangle(x1, y1, x2 - x1, y2 - y1);
        }
        Rectangle2D dest = new Rectangle2D.Double();
        Rectangle2D.intersect(this, r, dest);
        return dest;
    }

    @Override
    public Rectangle2D createUnion(Rectangle2D r) {
        if (r instanceof ERectangle) {
            ERectangle src = (ERectangle) r;
            long x1 = Math.min(gridMinX, src.gridMinX);
            long y1 = Math.min(gridMinY, src.gridMinY);
            long x2 = Math.max(gridMaxX, src.gridMaxX);
            long y2 = Math.max(gridMaxY, src.gridMaxY);
            if (x1 == gridMinX && y1 == gridMinY && x2 == gridMaxX && y2 == gridMaxY) {
                return this;
            }
            if (x1 == src.gridMinX && y1 == src.gridMinY && x2 == src.gridMaxX && y2 == src.gridMaxY) {
                return src;
            }
            return new ERectangle(x1, y1, x2 - x1, y2 - 1);
        }
        Rectangle2D dest = new Rectangle2D.Double();
        Rectangle2D.union(this, r, dest);
        return dest;
    }
//    @Override
//    public String toString() {
//        return getClass().getName() + "[x=" + getX() + ",y=" + getY() + ",w=" + getWidth() + ",h=" + getHeight() + "]";
//    }
}
