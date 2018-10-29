/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: ScreenPoint.java
 *
 * Copyright (c) 2014, Static Free Software. All rights reserved.
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

/**
 * A point at screen coordinates
 */
public class ScreenPoint
{
    public long x;
    public long y;

    public ScreenPoint()
    {
    }

    public ScreenPoint(long x, long y)
    {
        this.x = x;
        this.y = y;
    }

    public long getX()
    {
        return x;
    }

    public long getY()
    {
        return y;
    }

    public int getIntX()
    {
        return (int) x;
    }

    public int getIntY()
    {
        return (int) y;
    }
}
