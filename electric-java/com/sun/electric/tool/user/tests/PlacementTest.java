	/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: PlacementTest.java
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

import com.sun.electric.database.EditingPreferences;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.Library;
import com.sun.electric.technology.TechPool;
import com.sun.electric.technology.Technology;
import com.sun.electric.tool.io.FileType;
import com.sun.electric.tool.io.input.LibraryFiles;
import com.sun.electric.tool.io.output.Output;
import com.sun.electric.tool.placement.Placement;
import com.sun.electric.tool.placement.PlacementAdapter;
import com.sun.electric.tool.placement.PlacementFrame;
import com.sun.electric.tool.user.MessagesStream;
import com.sun.electric.tool.user.User;
import com.sun.electric.util.TextUtils;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Class to test the Placement tool.
 */
public class PlacementTest extends AbstractTest
{
	public PlacementTest(String name)
	{
		super(name);
	}

	public static List<AbstractTest> getTests()
	{
		List<AbstractTest> list = new ArrayList<AbstractTest>();
		list.add(new PlacementTest("MinCut"));
		list.add(new PlacementTest("ForceDirected1"));
		list.add(new PlacementTest("ForceDirected2"));
		list.add(new PlacementTest("Genetic1"));
		list.add(new PlacementTest("Genetic2"));
		list.add(new PlacementTest("SimulatedAnnealing1"));
		list.add(new PlacementTest("SimulatedAnnealing2"));
		return list;
	}

	public static String getOutputDirectory()
	{
		String rootPath = User.getRegressionPath();
		if (rootPath == null) return null;
		return rootPath + "/tools/Placement/output/";
	}

	/************************************* MinCut *********************************************************/

	public Boolean MinCut()
	{
		return runParallelPlacementTestAlgorithm("Min-Cut", getRegressionPath(),
			"placementTests.jelib", "PlacementTest4", 0, 0, true);
	}

	/************************************* ForceDirected1 *********************************************************/

	public Boolean ForceDirected1()
	{
		return runParallelPlacementTestAlgorithm("Force-Directed-1", getRegressionPath(),
			"placementTests.jelib", "PlacementTest4", 2, 10, false);
	}

	/************************************* ForceDirected2 *********************************************************/

	public Boolean ForceDirected2()
	{
		return runParallelPlacementTestAlgorithm("Force-Directed-2", getRegressionPath(),
			"placementTests.jelib", "PlacementTest4", 2, 10, false);
	}

	/************************************* Genetic1 *********************************************************/

	public Boolean Genetic1()
	{
		return runParallelPlacementTestAlgorithm("Genetic-1", getRegressionPath(),
			"placementTests.jelib", "PlacementTest4", 2, 10, false);
	}

	/************************************* Genetic2 *********************************************************/

	public Boolean Genetic2()
	{
		return runParallelPlacementTestAlgorithm("Genetic-2", getRegressionPath(),
			"placementTests.jelib", "PlacementTest4", 2, 10, false);
	}

	/************************************* SimulatedAnnealing1 *********************************************************/

	public Boolean SimulatedAnnealing1()
	{
		return runParallelPlacementTestAlgorithm("Simulated-Annealing-1", getRegressionPath(),
			"placementTests.jelib", "PlacementTest4", 2, 10, false);
	}

	/************************************* SimulatedAnnealing2 *********************************************************/

	public Boolean SimulatedAnnealing2()
	{
		return runParallelPlacementTestAlgorithm("Simulated-Annealing-2", getRegressionPath(),
			"placementTests.jelib", "PlacementTest4", 2, 10, false);
	}

	/************************************* SUPPORT *********************************************************/

	/**
	 * Public function to call different placement algorithms applied to the
	 * same cell. Function must be public because it is used in the regression.
	 */
	public static Boolean runParallelPlacementTestAlgorithm(String testName, String rootPath, String libName,
		String cell, int numOfThreads, int runtime, boolean test)
	{
		PlacementFrame[] algorithms = PlacementAdapter.getPlacementAlgorithms();
		PlacementFrame algorithm = null;
		for (int i = 0; i < algorithms.length; i++)
		{
			if (algorithms[i].getAlgorithmName().equals(testName))
			{
				algorithm = algorithms[i];
				break;
			}
		}
		if (algorithm == null) return Boolean.FALSE;
		return doPlacement(rootPath, libName, cell, algorithm, numOfThreads, runtime, test);
	}

	private static Boolean doPlacement(String rootPath, String libName, String cellName,
		PlacementFrame algorithm, int numOfThreads, int runtime, boolean test)
	{
		String trueRootPath = (rootPath != null) ? (rootPath + "/tools/Placement/") : "";
		String outputDir = trueRootPath + "output/";
		ensureOutputDirectory(outputDir);
		String testName = algorithm.getAlgorithmName() + "-" + runtime + "sec-" + numOfThreads + "thr";
		MessagesStream.getMessagesStream().save(outputDir + testName + ".log");

		// initialize
		setFoundry(Technology.getMocmosTechnology());

		EditingPreferences ep = new EditingPreferences(true, TechPool.getThreadTechPool());
		Placement.PlacementPreferences prefs = new Placement.PlacementPreferences(true);
		prefs.placementAlgorithm = algorithm.getAlgorithmName();
		PlacementParameterSetter paramSetter = PlacementParameterSetter.algorithmMapping.get(algorithm.getAlgorithmName());
		if (paramSetter != null)
		{
			paramSetter.setParameter(prefs, numOfThreads, runtime, algorithm, true);
		}

		// read library with test setup
		Library rootLib = Library.findLibrary(libName);
		if (rootLib == null)
		{
			// read the JELIB
			URL fileURL = TextUtils.makeURLToFile(trueRootPath + "data/libs/" + libName);
			rootLib = LibraryFiles.readLibrary(ep, fileURL, libName, FileType.JELIB, true);
			if (rootLib == null)
			{
				System.out.println("Can't find library '" + libName + "'");
				return Boolean.FALSE;
			}
		}

		Cell cell = rootLib.findNodeProto(cellName + "{lay}");
		PlacementFrame pf = Placement.getCurrentPlacementAlgorithm(prefs);
		Cell newCell = Placement.placeCellNoJob(cell, ep, pf, prefs, false, null);
		Output.saveJelib(outputDir + testName + ".jelib", newCell.getLibrary());

		if (test)
		{
			return Boolean.valueOf(compareLibraryResults(trueRootPath, testName,
				newCell.getLibrary(), new char[] { 'H', 'C', 'F', 'R', 'T', 'O', '#' }));
		}
		return Boolean.TRUE;
	}
}
