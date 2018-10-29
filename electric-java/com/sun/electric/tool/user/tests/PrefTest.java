	/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: PrefTest.java
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
import com.sun.electric.database.text.Pref;
import com.sun.electric.database.text.PrefPackage;
import com.sun.electric.database.text.Setting;
import com.sun.electric.technology.TechFactory;
import com.sun.electric.technology.TechPool;
import com.sun.electric.technology.Technology;
import com.sun.electric.tool.Tool;
import com.sun.electric.tool.drc.DRC;
import com.sun.electric.tool.erc.ERCAntenna;
import com.sun.electric.tool.erc.ERCWellCheck;
import com.sun.electric.tool.extract.LayerCoverageTool;
import com.sun.electric.tool.io.output.GenerateVHDL;
import com.sun.electric.tool.placement.Placement;
import com.sun.electric.tool.routing.RoutingFrame;
import com.sun.electric.tool.sandbox.DummyPreferencesFactory;
import com.sun.electric.tool.sc.SilComp;
import com.sun.electric.tool.user.GraphicsPreferences;
import com.sun.electric.tool.user.User;
import com.sun.electric.tool.user.dialogs.ComponentMenu;
import com.sun.electric.tool.user.ui.LayerVisibility;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * Class to test the Preferences facility.
 */
public class PrefTest extends AbstractTest
{
	public PrefTest(String name)
	{
		super(name);
	}

	public static List<AbstractTest> getTests()
	{
		List<AbstractTest> list = new ArrayList<AbstractTest>();
		list.add(new PrefTest("Preferences"));
		list.add(new PrefTest("SettingsGroup"));
		return list;
	}

	public static String getOutputDirectory()
	{
		String rootPath = User.getRegressionPath();
		if (rootPath == null) return null;
		return rootPath + "/tools/Database/output/";
	}

	/************************************* Preferences *********************************************************/

	public Boolean Preferences()
	{
		Pref.forbidPreferences();
		boolean result = basicPrefTest(getRegressionPath());
		Pref.allowPreferences();
		return Boolean.valueOf(result);
	}

	/**
	 * Method to run Preferences test. Must be public/static due to regressions.
	 * @param rootPath if rootPath is not null, then the data path includes tool path
	 * otherwise assume the regression is running locally
	 * @return true if test successfully run.
	 */
	private static boolean basicPrefTest(String rootPath)
	{
		String trueRootPath = (rootPath != null) ? rootPath + "/tools/Database/" : "";
		String outputDir = trueRootPath + "output/";
		ensureOutputDirectory(outputDir);
		boolean good = false;
		try
		{
			Map<String,String> m = new TreeMap<String,String>();

			// Preferences options
			for (Pref.Group group: Pref.getAllGroups())
				putPrefs(group, m);

			// new style options
			TechPool techPool = TechPool.getThreadTechPool();
			putPrefs(new EditingPreferences(true, techPool), m, true);
			putPrefs(new GraphicsPreferences(true, techPool), m, true);
			putPrefs(new LayerVisibility(true), m, true);
			putPrefs(new ComponentMenu.ComponentMenuPreferences(true), m, true);
			putPrefs(new GenerateVHDL.VHDLPreferences(true), m, false);
			putPrefs(new DRC.DRCPreferences(true), m, true);
			putPrefs(new LayerCoverageTool.LayerCoveragePreferences(true), m, true);
			putPrefs(new SilComp.SilCompPrefs(true), m, false);
			putPrefs(new ERCWellCheck.WellCheckPreferences(true), m, false);
			putPrefs(new ERCAntenna.AntennaPreferences(true, techPool), m, true);
			putPrefs(new Placement.PlacementPreferences(true), m, false);
			putPrefs(new RoutingFrame.RoutingPrefs(true), m, false);

			String resultPrefs = outputDir + "AllPrefs.txt";
			HashSet<String> excludeSet = new HashSet<String>();
			excludeSet.addAll(Arrays.asList(
				"tool/routing/AllNodesAsTargets",
				"tool/routing/CutFile",
				"tool/routing/MaxBadEdges",
				"tool/routing/MaxLapCount",
				"tool/routing/MaxPinCount",
				"tool/user/WorkingDirectory"));
			List<String> excludePrefix = new ArrayList<String>();
			excludePrefix.addAll(Arrays.asList(
				"plugins/menus",
				"tool/io/FileTypeGroup"));
			PrintWriter out = new PrintWriter(resultPrefs);
			for (Map.Entry<String,String> e: m.entrySet())
			{
				String key = e.getKey();
				if (excludeSet.contains(key)) continue;
				if (key.startsWith("tool/user/ComponentMenuEntry")) continue;
				boolean exclude = false;
				for(String eKey : excludePrefix)
				{
					if (key.startsWith(eKey)) { exclude = true;  break; }
				}
				if (exclude) continue;
				out.println(e.toString());
			}
			out.close();
			String expectedPrefs = trueRootPath + "data/expected/AllPrefs.txt";
			good = compareResults(resultPrefs, expectedPrefs);
		} catch (Exception e)
		{
			// Catching any time of exception!
			e.printStackTrace();
		}
		return good;
	}

	private static void putPrefs(Pref.Group group, Map<String,String> m)
	{
		for (Pref pref: group.getPrefs())
		{
			String old = m.put(pref.getPrefPath(), pref.getFactoryValue().toString());
			assert old == null;
		}
	}

	private static void putPrefs(PrefPackage pp, Map<String,String> m, boolean skipNonstandardTechs)
		throws BackingStoreException
	{
		Preferences root = new DummyPreferencesFactory().userRoot();
		root.clear();
		for (String child: root.childrenNames())
			root.node(child).removeNode();
		PrefPackage.lowLevelPutPrefs(pp, root, false);
		putPrefs(root, m, skipNonstandardTechs);
	}

	private static void putPrefs(Preferences p, Map<String,String> m, boolean skipNonstandardTechs)
		throws BackingStoreException
	{
		assert p.absolutePath().charAt(0) == '/';
		String relativePath = p.absolutePath().substring(1) + "/";
		for (String key: p.keys())
		{
			String entry = relativePath + key;
			if (skipNonstandardTechs)
			{
				List<String> sensitiveTechNames = TechFactory.getSensitiveTechNames();
				boolean sensitive = false;
				for(String stn : sensitiveTechNames)
				{
					String poss1 = "In" + stn, poss2 = "IN" + stn;
					String poss3 = "for" + stn, poss4 = "For" + stn;
					if (entry.indexOf(poss1) >= 0 || entry.indexOf(poss2) >= 0 ||
						entry.indexOf(poss3) >= 0 || entry.indexOf(poss4) >= 0)
					{
						sensitive = true;
						break;
					}
				}
				if (sensitive) continue;
			}
			String old = m.put(entry, p.get(key, null));
			assert old == null;
		}
		for (String child: p.childrenNames())
			putPrefs(p.node(child), m, skipNonstandardTechs);
	}

	/************************************* SettingsGroup *********************************************************/

	public Boolean SettingsGroup()
	{
		for (Setting setting: getDatabase().getSettings().keySet())
		{
			String xmlPath = setting.getXmlPath();
			int pos = xmlPath.indexOf('.');
			String pathHead = xmlPath.substring(0, pos + 1);
			String pathTail = xmlPath.substring(pos + 1);
			int n = 0;
			for (Iterator<Tool> it = Tool.getTools(); it.hasNext(); )
			{
				Tool tool = it.next();
				if (!pathHead.equals(tool.getProjectSettings().getXmlPath())) continue;
				Setting s = tool.getProjectSettings().getSetting(pathTail);
				if (s != null)
				{
					if (s != setting)
					{
						System.out.println("ERROR: Setting duplicate: " + xmlPath);
						return Boolean.FALSE;
					}
					n++;
				}
			}
			for (Iterator<Technology> it = Technology.getTechnologies(); it.hasNext(); )
			{
				Technology tech = it.next();
				if (!pathHead.equals(tech.getProjectSettings().getXmlPath())) continue;
				Setting s = tech.getProjectSettings().getSetting(pathTail);
				if (s != null)
				{
					if (s != setting)
					{
						System.out.println("ERROR: Setting duplicate: " + xmlPath);
						return Boolean.FALSE;
					}
					n++;
				}
			}
			if (n > 1)
			{
				System.out.println("ERROR: Setting count " + xmlPath + " is " + n);
				return Boolean.FALSE;
			}
		}
		return Boolean.TRUE;
	}
}
