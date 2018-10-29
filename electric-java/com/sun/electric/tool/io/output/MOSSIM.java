/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: MOSSIM.java
 * Input/output tool: MOSSIM net list generator
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
import com.sun.electric.database.hierarchy.Nodable;
import com.sun.electric.database.network.Global;
import com.sun.electric.database.network.Netlist;
import com.sun.electric.database.network.Network;
import com.sun.electric.database.prototype.PortCharacteristic;
import com.sun.electric.database.prototype.PortProto;
import com.sun.electric.database.topology.ArcInst;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.topology.PortInst;
import com.sun.electric.database.variable.VarContext;
import com.sun.electric.database.variable.Variable;
import com.sun.electric.technology.PrimitiveNode;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * This is the netlister for MOSSIM.
 *
 * A circuit can be augmented in a number of ways for MOSSIM output:
 * The variable "SIM_mossim_strength" may be placed on any node or arc.
 * On transistor nodes, it specifies the strength field to use for that
 * transistor declaration.  On arcs, it specifies the strength field to
 * use for that network node (only works for internal nodes of a cell).
 */
public class MOSSIM extends Topology
{
	/** key of Variable holding node or arc strength. */	public static final Variable.Key MOSSIM_STRENGTH_KEY = Variable.newKey("SIM_mossim_strength");

//	private MOSSIMPreferences localPrefs;

	public static class MOSSIMPreferences extends OutputPreferences
    {
        public MOSSIMPreferences(boolean factory) { super(factory); }

        public Output doOutput(Cell cell, VarContext context, String filePath)
        {
    		MOSSIM out = new MOSSIM(this);
    		if (out.openTextOutputStream(filePath)) return out.finishWrite();
    		if (out.writeCell(cell, context)) return out.finishWrite();
    		if (out.closeTextOutputStream()) return out.finishWrite();
    		System.out.println(filePath + " written");
            return out.finishWrite();
        }
    }

	/**
	 * Creates a new instance of the MOSSIM netlister.
	 */
	MOSSIM(MOSSIMPreferences mp) { /* localPrefs = mp; */ }

	protected void start() {}

	protected void done() {}

	/**
	 * Method to write cellGeom
	 */
	protected void writeCellTopology(Cell cell, String cellName, CellNetInfo cni, VarContext context, Topology.MyCellInfo info)
	{
		if (cell == topCell)
		{
			// declare power and ground nodes if this is top cell
			printWriter.println("| Top-level cell " + cell.describe(false) + " ;");
			printWriter.println("i VDD ;");
			printWriter.println("i GND ;");
		} else
		{
			// write ports if this cell is sub-cell
			printWriter.println("| Cell " + cell.describe(false) + " ;");
			printWriter.print("c " + cell.getName());
			for(Iterator<CellSignal> it = cni.getCellSignals(); it.hasNext(); )
			{
				CellSignal cs = it.next();
				if (cs.isExported()) printWriter.print(" " + cs.getName());
			}
			printWriter.println(" ;");
		}

		// gather strength information
		Netlist netList = cni.getNetList();
		Map<Network,String> strengthMap = new HashMap<Network,String>();
		for(Iterator<ArcInst> it = cell.getArcs(); it.hasNext(); )
		{
			ArcInst ai = it.next();
			Variable var = ai.getVar(MOSSIM_STRENGTH_KEY);
			if (var == null) continue;
			Network net = netList.getNetwork(ai, 0);
			strengthMap.put(net, var.getPureValue(-1));
		}

		// mark all ports that are equivalent
		for(Iterator<CellSignal> it = cni.getCellSignals(); it.hasNext(); )
		{
			CellSignal cs = it.next();
			if (cs.isPower())
			{
				if (!cs.getName().equalsIgnoreCase("vdd"))
					printWriter.println("e VDD " + cs.getName() + " ;");
				continue;
			}
			if (cs.isGround())
			{
				if (!cs.getName().equalsIgnoreCase("gnd"))
					printWriter.println("e GND " + cs.getName() + " ;");
				continue;
			}
			if (cs.isExported())
			{
				if (cell == topCell)
				{
					Export e = cs.getExport();
					if (e.getCharacteristic() == PortCharacteristic.IN)
					{
						printWriter.println("i " + cs.getName() + " ;");
					} else
					{
						printWriter.println("s 1 " + cs.getName() + " ;");
					}
				}
			} else
			{
				String strength = strengthMap.get(cs.getNetwork());
				if (strength == null) strength = "1";
				printWriter.println("s " + strength + " " + cs.getName());
			}
		}

		// now write the transistors
		for(Iterator<Nodable> nIt = netList.getNodables(); nIt.hasNext(); )
		{
			Nodable no = nIt.next();
			if (no.isCellInstance())
			{
				// complex node: make instance call
				String nodeName = parameterizedName(no, context);
				StringBuffer infstr = new StringBuffer();
				infstr.append("h " + nodeName + " " + no.getName());

				CellNetInfo subCni = getCellNetInfo(nodeName);
				for(Iterator<CellSignal> sIt = subCni.getCellSignals(); sIt.hasNext(); )
				{
					CellSignal subCs = sIt.next();
					if (!subCs.isExported()) continue;
					PortProto pp = subCs.getExport();
					Network net = netList.getNetwork(no, pp, subCs.getExportIndex());
					CellSignal cs = cni.getCellSignal(net);
					infstr.append(" " + cs.getName());
				}
				infstr.append(" ;");
				printWriter.println(infstr.toString());
			} else
			{
				// handle primitives
				NodeInst ni = (NodeInst)no;
				PrimitiveNode.Function type = ni.getFunction();

				// if it is a transistor, write the information
				if (!type.isFET()) continue;

				// gate is port 0 or 2, source is port 1, drain is port 3
				PortInst gate = ni.getTransistorGatePort();
				PortInst source = ni.getTransistorSourcePort();
				PortInst drain = ni.getTransistorDrainPort();

				// write the transistor
				StringBuffer infstr = new StringBuffer();
				if (type.isNTypeTransistor()) infstr.append("n"); else
					if (type.isPTypeTransistor()) infstr.append("p"); else
						infstr.append("d");

				// write the strength of the transistor
				Variable var = ni.getVar(MOSSIM_STRENGTH_KEY);
				if (var != null)
				{
					infstr.append(" " + var.getPureValue(-1));
				} else
				{
					infstr.append(" 2");
				}

				// write the gate/source/drain nodes
				CellSignal cs = cni.getCellSignal(netList.getNetwork(gate));
				if (cs == null) reportError(ni.getParent() + " CANNOT DETERMINE GATE NETWORK ON " + ni);
				infstr.append(" " + cs.getName());
				cs = cni.getCellSignal(netList.getNetwork(source));
				if (cs == null) reportError(ni.getParent() + " CANNOT DETERMINE SOURCE NETWORK ON " + ni);
				infstr.append(" " + cs.getName());
				cs = cni.getCellSignal(netList.getNetwork(drain));
				if (cs == null) reportError(ni.getParent() + " CANNOT DETERMINE DRAIN NETWORK ON " + ni);
				infstr.append(" " + cs.getName());
				infstr.append(" ;   | " + ni.getName() + ";");
				printWriter.println(infstr.toString());
			}
		}

		// finish up
		printWriter.println(".");
	}

	/****************************** SUBCLASSED METHODS FOR THE TOPOLOGY ANALYZER ******************************/

	/**
	 * Method to adjust a cell name to be safe for MOSSIM output.
	 * @param name the cell name.
	 * @return the name, adjusted for MOSSIM output.
	 */
	protected String getSafeCellName(String name) { return name; }

	/** Method to return the proper name of Power */
	protected String getPowerName(Network net) { return "VDD"; }

	/** Method to return the proper name of Ground */
	protected String getGroundName(Network net) { return "GND"; }

	/** Method to return the proper name of a Global signal */
	protected String getGlobalName(Global glob) { return glob.getName(); }

	/** Method to report that export names DO take precedence over
	 * arc names when determining the name of the network. */
	protected boolean isNetworksUseExportedNames() { return true; }

	/** Method to report that library names ARE always prepended to cell names. */
	protected boolean isLibraryNameAlwaysAddedToCellName() { return false; }

	/** Method to report that aggregate names (busses) ARE used. */
	protected boolean isAggregateNamesSupported() { return false; }

	/** Abstract method to decide whether aggregate names (busses) can have gaps in their ranges. */
	protected boolean isAggregateNameGapsSupported() { return false; }

	/** Method to report whether input and output names are separated. */
	protected boolean isSeparateInputAndOutput() { return true; }

	/** Abstract method to decide whether netlister is case-sensitive (Verilog) or not (Spice). */
	protected boolean isCaseSensitive() { return true; }

	/**
	 * Method to adjust a network name to be safe for MOSSIM output.
	 */
	protected String getSafeNetName(String name, boolean bus) { return name; }

    /** Tell the Hierarchy enumerator how to short resistors */
    @Override
    protected Netlist.ShortResistors getShortResistors() { return Netlist.ShortResistors.ALL; }

	/**
	 * Method to tell whether the topological analysis should mangle cell names that are parameterized.
	 */
	protected boolean canParameterizeNames() { return true; }
}
