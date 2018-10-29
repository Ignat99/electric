/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: VerticalRoute.java
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
import com.sun.electric.database.ImmutableArcInst;
import com.sun.electric.database.geometry.EPoint;
import com.sun.electric.database.geometry.PolyMerge;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.prototype.PortProto;
import com.sun.electric.database.topology.ArcInst;
import com.sun.electric.database.variable.ElectricObject;
import com.sun.electric.technology.ArcProto;
import com.sun.electric.technology.PrimitiveNode;
import com.sun.electric.technology.PrimitivePort;
import com.sun.electric.technology.Technology;
import com.sun.electric.technology.Xml;
import com.sun.electric.technology.Technology.TechPoint;
import com.sun.electric.technology.Xml.MenuNodeInst;
import com.sun.electric.technology.technologies.Generic;
import com.sun.electric.tool.user.User;
import com.sun.electric.tool.user.dialogs.ComponentMenu;
import com.sun.electric.util.math.EDimension;
import com.sun.electric.util.math.Orientation;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Class to route vertically (in Z direction) between two RouteElements.
 * The class is used as following:
 * <p>After creating the object, call specifyRoute() to find a way to connect
 * between startRE and endRE RouteElement objects.  At this point you may wish to
 * use the information about the specified route before actually building the route.
 * Right now the only useful information that is exported is the start and end ArcProtos
 * used, if not already specified.
 * <p>Once satisfied with the specification, call buildRoute() to create all
 * the RouteElements that determine exactly what the route will look like. Note
 * that this does not actually create any objects, it just creates a Route, which
 * can then be used to create Electric database objects.
 * <p>There are two forms of build route, the first tries to figure out everything
 * for you (contact sizes, arc angles), and connects to startRE and endRE if given.
 * The second just creates RouteElements from the specification, and you need to give
 * it the contact size and arc angle, and it does not connect to startRE or endRE.
 */
public class VerticalRoute {

    /** start of the vertical route */          private PortProto startPort;
    /** end of the vertical route */            private PortProto endPort;
    /** start of the vertical route */          private ElectricObject startObj;
    /** end of the vertical route */            private ElectricObject endObj;
    /** list of arcs and nodes to make route */ private SpecifiedRoute specifiedRoute;
    /** list of all valid specified routes */   private List<SpecifiedRoute> allSpecifiedRoutes;
    /** first arc (from startRE) */             private ArcProto startArc;
    /** last arc (to endRE) */                  private ArcProto endArc;
//  /** list of contacts to use */              private List<PrimitiveNode> contacts;

    /** the possible start arcs */              private ArcProto [] startArcs;
    /** the possible end arcs */                private ArcProto [] endArcs;
    /** if route specification succeeded */     private boolean specificationSucceeded;

    private int searchNumber;
    private static final int SEARCHLIMIT = 3000;
    private static final boolean DEBUG = false;
    private static final boolean DEBUGSEARCH = false;
    private static final boolean DEBUGTERSE = false;

    private static class SpecifiedRoute extends ArrayList<Object> {
        ArcProto startArc;
        ArcProto endArc;

        void printRoute() {
            for (int k=0; k<size(); k++) {
                System.out.println("   "+k+": "+get(k));
            }
        }
    }

    /**
     * Private constructor. Any of start/endPort, or start/endArc may be null, however
     * startArcs and endArcs must not be null.  They are the possible arcs to connect between
     * startPort/Arc and endPort/Arc.
     * @param startPort the start port of the route
     * @param endPort the end port of the route
     * @param startArc the start arc of the route
     * @param endArc the end arc of the route
     * @param startArcs the possible starting arcs
     * @param endArcs the possible ending arcs
     */
    private VerticalRoute(PortProto startPort, PortProto endPort, ArcProto startArc, ArcProto endArc,
                          ArcProto [] startArcs, ArcProto [] endArcs, ElectricObject startObj, ElectricObject endObj) {
        this.startPort = startPort;
        this.endPort = endPort;
        this.startObj = startObj;
        this.endObj = endObj;
        // special case: if port is a universal port, limit arc lists, otherwise
        // searching entire space for best connection will take forever
        if (DEBUGTERSE) {
            System.out.println("Searching for way to connect "+startPort.getBasePort().getParent()+
                    " and "+endPort.getBasePort().getParent());
        }
        if ((startPort.getBasePort().getParent() == Generic.tech().universalPinNode &&
            endPort.getBasePort().getParent() == Generic.tech().universalPinNode) ||
            (startPort.getBasePort().getParent() == Generic.tech().invisiblePinNode &&
            endPort.getBasePort().getParent() == Generic.tech().invisiblePinNode)) {
            startArc = endArc = User.getUserTool().getCurrentArcProto();
            startArcs = endArcs = new ArcProto [] { startArc };
        }
        this.startArc = startArc;
        this.endArc = endArc;
        this.startArcs = copyArcArray(startArcs);
        this.endArcs = copyArcArray(endArcs);
        specifiedRoute = null;
        allSpecifiedRoutes = null;
        specificationSucceeded = false;
    }

    /**
     * Create new VerticalRoute object to route between startRE and endRE
     * @param startPort the start port of the route
     * @param endPort the end port of the route
     */
    public static VerticalRoute newRoute(PortProto startPort, PortProto endPort, ElectricObject startObj, ElectricObject endObj) {
        ArcProto [] startArcs = startPort.getBasePort().getConnections();
        ArcProto [] endArcs = endPort.getBasePort().getConnections();
        // special case for universal pins
        if (startPort.getBasePort().getParent() == Generic.tech().universalPinNode ||
            startPort.getBasePort().getParent() == Generic.tech().invisiblePinNode)
            startArcs = endArcs;
        if (endPort.getBasePort().getParent() == Generic.tech().universalPinNode ||
            endPort.getBasePort().getParent() == Generic.tech().invisiblePinNode)
            endArcs = startArcs;
        if ((startPort.getBasePort().getParent() == Generic.tech().universalPinNode ||
             startPort.getBasePort().getParent() == Generic.tech().invisiblePinNode) &&
            (endPort.getBasePort().getParent() == Generic.tech().universalPinNode ||
             endPort.getBasePort().getParent() == Generic.tech().invisiblePinNode))
            startArcs = endArcs = new ArcProto[] {User.getUserTool().getCurrentArcProto()};
        VerticalRoute vr = new VerticalRoute(startPort, endPort, null, null, startArcs, endArcs, startObj, endObj);
        vr.specificationSucceeded = vr.specifyRoute();
        return vr;
    }

    /**
     * Create new VerticalRoute object to route between startRE and endArc
     * @param startPort the start port of the route
     * @param endArc and arc the end of the route will be able to connect to
     */
    public static VerticalRoute newRoute(PortProto startPort, ArcProto endArc) {
        ArcProto [] startArcs = startPort.getBasePort().getConnections();
        ArcProto [] endArcs = {endArc};
        // special case for universal pins
        if (startPort.getBasePort().getParent() == Generic.tech().universalPinNode)
            startArcs = endArcs;
        VerticalRoute vr = new VerticalRoute(startPort, null, null, endArc, startArcs, endArcs, null, null);
        vr.specificationSucceeded = vr.specifyRoute();
        return vr;
    }

    /**
     * Get the arc used to start the vertical route from startRE
     * @return the start arc, or null if route could not be found or not created
     */
    public ArcProto getStartArc() { return startArc; }

    /**
     * Get the arc used to end the vertical route to endRE
     * @return the end arc, or null if route could not be found or not created
     */
    public ArcProto getEndArc() { return endArc; }

    /**
     * See if specification succeeded and VerticalRoute contains a valid specification
     * @return true if succeeded, false otherwise.
     */
    public boolean isSpecificationSucceeded() { return specificationSucceeded; }

    // we need to copy the array, because we want to modify it
    private ArcProto [] copyArcArray(ArcProto [] arcs)
    {
        ArcProto [] copy = new ArcProto[arcs.length];

        // see if there are non-generic arcs
        boolean allGeneric = true;
        for(int i=0; i<arcs.length; i++)
        	if (arcs[i].getTechnology() != Generic.tech()) allGeneric = false;

        for (int i=0; i<arcs.length; i++)
        {
            ArcProto arc = arcs[i];
            // get rid of arcs we won't route with
            if (!allGeneric && arc.getTechnology() == Generic.tech() && User.getUserTool().getCurrentArcProto() != arc) arc = null;
            if (arc != null && arc.isNotUsed()) arc = null;
            copy[i] = arc;
        }
        return copy;
    }

    /**
     * Specify a Route between startRE and endRE
     * @return true if a route was found, false otherwise
     */
    private boolean specifyRoute() {

        if (endArcs == null || startArcs == null) {
            System.out.println("VerticalRoute: invalid start or end point");
            return false;
        }

        return specifyRoute(startArcs, endArcs);
    }

    /**
     * Builds a Route using the specification from specifyRoute(). It connects
     * this route up to startRE and endRE if they were specified.
     * Note that this may create non-orthogonal
     * arcs if startRE and endRE are not orthogonal to location.  Also,
     * startRE and endRE must have valid ports (i.e. are existingPortInst or newNode
     * types) if they are non-null. This method automatically determines the contact size, and the
     * angle of all the zero length arcs.
     * @param route the route to append with the new RouteElements
     * @param cell the cell in which to create the vertical route
     * @param location where to create the route (database units)
     * @param stayInside a polygonal area in which the new arc must reside (if not null).
     */
/*    public void buildRoute(Route route, Cell cell, RouteElementPort startRE, RouteElementPort endRE,
                           Point2D startLoc, Point2D endLoc, Point2D location, PolyMerge stayInside) {
        buildRoute(route, cell, startRE, endRE, startLoc, endLoc, location, stayInside, null);
    }
*/
    /**
     * Builds a Route using the specification from specifyRoute(). It connects
     * this route up to startRE and endRE if they were specified.
     * Note that this may create non-orthogonal
     * arcs if startRE and endRE are not orthogonal to location.  Also,
     * startRE and endRE must have valid ports (i.e. are existingPortInst or newNode
     * types) if they are non-null. This method automatically determines the contact size, and the
     * angle of all the zero length arcs.
     * @param route the route to append with the new RouteElements
     * @param cell the cell in which to create the vertical route
     * @param location where to create the route (database units)
     * @param stayInside a polygonal area in which the new arc must reside (if not null).
     */
/*    public void buildRoute(Route route, Cell cell, RouteElementPort startRE, RouteElementPort endRE,
                           Point2D startLoc, Point2D endLoc, Point2D location, PolyMerge stayInside, Rectangle2D contactArea) {

        if (specifiedRoute == null) {
            System.out.println("Error: Trying to build VerticalRoute without a call to specifyRoute() first");
            return;
        }
        if (specifiedRoute.size() == 0) return;

        if (startRE != null) if (!route.contains(startRE)) route.add(startRE);
        if (endRE != null) if (!route.contains(endRE)) route.add(endRE);

        // set angle by start arc if it is vertical, otherwise angle is zero
        int startArcAngle = 0;
        int endArcAngle = 0;
        if (startRE != null) {
//            if (startRE.getLocation().getX() == location.getX() &&
//                startRE.getLocation().getY() != location.getY()) arcAngle = 900;
            if (startLoc.getX() == location.getX() &&
                startLoc.getY() != location.getY()) startArcAngle = 900;
            if (startLoc.getX() == location.getX() &&
                startLoc.getY() == location.getY()) startArcAngle = startRE.getConnectingArcAngle(specifiedRoute.startArc);
        }
        if (endRE != null) {
//            if (startRE.getLocation().getX() == location.getX() &&
//                startRE.getLocation().getY() != location.getY()) arcAngle = 900;
            if (endLoc.getX() == location.getX() &&
                endLoc.getY() != location.getY()) endArcAngle = 900;
            if (endLoc.getX() == location.getX() &&
                endLoc.getY() == location.getY()) endArcAngle = endRE.getConnectingArcAngle(specifiedRoute.endArc);
        }

        // create Route, using default contact size
        Route vertRoute = buildRoute(cell, location, new Dimension2D.Double(-1,-1), startArcAngle, stayInside);

        // remove startRE and endRE if they are bisect arc pins and at same location,
        // otherwise, connect them to start and end of vertical route
        double startArcWidth = 0;
        double endArcWidth = 0;
        if (startRE != null) {
            //Router.ArcWidth aw = new Router.ArcWidth(startArcAngle);
            //aw.findArcWidthToUse(startRE, startArc);
            startArcWidth = Router.getArcWidthToUse(startRE, startArc, startArcAngle);
            //startArcWidth = aw.getWidth();

            if (route.replacePin(startRE, vertRoute.getStart(), stayInside)) {
                route.remove(startRE);
                if (route.getStart() == startRE) route.setStart(vertRoute.getStart());
            } else {
                RouteElementArc arc1 = RouteElementArc.newArc(cell, startArc, startArcWidth, startRE, vertRoute.getStart(),
                        startLoc, location, null, null, null, startArc.isExtended(), startArc.isExtended(), stayInside);
                arc1.setArcAngle(startArcAngle);
                route.add(arc1);
            }
        }
        if (endRE != null) {
            //Router.ArcWidth aw = new Router.ArcWidth(endArcAngle);
            //aw.findArcWidthToUse(endRE, endArc);
            endArcWidth = Router.getArcWidthToUse(endRE, endArc, endArcAngle);
            //endArcWidth = aw.getWidth();

            if (route.replacePin(endRE, vertRoute.getEnd(), stayInside)) {
                route.remove(endRE);
                if (route.getEnd() == endRE) route.setEnd(vertRoute.getEnd());
            } else {
                RouteElementArc arc2 = RouteElementArc.newArc(cell, endArc, endArcWidth, endRE, vertRoute.getEnd(),
                        endLoc, location, null, null, null, endArc.isExtended(), endArc.isExtended(), stayInside);
                arc2.setArcAngle(endArcAngle);
                route.add(arc2);
            }
        } else {
            if (route.getEnd() == null) {
                // both endRE and end of route are null, use end of vertical route
                route.setEnd(vertRoute.getEnd());
            }
        }

        // resize contacts to right size, and add to route
        Dimension2D size;
        if (contactArea != null) {
            size = new Dimension2D.Double(contactArea.getWidth(), contactArea.getHeight());
        } else {
            //size = Router.getContactSize(vertRoute.getStart(), vertRoute.getEnd());
            double width = 0, height = 0;
            if (startArcAngle == 900 && startArcWidth > width) width = startArcWidth;
            if (startArcAngle == 0 && startArcWidth > height) height = startArcWidth;
            if (endArcAngle == 900 && endArcWidth > width) width = endArcWidth;
            if (endArcAngle == 0 && endArcWidth > height) height = endArcWidth;
            size = new Dimension2D.Double(width, height);
        }
        for (RouteElement re : vertRoute) {
            if (re instanceof RouteElementPort)
                ((RouteElementPort)re).setNodeSize(size);
            if (!route.contains(re)) route.add(re);
        }
    }
*/
    /**
     * Builds a Route using the specification from specifyRoute(), but without
     * connecting to startRE and endRE.  The start of the returned Route can connect
     * to startRE, and the end of the returned Route can connect to endRE.
     * The caller must handle the final connections.
     * @param cell the Cell in which to create the route
     * @param location where in the database the vertical route is to be created
     * @param contactSize the size of contacts
     * @param arcAngle angle of zero length arcs created between contacts (usually zero)
     * @param stayInside a polygonal area in which the new arc must reside (if not null).
     * @param ep EditingPreferences with default sizes
     * @param evenHor if even metal is horizontal
     * @return a Route whose start can connect to startRE and whose end
     * can connect to endRE. Returns null if no specification for the route exists.
     */
    public Route buildRoute(Cell cell, Point2D location, EDimension contactSize, int arcAngle, PolyMerge stayInside, EditingPreferences ep,
    		Boolean evenHor) {
        if (specifiedRoute == null) {
            System.out.println("Error: Trying to build VerticalRoute without a call to specifyRoute() first");
            return null;
        }
        Route route = new Route();
        if (specifiedRoute.size() == 0) return route;
        if (DEBUG) {
            System.out.println("Building route: ");
            for (Object obj : specifiedRoute) {
                System.out.println("  "+obj);
            }
        }

        // determine orientation of primitive
        Orientation orient = Orientation.IDENT;
        PrimitivePort pp = (PrimitivePort)specifiedRoute.remove(0);
        PrimitiveNode pnp = pp.getParent();
        
        // If no information about even metal is provided -> use old style
        if (evenHor == null)
        {
	        Xml.MenuPalette pal = ComponentMenu.getMenuPalette(pnp.getTechnology());
			for(int index=0; index<pal.menuBoxes.size(); index++)
			{
	            List<?> menuBoxList = pal.menuBoxes.get(index);
	            if (menuBoxList == null || menuBoxList.isEmpty()) continue;
	            for(int i=0; i<menuBoxList.size(); i++)
	            {
	            	Object obj = menuBoxList.get(i);
	        		if (obj instanceof MenuNodeInst)
	        		{
	        			MenuNodeInst mni = (MenuNodeInst)obj;
	        			if (mni.protoName.equals(pnp.getName()))
	        			{
	        				orient = Orientation.fromAngle(mni.rotation);
	        				// know what to do with 900 and 1800 angle rotations
	        				if (mni.rotation == Orientation.R.getAngle() || 
	        					mni.rotation == Orientation.RR.getAngle())
	        				{
	                			double w = contactSize.getWidth();
	                			contactSize = new EDimension(contactSize.getHeight(), w);
	        				}
	        				else if (mni.rotation != 0)
	        				{
	        					System.out.println(pnp.getName() + " instances are not normally rotated " + (mni.rotation/10) +
	        						" degrees, so this one may not fit correctly");
	        				}
	        			}
	    			}
	    		}
	        }
        }
        else
        {
        	Technology.NodeLayer[] nodes = pnp.getNodeLayers();
        	boolean metalFound = false;
        	// look for the first metal layer
        	for (int i = 0; i < nodes.length && !metalFound; i++)
        	{
        		Technology.NodeLayer n = nodes[i];
        		com.sun.electric.technology.Layer.Function f = n.getLayer().getFunction();
        		if (!f.isMetal()) continue;
        		boolean evenM = f.getLevel() % 2 == 0;
        		if (!evenM) continue; // just looking for even metals
        		metalFound = true;
//        		com.sun.electric.technology.EdgeH eh = n.getLeftEdge();
//        		com.sun.electric.technology.EdgeV ev = n.getBottomEdge();
        		TechPoint[] pts = n.getPoints();
        		assert(pts.length == 2); // the assumption for now
        		long width = pts[1].getX().getGridValue(EPoint.ORIGIN) - pts[0].getX().getGridValue(EPoint.ORIGIN);
        		long height = pts[1].getY().getGridValue(EPoint.ORIGIN) - pts[0].getY().getGridValue(EPoint.ORIGIN);
        		long basedW = pnp.getBaseRectangle().getGridWidth();
        		long basedH = pnp.getBaseRectangle().getGridHeight();
        		// checking if node has any metal outside the selection area. If not, we can't determine the preferred orientation.
        		width -= basedW; // reduce the selection cut to determine if there is a reminder metal around to make the decision
        		height -= basedH;
        		assert(width >= 0 && height >= 0);
        		if (height == 0 && width == 0) continue; // nothing to check
        		boolean isHorizontal = width > height;
        		// time to rotate the contact including the cuts
        		if (!isHorizontal && evenM)
        		{
        			orient = Orientation.R;
        			double w = contactSize.getWidth();
        			contactSize = new EDimension(contactSize.getHeight(), w);
        		}
        	}
        	assert(metalFound);
        }

        // pull off the first object, which will be a port, and create contact from that
        RouteElementPort node = RouteElementPort.newNode(cell, pp.getParent(), pp,
                location, contactSize.getWidth(), contactSize.getHeight(), orient, ep);
        route.add(node);
        route.setStart(node);
        route.setEnd(node);

        // now iterate through rest of list and create arc,port route element pairs
        for (Iterator<Object> it = specifiedRoute.iterator(); it.hasNext(); ) {
            ArcProto ap = (ArcProto)it.next();
            PrimitivePort port = (PrimitivePort)it.next();

            // create node
            RouteElementPort newNode = RouteElementPort.newNode(cell, port.getParent(), port,
                    location, contactSize.getWidth(), contactSize.getHeight(), Orientation.IDENT, ep);
            route.add(newNode);
            route.setEnd(newNode);

            // create arc
            ImmutableArcInst defA = ap.getDefaultInst(ep);
            double arcWidth = ap.getDefaultLambdaBaseWidth(ep);
            RouteElementArc arc = RouteElementArc.newArc(cell, ap, arcWidth, node, newNode, location, location,
            	null, null, null, defA.isHeadExtended(), defA.isTailExtended(), stayInside);
            arc.setArcAngle(arcAngle);
            route.add(arc);

            node = newNode;
        }

        return route;
    }

    /**
     * Specify the route
     */
    private boolean specifyRoute(ArcProto [] startArcs, ArcProto [] endArcs) {

        specifiedRoute = new SpecifiedRoute();
        allSpecifiedRoutes = new ArrayList<SpecifiedRoute>();
        this.startArc = null;
        this.endArc = null;

        // try to find a way to connect, do exhaustive search
        for (int i=0; i<startArcs.length; i++) {
            for (int j=0; j<endArcs.length; j++) {
                ArcProto startArc = startArcs[i];
                ArcProto endArc = endArcs[j];
                if (startArc == null || endArc == null) continue;

                if (startObj != null && startObj instanceof ArcInst)
                {
                	ArcProto ap = ((ArcInst)startObj).getProto();
                	if (ap != startArc) continue;
                }
                if (endObj != null && endObj instanceof ArcInst)
                {
                	ArcProto ap = ((ArcInst)endObj).getProto();
                	if (ap != endArc) continue;
                }

                specifiedRoute.clear();
                specifiedRoute.startArc = startArc;
                specifiedRoute.endArc = endArc;
                searchNumber = 0;
//              Technology tech = startArc.getTechnology();
//              this.contacts = tech.getPreferredContacts();
                if (DEBUGSEARCH || DEBUGTERSE) System.out.println("** Start search startArc="+startArc+", endArc="+endArc);
                findConnectingPorts(startArc, endArc, new StringBuffer());
                if (DEBUGSEARCH || DEBUGTERSE) System.out.println("   Search reached searchNumber "+searchNumber);
            }
        }

        if (allSpecifiedRoutes.size() == 0) return false;           // nothing found

        // choose shortest route
        specifiedRoute = allSpecifiedRoutes.get(0);
        List<SpecifiedRoute> zeroLengthRoutes = new ArrayList<SpecifiedRoute>();
        for (int i=0; i<allSpecifiedRoutes.size(); i++) {
            SpecifiedRoute r = allSpecifiedRoutes.get(i);
            if (r.size() < specifiedRoute.size()) specifiedRoute = r;
            if (r.size() == 0) zeroLengthRoutes.add(r);
        }
        // if multiple ways to connect that use only one wire, choose
        // the one that uses the current wire, if any.
        if (zeroLengthRoutes.size() > 0) {
            for (SpecifiedRoute r : zeroLengthRoutes) {
                if (r.startArc == User.getUserTool().getCurrentArcProto())
                    specifiedRoute = r;
            }
        }

        allSpecifiedRoutes.clear();
        startArc = specifiedRoute.startArc;
        endArc = specifiedRoute.endArc;
        if (DEBUGSEARCH || DEBUGTERSE) {
            System.out.println("*** Using Best Route: ");
            specifiedRoute.printRoute();
        }

        return true;
    }

    /**
     * Method to check whether an equivalent PortProto has been added to the list.
     * @param pp
     * @return true if it is in the list.
     */
    private boolean isPortProtoContained(Object pp)
    {
        if (specifiedRoute.contains(pp)) return true; // exactly the same port

        if (!(pp instanceof PrimitivePort)) return false; // not a concern

        PrimitivePort ppp = (PrimitivePort)pp;
        List<PrimitivePort> equivalent = User.getUserTool().getEquivalentPorts(ppp);

        if (equivalent != null)
        {
            for (PrimitivePort p : equivalent)
            {
                if (specifiedRoute.contains(p))
                    return true;
            }
        }
        return false;
    }

    /**
     * Recursive method to create a specification list of ports and arcs
     * that connect startArc to endArc.  The list will be odd in length
     * (or zero if startArc and endArc are the same). It will consist
     * of a PortProto, and zero or more ArcProto,PortProto pairs in that order.
     * The first PortProto will be able to connect to the initial startArc,
     * and the last PortProto will be able to connect to the final endArc.
     * <p>PortProtos used are Ports from the current technology whose parents
     * (NodeProtos) have the function of CONTACT.
     * @param startArc connect from this arc
     * @param endArc connect to this arc
     * @param ds spacing for debug messages, if enabled
     */
    private void findConnectingPorts(ArcProto startArc, ArcProto endArc, StringBuffer ds) {

        // throw away route if it's longer than shortest good route
        if (specifiedRoute.size() > getShortestRouteLength())
            return;

        if (startArc == endArc) {
            saveRoute(specifiedRoute);
            if (DEBUGTERSE) System.out.println("  --Found good route of length "+specifiedRoute.size());
            return;    // don't need anything to connect between them
        }

        ds.append("  ");
        if (searchNumber > SEARCHLIMIT) { return; }
        if (searchNumber == SEARCHLIMIT) {
            System.out.println("Search limit reached in VerticalRoute");
            searchNumber++;
            return;
        }
        searchNumber++;

        PrimitivePort pp = User.getUserTool().getPreferredContactPortProto(startArc, endArc);
        if (pp != null)
        {
            if (DEBUGSEARCH) System.out.println(ds+"Checking if "+pp+" connects between "+startArc+" and "+endArc);
            specifiedRoute.add(pp);
            saveRoute(specifiedRoute);
            return;
        }

        // see if we can find a port in the current technology
        // that will connect the two arcs
//      Technology tech = startArc.getTechnology();
//		for (Iterator<PrimitiveNode> nodesIt = tech.getNodes(); nodesIt.hasNext(); ) {
//			PrimitiveNode pn = nodesIt.next();
//          for (PrimitiveNode pn : contacts) {
//            // ignore anything that is not CONTACT
//            if (!pn.getFunction().isContact()) continue;
//            if (pn.isNotUsed()) continue;
//
//            for (Iterator<PortProto> portsIt = pn.getPorts(); portsIt.hasNext(); ) {
//				PrimitivePort ppp = (PrimitivePort)portsIt.next();
//				if (DEBUGSEARCH) System.out.println(ds+"Checking if "+ppp+" connects between "+startArc+" and "+endArc);
//				if (ppp.connectsTo(startArc) && ppp.connectsTo(endArc)) {
//					specifiedRoute.add(ppp);
//                    if (ppp != pp)
//                        System.out.println("something diff");
//                    saveRoute(specifiedRoute);
//					return;                                // this connects between both arcs
//				}
//			}
//		}

        // try all contact ports as an intermediate
//		for (Iterator<PrimitiveNode> nodesIt = tech.getNodes(); nodesIt.hasNext(); )
//        {
//            PrimitiveNode pn = nodesIt.next();
//        //for (PrimitiveNode pn : contacts) {
//            // ignore anything that is not CONTACT
//            if (!pn.getFunction().isContact()) continue;
//            if (pn.isNotUsed()) continue;
//
//			for (Iterator<PortProto> portsIt = pn.getPorts(); portsIt.hasNext(); )
//            {
//				pp = (PrimitivePort)portsIt.next();
        List<PrimitivePort> portsList = User.getUserTool().getPrimitivePortConnectedToArc(startArc);
        for (PrimitivePort p : portsList)
        {
            pp = p;
                if (DEBUGSEARCH) System.out.println(ds+"Checking if "+pp+" (parent is "+pp.getParent()+") connects to "+startArc);
                assert(pp.connectsTo(startArc));
//				if (pp.connectsTo(startArc))
                {
					if (pp == startPort) continue;                       // ignore start port
					if (pp == endPort) continue;                         // ignore end port

                    if (isPortProtoContained(pp))
//                    if (specifiedRoute.contains(pp))
                        continue;          // ignore ones we've already hit

                    // add to list
					int prePortSize = specifiedRoute.size();
//                    pp = User.getUserTool().getCurrentContactPortProto(pp); // get the equivalent
                    specifiedRoute.add(pp);

					// now try to connect through all arcs that can connect to the found pp
					int preArcSize = specifiedRoute.size();
					ArcProto [] arcs = pp.getConnections();
					for (int i=0; i<arcs.length; i++) {
						ArcProto tryarc = arcs[i];
//						if (tryarc == Generic.tech().universal_arc) continue;
//						if (tryarc == Generic.tech().invisible_arc) continue;
//						if (tryarc == Generic.tech().unrouted_arc) continue;
                        if (tryarc.getTechnology() == Generic.tech()) continue;
                        if (tryarc.isNotUsed()) continue;
						if (tryarc == startArc) continue;           // already connecting through startArc
						if (tryarc == this.startArc) continue;      // original arc connecting from
//						if (specifiedRoute.contains(tryarc)) continue;       // already used this arc
						if (isPortProtoContained(tryarc)) continue;       // already used this arc
                        // if it is not the first specific route, then avoid to come back to the startPin
//                        if (specifiedRoute.size() > 0)
//                        {
//                            boolean notSameStart = false;
//                            for (Iterator<PrimitivePort> itP = tryarc.findPinProto().getPrimitivePorts(); itP.hasNext();)
//                            {
//                                PrimitivePort p = itP.next();
//                                if (p == startPort)
//                                {
//                                    notSameStart = true; // passing for the same port, avoiding loops
//                                }
//                            }
//                            if (notSameStart)
//                                continue;
//                        }
                        specifiedRoute.add(tryarc);
						if (DEBUGSEARCH) System.out.println(ds+"...found intermediate node "+pp+" through "+startArc+" to "+tryarc);
						// recurse
						findConnectingPorts(tryarc, endArc, ds);

						// remove added arcs and port and continue search
						while (specifiedRoute.size() > preArcSize) {
							specifiedRoute.remove(specifiedRoute.size()-1);
						}
					}

					// that port didn't get us anywhere, clear list back to last good point
					while (specifiedRoute.size() > prePortSize) {
						specifiedRoute.remove(specifiedRoute.size()-1);
					}
				}
//			}
		}

        if (DEBUGSEARCH) System.out.println(ds+"--- Bad path ---");
        return;               // no valid path to endpp found
    }

    /**
     * Save a successful route
     * @param route the route to save
     */
    private void saveRoute(SpecifiedRoute route) {
        // create copy and store it
        if (DEBUGSEARCH) {
            System.out.println("** Found Route for: startArc="+route.startArc+", endArc="+route.endArc);
            route.printRoute();
        }
        int shortestLength = getShortestRouteLength();
        if (route.size() > shortestLength) {
            // ignore it
            return;
        }
        SpecifiedRoute loggedRoute = new SpecifiedRoute();
        loggedRoute.startArc = route.startArc;
        loggedRoute.endArc = route.endArc;
        loggedRoute.addAll(route);
        allSpecifiedRoutes.add(loggedRoute);
        boolean trim = true;
        while (trim) {
            // remove shorter routes
            Iterator<SpecifiedRoute> it = null;
            for (it = allSpecifiedRoutes.iterator(); it.hasNext(); ) {
                SpecifiedRoute r = it.next();
                if (r.size() > shortestLength) {
                    allSpecifiedRoutes.remove(r);
                    break;
                }
            }
            if (!it.hasNext()) {
                trim = false;           // done trimming
            }
        }
    }

    /**
     * Get the length of the shortest route.
     */
    private int getShortestRouteLength() {
        // Because all routes should be of the
        // shortest length, just return the length of the first route
        if (allSpecifiedRoutes.size() == 0) return Integer.MAX_VALUE;
        SpecifiedRoute r = allSpecifiedRoutes.get(0);
        return r.size();
    }
}
