/* -*- tab-width: 4 -*-
*
* Electric(tm) VLSI Design System
*
* File: LocalPartitioning.java
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
/**
 * This is the second generation algorithm for local partitioning of Wires.  
 * It's intended to be more efficient. (That is not O(n^2) in number of
 * Port instances.)    
 */
package com.sun.electric.tool.ncc.processing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.sun.electric.tool.ncc.NccGlobals;
import com.sun.electric.tool.ncc.lists.LeafList;
import com.sun.electric.tool.ncc.netlist.NetObject;
import com.sun.electric.tool.ncc.netlist.Part;
import com.sun.electric.tool.ncc.netlist.PinType;
import com.sun.electric.tool.ncc.netlist.Wire;
import com.sun.electric.tool.ncc.strategy.Strategy;
import com.sun.electric.tool.ncc.trees.EquivRecord;
import com.sun.electric.tool.Job;
import com.sun.electric.util.TextUtils;

/**
 * Partition Wires into equivalence classes based only upon
 * local information. Compute a Signature for each wire and then
 * group together Wires with the same Signature.
 */
public class LocalPartitionWires {
	// ------------------------ data ---------------------------------
	private final NccGlobals globals;

	// ------------------------ classes ---------------------------------
	private static class PinTypeCount {
		private PinType pinType;
		private int count;
		@Override
		public boolean equals(Object o) {
			if (!(o instanceof PinTypeCount)) return false;
			PinTypeCount p = (PinTypeCount) o;
			return p.count==count && p.pinType==pinType;
		}
		@Override
		public int hashCode() {
			return pinType.hashCode() * count;
		}
		
		public PinTypeCount(PinType t) {pinType=t;}
		public void increment() {count++;}
		public String description() {
			return count+" "+pinType.description(); 
		}
	}
	
	// Sort PinTypeCounts by PinType name
	private static class PinTypeCompare implements Comparator<PinTypeCount> {
		public int compare(PinTypeCount c1, PinTypeCount c2) {
			String n1 = c1.pinType.description();
			String n2 = c2.pinType.description();
			return TextUtils.STRING_NUMBER_ORDER.compare(n1, n2);
		}
	}
	/** A Wire's Signature is an unordered list of of the pairs {PinType, count}.
	 * For example, 2 Mos diffusions and 1 Mos gate. Signatures are used as
	 * hash keys to quickly partition Wires based upon their Signatures. 
	 */
	public static class Signature {
		private Map<PinType,PinTypeCount> pinTypeToPinTypeCount = new HashMap<PinType,PinTypeCount>();
		// Note: id isn't supposed to be used by equals()
		private Integer id;
		private List<PinTypeCount> getListOfPinTypeCounts() {
			List<PinTypeCount> l = new ArrayList<PinTypeCount>();
			for (PinType t : pinTypeToPinTypeCount.keySet()) {
				PinTypeCount c = pinTypeToPinTypeCount.get(t);
				l.add(c);
			}
			return l;
		}
		@Override
		public int hashCode() {
			int code = 0;
			for (PinType t : pinTypeToPinTypeCount.keySet()) {
				PinTypeCount c = pinTypeToPinTypeCount.get(t);
				code += t.hashCode() + c.hashCode();
			}
			return code; 
		}
		@Override
		public boolean equals(Object o) {
			if (!(o instanceof Signature)) return false;
			Signature s2 = (Signature) o;
			if (pinTypeToPinTypeCount.size()!=s2.pinTypeToPinTypeCount.size())
				return false;
			for (PinType t : pinTypeToPinTypeCount.keySet()) {
				PinTypeCount c = pinTypeToPinTypeCount.get(t);
				PinTypeCount c2 = s2.pinTypeToPinTypeCount.get(t);
				if (!c.equals(c2)) return false;
			}
			return true;
		}
		
		public void increment(PinType t) {
			PinTypeCount c = pinTypeToPinTypeCount.get(t);
			if (c==null) {
				c = new PinTypeCount(t);
				pinTypeToPinTypeCount.put(t, c);
			}
			c.increment();
		}
		public void setID(int id) {
			Job.error(this.id!=null, "assigned a second ID?");
			this.id = new Integer(id);
		}
		public Integer getID() {return id;}
		public List<String> getReasons() {
			List<PinTypeCount> pinTypeCounts = getListOfPinTypeCounts();
			
			Collections.sort(pinTypeCounts, new PinTypeCompare());
			
			List<String> pinTypeDescrs = new ArrayList<String>();

			if (pinTypeCounts.size()==0) {
				pinTypeDescrs.add("disconnected");
			} else {
				for (PinTypeCount t : pinTypeCounts) {
					String descr = t.description();
					pinTypeDescrs.add(descr);
				}
			}
			
			return pinTypeDescrs;
		}
	}
	private static class ComputeSignatures extends Strategy {
		private Map<Wire,Signature> wireToSignature = new HashMap<Wire,Signature>();
		private Signature disconnectedWireSignature = new Signature();

		// Set signatures for Wires with nothing attached 
		private void doFor(Wire w) {
			if (w.numParts()==0) {
				wireToSignature.put(w, disconnectedWireSignature);
			}
		}
		
		// Compute signatures for all Wires attached to PortInsts
		private void doFor(Part p) {
			int pinNdx=0;
			for (Iterator<Wire> itw=p.getConnected(); itw.hasNext(); pinNdx++) {
				Wire w = itw.next();
				PinType t = p.getPinTypeOfNthPin(pinNdx);
				Signature s = wireToSignature.get(w);
				if (s==null) {
					s = new Signature();
					wireToSignature.put(w, s);
				}
				s.increment(t);
			}
		}
		@Override
		public Integer doFor(NetObject no) {
			if (no instanceof Part) {
				doFor((Part)no);
			} else if (no instanceof Wire) {
				doFor((Wire)no);
			}
			return Strategy.CODE_NO_CHANGE;
		}
		
		private ComputeSignatures(NccGlobals globs) {super(globs);}
		
		private Map<Wire,Signature> doYourJob2() {
			EquivRecord root = globals.getRoot();
			
			// don't blow up if no Parts Wires or Ports
			if (root!=null) doFor(root);
			
			return wireToSignature;
		}
		
		// Calculate a signature for each Wire
		public static Map<Wire,Signature> doYourJob(NccGlobals globs) {
			return (new ComputeSignatures(globs)).doYourJob2();
		}
	}
	
	// If two Signatures are != but are .equals() then discard one of them.
	// Assign each remaining Signature a unique Integer ID.
	private void cannonizeSignatures(Map<Wire,Signature> wireToSignature) {
		Map<Signature,Signature> signatures = new HashMap<Signature,Signature>();
		int sigID = 0;
		for (Wire w : wireToSignature.keySet()) {
			Signature s = wireToSignature.get(w);

			Signature cannonicalS = signatures.get(s);
			if (cannonicalS==null) {
				cannonicalS = s;
				signatures.put(cannonicalS, cannonicalS);
				cannonicalS.setID(sigID++);
			} else {
				wireToSignature.put(w, cannonicalS);
			}
		}
	}
	
	private static class StratLocalPartitionWires extends Strategy {
		private Map<Wire,Signature> wireToSignature;
		private Set<Wire> forcedMatchWires;

		private StratLocalPartitionWires(Map<Wire,Signature> wireToSignature,
				                    Set<Wire> forcedMatchWires, NccGlobals globals) {
			super(globals);
			this.wireToSignature = wireToSignature;
			this.forcedMatchWires = forcedMatchWires;
		}
		
		@Override
		public Integer doFor(NetObject o) {
			Wire w = (Wire) o;
			if (forcedMatchWires.contains(w)) {
				// don't repartition Wires that were forced to match
				return 0;
			} else {
				Signature s = wireToSignature.get(w);
				// add 1000 to avoid collision with 0 which means "don't partition"
				return 1000 + s.getID();
			}
		}
	    private void summary(LeafList offspring) {
	        globals.status2("StratLocalPartitionWires produced " + offspring.size() +
	                        " offspring");
	        if (offspring.size()!=0) {
				globals.status2(offspring.sizeInfoString());
				globals.status2(offspringStats(offspring));
	        }
	    }
	    private void doYourJob2(EquivRecord wireRec) {
	    	LeafList offspring = doFor(wireRec);
	    	summary(offspring);
	    }
		
		public static void doYourJob(Map<Wire,Signature> wireToSignature,
				                     Set<Wire> forcedMatchWires, NccGlobals globs) {
			(new StratLocalPartitionWires(wireToSignature, forcedMatchWires, globs))
				.doYourJob2(globs.getWires());
		}
	}

	// Add a signature to each Wire partition
	private static class SignPartitions extends Strategy {
		private Map<Wire,Signature> wireToSignature;
		// Pass signature from doFor(NetObject) to doFor(EquivRecord)
		private Signature lastSignature;
		
		@Override
		public LeafList doFor(EquivRecord er) {
			LeafList l = super.doFor(er);
			if (er.isLeaf()) er.setWireSignature(lastSignature);
			return l;
		}
		@Override
		public Integer doFor(NetObject no) {
			lastSignature = wireToSignature.get(no); 
			return Strategy.CODE_NO_CHANGE;
		}
		private SignPartitions(Map<Wire,Signature> wireToSignature, NccGlobals globs) {
			super(globs);
			this.wireToSignature = wireToSignature;
		}
		public static void doYourJob(Map<Wire,Signature> wireToSignature, NccGlobals globs) {
			(new SignPartitions(wireToSignature, globs))
				.doFor(globs.getWires());
		}
	}

	private LocalPartitionWires(NccGlobals globs) {globals=globs;}
	
	private void doYourJob(Set<Wire> forcedMatchWires) {
		EquivRecord wires = globals.getWires();
		if (wires==null) return; // don't blow up if no Wires
		
		Map<Wire,Signature> wireToSignature = ComputeSignatures.doYourJob(globals);
		cannonizeSignatures(wireToSignature);
		
		StratLocalPartitionWires.doYourJob(wireToSignature, forcedMatchWires, globals);
		
		SignPartitions.doYourJob(wireToSignature, globals);
	}

	// ------------------------ public method ---------------------------------
	/** Partition Wires based upon purely local information
	 * @param forcedMatchWires Wires that were pre-matched by the user
	 * @param globs variables used by all of NCC
	 */
	public static void doYourJob(Set<Wire> forcedMatchWires, NccGlobals globs) {
		globs.status2("Partition Wires using local information");
		LocalPartitionWires lpw = new LocalPartitionWires(globs);
		lpw.doYourJob(forcedMatchWires);
	}
}
