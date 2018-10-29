	/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: LogicalEffortTest.java
 *
 * Copyright (c) 2008, Static Free Software. All rights reserved.
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
import com.sun.electric.database.variable.VarContext;
import com.sun.electric.technology.TechPool;
import com.sun.electric.tool.generator.layout.LayoutLib;
import com.sun.electric.tool.lang.EvalJavaBsh;
import com.sun.electric.tool.logicaleffort.LENetlister;
import com.sun.electric.tool.logicaleffort.LESizer;
import com.sun.electric.tool.logicaleffort.LETool;
import com.sun.electric.tool.user.ErrorLogger;
import com.sun.electric.tool.user.MessagesStream;
import com.sun.electric.tool.user.User;

import java.util.ArrayList;
import java.util.List;

/**
 * Class to test the Logical Effort tool.
 */
public class LogicalEffortTest extends AbstractTest
{
	public LogicalEffortTest(String name)
	{
		super(name);
	}

	public static List<AbstractTest> getTests()
	{
		List<AbstractTest> list = new ArrayList<AbstractTest>();
		if (EvalJavaBsh.hasBeanShell())
			list.add(new LogicalEffortTest("LE"));
		return list;
	}

	public static String getOutputDirectory()
	{
		String rootPath = User.getRegressionPath();
		if (rootPath == null) return null;
		return rootPath + "/tools/LogicalEffort/output/";
	}

	/************************************* LE *********************************************************/

	public Boolean LE()
	{
		String libName = "LETest.jelib";
		String cellName = "total_core";
		int expectedSize = 2247;
		int expectedErrors = 0;
		int expectedWarnings = 770;

		boolean failed = runSizing("LogicalEffort", libName, cellName, getRegressionPath(), expectedSize, expectedErrors, expectedWarnings);
		return Boolean.valueOf(!failed);
	}

	/**
	 * Run Logical Effort Sizing on a cell.
	 * @param testName the name of the test.
	 * @param libName the name of the library to load.
	 * @param cellName the name of the schematic cell to test.
	 * @param rootPath
	 * @param expectedSize the expected total size of all sizable gates, truncated to an integer.
	 * @param expectedErrors the number of expected errors.
	 * @param expectedWarnings the number of expected warnings.
	 * @return true if failed.
	 */
	public static boolean runSizing(String testName, String libName, String cellName,
		String rootPath, int expectedSize, int expectedErrors, int expectedWarnings)
	{
		boolean failed = false;
		try
		{
			String testParameter = properDirectory(rootPath, LogicalEffortTest.class);
			String testLibPath = dataDir(rootPath, testParameter) + libName;
			String outputDir = outputDir(rootPath, testParameter);
			ensureOutputDirectory(outputDir);
			MessagesStream.getMessagesStream().save(outputDir + testName + ".log");

			EditingPreferences ep = new EditingPreferences(true, TechPool.getThreadTechPool());
			Library lib = LayoutLib.openLibForRead(testLibPath, ep, true);
			if (lib == null)
			{
				System.out.println("ERROR: Cannot read library " + testLibPath);
				return true;
			}
			Cell lay = lib.findNodeProto(cellName + "{sch}");
			if (lay == null)
			{
				System.out.println("ERROR: Cannot find cell " + cellName + "{sch} in library " + lib.getName());
				return true;
			}

			// run logical effort on top level
			LETool.AnalyzeCell job = new LETool.AnalyzeCell(LESizer.Alg.EQUALGATEDELAYS, lay, VarContext.globalContext, true);
			job.doIt();
			LENetlister netlister = job.getNetlister();
			ErrorLogger logger = netlister.getErrorLogger();

			// check number of errors and warnings
			if (expectedErrors != logger.getNumErrors())
			{
				System.out.println("Error: found: " + logger.getNumErrors() + " errors but expected " + expectedErrors);
				failed = true;
			}
			if (expectedWarnings != logger.getNumWarnings())
			{
				System.out.println("Error: found: " + logger.getNumWarnings() + " warnings but expected " + expectedWarnings);
				failed = true;
			}
			if (expectedSize != (int)netlister.getTotalLESize())
			{
				System.out.println("Error: found: " + (int)netlister.getTotalLESize() + " size, but expected " + expectedSize);
				failed = true;
			}
		} catch (Exception e)
		{
			System.out.println("Exception: " + e);
			e.printStackTrace();
			failed = true;
		}
		return failed;
	}
}
