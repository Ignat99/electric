/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: RTBoundsFixp.java 
 *
 * Copyright (c) 2007, Static Free Software. All rights reserved.
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
package com.sun.electric.database.topology;

import com.sun.electric.util.math.AbstractFixpRectangle;

/**
 * Interface to define the objects stored in an R-Tree.
 * The objects passed into the RTNode need only implement this.
 * For most database work, the Geometric is used.
 * However, other simpler objects may also be used.
 */
public interface RTBounds {

    public AbstractFixpRectangle getBounds();
}

