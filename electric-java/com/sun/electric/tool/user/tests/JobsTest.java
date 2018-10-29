	/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: JobsTest.java
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
import com.sun.electric.database.id.CellId;
import com.sun.electric.tool.Consumer;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.MultiTaskJob;
import com.sun.electric.tool.user.User;
import com.sun.electric.tool.user.UserInterfaceMain;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Class to test Job control.
 */
public class JobsTest extends AbstractTest
{
	EditingPreferences ep = UserInterfaceMain.getEditingPreferences();

	public JobsTest(String name, boolean interactive)
	{
		super(name, false, interactive);
	}

	public static List<AbstractTest> getTests()
	{
		List<AbstractTest> list = new ArrayList<AbstractTest>();
		list.add(new JobsTest("Terminate", true));
		list.add(new JobsTest("MultiTask", true));
		list.add(new JobsTest("HangingReferenceJob", false));
		list.add(new JobsTest("PrintJob", false));
		list.add(new JobsTest("InfiniteLoopJob", true));
		return list;
	}

	public static String getOutputDirectory()
	{
		String rootPath = User.getRegressionPath();
		if (rootPath == null) return null;
		return rootPath + "/tools/Database/output/";
	}

	/************************************* Terminate *********************************************************/

	public Boolean Terminate()
	{
		new FakeJob(Job.Type.CHANGE, false);
		new FakeJob(Job.Type.SERVER_EXAMINE, false);
//		new FakeJob(Job.Type.CLIENT_EXAMINE, false);
		new FakeJob(Job.Type.CHANGE, true);
		new FakeJob(Job.Type.SERVER_EXAMINE, true);
//		new FakeJob(Job.Type.CLIENT_EXAMINE, true);
		return Boolean.TRUE;
	}

	private static class FakeJob extends Job
	{
		Job.Type jobType;
		String field;
		boolean fail;

		FakeJob(Job.Type jobType, boolean fail)
		{
			super("JobTest", null, jobType, null, null, null);
			this.jobType = jobType;
			this.fail = fail;
			startJob();
		}

		@Override
		public boolean doIt()
		{
			System.out.println("doIt " + jobType + " fail=" + fail);
			field = "Value";
			fieldVariableChanged("field");
			if (fail)
			{
				int x = 0;
				int y = 1/x;
				if (y == 0) return false;
			}
			return true;
		}

		@Override
		public void terminateOK()
		{
			System.out.println("TerminateOK " + jobType + " fail=" + fail + " field=" + field);
		}

		@Override
		public void terminateFail(Throwable e)
		{
			System.out.println("TerminateFail " + jobType + " fail=" + fail + " e=" + e + " field=" + field);
			super.terminateFail(e);
		}
	}

	/************************************* MultiTask *********************************************************/

	private static Boolean multiTaskTestDone;

	public Boolean MultiTask()
	{
		multiTaskTestDone = null;
		final double expectedResult = 9900;
		Consumer<Double> consumer = new Consumer<Double>()
		{
			public void consume(Double result)
			{
				System.out.println("Server " + result + " expected " + expectedResult);
				boolean good = result.equals(expectedResult);
				multiTaskTestDone = Boolean.valueOf(good);
//				getStarterJob().updateTestResult(good);
			}
		};
		LauncherJob lj = new LauncherJob(100, consumer);
		lj.startJob();
		try
		{
			for(;;)
			{
				lj.wait();
				if (lj.isFinished()) break;
			}
		} catch (Exception e) {}
		return multiTaskTestDone;
	}

	private static class LauncherJob extends MultiTaskJob<Integer,Point2D,Double>
	{
		private int numTasks;

		LauncherJob(int numTasks, Consumer<Double> consumer)
		{
			super("MultiTaskJobTest", User.getUserTool(), consumer);
			this.numTasks = numTasks;
		}

		@Override
		public void prepareTasks()
		{
			for (int i = 0; i < numTasks; i++)
				startTask("Task " + i, Integer.valueOf(i));
		}

		@Override
		public Point2D runTask(Integer taskKey)
		{
			return new Point2D.Double(taskKey, taskKey);
		}

		@Override
		public Double mergeTaskResults(Map<Integer,Point2D> taskResults)
		{
			double result = 0;
			for (Map.Entry<Integer,Point2D> e: taskResults.entrySet())
			{
				Point2D p = e.getValue();
				System.out.println(e.getKey() + ": " + p);
				result += p.getX() + p.getY();
			}
			return Double.valueOf(result);
		}
	}

	/************************************* HangingReferenceJob *********************************************************/

	public Boolean HangingReferenceJob()
	{
		String libName = "hangRef";
		Library lib = Library.findLibrary(libName);
		if (lib == null)
			lib = Library.newInstance(libName, null);
		Cell refCell = Cell.newInstance(lib, "referenced{ic}");
		CellId refCellId = refCell.getId();
		Cell cell = Cell.newInstance(lib, "hangRef{sch}");
		cell.newVar("ATTR_FOO", refCellId, ep);
		refCell.kill();
		return Boolean.TRUE;
	}

	/************************************* PrintJob *********************************************************/

	public Boolean PrintJob()
	{
		String longString = "qqwqweqwerwefasdfvadg;ladfladjfl;gjadlfbvaldkmvalkml;vasvlkjaslkvjalskvjlakssjvlkasjvlkadfjvlkadjfvlkjadlkvjalkdjvlkadfjvlkadjvlkajvlakjvladkjvladkjvlkajvlkadjf";
		for (int i = 0; i < 1000; i++)
			System.out.println(longString);
		return Boolean.TRUE;
	}

	/************************************* InfiniteLoopJob *********************************************************/

	public Boolean InfiniteLoopJob()
	{
		for (;;)
		{
			if (getStarterJob().checkAbort()) break;
			if (false) break;
		}
		return Boolean.TRUE;
	}
}
