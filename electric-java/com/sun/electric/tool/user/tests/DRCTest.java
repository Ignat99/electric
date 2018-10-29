/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: DRCTest.java
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
import com.sun.electric.database.geometry.EPoint;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.Library;
import com.sun.electric.database.hierarchy.View;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.technology.Foundry;
import com.sun.electric.technology.PrimitiveNode;
import com.sun.electric.technology.SizeOffset;
import com.sun.electric.technology.TechPool;
import com.sun.electric.technology.Technology;
import com.sun.electric.tool.drc.DRC;
import com.sun.electric.tool.drc.Quick;
import com.sun.electric.tool.drc.Schematic;
import com.sun.electric.tool.generator.layout.LayoutLib;
import com.sun.electric.tool.user.ErrorLogger;
import com.sun.electric.tool.user.User;
import com.sun.electric.util.ElapseTimer;
import com.sun.electric.util.TextUtils;
import com.sun.electric.util.math.ECoord;

import java.awt.geom.Point2D;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Class to test the DRC tool.
 */
public class DRCTest extends AbstractTest
{
	public DRCTest(String name)
	{
		super(name);
	}

	public static List<AbstractTest> getTests()
	{
		List<AbstractTest> list = new ArrayList<AbstractTest>();
		list.add(new DRCTest("Primitive"));
		list.add(new DRCTest("Layout1"));
		list.add(new DRCTest("Layout2"));
		list.add(new DRCTest("Schematic1"));
		list.add(new DRCTest("Schematic2"));
		return list;
	}

	public static String getOutputDirectory()
	{
		String rootPath = User.getRegressionPath();
		if (rootPath == null) return null;
		return rootPath + "/tools/DRC/output/";
	}

	/************************************* Primitive *********************************************************/

	/**
	 * Method to test PrimitiveNodes against their DRC rules.
	 * @return true if no DRC errors were found
	 */
	public Boolean Primitive()
	{
		createMessageOutput();
		return primitiveTechTest();
	}

	/**
	 * Static version of primitiveTest using Electric's DRC tool. Keep it public for the regressions
	 * @return true if it passes all primitive tests.
	 */
	public static Boolean primitiveTechTest()
	{
		// by implementation of the tests, it is always a p-well process in this regression
		Boolean passed = Boolean.TRUE;
		if (!testDRCRules("mocmos", "MOSIS", 20, 20, 8)) passed = Boolean.FALSE;
		return passed;
	}

	/**
	 * Method to run DRC tests on Cell. Keep it public for regressions.
	 * @param techName
	 * @param foundryName
	 * @return true if no errors were found
	 */
	public static boolean testDRCRules(String techName, String foundryName, int ERROR_DEFAULT, int ERROR_EXHAUSTIVE, int ERROR_CELL)
	{
		// Make sure the foundry/technology are correct
		Technology tech = setFoundry(Technology.findTechnology(techName), foundryName);
		EditingPreferences ep = new EditingPreferences(true, TechPool.getThreadTechPool());

		Cell gallery = builTechLibrary(tech, foundryName, ep);
		return basicDRCLayoutTestInternal(gallery, ERROR_DEFAULT, 0, ERROR_EXHAUSTIVE, 0, ERROR_CELL, 0,
			false, DRC.DRCCheckMinArea.AREA_LOCAL);
	}

	private static Cell builTechLibrary(Technology tech, String foundryName, EditingPreferences ep)
	{
		Library lib = Library.newInstance(tech.getTechName()+"-"+foundryName, null);

		List<Cell> cellList = new ArrayList<Cell>();

		// Setting the technology so the correct nodes will be tested
		Technology theTech = setFoundry(tech, foundryName);

		for(Iterator<PrimitiveNode> it = theTech.getNodes(); it.hasNext(); )
		{
			PrimitiveNode pnp = it.next();
			if (pnp.isNotUsed()) continue;
			boolean first = true;

			// create the node layers
			double xS = pnp.getDefWidth(ep) * 2;
			double yS = pnp.getDefHeight(ep) * 2;
			if (xS < 3) xS = 3;
			if (yS < 3) yS = 3;
			double nodeXPos = -xS*2;
			Point2D [] pos = new Point2D[4];
			pos[0] = new Point2D.Double(nodeXPos - xS, -5 + yS);
			pos[1] = new Point2D.Double(nodeXPos + xS, -5 + yS);
			pos[2] = new Point2D.Double(nodeXPos - xS, -5 - yS);
			pos[3] = new Point2D.Double(nodeXPos + xS, -5 - yS);

			SizeOffset so = pnp.getProtoSizeOffset();
			xS = pnp.getDefWidth(ep) - so.getLowXOffset() - so.getHighXOffset();
			yS = pnp.getDefHeight(ep) - so.getLowYOffset() - so.getHighYOffset();
			double [] xsc = new double[4];
			double [] ysc = new double[4];
			xsc[0] = xS*1;   ysc[0] = yS*1;
			xsc[1] = xS*2;   ysc[1] = yS*1;
			xsc[2] = xS*1;   ysc[2] = yS*2;
			xsc[3] = xS*2;   ysc[3] = yS*2;

			// for multi-cut contacts, make large size be just right for 2 cuts
			if (pnp.isMulticut())
			{
				EPoint min2size = pnp.getMulticut2Size();
				double min2X = min2size.getLambdaX();
				double min2Y = min2size.getLambdaY();
				xsc[1] = min2X;
				xsc[3] = min2X;
				ysc[2] = min2Y;
				ysc[3] = min2Y;
			}
			Cell nNp = null;
			for(int e=0; e<4; e++)
			{
				// do not create node if main example had no polygons
				if (e != 0 && first) continue;

				// square nodes have only two examples
				if (pnp.isSquare() && (e == 1 || e == 2)) continue;
				double newXSize = xsc[e] + so.getLowXOffset() + so.getHighXOffset();
				double newYSize = ysc[e] + so.getLowYOffset() + so.getHighYOffset();

				// create the node cell on the first valid layer
				if (first)
				{
					first = false;
					String fName = "node-" + pnp.getName() + "{lay}";

					// make sure the node doesn't exist
					if (lib.findNodeProto(fName) != null)
					{
						System.out.println("Warning: multiple nodes named '" + fName + "'");
						break;
					}

					nNp = Cell.makeInstance(ep, lib, fName);
					cellList.add(nNp);
					if (nNp == null) return null;
				}

				NodeInst.makeInstance(pnp, ep, EPoint.snap(pos[e]), newXSize, newYSize, nNp);
			}
		}
		Cell gallery = Cell.newInstance(lib, "gallery{lay}");
		NodeInst ni = null;
		double y = 0;
		for (Cell c : cellList)
		{
			if (ni != null) // not the first one
			{
				y += ni.getBounds().getHeight() * 1.5;
			}
			y += c.getBounds().getHeight();
			ni = LayoutLib.newNodeInst(c, ep, c.getBounds().getWidth(), y, 0, 0, 0, gallery);
		}

		return gallery;
	}

	/************************************* Layout1&2 *********************************************************/

	/**
	 * Method to run the simplest DRC test on an existing design
	 * @return true if it passes the test.
	 */
	public Boolean Layout1()
	{
		createMessageOutput();
		if (basicDRCTest(Technology.getMocmosTechnology(), Foundry.Type.MOSIS, getRegressionPath()+"/data/qThree",
			"qThreeTop", "qThreeTop.jelib",
			4739, 0,	// DEFAULT
			5027, 0,	// EXHAUSTIVE
			810, 0,		// CELL
			false, DRC.DRCCheckMinArea.AREA_LOCAL)) return Boolean.TRUE;
		return Boolean.FALSE;
	}

	/**
	 * Method to run the simplest DRC test on an existing design
	 * @return true if it passes the test.
	 */
	public Boolean Layout2()
	{
		createMessageOutput();
		if (basicDRCTest(Technology.getMocmosTechnology(), Foundry.Type.MOSIS, getRegressionPath()+"/data/muddChip",
			"chip", "MIPS.jelib",
			532012, 4444,	// DEFAULT
			759286, 4444,	// EXHAUSTIVE
			189, 4444,		// CELL
			false, DRC.DRCCheckMinArea.AREA_LOCAL)) return Boolean.TRUE;
		return Boolean.FALSE;
	}

	/**
	 * Method to test DRC on Layout examples. The method is public so it can be used from the script applied
	 * to big examples.
	 * @return true if test was successful
	 */
	public static boolean basicDRCTest(Technology tech, Foundry.Type foundry, String regressiondata,
		String testCell, String testlib,
		int ERROR_DEFAULT, int WARN_DEFAULT, int ERROR_EXHAUSTIVE, int WARN_EXHAUSTIVE,
		int ERROR_CELL, int WARN_CELL, boolean checkArea, DRC.DRCCheckMinArea areaAlgo)
	{
		Cell lay = getCellToDRC(tech, foundry, regressiondata, testCell, testlib);
		return basicDRCLayoutTestInternal(lay,
			ERROR_DEFAULT, WARN_DEFAULT,
			ERROR_EXHAUSTIVE, WARN_EXHAUSTIVE,
			ERROR_CELL, WARN_CELL, checkArea, areaAlgo);
	}

	private static Cell getCellToDRC(Technology tech, Foundry.Type foundry, String regressiondata,
		String testcell, String testlib)
	{
		// Make sure the foundry/technology are correct
		setFoundry(tech, foundry.getName());
		EditingPreferences ep = new EditingPreferences(true, TechPool.getThreadTechPool());
		Library rootLib = LayoutLib.openLibForRead(regressiondata+"/"+testlib, ep, true);
		View view = View.LAYOUT;
		Cell lay = rootLib.findNodeProto(testcell+view.getAbbreviationExtension());
		return lay;
	}

	/************************************* Schematic1&2 *********************************************************/

	/**
	 * Method to run the DRC test on an existing design
	 * @return true if it passes the test.
	 */
	public Boolean Schematic1()
	{
		createMessageOutput();
		if (basicDRCSchematicTest(getRegressionPath()+"/data/qThree", "qThreeTop", "qThreeTop.jelib", 1050, 2)) return Boolean.TRUE;
		return Boolean.FALSE;
	}

	/**
	 * Method to run the DRC test on an existing design
	 * @return true if it passes the test.
	 */
	public Boolean Schematic2()
	{
		createMessageOutput();
		if (basicDRCSchematicTest(getRegressionPath()+"/data/muddChip", "chip", "MIPS.jelib", 195, 0)) return Boolean.TRUE;
		return Boolean.FALSE;
	}

	/**
	 * Method to test DRC schematics. Used by regressions so don't remove
	 * @return true if number of errors and warnings match expected values
	 */
	public static boolean basicDRCSchematicTest(String regressiondata, String testcell, String testlib,
		int ERROR_DEFAULT, int WARN_DEFAULT)
	{
		EditingPreferences ep = new EditingPreferences(true, TechPool.getThreadTechPool());
		LayoutLib.openLibForRead(regressiondata + "/" + testlib, ep, true);
		Library.repairAllLibraries(ep);
		URL fileURL = TextUtils.makeURLToFile(testlib);
		String libName = TextUtils.getFileNameWithoutExtension(fileURL);
		Library rootLib = Library.findLibrary(libName);
		View view = View.SCHEMATIC;
		Cell schematic = rootLib.findNodeProto(testcell+view.getAbbreviationExtension());
		return basicDRCSchematicTestInternal(schematic, ERROR_DEFAULT, WARN_DEFAULT);
	}

	private static boolean basicDRCSchematicTestInternal(Cell schematic, int ERROR_DEFAULT, int WARN_DEFAULT)
	{
		int errorCounts, warnCounts;

		try {
			if (schematic == null) return false; // error reading the cell
			DRC.resetDRCDates(false);
			ElapseTimer timer = ElapseTimer.createInstance();
			timer.start();
			ErrorLogger errorLogger = DRC.getDRCErrorLogger(false, null);
			errorLogger.disablePopups();
			Schematic.doCheck(errorLogger, schematic, null, new DRC.DRCPreferences(true));
			errorLogger.termLogging(true);
			errorCounts  = errorLogger.getNumErrors();
			warnCounts  = errorLogger.getNumWarnings();
			timer.end();
			System.out.println(errorCounts + " errors and " + warnCounts + " warnings found (took " + timer.toString() + ")");
			System.out.println();
		} catch (Exception e) {
			System.out.println("exception: "+e);
			e.printStackTrace();
			return false;
		}

		boolean good = (errorCounts == ERROR_DEFAULT && warnCounts == WARN_DEFAULT);
		if (!good) System.out.println("ERROR: Expected " +ERROR_DEFAULT + " errors and " + WARN_DEFAULT + " warnings");
		return good;
	}

	/************************************* SUPPORT *********************************************/

	public static boolean basicDRCLayoutTestInternal(Cell lay,
		int ERROR_DEFAULT, int WARN_DEFAULT,
		int ERROR_EXHAUSTIVE, int WARN_EXHAUSTIVE,
		int ERROR_CELL, int WARN_CELL,
		boolean checkArea, DRC.DRCCheckMinArea areaAlgo)
	{
		boolean globalPassed = true;

		try {

			if (lay == null) return false; // error reading the cell

			DRC.DRCPreferences dp = new DRC.DRCPreferences(true);

			// No resolution check for now
			dp.setResolution(lay.getTechnology(), ECoord.ZERO);

			// No area nor surround checking Since v8.03j, they are not variables stored in library.
			dp.ignoreAreaCheck = !checkArea;
			dp.ignoreExtensionRuleChecking = true;
			dp.storeDatesInMemory = true;
			dp.minAreaAlgoOption = areaAlgo;

			int[] expectedErrors = {ERROR_DEFAULT, ERROR_CELL, ERROR_EXHAUSTIVE};
			int[] expectedWarns = {WARN_DEFAULT, WARN_CELL, WARN_EXHAUSTIVE};

			for (DRC.DRCCheckMode mode : DRC.DRCCheckMode.values())
			{
				// if expected numbers are -1, then skip the test
				if (expectedErrors[mode.mode()] == -1) continue;

				System.out.println("------RUNNING " + mode + " MODE -------------");
				dp.errorType = mode;
				DRC.resetDRCDates(false);
				ElapseTimer timer = ElapseTimer.createInstance();
				timer.start();
				ErrorLogger errorLogger = Quick.checkDesignRules(dp, lay, null, null);
				errorLogger.disablePopups();
				errorLogger.termLogging(true);
				int errorCounts = errorLogger.getNumErrors();
				int warnCounts = errorLogger.getNumWarnings();
				timer.end();
				System.out.println(errorCounts + " errors and " + warnCounts + " warnings found (took " + timer.toString() + ")");

				// check if passes
				boolean passed = (errorCounts == expectedErrors[mode.mode()]) &&
					(warnCounts == expectedWarns[mode.mode()]);
				if (!passed)
				{
				   	System.out.println("ERROR: Expected " + expectedErrors[mode.mode()] + " errors and " +  expectedWarns[mode.mode()] + " warnings");
					globalPassed = false;
				}
				System.out.println();
			}
		} catch (Exception e)
		{
			System.out.println("exception: " + e);
			e.printStackTrace();
			return false;
		}
		return globalPassed;
	}
}
