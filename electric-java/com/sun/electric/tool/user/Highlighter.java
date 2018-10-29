/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Highlighter.java
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
package com.sun.electric.tool.user;

import com.sun.electric.database.change.DatabaseChangeEvent;
import com.sun.electric.database.change.DatabaseChangeListener;
import com.sun.electric.database.geometry.EPoint;
import com.sun.electric.database.geometry.Poly;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.Export;
import com.sun.electric.database.network.Netlist;
import com.sun.electric.database.network.Network;
import com.sun.electric.database.prototype.NodeProto;
import com.sun.electric.database.prototype.PortProto;
import com.sun.electric.database.text.TextUtils;
import com.sun.electric.database.topology.ArcInst;
import com.sun.electric.database.topology.Connection;
import com.sun.electric.database.topology.Geometric;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.topology.PortInst;
import com.sun.electric.database.topology.RTBounds;
import com.sun.electric.database.topology.RTNode;
import com.sun.electric.database.variable.DisplayedText;
import com.sun.electric.database.variable.ElectricObject;
import com.sun.electric.database.variable.TextDescriptor;
import com.sun.electric.database.variable.Variable;
import com.sun.electric.technology.ArcProto;
import com.sun.electric.technology.Layer;
import com.sun.electric.technology.PrimitiveNode;
import com.sun.electric.technology.PrimitivePort;
import com.sun.electric.technology.Technology;
import com.sun.electric.technology.technologies.Generic;
import com.sun.electric.technology.technologies.Schematics;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.routing.Router;
import com.sun.electric.tool.user.ui.EditWindow;
import com.sun.electric.tool.user.ui.LayerVisibility;
import com.sun.electric.tool.user.ui.WindowContent;
import com.sun.electric.tool.user.ui.WindowFrame;
import com.sun.electric.tool.user.waveform.WaveformWindow;
import com.sun.electric.util.math.DBMath;
import com.sun.electric.util.math.FixpRectangle;
import com.sun.electric.util.math.FixpTransform;
import com.sun.electric.util.math.GenMath;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Class for per-window highlighting information.
 */
public class Highlighter implements DatabaseChangeListener
{
    private static Highlighter currentHighlighter = null;

    /** Screen offset for display of highlighting. */			private long highOffX, highOffY;
    /** the highlighted objects. */								private final List<Highlight> highlightList = new ArrayList<Highlight>();
    /** the highlighted objects that can't be shown in ERaster. */private final List<Highlight> difficultHighlightList = new ArrayList<Highlight>();
    /** the stack of highlights. */								private final List<List<Highlight>> highlightStack = new ArrayList<List<Highlight>>();
    /** true if highlights have changed recently */             private boolean changed;
    /** last object selected before last clear() */             private Highlight lastHighlightListEndObj;
    /** what was the last level of "showNetwork" */             private int showNetworkLevel;
	/** the type of highlighter */                              private int type;

    /** List of HighlightListeners */                           private static Set<HighlightListener> highlightListeners = new HashSet<HighlightListener>();

    /** the selection highlighter type */       				public static final int SELECT_HIGHLIGHTER = 0;
    /** the mouse over highlighter type */      				public static final int MOUSEOVER_HIGHLIGHTER = 1;
    /** the "measurement" highlighter type */   				public static final int RULER_HIGHLIGHTER = 2;

    /** the max pixel distance that's acceptable selection */	public static final int EXACTSELECTDISTANCE = 5;

    /**
     * Create a new Highlighter object
     * @param type
     */
    public Highlighter(int type, WindowFrame wf) {
        highOffX = highOffY = 0;
        changed = false;
        UserInterfaceMain.addDatabaseChangeListener(this);
        if (currentHighlighter == null) currentHighlighter = this;
        lastHighlightListEndObj = null;
        showNetworkLevel = 0;
		this.type = type;
    }

    void setChanged(boolean c) { changed = c; }

    /**
     * Destructor
     */
    public void delete()
    {
        UserInterfaceMain.removeDatabaseChangeListener(this);
    }

    /**
	 * Method to add an ElectricObject to the list of highlighted objects.
	 * @param eobj the ElectricObject to add to the list of highlighted objects.
	 * @param cell the Cell in which the ElectricObject resides.
	 * @return the newly created Highlight object.
	 */
    public Highlight addElectricObject(ElectricObject eobj, Cell cell)
    {
        Highlight h1 = new HighlightEOBJ(eobj, cell, true, -1);
		addHighlight(h1);
		return h1;
    }

    public Highlight addElectricObject(ElectricObject eobj, boolean isError, Cell cell)
    {
        Highlight h1 = new HighlightEOBJ(eobj, cell, true, -1, isError);
		addHighlight(h1);
		return h1;
    }

	/**
	 * Method to add an ElectricObject to the list of highlighted objects.
	 * @param eobj the ElectricObject to add to the list of highlighted objects.
	 * @param cell the Cell in which the ElectricObject resides.
	 * @return the newly created Highlight object.
	 */
	public Highlight addElectricObject(ElectricObject eobj, Cell cell, Color col)
	{
		Highlight h1 = new HighlightEOBJ(eobj, cell, true, -1, col);
		addHighlight(h1);
		return h1;
	}

	/**
	 * Method to add an ElectricObject to the list of highlighted objects.
	 * @param eobj the ElectricObject to add to the list of highlighted objects.
	 * @param cell the Cell in which the ElectricObject resides.
	 * @param highlightConnected if true, highlight all objects that are in some way connected
	 * to this object.  If false, do not. This is used by addNetwork to prevent extra
	 * things from being highlighted later that are not connected to the network.
	 * @return the newly created Highlight object.
	 */
	public Highlight addElectricObject(ElectricObject eobj, Cell cell, boolean highlightConnected, Color col)
	{
		Highlight h1 = new HighlightEOBJ(eobj, cell, highlightConnected, -1, col);
		addHighlight(h1);
		return h1;
	}

	/**
	 * Method to replace a given HighlightEOBJ by new HighlighEOBJ with specified eObj and point.
     * @param oldHighlight given Highlight
	 * @param eobj new ElectricObject.
	 * @param point new point.
	 * @return the newly created Highlight object.
	 */
    public Highlight setPoint(Highlight oldHighlight, ElectricObject eobj, int point) {
        if (!highlightList.contains(oldHighlight) || !(oldHighlight instanceof HighlightEOBJ)) {
            return oldHighlight;
        }
        remove(oldHighlight);
        HighlightEOBJ h = (HighlightEOBJ)oldHighlight;
        Highlight h1 = new HighlightEOBJ(h, eobj, point);
        highlightList.add(h1);
        return h1;
    }

	/**
	 * Method to add a text selection to the list of highlighted objects.
	 * @param cell the Cell in which this area resides.
	 * @param varKey the Variable.Key associated with the text (text is then a visual of that variable).
	 * @return the newly created Highlight object.
	 */
	public Highlight addText(ElectricObject eobj, Cell cell, Variable.Key varKey)
	{
		HighlightText h1 = new HighlightText(eobj, cell, varKey);
		addHighlight(h1);
		return h1;
	}

	/**
	 * Method to add a message display to the list of highlighted objects.
	 * @param cell the Cell in which this area resides.
	 * @param message the String to display.
	 * @param loc the location of the string (in database units).
	 * @return the newly created Highlight object.
	 */
	public Highlight addMessage(Cell cell, String message, Point2D loc)
	{
		Highlight h1 = new Highlight.Message(cell, message, loc, 0, null);
		addHighlight(h1);
		return h1;
	}

	/**
	 * Method to add a message display to the list of highlighted objects.
	 * @param cell the Cell in which this area resides.
	 * @param message the String to display.
	 * @param loc the location of the string (in database units).
	 * @param corner 0=lowerLeft, 1=upperLeft, 2=upperRight, 3=lowerRight.
     * @param backgroundColor the text background color.
	 * @return the newly created Highlight object.
	 */
	public Highlight addMessage(Cell cell, String message, Point2D loc, int corner, Color backgroundColor)
	{
		Highlight h1 = new Highlight.Message(cell, message, loc, corner, backgroundColor);
		addHighlight(h1);
		return h1;
	}

	/**
	 * Method to add an area to the list of highlighted objects.
	 * @param area the Rectangular area to add to the list of highlighted objects.
	 * @param cell the Cell in which this area resides.
	 * @return the newly created Highlight object.
	 */
	public Highlight addArea(Rectangle2D area, Cell cell)
	{
        Highlight h1 = new HighlightArea(cell, null, area);
        addHighlight(h1);
		return h1;
	}

	/**
	 * Method to add an area to the list of highlighted objects.
	 * @param area the Rectangular area to add to the list of highlighted objects.
     * @param col color of the area
	 * @param cell the Cell in which this area resides.
	 * @return the newly created Highlight object.
	 */
	public Highlight addArea(Rectangle2D area, Color col, Cell cell)
	{
        Highlight h1 = new HighlightArea(cell, col, area);
        addHighlight(h1);
		return h1;
	}

	/**
	 * Method to generic Object.
	 * @param obj object to add.
	 * @param cell the Cell in which this object resides.
	 * @return the newly created Highlight object.
	 */
	public Highlight addObject(Object obj, Cell cell)
	{
        Highlight h1 = new HighlightObject(cell, obj);
        addHighlight(h1);
		return h1;
	}

    /**
	 * Method to add a line to the list of highlighted objects.
	 * @param start the start point of the line to add to the list of highlighted objects.
	 * @param end the end point of the line to add to the list of highlighted objects.
	 * @param cell the Cell in which this line resides.
	 * @return the newly created Highlight object.
	 */
	public Highlight addLine(Point2D start, Point2D end, Cell cell)
	{
        Highlight h1 = new HighlightLine(cell, start, end, null, false, null, false, null);
        addHighlight(h1);
		return h1;
	}

    /**
	 * Method to add a line to the list of highlighted objects in a specific WindowFrame.
	 * @param start the start point of the line to add to the list of highlighted objects.
	 * @param end the end point of the line to add to the list of highlighted objects.
	 * @param cell the Cell in which this line resides.
	 * @param wf the WindowFrame in which this line resides.
	 * @return the newly created Highlight object.
	 */
	public Highlight addLine(Point2D start, Point2D end, Cell cell, WindowFrame wf)
	{
        Highlight h1 = new HighlightLine(cell, start, end, null, false, null, false, wf);
        addHighlight(h1);
		return h1;
	}

    /**
	 * Method to add a line to the list of highlighted objects.
	 * @param start the start point of the line to add to the list of highlighted objects.
	 * @param end the end point of the line to add to the list of highlighted objects.
	 * @param cell the Cell in which this line resides.
	 * @param thick true for a thick line.
	 * @return the newly created Highlight object.
	 */
	public Highlight addLine(Point2D start, Point2D end, Cell cell, boolean thick, boolean isError)
	{
        Highlight h1 = new HighlightLine(cell, start, end, null, thick, null, isError, null);
        addHighlight(h1);
		return h1;
	}

    /**
	 * Method to add a line to the list of highlighted objects.
	 * @param start the start point of the line to add to the list of highlighted objects.
	 * @param end the end point of the line to add to the list of highlighted objects.
	 * @param cell the Cell in which this line resides.
	 * @param thick true for a thick line.
     * @param col color of the line
     * @param isError line can pulsate
	 * @return the newly created Highlight object.
	 */
	public Highlight addLine(Point2D start, Point2D end, Cell cell, boolean thick, Color col, boolean isError)
	{
        Highlight h1 = new HighlightLine(cell, start, end, null, thick, col, isError, null);
        addHighlight(h1);
		return h1;
	}

    /**
	 * Method to add a line to the list of highlighted objects.
	 * @param start the start point of the line to add to the list of highlighted objects.
	 * @param end the end point of the line to add to the list of highlighted objects.
	 * @param cell the Cell in which this line resides.
	 * @return the newly created Highlight object.
	 */
	public Highlight addThickLine(Point2D start, Point2D end, Cell cell, boolean isError)
	{
        Highlight h1 = new HighlightLine(cell, start, end, null, true, null, isError, null);
        addHighlight(h1);
		return h1;
	}

    /**
     * Method to add a Poly to the list of highlighted objects
     * @param poly the poly to add
     * @param cell the cell in which to display the poly
     * @param color the color to draw the poly with (if null, uses default)
     * @return the newly created highlight object
     */
    public Highlight addPoly(Poly poly, Cell cell, Color color)
    {
        Highlight h1 = new HighlightPoly(cell, poly, color);
        addHighlight(h1);
        return h1;
    }

    /**
	 * Method to add a network to the list of highlighted objects.
	 * Many arcs may be highlighted as a result.
	 * @param net the network to highlight.
	 * @param cell the Cell in which this line resides.
	 */
	public void addNetwork(Network net, Cell cell)
	{
		Netlist netlist = cell.getNetlist();
		if (netlist == null)
		{
			System.out.println("Sorry, a deadlock aborted highlighting (network information unavailable).  Please try again");
			return;
		}
        Set<Network> nets = new HashSet<Network>();
        nets.add(net);
        List<Highlight> highlights = NetworkHighlighter.getHighlights(cell, netlist, nets, 0, 0);
        for (Highlight h : highlights) {
            addHighlight(h);
        }
	}

    /**
     * This is the show network command. It is similar to addNetwork, however
     * each time it is used without first clearing
     * the highlighter, it shows connections to the network another level down
     * in the hierarchy.
     * @param cell the cell in which to create the highlights
     */
    public void showNetworks(Cell cell)
    {
    	// find out what is selected
		Netlist netlist = cell.getNetlist();
		if (netlist == null)
		{
			System.out.println("Sorry, a deadlock aborted netlist display (network information unavailable).  Please try again");
			return;
		}
		Set<Network> nets = getHighlightedNetworks();
		if (nets.size() == 0)
		{
			// no nets selected.  If a cell instance is selected, use all nets on it that are wired
			List<NodeInst> nodes = getHighlightedNodes();
			if (nodes.size() == 1)
			{
				NodeInst ni = nodes.get(0);
				for(Iterator<Connection> it = ni.getConnections(); it.hasNext(); )
				{
					Connection con = it.next();
					Network net = netlist.getNetwork(con.getPortInst());
					if (net == null)
						System.out.println("Error: no net associated to connection '" + con.toString() + "' in NodeInst '" + ni.getName());
					else
						nets.add(net);
				}
			}
		}

		if (nets.size() == 1)
		{
			// see if this cell has all nodes (and no arcs, so no connectivity information)
			boolean hasTopology = false;
			for(Iterator<ArcInst> it = cell.getArcs(); it.hasNext(); )
			{
				ArcInst ai = it.next();
				if (ai.getProto().getTechnology() == Generic.tech()) continue;
				hasTopology = true;
				break;
			}
			if (!hasTopology)
			{
				Network net = nets.iterator().next();
				NodeInst ni = net.getNodes().next();
				String nodeName = ni.getName();

				boolean flashHighlight = false;
				HighlightConnectivity qc = new HighlightConnectivity(cell);
				String[] topology = qc.getTopology();
				if (topology != null)
				{
					boolean first = true;
					for(int i=0; i<topology.length; i++)
					{
						String[] nodeNames = topology[i].split("/");
						for(int j=0; j<nodeNames.length; j++)
						{
							if (nodeNames[j].charAt(0) != 'N') continue;
							int sPos = nodeNames[j].indexOf('[');
							String listedName = sPos < 0 ? nodeNames[j].substring(1) : nodeNames[j].substring(1, sPos);
							if (listedName.equals(nodeName))
							{
								// found the node in the quick topology, show all geometry
								if (first) clear();
								first = false;
								for(int k=0; k<nodeNames.length; k++)
								{
									String element = nodeNames[k];
									Rectangle2D altRect = null;
									int squarePos = element.indexOf('[');
									if (squarePos >= 0)
									{
										int eSquarePos = element.indexOf(']', squarePos+1);
										if (eSquarePos >= 0)
										{
											String[] coords = element.substring(squarePos+1, eSquarePos).split(";");
											if (coords.length == 4)
											{
												double x = TextUtils.atof(coords[0]);
												double y = TextUtils.atof(coords[1]);
												double w = TextUtils.atof(coords[2]);
												double h = TextUtils.atof(coords[3]);
												altRect = new Rectangle2D.Double(x, y, w, h);
											}
										}
									}
									if (altRect != null)
									{
										addArea(altRect, cell);
										Point2D p1 = new Point2D.Double(altRect.getMinX(), altRect.getMinY());
										Point2D p2 = new Point2D.Double(altRect.getMinX(), altRect.getMaxY());
										Point2D p3 = new Point2D.Double(altRect.getMaxX(), altRect.getMaxY());
										Point2D p4 = new Point2D.Double(altRect.getMaxX(), altRect.getMinY());
										addLine(p1, p2, cell, false, flashHighlight);
										addLine(p2, p3, cell, false, flashHighlight);
										addLine(p3, p4, cell, false, flashHighlight);
										addLine(p4, p1, cell, false, flashHighlight);
									} else
									{
										if (element.charAt(0) == 'N')
										{
											String niName = element.substring(1);
											NodeInst niObj = cell.findNode(niName);
											addElectricObject(niObj, flashHighlight, cell);
										} else
										{
											String aiName = element.substring(1);
											ArcInst aiObj = cell.findArc(aiName);
											addElectricObject(aiObj, flashHighlight, cell);
										}
									}
								}
							}
						}
					}
					if (!first)
					{
						finished();
						return;
					}
				}
			}
		}

		// see if there are other cells being displayed that might be able to be cross-probed
		List<EditWindow> crossProbeWindows = new ArrayList<EditWindow>();
		for(Iterator<WindowFrame> it = WindowFrame.getWindows(); it.hasNext(); )
		{
			WindowFrame wf = it.next();
			WindowContent wc = wf.getContent();
			if (!(wc instanceof EditWindow)) continue;
			Cell otherCell = wc.getCell();
			if (otherCell == cell) continue;
			if (cell.getCellGroup().containsCell(otherCell))
				crossProbeWindows.add((EditWindow)wc);
		}

		int showNetworkLevel;
	    synchronized(this) {
            showNetworkLevel = this.showNetworkLevel;
        }
        if (showNetworkLevel == 0)
        {
            List<Network> sortedNets = new ArrayList<Network>(nets);
            Collections.sort(sortedNets, new TextUtils.NetworksByName());
            for (Network net : sortedNets) {
                System.out.println("Highlighting "+net);
            }
            clear();
            for(EditWindow wnd : crossProbeWindows) wnd.getHighlighter().clear();
        }
        int count = 0;
        List<Highlight> highlights = NetworkHighlighter.getHighlights(cell, netlist, nets,
            showNetworkLevel, showNetworkLevel);
        for (Highlight h : highlights) {
            addHighlight(h);
            count++;
        }

        for(EditWindow wnd : crossProbeWindows)
        {
        	Cell otherCell = wnd.getCell();
        	Highlighter otherHighlighter = wnd.getHighlighter();
        	Netlist otherNetlist = otherCell.getNetlist();
        	Set<Network> crossNets = new HashSet<Network>();
        	for(Network net : nets)
        	{
        		for(Iterator<Network> it = otherNetlist.getNetworks(); it.hasNext(); )
        		{
        			Network otherNet = it.next();
        			if (sameNetName(net, otherNet)) crossNets.add(otherNet);
        		}
        	}
	        List<Highlight> otherHighlights = NetworkHighlighter.getHighlights(otherCell, otherNetlist, crossNets,
	            showNetworkLevel, showNetworkLevel);
	        for (Highlight h : otherHighlights) otherHighlighter.addHighlight(h);
        }
        synchronized(this) {
            this.showNetworkLevel = showNetworkLevel+1;
        }
        for(EditWindow wnd : crossProbeWindows) wnd.getHighlighter().finished();
        if (count == 0) {
            System.out.println("Nothing more in hierarchy on network(s) to show");
        }
    }

    /**
     * Method to tell whether two networks share a name.
     * @param net1 the first Network to consider.
     * @param net2 the second Network to consider.
     * @return true if they share a common name.
     */
    private boolean sameNetName(Network net1, Network net2)
    {
    	for(Iterator<String> it1 = net1.getNames(); it1.hasNext(); )
    	{
    		String name1 = it1.next();
    		for(Iterator<String> it2 = net2.getNames(); it2.hasNext(); )
    		{
    			String name2 = it2.next();
    			if (name1.equalsIgnoreCase(name2)) return true;
    		}
    	}
    	return false;
    }

    /**
     * Add a Highlight
     */
    public synchronized void addHighlight(Highlight h) {
        if (h == null) return;
        highlightList.add(h);
        if (!h.showInRaster()) {
            difficultHighlightList.add(h);
        }
        changed = true;
    }

    /**
	 * Method to clear the list of all highlighted objects in
	 */
	public void clear()
	{
        clear(true);
    }

    private synchronized void clear(boolean resetLastHighlightListEndObj) {
        highOffX = highOffY = 0;
        showNetworkLevel = 0;

        if (highlightList.isEmpty()) return;

        // save last selected
        if (resetLastHighlightListEndObj)
            lastHighlightListEndObj = highlightList.get(highlightList.size()-1);
        // clear
        highlightList.clear();
        difficultHighlightList.clear();
        changed = true;
	}


    /**
	 * Method to indicate that changes to highlighting are finished.
	 * Call this after any change to highlighting.
	 */
	public void finished()
	{
        // only do something if highlights changed
        synchronized(this)
        {
            // check to see if any highlights are now invalid
            for (Highlight h : getHighlights())
            {
                if (!h.isValid())
                {
                    // remove
                    remove(h); // we can do this because iterator is iterating over copy
                    changed = true;
                }
            }
            if (!changed) return;
        }

		// see if arcs of a single type were selected
		boolean mixedArc = false;
		ArcProto foundArcProto = null;
		for(Highlight h : getHighlights())
		{
            if (h instanceof HighlightEOBJ)
			{
				ElectricObject eobj = ((HighlightEOBJ)h).eobj;
				if (eobj instanceof ArcInst)
				{
					ArcProto ap = ((ArcInst)eobj).getProto();
					if (foundArcProto == null)
					{
						foundArcProto = ap;
					} else
					{
						if (foundArcProto != ap) mixedArc = true;
					}
				}
			}
		}
        if (type == SELECT_HIGHLIGHTER)
		    if (foundArcProto != null && !mixedArc) User.getUserTool().setCurrentArcProto(foundArcProto);

        // notify all listeners that highlights have changed (changes committed).
        if (!SwingUtilities.isEventDispatchThread())
        {
            SwingUtilities.invokeLater(new Runnable()
            {
                @Override
                public void run() { fireHighlightChanged(); }
            });
        } else
        {
            fireHighlightChanged();
        }
	}

	/**
	 * Method to ensure that the highlighting is visible.
	 * If the highlighting is offscreen, flash an arrow towards it.
	 * If the highlighting is small, flash lines around it.
	 */
	public void ensureHighlightingSeen(WindowFrame wf)
	{
		// must be drawing in an edit window
	    if (wf == null || !(wf.getContent() instanceof EditWindow)) return;
		EditWindow wnd = (EditWindow)wf.getContent();

		// must have something highlighted
		Rectangle2D bounds = getHighlightedArea(wnd, false);
		if (bounds == null) return;

		// determine the area being highlighted
		double boundsArea = bounds.getWidth() * bounds.getHeight();
		Rectangle2D displayBounds = wnd.displayableBounds();
		double displayArea = displayBounds.getWidth() * displayBounds.getHeight();
		Highlight line1 = null, line2 = null, line3 = null, line4 = null;

		// if objects are offscreen, point the way
		if (bounds.getMinX() >= displayBounds.getMaxX() ||
			bounds.getMaxX() <= displayBounds.getMinX() ||
			bounds.getMinY() >= displayBounds.getMaxY() ||
			bounds.getMaxY() <= displayBounds.getMinY())
		{
			Point2D fromPt = new Point2D.Double(displayBounds.getCenterX(), displayBounds.getCenterY());
			Point2D toPt = new Point2D.Double(bounds.getCenterX(), bounds.getCenterY());
			GenMath.clipLine(fromPt, toPt, displayBounds.getMinX(), displayBounds.getMaxX(),
					displayBounds.getMinY(), displayBounds.getMaxY());
			if (fromPt.getX() != displayBounds.getCenterX() || fromPt.getY() != displayBounds.getCenterY())
			{
				// clipLine may swap points: swap them back
				Point2D swap = fromPt;
				fromPt = toPt;
				toPt = swap;
			}
			line1 = addLine(fromPt, toPt, wnd.getCell(), wf);
			int angle = GenMath.figureAngle(fromPt, toPt);
			double headLength = fromPt.distance(toPt) / 10;
			double xLeft = toPt.getX() - headLength * DBMath.cos(angle+150);
			double yLeft = toPt.getY() - headLength * DBMath.sin(angle+150);
			double xRight = toPt.getX() - headLength * DBMath.cos(angle-150);
			double yRight = toPt.getY() - headLength * DBMath.sin(angle-150);
			line2 = addLine(new Point2D.Double(xLeft, yLeft), toPt, wnd.getCell(), wf);
			line3 = addLine(new Point2D.Double(xRight, yRight), toPt, wnd.getCell(), wf);
		} else
		{
			// if displayed objects are very small, point them out
			if (boundsArea * 500 <  displayArea)
			{
				if (bounds.getMinX() > displayBounds.getMinX() && bounds.getMinY() > displayBounds.getMinY())
					line1 = addLine(new Point2D.Double(displayBounds.getMinX(), displayBounds.getMinY()),
						new Point2D.Double(bounds.getMinX(), bounds.getMinY()), wnd.getCell(), wf);

				if (bounds.getMinX() > displayBounds.getMinX() && bounds.getMaxY() < displayBounds.getMaxY())
					line2 = addLine(new Point2D.Double(displayBounds.getMinX(), displayBounds.getMaxY()),
						new Point2D.Double(bounds.getMinX(), bounds.getMaxY()), wnd.getCell(), wf);

				if (bounds.getMaxX() < displayBounds.getMaxX() && bounds.getMinY() > displayBounds.getMinY())
					line3 = addLine(new Point2D.Double(displayBounds.getMaxX(), displayBounds.getMinY()),
						new Point2D.Double(bounds.getMaxX(), bounds.getMinY()), wnd.getCell(), wf);

				if (bounds.getMaxX() < displayBounds.getMaxX() && bounds.getMaxY() < displayBounds.getMaxY())
					line4 = addLine(new Point2D.Double(displayBounds.getMaxX(), displayBounds.getMaxY()),
						new Point2D.Double(bounds.getMaxX(), bounds.getMaxY()), wnd.getCell(), wf);
			}
		}

		// if there was temporary identification, queue a timer to turn it off
		if (line1 != null || line2 != null || line3 != null || line4 != null)
		{
			Timer timer = new Timer(500, new FlashActionListener(wf, this, line1, line2, line3, line4));
			timer.setRepeats(false);
			timer.start();
		}
	}

	/**
	 * Class to temporarily "flash" a selection that is otherwise hard to see.
	 */
	private static class FlashActionListener implements ActionListener
	{
		private WindowFrame wf;
		private Highlighter hl;
		private Highlight line1, line2, line3, line4;

		FlashActionListener(WindowFrame wf, Highlighter hl, Highlight line1, Highlight line2, Highlight line3, Highlight line4)
		{
			this.wf = wf;
			this.hl = hl;
			this.line1 = line1;
			this.line2 = line2;
			this.line3 = line3;
			this.line4 = line4;
		}
        @Override
	    public void actionPerformed(ActionEvent evt)
		{
			if (line1 != null) hl.remove(line1);
			if (line2 != null) hl.remove(line2);
			if (line3 != null) hl.remove(line3);
			if (line4 != null) hl.remove(line4);
			hl.finished();
			wf.getContent().repaint();
		}
	}

    /**
     * Get the last object that was selected. If underCursor is not null,
     * if any of the Highlights in underCursor are currently highlighted, then
     * the last thing highlighted will be the last thing selected before the last
     * clear(). This is to be able to properly cycle through objects under the cursor.
     * @param underCursor a list of Highlights underCursor.
     * @return the last object that was selected
     */
    private synchronized Highlight getLastSelected(List<Highlight> underCursor)
    {
        List<Highlight> currentHighlights = getHighlights();	// not that this is a copy

        // check underCursor list
        for (Highlight h : underCursor) {
            for (Highlight curHigh : currentHighlights) {
                if (h.sameThing(curHigh, false)) {
                    return lastHighlightListEndObj;
                }
            }
        }

        if (currentHighlights.size() > 0)
            return currentHighlights.get(currentHighlights.size()-1);
        return lastHighlightListEndObj;
    }

    /**
     * Inherits the last selected object from the specified highlighter.
     * This is a hack, don't use it.
     * @param highlighter
     */
    public synchronized void copyState(Highlighter highlighter) {
        clear();
        lastHighlightListEndObj = highlighter.lastHighlightListEndObj;
        for (Highlight h : highlighter.getHighlights()) {
            Highlight copy = (Highlight)h.clone();
            addHighlight(copy);
        }

        // don't inherit offset, messes up mouse over highlighter
        //highOffX = highlighter.highOffX;
        //highOffY = highlighter.highOffY;
    }

    /**
     * Shows highlights for the current EditWindow
     * @param wnd
     * @param g
     */
    public void showHighlights(EditWindow wnd, Graphics g, boolean onlyDifficult)
    {
        int num = getNumHighlights();
        boolean onlyHighlight = num == 1;
        long highOffX, highOffY;
        synchronized(this) {
            highOffX = this.highOffX;
            highOffY = this.highOffY;
        }

        List<Highlight> list = onlyDifficult && num > 1 ? getDifficultHighlights() : getHighlights();

        Stroke stroke = Highlight.solidLine;

        for (Highlight h : list)
        {
            // only show highlights for the current cell
            if (h.getCell() == wnd.getCell())
            {
                boolean setConnected = User.isHighlightConnectedObjects();
                Color color;
                if (type == SELECT_HIGHLIGHTER)
                {
                	// normal highlighter
                    color = new Color(User.getColor(User.ColorPrefType.HIGHLIGHT));
                } else if (type == MOUSEOVER_HIGHLIGHTER)
                {
                	// mouse-over pre-highlighter
                    color = new Color(User.getColor(User.ColorPrefType.MOUSEOVER_HIGHLIGHT));
                    setConnected = false;
                } else
                {
                	// measurement highlighter
                    color = new Color(User.getColor(User.ColorPrefType.MEASUREMENT));
                }
//				if (h.isError || !errorsOnly)
					h.showHighlight(wnd, g, highOffX, highOffY, onlyHighlight, color, stroke);
                    if (onlyHighlight && setConnected && !onlyDifficult) {
                        h.showHighlightsConnected((Graphics2D)g, wnd);
                    }
            }
        }
    }

    /** Add a Highlight listener */
    public static synchronized void addHighlightListener(HighlightListener l)
    {
        highlightListeners.add(l);
    }

    /** Remove a Highlight listener */
    public static synchronized void removeHighlightListener(HighlightListener l)
    {
        highlightListeners.remove(l);
    }

    /** Notify listeners that highlights have changed */
    private void fireHighlightChanged()
    {
    	if (type == SELECT_HIGHLIGHTER)
    	{
	        List<HighlightListener> listenersCopy;
	        synchronized(this) {
	            listenersCopy = new ArrayList<HighlightListener>(highlightListeners);
	        }
	        for (HighlightListener l : listenersCopy) {
	            l.highlightChanged(this);
	        }
    	}
        synchronized(this) {
            changed = false;
        }
    }

    /** Notify listeners that the current Highlighter has changed */
    private synchronized void fireHighlighterLostFocus(Highlighter highlighterGainedFocus)
    {
    	if (type == SELECT_HIGHLIGHTER)
    	{
	        List<HighlightListener> listenersCopy;
	        synchronized(this) {
	            listenersCopy = new ArrayList<HighlightListener>(highlightListeners);
	        }
	        for (HighlightListener l : listenersCopy) {
	            l.highlighterLostFocus(highlighterGainedFocus);
	        }
    	}
    }

    /**
     * Called when the Highlighter owner has gained focus, and the
     * current highlighter switches to this.
     */
    public void gainedFocus() {
        Highlighter oldHighlighter = null;
        synchronized(currentHighlighter) {
            oldHighlighter = currentHighlighter;
            currentHighlighter = this;
        }
        // fire focus changed on old highlighter
        if ((oldHighlighter != null) && (oldHighlighter != this))
            oldHighlighter.fireHighlighterLostFocus(this);
    }

    /**
	 * Method to push the current highlight list onto a stack.
	 */
	public synchronized void pushHighlight()
	{
		// make a copy of the highlighted list
		List<Highlight> pushable = new ArrayList<Highlight>();
		for(Highlight h : highlightList)
			pushable.add(h);
		highlightStack.add(pushable);
	}

    /**
	 * Method to pop the current highlight list from the stack.
	 */
	public synchronized void popHighlight()
	{
		int stackSize = highlightStack.size();
		if (stackSize <= 0)
		{
			System.out.println("There is no highlighting saved on the highlight stack");
			return;
		}

		// get the stacked highlight
		List<Highlight> popable = highlightStack.get(stackSize-1);
		highlightStack.remove(stackSize-1);
		showHighlights(popable);
	}
	
	public void showHighlights(List<Highlight> popable)
	{
		// validate each highlight as it is added
		clear();
		for(Highlight h : popable)
		{
            Cell cell = h.getCell();
            if (h instanceof HighlightEOBJ)
			{
                HighlightEOBJ hh = (HighlightEOBJ)h;
                ElectricObject eobj = hh.eobj;
				if (cell.objInCell(eobj))
				{
            		HighlightEOBJ newH = new HighlightEOBJ(hh, eobj, hh.point);
                    addHighlight(newH);
				}
			} else if (h instanceof HighlightText)
			{
                HighlightText hh = (HighlightText)h;
                ElectricObject eobj = hh.eobj;
				if (cell.objInCell(eobj))
				{
					addText(eobj, cell, hh.varKey);
				}
			} else if (h instanceof HighlightArea)
			{
                HighlightArea hh = (HighlightArea)h;
				addArea(hh.bounds, cell);
			} else if (h instanceof HighlightLine)
			{
                HighlightLine hh = (HighlightLine)h;
                if (hh.thickLine)
                    addThickLine(hh.start, hh.end, cell, hh.isError);
                else
				    addLine(hh.start, hh.end, cell);
			} else if (h instanceof Highlight.Message) //type == Highlight.Type.MESSAGE)
            {
				Highlight.Message hh = (Highlight.Message)h;
				addMessage(cell, hh.msg, hh.loc);
			}
		}
		finished();
	}

    /**
     * Removes a Highlight object from the current set of highlights.
     * @param h the Highlight to remove
     */
    public synchronized void remove(Highlight h)
    {
        highlightList.remove(h);
        if (!h.showInRaster()) {
            difficultHighlightList.remove(h);
        }
    }

    /**
	 * Method to return the number of highlighted objects.
	 * @return the number of highlighted objects.
	 */
	public synchronized int getNumHighlights() { return highlightList.size(); }

    /**
	 * Method to return a list that is a copy of the list of current highlights.
	 * @return an list of highlights
	 */
	public synchronized List<Highlight> getHighlights() {
        List<Highlight> highlightsCopy = new ArrayList<Highlight>(highlightList);
        return highlightsCopy;
    }

    /**
	 * Method to return a list that is a copy of the list of current highlights.
	 * @return an list of highlights
	 */
	public synchronized List<Highlight> getDifficultHighlights() {
        return new ArrayList<Highlight>(difficultHighlightList);
    }

    /**
	 * Method to load a list of Highlights into the highlighting.
	 * @param newHighlights a List of Highlight objects.
	 */
	public synchronized void setHighlightListGeneral(List<Highlight> newHighlights)
	{
        clear();
		for(Highlight h : newHighlights)
		{
			addHighlight(h);
		}
	}

    /**
	 * Method to load a list of Highlights into the highlighting.
	 * @param newHighlights a List of Highlight objects.
	 */
	public synchronized void setHighlightList(List<Highlight> newHighlights)
	{
        clear();
		for(Highlight h : newHighlights)
		{
            addHighlight(h);
		}
	}

    /**
	 * Method to return a List of all highlighted Geometrics.
	 * @param wantNodes true if NodeInsts should be included in the list.
	 * @param wantArcs true if ArcInsts should be included in the list.
	 * @return a list with the highlighted Geometrics.
	 */
	public List<Geometric> getHighlightedEObjs(boolean wantNodes, boolean wantArcs)
	{
		// now place the objects in the list
		List<Geometric> highlightedGeoms = new ArrayList<Geometric>();
		for(Highlight h : getHighlights())
		{
            h.getHighlightedEObjs(this, highlightedGeoms, wantNodes, wantArcs);
		}
		return highlightedGeoms;
	}

    /**
	 * Method to return a List of all highlighted NodeInsts.
	 * @return a list with the highlighted NodeInsts.
	 */
	public List<NodeInst> getHighlightedNodes()
	{
		// now place the objects in the list
		Set<NodeInst> highlightedNodes = new HashSet<NodeInst>();
		for(Highlight h : getHighlights())
		{
            h.getHighlightedNodes(this, highlightedNodes);
		}
		return new ArrayList<NodeInst>(highlightedNodes);
	}

    /**
	 * Method to return a List of all highlighted ArcInsts.
	 * @return a list with the highlighted ArcInsts.
	 */
	public List<ArcInst> getHighlightedArcs()
	{
		// now place the objects in the list
		Set<ArcInst> highlightedArcs = new HashSet<ArcInst>();
		for(Highlight h : getHighlights())
		{
            h.getHighlightedArcs(this, highlightedArcs);
		}
		return new ArrayList<ArcInst>(highlightedArcs);
	}

    /**
	 * Method to return a set of the currently selected networks.
	 * @return a set of the currently selected networks.
	 * If there are no selected networks, the list is empty.
	 */
	public Set<Network> getHighlightedNetworks()
	{
		WindowFrame wf = WindowFrame.getCurrentWindowFrame();
		if (wf.getContent() instanceof WaveformWindow)
		{
			WaveformWindow ww = (WaveformWindow)wf.getContent();
			return ww.getHighlightedNetworks();
		}
		Set<Network> nets = new HashSet<Network>();
		Cell cell = WindowFrame.getCurrentCell();
		if (cell != null)
		{
			Netlist netlist = cell.getNetlist();
			if (netlist == null)
			{
				String msg = "Selected networks are not ready";
				System.out.println(msg);
				ActivityLogger.logMessage(msg);
				return nets;
			}
			for(Highlight h : getHighlights())
			{
                h.getHighlightedNetworks(nets, netlist);
			}
		}
		return nets;
	}

    /**
	 * Method to return a List of all highlighted text.
	 * @param unique true to request that the text objects be unique,
	 * and not attached to another object that is highlighted.
	 * For example, if a node and an export on that node are selected,
	 * the export text will not be included if "unique" is true.
	 * @return a list with the Highlight objects that point to text.
	 */
	public List<DisplayedText> getHighlightedText(boolean unique)
	{
		// now place the objects in the list
		List<DisplayedText> highlightedText = new ArrayList<DisplayedText>();
		for(Highlight h : getHighlights())
		{
            h.getHighlightedText(highlightedText, unique, getHighlights());
		}
		return highlightedText;
	}

    /**
	 * Method to return the bounds of the highlighted objects.
	 * @param wnd the window in which to get bounds.
	 * @return the bounds of the highlighted objects (null if nothing is highlighted).
	 */
	public Rectangle2D getHighlightedArea(EditWindow wnd)
	{
		return getHighlightedArea(wnd, false);
	}

    /**
	 * Method to return the bounds of the highlighted objects.
	 * @param wnd the window in which to get bounds.
	 * @param onlyEObjects true to get the bounds of only the Electric Objects that are highlighted.
	 * @return the bounds of the highlighted objects (null if nothing is highlighted).
	 */
	public Rectangle2D getHighlightedArea(EditWindow wnd, boolean onlyEObjects)
	{
		// initially no area
		Rectangle2D bounds = null;

		// look at all highlighted objects
		for(Highlight h : getHighlights())
		{
			if (onlyEObjects)
			{
				if (!(h instanceof HighlightEOBJ)) continue;
			}

			// find the bounds of this highlight
			Rectangle2D highBounds = h.getHighlightedArea(wnd);

			// combine this highlight's bounds with the overall one
			if (highBounds != null)
			{
				if (bounds == null)
				{
					bounds = new Rectangle2D.Double();
					bounds.setRect(highBounds);
				} else
				{
					Rectangle2D.union(bounds, highBounds, bounds);
				}
			}
		}

		// return the overall bounds
		return bounds;
	}

    /**
	 * Method to return the only highlight that encompasses an object in Cell cell.
	 * If there is not one highlighted object, an error is issued.
	 * @return the highlight that selects an object (null if error).
	 */
	public Highlight getOneHighlight()
	{
		if (getNumHighlights() == 0)
		{
			System.out.println("Must select an object first");
			return null;
		}
		Highlight h = null;
		for(Highlight theH : getHighlights())
		{
            if (theH.getElectricObject() != null) return theH;
		}
		if (h == null)
		{
			System.out.println("Must select an object first");
			return null;
		}
		return h;
	}

    /**
	 * Method to return the only highlighted object.
	 * If there is not one highlighted object, an error is issued.
	 * @return the highlighted object (null if error).
	 */
	public ElectricObject getOneElectricObject(Class<?> type)
	{
		Highlight high = getOneHighlight();
		if (high == null) return null;
		if (!(high instanceof HighlightEOBJ))
		{
            System.out.println("Must first select an object");
            return null;
        }
        ElectricObject eobj = high.getElectricObject();
		if (type == NodeInst.class)
		{
			if (eobj instanceof PortInst) eobj = ((PortInst)eobj).getNodeInst();
		}
		if (!type.isInstance(eobj))
		{

            System.out.println("Wrong type of object is selected");
            System.out.println(" (Wanted "+getClassName(type)+" but got "+getClassName(eobj.getClass())+")");
            return null;
		}
		return eobj;
	}

	private String getClassName(Class<?> type)
	{
		if (type == NodeInst.class) return "Node";
		if (type == ArcInst.class) return "Arc";
		return type.toString();
	}

	/**
	 * Method to set a screen offset for the display of highlighting.
	 * @param offX the X offset (in pixels) of the highlighting.
	 * @param offY the Y offset (in pixels) of the highlighting.
	 */
	public synchronized void setHighlightOffset(long offX, long offY)
	{
		highOffX = offX;
		highOffY = offY;
	}

    /**
     * Method to return the screen offset for the display of highlighting
     * @return a Point2D containing the x and y offset.
     */
    public synchronized Point2D getHighlightOffset()
    {
        return new Point2D.Double(highOffX, highOffY);
    }

    /**
	 * Method to add everything in an area to the selection.
	 * @param wnd the window being examined.
	 * @param minSelX the low X coordinate of the area in database units.
	 * @param maxSelX the high X coordinate of the area in database units.
	 * @param minSelY the low Y coordinate of the area in database units.
	 * @param maxSelY the high Y coordinate of the area in database units.
	 * @param invertSelection is true to invert the selection (remove what is already highlighted and add what is new).
	 * @param findSpecial is true to find hard-to-select objects.
	 */
	public void selectArea(EditWindow wnd, double minSelX, double maxSelX, double minSelY, double maxSelY,
		boolean invertSelection, boolean findSpecial)
	{
		Rectangle2D searchArea = new Rectangle2D.Double(minSelX, minSelY, maxSelX - minSelX, maxSelY - minSelY);
		List<Highlight> underCursor = findAllInArea(this, wnd.getCell(), false, false, false, findSpecial, true, searchArea, wnd);
		if (invertSelection)
		{
			for(Highlight newHigh : underCursor)
			{
				boolean found = false;
                for (Highlight oldHigh : getHighlights()) {
                    if (newHigh.sameThing(oldHigh, false)) {
                        remove(oldHigh);
                        found = true;
                        break;
                    }
                }
				if (found) continue;
				addHighlight(newHigh);
			}
		} else
		{
			setHighlightList(underCursor);
		}
	}

    /**
	 * Method to tell whether a point is over this Highlight.
	 * @param wnd the window being examined.
	 * @param x the X screen coordinate of the point.
	 * @param y the Y screen coordinate of the point.
	 * @param change true to update the highlight; false to leave things alone.
	 * @return Highlight if the point is over this Highlight.
	 */
	public Highlight overHighlighted(EditWindow wnd, int x, int y, boolean change)
	{
		for(Highlight h : getHighlights())
		{
            Highlight updated = h.overHighlighted(wnd, x, y, this, change);
            if (updated != null)
                return updated;
		}
		return null;
	}

    /**
	 * Method to describe an object/variable-key pair.
	 * @param wnd the EditWindow in which the object/variable-key is displayed.
	 * @param eObj the object.
	 * @param varKey the variable-key.
	 * @param bounds gets filled with the bounds of the text on the screen (in object space).
	 * @return the style of the text (null on error).
	 */
	private static Poly.Type getHighlightTextStyleBounds(EditWindow wnd, ElectricObject eObj, Variable.Key varKey, Rectangle2D bounds)
	{
        if (eObj == null) return null; // in case of massive delete -> Swing accesses objects that are currently being modified
        Poly poly = eObj.computeTextPoly(wnd, varKey);
        if (poly == null) return null;
        bounds.setRect(poly.getBounds2D());
        Poly.Type style = poly.getStyle();
		if (style != Poly.Type.TEXTCENT && style != Poly.Type.TEXTBOX)
		{
            style = Poly.rotateType(style, eObj);
			TextDescriptor td = poly.getTextDescriptor();
			if (td != null)
			{
				int rotation = td.getRotation().getIndex();
				if (rotation != 0)
				{
					int angle = style.getTextAngle();
					style = Poly.Type.getTextTypeFromAngle((angle+900*rotation) % 3600);
				}
			}
		}
        if (style == Poly.Type.TEXTBOX && (eObj instanceof Geometric))
        {
            bounds.setRect(((Geometric)eObj).getBounds());
        }
        return style;
	}

    /**
	 * Method to describe an object/variable-key pair as a set of points to draw.
	 * @param wnd the EditWindow in which the object/variable-key is displayed.
	 * @param eObj the object.
	 * @param varKey the variable-key.
	 * @return the set of points to draw, two points per line.
	 * Returns null on error.
	 */
	public static Point2D [] describeHighlightText(EditWindow wnd, ElectricObject eObj, Variable.Key varKey)
	{
		Rectangle2D bounds = new Rectangle2D.Double();
		Poly.Type style = null;
//        if (!Job.acquireExamineLock(false)) return null;
        try
        {
    		style = getHighlightTextStyleBounds(wnd, eObj, varKey, bounds);
//            Job.releaseExamineLock();
        } catch (Error e)
        {
//            Job.releaseExamineLock();
            throw e;
        }
		if (style == null) return null;
        Point2D[] points = null;
        if (style == Poly.Type.TEXTCENT)
        {
            points = new Point2D.Double[4];
            points[0] = new Point2D.Double(bounds.getMinX(), bounds.getMinY());
            points[1] = new Point2D.Double(bounds.getMaxX(), bounds.getMaxY());
            points[2] = new Point2D.Double(bounds.getMinX(), bounds.getMaxY());
            points[3] = new Point2D.Double(bounds.getMaxX(), bounds.getMinY());
        }
        else if (style == Poly.Type.TEXTBOT)
        {
            points = new Point2D.Double[6];
            points[0] = new Point2D.Double(bounds.getMinX(), bounds.getMaxY());
            points[1] = new Point2D.Double(bounds.getMinX(), bounds.getMinY());
            points[2] = new Point2D.Double(bounds.getMinX(), bounds.getMinY());
            points[3] = new Point2D.Double(bounds.getMaxX(), bounds.getMinY());
            points[4] = new Point2D.Double(bounds.getMaxX(), bounds.getMinY());
            points[5] = new Point2D.Double(bounds.getMaxX(), bounds.getMaxY());
        }
        else if (style == Poly.Type.TEXTTOP)
        {
            points = new Point2D.Double[6];
            points[0] = new Point2D.Double(bounds.getMinX(), bounds.getMinY());
            points[1] = new Point2D.Double(bounds.getMinX(), bounds.getMaxY());
            points[2] = new Point2D.Double(bounds.getMinX(), bounds.getMaxY());
            points[3] = new Point2D.Double(bounds.getMaxX(), bounds.getMaxY());
            points[4] = new Point2D.Double(bounds.getMaxX(), bounds.getMaxY());
            points[5] = new Point2D.Double(bounds.getMaxX(), bounds.getMinY());
        }
        else if (style == Poly.Type.TEXTLEFT)
        {
            points = new Point2D.Double[6];
            points[0] = new Point2D.Double(bounds.getMaxX(), bounds.getMinY());
            points[1] = new Point2D.Double(bounds.getMinX(), bounds.getMinY());
            points[2] = new Point2D.Double(bounds.getMinX(), bounds.getMinY());
            points[3] = new Point2D.Double(bounds.getMinX(), bounds.getMaxY());
            points[4] = new Point2D.Double(bounds.getMinX(), bounds.getMaxY());
            points[5] = new Point2D.Double(bounds.getMaxX(), bounds.getMaxY());
        }
        else if (style == Poly.Type.TEXTRIGHT)
        {
            points = new Point2D.Double[6];
            points[0] = new Point2D.Double(bounds.getMinX(), bounds.getMinY());
            points[1] = new Point2D.Double(bounds.getMaxX(), bounds.getMinY());
            points[2] = new Point2D.Double(bounds.getMaxX(), bounds.getMinY());
            points[3] = new Point2D.Double(bounds.getMaxX(), bounds.getMaxY());
            points[4] = new Point2D.Double(bounds.getMaxX(), bounds.getMaxY());
            points[5] = new Point2D.Double(bounds.getMinX(), bounds.getMaxY());
        }
        else if (style == Poly.Type.TEXTTOPLEFT)
        {
            points = new Point2D.Double[4];
            points[0] = new Point2D.Double(bounds.getMaxX(), bounds.getMaxY());
            points[1] = new Point2D.Double(bounds.getMinX(), bounds.getMaxY());
            points[2] = new Point2D.Double(bounds.getMinX(), bounds.getMaxY());
            points[3] = new Point2D.Double(bounds.getMinX(), bounds.getMinY());
        }
        else if (style == Poly.Type.TEXTBOTLEFT)
        {
            points = new Point2D.Double[4];
            points[0] = new Point2D.Double(bounds.getMinX(), bounds.getMaxY());
            points[1] = new Point2D.Double(bounds.getMinX(), bounds.getMinY());
            points[2] = new Point2D.Double(bounds.getMinX(), bounds.getMinY());
            points[3] = new Point2D.Double(bounds.getMaxX(), bounds.getMinY());
        }
        else if (style == Poly.Type.TEXTTOPRIGHT)
        {
            points = new Point2D.Double[4];
            points[0] = new Point2D.Double(bounds.getMinX(), bounds.getMaxY());
            points[1] = new Point2D.Double(bounds.getMaxX(), bounds.getMaxY());
            points[2] = new Point2D.Double(bounds.getMaxX(), bounds.getMaxY());
            points[3] = new Point2D.Double(bounds.getMaxX(), bounds.getMinY());
        }
        else if (style == Poly.Type.TEXTBOTRIGHT)
        {
            points = new Point2D.Double[4];
            points[0] = new Point2D.Double(bounds.getMinX(), bounds.getMinY());
            points[1] = new Point2D.Double(bounds.getMaxX(), bounds.getMinY());
            points[2] = new Point2D.Double(bounds.getMaxX(), bounds.getMinY());
            points[3] = new Point2D.Double(bounds.getMaxX(), bounds.getMaxY());
        }
        else if (style == Poly.Type.TEXTBOX)
        {
            points = new Point2D.Double[12];
            double lX = bounds.getMinX();
            double hX = bounds.getMaxX();
            double lY = bounds.getMinY();
            double hY = bounds.getMaxY();
            points[0] = new Point2D.Double(lX, lY);
            points[1] = new Point2D.Double(hX, hY);
            points[2] = new Point2D.Double(lX, hY);
            points[3] = new Point2D.Double(hX, lY);
            double shrinkX = (hX - lX) / 5;
            double shrinkY = (hY - lY) / 5;
            points[4] = new Point2D.Double(lX+shrinkX, lY);
            points[5] = new Point2D.Double(hX-shrinkX, lY);
            points[6] = new Point2D.Double(lX+shrinkX, hY);
            points[7] = new Point2D.Double(hX-shrinkX, hY);
            points[8] = new Point2D.Double(lX, lY+shrinkY);
            points[9] = new Point2D.Double(lX, hY-shrinkY);
            points[10] = new Point2D.Double(hX, lY+shrinkY);
            points[11] = new Point2D.Double(hX, hY-shrinkY);
        }
        return points;
	}

    /**
	 * Method to handle a click in a window and select the appropriate objects.
	 * @param pt the coordinates of the click (in database units).
	 * @param wnd the window being examined.
	 * @param exclusively true if the currently selected object must remain selected.
	 * This happens during "outline edit" when the node doesn't change, just the point on it.
	 * @param another true to find another object under the point (when there are multiple ones).
	 * @param invert true to invert selection (add if not selected, remove if already selected).
	 * @param findPort true to also show the closest port on a selected node.
	 * @param findPoint true to also show the closest point on a selected outline node.
	 * @param findSpecial true to select hard-to-find objects.
	 * @param findText true to select text objects.
	 * @param change true to update this Highlighter with the result; false to leave Highlighter alone (just return next selection).
	 * The name of an unexpanded cell instance is always hard-to-select.
	 * Other objects are set this way by the user (although the cell-center is usually set this way).
	 */
	public Highlight findObject(Point2D pt, EditWindow wnd, boolean exclusively,
		boolean another, boolean invert, boolean findPort, boolean findPoint, boolean findSpecial, boolean findText, boolean change)
	{
		// search the relevant objects in the circuit
		Cell cell = wnd.getCell();
        Rectangle2D bounds = new Rectangle2D.Double(pt.getX(), pt.getY(), 0, 0);
		List<Highlight> underCursor = findAllInArea(this, cell, exclusively, findPort, findPoint, findSpecial, findText, bounds, wnd);
        Highlight found = null;

		// if nothing under the cursor, stop now
		if (underCursor.size() == 0)
		{
			if (change && !invert)
			{
				clear();
				finished();
			}
			return found;
		}

        // get last selected object. Next selected object should be related
        Highlight lastSelected = getLastSelected(underCursor);

        if (lastSelected != null) {
            // sort under cursor by relevance to lastSelected. first object is most relevant.
            List<Highlight> newUnderCursor = new ArrayList<Highlight>();
            while (!underCursor.isEmpty()) {
                Highlight h = getSimiliarHighlight(underCursor, lastSelected);
                newUnderCursor.add(h);
                underCursor.remove(h);
            }
            underCursor = newUnderCursor;
        }

		// multiple objects under the cursor
		if (underCursor.size() > 1 && another)
		{
            // I don't think you should loop and get getHighlight() every time
            List<Highlight> highlightList = getHighlights();
			for(int j=0; j<getNumHighlights(); j++)
			{
				Highlight oldHigh = highlightList.get(j);
				for(int i=0; i<underCursor.size(); i++)
				{
					if (oldHigh.sameThing(underCursor.get(i), true))
					{
						// found the same thing: loop
						if (change)
						{
							if (invert) remove(oldHigh); else
								clear(false);
						}
						if (i < underCursor.size()-1)
						{
							found = (underCursor.get(i+1));
						} else
						{
							found = (underCursor.get(0));
						}
						if (change)
						{
	                        addHighlight(found);
							finished();
						}
						return found;
					}
				}
			}
		}

		// just use the first in the list
        found = underCursor.get(0);
        if (change)
        {
			if (invert)
			{
	            List<Highlight> highlightList = getHighlights();
	            for (Highlight h : highlightList)
				{
					if (found.sameThing(h, false))
					{
						remove(h);
						finished();
						return found;
					}
				}
			} else
			{
				clear();
			}
	        addHighlight(found);
	        finished();
        }
		return found;
	}

	/**
	 * Method to search a Cell for all objects at a point.
	 * @param cell the cell to search.
	 * @param exclusively true if the currently selected object must remain selected.
	 * This happens during "outline edit" when the node doesn't change, just the point on it.
	 * @param findPort true to also show the closest port on a selected node.
	 * @param findPoint true to also show the closest point on a selected outline node.
	 * @param findSpecial true to select hard-to-find objects.
	 * @param findText true to select text objects.
	 * The name of an unexpanded cell instance is always hard-to-select.
	 * Other objects are set this way by the user (although the cell-center is usually set this way).
	 * @param bounds the area of the search (in database units).
	 * @param wnd the window being examined (null to ignore window scaling).
	 * @return a list of Highlight objects.
	 * The list is ordered by importance, so the default action is to select the first entry.
	 */
	public static List<Highlight> findAllInArea(Highlighter highlighter, Cell cell, boolean exclusively, boolean findPort,
		 boolean findPoint, boolean findSpecial, boolean findText, Rectangle2D bounds, EditWindow wnd)
	{
		// make a list of things under the cursor
		List<Highlight> list = new ArrayList<Highlight>();
//        if (!Job.acquireExamineLock(false)) return list;

        try
        {
    		// this is the distance from an object that is necessary for a "direct hit"
    		double directHitDist = Double.MIN_VALUE;
    		if (wnd != null)
    		{
    			Point2D extra = wnd.deltaScreenToDatabase(EXACTSELECTDISTANCE, EXACTSELECTDISTANCE);
    			directHitDist = Math.abs(extra.getX()); // + 0.4;
    		}

    		// look for text if a window was given
            if (findText && wnd != null)
            {
            	findTextNow(cell, wnd, directHitDist, bounds, findSpecial, list);
            }

    		boolean areaMustEnclose = User.isDraggingMustEncloseObjects();
            boolean showTempNames = false;
            if (exclusively)
            {
                // special case: only review what is already highlighted
                showTempNames = wnd.getGraphicsPreferences().isShowTempNames();
                for(Highlight h : highlighter.getHighlights())
                {
                    if (!(h instanceof HighlightEOBJ)) continue;
                    ElectricObject eobj = h.getElectricObject();
                    if (eobj instanceof PortInst) eobj = ((PortInst)eobj).getNodeInst();
                    if (eobj instanceof NodeInst)
                    {
                        List<Highlight> found = checkOutObject((Geometric)eobj, findPort, findPoint, findSpecial,
                                bounds, wnd, Double.MAX_VALUE, areaMustEnclose, showTempNames);
                        for(Highlight h2 : found) list.add(h2);
                    }
                }
//                Job.releaseExamineLock();
                return list;
            }

            // determine proper area to search
            Rectangle2D searchArea = new Rectangle2D.Double(bounds.getMinX() - directHitDist,
                bounds.getMinY() - directHitDist, bounds.getWidth()+directHitDist*2, bounds.getHeight()+directHitDist*2);

            // now do 3 phases of examination: cells, arcs, then primitive nodes
            for(int phase=0; phase<3; phase++)
            {
                // examine everything in the area
                for(Iterator<Geometric> it = cell.searchIterator(searchArea); it.hasNext(); )
                {
                    Geometric geom = it.next();
                    switch (phase)
                    {
                        case 0:			// check primitive nodes
                            if (!(geom instanceof NodeInst)) break;
                            if (((NodeInst)geom).isCellInstance()) break;
                            List<Highlight> found = checkOutObject(geom, findPort, findPoint, findSpecial,
                                    bounds, wnd, directHitDist, areaMustEnclose, showTempNames);
                            for(Highlight h2 : found) list.add(h2);
                            break;
                        case 1:			// check Cell instances
                            if (!findSpecial && !User.isEasySelectionOfCellInstances()) break; // ignore cells if requested
                            if (!(geom instanceof NodeInst)) break;
                            if (!((NodeInst)geom).isCellInstance()) break;
                            found = checkOutObject(geom, findPort, findPoint, findSpecial,
                                    bounds, wnd, directHitDist, areaMustEnclose, showTempNames);
                            for(Highlight h2 : found) list.add(h2);
                            break;
                        case 2:			// check arcs
                            if (!(geom instanceof ArcInst)) break;
                            found = checkOutObject(geom, findPort, findPoint, findSpecial,
                                    bounds, wnd, directHitDist, areaMustEnclose, showTempNames);
                            for(Highlight h2 : found) list.add(h2);
                            break;
                    }
                }
            }
//            Job.releaseExamineLock();
        } catch (Error e) {
//            Job.releaseExamineLock();
            throw e;
        }
        return list;
	}

	/**
	 * Class to define an R-Tree leaf node for cell text.
	 */
	public static class TextHighlightBound implements RTBounds
	{
		private final FixpRectangle bound;
		private final ElectricObject obj;
		private final Variable.Key key;

		TextHighlightBound(Rectangle2D bound, ElectricObject obj, Variable.Key key)
		{
			this.bound = FixpRectangle.from(bound);
			this.obj = obj;
			this.key = key;
		}

        @Override
		public FixpRectangle getBounds() { return bound; }

		public ElectricObject getElectricObject() { return obj; }

		public Variable.Key getKey() { return key; }

        @Override
		public String toString() { return "TextBound"; }
	}

	/**
	 * Method to locate text in an area, using the window's cache of text locations.
	 * @param cell the Cell to examine.
	 * @param wnd the EditWindow that the cell is displayed in.
	 * @param directHitDist the distance to consider a direct hit.
	 * @param bounds the area to search (all text in this bound will be returned in "list").
	 * @param findSpecial true to find "hard-to-select" text.
	 * @param list the place to add selected text.
	 */
	private static void findTextNow(Cell cell, EditWindow wnd, double directHitDist, Rectangle2D bounds, boolean findSpecial, List<Highlight> list)
	{
		// get the window's cache of text locations
        LayerVisibility lv = wnd.getLayerVisibility();
        GraphicsPreferences gp = wnd.getGraphicsPreferences();
		RTNode<TextHighlightBound> rtn = wnd.getTextInCell();
		if (rtn == null)
		{
			// must rebuild the RTree of text in this cell
			rtn = RTNode.makeTopLevel();

			// create temporary Rectangle
			Rectangle2D textBounds = new Rectangle2D.Double();

			// start by examining all text on this Cell
            Poly [] polys = cell.getAllText(findSpecial, wnd);
            if (polys != null)
            {
                for(int i=0; i<polys.length; i++)
                {
                    Poly poly = polys[i];
                    if (poly == null) continue;
                    if (poly.setExactTextBounds(wnd, cell)) continue;

                    // save text area in cache
                    rtn = RTNode.linkGeom(null, rtn, new TextHighlightBound(poly.getBounds2D(), cell, poly.getDisplayedText().getVariableKey()));
                }
            }

	        // next examine all text on nodes in the cell
	        for(Iterator<NodeInst> it = cell.getNodes(); it.hasNext(); )
	        {
	            NodeInst ni = it.next();
	            if (ni == null)
	            {
	            	if (Job.getDebug())
	            		System.out.println("Something is wrong in Highlighter:findAllInArea");
	                continue;
	            }

	    		// check out node text: first see if cell name text is selectable
        		if (ni.isCellInstance() && !ni.isExpanded() && findSpecial)
        		{
            		Poly.Type style = getHighlightTextStyleBounds(wnd, ni, NodeInst.NODE_PROTO, textBounds);
            		if (style != null)
            		{
	                    // save text area in cache
	                    rtn = RTNode.linkGeom(null, rtn, new TextHighlightBound(textBounds, ni, NodeInst.NODE_PROTO));
            		}
        		}

        		// now see if node is named
        		if (ni.isUsernamed() || (gp.isShowTempNames() && ni.isLinked()))
        		{
                    TextDescriptor td = ni.getTextDescriptor(NodeInst.NODE_NAME);
                    if (td.getDisplay() == TextDescriptor.Display.SHOWN)
                    {
	            		Poly.Type style = getHighlightTextStyleBounds(wnd, ni, NodeInst.NODE_NAME, textBounds);
	            		if (style != null)
	            		{
		                    // save text area in cache
		                    rtn = RTNode.linkGeom(null, rtn, new TextHighlightBound(textBounds, ni, NodeInst.NODE_NAME));
	            		}
                    }
        		}

        		// look at all variables on the node
        		for(Iterator<Variable> vIt = ni.getParametersAndVariables(); vIt.hasNext(); )
            	{
            		Variable var = vIt.next();
            		if (!var.isDisplay()) continue;
            		Poly.Type style = getHighlightTextStyleBounds(wnd, ni, var.getKey(), textBounds);
            		if (style != null)
            		{
	                    // save text area in cache
	                    rtn = RTNode.linkGeom(null, rtn, new TextHighlightBound(textBounds, ni, var.getKey()));
            		}
            	}

        		// look at variables on ports on the node
        		for(Iterator<PortInst> pIt = ni.getPortInsts(); pIt.hasNext(); )
        		{
        			PortInst pi = pIt.next();
            		for(Iterator<Variable> vIt = pi.getVariables(); vIt.hasNext(); )
                	{
                		Variable var = vIt.next();
                		if (!var.isDisplay()) continue;
                		Poly.Type style = getHighlightTextStyleBounds(wnd, pi, var.getKey(), textBounds);
                		if (style != null)
                		{
    	                    // save text area in cache
		                    rtn = RTNode.linkGeom(null, rtn, new TextHighlightBound(textBounds, pi, var.getKey()));
                		}
                	}
        		}

	    		// add export text
            	NodeProto np = ni.getProto();
            	if (!(np instanceof PrimitiveNode) || lv.isVisible((PrimitiveNode)np))
            	{
        			for(Iterator<Export> eIt = ni.getExports(); eIt.hasNext(); )
        			{
        				Export pp = eIt.next();
                        if (pp == null) continue; // in case of massive delete -> Swing accesses objects that are currently being modified
                        Poly.Type style = getHighlightTextStyleBounds(wnd, pp, Export.EXPORT_NAME, textBounds);
                		if (style != null)
                		{
    	                    // save text area in cache
		                    rtn = RTNode.linkGeom(null, rtn, new TextHighlightBound(textBounds, pp, Export.EXPORT_NAME));
                		}

        				// add in variables on the exports
                		for(Iterator<Variable> vIt = pp.getVariables(); vIt.hasNext(); )
                    	{
                    		Variable var = vIt.next();
                    		if (!var.isDisplay()) continue;
                    		style = getHighlightTextStyleBounds(wnd, pp, var.getKey(), textBounds);
                    		if (style != null)
                    		{
        	                    // save text area in cache
			                    rtn = RTNode.linkGeom(null, rtn, new TextHighlightBound(textBounds, pp, var.getKey()));
                    		}
                    	}
	            	}
	    		}
	        }

	        // next examine all text on arcs in the cell
            for(Iterator<ArcInst> it = cell.getArcs(); it.hasNext(); )
            {
                ArcInst ai = it.next();

                // now see if arc is named
        		if (ai.isUsernamed())
        		{
            		Poly.Type style = getHighlightTextStyleBounds(wnd, ai, ArcInst.ARC_NAME, textBounds);
            		if (style != null)
            		{
	                    // save text area in cache
	                    rtn = RTNode.linkGeom(null, rtn, new TextHighlightBound(textBounds, ai, ArcInst.ARC_NAME));
            		}
        		}

        		// look at all variables on the arc
        		for(Iterator<Variable> vIt = ai.getVariables(); vIt.hasNext(); )
            	{
            		Variable var = vIt.next();
            		if (!var.isDisplay()) continue;
            		Poly.Type style = getHighlightTextStyleBounds(wnd, ai, var.getKey(), textBounds);
            		if (style != null)
            		{
	                    // save text area in cache
	                    rtn = RTNode.linkGeom(null, rtn, new TextHighlightBound(textBounds, ai, var.getKey()));
            		}
            	}
            }

	        // save this R-Tree as the window's current cache
	        wnd.setTextInCell(rtn);
		}

		// look through the R-Tree cache to find the proper highlight
		Rectangle2D searchArea = new Rectangle2D.Double(bounds.getMinX()-directHitDist, bounds.getMinY()-directHitDist,
			bounds.getWidth()+directHitDist*2, bounds.getHeight()+directHitDist*2);
		for(Iterator<TextHighlightBound> sea = new RTNode.Search<TextHighlightBound>(searchArea, rtn, true); sea.hasNext(); )
		{
			TextHighlightBound thb = sea.next();
            if (!User.isTextVisibilityOnCell() && thb.getElectricObject()==cell)
                continue;
            if (thb.getElectricObject() instanceof NodeInst) {
                if (!User.isTextVisibilityOnNode()) continue;
                if (!User.isTextVisibilityOnAnnotation() && thb.getKey()!=NodeInst.NODE_PROTO) continue;
            }
            if (!User.isTextVisibilityOnPort() && thb.getElectricObject() instanceof PortInst)
                continue;
            if (!User.isTextVisibilityOnExport() && thb.getElectricObject() instanceof Export)
                continue;
            if (!User.isTextVisibilityOnArc() && thb.getElectricObject() instanceof ArcInst)
                continue;
			if (boundsIsHit(thb.getBounds(), bounds, directHitDist))
				list.add(new HighlightText(thb.getElectricObject(), cell, thb.getKey()));
		}
	}

	/**
	 * Method to see if a bound is within a given distance of a selection.
	 * @param bounds the bounds being tested.
	 * @param selection the selection area/point.
	 * @param directHitDist the required distance.
	 * @return true if the bound is close enough to the selection.
	 */
	private static boolean boundsIsHit(Rectangle2D bounds, Rectangle2D selection, double directHitDist)
	{
		// ignore areaMustEnclose if bounds is size 0,0
		boolean areaMustEnclose = User.isDraggingMustEncloseObjects();
	    if (areaMustEnclose && (selection.getHeight() > 0 || selection.getWidth() > 0))
	    {
	    	if (bounds.getMaxX() > selection.getMaxX()) return false;
	    	if (bounds.getMinX() < selection.getMinX()) return false;
	    	if (bounds.getMaxY() > selection.getMaxY()) return false;
	    	if (bounds.getMinY() < selection.getMinY()) return false;
	    } else
	    {
	    	double dist1 = selection.getMinX() - bounds.getMaxX();
	    	double dist2 = bounds.getMinX() - selection.getMaxX();
	    	double dist3 = selection.getMinY() - bounds.getMaxY();
	    	double dist4 = bounds.getMinY() - selection.getMaxY();
	    	double worstDist = Math.max(Math.max(dist1, dist2), Math.max(dist3, dist4));
	    	if (worstDist > directHitDist) return false;
	    }
	    return true;
	}

	/**
	 * Method to determine whether an object is in a bounds.
	 * @param geom the Geometric being tested for selection.
	 * @param findPort true if a port should be selected with a NodeInst.
	 * @param findPoint true if a point should be selected with an outline NodeInst.
	 * @param findSpecial true if hard-to-select and other special selection is being done.
	 * @param bounds the selected area or point.
	 * @param wnd the window being examined (null to ignore window scaling).
	 * @param directHitDist the slop area to forgive when searching (a few pixels in screen space, transformed to database units).
	 * @param areaMustEnclose true if the object must be completely inside of the selection area.
     * @param showTempNames consider temporary names on nodes and arcs.
	 * @return a List of Highlights that define the object, empty if the point is not over any part of this object.
	 */
	public static List<Highlight> checkOutObject(Geometric geom, boolean findPort, boolean findPoint, boolean findSpecial, Rectangle2D bounds,
		EditWindow wnd, double directHitDist, boolean areaMustEnclose, boolean showTempNames)
	{
		List<Highlight> found = new ArrayList<Highlight>();
        LayerVisibility lv = wnd != null ? wnd.getLayerVisibility() : LayerVisibility.getLayerVisibility();
		if (geom instanceof NodeInst)
		{
			// examine a node object
			NodeInst ni = (NodeInst)geom;

			// do not "find" hard-to-find nodes if "findSpecial" is not set
			boolean hardToSelect = ni.isHardSelect();
			if (ni.isCellInstance())
			{
				if (!User.isEasySelectionOfCellInstances()) hardToSelect = true;
			} else
			{
				// do not include primitives that have all layers invisible
				if (!User.isHighlightInvisibleObjects())
				{
					PrimitiveNode np = (PrimitiveNode)ni.getProto();
					if (!lv.isVisible(np)) return found;
				}
			}
			if (!findSpecial && hardToSelect) return found;

			// Invisible-Pins with text are "hard to find"
			if (!findSpecial && ni.isInvisiblePinWithText()) return found;

			// ignore areaMustEnclose if bounds is size 0,0
	        if (areaMustEnclose && (bounds.getHeight() > 0 || bounds.getWidth() > 0))
			{
	        	Poly poly = Highlight.getNodeInstOutline(ni);
	            if (poly == null) return found;
	   			if (!poly.isInside(bounds)) return found;
	   			found.add(new HighlightEOBJ(geom, geom.getParent(), true, -1));
                return found;
			}

			// get the distance to the object
			double dist = distToNode(bounds, ni, wnd, showTempNames);

			// direct hit
			if (dist <= directHitDist)
			{
				List<PortInst> bestPorts = new ArrayList<PortInst>();

				// add the closest port
				if (findPort)
				{
					double bestDist = Double.MAX_VALUE;
					for(Iterator<PortInst> it = ni.getPortInsts(); it.hasNext(); )
					{
						PortInst pi = it.next();
						PortProto pp = pi.getPortProto();
						if (pp instanceof PrimitivePort)
						{
							if (((PrimitivePort)pp).isWellPort() && !findSpecial) continue;
						}
						Poly poly = pi.getPoly();
						Point2D ctr = new Point2D.Double(poly.getCenterX(), poly.getCenterY());

						double boundCX = bounds.getCenterX();
						double boundCY = bounds.getCenterY();
						dist = ctr.distance(new Point2D.Double(boundCX, boundCY));
						if (bounds.getWidth() == 0 && bounds.getHeight() == 0)
						{
							if (poly.getCenterX() == boundCX && poly.getCenterY() == boundCY) dist = Double.MIN_VALUE;
						} else
						{
							if (bounds.contains(ctr)) dist = Double.MIN_VALUE;
						}
						if (dist < bestDist)
						{
							bestDist = dist;
							bestPorts.clear();
							bestPorts.add(pi);
						} else if (dist == bestDist)
						{
							bestPorts.add(pi);
						}
					}
				}

				// add the closest point
                int bestPoint = -1;
				if (findPoint)
				{
					Point2D [] points = ni.getTrace();
					Point2D cursor = new Point2D.Double(bounds.getCenterX(), bounds.getCenterY());
					if (points != null)
					{
						double bestDist = Double.MAX_VALUE;
						FixpTransform trans = ni.rotateOutAboutTrueCenter();
						for(int i=0; i<points.length; i++)
						{
							if (points[i] == null) continue;
							Point2D pt = new Point2D.Double(ni.getAnchorCenterX() + points[i].getX(),
								ni.getAnchorCenterY() + points[i].getY());
							trans.transform(pt, pt);
							dist = pt.distance(cursor);
							if (dist < bestDist)
							{
								bestDist = dist;
								bestPoint = i;
							}
						}
					}
				}
				if (bestPorts.size() > 0)
				{
                    HighlightEOBJ h = new HighlightEOBJ(bestPorts.get(0), geom.getParent(), true, bestPoint);
					found.add(h);
					for(int i=1; i<bestPorts.size(); i++)
					{
		                HighlightEOBJ hMore = new HighlightEOBJ(bestPorts.get(i), geom.getParent(), true, bestPoint);
						found.add(hMore);
					}
				} else
				{
                    HighlightEOBJ h = new HighlightEOBJ(ni, geom.getParent(), true, bestPoint);
					found.add(h);
				}
				return found;
			}
		} else
		{
			// examine an arc object
			ArcInst ai = (ArcInst)geom;

			// do not "find" hard-to-find arcs if "findSpecial" is not set
			if (!findSpecial && ai.isHardSelect()) return found;

			// do not include arcs that have all layers invisible
			if (!User.isHighlightInvisibleObjects() && !lv.isVisible(ai.getProto())) return found;

			// ignore areaMustEnclose if bounds is size 0,0
	        if (areaMustEnclose && (bounds.getHeight() > 0 || bounds.getWidth() > 0))
			{
	        	Poly poly = ai.makeLambdaPoly(ai.getGridBaseWidth(), Poly.Type.CLOSED);
	            if (poly == null) return found;
	   			if (!poly.isInside(bounds)) return found;
                Highlight h = new HighlightEOBJ(geom, geom.getParent(), true, -1);
                found.add(h);
				return found;
			}

			// get distance to arc
			double dist = distToArc(bounds, ai, wnd, showTempNames);

			// direct hit
			if (dist <= directHitDist)
			{
                Highlight h = new HighlightEOBJ(geom, geom.getParent(), true, -1);
                found.add(h);
				return found;
			}
		}
		return found;
	}

    /**
     * Chooses a single Highlight from the list of Highlights 'highlights' that is most
     * similar to Highlight 'exampleHigh'.
     * @param highlights a list of Highlight Objects
     * @param exampleHigh the Highlight that serves as an example of what type
     * of Highlight should be retrieved from the highlights list.
     */
    public static Highlight getSimiliarHighlight(List<Highlight> highlights, Highlight exampleHigh) {
        if (highlights.isEmpty()) return null;
        if (exampleHigh == null || !exampleHigh.isValid()) return highlights.get(0);

        // get Highlights of the same type
        List<Highlight> sameTypes = new ArrayList<Highlight>();
        for (Highlight h : highlights)
        {
            assert(h.isValid()); // looking for more invalid cases
            if (h.getClass() == exampleHigh.getClass())
            {
                sameTypes.add(h);
            }
        }

        // if only one, just return it
        if (sameTypes.size() == 1) return sameTypes.get(0);

        // if none of same type, just return first in list of all highlights
        if (sameTypes.isEmpty()) return highlights.get(0);

        // we have different rules depending on the type
        if (exampleHigh.isHighlightEOBJ())
        {
            // get Highlights of the same electric object
            List<Highlight> sameEObj = new ArrayList<Highlight>();
            for (Highlight h : sameTypes) {
                if (h.getElectricObject().getClass() == exampleHigh.getElectricObject().getClass())
                    sameEObj.add(h);
            }

            // if only one of same object, return it
            if (sameEObj.size() == 1) return sameEObj.get(0);

            // if more than one of the same ElectricObject, make decisions
            // for some of the common choices
            if (sameEObj.size() > 0) {
                // for PortInsts (Mouse GUI always sets "findPort", so we don't care about NodeInsts, only PortInsts)
                if (exampleHigh.getElectricObject().getClass() == PortInst.class) {
                    // see if we can find a port on the same NodeProto
                    PortInst exPi = (PortInst)exampleHigh.getElectricObject();
                    NodeProto exNp = exPi.getNodeInst().getProto();
                    for (Highlight h : sameEObj) {
                        PortInst pi = (PortInst)h.getElectricObject();
                        NodeProto np = pi.getNodeInst().getProto();
                        if (np == exNp) return h;
                    }
                    // nothing with the same prototype, see if we can find a port that can connect to it
                    for (Highlight h : sameEObj) {
                        PortInst pi = (PortInst)h.getElectricObject();
                        if (Router.getArcToUse(exPi.getPortProto(), pi.getPortProto()) != null) {
                            return h;
                        }
                    }
                }
                // for ArcInsts, see if we can find an arc with the same ArcProto
                if (exampleHigh.getElectricObject().getClass() == ArcInst.class) {
                    ArcInst exAi = (ArcInst)exampleHigh.getElectricObject();
                    ArcProto exAp = exAi.getProto();
                    for (Highlight h : sameEObj) {
                        ArcInst ai = (ArcInst)h.getElectricObject();
                        ArcProto ap = ai.getProto();
                        if (exAp == ap) return h;
                    }
                }
            } else { // (sameEObj.size() == 0)
                // no Highlights of same object. See if we can find another object that will connect
                // one must be an ArcInst and one must be a PortInst. Other combinations already handled above.
                ArcInst exAi = null;
                PortInst exPi = null;
                if (exampleHigh.getElectricObject().getClass() == ArcInst.class)
                    exAi = (ArcInst)exampleHigh.getElectricObject();
                if (exampleHigh.getElectricObject().getClass() == PortInst.class)
                    exPi = (PortInst)exampleHigh.getElectricObject();
                for (Highlight h : sameTypes) {
                    // reset ai and pi
                    ArcInst ai = exAi;
                    PortInst pi = exPi;
                    assert(h.isValid()); // looking for more invalid cases
                    if (h.getElectricObject().getClass() == ArcInst.class)
                        ai = (ArcInst)h.getElectricObject();
                    if (h.getElectricObject().getClass() == PortInst.class)
                        pi = (PortInst)h.getElectricObject();
                    // if either null, can't connect these two EObjs
                    if ((ai == null) || (pi == null)) continue;
                    if (pi.getPortProto().connectsTo(ai.getProto())) return h;
                }
            }
            // couldn't find a highlight based on connectivity or same object class
            // return first in list if possible
            if (sameEObj.size() > 0) return sameEObj.get(0);
        }
        // return first in list (list empty case handled above)
        return sameTypes.get(0);
    }

    private static Map<PrimitiveNode,Boolean> portsOutsideHighlight = new HashMap<PrimitiveNode,Boolean>();

    /**
	 * Method to return the distance from a bound to a NodeInst.
	 * @param bounds the bounds in question.
	 * @param ni the NodeInst.
	 * @param wnd the window being examined (null to ignore text/window scaling).
     * @param showTempNames consider temporary names on nodes and arcs
	 * @return the distance from the bounds to the NodeInst.
	 * Negative values are direct hits.
	 */
	public static double distToNode(Rectangle2D bounds, NodeInst ni, EditWindow wnd, boolean showTempNames)
	{
		FixpTransform trans = ni.rotateOut();

		NodeProto np = ni.getProto();
		if (!ni.isCellInstance())
		{
			// special case for MOS transistors and RESIST in layout: examine the gate/active tabs
			PrimitiveNode pnp = (PrimitiveNode)ni.getProto();
			PrimitiveNode.Function fun = np.getFunction();
			Technology tech = np.getTechnology();
			if (fun.isFET() ||
				(fun.isResistor() && tech != Schematics.tech()))
			{
				double bestDist = Double.MAX_VALUE;
				Poly [] polys = tech.getShapeOfNode(ni);
				for(int box=0; box<polys.length; box++)
				{
					Poly poly = polys[box];
					Layer layer = poly.getLayer();
					if (layer == null) continue;
					Layer.Function lf = layer.getFunction();
					if (!lf.isPoly() && !lf.isDiff() && !lf.isMetal()) continue;
					poly.transform(trans);
					double dist = poly.polyDistance(bounds);
					if (dist < bestDist) bestDist = dist;
				}
				if (wnd != null)
				{
					for(Poly poly: ni.getDisplayableVariables(wnd, showTempNames))
					{
						Layer layer = poly.getLayer();
						if (layer == null) continue;
						Layer.Function lf = layer.getFunction();
						if (!lf.isPoly() && !lf.isDiff() && !lf.isMetal()) continue;
						poly.transform(trans);
						double dist = poly.polyDistance(bounds);
						if (dist < bestDist) bestDist = dist;
					}
				}
				return bestDist;
			}

			// check special cases where ports are outside of the highlight area
			Boolean outsideBase = portsOutsideHighlight.get(pnp);
			if (outsideBase == null)
			{
				Poly nodePoly = ni.getBaseShape();
				boolean portOutside = false;
				for(Iterator<PortInst> it = ni.getPortInsts(); it.hasNext(); )
				{
					PortInst pi = it.next();
					EPoint pt = pi.getCenter();
					if (nodePoly.contains(pt)) continue;
					portOutside = true;
					break;
				}
				portsOutsideHighlight.put(pnp, outsideBase = Boolean.valueOf(portOutside));
			}

			// special case for artwork and ports outside of highlight area: examine all polygons
			if (outsideBase.booleanValue() || fun == PrimitiveNode.Function.ART)
			{
				double bestDist = Double.MAX_VALUE;
				Poly [] polys = tech.getShapeOfNode(ni);
				for(int box=0; box<polys.length; box++)
				{
					Poly poly = polys[box];
					poly.transform(trans);
					double dist = poly.polyDistance(bounds);
					if (dist < bestDist) bestDist = dist;
				}
				if (wnd != null)
				{
					for(Poly poly: ni.getDisplayableVariables(wnd, showTempNames))
					{
						poly.transform(trans);
						double dist = poly.polyDistance(bounds);
						if (dist < bestDist) bestDist = dist;
					}
				}
				return bestDist;
			}

			// special case for 1-polygon primitives: check precise distance to cursor
			if (((PrimitiveNode)np).isEdgeSelect())
			{
				double bestDist = Double.MAX_VALUE;
				Poly [] polys = tech.getShapeOfNode(ni);
				for(int box=0; box<polys.length; box++)
				{
					Poly poly = polys[box];
					poly.transform(trans);
					double dist = poly.polyDistance(bounds);
					if (dist < bestDist) bestDist = dist;
				}
				if (wnd != null)
				{
					for(Poly poly: ni.getDisplayableVariables(wnd, showTempNames))
					{
						poly.transform(trans);
						double dist = poly.polyDistance(bounds);
						if (dist < bestDist) bestDist = dist;
					}
				}
				return bestDist;
			}
		}

        Poly nodePoly = ni.getBaseShape();
		nodePoly.setStyle(Poly.Type.FILLED);
		double dist = nodePoly.polyDistance(bounds);
		return dist;
	}

    /**
	 * Method to return the distance from a bounds to an ArcInst.
	 * @param bounds the bounds in question.
	 * @param ai the ArcInst.
	 * @param wnd the window being examined.
     * @param showTempNames consider temporary names on nodes and arcs
	 * @return the distance from the bounds to the ArcInst.
	 * Negative values are direct hits or intersections.
	 */
	public static double distToArc(Rectangle2D bounds, ArcInst ai, EditWindow wnd, boolean showTempNames)
	{
		ArcProto ap = ai.getProto();

		// if arc is selectable precisely, check distance to cursor
		if (ap.isEdgeSelect())
		{
			Technology tech = ap.getTechnology();
			Poly[] polys = tech.getShapeOfArc(ai);
			double bestDist = Double.MAX_VALUE;
			for(int box=0; box<polys.length; box++)
			{
				Poly poly = polys[box];
				double dist = poly.polyDistance(bounds);
				if (dist < bestDist) bestDist = dist;
			}
			Poly[] textPolys = ai.getDisplayableVariables(wnd, showTempNames);
			for(int box=0; box<textPolys.length; box++)
			{
				Poly poly = textPolys[box];
				double dist = poly.polyDistance(bounds);
				if (dist < bestDist) bestDist = dist;
			}
			return bestDist;
		}

		// standard distance to the arc
		long gridWid = ai.getGridBaseWidth();
		if (gridWid == 0) gridWid = DBMath.lambdaToSizeGrid(1);
		Poly poly = ai.makeLambdaPoly(gridWid, Poly.Type.FILLED);
		return poly.polyDistance(bounds);
	}

    @Override
    public void databaseChanged(DatabaseChangeEvent e) {
        // see if anything we care about changed
        finished();
    }
}
