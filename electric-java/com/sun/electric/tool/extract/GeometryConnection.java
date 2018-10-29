/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: GeometryConnection.java
 *
 * Copyright (c) 2005, Static Free Software. All rights reserved.
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

package com.sun.electric.tool.extract;

import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.HierarchyEnumerator;
import com.sun.electric.database.hierarchy.Nodable;
import com.sun.electric.database.hierarchy.Export;
import com.sun.electric.database.variable.VarContext;
import com.sun.electric.database.network.NetworkTool;
import com.sun.electric.database.network.Network;
import com.sun.electric.database.network.Netlist;
import com.sun.electric.database.topology.Geometric;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.prototype.PortProto;
import com.sun.electric.util.math.DBMath;
import com.sun.electric.util.math.FixpTransform;

import java.awt.geom.Rectangle2D;
import java.util.*;

/**
 * Class to describe topology.
 */
public class GeometryConnection
{

    // Private construction for now
    private GeometryConnection() {}

    // Version to cover old Steve's request for NodeExtraction
    public static boolean checkCellConnectivity(NodeInst cellA, NodeInst cellB)
    {
        Rectangle2D cellBounds = (Rectangle2D)cellA.getBounds().clone();
        FixpTransform rTransI = cellB.rotateIn(cellB.translateIn());
        FixpTransform cellUp = cellB.transformOut();
        DBMath.transformRect(cellBounds, rTransI);
        Netlist topNetlist = cellA.getParent().getNetlist();

        if (cellB.isCellInstance() && cellA.isCellInstance())
        {
            Cell cellBProto = (Cell)cellB.getProto();
            FixpTransform subTrans = cellA.rotateIn(cellA.translateIn());

            // Search in other cell possible neighbors
            for(Iterator<Geometric> it = cellBProto.searchIterator(cellBounds); it.hasNext(); )
            {
            	Geometric nGeom = it.next();

                if (nGeom instanceof NodeInst)
                {
                Rectangle2D rect = (Rectangle2D)nGeom.getBounds().clone();
                DBMath.transformRect(rect, cellUp);
                DBMath.transformRect(rect, subTrans);
                ConnectionEnumerator check = new ConnectionEnumerator(cellA, nGeom, rect,
                        cellBProto.getNetlist(), topNetlist);
                HierarchyEnumerator.enumerateCell(cellA.getParent(), VarContext.globalContext, check);
//                HierarchyEnumerator.enumerateCell(cellA.getParent(), VarContext.globalContext,
//                        NetworkTool.getNetlist(cellA.getParent()),
//                        check);
                if (check.found) return true;
                }
            }
        }
       return false;
    }

    public static boolean searchInExportNetwork(Netlist netlist1, Network net1,
                                                Netlist netlist2, Network net2)
    {
        for (Iterator<Export> it = net1.getExports(); it.hasNext();)
        {
            Export exp1 = it.next();
            Network tmpNet1 = netlist1.getNetwork(exp1.getOriginalPort());
            for (Iterator<Export> otherIt = net2.getExports(); otherIt.hasNext();)
            {
                Export exp2 = otherIt.next();
                Network tmpNet2 = netlist2.getNetwork(exp2.getOriginalPort());
                if (tmpNet2 == tmpNet1) return true;
            }
        }
        return false;
    }

    /**************************************************************************************************************
	 *  ConnectionEnumerator class
	 **************************************************************************************************************/
	// Extra functions to check area
	private static class ConnectionEnumerator extends HierarchyEnumerator.Visitor
    {
        private Geometric geomA;
        private Geometric geomB;
        private Rectangle2D geomBBnd;
        public boolean found; // To determine if connection without port was found
        private Set netsB;
        private Netlist topNetlist;
        private Cell topCell;

        public ConnectionEnumerator(Geometric geomA, Geometric geomB, Rectangle2D cellABnds,
                                    Netlist netlistB, Netlist topNetlist)
        {
            this.geomA = geomA;
            this.geomB = geomB;
            this.geomBBnd = cellABnds;
            this.found = false;
            this.netsB = NetworkTool.getNetworks(geomB, netlistB, null);
            this.topNetlist = topNetlist;
            this.topCell = geomA.getParent();
        }

        /**
		 */
		public boolean enterCell(HierarchyEnumerator.CellInfo info)
        {
            Cell cell = info.getCell();

            if (cell == geomA.getParent()) return true; // skip first one.

            Set<Network> nets = null;

            for(Iterator<Geometric> it = cell.searchIterator(geomBBnd); it.hasNext(); )
            {
                Geometric nGeom = it.next();
                System.out.println(nGeom.toString());

                // Only valid for arc-nodeinst pair
                if (nGeom.isConnected(geomB))
                {
                    found = true;
                    return false;
                }
                // Checking if they already belong to same net
                nets = NetworkTool.getNetworks(nGeom, info.getNetlist(), nets);
                if (nets.containsAll(netsB))
                         System.out.println("Found net");
                else if (nets.size() == 1 && netsB.size() == 1)
                {
                    for (Iterator<PortProto> pIt = topCell.getPorts(); pIt.hasNext();)
                    {
                        PortProto port = pIt.next();
                        System.out.println("Port " + port);
                    }
                    if (searchInExportNetwork(topNetlist,(Network)netsB.toArray()[0],
                              topNetlist, (Network)nets.toArray()[0]))
//                    if (NetworkTool.searchNetworkInParent((Network)netsB.toArray()[0], info,
//                            (Network)nets.toArray()[0]))
                         System.out.println("Found net paretn");
                }
//                for (int i = 0; i < nets.size(); i++)
//                {
//                    // binarySearch doesn't work with only 1 element?
//                    //if (Collections.binarySearch(netsB, nets.get(i)) > -1)
//                    for (int j = 0; j < netsB.size(); j++)
//                    {
//                        nets.
//                        if (nets.get(i) == netsB.get(j))
//                         System.out.println("Found net");
//                    }
//                }
            }

            return false;
        }

        /**
		 */
		public void exitCell(HierarchyEnumerator.CellInfo info) {}

        /**
		 */
		public boolean visitNodeInst(Nodable no, HierarchyEnumerator.CellInfo info)
        {
            return true;
        }
    }
}
