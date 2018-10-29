/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: NccJob.java
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

import java.util.List;

import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.View;
import com.sun.electric.database.network.NetworkTool;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.ncc.basic.CellContext;
import com.sun.electric.tool.ncc.basic.NccUtils;
import com.sun.electric.tool.ncc.result.NccResults;
import com.sun.electric.tool.user.ncc.NccMsgsFrame;

/* Implements the NCC user command */
public class NccJob extends Job {
    static final long serialVersionUID = 0;

    /** Save the results from the last NCC run here. lastResults is static
     * because we want results to persist even after the job is done.
     * This makes it possible to do schematic / layout cross probing
     * after running NCC. */
    private static NccResults lastResults = null;
    
	/** Remember which Cells have already passed NCC. passed is
	 * static because the next run of NCC might want to skip 
	 * checking cells because they passed in the previous run of NCC. 
	 * passed is only used in interactive mode. */
	private static PassedNcc passed = null;

	/** nccgui is static for efficiency. It is public because we want
	 * to share with com.sun.electric.plugins.pie.NccJob. nccgui
	 * is only used in interactive mode. Avoid initializing it
	 * in batch mode because it will throw an exception. */
    public static NccMsgsFrame nccgui = null;
    
	// These fields are arguments passed to server
	private final int numWindows;
	private final NccOptions options;
	private final CellContext[] cellCtxts;
	
	// This field is the result passed from the server to the client
	private NccResults results;

	private void prln(String s) {System.out.println(s);}
	private void pr(String s) {System.out.print(s);}
	
	private void initInteractiveStatics() {
		if (passed==null) passed = new PassedNcc();
		if (nccgui==null) nccgui = new NccMsgsFrame();
	}
	
	private CellContext[] getSchemLayFromCurrentWindow() {
		CellContext curCellCtxt = NccUtils.getCurrentCellContext();
		if (curCellCtxt==null) {
			prln("Please open the Cell you wish to NCC");
			return null;
		}
		Cell[] schLay = NccUtils.findSchematicAndLayout(curCellCtxt.cell);
		if (schLay==null) {
			prln("current Cell Group doesn't have both schematic and layout Cells");
			return null;
		}
		CellContext[] cc = {
			new CellContext(schLay[0], curCellCtxt.context),
			new CellContext(schLay[1], curCellCtxt.context)
		};
		return cc;
	}
	
	private boolean isSchemOrLay(CellContext cc) {
		Cell c = cc.cell;
		View v = c.getView();
		boolean ok = c.isSchematic() || v==View.LAYOUT;
		if (!ok) prln("Cell: "+NccUtils.fullName(c)+
									" isn't schematic or layout");
		return ok;
	}

	private boolean isSchem(CellContext cc) {
		return cc.cell.isSchematic();
	}

	/** If one Cell is a schematprlnst
	 * @return null if not schematic or layout Cells */ 
	private CellContext[] getTwoCellsFromTwoWindows() {
		List<CellContext> cellCtxts = NccUtils.getCellContextsFromWindows();
		if (cellCtxts.size()<2) {
			prln("Two Cells aren't open in two windows");
			return null;
		} 
		if (cellCtxts.size()>2) {
			prln("More than two Cells are open in windows. Could you please");
			prln("close windows until only two Cells are open. (Sorry JonL.)");
			return null;
		}
		CellContext[] cellContexts = new CellContext[2];
		cellContexts[0] = (CellContext) cellCtxts.get(0);
		cellContexts[1] = (CellContext) cellCtxts.get(1);
	
		if (!isSchemOrLay(cellContexts[0]) || 
		    !isSchemOrLay(cellContexts[1])) return null;

		// Try to put schematic first
		if (!isSchem(cellContexts[0]) && isSchem(cellContexts[1])) {
			CellContext cc = cellContexts[0];
			cellContexts[0] = cellContexts[1];
			cellContexts[1] = cc;
		}

	    return cellContexts;
	}

	/** @return array of two CellContexts to compare */
	private CellContext[] getCellsFromWindows(int numWindows) {
		if (numWindows==2) return getTwoCellsFromTwoWindows();
		else return getSchemLayFromCurrentWindow();
	}
	
	// Some day we may run this on server
	@Override
    public boolean doIt() {
		if (cellCtxts==null) {
			// null results means couldn't run NCC
			results = null;
			return true;
		}
		
		passed.removeCellsChangedSinceLastNccRun(cellCtxts);

		results = Ncc.compare(cellCtxts[0].cell, cellCtxts[0].context,
				              cellCtxts[1].cell, cellCtxts[1].context, 
					  	      passed, options, this);

		fieldVariableChanged("results");

		return true;
    }
    @Override
    public void terminateOK() {
    	lastResults = results;
    	if (results==null) return; // NCC couldn't even run
    	
        nccgui.setMismatches(results.getAllComparisonMismatches(), options);
        NccJob.nccgui.display();
    }

	// ------------------------- public methods -------------------------------
    
    /** Get the results from the last NCC run. If null then there are no
     * valid results. */
    public static NccResults getLastNccResults() {return lastResults;}
    	
    /** Call this if you modify the design or preferences so that the 
     * results from the last NCC run are discarded. */
    public static void invalidateLastNccResult() {
    	lastResults=null;
    	passed = null;
    }

	/**
	 * Run a NCC job.
	 * @param numWind may be 1 or 2. 1 means compare the schematic and layout 
	 * views of the current window. 2 means compare the 2 Cells open in 2 Windows.
	 */
	public NccJob(int numWind) {
		super("Run NCC", NetworkTool.getNetworkTool(), Job.Type.SERVER_EXAMINE, null,
			  null, Job.Priority.ANALYSIS);
		
		// Set up arguments for doIt() method that is run on server
		numWindows = numWind;
		error(numWindows!=1 && numWindows!=2,
		                "numWindows must be 1 or 2");
		cellCtxts = getCellsFromWindows(numWindows);
		
		options = NccOptions.getOptionsFromNccPreferences();
		
		// abandon results from last run in order to reclaim storage
		results = null;
		
		initInteractiveStatics();
		
		startJob();
	}
}
