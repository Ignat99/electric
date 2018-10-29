/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Telesis.java
 * Input/output tool: Telesis output
 * Written by Gilda Garreton
 *
 * Copyright (c) 2013, Static Free Software. All rights reserved.
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
import com.sun.electric.database.hierarchy.HierarchyEnumerator;
import com.sun.electric.database.hierarchy.View;
import com.sun.electric.database.network.Netlist;
import com.sun.electric.database.network.Network;
import com.sun.electric.database.prototype.PortProto;
import com.sun.electric.database.topology.Connection;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.topology.PortInst;
import com.sun.electric.database.variable.VarContext;
import com.sun.electric.database.variable.Variable;
import com.sun.electric.tool.io.FileType;
import com.sun.electric.util.TextUtils;
import com.sun.electric.util.math.MutableInteger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

public class Telesis extends Geometry {

//	private TelesisPreferences localPrefs;
	private TelesisParamSort sortFunction;
	private Map<Cell,Cell> visitedCells = new HashMap<Cell,Cell>();
	private Map<Cell,List<TelesisParam>> telesisParamsList = new HashMap<Cell,List<TelesisParam>>();
	private Cell topCell;
	private boolean topTelesisCell; // if top cell must be treated as a package cell
	private String outputPath;

	public static class TelesisPreferences extends OutputPreferences
	{
        public TelesisPreferences(boolean factory)
		{
			super(factory);
		}

		@Override
		public Output doOutput(Cell cell, VarContext context, String filePath)
		{
			if (cell.getView() != View.SCHEMATIC)
			{
				System.out.println("Can only write Telesis for schematics cells based on information found on icon cells");
				return null;
			}
			Telesis out = new Telesis(this);
			out.outputPath = filePath;
			out.topCell = cell;
			out.topTelesisCell = false;
			TelesisVisitor visitor = out.makeTelesisVisitor(getMaxHierDepth(cell));
            if (!out.writeCell(cell, context, visitor)) // no error
            {
                System.out.println(out.outputPath + " written");
                if (out.errorLogger.getNumErrors() != 0)
                    System.out.println(out.errorLogger.getNumErrors() + " Telesis errors found");
            }
			return out.finishWrite();
	   }
	}

	/**
	 * Method to extract root name of bus name and also returns the indices
	 * @param name
	 * @param f
	 * @param l
	 * @return root name.
	 */
	private static String extractRootNameAndArrayIndices(String name, MutableInteger f, MutableInteger l,
			List<String> secondArray)
	{
		f.setValue(-1);
		l.setValue(-1);
		int last = -1;
		int first = name.indexOf("["); // array
		if (first != -1)
		{
			String numberArray = name.substring(first);
			name = name.substring(0, first);
			first = -1;
			last = -1;
			// look for the indices
			StringTokenizer parse = new StringTokenizer(numberArray, "[]:, ", false);
			while (parse.hasMoreTokens())
	        {
				String s = parse.nextToken();
				if (first == -1)
					first = TextUtils.atoi(s);
				else if (last == -1)
					last = TextUtils.atoi(s);
				else
				{
					assert(secondArray != null);
					// Accumulate second array indices in another list
					secondArray.add("["+s+"]");
				}
	        }
		}
		f.setValue(first);
		l.setValue(last);
		return name; // root name
	}
	
    /*********************/
    /** TelesisParam    **/
    /*********************/
	private class TelesisParam
	{
		String rootName;
		List<String> ports;
		String order;
		int first; // to know if the array of elements must be sorted out
		boolean ignorePin; // skip port during netlisting.
		
		TelesisParam(String name, List<String> ports, int first, String use, boolean ignorePin)
		{
			this.rootName = name;
			this.ports = ports;
			this.order = use;
			this.first = first;
			this.ignorePin = ignorePin;
		}
		
		String getPorts()
		{
			String output = "";
			
			if (first != -1) // sort elements
				Collections.sort(ports);
			
			for (int i = 0; i < ports.size(); i++)
			{
				String elem = ports.get(i);
				int index = elem.lastIndexOf("]");
				assert(index != -1);
				elem = elem.substring(index+1);
				output += elem + " ";
			}
			return output;
		}
		
		String composePortName(String nodeName, String portName)
		{
			// remove rootName + [] + extra ' from the name
			int index = portName.indexOf("'"); // first occurrence of '
			assert(index != -1);
			portName = portName.substring(index); // remove extra [] if available and get it from the first '
			return "'" + nodeName + "'." + portName + " ";
		}
		
		
//		private String stripApostrophe(String a)
//		{
//			int ind = a.indexOf("'");
//			if (ind < 0) return a;
//			return a.substring(0, ind);
//		}

		String getOrder()
		{
			String output = "";
			for (int i = 0; i < ports.size() - 1; i++)
				output += order + " ";
			output += order; // last one without adding the extra space
			return output;
		}
	}
	
    /*********************/
    /** TelesisParamSort**/
    /*********************/
    /**
	 * Comparator class for sorting TelesisParam by name
	 */
    private static class TelesisParamSort implements Comparator<TelesisParam>
	{
		/**
		 * Method to sort TelesisParam by their variable name.
		 */
		public int compare(TelesisParam p1, TelesisParam p2)
		{
			return p1.rootName.compareTo(p2.rootName);
		}
	}
	
	private Telesis(TelesisPreferences gp)
	{
//		localPrefs = gp;
		sortFunction = new TelesisParamSort();
	}

	@Override
	protected void start()
	{
		initOutput();
	}

	@Override
	protected void done() {}

	@Override
	protected void writeCellGeom(CellGeom cellGeom) {}
	
	/*************************** Telesis OUTPUT ROUTINES ***************************/

	/**
	 * Method to initialize various fields, get some standard values
	 */
	private void initOutput()
	{
	}

//	String extractRealPortName(List<TelesisParam> l, String rootName, int number, String nodeName, MutableInteger count)
//	{
//		for (TelesisParam ap : l)
//		{
//			if (!ap.rootName.equals(rootName)) continue; // no match
////				theFinalName = ap.extractRealPortName(number, theNodeName, portCount);
////				break;	
//		
//			if (ap.ignorePin) return ""; // nothing and found
//		
//			// Ports have already those extra '
//			if (number == -1 || ap.ports.size() == 1) // have to decompose the list regardless if number=-1 or 0
//			{
//				String finalName = "";
//				for (String s : ap.ports)
//				{
//					finalName += ap.composePortName(nodeName, s);
//				}
//				count.setValue(ap.ports.size());
//				return finalName;
//			}
//			else if (ap.ports.size() > 1 && number != -1)
//			{
//				String key = rootName + "[" + number + "]";
//				for (String s : ap.ports)
//				{
//					if (s.startsWith(key))
//					{
//						count.setValue(1);
//						return ap.composePortName(nodeName, s);
//					}
//				}
//
//				List<String> found = new ArrayList<String>();
//				for (String s : ap.ports)
//					if (s.startsWith(rootName)) found.add(s);
//				Collections.sort(found, TextUtils.STRING_NUMBER_ORDER);
//				if (found.size() > 0)
//				{
//					System.out.println("WARNING: allegro_pins is missing entry \"" + key + "\", using \"" + ap.stripApostrophe(found.get(0)) + "\" instead");
//					String msg = ap.stripApostrophe(found.get(0));
//					for(int i=1; i<found.size(); i++)
//						msg += ", " +  ap.stripApostrophe(found.get(i));
//					System.out.println("   (Did find entries: " + msg + ")");
////					return composePortName(nodeName, found.get(0));
//					// continue with another TelesisParam object
//				} else
//				{
//					System.out.println("WARNING: allegro_pins is missing entry \"" + key + "\", using \"" + ap.stripApostrophe(ap.ports.get(0)) + "\" instead");
////					return composePortName(nodeName, ports.get(0));
//					// continue with another TelesisParam object
//				}
//			}
//		}
//		//assert(false); // should it reach this level?
//		return "";
//	}
			
	String extractRealPortName(List<TelesisParam> l, String rootName, String matchingName, String nodeName, MutableInteger count)
	{
		String finalName = "";
		String lowCaseMatchingName = matchingName.toLowerCase();
		
		for (TelesisParam ap : l)
		{
			if (!ap.rootName.equalsIgnoreCase(rootName)) continue; // no match
		
			if (ap.ignorePin) return ""; // nothing and found
		
			// Ports have already those extra '
			// in case there are multiple ports -> matching by name
			boolean foundOne = false;
			for (String s : ap.ports)
			{
				if (s.toLowerCase().startsWith(lowCaseMatchingName))
				{
					finalName += ap.composePortName(nodeName, s);
					count.setValue(count.intValue()+1);
					foundOne = true;
					// must collect all ports associated with the matchingName
//					return finalName;
				}
			}
			// found one at least -> stop search here
			if (foundOne)
				return finalName;
		}
		System.out.println("Error: it should not reach this level extracting port name: " + rootName + ", " + matchingName);
		return finalName;
	}
	
	/****************************** VISITOR SUBCLASS ******************************/

	private TelesisVisitor makeTelesisVisitor(int maxDepth)
	{
		TelesisVisitor visitor = new TelesisVisitor(this, maxDepth);
		return visitor;
	}

	/**
	 * Private class to deal with multiple buses in the same net
	 *
	 */
	private class NetBusMatching
	{
		String rootName;
		String origName;
		String[] numbers; // in case of double array -> less significant number
		
		NetBusMatching(String name)
		{
			origName = name; //.replaceAll("\\[", "_").replaceAll("]", "_");
			rootName = name; // in case no [] are found
			int pos = name.indexOf("[");
			if (pos != -1)
				rootName = rootName.substring(0, pos);
			String[] values = name.replace("]", "").split("\\[");
			assert(values.length < 4);
			
			if (values.length > 1) // array, single or double
			{
				numbers = new String[values.length-1];
				System.arraycopy(values, 1, numbers, 0, values.length-1);
			}
		}
	}
	
	/**
	 * Class to override the Geometry visitor and add bloating to all polygons.
	 * Currently, no bloating is being done.
	 */
	private class TelesisVisitor extends Geometry.Visitor
	{
		TelesisVisitor(Geometry outGeom, int maxHierDepth)
		{
			super(outGeom, maxHierDepth);
		}

		/**
		 * Allow to visit icon cells which contain the Telesis data
		 */
		@Override
		public boolean visitIcons() 
		{ 
			return true; 
		}
		
		/**
		 * Method to take care of those @,[] not allowed and replaced with _
		 * @param name
		 * @return name with conflicting characters replaced
		 */
		private String correctTelesisName(String name)
		{
			return name.replaceAll("\\[", "_").replaceAll("]", "_").replaceAll("@", "_");
		}
		
		/**
		 * Overriding this function to write top cell information on exist
		 */
		public void exitCell(HierarchyEnumerator.CellInfo info)
		{
			Cell cell = info.getCell();
			if (cell != topCell || topTelesisCell) return; // done
			
			String cellName = cell.getName();
			System.out.println("Dealing with top cell '" + cellName + "'");
			String fileName = outputPath + "/" + cellName + "." + FileType.TELESIS.getFirstExtension();
			
			Map<Cell,List<NodeInst>> subCellsMap = new HashMap<Cell,List<NodeInst>>();
			
			for (Iterator<NodeInst> it = cell.getNodes(); it.hasNext();)
			{
				NodeInst ni = it.next();
				if (!ni.isCellInstance()) continue; // no subcell
				Cell c = (Cell)ni.getProto();
				List<NodeInst> l = subCellsMap.get(c);
				if (l == null)
				{
					l = new ArrayList<NodeInst>();
					subCellsMap.put(c, l);
				}
				l.add(ni);
			}
			
			if (!openTextOutputStream(fileName))
			{
				printWriter.println("$PACKAGES");
				// list of instances
				// Get the keys first and sort them so the output will be deterministic.
				List<Cell> keysList = new ArrayList<Cell>(subCellsMap.keySet());
				Collections.sort(keysList); // it should sort by cell names;
				
				for (Cell c : keysList)
				{
					List<NodeInst> nlist = subCellsMap.get(c);
					assert(nlist != null);
					printWriter.print("! " + c.getName() + " ; ");
					for (NodeInst ni : nlist)
						printWriter.print(correctTelesisName(ni.getName()) + " ");
					printWriter.println(" (# instances " + nlist.size() + ")");
				}
				printWriter.println();
				
				// nets
				printWriter.println("$NETS");
				Netlist netlist = cell.getNetlist();
				
				// new code
				for (Iterator<Network> it = netlist.getNetworks(); it.hasNext();)
				{
					Network net = it.next();
					String name = net.getName(); // in case of multiple buses -> get only the first one
					List<NetBusMatching> listConnect = new ArrayList<NetBusMatching>();
					
					// getting all possible buses/wires
					String newString = correctTelesisName(name);
					
					for (Iterator<String> sIt = net.getNames(); sIt.hasNext();)
			        {
						String s = sIt.next();  // .nextToken();
						listConnect.add(new NetBusMatching(s));
			        }
					
					int count = 0;
					StringBuffer netString = new StringBuffer("'" + newString + "' ; ");
					// iterate along list connect
					// Old code
					count = 0;
					Set<PortInst> portsVisited = new HashSet<PortInst>();
					
					for (NetBusMatching nbl : listConnect)
					{
						// caching array information 
						// look for port associated
						for (Iterator<PortInst> itP = net.getPorts(); itP.hasNext(); )
						{
							PortInst p = itP.next();
							if (portsVisited.contains(p)) continue; // already analyzed
//							portsVisited.add(p);
							
							NodeInst pi = p.getNodeInst();
							if (!pi.isCellInstance()) continue; // skipping non-subcell instances
							PortProto ex = p.getPortProto();
							String exName = ex.getName();
							
							// get indices from export
							String[] vals = exName.replace("]", "").split("\\[");
							
							Cell c = (Cell)pi.getProto();						
							if (c.isSchematic())
							{
								// look for icon cell since Telesis data is there
								Cell iconC = c.iconView();
								assert(iconC != null);
								c = iconC;
							}
							List<TelesisParam> l = telesisParamsList.get(c);
							String theNodeName = correctTelesisName(p.getNodeInst().getName());
							
							boolean foundPortMatch = false;
							boolean noMatchingName = false;
							
							// look for matching in connection names
							for (Iterator<Connection> cIt = p.getConnections(); cIt.hasNext();)
							{
								Connection con = cIt.next();
								String[] values = con.getArc().getName().replace("]", "").split("\\["); // remove ] first and later splits by [
								assert(values.length > 0);
								String rootName = values[0];
								
								if (!rootName.equalsIgnoreCase(nbl.rootName)) 
								{
									// Just only one chance to find an arc -> force name matching
									rootName = nbl.rootName;
									noMatchingName = true;
//									if (listConnect.size() == 1)
//										rootName = nbl.rootName;
//									else
//										continue; // no match
								}
								
								String theFinalName = "'" + theNodeName + "'.'" + exName + "' "; // simple case
								String origName = nbl.origName;
								int indexExRoot = exName.indexOf("[");
								String exNameRoot = (indexExRoot != -1)? exName.substring(0, indexExRoot) : exName;
								boolean checkName = true;
								
								if (values.length > 1) // array case
								{
									if (!exNameRoot.equalsIgnoreCase(rootName))
									{
										origName = origName.replace(rootName, exNameRoot);
										rootName = exNameRoot;
									}
									// length == 2 -> simple array
									// length == 3 -> double array
									// length > 3 -> not supported
									assert(values.length < 4);
									// checking indices
									checkName = false;
									int index = -1;
									int pos = -1;
									boolean noMatching = false;
									
									for (int i = 1; i < values.length; i++)
									{
										int localIndex = values[i].indexOf(":");
										
										if (localIndex != -1) // -> range -> look for first number
										{
											// found
											index = localIndex;
											pos = i;
										}
										else
										{
											// check that they match in index. Consider single digits or set like p,n
											if (i > nbl.numbers.length || !values[i].contains(nbl.numbers[i-1]))
											{
												noMatching = true;
												break;
											}
										}
									}
									
									// if both are double arrays -> check that indices matches
									// skip if pos != -1 -> range case
									if (noMatching)
									{
										continue; // no matching in the array without range
									}
									
									int start, end;
									
									if (index != -1) // range case
									{
										String startR = values[pos].substring(0, index);
										String endR = values[pos].substring(index+1, values[pos].length());
										assert(TextUtils.isANumber(startR) && TextUtils.isANumber(endR));
										start = TextUtils.atoi(startR);
										end = TextUtils.atoi(endR);
										if (start > end)
										{
											int tmp = start;
											start = end;
											end = tmp;
										}
									}
									else
									{
										pos = 1;
										String startR = values[pos];
										end = start = TextUtils.atoi(startR);
										// single index
									}
									index = TextUtils.atoi(nbl.numbers[pos-1]);
									
									// compare with first index
									if (nbl.numbers.length != vals.length - 1)
									{
										// quick fix for now -> assuming vals.length is smaller than nbl.numbers.length
										assert(vals.length < 3); // handling this case for now
										origName = vals[0];
										if (vals.length == 2)
											origName += "[" + index + "]";
									}
									
									if (start <= index && index <= end)
									{
										checkName = true;
//										 index in between range.
//										 look for port
										MutableInteger portCount = new MutableInteger(0);
										if (l != null)
										{
											theFinalName = extractRealPortName(l, rootName, origName, theNodeName, portCount);
										}
										netString.append(theFinalName);
										count += portCount.intValue();
										portsVisited.add(p);
										break; // done with this array
									}
								}
								else
								{
									origName = rootName = exName;
								}
								
								if (theFinalName.isEmpty()) // it is empty when port must be ignore
									continue;
								if (checkName)
								{
									MutableInteger portCount = new MutableInteger(0);
									if (l != null)
									{
										theFinalName = extractRealPortName(l, rootName, origName, theNodeName, portCount);
									}
									netString.append(theFinalName);
									count += portCount.intValue();
									portsVisited.add(p);
								}
								foundPortMatch = true;
								// if there is only 1 element in listConnect -> forcing to check more connections 
								// for this particular port.
//								if (listConnect.size() != 1)
									break;
							}
							// if there is only 1 element in listConnect -> forcing to check more connections 
							// for this particular port.
//							if (listConnect.size() != 1)
//								if (foundPortMatch) break;
						}
					}
					if (count == 0) 
					{
//						if (Job.getDebug())
//							System.out.println("WARNING: no matching found for net '" + name + "'");
						
						continue; // not connected net
					}
					printWriter.print(formatString(netString));
					printWriter.println(" (# of ports " + count + ")\n");
				}
				
				/** OLD CODE
				// Old code
				for (Iterator<Network> it = netlist.getNetworks(); it.hasNext();)
				{
					Network net = it.next();
					int count = 0;
					String name = net.getName();
					String newName = net.getName(); 
					String nameL = name;
					List<NetBusMatching> listConnect = new ArrayList<NetBusMatching>();
					
					// getting all possible buses/wires
					String allNames = net.describe(false);
					StringTokenizer netParse = new StringTokenizer(allNames, "/", false);
					
					while (netParse.hasMoreTokens())
			        {
						String s = netParse.nextToken();
						listConnect.add(new NetBusMatching(s));
			        }
					
					// trying to get the net name before "/"
					int index = newName.indexOf("/");
					if (index != -1) // found and therefore trimming is needed
					{
						newName = newName.substring(0, index);
					}
					
					// convert [ or ] into _
					String newNetString = newName.replaceAll("\\[", "_").replaceAll("]", "_");
					String newReminder = "";
					
					int startBus = name.indexOf("[");
					int number = -1;
					if (startBus != -1) // conform bus name a[1] -> a_1_
					{
						int endBus = name.indexOf("]");
						assert(endBus != -1);
						if (endBus < name.length() - 1) // there is more infor after first "[]"
							newReminder = name.substring(endBus+1);
						number = TextUtils.atoi(name.substring(startBus+1, endBus));
						String rootName = name.substring(0, startBus);
						name = rootName + "_" + number + "_";
					}
//					StringBuffer netString = new StringBuffer("'" + name + "' ; ");
					StringBuffer netString = new StringBuffer("'" + newNetString + "' ; ");
					
					int startBusL = name.lastIndexOf("[");
					int numberL = -1;
					if (startBusL != -1) // conform bus name a[1] -> a_1_
					{
						int endBus = name.lastIndexOf("]");
						assert(endBus != -1);
						if (endBus < name.length() - 1) // there is more infor after first "[]"
							newReminder = name.substring(endBus+1);
						numberL = TextUtils.atoi(name.substring(startBusL+1, endBus));
						String rootName = name.substring(0, startBusL);
						nameL = rootName + "_" + numberL + "_";
					}
					
					for (Iterator<PortInst> itP = net.getPorts(); itP.hasNext(); )
					{
						PortInst p = itP.next();
						NodeInst pi = p.getNodeInst();
						if (!pi.isCellInstance()) continue; // skipping non-subcell instances
						Cell c = (Cell)pi.getProto();
						if (c.isSchematic())
						{
							// look for icon cell since Telesis data is there
							Cell iconC = c.iconView();
							assert(iconC != null);
							c = iconC;
						}
						PortProto ex = p.getPortProto();
						String exName = ex.getName();
						String rootName = exName;
						startBus = exName.indexOf("[");
						if (startBus != -1) // only line is needed
						{
							assert(number != -1);
							assert(exName.length() > startBus);
							int reminderIndex = exName.indexOf("]");
							assert(reminderIndex != -1); // must be a ]
							String reminder = exName.substring(reminderIndex+1); // can go over the length?
							reminderIndex = reminder.indexOf("[");
							if (reminderIndex != -1) // recover reminding bus information, A[][]* case
								reminder = newReminder;
							rootName = exName.substring(0, startBus);
							exName = exName.substring(0, startBus) + "[" + number + "]" + reminder;
						}
						List<TelesisParam> l = telesisParamsList.get(c);
						String theNodeName = p.getNodeInst().getName();
						String theFinalName = "'" + p.getNodeInst().getName() + "'.'" + exName + "' ";
						
						MutableInteger portCount = new MutableInteger(1);
						if (l != null)
						{
							theFinalName = extractRealPortName(l, rootName, number, theNodeName, portCount);
						}
						if (theFinalName.isEmpty()) // it is empty when port must be ignore
							continue;
						netString.append(theFinalName);
						count += portCount.intValue();
					}
					if (count == 0) continue; // not connected net
					printWriter.print(formatString(netString));
					printWriter.println(" (# of ports " + count + ")");
				}
				*/
				
				// closing the file
				closeTextOutputStream();
			}
		}
		
		/**
		 * Overriding this class allows us to write information for the corresponding icon cell
		 */
		public boolean enterCell(HierarchyEnumerator.CellInfo info)
        {
			Cell cell = info.getCell();
		
			if (visitedCells.get(cell) != null)
				return false; // done with this cell

			visitedCells.put(cell, cell);
			
			String cellName = cell.getName();
			String fileName = outputPath + "/" + cellName + "." + FileType.TELESIS.getFirstExtension();
			boolean telesisCell = false;

			Cell iconCell = (cell.isIcon()) ? cell: cell.iconView(); // trying to get its corresponding icon cell
			String packageInfo = "";
			String classInfo = "";
			List<TelesisParam> pinsList = new ArrayList<TelesisParam>();
			List<TelesisParam> pinsUndefinedList = new ArrayList<TelesisParam>();
			int totalNumberOfPins = 0;
			
			if (iconCell == null)
				System.out.println("No icon cell found for cell '" + cellName + "'");
			else
			{
				// check if cell has any Allegro variable. If not, it is considered as the top cell
				// Look for variables related to Allegro
				for (Iterator<Variable> it = iconCell.getVariables(); it.hasNext(); )
				{
					Variable var = it.next();
					boolean pinsHeadFound = false; // to allow multiple lines with pins info
					for (int i = 0; i < var.getLength(); i++)
					{
						if (!var.isArray()) continue; // can't access if it is not an array
						
						String vS = (String)var.getObject(i); // I know it is a string 
						int index = vS.indexOf("-text"); // look for beginning of important info
						if (!pinsHeadFound) assert(index != -1);
						if (vS.toLowerCase().contains("allegro_package"))
							packageInfo = vS.substring(index+5).trim();
						else if (vS.toLowerCase().contains("allegro_class"))
							classInfo = vS.substring(index+5).trim();
						else if (vS.toLowerCase().contains("allegro_pins") || pinsHeadFound)
						{
							if (!pinsHeadFound)
								pinsHeadFound = vS.toLowerCase().contains("allegro_pins");
							String pinsInfo = (pinsHeadFound) ? vS.trim() : vS.substring(index+5).trim();
							int pos = 0;
							int len = pinsInfo.length();
							while (pos < len)
							{
								// look for set of elements {name pins type}
								int start = pinsInfo.indexOf("{", pos);
								if (start == -1) break; // done
								if (start < len && pinsInfo.charAt(start) == '{') start++;
								// look for matching '}' discarding intermediate pairs of {}
								int end = start;
								int numCurly = 1;
								while (end < len && numCurly != 0)
								{
									if (pinsInfo.charAt(end) == '{') numCurly++;
									else if (pinsInfo.charAt(end) == '}') numCurly--;
									end++;
								}
								String elem = pinsInfo.substring(start, --end).trim(); // trim in case there are empty space at the end or start
								pos = end++; // ++ to remove the last '}'
								// get the 3 elements from elem. The second might have extra {}
								// get the name first. If name has [] -> break it now
								int first = elem.indexOf(" "); // look for first white space
								int last = elem.lastIndexOf(" "); // last space
								assert(first != -1 && last != -1);
								String name = elem.substring(0, first).trim();
								String port = elem.substring(first, last).trim();
								String use = elem.substring(last, elem.length()).trim();
								
								// special cases -> if use = {} or port=ignorenilpin
								boolean ignorePin = port.toLowerCase().startsWith("ignorenilpin");
								
								if (use.toLowerCase().equals("fiducial"))
									use = "unspec";
								boolean undefined = use.toLowerCase().equals("unspec");
								
								MutableInteger f = new MutableInteger(-1);
								MutableInteger l = new MutableInteger(-1);
								List<String> secondArray = new ArrayList<String>();
								
								name = extractRootNameAndArrayIndices(name, f, l, secondArray);								
								last = l.intValue();
								first = f.intValue();
								
								// get list with elements
								// Should read {A B C {D C F}}
								// Remove first and last {}
								int firstB = port.indexOf("{");
								int lastB = port.lastIndexOf("}");
								// extra checking
								assert((firstB == -1 && lastB == -1) || (firstB == 0 && lastB == (port.length()-1)));
								if (firstB != -1)
									port = port.substring(firstB+1, lastB);
								boolean descend = (first > last);
								
								// New code
								StringTokenizer listParse = new StringTokenizer(port, "{} ", true);
								// only consider two levels of nesting
								List<String> listPorts = new ArrayList<String>();
								
								while (listParse.hasMoreTokens())
								{
									String s = listParse.nextToken();
									if (s.equals(" ")) continue; // escape white space
									if (s.equals("{"))
									{
										// search until } is found - no double nesting is allowed -> simple algorithm
										String tmp = "{"; // starting point - no {}
										boolean foundOne = false;
										while (listParse.hasMoreTokens())
										{
											String t = listParse.nextToken();
											if (t.equals("{")) assert(false); // not handling this case
											if (t.equals("}"))
											{
												tmp += t;
												break;
											}
											else
											{
												if (!s.equals(" ")) foundOne = true;
												tmp += t; // even white space counts
											}
										}
										if (!foundOne)
											System.out.println("Empty array");
										listPorts.add(tmp); // simple element
										
									}
									else
										listPorts.add(s); // simple element
								}
								
								// Old code
//								StringTokenizer listParse = new StringTokenizer(port, "{} ", false);
//								while (listParse.hasMoreTokens())
//						        {
//									String s = listParse.nextToken();
//									// check if s is not an array
//									s = extractRootNameAndArrayIndices(s, f, l, null);
//									int localFirst = f.intValue();
//									if (localFirst == -1) // just one element
//										listPorts.add(s); // it will extract real name after ']'
//									else
//										assert(false);  // not implemented
//						        }
								
								int length = (last != -1 && last != first) ? Math.abs(last - first) + 1 : listPorts.size();
								if (secondArray.isEmpty()) secondArray.add("");

								// Using the second array to create a long list with names
								// A[0:30][p,n] => A[0][p] A[0][n] A[1][p] A[1][n]
								// Check if it is only A[0] for example
								List<String> list = new ArrayList<String>();
								int pos2 = 0;
								int localCount1 = first;
								// Make sure first and last are not -1 to increase/decrease the localCout
								// Example AA[0] {{A V C}} needs to be treated as a single element, not array
								for (int k = 0; k < length; k++)
								{	
									for (String ex: secondArray)
									{
										String number = name + "[" + localCount1 + "]" + ex;
										String value = listPorts.get(pos2++);
										listParse = new StringTokenizer(value, "{} ", false);
										// It could be nested definition - using {} to make debugging easier
										while (listParse.hasMoreTokens())
											list.add(number + "'" + listParse.nextToken() + "'"); // it will extract real name after ']'
									}
									if (first != -1 && last != -1)
									{
										// the [<number>] is needed for the sorting. Cadence needs A[0], A[10], A[11], ....
										if (descend) localCount1--; else localCount1++;
									}
								}

								// If another TelesisParam exists with the same rootName -> not merging them
								// as the arrays might not be consecutive.
								TelesisParam param = new TelesisParam(name, list, first, use, ignorePin);
								if (!ignorePin) totalNumberOfPins += list.size();
								if (undefined)
									pinsUndefinedList.add(param);
								else
									pinsList.add(param);
							}
						}
					}
				}
				// Sort pins alphabetically CADENCE style
				Collections.sort(pinsList, sortFunction);
				Collections.sort(pinsUndefinedList, sortFunction);
				
				pinsList.addAll(pinsUndefinedList);
				
				telesisCell = pinsList.size() > 0 || !packageInfo.isEmpty() || !classInfo.isEmpty();
			}

			if (cell == topCell)
				topTelesisCell = telesisCell;
			
			if (!telesisCell)
			{
				System.out.println("No Allegro information found in '" + cellName + "'. Skipping cell");
				return super.enterCell(info); // nothing to do here
			}

			System.out.println("Writing Telesis format for Cell " + cellName);
			if (!openTextOutputStream(fileName))
			{

				printWriter.println("(Device file for " + cellName + ")");
				telesisParamsList.put(iconCell, pinsList);
				
				StringBuffer order = new StringBuffer("PINORDER '" + cellName + "' ");
				StringBuffer uses = new StringBuffer("PINUSE '" + cellName + "' ");
				StringBuffer ports = new StringBuffer("FUNCTION G1 '" + cellName + "' ");
				for (TelesisParam p : pinsList)
				{
					if (p.ignorePin) continue;
					
					order.append(p.getPorts());
					uses.append(p.getOrder() + " ");
					ports.append(p.getPorts());
				}
				printWriter.println("PACKAGE '" + packageInfo + "'");
				printWriter.println("CLASS " + classInfo);
				printWriter.println("PINCOUNT " + totalNumberOfPins);
				printWriter.println(formatString(order));
				printWriter.println(formatString(uses));
				printWriter.println(formatString(ports));
				printWriter.println("END");
				closeTextOutputStream();
			}
			return super.enterCell(info);
        }
	}
	
	private static String formatString(StringBuffer sb)
	{
		int maxLen = 68; // the value has been adjusted to match SUE output
		int len = sb.length();
		if (len <= maxLen) 
			return sb.toString();
		
		int front = sb.indexOf(" "); // cut at white spaces
		int last = 0;
		assert(front != -1);
		int lineCount = last;
		while (front < len)
		{
			front = sb.indexOf(" ", last + 1); // cut at white spaces
			if (front == -1) // done
				break;
			int tmp = lineCount + front - last;
			if (tmp > maxLen) // cut line in the previous
			{
				sb.insert(last+1, ",\n");
				lineCount = 0;
				last = front;
			}
			else
			{
				last = front;
				lineCount = tmp;
			}
		}
		return sb.toString();
	}
}
