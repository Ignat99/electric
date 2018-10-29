	/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: LayerCoverageToolTest.java
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
import com.sun.electric.database.geometry.GeometryHandler;
import com.sun.electric.database.geometry.ObjectQTree;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.EDatabase;
import com.sun.electric.database.hierarchy.Library;
import com.sun.electric.database.id.CellId;
import com.sun.electric.database.network.Netlist;
import com.sun.electric.database.network.Network;
import com.sun.electric.database.prototype.NodeProto;
import com.sun.electric.database.text.TextUtils;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.technology.Layer;
import com.sun.electric.technology.TechPool;
import com.sun.electric.technology.Technology;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.JobException;
import com.sun.electric.tool.extract.LayerCoverageTool;
import com.sun.electric.tool.user.MessagesStream;
import com.sun.electric.tool.user.User;
import com.sun.electric.util.math.DBMath;
import com.sun.electric.util.math.GenMath;
import com.sun.electric.util.math.Orientation;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Class to test the layer coverage tool.
 */
public class LayerCoverageToolTest extends AbstractTest
{
	public LayerCoverageToolTest(String name)
	{
		super(name);
	}

	public static List<AbstractTest> getTests()
	{
		List<AbstractTest> list = new ArrayList<AbstractTest>();
		list.add(new LayerCoverageToolTest("Area"));
		list.add(new LayerCoverageToolTest("Network"));
		list.add(new LayerCoverageToolTest("Implant"));
		list.add(new LayerCoverageToolTest("QTree"));
		return list;
	}

	public static String getOutputDirectory()
	{
		return null;
	}

	/************************************* Area *********************************************************/

	public Boolean Area()
	{
		return Boolean.valueOf(basicAreaCoverageTest(null));
	}

	/**
	 * Basic test of area coverage. Function must be public due to regressions.
	 * @param logname
	 * @return true if test passes
	 */
	public static boolean basicAreaCoverageTest(String logname)
	{
		boolean[] errorCounts = new boolean[2];

		try
		{
			if (logname != null)
				MessagesStream.getMessagesStream().save(logname);
			String techName = "mocmos";
			String libName = "areaCoverage" + techName;
			EditingPreferences ep = new EditingPreferences(true, TechPool.getThreadTechPool());
			makeFakeCircuitryForCoverageCommand(libName, techName, false, ep);
			Library rootLib = Library.findLibrary(libName);
			Cell cell = rootLib.findNodeProto("higher{lay}");

			GeometryHandler.GHMode[] modes = {GeometryHandler.GHMode.ALGO_MERGE, GeometryHandler.GHMode.ALGO_SWEEP};
			for (int i = 0; i < modes.length; i++)
			{
				GeometryHandler.GHMode mode = modes[i];
				System.out.println("------ RUNNING " + mode + " MODE -------------");
				Map<Layer,Double> map = LayerCoverageTool.layerCoverageCommand(cell, mode, false, new LayerCoverageTool.LayerCoveragePreferences(true));
				errorCounts[i] = map == null;
				System.out.println("------ FINISHED " + mode + " MODE: " + (errorCounts[i] ? "FAILED" : "PASSED"));
			}
		} catch (Exception e)
		{
			System.out.println("exception: "+e);
			e.printStackTrace();
			return false;
		}
		return(!errorCounts[0] && !errorCounts[1]);
	}

	public static void makeFakeCircuitryForCoverageCommand(String libName, String tech, boolean asJob, EditingPreferences ep)
 	{
 		// test code to make and show something
		if (asJob)
		{
			new FakeCoverageCircuitry(libName, tech);
		} else
		{
			FakeCoverageCircuitry.doItInternal(libName, tech, new HashMap<CellId,BitSet>(), ep);
		}
 	}

	private static class FakeCoverageCircuitry extends Job
	{
		private String theTechnology;
		private String theLibrary;
		private Map<CellId,BitSet> nodesToExpand = new HashMap<CellId,BitSet>();

		protected FakeCoverageCircuitry(String libName, String tech)
		{
			super("Make fake circuitry for coverage tests", User.getUserTool(), Job.Type.CHANGE, null, null, Job.Priority.USER);
			theTechnology = tech;
			theLibrary = libName;
			startJob();
		}

		@Override
		public boolean doIt() throws JobException
		{
			doItInternal(theLibrary, theTechnology, nodesToExpand, getEditingPreferences());
			fieldVariableChanged("nodesToExpand");
			return true;
		}

		@Override
		public void terminateOK()
		{
			getDatabase().expandNodes(nodesToExpand);
		}

		private static void doItInternal(String libName, String technology, Map<CellId,BitSet> nodesToExpand, EditingPreferences ep)
		{
			EDatabase database = EDatabase.currentDatabase();

			// get information about the nodes
			Technology tech = Technology.findTechnology(technology);
			if (tech == null)
			{
				System.out.println("Technology not found in createCoverageTestCells");
				return;
			}

			NodeProto m1NodeProto = Cell.findNodeProto(technology+":Metal-1-Node");
			NodeProto m2NodeProto = Cell.findNodeProto(technology+":Metal-2-Node");
			NodeProto m3NodeProto = Cell.findNodeProto(technology+":Metal-3-Node");
			NodeProto m4NodeProto = Cell.findNodeProto(technology+":Metal-4-Node");

			// create the test library
			Library mainLib = Library.newInstance(libName, null);

			// create a layout cell in the library
			Cell m1Cell = Cell.makeInstance(ep, mainLib, technology+"Metal1Test{lay}");
			NodeInst.newInstance(m1NodeProto, ep, new Point2D.Double(0, 0), m1NodeProto.getDefWidth(ep), m1NodeProto.getDefHeight(ep), m1Cell);

			// Two metals
			Cell myCell = Cell.makeInstance(ep, mainLib, technology+"M1M2Test{lay}");
			NodeInst.newInstance(m1NodeProto, ep, new Point2D.Double(-m1NodeProto.getDefWidth(ep)/2, -m1NodeProto.getDefHeight(ep)/2),
				m1NodeProto.getDefWidth(ep), m1NodeProto.getDefHeight(ep), myCell);
			NodeInst.newInstance(m2NodeProto, ep, new Point2D.Double(-m2NodeProto.getDefWidth(ep)/2, m2NodeProto.getDefHeight(ep)/2),
				m2NodeProto.getDefWidth(ep), m2NodeProto.getDefHeight(ep), myCell);
			NodeInst.newInstance(m3NodeProto, ep, new Point2D.Double(m3NodeProto.getDefWidth(ep)/2, -m3NodeProto.getDefHeight(ep)/2),
				m3NodeProto.getDefWidth(ep), m3NodeProto.getDefHeight(ep), myCell);
			NodeInst.newInstance(m4NodeProto, ep, new Point2D.Double(m4NodeProto.getDefWidth(ep)/2, m4NodeProto.getDefHeight(ep)/2),
				m4NodeProto.getDefWidth(ep), m4NodeProto.getDefHeight(ep), myCell);

			// now up the hierarchy
			Cell higherCell = Cell.makeInstance(ep, mainLib, "higher{lay}");
			double myWidth = myCell.getDefWidth();
			double myHeight = myCell.getDefHeight();
			for (int iX = 0; iX < 2; iX++)
			{
				boolean flipX = iX != 0;
				for (int i = 0; i < 4; i++)
				{
					Orientation orient = Orientation.fromJava(i*900, flipX, false);
					NodeInst instanceNode = NodeInst.newInstance(myCell, ep, new Point2D.Double(i*myWidth, iX*myHeight), myWidth, myHeight, higherCell, orient, null);
					database.addToNodes(nodesToExpand, instanceNode);
				}
			}
			System.out.println("Created " + higherCell);
		}
	}

	/************************************* Network *********************************************************/

	public Boolean Network()
	{
		return Boolean.valueOf(basicNetworkCoverageTest(null));
	}

	/**
	 * Basic test of network coverage. Function must be public due to regressions.
	 * @param logname
	 * @return true if test passes.
	 */
	public static boolean basicNetworkCoverageTest(String logname)
	{
		boolean[] errorCounts = new boolean[2];
		double delta = DBMath.getEpsilon()* DBMath.getEpsilon();
		double wireLength = GenMath.toNearest(165.45876875, delta);

		try
		{
			if (logname != null)
				MessagesStream.getMessagesStream().save(logname);
			String techName = "mocmos";
			String libName = "networkCoverage"+techName;
			EditingPreferences ep = new EditingPreferences(true, TechPool.getThreadTechPool());
			MakeFakeCircuitry.makeFakeCircuitryCommand(libName, techName, Boolean.FALSE, ep);
			Library rootLib = Library.findLibrary(libName);
			Cell cell = rootLib.findNodeProto(techName+"test{lay}");
			double calculatedValue = 0;

			// Similar to ListGeomsAllNetworksJob
			Netlist netlist = cell.getNetlist();
			ArrayList<Network> networks = new ArrayList<Network>();
			for (Iterator<Network> it = netlist.getNetworks(); it.hasNext(); )
			{
				networks.add(it.next());
			}

			// sort list of networks by name
			Collections.sort(networks, new TextUtils.NetworksByName());

			GeometryHandler.GHMode[] modes = {GeometryHandler.GHMode.ALGO_MERGE, GeometryHandler.GHMode.ALGO_SWEEP};

			LayerCoverageTool.LayerCoveragePreferences lcp = new LayerCoverageTool.LayerCoveragePreferences(true);
			for (int i = 0; i < modes.length; i++)
			{
				GeometryHandler.GHMode mode = modes[i];
				System.out.println("------ RUNNING " + mode + " MODE -------------");
				for (Iterator<Network> it = networks.iterator(); it.hasNext(); )
				{
					Network net = it.next();
					Set<Network> nets = new HashSet<Network>();
					nets.add(net);
					LayerCoverageTool.GeometryOnNetwork geoms =
						LayerCoverageTool.listGeometryOnNetworks(cell, nets, false, mode, lcp);
					System.out.println("Network "+net+" has wire length "+geoms.getTotalWireLength());

					// Only 1 net gives non zero value
					if (geoms.getTotalWireLength() != 0)
						calculatedValue = GenMath.toNearest(geoms.getTotalWireLength(), delta);
				}
				System.out.println("Wire value " + calculatedValue + " (expected " + wireLength + ")");
				errorCounts[i] = !GenMath.doublesEqual(calculatedValue, wireLength);
				System.out.println("------ FINISHED " + mode + " MODE: " + (errorCounts[i] ? "FAILED" : "PASSED"));
			}
		} catch (Exception e)
		{
			System.out.println("exception: "+e);
			e.printStackTrace();
			return false;
		}
		return(!errorCounts[0] && !errorCounts[1]);
	}

	/************************************* Implant *********************************************************/

	public Boolean Implant()
	{
		return Boolean.valueOf(basicImplantCoverageTest(null));
	}

	/**
	 * Basic test of implant coverage. Function must be public due to regressions.
	 * @param logname
	 * @return true if test passes
	 */
	public static boolean basicImplantCoverageTest(String logname)
	{
		boolean[] errorCounts = new boolean[2];

		try
		{
			if (logname != null)
				MessagesStream.getMessagesStream().save(logname);
			String techName = "mocmos";
			String libName = "implantCoverage"+techName;
			EditingPreferences ep = new EditingPreferences(true, TechPool.getThreadTechPool());
			MakeFakeCircuitry.makeFakeCircuitryCommand(libName, techName, Boolean.FALSE, ep);
			Library rootLib = Library.findLibrary(libName);
			Cell cell = rootLib.findNodeProto(techName+"test{lay}");
			GeometryHandler.GHMode[] modes = {GeometryHandler.GHMode.ALGO_MERGE, GeometryHandler.GHMode.ALGO_SWEEP};
			int[] results = {2, 2};

			for (int i = 0; i < modes.length; i++)
			{
				GeometryHandler.GHMode mode = modes[i];
				System.out.println("------ RUNNING " + mode + " MODE -------------");
				List<Object> list = LayerCoverageTool.layerCoverageCommand(LayerCoverageTool.LCMode.IMPLANT, mode, cell, false, new LayerCoverageTool.LayerCoveragePreferences(true));
				errorCounts[i] = (list.size() != results[i]); // Only one implant is expected. We should get the size and location!!!
				System.out.println("------ FINISHED " + mode + " MODE: " + (errorCounts[i] ? "FAILED" : "PASSED"));
			}
		} catch (Exception e)
		{
			System.out.println("exception: "+e);
			e.printStackTrace();
			return false;
		}
		return !errorCounts[0] && !errorCounts[1];
	}

	/************************************* QTree *********************************************************/

	public Boolean QTree()
	{
		return Boolean.valueOf(basicQTreeTest(null));
	}

	/**
	 * Basic function to test QTree operations. Function must be public due to regressions.
	 * @param logname
	 * @return true if test passes
	 */
	public static boolean basicQTreeTest(String logname)
	{
		boolean pass = false;

		try
		{
			if (logname != null)
				MessagesStream.getMessagesStream().save(logname);

			Rectangle2D expanded = new Rectangle2D.Double(9, 7, 2, 6);
			ObjectQTree oqt = new ObjectQTree(expanded);

			Rectangle2D bounds1 = new Rectangle2D.Double(10, 8, 0, 0); // along middle X = 10, outside search box
			oqt.add(new Integer(1), bounds1);
			Rectangle2D bounds2 = new Rectangle2D.Double(10, 10, 0, 0); // along middle X = 10
			oqt.add(new Integer(2), bounds2);
			Rectangle2D bounds3 = new Rectangle2D.Double(10, 12, 0, 0); // along middle X = 10
			oqt.add(new Integer(3), bounds3);
			Rectangle2D bounds4 = new Rectangle2D.Double(10.5, 11, 0, 0); // in top-left quadrant
			oqt.add(new Integer(4), bounds4);
			Rectangle2D bounds5 = new Rectangle2D.Double(14, 10.6, 0, 0); // in bottom-left quadrant, outside search box
			oqt.add(new Integer(5), bounds5);

			Rectangle2D searchBounds = new Rectangle2D.Double(9.5, 10, 2, 4);
			Set set = oqt.find(searchBounds);
			int setSize = 0;
			if (set != null) setSize = set.size();
			pass = setSize == 3;

		} catch (Exception e)
		{
			System.out.println("exception: "+e);
			e.printStackTrace();
		}
		return pass;
	}
}
