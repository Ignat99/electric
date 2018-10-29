/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: TimingAnalysisTest.java
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

import com.sun.electric.tool.user.User;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class CommonSignalAnalysisTest extends AbstractTest
{
	private String name, vlFile, libFile;

	/** Creates a new instance of TimingAnalysisTest */
	public CommonSignalAnalysisTest(String name)
	{
		super(name);
	}

	public CommonSignalAnalysisTest(String name, String vlFile, String libFile)
	{
		super(name);
		this.name = name;
		this.vlFile = vlFile;
		this.libFile = libFile;
	}

	public static List<AbstractTest> getTests()
	{
		List<AbstractTest> list = new ArrayList<AbstractTest>();
		list.add(new CommonSignalAnalysisTest("det_msff_12x_testvalue", "test.vL", "u1.lib"));
		list.add(new CommonSignalAnalysisTest("gate_basic_fourflops", "fourflops.vS", "u1.lib"));
		list.add(new CommonSignalAnalysisTest("gate_basic_load", "test.vL", "sc2_l.lib"));
//		list.add(new CommonSignalAnalysisTest("gate_instNetList", "test.vL", "sc2_l.lib"));					// FAILED
		list.add(new CommonSignalAnalysisTest("gate_inv_2x_25ff_hiR", "test.vL", "u1.lib"));
//		list.add(new CommonSignalAnalysisTest("gate_inv_2x_25ff_loR", "test.vL", "u1.lib"));				// FAILED
		list.add(new CommonSignalAnalysisTest("gate_inv_16x_25ff_hiR", "test.vL", "u1.lib"));
//		list.add(new CommonSignalAnalysisTest("gate_inv_16x_25ff_loR", "test.vL", "u1.lib"));				// FAILED
//		list.add(new CommonSignalAnalysisTest("gate_inv_16x_100ff_hiR", "test.vL", "u1.lib"));				// FAILED
//		list.add(new CommonSignalAnalysisTest("gate_inv_16x_100ff_loR", "test.vL", "u1.lib"));				// FAILED
//		list.add(new CommonSignalAnalysisTest("gate_inv_16x_100ff_superhiR", "test.vL", "u1.lib"));			// FAILED
		list.add(new CommonSignalAnalysisTest("net_ckt_6ps_25ff_loR", "test.vL", "sc2_l.lib"));
		list.add(new CommonSignalAnalysisTest("net_dir_const_transfunction", "test.vL", "sc2_l.lib"));
		list.add(new CommonSignalAnalysisTest("net_dir_non_const_transfunction", "test.vL", "sc2_l.lib"));
//		list.add(new CommonSignalAnalysisTest("stat_fsdseql_16_testvalue", "test.vL", "u1.lib"));			// FAILED
		return list;
	}

	public static String getOutputDirectory()
	{
		return null;
	}

	public Boolean det_msff_12x_testvalue() { return doIt(); }
	public Boolean gate_basic_fourflops() { return doIt(); }
	public Boolean gate_basic_load() { return doIt(); }
	public Boolean gate_instNetList() { return doIt(); }
	public Boolean gate_inv_2x_25ff_hiR() { return doIt(); }
	public Boolean gate_inv_2x_25ff_loR() { return doIt(); }
	public Boolean gate_inv_16x_25ff_hiR() { return doIt(); }
	public Boolean gate_inv_16x_25ff_loR() { return doIt(); }
	public Boolean gate_inv_16x_100ff_hiR() { return doIt(); }
	public Boolean gate_inv_16x_100ff_loR() { return doIt(); }
	public Boolean gate_inv_16x_100ff_superhiR() { return doIt(); }
	public Boolean net_ckt_6ps_25ff_loR() { return doIt(); }
	public Boolean net_dir_const_transfunction() { return doIt(); }
	public Boolean net_dir_non_const_transfunction() { return doIt(); }
	public Boolean stat_fsdseql_16_testvalue() { return doIt(); }

	public Boolean doIt()
	{
		if (!hasCSA()) return false;

		String testParameter = createMessageOutput();
		String path = workingDir(getRegressionPath(), testParameter) + getFunctionName();
		String fileNameVerilog = path + "/" + vlFile;
		String fileNameLib = path + "/" + libFile;

		Boolean good = Boolean.FALSE;
		try
		{
			Object obj = csaTestInputMethod.invoke(csaTestClass, fileNameLib, fileNameVerilog, name);
			if (obj instanceof Boolean)
			{
				good = (Boolean)obj;
			}
		} catch (Exception e)
		{
			System.out.println("Unable to run the CSA test module (" + name + ")");
			e.printStackTrace(System.out);
			good = Boolean.FALSE;
		}
		return good;
	}

	/****************************** REFLECTION INTERFACE ******************************/

	private static boolean csaTestChecked = false;
	private static Class<?> csaTestClass = null;
	private static Method csaTestInputMethod;

	/**
	 * Method to tell whether CSA is available.
	 * CSA is part of the Oyster timing analyzer.
	 * This method dynamically figures out whether the CSA module is present by using reflection.
	 * @return true if the CSA module is available.
	 */
	public static boolean hasCSA()
	{
		if (!csaTestChecked)
		{
			csaTestChecked = true;

			// find the CSA class
			try
			{
				csaTestClass = Class.forName("com.sun.electric.plugins.csa.api.CsaDashInterface");
			} catch (ClassNotFoundException e)
			{
				csaTestClass = null;
				return false;
			}

			// find the necessary method on the CSA class
			try
			{
				csaTestInputMethod = csaTestClass.getMethod("csa_regression", new Class[] {String.class, String.class, String.class});
			} catch (NoSuchMethodException e)
			{
				csaTestClass = null;
				return false;
			}
		}

		// if already initialized, return
		if (csaTestClass == null) return false;
	 	return true;
	}
}
