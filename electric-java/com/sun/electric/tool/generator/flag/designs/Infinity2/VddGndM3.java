/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: VddGndM3.java
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
package com.sun.electric.tool.generator.flag.designs.Infinity2;

import com.sun.electric.database.EditingPreferences;
import java.awt.geom.Rectangle2D;
import java.util.Set;
import java.util.TreeSet;

import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.Library;
import com.sun.electric.database.text.CellName;
import com.sun.electric.database.topology.PortInst;
import com.sun.electric.tool.generator.layout.LayoutLib;
import com.sun.electric.tool.generator.layout.TechType;

public class VddGndM3 {
	private final double DEF_SIZE = LayoutLib.DEF_SIZE;
	private final double vddGndWidth = 23;
	
	Set<Integer> xCoords = new TreeSet<Integer>();
	VddGndM3() {
		// lets do one side.
		for (int i=0; i<28; i++) {
			xCoords.add((i*144)+72);
		}
		for (int i=0; i<6; i++) {
			// avoid m3 obstacles
			// These first two coincide with scan busses
			if (i==0) continue;
			if (i==1) continue;
			// These two might be an accidental collisions
			if (i==2) continue;
			if (i==3) continue;
			xCoords.add((-i*144)-72);
		}
	}
	public Cell makeVddGndM3Cell(Cell layCell, TechType tech, EditingPreferences ep) {
		Library layLib = layCell.getLibrary();
		CellName nm = layCell.getCellName();
		String m3Nm = nm.getName()+"_m3{lay}";
		Cell m3Cell = Cell.newInstance(layLib, m3Nm);
		Rectangle2D bounds = layCell.findEssentialBounds();
		double minX = bounds.getMinX();
		double minY = bounds.getMinY();
		double maxX = bounds.getMaxX();
		double maxY = bounds.getMaxY();
		LayoutLib.newNodeInst(tech.essentialBounds(), ep, minX, minY, 
				              DEF_SIZE, DEF_SIZE, 0, m3Cell);
		LayoutLib.newNodeInst(tech.essentialBounds(), ep, maxX, maxY, 
				              DEF_SIZE, DEF_SIZE, 180, m3Cell);
		for (Integer x : xCoords) {
			PortInst p1 = LayoutLib.newNodeInst(tech.m3pin(), ep, x, minY, 
					DEF_SIZE, DEF_SIZE, 180, m3Cell).getOnlyPortInst();
			PortInst p2 = LayoutLib.newNodeInst(tech.m3pin(), ep, x, maxY, 
					DEF_SIZE, DEF_SIZE, 180, m3Cell).getOnlyPortInst();
			LayoutLib.newArcInst(tech.m3(), ep, vddGndWidth, p1, p2);
		}
		
		
		return m3Cell;
	}
	
}
