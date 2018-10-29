/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: PlacementParameterSetter.java
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
package com.sun.electric.tool.user.tests;

import com.sun.electric.tool.placement.Placement;
import com.sun.electric.tool.placement.PlacementFrame;
import com.sun.electric.tool.placement.PlacementFrame.PlacementParameter;
import com.sun.electric.tool.placement.forceDirected1.PlacementForceDirectedTeam5;
import com.sun.electric.tool.placement.forceDirected2.PlacementForceDirectedStaged;
import com.sun.electric.tool.placement.genetic1.g1.GeneticPlacement;
import com.sun.electric.tool.placement.genetic2.PlacementGenetic;
import com.sun.electric.tool.placement.simulatedAnnealing1.SimulatedAnnealing;
import com.sun.electric.tool.placement.simulatedAnnealing2.PlacementSimulatedAnnealing;
import com.sun.electric.util.CollectionFactory;

import java.util.Map;

public abstract class PlacementParameterSetter
{
	public static Map<String, PlacementParameterSetter> algorithmMapping = CollectionFactory.createHashMap();

	static
	{
		algorithmMapping.put("Simulated-Annealing-1", new SimulatedAnnealing1Setter());
		algorithmMapping.put("Simulated-Annealing-2", new SimulatedAnnealing2Setter());
		algorithmMapping.put("Genetic-1", new Genetic1Setter());
		algorithmMapping.put("Genetic-2", new Genetic2Setter());
		algorithmMapping.put("Force-Directed-1", new ForceDirected1Setter());
		algorithmMapping.put("Force-Directed-2", new ForceDirected2Setter());
	}

	public abstract void setParameter(Placement.PlacementPreferences prefs, int numOfThreads, int runtime, PlacementFrame placement, boolean regression);

	public static class SimulatedAnnealing1Setter extends PlacementParameterSetter
	{
		@Override
		public void setParameter(Placement.PlacementPreferences prefs, int numOfThreads, int runtime, PlacementFrame placement, boolean regression)
		{
			SimulatedAnnealing tool = (SimulatedAnnealing) placement;
			makeTempValuesReal(prefs, tool.maxThreadsParam, numOfThreads, regression);
		}

	}

	public static class SimulatedAnnealing2Setter extends PlacementParameterSetter
	{
		@Override
		public void setParameter(Placement.PlacementPreferences prefs, int numOfThreads, int runtime, PlacementFrame placement, boolean regression)
		{
			PlacementSimulatedAnnealing tool = (PlacementSimulatedAnnealing) placement;
			makeTempValuesReal(prefs, tool.numThreadsParam, numOfThreads, regression);
			makeTempValuesReal(prefs, tool.maxRuntimeParam, runtime, regression);
		}
	}

	public static class Genetic1Setter extends PlacementParameterSetter
	{
		@Override
		public void setParameter(Placement.PlacementPreferences prefs, int numOfThreads, int runtime, PlacementFrame placement, boolean regression)
		{
			GeneticPlacement tool = (GeneticPlacement) placement;
			makeTempValuesReal(prefs, tool.maxThreadsParam, numOfThreads, regression);
			makeTempValuesReal(prefs, tool.maxRuntimeParam, runtime, regression);
		}
	}

	public static class Genetic2Setter extends PlacementParameterSetter
	{
		@Override
		public void setParameter(Placement.PlacementPreferences prefs, int numOfThreads, int runtime, PlacementFrame placement, boolean regression)
		{
			PlacementGenetic tool = (PlacementGenetic) placement;
			makeTempValuesReal(prefs, tool.maxThreadsParam, numOfThreads, regression);
			makeTempValuesReal(prefs, tool.maxRuntimeParam, runtime, regression);

		}
	}

	public static class ForceDirected1Setter extends PlacementParameterSetter
	{
		@Override
		public void setParameter(Placement.PlacementPreferences prefs, int numOfThreads, int runtime, PlacementFrame placement, boolean regression)
		{
			PlacementForceDirectedTeam5 tool = (PlacementForceDirectedTeam5) placement;
			makeTempValuesReal(prefs, tool.maxThreadsParam, numOfThreads, regression);
			makeTempValuesReal(prefs, tool.maxRuntimeParam, runtime, regression);
		}
	}

	public static class ForceDirected2Setter extends PlacementParameterSetter
	{
		@Override
		public void setParameter(Placement.PlacementPreferences prefs, int numOfThreads, int runtime, PlacementFrame placement, boolean regression)
		{
			PlacementForceDirectedStaged tool = (PlacementForceDirectedStaged) placement;
			makeTempValuesReal(prefs, tool.maxThreadsParam, numOfThreads, regression);
			makeTempValuesReal(prefs, tool.maxRuntimeParam, runtime, regression);
		}
	}

	private static void makeTempValuesReal(Placement.PlacementPreferences prefs, PlacementParameter param, int value, boolean regression)
	{
		prefs.setParameter(param, Integer.valueOf(value));
	}

}
