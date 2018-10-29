/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: InstFifoAll.java
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
package com.sun.electric.tool.generator.flag.designs.Infinity2;

import java.util.ArrayList;
import java.util.List;

import com.sun.electric.database.EditingPreferences;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.Library;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.topology.PortInst;
import com.sun.electric.technology.ArcProto;
import com.sun.electric.tool.generator.flag.FlagConstructorData;
import com.sun.electric.tool.generator.flag.FlagDesign;
import com.sun.electric.tool.generator.flag.router.ToConnect;
import com.sun.electric.tool.generator.layout.AbutRouter;
import com.sun.electric.tool.generator.layout.LayoutLib;
import com.sun.electric.tool.generator.layout.LayoutLib.Corner;
import com.sun.electric.util.math.Orientation;

public class InstFifoAll extends FlagDesign {
	private final double DEF_SIZE = LayoutLib.DEF_SIZE;
	private final double LEFT_COL_X = -1224;
	private final double RIGHT_COL_X = 1224;
	private final double START_COL_Y = -504;
	private final double Y_PITCH = 144;
	
	private List<NodeInst> lCol = new ArrayList<NodeInst>(), 
	                       rCol = new ArrayList<NodeInst>();
	private NodeInst ctl;
	
	private Library findLibrary(String nm) {
		Library l = Library.findLibrary(nm);
		if (l==null) {
			String errMsg = "Can't find Library: "+nm; 
			prln(errMsg);
			throw new RuntimeException(errMsg);
		}
		return l;
	}
	
	private Cell findCell(Library lib, String cellNm) {
		Cell c = lib.findNodeProto(cellNm);
		if (c==null) { 
			String errMsg = "Can't find cell: "+cellNm+" in library: "+lib.getName();
			prln(errMsg);
			throw new RuntimeException(errMsg);
		}
		return c;
	}
	
	private PortInst findPortInst(NodeInst ni, String portNm) {
		PortInst pi = ni.findPortInst(portNm);
		if (pi==null) {
			String errMsg = "NodeInst: "+ni.describe(false)+" has no PortInst named: "+portNm;
			prln(errMsg);
			throw new RuntimeException(errMsg);
		}
		return pi;
	}

	private void stackInsts(List<NodeInst> layInsts) {
        NodeInst prev = null;
        for (NodeInst me : layInsts) {
        	if (prev!=null) {
        		LayoutLib.alignCorners(prev, Corner.TL, me, Corner.BL, 0, 0);
        	}
        	prev = me;
        }
	}
	
	private void createLayInsts(Cell layCell) {
        EditingPreferences ep = getEditingPreferences();
		Library registersK = findLibrary("registersK");
		Cell instReg14 = findCell(registersK, "instReg14{lay}");
		Cell instReg12 = findCell(registersK, "instReg12{lay}");
		Cell instReg14x2 = findCell(registersK, "instReg14x2{lay}");
		Cell instReg12x2 = findCell(registersK, "instReg12x2{lay}");
		Cell instMerge14 = findCell(registersK, "instMerge14{lay}");
		Cell instMerge12 = findCell(registersK, "instMerge12{lay}");
		
		Library fifosK = findLibrary("fifosK");
		Cell instFifoCont = fifosK.findNodeProto("instFifoCont{lay}");
		
		// registers are ordered in the arrays from bottom to top 
		lCol.add(LayoutLib.newNodeInst(instReg14, ep, 0, 0, DEF_SIZE, DEF_SIZE, 0, layCell));
		rCol.add(LayoutLib.newNodeInst(instReg12, ep, 0, 0, DEF_SIZE, DEF_SIZE, 0, layCell));
		for (int i=0; i<6; i++) {
			lCol.add(LayoutLib.newNodeInst(instReg14x2, ep, 0, 0, DEF_SIZE, DEF_SIZE, 0, layCell));
			rCol.add(LayoutLib.newNodeInst(instReg12x2, ep, 0, 0, DEF_SIZE, DEF_SIZE, 0, layCell));
		}
		NodeInst m = LayoutLib.newNodeInst(instMerge14, ep, 0, 0, DEF_SIZE, DEF_SIZE, 0, layCell);
		m.modifyInstance(0, 0, 0, 0, Orientation.Y);	// mirror top <-> bottom
		lCol.add(m);
		m=LayoutLib.newNodeInst(instMerge12, ep, 0, 0, DEF_SIZE, DEF_SIZE, 0, layCell);
		m.modifyInstance(0, 0, 0, 0, Orientation.Y);	// mirror top <-> bottom
		rCol.add(m);
		lCol.add(LayoutLib.newNodeInst(instReg14, ep, 0, 0, DEF_SIZE, DEF_SIZE, 0, layCell));
		rCol.add(LayoutLib.newNodeInst(instReg12, ep, 0, 0, DEF_SIZE, DEF_SIZE, 0, layCell));
		
		ctl = LayoutLib.newNodeInst(instFifoCont, ep, 0, 0, DEF_SIZE, DEF_SIZE, 0, layCell);
	}
	
	// position everything relative to the control block
	private void placeLayInsts() {
		LayoutLib.alignCorners(ctl, Corner.BL, lCol.get(0), Corner.BR, 0, 0);
		LayoutLib.alignCorners(ctl, Corner.BR, rCol.get(0), Corner.BL, 0, 0);
		stackInsts(lCol);
		stackInsts(rCol);
	}
	private void abutRoute(EditingPreferences ep) {
		List<ArcProto> layers = new ArrayList<ArcProto>();
		layers.add(tech().m2());
		for (NodeInst ni : lCol) {
			AbutRouter.abutRouteLeftRight(ni, ctl, 10, layers, ep);
		}
		for (NodeInst ni: rCol) {
			AbutRouter.abutRouteLeftRight(ctl, ni, 10, layers, ep);
		}
	}
	private void connectBus(List<ToConnect> toConns, int row1, String bus1, int row2, String bus2) {
		for (int i=1; i<=14; i++) {
			ToConnect net = new ToConnect();
			net.addPortInst(findPortInst(lCol.get(row1), bus1+"["+i+"]"));
			net.addPortInst(findPortInst(lCol.get(row2), bus2+"["+i+"]"));
			toConns.add(net);
		}
		for (int i=1; i<=12; i++) {
			ToConnect net = new ToConnect();
			net.addPortInst(findPortInst(rCol.get(row1), bus1+"["+i+"]"));
			net.addPortInst(findPortInst(rCol.get(row2), bus2+"["+i+"]"));
			toConns.add(net);
		}
	}
	private List<ToConnect> createNetlist() {
		List<ToConnect> toConns = new ArrayList<ToConnect>();
		connectBus(toConns, 0, "out", 3, "inX");
		connectBus(toConns, 3, "outX", 3, "inY");
		connectBus(toConns, 3, "outY", 6, "inY");
		connectBus(toConns, 6, "outY", 6, "inX");
		
		connectBus(toConns, 6, "outX", 7, "inA");
		connectBus(toConns, 7, "out", 5, "inY");
		connectBus(toConns, 5, "outY", 4, "inY");
		connectBus(toConns, 4, "outY", 2, "inY");
		connectBus(toConns, 2, "outY", 1, "inY");
		connectBus(toConns, 1, "outY", 1, "inX");
		connectBus(toConns, 1, "outX", 2, "inX");
		connectBus(toConns, 2, "outX", 4, "inX");
		connectBus(toConns, 4, "outX", 5, "inX");
		connectBus(toConns, 5, "outX", 8, "in");
		return toConns;
	}
	
	public InstFifoAll(FlagConstructorData data) {
		super(Infinity2Config.CONFIG, data);
		createLayInsts(data.getLayoutCell());
		placeLayInsts();
        addEssentialBounds(data.getLayoutCell());
		abutRoute(data.getEditingPreferences());
		List<ToConnect> toConns = createNetlist();
		routeSignalsSog(toConns, data.getEditingPreferences(), data.getSOGPrefs());
		
        reexportPowerGround(data.getLayoutCell());
        addNccVddGndExportsConnectedByParent(data.getLayoutCell());
	}
}
