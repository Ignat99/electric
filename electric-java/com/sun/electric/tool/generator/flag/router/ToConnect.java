/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: ToConnect.java
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
package com.sun.electric.tool.generator.flag.router;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import com.sun.electric.database.topology.PortInst;
import com.sun.electric.tool.generator.flag.Utils;

/** A list of PortInsts that need to be connected by the router */
public class ToConnect {
	private List<PortInst> ports = new ArrayList<PortInst>();
	private List<String> exportNames = new ArrayList<String>();
	
	public ToConnect() { }
	public ToConnect(List<String> expNms) {
		for (String expNm : expNms)  exportNames.add(expNm);
	}
	public void addPortInst(PortInst pi) {ports.add(pi);}
	public List<PortInst> getPortInsts() {return ports;}
	public int numPortInsts() {return ports.size();}
	public boolean isExported() {return exportNames.size()!=0;}
	public Collection<String> getExportName() {return exportNames;}
	
	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append("ToConnect: ");
		if (isExported()) {
			sb.append("Exports:");
			for (String expNm : exportNames) sb.append(" "+expNm);
			sb.append(", ");
		}
		sb.append("Ports: ");
		for (PortInst pi : ports) {
			sb.append(pi.toString()+" ");
		}
		return sb.toString();
	}
	public boolean isPowerOrGround() {
		for (PortInst pi : getPortInsts()) {
			if (Utils.isPwrGnd(pi)) return true;
		}
		return false;
	}

}
