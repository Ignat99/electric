/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: SeaOfGatesEngine.java
 * Routing tool: Sea of Gates routing
 * Written by: Steven M. Rubin
 *
 * Copyright (c) 2007, Static Free Software. All rights reserved.
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
package com.sun.electric.tool.routing.seaOfGates;

import com.sun.electric.database.EditingPreferences;
import com.sun.electric.database.Environment;
import com.sun.electric.database.ImmutableNodeInst;
import com.sun.electric.database.geometry.EPoint;
import com.sun.electric.database.geometry.ERectangle;
import com.sun.electric.database.geometry.Poly;
import com.sun.electric.database.geometry.PolyBase;
import com.sun.electric.database.geometry.PolyBase.Point;
import com.sun.electric.database.geometry.PolyMerge;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.Export;
import com.sun.electric.database.id.ArcProtoId;
import com.sun.electric.database.id.CellId;
import com.sun.electric.database.id.NodeProtoId;
import com.sun.electric.database.id.PortProtoId;
import com.sun.electric.database.network.Netlist;
import com.sun.electric.database.network.Network;
import com.sun.electric.database.prototype.NodeProto;
import com.sun.electric.database.prototype.PortProto;
import com.sun.electric.database.text.Name;
import com.sun.electric.database.text.TextUtils;
import com.sun.electric.database.topology.ArcInst;
import com.sun.electric.database.topology.Connection;
import com.sun.electric.database.topology.Geometric;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.topology.PortInst;
import com.sun.electric.database.topology.RTBounds;
import com.sun.electric.database.topology.RTNode;
import com.sun.electric.database.topology.SteinerTree;
import com.sun.electric.database.topology.SteinerTree.SteinerTreePort;
import com.sun.electric.database.topology.SteinerTree.SteinerTreePortPair;
import com.sun.electric.database.variable.Variable;
import com.sun.electric.technology.ArcProto;
import com.sun.electric.technology.DRCTemplate;
import com.sun.electric.technology.Layer;
import com.sun.electric.technology.PrimitiveNode;
import com.sun.electric.technology.SizeOffset;
import com.sun.electric.technology.Technology;
import com.sun.electric.technology.Technology.NodeLayer;
import com.sun.electric.technology.technologies.Generic;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.drc.DRC;
import com.sun.electric.tool.routing.Routing.SoGNetOrder;
import com.sun.electric.tool.routing.SeaOfGates;
import com.sun.electric.tool.routing.SeaOfGates.SeaOfGatesArcProperties;
import com.sun.electric.tool.routing.SeaOfGates.SeaOfGatesCellParameters;
import com.sun.electric.tool.routing.SeaOfGates.SeaOfGatesExtraBlockage;
import com.sun.electric.tool.routing.SeaOfGates.SeaOfGatesTrack;
import com.sun.electric.tool.user.ErrorLogger;
import com.sun.electric.tool.user.ErrorLogger.MessageLog;
import com.sun.electric.tool.user.ui.RoutingDebug;
import com.sun.electric.util.ElapseTimer;
import com.sun.electric.util.math.DBMath;
import com.sun.electric.util.math.FixpCoord;
import com.sun.electric.util.math.FixpRectangle;
import com.sun.electric.util.math.FixpTransform;
import com.sun.electric.util.math.GenMath;
import com.sun.electric.util.math.MutableBoolean;
import com.sun.electric.util.math.MutableDouble;
import com.sun.electric.util.math.MutableInteger;
import com.sun.electric.util.math.Orientation;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class to do sea-of-gates routing. This router replaces unrouted arcs with real geometry.
 * It has these features:
 * <ul>
 * <li> The router only works in layout, and only routes metal wires.
 * <li> The router uses vias to move up and down the metal layers.
 * <li> Understands multiple vias and multiple via orientations.
 * <li> The router is not tracked: it runs gridless on the Electric database
 * <li> Favors wires on full-grid units
 * <li> Tries to cover multiple grid units in a single jump
 * <li> Routes power and ground first, then goes by length (shortest nets first)
 * <li> Uses Steiner Trees to reorganize a network into short segments
 * <li> Can detect "spine" situations and route specially
 * <li> Global router assigns specific paths to each route
 * <li> Can prefer to run odd metal layers in one axis (default horizontal), even layers on the other axis (default vertical)
 * <li> Routes in both directions (alternating steps from A to B and from B to A) and stops when one direction completes
 * <li> Parallel option runs both wavefronts at once and aborts the slower one
 * <li> Users can request that some layers not be used, can request that some layers be favored
 * <li> Routes are made as wide as the widest arc already connected to any point
 * <li> User preference can limit width
 * <li> Cost penalty also includes space left in the track on either side of a segment
 * <li> Is able to connect to anything on the destination network, not just the destination port
 * </ul>
 *
 * Things to do:
 *  Fix ability to route to any point on destination (ANYPOINTONDESTINATION=true)
 *  Improve Global routing
 *  Weight layer changes higher if far from end layer and going away
 *  Detect "river routes" and route specially
 *  Rip-up
 *  Sweep cost parameters (with parallel processors) to dynamically find best settings
 */
public abstract class SeaOfGatesEngine
{
	// code switches
	/** true to allow multiple destinations. */					private static final boolean MULTIPLEDESTINATIONS = true;
	/** true to run both directions and choose best */			private static final boolean CHECKBOTHDIRECTIONS = false;
	/** true to ignore grid errors at start of route */			private static final boolean NOGRIDPENALTYATSOURCE = true;
	/** true to route to any point on the destination */		private static final boolean ANYPOINTONDESTINATION = true;

	// tunable parameters
	/** Granularity of coordinates. */							private static final double GRAINSIZE = 1;
	/** the height/width ratio that defines a spine */			private static final double SPINERATIO = 50;
	/** Cost: of forcing horizontal/vertical metal layers */	private static final int COSTALTERNATINGMETAL = 20;
	/** Cost of changing layers. */								private static final int COSTLAYERCHANGE = 8;
	/** Cost of routing away from the target. */				private static final int COSTWRONGDIRECTION = 15;
	/** Cost of running on non-favored layer. */				private static final int COSTUNFAVORED = 10;
	/** Cost of making a turn. */								private static final int COSTTURNING = 1;
	/** Cost of having coordinates that are off-grid. */		private static final int COSTOFFGRID = 15;

	// blockage factors
	/** Bit set in network ID for fake endpoint blockage. */	private static final int BLOCKAGEFAKEENDPOINT = 1;
	/** Bit set in network ID for blockage on end A. */			private static final int BLOCKAGEENDA = 2;
	/** Bit set in network ID for blockage on end B. */			private static final int BLOCKAGEENDB = 4;
	/** Bit set in network ID for user-supplied blockage. */	private static final int BLOCKAGEFAKEUSERSUPPLIED = 8;
	/** Number of bits to shift to skip blockage bits */		private static final int SHIFTBLOCKBITS = 4;

	public static SearchVertex svAborted = new SearchVertex(0, 0, 0, 0, 0, null, null, 0, null, 0, null);
	public static SearchVertex svExhausted = new SearchVertex(0, 0, 0, 0, 0, null, null, 0, null, 0, null);
	public static SearchVertex svLimited = new SearchVertex(0, 0, 0, 0, 0, null, null, 0, null, 0, null);
	public static SearchVertex svAbandoned = new SearchVertex(0, 0, 0, 0, 0, null, null, 0, null, 0, null);

	/** number of metal layers in the technology. */			private static int numMetalLayers;

	/** Environment */											private Environment env;
	/** Cell in which routing occurs. */						private Cell cell;
	/** true to run to/from and from/to routing in parallel */	private boolean parallelDij;
	/** for logging errors */									private ErrorLogger errorLogger;
	/** Cell size. */											private Rectangle2D cellBounds;
	/** Cell-wide routing limit */								private ERectangle routingBoundsLimit;
	/** Technology to use for routing. */						private Technology tech;
	/** metal layers in the technology. */						private Layer[][] metalLayers;
	/** single layer to use for each metal level. */			private Layer[] primaryMetalLayer;
	/** via layers in the technology. */						private Layer[] viaLayers;
	/** layer that removes metal in the technology. */			private Map<Layer,Layer> removeLayers;
	/** arcs to use for each metal layer. */					private ArcProto[][] metalArcs;
	/** pure-layer nodes to use for each metal layer. */		private PrimitiveNode[][] metalPureLayerNodes;
	/** single arc to use for each metal layer. */				private ArcProto[] primaryMetalArc;
	/** maximum default arc width. */							private double[] maxDefArcWidth;
	/** minimum area for a layer. */							private double[] minimumArea;
	/** minimum resolution for technology. */					private double minResolution;
	/** size of 2X arcs. */										private double[] size2X;
	/** maximum length of tapers. */							private double[] taperLength;
	/** favoritism for each metal layer. */						private boolean[] favorArcs;
	/** avoidance for each metal layer. */						private boolean[] preventArcs;
	/** arcs that can only be tapers. */						private boolean[] taperOnlyArcs;
	/** vias to use to go up from each metal layer. */			private MetalVias[] metalVias, metalVias2X;
	/** metal gridding for the cell. */							private SeaOfGatesTrack[][] metalGrid;
	/** metal gridding range for the cell. */					private double[] metalGridRange;
	/** spacing rules for a given metal layer. */				private double[] metalSurroundX, metalSurroundY;
	/** minimum spacing between the corners of two vias. */		private double[] viaSurround;
	/** spacing between 3 or 4 diagonal vias. */				private double[] viaDiagonalDistance;
	/** spacing vias with different mask colors. */				private double[] viaColorDiffSpacing;
	/** method for secret via rules. */							private Method[] secretViaSpacingRules;
	/** minimum size of two vias. */							private double[] viaSize;
	/** R-Trees for routing blockages */						private BlockageTrees rTrees;
	/** converts Networks to unique integers */					private Map<Network, Integer> netIDs;
	/** preferences */											private SeaOfGates.SeaOfGatesOptions prefs;
	/** interaction with outer environment */					private Handler handler;
	/** EditingPreferences */									private EditingPreferences ep;
	/** cell-specific parameters */								private SeaOfGatesCellParameters sogp;
	/** taps that need to be added to spine routes */			private List<NeededRoute> tapRoutes;
	/** true to keep messages quiet */							private boolean messagesQuiet;
	/** total number of blockages to node-extract */			private int totalBlockages;
	/** number of blockages node-extracted so far */			private int blockagesFound;
	/** minimum spacing between this metal and itself. */		private Map<Double, Map<Double, double[]>>[] layerSurround;
	/** routing quality */										private SoGWireQualityMetric sogQual;

	/************************************** CONTROL **************************************/

	/**
	 * This is the public interface for Sea-of-Gates Routing when done in batch mode.
	 * @param handler interaction with outer environment
	 * @param cell the cell to be Sea-of-Gates-routed.
	 */
	public void routeIt(Handler handler, Cell cell, boolean quiet) {
		routeIt(handler, cell, quiet, null);
	}

	/**
	 * This is the public interface for Sea-of-Gates Routing when done in batch mode.
	 * @param handler interaction with outer environment
	 * @param cell the cell to be Sea-of-Gates-routed.
	 * @param arcsToRoute a List of ArcInsts on networks to be routed.
	 */
	public void routeIt(Handler handler, Cell cell, boolean quiet, List<ArcInst> arcsToRoute)
	{
		routeIt(handler, cell, quiet, arcsToRoute, new SeaOfGatesCellParameters(cell));
	}

	/**
	 * This is the public interface for Sea-of-Gates Routing when done in batch mode.
	 * @param handler interaction with outer environment
	 * @param cell the cell to be Sea-of-Gates-routed.
	 * @param arcsToRoute a List of ArcInsts on networks to be routed.
	 * @param sogp parameters to use
	 */
	public void routeIt(Handler handler, Cell cell, boolean quiet, List<ArcInst> arcsToRoute, SeaOfGatesCellParameters sogp)
	{
		// initialize routing
		messagesQuiet = quiet;
		this.handler = handler;
		ep = handler.getEditingPreferences();
		env = cell.getDatabase().getEnvironment();
		this.sogp = sogp;
		tapRoutes = new ArrayList<NeededRoute>();

		this.cell = cell;
		cellBounds = cell.getBounds();
		tech = cell.getTechnology();

		// printing preferences for debugging purposes
		if (Job.getDebug())
			System.out.println("Preferences " + prefs);

		// find the routing bounds limit
		routingBoundsLimit = null;
		String routingBoundsLayerName = sogp.getRoutingBoundsLayerName();
		if (routingBoundsLayerName != null)
		{
			Layer boundsLayer = tech.findLayer(routingBoundsLayerName);
			if (boundsLayer == null)
			{
				if (!RoutingDebug.isActive())
					System.out.println("WARNING: Routing bounds layer '" + routingBoundsLayerName + "' not found in technology " + tech.getTechName());
			} else
			{
				List<NodeInst> boundsLayerNodes = new ArrayList<NodeInst>();
				for(Iterator<NodeInst> it = cell.getNodes(); it.hasNext(); )
				{
					NodeInst ni = it.next();
					if (ni.isCellInstance()) continue;
					if (ni.getFunction() != PrimitiveNode.Function.NODE) continue;
					PrimitiveNode pNp = (PrimitiveNode)ni.getProto();
					NodeLayer[] nodeLayers = pNp.getNodeLayers();
					for(int i=0; i<nodeLayers.length; i++)
					{
						if (nodeLayers[i].getLayer() == boundsLayer)
						{
							boundsLayerNodes.add(ni);
							break;
						}
					}
				}
				if (boundsLayerNodes.size() == 0)
				{
					if (!RoutingDebug.isActive())
						System.out.println("WARNING: No nodes found with the routing bounds layer '" + routingBoundsLayerName + "'");
				} else if (boundsLayerNodes.size() > 1)
				{
					if (!RoutingDebug.isActive())
						System.out.println("WARNING: Found " + boundsLayerNodes.size() + " nodes with the routing bounds layer, must have only 1.");
				} else
				{
					NodeInst ni = boundsLayerNodes.get(0);
					routingBoundsLimit = ni.getBounds();
					if (!RoutingDebug.isActive())
						System.out.println("NOTE: No routes will extend beyond " + TextUtils.formatDistance(routingBoundsLimit.getMinX()) +
							"<=X<=" + TextUtils.formatDistance(routingBoundsLimit.getMaxX()) + " AND " +
							TextUtils.formatDistance(routingBoundsLimit.getMinY()) + "<=Y<=" +
							TextUtils.formatDistance(routingBoundsLimit.getMaxY()));
				}
			}
		}

		if (initializeDesignRules()) return;
		initializeGrids();
		netIDs = new HashMap<Network, Integer>();
		errorLogger = ErrorLogger.newInstance("Routing (Sea of gates) " + cell.describe(false));
		prefs.theTimer = ElapseTimer.createInstance().start();
		Netlist netList = cell.getNetlist();

		// get arcs to route
		List<String> netsToRoute = sogp.getNetsToRoute();

		// Sort names based arc distances
		if (prefs.netOrder == SoGNetOrder.SOGNETORDERDESCENDING || prefs.netOrder == SoGNetOrder.SOGNETORDERASCENDING)
			Collections.sort(netsToRoute, new SortArcByDistance(cell, prefs.netOrder));
		else if (netsToRoute != null && prefs.netOrder == SoGNetOrder.SOGNETORDERBUS)
		{
			Map<String,BusBitsNumberClass> busMap = new HashMap<String,BusBitsNumberClass>(); // counting number of bits per bus
			List<String> simpleNets = new ArrayList<String>();

			for (String s : netsToRoute)
			{
				int index = s.indexOf("[");
				if (index != -1)
				{
					String bus = s.substring(0, index);
					BusBitsNumberClass list = busMap.get(bus);
					if (list == null)
					{
						list = new BusBitsNumberClass(bus);
						busMap.put(bus, list);
					}
					list.bits.add(s.substring(index));
				}
				else
					simpleNets.add(s);
			}

			// sort busses based on number of bits
			List<BusBitsNumberClass> listToSort = new ArrayList<BusBitsNumberClass>(busMap.values());
			Collections.sort(listToSort, new SortByBusBitsNumberClass());
			netsToRoute.clear();
			// adding the multiple connection nets first
			for (BusBitsNumberClass bus: listToSort)
				netsToRoute.addAll(bus.getNets());
			netsToRoute.addAll(simpleNets);
		}

		if (netsToRoute != null && netsToRoute.size() > 0)
		{
			// overriding nets are listed in the Sea-of-Gates Cell Properties, see if the user selected an overriding subset
			boolean allSelected = true;
			if (arcsToRoute != null)
			{
				for (Iterator<ArcInst> it = cell.getArcs(); it.hasNext(); )
				{
					ArcInst ai = it.next();
					if (ai.getProto() != Generic.tech().unrouted_arc) continue;
					if (!arcsToRoute.contains(ai)) { allSelected = false;  break; }
				}
			}
			if (allSelected)
			{
				arcsToRoute = new ArrayList<ArcInst>();
				for(String netName : netsToRoute)
				{
					Network net = null;
					ArcInst ai = cell.findArc(netName);
					if (ai != null) net = netList.getNetwork(ai, 0); else
					{
						Netlist nl = cell.getNetlist();
						for(Iterator<Network> it = nl.getNetworks(); it.hasNext(); )
						{
							Network n = it.next();
							for(Iterator<String> nIt = n.getNames(); nIt.hasNext(); )
							{
								String nn = nIt.next();
								if (nn.equals(netName))
								{
									net = n;
									break;
								}
							}
							if (net != null) break;
						}
					}
					if (net == null)
					{
						System.out.println("WARNING: Could not find network '" + netName + "' which was requested by the Sea-of-Gates Cell Properties");
						continue;
					}
					for(Iterator<ArcInst> it = net.getArcs(); it.hasNext() ;)
					{
						ai = it.next();
						if (ai.getProto() != Generic.tech().unrouted_arc) continue;
						arcsToRoute.add(ai);
					}
				}
				if (!RoutingDebug.isActive())
					System.out.println("Routing " + arcsToRoute.size() + " arcs from the list given in the Sea-of-Gates Cell Properties");
			}
		}
		if (arcsToRoute == null)
		{
			arcsToRoute = new ArrayList<ArcInst>();
			for (Iterator<ArcInst> it = cell.getArcs(); it.hasNext(); )
			{
				ArcInst ai = it.next();
				if (ai.getProto() != Generic.tech().unrouted_arc) continue;
				arcsToRoute.add(ai);
			}
		}
		if (arcsToRoute.isEmpty()) return;

		// organize routes by networks and build routing data structures
		setProgressNote("Make list of routes...");
		if (!RoutingDebug.isActive())
			handler.startProgressDialog("Routing " + arcsToRoute.size() + " nets in cell " + cell.describe(false));
		List<EPoint> linesInNonMahnattan = new ArrayList<EPoint>();
		RouteBatch[] routeBatches = makeListOfRoutes(netList, arcsToRoute, linesInNonMahnattan);
		MutableBoolean hadNonmanhattan = new MutableBoolean(linesInNonMahnattan.size() > 0);

		if (routeBatches.length == 0) return;
		if (RoutingDebug.isRewireNetworks())
		{
			rewireNetworks(routeBatches);
			return;
		}
		List<NeededRoute> allRoutes = new ArrayList<NeededRoute>();
		for(int b=0; b<routeBatches.length; b++)
			for(NeededRoute nr : routeBatches[b].routesInBatch) allRoutes.add(nr);
		info("");
		info("Sea-of-gates router finding " + allRoutes.size() + " paths on " + routeBatches.length + " networks in cell " + cell.describe(false));
		if (hadNonmanhattan.booleanValue())
		{
			String info = "Found nonmanhattan geometry (" + linesInNonMahnattan.size() +
				" points). This may cause larger rectangular blockages, which may block too much.";
			if (linesInNonMahnattan.size() > 100)
			{
				linesInNonMahnattan = linesInNonMahnattan.subList(0, 100); // just show first 100
				info += ". Displaying only the first 100";
			}
			warn(info, cell, linesInNonMahnattan, null);
		}

		// do "global routing" preprocessing
		if (prefs.useGlobalRouter || RoutingDebug.isTestGlobalRouting())
		{
			setProgressNote("Do Global Routing...");
			info("Doing Global Routing...");

			// in debug mode, construct fake routes for everything in the cell
			RouteBatch[] fakeRBs = null;
			if (RoutingDebug.isActive())
			{
				Map<Network,RouteBatch> additionalRBs = new HashMap<Network,RouteBatch>();
				Set<ArcInst> arcsInCell = new HashSet<ArcInst>();
				for(ArcInst ai : arcsToRoute) arcsInCell.add(ai);
				for (Iterator<ArcInst> it = cell.getArcs(); it.hasNext(); )
				{
					ArcInst ai = it.next();
					if (ai.getProto() != Generic.tech().unrouted_arc) continue;
					if (arcsInCell.contains(ai)) continue;
					Network net = netList.getNetwork(ai, 0);
					RouteBatch rb = additionalRBs.get(net);
					if (rb == null) additionalRBs.put(net, rb = new RouteBatch(net.getName()));

					// get Arc information about the ends of the path
					PortInst aPi = ai.getHeadPortInst();
					PortInst bPi = ai.getTailPortInst();
					ArcProto aArc = getMetalArcOnPort(aPi);
					if (aArc == null) continue;
					ArcProto bArc = getMetalArcOnPort(bPi);
					if (bArc == null) continue;

					// create the fake NeededRoute
					NeededRoute nr = new NeededRoute(net.getName(), aPi, bPi, aArc, bArc, null, 0);
					rb.addRoute(nr);
				}
				fakeRBs = new RouteBatch[additionalRBs.size()];
				int i = 0;
				for(Network net : additionalRBs.keySet()) fakeRBs[i++] = additionalRBs.get(net);
			}

			// do the global routing
			double wirePitch = Math.max(metalSurroundX[0], metalSurroundY[0]) + maxDefArcWidth[0];
			GlobalRouter gr = doGlobalRouting(cell, routeBatches, fakeRBs, wirePitch);

			// reorder so that paths without Global Routing (small ones) come first
			List<NeededRoute> withGR = new ArrayList<NeededRoute>();
			List<NeededRoute> withoutGR = new ArrayList<NeededRoute>();
			for(NeededRoute nr : allRoutes)
			{
				if (nr.buckets != null) withGR.add(nr); else withoutGR.add(nr);
			}
			info("Global Routing planned " + withGR.size() + " paths in " + gr.getXBuckets() + "x" + gr.getYBuckets() +
				" buckets (" + withoutGR.size() + " paths are too short to route globally)");

			// reorder the routes so that globally-routed nets come first or last
//			allRoutes.clear();
//			for(NeededRoute nr : withGR) allRoutes.add(nr);
//			for(NeededRoute nr : withoutGR) allRoutes.add(nr);

			if (RoutingDebug.isActive()) RoutingDebug.setGlobalRouting(gr);
			if (RoutingDebug.isTestGlobalRouting())
			{
				RoutingDebug.showGlobalRouting();
				return;
			}
			setProgressNote("Detail Route " + allRoutes.size() + " paths...");
			info("Detail Routing " + allRoutes.size() + " paths...");
		} else
		{
			setProgressNote("Route " + allRoutes.size() + " paths...");
			info("Routing " + allRoutes.size() + " paths...");
		}

		// warn if an endpoint is off-grid and gridding is forced
		for(NeededRoute nr : allRoutes)
			nr.checkGridValidity();

		// if debugging, mark route to debug
		if (RoutingDebug.isActive() && allRoutes.size() > 0)
		{
			String whichRoute = RoutingDebug.getDesiredRouteToDebug();
			if (whichRoute != null)
			{
				NeededRoute nr = allRoutes.get(0);
				for(NeededRoute nrTest : allRoutes)
				{
					if (nrTest.routeName.equalsIgnoreCase(whichRoute))
					{
						nr = nrTest;
						break;
					}
				}
				nr.setDebugging(Boolean.valueOf(RoutingDebug.isEndADebugging()));
			}
		}

		// determine the kind of parallelism to use
		boolean parallel = prefs.useParallelRoutes;
		parallelDij = prefs.useParallelFromToRoutes;
		int numberOfProcessors = Runtime.getRuntime().availableProcessors();
		if (numberOfProcessors <= 1) parallelDij = false;

		// determine the number of parallel threads to use
		int numberOfThreads = numberOfProcessors;
		if (prefs.forcedNumberOfThreads > 0)
		{
			// user's input overrides thread-count computation
			info("Forcing use of " + prefs.forcedNumberOfThreads + " threads");
			numberOfThreads = prefs.forcedNumberOfThreads;
		}
		if (!parallel) numberOfThreads = 1;
		if (numberOfThreads == 1) {
			parallel = false;
			parallelDij = false;
		}
		if (parallel)
		{
			String message = "NOTE: System has " + numberOfProcessors + " processors so";
			if (parallelDij)
			{
				message += " routing " + (numberOfThreads/2) + " paths in parallel";
				message += " and routing both directions of each path in parallel";
			} else {
				message += " routing " + numberOfThreads + " paths in parallel";
			}
			info(message);
		}

		// do the routing
		if (numberOfThreads > 1) doRoutingParallel(numberOfThreads, allRoutes); else
			doRouting(allRoutes);
		if (!RoutingDebug.isActive())
		{
			handler.flush(true);

			if (tapRoutes.size() > 0)
			{
				setProgressNote("Adding taps to spine routes...");
				info("------------------ Adding taps to spine routes...");

				// do the routing again
				if (numberOfThreads > 1) doRoutingParallel(numberOfThreads, tapRoutes); else
					doRouting(tapRoutes);
				handler.flush(true);
			}

			// see if any routes failed and need to be redone
			List<NeededRoute> redoRoutes = new ArrayList<NeededRoute>();
			for (int b = 0; b < routeBatches.length; b++)
			{
				for(NeededRoute nr : routeBatches[b].routesInBatch)
				{
					if (nr.routedSuccess) continue;
					redoRoutes.add(nr);
					nr.buckets = null;
					nr.errorMessage = null;
					nr.complexityLimit = prefs.rerunComplexityLimit;
					nr.makeWavefronts();
				}
			}

			// redo the routing on the failed routes, ignoring global routing information
			if (prefs.reRunFailedRoutes && redoRoutes.size() > 0)
			{
				setProgressNote("Re-Route " + redoRoutes.size() + " paths...");
				info("------------------ Re-Route " + redoRoutes.size() + " paths...");

				// do the routing again
				for(NeededRoute nr : redoRoutes)
				{
					nr.reroute = true;

					// if an error was already logged for this route, remove it
					if (nr.loggedMessage != null)
					{
						List<MessageLog> oldMessage = new ArrayList<MessageLog>();
						oldMessage.add(nr.loggedMessage);
						errorLogger.deleteMessages(oldMessage);
					}
				}

				if (numberOfThreads > 1) doRoutingParallel(numberOfThreads, redoRoutes); else
					doRouting(redoRoutes);
				handler.flush(true);
			}

			// make new unrouted arcs for all failed routes
			RouteResolution resolution = new RouteResolution(cell.getId());
			for (int b = 0; b < routeBatches.length; b++)
			{
				for(NeededRoute nr : routeBatches[b].routesInBatch)
				{
					if (nr.routedSuccess) continue;
					resolution.addUnrouted(nr.aPi, nr.bPi, nr.getName());
				}
			}
			handler.instantiate(resolution);
			handler.flush(true);

			// show statistics on the routing
			summarize(routeBatches, allRoutes);
			// dumpSpacing();
		}

		// clean up
		handler.termLogging(errorLogger);
		handler.stopProgressDialog();
	}

	/**
	 * Method to return an R-Tree of blockages on a given metal Layer.
	 * @param lay the metal Layer to examine.
	 * @return an RTNode that is the top of the tree of blockages on that Layer.
	 */
	public RTNode<SOGBound> getMetalTree(Layer lay) { return rTrees.getMetalTree(lay).getRoot(); }

	public Iterator<SOGBound> searchMetalTree(Layer lay, Rectangle2D bound)
	{
		return rTrees.getMetalTree(lay).search(bound);
	}

	/**
	 * Method to rip-out all unrouted wires and replace them with efficiently-organized unrouted wires
	 * that follow the minimum-distance Steiner tree.
	 * @param routeBatches the batches of wires to rewire.
	 */
	private void rewireNetworks(RouteBatch[] routeBatches)
	{
		for (int b = 0; b < routeBatches.length; b++)
		{
			RouteBatch rb = routeBatches[b];
			for (ArcInst ai : rb.unroutedArcs) ai.kill();
			for(NeededRoute nr : rb.routesInBatch)
			{
				EPoint aPt = nr.aPi.getCenter();
				EPoint bPt = nr.bPi.getCenter();
				ArcInst.makeInstance(Generic.tech().unrouted_arc, ep, nr.aPi, nr.bPi, aPt, bPt, nr.getName());
			}
		}
		sogp.setSteinerDone(true);
		sogp.saveParameters(ep);
	}

	/**
	 * Method to describe the results of routing
	 */
	private void summarize(RouteBatch[] routeBatches, List<NeededRoute> allRoutes)
	{
		// calculate metrics only if routing results are not stored in special cell
		// otherwise the result cell doesn't have any connectivity
		sogQual = new SoGWireQualityMetric("SoG");
		sogQual.setOutput(prefs.qualityPrintStream);
		for (int b = 0; b < routeBatches.length; b++)
		{
			sogQual.calculate(routeBatches[b]);
		}
		prefs.theTimer.end();
		info("Cell " + cell.describe(false) + " routed " + sogQual.numRoutedSegments + " out of " + allRoutes.size() +
			" segments" +
			" (took " + prefs.theTimer + ")");
		if (sogQual.numFailedSegments > 0) info("NOTE: " + sogQual.numFailedSegments + " segments on " + sogQual.numFailedBatches + " nets were not routed");

		info(sogQual.printAverageResults());
	}

	public String getRoutedNetRatio()
	{
		String value = "";
		if (sogQual != null)
		{
			int total = sogQual.numFailedSegments + sogQual.numRoutedSegments;
			value = "" + sogQual.numRoutedSegments + "/" + total;
		}
		return value;
	}

	/**
	 * Method to set the preferences in this SeaOfGatesEngine.
	 * @param p Preferences to use for routing.
	 */
	public void setPrefs(SeaOfGates.SeaOfGatesOptions p) { prefs = p; }

	/**
	 * Method to return the preferences in this SeaOfGatesEngine.
	 * @return the preferences to use for routing.
	 */
	public SeaOfGates.SeaOfGatesOptions getPrefs() { return prefs; }

	/**
	 * Method to return the technology considered in routing.
	 * @return the technology considered in routing.
	 */
	public Technology getTech() { return tech; }

	/**
	 * Method to return the number of metal layers being considered in routing.
	 * @return the number of metal layers being considered in routing.
	 */
	public int getNumMetals() { return numMetalLayers; }

	/**
	 * Method to return the primary metal Layer associated with a layer number.
	 * @param layNum a layer number, from 0 to getNumMetals()-1.
	 * @return the metal Layer being used on the layer number.
	 */
	public Layer getPrimaryMetalLayer(int layNum) { return primaryMetalLayer[layNum]; }

	private boolean isOnMetalArc(int layNum, ArcProto ap)
	{
		for(int c=0; c<metalArcs[layNum].length; c++)
			if (metalArcs[layNum][c] == ap) return true;
		return false;
	}

	/**
	 * Method to return the via Layer associated with a layer number.
	 * @param layNum a layer number, from 0 to getNumMetals()-2.
	 * @return the via Layer being used on the layer number.
	 */
	public Layer getViaLayer(int layNum) { return viaLayers[layNum]; }

	/**
	 * Method to describe this NodeInst as a string.
	 * @param ni NodeInst to describe
	 * @return a description of this NodeInst as a string.
	 */
	protected String describe(NodeInst ni) {
		NodeProto np = ni.getProto();
		boolean libDescribe;
		if (np instanceof Cell) {
			libDescribe = ((Cell)np).getLibrary() != cell.getLibrary();
		} else {
			libDescribe = ((PrimitiveNode)np).getTechnology() != tech;
		}
		return libDescribe ? ni.libDescribe() : ni.noLibDescribe();
	}

	/**
	 * Method to describe this ArcInst as a string.
	 * @param ai ArcInst to describe
	 * @return a description of this ArcInst as a string.
	 */
	protected String describe(ArcInst ai) {
		ArcProto ap = ai.getProto();
		boolean libDescribe = ap.getTechnology() != tech;
		return libDescribe ? ai.libDescribe() : ai.noLibDescribe();
	}

	/**
	 * Method to describe this NodeProto as a string.
	 * @param np NodeProto to describe
	 * @return a description of this NodeProto as a string.
	 */
	protected String describe(NodeProto np) {
		boolean libDescribe;
		if (np instanceof Cell) {
			libDescribe = ((Cell)np).getLibrary() != cell.getLibrary();
		} else {
			libDescribe = ((PrimitiveNode)np).getTechnology() != tech;
		}
		return libDescribe ? np.libDescribe() : np.noLibDescribe();
	}

	/**
	 * Method to describe this ArcProto as a string.
	 * @param ap ArcProto to describe
	 * @return a description of this ArcProto as a string.
	 */
	protected String describe(ArcProto ap) {
		boolean libDescribe = ap.getTechnology() != tech;
		return libDescribe ? ap.getFullName() : ap.getName();
	}

	protected Environment getEnvironment() { return env; }

	/**
	 * Check if we are scheduled to abort. If so, print message if non null and
	 * return true.
	 * @return true on abort, false otherwise. If job is scheduled for abort or
	 *		 aborted. and it will report it to standard output
	 */
	protected boolean checkAbort() {
		return handler.checkAbort();
	}

	/**
	 * Log a message at the TRACE level.
	 *
	 * @param msg the message string to be logged
	 */
	protected void trace(String msg) {
		handler.trace(msg);
	}

	/**
	 * Log a message at the DEBUG level.
	 *
	 * @param msg the message string to be logged
	 */
	protected void debug(String msg) {
		handler.debug(msg);
	}

	/**
	 * Log a message at the INFO level.
	 *
	 * @param msg the message string to be logged
	 */
	protected void info(String msg) {
		handler.info(msg);
	}

	/**
	 * Log a message at the WARN level.
	 *
	 * @param msg the message string to be logged
	 */
	protected void warn(String msg) {
		handler.warn(msg);
	}

	/**
	 * Log a message at the WARN level including
	 * ErrorLogger
	 * @param msg the message string to be logged
	 * @param cell cell warning belongs to
	 * @param linesToShow list of points related to the warning
	 * @param polysToShow list of polygons related to the warning
	 */
	protected void warn(String msg, Cell cell, List<EPoint> linesToShow, List<PolyBase> polysToShow)
	{
		warn(msg);
		if (polysToShow != null)
		{
			errorLogger.logMessage(msg, null, polysToShow, cell, 0, false);
			return;
		}
		errorLogger.logMessageWithLines(msg, linesToShow, linesToShow, cell, 0, false);
	}

	/**
	 * Log a message at the ERROR level.
	 *
	 * @param msg the message string to be logged
	 */
	protected void error(String msg) {
		handler.error(msg);
	}

	 /**
	 * Method to set a text message in the progress dialog.
	 * @param message the new progress message.
	 */
	protected void setProgressNote(String message) {
		if (!RoutingDebug.isActive())
			handler.setProgressNote(message);
	}

	/**
	 * Method to update the progress bar
	 * @param done the amount done (from 0 to total-1).
	 * @param total the total amount to do.
	 */
	protected void setProgressValue(int done, int total) {
		if (!RoutingDebug.isActive())
			handler.setProgressValue(done, total);
	}

	/**
	 * flush changes
	 */
	void flush() {
		handler.flush(false);
	}

	/**
	 * Stub for parallel routing.
	 * @param numberOfThreads number of threads to create.
	 * @param allRoutes the routes that need to be done.
	 */
	protected abstract void doRoutingParallel(int numberOfThreads, List<NeededRoute> allRoutes);

	/**
	 * Method to do the routing in a single thread.
	 * @param allRoutes the routes that need to be done.
	 */
	private void doRouting(List<NeededRoute> allRoutes)
	{
		int totalRoutes = allRoutes.size();
		for (int r = 0; r < totalRoutes; r++)
		{
			if (checkAbort())
			{
				info("Sea-of-gates routing aborted");
				break;
			}

			// get information on the segment to be routed
			NeededRoute nr = allRoutes.get(r);
			setProgressValue(r, totalRoutes);
			String progMsg = "Routing network " + nr.routeName + "...";
			setProgressNote(progMsg);
			if (!messagesQuiet && !Job.getDebug()) trace(progMsg);

			if (nr.alreadyRouted)
			{
				// show the existing route
				List<PolyBase> polysInConnection = new ArrayList<PolyBase>();
				Point[] points = new Point[2];
				points[0] = PolyBase.fromLambda(nr.aEndpoints.getCenterX(), nr.aEndpoints.getCenterY());
				points[1] = PolyBase.fromLambda(nr.bEndpoints.getCenterX(), nr.bEndpoints.getCenterY());
				polysInConnection.add(new PolyBase(points));
				for(int i=0; i<numMetalLayers; i++)
				{
					RTNode<SOGBound> root = rTrees.metalTrees[i].getRoot();
					if (root != null) addNetsToList(root, nr.netID, polysInConnection);
				}

				if (prefs.runOnConnectedRoutes)
				{
					warn("Network " + nr.getName() + " is already routed in the circuit, running router on it anyway", cell, null, polysInConnection);
				} else
				{
					warn("Not routing network " + nr.getName() + " because it is already routed in the circuit", cell, null, polysInConnection);
					continue;
				}
			}

			// route the segment
			Runnable[] runnables = findPath(nr);
			if (runnables != null) {
				for (Runnable runnable: runnables) {
					runnable.run();
				}
			}
			if (nr.debuggingRouteFromA != null)
			{
				// this NeededRoute was being debugged, so stop now
				RoutingDebug.debugRoute(nr);
				break;
			}
		}
	}

	private void addNetsToList(RTNode<SOGBound> node, MutableInteger net, List<PolyBase> polysInConnection)
	{
		for(int i=0; i<node.getTotal(); i++)
		{
			if (node.getFlag())
			{
				SOGBound b = (SOGBound)node.getChild(i);
				if (b.getNetID() == null) continue;
				if (b.getNetID().intValue() == net.intValue())
				{
					ERectangle rect = b.getBounds();
					polysInConnection.add(new PolyBase(rect));
				}
			} else
			{
				RTNode<SOGBound> subNode = (RTNode)node.getChild(i);
				addNetsToList(subNode, net, polysInConnection);
			}
		}
	}

	/**
	 * Method to retrieve ErrorLogger associated with SoG
	 * @return pointer to ErrorLogger
	 */
	public ErrorLogger getErrorLgger() { return this.errorLogger;}

	/**
	 * Public interface that encapsulates interaction of SeaOfGatesEngines with outer environment
	 */
	public static interface Handler {

		/**
		 * Returns EditingPreferences
		 * @return EditingPreferences
		 */
		EditingPreferences getEditingPreferences();

		/**
		 * Check if we are scheduled to abort. If so, print message if non null and
		 * return true.
		 * @return true on abort, false otherwise. If job is scheduled for abort or
		 *		 aborted. and it will report it to standard output
		 */
		boolean checkAbort();

		/**
		 * Log a message at the TRACE level.
		 *
		 * @param msg the message string to be logged
		 */
		void trace(String msg);

		/**
		 * Log a message at the DEBUG level.
		 *
		 * @param msg the message string to be logged
		 */
		void debug(String msg);

		/**
		 * Log a message at the INFO level.
		 *
		 * @param msg the message string to be logged
		 */
		void info(String msg);

		/**
		 * Log a message at the WARN level.
		 *
		 * @param msg the message string to be logged
		 */
		void warn(String msg);

		/**
		 * Log a message at the ERROR level.
		 *
		 * @param msg the message string to be logged
		 */
		void error(String msg);

		/**
		 * Method called when all errors are logged.  Initializes pointers for replay of errors.
		 */
		void termLogging(ErrorLogger errorLogger);

		/**
		 * Method to start the display of a progress dialog.
		 * @param msg the message to show in the progress dialog.
		 */
		void startProgressDialog(String msg);

		/**
		 * Method to stop the progress bar
		 */
		void stopProgressDialog();

		/**
		 * Method to set a text message in the progress dialog.
		 * @param message the new progress message.
		 */
		void setProgressNote(String message);

		/**
		 * Method to update the progress bar
		 * @param done the amount done (from 0 to total-1).
		 * @param total the total amount to do.
		 */
		void setProgressValue(long done, long total);

		/**
		 * Method to instantiate RouteResolution.
		 * Can be called from any thread.
		 * @param resolution RouteResolution
		 */
		void instantiate(RouteResolution resolution);

		/**
		 * Method to tell the name of the cell where routing results are stored.
		 * If null, results are placed back in the original cell.
		 * @return the name of the routing results cell.
		 */
		String getRoutingCellName();

		/**
		 * flush changes
		 * Can be called only from database thread
		 * @param force unconditionally perform the final flush
		 */
		void flush(boolean force);
	}

	/************************************** ROUTEBATCH: A COLLECTION OF NEEDEDROUTE OBJECTS ON THE SAME NETWORK **************************************/

	/**
	 * Class to hold a "batch" of NeededRoute objects, all on the same network.
	 */
	class RouteBatch implements Comparable<RouteBatch>
	{
		Set<ArcInst> unroutedArcs;
		Set<NodeInst> unroutedNodes;
		List<NeededRoute> routesInBatch;
		boolean isPwrGnd;
		double length;
		String netName;
		private RouteResolution resolution;
		private final ReentrantLock completedRouteLock = new ReentrantLock();

		public RouteBatch(String nn)
		{
			unroutedArcs = new HashSet<ArcInst>();
			unroutedNodes = new HashSet<NodeInst>();
			routesInBatch = new ArrayList<NeededRoute>();
			CellId destCellId = cell.getId();
			resolution = new RouteResolution(destCellId);
			isPwrGnd = false;
			length = 0;
			netName = nn;
		}

		public void addRoute(NeededRoute nr)
		{
			routesInBatch.add(nr);
		}

		private void completedRoute(NeededRoute nr, Wavefront winningWF, SearchVertex result)
		{
			completedRouteLock.lock();
			nr.winningWF = winningWF;
			try {
				if (winningWF != null && winningWF.vertices != null) {
					// if route was successful, setup geometry for it
					winningWF.createRoute();
				}

				// remove the original unrouted nodes/arcs
				for (ArcInst aiKill : unroutedArcs) resolution.killArc(aiKill);
				for (NodeInst niKill : unroutedNodes) resolution.killNode(niKill);
				unroutedArcs.clear();
				unroutedNodes.clear();

				handler.instantiate(resolution);

				// schedule routing of any taps on a spine
				if (nr.spineTaps != null)
				{
					handler.flush(true);
					int tapNumber = 1;
					for(SearchVertex sv : nr.spineTapMap.keySet())
					{
						int thisTapNumber = tapNumber++;
						PortInst aPi = nr.spineTapMap.get(sv);
						ArcProto aArc = getMetalArcOnPort(aPi);
						ImmutableNodeInst ini = nr.spineTapNIMap.get(aPi);
						if (ini != null)
						{
							NodeInst ni = cell.getNodeById(ini.nodeId);
							if (handler.getRoutingCellName() != null)
							{
								String realCellName = handler.getRoutingCellName() + "{lay}";
								Cell realCell = cell.getLibrary().findNodeProto(realCellName);
								if (realCell != null)
								{
									NodeInst otherNi = realCell.getNodeById(ini.nodeId);
									if (otherNi != null) ni = otherNi;
								}
							}
							if (ni != null)
							{
								// schedule the route to make the tap
								PortInst bPi = ni.getOnlyPortInst();
								ArcProto bArc = getMetalArcOnPort(bPi);
								if (aArc != null && bArc != null)
								{
									String routeName = nr.routeName;
									if (routeName.endsWith("spine)"))
										routeName = routeName.substring(0, routeName.length()-1) + " tap " + thisTapNumber + ")";
									NeededRoute nrTap = new NeededRoute(routeName, aPi, bPi, aArc, bArc, null, nr.minWidth);
									nrTap.setNetID(nr.netID);
									boolean aInvalid = nrTap.invalidPort(true, true, aPi);
									boolean bInvalid = nrTap.invalidPort(false, true, bPi);
									if (aInvalid || bInvalid) continue;
									nrTap.growNetwork();
									tapRoutes.add(nrTap);
									nrTap.setBatchInfo(nr.batch, thisTapNumber);
								}
							}
						}
					}
				}
			} finally {
				completedRouteLock.unlock();
			}
		}

		/**
		 * Method to sort RouteBatch by their length and power/ground usage.
		 */
		public int compareTo(RouteBatch other)
		{
			// make power or ground nets come first
			if (isPwrGnd != other.isPwrGnd)
			{
				if (isPwrGnd) return -1;
				return 1;
			}

			// make shorter nets come before longer ones
			if (length < other.length) return -1;
			if (length > other.length) return 1;
			return 0;
		}
	}

	/**
	 * Class to define a single endpoint possibility for a waveform.
	 */
	public static class PossibleEndpoint
	{
		EPoint coord;
		MetalVia viaToPlace;
		double viaSizeX, viaSizeY;
		Orientation viaOrient;

		PossibleEndpoint(EPoint coord, MetalVia mv, double sX, double sY, Orientation o)
		{
			this.coord = coord;
			viaToPlace = mv;
			viaSizeX = sX;     viaSizeY = sY;
			viaOrient = o;
		}

		public EPoint getCoord() { return coord; }
	}

	/**
	 * Class that defines the endpoint of a waveform,
	 * which can be a single point, a rectangle, or a list of coordinates.
	 */
	public static class PossibleEndpoints
	{
		/** The center X/Y coordinates of the endpoint. */		private double x, y;
		/** The endpoint as a point. */							private EPoint coord;
		/** The area of the endpoint. */						private FixpRectangle rect;
		/** The expanded grid of the area of the endpoint. */	private FixpRectangle rectGridded;
		/** An overriding list of coordinates. */				private List<PossibleEndpoint> choices;

		public PossibleEndpoints(double x, double y, FixpRectangle rect, FixpRectangle rectGridded)
		{
			this.x = x;
			this.y = y;
			this.coord = null;
			this.rect = rect;
			this.rectGridded = rectGridded;
			choices = null;
		}

		public void setCenterX(double v) { x = v; coord = null; }

		public void setCenterY(double v) { y = v; coord = null; }

		public void setRect(double x, double y, double w, double h) { rect.setRect(x, y, w, h); }

		public void setGriddedRect(double lX, double hX, double lY, double hY) { rectGridded = FixpRectangle.from(new Rectangle2D.Double(lX, lY, hX-lX, hY-lY)); }

		public void add(PossibleEndpoint pe)
		{
			if (choices == null) choices = new ArrayList<PossibleEndpoint>();
			choices.add(pe);
		}


		public double getCenterX() { return x; }

		public double getCenterY() { return y; }

		public EPoint getCenter()
		{
			if (coord == null) coord = EPoint.fromLambda(x, y);
			return coord;
		}

		public FixpRectangle getRect() { return rect; }

		public FixpRectangle getGriddedRect() { return rectGridded; }

		public boolean hasEndpoints() { return choices != null; }

		public List<PossibleEndpoint> getEndpoints() { return choices; }

		public void finishedAddingEndpoints()
		{
			if (hasEndpoints())
			{
				PossibleEndpoint peFirst = choices.get(0);
				double lX = peFirst.coord.getX(), hX = peFirst.coord.getX();
				double lY = peFirst.coord.getY(), hY = peFirst.coord.getY();
				for(int i=1; i<choices.size(); i++)
				{
					PossibleEndpoint pe = choices.get(i);
					if (pe.coord.getX() < lX) lX = pe.coord.getX();
					if (pe.coord.getX() > hX) hX = pe.coord.getX();
					if (pe.coord.getY() < lY) lY = pe.coord.getY();
					if (pe.coord.getY() > hY) hY = pe.coord.getY();
				}
				rect.setRect(lX, lY, hX-lX, hY-lY);
				rectGridded.setRect(lX, lY, hX-lX, hY-lY);

				// find centermost endpoint
				boolean pointValid = false;
				for(PossibleEndpoint pe : choices)
				{
					if (DBMath.areEquals(pe.coord.getX(), x) && DBMath.areEquals(pe.coord.getY(), y))
						pointValid = true;
				}

				if (!pointValid)
				{
					double cX = (lX + hX) / 2, cY = (lY + hY) / 2;
					PossibleEndpoint bestPE = null;
					double bestDist = Double.MAX_VALUE;
					for(PossibleEndpoint pe : choices)
					{
						double dx = cX - pe.coord.getX(), dy = cY - pe.coord.getY();
						double dist = Math.sqrt(dx*dx + dy*dy);
						if (dist < bestDist)
						{
							bestDist = dist;
							bestPE = pe;
						}
					}
					x = bestPE.coord.getX();
					y = bestPE.coord.getY();
					coord = bestPE.coord;
				}
			}
		}

		/**
		 * Method to return the closest endpoint to a given coordinate.
		 * @param curX the X coordinate.
		 * @param curY the Y coordinate.
		 * @return the closest endpoint to the given coordinate.
		 */
		public EPoint getClosestPoint(double curX, double curY)
		{
			if (hasEndpoints())
			{
				PossibleEndpoint bestPE = null;
				double bestDist = Double.MAX_VALUE;
				for(PossibleEndpoint pe : choices)
				{
					double dx = curX - pe.coord.getX(), dy = curY - pe.coord.getY();
					double dist = Math.sqrt(dx*dx + dy*dy);
					if (dist < bestDist)
					{
						bestDist = dist;
						bestPE = pe;
					}
				}
				if (bestPE != null) return bestPE.getCoord();
			}
			return EPoint.fromLambda(x, y);
		}

		/**
		 * Method to determine if a given point is a destination point.
		 * @param curX the X coordinate.
		 * @param curY the Y coordinate.
		 * @return true if this point is a valid destination point.
		 */
		public boolean isToPoint(double curX, double curY)
		{
			if (hasEndpoints())
			{
				for(PossibleEndpoint pe : choices)
				{
					if (DBMath.areEquals(pe.coord.getX(), curX) && DBMath.areEquals(pe.coord.getY(), curY))
						return true;
				}
				return false;
			}
			return DBMath.pointInRect(EPoint.fromLambda(curX, curY), rect);
		}

		/**
		 * Method to determine if a given coordinate is on Y and within a nonzero range of X coordinates.
		 * @param curX the X coordinate.
		 * @param curY the Y coordinate.
		 * @return true if the given coordinate is on Y and within a nonzero range of X coordinates.
		 */
		public boolean isWithinNonzeroX(double curX, double curY)
		{
			if (rectGridded.getWidth() == 0 || !DBMath.areEquals(curX, x)) return false;

			if (hasEndpoints())
			{
				for(PossibleEndpoint pe : choices)
				{
					if (DBMath.areEquals(pe.coord.getX(), curX) && DBMath.areEquals(pe.coord.getY(), curY))
						return true;
				}
				return false;
			}

			return DBMath.areEquals(curY, y);
		}

		/**
		 * Method to determine if a given coordinate is on X and within a nonzero range of Y coordinates.
		 * @param curX the X coordinate.
		 * @param curY the Y coordinate.
		 * @return true if the given coordinate is on X and within a nonzero range of Y coordinates.
		 */
		public boolean isWithinNonzeroY(double curX, double curY)
		{
			if (rectGridded.getHeight() == 0 || !DBMath.areEquals(curY, y)) return false;

			if (hasEndpoints())
			{
				for(PossibleEndpoint pe : choices)
				{
					if (DBMath.areEquals(pe.coord.getX(), curX) && DBMath.areEquals(pe.coord.getY(), curY))
						return true;
				}
				return false;
			}

			return DBMath.areEquals(curX, x);
		}

		/**
		 * Method to return the closest X coordinate to a given value.
		 * @param curX the given value.
		 * @return the closest X coordinate to a given value.
		 */
		public double getClosestX(double curX)
		{
			if (hasEndpoints())
			{
				PossibleEndpoint bestPE = null;
				double bestDist = Double.MAX_VALUE;
				for(PossibleEndpoint pe : choices)
				{
					double dist = Math.abs(curX - pe.coord.getX());
					if (dist < bestDist)
					{
						bestDist = dist;
						bestPE = pe;
					}
				}
				if (bestPE != null) return bestPE.getCoord().getX();
			}
			return x;
		}

		/**
		 * Method to return the closest Y coordinate to a given value.
		 * @param curY the given value.
		 * @return the closest Y coordinate to a given value.
		 */
		public double getClosestY(double curY)
		{
			if (hasEndpoints())
			{
				PossibleEndpoint bestPE = null;
				double bestDist = Double.MAX_VALUE;
				for(PossibleEndpoint pe : choices)
				{
					double dist = Math.abs(curY - pe.coord.getY());
					if (dist < bestDist)
					{
						bestDist = dist;
						bestPE = pe;
					}
				}
				if (bestPE != null) return bestPE.getCoord().getY();
			}
			return y;
		}

		/**
		 * Method to determine whether the goal is below a given X coordinate.
		 * @param curX the given value.
		 * @return true if the goal is below the given X coordinate.
		 */
		public boolean isBelowX(double curX)
		{
			if (hasEndpoints())
			{
				for(PossibleEndpoint pe : choices)
				{
					if (pe.coord.getX() >= curX) return false;
				}
				return true;
			}

			return x < curX;
		}

		/**
		 * Method to determine whether the goal is above a given X coordinate.
		 * @param curX the given value.
		 * @return true if the goal is above the given X coordinate.
		 */
		public boolean isAboveX(double curX)
		{
			if (hasEndpoints())
			{
				for(PossibleEndpoint pe : choices)
				{
					if (pe.coord.getX() <= curX) return false;
				}
				return true;
			}

			return x > curX;
		}

		/**
		 * Method to determine whether the goal is below a given Y coordinate.
		 * @param curY the given value.
		 * @return true if the goal is below the given Y coordinate.
		 */
		public boolean isBelowY(double curY)
		{
			if (hasEndpoints())
			{
				for(PossibleEndpoint pe : choices)
				{
					if (pe.coord.getY() >= curY) return false;
				}
				return true;
			}

			return y < curY;
		}

		/**
		 * Method to determine whether the goal is above a given Y coordinate.
		 * @param curY the given value.
		 * @return true if the goal is above the given Y coordinate.
		 */
		public boolean isAboveY(double curY)
		{
			if (hasEndpoints())
			{
				for(PossibleEndpoint pe : choices)
				{
					if (pe.coord.getY() <= curY) return false;
				}
				return true;
			}

			return y > curY;
		}

		/**
		 * Method to tell if a given coordinate is a goal point.
		 * @param curX the X coordinate.
		 * @param curY the Y coordinate.
		 * @return true if the given coordinate is a goal point.
		 */
		public boolean atGoalPoint(double curX, double curY)
		{
			if (hasEndpoints())
			{
				for(PossibleEndpoint pe : choices)
				{
					if (DBMath.areEquals(pe.coord.getX(), curX) && DBMath.areEquals(pe.coord.getY(), curY))
						return true;
				}
				return false;
			}

			if (inDestGrid(rectGridded, curX, curY)) return true;
			return false;
		}

		/**
		 * Method to tell if a given X coordinate is at a goal point.
		 * @param curX the X coordinate.
		 * @return true if the given X coordinate is at a goal point.
		 */
		public boolean isOutOfGoalX(double curX)
		{
			if (hasEndpoints())
			{
				for(PossibleEndpoint pe : choices)
				{
					if (DBMath.areEquals(pe.coord.getX(), curX)) return true;
				}
				return false;
			}

			if (curX > rect.getMaxX() || curX < rect.getMinX()) return true;
			return false;
		}

		/**
		 * Method to tell if a given Y coordinate is at a goal point.
		 * @param curY the Y coordinate.
		 * @return true if the given Y coordinate is at a goal point.
		 */
		public boolean isOutOfGoalY(double curY)
		{
			if (hasEndpoints())
			{
				for(PossibleEndpoint pe : choices)
				{
					if (DBMath.areEquals(pe.coord.getY(), curY)) return true;
				}
				return false;
			}

			if (curY > rect.getMaxY() || curY < rect.getMinY()) return true;
			return false;
		}

		/**
		 * Method to tell if a given coordinate is on one of the goal axes.
		 * @param curX the X coordinate.
		 * @param curY the Y coordinate.
		 * @return true if the given coordinate is on one of the goal axes.
		 */
		public boolean isOnGoalAxis(double curX, double curY)
		{
			if (hasEndpoints())
			{
				for(PossibleEndpoint pe : choices)
				{
					if (DBMath.areEquals(pe.coord.getX(), curX) || DBMath.areEquals(pe.coord.getY(), curY))
						return true;
				}
				return false;
			}

			if (DBMath.areEquals(curX, x) || DBMath.areEquals(curY, y)) return true;
			return false;
		}

		/**
		 * Method to return the distance to the closest goal point.
		 * @param curX the X coordinate.
		 * @param curY the Y coordinate.
		 * @return the distance to the closest goal point.
		 */
		public double getDistToGoal(double curX, double curY)
		{
			if (hasEndpoints())
			{
				PossibleEndpoint bestPE = null;
				double bestDist = Double.MAX_VALUE;
				for(PossibleEndpoint pe : choices)
				{
					double dx = curX - pe.coord.getX(), dy = curY - pe.coord.getY();
					double dist = Math.sqrt(dx*dx + dy*dy);
					if (dist < bestDist)
					{
						bestDist = dist;
						bestPE = pe;
					}
				}
				if (bestPE != null) return bestDist;
			}

			return Math.sqrt((curX-x)*(curX-x) + (curY-y)*(curY-y));
		}

		/**
		 * Method to find a goal point in a given range of X values and at a given Y value.
		 * @param lowX the low X value.
		 * @param highX the high X value.
		 * @param curY the Y coordinate.
		 * @return the X coordinate of the goal point that satisfies the request (null if none do).
		 */
		public Double getGoalWithinX(double lowX, double highX, double curY)
		{
			if (hasEndpoints())
			{
				for(PossibleEndpoint pe : choices)
				{
					if (curY == pe.coord.getY() && lowX < pe.coord.getX() && highX > pe.coord.getX())
						return new Double(pe.coord.getX());
				}
				return null;
			}

			if (curY == y && lowX < x && highX > x) return new Double(x);
			return null;
		}

		/**
		 * Method to find a goal point in a given range of Y values and at a given X value.
		 * @param curX the X coordinate.
		 * @param lowY the low Y value.
		 * @param highY the high Y value.
		 * @return the Y coordinate of the goal point that satisfies the request (null if none do).
		 */
		public Double getGoalWithinY(double curX, double lowY, double highY)
		{
			if (hasEndpoints())
			{
				for(PossibleEndpoint pe : choices)
				{
					if (curX == pe.coord.getX() && lowY < pe.coord.getY() && highY > pe.coord.getY())
						return new Double(pe.coord.getY());
				}
				return null;
			}

			if (curX == x && lowY < y && highY > y) return new Double(y);
			return null;
		}

		/**
		 * Method to find a goal point at a Y value and above a given X value.
		 * @param curX the X coordinate.
		 * @param curY the Y coordinate.
		 * @return the X coordinate of the goal point that satisfies the request (null if none do).
		 */
		public Double getGoalAboveX(double curX, double curY)
		{
			if (hasEndpoints())
			{
				for(PossibleEndpoint pe : choices)
				{
					if (curX > pe.coord.getX() && DBMath.areEquals(curY, pe.coord.getY()))
						return new Double(pe.coord.getX());
				}
				return null;
			}

			if (curX > x && curY >= rect.getMinY() && curY <= rect.getMaxY()) return new Double(x);
			return null;
		}

		/**
		 * Method to find a goal point at a Y value and below a given X value.
		 * @param curX the X coordinate.
		 * @param curY the Y coordinate.
		 * @return the X coordinate of the goal point that satisfies the request (null if none do).
		 */
		public Double getGoalBelowX(double curX, double curY)
		{
			if (hasEndpoints())
			{
				for(PossibleEndpoint pe : choices)
				{
					if (curX < pe.coord.getX() && DBMath.areEquals(curY, pe.coord.getY()))
						return new Double(pe.coord.getX());
				}
				return null;
			}

			if (curX < x && curY >= rect.getMinY() && curY <= rect.getMaxY()) return new Double(x);
			return null;
		}

		/**
		 * Method to find a goal point at an X value and above a given Y value.
		 * @param curX the X coordinate.
		 * @param curY the Y coordinate.
		 * @return the Y coordinate of the goal point that satisfies the request (null if none do).
		 */
		public Double getGoalAboveY(double curX, double curY)
		{
			if (hasEndpoints())
			{
				for(PossibleEndpoint pe : choices)
				{
					if (curY > pe.coord.getY() && DBMath.areEquals(curX, pe.coord.getX()))
						return new Double(pe.coord.getY());
				}
				return null;
			}

			if (curY > y && curX >= rect.getMinX() && curX <= rect.getMaxX()) return new Double(y);
			return null;
		}

		/**
		 * Method to find a goal point at an X value and below a given Y value.
		 * @param curX the X coordinate.
		 * @param curY the Y coordinate.
		 * @return the Y coordinate of the goal point that satisfies the request (null if none do).
		 */
		public Double getGoalBelowY(double curX, double curY)
		{
			if (hasEndpoints())
			{
				for(PossibleEndpoint pe : choices)
				{
					if (curY < pe.coord.getY() && DBMath.areEquals(curX, pe.coord.getX()))
						return new Double(pe.coord.getY());
				}
				return null;
			}

			if (curY < y && curX >= rect.getMinX() && curX <= rect.getMaxX()) return new Double(y);
			return null;
		}
	}

	/************************************** NEEDEDROUTE: A ROUTE TO BE RUN **************************************/

	Map<Integer,List<MutableInteger>> netIDsByValue = new HashMap<Integer,List<MutableInteger>>();

	/**
	 * Class to hold a route that must be run.
	 */
	public class NeededRoute
	{
		private String routeName;
		private RouteBatch batch;
		private int routeInBatch;
		private final Rectangle2D routeBounds;
		private MutableInteger netID;
		private final double minWidth;
		private Rectangle2D jumpBound;
		private int complexityLimit;
		private int maxDistance;
		private PossibleEndpoints aEndpoints, bEndpoints;
		private double aTaperWid, bTaperWid;
		private double aTaperLen, bTaperLen;
		private final PortInst aPi, bPi;
		private int aZ, bZ;
		private int aC, bC;
		private boolean alreadyRouted;
		private Boolean debuggingRouteFromA;
		private PossibleEndpoint replaceA, replaceB;
		private int replaceAZ, replaceBZ;
		private int replaceAC, replaceBC;
		private final List<PortInst> spineTaps;
		private final Map<SearchVertex,PortInst> spineTapMap;
		private final Map<PortInst,ImmutableNodeInst> spineTapNIMap;
		private final Poly aPoly, bPoly;
		private Rectangle2D[] buckets;
		private Map<Layer,List<SOGBound>> endBlockages;
		/** true when routed successfully */ private volatile boolean routedSuccess;
		private String errorMessage;
		/** true when this is the second try on the route */	private boolean reroute;
		private SeaOfGatesTrack[][] gridLocationsX, gridLocationsY;
		private Map<SOGBound,Integer> extractList;
		private boolean[] overridePreventArcs, forceGridArcs;
		private double[] overrideMetalWidth, overrideMetalSpacingX, overrideMetalSpacingY;
		private double[][] overrideMetalSpacings;
		private Wavefront winningWF, dirAtoB, dirBtoA;
		private MessageLog loggedMessage;

		public NeededRoute(String routeName, PortInst aPi, PortInst bPi, ArcProto aArc, ArcProto bArc,
			List<PortInst> spineTaps, double minWidth)
		{
			// determine the coordinates of the route
			winningWF = null;
			this.routeName = routeName;
			this.spineTaps = spineTaps;
			this.complexityLimit = prefs.complexityLimit;
			this.maxDistance = prefs.maxDistance;
			alreadyRouted = false;
			replaceA = replaceB = null;
			replaceAZ = replaceBZ = 0;
			replaceAC = replaceBC = 0;
			if (spineTaps == null)
			{
				spineTapMap = null;
				spineTapNIMap = null;
			} else
			{
				spineTapMap =  new HashMap<SearchVertex,PortInst>();
				spineTapNIMap = new HashMap<PortInst,ImmutableNodeInst>();
			}
			overrideMetalWidth = null;
			overrideMetalSpacingX = overrideMetalSpacingY = null;
			overrideMetalSpacings = new double[numMetalLayers][];

			// set minimum arc width if there are no width overrides
			for(int z=0; z<numMetalLayers; z++)
			{
				for(int c=0; c<metalArcs[z].length; c++)
				{
					if (sogp.getDefaultWidthOverride(metalArcs[z][c]) == null) continue;
					minWidth = 0;
					break;
				}
			}
			this.minWidth = minWidth;

			for(int z=0; z<numMetalLayers; z++)
			{
				overrideMetalSpacings[z] = null;
				Double overrideWidth = null;
				boolean hadXOverride = false, hadYOverride = false;
				for(int c=0; c<metalArcs[z].length; c++)
				{
					Double o = sogp.getDefaultWidthOverride(metalArcs[z][c]);
					if (o == null) continue;
					if (overrideWidth == null || o.doubleValue() > overrideWidth.doubleValue()) overrideWidth = o;

					SeaOfGatesArcProperties sogap = sogp.getOverridesForArcsOnNet(routeName, metalArcs[z][c]);
					if (sogap != null && sogap.getWidthOverride() != null) overrideWidth = sogap.getWidthOverride();
					if (overrideWidth != null)
					{
						if (overrideMetalWidth == null)
						{
							overrideMetalWidth = new double[numMetalLayers];
							for (int i = 0; i < numMetalLayers; i++)
							{
								if (metalArcs[i] == null) continue;
								for(int cc=0; cc<metalArcs[i].length; cc++)
									overrideMetalWidth[i] = Math.max(metalArcs[i][cc].getDefaultLambdaBaseWidth(ep), minWidth);
							}
						}
						overrideMetalWidth[z] = overrideWidth.doubleValue();
					}

					Double overrideSpacingX = sogp.getDefaultSpacingOverride(metalArcs[z][c], 0);
					Double overrideSpacingY = sogp.getDefaultSpacingOverride(metalArcs[z][c], 1);
					if (sogap != null && sogap.getSpacingOverride(0) != null) overrideSpacingX = sogap.getSpacingOverride(0);
					if (sogap != null && sogap.getSpacingOverride(1) != null) overrideSpacingY = sogap.getSpacingOverride(1);

					if (overrideSpacingX != null)
					{
						hadXOverride = true;
						if (overrideMetalSpacingX == null) // first time?
						{
							overrideMetalSpacingX = new double[numMetalLayers];
							for (int i = 0; i < numMetalLayers; i++)
							{
								overrideMetalSpacingX[i] = 0;
								if (metalLayers[i] == null || metalArcs[i] == null) continue;
								for(int cc=0; cc<metalArcs[i].length; cc++)
								{
									DRCTemplate rule = DRC.getSpacingRule(metalLayers[i][cc], null, metalLayers[i][cc], null,
										false, -1, metalArcs[i][cc].getDefaultLambdaBaseWidth(ep), 50);
									if (rule != null)
										overrideMetalSpacingX[i] = rule.getValue(0);
								}
							}
						}
						overrideMetalSpacingX[z] = overrideSpacingX.doubleValue();
					}
					if (overrideSpacingY != null)
					{
						hadYOverride = true;
						if (overrideMetalSpacingY == null) // first time?
						{
							overrideMetalSpacingY = new double[numMetalLayers];
							for (int i = 0; i < numMetalLayers; i++)
							{
								overrideMetalSpacingY[i] = 0;
								if (metalLayers[i] == null || metalArcs[i] == null) continue;
								for(int cc=0; cc<metalArcs[i].length; cc++)
								{
									DRCTemplate rule = DRC.getSpacingRule(metalLayers[i][cc], null, metalLayers[i][cc], null,
										false, -1, metalArcs[i][cc].getDefaultLambdaBaseWidth(ep), 50);
									if (rule != null)
									{
										if (rule.getNumValues() <= 1) overrideMetalSpacingY[i] = rule.getValue(0); else
											overrideMetalSpacingY[i] = rule.getValue(1);
									}
								}
							}
						}
						overrideMetalSpacingY[z] = overrideSpacingY.doubleValue();
					}
				}
				if (hadXOverride && hadYOverride)
					overrideMetalSpacings[z] = new double[] {overrideMetalSpacingX[z], overrideMetalSpacingY[z]};
			}

			this.aPi = aPi;
			this.bPi = bPi;
			aPoly = aPi.getPoly();
			bPoly = bPi.getPoly();

			// determine area of endpoints
			FixpRectangle aRect = aPoly.getBounds2D();
			FixpRectangle bRect = bPoly.getBounds2D();

			double aX, aY, bX, bY;
			if (bRect.getMaxX() < aRect.getMinX())
			{
				bX = upToGrain(bRect.getCenterX());
				aX = downToGrain(aRect.getCenterX());
			} else if (bRect.getMinX() > aRect.getMaxX())
			{
				bX = downToGrain(bRect.getCenterX());
				aX = upToGrain(aRect.getCenterX());
			} else
			{
				double xVal = (Math.max(bRect.getMinX(), aRect.getMinX()) + Math.min(bRect.getMaxX(), aRect.getMaxX())) / 2;
				bX = aX = upToGrain(xVal);
			}
			if (bRect.getMaxY() < aRect.getMinY())
			{
				bY = upToGrain(bRect.getCenterY());
				aY = downToGrain(aRect.getCenterY());
			} else if (bRect.getMinY() > aRect.getMaxY())
			{
				bY = downToGrain(bRect.getCenterY());
				aY = upToGrain(aRect.getCenterY());
			} else
			{
				double yVal = (Math.max(bRect.getMinY(), aRect.getMinY()) + Math.min(bRect.getMaxY(), aRect.getMaxY())) / 2;
				bY = aY = upToGrain(yVal);
			}

			aZ = aArc.getFunction().getLevel() - 1;
			bZ = bArc.getFunction().getLevel() - 1;
			aC = aArc.getMaskLayer();
			bC = bArc.getMaskLayer();

			double lowX = Math.min(aRect.getMinX(), bRect.getMinX()), highX = Math.max(aRect.getMaxX(), bRect.getMaxX());
			double lowY = Math.min(aRect.getMinY(), bRect.getMinY()), highY = Math.max(aRect.getMaxY(), bRect.getMaxY());

			// first construct a grid for an immense bound
			// original value was 100
			double gap = DRC.getWorstSpacingDistance(tech, -1) * maxDistance;
			Rectangle2D testBounds = new Rectangle2D.Double(lowX - gap, lowY - gap, highX - lowX + gap * 2, highY - lowY + gap * 2);
			buildGrids(aX, aY, bX, bY, testBounds);

			// now expand the end bounds to reach grid locations
			double aLX = getLowerXGrid(aZ, aRect.getMinX()).getCoordinate();
			double aHX = getUpperXGrid(aZ, aRect.getMaxX()).getCoordinate();
			double aLY = getLowerYGrid(aZ, aRect.getMinY()).getCoordinate();
			double aHY = getUpperYGrid(aZ, aRect.getMaxY()).getCoordinate();
			FixpRectangle aRectGridded = FixpRectangle.from(new Rectangle2D.Double(aLX, aLY, aHX-aLX, aHY-aLY));
			double bLX = getLowerXGrid(bZ, bRect.getMinX()).getCoordinate();
			double bHX = getUpperXGrid(bZ, bRect.getMaxX()).getCoordinate();
			double bLY = getLowerYGrid(bZ, bRect.getMinY()).getCoordinate();
			double bHY = getUpperYGrid(bZ, bRect.getMaxY()).getCoordinate();
			FixpRectangle bRectGridded = FixpRectangle.from(new Rectangle2D.Double(bLX, bLY, bHX-bLX, bHY-bLY));

			aEndpoints = new PossibleEndpoints(aX, aY, aRect, aRectGridded);
			bEndpoints = new PossibleEndpoints(bX, bY, bRect, bRectGridded);

			// now define bounds as the gridded envelope of a smaller bound
			double maxStrayFromRouteBoundsX = gap, maxStrayFromRouteBoundsY = gap;
			double griddedLowX = Math.min(getLowerXGrid(aZ, lowX-maxStrayFromRouteBoundsX).getCoordinate(), getLowerXGrid(bZ, lowX-maxStrayFromRouteBoundsX).getCoordinate());
			double griddedHighX = Math.max(getUpperXGrid(aZ, highX+maxStrayFromRouteBoundsX).getCoordinate(), getUpperXGrid(bZ, highX+maxStrayFromRouteBoundsX).getCoordinate());
			double griddedLowY = Math.min(getLowerYGrid(aZ, lowY-maxStrayFromRouteBoundsY).getCoordinate(), getLowerYGrid(bZ, lowY-maxStrayFromRouteBoundsY).getCoordinate());
			double griddedHighY = Math.max(getUpperYGrid(aZ, highY+maxStrayFromRouteBoundsY).getCoordinate(), getUpperYGrid(bZ, highY+maxStrayFromRouteBoundsY).getCoordinate());
			if (routingBoundsLimit != null)
			{
				if (griddedLowX < routingBoundsLimit.getMinX()) griddedLowX = routingBoundsLimit.getMinX();
				if (griddedHighX > routingBoundsLimit.getMaxX()) griddedHighX = routingBoundsLimit.getMaxX();
				if (griddedLowY < routingBoundsLimit.getMinY()) griddedLowY = routingBoundsLimit.getMinY();
				if (griddedHighY > routingBoundsLimit.getMaxY()) griddedHighY = routingBoundsLimit.getMaxY();
			}
			routeBounds = new Rectangle2D.Double(griddedLowX, griddedLowY, griddedHighX - griddedLowX, griddedHighY - griddedLowY);
			jumpBound = new Rectangle2D.Double(Math.min(aX, bX), Math.min(aY, bY), Math.abs(aX-bX), Math.abs(aY-bY));

			// set overrides for this specific route
			overridePreventArcs = null;
			List<ArcProto> arcs = sogp.getArcsOnNet(routeName);
			if (arcs != null && arcs.size() > 0)
			{
				overridePreventArcs = new boolean[numMetalLayers];
				for(int i=0; i<numMetalLayers; i++) overridePreventArcs[i] = true;
				for(ArcProto ap : arcs)
				{
					int metNum = ap.getFunction().getLevel() - 1;
					overridePreventArcs[metNum] = false;
				}
			}
			forceGridArcs = new boolean[numMetalLayers];
			for(int i=0; i<numMetalLayers; i++)
			{
				forceGridArcs[i] = false;
				for(int c=0; c<metalArcs[i].length; c++)
					if (sogp.isGridForced(metalArcs[i][c])) forceGridArcs[i] = true;
			}

			// determine the taper widths
			aTaperWid = getTaperWidth(aPi, aZ);
			bTaperWid = getTaperWidth(bPi, bZ);
			aTaperLen = taperLength[aZ];
			bTaperLen = taperLength[bZ];
			if (DBMath.areEquals(aTaperWid, getUntaperedArcWidth(aZ))) aTaperLen = -1;
			if (DBMath.areEquals(bTaperWid, getUntaperedArcWidth(bZ))) bTaperLen = -1;
		}

		public boolean getRoutedSucess() { return routedSuccess; }
		public boolean isAlreadyRouted() { return alreadyRouted; }
		public Wavefront getWavefront() { return winningWF; }
		public Wavefront getWavefrontAtoB() { return dirAtoB; }
		public Wavefront getWavefrontBtoA() { return dirBtoA; }
		public double getATaperWidth() { return aTaperWid; }
		public double getBTaperWidth() { return bTaperWid; }
		public double getATaperLength() { return aTaperLen; }
		public double getBTaperLength() { return bTaperLen; }
		public PossibleEndpoints getAPossibleEndpoints() { return aEndpoints; }
		public PossibleEndpoints getBPossibleEndpoints() { return bEndpoints; }
		public String getErrorMessage() { return errorMessage; }

		public void setDebugging(Boolean fromA) { debuggingRouteFromA = fromA; }

		/**
		 * Method to check the validity of the endpoints with respect to forced gridding.
		 * If the grid is being forced and the endpoints are not on grid,
		 * routing may fail (and a warning will be issued here).
		 */
		public void checkGridValidity()
		{
			if (forceGridArcs[aZ])
			{
				boolean hor = true;
				if (sogp.isHorizontalEven())
				{
					if ((aZ%2) == 0) hor = false;
				} else
				{
					if ((aZ%2) != 0) hor = false;
				}
				if (!hor && !isOnXGrid(aZ, getAX()))
				{
					List<EPoint> offGrid = new ArrayList<EPoint>(); offGrid.add(aEndpoints.getCenter()); offGrid.add(aEndpoints.getCenter());
					warn("Route " + routeName + ", end (" + TextUtils.formatDistance(aEndpoints.getCenterX()) + "," + TextUtils.formatDistance(aEndpoints.getCenterY()) + "," +
						describeMetal(aZ,aC) + ") is not on X grid (nearest X grids are at " + TextUtils.formatDistance(getLowerXGrid(aZ, aEndpoints.getCenterX()).getCoordinate()) +
						" and " + TextUtils.formatDistance(getUpperXGrid(aZ, aEndpoints.getCenterX()).getCoordinate()) + "). Route may fail.", cell, offGrid, null);
				}
				if (hor && !isOnYGrid(aZ, aEndpoints.getCenterY()))
				{
					List<EPoint> offGrid = new ArrayList<EPoint>(); offGrid.add(aEndpoints.getCenter()); offGrid.add(aEndpoints.getCenter());
					warn("Route " + routeName + ", end (" + TextUtils.formatDistance(aEndpoints.getCenterX()) + "," + TextUtils.formatDistance(aEndpoints.getCenterY()) + "," +
						describeMetal(aZ,aC) + ") is not on Y grid (nearest Y grids are at " + TextUtils.formatDistance(getLowerYGrid(aZ, aEndpoints.getCenterY()).getCoordinate()) +
						" and " + TextUtils.formatDistance(getUpperYGrid(aZ, aEndpoints.getCenterY()).getCoordinate()) + "). Route may fail.", cell, offGrid, null);
				}
			}
			if (forceGridArcs[bZ])
			{
				boolean hor = true;
				if (sogp.isHorizontalEven())
				{
					if ((bZ%2) == 0) hor = false;
				} else
				{
					if ((bZ%2) != 0) hor = false;
				}
				if (!hor && !isOnXGrid(bZ, bEndpoints.getCenterX()))
				{
					List<EPoint> offGrid = new ArrayList<EPoint>(); offGrid.add(bEndpoints.getCenter()); offGrid.add(bEndpoints.getCenter());
					warn("Route " + routeName + ", end (" + TextUtils.formatDistance(bEndpoints.getCenterX()) + "," + TextUtils.formatDistance(bEndpoints.getCenterY()) + "," +
						describeMetal(bZ,bC) + ") is not on X grid (nearest X grids are at " + TextUtils.formatDistance(getLowerXGrid(bZ, bEndpoints.getCenterX()).getCoordinate()) +
						" and " + TextUtils.formatDistance(getUpperXGrid(bZ, bEndpoints.getCenterX()).getCoordinate()) + "). Route may fail.", cell, offGrid, null);
				}
				if (hor && !isOnYGrid(bZ, bEndpoints.getCenterY()))
				{
					List<EPoint> offGrid = new ArrayList<EPoint>(); offGrid.add(bEndpoints.getCenter()); offGrid.add(bEndpoints.getCenter());
					warn("Route " + routeName + ", end (" + TextUtils.formatDistance(bEndpoints.getCenterX()) + "," + TextUtils.formatDistance(bEndpoints.getCenterY()) + "," +
						describeMetal(bZ,bC) + ") is not on Y grid (nearest Y grids are at " + TextUtils.formatDistance(getLowerYGrid(bZ, bEndpoints.getCenterY()).getCoordinate()) +
						" and " + TextUtils.formatDistance(getUpperYGrid(bZ, bEndpoints.getCenterY()).getCoordinate()) + "). Route may fail.", cell, offGrid, null);
				}
			}
		}

		public SeaOfGatesTrack[][] getXRoutingGrid() { return gridLocationsX; }

		public SeaOfGatesTrack[][] getYRoutingGrid() { return gridLocationsY; }

		/**
		 * Method to construct grids for all layers in the vicinity of this route.
		 */
		public void buildGrids(double aX, double aY, double bX, double bY, Rectangle2D bounds)
		{
			gridLocationsX = new SeaOfGatesTrack[numMetalLayers][];
			gridLocationsY = new SeaOfGatesTrack[numMetalLayers][];

			// first make the grid positions for each layer
			for(int metNum=1; metNum<=numMetalLayers; metNum++)
			{
				int metIndex = metNum - 1;
				SeaOfGatesTrack[] thisGrid = metalGrid[metIndex];
				if (thisGrid == null) continue;
				if (!sogp.isForceHorVer() && !sogp.isFavorHorVer()) continue;
				boolean hor = true;
				if (sogp.isHorizontalEven())
				{
					if ((metNum%2) != 0) hor = false;
				} else
				{
					if ((metNum%2) == 0) hor = false;
				}
				double offset = thisGrid[0].getCoordinate();
				double range = thisGrid[thisGrid.length-1].getCoordinate() - offset;
				range += thisGrid[1].getCoordinate() - thisGrid[0].getCoordinate();
				if (range > 0)
				{
					List<SeaOfGatesTrack> values = new ArrayList<SeaOfGatesTrack>();
					double low, high;
					if (hor)
					{
						low = bounds.getMinY();
						high = bounds.getMaxY();
					} else
					{
						low = bounds.getMinX();
						high = bounds.getMaxX();
					}
					double lowGroup = Math.floor((low - offset) / range) * range;
					double highGroup = Math.ceil((high - offset) / range) * range;
					for(double v = lowGroup; v <= highGroup; v += range)
					{
						for(int i=0; i<thisGrid.length; i++)
						{
							double val = v + thisGrid[i].getCoordinate();
							int maskNum = thisGrid[i].getMaskNum();
							if (val >= low && val <= high)
								values.add(new SeaOfGatesTrack(val, maskNum));
						}
					}
					if (values.size() >= 2)
					{
						if (hor)
						{
							gridLocationsY[metIndex] = makeArrayOfUniqueTracks(values);
						} else
						{
							gridLocationsX[metIndex] = makeArrayOfUniqueTracks(values);
						}
					}
				}
			}

			// now make the intermediate grid positions that combine locations from upper and lower layers
			for(int metNum=1; metNum<=numMetalLayers; metNum++)
			{
				int metIndex = metNum - 1;
				boolean hor = true;
				if (sogp.isHorizontalEven())
				{
					if ((metNum%2) != 0) hor = false;
				} else
				{
					if ((metNum%2) == 0) hor = false;
				}

				SeaOfGatesTrack[][] gridLocations;
				SeaOfGatesTrack t1, t2;
				if (hor)
				{
					// horizontal layer: combine X locations from upper and lower layers
					gridLocations = gridLocationsX;
					t1 = getClosestXGrid(aZ, aX);
					t2 = getClosestXGrid(bZ, bX);
				} else
				{
					// vertical layer: combine Y locations from upper and lower layers
					gridLocations = gridLocationsY;
					t1 = getClosestXGrid(aZ, aY);
					t2 = getClosestXGrid(bZ, bY);
				}
				List<SeaOfGatesTrack> values = new ArrayList<SeaOfGatesTrack>();
				boolean realGrid = false;
				if (metIndex > 0)
					realGrid |= gridAlternateLayer(metIndex, -1, values, t1, t2, gridLocations);
				if (metIndex < numMetalLayers-1)
					realGrid |= gridAlternateLayer(metIndex, 1, values, t1, t2, gridLocations);
				if (realGrid)
				{
					gridLocations[metIndex] = makeArrayOfUniqueTracks(values);
				}
			}
		}

		private SeaOfGatesTrack[] makeArrayOfUniqueTracks(List<SeaOfGatesTrack> values)
		{
			Collections.sort(values);
			for(int i=1; i<values.size(); i++)
			{
				if (DBMath.areEquals(values.get(i-1).getCoordinate(), values.get(i).getCoordinate()))
				{
					values.remove(i);
					i--;
				}
			}
			SeaOfGatesTrack[] gridLocations = new SeaOfGatesTrack[values.size()];
			int i=0;
			for(SeaOfGatesTrack v : values) gridLocations[i++] = v;
			return gridLocations;
		}

		/**
		 * Method to consider an alternate layer's gridding when building the current layer's gridding information.
		 * @param curLayer the metal layer being considered (0-based).
		 * @param diff +1 or -1 to indicate the adjoining layer.
		 * @param values a Set of Doubles with grid stops in the X/Y axis.
		 * @param e1 the A endpoint coordinate (X or Y) to use if needed.
		 * @param e1 the B endpoint coordinate (X or Y) to use if needed.
		 * @param gridLocations the grid values (entry may be null)
		 * @return true if there are real grid values applied from a neighboring layer.
		 */
		private boolean gridAlternateLayer(int curLayer, int diff, List<SeaOfGatesTrack> values, SeaOfGatesTrack t1, SeaOfGatesTrack t2, SeaOfGatesTrack[][] gridLocations)
		{
			// if the current layer is blocked, ignore grid building on it
			if (sogp.isPrevented(primaryMetalArc[curLayer])) return false;

			// if the alternate layer is blocked, ignore its grid factor
			int altLayer = curLayer + diff;

			// if the alternate layer has no grid, use the endpoints of the route
			if (gridLocations[altLayer] == null)
			{
				values.add(t1);
				values.add(t2);
				return false;
			}

			// alternate layer is gridded, so grid this layer with those stops
			for(int i=0; i<gridLocations[altLayer].length; i++)
				values.add(new SeaOfGatesTrack(gridLocations[altLayer][i].getCoordinate(), gridLocations[altLayer][i].getMaskNum()));
			return true;
		}

		/**
		 * Method to adjust an X value down to the lower grid value.
		 * @param metNum the metal layer number (0-based).
		 * @param value the X value to be adjusted.
		 * @return the closest X grid value at or below the given value.
		 */
		public SeaOfGatesTrack getLowerXGrid(int metNum, double value)
		{
			return findLowerValue(gridLocationsX[metNum], value);
		}

		/**
		 * Method to adjust an X value up to the higher grid value.
		 * @param metNum the metal layer number (0-based).
		 * @param value the X value to be adjusted.
		 * @return the closest X grid value at or above the given value.
		 */
		public SeaOfGatesTrack getUpperXGrid(int metNum, double value)
		{
			return findUpperValue(gridLocationsX[metNum], value);
		}

		/**
		 * Method to adjust an X value up to the nearest grid value.
		 * @param metNum the metal layer number (0-based).
		 * @param value the X value to be adjusted.
		 * @return the closest X grid value to the given value.
		 */
		public SeaOfGatesTrack getClosestXGrid(int metNum, double value)
		{
			return findClosestValue(gridLocationsX[metNum], value);
		}

		/**
		 * Method to adjust an Y value down to the lower grid value.
		 * @param metNum the metal layer number (0-based).
		 * @param value the Y value to be adjusted.
		 * @return the closest Y grid value at or below the given value.
		 */
		public SeaOfGatesTrack getLowerYGrid(int metNum, double value)
		{
			return findLowerValue(gridLocationsY[metNum], value);
		}

		/**
		 * Method to adjust an Y value up to the higher grid value.
		 * @param metNum the metal layer number (0-based).
		 * @param value the Y value to be adjusted.
		 * @return the closest Y grid value at or above the given value.
		 */
		public SeaOfGatesTrack getUpperYGrid(int metNum, double value)
		{
			return findUpperValue(gridLocationsY[metNum], value);
		}

		/**
		 * Method to adjust an Y value up to the nearest grid value.
		 * @param metNum the metal layer number (0-based).
		 * @param value the Y value to be adjusted.
		 * @return the closest Y grid value to the given value.
		 */
		public SeaOfGatesTrack getClosestYGrid(int metNum, double value)
		{
			return findClosestValue(gridLocationsY[metNum], value);
		}

		public boolean isOnXGrid(int metNum, double value)
		{
			return isOnGrid(gridLocationsX[metNum], value);
		}

		public boolean isOnYGrid(int metNum, double value)
		{
			return isOnGrid(gridLocationsY[metNum], value);
		}

		private SeaOfGatesTrack findLowerValue(SeaOfGatesTrack[] thisGrid, double value)
		{
			if (thisGrid == null) return new SeaOfGatesTrack(value, 0);
			int lo = 0, hi = thisGrid.length - 1;
			if (DBMath.isLessThanOrEqualTo(value, thisGrid[lo].getCoordinate())) return thisGrid[lo];
			if (DBMath.isGreaterThanOrEqualTo(value, thisGrid[hi].getCoordinate())) return thisGrid[hi];

			for(int i=0; i<1000; i++)
			{
				int med = (hi + lo) / 2;
				if (DBMath.isGreaterThanOrEqualTo(value, thisGrid[med].getCoordinate()) &&
					DBMath.isLessThan(value, thisGrid[med+1].getCoordinate())) return thisGrid[med];
				if (DBMath.isLessThan(value, thisGrid[med].getCoordinate())) hi = med; else lo = med;
			}
			return new SeaOfGatesTrack(value, 0);
		}

		private SeaOfGatesTrack findUpperValue(SeaOfGatesTrack[] thisGrid, double value)
		{
			if (thisGrid == null) return new SeaOfGatesTrack(value, 0);

			int lo = 0, hi = thisGrid.length - 1;
			if (DBMath.isLessThanOrEqualTo(value, thisGrid[lo].getCoordinate())) return thisGrid[lo];
			if (DBMath.isGreaterThanOrEqualTo(value, thisGrid[hi].getCoordinate())) return thisGrid[hi];

			for(int i=0; i<1000; i++)
			{
				int med = (hi + lo) / 2;
				if (DBMath.isGreaterThan(value, thisGrid[med].getCoordinate()) &&
					DBMath.isLessThanOrEqualTo(value, thisGrid[med+1].getCoordinate())) return thisGrid[med+1];
				if (DBMath.isLessThanOrEqualTo(value, thisGrid[med].getCoordinate())) hi = med; else lo = med;
			}
			return new SeaOfGatesTrack(value, 0);
		}

		private SeaOfGatesTrack findClosestValue(SeaOfGatesTrack[] thisGrid, double value)
		{
			if (thisGrid == null) return new SeaOfGatesTrack(value, 0);

			int lo = 0, hi = thisGrid.length - 1;
			if (DBMath.isLessThanOrEqualTo(value, thisGrid[lo].getCoordinate())) return thisGrid[lo];
			if (DBMath.isGreaterThanOrEqualTo(value, thisGrid[hi].getCoordinate())) return thisGrid[hi];

			for(int i=0; i<1000; i++)
			{
				int med = (hi + lo) / 2;
				if (DBMath.isGreaterThanOrEqualTo(value, thisGrid[med].getCoordinate()) &&
					DBMath.isLessThan(value, thisGrid[med+1].getCoordinate()))
				{
					if (DBMath.isLessThan(value - thisGrid[med].getCoordinate(), thisGrid[med+1].getCoordinate() - value))
						return thisGrid[med];
					return thisGrid[med+1];
				}
				if (DBMath.isLessThanOrEqualTo(value, thisGrid[med].getCoordinate())) hi = med; else lo = med;
			}
			return new SeaOfGatesTrack(value, 0);
		}

		private boolean isOnGrid(SeaOfGatesTrack[] thisGrid, double value)
		{
			if (thisGrid != null)
			{
				int lo = 0, hi = thisGrid.length - 1;
				if (DBMath.isLessThan(value, thisGrid[lo].getCoordinate())) return false;
				if (DBMath.isGreaterThan(value, thisGrid[hi].getCoordinate())) return false;

				for(int i=0; i<1000; i++)
				{
					int med = (hi + lo) / 2;
					if (DBMath.isGreaterThanOrEqualTo(value, thisGrid[med].getCoordinate()) &&
						DBMath.isLessThanOrEqualTo(value, thisGrid[med+1].getCoordinate()))
					{
						if (DBMath.areEquals(value, thisGrid[med].getCoordinate()) ||
							DBMath.areEquals(thisGrid[med+1].getCoordinate(), value))
							return true;
						return false;
					}
					if (DBMath.isLessThanOrEqualTo(value, thisGrid[med].getCoordinate())) hi = med; else lo = med;
				}
				return false;
			}
			return true;
		}

		/**
		 * Method to round a value up to the nearest routing grain size.
		 * @param v the value to round up.
		 * @return the granularized value.
		 */
		private double upToGrain(double v)
		{
			return v;
		}

		/**
		 * Method to round a value up to the nearest routing grain size.
		 * @param v the value to round up.
		 * @return the granularized value.
		 */
		private double upToGrainAlways(double v)
		{
			return Math.ceil(v);
		}

		/**
		 * Method to round a value down to the nearest routing grain size.
		 * @param v the value to round down.
		 * @return the granularized value.
		 */
		private double downToGrain(double v)
		{
			return v;
		}

		/**
		 * Method to round a value down to the nearest routing grain size.
		 * @param v the value to round down.
		 * @return the granularized value.
		 */
		private double downToGrainAlways(double v)
		{
			return Math.floor(v);
		}

		private class ContactPlacementError
		{
			public int metalLayer;
			public ERectangle offendingRect;
			public Rectangle2D missingRect;

			public ContactPlacementError(int metalLayer)
			{
				this.metalLayer = metalLayer;
			}

			public String getError(PrimitiveNode pNp)
			{
				String msg = pNp.describe(false);
				if (missingRect != null)
				{
					msg += " is missing Metal " + (metalLayer+1) + " at " + TextUtils.formatDistance(missingRect.getMinX()) +
						"<=X<=" + TextUtils.formatDistance(missingRect.getMaxX()) + " and " + TextUtils.formatDistance(missingRect.getMinY()) +
						"<=Y<=" + TextUtils.formatDistance(missingRect.getMaxY());
				} else
				{
					msg += " has spacing error to Metal " + (metalLayer+1) + " at " + TextUtils.formatDistance(offendingRect.getMinX()) +
						"<=X<=" + TextUtils.formatDistance(offendingRect.getMaxX()) + " and " + TextUtils.formatDistance(offendingRect.getMinY()) +
						"<=Y<=" + TextUtils.formatDistance(offendingRect.getMaxY());
				}
				return msg;
			}
		}

		/**
		 * Method to determine whether a contact can be placed that will connect existing metal to new metal.
		 * @param np the contact.
		 * @param newMetal the new metal layer (0-based).
		 * @param offendingMetal the existing metal layer (0-based).
		 * @param offendingMetalColor the mask color of the existing metal layer.
		 * @param conX the X coordinate of the contact.
		 * @param conY the Y coordinate of the contact.
		 * @param conWid the width of the contact.
		 * @param conHei the height of the contact.
		 * @param orient the orientation of the contact.
		 * @return null if the contact fits (the offending metal already exists and the new metal is available).
		 * Returns a ContactPlacementError object to describe the problem if there is one.
		 */
		private ContactPlacementError canPlaceContact(PrimitiveNode np, int newMetal, int offendingMetal, int offendingMetalColor,
			double conX, double conY, double conWid, double conHei, Orientation orient, Boolean endA)
		{
			NodeInst dummyNi = NodeInst.makeDummyInstance(np, ep, EPoint.fromLambda(conX, conY), conWid, conHei, orient);
			Poly[] conPolys = tech.getShapeOfNode(dummyNi);
			FixpTransform trans = null;
			int blockFactor = (endA == null ? 0 : (endA.booleanValue() ? BLOCKAGEENDA : BLOCKAGEENDB));
			MutableInteger mi = new MutableInteger(netID.intValue() + blockFactor);
			if (orient != Orientation.IDENT) trans = dummyNi.rotateOut();
			for (int p = 0; p < conPolys.length; p++)
			{
				Poly conPoly = conPolys[p];
				Layer conLayer = conPoly.getLayer();
				if (!conLayer.getFunction().isMetal()) continue;
				if (trans != null) conPoly.transform(trans);
				Rectangle2D conRect = conPoly.getBounds2D();

				// if this is the offending metal layer, check to see that the geometry already exists
				boolean found = false;
				for(int c=0; c<metalLayers[offendingMetal].length; c++)
					if (conLayer == metalLayers[offendingMetal][c] &&
						conLayer.getFunction().getMaskColor() == offendingMetalColor) found = true;
				if (found)
				{
					EPoint problem = null;
					if (!isPointInMetal(conRect.getMinX(), conRect.getMinY(), offendingMetal, mi))
						problem = EPoint.fromLambda(conRect.getMinX(), conRect.getMinY());
					if (problem == null && !isPointInMetal(conRect.getMinX(), conRect.getMaxY(), offendingMetal, mi))
						problem = EPoint.fromLambda(conRect.getMinX(), conRect.getMaxY());
					if (problem == null && !isPointInMetal(conRect.getMaxX(), conRect.getMaxY(), offendingMetal, mi))
						problem = EPoint.fromLambda(conRect.getMaxX(), conRect.getMaxY());
					if (problem == null && !isPointInMetal(conRect.getMaxX(), conRect.getMinY(), offendingMetal, mi))
						problem = EPoint.fromLambda(conRect.getMaxX(), conRect.getMinY());
					if (problem == null && !isPointInMetal(conRect.getCenterX(), conRect.getCenterY(), offendingMetal, mi))
						problem = EPoint.fromLambda(conRect.getCenterX(), conRect.getCenterY());
					if (problem != null)
					{
						// offending layer is not there: cannot place contact
						ContactPlacementError cpe = new ContactPlacementError(offendingMetal);
						cpe.missingRect = conRect;
						return cpe;
					}
				}

				// if this is the new metal layer, make sure there is room for it
				found = false;
				int color = -1;
				for(int c=0; c<metalLayers[newMetal].length; c++)
					if (conLayer == metalLayers[newMetal][c]) { found = true; color = c; }
				if  (found)
				{
					double[] fromSurround = getSpacingRule(newMetal, maxDefArcWidth[newMetal], -1);
					double halfWidth = conRect.getWidth() / 2;
					double halfHeight = conRect.getHeight() / 2;
					SOGBound block = getMetalBlockage(netID, newMetal, color, halfWidth, halfHeight, fromSurround,
						conRect.getCenterX(), conRect.getCenterY());
					if (block != null)
					{
						ContactPlacementError cpe = new ContactPlacementError(newMetal);
						cpe.offendingRect = block.bound;
						return cpe;
					}
				}
			}
			return null;
		}

		/**
		 * Method to determine whether a point is covered by a metal layer on a given network.
		 * @param x the X coordinate to search.
		 * @param y the Y coordinate to search.
		 * @param metalNo the metal number to consider.
		 * @param netID the netID that must be on the metal (null to ignore this).
		 * @return true if the point is in metal.
		 */
		private boolean isPointInMetal(double x, double y, int metalNo, MutableInteger netID)
		{
			// get the R-Tree data for the metal layer
			BlockageTree bTree = rTrees.getMetalTree(primaryMetalLayer[metalNo]);
			bTree.lock();
			try {
				if (bTree.isEmpty()) return false;

				Rectangle2D searchArea = new Rectangle2D.Double(x, y, 0, 0);
				for (Iterator<SOGBound> sea = bTree.search(searchArea); sea.hasNext();)
				{
					SOGBound sBound = sea.next();
					if (sBound.containsPoint(x, y))
					{
						if (netID != null)
						{
							if (!sBound.isSameBasicNet(netID)) continue;
							int endBits = BLOCKAGEENDA | BLOCKAGEENDB;
							if ((sBound.getNetID().intValue()&endBits) != (netID.intValue()&endBits)) continue;
						}
						return true;
					}
				}
			} finally {
				bTree.unlock();
			}
			return false;
		}

		/**
		 * Method to determine the size and orientation of a contact.
		 * @param mv the MetalVia describing the contact.
		 * @param wid where the width will be stored.
		 * @param hei where the height will be stored.
		 * @return the orientation to use.
		 */
		private Orientation getMVSize(MetalVia mv, double x, double y, double lastX, double lastY, MutableDouble wid, MutableDouble hei)
		{
			PrimitiveNode np = mv.via;
			Orientation orient = Orientation.fromJava(mv.orientation * 10, false, false);
			SizeOffset so = np.getProtoSizeOffset();
			double minWid = minWidth;
			double minHei = minWidth;
			double xOffset = so.getLowXOffset() + so.getHighXOffset();
			double yOffset = so.getLowYOffset() + so.getHighYOffset();
			double conWid = Math.max(np.getDefWidth(ep) - xOffset, minWid) + xOffset;
			double conHei = Math.max(np.getDefHeight(ep) - yOffset, minHei) + yOffset;
			if (mv.horMetal >= 0)
			{
				double arcWid = getArcWidth(mv.horMetal, x, y, lastX, lastY) + mv.horMetalInset;
				if (arcWid > conHei) conHei = arcWid;
			}
			if (mv.verMetal >= 0)
			{
				double arcWid = getArcWidth(mv.verMetal, x, y, lastX, lastY) + mv.verMetalInset;
				if (arcWid > conWid) conWid = arcWid;
			}
			wid.setValue(conWid);
			hei.setValue(conHei);
			return orient;
		}

		/**
		 * Method to tell whether an endpoint of the route is invalid.
		 * @param endA true if the A end is being examined, false for the B end.
		 * @param pi the port of the end being examined.
		 * @return true if the port is invalid and cannot be routed (displays error message if so).
		 */
		private boolean invalidPort(boolean endA, boolean tap, PortInst pi)
		{
			if (MULTIPLEDESTINATIONS)
				return invalidPortNEW(endA, tap, pi);
			return invalidPortOLD(endA, tap, pi);
		}

		/**
		 * Method to tell whether an endpoint of the route is invalid.
		 * @param endA true if the A end is being examined, false for the B end.
		 * @param pi the port of the end being examined.
		 * @return true if the port is invalid and cannot be routed (displays error message if so).
		 */
		private boolean invalidPortNEW(boolean endA, boolean tap, PortInst pi)
		{
			EPoint pt = pi.getCenter();
			double conX = pt.getX(), conY = pt.getY();

			// first see if any metal layer on this end is available
			ArcProto[] conns = getPossibleConnections(pi.getPortProto());
			for (int j = 0; j < conns.length; j++)
			{
				ArcProto ap = conns[j];
				if (ap.getTechnology() != tech) continue;
				if (!ap.getFunction().isMetal()) continue;
				if (preventArc(conns[j].getFunction().getLevel() - 1)) continue;
				return false;
			}

			// determine location of contact
			Map<Integer,Map<PrimitiveNode,ContactPlacementError>> whatWasChecked = new HashMap<Integer,Map<PrimitiveNode,ContactPlacementError>>();
			Map<Integer,java.awt.Point> contactInvalidColors = new HashMap<Integer,java.awt.Point>();
			int realOffendingMetal = 0;
			Boolean end = tap ? null : Boolean.valueOf(endA);

			// no metal layers available: see if a contact can be placed to shift to a valid layer
			if (sogp.isContactAllowedDownToAvoidedLayer() || sogp.isContactAllowedUpToAvoidedLayer())
			{
				// list of possible places for a via
				PossibleEndpoints possibleVias = endA ? aEndpoints : bEndpoints;

				// first see if an adjoining layer is available
				for (int j = 0; j < conns.length; j++)
				{
					ArcProto ap = conns[j];
					if (ap.getTechnology() != tech) continue;
					if (!ap.getFunction().isMetal()) continue;
					int offendingMetal = conns[j].getFunction().getLevel() - 1;
					int offendingMetalColor = conns[j].getMaskLayer();
					int newMetal = 0;
					int newMetalColor = 0;
					int lowerMetal = offendingMetal - 1;
					int upperMetal = offendingMetal + 1;
					List<MetalVia> mvs = null;
					if (sogp.isContactAllowedUpToAvoidedLayer() && lowerMetal >= 0 && !preventArc(lowerMetal))
					{
						// can drop down one metal layer to connect
						mvs = metalVias[lowerMetal].getVias();
						newMetal = lowerMetal;
					}
					if (sogp.isContactAllowedDownToAvoidedLayer() && upperMetal < numMetalLayers && !preventArc(upperMetal))
					{
						// can move up one metal layer to connect
						mvs = metalVias[upperMetal-1].getVias();
						newMetal = upperMetal;
					}
					if (mvs == null) continue;

					// determine horizontal/vertical orientation
					boolean hor = true;
					if (sogp.isHorizontalEven())
					{
						if ((newMetal%2) == 0) hor = false;
					} else
					{
						if ((newMetal%2) != 0) hor = false;
					}
					if (hor)
					{
						SeaOfGatesTrack sogt = getClosestYGrid(newMetal, conY);
						if (sogt != null) newMetalColor = sogt.getMaskNum();
					} else
					{
						SeaOfGatesTrack sogt = getClosestXGrid(newMetal, conX);
						if (sogt != null) newMetalColor = sogt.getMaskNum();
					}

					// see if any contacts can be placed
					Map<PrimitiveNode,ContactPlacementError> checkedThese = whatWasChecked.get(Integer.valueOf(newMetal));
					if (checkedThese == null) whatWasChecked.put(Integer.valueOf(newMetal), checkedThese = new HashMap<PrimitiveNode,ContactPlacementError>());
					boolean hasContacts = false;
					for (MetalVia mv : mvs)
					{
						if (mv.horMetal == offendingMetal && mv.verMetal == newMetal)
						{
							if (mv.horMetalColor != offendingMetalColor || mv.verMetalColor != newMetalColor) continue;
						} else if (mv.verMetal == offendingMetal && mv.horMetal == newMetal)
						{
							if (mv.verMetalColor != offendingMetalColor || mv.horMetalColor != newMetalColor) continue;
						}
						hasContacts = true;
					}
					if (!hasContacts)
					{
						realOffendingMetal = offendingMetal;
						contactInvalidColors.put(Integer.valueOf(newMetal), new java.awt.Point(offendingMetalColor, newMetalColor));
					}

					// adjust location of contact to be on grid
					if (forceGridArcs[newMetal])
					{
						// determine starting index in the array of possible grid locations
						int index = 0;
						if (hor)
						{
							if (gridLocationsY[newMetal] != null)
							{
								conY = getClosestYGrid(newMetal, conY).getCoordinate();
								for(index=0; index<gridLocationsY[newMetal].length; index++)
									if (gridLocationsY[newMetal][index].getCoordinate() == conY) break;
							}
						} else
						{
							if (gridLocationsX[newMetal] != null)
							{
								conX = getClosestXGrid(newMetal, conX).getCoordinate();
								for(index=0; index<gridLocationsX[newMetal].length; index++)
									if (gridLocationsX[newMetal][index].getCoordinate() == conX) break;
							}
						}

						// try all values on the grid, heading toward goal
						int startIndex = index;

						for(;;)
						{
							if (hor)
							{
								if (index < 0 || index >= gridLocationsY[newMetal].length) break;
								SeaOfGatesTrack sogt = gridLocationsY[newMetal][index];
								conY = sogt.getCoordinate();
								newMetalColor = sogt.getMaskNum();
								if (conY < routeBounds.getMinY() || conY > routeBounds.getMaxY()) break;
							} else
							{
								if (index < 0 || index >= gridLocationsX[newMetal].length) break;
								SeaOfGatesTrack sogt = gridLocationsX[newMetal][index];
								conX = sogt.getCoordinate();
								newMetalColor = sogt.getMaskNum();
								if (conX < routeBounds.getMinX() || conX > routeBounds.getMaxX()) break;
							}

							// gather possible contacts at this location
							boolean foundLocation = false;
							for (MetalVia mv : mvs)
							{
								if (mv.horMetal == offendingMetal && mv.verMetal == newMetal)
								{
									if (mv.horMetalColor != offendingMetalColor || mv.verMetalColor != newMetalColor) continue;
								} else if (mv.verMetal == offendingMetal && mv.horMetal == newMetal)
								{
									if (mv.verMetalColor != offendingMetalColor || mv.horMetalColor != newMetalColor) continue;
								}
								PrimitiveNode np = mv.via;
								MutableDouble conWid, conHei;
								Orientation orient = getMVSize(mv, conX, conY, conX, conY, conWid = new MutableDouble(0), conHei = new MutableDouble(0));
								ContactPlacementError cpe = canPlaceContact(np, newMetal, offendingMetal, offendingMetalColor, conX, conY,
									conWid.doubleValue(), conHei.doubleValue(), orient, end);
								if (cpe != null)
								{
									if (checkedThese.get(np) == null) checkedThese.put(np, cpe);
								} else
								{
									// offending layer is already there, so via can be dropped from it
									PossibleEndpoint vtvl = new PossibleEndpoint(EPoint.fromLambda(conX, conY), mv,
										conWid.doubleValue(), conHei.doubleValue(), orient);
									possibleVias.add(vtvl);
									foundLocation = true;
								}
							}

							// if nothing possible and already checked vicinity of endpoint, stop
							if (!foundLocation && Math.abs(index - startIndex) > 5) break;

							// advance to next possible location
							index++;
						}

						// now head in the other direction
						index = startIndex-1;
						for(;;)
						{
							if (hor)
							{
								if (index < 0 || index >= gridLocationsY[newMetal].length) break;
								SeaOfGatesTrack sogt = gridLocationsY[newMetal][index];
								conY = sogt.getCoordinate();
								newMetalColor = sogt.getMaskNum();
								if (conY < routeBounds.getMinY() || conY > routeBounds.getMaxY()) break;
							} else
							{
								if (index < 0 || index >= gridLocationsX[newMetal].length) break;
								SeaOfGatesTrack sogt = gridLocationsX[newMetal][index];
								conX = sogt.getCoordinate();
								newMetalColor = sogt.getMaskNum();
								if (conX < routeBounds.getMinX() || conX > routeBounds.getMaxX()) break;
							}

							// gather possible contacts at this location
							boolean foundLocation = false;
							for (MetalVia mv : mvs)
							{
								if (mv.horMetal == offendingMetal && mv.verMetal == newMetal)
								{
									if (mv.horMetalColor != offendingMetalColor || mv.verMetalColor != newMetalColor) continue;
								} else if (mv.verMetal == offendingMetal && mv.horMetal == newMetal)
								{
									if (mv.verMetalColor != offendingMetalColor || mv.horMetalColor != newMetalColor) continue;
								}
								PrimitiveNode np = mv.via;
								MutableDouble conWid, conHei;
								Orientation orient = getMVSize(mv, conX, conY, conX, conY, conWid = new MutableDouble(0), conHei = new MutableDouble(0));
								ContactPlacementError cpe = canPlaceContact(np, newMetal, offendingMetal, offendingMetalColor, conX, conY,
									conWid.doubleValue(), conHei.doubleValue(), orient, end);
								if (cpe != null)
								{
									if (checkedThese.get(np) == null) checkedThese.put(np, cpe);
								} else
								{
									// offending layer is already there, so via can be dropped from it
									PossibleEndpoint vtvl = new PossibleEndpoint(EPoint.fromLambda(conX, conY), mv,
										conWid.doubleValue(), conHei.doubleValue(), orient);
									possibleVias.add(vtvl);
									foundLocation = true;
								}
							}

							// if nothing possible and already checked vicinity of endpoint, stop
							if (!foundLocation && Math.abs(index - startIndex) > 5) break;

							// advance to next possible location
							index--;
						}
					} else
					{
						// no forced gridding: just find a via at this coordinate
						for (MetalVia mv : mvs)
						{
							if (mv.horMetal == offendingMetal && mv.verMetal == newMetal)
							{
								if (mv.horMetalColor != offendingMetalColor || mv.verMetalColor != newMetalColor) continue;
							} else if (mv.verMetal == offendingMetal && mv.horMetal == newMetal)
							{
								if (mv.verMetalColor != offendingMetalColor || mv.horMetalColor != newMetalColor) continue;
							}
							MutableDouble conWid = new MutableDouble(0), conHei = new MutableDouble(0);
							Orientation orient = getMVSize(mv, conX, conY, conX, conY, conWid, conHei);
							ContactPlacementError cpe = canPlaceContact(mv.via, newMetal, offendingMetal, offendingMetalColor, conX, conY,
								conWid.doubleValue(), conHei.doubleValue(), orient, end);
							if (cpe != null) checkedThese.put(mv.via, cpe); else
							{
								// can place via here
								PossibleEndpoint vtvl = new PossibleEndpoint(EPoint.fromLambda(conX, conY), mv,
									conWid.doubleValue(), conHei.doubleValue(), orient);
								possibleVias.add(vtvl);
							}
						}
					}
					if (possibleVias.hasEndpoints())
					{
						EPoint otherEnd;
						if (endA)
						{
							otherEnd = bEndpoints.getCenter();
						} else
						{
							otherEnd = aEndpoints.getCenter();
						}
						double closestDist = Double.MAX_VALUE;
						PossibleEndpoint closestEP = null;
						for(PossibleEndpoint vtvl : possibleVias.getEndpoints())
						{
							double dist = vtvl.coord.distance(otherEnd);
							if (closestEP == null || dist < closestDist)
							{
								closestEP = vtvl;
								closestDist = dist;
							}
						}
						if (endA)
						{
							double dX = closestEP.coord.getX()-aEndpoints.getCenterX(), dY = closestEP.coord.getY()-aEndpoints.getCenterY();
							aEndpoints.setCenterX(closestEP.coord.getX());
							aEndpoints.setCenterY(closestEP.coord.getY());
							Point[] pts = aPoly.getPoints();
							for(int i=0; i<pts.length; i++)
								pts[i].setLocation(pts[i].getX()+dX, pts[i].getY()+dY);
							aEndpoints.setRect(aEndpoints.getCenterX(), aEndpoints.getCenterY(), 0, 0);
							double aLX = getLowerXGrid(aZ, aEndpoints.getRect().getMinX()).getCoordinate();
							double aHX = getUpperXGrid(aZ, aEndpoints.getRect().getMaxX()).getCoordinate();
							double aLY = getLowerYGrid(aZ, aEndpoints.getRect().getMinY()).getCoordinate();
							double aHY = getUpperYGrid(aZ, aEndpoints.getRect().getMaxY()).getCoordinate();
							aEndpoints.setGriddedRect(aLX, aHX, aLY, aHY);
							aZ = newMetal;
							if (forceGridArcs[newMetal])
							{
								if (hor)
								{
									SeaOfGatesTrack sogt = getClosestYGrid(newMetal, aEndpoints.getCenterY());
									if (sogt != null) newMetalColor = sogt.getMaskNum();
								} else
								{
									SeaOfGatesTrack sogt = getClosestXGrid(newMetal, aEndpoints.getCenterX());
									if (sogt != null) newMetalColor = sogt.getMaskNum();
								}
							}
							aC = newMetalColor;
							jumpBound = new Rectangle2D.Double(Math.min(aEndpoints.getCenterX(), bEndpoints.getCenterX()),
								Math.min(aEndpoints.getCenterY(), bEndpoints.getCenterY()),
								Math.abs(aEndpoints.getCenterX()-bEndpoints.getCenterX()),
								Math.abs(aEndpoints.getCenterY()-bEndpoints.getCenterY()));
							replaceA = closestEP;
							replaceAZ = offendingMetal;
							if (offendingMetalColor > 0)
								replaceAC = offendingMetalColor-1;

							// reset taper width on this end since contact will be placed
							aTaperWid = getUntaperedArcWidth(newMetal);
							aTaperLen = -1;
						} else
						{
							double dX = closestEP.coord.getX()-bEndpoints.getCenterX(), dY = closestEP.coord.getY()-bEndpoints.getCenterY();
							bEndpoints.setCenterX(closestEP.coord.getX());
							bEndpoints.setCenterY(closestEP.coord.getY());
							Point[] pts = bPoly.getPoints();
							for(int i=0; i<pts.length; i++)
								pts[i].setLocation(pts[i].getX()+dX, pts[i].getY()+dY);
							bEndpoints.setRect(bEndpoints.getCenterX(), bEndpoints.getCenterY(), 0, 0);
							double bLX = getLowerXGrid(bZ, bEndpoints.getRect().getMinX()).getCoordinate();
							double bHX = getUpperXGrid(bZ, bEndpoints.getRect().getMaxX()).getCoordinate();
							double bLY = getLowerYGrid(bZ, bEndpoints.getRect().getMinY()).getCoordinate();
							double bHY = getUpperYGrid(bZ, bEndpoints.getRect().getMaxY()).getCoordinate();
							bEndpoints.setGriddedRect(bLX, bHX, bLY, bHY);
							bZ = newMetal;
							if (forceGridArcs[newMetal])
							{
								if (hor)
								{
									SeaOfGatesTrack sogt = getClosestYGrid(newMetal, bEndpoints.getCenterY());
									if (sogt != null) newMetalColor = sogt.getMaskNum();
								} else
								{
									SeaOfGatesTrack sogt = getClosestXGrid(newMetal, bEndpoints.getCenterX());
									if (sogt != null) newMetalColor = sogt.getMaskNum();
								}
							}
							bC = newMetalColor;
							jumpBound = new Rectangle2D.Double(Math.min(aEndpoints.getCenterX(), bEndpoints.getCenterX()),
								Math.min(aEndpoints.getCenterY(), bEndpoints.getCenterY()),
								Math.abs(aEndpoints.getCenterX()-bEndpoints.getCenterX()),
								Math.abs(aEndpoints.getCenterY()-bEndpoints.getCenterY()));
							replaceB = closestEP;
							replaceBZ = offendingMetal;
							if (offendingMetalColor > 0)
								replaceBC = offendingMetalColor-1;

							// reset taper width on this end since contact will be placed
							bTaperWid = getUntaperedArcWidth(newMetal);
							bTaperLen = -1;
						}

						// add contact to blockages
						NodeInst dummyNi = NodeInst.makeDummyInstance(closestEP.viaToPlace.via, ep, closestEP.coord,
							closestEP.viaSizeX, closestEP.viaSizeY, closestEP.viaOrient);
						Poly[] conPolys = tech.getShapeOfNode(dummyNi);
						FixpTransform trans = null;
						if (closestEP.viaOrient != Orientation.IDENT) trans = dummyNi.rotateOut();
						for (int p = 0; p < conPolys.length; p++)
						{
							Poly conPoly = conPolys[p];
							Layer conLayer = conPoly.getLayer();
							if (trans != null) conPoly.transform(trans);
							Rectangle2D conRect = conPoly.getBounds2D();
							Layer.Function fun = conLayer.getFunction();
							if (fun.isMetal())
							{
								addRectangle(conRect, conLayer, netID, false, false);
							} else if (fun.isContact())
							{
								addVia(ERectangle.fromLambda(conRect), conLayer, netID, false);
							}
						}
						possibleVias.finishedAddingEndpoints();
						return false;
					}
				}
			}

			// cannot place contact or use existing layers, give an error
			String msg = "Route '" + routeName +  "' at (" + TextUtils.formatDistance(pt.getX()) + "," + TextUtils.formatDistance(pt.getY()) +
				") from port " + pi.getPortProto().getName() + " on node " + describe(pi.getNodeInst()) + " cannot connect";
			if (sogp.isContactAllowedDownToAvoidedLayer() || sogp.isContactAllowedUpToAvoidedLayer())
			{
				msg += " (allowed to go";
				if (sogp.isContactAllowedDownToAvoidedLayer())
				{
					if (sogp.isContactAllowedUpToAvoidedLayer()) msg += " up/down"; else msg += " down";
				} else if (sogp.isContactAllowedUpToAvoidedLayer()) msg += " up";
				msg += " one layer)";
			}

			if (whatWasChecked.size() > 0)
			{
				boolean first = true;
				for(Integer m : whatWasChecked.keySet())
				{
					if (first) msg += " because"; else msg += " and";
					first = false;
					java.awt.Point invalidColors = contactInvalidColors.get(m);
					if (invalidColors != null)
					{
						msg += " no contact exists from metal-" + (realOffendingMetal+1) + " mask color " + invalidColors.x +
							" to metal-" + (m.intValue() + 1) + " mask color " + invalidColors.y;
					} else
					{
						msg += " contact(s) cannot be placed:";
						Map<PrimitiveNode,ContactPlacementError> contacts = whatWasChecked.get(m);
						boolean firstPrim = true;
						for(PrimitiveNode pNp : contacts.keySet())
						{
							ContactPlacementError cpe = contacts.get(pNp);
							if (firstPrim) msg += " "; else msg += " / ";
							firstPrim = false;
							msg += cpe.getError(pNp);
						}
					}
				}
			} else
			{
				 msg += " because all connecting layers have been prevented by Routing Preferences";
			}
			error(msg);
			List<PolyBase> polyList = new ArrayList<PolyBase>();
			polyList.add(new PolyBase(pt.getX(), pt.getY(), 0, 0));
			errorLogger.logMessageWithLines(msg, polyList, null, cell, 0, true);
			return true;
		}

		/**
		 * Method to tell whether an endpoint of the route is invalid.
		 * @param endA true if the A end is being examined, false for the B end.
		 * @param pi the port of the end being examined.
		 * @return false if port is valid and can be routed.
		 * True if the endpoint is invalid and cannot be routed (displays error message if so).
		 */
		private boolean invalidPortOLD(boolean endA, boolean tap, PortInst pi)
		{
			EPoint pt = pi.getCenter();
			double conX = pt.getX(), conY = pt.getY();

			// first see if any metal layer on this end is available
			ArcProto[] conns = getPossibleConnections(pi.getPortProto());
			for (int j = 0; j < conns.length; j++)
			{
				ArcProto ap = conns[j];
				if (ap.getTechnology() != tech) continue;
				if (!ap.getFunction().isMetal()) continue;
				if (preventArc(conns[j].getFunction().getLevel() - 1)) continue;
				return false;
			}

			// determine location of contact
			Map<Integer,Map<PrimitiveNode,ContactPlacementError>> whatWasChecked = new HashMap<Integer,Map<PrimitiveNode,ContactPlacementError>>();
			Map<Integer,java.awt.Point> contactInvalidColors = new HashMap<Integer,java.awt.Point>();
			int realOffendingMetal = 0;
			Boolean end = tap ? null : Boolean.valueOf(endA);

			// no metal layers available: see if a contact can be placed to shift to a valid layer
			double endCoord = 0;
			if (sogp.isContactAllowedDownToAvoidedLayer() || sogp.isContactAllowedUpToAvoidedLayer())
			{
				// first see if an adjoining layer is available
				for (int j = 0; j < conns.length; j++)
				{
					ArcProto ap = conns[j];
					if (ap.getTechnology() != tech) continue;
					if (!ap.getFunction().isMetal()) continue;
					int offendingMetal = conns[j].getFunction().getLevel() - 1;
					int offendingMetalColor = conns[j].getMaskLayer();
					int newMetal = 0;
					int newMetalColor = 0;
					int lowerMetal = offendingMetal - 1;
					int upperMetal = offendingMetal + 1;
					List<MetalVia> mvs = null;
					if (sogp.isContactAllowedUpToAvoidedLayer() && lowerMetal >= 0 && !preventArc(lowerMetal))
					{
						// can drop down one metal layer to connect
						mvs = metalVias[lowerMetal].getVias();
						newMetal = lowerMetal;
					}
					if (sogp.isContactAllowedDownToAvoidedLayer() && upperMetal < numMetalLayers && !preventArc(upperMetal))
					{
						// can move up one metal layer to connect
						mvs = metalVias[upperMetal-1].getVias();
						newMetal = upperMetal;
					}
					if (mvs == null) continue;

					// determine horizontal/vertical orientation
					boolean hor = true;
					if (sogp.isHorizontalEven())
					{
						if ((newMetal%2) == 0) hor = false;
					} else
					{
						if ((newMetal%2) != 0) hor = false;
					}
					SeaOfGatesTrack sogt;
					if (hor)
					{
						sogt = getClosestYGrid(newMetal, conY);
					} else
					{
						sogt = getClosestXGrid(newMetal, conX);
					}
					if (sogt != null)
						newMetalColor = sogt.getMaskNum();

					// see if any contacts can be placed
					Map<PrimitiveNode,ContactPlacementError> checkedThese = whatWasChecked.get(Integer.valueOf(newMetal));
					if (checkedThese == null) whatWasChecked.put(Integer.valueOf(newMetal), checkedThese = new HashMap<PrimitiveNode,ContactPlacementError>());
					boolean hasContacts = false;
					for (MetalVia mv : mvs)
					{
						if (mv.horMetal == offendingMetal && mv.verMetal == newMetal)
						{
							if (mv.horMetalColor != offendingMetalColor || mv.verMetalColor != newMetalColor) continue;
						} else if (mv.verMetal == offendingMetal && mv.horMetal == newMetal)
						{
							if (mv.verMetalColor != offendingMetalColor || mv.horMetalColor != newMetalColor) continue;
						}
						hasContacts = true;
					}
					if (!hasContacts)
					{
						realOffendingMetal = offendingMetal;
						contactInvalidColors.put(Integer.valueOf(newMetal), new java.awt.Point(offendingMetalColor, newMetalColor));
					}
					MetalVia viaToPlace = null;
					double viaToPlaceX = 0, viaToPlaceY = 0;
					double viaSizeX = 0, viaSizeY = 0;
					Orientation viaOrient = Orientation.IDENT;

					// adjust location of contact to be on grid
					if (forceGridArcs[newMetal])
					{
						int index = 0, dir = 0;
						if (hor)
						{
							if (gridLocationsY[newMetal] != null)
							{
								double otherY = endA ? bEndpoints.getCenterY() : aEndpoints.getCenterY();
								if (otherY < conY)
								{
									sogt = getLowerYGrid(newMetal, conY);
									dir = -1;
								} else
								{
									sogt = getUpperYGrid(newMetal, conY);
									dir = 1;
								}
								conY = sogt.getCoordinate();
								newMetalColor = sogt.getMaskNum();
								for(index=0; index<gridLocationsY[newMetal].length; index++)
									if (gridLocationsY[newMetal][index].getCoordinate() == conY) break;
							}
						} else
						{
							if (gridLocationsX[newMetal] != null)
							{
								double otherX = endA ? bEndpoints.getCenterX() : aEndpoints.getCenterX();
								if (otherX < conX)
								{
									sogt = getLowerXGrid(newMetal, conX);
									dir = -1;
								} else
								{
									sogt = getUpperXGrid(newMetal, conX);
									dir = 1;
								}
								conX = sogt.getCoordinate();
								newMetalColor = sogt.getMaskNum();
								for(index=0; index<gridLocationsX[newMetal].length; index++)
									if (gridLocationsX[newMetal][index].getCoordinate() == conX) break;
							}
						}

						// try all values on the grid, heading toward goal
						int startIndex = index;

						// code to travel as far as possible rather than stopping at first valid location
						if (hor)
						{
							endCoord = endA ? bEndpoints.getCenterY() : aEndpoints.getCenterY();
						} else
						{
							endCoord = endA ? bEndpoints.getCenterX() : aEndpoints.getCenterX();
						}

						for(;;)
						{
							// more code to travel as far as possible rather than stopping at first valid location
							boolean tooFar = false;
							if (hor)
							{
								if (dir < 0)
								{
									if (conY < endCoord) tooFar = true;
								} else
								{
									if (conY > endCoord) tooFar = true;
								}
							} else
							{
								if (dir < 0)
								{
									if (conX < endCoord) tooFar = true;
								} else
								{
									if (conX > endCoord) tooFar = true;
								}
							}
							if (tooFar && viaToPlace != null) break;

							for (MetalVia mv : mvs)
							{
								if (mv.horMetal == offendingMetal && mv.verMetal == newMetal)
								{
									if (mv.horMetalColor != offendingMetalColor || mv.verMetalColor != newMetalColor) continue;
								} else if (mv.verMetal == offendingMetal && mv.horMetal == newMetal)
								{
									if (mv.verMetalColor != offendingMetalColor || mv.horMetalColor != newMetalColor) continue;
								}
								PrimitiveNode np = mv.via;
								MutableDouble conWid, conHei;
								Orientation orient = getMVSize(mv, conX, conY, conX, conY, conWid = new MutableDouble(0), conHei = new MutableDouble(0));
								ContactPlacementError cpe = canPlaceContact(np, newMetal, offendingMetal, offendingMetalColor, conX, conY,
									conWid.doubleValue(), conHei.doubleValue(), orient, end);
								if (cpe != null)
								{
									if (checkedThese.get(np) == null) checkedThese.put(np, cpe);
								} else
								{
									// offending layer is already there, so via can be dropped from it
									viaToPlace = mv;
									viaToPlaceX = conX;   viaToPlaceY = conY;
									viaSizeX = conWid.doubleValue(); viaSizeY = conHei.doubleValue();
									viaOrient = orient;
									break;
								}
							}

							index += dir;
							if (hor)
							{
								if (index < 0 || index >= gridLocationsY[newMetal].length) break;
								sogt = gridLocationsY[newMetal][index];
								conY = sogt.getCoordinate();
								newMetalColor = sogt.getMaskNum();
							} else
							{
								if (index < 0 || index >= gridLocationsX[newMetal].length) break;
								sogt = gridLocationsX[newMetal][index];
								conX = sogt.getCoordinate();
								newMetalColor = sogt.getMaskNum();
							}
						}
						if (viaToPlace == null)
						{
							// heading away from goal (last resort)
							dir = -dir;
							index = startIndex + dir;
							for(;;)
							{
								if (hor)
								{
									if (index < 0 || index >= gridLocationsY[newMetal].length) break;
									sogt = gridLocationsY[newMetal][index];
									conY = sogt.getCoordinate();
									newMetalColor = sogt.getMaskNum();
								} else
								{
									if (index < 0 || index >= gridLocationsX[newMetal].length) break;
									sogt = gridLocationsX[newMetal][index];
									conX = sogt.getCoordinate();
									newMetalColor = sogt.getMaskNum();
								}
								for (MetalVia mv : mvs)
								{
									if (mv.horMetal == offendingMetal && mv.verMetal == newMetal)
									{
										if (mv.horMetalColor != offendingMetalColor || mv.verMetalColor != newMetalColor) continue;
									} else if (mv.verMetal == offendingMetal && mv.horMetal == newMetal)
									{
										if (mv.verMetalColor != offendingMetalColor || mv.horMetalColor != newMetalColor) continue;
									}
									PrimitiveNode np = mv.via;
									MutableDouble conWid, conHei;
									Orientation orient = getMVSize(mv, conX, conY, conX, conY, conWid = new MutableDouble(0), conHei = new MutableDouble(0));
									ContactPlacementError cpe = canPlaceContact(np, newMetal, offendingMetal, offendingMetalColor, conX, conY,
										conWid.doubleValue(), conHei.doubleValue(), orient, end);
									if (cpe != null)
									{
										if (checkedThese.get(np) == null) checkedThese.put(np, cpe);
									} else
									{
										// offending layer is already there, so via can be dropped from it
										viaToPlace = mv;
										viaToPlaceX = conX;   viaToPlaceY = conY;
										viaSizeX = conWid.doubleValue(); viaSizeY = conHei.doubleValue();
										viaOrient = orient;
										break;
									}
								}
								if (viaToPlace != null) break;

								index += dir;
							}
						}
					} else
					{
						// no forced gridding: just find a via at this coordinate
						for (MetalVia mv : mvs)
						{
							if (mv.horMetal == offendingMetal && mv.verMetal == newMetal)
							{
								if (mv.horMetalColor != offendingMetalColor || mv.verMetalColor != newMetalColor) continue;
							} else if (mv.verMetal == offendingMetal && mv.horMetal == newMetal)
							{
								if (mv.verMetalColor != offendingMetalColor || mv.horMetalColor != newMetalColor) continue;
							}
							PrimitiveNode np = mv.via;
							MutableDouble conWid, conHei;
							Orientation orient = getMVSize(mv, conX, conY, conX, conY, conWid = new MutableDouble(0), conHei = new MutableDouble(0));
							ContactPlacementError cpe = canPlaceContact(np, newMetal, offendingMetal, offendingMetalColor, conX, conY,
								conWid.doubleValue(), conHei.doubleValue(), orient, end);
							if (cpe != null)
							{
								checkedThese.put(np, cpe);
							} else
							{
								// offending layer is already there, so via can be dropped from it
								viaToPlace = mv;
								viaToPlaceX = conX;   viaToPlaceY = conY;
								viaSizeX = conWid.doubleValue(); viaSizeY = conHei.doubleValue();
								viaOrient = orient;
								break;
							}
						}
					}
					if (viaToPlace != null)
					{
						String msg = "Route '" + routeName + "' at (" + TextUtils.formatDistance(pt.getX()) + "," +
							TextUtils.formatDistance(pt.getY()) + ") from port " + pi.getPortProto().getName() + " on node " +
							describe(pi.getNodeInst()) + " is disallowed on Metal " + (offendingMetal+1) + " so inserting " +
							viaToPlace.via.describe(false);
						if (!DBMath.areEquals(pt.getX(), viaToPlaceX) || !DBMath.areEquals(pt.getY(), viaToPlaceY))
								msg += " at (" + TextUtils.formatDistance(viaToPlaceX) + "," + TextUtils.formatDistance(viaToPlaceY) + ")";
						msg += " and routing from Metal " + (newMetal+1);
						warn(msg);
						if (endA)
						{
							double dX = viaToPlaceX-aEndpoints.getCenterX(), dY = viaToPlaceY-aEndpoints.getCenterY();
							aEndpoints.setCenterX(viaToPlaceX);
							aEndpoints.setCenterY(viaToPlaceY);
							Point[] pts = aPoly.getPoints();
							for(int i=0; i<pts.length; i++)
								pts[i].setLocation(pts[i].getX()+dX, pts[i].getY()+dY);
							aEndpoints.setRect(aEndpoints.getCenterX(), aEndpoints.getCenterY(), 0, 0);
							double aLX = getLowerXGrid(aZ, aEndpoints.getRect().getMinX()).getCoordinate();
							double aHX = getUpperXGrid(aZ, aEndpoints.getRect().getMaxX()).getCoordinate();
							double aLY = getLowerYGrid(aZ, aEndpoints.getRect().getMinY()).getCoordinate();
							double aHY = getUpperYGrid(aZ, aEndpoints.getRect().getMaxY()).getCoordinate();
							aEndpoints.setGriddedRect(aLX, aHX, aLY, aHY);
							aZ = newMetal;
							if (forceGridArcs[newMetal])
							{
								if (hor)
								{
									sogt = getClosestYGrid(newMetal, aEndpoints.getCenterY());
								} else
								{
									sogt = getClosestXGrid(newMetal, aEndpoints.getCenterX());
								}
								if (sogt != null) newMetalColor = sogt.getMaskNum();
							}
							aC = newMetalColor;
							jumpBound = new Rectangle2D.Double(Math.min(aEndpoints.getCenterX(), bEndpoints.getCenterX()),
								Math.min(aEndpoints.getCenterY(), bEndpoints.getCenterY()),
								Math.abs(aEndpoints.getCenterX()-bEndpoints.getCenterX()),
								Math.abs(aEndpoints.getCenterY()-bEndpoints.getCenterY()));
							replaceA = new PossibleEndpoint(aEndpoints.getCenter(), viaToPlace, viaSizeX, viaSizeY, viaOrient);
							replaceAZ = offendingMetal;
							if (offendingMetalColor > 0)
								replaceAC = offendingMetalColor-1;

							// reset taper width on this end since contact will be placed
							aTaperWid = getUntaperedArcWidth(newMetal);
							aTaperLen = -1;
						} else
						{
							double dX = viaToPlaceX-bEndpoints.getCenterX(), dY = viaToPlaceY-bEndpoints.getCenterY();
							bEndpoints.setCenterX(viaToPlaceX);
							bEndpoints.setCenterY(viaToPlaceY);
							Point[] pts = bPoly.getPoints();
							for(int i=0; i<pts.length; i++)
								pts[i].setLocation(pts[i].getX()+dX, pts[i].getY()+dY);
							bEndpoints.setRect(bEndpoints.getCenterX(), bEndpoints.getCenterY(), 0, 0);
							double bLX = getLowerXGrid(bZ, bEndpoints.getRect().getMinX()).getCoordinate();
							double bHX = getUpperXGrid(bZ, bEndpoints.getRect().getMaxX()).getCoordinate();
							double bLY = getLowerYGrid(bZ, bEndpoints.getRect().getMinY()).getCoordinate();
							double bHY = getUpperYGrid(bZ, bEndpoints.getRect().getMaxY()).getCoordinate();
							bEndpoints.setGriddedRect(bLX, bHX, bLY, bHY);
							bZ = newMetal;
							if (forceGridArcs[newMetal])
							{
								if (hor)
								{
									sogt = getClosestYGrid(newMetal, bEndpoints.getCenterY());
								} else
								{
									sogt = getClosestXGrid(newMetal, bEndpoints.getCenterX());
								}
								if (sogt != null) newMetalColor = sogt.getMaskNum();
							}
							bC = newMetalColor;
							jumpBound = new Rectangle2D.Double(Math.min(aEndpoints.getCenterX(), bEndpoints.getCenterX()),
								Math.min(aEndpoints.getCenterY(), bEndpoints.getCenterY()),
								Math.abs(aEndpoints.getCenterX()-bEndpoints.getCenterX()),
								Math.abs(aEndpoints.getCenterY()-bEndpoints.getCenterY()));
							replaceB = new PossibleEndpoint(bEndpoints.getCenter(), viaToPlace, viaSizeX, viaSizeY, viaOrient);
							replaceBZ = offendingMetal;
							if (offendingMetalColor > 0)
								replaceBC = offendingMetalColor-1;

							// reset taper width on this end since contact will be placed
							bTaperWid = getUntaperedArcWidth(newMetal);
							bTaperLen = -1;
						}

						// add contact to blockages
						NodeInst dummyNi = NodeInst.makeDummyInstance(viaToPlace.via, ep, EPoint.fromLambda(viaToPlaceX, viaToPlaceY),
							viaSizeX, viaSizeY, viaOrient);
						Poly[] conPolys = tech.getShapeOfNode(dummyNi);
						FixpTransform trans = null;
						if (viaOrient != Orientation.IDENT) trans = dummyNi.rotateOut();
						for (int p = 0; p < conPolys.length; p++)
						{
							Poly conPoly = conPolys[p];
							Layer conLayer = conPoly.getLayer();
							if (trans != null) conPoly.transform(trans);
							Rectangle2D conRect = conPoly.getBounds2D();
							Layer.Function fun = conLayer.getFunction();
							if (fun.isMetal())
							{
								addRectangle(conRect, conLayer, netID, false, false);
							} else if (fun.isContact())
							{
								addVia(ERectangle.fromLambda(conRect), conLayer, netID, false);
							}
						}
						return false;
					}
				}
			}

			// cannot place contact or use existing layers, give an error
			String msg = "Route '" + routeName +  "' at (" + TextUtils.formatDistance(pt.getX()) + "," + TextUtils.formatDistance(pt.getY()) +
				") from port " + pi.getPortProto().getName() + " on node " + describe(pi.getNodeInst()) + " cannot connect";
			if (sogp.isContactAllowedDownToAvoidedLayer() || sogp.isContactAllowedUpToAvoidedLayer())
			{
				msg += " (allowed to go";
				if (sogp.isContactAllowedDownToAvoidedLayer())
				{
					if (sogp.isContactAllowedUpToAvoidedLayer()) msg += " up/down"; else msg += " down";
				} else if (sogp.isContactAllowedUpToAvoidedLayer()) msg += " up";
				msg += " one layer)";
			}

			if (whatWasChecked.size() > 0)
			{
				boolean first = true;
				for(Integer m : whatWasChecked.keySet())
				{
					if (first) msg += " because"; else msg += " and";
					first = false;
					java.awt.Point invalidColors = contactInvalidColors.get(m);
					if (invalidColors != null)
					{
						msg += " no contact exists from metal-" + (realOffendingMetal+1) + " mask color " + invalidColors.x +
							" to metal-" + (m.intValue() + 1) + " mask color " + invalidColors.y;
					} else
					{
						msg += " contact(s) cannot be placed:";
						Map<PrimitiveNode,ContactPlacementError> contacts = whatWasChecked.get(m);
						boolean firstPrim = true;
						for(PrimitiveNode pNp : contacts.keySet())
						{
							ContactPlacementError cpe = contacts.get(pNp);
							if (firstPrim) msg += " "; else msg += " / ";
							firstPrim = false;
							msg += cpe.getError(pNp);
						}
					}
				}
			} else
			{
				 msg += " because all connecting layers have been prevented by Routing Preferences";
			}
			error(msg);
			List<PolyBase> polyList = new ArrayList<PolyBase>();
			polyList.add(new PolyBase(pt.getX(), pt.getY(), 0, 0));
			errorLogger.logMessageWithLines(msg, polyList, null, cell, 0, true);
			return true;
		}

		/**
		 * Method to determine the width of tapers.
		 * Tapers are as wide as the metal at the end of the route.
		 * @param pi the PortInst at the end of the route.
		 * @param metalNo the metal layer (0-based) at the end of the route.
		 * @return the taper width to use for that end of the route.
		 */
		private double getTaperWidth(PortInst pi, int metalNo)
		{
			PortProto pp = pi.getPortProto();
			NodeInst ni = pi.getNodeInst();
			FixpTransform trans = ni.rotateOut();
			while (ni.isCellInstance())
			{
				Export e = (Export)pp;
				ni = e.getOriginalPort().getNodeInst();
				pp = e.getOriginalPort().getPortProto();
				trans.concatenate(ni.rotateOut());
			}
			Poly[] polys = ni.getProto().getTechnology().getShapeOfNode(ni);
			for(int i=0; i<polys.length; i++)
			{
				Poly poly = polys[i];
				boolean found = false;
				for(int j=0; j<metalLayers[metalNo].length; j++)
					if (metalLayers[metalNo][j] == poly.getLayer()) { found = true;  break; }
				if (!found) continue;
				poly.transform(trans);
				FixpRectangle rect = poly.getBounds2D();
				boolean hor = true;
				if (sogp.isForceHorVer())
				{
					if (sogp.isHorizontalEven())
					{
						if ((metalNo%2) == 0) hor = false;
					} else
					{
						if ((metalNo%2) != 0) hor = false;
					}
					if (hor) return rect.getHeight();
					return rect.getWidth();
				} else
				{
					// choose narrowest dimension for taper width
					return Math.min(rect.getWidth(), rect.getHeight());
				}
			}
			return getUntaperedArcWidth(metalNo);
		}

		public void makeWavefronts()
		{
			// make two wavefronts going in both directions
			dirAtoB = new Wavefront(this, aPi, aEndpoints.getRect(), aEndpoints.getCenterX(), aEndpoints.getCenterY(), aZ, aC, aTaperLen, BLOCKAGEENDA,
				bPi, bEndpoints, bZ, bC, bTaperLen, 1, true, debuggingRouteFromA != null && debuggingRouteFromA.booleanValue());
			dirBtoA = new Wavefront(this, bPi, bEndpoints.getRect(), bEndpoints.getCenterX(), bEndpoints.getCenterY(), bZ, bC, bTaperLen, BLOCKAGEENDB,
				aPi, aEndpoints, aZ, aC, aTaperLen, -1, false, debuggingRouteFromA != null && !debuggingRouteFromA.booleanValue());

//			if (ANYPOINTONDESTINATION)
//			{
//				// mark the blockages with the two ends of the route
//				growPoint(aX, aY, aZ, netID+BLOCKAGEENDA);
//				growPoint(bX, bY, bZ, netID+BLOCKAGEENDB);
//			}
		}

		public void setBatchInfo(RouteBatch batch, int routeInBatch)
		{
			this.batch = batch;
			this.routeInBatch = routeInBatch;
		}

		public int getNumInBatch() { return batch.routesInBatch.size(); }

		public int getRouteInBatch() { return routeInBatch; }

		public MutableInteger getNetID() { return netID; }

		public void setNetID(MutableInteger id) { netID = id; }

		private void setNetID(Network net)
		{
			Integer netIDI = netIDs.get(net);
			assert netIDI != null;
			netID = new MutableInteger(netIDI.intValue());

			// keep track of all MutableIntegers by netID value
			List<MutableInteger> theseNetIDs = netIDsByValue.get(netIDI);
			if (theseNetIDs == null) netIDsByValue.put(netIDI, theseNetIDs = new ArrayList<MutableInteger>());
			theseNetIDs.add(netID);
		}

		/**
		 * Method to determine whether a given layer of metal runs on 2X width wires or not.
		 * @param metNum the 0-based layer of metal.
		 * @return true if the layer of metal runs on 2X width wires.
		 */
		public boolean is2X(int metNum, double fX, double fY, double tX, double tY)
		{
			if (size2X[metNum] != 0)
			{
				double wid = getArcWidth(metNum, fX, fY, tX, tY);
				if (wid >= size2X[metNum]) return true;
			}
			return false;
		}

		/**
		 * Method to determine the width to use for an arc.
		 * @param metNum the metal number of the arc.
		 * @param fX X coordinate of one point on the arc.
		 * @param fY Y coordinate of one point on the arc.
		 * @param tX X coordinate of another point on the arc.
		 * @param tY Y coordinate of another point on the arc.
		 * @return the width to use for that arc.
		 */
		public double getArcWidth(int metNum, double fX, double fY, double tX, double tY)
		{
			// "hor" is null if arc can run either way
			Boolean hor = null;
			if (sogp.isForceHorVer())
			{
				hor = Boolean.TRUE;
				if (sogp.isHorizontalEven())
				{
					if ((metNum%2) == 0) hor = Boolean.FALSE;
				} else
				{
					if ((metNum%2) != 0) hor = Boolean.FALSE;
				}
			}

			// see if either point could be a taper from the "a" end
			boolean foundATaper = false, foundBTaper = false;
			if (aZ == metNum)
			{
				if (hor == null || hor.booleanValue())
				{
					if (DBMath.areEquals(fY, aEndpoints.getCenterY()) &&
						(aTaperLen < 0 || Math.abs(fX-aEndpoints.getCenterX()) < aTaperLen)) foundATaper = true;
					if (DBMath.areEquals(tY, aEndpoints.getCenterY()) &&
						(aTaperLen < 0 || Math.abs(tX-aEndpoints.getCenterX()) < aTaperLen)) foundATaper = true;
				}
				if (hor == null || !hor.booleanValue())
				{
					if (DBMath.areEquals(fX, aEndpoints.getCenterX()) &&
						(aTaperLen < 0 || Math.abs(fY-aEndpoints.getCenterY()) < aTaperLen)) foundATaper = true;
					if (DBMath.areEquals(tX, aEndpoints.getCenterX()) &&
						(aTaperLen < 0 || Math.abs(tY-aEndpoints.getCenterY()) < aTaperLen)) foundATaper = true;
				}
			}

			// see if either point could be a taper from the "b" end
			if (bZ == metNum)
			{
				if (hor == null || hor.booleanValue())
				{
					if (DBMath.areEquals(fY, bEndpoints.getCenterY()) &&
						(bTaperLen < 0 || Math.abs(fX-bEndpoints.getCenterX()) < bTaperLen)) foundBTaper = true;
					if (DBMath.areEquals(tY, bEndpoints.getCenterY()) &&
						(bTaperLen < 0 || Math.abs(tX-bEndpoints.getCenterX()) < bTaperLen)) foundBTaper = true;
				}
				if (hor == null || !hor.booleanValue())
				{
					if (DBMath.areEquals(fX, bEndpoints.getCenterX()) &&
						(bTaperLen < 0 || Math.abs(fY-bEndpoints.getCenterY()) < bTaperLen)) foundBTaper = true;
					if (DBMath.areEquals(tX, bEndpoints.getCenterX()) &&
						(bTaperLen < 0 || Math.abs(tY-bEndpoints.getCenterY()) < bTaperLen)) foundBTaper = true;
				}
			}
			if (foundATaper && foundBTaper) return Math.max(aTaperWid, bTaperWid);
			if (foundATaper) return aTaperWid;
			if (foundBTaper) return bTaperWid;

			// not a taper: use the default widths
			return getUntaperedArcWidth(metNum);
		}

		public double getUntaperedArcWidth(int metNum)
		{
			if (overrideMetalWidth != null) return overrideMetalWidth[metNum];
			double width = Math.max(maxDefArcWidth[metNum], minWidth);
			return width;
		}

		/**
		 * Method to determine the design rule spacing between two pieces of a given layer.
		 * @param layer the layer index.
		 * @param width the width of one of the pieces (-1 to use default).
		 * @param length the length of one of the pieces (-1 to use default).
		 * @return the design rule spacing (0 if none).
		 */
		public double[] getSpacingRule(int layer, double width, double length)
		{
			// use override spacing if specified
			if (overrideMetalSpacings[layer] != null) return overrideMetalSpacings[layer];

			// use default width if none specified
			if (width < 0) width = maxDefArcWidth[layer];
			if (length < 0) length = 50;

			// see if the rule is cached
			Double wid = Double.valueOf(width);
			Map<Double, double[]> widMap = layerSurround[layer].get(wid);
			if (widMap == null)
			{
				synchronized(layerSurround)
				{
					widMap = layerSurround[layer].get(wid);
					if (widMap == null)
						layerSurround[layer].put(wid, widMap = new HashMap<Double, double[]>());
				}
			}
			Double len = Double.valueOf(length);
			double[] value = new double[2];
			double[] cachedValue = widMap.get(len);
			if (cachedValue != null)
			{
				value[0] = cachedValue[0];
				value[1] = cachedValue[1];
			} else
			{
				// rule not cached: compute it
				value[0] = 0;   value[1] = -1;
				for(int c=0; c<metalLayers[layer].length; c++)
				{
					Layer lay = metalLayers[layer][c];
					DRCTemplate rule = DRC.getSpacingRule(lay, null, lay, null, false, -1, width, length);
					if (rule != null)
					{
						value[0] = Math.max(value[0], rule.getValue(0));
						if (rule.getNumValues() > 1) value[1] = Math.max(value[1], rule.getValue(1));
					}
				}
				if (value[1] < 0) value[1] = value[0];
				widMap.put(len, value);
			}

			// handle overrides
			if (overrideMetalSpacingX != null) value[0] = overrideMetalSpacingX[layer];
			if (overrideMetalSpacingY != null) value[1] = overrideMetalSpacingY[layer];
			return value;
		}

		/**
		 * Method to tell whether a given layer of metal can be used in the route.
		 * @param metNum the metal number (starting at 1).
		 * @return true to disallow use of that metal in the route.
		 */
		public boolean preventArc(int metNum)
		{
			if (overridePreventArcs != null) return overridePreventArcs[metNum];
			return preventArcs[metNum];
		}

		public String getName() { return routeName; }

		/**
		 * Method to return the bounds of this route.
		 * No geometry may be placed outside of this area.
		 * @return the bounds of this route.
		 */
		public Rectangle2D getBounds() { return routeBounds; }

		/**
		 * Method to return the PortInst on end A of this NeededRoute.
		 * @return the PortInst on end A of this NeededRoute.
		 */
		public PortInst getAPort() { return aPi; }

		/**
		 * Method to return the PortInst on end B of this NeededRoute.
		 * @return the PortInst on end B of this NeededRoute.
		 */
		public PortInst getBPort() { return bPi; }

		/**
		 * Method to return the X coordinate of point A of this NeededRoute.
		 * @return the X coordinate of point A of this NeededRoute.
		 */
		public double getAX() { return aEndpoints.getCenterX(); }

		/**
		 * Method to return the Y coordinate of point A of this NeededRoute.
		 * @return the Y coordinate of point A of this NeededRoute.
		 */
		public double getAY() { return aEndpoints.getCenterY(); }

		/**
		 * Method to return the X coordinate of point B of this NeededRoute.
		 * @return the X coordinate of point B of this NeededRoute.
		 */
		public double getBX() { return bEndpoints.getCenterX(); }

		/**
		 * Method to return the Y coordinate of point B of this NeededRoute.
		 * @return the Y coordinate of point B of this NeededRoute.
		 */
		public double getBY() { return bEndpoints.getCenterY(); }

		/**
		 * Method to return an R-Tree of blockages on a given via Layer.
		 * @param lay the via Layer to examine.
		 * @return an RTNode that is the top of the tree of blockages on that Layer.
		 */
		public RTNode<SOGBound> getViaTree(Layer lay) { return rTrees.getViaTree(lay).getRoot(); }

		public Iterator<SOGBound> searchViaTree(Layer lay, Rectangle2D bound) {
			return rTrees.getViaTree(lay).search(bound);
		}

		public Rectangle2D[] getGRBuckets() { return buckets; }

		public boolean checkEndSurround()
		{
			// determine "A" end surround
			double fromMetalSpacing = getArcWidth(aZ, aEndpoints.getCenterX(), aEndpoints.getCenterY(), aEndpoints.getCenterX(), aEndpoints.getCenterY()) / 2;
			double[] fromSurround = getSpacingRule(aZ, maxDefArcWidth[aZ], -1);

			// see if "A" end access is blocked
			SOGBound block = getMetalBlockage(netID, aZ, aC, fromMetalSpacing, fromMetalSpacing, fromSurround, aEndpoints.getCenterX(), aEndpoints.getCenterY());
			if (block != null && !sogp.isGridForced(primaryMetalArc[aZ]))
			{
				// ungridded center location still blocked: see if port has nonzero area and other places in it are free
				Rectangle2D fromRect = aPoly.getBounds2D();
				double stepSize = fromMetalSpacing + Math.max(fromSurround[0], fromSurround[1]);
				if (stepSize > 0 && (fromRect.getWidth() > 0 || fromRect.getHeight() > 0))
				{
					for(double x = fromRect.getMinX(); x <= fromRect.getMaxX(); x += stepSize)
					{
						for(double y = fromRect.getMinY(); y <= fromRect.getMaxY(); y += stepSize)
						{
							SOGBound stepBlock = getMetalBlockage(netID, aZ, aC, fromMetalSpacing, fromMetalSpacing, fromSurround, x, y);
							if (stepBlock == null)
							{
								aEndpoints.setCenterX(x);   aEndpoints.setCenterY(y);
								block = null;
								break;
							}
						}
						if (block == null) break;
					}
				}
				if (block != null)
				{
					String errorMsg = "Cannot route from port " + aPi.getPortProto().getName()
						+ " of node " + describe(aPi.getNodeInst()) + " at ("
						+ TextUtils.formatDistance(aEndpoints.getCenterX()) + "," + TextUtils.formatDistance(aEndpoints.getCenterY())
						+ ") because it is blocked on layer " + describeMetal(aZ,aC)
						+ " [needs " + TextUtils.formatDistance(fromMetalSpacing + Math.max(fromSurround[0], fromSurround[1]))
						+ " all around, blockage is "
						+ TextUtils.formatDistance(block.getBounds().getMinX()) + "<=X<="
						+ TextUtils.formatDistance(block.getBounds().getMaxX()) + " and "
						+ TextUtils.formatDistance(block.getBounds().getMinY()) + "<=Y<="
						+ TextUtils.formatDistance(block.getBounds().getMaxY())
						+ " on net " + block.getNetID() + " but routing net " + netID + "]";
					if (reroute) errorMsg = "(Retry) " + errorMsg;
					error(errorMsg);
					List<PolyBase> polyList = new ArrayList<PolyBase>();
					polyList.add(new PolyBase(aEndpoints.getCenterX(), aEndpoints.getCenterY(), (fromMetalSpacing + fromSurround[0]) * 2,
						(fromMetalSpacing + fromSurround[1]) * 2));
					polyList.add(new PolyBase(block.getBounds()));
					List<EPoint> lineList = new ArrayList<EPoint>();
					lineList.add(EPoint.fromLambda(block.getBounds().getMinX(), block.getBounds().getMinY()));
					lineList.add(EPoint.fromLambda(block.getBounds().getMaxX(), block.getBounds().getMaxY()));
					lineList.add(EPoint.fromLambda(block.getBounds().getMinX(), block.getBounds().getMaxY()));
					lineList.add(EPoint.fromLambda(block.getBounds().getMaxX(), block.getBounds().getMinY()));
					errorLogger.logMessageWithLines(errorMsg, polyList, lineList, cell, 0, true);
					return true;
				}
			}

			// determine "B" end surround
			double toMetalSpacing = getArcWidth(bZ, bEndpoints.getCenterX(), bEndpoints.getCenterY(), bEndpoints.getCenterX(), bEndpoints.getCenterY()) / 2;
			double[] toSurround = getSpacingRule(bZ, maxDefArcWidth[bZ], -1);

			// see if "B" end access is blocked
			block = getMetalBlockage(netID, bZ, bC, toMetalSpacing, toMetalSpacing, toSurround, bEndpoints.getCenterX(), bEndpoints.getCenterY());
			if (block != null && !sogp.isGridForced(primaryMetalArc[bZ]))
			{
				// ungridded center location still blocked: see if port has nonzero area and other places in it are free
				Rectangle2D toRect = bPoly.getBounds2D();
				double stepSize = toMetalSpacing + Math.max(toSurround[0], toSurround[1]);
				if (stepSize > 0 && (toRect.getWidth() > 0 || toRect.getHeight() > 0))
				{
					for(double x = toRect.getMinX(); x <= toRect.getMaxX(); x += stepSize)
					{
						for(double y = toRect.getMinY(); y <= toRect.getMaxY(); y += stepSize)
						{
							SOGBound stepBlock = getMetalBlockage(netID, bZ, bC, toMetalSpacing, toMetalSpacing, toSurround, x, y);
							if (stepBlock == null)
							{
								bEndpoints.setCenterX(x);   bEndpoints.setCenterY(y);
								block = null;
								break;
							}
						}
						if (block == null) break;
					}
				}
				if (block != null)
				{
					String errorMsg = "Cannot route to port " + bPi.getPortProto().getName()
						+ " of node " + describe(bPi.getNodeInst()) + " at ("
						+ TextUtils.formatDistance(bEndpoints.getCenterX()) + "," + TextUtils.formatDistance(bEndpoints.getCenterY())
						+ ") because it is blocked on layer " + describeMetal(bZ,bC)
						+ " [needs " + TextUtils.formatDistance(toMetalSpacing + Math.max(toSurround[0], toSurround[1]))
						+ " all around, blockage is "
						+ TextUtils.formatDistance(block.getBounds().getMinX()) + "<=X<="
						+ TextUtils.formatDistance(block.getBounds().getMaxX()) + " and "
						+ TextUtils.formatDistance(block.getBounds().getMinY()) + "<=Y<="
						+ TextUtils.formatDistance(block.getBounds().getMaxY())
						+ " on net " + block.getNetID() + " but routing net " + netID + "]";
					if (reroute) errorMsg = "(Retry) " + errorMsg;
					error(errorMsg);
					List<PolyBase> polyList = new ArrayList<PolyBase>();
					polyList.add(new PolyBase(bEndpoints.getCenterX(), bEndpoints.getCenterY(), (toMetalSpacing + toSurround[0]) * 2,
						(toMetalSpacing + toSurround[1]) * 2));
					polyList.add(new PolyBase(block.getBounds()));
					List<EPoint> lineList = new ArrayList<EPoint>();
					lineList.add(EPoint.fromLambda(block.getBounds().getMinX(), block.getBounds().getMinY()));
					lineList.add(EPoint.fromLambda(block.getBounds().getMaxX(), block.getBounds().getMaxY()));
					lineList.add(EPoint.fromLambda(block.getBounds().getMinX(), block.getBounds().getMaxY()));
					lineList.add(EPoint.fromLambda(block.getBounds().getMaxX(), block.getBounds().getMinY()));
					errorLogger.logMessageWithLines(errorMsg, polyList, lineList, cell, 0, true);
					return true;
				}
			}
			return false;
		}

		private void growNetwork()
		{
			// initialize list of SOGBounds to extract
			extractList = new HashMap<SOGBound,Integer>();

			// fill list from the first endpoint of the route
			MutableInteger miA = new MutableInteger(netID.intValue() | BLOCKAGEENDA);
			MutableInteger miB = new MutableInteger(netID.intValue() | BLOCKAGEENDB);
			growPoint(aEndpoints.getCenterX(), aEndpoints.getCenterY(), aZ, miA);

			// iterate until the list is empty
			for(;;)
			{
				Iterator<SOGBound> it = extractList.keySet().iterator();
				if (!it.hasNext()) break;
				SOGBound sBound = it.next();
				Integer layerNumInt = extractList.get(sBound);
				extractList.remove(sBound);
				growArea(sBound, layerNumInt.intValue(), sBound.getNetID());
			}

			// now fill list from the second endpoint, noting whether the routing is already done
			boolean alreadyDone = growPoint(bEndpoints.getCenterX(), bEndpoints.getCenterY(), bZ, miB);
			if (alreadyDone) alreadyRouted = true;

			// add in blockage setting for any tap points on the spine
			if (spineTaps != null)
			{
				for(PortInst pi : spineTaps)
				{
					ArcProto ap = getMetalArcOnPort(pi);
					if (ap == null) continue;
					int z = ap.getFunction().getLevel() - 1;
					EPoint pt = pi.getCenter();
					growPoint(pt.getX(), pt.getY(), z, netID);
				}
			}

			// iterate until the list is empty
			for(;;)
			{
				Iterator<SOGBound> it = extractList.keySet().iterator();
				if (!it.hasNext()) break;
				SOGBound sBound = it.next();
				Integer layerNumInt = extractList.get(sBound);
				extractList.remove(sBound);
				growArea(sBound, layerNumInt.intValue(), sBound.getNetID());
			}
			extractList = null;
		}

		/**
		 * Method to accumulate a list of blockage rectangles that are at a given coordinate.
		 * @param x the X coordinate.
		 * @param y the Y coordinate.
		 * @param layerNum the metal layer number (0-based).
		 * @param idNumber the network number being propagated.
		 * @return true if this network number is already at the coordinate.
		 */
		private boolean growPoint(double x, double y, int layerNum, MutableInteger idNumber)
		{
			Rectangle2D search = new Rectangle2D.Double(x, y, 0, 0);
			BlockageTree bTree = rTrees.getMetalTree(primaryMetalLayer[layerNum]);
			if (bTree.isEmpty()) return false;
			boolean foundNet = false;
			for (Iterator<SOGBound> sea = bTree.search(search); sea.hasNext();)
			{
				SOGBound sBound = sea.next();
				if (sBound.isUserSuppliedBlockage()) continue;
				if (!sBound.containsPoint(x, y)) continue;
				if (sBound.getNetID() == null)
				{
					sBound.setNetID(idNumber);
					if (extractList.get(sBound) == null)
					{
						extractList.put(sBound, Integer.valueOf(layerNum));
						blockagesFound++;
						if ((blockagesFound%100) == 0)
							setProgressValue(blockagesFound, totalBlockages);
					}
					continue;
				} else
				{
					if (sBound.isSameBasicNet(idNumber)) foundNet = true;
				}
				sBound.updateNetID(idNumber, netIDsByValue);
			}
			return foundNet;
		}

		private void growArea(SOGBound sBound, int layerNum, MutableInteger idNumber)
		{
			BlockageTree metalTree = rTrees.getMetalTree(primaryMetalLayer[layerNum]);
			Rectangle2D bound = sBound.getBounds();
			for (Iterator<SOGBound> sea = metalTree.search(bound); sea.hasNext(); )
			{
				SOGBound subBound = sea.next();
				if (subBound.isUserSuppliedBlockage()) continue;
				if (sBound instanceof SOGPoly || subBound instanceof SOGPoly)
				{
					// make sure they really intersect
					if (!doesIntersect(sBound, subBound)) continue;
				}
				if (subBound.getNetID() == null)
				{
					subBound.setNetID(idNumber);
					if (extractList.get(subBound) == null)
					{
						extractList.put(subBound, Integer.valueOf(layerNum));
						blockagesFound++;
						if ((blockagesFound%100) == 0)
							setProgressValue(blockagesFound, totalBlockages);
					}
					continue;
				}
				subBound.updateNetID(idNumber, netIDsByValue);
			}

			// look at vias on lower layer
			if (layerNum > 0)
			{
				BlockageTree viaTree = rTrees.getViaTree(viaLayers[layerNum-1]);
				if (!viaTree.isEmpty())
				{
					for (Iterator<SOGBound> sea = viaTree.search(bound); sea.hasNext(); )
					{
						SOGVia subBound = (SOGVia)sea.next();
						if (sBound instanceof SOGPoly)
						{
							// make sure they really intersect
							if (!doesIntersect(sBound, subBound)) continue;
						}
						if (subBound.getNetID() == null)
						{
							subBound.setNetID(idNumber);
							growPoint(subBound.getBounds().getCenterX(), subBound.getBounds().getCenterY(), layerNum-1, idNumber);
							continue;
						}
						subBound.updateNetID(idNumber, netIDsByValue);
					}
				}
			}

			// look at vias on higher layer
			if (layerNum < numMetalLayers-1)
			{
				BlockageTree bTree = rTrees.getViaTree(viaLayers[layerNum]);
				for (Iterator<SOGBound> sea = bTree.search(bound); sea.hasNext();)
				{
					SOGVia subBound = (SOGVia)sea.next();
					if (sBound instanceof SOGPoly)
					{
						// make sure they really intersect
						if (!doesIntersect(sBound, subBound)) continue;
					}
					if (subBound.getNetID() == null)
					{
						subBound.setNetID(idNumber);
						growPoint(subBound.getBounds().getCenterX(), subBound.getBounds().getCenterY(), layerNum+1, idNumber);
						continue;
					}
					subBound.updateNetID(idNumber, netIDsByValue);
				}
			}
		}

		private boolean doesIntersect(SOGBound bound1, SOGBound bound2)
		{
			// first see if the polygons are Manhattan
			if (!bound1.isManhattan() || !bound2.isManhattan()) return true;

			EPoint[] points1;
			if (bound1 instanceof SOGPoly)
			{
				SOGPoly p = (SOGPoly)bound1;
				Point[] po = p.poly.getPoints();
				points1 = new EPoint[po.length];
				for(int i=0; i<po.length; i++) points1[i] = EPoint.fromLambda(po[i].getX(), po[i].getY());
			} else
			{
				points1 = new EPoint[5];
				ERectangle r = bound1.getBounds();
				points1[0] = EPoint.fromLambda(r.getMinX(), r.getMinY());
				points1[1] = EPoint.fromLambda(r.getMinX(), r.getMaxY());
				points1[2] = EPoint.fromLambda(r.getMaxX(), r.getMaxY());
				points1[3] = EPoint.fromLambda(r.getMaxX(), r.getMinY());
				points1[4] = EPoint.fromLambda(r.getMinX(), r.getMinY());
			}

			EPoint[] points2;
			if (bound2 instanceof SOGPoly)
			{
				SOGPoly p = (SOGPoly)bound2;
				Point[] po = p.poly.getPoints();
				points2 = new EPoint[po.length];
				for(int i=0; i<po.length; i++) points2[i] = EPoint.fromLambda(po[i].getX(), po[i].getY());
			} else
			{
				points2 = new EPoint[5];
				ERectangle r = bound2.getBounds();
				points2[0] = EPoint.fromLambda(r.getMinX(), r.getMinY());
				points2[1] = EPoint.fromLambda(r.getMinX(), r.getMaxY());
				points2[2] = EPoint.fromLambda(r.getMaxX(), r.getMaxY());
				points2[3] = EPoint.fromLambda(r.getMaxX(), r.getMinY());
				points2[4] = EPoint.fromLambda(r.getMinX(), r.getMinY());
			}

			// now look for line intersections
			for(int i=1; i<points1.length; i++)
			{
				EPoint p1a = points1[i-1];
				EPoint p1b = points1[i];
				if (p1a.getX() == p1b.getX() && p1a.getY() == p1b.getY()) continue;
				double l1X = Math.min(p1a.getX(), p1b.getX());
				double h1X = Math.max(p1a.getX(), p1b.getX());
				double l1Y = Math.min(p1a.getY(), p1b.getY());
				double h1Y = Math.max(p1a.getY(), p1b.getY());
				for(int j=1; j<points2.length; j++)
				{
					EPoint p2a = points2[j-1];
					EPoint p2b = points2[j];
					if (p2a.getX() == p2b.getX() && p2a.getY() == p2b.getY()) continue;
					double l2X = Math.min(p2a.getX(), p2b.getX());
					double h2X = Math.max(p2a.getX(), p2b.getX());
					double l2Y = Math.min(p2a.getY(), p2b.getY());
					double h2Y = Math.max(p2a.getY(), p2b.getY());

					if (l1X == h1X)
					{
						// line 1 is vertical
						if (l2X == h2X)
						{
							// both lines are vertical
							if (l1X != l2X) continue;
							if (h1Y > l2Y && h2Y > l1Y) return true;
							continue;
						}

						// line one vertical, line two horizontal
						if (l1X > l2X && l1X < h2X && l2Y > l1Y && l2Y < h1Y) return true;
						continue;
					} else
					{
						// line 1 is horizontal
						if (l2Y == h2Y)
						{
							// both lines are horizontal
							if (l1Y != l2Y) continue;
							if (h1X > l2X && h2X > l1X) return true;
							continue;
						}

						// line one horizontal, line two vertical
						if (l1Y > l2Y && l1Y < h2Y && l2X > l1X && l2X < h1X) return true;
						continue;
					}
				}
			}

			// no intersection. Check for complete surround
			Poly p1 = new Poly(points1);
			if (p1.contains(points2[0])) return true;

			Poly p2 = new Poly(points2);
			if (p2.contains(points1[0])) return true;

			// they do not intersect
			return false;
		}

		/**
		 * Method to add extra blockage information that corresponds to ends of each route.
		 */
		private void addBlockagesAtPorts(PortInst pi)
		{
			MutableInteger netIDUse = new MutableInteger(netID.intValue() + BLOCKAGEFAKEENDPOINT);
			PolyBase poly = pi.getPoly();
			Rectangle2D portBounds = poly.getBounds2D();
			ArcProto[] poss = getPossibleConnections(pi.getPortProto());
			int lowMetal = -1, highMetal = -1;
			for (int i = 0; i < poss.length; i++)
			{
				if (poss[i].getTechnology() != tech) continue;
				if (!poss[i].getFunction().isMetal()) continue;
				int level = poss[i].getFunction().getLevel();
				if (lowMetal < 0) lowMetal = highMetal = level; else
				{
					lowMetal = Math.min(lowMetal, level);
					highMetal = Math.max(highMetal, level);
				}
			}
			if (lowMetal < 0) return;

			// reserve space on layers above and below
			double x = pi.getCenter().getX(), y = pi.getCenter().getY();
			Map<Layer,List<Rectangle2D>> blockageRects = new HashMap<Layer,List<Rectangle2D>>();
			for (int via = lowMetal - 2; via < highMetal; via++)
			{
				if (via < 0 || via >= numMetalLayers - 1) continue;
				List<MetalVia> mvs = metalVias[via].getVias();
				if (is2X(via, x, y, x, y) || (via+1 < numMetalLayers && is2X(via+1, x, y, x, y)))
				{
					List<MetalVia> mvs2X = metalVias2X[via].getVias();
					if (mvs2X.size() > 0) mvs = mvs2X;
				}
				int upper = mvs.size();
//upper = 1;   // Used to be uncommented, then ends got blocked
				for(int j=0; j<upper; j++)
				{
					MetalVia mv = mvs.get(j);
					PrimitiveNode np = mv.via;
					SizeOffset so = np.getProtoSizeOffset();
					double xOffset = so.getLowXOffset() + so.getHighXOffset();
					double yOffset = so.getLowYOffset() + so.getHighYOffset();
					double wid = Math.max(np.getDefWidth(ep) - xOffset, minWidth) + xOffset;
					double hei = Math.max(np.getDefHeight(ep) - yOffset, minWidth) + yOffset;
					NodeInst dummy = NodeInst.makeDummyInstance(np, ep, EPoint.ORIGIN, wid, hei, Orientation.IDENT);
					PolyBase[] polys = tech.getShapeOfNode(dummy);
					for (int i = 0; i < polys.length; i++)
					{
						PolyBase metalPoly = polys[i];
						Layer layer = metalPoly.getLayer();
						if (!layer.getFunction().isMetal()) continue;
						Rectangle2D metalBounds = metalPoly.getBounds2D();
						Rectangle2D bounds = new Rectangle2D.Double(metalBounds.getMinX() + portBounds.getCenterX(),
							metalBounds.getMinY() + portBounds.getCenterY(), metalBounds.getWidth(), metalBounds.getHeight());

						// if gridding is forced and this layer is not on grid, do not place blockage
						int layNum = layer.getFunction().getLevel() - 1;
						boolean hor = true;
						if (sogp.isHorizontalEven())
						{
							if ((layNum%2) == 0) hor = false;
						} else
						{
							if ((layNum%2) != 0) hor = false;
						}
						if (forceGridArcs[layNum])
						{
							if (hor)
							{
								if (!isOnYGrid(layNum, bounds.getCenterY())) continue;
							} else
							{
								if (!isOnXGrid(layNum, bounds.getCenterX())) continue;
							}
						}

						// only add blockage if there is nothing else present
						boolean free = true;
						BlockageTree bTree = rTrees.getMetalTree(layer);
						if (!bTree.isEmpty())
						{
							for (Iterator<SOGBound> sea = bTree.search(bounds); sea.hasNext();)
							{
								SOGBound sBound = sea.next();
								int netValue = 0;
								if (sBound.getNetID() != null) netValue = sBound.getNetID().intValue();
								if (netValue != netID.intValue()) continue;
								if (sBound.getBounds().getMinX() > bounds.getMinX() ||
									sBound.getBounds().getMaxX() < bounds.getMaxX() ||
									sBound.getBounds().getMinY() > bounds.getMinY() ||
									sBound.getBounds().getMaxY() < bounds.getMaxY()) continue;
								free = false;
								break;
							}
						}
						if (free)
						{
							List<Rectangle2D> rects = blockageRects.get(layer);
							if (rects == null) blockageRects.put(layer, rects = new ArrayList<Rectangle2D>());
							rects.add(bounds);
						}
					}
				}
			}

			for(Layer layer : blockageRects.keySet())
			{
				List<Rectangle2D> rects = blockageRects.get(layer);
				for(int i=0; i<rects.size(); i++)
				{
					Rectangle2D bound1 = rects.get(i);
					for(int j=0; j<rects.size(); j++)
					{
						if (j == i) continue;
						Rectangle2D bound2 = rects.get(j);
						if (bound1.getMinX() <= bound2.getMinX() && bound1.getMaxX() >= bound2.getMaxX() &&
							bound1.getMinY() <= bound2.getMinY() && bound1.getMaxY() >= bound2.getMaxY())
						{
							// bound2 is smaller and can be eliminated
							rects.remove(j);
							if (i > j) i--;
							j--;
						}
					}
				}
				for(Rectangle2D bounds : rects)
				{
					SOGBound rtn = addRectangle(bounds, layer, netIDUse, false, false);
					if (endBlockages == null)
						endBlockages = new HashMap<Layer,List<SOGBound>>();
					List<SOGBound> blocksOnLayer = endBlockages.get(layer);
					if (blocksOnLayer == null) endBlockages.put(layer, blocksOnLayer = new ArrayList<SOGBound>());
					blocksOnLayer.add(rtn);
				}
			}
		}

		/**
		 * Method to see if a proposed piece of metal has DRC errors (ignoring notches).
		 * @param netID the network ID of the desired metal (blockages on this netID are ignored).
		 * @param metNo the level of the metal.
		 * @param maskColor the mask number of the metal.
		 * @param halfWidth half of the width of the metal.
		 * @param halfHeight half of the height of the metal.
		 * @param surround is the maximum possible DRC surround around the metal (index 0 for X, index 1 for Y).
		 * @param x the X coordinate at the center of the metal.
		 * @param y the Y coordinate at the center of the metal.
		 * @return a blocking SOGBound object that is in the area. Returns null if the area is clear.
		 */
		public SOGBound getMetalBlockage(MutableInteger netID, int metNo, int maskColor, double halfWidth, double halfHeight,
			double surround[], double x, double y)
		{
			// get the R-Tree data for the metal layer
			BlockageTree bTree = rTrees.getMetalTree(primaryMetalLayer[metNo]);
			bTree.lock();
			try {
				// compute the area to search
				double lX = x - halfWidth - surround[0], hX = x + halfWidth + surround[0];
				double lY = y - halfHeight - surround[1], hY = y + halfHeight + surround[1];
				Rectangle2D searchArea = new Rectangle2D.Double(lX, lY, hX - lX, hY - lY);

				// see if there is anything in that area
				for (Iterator<SOGBound> sea = bTree.search(searchArea); sea.hasNext(); )
				{
					SOGBound sBound = sea.next();
					ERectangle bound = sBound.getBounds();
					if (DBMath.isLessThanOrEqualTo(bound.getMaxX(), lX) ||
						DBMath.isGreaterThanOrEqualTo(bound.getMinX(), hX) ||
						DBMath.isLessThanOrEqualTo(bound.getMaxY(), lY) ||
						DBMath.isGreaterThanOrEqualTo(bound.getMinY(), hY)) continue;

					// ignore if on the same net and same mask
					if (sBound.getMaskColor() == maskColor)
					{
						if (netID != null && sBound.isSameBasicNet(netID)) continue;
					}

					// if this is a polygon, do closer examination
					if (sBound instanceof SOGPoly)
					{
						PolyBase poly = ((SOGPoly) sBound).getPoly();
						if (!poly.contains(searchArea)) continue;
					}
					return sBound;
				}
				return null;
			} finally {
				bTree.unlock();
			}
		}

		public void completeRoute(SearchVertex result)
		{
			if (result.wf != null)
			{
				result.wf.vertices = new ArrayList<SearchVertex>();
				getOptimizedList(result, result.wf.vertices);
				assert !result.wf.vertices.isEmpty();

				// if this is a spine route, determine where the taps hit this route
				if (spineTaps != null)
				{
					for(PortInst pi : spineTaps)
					{
						EPoint pt = pi.getCenter();
						SearchVertex lastSV = result.wf.vertices.get(0);
						double bestDist = Double.MAX_VALUE;
						Point2D bestLoc = null;
						int bestInsertPos = 0;
						for (int i=1; i<result.wf.vertices.size(); i++)
						{
							SearchVertex sv = result.wf.vertices.get(i);
							if (lastSV.getZ() == sv.getZ())
							{
								Point2D loc = GenMath.closestPointToSegment(new Point2D.Double(lastSV.getX(), lastSV.getY()),
									new Point2D.Double(sv.getX(), sv.getY()), pt);
								double locX = loc.getX(), locY = loc.getY();
								if (gridLocationsX[sv.getZ()] != null && forceGridArcs[sv.getZ()])
									locX = getClosestXGrid(sv.getZ(), locX).getCoordinate();
								if (gridLocationsY[sv.getZ()] != null && forceGridArcs[sv.getZ()])
									locY = getClosestYGrid(sv.getZ(), locY).getCoordinate();
								double dist = Math.abs(locX - pt.getX()) +  Math.abs(locY - pt.getY());
								if (dist < bestDist)
								{
									bestDist = dist;
									bestLoc = new Point2D.Double(locX, locY);
									bestInsertPos = i;
								}
							}
							lastSV = sv;
						}
						if (bestLoc != null)
						{
							SearchVertex last = result.wf.vertices.get(bestInsertPos);
							SearchVertex svTap = new SearchVertex(bestLoc.getX(), bestLoc.getY(), last.getZ(), last.getC(),
								0, null, null, 0, null, 0, null);
							result.wf.vertices.add(bestInsertPos, svTap);
							spineTapMap.put(svTap, pi);
						}
					}
				}
				routedSuccess = true;
			} else if (result != svAbandoned)
			{
				// failed to route
				if (result == svLimited)
				{
					errorMessage = "Search for '" + routeName + "' too complex (took more than " + complexityLimit + " steps)";
				} else if (result == svExhausted)
				{
					errorMessage = "Search for '" + routeName + "' examined all possibilities without success";
				} else
				{
					assert result == svAborted;
					errorMessage = "Search for '" + routeName + "' aborted by user";
				}
				if (reroute) errorMessage = "(Retry) " + errorMessage;
				boolean isAnError = true;
				if (alreadyRouted)
				{
					errorMessage += ", but route already exists in the circuit";
					warn(errorMessage);
					isAnError = false;
				} else
				{
					error(errorMessage);
				}
				if (result == svLimited || result == svExhausted)
				{
					List<EPoint> lineList = new ArrayList<EPoint>();
					lineList.add(aEndpoints.getCenter());
					lineList.add(bEndpoints.getCenter());
					loggedMessage = errorLogger.logMessageWithLines(errorMessage, null, lineList, cell, 0, isAnError);
				}
			}
			batch.completedRoute(this, result.wf, result);
		}
	}

	public static class RouteResolution implements Serializable
	{
		final CellId cellId;
		final List<RouteNode> nodesToRoute = new ArrayList<RouteNode>();
		final List<RouteArc> arcsToRoute = new ArrayList<RouteArc>();
		final List<Integer> nodesIDsToKill = new ArrayList<Integer>();
		final List<Integer> arcsIDsToKill = new ArrayList<Integer>();
		final Map<RouteAddUnrouted,String> unroutedToAdd = new HashMap<RouteAddUnrouted,String>();

		public RouteResolution(CellId cellId)
		{
			this.cellId = cellId;
		}

		public void addNode(RouteNode rn) { nodesToRoute.add(rn); }

		public void addArc(RouteArc ra) { arcsToRoute.add(ra); }

		public void killNode(NodeInst ni) { nodesIDsToKill.add(Integer.valueOf(ni.getNodeId())); }

		public void killArc(ArcInst ai) { arcsIDsToKill.add(Integer.valueOf(ai.getArcId())); }

		public void addUnrouted(PortInst piA, PortInst piB, String name) { unroutedToAdd.put(new RouteAddUnrouted(piA, piB), name); }

		public void clearRoutes()
		{
			nodesToRoute.clear();
			arcsToRoute.clear();
			nodesIDsToKill.clear();
			arcsIDsToKill.clear();
			unroutedToAdd.clear();
		}
	}

	public static class RouteAddUnrouted implements Serializable
	{
		private int nodeIDA, nodeIDB;
		private PortInst piA, piB;
		private PortProtoId portIdA, portIdB;
		private EPoint locA, locB;

		public RouteAddUnrouted(PortInst piA, PortInst piB)
		{
			this.piA = piA;
			nodeIDA = piA.getNodeInst().getNodeId();
			portIdA = piA.getPortProto().getId();
			locA = piA.getCenter();
			this.piB = piB;
			nodeIDB = piB.getNodeInst().getNodeId();
			portIdB = piB.getPortProto().getId();
			locB = piB.getCenter();
		}

		int getTailId() { return nodeIDA; }

		PortInst getTailPort() { return piA; }

		PortProtoId getTailPortProtoId() { return portIdA; }

		EPoint getTailLocation() { return locA; }

		int getHeadId() { return nodeIDB; }

		PortInst getHeadPort() { return piB; }

		PortProtoId getHeadPortProtoId() { return portIdB; }

		EPoint getHeadLocation() { return locB; }
	}

	public static class RouteNode implements Serializable
	{
		private boolean exists;
		private NodeProto np;
		private EPoint loc;
		private FixpRectangle rect;
		private double wid;
		private double hei;
		private Orientation orient;
		private PortInst pi;
		private int terminalNodeID;
		private PortProtoId terminalNodePort;
		private PortInst tapConnection;
		private NeededRoute nr;

		public RouteNode(NodeProto np, SeaOfGatesEngine soge, EPoint loc, double wid, double hei, Orientation orient, PortInst tapConnection, NeededRoute nr)
		{
			exists = false;
			this.np = np;
			this.loc = loc;
			long x = FixpCoord.lambdaToFixp(loc.getX());
			long y = FixpCoord.lambdaToFixp(loc.getY());
			rect = FixpRectangle.fromFixpDiagonal(x, y, x, y);
			this.wid = wid;
			this.hei = hei;
			this.orient = orient;
			this.pi = null;
			this.tapConnection = tapConnection;
			this.nr = nr;

			if (np.getFunction() == PrimitiveNode.Function.PIN) return;
			NodeInst ni = NodeInst.makeDummyInstance(np, soge.ep, loc, wid, hei, orient);
			FixpTransform trans = ni.rotateOut();
			Poly[] nodeInstPolyList = np.getTechnology().getShapeOfNode(ni, true, false, null);
			for (int i = 0; i < nodeInstPolyList.length; i++)
			{
				PolyBase poly = nodeInstPolyList[i];
				if (poly.getPort() == null) continue;
				poly.transform(trans);
				poly.setStyle(Poly.Type.FILLED);
				soge.addLayer(poly, GenMath.MATID, nr.getNetID(), true, null, false);
			}
		}

		public RouteNode(PortInst pi)
		{
			exists = true;
			this.pi = pi;
			terminalNodeID = pi.getNodeInst().getNodeId();
			terminalNodePort = pi.getPortProto().getId();
			loc = pi.getCenter();
			rect = pi.getPoly().getBounds2D();
		}

		boolean exists() { return exists; }

		NodeProto getProto() { return np; }

		NodeProtoId getProtoId() { return np.getId(); }

		void setPi(PortInst pi) { this.pi = pi; }

		PortInst getPi() { return pi; }

		PortInst getTapConnection() { return tapConnection; }

		void setTapConnection(ImmutableNodeInst ini)
		{
			if (tapConnection != null)
				nr.spineTapNIMap.put(tapConnection, ini);
		}

		Name getBaseName() {
			assert !exists;
			PrimitiveNode pn = (PrimitiveNode)np;
			return pn.getPrimitiveFunction(getTechBits()).getBasename();
		}

		Orientation getOrient() { return orient; }

		EPoint getLoc() { return loc; }

		double getWidth() { return wid;}

		double getHeight() { return hei; }

		EPoint getSize() {
			if (np instanceof Cell) {
				return EPoint.ORIGIN;
			}
			PrimitiveNode pn = (PrimitiveNode)np;
			ERectangle fullRectangle = pn.getFullRectangle();
			long sizeX = DBMath.lambdaToSizeGrid(wid) - fullRectangle.getGridWidth();
			long sizeY = DBMath.lambdaToSizeGrid(hei) - fullRectangle.getGridHeight();
			return EPoint.fromGrid(sizeX, sizeY);
		}

		int getTechBits() { return 0; }

		int getNodeId() {
			assert exists;
			return terminalNodeID;
		}

		PortProtoId getPortProtoId() {
			if (exists) {
				return terminalNodePort;
			}
			PrimitiveNode pn = (PrimitiveNode)np;
			assert pn.getNumPorts() == 1;
			return pn.getPort(0).getId();
		}
	}

	public class RouteArc implements Serializable
	{
		private ArcProto type;
		private double wid;
		private RouteNode from, to;
		private String netName;

		public RouteArc(ArcProto type, String netName, SeaOfGatesEngine soge, Layer layer, double wid, RouteNode from, RouteNode to, NeededRoute nr)
		{
			this.type = type;
			this.netName = netName;
			this.wid = wid;
			this.from = from;
			this.to = to;

			// presuming a simple arc shape
			EPoint fromLoc = from.loc;
			EPoint toLoc = to.loc;

			Poly poly = null;
			if (fromLoc.getX() == toLoc.getX())
			{
				poly = new Poly(fromLoc.getX(), (fromLoc.getY() + toLoc.getY()) / 2,
					wid, Math.abs(fromLoc.getY() - toLoc.getY()) + wid);
			} else if (fromLoc.getY() == toLoc.getY())
			{
				poly = new Poly((fromLoc.getX() + toLoc.getX()) / 2, fromLoc.getY(),
					Math.abs(fromLoc.getX() - toLoc.getX()) + wid, wid);
			} else
			{
				if (from.rect.getMaxX() >= to.rect.getMinX() && from.rect.getMinX() <= to.rect.getMaxX())
				{
					// X coordinates overlap: make vertical arc
					double x = (Math.max(from.rect.getMinX(), to.rect.getMinX()) + Math.min(from.rect.getMaxX(), to.rect.getMaxX())) / 2;
					if (fromLoc.getX() != x) from.loc = EPoint.fromLambda(x, from.loc.getY());
					if (toLoc.getX() != x) to.loc = EPoint.fromLambda(x, to.loc.getY());
					poly = new Poly(x, (fromLoc.getY() + toLoc.getY()) / 2,
						wid, Math.abs(fromLoc.getY() - toLoc.getY()) + wid);
				} else if (from.rect.getMaxY() >= to.rect.getMinY() && from.rect.getMinY() <= to.rect.getMaxY())
				{
					// Y coordinates overlap: make horizontal arc
					double y = (Math.max(from.rect.getMinY(), to.rect.getMinY()) + Math.min(from.rect.getMaxY(), to.rect.getMaxY())) / 2;
					if (fromLoc.getY() != y) from.loc = EPoint.fromLambda(from.loc.getX(), y);
					if (toLoc.getY() != y) to.loc = EPoint.fromLambda(to.loc.getX(), y);
					poly = new Poly((fromLoc.getX() + toLoc.getX()) / 2, y,
						Math.abs(fromLoc.getX() - toLoc.getX()) + wid, wid);
				} else
				{
					String layerName = "";
					if (layer != null) layerName = " " + layer.getName();
					System.out.println("WARNING: angled" + layerName + " wire from (" +
						TextUtils.formatDistance(fromLoc.getX()) + "," + TextUtils.formatDistance(fromLoc.getY()) + ") to (" +
						TextUtils.formatDistance(toLoc.getX()) + "," + TextUtils.formatDistance(toLoc.getY()) + ")");
				}
			}
			if (poly != null && layer != null)
			{
				poly.setLayer(layer);
				poly.setStyle(Poly.Type.FILLED);
				soge.addLayer(poly, GenMath.MATID, nr.getNetID(), true, null, false);
			}

		}

		ArcProto getProto() {
			return type;
		}

		ArcProtoId getProtoId() {
			return type.getId();
		}

		RouteNode getTail() {
			return to;
		}

		RouteNode getHead() {
			return from;
		}

		String getName() {
			return netName;
		}

		double getWidth() {
			return wid;
		}

		long getGridExtendOverMin() {
			return DBMath.lambdaToGrid(0.5 * wid) - type.getBaseExtend().getGrid();
		}

		int getFlags(EditingPreferences ep) {
			return type.getDefaultInst(ep).flags;
		}
	}

	/************************************** WAVEFRONT: THE ACTUAL SEARCH CODE **************************************/

	/**
	 * Class to define a routing search that advances a "wave" of search coordinates from the starting point
	 * to the ending point.
	 */
	public class Wavefront
	{
		/** The route that this is part of. */							final NeededRoute nr;
		/** The direction of the route. */								final boolean aToB;
		/** Active search vertices while running wavefront. */			private final OrderedSearchVertex active;
		/** Used search vertices while running wavefront (debug). */	private final List<SearchVertex> inactive;
		/** Resulting list of vertices found for this wavefront. */		List<SearchVertex> vertices;
		/** Set true to abort this wavefront's search. */				volatile boolean abort;
		/** true if debugging this wavefront */							private boolean debuggingWavefront;
		/** the final SearchVertex for this wavefront. */				private SearchVertex solution;
		/** The starting and ending ports of the wavefront. */			final PortInst from, to;
		/** The starting X/Y coordinates of the wavefront. */			final double fromX, fromY;
		/** The starting area of the wavefront. */						final FixpRectangle fromRect;
		/** The starting metal layer of the wavefront. */				final int fromZ, fromC;
		/** The possible ending coordinates of the wavefront. */		public PossibleEndpoints toPE;
		/** The ending metal layer of the wavefront. */					final int toZ, toC;
		/** The maximum taper lengths. */								final double fromTaperLen, toTaperLen;
		/** Count of the number of wavefront advances made. */			int numStepsMade;
		/** Global routing order for this wavefront direction. */		Rectangle2D [] orderedBuckets;
		/** Global routing lowest bucket for each step. */				int [] orderedBase;
		/** Network ID bits for ends of route. */						final int fromBit;
		/** Direction to move through global routing buckets */			final int globalRoutingDelta;
		@SuppressWarnings({ "unchecked" } )
		/** Search vertices found while running the wavefront. */		final Map<Integer, Map<Integer,SearchVertex>>[] searchVertexPlanes = new Map[numMetalLayers];
		@SuppressWarnings({ "unchecked" } )
		/** true when searching finished successfully or failed */		private boolean finished;
		/** array for optimized vertices (allocated once) */			private List<SearchVertex> optimizedList = new ArrayList<SearchVertex>();

		Wavefront(NeededRoute nr,
			PortInst from, FixpRectangle fromRect, double fromX, double fromY, int fromZ, int fromC, double fromTaperLen, int fromBit,
			PortInst to, PossibleEndpoints toPE, int toZ, int toC, double toTaperLen,
			int globalRoutingDelta, Boolean aToB, boolean debugIt)
		{
			this.nr = nr;
			this.from = from;
			this.fromX = fromX;
			this.fromY = fromY;
			this.fromZ = fromZ;
			this.fromC = fromC;
			this.fromRect = fromRect;
			this.fromBit = fromBit;
			this.to = to;
			this.toPE = toPE;
			this.toZ = toZ;
			this.toC = toC;
			this.fromTaperLen = fromTaperLen;
			this.toTaperLen = toTaperLen;
			if (nr.buckets == null) globalRoutingDelta = 0;
			this.globalRoutingDelta = globalRoutingDelta;
			this.aToB = aToB;
			this.numStepsMade = 0;
			active = new OrderedSearchVertex();
			inactive = new ArrayList<SearchVertex>();
			vertices = null;
			abort = false;
			debuggingWavefront = debugIt;

			SearchVertex svStart = new SearchVertex(fromX, fromY, fromZ, fromC, 0, null, null, 0, this, 0, null);
			if (debuggingWavefront) RoutingDebug.ensureDebuggingShadow(svStart, true);
			if (globalRoutingDelta != 0)
			{
				orderedBuckets = new Rectangle2D[nr.buckets.length];
				orderedBase = new int[nr.buckets.length];
				if (globalRoutingDelta > 0)
				{
					// going from A to B, setup global routing information
					svStart.globalRoutingBucket = 0;
					for(int i=0; i<nr.buckets.length; i++)
					{
						int lastInRun = i;
						for(int j=i+1; j<nr.buckets.length; j++)
						{
							if (nr.buckets[i].getMinX() == nr.buckets[j].getMinX() &&
								nr.buckets[i].getMaxX() == nr.buckets[j].getMaxX()) lastInRun = j;
							if (nr.buckets[i].getMinY() == nr.buckets[j].getMinY()
								&& nr.buckets[i].getMaxY() == nr.buckets[j].getMaxY()) lastInRun = j;
							if (lastInRun != j) break;
						}
						double lX = Math.min(nr.buckets[i].getMinX(), nr.buckets[lastInRun].getMinX());
						double hX = Math.max(nr.buckets[i].getMaxX(), nr.buckets[lastInRun].getMaxX());
						double lY = Math.min(nr.buckets[i].getMinY(), nr.buckets[lastInRun].getMinY());
						double hY = Math.max(nr.buckets[i].getMaxY(), nr.buckets[lastInRun].getMaxY());
						Rectangle2D combinedRun = new Rectangle2D.Double(lX, lY, hX-lX, hY-lY);
						int initially = i;   if (i > 0) initially--;
						for(int pos=i; pos<=lastInRun; pos++)
						{
							orderedBuckets[pos] = combinedRun;
							orderedBase[pos] = initially;
							initially = i;
						}
						if (lastInRun == nr.buckets.length-1) break;
						i = lastInRun-1;
					}
				} else
				{
					svStart.globalRoutingBucket = nr.buckets.length-1;
					for(int i = nr.buckets.length-1; i >= 0; i--)
					{
						int lastInRun = i;
						for(int j = i-1; j >= 0; j--)
						{
							if (nr.buckets[i].getMinX() == nr.buckets[j].getMinX() &&
								nr.buckets[i].getMaxX() == nr.buckets[j].getMaxX()) lastInRun = j;
							if (nr.buckets[i].getMinY() == nr.buckets[j].getMinY() &&
								nr.buckets[i].getMaxY() == nr.buckets[j].getMaxY()) lastInRun = j;
							if (lastInRun != j) break;
						}
						double lX = Math.min(nr.buckets[i].getMinX(), nr.buckets[lastInRun].getMinX());
						double hX = Math.max(nr.buckets[i].getMaxX(), nr.buckets[lastInRun].getMaxX());
						double lY = Math.min(nr.buckets[i].getMinY(), nr.buckets[lastInRun].getMinY());
						double hY = Math.max(nr.buckets[i].getMaxY(), nr.buckets[lastInRun].getMaxY());
						Rectangle2D combinedRun = new Rectangle2D.Double(lX, lY, hX-lX, hY-lY);
						int initially = i;   if (i < nr.buckets.length-1) initially++;
						for(int pos=i; pos>=lastInRun; pos--)
						{
							orderedBuckets[pos] = combinedRun;
							orderedBase[pos] = initially;
							initially = i;
						}
						if (lastInRun == 0) break;
						i = lastInRun+1;
					}
				}
			}
			svStart.cost = 0;
			setVertex(fromX, fromY, fromZ, svStart);
			active.add(svStart);
		}

		public boolean isAtoB() { return aToB; }

		public PortInst getFromPortInst() { return from; }

		public PortInst getToPortInst() { return to; }

		public double getFromX() { return fromX; }

		public double getFromY() { return fromY; }

		public int getFromZ() { return fromZ; }

		public int getFromMask() { return fromC; }

		public PossibleEndpoints getTo() { return toPE; }

		public int getToZ() { return toZ; }

		public int getToMask() { return toC; }

		public Set<SearchVertex> getActive() { return active.getSet(); }

		public List<SearchVertex> getInactive() { return inactive; }

		public NeededRoute getNeededRoute() { return nr; }

		public int getGRDirection() { return globalRoutingDelta; }

		public Rectangle2D[] getOrderedBuckets() { return orderedBuckets; }

		public SearchVertex getFinalSearchVertex() { return solution; }

		public SearchVertex getNextSearchVertex()
		{
			SearchVertex sv = active.getFirst();
			if (sv == null) return svExhausted;
			return sv;
		}

		/**
		 * Method to tell whether there is a SearchVertex at a given coordinate.
		 * @param x the X coordinate desired.
		 * @param y the Y coordinate desired.
		 * @param z the Z coordinate (metal layer) desired.
		 * @return true if there is a SearchVertex at that point.
		 */
		public SearchVertex getVertex(double x, double y, int z)
		{
			Map<Integer, Map<Integer,SearchVertex>> plane = searchVertexPlanes[z];
			if (plane == null) return null;
			Map<Integer,SearchVertex> row = plane.get(new Integer((int)Math.round(y * DBMath.GRID)));
			if (row == null) return null;
			SearchVertex found = row.get(new Integer((int)Math.round(x * DBMath.GRID)));
			return found;
		}

		/**
		 * Method to mark a given coordinate.
		 * @param x the X coordinate desired.
		 * @param y the Y coordinate desired.
		 * @param z the Z coordinate (metal layer) desired.
		 */
		public void setVertex(double x, double y, int z, SearchVertex sv)
		{
			Map<Integer, Map<Integer,SearchVertex>> plane = searchVertexPlanes[z];
			if (plane == null)
				searchVertexPlanes[z] = plane = new TreeMap<Integer, Map<Integer,SearchVertex>>();
			Integer iY = new Integer((int)Math.round(y * DBMath.GRID));
			Map<Integer,SearchVertex> row = plane.get(iY);
			if (row == null)
				plane.put(iY, row = new TreeMap<Integer,SearchVertex>());
			row.put(new Integer((int)Math.round(x * DBMath.GRID)), sv);
		}

		public Map<Integer, Map<Integer,SearchVertex>>[] getSearchVertexPlanes() { return searchVertexPlanes; }

		private String[] debugString;

		private void initDebugStrings()
		{
			debugString = new String[7];
		}

		private void setDebugStringHeader(String str)
		{
			debugString[0] = str;
			//System.out.println(str);
		}

		private void setDebugString(int direction, String str)
		{
			debugString[direction+1] = str;
			//System.out.println(str);
		}

		private void addDebugString(int direction, String str)
		{
			debugString[direction+1] += str;
			//System.out.println(str);
		}

		private void completeDebugString(int direction, String str)
		{
			debugString[direction+1] += str;
			//System.out.println(str);
		}

		/**
		 * Method to advance a wavefront by a single step.
		 * Takes the first SearchVertex in the WaveFront (the one with the lowest cost) and expands it in 6 directions
		 * (+X, -X, +Y, -Y, +Z, -Z), creating up to 6 new SearchVertex objects on the WaveFront.
		 * @return null for a successful advance, non-null to terminate wavefront (could be a completion of the route
		 * or an error).
		 */
		public SearchVertex advanceWavefront()
		{
			// stop if too many steps have been made
			numStepsMade++;
			if (numStepsMade > nr.complexityLimit) return solution = svLimited;

			// get the lowest cost point
			SearchVertex svCurrent = getNextSearchVertex();
			if (svCurrent == svExhausted) return solution = svCurrent;
			active.remove(svCurrent);
			inactive.add(svCurrent);

			double curX = svCurrent.getX();
			double curY = svCurrent.getY();
			int curZ = svCurrent.getZ();
			int curC = svCurrent.getC();

			if (debuggingWavefront)
			{
				initDebugStrings();
				String str = "At: (" + TextUtils.formatDistance(curX) + "," + TextUtils.formatDistance(curY) +
					"," + describeMetal(curZ,curC) + "), Cost: " + svCurrent.cost;
				if (globalRoutingDelta == 0) str += ", NO Global Routing"; else
					str += ", Global Routing Bucket: " + svCurrent.globalRoutingBucket;
				setDebugStringHeader(str);
			}

			// see if automatic generation is requested
			int lastDirection = svCurrent.getAutoGen();
			if (lastDirection >= 0)
				svCurrent.generateIntermediateVertex(lastDirection, toPE.getGriddedRect(), cell);

			// look at all directions from this point
			SearchVertex destinationSV = null;
			for (int i = 0; i < 6; i++)
			{
				// compute a neighboring point
				double dx = 0, dy = 0;
				int dz = 0;
				boolean tooFarFromEnd = false, closeToEnd = false;
				StringBuffer jumpExplanation = null;
				if (debuggingWavefront) jumpExplanation = new StringBuffer();
				switch (i)
				{
					case 0:					// move -X
						if (sogp.isForceHorVer() && ((curZ % 2) == 0) == sogp.isHorizontalEven())
						{
							if (debuggingWavefront) setDebugString(i, "Cannot move in -X axis");
							continue;
						}
						if (toPE.isWithinNonzeroX(curX, curY)) dx = toPE.getClosestX(curX) - curX; else
							if (toPE.atGoalPoint(curX-GRAINSIZE, curY)) dx = -GRAINSIZE; else
									dx = nr.getLowerXGrid(curZ, curX-GRAINSIZE).getCoordinate() - curX;

						if (nr.gridLocationsX[curZ] == null || !nr.forceGridArcs[curZ])
						{
							double intermediate = nr.upToGrainAlways(curX + dx);
							if (intermediate != curX + dx) dx = intermediate - curX;
						}

						if (toPE.isBelowX(curX))
						{
							// jump as far as possible toward goal
							dx = getJumpSize(svCurrent, curX, curY, curZ, dx, dy, jumpExplanation);
							if (dx >= 0) dx = -1;
						}
						if (debuggingWavefront)
						{
							setDebugString(i, "Move " + (dx < 0 ? "" : "+") + TextUtils.formatDistance(dx));
							if (jumpExplanation.length() != 0) addDebugString(i, " [" + jumpExplanation.toString() + "]"); else
								if (!toPE.isBelowX(curX)) addDebugString(i, " [already at Y="+TextUtils.formatDistance(curX)+
									" which is to left of "+TextUtils.formatDistance(toPE.getCenterX()) + "]");
						}
						if (nr.gridLocationsX[curZ] != null && nr.forceGridArcs[curZ] && (!toPE.atGoalPoint(curX+dx, curY) || curZ != toZ))
						{
							double gridX = nr.getClosestXGrid(curZ, curX+dx).getCoordinate();
							if (gridX == curX)
							{
								if (debuggingWavefront) addDebugString(i, ", but gridded right to " + TextUtils.formatDistance(gridX) + " (no movement)");
								continue;
							}
							dx = gridX - curX;
						}
						break;
					case 1:					// move +X
						if (sogp.isForceHorVer() && ((curZ % 2) == 0) == sogp.isHorizontalEven())
						{
							if (debuggingWavefront) setDebugString(i, "Cannot move in +X axis");
							continue;
						}
						if (toPE.isWithinNonzeroX(curX, curY)) dx = toPE.getClosestX(curX) - curX; else
							if (toPE.atGoalPoint(curX+GRAINSIZE, curY)) dx = GRAINSIZE; else
									dx = nr.getLowerXGrid(curZ, curX+GRAINSIZE).getCoordinate() - curX;

						if (nr.gridLocationsX[curZ] == null || !nr.forceGridArcs[curZ])
						{
							double intermediate = nr.downToGrainAlways(curX + dx);
							if (intermediate != curX + dx) dx = intermediate - curX;
						}

						if (toPE.isAboveX(curX))
						{
							// jump as far as possible toward goal
							dx = getJumpSize(svCurrent, curX, curY, curZ, dx, dy, jumpExplanation);
							if (dx <= 0) dx = 1;
						}
						if (debuggingWavefront)
						{
							setDebugString(i, "Move " + (dx < 0 ? "" : "+") + TextUtils.formatDistance(dx));
							if (jumpExplanation.length() != 0) addDebugString(i, " [" + jumpExplanation.toString() + "]"); else
								if (!toPE.isAboveX(curX)) addDebugString(i, " [already at Y="+TextUtils.formatDistance(curX)+
									" which is to right of "+TextUtils.formatDistance(toPE.getCenterX()) + "]");
						}
						if (nr.gridLocationsX[curZ] != null && nr.forceGridArcs[curZ] && (!toPE.atGoalPoint(curX+dx, curY) || curZ != toZ))
						{
							double gridX = nr.getClosestXGrid(curZ, curX+dx).getCoordinate();
							if (gridX == curX)
							{
								if (debuggingWavefront) addDebugString(i, ", but gridded left to " + TextUtils.formatDistance(gridX) + " (no movement)");
								continue;
							}
							dx = gridX - curX;
						}
						break;
					case 2:					// move -Y
						if (sogp.isForceHorVer() && ((curZ % 2) != 0) == sogp.isHorizontalEven())
						{
							if (debuggingWavefront) setDebugString(i, "Cannot move in -Y axis");
							continue;
						}
						if (toPE.isWithinNonzeroY(curX, curY)) dy = toPE.getClosestY(curX) - curY; else
							if (toPE.atGoalPoint(curX, curY-GRAINSIZE)) dy = -GRAINSIZE; else
									dy = nr.getLowerYGrid(curZ, curY-GRAINSIZE).getCoordinate() - curY;

						if (nr.gridLocationsY[curZ] == null || !nr.forceGridArcs[curZ])
						{
							double intermediate = nr.upToGrainAlways(curY + dy);
							if (intermediate != curY + dy) dy = intermediate - curY;
						}

						if (toPE.isBelowY(curY))
						{
							// jump as far as possible toward goal
							dy = getJumpSize(svCurrent, curX, curY, curZ, dx, dy, jumpExplanation);
							if (dy >= 0) dy = -1;
						}
						if (debuggingWavefront)
						{
							setDebugString(i, "Move " + (dy < 0 ? "" : "+") + TextUtils.formatDistance(dy));
							if (jumpExplanation.length() != 0) addDebugString(i, " [" + jumpExplanation.toString() + "]"); else
								if (!toPE.isBelowY(curY)) addDebugString(i, " [already at Y="+TextUtils.formatDistance(curY)+
									" which is below "+TextUtils.formatDistance(toPE.getCenterY()) + "]");
						}
						if (nr.gridLocationsY[curZ] != null && nr.forceGridArcs[curZ] && (!toPE.atGoalPoint(curX, curY+dy) || curZ != toZ))
						{
							double gridY = nr.getClosestYGrid(curZ, curY+dy).getCoordinate();
							if (gridY == curY)
							{
								if (debuggingWavefront) addDebugString(i, ", but gridded up to " + TextUtils.formatDistance(gridY) + " (no movement)");
								continue;
							}
							dy = gridY - curY;
						}
						break;
					case 3:					// move +Y
						if (sogp.isForceHorVer() && ((curZ % 2) != 0) == sogp.isHorizontalEven())
						{
							if (debuggingWavefront) setDebugString(i, "Cannot move in +Y axis");
							continue;
						}
						if (toPE.isWithinNonzeroY(curX, curY)) dy = toPE.getClosestY(curX) - curY; else
							if (toPE.atGoalPoint(curX, curY+GRAINSIZE)) dy = GRAINSIZE; else
									dy = nr.getLowerYGrid(curZ, curY+GRAINSIZE).getCoordinate() - curY;

						if (nr.gridLocationsY[curZ] == null || !nr.forceGridArcs[curZ])
						{
							double intermediate = nr.downToGrainAlways(curY + dy);
							if (intermediate != curY + dy) dy = intermediate - curY;
						}

						if (toPE.isAboveY(curY))
						{
							// jump as far as possible toward goal
							dy = getJumpSize(svCurrent, curX, curY, curZ, dx, dy, jumpExplanation);
							if (dy <= 0) dy = 1;
						}
						if (debuggingWavefront)
						{
							setDebugString(i, "Move " + (dy < 0 ? "" : "+") + TextUtils.formatDistance(dy));
							if (jumpExplanation.length() != 0) addDebugString(i, " [" + jumpExplanation.toString() + "]"); else
								if (!toPE.isAboveY(curY)) addDebugString(i, " [already at Y="+TextUtils.formatDistance(curY)+
									" which is above "+TextUtils.formatDistance(toPE.getCenterY()) + "]");
						}
						if (nr.gridLocationsY[curZ] != null && nr.forceGridArcs[curZ] && (!toPE.atGoalPoint(curX, curY+dy) || curZ != toZ))
						{
							double gridY = nr.getClosestYGrid(curZ, curY+dy).getCoordinate();
							if (gridY == curY)
							{
								if (debuggingWavefront) addDebugString(i, ", but gridded down to " + TextUtils.formatDistance(gridY) + " (no movement)");
								continue;
							}
							dy = gridY - curY;
						}
						break;
					case 4:					// move -Z
						dz = -1;
						if (debuggingWavefront) setDebugString(i, "Move -1");
						break;
					case 5:					// move +Z
						dz = 1;
						if (debuggingWavefront) setDebugString(i, "Move +1");
						break;
				}

				// determine new SearchVertex coordinates
				double nX = curX + dx;
				double nY = curY + dy;
				int nZ = curZ + dz;
				int nC = curC;

				// create the "next" step location
				if (dz == 0)
				{
					// running on same layer: limit distance if running a taper on the initial segment
					if (fromTaperLen >= 0 && !svCurrent.isOffInitialSegment())
					{
						// compute distance from start
						double distX = nX - fromX;
						double distY = nY - fromY;
						double dist = Math.sqrt(distX*distX + distY*distY);
						if (dist > fromTaperLen)
						{
							if (debuggingWavefront) completeDebugString(i, ": initial taper is " + TextUtils.formatDistance(dist)
								+ " from start but maximum taper is " + TextUtils.formatDistance(fromTaperLen));
							continue;
						}
					}
				} else
				{
					// switching to new layer
					if (nZ < 0 || nZ >= numMetalLayers)
					{
						if (debuggingWavefront) completeDebugString(i, ": Out Of Bounds");
						continue;
					}
					if (nr.preventArc(nZ))
					{
						if (debuggingWavefront) completeDebugString(i, ": Disallowed Arc");
						continue;
					}

					// make sure gridding on new layer is honored
					if (nr.forceGridArcs[nZ])
					{
						boolean hor = true;
						if (sogp.isHorizontalEven())
						{
							if ((nZ%2) == 0) hor = false;
						} else
						{
							if ((nZ%2) != 0) hor = false;
						}
						if (!hor && !nr.isOnXGrid(nZ, curX))
						{
							if (debuggingWavefront)
								setDebugString(i, "Not on metal " + (nZ+1) + " X grid");
							continue;
						}
						if (hor && !nr.isOnYGrid(nZ, curY))
						{
							if (debuggingWavefront)
								setDebugString(i, "Not on metal " + (nZ+1) + " Y grid");
							continue;
						}
					}

					// if switching to destination layer that tapers but is too far away, mark that this cannot finish the route
					if (nZ == toZ && toTaperLen >= 0 && toPE.isOnGoalAxis(nX, nY))
					{
						// compute distance from end
						double closest = toPE.getDistToGoal(nX, nY);
						if (closest > toTaperLen)
						{
							if (taperOnlyArcs[nZ])
							{
								if (debuggingWavefront) completeDebugString(i, ": Taper-only layer too far from destination");
								continue;
							}
							tooFarFromEnd = true;
						} else
						{
							closeToEnd = true;
						}
					}

					// if new layer has multiple masks, choose the color
					if (metalLayers[nZ].length == 1) nC = 0; else
					{
						if (sogp.isForceHorVer())
						{
							// layers are forced into tracks: get mask number from coordinate information
							if (((nZ % 2) == 0) == sogp.isHorizontalEven())
							{
								// new layer runs vertically
								if (nr.gridLocationsX[nZ] == null) System.out.println("WARNING: No X grid information for Metal " + (nZ+1)); else
								{
									SeaOfGatesTrack sogt = nr.getClosestXGrid(nZ, nX);
									nC = sogt.getMaskNum();
									if (nC != 0)
									{
										if (nr.is2X(nZ, curX, curY, nX, nY)) nC = metalLayers[nZ].length + 1 - nC;
									} else
									{
										System.out.println("WARNING: No mask color for Metal " + (nZ+1) + " at X=" + TextUtils.formatDistance(nX));
									}
								}
							} else
							{
								// new layer runs horizontally
								if (nr.gridLocationsY[nZ] == null) System.out.println("WARNING: No Y grid information for Metal " + (nZ+1)); else
								{
									SeaOfGatesTrack sogt = nr.getClosestYGrid(nZ, nY);
									nC = sogt.getMaskNum();
									if (nC != 0)
									{
										if (nr.is2X(nZ, curX, curY, nX, nY)) nC = metalLayers[nZ].length + 1 - nC;
									} else
									{
										System.out.println("WARNING: No mask color for Metal " + (nZ+1) + " at Y=" + TextUtils.formatDistance(nY));
									}
								}
							}
						}

						if (nC == 0)
						{
							// track rules failed, figure out color from general rules

							// Rule 1: presume the same mask color as the previous metal
							nC = curC;

							// Rule 2: if switching to destination layer, presume mask color of destination
							if (nZ == toZ) nC = toC;

							// Rule 3: if over new layer, make sure to match its mask color
							if (tech.hasColoredMetalLayer(primaryMetalLayer[nZ]))
							{
								BlockageTree bTree = rTrees.getMetalTree(primaryMetalLayer[nZ]);
								bTree.lock();
								try {
									if (!bTree.isEmpty())
									{
										// see if there is anything under this point
										Rectangle2D searchArea = new Rectangle2D.Double(nX, nY, 0, 0);
										for (Iterator<SOGBound> sea = bTree.search(searchArea); sea.hasNext(); )
										{
											SOGBound sBound = sea.next();
											if (sBound.isSameBasicNet(nr.netID))
												nC = sBound.getMaskColor();
										}
									}
								} finally {
									bTree.unlock();
								}
							}
						}
					}
				}

				// force next step to be inside of global routing area
				if (globalRoutingDelta != 0)
				{
					Rectangle2D limit = orderedBuckets[svCurrent.globalRoutingBucket];
					if (nX < limit.getMinX())
					{
						nX = limit.getMinX();
						if (!toPE.atGoalPoint(nX, curY)) nX = nr.getUpperXGrid(curZ, nX).getCoordinate();
						dx = nX - curX;
						if (dx == 0)
						{
							if (debuggingWavefront) completeDebugString(i, ": Out Of Global Routing X Bounds");
							continue;
						}
					}
					if (nX > limit.getMaxX())
					{
						nX = limit.getMaxX();
						if (!toPE.atGoalPoint(nX, curY)) nX = nr.getLowerXGrid(curZ, nX).getCoordinate();
						dx = nX - curX;
						if (dx == 0)
						{
							if (debuggingWavefront) completeDebugString(i, ": Out Of Global Routing X Bounds");
							continue;
						}
					}
					if (nY < limit.getMinY())
					{
						nY = limit.getMinY();
						if (!toPE.atGoalPoint(curX, nY)) nY = nr.getUpperYGrid(curZ, nY).getCoordinate();
						dy = nY - curY;
						if (dy == 0)
						{
							if (debuggingWavefront) completeDebugString(i, ": Out Of Global Routing Y Bounds");
							continue;
						}
					}
					if (nY > limit.getMaxY())
					{
						nY = limit.getMaxY();
						if (!toPE.atGoalPoint(curX,nY)) nY = nr.getLowerYGrid(curZ, nY).getCoordinate();
						dy = nY - curY;
						if (dy == 0)
						{
							if (debuggingWavefront) completeDebugString(i, ": Out Of Global Routing Y Bounds");
							continue;
						}
					}
				}

				// force next step to be inside of routing bounds
				if (nX < nr.routeBounds.getMinX())
				{
					nX = nr.routeBounds.getMinX();
					if (!toPE.atGoalPoint(nX, curY)) nX = nr.getUpperXGrid(curZ, nX).getCoordinate();
					dx = nX - curX;
					if (dx == 0)
					{
						if (debuggingWavefront) completeDebugString(i, ": Out Of X Bounds");
						continue;
					}
				}
				if (nX > nr.routeBounds.getMaxX())
				{
					nX = nr.routeBounds.getMaxX();
					if (!toPE.atGoalPoint(nX, curY)) nX = nr.getLowerXGrid(curZ, nX).getCoordinate();
					dx = nX - curX;
					if (dx == 0)
					{
						if (debuggingWavefront) completeDebugString(i, ": Out Of X Bounds");
						continue;
					}
				}
				if (nY < nr.routeBounds.getMinY())
				{
					nY = nr.routeBounds.getMinY();
					if (!toPE.atGoalPoint(curX, nY)) nY = nr.getUpperYGrid(curZ, nY).getCoordinate();
					dy = nY - curY;
					if (dy == 0)
					{
						if (debuggingWavefront) completeDebugString(i, ": Out Of Y Bounds");
						continue;
					}
				}
				if (nY > nr.routeBounds.getMaxY())
				{
					nY = nr.routeBounds.getMaxY();
					if (!toPE.atGoalPoint(curX, nY)) nY = nr.getLowerYGrid(curZ, nY).getCoordinate();
					dy = nY - curY;
					if (dy == 0)
					{
						if (debuggingWavefront) completeDebugString(i, ": Out Of Y Bounds");
						continue;
					}
				}

				// see if the point has already been visited
				SearchVertex alreadyThere = getVertex(nX, nY, nZ);
				if (alreadyThere != null)
				{
					if (!active.inList(alreadyThere))
					{
						if (debuggingWavefront) completeDebugString(i, ": Already Visited");
						continue;
					}
				}

				// see if the space is available
				int whichContact = 0;
				Poly[] cuts = null;
				Point2D size = null;
				SearchVertexAddon extraGeometryforMinArea = null;
				if (dz == 0)
				{
					// running on one layer: check surround
					double width = nr.getArcWidth(nZ, curX, curY, nX, nY);
					double metalSpacing = width / 2;
					boolean allClear = false;
					double initNX = nX, initNY = nY;
					String explanation = null;
					if (debuggingWavefront) explanation = "";
					for(;;)
					{
						SearchVertex prevPath = svCurrent;
						double checkX = (curX + nX) / 2, checkY = (curY + nY) / 2;
						double halfWid = metalSpacing + Math.abs(dx) / 2;
						double halfHei = metalSpacing + Math.abs(dy) / 2;
						while (prevPath != null && prevPath.last != null)
						{
							if (prevPath.zv != nZ || prevPath.last.zv != nZ) break;
							if (prevPath.xv == prevPath.last.xv && dx == 0)
							{
								checkY = (prevPath.last.yv + nY) / 2;
								halfHei = metalSpacing + Math.abs(prevPath.last.yv - nY) / 2;
								prevPath = prevPath.last;
							} else if (prevPath.yv == prevPath.last.yv && dy == 0)
							{
								checkX = (prevPath.last.xv + nX) / 2;
								halfWid = metalSpacing + Math.abs(prevPath.last.xv - nX) / 2;
								prevPath = prevPath.last;
							} else
								break;
						}
						StringBuffer reason = null;
						if (debuggingWavefront) reason = new StringBuffer();
						SOGBound sb = getMetalBlockageAndNotch(nZ, nC, halfWid, halfHei, checkX, checkY, prevPath, false, reason);
						if (sb == null)
						{
							allClear = true;
							break;
						}
						if (debuggingWavefront)
							explanation += ": Blocked on " + describeMetal(nZ,nC) + " because proposed " +
								TextUtils.formatDistance(checkX-halfWid) + "<=X<=" + TextUtils.formatDistance(checkX+halfWid) +
								" and " + TextUtils.formatDistance(checkY-halfHei) + "<=Y<=" + TextUtils.formatDistance(checkY+halfHei) +
								" is less than " + TextUtils.formatDistance(metalSpacing) +
								" to " + TextUtils.formatDistance(sb.getBounds().getMinX()) + "<=X<=" + TextUtils.formatDistance(sb.getBounds().getMaxX()) +
								" and " + TextUtils.formatDistance(sb.getBounds().getMinY()) + "<=Y<=" + TextUtils.formatDistance(sb.getBounds().getMaxY()) +
								" (" + reason.toString() + " error)";

						// see if it can be backed out slightly
						if (i == 0)
						{
							// moved left too far...try a bit to the right
							double newNX = nX + GRAINSIZE;
							if (!nr.forceGridArcs[nZ] && toPE.atGoalPoint(newNX, curY)) newNX = nr.downToGrainAlways(newNX); else
								newNX = nr.getUpperXGrid(curZ, newNX).getCoordinate();
							if (newNX >= curX || DBMath.areEquals(newNX, nX)) break;
							dx = newNX - curX;
							if (dx == 0) break;
						} else if (i == 1)
						{
							// moved right too far...try a bit to the left
							double newNX = nX - GRAINSIZE;
							if (!nr.forceGridArcs[nZ] && toPE.atGoalPoint(newNX, curY)) newNX = nr.upToGrainAlways(newNX); else
								newNX = nr.getLowerXGrid(curZ, newNX).getCoordinate();
							if (newNX <= curX || DBMath.areEquals(newNX, nX)) break;
							dx = newNX - curX;
							if (dx == 0) break;
						} else if (i == 2)
						{
							// moved down too far...try a bit up
							double newNY = nY + GRAINSIZE;
							if (!nr.forceGridArcs[nZ] && toPE.atGoalPoint(curX, newNY)) newNY = nr.downToGrainAlways(newNY); else
								newNY = nr.getUpperYGrid(curZ, newNY).getCoordinate();
							if (newNY >= curY || DBMath.areEquals(newNY, nY)) break;
							dy = newNY - curY;
							if (dy == 0) break;
						} else if (i == 3)
						{
							// moved up too far...try a bit down
							double newNY = nY - GRAINSIZE;
							if (!nr.forceGridArcs[nZ] && toPE.atGoalPoint(curX, newNY)) newNY = nr.upToGrainAlways(newNY); else
								newNY = nr.getLowerYGrid(curZ, newNY).getCoordinate();
							if (newNY <= curY || DBMath.areEquals(newNY, nY)) break;
							dy = newNY - curY;
							if (dy == 0) break;
						}

						nX = curX + dx;
						nY = curY + dy;
					}
					if (!allClear)
					{
						if (debuggingWavefront)
						{
							double checkX = (curX + nX) / 2, checkY = (curY + nY) / 2;
							double halfWid = metalSpacing + Math.abs(dx) / 2;
							double halfHei = metalSpacing + Math.abs(dy) / 2;

							double[] surround = nr.getSpacingRule(nZ, maxDefArcWidth[nZ], -1);
							SOGBound sb = nr.getMetalBlockage(nr.netID, nZ, nC, halfWid, halfHei, surround, checkX, checkY);
							if (sb != null) explanation += ": Blocked"; else
								explanation += ": Blocked, Notch";
							completeDebugString(i, explanation);
						}
						continue;
					}
					if (debuggingWavefront)
					{
						if (initNX != nX || initNY != nY)
						{
							explanation += " so move only ";
							switch (i)
							{
								case 0: explanation += TextUtils.formatDistance(Math.abs(dx));  break;
								case 1: explanation += TextUtils.formatDistance(dx);            break;
								case 2: explanation += TextUtils.formatDistance(Math.abs(dy));  break;
								case 3: explanation += TextUtils.formatDistance(dy);            break;
							}
						}
						addDebugString(i, explanation);
					}
				} else
				{
					// switching layers
					int lowMetal = Math.min(curZ, nZ);
					int highMetal = Math.max(curZ, nZ);
					List<MetalVia> nps = metalVias[lowMetal].getVias();
					if (nr.is2X(lowMetal, curX, curY, nX, nY) || nr.is2X(highMetal, curX, curY, nX, nY))
					{
						List<MetalVia> nps2X = metalVias2X[lowMetal].getVias();
						if (nps2X.size() > 0) nps = nps2X;
					}
					whichContact = -1;
					String[] failureReasons = null;
					if (debuggingWavefront) failureReasons = new String[nps.size()];
					for (int contactNo = 0; contactNo < nps.size(); contactNo++)
					{
						MetalVia mv = nps.get(contactNo);
						if (mv.horMetal == curZ)
						{
							if (mv.horMetalColor != curC || mv.verMetalColor != nC)
							{
								if (debuggingWavefront) failureReasons[contactNo] = "masks are " + mv.horMetalColor + " and " +
									mv.verMetalColor + " but want masks " + curC + " and " + nC;
								continue;
							}
						} else if (mv.verMetal == curZ)
						{
							if (mv.verMetalColor != curC || mv.horMetalColor != nC)
							{
								if (debuggingWavefront) failureReasons[contactNo] = "masks are " + mv.verMetalColor + " and " +
									mv.horMetalColor + " but want masks " + curC + " and " + nC;
								continue;
							}
						}
						MutableDouble conWid = new MutableDouble(0), conHei = new MutableDouble(0);
						double lastX = nX, lastY = nY;
						SearchVertex prev = svCurrent.getLast();
						if (prev != null)
						{
							lastX = prev.getX();
							lastY = prev.getY();
							if (closeToEnd && (lastX != fromX || lastY != fromY))
							{
								EPoint closest = toPE.getClosestPoint(curX, curY);
								lastX = closest.getX();
								lastY = closest.getY();
							}
						}
						Orientation orient = nr.getMVSize(mv, nX, nY, lastX, lastY, conWid, conHei);
						PrimitiveNode np = mv.via;
						NodeInst dummyNi = NodeInst.makeDummyInstance(np, ep, EPoint.fromLambda(nX, nY), conWid.doubleValue(), conHei.doubleValue(), orient);
						Poly[] conPolys = tech.getShapeOfNode(dummyNi);
						FixpTransform trans = null;
						if (orient != Orientation.IDENT) trans = dummyNi.rotateOut();

						// count the number of cuts and make an array for the data
						int cutCount = 0;
						for (int p = 0; p < conPolys.length; p++)
							if (conPolys[p].getLayer().getFunction().isContact()) cutCount++;
						Poly[] curCuts = new Poly[cutCount];
						cutCount = 0;
						String failedReason = null;
						boolean contactFailed = false;
						for (int p = 0; p < conPolys.length; p++)
						{
							Poly conPoly = conPolys[p];
							if (trans != null) conPoly.transform(trans);
							Rectangle2D conRect = conPoly.getBounds2D();
							Layer conLayer = conPoly.getLayer();
							Layer.Function lFun = conLayer.getFunction();
							if (lFun.isMetal())
							{
								int metalNo = lFun.getLevel() - 1;
								int maskNo = lFun.getMaskColor();
								double halfWid = conRect.getWidth() / 2;
								double halfHei = conRect.getHeight() / 2;
								StringBuffer reason = null;
								if (debuggingWavefront) reason = new StringBuffer();
								SOGBound sb = getMetalBlockageAndNotch(metalNo, maskNo, halfWid, halfHei,
									conRect.getCenterX(), conRect.getCenterY(), svCurrent, false, reason);
								if (sb != null)
								{
									contactFailed = true;
									if (debuggingWavefront)
									{
										SizeOffset so = np.getProtoSizeOffset();
										double xOffset = so.getLowXOffset() + so.getHighXOffset();
										double yOffset = so.getLowYOffset() + so.getHighYOffset();
										failedReason = "layer " + conLayer.getName() + " of " +
											TextUtils.formatDistance(conWid.doubleValue()-xOffset) + "x" + TextUtils.formatDistance(conHei.doubleValue()-yOffset) +
											" " + np.describe(false) +
											" at " + TextUtils.formatDistance(conRect.getMinX()) + "<=X<=" + TextUtils.formatDistance(conRect.getMaxX()) +
											" and " + TextUtils.formatDistance(conRect.getMinY()) + "<=Y<=" + TextUtils.formatDistance(conRect.getMaxY()) +
											" on net " + nr.netID + " has " + reason.toString() + " error with " +
											TextUtils.formatDistance(sb.getBounds().getMinX()) + "<=X<=" + TextUtils.formatDistance(sb.getBounds().getMaxX()) +
											" and " + TextUtils.formatDistance(sb.getBounds().getMinY()) + "<=Y<=" + TextUtils.formatDistance(sb.getBounds().getMaxY()) +
											" on net " + sb.getNetID();
									}
									break;
								}
							} else if (lFun.isContact())
							{
								// make sure vias don't get too close
								String error = validCut(svCurrent, lowMetal, highMetal, conRect, conLayer);
								if (error != null)
								{
									if (debuggingWavefront) failedReason = error;
									contactFailed = true;
									break;
								}
								curCuts[cutCount++] = conPoly;
							}
						}
						if (contactFailed)
						{
							if (debuggingWavefront) failureReasons[contactNo] = failedReason;
							continue;
						}

						// see if previous metal meets minimum area considerations
						StringBuffer message = new StringBuffer();
						MutableBoolean error = new MutableBoolean(false);
						extraGeometryforMinArea = determineMinimumArea(svCurrent, nX, nY, nC, nZ, curC, curZ, mv,
							conWid.doubleValue(), conHei.doubleValue(), error, message, false);
						if (error.booleanValue())
						{
							if (message.length() > 0)
							{
								if (debuggingWavefront)
									failureReasons[contactNo] = message.toString();
							}
							continue;
						}

						whichContact = contactNo;
						cuts = curCuts;
						size = new Point2D.Double(conWid.doubleValue(), conHei.doubleValue());
						break;
					}
					if (whichContact < 0)
					{
						if (debuggingWavefront)
						{
							String further = ": Blocked because:";
							for(int contactNo = 0; contactNo < failureReasons.length; contactNo++)
							{
								MetalVia mv = nps.get(contactNo);
								further += "|In " + describe(mv.via);
								if (mv.orientation != 0) further += " (rotated " + mv.orientation + ")";
								further += " cannot place: " + failureReasons[contactNo];
							}
							completeDebugString(i, further);
						}
						continue;
					}
				}

				// see if it found the destination
				boolean foundDest = toPE.isToPoint(nX, nY) && nZ == toZ;
				if (svCurrent.isMustCompleteRoute() && !foundDest)
				{
					if (debuggingWavefront) completeDebugString(i, ": Switched to taper layer so must connect to destination");
					continue;
				}
				if (svCurrent.isCantCompleteRoute() && foundDest)
				{
					if (debuggingWavefront) completeDebugString(i, ": Switched to taper layer too far from destination");
					continue;
				}

				// check for minimum area in final segment
				if (foundDest)
				{
					StringBuffer message = new StringBuffer();
					MutableBoolean error = new MutableBoolean(false);
					extraGeometryforMinArea = determineMinimumArea(svCurrent, nX, nY, nC, nZ, curC, curZ, null, 0, 0, error, message, true);
					if (error.booleanValue())
					{
						if (message.length() > 0)
						{
							if (debuggingWavefront)
								completeDebugString(i, ": Blocked because " + message.toString());
						}
						continue;
					}
				}

				// check for off-grid situations
				boolean hor = true;
				if (sogp.isHorizontalEven())
				{
					if (((nZ+1)%2) != 0) hor = false;
				} else
				{
					if (((nZ+1)%2) == 0) hor = false;
				}

				// reject if not on grid and grid is forced
				if (nr.forceGridArcs[nZ] && !foundDest)
				{
					if (nr.gridLocationsX[nZ] != null && (hor && !nr.isOnXGrid(nZ, nX)))
					{
						if (debuggingWavefront) completeDebugString(i, ": Not on X grid");
						continue;
					}
					if (nr.gridLocationsY[nZ] != null && (!hor && !nr.isOnYGrid(nZ, nY)))
					{
						if (debuggingWavefront) completeDebugString(i, ": Not on Y grid");
						continue;
					}
				}

				// determine if off-grid
				boolean penaltyOffGridX = false;
				boolean inGoalX = toPE.isOutOfGoalX(nX);
				if (NOGRIDPENALTYATSOURCE) inGoalX &= nX != fromX;
				if (inGoalX || nr.forceGridArcs[nZ])
				{
					if (nr.gridLocationsX[nZ] == null)
					{
						penaltyOffGridX = nr.downToGrainAlways(nX) != nX;
					} else if (!hor)
					{
						penaltyOffGridX = !nr.isOnXGrid(nZ, nX);
					}
				}
				boolean penaltyOffGridY = false;
				boolean inGoalY = toPE.isOutOfGoalY(nY);
				if (NOGRIDPENALTYATSOURCE) inGoalY &= nY != fromY;
				if (inGoalY || nr.forceGridArcs[nZ])
				{
					if (nr.gridLocationsY[nZ] == null)
					{
						penaltyOffGridY = nr.downToGrainAlways(nY) != nY;
					} else if (hor)
					{
						penaltyOffGridY = !nr.isOnYGrid(nZ, nY);
					}
				}

				// we have a candidate next-point
				int newFlags = svCurrent.flags;
				if (curZ != nZ) newFlags &= ~SearchVertex.TOOFARFROMEND;
				if (dz != 0) newFlags |= SearchVertex.OFFINITIALSEGMENT;
				if (tooFarFromEnd) newFlags |= SearchVertex.TOOFARFROMEND;
				if (closeToEnd) newFlags |= SearchVertex.CLOSETOEND;
				SearchVertex svNext = new SearchVertex(nX, nY, nZ, nC, whichContact, cuts, size, Math.min(curZ, nZ), this, newFlags, extraGeometryforMinArea);
				if (debuggingWavefront) RoutingDebug.ensureDebuggingShadow(svNext, false);
				if (dz == 0 && (Math.abs(dx) >= 2 || Math.abs(dy) >= 2)) svNext.setAutoGen(i);
				svNext.last = svCurrent;

				if (foundDest)
				{
					if (debuggingWavefront)
					{
						RoutingDebug.saveSVLink(svNext, i);
						completeDebugString(i, ": Found Destination!");
					}
					destinationSV = svNext;
					continue;
				}

				// determine any change to the global routing bucket
				if (globalRoutingDelta != 0)
					svNext.globalRoutingBucket = getNextBucket(svCurrent, nX, nY);

				// compute the cost
				int newCost = svCurrent.cost;
				String costExplanation = "";
				double distBefore = toPE.getDistToGoal(curX, curY);
				double distAfter = toPE.getDistToGoal(nX, nY);
				int c = (int)((distAfter - distBefore) / 5);
				newCost += c;
				if (debuggingWavefront) costExplanation = " [COST: Progress-to-target=" + c;
				if (dx != 0)
				{
					if (!toPE.isOutOfGoalX(curX))
					{
						c = COSTWRONGDIRECTION / 2;
						newCost += c;
						if (debuggingWavefront) costExplanation += " Zero-X-progress=" + c;
					} else if ((toPE.getCenterX() - curX) * dx < 0)
					{
						if (!toPE.isOutOfGoalY(curY))
						{
							c = 1;
							newCost += c;
							if (debuggingWavefront) costExplanation += " Backward-X-progress-at-dest-Y=" + c;
						} else
						{
							c = COSTWRONGDIRECTION;
							newCost += c;
							if (debuggingWavefront) costExplanation += " Backward-X-progress=" + c;
						}
					}
					if (sogp.isFavorHorVer())
					{
						if (((nZ % 2) == 0) == sogp.isHorizontalEven())
						{
							c = (int)Math.round(COSTALTERNATINGMETAL * Math.abs(dx));
							newCost += c;
							if (debuggingWavefront) costExplanation += " Not-alternating-metal=" + c;
						}
					}
				}
				if (dy != 0)
				{
					if (!toPE.isOutOfGoalY(curY))
					{
						c = COSTWRONGDIRECTION / 2;
						newCost += c;
						if (debuggingWavefront) costExplanation += " Zero-Y-progress=" + c;
					} else if ((toPE.getCenterY() - curY) * dy < 0)
					{
						if (!toPE.isOutOfGoalX(curX))
						{
							c = 1;
							newCost += c;
							if (debuggingWavefront) costExplanation += " Backward-Y-progress-at-dest-X=" + c;
						} else
						{
							c = COSTWRONGDIRECTION;
							newCost += c;
							if (debuggingWavefront) costExplanation += " Backward-Y-progress=" + c;
						}
					}
					if (sogp.isFavorHorVer())
					{
						if (((nZ % 2) != 0) == sogp.isHorizontalEven())
						{
							c = (int)Math.round(COSTALTERNATINGMETAL * Math.abs(dy));
							newCost += c;
							if (debuggingWavefront) costExplanation += " Not-alternating-metal=" + c;
						}
					}
				}
				if (dz != 0)
				{
					if (toZ == curZ)
					{
						c = COSTLAYERCHANGE;
						newCost += c;
						if (debuggingWavefront) costExplanation += " Layer-change=" + c;
					} else if ((toZ - curZ) * dz < 0)
					{
						c = COSTLAYERCHANGE + 1;

						// increase layer-change cost as distance from goal increases
//						int distFromGoal = Math.abs(toZ - (curZ + dz));
//						if (distFromGoal > 3) c *= (distFromGoal-3) * COSTLAYERCHANGE;

						newCost += c;
						if (debuggingWavefront) costExplanation += " Layer-change-wrong-direction=" + c;
					}
				} else
				{
					// not changing layers: compute penalty for unused tracks on either side of run
					double jumpSize1 = Math.abs(getJumpSize(svCurrent, nX, nY, nZ, dx, dy, null));
					double jumpSize2 = Math.abs(getJumpSize(svCurrent, curX, curY, curZ, -dx, -dy, null));
					if (jumpSize1 > GRAINSIZE && jumpSize2 > GRAINSIZE)
					{
						c = (int)((jumpSize1 * jumpSize2) / 10);
						if (c > 0)
						{
							newCost += c;
							if (debuggingWavefront) costExplanation += " Fragments-track=" + c;
						}
					}

					// not changing layers: penalize if turning in X or Y
					if (svCurrent.last != null)
					{
						boolean xTurn = svCurrent.getX() != svCurrent.last.getX();
						boolean yTurn = svCurrent.getY() != svCurrent.last.getY();
						if (xTurn != (dx != 0) || yTurn != (dy != 0))
						{
							c = COSTTURNING;
							newCost += c;
							if (debuggingWavefront) costExplanation += " Turning=" + c;
						}
					}
				}
				if (!favorArcs[nZ])
				{
					c = (int)((COSTLAYERCHANGE * COSTUNFAVORED) * Math.abs(dz) + COSTUNFAVORED * Math.abs(dx + dy));
					newCost += c;
					if (debuggingWavefront) costExplanation += " Layer-unfavored=" + c;
				}
				if (penaltyOffGridX)
				{
					c = COSTOFFGRID;
					newCost += c;
					if (debuggingWavefront) costExplanation += " Off-X-grid=" + c;
				}
				if (penaltyOffGridY)
				{
					c = COSTOFFGRID;
					newCost += c;
					if (debuggingWavefront) costExplanation += " Off-Y-grid=" + c;
				}
				svNext.cost = newCost;

				// replace pending SearchVertex with this if cost is better
				if (alreadyThere != null)
				{
					if (alreadyThere.getCost() < svNext.getCost())
					{
						if (debuggingWavefront) completeDebugString(i, ": Already planned at lower cost (" + alreadyThere.getCost() + ")");
						continue;
					}
					active.remove(alreadyThere);
				}

				// add this vertex into the data structures
				setVertex(nX, nY, nZ, svNext);
				active.add(svNext);
				if (debuggingWavefront)
				{
					completeDebugString(i, ": To (" + TextUtils.formatDistance(svNext.getX()) + "," + TextUtils.formatDistance(svNext.getY()) +
						"," + svNext.describeMetal() + ")" + costExplanation + "]");
				}
				if (debuggingWavefront)
					RoutingDebug.saveSVLink(svNext, i);
			}
			if (debuggingWavefront)
				RoutingDebug.saveSVDetails(svCurrent, debugString, false);
			if (destinationSV != null) solution = destinationSV;
			return destinationSV;
		}

		/**
		 * Method to analyze a cut and see if it conflicts with previous cuts in the chain.
		 * @param svCurrent the chain so far.
		 * @param lowMetal the low metal layer of the contact.
		 * @param highMetal the high metal layer of the contact.
		 * @param conRect the rectangle of the contact cut.
		 * @param conLayer the layer of the contact cut.
		 * @return an error message if there is a problem. Null if the contact is valid.
		 */
		String validCut(SearchVertex svCurrent, int lowMetal, int highMetal, Rectangle2D conRect, Layer conLayer)
		{
			double spacingDist = viaSurround[lowMetal];
			double diagonalViaDist = viaDiagonalDistance[lowMetal];
			double colorDiffDist = viaColorDiffSpacing[lowMetal];
			double surround = spacingDist;
			if (colorDiffDist > surround) surround = colorDiffDist;
			if (diagonalViaDist*2 > surround) surround = diagonalViaDist*2;
			double rectLX = conRect.getMinX(), rectHX = conRect.getMaxX();
			double rectLY = conRect.getMinY(), rectHY = conRect.getMaxY();
			List<SOGBound> viasInArea = new ArrayList<SOGBound>();

			// gather all close vias in the circuit
			BlockageTree bTree = rTrees.getViaTree(conLayer);
			bTree.lock();
			try {
				if (bTree.isEmpty()) return null;
				Rectangle2D searchArea = new Rectangle2D.Double(rectLX-surround-1, rectLY-surround-1,
					conRect.getWidth()+surround*2+2, conRect.getHeight()+surround*2+2);
				for (Iterator<SOGBound> sea = bTree.search(searchArea); sea.hasNext();)
				{
					SOGVia sLoc = (SOGVia)sea.next();
					ERectangle rect = sLoc.getBounds();
					if (sLoc.isSameBasicNet(nr.netID))
					{
						if (DBMath.areEquals(rect.getCenterX(), conRect.getCenterX()) &&
							DBMath.areEquals(rect.getCenterY(), conRect.getCenterY())) continue;
					}
					viasInArea.add(sLoc);
				}
			} finally {
				bTree.unlock();
			}

			// gather all close vias in this path
			for (SearchVertex sv = svCurrent; sv != null; sv = sv.last)
			{
				SearchVertex lastSv = sv.last;
				if (lastSv == null) break;
				if (Math.min(sv.getZ(), lastSv.getZ()) == lowMetal && Math.max(sv.getZ(), lastSv.getZ()) == highMetal)
				{
					Poly[] svCutPolys;
					if (sv.getCutLayer() == lowMetal) svCutPolys = sv.getCutPolys(); else
						svCutPolys = lastSv.getCutPolys();
					if (svCutPolys != null)
					{
						for (Poly cutPoly : svCutPolys)
						{
							Rectangle2D rect = cutPoly.getBounds2D();
							double dist = cutDistance(conRect, rect);
							if (DBMath.isLessThan(dist, surround))
							{
								SOGBound conSB = new SOGBound(ERectangle.fromLambda(rect), null, cutPoly.getLayer().getFunction().getMaskColor());
								viasInArea.add(conSB);
							}
						}
					}
				}
			}

			// stop now if nothing near
			if (viasInArea.size() == 0) return null;

			// analyze the vias for spacing distance to this one
			for(SOGBound sb : viasInArea)
			{
				double dist = cutDistance(conRect, sb.getBounds());
				if (DBMath.isLessThan(dist, spacingDist))
				{
					return "cut " + conLayer.getName() +
						" at " + TextUtils.formatDistance(rectLX) + "<=X<=" + TextUtils.formatDistance(rectHX) +
						" and " + TextUtils.formatDistance(rectLY) + "<=Y<=" + TextUtils.formatDistance(rectHY) +
						" less than "+TextUtils.formatDistance(spacingDist)+" to cut at (" +
						TextUtils.formatDistance(sb.getBounds().getCenterX()) + "," + TextUtils.formatDistance(sb.getBounds().getCenterY()) + ")";
				}
				if (colorDiffDist > 0)
				{
					// different spacing rule if masks are uncolored or differ
					if (sb.getMaskColor() == 0 || conLayer.getFunction().getMaskColor() == 0 ||
						sb.getMaskColor() == conLayer.getFunction().getMaskColor())
					{
						if (DBMath.isLessThan(dist, colorDiffDist))
						{
							return "cut " + conLayer.getName() +
								" at " + TextUtils.formatDistance(rectLX) + "<=X<=" + TextUtils.formatDistance(rectHX) +
								" and " + TextUtils.formatDistance(rectLY) + "<=Y<=" + TextUtils.formatDistance(rectHY) +
								" less than "+TextUtils.formatDistance(colorDiffDist)+" to cut colored " + sb.getMaskColor() + " at (" +
								TextUtils.formatDistance(sb.getBounds().getCenterX()) + "," + TextUtils.formatDistance(sb.getBounds().getCenterY()) + ")";
						}
					}
				}
			}

			// see if any secret rules need to be applied
			SOGBound conSB = new SOGBound(ERectangle.fromLambda(conRect), null, conLayer.getFunction().getMaskColor());
			viasInArea.add(conSB);
			Method secretViaRules = secretViaSpacingRules[lowMetal];
			if (secretViaRules != null)
			{
				try
				{
					String err = (String)secretViaRules.invoke(null, viasInArea);
					if (err != null) return err;
				}
				catch (InvocationTargetException e) {}
				catch (IllegalAccessException e) {}
			}

			// apply diagonal via spacing rules
			if (diagonalViaDist > 0)
			{
				// look for violations among three vias
				if (viasInArea.size() >= 3)
				{
					Rectangle2D[] rects =  new Rectangle2D[3];
					int totVias = viasInArea.size();
					int totViasM1 = totVias - 1;
					int totViasM2 = totViasM1 - 1;
					for(int i=0; i<totViasM2; i++)
					{
						Rectangle2D rect1 = viasInArea.get(i).getBounds();
						for(int j=i+1; j<totViasM1; j++)
						{
							Rectangle2D rect2 = viasInArea.get(j).getBounds();
							for(int k=j+1; k<totVias; k++)
							{
								Rectangle2D rect3 = viasInArea.get(k).getBounds();

								// ignore three in a row
								boolean zigZag = false;
								rects[0] = rect1;  rects[1] = rect2;  rects[2] = rect3;
								Arrays.sort(rects, new SortRectsByCenter(true));
								if (rects[0].getMaxX() < rects[1].getMinX() && rects[1].getMaxX() < rects[2].getMinX())
								{
									// contacts form a line in X, see if they form a line in Y, too
									if (rects[1].getMaxY() < rects[0].getMinY() && rects[1].getMaxY() < rects[2].getMinY()) zigZag = true; else
										if (rects[1].getMinY() > rects[0].getMaxY() && rects[1].getMinY() > rects[2].getMaxY()) zigZag = true;
								}
								if (!zigZag)
								{
									Arrays.sort(rects, new SortRectsByCenter(false));
									if (rects[0].getMaxY() < rects[1].getMinY() && rects[1].getMaxY() < rects[2].getMinY())
									{
										// contacts form a line in Y, see if they form a line in X, too
										if (rects[1].getMaxX() < rects[0].getMinX() && rects[1].getMaxX() < rects[2].getMinX()) zigZag = true; else
											if (rects[1].getMinX() > rects[0].getMaxX() && rects[1].getMinX() > rects[2].getMaxX()) zigZag = true;
									}
								}
								if (!zigZag) continue;

								double dist1 = cutDistance(rect1, rect2);
								double dist2 = cutDistance(rect2, rect3);
								double dist3 = cutDistance(rect3, rect1);
								Rectangle2D centerCut = null, cut1 = null, cut2 = null;
								if (DBMath.isLessThan(dist1, diagonalViaDist) && DBMath.isLessThan(dist2, diagonalViaDist)) { centerCut = rect2;  cut1 = rect1; cut2 = rect3; }
								if (DBMath.isLessThan(dist2, diagonalViaDist) && DBMath.isLessThan(dist3, diagonalViaDist)) { centerCut = rect3;  cut1 = rect1; cut2 = rect2; }
								if (DBMath.isLessThan(dist1, diagonalViaDist) && DBMath.isLessThan(dist3, diagonalViaDist)) { centerCut = rect1;  cut1 = rect2; cut2 = rect3; }
								if (centerCut != null)
								{
									String msg =  "cut " + conLayer.getName() +
										" at " + TextUtils.formatDistance(centerCut.getMinX()) + "<=X<=" + TextUtils.formatDistance(centerCut.getMaxX()) +
										" and " + TextUtils.formatDistance(centerCut.getMinY()) + "<=Y<=" + TextUtils.formatDistance(centerCut.getMaxY()) +
										" is less than " + TextUtils.formatDistance(diagonalViaDist) + " from cuts at (" +
										TextUtils.formatDistance(cut1.getCenterX()) + "," + TextUtils.formatDistance(cut1.getCenterY()) +
										") and (" + TextUtils.formatDistance(cut2.getCenterX()) + "," + TextUtils.formatDistance(cut2.getCenterY()) + ")";
									return msg;
								}
							}
						}
					}
				}

				// look for violations among four vias
				if (viasInArea.size() >= 4)
				{
					Rectangle2D[] rects =  new Rectangle2D[4];
					int totVias = viasInArea.size();
					int totViasM1 = totVias - 1;
					int totViasM2 = totViasM1 - 1;
					int totViasM3 = totViasM2 - 1;
					for(int i=0; i<totViasM3; i++)
					{
						Rectangle2D rect1 = viasInArea.get(i).getBounds();
						for(int j=i+1; j<totViasM2; j++)
						{
							Rectangle2D rect2 = viasInArea.get(j).getBounds();
							for(int k=j+1; k<totViasM1; k++)
							{
								Rectangle2D rect3 = viasInArea.get(k).getBounds();
								for(int l=k+1; l<totVias; l++)
								{
									Rectangle2D rect4 = viasInArea.get(l).getBounds();

									boolean inALine = false;
									rects[0] = rect1;  rects[1] = rect2;  rects[2] = rect3;  rects[3] = rect4;
									Arrays.sort(rects, new SortRectsByCenter(true));
									if (rects[0].getMaxX() < rects[1].getMinX() && rects[1].getMaxX() < rects[2].getMinX() && rects[2].getMaxX() < rects[3].getMinX())
									{
										// contacts form a line in X, see if they form a line in Y, too
										if (rects[0].getCenterY() < rects[1].getCenterY() &&
											rects[1].getCenterY() < rects[2].getCenterY() &&
											rects[2].getCenterY() < rects[3].getCenterY())
										{
											inALine = true;
										} else if (rects[3].getCenterY() < rects[2].getCenterY() &&
											rects[2].getCenterY() < rects[1].getCenterY() &&
											rects[1].getCenterY() < rects[0].getCenterY())
										{
											inALine = true;
										}
									}

									if (!inALine)
									{
										Arrays.sort(rects, new SortRectsByCenter(false));
										if (rects[0].getMaxY() < rects[1].getMinY() && rects[1].getMaxY() < rects[2].getMinY() && rects[2].getMaxY() < rects[3].getMinY())
										{
											// contacts form a line in Y, see if they form a line in X, too
											if (rects[0].getCenterX() < rects[1].getCenterX() &&
												rects[1].getCenterX() < rects[2].getCenterX() &&
												rects[2].getCenterX() < rects[3].getCenterX())
											{
												inALine = true;
											} else if (rects[3].getCenterX() < rects[2].getCenterX() &&
												rects[2].getCenterX() < rects[1].getCenterX() &&
												rects[1].getCenterX() < rects[0].getCenterX())
											{
												inALine = true;
											}
										}
									}
									if (inALine)
									{
										if (DBMath.isGreaterThanOrEqualTo(cutDistance(rects[0], rects[1]), diagonalViaDist)) continue;
										if (DBMath.isGreaterThanOrEqualTo(cutDistance(rects[1], rects[2]), diagonalViaDist)) continue;
										if (DBMath.isGreaterThanOrEqualTo(cutDistance(rects[2], rects[3]), diagonalViaDist)) continue;
										String msg =  "cut " + conLayer.getName() +
											" at " + TextUtils.formatDistance(conRect.getMinX()) + "<=X<=" + TextUtils.formatDistance(conRect.getMaxX()) +
											" and " + TextUtils.formatDistance(conRect.getMinY()) + "<=Y<=" + TextUtils.formatDistance(conRect.getMaxY()) +
											" forms 4-in-a-row line spaced " + TextUtils.formatDistance(diagonalViaDist) + " or less among cuts at (" +
											TextUtils.formatDistance(rects[0].getCenterX()) + "," + TextUtils.formatDistance(rects[0].getCenterY()) +
											") and (" + TextUtils.formatDistance(rects[1].getCenterX()) + "," + TextUtils.formatDistance(rects[1].getCenterY()) +
											") and (" + TextUtils.formatDistance(rects[2].getCenterX()) + "," + TextUtils.formatDistance(rects[2].getCenterY()) +
											") and (" + TextUtils.formatDistance(rects[3].getCenterX()) + "," + TextUtils.formatDistance(rects[3].getCenterY()) + ")";
										return msg;
									}
								}
							}
						}
					}
				}
			}

			// via is good
			return null;
		}

		/**
		 * Comparator class for sorting Rectangles by their X or Y coordinate.
		 */
		private class SortRectsByCenter implements Comparator<Rectangle2D>
		{
			private boolean doX;
			public SortRectsByCenter(boolean doX) { this.doX = doX; }

			public int compare(Rectangle2D r1, Rectangle2D r2)
			{
				double v1, v2;
				if (doX)
				{
					v1 = r1.getCenterX();
					v2 = r2.getCenterX();
				} else
				{
					v1 = r1.getCenterY();
					v2 = r2.getCenterY();
				}
				if (v1 < v2) return -1;
				if (v1 > v2) return 1;
				return 0;
			}
		}

		/**
		 * Method to determine extra geometry needed to ensure minimum area is met
		 * @param svCurrent the current SearchVertex that is changing layers (or ending a route).
		 * @param curX the X coordinate of the SearchVertex.
		 * @param curY the Y coordinate of the SearchVertex.
		 * @param curC the mask color of the SearchVertex.
		 * @param curZ the metal layer of the SearchVertex.
		 * @param metNum the metal layer to consider for minimum area computation.
		 * @param metCol the color of the metal layer being considered for minimum area computation
		 * @param mv the contact being placed at this SearchVertex (may be null).
		 * @param conWid the width of the contact being placed.
		 * @param conHei the height of the contact being placed.
		 * @param error set to true if the geometry is needed but cannot be placed.
		 * @param message set to an error message which explains the problem.
		 * @param finalDest true if this is at the destination of the route.
		 * @return a SearchVertexAddon structure (null if no additional geometry is needed or there is an error).
		 */
		private SearchVertexAddon determineMinimumArea(SearchVertex svCurrent, double curX, double curY, int curC, int curZ, int metCol, int metNum, MetalVia mv,
			double conWid, double conHei, MutableBoolean error, StringBuffer message, boolean finalDest)
		{
			// stop now if not checking min-area for this metal
			if (minimumArea[metNum] <= 0) return null;

			// first compute rectangle in current contact
			List<SearchVertexAddon> possibleCorrections = getExtraGeometryForMinArea(svCurrent, mv, conWid, conHei, curX, curY, curZ, metNum, metCol, finalDest);
			if (possibleCorrections == null) return null;
			boolean rbError = false, anyError = false;
			for(SearchVertexAddon sva : possibleCorrections)
			{
				Rectangle2D[] addedGeoms = sva.getGeometry();

				// see if it is outside of routing bounds
				if (routingBoundsLimit != null)
				{
					boolean failed = false;
					for(int i=0; i<addedGeoms.length; i++)
					{
						Rectangle2D rect = addedGeoms[i];
						if (rect.getMinX() < routingBoundsLimit.getMinX() || rect.getMaxX() > routingBoundsLimit.getMaxX() ||
							rect.getMinY() < routingBoundsLimit.getMinY() || rect.getMaxY() > routingBoundsLimit.getMaxY())
						{
							if (debuggingWavefront)
							{
								if (anyError) message.append(", "); else
									message.append("problem with extra piece of layer " + primaryMetalLayer[metNum].getName() + " for minimum area:");
								message.append(" is outside of routing bounds at " + TextUtils.formatDistance(rect.getMinX()) + "<=X<=" + TextUtils.formatDistance(rect.getMaxX()) +
									" and " + TextUtils.formatDistance(rect.getMinY()) + "<=Y<=" + TextUtils.formatDistance(rect.getMaxY()));
								if (!rbError) message.append(" (routing bounds is " + TextUtils.formatDistance(routingBoundsLimit.getMinX()) + "<=X<=" + TextUtils.formatDistance(routingBoundsLimit.getMaxX()) +
									" and " + TextUtils.formatDistance(routingBoundsLimit.getMinY()) + "<=Y<=" + TextUtils.formatDistance(routingBoundsLimit.getMaxY()) + ")");
							}
							failed = anyError = rbError = true;
							break;
						}
					}
					if (failed) continue;
				}

				// see if it creates a design-rule violation
				boolean failed = false;
				for(int i=0; i<addedGeoms.length; i++)
				{
					Rectangle2D rect = addedGeoms[i];
					double halfWid = rect.getWidth() / 2;
					double halfHei = rect.getHeight() / 2;
					StringBuffer reason = null;
					if (debuggingWavefront) reason = new StringBuffer();
					SOGBound sb = getMetalBlockageAndNotch(metNum, metCol, halfWid, halfHei, rect.getCenterX(), rect.getCenterY(), svCurrent, true, reason);
					if (sb != null)
					{
						if (debuggingWavefront)
						{
							if (anyError) message.append(", "); else
								message.append("problem with extra piece of layer " + primaryMetalLayer[metNum].getName() + " for minimum area:");
							message.append(" rect " + TextUtils.formatDistance(rect.getMinX()) + "<=X<=" + TextUtils.formatDistance(rect.getMaxX()) +
								" and " + TextUtils.formatDistance(rect.getMinY()) + "<=Y<=" + TextUtils.formatDistance(rect.getMaxY()) +
								" has " + reason.toString() + " error with " +
								TextUtils.formatDistance(sb.getBounds().getMinX()) + "<=X<=" + TextUtils.formatDistance(sb.getBounds().getMaxX()) +
								" and " + TextUtils.formatDistance(sb.getBounds().getMinY()) + "<=Y<=" + TextUtils.formatDistance(sb.getBounds().getMaxY()));
						}
						failed = anyError = true;
						break;
					}
				}
				if (failed) continue;

				// this correction is valid
				return sva;
			}
			if (anyError) error.setValue(true);
			return null;
		}

		/**
		 * Method to figure out whether the minimum area has been met for a contact.
		 * @param svCurrent the chain of SearchVertex objects leading up to this contact.
		 * @param mv the contact that will be placed.
		 * @param curX the X coordinate of the contact.
		 * @param curY the Y coordinate of the contact.
		 * @param curZ the Metal layer being considered for minimum area.
		 * @param metNum the metal layer to consider for minimum area computation.
		 * @param finalDest true if this is the end of the route.
		 * @return a Rectangle2D with extra geometry on that layer (if the minimum area has not been met).
		 * Returns null if the minimum area has been met.
		 */
		private List<SearchVertexAddon> getExtraGeometryForMinArea(SearchVertex svCurrent, MetalVia mv, double wid, double hei, double curX, double curY, int curZ,
			int metNum, int metCol, boolean finalDest)
		{
			// look for SearchVertex with a layer change
			getOptimizedList(svCurrent, optimizedList);
			if (finalDest)
			{
				SearchVertex svInsert = new SearchVertex(curX, curY, curZ, 0, 0, null, null, 0, this, 0, null);
				optimizedList.add(0, svInsert);
			}
			PossibleEndpoint added = aToB ? nr.replaceA : nr.replaceB;
			if (added != null)
			{
				MetalVia realMV = added.viaToPlace;
				int newMetal = optimizedList.get(optimizedList.size()-1).getZ() == realMV.horMetal ? realMV.verMetal : realMV.horMetal;
				Point2D size = new Point2D.Double(realMV.via.getDefWidth(ep), realMV.via.getDefHeight(ep));
				optimizedList.get(optimizedList.size()-1).size = size;
				SearchVertex svInsert = new SearchVertex(fromX, fromY, newMetal, 0, 0, null, size, 0, this, 0, null);
				optimizedList.add(svInsert);
			}
			if (finalDest)
			{
				PossibleEndpoint addedFinal = aToB ? nr.replaceA : nr.replaceB;
				if (addedFinal != null)
				{
					MetalVia realMV = addedFinal.viaToPlace;
					int newMetal = optimizedList.get(0).getZ() == realMV.horMetal ? realMV.verMetal : realMV.horMetal;
					Point2D size = new Point2D.Double(realMV.via.getDefWidth(ep), realMV.via.getDefHeight(ep));
					optimizedList.get(0).size = size;
					SearchVertex svInsert = new SearchVertex(toPE.getCenterX(), toPE.getCenterY(), newMetal, 0, 0, null, size, 0, this, 0, null);
					optimizedList.add(0, svInsert);
				}
			}

			int lastInd = optimizedList.size() - 1;
			int prevViaMet = 0;
			for (int ind = 1; ind < optimizedList.size(); ind++)
			{
				SearchVertex sv = optimizedList.get(ind);
				SearchVertex lastSv = optimizedList.get(ind - 1);
				if (sv.getZ() != lastSv.getZ())
				{
					prevViaMet = Math.min(sv.getZ(), lastSv.getZ());
					lastInd = ind-1;
					break;
				}
			}
			SearchVertex svLast = optimizedList.get(lastInd);

			// compute rectangle of metal arc on this layer
			double width = nr.getArcWidth(metNum, curX, curY, svLast.getX(), svLast.getY());
			Point2D head = new Point2D.Double(curX, curY);
			Point2D tail = new Point2D.Double(svLast.getX(), svLast.getY());
			int ang = 0;
			if (head.getX() != tail.getX() || head.getY() != tail.getY())
				ang = GenMath.figureAngle(tail, head);
			Poly poly = Poly.makeEndPointPoly(head.distance(tail), width, ang, head, width / 2, tail,
				width / 2, Poly.Type.FILLED);
			Rectangle2D boundArc = poly.getBounds2D();

			// compute rectangle in current contact (if there is one)
			Rectangle2D bound;
			if (mv == null) bound = boundArc; else
				bound = getContactGeometry(mv, wid, hei, curX, curY, metNum);

			// compute rectangle in previous contact (if there is one)
			Rectangle2D boundPrev;
			if (svLast.size == null) boundPrev = boundArc; else
			{
				// previous contact found: compute its bounds
				List<MetalVia> npsPrev = metalVias[prevViaMet].getVias();
				if (nr.is2X(prevViaMet, svLast.getX(), svLast.getY(), svLast.getX(), svLast.getY()) ||
					(prevViaMet+1 < numMetalLayers && nr.is2X(prevViaMet+1, svLast.getX(), svLast.getY(), svLast.getX(), svLast.getY())))
				{
					List<MetalVia> nps2X = metalVias2X[prevViaMet].getVias();
					if (nps2X.size() > 0) npsPrev = nps2X;
				}
				MetalVia mvPrev = npsPrev.get(svLast.getContactNo());
				boundPrev = getContactGeometry(mvPrev, svLast.size.getX(), svLast.size.getY(), svLast.getX(), svLast.getY(), metNum);
				if (boundPrev == null) boundPrev = bound;
			}

			// if any of the parts is big enough, no need to look further
			if (bound != null && bound.getWidth() * bound.getHeight() >= minimumArea[metNum]) return null;
			if (boundPrev != null && boundPrev.getWidth() * boundPrev.getHeight() >= minimumArea[metNum]) return null;
			if (boundArc != null && boundArc.getWidth() * boundArc.getHeight() >= minimumArea[metNum]) return null;

			// prepare for min-area corrections
			int c = metCol;
			if (c > 0) c--;
			PrimitiveNode pNp = metalPureLayerNodes[metNum][c];
			List<SearchVertexAddon> possibleCorrections = new ArrayList<SearchVertexAddon>();

			// determine extra geometry needed to satisfy minimum area
			boolean metHor = true;
			if (sogp.isHorizontalEven())
			{
				if ((metNum%2) == 0) metHor = false;
			} else
			{
				if ((metNum%2) != 0) metHor = false;
			}
			Boolean hor = null;
			if (sogp.isForceHorVer()) hor = Boolean.valueOf(metHor); else
			{
				if (bound.equals(boundArc) && boundPrev.equals(boundArc))
				{
					if (boundArc.getWidth() > boundArc.getHeight()) hor = Boolean.TRUE; else
						if (boundArc.getWidth() < boundArc.getHeight()) hor = Boolean.FALSE; else
							hor = Boolean.valueOf(metHor);
				} else
				{
					if (bound.getMinX() == boundPrev.getMinX() && bound.getMinX() == boundArc.getMinX() &&
						bound.getMaxX() == boundPrev.getMaxX() && bound.getMaxX() == boundArc.getMaxX()) hor = Boolean.FALSE;
					if (bound.getMinY() == boundPrev.getMinY() && bound.getMinY() == boundArc.getMinY() &&
						bound.getMaxY() == boundPrev.getMaxY() && bound.getMaxY() == boundArc.getMaxY())
					{
						if (hor == null) hor = Boolean.TRUE; else
							hor = Boolean.valueOf(metHor);
					}
				}
			}
			if (hor != null)
			{
				// simple computation if all in a line
				if (hor.booleanValue())
				{
					// geometry runs horizontally
					double lX = Math.min(bound.getMinX(), Math.min(boundPrev.getMinX(), boundArc.getMinX()));
					double hX = Math.max(bound.getMaxX(), Math.max(boundPrev.getMaxX(), boundArc.getMaxX()));
					double area = (hX - lX) * (bound.getMaxY() - bound.getMinY());
					if (DBMath.isLessThan(area, minimumArea[metNum]))
					{
						// compute where extra geometry will go
						double extraLength = (minimumArea[metNum] - area) / (bound.getMaxY() - bound.getMinY());
						double extraLengthGrid = Math.ceil(extraLength / minResolution) * minResolution;
						double extraLengthHalfGrid = Math.ceil(extraLength / 2 / minResolution) * minResolution;

						// try a half-size piece on both sides
						Rectangle2D solution1 = new Rectangle2D.Double(hX, bound.getMinY(), extraLengthHalfGrid, bound.getHeight());
						Rectangle2D solution2 = new Rectangle2D.Double(lX-extraLengthHalfGrid, bound.getMinY(), extraLengthHalfGrid, bound.getHeight());
						possibleCorrections.add(new SearchVertexAddon(solution1, solution2, pNp));

						// try full-size pieces to one side or the other
						solution1 = new Rectangle2D.Double(hX, bound.getMinY(), extraLengthGrid, bound.getHeight());
						solution2 = new Rectangle2D.Double(lX-extraLengthGrid, bound.getMinY(), extraLengthGrid, bound.getHeight());
						if (bound.getCenterX() > boundPrev.getCenterX())
						{
							possibleCorrections.add(new SearchVertexAddon(solution1, null, pNp));
							possibleCorrections.add(new SearchVertexAddon(solution2, null, pNp));
						} else
						{
							possibleCorrections.add(new SearchVertexAddon(solution2, null, pNp));
							possibleCorrections.add(new SearchVertexAddon(solution1, null, pNp));
						}
					}
				} else
				{
					// geometry runs vertically
					double lY = Math.min(bound.getMinY(), Math.min(boundPrev.getMinY(), boundArc.getMinY()));
					double hY = Math.max(bound.getMaxY(), Math.max(boundPrev.getMaxY(), boundArc.getMaxY()));
					double area = (hY - lY) * (bound.getMaxX() - bound.getMinX());
					if (DBMath.isLessThan(area, minimumArea[metNum]))
					{
						// compute where extra geometry will go
						double extraLength = (minimumArea[metNum] - area) / (bound.getMaxX() - bound.getMinX());
						double extraLengthGrid = Math.ceil(extraLength / minResolution) * minResolution;
						double extraLengthHalfGrid = Math.ceil(extraLength / 2 / minResolution) * minResolution;

						// try a half-size piece on both sides
						Rectangle2D solution1 = new Rectangle2D.Double(bound.getMinX(), hY, bound.getWidth(), extraLengthHalfGrid);
						Rectangle2D solution2 = new Rectangle2D.Double(bound.getMinX(), lY-extraLengthHalfGrid, bound.getWidth(), extraLengthHalfGrid);
						possibleCorrections.add(new SearchVertexAddon(solution1, solution2, pNp));

						// try full-size pieces to one side or the other
						solution1 = new Rectangle2D.Double(bound.getMinX(), hY, bound.getWidth(), extraLengthGrid);
						solution2 = new Rectangle2D.Double(bound.getMinX(), lY-extraLengthGrid, bound.getWidth(), extraLengthGrid);
						if (bound.getCenterY() > boundPrev.getCenterY())
						{
							possibleCorrections.add(new SearchVertexAddon(solution1, null, pNp));
							possibleCorrections.add(new SearchVertexAddon(solution2, null, pNp));
						} else
						{
							possibleCorrections.add(new SearchVertexAddon(solution2, null, pNp));
							possibleCorrections.add(new SearchVertexAddon(solution1, null, pNp));
						}
					}
				}
			} else
			{
				// more complex area computation
				PolyMerge pm = new PolyMerge();
				Layer layer = primaryMetalLayer[metNum];
				pm.addPolygon(layer, new PolyBase(bound));
				pm.addPolygon(layer, new PolyBase(boundPrev));
				pm.addPolygon(layer, new PolyBase(boundArc));
				double area = pm.getAreaOfLayer(layer);
				if (DBMath.isLessThan(area, minimumArea[metNum]))
				{
					if (bound.getCenterX() == boundPrev.getCenterX() && bound.getCenterY() != boundPrev.getCenterY())
					{
						// geometry runs vertically
						double lY = Math.min(bound.getMinY(), Math.min(boundPrev.getMinY(), boundArc.getMinY()));
						double hY = Math.max(bound.getMaxY(), Math.max(boundPrev.getMaxY(), boundArc.getMaxY()));
						double extraLength = (minimumArea[metNum] - area) / (bound.getMaxX() - bound.getMinX());
						extraLength = Math.ceil(extraLength / minResolution) * minResolution;
						Rectangle2D solution;
						if (bound.getCenterY() > boundPrev.getCenterY())
							solution = new Rectangle2D.Double(bound.getMinX(), hY, bound.getWidth(), extraLength); else
								solution = new Rectangle2D.Double(bound.getMinX(), lY-extraLength, bound.getWidth(), extraLength);
						possibleCorrections.add(new SearchVertexAddon(solution, null, pNp));
					} else if (bound.getCenterX() != boundPrev.getCenterX() && bound.getCenterY() == boundPrev.getCenterY())
					{
						// geometry runs horizontally
						double lX = Math.min(bound.getMinX(), Math.min(boundPrev.getMinX(), boundArc.getMinX()));
						double hX = Math.max(bound.getMaxX(), Math.max(boundPrev.getMaxX(), boundArc.getMaxX()));
						double extraLength = (minimumArea[metNum] - area) / (bound.getMaxY() - bound.getMinY());
						extraLength = Math.ceil(extraLength / minResolution) * minResolution;
						Rectangle2D solution;
						if (bound.getCenterX() > boundPrev.getCenterX())
							solution = new Rectangle2D.Double(hX, bound.getMinY(), extraLength, bound.getHeight()); else
								solution = new Rectangle2D.Double(lX-extraLength, bound.getMinY(), extraLength, bound.getHeight());
						possibleCorrections.add(new SearchVertexAddon(solution, null, pNp));
					} else
					{
						// contacts are not in a line, more advanced computation
						if (bound.getWidth() > bound.getHeight())
						{
							// layer runs horizontally
							double extraLength = (minimumArea[metNum] - area) / (bound.getMaxY() - bound.getMinY());
							extraLength = Math.ceil(extraLength / minResolution) * minResolution;
							Rectangle2D solution;
							if (bound.getCenterX() > boundPrev.getCenterX())
								solution = new Rectangle2D.Double(bound.getMaxX(), bound.getMinY(), extraLength, bound.getHeight()); else
									solution = new Rectangle2D.Double(bound.getMinX()-extraLength, bound.getMinY(), extraLength, bound.getHeight());
							possibleCorrections.add(new SearchVertexAddon(solution, null, pNp));
						} else
						{
							// layer runs vertically
							double extraLength = (minimumArea[metNum] - area) / (bound.getMaxX() - bound.getMinX());
							extraLength = Math.ceil(extraLength / minResolution) * minResolution;
							Rectangle2D solution;
							if (bound.getCenterY() > boundPrev.getCenterY())
								solution = new Rectangle2D.Double(bound.getMinX(), bound.getMaxY(), bound.getWidth(), extraLength); else
									solution = new Rectangle2D.Double(bound.getMinX(), bound.getMinY()-extraLength, bound.getWidth(), extraLength);
							possibleCorrections.add(new SearchVertexAddon(solution, null, pNp));
						}
					}
				}
			}
			return possibleCorrections;
		}

		/**
		 * Method to get the geometry in a given contact and a given metal.
		 * @param mv the contact to analyze.
		 * @param wid the width of the contact.
		 * @param hei the height of the contact.
		 * @param curX the X coordinate of the contact.
		 * @param curY the Y coordinate of the contact.
		 * @param metNum the desired metal in the contact (0-based).
		 * @return the rectangle of that metal in the contact.
		 */
		private Rectangle2D getContactGeometry(MetalVia mv, double wid, double hei, double curX, double curY, int metNum)
		{
			PrimitiveNode np = mv.via;
			Orientation orient = Orientation.fromJava(mv.orientation * 10, false, false);
			NodeInst ni = NodeInst.makeDummyInstance(np, ep, EPoint.fromLambda(curX, curY), wid, hei, orient);
			FixpTransform trans = null;
			if (orient != Orientation.IDENT) trans = ni.rotateOut();
			Poly[] polys = np.getTechnology().getShapeOfNode(ni);
			for (int j = 0; j < polys.length; j++)
			{
				Poly poly = polys[j];
				if (poly.getLayer().getFunction().getLevel() != metNum+1) continue;
				if (trans != null) poly.transform(trans);
				return poly.getBounds2D();
			}
			return null;
		}

		private int getNextBucket(SearchVertex svCurrent, double nX, double nY)
		{
			int start = orderedBase[svCurrent.globalRoutingBucket];
			for(int bucket = start; ; bucket += globalRoutingDelta)
			{
				if (bucket < 0 || bucket >= nr.buckets.length) break;
				Rectangle2D limit = nr.buckets[bucket];
				if (DBMath.isGreaterThanOrEqualTo(nX, limit.getMinX()) && DBMath.isLessThanOrEqualTo(nX, limit.getMaxX()) &&
					DBMath.isGreaterThanOrEqualTo(nY, limit.getMinY()) && DBMath.isLessThanOrEqualTo(nY, limit.getMaxY()))
						return bucket;
			}

			// not found where it should be, look in the other direction
			for(int bucket = start-globalRoutingDelta; ; bucket -= globalRoutingDelta)
			{
				if (bucket < 0 || bucket >= nr.buckets.length) break;
				Rectangle2D limit = nr.buckets[bucket];
				if (DBMath.isGreaterThanOrEqualTo(nX, limit.getMinX()) && DBMath.isLessThanOrEqualTo(nX, limit.getMaxX()) &&
					DBMath.isGreaterThanOrEqualTo(nY, limit.getMinY()) && DBMath.isLessThanOrEqualTo(nY, limit.getMaxY()))
						return bucket;
			}

			// not found at all: an error
			error("ERROR: Could not find next bucket going from (" + TextUtils.formatDistance(svCurrent.xv) + "," +
				TextUtils.formatDistance(svCurrent.yv) + ") to (" + TextUtils.formatDistance(nX) + "," + TextUtils.formatDistance(nY) +
				") starting at bucket " + orderedBase[svCurrent.globalRoutingBucket] +
				" (really " + svCurrent.globalRoutingBucket + ") and going " + globalRoutingDelta);
			return svCurrent.globalRoutingBucket;
		}

		/**
		 * Method to create the geometry for a winning wavefront. Places nodes and arcs to make
		 * the route, and also updates the R-Tree data structure.
		 */
		public void createRoute()
		{
			if (toPE != null && toPE.hasEndpoints())
			{
				// find the endpoint that was used
				SearchVertex finalSV = optimizedList.get(0);
				PossibleEndpoint replace = aToB ? nr.replaceB : nr.replaceA;
				if (replace.coord.getX() == finalSV.getX() && replace.coord.getY() == finalSV.getY())
				{
					optimizedList.remove(0);
					finalSV = optimizedList.get(0);
					vertices.remove(0);
				}
				for(PossibleEndpoint pe : toPE.choices)
				{
					if (pe.coord.getX() == finalSV.getX() && pe.coord.getY() == finalSV.getY())
					{
						if (aToB)
						{
							nr.replaceB = pe;
							nr.bEndpoints.setCenterX(finalSV.getX());
							nr.bEndpoints.setCenterY(finalSV.getY());
						} else
						{
							nr.replaceA = pe;
							nr.aEndpoints.setCenterX(finalSV.getX());
							nr.aEndpoints.setCenterY(finalSV.getY());
						}
						break;
					}
				}
			}
			String routeName = nr.routeName;
			if (routeName.endsWith("...")) routeName = routeName.substring(0, routeName.length()-3);
			int parenPos = routeName.lastIndexOf('(');
			if (parenPos > 0) routeName = routeName.substring(0, parenPos);
			String origRouteName = routeName;

			RouteNode fromRN = new RouteNode(from);
			RouteNode toRN = new RouteNode(to);
			RouteResolution resolution = nr.batch.resolution;

			// if endpoints were on forbidden metal layers but quick changes were allowed, insert them now
			if (nr.replaceA != null)
			{
				EPoint ctr = nr.bPi.getCenter();
				String msg = "Route '" + origRouteName + "' at (" + TextUtils.formatDistance(ctr.getX()) + "," +
					TextUtils.formatDistance(ctr.getY()) + ") from port " + nr.aPi.getPortProto().getName() + " on node " +
					describe(nr.aPi.getNodeInst()) + " is disallowed on Metal " + (nr.replaceAZ+1) + " so inserting " +
					nr.replaceA.viaToPlace.via.describe(false);
				if (!DBMath.areEquals(ctr.getX(), nr.replaceA.coord.getX()) || !DBMath.areEquals(ctr.getY(), nr.replaceA.coord.getY()))
					msg += " at (" + TextUtils.formatDistance(nr.replaceA.coord.getX()) + "," + TextUtils.formatDistance(nr.replaceA.coord.getY()) + ")";
				msg += " and routing from Metal " + (nr.aZ+1);
				warn(msg);

				PrimitiveNode pNp = nr.replaceA.viaToPlace.via;
				Orientation orient = Orientation.fromJava(nr.replaceA.viaToPlace.orientation * 10, false, false);
				RouteNode newRN = new RouteNode(pNp, SeaOfGatesEngine.this, nr.aEndpoints.getCenter(),
					pNp.getDefWidth(ep), pNp.getDefHeight(ep), orient, null, nr);
				resolution.addNode(newRN);
				RouteNode goal = (fromBit == BLOCKAGEENDA ? fromRN : toRN);
				for(RouteArc ra : resolution.arcsToRoute)
				{
					if (ra.from == goal) ra.from = newRN;
					if (ra.to == goal) ra.to = newRN;
				}
				if (prefs.resultCellName == null)
				{
					// creating connected circuitry: place arc to port at end of route
					ArcProto type = metalArcs[nr.replaceAZ][nr.replaceAC];
					Layer layer = metalLayers[nr.replaceAZ][nr.replaceAC];
					double width = nr.getArcWidth(nr.replaceAZ, nr.aEndpoints.getCenterX(), nr.aEndpoints.getCenterY(), nr.aEndpoints.getCenterX(), nr.aEndpoints.getCenterY());
					resolution.addArc(new RouteArc(type, routeName, SeaOfGatesEngine.this, layer, width, newRN, goal, nr));
				}
				if (fromBit == BLOCKAGEENDA) fromRN = newRN; else toRN = newRN;
				routeName = null;
			}
			if (nr.replaceB != null)
			{
				EPoint ctr = nr.bPi.getCenter();
				String msg = "Route '" + origRouteName + "' at (" + TextUtils.formatDistance(ctr.getX()) + "," +
					TextUtils.formatDistance(ctr.getY()) + ") from port " + nr.bPi.getPortProto().getName() + " on node " +
					describe(nr.bPi.getNodeInst()) + " is disallowed on Metal " + (nr.replaceBZ+1) + " so inserting " +
					nr.replaceB.viaToPlace.via.describe(false);
				if (!DBMath.areEquals(ctr.getX(), nr.replaceB.coord.getX()) || !DBMath.areEquals(ctr.getY(), nr.replaceB.coord.getY()))
					msg += " at (" + TextUtils.formatDistance(nr.replaceB.coord.getX()) + "," + TextUtils.formatDistance(nr.replaceB.coord.getY()) + ")";
				msg += " and routing from Metal " + (nr.bZ+1);
				warn(msg);

				PrimitiveNode pNp = nr.replaceB.viaToPlace.via;
				Orientation orient = Orientation.fromJava(nr.replaceB.viaToPlace.orientation * 10, false, false);
				RouteNode newRN = new RouteNode(pNp, SeaOfGatesEngine.this, nr.bEndpoints.getCenter(),
					pNp.getDefWidth(ep), pNp.getDefHeight(ep), orient, null, nr);
				resolution.addNode(newRN);
				RouteNode goal = (fromBit == BLOCKAGEENDA ? toRN : fromRN);
				for(RouteArc ra : resolution.arcsToRoute)
				{
					if (ra.from == goal) ra.from = newRN;
					if (ra.to == goal) ra.to = newRN;
				}
				if (prefs.resultCellName == null)
				{
					// creating connected circuitry: place arc to port at end of route
					ArcProto type = metalArcs[nr.replaceBZ][nr.replaceBC];
					Layer layer = metalLayers[nr.replaceBZ][nr.replaceBC];
					double width = nr.getArcWidth(nr.replaceBZ, nr.bEndpoints.getCenterX(), nr.bEndpoints.getCenterY(), nr.bEndpoints.getCenterX(), nr.bEndpoints.getCenterY());
					resolution.addArc(new RouteArc(type, routeName, SeaOfGatesEngine.this, layer, width, newRN, goal, nr));
				}
				if (fromBit == BLOCKAGEENDA) toRN = newRN; else fromRN = newRN;
				routeName = null;
			}

			RouteNode lastRN = toRN;
			PolyBase toPoly = to.getPoly();
			if (!DBMath.pointInRect(toPE.getCenter(), toPE.getRect()))
			{
				// end of route is off-grid: adjust it
				if (vertices.size() >= 2)
				{
					SearchVertex v1 = vertices.get(0);
					SearchVertex v2 = vertices.get(1);
					int tc = toC;
					if (tc > 0) tc--;
					ArcProto type = metalArcs[toZ][tc];
					Layer layer = metalLayers[toZ][tc];
					double width = nr.getArcWidth(toZ, toPE.getCenterX(), toPE.getCenterY(), toPE.getCenterX(), toPE.getCenterY());
					PrimitiveNode np = metalArcs[toZ][tc].findPinProto();
					if (v1.getX() == v2.getX())
					{
						// first line is vertical: run a horizontal bit
						RouteNode rn = new RouteNode(np, SeaOfGatesEngine.this, EPoint.fromLambda(v1.getX(), toPoly.getCenterY()),
							np.getDefWidth(ep), np.getDefHeight(ep), Orientation.IDENT, null, nr);
						resolution.addNode(rn);
						RouteArc ra = new RouteArc(type, routeName, SeaOfGatesEngine.this, layer, width, rn, toRN, nr);
						routeName = null;
						resolution.addArc(ra);
						lastRN = rn;
					} else if (v1.getY() == v2.getY())
					{
						// first line is horizontal: run a vertical bit
						RouteNode rn = new RouteNode(np, SeaOfGatesEngine.this, EPoint.fromLambda(toPoly.getCenterX(), v1.getY()),
							np.getDefWidth(ep), np.getDefHeight(ep), Orientation.IDENT, null, nr);
						resolution.addNode(rn);
						RouteArc ra = new RouteArc(type, routeName, SeaOfGatesEngine.this, layer, width, rn, toRN, nr);
						routeName = null;
						resolution.addArc(ra);
						lastRN = rn;
					}
				}
			}

			for (int i = 0; i < vertices.size(); i++)
			{
				SearchVertex sv = vertices.get(i);
				boolean madeContacts = false;
				while (i < vertices.size() - 1)
				{
					SearchVertex svNext = vertices.get(i + 1);
					if (sv.getX() != svNext.getX() || sv.getY() != svNext.getY() || sv.getZ() == svNext.getZ()) break;
					int metNum = Math.min(sv.getZ(), svNext.getZ());
					List<MetalVia> nps = metalVias[metNum].getVias();
					if (nr.is2X(metNum, sv.getX(), sv.getY(), sv.getX(), sv.getY()) ||
						(metNum+1 < numMetalLayers && nr.is2X(metNum+1, sv.getX(), sv.getY(), sv.getX(), sv.getY())))
					{
						List<MetalVia> nps2X = metalVias2X[metNum].getVias();
						if (nps2X.size() > 0) nps = nps2X;
					}
					int whichContact = sv.getContactNo();
					MetalVia mv = nps.get(whichContact);
					PrimitiveNode np = mv.via;
					Orientation orient = Orientation.fromJava(mv.orientation * 10, false, false);
					Point2D size = sv.getSize();
					double conWid = size.getX();
					double conHei = size.getY();
					double otherX = sv.getX(), otherY = sv.getY();
					if (i > 0)
					{
						otherX = vertices.get(i-1).getX();
						otherY = vertices.get(i-1).getY();
					}
					double width = nr.getArcWidth(sv.getZ(), sv.getX(), sv.getY(), otherX, otherY);

					int sCol = sv.getC();
					if (sCol > 0) sCol--;
					ArcProto type = metalArcs[sv.getZ()][sCol];
					Layer layer = metalLayers[sv.getZ()][sCol];
					RouteNode rn = new RouteNode(np, SeaOfGatesEngine.this, EPoint.fromLambda(sv.getX(), sv.getY()), conWid, conHei, orient, null, nr);
					resolution.addNode(rn);
					RouteArc ra = new RouteArc(type, routeName, SeaOfGatesEngine.this, layer, width, lastRN, rn, nr);
					routeName = null;
					resolution.addArc(ra);
					lastRN = rn;
					madeContacts = true;
					sv = svNext;
					i++;
				}
				if (madeContacts && i != vertices.size() - 1) continue;

				int sCol = sv.getC();
				if (sCol > 0) sCol--;
				PrimitiveNode np = metalArcs[sv.getZ()][sCol].findPinProto();
				RouteNode piRN;
				if (i == vertices.size() - 1)
				{
					piRN = fromRN;
					if (!DBMath.pointInRect(EPoint.fromLambda(sv.getX(), sv.getY()), fromRect))
					{
						// end of route is off-grid: adjust it
						if (vertices.size() >= 2)
						{
							int fc = fromC;
							if (fc > 0) fc--;
							SearchVertex v1 = vertices.get(vertices.size() - 2);
							SearchVertex v2 = vertices.get(vertices.size() - 1);
							ArcProto type = metalArcs[fromZ][fc];
							Layer layer = metalLayers[fromZ][fc];
							double width = nr.getArcWidth(fromZ, fromX, fromY, fromX, fromY);
							if (v1.getX() == v2.getX())
							{
								// last line is vertical: run a horizontal bit
								PrimitiveNode pNp = metalArcs[fromZ][fc].findPinProto();
								piRN = new RouteNode(pNp, SeaOfGatesEngine.this, EPoint.fromLambda(v1.getX(), fromY),
									np.getDefWidth(ep), np.getDefHeight(ep), Orientation.IDENT, null, nr);
								resolution.addNode(piRN);
								RouteArc ra = new RouteArc(type, routeName, SeaOfGatesEngine.this, layer, width, piRN, fromRN, nr);
								routeName = null;
								resolution.addArc(ra);
							} else if (v1.getY() == v2.getY())
							{
								// last line is horizontal: run a vertical bit
								PrimitiveNode pNp = metalArcs[fromZ][fc].findPinProto();
								piRN = new RouteNode(pNp, SeaOfGatesEngine.this, EPoint.fromLambda(fromX, v1.getY()),
									np.getDefWidth(ep), np.getDefHeight(ep), Orientation.IDENT, null, nr);
								resolution.addNode(piRN);
								RouteArc ra = new RouteArc(type, routeName, SeaOfGatesEngine.this, layer, width, piRN, fromRN, nr);
								routeName = null;
								resolution.addArc(ra);
							}
						}
					}
				} else
				{
					PortInst pi = null;
					double width = np.getDefWidth(ep), height = np.getDefHeight(ep);
					if (np.getFunction().isPin())
						width = height = nr.getArcWidth(sv.getZ(), sv.getX(), sv.getY(), sv.getX(), sv.getY());
					if (nr.spineTapMap != null) pi = nr.spineTapMap.get(sv);
					piRN = new RouteNode(np, SeaOfGatesEngine.this, EPoint.fromLambda(sv.getX(), sv.getY()),
						width, height, Orientation.IDENT, pi, nr);
					resolution.addNode(piRN);
				}
				if (lastRN != null)
				{
					ArcProto type = metalArcs[sv.getZ()][sCol];
					Layer layer = metalLayers[sv.getZ()][sCol];
					Layer primaryLayer = primaryMetalLayer[sv.getZ()];
					double width = nr.getArcWidth(sv.getZ(), sv.getX(), sv.getY(), sv.getX(), sv.getY());
					if (ANYPOINTONDESTINATION && i == 0)
					{
						if (!DBMath.rectsIntersect(lastRN.rect, piRN.rect))
						{
							// is the layer present for extending the arc?
							double fX = lastRN.getLoc().getX(), fY = lastRN.getLoc().getY();
							double tX = piRN.getLoc().getX(), tY = piRN.getLoc().getY();
							double lX = Math.min(fX, tX) - width/2;
							double hX = Math.max(fX, tX) + width/2;
							double lY = Math.min(fY, tY) - width/2;
							double hY = Math.max(fY, tY) + width/2;
							Rectangle2D searchArea = new Rectangle2D.Double(lX, lY, hX-lX, hY-lY);
							BlockageTree bTree = rTrees.getMetalTree(primaryLayer);
							boolean covered = false;
							bTree.lock();
							try {
								if (!bTree.isEmpty())
								{
									for (Iterator<SOGBound> sea = bTree.search(searchArea); sea.hasNext();)
									{
										SOGBound sBound = sea.next();
										Rectangle2D bound = sBound.getBounds();
										if (bound.getMinX() <= lX && bound.getMaxX() >= hX && bound.getMinY() <= lY && bound.getMaxY() >= hY)
										{
											covered = true;
											break;
										}
									}
								}
							} finally {
								bTree.unlock();
							}
							if (!covered)
							{
								boolean geomOK = false;
								if (fX == tX || fY == tY)
								{
									// try running a single arc
									SOGBound errSV = getMetalBlockageAndNotch(sv.getZ(), sv.getC(), (hX-lX)/2, (hY-lY)/2, (hX+lX)/2, (hY+lY)/2, null, false, null);
									if (errSV == null) geomOK = true;
								} else
								{
									// try running two arcs
									double bend1X = fX, bend1Y = tY;
									double bend2X = tX, bend2Y = fY;

									double bend1aLX = lX, bend1aHX = lX + width;
									double bend1aLY = lY, bend1aHY = hY;

									double bend1bLX = lX, bend1bHX = hX;
									double bend1bLY = hY - width, bend1bHY = hY;

									double bend2aLX = lX, bend2aHX = hX;
									double bend2aLY = lY, bend2aHY = lY + width;

									double bend2bLX = hX - width, bend2bHX = hX;
									double bend2bLY = lY, bend2bHY = hY;

									SOGBound errSVa = getMetalBlockageAndNotch(sv.getZ(), sv.getC(), (bend1aHX-bend1aLX)/2, (bend1aHY-bend1aLY)/2,
										(bend1aHX+bend1aLX)/2, (bend1aHY+bend1aLY)/2, null, false, null);
									SOGBound errSVb = getMetalBlockageAndNotch(sv.getZ(), sv.getC(), (bend1bHX-bend1bLX)/2, (bend1bHY-bend1bLY)/2,
										(bend1bHX+bend1bLX)/2, (bend1bHY+bend1bLY)/2, null, false, null);
									if (errSVa == null && errSVb == null)
									{
										RouteNode bend1RN = new RouteNode(np, SeaOfGatesEngine.this, EPoint.fromLambda(bend1X, bend1Y),
											np.getDefWidth(ep), np.getDefHeight(ep), Orientation.IDENT, null, nr);
										resolution.addNode(bend1RN);
										RouteArc ra = new RouteArc(type, routeName, SeaOfGatesEngine.this, layer, width, piRN, bend1RN, nr);
										routeName = null;
										resolution.addArc(ra);
										piRN = bend1RN;
										geomOK = true;
										System.out.println("AVOIDED UNIVERSAL ARC BECAUSE BEND 1 WORKS");
									} else
									{
										errSVa = getMetalBlockageAndNotch(sv.getZ(), sv.getC(), (bend2aHX-bend2aLX)/2, (bend2aHY-bend2aLY)/2,
											(bend2aHX+bend2aLX)/2, (bend2aHY+bend2aLY)/2, null, false, null);
										errSVb = getMetalBlockageAndNotch(sv.getZ(), sv.getC(), (bend2bHX-bend2bLX)/2, (bend2bHY-bend2bLY)/2,
											(bend2bHX+bend2bLX)/2, (bend2bHY+bend2bLY)/2, null, false, null);
										if (errSVa == null && errSVb == null)
										{
											RouteNode bend2RN = new RouteNode(np, SeaOfGatesEngine.this, EPoint.fromLambda(bend2X, bend2Y),
												np.getDefWidth(ep), np.getDefHeight(ep), Orientation.IDENT, null, nr);
											resolution.addNode(bend2RN);
											RouteArc ra = new RouteArc(type, routeName, SeaOfGatesEngine.this, layer, width, piRN, bend2RN, nr);
											routeName = null;
											resolution.addArc(ra);
											piRN = bend2RN;
											geomOK = true;
											System.out.println("AVOIDED UNIVERSAL ARC BECAUSE BEND 2 WORKS");
										}
									}
								}
								if (!geomOK)
								{
									type = Generic.tech().universal_arc;
									layer = null;
									width = 0;
								} else System.out.println("AVOIDED UNIVERSAL ARC FROM ("+TextUtils.formatDistance(lastRN.loc.getX())+","+
									TextUtils.formatDistance(lastRN.loc.getY())+") TO ("+TextUtils.formatDistance(piRN.loc.getX())+","+
									TextUtils.formatDistance(piRN.loc.getY())+") BECAUSE EXISTING LAYER IS DRC CLEAN");
							} else System.out.println("AVOIDED UNIVERSAL ARC BECAUSE EXISTING LAYER SURROUNDS OK");
						}
					}
					RouteArc ra = new RouteArc(type, routeName, SeaOfGatesEngine.this, layer, width, lastRN, piRN, nr);
					routeName = null;
					resolution.addArc(ra);
				}
				lastRN = piRN;
			}

			for (int i = 0; i < vertices.size(); i++)
			{
				SearchVertex sv = vertices.get(i);
				SearchVertexAddon sva = sv.getMoreGeometry();
				if (sva != null)
				{
					Rectangle2D[] addedGeometry = sva.getGeometry();
					PrimitiveNode pNp = sva.getPureLayerNode();
					for(int j=0; j<addedGeometry.length; j++)
					{
						Rectangle2D geomPiece = addedGeometry[j];
						RouteNode rn = new RouteNode(pNp, SeaOfGatesEngine.this, EPoint.fromLambda(geomPiece.getCenterX(), geomPiece.getCenterY()),
							geomPiece.getWidth(), geomPiece.getHeight(), Orientation.IDENT, null, nr);
						resolution.addNode(rn);
					}
				}
			}

			if (nr.endBlockages != null)
			{
				List<Layer> allLayers = new ArrayList<Layer>();
				for(Layer lay : nr.endBlockages.keySet()) allLayers.add(lay);
				for(Layer lay : allLayers)
				{
					BlockageTree bTree = rTrees.getMetalTree(lay);
					bTree.lock();
					try {
						List<SOGBound> endBlocks = nr.endBlockages.get(lay);
						for(SOGBound endBlock : endBlocks)
						{
							RTNode<SOGBound> origRoot = bTree.getRoot();
							RTNode<SOGBound> newRoot = RTNode.unLinkGeom(null, origRoot, endBlock, false);
							if (newRoot != origRoot)
								bTree.setRoot(newRoot);
						}
						nr.endBlockages.remove(lay);
					} finally {
						bTree.unlock();
					}
				}
			}
		}

		/**
		 * Method to find the X position on a horizontal line that is a given design-rule distance from a blockage point.
		 * @param xPos an X coordinate on the horizontal line.
		 * @param yPos the Y coordinate of the horizontal line.
		 * @param drDist the given design-rule distance.
		 * @param xBlock the X coordinate of the blockage point.
		 * @param yBlock the Y coordinate of the blockage point.
		 * @return the new X coordinate along the horizontal line that is the distance from the point.
		 * Returns null if there is no intersection.
		 */
		private Double getHorizontalBlockage(double xPos, double yPos, double drDist, double xBlock, double yBlock)
		{
			double dY = yBlock - yPos;
			if (dY >= drDist) return null;
			double dX = Math.sqrt(drDist*drDist - dY*dY);
			if (xPos > xBlock) return new Double(xBlock + dX);
			return new Double(xBlock - dX);
		}

		/**
		 * Method to find the X position on a vertical line that is a given design-rule distance from a blockage point.
		 * @param xPos the X coordinate on the vertical line.
		 * @param yPos a Y coordinate of the vertical line.
		 * @param drDist the given design-rule distance.
		 * @param xBlock the X coordinate of the blockage point.
		 * @param yBlock the Y coordinate of the blockage point.
		 * @return the new Y coordinate along the vertical line that is the distance from the point.
		 * Returns null if there is no intersection.
		 */
		private Double getVerticalBlockage(double xPos, double yPos, double drDist, double xBlock, double yBlock)
		{
			double dX = xBlock - xPos;
			if (dX >= drDist) return null;
			double dY = Math.sqrt(drDist*drDist - dX*dX);
			if (yPos > yBlock) return new Double(yBlock + dY);
			return new Double(yBlock - dY);
		}

		private double getJumpSize(SearchVertex sv, double curX, double curY, int curZ, double dx, double dy,
			StringBuffer explanation)
		{
			Rectangle2D jumpBound = nr.jumpBound;
			double width = nr.getArcWidth(curZ, curX, curY, curX+dx, curY+dy);
			double halfWidth = width / 2;
			double[] fromSurround = nr.getSpacingRule(curZ, width, 50);
			double lX = curX - halfWidth, hX = curX + halfWidth;
			double lY = curY - halfWidth, hY = curY + halfWidth;
			// TODO: should stop jump at destination
//			if (dx > 0) hX = Math.min(jumpBound.getMaxX(), toRect.getMaxX()) + halfWidth; else
//				if (dx < 0) lX = Math.max(jumpBound.getMinX(), toRect.getMinX()) - halfWidth; else
//					if (dy > 0) hY = Math.min(jumpBound.getMaxY(), toRect.getMaxY()) + halfWidth; else
//						if (dy < 0) lY = Math.max(jumpBound.getMinY(), toRect.getMinY()) - halfWidth;
			if (dx > 0) hX = jumpBound.getMaxX() + halfWidth; else
				if (dx < 0) lX = jumpBound.getMinX() - halfWidth; else
					if (dy > 0) hY = jumpBound.getMaxY() + halfWidth; else
						if (dy < 0) lY = jumpBound.getMinY() - halfWidth;
			BlockageTree bTree = rTrees.getMetalTree(primaryMetalLayer[curZ]);
			SOGBound topX = null, botX = null, topY = null, botY = null;
			bTree.lock();
			try {
				if (!bTree.isEmpty())
				{
					// see if there is anything in that area
					double lXSearch = lX - fromSurround[0], hXSearch = hX + fromSurround[0];
					double lYSearch = lY - fromSurround[1], hYSearch = hY + fromSurround[1];
					Rectangle2D searchArea = new Rectangle2D.Double(lXSearch, lYSearch, hXSearch - lXSearch, hYSearch - lYSearch);
					for (Iterator<SOGBound> sea = bTree.search(searchArea); sea.hasNext(); )
					{
						SOGBound sBound = sea.next();
						Rectangle2D bound = sBound.getBounds();
						if (sBound.isSameBasicNet(nr.netID)) continue;

						// handle diagonal spacing
						if (lX <= bound.getMaxX() && hX >= bound.getMinX())
						{
							if (lY <= bound.getMaxY() && hY >= bound.getMinY())
							{
								// rectangles touch or overlap
								if (dx > 0 && bound.getMinX()-fromSurround[0] < hX) { hX = bound.getMinX()-fromSurround[0];  topX = sBound; }
								if (dx < 0 && bound.getMaxX()+fromSurround[0] > lX) { lX = bound.getMaxX()+fromSurround[0];  botX = sBound; }
								if (dy > 0 && bound.getMinY()-fromSurround[1] < hY) { hY = bound.getMinY()-fromSurround[1];  topY = sBound; }
								if (dy < 0 && bound.getMaxY()+fromSurround[1] > lY) { lY = bound.getMaxY()+fromSurround[1];  botY = sBound; }
								continue;
							}

							// rectangles are one above the other
							double diff;
							if ((lY+hY)/2 > (bound.getMinY()+bound.getMaxY())/2)
								diff = lY - bound.getMaxY(); else
									diff = bound.getMinY() - hY;
							if (DBMath.isGreaterThanOrEqualTo(diff, fromSurround[1])) continue;
						} else
						{
							if (lY <= bound.getMaxY() && hY >= bound.getMinY())
							{
								// rectangles are side-by-side
								double diff;
								if ((lX+hX)/2 > (bound.getMinX()+bound.getMaxX())/2)
									diff = lX - bound.getMaxX(); else
										diff = bound.getMinX() - hX;
								if (DBMath.isGreaterThanOrEqualTo(diff, fromSurround[0])) continue;
							} else
							{
								// diagonal offset, compute Euclidean distance to corners
								double cut2CornerX, cut2CornerY, cut1CornerX, cut1CornerY;
								if ((bound.getMinX()+bound.getMaxX())/2 < (lX+hX)/2)
								{
									cut2CornerX = bound.getMaxX();
									cut1CornerX = lX;
								} else
								{
									cut2CornerX = bound.getMinX();
									cut1CornerX = hX;
								}
								if ((bound.getMinY()+bound.getMaxY())/2 < (lY+hY)/2)
								{
									cut2CornerY = bound.getMaxY();
									cut1CornerY = lY;
								} else
								{
									cut2CornerY = bound.getMinY();
									cut1CornerY = hY;
								}
								double dX = Math.abs(cut2CornerX - cut1CornerX);
								double dY = Math.abs(cut2CornerY - cut1CornerY);
								if (DBMath.isGreaterThanOrEqualTo(dX, fromSurround[0])) continue;
								if (DBMath.isGreaterThanOrEqualTo(dY, fromSurround[1])) continue;
								double diff = Math.sqrt(dX*dX + dY*dY);
								if (DBMath.isGreaterThanOrEqualTo(diff, Math.max(fromSurround[0], fromSurround[1]))) continue;
							}
						}

						// determine diagonal limit to the jump
						if (dx != 0)
						{
							// horizontal line
							double drDist = Math.max(fromSurround[0], fromSurround[1]);
							double xBlock = bound.getCenterX() < curX ? bound.getMaxX() : bound.getMinX();
							double yBlock = bound.getCenterY() < curY ? bound.getMaxY() : bound.getMinY();
							Double lYintX = getHorizontalBlockage(curX, lY, drDist, xBlock, yBlock);
							Double hYintX = getHorizontalBlockage(curX, hY, drDist, xBlock, yBlock);
							if (dx < 0)
							{
								// moving left
								if (lYintX != null && lYintX.doubleValue() > lX && lYintX.doubleValue() <= curX) { lX = lYintX.doubleValue();  botX = sBound; }
								if (hYintX != null && hYintX.doubleValue() > lX && hYintX.doubleValue() <= curX) { lX = hYintX.doubleValue();  botX = sBound; }
							} else
							{
								// moving right
								if (lYintX != null && lYintX.doubleValue() < hX && lYintX.doubleValue() >= curX) { hX = lYintX.doubleValue();  topX = sBound; }
								if (hYintX != null && hYintX.doubleValue() < hX && hYintX.doubleValue() >= curX) { hX = hYintX.doubleValue();  topX = sBound; }
							}
						} else
						{
							// vertical line
							double drDist = Math.max(fromSurround[0], fromSurround[1]);
							double xBlock = bound.getCenterX() < curX ? bound.getMaxX() : bound.getMinX();
							double yBlock = bound.getCenterY() < curY ? bound.getMaxY() : bound.getMinY();
							Double lXintY = getVerticalBlockage(lX, curY, drDist, xBlock, yBlock);
							Double hXintY = getVerticalBlockage(hX, curY, drDist, xBlock, yBlock);
							if (dy < 0)
							{
								// moving down
								if (lXintY != null && lXintY.doubleValue() > lY && lXintY.doubleValue() <= curY) { lY = lXintY.doubleValue();  botY = sBound; }
								if (hXintY != null && hXintY.doubleValue() > lY && hXintY.doubleValue() <= curY) { lY = hXintY.doubleValue();  botY = sBound; }
							} else
							{
								// moving up
								if (lXintY != null && lXintY.doubleValue() < hY && lXintY.doubleValue() >= curY) { hY = lXintY.doubleValue();  topY = sBound; }
								if (hXintY != null && hXintY.doubleValue() < hY && hXintY.doubleValue() >= curY) { hY = hXintY.doubleValue();  topY = sBound; }
							}
						}
					}
				}
			} finally {
				bTree.unlock();
			}
			if (dx > 0)
			{
				dx = nr.downToGrain(hX - halfWidth) - curX;
				double initialDX = dx;
				if (fromTaperLen >= 0 && !sv.isOffInitialSegment() && dx > fromTaperLen) dx = fromTaperLen;
				if (curZ == toZ)
				{
					Double goalX = toPE.getGoalWithinX(curX, curX+dx, curY);
					if (goalX != null) dx = goalX.doubleValue() - curX;
				}
				Double goalX = toPE.getGoalAboveX(curX+dx, curY);
				if (goalX != null && curZ == toZ) dx = goalX.doubleValue() - curX; else
				{
					if (!toPE.atGoalPoint(curX+dx, curY))
						dx = nr.getLowerXGrid(curZ, curX+dx).getCoordinate() - curX;
					if (globalRoutingDelta != 0)
					{
						Rectangle2D limit = orderedBuckets[sv.globalRoutingBucket];
						if (curX + dx > limit.getMaxX())
						{
							Rectangle2D originalBucket = nr.buckets[sv.globalRoutingBucket];
							dx = limit.getMaxX() - curX;

							// if moved into a new bucket, center it
							if (curX >= originalBucket.getMinX() && curX >= originalBucket.getMaxX() && curX + dx > originalBucket.getMaxX())
								dx -= originalBucket.getWidth()/2;
							dx = nr.getClosestXGrid(curZ, curX+dx).getCoordinate() - curX;
						}
					}
					if (toPE.isOutOfGoalX(curX + dx)) dx = nr.downToGrainAlways(curX + dx) - curX;
				}
				if (explanation != null)
				{
					if (topX == null)
					{
						if (fromTaperLen >= 0 && !sv.isOffInitialSegment() && initialDX > fromTaperLen)
							explanation.append("went full taper distance of "+TextUtils.formatDistance(fromTaperLen)); else
								explanation.append("went to jump bound "+TextUtils.formatDistance(nr.jumpBound.getMinX())+"<=X<="+
									TextUtils.formatDistance(nr.jumpBound.getMaxX())+" and "+TextUtils.formatDistance(nr.jumpBound.getMinY())+"<=Y<="+
									TextUtils.formatDistance(nr.jumpBound.getMaxY()));
					} else
					{
						explanation.append("blocked by rect at " + TextUtils.formatDistance(topX.bound.getMinX()) + "<=X<=" +
							TextUtils.formatDistance(topX.bound.getMaxX()) + " and " +
							TextUtils.formatDistance(topX.bound.getMinY()) + "<=Y<=" + TextUtils.formatDistance(topX.bound.getMaxY()) +
							" on net " + topX.getNetID() + " which is " + TextUtils.formatDistance(fromSurround[0]));
						if (fromSurround[0] != fromSurround[1]) explanation.append("/" + TextUtils.formatDistance(fromSurround[1]));
						explanation.append(" from rect " + TextUtils.formatDistance(lX) + "<=X<=" + TextUtils.formatDistance(hX) + " and " +
							TextUtils.formatDistance(lY) + "<=Y<=" + TextUtils.formatDistance(hY));
					}
				}
				return dx;
			}
			if (dx < 0)
			{
				dx = nr.upToGrain(lX + halfWidth) - curX;
				double initialDX = dx;
				if (fromTaperLen >= 0 && !sv.isOffInitialSegment() && -dx > fromTaperLen) dx = -fromTaperLen;
				if (curZ == toZ)
				{
					Double goalX = toPE.getGoalWithinX(curX+dx, curX, curY);
					if (goalX != null) dx = goalX.doubleValue() - curX;
				}
				Double goalX = toPE.getGoalBelowX(curX+dx, curY);
				if (goalX != null && curZ == toZ) dx = goalX.doubleValue() - curX; else
				{
					if (!toPE.atGoalPoint(curX+dx, curY))
						dx = nr.getUpperXGrid(curZ, curX+dx).getCoordinate() - curX;
					if (globalRoutingDelta != 0)
					{
						Rectangle2D limit = orderedBuckets[sv.globalRoutingBucket];
						if (curX + dx < limit.getMinX())
						{
							Rectangle2D originalBucket = nr.buckets[sv.globalRoutingBucket];
							dx = limit.getMinX() - curX;

							// if moved into a new bucket, center it
							if (curX >= originalBucket.getMinX() && curX >= originalBucket.getMaxX() && curX + dx < originalBucket.getMinX())
								dx += originalBucket.getWidth()/2;
							dx = nr.getClosestXGrid(curZ, curX+dx).getCoordinate() - curX;
						}
					}
					if (toPE.isOutOfGoalX(curX + dx)) dx = nr.upToGrainAlways(curX + dx) - curX;
				}
				if (explanation != null)
				{
					if (botX == null)
					{
						if (fromTaperLen >= 0 && !sv.isOffInitialSegment() && -initialDX > fromTaperLen)
							explanation.append("went full taper distance of "+TextUtils.formatDistance(fromTaperLen)); else
								explanation.append("went to jump bound "+TextUtils.formatDistance(nr.jumpBound.getMinX())+"<=X<="+
									TextUtils.formatDistance(nr.jumpBound.getMaxX())+" and "+TextUtils.formatDistance(nr.jumpBound.getMinY())+"<=Y<="+
									TextUtils.formatDistance(nr.jumpBound.getMaxY()));
					} else
					{
						explanation.append("blocked by rect at " + TextUtils.formatDistance(botX.bound.getMinX()) + "<=X<=" +
							TextUtils.formatDistance(botX.bound.getMaxX()) + " and " +
							TextUtils.formatDistance(botX.bound.getMinY()) + "<=Y<=" + TextUtils.formatDistance(botX.bound.getMaxY()) +
							" on net " + botX.getNetID() + " which is " + TextUtils.formatDistance(fromSurround[0]));
						if (fromSurround[0] != fromSurround[1]) explanation.append("/" + TextUtils.formatDistance(fromSurround[1]));
						explanation.append(" from rect " + TextUtils.formatDistance(lX) + "<=X<=" + TextUtils.formatDistance(hX) + " and " +
							TextUtils.formatDistance(lY) + "<=Y<=" + TextUtils.formatDistance(hY));
					}
				}
				return dx;
			}
			if (dy > 0)
			{
				dy = nr.downToGrain(hY - halfWidth) - curY;
				double initialDY = dy;
				if (fromTaperLen >= 0 && !sv.isOffInitialSegment() && dy > fromTaperLen) dy = fromTaperLen;
				if (curZ == toZ)
				{
					Double goalY = toPE.getGoalWithinY(curX, curY, curY+dy);
					if (goalY != null) dy = goalY.doubleValue() - curY;
				}
				Double goalY = toPE.getGoalAboveY(curX, curY+dy);
				if (goalY != null && curZ == toZ) dy = goalY.doubleValue() - curY; else
				{
					if (!toPE.atGoalPoint(curX, curY+dy))
						dy = nr.getLowerYGrid(curZ, curY+dy).getCoordinate() - curY;
					if (globalRoutingDelta != 0)
					{
						Rectangle2D limit = orderedBuckets[sv.globalRoutingBucket];
						if (curY + dy > limit.getMaxY())
						{
							Rectangle2D originalBucket = nr.buckets[sv.globalRoutingBucket];
							dy = limit.getMaxY() - curY;

							// if moved into a new bucket, center it
							if (curY >= originalBucket.getMinY() && curY >= originalBucket.getMaxY() && curY + dy > originalBucket.getMaxY())
								dy -= originalBucket.getHeight()/2;
							dy = nr.getClosestYGrid(curZ, curY+dy).getCoordinate() - curY;
						}
					}
					if (toPE.isOutOfGoalY(curY + dy)) dy = nr.downToGrainAlways(curY + dy) - curY;
				}
				if (explanation != null)
				{
					if (topY == null)
					{
						if (fromTaperLen >= 0 && !sv.isOffInitialSegment() && initialDY > fromTaperLen)
							explanation.append("went full taper distance of "+TextUtils.formatDistance(fromTaperLen)); else
								explanation.append("went to jump bound "+TextUtils.formatDistance(nr.jumpBound.getMinX())+"<=X<="+
									TextUtils.formatDistance(nr.jumpBound.getMaxX())+" and "+TextUtils.formatDistance(nr.jumpBound.getMinY())+"<=Y<="+
									TextUtils.formatDistance(nr.jumpBound.getMaxY()));
					} else
					{
						explanation.append("blocked by rect at " + TextUtils.formatDistance(topY.bound.getMinX()) + "<=X<=" +
							TextUtils.formatDistance(topY.bound.getMaxX()) + " and " +
							TextUtils.formatDistance(topY.bound.getMinY()) + "<=Y<=" + TextUtils.formatDistance(topY.bound.getMaxY()) +
							" on net " + topY.getNetID() + " which is " + TextUtils.formatDistance(fromSurround[0]));
						if (fromSurround[0] != fromSurround[1]) explanation.append("/" + TextUtils.formatDistance(fromSurround[1]));
						explanation.append(" from rect " + TextUtils.formatDistance(lX) + "<=X<=" + TextUtils.formatDistance(hX) + " and " +
							TextUtils.formatDistance(lY) + "<=Y<=" + TextUtils.formatDistance(hY));
					}
				}
				return dy;
			}
			if (dy < 0)
			{
				dy = nr.upToGrain(lY + halfWidth) - curY;
				double initialDY = dy;
				if (fromTaperLen >= 0 && !sv.isOffInitialSegment() && -dy > fromTaperLen) dy = -fromTaperLen;
				if (curZ == toZ)
				{
					Double goalY = toPE.getGoalWithinY(curX, curY+dy, curY);
					if (goalY != null) dy = goalY.doubleValue() - curY;
				}
				Double goalY = toPE.getGoalBelowY(curX, curY+dy);
				if (goalY != null && curZ == toZ) dy = goalY.doubleValue() - curY; else
				{
					if (!toPE.atGoalPoint(curX, curY+dy))
						dy = nr.getUpperYGrid(curZ, curY+dy).getCoordinate() - curY;
					if (globalRoutingDelta != 0)
					{
						Rectangle2D limit = orderedBuckets[sv.globalRoutingBucket];
						if (curY + dy < limit.getMinY())
						{
							Rectangle2D originalBucket = nr.buckets[sv.globalRoutingBucket];
							dy = limit.getMinY() - curY;

							// if moved into a new bucket, center it
							if (curY >= originalBucket.getMinY() && curY >= originalBucket.getMaxY() && curY + dy < originalBucket.getMinY())
								dy += originalBucket.getHeight()/2;
							dy = nr.getClosestYGrid(curZ, curY+dy).getCoordinate() - curY;
						}
					}
					if (toPE.isOutOfGoalY(curY + dy)) dy = nr.upToGrainAlways(curY + dy) - curY;
				}
				if (explanation != null)
				{
					if (botY == null)
					{
						if (fromTaperLen >= 0 && !sv.isOffInitialSegment() && -initialDY > fromTaperLen)
							explanation.append("went full taper distance of "+TextUtils.formatDistance(fromTaperLen)); else
								explanation.append("went to jump bound "+TextUtils.formatDistance(nr.jumpBound.getMinX())+"<=X<="+
									TextUtils.formatDistance(nr.jumpBound.getMaxX())+" and "+TextUtils.formatDistance(nr.jumpBound.getMinY())+"<=Y<="+
									TextUtils.formatDistance(nr.jumpBound.getMaxY()));
					} else
					{
						explanation.append("blocked by rect at " + TextUtils.formatDistance(botY.bound.getMinX()) + "<=X<=" +
							TextUtils.formatDistance(botY.bound.getMaxX()) + " and " +
							TextUtils.formatDistance(botY.bound.getMinY()) + "<=Y<=" + TextUtils.formatDistance(botY.bound.getMaxY()) +
							" on net " + botY.getNetID() + " which is " + TextUtils.formatDistance(fromSurround[0]));
						if (fromSurround[0] != fromSurround[1]) explanation.append("/" + TextUtils.formatDistance(fromSurround[1]));
						explanation.append(" from rect " + TextUtils.formatDistance(lX) + "<=X<=" + TextUtils.formatDistance(hX) + " and " +
							TextUtils.formatDistance(lY) + "<=Y<=" + TextUtils.formatDistance(hY));
					}
				}
				return dy;
			}
			return 0;
		}

		/**
		 * Method to see if a proposed piece of metal has DRC errors.
		 * @param metNo the level of the metal.
		 * @param halfWidth half of the width of the metal.
		 * @param halfHeight half of the height of the metal.
		 * @param x the X coordinate at the center of the metal.
		 * @param y the Y coordinate at the center of the metal.
		 * @param svCurrent the list of SearchVertex's for finding notch errors in the current path.
		 * @return a blocking SOGBound object that is in the area. Returns null if the area is clear.
		 */
		private SOGBound getMetalBlockageAndNotch(int metNo, int maskNo, double halfWidth, double halfHeight,
			double x, double y, SearchVertex svCurrent, boolean minArea, StringBuffer explaination)
		{
			// get the R-Tree data for the metal layer
			int maskIndex = maskNo;
			if (maskIndex > 0) maskIndex--;
			Layer layer = metalLayers[metNo][maskIndex];
			Layer primaryLayer = primaryMetalLayer[metNo];
			BlockageTree bTree = rTrees.getMetalTree(primaryLayer);
			bTree.lock();
			try {
				if (bTree.isEmpty()) return null;

				// determine the size and width/length of this piece of metal
				double metLX = x - halfWidth, metHX = x + halfWidth;
				double metLY = y - halfHeight, metHY = y + halfHeight;
				Rectangle2D metBound = new Rectangle2D.Double(metLX, metLY, metHX - metLX, metHY - metLY);
				double metWid = Math.min(halfWidth, halfHeight) * 2;
				double metLen = Math.max(halfWidth, halfHeight) * 2;

				// determine the area to search about the metal
				double surroundX = metalSurroundX[metNo], surroundY = metalSurroundY[metNo];
				double lX = metLX - surroundX, hX = metHX + surroundX;
				double lY = metLY - surroundY, hY = metHY + surroundY;
				Rectangle2D searchArea = new Rectangle2D.Double(lX, lY, hX - lX, hY - lY);

				// prepare for notch detection
				List<SOGBound> nodeRecsOnPath = new ArrayList<SOGBound>();
				List<SOGBound> recsOnPath = new ArrayList<SOGBound>();

				// make a list of rectangles on the path
				if (svCurrent != null)
				{
					getOptimizedList(svCurrent, optimizedList);
					for (int ind = 1; ind < optimizedList.size(); ind++)
					{
						SearchVertex sv = optimizedList.get(ind);
						SearchVertex lastSv = optimizedList.get(ind - 1);
						if (sv.getZ() != metNo && lastSv.getZ() != metNo) continue;
						if (sv.getZ() != lastSv.getZ())
						{
							// changed layers: compute via rectangles
							int metNum = Math.min(sv.getZ(), lastSv.getZ());
							List<MetalVia> nps = metalVias[metNum].getVias();
							if (nr.is2X(metNum, sv.getX(), sv.getY(), sv.getX(), sv.getY()) ||
								(metNum+1 < numMetalLayers && nr.is2X(metNum+1, sv.getX(), sv.getY(), sv.getX(), sv.getY())))
							{
								List<MetalVia> nps2X = metalVias2X[metNum].getVias();
								if (nps2X.size() > 0) nps = nps2X;
							}
							int whichContact = lastSv.getContactNo();
							MetalVia mv = nps.get(whichContact);
							PrimitiveNode np = mv.via;
							Orientation orient = Orientation.fromJava(mv.orientation * 10, false, false);
							SizeOffset so = np.getProtoSizeOffset();
							double xOffset = so.getLowXOffset() + so.getHighXOffset();
							double yOffset = so.getLowYOffset() + so.getHighYOffset();
							double wid = Math.max(np.getDefWidth(ep) - xOffset, nr.minWidth) + xOffset;
							double hei = Math.max(np.getDefHeight(ep) - yOffset, nr.minWidth) + yOffset;
							NodeInst ni = NodeInst.makeDummyInstance(np, ep, EPoint.fromLambda(sv.getX(), sv.getY()), wid, hei, orient);
							FixpTransform trans = null;
							if (orient != Orientation.IDENT) trans = ni.rotateOut();
							Poly[] polys = np.getTechnology().getShapeOfNode(ni);
							for (int i = 0; i < polys.length; i++)
							{
								Poly poly = polys[i];
								if (poly.getLayer() != layer) continue;
								if (trans != null) poly.transform(trans);
								Rectangle2D bound = poly.getBounds2D();
								if (bound.getMaxX() <= lX || bound.getMinX() >= hX || bound.getMaxY() <= lY || bound.getMinY() >= hY)
									continue;
								SOGBound bb = new SOGBound(ERectangle.fromLambda(bound), nr.netID, sv.getC());
								recsOnPath.add(bb);
								if (!minArea) nodeRecsOnPath.add(bb);
							}
							continue;
						}

						// stayed on one layer: compute arc rectangle
						double width = nr.getArcWidth(metNo, lastSv.getX(), lastSv.getY(), sv.getX(), sv.getY());
						Point2D head = new Point2D.Double(sv.getX(), sv.getY());
						Point2D tail = new Point2D.Double(lastSv.getX(), lastSv.getY());
						int ang = 0;
						if (head.getX() != tail.getX() || head.getY() != tail.getY())
							ang = GenMath.figureAngle(tail, head);
						Poly poly = Poly.makeEndPointPoly(head.distance(tail), width, ang, head, width / 2, tail,
							width / 2, Poly.Type.FILLED);
						Rectangle2D bound = poly.getBounds2D();
						if (bound.getMaxX() <= lX || bound.getMinX() >= hX || bound.getMaxY() <= lY || bound.getMinY() >= hY)
							continue;
						SOGBound bb = new SOGBound(ERectangle.fromLambda(bound), nr.netID, sv.getC());
						recsOnPath.add(bb);
						if (!minArea) nodeRecsOnPath.add(bb);
					}
				}

				SOGBound violation = null;
				for (Iterator<SOGBound> sea = bTree.search(searchArea); sea.hasNext(); )
				{
					SOGBound sBound = sea.next();
					ERectangle bound = sBound.getBounds();

					// eliminate if out of worst surround
					if (bound.getMaxX() <= lX || bound.getMinX() >= hX || bound.getMaxY() <= lY || bound.getMinY() >= hY)
						continue;

					// see if it is within design-rule distance
					double drWid = 0, drLen = 0;
					// TODO: determine proper width/length
//					if (metBound.getMinX() <= bound.getMaxX() && metBound.getMaxX() >= bound.getMinX())
//					{
//						// geometry is stacked vertically
//						drWid = Math.max(bound.getHeight(), metWid);
//						drLen = Math.max(bound.getWidth(), metLen);
//					} else if (metBound.getMinY() <= bound.getMaxY() && metBound.getMaxY() >= bound.getMinY())
//					{
//						// geometry is stacked horizontally
//						drWid = Math.max(bound.getWidth(), metWid);
//						drLen = Math.max(bound.getHeight(), metLen);
//					} else
					{
//						drWid = Math.max(Math.min(bound.getWidth(), bound.getHeight()), metWid);
//						drLen = Math.max(Math.max(bound.getWidth(), bound.getHeight()), metLen);
						drWid = Math.max(Math.min(bound.getWidth(), bound.getHeight()), Math.min(metWid, metLen));
						drLen = Math.max(Math.max(bound.getWidth(), bound.getHeight()), Math.max(metWid, metLen));
					}
					double[] spacing = nr.getSpacingRule(metNo, drWid, drLen);
					double lXAllow = metLX - spacing[0], hXAllow = metHX + spacing[0];
					double lYAllow = metLY - spacing[1], hYAllow = metHY + spacing[1];
					if (DBMath.isLessThanOrEqualTo(bound.getMaxX(), lXAllow) ||
						DBMath.isGreaterThanOrEqualTo(bound.getMinX(), hXAllow) ||
						DBMath.isLessThanOrEqualTo(bound.getMaxY(), lYAllow) ||
						DBMath.isGreaterThanOrEqualTo(bound.getMinY(), hYAllow)) continue;

					// too close for DRC: allow if on the same net
//					if (sBound.getMaskColor() == maskNo)
					{
						if (sBound.isSameBasicNet(nr.netID))
						{
							// only consider real blockages, not those set about endpoints to keep them clear
							boolean notPseudoBlockage = false;
							if (!sBound.isPseudoBlockage()) notPseudoBlockage = true;
							if (notPseudoBlockage)
							{
								// on same net: see if it completely covers this
								if (bound.getMinX() <= metLX && bound.getMaxX() >= metHX &&
									bound.getMinY() <= metLY && bound.getMaxY() >= metHY) return null;

								// make sure there is no notch error
								boolean notch = foundANotch(bTree, metBound, bound, nr.netID, recsOnPath, spacing);
								if (notch) violation = sBound;
							}
							continue;
						}
					}

					// if this is a polygon, do closer examination
					if (sBound instanceof SOGPoly)
					{
						PolyBase poly = ((SOGPoly) sBound).getPoly();
						Rectangle2D drcArea = new Rectangle2D.Double(lXAllow, lYAllow, hXAllow - lXAllow, hYAllow - lYAllow);
						if (!poly.contains(drcArea)) continue;
					}

					// DRC error found: return the offending geometry
					violation = sBound;
					if (explaination != null) explaination.append("spacing [X=" + TextUtils.formatDistance(spacing[0]) +
						", Y=" + TextUtils.formatDistance(spacing[1]) + "]");
					break;
				}
				if (violation != null) return violation;

				// consider notch errors in the existing path
				double[] spacing = nr.getSpacingRule(metNo, Math.min(metWid, metLen), Math.max(metWid, metLen));
				for(SOGBound sBound : nodeRecsOnPath)
				{
					if (foundANotch(bTree, metBound, sBound.getBounds(), nr.netID, recsOnPath, spacing))
					{
						if (explaination != null) explaination.append("notch");
						return sBound;
					}
				}
				return null;
			} finally {
				bTree.unlock();
			}
		}

		/**
		 * Method to tell whether there is a notch between two pieces of metal.
		 * @param bTree the BlocakgeTree with the metal information.
		 * @param metBound one piece of metal.
		 * @param bound another piece of metal.
		 * @return true if there is a notch error between the pieces of metal.
		 */
		private boolean foundANotch(BlockageTree bTree, Rectangle2D metBound, Rectangle2D bound, MutableInteger netID,
			List<SOGBound> recsOnPath, double[] dist)
		{
			// see if they overlap in X or Y
			boolean hOverlap = metBound.getMinX() <= bound.getMaxX() && metBound.getMaxX() >= bound.getMinX();
			boolean vOverlap = metBound.getMinY() <= bound.getMaxY() && metBound.getMaxY() >= bound.getMinY();

			// if they overlap in both, they touch and it is not a notch
			if (hOverlap && vOverlap) return false;

			// if they overlap horizontally then they line-up vertically
			if (hOverlap)
			{
				double ptY;
				if (metBound.getCenterY() > bound.getCenterY())
				{
					if (metBound.getMinY() - bound.getMaxY() > dist[1]) return false;
					ptY = (metBound.getMinY() + bound.getMaxY()) / 2;
				} else
				{
					if (bound.getMinY() - metBound.getMaxY() > dist[1]) return false;
					ptY = (metBound.getMaxY() + bound.getMinY()) / 2;
				}
				double pt1X = Math.max(metBound.getMinX(), bound.getMinX());
				double pt2X = Math.min(metBound.getMaxX(), bound.getMaxX());
				double pt3X = (pt1X + pt2X) / 2;
				if (!pointInRTree(bTree, pt1X, ptY, netID, recsOnPath)) return true;
				if (!pointInRTree(bTree, pt2X, ptY, netID, recsOnPath)) return true;
				if (!pointInRTree(bTree, pt3X, ptY, netID, recsOnPath)) return true;
				return false;
			}

			// if they overlap vertically then they line-up horizontally
			if (vOverlap)
			{
				double ptX;
				if (metBound.getCenterX() > bound.getCenterX())
				{
					if (metBound.getMinX() - bound.getMaxX() > dist[0]) return false;
					ptX = (metBound.getMinX() + bound.getMaxX()) / 2;
				} else
				{
					if (bound.getMinX() - metBound.getMaxX() > dist[0]) return false;
					ptX = (metBound.getMaxX() + bound.getMinX()) / 2;
				}
				double pt1Y = Math.max(metBound.getMinY(), bound.getMinY());
				double pt2Y = Math.min(metBound.getMaxY(), bound.getMaxY());
				double pt3Y = (pt1Y + pt2Y) / 2;
				if (!pointInRTree(bTree, ptX, pt1Y, netID, recsOnPath)) return true;
				if (!pointInRTree(bTree, ptX, pt2Y, netID, recsOnPath)) return true;
				if (!pointInRTree(bTree, ptX, pt3Y, netID, recsOnPath)) return true;
				return false;
			}

			// they are diagonal, ensure that one of the "L"s is filled
			if (metBound.getMinX() > bound.getMaxX() && metBound.getMinY() > bound.getMaxY())
			{
				// metal to upper-right of test area
				double pt1X = metBound.getMinX();
				double pt1Y = bound.getMaxY();
				double pt2X = bound.getMaxX();
				double pt2Y = metBound.getMinY();
				if (Math.sqrt((pt1X - pt2X) * (pt1X - pt2X) + (pt1Y - pt2Y) * (pt1Y - pt2Y)) > Math.max(dist[0], dist[1])) return false;
				if (pointInRTree(bTree, pt1X, pt1Y, netID, recsOnPath)) return false;
				if (pointInRTree(bTree, pt2X, pt2Y, netID, recsOnPath)) return false;
				return true;
			}
			if (metBound.getMaxX() < bound.getMinX() && metBound.getMinY() > bound.getMaxY())
			{
				// metal to upper-left of test area
				double pt1X = metBound.getMaxX();
				double pt1Y = bound.getMaxY();
				double pt2X = bound.getMinX();
				double pt2Y = metBound.getMinY();
				if (Math.sqrt((pt1X - pt2X) * (pt1X - pt2X) + (pt1Y - pt2Y) * (pt1Y - pt2Y)) > Math.max(dist[0], dist[1])) return false;
				if (pointInRTree(bTree, pt1X, pt1Y, netID, recsOnPath)) return false;
				if (pointInRTree(bTree, pt2X, pt2Y, netID, recsOnPath)) return false;
				return true;
			}
			if (metBound.getMaxX() < bound.getMinX() && metBound.getMaxY() < bound.getMinY())
			{
				// metal to lower-left of test area
				double pt1X = metBound.getMaxX();
				double pt1Y = bound.getMinY();
				double pt2X = bound.getMinX();
				double pt2Y = metBound.getMaxY();
				if (Math.sqrt((pt1X - pt2X) * (pt1X - pt2X) + (pt1Y - pt2Y) * (pt1Y - pt2Y)) > Math.max(dist[0], dist[1])) return false;
				if (pointInRTree(bTree, pt1X, pt1Y, netID, recsOnPath)) return false;
				if (pointInRTree(bTree, pt2X, pt2Y, netID, recsOnPath)) return false;
				return true;
			}
			if (metBound.getMinX() > bound.getMaxX() && metBound.getMaxY() < bound.getMinY())
			{
				// metal to lower-right of test area
				double pt1X = metBound.getMinX();
				double pt1Y = bound.getMinY();
				double pt2X = bound.getMaxX();
				double pt2Y = metBound.getMaxY();
				if (Math.sqrt((pt1X - pt2X) * (pt1X - pt2X) + (pt1Y - pt2Y) * (pt1Y - pt2Y)) > Math.max(dist[0], dist[1])) return false;
				if (pointInRTree(bTree, pt1X, pt1Y, netID, recsOnPath)) return false;
				if (pointInRTree(bTree, pt2X, pt2Y, netID, recsOnPath)) return false;
				return true;
			}
			return false;
		}

		private boolean pointInRTree(BlockageTree bTree, double x, double y, MutableInteger netID, List<SOGBound> recsOnPath)
		{
			Rectangle2D searchArea = new Rectangle2D.Double(x-0.5, y-0.5, 1, 1);
			for (Iterator<SOGBound> sea = bTree.search(searchArea); sea.hasNext(); )
			{
				SOGBound sBound = sea.next();
				if (!sBound.isSameBasicNet(netID)) continue;
				if (DBMath.isGreaterThan(sBound.getBounds().getMinX(), x) ||
					DBMath.isLessThan(sBound.getBounds().getMaxX(), x) ||
					DBMath.isGreaterThan(sBound.getBounds().getMinY(), y) ||
					DBMath.isLessThan(sBound.getBounds().getMaxY(), y))
						continue;
				return true;
			}

			// now see if it is on the path
			for (SOGBound bound : recsOnPath)
			{
				if (DBMath.isGreaterThan(bound.getBounds().getMinX(), x) ||
					DBMath.isLessThan(bound.getBounds().getMaxX(), x) ||
					DBMath.isGreaterThan(bound.getBounds().getMinY(), y) ||
					DBMath.isLessThan(bound.getBounds().getMaxY(), y))
						continue;
				return true;
			}
			return false;
		}
	}

	/************************************** SEARCH VERTICES **************************************/

	public static class OrderedSearchVertex
	{
		TreeMap<Integer,List<SearchVertex>> listBetter;

		OrderedSearchVertex()
		{
			listBetter = new TreeMap<Integer,List<SearchVertex>>();
		}

		public Set<SearchVertex> getSet()
		{
			Set<SearchVertex> totalList = new TreeSet<SearchVertex>();
			for(Integer key : listBetter.keySet())
			{
				List<SearchVertex> curList = listBetter.get(key);
				for(SearchVertex sv : curList) totalList.add(sv);
			}
			return totalList;
		}

		public void add(SearchVertex sv)
		{
			Integer key = Integer.valueOf(sv.cost);
			List<SearchVertex> curList = listBetter.get(key);
			if (curList == null) listBetter.put(key, curList = new ArrayList<SearchVertex>());
			curList.add(sv);
		}

		public void remove(SearchVertex sv)
		{
			Integer key = Integer.valueOf(sv.cost);
			List<SearchVertex> curList = listBetter.get(key);
			if (curList != null)
			{
				curList.remove(sv);
				if (curList.size() == 0) listBetter.remove(key);
			} else
				System.out.println("++++++++++ COULD NOT REMOVE SEARCH VERTEX");
		}

		public boolean inList(SearchVertex sv)
		{
			Integer key = Integer.valueOf(sv.cost);
			List<SearchVertex> curList = listBetter.get(key);
			if (curList == null) return false;
			return curList.contains(sv);
		}

		public SearchVertex getFirst()
		{
			if (listBetter.size() == 0) return null;
			Integer key = listBetter.firstKey();
			List<SearchVertex> curList = listBetter.get(key);
			if (curList.size() == 0) System.out.println("+++++++++++++ HMMM, FIRST KEY HAS NOTHING ("+key+")");
			return curList.get(0);
		}
	}

	/**
	 * Class to store additional geometry needed by SearchVertex contacts to ensure minimum area.
	 */
	public static class SearchVertexAddon
	{
		private Rectangle2D[] addedGeometry;
		private PrimitiveNode pureLayerNode;

		public SearchVertexAddon(Rectangle2D geom1, Rectangle2D geom2, PrimitiveNode pNp)
		{
			if (geom2 == null)
			{
				addedGeometry = new Rectangle2D[1];
				addedGeometry[0] = geom1;
			} else
			{
				addedGeometry = new Rectangle2D[2];
				addedGeometry[0] = geom1;
				addedGeometry[1] = geom2;
			}
			pureLayerNode = pNp;
		}

		public Rectangle2D[] getGeometry() { return addedGeometry; }

		public PrimitiveNode getPureLayerNode() { return pureLayerNode; }
	}

	/**
	 * Class to define a vertex in the Dijkstra search.
	 */
	public static class SearchVertex implements Comparable<SearchVertex>
	{
		private static final int OFFINITIALSEGMENT = 1;
		private static final int TOOFARFROMEND = 2;
		private static final int CLOSETOEND = 4;

		/** the coordinate of the search vertex. */		private final double xv, yv;
		/** the layer of the search vertex. */			private int zv, cv;
		/** the cost of search to this vertex. */		private int cost;
		/** the layer of cuts in "cuts". */				private int cutLayer;
		/** auto-generation of intermediate vertices */	private int autoGen;
		/** the state of this vertex. */				private int flags;
		/** index of global-routing bucket progress */	private int globalRoutingBucket;
		/** the cuts in the contact. */					private Poly[] cutPolys;
		/** the size of the contact. */					private Point2D size;
		/** the previous vertex in the search. */		private SearchVertex last;
		/** the routing state. */						private Wavefront wf;
		/** added geometry for minimum area. */			private SearchVertexAddon addOn;

		/**
		 * Method to create a new SearchVertex.
		 * @param x the X coordinate of the SearchVertex.
		 * @param y the Y coordinate of the SearchVertex.
		 * @param z the Z coordinate (metal layer) of the SearchVertex.
		 * @param whichContact the contact number to use if switching layers.
		 * @param conPolys an array of cuts in this contact (if switching layers).
		 * @param cl the layer of the cut (if switching layers).
		 * @param w the Wavefront that this SearchVertex is part of.
		 */
		SearchVertex(double x, double y, int z, int c, int whichContact, Poly[] conPolys, Point2D size, int cl, Wavefront w, int flags, SearchVertexAddon minareaGeom)
		{
			xv = x;
			yv = y;
			zv = (z << 8) + (whichContact & 0xFF);
			cv = c;
			this.cutPolys = conPolys;
			this.size = size;
			cutLayer = cl;
			autoGen = -1;
			this.flags = flags;
			globalRoutingBucket = -1;
			wf = w;
			addOn = minareaGeom;
		}

		public SearchVertex(SearchVertex sv)
		{
			xv = sv.xv;
			yv = sv.yv;
			zv = sv.zv;
			cv = sv.cv;
			cost = sv.cost;
			cutLayer = sv.cutLayer;
			autoGen = sv.autoGen;
			flags = sv.flags;
			globalRoutingBucket = sv.globalRoutingBucket;
			cutPolys = sv.cutPolys;
			size = sv.size;
			last = sv.last;
			wf = sv.wf;
			addOn = sv.addOn;
		}

		public double getX() { return xv; }

		public double getY() { return yv; }

		public int getZ() { return zv >> 8; }

		public String describeMetal()
		{
			String ret = "M" + (getZ()+1);
			if (cv > 0) ret += (char)('a' + cv - 1);
			return ret;
		}

		public int getC() { return cv; }

		public SearchVertex getLast() { return last; }

		public int getCost() { return cost; }

		public SearchVertexAddon getMoreGeometry() { return addOn; }

		public int getGRBucket() { return globalRoutingBucket; }

		public Wavefront getWavefront() { return wf; }

		int getContactNo() { return zv & 0xFF; }

		Poly[] getCutPolys() { return cutPolys; }

		Point2D getSize() { return size; }

		int getCutLayer() { return cutLayer; }

		int getAutoGen() { return autoGen; }

		/**
		 * Method to set the automatic generation of intermediate SearchVertex objects.
		 * When a SearchVertex is placed at a great distance from the previous one,
		 * it may have missed intermediate steps.
		 * The AutoGen value directs creation of an intermediate step.
		 * @param a negative to ignore, otherwise is the direction index to automatically generate intermediate steps.
		 */
		void setAutoGen(int a) { autoGen = a; }

		/**
		 * Method to tell whether this SearchVertex is off the initial segment.
		 * This is determined by whether a layer change has occurred.
		 * @return true if this SearchVertex is on the initial segment.
		 */
		public boolean isOffInitialSegment() { return (flags&OFFINITIALSEGMENT) != 0; }

		/**
		 * Method to tell whether this SearchVertex cannot connect to the end of the route.
		 * If it switched to a taper layer but did it too far from the end,
		 * it cannot finish the route.
		 * @return true if this SearchVertex cannot connect to the end of the route.
		 */
		public boolean isCantCompleteRoute() { return (flags&TOOFARFROMEND) != 0; }

		/**
		 * Method to tell whether this SearchVertex must connect to the end of the route.
		 * If it switched to a taper layer close to the end,
		 * it has taper size and must finish the route.
		 * @return true if this SearchVertex must connect to the end of the route.
		 */
		public boolean isMustCompleteRoute() { return (flags&CLOSETOEND) != 0; }

		/**
		 * Method to sort SearchVertex objects by their cost.
		 */
		public int compareTo(SearchVertex svo)
		{
			int diff = cost - svo.cost;
			if (diff != 0) return diff;
			if (wf != null)
			{
				double thisDist = Math.abs(xv - wf.toPE.getCenterX()) + Math.abs(yv - wf.toPE.getCenterY()) + Math.abs(getZ() - wf.toZ);
				double otherDist = Math.abs(svo.xv - wf.toPE.getCenterX()) + Math.abs(svo.yv - wf.toPE.getCenterY()) + Math.abs(svo.getZ() - wf.toZ);
				if (thisDist < otherDist) return -1;
				if (thisDist > otherDist) return 1;
			}
			return 0;
		}

		private void generateIntermediateVertex(int lastDirection, FixpRectangle toRectGridded, Cell cell)
		{
			NeededRoute nr = wf.nr;
			SearchVertex prevSV = last;
			double dX = 0, dY = 0;
			if (getX() > prevSV.getX()) dX = -1; else
				if (getX() < prevSV.getX()) dX = 1; else
					if (getY() > prevSV.getY()) dY = -1; else
						if (getY() < prevSV.getY()) dY = 1;
			if (dX == 0 && dY == 0) return;
			int z = getZ();
			int c = getC();
			double newX = getX();
			double newY = getY();

			for(;;)
			{
				if (dX < 0)
				{
					if (nr.forceGridArcs[z])
					{
						newX = nr.getLowerXGrid(z, newX-GRAINSIZE).getCoordinate();
					} else
					{
						newX -= GRAINSIZE;
						if (!inDestGrid(toRectGridded, newX, newY)) newX = nr.getLowerXGrid(z, newX).getCoordinate();
					}
				}
				if (dX > 0)
				{
					if (nr.forceGridArcs[z])
					{
						newX = nr.getUpperXGrid(z, newX+GRAINSIZE).getCoordinate();
					} else
					{
						newX += GRAINSIZE;
						if (!inDestGrid(toRectGridded, newX, newY)) newX = nr.getUpperXGrid(z, newX).getCoordinate();
					}
				}
				if (dY < 0)
				{
					if (nr.forceGridArcs[z])
					{
						newY = nr.getLowerYGrid(z, newY-GRAINSIZE).getCoordinate();
					} else
					{
						newY -= GRAINSIZE;
						if (!inDestGrid(toRectGridded, newX, newY)) newY = nr.getLowerYGrid(z, newY).getCoordinate();
					}
				}
				if (dY > 0)
				{
					if (nr.forceGridArcs[z])
					{
						newY = nr.getUpperYGrid(z, newY+GRAINSIZE).getCoordinate();
					} else
					{
						newY += GRAINSIZE;
						if (!inDestGrid(toRectGridded, newX, newY)) newY = nr.getUpperYGrid(z, newY).getCoordinate();
					}
				}
				if (dX < 0 && newX <= prevSV.getX()) break;
				if (dX > 0 && newX >= prevSV.getX()) break;
				if (dY < 0 && newY <= prevSV.getY()) break;
				if (dY > 0 && newY >= prevSV.getY()) break;
				if (wf.getVertex(newX, newY, z) == null)
				{
					SearchVertex svIntermediate = new SearchVertex(newX, newY, z, c, getContactNo(), getCutPolys(), getSize(), z, wf, last.flags, null);
					if (wf.debuggingWavefront) RoutingDebug.ensureDebuggingShadow(svIntermediate, false);
					if (wf.globalRoutingDelta != 0)
						svIntermediate.globalRoutingBucket = wf.getNextBucket(this, newX, newY);
					svIntermediate.setAutoGen(lastDirection);
					svIntermediate.last = prevSV;
					svIntermediate.cost = cost + 1;
					wf.setVertex(newX, newY, z, svIntermediate);
					wf.active.add(svIntermediate);
					break;
				}
			}
		}
	}

	/********************************* INITIALIZATION *********************************/

	/**
	 * Method to initialize technology information, including design rules.
	 * @return true on error.
	 */
	private boolean initializeDesignRules()
	{
		// find the number of metal layers
		numMetalLayers = 0;
		for(Iterator<Layer> it = tech.getLayers(); it.hasNext(); )
		{
			Layer lay = it.next();
			if (!lay.getFunction().isMetal()) continue;
			int metNum = lay.getFunction().getLevel();
			numMetalLayers = Math.max(metNum, numMetalLayers);
		}
		for(Iterator<ArcProto> it = tech.getArcs(); it.hasNext(); )
		{
			ArcProto ap = it.next();
			if (!ap.getFunction().isMetal()) continue;
			int metNum = ap.getFunction().getLevel();
			numMetalLayers = Math.max(metNum, numMetalLayers);
		}

		// load up the metal layers
		metalLayers = new Layer[numMetalLayers][];
		primaryMetalLayer = new Layer[numMetalLayers];
		List<Layer>[] tempLayerList = new List[numMetalLayers];
		for(int i=0; i<numMetalLayers; i++) tempLayerList[i] = new ArrayList<Layer>();
		for(Iterator<Layer> it = tech.getLayers(); it.hasNext(); )
		{
			Layer lay = it.next();
			if (!lay.getFunction().isMetal()) continue;
			int metNum = lay.getFunction().getLevel() - 1;
			tempLayerList[metNum].add(lay);
		}
		for(int i=0; i<numMetalLayers; i++)
		{
			primaryMetalLayer[i] = null;
			Collections.sort(tempLayerList[i], new SortLayersByMaskNumber());
			if (tempLayerList[i].size() > 1)
			{
				// multiple layers for this metal: see if an uncolored layer is mixed with colored ones
				Layer first = tempLayerList[i].get(0);
				int colorNum = first.getFunction().getMaskColor();
				if (colorNum == 0) { primaryMetalLayer[i] = tempLayerList[i].get(0);  tempLayerList[i].remove(0); }
			}
			metalLayers[i] = new Layer[tempLayerList[i].size()];
			for(int c=0; c<tempLayerList[i].size(); c++) metalLayers[i][c] = tempLayerList[i].get(c);
			if (primaryMetalLayer[i] == null) primaryMetalLayer[i] = metalLayers[i][0];
		}

		// load up the metal arcs
		metalArcs = new ArcProto[numMetalLayers][];
		metalPureLayerNodes = new PrimitiveNode[numMetalLayers][];
		primaryMetalArc = new ArcProto[numMetalLayers];
		size2X = new double[numMetalLayers];
		taperLength = new double[numMetalLayers];
		boolean hasFavorites = false;
		favorArcs = new boolean[numMetalLayers];
		preventArcs = new boolean[numMetalLayers];
		taperOnlyArcs = new boolean[numMetalLayers];
		for(int i=0; i<numMetalLayers; i++)
			favorArcs[i] = preventArcs[i] = taperOnlyArcs[i] = false;
		List<ArcProto>[] tempArcList = new List[numMetalLayers];
		for(int i=0; i<numMetalLayers; i++) tempArcList[i] = new ArrayList<ArcProto>();
		for(Iterator<ArcProto> it = tech.getArcs(); it.hasNext(); )
		{
			ArcProto ap = it.next();
			if (!ap.getFunction().isMetal()) continue;
			int metNum = ap.getFunction().getLevel() - 1;
			tempArcList[metNum].add(ap);
		}
		for(int i=0; i<numMetalLayers; i++)
		{
			primaryMetalArc[i] = null;
			Collections.sort(tempArcList[i], new SortArcsByMaskNumber());
			if (tempArcList[i].size() > 1)
			{
				// multiple layers for this metal: see if an uncolored layer is mixed with colored ones
				ArcProto first = tempArcList[i].get(0);
				int colorNum = first.getMaskLayer();
				if (colorNum == 0) { primaryMetalArc[i] = tempArcList[i].get(0);  tempArcList[i].remove(0); }
			}
			metalArcs[i] = new ArcProto[tempArcList[i].size()];
			metalPureLayerNodes[i] = new PrimitiveNode[tempArcList[i].size()];
			for(int c=0; c<tempArcList[i].size(); c++)
			{
				ArcProto ap = tempArcList[i].get(c);
				int metNum = ap.getFunction().getLevel() - 1;
				metalArcs[i][c] = ap;
				for(Iterator<PrimitiveNode> it = tech.getNodes(); it.hasNext(); )
				{
					PrimitiveNode pNp = it.next();
					if (pNp.getFunction() != PrimitiveNode.Function.NODE) continue;
					for(Iterator<Layer> lIt = pNp.getLayerIterator(); lIt.hasNext(); )
					{
						Layer lay = lIt.next();
						if (!lay.getFunction().isMetal()) continue;
						if (lay.getFunction().getLevel()-1 != metNum) continue;
						if (lay.getFunction().getMaskColor() != ap.getMaskLayer()) continue;
						metalPureLayerNodes[i][c] = pNp;
						break;
					}
					if (metalPureLayerNodes[i][c] != null) break;
				}
				if (sogp.isFavored(ap))
				{
					favorArcs[metNum] = true;
					hasFavorites = true;
				}
				if (sogp.isPrevented(ap)) preventArcs[metNum] = true;
				if (sogp.isTaperOnly(ap)) taperOnlyArcs[metNum] = true;
			}
			if (primaryMetalArc[i] == null) primaryMetalArc[i] = metalArcs[i][0];

			Double d = sogp.get2XWidth(primaryMetalArc[i]);
			if (d == null) size2X[i] = 0; else size2X[i] = d.doubleValue();
			d = sogp.getTaperLength(primaryMetalArc[i]);
			if (d == null) taperLength[i] = -1; else taperLength[i] = d.doubleValue();
		}
		if (!hasFavorites)
			for (int i = 0; i < numMetalLayers; i++) favorArcs[i] = true;

		// do error checking
		boolean failure = false;
		for(int i=0; i<numMetalLayers; i++)
		{
			if (metalLayers[i].length == 0)
			{
				error("Cannot find layer for Metal " + (i+1) + " in technology " + tech.getTechName());
				failure = true;
			}
			if (metalArcs[i].length == 0)
			{
				error("Cannot find arc for Metal " + (i+1) + " in technology " + tech.getTechName());
				failure = true;
			}
			for(int c=0; c<metalPureLayerNodes[i].length; c++)
			{
				if (metalPureLayerNodes[i][c] == null)
				{
					error("Cannot find pure-layer node for Metal " + (i+1) + " color " + (c+1) + " in technology " + tech.getTechName());
					failure = true;
				}
			}
			if (metalLayers[i].length != metalArcs[i].length)
			{
				String layMsg = "";
				for(int c=0; c<metalLayers[i].length; c++)
				{
					if (layMsg.length() > 0) layMsg += ", ";
					layMsg += metalLayers[i][c].getName();
				}
				String arcMsg = "";
				for(int c=0; c<metalArcs[i].length; c++)
				{
					if (arcMsg.length() > 0) arcMsg += ", ";
					arcMsg += metalArcs[i][c].getName();
				}
				warn("Metal " + (i+1) + " in technology " + tech.getTechName() + " has " + metalLayers[i].length +
					" mask layers (" + layMsg + ") but " + metalArcs[i].length + " mask arcs (" + arcMsg + "). Making them equal length.");
				if (metalLayers[i].length > metalArcs[i].length)
				{
					Layer[] newMetalLayers = new Layer[metalArcs[i].length];
					for(int c=0; c<newMetalLayers.length; c++) newMetalLayers[c] = metalLayers[i][c];
					metalLayers[i] = newMetalLayers;
				} else
				{
					ArcProto[] newMetalArcs = new ArcProto[metalLayers[i].length];
					for(int c=0; c<newMetalArcs.length; c++) newMetalArcs[c] = metalArcs[i][c];
					metalArcs[i] = newMetalArcs;
				}
			}
			if (metalLayers[i].length == metalArcs[i].length)
			{
				boolean badOrder = false;
				String layMsg = "", arcMsg = "";
				for(int c=0; c<metalLayers[i].length; c++)
				{
					int layMask = metalLayers[i][c].getFunction().getMaskColor();
					int arcMask = metalArcs[i][c].getMaskLayer();
					if (layMsg.length() > 0) layMsg += ", ";
					layMsg += layMask;
					if (arcMsg.length() > 0) arcMsg += ", ";
					arcMsg += arcMask;
					if (layMask != arcMask) badOrder = true;
				}
				if (badOrder)
				{
					error("Metal " + (i+1) + " in technology " + tech.getTechName() + " has layer masks " + layMsg + " but arc masks " + arcMsg);
					failure = true;
				}
			}
		}
		if (failure) return true;

		// find the layers that remove metal
		removeLayers = new HashMap<Layer,Layer>();
		for(int i=0; i<numMetalLayers; i++)
		{
			for(int j=0; j<metalLayers[i].length; j++)
			{
				Layer metLay = metalLayers[i][j];
				ArcProto metAp = metalArcs[i][j];
				String removeLayerName = sogp.getRemoveLayer(metAp);
				if (removeLayerName == null) continue;
				Layer removeLay = tech.findLayer(removeLayerName);
				if (removeLay == null) System.out.println("WARNING: Unknown removal layer: " + removeLayerName); else
					removeLayers.put(removeLay, metLay);
			}
		}

		// now gather the via information
		viaLayers = new Layer[numMetalLayers - 1];
		metalVias = new MetalVias[numMetalLayers - 1];
		for (int i = 0; i < numMetalLayers - 1; i++)
			metalVias[i] = new MetalVias();
		metalVias2X = new MetalVias[numMetalLayers - 1];
		for (int i = 0; i < numMetalLayers - 1; i++)
			metalVias2X[i] = new MetalVias();

		List<Layer>[] tempViaLayerList = new List[numMetalLayers-1];
		for(int i=0; i<numMetalLayers-1; i++) tempViaLayerList[i] = new ArrayList<Layer>();
		for(Iterator<Layer> it = tech.getLayers(); it.hasNext(); )
		{
			Layer lay = it.next();
			if (!lay.getFunction().isContact()) continue;
			int viaNum = lay.getFunction().getLevel() - 2;
			if (viaNum >= 0) tempViaLayerList[viaNum].add(lay);
		}
		for(int i=0; i<numMetalLayers-1; i++)
		{
			Collections.sort(tempViaLayerList[i], new SortLayersByMaskNumber());
			viaLayers[i] = tempViaLayerList[i].get(0);
		}

		// regular expression to discard some primitives
		String ignorePattern = sogp.getIgnorePrimitives();		// "Z-(\\w+)"
		String accept1XPattern = sogp.getAcceptOnly1XPrimitives();	// "X-(\\w+)"
		String accept2XPattern = sogp.getAcceptOnly2XPrimitives();  // "R-(\\w+)"
		boolean contactsCanRotate = sogp.isContactsRotate();
		Pattern ignorePat = (ignorePattern != null) ? Pattern.compile(ignorePattern) : null;
		Pattern acceptPat1X = (accept1XPattern != null) ? Pattern.compile(accept1XPattern) : null;
		Pattern acceptPat2X = (accept2XPattern != null) ? Pattern.compile(accept2XPattern) : null;
		for (Iterator<PrimitiveNode> it = tech.getNodes(); it.hasNext();)
		{
			PrimitiveNode np = it.next();
			if (np.isNotUsed()) continue;
			if (!np.getFunction().isContact()) continue;

			if (ignorePat != null)
			{
				// Match found and therefore not using this primitive
				Matcher matcher = ignorePat.matcher(np.getName());
				if (matcher.find()) continue;
			}

			// see if this is a 1X or 2X contact
			boolean is1XContact = false, is2XContact = false;
			if (acceptPat1X == null) is1XContact = true; else
			{
				Matcher matcher = acceptPat1X.matcher(np.getName());
				is1XContact = matcher.find();
			}
			if (acceptPat2X == null) is2XContact = true; else
			{
				Matcher matcher = acceptPat2X.matcher(np.getName());
				is2XContact = matcher.find();
			}
			if (is1XContact || is2XContact)
			{
				MetalVias[] whichOnes = is1XContact ? metalVias : metalVias2X;
				ArcProto[] conns = np.getPort(0).getConnections();
				for (int i = 0; i < numMetalLayers - 1; i++)
				{
					if ((isOnMetalArc(i, conns[0]) && isOnMetalArc(i+1, conns[1])) ||
						(isOnMetalArc(i, conns[1]) && isOnMetalArc(i+1, conns[0])))
					{
						// see if the node is asymmetric and should exist in rotated states
						boolean square = true, offCenter = false;
						NodeInst dummyNi = NodeInst.makeDummyInstance(np, ep);
						Poly[] conPolys = tech.getShapeOfNode(dummyNi);
						int horMet = -1, verMet = -1;
						double horMetInset = 0, verMetInset = 0;
						int horMetColor = 0, verMetColor = 0;
						for (int p = 0; p < conPolys.length; p++)
						{
							Poly conPoly = conPolys[p];
							Layer conLayer = conPoly.getLayer();
							Layer.Function lFun = conLayer.getFunction();
							if (lFun.isMetal())
							{
								Rectangle2D conRect = conPoly.getBounds2D();
								if (conRect.getWidth() != conRect.getHeight())
								{
									square = false;
									if (conRect.getWidth() > conRect.getHeight())
									{
										// horizontal piece
										horMet = lFun.getLevel() - 1;
										horMetColor = lFun.getMaskColor();
										horMetInset = dummyNi.getYSize() - conRect.getHeight();
									} else
									{
										// vertical piece
										verMet = lFun.getLevel() - 1;
										verMetColor = lFun.getMaskColor();
										verMetInset = dummyNi.getXSize() - conRect.getWidth();
									}
								}
								if (conRect.getCenterX() != 0 || conRect.getCenterY() != 0) offCenter = true;
							}
						}

						if (square || offCenter) horMet = verMet = -1; else
							if (horMet < 0 || verMet < 0) horMet = verMet = -1;
						whichOnes[i].addVia(np, 0, horMet, horMetColor, horMetInset, verMet, verMetColor, verMetInset);
						if (contactsCanRotate)
						{
							if (offCenter)
							{
								// off center: test in all 4 rotations
								whichOnes[i].addVia(np, 90, horMet, horMetColor, horMetInset, verMet, verMetColor, verMetInset);
								whichOnes[i].addVia(np, 180, horMet, horMetColor, horMetInset, verMet, verMetColor, verMetInset);
								whichOnes[i].addVia(np, 270, horMet, horMetColor, horMetInset, verMet, verMetColor, verMetInset);
							} else if (!square)
							{
								// centered but not square: test in 90-degree rotation
								whichOnes[i].addVia(np, 90, verMet, verMetColor, verMetInset, horMet, horMetColor, horMetInset);
							}
						}
						break;
					}
				}
			}
		}
		for (int i = 0; i < numMetalLayers-1; i++)
		{
			// check that the vias span all of the masks
			if (metalArcs[i].length > 1 || metalArcs[i+1].length > 1)
			{
				boolean[][] masks = new boolean[metalArcs[i].length][metalArcs[i+1].length];
				for(int c1=0; c1<metalArcs[i].length; c1++)
					for(int c2=0; c2<metalArcs[i+1].length; c2++) masks[c1][c2] = false;
				List<MetalVia> allContacts = new ArrayList<MetalVia>();
				for(MetalVia mv : metalVias[i].getVias())
					allContacts.add(mv);
				for(MetalVia mv : metalVias2X[i].getVias())
					allContacts.add(mv);
				for(MetalVia mv : allContacts)
				{
					int met1 = -1, met1c = -1, met2 = -1, met2c = -1;
					for(NodeLayer nl : mv.via.getNodeLayers())
					{
						Layer layer = nl.getLayer();
						if (layer.getFunction().isMetal())
						{
							if (met1 < 0)
							{
								met1 = layer.getFunction().getLevel();
								met1c = layer.getFunction().getMaskColor();
							} else
							{
								met2 = layer.getFunction().getLevel();
								met2c = layer.getFunction().getMaskColor();
							}
						}
					}
					if (met1 < 0 || met2 < 0) continue;
					if (met1 != i+1 || met2 != i+2)
					{
						warn("Contact " + mv.via.getName() + " in technology " + tech.getTechName() +
							" bridges metals " + (i+1) + " and " + (i+2) + " but mentions different metals in its name");
						continue;
					}
					if (met1c == 0)
					{
						if (metalArcs[i].length > 1)
							warn("Contact " + mv.via.getName() + " in technology " + tech.getTechName() +
								" does not specify mask for metal " + (i+1));
						met1c = 1;
					}
					if (met2c == 0)
					{
						if (metalArcs[i+1].length > 1)
							warn("Contact " + mv.via.getName() + " in technology " + tech.getTechName() +
								" does not specify mask for metal " + (i+2));
						met2c = 1;
					}
					masks[met1c-1][met2c-1] = true;
				}
				for(int c1=0; c1<metalArcs[i].length; c1++)
				{
					for(int c2=0; c2<metalArcs[i+1].length; c2++)
					{
						if (masks[c1][c2]) continue;
						warn("No metal-" + (i+1) + " to metal-" + (i+2) + " contact in technology " + tech.getTechName() +
							" that bridges metal-" + (i+1) + "/mask-" + (c1+1) + " and metal-" + (i+2) + "/mask-" + (c2+1));
					}
				}
			}

			boolean noContact = false;
			if (metalVias[i].getVias().size() == 0)
			{
				warn("Cannot find contact node between Metal " + (i+1) + " and Metal " + (i+2) + " in technology " + tech.getTechName() +
					". Ignoring Metal layers " + (i+2) + " and higher.");
				noContact = true;
			} else if (viaLayers[i] == null)
			{
				warn("Cannot find contact layer between Metal " + (i+1) + " and Metal " + (i+2) + " in technology " + tech.getTechName() +
					". Ignoring Metal layers " + (i+2) + " and higher.");
				noContact = true;
			}
			if (noContact)
			{
				for(int j=i+1; j<numMetalLayers; j++)
					preventArcs[j] = true;
				break;
			}
		}

		// compute design rule spacings
		DRC.DRCPreferences dp = new DRC.DRCPreferences(false);
		minResolution = dp.getResolution(tech).getLambda();
		layerSurround = new Map[numMetalLayers];
		for (int i = 0; i < numMetalLayers; i++)
			layerSurround[i] = new HashMap<Double, Map<Double, double[]>>();
		metalSurroundX = new double[numMetalLayers];
		metalSurroundY = new double[numMetalLayers];
		maxDefArcWidth = new double[numMetalLayers];
		minimumArea = new double[numMetalLayers];
		MutableDouble mutableDist = new MutableDouble(0);
		for (int i = 0; i < numMetalLayers; i++)
		{
			if (metalLayers[i] == null) continue;

			// get minimum area rule
			minimumArea[i] = 0;
			DRCTemplate minAreaRule = DRC.getMinValue(primaryMetalLayer[i], DRCTemplate.DRCRuleType.MINAREA);
			if (minAreaRule != null)
				minimumArea[i] = minAreaRule.getValue(0);

			// get surrounds
			metalSurroundX[i] = metalSurroundY[i] = maxDefArcWidth[i] = 0;
			for(int c=0; c<metalArcs[i].length; c++)
			{
				maxDefArcWidth[i] = Math.max(maxDefArcWidth[i], metalArcs[i][c].getDefaultLambdaBaseWidth(ep));
				DRCTemplate rule = DRC.getSpacingRule(metalLayers[i][c], null, metalLayers[i][c], null, false, -1,
					metalArcs[i][c].getDefaultLambdaBaseWidth(ep), 50);
				if (rule != null)
				{
					metalSurroundX[i] = Math.max(metalSurroundX[i], rule.getValue(0));
					if (rule.getNumValues() > 1)
					{
						metalSurroundY[i] = Math.max(metalSurroundY[i], rule.getValue(1));
					} else
					{
						metalSurroundY[i] = metalSurroundX[i];
					}
				}
				if (DRC.getMaxSurround(metalLayers[i][c], Double.MAX_VALUE, mutableDist)) // only when found
				{
					metalSurroundX[i] = Math.max(metalSurroundX[i], mutableDist.doubleValue());
					if (metalSurroundY[i] != mutableDist.doubleValue())
						metalSurroundY[i] = Math.max(metalSurroundY[i], mutableDist.doubleValue());
				}
			}
		}

		viaSurround = new double[numMetalLayers - 1];
		viaSize = new double[numMetalLayers - 1];
		viaDiagonalDistance = new double[numMetalLayers - 1];
		viaColorDiffSpacing = new double[numMetalLayers - 1];
		secretViaSpacingRules = new Method[numMetalLayers - 1];
		for (int i = 0; i < numMetalLayers - 1; i++)
		{
			Layer lay = viaLayers[i];
			if (lay == null) continue;

			// see if there are any secret via spacing rules embodied as methods
			secretViaSpacingRules[i] = getViaSpacingChecker(tech, i);

			// determine spacing distance
			double spacing = 2;
			DRCTemplate ruleSpacing = DRC.getSpacingRule(lay, null, lay, null, false, -1, maxDefArcWidth[i], 50);
			if (ruleSpacing != null) spacing = ruleSpacing.getValue(0);
			viaSurround[i] = spacing;

			// determine diagonal via distance
			double diagonalDist = 0;
			DRCTemplate ruleDiagonalVia = DRC.getMinValue(lay, DRCTemplate.DRCRuleType.DIAGONALVIA);
			if (ruleDiagonalVia != null)
				diagonalDist = ruleDiagonalVia.getValue(0);
			viaDiagonalDistance[i] = diagonalDist;

			// determine the via distance when colors differ
			double colorDiffDist = 0;
			DRCTemplate ruleColorDiffVia = DRC.getMinValue(lay, DRCTemplate.DRCRuleType.DIFFMASKSEP);
			if (ruleColorDiffVia != null)
			{
				colorDiffDist = ruleColorDiffVia.getValue(0);
			}
			viaColorDiffSpacing[i] = colorDiffDist;

			// determine cut size
			double width = 0;
			DRCTemplate ruleWidth = DRC.getMinValue(lay, DRCTemplate.DRCRuleType.NODSIZ);
			if (ruleWidth != null) width = ruleWidth.getValue(0);

			// extend to the size of the largest cut
			List<MetalVia> nps = new ArrayList<MetalVia>();
			for(MetalVia mv : metalVias[i].getVias()) nps.add(mv);
			for(MetalVia mv : metalVias2X[i].getVias()) nps.add(mv);
			for (MetalVia mv : nps)
			{
				NodeInst dummyNi = NodeInst.makeDummyInstance(mv.via, ep);
				Poly[] conPolys = tech.getShapeOfNode(dummyNi);
				for (int p = 0; p < conPolys.length; p++)
				{
					Poly conPoly = conPolys[p];
					if (conPoly.getLayer().getFunction().isContact())
					{
						Rectangle2D bounds = conPoly.getBounds2D();
						width = Math.max(width, bounds.getWidth());
						width = Math.max(width, bounds.getHeight());
					}
				}
			}
			viaSize[i] = width;
		}
		return false;
	}

	private static boolean tsmcDesignRulesChecked = false;
	private static Class<?> tsmcDesignRulesClass;

	private static boolean hasTSMCDesignRules()
	{
		if (!tsmcDesignRulesChecked)
		{
			tsmcDesignRulesChecked = true;
			try
			{
				tsmcDesignRulesClass = Class.forName("com.sun.electric.plugins.tsmc.TSMCDesignRules");
			} catch (ClassNotFoundException e) {}
		}
		return tsmcDesignRulesClass != null;
	}

	private static Method getViaSpacingChecker(Technology tech, int metalNo)
	{
		if (!hasTSMCDesignRules()) return null;

		String methodName = "tsmcVia" + (metalNo+1) + "RuleFor" + tech.getTechName().toUpperCase();
		try
		{
			Method viaSpacingCheckMethod = tsmcDesignRulesClass.getMethod(methodName, new Class[]{List.class});
			return viaSpacingCheckMethod;
		} catch (Exception e) {}
		return null;
	}

	// extra class for sorting nets based on # of bits per bus
	private static class BusBitsNumberClass
	{
		String netName;
		List<String> bits;

		BusBitsNumberClass(String name)
		{
			netName = name;
			bits = new ArrayList<String>();
		}
		List<String> getNets()
		{
			List<String> l = new ArrayList<String>();

			for (String bit: bits)
				l.add(netName+bit);
			return l;
		}
	}
	private static class SortByBusBitsNumberClass implements Comparator<BusBitsNumberClass>
	{
		public int compare(BusBitsNumberClass s1, BusBitsNumberClass s2)
		{
			int l1 =  s1.bits.size();
			int l2 =  s2.bits.size();
			if (l1 > l2) return -1; // descending order is preferred
			else if (l1 < l2) return 1;
			return s1.netName.compareToIgnoreCase(s2.netName); // use alphabetic order
		}
	}

	// SortArcByDistance
	private static class SortArcByDistance implements Comparator<String>
	{
		Cell theCell;
		SoGNetOrder sortingType;

		SortArcByDistance(Cell cell, SoGNetOrder type)
		{
			theCell = cell;
			sortingType = type;
		}

		public int compare(String s1, String s2)
		{
			ArcInst a1 = theCell.findArc(s1);
			ArcInst a2 = theCell.findArc(s2);
			double d1 = a1.getLambdaLength();
			double d2 = a2.getLambdaLength();
			switch (sortingType)
			{
				case SOGNETORDERPERCENTAGE:
					if (d1 > d2) return 1;
					if (d1 < d2) return -1;
					break;
				case SOGNETORDERDESCENDING:
					if (d1 < d2) return 1;
					if (d1 > d2) return -1;
					break;
				case SOGNETORDERASCENDING:
				case SOGNETORDERBUS:
				case SOGNETORDERGIVEN:
				case SOGNETORDERORIGINAL:
					break;
			}
			return 0;
		}
	}

	/**
	 * Comparator class for sorting Layers by their mask number.
	 */
	private static class SortLayersByMaskNumber implements Comparator<Layer>
	{
		public int compare(Layer l1, Layer l2)
		{
			int m1 = l1.getFunction().getMaskColor();
			int m2 = l2.getFunction().getMaskColor();
			return m1 - m2;
		}
	}

	/**
	 * Comparator class for sorting ArcProtos by their mask number.
	 */
	private static class SortArcsByMaskNumber implements Comparator<ArcProto>
	{
		public int compare(ArcProto a1, ArcProto a2)
		{
			int m1 = a1.getMaskLayer();
			int m2 = a2.getMaskLayer();
			return m1 - m2;
		}
	}

	// debugging code
//	private void dumpSpacing()
//	{
//		System.out.println("CACHED METAL SPACINGS");
//		for(int lay=0; lay<numMetalLayers; lay++)
//		{
//			System.out.println("LAYER "+(lay+1));
//			for(Double wid : layerSurround[lay].keySet())
//			{
//				Map<Double, Double> widMap = layerSurround[lay].get(wid);
//				System.out.println("   WIDTH "+wid);
//				Map<Double,Set<Double>> found = new TreeMap<Double,Set<Double>>();
//				for(Double len : widMap.keySet())
//				{
//					Double dist = widMap.get(len);
//					Set<Double> lengths = found.get(dist);
//					if (lengths == null) found.put(dist, lengths = new TreeSet<Double>());
//					lengths.add(len);
//				}
//				for(Double dist : found.keySet())
//				{
//					boolean foundOne = false;
//					double low = 0, high = 0;
//					for(Double len : found.get(dist))
//					{
//						if (foundOne)
//						{
//							if (len.doubleValue() < low) low = len.doubleValue();
//							if (len.doubleValue() > high) high = len.doubleValue();
//						} else
//						{
//							foundOne = true;
//							low = high = len.doubleValue();
//						}
//					}
//					System.out.println("      LENGTHS "+low+" TO "+high+" HAVE SPACING "+dist);
//				}
//			}
//		}
//	}

	private void initializeGrids()
	{
		metalGrid = new SeaOfGatesTrack[numMetalLayers][];
		metalGridRange = new double[numMetalLayers];
		for(int i=0; i<numMetalLayers; i++)
		{
			ArcProto ap = primaryMetalArc[i];
			String arcGrid = sogp.getGrid(ap);
			if (arcGrid == null)
			{
				metalGrid[i] = null;
				metalGridRange[i] = 0;
			} else
			{
				List<SeaOfGates.SeaOfGatesTrack> found = new ArrayList<SeaOfGates.SeaOfGatesTrack>();
				String[] parts = arcGrid.split(",");
				for(int j=0; j<parts.length; j++)
				{
					String part = parts[j].trim();
					if (part.length() == 0) continue;
					int trackColor = SeaOfGatesTrack.getSpecificMaskNumber(part);
					if (!Character.isDigit(part.charAt(part.length()-1))) part = part.substring(0, part.length()-1);
					double val = TextUtils.atof(part);
					found.add(new SeaOfGates.SeaOfGatesTrack(val, trackColor));
				}
				Collections.sort(found);
				metalGrid[i] = new SeaOfGatesTrack[found.size()];
				for(int j=0; j<found.size(); j++) metalGrid[i][j] = found.get(j);
				metalGridRange[i] = metalGrid[i][found.size()-1].getCoordinate() - metalGrid[i][0].getCoordinate();
			}
		}
	}

	public static class RoutesOnNetwork implements Comparable<RoutesOnNetwork>
	{
		private String netName;
		private List<SteinerTreePortPair> pairs;
		List<ArcInst> unroutedArcs;
		List<PortInst> unorderedPorts;
		List<NeededRoute> neededRoutes;

		RoutesOnNetwork(String netName)
		{
			this.netName = netName;
			pairs = new ArrayList<SteinerTreePortPair>();
			unroutedArcs = new ArrayList<ArcInst>();
			unorderedPorts = new ArrayList<PortInst>();
			neededRoutes = new ArrayList<NeededRoute>();
		}

		public List<SteinerTreePortPair> getPairs() { return pairs; }

		public String getName() { return netName; }

		public boolean addUnorderedPort(PortInst pi)
		{
			if (pi.getNodeInst().isCellInstance() ||
				((PrimitiveNode)pi.getNodeInst().getProto()).getTechnology() != Generic.tech())
			{
				if (!unorderedPorts.contains(pi)) unorderedPorts.add(pi);
				return false;
			}
			return true;
		}

		/**
		 * Method to setup a spine network (many colinear points).
		 */
		public void setupSpineInfo(boolean disableAdvancedSpineRouting)
		{
			// first make a set of unique ports (remove duplicates)
			Set<PortInst> uniquePorts = new HashSet<PortInst>();
			for(PortInst pi : unorderedPorts)
				uniquePorts.add(pi);

			// make histograms of X and Y coordinates
			Map<Double,List<PortInst>> xMap = new TreeMap<Double,List<PortInst>>();
			Map<Double,List<PortInst>> yMap = new TreeMap<Double,List<PortInst>>();
			for(PortInst pi : uniquePorts)
			{
				EPoint pt = pi.getCenter();
				Double xv = new Double(pt.getX());
				List<PortInst> xTotal = xMap.get(xv);
				if (xTotal == null) xMap.put(xv, xTotal = new ArrayList<PortInst>());
				xTotal.add(pi);

				Double yv = new Double(pt.getY());
				List<PortInst> yTotal = yMap.get(yv);
				if (yTotal == null) yMap.put(yv, yTotal = new ArrayList<PortInst>());
				yTotal.add(pi);
			}

			// examine histograms and generate spine runs
			List<SteinerTreePortPair> xClusters = generateSpines(xMap);
			List<SteinerTreePortPair> yClusters = generateSpines(yMap);

			// if no clusters were found, ignore spines
			if (xClusters.size() == 0 && yClusters.size() == 0)
			{
				List<SteinerTreePort> portList = new ArrayList<SteinerTreePort>();
				for(PortInst pi : uniquePorts) portList.add(new PortInstShadow(pi));
				SteinerTree st = new SteinerTree(portList, disableAdvancedSpineRouting);
				for(SteinerTreePortPair stpp : st.getTreeBranches()) pairs.add(stpp);
				return;
			}

			// eliminate spines in one axis that are in the other
			Collections.sort(xClusters, new SortSpinesByNumberOfTaps());
			Collections.sort(yClusters, new SortSpinesByNumberOfTaps());
			eliminateSpinePoints(xClusters, yClusters);
			eliminateSpinePoints(yClusters, xClusters);

			// now combine all spines into a single list
			for(SteinerTreePortPair stpp : xClusters) pairs.add(stpp);
			for(SteinerTreePortPair stpp : yClusters) pairs.add(stpp);

			// add in extra networks to connect it all together
			List<Set<SteinerTreePort>> allPortGroups = new ArrayList<Set<SteinerTreePort>>();
			for(SteinerTreePortPair stpp : pairs)
			{
				// build set of ports on this spine
				Set<SteinerTreePort> portsInSpine = new HashSet<SteinerTreePort>();
				portsInSpine.add(stpp.getPort1());
				portsInSpine.add(stpp.getPort2());
				List<PortInst> taps = stpp.getSpineTaps();
				if (taps != null)
					for(PortInst pi : taps) portsInSpine.add(pi);

				// make list of groups that these ports are already a part of
				Set<Set<SteinerTreePort>> foundPortGroups = new HashSet<Set<SteinerTreePort>>();
				for(SteinerTreePort stp : portsInSpine)
				{
					Set<SteinerTreePort> portGroup = findPortGroup(stp, allPortGroups);
					if (portGroup != null) foundPortGroups.add(portGroup);
				}
				if (foundPortGroups.size() == 0)
				{
					// nothing in this segment connects to any other segment: add this port group
					allPortGroups.add(portsInSpine);
				} else
				{
					// connects to other groups: merge these points in and reduce them to a single group
					List<Set<SteinerTreePort>> otherPortGroups = new ArrayList<Set<SteinerTreePort>>();
					Set<SteinerTreePort> firstPortGroup = null;
					for(Set<SteinerTreePort> portGroup : foundPortGroups)
					{
						if (firstPortGroup == null)
						{
							// the first group: add this one's ports to it
							firstPortGroup = portGroup;
							for(SteinerTreePort stp : portsInSpine) firstPortGroup.add(stp);
						} else
						{
							// the later groups: add to the first one and schedule them for deletion
							for(SteinerTreePort stp : portGroup) firstPortGroup.add(stp);
							otherPortGroups.add(portGroup);
						}
					}
					for(Set<SteinerTreePort> portGroup : otherPortGroups)
						allPortGroups.remove(portGroup);
				}
			}

			// now find ports that weren't in any cluster
			Set<PortInst> unusedPorts = new HashSet<PortInst>();
			for(PortInst pi : uniquePorts) unusedPorts.add(pi);
			for(SteinerTreePortPair stpp : pairs)
			{
				unusedPorts.remove(stpp.getPort1());
				unusedPorts.remove(stpp.getPort2());
				for(PortInst pi : stpp.getSpineTaps()) unusedPorts.remove(pi);
			}
			for(PortInst pi : unusedPorts)
			{
				Set<SteinerTreePort> portsInSpine = new HashSet<SteinerTreePort>();
				portsInSpine.add(pi);
				allPortGroups.add(portsInSpine);
			}

			// now create nets that join the different groups
			while (allPortGroups.size() > 1)
			{
				// merge the first group with the closest other one
				Set<SteinerTreePort> firstGroup = allPortGroups.get(0);
				double bestDist = Double.MAX_VALUE;
				Set<SteinerTreePort> bestGroup = null;
				SteinerTreePort bestFirstPort = null, bestOtherPort = null;
				for(int i=1; i<allPortGroups.size(); i++)
				{
					Set<SteinerTreePort> otherGroup = allPortGroups.get(i);
					for(SteinerTreePort stpFirst : firstGroup)
					{
						for(SteinerTreePort stpOther : otherGroup)
						{
							double dist = stpFirst.getCenter().distance(stpOther.getCenter());
							if (dist < bestDist)
							{
								bestDist = dist;
								bestFirstPort = stpFirst;
								bestOtherPort = stpOther;
								bestGroup = otherGroup;
							}
						}
					}
				}

				// found the best way to merge the first group
				SteinerTreePortPair stpp = new SteinerTreePortPair(bestFirstPort, bestOtherPort);
				pairs.add(stpp);
				for(SteinerTreePort stp : firstGroup) bestGroup.add(stp);
				allPortGroups.remove(0);
			}
		}

		private Set<SteinerTreePort> findPortGroup(SteinerTreePort pi, List<Set<SteinerTreePort>> allPortGroups)
		{
			for(Set<SteinerTreePort> portGroup : allPortGroups)
			{
				if (portGroup.contains(pi)) return portGroup;
			}
			return null;
		}

		/**
		 * Method to remove spines in one axis that are completely connected by spines in the other axis.
		 * @param goodSet the set being reduced.
		 * @param otherSet the other set.
		 */
		private void eliminateSpinePoints(List<SteinerTreePortPair> goodSet, List<SteinerTreePortPair> otherSet)
		{
			for(int i=0; i<goodSet.size(); i++)
			{
				SteinerTreePortPair stpp = goodSet.get(i);
				SteinerTreePort p1 = stpp.getPort1();
				if (!findPortInSpine(p1, otherSet)) continue;
				SteinerTreePort p2 = stpp.getPort2();
				if (!findPortInSpine(p2, otherSet)) continue;
				List<PortInst> taps = stpp.getSpineTaps();
				if (taps != null)
				{
					boolean anyMissing = false;
					for(PortInst pi : taps)
					{
						if (!findPortInSpine(pi, otherSet))
						{
							anyMissing = true;
							break;
						}
					}
					if (anyMissing) continue;
				}

				// this is duplicated: eliminate it
				goodSet.remove(i);
				i--;
			}
		}

		/**
		 * Comparator class for sorting spines by the number of taps they have.
		 */
		private static class SortSpinesByNumberOfTaps implements Comparator<SteinerTreePortPair>
		{
			public int compare(SteinerTreePortPair stpp1, SteinerTreePortPair stpp2)
			{
				List<PortInst> taps1 = stpp1.getSpineTaps();
				List<PortInst> taps2 = stpp2.getSpineTaps();
				int tapCount1 = taps1 == null ? 0 : taps1.size();
				int tapCount2 = taps2 == null ? 0 : taps2.size();
				return tapCount1 - tapCount2;
			}
		}

		private boolean findPortInSpine(SteinerTreePort stp, List<SteinerTreePortPair> otherSet)
		{
			for(SteinerTreePortPair stpp : otherSet)
			{
				if (stpp.getPort1() == stp) return true;
				if (stpp.getPort2() == stp) return true;
				List<PortInst> taps = stpp.getSpineTaps();
				if (taps != null)
				{
					for(PortInst pi : taps) if (pi == stp) return true;
				}
			}
			return false;
		}

		/**
		 * Method to reduce a histogram of ports to a collection of nets (both simple and spine).
		 * @param map a Map from X or Y coordinates to lists of ports on those coordinates.
		 * @return a List of SteinerTreePortPair nets that connect two points
		 * (and optionally include spine tap points in the middle).
		 */
		private List<SteinerTreePortPair> generateSpines(Map<Double,List<PortInst>> map)
		{
			List<List<PortInst>> clusters = new ArrayList<List<PortInst>>();
			List<Double> mapCoords = new ArrayList<Double>();
			for(Double v : map.keySet()) mapCoords.add(v);
			int startIndex = 0;
			List<PortInst> bestCluster = null;
			for(int index = 0; index<mapCoords.size(); index++)
			{
				// get bounds of range of coordinates
				int total = 0;
				double lowX = 0, highX = 0, lowY = 0, highY = 0;
				for(int i = startIndex; i <= index; i++)
				{
					Double v = mapCoords.get(i);
					List<PortInst> ports = map.get(v);
					for(PortInst pi : ports)
					{
						EPoint pt = pi.getCenter();
						if (total == 0)
						{
							lowX = highX = pt.getX();
							lowY = highY = pt.getY();
						} else
						{
							if (pt.getX() < lowX) lowX = pt.getX();
							if (pt.getX() > highX) highX = pt.getX();
							if (pt.getY() < lowY) lowY = pt.getY();
							if (pt.getY() > highY) highY = pt.getY();
						}
						total++;
					}
				}
				double width = highX - lowX, height = highY - lowY;
				if (width*SPINERATIO > height && height*SPINERATIO > width)
				{
					// not a cluster
					if (bestCluster != null) clusters.add(bestCluster);
					bestCluster = null;
					startIndex = index;
					index--;
				} else
				{
					// a good cluster (if it has enough points)
					List<PortInst> cluster = new ArrayList<PortInst>();
					for(int i = startIndex; i <= index; i++)
					{
						Double v = mapCoords.get(i);
						List<PortInst> ports = map.get(v);
						for(PortInst pi : ports) cluster.add(pi);
					}
					if (cluster.size() > 2) bestCluster = cluster;
				}
			}
			if (bestCluster != null) clusters.add(bestCluster);

			// build spines for every cluster
			List<SteinerTreePortPair> pairs = new ArrayList<SteinerTreePortPair>();
			for(List<PortInst> cluster : clusters)
			{
				// find the point farthest from the first one
				PortInst first = cluster.get(0);
				EPoint firstPT = first.getCenter();
				double bestDist = 0;
				PortInst end1 = null, end2 = null;
				for(int i=1; i<cluster.size(); i++)
				{
					PortInst pi = cluster.get(i);
					EPoint pt = pi.getCenter();
					double dist = firstPT.distance(pt);
					if (dist > bestDist)
					{
						bestDist = dist;
						end1 = pi;
					}
				}
				EPoint end1PT = end1.getCenter();

				// find the point farthest from the far one
				bestDist = 0;
				for(int i=0; i<cluster.size(); i++)
				{
					PortInst pi = cluster.get(i);
					if (pi == end1) continue;
					EPoint pt = pi.getCenter();
					double dist = end1PT.distance(pt);
					if (dist > bestDist)
					{
						bestDist = dist;
						end2 = pi;
					}
				}
				SteinerTreePortPair stpp = new SteinerTreePortPair(end1, end2);
				for(int i=0; i<cluster.size(); i++)
				{
					PortInst pi = cluster.get(i);
					if (pi != end1 && pi != end2)
						stpp.addTapPort(pi);
				}
				pairs.add(stpp);
			}
			return pairs;
		}

		/**
		 * Method to sort RoutesOnNetwork by their network name.
		 */
		public int compareTo(RoutesOnNetwork other) { return getName().compareTo(other.getName()); }
	}

	private RouteBatch[] makeListOfRoutes(Netlist netList, List<ArcInst> arcsToRoute, List<EPoint> linesInNonMahnattan)
	{
		// normal condition: organize arcs by network
		Map<Network,List<ArcInst>> seen = new HashMap<Network,List<ArcInst>>();
		for (int b = 0; b < arcsToRoute.size(); b++)
		{
			// get list of PortInsts that comprise this net
			ArcInst ai = arcsToRoute.get(b);
			Network net = netList.getNetwork(ai, 0);
			if (net == null)
			{
				warn("Arc " + describe(ai) + " has no network!");
				continue;
			}
			List<ArcInst> arcsOnNet = seen.get(net);
			if (arcsOnNet == null) seen.put(net, arcsOnNet = new ArrayList<ArcInst>());

			// remove duplicates
			boolean exists = false;
			for(ArcInst oldAI : arcsOnNet)
			{
				if (oldAI.getHeadPortInst().getPortProto() == ai.getHeadPortInst().getPortProto() &&
					oldAI.getHeadPortInst().getNodeInst() == ai.getHeadPortInst().getNodeInst() &&
					oldAI.getTailPortInst().getPortProto() == ai.getTailPortInst().getPortProto() &&
					oldAI.getTailPortInst().getNodeInst() == ai.getTailPortInst().getNodeInst()) { exists = true;  break; }
				if (oldAI.getHeadPortInst().getPortProto() == ai.getTailPortInst().getPortProto() &&
					oldAI.getHeadPortInst().getNodeInst() == ai.getTailPortInst().getNodeInst() &&
					oldAI.getTailPortInst().getPortProto() == ai.getHeadPortInst().getPortProto() &&
					oldAI.getTailPortInst().getNodeInst() == ai.getHeadPortInst().getNodeInst()) { exists = true;  break; }
			}
			if (exists)
			{
				String warnMsg = "Arc from (" + TextUtils.formatDistance(ai.getHeadLocation().getX()) + "," +
					TextUtils.formatDistance(ai.getHeadLocation().getY()) + ") to (" + TextUtils.formatDistance(ai.getTailLocation().getX()) + "," +
					TextUtils.formatDistance(ai.getTailLocation().getY()) + ") is redundant";
				List<EPoint> linesToShow = new ArrayList<EPoint>();
				linesToShow.add(EPoint.fromLambda(ai.getHeadLocation().getX(), ai.getHeadLocation().getY()));
				linesToShow.add(EPoint.fromLambda(ai.getTailLocation().getX(), ai.getTailLocation().getY()));
				warn(warnMsg, netList.getCell(), linesToShow, null);
			} else
			{
				arcsOnNet.add(ai);
			}
		}

		// build a RoutesOnNetwork for every network
		List<RoutesOnNetwork> allRoutes = new ArrayList<RoutesOnNetwork>();
		List<Network> allNets = new ArrayList<Network>();
		for(Network net : seen.keySet()) allNets.add(net);

		if (prefs.netOrder == SoGNetOrder.SOGNETORDERORIGINAL)
			Collections.sort(allNets);

		for(Network net : allNets)
		{
			RoutesOnNetwork ron = new RoutesOnNetwork(net.getName());
			allRoutes.add(ron);

			// make list of all ends in the batch
			List<ArcInst> arcsOnNet = seen.get(net);
			for (ArcInst ai : arcsOnNet)
			{
				ron.unroutedArcs.add(ai);
				if (ron.addUnorderedPort(ai.getHeadPortInst()))
					warn("Arc " + describe(ai) + " has an unconnectable end (on " + ai.getHeadPortInst().getNodeInst().describe(false) + ")");
				if (ron.addUnorderedPort(ai.getTailPortInst()))
					warn("Arc " + describe(ai) + " has an unconnectable end (on " + ai.getTailPortInst().getNodeInst().describe(false) + ")");
			}

			// see if reconfiguration of routing is prevented
			if (sogp.isSteinerDone())
			{
				// unrouted wires define the exact routing configuration, so don't do spine detection or Steiner tree reorganization
				for (ArcInst ai : arcsOnNet)
					ron.getPairs().add(new SteinerTreePortPair(ai.getHeadPortInst(), ai.getTailPortInst()));
			} else
			{
				// see if spine routing is enabled
				if (prefs.enableSpineRouting)
				{
					// decompose into spines
					ron.setupSpineInfo(prefs.disableAdvancedSpineRouting);
				} else
				{
					// do not consider look for spines: just do Steiner tree reorganization
					List<SteinerTreePort> portList = new ArrayList<SteinerTreePort>();
					for(PortInst pi : ron.unorderedPorts) portList.add(new PortInstShadow(pi));
					SteinerTree st = new SteinerTree(portList, prefs.disableAdvancedSpineRouting);
					ron.pairs = st.getTreeBranches();
					if (ron.pairs == null)
					{
						String errMsg = "Arcs in " + net.getName() + " do not make valid connection: deleted";
						error(errMsg);
						List<EPoint> lineList = new ArrayList<EPoint>();
						for(ArcInst delAi : ron.unroutedArcs)
						{
							lineList.add(delAi.getHeadLocation());
							lineList.add(delAi.getTailLocation());
						}
						errorLogger.logMessageWithLines(errMsg, null, lineList, cell, 0, true);
					}
				}
			}
		}

		// if debugging spine information, compute it and finish
		if (RoutingDebug.isShowingSpines())
		{
			RoutingDebug.showSpineNetworks(allRoutes);
			return new RouteBatch[0];
		}

		// build the R-Tree
		if (buildRTrees(netList, arcsToRoute, linesInNonMahnattan))
		{
			info("Non-Manhattan geometry found");
		}

		// make NeededRoute objects and fill-in network information in the R-Tree
		totalBlockages = getNumBlockages();
		blockagesFound = 0;
		setProgressNote("Extract connectivity (" + totalBlockages + " blockages)");
		setProgressValue(0, 100);

		for(RoutesOnNetwork ron : allRoutes)
		{
			// determine the minimum width of arcs on this net
			double minWidth = getMinWidth(ron.unorderedPorts);
			for(int i=0; i<ron.getPairs().size(); i++)
			{
				SteinerTreePortPair stpp = ron.getPairs().get(i);
				Object obj1 = stpp.getPort1();
				if (obj1 instanceof PortInstShadow) obj1 = ((PortInstShadow)obj1).getPortInst();
				Object obj2 = stpp.getPort2();
				if (obj2 instanceof PortInstShadow) obj2 = ((PortInstShadow)obj2).getPortInst();
				PortInst aPi = (PortInst)obj1;
				PortInst bPi = (PortInst)obj2;

				// get Arc information about the ends of the path
				ArcProto aArc = getMetalArcOnPort(aPi);
				if (aArc == null) continue;
				ArcProto bArc = getMetalArcOnPort(bPi);
				if (bArc == null) continue;

				// create the NeededRoute
				NeededRoute nr = new NeededRoute(ron.getName(), aPi, bPi, aArc, bArc, stpp.getSpineTaps(), minWidth);

				// set the network number on this net
				Iterator<ArcInst> it = ron.unroutedArcs.iterator();
				ArcInst sampleArc = it.next();
				Network net = netList.getNetwork(sampleArc, 0);
				nr.setNetID(net);
				nr.growNetwork();

				// check endpoints and place contacts there if needed
				boolean aInvalid = nr.invalidPort(true, false, aPi);
				boolean bInvalid = nr.invalidPort(false, false, bPi);
				if (aInvalid || bInvalid) continue;

				ron.neededRoutes.add(nr);
			}
		}

		// merge RoutesOnNetwork that are on the same network
		setProgressNote("Final Prepare for Routing...");
		setProgressValue(0, 100);
		Map<Integer,Set<RoutesOnNetwork>> uniqueNets = new TreeMap<Integer,Set<RoutesOnNetwork>>();
		for(RoutesOnNetwork ron : allRoutes)
		{
			int netNumber = -1;
			for(NeededRoute nr : ron.neededRoutes)
			{
				int thisNetNumber = nr.netID.intValue();
				if (netNumber < 0) netNumber = thisNetNumber;
				if (netNumber != thisNetNumber)
					error("Error: network " + ron.getName() + " has network IDs " + netNumber + " and " + thisNetNumber);
			}
			Integer id = Integer.valueOf(netNumber);
			Set<RoutesOnNetwork> mergedRoutes = uniqueNets.get(id);
			if (mergedRoutes == null) uniqueNets.put(id, mergedRoutes = new TreeSet<RoutesOnNetwork>());
			mergedRoutes.add(ron);
		}

		// build RouteBatch objects for merged RoutesOnNetwork
		List<RouteBatch> allBatches = new ArrayList<RouteBatch>();
		for(Integer netID : uniqueNets.keySet())
		{
			Set<RoutesOnNetwork> mergedRoutes = uniqueNets.get(netID);
			RouteBatch rb = null;
			for(RoutesOnNetwork ron : mergedRoutes)
			{
				if (rb == null)
				{
					rb = new RouteBatch(ron.getName());
					allBatches.add(rb);
				}
				for(ArcInst ai : ron.unroutedArcs)
				{
					rb.unroutedArcs.add(ai);
					if (!rb.isPwrGnd)
					{
						Network net = netList.getNetwork(ai, 0);
						for (Iterator<Export> it = net.getExports(); it.hasNext();)
						{
							Export e = it.next();
							if (e.isGround() || e.isPower())
							{
								rb.isPwrGnd = true;
								break;
							}
						}
						PortProto headPort = ai.getHeadPortInst().getPortProto();
						PortProto tailPort = ai.getTailPortInst().getPortProto();
						if (headPort.isGround() || headPort.isPower() || tailPort.isGround() || tailPort.isPower()) rb.isPwrGnd = true;
					}
				}
				for(NeededRoute nr : ron.neededRoutes)
				{
					nr.setBatchInfo(rb, rb.routesInBatch.size());
					double dX = nr.getAX() - nr.getBX();
					double dY = nr.getAY() - nr.getBY();
					rb.length += Math.sqrt(dX*dX + dY*dY);

					// put the NeededRoute in the batch
					rb.addRoute(nr);
				}
			}
		}

		// add blockages around port areas
		for(RouteBatch rb : allBatches)
		{
			for(NeededRoute nr : rb.routesInBatch)
			{
				nr.addBlockagesAtPorts(nr.aPi);
				nr.addBlockagesAtPorts(nr.bPi);
				if (nr.spineTaps != null)
					for(PortInst pi : nr.spineTaps)
						nr.addBlockagesAtPorts(pi);
			}
		}

		// sort batches
		if (prefs.netOrder == SoGNetOrder.SOGNETORDERORIGINAL)
			Collections.sort(allBatches);

		RouteBatch[] routeBatches = new RouteBatch[allBatches.size()];
		for(int i=0; i<allBatches.size(); i++)
			routeBatches[i] = allBatches.get(i);

		for(RouteBatch rb : allBatches)
		{
			for(NeededRoute nr : rb.routesInBatch)
			{
				boolean paren = false;
				if (nr.batch.routesInBatch.size() > 1)
				{
					nr.routeName += " (" + (nr.routeInBatch+1) + " of " + nr.batch.routesInBatch.size();
					paren = true;
				}
				if (nr.spineTaps != null)
				{
					nr.routeName += (paren ? ", " : " (") + "spine";
					paren = true;
				}
				if (paren) nr.routeName += ")";
			}
		}
		return routeBatches;
	}

	private ArcProto getMetalArcOnPort(PortInst pi)
	{
		ArcProto[] arcs = getPossibleConnections(pi.getPortProto());
		for (int j = 0; j < arcs.length; j++)
		{
			if (arcs[j].getTechnology() == tech && arcs[j].getFunction().isMetal()) return arcs[j];
		}

		String errorMsg = "Cannot connect port " + pi.getPortProto().getName() + " of node " +
			describe(pi.getNodeInst()) + " because it has no metal connection in " + tech.getTechName() + " technology";
		error(errorMsg);
		List<PolyBase> polyList = new ArrayList<PolyBase>();
		polyList.add(pi.getPoly());
		errorLogger.logMessage(errorMsg, polyList, cell, 0, true);
		return null;
	}

	/**
	 * Method to find a path between two ports.
	 * @param nr the NeededRoute object with all necessary information.
	 * If successful, the NeededRoute's "vertices" field is filled with the route data.
	 */
	Runnable[] findPath(NeededRoute nr)
	{
		// prepare the routing path
		nr.makeWavefronts();

		if (nr.checkEndSurround()) return null;

		// special case when route is null length
		Wavefront d1 = nr.dirAtoB;
		Wavefront d2 = nr.dirBtoA;
		if (DBMath.rectsIntersect(d1.fromRect, d1.toPE.getRect()) && d1.toZ == d1.fromZ)
		{
			double xVal = (Math.max(d1.fromRect.getMinX(), d1.toPE.getRect().getMinX()) + Math.min(d1.fromRect.getMaxX(), d1.toPE.getRect().getMaxX())) / 2;
			double yVal = (Math.max(d1.fromRect.getMinY(), d1.toPE.getRect().getMinY()) + Math.min(d1.fromRect.getMaxY(), d1.toPE.getRect().getMaxY())) / 2;
			SearchVertex sv = new SearchVertex(xVal, yVal, d1.toZ, d1.toC, 0, null, null, 0, d1, 0, null);
			if (nr.debuggingRouteFromA != null) RoutingDebug.ensureDebuggingShadow(sv, false);
			nr.completeRoute(sv);
			return null;
		}

		if (parallelDij)
		{
			DijkstraParallel aToB = new DijkstraParallel(d1, d2);
			DijkstraParallel bToA = new DijkstraParallel(d2, d1);
			return new Runnable[] { aToB, bToA };
		} else {
			return new Runnable[] { new DijkstraTwoWay(nr, d1, d2) };
		}
	}

	/********************************* SEARCH SUPPORT *********************************/

	private class DijkstraParallel implements Runnable {
		private final Wavefront wf;
		private final Wavefront otherWf;

		private DijkstraParallel(Wavefront wf, Wavefront otherWf) {
			this.wf = wf;
			this.otherWf = otherWf;
		}

		@Override
		public void run() {
			Environment.setThreadEnvironment(env);

			// run the wavefront to the end
			SearchVertex result = null;
			while (result == null)
			{
				if (wf.abort) result = svAbandoned; else
					result = wf.advanceWavefront();
			}

			boolean success = result.wf != null;
			if (success) {
				assert result.wf == wf;
			}

			synchronized (wf.nr) {
				assert !wf.finished;
				if (otherWf.finished) {
				} else {
					otherWf.abort = true;
					if (success) {
						wf.nr.routedSuccess = true; // Mark in lock
					}
				}
				wf.finished = true;
			}
			wf.nr.completeRoute(result);
		}
	}

	/**
	 * Class to run search from both ends at once (interleaved).
	 * Used in single-processor systems.
	 */
	private class DijkstraTwoWay implements Runnable {
		private final NeededRoute nr;
		private final Wavefront dirAtoB, dirBtoA;

		/**
		 * @param nr the NeededRoute to search.
		 * @param dirAtoB Wavefront from A to B
		 * @param dirBtoA Wavefront from B to A
		 */
		private DijkstraTwoWay(NeededRoute nr, Wavefront dirAtoB, Wavefront dirBtoA) {
			this.nr = nr;
			this.dirAtoB = dirAtoB;
			this.dirBtoA = dirBtoA;
		}

		@Override
		public void run() {
			Environment.setThreadEnvironment(env);

			// run both wavefronts in parallel (interleaving steps)
			SearchVertex result = null;
			SearchVertex resultA = null, resultB = null;
			boolean forceBothDirectionsToFinish = CHECKBOTHDIRECTIONS;
			if (nr.debuggingRouteFromA != null) forceBothDirectionsToFinish = true;
			if (forceBothDirectionsToFinish)
			{
				while (result == null)
				{
					if (resultA == null) resultA = dirAtoB.advanceWavefront();
					if (resultB == null) resultB = dirBtoA.advanceWavefront();

					// stop now if either direction aborted
					if (resultA == svAborted || resultB == svAborted)
					{
						nr.completeRoute(svAborted);
						return;
					}

					// both directions must complete
					if (resultA == null || resultB == null) continue;

					// search is done, see if they failed
					if ((resultA == svLimited || resultA == svExhausted) &&
						(resultB == svLimited || resultB == svExhausted))
					{
						// both directions failed: see if there is anything common in the two wavefronts
						result = tryToFindPath(dirAtoB, dirBtoA);
						if (result == null)
						{
							nr.completeRoute(resultA);
							return;
						}
						resultA = result;
					}

					// routing completed
					boolean aGood = resultA != null && resultA != svAbandoned && resultA != svLimited && resultA != svExhausted;
					boolean bGood = resultB != null && resultB != svAbandoned && resultB != svLimited && resultB != svExhausted;
					if (aGood && bGood)
					{
						// both directions completed: choose the best
						List<SearchVertex> aList = new ArrayList<SearchVertex>();
						getOptimizedList(resultA, aList);
						int aVias = 0;
						double aLength = 0;
						for (int i=1; i<aList.size(); i++)
						{
							SearchVertex svLast = aList.get(i-1);
							SearchVertex sv = aList.get(i);
							if (svLast.getZ() != sv.getZ())
							{
								aVias++;
							} else
							{
								double dX = Math.abs(svLast.getX() - sv.getX());
								double dY = Math.abs(svLast.getY() - sv.getY());
								aLength += Math.sqrt(dY*dY + dX*dX);
							}
						}

						List<SearchVertex> bList = new ArrayList<SearchVertex>();
						getOptimizedList(resultB, bList);
						int bVias = 0;
						double bLength = 0;
						for (int i=1; i<aList.size(); i++)
						{
							SearchVertex svLast = aList.get(i-1);
							SearchVertex sv = aList.get(i);
							if (svLast.getZ() != sv.getZ())
							{
								aVias++;
							} else
							{
								double dX = Math.abs(svLast.getX() - sv.getX());
								double dY = Math.abs(svLast.getY() - sv.getY());
								bLength += Math.sqrt(dY*dY + dX*dX);
							}
						}

						if (aLength < bLength || (aLength == bLength && aVias < bVias))
						{
							// A is better
							resultB = null;
						} else
						{
							// B is better
							resultA = null;
						}
					}

					result = resultA;
					if (result == null || result == svAbandoned || result == svLimited || result == svExhausted)
					{
						if (resultB != svAbandoned && resultB != svLimited && resultB != svExhausted)
							result = resultB;
					}
				}
			} else
			{
				// just wait until one of them terminates
				while (result == null)
				{
					if (resultA == null) resultA = dirAtoB.advanceWavefront();
					if (resultB == null) resultB = dirBtoA.advanceWavefront();

					if (resultA != null || resultB != null)
					{
						if (resultA == svAborted || resultB == svAborted)
						{
							nr.completeRoute(svAborted);
							return;
						}
						if ((resultA == svLimited || resultA == svExhausted) &&
							(resultB == svLimited || resultB == svExhausted))
						{
							// see if there is anything common in the two wavefronts
							result = tryToFindPath(dirAtoB, dirBtoA);
							if (result == null)
							{
								nr.completeRoute(resultA);
								return;
							}
							resultA = result;
						}
						result = resultA;
						if (result == null || result == svAbandoned || result == svLimited || result == svExhausted)
						{
							if (resultB != svAbandoned && resultB != svLimited && resultB != svExhausted)
								result = resultB;
						}
					}
				}
			}

			nr.completeRoute(result);
		}
	}

	/**
	 * Method to examine two failed wavefronts (from A to B and from B to A) and look for
	 * a point of intersection that would establish a successful route.
	 * @param a one Wavefront
	 * @param b the other Wavefront
	 * @return null if no path can be found. Otherwise it returns the point to unwind to do the route.
	 */
	private SearchVertex tryToFindPath(Wavefront a, Wavefront b)
	{
		List<SearchVertex> bestPath = null;
		SearchVertex bestSV = null;
		double bestDistance = Double.MAX_VALUE;
		for(int z=0; z<a.searchVertexPlanes.length; z++)
		{
			Map<Integer, Map<Integer,SearchVertex>> plane = a.searchVertexPlanes[z];
			if (plane == null) continue;
			for(Integer y : plane.keySet())
			{
				Map<Integer,SearchVertex> row = plane.get(y);
				if (row == null) continue;
				double yCoord = y.intValue() / DBMath.GRID;
				for(Integer x : row.keySet())
				{
					SearchVertex foundInA = row.get(x);
					double xCoord = x.intValue() / DBMath.GRID;
					SearchVertex foundInB = b.getVertex(xCoord, yCoord, z);
					if (foundInB == null) continue;

					// found a common point in the two wavefronts, check lengths against previous common point
					double total = 0;
					for(SearchVertex sv = foundInA; sv != null; sv = sv.last)
					{
						SearchVertex prev = sv.last;
						if (prev == null) break;
						double dX = sv.getX() - prev.getX();
						double dY = sv.getY() - prev.getY();
						total += Math.sqrt(dX*dX + dY*dY);
						if (sv.getZ() != prev.getZ()) total++;
					}
					for(SearchVertex sv = foundInB; sv != null; sv = sv.last)
					{
						SearchVertex prev = sv.last;
						if (prev == null) break;
						double dX = sv.getX() - prev.getX();
						double dY = sv.getY() - prev.getY();
						total += Math.sqrt(dX*dX + dY*dY);
						if (sv.getZ() != prev.getZ()) total++;
					}
					boolean better = DBMath.isLessThan(total, bestDistance);
					if (!better) continue;

					// see if the two halves have via contact issues
					boolean fail = false;
					for(SearchVertex sv = foundInB; sv != null; sv = sv.last)
					{
						if (sv.getSize() == null) continue;
						SearchVertex lastSv = sv.last;
						if (lastSv == null) continue;
						int lowMetal = Math.min(sv.getZ(), lastSv.getZ());
						int highMetal = Math.max(sv.getZ(), lastSv.getZ());
						for(Poly conPoly : sv.getCutPolys())
						{
							String error = a.validCut(foundInA, lowMetal, highMetal, conPoly.getBounds2D(), conPoly.getLayer());
							if (error != null) { fail = true;  break; }
						}
						if (fail) break;
					}
					if (fail) continue;
					for(SearchVertex sv = foundInA; sv != null; sv = sv.last)
					{
						if (sv.getSize() == null) continue;
						SearchVertex lastSv = sv.last;
						if (lastSv == null) continue;
						int lowMetal = Math.min(sv.getZ(), lastSv.getZ());
						int highMetal = Math.max(sv.getZ(), lastSv.getZ());
						for(Poly conPoly : sv.getCutPolys())
						{
							String error = b.validCut(foundInB, lowMetal, highMetal, conPoly.getBounds2D(), conPoly.getLayer());
							if (error != null) { fail = true;  break; }
						}
						if (fail) break;
					}
					if (fail) continue;

					// see if minimum area rules stop this connection
					SearchVertex svCurrent = null;
					List<SearchVertex> halfPath = new ArrayList<SearchVertex>();
					SearchVertex svBuild = foundInB;
					while (svBuild != null)
					{
						SearchVertex svAdd = new SearchVertex(svBuild);
						if (svCurrent == null && halfPath.size() > 0 && halfPath.get(halfPath.size()-1).getZ() != svAdd.getZ())
							svCurrent = halfPath.get(halfPath.size()-1);
						halfPath.add(svAdd);
						svBuild = svBuild.last;
					}
					List<SearchVertex> path = new ArrayList<SearchVertex>();
					Point2D lastSize = null;
					Poly[] lastCuts = null;
					int lastCutNumber = 0;
					for(int i=0; i<halfPath.size(); i++)
					{
						SearchVertex thisOne = halfPath.get(i);
						Point2D thisSize = thisOne.size;
						Poly[] thisCuts = thisOne.getCutPolys();
						int thisCutNumber = thisOne.zv & 0xFF;

						thisOne.size = lastSize;
						thisOne.cutPolys = lastCuts;
						thisOne.zv = (thisOne.zv & 0xFFFFFF00) | (lastCutNumber & 0xFF);

						lastSize = thisSize;
						lastCuts = thisCuts;
						lastCutNumber = thisCutNumber;
					}
					for(int i=halfPath.size()-1; i>=0; i--)
						path.add(halfPath.get(i));
					svBuild = foundInA;
					while (svBuild != null)
					{
						path.add(new SearchVertex(svBuild));
						svBuild = svBuild.last;
					}
					for(int i=0; i<path.size()-1; i++)
					{
						SearchVertex sv1 = path.get(i);
						SearchVertex sv2 = path.get(i+1);
						sv1.last = sv2;
					}
					path.get(path.size()-1).last = null;

					boolean finalDest = false;
					if (svCurrent == null) { svCurrent = path.get(0); finalDest = true; }
					StringBuffer message = new StringBuffer();
					MutableBoolean err = new MutableBoolean(false);
					SearchVertexAddon sva = a.determineMinimumArea(svCurrent, svCurrent.getX(), svCurrent.getY(), svCurrent.getC(), svCurrent.getZ(),
						svCurrent.getC(), svCurrent.getZ(), null, 0, 0, err, message, finalDest);
					if (sva != null)
					{
						SearchVertex svGoodInsertion = null, svAnyInsertion = null;
						for(SearchVertex sv = svCurrent; sv != null; sv = sv.last)
						{
							if (sv.addOn == null)
							{
								if (svAnyInsertion == null) svAnyInsertion = sv;
								if (sv.getZ() != svCurrent.getZ())
								{
									svGoodInsertion = sv;
									break;
								}
							}
						}
						if (svGoodInsertion != null) svGoodInsertion.addOn = sva; else
							if (svAnyInsertion != null) svAnyInsertion.addOn = sva; else
						{
							System.out.println("!!!!!!!!!!! ERROR: Failed to insert minimum area geometry " +
								TextUtils.formatDistance(sva.addedGeometry[0].getMinX()) + "<=X<=" + TextUtils.formatDistance(sva.addedGeometry[0].getMaxX()) +
								" AND " + TextUtils.formatDistance(sva.addedGeometry[0].getMinY()) + "<=Y<=" + TextUtils.formatDistance(sva.addedGeometry[0].getMaxY()) +
								"," + sva.pureLayerNode.describe(false));
							continue;
						}
					}
					if (err.booleanValue()) continue;

					// intersection is valid, save it
					bestDistance = total;
					bestPath = path;
					bestSV = foundInA;
				}
			}
		}
		if (bestPath != null)
		{
			System.out.println("Network " + a.nr.routeName + " failed in both directions, but found a common point at (" +
				TextUtils.formatDistance(bestSV.xv) + "," + TextUtils.formatDistance(bestSV.yv) + ")");
			SearchVertex result = bestPath.get(0);
			result.wf = a;
			return result;
		}
		return null;
	}

	/********************************* MISCELLANEOUS SUPPORT *********************************/

	private double getMinWidth(List<PortInst> orderedPorts)
	{
		double minWidth = 0;
		for (PortInst pi : orderedPorts)
		{
			double widestAtPort = getWidestMetalArcOnPort(pi);
			if (widestAtPort > minWidth) minWidth = widestAtPort;
		}
		if (minWidth > prefs.maxArcWidth) minWidth = prefs.maxArcWidth;
		return minWidth;
	}

	/**
	 * Method to find the possible ArcProtos that can connect to a PortProto.
	 * Considers special variables on Exports that specify the possibilities.
	 * @param pp the PortProto to examine.
	 * @return an array of possible ArcProtos that can connect.
	 */
	private ArcProto[] getPossibleConnections(PortProto pp)
	{
		ArcProto[] poss = pp.getBasePort().getConnections();
		if (pp instanceof Export)
		{
			Export e = (Export)pp;
			Variable var = e.getVar(Export.EXPORT_PREFERRED_ARCS);
			if (var != null)
			{
				String[] arcNames = (String[])var.getObject();
				ArcProto[] arcs = new ArcProto[arcNames.length];
				boolean allFound = true;
				for(int j=0; j<arcNames.length; j++)
				{
					arcs[j] = ArcProto.findArcProto(arcNames[j]);
					if (arcs[j] == null) allFound = false;
				}
				if (allFound) return arcs;
			}
		}
		return poss;
	}

	private static boolean inDestGrid(FixpRectangle toRectGridded, double x, double y)
	{
		if (x < toRectGridded.getMinX()) return false;
		if (x > toRectGridded.getMaxX()) return false;
		if (y < toRectGridded.getMinY()) return false;
		if (y > toRectGridded.getMaxY()) return false;
		return true;
	}

	/**
	 * Method to determine the Euclidean distance between two via cuts.
	 * @param cut1LX via #1 low X.
	 * @param cut1HX via #1 high X.
	 * @param cut1LY via #1 low Y.
	 * @param cut1HY via #1 high Y.
	 * @param cut2LX via #2 low X.
	 * @param cut2HX via #2 high X.
	 * @param cut2LY via #2 low Y.
	 * @param cut2HY via #2 high Y.
	 * @return the corner-to-corner distance of the vias (0 if they touch or overlap).
	 */
	private double cutDistance(Rectangle2D cut1, Rectangle2D cut2)
	{
		if (cut1.getMinX() <= cut2.getMaxX() && cut1.getMaxX() >= cut2.getMinX())
		{
			if (cut1.getMinY() <= cut2.getMaxY() && cut1.getMaxY() >= cut2.getMinY())
			{
				// contacts touch
				return 0;
			} else
			{
				// contacts are one above the other
				if ((cut1.getMinY()+cut1.getMaxY())/2 > (cut2.getMinY()+cut2.getMaxY())/2)
					return cut1.getMinY() - cut2.getMaxY(); else
						return cut2.getMinY() - cut1.getMaxY();
			}
		} else
		{
			if (cut1.getMinY() <= cut2.getMaxY() && cut1.getMaxY() >= cut2.getMinY())
			{
				// contacts are side-by-side
				if ((cut1.getMinX()+cut1.getMaxX())/2 > (cut2.getMinX()+cut2.getMaxX())/2)
					return cut1.getMinX() - cut2.getMaxX(); else
						return cut2.getMinX() - cut1.getMaxX();
			}

			// diagonal offset, compute Euclidean distance to corners
			double cut2CornerX, cut2CornerY, cut1CornerX, cut1CornerY;
			if ((cut2.getMinX()+cut2.getMaxX())/2 < (cut1.getMinX()+cut1.getMaxX())/2)
			{
				cut2CornerX = cut2.getMaxX();
				cut1CornerX = cut1.getMinX();
			} else
			{
				cut2CornerX = cut2.getMinX();
				cut1CornerX = cut1.getMaxX();
			}
			if ((cut2.getMinY()+cut2.getMaxY())/2 < (cut1.getMinY()+cut1.getMaxY())/2)
			{
				cut2CornerY = cut2.getMaxY();
				cut1CornerY = cut1.getMinY();
			} else
			{
				cut2CornerY = cut2.getMinY();
				cut1CornerY = cut1.getMaxY();
			}
			double dX = cut2CornerX - cut1CornerX, dY = cut2CornerY - cut1CornerY;
			return Math.sqrt(dX*dX + dY*dY);
		}
	}

	/**
	 * Get the widest metal arc already connected to a given PortInst. Looks
	 * recursively down the hierarchy.
	 * @param pi the PortInst to connect.
	 * @return the widest metal arc connect to that port (zero if none)
	 */
	private double getWidestMetalArcOnPort(PortInst pi)
	{
		// first check the top level
		double width = 0;
		for (Iterator<Connection> it = pi.getConnections(); it.hasNext();)
		{
			Connection c = it.next();
			ArcInst ai = c.getArc();
			ArcProto ap = ai.getProto();
			if (sogp.isPrevented(ap)) continue;
			if (!ap.getFunction().isMetal()) continue;
			double newWidth = ai.getLambdaBaseWidth();
			if (newWidth > width) width = newWidth;
		}

		// now recurse down the hierarchy
		NodeInst ni = pi.getNodeInst();
		if (ni.isCellInstance())
		{
			Export export = (Export)pi.getPortProto();
			PortInst exportedInst = export.getOriginalPort();
			double width2 = getWidestMetalArcOnPort(exportedInst);
			if (width2 > width) width = width2;
		}
		return width;
	}

	/**
	 * Class to cache a PortInst and its center location for Steiner Tree computation.
	 */
	private static class PortInstShadow implements SteinerTreePort
	{
		private PortInst pi;
		private EPoint ctr;

		PortInstShadow(PortInst pi)
		{
			this.pi = pi;
			ctr = pi.getNodeInst().getShapeOfPort(pi.getPortProto()).getCenter();
		}

		public EPoint getCenter() { return ctr; }

		public PortInst getPortInst() { return pi; }
	}

	/**
	 * Method to convert a linked list of SearchVertex objects to an optimized path.
	 * Consolidates runs in the X or Y axes.
	 * @param initialVertex the initial SearchVertex in the linked list.
	 * @param realVertices a List where the optimized vertices will be stored.
	 */
	void getOptimizedList(SearchVertex initialVertex, List<SearchVertex> realVertices)
	{
		realVertices.clear();
		SearchVertex vertex = initialVertex;
		if (vertex != null)
		{
			SearchVertex lastVertex = vertex;
			vertex = vertex.last;
			realVertices.add(lastVertex);
			while (vertex != null)
			{
				if (lastVertex.getZ() != vertex.getZ())
				{
					lastVertex = vertex;
					vertex = vertex.last;
					realVertices.add(lastVertex);
				} else
				{
					// gather a run of vertices on this layer
					double dx = vertex.getX() - lastVertex.getX();
					double dy = vertex.getY() - lastVertex.getY();
					lastVertex = vertex;
					vertex = vertex.last;
					while (vertex != null)
					{
						if (lastVertex.getZ() != vertex.getZ()) break;
						if ((dx == 0 && vertex.getX() - lastVertex.getX() != 0) ||
							(dy == 0 && vertex.getY() - lastVertex.getY() != 0)) break;
						lastVertex = vertex;
						vertex = vertex.last;
					}
					realVertices.add(lastVertex);
				}
			}
		}
	}

	/**
	 * Class to define a list of possible nodes that can connect two layers.
	 * This includes orientation.
	 */
	private static class MetalVia
	{
		PrimitiveNode via;
		int orientation;
		int horMetal, verMetal;
		int horMetalColor, verMetalColor;
		double horMetalInset, verMetalInset;

		MetalVia(PrimitiveNode v, int o, int hm, int hmc, double hmi, int vm, int vmc, double vmi)
		{
			via = v;
			orientation = o;
			horMetal = hm;
			horMetalColor = hmc;
			horMetalInset = hmi;
			verMetal = vm;
			verMetalColor = vmc;
			verMetalInset = vmi;
		}
	}

	/**
	 * Class to define a list of possible nodes that can connect two layers.
	 * This includes orientation.
	 */
	private class MetalVias
	{
		List<MetalVia> vias = new ArrayList<MetalVia>();

		void addVia(PrimitiveNode pn, int o, int hm, int hmc, double hmi, int vm, int vmc, double vmi)
		{
			vias.add(new MetalVia(pn, o, hm, hmc, hmi, vm, vmc, vmi));
			Collections.sort(vias, new PrimsBySize());
		}

		List<MetalVia> getVias() { return vias; }
	}

	/**
	 * Comparator class for sorting primitives by their size.
	 */
	private class PrimsBySize implements Comparator<MetalVia>
	{
		/**
		 * Method to sort primitives by their size.
		 */
		public int compare(MetalVia mv1, MetalVia mv2)
		{
			PrimitiveNode pn1 = mv1.via;
			PrimitiveNode pn2 = mv2.via;
			double sz1 = pn1.getDefWidth(ep) * pn1.getDefHeight(ep);
			double sz2 = pn2.getDefWidth(ep) * pn2.getDefHeight(ep);
			if (sz1 < sz2) return -1;
			if (sz1 > sz2) return 1;
			return 0;
		}
	}

	/********************************* BLOCKAGE CLASSES *********************************/

	/**
	 * Method to return the total number of blockage objects in the Metal blockage trees.
	 * @return the total number of blockage objects in the Metal blockage trees.
	 */
	private int getNumBlockages()
	{
		int total = 0;
		for(int i=0; i<numMetalLayers; i++)
		{
			BlockageTree bTree = rTrees.getMetalTree(primaryMetalLayer[i]);
			if (bTree.root != null)
				total += getNumLeafs(bTree.root);
		}
		return total;
	}

	private int getNumLeafs(RTNode<SOGBound> branch)
	{
		int total = 0;
		for(int i=0; i<branch.getTotal(); i++)
		{
			if (branch.getFlag())
			{
				total++;
			} else
			{
				RTNode<SOGBound> subrt = branch.getChildTree(i);
				total += getNumLeafs(subrt);
			}
		}
		return total;
	}

	Map<Layer,List<Rectangle2D>> removeGeometry;

	/**
	 * Method to build blockage R-Trees.
	 * @param netList
	 * @param arcsToRoute
	 * @return true if there is nonmanhattan geometry in the blockages (may cause problems).
	 */
	private boolean buildRTrees(Netlist netList, List<ArcInst> arcsToRoute, List<EPoint> linesInNonMahnattan)
	{
		rTrees = new BlockageTrees(numMetalLayers);

		MutableInteger nextNetNumber = new MutableInteger(1);
		Map<Network,Integer> netNumbers = new HashMap<Network,Integer>();
		for (ArcInst ai : arcsToRoute)
		{
			Network net = netList.getNetwork(ai, 0);
			Integer netNumber = netNumbers.get(net);
			if (netNumber != null) continue;

			netNumbers.put(net, netNumber = Integer.valueOf(nextNetNumber.intValue() << SHIFTBLOCKBITS));
			if (RoutingDebug.isActive()) RoutingDebug.setNetName(netNumber, net.getName());
			nextNetNumber.increment();
			netIDs.put(net, netNumber);
		}

		// recursively add all polygons in the routing area
		setProgressNote("Find blockages...");
		setProgressValue(0, 100);
		removeGeometry = new HashMap<Layer,List<Rectangle2D>>();
		boolean retval = addArea(cell, cellBounds, Orientation.IDENT.pureRotate(), true, nextNetNumber, linesInNonMahnattan);

		// add in additional user-specified blockages
		List<SeaOfGatesExtraBlockage> list = sogp.getBlockages();
		for(SeaOfGatesExtraBlockage sogeb : list)
		{
			Rectangle2D bounds = new Rectangle2D.Double(sogeb.getLX(), sogeb.getLY(), sogeb.getHX()-sogeb.getLX(), sogeb.getHY()-sogeb.getLY());
			int metNo = sogeb.getLayer().getFunction().getLevel() - 1;
			Layer layer = primaryMetalLayer[metNo];
			Integer nn = Integer.valueOf((nextNetNumber.intValue() << SHIFTBLOCKBITS) | BLOCKAGEFAKEUSERSUPPLIED);
			nextNetNumber.increment();
			MutableInteger netID = new MutableInteger(nn.intValue());
			addRectangle(bounds, layer, netID, false, false);
		}

		// now remove any geometry that was covered by a removal layer
		for(Layer metLayer : removeGeometry.keySet())
		{
			List<Rectangle2D> removeRects = removeGeometry.get(metLayer);
			for(Rectangle2D rect : removeRects)
			{
				int metNum = metLayer.getFunction().getLevel() - 1;
				int metCol = metLayer.getFunction().getMaskColor();
				Layer primaryMetLayer = primaryMetalLayer[metNum];
				BlockageTree bTree = rTrees.getMetalTree(primaryMetLayer);
				List<SOGBound> thingsThatGetRemoved = new ArrayList<SOGBound>();
				for (Iterator<SOGBound> sea = bTree.search(rect); sea.hasNext();)
				{
					SOGBound sBound = sea.next();
					if (sBound.getMaskColor() != metCol) continue;
					Rectangle2D bound = sBound.getBounds();
					if (bound.getMaxX() <= rect.getMinX() || bound.getMinX() >= rect.getMaxX() ||
						bound.getMaxY() <= rect.getMinY() || bound.getMinY() >= rect.getMaxY()) continue;
					thingsThatGetRemoved.add(sBound);
				}

				// remove those R-Tree elements that get cut
				RTNode<SOGBound> rootFixp = bTree.getRoot();
				for(SOGBound s : thingsThatGetRemoved)
				{
					RTNode<SOGBound> newRootFixp = RTNode.unLinkGeom(null, rootFixp, s);
					if (newRootFixp != rootFixp) bTree.setRoot(rootFixp = newRootFixp);
				}

				// now reinsert geometry that wasn't removed
				for(SOGBound s : thingsThatGetRemoved)
				{
					PolyMerge merge = new PolyMerge();
					merge.addRectangle(metLayer, s.getBounds());
					merge.subtract(metLayer, new Poly(rect));
					List<PolyBase> remaining = merge.getMergedPoints(metLayer, true);
					for(PolyBase pb : remaining)
					{
						ERectangle reducedBound = ERectangle.fromLambda(pb.getBounds2D());
						SOGBound sogb = new SOGBound(reducedBound, s.getNetID(), s.getMaskColor());
						RTNode<SOGBound> newRootFixp = RTNode.linkGeom(null, rootFixp, sogb);
						if (newRootFixp != rootFixp) bTree.setRoot(rootFixp = newRootFixp);
					}
				}
			}
		}
		return retval;
	}

	/**
	 * Method to add geometry to the blockage R-Trees.
	 * @param cell
	 * @param bounds
	 * @param transToTop
	 * @param topLevel
	 * @param nextNetNumber
	 * @param linesInNonMahnattan List to store non-Manhattan geometry
	 * @return true if some of the geometry is nonmanhattan (and may cause problems)
	 */
	private boolean addArea(Cell cell, Rectangle2D bounds, FixpTransform transToTop, boolean topLevel,
		MutableInteger nextNetNumber, List<EPoint> linesInNonMahnattan)
	{
		// first add primitive nodes and arcs
		boolean hasNonmanhattan = false;
		int numCells = 0;
		for (Iterator<Geometric> it = cell.searchIterator(bounds); it.hasNext();)
		{
			Geometric geom = it.next();
			if (geom instanceof NodeInst)
			{
				NodeInst ni = (NodeInst)geom;
				if (ni.isCellInstance()) { numCells++;   continue; }
				PrimitiveNode pNp = (PrimitiveNode)ni.getProto();
				if (pNp.getFunction() == PrimitiveNode.Function.PIN) continue;
				FixpTransform nodeTrans = ni.rotateOut(transToTop);
				Technology tech = pNp.getTechnology();
				Poly[] nodeInstPolyList = tech.getShapeOfNode(ni, true, false, null);
				MutableInteger netNumber = null;
				List<Integer> exclusionLayers = null;
				if (pNp == Generic.tech().routeNode)
				{
					Integer nn = Integer.valueOf(nextNetNumber.intValue() << SHIFTBLOCKBITS);
					nextNetNumber.increment();
					netNumber = new MutableInteger(nn.intValue());
					exclusionLayers = parseExclusionLayers(ni);
				}
				for (int i = 0; i < nodeInstPolyList.length; i++)
				{
					PolyBase poly = nodeInstPolyList[i];
					if (exclusionLayers != null)
					{
						for(Integer lay : exclusionLayers)
						{
							poly.setLayer(primaryMetalLayer[lay.intValue()]);
							if (addLayer(poly, nodeTrans, netNumber, false, linesInNonMahnattan, true)) hasNonmanhattan = true;
						}
					} else
					{
						if (addLayer(poly, nodeTrans, netNumber, false, linesInNonMahnattan, true)) hasNonmanhattan = true;
					}
				}
			} else
			{
				ArcInst ai = (ArcInst)geom;
				if (ai.getProto() == Generic.tech().unrouted_arc) continue;
				Technology tech = ai.getProto().getTechnology();
				PolyBase[] polys = tech.getShapeOfArc(ai);
				for (int i = 0; i < polys.length; i++)
				{
					PolyBase poly = polys[i];
					if (addLayer(poly, transToTop, null, false, linesInNonMahnattan, true)) hasNonmanhattan = true;
				}
			}
		}

		// now add cell contents
		int cellCount = 0;
		for (Iterator<Geometric> it = cell.searchIterator(bounds); it.hasNext();)
		{
			Geometric geom = it.next();
			if (geom instanceof NodeInst)
			{
				NodeInst ni = (NodeInst)geom;
				if (ni.isCellInstance())
				{
					if (topLevel)
					{
						cellCount++;
						setProgressValue(cellCount, numCells+1);
					}
					Rectangle2D subBounds = new Rectangle2D.Double(bounds.getMinX(), bounds.getMinY(),
						bounds.getWidth(), bounds.getHeight());
					DBMath.transformRect(subBounds, ni.transformIn());
					FixpTransform transBack = ni.transformOut(transToTop);
					addArea((Cell)ni.getProto(), subBounds, transBack, false, nextNetNumber, linesInNonMahnattan);
				}
			}
		}
		return hasNonmanhattan;
	}

	/**
	 * Method to parse the exclusion layer on the Generic:RoutingNode object.
	 * Examples:
	 *   ""     no layers excluded
	 *   "ALL"  all layers excluded
	 *   "1"    Metal 1 excluded
	 *   "3,5"  Metals 3 and 5 excluded
	 *   "2-6"  Metals 2 through 6 excluded
	 * @param layers the string with the exclusion layers
	 * @return a List of Integers with exclusion layers
	 */
	private List<Integer> parseExclusionLayers(NodeInst ni)
	{
		List<Integer> exclusionLayers = new ArrayList<Integer>();
		String layers = "";
		Variable var = ni.getVar(Generic.ROUTING_EXCLUSION);
		if (var != null) layers = var.getPureValue(-1);
		layers.replaceAll(" ", "");
		if (layers.length() == 0) return exclusionLayers;
		if (layers.equalsIgnoreCase("all"))
		{
			for(int i=0; i<getNumMetals(); i++)
				exclusionLayers.add(Integer.valueOf(i));
			return exclusionLayers;
		}

		int pt = 0;
		while (pt < layers.length())
		{
			int layNum = TextUtils.atoi(layers.substring(pt));
			while (pt < layers.length() && Character.isDigit(layers.charAt(pt))) pt++;
			if (pt >= layers.length() || layers.charAt(pt) == ',')
			{
				exclusionLayers.add(Integer.valueOf(layNum-1));
				if (pt < layers.length()) pt++;
				continue;
			}
			if (layers.charAt(pt) != '-' || pt >= layers.length()-1)
			{
				System.out.println("ERROR: Routing exclusion node " + ni.describe(false) +
					" has invalid exclusion description: " + layers);
				break;
			}
			pt++;
			int layEnd = TextUtils.atoi(layers.substring(pt));
			while (pt < layers.length() && Character.isDigit(layers.charAt(pt))) pt++;
			for(int i=layNum; i<=layEnd; i++)
				exclusionLayers.add(Integer.valueOf(i-1));
			if (pt < layers.length())
			{
				if (layers.charAt(pt) != ',')
				{
					System.out.println("ERROR: Routing exclusion node " + ni.describe(false) +
						" has invalid exclusion description: " + layers);
					break;
				}
				pt++;
			}
		}

		return exclusionLayers;
	}

	/**
	 * Method to add geometry to the R-Tree.
	 * @param poly the polygon to add (only rectangles are added, so the bounds is used).
	 * @param trans a transformation matrix to apply to the polygon.
	 * @param netID the global network ID of the geometry.
	 * (converted to non-pseudo and stored). False to ignore pseudo-layers.
	 * @param lockTree true to lock the R-Tree before accessing it (when doing parallel routing).
	 * @return true if the geometry is nonmanhattan (and may cause problems).
	 */
	private boolean addLayer(PolyBase poly, FixpTransform trans, MutableInteger netID,
		boolean lockTree, List<EPoint> linesInNonMahnattan, boolean merge)
	{
		boolean isNonmanhattan = false;
		Layer layer = poly.getLayer();
		Layer.Function fun = layer.getFunction();

		// save any removal geometry
		Layer removeAp = removeLayers.get(layer);
		if (removeAp != null)
		{
			List<Rectangle2D> geomsToRemove = removeGeometry.get(removeAp);
			if (geomsToRemove == null) removeGeometry.put(removeAp, geomsToRemove = new ArrayList<Rectangle2D>());
			poly.transform(trans);
			Rectangle2D bounds = poly.getBox();
			if (bounds == null) return true;
			geomsToRemove.add(bounds);
			return false;
		}

		if (fun.isMetal())
		{
			// ignore polygons that aren't solid filled areas
			if (poly.getStyle() != Poly.Type.FILLED) return false;
			poly.transform(trans);
			Rectangle2D bounds = poly.getBox();
			if (bounds == null)
			{
				addPolygon(poly, layer, netID, lockTree);
				Point[] points = poly.getPoints();
				for (int i=1; i<points.length; i++)
				{
					if (points[i-1].getX() != points[i].getX() && points[i-1].getY() != points[i].getY())
					{
						isNonmanhattan = true;
						linesInNonMahnattan.add(EPoint.fromLambda(points[i-1].getX(), points[i-1].getY()));
						linesInNonMahnattan.add(EPoint.fromLambda(points[i].getX(), points[i].getY()));
					}
				}
			} else
			{
				addRectangle(bounds, layer, netID, lockTree, merge);
			}
		} else if (fun.isContact())
		{
			Rectangle2D bounds = poly.getBounds2D();
			DBMath.transformRect(bounds, trans);
			addVia(ERectangle.fromLambda(bounds), layer, netID, lockTree);
		}
		return isNonmanhattan;
	}

	public static String describeMetal(int metal, int color)
	{
		String ret = "M" + (metal+1);
		if (color > 0) ret += (char)('a' + color - 1);
		return ret;
	}

	/**
	 * Method to add a rectangle to the metal R-Tree.
	 * @param bounds the rectangle to add.
	 * @param layer the metal layer on which to add the rectangle.
	 * @param netID the global network ID of the geometry.
	 * @param lockTree true to lock the R-Tree before accessing it (when doing parallel routing).
	 */
	private SOGBound addRectangle(Rectangle2D bounds, Layer layer, MutableInteger netID, boolean lockTree, boolean merge)
	{
		SOGBound sogb = null;
		BlockageTree bTree = rTrees.getMetalTree(layer);

		// avoid duplication
		if (merge)
		{
			List<SOGBound> removeThese = null;
			for (Iterator<SOGBound> sea = bTree.search(bounds); sea.hasNext(); )
			{
				SOGBound sBound = sea.next();
				if (sBound instanceof SOGPoly) continue;

				// if an existing bound is bigger than new one, ignore this
				ERectangle r = sBound.getBounds();
				if (r.getMinX() <= bounds.getMinX() &&
					r.getMaxX() >= bounds.getMaxX() &&
					r.getMinY() <= bounds.getMinY() &&
					r.getMaxY() >= bounds.getMaxY()) return null;

				// if new one is bigger than an existing bound, remove existing one
				if (bounds.getMinX() <= r.getMinX() &&
					bounds.getMaxX() >= r.getMaxX() &&
					bounds.getMinY() <= r.getMinY() &&
					bounds.getMaxY() >= r.getMaxY())
				{
					if (removeThese == null) removeThese = new ArrayList<SOGBound>();
					removeThese.add(sBound);
				}
			}

			if (removeThese != null)
			{
				if (lockTree) bTree.lock();
				try {
					RTNode<SOGBound> rootFixp = bTree.getRoot();
					for(SOGBound s : removeThese)
					{
						RTNode<SOGBound> newRootFixp = RTNode.unLinkGeom(null, rootFixp, s);
						if (newRootFixp != rootFixp) bTree.setRoot(rootFixp = newRootFixp);
					}
				} finally {
					if (lockTree) bTree.unlock();
				}
			}
		}

		if (lockTree) bTree.lock();
		try {
			int maskLayer = layer.getFunction().getMaskColor();
			sogb = new SOGBound(ERectangle.fromLambda(bounds), netID, maskLayer);
			RTNode<SOGBound> rootFixp = bTree.getRoot();
			if (rootFixp == null)
			{
				rootFixp = RTNode.makeTopLevel();
				bTree.setRoot(rootFixp);
			}
			RTNode<SOGBound> newRootFixp = RTNode.linkGeom(null, rootFixp, sogb);
			if (newRootFixp != rootFixp) bTree.setRoot(newRootFixp);
		} finally {
			if (lockTree) bTree.unlock();
		}
		return sogb;
	}

	/**
	 * Method to add a polygon to the metal R-Tree.
	 * @param poly the polygon to add.
	 * @param layer the metal layer on which to add the rectangle.
	 * @param netID the global network ID of the geometry.
	 * @param lockTree true to lock the R-Tree before accessing it (when doing parallel routing).
	 */
	private void addPolygon(PolyBase poly, Layer layer, MutableInteger netID, boolean lockTree)
	{
		BlockageTree bTree = rTrees.getMetalTree(layer);
		if (lockTree) bTree.lock();
		try {
			int maskLayer = layer.getFunction().getMaskColor();
			SOGBound sogb = new SOGPoly(ERectangle.fromLambda(poly.getBounds2D()), netID, poly, maskLayer);
			RTNode<SOGBound> rootFixp = bTree.getRoot();
			if (rootFixp == null)
			{
				rootFixp = RTNode.makeTopLevel();
				bTree.setRoot(rootFixp);
			}
			RTNode<SOGBound> newRootFixp = RTNode.linkGeom(null, rootFixp, sogb);
			if (newRootFixp != rootFixp) bTree.setRoot(newRootFixp);
		} finally {
			if (lockTree) bTree.unlock();
		}
	}

	/**
	 * Method to add a point to the via R-Tree.
	 * @param loc the point to add.
	 * @param layer the via layer on which to add the point.
	 * @param netID the global network ID of the geometry.
	 * @param lockTree true to lock the R-Tree before accessing it (when doing parallel routing).
	 */
	private void addVia(ERectangle rect, Layer layer, MutableInteger netID, boolean lockTree)
	{
		BlockageTree bTree = rTrees.getViaTree(layer);

		// remove duplicate vias and favor colored ones
		int color = layer.getFunction().getMaskColor();
		for(Iterator<SOGBound> it = bTree.search(rect); it.hasNext(); )
		{
			SOGBound sogb = it.next();
			if (sogb.getBounds().getCenterX() == rect.getCenterX() && sogb.getBounds().getCenterY() == rect.getCenterY())
			{
				// found existing via at this location
				if (sogb.getMaskColor() == 0 && color != 0)
					sogb.setMaskColor(color);
				return;
			}
		}

		if (lockTree) bTree.lock();
		try {
			SOGBound sogb = new SOGVia(rect, netID);
			sogb.setMaskColor(color);
			RTNode<SOGBound> rootFixp = bTree.getRoot();
			if (rootFixp == null)
			{
				rootFixp = RTNode.makeTopLevel();
				bTree.setRoot(rootFixp);
			}
			RTNode<SOGBound> newRootFixp = RTNode.linkGeom(null, rootFixp, sogb);
			if (newRootFixp != rootFixp) bTree.setRoot(newRootFixp);
		} finally {
			if (lockTree) bTree.unlock();
		}
	}

	private static class BlockageTree {
		private final ReentrantLock lock = new ReentrantLock();
		private RTNode<SOGBound> root;

		public static BlockageTree emptyTree = new BlockageTree(null);

		private BlockageTree(RTNode<SOGBound> root) {
			this.root = root;
		}

		private void lock() { lock.lock(); }

		private void unlock() { lock.unlock(); }

		private RTNode<SOGBound> getRoot() { return root; }

		private void setRoot(RTNode<SOGBound> root) { this.root = root; }

		private boolean isEmpty() { return root == null; }

		private Iterator<SOGBound> search(Rectangle2D searchArea) {
			if (root == null) {
				return Collections.<SOGBound>emptyList().iterator();
			}
			Iterator<SOGBound> it = new RTNode.Search<SOGBound>(searchArea, root, true);
			return it;
		}
	}

	private class BlockageTrees
	{
		private final BlockageTree[] metalTrees;
		private final BlockageTree[] viaTrees;

		BlockageTrees(int numMetals)
		{
			metalTrees = new BlockageTree[numMetals];
			viaTrees = new BlockageTree[numMetals];
			for (int i = 0; i < metalTrees.length; i++)
			{
				metalTrees[i] = new BlockageTree(null);
				viaTrees[i] = new BlockageTree(null);
			}
		}

		private BlockageTree getMetalTree(Layer lay) {
			if (lay == null) return BlockageTree.emptyTree;
			return metalTrees[lay.getFunction().getLevel() - 1];
		}

		private BlockageTree getViaTree(Layer lay) {
			if (lay == null) return BlockageTree.emptyTree;
			return viaTrees[lay.getFunction().getLevel() - 1];
		}
	}

	public static class SOGNetID
	{
		private MutableInteger netID;

		SOGNetID(MutableInteger netID)
		{
			this.netID = netID;
		}

		/**
		 * Method to return the global network ID for this SOGNetID.
		 * Numbers > 0 are normal network IDs.
		 * Numbers <= 0 are blockages added around the ends of routes.
		 * @return the global network ID for this SOGNetID.
		 */
		public MutableInteger getNetID() { return netID; }

		/**
		 * Method to set the global network ID for this SOGNetID.
		 * @param n the global network ID for this SOGNetID.
		 */
		public void setNetID(MutableInteger n)
		{
			netID = n;
		}

		public void updateNetID(MutableInteger n, Map<Integer,List<MutableInteger>> netIDsByValue)
		{
			if (isSameBasicNet(n)) return;

			// update all MutableIntegers with the old value
			List<MutableInteger> oldNetIDs = netIDsByValue.get(Integer.valueOf(netID.intValue()));
			if (oldNetIDs == null) return;
			Integer netIDI = Integer.valueOf(n.intValue());
			List<MutableInteger> newNetIDs = netIDsByValue.get(netIDI);
			if (newNetIDs == null) netIDsByValue.put(netIDI, newNetIDs = new ArrayList<MutableInteger>());
			for(MutableInteger mi : oldNetIDs)
			{
				mi.setValue(n.intValue());
				newNetIDs.add(mi);
			}
			oldNetIDs.clear();
		}

		/**
		 * Method to tell whether this SOGNetID is on a given network.
		 * Network numbers are encoded integers, where some values indicate
		 * variations on the type of network (for example, the area near routing points
		 * is marked with a "pseudo" blockage that keeps the area clear).
		 * @param otherNetID the network ID of the other net.
		 * @return true if this and the other net IDs are equivalent.
		 */
		public boolean isSameBasicNet(MutableInteger otherNetID)
		{
			int netValue = 0;
			if (netID != null) netValue = netID.intValue();
			if ((netValue >> SHIFTBLOCKBITS) == (otherNetID.intValue() >> SHIFTBLOCKBITS)) return true;
			return false;
		}

		/**
		 * Method to tell whether the network ID on this object is a
		 * pseudo-blockage placed around route endpoints to keep routing traffic away.
		 * @return true if this is pseudo-blockage.
		 */
		public boolean isPseudoBlockage()
		{
			if (netID == null) return false;
			return (netID.intValue() & BLOCKAGEFAKEENDPOINT) != 0;
		}

		/**
		 * Method to tell whether the network ID on this object is a
		 * user-supplied blockage.
		 * @return true if this is user-supplied blockage.
		 */
		public boolean isUserSuppliedBlockage()
		{
			if (netID == null) return false;
			return (netID.intValue() & BLOCKAGEFAKEUSERSUPPLIED) != 0;
		}
	}

	/**
	 * Class to define an R-Tree leaf node for geometry in the blockage data structure.
	 */
	public static class SOGBound extends SOGNetID implements RTBounds
	{
		private ERectangle bound;
		private int maskLayer;

		SOGBound(ERectangle bound, MutableInteger netID, int maskLayer)
		{
			super(netID);
			this.bound = bound;
			this.maskLayer = maskLayer;
		}

		@Override
		public ERectangle getBounds() { return bound; }

		public int getMaskColor() { return maskLayer; }

		public void setMaskColor(int c) { maskLayer = c; }

		public boolean containsPoint(double x, double y)
		{
			return x >= bound.getMinX() && x <= bound.getMaxX() && y >= bound.getMinY() && y <= bound.getMaxY();
		}

		public boolean isManhattan() { return true; }

		@Override
		public String toString() { return "SOGBound on net " + getNetID(); }
	}

	public static class SOGPoly extends SOGBound
	{
		private PolyBase poly;

		SOGPoly(ERectangle bound, MutableInteger netID, PolyBase poly, int maskLayer)
		{
			super(bound, netID, maskLayer);
			this.poly = poly;
		}

		public boolean containsPoint(double x, double y)
		{
			return poly.isInside(new Point2D.Double(x, y));
		}

		public boolean isManhattan()
		{
			Point[] pts = poly.getPoints();
			for(int i=1; i<pts.length; i++)
			{
				if (pts[i].getX() != pts[i-1].getX() && pts[i].getY() != pts[i-1].getY()) return false;
			}
			return true;
		}

		public PolyBase getPoly() { return poly; }
	}

	/**
	 * Class to define an R-Tree leaf node for vias in the blockage data structure.
	 */
	public static class SOGVia extends SOGBound
	{
		SOGVia(ERectangle rect, MutableInteger netID)
		{
			super(rect, netID, 0);
		}

		@Override
		public String toString() { return "SOGVia on net " + getNetID(); }
	}

	/******************************* GLOBAL ROUTER *******************************/

	public GlobalRouter doGlobalRouting(Cell cell, RouteBatch[] routeBatches, RouteBatch[] fakeBatches, double wirePitch)
	{
		// make a graph for Global Routing
		GlobalRouter gr = new GlobalRouter(cell, routeBatches, fakeBatches, wirePitch);

		// Do the routing
		gr.solve();
		return gr;
	}

	public class GlobalRouter
	{
		private int numXBuckets, numYBuckets;
		private GRBucket[] buckets;
		private GREdge[] edges;
		private List<GRNet> nets;

		public List<GRNet> getNets() { return nets; }

		public int getXBuckets() { return numXBuckets; }

		public int getYBuckets() { return numYBuckets; }

		public GlobalRouter(Cell c, RouteBatch[] routeBatches, RouteBatch[] fakeBatches, double wirePitch)
		{
			// determine the number of wires to route
			int total = 0;
			for(RouteBatch rb : routeBatches) total += rb.routesInBatch.size();
			if (fakeBatches != null) for(RouteBatch rb : fakeBatches) total += rb.routesInBatch.size();

			// determine the number of X and Y buckets (areas of the circuit to be routed)
			int size = (int)Math.sqrt(total);
			if (size < 2) size = 2;
			ERectangle bounds = c.getBounds();
			if (bounds.getWidth() > bounds.getHeight())
			{
				numXBuckets = size;
				numYBuckets = (int)Math.round(size * bounds.getHeight() / bounds.getWidth());
				if (numYBuckets < 2) numYBuckets = 2;
			} else
			{
				numXBuckets = (int)Math.round(size * bounds.getWidth() / bounds.getHeight());
				if (numXBuckets < 2) numXBuckets = 2;
				numYBuckets = size;
			}
			double bucketWidth = bounds.getWidth() / numXBuckets;
			double bucketHeight = bounds.getHeight() / numYBuckets;

			// determine the capacity between two buckets
			int capacity = (int)Math.round(bucketWidth / wirePitch);
			if (capacity < 1) capacity = 1;

			// build the buckets
			buckets = new GRBucket[numXBuckets * numYBuckets];
			int t = 0;
			for(int y=0; y<numYBuckets; y++)
			{
				for(int x=0; x<numXBuckets; x++)
				{
					double lX = bounds.getMinX() + x * bucketWidth;
					double hX = lX + bucketWidth;
					double lY = bounds.getMinY() + y * bucketHeight;
					double hY = lY + bucketHeight;
					buckets[t++] = new GRBucket(t, new Rectangle2D.Double(lX, lY, hX-lX, hY-lY));
				}
			}

			// build the connections between buckets
			int numEdges = numXBuckets * (numYBuckets-1) + numYBuckets * (numXBuckets-1);
			edges = new GREdge[numEdges];
			int e = 0;
			for(int x=0; x<numXBuckets; x++)
			{
				for(int y=1; y<numYBuckets; y++)
				{
					int sid = y*numXBuckets + x;
					int eid = (y-1)*numXBuckets + x;
					edges[e++] = new GREdge(buckets[sid], buckets[eid], capacity);
				}
			}
			for(int x=1; x<numXBuckets; x++)
			{
				for(int y=0; y<numYBuckets; y++)
				{
					int sid = y*numXBuckets + x;
					int eid = y*numXBuckets + (x-1);
					edges[e++] = new GREdge(buckets[sid], buckets[eid], capacity);
				}
			}

			// build the networks to be routed
			nets = new ArrayList<GRNet>();
			addBatches(routeBatches, bounds, bucketWidth, bucketHeight);
			if (fakeBatches != null) addBatches(fakeBatches, bounds, bucketWidth, bucketHeight);
		}

		private void addBatches(RouteBatch[] batches, ERectangle bounds, double bucketWidth, double bucketHeight)
		{
			for(RouteBatch rb : batches)
			{
				GRNet nn = null;
				for(NeededRoute nr : rb.routesInBatch)
				{
					int x1 = (int)((nr.aEndpoints.getCenterX() - bounds.getMinX()) / bucketWidth);
					int y1 = (int)((nr.aEndpoints.getCenterY() - bounds.getMinY()) / bucketHeight);
					int x2 = (int)((nr.bEndpoints.getCenterX() - bounds.getMinX()) / bucketWidth);
					int y2 = (int)((nr.bEndpoints.getCenterY() - bounds.getMinY()) / bucketHeight);
					int bucket1 = y1*numXBuckets + x1;
					int bucket2 = y2*numXBuckets + x2;
					if (bucket1 == bucket2) continue;

					if (nn == null)
					{
						nn = new GRNet();
						nets.add(nn);
					}
					GRWire w = new GRWire(nr, buckets[bucket1], buckets[bucket2], nr.aEndpoints.getCenter(), nr.bEndpoints.getCenter());
					nn.addWire(w);
				}
			}
		}

		public void solve()
		{
			// do the routing
			ElapseTimer theTimer = ElapseTimer.createInstance().start();
			route();
			theTimer.end();
			info("Global routing: initialized (took " + theTimer + ")");

			// rip-up and reroute
			int iterations = 4;
			for (int iteration = 0; iteration < iterations; iteration++)
			{
				theTimer.start();
				ripupReroute();
				theTimer.end();
				info("Global routing: Rip-up and reroute pass " + iteration + " (took " + theTimer + ")");
			}

			// set the buckets on each NeededRoute
			for(GRNet net : nets)
			{
				for(GRWire wire : net.wires)
					wire.setPathOnRoute();
			}
		}

		private void route()
		{
			for (GRNet net : nets)
			{
				for (GRWire w : net.getWires())
				{
					if (w.setShortestPath())
						error("ERROR: No path from "+w.n1+" to "+w.n2);
					w.addPath(1);
				}
			}
		}

		private void ripupReroute()
		{
			for (GRNet net : nets)
			{
				for (GRWire w : net.getWires())
				{
					// remove the old routing
					w.addPath(-1);

					// add new path
					if (w.setShortestPath())
						error("ERROR: No path from "+w.n1+" to "+w.n2);
					w.addPath(1);
				}
			}
		}
	}

	public static class GRBucket implements Comparable<GRBucket>
	{
		/** unique bucket number */				private int id;
		/** location of this bucket */			private Rectangle2D bounds;
		/** edges to adjoining buckets */		private List<GREdge> edges;
		/** cost of a path to this bucket */	private double cost;
		/** edge to previous bucket in path */	private GREdge prev;

		public GRBucket(int id, Rectangle2D bounds)
		{
			this.id = id;
			this.bounds = bounds;
			this.edges = new ArrayList<GREdge>();
		}

		public Rectangle2D getBounds() { return bounds; }

		public double getCost() { return cost; }

		public void setCost(double c) { cost = c; }

		public GREdge getPrevEdge() { return prev; }

		public void setPrevEdge(GREdge e) { prev = e; }

		public void addEdge(GREdge e) { edges.add(e); }

		public List<GREdge> getEdges() { return edges; }

		public String toString() { return "BUCKET-" + id; }

		public int compareTo(GRBucket other) { return id - other.id; }
	}

	public static class GRNet
	{
		private List<GRWire> wires;

		GRNet()
		{
			wires = new ArrayList<GRWire>();
		}

		public void addWire(GRWire w) { wires.add(w); }

		public List<GRWire> getWires() { return wires; }
	}

	public static class GRWire
	{
		private GRBucket n1, n2;
		private EPoint pt1, pt2;
		private List<GRPathElement> path;
		private NeededRoute nr;

		public GRWire(NeededRoute nr, GRBucket n1, GRBucket n2, EPoint pt1, EPoint pt2)
		{
			this.nr = nr;
			this.n1 = n1;
			this.n2 = n2;
			this.pt1 = pt1;
			this.pt2 = pt2;
		}

		public void setPathOnRoute()
		{
			nr.buckets = new Rectangle2D[path.size()];
			for(int i=0; i<path.size(); i++) nr.buckets[i] = path.get(i).getBucket().bounds;
		}

		public int getNumPathElements() { return path.size(); }

		public GRBucket getPathBucket(int index) { return path.get(index).getBucket(); }

		public GRBucket getBucket1() { return n1; }

		public GRBucket getBucket2() { return n2; }

		public EPoint getPoint1() { return pt1; }

		public EPoint getPoint2() { return pt2; }

		public NeededRoute getNeededRoute() { return nr; }

		private void addPath(int width)
		{
			for (GRPathElement pe : path)
			{
				if (pe.getEdge() != null)
					pe.getEdge().changeCurrentValue(width);
			}
		}

		/**
		 * Method to find a path between two points.
		 * @return true on error.
		 */
		private boolean setShortestPath()
		{
			GRBucket start = n1, finish = n2;
			path = new ArrayList<GRPathElement>();

			if (start == finish) return false;

			Set<GRWavefrontPoint> h = new TreeSet<GRWavefrontPoint>();
			List<GRBucket> clean = new ArrayList<GRBucket>();

			GRBucket current = start;
			h.add(new GRWavefrontPoint(current, 0));

			for(;;)
			{
				// find the next element in the expanding wavefront
				Iterator<GRWavefrontPoint> it = h.iterator();
				if (it.hasNext())
				{
					GRWavefrontPoint he = it.next();
					current = he.getBucket();
					h.remove(he);
					if (current.getCost() != he.getCost()) continue;
				} else current = null;

				// this is the lowest-cost point on the wavefront, extend to the next bucket
				if (current == null || current == finish) break;

				for (GREdge e : current.getEdges())
				{
					GRBucket next = e.getOtherOne(current);
					if (next == start) continue;

					// figure the cost of going to the next bucket
					double cost = current.getCost() + e.usageCost();
					if (next.getPrevEdge() == null || next.getCost() > cost)
					{
						next.setCost(cost);
						h.add(new GRWavefrontPoint(next, cost));

						if (next.getPrevEdge() == null)
							clean.add(next);
						next.setPrevEdge(e);
					}
				}
			}

			// if we got the finishing bucket, backtrack and build the path
			if (current == finish)
			{
				while (current != null)
				{
					GRPathElement pe = new GRPathElement(current, current.getPrevEdge());
					path.add(pe);
					current = (pe.getEdge() == null) ? null : pe.getEdge().getOtherOne(current);
				}
				Collections.reverse(path);
			} else
			{
				return true;
			}

			// clean all the pointers
			for (GRBucket n : clean)
			{
				n.setPrevEdge(null);
				n.setCost(0);
			}
			return false;
		}
	}

	private static class GREdge
	{
		private GRBucket n1, n2;
		private int current;
		private final int capacity;
		private final double minCost, maxCost;

		public GREdge(GRBucket n1, GRBucket n2, int cap)
		{
			this.n1 = n1;
			this.n2 = n2;
			current = 0;
			minCost = 1;
			maxCost = 16;
			capacity = cap;
			n1.addEdge(this);
			n2.addEdge(this);
		}

		public void changeCurrentValue(int delta) { current += delta; }

		public GRBucket getOtherOne(GRBucket thisOne) { return thisOne == n1 ? n2 : n1; }

		double usageCost()
		{
			if (current <= 0) return minCost;
			if (current >= capacity) return maxCost;
			double ratio = current / (double)capacity;
			return minCost + (maxCost - minCost) * ratio;
		}
	}

	private static class GRPathElement
	{
		private GRBucket n;
		private GREdge e;

		public GRPathElement(GRBucket n, GREdge e)
		{
			this.n = n;
			this.e = e;
		}

		public GRBucket getBucket() { return n; }

		public GREdge getEdge() { return e; }
	}

	private static class GRWavefrontPoint implements Comparable<GRWavefrontPoint>
	{
		private GRBucket n;
		private double cost;

		public GRWavefrontPoint(GRBucket n, double c)
		{
			this.n = n;
			cost = c;
		}

		public GRBucket getBucket() { return n; }

		public double getCost() { return cost; }

		public int compareTo(GRWavefrontPoint other)
		{
			if (cost < other.cost) return -1000000000;
			if (cost > other.cost) return 1000000000;
			return n.compareTo(other.n);
		}
	}
}
