/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: TaskPrinter.java
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
package com.sun.electric.tool.generator.flag;

import com.sun.electric.tool.Job;

/** TaskPrinter will describe the task at hand only if I need to report
 * that something went wrong. */ 
public class TaskPrinter {
	private final StringBuffer taskDescription = new StringBuffer();
	private boolean taskDescriptionPrinted = false;
	private void printTaskDescription() {
		if (taskDescriptionPrinted) return;
		System.out.println(taskDescription.toString());
		taskDescriptionPrinted = true;
	}
	public void saveTaskDescription(String msg) {
		taskDescription.setLength(0);
		taskDescriptionPrinted = false;
		taskDescription.append(msg);
	}
	public void clearTaskDescription() {
		taskDescriptionPrinted = false;
	}
	public void prln(String s) {
		printTaskDescription();
		System.out.println(s);
	}
	public void pr(String s) {
		printTaskDescription();
		System.out.print(s);
	}
	public void error(boolean cond, String msg) {
		if (cond) {
			printTaskDescription();
			Job.error(true, msg);
		}
	}
}
