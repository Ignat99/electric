	/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: TechnologyTest.java
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

import com.sun.electric.database.Environment;
import com.sun.electric.database.id.IdManager;
import com.sun.electric.database.text.Setting;
import com.sun.electric.technology.TechFactory;
import com.sun.electric.technology.Technology;
import com.sun.electric.technology.Xml;
import com.sun.electric.technology.technologies.Generic;
import com.sun.electric.tool.user.User;
import com.sun.electric.tool.user.projectSettings.ProjSettings;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * class to test the technologies.
 */
public class TechnologyTest extends AbstractTest
{
	public TechnologyTest(String testName)
	{
		super(testName);
	}

	public static List<AbstractTest> getTests()
	{
		List<AbstractTest> list = new ArrayList<AbstractTest>();
		list.add(new TechnologyTest("artwork"));
		list.add(new TechnologyTest("bicmos"));
		list.add(new TechnologyTest("bipolar"));
		list.add(new TechnologyTest("cmos"));
		list.add(new TechnologyTest("efido"));
		list.add(new TechnologyTest("fpga"));
		list.add(new TechnologyTest("gem"));
		list.add(new TechnologyTest("mocmos_deep_5_1"));
		list.add(new TechnologyTest("mocmos_deep_5_1_a"));
		list.add(new TechnologyTest("mocmos_deep_5_2"));
		list.add(new TechnologyTest("mocmos_deep_6_1"));
		list.add(new TechnologyTest("mocmos_deep_6_2"));
		list.add(new TechnologyTest("mocmos_deep_6_2_s"));
		list.add(new TechnologyTest("mocmos_scmos_2_1"));
		list.add(new TechnologyTest("mocmos_scmos_2_1_a"));
		list.add(new TechnologyTest("mocmos_scmos_2_2"));
		list.add(new TechnologyTest("mocmos_scmos_3_1"));
		list.add(new TechnologyTest("mocmos_scmos_3_2"));
		list.add(new TechnologyTest("mocmos_scmos_4_1"));
		list.add(new TechnologyTest("mocmos_scmos_4_2"));
		list.add(new TechnologyTest("mocmos_scmos_4_2_s"));
		list.add(new TechnologyTest("mocmos_sub_2_1"));
		list.add(new TechnologyTest("mocmos_sub_2_1_a"));
		list.add(new TechnologyTest("mocmos_sub_2_2"));
		list.add(new TechnologyTest("mocmos_sub_3_1"));
		list.add(new TechnologyTest("mocmos_sub_3_2"));
		list.add(new TechnologyTest("mocmos_sub_4_1"));
		list.add(new TechnologyTest("mocmos_sub_4_2"));
		list.add(new TechnologyTest("mocmos_sub_5_1"));
		list.add(new TechnologyTest("mocmos_sub_5_2"));
		list.add(new TechnologyTest("mocmos_sub_6_1"));
		list.add(new TechnologyTest("mocmos_sub_6_1_a"));
		list.add(new TechnologyTest("mocmos_sub_6_2"));
		list.add(new TechnologyTest("mocmos_sub_6_2_s"));
		list.add(new TechnologyTest("mocmos_scna"));
		list.add(new TechnologyTest("mocmosold"));
		list.add(new TechnologyTest("mocmossub"));
		list.add(new TechnologyTest("nmos"));
		list.add(new TechnologyTest("pcb"));
		list.add(new TechnologyTest("rcmos"));
		list.add(new TechnologyTest("schematic"));
		list.add(new TechnologyTest("tft"));
		return list;
	}

	public static String getOutputDirectory()
	{
		String rootPath = User.getRegressionPath();
		if (rootPath == null) return null;
		return rootPath + "/tools/Database/output/";
	}

	public Boolean artwork() { return basicTest(getRegressionPath(), "artwork", "artwork"); }

	public Boolean bicmos() { return basicTest(getRegressionPath(), "bicmos", "bicmos"); }

	public Boolean bipolar() { return basicTest(getRegressionPath(), "bipolar", "bipolar"); }

	public Boolean cmos() { return basicTest(getRegressionPath(), "cmos", "cmos"); }

	public Boolean efido() { return basicTest(getRegressionPath(), "efido", "efido"); }

	public Boolean fpga() { return basicTest(getRegressionPath(), "fpga", "fpga"); }

	public Boolean gem() { return basicTest(getRegressionPath(), "gem", "gem"); }

	public Boolean mocmos_deep_5_1() { return basicTest(getRegressionPath(), "mocmos", "mocmos_deep_5_1_sf_af"); }

	public Boolean mocmos_deep_5_1_a() { return basicTest(getRegressionPath(), "mocmos", "mocmos_deep_5_1_sf_at"); }

	public Boolean mocmos_deep_5_2() { return basicTest(getRegressionPath(), "mocmos", "mocmos_deep_5_2_sf_af"); }

	public Boolean mocmos_deep_6_1() { return basicTest(getRegressionPath(), "mocmos", "mocmos_deep_6_1_sf_af"); }

	public Boolean mocmos_deep_6_2() { return basicTest(getRegressionPath(), "mocmos", "mocmos_deep_6_2_sf_af"); }

	public Boolean mocmos_deep_6_2_s() { return basicTest(getRegressionPath(), "mocmos", "mocmos_deep_6_2_st_af"); }

	public Boolean mocmos_scmos_2_1() { return basicTest(getRegressionPath(), "mocmos", "mocmos_scmos_2_1_sf_af"); }

	public Boolean mocmos_scmos_2_1_a() { return basicTest(getRegressionPath(), "mocmos", "mocmos_scmos_2_1_sf_at"); }

	public Boolean mocmos_scmos_2_2() { return basicTest(getRegressionPath(), "mocmos", "mocmos_scmos_2_2_sf_af"); }

	public Boolean mocmos_scmos_3_1() { return basicTest(getRegressionPath(), "mocmos", "mocmos_scmos_3_1_sf_af"); }

	public Boolean mocmos_scmos_3_2() { return basicTest(getRegressionPath(), "mocmos", "mocmos_scmos_3_2_sf_af"); }

	public Boolean mocmos_scmos_4_1() { return basicTest(getRegressionPath(), "mocmos", "mocmos_scmos_4_1_sf_af"); }

	public Boolean mocmos_scmos_4_2() { return basicTest(getRegressionPath(), "mocmos", "mocmos_scmos_4_2_sf_af"); }

	public Boolean mocmos_scmos_4_2_s() { return basicTest(getRegressionPath(), "mocmos", "mocmos_scmos_4_2_st_af"); }

	public Boolean mocmos_sub_2_1() { return basicTest(getRegressionPath(), "mocmos", "mocmos_sub_2_1_sf_af"); }

	public Boolean mocmos_sub_2_1_a() { return basicTest(getRegressionPath(), "mocmos", "mocmos_sub_2_1_sf_at"); }

	public Boolean mocmos_sub_2_2() { return basicTest(getRegressionPath(), "mocmos", "mocmos_sub_2_2_sf_af"); }

	public Boolean mocmos_sub_3_1() { return basicTest(getRegressionPath(), "mocmos", "mocmos_sub_3_1_sf_af"); }

	public Boolean mocmos_sub_3_2() { return basicTest(getRegressionPath(), "mocmos", "mocmos_sub_3_2_sf_af"); }

	public Boolean mocmos_sub_4_1() { return basicTest(getRegressionPath(), "mocmos", "mocmos_sub_4_1_sf_af"); }

	public Boolean mocmos_sub_4_2() { return basicTest(getRegressionPath(), "mocmos", "mocmos_sub_4_2_sf_af"); }

	public Boolean mocmos_sub_5_1() { return basicTest(getRegressionPath(), "mocmos", "mocmos_sub_5_1_sf_af"); }

	public Boolean mocmos_sub_5_2() { return basicTest(getRegressionPath(), "mocmos", "mocmos_sub_5_2_sf_af"); }

	public Boolean mocmos_sub_6_1() { return basicTest(getRegressionPath(), "mocmos", "mocmos_sub_6_1_sf_af"); }

	public Boolean mocmos_sub_6_1_a() { return basicTest(getRegressionPath(), "mocmos", "mocmos_sub_6_1_sf_at"); }

	public Boolean mocmos_sub_6_2() { return basicTest(getRegressionPath(), "mocmos", "mocmos_sub_6_2_sf_af"); }

	public Boolean mocmos_sub_6_2_s() { return basicTest(getRegressionPath(), "mocmos", "mocmos_sub_6_2_st_af"); }

	public Boolean mocmos_scna() { return basicTest(getRegressionPath(), "mocmos", "mocmos_scna"); }

	public Boolean mocmosold() { return basicTest(getRegressionPath(), "mocmosold", "mocmosold"); }

	public Boolean mocmossub() { return basicTest(getRegressionPath(), "mocmossub", "mocmossub"); }

	public Boolean nmos() { return basicTest(getRegressionPath(), "nmos", "nmos"); }

	public Boolean pcb() { return basicTest(getRegressionPath(), "pcb", "pcb"); }

	public Boolean rcmos() { return basicTest(getRegressionPath(), "rcmos", "rcmos"); }

	public Boolean schematic() { return basicTest(getRegressionPath(), "schematic", "schematic"); }

	public Boolean tft() { return basicTest(getRegressionPath(), "tft", "tft"); }

	/************************************* SUPPORT *********************************************************/

	public Boolean basicTest(String rootPath, String techName, String testName)
	{
		Environment savedEnvironment = Environment.setThreadEnvironment(null);
		try
		{
			return Boolean.valueOf(basicTechnologyTest(rootPath, techName, testName));
		} finally
		{
			Environment.setThreadEnvironment(savedEnvironment);
		}
	}

	/**
	 * Method to run basic technology test. Must be public/static due to regressions.
	 * @param rootPath the top level of the regressions data.
	 * @return true if test successfully run.
	 */
	private static boolean basicTechnologyTest(String rootPath, String techName, String testName)
	{
		String trueRootPath = getValidRootPath(rootPath, "/tools/Database/", "");
		String outputDir = trueRootPath + "output/";
		ensureOutputDirectory(outputDir);

		try
		{
			// initialize
			String fileName = trueRootPath + "data/projsettings_" + testName + ".xml";
			final ProjSettings projSettings = ProjSettings.read(new File(fileName));

			if (projSettings == null)
			{
				System.out.println("Error: can't open '" + fileName + "'");
				return false;
			}
			TechFactory techFactory = TechFactory.getKnownTechs().get(techName);
			if (techFactory == null)
			{
				System.out.println("Error: no factory for technology " + techName);
				return false;
			}

			Map<TechFactory.Param, Object> paramValues = new HashMap<TechFactory.Param, Object>();
			for (TechFactory.Param param : techFactory.getTechParams())
			{
				Object value = projSettings.getValue(param.xmlPath);
				if (value != null) paramValues.put(param, value);
			}
			Map<Object,Map<String,Object>> additionalAttributes = new HashMap<Object,Map<String,Object>>();
			Xml.Technology xmlTech = techFactory.getXml(paramValues, additionalAttributes);
			if (!checkXml(trueRootPath, testName, xmlTech, additionalAttributes)) return false;

			IdManager idManager = new IdManager();
			Generic generic = Generic.newInstance(idManager);

			Technology tech0 = techFactory.newInstance(generic, paramValues);
			if (!checkSettings(trueRootPath, techName, tech0)) return false;
			if (!checkDump(trueRootPath, testName, tech0, projSettings)) return false;
			if (!checkXml(trueRootPath, testName, tech0)) return false;

			Technology tech1 = techFactory.newInstance(generic);
			Technology tech2 = tech1.withTechParams(paramValues);
			if (!checkSettings(trueRootPath, techName, tech1)) return false;
			if (!checkSettings(trueRootPath, techName, tech2)) return false;
			if (!checkDump(trueRootPath, testName, tech2, projSettings)) return false;
			if (!checkXml(trueRootPath, testName, tech2)) return false;

			// check tech dump
			return true;
		} catch (Exception e)
		{
			// Catching any type of exception
			e.printStackTrace();
			return false;
		}
	}

	private static boolean checkDump(String rootPath, String testName, Technology tech, ProjSettings projSettings)
		throws IOException
	{
		HashMap<Setting, Object> settingValues = new HashMap<Setting, Object>();
		for (Setting setting : tech.getProjectSettings().getSettings())
		{
			Object value = projSettings.getValue(setting.getXmlPath());
			if (value == null) value = setting.getFactoryValue();
			settingValues.put(setting, value);
		}

		String fileName = testName + ".txt";
		File dumpFile = new File(rootPath + "output/" + fileName);
		dumpFile.delete();
		PrintWriter out = new PrintWriter(dumpFile);
		tech.dump(out, settingValues);
		out.close();
		System.out.println("Wrote tech dump to " + dumpFile);
		return equal(rootPath, fileName);
	}

	private static boolean checkXml(String rootPath, String testName, Technology tech)
		throws IOException
	{
		Map<Object, Map<String, Object>> additionalAttributes = new HashMap<Object, Map<String, Object>>();
		Xml.Technology techXml = tech.makeXml(additionalAttributes);
		return checkXml(rootPath, testName, techXml, additionalAttributes);
	}

	private static boolean checkXml(String rootPath, String testName, Xml.Technology techXml, Map<Object, Map<String, Object>> additionalAttributes)
	{
		String fileName = testName + ".xml";
		File dumpFile = new File(rootPath + "output/" + fileName);
		dumpFile.delete();
		techXml.writeXml(dumpFile.getPath(), false, null, additionalAttributes);
		System.out.println("Wrote Xml tech dump to " + dumpFile);
		return equal(rootPath, fileName);
	}

	private static boolean checkSettings(String rootPath, String techName, Technology tech)
		throws IOException
	{
		ProjSettings ps = new ProjSettings();
		for (Setting setting : tech.getProjectSettings().getSettings())
		{
			ps.putValue(setting.getXmlPath(), setting.getFactoryValue());
		}
		return check(rootPath, techName + ".set", ps);
	}

	private static boolean check(String rootPath, String fileName, ProjSettings ps)
	{
		File prefFile = new File(rootPath + "output/" + fileName);
		prefFile.delete();
		ps.write(prefFile.toString());
		return equal(rootPath, fileName);
	}

	private static boolean equal(String rootPath, String fileName)
	{
		String resultFile = rootPath + "output/" + fileName;
		String expectedFile = rootPath + "data/expected/" + fileName;
		return compareResults(resultFile, expectedFile);
	}
}
