/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: EvalJython.java
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
package com.sun.electric.tool.lang;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLConnection;

import com.sun.electric.database.EditingPreferences;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.id.CellId;
import com.sun.electric.database.text.TextUtils;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.variable.TextDescriptor;
import com.sun.electric.database.variable.Variable;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.JobException;
import com.sun.electric.tool.user.Resources;
import com.sun.electric.tool.user.User;

public class EvalJython {

    private static boolean jythonChecked = false;
    private static boolean jythonInited = false;
    private static Class<?> jythonClass;
    private static Object jythonInterpreterObject;
    private static Method jythonExecMethod;

    public static boolean hasJython() {
        if (!jythonChecked) {
            jythonChecked = true;

            // find the Jython class
            jythonClass = Resources.getJythonClass("PythonInterpreter");
            if (jythonClass == null)
            	TextUtils.recordMissingComponent("Jython");
        }

        // if already initialized, return state
        return jythonClass != null;
    }

    private static void initJython() {
        if (!hasJython()) {
            return;
        }
        if (jythonInited) {
            return;
        }
        try {
            Constructor<?> instance = jythonClass.getDeclaredConstructor();
            jythonInterpreterObject = instance.newInstance();
            jythonExecMethod = jythonClass.getMethod("exec", new Class[]{String.class});
            jythonExecMethod.invoke(jythonInterpreterObject, new Object[]{"import sys"});
        } catch (Exception e) {
            jythonClass = null;
        }
        jythonInited = true;
    }

    /**
     * Method to run the Jython interpreter on a given line of text.
     * Uses reflection to invoke the Jython interpreter (if it exists).
     * @param code the code to run.
     */
    public static void execJython(String code) {
        try {
            jythonExecMethod.invoke(jythonInterpreterObject, new Object[]{code});
            return;
        } catch (Exception e) {
            e.printStackTrace();
            Throwable ourCause = e.getCause();
            if (ourCause != null) {
                System.out.println("Jython: " + ourCause);
            } else {
                System.out.println("Jython error");
            }
        }
    }

    /**
     * Method to execute a script file in a Job.
     * @param fileName the script file name.
     */
    public static void runScript(String fileName) {
        (new EvalJython.RunJythonScriptJob(fileName)).startJob();
    }

    /**
     * Method to execute a script file without starting a new Job.
     * @param fileName the script file name.
     */
    public static void runScriptNoJob(String fileName) {
        URL url = TextUtils.makeURLToFile(fileName);
        try {
            URLConnection urlCon = url.openConnection();
            InputStreamReader is = new InputStreamReader(urlCon.getInputStream());
            LineNumberReader lineReader = new LineNumberReader(is);
            StringBuffer sb = new StringBuffer();
            String sep = System.getProperty("line.separator");
            for (;;) {
                String buf = lineReader.readLine();
                if (buf == null) {
                    break;
                }
                sb.append(buf);
                sb.append(sep);
            }
            lineReader.close();
            initJython();
            execJython(sb.toString());
        } catch (IOException e) {
            System.out.println("Error reading " + fileName);
        }
    }

    /**
     * returns EditingPreferences with default sizes and text descriptors
     * @return EditingPreferences with default sizes and text descriptors
     */
    public static EditingPreferences getEditingPreferences() {
        return Job.getRunningJob().getEditingPreferences();
    }

    /**
     * Display specified Cell after termination of currently running script
     * @param cell the Cell to display.
     */
    public static void displayCell(Cell cell) {
        Job curJob = Job.getRunningJob();
        if (curJob instanceof RunJythonScriptJob) {
            ((RunJythonScriptJob) curJob).displayCell(cell);
        }
    }

    @SuppressWarnings("serial")
	private static class RunJythonScriptJob extends Job {

        private String script;
        private Cell cellToDisplay;

        public RunJythonScriptJob(String script) {
            super("Jython script: " + script, User.getUserTool(), Job.Type.CHANGE, null, null, Job.Priority.USER);
            this.script = script;
            cellToDisplay = null;
        }

        public boolean doIt() throws JobException {
            runScriptNoJob(script);
            return true;
        }

        private void displayCell(Cell cell) {
            if (cellToDisplay != null) {
                return;
            }
            cellToDisplay = cell;
            fieldVariableChanged("cellToDisplay");
        }

        @Override
        public void terminateOK() {
            if (cellToDisplay != null) {
                Job.getUserInterface().displayCell(cellToDisplay);
            }
        }
    }

    /**
	 * Method to set a variable on a NodeInst in a new Job.
	 * It is necessary to use IDs because Jython makes copies of objects,
	 * so the Jython copy of the NodeInst is "not linked".
	 * @param cellId the ID of the Cell in which to set a variable.
	 * @param nodeId the ID of the NodeInst (in the Cell) on which to set a variable.
	 * @param key the Variable key.
	 * @param newVal the new value of the Variable.
	 * @param td the Textdescriptor.
	 */
	public static void setVarInJob(CellId cellId, int nodeId, Variable.Key key, Object newVal, TextDescriptor td)
	{
		new SetVarJob(cellId, nodeId, key, newVal, td);
	}

	/**
	 * Class for setting a variable in a new Job.
	 */
	@SuppressWarnings("serial")
	private static class SetVarJob extends Job
	{
		private CellId cellId;
		private int nodeId;
		private Variable.Key key;
		private Object newVal;
		private TextDescriptor td;

		protected SetVarJob(CellId cellId, int nodeId, Variable.Key key, Object newVal, TextDescriptor td)
		{
			super("Add Variable", User.getUserTool(), Job.Type.CHANGE, null, null, Job.Priority.USER);
			this.cellId = cellId;
			this.nodeId = nodeId;
			this.key = key;
			this.newVal = newVal;
			this.td = td;
			startJob();
		}

		public boolean doIt() throws JobException
		{
			Cell c = Cell.inCurrentThread(cellId);
			NodeInst ni = c.getNodeById(nodeId);
			ni.newVar(key, newVal, td);
			return true;
		}
	}
}
