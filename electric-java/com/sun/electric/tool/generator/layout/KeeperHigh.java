/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: KeeperHigh.java
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

import java.util.ArrayList;
import java.util.Iterator;

import com.sun.electric.database.EditingPreferences;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.Export;
import com.sun.electric.database.network.Netlist;
import com.sun.electric.database.network.Network;
import com.sun.electric.database.prototype.NodeProto;
import com.sun.electric.database.prototype.PortCharacteristic;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.topology.PortInst;
import com.sun.electric.database.variable.VarContext;
import com.sun.electric.tool.generator.layout.gates.Inv;
import com.sun.electric.tool.generator.layout.gates.Pms1;
import com.sun.electric.tool.Job;

public class KeeperHigh {
	private static void error(boolean pred, String msg) {
		Job.error(pred, msg);
	}

	public static Cell makePart(Cell schem, VarContext context,
								StdCellParams stdCell) {
		TechType tech = stdCell.getTechType();
        EditingPreferences ep = stdCell.getEditingPreferences();
		Netlist netlist = schem.getNetlist();

		Iterator<NodeInst> nodes = schem.getNodes();

		// extract size information
		double szMc = -1, szPmos = -1, szK = -1, szI = -1;
		while (nodes.hasNext()) {
			NodeInst ni = nodes.next();
			String nm = ni.getProto().getName();
			if (nm.equals("pms1K{ic}")) {
				szPmos = StdCellParams.getSize(ni, context);
			} else if (nm.equals("invK{ic}")) {
				szK = StdCellParams.getSize(ni, context);
			} else if (nm.equals("inv{ic}")) {
				Network net = netlist.getNetwork(ni.findPortInst("in"));
				if (net.hasName("mc")) {
					szMc = StdCellParams.getSize(ni, context);
				} else if (net.hasName("d")) {
					szI = StdCellParams.getSize(ni, context);
				} else {
					System.out.println("Unrecognized net: ");
					Iterator<String> it = net.getNames();
					while (it.hasNext()) {
						System.out.println(it.next());
					}
					error(true, "unrecognized net");
				}
			}
		}
		error(szMc == -1, "KeeperHigh: inv bufferring mc not found");
		error(szPmos == -1, "KeeperHigh: pmos not found");
		error(szK == -1, "KeeperHigh: invK not found");
		error(szI == -1, "KeeperHigh: inv driving invK not found");

		double sz = stdCell.roundSize(szK);
		Cell keep = stdCell.findPart("keeper_high", sz);
		if (keep != null)
			return keep;
		keep = stdCell.newPart("keeper_high", sz);

		NodeProto invKProto = Inv.makePart(szK, stdCell);
		NodeInst invK = LayoutLib.newNodeInst(invKProto, ep, 0, 0, 0, 0, 0, keep);
		NodeProto invIProto = Inv.makePart(szI, stdCell);
		NodeInst invI = LayoutLib.newNodeInst(invIProto, ep, 0, 0, 0, 0, 0, keep);
		NodeProto pmosProto = Pms1.makePart(szPmos, stdCell);
		NodeInst pmos = LayoutLib.newNodeInst(pmosProto, ep, 0, 0, 0, 0, 0, keep);
		NodeProto mcProto = Inv.makePart(szMc, stdCell);
		NodeInst mc = LayoutLib.newNodeInst(mcProto, ep, 0, 0, 0, 0, 0, keep);

		ArrayList<NodeInst> l = new ArrayList<NodeInst>();
		l.add(mc);
		l.add(pmos);
		l.add(invK);
		l.add(invI);
		LayoutLib.abutLeftRight(l);

		/* doesn't work, either it never worked or it stopped working
		// Mirror invK left to right so I don't have to use a metal-2 track.
		invK.mirrorLeftToRight();
		*/

		// connect up power and ground
		TrackRouter vdd = new TrackRouterH(tech.m2(), 10, tech, ep, keep);
		vdd.connect(new NodeInst[] { mc, pmos, invK, invI }, "vdd");

		TrackRouter gnd = new TrackRouterH(tech.m2(), 10, tech, ep, keep);
		gnd.connect(new NodeInst[] { mc, invK, invI }, "gnd");

		// connect up signal wires
		TrackRouter d =
			new TrackRouterH(tech.m2(), 4,
							 LayoutLib.roundCenterY(pmos.findPortInst("d")),
							 tech, ep, keep);
		d.connect(new PortInst[] {pmos.findPortInst("d"),
								  invK.findPortInst("out"),
								  invI.findPortInst("in")});

		/* Use this when I finally get mirroring to work (again?)
		//double trackY = -11;
		*/
		double trackY = stdCell.getTrackY(-1);
		TrackRouter d_bar = new TrackRouterH(tech.m2(), 4, trackY, tech, ep, keep);
		d_bar.connect(new PortInst[] {invK.findPortInst("in"),
									  invI.findPortInst("out")});
		TrackRouter mc_bar =
			new TrackRouterH(tech.m1(), 4,
					         LayoutLib.roundCenterY(pmos.findPortInst("g")),
					         tech, ep, keep);
		mc_bar.connect(new PortInst[] {mc.findPortInst("out"),
									   pmos.findPortInst("g")});

		// exports
		Export.newInstance(keep, mc.findPortInst("in"), "mc", ep)
			.setCharacteristic(PortCharacteristic.IN);
		Export.newInstance(keep, pmos.findPortInst("d"), "d", ep)
			.setCharacteristic(PortCharacteristic.BIDIR);
		Export.newInstance(keep, invK.findPortInst("vdd"), "vdd", ep)
			.setCharacteristic(PortCharacteristic.PWR);
		Export.newInstance(keep, invK.findPortInst("gnd"), "gnd", ep)
			.setCharacteristic(PortCharacteristic.GND);

		// patch well over pullup
		stdCell.addNmosWell(
			mc.getBounds().getWidth(),
			mc.getBounds().getWidth() + pmos.getBounds().getWidth(),
			keep);

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
			error(mismatch, "KeeperHigh topological mismatch");
		}
		*/

		return keep;
	}
}
