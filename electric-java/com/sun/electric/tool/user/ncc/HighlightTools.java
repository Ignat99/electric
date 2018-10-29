/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: HighlightTools.java
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
package com.sun.electric.tool.user.ncc;

import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.Export;
import com.sun.electric.database.hierarchy.Nodable;
import com.sun.electric.database.network.Netlist;
import com.sun.electric.database.network.Network;
import com.sun.electric.database.variable.VarContext;
import com.sun.electric.tool.ncc.result.NetObjReport;
import com.sun.electric.tool.ncc.result.PartReport;
import com.sun.electric.tool.ncc.result.PortReport;
import com.sun.electric.tool.ncc.result.WireReport;
import com.sun.electric.tool.user.Highlighter;
import com.sun.electric.tool.user.User;
import com.sun.electric.tool.user.ui.EditWindow;

import java.util.Iterator;

public class HighlightTools {

    public static Highlighter getHighlighter(Cell cell, VarContext context) {
        Highlighter highlighter = null;
        // validate the cell (it may have been deleted)
        if (cell != null) {
            if (!cell.isLinked()) 
        	{
            	System.out.println("Cell is deleted");
            	return highlighter;
        	}

            // make sure it is shown
            EditWindow wnd = EditWindow.getCurrent();
        	if (User.isShowCellsInNewWindow()) wnd = null;
        	if (wnd == null)
        	{
            	wnd = EditWindow.showEditWindowForCell(cell, context);
        	} else
        	{
        		wnd.setCell(cell, context, null);
        	}
            highlighter = wnd.getHighlighter();
            highlighter.clear();
        }
        return highlighter;
    }

    public static void highlightPortExports(Highlighter highlighter, Cell cell, PortReport p) {
        String name = p.getWireName();
        highlightNetworkByName(highlighter, cell, name);
    }

    public static void highlightNetworkByName(Highlighter highlighter, Cell cell, String netName) {
        Netlist netlist = cell.getNetlist();
        if (netlist == null) {
            System.out.println("Sorry, a deadlock aborted mimic-routing (network information unavailable).  Please try again");
            return;
        }

        for (Iterator<Network> it = netlist.getNetworks(); it.hasNext(); ) {
            Network net = it.next();
            if (! net.hasName(netName)) continue;
            highlighter.addNetwork(net, cell);
            // All the following appear to have no affect
//            for (Iterator<Export> it2 = net.getExports(); it2.hasNext(); ) {
//                Export exp = it2.next();
//                //highlighter.addText(exp, cell, null);
//                highlighter.addElectricObject(exp, cell);
//                //highlighter.addObject(exp, cell);
//            }
        }
    }

    public static void highlightPart(Highlighter highlighter, Cell cell, PartReport part) {
        String name = part.getNameProxy().leafName();
        Netlist netlist = cell.getNetlist();
        if (netlist == null) {
            System.out.println("Sorry, a deadlock aborted mimic-routing (network information unavailable).  Please try again");
            return;
        }
        for(Iterator<Nodable> it = netlist.getNodables(); it.hasNext(); ) {
            Nodable nod = it.next();
            if (name.equals(nod.getName()))
                highlighter.addElectricObject(nod.getNodeInst(), cell);
        }
    }

    public static void highlightWire(Highlighter highlighter, Cell cell, WireReport wire) {
        highlightNetNamed(highlighter, cell, wire.getNameProxy().leafName());
    }

    public static void highlightNetNamed(Highlighter highlighter, Cell cell, String name) {
        Netlist netlist = cell.getNetlist();
        if (netlist == null) {
            System.out.println("Sorry, a deadlock aborted mimic-routing (network information unavailable).  Please try again");
            return;
        }
        for(Iterator<Network> it = netlist.getNetworks(); it.hasNext(); ) {
            Network net = it.next();
            if (net.hasName(name)) {
                highlighter.addNetwork(net, cell);
                for (Iterator<Export> it2 = net.getExports(); it2.hasNext(); ) {
                    Export exp = it2.next();
                    highlighter.addText(exp, cell, Export.EXPORT_NAME);
                }
            }
        }
    }

    public static void highlightPortOrWire(Highlighter highlighter, Cell cell, NetObjReport portOrWire) {
        if (portOrWire instanceof WireReport)
            highlightWire(highlighter, cell, (WireReport)portOrWire);
        else if (portOrWire instanceof PortReport)
            // highlight port exports
            highlightPortExports(highlighter, cell, (PortReport)portOrWire);
    }
}
