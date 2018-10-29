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
import java.util.StringTokenizer;

public class TimingAnalysisTest extends AbstractTest
{
	private String name, functionName, dspfFile, cmdFile, gcfFile, sdcFile, oysterNodesFile, oysterReportFile;
	private List<String> libFiles, vlFiles;
	private Boolean splitNodes;

	/** Creates a new instance of TimingAnalysisTest */
	public TimingAnalysisTest(String name)
	{
		super(name);
		functionName = name;
	}

	public TimingAnalysisTest(String name, String functionName, String vlFiles, String libFiles, String dspfFile,
							  String cmdFile, String gcfFile, String sdcFile,
							  String oysterOriginal, String reportFile, Boolean split)
	{
		super(name);
		this.name = name;
		this.functionName = functionName;
		this.vlFiles = new ArrayList<String>();
		this.libFiles = new ArrayList<String>();
		this.dspfFile = dspfFile;
		this.cmdFile = cmdFile;
		this.gcfFile = gcfFile;
		this.sdcFile = sdcFile;
		this.oysterNodesFile = oysterOriginal;
		this.oysterReportFile = reportFile;
		this.splitNodes = split;

		if (vlFiles != null)
		{
			StringTokenizer parse = new StringTokenizer(vlFiles, "{, }", false); // extracting lib names
			while (parse.hasMoreTokens())
			{
				this.vlFiles.add(parse.nextToken());
			}
		}
		if (libFiles != null)
		{
			StringTokenizer parse = new StringTokenizer(libFiles, "{, }", false); // extracting lib names
			while (parse.hasMoreTokens())
			{
				this.libFiles.add(parse.nextToken());
			}
		}
	}

	public static List<AbstractTest> getTests()
	{
		List<AbstractTest> list = new ArrayList<AbstractTest>();
		// Tests not available due to privacy issues
		list.add(new TimingAnalysisTest("controlblock with no split", "controlblock", "rk_cb_dsu_cms.vL", "data/u1.lib, data/u1fib.lib", "data/rk_cb_dsu_cms.dspf",
				"rk_cb_dsu_cms.cmd", null, "rk_cb_dsu_cms.sdc", "report.graph", "nodereport", false));
		list.add(new TimingAnalysisTest("controlblock with split", "controlblock", "rk_cb_dsu_cms.vL", "data/u1.lib, data/u1fib.lib", "data/rk_cb_dsu_cms.dspf",
				"rk_cb_dsu_cms.cmd", null, "rk_cb_dsu_cms.sdc", "report.graph", "nodereport", true));

		list.add(new TimingAnalysisTest("rk_m_stadd64 with no split", "rk_m_stadd64", "rk_m_stadd64.vL", "data/u1.lib, ", "rk_m_stadd64.dspf",
				"rk_m_stadd64.cmd", null, "rk_m_stadd64.sdc", "report.graph", "nodereport", false));
		list.add(new TimingAnalysisTest("rk_m_stadd64 with split", "rk_m_stadd64", "rk_m_stadd64.vL", "data/u1.lib, ", "rk_m_stadd64.dspf",
				"rk_m_stadd64.cmd", null, "rk_m_stadd64.sdc", "report.graph", "nodereport", true));
		// Tests in CVS
		list.add(new TimingAnalysisTest("fastProx with no split", "fastProx", "input/testFlops.v", "input/flops.MAX.lib,input/sclibTSMC90.MAX.lib",
				"input/testFlops.dspf", "fastProx.cmd", null, "fastProx.sdc", "oysterData/report.graph", "oysterData/node-report", false));
		list.add(new TimingAnalysisTest("fastProx with split", "fastProx", "input/testFlops.v", "input/flops.MAX.lib,input/sclibTSMC90.MAX.lib",
				"input/testFlops.dspf", "fastProx.cmd", null, "fastProx.sdc", "oysterData/report.graph", "oysterData/node-report", true));

		list.add(new TimingAnalysisTest("main_t1 with no split", "main_t1", "{input/main.v}", "input/sc.lib", "input/main.dspf", "main_t1.cmd", null,
				"main_t1.sdc", "oysterData/report.graph", "oysterData/node-report", false)); // node-report.full.regression.MINMAX
		list.add(new TimingAnalysisTest("main_t1 with split", "main_t1", "{input/main.v}", "input/sc.lib", "input/main.dspf", "main_t1.cmd", null,
				"main_t1.sdc", "oysterData/report.graph", "oysterData/node-report", true)); // node-report.full.regression.MINMAX

		list.add(new TimingAnalysisTest("multithread_example with no split", "multithread_example", "input/multithread_example.vL", "input/multithread_example_lib.SynT",
				"input/multithread_example.dspf", "simple.cmd", null, "simple.sdc", "oysterData/report.graph", "oysterData/node-report", false));
		list.add(new TimingAnalysisTest("multithread_example with split", "multithread_example", "input/multithread_example.vL", "input/multithread_example_lib.SynT",
				"input/multithread_example.dspf", "simple.cmd", null, "simple.sdc", "oysterData/report.graph", "oysterData/node-report", true));
		return list;
	}

	public static String getOutputDirectory()
	{
		return null;
	}

	public Boolean controlblock() { return doIt(); }
	public Boolean fastProx() { return doIt(); }
	public Boolean main_t1() { return doIt(); }
	public Boolean multithread_example() { return doIt(); }
	public Boolean noise_ch_cq_ctl() { return doIt(); }
	public Boolean rk_m_stadd64() { return doIt(); }
	public Boolean timingmodel_case1() { return doIt(); }
	public String getFunctionName() { return functionName; }

	public Boolean doIt()
	{
		if (!hasOyster()) return false;

		String testParameter = createMessageOutput();

		String path = workingDir(getRegressionPath(), testParameter) + getFunctionName();
		List<String> fileNamesVerilog = new ArrayList<String>(vlFiles.size());
		for (String n : vlFiles)
			fileNamesVerilog.add(path + "/" + n);
		List<String> fileNameLibs = new ArrayList<String>(libFiles.size());
		for (String n : libFiles)
			fileNameLibs.add(path + "/" + n);
		String fileDspf = (dspfFile != null) ? path + "/" + dspfFile : null;
		String fileCmd = (cmdFile != null) ? path + "/" + cmdFile : null;
		String fileGcf = (gcfFile != null) ? path + "/" + gcfFile : null;
		String fileSdc = (sdcFile != null) ? path + "/" + sdcFile : null;
		String fileOysterGraph = (oysterNodesFile != null) ? path + "/" + oysterNodesFile : null;
		String fileOysterReport = (oysterReportFile != null) ? path + "/" + oysterReportFile : null;

		Boolean good = false;
		try
		{
			Object obj = oysterTestInputMethod.invoke(oysterTestClass, fileNameLibs, fileNamesVerilog,
					fileDspf, fileCmd, fileGcf, fileSdc, fileOysterGraph, fileOysterReport, name, splitNodes);
			if (obj instanceof Boolean)
			{
				good = (Boolean)obj;
			}
		} catch (Exception e)
		{
			System.out.println("Unable to run the Oyster test module (" + name + ")");
			e.printStackTrace(System.out);
			good = false;
		}
		return good;
	}

	/****************************** REFLECTION INTERFACE ******************************/

	private static boolean oysterTestChecked = false;
	private static Class<?> oysterTestClass = null;
	private static Method oysterTestInputMethod;

	/**
	 * Method to tell whether CSA is available.
	 * CSA is part of the Oyster timing analyzer.
	 * This method dynamically figures out whether the CSA module is present by using reflection.
	 * @return true if the CSA module is available.
	 */
	public static boolean hasOyster()
	{
		if (!oysterTestChecked)
		{
			oysterTestChecked = true;

			// find the CSA class
			try
			{
				oysterTestClass = Class.forName("com.sun.electric.plugins.oyster.OysterMain");
			} catch (ClassNotFoundException e)
			{
				oysterTestClass = null;
				return false;
			}

			// find the necessary method on the CSA class
			try
			{
				oysterTestInputMethod = oysterTestClass.getMethod("oyster_regression",
						List.class, List.class, String.class, String.class, String.class, String.class,
						String.class, String.class, String.class, Boolean.class);
			} catch (NoSuchMethodException e)
			{
				oysterTestClass = null;
				e.printStackTrace();
				return false;
			}
		}

		// if already initialized, return
		return (oysterTestClass != null);
	}
}
