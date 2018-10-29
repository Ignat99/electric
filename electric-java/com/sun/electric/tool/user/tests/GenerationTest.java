	/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: GenerationTest.java
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
import com.sun.electric.technology.Technology;
import com.sun.electric.tool.drc.DRC;
import com.sun.electric.tool.drc.Quick;
import com.sun.electric.tool.extract.LayerCoverageTool;
import com.sun.electric.tool.generator.PadGenerator;
import com.sun.electric.tool.generator.ROMGenerator;
import com.sun.electric.tool.generator.cmosPLA.PLA;
import com.sun.electric.tool.generator.layout.GateRegression;
import com.sun.electric.tool.generator.layout.LayoutLib;
import com.sun.electric.tool.generator.layout.fill.FillGenConfig;
import com.sun.electric.tool.generator.layout.fill.FillGeneratorTool;
import com.sun.electric.tool.generator.layout.fill.StitchFillJob;
import com.sun.electric.tool.generator.layout.fillCell.FillCellGenJob;
import com.sun.electric.tool.io.FileType;
import com.sun.electric.tool.io.IOTool;
import com.sun.electric.tool.io.input.LibraryFiles;
import com.sun.electric.tool.io.output.Output;
import com.sun.electric.tool.sc.SilComp;
import com.sun.electric.tool.user.ErrorLogger;
import com.sun.electric.tool.user.MessagesStream;
import com.sun.electric.tool.user.User;
import com.sun.electric.tool.user.menus.ToolMenu;
import com.sun.electric.tool.user.tecEditWizard.TechEditWizardData;
import com.sun.electric.util.TextUtils;

import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

/**
 * Class to test PLA, PadFrame, ROM, Fill, and other generation tests.
 */
public class GenerationTest extends AbstractTest
{
	GenerationTest(String name)
	{
		super(name);
	}

	public static List<AbstractTest> getTests()
	{
		List<AbstractTest> list = new ArrayList<AbstractTest>();
		list.add(new GenerationTest("FillTemplate"));
		list.add(new GenerationTest("FillStitch"));
		list.add(new GenerationTest("PLA"));
		list.add(new GenerationTest("PadFrame1"));
		list.add(new GenerationTest("PadFrame2"));
		list.add(new GenerationTest("ROM"));
		list.add(new GenerationTest("SiliconCompiler1"));
		list.add(new GenerationTest("SiliconCompiler2"));
		list.add(new GenerationTest("TechEditWizard"));
		list.add(new GenerationTest("GateGen"));
		return list;
	}

	public static String getOutputDirectory()
	{
		String rootPath = User.getRegressionPath();
		if (rootPath == null) return null;
		return rootPath + "/tools/Generation/output/";
	}

	/************************************* FillTemplate *********************************************************/

	public Boolean FillTemplate()
	{
		return basicTemplateFillTest(getRegressionPath());
	}

	/**
	 * Method to run Template Fill test. Must be public/static due to regressions.
	 * @param rootPath
	 * @return true if test successfully run.
	 */
	public static Boolean basicTemplateFillTest(String rootPath)
	{
		String trueRootPath = (rootPath != null) ? (rootPath + "/tools/Generation/") : "";
		String outputDir = trueRootPath + "output/";
		ensureOutputDirectory(outputDir);
		MessagesStream.getMessagesStream().save(outputDir + "TemplateFillOut.log");

		int[] cells = {2, 3, 4, 5, 6, 7, 8, 9, 10};
		double drcSpacingRule = 6;
		double vddReserve = drcSpacingRule*2;
		double gndReserve = drcSpacingRule*3;

		// set technology properly
		Technology tech = Technology.getMocmosTechnology();
		setFoundry(tech);

		String rootName = "autoFillTemplateLib";
		FillGenConfig config = new FillGenConfig(tech, FillGeneratorTool.FillTypeEnum.TEMPLATE, rootName,
			FillGeneratorTool.PERIMETER, 2, 6, 100, 200,
			true, cells, true, 0.1, drcSpacingRule, false, false, false, 0, FillGenConfig.FillGenType.INTERNAL, -1);
		config.reserveSpaceOnLayer(tech, 3, vddReserve, FillGeneratorTool.LAMBDA, gndReserve, FillGeneratorTool.LAMBDA);
		config.reserveSpaceOnLayer(tech, 4, vddReserve, FillGeneratorTool.LAMBDA, gndReserve, FillGeneratorTool.LAMBDA);

		FillCellGenJob job = new FillCellGenJob(null, config, true, new LayerCoverageTool.LayerCoveragePreferences(true));

		// compare results by writing results in a different library
		Library lib = Library.findLibrary(rootName);
		boolean sameLib = compareLibraryResults(trueRootPath, rootName, lib,
			new char [] {'H', 'C', 'F', 'R', 'T', 'O', '#'});

		Cell cell = job.getAutoFilLibrary().findNodeProto("gallery");

		// Make sure the foundry/technology are correct
		setFoundry(tech);

		// Running DRC now
		boolean passed = DRCTest.basicDRCLayoutTestInternal(cell, 0, 0, 0, 0, 0, 0, false, DRC.DRCCheckMinArea.AREA_LOCAL);

		// Last messages to print otherwise they might not be seen
		if (!sameLib)
			System.out.println("Error: TemplateFill didn't generate expected results");
		if (!passed)
			System.out.println("Error: TemplateFill didn't pass DRC");

		return Boolean.valueOf(passed && sameLib);
	}

	/************************************* FillStitch *********************************************************/

	public Boolean FillStitch()
	{
		return Boolean.valueOf(basicStitchFillGenTest(getRegressionPath()));
	}

	/**
	 * Method to run Stitch Fill test. Must be public/static due to regressions.
	 * @param rootPath if rootPath is not null, then the data path includes tool path
	 * otherwise assume the regression is running locally
	 * @return true if test successfully run.
	 */
	public static boolean basicStitchFillGenTest(String rootPath)
	{
		String trueRootPath = (rootPath != null) ? (rootPath + "/tools/Generation/") : "";
		String outputDir = trueRootPath + "output/";
		ensureOutputDirectory(outputDir);
		MessagesStream.getMessagesStream().save(outputDir + "StitchFillOut.log");

		int expectedNumCells = 1, foundCells;
		int expectedDRCNumErrors = 0, foundDRCErrors;
		boolean sameLib = false;

		try
		{
			// Check that technology is properly set
			Technology tech = Technology.getMocmosTechnology();
			setFoundry(tech);
			EditingPreferences ep = new EditingPreferences(true, TechPool.getThreadTechPool());

			String libName = "GenerationTests";
			URL fileURL = TextUtils.makeURLToFile(trueRootPath + "data/libs/GenerationTests.jelib");
			Library rootLib = Library.findLibrary(libName);
			if (rootLib == null) rootLib = LibraryFiles.readLibrary(ep, fileURL, libName, FileType.JELIB, true);

			String rootName = "StitchFill";
			String testcell = rootName + "{doc}";
			Cell cell = rootLib.findNodeProto(testcell);
			if (cell == null)
			{
				System.out.println("Cell '" + testcell + "' can't be read in basicStitchFillGenTest");
				return(false); // error reading the cell
			}

			// running the job
			fileURL = TextUtils.makeURLToFile(trueRootPath + "output/" + rootName + ".jelib");
			Library lib = Library.newInstance(rootName, fileURL);
			StitchFillJob job = new StitchFillJob(cell, lib, true);
			List<Cell> list = job.getGeneratedCells();
			foundCells = list.size();
			assert(foundCells == 1);

			// compare results by writing results in a different library
			sameLib = compareLibraryResults(trueRootPath, rootName, lib,
				new char [] {'H', 'C', 'F', 'R', 'T', 'O', '#'});

			// Make sure the foundry/technology are correct
			setFoundry(tech);

			// Running DRC now
			boolean passed = DRCTest.basicDRCLayoutTestInternal(cell, 0, 0, 0, 0, 0, 0, false, DRC.DRCCheckMinArea.AREA_LOCAL);
			foundDRCErrors = (passed) ? 0 : 1; // any positive number would make it
		} catch (Exception e)
		{
			// Catching any time of exception!
			e.printStackTrace();
			return false;
		}

		// 1 warning from gnd, 1 error from vdd
		boolean noErrors = foundCells == expectedNumCells && foundDRCErrors == expectedDRCNumErrors && sameLib;
		System.out.println("Expected results: ");
		System.out.println("\t#cells generated=" + expectedNumCells + "(found="+foundCells+")");
		System.out.println("\t#DRC errors=" + expectedDRCNumErrors + "(found="+foundDRCErrors+")");
		System.out.println("\tGenerated same library?: " + sameLib);
		return noErrors;
	}

	/************************************* PLA *********************************************************/

	public Boolean PLA()
	{
		return Boolean.valueOf(basicPLATest(getRegressionPath()));
	}

	/**
	 * Method to run basic test for PLA. Must be public/static due to regressions.
	 * @param rootPath the top level of the regressions data.
	 * @return true if test successfully run.
	 */
	public static boolean basicPLATest(String rootPath)
	{
		String trueRootPath = (rootPath != null) ? (rootPath + "/tools/Generation/") : "";
		String outputDir = trueRootPath + "output/";
		ensureOutputDirectory(outputDir);
		MessagesStream.getMessagesStream().save(outputDir + "CMOSPLA1Out.log");

		boolean good = true;
		try
		{
			// initialize
			setFoundry(Technology.getMocmosTechnology());

			// Generate PLA
			Library lib = Library.newInstance("CMOSPLA1Result", null);
			String libDir = trueRootPath + "data/libs/";
			PLA.generate(lib, "pla", libDir + "/cmos-pla-and-table", libDir + "/cmos-pla-or-table", true, true, true);

			// reset creation dates for consistent output
			Date zeroDate = new Date(0);
			for(Iterator<Cell> it = lib.getCells(); it.hasNext(); )
			{
				Cell cell = it.next();
				cell.lowLevelSetCreationDate(zeroDate);
				cell.lowLevelSetRevisionDate(zeroDate);
			}
			lib.getDatabase().backup();

			// compare results by writing results in a different library
			if (!compareLibraryResults(trueRootPath, "CMOSPLA1", lib,
				new char [] {'H', 'L', 'F', 'R', 'T', 'O', '#'})) good = false;
		} catch (Exception e)
		{
			// Catching any type of exception
			e.printStackTrace();
			good = false;
		}
		return good;
	}

	/************************************* PadFrame1&2 *********************************************************/

	public Boolean PadFrame1()
	{
		return Boolean.valueOf(basicPadFrameTest(getRegressionPath(), 1));
	}

	public Boolean PadFrame2()
	{
		return Boolean.valueOf(basicPadFrameTest(getRegressionPath(), 2));
	}

	/**
	 * Method to run the PadFrame regression. Must be public/static due to regressions.
	 * @param rootPath
	 * @param testNumber 1 or 2 for the two tests.
	 * @return true if basic pad frame test passes.
	 */
	public static boolean basicPadFrameTest(String rootPath, int testNumber)
	{
		String trueRootPath = (rootPath != null) ? rootPath + "/tools/Generation/" : "";
		String outputDir = trueRootPath + "output/";
		ensureOutputDirectory(outputDir);
		MessagesStream.getMessagesStream().save(outputDir + "PadFrame" + testNumber + "Out.log");
		EditingPreferences ep = new EditingPreferences(true, TechPool.getThreadTechPool());

		boolean good = true;
		try
		{
			// initialize
			setFoundry(Technology.getMocmosTechnology());

			// read library
			String libName = "Generation" + testNumber + "Tests";
			URL fileURL = TextUtils.makeURLToFile(trueRootPath + "data/libs/GenerationTests.jelib");
			Library lib = Library.findLibrary(libName);
			if (lib == null) lib = LibraryFiles.readLibrary(ep, fileURL, libName, FileType.JELIB, true);

			// Generate Pad Frame
			String arrFile = "pads4u.arr";
			if (testNumber == 2) arrFile = "pads4uGapCopy.arr";
			PadGenerator.makePadFrameUseJob(lib, trueRootPath + "data/libs/" + arrFile, null, ep);

			// reset creation date for consistent output
			Cell c = lib.findNodeProto("padframe");
			if (c != null)
			{
				c.lowLevelSetCreationDate(new Date(0));
				c.lowLevelSetRevisionDate(new Date(0));
			}
			lib.getDatabase().backup();

			// write results
			String destLib = outputDir + "PadFrame" + testNumber + "Result.jelib";
			Output.saveJelib(destLib, lib);

			// remove header lines from the library
			String trimmedLib = outputDir + "PadFrame" + testNumber + "ResultPartial.jelib";
			trimLibToCell(destLib, trimmedLib, "padframe");

			// see if the library is as expected
			String expectedLib = trueRootPath + "data/expected/PadFrame" + testNumber + "Result.jelib";
			if (!compareResults(trimmedLib, expectedLib)) good = false;
		} catch (Exception e)
		{
			// Catching any time of exception!
			e.printStackTrace();
			good = false;
		}

		return good;
	}

	/************************************* ROM *********************************************************/

	public Boolean ROM()
	{
		return Boolean.valueOf(basicROMTest(getRegressionPath()));
	}

	/**
	 * Method to run ROM generation test. Must be public/static due to regressions.
	 * @param rootPath if rootPath is not null, then the data path includes tool path
	 * otherwise assume the regression is running locally
	 * @return true if test successfully run.
	 */
	public static boolean basicROMTest(String rootPath)
	{
		String trueRootPath = (rootPath != null) ? rootPath + "/tools/Generation/" : "";
		String outputDir = trueRootPath + "output/";
		ensureOutputDirectory(outputDir);
		MessagesStream.getMessagesStream().save(outputDir + "ROM1Out.log");

		boolean good = true;
		try
		{
			// initialize
			setFoundry(Technology.getMocmosTechnology());

			// generate ROM
			Library lib = Library.newInstance("rom", null);
			EditingPreferences ep = new EditingPreferences(true, lib.getTechPool());
			String libDir = trueRootPath + "data/libs/";
			ROMGenerator.generateROM(lib, libDir + "rom.txt", ep);

			// reset creation dates for consistent output
			Date zeroDate = new Date(0);
			for(Iterator<Cell> it = lib.getCells(); it.hasNext(); )
			{
				Cell cell = it.next();
				cell.lowLevelSetCreationDate(zeroDate);
				cell.lowLevelSetRevisionDate(zeroDate);
			}
			lib.getDatabase().backup();

			// compare results by writing results in a different library
			if (!compareLibraryResults(trueRootPath, "ROM1", lib,
				new char [] {'H', 'L', 'F', 'R', 'T', 'O', '#'})) good = false;
		} catch (Exception e)
		{
			// Catching any time of exception!
			e.printStackTrace();
			good = false;
		}
		return good;
	}

	/************************************* SiliconCompiler1&2 *********************************************************/

	public Boolean SiliconCompiler1()
	{
		return Boolean.valueOf(basicSiliconCompilerTest(getRegressionPath(), 1));
	}

	public Boolean SiliconCompiler2()
	{
		return Boolean.valueOf(basicSiliconCompilerTest(getRegressionPath(), 2));
	}

	/**
	 * Method to run Silicon Compiler test. Must be public/static due to regressions.
	 * @param rootPath if rootPath is not null, then the data path includes tool path
	 * otherwise assume the regression is running locally
	 * @return true if test successfully run.
	 */
	public static boolean basicSiliconCompilerTest(String rootPath, int testNumber)
	{
		String trueRootPath = (rootPath != null) ? rootPath + "/tools/Generation/" : "";
		String outputDir = trueRootPath + "output/";
		ensureOutputDirectory(outputDir);
		MessagesStream.getMessagesStream().save(outputDir + "SiliconCompilation" + testNumber + "Out.log");

		boolean good = true;
		try
		{
			// initialize
			setFoundry(Technology.getMocmosTechnology());
			EditingPreferences ep = new EditingPreferences(true, TechPool.getThreadTechPool());

			// read library
			String libName = "GenerationTests";
			URL fileURL = TextUtils.makeURLToFile(trueRootPath + "data/libs/GenerationTests.jelib");
			Library lib = Library.findLibrary(libName);
			if (lib == null) lib = LibraryFiles.readLibrary(ep, fileURL, libName, FileType.JELIB, true);

			// do silicon compilation
			String trimStr;
			if (testNumber == 1)
			{
				trimStr = "ACC;";
				Cell lay = lib.findNodeProto("tool-SiliconCompiler{vhdl}");
				SilComp.SilCompPrefs prefs = new SilComp.SilCompPrefs(true, Technology.getMocmosTechnology().getTechName());
				ToolMenu.doSiliconCompilation(lay, true, prefs, ep);

				// write results
				String destLibA = outputDir + "SiliconCompilation1ResultA.jelib";
				Output.saveJelib(destLibA, lib);
			} else
			{
				trimStr = "adder4;";
				Cell lay = lib.findNodeProto("adder4{vhdl}");
				SilComp.SilCompPrefs prefs = new SilComp.SilCompPrefs(true, Technology.getMocmosTechnology().getTechName());
				ToolMenu.doSilCompActivityNoJob(lay, ToolMenu.COMPILE_VHDL_FOR_SC, prefs, true);
			}

			// reset creation dates for consistent output
			Date zeroDate = new Date(0);
			for(Iterator<Cell> it = lib.getCells(); it.hasNext(); )
			{
				Cell cell = it.next();
				cell.lowLevelSetCreationDate(zeroDate);
				cell.lowLevelSetRevisionDate(zeroDate);
			}
			lib.getDatabase().backup();

			// write results
			String destLib = outputDir + "SiliconCompilation" + testNumber + "Result.jelib";
			Output.saveJelib(destLib, lib);

			// remove header lines from the library
			String trimmedLib = outputDir + "SiliconCompilation" + testNumber + "ResultPartial.jelib";
			trimLibToCell(destLib, trimmedLib, trimStr);

			// see if the library is as expected
			String expectedLib = trueRootPath + "data/expected/SiliconCompilation" + testNumber + "Result.jelib";
			if (!compareResults(trimmedLib, expectedLib)) good = false;
		} catch (Exception e)
		{
			// Catching any time of exception!
			e.printStackTrace();
			good = false;
		}
		return good;
	}

	/************************************* TechEditWizard *********************************************************/

	public Boolean TechEditWizard()
	{
		return Boolean.valueOf(basicTechEditWizardTest(getRegressionPath()));
	}

	/**
	 * Method to run TechEditWizard test. Must be public/static due to regressions.
	 * @param rootPath if rootPath is not null, then the data path includes tool path
	 * otherwise assume the regression is running locally
	 * @return true if test successfully run.
	 */
	public static boolean basicTechEditWizardTest(String rootPath)
	{
		String trueRootPath = (rootPath != null) ? rootPath + "/tools/Generation/" : "";
		String outputDir = trueRootPath + "output/";
		ensureOutputDirectory(outputDir);
		MessagesStream.getMessagesStream().save(outputDir + "TechEditWizardOut.log");

		boolean good = true;
		try
		{
			// initialize
			setFoundry(Technology.getMocmosTechnology());

			TechEditWizardData data = new TechEditWizardData();

			// Open the text file
			String destLib = trueRootPath + "output/MoCmos.xml";
			data.importDataFromWizardFormat(trueRootPath + "data/libs/mocmos.txt");

			// Write the file
			data.dumpXMLFile(destLib);

			// remove header lines from the library
			String trimmedLib = trueRootPath + "output/MoCmosPartial.xml";
			removeLines(destLib, trimmedLib, " *"); // remove license

			// see if the library is as expected
			String expectedLib = trueRootPath + "data/expected/TechEditWizardMoCmos.xml";
			if (!compareResults(trimmedLib, expectedLib)) good = false;
		} catch (Exception e)
		{
			// Catching any time of exception!
			e.printStackTrace();
			good = false;
		}
		return good;
	}

	/************************************* GateGen *********************************************************/

	public Boolean GateGen()
	{
		return Boolean.valueOf(basicGateGenerationTest(getRegressionPath()));
	}

	/**
	 * Method to run Gate Generator test. Must be public/static due to regressions.
	 * @return true if test successfully run.
	 */
	public static boolean basicGateGenerationTest(String rootPath)
	{
		String trueRootPath = (rootPath != null) ? (rootPath + "/tools/Generation/") : "";
		String outputDir = trueRootPath + "output/";
		ensureOutputDirectory(outputDir);
		MessagesStream.getMessagesStream().save(outputDir + "GateGen.log");

		int numErrs = 0;
		boolean sameLib = false;
		try
		{
			String libName = trueRootPath + "data/libs/purpleFour.jelib";
			System.out.println("Opening " + libName);

			EditingPreferences ep = new EditingPreferences(true, TechPool.getThreadTechPool());
			LayoutLib.openLibForRead(libName, ep, true);
			Library scratchLib = Library.newInstance("GateGenScratch", TextUtils.makeURLToFile(outputDir + "GateGenScratch.jelib"));

			// Check that technology is properly set
			Technology tech = Technology.getMocmosTechnology();
			tech = setFoundry(tech);
			GateRegression.runRegression(tech, scratchLib, IOTool.getBackupRedundancy(), ep);
			sameLib = compareLibraryResults(trueRootPath, scratchLib.getName(), scratchLib,
				new char [] {'H', 'C', 'F', 'R', 'T', 'O', '#'});

			// Running now DRC on those new cells
			Cell gallery = scratchLib.findNodeProto("gallery{lay}");
			DRC.DRCPreferences dp = new DRC.DRCPreferences(true);
			dp.ignoreAreaCheck = true; // no min area
			dp.ignoreExtensionRuleChecking = true;
			ErrorLogger errorLog = Quick.checkDesignRules(dp, gallery, null, null);
			errorLog.termLogging(true);
			numErrs = errorLog.getNumErrors();
		} catch (Exception e)
		{
			System.out.println("exception: "+e);
			e.printStackTrace();
			return false;
		}
		boolean cleanDRC = numErrs == 0;
		if (!sameLib)
			System.out.println("ERROR: Library generated is different from the reference");
		else if (!cleanDRC)
			System.out.println("ERROR: DRC errors in basicGateGenerationTest");

		return sameLib && cleanDRC;
	}
}
