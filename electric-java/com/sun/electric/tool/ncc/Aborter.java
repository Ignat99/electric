/* -*- tab-width: 4 -*-
*
* Electric(tm) VLSI Design System
*
* File: Aborter.java
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
package com.sun.electric.tool.ncc;

import com.sun.electric.tool.Job;
import com.sun.electric.tool.JobException;

/**
 * Indicate when user wants to abort
 */
public class Aborter {
	private final Job job;
	public Aborter(Job j) {job=j;}

	public boolean userWantsToAbort() {
		if (job==null) return false;
		try {
			// Eventually, job.checkAbort() will throw an exception
			if (job.checkAbort()) throw new JobException();
		} catch (JobException e) {
			return true;
		}
		return false;
	}
}
