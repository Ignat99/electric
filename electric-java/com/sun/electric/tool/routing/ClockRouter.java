/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: ClockRouter.java
 *
 * Copyright (c) 2012, Static Free Software. All rights reserved.
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
package com.sun.electric.tool.routing;

import com.sun.electric.database.EditingPreferences;
import com.sun.electric.database.geometry.EPoint;
import com.sun.electric.database.geometry.ERectangle;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.Export;
import com.sun.electric.database.hierarchy.Library;
import com.sun.electric.database.network.Netlist;
import com.sun.electric.database.network.Network;
import com.sun.electric.database.prototype.NodeProto;
import com.sun.electric.database.prototype.PortCharacteristic;
import com.sun.electric.database.prototype.PortProto;
import com.sun.electric.database.topology.ArcInst;
import com.sun.electric.database.topology.Geometric;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.topology.PortInst;
import com.sun.electric.database.topology.RTBounds;
import com.sun.electric.database.topology.RTNode;
import com.sun.electric.database.variable.EditWindow_;
import com.sun.electric.database.variable.UserInterface;
import com.sun.electric.technology.ArcProto;
import com.sun.electric.technology.Layer.Function;
import com.sun.electric.technology.PrimitiveNode;
import com.sun.electric.technology.Technology;
import com.sun.electric.technology.Technology.NodeLayer;
import com.sun.electric.technology.technologies.Generic;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.JobException;
import com.sun.electric.tool.erc.ERCWellCheck.WellBound;
import com.sun.electric.tool.erc.wellcheck.NetValues;
import com.sun.electric.tool.erc.wellcheck.Utils;
import com.sun.electric.tool.io.FileType;
import com.sun.electric.tool.io.IOTool;
import com.sun.electric.tool.routing.seaOfGates.SeaOfGatesEngine.SOGBound;
import com.sun.electric.tool.user.dialogs.OpenFile;
import com.sun.electric.util.TextUtils;
import com.sun.electric.util.math.DBMath;
import com.sun.electric.util.math.FixpRectangle;
import com.sun.electric.util.math.MutableInteger;
import com.sun.electric.util.math.Orientation;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.Serializable;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Class to build balanced clock trees.
 */
public class ClockRouter
{
	/** Editing preferences for routing. */				EditingPreferences ep;
	/** the Cell in which routing takes place. */		Cell cell;
	/** the Technology used for routing. */				Technology tech;
	/** the scale for converting to Electric units. */	double scaleUnits;
	/** the repeater placement information. */			List<RowSpec> repeaterRows;
	/** location of repeaters already placed. */		private RTNode<RepeaterBound> placedRepeaters;
	/** the minimum repeater spacing */					private double repeaterMinSpacing;
	List<ClockPath> allPaths;

    class ClockPath
	{
		List<SubTree> allGroups;
		SerializablePortInst sourceSPI;
		double sourceStubX, sourceStubY;
		/** the Cell to place for repeaters. */				Cell repeaterCell;
		/** ports on the repeater cell. */					PortProto repeaterIn, repeaterOut;
		/** the maximum distance between repeaters. */		double repeaterDistance;
		/** the prefix for repeater instance names. */		String repeaterInstancePrefix;
		/** the prefix for repeater network names. */		String repeaterNetworkPrefix;
		/** the repeater connecting arcs. */				ArcProto repeaterArc;
		/** the repeater connecting arc widths. */			double repeaterArcWidth;
		/** the scale for arc width. */						double horizScale, vertScale;
		/** the horizontal/vertical routing arcs. */		ArcProto horizArc, vertArc;
		/** the horizontal/vertical arc widths. */			double horizArcWidth, vertArcWidth;
		/** the contact between horizontal and vertical. */	PrimitiveNode cornerContact;
		/** the length of stubs coming from cells. */		double stubLength;
		String destinationNodeName, destinationPortName;
		/** the total length of this clock path. */			double totalLength;

		ClockPath()
		{
			allGroups = new ArrayList<SubTree>();
			sourceSPI = null;
			sourceStubX = sourceStubY = 0;
			repeaterDistance = 0;
			repeaterInstancePrefix = "CLK_BUF";
			repeaterNetworkPrefix = "CLK";
			repeaterArc = null;
			horizScale = vertScale = 1;
			stubLength = 10;
			destinationNodeName = "";
			destinationPortName = "";
		}

		public SubTree findSubTree(String name)
		{
			for(SubTree subST : allGroups)
				if (name.equalsIgnoreCase(subST.treeName)) return subST;
			return null;
		}

		public double getArcWidth(ArcProto ap)
		{
			double arcWidth = ap.getDefaultLambdaBaseWidth(ep) * horizScale;
			return arcWidth;
		}
	}

	public ClockRouter(EditingPreferences ep, Cell cell)
	{
		this.ep = ep;
		this.cell = cell;
		tech = Technology.getCurrent();
		allPaths = new ArrayList<ClockPath>();
		scaleUnits = 1;
		repeaterRows = new ArrayList<RowSpec>();
		placedRepeaters = RTNode.makeTopLevel();
	}

	private Point2D getContactSize(PrimitiveNode pnp, ArcProto ap1, double width1, ArcProto ap2, double width2)
	{
		double extra1 = width1 - ap1.getDefaultLambdaBaseWidth(ep);
		double extra2 = width2 - ap2.getDefaultLambdaBaseWidth(ep);
		double sizeX = pnp.getDefWidth(ep) + extra1;
		double sizeY = pnp.getDefHeight(ep) + extra2;
		return new Point2D.Double(sizeX, sizeY);
	}

	private PrimitiveNode findContact(Technology tech, int l1, int l2)
	{
		for(Iterator<PrimitiveNode> it = tech.getNodes(); it.hasNext(); )
		{
			PrimitiveNode pn = it.next();
			if (pn.getFunction() != PrimitiveNode.Function.CONTACT) continue;
			NodeLayer[] layers = pn.getNodeLayers();
			boolean sourceFound = false, destFound = false;
			for(int j=0; j<layers.length; j++)
			{
				Function fun = layers[j].getLayer().getFunction();
				if (!fun.isMetal()) continue;
				if (fun.getLevel() == l1) sourceFound = true;
				if (fun.getLevel() == l2) destFound = true;
			}
			if (sourceFound && destFound) return pn;
		}
		return null;
	}

	private static class NeededHTree implements Serializable
	{
		SerializablePortInst source;
		List<SerializablePortInst> destinations;
		List<ArcInst> originalArcs;

		public NeededHTree()
		{
			source = null;
			destinations = new ArrayList<SerializablePortInst>();
			originalArcs = new ArrayList<ArcInst>();
		}

		public void addDestination(SerializablePortInst spi)
		{
			for(SerializablePortInst spiTest : destinations)
				if (spiTest.equals(spi)) return;
			destinations.add(spi);
		}
	}

	private static class SerializablePortInst implements Serializable
	{
		NodeInst ni;
		String portName;
		double distanceTraversed;
		String subTreeName;

		public SerializablePortInst(PortInst pi)
		{
			ni = pi.getNodeInst();
			portName = pi.getPortProto().getName();
			distanceTraversed = 0;
			subTreeName = null;
		}

		public PortProto getPortProto()
		{
			return ni.getProto().findPortProto(portName);
		}

		public PortInst getPortInst()
		{
			return ni.findPortInstFromProto(getPortProto());
		}

		public boolean equals(SerializablePortInst spi)
		{
			return ni == spi.ni && portName.equals(spi.portName);
		}
	}

	public static void routeHTree()
	{
		UserInterface ui = Job.getUserInterface();
		Cell cell = ui.needCurrentCell();
		if (cell == null) return;
		EditWindow_ wnd = ui.getCurrentEditWindow_();
		if (wnd == null) return;

		List<NeededHTree> treesToRoute = new ArrayList<NeededHTree>();

		Set<Network> nets = wnd.getHighlightedNetworks();
		if (nets.size() == 0)
		{
			NeededHTree nht = new NeededHTree();
			treesToRoute.add(nht);
		} else
		{
			Netlist netList = cell.getNetlist();
			if (netList == null)
			{
				System.out.println("Sorry, a deadlock aborted routing (network information unavailable).  Please try again");
				return;
			}
			Map<Network,ArcInst[]> arcMap = netList.getArcInstsByNetwork();

			// convert to a list of NeededHTree objects
			for(Network net : nets)
			{
				ArcInst[] arcs = arcMap.get(net);
				if (arcs == null)
				{
					System.out.println("WARNING: Network " + net.describe(false) + " has no arcs on it");
					continue;
				}

				// make the NeededHTree object
				NeededHTree nht = new NeededHTree();
				for(int i=0; i<arcs.length; i++)
				{
					ArcInst ai = arcs[i];
					if (ai.getProto() != Generic.tech().unrouted_arc) continue;
					for(int e=0; e<2; e++)
					{
						PortInst pi = ai.getPortInst(e);
						NodeInst ni = pi.getNodeInst();
						if (!ni.isCellInstance()) continue;
						Export ex = (Export)pi.getPortProto();
						PortCharacteristic pc = ex.getCharacteristic();
						if (pc.isClock())
						{
							nht.addDestination(new SerializablePortInst(pi));
						} else if (pc == PortCharacteristic.OUT)
						{
							SerializablePortInst spiNew = new SerializablePortInst(pi);
							if (nht.source != null)
							{
								if (!nht.source.equals(spiNew))
								{
									System.out.println("ERROR: Network " + net.describe(false) + " has multiple drivers: " +
										nht.source.ni.describe(false) + ", port " + nht.source.portName +
										" and " + ni.describe(false) + ", port " + ex.getName());
								}
								continue;
							}
							nht.source = spiNew;
						}
					}
				}

				// make sure something is driving the network
				if (nht.source == null)
				{
					System.out.println("ERROR: Network "+net.describe(false) + " has no source (driver)");
					continue;
				}

				// add original arcs to be removed when routed
				for(int i=0; i<arcs.length; i++)
				{
					ArcInst ai = arcs[i];
					if (ai.getProto() != Generic.tech().unrouted_arc) continue;
					nht.originalArcs.add(ai);
				}

				treesToRoute.add(nht);
			}
		}

		// get the directive file
		String fileName = OpenFile.chooseInputFile(FileType.TEXT, "Clock-Tree Routing Directive file:", null);
		if (fileName == null) return;
		new ClockTreeRouteJob(treesToRoute, fileName, cell);
	}

	private static class ClockTreeRouteJob extends Job
	{
		private List<NeededHTree> treesToRoute;
		private String fileName;
		private Cell cell;

		protected ClockTreeRouteJob(List<NeededHTree> treesToRoute, String fileName, Cell cell)
		{
			super("Clock-Tree Route", Routing.getRoutingTool(), Job.Type.CHANGE, null, null, Job.Priority.USER);
			this.treesToRoute = treesToRoute;
			this.fileName = fileName;
			this.cell = cell;
			startJob();
		}

		@Override
		public boolean doIt() throws JobException
		{
			ClockRouter router = new ClockRouter(getEditingPreferences(), cell);
			for(NeededHTree nht : treesToRoute)
			{
//				boolean good = router.routeAlgorithm1(nht, cell);
				boolean good = router.routeAlgorithm2(nht, fileName, cell);
				if (good)
				{
					for(ArcInst ai : nht.originalArcs)
						ai.kill();
				}
			}
			return true;
		}
	}

	// ***************************************** ALGORITHM 2 *****************************************

	private boolean routeAlgorithm2(NeededHTree nht, String fileName, Cell cell)
	{
		String pnrOutputFile = null;
		double pnrOutputScale = 1;
		ClockPath curCP = null;
		URL url = TextUtils.makeURLToFile(fileName);
		try
		{
			URLConnection urlCon = url.openConnection();
			InputStreamReader is = new InputStreamReader(urlCon.getInputStream());
			LineNumberReader lineReader = new LineNumberReader(is);
			for(;;)
			{
				String directive = lineReader.readLine();
				if (directive == null) break;

				String[] splitParts = directive.trim().split(" ");
				List<String> parts = new ArrayList<String>();
				for(int j=0; j<splitParts.length; j++)
				{
					if (splitParts[j].length() > 0) parts.add(splitParts[j]);
				}

				// ignore blank lines or comments
				if (parts.size() == 0) continue;
				String firstPart = parts.get(0);
				if (firstPart.startsWith("#")) continue;

				// directives that can be given anywhere
				if (firstPart.equalsIgnoreCase("UNITS"))
				{
					for(int j=1; j<parts.size(); j++)
					{
						if (parts.get(j).toLowerCase().startsWith("microns="))
						{
							scaleUnits = TextUtils.atof(parts.get(j).substring(8));
							continue;
						}
					}
					continue;
				}
				if (firstPart.equalsIgnoreCase("ROW"))
				{
					// special format: from DEF
					if (parts.size() != 14)
					{
						System.out.println("ERROR on line " + lineReader.getLineNumber() +
							" (ROW directive): expecting 14 keywords but found " + parts.size());
						continue;
					}
					if (!parts.get(6).toLowerCase().equals("do"))
					{
						System.out.println("ERROR on line " + lineReader.getLineNumber() +
							" (ROW directive): missing 'DO' keyword");
						continue;
					}
					if (!parts.get(8).toLowerCase().equals("by"))
					{
						System.out.println("ERROR on line " + lineReader.getLineNumber() +
							" (ROW directive): missing 'BY' keyword");
						continue;
					}
					if (!parts.get(10).toLowerCase().equals("step"))
					{
						System.out.println("ERROR on line " + lineReader.getLineNumber() +
							" (ROW directive): missing 'STEP' keyword");
						continue;
					}
					if (!parts.get(13).equals(";"))
					{
						System.out.println("ERROR on line " + lineReader.getLineNumber() +
							" (ROW directive): missing ';'");
						continue;
					}
					RowSpec rs = new RowSpec();
					rs.origX = convertToUnits(parts.get(3));
					rs.origY = convertToUnits(parts.get(4));
					String orientStr = parts.get(5);
					rs.orient = getOrientation(orientStr);
					rs.repeatX = TextUtils.atoi(parts.get(7));
					rs.repeatY = TextUtils.atoi(parts.get(9));
					rs.stepX = convertToUnits(parts.get(11));
					rs.stepY = convertToUnits(parts.get(12));
					repeaterRows.add(rs);
					continue;
				}
				if (firstPart.equalsIgnoreCase("PNR-OUTPUT"))
				{
					if (!IOTool.hasPnR())
					{
						System.out.println("WARNING: PNR-OUTPUT directive ignored because PNR module is not installed");
						continue;
					}
					for(int j=1; j<parts.size(); j++)
					{
						if (parts.get(j).toLowerCase().startsWith("file="))
						{
							pnrOutputFile = parts.get(j).substring(5);
							continue;
						}
						if (parts.get(j).toLowerCase().startsWith("scale="))
						{
							pnrOutputScale = TextUtils.atof(parts.get(j).substring(6));
							continue;
						}
						System.out.println("ERROR on line " + lineReader.getLineNumber() +
							" (PNR-OUTPUT directive): unknown keyword (" + parts.get(j) + ")");
						lineReader.close();
						return false;
					}
					continue;
				}

				// directives for defining a path
				if (firstPart.equalsIgnoreCase("START-PATH"))
				{
					curCP = new ClockPath();
					allPaths.add(curCP);
					continue;
				}
				if (firstPart.equalsIgnoreCase("END-PATH"))
				{
					curCP = null;
					continue;
				}

				// directives that must be given inside of a path
				if (firstPart.equalsIgnoreCase("DESTINATION"))
				{
					if (curCP == null)
					{
						System.out.println("ERROR on line " + lineReader.getLineNumber() +
							": Must have START-PATH before a DESTINATION directive");
						lineReader.close();
						return false;
					}
					for(int j=1; j<parts.size(); j++)
					{
						if (parts.get(j).toLowerCase().startsWith("node="))
						{
							curCP.destinationNodeName = parts.get(j).substring(5);
							continue;
						}
						if (parts.get(j).toLowerCase().startsWith("port="))
						{
							curCP.destinationPortName = parts.get(j).substring(5);
							continue;
						}
						if (parts.get(j).toLowerCase().startsWith("stub="))
						{
							String stubStr = parts.get(j).substring(5);
							curCP.stubLength = convertToUnits(stubStr);
							continue;
						}
						System.out.println("ERROR on line " + lineReader.getLineNumber() +
							" (DESTINATION directive): unknown keyword (" + parts.get(j) + ")");
						lineReader.close();
						return false;
					}
					continue;
				}
				if (firstPart.equalsIgnoreCase("SOURCE"))
				{
					if (curCP == null)
					{
						System.out.println("ERROR on line " + lineReader.getLineNumber() +
							": Must have START-PATH before a SOURCE directive");
						lineReader.close();
						return false;
					}
					String sourceNodeName = "", sourcePortName = "";
					for(int j=1; j<parts.size(); j++)
					{
						if (parts.get(j).toLowerCase().startsWith("node="))
						{
							sourceNodeName = parts.get(j).substring(5);
							continue;
						}
						if (parts.get(j).toLowerCase().startsWith("port="))
						{
							sourcePortName = parts.get(j).substring(5);
							continue;
						}
						if (parts.get(j).toLowerCase().startsWith("stubx="))
						{
							String stubStr = parts.get(j).substring(6);
							curCP.sourceStubX = convertToUnits(stubStr);
							continue;
						}
						if (parts.get(j).toLowerCase().startsWith("stuby="))
						{
							String stubStr = parts.get(j).substring(6);
							curCP.sourceStubY = convertToUnits(stubStr);
							continue;
						}
						System.out.println("ERROR on line " + lineReader.getLineNumber() +
							" (SOURCE directive): unknown keyword (" + parts.get(j) + ")");
						lineReader.close();
						return false;
					}

					// find the source node
					for(Iterator<NodeInst> it = cell.getNodes(); it.hasNext(); )
					{
						NodeInst ni = it.next();
						if (!ni.isCellInstance()) continue;
						if (ni.getProto().getName().equals(sourceNodeName))
						{
							PortProto pp = ni.getProto().findPortProto(sourcePortName);
							if (pp == null)
							{
								System.out.println("ERROR on line " + lineReader.getLineNumber() +
									" (SOURCE directive): cannot find port " + sourcePortName + " on node " + ni.describe(false));
								lineReader.close();
								return false;
							}
							curCP.sourceSPI = new SerializablePortInst(ni.findPortInstFromProto(pp));
							break;
						}
					}
					if (curCP.sourceSPI == null)
					{
						System.out.println("ERROR on line " + lineReader.getLineNumber() +
							" (SOURCE directive): cannot find " + sourceNodeName + " cell");
						lineReader.close();
						return false;
					}
					continue;
				}
				if (firstPart.equalsIgnoreCase("REPEATER"))
				{
					if (curCP == null)
					{
						System.out.println("ERROR on line " + lineReader.getLineNumber() +
							": Must have START-PATH before a REPEATER directive");
						lineReader.close();
						return false;
					}
					for(int j=1; j<parts.size(); j++)
					{
						if (parts.get(j).toLowerCase().startsWith("cell="))
						{
							String repeaterCellName = parts.get(j).substring(5);
							for(Library lib : Library.getVisibleLibraries())
							{
								curCP.repeaterCell = lib.findNodeProto(repeaterCellName);
								if (curCP.repeaterCell != null) break;
							}
							if (curCP.repeaterCell == null)
							{
								System.out.println("WARNING on line " + lineReader.getLineNumber() +
									" (REPEATER directive): cell " + repeaterCellName + " not found.  Not placing repeaters.");
							}
							curCP.repeaterIn = curCP.repeaterOut = null;
							for(Iterator<PortProto> it = curCP.repeaterCell.getPorts(); it.hasNext(); )
							{
								PortProto pp = it.next();
								PortCharacteristic pc = pp.getCharacteristic();
								if (pc == PortCharacteristic.IN)
								{
									if (curCP.repeaterIn != null)
										System.out.println("WARNING on line " + lineReader.getLineNumber() +
											" (REPEATER directive): cell " + curCP.repeaterCell.describe(false) + " has multiple input ports.");
									curCP.repeaterIn = pp;
								}
								if (pc == PortCharacteristic.OUT)
								{
									if (curCP.repeaterOut != null)
										System.out.println("WARNING on line " + lineReader.getLineNumber() +
											" (REPEATER directive): cell " + curCP.repeaterCell.describe(false) + " has multiple output ports.");
									curCP.repeaterOut = pp;
								}
							}
							if (curCP.repeaterIn == null)
								System.out.println("WARNING on line " + lineReader.getLineNumber() +
									" (REPEATER directive): cell " + curCP.repeaterCell.describe(false) + " has no input ports. Not placing repeaters.");
							if (curCP.repeaterOut == null)
								System.out.println("WARNING on line " + lineReader.getLineNumber() +
									" (REPEATER directive): cell " + curCP.repeaterCell.describe(false) + " has no output ports. Not placing repeaters.");
							if (curCP.repeaterIn == null || curCP.repeaterOut == null) curCP.repeaterCell = null;
							continue;
						}
						if (parts.get(j).toLowerCase().startsWith("dist="))
						{
							String distNum = parts.get(j).substring(5);
							curCP.repeaterDistance = convertToUnits(distNum);
							continue;
						}
						if (parts.get(j).toLowerCase().startsWith("instname="))
						{
							curCP.repeaterInstancePrefix = parts.get(j).substring(9);
							continue;
						}
						if (parts.get(j).toLowerCase().startsWith("netname="))
						{
							curCP.repeaterNetworkPrefix = parts.get(j).substring(8);
							continue;
						}
						if (parts.get(j).toLowerCase().startsWith("connect="))
						{
							curCP.repeaterArc = null;
							int level = TextUtils.atoi(parts.get(j).substring(8));
							for(Iterator<ArcProto> it = tech.getArcs(); it.hasNext(); )
							{
								ArcProto ap = it.next();
								ArcProto.Function fun = ap.getFunction();
								if (fun.isMetal() && fun.getLevel() == level) curCP.repeaterArc = ap;
							}
							if (curCP.repeaterArc == null)
							{
								System.out.println("ERROR on line " + lineReader.getLineNumber() +
									" (REPEATER directive): connection layer unknown (" + level + ")");
								lineReader.close();
								return false;
							}
							curCP.repeaterArcWidth = curCP.getArcWidth(curCP.repeaterArc);
							continue;
						}
						System.out.println("ERROR on line " + lineReader.getLineNumber() +
							" (REPEATER directive): unknown keyword (" + parts.get(j) + ")");
						lineReader.close();
						return false;
					}
					continue;
				}
				if (firstPart.equalsIgnoreCase("LAYERS"))
				{
					if (curCP == null)
					{
						System.out.println("ERROR on line " + lineReader.getLineNumber() +
							": Must have START-PATH before a LAYERS directive");
						lineReader.close();
						return false;
					}
					for(int j=1; j<parts.size(); j++)
					{
						if (parts.get(j).toLowerCase().startsWith("horizontal="))
						{
							curCP.horizArc = null;
							int level = TextUtils.atoi(parts.get(j).substring(11));
							for(Iterator<ArcProto> it = tech.getArcs(); it.hasNext(); )
							{
								ArcProto ap = it.next();
								ArcProto.Function fun = ap.getFunction();
								if (fun.isMetal() && fun.getLevel() == level) curCP.horizArc = ap;
							}
							if (curCP.horizArc == null)
							{
								System.out.println("ERROR on line " + lineReader.getLineNumber() +
									" (LAYERS directive): horizontal layer unknown (" + level + ")");
								lineReader.close();
								return false;
							}
							curCP.horizArcWidth = curCP.getArcWidth(curCP.horizArc);
							continue;
						}
						if (parts.get(j).toLowerCase().startsWith("vertical="))
						{
							curCP.vertArc = null;
							int level = TextUtils.atoi(parts.get(j).substring(9));
							for(Iterator<ArcProto> it = tech.getArcs(); it.hasNext(); )
							{
								ArcProto ap = it.next();
								ArcProto.Function fun = ap.getFunction();
								if (fun.isMetal() && fun.getLevel() == level) curCP.vertArc = ap;
							}
							if (curCP.vertArc == null)
							{
								System.out.println("ERROR on line " + lineReader.getLineNumber() +
									" (LAYERS directive): vertical layer unknown (" + level + ")");
								lineReader.close();
								return false;
							}
							curCP.vertArcWidth = curCP.getArcWidth(curCP.vertArc);
							continue;
						}
						if (parts.get(j).toLowerCase().startsWith("horizontal-scale="))
						{
							curCP.horizScale = TextUtils.atof(parts.get(j).substring(17));
							continue;
						}
						if (parts.get(j).toLowerCase().startsWith("vertical-scale="))
						{
							curCP.vertScale = TextUtils.atof(parts.get(j).substring(15));
							continue;
						}
						System.out.println("ERROR on line " + lineReader.getLineNumber() +
							" (LAYERS directive): unknown keyword (" + parts.get(j) + ")");
						lineReader.close();
						return false;
					}
					if (curCP.horizArc != null && curCP.vertArc != null)
					{
						int l1 = curCP.horizArc.getFunction().getLevel();
						int l2 = curCP.vertArc.getFunction().getLevel();
						curCP.cornerContact = findContact(tech, l1, l2);
						if (curCP.cornerContact == null)
						{
							System.out.println("ERROR on line " + lineReader.getLineNumber() +
								" (LAYERS directive): cannot find contact to join metals " + l1 + " and " + l2);
							lineReader.close();
							return false;
						}
					}
					continue;
				}

				// handle channels
				if (firstPart.equalsIgnoreCase("CHANNEL"))
				{
					if (curCP == null)
					{
						System.out.println("ERROR on line " + lineReader.getLineNumber() +
							": Must have START-PATH before a CHANNEL directive");
						lineReader.close();
						return false;
					}
					SubTree st = new SubTree();
					for(int j=1; j<parts.size(); j++)
					{
						String part = parts.get(j);
						if (part.toLowerCase().startsWith("name="))
						{
							st.setTreeName(part.substring(5));
							continue;
						}
						if (part.toLowerCase().startsWith("in="))
						{
							st.inEdge = getDirectionName(part.substring(3));
							if (st.inEdge < 0)
							{
								System.out.println("ERROR on line " + lineReader.getLineNumber() +
									" (CHANNEL directive): unknown 'in' edge (" + part.substring(3) + ")");
								lineReader.close();
								return false;
							}
							continue;
						}
						if (part.toLowerCase().startsWith("out="))
						{
							st.outEdge = getDirectionName(part.substring(4));
							if (st.outEdge < 0)
							{
								System.out.println("ERROR on line " + lineReader.getLineNumber() +
									" (CHANNEL directive): unknown 'out' edge (" + part.substring(4) + ")");
								lineReader.close();
								return false;
							}
							continue;
						}

						// presume that it is a node name
						SerializablePortInst found = null;
						for(Iterator<NodeInst> it = cell.getNodes(); it.hasNext(); )
						{
							NodeInst ni = it.next();
							if (!ni.isCellInstance()) continue;
							if (ni.getProto().getName().equals(curCP.destinationNodeName))
							{
								if (ni.getName().endsWith(part))
								{
									PortProto pp = ni.getProto().findPortProto(curCP.destinationPortName);
									if (pp == null)
									{
										System.out.println("ERROR on line " + lineReader.getLineNumber() +
											" (CHANNEL directive): cannot find port " + curCP.destinationPortName + " on node " + ni.describe(false));
										lineReader.close();
										return false;
									}
									found = new SerializablePortInst(ni.findPortInstFromProto(pp));
									break;
								}
							}
						}
						if (found == null)
						{
							// look through previous group names
							SubTree subST = curCP.findSubTree(part);
							if (subST != null) found = subST.output;
						}
						if (found == null)
						{
							System.out.println("ERROR on line " + lineReader.getLineNumber() +
								" (CHANNEL directive): unknown node name (" + part + ")");
							lineReader.close();
							return false;
						}
						st.connections.add(found);
					}
					for(int i=0; i<100; i++)
					{
						if (st.route(curCP)) break;
					}
					curCP.allGroups.add(st);
				}
			}
			lineReader.close();
		} catch (IOException e)
		{
			System.out.println("Error reading " + fileName);
			return false;
		}

		// figure out minimum repeater row spacing
		RowSpec lastRS = null;
		repeaterMinSpacing = Double.MAX_VALUE;
		for(RowSpec rs : repeaterRows)
		{
			if (rs.stepX != 0 && rs.stepX < repeaterMinSpacing) repeaterMinSpacing = rs.stepX;
			if (rs.stepY != 0 && rs.stepY < repeaterMinSpacing) repeaterMinSpacing = rs.stepY;
			if (lastRS != null)
			{
				if (lastRS.origX != rs.origX)
				{
					double dist = Math.abs(lastRS.origX - rs.origX);
					if (dist > 0 && dist < repeaterMinSpacing) repeaterMinSpacing = dist;
				}
				if (lastRS.origY != rs.origY)
				{
					double dist = Math.abs(lastRS.origY - rs.origY);
					if (dist > 0 && dist < repeaterMinSpacing) repeaterMinSpacing = dist;
				}
			}
			lastRS = rs;
		}

		// start by adding source stubs to every clock path
		for(ClockPath cp : allPaths)
		{
			// find the root subtree of this path
			SubTree thisST = cp.allGroups.get(cp.allGroups.size()-1);

			// route the top of the tree to the source
			EPoint sourcePT = null;
			if (cp.sourceSPI != null)
			{
				PortInst sourcePI = cp.sourceSPI.getPortInst();
				sourcePT = sourcePI.getCenter();
				if (cp.sourceStubX != 0 || cp.sourceStubY != 0)
				{
					MakePoint sourceMP = new MakePoint(cp.sourceSPI);
					EPoint ctr = EPoint.fromLambda(sourcePT.getX() + cp.sourceStubX, sourcePT.getY() + cp.sourceStubY);
					Point2D mpSize = getContactSize(cp.cornerContact, cp.horizArc, cp.horizArcWidth, cp.vertArc, cp.vertArcWidth);
					MakePoint mp = new MakePoint(cp.cornerContact, ctr, mpSize.getX(), mpSize.getY(), ep, cell);
					thisST.allPoints.add(mp);
					thisST.allConnections.add(new MakeConnection(cp, sourceMP, mp));
					cp.sourceSPI = new SerializablePortInst(mp.ni.getOnlyPortInst());
					thisST.output.distanceTraversed += Math.sqrt(cp.sourceStubX*cp.sourceStubX + cp.sourceStubY*cp.sourceStubY);
				}
			}
		}

		// find out longest path
		double biggestDistance = 0;
		for(ClockPath cp : allPaths)
		{
			cp.totalLength = 0;
			for(SubTree st : cp.allGroups)
			{
				if (st.output.distanceTraversed > cp.totalLength) cp.totalLength = st.output.distanceTraversed;
			}

			// add in distance to source
			PortInst sourcePI = cp.sourceSPI.getPortInst();
			EPoint sourcePT = sourcePI.getCenter();
			SubTree thisST = cp.allGroups.get(cp.allGroups.size()-1);
			SerializablePortInst destSPI = thisST.output;
			EPoint destPT = destSPI.ni.getAnchorCenter();
			cp.totalLength += Math.abs(sourcePT.getX() - destPT.getX()) + Math.abs(sourcePT.getY() - destPT.getY());

			// accumulate longest clock path
			if (cp.totalLength > biggestDistance) biggestDistance = cp.totalLength;
		}

		// finish trees, with synchronized geometry and repeaters
		for(ClockPath cp : allPaths)
		{
			// extend this path if it is shorter than the longest synchronized path
			SubTree thisST = cp.allGroups.get(cp.allGroups.size()-1);
			if (cp.totalLength < biggestDistance)
			{
				// add serpentine up front
				double amountToAdd = biggestDistance - cp.totalLength - cp.stubLength*2;
//System.out.println("PATH "+thisST.treeName+" IS "+cp.totalLength+" LONG BUT MUST BE "+biggestDistance+" SO EXTEND PATH BY "+amountToAdd);
				thisST.addSerpentineAmount(cp, amountToAdd);
			}

			// route the top of the tree to the source
			connectToSource(cp, thisST, cp.sourceSPI);

			// prepare this path
			List<MakeConnection> everyConnection = new ArrayList<MakeConnection>();
			List<MakePoint> everyPoint = new ArrayList<MakePoint>();
			for(SubTree st : cp.allGroups)
			{
				for(MakeConnection mc : st.allConnections) everyConnection.add(mc);
				for(MakePoint mp : st.allPoints) everyPoint.add(mp);
			}

			// add repeaters, if source is defined
			EPoint sourcePT = null;
			if (cp.sourceSPI != null)
			{
				PortInst sourcePI = cp.sourceSPI.getPortInst();
				sourcePT = sourcePI.getCenter();
				RoutePath fullPath = new RoutePath(everyConnection, sourcePT, null, 1);
//				fullPath.dumpRoutePath(0, 0);

				// add repeaters to tree if requested
				if (cp.repeaterDistance > 0)
				{
					Map<Integer,MutableInteger> levelCount = new HashMap<Integer,MutableInteger>();
					fullPath.addRepeaters(cp, everyConnection, cp.repeaterDistance, everyPoint, levelCount);
				}
			}

			// create all connections
			placeArcs(cp, everyConnection, everyPoint, cell);
		}

		// dump PNR output if requested
		if (pnrOutputFile != null && IOTool.hasPnR())
		{
			// make a list of geometry to write to the PNR file
			List<Geometric> allGeometry = new ArrayList<Geometric>();
			for(ClockPath cp : allPaths)
			{
				for(SubTree st : cp.allGroups)
				{
					for(MakeConnection mc : st.allConnections) if (mc.ai != null) allGeometry.add(mc.ai);
					for(MakePoint mp : st.allPoints) if (mp.ni != null) allGeometry.add(mp.ni);
				}
			}
			String outFileName = TextUtils.getFilePath(url) + pnrOutputFile;
			IOTool.PnRPreferences pnrp = new IOTool.PnRPreferences(true);
			pnrp.writePnR(allGeometry, pnrOutputScale, outFileName, true);
		}

// dump directives
//for(SubTree st : allGroups)
//{
//	System.out.println("TREE "+st.treeName+" CONNECTS:");
//	for(SerializablePortInst spi : st.connections)
//	{
//		EPoint pt = spi.getPortInst().getCenter();
//		System.out.println("   "+spi.ni.describe(false)+", PORT "+spi.getPortProto().getName()+" AT ("+pt.getX()+","+pt.getY()+")");
//	}
//	for(MakeConnection mc : st.allConnections)
//		System.out.println("   ("+mc.from.loc.getX()+","+mc.from.loc.getY()+") to ("+mc.to.loc.getX()+","+mc.to.loc.getY()+")");
//	for(MakeConnection mc : st.serpentineConnections)
//		System.out.println("   SERP: ("+mc.from.loc.getX()+","+mc.from.loc.getY()+") to ("+mc.to.loc.getX()+","+mc.to.loc.getY()+")");
//}
//System.out.println("DRIVEN FROM "+sourceSPI.ni.describe(false)+", PORT "+sourceSPI.getPortProto().getName());

		return false;
	}

	private class RowSpec
	{
		double origX, origY;
		int repeatX, repeatY;
		double stepX, stepY;
		Orientation orient;

		RowSpec()
		{
			stepX = stepY = 1;
		}
	}

	private class AlignRepeater
	{
		EPoint loc;
		Orientation orient;

		public AlignRepeater(double x, double y, Cell rc)
		{
			boolean good = findRepeater(x, y, rc);
			if (good)
			{
				ERectangle bound = getKey(loc, orient, rc);
				placedRepeaters = RTNode.linkGeom(null, placedRepeaters, new RepeaterBound(bound));
				return;
			}

			// already a repeater there, look in nearby locations
			EPoint orig = EPoint.fromLambda(x, y);
			for(int mult=1; mult<10; mult++)
			{
				Map<Double,EPoint> choiceLocs = new TreeMap<Double,EPoint>();
				Map<Double,Orientation> choiceOris = new TreeMap<Double,Orientation>();
				for(int offX = -mult; offX <= mult; offX++)
				{
					for(int offY = -mult; offY <= mult; offY++)
					{
						if (offX == 0 && offY == 0) continue;
						double newX = x + offX * repeaterMinSpacing;
						double newY = y + offY * repeaterMinSpacing;
						good = findRepeater(newX, newY, rc);
						if (!good) continue;
						double dist = loc.distance(orig);
						choiceLocs.put(Double.valueOf(dist), loc);
						choiceOris.put(Double.valueOf(dist), orient);
					}
				}
				if (choiceLocs.size() > 0)
				{
					Set<Double> keys = choiceLocs.keySet();
					for(Double d : keys)
					{
						loc = choiceLocs.get(d);
						orient = choiceOris.get(d);
						ERectangle bound = getKey(loc, orient, rc);
						placedRepeaters = RTNode.linkGeom(null, placedRepeaters, new RepeaterBound(bound));
						return;
					}
				}
			}
		}

		private ERectangle getKey(EPoint pt, Orientation orient, Cell rc)
		{
			double wid = rc.getDefWidth(ep);
			double hei = rc.getDefHeight(ep);
			ERectangle cellBound = rc.getBounds();
			double shiftX = cellBound.getMinX();
			double shiftY = cellBound.getMinY();
			pt = EPoint.fromLambda(pt.getX()-shiftX, pt.getY()-shiftY);
			NodeInst ni = NodeInst.makeDummyInstance(rc, ep, loc, wid, hei, orient);
			return ni.getBounds();
		}

		private boolean canPlaceRepeater(ERectangle bounds)
		{
            for (Iterator<RepeaterBound> sea = new RTNode.Search<RepeaterBound>(bounds, placedRepeaters, true); sea.hasNext();)
            {
            	RepeaterBound rb = sea.next();
            	if (rb.getBounds().intersects(bounds)) return false;
            }
            return true;
		}

		private boolean findRepeater(double x, double y, Cell rc)
		{
			RowSpec closest = null;
			double bestDist = Double.MAX_VALUE;
			for(RowSpec rs : repeaterRows)
			{
				double dist = Math.abs(x - rs.origX) + Math.abs(y - rs.origY);
				if (dist < bestDist)
				{
					bestDist = dist;
					closest = rs;
				}
			}
			orient = Orientation.IDENT;
			if (closest != null)
			{
				orient = closest.orient;
				if (closest.repeatX == 1)
				{
					x = closest.origX;
					long repeatFactor = Math.round((y - closest.origY) / closest.stepY);
					if (repeatFactor < 0) repeatFactor = 0;
					if (repeatFactor > closest.repeatY) repeatFactor = closest.repeatY;
					y = repeatFactor * closest.stepY + closest.origY;
				} else
				{
					long repeatFactor = Math.round((x - closest.origX) / closest.stepX);
					if (repeatFactor < 0) repeatFactor = 0;
					if (repeatFactor > closest.repeatX) repeatFactor = closest.repeatX;
					x = repeatFactor * closest.stepX + closest.origX;
					y = closest.origY;
				}
			}
			loc = EPoint.fromLambda(x, y);
			ERectangle bounds = getKey(loc, orient, rc);
			if (canPlaceRepeater(bounds)) return true;
			return false;
		}

		public EPoint getLocation() { return loc; }

		public Orientation getOrientation() { return orient; }
	}

    /**
     * Class to define an R-Tree leaf node for repeater bounds.
     */
    public static class RepeaterBound implements RTBounds
    {
        private final ERectangle bound;

        RepeaterBound(ERectangle bound) { this.bound = bound; }

        @Override
        public FixpRectangle getBounds() { return FixpRectangle.from(bound); }
    }

	/**
	 * Class for analyzing the clock tree and adding repeaters.
	 */
	private class RoutePath
	{
		List<MakeConnection> path;
		RoutePath next1, next2;

		private RoutePath(List<MakeConnection> initialConnections, EPoint startPt, MakeConnection startMC, int treeDepth)
		{
			path = new ArrayList<MakeConnection>();
			if (startMC != null)
			{
				startMC.treeDepth = treeDepth;
				path.add(startMC);
			}

			List<MakeConnection> everyConnection = new ArrayList<MakeConnection>();
			for(MakeConnection mc : initialConnections) everyConnection.add(mc);
			for(;;)
			{
				MakeConnection mc1 = null, mc2 = null;
				for(MakeConnection mc : everyConnection)
				{
					if (mc.from.loc.equals(startPt) || mc.to.loc.equals(startPt))
					{
						if (mc1 == null) mc1 = mc; else mc2 = mc;
					}
				}
				if (mc1 == null) break;
				if (mc2 == null)
				{
					// extend by one point
					if (mc1.to.loc.equals(startPt)) mc1.swapEnds();
					startPt = mc1.to.loc;
					path.add(mc1);
					mc1.treeDepth = treeDepth;
					everyConnection.remove(mc1);
					continue;
				}

				// hit a branch
				if (mc1.to.loc.equals(startPt)) mc1.swapEnds();
				EPoint nextPt = mc1.to.loc;
				everyConnection.remove(mc1);
				next1 = new RoutePath(everyConnection, nextPt, mc1, treeDepth+1);

				if (mc2.to.loc.equals(startPt)) mc2.swapEnds();
				nextPt = mc2.to.loc;
				everyConnection.remove(mc2);
				next2 = new RoutePath(everyConnection, nextPt, mc2, treeDepth+1);
			}
		}

		private void addRepeaters(ClockPath cp, List<MakeConnection> everyConnection, double distToRepeater, List<MakePoint> everyPoint,
			Map<Integer,MutableInteger> levelCount)
		{
			for(int i=0; i<path.size(); i++)
			{
				MakeConnection mc = path.get(i);
				double dist = mc.from.loc.distance(mc.to.loc);
				if (dist >= distToRepeater)
				{
					double repeatLocX = mc.from.loc.getX(), repeatLocY = mc.from.loc.getY();
					if (mc.from.loc.getX() == mc.to.loc.getX())
					{
						double sign = 1;
						if (mc.from.loc.getY() > mc.to.loc.getY()) sign = -1;
						repeatLocY = mc.from.loc.getY() + distToRepeater * sign;
					} else if (mc.from.loc.getY() == mc.to.loc.getY())
					{
						double sign = 1;
						if (mc.from.loc.getX() > mc.to.loc.getX()) sign = -1;
						repeatLocX = mc.from.loc.getX() + distToRepeater * sign;
					} else
					{
						double angle = DBMath.figureAngleRadians(mc.from.loc, mc.to.loc);
						repeatLocX = mc.from.loc.getX() + distToRepeater * Math.cos(angle);
						repeatLocY = mc.from.loc.getY() + distToRepeater * Math.sin(angle);
//System.out.println("ANGLE FROM ("+mc.from.loc.getX()+","+mc.from.loc.getY()+") TO ("+mc.to.loc.getX()+","+mc.to.loc.getY()+") IS "+angle+
//	" SO "+distToRepeater+" ALONG THAT PATH IS ("+repeatLocX+","+repeatLocY+")");
					}
					boolean validRepeater = cp.repeaterCell != null;
//validRepeater = false;
					if (validRepeater)
					{
						// insert a repeater
						Cell rc = cp.repeaterCell;

						// get proper grid location of repeater and place it
						AlignRepeater ap = new AlignRepeater(repeatLocX, repeatLocY, rc);
						EPoint pt = ap.getLocation();
						double wid = rc.getDefWidth(ep);
						double hei = rc.getDefHeight(ep);
						ERectangle cellBound = rc.getBounds();
						double shiftX = cellBound.getMinX();
						double shiftY = cellBound.getMinY();
						pt = EPoint.fromLambda(pt.getX()-shiftX, pt.getY()-shiftY);
						Orientation orient = ap.getOrientation();
//if (orient != Orientation.IDENT) System.out.println("ORIENTATION IS "+orient.toString()+" AT ("+pt.getX()+","+pt.getY()+")");
						Integer key = Integer.valueOf(mc.treeDepth);
						MutableInteger levelIndex = levelCount.get(key);
						if (levelIndex == null) levelCount.put(key, levelIndex = new MutableInteger(0));
						levelIndex.increment();
						String instName = cp.repeaterInstancePrefix + "_L" + mc.treeDepth + "I" + levelIndex.intValue();
						String netName = cp.repeaterNetworkPrefix + "_L" + mc.treeDepth + "I" + levelIndex.intValue() + "_OUT";
						MakePoint mp = new MakePoint(rc, pt, wid, hei, orient, instName, ep, cell);
						ERectangle placed = mp.ni.getBounds();
						everyPoint.add(mp);
						PortInst piIn = mp.ni.findPortInstFromProto(cp.repeaterIn);
						PortInst piOut = mp.ni.findPortInstFromProto(cp.repeaterOut);
						EPoint inCtr = piIn.getCenter();
						EPoint outCtr = piOut.getCenter();
						MakePoint mpRepeaterIn = new MakePoint(new SerializablePortInst(piIn));
						MakePoint mpRepeaterOut = new MakePoint(new SerializablePortInst(piOut));

						// determine endpoints of wire being repeated
						double fromEndX = mc.from.loc.getX(), fromEndY = mc.from.loc.getY();
						double toEndX = mc.from.loc.getX(), toEndY = mc.from.loc.getY();
						if (mc.from.loc.getX() == mc.to.loc.getX())
						{
							if (mc.from.loc.getY() < pt.getY())
							{
								fromEndY = placed.getMinY();
								toEndY = placed.getMaxY();
							} else
							{
								fromEndY = placed.getMaxY();
								toEndY = placed.getMinY();
							}
						} else if (mc.from.loc.getY() == mc.to.loc.getY())
						{
							if (mc.from.loc.getX() < pt.getX())
							{
								fromEndX = placed.getMinX();
								toEndX = placed.getMaxX();
							} else
							{
								fromEndX = placed.getMaxX();
								toEndX = placed.getMinX();
							}
						}

						// determine bend point where wire turns to connect to repeater
						double fromBendX = fromEndX, fromBendY = fromEndY;
						double toBendX = toEndX, toBendY = toEndY;
						if (mc.from.loc.getX() == mc.to.loc.getX())
						{
							fromBendX = inCtr.getX();
							toBendX = outCtr.getX();
						} else if (mc.from.loc.getY() == mc.to.loc.getY())
						{
							fromBendY = inCtr.getY();
							toBendY = outCtr.getY();
						}

						// determine the layer to use for the repeater connections
						ArcProto conAP = mc.ap;
						double conWidth = mc.width;
						if (cp.repeaterArc != null)
						{
							conAP = cp.repeaterArc;
							conWidth = cp.repeaterArcWidth;
						}

						// make points where wire stops and gets repeated
						PrimitiveNode np = conAP.findPinProto();
						wid = np.getDefWidth(ep);
						hei = np.getDefHeight(ep);
						MakePoint mpIn = new MakePoint(np, EPoint.fromLambda(fromEndX, fromEndY), wid, hei, ep, cell);
						everyPoint.add(mpIn);
						MakePoint mpOut = new MakePoint(np, EPoint.fromLambda(toEndX, toEndY), wid, hei, ep, cell);
						everyPoint.add(mpOut);

						// make points where wire turns to enter repeater
						MakePoint mpBendIn = new MakePoint(np, EPoint.fromLambda(fromBendX, fromBendY), wid, hei, ep, cell);
						everyPoint.add(mpBendIn);
						MakePoint mpBendOut = new MakePoint(np, EPoint.fromLambda(toBendX, toBendY), wid, hei, ep, cell);
						everyPoint.add(mpBendOut);

						// build wires that rebuild path but stop around repeater
						MakeConnection mcIn = new MakeConnection(cp, mc.from, mpIn);
						MakeConnection mcOut = new MakeConnection(cp, mpOut, mc.to);
						mcIn.treeDepth = mc.treeDepth;
						mcOut.treeDepth = mc.treeDepth;
						everyConnection.add(mcIn);
						everyConnection.add(mcOut);

						// build wires that turn from endpoint to bend point
						MakeConnection mcBendIn = new MakeConnection(cp, mpIn, mpBendIn);
						mcBendIn.ap = conAP;
						mcBendIn.width = conWidth;
						mcBendIn.treeDepth = mc.treeDepth;
						MakeConnection mcBendOut = new MakeConnection(cp, mpBendOut, mpOut);
						mcBendOut.ap = conAP;
						mcBendOut.width = conWidth;
						mcBendOut.treeDepth = mc.treeDepth;
						everyConnection.add(mcBendIn);
						everyConnection.add(mcBendOut);

						// build wires that run from bend point to the repeater
						MakeConnection mcRepIn = new MakeConnection(cp, mpBendIn, mpRepeaterIn);
						mcRepIn.ap = conAP;
						mcRepIn.width = conWidth;
						mcRepIn.treeDepth = mc.treeDepth;
						MakeConnection mcRepOut = new MakeConnection(cp, mpRepeaterOut, mpBendOut);
						mcRepOut.ap = conAP;
						mcRepOut.width = conWidth;
						mcRepOut.treeDepth = mc.treeDepth;
						mcRepOut.netName = netName;
						everyConnection.add(mcRepIn);
						everyConnection.add(mcRepOut);

						// remove previous wire that didn't break for the repeater
						everyConnection.remove(mc);

						// insert the two wire halves into the path
						path.set(i, mcIn);
						path.add(i+1, mcOut);
						distToRepeater = cp.repeaterDistance;
					} else
					{
						// just split the arc and place an DRC node at the repeater location
						NodeProto np = Generic.tech().drcNode;
						double wid = np.getDefWidth(ep) * 50;
						double hei = np.getDefHeight(ep) * 50;
						EPoint pt = EPoint.fromLambda(repeatLocX, repeatLocY);
						MakePoint mp = new MakePoint(np, pt, wid, hei, ep, cell);
						everyPoint.add(mp);

						np = mc.ap.findPinProto();
						wid = np.getDefWidth(ep);
						hei = np.getDefHeight(ep);
						mp = new MakePoint(np, pt, wid, hei, ep, cell);
						everyPoint.add(mp);

						MakeConnection mcIn = new MakeConnection(cp, mc.from, mp);
						MakeConnection mcOut = new MakeConnection(cp, mp, mc.to);
						mcIn.treeDepth = mc.treeDepth;
						mcOut.treeDepth = mc.treeDepth;
						everyConnection.remove(mc);
						everyConnection.add(mcIn);
						everyConnection.add(mcOut);

						path.set(i, mcIn);
						path.add(i+1, mcOut);
						distToRepeater = cp.repeaterDistance;
					}
					continue;
				}
				distToRepeater = distToRepeater - dist;
			}
			if (next1 != null)
				next1.addRepeaters(cp, everyConnection, distToRepeater, everyPoint, levelCount);
			if (next2 != null)
				next2.addRepeaters(cp, everyConnection, distToRepeater, everyPoint, levelCount);
		}

//		private void dumpRoutePath(int depth, double soFar)
//		{
//			for(int i=0; i<depth; i++) System.out.print("  ");
//			System.out.print("PATH:");
//			boolean first = true;
//			for(MakeConnection mc : path)
//			{
//				double dist = mc.from.loc.distance(mc.to.loc);
//				soFar += dist;
//				if (first) System.out.print(" ("+mc.from.loc.getX()+","+mc.from.loc.getY()+")");
//				System.out.print(" to ("+mc.to.loc.getX()+","+mc.to.loc.getY()+")="+soFar);
//				first = false;
//			}
//			System.out.println();
//			if (next1 != null) next1.dumpRoutePath(depth+1, soFar);
//			if (next2 != null) next2.dumpRoutePath(depth+1, soFar);
//		}
	}

	private class SubTree
	{
		private String treeName;
		List<SerializablePortInst> connections;
		int inEdge, outEdge;
		SerializablePortInst output;
		List<MakePoint> allPoints;
		List<MakeConnection> allConnections;

		public SubTree()
		{
			connections = new ArrayList<SerializablePortInst>();
			allPoints = new ArrayList<MakePoint>();
			allConnections = new ArrayList<MakeConnection>();
		}

		public void setTreeName(String name)
		{
			treeName = name;
		}

		/**
		 * Method to construct the geometry for this level of the SubTree.
		 * @return true if the tree is good.
		 * False if it must be re-constructed
		 * (because a sub-level had to have serpentine geometry added to it, so it is necessary to start over).
		 */
		public boolean route(ClockPath curCP)
		{
			allConnections.clear();
			allPoints.clear();

			// figure out the size of the routing channel
			double lXPort=0, hXPort=0, lYPort=0, hYPort=0;
			double lXNodes=0, hXNodes=0, lYNodes=0, hYNodes=0;
			boolean first = true;
//System.out.println("SUBTREE "+treeName+" HAS "+connections.size()+" DESTINATIONS");
			for(SerializablePortInst spi : connections)
			{
				PortInst pi = spi.getPortInst();
				EPoint pt = pi.getCenter();
				ERectangle bound = spi.ni.getBounds();
//System.out.println("  HAS NODE "+spi.ni.describe(false)+" AT "+bound.getMinX()+"<=X<="+bound.getMaxX()+" AND "+bound.getMinY()+"<=Y<="+bound.getMaxY());
				if (first)
				{
					lXPort = hXPort = pt.getX();
					lYPort = hYPort = pt.getY();
					lXNodes = bound.getMinX();   hXNodes = bound.getMaxX();
					lYNodes = bound.getMinY();   hYNodes = bound.getMaxY();
					first = false;
				} else
				{
					if (pt.getX() < lXPort) lXPort = pt.getX();
					if (pt.getX() > hXPort) hXPort = pt.getX();
					if (pt.getY() < lYPort) lYPort = pt.getY();
					if (pt.getY() > hYPort) hYPort = pt.getY();
					if (bound.getMinX() < lXNodes) lXNodes = bound.getMinX();
					if (bound.getMaxX() > hXNodes) hXNodes = bound.getMaxX();
					if (bound.getMinY() < lYNodes) lYNodes = bound.getMinY();
					if (bound.getMaxY() > hYNodes) hYNodes = bound.getMaxY();
				}
			}
//System.out.println("SUBTREE "+treeName+" HAS PORT BOUNDS "+lXPort+"<=X<="+hXPort+" AND "+lYPort+"<=Y<="+hYPort);
//System.out.println("SUBTREE "+treeName+" HAS CELL BOUNDS "+lXNodes+"<=X<="+hXNodes+" AND "+lYNodes+"<=Y<="+hYNodes);

			// determine routing channel area
			double chanWid, chanHei;
			if (hXPort-lXPort > hYPort-lYPort)
			{
				// horizontal channel
				chanWid = hXNodes - lXNodes;
				chanHei = 10000;
			} else
			{
				// vertical channel
				chanWid = 10000;
				chanHei = hYNodes - lYNodes;
			}
			double lXChan=0, hXChan=0, lYChan=0, hYChan=0;
			switch (outEdge)
			{
				case LEFT_EDGE:
					lXChan = hXNodes;
					hXChan = lXChan + chanWid;
					lYChan = (lYNodes + hYNodes) / 2 - chanHei/2;
					hYChan = lYChan + chanHei;
					break;
				case RIGHT_EDGE:
					hXChan = lXNodes;
					lXChan = hXChan - chanWid;
					lYChan = (lYNodes + hYNodes) / 2 - chanHei/2;
					hYChan = lYChan + chanHei;
					break;
				case UP_EDGE:
					lXChan = (lXNodes + hXNodes) / 2 - chanWid/2;
					hXChan = lXChan + chanWid;
					hYChan = lYNodes;
					lYChan = hYChan - chanHei;
					break;
				case DOWN_EDGE:
					lXChan = (lXNodes + hXNodes) / 2 - chanWid/2;
					hXChan = lXChan + chanWid;
					lYChan = hYNodes;
					hYChan = lYChan + chanHei;
					break;
			}
//System.out.println("SUBTREE "+treeName+" HAS CHANNEL SIZE "+chanWid+"x"+chanHei+" BOUNDED "+lXChan+"<=X<="+hXChan+" AND "+lYChan+"<=Y<="+hYChan);

			// connect the inputs together
			Point2D mpSize = getContactSize(curCP.cornerContact, curCP.horizArc, curCP.horizArcWidth, curCP.vertArc, curCP.vertArcWidth);
			boolean horizontal = true;
			double stubX = 0, stubY = 0;
			switch (outEdge)
			{
				case LEFT_EDGE:
					horizontal = false;
					stubX = curCP.stubLength;
					break;
				case RIGHT_EDGE:
					horizontal = false;
					stubX = -curCP.stubLength;
					break;
				case UP_EDGE:
					stubY = -curCP.stubLength;
					break;
				case DOWN_EDGE:
					stubY = curCP.stubLength;
					break;
			}
			Collections.sort(connections, new SortConnections(horizontal));
			List<SerializablePortInst> reducedConnections = connections;
			while (reducedConnections.size() > 1)
			{
				// connect in pairs
				List<SerializablePortInst> newConnections = new ArrayList<SerializablePortInst>();
				for(int i=0; i<reducedConnections.size()-1; i += 2)
				{
					SerializablePortInst s1 = reducedConnections.get(i);
					SerializablePortInst s2 = reducedConnections.get(i+1);
					PortInst pi1 = s1.getPortInst();
					PortInst pi2 = s2.getPortInst();
					EPoint p1 = pi1.getCenter();
					EPoint p2 = pi2.getCenter();

					// determine the location of the stubs
					double cX1 = p1.getX() + stubX;
					double cY1 = p1.getY() + stubY;
					double dist1 = s1.distanceTraversed + Math.abs(stubX) + Math.abs(stubY);
					double cX2 = p2.getX() + stubX;
					double cY2 = p2.getY() + stubY;
					double dist2 = s2.distanceTraversed + Math.abs(stubX) + Math.abs(stubY);
					if (horizontal)
					{
						// horizontal connections
						if (cY1 != cY2)
						{
							if (outEdge == DOWN_EDGE)
							{
								// align to the top
								if (cY1 < cY2) { dist1 += (cY2 - cY1);   cY1 = cY2; } else
									{ dist2 += (cY1 - cY2);   cY2 = cY1; }
							} else
							{
								// align to the bottom
								if (cY2 < cY1) { dist1 += (cY1 - cY2);   cY1 = cY2; } else
									{ dist2 += (cY2 - cY1);   cY2 = cY1; } 
							}
						}
					} else
					{
						// align vertical connections
						if (cX1 != cX2)
						{
							if (outEdge == LEFT_EDGE)
							{
								// align to the right
								if (cX1 < cX2) { dist1 += (cX2 - cX1);   cX1 = cX2; } else
									{ dist2 += (cX1 - cX2);   cX2 = cX1; } 
							} else
							{
								// align to the left
								if (cX2 < cX1) { dist1 += (cX1 - cX2);   cX1 = cX2; } else
									{ dist2 += (cX2 - cX1);   cX2 = cX1; } 
							}
						}
					}

					// make the stub points
					EPoint bend1 = EPoint.fromLambda(cX1, cY1);
					EPoint bend2 = EPoint.fromLambda(cX2, cY2);

					MakePoint mp1 = new MakePoint(curCP.cornerContact, bend1, mpSize.getX(), mpSize.getY(), ep, cell);
					allPoints.add(mp1);
					MakePoint mp1a = new MakePoint(s1);
					allConnections.add(new MakeConnection(curCP, mp1, mp1a));
					MakePoint mp2 = new MakePoint(curCP.cornerContact, bend2, mpSize.getX(), mpSize.getY(), ep, cell);
					allPoints.add(mp2);
					MakePoint mp2a = new MakePoint(s2);
					allConnections.add(new MakeConnection(curCP, mp2, mp2a));

					// now find the location that makes the distances equal
					double pinX = 0, pinY = 0;
					double lengthDifference = Math.abs(dist1 - dist2);
					if (horizontal)
					{
						pinY = cY1;
						double separation = Math.abs(cX1 - cX2);
						if (lengthDifference <= separation)
						{
							// can be done directly
							pinX = (cX1 + cX2) / 2;
							if (cX1 > cX2) pinX -= (dist1 - dist2) / 2; else
								pinX -= (dist1 - dist2) / 2;
						} else
						{
							// needs serpentine work
							SerializablePortInst sToLenghten = dist1 < dist2 ? s1 : s2;
							if (sToLenghten.subTreeName != null)
							{
								SubTree subST = curCP.findSubTree(sToLenghten.subTreeName);
								if (subST != null)
								{
									SerializablePortInst newSPI = subST.addSerpentineAmount(curCP, lengthDifference);
									for(int j=0; j<connections.size(); j++)
										if (connections.get(j) == sToLenghten) connections.set(j, newSPI);
									for(MakePoint mp : allPoints) mp.ni.kill();
									return false;
								} else
								{
									System.out.println("HORIZONTAL SUBTREE "+treeName+" NEEDS TO MAKE "+sToLenghten.subTreeName+
										" SERPENTINE BY "+lengthDifference+" WHICH DOESN'T FIT IN "+separation);
								}
							}
						}
					} else
					{
						pinX = cX1;
						double separation = Math.abs(cY1 - cY2);
						if (lengthDifference <= separation)
						{
							// can be done directly
							pinY = (cY1 + cY2) / 2;
							if (cY1 > cY2) pinY -= (dist1 - dist2) / 2; else
								pinY -= (dist1 - dist2) / 2;
						} else
						{
							// needs serpentine work
							SerializablePortInst sToLenghten = dist1 < dist2 ? s1 : s2;
							if (sToLenghten.subTreeName != null)
							{
								SubTree subST = curCP.findSubTree(sToLenghten.subTreeName);
								if (subST != null)
								{
									SerializablePortInst newSPI = subST.addSerpentineAmount(curCP, lengthDifference);
									for(int j=0; j<connections.size(); j++)
										if (connections.get(j) == sToLenghten) connections.set(j, newSPI);
									for(MakePoint mp : allPoints) mp.ni.kill();
									return false;
								} else
								{
									System.out.println("VERTICAL SUBTREE "+treeName+" NEEDS TO MAKE "+sToLenghten.subTreeName+
										" SERPENTINE BY "+lengthDifference+" WHICH DOESN'T FIT IN "+separation);
								}
							}
						}
					}

//System.out.println("CONNECTING "+pi1.getNodeInst().describe(false)+" AT ("+p1.getX()+","+p1.getY()+") TO "+pi2.getNodeInst().describe(false)+" AT ("+p2.getX()+","+p2.getY()+")");
//System.out.println("  STUBBING THEM TO ("+cX1+","+cY1+"), DIST="+dist1+" AND ("+cX2+","+cY2+"), DIST="+dist2);
//System.out.println("  FINALLY, CONNECT THEM AT ("+pinX+","+pinY+")");
					EPoint pinLoc = EPoint.fromLambda(pinX, pinY);
					if (dist1 + pinLoc.distance(bend1) != dist2 + pinLoc.distance(bend2))
						System.out.println("HEY!!! "+dist1+" + "+pinLoc.distance(bend1)+" NOT EQUAL TO "+dist2+" + "+pinLoc.distance(bend2));

					MakePoint mpPin = new MakePoint(curCP.cornerContact, pinLoc, mpSize.getX(), mpSize.getY(), ep, cell);
					allPoints.add(mpPin);
					allConnections.add(new MakeConnection(curCP, mp1, mpPin));
					allConnections.add(new MakeConnection(curCP, mp2, mpPin));

					SerializablePortInst spiPin = new SerializablePortInst(mpPin.ni.getOnlyPortInst());
					spiPin.distanceTraversed = dist1 + pinLoc.distance(bend1);
					newConnections.add(spiPin);
				}
				if ((reducedConnections.size() & 1) != 0)
					newConnections.add(reducedConnections.get(reducedConnections.size()-1));

				// make the new connections list the real one
				reducedConnections = newConnections;
			}

			// now run the single reduced connection to the output edge
			SerializablePortInst sTop = reducedConnections.get(0);
			PortInst piTop = sTop.getPortInst();
			EPoint pTop = piTop.getCenter();

			// make the output node
			double outLocX=0, outLocY=0;
			boolean outHorizontal = false;
			switch (inEdge)
			{
				case LEFT_EDGE:
					if (!horizontal) outLocX = pTop.getX() - curCP.stubLength; else
						outLocX = lXChan;
					outLocY = pTop.getY();
					break;
				case RIGHT_EDGE:
					if (!horizontal) outLocX = pTop.getX() + curCP.stubLength; else
						outLocX = hXChan;
					outLocY = pTop.getY();
					break;
				case UP_EDGE:
					outLocX = pTop.getX();
					if (horizontal) outLocY = pTop.getY() + curCP.stubLength; else
						outLocY = hYChan;
					outHorizontal = true;
					break;
				case DOWN_EDGE:
					outLocX = pTop.getX();
					if (horizontal) outLocY = pTop.getY() - curCP.stubLength; else
						outLocY = lYChan;
					outHorizontal = true;
					break;
			}
			MakePoint mpTop = new MakePoint(sTop);
			if (outHorizontal != horizontal)
			{
				// routing channel makes bend: stub out the point before turning
				EPoint pTopShift = EPoint.fromLambda(pTop.getX() + stubX, pTop.getY() + stubY);
				MakePoint mp = new MakePoint(curCP.cornerContact, pTopShift, mpSize.getX(), mpSize.getY(), ep, cell);
				allPoints.add(mp);
				allConnections.add(new MakeConnection(curCP, mp, mpTop));
				outLocX += stubX;
				outLocY += stubY;
				sTop.distanceTraversed += Math.abs(stubX) + Math.abs(stubY);
				pTop = pTopShift;
				mpTop = mp;
			}

			EPoint finalPt = EPoint.fromLambda(outLocX, outLocY);
			PrimitiveNode stubNP = outHorizontal ? curCP.vertArc.findPinProto() : curCP.horizArc.findPinProto();
			MakePoint mp = new MakePoint(stubNP, finalPt, stubNP.getDefWidth(ep), stubNP.getDefHeight(ep), ep, cell);
			allPoints.add(mp);
			allConnections.add(new MakeConnection(curCP, mp, mpTop));
			output = new SerializablePortInst(mp.ni.getOnlyPortInst());
			output.distanceTraversed = sTop.distanceTraversed + finalPt.distance(pTop);
			output.subTreeName = treeName;
//System.out.println("SUBTREE "+treeName+" HAS OUTPUT AT ("+outLocX+","+outLocY+"), DISTANCE SO FAR="+output.distanceTraversed);
			return true;
		}

		public SerializablePortInst addSerpentineAmount(ClockPath curCP, double amount)
		{
			double jogX = 0, jogY = 0;
			switch (outEdge)
			{
				case LEFT_EDGE:  jogX = curCP.stubLength;    break;
				case RIGHT_EDGE: jogX = -curCP.stubLength;   break;
				case UP_EDGE:    jogY = -curCP.stubLength;   break;
				case DOWN_EDGE:  jogY = curCP.stubLength;    break;
			}
			double awaydist = (amount - (jogX+jogY)*2) / 2;
			double awayX = 0, awayY = 0;
			double extraX = 0, extraY = 0;
			switch (inEdge)
			{
				case LEFT_EDGE:  awayX = awaydist;    extraX = -curCP.stubLength;  break;
				case RIGHT_EDGE: awayX = -awaydist;   extraX = curCP.stubLength;   break;
				case UP_EDGE:    awayY = -awaydist;   extraY = curCP.stubLength;   break;
				case DOWN_EDGE:  awayY = awaydist;    extraY = -curCP.stubLength;  break;
			}
			EPoint outLoc = output.getPortInst().getCenter();

			EPoint pin1Loc = EPoint.fromLambda(outLoc.getX() + jogX,            outLoc.getY() + jogY);
			EPoint pin2Loc = EPoint.fromLambda(outLoc.getX() + jogX   + awayX,  outLoc.getY() + jogY   + awayY);
			EPoint pin3Loc = EPoint.fromLambda(outLoc.getX() + jogX*2 + awayX,  outLoc.getY() + jogY*2 + awayY);
			EPoint pin4Loc = EPoint.fromLambda(outLoc.getX() + jogX*2 + extraX, outLoc.getY() + jogY*2 + extraY);

			MakePoint mpOut = new MakePoint(output);
			Point2D mpSize = getContactSize(curCP.cornerContact, curCP.horizArc, curCP.horizArcWidth, curCP.vertArc, curCP.vertArcWidth);
			MakePoint mp1 = new MakePoint(curCP.cornerContact, pin1Loc, mpSize.getX(), mpSize.getY(), ep, cell);
			MakePoint mp2 = new MakePoint(curCP.cornerContact, pin2Loc, mpSize.getX(), mpSize.getY(), ep, cell);
			MakePoint mp3 = new MakePoint(curCP.cornerContact, pin3Loc, mpSize.getX(), mpSize.getY(), ep, cell);
			MakePoint mp4 = new MakePoint(curCP.cornerContact, pin4Loc, mpSize.getX(), mpSize.getY(), ep, cell);
			allPoints.add(mp1);
			allPoints.add(mp2);
			allPoints.add(mp3);
			allPoints.add(mp4);
			allConnections.add(new MakeConnection(curCP, mpOut, mp1));
			allConnections.add(new MakeConnection(curCP, mp1, mp2));
			allConnections.add(new MakeConnection(curCP, mp2, mp3));
			allConnections.add(new MakeConnection(curCP, mp3, mp4));
			SerializablePortInst newOutput = new SerializablePortInst(mp4.ni.getOnlyPortInst());
			newOutput.distanceTraversed = output.distanceTraversed + amount;
			output = newOutput;
			return output;
		}
	}

	private static class SortConnections implements Comparator<SerializablePortInst>
	{
		private boolean horizontal;

		SortConnections(boolean horizontal) { this.horizontal = horizontal; }

		public int compare(SerializablePortInst s1, SerializablePortInst s2)
		{
			EPoint p1 = s1.getPortInst().getCenter();
			EPoint p2 = s2.getPortInst().getCenter();
			if (horizontal) return Double.compare(p1.getX(), p2.getX());
			return Double.compare(p1.getY(), p2.getY());
		}
	}

	private static final int LEFT_EDGE  = 0;
	private static final int RIGHT_EDGE = 1;
	private static final int UP_EDGE    = 2;
	private static final int DOWN_EDGE  = 3;

	public int getDirectionName(String name)
	{
		if (name.equalsIgnoreCase("left")) return LEFT_EDGE;
		if (name.equalsIgnoreCase("right")) return RIGHT_EDGE;
		if (name.equalsIgnoreCase("up")) return UP_EDGE;
		if (name.equalsIgnoreCase("down")) return DOWN_EDGE;
		return -1;
	}

	private static class MakePoint
	{
		SerializablePortInst spi;
		NodeInst ni;
		EPoint loc;

		MakePoint(NodeProto np, EPoint loc, double wid, double hei, EditingPreferences ep, Cell cell)
		{
			this.loc = loc;
			ni = NodeInst.makeInstance(np, ep, loc, wid, hei, cell);
		}

		MakePoint(NodeProto np, EPoint loc, double wid, double hei, Orientation orient, String name, EditingPreferences ep, Cell cell)
		{
			this.loc = loc;
			ni = NodeInst.makeInstance(np, ep, loc, wid, hei, cell, orient, name);
		}

		MakePoint(SerializablePortInst spi)
		{
			loc = spi.getPortInst().getCenter();
			this.spi = spi;
		}
	}

	private class MakeConnection
	{
		ArcProto ap;
		ArcInst ai;
		double width;
		MakePoint from, to;
		int treeDepth;
		String netName;

		MakeConnection(ClockPath cp, MakePoint from, MakePoint to)
		{
			ap = null;
			width = 0;
			treeDepth = 0;
			netName = null;
			if (from.loc.getX() == to.loc.getX())
			{
				// vertical wire
				ap = cp.vertArc;
				width = cp.getArcWidth(ap);
			} else if (from.loc.getY() == to.loc.getY())
			{
				// horizontal wire
				ap = cp.horizArc;
				width = cp.getArcWidth(ap);
			}
			if (ap == null)
			{
				System.out.println("WARNING: Connection from (" + from.loc.getX() + "," + from.loc.getY() + ") to (" +
					to.loc.getX() + "," + to.loc.getY() + ") is nonManhattan");
				ap = cp.horizArc;
				width = cp.getArcWidth(ap);
			}
			this.from = from;
			this.to = to;
		}

		public void swapEnds()
		{
			MakePoint swap = from;
			from = to;
			to = swap;
		}
	}

	private void placeArcs(ClockPath cp, List<MakeConnection> connections, List<MakePoint> points, Cell cell)
	{
		// now build all the scheduled geometry
		for(int i=0; i<connections.size(); i++)
		{
			MakeConnection mc = connections.get(i);
			if (mc.ap == null) { System.out.println("CANNOT PLACE ARC!");  continue; }
			MakePoint mp1 = ensureArcConnectsToPort(cp, mc, mc.from, connections, points);
			if (mp1 == null) { System.out.println("CANNOT PLACE ARC, FROM END!");  continue; }
			MakePoint mp2 = ensureArcConnectsToPort(cp, mc, mc.to, connections, points);
			if (mp2 == null) { System.out.println("CANNOT PLACE ARC, TO END!");  continue; }
			PortInst pi1 = mp1.spi != null ? mp1.spi.getPortInst() : mp1.ni.getOnlyPortInst();
			PortInst pi2 = mp2.spi != null ? mp2.spi.getPortInst() : mp2.ni.getOnlyPortInst();
			mc.ai = ArcInst.makeInstanceBase(mc.ap, ep, mc.width, pi1, pi2);
			if (mc.ai == null)
			{
				System.out.println("DID NOT MAKE ARC");
				continue;
			}
			if (mc.netName != null) mc.ai.setName(mc.netName, ep);
		}
	}

	private MakePoint ensureArcConnectsToPort(ClockPath cp, MakeConnection mc, MakePoint mp, List<MakeConnection> connections, List<MakePoint> points)
	{
		if (mc.ap == null) return mp;
		PortInst pi = mp.spi != null ? mp.spi.getPortInst() : mp.ni.getOnlyPortInst();
		if (pi.getPortProto().connectsTo(mc.ap)) return mp;
		EPoint stackLoc = pi.getCenter();

		// find the two levels that must be bridged
		int destinationLevel = mc.ap.getFunction().getLevel();
		int bestDist = Integer.MAX_VALUE;
		int sourceLevel = -1;
		ArcProto [] portConnections = pi.getPortProto().getBasePort().getConnections();
		for(int i=0; i<portConnections.length; i++)
		{
			ArcProto apAlt = portConnections[i];
			if (apAlt.getTechnology() == Generic.tech()) continue;
			int levelAlt = apAlt.getFunction().getLevel();
			if (levelAlt < 0) continue;
			int dist = Math.abs(destinationLevel - levelAlt);
			if (dist < bestDist)
			{
				bestDist = dist;
				sourceLevel = levelAlt;
			}
		}
		if (destinationLevel == sourceLevel) return mp;

		// bridge the levels with a contact stack
		int dir = (destinationLevel - sourceLevel) / Math.abs(destinationLevel - sourceLevel);
		int sl = sourceLevel, dl = destinationLevel;
		if (dir > 0) { sl++; dl++; }
//System.out.println("CONNECTING source node " + pi.getNodeInst().describe(false) +" TO ARC "+ap.describe());
//System.out.println("SOURCE LEVEL "+sourceLevel+", DESTINATION LEVEL "+destinationLevel+" SL="+sl+", DL="+dl);
		for(int i=sl; i != dl; i += dir)
		{
			PrimitiveNode connection = findContact(tech, i-1, i);
			if (connection == null)
			{
				System.out.println("Warning: Cannot bring source node " + pi.getNodeInst().describe(false) +
					" up to Metal-" + destinationLevel + " because there is no Metal-" + (i-1) + "-to-Metal-" + i + " contact in technology " +
						tech.getTechName());
				return null;
			}
			ArcProto arcIn = null, arcOut = null;
			ArcProto[] possibleCons = connection.getPort(0).getConnections();
			for(int j=0; j<possibleCons.length; j++)
			{
				if (possibleCons[j].getTechnology() == Generic.tech()) continue;
				if (possibleCons[j].getFunction().getLevel() == i-1) arcIn = possibleCons[j];
				if (possibleCons[j].getFunction().getLevel() == i) arcOut = possibleCons[j];
			}
			if (dir < 0) arcIn = arcOut;
//System.out.println("PLACE "+connection.describe(false)+" AT ("+stackLoc.getX()+","+stackLoc.getY()+"), CONNECT WITH "+arcIn.describe());
			Point2D mpSize = getContactSize(connection, arcIn, cp.getArcWidth(arcIn), arcOut, cp.getArcWidth(arcOut));
			MakePoint mpNew = new MakePoint(connection, stackLoc, mpSize.getX(), mpSize.getY(), ep, cell);
			points.add(mpNew);
			MakeConnection mcNew = new MakeConnection(cp, mp, mpNew);
			mcNew.ap = arcIn;
			mcNew.width = cp.getArcWidth(arcIn);
			connections.add(mcNew);
			mp = mpNew;
		}
		return mp;
	}

	public void connectToSource(ClockPath cp, SubTree topTree, SerializablePortInst sourceSPI)
	{
		PortInst sourcePI = sourceSPI.getPortInst();
		EPoint sourcePT = sourcePI.getCenter();
		SerializablePortInst destSPI = topTree.output;
		MakePoint sourceMP = new MakePoint(sourceSPI);
		MakePoint destMP = new MakePoint(destSPI);

		PortInst destPI = destSPI.getPortInst();
		EPoint destPT = destPI.getCenter();
		sourcePT = sourcePI.getCenter();
		if (destPT.getX() != sourcePT.getX() && destPT.getY() != sourcePT.getY())
		{
			EPoint ctr;
			if (topTree.inEdge == LEFT_EDGE || topTree.inEdge == RIGHT_EDGE)
			{
				ctr = EPoint.fromLambda(destPT.getX(), sourcePT.getY());
			} else
			{
				ctr = EPoint.fromLambda(sourcePT.getX(), destPT.getY());
			}
			Point2D mpSize = getContactSize(cp.cornerContact, cp.horizArc, cp.horizArcWidth, cp.vertArc, cp.vertArcWidth);
			MakePoint mp = new MakePoint(cp.cornerContact, ctr, mpSize.getX(), mpSize.getY(), ep, cell);
			topTree.allPoints.add(mp);
			topTree.allConnections.add(new MakeConnection(cp, sourceMP, mp));
			topTree.allConnections.add(new MakeConnection(cp, destMP, mp));
		} else
		{
			topTree.allConnections.add(new MakeConnection(cp, destMP, sourceMP));
		}
		sourceMP.spi.distanceTraversed = destSPI.distanceTraversed + Math.abs(destPT.getX() - sourcePT.getX()) + Math.abs(destPT.getY() - sourcePT.getY());
		topTree.output = sourceMP.spi;
	}

	private double convertToUnits(String val)
	{
		double v = TextUtils.atof(val) / scaleUnits;
		return com.sun.electric.database.text.TextUtils.convertFromDistance(v, tech, TextUtils.UnitScale.MICRO);
	}

	// code taken from tool.io.input.DEF.java (combine, please)
	private Orientation getOrientation(String key)
	{
		int angle;
		boolean transpose = false;
		if (key.equalsIgnoreCase("N"))  { angle = 0;    } else
		if (key.equalsIgnoreCase("S"))  { angle = 1800; } else
		if (key.equalsIgnoreCase("E"))  { angle = 2700; } else
		if (key.equalsIgnoreCase("W"))  { angle = 900;  } else
		if (key.equalsIgnoreCase("FN")) { angle = 900;   transpose = true; } else
		if (key.equalsIgnoreCase("FS")) { angle = 2700;  transpose = true; } else
		if (key.equalsIgnoreCase("FE")) { angle = 1800;  transpose = true; } else
		if (key.equalsIgnoreCase("FW")) { angle = 0;     transpose = true; } else return null;
		return Orientation.fromC(angle, transpose);
	}

	// ***************************************** ALGORITHM 1 *****************************************

//	private static class PortPair
//	{
//		SerializablePortInst s1, s2;
//
//		PortPair(SerializablePortInst s1, SerializablePortInst s2)
//		{
//			this.s1 = s1;
//			this.s2 = s2;
//		}
//	}
//
//	/**
//	 * The simplest algorithm: takes one source and 2^n destinations and automatically finds the path to each.
//	 * @param cell the Cell in which to route
//	 * @param nht the NeededHTree information for routing.
//	 * @return true if the routing was successful.
//	 */
//	private boolean routeAlgorithm1(NeededHTree nht, Cell cell)
//	{
//		// make sure there is a power-of-2 number of destinations
//		int numDests = nht.destinations.size();
//		int numOnes = 0;
//		for(int i=0; i<32; i++)
//		{
//			if ((numDests & (1<<i)) != 0) numOnes++;
//		}
//		if (numOnes != 1)
//		{
//			System.out.println("ERROR: Network " + nht + " has " + numDests + " destinations which is not a power of 2");
//			return false;
//		}
//
//		// find the arc which connects all points
//		Set<ArcProto> possibleArcs = null;
//		for(SerializablePortInst spi : nht.destinations)
//		{
//			ArcProto[] arcs = spi.getPortProto().getBasePort().getConnections();
//			if (possibleArcs == null)
//			{
//				// first time: fill the array
//				possibleArcs = new HashSet<ArcProto>();
//				for(int i=0; i<arcs.length; i++)
//				{
//					ArcProto ap = arcs[i];
//					if (ap.getTechnology() == Generic.tech()) continue;
//					possibleArcs.add(ap);
//				}
//			} else
//			{
//				// next time: reduce the array
//				List<ArcProto> removeThese = new ArrayList<ArcProto>();
//				for(ArcProto ap : possibleArcs)
//				{
//					boolean found = false;
//					for(int i=0; i<arcs.length; i++)
//					{
//						if (ap == arcs[i]) { found = true;   break; }
//					}
//					if (!found) removeThese.add(ap);
//				}
//				for(ArcProto ap : removeThese) possibleArcs.remove(ap);
//			}
//		}
//		if (possibleArcs.size() == 0)
//		{
//			System.out.println("ERROR: Cannot find a common arc to connect all points");
//			return false;
//		}
//		ArcProto connectingArc = possibleArcs.iterator().next();
//		System.out.println("Routing on arc " + connectingArc.describe());
//
//		boolean found = false;
//		ArcProto[] connections = nht.source.getPortProto().getBasePort().getConnections();
//		for(int i=0; i<connections.length; i++)
//		{
//			if (connections[i] == connectingArc) { found = true;   break; }
//		}
//		if (!found)
//		{
//			System.out.println("Must bring source up to layer "+connectingArc.describe());
//		}
//
//		MutableBoolean mb = new MutableBoolean(true);
//		Boolean horizontal = Boolean.FALSE;
//		double widthFactor = 50;
//		while(nht.destinations.size() > 1)
//		{
//			List<NodeInst> niList = reduceTreeLevel(nht, cell, connectingArc, horizontal, mb, widthFactor);
//			if (horizontal == null) horizontal = Boolean.valueOf(!mb.booleanValue()); else
//				horizontal = Boolean.valueOf(!horizontal.booleanValue());
//			widthFactor *= 2;
//			nht.destinations.clear();
//			for(NodeInst ni : niList)
//				nht.destinations.add(new SerializablePortInst(ni.getOnlyPortInst()));
//		}
//
//		// delete original arcs and place an unrouted arc to the center of the H Tree
//		PortInst sourcePi = nht.destinations.get(0).getPortInst();
//		PortInst treePi = nht.source.getPortInst();
//
//		EPoint sourcePt = sourcePi.getCenter();
//		EPoint treePt = treePi.getCenter();
//		double cX, cY;
//		if (horizontal.booleanValue())
//		{
//			cX = treePt.getX();
//			cY = sourcePt.getY();
//		} else
//		{
//			cX = sourcePt.getX();
//			cY = treePt.getY();
//		}
//		PrimitiveNode np = connectingArc.findPinProto();
//		double width = np.getDefWidth(ep);
//		double height = np.getDefHeight(ep);
//		NodeInst ni = NodeInst.makeInstance(np, ep, EPoint.fromLambda(cX, cY), width, height, cell);
//		double arcWidth = connectingArc.getDefaultLambdaBaseWidth(ep) * widthFactor;
//		ArcInst ai1 = ArcInst.makeInstanceBase(connectingArc, ep, arcWidth, sourcePi, ni.getOnlyPortInst());
//		ArcInst ai2 = ArcInst.makeInstanceBase(connectingArc, ep, arcWidth, treePi, ni.getOnlyPortInst());
//		if (ai1 != null) ai1.setHeadExtended(false);
//		if (ai2 != null) ai2.setTailExtended(false);
//		return true;
//	}
//
//	private double nonEuclideanDistance(EPoint pt1, EPoint pt2)
//	{
//		return Math.abs(pt1.getX() - pt2.getX()) + Math.abs(pt1.getY() - pt2.getY());
//	}
//
//	private List<NodeInst> reduceTreeLevel(NeededHTree nht, Cell cell, ArcProto connectingArc, Boolean horizontal, MutableBoolean didHorizontal, double widthFactor)
//	{
//		// make a collection of distances and the two ports that are connected at those distances
//		Map<Double,List<PortPair>> allConnections = new TreeMap<Double,List<PortPair>>();
//		for(int i=0; i<nht.destinations.size(); i++)
//		{
//			SerializablePortInst spi1 = nht.destinations.get(i);
//			PortInst pi1 = spi1.getPortInst();
//			EPoint pt1 = pi1.getCenter();
//			for(int j=i+1; j<nht.destinations.size(); j++)
//			{
//				SerializablePortInst spi2 = nht.destinations.get(j);
//				PortInst pi2 = spi2.getPortInst();
//				EPoint pt2 = pi2.getCenter();
//				double distance = nonEuclideanDistance(pt1, pt2);
//				if (horizontal != null)
//				{
//					if (horizontal.booleanValue())
//					{
//						if (Math.abs(pt1.getY() - pt2.getY()) > Math.abs(pt1.getX() - pt2.getX()))
//							distance += Math.abs(pt1.getY() - pt2.getY()) * 100;
//					} else
//					{
//						if (Math.abs(pt1.getX() - pt2.getX()) > Math.abs(pt1.getY() - pt2.getY()))
//							distance += Math.abs(pt1.getX() - pt2.getX()) * 100;
//					}
//				}
//				Double dist = new Double(distance);
//				List<PortPair> pairsThisDist = allConnections.get(dist);
//				if (pairsThisDist == null) allConnections.put(dist, pairsThisDist = new ArrayList<PortPair>());
//				pairsThisDist.add(new PortPair(spi1, spi2));
//			}
//		}
//
//		// mark all ports unseen
//		for(SerializablePortInst spi : nht.destinations) spi.flag = false;
//		List<PortPair> lowestList = new ArrayList<PortPair>();
//
//		// collect close ports
//		int portsSelected = 0;
//		for(Double d : allConnections.keySet())
//		{
//			List<PortPair> pairsThisDist = allConnections.get(d);
//
//			Map<SerializablePortInst,MutableInteger> found = new HashMap<SerializablePortInst,MutableInteger>();
//			int highestCount = 0;
//			for(PortPair pp : pairsThisDist)
//			{
//				MutableInteger mi = found.get(pp.s1);
//				if (mi == null) found.put(pp.s1, mi = new MutableInteger(0));
//				mi.increment();
//				if (mi.intValue() > highestCount) highestCount = mi.intValue();
//
//				mi = found.get(pp.s2);
//				if (mi == null) found.put(pp.s2, mi = new MutableInteger(0));
//				mi.increment();
//				if (mi.intValue() > highestCount) highestCount = mi.intValue();
//			}
//
//			for(int i=1; i<=highestCount; i++)
//			{
//				for(PortPair pp : pairsThisDist)
//				{
//					if (pp.s1.flag || pp.s2.flag) continue;
//					if (found.get(pp.s1).intValue() > i && found.get(pp.s2).intValue() > i) continue;
//					lowestList.add(pp);
//					pp.s1.flag = pp.s2.flag = true;
//					portsSelected += 2;
//				}
//			}
//
//			if (portsSelected == nht.destinations.size()) break;
//		}
//
//		// find the largest distance between points
//		double biggestDist = 0;
//		for(PortPair pp : lowestList)
//		{
//			double dist = nonEuclideanDistance(pp.s1.getPortInst().getCenter(), pp.s2.getPortInst().getCenter());
//			if (dist > biggestDist) biggestDist = dist;
//
//			// determine horizontal/vertical orientation of this pair
//			PortInst pi1 = pp.s1.getPortInst();
//			EPoint pt1 = pi1.getCenter();
//			PortInst pi2 = pp.s2.getPortInst();
//			EPoint pt2 = pi2.getCenter();
//			didHorizontal.setValue(Math.abs(pt1.getX() - pt2.getX()) > Math.abs(pt1.getY() - pt2.getY()));
//		}
//
//		// route the points
//		List<NodeInst> reducedPoints = new ArrayList<NodeInst>();
//		PrimitiveNode np = connectingArc.findPinProto();
//		double width = np.getDefWidth(ep);
//		double height = np.getDefHeight(ep);
//		double arcWidth = connectingArc.getDefaultLambdaBaseWidth(ep) * widthFactor;
//		for(PortPair pp : lowestList)
//		{
//			PortInst pi1 = pp.s1.getPortInst();
//			EPoint pt1 = pi1.getCenter();
//			PortInst pi2 = pp.s2.getPortInst();
//			EPoint pt2 = pi2.getCenter();
//			double dist = nonEuclideanDistance(pt1, pt2);
//			if (dist == biggestDist)
//			{
//				// make a direct connection
//				double cX = (pt1.getX() + pt2.getX()) / 2;
//				double cY = (pt1.getY() + pt2.getY()) / 2;
//				NodeInst ni = NodeInst.makeInstance(np, ep, EPoint.fromLambda(cX, cY), width, height, cell);
//				ArcInst ai1 = ArcInst.makeInstanceBase(connectingArc, ep, arcWidth, pi1, ni.getOnlyPortInst());
//				ArcInst ai2 = ArcInst.makeInstanceBase(connectingArc, ep, arcWidth, pi2, ni.getOnlyPortInst());
//				ai1.setHeadExtended(false);   ai1.setTailExtended(false);
//				ai2.setHeadExtended(false);   ai2.setTailExtended(false);
//				reducedPoints.add(ni);
//			} else
//			{
//				// pad the connection to make the length proper
//				double extraAmount = (biggestDist - dist) / 2;
//				double x1, y1, x2, y2;
//				double x1s, y1s, x2s, y2s;
//				if (Math.abs(pt1.getX() - pt2.getX()) > Math.abs(pt1.getY() - pt2.getY()))
//				{
//					// vertical shift
//					double quarterDist = Math.abs(pt1.getX() - pt2.getX()) / 4;
//					y1 = pt1.getY();
//					y2 = pt2.getY();
//					if (pt1.getX() > pt2.getX())
//					{
//						x1 = pt1.getX() - quarterDist;
//						x2 = pt2.getX() + quarterDist;
//					} else
//					{
//						x1 = pt1.getX() + quarterDist;
//						x2 = pt2.getX() - quarterDist;
//					}
//					x1s = x1;
//					y1s = y1 + extraAmount;
//					x2s = x2;
//					y2s = y2 + extraAmount;
//				} else
//				{
//					// horizontal shift
//					double quarterDist = Math.abs(pt1.getY() - pt2.getY()) / 4;
//					x1 = pt1.getX();
//					x2 = pt2.getX();
//					if (pt1.getY() > pt2.getY())
//					{
//						y1 = pt1.getY() - quarterDist;
//						y2 = pt2.getY() + quarterDist;
//					} else
//					{
//						y1 = pt1.getY() + quarterDist;
//						y2 = pt2.getY() - quarterDist;
//					}
//					x1s = x1 + extraAmount;
//					y1s = y1;
//					x2s = x2 + extraAmount;
//					y2s = y2;
//				}
//				NodeInst ni1 = NodeInst.makeInstance(np, ep, EPoint.fromLambda(x1, y1), width, height, cell);
//				NodeInst ni2 = NodeInst.makeInstance(np, ep, EPoint.fromLambda(x2, y2), width, height, cell);
//				NodeInst ni1s = NodeInst.makeInstance(np, ep, EPoint.fromLambda(x1s, y1s), width, height, cell);
//				NodeInst ni2s = NodeInst.makeInstance(np, ep, EPoint.fromLambda(x2s, y2s), width, height, cell);
//				ArcInst.makeInstanceBase(connectingArc, ep, arcWidth, pi1, ni1.getOnlyPortInst());
//				ArcInst.makeInstanceBase(connectingArc, ep, arcWidth, pi2, ni2.getOnlyPortInst());
//				ArcInst.makeInstanceBase(connectingArc, ep, arcWidth, ni1.getOnlyPortInst(), ni1s.getOnlyPortInst());
//				ArcInst.makeInstanceBase(connectingArc, ep, arcWidth, ni2.getOnlyPortInst(), ni2s.getOnlyPortInst());
//
//				double cX = (x1s + x2s) / 2;
//				double cY = (y1s + y2s) / 2;
//				NodeInst ni = NodeInst.makeInstance(np, ep, EPoint.fromLambda(cX, cY), width, height, cell);
//				ArcInst ai1 = ArcInst.makeInstanceBase(connectingArc, ep, arcWidth, ni1s.getOnlyPortInst(), ni.getOnlyPortInst());
//				ArcInst ai2 = ArcInst.makeInstanceBase(connectingArc, ep, arcWidth, ni2s.getOnlyPortInst(), ni.getOnlyPortInst());
//				ai1.setHeadExtended(false);   ai1.setTailExtended(false);
//				ai2.setHeadExtended(false);   ai2.setTailExtended(false);
//				reducedPoints.add(ni);
//			}
//		}
//
//		return reducedPoints;
//	}
}
