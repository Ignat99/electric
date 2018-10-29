/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: ViaAmountMetric.java
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
package com.sun.electric.tool.routing.metrics;

import java.util.Iterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.network.Network;
import com.sun.electric.database.topology.NodeInst;

/**
 * @author Felix Schmidt
 *
 * This metric is part of the routing quality metric
 *
 */
public class ViaAmountMetric extends RoutingMetric<Integer> {
	
	private static Logger logger = LoggerFactory.getLogger(ViaAmountMetric.class);

	/* (non-Javadoc)
	 * @see com.sun.electric.tool.routing.metrics.RoutingMetric#calculate(com.sun.electric.database.hierarchy.Cell)
	 */
	public Integer calculate(Cell cell) {

		int result = 0;
		
		for(Iterator<NodeInst> nodes = cell.getNodes(); nodes.hasNext();) {
			NodeInst node = nodes.next();
			if(node.getFunction().isContact())
			{
				result++;
			}
		}
		
		return result;
	}
	
	/**
	 * Calculate number of vias per net
	 * @param net Network to analyze
	 * @return number of contacts found in the net
	 */
	@Override
	public Integer calculate(Network net) {

		int result = 0;
		for(Iterator<NodeInst> nodes = net.getNodes(); nodes.hasNext();) {
			NodeInst node = nodes.next();
			if(node.getFunction().isContact())
			{
				result++;
			}
		}
		
		return result;
	}
}
