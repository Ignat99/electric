	/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: RoutingTest.java
 *
 * Copyright (c) 2006, Static Free Software. All rights reserved.
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
import com.sun.electric.database.topology.ArcInst;
import com.sun.electric.technology.TechPool;
import com.sun.electric.technology.Technology;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.io.FileType;
import com.sun.electric.tool.io.input.LibraryFiles;
import com.sun.electric.tool.io.output.Output;
import com.sun.electric.tool.routing.AutoStitch;
import com.sun.electric.tool.routing.AutoStitch.AutoOptions;
import com.sun.electric.tool.routing.Maze;
import com.sun.electric.tool.routing.MimicStitch;
import com.sun.electric.tool.routing.MimicStitch.MimicOptions;
import com.sun.electric.tool.routing.River;
import com.sun.electric.tool.routing.Routing;
import com.sun.electric.tool.routing.Routing.SoGContactsStrategy;
import com.sun.electric.tool.routing.SeaOfGates;
import com.sun.electric.tool.routing.metrics.WireQualityMetric;
import com.sun.electric.tool.routing.seaOfGates.SeaOfGatesEngine;
import com.sun.electric.tool.routing.seaOfGates.SeaOfGatesEngineFactory;
import com.sun.electric.tool.routing.seaOfGates.SeaOfGatesEngineFactory.SeaOfGatesEngineType;
import com.sun.electric.tool.routing.seaOfGates.SeaOfGatesHandlers;
import com.sun.electric.tool.user.MessagesStream;
import com.sun.electric.tool.user.User;
import com.sun.electric.util.TextUtils;

import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Class to test the routing tool.
 */
public class RoutingTest extends AbstractTest
{
	public RoutingTest(String name)
	{
		super(name);
	}

	public static List<AbstractTest> getTests()
	{
		List<AbstractTest> list = new ArrayList<AbstractTest>();
		list.add(new RoutingTest("Auto"));
		list.add(new RoutingTest("Maze1"));
		list.add(new RoutingTest("Maze2"));
		list.add(new RoutingTest("Mimic"));
		list.add(new RoutingTest("River"));
		list.add(new RoutingTest("CopyTopology1"));
		list.add(new RoutingTest("CopyTopology2"));
		list.add(new RoutingTest("CopyTopology3"));
		list.add(new RoutingTest("SeaOfGates1"));
		list.add(new RoutingTest("SeaOfGates2"));
		list.add(new RoutingTest("SeaOfGates3"));
		return list;
	}

	public static String getOutputDirectory()
	{
		String rootPath = User.getRegressionPath();
		if (rootPath == null) return null;
		return rootPath + "/tools/Routing/output/";
	}

	public static boolean runRoutingTest(String testName)
	{
		RoutingTest t = new RoutingTest(testName);
		Class c = t.getClass();
		boolean good = false;
		try
		{
			Method method = c.getDeclaredMethod(testName, new Class[]{});
			Object obj = method.invoke(t, new Object[]{});
			if (obj instanceof Boolean)
				good = ((Boolean)obj).booleanValue();
		} catch (Exception e)
		{
			e.printStackTrace();
		}
		return good;
	}

	/**
	 * Run Auto-Stitching test 1.
	 * @return true if the test is successful.
	 */
	public Boolean Auto()
	{
        String testParameter = createMessageOutput();
        EditingPreferences ep = new EditingPreferences(true, TechPool.getThreadTechPool()).withFatWires(false);

		// initialize
		setFoundry(Technology.getMocmosTechnology());

		// read library with test setup
		URL fileURL = TextUtils.makeURLToFile(dataDir(getRegressionPath(), testParameter) + "routingTests.jelib");
		String libName = "routingTests";
		Library rootLib = Library.findLibrary(libName);
		if (rootLib == null) rootLib = LibraryFiles.readLibrary(ep, fileURL, libName, FileType.JELIB, true);
		String cellName = "AutoRoutingTest1";
		Cell lay = rootLib.findNodeProto(cellName + "{lay}");
		AutoOptions prefs = new AutoOptions(true);
		prefs.createExports = false;

		// do auto-routing
		AutoStitch.runAutoStitch(lay, null, null, null, null, null, true, false, ep, prefs, false, null);
		return Boolean.valueOf(compareCellResults(lay, getResultName()));
	}

	/**
	 * Run Maze-routing test 1.
	 * @return true if the test is successful.
	 */
	public Boolean Maze1()
	{
        String testParameter = createMessageOutput();
        EditingPreferences ep = new EditingPreferences(true, TechPool.getThreadTechPool());

		// initialize
		setFoundry(Technology.getMocmosTechnology());

		// read library with test setup
		URL fileURL = TextUtils.makeURLToFile(dataDir(getRegressionPath(), testParameter) + "routingTests.jelib");
		String libName = "routingTests";
		Library rootLib = Library.findLibrary(libName);
		if (rootLib == null) rootLib = LibraryFiles.readLibrary(ep, fileURL, libName, FileType.JELIB, true);
		String cellName = "MazeRoutingTest1";
		Cell lay = rootLib.findNodeProto(cellName + "{sch}");

		// do maze-routing
		Maze router = new Maze(ep);
		router.routeSelected(lay, new ArrayList<ArcInst>());
		return Boolean.valueOf(compareCellResults(lay, getResultName()));
	}

	/**
	 * Run Maze-routing test 2.
	 * @return true if the test is successful.
	 */
	public Boolean Maze2()
	{
        String testParameter = createMessageOutput();
        EditingPreferences ep = new EditingPreferences(true, TechPool.getThreadTechPool());

		// initialize
		setFoundry(Technology.getMocmosTechnology());

		// read library with test setup
		URL fileURL = TextUtils.makeURLToFile(dataDir(getRegressionPath(), testParameter) + "routingTests.jelib");
		String libName = "routingTests";
		Library rootLib = Library.findLibrary(libName);
		if (rootLib == null) rootLib = LibraryFiles.readLibrary(ep, fileURL, libName, FileType.JELIB, true);
		String cellName = "MazeRoutingTest2";
		Cell lay = rootLib.findNodeProto(cellName + "{lay}");

		// do maze-routing
		Maze router = new Maze(ep);
		router.routeSelected(lay, new ArrayList<ArcInst>());
		return Boolean.valueOf(compareCellResults(lay, getResultName()));
	}

	/**
	 * Run Mimic-Stitching test 1.
	 * @return true if the test is successful.
	 */
	public Boolean Mimic()
	{
        String testParameter = createMessageOutput();

		// initialize
		setFoundry(Technology.getMocmosTechnology());
        EditingPreferences ep = new EditingPreferences(true, TechPool.getThreadTechPool()).withFatWires(false);

		// read library with test setup
		URL fileURL = TextUtils.makeURLToFile(dataDir(getRegressionPath(), testParameter) + "routingTests.jelib");
		String libName = "routingTests";
		Library rootLib = Library.findLibrary(libName);
		if (rootLib == null) rootLib = LibraryFiles.readLibrary(ep, fileURL, libName, FileType.JELIB, true);
		String cellName = "MimicStitchTest1";
		Cell cell = rootLib.findNodeProto(cellName + "{lay}");
		ArcInst ai = cell.findArc("A");
		boolean good = true;

		// first test (strict mimicking, should connect 3 arcs)
		MimicOptions prefs = new MimicOptions();
		prefs.mimicInteractive = false;
		prefs.matchPorts = true;
		prefs.matchPortWidth = true;
		prefs.matchArcCount = true;
		prefs.matchNodeType = true;
		prefs.matchNodeSize = true;
		prefs.noOtherArcsThisDir = false;
		prefs.notAlreadyConnected = true;
		MimicStitch.mimicOneArc(ai, 0, ai, 1, ai.getLambdaBaseWidth(), ai.getProto(), 0, 0, true,
			Job.Type.CHANGE, ep, prefs, null);
		good = good && compareCellResults(cell, getFunctionName() + "aResult");

		// second test (allow node type variation, should connect 1 more arc)
		prefs.mimicInteractive = false;
		prefs.matchPorts = true;
		prefs.matchPortWidth = true;
		prefs.matchArcCount = true;
		prefs.matchNodeType = false;
		prefs.matchNodeSize = true;
		prefs.noOtherArcsThisDir = false;
		prefs.notAlreadyConnected = true;
		MimicStitch.mimicOneArc(ai, 0, ai, 1, ai.getLambdaBaseWidth(), ai.getProto(), 0, 0, true,
			Job.Type.CHANGE, ep, prefs, null);
		good = good && compareCellResults(cell, getFunctionName() + "bResult");

		// third test (allow node size variation, should connect 1 more arc)
		prefs.mimicInteractive = false;
		prefs.matchPorts = true;
		prefs.matchPortWidth = true;
		prefs.matchArcCount = true;
		prefs.matchNodeType = true;
		prefs.matchNodeSize = false;
		prefs.noOtherArcsThisDir = false;
		prefs.notAlreadyConnected = true;
		MimicStitch.mimicOneArc(ai, 0, ai, 1, ai.getLambdaBaseWidth(), ai.getProto(), 0, 0, true,
			Job.Type.CHANGE, ep, prefs, null);
		good = good && compareCellResults(cell, getFunctionName() + "cResult");

		return Boolean.valueOf(good);
	}

	/**
	 * Run River-Routing test 1.
	 * @return true if the test is successful.
	 */
	public Boolean River()
	{
        String testParameter = createMessageOutput();
        EditingPreferences ep = new EditingPreferences(true, TechPool.getThreadTechPool());

		// initialize
		setFoundry(Technology.getMocmosTechnology());

		// read library with test setup
		URL fileURL = TextUtils.makeURLToFile(dataDir(getRegressionPath(), testParameter) + "routingTests.jelib");
		String libName = "routingTests";
		Library rootLib = Library.findLibrary(libName);
		if (rootLib == null) rootLib = LibraryFiles.readLibrary(ep, fileURL, libName, FileType.JELIB, true);
		String cellName = "RiverRoutingTest1";
		Cell lay = rootLib.findNodeProto(cellName + "{lay}");

		// do river-routing
		River router = new River(ep);
		List<ArcInst> allArcs = new ArrayList<ArcInst>();
		for(Iterator<ArcInst> it = lay.getArcs(); it.hasNext(); ) allArcs.add(it.next());
		router.river(lay, allArcs);
		return Boolean.valueOf(compareCellResults(lay, getResultName()));
	}

	/**
	 * Run Copy-Routing-Topology test 1.
	 * @return true if the test is successful.
	 */
	public Boolean CopyTopology1()
	{
		return CopyTopology("CopyTopologyTest1From{lay}", "CopyTopologyTest1To");
	}

	/**
	 * Run Copy-Routing-Topology test 2.
	 * @return true if the test is successful.
	 */
	public Boolean CopyTopology2()
	{
		return CopyTopology("CopyTopologyTest2From{sch}", "CopyTopologyTest2To");
	}

	/**
	 * Run Copy-Routing-Topology test 3.
	 * @return true if the test is successful.
	 */
	public Boolean CopyTopology3()
	{
		return CopyTopology("CopyTopologyTest3From{sch}", "CopyTopologyTest3To");
	}

	private Boolean CopyTopology(String fromCellName, String cellName)
	{
        String testParameter = createMessageOutput();
        EditingPreferences ep = new EditingPreferences(true, TechPool.getThreadTechPool());

		// initialize
		setFoundry(Technology.getMocmosTechnology());

		// read library with test setup
		URL fileURL = TextUtils.makeURLToFile(dataDir(getRegressionPath(), testParameter) + "routingTests.jelib");
		String libName = "routingTests";
		Library rootLib = Library.findLibrary(libName);
		if (rootLib == null) rootLib = LibraryFiles.readLibrary(ep, fileURL, libName, FileType.JELIB, true);
		Cell fromCell = rootLib.findNodeProto(fromCellName);
		Cell toCell = rootLib.findNodeProto(cellName + "{lay}");

		// do topology copying
		Routing.copyTopology(fromCell, toCell, ep);
		return Boolean.valueOf(compareCellResults(toCell, getResultName()));
	}

	/**
	 * Run Sea-of-Gates Routing test 1.
	 * @return true if the test is successful.
	 */
	public Boolean SeaOfGates1()
	{
		return SeaOfGates("SOGbug1334", "oneInput14");
	}

	/**
	 * Run Sea-of-Gates Routing test 2.
	 * @return true if the test is successful.
	 */
	public Boolean SeaOfGates2()
	{
		return SeaOfGates("SOGParallelRoute", "infinityC");
	}

	/**
	 * Run Sea-of-Gates Routing test 3.
	 * @return true if the test is successful.
	 */
	public Boolean SeaOfGates3()
	{
		return SeaOfGates("SOGPadFrame", "chip");
	}

	private Boolean SeaOfGates(String libName, String cellName)
	{
        String testParameter = createMessageOutput();
        String pathLib = dataDir(getRegressionPath(), testParameter) + libName + ".jelib";
        EditingPreferences ep = new EditingPreferences(true, TechPool.getThreadTechPool());
        SeaOfGates.SeaOfGatesOptions prefs = prepareSeaOfGatesOptions(false, false, 1);
        prefs.disableAdvancedSpineRouting = true;
        Cell toCell = SeaOfGates(pathLib, libName, cellName, ep, prefs);
        return compareCellResults(toCell, getResultName());
	}

	private static SeaOfGates.SeaOfGatesOptions prepareSeaOfGatesOptions(boolean useParallelFromToRoutes,
			boolean useParallelRoutes, int userNumberOfThreads)
	{
		// initialize
		setFoundry(Technology.getMocmosTechnology());
		SeaOfGates.SeaOfGatesOptions prefs = new SeaOfGates.SeaOfGatesOptions();
		prefs.useParallelFromToRoutes = useParallelFromToRoutes;
		prefs.useParallelRoutes = useParallelRoutes;
		if (useParallelRoutes && userNumberOfThreads > 1) prefs.forcedNumberOfThreads = userNumberOfThreads; 
		return prefs;
	}

    private static Cell SeaOfGates(String pathLib, String libName, String cellName, EditingPreferences ep, SeaOfGates.SeaOfGatesOptions prefs)
	{
		// read library with test setup
		URL fileURL = TextUtils.makeURLToFile(pathLib);
		Library rootLib = Library.findLibrary(libName);
		if (rootLib == null) rootLib = LibraryFiles.readLibrary(ep, fileURL, libName, FileType.JELIB, true);
		Cell toCell = rootLib.findNodeProto(cellName + "{lay}");
        if (toCell == null) return null;

		// do topology copying
		SeaOfGatesEngine router = SeaOfGatesEngineFactory.createSeaOfGatesEngine(SeaOfGatesEngineType.defaultVersion);
		router.setPrefs(prefs);
        router.routeIt(SeaOfGatesHandlers.getDefault(toCell, null, SoGContactsStrategy.SOGCONTACTSATTOPLEVEL, Job.getRunningJob(), ep), toCell, false);
        return toCell;
	}

    // Used in the JoinedPlacementRouting
    public static Boolean runSeaOfGates(String rootPath, String libName, String cellName, boolean useParallelFromToRoutes,
                                       	int userNumberOfThreads)
    {
    	String outputDir = "output/";
    	ensureOutputDirectory(outputDir);
        String rootName = outputDir + libName + "-useFromTo=" + useParallelFromToRoutes + "-userNumberOfThreads=" + userNumberOfThreads;
		MessagesStream.getMessagesStream().save(rootName + ".log");
        String libPath = rootPath + libName;
        EditingPreferences ep = new EditingPreferences(true, TechPool.getThreadTechPool());
        SeaOfGates.SeaOfGatesOptions prefs = prepareSeaOfGatesOptions(useParallelFromToRoutes, true, userNumberOfThreads);
        Cell toCell = SeaOfGates(libPath, libName, cellName, ep, prefs);
        if (toCell == null) return Boolean.FALSE;
        Output.saveJelib(rootName + ".jelib", toCell.getLibrary());

        // Calculating metrics
        new WireQualityMetric(rootName, prefs.theTimer).calculate(toCell);

        return Boolean.TRUE;
    }
}
