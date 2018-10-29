/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: ERaster.java
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
package com.sun.electric.tool.user.redisplay;

import com.sun.electric.database.geometry.EGraphics;

/**
 * Interface to describe an offscreen data for a single layer
 */
public interface ERaster {

    /**
     * Method to fill a box [lX,hX] x [lY,hY].
     * Both low and high coordinates are inclusive.
     * Filling might be patterned.
     * @param lX left X coordinate
     * @param hX right X coordinate
     * @param lY top Y coordinate
     * @param hY bottom Y coordinate
     */
    public void fillBox(int lX, int hX, int lY, int hY);

    /**
     * Method to fill a horizontal scanline [lX,hX] x [y].
     * Both low and high coordinates are inclusive.
     * Filling might be patterned.
     * @param y Y coordinate
     * @param lX left X coordinate
     * @param hX right X coordinate
     */
    public void fillHorLine(int y, int lX, int hX);

    /**
     * Method to fill a vertical scanline [x] x [lY,hY].
     * Both low and high coordinates are inclusive.
     * Filling might be patterned.
     * @param x X coordinate
     * @param lY top Y coordinate
     * @param hY bottom Y coordinate
     */
    public void fillVerLine(int x, int lY, int hY);

    /**
     * Method to fill a point.
     * Filling might be patterned.
     * @param x X coordinate
     * @param y Y coordinate
     */
    public void fillPoint(int x, int y);

    /**
     * Method to draw a horizontal line [lX,hX] x [y].
     * Both low and high coordinates are inclusive.
     * Drawing is always solid.
     * @param y Y coordinate
     * @param lX left X coordinate
     * @param hX right X coordinate
     */
    public void drawHorLine(int y, int lX, int hX);

    /**
     * Method to draw a vertical line [x] x [lY,hY].
     * Both low and high coordinates are inclusive.
     * Drawing is always solid.
     * @param x X coordinate
     * @param lY top Y coordinate
     * @param hY bottom Y coordinate
     */
    public void drawVerLine(int x, int lY, int hY);

    /**
     * Method to draw a point.
     * @param x X coordinate
     * @param y Y coordinate
     */
    public void drawPoint(int x, int y);

    /**
     * Method to return Electric Outline style for this ERaster.
     * @return Electric Outline style for this ERaster or null for no outline.
     */
    public EGraphics.Outline getOutline();

    /**
     * Method to copy bits from rectangle of source TransparentRaster to thus ERaster.
     * @param src source TransparentRaster.
     * @param minSrcX left bound of source rectangle (inclusive).
     * @param maxSrcX right bound of source rectangle (inclusive).
     * @param minSrcY top bound of source rectangle (inclusive).
     * @param maxSrcY bottom bound of source rectangle (inclusive).
     * @param dx the X translation factor from src space to dst space of the copy.
     * @param dy the Y translation factor from src space to dst space of the copy.
     */
    public void copyBits(TransparentRaster src, int minSrcX, int maxSrcX, int minSrcY, int maxSrcY, int dx, int dy);
}
