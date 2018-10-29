/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: ErrorLogger.java
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
package com.sun.electric.api.minarea;

import java.awt.Shape;

public interface ErrorLogger {

    /**
     * The algorithm uses this method to report about polygon that violates
     * min area rule. The algorithm report actual area of the polygon and
     * vertex with lexigraphically maximal coordinates (x,y).
     * This means that rightmost vertical edges of polygon are choosen,
     * and than the most upper vertex on these edges is choosen.
     * Formally, such point (x,y) is reported that for any other point (x',y') of
     * this polygin:  (x' < x || x' == x && y' < y)
     * @param area the area of violating polygon
     * @param x x-coordinate of lexigraphically largest point of violating polygon
     * @param y y-coordinate of lexigraphically largest point of violating polygon
     * @param shape optional Shape of Polygon
     */
    public void reportMinAreaViolation(long area, int x, int y, Shape shape);
}
