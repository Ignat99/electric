/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: InvCTLn.java
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

public class InvCTLn {
	private static final double wellOverhangDiff = 6;
	private static final double outHiY = 11.0;
	private static final double outLoY = -11.0;
	private static final double wirePitch = 7;
	private static final double wireWithPolyPitch = 8;
	// p1m1_wid/2 + p1_mos_sp
	private static final double pmosBot = wireWithPolyPitch/2 + 5./2 + 2;
	private static final double nmosTop = -pmosBot;
	private static final double inY = wireWithPolyPitch/2;
	private static final double ctlY = -wireWithPolyPitch/2;
    
	private static void error(boolean pred, String msg) {
		Job.error(pred, msg);
	}
	
	public static Cell makePart(double sz, StdCellParams stdCell) {
		TechType tech = stdCell.getTechType();
        EditingPreferences ep = stdCell.getEditingPreferences();
		String nm = "invCTLn";
		sz = stdCell.roundSize(sz);
		sz = stdCell.checkMinStrength(sz, .5, nm);
		
		// Compute number of folds and width for NMOS
		int nbSeriesN = 2;
		double spaceAvail = nmosTop - (stdCell.getCellBot() + wellOverhangDiff);
		double totWid = sz * nbSeriesN * 3;
		FoldsAndWidth fwN = stdCell.calcFoldsAndWidth(spaceAvail, totWid, 1);
		error(fwN==null, "can't make "+nm+" this small: "+sz);
		
		// Compute number of folds and width for PMOS.
		spaceAvail = stdCell.getCellTop() - wellOverhangDiff - pmosBot;
		totWid = sz * 6;
		FoldsAndWidth fwP = stdCell.calcFoldsAndWidth(spaceAvail, totWid, 1);
		error(fwP==null, "can't make "+nm+" this small: "+sz);
		
		// create Inv Part
		Cell inv = stdCell.findPart(nm, sz);
		if (inv!=null) return inv;
		inv = stdCell.newPart(nm, sz);
		
		// leave vertical m1 track for in
		double inX = wirePitch/2;
		
		// Allocate two folds per FoldedPmos. Align PMOS gate 1 with NMOS
		// gate 0.
		double pmosX = inX + wirePitch;
		double pmosY = pmosBot + fwP.physWid/2;
		FoldedMos[] pmoss = new FoldedMos[(fwP.nbFolds+1)/2];
		for (int nbFoldsP=0; nbFoldsP<fwP.nbFolds; nbFoldsP+=2) {
			double pmosPitch = 26;
			double x = pmosX + (nbFoldsP/2)*pmosPitch;
			int nbFolds = Math.min(2, fwP.nbFolds - nbFoldsP);
			FoldedMos pmos = new FoldedPmos(x, pmosY, nbFolds, 1, fwP.gateWid,
											inv, tech, ep);
			pmoss[nbFoldsP/2] = pmos;
		}
		stdCell.fillDiffAndSelectNotches(pmoss, true);
		
		// NMOS width dominates width of inv. Allocate it in one FoldedNmos
		double nmosX = pmosX + 8;
		double nmosY = nmosTop - fwN.physWid/2;
		FoldedMos nmos = new FoldedNmos(nmosX, nmosY, fwN.nbFolds, nbSeriesN,
										fwN.gateWid, inv, tech, ep);
		
		// create vdd and gnd exports and connect to MOS source/drains
		stdCell.wireVddGnd(nmos, StdCellParams.EVEN, inv);
		stdCell.wireVddGnd(pmoss, StdCellParams.EVEN, inv);
		
//		// fool Electric's NCC into paralleling NMOS stacks by connecting
//		// stacks' internal diffusion nodes.
//		for (int i=0; i<nmos.nbInternalSrcDrns(); i++) {
//			LayoutLib.newArcInst(tech.universalArc, 0,nmos.getInternalSrcDrn(0),
//								 nmos.getInternalSrcDrn(i));
//		}
		
		// Inv input: in 
		// m1_wid + m1_space + m1_wid/2
		LayoutLib.newExport(inv, "in", ep, PortCharacteristic.IN, tech.m1(), 4,
							inX, inY);
		TrackRouter in = new TrackRouterH(tech.m1(), 3, inY, tech, ep, inv);
		in.connect(inv.findExport("in"));
		for (int i=0; i<pmoss.length; i++) {
			FoldedMos pmos = pmoss[i];
			in.connect(pmos.getGate(0, 'B'), 4, tech.getPolyLShapeOffset());
			if (pmos.nbGates()==2) {
				in.connect(pmos.getGate(1, 'B'), -4, tech.getPolyLShapeOffset());
			}
		}
		for (int i=0; i<nmos.nbGates(); i+=2) {
			if (i/2 %2 == 0) {
				in.connect(nmos.getGate(i, 'T'), -4, -tech.getPolyTShapeOffset());
			} else {
				in.connect(nmos.getGate(i+1, 'T'), 4, -tech.getPolyTShapeOffset());
			}
		}
		
		// Inv input: ctl
		double rightDiffX = StdCellParams.getRightDiffX(nmos, pmoss);
		double ctlX = rightDiffX + wirePitch;
		LayoutLib.newExport(inv, "ctl", ep, PortCharacteristic.IN, tech.m1(), 4,
							ctlX, ctlY);
		TrackRouter ctl = new TrackRouterH(tech.m1(), 3, ctlY, tech,ep, inv);
		ctl.connect(inv.findExport("ctl"));
		for (int i=0; i<nmos.nbGates(); i+=2) {
			if (i/2 % 2 == 0) {
				ctl.connect(nmos.getGate(i+1, 'T'), 4, -tech.getPolyLShapeOffset());
			} else {
				ctl.connect(nmos.getGate(i, 'T'), -4, -tech.getPolyLShapeOffset());
			}
		}
		
		// Inv output: out
		double outX = ctlX + wirePitch;
		LayoutLib.newExport(inv, "out", ep, PortCharacteristic.OUT, tech.m1(),
							4, outX, outHiY);
		TrackRouter outHi = new TrackRouterH(tech.m2(), 4, outHiY, tech, ep, inv);
		outHi.connect(inv.findExport("out"));
		for (int i=0; i<pmoss.length; i++) {
			outHi.connect(pmoss[i].getSrcDrn(1));
		}
		TrackRouter outLo = new TrackRouterH(tech.m2(), 4, outLoY, tech, ep, inv);
		outLo.connect(inv.findExport("out"));
		for (int i=1; i<nmos.nbSrcDrns(); i+=2) {
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
		stdCell.doNCC(inv, nm+"{sch}");
		
		return inv;
	}
}
