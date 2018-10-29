/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: PolyNodeMerge.java
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

import java.awt.geom.Rectangle2D;

/**
 * The intention of this interface is to make transparent transistion between
 * merge structures and the rest of the database classes.
 */
public interface PolyNodeMerge 
{
	/**
	 * Method to get the polygon object.
	 * @return a PolyBase object.
	 */
    public PolyBase getPolygon();

	/**
	 * Method to get the bounds of the poly node.
	 * @return a Rectangle2D with the bounds.
	 */
    public Rectangle2D getBounds2D();
}
