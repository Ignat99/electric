/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: RouteElementArc.java
 * Written by: Jonathan Gainsley.
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
import com.sun.electric.database.geometry.PolyMerge;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.EDatabase;
import com.sun.electric.database.topology.ArcInst;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.topology.PortInst;
import com.sun.electric.database.variable.ElectricObject;
import com.sun.electric.database.variable.TextDescriptor;
import com.sun.electric.technology.ArcProto;
import com.sun.electric.technology.Layer;
import com.sun.electric.technology.PrimitiveNode;
import com.sun.electric.technology.technologies.Schematics;
import com.sun.electric.tool.user.Highlighter;
import com.sun.electric.util.math.DBMath;
import com.sun.electric.util.math.EDimension;
import com.sun.electric.util.math.GenMath;
import com.sun.electric.util.math.MutableBoolean;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.Set;

/**
 * Class for defining RouteElements that are arcs.
 */
public class RouteElementArc extends RouteElement {

    // ---- New Arc info ----
    /** Arc type to create */                       private ArcProto arcProto;
    /** width of arc */                             private double arcBaseWidth;
    /** Head of arc */                              private RouteElementPort headRE;
    /** Tail of arc */                              private RouteElementPort tailRE;
    /** Head connecting point */                    private EPoint headConnPoint;
    /** Tail connecting point */                    private EPoint tailConnPoint;
    /** Name of arc */                              private String arcName;
    /** Text descriptor of name */                  private TextDescriptor arcNameDescriptor;
    /** Angle of arc */                             private int arcAngle;
    /** true if angle was set */                    private boolean arcAngleSet;
    /** inherit properties from this arc */         private ArcInst inheritFrom;
    /** set the arc extension if inheritFrom is null */ private boolean extendArcHead;
    /** set the arc extension if inheritFrom is null */ private boolean extendArcTail;

    /** This contains the newly create instance, or the instance to delete */ private ArcInst arcInst;

    /**
     * Private Constructor
     * @param action the action this RouteElementAction will do.
     */
    private RouteElementArc(RouteElementAction action, Cell cell) { super(action, cell); }

    /**
     * Factory method for making a newArc RouteElement
     * @param ap Type of ArcInst to make
     * @param headRE RouteElement (must be newNode or existingPortInst) at head of arc
     * @param tailRE RouteElement (must be newNode or existingPortInst) at tail or arc
     * @param nameTextDescriptor
     * @param inheritFrom
     * @param extendArcHead only applied if inheritFrom is null
     * @param extendArcTail only applied if inheritFrom is null
     * @param stayInside a polygonal area in which the new arc must reside (if not null).
     * The arc is narrowed and has its ends extended in an attempt to stay inside this area.
     */
    public static RouteElementArc newArc(Cell cell, ArcProto ap, double arcBaseWidth, RouteElementPort headRE, RouteElementPort tailRE,
                                         Point2D headConnPoint, Point2D tailConnPoint, String name, TextDescriptor nameTextDescriptor,
                                         ArcInst inheritFrom, boolean extendArcHead, boolean extendArcTail, PolyMerge stayInside) {
        EPoint headEP = EPoint.snap(headConnPoint);
    	EPoint tailEP = EPoint.snap(tailConnPoint);
    	MutableBoolean headExtend = new MutableBoolean(extendArcHead);
    	MutableBoolean tailExtend = new MutableBoolean(extendArcTail);

        if (stayInside != null)
    	{
        	Set<Layer> allLayers = stayInside.getKeySet();
        	for(int i=0; i<ap.getNumArcLayers(); i++)
        	{
        		Layer layer = ap.getLayer(i);

	        	// if Active layer is not present, try any active layer
	        	if (layer.getFunction().isDiff() && !allLayers.contains(layer))
	        	{
	        		for(Layer other : allLayers)
	        		{
	        			if (other.getFunction().isDiff()) { layer = other;   break; }
	        		}
	        	}
	            double layerExtend = ap.getLayerExtend(i).getLambda();
	            double arcExtendOverMin = arcBaseWidth*0.5 - ap.getBaseExtend().getLambda();
	        	boolean good = stayInside.arcPolyFits(layer, headEP, tailEP, 2*(arcExtendOverMin+layerExtend), headExtend, tailExtend);

	        	// try reducing to default width if it doesn't fit
	        	while (!good && arcBaseWidth > 0)
	        	{
	        		arcBaseWidth = Math.max(arcBaseWidth-1, 0);
	                arcExtendOverMin = arcBaseWidth*0.5 - ap.getBaseExtend().getLambda();
	                if (arcExtendOverMin < 0) break;
	            	good = stayInside.arcPolyFits(layer, headEP, tailEP, 2*(arcExtendOverMin+layerExtend), headExtend, tailExtend);
	        	}

	        	// make it zero-size if it still doesn't fit
	        	if (!good)
	        	{
//					ap = Generic.tech().universal_arc;
	        		arcBaseWidth = 0;
	        		break;
	        	}
        	}
    	}

        RouteElementArc e = new RouteElementArc(RouteElementAction.newArc, cell);
        e.arcProto = ap;
        e.arcBaseWidth = arcBaseWidth;
        e.headRE = headRE;
        e.tailRE = tailRE;
        e.arcName = name;
        e.arcNameDescriptor = nameTextDescriptor;
        if (headRE.getAction() != RouteElement.RouteElementAction.newNode &&
            headRE.getAction() != RouteElement.RouteElementAction.existingPortInst)
            System.out.println("  ERROR: headRE of newArc RouteElementArc must be newNode or existingPortInst");
        if (tailRE.getAction() != RouteElement.RouteElementAction.newNode &&
            tailRE.getAction() != RouteElement.RouteElementAction.existingPortInst)
            System.out.println("  ERROR: tailRE of newArc RouteElementArc must be newNode or existingPortInst");
        headRE.addConnectingNewArc(e);
        tailRE.addConnectingNewArc(e);
        e.headConnPoint = headEP;
        e.tailConnPoint = tailEP;
        assert(e.headConnPoint != null);
        assert(e.tailConnPoint != null);
        e.arcAngle = 0;
        e.arcAngleSet = false;
        e.arcInst = null;
        e.inheritFrom = inheritFrom;
        e.extendArcHead = headExtend.booleanValue();
        e.extendArcTail = tailExtend.booleanValue();
        return e;
    }

    /**
     * Factory method for making a deleteArc RouteElement
     * @param arcInstToDelete the arcInst to delete
     * @param ep EditingPreferences with default sizes
     */
    public static RouteElementArc deleteArc(ArcInst arcInstToDelete, EditingPreferences ep) {
        RouteElementArc e = new RouteElementArc(RouteElementAction.deleteArc, arcInstToDelete.getParent());
        e.arcProto = arcInstToDelete.getProto();
        e.arcBaseWidth = arcInstToDelete.getLambdaBaseWidth();
//        e.arcBaseWidth = arcInstToDelete.getLambdaFullWidth();
        e.headRE = RouteElementPort.existingPortInst(arcInstToDelete.getHeadPortInst(), arcInstToDelete.getHeadLocation(), ep);
        e.tailRE = RouteElementPort.existingPortInst(arcInstToDelete.getTailPortInst(), arcInstToDelete.getTailLocation(), ep);
        e.arcName = arcInstToDelete.getName();
        e.arcNameDescriptor = arcInstToDelete.getTextDescriptor(ArcInst.ARC_NAME);
        e.headConnPoint = arcInstToDelete.getHeadLocation();
        e.tailConnPoint = arcInstToDelete.getTailLocation();
        e.arcAngle = 0;
        e.arcAngleSet = false;
        e.arcInst = arcInstToDelete;
        e.inheritFrom = null;
        e.extendArcHead = e.extendArcTail = true;
        return e;
    }

    /**
     * Get the arc prototype to be created/deleted.
     * @return the arc prototype.
     */
    public ArcProto getArcProto() { return arcProto; }

    public RouteElementPort getHead() { return headRE; }
    public RouteElementPort getTail() { return tailRE; }
    public Point2D getHeadConnPoint() { return headConnPoint; }
    public Point2D getTailConnPoint() { return tailConnPoint; }
    public boolean getHeadExtension() { return extendArcHead; }
    public boolean getTailExtension() { return extendArcTail; }

//    /**
//     * Return arc width
//     */
//    public double getArcFullWidth() { return arcBaseWidth; }

    /**
     * Return arc width.
     * This returns the arc width taking into account any offset
     */
    public double getArcBaseWidth() {
        return arcBaseWidth;
//        return arcBaseWidth - arcProto.getLambdaWidthOffset();
    }

//    /**
//     * Set the arc width if this is a newArc RouteElement, otherwise does nothing.
//     * This is the non-offset width (i.e. the bloated width).
//     */
//    public void setArcFullWidth(double width) {
//        if (getAction() == RouteElementAction.newArc)
//            arcBaseWidth = width;
//    }

    /**
     * Set the arc width if this is a newArc RouteElement, otherwise does nothing.
     * This is offset arc width (i.e. what the user sees).
     */
    public void setArcBaseWidth(double width) {
        if (getAction() == RouteElementAction.newArc)
            arcBaseWidth = width;
//            arcBaseWidth = width + arcProto.getLambdaWidthOffset();
    }

    /**
     * Set a newArc's angle. This only does something if both the
     * head and tail of the arc are coincident points. This does
     * nothing if the RouteElement is not a newArc
     * @param angle the angle, in tenth degrees
     */
    public void setArcAngle(int angle) {
        if (getAction() == RouteElementAction.newArc)
        {
            arcAngle = angle;
            arcAngleSet = true;
        }
    }

    public int getArcAngle() {
        if (isArcHorizontal() && isArcVertical()) return arcAngle;
        if (isArcHorizontal()) return 0;
        return 900;
    }

    public void setHeadExtension(boolean e) { extendArcHead = e; }

    public void setTailExtension(boolean e) { extendArcTail = e; }

    /**
     * Return true if the new arc is a vertical arc, false otherwise
     */
    public boolean isArcVertical() {
        Point2D head = headConnPoint;
        Point2D tail = tailConnPoint;
        if (head == null) head = headRE.getLocation();
        if (tail == null) tail = tailRE.getLocation();
        if ((head == null) || (tail == null)) return false;
        if (head.getX() == tail.getX()) return true;
        return false;
    }

    /**
     * Return true if the new arc is a horizontal arc, false otherwise
     */
    public boolean isArcHorizontal() {
        Point2D head = headConnPoint;
        Point2D tail = tailConnPoint;
        if (head == null) head = headRE.getLocation();
        if (tail == null) tail = tailRE.getLocation();
        if ((head == null) || (tail == null)) return false;
        if (head.getY() == tail.getY()) return true;
        return false;
    }

    /**
     * Used to update end points of new arc if they change
     * Only valid if called on newArcs, does nothing otherwise.
     * @return true if either (a) this arc does not use oldEnd, or
     * (b) this arc replaced oldEnd with newEnd, and no longer uses oldEnd at all.
     */
    public boolean replaceArcEnd(RouteElementPort oldEnd, RouteElementPort newEnd) {
        if (getAction() == RouteElementAction.newArc) {
            Poly poly = newEnd.getConnectingSite();
            if (headRE == oldEnd) {
                if (poly != null && poly.contains(headConnPoint)) {
                    headRE = newEnd;
                    // update book-keeping
                    oldEnd.removeConnectingNewArc(this);
                    newEnd.addConnectingNewArc(this);
                }
            }
            if (tailRE == oldEnd) {
                if (poly != null && poly.contains(tailConnPoint)) {
                    tailRE = newEnd;
                    // update book-keeping
                    oldEnd.removeConnectingNewArc(this);
                    newEnd.addConnectingNewArc(this);
                }
            }
        }
        if (headRE == oldEnd || tailRE == oldEnd) return false;
        return true;
    }

    /**
     * Perform the action specified by RouteElementAction <i>action</i>.
     * Note that this method performs database editing, and should only
     * be called from within a Job.
     * @param ep EditingPreferences with default sizes
     * @return the object created, or null if deleted or nothing done.
     */
    @Override
    public ElectricObject doAction(EditingPreferences ep) {

        EDatabase.serverDatabase().checkChanging();

        if (isDone()) return null;

        if (getAction() == RouteElementAction.newArc) {
            PortInst headPi = headRE.getPortInst();
            PortInst tailPi = tailRE.getPortInst();
            EPoint headPoint = headConnPoint;
            EPoint tailPoint = tailConnPoint;

			// special case when routing to expandable gate (and, or, mux, etc.)
			Poly headPoly = headPi.getPoly();
			boolean inHead = headPoly.isInside(headPoint);
			if (!inHead)
			{
				NodeInst headNi = headPi.getNodeInst();
				if (!headNi.isCellInstance())
				{
					PrimitiveNode pNp = (PrimitiveNode)headNi.getProto();
					EDimension autoGrowth = pNp.getAutoGrowth();
					if (autoGrowth != null)
					{
						// grow the node to allow expandable port to fit
						headNi.resize(autoGrowth.getWidth(), autoGrowth.getHeight());
			            headPoly = headPi.getPoly();
			            inHead = headPoly.isInside(headPoint);
					}
				}
	            if (!inHead) {
					// see if zero-size port is off-grid
	            	Rectangle2D headBounds = headPi.getBounds();
	            	double trueX = headBounds.getCenterX();
	            	if (headBounds.getWidth() == 0 && trueX != headPoint.getX())
	            		trueX = DBMath.round(headBounds.getCenterX());
	            	double trueY = headBounds.getCenterX();
	            	if (headBounds.getHeight() == 0 && trueY != headPoint.getY())
	            		trueY = DBMath.round(headBounds.getCenterY());
            		if (trueX == headPoint.getX() && trueY == headPoint.getY()) inHead = true;
	            }
			}
            if (!inHead) {
                // can't connect
            	Rectangle2D headBounds = headPi.getBounds();
                System.out.println("Arc head (" + headPoint.getX() + "," + headPoint.getY() + ") not inside " + headPi + " which is "+
               		headBounds.getMinX() + "<=X<=" + headBounds.getMaxX() + " and " + headBounds.getMinY() + "<=Y<=" + headBounds.getMaxY());
                System.out.println("  Arc ran from " + headPi.getNodeInst() + ", port " + headPi.getPortProto().getName() +
                	" to " + tailPi.getNodeInst() + ", port " + tailPi.getPortProto().getName());
                headPoly = headPi.getPoly();
                return null;
            }
			Poly tailPoly = tailPi.getPoly();
			boolean inTail = tailPoly.isInside(tailPoint);
			if (!inTail)
			{
				NodeInst tailNi = tailPi.getNodeInst();
				if (!tailNi.isCellInstance())
				{
					PrimitiveNode pNp = (PrimitiveNode)tailNi.getProto();
					EDimension autoGrowth = pNp.getAutoGrowth();
					if (autoGrowth != null)
					{
						// grow the node to allow expandable port to fit
						tailNi.resize(autoGrowth.getWidth(), autoGrowth.getHeight());
						tailPoly = tailPi.getPoly();
						inTail = tailPoly.isInside(tailPoint);
					}
				}
	            if (!inTail) {
					// see if zero-size port is off-grid
	            	Rectangle2D tailBounds = tailPi.getBounds();
	            	double trueX = tailBounds.getCenterX();
	            	if (tailBounds.getWidth() == 0 && trueX != tailPoint.getX())
	            		trueX = DBMath.round(tailBounds.getCenterX());
	            	double trueY = tailBounds.getCenterX();
	            	if (tailBounds.getHeight() == 0 && trueY != tailPoint.getY())
	            		trueY = DBMath.round(tailBounds.getCenterY());
            		if (trueX == tailPoint.getX() && trueY == tailPoint.getY()) inTail = true;
	            }
			}
            if (!inTail) {
                // can't connect
            	Rectangle2D tailBounds = tailPi.getBounds();
                System.out.println("Arc tail (" + tailPoint.getX() + "," + tailPoint.getY() + ") not inside " + headPi + " which is "+
               		tailBounds.getMinX() + "<=X<=" + tailBounds.getMaxX() + " and " + tailBounds.getMinY() + "<=Y<=" + tailBounds.getMaxY());
                System.out.println("  Arc ran from " + headPi.getNodeInst() + ", port " + headPi.getPortProto().getName() +
                   	" to " + tailPi.getNodeInst() + ", port " + tailPi.getPortProto().getName());
                return null;
            }

			// now run the arc
            double thisWidth = arcBaseWidth;

            // The arc is zero length so better if arc width is min default width to avoid DRC errors if head/tail extended
            // The arc is zero length so better if arc width is zero to avoid DRC errors if head/tail is not extended
            if (headPoint.equals(tailPoint))
            {
                if (extendArcHead || extendArcTail || (!arcProto.getFactoryDefaultInst().isHeadExtended() && !arcProto.getFactoryDefaultInst().isTailExtended())) {
                    if (arcProto.getDefaultLambdaBaseWidth(ep) < thisWidth)
                        thisWidth = arcProto.getDefaultLambdaBaseWidth(ep);   // Sept 4 2008. Force to be a flat arc
                } else
                {
                	if (arcProto != Schematics.tech().bus_arc)
                		thisWidth = 0; // Sept 4 2008. Force to be a single point
                }
            }
            ArcInst newAi = ArcInst.makeInstanceBase(arcProto, ep, thisWidth, headPi, tailPi, headPoint, tailPoint, arcName);
            if (newAi == null) return null;

            // for zero-length arcs, ensure that the angle is sensible
            if (!arcAngleSet)
            {
            	if (headPoint.getX() == tailPoint.getX() && headPoint.getY() == tailPoint.getY() && arcAngle == 0)
	            {
	            	Rectangle2D headRect = headPi.getNodeInst().getBounds();
	            	Rectangle2D tailRect = tailPi.getNodeInst().getBounds();
	            	if (Math.abs(headRect.getCenterX() - tailRect.getCenterX()) < Math.abs(headRect.getCenterY() - tailRect.getCenterY()))
	            		arcAngle = 900;
	            }
            }
            if (arcAngle != 0)
                newAi.setAngle(arcAngle);
            if ((arcName != null) && (arcNameDescriptor != null)) {
                newAi.setTextDescriptor(ArcInst.ARC_NAME, arcNameDescriptor);
            }
            setDone();
            arcInst = newAi;
            arcInst.copyPropertiesFrom(inheritFrom);
            arcInst.setHeadExtended(extendArcHead);
            arcInst.setTailExtended(extendArcTail);
            return newAi;
        }
        if (getAction() == RouteElementAction.deleteArc) {
            // delete existing arc
        	if (arcInst.isLinked())
        		arcInst.kill();
            setDone();
        }
        return null;
    }

    /**
     * Adds RouteElement to highlights
     */
    public void addHighlightArea(Highlighter highlighter) {

        if (!isShowHighlight()) return;

        if (getAction() == RouteElementAction.newArc) {
            // figure out highlight area based on arc width and start and end locations
            Point2D headPoint = headConnPoint;
            Point2D tailPoint = tailConnPoint;

            double offset = 0.5*getArcBaseWidth();
            Cell cell = getCell();

            // if they are the same point, the two nodes are in top of each other
            int angle = (headPoint.equals(tailPoint)) ? 0 : GenMath.figureAngle(tailPoint, headPoint);
            double length = headPoint.distance(tailPoint);
        	Poly poly = Poly.makeEndPointPoly(length, getArcBaseWidth(), angle, headPoint, offset,
        		tailPoint, offset, Poly.Type.FILLED);
        	Point2D [] points = poly.getPoints();
        	for(int i=0; i<points.length; i++)
        	{
        		int last = i-1;
        		if (last < 0) last = points.length - 1;
        		highlighter.addLine(points[last], points[i], cell);
        	}

//            double offsetX, offsetY;
//            boolean endsExtend = arcProto.isExtended();
//            double offsetEnds = endsExtend ? offset : 0;
//            Point2D head1, head2, tail1, tail2;
//            if (headPoint.getX() == tailPoint.getX()) {
//                // vertical arc
//                if (headPoint.getY() > tailPoint.getY()) {
//                    offsetX = offset;
//                    offsetY = offsetEnds;
//                } else {
//                    offsetX = offset;
//                    offsetY = -offsetEnds;
//                }
//                head1 = new Point2D.Double(headPoint.getX()+offsetX, headPoint.getY()+offsetY);
//                head2 = new Point2D.Double(headPoint.getX()-offsetX, headPoint.getY()+offsetY);
//                tail1 = new Point2D.Double(tailPoint.getX()+offsetX, tailPoint.getY()-offsetY);
//                tail2 = new Point2D.Double(tailPoint.getX()-offsetX, tailPoint.getY()-offsetY);
//            } else {
//                //assert(headPoint.getY() == tailPoint.getY());
//                if (headPoint.getX() > tailPoint.getX()) {
//                    offsetX = offsetEnds;
//                    offsetY = offset;
//                } else {
//                    offsetX = -offsetEnds;
//                    offsetY = offset;
//                }
//                head1 = new Point2D.Double(headPoint.getX()+offsetX, headPoint.getY()+offsetY);
//                head2 = new Point2D.Double(headPoint.getX()+offsetX, headPoint.getY()-offsetY);
//                tail1 = new Point2D.Double(tailPoint.getX()-offsetX, tailPoint.getY()+offsetY);
//                tail2 = new Point2D.Double(tailPoint.getX()-offsetX, tailPoint.getY()-offsetY);
//            }
//            highlighter.addLine(head1, tail1, cell);
//            //Highlight.addLine(headPoint, tailPoint, cell);
//            highlighter.addLine(head2, tail2, cell);
//            highlighter.addLine(head1, head2, cell);
//            highlighter.addLine(tail1, tail2, cell);
        }
        if (getAction() == RouteElementAction.deleteArc) {
            highlighter.addElectricObject(arcInst, getCell());
        }
    }

    /** Return string describing the RouteElement */
    public String toString() {
        if (getAction() == RouteElementAction.newArc) {
            return "RouteElementArc-new "+arcProto+" width="+arcBaseWidth+",\n   head: "+
            	headRE+" at ("+headConnPoint.getX()+","+headConnPoint.getY()+")\n   tail: "+
            	tailRE+" at ("+tailConnPoint.getX()+","+tailConnPoint.getY()+")";
        }
        else if (getAction() == RouteElementAction.deleteArc) {
            return "RouteElementArc-delete "+arcInst;
        }
        return "RouteElement bad action";
    }

}
