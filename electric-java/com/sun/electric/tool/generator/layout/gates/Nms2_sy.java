/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Nms2_sy.java
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
import com.sun.electric.tool.generator.layout.FoldsAndWidth;
import com.sun.electric.tool.generator.layout.LayoutLib;
import com.sun.electric.tool.generator.layout.StdCellParams;
import com.sun.electric.tool.generator.layout.TechType;
import com.sun.electric.tool.generator.layout.TrackRouter;
import com.sun.electric.tool.generator.layout.TrackRouterH;
import com.sun.electric.tool.Job;

public class Nms2_sy {
	private static final double gY = -4.0;
	private static final double dY = -11.0;
	// p1_p1_sp/2 + p1m1_wid + p1_diff_sp
	private static final double nmosTop = -(1.5 + 5 + 2);
	
	private static void error(boolean pred, String msg) {
		Job.error(pred, msg);
	}
	
	public static Cell makePart(double sz, StdCellParams stdCell) {
		TechType tech = stdCell.getTechType();
        EditingPreferences ep = stdCell.getEditingPreferences();
	    sz = stdCell.roundSize(sz);
	    String nm = "nms2_sy";
	    sz = stdCell.checkMinStrength(sz, 1, nm);
	
	    // p1_p1_sp/2 + p1m1_wid + gate_overhang_diff
	    double nmosLowest = stdCell.getCellBot() + 1.5 + 5 + 2;
	    double spaceAvail = nmosTop - nmosLowest;
	    int nbStacked = 2;
	    double totWid = sz * 3 * nbStacked;
	    FoldsAndWidth fw = stdCell.calcFoldsAndWidth(spaceAvail, totWid, 2);
	    error(fw==null, "can't make Nms2_sy this small: "+sz);
	
	    // g2 must be spaced from gnd and nmos
	    // lowerGndEdge -m1_m1_sp - m1_wid/2
	    double g2FromGndY = stdCell.getGndY() - stdCell.getGndWidth()/2 - 3 - 2; 
	
	    // lowerMosEdge -gateOverhangDiff - p1m1_wid/2
	    double g2FromMosY = nmosTop - fw.physWid - 2 -2.5;
	    double g2Y = Math.min(g2FromGndY, g2FromMosY);
	
	    Cell nms2 = stdCell.findPart(nm, sz);
	    if (nms2!=null) return nms2;
	    nms2 = stdCell.newPart(nm, sz);
	
	    // leave vertical m1 track for g
	    double gX = 1.5 + 2;		// m1_m1_sp/2 + m1_wid/2
	    LayoutLib.newExport(nms2, "g", ep, PortCharacteristic.IN, tech.m1(), 4,
							gX, gY);
	    double mosX = gX + 2 + 3 + 2; 	// m1_wid/2 + m1_m1_sp + m1_wid/2
	    
	    double nmosY = nmosTop - fw.physWid/2;
	    FoldedMos nmos = new FoldedNmos(mosX, nmosY, fw.nbFolds, nbStacked,
					   fw.gateWid, nms2, tech, ep);
	    // g2  m1_wid/2 + m1_m1_sp + m1_wid/2
	    double g2X = StdCellParams.getRightDiffX(nmos) + 2 + 3 + 2;
	    LayoutLib.newExport(nms2, "g2", ep, PortCharacteristic.IN, tech.m1(), 4,
							g2X, g2Y);
	    // output d  m1_wid/2 + m1_m1_sp + m1_wid/2
	    double dX = g2X + 2 + 3 + 2;
	    LayoutLib.newExport(nms2, "d", ep, PortCharacteristic.OUT, tech.m1(), 4,
							dX, dY);
	    // create gnd export and connect to MOS source/drains
	    stdCell.wireVddGnd(nmos, StdCellParams.EVEN, nms2);
	
	    // connect inputs g and g2
	    TrackRouter g = new TrackRouterH(tech.m1(), 3, gY, tech, ep, nms2);
	    TrackRouter g2 = new TrackRouterH(tech.m1(), 3, g2Y, tech, ep, nms2);
	    g.connect(nms2.findExport("g"));
	    g2.connect(nms2.findExport("g2"));
	    for (int i=0; i<nmos.nbGates(); i++) {
	      switch (i%2) {
	      case 0: g.connect(nmos.getGate(i, 'T'), -1.5);  break;
	      case 1: g2.connect(nmos.getGate(i, 'B'), 1.5); break;
	      }
	    }
	
	    // connect output d
	    TrackRouter d = new TrackRouterH(tech.m2(), 4, dY, tech, ep, nms2);
	    d.connect(nms2.findExport("d"));
	    for (int i=1; i<nmos.nbSrcDrns(); i+=2) {d.connect(nmos.getSrcDrn(i));}
	
	    // add wells
	    double wellMinX = 0;
	    double wellMaxX = dX + 2 + 1.5; // m1_wid/2 + m1m1_space/2
	    stdCell.addNmosWell(wellMinX, wellMaxX, nms2);
	
	    // add essential bounds
	    stdCell.addNstackEssentialBounds(wellMinX, wellMaxX, nms2);
	
	    // perform Network Consistency Check
	    stdCell.doNCC(nms2, nm+"{sch}");
	
	    return nms2;
	  }
}

