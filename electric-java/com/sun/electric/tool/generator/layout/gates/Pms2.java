/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Pms2.java
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
import com.sun.electric.tool.generator.layout.FoldedPmos;
import com.sun.electric.tool.generator.layout.FoldsAndWidth;
import com.sun.electric.tool.generator.layout.LayoutLib;
import com.sun.electric.tool.generator.layout.StdCellParams;
import com.sun.electric.tool.generator.layout.TechType;
import com.sun.electric.tool.generator.layout.TrackRouter;
import com.sun.electric.tool.generator.layout.TrackRouterH;
import com.sun.electric.tool.Job;

public class Pms2 {
	private static final double gY = 4.0;
	private static final double dY = 11.0;
	private static final double pmosBot = 9.0;
	
	private static void error(boolean pred, String msg) {
		Job.error(pred, msg);
	}
	
	public static Cell makePart(double sz, StdCellParams stdCell) {
		TechType tech = stdCell.getTechType();
        EditingPreferences ep = stdCell.getEditingPreferences();
		sz = stdCell.roundSize(sz);
		String nm = "pms2";
		sz = stdCell.checkMinStrength(sz, .25, nm);
		
		int nbStacked = 2;
		// p1_p1_sp/2 + p1m1_wid + p1_diff_sp
		double spaceAvail = stdCell.getCellTop() - 1.5 - 5 - 2 - pmosBot;
		double totWid = sz * 6 * nbStacked;
		FoldsAndWidth fw = stdCell.calcFoldsAndWidth(spaceAvail, totWid, 1);
		error(fw==null, "can't make Pms2 this small: "+sz);
		
		// g2 must be spaced from vdd rail and pmos
		// upperVddEdge +m1_m1_sp + m1_wid/2
		double g2FromVddY =
			stdCell.getVddY() + stdCell.getVddWidth()/2 + 3 + 2; 
		
		// upperMosEdge + p1_diff_sp + p1m1_wid/2 
		double g2FromMosY = pmosBot + fw.physWid + 2 + 2.5;
		double g2Y = Math.max(g2FromVddY, g2FromMosY);
		
		Cell pms2 = stdCell.findPart(nm, sz);
		if (pms2!=null) return pms2;
		pms2 = stdCell.newPart(nm, sz);
		
		// leave vertical m1 track for g
		double gX = 1.5 + 2;		// m1_m1_sp/2 + m1_wid/2
		LayoutLib.newExport(pms2, "g", ep, PortCharacteristic.IN, tech.m1(),
							4, gX, gY);
		double mosX = gX + 2 + 3 + 2; 	// m1_wid/2 + m1_m1_sp + m1_wid/2
		double pmosY = pmosBot + fw.physWid/2;
		FoldedMos pmos = new FoldedPmos(mosX, pmosY, fw.nbFolds, nbStacked,
										fw.gateWid, pms2, tech, ep);
		
		// g2  m1_wid/2 + m1_m1_sp + m1_wid/2
		double g2X = StdCellParams.getRightDiffX(pmos) + 2 + 3 + 2;
		LayoutLib.newExport(pms2, "g2", ep, PortCharacteristic.IN, tech.m1(),
							4, g2X, g2Y);
		// output  m1_wid/2 + m1_m1_sp + m1_wid/2
		double dX = g2X + 2 + 3 + 2;
		LayoutLib.newExport(pms2, "d", ep, PortCharacteristic.OUT, tech.m1(),
							4, dX, dY);
		// create gnd export and connect to MOS source/drains
		stdCell.wireVddGnd(pmos, StdCellParams.EVEN, pms2);
		
		// connect inputs a and b
		TrackRouter g = new TrackRouterH(tech.m1(), 3, gY, tech, ep, pms2);
		TrackRouter g2 = new TrackRouterH(tech.m1(), 3, g2Y, tech, ep, pms2);
		g.connect(pms2.findExport("g"));
		g2.connect(pms2.findExport("g2"));
		for (int i=0; i<pmos.nbGates(); i+=2) {
			// connect 2 gates at a time in case nbFolds==1
			if ((i/2)%2==0) {
				g.connect(pmos.getGate(i, 'B'), -4, tech.getPolyLShapeOffset());
				g2.connect(pmos.getGate(i+1, 'T'), 4, -tech.getPolyLShapeOffset());
			} else {
				g.connect(pmos.getGate(i+1, 'B'), 4, tech.getPolyLShapeOffset());
				g2.connect(pmos.getGate(i, 'T'), -4, -tech.getPolyLShapeOffset());
			}
		}
		
		// connect output
		TrackRouter d = new TrackRouterH(tech.m2(), 4, dY, tech, ep, pms2);
		d.connect(pms2.findExport("d"));
		for (int i=1; i<pmos.nbSrcDrns(); i+=2) {d.connect(pmos.getSrcDrn(i));}
		
		// add wells
		double wellMinX = 0;
		double wellMaxX = dX + 2 + 1.5; // m1_wid/2 + m1m1_space/2
		stdCell.addPmosWell(wellMinX, wellMaxX, pms2);
		
		// add essential bounds
		stdCell.addPstackEssentialBounds(wellMinX, wellMaxX, pms2);
		
		// perform Network Consistency Check
		stdCell.doNCC(pms2, nm+"{sch}");
		
		return pms2;
	}
}
