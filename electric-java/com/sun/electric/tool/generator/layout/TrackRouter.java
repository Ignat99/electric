/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: TrackRouter.java
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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.sun.electric.database.EditingPreferences;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.Export;
import com.sun.electric.technology.ArcProto;
import com.sun.electric.database.topology.ArcInst;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.topology.PortInst;
import com.sun.electric.tool.Job;

public abstract class TrackRouter {
	// ----------------------- private and protected data ------------------------
	private double shareableViaDist = 6;
	// The list: vias is a list of ViaStacks sorted by their X/Y
	// coordinates.  ViaStack connections are daisy chained in the order
	// that they occur on this list. TrackRouter maintains daisy-chained
	// connections to avoid overlapping metal. This was done because I
	// initially understood that Electric doesn't report parasitics
	// correctly when metal overlaps. This rumor may not be true.
	private List<ViaStack> vias = new ArrayList<ViaStack>();
	// lastPos is a Cursor into vias that makes insertion more
	// efficient. The next point of insertion is often very close to the
	// last point of insertion.
	private int lastPos = 0;

	Cell parent;
	PortInst curPort;
	ArcProto layer;
	double width = 0;
	Double center = null;
	PortInst prevElbow;
    boolean endsExtend;
    final TechType tech;
    final EditingPreferences ep;

	// ------------------ private and protected methods ----------------------
	static void error(boolean pred, String msg) {
		Job.error(pred, msg);
	}

	private double getXY(ViaStack via) {
		return (this instanceof TrackRouterH)
			? via.getCenterX()
			: via.getCenterY();
	}

	private void removeExistingArc(ViaStack prev, ViaStack next) {
		if (prev == null || next == null)
			return;
		PortInst prevPort = prev.getPort1();
		PortInst nextPort = next.getPort1();
		for (Iterator<ArcInst> it = LayoutLib.getArcInstsOnPortInst(prevPort);
			it.hasNext();
			) {
			ArcInst ai = it.next();
			PortInst end0 = ai.getHeadPortInst();
			PortInst end1 = ai.getTailPortInst();
			if (end0 == nextPort || end1 == nextPort)
				ai.kill();
		}
	}

	private void addArc(ViaStack v1, ViaStack v2) {
		PortInst p1 = v1.getPort1();
		PortInst p2 = v2.getPort1();
		ArcInst ai = LayoutLib.newArcInst(layer, ep, width, p1, p2);
        ai.setHeadExtended(endsExtend);
        ai.setTailExtended(endsExtend);
	}

	private void addArcs(ViaStack prev, ViaStack via, ViaStack next) {
		if (prev != null)
			addArc(prev, via);
		if (next != null)
			addArc(via, next);
	}

	void insertVia(ViaStack via) {
		if (vias.isEmpty()) {vias.add(via);  return;}
		double myXY = getXY(via);
		while (lastPos>0 && getXY(vias.get(lastPos-1))>myXY)
			lastPos--;
		while (lastPos < vias.size() 
		       && getXY( vias.get(lastPos)) < myXY)  lastPos++;
		vias.add(lastPos, via);
		ViaStack prev = lastPos==0 ? null : (ViaStack)vias.get(lastPos-1);
		ViaStack next =	lastPos==vias.size()-1
				              ? null
                         	  : (ViaStack) vias.get(lastPos+1);
		removeExistingArc(prev, next);
		addArcs(prev, via, next);
	}

	// Find the closest via port to position. Search the range position
	// +/- shareableViaDist. If nothing found then return null.
	ViaStack findSharableVia(double position, ArcProto lay) {
		if (vias.isEmpty())
			return null;

		int closest = -1;
		double closestDist = Double.MAX_VALUE;

		// search backwards
		for (int i = lastPos; i >= 0; i--) {
			ViaStack v = vias.get(i);
			double dist = Math.abs(getXY(v) - position);
			if (dist > closestDist)  break;
			if (dist > shareableViaDist)  continue;
			PortInst pi = v.getPort2();
			if (pi.getPortProto().connectsTo(lay)) {
				if (dist<closestDist) {
					closest = i;
					closestDist = dist;
				}
			}
		}
		// search forwards
		for (int i = lastPos + 1; i < vias.size(); i++) {
			ViaStack v = vias.get(i);
			double dist = Math.abs(getXY(v) - position);
			if (dist > closestDist)  break;
			if (dist > shareableViaDist)  continue;
			PortInst pi = v.getPort2();
			if (pi.getPortProto().connectsTo(lay)) {
				if (dist<closestDist) {
					closest = i;
					closestDist = dist;
				}
			}
		}
		if (closestDist == Double.MAX_VALUE)  return null;
		lastPos = closest;
		return vias.get(closest);
	}

	//------------------------------- public methods  ----------------------------
	// all ports lie on the same routing track
	public TrackRouter(ArcProto lay, double wid, TechType tech, EditingPreferences ep, Cell parnt) {
		parent = parnt;
		layer = lay;
		width = wid;
        endsExtend = true;
        this.tech = tech;
        this.ep = ep;
	}

	// ports may be offset from routing track
	public TrackRouter(ArcProto lay, double wid, double centerVal,
			           TechType tech, EditingPreferences ep,
	                   Cell parnt) {
		parent = parnt;
		layer = lay;
		width = wid;
		center = new Double(centerVal);
        endsExtend = true;
        this.tech = tech;
        this.ep = ep;
	}

    public void setEndsExtend(boolean b) { endsExtend = b; }
    public boolean getEndsExtend() { return endsExtend; }

	public void connect(ArrayList<NodeInst> nodeInsts, String portNm) {
		ArrayList<PortInst> ports = new ArrayList<PortInst>();
		for (int i=0; i<nodeInsts.size(); i++) {
			PortInst p = nodeInsts.get(i).findPortInst(portNm);
			if (p!=null)  ports.add(p);
		}
		connect(ports);
	}

	public void connect(NodeInst[] nodeInsts, String portNm) {
		ArrayList<NodeInst> a = new ArrayList<NodeInst>();
		for (int i=0; i<nodeInsts.length; i++)  a.add(nodeInsts[i]);
		connect(a, portNm);
	}

	public void connect(ArrayList<PortInst> ports) {
		for (int i=0; i<ports.size(); i++)  connect(ports.get(i));
	}

	public void connect(PortInst[] ports) {
		ArrayList<PortInst> p = new ArrayList<PortInst>();
		for (int i=0; i<ports.length; i++)  p.add(ports[i]);
		connect(p);
	}
	public void connect(PortInst newPort) {connect(newPort, 0);}
	public void connect(Export export) {
		connect(export.getOriginalPort(), 0);
	}
	public void connect(PortInst newPort, double viaOffset) {
		connect(newPort, viaOffset, 0);
	}

	// this connect is specialized for TrackRouterH and TrackRouterV
	public abstract void connect(PortInst newPort, double viaOffset, 
	                             double wireOffset);
	/** If you find an existing via within distance d of this connection,
	 * connect to that via rather than creating a new one. */
	public void setShareableViaDist(double d) {shareableViaDist = d;}
}
