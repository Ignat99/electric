/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: PlacementRandom.java
 *
 * Copyright (c) 2009, Static Free Software. All rights reserved.
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
package com.sun.electric.tool.placement;

import com.sun.electric.tool.Job;
import com.sun.electric.tool.placement.PlacementAdapter.PlacementExport;

import java.util.List;

/**
 * Random Placement algorithm to arbitrarily assign locations in a square grid.
 */
public class PlacementRandom extends PlacementFrame
{
	private static final int SPACING = 5;

	/**
	 * Method to return the name of this placement algorithm.
	 * @return the name of this placement algorithm.
	 */
	public String getAlgorithmName() { return "Random"; }

	/**
	 * Method to do Random Placement.
	 * @param nodesToPlace a list of all nodes that are to be placed.
	 * @param allNetworks a list of all networks that connect the nodes.
	 * @param cellName the name of the cell being placed.
	 * @param job the Job (for testing abort).
	 */
	public void runPlacement(List<PlacementNode> nodesToPlace, List<PlacementNetwork> allNetworks,
		List<PlacementExport> exportsToPlace, String cellName, Job job)
	{
		int numRows = (int)Math.round(Math.sqrt(nodesToPlace.size()));
		double xPos = 0, yPos = 0;
		double maxHeight = 0;
		for(int i=0; i<nodesToPlace.size(); i++)
		{
			PlacementNode plNode = nodesToPlace.get(i);
			plNode.setPlacement(xPos, yPos);
			xPos += plNode.getWidth() + SPACING;
			maxHeight = Math.max(maxHeight, plNode.getHeight());
			if ((i%numRows) == numRows-1)
			{
				xPos = 0;
				yPos += maxHeight + SPACING;
				maxHeight = 0;
			}
		}
	}
}
