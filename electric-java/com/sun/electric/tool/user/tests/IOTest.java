	/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: IOTest.java
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
import com.sun.electric.database.network.NetworkTool;
import com.sun.electric.technology.TechPool;
import com.sun.electric.technology.Technology;
import com.sun.electric.tool.generator.layout.LayoutLib;
import com.sun.electric.tool.io.input.Input;
import com.sun.electric.tool.user.ErrorLogger;
import com.sun.electric.tool.user.MessagesStream;
import com.sun.electric.tool.user.User;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Class to test library reading.
 */
public class IOTest extends AbstractTest
{
	public IOTest(String name)
	{
		super(name);
	}

	public static List<AbstractTest> getTests()
	{
		List<AbstractTest> list = new ArrayList<AbstractTest>();
		list.add(new IOTest("readLib1"));
		list.add(new IOTest("readLib2"));
		return list;
	}

	public static String getOutputDirectory()
	{
		String rootPath = User.getRegressionPath();
		if (rootPath == null) return null;
		return rootPath + "/tools/IO/output/";
	}

	/************************************* readLib1 *********************************************************/

	public boolean readLib1()
	{
		return ioReadGenStatic(getRegressionPath(), "/data/qThree", "qThreeTop.jelib", "qThreeTop", 365, 0, 0);
	}

	/************************************* readLib2 *********************************************************/

	public boolean readLib2()
	{
		return ioReadGenStatic(getRegressionPath(), "/data/muddChip", "MIPS.jelib", "chip", 0, 3879, 0);
	}

	/************************************* SUPPORT *********************************************************/

	public static boolean ioReadGenStatic(String trueRootPath, String libPath, String libName,
		String logFile, int expectedNumInputLogs, int expectedNumNetworkLogs, int expectedNumCheckErrors)
	{
		boolean passed = false;
		try
		{
			String testParameter = properDirectory(trueRootPath, IOTest.class);
			String outputDir = outputDir(trueRootPath, testParameter);
			ensureOutputDirectory(outputDir);
			MessagesStream.getMessagesStream().save(outputDir + logFile + ".log");
			setFoundry(Technology.getMocmosTechnology(), "Mosis");
			EditingPreferences ep = new EditingPreferences(true, TechPool.getThreadTechPool());

			NetworkTool.totalNumErrors = 0;
			wipeLibraries();
			LayoutLib.openLibForRead(trueRootPath + libPath + "/" + libName, ep, true);

			for (Iterator<Library> it = Library.getLibraries(); it.hasNext(); )
			{
				Library lib = it.next();
				for (Iterator<Cell> cit = lib.getCells(); cit.hasNext(); )
				{
					Cell cell = cit.next();
					cell.getNetlist();
				}
			}

			int numInputLogs = Input.errorLogger.getNumLogs();
			int numNetworkLogs = NetworkTool.totalNumErrors;
			int numCheckErrors = 0;
			for(Iterator<Library> it = Library.getLibraries(); it.hasNext(); )
			{
				Library lib = it.next();
				numCheckErrors += lib.checkAndRepair(false, ErrorLogger.newInstance("Check Libraries"), ep);
			}

			passed = (expectedNumInputLogs == numInputLogs && expectedNumNetworkLogs == numNetworkLogs &&
				expectedNumCheckErrors == numCheckErrors);
			if (!passed)
			{
				System.out.println("ERROR: Expected " + expectedNumInputLogs + " input " + expectedNumNetworkLogs + " network " + expectedNumCheckErrors + " errors");
				System.out.println("   but obtained " + numInputLogs + " input " + numNetworkLogs + " network " + numCheckErrors + " errors");
			}
		} catch (Exception e)
		{
			System.out.println("Exception: " +e);
			e.printStackTrace();
		}
		return passed;
	}
}
