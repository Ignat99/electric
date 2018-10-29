/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Nor2kresetV.java
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

public class Nor2kresetV {
	private static final double wellOverhangDiff = 6;
	private static final double outHiY = 11.0;
	private static final double outLoY = -11.0;
	private static final double wirePitch = 7;
	private static final double wireWithPolyPitch = 8;
	// p1m1_wid/2 + p1_mos_sp
	private static final double pmosBot = wireWithPolyPitch/2 + 5./2 + 2;
	private static final double nmosTop = -pmosBot;
	private static final double inaY = -wireWithPolyPitch/2;
	private static final double inbLoY = wireWithPolyPitch/2;
    
	private static void error(boolean pred, String msg) {
		Job.error(pred, msg);
	}
	
	public static Cell makePart(double sz, StdCellParams stdCell) {
		TechType tech = stdCell.getTechType();
        EditingPreferences ep = stdCell.getEditingPreferences();
		String nm = "nor2kresetV";
		sz = stdCell.roundSize(sz);
		sz = stdCell.checkMinStrength(sz, 1, nm);
		
		// Compute number of folds and width for PMOS (they're all weak)
		int nbSeriesP = 2;
		double spaceAvail = stdCell.getCellTop() - wellOverhangDiff - pmosBot;
		double totWid = Math.max(3, sz/10 * nbSeriesP * 6);
		FoldsAndWidth fwP = stdCell.calcFoldsAndWidth(spaceAvail, totWid, 1);
		error(fwP==null, "can't make "+nm+" this small: "+sz);
		
		// Compute number of folds and width for weak NMOS.
		spaceAvail = nmosTop - (stdCell.getCellBot() + wellOverhangDiff);
		totWid = Math.max(3, sz/10 * 3);
		FoldsAndWidth fwN = stdCell.calcFoldsAndWidth(spaceAvail, totWid, 1);
		error(fwN==null, "can't make "+nm+" this small: "+sz);
		
		// Compute number of folds and width for strong NMOS
		totWid = sz * 3;
		spaceAvail = nmosTop - (stdCell.getCellBot() + wirePitch/2 + wirePitch -
								5./2); //ndm1_wid/2
		FoldsAndWidth fwS = stdCell.calcFoldsAndWidth(spaceAvail, totWid, 1);
		error(fwS==null, "can't make "+nm+" this small: "+sz);
		
		// create NOR Part
		Cell nor = stdCell.findPart(nm, sz);
		if (nor!=null) return nor;
		nor = stdCell.newPart(nm, sz);
    
		// leave vertical m1 track for inB
		double inbX = wirePitch/2;
		double inaX = inbX + wirePitch;
		double jogX = inaX + wirePitch;
		
		// PMOS transistors will set the gate width.  Pack PMOS
		// transistors into one FoldedMos.
		double pmosX = jogX + wirePitch;
		double pmosY = pmosBot + fwP.physWid/2;
		FoldedMos pmos = new FoldedPmos(pmosX, pmosY, fwP.nbFolds, nbSeriesP,
										fwP.gateWid, nor, tech, ep);
		
		// Allocate two folds per FoldedNmos.  Align NMOS gate 0 with PMOS
		// gate 1
		double nmosX = pmosX + 5;
		double nmosY = nmosTop - fwN.physWid/2;
		FoldedMos[] nmoss = new FoldedMos[(fwN.nbFolds+1)/2];
		for (int nbFoldsN=0; nbFoldsN<fwN.nbFolds; nbFoldsN+=2) {
			double nmosPitch = 26;
			double x = nmosX + (nbFoldsN/2)*nmosPitch;
			int nbFolds = Math.min(2, fwN.nbFolds - nbFoldsN);
			FoldedMos nmos = new FoldedNmos(x, nmosY, nbFolds, 1, fwN.gateWid,
											nor, tech, ep);
			nmoss[nbFoldsN/2] = nmos;
		}
		stdCell.fillDiffAndSelectNotches(nmoss, true);
		
		// Strong NMOS is 10x size of weak NMOS
		double rightNDiffX = StdCellParams.getRightDiffX(nmoss, nmoss);
		double rightPDiffX = StdCellParams.getRightDiffX(pmos, pmos);
		// diff_diff_sp
		double bigNmosX = Math.max(rightPDiffX, rightNDiffX + 11);
		double bigY = nmosTop - fwS.physWid/2;
		FoldedMos bigMos = new FoldedNmos(bigNmosX, bigY, fwS.nbFolds, 1,
										  fwS.gateWid, nor, tech, ep);

        // Fill select notch between weak mos and reset nmos
        stdCell.fillDiffAndSelectNotches(new FoldedMos[]{nmoss[nmoss.length-1], bigMos}, false);

		// create vdd and gnd exports and connect to MOS source/drains
		stdCell.wireVddGnd(nmoss, StdCellParams.EVEN, nor);
		stdCell.wireVddGnd(pmos, StdCellParams.EVEN, nor);
		
		// Nor input B
		double inbHiY = outHiY;
		// m1_wid + m1_space + m1_wid/2
		LayoutLib.newExport(nor, "inb", ep, PortCharacteristic.IN, tech.m1(),
							4, inbX, inbHiY);
		PortInst jog = LayoutLib.newNodeInst(tech.m1pin(), ep, jogX, inbHiY, 1, 1, 0,
											 nor).getOnlyPortInst();
		TrackRouter inbHi = new TrackRouterH(tech.m2(), 3, inbHiY, tech, ep, nor);
		inbHi.connect(nor.findExport("inb"));
		inbHi.connect(jog);
		
		TrackRouter inb = new TrackRouterH(tech.m1(), 3, inbLoY, tech, ep, nor);
		inb.connect(jog);
		for (int i=0; i<pmos.nbGates(); i+=2) {
			if (i/2 % 2 == 0){
				inb.connect(pmos.getGate(i+1, 'B'), 4, tech.getPolyLShapeOffset());
			} else {
				inb.connect(pmos.getGate(i, 'B'), -4, tech.getPolyLShapeOffset());
			}
		}
		for (int i=0; i<nmoss.length; i++) {
			FoldedMos mos = nmoss[i];
			for (int j=0; j<mos.nbGates(); j++) {
				inb.connect(mos.getGate(j, 'T'), (j%2==0 ? 4. : -4.), -tech.getPolyTShapeOffset());
			}
		}
		
		// Nor input A
		LayoutLib.newExport(nor, "ina", ep, PortCharacteristic.IN, tech.m1(),
							4, inaX, inaY);
		TrackRouter inA = new TrackRouterH(tech.m1(), 3, inaY, tech, ep, nor);
		inA.connect(nor.findExport("ina"));
		for (int i=0; i<pmos.nbGates(); i+=2) {
			if (i/2 % 2 == 0) {
				inA.connect(pmos.getGate(i, 'B'), -4, tech.getPolyLShapeOffset());
			} else {
				inA.connect(pmos.getGate(i+1, 'B'), 4, tech.getPolyLShapeOffset());
			}
		}
		for (int i=0; i<bigMos.nbGates(); i++) {
			inA.connect(bigMos.getGate(i, 'T'), 0, -tech.getPolyLShapeOffset());
		}
		
		// resetV input
		// ndm1_wid
		double resetX = StdCellParams.getRightDiffX(bigMos) + 2 + 3 + 2;
		double resetY = nmosTop - fwS.physWid + 2.5 - wirePitch;
		LayoutLib.newExport(nor, "resetV", ep, PortCharacteristic.IN,
							tech.m1(), 4, resetX, resetY);
		TrackRouter reset = new TrackRouterH(tech.m1(), 3, resetY, tech, ep, nor);
		reset.connect(nor.findExport("resetV"));
		for (int i=0; i<bigMos.nbSrcDrns(); i+=2) {
			reset.connect(bigMos.getSrcDrn(i));
		}
		
		// Nor output
		double outX = resetX + 2 + 3 + 2;	// m1_wid/2 + m1_sp + m1_wid/2
		LayoutLib.newExport(nor, "out", ep, PortCharacteristic.OUT, tech.m1(),
							4, outX, outHiY);
		TrackRouter outHi = new TrackRouterH(tech.m2(), 4, outHiY, tech, ep, nor);
		outHi.connect(nor.findExport("out"));
		for (int i=1; i<pmos.nbSrcDrns(); i+=2) {
			outHi.connect(pmos.getSrcDrn(i));
		}
		TrackRouter outLo = new TrackRouterH(tech.m2(), 4, outLoY, tech, ep, nor);
		outLo.connect(nor.findExport("out"));
		for (int i=0; i<nmoss.length; i++) {
			for (int j=1; j<nmoss[i].nbSrcDrns(); j+=2) {
				outLo.connect(nmoss[i].getSrcDrn(j));
			}
		}
		for (int i=1; i<bigMos.nbSrcDrns(); i+=2) {
			outLo.connect(bigMos.getSrcDrn(i));
		}
		
		// add wells
		double wellMinX = 0;
		double wellMaxX = outX + 2 + 1.5; // m1_wid/2 + m1m1_space/2
		stdCell.addNmosWell(wellMinX, wellMaxX, nor);
		stdCell.addPmosWell(wellMinX, wellMaxX, nor);
		
		// add essential bounds
		stdCell.addEssentialBounds(wellMinX, wellMaxX, nor);
		
		// perform Network Consistency Check
		stdCell.doNCC(nor, nm+"{sch}");
		
		return nor;
	}
}

