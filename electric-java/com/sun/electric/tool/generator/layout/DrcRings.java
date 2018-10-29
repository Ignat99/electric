/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: DrcRings.java
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
package com.sun.electric.tool.generator.layout;

import com.sun.electric.database.EditingPreferences;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Iterator;

import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.tool.generator.layout.gates.DrcRing;

public class DrcRings {
	public static class Filter {
		public boolean skip(NodeInst ni) {return false;}
	}

/*
    public static void addDrcRings(Cell gallery, Filter filter) {
        Library lib = gallery.getLibrary();
        addDrcRings(gallery, filter, new StdCellParams(lib, Tech.MOCMOS));
    }
*/

	public static void addDrcRings(Cell gallery, Filter filter, StdCellParams stdCell) {
		if (filter==null) filter = new Filter();
		
        double spacing = stdCell.getDRCRingSpacing();
        EditingPreferences ep = stdCell.getEditingPreferences();
		// record original gates to avoid putting DrcRings around DrcRings
		ArrayList<NodeInst> gates = new ArrayList<NodeInst>();
		for (Iterator<NodeInst> it=gallery.getNodes(); it.hasNext();) {
			 gates.add(it.next()); 
		} 
		
		// place a DrcRing around each instance
		for (int i=0; i<gates.size(); i++) {
			NodeInst ni = gates.get(i);
			
			// skip things user doesn't want ring around
			if (filter.skip(ni)) continue;
			
			// only do Cells
			if (!ni.isCellInstance())  continue;
			
			Rectangle2D cellBounds = LayoutLib.getBounds(ni);
			
			double ringW = cellBounds.getWidth() + spacing;
			double ringH = cellBounds.getHeight() + spacing;
			Cell ringProto = DrcRing.makePart(ringW, ringH, stdCell);
			
			// Center the ring about the cell			
			NodeInst ringInst = LayoutLib.newNodeInst(ringProto, ep, 0,0,0,0,0, 
													  gallery);
			Rectangle2D ringBounds = LayoutLib.getBounds(ringInst);
			
			LayoutLib.modNodeInst(ringInst,
								  cellBounds.getCenterX()-ringBounds.getCenterX(),
								  cellBounds.getCenterY()-ringBounds.getCenterY(),
								  0, 0, false, false, 0);
		}
	}
	
}
