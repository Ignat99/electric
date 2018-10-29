/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: SkeyTreeJob.java
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
package com.sun.electric.tool.generator.flag.hornFunnel2;

import java.util.List;

import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.View;
import com.sun.electric.database.network.NetworkTool;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.JobException;
import com.sun.electric.tool.generator.layout.LayoutLib;
import com.sun.electric.tool.ncc.basic.CellContext;
import com.sun.electric.tool.ncc.basic.NccUtils;
import com.sun.electric.tool.ncc.result.NccResults;
import com.sun.electric.tool.user.ncc.NccMsgsFrame;

/* Implements the NCC user command */
public class SkewTreeJob extends Job {
    static final long serialVersionUID = 0;

	private void prln(String s) {System.out.println(s);}
	private void pr(String s) {System.out.print(s);}
	
	// Some day we may run this on server
	@Override
    public boolean doIt() {
		SkewTree.doIt(getEditingPreferences());
		return true;
    }
    @Override
    public void terminateOK() {}

	// ------------------------- public methods -------------------------------
    
	public SkewTreeJob() {
		super("Run SkewTree", NetworkTool.getNetworkTool(), Job.Type.CHANGE, null, 
			  null, Job.Priority.ANALYSIS);
		
		startJob();
	}
}
