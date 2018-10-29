/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: NetworkHighlighter.java
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
package com.sun.electric.tool.user;

import com.sun.electric.database.geometry.Poly;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.Export;
import com.sun.electric.database.hierarchy.HierarchyEnumerator;
import com.sun.electric.database.hierarchy.Nodable;
import com.sun.electric.database.network.Netlist;
import com.sun.electric.database.network.Network;
import com.sun.electric.database.prototype.PortProto;
import com.sun.electric.database.topology.ArcInst;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.topology.PortInst;
import com.sun.electric.database.variable.ElectricObject;
import com.sun.electric.database.variable.VarContext;
import com.sun.electric.util.math.FixpTransform;

import java.awt.Color;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * This class is used for hierarchical highlighting of networks
 */
public class NetworkHighlighter extends HierarchyEnumerator.Visitor {

	private static final boolean TRIMMEDDISPLAY = false;
    private Cell cell;
    private Netlist netlist;
    private Set<Network> nets;
    private BitSet netIDs;
    private int startDepth;
    private int endDepth;
    private int currentDepth;
    private List<Highlight> highlights = new ArrayList<Highlight>();

    private NetworkHighlighter(Cell cell, Netlist netlist, Set<Network> nets, int startDepth, int endDepth) {
        this.cell = cell;
        this.netlist = netlist;
        this.nets = nets;
        this.startDepth = startDepth;
        this.endDepth = endDepth;
        currentDepth = 0;
    }

    /**
     * Returns a list of Highlight objects that draw lines and boxes over
     * instances that denote the location of objects in that instance that
     * are connected to net. The depth of the search is specified by startDepth
     * and endDepth, which start at 0. If both are set to zero, only objects in the
     * cell Cell are highlighted.
     * @param cell the cell in which to highlight objects
     * @param netlist the netlist for the cell
     * @param nets objects connected to these networks will be highlighted
     * @param startDepth to start depth of the hierarchical search
     * @return endDepth the end depth of the hierarchical search
     */
    public static synchronized List<Highlight> getHighlights(Cell cell, Netlist netlist, Set<Network> nets,
    	int startDepth, int endDepth)
    {
        NetworkHighlighter networkHighlighter = new NetworkHighlighter(cell, netlist, nets, startDepth, endDepth);

        HierarchyEnumerator.enumerateCell(cell, VarContext.globalContext, networkHighlighter);

        return networkHighlighter.highlights;
    }

    public boolean enterCell(HierarchyEnumerator.CellInfo info)
    {
        if (currentDepth == 0) {
            // set the global net ID that will be used in sub cells
            netIDs = new BitSet();
            for (Network net : nets) {
                netIDs.set(info.getNetID(net));
            }
        }

        // do not go below depth 0 for schematics
        if (cell.isSchematic()) {
            if (currentDepth > 0) return false;
        }

        if (currentDepth >= startDepth) {
            if (currentDepth > endDepth) return false;      // stop here. No more traversal down.
            // highlight connected in current cell. Highlight actual objects if top level
            if (currentDepth == 0) {
                addNetworkObjects();
            } else {
                addNetworkPolys(info);
            }
        }

        // increment depth and continue
        currentDepth++;
        return true;
    }

    public void exitCell(HierarchyEnumerator.CellInfo info) {
        currentDepth--;
    }

    public boolean visitNodeInst(Nodable ni, HierarchyEnumerator.CellInfo info) {
        return true;
    }

    /**
     * Get objects connected to a network in a Cell.
     * @param cell the Cell to examine.
     * @param netlist the Netlist in the cell being examined.
     * @param nets the desired Networks to find in the cell.
     * @return Set of ArcInsts, PortInsts and Exports on the desired networs.
     */
    private static Set<ElectricObject> getNetworkObjects(Cell cell, Netlist netlist, Set<Network> nets)
    {
        Set<ElectricObject> objs = new HashSet<ElectricObject>();

        // all port instances on the networks
		if (!TRIMMEDDISPLAY)
		{
	        for (Iterator<NodeInst> it = cell.getNodes(); it.hasNext(); ) {
	            NodeInst ni = it.next();
	            if (ni.getNameKey().isBus() || ni.isIconOfParent()) continue;
	            for (Iterator<PortInst> pit = ni.getPortInsts(); pit.hasNext(); ) {
	                PortInst pi = pit.next();
	                PortProto portProto = pi.getPortProto();
	                if (portProto.getNameKey().isBus()) continue;
	                if (!nets.contains(netlist.getNetwork(pi))) continue;
	                objs.add(pi);
	            }
	        }
		}

        // all arcs on the networks
        for(Iterator<ArcInst> aIt = cell.getArcs(); aIt.hasNext(); ) {
            ArcInst ai = aIt.next();
            int width = netlist.getBusWidth(ai);
            for(int i=0; i<width; i++) {
                if (!nets.contains(netlist.getNetwork(ai, i))) continue;
                objs.add(ai);
        		if (!TRIMMEDDISPLAY)
        		{
	                // also highlight end nodes of arc, if they are primitive nodes
	                PortInst pi = ai.getHeadPortInst();
	                if (!pi.getNodeInst().isCellInstance()) {
	                    // ignore pins
	                    if (!pi.getNodeInst().getProto().getFunction().isPin())
	                        objs.add(pi);
	                }
	                pi = ai.getTailPortInst();
	                if (!pi.getNodeInst().isCellInstance()) {
	                    if (!pi.getNodeInst().getProto().getFunction().isPin())
	                        objs.add(pi);
	                }
        		}
            }
        }

        // show all exports on the networks if at the top level
		if (!TRIMMEDDISPLAY)
		{
	        for(Iterator<PortProto> pIt = cell.getPorts(); pIt.hasNext(); ) {
	            Export pp = (Export)pIt.next();
	            int width = netlist.getBusWidth(pp);
	            for(int i=0; i<width; i++) {
	                if (!nets.contains(netlist.getNetwork(pp, i))) continue;
	                objs.add(pp);
		        }
			}
		}
        return objs;
    }

    /**
     * Retrieves the User's preference color for NodeInst objects
     * @return the preference color.
     */
    private static Color getNodeOrPortColor()
    {
        return new Color(User.getColor(User.ColorPrefType.NODE_HIGHLIGHT)); // use to hightlight NodeInsts
    }

    /**
     * Add all network objects (arcs, primitive nodes) to highlighter for current cell.
     * Choose MOUSEOVER_HIGHLLIGHT color for non-wire objects and HIGHLIGHT color for wires.
     */
    private void addNetworkObjects() {
        // highlight objects in this cell
        Set<ElectricObject> objs = getNetworkObjects(cell, netlist, nets);
        Color nodeColor = getNodeOrPortColor();

        for (ElectricObject eObj : objs)
        {
            Color color = !((eObj instanceof ArcInst)) ? nodeColor : null;
            if (eObj instanceof Export)
            {
                highlights.add(new HighlightText(eObj, cell, Export.EXPORT_NAME));
            } else
            {
                highlights.add(new HighlightEOBJ(eObj, cell, false, -1, color));
            }
        }
    }

    /**
     * Add all network objects (arcs, primitive nodes) as poly
     * objects, transformed by trans.
     */
    private void addNetworkPolys(HierarchyEnumerator.CellInfo info) {
        Netlist netlist = info.getNetlist();

        // find all local networks in this cell that corresponds to global id
        Set<Network> localNets = new HashSet<Network>();
        for (Iterator<Network> it = netlist.getNetworks(); it.hasNext(); ) {
            Network aNet = it.next();
            if (netIDs.get(info.getNetID(aNet))) {
                localNets.add(aNet);
            }
        }
        addNetworkPolys(localNets, info);
    }

    private void addNetworkPolys(Set<Network> localNets, HierarchyEnumerator.CellInfo info) {
        Netlist netlist = info.getNetlist();

        // no local net that matches with global network
        if (localNets.size() == 0) return;

        Cell currentCell = info.getCell();
        Set<ElectricObject> objs = getNetworkObjects(currentCell, netlist, localNets);
        Color colorN = getNodeOrPortColor();

        // get polys for each object and transform them to root
        FixpTransform trans = info.getTransformToRoot();
        for (ElectricObject o : objs) {
            Color color = null;
            Poly poly = null;
            if (o instanceof ArcInst) {
                ArcInst ai = (ArcInst)o;
				if (TRIMMEDDISPLAY)
					poly = new Poly(ai.getHeadLocation(), ai.getTailLocation());
                else
                    poly = ai.makeLambdaPoly(ai.getGridBaseWidth(), Poly.Type.CLOSED);
            } else if (o instanceof PortInst) {
                PortInst pi = (PortInst)o;
                NodeInst ni = pi.getNodeInst();
                poly = Highlight.getNodeInstOutline(ni);
                color = colorN;
            } else if (o instanceof Export) {
                Export ep = (Export)o;
                PortInst pi = ep.getOriginalPort();
                NodeInst ni = pi.getNodeInst();
                poly = ni.getShapeOfPort(pi.getPortProto());
                color = Color.YELLOW;
            }
            else if (o instanceof NodeInst)
            {
                color = colorN;
            }
            poly.transform(trans);
            highlights.add(new HighlightPoly(cell, poly, color));
        }
    }
}
