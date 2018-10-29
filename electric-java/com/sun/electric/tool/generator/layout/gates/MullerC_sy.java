/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: MullerC_sy.java
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

public class MullerC_sy {
	private static final double nmosTop = -9.0;
	private static final double pmosBot = 9.0;
	private static final double wellOverhangDiff = 6;
	private static final double inbY = 4.0;
	private static final double inaY = -4.0;
	private static final double outHiY = 11.0;
	private static final double outLoY = -11.0;
	
	private static void error(boolean pred, String msg) {
		Job.error(pred, msg);
	}
	
	public static Cell makePart(double sz, StdCellParams stdCell) {
		TechType tech = stdCell.getTechType();
        EditingPreferences ep = stdCell.getEditingPreferences();
		sz = stdCell.roundSize(sz);
		String nm = "mullerC_sy";
		sz = stdCell.checkMinStrength(sz, 1, nm);
		
		double spaceAvail = nmosTop - (stdCell.getCellBot() + wellOverhangDiff);
		int nbSeries = 2;
		double totWid = sz * 3 * nbSeries;
		FoldsAndWidth fwN = stdCell.calcFoldsAndWidth(spaceAvail, totWid, 2);
		error(fwN==null, "can't make "+nm+" this small: "+sz);
		
		// p1_p1_sp/2 + p1m1_wid + p1_diff_sp
		spaceAvail = stdCell.getCellTop() - wellOverhangDiff - pmosBot;
		totWid = sz * 6 * nbSeries;
		FoldsAndWidth fwP = stdCell.calcFoldsAndWidth(spaceAvail, totWid, 2);
		error(fwP==null, "can't make "+nm+" this small: "+sz);
		
		Cell mull = stdCell.findPart(nm, sz);
		if (mull!=null) return mull;
		mull = stdCell.newPart(nm, sz);
		
		// leave vertical m1 track for ina
		double inaX = 1.5 + 2;		// m1_m1_sp/2 + m1_wid/2
		LayoutLib.newExport(mull, "ina", ep, PortCharacteristic.IN, tech.m1(),
							4, inaX, inaY);
		double mosX = inaX + 2 + 3 + 2; 	// m1_wid/2 + m1_m1_sp + m1_wid/2
		
		double nmosY = nmosTop - fwN.physWid/2;
		FoldedMos nmos = new FoldedNmos(mosX, nmosY, fwN.nbFolds, nbSeries,
										fwN.gateWid, mull, tech, ep);
		
		double pmosY = pmosBot + fwP.physWid/2;
		FoldedMos pmos = new FoldedPmos(mosX, pmosY, fwP.nbFolds, nbSeries,
										fwP.gateWid, mull, tech, ep);
		
		double rightDiffX =
			StdCellParams.getRightDiffX(nmos, pmos); 
		// inb  m1_wid/2 + m1_m1_sp + m1_wid/2
		double inbX = rightDiffX + 2 + 3 + 2;
		LayoutLib.newExport(mull, "inb", ep, PortCharacteristic.IN, tech.m1(),
							4, inbX, inbY);
		// output d  m1_wid/2 + m1_m1_sp + m1_wid/2
		double outX = inbX + 2 + 3 + 2;
		LayoutLib.newExport(mull, "out", ep, PortCharacteristic.OUT, tech.m1(),
							4, outX, outLoY);
		// create gnd export and connect to MOS source/drains
		stdCell.wireVddGnd(nmos, StdCellParams.EVEN, mull);
		stdCell.wireVddGnd(pmos, StdCellParams.EVEN, mull);
		
		// connect input ina
		TrackRouter ina = new TrackRouterH(tech.m1(), 3, inaY, tech, ep, mull);
		ina.connect(mull.findExport("ina"));
		for (int i=0; i<nmos.nbGates(); i+=2) {
			ina.connect(nmos.getGate(i, 'T'), -1.5);
		}
		for (int i=0; i<pmos.nbGates(); i+=2) {
			ina.connect(pmos.getGate(i, 'B'), -1.5);
		}
		
		// connect input inb
		TrackRouter inb = new TrackRouterH(tech.m1(), 3, inbY, tech, ep, mull);
		inb.connect(mull.findExport("inb"));
		for (int i=1; i<pmos.nbGates(); i+=2) {
			inb.connect(pmos.getGate(i, 'B'), 1.5);
		}
		for (int i=1; i<nmos.nbGates(); i+=2) {
			inb.connect(nmos.getGate(i, 'T'), 1.5);
		}
		
		// connect output outLo
		TrackRouter outLo = new TrackRouterH(tech.m2(), 4, outLoY, tech, ep, mull);
		outLo.connect(mull.findExport("out"));
		for (int i=1; i<nmos.nbSrcDrns(); i+=2) {
			outLo.connect(nmos.getSrcDrn(i));
		}
		
		// connect output outhi
		TrackRouter outHi = new TrackRouterH(tech.m2(), 4, outHiY, tech, ep, mull);
		outHi.connect(mull.findExport("out"));
		for (int i=1; i<pmos.nbSrcDrns(); i+=2) {
			outHi.connect(pmos.getSrcDrn(i));
		}
		
		// add wells
		double wellMinX = 0;
		double wellMaxX = outX + 2 + 1.5; // m1_wid/2 + m1m1_space/2
		stdCell.addNmosWell(wellMinX, wellMaxX, mull);
		stdCell.addPmosWell(wellMinX, wellMaxX, mull);
		
		// add essential bounds
		stdCell.addEssentialBounds(wellMinX, wellMaxX, mull);
		
		// perform Network Consistency Check
		stdCell.doNCC(mull, nm+"{sch}");
		
		return mull;
	}
}

