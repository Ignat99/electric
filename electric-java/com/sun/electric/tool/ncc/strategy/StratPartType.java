/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: StratPartType.java
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
package com.sun.electric.tool.ncc.strategy;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import com.sun.electric.tool.ncc.NccGlobals;
import com.sun.electric.tool.ncc.lists.LeafList;
import com.sun.electric.tool.ncc.netlist.NetObject;
import com.sun.electric.tool.ncc.netlist.Part;
import com.sun.electric.tool.ncc.trees.EquivRecord;

/** StratPartType partitions Part equivalence classes
 * based upon the Part's type. */
public class StratPartType extends Strategy {
	private Map<Integer,String> typeCodeToTypeName = new HashMap<Integer,String>();
	private Set<Part> forcedMatchParts;
	
    private StratPartType(Set<Part> forcedMatchParts, NccGlobals globals) {
    	super(globals);
    	this.forcedMatchParts = forcedMatchParts;
    }

	private LeafList doYourJob2() {
        EquivRecord parts = globals.getParts();

		LeafList offspring = doFor(parts);
		setReasons(offspring);
		summary(offspring);
		return offspring;
	}
	
	private void setReasons(LeafList offspring) {
		for (Iterator<EquivRecord> it=offspring.iterator(); it.hasNext();) {
			EquivRecord r = it.next();
			int value = r.getValue();
			String reason = "part type is "+
			                typeCodeToTypeName.get(new Integer(value));
			globals.status2(reason);
			r.setPartitionReason(reason);
		}
	}

    private void summary(LeafList offspring) {
        globals.status2("StratPartType produced " + offspring.size() +
                        " offspring");
        if (offspring.size()!=0) {
			globals.status2(offspring.sizeInfoString());
			globals.status2(offspringStats(offspring));
        }
    }
    
    @Override
    public Integer doFor(NetObject n){
    	error(!(n instanceof Part), "StratPartType expects only Parts");
		Part p = (Part) n;
		// don't repartition EquivRecords that designer explicitly forced
		// to match
		int typeCode = forcedMatchParts.contains(p) ? 0 : (p.typeCode()+1000);
		
		if (typeCode!=0) {
			String typeName = p.typeString();
			String oldTypeName = typeCodeToTypeName.get(typeCode);
			if (oldTypeName!=null) {
				globals.error(typeCode!=0 && !typeName.equals(oldTypeName), 
							  "type code maps to multiple type names");
			} else {
				typeCodeToTypeName.put(typeCode, typeName);
			}
		}
		return typeCode;
    }

	// ------------------------- intended inteface ----------------------------
	public static LeafList doYourJob(Set<Part> forcedMatchParts, NccGlobals globals){
		StratPartType pow = new StratPartType(forcedMatchParts, globals);
		return pow.doYourJob2();
	}
}
