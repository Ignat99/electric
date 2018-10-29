/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: NccContext.java
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
package com.sun.electric.tool.ncc.result.equivalence;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.HierarchyEnumerator.NameProxy;
import com.sun.electric.database.variable.VarContext;
import com.sun.electric.tool.Job;

/* A data structure that parallels VarContext. A key difference is that 
 * NccContext has pointers from the root to the leaves while VarContext has
 * pointers from the leaves to the root. Also, the NccContext holds the indices
 * of NameProxy's at that point in the design hierarchy. */
class NccContext implements Serializable {
    static final long serialVersionUID = 0;

	private VarContext context;
	/** Parent of all NameProxy's at this point in the design hierarchy */ 
	private Cell cell;
	private Map<String,NccContext> nodableNameToChild = new HashMap<String,NccContext>();
	private Set<Integer> objectIndices = new HashSet<Integer>();
	
	public NccContext(VarContext vc) {context=vc;}
	public void addChild(NccContext child) {
		String name = child.context.getNodable().getName();
		Job.error(nodableNameToChild.containsKey(name),
				        "2 nodables with same name?");
		nodableNameToChild.put(name, child);
	}
	public void addNameProxyIndex(NameProxy np, int i) {
		Integer bi = new Integer(i);
		Job.error(objectIndices.contains(bi),
				        "duplicate index?");
		objectIndices.add(bi);
		// Check invariant: All NameProxy's in the same NccContext are
		// contained by the same parent Cell
		Job.error(cell!=null && cell!=np.leafCell(),
				        "NameProxy's in NccContext don't have same parent");
		cell = np.leafCell();
		Job.error(cell==null, "NameProxy with no parent Cell?");
	}
	public Iterator<Integer> getIndices() {return objectIndices.iterator();}
	public NccContext findChild(String instNm) {
		return nodableNameToChild.get(instNm);
	}
	/** @return the VarContext */
	public VarContext getContext() {return context;}
	/** @return the parent Cell of all NameProxy's at this point in the 
	 * design hierarchy.  If there aren't any NameProxy's here then return
	 * null. */
	public Cell getCell() {return cell;}
}
