	/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: TestMenu.java
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

import com.sun.electric.tool.lang.EvalJavaBsh;
import com.sun.electric.tool.lang.EvalJython;
import com.sun.electric.tool.simulation.irsim.IRSIM;
import com.sun.electric.tool.user.menus.EMenu;
import com.sun.electric.tool.user.menus.EMenuItem;
import com.sun.electric.util.ClientOS;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Class to collect all tests available for Electric organized by tools.
 */
public class TestMenu
{
	static List<AbstractTest> totalList = new ArrayList<AbstractTest>();
	static Set<String> outputDirectories = new HashSet<String>();

	public static EMenu makeMenu()
	{
		List<EMenuItem> items = new ArrayList<EMenuItem>();
		items.add(new EveryTestMenuItem());
		items.add(new CleanTestMenuItem());
		items.add(EMenuItem.SEPARATOR);

//		if (CommonSignalAnalysisTest.hasCSA())
//			items.add(testSubMenu("Common Signal Analysis", CommonSignalAnalysisTest.getTests(), CommonSignalAnalysisTest.getOutputDirectory()));
		items.add(testSubMenu("Compaction", CompactionTest.getTests(), CompactionTest.getOutputDirectory()));
   		items.add(testSubMenu("DRC", DRCTest.getTests(), DRCTest.getOutputDirectory()));
  		items.add(testSubMenu("ERC", ERCTest.getTests(), ERCTest.getOutputDirectory()));
		items.add(testSubMenu("Export", ExportForeignTest.getTests(), ExportForeignTest.getOutputDirectory()));
		items.add(testSubMenu("Extraction", ExtractionTest.getTests(), ExtractionTest.getOutputDirectory()));
		items.add(testSubMenu("Generation", GenerationTest.getTests(), GenerationTest.getOutputDirectory()));
		items.add(testSubMenu("I-O", IOTest.getTests(), IOTest.getOutputDirectory()));
		items.add(testSubMenu("Import", ImportForeignTest.getTests(), ImportForeignTest.getOutputDirectory()));
		items.add(testSubMenu("Jobs", JobsTest.getTests(), JobsTest.getOutputDirectory()));
		items.add(testSubMenu("Layer Coverage", LayerCoverageToolTest.getTests(), LayerCoverageToolTest.getOutputDirectory()));
		items.add(testSubMenu("Logical Effort", LogicalEffortTest.getTests(), LogicalEffortTest.getOutputDirectory()));
		items.add(testSubMenu("NCC", NCCTest.getTests(), NCCTest.getOutputDirectory()));
		items.add(testSubMenu("Placement", PlacementTest.getTests(), PlacementTest.getOutputDirectory()));
		items.add(testSubMenu("Preferences", PrefTest.getTests(), PrefTest.getOutputDirectory()));
		items.add(testSubMenu("Routing", RoutingTest.getTests(), RoutingTest.getOutputDirectory()));
		items.add(testSubMenu("Scripting", ScriptingTest.getTests(), ScriptingTest.getOutputDirectory()));
		items.add(testSubMenu("Technology", TechnologyTest.getTests(), TechnologyTest.getOutputDirectory()));
		items.add(testSubMenu("Technology Edit", TechnologyEditTest.getTests(), TechnologyEditTest.getOutputDirectory()));
//		if (TimingAnalysisTest.hasOyster())
//			items.add(testSubMenu("Timing Analysis", TimingAnalysisTest.getTests(), TimingAnalysisTest.getOutputDirectory()));
		if (ClientOS.isOSWindows())
			items.add(testSubMenu("Waveform Window", WaveformTest.getTests(), WaveformTest.getOutputDirectory()));
		return new EMenu("Test", items);
	}

	private static EMenu testSubMenu(String menuName, List<AbstractTest> list, String outputDir)
	{
		for(AbstractTest at : list)
		{
			at.setGroupName(menuName);
			if (!at.isInteractive()) totalList.add(at);
		}
		if (outputDir != null) outputDirectories.add(outputDir);
		int testSize = list.size() + 2;
		EMenuItem[] tests = new EMenuItem[testSize];
		int fill = 0;
		tests[fill++] = new AllMenuItems(list);
		tests[fill++] = EMenuItem.SEPARATOR;
		for (AbstractTest t : list)
		{
			tests[fill++] = new TestMenuItem(t);
		}
		return new EMenu(menuName, tests);
	}

	/**
	 * Class to define the first entry in each Tests sub-menu to run every one of its tests.
	 */
	private static class AllMenuItems extends EMenuItem
	{
		private final List<AbstractTest> list;

		AllMenuItems(List<AbstractTest> list)
		{
			super("Run All Tests");
			this.list = list;
		}

		public void run()
		{
			new FakeTestJob(list);
		}
	}

	/**
	 * Class to define the first entry in the "Tests" menu
	 * that runs every test.
	 */
	private static class EveryTestMenuItem extends EMenuItem
	{
		EveryTestMenuItem()
		{
			super("Run Every Test");
		}

		public void run()
		{
			if (!EvalJavaBsh.hasBeanShell())
				System.out.println("WARNING: Bean Shell is not installed, so some tests will not be run");
			if (!EvalJython.hasJython())
				System.out.println("WARNING: Jython is not installed, so some tests will not be run");
			if (!IRSIM.hasIRSIM())
				System.out.println("WARNING: IRSIM is not installed, so some tests will not be run");
			new FakeTestJob(totalList);
		}
	}

	private static class TestMenuItem extends EMenuItem
	{
		final AbstractTest t;

		TestMenuItem(AbstractTest t)
		{
			super(t.getFullTestName());
			this.t = t;
		}

		public void run()
		{
			new FakeTestJob(t);
		}
	}

	/**
	 * Class to define the second entry in the "Tests" menu
	 * that cleans up after tests.
	 */
	private static class CleanTestMenuItem extends EMenuItem
	{
		CleanTestMenuItem()
		{
			super("Clean Test Menu Directory");
		}

		public void run()
		{
			System.out.println("Cleaning up test area");
			for(String dir : outputDirectories)
			{
				File f = new File(dir);
				if (!f.exists()) continue;
				String[] subFiles = f.list();
				int deletedFiles = 0;
				for(int i=0; i<subFiles.length; i++)
				{
					File sf = new File(dir + File.separator + subFiles[i]);
					if (sf.exists())
					{
						if (sf.delete())
							deletedFiles++;
					}
				}
				if (f.delete())
				{
					System.out.println("Deleted directory " + dir + " (" + deletedFiles + " files)");
				} else
				{
					System.out.println("ERROR deleting directory " + dir + " (did delete " + deletedFiles + " files)");
				}
			}
		}
	}
}
