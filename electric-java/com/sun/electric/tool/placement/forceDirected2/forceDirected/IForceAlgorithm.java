/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: IForceAlgorithm.java
 * Written by Team 7: Felix Schmidt, Daniel Lechner
 * 
 * This code has been developed at the Karlsruhe Institute of Technology (KIT), Germany, 
 * as part of the course "Multicore Programming in Practice: Tools, Models, and Languages".
 * Contact instructor: Dr. Victor Pankratius (pankratius@ipd.uka.de)
 *
 * Copyright (c) 2010, Static Free Software. All rights reserved.
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
package com.sun.electric.tool.placement.forceDirected2.forceDirected;

import com.sun.electric.tool.placement.forceDirected2.forceDirected.util.Force2D;

import java.awt.geom.Point2D;

/**
 * Parallel Placement
 * 
 * Base interface for force calculations
 */
public interface IForceAlgorithm {

	public Force2D calculate(Point2D node1, Point2D node2, double weight, double minDist);

}
