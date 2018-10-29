/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: FixpPoint.java
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
public class FixpRectangle extends AbstractFixpRectangle {
    private long fixpMinX;
    private long fixpMinY;
    private long fixpMaxX;
    private long fixpMaxY;
    
    private FixpRectangle(long fixpMinX, long fixpMinY, long fixpMaxX, long fixpMaxY) {
        this.fixpMinX = fixpMinX;
        this.fixpMinY = fixpMinY;
        this.fixpMaxX = fixpMaxX;
        this.fixpMaxY = fixpMaxY;
    }
    
    public static FixpRectangle fromFixpDiagonal(long fixpMinX, long fixpMinY, long fixpMaxX, long fixpMaxY) {
        return new FixpRectangle(fixpMinX, fixpMinY, fixpMaxX, fixpMaxY);
    }
    
    public static FixpRectangle from(Rectangle2D r) {
        if (r instanceof AbstractFixpRectangle) {
            AbstractFixpRectangle fr = (AbstractFixpRectangle)r;
            return new FixpRectangle(fr.getFixpMinX(), fr.getFixpMinY(), fr.getFixpMaxX(), fr.getFixpMaxY());
        }
        long x = FixpCoord.lambdaToFixp(r.getX());
        long y = FixpCoord.lambdaToFixp(r.getY());
        long w = FixpCoord.lambdaToFixp(r.getWidth());
        long h = FixpCoord.lambdaToFixp(r.getHeight());
        return new FixpRectangle(x, y, x + w, y + h);
    } 
    
    @Override
    public long getFixpMinX() {
        return fixpMinX;
    }

    @Override
    public long getFixpMinY() {
        return fixpMinY;
    }

    @Override
    public long getFixpMaxX() {
        return fixpMaxX;
    }

    @Override
    public long getFixpMaxY() {
        return fixpMaxY;
    }
    
    @Override
    public void setFixp(long fixpMinX, long fixpMinY, long fixpMaxX, long fixpMaxY) {
        this.fixpMinX = fixpMinX;
        this.fixpMinY = fixpMinY;
        this.fixpMaxX = fixpMaxX;
        this.fixpMaxY = fixpMaxY;
    }
    
    @Override
    public FixpRectangle createFixp(long fixpMinX, long fixpMinY, long fixpMaxX, long fixpMaxY) {
        return new FixpRectangle(fixpMinX, fixpMinY, fixpMaxX, fixpMaxY);
    }
}
