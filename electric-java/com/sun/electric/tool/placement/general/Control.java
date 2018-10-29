/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Control.java
 * Written by Steven M. Rubin
 *
 * Copyright (c) 2011, Static Free Software. All rights reserved.
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
package com.sun.electric.tool.placement.general;

import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.Library;
import com.sun.electric.database.id.CellUsage;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.placement.Placement;
import com.sun.electric.tool.placement.Placement.PlacementPreferences;
import com.sun.electric.tool.placement.PlacementAdapter;
import com.sun.electric.tool.placement.PlacementAdapter.PlacementExport;
import com.sun.electric.tool.placement.PlacementFrame;
import com.sun.electric.tool.placement.PlacementFrameElectric;
import com.sun.electric.tool.placement.simulatedAnnealing2.PlacementSimulatedAnnealing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Implementation of the overall floorplanning and placement process, using multiple algorithms.
 */
public class Control extends PlacementFrameElectric
{
    @Override
	public String getAlgorithmName() { return "General Placement"; }
    
	/**
	 * Method to do placement by whatever method is appropriate.
	 * @param placementNodes a list of all nodes that are to be placed.
	 * @param allNetworks a list of all networks that connect the nodes.
	 * @param cellName the name of the cell being placed.
	 * @param job the Job (for testing abort).
	 */
    @Override
	public void runPlacement(List<PlacementNode> placementNodes, List<PlacementNetwork> allNetworks,
		List<PlacementExport> exportsToPlace, String cellName, Job job)
	{
		// use Bottom-up Partition algorithm to decompose large circuits
		BottomUpPartition decomposeAlgorithm = PlacementAdapter.BUpa;
		decomposeAlgorithm.setEditingPreferences(ep);

		// use Bottom-up Placement algorithm to reassemble large circuits
		BottomUpPlace recomposeAlgorithm = PlacementAdapter.BUpl;
		recomposeAlgorithm.setEditingPreferences(ep);

		// use Force-Directed-Row/Column algorithm to place fixed-pitch cells (all same width or height)
    	PlacementFrame fixedPitchAlgorithm = PlacementAdapter.FD3;
		PlacementAdapter.FD3.makeStacksEven.setValue(Boolean.TRUE);

		// use Simulated-Annealing-2 algorithm to place non-fixed-pitch cells (with 30 second limit)
		PlacementSimulatedAnnealing nonFixedPitchAlgorithm = PlacementAdapter.SA2;
		Integer timeLimit = Integer.valueOf(30);
		PlacementPreferences ppIrregular = new PlacementPreferences(false);
		ppIrregular.placementAlgorithm = nonFixedPitchAlgorithm.getAlgorithmName();
		nonFixedPitchAlgorithm.maxRuntimeParam.setValue(timeLimit);
		ppIrregular.setParameter(nonFixedPitchAlgorithm.maxRuntimeParam, timeLimit);

		// check size of placement task
		if (placementNodes.size() > 100)
		{
			// large task: start by decomposing into smaller ones in a new library
			System.out.println("Large placement task: breaking it into smaller tasks...");
			Library lib = decomposeAlgorithm.doBottomUp(placementNodes, allNetworks, exportsToPlace);
			System.out.println("Placement decomposed into new library: " + lib.getName());
			System.out.println();

			// now place the smaller cells in the decomposed library
			Map<Cell,Cell> placedCells = new HashMap<Cell,Cell>();
			List<Cell> regularCells = new ArrayList<Cell>();
			List<Cell> irregularCells = new ArrayList<Cell>();
			Cell allClusters = null;
			for(Iterator<Cell> it = lib.getCells(); it.hasNext(); )
			{
				Cell cell = it.next();
				if (cell.getName().equals("ALLCLUSTERS")) { allClusters = cell;   continue; }
				Boolean useColumns = getColumnPlacement(cell);
				if (useColumns == null) irregularCells.add(cell); else
					regularCells.add(cell);
			}

			// use selected fixed-pitch placement algorithm
			PlacementPreferences ppRowCol = new PlacementPreferences(false);
			ppRowCol.placementAlgorithm = fixedPitchAlgorithm.getAlgorithmName();
			for(Cell cell : regularCells)
			{
				Cell newCell = Placement.placeCellNoJob(cell, ep, fixedPitchAlgorithm, ppRowCol, true, job);
				if (newCell != null) placedCells.put(cell, newCell);
			}

			// use Bottom-Up Placement on irregular-size placement tasks
			for(Cell cell : irregularCells)
			{
				Cell newCell = Placement.placeCellNoJob(cell, ep, nonFixedPitchAlgorithm, ppIrregular, true, job);
				if (newCell != null) placedCells.put(cell, newCell);
			}

			// now rebuild the "ALLCLUSTERS" cell
			if (allClusters != null)
			{
				for(Iterator<NodeInst> it = allClusters.getNodes(); it.hasNext(); )
				{
					NodeInst ni = it.next();
					if (!ni.isCellInstance()) continue;
					Cell np = (Cell)ni.getProto();
					Cell newCell = placedCells.get(np);
					if (newCell == null) continue;
					if (ni.replace(newCell, ep, false, false, false) == null)
						System.out.println("ERROR: Could not replace node " + ni.describe(false) + " in cell " +
							allClusters.describe(false) + " with cell " + newCell.describe(false));
				}

				// delete unplaced cells
				for(Cell cell : placedCells.keySet())
				{
			        Iterator<CellUsage> it = cell.getUsagesOf();
			        if (!it.hasNext()) cell.kill();
				}

				// now place the top-level cell
				setRedispCell(allClusters);
				PlacementPreferences ppToplevel = new PlacementPreferences(false);
				ppToplevel.placementAlgorithm = recomposeAlgorithm.getAlgorithmName();
				Cell newCell = Placement.placeCellNoJob(allClusters, ep, recomposeAlgorithm, ppToplevel, false, job);
				if (newCell != null) setRedispCell(newCell);
			}

			// set "failure" so that the original cell doesn't get rebuilt
			setFailure(true);
		} else
		{
			// cell is small: just do placement
			Boolean useColumns = RowCol.isColumnPlacement(placementNodes, null, null, true);
			if (useColumns == null)
			{
				// there are no common widths or heights: use Bottom-up placement
				System.out.println("General Placement algorithm chooses " + nonFixedPitchAlgorithm.getAlgorithmName() +
					" to place the cell");
				nonFixedPitchAlgorithm.runPlacement(placementNodes, allNetworks, exportsToPlace, cellName, job);
				return;	
			}

			// cell has fixed width/height nodes: use fixed pitch algorithm
			System.out.println("General Placement algorithm chooses " + fixedPitchAlgorithm.getAlgorithmName() +
				" to place the cell");
			fixedPitchAlgorithm.runPlacement(placementNodes, allNetworks, exportsToPlace, cellName, job);
		}
	}

	/**
	 * Method to determine whether a Cell uses row or column placement.
	 * @param cell the Cell to be placed.
	 * @return TRUE to do column placement (all nodes are fixed width);
	 * FALSE to do row placement (all nodes are fixed height);
	 * null if nodes are mixed size.
	 */
	public Boolean getColumnPlacement(Cell cell)
	{
		// make sure cells have the same girth
		Double commonWid = new Double(-1), commonHei = new Double(-1);
		boolean foundCells = false;
		for(Iterator<NodeInst> it = cell.getNodes(); it.hasNext(); )
		{
			NodeInst ni = it.next();
			if (!ni.isCellInstance()) continue;
			foundCells = true;
			if (commonWid != null)
			{
				if (commonWid.doubleValue() < 0) commonWid = new Double(ni.getXSize());
				if (commonWid.doubleValue() != ni.getXSize()) commonWid = null;
			}
			if (commonHei != null)
			{
				if (commonHei.doubleValue() < 0) commonHei = new Double(ni.getYSize());
				if (commonHei.doubleValue() != ni.getYSize()) commonHei = null;
			}
		}
		if (!foundCells) return null;
		if (commonWid == null && commonHei == null)
		{
			// there are no common widths or heights
			return null;	
		}

		Boolean useColumns = Boolean.FALSE;
		if (commonWid != null && commonHei == null)
		{
			useColumns = Boolean.TRUE;
		}
		return useColumns;
	}
}
