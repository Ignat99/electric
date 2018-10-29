/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: ERCTest.java
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
import com.sun.electric.technology.TechPool;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.erc.ERCAntenna;
import com.sun.electric.tool.erc.ERCWellCheck;
import com.sun.electric.tool.generator.layout.LayoutLib;
import com.sun.electric.tool.user.User;
import com.sun.electric.util.TextUtils;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Class to test the ERC tool.
 */
public class ERCTest extends AbstractTest
{
	public ERCTest(String name)
	{
		super(name);
	}

	public static List<AbstractTest> getTests()
	{
		List<AbstractTest> list = new ArrayList<AbstractTest>();
		list.add(new ERCTest("Well1"));
		list.add(new ERCTest("Well2"));
		list.add(new ERCTest("Antenna1"));
		list.add(new ERCTest("Antenna2"));
		return list;
	}

	public static String getOutputDirectory()
	{
		String rootPath = User.getRegressionPath();
		if (rootPath == null) return null;
		return rootPath + "/tools/ERC/output/";
	}

	/************************************* Well1 *********************************************************/

	public Boolean Well1()
	{
		createMessageOutput();
		Boolean passed = Boolean.TRUE;
		if (!basicWellTest(getRegressionPath() + "/data/muddChip", "MIPS.jelib", "chip", "", 0, 0)) passed = Boolean.FALSE;
		return passed;
	}

	/************************************* Well2 *********************************************************/

	public Boolean Well2()
	{
		createMessageOutput();
		Boolean passed = Boolean.TRUE;
		if (!basicWellTest(getRegressionPath() + "/data/qThree", "qThreeTop.jelib", "qThreeTop", "", 390, 2)) passed = Boolean.FALSE;
		return passed;
	}

	/************************************* Antenna1 *********************************************************/

	public Boolean Antenna1()
	{
		createMessageOutput();
		Boolean passed = Boolean.TRUE;
		if (!basicERCAntennaTest(getRegressionPath() + "/data/muddChip", "MIPS.jelib", "chip", "", 1039)) passed = Boolean.FALSE;
		return passed;
	}

	/************************************* Antenna2 *********************************************************/

	public Boolean Antenna2()
	{
		createMessageOutput();
		Boolean passed = Boolean.TRUE;
		if (!basicERCAntennaTest(getRegressionPath() + "/data/qThree", "qThreeTop.jelib", "qThreeTop", "", 8)) passed = Boolean.FALSE;
		return passed;
	}

	/************************************* SUPPORT *********************************************/

	/**
	 * Basic ERC Test. Must be public for call from regression
	 */
	public static boolean basicERCAntennaTest(String regressionData, String libNameIO, String cellName, String logName, int numErrors)
	{
		Cell lay = prepareERCTests(regressionData, libNameIO, cellName);
		if (lay == null)
		{
			System.out.println("Layout cell '" + cellName + "' not found");
			return false;
		}

		ERCAntenna.AntennaPreferences antennaPrefs = new ERCAntenna.AntennaPreferences(false, lay.getDatabase().getTechPool());
		antennaPrefs.disablePopups = true;
		int err = ERCAntenna.checkERCAntenna(lay, antennaPrefs, null);
		boolean passed = (numErrors == err);
		if (!passed)
			System.out.println("ERC ANTENNA FOUND " + err + " ERRORS BUT EXPECTED " + numErrors);
		return passed;
	}

	/**
	 * Basic ERC Test. Must be public for call from regression
	 */
	public static boolean basicWellTest(String regressionData, String libNameIO, String cellName, String logName, int numErrors, int numOfThreads)
	{
		System.out.println("Running ERC Well Check");
		Job.setDebug(true);

		Cell lay = prepareERCTests(regressionData, libNameIO, cellName);
		if (lay == null)
		{
			System.out.println("Layout cell '" + cellName + "' not found");
			return false;
		}

		ERCWellCheck.WellCheckPreferences prefs = new ERCWellCheck.WellCheckPreferences(true);
		prefs.drcCheck = false;
		prefs.pWellCheck = 1;
		prefs.nWellCheck = 1;
		prefs.maxProc = numOfThreads;
		prefs.disablePopups = true;

		int err = ERCWellCheck.checkERCWell(lay, prefs);
		boolean passed = (numErrors == err);
		if (!passed)
			System.out.println("ERC FOUND " + err + " ERRORS BUT EXPECTED " + numErrors);

		return passed;
	}

	private static Cell prepareERCTests(String regressionData, String libNameIO, String cellName)
	{
		Cell lay = null;
		try
		{
			EditingPreferences ep = new EditingPreferences(true, TechPool.getThreadTechPool());
			LayoutLib.openLibForRead(regressionData + "/" + libNameIO, ep, true);

			URL fileURL = TextUtils.makeURLToFile(libNameIO);
			String libName = TextUtils.getFileNameWithoutExtension(fileURL);
			Library rootLib = Library.findLibrary(libName);
			lay = rootLib.findNodeProto(cellName + "{lay}");
		} catch (Exception e) { e.printStackTrace(); }
		return lay;
	}
}
