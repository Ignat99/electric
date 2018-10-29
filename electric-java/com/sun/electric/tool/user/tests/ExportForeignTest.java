/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: ExportForeignTest.java
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
import com.sun.electric.database.hierarchy.EDatabase;
import com.sun.electric.database.hierarchy.Library;
import com.sun.electric.database.variable.VarContext;
import com.sun.electric.database.variable.Variable;
import com.sun.electric.technology.TechPool;
import com.sun.electric.technology.Technology;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.generator.layout.LayoutLib;
import com.sun.electric.tool.io.FileType;
import com.sun.electric.tool.io.input.LibraryFiles;
import com.sun.electric.tool.io.output.DXF;
import com.sun.electric.tool.io.output.EDIF.EDIFPreferences;
import com.sun.electric.tool.io.output.Output;
import com.sun.electric.tool.io.output.Output.OutputPreferences;
import com.sun.electric.tool.io.output.Spice;
import com.sun.electric.tool.io.output.Telesis.TelesisPreferences;
import com.sun.electric.tool.io.output.Verilog;
import com.sun.electric.tool.lang.EvalJavaBsh;
import com.sun.electric.tool.simulation.SimulationTool;
import com.sun.electric.tool.user.MessagesStream;
import com.sun.electric.tool.user.User;
import com.sun.electric.tool.user.UserInterfaceMain;
import com.sun.electric.tool.user.menus.FileMenu;
import com.sun.electric.tool.user.projectSettings.ProjSettings;
import com.sun.electric.util.TextUtils;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Class to test foreign-file export.
 */
public class ExportForeignTest extends AbstractTest
{
	public ExportForeignTest(String name)
	{
		super(name);
	}

	public ExportForeignTest(String name, boolean interactive)
	{
		super(name, false, interactive);
	}

	public static List<AbstractTest> getTests()
	{
		List<AbstractTest> list = new ArrayList<AbstractTest>();
		list.add(new ExportForeignTest("CIF1"));
		list.add(new ExportForeignTest("CIF2"));
		list.add(new ExportForeignTest("DXF"));
		list.add(new ExportForeignTest("EAGLE"));
		list.add(new ExportForeignTest("ECAD"));
		list.add(new ExportForeignTest("EDIF"));
		list.add(new ExportForeignTest("ESIM"));
		list.add(new ExportForeignTest("FastHenry"));
		list.add(new ExportForeignTest("GDS"));
		list.add(new ExportForeignTest("L"));
		list.add(new ExportForeignTest("LEFDEF"));
		list.add(new ExportForeignTest("Mossim"));
		list.add(new ExportForeignTest("PADS"));
		list.add(new ExportForeignTest("PAL"));
		list.add(new ExportForeignTest("PNG", true));
		list.add(new ExportForeignTest("Silos"));
		list.add(new ExportForeignTest("Spice1"));
		list.add(new ExportForeignTest("Spice2"));
		list.add(new ExportForeignTest("Spice3"));
		if (EvalJavaBsh.hasBeanShell())
		{
			list.add(new ExportForeignTest("Spice4"));
			list.add(new ExportForeignTest("Spice4_C"));
		}
		list.add(new ExportForeignTest("SpiceBus"));
		list.add(new ExportForeignTest("STL"));
		list.add(new ExportForeignTest("SVG"));
		list.add(new ExportForeignTest("Tegas"));
		list.add(new ExportForeignTest("Telesis"));
		list.add(new ExportForeignTest("Verilog1"));
		list.add(new ExportForeignTest("Verilog2"));
		list.add(new ExportForeignTest("Verilog3"));
		list.add(new ExportForeignTest("Verilog4"));
		list.add(new ExportForeignTest("Verilog5"));
		list.add(new ExportForeignTest("Verilog6"));
		return list;
	}

	public static String getOutputDirectory()
	{
		String rootPath = User.getRegressionPath();
		if (rootPath == null) return null;
		return rootPath + "/tools/Database/output/";
	}

	public Boolean CIF1()
	{
		return exportGDS_CIF("cif", getRegressionPath()+"/data/qThree", "qThreeTop.jelib", "qThreeTop", getRegressionPath()+"/tools/IO/output/", 750, 0);
	}

	public Boolean CIF2()
	{
		return exportGDS_CIF("cif", getRegressionPath()+"/data/muddChip", "MIPS.jelib", "chip", getRegressionPath()+"/tools/IO/output/", 5748, 0);
	}

	public Boolean DXF()
	{
		return runExportTest(getRegressionPath(), "DXFOut.log", "DXFTest1.dxf", "DXFTest", FileType.DXF);
	}

	public Boolean EAGLE()
	{
		return runExportTest(getRegressionPath(), "EAGLEOut.log", "EagleTest1.txt", "EaglePadsECADTest{sch}", FileType.EAGLE);
	}

	public Boolean ECAD()
	{
		return runExportTest(getRegressionPath(), "ECADOut.log", "ECADTest1.enl", "EaglePadsECADTest{sch}", FileType.ECAD);
	}

	public Boolean EDIF()
	{
		return runExportTest(getRegressionPath(), "EDIFOut.log", "EDIFTest1.edif", "Bus{sch}", FileType.EDIF);
	}

	public Boolean ESIM()
	{
		return runExportTest(getRegressionPath(), "ESIMOut.log", "SimTest1.sim", "SimTest{lay}", FileType.ESIM);
	}

	public Boolean FastHenry()
	{
		return runExportTest(getRegressionPath(), "FastHenryOut.log", "FastHenryTest1.inp", "FastHenryTest{lay}", FileType.FASTHENRY);
	}

	public Boolean GDS()
	{
		return exportGDS_CIF("gds", getRegressionPath()+"/tools/IO/data/libs", "simpleDRCTest.jelib", "NoErrorsOnPins", getRegressionPath()+"/tools/IO/output/", 0, 0);
	}

	public Boolean L()
	{
		return runExportTest(getRegressionPath(), "LOut.log", "LTest1.L", "LTest{lay}", FileType.L);
	}

	public Boolean LEFDEF()
	{
		Boolean result1 = runExportTest(getRegressionPath(), "LEFOut.log", "LEFTest1.lef", "LEFTest{lay}", FileType.LEF);
		Boolean result2 = runExportTest(getRegressionPath(), "DEFOut.log", "DEFTest1.lef", "LEFTest{lay}", FileType.DEF);
		return Boolean.valueOf(result1.booleanValue() && result2.booleanValue());
	}

	public Boolean Mossim()
	{
		return runExportTest(getRegressionPath(), "MossimOut.log", "MOSSIMTest1.ntk", "MOSSIMTest{lay}", FileType.MOSSIM);
	}

	public Boolean PADS()
	{
		return runExportTest(getRegressionPath(), "PADSOut.log", "PadsTest1.asc", "EaglePadsECADTest{sch}", FileType.PADS);
	}

	public Boolean PAL()
	{
		return runExportTest(getRegressionPath(), "PALOut.log", "PalTest1.pal", "PalTest{sch}", FileType.PAL);
	}

	public Boolean PNG()
	{
		// do not run in regressions...needs display
		if (!(Job.getExtendedUserInterface() instanceof UserInterfaceMain)) return Boolean.TRUE;

		return runExportTestFromMenu(getRegressionPath(), "PNGOut.log", "PNGTest1.png", "PNGTest1{lay}", null);
	}

	public Boolean Silos()
	{
		return runExportTest(getRegressionPath(), "SilosOut.log", "SilosTest1.sil", "SilosTest{sch}", FileType.SILOS);
	}

	public Boolean Spice1()
	{
		return basicSpice(getRegressionPath(), "SpiceOut1.log", "SpiceHOut1.spi", "SpiceTest1{lay}",
			SimulationTool.SpiceEngine.SPICE_ENGINE_H, SimulationTool.SpiceParasitics.RC_CONSERVATIVE,
			SimulationTool.SpiceGlobal.USEGLOBALBLOCK, false, false);
	}

	public Boolean Spice2()
	{
		return basicSpice(getRegressionPath(), "SpiceOut2.log", "SpiceHOut2.spi", "SpiceTest2{sch}",
			SimulationTool.SpiceEngine.SPICE_ENGINE_H, SimulationTool.SpiceParasitics.RC_CONSERVATIVE,
			SimulationTool.SpiceGlobal.USEGLOBALBLOCK, false, false);
	}

	public Boolean Spice3()
	{
		return basicSpice(getRegressionPath(), "SpiceOut3.log", "Spice3Out1.spi", "SpiceTest2{sch}",
			SimulationTool.SpiceEngine.SPICE_ENGINE_3, SimulationTool.SpiceParasitics.RC_CONSERVATIVE,
			SimulationTool.SpiceGlobal.USESUBCKTPORTS, false, false);
	}

	public Boolean Spice4()
	{
		return basicSpice(getRegressionPath(), "SpiceOut4.log", "SpiceHOut4.spi", "SpiceTest4{sch}",
			SimulationTool.SpiceEngine.SPICE_ENGINE_H, SimulationTool.SpiceParasitics.RC_CONSERVATIVE,
			SimulationTool.SpiceGlobal.USEGLOBALBLOCK, false, false);
	}

	public Boolean Spice4_C()
	{
		return basicSpice(getRegressionPath(), "SpiceHOut4_c.log", "SpiceHOut4_c.spi", "SpiceTest4{sch}",
			SimulationTool.SpiceEngine.SPICE_ENGINE_H, SimulationTool.SpiceParasitics.RC_CONSERVATIVE,
			SimulationTool.SpiceGlobal.USEGLOBALBLOCK, false, true);
	}

	public Boolean SpiceBus()
	{
		return basicSpice(getRegressionPath(), "SpiceBus.log", "SpiceBus.spi", "Bus{sch}",
			SimulationTool.SpiceEngine.SPICE_ENGINE_H, SimulationTool.SpiceParasitics.RC_CONSERVATIVE,
			SimulationTool.SpiceGlobal.USESUBCKTPORTS, false, false);
	}

	public Boolean STL()
	{
		return runExportTest(getRegressionPath(), "STLOut.log", "STLTest.stl", "STLTest{lay}", FileType.STL);
	}

	public Boolean SVG()
	{
		return runExportTest(getRegressionPath(), "SVGOut.log", "SVG1.svg", "SVGTest1{lay}", FileType.SVG);
	}

	public Boolean Tegas()
	{
		return runExportTest(getRegressionPath(), "TegasOut.log", "TegasTest1.tdl", "TegasTest{sch}", FileType.TEGAS);
	}

	public Boolean Telesis()
	{
		return runExportTest(getRegressionPath(), "TelesisOut.log", "TelesisTest.txt", "TelesisTest{sch}", FileType.TELESIS);
	}

	public Boolean Verilog1()
	{
		return runExportTest(getRegressionPath(), "Verilog1Out.log", "VerilogFile1.v", "VerilogTest1{sch}", FileType.VERILOG);
	}

	public Boolean Verilog2()
	{
		return runExportTest(getRegressionPath(), "Verilog2Out.log", "VerilogFile2.v", "VerilogTest2{sch}", FileType.VERILOG);
	}

	public Boolean Verilog3()
	{
		return runExportTest(getRegressionPath(), "Verilog3Out.log", "VerilogFile3.v", "VerilogTest3{sch}", FileType.VERILOG);
	}

	public Boolean Verilog4()
	{
		return runExportTest(getRegressionPath(), "Verilog4Out.log", "VerilogFile4.v", "VerilogTest4{sch}", FileType.VERILOG);
	}

	public Boolean Verilog5()
	{
		return runExportTest(getRegressionPath(), "Verilog5Out.log", "VerilogFile5.v", "VerilogTest5{sch}", FileType.VERILOG);
	}

	public Boolean Verilog6()
	{
		return runExportTest(getRegressionPath(), "Verilog6Out.log", "VerilogFile6.v",
			"VerilogTest6WithVeryLongNameNowIsTheTimeForAllGoodMenToComeToTheAidOfTheParty-1-nowIsTheTimeForAllGoodMenToComeToTheAidOfTheParty-2{lay}",
			FileType.VERILOG);
	}

	/************************************* SUPPORT *********************************************/

	/**
	 * Method must be public due to the regressions
	 */
	public static Boolean exportGDS_CIF(String type, String regName, String libName, String cellName, int expectedE, int expectedW)
	{
		return exportGDS_CIF(type, regName, libName, cellName, "", expectedE, expectedW);
	}

	private static Boolean exportGDS_CIF(String type, String regName, String libName, String cellName, String outputDir, int expectedE, int expectedW)
	{
		FileType theType = FileType.findType(type);

		ensureOutputDirectory(outputDir);
		MessagesStream.getMessagesStream().save(outputDir + type + "LogFile.log");

		try
		{
			EditingPreferences ep = new EditingPreferences(true, TechPool.getThreadTechPool());
			Library rootLib = LayoutLib.openLibForRead(regName + "/" + libName, ep, true);
			Cell lay = rootLib.findNodeProto(cellName + "{lay}");

			OutputPreferences op = Output.getOutputPreferences(theType, null, true, null);
			op.disablePopups();
			Output out = op.doOutput(lay, VarContext.globalContext, outputDir + "output." + type);
			if (out != null)
			{
				System.out.println("Got " + out.getNumErrors() + " errors and " + out.getNumWarnings() + " warnings");
				if (expectedE != out.getNumErrors() || expectedW != out.getNumWarnings())
				{
					System.out.println("BUT EXPECTED " + expectedE + " errors and " + expectedW + " warnings");
					return Boolean.FALSE;
				}
			}
			return Boolean.TRUE;
		} catch (Exception e)
		{
			System.out.println("Exception: " + e);
			e.printStackTrace();
			return Boolean.FALSE;
		}
	}

	private static Boolean basicSpice(String rootPath, String logFile, String outputFile, String cellToWrite,
		SimulationTool.SpiceEngine engine, SimulationTool.SpiceParasitics parasitics, SimulationTool.SpiceGlobal global, boolean noResistors, boolean useCellParams)
	{
		Spice.SpicePreferences sp = new Spice.SpicePreferences(true, false);
		sp.engine = engine;
		sp.ignoreParasiticResistors = noResistors;
		sp.parasiticsLevel = parasitics;
		sp.globalTreatment = global;
		sp.useCellParameters = useCellParams;
		return runExportTestFromMenu(rootPath, logFile, outputFile, cellToWrite, sp);
	}

	private static Boolean runExportTest(String rootPath, String logFile, String outputFile, String cellToWrite, FileType type)
	{
		return runExportTestFromMenu(rootPath, logFile, outputFile, cellToWrite, Output.getOutputPreferences(type, null, true, null));
	}

	private static Boolean runExportTestFromMenu(String rootPath, String logFile, String outputFile, String cellToWrite, Output.OutputPreferences op)
	{
		if (op != null) op.disablePopups();
		String trueRootPath = (rootPath != null) ? (rootPath + "/tools/IO/") : "";
		return runExportTestInternal(trueRootPath, "data/libs", "netlistTests.jelib", logFile,
			outputFile, cellToWrite, op, Technology.getMocmosTechnology());
	}

	/**
	 * Public because it is used in the regressions
	 */
	public static Boolean runExportTest(String libPath, String libName, String logFile, String outputFile,
		String cellToWrite, FileType type, String spiceModel, String layTech, String spiceFlags, String spiceLevel, String spiceGlobal)
	{
		Output.OutputPreferences op = Output.getOutputPreferences(type, null, true, null);
		if (type == FileType.SPICE)
		{
			Spice.SpicePreferences sp = (Spice.SpicePreferences)op;
			if (spiceModel != null && !spiceModel.equals(""))
				sp.engine = SimulationTool.SpiceEngine.valueOf(spiceModel);

			// handle any flags to change preferences
			if (spiceFlags.contains("p")) sp.ignoreParasiticResistors = true;
			if (spiceFlags.contains("c")) sp.useCellParameters = true;
			if (!spiceLevel.equals("")) sp.parasiticsLevel = SimulationTool.SpiceParasitics.valueOf(spiceLevel);

			// Dealing with Spice global setting
			if (!spiceGlobal.equals("")) sp.globalTreatment = SimulationTool.SpiceGlobal.valueOf(spiceGlobal);
		}

		return runExportTestInternal("", libPath, libName, logFile, outputFile, cellToWrite, op,
			Technology.findTechnology(layTech));
	}

	private static Boolean runExportTestInternal(String trueRootPath, String libPath, String libName, String logFile,
		String outputFile, String cellToWrite, Output.OutputPreferences op, Technology layTech)
	{
		String outputDir = trueRootPath + "output/";
		ensureOutputDirectory(outputDir);
		MessagesStream.getMessagesStream().save(outputDir + logFile);
		ProjSettings.readSettings(new File(trueRootPath + "data/projsettings_" + layTech.getTechName() + ".xml"), EDatabase.currentDatabase(), true, true);
		EditingPreferences ep = new EditingPreferences(true, TechPool.getThreadTechPool());
		String destFile = outputDir + outputFile;
		String expectedFilePath = trueRootPath + "data/expected/" + outputFile;

		Boolean good = Boolean.TRUE;
		try
		{
			// read library
			URL fileURL = TextUtils.makeURLToFile(trueRootPath + libPath + "/" + libName);
			Library lib = Library.findLibrary(libName);
			if (lib == null) lib = LibraryFiles.readLibrary(ep, fileURL, null, FileType.JELIB, true);
			if (lib == null)
			{
				System.out.println("Error loading " + fileURL.getFile());
				return Boolean.FALSE;
			}
			Library.repairAllLibraries(ep);
			Job.setCurrentLibraryInJob(lib);
			Cell cell = lib.findNodeProto(cellToWrite);
			String destFileOrdir = destFile;

			if (op == null)
			{
				// PNG format has no OutputPreferences
				new FileMenu.ExportImage(cell, null, destFile, true);

				// compare results
				if (!compareImages(-1, destFile, expectedFilePath)) good = Boolean.FALSE;
			} else
			{
				if (op instanceof Spice.SpicePreferences) {
					((Spice.SpicePreferences)op).modelFiles = convertVarsToModelFiles(Spice.SPICE_MODEL_FILE_KEY);
				} else if (op instanceof Verilog.VerilogPreferences) {
					((Verilog.VerilogPreferences)op).modelFiles = convertVarsToModelFiles(Verilog.VERILOG_BEHAVE_FILE_KEY);
				} else if (op instanceof DXF.DXFPreferences) {
					((DXF.DXFPreferences)op).tech = Technology.getMocmosTechnology();
				} else if (op instanceof TelesisPreferences) {
					destFileOrdir = outputDir; // the name is taken from the cell name
				} else if (op instanceof EDIFPreferences) {
					((EDIFPreferences)op).writeTime = false;
				}
				op.includeDateAndVersionInOutput = false;

				// Generate output
				if (cell != null)
				{
					Output out = op.doOutput(cell, null, destFileOrdir, ep);
					System.out.println("Errors writing: " + out.getNumErrors());
					System.out.println("Warnings writing: " + out.getNumWarnings());
				}

				// see if the output is as expected
				if (!compareResults(destFile, expectedFilePath)) good = Boolean.FALSE;
			}
		} catch (Exception e)
		{
			// Catching any type of exception
			e.printStackTrace();
			good = Boolean.FALSE;
		}
		return good;
	}

	private static Map<Cell,String> convertVarsToModelFiles(Variable.Key varKey)
	{
		HashMap<Cell,String> m = new HashMap<Cell,String>();
		for (Iterator<Library> lit = Library.getLibraries(); lit.hasNext(); )
		{
			Library lib = lit.next();
			for (Iterator<Cell> it = lib.getCells(); it.hasNext(); )
			{
				Cell cell = it.next();
				String unfilteredModelFile = cell.getVarValue(varKey, String.class);
				if (unfilteredModelFile != null && unfilteredModelFile.length() > 0)
					m.put(cell, unfilteredModelFile);
			}
		}
		return m;
	}
}
