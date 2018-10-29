/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Inv_star_wideOutput.java
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

/**
 * Create inverters with wide output busses.
 */
/** run a wide output bus in metal-1 along n-well/p-well boundary */
/** Tricky: Normally the strong PMOS is folded and the right most
 * source/drain is ground. However, if the strong PMOS is so small
 * that it has only one fold then the right most source/drain is the
 * output.  This creates a special case for the weak PMOS because the
 * weak PMOS overlaps the strong PMOS's right most source/drain. When
 * the strong PMOS gets very small, we have to mirror the weak PMOS
 * about it's gate. */
public class Inv2iKn {
//	private static final double outHiY = 11.0;
	private static final double outLoY = -11.0;
	private static final double outBusWidth = 10;
	private static final double outBusSpace =  3;
	private static final double outY = 0;
	private static final double weakRatio = 10;
	
	private static class MyGateSpace implements FoldedMos.GateSpace {
		double dogBoneExtra;
		MyGateSpace(TechType tech) {
			dogBoneExtra = tech.getGateToDiffContSpaceDogBone() -
			             tech.getGateToDiffContSpace();
		}
		
		public double getExtraSpace(double requiredExtraSpace, int foldNdx,
				int nbFolds, int spaceNdx, int nbGates){
			return (foldNdx==nbFolds-1 && spaceNdx==1) ? 
					  	dogBoneExtra 
					: 
						requiredExtraSpace;
		}
	}

	private static void error(boolean pred, String msg) {
		Job.error(pred, msg);
	}

	// If strong PMOS has only one fold then connect ODD src/drn of weak
	// NMOS to ground
	private static final StdCellParams.SelectSrcDrn flipWeakPmos =
		new StdCellParams.SelectSrcDrn() {
			public boolean connectThisOne(int mosNdx, int srcDrnNdx) {
				if (mosNdx==0) {
					return srcDrnNdx%2==1;
				} else if (mosNdx==1) {
					return srcDrnNdx%2==0;
				} else {
					error(true, "more than two FoldedMos?");
					return false;
				}
			}
		};
	public static Cell makePart(double sz, StdCellParams stdCell) {
		TechType tech = stdCell.getTechType();
        EditingPreferences ep = stdCell.getEditingPreferences();
		sz = stdCell.roundSize(sz);
		String nm = "inv2iKn";
		sz = stdCell.checkMinStrength(sz, 1, nm);

		double outsideSpace = 2 + 5 + 1.5; // p1_nd_sp + p1m1_wid + p1_p1_sp/2
		double insideSpace = outBusWidth/2 + outBusSpace - .5; // MOS diff surrounds m1 by .5  

		double spaceAvail = stdCell.getCellTop() - outsideSpace - insideSpace;

		// find number of folds and width of PMOS
		double totWidP = sz * 6;
		FoldsAndWidth fwP = stdCell.calcFoldsAndWidth(spaceAvail, totWidP, 1);
		error(fwP==null, "can't make "+nm+" this small: " + sz);

		// Weak PMOS is 1/weakRatio the width of the strong PMOS
		totWidP = Math.max(3, totWidP/weakRatio);
		FoldsAndWidth fwW = stdCell.calcFoldsAndWidth(spaceAvail, totWidP, 1);
		error(fwW==null, "can't make "+nm+" this small: "+sz);
		
		// find number of folds and width of NMOS
		spaceAvail = -insideSpace - (stdCell.getCellBot() + outsideSpace);
		double totWidN = sz * 3;
		FoldsAndWidth fwN = stdCell.calcFoldsAndWidth(spaceAvail, totWidN, 1);
		error(fwN==null, "can't make "+nm+" this small: " + sz);

		// create Inverter Part
		Cell inv = stdCell.findPart(nm, sz);
		if (inv!=null)  return inv;
		inv = stdCell.newPart(nm, sz);

		// leave vertical m1 tracks for in[p], in[n], cross-over
		double inNX = 1.5 + 2; // m1_m1_sp/2 + m1_wid/2
		double inPX = inNX + 1.5 + 2; // m1_m1_sp/2 + m1_wid/2
		double weakX = inPX + 2 + 3 + 2; // m1_wid/2 + m1_m1_sp + m1_wid/2
		double nmosX = weakX + 2 + 3 + 2; // m1_wid/2 + m1_m1_sp + m1_wid/2
		
		double nmosY = -insideSpace - fwN.physWid / 2;
		FoldedMos nmos = new FoldedNmos(nmosX, nmosY, fwN.nbFolds, 1, 
		                                fwN.gateWid, inv, tech, ep);

		// Insert extra space between the leftmost diffusion contact and the
		// leftmost gate.
		FoldedMos.GateSpace spaceLastGate =	new MyGateSpace(tech);
		double weakY = insideSpace + fwW.physWid/2;
		FoldedMos pmosW = new FoldedPmos(weakX, weakY, fwW.nbFolds, 1,
										 fwW.gateWid, spaceLastGate, 'B', inv, tech, ep);
		
		// strong PMOS overlaps weak PMOS
		double pmosY = insideSpace + fwP.physWid/2;
		double pmosX = pmosW.getSrcDrn(fwW.nbFolds).getBounds().getCenterX();
		FoldedMos pmos = new FoldedPmos(pmosX, pmosY, fwP.nbFolds, 1,
		                                fwP.gateWid, inv, tech, ep);

		// inverter output:  m1_wid/2 + m1_m1_sp + m1_wid/2 
		double outX = StdCellParams.getRightDiffX(nmos, pmos) + 2 + 3 + 2;
		LayoutLib.newExport(inv, "out", ep, PortCharacteristic.OUT,
			                tech.m1(), 4, outX, 0);

		// create vdd and gnd exports and connect to MOS source/drains
		stdCell.wireVddGnd(nmos, StdCellParams.EVEN, inv);
		boolean weakPmosOneFold = fwW.nbFolds==1;
		stdCell.wireVddGnd(new FoldedMos[] {pmosW, pmos},
				  		   weakPmosOneFold ? flipWeakPmos : StdCellParams.EVEN,
				           inv);

		// Connect gates of weak MOS using poly
		TrackRouter weakPoly = new TrackRouterH(tech.p1(), 2, 0, tech, ep, inv);
		for (int i=0; i<pmosW.nbGates(); i++) {
			weakPoly.connect(pmosW.getGate(i, 'B'));
		}
		// Connect an equal number of NMOS gates as weak PMOS gates
		for (int i=0; i<Math.min(nmos.nbGates(), pmosW.nbGates()); i++) {
			weakPoly.connect(nmos.getGate(i, 'T'));
		}
		
		// Connect gates using metal1 along bottom of cell 
		double gndBot = stdCell.getGndY() - stdCell.getGndWidth() / 2;
		double inLoFromGnd = gndBot - 3 - 2; // -m1_m1_sp -m1_wid/2
		double nmosBot = nmosY - fwN.physWid / 2;
		double inLoFromMos = nmosBot - 2 - 2.5; // -nd_p1_sp - p1m1_wid/2
		double inLoY = Math.min(inLoFromGnd, inLoFromMos);

		LayoutLib.newExport(inv, "in[n]", ep, PortCharacteristic.IN, tech.m1(),
                            4, inNX, outLoY);
		PortInst m1pin = LayoutLib.newNodeInst(tech.m1pin(), ep, weakX, outLoY, 4, 4, 0, 
				                               inv).getOnlyPortInst();
		TrackRouter inNHi = new TrackRouterH(tech.m2(), 3, outLoY, tech, ep, inv);
		inNHi.connect(inv.findExport("in[n]"));
		inNHi.connect(m1pin);

		TrackRouter inLo = new TrackRouterH(tech.m1(), 3, inLoY, tech, ep, inv);
		inLo.connect(m1pin);
		for (int i = 0; i < nmos.nbGates(); i++) {
			inLo.connect(nmos.getGate(i, 'B'));
		}

		// Connect gates using metal1 along top of cell 
		double vddTop = stdCell.getVddY() + stdCell.getVddWidth() / 2;
		double inHiFromVdd = vddTop + 3 + 2; // +m1_m1_sp + m1_wid/2
		double pmosTop = pmosY + fwP.physWid / 2;
		double inHiFromMos = pmosTop + 2 + 2.5; // +pd_p1_sp + p1m1_wid/2
		double inHiY = Math.max(inHiFromVdd, inHiFromMos);

		LayoutLib.newExport(inv, "in[p]", ep, PortCharacteristic.IN, tech.m1(),
                            4, inPX, inHiY);
		TrackRouter inHi = new TrackRouterH(tech.m1(), 3, inHiY, tech, ep, inv);
		inHi.connect(inv.findExport("in[p]"));
		for (int i=0; i<pmos.nbGates(); i++) {
			inHi.connect(pmos.getGate(i, 'T'));
		}

		// connect up output
		TrackRouter out = new TrackRouterH(tech.m1(), outBusWidth, outY, tech, ep, inv);
		out.connect(inv.findExport("out"));
		for (int i=(weakPmosOneFold?0:1); i<pmosW.nbSrcDrns(); i+=2) {
			out.connect(pmosW.getSrcDrn(i));
		}
		for (int i=1; i<pmos.nbSrcDrns(); i+=2) {
			out.connect(pmos.getSrcDrn(i));
		}
		for (int i=1; i<nmos.nbSrcDrns(); i+=2) {
			out.connect(nmos.getSrcDrn(i));
		}

		// add wells
		double wellMinX = 0;
		double wellMaxX = outX + outBusWidth/2 + 1.5; // m1_wid/2 + m1m1_space/2
		stdCell.addNmosWell(wellMinX, wellMaxX, inv);
		stdCell.addPmosWell(wellMinX, wellMaxX, inv);

		// add essential bounds
		stdCell.addEssentialBounds(wellMinX, wellMaxX, inv);

		// perform Network Consistency Check
		stdCell.doNCC(inv, nm+"{sch}");

		return inv;
	}
}
