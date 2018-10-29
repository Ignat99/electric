	/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: ScriptingTest.java
 *
 * Copyright (c) 2009, Static Free Software. All rights reserved.
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
import com.sun.electric.database.hierarchy.Library;
import com.sun.electric.tool.JobException;
import com.sun.electric.tool.lang.EvalJavaBsh;
import com.sun.electric.tool.lang.EvalJython;
import com.sun.electric.tool.user.User;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Class to test the Scripting facilities.
 */
public class ScriptingTest extends AbstractTest
{
	public ScriptingTest(String name)
	{
		super(name);
	}

	public static List<AbstractTest> getTests()
	{
		List<AbstractTest> list = new ArrayList<AbstractTest>();
		if (EvalJavaBsh.hasBeanShell())
			list.add(new ScriptingTest("BeanShell"));
		if (EvalJython.hasJython())
			list.add(new ScriptingTest("Jython"));
		return list;
	}

	public static String getOutputDirectory()
	{
		String rootPath = User.getRegressionPath();
		if (rootPath == null) return null;
		return rootPath + "/tools/Scripting/output/";
	}

	/************************************* BeanShell *********************************************************/

	public Boolean BeanShell()
	{
		String testParameter = createMessageOutput();

		// invoke the script
		String fileName = dataDir(getRegressionPath(), testParameter) + "BSH_MakeInverter.bsh";
		EvalJavaBsh evaluator = new EvalJavaBsh();
		try
		{
			evaluator.doSource(fileName);
		} catch (JobException e)
		{
			return Boolean.FALSE;
		}

		// do auto-routing
		Cell lay = Library.findLibrary("BeanShellTest").findNodeProto("InverterJ{lay}");
		if (lay == null)
		{
			System.out.println("InverterJ cell in BeanShellTest not found");
			return Boolean.FALSE;
		}
		lay.lowLevelSetCreationDate(new Date(0));
		return Boolean.valueOf(compareCellResults(lay, getResultName()));
	}

	/************************************* Jython *********************************************************/

	public Boolean Jython()
	{
		String testParameter = createMessageOutput();

		// invoke the script
		String fileName = dataDir(getRegressionPath(), testParameter) + "JY_MakeInverter.jy";
		EvalJython.runScriptNoJob(fileName);

		// do auto-routing
		Cell lay = Library.findLibrary("JythonTest").findNodeProto("InverterP{lay}");
		if (lay == null)
		{
			System.out.println("InverterJ cell in JythonTest not found");
			return Boolean.FALSE;
		}
		lay.lowLevelSetCreationDate(new Date(0));
		return Boolean.valueOf(compareCellResults(lay, getResultName()));
	}

}
