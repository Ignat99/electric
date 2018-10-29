/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Highlight.java
 *
 * Copyright (c) 2006, Static Free Software. All rights reserved.
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
package com.sun.electric.tool.user;

import com.sun.electric.database.geometry.EPoint;
import com.sun.electric.database.geometry.Poly;
import com.sun.electric.database.geometry.ScreenPoint;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.Export;
import com.sun.electric.database.hierarchy.Nodable;
import com.sun.electric.database.network.Netlist;
import com.sun.electric.database.network.Network;
import com.sun.electric.database.network.NetworkTool;
import com.sun.electric.database.prototype.PortProto;
import com.sun.electric.database.text.Name;
import com.sun.electric.database.topology.ArcInst;
import com.sun.electric.database.topology.Connection;
import com.sun.electric.database.topology.Geometric;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.topology.PortInst;
import com.sun.electric.database.variable.DisplayedText;
import com.sun.electric.database.variable.ElectricObject;
import com.sun.electric.database.variable.TextDescriptor;
import com.sun.electric.database.variable.Variable;
import com.sun.electric.technology.PrimitiveNode;
import com.sun.electric.technology.technologies.Artwork;
import com.sun.electric.tool.user.redisplay.AbstractLayerDrawing;
import com.sun.electric.tool.user.redisplay.ERaster;
import com.sun.electric.tool.user.ui.EditWindow;
import com.sun.electric.tool.user.ui.ToolBar;
import com.sun.electric.tool.user.ui.Util;
import com.sun.electric.tool.user.ui.WindowFrame;
import com.sun.electric.util.math.DBMath;
import com.sun.electric.util.math.FixpTransform;
import com.sun.electric.util.math.GenMath;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Stroke;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.font.LineMetrics;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * A Highlight (or subclass thereof) includes a reference to something
 * to which the user's attention is being called (an ElectricObject,
 * some text, a region, etc) and enough information to render the
 * highlighting (boldness, etc) on any given window.  It is not
 * specific to any given EditWindow.
 */
public abstract class Highlight implements Cloneable{

	/** for drawing solid lines */		public static final BasicStroke solidLine = new BasicStroke(0);
	/** for drawing dotted lines */		public static final BasicStroke dottedLine = new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[] {1}, 0);
	/** for drawing dashed lines */		public static final BasicStroke dashedLine = new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[] {10}, 0);
	/** for drawing dashed lines */		public static final BasicStroke boldLine = new BasicStroke(3);

	/** The Cell containing the selection. */	protected final Cell cell;
	/** The color used when drawing */			protected final Color color;
    /** The highlight is an error */		    public final boolean isError;
	private static final int CROSSSIZE = 3;

	Highlight(Cell c, Color color, boolean isError)
	{
		this.cell = c;
		this.color = color;
        this.isError = isError;
	}

	public Cell getCell() { return cell; }

	public boolean isValid()
	{
		if (cell != null)
			if (!cell.isLinked()) return false;
		return true;
	}

    public boolean showInRaster() {
        return false;
    }

    // creating so HighlightEOBJ is not a public class
    public boolean isHighlightEOBJ() { return false; }

    // creating so HighlightText is not a public class
    public boolean isHighlightText() { return false; }

    public Object getObject() { return null; }

    public Variable.Key getVarKey() { return null; }

    // point variable, only useful for HighlightEOBJ?
    public int getPoint() { return -1; }

    @Override
    public Object clone()
    {
        try {
			return super.clone();
		}
		catch (CloneNotSupportedException e) {
            e.printStackTrace();
		}
        return null;
    }

    /**
	 * Method to tell whether two Highlights are the same.
	 * @param obj the Highlight to compare to this one.
	 * @param exact true to ensure that even ports are the same.
	 * @return true if the two refer to the same thing.
	 */
    public boolean sameThing(Highlight obj, boolean exact)
    {
        return false;
    }

    /**
	 * Method to tell whether this Highlight is text that stays with its node.
	 * The two possibilities are (1) text on invisible pins
	 * (2) export names, when the option to move exports with their labels is requested.
	 * @return true if this Highlight is text that should move with its node.
	 */
    public boolean nodeMovesWithText()
	{
		return false;
	}

    /** the highlight pattern will repeat itself rotationally every PULSATE_ROTATE_PERIOD milliseconds */
    private static final int PULSATE_ROTATE_PERIOD = 1000;

    /** the length of the rotating pattern */
    private static final int PULSATE_STRIPE_LENGTH = 30;

    /** the number of "segments" in each stripe; increasing this number slows down rendering*/
    private static final int PULSATE_STRIPE_SEGMENTS = 10;

    /**
	 * Method to display this Highlight in a window.
	 * @param wnd the window in which to draw this highlight.
	 * @param g_ the Graphics associated with the window.
	 */
	public void showHighlight(EditWindow wnd, Graphics g_, long highOffX, long highOffY, boolean onlyHighlight,
                              Color mainColor, Stroke primaryStroke)
    {
        if (!isValid()) return;
		g_.setColor(mainColor);
        Graphics2D g2 = (Graphics2D)g_;
        g2.setStroke(primaryStroke);
        if (User.isErrorHighlightingPulsate() && isError) {
            long now = System.currentTimeMillis();
            for(int i=0; i<PULSATE_STRIPE_SEGMENTS; i++) {
                float h = Util.hueFromColor(mainColor);
                float s = 1;
                float v = (i / ((float)PULSATE_STRIPE_SEGMENTS));
                g2.setColor(Util.colorFromHSV(h, s, v));
                float segment_length = PULSATE_STRIPE_LENGTH / ((float)PULSATE_STRIPE_SEGMENTS);
                g2.setStroke(new BasicStroke(1, BasicStroke.CAP_ROUND,  BasicStroke.JOIN_ROUND, 20,
                     new float[] { segment_length, PULSATE_STRIPE_LENGTH-segment_length },
                     (((now % PULSATE_ROTATE_PERIOD) * PULSATE_STRIPE_LENGTH) / ((float)PULSATE_ROTATE_PERIOD)) + i));
                showInternalHighlight(wnd, g2, highOffX, highOffY, onlyHighlight);
            }
		} else {
			showInternalHighlight(wnd, g_, highOffX, highOffY, onlyHighlight);
		}
    }

    abstract void showInternalHighlight(EditWindow wnd, Graphics g, long highOffX, long highOffY,
                                        boolean onlyHighlight);

    public void showHighlight(FixpTransform outOfPlaceTransfrom, AbstractLayerDrawing  ald, ERaster raster) {
        throw new UnsupportedOperationException();
    }

    /**
     * highlight objects that are electrically connected to this object
     * unless specified not to. HighlightConnected is set to false by addNetwork when
     * it figures out what's connected and adds them manually. Because they are added
     * in addNetwork, we shouldn't try and add connected objects here.
     * @param g2
     * @param wnd
     */
    void showHighlightsConnected(Graphics2D g2, EditWindow wnd) {
    }

    public void showHighlightsConnected(FixpTransform outOfPlaceTransform, AbstractLayerDrawing  ald, ERaster raste) {
    }

    /**
	 * Method to populate a List of all highlighted Geometrics.
     * @param list the list to populate
	 * @param wantNodes true if NodeInsts should be included in the list.
	 * @param wantArcs true if ArcInsts should be included in the list.
	 */
    void getHighlightedEObjs(Highlighter highlighter, List<Geometric> list, boolean wantNodes, boolean wantArcs) {;}

    static void getHighlightedEObjsInternal(Geometric geom, List<Geometric> list, boolean wantNodes, boolean wantArcs)
    {
        if (geom == null) return;
        if (!wantNodes && geom instanceof NodeInst) return;
        if (!wantArcs && geom instanceof ArcInst) return;

        if (list.contains(geom)) return;
        list.add(geom);
    }

    /**
	 * Method to return the Geometric object that is in this Highlight.
	 * If the highlight is a PortInst, an Export, or annotation text, its base NodeInst is returned.
	 * @return the Geometric object that is in this Highlight.
	 * Returns null if this Highlight is not on a Geometric.
	 */
    public Geometric getGeometric() { return null; }

    /**
	 * Method to return a List of all highlighted NodeInsts.
	 * Return a list with the highlighted NodeInsts.
	 */
	void getHighlightedNodes(Highlighter highlighter, Set<NodeInst> set) {;}

    static void getHighlightedNodesInternal(Geometric geom, Set<NodeInst> set)
    {
        if (geom == null || !(geom instanceof NodeInst)) return;
        NodeInst ni = (NodeInst)geom;
        set.add(ni);
    }

    /**
	 * Method to return a List of all highlighted ArcInsts.
	 * Return a list with the highlighted ArcInsts.
	 */
    void getHighlightedArcs(Highlighter highlighter, Set<ArcInst> set) {;}

    static void getHighlightedArcsInternal(Geometric geom, Set<ArcInst> set)
    {
        if (geom == null || !(geom instanceof ArcInst)) return;
        ArcInst ai = (ArcInst)geom;

        set.add(ai);
    }

    /**
	 * Method to return a set of the currently selected networks.
	 * Return a set of the currently selected networks.
	 * If there are no selected networks, the list is empty.
	 */
    void getHighlightedNetworks(Set<Network> nets, Netlist netlist) {;}

    /**
	 * Method to return a List of all highlighted text.
     * @param list list to populate.
	 * @param unique true to request that the text objects be unique,
	 * and not attached to another object that is highlighted.
	 * For example, if a node and an export on that node are selected,
	 * the export text will not be included if "unique" is true.
	 * Return a list with the Highlight objects that point to text.
	 */
    void getHighlightedText(List<DisplayedText> list, boolean unique, List<Highlight> getHighlights) {;}

    /**
	 * Method to return the bounds of the highlighted objects.
	 * @param wnd the window in which to get bounds.
	 * @return the bounds of the highlighted objects (null if nothing is highlighted).
	 */
    Rectangle2D getHighlightedArea(EditWindow wnd) { return null; }

    /**
	 * Method to return the ElectricObject associated with this Highlight object.
	 * @return the ElectricObject associated with this Highlight object.
	 */
    public ElectricObject getElectricObject() { return null; }

    /**
	 * Method to tell whether a point is over this Highlight.
	 * @param wnd the window being examined.
	 * @param x the X screen coordinate of the point.
	 * @param y the Y screen coordinate of the point.
	 * @param change true to update the highlight; false to leave things alone.
	 * @return (possible updated) this Highlight if the point is over this Highlight, null otherwise
	 */
    Highlight overHighlighted(EditWindow wnd, int x, int y, Highlighter highlighter, boolean change) { return null; }

    public String getInfo() { return null;}

    /**
     * Method to get a list of Arcs and Nodes from a Highlight list. 
     * If a port is found, it adds the node instance associated to.
     * @param list the list of highlighted objects.
     * @return a list with nodes and arcs
     */
    public static List<ElectricObject> getEOBJElements(List<Highlight> list)
    {
    	List<ElectricObject> l = new ArrayList<ElectricObject>();
    	
    	for(Highlight h : list)
        {
            ElectricObject eobj = h.getElectricObject();
            if (h.isHighlightEOBJ())
            {
                if (eobj instanceof PortInst)
                {
                	l.add(((PortInst)eobj).getNodeInst());
                } else if (eobj instanceof ArcInst || eobj instanceof NodeInst)
                {
                	l.add(eobj);
                }
            }
        }
    	return l;
    }
    
    /**
     * Method to load an array of counts with the number of highlighted objects in a list.
     * arc = 0, node = 1, export = 2, text = 3, graphics = 4
     * @param list the list of highlighted objects.
     * @param counts the array of counts to set.
     * @return a NodeInst, if it is in the list.
     */
    public static NodeInst getInfoCommand(List<Highlight> list, int[] counts)
    {
        // information about the selected items
        NodeInst theNode = null;
        for(Highlight h : list)
        {
            ElectricObject eobj = h.getElectricObject();
            if (h.isHighlightEOBJ())
            {
                if (eobj instanceof NodeInst || eobj instanceof PortInst)
                {
                    counts[1]++;
                    if (eobj instanceof NodeInst) theNode = (NodeInst)eobj; else
                        theNode = ((PortInst)eobj).getNodeInst();
                } else if (eobj instanceof ArcInst)
                {
                    counts[0]++;
                }
            } else if (h.isHighlightText())
            {
            	if (h.getVarKey() == Export.EXPORT_NAME) counts[2]++; else
            	{
            		if (h.getElectricObject() instanceof NodeInst)
            			theNode = (NodeInst)h.getElectricObject();
                    counts[3]++;
            	}
            } else if (h instanceof HighlightArea)
            {
                counts[4]++;
            } else if (h instanceof HighlightLine)
            {
                counts[4]++;
            }
        }
        return theNode;
    }

    /**
	 * Method to draw an array of points as highlighting.
	 * @param wnd the window in which drawing is happening.
     * @param g the Graphics for the window.
     * @param points the array of points being drawn.
     * @param offX the X offset of the drawing.
     * @param offY the Y offset of the drawing.
     * @param opened true if the points are drawn "opened".
     * @param thickLine
     */
	public static void drawOutlineFromPoints(EditWindow wnd, Graphics g, Point2D[] points, long offX, long offY,
                                             boolean opened, boolean thickLine)
	{
		boolean onePoint = true;
		if (points.length <= 0)
			return;
		ScreenPoint firstP = wnd.databaseToScreen(points[0].getX(), points[0].getY());
		for(int i=1; i<points.length; i++)
		{
			ScreenPoint p = wnd.databaseToScreen(points[i].getX(), points[i].getY());
			if (DBMath.doublesEqual(p.getX(), firstP.getX()) &&
				DBMath.doublesEqual(p.getY(), firstP.getY())) continue;
			onePoint = false;
			break;
		}
		if (onePoint)
		{
			drawLine(g, wnd, firstP.getX() + offX-CROSSSIZE, firstP.getY() + offY, firstP.getX() + offX+CROSSSIZE, firstP.getY() + offY);
			drawLine(g, wnd, firstP.getX() + offX, firstP.getY() + offY-CROSSSIZE, firstP.getX() + offX, firstP.getY() + offY+CROSSSIZE);
			return;
		}

		// find the center
		int cX = 0, cY = 0;
		Point p = new Point(0, 0);
		Point2D ptXF = new Point2D.Double(0, 0);
		for(int i=0; i<points.length; i++)
		{
			int lastI = i-1;
			if (lastI < 0)
			{
				if (opened) continue;
				lastI = points.length - 1;
			}
			Point2D pt = points[lastI];
			if (wnd.isInPlaceEdit())
			{
		   		wnd.getInPlaceTransformOut().transform(pt, ptXF);
		   		pt = ptXF;
			}
			wnd.gridToScreen(DBMath.lambdaToGrid(pt.getX()), DBMath.lambdaToGrid(pt.getY()), p);
			long fX = p.x + offX;   long fY = p.y + offY;
			pt = points[i];
			if (wnd.isInPlaceEdit())
			{
		   		wnd.getInPlaceTransformOut().transform(pt, ptXF);
		   		pt = ptXF;
			}
			wnd.gridToScreen(DBMath.lambdaToGrid(pt.getX()), DBMath.lambdaToGrid(pt.getY()), p);
			long tX = p.x + offX;    long tY = p.y + offY;
			drawLine(g, wnd, fX, fY, tX, tY);
			if (thickLine)
			{
				if (fX < cX) fX--; else fX++;
				if (fY < cY) fY--; else fY++;
				if (tX < cX) tX--; else tX++;
				if (tY < cY) tY--; else tY++;
				drawLine(g, wnd, fX, fY, tX, tY);
			}
		}
	}

    /**
	 * Method to draw an array of points as highlighting.
     * @param points the array of points being drawn.
     * @param offX the X offset of the drawing.
     * @param offY the Y offset of the drawing.
     * @param opened true if the points are drawn "opened".
     * @param thickLine true to draw the line thick.
     */
	public static void drawOutlineFromPoints(FixpTransform outOfPlaceTransform, AbstractLayerDrawing ald, ERaster raster,
		Point2D[] points, int offX, int offY, boolean opened, boolean thickLine)
	{
		boolean onePoint = true;
		if (points.length <= 0)
			return;
        Point firstP = new Point();
        Point p = new Point();
		ald.databaseToScreen(points[0].getX(), points[0].getY(), firstP);
		for(int i=1; i<points.length; i++)
		{
			ald.databaseToScreen(points[i].getX(), points[i].getY(), p);
			if (p.equals(firstP)) continue;
			onePoint = false;
			break;
		}
		if (onePoint)
		{
            Point2D pt = points[0];
            if (outOfPlaceTransform != null)
                outOfPlaceTransform.transform(pt, pt);
            ald.drawCross((int)DBMath.lambdaToGrid(pt.getX()), (int)DBMath.lambdaToGrid(pt.getY()), CROSSSIZE, raster);
			return;
		}

        if (outOfPlaceTransform != null) {
            for (int i = 0; i < points.length; i++) {
                outOfPlaceTransform.transform(points[i], points[i]);
            }
        }
		for(int i=0; i<points.length; i++)
		{
			int lastI = i-1;
			if (lastI < 0)
			{
				if (opened) continue;
				lastI = points.length - 1;
			}
			Point2D pt1 = points[lastI];
			Point2D pt2 = points[i];
			ald.drawLine((int)DBMath.lambdaToGrid(pt1.getX()), (int)DBMath.lambdaToGrid(pt1.getY()),
                    (int)DBMath.lambdaToGrid(pt2.getX()), (int)DBMath.lambdaToGrid(pt2.getY()),
                0, raster);
		}
	}

	void internalDescribe(StringBuffer desc) {}

    /**
     * Describe the Highlight
     * @return a string describing the highlight
     */
    public String describe() {
        StringBuffer desc = new StringBuffer();
        desc.append(this.getClass().getName());
        if (cell != null)
        {
	        desc.append(" in ");
	        desc.append(cell);
        }
        desc.append(": ");
        internalDescribe(desc);
        return desc.toString();
    }

    /**
     * Gets a poly that describes the Highlight for the NodeInst.
     * @param ni the nodeinst to get a poly that will be used to highlight it
     * @return a poly outlining the nodeInst.
     */
    public static Poly getNodeInstOutline(NodeInst ni)
    {
        FixpTransform trans = ni.rotateOutAboutTrueCenter();

        Poly poly = null;
        if (!ni.isCellInstance())
        {
        	PrimitiveNode pn = (PrimitiveNode)ni.getProto();

        	// special case for outline nodes
            if (pn.isHoldsOutline())
            {
                EPoint [] outline = ni.getTrace();
                if (outline != null)
                {
                    int numPoints = outline.length;
                    boolean whole = true;
                    for(int i=1; i<numPoints; i++)
                    {
                        if (outline[i] == null)
                        {
                            whole = false;
                            break;
                        }
                    }
					if (whole)
					{
	                    Poly.Point [] pointList = new Poly.Point[numPoints];
	                    for(int i=0; i<numPoints; i++)
	                    {
                            EPoint anchor = ni.getAnchorCenter();
	                        pointList[i] = Poly.fromFixp(anchor.getFixpX() + outline[i].getFixpX(),
	                            anchor.getFixpY() + outline[i].getFixpY());
	                    }
	                    trans.transform(pointList, 0, pointList, 0, numPoints);
	                    poly = new Poly(pointList);
	    				if (ni.getFunction() == PrimitiveNode.Function.NODE)
	    				{
	    					poly.setStyle(Poly.Type.FILLED);
	    				} else
	    				{
	    					poly.setStyle(Poly.Type.OPENED);
	    				}
	    				return poly;
					}
                }
            }

            // special case for circular Artwork nodes
            if (pn.getTechnology() == Artwork.tech())
            {
	            Poly[] polys = pn.getTechnology().getShapeOfNode(ni);
	            if (polys.length == 1)
	            {
	            	Poly.Type type = polys[0].getStyle();
	            	if (type == Poly.Type.CIRCLE || type == Poly.Type.DISC || type == Poly.Type.CIRCLEARC ||
	            		type == Poly.Type.THICKCIRCLE || type == Poly.Type.THICKCIRCLEARC)
	            	{
	        			double [] angles = ni.getArcDegrees();
        				Poly.Point [] pointList = Artwork.fillEllipse(ni.getAnchorCenter(), ni.getXSize(), ni.getYSize(), angles[0], angles[1]);
        				poly = new Poly(pointList);
        				poly.setStyle(Poly.Type.OPENED);
        				poly.transform(ni.rotateOut());
	            	}
	            }
            }

            // special case for curved pins
            if (pn.isCurvedPin())
            {
	            Poly[] polys = pn.getTechnology().getShapeOfNode(ni);
				poly = polys[0];
				poly.transform(ni.rotateOut());
            }
        }

        // setup outline of node with standard offset
        if (poly == null)
            poly = ni.getBaseShape();
        return poly;
    }

    /**
     * Implementing clipping here speeds things up a lot if there are
     * many large highlights off-screen
     */
    public static void drawLine(Graphics g, EditWindow wnd, long x1, long y1, long x2, long y2)
    {
        Dimension size = wnd.getScreenSize();

        // first clip the line
        Point pt1 = new Point((int)x1, (int)y1);
        Point pt2 = new Point((int)x2, (int)y2);
//        if (x1 != pt1.x || y1 != pt1.y || x2 != pt2.x || y2 != pt2.y)
//        {
//        	System.out.println("OVERFLOW!!!!!");
//        }
		if (GenMath.clipLine(pt1, pt2, 0, size.width-1, 0, size.height-1)) return;
		g.drawLine(pt1.x, pt1.y, pt2.x, pt2.y);
    }


    /**
     * General purpose function to sort Highlight objects based on their getInfo output
     */
    public static class HighlightSorting implements Comparator<Highlight>
    {
        @Override
        public int compare(Highlight h1, Highlight h2)
        {
        	String h1Info = h1.getInfo();
        	String h2Info = h2.getInfo();
        	if (h1Info == null) h1Info = "";
        	if (h2Info == null) h2Info = "";
            return h1Info.compareTo(h2Info);
        }
    }

    /**
     *  A Highlight which calls the user's attention to a Point2D and includes a text message.
     */
    public static class Message extends Highlight
    {
    	/** The highlighted message. */								protected final String msg;
        /** Location of the message highlight */                    protected final Point2D loc;
        /** Corner of text: 0=lowerLeft, 1=upperLeft, 2=upperRight, 3=lowerRight */ protected final int corner;
        private final Color backgroundColor;

        Message(Cell c, String m, Point2D p, int co, Color backgroundColor)
        {
            super(c, null, false);
            this.msg = m;
            this.loc = p;
            this.corner = co;
            this.backgroundColor = backgroundColor;
        }

        @Override
        void internalDescribe(StringBuffer desc)
        {
            desc.append(msg);
        }

        @Override
        public String getInfo() { return msg; }

        @Override
        public void showInternalHighlight(EditWindow wnd, Graphics g, long highOffX, long highOffY,
                                          boolean onlyHighlight)
        {
            ScreenPoint location = wnd.databaseToScreen(loc.getX(), loc.getY());
            long locX = location.getX(), locY = location.getY();
            int width=0, height=0;
            if (corner != 0 || backgroundColor != null)
            {
            	// determine the size of the text
    			Font font = g.getFont();
    			FontRenderContext frc = new FontRenderContext(null, true, true);
    			GlyphVector gv = font.createGlyphVector(frc, msg);
    			LineMetrics lm = font.getLineMetrics(msg, frc);
    			Rectangle2D rasRect = gv.getLogicalBounds();
    			width = (int)rasRect.getWidth();
    			height = (int)(lm.getHeight()+0.5);
            }
        	switch (corner)
        	{
        		case 1:		// put upper-left corner of text at drawing coordinate
        			locY += height;
        			break;
        		case 2:		// put upper-right corner of text at drawing coordinate
        			locY += height;
        			locX -= width;
        			break;
        		case 3:		// put lower-right corner of text at drawing coordinate
        			locY += height;
        			locX -= width;
        			break;
        	}
            Color oldColor = g.getColor();
            Color mainColor;
            if (color != null) mainColor = color; else
            	mainColor = new Color(User.getColor(User.ColorPrefType.TEXT));
            int mainColorRed = mainColor.getRed() & 0xFF;
            int mainColorGreen = mainColor.getGreen() & 0xFF;
            int mainColorBlue = mainColor.getBlue() & 0xFF;
            Color shadowColor = new Color(255-mainColorRed, 255-mainColorGreen, 255-mainColorBlue);
            if (backgroundColor == null)
            {
	            g.setColor(shadowColor);
	            g.drawString(msg, (int)(locX+1), (int)(locY+1));
            } else
            {
            	g.setColor(backgroundColor);
            	g.fillRect((int)locX, (int)(locY-height), width, height);
            }
            g.setColor(mainColor);
            g.drawString(msg, (int)locX, (int)locY);
            g.setColor(oldColor);
        }

        @Override
        Rectangle2D getHighlightedArea(EditWindow wnd)
        {
            return new Rectangle2D.Double(loc.getX(), loc.getY(), 0, 0);
        }
    }
}

/**
 *  A Highlight which calls the user's attention to a Poly.
 */
class HighlightPoly extends Highlight
{
    /** The highlighted polygon */                              private final Poly polygon;
    HighlightPoly(Cell c, Poly p, Color col)
    {
        super(c, col, false);
        this.polygon = p;
    }

    @Override
    public void showInternalHighlight(EditWindow wnd, Graphics g, long highOffX, long highOffY,
                                      boolean onlyHighlight)
    {
        // switch colors if specified
        Color oldColor = null;
        if (color != null)
        {
            oldColor = g.getColor();
            g.setColor(color);
        }

        // draw poly
    	Point2D[] points = polygon.getPoints();
        if (polygon.getStyle() == Poly.Type.FILLED)
        {
        	int [] xPoints = new int[points.length];
        	int [] yPoints = new int[points.length];
    		for(int i=0; i<points.length; i++)
    		{
    			ScreenPoint p = wnd.databaseToScreen(points[i].getX(), points[i].getY());
    			xPoints[i] = p.getIntX();
    			yPoints[i] = p.getIntY();
    		}
			g.fillPolygon(xPoints, yPoints, points.length);
        } else if (polygon.getStyle() == Poly.Type.DISC)
        {
        	long radius = Math.round(points[0].distance(points[1]) * wnd.getScale());
        	ScreenPoint ctr = wnd.databaseToScreen(points[0].getX(), points[0].getY());
        	g.fillOval((int)(ctr.getX()-radius), (int)(ctr.getY()-radius), (int)radius*2, (int)radius*2);
        } else
        {
	        boolean opened = (polygon.getStyle() == Poly.Type.OPENED);
	        drawOutlineFromPoints(wnd, g, points, highOffX, highOffY, opened, false);
        }

        // switch back to old color if switched
        if (oldColor != null)
            g.setColor(oldColor);
    }
}

/**
 *  A Highlight which calls the user's attention to a Line.
 */
class HighlightLine extends Highlight
{
	/** The highlighted line. */								protected final Point2D start, end, center;
    /** The highlighted line is thick. */					    protected final boolean thickLine;
    /** The highlighted line can only draw in this window */	protected final WeakReference<WindowFrame> onlyThis;

    HighlightLine(Cell c, Point2D s, Point2D e, Point2D cen, boolean thick, Color col, boolean isError, WindowFrame onlyThis)
    {
        super(c, col, isError);
        this.start = s;
        this.end = e;
        this.center = cen;
        this.thickLine = thick;
        this.onlyThis = onlyThis != null ? new WeakReference<WindowFrame>(onlyThis) : null;
    }

    @Override
    public void showInternalHighlight(EditWindow wnd, Graphics g, long highOffX, long highOffY,
                                      boolean onlyHighlight)
    {
    	if (onlyThis != null && onlyThis.get() != wnd.getWindowFrame()) return;

    	// switch colors if specified
        Color oldColor = null;
        if (color != null)
        {
            oldColor = g.getColor();
            g.setColor(color);
        }

        // draw line
        Point2D [] points = new Point2D.Double[2];
        points[0] = new Point2D.Double(start.getX(), start.getY());
        points[1] = new Point2D.Double(end.getX(), end.getY());
		drawOutlineFromPoints(wnd, g, points, highOffX, highOffY, false, thickLine);

        // switch back to old color if switched
        if (oldColor != null)
            g.setColor(oldColor);
    }

    @Override
    Rectangle2D getHighlightedArea(EditWindow wnd)
    {
        double cX = Math.min(start.getX(), end.getX());
        double cY = Math.min(start.getY(), end.getY());
        double sX = Math.abs(start.getX() - end.getX());
        double sY = Math.abs(start.getY() - end.getY());
		return new Rectangle2D.Double(cX, cY, sX, sY);
    }

    @Override
    public String getInfo()
    {
        String description = "Line from (" + start.getX() + "," + start.getY() + ") to (" +
            end.getX() + "," + end.getY() + ")";
        return description;
    }
}

class HighlightObject extends Highlight
{
	/** The highlighted generic object */                       private final Object object;
    HighlightObject(Cell c, Object obj)
    {
        super(c, null, false);
        this.object = obj;
    }

    @Override
    public Object getObject() { return object; }

    @Override
    public void showInternalHighlight(EditWindow wnd, Graphics g, long highOffX, long highOffY,
                                      boolean onlyHighlight)
    {
        System.out.println("Should call this one?");
    }
}

/**
 *  A Highlight which calls the user's attention to a Rectangle2D.
 */
class HighlightArea extends Highlight
{
    /** The highlighted area. */								protected Rectangle2D bounds;
    HighlightArea(Cell c, Color col, Rectangle2D area)
    {
        super(c, col, false);
		bounds = new Rectangle2D.Double();
		bounds.setRect(area);
    }

    @Override
    public void showInternalHighlight(EditWindow wnd, Graphics g, long highOffX, long highOffY,
                                      boolean onlyHighlight)
    {
        // switch colors if specified
        Color oldColor = null;
        if (color != null)
        {
            oldColor = g.getColor();
            g.setColor(color);
        }

        // draw area
        Point2D [] points = new Point2D.Double[5];
        points[0] = new Point2D.Double(bounds.getMinX(), bounds.getMinY());
        points[1] = new Point2D.Double(bounds.getMinX(), bounds.getMaxY());
        points[2] = new Point2D.Double(bounds.getMaxX(), bounds.getMaxY());
        points[3] = new Point2D.Double(bounds.getMaxX(), bounds.getMinY());
        points[4] = new Point2D.Double(bounds.getMinX(), bounds.getMinY());
        drawOutlineFromPoints(wnd, g, points, highOffX, highOffY, false, false);

        // switch back to old color if switched
        if (oldColor != null)
            g.setColor(oldColor);
    }

    @Override
    void getHighlightedEObjs(Highlighter highlighter, List<Geometric> list, boolean wantNodes, boolean wantArcs)
    {
        List<Highlight> inArea = Highlighter.findAllInArea(highlighter, cell, false, false, false, false, false, bounds, null);
        for(Highlight ah : inArea)
        {
            if (!(ah instanceof HighlightEOBJ)) continue;
            ElectricObject eobj = ((HighlightEOBJ)ah).eobj;
            if (eobj instanceof ArcInst) {
                if (wantArcs)
                    list.add((ArcInst)eobj);
            } else if (eobj instanceof NodeInst) {
                if (wantNodes)
                    list.add((NodeInst)eobj);
            } else if (eobj instanceof PortInst) {
                if (wantNodes)
                    list.add(((PortInst)eobj).getNodeInst());
            }
        }
    }

    @Override
    void getHighlightedNodes(Highlighter highlighter, Set<NodeInst> set)
    {
        List<Highlight> inArea = Highlighter.findAllInArea(highlighter, cell, false, false, false, false, false,
                bounds, null);
        for(Highlight ah : inArea)
        {
            if (!(ah instanceof HighlightEOBJ)) continue;
            ElectricObject eobj = ((HighlightEOBJ)ah).eobj;
            if (eobj instanceof NodeInst)
                set.add((NodeInst)eobj);
            else if (eobj instanceof PortInst)
                set.add(((PortInst)eobj).getNodeInst());
        }
    }

    @Override
    void getHighlightedArcs(Highlighter highlighter, Set<ArcInst> set)
    {
        List<Highlight> inArea = Highlighter.findAllInArea(highlighter, cell, false, false, false, false, false,
                bounds, null);
        for(Highlight ah : inArea)
        {
            if (!(ah instanceof HighlightEOBJ)) continue;
            ElectricObject eobj = ((HighlightEOBJ)ah).eobj;
            if (eobj instanceof ArcInst)
                set.add((ArcInst)eobj);
        }
    }

    @Override
    Rectangle2D getHighlightedArea(EditWindow wnd)
    {
        return bounds;
    }

    @Override
    public String getInfo()
    {
        String description = "Area from " + bounds.getMinX() + "<=X<=" + bounds.getMaxX() +
            " and " + bounds.getMinY() + "<=Y<=" + bounds.getMaxY();
        return description;
    }
}

/**
 *  A Highlight which calls the user's attention to an ElectricObject.
 */
class HighlightEOBJ extends Highlight
{
	/** The highlighted object. */								protected final ElectricObject eobj;
	/** For Highlighted networks, this prevents excess highlights */ private final boolean highlightConnected;
	/** The highlighted outline point (only for NodeInst). */	protected final int point;

	public HighlightEOBJ(ElectricObject e, Cell c, boolean connected, int p)
	{
		super(c, null, false);
		this.eobj = e;
		this.highlightConnected = connected;
		this.point = p;
	}

	public HighlightEOBJ(ElectricObject e, Cell c, boolean connected, int p, boolean isError)
	{
		super(c, null, isError);
		this.eobj = e;
		this.highlightConnected = connected;
		this.point = p;
	}

	public HighlightEOBJ(ElectricObject e, Cell c, boolean connected, int p, Color col)
	{
		super(c, col, false);
		this.eobj = e;
		this.highlightConnected = connected;
		this.point = p;
	}

	public HighlightEOBJ(HighlightEOBJ h, ElectricObject eobj, int p)
	{
		super(h.cell, h.color, h.isError);
		this.eobj = eobj;
		this.highlightConnected = h.highlightConnected;
		this.point = p;
	}

    @Override
	void internalDescribe(StringBuffer desc)
	{
		if (eobj instanceof PortInst) {
			desc.append(((PortInst)eobj).describe(true));
		}
		if (eobj instanceof NodeInst) {
			desc.append(((NodeInst)eobj).describe(true));
		}
		if (eobj instanceof ArcInst) {
			desc.append(((ArcInst)eobj).describe(true));
		}
	}

    @Override
	public ElectricObject getElectricObject() { return eobj; }

    @Override
	public boolean isHighlightEOBJ() { return true; }

    @Override
	public int getPoint() { return point; }

    @Override
	public boolean isValid()
	{
		if (!super.isValid()) return false;
		return eobj.isLinked();
	}

    @Override
    public boolean showInRaster() {
        return !isError && color == null && eobj instanceof NodeInst && point < 0;
    }

    @Override
    public boolean sameThing(Highlight obj, boolean exact)
	{
		if (this == obj) return (true);

		// Consider already obj==null
	    if (obj == null || getClass() != obj.getClass())
            return (false);

        ElectricObject realEObj = eobj;
        if (!exact && realEObj instanceof PortInst) realEObj = ((PortInst)realEObj).getNodeInst();

        HighlightEOBJ other = (HighlightEOBJ)obj;
        ElectricObject realOtherEObj = other.eobj;
        if (!exact && realOtherEObj instanceof PortInst) realOtherEObj = ((PortInst)realOtherEObj).getNodeInst();
        if (realEObj != realOtherEObj) return false;
        return true;
    }

    @Override
    public void showInternalHighlight(EditWindow wnd, Graphics g, long highOffX, long highOffY,
                                      boolean onlyHighlight)
    {
        if (eobj == null || !eobj.isLinked()) return;

		// switch colors if specified
        Color oldColor = null;
        if (color != null)
        {
            oldColor = g.getColor();
            g.setColor(color);
        }

        // highlight ArcInst
		if (eobj instanceof ArcInst)
		{
			ArcInst ai = (ArcInst)eobj;

            try {
                // construct the polygons that describe the basic arc
                Poly poly = ai.makeLambdaPoly(ai.getGridBaseWidth(), Poly.Type.CLOSED);
                if (poly == null) return;
                drawOutlineFromPoints(wnd, g, poly.getPoints(), highOffX, highOffY, false, false);

                if (onlyHighlight)
                {
                    // this is the only thing highlighted: give more information about constraints
                    String constraints = "X";
                    if (ai.isRigid()) constraints = "R"; else
                    {
                        if (ai.isFixedAngle())
                        {
                            if (ai.isSlidable()) constraints = "FS"; else
                                constraints = "F";
                        } else if (ai.isSlidable()) constraints = "S";
                    }
                    ScreenPoint p = wnd.databaseToScreen(ai.getTrueCenterX(), ai.getTrueCenterY());
                    Font font = wnd.getFont(null);
                    if (font != null)
                    {
                        GlyphVector gv = wnd.getGlyphs(constraints, font);
                        Rectangle2D glyphBounds = gv.getVisualBounds();
                        g.drawString(constraints, (int)(p.getX() - glyphBounds.getWidth()/2 + highOffX),
                            (int)(p.getY() + font.getSize()/2 + highOffY));
                    }
                }
            } catch (Error e) {
                throw e;
            }
			return;
		}

		// highlight NodeInst
		PortProto pp = null;
		ElectricObject realEObj = eobj;
        PortInst originalPi = null;
        if (realEObj instanceof PortInst)
		{
            originalPi = ((PortInst)realEObj);
            pp = originalPi.getPortProto();
			realEObj = ((PortInst)realEObj).getNodeInst();
		}
		if (realEObj instanceof NodeInst)
		{
			NodeInst ni = (NodeInst)realEObj;
			FixpTransform trans = ni.rotateOutAboutTrueCenter();
			long offX = highOffX;
			long offY = highOffY;

			// draw the selected point
			if (point >= 0)
			{
				EPoint [] points = ni.getTrace();
				if (points != null)
				{
					if (points.length <= point)
					{
						System.err.println("Invalid index " + point + " in trace point ");
						return;
					}
					
					// if this is a spline, highlight the true shape
					if (ni.getProto() == Artwork.tech().splineNode)
					{
						EPoint [] changedPoints = new EPoint[points.length];
						for(int i=0; i<points.length; i++)
						{
							changedPoints[i] = points[i];
							if (i == point)
							{
								double x = ni.getAnchorCenterX() + points[point].getX();
								double y = ni.getAnchorCenterY() + points[point].getY();
								Point2D thisPt = new Point2D.Double(x, y);
								trans.transform(thisPt, thisPt);
								ScreenPoint cThis = wnd.databaseToScreen(thisPt);
								Point2D db = wnd.screenToDatabase(cThis.getX()+offX, cThis.getY()+offY);
								changedPoints[i] = EPoint.fromLambda(db.getX() - ni.getAnchorCenterX(), db.getY() - ni.getAnchorCenterY());
							}
						}
						Point2D [] spPoints = Artwork.tech().fillSpline(ni.getAnchorCenter(), changedPoints);
						ScreenPoint cLast = wnd.databaseToScreen(spPoints[0]);
						for(int i=1; i<spPoints.length; i++)
						{
							ScreenPoint cThis = wnd.databaseToScreen(spPoints[i]);
							drawLine(g, wnd, cLast.getX(), cLast.getY(), cThis.getX(), cThis.getY());
							cLast = cThis;
						}
					}

					// draw an "x" through the selected point
					if (points[point] != null)
					{
						double x = ni.getAnchorCenterX() + points[point].getX();
						double y = ni.getAnchorCenterY() + points[point].getY();
						Point2D thisPt = new Point2D.Double(x, y);
						trans.transform(thisPt, thisPt);
						ScreenPoint cThis = wnd.databaseToScreen(thisPt);
						int size = 3;
						drawLine(g, wnd, cThis.getX() + size + offX, cThis.getY() + size + offY, cThis.getX() - size + offX, cThis.getY() - size + offY);
						drawLine(g, wnd, cThis.getX() + size + offX, cThis.getY() - size + offY, cThis.getX() - size + offX, cThis.getY() + size + offY);

						// find previous and next point, and draw lines to them
						boolean showWrap = ni.traceWraps();
						Point2D prevPt = null, nextPt = null;
						int prevPoint = point - 1;
						if (prevPoint < 0 && showWrap) prevPoint = points.length - 1;
						if (prevPoint >= 0 && points[prevPoint] != null)
						{
							prevPt = new Point2D.Double(ni.getAnchorCenterX() + points[prevPoint].getX(),
								ni.getAnchorCenterY() + points[prevPoint].getY());
							trans.transform(prevPt, prevPt);
							if (prevPt.getX() == thisPt.getX() && prevPt.getY() == thisPt.getY()) prevPoint = -1; else
							{
								ScreenPoint cPrev = wnd.databaseToScreen(prevPt);
								drawLine(g, wnd, cThis.getX() + offX, cThis.getY() + offY, cPrev.getX(), cPrev.getY());
							}
						}
						int nextPoint = point + 1;
						if (nextPoint >= points.length)
						{
							if (showWrap) nextPoint = 0; else
								nextPoint = -1;
						}
						if (nextPoint >= 0 && points[nextPoint] != null)
						{
							nextPt = new Point2D.Double(ni.getAnchorCenterX() + points[nextPoint].getX(),
								ni.getAnchorCenterY() + points[nextPoint].getY());
							trans.transform(nextPt, nextPt);
							if (nextPt.getX() == thisPt.getX() && nextPt.getY() == thisPt.getY()) nextPoint = -1; else
							{
								ScreenPoint cNext = wnd.databaseToScreen(nextPt);
								drawLine(g, wnd, cThis.getX() + offX, cThis.getY() + offY, cNext.getX(), cNext.getY());
							}
						}

						// draw arrows on the lines
						if (offX == 0 && offY == 0 && points.length > 2 && prevPt != null && nextPt != null)
						{
							double arrowLen = Double.MAX_VALUE;
							if (prevPoint >= 0) arrowLen = Math.min(thisPt.distance(prevPt), arrowLen);
							if (nextPoint >= 0) arrowLen = Math.min(thisPt.distance(nextPt), arrowLen);
							arrowLen /= 10;
							double angleOfArrow = Math.PI * 0.8;
							if (prevPoint >= 0)
							{
								Point2D prevCtr = new Point2D.Double((prevPt.getX()+thisPt.getX()) / 2,
									(prevPt.getY()+thisPt.getY()) / 2);
								double prevAngle = DBMath.figureAngleRadians(prevPt, thisPt);
								Point2D prevArrow1 = new Point2D.Double(prevCtr.getX() + Math.cos(prevAngle+angleOfArrow) * arrowLen,
									prevCtr.getY() + Math.sin(prevAngle+angleOfArrow) * arrowLen);
								Point2D prevArrow2 = new Point2D.Double(prevCtr.getX() + Math.cos(prevAngle-angleOfArrow) * arrowLen,
									prevCtr.getY() + Math.sin(prevAngle-angleOfArrow) * arrowLen);
								ScreenPoint cPrevCtr = wnd.databaseToScreen(prevCtr);
								ScreenPoint cPrevArrow1 = wnd.databaseToScreen(prevArrow1);
								ScreenPoint cPrevArrow2 = wnd.databaseToScreen(prevArrow2);
								drawLine(g, wnd, cPrevCtr.getX(), cPrevCtr.getY(), cPrevArrow1.getX(), cPrevArrow1.getY());
								drawLine(g, wnd, cPrevCtr.getX(), cPrevCtr.getY(), cPrevArrow2.getX(), cPrevArrow2.getY());
							}

							if (nextPoint >= 0)
							{
								Point2D nextCtr = new Point2D.Double((nextPt.getX()+thisPt.getX()) / 2,
									(nextPt.getY()+thisPt.getY()) / 2);
								double nextAngle = DBMath.figureAngleRadians(thisPt, nextPt);
								Point2D nextArrow1 = new Point2D.Double(nextCtr.getX() + Math.cos(nextAngle+angleOfArrow) * arrowLen,
									nextCtr.getY() + Math.sin(nextAngle+angleOfArrow) * arrowLen);
								Point2D nextArrow2 = new Point2D.Double(nextCtr.getX() + Math.cos(nextAngle-angleOfArrow) * arrowLen,
									nextCtr.getY() + Math.sin(nextAngle-angleOfArrow) * arrowLen);
								ScreenPoint cNextCtr = wnd.databaseToScreen(nextCtr);
								ScreenPoint cNextArrow1 = wnd.databaseToScreen(nextArrow1);
								ScreenPoint cNextArrow2 = wnd.databaseToScreen(nextArrow2);
								drawLine(g, wnd, cNextCtr.getX(), cNextCtr.getY(), cNextArrow1.getX(), cNextArrow1.getY());
								drawLine(g, wnd, cNextCtr.getX(), cNextCtr.getY(), cNextArrow2.getX(), cNextArrow2.getY());
							}
						}
					}

					// do not offset the node, just this point
					offX = offY = 0;
				}
			}

            // draw nodeInst outline
            if ((offX == 0 && offY == 0) || point < 0)
            {
            	Poly niPoly = getNodeInstOutline(ni);
                boolean niOpened = (niPoly.getStyle() == Poly.Type.OPENED);
            	Point2D [] points = niPoly.getPoints();
            	drawOutlineFromPoints(wnd, g, points, offX, offY, niOpened, false);
            }

			// draw the selected port
			if (pp != null)
			{
				Poly poly = ni.getShapeOfPort(pp);
				boolean opened = true;
				Point2D [] points = poly.getPoints();
				if (poly.getStyle() == Poly.Type.FILLED || poly.getStyle() == Poly.Type.CLOSED) opened = false;
				if (poly.getStyle() == Poly.Type.CIRCLE || poly.getStyle() == Poly.Type.THICKCIRCLE ||
					poly.getStyle() == Poly.Type.DISC)
				{
					double sX = points[0].distance(points[1]) * 2;
					Poly.Point [] pts = Artwork.fillEllipse(points[0], sX, sX, 0, 360);
					poly = new Poly(pts);
					poly.transform(ni.rotateOut());
					points = poly.getPoints();
				} else if (poly.getStyle() == Poly.Type.CIRCLEARC)
				{
					double [] angles = ni.getArcDegrees();
					double sX = points[0].distance(points[1]) * 2;
					Poly.Point [] pts = Artwork.fillEllipse(points[0], sX, sX, angles[0], angles[1]);
					poly = new Poly(pts);
					poly.transform(ni.rotateOut());
					points = poly.getPoints();
				}
				drawOutlineFromPoints(wnd, g, points, offX, offY, opened, false);

                // show name of port
                if (ni.isCellInstance() && (g instanceof Graphics2D))
				{
					// only show name if port is wired (because all other situations already show the port)
					boolean wired = false;
					for(Iterator<Connection> cIt = ni.getConnections(); cIt.hasNext(); )
					{
						Connection con = cIt.next();
						if (con.getPortInst().getPortProto() == pp) { wired = true;   break; }
					}
					if (wired)
					{
	                    Font font = new Font(User.getDefaultFont(), Font.PLAIN, (int)(1.5*EditWindow.getDefaultFontSize()));
    	                GlyphVector v = wnd.getGlyphs(pp.getName(), font);
        	            ScreenPoint point = wnd.databaseToScreen(poly.getCenterX(), poly.getCenterY());
            	        ((Graphics2D)g).drawGlyphVector(v, (float)point.getX()+offX, (float)point.getY()+offY);
					}
                }
			}
		}

		// switch back to old color if switched
		if (oldColor != null)
			g.setColor(oldColor);
	}

    /**
     * highlight objects that are electrically connected to this object
     * unless specified not to. HighlightConnected is set to false by addNetwork when
     * it figures out what's connected and adds them manually. Because they are added
     * in addNetwork, we shouldn't try and add connected objects here.
     * @param g2
     * @param wnd
     */
    @Override
    void showHighlightsConnected(Graphics2D g2, EditWindow wnd) {
        if (!isValid() || !(eobj instanceof PortInst) || !highlightConnected) return;
        PortInst originalPi = (PortInst)eobj;
        Netlist netlist = cell.getNetlist();
        if (netlist == null) return;
        NodeInst ni = originalPi.getNodeInst();
        NodeInst originalNI = ni;
        PortProto pp = originalPi.getPortProto();
        if (ni.isIconOfParent())
        {
            // find export in parent
            Export equiv = (Export)cell.findPortProto(pp.getName());
            if (equiv != null)
            {
                originalPi = equiv.getOriginalPort();
                ni = originalPi.getNodeInst();
                pp = originalPi.getPortProto();
            }
        }
        Set<Network> networks = new HashSet<Network>();
        networks = NetworkTool.getNetworksOnPort(originalPi, netlist, networks);

        Set<Geometric> markObj = new HashSet<Geometric>();
        for(Iterator<ArcInst> it = cell.getArcs(); it.hasNext(); )
        {
            ArcInst ai = it.next();
            Name arcName = ai.getNameKey();
            for (int i=0; i<arcName.busWidth(); i++) {
                if (networks.contains(netlist.getNetwork(ai, i))) {
                    markObj.add(ai);
                    markObj.add(ai.getHeadPortInst().getNodeInst());
                    markObj.add(ai.getTailPortInst().getNodeInst());
                    break;
                }
            }
        }

        for (Iterator<Nodable> it = netlist.getNodables(); it.hasNext(); ) {
            Nodable no = it.next();
            NodeInst oNi = no.getNodeInst();
            if (oNi == originalNI) continue;
            if (markObj.contains(ni)) continue;

            boolean highlightNo = false;
            for(Iterator<PortProto> eIt = no.getProto().getPorts(); eIt.hasNext(); )
            {
                PortProto oPp = eIt.next();
                Name opName = oPp.getNameKey();
                for (int j=0; j<opName.busWidth(); j++) {
                    if (networks.contains(netlist.getNetwork(no, oPp, j))) {
                        highlightNo = true;
                        break;
                    }
                }
                if (highlightNo) break;
            }
            if (highlightNo)
                markObj.add(oNi);
        }

        // draw lines along all of the arcs on the network
        Stroke origStroke = g2.getStroke();
        g2.setStroke(dashedLine);
        for(Iterator<ArcInst> it = cell.getArcs(); it.hasNext(); )
        {
            ArcInst ai = it.next();
            if (!markObj.contains(ai)) continue;
            ScreenPoint c1 = wnd.databaseToScreen(ai.getHeadLocation());
            ScreenPoint c2 = wnd.databaseToScreen(ai.getTailLocation());
            drawLine(g2, wnd, c1.getX(), c1.getY(), c2.getX(), c2.getY());
        }

        // draw dots in all connected nodes
        g2.setStroke(solidLine);
        for (Iterator<NodeInst> it = cell.getNodes(); it.hasNext(); )
        {
            NodeInst oNi = it.next();
            if (!markObj.contains(oNi)) continue;

            ScreenPoint c = wnd.databaseToScreen(oNi.getTrueCenter());
            g2.fillOval(c.getIntX()-4, c.getIntY()-4, 8, 8);

            // connect the center dots to the input arcs
            Point2D nodeCenter = oNi.getTrueCenter();
            for(Iterator<Connection> pIt = oNi.getConnections(); pIt.hasNext(); )
            {
                Connection con = pIt.next();
                ArcInst ai = con.getArc();
                if (!markObj.contains(ai)) continue;
                Point2D arcEnd = con.getLocation();
                if (arcEnd.getX() != nodeCenter.getX() || arcEnd.getY() != nodeCenter.getY())
                {
                    ScreenPoint c1 = wnd.databaseToScreen(arcEnd);
                    ScreenPoint c2 = wnd.databaseToScreen(nodeCenter);
                    drawLine(g2, wnd, c1.getX(), c1.getY(), c2.getX(), c2.getY());
                }
            }
        }
        g2.setStroke(origStroke);
    }

    @Override
    public void showHighlight(FixpTransform outOfPlaceTransform, AbstractLayerDrawing  ald, ERaster raster) {
        if (eobj == null || !eobj.isLinked()) {
            return;
        }
        // highlight NodeInst
        NodeInst ni = (NodeInst) eobj;

        // switch colors if specified
        assert color == null;

        // draw nodeInst outline
        Poly niPoly = getNodeInstOutline(ni);
        boolean niOpened = (niPoly.getStyle() == Poly.Type.OPENED);
        drawOutlineFromPoints(outOfPlaceTransform, ald, raster, niPoly.getPoints(), 0, 0, niOpened, false);
    }

    /**
     * highlight objects that are electrically connected to this object
     * unless specified not to. HighlightConnected is set to false by addNetwork when
     * it figures out what's connected and adds them manually. Because they are added
     * in addNetwork, we shouldn't try and add connected objects here.
     * @param g2
     * @param wnd
     */
    @Override
    public void showHighlightsConnected(FixpTransform outOfPlaceTransform, AbstractLayerDrawing  ald, ERaster raster) {
        if (!isValid() || !(eobj instanceof PortInst) || !highlightConnected) return;
        PortInst originalPi = (PortInst)eobj;
        Netlist netlist = cell.getNetlist();
        if (netlist == null) return;
        NodeInst ni = originalPi.getNodeInst();
        NodeInst originalNI = ni;
        PortProto pp = originalPi.getPortProto();
        if (ni.isIconOfParent())
        {
            // find export in parent
            Export equiv = (Export)cell.findPortProto(pp.getName());
            if (equiv != null)
            {
                originalPi = equiv.getOriginalPort();
                ni = originalPi.getNodeInst();
                pp = originalPi.getPortProto();
            }
        }
        Set<Network> networks = new HashSet<Network>();
        networks = NetworkTool.getNetworksOnPort(originalPi, netlist, networks);

        Set<Geometric> markObj = new HashSet<Geometric>();
        for(Iterator<ArcInst> it = cell.getArcs(); it.hasNext(); )
        {
            ArcInst ai = it.next();
            Name arcName = ai.getNameKey();
            for (int i=0; i<arcName.busWidth(); i++) {
                if (networks.contains(netlist.getNetwork(ai, i))) {
                    markObj.add(ai);
                    markObj.add(ai.getHeadPortInst().getNodeInst());
                    markObj.add(ai.getTailPortInst().getNodeInst());
                    break;
                }
            }
        }

        for (Iterator<Nodable> it = netlist.getNodables(); it.hasNext(); ) {
            Nodable no = it.next();
            NodeInst oNi = no.getNodeInst();
            if (oNi == originalNI) continue;
            if (markObj.contains(ni)) continue;

            boolean highlightNo = false;
            for(Iterator<PortProto> eIt = no.getProto().getPorts(); eIt.hasNext(); )
            {
                PortProto oPp = eIt.next();
                Name opName = oPp.getNameKey();
                for (int j=0; j<opName.busWidth(); j++) {
                    if (networks.contains(netlist.getNetwork(no, oPp, j))) {
                        highlightNo = true;
                        break;
                    }
                }
                if (highlightNo) break;
            }
            if (highlightNo)
                markObj.add(oNi);
        }
        //System.out.println("Search took "+com.sun.electric.database.text.TextUtils.getElapsedTime(System.currentTimeMillis()-start));

        // draw lines along all of the arcs on the network
        for(Iterator<ArcInst> it = cell.getArcs(); it.hasNext(); )
        {
            ArcInst ai = it.next();
            if (!markObj.contains(ai)) continue;
            EPoint c1 = ai.getHeadLocation();
            EPoint c2 = ai.getTailLocation();
            if (outOfPlaceTransform != null) {
                c1 = (EPoint)outOfPlaceTransform.transform(c1, null);
                c2 = (EPoint)outOfPlaceTransform.transform(c2, null);
            }
            ald.drawLine((int)c1.getGridX(), (int)c1.getGridY(), (int)c2.getGridX(), (int)c2.getGridY(), 2, raster);
        }

        // draw dots in all connected nodes
        for (Iterator<NodeInst> it = cell.getNodes(); it.hasNext(); )
        {
            NodeInst oNi = it.next();
            if (!markObj.contains(oNi)) continue;

            EPoint c = EPoint.snap(oNi.getTrueCenter());
            if (outOfPlaceTransform != null) {
                c = (EPoint)outOfPlaceTransform.transform(c, null);
            }
            ald.drawOval((int)c.getGridX(), (int)c.getGridY(), 4, raster);

            // connect the center dots to the input arcs
            Point2D nodeCenter = oNi.getTrueCenter();
            for(Iterator<Connection> pIt = oNi.getConnections(); pIt.hasNext(); )
            {
                Connection con = pIt.next();
                ArcInst ai = con.getArc();
                if (!markObj.contains(ai)) continue;
                EPoint arcEnd = con.getLocation();
                if (arcEnd.getGridX() != nodeCenter.getX() || arcEnd.getY() != nodeCenter.getY())
                {
                    EPoint c1 = arcEnd;
                    EPoint c2 = EPoint.snap(nodeCenter);
                    if (outOfPlaceTransform != null) {
                        c1 = (EPoint)outOfPlaceTransform.transform(c1, null);
                        c2 = (EPoint)outOfPlaceTransform.transform(c2, null);
                    }
                    ald.drawLine((int)c1.getGridX(), (int)c1.getGridY(), (int)c2.getGridX(), (int)c2.getGridY(), 0, raster);
                }
            }
        }
    }

    @Override
	void getHighlightedEObjs(Highlighter highlighter, List<Geometric> list, boolean wantNodes, boolean wantArcs)
    {
        getHighlightedEObjsInternal(getGeometric(), list, wantNodes, wantArcs);
    }

    @Override
    void getHighlightedNodes(Highlighter highlighter, Set<NodeInst> set)
    {
        getHighlightedNodesInternal(getGeometric(), set);
    }

    @Override
    void getHighlightedArcs(Highlighter highlighter, Set<ArcInst> set)
    {
        getHighlightedArcsInternal(getGeometric(), set);
    }

    @Override
    void getHighlightedNetworks(Set<Network> nets, Netlist netlist)
    {
        ElectricObject eObj = eobj;
        if (eObj instanceof NodeInst)
        {
            NodeInst ni = (NodeInst)eObj;
            if (ni.getNumPortInsts() == 1)
            {
                PortInst pi = ni.getOnlyPortInst();
                if (pi != null) eObj = pi;
            }
        }
        if (eObj instanceof PortInst)
        {
            PortInst pi = (PortInst)eObj;
            nets = NetworkTool.getNetworksOnPort(pi, netlist, nets);
        } else if (eObj instanceof ArcInst)
        {
            ArcInst ai = (ArcInst)eObj;
            int width = netlist.getBusWidth(ai);
            for(int i=0; i<width; i++)
            {
                Network net = netlist.getNetwork((ArcInst)eObj, i);
                if (net != null) nets.add(net);
            }
        }
    }

    @Override
    Rectangle2D getHighlightedArea(EditWindow wnd)
    {
        ElectricObject eObj = eobj;
        if (eObj instanceof PortInst) eObj = ((PortInst)eObj).getNodeInst();
        if (eObj instanceof Geometric)
        {
            Geometric geom = (Geometric)eObj;
            return geom.getBounds();
        }
        return null;
    }

    @Override
    public Geometric getGeometric()
    {
    	Geometric retVal = null;
        if (eobj instanceof PortInst) retVal = ((PortInst)eobj).getNodeInst(); else
        	if (eobj instanceof Geometric) retVal = (Geometric)eobj;
        return retVal;
    }

    @Override
    Highlight overHighlighted(EditWindow wnd, int x, int y, Highlighter highlighter, boolean change)
    {
        Point2D slop = wnd.deltaScreenToDatabase(Highlighter.EXACTSELECTDISTANCE*2, Highlighter.EXACTSELECTDISTANCE*2);
        double directHitDist = slop.getX();
        Point2D start = wnd.screenToDatabase(x, y);
        Rectangle2D searchArea = new Rectangle2D.Double(start.getX(), start.getY(), 0, 0);

        ElectricObject eobj = this.eobj;
        if (eobj instanceof PortInst) eobj = ((PortInst)eobj).getNodeInst();
        if (eobj instanceof Geometric)
        {
        	boolean specialSelect = ToolBar.isSelectSpecial();
            List<Highlight> gotAll = Highlighter.checkOutObject((Geometric)eobj, true, false, specialSelect,
                    searchArea, wnd, directHitDist, false, wnd.getGraphicsPreferences().isShowTempNames());
            if (gotAll.isEmpty()) return null;
            boolean found = false;
            Highlight result = this;
            for(Highlight got : gotAll)
            {
	            if (!(got instanceof HighlightEOBJ))
	                System.out.println("Error?");
	            ElectricObject hObj = got.getElectricObject();
	            ElectricObject hReal = hObj;
	            if (hReal instanceof PortInst) hReal = ((PortInst)hReal).getNodeInst();
	            for(Highlight alreadyDone : highlighter.getHighlights())
	            {
	                if (!(alreadyDone instanceof HighlightEOBJ)) continue;
	                HighlightEOBJ alreadyHighlighted = (HighlightEOBJ)alreadyDone;
	                ElectricObject aHObj = alreadyHighlighted.getElectricObject();
	                ElectricObject aHReal = aHObj;
	                if (aHReal instanceof PortInst) aHReal = ((PortInst)aHReal).getNodeInst();
	                if (hReal == aHReal)
	                {
	                    // found it: adjust the port/point
	                	found = true;
	                    if (hObj != aHObj || alreadyHighlighted.point != ((HighlightEOBJ)got).point)
	                    {
	                    	if (change)
	                    	{
	                            Highlight updated = highlighter.setPoint(alreadyHighlighted, got.getElectricObject(), ((HighlightEOBJ)got).point);
	                            if (alreadyHighlighted == this) result = updated;
	                    	} else
	                    	{
	                    		result = new HighlightEOBJ(alreadyHighlighted, got.getElectricObject(), ((HighlightEOBJ)got).point);
	                    	}
	                    }
	                    break;
	                }
	            }
	            if (found) break;
            }
            return result;
        }
        return null;
    }

    @Override
    public String getInfo()
    {
        String description = "";
        ElectricObject realObj = eobj;

        if (realObj instanceof PortInst)
            realObj = ((PortInst)realObj).getNodeInst();
        if (realObj instanceof NodeInst)
        {
            NodeInst ni = (NodeInst)realObj;
            description = "Node " + ni.describe(true);
        } else if (realObj instanceof ArcInst)
        {
            ArcInst ai = (ArcInst)eobj;
            description = "Arc " + ai.describe(true);
        }
        return description;
    }
}


/**
 *  A Highlight which calls the user's attention to an ElectricObject which happens to be a piece of text.
 */
class HighlightText extends Highlight
{
	/** The highlighted object. */								protected final ElectricObject eobj;
	/** The highlighted variable. */							protected final Variable.Key varKey;

    public HighlightText(ElectricObject e, Cell c, Variable.Key key)
    {
        super(c, null, false);
        this.eobj = e;
        this.varKey = key;
        Class<?> cls = null;
        if (key == NodeInst.NODE_NAME || key == NodeInst.NODE_PROTO)
            cls = NodeInst.class;
        else if (key == ArcInst.ARC_NAME)
            cls = ArcInst.class;
        else if (key == Export.EXPORT_NAME)
            cls = Export.class;
        else if (key == null)
            throw new NullPointerException();
        if (cls != null && !cls.isInstance(e))
            throw new IllegalArgumentException(key + " in " + e);
    }

    @Override
    void internalDescribe(StringBuffer desc)
    {
        if (varKey != null)
        {
        	if (varKey == NodeInst.NODE_NAME)
        	{
	            desc.append("name: ");
	            desc.append(((NodeInst)eobj).getName());
        	} else if (varKey == NodeInst.NODE_PROTO)
        	{
	            desc.append("instance: ");
	            desc.append(((NodeInst)eobj).getProto().getName());
        	} else if (varKey == ArcInst.ARC_NAME)
        	{
	            desc.append("name: ");
	            desc.append(((ArcInst)eobj).getName());
        	} else if (varKey == Export.EXPORT_NAME)
        	{
	            desc.append("export: ");
	            desc.append(((Export)eobj).getName());
        	} else
        	{
	            desc.append("var: ");
	            desc.append(eobj.getParameterOrVariable(varKey).describe(-1));
        	}
        }
    }

    @Override
    public ElectricObject getElectricObject() { return eobj; }

    // creating so HighlightText is not a public class
    @Override
    public boolean isHighlightText() { return true; }

    @Override
    public Variable.Key getVarKey() { return varKey; }

    @Override
    public boolean isValid()
    {
        if (!super.isValid()) return false;
        if (eobj == null || varKey == null) return false;
        if (!eobj.isLinked()) return false;

    	if (varKey == NodeInst.NODE_NAME ||
			varKey == ArcInst.ARC_NAME ||
			varKey == NodeInst.NODE_PROTO ||
			varKey == Export.EXPORT_NAME) return true;
    	return eobj.getParameterOrVariable(varKey) != null;
    }

    @Override
    public boolean sameThing(Highlight obj, boolean exact)
    {
        if (this == obj) return (true);

		// Consider already obj==null
        if (obj == null || getClass() != obj.getClass())
            return (false);

        HighlightText other = (HighlightText)obj;
        if (eobj != other.eobj) return false;
        if (cell != other.cell) return false;
        if (varKey != other.varKey) return false;
        return true;
    }

    @Override
    public void showInternalHighlight(EditWindow wnd, Graphics g, long highOffX, long highOffY,
                                      boolean onlyHighlight)
    {
        Graphics2D g2 = (Graphics2D)g;
        Point2D [] points = Highlighter.describeHighlightText(wnd, eobj, varKey);
        if (points == null) return;
        Point2D [] linePoints = new Point2D[2];
        for(int i=0; i<points.length; i += 2)
        {
            linePoints[0] = points[i];
            linePoints[1] = points[i+1];
            drawOutlineFromPoints(wnd, g, linePoints, highOffX, highOffY, false, false);
        }
        if (onlyHighlight)
        {
            // this is the only thing highlighted: show the attached object
            ElectricObject eObj = eobj;
            if (eObj != null && eObj instanceof Geometric)
            {
                Geometric geom = (Geometric)eObj;
                if (geom instanceof ArcInst || !((NodeInst)geom).isInvisiblePinWithText())
                {
                    Point2D objCtr = geom.getTrueCenter();
                    ScreenPoint c = wnd.databaseToScreen(objCtr);

                    TextDescriptor td = eobj.getTextDescriptor(varKey);
                    Point2D offset = new Point2D.Double(td.getXOff(), td.getYOff());
                    if (geom instanceof NodeInst)
                    {
                    	NodeInst ni = (NodeInst)geom;
                    	FixpTransform trans = ni.pureRotateOut();
                    	trans.transform(offset, offset);
                    }
                    double locX = objCtr.getX() + offset.getX();
                    double locY = objCtr.getY() + offset.getY();
                    Point2D txtAnchor = new Point2D.Double(locX, locY);
                    ScreenPoint a = wnd.databaseToScreen(txtAnchor);
                    long cX = a.getX(), cY = a.getY();
                    if (Math.abs(cX - c.getX()) > 4 || Math.abs(cY - c.getY()) > 4)
                    {
                        g.fillOval(c.getIntX()-4, c.getIntY()-4, 8, 8);
                        g2.setStroke(dottedLine);
                        drawLine(g, wnd, c.getX(), c.getY(), cX, cY);
                        g2.setStroke(solidLine);
                    }
                }
            }
        }
    }

    @Override
    public Geometric getGeometric()
    {
        if (DisplayedText.objectMovesWithText(eobj, varKey, User.isMoveNodeWithExport()))
        {
            if (eobj instanceof Export) return ((Export)eobj).getOriginalPort().getNodeInst();
            if (eobj instanceof Geometric) return (Geometric)eobj;
        }
        return null;
    }

    @Override
    void getHighlightedEObjs(Highlighter highlighter, List<Geometric> list, boolean wantNodes, boolean wantArcs)
    {
        getHighlightedEObjsInternal(getGeometric(), list, wantNodes, wantArcs);
    }

    @Override
    void getHighlightedNodes(Highlighter highlighter, Set<NodeInst> set)
    {
        getHighlightedNodesInternal(getGeometric(), set);
    }

    @Override
    void getHighlightedArcs(Highlighter highlighter, Set<ArcInst> set)
    {
        getHighlightedArcsInternal(getGeometric(), set);
    }

    @Override
    void getHighlightedNetworks(Set<Network> nets, Netlist netlist)
    {
        if (/*varKey == null &&*/ eobj instanceof Export)
        {
            Export pp = (Export)eobj;
            int width = netlist.getBusWidth(pp);
            for(int i=0; i<width; i++)
            {
                Network net = netlist.getNetwork(pp, i);
                if (net != null) nets.add(net);
            }
        }
    }

    DisplayedText makeDisplayedText()
    {
    	if (varKey != null)
    		return new DisplayedText(eobj, varKey);
    	return null;
    }

    @Override
    void getHighlightedText(List<DisplayedText> list, boolean unique, List<Highlight> getHighlights)
    {
    	DisplayedText dt = makeDisplayedText();
    	if (dt == null) return;
        if (list.contains(dt)) return;

        // if this text is on a selected object, don't include the text
        if (unique)
        {
            ElectricObject onObj = null;
            if (varKey != null)
            {
                if (eobj instanceof Export)
                {
                    onObj = ((Export)eobj).getOriginalPort().getNodeInst();
                } else if (eobj instanceof PortInst)
                {
                    onObj = ((PortInst)eobj).getNodeInst();
                } else if (eobj instanceof Geometric)
                {
                    onObj = eobj;
                }
            }

            // now see if the object is in the list
            if (eobj != null)
            {
                boolean found = false;
                for(Highlight oH : getHighlights)
                {
                    if (!(oH instanceof HighlightEOBJ)) continue;
                    ElectricObject fobj = ((HighlightEOBJ)oH).eobj;
                    if (fobj instanceof PortInst) fobj = ((PortInst)fobj).getNodeInst();
                    if (fobj == onObj) { found = true;   break; }
                }
                if (found) return;
            }
        }

        // add this text
        list.add(dt);
    }

    @Override
    Rectangle2D getHighlightedArea(EditWindow wnd)
    {
        if (wnd != null)
        {
            Poly poly = eobj.computeTextPoly(wnd, varKey);
            if (poly != null) return poly.getBounds2D();
        }
        return null;
    }

    @Override
    Highlight overHighlighted(EditWindow wnd, int x, int y, Highlighter highlighter, boolean change)
    {
        Point2D start = wnd.screenToDatabase(x, y);
        Poly poly = eobj.computeTextPoly(wnd, varKey);
        if (poly != null)
            if (poly.isInside(start)) return this;
        return null;
    }

    @Override
    public String describe()
    {
        String description = "Unknown";
        if (varKey != null && eobj != null)
        {
        	if (varKey == NodeInst.NODE_NAME)
        	{
        		description = "Node name for " + ((NodeInst)eobj).describe(true);
        	} else if (varKey == ArcInst.ARC_NAME)
        	{
        		description = "Arc name for " + ((ArcInst)eobj).describe(true);
        	} else if (varKey == Export.EXPORT_NAME)
        	{
        		description = "Export '" + ((Export)eobj).getName() + "'";
        	} else if (varKey == NodeInst.NODE_PROTO)
        	{
        		description = "Cell instance name " + ((NodeInst)eobj).describe(true);
        	} else
        	{
        		Variable var = eobj.getParameterOrVariable(varKey);
        		if (var != null) description = eobj.getFullDescription(var);
        	}
        }
        return description;
    }

    @Override
    public String getInfo()
    {
        return "Text: " + describe();
    }
}
