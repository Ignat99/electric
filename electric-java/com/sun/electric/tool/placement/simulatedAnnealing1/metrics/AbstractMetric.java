/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: AbstractMetric.java
 * Written by Team 2: Jan Barth, Iskandar Abudiab
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
package com.sun.electric.tool.placement.simulatedAnnealing1.metrics;

import com.sun.electric.tool.placement.PlacementFrame.PlacementNetwork;
import com.sun.electric.tool.placement.PlacementFrame.PlacementNode;

import java.text.DecimalFormat;
import java.util.List;

public abstract class AbstractMetric {

	List<PlacementNode> nodesToPlace;
	List<PlacementNetwork> allNetworks;
	
	public AbstractMetric(List<PlacementNode> nodesToPlace, List<PlacementNetwork> allNetworks) {
		this.nodesToPlace = nodesToPlace;
		this.allNetworks = allNetworks;
	}
	
	public abstract double compute();
	
	public abstract String getMetricName();
	
	public String toString() {
		DecimalFormat formater = new DecimalFormat("###,###.#");
		String output = "Result of " + getMetricName() + ": " + formater.format(compute());
		return output;
	}
	
}
