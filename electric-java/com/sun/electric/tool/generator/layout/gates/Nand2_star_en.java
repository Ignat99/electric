/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Nand2_star_en.java
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

public class Nand2_star_en {
	private static final double wellOverhangDiff = 6;
	private static final double outHiY = 11.0;
	private static final double outLoY = -11.0;
	private static final double wirePitch = 7;
	private static final double wireWithPolyPitch = 8;
	// p1m1_wid/2 + p1_mos_sp
	private static final double pmosBot = wireWithPolyPitch/2 + 5./2 + 2;
	private static final double nmosTop = -pmosBot;
	private static final double inbY = wireWithPolyPitch/2;
	private static final double inaY = -wireWithPolyPitch/2;
	private static final double pmosPitch = 26;
    
	private static void error(boolean pred, String msg) {
		Job.error(pred, msg);
	}
	
	public static Cell makePart(double sz, String threshold, StdCellParams stdCell) {
		TechType tech = stdCell.getTechType();
        EditingPreferences ep = stdCell.getEditingPreferences();
		error(!threshold.equals("") && !threshold.equals("LT")
				  && !threshold.equals("HT"),
				  "Nand2_star_en: threshold not \"\", \"LT\", or \"HT\": " + threshold);
		String nm = "nand2"+threshold+"en";
		sz = stdCell.roundSize(sz);
		sz = stdCell.checkMinStrength(sz,
									  threshold.equals("LT") ? 1 : .5,
									  nm);
		
		// Compute number of folds and width for NMOS
		int nbSeriesN = 2;
		double spaceAvail = nmosTop - (stdCell.getCellBot() + wellOverhangDiff);
		double totWid = sz * nbSeriesN * 3;
		FoldsAndWidth fwN = stdCell.calcFoldsAndWidth(spaceAvail, totWid, 1);
		error(fwN==null, "can't make "+nm+" this small: "+sz);
		
		// Compute number of folds and width for PMOS.
		spaceAvail = stdCell.getCellTop() - wellOverhangDiff - pmosBot;
		totWid = sz * 6;
		if (threshold.equals("HT")) totWid = totWid * 2;
		else if (threshold.equals("LT")) totWid = totWid / 2;
		FoldsAndWidth fwP = stdCell.calcFoldsAndWidth(spaceAvail, totWid, 1);
		error(fwP==null, "can't make "+nm+" this small: "+sz);
		
		// Compute number of folds and width for weak PMOS
		totWid = Math.max(totWid / 10, 5);
		FoldsAndWidth fwW = stdCell.calcFoldsAndWidth(spaceAvail, totWid, 1);
		error(fwW==null, "can't make "+nm+" this small: "+sz);
		
		// create Nand Part
		Cell nand = stdCell.findPart(nm, sz);
		if (nand!=null) return nand;
		nand = stdCell.newPart(nm, sz);
		
		// leave vertical m1 track for inb
		double inbX = wirePitch/2;
		
		// NMOS width dominates width of nand. Allocate it in one FoldedNmos
		double nmosX = inbX + wirePitch;
		double nmosY = nmosTop - fwN.physWid/2;
		FoldedMos nmos = new FoldedNmos(nmosX, nmosY, fwN.nbFolds, nbSeriesN,
										fwN.gateWid, nand, tech, ep);
		
		// Build regular strength PMOS.  Allocate two folds per
		// FoldedPmos. Align PMOS gate 0 with NMOS gate 1.
		double pmosX = nmosX + 5;
		double pmosY = pmosBot + fwP.physWid/2;
		FoldedMos[] pmoss = new FoldedMos[(fwP.nbFolds+1)/2];
		for (int nbFoldsP=0; nbFoldsP<fwP.nbFolds; nbFoldsP+=2) {
			double x = pmosX + (nbFoldsP/2)*pmosPitch;
			int nbFolds = Math.min(2, fwP.nbFolds - nbFoldsP);
			FoldedMos pmos = new FoldedPmos(x, pmosY, nbFolds, 1, fwP.gateWid,
											nand, tech, ep);
			pmoss[nbFoldsP/2] = pmos;
		}
		stdCell.fillDiffAndSelectNotches(pmoss, true);
		
		// Build weak PMOS.  Allocate two folds per FoldedPmos. Align PMOS
		// gate 0 with NMOS gate k such that (k mod 4 == 3).
		double rightPdiffX = StdCellParams.getRightDiffX(pmoss);
		double rightNdiffX = StdCellParams.getRightDiffX(nmos);
		double weakX = Math.max(rightPdiffX+11, rightNdiffX+2.5);
		double weakY = pmosBot + fwW.physWid/2;
		FoldedMos weak = new FoldedPmos(weakX, weakY, fwW.nbFolds, 1,
										fwW.gateWid, nand, tech, ep);
		// create vdd and gnd exports and connect to MOS source/drains
		stdCell.wireVddGnd(nmos, StdCellParams.EVEN, nand);
		FoldedMos[] allPmoss = new FoldedMos[pmoss.length+1];
		for (int i=0; i<pmoss.length; i++) allPmoss[i] = pmoss[i];
		allPmoss[pmoss.length] = weak;
		stdCell.wireVddGnd(allPmoss, StdCellParams.EVEN, nand);

        // Only for TSMC180 for now ?
        // Fill select notch between last pmos and weak transistor
        FoldedMos[] tmp = {pmoss[pmoss.length-1], weak};
        stdCell.fillDiffAndSelectNotches(tmp, false);

		// Nand input: inb
		// m1_wid + m1_space + m1_wid/2
		LayoutLib.newExport(nand, "inb", ep, PortCharacteristic.IN, tech.m1(),
							4, inbX, inbY);
		TrackRouter inb = new TrackRouterH(tech.m1(), 3, inbY, tech, ep, nand);
		inb.connect(nand.findExport("inb"));
		for (int i=0; i<pmoss.length; i++) {
			FoldedMos pmos = pmoss[i];
			inb.connect(pmos.getGate(0, 'B'), 4, tech.getPolyLShapeOffset());
			if (pmos.nbGates()==2) {
				inb.connect(pmos.getGate(1, 'B'), -4, tech.getPolyLShapeOffset());
			}
		}
		for (int i=0; i<nmos.nbGates(); i+=2) {
			if (i/2 %2 == 0) {
				inb.connect(nmos.getGate(i+1, 'T'), 4, -tech.getPolyTShapeOffset());
			} else {
				inb.connect(nmos.getGate(i, 'T'), -4, -tech.getPolyTShapeOffset());
			}
		}
		
		// Nand input: ina
		double rightDiffX = StdCellParams.getRightDiffX(weak, weak);
		double inaX = rightDiffX + wirePitch;
		LayoutLib.newExport(nand, "ina", ep, PortCharacteristic.IN, tech.m1(),
							4, inaX, inaY);
		TrackRouter ina = new TrackRouterH(tech.m1(), 3, inaY, tech, ep, nand);
		ina.connect(nand.findExport("ina"));
		for (int i=0; i<weak.nbGates(); i++) {
			ina.connect(weak.getGate(i, 'B'), 1.5, tech.getPolyLShapeOffset());
		}
		for (int i=0; i<nmos.nbGates(); i+=2) {
			if (i/2 % 2 == 0) {
				ina.connect(nmos.getGate(i, 'T'), -4, -tech.getPolyLShapeOffset());
			} else {
				ina.connect(nmos.getGate(i+1, 'T'), 4, -tech.getPolyLShapeOffset());
			}
		}
		
		// Nand output: out
		double outX = inaX + wirePitch;
		LayoutLib.newExport(nand, "out", ep, PortCharacteristic.OUT, tech.m1(),
							4, outX, outHiY);
		TrackRouter outHi = new TrackRouterH(tech.m2(), 4, outHiY, tech, ep, nand);
		outHi.connect(nand.findExport("out"));
		for (int i=0; i<pmoss.length; i++) {
			outHi.connect(pmoss[i].getSrcDrn(1));
		}
		for (int i=1; i<weak.nbSrcDrns(); i+=2) {
			outHi.connect(weak.getSrcDrn(i));
		}
		
		TrackRouter outLo = new TrackRouterH(tech.m2(), 4, outLoY, tech, ep, nand);
		outLo.connect(nand.findExport("out"));
		for (int i=1; i<nmos.nbSrcDrns(); i+=2) {
			outLo.connect(nmos.getSrcDrn(i));
		}

        // Due to weak transistors on the right
        // ============================
//        StdCellParams.fillSelect(nand, true, false, false);
        // ============================

		// add wells
		double wellMinX = 0;
		double wellMaxX = outX + 2 + 1.5; // m1_wid/2 + m1m1_space/2
		stdCell.addNmosWell(wellMinX, wellMaxX, nand);
		stdCell.addPmosWell(wellMinX, wellMaxX, nand);
		
		// add essential bounds
		stdCell.addEssentialBounds(wellMinX, wellMaxX, nand);
		
		// perform Network Consistency Check
		stdCell.doNCC(nand, nm+"{sch}");
		
		return nand;
	}
}
