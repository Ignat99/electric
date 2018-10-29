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

import java.awt.geom.Point2D;
import java.util.List;

import com.sun.electric.tool.placement.PlacementFrame.PlacementNetwork;
import com.sun.electric.tool.placement.PlacementFrame.PlacementNode;
import com.sun.electric.tool.placement.PlacementFrame.PlacementPort;
import com.sun.electric.tool.util.concurrent.patterns.PReduceJob.PReduceTask;
import com.sun.electric.tool.util.concurrent.utils.BlockedRange1D;

/**
 * Parallel Placement
 * 
 * Estimate wirelength using the bounding box metric
 */
public class BBMetricTask extends PReduceTask<Double, BlockedRange1D> {

	List<PlacementNode> nodesToPlace;
	List<PlacementNetwork> allNetworks;
	private Double sum = 0.0;

	public BBMetricTask(List<PlacementNode> nodesToPlace, List<PlacementNetwork> allNetworks) {
		this.nodesToPlace = nodesToPlace;
		this.allNetworks = allNetworks;
	}

	private double compute(PlacementNetwork net) {
		List<PlacementPort> portsOnNet = net.getPortsOnNet();

		double leftmost = Double.MAX_VALUE;
		double rightmost = -Double.MAX_VALUE;
		double uppermost = -Double.MAX_VALUE;
		double undermost = Double.MAX_VALUE;

		for (PlacementPort port : portsOnNet) {
			Point2D.Double position = this.getPortPosition(port);
			if (position.getX() < leftmost) {
				leftmost = position.getX();
			}
			if (position.getX() > rightmost) {
				rightmost = position.getX();
			}
			if (position.getY() > uppermost) {
				uppermost = position.getY();
			}
			if (position.getY() < undermost) {
				undermost = position.getY();
			}
		}

		return (rightmost - leftmost) + (uppermost - undermost);

	}

	private Point2D.Double getPortPosition(PlacementPort port) {
		double x = port.getRotatedOffX() + port.getPlacementNode().getPlacementX();
		double y = port.getRotatedOffY() + port.getPlacementNode().getPlacementY();

		return new Point2D.Double(x, y);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sun.electric.tool.util.concurrent.patterns.PReduceJob.PReduceTask
	 * #reduce
	 * (com.sun.electric.tool.util.concurrent.patterns.PReduceJob.PReduceTask)
	 */
	@Override
	public synchronized Double reduce(PReduceTask<Double, BlockedRange1D> other) {
		BBMetricTask bbOther = (BBMetricTask) other;

		if (!this.equals(other)) {
			this.sum += bbOther.sum;
		}

		return this.sum;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sun.electric.tool.util.concurrent.patterns.PForJob.PForTask#execute
	 * (com.sun.electric.tool.util.concurrent.patterns.PForJob.BlockedRange)
	 */
	@Override
	public void execute() {
		for (int i = range.start(); i < range.end(); i++) {
			sum = sum + this.compute(allNetworks.get(i));
		}
	}
}
