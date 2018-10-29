/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: CapFloorplan.java
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
package com.sun.electric.tool.generator.layout.fill;

/**
 * FloorPlan.
 * User: gg151869
 * Date: Feb 23, 2007
 */
class Floorplan {
	public final double cellWidth;
	public final double cellHeight;
	public final boolean horizontal;
	public Floorplan(double width, double height, boolean horiz) {
		cellWidth = width;
		cellHeight = height;
		horizontal = horiz;
	}
}

/**
 * CapFloorPlan.
 */
public class CapFloorplan extends Floorplan {
	public CapFloorplan(double width, double height, boolean horiz) {
		super(width, height, horiz);
	}
}
