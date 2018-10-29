/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: TransistorSearch.java
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

import com.sun.electric.database.hierarchy.HierarchyEnumerator;
import com.sun.electric.database.hierarchy.Nodable;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.topology.PortInst;
import com.sun.electric.database.variable.VarContext;
import com.sun.electric.database.network.Network;
import com.sun.electric.database.network.Netlist;
import com.sun.electric.database.network.Global;
import com.sun.electric.database.prototype.PortCharacteristic;
import com.sun.electric.tool.Job;
import com.sun.electric.util.ElapseTimer;

import java.util.Iterator;

/**
 * User: Gilda
 * Date: Aug 24, 2006
 */
public class TransistorSearch
{
    public static void countNumberOfTransistors(Cell cell)
    {
        if (!cell.isSchematic())
        {
            System.out.println("Counting number of transistors only valid for Schematics cells");
            return;
        }
        new TransistorSearchJob(cell);
    }

    private static class TransistorSearchJob extends Job
    {
        private Cell cell;

        public TransistorSearchJob(Cell cell)
        {
            super("Searching Transistors in " + cell.getName(), null, Job.Type.SERVER_EXAMINE, null, null, Job.Priority.USER);
            this.cell = cell;
            startJob();
        }

        public boolean doIt()
        {
        	ElapseTimer timer = ElapseTimer.createInstance().start();

            TransistorSearchEnumerator visitor = new TransistorSearchEnumerator();
            HierarchyEnumerator.enumerateCell(cell, VarContext.globalContext, visitor);
            timer.end();
            System.out.println("Number of transistors found from cell " + cell.getName() + ": " + visitor.transistorTotalNumber);
//            System.out.println("Number of non-cap transistors found from cell (old" + cell.getName() + ": " + visitor.transistorRealNumber);  
            System.out.println("Number of non-cap transistors found from cell " + cell.getName() + ": " + visitor.transistorRealNumber2);            
            System.out.println("(took " + timer + ")");

            return true;
        }
    }

    /**************************************************************************************************************
     *  TransistorSearchEnumerator class
     **************************************************************************************************************/
    private static class TransistorSearchEnumerator extends HierarchyEnumerator.Visitor
    {
        private int transistorTotalNumber;
        private int transistorRealNumber; // doesn't include cap transistors where drain/source ports are connected
        private int transistorRealNumber2; // doesn't include cap transistors where drain/source ports are connected - other consideration

        public TransistorSearchEnumerator() {}

        public boolean enterCell(HierarchyEnumerator.CellInfo info)
        {
            return true;
        }
        public void exitCell(HierarchyEnumerator.CellInfo info) {}
        public boolean visitNodeInst(Nodable no, HierarchyEnumerator.CellInfo info)
        {
            NodeInst ni = no.getNodeInst();
            if (ni.isCellInstance()) return true;
            if (ni.getProto().getFunction().isTransistor())
            {
                Netlist netlist = info.getNetlist();
                HierarchyEnumerator.CellInfo parentInfo = info.getParentInfo() != null ? info.getParentInfo() : info;
                Global.Set globals = parentInfo.getNetlist().getGlobals();
                // checking the ports
                boolean found = false; // no cap transistor (2 gnd or 2 vdd)
                boolean found1 = false;
                int netID = -1;
                // Bypass capacitors: gate=vdd, substrate=gnd.
                for (Iterator<PortInst> itPi = ni.getPortInsts(); itPi.hasNext();)
                {
                    PortInst pi = itPi.next();
                    // Only checking active ports: source and drain
                    if (pi.getPortProto().getCharacteristic() != PortCharacteristic.BIDIR)
                        continue;
                    Network net = netlist.getNetwork(pi);
                    int key = info.getNetID(net);
                    if (netID == -1)
                        netID = key;
                    else if (key == netID)  // same network
                    {
                    	found1 = true;
                        for (int j = 0; j < globals.size(); j++)
                        {
                            Global g = globals.get(j);
                            Network gnet = parentInfo.getNetlist().getNetwork(g);
                            int gnetID = parentInfo.getNetID(gnet);
                            if (gnetID == netID)
                            {
                                found = true;
                                break; // not checking if they are ground or vdd
                            }
                        }
                        if (found)
                            break;
                    }
                }
                // Only counting when it is not a cap transistor.
                transistorTotalNumber++;
                if (!found1)
                	transistorRealNumber2++;
                if (!found)
                    transistorRealNumber++;
            }
            return true;
        }
    }
}
