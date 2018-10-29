/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Wire.java
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import com.sun.electric.database.prototype.PortCharacteristic;
import com.sun.electric.tool.ncc.netlist.NccNameProxy.WireNameProxy;
import com.sun.electric.tool.ncc.result.WireReport.WireReportable;
import com.sun.electric.tool.ncc.trees.Circuit;
/** A Wire represents an electrical equipotential.
 */
public class Wire extends NetObject implements WireReportable {
	private static final ArrayList<Part> DELETED = null;

	// ---------- private data -------------
    private ArrayList<Part> parts = new ArrayList<Part>();
	private Port port;  	  // usually null because most Wires have no Port
	private WireNameProxy nameProxy;
	
    // ---------- public methods ----------

	public Wire(WireNameProxy name){nameProxy = name;}
	@Override
	public String getName() {return nameProxy.getName();}
	public WireNameProxy getNameProxy() {return nameProxy;}
	public Iterator<Part> getParts() {return parts.iterator();}
	@Override
	public Iterator getConnected() {return getParts();}

    /** add a Part to this Wire
	 * @param p the Part to add */
    public void add(Part p){
    	error(p==null, "Wires can't add null Part");
		parts.add(p);
    }
    
	/** add a Port to this Wire
	 * @param portName the Port to add
	 */
    public Port addExport(String portName, PortCharacteristic type, boolean oneNamePerPort) {
		if (port==null)  port = new Port(portName, type, this);
		else port.addExport(portName, type, oneNamePerPort); 
		return port;
    }
    
    /** Remove deleted Parts. Remove duplicate Parts. Minimize storage use. */
    public void putInFinalForm() {
    	Set<Part> goodParts = new HashSet<Part>();
    	for (Iterator<Part> it=getParts(); it.hasNext();) {
    		Part p = it.next();
    		if (!p.isDeleted())  goodParts.add(p);
    	}
    	parts = new ArrayList<Part>();
    	parts.addAll(goodParts);
    	parts.trimToSize();
    }
	
	/** @return the Port on this Wire. Return null if wire has no Export 
	 * attached */
	public Port getPort() {return port;}
	@Override
    public Type getNetObjType() {return Type.WIRE;}

	/** Mark this wire deleted and release all storage */
	public void setDeleted() {parts=DELETED;}
	@Override
	public boolean isDeleted() {return parts==DELETED;}

    /** check that this Wire is properly structured.  check each
	 * connection to see if it points back
	 * @param parent the wire's parent */
	@Override
    public void checkMe(Circuit parent){
    	error(getParent()!=parent, "wrong parent");
        for (Iterator<Part> it=getParts(); it.hasNext();) {
            NetObject nn=it.next();
            error(!(nn instanceof Part), "expecting only parts");
            Part pp=(Part)nn;
            error(pp.numPinsConnected(this)==0, 
                  "Part not connected back to wire"); 
        }
    }

    /** 
	 * Does this Wire connect to the given Part?
	 * @param p the Part to test
	 * @return true if it touches, false if not
	 */
    public boolean touches(Part p){return parts.contains(p);}
    /** Does this Wire connect to the given Port?
     * @param p the Port in question
     * @return true if Wire connects to Port p
     */
    public boolean touches(Port p) {return port==p;}
    public Integer computeHashCode(){
        int sum= 0;
        for (Iterator<Part> it=getParts(); it.hasNext();) {
            Part pp= it.next();
            sum += pp.getHashFor(this);
        }
        return new Integer(sum);
    }

    /** count the number of Parts connected to this wire.
	 * @return an int with the number of connections */
    public int numParts(){return parts.size();}
    
    /** @return a String describing Cell containing wire and instance path */
    @Override
    public String instanceDescription() {
    	// Don't print "Cell instance:" in root Cell where there is no path.
    	String inst = nameProxy.cellInstPath();
    	String instMsg = inst.equals("") ? "" : (" Cell instance: "+inst); 
    	return "Wire: "+nameProxy.leafName()+" in Cell: "+
		       nameProxy.leafCell().libDescribe()+instMsg;
    }
    @Override
    public String valueDescription() {return "";}

    /** Get a String indicating up to N connections for this NetObject.
	 * @param maxParts the maximum number of connections to list
	 * @return a String of connections. */
    @Override
    public String connectionDescription(int maxParts){
        if (parts.size()==0) return (" unconnected");
        String s = " connected to";
		if (numParts()>maxParts)  s+=" "+parts.size() + " parts starting with";
        s += ": ";
        
		int i=0;
        for (Iterator<Part> it=getParts(); it.hasNext() && i<maxParts; i++){
            Part p = it.next();
            String cc = p.instanceDescription();
            s += " (" + cc + " Port: " + p.connectionDescription(this)+") ";
        }
        return s;
    }
}
