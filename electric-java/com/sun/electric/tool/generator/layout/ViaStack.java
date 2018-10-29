/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: ViaStack.java
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
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.prototype.NodeProto;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.topology.PortInst;
import com.sun.electric.technology.ArcProto;
import com.sun.electric.technology.PrimitiveNode;

class ViaStack {
	// ------------------------------private data -----------------------------
	private PortInst port1, port2;
	private final TechType tech;
    private final EditingPreferences ep;

	// ---------------------------- private methods --------------------------
	private void swap() {
		PortInst tp = port1;
		port1 = port2;
		port2 = tp;
	}
	private void buildStack(int hLo, int hHi, double x, double y, double width,
		                    double height, Cell f) {
		PortInst viaBelow = null;
		for (int h=hLo; h<hHi; h++) {
			PrimitiveNode via = tech.viaAbove(h);

			// Don't let via width or height drop below minimum required for
			// 1 cut.
			double wid = Math.max(width, LayoutLib.getNodeProtoWidth(via, ep));
			double hei = Math.max(height, LayoutLib.getNodeProtoHeight(via, ep));

			PortInst viaAbove = LayoutLib.newNodeInst(via, ep, x, y, wid, hei, 0,
			                                          f).getOnlyPortInst();
			if (viaBelow == null) {
				// First via in stack
				port1 = viaAbove;
			} else {
				// connect to lower via
				LayoutLib.newArcInst(tech.layerAtHeight(h), ep, 1, viaBelow,
					                 viaAbove);
			}
			port2 = viaAbove;
			viaBelow = viaAbove;
		}
	}

	// ----------------------------- public methods --------------------------

	// square vias
	public ViaStack(ArcProto arc1, ArcProto arc2, double x, double y,
	                double width, TechType tech, EditingPreferences ep, Cell f) {
		this(arc1, arc2, x, y, width, width, tech, ep, f);
	}

	// rectangular vias
	public ViaStack(ArcProto arc1, ArcProto arc2, double x, double y,
		            double width, double height, TechType tech, EditingPreferences ep, Cell f) {
		this.tech = tech;
        this.ep = ep;
		int h1 = tech.layerHeight(arc1);
		int h2 = tech.layerHeight(arc2);
		int deltaZ = h2 - h1;
		if (arc1==arc2) {
			NodeProto pin = arc1.findOverridablePinProto(ep);
			double defSz = LayoutLib.DEF_SIZE;
			NodeInst pinInst = LayoutLib.newNodeInst(pin,ep,x,y,defSz,defSz,0,f);
			port1 =	port2 = pinInst.getOnlyPortInst();
		} else if (deltaZ>0) {
			// arc2 higher than arc1
			buildStack(h1, h2, x, y, width, height, f);
		} else {
			buildStack(h2, h1, x, y, width, height, f);
			swap();
		}
	}

	public PortInst getPort1() {return port1;}
	public PortInst getPort2() {return port2;}
	public double getCenterX() {return port1.getCenter().getX() /*LayoutLib.roundCenterX(port1)*/;}
	public double getCenterY() {return port1.getCenter().getY() /*LayoutLib.roundCenterY(port1)*/;}
}
