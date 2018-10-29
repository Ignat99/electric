/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: FakeTestJob.java
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
import com.sun.electric.database.text.Setting;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.JobException;
import com.sun.electric.tool.io.FileType;
import com.sun.electric.tool.io.input.LibraryFiles;
import com.sun.electric.tool.user.ErrorLogger;
import com.sun.electric.tool.user.User;
import com.sun.electric.util.ElapseTimer;

import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.swing.SwingUtilities;

public class FakeTestJob extends Job
{
	private List<AbstractTest> list;
	private boolean result;
	private int whichTest, numPassed, numFailed;
	private ErrorLogger errorLogger;
	private List<URL> requiredLibraries;
	private static ElapseTimer theTimer;

	public FakeTestJob(AbstractTest test)
	{
		this(Collections.singletonList(test));
	}

	public FakeTestJob(List<AbstractTest> list)
	{
		this(list, 0, 0, 0, User.getRegressionPath(), ErrorLogger.newInstance(list.get(0).getClass().getName() + " Tests"));
	}

	private FakeTestJob(List<AbstractTest> list, int nextTest, int numPassed, int numFailed, String regressionPath, ErrorLogger errorLogs)
	{
		// does it have to be change?
		super("Test " + (list.size() == 1 ? "" : (nextTest+1) + " of " + list.size() + ": ") + list.get(nextTest).getFullTestName(),
			User.getUserTool(), Job.Type.CHANGE, null, null, Job.Priority.USER);
		this.list = list;
		this.numPassed = numPassed;
		this.numFailed = numFailed;
		whichTest = nextTest;
		if (nextTest == 0) theTimer = ElapseTimer.createInstance().start();
		System.out.println("\n****************** TEST " + list.get(whichTest).getFullTestName());
		errorLogger = errorLogs;
		errorLogger.disablePopups();
		boolean guiTests = (list.get(nextTest) instanceof AbstractGUITest);
		if (guiTests)
		{
			requiredLibraries = new ArrayList<URL>();
			for (AbstractTest t: list)
			{
				if (!(t instanceof AbstractGUITest)) continue;
				t.setStarterJob(this);
				requiredLibraries.addAll(((AbstractGUITest)t).getRequiredLibraries(t.workingDir()));
			}
		}
		startJob();
	}

	@Override
	public boolean doIt() throws JobException
	{
		EditingPreferences ep = getEditingPreferences();
		AbstractTest test = list.get(whichTest);
		if (test == null) throw new JobException("Test is null");
		boolean guiTests = (test instanceof AbstractGUITest);
		test.setStarterJob(this);

		Setting.SettingChangeBatch restoreBatch = new Setting.SettingChangeBatch();
		Setting.SettingChangeBatch resetBatch = new Setting.SettingChangeBatch();
		for (Map.Entry<Setting,Object> e: getDatabase().getSettings().entrySet())
		{
			Setting setting = e.getKey();
			Object value = e.getValue();
			restoreBatch.add(setting, value);
			resetBatch.add(setting, setting.getFactoryValue());
		}
		getDatabase().implementSettingChanges(resetBatch);
		try
		{
			if (guiTests)
			{
				for (URL libFileURL: requiredLibraries)
				{
					LibraryFiles.readLibrary(ep, libFileURL, null, FileType.JELIB, false);
				}
			} else
			{
				Method t = test.getClass().getMethod(test.getFunctionName(), new Class[] {});
				Object o = t.invoke(test, new Object[] {});
				if (o != null && o instanceof Boolean) result = ((Boolean)o).booleanValue();
			}
		} catch (Exception e)
		{
			e.printStackTrace();
		} finally
		{
			getDatabase().implementSettingChanges(restoreBatch);
			Job.getUserInterface().saveMessages(null);
		}
		if (!test.isMultiTask() && !guiTests)
			updateTestResult(result);
		if (guiTests)
		{
			AbstractGUITest gTest = (AbstractGUITest)test;
			if (!gTest.phase1()) updateTestResult(false);
		}
		return true;
	}

	@Override
	public void terminateOK()
	{
		AbstractTest test = (AbstractTest)list.get(whichTest);
		if (!(test instanceof AbstractGUITest)) return;
		final AbstractGUITest fTest = (AbstractGUITest)test;
		if (fTest.phase2()) {
			SwingUtilities.invokeLater(new Runnable()
			{
				public void run() { updateTestResult(fTest.phase3()); }
			});
		} else
		{
			updateTestResult(false);
		}
	}

	void updateTestResult(boolean result)
	{
		AbstractTest test = list.get(whichTest);

		String msg = "****************** TEST " + test.getFullTestName() + (result ? " PASSED" : " FAILED");
		System.out.println(msg);

		// update test results
		if (result) numPassed++; else numFailed++;

		if (result)
			errorLogger.logWarning(msg, null, -1);
		else
			errorLogger.logError(msg, -1);

		// see if there are more tests to run
		whichTest++;
		if (whichTest < list.size())
		{
			new FakeTestJob(list, whichTest, numPassed, numFailed, User.getRegressionPath(), errorLogger);
			return;
		}
		else
			errorLogger.termLogging(true);

		// give final result
		if (numPassed + numFailed == 1)
		{
			// ran only 1 test
			String message = result ? "Test passed" : "Test failed";
			Job.getUserInterface().showInformationMessage(message, test.getFullTestName());
		} else
		{
			String message = "";
			if (numPassed > 0) message += numPassed + " tests passed";
			if (numFailed > 0)
			{
				if (message.length() > 0) message += ", ";
				message += numFailed + " tests failed";
			}
			theTimer.end();
			message += " (took " + theTimer + ")";
			Job.getUserInterface().showInformationMessage(message, test.getFullTestName());
		}
	}
}
