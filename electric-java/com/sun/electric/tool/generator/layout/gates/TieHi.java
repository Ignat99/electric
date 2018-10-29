/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: TieHi.java
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
import com.sun.electric.tool.generator.layout.LayoutLib;
import com.sun.electric.tool.generator.layout.StdCellParams;
import com.sun.electric.tool.generator.layout.TechType;

/**
 * This part has an output connected to Vdd.
 */ 
public class TieHi {
	private static final double DEF_SIZE = LayoutLib.DEF_SIZE;

	public static Cell makePart(StdCellParams stdCell) {
		TechType tech = stdCell.getTechType();
        EditingPreferences ep = stdCell.getEditingPreferences();
		String nm = stdCell.parameterizedName("tieHi")+"{lay}";
		Cell tieHi = stdCell.findPart(nm);
		if (tieHi!=null) return tieHi;
		tieHi = stdCell.newPart(nm);
		
		// (m1m1 space)/2 + (m1 width)/2
		double pwrX = 1.5 + 2;
		double pwrY = stdCell.getVddY();
		
		// We need to export two pins in order to export two hints, one
		// for the width of metal-2 Vdd connections and one for the width
		// of metal-1 out connections.
		String vddName = stdCell.getVddExportName();
		PortCharacteristic vddRole = stdCell.getVddExportRole();
		LayoutLib.newExport(tieHi, vddName, ep, vddRole, tech.m2(), 4, pwrX, pwrY);
		LayoutLib.newExport(tieHi, "pwr", ep, PortCharacteristic.OUT,
							tech.m1(), 4, pwrX, pwrY);

		// connect the two exports using a via
		PortInst via = LayoutLib.newNodeInst(tech.m1m2(), ep, pwrX,
											 pwrY, 4, stdCell.getVddWidth(),
											 0, tieHi).getOnlyPortInst();
		
		LayoutLib.newArcInst(tech.m2(), ep, DEF_SIZE,
							 tieHi.findExport(vddName).getOriginalPort(), via);
		LayoutLib.newArcInst(tech.m1(), ep, DEF_SIZE,
							 tieHi.findExport("pwr").getOriginalPort(), via);
		
		// Well width must be at least 12 to avoid DRC errors
		// This cell is one of the rare cases where the cell's essential
		// bounds are narrower than the well
		double wellMinX = pwrX - 6;
		double wellMaxX = pwrX + 6;
		stdCell.addNmosWell(wellMinX, wellMaxX, tieHi);
		stdCell.addPmosWell(wellMinX, wellMaxX, tieHi);
		
		// add essential bounds
		double cellMaxX = pwrX + 2 + 1.5; // (m1 width)/2 + (m1-m1 space)/2
		stdCell.addEssentialBounds(0, cellMaxX, tieHi);
		
		return tieHi;
	}
}
