	/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: NCCTest.java
 *
 * Copyright (c) 2003, Static Free Software. All rights reserved.
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
import com.sun.electric.tool.ncc.Ncc;
import com.sun.electric.tool.ncc.NccOptions;
import com.sun.electric.tool.ncc.SchemNamesToLay;
import com.sun.electric.tool.ncc.result.NccResult;
import com.sun.electric.tool.ncc.result.NccResults;
import com.sun.electric.tool.user.User;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Class to test the NCC tool.
 */
public class NCCTest extends AbstractTest
{
	public NCCTest(String name)
	{
		super(name);
	}

	public static List<AbstractTest> getTests()
	{
		List<AbstractTest> list = new ArrayList<AbstractTest>();
		list.add(new NCCTest("Q0"));		// qThree top (which fails)
		list.add(new NCCTest("Q1"));
		list.add(new NCCTest("Q2"));
		list.add(new NCCTest("Q3"));
		list.add(new NCCTest("Q4"));
		list.add(new NCCTest("Q5"));
		list.add(new NCCTest("M0"));		// muddChip top (which fails)
		list.add(new NCCTest("M1"));
		list.add(new NCCTest("M2"));
		list.add(new NCCTest("M3"));
		list.add(new NCCTest("M4"));
		list.add(new NCCTest("M5"));
		list.add(new NCCTest("M6"));
		list.add(new NCCTest("M7"));
		list.add(new NCCTest("M8"));
		return list;
	}

	public static String getOutputDirectory()
	{
		String rootPath = User.getRegressionPath();
		if (rootPath == null) return null;
		return rootPath + "/tools/NCC/output/";
	}

	/************************************* Q0 *********************************************************/

	public boolean Q0()
	{
		createMessageOutput();
		return NCCTestQ0Run(getRegressionPath());
	}

	public static boolean NCCTestQ0Run(String regressionDirPath)
	{
		RootCells rootCells = getRootCells(regressionDirPath, "data/qThree/qThreeTop.jelib", "qThreeTop");
		NccResults results = compare(rootCells.sch, null, rootCells.lay, null, hierOptions());
		return checkResults(results, 39, 0, 0, false, 139, 0, 0, 0, 0, new String[] {
			"rowColScan:drivePass{sch} with: rowColScan:drivePass{lay}",
			"rowColScan:scan2{sch} with: rowColScan:scan2{lay}",
			"rowColScan:scanBoost5{sch} with: rowColScan:scanBoost5{lay}",
			"group:fullGroupCap{sch} with: group:fullGroupCap{lay}",
			"rowColScan:scan6{sch} with: rowColScan:scan6{lay}",
			"jtag:IRdecode{sch} with: jtag:IRdecode{lay}",
			"jtag:tsinv{sch} with: jtag:tsinv{lay}",
			"jtag:jtagScanControl{sch} with: jtag:jtagScanControl{lay}",
			"jtag:tapCtlJKL{sch} with: jtag:tapCtlJKL{lay}",
			"jtag:jtagCentral{sch} with: jtag:jtagCentral{lay}",
			"qThree_pads_180nm:padGnd{sch} with: qThree_pads_180nm:padGnd{lay}",
			"padParts:bufStrength48{sch} with: padParts:bufStrength48{lay}",
			"padParts:padpart_buf90{sch} with: padParts:padpart_buf90{lay}",
			"qThree_pads_180nm:padInStrong{sch} with: qThree_pads_180nm:padInStrong{lay}",
			"qThree_pads_180nm:padOut{sch} with: qThree_pads_180nm:padOut{lay}",
			"qThree_pads_180nm:padRawESD{sch} with: qThree_pads_180nm:padRawESD{lay}",
			"qThree_pads_180nm:padVdd{sch} with: qThree_pads_180nm:padVdd{lay}",
			"qThreeTop:rectGroup{sch} with: qThreeTop:rectGroup{lay}",
			"qThreeTop:qThreeTop{sch} with: qThreeTop:qThreeTop{lay}"
		});
	}

	/************************************* Q1 *********************************************************/

	public boolean Q1()
	{
		createMessageOutput();
		return NCCTestQ1Run(getRegressionPath());
	}

	public static boolean NCCTestQ1Run(String regressionDirPath)
	{
		RootCells rootCells = getRootCells(regressionDirPath, "data/qThree/qThreeTop.jelib", "rectifier");
		NccResults results = compare(rootCells.sch, null, rootCells.lay, null, hierOptions());
		return checkResults(results, 3, 0, 0, false, 5, 0, 0, 0, 0, new String[] {});
	}

	/************************************* Q2 *********************************************************/

	public boolean Q2()
	{
		createMessageOutput();
		return NCCTestQ2Run(getRegressionPath());
	}

	public static boolean NCCTestQ2Run(String regressionDirPath)
	{
		RootCells rootCells = getRootCells(regressionDirPath, "data/qThree/rowColScan.jelib", "colScanCx1");
		NccResults results = compare(rootCells.sch, null, rootCells.lay, null, hierOptions());
		return checkResults(results, 2, 0, 0, true, 0, 0, 0, 0, 0, new String[] {});
	}

	/************************************* Q3 *********************************************************/

	public boolean Q3()
	{
		createMessageOutput();
		return NCCTestQ3Run(getRegressionPath());
	}

	public static boolean NCCTestQ3Run(String regressionDirPath)
	{
		RootCells rootCells = getRootCells(regressionDirPath, "data/qThree/rowColScan.jelib", "driveRow");
		NccResults results = compare(rootCells.sch, null, rootCells.lay, null, hierOptions());
		return checkResults(results, 1, 0, 0, true, 0, 0, 0, 0, 0, new String[] {});
	}

	/************************************* Q4 *********************************************************/

	public boolean Q4()
	{
		createMessageOutput();
		return NCCTestQ4Run(getRegressionPath());
	}

	public static boolean NCCTestQ4Run(String regressionDirPath)
	{
		RootCells rootCells = getRootCells(regressionDirPath, "data/qThree/scanFans.jelib", "scanAmp8w432");
		NccResults results = compare(rootCells.sch, null, rootCells.lay, null, hierOptions());
		return checkResults(results, 2, 0, 0, true, 0, 0, 0, 0, 0, new String[] {});
	}

	/************************************* Q5 *********************************************************/

	public boolean Q5()
	{
		createMessageOutput();
		return NCCTestQ5Run(getRegressionPath());
	}

	public static boolean NCCTestQ5Run(String regressionDirPath)
	{
		RootCells rootCells = getRootCells(regressionDirPath, "data/qThree/jtag.jelib", "jtagIRControl");
		NccResults results = compare(rootCells.sch, null, rootCells.lay, null, hierOptions());
		return checkResults(results, 5, 0, 0, true, 0, 0, 0, 0, 0, new String[] {});
	}

	/************************************* M0 *********************************************************/

	public boolean M0()
	{
		createMessageOutput();
		return NCCTestM0Run(getRegressionPath());
	}

	public static boolean NCCTestM0Run(String regressionDirPath)
	{
		RootCells rootCells = getRootCells(regressionDirPath, "data/muddChip/MIPS.jelib", "chip");
		NccResults results = compare(rootCells.sch, null, rootCells.lay, null, hierOptions());
		return checkResults(results, 135, 0, 600, false, 89, 6, 3, 0, 0, new String[] {
			"DatapathDone:mdunit{sch} with: DatapathDone:mdunit{lay}",
			"DatapathDone:nand3_1x{sch} with: DatapathDone:nand3_1x{lay}",
			"DatapathDone:or3_1x{sch} with: DatapathDone:or3_1x{lay}",
			"memsys_final:memsys{sch} with: memsys_final:memsys{lay}",
			"MIPS:mips{sch} with: MIPS:mips{lay}",
			"muddlib07:a22o2_1x{sch} with: muddlib07:a22o2_1x{lay}",
			"muddlib07:nand3_1_5x{sch} with: muddlib07:nand3_1_5x{lay}",
			"muddlib07:or3_1x{sch} with: muddlib07:or3_1x{lay}"
		});
	}

	/************************************* M1 *********************************************************/

	public boolean M1()
	{
		createMessageOutput();
		return NCCTestM1Run(getRegressionPath());
	}

	public static boolean NCCTestM1Run(String regressionDirPath)
	{
		RootCells rootCells = getRootCells(regressionDirPath, "data/muddChip/DatapathDone.jelib", "Shifter_dp");
		NccResults results = compare(rootCells.sch, null, rootCells.lay, null, hierOptions());
		return checkResults(results, 7, 0, 0, false, 12, 0, 5, 0, 0, new String[] {});
	}

	/************************************* M2 *********************************************************/

	public boolean M2()
	{
		createMessageOutput();
		return NCCTestM2Run(getRegressionPath());
	}

	public static boolean NCCTestM2Run(String regressionDirPath)
	{
		RootCells rootCells = getRootCells(regressionDirPath, "data/muddChip/DatapathDone.jelib", "controlPLA");
		NccResults results = compare(rootCells.sch, null, rootCells.lay, null, hierOptions());
		return checkResults(results, 1, 0, 0, true, 0, 0, 0, 0, 0, new String[] {});
	}

	/************************************* M3 *********************************************************/

	public boolean M3()
	{
		createMessageOutput();
		return NCCTestM3Run(getRegressionPath());
	}

	public static boolean NCCTestM3Run(String regressionDirPath)
	{
		RootCells rootCells = getRootCells(regressionDirPath, "data/muddChip/DatapathDone.jelib", "regramarray_dp");
		NccResults results = compare(rootCells.sch, null, rootCells.lay, null, hierOptions());
		return checkResults(results, 3, 0, 0, false, 2, 0, 0, 0, 0, new String[] {});
	}

	/************************************* M4 *********************************************************/

	public boolean M4()
	{
		createMessageOutput();
		return NCCTestM4Run(getRegressionPath());
	}

	public static boolean NCCTestM4Run(String regressionDirPath)
	{
		RootCells rootCells = getRootCells(regressionDirPath, "data/muddChip/memsys_final.jelib", "cacheramarray");
		NccResults results = compare(rootCells.sch, null, rootCells.lay, null, hierOptions());
		return checkResults(results, 3, 0, 0, true, 0, 0, 0, 0, 0, new String[] {});
	}

	/************************************* M5 *********************************************************/

	public boolean M5()
	{
		createMessageOutput();
		return NCCTestM5Run(getRegressionPath());
	}

	public static boolean NCCTestM5Run(String regressionDirPath)
	{
		RootCells rootCells = getRootCells(regressionDirPath, "data/muddChip/memsys_final.jelib", "datapath");
		NccResults results = compare(rootCells.sch, null, rootCells.lay, null, hierOptions());
		return checkResults(results, 8, 0, 0, true, 0, 0, 0, 0, 0, new String[] {});
	}

	/************************************* M6 *********************************************************/

	public boolean M6()
	{
		createMessageOutput();
		return NCCTestM6Run(getRegressionPath());
	}

	public static boolean NCCTestM6Run(String regressionDirPath)
	{
		RootCells rootCells = getRootCells(regressionDirPath, "data/muddChip/new_controller.jelib", "alushpla");
		NccResults results = compare(rootCells.sch, null, rootCells.lay, null, hierOptions());
		return checkResults(results, 1, 0, 0, true, 0, 0, 0, 0, 0, new String[] {});
	}

	/************************************* M7 *********************************************************/

	public boolean M7()
	{
		createMessageOutput();
		return NCCTestM7Run(getRegressionPath());
	}

	public static boolean NCCTestM7Run(String regressionDirPath)
	{
		RootCells rootCells = getRootCells(regressionDirPath, "data/muddChip/new_controller.jelib", "maindec");
		NccResults results = compare(rootCells.sch, null, rootCells.lay, null, hierOptions());
		return checkResults(results, 3, 0, 0, true, 0, 0, 0, 0, 0, new String[] {});
	}

	/************************************* M8 *********************************************************/

	public boolean M8()
	{
		createMessageOutput();
		return NCCTestM8Run(getRegressionPath());
	}

	public static boolean NCCTestM8Run(String regressionDirPath)
	{
		RootCells rootCells = getRootCells(regressionDirPath, "data/muddChip/new_controller.jelib", "registers");
		NccResults results = compare(rootCells.sch, null, rootCells.lay, null, hierOptions());
		return checkResults(results, 4, 0, 0, false, 6, 1, 0, 0, 0, new String[] {});
	}

	/************************************* SUPPORT *********************************************/

	private static NccOptions hierOptions()
	{
		NccOptions options = new NccOptions();
		options.operation = NccOptions.HIER_EACH_CELL;
		options.haltAfterFirstMismatch = false;
		options.oneNamePerPort = false;
		return options;
	}

	private static NccResults compare(Cell c1, VarContext v1, Cell c2, VarContext v2, NccOptions opt)
	{
		try
		{
			return Ncc.compare(c1, v1, c2, v2, opt);
		} catch (Throwable t)
		{
			System.out.println("NCC throws Throwable: "+t);
			t.printStackTrace();
			return null;
		}
	}

	private static class RootCells
	{
		public final Cell sch, lay;
		RootCells(Cell s, Cell l) {sch=s; lay=l;}
	}

	private static String getRegressionDirPath(String regressionDirPath)
	{
		if (regressionDirPath != null) return regressionDirPath;
		return "../..";
	}

	private static String getFailedCellPair(NccResult result)
	{
		String[] cellNms = result.getRootCellNames();
		return cellNms[0] + " with: " + cellNms[1];
	}

	// Subtle: The new Java-based regression is "elegant" in that it removes the regression from the middle of NCC.
	// It generates NccResults and then checks the results.
	// A disadvantage of this approach is that keeping the entire NccResults for
	// a FLAT_EACH_CELL can take a large amount of storage. So far, the place where
	// this causes insufficient heap space problems is during the test of the renaming.
	// Therefore I skip the renaming tests for certain FLAT_EACH_CELL runs.
	private static boolean checkResults(NccResults results, int expectedTopologyPassed, int expectSzErrs, int expectedRegErrs,
		boolean testRenaming, int numArcRenames, int numNodeRenames, int numArcManRenames,
		int numNodeManRenames, int numNameConflicts, String[] failedCellPairs)
	{
		Set<String> setFailedPairs = new HashSet<String>();
		List<String> unexpectedFailed = new ArrayList<String>();
		for (int i=0; i<failedCellPairs.length; i++) setFailedPairs.add(failedCellPairs[i]);
		int numPassed = 0;
		int numSzErrs = 0;
		int eqErrs = 0;
		boolean ok = true;
		for (NccResult result : results)
		{
			// Keep a global count for size mismatches
			if (result.exportMatch() && result.topologyMatch())
			{
				numPassed++;
			} else
			{
				String failedCellPair = getFailedCellPair(result);
				if (!setFailedPairs.contains(failedCellPair)) unexpectedFailed.add(failedCellPair);
			}
			numSzErrs += result.getNccGuiInfo().getSizeMismatches().size();
			Cell cell1 = result.getRootCells()[0];
			Cell cell2 = result.getRootCells()[1];
			eqErrs += result.getEquivalence().regressionTest(cell1, cell2);
		}
		EditingPreferences ep = new EditingPreferences(true, TechPool.getThreadTechPool());
		SchemNamesToLay.RenameResult rr = SchemNamesToLay.copyNames(results, ep);

		// Print all the regression failure results at end of log file
		System.out.println("========================== Regression Results =============================");
		for (String failedCellPair : unexpectedFailed)
		{
			System.out.println("Unexpected comparison failure: " + failedCellPair);
			ok = false;
		}
		if (expectedTopologyPassed != numPassed)
		{
			System.out.println("Wrong number of comparisons passed: " + numPassed + " Expected: " + expectedTopologyPassed);
			ok = false;
		}
		if (expectSzErrs != numSzErrs)
		{
			System.out.println("Wrong number of size errors: " + numSzErrs + " Expected: " + expectSzErrs);
			ok = false;
		}
		if (eqErrs != expectedRegErrs)
		{
			System.out.println("Wrong number of NCC failures: " + eqErrs + " Expected: " + expectedRegErrs);
			ok = false;
		}

		if ((rr.numArcRenames != numArcRenames && testRenaming) || rr.numArcRenames > numArcRenames)
		{
			System.out.println("Wrong number of arc renames: " + rr.numArcRenames + " Expected: " + numArcRenames);
			ok = false;
		}
		if ((rr.numNodeRenames != numNodeRenames && testRenaming) || rr.numNodeRenames > numNodeRenames)
		{
			System.out.println("Wrong number of node renames: " + rr.numNodeRenames + " Expected: " + numNodeRenames);
			ok = false;
		}
		if ((rr.numArcManRenames != numArcManRenames && testRenaming) || rr.numArcManRenames > numArcManRenames)
		{
			System.out.println("Wrong number of arc manual renames: " + rr.numArcManRenames + " Expected: " + numArcManRenames);
			ok = false;
		}
		if ((rr.numNodeManRenames != numNodeManRenames && testRenaming) || rr.numNodeManRenames > numNodeManRenames)
		{
			System.out.println("Wrong number of node manual renames: " + rr.numNodeManRenames + " Expected: " + numNodeManRenames);
			ok = false;
		}
		if ((rr.numNameConflicts != numNameConflicts && testRenaming) || rr.numNameConflicts > numNameConflicts)
		{
			System.out.println("Wrong number of renaming conflicts: " + rr.numNameConflicts + " Expected: " + numNameConflicts);
			ok = false;
		}
		return ok;
	}

	/**
	 * @param regressionDirPath path to our "regression" directory. If null then
	 * assume that current directory is "regression/tool/NCC"
	 * @param libPath library file path relative to regressionDir
	 * @param cellNm the name of the schematic and layout Cells in library to compare
	 * @return the root cells.
	 */
	private static RootCells getRootCells(String regressionDirPath, String libPath, String cellNm)
	{
		regressionDirPath = getRegressionDirPath(regressionDirPath);
		EditingPreferences ep = new EditingPreferences(true, TechPool.getThreadTechPool());
		Library rootLib = LayoutLib.openLibForRead(regressionDirPath+"/"+libPath, ep, true);
		Library.repairAllLibraries(ep);
		Cell sch = rootLib.findNodeProto(cellNm+"{sch}");
		Cell lay = rootLib.findNodeProto(cellNm+"{lay}");
		return new RootCells(sch, lay);
	}
}
