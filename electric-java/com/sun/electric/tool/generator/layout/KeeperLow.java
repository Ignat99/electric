/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: KeeperLow.java
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
package com.sun.electric.tool.generator.layout;

import com.sun.electric.database.EditingPreferences;
import java.util.ArrayList;
import java.util.Iterator;

import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.Export;
import com.sun.electric.database.prototype.PortCharacteristic;
import com.sun.electric.database.prototype.NodeProto;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.topology.PortInst;
import com.sun.electric.database.variable.VarContext;
import com.sun.electric.tool.generator.layout.gates.Inv;
import com.sun.electric.tool.generator.layout.gates.Nms1;
import com.sun.electric.tool.Job;

public class KeeperLow {
	private static void error(boolean pred, String msg) {
		Job.error(pred, msg);
	}

	public static Cell makePart(Cell schem, VarContext context,
								StdCellParams stdCell) {
		TechType tech = stdCell.getTechType();
        EditingPreferences ep = stdCell.getEditingPreferences();
		Iterator<NodeInst> nodes = schem.getNodes();

		// extract size information
		double szNmos = -1, szK = -1, szI = -1;
		while (nodes.hasNext()) {
			NodeInst ni = nodes.next();
			String nm = ni.getProto().getName();
			if (nm.equals("nms1K{ic}")) {
				szNmos = StdCellParams.getSize(ni, context);
			} else if (nm.equals("invK{ic}")) {
				szK = StdCellParams.getSize(ni, context);
			} else if (nm.equals("inv{ic}")) {
				szI = StdCellParams.getSize(ni, context);
			}
		}
		error(szNmos == -1, "KeeperLow: nmos not found");
		error(szK == -1, "KeeperLow: invK not found");
		error(szI == -1, "KeeperLow: inv not found");

		double sz = stdCell.roundSize(szK);
		Cell keep = stdCell.findPart("keeper_low", sz);
		if (keep != null)
			return keep;
		keep = stdCell.newPart("keeper_low", sz);

		NodeProto invKProto = Inv.makePart(szK, stdCell);
		NodeInst invK = LayoutLib.newNodeInst(invKProto, ep, 0, 0, 0, 0, 0, keep);
		NodeProto invIProto = Inv.makePart(szI, stdCell);
		NodeInst invI =LayoutLib.newNodeInst(invIProto, ep, 0, 0, 0, 0, 0, keep);
		NodeProto nmosProto = Nms1.makePart(szNmos, stdCell);
		NodeInst nmos = LayoutLib.newNodeInst(nmosProto, ep, 0, 0, 0, 0, 0, keep);
		ArrayList<NodeInst> l = new ArrayList<NodeInst>();
		l.add(nmos);
		l.add(invK);
		l.add(invI);
		LayoutLib.abutLeftRight(l);

		// connect up power and ground
		TrackRouter vdd = new TrackRouterH(tech.m2(), 10, tech, ep, keep);
		vdd.connect(new NodeInst[] { invK, invI }, "vdd");

		TrackRouter gnd = new TrackRouterH(tech.m2(), 10, tech, ep, keep);
		gnd.connect(new NodeInst[] { nmos, invK, invI }, "gnd");

		// connect up signal wires
		TrackRouter d =
			new TrackRouterH(tech.m2(), 4,
							 LayoutLib.roundCenterY(nmos.findPortInst("d")),
							 tech, ep, keep);
		d.connect(new PortInst[] {nmos.findPortInst("d"),
								  invK.findPortInst("out"),
								  invI.findPortInst("in")});

		double trackY = stdCell.getTrackY(-1);
		TrackRouter d_bar = new TrackRouterH(tech.m2(), 4, trackY, tech, ep, keep);
		d_bar.connect(new PortInst[] {invK.findPortInst("in"),
									  invI.findPortInst("out")});

		// exports
		Export.newInstance(keep, nmos.findPortInst("g"), "mc", ep)
			.setCharacteristic(PortCharacteristic.IN);
		Export.newInstance(keep, nmos.findPortInst("d"), "d", ep)
			.setCharacteristic(PortCharacteristic.BIDIR);
		Export.newInstance(keep, invK.findPortInst("vdd"), "vdd", ep)
			.setCharacteristic(PortCharacteristic.PWR);
		Export.newInstance(keep, invK.findPortInst("gnd"), "gnd", ep)
			.setCharacteristic(PortCharacteristic.GND);

		// patch well over pulldown
		stdCell.addPmosWell(0, nmos.getBounds().getWidth(), keep);

		// add essential bounds
		stdCell.addEssentialBounds(0, invI.getBounds().getMaxX(), keep);

		// Compare schematic to layout
		/*
		if (stdCell.nccEnabled()) {
			NccOptions options = new NccOptions();
			options.checkExportNames = true;
			options.hierarchical = true;
			options.mergeParallel = true;
			boolean mismatch =
				Electric.networkConsistencyCheck(schem, context, keep, options);
			error(mismatch, "Keeper: gasp cell topological mismatch");
		}
		*/

		return keep;
	}
}
