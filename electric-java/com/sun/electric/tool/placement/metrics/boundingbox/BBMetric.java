/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: BBMetric.java
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
package com.sun.electric.tool.placement.metrics.boundingbox;

import com.sun.electric.database.geometry.EPoint;
import com.sun.electric.database.topology.SteinerTree.SteinerTreePortPair;
import com.sun.electric.tool.placement.PlacementFrame.PlacementNetwork;
import com.sun.electric.tool.placement.PlacementFrame.PlacementNode;
import com.sun.electric.tool.placement.PlacementFrame.PlacementPort;
import com.sun.electric.tool.placement.metrics.AbstractMetric;

import java.awt.geom.Point2D;
import java.util.List;
import java.util.Map;

/**
 * Parallel Placement
 * 
 * Estimate wire length using the bounding box metric
 */
public class BBMetric extends AbstractMetric {

	public BBMetric(List<PlacementNode> nodesToPlace, List<PlacementNetwork> allNetworks,
		Map<PlacementNetwork,List<SteinerTreePortPair>> optimalConnections) {
		super(nodesToPlace, allNetworks, optimalConnections);
	}

	/**
	 * estimate wire length using the bounding box metric for all nets
	 * 
	 * @return - the wire length estimate
	 */
	@Override
	public Double compute() {

		double sum = 0;

		for (PlacementNetwork net : this.allNetworks) {
			sum = sum + this.compute(net);
		}

		return new Double(sum);
	}

	private double compute(PlacementNetwork net) {
		double leftmost = Double.MAX_VALUE;
		double rightmost = -Double.MAX_VALUE;
		double uppermost = -Double.MAX_VALUE;
		double undermost = Double.MAX_VALUE;
		if (optimalConnections != null)
		{
			List<SteinerTreePortPair> connections = optimalConnections.get(net);
			for(SteinerTreePortPair pConn : connections)
			{
				EPoint position = pConn.getPort1().getCenter();
				if (position.getX() < leftmost) leftmost = position.getX();
				if (position.getX() > rightmost) rightmost = position.getX();
				if (position.getY() > uppermost) uppermost = position.getY();
				if (position.getY() < undermost) undermost = position.getY();
	
				position = pConn.getPort2().getCenter();
				if (position.getX() < leftmost) leftmost = position.getX();
				if (position.getX() > rightmost) rightmost = position.getX();
				if (position.getY() > uppermost) uppermost = position.getY();
				if (position.getY() < undermost) undermost = position.getY();
			}
		} else
		{
			for(PlacementPort pPort : net.getPortsOnNet())
			{
				Point2D.Double position = this.getPortPosition(pPort);
				if (position.getX() < leftmost) leftmost = position.getX();
				if (position.getX() > rightmost) rightmost = position.getX();
				if (position.getY() > uppermost) uppermost = position.getY();
				if (position.getY() < undermost) undermost = position.getY();
			}
		}

		return (rightmost - leftmost) + (uppermost - undermost);

	}

	@Override
	public String getMetricName() {
		return ("Bounding Box Metric");
	}

	private Point2D.Double getPortPosition(PlacementPort port) {
		double x = port.getRotatedOffX() + port.getPlacementNode().getPlacementX();
		double y = port.getRotatedOffY() + port.getPlacementNode().getPlacementY();
		return new Point2D.Double(x, y);
	}
}
