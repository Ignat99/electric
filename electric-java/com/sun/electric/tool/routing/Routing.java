/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Routing.java
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
package com.sun.electric.tool.routing;

import com.sun.electric.database.CellBackup;
import com.sun.electric.database.CellRevision;
import com.sun.electric.database.EditingPreferences;
import com.sun.electric.database.ImmutableArcInst;
import com.sun.electric.database.ImmutableNodeInst;
import com.sun.electric.database.Snapshot;
import com.sun.electric.database.geometry.EPoint;
import com.sun.electric.database.geometry.Poly;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.Export;
import com.sun.electric.database.hierarchy.Nodable;
import com.sun.electric.database.hierarchy.View;
import com.sun.electric.database.id.CellId;
import com.sun.electric.database.id.PortProtoId;
import com.sun.electric.database.network.Netlist;
import com.sun.electric.database.network.Network;
import com.sun.electric.database.prototype.NodeProto;
import com.sun.electric.database.prototype.PortProto;
import com.sun.electric.database.text.Name;
import com.sun.electric.database.text.Pref;
import com.sun.electric.database.text.TextUtils;
import com.sun.electric.database.topology.ArcInst;
import com.sun.electric.database.topology.Connection;
import com.sun.electric.database.topology.Geometric;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.topology.PortInst;
import com.sun.electric.database.variable.EditWindow_;
import com.sun.electric.database.variable.TextDescriptor;
import com.sun.electric.database.variable.UserInterface;
import com.sun.electric.technology.ArcProto;
import com.sun.electric.technology.PrimitiveNode;
import com.sun.electric.technology.PrimitivePort;
import com.sun.electric.technology.technologies.Generic;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.JobException;
import com.sun.electric.tool.Listener;
import com.sun.electric.tool.user.User;
import com.sun.electric.tool.user.UserInterfaceMain;
import com.sun.electric.tool.user.ui.EditWindow;

import java.awt.geom.Point2D;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This is the Routing tool.
 */
public class Routing extends Listener {
	/**
	 * Class to describe recent activity that pertains to routing.
	 */
	public static class Activity {
		int numCreatedArcs, numCreatedNodes;
		ImmutableArcInst[] createdArcs = new ImmutableArcInst[3];
		CellId createdArcsParents[] = new CellId[3];
		ImmutableNodeInst[] createdNodes = new ImmutableNodeInst[3];
		int numDeletedArcs, numDeletedNodes;
		ImmutableArcInst deletedArc;
		CellId deletedArcParent;
		PortProtoId[] deletedPorts = new PortProtoId[2];

		Activity() {}
	}

	private Activity current, past = null;
	private boolean checkAutoStitch = false;

	/** the Routing tool. */
	private static Routing tool = new Routing();

	/****************************** TOOL INTERFACE ******************************/

	/**
	 * The constructor sets up the Routing tool.
	 */
	private Routing() {
		super("routing");
	}

	/**
	 * Method to initialize the Routing tool.
	 */
	public void init() {
		setOn();
	}

	/**
	 * Method to retrieve the singleton associated with the Routing tool.
	 *
	 * @return the Routing tool.
	 */
	public static Routing getRoutingTool() {
		return tool;
	}

	/**
	 * Handles database changes of a Job.
	 *
	 * @param oldSnapshot
	 *            database snapshot before Job.
	 * @param newSnapshot
	 *            database snapshot after Job and constraint propagation.
	 * @param undoRedo
	 *            true if Job was Undo/Redo job.
	 */
	public void endBatch(Snapshot oldSnapshot, Snapshot newSnapshot, boolean undoRedo) {
		if (undoRedo)
			return;
		if (newSnapshot.tool == tool)
			return;
		current = new Activity();
		checkAutoStitch = false;
		for (CellId cellId : newSnapshot.getChangedCells(oldSnapshot)) {
			CellBackup oldBackup = oldSnapshot.getCell(cellId);
			if (oldBackup == null)
				continue; // Don't route in new cells
			CellBackup newBackup = newSnapshot.getCell(cellId);
			if (newBackup == null)
				continue;
			if (oldBackup == newBackup)
				continue;
			CellRevision oldRevision = oldBackup.cellRevision;
			CellRevision newRevision = newBackup.cellRevision;
			ArrayList<ImmutableNodeInst> oldNodes = new ArrayList<ImmutableNodeInst>();
			for (ImmutableNodeInst n : oldRevision.nodes) {
				while (n.nodeId >= oldNodes.size())
					oldNodes.add(null);
				oldNodes.set(n.nodeId, n);
			}
			ArrayList<ImmutableArcInst> oldArcs = new ArrayList<ImmutableArcInst>();
			for (ImmutableArcInst a : oldRevision.arcs) {
				while (a.arcId >= oldArcs.size())
					oldArcs.add(null);
				oldArcs.set(a.arcId, a);
			}
			BitSet newNodes = new BitSet();
			for (ImmutableNodeInst d : newRevision.nodes) {
				newNodes.set(d.nodeId);
				ImmutableNodeInst oldD = d.nodeId < oldNodes.size() ? oldNodes.get(d.nodeId) : null;
				if (oldD == null) {
					if (current.numCreatedNodes < 3)
						current.createdNodes[current.numCreatedNodes++] = d;
				} else if (oldD != d) {
					checkAutoStitch = true;
				}
			}
			BitSet newArcs = new BitSet();
			for (ImmutableArcInst d : newRevision.arcs) {
				newArcs.set(d.arcId);
				ImmutableArcInst oldD = d.arcId < oldArcs.size() ? oldArcs.get(d.arcId) : null;
				if (oldD == null) {
					if (current.numCreatedArcs < 3) {
						current.createdArcsParents[current.numCreatedArcs] = cellId;
						current.createdArcs[current.numCreatedArcs++] = d;
					}
				}
			}
			for (ImmutableNodeInst nid : oldRevision.nodes) {
				if (!newNodes.get(nid.nodeId))
					current.numDeletedNodes++;
			}
			for (ImmutableArcInst aid : oldRevision.arcs) {
				if (!newArcs.get(aid.arcId)) {
					if (current.numDeletedArcs == 0) {
						current.deletedArc = aid;
						current.deletedArcParent = cellId;
						current.deletedPorts[0] = aid.headPortId;
						current.deletedPorts[1] = aid.tailPortId;
					}
					current.numDeletedArcs++;
				}
			}
		}

		if (current.numCreatedArcs > 0 || current.numCreatedNodes > 0 || current.deletedArc != null) {
			past = current;
			if (isMimicStitchOn()) {
				MimicStitch.mimicStitch(UserInterfaceMain.getEditingPreferences(), false);
				return;
			}
		}
		if (checkAutoStitch && isAutoStitchOn()) {
			AutoStitch.autoStitch(false, false);
		}
		// JFluid results
		current = null;
	}

	/****************************** COMMANDS ******************************/

	/**
	 * Method to mimic the currently selected ArcInst.
	 */
	public void mimicSelected(EditingPreferences ep) {
		UserInterface ui = Job.getUserInterface();
		EditWindow_ wnd = ui.getCurrentEditWindow_();
		if (wnd == null)
			return;

		ArcInst ai = (ArcInst) wnd.getOneElectricObject(ArcInst.class);
		if (ai == null)
			return;
		past = new Activity();
		past.createdArcsParents[past.numCreatedArcs] = ai.getParent().getId();
		past.createdArcs[past.numCreatedArcs++] = ai.getD();
		MimicStitch.mimicStitch(ep, true);
	}

	/**
	 * Method to convert the current network(s) to an unrouted wire.
	 */
	public static void unrouteCurrent() {
		// see what is highlighted
		UserInterface ui = Job.getUserInterface();
		EditWindow_ wnd = ui.getCurrentEditWindow_();
		if (wnd == null) return;
		Cell cell = wnd.getCell();
		Set<Network> nets = wnd.getHighlightedNetworks();
		if (nets.size() == 0) {
			System.out.println("Must select networks to unroute");
			return;
		}

		unrouteCell(cell, nets);
	}

	/**
	 * Method to convert the current network(s) to an unrouted wire.
	 */
	public static void unrouteCell(Cell c, Set<Network> nets) {
		new UnrouteNetsJob(c, nets);
	}

	/**
	 * Method to determine the preferred ArcProto to use for routing. Examines
	 * preferences in the Routing tool and User interface.
	 *
	 * @return the preferred ArcProto to use for routing.
	 */
	public static ArcProto getPreferredRoutingArcProto() {
		ArcProto preferredArc = null;
		String preferredName = getPreferredRoutingArc();
		if (preferredName.length() > 0)
			preferredArc = ArcProto.findArcProto(preferredName);
		if (preferredArc == null) {
			// see if there is a default user arc
			ArcProto curAp = User.getUserTool().getCurrentArcProto();
			if (curAp != null)
				preferredArc = curAp;
		}
		return preferredArc;
	}

	private static class UnrouteNetsJob extends Job {
		private Cell cell;
		private Set<Network> nets;
		private List<ArcInst> highlightThese;

		private UnrouteNetsJob(Cell cell, Set<Network> nets) {
			super("Unroute Network", Routing.tool, Job.Type.CHANGE, null, null, Job.Priority.USER);
			this.cell = cell;
			this.nets = nets;
			startJob();
		}

		public boolean doIt() throws JobException {
			// convert requested nets
			Netlist netList = cell.getNetlist();
			if (netList == null)
				throw new JobException("Sorry, a deadlock aborted unrouting (network information unavailable).  Please try again");
			highlightThese = new ArrayList<ArcInst>();

			// make arrays of what to unroute
			Map<Network, ArcInst[]> arcMap = null;
			if (cell.getView() != View.SCHEMATIC)
				arcMap = netList.getArcInstsByNetwork();
			int total = nets.size();
			Network[] netsToUnroute = new Network[total];
			ArrayList<UnroutePort[]> netEnds = new ArrayList<UnroutePort[]>();
			Set<NodeInst> deleteAllNodes = new HashSet<NodeInst>();
			Set<ArcInst> deleteAllArcs = new HashSet<ArcInst>();
			int i = 0;
			for (Network net : nets) {
				netsToUnroute[i] = net;
				Set<ArcInst> arcs = new HashSet<ArcInst>();
				Set<NodeInst> nodes = new HashSet<NodeInst>();
				List<Connection> netCons = findNetEnds(net, arcMap, arcs, nodes, netList, false);
				UnroutePort[] netUnroutePorts = new UnroutePort[netCons.size()];
				for (int j = 0; j < netCons.size(); j++) {
					netUnroutePorts[j] = new UnroutePort(netCons.get(j));
				}
				netEnds.add(netUnroutePorts);
				for (NodeInst ni : nodes)
					deleteAllNodes.add(ni);
				for (ArcInst ai : arcs)
					deleteAllArcs.add(ai);
				i++;
			}

			// delete all arcs and nodes that are being unrouted
			cell.killArcs(deleteAllArcs);
			cell.killNodes(deleteAllNodes);

			// unroute the network
			for (int j = 0; j < total; j++) {
				if (makeUnroutedNet(netsToUnroute[j], netEnds.get(j), netList, highlightThese))
					break;
			}
			fieldVariableChanged("highlightThese");
			return true;
		}

		public void terminateOK() {
			UserInterface ui = Job.getUserInterface();
			EditWindow_ wnd = ui.getCurrentEditWindow_();
			if (wnd == null)
				return;
			wnd.clearHighlighting();
			for (ArcInst ai : highlightThese)
				wnd.addElectricObject(ai, ai.getParent());
			wnd.finishedHighlighting();
		}

		private static class UnroutePort {
			final PortInst pi;
			final EPoint center;

			UnroutePort(Connection con) {
				pi = con.getPortInst();
				center = pi.getCenter();
			}
		}

		private boolean makeUnroutedNet(Network net, UnroutePort[] netEnds, Netlist netList,
				List<ArcInst> highlightThese) {
            EditingPreferences ep = getEditingPreferences();
            TextDescriptor td = ep.getArcTextDescriptor();
			Cell cell = net.getNetlist().getCell();
			ImmutableArcInst a = Generic.tech().unrouted_arc.getDefaultInst(ep);
			long gridExtend = a.getGridExtendOverMin();

			// now create the new unrouted wires
			int count = netEnds.length;
			if (count >= 1000) {
				// too much work to make the solution look pretty: just radiate
				// all connections from a center point
				double x = 0, y = 0;
				for (int i = 0; i < count; i++) {
					x += netEnds[i].center.getX();
					y += netEnds[i].center.getY();
				}
				EPoint center = EPoint.fromLambda(x / count, y / count);
				PrimitiveNode centerNp = Generic.tech().unroutedPinNode;
				double width = centerNp.getDefWidth(ep);
				double height = centerNp.getDefHeight(ep);
				NodeInst centerNi = NodeInst.makeInstance(centerNp, ep, center, width, height, net.getParent());
				PortInst tail = centerNi.getOnlyPortInst();
				for (int i = 0; i < count; i++) {
					PortInst head = netEnds[i].pi;
					EPoint headP = netEnds[i].center;
					ArcInst ai = ArcInst.newInstanceNoCheck(cell, Generic.tech().unrouted_arc, null, td, head,
							tail, headP, center, gridExtend, ArcInst.DEFAULTANGLE, a.flags);
					if (ai == null) {
						System.out.println("Could not create unrouted arc");
						return true;
					}
					highlightThese.add(ai);
				}
			} else {
				// do a poor-man's traveling-salesman solution to make the
				// unrouted nets look good
				int[] covered = new int[count];
				for (int i = 0; i < count; i++)
					covered[i] = 0;
				for (int first = 0;; first++) {
					boolean found = true;
					double bestdist = 0;
					int besti = 0, bestj = 0;
					for (int i = 0; i < count; i++) {
						for (int j = i + 1; j < count; j++) {
							if (first != 0) {
								if (covered[i] + covered[j] != 1)
									continue;
							}
							double dist = netEnds[i].center.distance(netEnds[j].center);

							if (!found && dist >= bestdist)
								continue;
							found = false;
							bestdist = dist;
							besti = i;
							bestj = j;
						}
					}
					if (found)
						break;

					covered[besti] = covered[bestj] = 1;
					PortInst head = netEnds[besti].pi;
					PortInst tail = netEnds[bestj].pi;
					EPoint headP = netEnds[besti].center;
					EPoint tailP = netEnds[bestj].center;
					ArcInst ai = ArcInst.newInstanceNoCheck(cell, Generic.tech().unrouted_arc, null, td, head,
							tail, headP, tailP, gridExtend, ArcInst.DEFAULTANGLE, a.flags);
					if (ai == null) {
						System.out.println("Could not create unrouted arc");
						return true;
					}
					highlightThese.add(ai);
				}
			}
			return false;
		}
	}

	/**
	 * Method to find the endpoints of a network.
	 * @param net the network to "unroute".
	 * @param arcMap a shortcut to finding arcs on a network (if not null).
	 * @param arcsToDelete a HashSet of arcs that should be deleted.
	 * @param nodesToDelete a HashSet of nodes that should be deleted.
	 * @param netList the netlist for the current cell.
	 * @param deleteOnlyUnrouted false to include all items on the network in the list of
	 * arcs/nodes to delete. true to only include items from the generic technology or pins with no exports.
	 * @return a List of Connection (PortInst/Point2D pairs) that should be wired together.
	 */
	public static List<Connection> findNetEnds(Network net, Map<Network, ArcInst[]> arcMap,
			Set<ArcInst> arcsToDelete, Set<NodeInst> nodesToDelete, Netlist netList, boolean deleteOnlyUnrouted) {
		// initialize
		List<Connection> endList = new ArrayList<Connection>();
		Map<NodeInst, List<Connection>> endListByNode = new HashMap<NodeInst, List<Connection>>();

		// look at every arc and see if it is part of the network
		ArcInst[] arcsOnNet = null;
		if (arcMap != null)
			arcsOnNet = arcMap.get(net);
		else {
			List<ArcInst> arcList = new ArrayList<ArcInst>();
			for (Iterator<ArcInst> aIt = net.getArcs(); aIt.hasNext();)
				arcList.add(aIt.next());
			arcsOnNet = arcList.toArray(new ArcInst[] {});
		}
		for (ArcInst ai : arcsOnNet) {
			if (deleteOnlyUnrouted && ai.getProto() != Generic.tech().unrouted_arc)
				continue;
			arcsToDelete.add(ai);

			// see if an end of the arc is a network "end"
			for (int i = 0; i < 2; i++) {
				Connection thisCon = ai.getConnection(i);
				PortInst pi = thisCon.getPortInst();
				NodeInst ni = pi.getNodeInst();

				// see if this arc end is a "termination point" (on cell,
				// exported, or not further connected)
				boolean term = true;
				if (!ni.hasExports() && !ni.isCellInstance()) {
					PrimitiveNode.Function fun = ni.getFunction();
					if (ni.getNumConnections() != 1
							&& (fun == PrimitiveNode.Function.PIN || fun == PrimitiveNode.Function.CONTACT || fun == PrimitiveNode.Function.CONNECT))
						term = false;
					else {
						// allow well/substrate contacts
						if (fun != PrimitiveNode.Function.SUBSTRATE && fun != PrimitiveNode.Function.WELL)
						{
							// see if this primitive connects to other unconnected nets
							for (Iterator<Connection> cIt = ni.getConnections(); cIt.hasNext();) {
								Connection con = cIt.next();
								ArcInst conAi = con.getArc();
								if (deleteOnlyUnrouted && conAi.getProto() != Generic.tech().unrouted_arc)
									continue;
								if (conAi != ai && netList.getNetwork(conAi, 0) == net) {
									term = false;
									break;
								}
							}
						}
					}
				}

				// if a termination point, consider it for the list of net ends
				if (term) {
					// see if it is already in the list, connection-wise
					boolean found = false;
					Netlist nl = null;
					List<Connection> consOnNode = endListByNode.get(ni);
					if (consOnNode != null) {
						for (Connection lCon : consOnNode) {
							PortInst otherPI = lCon.getPortInst();
							if (otherPI == pi) {
								found = true;
								break;
							}

							// connecting to another port on a node: ignore if
							// they are connected internally
							if (ni.isCellInstance()) {
								if (nl == null)
									nl = ((Cell) ni.getProto()).getNetlist();
								if (nl == null)
									continue;
								Network subNet = nl.getNetwork((Export) pi.getPortProto(), 0);
								Network otherSubNet = nl.getNetwork((Export) otherPI.getPortProto(), 0);
								if (subNet == otherSubNet) {
									found = true;
									break;
								}
							} else {
								PrimitivePort subPP = (PrimitivePort) pi.getPortProto();
								PrimitivePort otherSubPP = (PrimitivePort) otherPI.getPortProto();
								if (subPP.getTopology() == otherSubPP.getTopology()) {
									found = true;
									break;
								}
							}
						}
					}
					if (!found) {
						endList.add(thisCon);
						List<Connection> consByNode = endListByNode.get(ni);
						if (consByNode == null)
							endListByNode.put(ni, consByNode = new ArrayList<Connection>());
						consByNode.add(thisCon);
					}
				} else {
					// not a network end: mark the node for removal
					boolean deleteNode = !deleteOnlyUnrouted;
					if (ni.getProto().getFunction().isPin())
						deleteNode = true;
					if (deleteNode)
						nodesToDelete.add(ni);
				}
			}
		}
		return endList;
	}

	/**
	 * Method to return the most recent routing activity.
	 */
	public Activity getLastActivity() {
		return past;
	}

	/**
	 * Method to unroute the currently selected segment.
	 * A segment terminates at a fork, so it is just a single run of arcs.
	 */
	public static void unrouteCurrentSegment()
	{
		EditWindow wnd = EditWindow.getCurrent();
		Cell cell = wnd.getCell();
		if (cell == null) return;
		List<Geometric> selected = wnd.getHighlightedEObjs(false, true);
		if (selected == null || selected.size() == 0)
		{
			Job.getUserInterface().showErrorMessage("Must select an arc to unroute", "Nothing is Selected");
			return;
		}
		for(Geometric geom : selected)
		{
			if (geom instanceof ArcInst)
				new UnrouteSegJob(cell, (ArcInst)geom);
		}
	}

	private static class UnrouteSegJob extends Job
	{
		private Cell cell;
		private ArcInst ai;

		private UnrouteSegJob(Cell cell, ArcInst ai)
		{
			super("Unroute Segment", Routing.tool, Job.Type.CHANGE, null, null, Job.Priority.USER);
			this.cell = cell;
			this.ai = ai;
			startJob();
		}

		public boolean doIt() throws JobException
		{
			// see if this arc end is a "termination point" (on cell, exported, or not further connected)
			Set<ArcInst> arcsToUnroute = new HashSet<ArcInst>();
			Set<NodeInst> nodesToUnroute = new HashSet<NodeInst>();

			arcsToUnroute.add(ai);
			Connection end1 = ai.getHead();
			for(;;)
			{
				Connection newEnd = pushEnd(end1, arcsToUnroute, nodesToUnroute);
				if (newEnd == null) break;
				end1 = newEnd;
			}
			Connection end2 = ai.getTail();
			for(;;)
			{
				Connection newEnd = pushEnd(end2, arcsToUnroute, nodesToUnroute);
				if (newEnd == null) break;
				end2 = newEnd;
			}

			// delete all arcs and nodes that are being unrouted
			PortInst pi1 = end1.getPortInst();
			PortInst pi2 = end2.getPortInst();
			cell.killArcs(arcsToUnroute);
			cell.killNodes(nodesToUnroute);

			// unroute the network
            EditingPreferences ep = getEditingPreferences();
			ai = ArcInst.makeInstance(Generic.tech().unrouted_arc, ep, pi1, pi2);
			if (ai == null) System.out.println("FAILED TO MAKE UNROUTED ARC");
			fieldVariableChanged("ai");
			return true;
		}

		private Connection pushEnd(Connection end, Set<ArcInst> arcsToUnroute, Set<NodeInst> nodesToUnroute)
		{
			NodeInst ni = end.getPortInst().getNodeInst();
			if (nodesToUnroute.contains(ni)) return null;

			// if exported, the segment ends here
			if (ni.hasExports()) return null;

			// if a cell instance, the segment ends here
			if (ni.isCellInstance()) return null;

			// if not a through-node, the segment ends here
			PrimitiveNode.Function fun = ni.getFunction();
			if (fun != PrimitiveNode.Function.PIN && fun != PrimitiveNode.Function.CONTACT &&
				fun != PrimitiveNode.Function.CONNECT && fun != PrimitiveNode.Function.SUBSTRATE &&
				fun != PrimitiveNode.Function.WELL) return null;

			// if there are not exactly 2 connections, the segment ends here
			if (ni.getNumConnections() != 2) return null;

			// get the other connection
			Connection oCon = null;
			for(Iterator<Connection> it = ni.getConnections(); it.hasNext(); )
			{
				Connection c = it.next();
				if (c.getArc() == end.getArc()) continue;
				if (arcsToUnroute.contains(c.getArc())) continue;
				oCon = c;
				break;
			}
			if (oCon == null) return null;

			nodesToUnroute.add(ni);
			arcsToUnroute.add(oCon.getArc());
			return oCon.getArc().getConnection(1-oCon.getEndIndex());
		}

		public void terminateOK()
		{
			UserInterface ui = Job.getUserInterface();
			EditWindow_ wnd = ui.getCurrentEditWindow_();
			if (wnd == null) return;
			if (ai != null)
			{
				wnd.clearHighlighting();
				wnd.addElectricObject(ai, ai.getParent());
				wnd.finishedHighlighting();
			}
		}
	}

	/****************************** SUN ROUTER INTERFACE ******************************/

	private static boolean sunRouterChecked = false;
	private static Class<?> sunRouterClass = null;
	private static Method sunRouterMethod;

	/**
	 * Method to tell whether the Sun Router is available. The method dynamically figures out whether
	 * the router is present by using reflection.
	 *
	 * @return true if the Sun Router is available.
	 */
	public static boolean hasSunRouter() {
		if (!sunRouterChecked) {
			sunRouterChecked = true;

			// find the Sun Router class
			try {
				sunRouterClass = Class.forName("com.sun.electric.plugins.sunRouter.SunRouter");
			} catch (ClassNotFoundException e) {
				TextUtils.recordMissingPrivateComponent("Sun Router");
				sunRouterClass = null;
				return false;
			}

			// find the necessary method on the router class
			try {
				sunRouterMethod = sunRouterClass.getMethod("routeCell", new Class[] { Cell.class });
			} catch (NoSuchMethodException e) {
				sunRouterClass = null;
				return false;
			}
		}

		// if already initialized, return
		if (sunRouterClass == null)
			return false;
		return true;
	}

	/**
	 * Method to invoke the Sun Router via reflection.
	 */
	public static void sunRouteCurrentCell() {
		if (!hasSunRouter())
			return;
		UserInterface ui = Job.getUserInterface();
		Cell curCell = ui.needCurrentCell();
		if (curCell == null)
			return;
		try {
			sunRouterMethod.invoke(sunRouterClass, new Object[] { curCell });
		} catch (Exception e) {
			System.out.println("Unable to run the Sun Router module");
			e.printStackTrace(System.out);
		}
	}

	/****************************** COPY / PASTE ROUTING TOPOLOGY ******************************/

	private static Cell copiedTopologyCell;

	/**
	 * Method called when the "Copy Routing Topology" command is issued. It
	 * remembers the topology in the current cell.
	 */
	public static void copyRoutingTopology() {
		UserInterface ui = Job.getUserInterface();
		Cell np = ui.needCurrentCell();
		if (np == null)
			return;
		copiedTopologyCell = np;
		System.out.println("Cell " + np.describe(true) + " will have its connectivity remembered");
	}

	/**
	 * Method called when the "Paste Routing Topology" command is issued. It
	 * applies the topology of the "copied" cell to the current cell.
	 */
	public static void pasteRoutingTopology() {
		if (copiedTopologyCell == null) {
			System.out.println("Must copy topology before pasting it");
			return;
		}

		// first validate the source cell
		if (!copiedTopologyCell.isLinked()) {
			System.out.println("Copied cell is no longer valid");
			return;
		}

		// get the destination cell
		UserInterface ui = Job.getUserInterface();
		Cell toCell = ui.needCurrentCell();
		if (toCell == null)
			return;

		// make sure copy goes to a different cell
		if (copiedTopologyCell == toCell) {
			System.out.println("Topology must be copied to a different cell");
			return;
		}

		// do the copy
		new CopyRoutingTopology(copiedTopologyCell, toCell);
	}

	/**
	 * Class to delete a cell in a new thread.
	 */
	private static class CopyRoutingTopology extends Job {
		private Cell fromCell, toCell;

		protected CopyRoutingTopology(Cell fromCell, Cell toCell) {
			super("Copy Routing Topology", Routing.tool, Job.Type.CHANGE, null, null, Job.Priority.USER);
			this.fromCell = fromCell;
			this.toCell = toCell;
			startJob();
		}

        @Override
		public boolean doIt() throws JobException {
			return copyTopology(fromCell, toCell, getEditingPreferences());
		}
	}

	private static class NodeMatch {
		NodeInst ni;
		NodeInst otherNi;

		NodeMatch(NodeInst ni) {
			this.ni = ni;
		}

		void findEquivalentByName(Cell other, Set<NodeInst> alreadyMatched) {
			// match by name
			String thisName = ni.getName();
			
			final int busdelim = (int)'[';
			int busstart = thisName.lastIndexOf(busdelim);
			
			if (busstart > 0) { // We have name with bus!
				for (Iterator<NodeInst> it = other.getNodes(); it.hasNext(); ) {
					NodeInst oNi = it.next();
					if (alreadyMatched.contains(oNi)) continue;
					String aname = oNi.getName();
					int busoni = aname.lastIndexOf(busdelim);
					if (busoni != busstart) continue;
					if (aname.regionMatches(0, thisName, 0, busstart)) { // Got match by name with cut bus name!
						otherNi = oNi;
						return;
					}
				}
			} else {
				for (Iterator<NodeInst> it = other.getNodes(); it.hasNext();) {
					NodeInst oNi = it.next();
					if (alreadyMatched.contains(oNi)) continue;
					if (oNi.getName().equals(thisName)) {
						otherNi = oNi;
						return;
					}
				}
			}

			// match by export
			if (!ni.isCellInstance() && ni.hasExports()) {
				for (Iterator<Export> it = ni.getExports(); it.hasNext();) {
					Export e = it.next();
					String eName = e.getName();
					for (Iterator<Export> oIt = other.getExports(); oIt.hasNext();) {
						Export oE = oIt.next();
						if (eName.equalsIgnoreCase(oE.getName())) {
							otherNi = oE.getOriginalPort().getNodeInst();
							return;
						}
					}
				}
			}
		}
	}

	/**
	 * Method to copy the routing topology from one cell to another.
	 *
	 * @param fromCell the source of the routing topology.
	 * @param toCell the destination cell for the routing topology.
     * @param ep EditingPreferences
	 * @return true if successful.
	 */
	public static boolean copyTopology(Cell fromCell, Cell toCell, EditingPreferences ep) {
		// make a list of all networks
		System.out.println("Copying topology from " + fromCell.describe(false) + " to " + toCell.describe(false));
		Netlist nl = fromCell.getNetlist();
		Map<Network, ArcInst[]> arcMap = null;
		if (fromCell.getView() != View.SCHEMATIC)
			arcMap = nl.getArcInstsByNetwork();

		// first make a list of all nodes in the "from" cell
		Map<NodeInst, List<NodeMatch>> nodeMap = new HashMap<NodeInst, List<NodeMatch>>();
		Set<NodeInst> alreadyMatched = new HashSet<NodeInst>();
		List<NodeMatch> unmatched = new ArrayList<NodeMatch>();
		for (Iterator<NodeInst> it = fromCell.getNodes(); it.hasNext();) {
			NodeInst ni = it.next();
			if (Generic.isCellCenterOrEssentialBnd(ni))
				continue;

			// ignore connecting primitives with no exports
			if (!ni.isCellInstance() && !ni.hasExports()) {
				PrimitiveNode.Function fun = ni.getFunction();
				if (fun == PrimitiveNode.Function.UNKNOWN || fun.isPin() || fun.isContact()
						|| fun == PrimitiveNode.Function.CONNECT || fun == PrimitiveNode.Function.NODE)
					continue;
			}

			// make a list of all equivalent nodes that match this one
			if (ni.isIconOfParent())
				continue;
			List<NodeMatch> matches = new ArrayList<NodeMatch>();
			for (Iterator<Nodable> noIt = nl.getNodables(); noIt.hasNext();) {
				Nodable no = noIt.next();
				if (no.getNodeInst() == ni) {
					NodeMatch nm = new NodeMatch(ni);
					nm.findEquivalentByName(toCell, alreadyMatched);
					if (nm.otherNi == null) {
						unmatched.add(nm);
					}
					matches.add(nm);
					alreadyMatched.add(nm.otherNi);
				}
			}
			nodeMap.put(ni, matches);
		}

		// resolve unmatched nodes
		if (unmatched.size() > 0) {
			// make a list of unmatched nodes in the "to" cell
			Set<NodeInst> availableToNodes = new HashSet<NodeInst>();
			for (Iterator<NodeInst> it = toCell.getNodes(); it.hasNext();)
				availableToNodes.add(it.next());
			for (Iterator<NodeInst> it = fromCell.getNodes(); it.hasNext();) {
				List<NodeMatch> associated = nodeMap.get(it.next());
				if (associated == null)
					continue;
				for (NodeMatch nm : associated) {
					if (nm.otherNi != null)
						availableToNodes.remove(nm.otherNi);
				}
			}

			for (int i = 0; i < unmatched.size(); i++) {
				NodeMatch nm = unmatched.get(i);
				NodeProto fromNp = nm.ni.getProto();

				// make a list of these nodes in the "from" cell
				List<NodeMatch> fromNm = new ArrayList<NodeMatch>();
				for (NodeMatch nmTest : unmatched) {
					if (nmTest.ni.getProto() == fromNp)
						fromNm.add(nmTest);
				}

				// make a list of these nodes in the "to" cell
				List<NodeInst> toNi = new ArrayList<NodeInst>();
				for (NodeInst ni : availableToNodes) {
					if (ni.isCellInstance()) {
						if (fromNp instanceof Cell) {
							if (((Cell) fromNp).getCellGroup() == ((Cell) ni.getProto()).getCellGroup())
								toNi.add(ni);
						}
					} else {
						if (fromNp instanceof PrimitiveNode) {
							if (nm.ni.getFunction() == ni.getFunction())
								toNi.add(ni);
						}
					}
				}

				// if lists are not the same length, give a warning
				if (fromNm.size() != toNi.size()) {
					if (fromNp instanceof Cell)
						System.out.println("Error: there are " + fromNm.size() + " instances of " +
							fromNp.describe(false) + " in source and " + toNi.size() + " in destination");
					continue;
				}

				// sort the rest by position and force matches based on that
				if (fromNm.size() == 0)
					continue;
				Collections.sort(fromNm, new NameMatchSpatially());
				Collections.sort(toNi, new InstancesSpatially());
				for (int j = 0; j < fromNm.size(); j++) {
					NodeMatch nmAssoc = fromNm.get(j);
					NodeInst niAssoc = toNi.get(j);
//					System.out.println("Associated "+nmAssoc.ni.getName()+" in schematic with "+niAssoc.getName()+" in layout");
					nmAssoc.otherNi = niAssoc;
					unmatched.remove(nmAssoc);
					availableToNodes.remove(niAssoc);
				}
			}
		}

		// now construct the unrouted arcs in the "to" cell
		int wiresMade = 0;
		for (Iterator<Network> it = nl.getNetworks(); it.hasNext();) {
			Network net = it.next();
			Set<PortInst> endSet = new HashSet<PortInst>();

			// deal with export matches
			for (Iterator<Export> eIt = net.getExports(); eIt.hasNext();) {
				Export e = eIt.next();
				int eWidth = nl.getBusWidth(e);
				for (int i = 0; i < eWidth; i++) {
					if (net != nl.getNetwork(e, i))
						continue;
					String eName = e.getNameKey().subname(i).toString();

					for (Iterator<Export> oEIt = toCell.getExports(); oEIt.hasNext();) {
						Export oE = oEIt.next();
						if (oE.getName().equalsIgnoreCase(eName))
							endSet.add(oE.getOriginalPort());
					}
				}
			}

			// find all PortInsts on this network
			ArcInst[] arcsOnNet = null;
			if (arcMap != null)
				arcsOnNet = arcMap.get(net);
			else {
				List<ArcInst> arcList = new ArrayList<ArcInst>();
				for (Iterator<ArcInst> aIt = net.getArcs(); aIt.hasNext();)
					arcList.add(aIt.next());
				arcsOnNet = arcList.toArray(new ArcInst[] {});
			}
			for (ArcInst ai : arcsOnNet) {
				int busWidth = nl.getBusWidth(ai);
				for (int b = 0; b < busWidth; b++) {
					Network busNet = nl.getNetwork(ai, b);
					if (busNet != net)
						continue;

					// wire "b" of arc "ai" is on the network: include both ends
					for (int e = 0; e < 2; e++) {
						PortInst pi = ai.getPortInst(e);
						NodeInst ni = pi.getNodeInst();
						List<NodeMatch> possibleNodes = nodeMap.get(ni);
						if (possibleNodes == null)
							continue;
						if (possibleNodes.size() > 1) {
							PortProto p = pi.getPortProto();
							int portWidth = p.getNameKey().busWidth();
							if (busWidth == portWidth) {
								// each wire of the bus connects to the same
								// port on the different arrayed nodes
								Name portName = p.getNameKey().subname(b);
								for (NodeMatch nm : possibleNodes) {
									if (nm.otherNi == null)
										continue;
									PortProto pp = nm.otherNi.getProto().findPortProto(portName);
									PortInst piDest = nm.otherNi.findPortInstFromEquivalentProto(pp);
									if (piDest != null)
										endSet.add(piDest);
								}
							} else {
								// bus is has different signal for each arrayed
								// node
								int nodeIndex = b / portWidth;
								int portIndex = b % portWidth;
								NodeMatch nm = possibleNodes.get(nodeIndex);
								if (nm.otherNi != null) {
									Name portName = p.getNameKey().subname(portIndex);
									PortProto pp = nm.otherNi.getProto().findPortProto(portName);
									PortInst piDest = nm.otherNi.findPortInstFromEquivalentProto(pp);
									if (piDest != null)
										endSet.add(piDest);
								}
							}
						} else if (possibleNodes.size() == 1) {
							PortProto p = pi.getPortProto();

							int nodeIndex = 0;
							int portIndex = b;
							NodeMatch nm = possibleNodes.get(nodeIndex);
							if (nm.otherNi != null) {
								Name portName = p.getNameKey().subname(portIndex);
								PortProto pp = nm.otherNi.getProto().findPortProto(portName);
								PortInst piDest = null;
								if (pp != null) piDest = nm.otherNi.findPortInstFromEquivalentProto(pp);
								if (piDest == null) {
									if (nm.otherNi.getNumPortInsts() == 1)
										piDest = nm.otherNi.getOnlyPortInst();
								}
								if (piDest != null)
									endSet.add(piDest);
							}
						}
					}
				}
			}

			// sort the connections
			List<PortInst> sortedPorts = new ArrayList<PortInst>();
			for (PortInst pi : endSet)
				sortedPorts.add(pi);
			Collections.sort(sortedPorts, new PortsByName());

			// create the connections
			PortInst lastPi = null;
			for (PortInst pi : sortedPorts) {
				if (lastPi != null) {
					Poly lPoly = lastPi.getPoly();
					Poly poly = pi.getPoly();
					Point2D lPt = new Point2D.Double(lPoly.getCenterX(), lPoly.getCenterY());
					Point2D pt = new Point2D.Double(poly.getCenterX(), poly.getCenterY());
					ArcInst newAi = ArcInst.makeInstance(Generic.tech().unrouted_arc, ep, lastPi, pi, lPt, pt,
							null);
					if (newAi == null)
						break;
					wiresMade++;
				}
				lastPi = pi;
			}
		}

		if (wiresMade == 0)
			System.out.println("No topology was copied");
		else
			System.out.println("Created " + wiresMade + " arcs to copy the topology");
		return true;
	}

	private static class PortsByName implements Comparator<PortInst> {
		public int compare(PortInst p1, PortInst p2) {
			NodeInst n1 = p1.getNodeInst();
			NodeInst n2 = p2.getNodeInst();
			return n1.getName().compareToIgnoreCase(n2.getName());
		}
	}

	private static class InstancesSpatially implements Comparator<NodeInst> {
		public int compare(NodeInst n1, NodeInst n2) {
			double x1 = n1.getAnchorCenterX();
			double y1 = n1.getAnchorCenterY();
			double x2 = n2.getAnchorCenterX();
			double y2 = n2.getAnchorCenterY();
			if (y1 == y2) {
				if (x1 == x2)
					return 0;
				if (x1 > x2)
					return 1;
				return -1;
			}
			if (y1 == y2)
				return 0;
			if (y1 > y2)
				return 1;
			return -1;
		}
	}

	private static class NameMatchSpatially implements Comparator<NodeMatch> {
		public int compare(NodeMatch nm1, NodeMatch nm2) {
			NodeInst n1 = nm1.ni;
			NodeInst n2 = nm2.ni;
			double x1 = n1.getAnchorCenterX();
			double y1 = n1.getAnchorCenterY();
			double x2 = n2.getAnchorCenterX();
			double y2 = n2.getAnchorCenterY();
			if (y1 == y2) {
				if (x1 == x2)
					return 0;
				if (x1 > x2)
					return 1;
				return -1;
			}
			if (y1 == y2)
				return 0;
			if (y1 > y2)
				return 1;
			return -1;
		}
	}

	/****************************** GENERAL ROUTING OPTIONS ******************************/

	private static Pref cachePreferredRoutingArc = Pref.makeStringPref("PreferredRoutingArc",
			Routing.tool.prefs, "");

	/**
	 * Method to return the name of the arc that should be used as a default by
	 * the stitching routers. The default is "".
	 *
	 * @return the name of the arc that should be used as a default by the
	 *         stitching routers.
	 */
	public static String getPreferredRoutingArc() {
		return cachePreferredRoutingArc.getString();
	}

	/**
	 * Method to set the name of the arc that should be used as a default by the
	 * stitching routers.
	 *
	 * @param arcName
	 *            the name of the arc that should be used as a default by the
	 *            stitching routers.
	 */
	public static void setPreferredRoutingArc(String arcName) {
		cachePreferredRoutingArc.setString(arcName);
	}

	/**
	 * Method to return the name of the arc that should be used as a factory
	 * default by the stitching routers.
	 *
	 * @return the name of the arc that should be used as a factory default by
	 *         the stitching routers.
	 */
	public static String getFactoryPreferredRoutingArc() {
		return cachePreferredRoutingArc.getStringFactoryValue();
	}

	/****************************** AUTO-STITCHING OPTIONS ******************************/

	private static Pref cacheAutoStitchOn = Pref.makeBooleanPref("AutoStitchOn", Routing.tool.prefs, false);

	/**
	 * Method to tell whether Auto-stitching should be done. The default is
	 * "false".
	 *
	 * @return true if Auto-stitching should be done.
	 */
	public static boolean isAutoStitchOn() {
		return cacheAutoStitchOn.getBoolean();
	}

	/**
	 * Method to set whether Auto-stitching should be done.
	 *
	 * @param on
	 *            true if Auto-stitching should be done.
	 */
	public static void setAutoStitchOn(boolean on) {
		cacheAutoStitchOn.setBoolean(on);
	}

	/**
	 * Method to tell whether Auto-stitching should be done, by default.
	 *
	 * @return true if Auto-stitching should be done, by default.
	 */
	public static boolean isFactoryAutoStitchOn() {
		return cacheAutoStitchOn.getBooleanFactoryValue();
	}

	private static Pref cacheAutoStitchCreateExports = Pref.makeBooleanPref("AutoStitchCreateExports",
			Routing.tool.prefs, false);

	/**
	 * Method to tell whether Auto-stitching should create exports if necessary.
	 * The default is "false".
	 *
	 * @return true if Auto-stitching should create exports if necessary.
	 */
	public static boolean isAutoStitchCreateExports() {
		return cacheAutoStitchCreateExports.getBoolean();
	}

	/**
	 * Method to set whether Auto-stitching should create exports if necessary.
	 *
	 * @param on
	 *            true if Auto-stitching should create exports if necessary.
	 */
	public static void setAutoStitchCreateExports(boolean on) {
		cacheAutoStitchCreateExports.setBoolean(on);
	}

	/**
	 * Method to tell whether Auto-stitching should create exports if necessary,
	 * by default.
	 *
	 * @return true if Auto-stitching should create exports if necessary, by
	 *         default.
	 */
	public static boolean isFactoryAutoStitchCreateExports() {
		return cacheAutoStitchCreateExports.getBooleanFactoryValue();
	}

	/****************************** MIMIC-STITCHING OPTIONS ******************************/

	private static Pref cacheMimicStitchOn = Pref.makeBooleanPref("MimicStitchOn", Routing.tool.prefs, false);

	/**
	 * Method to tell whether Mimic-stitching should be done. The default is "false".
	 * @return true if Mimic-stitching should be done.
	 */
	public static boolean isMimicStitchOn() {
		return cacheMimicStitchOn.getBoolean();
	}

	/**
	 * Method to set whether Mimic-stitching should be done.
	 * @param on true if Mimic-stitching should be done.
	 */
	public static void setMimicStitchOn(boolean on) {
		cacheMimicStitchOn.setBoolean(on);
	}

	/**
	 * Method to tell whether Mimic-stitching should be done, by default.
	 * @return true if Mimic-stitching should be done, by default.
	 */
	public static boolean isFactoryMimicStitchOn() {
		return cacheMimicStitchOn.getBooleanFactoryValue();
	}

	private static Pref cacheMimicStitchInteractive = Pref.makeBooleanPref("MimicStitchInteractive",
			Routing.tool.prefs, false);

	/**
	 * Method to tell whether Mimic-stitching runs interactively. During
	 * interactive Mimic stitching, each new set of arcs is shown to the user
	 * for confirmation. The default is "false".
	 * @return true if Mimic-stitching runs interactively.
	 */
	public static boolean isMimicStitchInteractive() {
		return cacheMimicStitchInteractive.getBoolean();
	}

	/**
	 * Method to set whether Mimic-stitching runs interactively. During
	 * interactive Mimic stitching, each new set of arcs is shown to the user
	 * for confirmation.
	 * @param on true if Mimic-stitching runs interactively.
	 */
	public static void setMimicStitchInteractive(boolean on) {
		cacheMimicStitchInteractive.setBoolean(on);
	}

	/**
	 * Method to tell whether Mimic-stitching runs interactively, by default.
	 * During interactive Mimic stitching, each new set of arcs is shown to the
	 * user for confirmation.
	 * @return true if Mimic-stitching runs interactively, by default.
	 */
	public static boolean isFactoryMimicStitchInteractive() {
		return cacheMimicStitchInteractive.getBooleanFactoryValue();
	}

	private static Pref cacheMimicStitchPinsKept = Pref.makeBooleanPref("MimicStitchPinsKept",
			Routing.tool.prefs, false);

	/**
	 * Method to tell whether Mimic-stitching keeps pins even if it has no arc
	 * connections. The default is "false".
	 * @return true if Mimic-stitching runs interactively.
	 */
	public static boolean isMimicStitchPinsKept() {
		return cacheMimicStitchPinsKept.getBoolean();
	}

	/**
	 * Method to set whether Mimic-stitching keeps pins even if it has no arc connections.
	 * @param on true if Mimic-stitching runs interactively.
	 */
	public static void setMimicStitchPinsKept(boolean on) {
		cacheMimicStitchPinsKept.setBoolean(on);
	}

	/**
	 * Method to tell whether Mimic-stitching keeps pins even if it has no arc
	 * connections, by default.
	 * @return true if Mimic-stitching runs interactively, by default.
	 */
	public static boolean isFactoryMimicStitchPinsKept() {
		return cacheMimicStitchPinsKept.getBooleanFactoryValue();
	}

	private static Pref cacheMimicStitchMatchPorts = Pref.makeBooleanPref("MimicStitchMatchPorts",
			Routing.tool.prefs, false);

	/**
	 * Method to tell whether Mimic-stitching only works between matching ports.
	 * The default is "false".
	 * @return true if Mimic-stitching only works between matching ports.
	 */
	public static boolean isMimicStitchMatchPorts() {
		return cacheMimicStitchMatchPorts.getBoolean();
	}

	/**
	 * Method to set whether Mimic-stitching only works between matching ports.
	 * @param on true if Mimic-stitching only works between matching ports.
	 */
	public static void setMimicStitchMatchPorts(boolean on) {
		cacheMimicStitchMatchPorts.setBoolean(on);
	}

	/**
	 * Method to tell whether Mimic-stitching only works between matching ports, by default.
	 * @return true if Mimic-stitching only works between matching ports, by default.
	 */
	public static boolean isFactoryMimicStitchMatchPorts() {
		return cacheMimicStitchMatchPorts.getBooleanFactoryValue();
	}

	private static Pref cacheMimicStitchMatchPortWidth = Pref.makeBooleanPref("MimicStitchMatchPortWidth",
			Routing.tool.prefs, true);

	/**
	 * Method to tell whether Mimic-stitching only works between ports of the
	 * same width. This applies only in the case of busses. The default is "true".
	 * @return true if Mimic-stitching only works between matching ports.
	 */
	public static boolean isMimicStitchMatchPortWidth() {
		return cacheMimicStitchMatchPortWidth.getBoolean();
	}

	/**
	 * Method to set whether Mimic-stitching only works between ports of the
	 * same width. This applies only in the case of busses.
	 * @param on true if Mimic-stitching only works between ports of the same width.
	 */
	public static void setMimicStitchMatchPortWidth(boolean on) {
		cacheMimicStitchMatchPortWidth.setBoolean(on);
	}

	/**
	 * Method to tell whether Mimic-stitching only works between ports of the
	 * same width, by default. This applies only in the case of busses.
	 * @return true if Mimic-stitching only works between matching ports, by default.
	 */
	public static boolean isFactoryMimicStitchMatchPortWidth() {
		return cacheMimicStitchMatchPortWidth.getBooleanFactoryValue();
	}

	private static Pref cacheMimicStitchMatchNumArcs = Pref.makeBooleanPref("MimicStitchMatchNumArcs",
			Routing.tool.prefs, false);

	/**
	 * Method to tell whether Mimic-stitching only works when the number of
	 * existing arcs matches. The default is "false".
	 * @return true if Mimic-stitching only works when the number of existing arcs matches.
	 */
	public static boolean isMimicStitchMatchNumArcs() {
		return cacheMimicStitchMatchNumArcs.getBoolean();
	}

	/**
	 * Method to set whether Mimic-stitching only works when the number of
	 * existing arcs matches.
	 * @param on true if Mimic-stitching only works when the number of existing arcs matches.
	 */
	public static void setMimicStitchMatchNumArcs(boolean on) {
		cacheMimicStitchMatchNumArcs.setBoolean(on);
	}

	/**
	 * Method to tell whether Mimic-stitching only works when the number of
	 * existing arcs matches, by default.
	 * @return true if Mimic-stitching only works when the number of existing arcs matches, by default.
	 */
	public static boolean isFactoryMimicStitchMatchNumArcs() {
		return cacheMimicStitchMatchNumArcs.getBooleanFactoryValue();
	}

	private static Pref cacheMimicStitchMatchNodeSize = Pref.makeBooleanPref("MimicStitchMatchNodeSize",
			Routing.tool.prefs, false);

	/**
	 * Method to tell whether Mimic-stitching only works when the node sizes are
	 * the same. The default is "false".
	 * @return true if Mimic-stitching only works when the node sizes are the same.
	 */
	public static boolean isMimicStitchMatchNodeSize() {
		return cacheMimicStitchMatchNodeSize.getBoolean();
	}

	/**
	 * Method to set whether Mimic-stitching only works when the node sizes are the same.
	 * @param on true if Mimic-stitching only works when the node sizes are the same.
	 */
	public static void setMimicStitchMatchNodeSize(boolean on) {
		cacheMimicStitchMatchNodeSize.setBoolean(on);
	}

	/**
	 * Method to tell whether Mimic-stitching only works when the node sizes are
	 * the same, by default.
	 * @return true if Mimic-stitching only works when the node sizes are the same, by default.
	 */
	public static boolean isFactoryMimicStitchMatchNodeSize() {
		return cacheMimicStitchMatchNodeSize.getBooleanFactoryValue();
	}

	private static Pref cacheMimicStitchMatchNodeType = Pref.makeBooleanPref("MimicStitchMatchNodeType",
			Routing.tool.prefs, true);

	/**
	 * Method to tell whether Mimic-stitching only works when the nodes have the
	 * same type. The default is "true".
	 * @return true if Mimic-stitching only works when the nodes have the same type.
	 */
	public static boolean isMimicStitchMatchNodeType() {
		return cacheMimicStitchMatchNodeType.getBoolean();
	}

	/**
	 * Method to set whether Mimic-stitching only works when the nodes have the same type.
	 * @param on true if Mimic-stitching only works when the nodes have the same type.
	 */
	public static void setMimicStitchMatchNodeType(boolean on) {
		cacheMimicStitchMatchNodeType.setBoolean(on);
	}

	/**
	 * Method to tell whether Mimic-stitching only works when the nodes have the same type, by default.
	 * @return true if Mimic-stitching only works when the nodes have the same type, by default.
	 */
	public static boolean isFactoryMimicStitchMatchNodeType() {
		return cacheMimicStitchMatchNodeType.getBooleanFactoryValue();
	}

	private static Pref cacheMimicStitchNoOtherArcsSameDir = Pref.makeBooleanPref(
			"MimicStitchNoOtherArcsSameDir", Routing.tool.prefs, true);

	/**
	 * Method to tell whether Mimic-stitching only works when there are no other
	 * arcs running in the same direction. The default is "true".
	 * @return true if Mimic-stitching only works when there are no other arcs
	 * running in the same direction.
	 */
	public static boolean isMimicStitchNoOtherArcsSameDir() {
		return cacheMimicStitchNoOtherArcsSameDir.getBoolean();
	}

	/**
	 * Method to set whether Mimic-stitching only works when there are no other
	 * arcs running in the same direction.
	 * @param on true if Mimic-stitching only works when there are no other
	 * arcs running in the same direction.
	 */
	public static void setMimicStitchNoOtherArcsSameDir(boolean on) {
		cacheMimicStitchNoOtherArcsSameDir.setBoolean(on);
	}

	/**
	 * Method to tell whether Mimic-stitching only works when there are no other
	 * arcs running in the same direction, by default.
	 * @return true if Mimic-stitching only works when there are no other arcs
	 *         running in the same direction, by default.
	 */
	public static boolean isFactoryMimicStitchNoOtherArcsSameDir() {
		return cacheMimicStitchNoOtherArcsSameDir.getBooleanFactoryValue();
	}

	private static Pref cacheMimicStitchOnlyNewTopology = Pref.makeBooleanPref("MimicStitchOnlyNewTopology",
			Routing.tool.prefs, true);

	/**
	 * Method to tell whether Mimic-stitching creates arcs only where not
	 * already connected. If a connection is already made elsewhere, the new one
	 * is not made. The default is "true".
	 * @return true if Mimic-stitching creates arcs only where not already connected.
	 */
	public static boolean isMimicStitchOnlyNewTopology() {
		return cacheMimicStitchOnlyNewTopology.getBoolean();
	}

	/**
	 * Method to set whether Mimic-stitching creates arcs only where not already
	 * connected. If a connection is already made elsewhere, the new one is not made.
	 * @param on true if Mimic-stitching creates arcs only where not already connected.
	 */
	public static void setMimicStitchOnlyNewTopology(boolean on) {
		cacheMimicStitchOnlyNewTopology.setBoolean(on);
	}

	/**
	 * Method to tell whether Mimic-stitching creates arcs only where not
	 * already connected, by default. If a connection is already made elsewhere,
	 * the new one is not made.
	 * @return true if Mimic-stitching creates arcs only where not already
	 * connected, by default.
	 */
	public static boolean isFactoryMimicStitchOnlyNewTopology() {
		return cacheMimicStitchOnlyNewTopology.getBooleanFactoryValue();
	}

	/****************************** SEA-OF-GATES ROUTER OPTIONS ******************************/

	private static Pref cacheSOGMaxWidth = Pref.makeDoublePref("SeaOfGatesMaxWidth",
			Routing.getRoutingTool().prefs, 10);

	/**
	 * Method to get the "sea-of-gates" maximum arc width. Since the SOG router
	 * places arcs that are as wide as the widest arc on the net, this may be
	 * too large (especially near pads). This value limits the width.
	 * @return the maximum arc width in sea-of-gates routing.
	 */
	public static double getSeaOfGatesMaxWidth() {
		return cacheSOGMaxWidth.getDouble();
	}

	/**
	 * Method to set the "sea-of-gates" maximum arc width. Since the SOG router
	 * places arcs that are as wide as the widest arc on the net, this may be
	 * too large (especially near pads). This value limits the width.
	 * @param w the maximum arc width in sea-of-gates routing.
	 */
	public static void setSeaOfGatesMaxWidth(double w) {
		cacheSOGMaxWidth.setDouble(w);
	}

	/**
	 * Method to get the "sea-of-gates" maximum arc width, by default. Since the
	 * SOG router places arcs that are as wide as the widest arc on the net,
	 * this may be too large (especially near pads). This value limits the width.
	 * @return the maximum arc width in sea-of-gates routing, by default.
	 */
	public static double getFactorySeaOfGatesMaxWidth() {
		return cacheSOGMaxWidth.getDoubleFactoryValue();
	}

	private static Pref cacheSOGComplexityLimit = Pref.makeIntPref("SeaOfGatesComplexityLimit", Routing.getRoutingTool().prefs, 200000);

	/**
	 * Method to get the "sea-of-gates" complexity limit. This is the maximum
	 * number of steps allowed when searching for a routing path.
	 * @return the "sea-of-gates" complexity limit.
	 */
	public static int getSeaOfGatesComplexityLimit() { return cacheSOGComplexityLimit.getInt(); }

	/**
	 * Method to set the "sea-of-gates" complexity limit. This is the maximum
	 * number of steps allowed when searching for a routing path.
	 * @param c the "sea-of-gates" complexity limit.
	 */
	public static void setSeaOfGatesComplexityLimit(int c) { cacheSOGComplexityLimit.setInt(c); }

	/**
	 * Method to get the "sea-of-gates" complexity limit, by default. This is
	 * the maximum number of steps allowed when searching for a routing path.
	 * @return the "sea-of-gates" complexity limit, by default.
	 */
	public static int getFactorySeaOfGatesComplexityLimit() { return cacheSOGComplexityLimit.getIntFactoryValue(); }

	private static Pref cacheSOGRerunComplexityLimit = Pref.makeIntPref("SeaOfGatesRerunComplexityLimit", Routing.getRoutingTool().prefs, 400000);

	/**
	 * Method to get the "sea-of-gates" complexity limit when rerunning failed arcs.
	 * This is the maximum number of steps allowed when searching for a routing path.
	 * @return the "sea-of-gates" complexity limit when rerunning failed arcs.
	 */
	public static int getSeaOfGatesRerunComplexityLimit() { return cacheSOGRerunComplexityLimit.getInt(); }

	/**
	 * Method to set the "sea-of-gates" complexity limit when rerunning failed arcs.
	 * This is the maximum number of steps allowed when searching for a routing path.
	 * @param c the "sea-of-gates" complexity limit when rerunning failed arcs.
	 */
	public static void setSeaOfGatesRerunComplexityLimit(int c) { cacheSOGRerunComplexityLimit.setInt(c); }

	/**
	 * Method to get the "sea-of-gates" complexity limit when rerunning failed arcs, by default.
	 * This is the maximum number of steps allowed when searching for a routing path.
	 * @return the "sea-of-gates" complexity limit when rerunning failed arcs, by default.
	 */
	public static int getFactorySeaOfGatesRerunComplexityLimit() { return cacheSOGRerunComplexityLimit.getIntFactoryValue(); }

	private static Pref cacheSOGMaxDistance = Pref.makeIntPref("SeaOfGatesMaxDistance", Routing.getRoutingTool().prefs, 10);
	/**
	 * Method to get the "sea-of-gates" maximum distance to search from router bound. 
	 * @return the "sea-of-gates" maximum distance.
	 */
	public static int getSeaOfGatesMaxDistance() { return cacheSOGMaxDistance.getInt(); }

	/**
	 * Method to set the "sea-of-gates" maximum distance to search from router bound. 
	 * @param c the "sea-of-gates" maximum distance.
	 */
	public static void setSeaOfGatesMaxDistance(int c) { cacheSOGMaxDistance.setInt(c); }

	/**
	 * Method to get the "sea-of-gates" maximum distance to search from router bound. 
	 * @return the "sea-of-gates" maximum distance, by default.
	 */
	public static int getFactorySeaOfGatesMaxDistance() { return cacheSOGMaxDistance.getIntFactoryValue(); }
	
	private static Pref cacheSOGUseGlobalRouting = Pref.makeBooleanPref("SeaOfGatesUseGlobalRouting", Routing.getRoutingTool().prefs, false);

	/**
	 * Method to tell whether the "sea-of-gates" router does global routing.
	 * This preprocessing step gives the router guidelines for lower-congestion paths.
	 * @return true if the "sea-of-gates" router does global routing.
	 */
	public static boolean isSeaOfGatesUseGlobalRouting() { return cacheSOGUseGlobalRouting.getBoolean(); }

	/**
	 * Method to set whether the "sea-of-gates" router does global routing.
	 * This preprocessing step gives the router guidelines for lower-congestion paths.
	 * @param p true if the "sea-of-gates" router does global routing.
	 */
	public static void setSeaOfGatesUseGlobalRouting(boolean p) { cacheSOGUseGlobalRouting.setBoolean(p); }

	/**
	 * Method to tell whether the "sea-of-gates" router does global routing, by default.
	 * This preprocessing step gives the router guidelines for lower-congestion paths.
	 * @return true if the "sea-of-gates" router does global routing, by default.
	 */
	public static boolean isFactorySeaOfGatesUseGlobalRouting() { return cacheSOGUseGlobalRouting.getBooleanFactoryValue(); }

	private static Pref cacheSOGRerunFailedRoutes = Pref.makeBooleanPref("SeaOfGatesRerunFailedRoutes", Routing.getRoutingTool().prefs, true);

	/**
	 * Method to tell whether the "sea-of-gates" router should re-route those that failed in a second pass.
	 * @return true if the "sea-of-gates" router should re-route those that failed in a second pass.
	 */
	public static boolean isSeaOfGatesRerunFailedRoutes() { return cacheSOGRerunFailedRoutes.getBoolean(); }

	/**
	 * Method to set whether the "sea-of-gates" router should re-route those that failed in a second pass.
	 * @param p true if the "sea-of-gates" router should re-route those that failed in a second pass.
	 */
	public static void setSeaOfGatesRerunFailedRoutes(boolean p) { cacheSOGRerunFailedRoutes.setBoolean(p); }

	/**
	 * Method to tell whether the "sea-of-gates" router should re-route those that failed in a second pass, by default.
	 * @return true if the "sea-of-gates" router should re-route those that failed in a second pass, by default.
	 */
	public static boolean isFactorySeaOfGatesRerunFailedRoutes() { return cacheSOGRerunFailedRoutes.getBooleanFactoryValue(); }

	private static Pref cacheSOGRunOnConnectedRoutes = Pref.makeBooleanPref("SeaOfGatesRunOnConnectedRoutes", Routing.getRoutingTool().prefs, true);

	/**
	 * Method to tell whether the "sea-of-gates" router should run on routes that are already connected.
	 * The router analyzes existing geometry and detects when the routes are already connected.
	 * If this is false and the route is connected, no routing is done.
	 * @return true if the "sea-of-gates" router should run on routes that are already connected.
	 */
	public static boolean isSeaOfGatesRunOnConnectedRoutes() { return cacheSOGRunOnConnectedRoutes.getBoolean(); }

	/**
	 * Method to set whether the "sea-of-gates" router should run on routes that are already connected.
	 * The router analyzes existing geometry and detects when the routes are already connected.
	 * If this is false and the route is connected, no routing is done.
	 * @param p true if the "sea-of-gates" router should run on routes that are already connected.
	 */
	public static void setSeaOfGatesRunOnConnectedRoutes(boolean p) { cacheSOGRunOnConnectedRoutes.setBoolean(p); }

	/**
	 * Method to tell whether the "sea-of-gates" router should run on routes that are already connected, by default.
	 * The router analyzes existing geometry and detects when the routes are already connected.
	 * If this is false and the route is connected, no routing is done.
	 * @return true if the "sea-of-gates" router should run on routes that are already connected, by default.
	 */
	public static boolean isFactorySeaOfGatesRunOnConnectedRoutes() { return cacheSOGRunOnConnectedRoutes.getBooleanFactoryValue(); }

	private static Pref cacheSOGNetOrder = Pref.makeIntPref("SeaOfGatesNetOrder", Routing.getRoutingTool().prefs, SoGNetOrder.SOGNETORDERORIGINAL.getValue());

	/**
	 * Method to tell the "sea-of-gates" router how to sort the nets to route.
	 * @return value associated to the different SoGNetOrder modes: (1) order provided by user (SOGNETORDERGIVEN, default),
	 * (2) descending order given by arc lengths and (3) ascending order given by arc lengths.
	 */
	public static SoGNetOrder getSeaOfGatesNetOrder() { return SoGNetOrder.findByValue(cacheSOGNetOrder.getInt()); }

	/**
	 * Method to set the "sea-of-gates" router how to sort the nets to route.
	 * (1) order provided by user (SOGNETORDERGIVEN, default),
	 * (2) descending order given by arc lengths and (3) ascending order given by arc lengths.
	 */
	public static void setSeaOfGatesNetOrder(SoGNetOrder p) { cacheSOGNetOrder.setInt(p.getValue()); }

	/**
	 * Method to tell the "sea-of-gates" router how to sort the nets to route, by default
	 * @return value associated to the different SoGNetOrder modes: (1) order provided by user (SOGNETORDERORIGINAL, default),
	 * (2) descending order given by arc lengths and (3) ascending order given by arc lengths.
	 */
	public static SoGNetOrder getFactorySeaOfGatesNetOrder() { return SoGNetOrder.findByValue(cacheSOGNetOrder.getIntFactoryValue()); }
	
	public enum SoGNetOrder {
		SOGNETORDERDESCENDING(1), // Sorting nets in descending length order -> longest first
		SOGNETORDERASCENDING(2), // Sorting nets in ascending length order -> shortest first
		SOGNETORDERGIVEN(3), // Given by user/file
		SOGNETORDERBUS(4), // Route busses first
		SOGNETORDERPERCENTAGE(5), // Idea that X% of shortest, Y% largest and the (1-X-Y) at the end.
		SOGNETORDERORIGINAL(0); // String sorting on net Network/RouteBatch comparators. Original code
		
		private int value = -1;
		
		SoGNetOrder(int val)
		{
			value = val; // level
		}
		
		int getValue() {return value;}
		public static SoGNetOrder findByValue(int level)
		{
			for (SoGNetOrder s : SoGNetOrder.values())
			{
				if (s.getValue() == level) return s;
			}
			assert(false); // should never reach this code.
			return null;
		}
	}
	
	public enum SoGContactsStrategy {
		SOGCONTACTSATTOPLEVEL         (0, "Contacts at top level"), 
		SOGCONTACTSUSEEXISTINGSUBCELLS(1, "Contacts use existing subcells or place at top"), 
		SOGCONTACTSALWAYSINSUBCELLS   (2, "Contacts use existing subcells or create new ones"), 
		SOGCONTACTSFORCESUBCELLS      (3, "Contacts create new subcells");
		
		String explanation;
		int level;
	
		SoGContactsStrategy(int num, String name)
		{
			level = num;
			explanation = name;
		}
		
		int getLevel() {return level;}
		public static SoGContactsStrategy findByLevel(int level)
		{
			for (SoGContactsStrategy s : SoGContactsStrategy.values())
			{
				if (s.getLevel() == level) return s;
			}
			assert(false); // should never reach this code.
			return null;
		}
		
		@Override
		public String toString() {return explanation;}
	};

	private static Pref cacheSOGContactPlacementAction = Pref.makeIntPref("SeaOfGatesContactPlacementAction", Routing.getRoutingTool().prefs, 
			SoGContactsStrategy.SOGCONTACTSATTOPLEVEL.getLevel());

	/**
	 * Method to tell how the "sea-of-gates" router should place contacts.
	 * @return
	 * 0 (SOGCONTACTSATTOPLEVEL) to place contacts at the top level in the cell.
	 * 1 (SOGCONTACTSUSEEXISTINGSUBCELLS) to use existing subcells for contacts (if the cells match the contact geometry).
	 * 2 (SOGCONTACTSALWAYSINSUBCELLS) to always use subcells for contacts (use existing ones if they match, create new ones if necessary).
	 * 2 (SOGCONTACTSFORCESUBCELLS) to always create new subcells for contacts.
	 */
	public static int getSeaOfGatesContactPlacementAction() { return cacheSOGContactPlacementAction.getInt(); }

	/**
	 * Method to set how the "sea-of-gates" router should place contacts.
	 * @param a
	 * 0 (SOGCONTACTSATTOPLEVEL) to place contacts at the top level in the cell.
	 * 1 (SOGCONTACTSUSEEXISTINGSUBCELLS) to use existing subcells for contacts (if the cells match the contact geometry).
	 * 2 (SOGCONTACTSALWAYSINSUBCELLS) to always use subcells for contacts (use existing ones if they match, create new ones if necessary).
	 * 2 (SOGCONTACTSFORCESUBCELLS) to always create new subcells for contacts.
	 */
	public static void setSeaOfGatesContactPlacementAction(int a) { cacheSOGContactPlacementAction.setInt(a); }

	/**
	 * Method to tell how the "sea-of-gates" router should place contacts, by default.
	 * @return
	 * 0 (SOGCONTACTSATTOPLEVEL) to place contacts at the top level in the cell.
	 * 1 (SOGCONTACTSUSEEXISTINGSUBCELLS) to use existing subcells for contacts (if the cells match the contact geometry).
	 * 2 (SOGCONTACTSALWAYSINSUBCELLS) to always use subcells for contacts (use existing ones if they match, create new ones if necessary).
	 * 2 (SOGCONTACTSFORCESUBCELLS) to always create new subcells for contacts.
	 */
	public static int getFactorySeaOfGatesContactPlacementAction() { return cacheSOGContactPlacementAction.getIntFactoryValue(); }

	private static Pref cacheSOGEnableSpineRouting = Pref.makeBooleanPref("SeaOfGatesEnableSpineRouting", Routing.getRoutingTool().prefs, false);

	/**
	 * Method to tell whether the "sea-of-gates" router should detect and handle spine routing.
	 * Spine routes are for long colinear daisy-chains that should be routed end-to-end with taps,
	 * instead of hopping from tap to tap.
	 * @return true if the "sea-of-gates" router should detect and handle spine routing.
	 */
	public static boolean isSeaOfGatesEnableSpineRouting() { return cacheSOGEnableSpineRouting.getBoolean(); }

	/**
	 * Method to set whether the "sea-of-gates" router should detect and handle spine routing.
	 * Spine routes are for long colinear daisy-chains that should be routed end-to-end with taps,
	 * instead of hopping from tap to tap.
	 * @param p true if the "sea-of-gates" router should detect and handle spine routing.
	 */
	public static void setSeaOfGatesEnableSpineRouting(boolean p) { cacheSOGEnableSpineRouting.setBoolean(p); }

	/**
	 * Method to tell whether the "sea-of-gates" router should detect and handle spine routing, by default.
	 * Spine routes are for long colinear daisy-chains that should be routed end-to-end with taps,
	 * instead of hopping from tap to tap.
	 * @return true if the "sea-of-gates" router should detect and handle spine routing, by default.
	 */
	public static boolean isFactorySeaOfGatesEnableSpineRouting() { return cacheSOGEnableSpineRouting.getBooleanFactoryValue(); }

	private static Pref cacheSOGForcedProcessorCount = Pref.makeIntPref("SeaOfGatesForcedProcessorCount", Routing.getRoutingTool().prefs, 0);

	/**
	 * Method to get the "sea-of-gates" forced processor count.
	 * This is the number of processors (and threads) to assume, independent of the actual processor count.
	 * @return the "sea-of-gates" forced processor count (0 to use the actual number of processors).
	 */
	public static int getSeaOfGatesForcedProcessorCount() { return cacheSOGForcedProcessorCount.getInt(); }

	/**
	 * Method to set the "sea-of-gates" forced processor count.
	 * This is the number of processors (and threads) to assume, independent of the actual processor count.
	 * @param c the "sea-of-gates" forced processor count (0 to use the actual number of processors).
	 */
	public static void setSeaOfGatesForcedProcessorCount(int c) { cacheSOGForcedProcessorCount.setInt(c); }

	/**
	 * Method to get the "sea-of-gates" forced processor count, by default.
	 * This is the number of processors (and threads) to assume, independent of the actual processor count.
	 * @return the "sea-of-gates" forced processor count, by default (0 to use the actual number of processors).
	 */
	public static int getFactorySeaOfGatesForcedProcessorCount() { return cacheSOGForcedProcessorCount.getIntFactoryValue(); }

	private static Pref cacheSOGUseParallelFromToRoutes = Pref.makeBooleanPref(
			"SeaOfGatesUseParallelFromToRoutes", Routing.getRoutingTool().prefs, true);

	/**
	 * Method to tell whether the "sea-of-gates" router does from/to analysis in
	 * parallel. Normally, a path is found by looking both from one end to the
	 * other, and then from the other end back to the first. The best result of
	 * these two searches is used as the route. When true, both paths are run in
	 * parallel on separate processors if there are multiple processors (default is true).
	 * @return true if the "sea-of-gates" router does from/to analysis in parallel.
	 */
	public static boolean isSeaOfGatesUseParallelFromToRoutes() {
		return cacheSOGUseParallelFromToRoutes.getBoolean();
	}

	/**
	 * Method to set whether the "sea-of-gates" router does from/to analysis in
	 * parallel. Normally, a path is found by looking both from one end to the
	 * other, and then from the other end back to the first. The best result of
	 * these two searches is used as the route. When true, both paths are run in
	 * parallel on separate processors if there are multiple processors (default is true).
	 * @param p true if the "sea-of-gates" router does from/to analysis in parallel.
	 */
	public static void setSeaOfGatesUseParallelFromToRoutes(boolean p) {
		cacheSOGUseParallelFromToRoutes.setBoolean(p);
	}

	/**
	 * Method to tell whether the "sea-of-gates" router does from/to analysis in
	 * parallel, by default. Normally, a path is found by looking both from one
	 * end to the other, and then from the other end back to the first. The best
	 * result of these two searches is used as the route. When true, both paths
	 * are run in parallel on separate processors if there are multiple processors.
	 * @return true if the "sea-of-gates" router does from/to analysis in parallel, by default.
	 */
	public static boolean isFactorySeaOfGatesUseParallelFromToRoutes() {
		return cacheSOGUseParallelFromToRoutes.getBooleanFactoryValue();
	}

	private static Pref cacheSOGUseParallelRoutes = Pref.makeBooleanPref("SeaOfGatesUseParallelRoutes",
			Routing.getRoutingTool().prefs, false);

	/**
	 * Method to tell whether the "sea-of-gates" router finds routes in
	 * parallel. When true, multiple routes are searched using parallel threads,
	 * if there are multiple processors (default is false).
	 * @return true if the "sea-of-gates" router finds routes in parallel.
	 */
	public static boolean isSeaOfGatesUseParallelRoutes() {
		return cacheSOGUseParallelRoutes.getBoolean();
	}

	/**
	 * Method to set whether the "sea-of-gates" router finds routes in parallel.
	 * When true, multiple routes are searched using parallel threads, if there
	 * are multiple processors (default is false).
	 * @param p true if the "sea-of-gates" router finds routes in parallel.
	 */
	public static void setSeaOfGatesUseParallelRoutes(boolean p) {
		cacheSOGUseParallelRoutes.setBoolean(p);
	}

	/**
	 * Method to tell whether the "sea-of-gates" router finds routes in
	 * parallel, by default. When true, multiple routes are searched using
	 * parallel threads, if there are multiple processors.
	 * @return true if the "sea-of-gates" router finds routes in parallel, by default.
	 */
	public static boolean isFactorySeaOfGatesUseParallelRoutes() {
		return cacheSOGUseParallelRoutes.getBooleanFactoryValue();
	}

	/****************************** SUN ROUTER OPTIONS ******************************/

	private static Pref cacheSLRVerboseLevel = Pref.makeIntPref("SunRouterVerboseLevel", Routing
			.getRoutingTool().prefs, 2);

	/** verbose level can be 0: silent, 1: quiet, 2: normal 3: verbose */
	public static int getSunRouterVerboseLevel() {
		return cacheSLRVerboseLevel.getInt();
	}

	public static void setSunRouterVerboseLevel(int v) {
		cacheSLRVerboseLevel.setInt(v);
	}

	public static int getFactorySunRouterVerboseLevel() {
		return cacheSLRVerboseLevel.getIntFactoryValue();
	}

	private static Pref cacheSLRCostLimit = Pref.makeDoublePref("SunRouterCostLimit", Routing
			.getRoutingTool().prefs, 10);

	public static double getSunRouterCostLimit() {
		return cacheSLRCostLimit.getDouble();
	}

	public static void setSunRouterCostLimit(double r) {
		cacheSLRCostLimit.setDouble(r);
	}

	public static double getFactorySunRouterCostLimit() {
		return cacheSLRCostLimit.getDoubleFactoryValue();
	}

	private static Pref cacheSLRCutlineDeviation = Pref.makeDoublePref("SunRouterCutlineDeviation", Routing
			.getRoutingTool().prefs, 0.1);

	public static double getSunRouterCutlineDeviation() {
		return cacheSLRCutlineDeviation.getDouble();
	}

	public static void setSunRouterCutlineDeviation(double r) {
		cacheSLRCutlineDeviation.setDouble(r);
	}

	public static double getFactorySunRouterCutlineDeviation() {
		return cacheSLRCutlineDeviation.getDoubleFactoryValue();
	}

	private static Pref cacheSLRDelta = Pref.makeDoublePref("SunRouterDelta", Routing.getRoutingTool().prefs,
			1);

	public static double getSunRouterDelta() {
		return cacheSLRDelta.getDouble();
	}

	public static void setSunRouterDelta(double r) {
		cacheSLRDelta.setDouble(r);
	}

	public static double getFactorySunRouterDelta() {
		return cacheSLRDelta.getDoubleFactoryValue();
	}

	private static Pref cacheSLRXBitSize = Pref.makeIntPref("SunRouterXBitSize",
			Routing.getRoutingTool().prefs, 20);

	public static int getSunRouterXBitSize() {
		return cacheSLRXBitSize.getInt();
	}

	public static void setSunRouterXBitSize(int r) {
		cacheSLRXBitSize.setInt(r);
	}

	public static int getFactorySunRouterXBitSize() {
		return cacheSLRXBitSize.getIntFactoryValue();
	}

	private static Pref cacheSLRYBitSize = Pref.makeIntPref("SunRouterYBitSize",
			Routing.getRoutingTool().prefs, 20);

	public static int getSunRouterYBitSize() {
		return cacheSLRYBitSize.getInt();
	}

	public static void setSunRouterYBitSize(int r) {
		cacheSLRYBitSize.setInt(r);
	}

	public static int getFactorySunRouterYBitSize() {
		return cacheSLRYBitSize.getIntFactoryValue();
	}

	private static Pref cacheSLRXTileSize = Pref.makeIntPref("SunRouterXTileSize",
			Routing.getRoutingTool().prefs, 40);

	public static int getSunRouterXTileSize() {
		return cacheSLRXTileSize.getInt();
	}

	public static void setSunRouterXTileSize(int r) {
		cacheSLRXTileSize.setInt(r);
	}

	public static int getFactorySunRouterXTileSize() {
		return cacheSLRXTileSize.getIntFactoryValue();
	}

	private static Pref cacheSLRYTileSize = Pref.makeIntPref("SunRouterYTileSize",
			Routing.getRoutingTool().prefs, 40);

	public static int getSunRouterYTileSize() {
		return cacheSLRYTileSize.getInt();
	}

	public static void setSunRouterYTileSize(int r) {
		cacheSLRYTileSize.setInt(r);
	}

	public static int getFactorySunRouterYTileSize() {
		return cacheSLRYTileSize.getIntFactoryValue();
	}

	private static Pref cacheSLRLayerAssgnCapF = Pref.makeDoublePref("SunRouterLayerAssgnCapF", Routing
			.getRoutingTool().prefs, 0.9);

	public static double getSunRouterLayerAssgnCapF() {
		return cacheSLRLayerAssgnCapF.getDouble();
	}

	public static void setSunRouterLayerAssgnCapF(double r) {
		cacheSLRLayerAssgnCapF.setDouble(r);
	}

	public static double getFactorySunRouterLayerAssgnCapF() {
		return cacheSLRLayerAssgnCapF.getDoubleFactoryValue();
	}

	private static Pref cacheSLRLengthLongNet = Pref.makeDoublePref("SunRouterLengthLongNet", Routing
			.getRoutingTool().prefs, 0);

	public static double getSunRouterLengthLongNet() {
		return cacheSLRLengthLongNet.getDouble();
	}

	public static void setSunRouterLengthLongNet(double r) {
		cacheSLRLengthLongNet.setDouble(r);
	}

	public static double getFactorySunRouterLengthLongNet() {
		return cacheSLRLengthLongNet.getDoubleFactoryValue();
	}

	private static Pref cacheSLRLengthMedNet = Pref.makeDoublePref("SunRouterLengthMedNet", Routing
			.getRoutingTool().prefs, 0);

	public static double getSunRouterLengthMedNet() {
		return cacheSLRLengthMedNet.getDouble();
	}

	public static void setSunRouterLengthMedNet(double r) {
		cacheSLRLengthMedNet.setDouble(r);
	}

	public static double getFactorySunRouterLengthMedNet() {
		return cacheSLRLengthMedNet.getDoubleFactoryValue();
	}

	private static Pref cacheSLRTilesPerPinLongNet = Pref.makeDoublePref("SunRouterTilesPerPinLongNet",
			Routing.getRoutingTool().prefs, 5);

	public static double getSunRouterTilesPerPinLongNet() {
		return cacheSLRTilesPerPinLongNet.getDouble();
	}

	public static void setSunRouterTilesPerPinLongNet(double r) {
		cacheSLRTilesPerPinLongNet.setDouble(r);
	}

	public static double getFactorySunRouterTilesPerPinLongNet() {
		return cacheSLRTilesPerPinLongNet.getDoubleFactoryValue();
	}

	private static Pref cacheSLRTilesPerPinMedNet = Pref.makeDoublePref("SunRouterTilesPerPinMedNet", Routing
			.getRoutingTool().prefs, 3);

	public static double getSunRouterTilesPerPinMedNet() {
		return cacheSLRTilesPerPinMedNet.getDouble();
	}

	public static void setSunRouterTilesPerPinMedNet(double r) {
		cacheSLRTilesPerPinMedNet.setDouble(r);
	}

	public static double getFactorySunRouterTilesPerPinMedNet() {
		return cacheSLRTilesPerPinMedNet.getDoubleFactoryValue();
	}

	private static Pref cacheSLROneTileFactor = Pref.makeDoublePref("SunRouterOneTileFactor", Routing
			.getRoutingTool().prefs, 2.65);

	public static double getSunRouterOneTileFactor() {
		return cacheSLROneTileFactor.getDouble();
	}

	public static void setSunRouterOneTileFactor(double r) {
		cacheSLROneTileFactor.setDouble(r);
	}

	public static double getFactorySunRouterOneTileFactor() {
		return cacheSLROneTileFactor.getDoubleFactoryValue();
	}

	private static Pref cacheSLROverloadLimit = Pref.makeIntPref("SunRouterOverloadLimit", Routing
			.getRoutingTool().prefs, 10);

	public static int getSunRouterOverloadLimit() {
		return cacheSLROverloadLimit.getInt();
	}

	public static void setSunRouterOverloadLimit(int r) {
		cacheSLROverloadLimit.setInt(r);
	}

	public static int getFactorySunRouterOverloadLimit() {
		return cacheSLROverloadLimit.getIntFactoryValue();
	}

	private static Pref cacheSLRPinFactor = Pref.makeIntPref("SunRouterPinFactor",
			Routing.getRoutingTool().prefs, 10);

	public static int getSunRouterPinFactor() {
		return cacheSLRPinFactor.getInt();
	}

	public static void setSunRouterPinFactor(int r) {
		cacheSLRPinFactor.setInt(r);
	}

	public static int getFactorySunRouterPinFactor() {
		return cacheSLRPinFactor.getIntFactoryValue();
	}

	private static Pref cacheSLRUPinDensityF = Pref.makeDoublePref("SunRouterUPinDensityF", Routing
			.getRoutingTool().prefs, 100);

	public static double getSunRouterUPinDensityF() {
		return cacheSLRUPinDensityF.getDouble();
	}

	public static void setSunRouterUPinDensityF(double r) {
		cacheSLRUPinDensityF.setDouble(r);
	}

	public static double getFactorySunRouterUPinDensityF() {
		return cacheSLRUPinDensityF.getDoubleFactoryValue();
	}

	private static Pref cacheSLRWindow = Pref.makeIntPref("SunRouterWindow", Routing.getRoutingTool().prefs,
			30);

	public static int getSunRouterWindow() {
		return cacheSLRWindow.getInt();
	}

	public static void setSunRouterWindow(int r) {
		cacheSLRWindow.setInt(r);
	}

	public static int getFactorySunRouterWindow() {
		return cacheSLRWindow.getIntFactoryValue();
	}

	private static Pref cacheWireOffset = Pref.makeIntPref("SunRouterWireOffset",
			Routing.getRoutingTool().prefs, 0);

	public static int getSunRouterWireOffset() {
		return cacheWireOffset.getInt();
	}

	public static void setSunRouterWireOffset(int r) {
		cacheWireOffset.setInt(r);
	}

	public static int getFactorySunRouterWireOffset() {
		return cacheWireOffset.getIntFactoryValue();
	}

	private static Pref cacheWireModulo = Pref.makeIntPref("SunRouterWireModulo",
			Routing.getRoutingTool().prefs, -1);

	public static int getSunRouterWireModulo() {
		return cacheWireModulo.getInt();
	}

	public static void setSunRouterWireModulo(int r) {
		cacheWireModulo.setInt(r);
	}

	public static int getFactorySunRouterWireModulo() {
		return cacheWireModulo.getIntFactoryValue();
	}

	private static Pref cacheWireBlockageFactor = Pref.makeDoublePref("SunRouterWireBlockageFactor", Routing
			.getRoutingTool().prefs, 0);

	public static double getSunRouterWireBlockageFactor() {
		return cacheWireBlockageFactor.getDouble();
	}

	public static void setSunRouterWireBlockageFactor(double r) {
		cacheWireBlockageFactor.setDouble(r);
	}

	public static double getFactorySunRouterWireBlockageFactor() {
		return cacheWireBlockageFactor.getDoubleFactoryValue();
	}

	private static Pref cacheRipUpMaximum = Pref.makeIntPref("SunRouterRipUpMaximum", Routing
			.getRoutingTool().prefs, 3);

	public static int getSunRouterRipUpMaximum() {
		return cacheRipUpMaximum.getInt();
	}

	public static void setSunRouterRipUpMaximum(int r) {
		cacheRipUpMaximum.setInt(r);
	}

	public static int getFactorySunRouterRipUpMaximum() {
		return cacheRipUpMaximum.getIntFactoryValue();
	}

	private static Pref cacheRipUpPenalty = Pref.makeIntPref("SunRouterRipUpPenalty", Routing
			.getRoutingTool().prefs, 10);

	public static int getSunRouterRipUpPenalty() {
		return cacheRipUpPenalty.getInt();
	}

	public static void setSunRouterRipUpPenalty(int r) {
		cacheRipUpPenalty.setInt(r);
	}

	public static int getFactorySunRouterRipUpPenalty() {
		return cacheRipUpPenalty.getIntFactoryValue();
	}

	private static Pref cacheRipUpExpansion = Pref.makeIntPref("SunRouterRipUpExpansion", Routing
			.getRoutingTool().prefs, 10);

	public static int getSunRouterRipUpExpansion() {
		return cacheRipUpExpansion.getInt();
	}

	public static void setSunRouterRipUpExpansion(int r) {
		cacheRipUpExpansion.setInt(r);
	}

	public static int getFactorySunRouterRipUpExpansion() {
		return cacheRipUpExpansion.getIntFactoryValue();
	}

	private static Pref cacheZRipUpExpansion = Pref.makeIntPref("SunRouterZRipUpExpansion", Routing
			.getRoutingTool().prefs, 2);

	public static int getSunRouterZRipUpExpansion() {
		return cacheZRipUpExpansion.getInt();
	}

	public static void setSunRouterZRipUpExpansion(int r) {
		cacheZRipUpExpansion.setInt(r);
	}

	public static int getFactorySunRouterZRipUpExpansion() {
		return cacheZRipUpExpansion.getIntFactoryValue();
	}

	private static Pref cacheRipUpSearches = Pref.makeIntPref("SunRouterRipUpSearches", Routing
			.getRoutingTool().prefs, 1);

	public static int getSunRouterRipUpSearches() {
		return cacheRipUpSearches.getInt();
	}

	public static void setSunRouterRipUpSearches(int r) {
		cacheRipUpSearches.setInt(r);
	}

	public static int getFactorySunRouterRipUpSearches() {
		return cacheRipUpSearches.getIntFactoryValue();
	}

	private static Pref cacheGlobalPathExpansion = Pref.makeIntPref("SunRouterGlobalPathExpansion", Routing
			.getRoutingTool().prefs, 5);

	public static int getSunRouterGlobalPathExpansion() {
		return cacheGlobalPathExpansion.getInt();
	}

	public static void setSunRouterGlobalPathExpansion(int r) {
		cacheGlobalPathExpansion.setInt(r);
	}

	public static int getFactorySunRouterGlobalPathExpansion() {
		return cacheGlobalPathExpansion.getIntFactoryValue();
	}

	private static Pref cacheSourceAccessExpansion = Pref.makeIntPref("SunRouterSourceAccessExpansion",
			Routing.getRoutingTool().prefs, 10);

	public static int getSunRouterSourceAccessExpansion() {
		return cacheSourceAccessExpansion.getInt();
	}

	public static void setSunRouterSourceAccessExpansion(int r) {
		cacheSourceAccessExpansion.setInt(r);
	}

	public static int getFactorySunRouterSourceAccessExpansion() {
		return cacheSourceAccessExpansion.getIntFactoryValue();
	}

	private static Pref cacheSinkAccessExpansion = Pref.makeIntPref("SunRouterSinkAccessExpansion", Routing
			.getRoutingTool().prefs, 10);

	public static int getSunRouterSinkAccessExpansion() {
		return cacheSinkAccessExpansion.getInt();
	}

	public static void setSunRouterSinkAccessExpansion(int r) {
		cacheSinkAccessExpansion.setInt(r);
	}

	public static int getFactorySunRouterSinkAccessExpansion() {
		return cacheSinkAccessExpansion.getIntFactoryValue();
	}

	private static Pref cacheDenseViaAreaSize = Pref.makeIntPref("SunRouterDenseViaAreaSize", Routing
			.getRoutingTool().prefs, 60);

	public static int getSunRouterDenseViaAreaSize() {
		return cacheDenseViaAreaSize.getInt();
	}

	public static void setSunRouterDenseViaAreaSize(int r) {
		cacheDenseViaAreaSize.setInt(r);
	}

	public static int getFactorySunRouterDenseViaAreaSize() {
		return cacheDenseViaAreaSize.getIntFactoryValue();
	}

	private static Pref cacheRetryExpandRouting = Pref.makeIntPref("SunRouterRetryExpandRouting", Routing
			.getRoutingTool().prefs, 50);

	public static int getSunRouterRetryExpandRouting() {
		return cacheRetryExpandRouting.getInt();
	}

	public static void setSunRouterRetryExpandRouting(int r) {
		cacheRetryExpandRouting.setInt(r);
	}

	public static int getFactorySunRouterRetryExpandRouting() {
		return cacheRetryExpandRouting.getIntFactoryValue();
	}

	private static Pref cacheRetryDenseViaAreaSize = Pref.makeIntPref("SunRouterRetryDenseViaAreaSize",
			Routing.getRoutingTool().prefs, 100);

	public static int getSunRouterRetryDenseViaAreaSize() {
		return cacheRetryDenseViaAreaSize.getInt();
	}

	public static void setSunRouterRetryDenseViaAreaSize(int r) {
		cacheRetryDenseViaAreaSize.setInt(r);
	}

	public static int getFactorySunRouterRetryDenseViaAreaSize() {
		return cacheRetryDenseViaAreaSize.getIntFactoryValue();
	}

	private static Pref cachePathSearchControl = Pref.makeIntPref("SunRouterPathSearchControl", Routing
			.getRoutingTool().prefs, 10000);

	public static int getSunRouterPathSearchControl() {
		return cachePathSearchControl.getInt();
	}

	public static void setSunRouterPathSearchControl(int r) {
		cachePathSearchControl.setInt(r);
	}

	public static int getFactorySunRouterPathSearchControl() {
		return cachePathSearchControl.getIntFactoryValue();
	}

	private static Pref cacheSparseViaModulo = Pref.makeIntPref("SunRouterSparseViaModulo", Routing
			.getRoutingTool().prefs, 31);

	public static int getSunRouterSparseViaModulo() {
		return cacheSparseViaModulo.getInt();
	}

	public static void setSunRouterSparseViaModulo(int r) {
		cacheSparseViaModulo.setInt(r);
	}

	public static int getFactorySunRouterSparseViaModulo() {
		return cacheSparseViaModulo.getIntFactoryValue();
	}

	private static Pref cacheLowPathSearchCost = Pref.makeIntPref("SunRouterLowPathSearchCost", Routing
			.getRoutingTool().prefs, 5);

	public static int getSunRouterLowPathSearchCost() {
		return cacheLowPathSearchCost.getInt();
	}

	public static void setSunRouterLowPathSearchCost(int r) {
		cacheLowPathSearchCost.setInt(r);
	}

	public static int getFactorySunRouterLowPathSearchCost() {
		return cacheLowPathSearchCost.getIntFactoryValue();
	}

	private static Pref cacheMediumPathSearchCost = Pref.makeIntPref("SunRouterMediumPathSearchCost", Routing
			.getRoutingTool().prefs, 20);

	public static int getSunRouterMediumPathSearchCost() {
		return cacheMediumPathSearchCost.getInt();
	}

	public static void setSunRouterMediumPathSearchCost(int r) {
		cacheMediumPathSearchCost.setInt(r);
	}

	public static int getFactorySunRouterMediumPathSearchCost() {
		return cacheMediumPathSearchCost.getIntFactoryValue();
	}

	private static Pref cacheHighPathSearchCost = Pref.makeIntPref("SunRouterHighPathSearchCost", Routing
			.getRoutingTool().prefs, 100);

	public static int getSunRouterHighPathSearchCost() {
		return cacheHighPathSearchCost.getInt();
	}

	public static void setSunRouterHighPathSearchCost(int r) {
		cacheHighPathSearchCost.setInt(r);
	}

	public static int getFactorySunRouterHighPathSearchCost() {
		return cacheHighPathSearchCost.getIntFactoryValue();
	}

	private static Pref cacheTakenPathSearchCost = Pref.makeIntPref("SunRouterTakenPathSearchCost", Routing
			.getRoutingTool().prefs, 10000);

	public static int getSunRouterTakenPathSearchCost() {
		return cacheTakenPathSearchCost.getInt();
	}

	public static void setSunRouterTakenPathSearchCost(int r) {
		cacheTakenPathSearchCost.setInt(r);
	}

	public static int getFactorySunRouterTakenPathSearchCost() {
		return cacheTakenPathSearchCost.getIntFactoryValue();
	}
}
