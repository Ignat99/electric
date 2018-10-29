	/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: ImportForeignTest.java
 *
 * Copyright (c) 2010, Static Free Software. All rights reserved.
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
import com.sun.electric.database.id.CellId;
import com.sun.electric.database.id.CellUsage;
import com.sun.electric.technology.TechPool;
import com.sun.electric.technology.Technology;
import com.sun.electric.technology.technologies.Artwork;
import com.sun.electric.technology.technologies.MoCMOS;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.io.FileType;
import com.sun.electric.tool.io.input.CIF;
import com.sun.electric.tool.io.input.DEF;
import com.sun.electric.tool.io.input.DXF;
import com.sun.electric.tool.io.input.EDIF;
import com.sun.electric.tool.io.input.GDS;
import com.sun.electric.tool.io.input.Gerber;
import com.sun.electric.tool.io.input.Input;
import com.sun.electric.tool.io.input.LEF;
import com.sun.electric.tool.io.input.Sue;
import com.sun.electric.tool.io.input.bookshelf.Bookshelf;
import com.sun.electric.tool.lang.EvalJavaBsh;
import com.sun.electric.tool.user.MessagesStream;
import com.sun.electric.tool.user.User;
import com.sun.electric.util.TextUtils;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Class to test the foreign-file import facilities.
 */
public class ImportForeignTest extends AbstractTest
{
	public ImportForeignTest(String name)
	{
		super(name);
	}

	public static List<AbstractTest> getTests()
	{
		List<AbstractTest> list = new ArrayList<AbstractTest>();
		list.add(new ImportForeignTest("Bookshelf"));

		list.add(new ImportForeignTest("CIF1"));
		list.add(new ImportForeignTest("CIF2"));
		list.add(new ImportForeignTest("CIF3"));
		list.add(new ImportForeignTest("CIF4"));

		list.add(new ImportForeignTest("DXF1"));
		list.add(new ImportForeignTest("DXF2"));
		list.add(new ImportForeignTest("DXF3"));
		list.add(new ImportForeignTest("DXF4"));
		list.add(new ImportForeignTest("DXF5"));
		list.add(new ImportForeignTest("DXF6"));

		list.add(new ImportForeignTest("EDIF1"));
		list.add(new ImportForeignTest("EDIF2"));
		list.add(new ImportForeignTest("EDIF3"));
		list.add(new ImportForeignTest("EDIF4"));
		list.add(new ImportForeignTest("EDIF5"));

		list.add(new ImportForeignTest("GDS1"));
		list.add(new ImportForeignTest("GDS2"));
		list.add(new ImportForeignTest("GDS3"));
		list.add(new ImportForeignTest("GDS4"));

		list.add(new ImportForeignTest("Gerber"));

		list.add(new ImportForeignTest("LEFDEF1"));
		list.add(new ImportForeignTest("LEFDEF2"));

		if (EvalJavaBsh.hasBeanShell())
		{
			list.add(new ImportForeignTest("SUE1"));
			list.add(new ImportForeignTest("SUE2"));
			list.add(new ImportForeignTest("SUE3"));
		}
		return list;
	}

	public static String getOutputDirectory()
	{
		String rootPath = User.getRegressionPath();
		if (rootPath == null) return null;
		return rootPath + "/tools/IO/output/";
	}

	/***************************************** Bookshelf *****************************************/

	public Boolean Bookshelf() { return Boolean.valueOf(basicBOOKSHELF1(getRegressionPath(), getStarterJob())); }
	private static boolean basicBOOKSHELF1(String rootPath, Job job)
	{
		Bookshelf.BookshelfPreferences bp1 = new Bookshelf.BookshelfPreferences(true);
		return runImportTest(rootPath, "ImportBOOKSHELF-1", null, MoCMOS.getMocmosTechnology(), job, false,
			".aux", null, FileType.BOOKSHELF, null, bp1, null);
	}

	/***************************************** CIF *****************************************/

	public Boolean CIF1() { return Boolean.valueOf(basicCIF1(getRegressionPath(), getStarterJob())); }
	private static boolean basicCIF1(String rootPath, Job job)
	{
		CIF.CIFPreferences ip = new CIF.CIFPreferences(true);
		return runImportTest(rootPath, "ImportCIF-1", null, Technology.findTechnology("nmos"), job, true,
			".cif", null, FileType.CIF, null, ip, null);
	}

	public Boolean CIF2() { return Boolean.valueOf(basicCIF2(getRegressionPath(), getStarterJob())); }
	private static boolean basicCIF2(String rootPath, Job job)
	{
		CIF.CIFPreferences ip = new CIF.CIFPreferences(true);
		return runImportTest(rootPath, "ImportCIF-2", null, MoCMOS.getMocmosTechnology(), job, true,
			".cif", null, FileType.CIF, null, ip, null);
	}

	public Boolean CIF3() { return Boolean.valueOf(basicCIF3(getRegressionPath(), getStarterJob())); }
	private static boolean basicCIF3(String rootPath, Job job)
	{
		CIF.CIFPreferences ip = new CIF.CIFPreferences(true);
		return runImportTest(rootPath, "ImportCIF-3", null, MoCMOS.getMocmosTechnology(), job, true,
			".cif", null, FileType.CIF, null, ip, null);
	}

	public Boolean CIF4() { return Boolean.valueOf(basicCIF4(getRegressionPath(), getStarterJob())); }
	private static boolean basicCIF4(String rootPath, Job job)
	{
		CIF.CIFPreferences ip = new CIF.CIFPreferences(true);
		return runImportTest(rootPath, "ImportCIF-4", null, MoCMOS.getMocmosTechnology(), job, true,
			".cif", null, FileType.CIF, null, ip, null);
	}

	/***************************************** DXF *****************************************/

	public Boolean DXF1() { return Boolean.valueOf(basicDXF1(getRegressionPath(), getStarterJob())); }
	private static boolean basicDXF1(String rootPath, Job job)
	{
		DXF.DXFPreferences ip = new DXF.DXFPreferences(true);
		return runImportTest(rootPath, "ImportDXF-1", null, Artwork.tech(), job, true,
			".dxf", null, FileType.DXF, null, ip, null);
	}

	public Boolean DXF2() { return Boolean.valueOf(basicDXF2(getRegressionPath(), getStarterJob())); }
	private static boolean basicDXF2(String rootPath, Job job)
	{
		DXF.DXFPreferences ip = new DXF.DXFPreferences(true);
		return runImportTest(rootPath, "ImportDXF-2", null, null, job, true,
			".dxf", null, FileType.DXF, null, ip, null);
	}

	public Boolean DXF3() { return Boolean.valueOf(basicDXF3(getRegressionPath(), getStarterJob())); }
	private static boolean basicDXF3(String rootPath, Job job)
	{
		DXF.DXFPreferences ip = new DXF.DXFPreferences(true);
		return runImportTest(rootPath, "ImportDXF-3", null, Artwork.tech(), job, true,
			".dxf", null, FileType.DXF, null, ip, null);
	}

	public Boolean DXF4() { return Boolean.valueOf(basicDXF4(getRegressionPath(), getStarterJob())); }
	private static boolean basicDXF4(String rootPath, Job job)
	{
		DXF.DXFPreferences ip = new DXF.DXFPreferences(true);
		return runImportTest(rootPath, "ImportDXF-4", null, Artwork.tech(), job, true,
			".dxf", null, FileType.DXF, null, ip, null);
	}

	public Boolean DXF5() { return Boolean.valueOf(basicDXF5(getRegressionPath(), getStarterJob())); }
	private static boolean basicDXF5(String rootPath, Job job)
	{
		DXF.DXFPreferences ip = new DXF.DXFPreferences(true);
		return runImportTest(rootPath, "ImportDXF-5", null, Artwork.tech(), job, true,
			".dxf", null, FileType.DXF, null, ip, null);
	}

	public Boolean DXF6() { return Boolean.valueOf(basicDXF6(getRegressionPath(), getStarterJob())); }
	private static boolean basicDXF6(String rootPath, Job job)
	{
		DXF.DXFPreferences ip = new DXF.DXFPreferences(true);
		return runImportTest(rootPath, "ImportDXF-6", null, Artwork.tech(), job, true,
			".dxf", null, FileType.DXF, null, ip, null);
	}

	/***************************************** EDIF *****************************************/

	public Boolean EDIF1() { return Boolean.valueOf(basicEDIF1(getRegressionPath(), getStarterJob())); }
	private static boolean basicEDIF1(String rootPath, Job job)
	{
		EDIF.EDIFPreferences ip = new EDIF.EDIFPreferences(true);
		return runImportTest(rootPath, "ImportEDIF-1", null, MoCMOS.getMocmosTechnology(), job, false,
			".edif", null, FileType.EDIF, null, ip, null);
	}

	public Boolean EDIF2() { return Boolean.valueOf(basicEDIF2(getRegressionPath(), getStarterJob())); }
	private static boolean basicEDIF2(String rootPath, Job job)
	{
		EDIF.EDIFPreferences ip = new EDIF.EDIFPreferences(true);
		return runImportTest(rootPath, "ImportEDIF-2", null, MoCMOS.getMocmosTechnology(), job, false,
			".edif", null, FileType.EDIF, null, ip, null);
	}

	public Boolean EDIF3() { return Boolean.valueOf(basicEDIF3(getRegressionPath(), getStarterJob())); }
	private static boolean basicEDIF3(String rootPath, Job job)
	{
		EDIF.EDIFPreferences ip = new EDIF.EDIFPreferences(true);
		ip.cadenceCompatibility = false;
		return runImportTest(rootPath, "ImportEDIF-3", null, MoCMOS.getMocmosTechnology(), job, false,
			".edif", null, FileType.EDIF, null, ip, null);
	}

	public Boolean EDIF4() { return Boolean.valueOf(basicEDIF4(getRegressionPath(), getStarterJob())); }
	private static boolean basicEDIF4(String rootPath, Job job)
	{
		EDIF.EDIFPreferences ip = new EDIF.EDIFPreferences(true);
		ip.cadenceCompatibility = false;
		return runImportTest(rootPath, "ImportEDIF-4", null, MoCMOS.getMocmosTechnology(), job, false,
			".edif", null, FileType.EDIF, null, ip, null);
	}

	public Boolean EDIF5() { return Boolean.valueOf(basicEDIF5(getRegressionPath(), getStarterJob())); }
	private static boolean basicEDIF5(String rootPath, Job job)
	{
		EDIF.EDIFPreferences ip = new EDIF.EDIFPreferences(true);
		ip.cadenceCompatibility = false;
		return runImportTest(rootPath, "ImportEDIF-5", null, MoCMOS.getMocmosTechnology(), job, false,
			".edif", null, FileType.EDIF, null, ip, null);
	}

	public Boolean EDIF6() { return Boolean.valueOf(basicEDIF6(getRegressionPath(), getStarterJob())); }
	private static boolean basicEDIF6(String rootPath, Job job)
	{
		EDIF.EDIFPreferences ip = new EDIF.EDIFPreferences(true);
		return runImportTest(rootPath, "ImportEDIF-6", null, MoCMOS.getMocmosTechnology(), job, false,
			".edif", null, FileType.EDIF, null, ip, null);
	}

	/***************************************** GDS *****************************************/

	public Boolean GDS1() { return Boolean.valueOf(basicGDS1(getRegressionPath(), getStarterJob())); }
	private static boolean basicGDS1(String rootPath, Job job)
	{
		GDS.GDSPreferences ip = new GDS.GDSPreferences(true);
		return runImportTest(rootPath, "ImportGDS-1", null, MoCMOS.getMocmosTechnology(), job, true,
			".gds", null, FileType.GDS, null, ip, null);
	}

	public Boolean GDS2() { return Boolean.valueOf(basicGDS2(getRegressionPath(), getStarterJob())); }
	private static boolean basicGDS2(String rootPath, Job job)
	{
		GDS.GDSPreferences ip = new GDS.GDSPreferences(true);
		return runImportTest(rootPath, "ImportGDS-2", null, MoCMOS.getMocmosTechnology(), job, true,
			".gds", null, FileType.GDS, null, ip, null);
	}

	public Boolean GDS3() { return Boolean.valueOf(basicGDS3(getRegressionPath(), getStarterJob())); }
	private static boolean basicGDS3(String rootPath, Job job)
	{
		GDS.GDSPreferences ip = new GDS.GDSPreferences(true);
		return runImportTest(rootPath, "ImportGDS-3", null, MoCMOS.getMocmosTechnology(), job, true,
			".gds", null, FileType.GDS, null, ip, null);
	}

	public Boolean GDS4() { return Boolean.valueOf(basicGDS4(getRegressionPath(), getStarterJob())); }
	private static boolean basicGDS4(String rootPath, Job job)
	{
		GDS.GDSPreferences ip = new GDS.GDSPreferences(true);
		return runImportTest(rootPath, "ImportGDS-4", null, Technology.findTechnology("mocmosold"), job, true,
			".gds", null, FileType.GDS, null, ip, null);
	}

	/***************************************** Gerber *****************************************/

	public Boolean Gerber() { return Boolean.valueOf(basicGERBER1(getRegressionPath(), getStarterJob())); }
	private static boolean basicGERBER1(String rootPath, Job job)
	{
		Gerber.GerberPreferences gp1 = new Gerber.GerberPreferences(true);
		return runImportTest(rootPath, "ImportGERBER-1-1", null, MoCMOS.getMocmosTechnology(), job, false,
			".gbr", null, FileType.GERBER, null, gp1, null);
	}

	/***************************************** LEF/DEF *****************************************/

	public Boolean LEFDEF1() { return Boolean.valueOf(basicLEFDEF1(getRegressionPath(), getStarterJob())); }
	private static boolean basicLEFDEF1(String rootPath, Job job)
	{
		LEF.LEFPreferences ip1 = new LEF.LEFPreferences(true);
		DEF.DEFPreferences ip2 = new DEF.DEFPreferences(true);
		return runImportTest(rootPath, "ImportLEFDEF-1", null, MoCMOS.getMocmosTechnology(), job, false,
			".lef", ".def", FileType.LEF, FileType.DEF, ip1, ip2);
	}

	public Boolean LEFDEF2() { return Boolean.valueOf(basicLEFDEF2(getRegressionPath(), getStarterJob())); }
	private static boolean basicLEFDEF2(String rootPath, Job job)
	{
		LEF.LEFPreferences ip = new LEF.LEFPreferences(true);
		return runImportTest(rootPath, "ImportLEFDEF-2", null, MoCMOS.getMocmosTechnology(), job, false,
			".lef", null, FileType.LEF, null, ip, null);
	}

	/***************************************** SUE *****************************************/

	public Boolean SUE1() { return Boolean.valueOf(basicSUE1(getRegressionPath(), getStarterJob())); }
	private static boolean basicSUE1(String rootPath, Job job)
	{
		Sue.SuePreferences ip = new Sue.SuePreferences(true);
		return runImportTest(rootPath, "ImportSUE-1", "ImportSUE-1", MoCMOS.getMocmosTechnology(), job, true,
			".sue", null, FileType.SUE, null, ip, null);
	}

	public Boolean SUE2() { return Boolean.valueOf(basicSUE2(getRegressionPath(), getStarterJob())); }
	private static boolean basicSUE2(String rootPath, Job job)
	{
		Sue.SuePreferences ip = new Sue.SuePreferences(true);
		return runImportTest(rootPath, "ImportSUE-2", "ImportSUE-2", MoCMOS.getMocmosTechnology(), job, true,
			".sue", null, FileType.SUE, null, ip, null);
	}

	public Boolean SUE3() { return Boolean.valueOf(basicSUE3(getRegressionPath(), getStarterJob())); }
	private static boolean basicSUE3(String rootPath, Job job)
	{
		Sue.SuePreferences ip = new Sue.SuePreferences(true);
		return runImportTest(rootPath, "ImportSUE-3", "ImportSUE-3", MoCMOS.getMocmosTechnology(), job, true,
			".sue", null, FileType.SUE, null, ip, null);
	}

	private static boolean runImportTest(String rootPath, String testName, String subLoc, Technology tech, Job job, boolean makeLib,
		String extension1, String extension2, FileType type1, FileType type2,
		Input.InputPreferences ip1, Input.InputPreferences ip2)
	{
		String currentDir = new File("").getAbsolutePath() + File.separator;
		String trueRootPath = (rootPath != null && rootPath.length() > 0) ? (rootPath + "/tools/IO/") : currentDir;
		String inputDir = trueRootPath + "data/libs/";
		String outputDir = trueRootPath + "output/";
		ensureOutputDirectory(outputDir);
		MessagesStream.getMessagesStream().save(outputDir + testName + ".log");
		try
		{
			// delete previous libraries
			List<Library> libsToDelete = getLibsInOrder();
			for(Library l : libsToDelete) l.kill("delete");

			// make a library
			Library iniLib = null;
			if (makeLib)
			{
				String outputFileName = inputDir + (subLoc == null ? "" : subLoc + "/") + testName + ".jelib";
				URL outputFileURL = TextUtils.makeURLToFile(outputFileName);
				iniLib = Library.newInstance(testName, outputFileURL);
			}
			TechPool techPool = TechPool.getThreadTechPool();
			EditingPreferences ep = new EditingPreferences(true, techPool);

			// Read foreign file
			String importFileName = inputDir + (subLoc == null ? "" : subLoc + "/") + testName;
			if (extension2 != null) importFileName += "a";
			importFileName += extension1;
			URL importFileURL = TextUtils.makeURLToFile(importFileName);
			Map<Library,Cell> currentCells = new HashMap<Library,Cell>();
			Map<CellId,BitSet> nodesToExpand = new HashMap<CellId,BitSet>();
			ip1.disablePopups = true;
			Library libNew = Input.importLibrary(ep, ip1, importFileURL, type1, iniLib, tech, currentCells, nodesToExpand, false, job);
			if (libNew == null)
			{
				System.out.println("Import failed");
				return false;
			}

			// if there is a second foreign file, read it too
			if (extension2 !=  null)
			{
				String importFileName2 = inputDir + (subLoc == null ? "" : subLoc + "/") + testName + "b" + extension2;
				URL importFileURL2 = TextUtils.makeURLToFile(importFileName2);
				currentCells = new HashMap<Library,Cell>();
				nodesToExpand = new HashMap<CellId,BitSet>();
				ip2.disablePopups = true;
				libNew = Input.importLibrary(ep, ip2, importFileURL2, type2, iniLib, tech, currentCells, nodesToExpand, false, job);
				if (libNew == null)
				{
					System.out.println("Import failed");
					return false;
				}
			}

			// make sure libraries have correct name
			for(Iterator<Library> it = Library.getLibraries(); it.hasNext(); )
			{
				Library lib = it.next();
				if (lib.getNumCells() == 0) continue;

				if (!lib.getName().startsWith(testName))
					lib.setName(testName + "_" + lib.getName());
			}

			// write and test all libraries
			List<Library> allLibraries = getLibsInOrder();
			for(Library l : allLibraries)
			{
				if (l.getNumCells() == 0) continue;

				// reset all dates so file will compare
				Date zeroDate = new Date(0);
				for(Iterator<Cell> it = l.getCells(); it.hasNext(); )
				{
					Cell cell = it.next();
					cell.lowLevelSetCreationDate(zeroDate);
					cell.lowLevelSetRevisionDate(zeroDate);
					cell.getDatabase().backup();
				}

				// save library and compare it to expected
				boolean compares = compareLibraryResults(trueRootPath, l.getName(), l,
					new char [] {'H', 'V', 'L', 'R', 'F', 'T', 'O', '#'});
				if (!compares)
				{
					System.out.println("ImportForeignTest test '" + testName + "' FAILED");
					return false;
				}
			}
		} catch (Exception e)
		{
			// Catching any type of exception
			e.printStackTrace();
			return false;
		}
		System.out.println("ImportForeignTest test '" + testName + "' PASSED");
		return true;
	}

	/**
	 * Method to return a list of all libraries in proper order for deletion.
	 * The first library does not depend on others, the second may depend on the
	 * first but no others, etc.
	 * @return a list of all libraries in proper order for deletion.
	 */
	private static List<Library> getLibsInOrder()
	{
		// make ordered list of libraries, from top down
		List<Library> allLibraries = new ArrayList<Library>();
		boolean libsToFind = true;
		while (libsToFind)
		{
			libsToFind = false;
			for(Iterator<Library> it = Library.getLibraries(); it.hasNext(); )
			{
				Library lib = it.next();
				if (lib.isHidden()) continue;
				EDatabase database = lib.getDatabase();
				if (allLibraries.contains(lib)) continue;
				if (lib.getNumCells() != 0)
				{
					boolean cleanParents = true;
					for(Iterator<Cell> cIt = lib.getCells(); cIt.hasNext(); )
					{
						Cell cell = cIt.next();
						for(Iterator<CellUsage> uIt = cell.getUsagesOf(); uIt.hasNext(); )
						{
							CellUsage cu = uIt.next();
							Cell parent = cu.getParent(database);
							Library parentLib = parent.getLibrary();
							if (parentLib != lib)
							{
								if (!allLibraries.contains(parentLib))
								{
									cleanParents = false;
									break;
								}
							}
						}
						if (!cleanParents) break;
					}
					if (!cleanParents) continue;
				}
				allLibraries.add(lib);
				libsToFind = true;
			}
		}
		return allLibraries;
	}
}
