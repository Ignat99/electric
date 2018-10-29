/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Eagle.java
 * Input/output tool: Eagle netlist output
 * Written by Steven M. Rubin.
 *
 * Copyright (c) 2004, Static Free Software. All rights reserved.
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
package com.sun.electric.tool.io.output;

import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.Export;
import com.sun.electric.database.hierarchy.HierarchyEnumerator;
import com.sun.electric.database.hierarchy.Nodable;
import com.sun.electric.database.hierarchy.View;
import com.sun.electric.database.network.Netlist;
import com.sun.electric.database.prototype.PortProto;
import com.sun.electric.database.text.Name;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.topology.PortInst;
import com.sun.electric.database.variable.VarContext;
import com.sun.electric.database.variable.Variable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Class to write Eagle netlists.
 * <BR>
 * Format:<BR>
 * ADD SO14 'IC1' R0 (0 0)<BR>
 * ADD SO16 'IC2' R0 (0 0)<BR>
 * ADD SO24L 'IC3' R0 (0 0)<BR>
 * ;<BR>
 * Signal 'A0'       'IC1'      '5' \<BR>
 *                   'IC2'      '10' \<BR>
 *                   'IC3'      '8' \<BR>
 * ;<BR>
 * Signal 'A1'       'IC1'      '6' \<BR>
 *                   'IC2'      '11' \<BR>
 *                   'IC3'      '5' \<BR>
 * ;
 */
public class Eagle extends Output
{
	/** key of Variable holding node name. */				public static final Variable.Key REF_DES_KEY = Variable.newKey("ATTR_ref_des");
	/** key of Variable holding package type. */			public static final Variable.Key PKG_TYPE_KEY = Variable.newKey("ATTR_pkg_type");
	/** key of Variable holding pin information. */			public static final Variable.Key PIN_KEY = Variable.newKey("ATTR_pin");

	private List<NetNames> networks;

	public static class EaglePreferences extends OutputPreferences
    {
        public EaglePreferences(boolean factory) { super(factory); }

        public Output doOutput(Cell cell, VarContext context, String filePath)
        {
    		Eagle out = new Eagle(this);
    		out.writeNetlist(cell, context, filePath);
            return out;
        }
    }

	/**
	 * Creates a new instance of Eagle netlister.
	 */
	private Eagle(EaglePreferences ep) {}

	private void writeNetlist(Cell cell, VarContext context, String filePath)
	{
		if (openTextOutputStream(filePath)) return;

		networks = new ArrayList<NetNames>();
		EagleNetlister netlister = new EagleNetlister();
		HierarchyEnumerator.enumerateCell(cell, context, netlister, Netlist.ShortResistors.ALL);
		printWriter.println(";");

		// warn the user if nets not found
		if (networks.size() == 0)
		{
			reportError("ERROR: no output produced.  Packages need attribute 'ref_des' and ports need attribute 'pin'");
            return;
        }

		// add all network pairs
		Collections.sort(networks, new NetNamesSort());
		int widestNodeName = 0, widestNetName = 0;
		for(NetNames nn : networks)
		{
			int nodeNameLen = nn.nodeName.length();
			if (nodeNameLen > widestNodeName) widestNodeName = nodeNameLen;
			int netNameLen = nn.netName.length();
			if (netNameLen > widestNetName) widestNetName = netNameLen;
		}
		widestNodeName += 4;
		widestNetName += 4;
		for(int i=0; i<networks.size(); i++)
		{
			NetNames nn = networks.get(i);
			String baseName = nn.netName;
			int endPos = i;
			for(int j=i+1; j<networks.size(); j++)
			{
				NetNames oNn = networks.get(j);
				if (!oNn.netName.equals(baseName)) break;
				endPos = j;
			}
			if (endPos == i) continue;
			for(int j=i; j<=endPos; j++)
			{
				NetNames oNn = networks.get(j);
				if (j == i) printWriter.print("Signal "); else
					printWriter.print("       ");
				printWriter.print("'" + oNn.netName + "'");
				for(int k=oNn.netName.length(); k<widestNetName; k++) printWriter.print(" ");
				printWriter.print("'" + oNn.nodeName + "'");
				for(int k=oNn.nodeName.length(); k<widestNodeName; k++) printWriter.print(" ");
				printWriter.println("'" + oNn.portName + "' \\");
			}
			printWriter.println(";");
		}

		if (closeTextOutputStream()) return;
		System.out.println(filePath + " written");
	}

	/** Eagle Netlister */
	private class EagleNetlister extends HierarchyEnumerator.Visitor
	{
		public boolean enterCell(HierarchyEnumerator.CellInfo info) { return true; }

		public void exitCell(HierarchyEnumerator.CellInfo info) {}

		public boolean visitNodeInst(Nodable no, HierarchyEnumerator.CellInfo info)
		{
			if (!no.isCellInstance()) return false;

			// if node doesn't have "ref_des" on it, recurse down the hierarchy
			Variable var = no.getVar(REF_DES_KEY);
			if (var == null) return true;

			// found symbol name: emit it
			String context = "";
			Nodable pNo = info.getParentInst();
			HierarchyEnumerator.CellInfo parentInfo = info.getParentInfo();
			if (parentInfo != null && pNo != null) context = parentInfo.getUniqueNodableName(pNo, ".") + ".";
			String nodeName = context + var.getPureValue(-1);
			String pkgType = no.getProto().getName();
			Variable pkgName = no.getVar(PKG_TYPE_KEY);
			if (pkgName != null) pkgType = pkgName.getPureValue(-1);
			printWriter.println("ADD " + pkgType + " '" + nodeName + "' (0 0)");

			// save all networks on this node for later
			NodeInst ni = no.getNodeInst();
			Cell altProto = null;
			if (ni.isCellInstance() && ((Cell)ni.getProto()).getView() == View.ICON)
				altProto = ((Cell)ni.getProto()).contentsView();
            for (Iterator<PortInst> it = ni.getPortInsts(); it.hasNext(); )
            {
                PortInst pi = it.next();
                PortProto pp = pi.getPortProto();
                Name busName = pp.getNameKey();
                String pName = null;
                Variable pVar = pi.getVar(PIN_KEY);
				if (pVar != null) pName = pVar.getPureValue(-1); else
				{
					if (altProto != null)
					{
						PortProto other = ((Export)pp).findEquivalent(altProto);
						if (other != null) pp = other;
					}
					pVar = ((Export)pp).getVar(PIN_KEY);
					if (pVar != null) pName = pVar.getPureValue(-1);
				}
				if (pName == null) continue;
                for (int busIndex = 0; busIndex < busName.busWidth(); busIndex++)
                {
                    Name wireName = busName.subname(busIndex);
                    int netId = info.getPortNetID(no, wireName);
					NetNames nn = new NetNames();
					nn.netName = info.getUniqueNetName(netId, ".");
					nn.nodeName = nodeName;
					nn.portName = pName;
					networks.add(nn);
                }
            }
			return false;
		}
	}
}
