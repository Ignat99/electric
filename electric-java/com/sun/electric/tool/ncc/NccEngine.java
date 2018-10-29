/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: NccEngine.java
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

/**
 * NccEngine performs an n-way netlist comparison.  NccEngine 
 * must be passed all Ncc options as procedural arguments; it does not access 
 * the User NCC options directly.  This allows programs to call the 
 * engine without becoming involved with how User's options are stored.
 */
package com.sun.electric.tool.ncc;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.variable.VarContext;
import com.sun.electric.tool.ncc.basic.NccUtils;
import com.sun.electric.tool.ncc.netlist.NccNetlist;
import com.sun.electric.tool.ncc.netlist.Part;
import com.sun.electric.tool.ncc.netlist.Wire;
import com.sun.electric.tool.ncc.processing.ExportChecker;
import com.sun.electric.tool.ncc.processing.ForceMatch;
import com.sun.electric.tool.ncc.processing.HashCodePartitioning;
import com.sun.electric.tool.ncc.processing.HierarchyInfo;
import com.sun.electric.tool.ncc.processing.LocalPartitionResult;
import com.sun.electric.tool.ncc.processing.LocalPartitioning;
import com.sun.electric.tool.ncc.processing.ReportHashCodeFailure;
import com.sun.electric.tool.ncc.processing.SerialParallelMerge;
import com.sun.electric.tool.ncc.result.NccResult;
import com.sun.electric.tool.ncc.result.BenchmarkResults.BenchIdx;
import com.sun.electric.tool.ncc.strategy.StratCheckSizes;
import com.sun.electric.tool.ncc.trees.Circuit;
import com.sun.electric.tool.ncc.trees.EquivRecord;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/** NccEngine performs the topological comparison of two Cells.
 * These Cells are from the same
 * CompareList and are supposed to be topologically
 * identical.
 * 
 * Note that NCC calls NccEngine.compare() many times. For example,
 * if every CompareList contained exactly two Cells, then NCC
 * would call NccEngine.compare() once for each CompareList.
 * 
 *  NCC calls NccEngine.compare() on the leaf Cells first, and proceeds 
 *  upwards towards the
 *  root. The object, HierarchyInfo, stores information that NCC
 *  must pass from lower level comparisons to higher level comparisons. 
 */
public class NccEngine {
	// ------------------------------ private data ----------------------------
	private NccGlobals globals;

	// ----------------------- private methods --------------------------------
	// return null if user aborts
	private List<NccNetlist> buildNccNetlists(List<Cell> cells, 
			                                  List<VarContext> contexts, 
			                                  boolean blackBox, 
			                                  HierarchyInfo hierInfo) {
		globals.error(cells.size()!=contexts.size(),
					  "number of cells, and contexts must be the same");										
		List<NccNetlist> nccLists = new ArrayList<NccNetlist>();
		Iterator<Cell> itCell;
		Iterator<VarContext> itCon;
		for (itCell=cells.iterator(),
			 itCon=contexts.iterator(); itCell.hasNext();) {
			Cell cell = itCell.next();
			VarContext context = itCon.next();
			NccNetlist nccList = new NccNetlist(cell, context,
			                                    hierInfo, blackBox, globals);
			if (nccList.userAbort()) return null;
			nccLists.add(nccList);
		}
		return nccLists;
	}
	
	private int[] getNetObjCounts(EquivRecord rec, int numCells) {
		int[] counts = new int[numCells];
		// don't blow up if no parts or wires
		if (rec==null) return counts;
		int i=0;
		for (Iterator<Circuit> it=rec.getCircuits(); it.hasNext(); i++) {
			Circuit ckt = it.next();
			counts[i] = ckt.numNetObjs();
		}
		return counts;
	}
	
	/** Ivan wants this print out. */
	private void printWireComponentCounts() {
		int numCells = globals.getNumNetlistsBeingCompared();
		int[] partCounts = getNetObjCounts(globals.getParts(), numCells);
		int[] wireCounts = getNetObjCounts(globals.getWires(), numCells);
		int[] portCounts = getNetObjCounts(globals.getWires(), numCells);
		String[] cellNames = globals.getRootCellNames();
		VarContext[] contexts = globals.getRootContexts();
		for (int i=0; i<cellNames.length; i++) {
			globals.status1(
				"  Cell: "+cellNames[i]+" has "+wireCounts[i]+" wires, "+
				partCounts[i]+" parts, and "+portCounts[i]+
				" ports after series/parallel combination. "+
				"Instance path: "+
				contexts[i].getInstPath("/")
			);
		}
	}
	
	private NccResult designsMatch(HierarchyInfo hierInfo, 
			                       boolean hierInfoOnly) {
		long start = NccUtils.getTime();
        boolean noNetlists = globals.cantBuildNetlist();
        if (noNetlists) {
        	// Can't build some net list.  This is a mismatch.
			globals.status2("netlist error");
			
			// Preserve the invariant: "part, wire, and port LeafLists exist" 
			// even if there are no Parts and no Wires OR there is a netlist error. 
			globals.initLeafLists();
			
			return NccResult.newResult(false, false, false, globals);
		} else {
//			Date d0 = new Date();
			ExportChecker expCheck = new ExportChecker(globals);
			expCheck.markPortsForRenaming();
			
			boolean expNamesOK = expCheck.matchByName();

			if (expNamesOK) {
				expCheck.saveInfoNeededToMakeMeASubcircuit(hierInfo);
			} else { 
				if (hierInfo!=null) hierInfo.purgeCurrentCompareList();
			}
			
			if (globals.getRoot()==null) {
				// all Cells contain no Wires and no Parts. The cells match but the 
				// rest of NCC isn't going to be happy.  We'll have to exit early.
				globals.status2("all cells empty");
				
				// Preserve the invariant: "part, wire, and port LeafLists exist" 
				// even if there are no Parts and no Wires OR there is a netlist error. 
				globals.initLeafLists();
				
				return NccResult.newResult(true, true, true, globals);
			}
			
			// Useless so far
			//expCheck.printExportTypeWarnings();
			
			start = NccUtils.registerTiming("  Export name matching took: ",start,BenchIdx.EXPORT_MATCHING_TIME,globals);
//			Date d1 = new Date();
//			globals.status1("  Export name matching took: "+
//			                NccUtils.hourMinSec(d0, d1));
			
			if (globals.userWantsToAbort()) return NccResult.newUserAbortResult();
			
			SerialParallelMerge.doYourJob(globals);
			start = NccUtils.registerTiming("  Serial/parallel merge took: ",start,BenchIdx.MERGE_TIME,globals);
//			Date d2 = new Date();
//			globals.status1("  Serial/parallel merge took: "+
//					        NccUtils.hourMinSec(d1, d2));

			if (globals.userWantsToAbort()) return NccResult.newUserAbortResult();

			printWireComponentCounts();
			
			// We need to keep track of forcedParts and forcedWires because we 
			// mustn't repartition EquivRecords containing Parts and Wires that 
			// we explicitly forced to match!!!
			Set<Part> forcedParts = new HashSet<Part>();
			Set<Wire> forcedWires = new HashSet<Wire>();
			ForceMatch.doYourJob(forcedParts, forcedWires, globals);

			LocalPartitionResult localRes =
				LocalPartitioning.doYourJob(forcedParts, forcedWires, globals);

			if (globals.userWantsToAbort()) return NccResult.newUserAbortResult();

			// Tricky: init leaf lists after Local Partitioning because Local
			// Partitioning can make an EquivRecord change from matched to
			// mismatched!
			globals.initLeafLists();

			
			start = NccUtils.registerTiming("  Local partitioning took ",start,BenchIdx.LOCAL_PARTITIONING_TIME,globals);
//			Date d3 = new Date();
//			globals.status1("  Local partitioning took "+ 
//					        NccUtils.hourMinSec(d2, d3));


			boolean topoOK = HashCodePartitioning.doYourJob(globals);
			if (!localRes.matches()) {
				globals.getNccGuiInfo().setPartRecReports(localRes.getPartRecReports());
				globals.getNccGuiInfo().setWireRecReports(localRes.getWireRecReports());
				localRes.printErrorReport();
				return NccResult.newResult(expNamesOK, false, false, globals);
			}
				
			if (globals.userWantsToAbort()) return NccResult.newUserAbortResult();
			
			//Date d4 = new Date();
			start = NccUtils.getTime();
			boolean expTopoOK = true;
			if (topoOK) {
				expCheck.suggestPortMatchesBasedOnTopology();
				expTopoOK = expCheck.ensureExportsWithMatchingNamesAreOnEquivalentNets();
			}

			start = NccUtils.registerTiming("  Export checking took ",start,BenchIdx.EXPORT_CHECKING_TIME,globals);

 //           Date d5 = new Date();
//			globals.status1("  Export checking took "+NccUtils.hourMinSec(d4, d5));
            
			boolean sizesOK = StratCheckSizes.doYourJob(globals);

			start = NccUtils.registerTiming("  Size checking took ",start,BenchIdx.SIZE_CHECKING_TIME,globals);

//			Date d6 = new Date();
//			globals.status1("  Size checking took "+NccUtils.hourMinSec(d5, d6));
			
			if (!topoOK) {
				ReportHashCodeFailure hcf = new ReportHashCodeFailure(globals);
				globals.getNccGuiInfo().setPartRecReports(hcf.getPartRecReports());
				globals.getNccGuiInfo().setWireRecReports(hcf.getWireRecReports());
			}

			boolean exportsOK = expNamesOK && expTopoOK;
			boolean topologyOK = localRes.matches() && topoOK;
            
			return NccResult.newResult(exportsOK, topologyOK, sizesOK, globals);
		}
	}

	private NccResult areEquivalent(List<Cell> cells, List<VarContext> contexts, 
					  		        HierarchyInfo hierInfo,
					  		        boolean blackBox, 
					  		        NccOptions options, Aborter aborter) {
		globals = new NccGlobals(options, aborter);
		
		globals.status2("****************************************"+					  		
		                "****************************************");					  		

		// black boxing is implemented by building netlists that are empty 
		// except for their Exports.
		Date before = new Date();
		List<NccNetlist> nccNetlists = 
			buildNccNetlists(cells, contexts, blackBox, hierInfo);
		Date after = new Date();
		globals.status1("  NCC net list construction took "+NccUtils.hourMinSec(before, after)+".");

		// null list returned means user requested abort
		if (nccNetlists==null) return NccResult.newUserAbortResult();
        globals.setInitialNetlists(nccNetlists);
        
		NccResult result = designsMatch(hierInfo, false);
		
		if(result.match())
			NccUtils.incrementBenchmarkCount(BenchIdx.PASS_RESULT, globals);
		else{
			NccUtils.incrementBenchmarkCount(BenchIdx.FAIL_RESULT, globals);
		}
		
		globals.status2("****************************************"+					  		
		                "****************************************");
		return result;		              					  				
	}
	
	private static NccResult compare2(Cell cell1, VarContext context1, 
									  Cell cell2, VarContext context2, 
									  HierarchyInfo hierInfo,
									  boolean blackBox,
									  NccOptions options,
									  Aborter aborter) {
		ArrayList<Cell> cells = new ArrayList<Cell>();
		cells.add(cell1);
		cells.add(cell2);
		ArrayList<VarContext> contexts = new ArrayList<VarContext>();
		contexts.add(context1);
		contexts.add(context2);
				
		return compareMany(cells, contexts, hierInfo, blackBox, 
		                   options, aborter);
	}
	private static NccResult compareMany(List<Cell> cells, List<VarContext> contexts,
									     HierarchyInfo hierCompInfo,
									     boolean blackBox, 
									     NccOptions options, Aborter aborter) {
		NccEngine ncc = new NccEngine();
		return ncc.areEquivalent(cells, contexts, hierCompInfo, 
								 blackBox, options, aborter);
	}
	// -------------------------- public methods ------------------------------
	/** 
	 * Check to see if all cells are topologically equivalent.  Note that
	 * the NCC engine can compare any number of Cells at the same time.
	 * @param cells a list of cells to compare.
	 * @param contexts a list of VarContexts for the corresponding Cell. The
	 * VarContxt is used to evaluate schematic 
	 * variables. Use null if variables don't need to be evaluated. 
	 * @param hierCompInfo Information needed to perform hierarchical
	 * netlist comparison. For flat comparisons pass null.
	 * @param options NCC options
	 */
	public static NccResult compare(List<Cell> cells, List<VarContext> contexts,
	                                HierarchyInfo hierCompInfo, 
	                                NccOptions options, Aborter aborter) {
		return compareMany(cells, contexts, hierCompInfo, false, 
		                   options, aborter);
	}
	/** 
	 * Check to see if two cells are topologically equivalent.  
	 * @param cell1 the root cell for the first design
	 * @param context1 the VarContext for the first Cell
	 * @param cell2 the root cell for the second design
	 * @param context2 the VarContext for the second Cell
	 * @param hierInfo Information needed to perform hierarchical
	 * netlist comparison. For flat comparisons pass null.
	 * @param options NCC options
	 */
	public static NccResult compare(Cell cell1, VarContext context1, 
	                                Cell cell2, VarContext context2, 
	                                HierarchyInfo hierInfo, 
	                                NccOptions options, Aborter aborter) {
		return compare2(cell1, context1, cell2, context2, hierInfo, false, 
				        options, aborter);
	}
	/** 
	 * Instead of checking to see if two cells are topologically 
	 * equivalent, simply pretend that we've already compared them
	 * and found them to match. Put information into hierInfo that indicates
	 * that these two Cells match. 
	 * @param cell1 the root cell for the first design
	 * @param ctxt1 the VarContext for the first Cell
	 * @param cell2 the root cell for the second design
	 * @param ctxt2 the VarContext for the second Cell
	 * @param hierInfo Information needed to perform hierarchical
	 * netlist comparison. For flat comparisons pass null.
	 * @param options NCC options
	 */
	public static NccResult buildBlackBoxes(Cell cell1, VarContext ctxt1, 
								          Cell cell2, VarContext ctxt2,
								          HierarchyInfo hierInfo, 
								          NccOptions options, Aborter aborter) {
		return compare2(cell1, ctxt1, cell2, ctxt2, hierInfo, true, 
				               options, aborter);
	}
}
