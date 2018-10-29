/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: SubcircuitInfo.java
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
package com.sun.electric.tool.ncc.processing;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.sun.electric.tool.ncc.basic.Primes;
import com.sun.electric.tool.ncc.netlist.PinType;
import com.sun.electric.tool.ncc.netlist.Port;
import com.sun.electric.tool.ncc.netlist.Subcircuit;
import com.sun.electric.tool.Job;

/** Holds information necessary to treat this Cell as a primitive subcircuit
 *  when performing hierarchical netlist comparison at a higher level */
public class SubcircuitInfo {
	/** Characteristics shared by all equivalent subcircuits.  A single
	 * instance of SubcircuitInfo is generated for the first Cell in the
	 * CellGroup (the "reference" Cell) and copied by all the other
	 * Cells in the CellGroup. */
	private static class ReferenceInfo {
		final String name;
		final int ID;
		final String[] portNames;
		final int[] portCoeffs;
		final PinType[] pinTypes;
		
		private int[] initPortCoeffs() {
			int[] coeffs = new int[portNames.length];
			int nameHash = 0;
			for (int i=0; i<name.length(); i++) {
				nameHash = (nameHash<<1) + (int) name.charAt(i);
			}
			nameHash = nameHash % 257;
			nameHash = Math.abs(nameHash);

			for (int i=0; i<coeffs.length; i++) {
				coeffs[i] = Primes.get(nameHash + i);
			}
			return coeffs;
		}

		ReferenceInfo(String name, int ID, String[] portNames) {
			Job.error(name==null, "No name?");
			this.name = name;
			this.ID = ID;
			this.portNames = portNames;
			this.portCoeffs = initPortCoeffs();

			pinTypes= new PinType[portNames.length];
			for (int i=0; i<pinTypes.length; i++) {
				String pinDesc = name+" "+portNames[i];
				// The first argment isn't used by new partition algorithm
				pinTypes[i] = new Subcircuit.SubcircuitPinType(0, i,pinDesc);
			}
		}
	}
	/** Arbitrarily choose one Export name from each Port. These names
	 * will be used to report errors when comparing hierarchically at the
	 * next level up.
	 * <p> Build a map from export names to port index */ 
	private void processReferencePorts(String[] portNames, 
	                                   Map<String,Integer> exportNameToPortIndex, 
	                                   Port[] refPorts) {
		for (int i=0; i<refPorts.length; i++) {
			Integer portIndex = new Integer(i);
			Port port = refPorts[i];
			for (Iterator<String> it=port.getExportNames(); it.hasNext();) {
				String exportName = it.next();
				if (portNames[i]==null) portNames[i] = exportName;
				exportNameToPortIndex.put(exportName, portIndex);
			}
			Job.error(portNames[i]==null, "Port with no name?");
		}
	}
	
	// -------------------------- private data -------------------------
	private final ReferenceInfo shared;
	private final Map<String,Integer> exportNameToPortIndex;
	
	// -------------------------- public methods -----------------------
	/** Create the first SubcircuitInfo for the reference Cell */
	public SubcircuitInfo(String name, int ID, Port[] refPorts) {
		String[] portNames = new String[refPorts.length];
		exportNameToPortIndex = new HashMap<String,Integer>();
		processReferencePorts(portNames, exportNameToPortIndex, refPorts);
		this.shared = new ReferenceInfo(name, ID, portNames);	                      	
	}
	/** Create SubcircuitInfos for all the rest of the Cells in the
	 * CellGroup */
	public SubcircuitInfo(SubcircuitInfo referenceInfo, 
	                      Map<String,Integer> exportNameToPortIndex) {
		this.shared = referenceInfo.shared;;
		this.exportNameToPortIndex = exportNameToPortIndex;
	}
	/** @return the unique ID assigned to this subcircuit */ 
	public int getID() {return shared.ID;}
	/** @return the name of this subcircuit */
	public String getName() {return shared.name;}
	/** @return the number of subcircuit ports */
	public int numPorts() {return shared.portNames.length;}
	/** @return the name of the ith port */
	public String getPortName(int i) {return shared.portNames[i];}
	/** @return the index of the port named exportName */
	public int getPortIndex(String exportName) {
		Integer I = exportNameToPortIndex.get(exportName);
		//LayoutLib.error(I==null, "Export name not found: "+exportName);
		// If oneNamePerPort then SubcircuitInfo saves only one Export
		// name with Port. If you ask for others then you get -1
		return I==null ? -1 : I.intValue();
	}
	/** @return an array of coefficients, one entry per port */
	public int[] getPortCoeffs() {return shared.portCoeffs;}
	/** @return array of PinTypes, one per Port */
	public PinType[] getPinTypes() {return shared.pinTypes;}
}
