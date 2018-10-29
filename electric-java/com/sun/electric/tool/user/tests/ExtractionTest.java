/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: ExtractionTest.java
 *
 * Copyright (c) 2012, Static Free Software. All rights reserved.
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
import com.sun.electric.tool.extract.Connectivity;
import com.sun.electric.tool.io.FileType;
import com.sun.electric.tool.io.input.LibraryFiles;
import com.sun.electric.tool.user.ErrorLogger;
import com.sun.electric.tool.user.User;
import com.sun.electric.util.TextUtils;
import com.sun.electric.util.math.ECoord;

import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Class to test the node extraction facility.
 */
public class ExtractionTest extends AbstractTest
{
	public ExtractionTest(String name)
	{
		super(name);
	}

	public static List<AbstractTest> getTests()
	{
		List<AbstractTest> list = new ArrayList<AbstractTest>();
		list.add(new ExtractionTest("Extract1"));
		list.add(new ExtractionTest("Extract2"));
		list.add(new ExtractionTest("Extract3"));
		list.add(new ExtractionTest("Extract4"));
		list.add(new ExtractionTest("Extract5"));
		list.add(new ExtractionTest("Extract6"));
		list.add(new ExtractionTest("Extract7"));
		list.add(new ExtractionTest("Extract8"));
		list.add(new ExtractionTest("Extract9"));
		list.add(new ExtractionTest("Extract10"));
		return list;
	}

	public static String getOutputDirectory()
	{
		String rootPath = User.getRegressionPath();
		if (rootPath == null) return null;
		return rootPath + "/tools/Extraction/output/";
	}

	public Boolean Extract1() { return commonExtractionTest("Test01"); }

	public Boolean Extract2() { return commonExtractionTest("Test02"); }

	public Boolean Extract3() { return commonExtractionTest("Test03"); }

	public Boolean Extract4() { return commonExtractionTest("Test04"); }

	public Boolean Extract5() { return commonExtractionTest("Test05"); }

	public Boolean Extract6() { return commonExtractionTest("Test06"); }

	public Boolean Extract7() { return commonExtractionTest("Test07"); }

	public Boolean Extract8() { return commonExtractionTest("Test08"); }

	public Boolean Extract9() { return commonExtractionTest("Test09"); }

	public Boolean Extract10() { return commonExtractionTest("Test10"); }

	public static boolean runExtractionTest(String testName)
	{
		ExtractionTest t = new ExtractionTest(testName);
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

	private Boolean commonExtractionTest(String cellName)
	{
		// initialize
		String testParameter = createMessageOutput();
		EditingPreferences ep = new EditingPreferences(true, TechPool.getThreadTechPool());
		Technology tech = Technology.getMocmosTechnology();
		setFoundry(tech);

		// read library with test setup
		URL fileURL = TextUtils.makeURLToFile(dataDir(getRegressionPath(), testParameter) + "ExtractionTests.jelib");
		String libName = "ExtractionTests";
		Library rootLib = Library.findLibrary(libName);
		if (rootLib == null) rootLib = LibraryFiles.readLibrary(ep, fileURL, libName, FileType.JELIB, true);
		Cell cell = rootLib.findNodeProto(cellName + "{lay}");
		if (cell == null)
		{
			System.out.println("ERROR: Cannot find cell " + cellName + "{lay}");
			return Boolean.FALSE;
		}

		// setup extraction parameters
		ErrorLogger errorLogger = ErrorLogger.newInstance("Node Extraction Test on cell " + cell.getName());
		boolean recursive = false;
		double smallestPolygonSize = 0;
		int activeHandling = 0;
		boolean gridAlignExtraction = false;
		ECoord scaledResolution = tech.getFactoryResolution();
		boolean approximateCuts = false;
		boolean flattenPcells = false;
		boolean usePureLayerNodes = false;
		List<Pattern> pats = new ArrayList<Pattern>();

		// run extraction
		Connectivity c = new Connectivity(cell, null, ep, errorLogger, smallestPolygonSize, activeHandling,
			gridAlignExtraction, scaledResolution, approximateCuts, recursive, pats);
		Cell newCell = c.doExtract(cell, recursive, pats, flattenPcells, usePureLayerNodes, true, null, null, null, null);

		// analyze results
		if (newCell == null)
			System.out.println("ERROR: Extraction of cell " + cell.describe(false) + " failed"); else
				newCell.lowLevelSetCreationDate(new Date(0));
		cell.rename(cell.getName() + "_ORIGINAL", null);
		return Boolean.valueOf(compareCellResults(newCell, getResultName()));
	}
}
