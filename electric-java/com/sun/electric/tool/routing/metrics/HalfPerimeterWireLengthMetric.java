/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: WireQualityMetric.java
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

import java.awt.geom.Rectangle2D;
import java.util.Iterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.network.Network;
import com.sun.electric.database.topology.ArcInst;
import com.sun.electric.database.topology.NodeInst;

/**
 * @author Gilda Garreton
 *
 * This metric is part of the routing quality metric
 *
 */
public class HalfPerimeterWireLengthMetric extends RoutingMetric<Double> {

	private static Logger logger = LoggerFactory.getLogger(HalfPerimeterWireLengthMetric.class);

	@Override
	public Double calculate(Cell cell) {
		// TODO Auto-generated method stub
		return null;
	}
	
	/**
	 * Calculate half-perimeter wire length of a network
	 * @param net Network to analyze
	 * @return number of contacts found in the net
	 */
	@Override
	public Double calculate(Network net) 
	{
		double maxX = Double.NEGATIVE_INFINITY;
		double maxY = Double.NEGATIVE_INFINITY;
		double minX = Double.POSITIVE_INFINITY;
		double minY = Double.POSITIVE_INFINITY;
		
		// Bounding box of all arcs first
		for(Iterator<ArcInst> arcs = net.getArcs(); arcs.hasNext();)
		{
			ArcInst ai = arcs.next();
			Rectangle2D rect = ai.getBounds();
			double localMaxX = rect.getMaxX();
			double localMaxY = rect.getMaxY();
			double localMinX = rect.getMinX();
			double localMinY = rect.getMinY();
			if (localMaxX > maxX) maxX = localMaxX;
			if (localMaxY > maxY) maxY = localMaxY;
			if (localMinX < minX) minX = localMinX;
			if (localMinY < minY) minY = localMinY;
		}
		for(Iterator<NodeInst> nodes = net.getNodes(); nodes.hasNext();) 
		{
			NodeInst node = nodes.next();
			Rectangle2D rect = node.getBounds();
			double localMaxX = rect.getMaxX();
			double localMaxY = rect.getMaxY();
			double localMinX = rect.getMinX();
			double localMinY = rect.getMinY();
			if (localMaxX > maxX) maxX = localMaxX;
			if (localMaxY > maxY) maxY = localMaxY;
			if (localMinX < minX) minX = localMinX;
			if (localMinY < minY) minY = localMinY;
		}
		double result = (maxX - minX) + (maxY- minY);
		
		return result;
	}
}
