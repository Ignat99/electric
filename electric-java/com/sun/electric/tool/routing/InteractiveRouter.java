/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: InteractiveRouter.java
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

import com.sun.electric.database.EditingPreferences;
import com.sun.electric.database.geometry.EPoint;
import com.sun.electric.database.geometry.ERectangle;
import com.sun.electric.database.geometry.Poly;
import com.sun.electric.database.geometry.PolyMerge;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.Export;
import com.sun.electric.database.prototype.NodeProto;
import com.sun.electric.database.prototype.PortProto;
import com.sun.electric.database.topology.ArcInst;
import com.sun.electric.database.topology.Connection;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.topology.PortInst;
import com.sun.electric.database.variable.ElectricObject;
import com.sun.electric.technology.ArcProto;
import com.sun.electric.technology.Layer;
import com.sun.electric.technology.PrimitiveNode;
import com.sun.electric.technology.PrimitivePort;
import com.sun.electric.technology.SizeOffset;
import com.sun.electric.technology.Technology;
import com.sun.electric.technology.technologies.Artwork;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.JobException;
import com.sun.electric.tool.user.CircuitChangeJobs;
import com.sun.electric.tool.user.Highlight;
import com.sun.electric.tool.user.Highlighter;
import com.sun.electric.tool.user.ui.EditWindow;
import com.sun.electric.util.math.DBMath;
import com.sun.electric.util.math.EDimension;
import com.sun.electric.util.math.FixpRectangle;
import com.sun.electric.util.math.GenMath;
import com.sun.electric.util.math.MutableBoolean;
import com.sun.electric.util.math.Orientation;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * An Interactive Router has several methods that build on Router
 * methods to provide interactive control to user.  It also
 * provides methods for highlighting routes to provide visual
 * feedback to the user.  Finally, non-interactive routing is done only
 * from PortInst to PortInst, whereas interactive routing can start and
 * end on any arc, and can end in space.
 * <p>
 * Note: 'Interactive' is somewhat of a misnomer, as it would imply
 * the route can be incrementally built or changed by the user.  In
 * reality, it is expected that the route simply be rebuilt whenever
 * the user input changes, until the user decides that the route is acceptable,
 * at which point the route can be made.
 * <p>
 * User: gainsley
 */
public abstract class InteractiveRouter extends Router {

    /** for highlighting the start of the route */  private List<Highlight> startRouteHighlights = new ArrayList<Highlight>();
    /** if start has been called */                 private boolean started;
    /** EditWindow we are routing in */             private EditWindow wnd;
    /** Need fat wires */                           private final EditingPreferences ep;

    /** last bad object routed from: prevent too many error messages */ private ElectricObject badStartObject;
    /** last bad object routing to: prevent too many error messages */  private ElectricObject badEndObject;

    public InteractiveRouter(EditingPreferences ep) {
        verbose = true;
        started = false;
        badStartObject = badEndObject = null;
        wnd = null;
        tool = Routing.getRoutingTool();
        this.ep = ep;
    }

    public String toString() { return "Interactive Router"; }

    protected abstract boolean planRoute(Route route, Cell cell, RouteElementPort endRE,
                                Point2D startLoc, Point2D endLoc, Point2D clicked, PolyMerge stayInside, VerticalRoute vroute,
                                boolean contactsOnEndObject, boolean extendArcHead, boolean extendArcTail, Rectangle2D contactArea);

    // ----------------------- Interactive Route Control --------------------------

    /**
     * This stores the currently highlighted objects to highlight
     * in addition to route highlighting.  If routing it cancelled,
     * it also restores the original highlighting.
     */
    public void startInteractiveRoute(EditWindow wnd) {
        this.wnd = wnd;
        // copy current highlights
        startRouteHighlights.clear();
        for (Highlight h : wnd.getHighlighter().getHighlights()) {
            startRouteHighlights.add(h);
        }
        wnd.clearHighlighting();
        started = true;
    }

    /**
     * Cancels interactive routing and restores original highlights
     */
    public void cancelInteractiveRoute() {
        // restore original highlights
        Highlighter highlighter = wnd.getHighlighter();
        highlighter.clear();
        highlighter.setHighlightList(startRouteHighlights);
        highlighter.finished();
        wnd = null;
        started = false;
    }

    /**
     * Make a route between startObj and endObj in the EditWindow_.
     * Uses the point where the user clicked as a parameter to set the route.
     * @param wnd the EditWindow_ the user is editing
     * @param cell the cell in which to create the route
     * @param startObj a PortInst or ArcInst from which to start the route
     * @param endObj a PortInst or ArcInst to end the route on. May be null
     * if the user is drawing to empty space.
     * @param clicked the point where the user clicked
     */
    public void makeRoute(EditWindow wnd, Cell cell, ElectricObject startObj, ElectricObject endObj, Point2D clicked) {
        if (!started) startInteractiveRoute(wnd);
        // plan the route
        EditingPreferences ep = wnd.getEditingPreferences();
        Route route = planRoute(cell, startObj, endObj, clicked, null, ep, true, true, null, null);
        // restore highlights at start of planning, so that
        // they will correctly show up if this job is undone.
        wnd.clearHighlighting();
        wnd.getHighlighter().setHighlightList(startRouteHighlights);
        // create route
        createRoute(route, cell);
        started = false;
    }

    /**
     * Make a vertical route.  Will add in contacts in startPort's technology
     * to be able to connect to endPort.  The added contacts will be placed on
     * top of startPort.  The final contact will be able to connect to <i>arc</i>.
     * @param wnd the EditWindow_ the user is editing
     * @param startPort the start of the route
     * @param arc the arc type that the last contact will be able to connect to
     * @return true on success
     */
    public boolean makeVerticalRoute(EditWindow wnd, PortInst startPort, ArcProto arc) {
        EditingPreferences ep = wnd.getEditingPreferences();
        // do nothing if startPort can already connect to arc
        if (startPort.getPortProto().connectsTo(arc)) return true;

        if (!started) startInteractiveRoute(wnd);

        Point2D startLoc = new Point2D.Double(startPort.getPoly().getCenterX(), startPort.getPoly().getCenterY());
        Poly poly = getConnectingSite(startPort, startLoc, ep, -1);
        RouteElementPort startRE = RouteElementPort.existingPortInst(startPort, poly, ep);
        Route route = new Route();
        route.add(startRE); route.setStart(startRE);

        VerticalRoute vroute = VerticalRoute.newRoute(startPort.getPortProto(), arc);
        if (!vroute.isSpecificationSucceeded()) {
            cancelInteractiveRoute();
            return false;
        }
        ArcProto startArc = vroute.getStartArc();
        ArcProto endArc = vroute.getEndArc();

        ContactSize sizer = new ContactSize(startPort, null, startLoc, startLoc, startLoc, startArc, endArc, false, ep);
        Rectangle2D contactArea = sizer.getContactSize();
        EDimension contactSize = new EDimension(contactArea.getWidth(), contactArea.getHeight());
        int startAngle = sizer.getStartAngle();
        double startArcWidth = sizer.getStartWidth();
        Cell cell = wnd.getCell();

        Route vertRoute = vroute.buildRoute(cell, startLoc, contactSize, startAngle, null, ep, null);
        for (RouteElement re : vertRoute) {
            if (!route.contains(re)) route.add(re);
            route.setEnd(vertRoute.getEnd());
        }
        // create arc between startRE and start of vertical route
        if (route.replacePin(startRE, vertRoute.getStart(), null, ep)) {
            route.remove(startRE);
            if (route.getStart() == startRE) route.setStart(vertRoute.getStart());
        } else {
            addConnectingArc(route, cell, startRE, vertRoute.getStart(), startLoc, startLoc, startArc,
                    startArcWidth, startAngle, true, true, null, null);
        }

        // restore highlights at start of planning, so that
        // they will correctly show up if this job is undone.
        wnd.finishedHighlighting();
        wnd.getHighlighter().setHighlightList(startRouteHighlights);
        new MakeVerticalRouteJob(this, route, cell, true);
        started = false;
        return true;
    }

    private static class MakeVerticalRouteJob extends Router.CreateRouteJob {
        protected MakeVerticalRouteJob(Router router, Route route, Cell cell, boolean verbose) {
            super(router.toString(), route, cell, verbose, Routing.getRoutingTool());
        }

        /** Implemented doIt() method to perform Job */
        @Override
        public boolean doIt() throws JobException {
            if (!super.doIt()) return false;

            EditingPreferences ep = getEditingPreferences();
            RouteElementPort startRE = route.getStart();
            if (startRE.getAction() == RouteElement.RouteElementAction.existingPortInst) {
                // if this is a pin, replace it with the first contact in vertical route
                PortInst pi = startRE.getPortInst();
                NodeInst ni = pi.getNodeInst();
                if (ni.getProto().getFunction().isPin()) {
                	CircuitChangeJobs.Reconnect re = CircuitChangeJobs.Reconnect.erasePassThru(ni, false, true, getEditingPreferences());
                    if (re != null) re.reconnectArcs(ep);
                    if (!ni.hasExports()) ni.kill();
                }
            }
            return true;
       }
    }

    // -------------------------- Highlight Route Methods -------------------------

    /**
     * Make a route and highlight it in the window.
     * @param cell the cell in which to create the route
     * @param startObj a PortInst or ArcInst from which to start the route
     * @param endObj a PortInst or ArcInst to end the route on. May be null
     * if the user is drawing to empty space.
     * @param clicked the point where the user clicked
     */
    public void highlightRoute(EditWindow wnd, Cell cell, ElectricObject startObj, ElectricObject endObj, Point2D clicked) {
        if (!started) startInteractiveRoute(wnd);
        // highlight route
        EditingPreferences ep = wnd.getEditingPreferences();
        Route route = planRoute(cell, startObj, endObj, clicked, null, ep, true, true, null, null);
        highlightRoute(wnd, route, cell);
    }

    /**
     * Highlight a route in the window
     * @param route the route to be highlighted
     */
    public void highlightRoute(EditWindow wnd, Route route, Cell cell) {
        if (!started) startInteractiveRoute(wnd);
        wnd.clearHighlighting();
        //wnd.getHighlighter().setHighlightList(startRouteHighlights);
        for (RouteElement e : route) {
            e.addHighlightArea(wnd.getHighlighter());
        }
        wnd.finishedHighlighting();
    }


    // -------------------- Internal Router Wrapper of Router ---------------------

    /**
     * Plan a route from startObj to endObj, taking into account
     * where the user clicked in the cell.
     * @param cell the cell in which to create the arc
     * @param startObj a PortInst or ArcInst from which to start the route
     * @param endObj a PortInst or ArcInst to end the route on. May be null
     * if the user is drawing to empty space.
     * @param clicked the point where the user clicked
     * @param stayInside the area in which to route (null if not applicable).
     * @param ep EditingPreferences
     * @param extendArcHead true to use default arc extension; false to force no arc extension. (head connects to startObj).
     * @param extendArcTail true to use default arc extension; false to force no arc extension. (tail connects to endObj).
     * @param contactArea
     * @param alignment edge alignment factors (null for no alignment).
     * @return a List of RouteElements denoting route
     */
    public Route planRoute(Cell cell, ElectricObject startObj, ElectricObject endObj, Point2D clicked, PolyMerge stayInside,
        EditingPreferences ep, boolean extendArcHead, boolean extendArcTail, Rectangle2D contactArea, EDimension alignment)
    {
    	return planRoute(cell, startObj, endObj, clicked, stayInside, ep, extendArcHead, extendArcTail, contactArea, alignment, null);
    }
    
    /**
     * Plan a route from startObj to endObj, taking into account
     * where the user clicked in the cell.
     * @param cell the cell in which to create the arc
     * @param startObj a PortInst or ArcInst from which to start the route
     * @param endObj a PortInst or ArcInst to end the route on. May be null
     * if the user is drawing to empty space.
     * @param clicked the point where the user clicked
     * @param stayInside the area in which to route (null if not applicable).
     * @param ep EditingPreferences
     * @param extendArcHead true to use default arc extension; false to force no arc extension. (head connects to startObj).
     * @param extendArcTail true to use default arc extension; false to force no arc extension. (tail connects to endObj).
     * @param contactArea
     * @param alignment edge alignment factors (null for no alignment).
     * @param evenHor if even metals should go horizontal. Only if data is available
     * @return a List of RouteElements denoting route
     */
    public Route planRoute(Cell cell, ElectricObject startObj, ElectricObject endObj, Point2D clicked, PolyMerge stayInside,
        EditingPreferences ep, boolean extendArcHead, boolean extendArcTail, Rectangle2D contactArea, EDimension alignment, Boolean evenHor)
    {
        Route route = new Route();               // hold the route
        if (cell == null) return route;

        if (startObj == endObj) return route;           // can't route to yourself

        RouteElementPort startRE = null;                // denote start of route
        RouteElementPort endRE = null;                  // denote end of route

        // first, convert NodeInsts to PortInsts, if it is one.
        // Now we don't have to worry about NodeInsts.
        startObj = filterRouteObject(startObj, clicked);
        endObj = filterRouteObject(endObj, clicked);
        
        // get the port types at each end so we can build electrical route
        PortProto startPort = getRoutePort(startObj, ep, clicked);
        if (startPort == null) return route;            // start cannot be null
        PortProto endPort;
        if (endObj == null) {
            // end object is null, we are routing to a pin. Figure out what arc to use
            ArcProto useArc = getArcToUse(startPort, null);
            if (useArc == null) return route;
            PrimitiveNode pn = useArc.findOverridablePinProto(ep);
            if (pn == null) // something wrong with technology. No pin found
            {
                System.out.println("No primitive node found for arc '" + useArc.getName() + "'");
                return route; // error
            }
            endPort = pn.getPort(0);
        } else {
            endPort = getRoutePort(endObj, ep, clicked);
        }

        // now determine the electrical route. We need to know what
        // arcs will be used (and their widths, eventually) to determine
        // the Port Poly size for contacts
        VerticalRoute vroute = VerticalRoute.newRoute(startPort, endPort, startObj, endObj);
        if (!vroute.isSpecificationSucceeded()) return new Route();

        // arc width of arcs that will connect to startObj, endObj will determine
        // valid attachment points of arcs
        ArcProto startArc = vroute.getStartArc();
        ArcProto endArc = vroute.getEndArc();
        double startArcWidth = 0;
        double endArcWidth = 0;

//        if (!fatWires)
        {
            // determine arc sizes and
            // start and end points based off of arc sizes and startObj and endObj port sizes
            startArcWidth = getArcWidthToUse(startObj, startArc, 0, true, ep);
            endArcWidth = (endObj == null) ? startArcWidth : getArcWidthToUse(endObj, endArc, 0, true, ep);
            if (startArc == endArc) {
                if (startArcWidth > endArcWidth) endArcWidth = startArcWidth;
                if (endArcWidth > startArcWidth) startArcWidth = endArcWidth;
            }
        }

        // if extension not suppressed, use defaults from arcs
        if (extendArcHead) extendArcHead = startArc.getDefaultInst(ep).isHeadExtended() || endArc.getDefaultInst(ep).isHeadExtended();
        if (extendArcTail) extendArcTail = startArc.getDefaultInst(ep).isTailExtended() || endArc.getDefaultInst(ep).isTailExtended();

        // get valid connecting sites for start and end objects based on the objects
        // themselves, the point the user clicked, and the width of the wire that will
        // attach to each
        Poly startPoly = getConnectingSite(startObj, clicked, ep, startArcWidth);
        Poly endPoly = getConnectingSite(endObj, clicked, ep, endArcWidth);
        Poly startPolyFull = getConnectingSite(startObj, clicked, ep, -1);
        Poly endPolyFull = getConnectingSite(endObj, clicked, ep, -1);

        // Now we can figure out where on the start and end objects the connecting
        // arc(s) should connect
        Point2D startPoint = new Point2D.Double(0, 0);
        Point2D endPoint = new Point2D.Double(0,0);
        getConnectingPoints(startObj, endObj, clicked, startPoint, endPoint, startPoly, endPoly,
        	startArc, endArc, alignment, ep);

        PortInst existingStartPort = null;
        PortInst existingEndPort = null;

        // favor contact cuts on arcs
        boolean contactsOnEndObject = true;

        // plan start of route
        if (startObj instanceof PortInst) {
            // portinst: just wrap in RouteElement
            existingStartPort = (PortInst)startObj;
            startRE = RouteElementPort.existingPortInst(existingStartPort, startPoly, ep);
            
        }
        if (startObj instanceof ArcInst) {
            // arc: figure out where on arc to start
            ArcInst ai = (ArcInst)startObj;
            startRE = findArcConnectingPoint(route, ai, startPoint, stayInside, alignment);
            if (startRE.getPortInst() == ai.getHeadPortInst() && extendArcHead) extendArcHead = ai.isHeadExtended();
            if (startRE.getPortInst() == ai.getTailPortInst() && extendArcHead) extendArcHead = ai.isTailExtended();
            if (startRE.isBisectArcPin()) contactsOnEndObject = false;
        }
        if (startRE == null) {
            if (startObj != badStartObject)
                System.out.println("  Can't route from "+startObj+", no ports");
            badStartObject = startObj;
            return route;
        }

        // plan end of route
        if (endObj != null) {
            // we have somewhere to route to
            if (endObj instanceof PortInst) {
                // portinst: just wrap in RouteElement
                existingEndPort = (PortInst)endObj;
                endRE = RouteElementPort.existingPortInst(existingEndPort, endPoly, ep);
            }
            if (endObj instanceof ArcInst) {
                // arc: figure out where on arc to end
                // use startRE location when possible if connecting to arc
                ArcInst ai = (ArcInst)endObj;
                endRE = findArcConnectingPoint(route, ai, endPoint, stayInside, alignment);
                if (endRE.getPortInst() == ai.getHeadPortInst() && extendArcTail) extendArcTail = ai.isHeadExtended();
                if (endRE.getPortInst() == ai.getTailPortInst() && extendArcTail) extendArcTail = ai.isTailExtended();
                if (endRE.isBisectArcPin()) contactsOnEndObject = true;
            }
            if (endRE == null) {
                if (endObj != badEndObject)
                    System.out.println("Warning: can't route to "+endObj+" because it has no ports");
                badEndObject = endObj;
                endObj = null;
            }
        }
        if (endObj == null) {
            // nowhere to route to, must make new pin to route to
            // first we need to determine what pin to make based on
            // start object
            ArcProto useArc = null;
            if (startObj instanceof PortInst) {
                PortInst startPi = (PortInst)startObj;
                useArc = getArcToUse(startPi.getPortProto(), null);
            }
            if (startObj instanceof ArcInst) {
                ArcInst startAi = (ArcInst)startObj;
                useArc = startAi.getProto();
            }
            // make new pin to route to
            PrimitiveNode pn = useArc.findOverridablePinProto(ep);
            SizeOffset so = pn.getProtoSizeOffset();
            endRE = RouteElementPort.newNode(cell, pn, pn.getPort(0), endPoint,
                    pn.getDefWidth(ep)-so.getHighXOffset()-so.getLowXOffset(),
                    pn.getDefHeight(ep)-so.getHighYOffset()-so.getLowYOffset(), Orientation.IDENT, ep);
        }

        // favor contact on bisected arc
        if (startRE.isBisectArcPin()) contactsOnEndObject = false;
        if (endRE != null && endRE.isBisectArcPin()) contactsOnEndObject = true;

        // special check: if both are existing port instances and are same port, do nothing
        if ((existingEndPort != null) && (existingEndPort == existingStartPort)) return new Route();

        Point2D cornerLoc = getCornerLocation(startPoint, endPoint, clicked, startArc, endArc,
                                              contactsOnEndObject, stayInside, contactArea, startPolyFull, endPolyFull, ep);
        int startAngle = GenMath.figureAngle(startPoint, cornerLoc);
        int endAngle = GenMath.figureAngle(endPoint, cornerLoc);

        // figure out sizes to use
        ContactSize sizer = new ContactSize(startObj, endObj, startPoint, endPoint, cornerLoc, startArc, endArc, false, ep);
        contactArea = sizer.getContactSize();
        startAngle = sizer.getStartAngle();
        endAngle = sizer.getEndAngle();
        startArcWidth = sizer.getStartWidth();
        endArcWidth = sizer.getEndWidth();

		// see if the starting point should be replaced with a curved pin
		if (startObj instanceof PortInst) {
			PortInst startPI = (PortInst)startObj;
			NodeInst startNI = startPI.getNodeInst();
			if (startNI.getFunction().isPin() && startNI.getNumConnections() == 1) {
				// starting from a pin connected to one previous arc: see if the technology supports curved pins
				if (hasCurvedPins(startArc)) {
					// now see if the starting node can be replaced with a curved pin
					Connection con = startNI.getConnections().next();
					Connection oCon = con.getArc().getConnection(1 - con.getEndIndex());
					PortInst prevPI = oCon.getPortInst();
					EPoint previous = oCon.getLocation();
					int forwardAngle = GenMath.figureAngle(startPoint, cornerLoc);
					int backAngle = GenMath.figureAngle(startPoint, previous);
					MutableBoolean flip = new MutableBoolean(false);
					int angleDiff = getAngleDiff(forwardAngle, backAngle, flip);
					Technology tech = ((PrimitiveNode)startNI.getProto()).getTechnology();
					PrimitiveNode pn = findCurvedPin(tech, angleDiff);
					if (pn != null) {
						// make the curved bend
						Orientation orient = getCurvedBendOrientation(backAngle+900, flip);
						EPoint curveCenter = getCurvedBendLocation(pn, orient, angleDiff, startPoint);

						// rearrange the route so that the selected node is replaced with a curve
						Poly prevPoly = new Poly(Poly.from(prevPI.getCenter()));
						startRE = RouteElementPort.existingPortInst(prevPI, prevPoly, ep);
						startRE.setShowHighlight(false);
						route.add(startRE);
						route.setEnd(endRE);
						route.add(RouteElementArc.deleteArc(con.getArc(), ep));
						route.add(RouteElementPort.deleteNode(startNI, ep));

						// figure out location of ports
						NodeInst dNi = NodeInst.makeDummyInstance(pn, ep, curveCenter, pn.getDefWidth(ep), pn.getDefHeight(ep), orient);
						PrimitivePort primPort1 = pn.getPort(0);
						PrimitivePort primPort2 = pn.getPort(1);
						EPoint portLoc1 = dNi.findPortInstFromProto(primPort1).getCenter();
						EPoint portLoc2 = dNi.findPortInstFromProto(primPort2).getCenter();

						// make the curved primitive
						RouteElementPort newPinRE1 = RouteElementPort.newNode(cell, pn, primPort1, curveCenter, pn.getDefWidth(ep), pn.getDefHeight(ep), orient, ep);
						newPinRE1.setConnectingSite(new Poly(Poly.from(portLoc1)));
						route.add(newPinRE1);
						RouteElementPort newPinRE2 = RouteElementPort.newNodeOtherPort(cell, newPinRE1, primPort2, ep);
						newPinRE2.setConnectingSite(new Poly(Poly.from(portLoc2)));
						route.setStart(newPinRE2);

						// connect arcs to the primitive
						RouteElementArc rea1 = addConnectingArc(route, cell, startRE, newPinRE1, prevPI.getCenter(), portLoc1, startArc,
							startArcWidth, startAngle, extendArcHead, extendArcTail, stayInside, alignment);
						rea1.setShowHighlight(false);
						addConnectingArc(route, cell, endRE, newPinRE2, endPoint, portLoc2, endArc,
							endArcWidth, endAngle, extendArcHead, extendArcTail, stayInside, alignment);
						return route;
					}
				}
			}
		}

		// add startRE and endRE to route
		route.setStart(startRE);
		route.setEnd(endRE);

        // create vertical route if needed
        if (startArc != endArc) {
            // build vertical route
            EDimension contactSize = new EDimension(contactArea.getWidth(), contactArea.getHeight());
            Route vertRoute = vroute.buildRoute(cell, cornerLoc, contactSize, startAngle, stayInside, ep, evenHor);
            for (RouteElement re : vertRoute) {
                if (!route.contains(re)) route.add(re);
            }

            // replace endpoints if possible (remove redundant pins), startRE
            if (route.replacePin(startRE, vertRoute.getStart(), stayInside, ep)) {
                route.remove(startRE);
                if (route.getStart() == startRE) route.setStart(vertRoute.getStart());
            } else {
                // create arc between startRE and start of vertical route
                addConnectingArc(route, cell, startRE, vertRoute.getStart(), startPoint, cornerLoc, startArc,
                        startArcWidth, startAngle, extendArcHead, extendArcTail, stayInside, alignment);
            }
            // replace endpoints if possible (remove redundant pins), endRE
            if (route.replacePin(endRE, vertRoute.getEnd(), stayInside, ep)) {
                route.remove(endRE);
                if (route.getEnd() == endRE) route.setEnd(vertRoute.getEnd());
            } else {
                // create arc between endRE and end of vertical route
                addConnectingArc(route, cell, endRE, vertRoute.getEnd(), endPoint, cornerLoc, endArc,
                        endArcWidth, endAngle, extendArcHead, extendArcTail, stayInside, alignment);
            }
        }
        else {
            // startRE and endRE can be connected with an arc.  If one of them is a bisectArcPin,
            // and can be replaced by the other, just replace it and we're done.
            if (route.replaceBisectPin(startRE, endRE)) {
                route.remove(startRE);
                return route;
            } else if (route.replaceBisectPin(endRE, startRE)) {
                route.remove(endRE);
                route.setEnd(startRE);
                return route;
            }
            // check single arc case
            int angle = DBMath.figureAngle(startPoint, endPoint);
            int angleIncrement = startArc.getAngleIncrement(ep);
            if (angleIncrement == 0 || (angle % (10*angleIncrement)) == 0) {
                // single arc
                addConnectingArc(route, cell, startRE, endRE, startPoint, endPoint, startArc,
                        startArcWidth, startAngle, extendArcHead, extendArcTail, stayInside, alignment);
            } else {
				// see if it should be a curved bend
				if (startArc == endArc && hasCurvedPins(startArc)) {
					// possible...see if the angle matches a bend primitive
					MutableBoolean flip = new MutableBoolean(false);
					int angleDiff = getAngleDiff(startAngle, endAngle, flip);
					Technology tech = startArc.getTechnology();
					PrimitiveNode pn = findCurvedPin(tech, angleDiff);
					if (pn != null) {
						// make a curved bend
						Orientation orient = getCurvedBendOrientation(endAngle+2700, flip);
						EPoint curveCenter = getCurvedBendLocation(pn, orient, angleDiff, cornerLoc);

						// figure out location of ports
						NodeInst dNi = NodeInst.makeDummyInstance(pn, ep, curveCenter, pn.getDefWidth(ep), pn.getDefHeight(ep), orient);
						PrimitivePort primPort1 = pn.getPort(0);
						PrimitivePort primPort2 = pn.getPort(1);
						EPoint portLoc1 = dNi.findPortInstFromProto(primPort1).getCenter();
						EPoint portLoc2 = dNi.findPortInstFromProto(primPort2).getCenter();

						// create the curve primitive
						RouteElementPort newPinRE1 = RouteElementPort.newNode(cell, pn, primPort1, curveCenter, pn.getDefWidth(ep), pn.getDefHeight(ep), orient, ep);
						newPinRE1.setConnectingSite(new Poly(Poly.from(portLoc1)));
						route.add(newPinRE1);
						RouteElementPort newPinRE2 = RouteElementPort.newNodeOtherPort(cell, newPinRE1, primPort2, ep);
						newPinRE2.setConnectingSite(new Poly(Poly.from(portLoc2)));
						route.add(newPinRE2);

						// connect arcs to the curve
						addConnectingArc(route, cell, startRE, newPinRE2, startPoint, portLoc2, startArc,
							startArcWidth, startAngle, extendArcHead, extendArcTail, stayInside, alignment);
						addConnectingArc(route, cell, endRE, newPinRE1, endPoint, portLoc1, endArc,
							endArcWidth, endAngle, extendArcHead, extendArcTail, stayInside, alignment);
						return route;
					}
				}

				// just a regular pin at the bend
                PrimitiveNode pn = startArc.findOverridablePinProto(ep);
                SizeOffset so = pn.getProtoSizeOffset();
                double defwidth = pn.getDefWidth(ep)-so.getHighXOffset()-so.getLowXOffset();
                double defheight = pn.getDefHeight(ep)-so.getHighYOffset()-so.getLowYOffset();
                RouteElementPort pinRE = RouteElementPort.newNode(cell, pn, pn.getPort(0), cornerLoc,
                        defwidth, defheight, Orientation.IDENT, ep);
                route.add(pinRE);
                addConnectingArc(route, cell, startRE, pinRE, startPoint, cornerLoc, startArc,
                        startArcWidth, startAngle, extendArcHead, extendArcTail, stayInside, alignment);
                addConnectingArc(route, cell, endRE, pinRE, endPoint, cornerLoc, endArc,
                        endArcWidth, endAngle, extendArcHead, extendArcTail, stayInside, alignment);
            }

        }
        return route;
    }

    /**
     * Method to determine whether a given technology has curved pins that
     * connect a given arc.
     * @param tech the Technology.
     * @param arc the Arc Prototype to connect.
     * @return true if there are curved pins in the technology that connect to the arc.
     */
    private Map<ArcProto,Boolean> hasCurvedPins = new HashMap<ArcProto,Boolean>();

    private boolean hasCurvedPins(ArcProto arc)
    {
    	Boolean hasIt = hasCurvedPins.get(arc);
    	if (hasIt == null)
    	{
    		hasIt = Boolean.FALSE;
    		Technology tech = arc.getTechnology();
			for(Iterator<PrimitiveNode> it = tech.getNodes(); it.hasNext(); )
			{
				PrimitiveNode pn = it.next();
				if (!pn.isCurvedPin()) continue;
				if (!pn.getPort(0).connectsTo(arc)) continue;
				hasIt = Boolean.TRUE;
				break;
			}
			hasCurvedPins.put(arc, hasIt);
    	}
		return hasIt.booleanValue();
    }

    private int getAngleDiff(int startAngle, int endAngle, MutableBoolean flip)
    {
		flip.setValue(false);
		if (startAngle > endAngle) {
			if (startAngle - endAngle > 1800) endAngle += 3600;
		} else {
			if (endAngle - startAngle > 1800) startAngle += 3600;
		}
		int angleDiff = Math.abs(startAngle - endAngle);
		if (startAngle > endAngle) flip.setValue(true);
		return angleDiff;
    }

    private Orientation getCurvedBendOrientation(int angle, MutableBoolean flip)
    {
		Orientation orient = Orientation.fromAngle(angle%3600);
		if (flip.booleanValue()) orient = orient.concatenate(Orientation.X);
		return orient;
    }

    private EPoint getCurvedBendLocation(PrimitiveNode pn, Orientation orient, int angleDiff, Point2D ctr)
    {
		NodeInst dNi = NodeInst.makeDummyInstance(pn, ep);
		ERectangle bounds = dNi.getBounds();
		Point2D coord;
		if (angleDiff == 900) {
			// place 90-degree bend so that former pin location is at upper-right
			coord = new Point2D.Double(-bounds.getWidth()/2, -bounds.getHeight()/2);
		} else {
			// place 45-degree bend so that former pin location is right side at 22.5-degree projection
			coord = GenMath.intersect(new Point2D.Double(bounds.getMinX(), bounds.getMinY()), 225,
				new Point2D.Double(bounds.getMaxX(), bounds.getMinY()), 900);
			coord.setLocation(-coord.getX(), -coord.getY());
		}
		coord = orient.transformPoint(coord);
		EPoint curveCenter = EPoint.fromLambda(ctr.getX() + coord.getX(), ctr.getY() + coord.getY());
		return curveCenter;
    }

    private PrimitiveNode findCurvedPin(Technology tech, int desiredAngle)
    {
    	for(Iterator<PrimitiveNode> it = tech.getNodes(); it.hasNext(); ) {
			PrimitiveNode pn = it.next();
			if (!pn.isCurvedPin()) continue;
			PrimitivePort pp1 = null, pp2 = null;
			for(Iterator<PrimitivePort> pIt = pn.getPrimitivePorts(); pIt.hasNext(); ) {
				PrimitivePort pp = pIt.next();
				if (pp1 == null) pp1 = pp; else
					if (pp2 == null) pp2 = pp;
			}
			if (pp1 != null && pp2 != null) {
				NodeInst dNi = NodeInst.makeDummyInstance(pn, ep);
				ERectangle bounds = dNi.getBounds();
				EPoint ctr = EPoint.fromLambda(bounds.getMinX(), bounds.getMinY());
				PortInst p1 = dNi.findPortInstFromProto(pp1);
				PortInst p2 = dNi.findPortInstFromProto(pp2);
				EPoint p1ctr = p1.getCenter();
				EPoint p2ctr = p2.getCenter();
				int p1Angle = GenMath.figureAngle(ctr, p1ctr);
				int p2Angle = GenMath.figureAngle(ctr, p2ctr);
				int aDiff = Math.abs(p1Angle - p2Angle);
				if (aDiff > 1800) aDiff = 3600 - aDiff;
				aDiff = 1800 - aDiff;
				if (aDiff == desiredAngle) return pn;
			}
    	}
    	return null;
    }

    // -------------------- Internal Router Utility Methods --------------------

    /**
     * If routeObj is a NodeInst, first thing we do is get the nearest PortInst
     * to where the user clicked, and use that instead.
     * @param routeObj the route object (possibly a NodeInst).
     * @param clicked where the user clicked
     * @return the PortInst on the NodeInst closest to where the user clicked,
     * or just the routeObj back if it is not a NodeInst.
     */
    protected static ElectricObject filterRouteObject(ElectricObject routeObj, Point2D clicked) {
        if (routeObj instanceof NodeInst) {
            return ((NodeInst)routeObj).findClosestPortInst(clicked);
        }
        if (routeObj instanceof Export) {
            Export exp = (Export)routeObj;
            return exp.getOriginalPort();
        }
        return routeObj;
    }

    /**
     * Get the PortProto associated with routeObj (it should be either
     * a ArcInst or a PortInst, otherwise this will return null).
     * @param routeObj the route object
     * @param ep EditingPreferences
     * @return the PortProto for this route object
     */
    protected static PortProto getRoutePort(ElectricObject routeObj, EditingPreferences ep, Point2D clicked) {
        assert(!(routeObj instanceof NodeInst));
        if (routeObj instanceof ArcInst) {
            ArcInst ai = (ArcInst)routeObj;
            double headDist = ai.getHeadLocation().distance(clicked);
            double tailDist = ai.getTailLocation().distance(clicked);
            if (headDist < tailDist) return ai.getHeadPortInst().getPortProto();
            return ai.getTailPortInst().getPortProto();
        }
        if (routeObj instanceof PortInst) {
            PortInst pi = (PortInst)routeObj;
            return pi.getPortProto();
        }
        return null;
    }

    /**
     * Get the connecting points for the start and end objects of the route. This fills in
     * the two Point2D's startPoint and endPoint. These will be the end points of an arc that
     * connects to either startObj or endObj.
     * @param startObj the start route object
     * @param endObj the end route object
     * @param clicked where the user clicked
     * @param startPoint point inside startPoly on startObj to connect arc to
     * @param endPoint point inside endPoly on endObj to connect arc to
     * @param startPoly valid port site on startObj
     * @param endPoly valid port site on endObj
     * @param startArc the arc type to connect to startObj
     * @param endArc the arc type to connect to endObj
     * @param ep EditingPreferences.
     */
    protected static void getConnectingPoints(ElectricObject startObj, ElectricObject endObj, Point2D clicked,
                                              Point2D startPoint, Point2D endPoint, Poly startPoly, Poly endPoly,
                                              ArcProto startArc, ArcProto endArc, EDimension alignment, EditingPreferences ep) {

        if (alignment == null) alignment = ep.getAlignmentToGrid();

        if ((startPoly.getBox() == null && startPoly.getPoints().length != 2) ||
            (endPoly != null && endPoly.getBox() == null && endPoly.getPoints().length != 2)) {
            // special case: one of the polys is not a rectangle
            startPoint.setLocation(startPoly.closestPoint(clicked));
            if (endPoly == null) {
                endPoint.setLocation(getClosestOrthogonalPoint(startPoint, clicked));
            } else {
                endPoint.setLocation(endPoly.closestPoint(clicked));
            }
            return;
        }

        // default is center point, aligned to grid
        Rectangle2D startBounds = new Rectangle2D.Double(startPoly.getBounds2D().getX(), startPoly.getBounds2D().getY(),
                                                         startPoly.getBounds2D().getWidth(), startPoly.getBounds2D().getHeight());
        getAlignedCenter(startBounds, alignment, startPoint);
        boolean manhattan = (startArc.getAngleIncrement(ep) == 90);

        // if startObj is arc instance
        if (startObj instanceof ArcInst) {
            ArcInst ai = (ArcInst)startObj;
            if (manhattan && (ai.getHeadLocation().getX() == ai.getTailLocation().getX() || ai.getHeadLocation().getY() == ai.getTailLocation().getY()))
            {
	            // if nothing to connect to, clicked will determine connecting point on startPoly
	            // endPoint will be location of new pin
                double x = getClosestValue(startBounds.getMinX(), startBounds.getMaxX(), clicked.getX(), alignment.getWidth());
                double y = getClosestValue(startBounds.getMinY(), startBounds.getMaxY(), clicked.getY(), alignment.getHeight());
	            startPoint.setLocation(x, y);
            } else
            {
            	startPoint.setLocation(GenMath.closestPointToSegment(ai.getHeadLocation(), ai.getTailLocation(), clicked));
            	manhattan = false;
            }
        }

        if (endPoly == null) {
            // if arc, find place to connect to. Otherwise use the center point (default)
            int angleIncrement = endArc.getAngleIncrement(ep);
            endPoint.setLocation(getClosestAngledPoint(startPoint, clicked, angleIncrement, alignment));
            //endPoint.setLocation(getClosestOrthogonalPoint(startPoint, clicked));
            // however, if this is an Artwork technology, just put end point at mouse
            if (startArc.getTechnology() == Artwork.tech())
                endPoint.setLocation(clicked);
            return;
        }

        Rectangle2D endBounds = new Rectangle2D.Double(endPoly.getBounds2D().getX(), endPoly.getBounds2D().getY(),
                                                       endPoly.getBounds2D().getWidth(), endPoly.getBounds2D().getHeight());
        getAlignedCenter(endBounds, alignment, endPoint);

        if (endObj instanceof ArcInst) {
            ArcInst ai = (ArcInst)endObj;
            if (manhattan && (ai.getHeadLocation().getX() == ai.getTailLocation().getX() || ai.getHeadLocation().getY() == ai.getTailLocation().getY()))
            {
	            // if nothing to connect to, clicked will determine connecting point on startPoly
	            // endPoint will be location of new pin
	            double x = getClosestValue(endBounds.getMinX(), endBounds.getMaxX(), clicked.getX(), alignment.getWidth());
	            double y = getClosestValue(endBounds.getMinY(), endBounds.getMaxY(), clicked.getY(), alignment.getHeight());
	            endPoint.setLocation(x, y);
            } else
            {
            	endPoint.setLocation(GenMath.closestPointToSegment(ai.getHeadLocation(), ai.getTailLocation(), startPoint));

            	// for nonmanhattan angles, check for other possible endpoints
            	int increment = startArc.getAngleIncrement(ep);
            	if (increment > 0 && increment < 90)
            	{
            		Point2D bestEnd = null;
            		double bestDist = Double.MAX_VALUE;            		
            		for(int a=0; a<180; a+=increment)
            		{
                    	Point2D intPt = GenMath.intersect(ai.getHeadLocation(), ai.getAngle(), startPoint, a*10);
                    	if (intPt == null) continue;
                        if (intPt.getX() >= Math.min(ai.getHeadLocation().getX(), ai.getTailLocation().getX()) ||
                        	intPt.getX() <= Math.max(ai.getHeadLocation().getX(), ai.getTailLocation().getX()) ||
                        	intPt.getY() >= Math.min(ai.getHeadLocation().getY(), ai.getTailLocation().getY()) ||
                        	intPt.getY() <= Math.max(ai.getHeadLocation().getY(), ai.getTailLocation().getY()))
                        {
                        	double dist = intPt.distance(clicked);
                        	if (dist < bestDist)
                        	{
                        		bestDist = dist;
                        		bestEnd = intPt;
                        	}
                        }
            		}
            		if (bestEnd != null)
                    	endPoint.setLocation(bestEnd.getX(), bestEnd.getY());
            	}

            	// see if that intersection point is actually on the segment
            	double headX = ai.getHeadLocation().getX(), headY = ai.getHeadLocation().getY();
            	double tailX = ai.getTailLocation().getX(), tailY = ai.getTailLocation().getY();
            	double minX = Math.min(headX, tailX), maxX = Math.max(headX, tailX);
            	double minY = Math.min(headY, tailY), maxY = Math.max(headY, tailY);
                if (endPoint.getX() < minX || endPoint.getX() > maxX ||
            		endPoint.getY() < minY || endPoint.getY() > maxY)
                {
                    if ((startPoint.getX() < minX || startPoint.getX() > maxX) &&
                    	(startPoint.getY() >= minY || startPoint.getY() <= maxY))
                    {
                    	int otherAng = (ai.getAngle()+900) % 3600;
                    	Point2D intPt = GenMath.intersect(ai.getHeadLocation(), ai.getAngle(), startPoint, otherAng);
                    	if (intPt == null)
						{
							System.out.println("Error: Could not determine intersection of "+(ai.getAngle()/10)+"-degree line through ("+
								headX+","+headY+") and " + (otherAng/10) + "-degree line through ("+ startPoint.getX()+","+startPoint.getY()+")");
						} else
							endPoint.setLocation(intPt);
                    } else if ((startPoint.getY() < minY || startPoint.getY() > maxY) &&
                    	(startPoint.getX() >= minX || startPoint.getX() <= maxX))
                    {
                    	int otherAng = (ai.getAngle()+900) % 3600;
                    	Point2D intPt = GenMath.intersect(ai.getHeadLocation(), ai.getAngle(), startPoint, otherAng);
						if (intPt == null)
						{
							System.out.println("Error: Could not determine intersection of "+(ai.getAngle()/10)+"-degree line through ("+
								headX+","+headY+") and " + (otherAng/10) + "-degree line through ("+ startPoint.getX()+","+startPoint.getY()+")");
						} else
							endPoint.setLocation(intPt);
                    } else
                    {
	    	            double x = getClosestValue(endBounds.getMinX(), endBounds.getMaxX(), clicked.getX(), alignment.getWidth());
	    	            double y = getClosestValue(endBounds.getMinY(), endBounds.getMaxY(), clicked.getY(), alignment.getHeight());
	    	            endPoint.setLocation(x, y);
                    }
                }
            	manhattan = false;
            }
        }

        double lowerBoundX = Math.max(startBounds.getMinX(), endBounds.getMinX());
        double upperBoundX = Math.min(startBounds.getMaxX(), endBounds.getMaxX());
        boolean overlapX = lowerBoundX <= upperBoundX;
        double lowerBoundY = Math.max(startBounds.getMinY(), endBounds.getMinY());
        double upperBoundY = Math.min(startBounds.getMaxY(), endBounds.getMaxY());
        boolean overlapY = lowerBoundY <= upperBoundY;

        if (ep.isFatWires()) {
            Rectangle2D startObjBounds = new Rectangle2D.Double(startPoly.getBounds2D().getX(), startPoly.getBounds2D().getY(),
                                                             startPoly.getBounds2D().getWidth(), startPoly.getBounds2D().getHeight());
            Rectangle2D endObjBounds = new Rectangle2D.Double(endPoly.getBounds2D().getX(), endPoly.getBounds2D().getY(),
                                                           endPoly.getBounds2D().getWidth(), endPoly.getBounds2D().getHeight());
            //Rectangle2D startObjBounds = getBounds(startObj);
            //Rectangle2D endObjBounds = getBounds(endObj);
            boolean objsOverlap = false;
            if (startObjBounds != null && endObjBounds != null) {
                if (startArc == endArc && startObjBounds.intersects(endObjBounds))
                    objsOverlap = true;
            }
            // grid align if needed
            Point2D startCenter = new Point2D.Double(startBounds.getCenterX(), startBounds.getCenterY());
            Point2D endCenter = new Point2D.Double(endBounds.getCenterX(), endBounds.getCenterY());
            gridAlignWithinBounds(startCenter, startBounds, alignment);
            gridAlignWithinBounds(endCenter, endBounds, alignment);

            boolean startObjInEndObjInX = false;
            boolean startObjInEndObjInY = false;
            boolean endObjInStartObjInX = false;
            boolean endObjInStartObjInY = false;
            // special case: start object is completely inside end object in one dimension
            if (startObjBounds.getMinX() >= endObjBounds.getMinX() && startObjBounds.getMaxX() <= endObjBounds.getMaxX()) {
                startObjInEndObjInX = true;
            }
            if (startObjBounds.getMinY() >= endObjBounds.getMinY() && startObjBounds.getMaxY() <= endObjBounds.getMaxY()) {
                startObjInEndObjInY = true;
            }
            // special case: end object is completely inside start object in one dimension
            if (endObjBounds.getMinX() >= startObjBounds.getMinX() && endObjBounds.getMaxX() <= startObjBounds.getMaxX()) {
                endObjInStartObjInX = true;
            }
            if (endObjBounds.getMinY() >= startObjBounds.getMinY() && endObjBounds.getMaxY() <= startObjBounds.getMaxY()) {
                endObjInStartObjInY = true;
            }

            // normally we route center to center (for PortInsts) in fat wiring mode,
            // but if the objects overlap (both ports and objects themselves, then we will route directly
            if (!objsOverlap || !overlapX) {
                // center X on both objects (if they are port instances)
                if ((startObj instanceof PortInst) && !endObjInStartObjInX) {
                    startBounds.setRect(startCenter.getX(), startBounds.getY(), 0, startBounds.getHeight());
                }
                if ((endObj instanceof PortInst) && !startObjInEndObjInX) {
                    endBounds.setRect(endCenter.getX(), endBounds.getY(), 0, endBounds.getHeight());
                }
            }
            if (!objsOverlap || !overlapY) {
                // center Y on both objects (if they are port instances)
                if ((startObj instanceof PortInst) && !endObjInStartObjInY) {
                    startBounds.setRect(startBounds.getX(), startCenter.getY(), startBounds.getWidth(), 0);
                }
                if ((endObj instanceof PortInst) && !startObjInEndObjInY) {
                    endBounds.setRect(endBounds.getX(), endCenter.getY(), endBounds.getWidth(), 0);
                }
            }
            // recalculate overlaps in case bounds have changed
            lowerBoundX = Math.max(startBounds.getMinX(), endBounds.getMinX());
            upperBoundX = Math.min(startBounds.getMaxX(), endBounds.getMaxX());
            overlapX = lowerBoundX <= upperBoundX;
            lowerBoundY = Math.max(startBounds.getMinY(), endBounds.getMinY());
            upperBoundY = Math.min(startBounds.getMaxY(), endBounds.getMaxY());
            overlapY = lowerBoundY <= upperBoundY;
        }

        if (startPoly.getPoints().length == 2 && endPoly.getPoints().length == 2) {
            // lines, allow special case where lines can be non-Manhattan
            Point2D [] points1 = startPoly.getPoints();
            Point2D [] points2 = endPoly.getPoints();
            Point2D intersection = getIntersection(points1, points2);
            if (intersection != null)
            {
                if (Job.getDebug())
                {
                    System.out.println("===========================================================");
                    System.out.println("Start Poly: "+points1[0]+", "+points1[1]);
                    System.out.println("End Poly: "+points2[0]+", "+points2[1]);
                    System.out.println("Intersection Point: "+intersection);
                    System.out.println("===========================================================");
                }
                startPoint.setLocation(intersection);
                endPoint.setLocation(intersection);
                return;
            }
        }

        // check if bounds share X and Y space (Manhattan rectangular polygons only)
        if (manhattan)
        {
	        if (lowerBoundX <= upperBoundX) {
	            double x = getClosestValue(lowerBoundX, upperBoundX, clicked.getX(), alignment.getWidth());
	            startPoint.setLocation(x, startPoint.getY());
	            endPoint.setLocation(x, endPoint.getY());
	        } else {
	            // otherwise, use closest point in bounds to the other port
	            // see which one is higher in X...they don't overlap, so any X coordinate in bounds is comparable
	            if (startBounds.getMinX() > endBounds.getMaxX()) {
	                startPoint.setLocation(startBounds.getMinX(), startPoint.getY());
	                endPoint.setLocation(endBounds.getMaxX(), endPoint.getY());
	            } else {
	                startPoint.setLocation(startBounds.getMaxX(), startPoint.getY());
	                endPoint.setLocation(endBounds.getMinX(), endPoint.getY());
	            }
	        }
	        // if bounds share y-space, use closest y within that space to clicked point
	        if (lowerBoundY <= upperBoundY) {
	            double y = getClosestValue(lowerBoundY, upperBoundY, clicked.getY(), alignment.getHeight());
	            startPoint.setLocation(startPoint.getX(), y);
	            endPoint.setLocation(endPoint.getX(), y);
	        } else {
	            // otherwise, use closest point in bounds to the other port
	            // see which one is higher in Y...they don't overlap, so any Y coordinate in bounds is comparable
	            if (startBounds.getMinY() > endBounds.getMaxY()) {
	                startPoint.setLocation(startPoint.getX(), startBounds.getMinY());
	                endPoint.setLocation(endPoint.getX(), endBounds.getMaxY());
	            } else {
	                startPoint.setLocation(startPoint.getX(), startBounds.getMaxY());
	                endPoint.setLocation(endPoint.getX(), endBounds.getMinY());
	            }
	        }
        }
//        if (alignment.getWidth() > 0 && alignment.getHeight() > 0) {
//        	long sX = (long)(startPoint.getX() / alignment.getWidth());
//        	long sY = (long)(startPoint.getY() / alignment.getHeight());
//        	long eX = (long)(endPoint.getX() / alignment.getWidth());
//        	long eY = (long)(endPoint.getY() / alignment.getHeight());
//            if (sX * alignment.getWidth() != startPoint.getX() ||
//            	eX * alignment.getWidth() != endPoint.getX() ||
//                sY * alignment.getHeight() != startPoint.getY() ||
//                eY * alignment.getHeight() != endPoint.getY()) {
//                System.out.println("Start point (" + startPoint.getX() + "," + startPoint.getY() + ") and/or end point (" +
//                	endPoint.getX() + "," + endPoint.getY() + ") not aligned to alignment: (" + alignment.getWidth() +
//                	"," + alignment.getHeight() + ")");
//            }
//        }
    }

    /**
     * Grid align the point within the confine of bounds. This assumes the point is already within the bounds.
     * @param pt the point to align
     * @param bounds the bounds within to align
     * @param alignment the alignment
     */
    private static void gridAlignWithinBounds(Point2D pt, Rectangle2D bounds, EDimension alignment)
    {
        if (alignment == null) return;
        double x = pt.getX();
        double y = pt.getY();
        if (alignment.getWidth() > 0) {
            double xfloor = Math.floor(x/alignment.getWidth()) * alignment.getWidth();
            double xceil = Math.ceil(x/alignment.getWidth()) * alignment.getWidth();
            if (xfloor >= bounds.getMinX() && xfloor <= bounds.getMaxX()) {
                x = xfloor;
            } else if (xceil >= bounds.getMinX() && xceil <= bounds.getMaxX()) {
                x = xceil;
            }
        }
        if (alignment.getHeight() > 0) {
            double yfloor = Math.floor(y/alignment.getHeight()) * alignment.getHeight();
            double yceil = Math.ceil(y/alignment.getHeight()) * alignment.getHeight();
            if (yfloor >= bounds.getMinY() && yfloor <= bounds.getMaxY()) {
                y = yfloor;
            } else if (yceil >= bounds.getMinY() && yceil <= bounds.getMaxY()) {
                y = yceil;
            }
        }
        pt.setLocation(x, y);
    }

    /**
     * Method to find the center of a Rectangle, grid aligned, and to grid-align the rectangle.
     * @param bounds the rectangle to evaluate and align.
     * @param alignment the alignment in X and Y.
     * @param ctr the center point is stored here.
     */
	private static void getAlignedCenter(Rectangle2D bounds, EDimension alignment, Point2D ctr)
	{
		double cX = bounds.getCenterX();
		double cY = bounds.getCenterY();
		if (alignment != null)
		{
			if (alignment.getWidth() > 0)
			{
				double xx = cX / alignment.getWidth();
				long xxL = Math.round(xx);
				if (xx != xxL)
				{
					double newX = xxL * alignment.getWidth();
					if (newX >= bounds.getMinX() && newX <= bounds.getMaxX()) cX = newX;
				}
				double lX = Math.ceil(bounds.getMinX() / alignment.getWidth()) * alignment.getWidth();
				double hX = Math.floor(bounds.getMaxX() / alignment.getWidth()) * alignment.getWidth();
				if (lX <= bounds.getMaxX() && hX >= bounds.getMinX())
					bounds.setRect(lX, bounds.getMinY(), hX-lX, bounds.getHeight());
			}
			if (alignment.getHeight() > 0)
			{
				double yy = cY / alignment.getHeight();
				long yyL = Math.round(yy);
				if (yy != yyL)
				{
					double newY = yyL * alignment.getHeight();
					if (newY >= bounds.getMinY() && newY <= bounds.getMaxY()) cY = newY;
				}
				double lY = Math.ceil(bounds.getMinY() / alignment.getHeight()) * alignment.getHeight();
				double hY = Math.floor(bounds.getMaxY() / alignment.getHeight()) * alignment.getHeight();
				if (lY <= bounds.getMaxY() && hY >= bounds.getMinY())
					bounds.setRect(bounds.getMinX(), lY, bounds.getWidth(), hY-lY);
			}
		}
		ctr.setLocation(cX, cY);
	}

    /**
     * Get the intersection point of the two line segments, or null if none
     * @param points1 An array of two points that define line 1
     * @param points2 An array of two points that define line 2
     * @return the intersection point, or null if none
     */
    public static Point2D getIntersection(Point2D [] points1, Point2D [] points2) {
        // Checking if line1 is not a singular point
        if (DBMath.areEquals(points1[0], points1[1]) || DBMath.areEquals(points2[0], points2[1]))
        {
            if (Job.getDebug())
                System.out.println("Line is a singular point in InteractiveRouter.getIntersection");
            return null;
        }
        Line2D line1 = new Line2D.Double(points1[0], points1[1]);
        Line2D line2 = new Line2D.Double(points2[0], points2[1]);

        if (!line1.intersectsLine(line2))
            return null;
        double [] co1 = getLineCoeffs(line1);
        double [] co2 = getLineCoeffs(line2);
        // determinant = A1*B2 - A2*B1

        double det = co1[0]*co2[1] - co2[0]*co1[1];
        if (det == 0)
        {
        	// lines are parallel.  Since they already passed the "intersection" test, they must meet at an end
        	if (points1[0].getX() == points2[0].getX() && points1[0].getY() == points2[0].getY()) return points1[0];
        	if (points1[0].getX() == points2[1].getX() && points1[0].getY() == points2[1].getY()) return points1[0];
        	if (points1[1].getX() == points2[0].getX() && points1[1].getY() == points2[0].getY()) return points1[1];
        	if (points1[1].getX() == points2[1].getX() && points1[1].getY() == points2[1].getY()) return points1[1];
        	return null;
        }

        // x = (B2*C1 - B1*C2) / determinant
        double x = (co2[1]*co1[2] - co1[1]*co2[2])/det;

        // y = (A1*C2 - A2*C1) / determinant
        double y = (co1[0]*co2[2] - co2[0]*co1[2])/det;
        return new Point2D.Double(x, y);
    }

    /**
     * Get the coefficients of the line of the form Ax + By = C.
     * Can't use y = Ax + B because it does not allow x = A type equations.
     * @param line the line
     * @return an array of the values A,B,C
     */
    private static double [] getLineCoeffs(Line2D line) {
        double A = line.getP2().getY() - line.getP1().getY();
        double B = line.getP1().getX() - line.getP2().getX();
        double C = A * line.getP1().getX() + B * line.getP1().getY();
        return new double [] {A,B,C};
    }

    /**
     * Get bounds of primitive instance. Returns null if object is not an instance of a primitive.
     * @param obj the object
     * @return the bounds of the instance
     */
    protected static Rectangle2D getBounds(ElectricObject obj) {
        if (obj instanceof ArcInst) {
            ArcInst ai = (ArcInst)obj;
            return ai.getBounds();
        }
        if (obj instanceof PortInst) {
            PortInst pi = (PortInst)obj;
            obj = pi.getNodeInst();
        }
        if (obj instanceof NodeInst) {
            NodeInst ni = (NodeInst)obj;
            NodeProto np = ni.getProto();
            if (np instanceof PrimitiveNode) {
                return ni.getBounds();
            }
        }
        return null;
    }

    /**
     * Get the connecting site of the electric object.
     * <ul>
     * <li>For NodeInsts, this is the nearest portinst to "clicked", which is then subject to:
     * <li>For PortInsts, this is the nearest site of a multi-site port, or just the entire port
     * <li>For ArcInsts, this is a poly composed of the head location and the tail location
     * </ul>
     * See NodeInst.getShapeOfPort() for more details.
     * @param obj the object to get a connection site for
     * @param clicked used to find the nearest portinst on a nodeinst, and nearest
     * site on a multi-site port
     * @param arcWidth contacts port sites are restricted by the size of arcs connecting
     * to them, such that the arc width does extend beyond the contact edges.
     * @return a poly describing where something can connect to
     */
    protected static Poly getConnectingSite(ElectricObject obj, Point2D clicked, EditingPreferences ep, double arcWidth) {

        assert(clicked != null);

        if (obj instanceof NodeInst) {
            PortInst pi = ((NodeInst)obj).findClosestPortInst(clicked);
            if (pi == null) return null;
            obj = pi;
        }
        if (obj instanceof PortInst) {
            PortInst pi = (PortInst)obj;
            NodeInst ni = pi.getNodeInst();
            PortProto pp = pi.getPortProto();
            if (ni.isCellInstance()) {
                return ni.getShapeOfPort(pp); // this is for multi-site ports
            } else {
                return ni.getShapeOfPortForWiringTool(pp, clicked, ep, arcWidth); // this is for multi-site ports
            }
        }
        if (obj instanceof ArcInst) {
            // make poly out of possible connecting points on arc
            ArcInst arc = (ArcInst)obj;
            Poly poly = new Poly(arc.getHeadLocation(), arc.getTailLocation());
            return poly;
        }
        return null;
    }

    protected static Rectangle2D getLayerArea(ElectricObject obj, Layer layer) {

        if (obj instanceof PortInst) {
            PortInst pi = (PortInst)obj;
            NodeInst ni = pi.getNodeInst();
            NodeProto np = ni.getProto();
            while (np instanceof Cell) {
                // TODO: add code to extract initial exported primitive object,
                // and create affine transform to translate extracted poly back up to current level
                return null;
            }

            if (np instanceof PrimitiveNode) {
                PrimitiveNode pn = (PrimitiveNode)np;

                // if pin, use area of arcs connected to it
                if (pn.getFunction().isPin()) {
                    // determine size by arcs connected to it
                    Rectangle2D horiz = null;
                    Rectangle2D vert = null;
                    for (Iterator<Connection> it = pi.getConnections(); it.hasNext(); ) {
                        Connection conn = it.next();
                        ArcInst ai = conn.getArc();
                        if (ai.getProto().getLayerIterator().next() != layer) continue;
                        int angle = ai.getDefinedAngle();
                        if (angle % 1800 == 0) {
                            if (horiz == null)
                                horiz = ai.getBounds();
                            else
                                horiz = horiz.createUnion(ai.getBounds());
                        }
                        if ((angle + 900) % 1800 == 0) {
                            if (vert == null)
                                vert = ai.getBounds();
                            else
                                vert = vert.createUnion(ai.getBounds());
                        }
                    }
                    return horiz.createIntersection(vert);
                }

                // if contact, use size of contact
                if (pn.getFunction().isContact()) {
                    Poly p = ni.getBaseShape();
                    return p.getBounds2D();
                }
            }
        }
        if (obj instanceof ArcInst) {
            ArcInst ai = (ArcInst)obj;
            return ai.getBounds();
        }
        return null;
    }

    /**
     * If drawing to/from an ArcInst, we may connect to some
     * point along the arc.  This may bisect the arc, in which case
     * we delete the current arc, add in a pin at the appropriate
     * place, and create 2 new arcs to the old arc head/tail points.
     * The bisection point depends on where the user point, but it
     * is always a point on the arc.
     * <p>
     * Note that this method adds the returned RouteElement to the
     * route, and updates the route if the arc is bisected.
     * This method should NOT add the returned RouteElement to the route.
     * @param route the route so far
     * @param arc the arc to draw from/to
     * @param connectingPoint point on or near arc
     * @param stayInside the area in which to route (null if not applicable).
     * @param alignment if non-null, alignment to align to
     * @return a RouteElement holding the new pin at the bisection
     * point, or a RouteElement holding an existingPortInst if
     * drawing from either end of the ArcInst.
     */
    protected RouteElementPort findArcConnectingPoint(Route route, ArcInst arc, Point2D connectingPoint, PolyMerge stayInside, EDimension alignment) {

        EPoint head = arc.getHeadLocation();
        EPoint tail = arc.getTailLocation();
        RouteElementPort headRE = RouteElementPort.existingPortInst(arc.getHeadPortInst(), head, ep);
        RouteElementPort tailRE = RouteElementPort.existingPortInst(arc.getTailPortInst(), tail, ep);

        if (head.equals(connectingPoint)) return headRE;
        if (tail.equals(connectingPoint)) return tailRE;

        // otherwise, we must be bisecting the arc
        return bisectArc(route, arc, connectingPoint, stayInside, alignment);
    }

    /**
     * Splits an arc at bisectPoint and updates the route to reflect the change.
     * This method should NOT add the returned RouteElement to the route.
     *
     * @param route the current route
     * @param arc the arc to split
     * @param bisectPoint point on arc from which to split it
     * @param stayInside the area in which to route (null if not applicable).
     * @param alignment alignment to align to, if non-null
     * @return the RouteElement from which to continue the route
     */
    protected RouteElementPort bisectArc(Route route, ArcInst arc, Point2D bisectPoint, PolyMerge stayInside, EDimension alignment) {

        Cell cell = arc.getParent();
        EPoint head = arc.getHeadLocation();
        EPoint tail = arc.getTailLocation();

        // determine pin type to use if bisecting arc
        PrimitiveNode pn = arc.getProto().findOverridablePinProto(ep);
        SizeOffset so = pn.getProtoSizeOffset();
        double width = pn.getDefWidth(ep)-so.getHighXOffset()-so.getLowXOffset();
        double height = pn.getDefHeight(ep)-so.getHighYOffset()-so.getLowYOffset();

        // make new pin
        RouteElementPort newPinRE = RouteElementPort.newNode(cell, pn, pn.getPort(0),
                bisectPoint, width, height, Orientation.IDENT, ep);
        newPinRE.setBisectArcPin(true);

        // make dummy end pins
        RouteElementPort headRE = RouteElementPort.existingPortInst(arc.getHeadPortInst(), head, ep);
        RouteElementPort tailRE = RouteElementPort.existingPortInst(arc.getTailPortInst(), tail, ep);
        headRE.setShowHighlight(false);
        tailRE.setShowHighlight(false);

        // put name on longer arc
        String name1 = null;
        String name2 = null;
        String nameToUse = arc.getName();
        if (arc.getNameKey().isTempname()) nameToUse = null;
        if (head.distance(bisectPoint) > tail.distance(bisectPoint))
            name1 = nameToUse; else
            	name2 = nameToUse;

        // add two arcs to rebuild old startArc
        boolean extendArcFromHeadSide = getExtendArcEnd(newPinRE, bisectPoint, arc.getLambdaBaseWidth(), arc.getProto(),
                arc.getAngle(), arc.isTailExtended(), alignment);
        boolean extendArcFromTailSide = getExtendArcEnd(newPinRE, bisectPoint, arc.getLambdaBaseWidth(), arc.getProto(),
                arc.getAngle(), arc.isHeadExtended(), alignment);
        RouteElement newHeadArcRE = RouteElementArc.newArc(cell, arc.getProto(), arc.getLambdaBaseWidth(), headRE, newPinRE,
            head, bisectPoint, name1, arc.getTextDescriptor(ArcInst.ARC_NAME), arc, arc.isHeadExtended(), extendArcFromHeadSide, stayInside);
        RouteElement newTailArcRE = RouteElementArc.newArc(cell, arc.getProto(), arc.getLambdaBaseWidth(), newPinRE, tailRE,
            bisectPoint, tail, name2, arc.getTextDescriptor(ArcInst.ARC_NAME), arc, extendArcFromTailSide, arc.isTailExtended(), stayInside);
        newHeadArcRE.setShowHighlight(false);
        newTailArcRE.setShowHighlight(false);

        // delete old arc
        RouteElement deleteArcRE = RouteElementArc.deleteArc(arc, ep);

        // add new stuff to route
        route.add(deleteArcRE);
        route.add(headRE);
        route.add(tailRE);
        route.add(newHeadArcRE);
        route.add(newTailArcRE);
        return newPinRE;
    }

    protected static Point2D getCornerLocation(Point2D startLoc, Point2D endLoc, Point2D clicked, ArcProto startArc, ArcProto endArc,
                                               boolean contactsOnEndObj, PolyMerge stayInside, Rectangle2D contactArea,
                                               Poly startPolyFull, Poly endPolyFull, EditingPreferences ep) {
        // connection point specified by contactArea
        if (contactArea != null) {
            return new Point2D.Double(contactArea.getCenterX(), contactArea.getCenterY());
        }

        // if startArc and endArc are the same, see if we can connect directly with whatever angle increment is on the arc
        boolean singleArc = false;
        if (startArc == endArc) {
            int inc = 10*startArc.getAngleIncrement(ep);
            if (inc == 0) singleArc = true; else
            {
                int ang = GenMath.figureAngle(startLoc, endLoc);
                if ((ang % inc) == 0) singleArc = true;
            }
        }
        else {
            // see if start and end line up in X or Y
            if (DBMath.areEquals(startLoc.getX(), endLoc.getX()) || DBMath.areEquals(startLoc.getY(), endLoc.getY())) singleArc = true;
        }

        // if single arc, corner location is either start or end location
        if (singleArc) {
            if (contactsOnEndObj)
                return new Point2D.Double(endLoc.getX(), endLoc.getY());
            else
                return new Point2D.Double(startLoc.getX(), startLoc.getY());
        }

        Point2D cornerLoc = null;

        Point2D pin1 = new Point2D.Double(startLoc.getX(), endLoc.getY());
        Point2D pin2 = new Point2D.Double(endLoc.getX(), startLoc.getY());

        int clickedQuad = findQuadrant(endLoc, clicked);
        int pin1Quad = findQuadrant(endLoc, pin1);
        int pin2Quad = findQuadrant(endLoc, pin2);
        int oppositeQuad = (clickedQuad + 2) % 4;
        // presume pin1 by default
        cornerLoc = pin1;
        if (pin2Quad == clickedQuad)
        {
            cornerLoc = pin2;                // same quad as pin2, use pin2
        } else if (pin1Quad == clickedQuad)
        {
            cornerLoc = pin1;                // same quad as pin1, use pin1
        } else if (pin1Quad == oppositeQuad)
        {
            cornerLoc = pin2;                // near to pin2 quad, use pin2
        }

        // special case - if poly sites overlap and corner is within that area, use that corner
        if (startPolyFull != null && endPolyFull !=  null && startPolyFull.intersects(endPolyFull)) {
            boolean usepin1 = false, usepin2 = false;
            if (startPolyFull.contains(pin1) && endPolyFull.contains(pin1)) usepin1 = true;
            if (startPolyFull.contains(pin2) && endPolyFull.contains(pin2)) usepin2 = true;
            if (usepin1 && !usepin2) cornerLoc = pin1;
            if (usepin2 && !usepin1) cornerLoc = pin2;
        }

        ArcProto useArc = startArc;
        if (!contactsOnEndObj) useArc = endArc;

        if (stayInside != null && useArc != null)
        {
            // make sure the bend stays inside of the merge area
            double pinSize = useArc.getDefaultLambdaBaseWidth(ep);
            Layer pinLayer = useArc.getLayerIterator().next();
            Rectangle2D pin1Rect = new Rectangle2D.Double(pin1.getX()-pinSize/2, pin1.getY()-pinSize/2, pinSize, pinSize);
            Rectangle2D pin2Rect = new Rectangle2D.Double(pin2.getX()-pinSize/2, pin2.getY()-pinSize/2, pinSize, pinSize);
            if (stayInside.contains(pinLayer, pin1Rect)) cornerLoc = pin1; else
                if (stayInside.contains(pinLayer, pin2Rect)) cornerLoc = pin2;
        }
        return cornerLoc;
    }

    protected static void updateContactArea(Rectangle2D contactArea, RouteElementPort re, Point2D cornerLoc, double arcWidth, int arcAngle) {
        if (arcAngle % 1800 == 0) {
            // horizontal arc
            if (contactArea.getHeight() < arcWidth) {
                contactArea.setRect(contactArea.getX(), contactArea.getCenterY()-arcWidth/2, contactArea.getWidth(), arcWidth);
            }
        }
        if ((arcAngle + 900) % 1800 == 0) {
            // vertical arc
            if (contactArea.getWidth() < arcWidth) {
                contactArea.setRect(contactArea.getCenterX()-arcWidth/2, contactArea.getY(), arcWidth, contactArea.getHeight());
            }
        }
    }

    protected static RouteElementArc addConnectingArc(Route route, Cell cell, RouteElementPort startRE, RouteElementPort endRE,
                                           Point2D startPoint, Point2D endPoint,
                                           ArcProto arc, double width, int arcAngle, boolean extendArcHead,
                                           boolean extendArcTail, PolyMerge stayInside, EDimension alignment) {

        if (extendArcHead) extendArcHead = getExtendArcEnd(startRE, startPoint, width, arc, arcAngle, extendArcHead, alignment);
        if (extendArcTail) extendArcTail = getExtendArcEnd(endRE, endPoint, width, arc, arcAngle, extendArcTail, alignment);
        RouteElementArc reArc = RouteElementArc.newArc(cell, arc, width, startRE, endRE, startPoint, endPoint,
                null, null, null, extendArcHead, extendArcTail, stayInside);
        reArc.setArcAngle(arcAngle);
        route.add(reArc);
        return reArc;
    }

    protected static boolean getExtendArcEnd(RouteElementPort re, Point2D point, double arcWidth, ArcProto arc, int arcAngle, boolean defExtends, EDimension alignment) {
        NodeProto np = re.getNodeProto();
        if (np == null) return defExtends;

        if (np instanceof PrimitiveNode) {
            PrimitiveNode pn = (PrimitiveNode)np;
            if (pn.getFunction().isContact()) {
                EDimension size = re.getNodeSize();
                if (arcAngle % 1800 == 0) {
                    if (arcWidth > size.getWidth()) return false;
                }
                if ((arcAngle+900) % 1800 == 0) {
                    if (arcWidth > size.getHeight()) return false;
                }
            }
        }
        if (alignment != null) {
        	if (arcAngle == -1) arcAngle = 0;
            if (arcAngle % 1800 == 0) {
                // horizontal
                if (isNumberAligned(point.getX(), alignment.getWidth()) &&
                    !isNumberAligned(point.getX() + arcWidth/2, alignment.getWidth()))
                    return false;
                if (!isNumberAligned(point.getX(), alignment.getWidth()) &&
                    isNumberAligned(point.getX() + arcWidth/2, alignment.getWidth()))
                    return true;
            }
            if ((arcAngle+900) % 1800 == 0) {
                // vertical
                if (isNumberAligned(point.getY(), alignment.getHeight()) &&
                    !isNumberAligned(point.getY() + arcWidth/2, alignment.getHeight()))
                    return false;
                if (!isNumberAligned(point.getY(), alignment.getHeight()) &&
                    isNumberAligned(point.getY() + arcWidth/2, alignment.getHeight()))
                    return true;
            }
        }

        return defExtends;
    }

    private static boolean isNumberAligned(double number, double alignment) {
        if (number % alignment == 0) return true;
        return false;
    }

    // ------------------------- Spatial Dimension Calculations -------------------------

    /**
     * Get closest value to clicked within a range from min to max
     */
    protected static double getClosestValue(double min, double max, double clicked, double alignment) {
        if (alignment > 0) {
            double newMax = Math.floor(max / alignment) * alignment;
            if (newMax > max) newMax = max;
            if (newMax < min) newMax = min;
            max = newMax;

            double newMin = Math.ceil(min / alignment) * alignment;
            if (newMin > max) newMin = max;
            if (newMin < min) newMin = min;
            min = newMin;
            clicked = Math.round(clicked / alignment) * alignment;
        }
        if (clicked >= max) {
            return max;
        } else if (clicked <= min) {
            return min;
        } else {
            return clicked;
        }
    }

    /**
     * Gets the closest orthogonal point from the startPoint to the clicked point.
     * This is used when the user clicks in space and the router draws only a single
     * arc towards the clicked point in one dimension.
     * @param startPoint start point of the arc
     * @param clicked where the user clicked
     * @return an end point to draw to from start point to make a single arc segment.
     */
    protected static Point2D getClosestOrthogonalPoint(Point2D startPoint, Point2D clicked) {
        Point2D newPoint;
        if (Math.abs(startPoint.getX() - clicked.getX()) < Math.abs(startPoint.getY() - clicked.getY())) {
            // draw horizontally
            newPoint = new Point2D.Double(startPoint.getX(), clicked.getY());
        } else {
            // draw vertically
            newPoint = new Point2D.Double(clicked.getX(), startPoint.getY());
        }
        return newPoint;
    }

    /**
     * Method to find the closest point to the clicked point when routed from a starting point.
     * This point will be at an angle from the starting point which is a multiple of
     * a specified angle increment.
     * @param startPoint the starting point of the route.
     * @param clicked the location of the user's click.
     * @param angleIncrement the increment of possible angles.
     * @param alignment the grid alignment to use.
     * @return the point to use for the end of the wire.
     */
    public static Point2D getClosestAngledPoint(Point2D startPoint, Point2D clicked, int angleIncrement, EDimension alignment) {
        angleIncrement = Math.abs(angleIncrement) * 10;

        // if any angle is possible, just select the clicked point
        if (angleIncrement == 0) return clicked;

        // special case for Manhattan computation
        if (angleIncrement == 900) return getClosestOrthogonalPoint(startPoint, clicked);

        // find closest two angles
        int angle = DBMath.figureAngle(startPoint, clicked);
        int nearest1 = (angle / angleIncrement) * angleIncrement;
        while (nearest1 < 0) nearest1 += 3600;
        if (nearest1 >= 3600) nearest1 %= 3600;
        int nearest2 = (angle < 0) ? nearest1 - angleIncrement : nearest1 + angleIncrement;
        while (nearest2 < 0) nearest2 += 3600;
        if (nearest2 >= 3600) nearest2 %= 3600;

        // find two possible points for the two angles
        double distance = clicked.distance(startPoint);
        Point2D p1 = new Point2D.Double(distance * DBMath.cos(nearest1), distance * DBMath.sin(nearest1));
        Point2D p2 = new Point2D.Double(distance * DBMath.cos(nearest2), distance * DBMath.sin(nearest2));

        // of the two angles, take closest to clicked point
        double xFinal, yFinal;
        double x = clicked.getX() - startPoint.getX();
        double y = clicked.getY() - startPoint.getY();
        int nearest;
        if (p2.distance(x, y) < p1.distance(x, y)) {
        	xFinal = DBMath.round(p2.getX() + startPoint.getX());
        	yFinal = DBMath.round(p2.getY() + startPoint.getY());
        	nearest = nearest2;
        } else {
        	xFinal = DBMath.round(p1.getX() + startPoint.getX());
        	yFinal = DBMath.round(p1.getY() + startPoint.getY());
        	nearest = nearest1;
        }
        Point2D result = new Point2D.Double(xFinal, yFinal);

        // grid align the result if it is on a Manhattan line
        int direction = -1;
        if (nearest == 0 || nearest == 1800) direction = 0;
        if (nearest == 900 || nearest == 2700) direction = 1;
        if (direction >= 0) DBMath.gridAlign(result, alignment, direction); else
        {
        	// special case for off-angle arcs
        	if (alignment.getWidth() > 0 && alignment.getHeight() > 0)
        	{
	            long ix = Math.round(startPoint.getX() / alignment.getWidth());
	            long iy = Math.round(startPoint.getY() / alignment.getHeight());
	            double fx = ix * alignment.getWidth(), fy = iy * alignment.getHeight();
	            if (DBMath.areEquals(fx, startPoint.getX()) && DBMath.areEquals(fy, startPoint.getY()))
	            {
	            	// starting point is on grid, see if ending point can be grid-aligned
	            	int gridAngle = DBMath.figureAngle(alignment.getWidth(), alignment.getHeight());
	            	if ((nearest / gridAngle) * gridAngle == nearest)
	            	{
	            		// angle of route can be grid-aligned
	            		DBMath.gridAlign(result, alignment);
	            	}
	            }
        	}
        }
        return result;
    }

    protected boolean withinBounds(double point, double bound1, double bound2) {
        double min, max;
        if (bound1 < bound2) {
            min = bound1; max = bound2;
        } else {
            min = bound2; max = bound1;
        }
        return ((point >= min) && (point <= max));
    }

    /**
     * Returns true if point is on the line segment, false otherwise.
     */
    protected boolean onSegment(Point2D point, Line2D line) {
        double minX, minY, maxX, maxY;
        Point2D head = line.getP1();
        Point2D tail = line.getP2();
        if (head.getX() < tail.getX()) {
            minX = head.getX(); maxX = tail.getX();
        } else {
            minX = tail.getX(); maxX = head.getX();
        }
        if (head.getY() < tail.getY()) {
            minY = head.getY(); maxY = tail.getY();
        } else {
            minY = tail.getY(); maxY = head.getY();
        }
        if ((point.getX() >= minX) && (point.getX() <= maxX) &&
            (point.getY() >= minY) && (point.getY() <= maxY))
            return true;
        return false;
    }

    /**
     * Determines what route quadrant a point is compared to a reference point.
     * A route can be drawn vertically or horizontally so this
     * method will return a number between 0 and 3, inclusive,
     * where quadrants are defined based on the angle relationship
     * of the reference point to the point.  Imagine a circle with <i>reference point</i> as
     * the center and <i>point</i> a point on the circumference of the
     * circle.  Then theta is the angle described by the arc reference point->point,
     * and quadrants are defined as:
     * <code>
     * <p>quadrant :     angle (theta)
     * <p>0 :            -45 degrees to 45 degrees
     * <p>1 :            45 degrees to 135 degrees
     * <p>2 :            135 degrees to 225 degrees
     * <p>3 :            225 degrees to 315 degrees (-45 degrees)
     *
     * @param refPoint reference point
     * @param pt variable point
     * @return which quadrant point is in.
     */
    protected static int findQuadrant(Point2D refPoint, Point2D pt) {
        // find angle
        double angle = Math.atan((pt.getY()-refPoint.getY())/(pt.getX()-refPoint.getX()));
        if (pt.getX() < refPoint.getX()) angle += Math.PI;
        if ((angle > -Math.PI/4) && (angle <= Math.PI/4))
            return 0;
        else if ((angle > Math.PI/4) && (angle <= Math.PI*3/4))
            return 1;
        else if ((angle > Math.PI*3/4) &&(angle <= Math.PI*5/4))
            return 2;
        else
            return 3;
    }

}
