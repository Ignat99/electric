/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: InvV.java
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
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.prototype.PortCharacteristic;
import com.sun.electric.tool.generator.layout.FoldedMos;
import com.sun.electric.tool.generator.layout.FoldedNmos;
import com.sun.electric.tool.generator.layout.FoldedPmos;
import com.sun.electric.tool.generator.layout.FoldsAndWidth;
import com.sun.electric.tool.generator.layout.LayoutLib;
import com.sun.electric.tool.generator.layout.StdCellParams;
import com.sun.electric.tool.generator.layout.TechType;
import com.sun.electric.tool.generator.layout.TrackRouter;
import com.sun.electric.tool.generator.layout.TrackRouterH;
import com.sun.electric.tool.Job;

/** Separate control over N and P transistor sizes.  It's not clear if this is really
 *  necessary or desireable. I'm doing this for Justin */
public class InvV {
	private static final double wellOverhangDiff = 6;
	private static final double inY = 0.0;
	private static final double outHiY = 11.0;
	private static final double outLoY = -11.0;

	private static void error(boolean pred, String msg) {
		Job.error(pred, msg);
	}

	public static Cell makePart(double pSz, double nSz, StdCellParams stdCell) {
		TechType tech = stdCell.getTechType();
        EditingPreferences ep = stdCell.getEditingPreferences();
		String nm = "invV_p"+pSz+"_n"+nSz+"{lay}";

		// Space needed at the top of the PMOS well and bottom of MOS well.
		// We need more space if we're double strapping poly.
		double outsideSpace = stdCell.getDoubleStrapGate() ? (
		  2 + 5 + 1.5 // p1_nd_sp + p1m1_wid + p1_p1_sp/2
		) : (
		  wellOverhangDiff
		);

		// find number of folds and width of PMOS
		double spaceAvail =
			stdCell.getCellTop() - outsideSpace - wellOverhangDiff;
		double lamPerSz = 6;
		double totWidP = pSz * lamPerSz;
		FoldsAndWidth fwP = stdCell.calcFoldsAndWidth(spaceAvail, totWidP, 1);
		error(fwP==null, "can't make " + nm + " this small: " + pSz);

		// find number of folds and width of NMOS
		spaceAvail = -wellOverhangDiff - (stdCell.getCellBot() + outsideSpace);
		lamPerSz = 3;
		double totWidN = nSz * lamPerSz;
		FoldsAndWidth fwN = stdCell.calcFoldsAndWidth(spaceAvail, totWidN, 1);
		error(fwN==null, "can't make " + nm + " this small: " + nSz);

		// create Inverter Part
		Cell inv = stdCell.findPart(nm);
		if (inv!=null)  return inv;
		inv = stdCell.newPart(nm);

		// leave vertical m1 track for in
		double inX = 1.5 + 2; // m1_m1_sp/2 + m1_wid/2
		LayoutLib.newExport(inv, "in", ep, PortCharacteristic.IN, tech.m1(),
							4, inX, inY);

		double mosX = inX + 2 + 3 + 2; // m1_wid/2 + m1_m1_sp + m1_wid/2
		double nmosY = -wellOverhangDiff - fwN.physWid / 2;
		FoldedMos nmos = new FoldedNmos(mosX, nmosY, fwN.nbFolds, 1, 
										fwN.gateWid, inv, tech, ep);
		double pmosY = wellOverhangDiff + fwP.physWid / 2;
		FoldedMos pmos = new FoldedPmos(mosX, pmosY, fwP.nbFolds, 1,
										fwP.gateWid, inv, tech, ep);

		// inverter output:  m1_wid/2 + m1_m1_sp + m1_wid/2 
		double outX = StdCellParams.getRightDiffX(nmos, pmos) + 2 + 3 + 2;
		LayoutLib.newExport(inv, "out", ep, PortCharacteristic.OUT,
							tech.m1(), 4, outX, 0);

		// create vdd and gnd exports and connect to MOS source/drains
		stdCell.wireVddGnd(nmos, StdCellParams.EVEN, inv);
		stdCell.wireVddGnd(pmos, StdCellParams.EVEN, inv);

		// Connect up input. Do PMOS gates first because PMOS gate spacing
		// is a valid spacing for p1m1 vias even for small strengths.
		TrackRouter in = new TrackRouterH(tech.m1(), 3, inY, tech, ep, inv);
		in.connect(inv.findExport("in"));
		for (int i=0; i<pmos.nbGates(); i++)  in.connect(pmos.getGate(i, 'B'));
		for (int i=0; i<nmos.nbGates(); i++)  in.connect(nmos.getGate(i, 'T'));

		if (stdCell.getDoubleStrapGate()) {
			// Connect gates using metal1 along bottom of cell 
			double gndBot = stdCell.getGndY() - stdCell.getGndWidth() / 2;
			double inLoFromGnd = gndBot - 3 - 2; // -m1_m1_sp -m1_wid/2
			double nmosBot = nmosY - fwN.physWid / 2;
			double inLoFromMos = nmosBot - 2 - 2.5; // -nd_p1_sp - p1m1_wid/2
			double inLoY = Math.min(inLoFromGnd, inLoFromMos);

			TrackRouter inLo = new TrackRouterH(tech.m1(), 3, inLoY, tech, ep, inv);
			inLo.connect(inv.findExport("in"));
			for (int i = 0; i < nmos.nbGates(); i++) {
				inLo.connect(nmos.getGate(i, 'B'));
			}

			// Connect gates using metal1 along top of cell 
			double vddTop = stdCell.getVddY() + stdCell.getVddWidth() / 2;
			double inHiFromVdd = vddTop + 3 + 2; // +m1_m1_sp + m1_wid/2
			double pmosTop = pmosY + fwP.physWid / 2;
			double inHiFromMos = pmosTop + 2 + 2.5; // +pd_p1_sp + p1m1_wid/2
			double inHiY = Math.max(inHiFromVdd, inHiFromMos);

			TrackRouter inHi = new TrackRouterH(tech.m1(), 3, inHiY, tech, ep, inv);
			inHi.connect(inv.findExport("in"));
			for (int i=0; i<pmos.nbGates(); i++) {
				inHi.connect(pmos.getGate(i, 'T'));
			}
		}

		// connect up output
		TrackRouter outHi = new TrackRouterH(tech.m2(), 4, outHiY, tech, ep, inv);
		outHi.connect(inv.findExport("out"));
		for (int i=1; i<pmos.nbSrcDrns(); i += 2) {
			outHi.connect(pmos.getSrcDrn(i));
		}

		TrackRouter outLo = new TrackRouterH(tech.m2(), 4, outLoY, tech, ep, inv);
		outLo.connect(inv.findExport("out"));
		for (int i = 1; i < nmos.nbSrcDrns(); i += 2) {
			outLo.connect(nmos.getSrcDrn(i));
		}

		// add wells
		double wellMinX = 0;
		double wellMaxX = outX + 2 + 1.5; // m1_wid/2 + m1m1_space/2
		stdCell.addNmosWell(wellMinX, wellMaxX, inv);
		stdCell.addPmosWell(wellMinX, wellMaxX, inv);

		// add essential bounds
		stdCell.addEssentialBounds(wellMinX, wellMaxX, inv);

		// perform Network Consistency Check
		stdCell.doNCC(inv, "inv{sch}");

		return inv;
	}
}
