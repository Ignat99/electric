/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Nand2PHfk.java
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
package com.sun.electric.tool.generator.layout.gates;

import com.sun.electric.database.EditingPreferences;
import java.util.ArrayList;

import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.Export;
import com.sun.electric.database.prototype.PortCharacteristic;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.topology.PortInst;
import com.sun.electric.tool.generator.layout.LayoutLib;
import com.sun.electric.tool.generator.layout.StdCellParams;
import com.sun.electric.tool.generator.layout.TechType;
import com.sun.electric.tool.generator.layout.TrackRouter;
import com.sun.electric.tool.generator.layout.TrackRouterH;

public class Nand2PHfk {
	private static final double outHiY = 11.0;
	private static final double outLoY = -11.0;
	
//	private static void error(boolean pred, String msg) {
//		LayoutLib.error(pred, msg);
//	}

	public static Cell makePart(double sz, StdCellParams stdCell) {
		TechType tech = stdCell.getTechType();
        EditingPreferences ep = stdCell.getEditingPreferences();
		String nm = "nand2PHfk";
		sz = stdCell.roundSize(sz);
		sz = stdCell.checkMinStrength(sz, 1, nm);
		
		Cell nand = stdCell.findPart(nm, sz);
		if (nand!=null)  return nand;
		nand = stdCell.newPart(nm, sz);

		String vddName = stdCell.getVddExportName();
		String gndName = stdCell.getGndExportName();
		PortCharacteristic vddRole = stdCell.getVddExportRole();
		PortCharacteristic gndRole = stdCell.getGndExportRole();

		NodeInst inv2i = LayoutLib.newNodeInst(Inv2i.makePart(sz, stdCell), ep,
											   0, 0, 1, 1, 0, nand);
		NodeInst pms1 = LayoutLib.newNodeInst(Pms1.makePart(sz, stdCell), ep,
											  0, 0, 1, 1, 0, nand);
		NodeInst invK = LayoutLib.newNodeInst(Inv.makePart(sz/10, stdCell), ep,
											  0, 0, -1, 1, 0, nand);
		NodeInst inv1 = LayoutLib.newNodeInst(Inv.makePart(1, stdCell), ep,
											  0, 0, 1, 1, 0, nand);
		ArrayList<NodeInst> l = new ArrayList<NodeInst>();
		l.add(inv2i);
		l.add(pms1);
		l.add(invK);
		l.add(inv1);
		LayoutLib.abutLeftRight(l);

		// Well tie
		Cell tieCell =
			WellTie.makePart(true, false, pms1.getBounds().getWidth(), stdCell);
		NodeInst tie = LayoutLib.newNodeInst(tieCell, ep, 0, 0, 1, 1, 0, nand);
		LayoutLib.abutLeftRight(inv2i, tie);
		l.add(tie);

		// connect up power and ground
		TrackRouter vdd = new TrackRouterH(tech.m2(), 10, tech, ep, nand);
		vdd.connect(l, vddName);

		TrackRouter gnd = new TrackRouterH(tech.m2(), 10, tech, ep, nand);
		gnd.connect(l, gndName);

		// connect up signal wires
		TrackRouter out = new TrackRouterH(tech.m2(), 4, outHiY, tech, ep, nand);
		out.connect(new PortInst[] {inv2i.findPortInst("out"),
									pms1.findPortInst("d"),
									invK.findPortInst("out"),
									inv1.findPortInst("in")});
		TrackRouter k = new TrackRouterH(tech.m2(), 4, outLoY, tech, ep, nand);
		k.connect(new PortInst[] {invK.findPortInst("in"),
								  inv1.findPortInst("out")});
		// exports
		Export.newInstance(nand, inv2i.findPortInst("in[p]"), "inb", ep)
			.setCharacteristic(PortCharacteristic.IN);
		Export.newInstance(nand, inv2i.findPortInst("in[n]"), "resetN", ep)
			.setCharacteristic(PortCharacteristic.IN);
		Export.newInstance(nand, pms1.findPortInst("g"), "ina", ep)
			.setCharacteristic(PortCharacteristic.IN);
		Export.newInstance(nand, inv2i.findPortInst("out"), "out", ep)
			.setCharacteristic(PortCharacteristic.OUT);
		Export.newInstance(nand, inv2i.findPortInst(vddName), vddName, ep)
			.setCharacteristic(vddRole);
		Export.newInstance(nand, inv2i.findPortInst(gndName), gndName, ep)
			.setCharacteristic(gndRole);

		// add essential bounds
		stdCell.addEssentialBounds(0, inv1.getBounds().getMaxX(), nand);

		// Compare schematic to layout
		// perform Network Consistency Check
		stdCell.doNCC(nand, nm + "{sch}");

		return nand;
	}
}
