/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: AbstractTest.java
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

import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.EDatabase;
import com.sun.electric.database.hierarchy.Library;
import com.sun.electric.database.text.Setting;
import com.sun.electric.technology.Foundry;
import com.sun.electric.technology.Technology;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.io.output.Output;
import com.sun.electric.tool.user.Clipboard;
import com.sun.electric.tool.user.MessagesStream;
import com.sun.electric.tool.user.User;
import com.sun.electric.tool.user.ui.WindowFrame;
import com.sun.electric.util.TextUtils;

import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.PrintWriter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.imageio.ImageIO;

/**
 * Auxiliary structure defined as class to avoid implementing all functions
 */
public abstract class AbstractTest implements Serializable
{
	////////////////// ** static function ** /////////////////////////////////
	static String properDirectory(String regressionPath, Class theClass)
	{
		String firstName = theClass.getName();

		int j = firstName.lastIndexOf(".");
		String type = firstName.substring(j + 1);

		int k = type.lastIndexOf("Test");
		type = type.substring(0, k);

		dirMakeCheck(regressionPath, type);
		return type;
	}

	static String outputDir(String regressionPath, String testType)
	{
		return workingDir(regressionPath, testType) + "output/";
	}

	static void ensureOutputDirectory(String dirName)
	{
		File f = new File(dirName);
		if (!f.exists()) f.mkdir();
	}

	private static void dirMakeCheck(String regressionPath, String type)
	{
		boolean dirCheck = (new File(outputDir(regressionPath, type))).isDirectory();
		if (dirCheck)
		{
			// System.out.println("Directory: " + outputDir(type) + " Exists");
		} else
		{
			boolean dirMake = (new File(outputDir(regressionPath, type))).mkdir();
			if (dirMake)
			{
				System.out.println("Successfully made directory: " + outputDir(regressionPath, type));
			} else
			{
				System.out.println("Failed to make directory");
			}
		}
	}

	protected static String workingDir(String regressionPath, String testType)
	{
		String path = regressionPath;
		if (path != null && path.length() != 0 && !path.contains("<")) // path setup
			return regressionPath + "/tools/" + testType + "/";
		return ""; // nothing
	}

	protected static String dataDir(String regressionPath, String testType)
	{
		return workingDir(regressionPath, testType) + "data/libs/";
	}

	/**
	 * Method to delete all current libraries so that any previous versions do not affect the current test.
	 */
	public static void wipeLibraries()
	{
		Clipboard.clear();

		List<Library> allLibs = new ArrayList<Library>();
		for(Iterator<Library> it = Library.getLibraries(); it.hasNext(); )
			allLibs.add(it.next());

		while (allLibs.size() != 0)
		{
			// find a library to delete
			boolean killedOne = false;
			for(Library lib : allLibs)
			{
				Set<Cell> found = Library.findReferenceInCell(lib);

				// if all references are from the clipboard, request that the clipboard be cleared, too
				boolean nonClipboard = false;
				for (Cell cell : found)
				{
					if (!cell.getLibrary().isHidden()) nonClipboard = true;
				}

				// You can't close it because there are open cells that refer to library elements
				if (nonClipboard) continue;
				WindowFrame.removeLibraryReferences(lib);
				lib.kill("delete");
				allLibs.remove(lib);
				killedOne = true;
				break;
			}
			if (!killedOne)
			{
				System.out.println("Circular loop in libraries: cannot wipe");
				break;
			}
		}
	}

	////////////////// ** end of static function ** /////////////////////////////////

	private String name;
	private String groupName;
	private boolean multiTask;
	private boolean interactive;
	private FakeTestJob starterJob;

	protected AbstractTest(String name)
	{
		this.name = name;
		this.multiTask = false;
		this.interactive = false;
	}

	protected AbstractTest(String name, boolean multiTask, boolean interactive)
	{
		this.name = name;
		this.multiTask = multiTask;
		this.interactive = interactive;
	}

	static String getValidRootPath(String root, String extraPath, String alternativePath)
	{
		return (root != null) ? root+extraPath : alternativePath;
	}

	public void setGroupName(String name) { groupName = name; }

	public String getFunctionName() { return name; }

	protected String getResultName() { return getFunctionName() + "Result"; }

	protected String getLogName() { return getFunctionName() + "Out.log"; }

	public String getFullTestName() { return groupName + ":" + name; }

	boolean isMultiTask() { return multiTask; }

	boolean isInteractive() { return interactive; }

	void setStarterJob(FakeTestJob starterJob) { this.starterJob = starterJob; }

	FakeTestJob getStarterJob() { return starterJob; }

	EDatabase getDatabase() { return starterJob.getDatabase(); }

	String getRegressionPath() { return User.getRegressionPath(); }

	/**
	 * Method to get the real root path from the regression root directory.
	 */
	protected String workingDir()
	{
		String path = getRegressionPath();
		String testParameter = properDirectory(path, getClass());
		return workingDir(path, testParameter);
	}

	public String toString() { return name; }

	protected static Technology setFoundry(Technology tech)
	{
		Foundry.Type f = null;
		if (tech == Technology.getMocmosTechnology())
		{
			f = Foundry.Type.MOSIS;
		} else {
			System.out.println(f + " not available for testing");
			return tech;
		}
		return setFoundry(tech, f.getName());
	}

	/**
	 * Change foundry preference of given technology and return new version for this technology.
	 * @param tech technology to change foundry
	 * @param foundryName foundry name
	 * @return new version of technology
	 */
	public static Technology setFoundry(Technology tech, String foundryName)
	{
		String techName = tech.getTechName();
		Setting.SettingChangeBatch changeBatch = new Setting.SettingChangeBatch();
		changeBatch.add(tech.getPrefFoundrySetting(), foundryName);
		EDatabase.serverDatabase().implementSettingChanges(changeBatch);
		return Technology.findTechnology(techName);
	}

	protected class CompareJob extends Job
	{
		private Cell cell;
		private String resultName;

		CompareJob(Cell cell)
		{
			this(cell, getResultName());
		}

		CompareJob(Cell cell, String resultName)
		{
			super("Test " + getFullTestName() + " compare", User.getUserTool(), Job.Type.CHANGE, null, null, Job.Priority.USER);
			this.cell = cell;
			this.resultName = resultName;
		}

		@Override
		public boolean doIt()
		{
			// examine results
			boolean good = false;
			try
			{
				good = compareCellResults(cell, resultName);
			} catch (Exception e)
			{
				System.out.println("Exception:" + e);
				e.printStackTrace();
			}
			if (getStarterJob() != null)  // not coming from the regression
				getStarterJob().updateTestResult(good);
			return good;
		}
	}

	/**
	 * Method to compare a given library with the expected library stored in file.
	 * @param trueRootPath
	 * @param rootName
	 * @param outLib
	 * @param lineKeys Set of first characters in lines to ignore to proper compare the data.
	 * @return true if they are the same.
	 */
	static boolean compareLibraryResults(String trueRootPath, String rootName, Library outLib, char[] lineKeys)
	{
		// write results in a different library
		String commondEnd = rootName + "Result";
		String outputDir = trueRootPath + "output/";
		String destLibName = outputDir + commondEnd + ".jelib";
		Output.saveJelib(destLibName, outLib);

		// remove header lines from the library
		String trimmedLib = outputDir + commondEnd + "Partial.jelib";
		removeLines(destLibName, trimmedLib, lineKeys);

		// see if the library is as expected
		String expectedLib = trueRootPath + "data/expected/" + commondEnd + ".jelib";
		return compareResults(trimmedLib, expectedLib);
	}

	/**
	 * Method to compare a given cell with the expected cell stored in file. This function can't be used
	 * if the cell doesn't exist in the expected data. Side effect: this function writes down the cell under
	 * analysis and also the trimmed version of it.
	 * @param cell Cell to compare.
	 * @param resultName
	 * @return true if they are the same.
	 */
	boolean compareCellResults(Cell cell, String resultName)
	{
		if (resultName == null)
			resultName = getResultName();

		String testParameter = properDirectory(getRegressionPath(), getClass());
		String outputDir = outputDir(getRegressionPath(), testParameter);

		// reset modification date for consistent output
		cell.lowLevelSetRevisionDate(new Date(0));
		cell.getDatabase().backup();

		// save output
		String destLib = outputDir + resultName + "Out.jelib";
		Output.saveJelib(destLib, cell.getLibrary());

		// extract the cell from the library
		String trimmedLib = outputDir + resultName + ".jelib";
		trimLibToCell(destLib, trimmedLib, cell.getName() + ";");

		// see if the cell is as expected
		String expectedLib = workingDir(getRegressionPath(), testParameter) + "data/expected/" + resultName + ".jelib";
		return compareResults(trimmedLib, expectedLib);
	}

	static void trimLibToCell(String inLibFile, String outLibFile, String cellName)
	{
		try
		{
			LineNumberReader lineReader = new LineNumberReader(new FileReader(inLibFile));
			PrintWriter printWriter = new PrintWriter(new BufferedWriter(new FileWriter(outLibFile)));
			boolean inCell = false;
			for(;;)
			{
				String buf = lineReader.readLine();
				if (buf == null) break;
				if (!inCell)
				{
					if (buf.startsWith("C"))
					{
						if (buf.substring(1).startsWith(cellName))
							inCell = true;
					}
				}
				if (inCell)
					printWriter.println(buf);
				if (buf.startsWith("X")) inCell = false;
			}
			printWriter.close();
			lineReader.close();
		} catch (IOException e)
		{
			System.out.println("Error reading " + inLibFile);
			return;
		}
	}

	/**
	 * Method to remove a given string from each file line if the line starts with it.
	 * @param inLibFile the original file
	 * @param outLibFile the modified file
	 * @param key String to remove
	 */
	public static void removeLines(String inLibFile, String outLibFile, String key)
	{
		try
		{
			LineNumberReader lineReader = new LineNumberReader(new FileReader(inLibFile));
			PrintWriter printWriter = new PrintWriter(new BufferedWriter(new FileWriter(outLibFile)));
			for(;;)
			{
				String buf = lineReader.readLine();
				if (buf == null) break;
				if (buf.length() == 0) continue;

				if (buf.startsWith(key)) continue; // key found
				printWriter.println(buf);
			}
			printWriter.close();
			lineReader.close();
		} catch (IOException e)
		{
			System.out.println("Error reading " + inLibFile);
			return;
		}
	}

	/**
	 * Method to remove first character from each file line if it matches with any of the given keys
	 * and it writes a new file.
	 * @param inLibFile the original file
	 * @param outLibFile the modified file
	 * @param lineKeys Key characters
	 */
	public static void removeLines(String inLibFile, String outLibFile, char [] lineKeys)
	{
		try
		{
			LineNumberReader lineReader = new LineNumberReader(new FileReader(inLibFile));
			PrintWriter printWriter = new PrintWriter(new BufferedWriter(new FileWriter(outLibFile)));
			for(;;)
			{
				String buf = lineReader.readLine();
				if (buf == null) break;
				if (buf.length() == 0) continue;

				char key = buf.charAt(0);
				boolean found = false;
				for(int i=0; i<lineKeys.length; i++)
				{
					if (lineKeys[i] == key) { found = true;   break; }
				}
				if (found) continue;
				printWriter.println(buf);
			}
			printWriter.close();
			lineReader.close();
		} catch (IOException e)
		{
			System.out.println("Error reading " + inLibFile);
			return;
		}
	}

	/**
	 * Method to compare two text files.
	 * @param file1 the first file.
	 * @param file2 the second file.
	 * @return true if they are the same, false if they differ.
	 */
	public static boolean compareResults(String file1, String file2)
	{
		String reason = null;
		LineNumberReader lineReader1 = null;
		try
		{
			lineReader1 = new LineNumberReader(new FileReader(file1));
		} catch (IOException e)
		{
			System.out.println("Cannot find file " + file1);
			return false;
		}
		LineNumberReader lineReader2 = null;
		try
		{
			lineReader2 = new LineNumberReader(new FileReader(file2));
		} catch (IOException e)
		{
			System.out.println("Cannot find file " + file2);
			try
			{
				lineReader1.close();
			} catch (IOException e2) {}
			return false;
		}
		try
		{
			for(;;)
			{
				String buf1 = lineReader1.readLine();
				String buf2 = lineReader2.readLine();
				if (buf1 == null && buf2 == null) break;
				if (buf1 == null || buf2 == null)
				{
					reason = "Files have unequal length";
					break;
				}
				if (!buf1.equals(buf2))
				{
					reason = "Line " + lineReader1.getLineNumber() + " differs";
					break;
				}
			}
			lineReader1.close();
			lineReader2.close();
		} catch (IOException e)
		{
			System.out.println("Error reading files");
			return false;
		}
		if (reason != null) System.out.println("*** EXPECTED RESULTS IN " + file2 + " DO NOT MATCH " + file1 + ": " + reason);
		return reason == null;
	}

	/**
	 * Method to compare two binary files.
	 * @param index the index of the image (-1 if it is not relevant).
	 * @param file the first file.
	 * @param exFile the second file.
	 * @return true if they are the same, false if they differ.
	 */
	public static boolean compareImages(int index, String file, String exFile)
	{
		String panelName = (index < 0) ? "" : " Panel " + index;
		BufferedImage image;
		try
		{
			image = ImageIO.read(TextUtils.makeURLToFile(file));
		} catch (IOException e)
		{
			System.out.println("ERROR Reading " + file + " (" + e.getMessage() + ")");
			return false;
		}
		BufferedImage exImage;
		try
		{
			exImage = ImageIO.read(TextUtils.makeURLToFile(exFile));
		} catch (IOException e)
		{
			System.out.println("ERROR Reading " + exFile + " (" + e.getMessage() + ")");
			return false;
		}
		int wid = image.getWidth(null);
		int hei = image.getHeight(null);
		int exWid = exImage.getWidth(null);
		int exHei = exImage.getHeight(null);
		double ratioX = 1, ratioY = 1;
		if (wid != exWid || hei != exHei)
		{
			System.out.println("WARNING:" + panelName + " image in file " + file + " does not match expected image in file " + exFile);
			System.out.println("        " + panelName + " image is " + wid + "x" + hei + " but expected image is " + exWid + "x" + exHei);
			if (wid != exWid) ratioX = exWid / (double)wid;
			if (hei != exHei) ratioY = exHei / (double)hei;
		}

		for(int y=0; y<hei; y++)
		{
			int trueY = (int)Math.round(y * ratioY);
			for(int x=0; x<wid; x++)
			{
				int trueX = (int)Math.round(x * ratioX);
				int c = image.getRGB(x, y) & 0xFFFFFF;
				int exC = exImage.getRGB(trueX, trueY) & 0xFFFFFF;
				if (c != exC)
				{
					String msg = "ERROR: ";
					if (wid == exWid && hei == exHei)
					{
						System.out.println(msg + panelName + " image in file " + file + " does not match expected image in file " + exFile);
						msg = "";
					}
					System.out.println(msg + "First pixel difference is at (" + x + "," + y +") where color is "+
						((c>>16)&0xFF)+"/"+((c>>8)&0xFF)+"/"+(c&0xFF) + " but expected color at (" + trueX + "," + trueY +") is "+
						((exC>>16)&0xFF)+"/"+((exC>>8)&0xFF)+"/"+(exC&0xFF));
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * Method to ensure output directory is available and it also initializes the messages file.
	 * @return String Root path to the current test location.
	 */
	String createMessageOutput()
	{
		// initialization
		String testParameter = properDirectory(getRegressionPath(), getClass());
		String outputDir = outputDir(getRegressionPath(), testParameter);
		ensureOutputDirectory(outputDir);
		MessagesStream.getMessagesStream().save(outputDir + getLogName());
		return testParameter;
	}
}
