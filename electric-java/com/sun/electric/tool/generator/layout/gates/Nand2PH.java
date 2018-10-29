/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Nand2PH.java
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
import com.sun.electric.database.topology.PortInst;
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

public class Nand2PH {
	private static final double DEF_SIZE = LayoutLib.DEF_SIZE;
	private static final double nmosTop = -9.0;
	private static final double pmosBot = 9.0;
	private static final double wellOverhangDiff = 6;
	private static final double pGatesY = 4.0;
	private static final double nGatesY = -4.0;
	private static final double outHiY = 11.0;
	private static final double outLoY = -11.0;
	private static final double weakRatio = 10;
	
	private static void error(boolean pred, String msg) {
		Job.error(pred, msg);
	}
	
	public static Cell makePart(double sz, StdCellParams stdCell) {
		TechType tech = stdCell.getTechType();
        EditingPreferences ep = stdCell.getEditingPreferences();
		sz = stdCell.roundSize(sz);
		String nm = "nand2PH";
		sz = stdCell.checkMinStrength(sz, 1, nm);
		
		// Compute number of folds and width for PMOS. Make this PMOS
		// large enough to share between inA and inB by doubling width and
		// setting group size to 2.
		double spaceAvail = stdCell.getCellTop() - wellOverhangDiff - pmosBot;
		double totWid = sz * 6 * 2;
		FoldsAndWidth fwP = stdCell.calcFoldsAndWidth(spaceAvail, totWid, 2);
		error(fwP==null, "can't make "+nm+" this small: "+sz);
		
		// Compute number of folds and width for NMOS
		spaceAvail = nmosTop - (stdCell.getCellBot() + wellOverhangDiff);
		totWid = sz * 3;
		FoldsAndWidth fwN = stdCell.calcFoldsAndWidth(spaceAvail, totWid, 1);
		error(fwN==null, "can't make "+nm+" this small: "+sz);
		
		// Weak NMOS is 1/weakRatio the width of the strong NMOS
		totWid = Math.max(3, totWid/weakRatio);
		FoldsAndWidth fwW = stdCell.calcFoldsAndWidth(spaceAvail, totWid, 1);
		error(fwW==null, "can't make "+nm+" this small: "+sz);
		
		// create GATE Part
		Cell gate = stdCell.findPart(nm, sz);
		if (gate!=null) return gate;
		gate = stdCell.newPart(nm, sz);
		
		double resetNX = 3.5;  		// track_pitch/2
		double inbX = resetNX + 7;		// track_pitch
		double resetNJogX = inbX + 7; 	// track_pitch
		double mosX = resetNJogX + 7; 	// track_pitch
		
		// PMOS
		double pmosY = pmosBot + fwP.physWid/2;
		FoldedMos pmos = new FoldedPmos(mosX, pmosY, fwP.nbFolds, 1,
										fwP.gateWid, gate, tech, ep);
		// weak NMOS B
		double nmosWeakY = nmosTop - fwW.physWid/2;
		FoldedMos nmosB = new FoldedNmos(mosX, nmosWeakY, fwW.nbFolds, 1,
										 fwW.gateWid, gate, tech, ep);
		// reset NMOS
		double nmosBRightX = StdCellParams.getRightDiffX(nmosB);
		double nmosX = nmosBRightX + 11; 	// ndm1_ndm1_sp
		double nmosY = nmosTop - fwN.physWid/2;
		FoldedMos nmosR = new FoldedNmos(nmosX, nmosY, fwN.nbFolds, 1,
										 fwN.gateWid, gate, tech, ep);
		
		// weak NMOS A
		double rstNmosRightX = StdCellParams.getRightDiffX(nmosR);
		double pmosRightX = StdCellParams.getRightDiffX(pmos);
		// We may be either 11 lambda right of reset NMOS or our right
		// diff aligns with right diff of PMOS.
		double nmosAX = Math.max(rstNmosRightX + 11,	// ndm1_ndm1_sp
								 pmosRightX-fwW.nbFolds*8); // mos_diff_diff_sp
		FoldedMos nmosA = new FoldedNmos(nmosAX, nmosWeakY, fwW.nbFolds, 1,
										 fwW.gateWid, gate, tech, ep);
		
		FoldedMos[] nmoss = new FoldedMos[] {nmosB, nmosR, nmosA};

        // Fill select notch between weak mos and reset nmos
        stdCell.fillDiffAndSelectNotches(new FoldedMos[]{nmosB, nmosR}, false);
        stdCell.fillDiffAndSelectNotches(new FoldedMos[]{nmosR, nmosA}, false);

		// create vdd and gnd exports and connect to MOS source/drains
		stdCell.wireVddGnd(pmos, StdCellParams.EVEN, gate);
		stdCell.wireVddGnd(nmoss, StdCellParams.EVEN, gate);
		
		// Connect PMOS gates
		TrackRouter bGates = new TrackRouterH(tech.m1(), 3, pGatesY, tech, ep, gate);
		TrackRouter aGatesHi = new TrackRouterH(tech.m1(), 4, pGatesY, tech, ep, gate);
		int nbGates = pmos.nbGates()/2;
		for (int i=0; i<nbGates; i++) {
			bGates.connect(pmos.getGate(i, 'B'));
			aGatesHi.connect(pmos.getGate(i+nbGates, 'B'));
		}
		// Connect weak NMOS gates
		TrackRouter aGatesLo = new TrackRouterH(tech.m1(), 3, nGatesY, tech, ep, gate);
		nbGates = nmosA.nbGates();
		for (int i=0; i<nbGates; i++) {
			bGates.connect(nmosB.getGate(i, 'T'));
			aGatesLo.connect(nmosA.getGate(i, 'T'));
		}
		// Connect reset NMOS input
		TrackRouter resetHi = new TrackRouterH(tech.m1(), 3, nGatesY, tech, ep, gate);
		for (int i=0; i<nmosR.nbGates(); i++) {
			resetHi.connect(nmosR.getGate(i, 'T'));
		}
		PortInst resetNjog =
			LayoutLib.newNodeInst(tech.m1pin(), ep, resetNJogX, nGatesY, DEF_SIZE,
								  DEF_SIZE, 0, gate).getOnlyPortInst();
		resetHi.connect(resetNjog);
		TrackRouter resetLo = new TrackRouterH(tech.m2(), 3, outLoY, tech, ep, gate);
		resetLo.connect(resetNjog);
		LayoutLib.newExport(gate, "resetN", ep, PortCharacteristic.IN,
							tech.m1(), 4, resetNX, outLoY);
		resetLo.connect(gate.findExport("resetN"));
		
		// Connect upper and lower tracks of input A
		PortInst jogA =
		  LayoutLib.newNodeInst(tech.m1pin(), ep, LayoutLib.roundCenterX(nmosA.getGate(0, 'T')), pGatesY,
								DEF_SIZE,
								DEF_SIZE, 0, gate).getOnlyPortInst();
		aGatesLo.connect(jogA);
		aGatesHi.connect(jogA);
		
		// input B
		LayoutLib.newExport(gate, "inb", ep, PortCharacteristic.IN, tech.m1(),
							4, inbX, pGatesY);
		bGates.connect(gate.findExport("inb"));
		
		// input A
		double rightDiffX = StdCellParams.getRightDiffX(pmos, nmosA);
		double inaX = rightDiffX + 7;	// track_pitch
		LayoutLib.newExport(gate, "ina", ep, PortCharacteristic.IN, tech.m1(),
							4, inaX, nGatesY);
		aGatesLo.connect(gate.findExport("ina"));
		
		// Gate output
		double outX = inaX + 7;		// track_pitch
		LayoutLib.newExport(gate, "out", ep, PortCharacteristic.OUT, tech.m1(),
							4, outX, 0);
		TrackRouter outHi = new TrackRouterH(tech.m2(), 4, outHiY, tech, ep, gate);
		outHi.connect(gate.findExport("out"));
		for (int i=1; i<pmos.nbSrcDrns(); i+=2) {
			outHi.connect(pmos.getSrcDrn(i));
		}
		
		TrackRouter outLo = new TrackRouterH(tech.m2(), 4, outLoY, tech, ep, gate);
		outLo.connect(gate.findExport("out"));
		for (int i=1; i<nmosB.nbSrcDrns(); i+=2) {
			outLo.connect(nmosB.getSrcDrn(i));
			outLo.connect(nmosA.getSrcDrn(i));
		}
		for (int i=1; i<nmosR.nbSrcDrns(); i+=2) {
			outLo.connect(nmosR.getSrcDrn(i));
		}
		
		// add wells
		double wellMinX = 0;
		double wellMaxX = outX + 3.5;	// track_pitch/2
		stdCell.addNmosWell(wellMinX, wellMaxX, gate);
		stdCell.addPmosWell(wellMinX, wellMaxX, gate);
		
		// add essential bounds
		stdCell.addEssentialBounds(wellMinX, wellMaxX, gate);
		
		// perform Network Consistency Check
		stdCell.doNCC(gate, nm+"{sch}");
		
		return gate;
	}
}

