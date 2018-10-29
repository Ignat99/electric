/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: PrimitiveNameToFunction.java
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

import com.sun.electric.technology.PrimitiveNode;
import com.sun.electric.technology.PrimitiveNode.Function;
import com.sun.electric.technology.Technology;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/** Translate the names found in "transistorType" and "resistorType" NCC 
 * declarations into the appropriate PrimitiveNode.Function enum.  This 
 * is for backwards compatibility only. In the Future, transistors and resistors 
 * should have the correct Function. */
public class PrimitiveNameToFunction {
	private static PrimitiveNameToFunction nmToF = null;
	private static Set<Technology> techList = new HashSet<Technology>();
	
	private Map<String, Function> nameToEnum = null;
	
    private void add(Function f, String nm) {nameToEnum.put(nm, f);}
    
    private PrimitiveNameToFunction(Technology tech) 
    {
    	// Look for the primitive names in the technology based on their function
    	// rather than hard coding the name here.
    	nameToEnum = new HashMap<String, Function>();
    	
    	addFunctions(tech);

    	// add all function names
    	for(PrimitiveNode.Function f : PrimitiveNode.Function.getFunctions())
    	{
    		if (f.isTransistor() || f.isResistor())
    			nameToEnum.put(f.getName(), f);
    	}
    }
    
    private void addFunctions(Technology tech)
    {
    	for (Iterator<PrimitiveNode> it = tech.getNodes(); it.hasNext(); )
    	{
    		PrimitiveNode pn = it.next();
    		Function f = pn.getFunction();
    		if (f.isTransistor() || f.isResistor())
    			add(f, pn.getName());
    	}
    }
    
    private Function nameToFunc(String nm) {return nameToEnum.get(nm);}
    
    public static void prepareToFunctionData(Technology tech)
    {
    	if (techList.contains(tech)) return; // added already
    	
    	if (nmToF == null)
    		nmToF = new PrimitiveNameToFunction(tech);
    	else if (tech.isLayout())
    		nmToF.addFunctions(tech); // just add any layout technology
    }
    public static Function nameToFunction(String nm) 
    {
    	return nmToF.nameToFunc(nm);
    }
	
}
