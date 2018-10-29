/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: PlacementAdapter.java
 *
 * Copyright (c) 2009, Static Free Software. All rights reserved.
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
package com.sun.electric.tool.placement;

import com.sun.electric.database.EditingPreferences;
import com.sun.electric.database.ImmutableArcInst;
import com.sun.electric.database.geometry.EPoint;
import com.sun.electric.database.geometry.ERectangle;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.Export;
import com.sun.electric.database.hierarchy.Library;
import com.sun.electric.database.prototype.NodeProto;
import com.sun.electric.database.prototype.PortCharacteristic;
import com.sun.electric.database.prototype.PortProto;
import com.sun.electric.database.text.Version;
import com.sun.electric.database.topology.ArcInst;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.topology.PortInst;
import com.sun.electric.database.topology.SteinerTree;
import com.sun.electric.database.topology.SteinerTree.SteinerTreePort;
import com.sun.electric.database.topology.SteinerTree.SteinerTreePortPair;
import com.sun.electric.database.variable.TextDescriptor;
import com.sun.electric.database.variable.Variable;
import com.sun.electric.technology.technologies.Generic;
import com.sun.electric.technology.technologies.Schematics;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.placement.PlacementFrame.PlacementNetwork;
import com.sun.electric.tool.placement.forceDirected1.PlacementForceDirectedTeam5;
import com.sun.electric.tool.placement.forceDirected2.PlacementForceDirectedStaged;
import com.sun.electric.tool.placement.general.BottomUpPartition;
import com.sun.electric.tool.placement.general.BottomUpPlace;
import com.sun.electric.tool.placement.general.Control;
import com.sun.electric.tool.placement.general.FDRowCol;
import com.sun.electric.tool.placement.general.SARowCol;
import com.sun.electric.tool.placement.genetic1.g1.GeneticPlacement;
import com.sun.electric.tool.placement.genetic2.PlacementGenetic;
import com.sun.electric.tool.placement.metrics.AbstractMetric;
import com.sun.electric.tool.placement.metrics.boundingbox.BBMetric;
import com.sun.electric.tool.placement.metrics.mst.MSTMetric;
import com.sun.electric.tool.placement.simulatedAnnealing1.SimulatedAnnealing;
import com.sun.electric.tool.placement.simulatedAnnealing2.PlacementSimulatedAnnealing;
import com.sun.electric.util.ElapseTimer;
import com.sun.electric.util.TextUtils;
import com.sun.electric.util.math.DBMath;
import com.sun.electric.util.math.Orientation;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PlacementExport describes exports in the cell. Placement algorithms do not
 * usually need this information: it exists as a way to communicate the
 * information internally.
 */
public class PlacementAdapter {

	private static final Logger logger = LoggerFactory.getLogger(PlacementAdapter.class);

	/**
	 * Class to define a node that is being placed. This is a shadow class for
	 * the internal Electric object "NodeInst". There are minor differences
	 * between PlacementNode and NodeInst, for example, PlacementNode is
	 * presumed to be centered in the middle, with port offsets based on that
	 * center, whereas the NodeInst has a cell-center that may not be in the
	 * middle.
	 */
	public static class PlacementNode extends PlacementFrame.PlacementNode {
		private final NodeInst originalNode;
		private final NodeProto original;
		private final String nodeName;
		private final int techBits;
		private final double width, height;
		private final List<PlacementFrame.PlacementPort> ports;
		private Map<String, Object> addedVariables;
		private final boolean terminal;
		private Map<PlacementPort,Set<PlacementPort>> equivPorts;

		/**
		 * Method to create a PlacementNode object.
		 * The original object must be defined by giving either the NodeInst or the NodeProto.
		 * @param ni the original NodeInst from which this PlacementNode is derived (may be null).
		 * @param type the original Electric type of this PlacementNode (may be null).
		 * @param name the name to give the node once placed (may be null).
		 * @param tBits the technology-specific bits of this PlacementNode
		 *            (typically 0 except for specialized Schematics components).
		 * @param wid the width of this PlacementNode.
		 * @param hei the height of this PlacementNode.
		 * @param pps a list of PlacementPort on the PlacementNode, indicating connection locations.
		 * @param terminal
		 */
		public PlacementNode(NodeInst ni, NodeProto type, String name, int tBits, double wid, double hei,
				List<PlacementPort> pps, boolean terminal) {
			originalNode = ni;
			original = type;
			nodeName = name;
			techBits = tBits;

			// make sure that odd-sized width/height (in grid units) is bumped up to be even so that half width/height is correct
			long gridWidth = (long)Math.ceil(wid * DBMath.GRID);
			if ((gridWidth&1) != 0) gridWidth++;
			width = gridWidth / DBMath.GRID;

			long gridHeight = (long)Math.ceil(hei * DBMath.GRID);
			if ((gridHeight&1) != 0) gridHeight++;
			height = gridHeight / DBMath.GRID;

			ports = new ArrayList<PlacementFrame.PlacementPort>(pps);
			equivPorts = new HashMap<PlacementPort,Set<PlacementPort>>();
			this.terminal = terminal;
		}

		/**
		 * Method to indicate that two ports are equivalent on a node,
		 * and therefore either one can be wired.
		 * @param p1 the first equivalent PlacementPort.
		 * @param p2 the second equivalent PlacementPort.
		 */
		public void addEquivalentPorts(PlacementPort p1, PlacementPort p2)
		{
			Set<PlacementPort> list1 = equivPorts.get(p1);
			if (list1 == null) equivPorts.put(p1, list1 = new HashSet<PlacementPort>());
			list1.add(p2);

			Set<PlacementPort> list2 = equivPorts.get(p2);
			if (list2 == null) equivPorts.put(p2, list2 = new HashSet<PlacementPort>());
			list2.add(p1);
		}

		public Set<PlacementPort> getEquivalents(PlacementPort p) { return equivPorts.get(p); }

		/**
		 * Method to add variables to this PlacementNode. Variables are extra
		 * name/value pairs, for example a transistor width and length.
		 * 
		 * @param name
		 *            the name of the variable to add.
		 * @param value
		 *            the value of the variable to add.
		 */
		public void addVariable(String name, Object value) {
			if (addedVariables == null)
				addedVariables = new HashMap<String, Object>();
			addedVariables.put(name, value);
		}

		/**
		 * Method to return the NodeProto of this PlacementNode.
		 * 
		 * @return the NodeProto of this PlacementNode.
		 */
		public NodeProto getType() {
			if (original != null) return original;
			if (originalNode != null) return originalNode.getProto();
			return null;
		}

		/**
		 * Method to return the original NodeInst of this PlacementNode.
		 * @return the original NodeInst of this PlacementNode (may be null).
		 */
		public NodeInst getOriginal() {
			return originalNode;
		}

		/**
		 * Method to return the name of NodeProto of this PlacementNode.
		 * 
		 * @return the name NodeProto of this PlacementNode.
		 */
		public String getTypeName() {
			if (original != null) return original.getName();
			if (originalNode != null) return originalNode.getProto().getName();
			return null;
		}

		/**
		 * Method to return a list of PlacementPorts on this PlacementNode.
		 * 
		 * @return a list of PlacementPorts on this PlacementNode.
		 */
		public List<PlacementFrame.PlacementPort> getPorts() {
			return ports;
		}

		/**
		 * Method to return the technology-specific information of this
		 * PlacementNode.
		 * 
		 * @return the technology-specific information of this PlacementNode
		 *         (typically 0 except for specialized Schematics components).
		 */
		public int getTechBits() {
			return techBits;
		}

		/**
		 * Method to return the width of this PlacementNode.
		 * 
		 * @return the width of this PlacementNode.
		 */
		@Override
		public double getWidth() {
			return width;
		}

		/**
		 * Method to return the height of this PlacementNode.
		 * 
		 * @return the height of this PlacementNode.
		 */
		@Override
		public double getHeight() {
			return height;
		}

		/**
		 * @return the terminal
		 */
		public boolean isTerminal() {
			return terminal;
		}

		@Override
		public String toString() {
			if (originalNode != null) return originalNode.describe(false);
			String name = original.describe(false);
			if (nodeName != null)
				name += "[" + nodeName + "]";
			if (getTechBits() != 0)
				name += "(" + getTechBits() + ")";
			return name;
		}
	}

	/**
	 * Class to define ports on PlacementNode objects. This is a shadow class
	 * for the internal Electric object "PortInst".
	 */
	public static class PlacementPort extends PlacementFrame.PlacementPort {
		private PortProto proto;

		/**
		 * Constructor to create a PlacementPort.
		 * 
		 * @param x the X offset of this PlacementPort from the center of its PlacementNode.
		 * @param y the Y offset of this PlacementPort from the center of its PlacementNode.
		 * @param pp the Electric PortProto of this PlacementPort.
		 */
		public PlacementPort(double x, double y, PortProto pp) {
			super(x, y);
			proto = pp;
		}

		/**
		 * Method to return the Electric PortProto that this PlacementPort uses.
		 * 
		 * @return the Electric PortProto that this PlacementPort uses.
		 */
		public PortProto getPortProto() {
			return proto;
		}

		public String toString() {
			return proto.getName();
		}
	}

	/**
	 * Class to define an Export that will be placed in the circuit.
	 */
	public static class PlacementExport {
		private PlacementPort portToExport;
		private String exportName;
		private PortCharacteristic characteristic;

		/**
		 * Constructor to create a PlacementExport with the information about an
		 * Export to be created.
		 * 
		 * @param port the PlacementPort that is being exported.
		 * @param name the name to give the Export.
		 * @param chr the PortCharacteristic (input, output, etc.) to give the Export.
		 */
		public PlacementExport(PlacementPort port, String name, PortCharacteristic chr) {
			portToExport = port;
			exportName = name;
			characteristic = chr;
		}

		public PlacementPort getPort() {
			return portToExport;
		}

		public String getName() {
			return exportName;
		}

		public PortCharacteristic getCharacteristic() {
			return characteristic;
		}
	}

	/**
	 * Class to define an optimized connection between two PlacementPorts
	 */
	public static class PlacementConnection extends SteinerTreePortPair
	{
		private boolean isRail;

		public PlacementConnection(SteinerTreePort p1, SteinerTreePort p2, boolean isRail)
		{
			super(p1, p2);
			this.isRail = isRail;
		}

		public boolean isOnPowerGround() { return isRail; }
	}

	/**
	 * Static list of all Placement algorithms. When you create a new algorithm,
	 * add it to the following list.
	 */
	public static Control GEN = new Control();												// General control
	public static BottomUpPartition BUpa = new BottomUpPartition();							// Bottom-Up Partition
	public static BottomUpPlace BUpl = new BottomUpPlace();									// Bottom-Up Placement
	public static SimulatedAnnealing SA1 = new SimulatedAnnealing();						// Simulated Annealing (team 2)
	public static PlacementSimulatedAnnealing SA2 = new PlacementSimulatedAnnealing();		// Simulated Annealing (team 6)
	public static SARowCol SA3 = new SARowCol();											// Simulated Annealing (row/column)
	public static GeneticPlacement G1 = new GeneticPlacement();								// Genetic (team 3)
	public static PlacementGenetic G2 = new PlacementGenetic();								// Genetic (team 4)
	public static PlacementForceDirectedTeam5 FD1 = new PlacementForceDirectedTeam5();		// Force Directed (team 5)
	public static PlacementForceDirectedStaged FD2 = new PlacementForceDirectedStaged();	// Force Directed (team 7)
	public static FDRowCol FD3 = new FDRowCol();											// Force Directed (row/column)
	public static PlacementMinCut MC = new PlacementMinCut();								// Min-Cut
	public static PlacementSimple SIMP = new PlacementSimple();								// Simple
	public static PlacementRandom RAND = new PlacementRandom();								// Random

	/** preserved mapping to placed cell */	private static Map<String, NodeInst> namedPlacedNodes;
	/** HPWL from last run */				private static String lastHPWL;

	static PlacementFrame[] placementAlgorithms = { GEN, BUpa, BUpl, SA1, SA2, SA3, G1, G2, FD1, FD2, FD3, MC, SIMP, RAND};

	/**
	 * Method to return a list of all Placement algorithms.
	 * @return a list of all Placement algorithms.
	 */
	public static PlacementFrame[] getPlacementAlgorithms() {
		return placementAlgorithms;
	}

	/**
	 * Entry point for other tools that wish to describe a network to be placed.
	 * Creates a cell with the placed network.
	 * @param lib the Library in which to create the placed Cell.
	 * @param cellName the name of the Cell to create.
	 * @param nodesToPlace a List of PlacementNodes to place in the Cell.
	 * @param allNetworks a List of PlacementNetworks to connect in the Cell.
	 * @param exportsToPlace a List of PlacementExports to create in the Cell.
	 * @param iconToPlace non-null to place an instance of itself (the icon) in the Cell.
     * @param ep EditingPreferences with default sizes and text descriptors
     * @param prefs placement preferences
	 * @param quiet 0 for normal output, 1 for verbose, -1 for total silence (-1, 0 is true   1 is false)
	 * @param job the Job (for testing abort).
	 * @return the newly created Cell.
	 */
	public static Cell doPlacement(PlacementFrame pla, Library lib, String cellName,
			List<PlacementNode> nodesToPlace, List<PlacementNetwork> allNetworks, List<PlacementExport> exportsToPlace,
			NodeProto iconToPlace, EditingPreferences ep, Placement.PlacementPreferences prefs, int quiet, Job job) {
		ElapseTimer timer = ElapseTimer.createInstance().start();
		if (quiet >= 0) System.out.println("Running placement on cell '" + (lib == Library.getCurrent() ? "" : lib.getName()+":") +
			cellName + "' using the '" + pla.getAlgorithmName() + "' algorithm");

		// do the real work of placement
        if (pla instanceof PlacementFrameElectric) {
           ((PlacementFrameElectric)pla).setEditingPreferences(ep); 
        }
		for (PlacementFrame.PlacementParameter par : pla.getParameters()) {
			par.setValue(prefs.getParameter(par));
		}
		List<PlacementFrame.PlacementNode> nodesToPlaceCopy = new ArrayList<PlacementFrame.PlacementNode>(nodesToPlace);

		pla.runPlacement(nodesToPlaceCopy, allNetworks, exportsToPlace, cellName, job);
		if (pla.isFailure()) return null;

		Map<PlacementNetwork,List<SteinerTreePortPair>> optimalConnections = new HashMap<PlacementNetwork,List<SteinerTreePortPair>>();
		Cell cell = doGeneratePlacedCell(lib, cellName, nodesToPlace, iconToPlace, allNetworks, exportsToPlace, ep, timer, quiet<1, optimalConnections);

		AbstractMetric bmetric = new BBMetric(nodesToPlaceCopy, allNetworks, optimalConnections);
		lastHPWL = bmetric.toString();

		if (Job.getDebug() && quiet==1 && logger.isDebugEnabled()) {
			InetAddress addr;
			Date now = new Date();
			
			try {
				addr = InetAddress.getLocalHost();
				String hostname = addr.getHostName();

				logger.debug("====================================================");
				logger.debug("machine: " + hostname);
				logger.debug("date: " + TextUtils.formatDate(now));
				logger.debug("Electric's version: " + Version.getVersion());
				logger.debug("algorithm: " + pla.getAlgorithmName());
				logger.debug("#threads : " + pla.numOfThreads);
				logger.debug("#runtime : " + pla.runtime);
				logger.debug("cell     : " + cellName);
				logger.debug("### BBMetric: " + lastHPWL);

				AbstractMetric mstMetric = new MSTMetric(nodesToPlaceCopy, allNetworks, optimalConnections);
				logger.debug("### MSTMetric: " + mstMetric.toString());

				logger.debug("====================================================");
			} catch (UnknownHostException e) {
				e.printStackTrace();
			}
		}
		return cell;
	}

	public static String getLastHPWL() { return lastHPWL; }

	private static Cell doGeneratePlacedCell(Library lib, String cellName, List<PlacementNode> nodesToPlace,
		NodeProto iconToPlace, List<PlacementNetwork> allNetworks, List<PlacementExport> exportsToPlace,
		EditingPreferences ep, ElapseTimer timer, boolean quiet, Map<PlacementNetwork,List<SteinerTreePortPair>> optimalConnections)
	{
		// create a new cell for the placement results
		Cell newCell = Cell.makeInstance(ep, lib, cellName);

		// place the nodes in the new cell
		Map<PlacementNode, NodeInst> placedNodes = new HashMap<PlacementNode, NodeInst>();
		namedPlacedNodes = new HashMap<String, NodeInst>();
		for (PlacementNode plNode : nodesToPlace) {
			double xPos = plNode.getPlacementX();
			double yPos = plNode.getPlacementY();
			Orientation orient = plNode.getPlacementOrientation();
			NodeProto np = plNode.getType();
			if (np instanceof Cell) {
				Cell placementCell = (Cell) np;
				Rectangle2D bounds = placementCell.getBounds();
				Point2D centerOffset = new Point2D.Double(bounds.getCenterX(), bounds.getCenterY());
				orient.pureRotate().transform(centerOffset, centerOffset);
				xPos -= centerOffset.getX();
				yPos -= centerOffset.getY();
			}
			String name = plNode.nodeName;
			if (name == null && plNode.originalNode != null && !plNode.originalNode.getNameKey().isTempname())
				name = plNode.originalNode.getName();
			NodeInst ni = NodeInst.makeInstance(np, ep, new Point2D.Double(xPos, yPos), np.getDefWidth(ep),
					np.getDefHeight(ep), newCell, orient, name, plNode.getTechBits());
			if (ni == null)
				System.out.println("Placement failed to create node");
			else {
				if (plNode.isTerminal())
					ni.setLocked();
				if (plNode.originalNode != null && !plNode.originalNode.getNameKey().isTempname())
					ni.setTextDescriptor(NodeInst.NODE_NAME, plNode.originalNode.getTextDescriptor(NodeInst.NODE_NAME));
				placedNodes.put(plNode, ni);
				namedPlacedNodes.put(ni.getName(), ni);
			}
			if (plNode.addedVariables != null) {
				for (String varName : plNode.addedVariables.keySet()) {
					Object value = plNode.addedVariables.get(varName);
					Variable.Key key = Variable.newKey(varName);
					Variable var = ni.newDisplayVar(key, value, ep);
					if (key == Schematics.SCHEM_RESISTANCE) {
						ni.setTextDescriptor(
								key,
								var.getTextDescriptor().withOff(0, 0.5)
										.withDispPart(TextDescriptor.DispPos.VALUE));
					} else if (key == Schematics.ATTR_WIDTH) {
						ni.setTextDescriptor(key, var.getTextDescriptor().withOff(0.5, -1).withRelSize(1)
								.withDispPart(TextDescriptor.DispPos.VALUE));
					} else if (key == Schematics.ATTR_LENGTH) {
						ni.setTextDescriptor(key, var.getTextDescriptor().withOff(-0.5, -1).withRelSize(0.5)
								.withDispPart(TextDescriptor.DispPos.VALUE));
					} else {
						ni.setTextDescriptor(key,
								var.getTextDescriptor().withDispPart(TextDescriptor.DispPos.VALUE));
					}
				}
			}
		}

		// place an icon if requested
		if (iconToPlace != null) {
			ERectangle bounds = newCell.getBounds();
			EPoint center = EPoint.fromLambda(bounds.getMaxX() + iconToPlace.getDefWidth(ep), bounds.getMaxY()
					+ iconToPlace.getDefHeight(ep));
			NodeInst.makeInstance(iconToPlace, ep, center, iconToPlace.getDefWidth(ep), iconToPlace.getDefHeight(ep),
					newCell);
		}

		// place exports in the new cell
		for (PlacementExport plExport : exportsToPlace) {
			PlacementPort plPort = plExport.getPort();
			String exportName = plExport.getName();
			PlacementNode plNode = (PlacementNode) plPort.getPlacementNode();
			NodeInst newNI = placedNodes.get(plNode);
			if (newNI == null)
			{
				continue;
			}
			PortInst portToExport = newNI.findPortInstFromProto(plPort.getPortProto());
			Export.newInstance(newCell, portToExport, exportName, ep, plExport.getCharacteristic());
		}

		ImmutableArcInst a = Generic.tech().unrouted_arc.getDefaultInst(ep);
		long gridExtend = a.getGridExtendOverMin();
		for (PlacementNetwork plNet : allNetworks) {
			List<SteinerTreePortPair> connections;
			connections = getOptimalConnections(plNet);
			optimalConnections.put(plNet, connections);
			for(SteinerTreePortPair pc : connections)
			{
				PlacementFrame.PlacementPort pp1 = (PlacementFrame.PlacementPort)pc.getPort1();
				PlacementPort thisPp1 = (PlacementPort)pp1;
				PlacementNode plNode1 = (PlacementNode)pp1.getPlacementNode();
				NodeInst newNi1 = placedNodes.get(plNode1);
				PortInst thisPi1 = newNi1.findPortInstFromProto(thisPp1.getPortProto());
				EPoint pt1 = thisPi1.getCenter();

				PlacementFrame.PlacementPort pp2 = (PlacementFrame.PlacementPort)pc.getPort2();
				PlacementNode plNode2 = (PlacementNode)pp2.getPlacementNode();
				PlacementPort thisPp2 = (PlacementPort)pp2;
				NodeInst newNi2 = placedNodes.get(plNode2);
				PortInst thisPi2 = newNi2.findPortInstFromProto(thisPp2.getPortProto());
				EPoint pt2 = thisPi2.getCenter();

                TextDescriptor td = ep.getArcTextDescriptor();
				ArcInst.newInstanceNoCheck(newCell, Generic.tech().unrouted_arc, null, td, thisPi1, thisPi2,
					pt1, pt2, gridExtend, ArcInst.DEFAULTANGLE, a.flags);
			}
		}

		if (timer != null)
		{
			timer.end();
			if (!quiet) System.out.println("\t(took " + timer + ")");
		}
		return newCell;
	}

	public static Map<String, NodeInst> getPlacementMap() { return namedPlacedNodes; }

	private static class PlacementSteinerTree extends SteinerTree
	{
		private boolean isRail;

		public PlacementSteinerTree(List<SteinerTreePort> portList, boolean isRail)
		{
			super(portList, false);
			this.isRail = isRail;
		}

		public SteinerTreePortPair makeTreeBranch(SteinerTreePort p1, SteinerTreePort p2)
		{
			return new PlacementConnection(p1, p2, isRail);
		}
	}

	private static final boolean NEWWAY = false;

	/**
	 * Method to return a list of segments that must be run to create a network.
	 * Finds the shortest set of segments.
	 * @param plNet the PlacementNetwork being evaluated.
	 * @return a List of SteinerTreePortPair objects with port pairs.
	 */
	public static List<SteinerTreePortPair> getOptimalConnections(PlacementNetwork plNet)
	{
		List<SteinerTreePort> ports = new ArrayList<SteinerTreePort>();
		for (PlacementFrame.PlacementPort plPort : plNet.getPortsOnNet())
			ports.add(plPort);
		if (NEWWAY)
		{
			PlacementSteinerTree st = new PlacementSteinerTree(ports, plNet.isOnRail());
			return st.getTreeBranches();
		} else
		{
			// first find the closest two ports
			double shortest = Double.MAX_VALUE;
			int i1 = -1, i2 = -1;
			for(int i=1; i<ports.size(); i++)
			{
				PlacementFrame.PlacementPort pI = (PlacementFrame.PlacementPort)ports.get(i);
				PlacementFrame.PlacementNode nI = pI.getPlacementNode();
				Set<PlacementPort> equiv = ((PlacementNode)nI).getEquivalents((PlacementPort)pI);
				for(int j=0; j<i; j++)
				{
					PlacementFrame.PlacementPort pJ = (PlacementFrame.PlacementPort)ports.get(j);
					PlacementFrame.PlacementNode nJ = pJ.getPlacementNode();
					if (equiv != null && equiv.contains(pJ)) continue;
					double hpwl = Math.abs((pI.getRotatedOffX() + nI.getPlacementX()) - (pJ.getRotatedOffX() + nJ.getPlacementX())) +
						Math.abs((pI.getRotatedOffY() + nI.getPlacementY()) - (pJ.getRotatedOffY() + nJ.getPlacementY()));
					if (hpwl < shortest)
					{
						shortest = hpwl;
						i1 = i;   i2 = j;
					}
				}
			}

			// now make the list of connections
			List<SteinerTreePortPair> connections = new ArrayList<SteinerTreePortPair>();

			if (i1 < 0 || i2 < 0) return connections;
			PlacementFrame.PlacementPort e1 = (PlacementFrame.PlacementPort)ports.get(i1);
			PlacementFrame.PlacementNode e1n = e1.getPlacementNode();
			PlacementFrame.PlacementPort e2 = (PlacementFrame.PlacementPort)ports.get(i2);
			PlacementFrame.PlacementNode e2n = e2.getPlacementNode();

			// add the shortest connection to the list
			boolean isRail = plNet.isOnRail();
			PlacementConnection pc = new PlacementConnection(e1, e2, isRail);
			connections.add(pc);
			ports.remove(Math.max(i1, i2));
			ports.remove(Math.min(i1, i2));
			Set<PlacementPort> equiv = ((PlacementNode)e1n).getEquivalents((PlacementPort)e1);
			if (equiv != null) for(PlacementPort pp : equiv) ports.remove(pp);
			equiv = ((PlacementNode)e2n).getEquivalents((PlacementPort)e2);
			if (equiv != null) for(PlacementPort pp : equiv) ports.remove(pp);

			// find shortest path through them
			while (ports.size() > 0)
			{
				double e1Dist = Double.MAX_VALUE, e2Dist = Double.MAX_VALUE;
				int ei1 = -1, ei2 = -1;
				for(int i=0; i<ports.size(); i++)
				{
					PlacementFrame.PlacementPort pI = (PlacementFrame.PlacementPort)ports.get(i);
					PlacementFrame.PlacementNode nI = pI.getPlacementNode();
					double hpwl = Math.abs((pI.getRotatedOffX() + nI.getPlacementX()) - (e1.getRotatedOffX() + e1n.getPlacementX())) +
						Math.abs((pI.getRotatedOffY() + nI.getPlacementY()) - (e1.getRotatedOffY() + e1n.getPlacementY()));
					if (hpwl < e1Dist)
					{
						e1Dist = hpwl;
						ei1 = i;
					}
					hpwl = Math.abs((pI.getRotatedOffX() + nI.getPlacementX()) - (e2.getRotatedOffX() + e2n.getPlacementX())) +
					Math.abs((pI.getRotatedOffY() + nI.getPlacementY()) - (e2.getRotatedOffY() + e2n.getPlacementY()));
					if (hpwl < e2Dist)
					{
						e2Dist = hpwl;
						ei2 = i;
					}
				}
				if (e1Dist > e2Dist)
				{
					// add e2 to second end
					PlacementConnection pCon = new PlacementConnection(e2, ports.get(ei2), isRail);
					connections.add(pCon);
					ports.remove(ei2);
					e2 = (PlacementFrame.PlacementPort)pCon.getPort2();
					equiv = ((PlacementNode)e2.getPlacementNode()).getEquivalents((PlacementPort)e2);
					if (equiv != null) for(PlacementPort pp : equiv) ports.remove(pp);
				} else
				{
					// add e1 to first end
					PlacementConnection pCon = new PlacementConnection(ports.get(ei1), e1, isRail);
					connections.add(pCon);
					ports.remove(ei1);
					e1 = (PlacementFrame.PlacementPort)pCon.getPort1();
					equiv = ((PlacementNode)e1.getPlacementNode()).getEquivalents((PlacementPort)e1);
					if (equiv != null) for(PlacementPort pp : equiv) ports.remove(pp);
				}
			}
		
			return connections;
		}
	}
}
