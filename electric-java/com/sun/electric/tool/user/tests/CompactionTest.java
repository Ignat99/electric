/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: CompactionTest.java
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
import com.sun.electric.tool.compaction.Compaction;
import com.sun.electric.tool.io.FileType;
import com.sun.electric.tool.io.input.LibraryFiles;
import com.sun.electric.tool.user.User;
import com.sun.electric.util.TextUtils;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Class to test the compaction tool.
 */
public class CompactionTest extends AbstractTest
{
	public CompactionTest(String name)
	{
		super(name, true, false);
	}

	public static List<AbstractTest> getTests()
	{
		List<AbstractTest> list = new ArrayList<AbstractTest>();
		list.add(new CompactionTest("Regular1"));
		list.add(new CompactionTest("Regular2"));
		list.add(new CompactionTest("Regular3"));
		list.add(new CompactionTest("Spread"));
		return list;
	}

	public static String getOutputDirectory()
	{
		String rootPath = User.getRegressionPath();
		if (rootPath == null) return null;
		return rootPath + "/tools/Compaction/output/";
	}

	/************************************* Regular1 *********************************************************/

	public boolean Regular1()
	{
		return test("compactionTest1", false);
	}

	/************************************* Regular2 *********************************************************/

	public boolean Regular2()
	{
		return test("compactionTest2", false);
	}

	/************************************* Regular3 *********************************************************/

	public boolean Regular3()
	{
		return test("compactionTest3", false);
	}

	/************************************* Spread *********************************************************/

	public boolean Spread()
	{
		return test("spreadTest1", true);
	}


	/************************************* SUPPORT *********************************************************/

	private boolean test(String cellName, boolean spreading)
	{
		String testParameter = createMessageOutput();

		// read library
		EditingPreferences ep = new EditingPreferences(true, TechPool.getThreadTechPool());
		URL fileURL = TextUtils.makeURLToFile(dataDir(getRegressionPath(), testParameter) + "compactionTests.jelib");
		String libName = "compactionTests";
		Library lib = Library.findLibrary(libName);
		if (lib == null) lib = LibraryFiles.readLibrary(ep, fileURL, libName, FileType.JELIB, true);
		if (lib == null)
		{
			System.out.println("Library " + libName + " not found");
			return false;
		}

		// find cell
		Cell lay = lib.findNodeProto(cellName + "{lay}");
		if (lay == null)
		{
			System.out.println("Cell " + cellName + "{lay} not found");
			return false;
		}

		Compaction.compactNow(lay, spreading, new CompareJob(lay));
		return true;
	}
}
