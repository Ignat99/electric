/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: AbstractFixpPoint.java
 * Written by: Dmitry Nadezhin.
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
package com.sun.electric.util.math;

import java.awt.geom.Rectangle2D;

/**
 *
 */
public abstract class AbstractFixpRectangle extends Rectangle2D {

    public abstract long getFixpMinX();

    public abstract long getFixpMinY();

    public abstract long getFixpMaxX();

    public abstract long getFixpMaxY();
    
    public abstract void setFixp(long fixpMinX, long fixpMinY, long fixpMaxX, long fixpMaxY);
    
    public abstract AbstractFixpRectangle createFixp(long fixpMinX, long fixpMinY, long fixpMaxX, long fixpMaxY);

    public long getFixpWidth() {
        return getFixpMaxX() - getFixpMinX();
    }

    public long getFixpHeight() {
        return getFixpMaxY() - getFixpMinY();
    }

    public long getFixpCenterX() {
        return (getFixpMinX() + getFixpMaxX()) >> 1;
    }

    public long getFixpCenterY() {
        return (getFixpMinY() + getFixpMaxY()) >> 1;
    }

    @Override
    public void setRect(double x, double y, double w, double h) {
        long fixpMinX = FixpCoord.lambdaToFixp(x);
        long fixpMinY = FixpCoord.lambdaToFixp(y);
        long fixpMaxX = fixpMinX + FixpCoord.lambdaToFixp(w);
        long fixpMaxY = fixpMinY + FixpCoord.lambdaToFixp(h);
        setFixp(fixpMinX, fixpMinY, fixpMaxX, fixpMaxY);
    }

    @Override
    public int outcode(double x, double y) {
        long fixpMinX = getFixpMinX();
        long fixpMinY = getFixpMinY();
        long fixpMaxX = getFixpMaxX();
        long fixpMaxY = getFixpMaxY();
        int out = 0;
        if (fixpMinX >= fixpMaxX) {
            out |= OUT_LEFT | OUT_RIGHT;
        } else if (x * (FixpCoord.GRIDS_IN_LAMBDA * FixpCoord.FIXP_SCALE) < fixpMinX) {
            out |= OUT_LEFT;
        } else if (x * (FixpCoord.GRIDS_IN_LAMBDA * FixpCoord.FIXP_SCALE) > fixpMaxX) {
            out |= OUT_RIGHT;
        }
        if (fixpMinY >= fixpMaxY) {
            out |= OUT_TOP | OUT_BOTTOM;
        } else if (y * (FixpCoord.GRIDS_IN_LAMBDA * FixpCoord.FIXP_SCALE) < fixpMinY) {
            out |= OUT_TOP;
        } else if (y * (FixpCoord.GRIDS_IN_LAMBDA * FixpCoord.FIXP_SCALE) > fixpMaxY) {
            out |= OUT_BOTTOM;
        }
        return out;
    }

    @Override
    public Rectangle2D createIntersection(Rectangle2D r) {
        if (r instanceof AbstractFixpRectangle) {
            AbstractFixpRectangle src = (AbstractFixpRectangle) r;
            long x1 = Math.max(getFixpMinX(), src.getFixpMinX());
            long y1 = Math.max(getFixpMinY(), src.getFixpMinY());
            long x2 = Math.min(getFixpMaxX(), src.getFixpMaxX());
            long y2 = Math.min(getFixpMaxY(), src.getFixpMaxY());
            return createFixp(x1, y1, x2, y2);
        }
        Rectangle2D dest = createFixp(0, 0, 0, 0);
        Rectangle2D.intersect(this, r, dest);
        return dest;
    }

    @Override
    public Rectangle2D createUnion(Rectangle2D r) {
        if (r instanceof AbstractFixpRectangle) {
            AbstractFixpRectangle src = (AbstractFixpRectangle) r;
            long x1 = Math.min(getFixpMinX(), src.getFixpMinX());
            long y1 = Math.min(getFixpMinY(), src.getFixpMinY());
            long x2 = Math.max(getFixpMaxX(), src.getFixpMaxX());
            long y2 = Math.max(getFixpMaxY(), src.getFixpMaxY());
            return createFixp(x1, y1, x2, y2);
        }
        Rectangle2D dest = createFixp(0, 0, 0, 0);
        Rectangle2D.union(this, r, dest);
        return dest;
    }

    @Override
    public void add(Rectangle2D r) {
        if (r instanceof AbstractFixpRectangle) {
            AbstractFixpRectangle af = (AbstractFixpRectangle) r;
            setFixp(Math.min(getFixpMinX(), af.getFixpMinX()), Math.min(getFixpMinY(), af.getFixpMinY()),
                Math.max(getFixpMaxX(), af.getFixpMaxX()), Math.max(getFixpMaxY(), af.getFixpMaxY()));
        } else {
            super.add(r);
        }
    }

    @Override
    public void setRect(Rectangle2D r) {
        if (r instanceof AbstractFixpRectangle) {
            AbstractFixpRectangle af = (AbstractFixpRectangle) r;
            setFixp(af.getFixpMinX(), af.getFixpMinY(), af.getFixpMaxX(), af.getFixpMaxY());
        } else {
            super.setRect(r);
        }
    }

    /**
     * Returns the X coordinate of this <code>AbstractFixpRectangle</code>
     * in lambda units in double precision.
     * @return the X coordinate of this <code>AbstractFixpRectangle</code>.
     */
    @Override
    public double getX() {
        return FixpCoord.fixpToLambda(getFixpMinX());
    }

    /**
     * Returns the Y coordinate of this <code>AbstractFixpRectangle</code>
     * in lambda units in double precision.
     * @return the X coordinate of this <code>AbstractFixpRectangle</code>.
     */
    @Override
    public double getY() {
        return FixpCoord.fixpToLambda(getFixpMinY());
    }

    /**
     * Returns the width of this <code>AbstractFixpRectangle</code>
     * in lambda units in double precision.
     * @return the width of this <code>AbstractFixpRectangle</code>.
     */
    @Override
    public double getWidth() {
        return FixpCoord.fixpToLambda(getFixpWidth());
    }

    /**
     * Returns the heigth of this <code>AbstractFixpRectangle</code>
     * in lambda units in double precision.
     * @return the heigth of this <code>AbstractFixpRectangle</code>.
     */
    @Override
    public double getHeight() {
        return FixpCoord.fixpToLambda(getFixpHeight());
    }
    
    /**
     * Returns the smallest X coordinate of this <code>AbstractFixpRectangle</code>
     * in lambda units in <code>double</code> precision.
     * @return the smallest x coordinate of this <code>AbstractFixpRectangle</code>.
     */
    @Override
    public double getMinX() {
        return FixpCoord.fixpToLambda(getFixpMinX());
    }

    /**
     * Returns the smallest Y coordinate of this <code>AbstractFixpRectangle</code>
     * in lambda units in <code>double</code> precision.
     * @return the smallest y coordinate of this <code>AbstractFixpRectangle</code>.
     */
    @Override
    public double getMinY() {
        return FixpCoord.fixpToLambda(getFixpMinY());
    }

    /**
     * Returns the largest X coordinate of this <code>AbstractFixpRectangle</code>
     * in lambda units in <code>double</code> precision.
     * @return the largest x coordinate of this <code>AbstractFixpRectangle</code>.
     */
    @Override
    public double getMaxX() {
        return FixpCoord.fixpToLambda(getFixpMaxX());
    }

    /**
     * Returns the largest Y coordinate of this <code>AbstractFixpRectangle</code>
     * in lambda units in <code>double</code> precision.
     * @return the largest y coordinate of this <code>AbstractFixpRectangle</code>.
     */
    @Override
    public double getMaxY() {
        return FixpCoord.fixpToLambda(getFixpMaxY());
    }
    
    /**
     * Returns the X coordinate of the center of the framing
     * rectangle of the <code>AbstractFixpRectangle</code> in <code>double</code>
     * precision.
     * @return the X coordinate of the center of the framing rectangle
     *          of the <code>AbstractFixpRectangle</code>.
     */
    @Override
    public double getCenterX() {
        return FixpCoord.fixpToLambda(getFixpCenterX());
    }

    /**
     * Returns the Y coordinate of the center of the framing
     * rectangle of the <code>AbstractFixpRectangle</code> in <code>double</code>
     * precision.
     * @return the Y coordinate of the center of the framing rectangle
     *          of the <code>AbstractFixpRectangle</code>.
     */
    @Override
    public double getCenterY() {
        return FixpCoord.fixpToLambda(getFixpCenterY());
    }

    /**
     * Returns the width of this <code>AbstractFixpRectangle</code> as ECoord object.
     * @return the width of this <code>AbstractFixpRectangle</code> as ECoord object.
     */
    public FixpCoord getCoordWidth() {
        return FixpCoord.fromFixp(getFixpWidth());
    }

    /**
     * Returns the heigth of this <code>AbstractFixpRectangle</code> as ECoord object.
     * @return the heigth of this <code>AbstractFixpRectangle</code> as ECoord object.
     */
    public FixpCoord getCoordHeight() {
        return FixpCoord.fromFixp(getFixpHeight());
    }

    /**
     * Returns the smallest X coordinate of this <code>AbstractFixpRectangle</code> as ECoord object.
     * @return the smallest x coordinate of this <code>AbstractFixpRectangle</code> as ECoord object.
     */
    public FixpCoord getCoordMinX() {
        return FixpCoord.fromFixp(getFixpMinX());
    }

    /**
     * Returns the smallest Y coordinate of this <code>AbstractFixpRectangle</code> as ECoord object.
     * @return the smallest y coordinate of this <code>AbstractFixpRectangle</code> as ECoord object.
     */
    public FixpCoord getCoordMinY() {
        return FixpCoord.fromFixp(getFixpMinY());
    }

    /**
     * Returns the largest X coordinate of this <code>AbstractFixpRectangle</code> as ECoord object.
     * @return the largest x coordinate of this <code>AbstractFixpRectangle</code> as ECoord object.
     */
    public FixpCoord getCoordMaxX() {
        return FixpCoord.fromFixp(getFixpMaxX());
    }

    /**
     * Returns the largest Y coordinate of this <code>AbstractFixpRectangle</code> as ECoord object.
     * @return the largest y coordinate of this <code>AbstractFixpRectangle</code> as ECoord object.
     */
    public FixpCoord getCoordMaxY() {
        return FixpCoord.fromFixp(getFixpMaxY());
    }

    /**
     * Returns the X coordinate of the center of this <code>AbstractFixpRectangle</code> as FixpCoord object.
     * @return the x coordinate of this <code>AbstractFixpRectangle</code> object's center.
     */
    public FixpCoord getCoordCenterX() {
        return FixpCoord.fromFixp(getFixpCenterX());
    }

    /**
     * Returns the Y coordinate of the center of this <code>AbstractFixpRectangle</code> as FixpCoord object.
     * @return the y coordinate of this <code>AbstractFixpRectangle</code> object's center.
     */
    public FixpCoord getCoordCenterY() {
        return FixpCoord.fromFixp(getFixpCenterX());
    }

    /**
     * Returns the X coordinate of this <code>AbstractFixpRectangle</code>
     * in lambda units in double precision.
     * @return the X coordinate of this <code>AbstractFixpRectangle</code>.
     */
    public double getLambdaX() {
        return FixpCoord.fixpToLambda(getFixpMinX());
    }

    /**
     * Returns the Y coordinate of this <code>AbstractFixpRectangle</code>
     * in lambda units in double precision.
     * @return the X coordinate of this <code>AbstractFixpRectangle</code>.
     */
    public double getLambdaY() {
        return FixpCoord.fixpToLambda(getFixpMinY());
    }

    /**
     * Returns the width of this <code>AbstractFixpRectangle</code>
     * in lambda units in double precision.
     * @return the width of this <code>AbstractFixpRectangle</code>.
     */
    public double getLambdaWidth() {
        return FixpCoord.fixpToLambda(getFixpWidth());
    }

    /**
     * Returns the heigth of this <code>AbstractFixpRectangle</code>
     * in lambda units in double precision.
     * @return the heigth of this <code>AbstractFixpRectangle</code>.
     */
    public double getLambdaHeight() {
        return FixpCoord.fixpToLambda(getFixpHeight());
    }

    /**
     * Returns the smallest X coordinate of this <code>AbstractFixpRectangle</code>
     * in lambda units in <code>double</code> precision.
     * @return the smallest x coordinate of this <code>AbstractFixpRectangle</code>.
     */
    public double getLambdaMinX() {
        return FixpCoord.fixpToLambda(getFixpMinX());
    }

    /**
     * Returns the smallest Y coordinate of this <code>AbstractFixpRectangle</code>
     * in lambda units in <code>double</code> precision.
     * @return the smallest y coordinate of this <code>AbstractFixpRectangle</code>.
     */
    public double getLambdaMinY() {
        return FixpCoord.fixpToLambda(getFixpMinY());
    }

    /**
     * Returns the largest X coordinate of this <code>AbstractFixpRectangle</code>
     * in lambda units in <code>double</code> precision.
     * @return the largest x coordinate of this <code>AbstractFixpRectangle</code>.
     */
    public double getLambdaMaxX() {
        return FixpCoord.fixpToLambda(getFixpMaxX());
    }

    /**
     * Returns the largest Y coordinate of this <code>AbstractFixpRectangle</code>
     * in lambda units in <code>double</code> precision.
     * @return the largest y coordinate of this <code>AbstractFixpRectangle</code>.
     */
    public double getLambdaMaxY() {
        return FixpCoord.fixpToLambda(getFixpMaxY());
    }

    /**
     * Returns the X coordinate of the center of this <code>AbstractFixpRectangle</code>
     * in lambda units in <code>double</code> precision.
     * @return the x coordinate of this <code>AbstractFixpRectangle</code> object's center.
     */
    public double getLambdaCenterX() {
        return FixpCoord.fixpToLambda(getFixpCenterX());
    }

    /**
     * Returns the Y coordinate of the center of this <code>AbstractFixpRectangle</code>
     * in lambda units in <code>double</code> precision.
     * @return the y coordinate of this <code>AbstractFixpRectangle</code> object's center.
     */
    public double getLambdaCenterY() {
        return FixpCoord.fixpToLambda(getFixpCenterY());
    }

    /**
     * Returns the X coordinate of this <code>AbstractFixpRectangle</code>
     * in fixed-point units in long precision.
     * @return the X coordinate of this <code>AbstractFixpRectangle</code>.
     */
    public long getFixpX() {
        return getFixpMinX();
    }

    /**
     * Returns the Y coordinate of this <code>AbstractFixpRectangle</code>
     * in fixed-point units in long precision.
     * @return the Y coordinate of this <code>AbstractFixpRectangle</code>.
     */
    public long getFixpY() {
        return getFixpMinY();
    }

    @Override
    public boolean isEmpty() {
        return getFixpWidth() <= 0 || getFixpHeight() <= 0;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName()
                + "[x=" + getX()
                + ",y=" + getY()
                + ",w=" + getWidth()
                + ",h=" + getHeight() + "]";
    }
}
