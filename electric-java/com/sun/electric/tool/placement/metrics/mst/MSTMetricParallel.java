/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: MSTMetric.java
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
package com.sun.electric.tool.placement.metrics.mst;

import com.sun.electric.database.topology.SteinerTree.SteinerTreePortPair;
import com.sun.electric.tool.placement.PlacementFrame.PlacementNetwork;
import com.sun.electric.tool.placement.PlacementFrame.PlacementNode;
import com.sun.electric.tool.placement.metrics.AbstractMetric;
import com.sun.electric.tool.util.concurrent.Parallel;
import com.sun.electric.tool.util.concurrent.utils.BlockedRange1D;

import java.util.List;
import java.util.Map;

/**
 * Parallel Placement
 */
public class MSTMetricParallel extends AbstractMetric {

	public MSTMetricParallel(List<PlacementNode> nodesToPlace, List<PlacementNetwork> allNetworks,
		Map<PlacementNetwork,List<SteinerTreePortPair>> optimalConnections) {
		super(nodesToPlace, allNetworks, optimalConnections);
	}

	@Override
	public Double compute() {
		return Parallel.Reduce(new BlockedRange1D(0, allNetworks.size(), 128), new MSTMetricTask(
				nodesToPlace, allNetworks));
	}

	@Override
	public String getMetricName() {
		return "MSTMetric";
	}
}
