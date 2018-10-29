/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: NccNameProxy.java
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
package com.sun.electric.tool.ncc.netlist;

import java.io.Serializable;
import java.util.Iterator;

import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.HierarchyEnumerator.NameProxy;
import com.sun.electric.database.hierarchy.HierarchyEnumerator.NetNameProxy;
import com.sun.electric.database.hierarchy.HierarchyEnumerator.NodableNameProxy;
import com.sun.electric.database.network.Network;
import com.sun.electric.database.variable.VarContext;
import com.sun.electric.tool.Job;

/** Stores the information necessary to generate an instance name for a Part 
  * It is the same as HierarchyEnumerator.NameProxy except that it removes
  * a common path prefix from the name. */
public abstract class NccNameProxy implements Serializable {
	private String commonPathPrefix;
	
	public static String removePrefix(String commonPathPrefix,
			                          String fullName) {
		if (commonPathPrefix.length()==0) return fullName;
		Job.error(!fullName.startsWith(commonPathPrefix),
                        "common path prefix not found");
		// +1 to skip separator
		int start = commonPathPrefix.length()+1;
		if (start>fullName.length()-1) {
			// nothing but the prefix
			return "";
		} else {
			return fullName.substring(start);		
		}
	}

	/** It was a mistake to use toString(). The use of toString() makes
	 * code unmodifiable. Let me begin the process of purging all uses. */
	public String toString() {
		Job.error(true, "Please tell Electric team about this use of toString");
		return null;
	}
	abstract NameProxy nameProxy();
	NccNameProxy(String commonPathPrefix) {
		this.commonPathPrefix = commonPathPrefix;
	}
	/** @return true if this Part or Wire is in the root Cell */
	public boolean nameIsInTopContext() {
		return nameProxy().getContext()==VarContext.globalContext;
	}
	/** @return the Cell containing this Part or Wire */
	public Cell leafCell() {return nameProxy().leafCell();}
	/** @return the name of this Part or Wire in the leaf Cell */
	public String leafName() {return nameProxy().leafName();}
	/** @return the VarContext of this Part or Wire */
    public VarContext getContext() { return nameProxy().getContext(); }    
	/** Name whose instance path starts from the Cell from which NCC was run */
	public String getName() {
		return removePrefix(commonPathPrefix, nameProxy().toString());
	}
	/** Cell instance path starting from the Cell from which NCC was run. */
	public String cellInstPath() {
		return removePrefix(commonPathPrefix, nameProxy().getContext().getInstPath("/"));
	}

	public static class PartNameProxy extends NccNameProxy {
    	static final long serialVersionUID = 0;
    	
		private NodableNameProxy nameProxy;
		NameProxy nameProxy() {return nameProxy;}
		public PartNameProxy(NodableNameProxy nameProxy, 
				             String commonPathPrefix) {
			super(commonPathPrefix);
			this.nameProxy = nameProxy;
		}
		public NodableNameProxy getNodableNameProxy() {return nameProxy;}
	}
	public static class WireNameProxy extends NccNameProxy {
    	static final long serialVersionUID = 0;
    	
		NetNameProxy nameProxy;
		NameProxy nameProxy() {return nameProxy;}
		public WireNameProxy(NetNameProxy nameProxy, String commonPathPrefix) {
			super(commonPathPrefix);
			this.nameProxy = nameProxy;
		}
		public NetNameProxy getNetNameProxy() {return nameProxy;}
		public Iterator getNetNames() {
			return ((NetNameProxy)nameProxy).leafNames();
		}
        public Network getNet() {
            return nameProxy.getNet();
        }
        
	}
}
