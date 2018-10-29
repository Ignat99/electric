/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: PlacementGenetic.java
 * Written by Team 4: Benedikt Mueller, Richard Fallert
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
package com.sun.electric.tool.placement.genetic2;

import com.sun.electric.tool.Job;
import com.sun.electric.tool.placement.PlacementFrame;
import com.sun.electric.tool.placement.PlacementAdapter.PlacementExport;
import com.sun.electric.tool.placement.PlacementFrame.PlacementParameter;

import java.util.ArrayList;
import java.util.List;

/**
 * Combination of Genetic Algorithm and Simulated Annealing.
 * 
 * We have implemented a parallel genetic algorithm using a UnifiedPopulation
 * and multiple Evolver threads working on it.
 * 
 * We save only changes in our DeltaIndividuals, which saves CPU time and
 * memory.
 * 
 * Please note: We had to make some methods inside PlacementFrame public:
 * PlacementPort.getPlacementNode() PlacementPort.getRotatedOffX()
 * PlacementPort.getRotatedOffY() PlacementNode.getPlacementX()
 * PlacementNode.getPlacementY()
 * 
 * @see GeneticPlacer
 * @see UnifiedPopulation
 * @see DeltaIndividual
 * @see Evolver
 * @see SimulatedAnnealing
 */
public class PlacementGenetic extends PlacementFrame {
	GeneticPlacer placer = null;

	// maximum runtime of the placement algorithm in seconds
	public PlacementParameter maxRuntimeParam = new PlacementParameter("runtime", "Runtime (seconds):", 240);
	// number of threads
	public PlacementParameter maxThreadsParam = new PlacementParameter("threads", "Number of threads:", 4);
	// if false: NO system.out.println statements
	public boolean printDebugInformation = true;

	// information for the benchmark framework
	String teamName = "team 4";
	String studentName1 = "Benedikt Mueller";
	String studentName2 = "Richard Fallert";
	String algorithmType = "genetic";

	// public void setBenchmarkValues(int runtime, int threads, boolean debug) {
	// maxRuntime = runtime;
	// numThreads = threads;
	// printDebugInformation = debug;
	// }

	public String getAlgorithmName() {
		return "Genetic-2";
	}

	public UnifiedPopulation getPopulation() {
		if (placer != null)
			return placer.getPopulation();
		else
			return null;
	}

	public PlacementGenetic() {}

	/**
	 * Method to run the genetic algorithm to find a good placement.
	 * 
	 * @param nodesToPlace a list of all nodes that are to be placed.
	 * @param allNetworks a list of all networks that connect the nodes.
	 * @param cellName the name of the cell being placed.
	 * @param job the Job (for testing abort).
	 */
	public void runPlacement(List<PlacementNode> nodesToPlace, List<PlacementNetwork> allNetworks, List<PlacementExport> exportsToPlace,
			String cellName, Job job) {
		this.setParamterValues(this.maxThreadsParam.getIntValue(), this.maxRuntimeParam.getIntValue());

		placer = new GeneticPlacer(nodesToPlace, allNetworks, runtime, numOfThreads, printDebugInformation);
		placer.runPlacement(nodesToPlace, allNetworks);
	}
}
