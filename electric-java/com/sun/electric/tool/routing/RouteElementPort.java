/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: RouteElementPort.java
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
import com.sun.electric.database.geometry.Poly;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.EDatabase;
import com.sun.electric.database.prototype.NodeProto;
import com.sun.electric.database.prototype.PortProto;
import com.sun.electric.database.topology.ArcInst;
import com.sun.electric.database.topology.Connection;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.topology.PortInst;
import com.sun.electric.database.variable.ElectricObject;
import com.sun.electric.technology.ArcProto;
import com.sun.electric.technology.PrimitiveNode;
import com.sun.electric.technology.SizeOffset;
import com.sun.electric.tool.user.Highlighter;
import com.sun.electric.util.math.EDimension;
import com.sun.electric.util.math.Orientation;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Class for defining RouteElements that are ports.
 */
public class RouteElementPort extends RouteElement {

    // ---- New Port info ----
    /** Node type to create */                      private NodeProto np;
    /** Port on node to use */                      private PortProto portProto;
    /** location to create Node */                  private EPoint location;
    /** angle of the Node */						private Orientation orient;
    /** size aspect that is seen on screen */       private double width, height;
    /** if this bisects an arc */                   private boolean isBisectArcPin;
    /** RouteElementArcs connecting to this */      private List<RouteElementArc> newArcs;
    /** port site spatial extents (db units) */     private transient Poly portInstSite;
    /** the other RouteElementPort (when paired) */	private RouteElementPort otherREP;

    /** The newly created instance, or the instance to delete */     private NodeInst nodeInst;
    /** The newly created portinst, or the existing port instance */ private PortInst portInst;

    /**
     * Private Constructor
     * @param action the action this RouteElementAction will do.
     */
    private RouteElementPort(RouteElementAction action, Cell cell) { super(action, cell); }

    /**
     * Factory method for making a newNode RouteElement.
     * @param cell the Cell to put the new RouteElement in
     * @param np Type of NodeInst to make
     * @param location the location of the new NodeInst
     * @param width the width of the new NodeInst
     * @param height the height of the new NodeInst
     * @param orient the Orientation of the new NodeInst
     * @param ep EditingPreferences with default sizes
     */
    public static RouteElementPort newNode(Cell cell, NodeProto np, PortProto newNodePort, Point2D location,
                                       double width, double height, Orientation orient, EditingPreferences ep) {
        RouteElementPort e = new RouteElementPort(RouteElement.RouteElementAction.newNode, cell);
        e.np = np;
        e.portProto = newNodePort;
        e.location = EPoint.snap(location);
        e.orient = orient;
        e.isBisectArcPin = false;
        e.newArcs = new ArrayList<RouteElementArc>();
        e.setNodeSize(new EDimension(width, height), ep);
        e.nodeInst = null;
        e.portInst = null;
        e.portInstSite = new Poly(Poly.from(location));
        return e;
    }

    public static RouteElementPort newNodeOtherPort(Cell cell, RouteElementPort rep, PortProto newNodePort, EditingPreferences ep) {
        RouteElementPort e = new RouteElementPort(RouteElement.RouteElementAction.newNode, cell);
        e.np = rep.np;
        e.portProto = newNodePort;
        e.location = EPoint.snap(rep.location);
        e.orient = rep.orient;
        e.isBisectArcPin = false;
        e.newArcs = new ArrayList<RouteElementArc>();
        e.setNodeSize(rep.getNodeSize(), ep);
        e.nodeInst = null;
        e.portInst = null;
        e.portInstSite = new Poly(Poly.from(rep.location));
        e.otherREP = rep;
        rep.otherREP = e;
        return e;
    }

    /**
     * Factory method for making a deleteNode RouteElement
     * @param nodeInstToDelete the nodeInst to delete
     * @param ep EditingPreferences with default sizes
     */
    public static RouteElementPort deleteNode(NodeInst nodeInstToDelete, EditingPreferences ep) {
        RouteElementPort e = new RouteElementPort(RouteElement.RouteElementAction.deleteNode, nodeInstToDelete.getParent());
        e.np = nodeInstToDelete.getProto();
        e.portProto = null;
        e.location = EPoint.snap(nodeInstToDelete.getTrueCenter());
        e.orient = Orientation.IDENT;
        e.isBisectArcPin = false;
        e.newArcs = new ArrayList<RouteElementArc>();
        e.setNodeSize(new EDimension(nodeInstToDelete.getXSizeWithoutOffset(), nodeInstToDelete.getYSizeWithoutOffset()), ep);
        e.nodeInst = nodeInstToDelete;
        e.portInst = null;
        e.portInstSite = null;
        return e;
    }

    /**
     * Factory method for making a dummy RouteElement for an
     * existing PortInst. This is usually use to mark the
     * start and/or ends of the route, which exist before
     * we start building the route.
     * @param existingPortInst the already existing portInst to connect to
     * @param portInstSite
     * @param ep EditingPreferences with default sizes
     */
    public static RouteElementPort existingPortInst(PortInst existingPortInst, EPoint portInstSite, EditingPreferences ep) {
        Poly poly = new Poly(portInstSite);
        return existingPortInst(existingPortInst, poly, ep);
    }

    /**
     * Factory method for making a dummy RouteElement for an
     * existing PortInst. This is usually use to mark the
     * start and/or ends of the route, which exist before
     * we start building the route.
     * @param existingPortInst the already existing portInst to connect to
     * @param portInstSite
     * @param ep EditingPreferences with default sizes
     */
    public static RouteElementPort existingPortInst(PortInst existingPortInst, Poly portInstSite, EditingPreferences ep) {
        RouteElementPort e = new RouteElementPort(RouteElement.RouteElementAction.existingPortInst, existingPortInst.getNodeInst().getParent());
        NodeInst nodeInst = existingPortInst.getNodeInst();
        e.np = nodeInst.getProto();
        e.portProto = existingPortInst.getPortProto();
        e.location = EPoint.snap(nodeInst.getTrueCenter());
        e.orient = nodeInst.getOrient();
        e.isBisectArcPin = false;
        e.newArcs = new ArrayList<RouteElementArc>();
        e.setNodeSize(new EDimension(nodeInst.getXSizeWithoutOffset(), nodeInst.getYSizeWithoutOffset()), ep);
        e.nodeInst = nodeInst;
        e.portInst = existingPortInst;
        e.portInstSite = portInstSite;
        return e;
    }

    /**
     * Get the NodeProto for connecting to this RouteElementPort.
     * This is not the same as getNodeInst.getProto(),
     * because if the action has not yet been done the NodeInst
     * will not have been created and will be null.
     * @return the NodeProto
     */
    public NodeProto getNodeProto() { return np; }

    /**
     * Get the PortProto for connecting to this RouteElementPort.
     * This is not the same as getPortInst().getPortProto(),
     * because if the action has not yet been done the PortInst
     * returned by getPortInst() may have not yet been created.
     * For a deleteNode, this will return null.
     * @return a PortProto of port to connect to this RouteElement.
     */
    public PortProto getPortProto() { return portProto; }

    /**
     * Get Connecting Port on RouteElement.
     * @return the PortInst, or null on error
     */
    public PortInst getPortInst() { return portInst; }

    /**
     * Get Connecting Node on RouteElement.
     * @return the NodeInst, or null on error
     */
    public NodeInst getNodeInst() { return nodeInst; }

    /** Returns location of newNode, existingPortInst, or deleteNode,
     * or null otherwise */
    public Point2D getLocation() { return location; }

    /** Set true by Interactive router if pin used to bisect arc
     * Router may want to remove this pin later if it places a
     * connecting contact cut in the same position.
     */
    public void setBisectArcPin(boolean state) { isBisectArcPin = state; }

    /** see setBisectArcPin */
    public boolean isBisectArcPin() { return isBisectArcPin; }

    /**
     * Book-keeping: Adds a newArc RouteElement to a list to keep
     * track of what newArc elements use this object as an end point.
     * This must be a RouteElement of type newNode or existingPortInst.
     * @param re the RouteElement to add.
     */
    public void addConnectingNewArc(RouteElementArc re) {
        if (re.getAction() != RouteElementAction.newArc) return;
        newArcs.add(re);
    }

    /**
     * Remove a newArc that connects to this newNode or existingPortInst.
     * @param re the RouteElement to remove
     */
    public void removeConnectingNewArc(RouteElementArc re) {
        if (re.getAction() != RouteElementAction.newArc) return;
        newArcs.remove(re);
    }

    /**
     * Get largest arc width of newArc RouteElements attached to this
     * RouteElement.  If none present returns -1.
     * <p>Note that these width values should have been pre-adjusted for
     * the arc width offset, so these values have had the offset subtracted away.
     */
    public double getWidestConnectingArc(ArcProto ap) {
        double width = -1;

        if (getAction() == RouteElementAction.existingPortInst) {
            // find all arcs of arc prototype connected to this
            for (Iterator<Connection> it = portInst.getConnections(); it.hasNext(); ) {
                Connection conn = it.next();
                ArcInst arc = conn.getArc();
                if (arc.getProto() == ap) {
                    double newWidth = arc.getLambdaBaseWidth();
                    if (newWidth > width) width = newWidth;
                }
            }
        }

        if (getAction() == RouteElementAction.newNode) {
            if (newArcs == null) return -1;
            for (RouteElementArc re : newArcs) {
                if (re.getArcProto() == ap) {
                    if (re.getArcBaseWidth() > width) width = re.getArcBaseWidth();
                }
            }
        }

        return width;
    }

    /**
     * Get largest arc width of newArc RouteElements attached to this
     * RouteElement.  If none present returns -1.
     * <p>Note that these width values should have been pre-adjusted for
     * the arc width offset, so these values have had the offset subtracted away.
     */
    public double getWidestConnectingArc(ArcProto ap, int arcAngle) {
        double width = -1;

        if (getAction() == RouteElementAction.existingPortInst) {
            // find all arcs of arc prototype connected to this
            for (Iterator<Connection> it = portInst.getConnections(); it.hasNext(); ) {
                Connection conn = it.next();
                ArcInst arc = conn.getArc();
                if (arc.getAngle() != arcAngle) continue;
                if (arc.getProto() == ap) {
                    double newWidth = arc.getLambdaBaseWidth();
                    if (newWidth > width) width = newWidth;
                }
            }
        }

        if (getAction() == RouteElementAction.newNode) {
            if (newArcs == null) return -1;
            for (RouteElementArc re : newArcs) {
                if (re.getArcProto() == ap) {
                    if (re.getArcBaseWidth() > width) width = re.getArcBaseWidth();
                }
            }
        }

        return width;
    }

    /**
     * Get the angle of any arcs connected to this RouteElement.
     * If there are multiple arcs, it returns the angle of the widest
     * connecting arc.
     * @param ap the arc prototype
     * @return the angle in tenths of a degree, 0 if no arcs of the specified type connected.
     */
    public int getConnectingArcAngle(ArcProto ap) {
        int angle = 0;
        double width = -1;

        if (getAction() == RouteElementAction.existingPortInst) {
            // find all arcs of arc prototype connected to this
            for (Iterator<Connection> it = portInst.getConnections(); it.hasNext(); ) {
                Connection conn = it.next();
                ArcInst arc = conn.getArc();
                if (arc.getProto() == ap) {
                    double newWidth = arc.getLambdaBaseWidth();
                    if (newWidth > width) {
                        width = newWidth;
                        angle = arc.getDefinedAngle() % 1800;
                    }
                }
            }
        }

        if (getAction() == RouteElementAction.newNode) {
            if (newArcs == null) return -1;
            for (RouteElementArc re : newArcs) {
                if (re.getArcProto() == ap) {
                    if (re.getArcBaseWidth() > width) {
                        width = re.getArcBaseWidth();
                        if (re.isArcVertical()) angle = 900;
                        if (re.isArcHorizontal()) angle = 0;
                    }
                }
            }
        }

        return angle;
    }

    /**
     * Get an iterator over any newArc RouteElements connected to this
     * newNode RouteElement.  Returns an iterator over an empty list
     * if no new arcs.
     */
    public Iterator<RouteElement> getNewArcs() {
        ArrayList<RouteElement> list = new ArrayList<RouteElement>();
        list.addAll(newArcs);
        return list.iterator();
    }

    /**
     * Get the size of a newNode, or the NodeInst an existingPortInst
     * is attached to.
     * @return the width,height of the node, or (-1, -1) if not a node
     */
    public EDimension getNodeSize() {
        return new EDimension(width, height);
    }

    /**
     * Set the size of a newNode.  Does not make it smaller
     * than the default size if this is a PrimitiveNode.
     * Does nothing for other RouteElements.
     * @param size the new size
     * @param ep EditingPreferences with default sizes
     */
    public void setNodeSize(EDimension size, EditingPreferences ep) {
        SizeOffset so = np.getProtoSizeOffset();
        double widthoffset = so.getLowXOffset() + so.getHighXOffset();
        double heightoffset = so.getLowYOffset() + so.getHighYOffset();

        double defWidth = np.getDefWidth(ep) - widthoffset;       // this is width we see on the screen
        double defHeight = np.getDefHeight(ep) - heightoffset;    // this is height we see on the screen
        if (size.getWidth() > defWidth) width = size.getWidth(); else width = defWidth;
        if (size.getHeight() > defHeight) height = size.getHeight(); else height = defHeight;
    }

    /**
     * Get a polygon that defines the port dimensions.
     * May return null.
     */
    public Poly getConnectingSite() { return portInstSite; }

    /**
     * Set a polygon that defines the port dimensions.
     * @param p the polygon that defines the port location.
     */
    public void setConnectingSite(Poly p) { portInstSite = p; }

    /**
     * Perform the action specified by RouteElementAction <i>action</i>.
     * Note that this method performs database editing, and should only
     * be called from within a Job.
     * @param ep EditingPreferences
     * @return the object created, or null if deleted or nothing done.
     */
    public ElectricObject doAction(EditingPreferences ep) {

        EDatabase.serverDatabase().checkChanging();

        if (isDone()) return null;
        ElectricObject returnObj = null;

        if (getAction() == RouteElementAction.newNode) {
            // create new Node
            if (nodeInst == null)
            {
                SizeOffset so = np.getProtoSizeOffset();
                double widthso = width +  so.getLowXOffset() + so.getHighXOffset();
                double heightso = height + so.getLowYOffset() + so.getHighYOffset();
                Point2D loc = new Point2D.Double(location.getX(), location.getY());
	            nodeInst = NodeInst.makeInstance(np, ep, loc, widthso, heightso, getCell(), orient, null);
	            if (nodeInst == null) return null;
	            if (otherREP != null)
	            {
	            	otherREP.nodeInst = nodeInst;
	            	otherREP.portInst = nodeInst.findPortInstFromEquivalentProto(otherREP.getPortProto());
	            }
	            portInst = nodeInst.findPortInstFromEquivalentProto(portProto);
            }
            returnObj = nodeInst;
        }
        if (getAction() == RouteElementAction.deleteNode) {
            // delete existing arc
            nodeInst.kill();
        }
        setDone();
        return returnObj;
    }

    /**
     * Adds RouteElement to highlights
     */
    public void addHighlightArea(Highlighter highlighter) {

        if (!isShowHighlight()) return;

        if (getAction() == RouteElementAction.newNode) {
            // draw curved outline for curved pins
        	if (np instanceof PrimitiveNode)
        	{
        		PrimitiveNode pnp = (PrimitiveNode)np;
        		if (pnp.isCurvedPin())
        		{
        			EditingPreferences ep = EditingPreferences.getInstance();
        			NodeInst ni = NodeInst.makeDummyInstance(pnp, ep, location, width, height, orient);
					Poly[] polys = pnp.getTechnology().getShapeOfNode(ni);
					Poly poly = polys[0];
					poly.setStyle(Poly.Type.CLOSED);
					poly.transform(ni.rotateOut());
					highlighter.addPoly(poly, getCell(), null);
					return;
        		}
        	}

        	// create box around new Node
            Rectangle2D bounds = new Rectangle2D.Double(location.getX()-0.5*width,
                    location.getY()-0.5*height, width, height);
            highlighter.addArea(bounds, getCell());
        }
        if (getAction() == RouteElementAction.existingPortInst) {
            highlighter.addElectricObject(portInst, getCell());
        }
    }

    /** Return string describing the RouteElement */
    public String toString() {
        if (getAction() == RouteElementAction.newNode) {
            return "RouteElementPort newNode "+(otherREP != null ? "(multiple ports) " : "")+np+" size "+width+","+height+" at "+location;
        }
        else if (getAction() == RouteElementAction.deleteNode) {
            return "RouteElementPort deleteNode "+nodeInst+" at ("+nodeInst.getAnchorCenterX()+","+nodeInst.getAnchorCenterY()+")";
        }
        else if (getAction() == RouteElementAction.existingPortInst) {
            return "RouteElementPort existingPortInst "+portInst;
        }
        return "RouteElement bad action";
    }

    /**
     * Save the state of the <tt>RouteElementPort</tt> instance to a stream (that
     * is, serialize it).
     *
     * @serialData The number of points in portInstSite polygon is emitted (integer),
     * followed by all of its coordinates (as pair of doubles) in the proper order.
     */
    private void writeObject(java.io.ObjectOutputStream s) throws java.io.IOException {
        s.defaultWriteObject();
        if (portInstSite != null) {
            Point2D[] points = portInstSite.getPoints();
            s.writeInt(points.length);
            for (int i = 0; i < points.length; i++) {
                s.writeDouble(points[i].getX());
                s.writeDouble(points[i].getY());
            }
        } else {
            s.writeInt(-1);
        }
    }

    /**
     * Reconstruct the <tt>RouteElementPort</tt> instance from a stream (that is,
     * deserialize it).
     */
    private void readObject(java.io.ObjectInputStream s) throws java.io.IOException, ClassNotFoundException {
        s.defaultReadObject();
        int len = s.readInt();
        if (len >= 0) {
            Point2D[] points = new Point2D[len];
            for (int i = 0; i < len; i++) {
                double x = s.readDouble();
                double y = s.readDouble();
                points[i] = new Point2D.Double(x, y);
            }
        }
    }
}
