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

import java.awt.geom.Point2D;

/**
 *
 */
public abstract class AbstractFixpPoint extends Point2D {
    
    public abstract long getFixpX();
    
    public abstract long getFixpY();
    
    public abstract void setFixpLocation(long fixpX, long fixpY);
    
    protected abstract AbstractFixpPoint create(long fixpX, long fixpY);
    
    @Override
    public double getX() {
        return FixpCoord.fixpToLambda(getFixpX());
    }
    
    @Override
    public double getY() {
        return FixpCoord.fixpToLambda(getFixpY());
    }
    
    @Override
    public void setLocation(double x, double y) {
        setFixpLocation(FixpCoord.lambdaToFixp(x), FixpCoord.lambdaToFixp(y));
    }
    
    /**
     * Sets the location of this <code>Point2D</code> to the same
     * coordinates as the specified <code>Point2D</code> object.
     * @param p the specified <code>Point2D</code> to which to set
     * this <code>Point2D</code>
     * @since 1.2
     */
    @Override
    public void setLocation(Point2D p) {
        if (p instanceof AbstractFixpPoint) {
            AbstractFixpPoint fp = (AbstractFixpPoint)p;
            setFixpLocation(fp.getFixpX(), fp.getFixpY());
        } else {
            super.setLocation(p);
        }
    }

    /**
     * Returns the square of the distance from this
     * <code>Point2D</code> to a specified <code>Point2D</code>.
     *
     * @param pt the specified point to be measured
     *           against this <code>Point2D</code>
     * @return the square of the distance between this
     * <code>Point2D</code> to a specified <code>Point2D</code>.
     * @since 1.2
     */
    @Override
    public double distanceSq(Point2D pt) {
        if (pt instanceof AbstractFixpPoint) {
            AbstractFixpPoint fp = (AbstractFixpPoint)pt;
            long fx = getFixpX() - fp.getFixpX();
            long fy = getFixpY() - fp.getFixpY();
            if (fx == 0) {
                double dy = FixpCoord.fixpToLambda(fy);
                return dy * dy;
            } else if (fy == 0) {
                double dx = FixpCoord.fixpToLambda(fx);
                return dx * dx;
            } else {
                double dx = FixpCoord.fixpToLambda(fx);
                double dy = FixpCoord.fixpToLambda(fy);
                return dx * dx + dy * dy;
            }
        }
        return super.distanceSq(pt);
    }
    
    /**
     * Returns the square of the distance from this
     * <code>Point2D</code> to a specified <code>Point2D</code>.
     *
     * @param pt the specified point to be measured
     *           against this <code>Point2D</code>
     * @return the square of the distance between this
     * <code>Point2D</code> to a specified <code>Point2D</code>.
     * @since 1.2
     */
    @Override
    public double distance(Point2D pt) {
        if (pt instanceof AbstractFixpPoint) {
            AbstractFixpPoint fp = (AbstractFixpPoint)pt;
            long fx = getFixpX() - fp.getFixpX();
            long fy = getFixpY() - fp.getFixpY();
            if (fx == 0) {
                return FixpCoord.fixpToLambda(Math.abs(fy));
            } else if (fy == 0) {
                return FixpCoord.fixpToLambda(Math.abs(fx));
            } else {
                double dx = FixpCoord.fixpToLambda(fx);
                double dy = FixpCoord.fixpToLambda(fy);
                return Math.sqrt(dx * dx + dy * dy);
            }
        }
        return super.distance(pt);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof AbstractFixpPoint) {
            AbstractFixpPoint that = (AbstractFixpPoint)o;
            return this.getFixpX() == that.getFixpX() && this.getFixpY() == that.getFixpY();
        }
        return super.equals(o);
    }
    
    @Override
    public String toString() {
       return getClass().getSimpleName() + "["+getX()+", "+getY()+"]";
    }
}
