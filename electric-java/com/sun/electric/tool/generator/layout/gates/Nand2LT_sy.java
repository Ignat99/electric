/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Nand2LT_sy.java
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

public class Nand2LT_sy {
	private static final double nmosTop = -9.0;
	private static final double pmosBot = 9.0;
	private static final double wellOverhangDiff = 6;
	private static final double inbY = -4.0;
	private static final double inaY = 4.0;
	private static final double outHiY = 11.0;
	private static final double outLoY = -11.0;
    
	private static void error(boolean pred, String msg) {
		Job.error(pred, msg);
	}

	public static Cell makePart(double sz, StdCellParams stdCell) {
		TechType tech = stdCell.getTechType();
        EditingPreferences ep = stdCell.getEditingPreferences();
		sz = stdCell.roundSize(sz);
		String nm = "nand2LT_sy";
		sz = stdCell.checkMinStrength(sz, 1, nm);
		
		// Compute number of folds and width for PMOS
		double spaceAvail = stdCell.getCellTop() - wellOverhangDiff - pmosBot;
		double totWid = sz * 3 * 2;	// 2 independent pullups
		FoldsAndWidth fwP = stdCell.calcFoldsAndWidth(spaceAvail, totWid, 2);
		error(fwP==null, "can't make nand2LT_sy this small: "+sz);
		
		// Compute number of folds and width for NMOS
		int nbStacked = 2;
		spaceAvail = nmosTop - (stdCell.getCellBot()+wellOverhangDiff);
		totWid = sz * 3 * nbStacked;
		FoldsAndWidth fwN = stdCell.calcFoldsAndWidth(spaceAvail, totWid, 2);
		error(fwN==null, "can't make nand2LT_sy this small: "+sz);
		
		// create NAND Part
		Cell nand = stdCell.findPart(nm, sz);
		if (nand!=null) return nand;
		nand = stdCell.newPart(nm, sz);
		
		// leave vertical m1 track for inB
		double inbX = 1.5 + 2;		// m1_m1_sp/2 + m1_wid/2
		double mosX = inbX + 2 + 3 + 2; // m1_wid/2 + m1_m1_sp + diffCont_wid/2
		
		// NMOS
		double nmosY = nmosTop - fwN.physWid/2;
		FoldedMos nmos = new FoldedNmos(mosX, nmosY, fwN.nbFolds, nbStacked,
										fwN.gateWid, nand, tech, ep);
		
		// PMOS Create multiple PMOS ORs.  Align source of each OR with
		// every other source of NMOS
		double pmosY = pmosBot + fwP.physWid/2;
		FoldedMos[] pmoss = new FoldedMos[fwP.nbFolds/2];
		for (int i=0; i<pmoss.length; i++) {
			double pmosPitch = 26;
			double pmosX = mosX + pmosPitch * i;
			pmoss[i] = new FoldedPmos(pmosX, pmosY, 2, 1, fwP.gateWid, nand, tech, ep);
		}
		stdCell.fillDiffAndSelectNotches(pmoss, true);

		// create vdd and gnd exports and connect to MOS source/drains
		stdCell.wireVddGnd(nmos, StdCellParams.EVEN, nand);
		stdCell.wireVddGnd(pmoss, StdCellParams.EVEN, nand);
		
		// Nand input B
		LayoutLib.newExport(nand, "inb", ep, PortCharacteristic.IN, tech.m1(),
							4, inbX, inbY);
		TrackRouter inB = new TrackRouterH(tech.m1(), 3, inbY, tech, ep, nand);
		inB.connect(nand.findExport("inb"));
		for (int i=0; i<nmos.nbGates(); i+=2) {
			inB.connect(nmos.getGate(i, 'T'), -1.5);
		}
		for (int i=0; i<pmoss.length; i++) {
			inB.connect(pmoss[i].getGate(0, 'B'), -1.5);
		}
		
		// Nand input A
		// NAND input in1
		// m1_wid + m1_space + m1_wid/2
		double inaX = StdCellParams.getRightDiffX(pmoss, nmos) + 2 + 3 + 2;
		LayoutLib.newExport(nand, "ina", ep, PortCharacteristic.IN, tech.m1(),
							4, inaX, inaY);
		TrackRouter ina = new TrackRouterH(tech.m1(), 3, inaY, tech, ep, nand);
		ina.connect(nand.findExport("ina"));
		for (int i=1; i<nmos.nbGates(); i+=2) {
			ina.connect(nmos.getGate(i, 'T'), 1.5);
		}
		for (int i=0; i<pmoss.length; i++) {
			ina.connect(pmoss[i].getGate(1, 'B'), -1.5);
		}
		
		// Nand output
		double outX = inaX + 2 + 3 + 2;	// m1_wid/2 + m1_sp + m1_wid/2
		LayoutLib.newExport(nand, "out", ep, PortCharacteristic.OUT, tech.m1(),
							4, outX, outHiY);
		TrackRouter outHi = new TrackRouterH(tech.m2(), 4, outHiY, tech, ep, nand);
		outHi.connect(nand.findExport("out"));
		for (int i=0; i<pmoss.length; i++) {
			outHi.connect(pmoss[i].getSrcDrn(1));
		}
		TrackRouter outLo = new TrackRouterH(tech.m2(), 4, outLoY, tech, ep, nand);
		outLo.connect(nand.findExport("out"));
		for (int i=1; i<nmos.nbSrcDrns(); i+=2) {
			outLo.connect(nmos.getSrcDrn(i));
		}
		
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

