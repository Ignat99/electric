/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: RoutingDebug.java
 *
 * Copyright (c) 2011, Static Free Software. All rights reserved.
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
package com.sun.electric.tool.user.ui;

import com.sun.electric.database.EditingPreferences;
import com.sun.electric.database.geometry.EPoint;
import com.sun.electric.database.geometry.ERectangle;
import com.sun.electric.database.geometry.Poly;
import com.sun.electric.database.geometry.PolyBase;
import com.sun.electric.database.geometry.PolyBase.Point;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.text.TextUtils;
import com.sun.electric.database.topology.ArcInst;
import com.sun.electric.database.topology.PortInst;
import com.sun.electric.database.topology.SteinerTree.SteinerTreePortPair;
import com.sun.electric.technology.ArcProto;
import com.sun.electric.technology.Layer;
import com.sun.electric.technology.Technology;
import com.sun.electric.technology.technologies.Generic;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.JobException;
import com.sun.electric.tool.routing.SeaOfGates;
import com.sun.electric.tool.routing.SeaOfGates.SeaOfGatesCellParameters;
import com.sun.electric.tool.routing.SeaOfGates.SeaOfGatesOptions;
import com.sun.electric.tool.routing.seaOfGates.SeaOfGatesEngine;
import com.sun.electric.tool.routing.seaOfGates.SeaOfGatesEngine.GRBucket;
import com.sun.electric.tool.routing.seaOfGates.SeaOfGatesEngine.GRNet;
import com.sun.electric.tool.routing.seaOfGates.SeaOfGatesEngine.GRWire;
import com.sun.electric.tool.routing.seaOfGates.SeaOfGatesEngine.GlobalRouter;
import com.sun.electric.tool.routing.seaOfGates.SeaOfGatesEngine.NeededRoute;
import com.sun.electric.tool.routing.seaOfGates.SeaOfGatesEngine.PossibleEndpoint;
import com.sun.electric.tool.routing.seaOfGates.SeaOfGatesEngine.PossibleEndpoints;
import com.sun.electric.tool.routing.seaOfGates.SeaOfGatesEngine.RoutesOnNetwork;
import com.sun.electric.tool.routing.seaOfGates.SeaOfGatesEngine.SOGBound;
import com.sun.electric.tool.routing.seaOfGates.SeaOfGatesEngine.SOGPoly;
import com.sun.electric.tool.routing.seaOfGates.SeaOfGatesEngine.SearchVertex;
import com.sun.electric.tool.routing.seaOfGates.SeaOfGatesEngine.Wavefront;
import com.sun.electric.tool.routing.seaOfGates.SeaOfGatesEngineFactory;
import com.sun.electric.tool.routing.seaOfGates.SeaOfGatesEngineFactory.SeaOfGatesEngineType;
import com.sun.electric.tool.routing.seaOfGates.SeaOfGatesHandlers;
import com.sun.electric.tool.user.Highlight;
import com.sun.electric.tool.user.Highlighter;
import com.sun.electric.tool.user.User;
import com.sun.electric.tool.user.UserInterfaceMain;
import com.sun.electric.tool.user.dialogs.EModelessDialog;
import com.sun.electric.tool.user.dialogs.SeaOfGatesCell;
import com.sun.electric.tool.user.ui.ToolBar.CursorMode;
import com.sun.electric.util.math.MutableInteger;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.UIManager;

/**
 * Class to handle Sea-of-Gates routing debugging.
 */
public class RoutingDebug
{
	private static final double goalWidth = 0.005;
	private static final double layerOffset = 0.04;
	private static final double possibleGoalOffset = 3;

	private static RoutingDialog debugDialog = null;
	private static Set<SearchVertex> onPath = new HashSet<SearchVertex>();
	private static SVState currentSVHighlight = null;
	private static Highlighter highlighter = null;
	private static Cell cell;
	private static boolean endADebug;
	private static int svOrder;
	private static Map<Integer,Color> netColors;
	private static Map<Integer,String> netNames;
	private static int colorAssigned;
	private static DebugType debuggingType;
	private static enum DebugType {NONE, DISPLAYROUTING, REWIRENETS, SHOWSPINES, RUNGLOBALROUTING};

	private static Color[] allColors = new Color[] {
		new Color(65535>>8, 16385>>8, 16385>>8),	// Red
		new Color(0>>8, 39321>>8, 0>>8),			// Green
		new Color(0>>8, 0>>8, 65535>>8),			// Blue
		new Color(39321>>8, 0>>8, 31457>>8),		// Purple
		new Color(65535>>8, 32768>>8, 32768>>8),	// Salmon
		new Color(0>>8, 65535>>8, 0>>8),			// Lime
		new Color(16385>>8, 65535>>8, 65535>>8),	// Turquoise
		new Color(65535>>8, 32768>>8, 58981>>8),	// Light purple
		new Color(39321>>8, 26208>>8, 0>>8),		// Brown
		new Color(52428>>8, 34958>>8, 0>>8),		// Light brown
		new Color(65535>>8, 32764>>8, 16385>>8),	// Orange
		new Color(0>>8, 52428>>8, 26586>>8),		// Teal
		new Color(0>>8, 0>>8, 39321>>8),			// Dark blue
		new Color(65535>>8, 49151>>8, 55704>>8)};	// Pink

	/************************************* CONTROL *************************************/

	/**
	 * Method to do bring up the dialog for interactive routing.
	 */
	public static void startDebugging()
	{
		debugDialog = new RoutingDialog();

		// start off in routing mode
		debugDialog.routingMode.setSelected(true);
		User.setRoutingMode(true);
		ToolBar.setCursorMode(CursorMode.ROUTING);
	}

	/**
	 * Method to close the interactive routing dialog.
	 */
	private static void endDebugging()
	{
		// disable routing mode in click/zoom/wire listener
		User.setRoutingMode(false);
		if (ToolBar.getCursorMode() == CursorMode.ROUTING)
			ToolBar.setCursorMode(CursorMode.CLICKZOOMWIRE);

		if (debugDialog != null)
		{
			// remove dialog
			debugDialog.setVisible(false);
			debugDialog.dispose();
			debugDialog = null;
		}
	}

	/**
	 * Method to tell whether the interactive routing dialog is present.
	 * @return true if the interactive routing dialog is present.
	 */
	public static boolean isActive() { return debugDialog != null; }

	/************************************* DEBUGGING ROUTING *************************************/

	/**
	 * Method called when a route is to be debugged.
	 * @param endA true to debug the A->B wavefront, false for the B->A wavefront.
	 */
	private static void showRouting(boolean endA)
	{
		EditWindow wnd = EditWindow.getCurrent();
		cell = wnd.getCell();
		highlighter = wnd.getRulerHighlighter();

		debuggingType = DebugType.DISPLAYROUTING;
		endADebug = endA;
		debugDialog.router = SeaOfGatesEngineFactory.createSeaOfGatesEngine(SeaOfGatesEngineType.defaultVersion);
		SeaOfGatesOptions prefs = new SeaOfGatesOptions();
		prefs.getOptionsFromPreferences(false);
		debugDialog.router.setPrefs(prefs);
		debugDialog.svInfo = new HashMap<SearchVertex,SVState>();
		netNames = new HashMap<Integer,String>();
		svOrder = 1;
		SeaOfGates.seaOfGatesRoute(UserInterfaceMain.getEditingPreferences(), debugDialog.router);
	}

	public static boolean isEndADebugging() { return endADebug; }

	public static void setNetName(Integer netID, String name)
	{
		netNames.put(netID, name);
	}

	/**
	 * Method called at the end of the routing to show the results.
	 * @param nr the NeededRoute that ran.
	 */
	public static void debugRoute(NeededRoute nr)
	{
		if (debugDialog == null) return;
		DebugThread runnable = new DebugThread(nr);
		runnable.startJob();
	}

	private static class DebugThread extends Job
	{
		private DebugThread(NeededRoute nr)
		{
            super("Debug Sea-Of-Gates Route", User.getUserTool(), Job.Type.CLIENT_EXAMINE, null, null, Job.Priority.USER);
			Wavefront wf;
			if (endADebug) wf = nr.getWavefrontAtoB(); else
				wf = nr.getWavefrontBtoA();
			SearchVertex result = wf.getFinalSearchVertex();

			highlighter.clear();

			// show all blockages
			showGeometryInArea(nr);

			// show the route in green
			Point2D p1 = new Point2D.Double(nr.getAX(), nr.getAY());
			Point2D p2 = new Point2D.Double(nr.getBX(), nr.getBY());
			highlighter.addLine(p1, p2, cell, true, Color.GREEN, false);

			// show goal(s)
			debugDialog.showPathToGoal(nr, result, cell, highlighter);

			// remember path to goal
			onPath.clear();
			for(SearchVertex sv = result; sv != null; sv = sv.getLast()) onPath.add(sv);

			// draw the search vertices
			debugDialog.showSearchVertices(cell, highlighter, wf);

			// show Global Routing information
			if (debugDialog.globalRoutingResults != null)
			{
				debugDialog.showGlobalRoutingGrid();
				debugDialog.showGlobalRoutingPath(nr);
			}

			highlighter.finished();
			EditWindow.repaintAllContents();
		}

		@Override
		public boolean doIt() throws JobException { return true; }

		@Override
		public void terminateOK()
		{
			// highlight the starting vertex
			SearchVertex svStart = null;
			for(SearchVertex sv : debugDialog.svInfo.keySet())
			{
				if (sv.getLast() == null) { svStart = sv;  break; }
			}
			if (svStart == null)
			{
				System.out.println("WARNING: Cannot find starting search-vertex");
				return;
			}
			showSelectedSV(svStart);
		}
	}

	/**
	 * Method called during search to record a directional link between two SearchVertex objects.
	 * @param sv the SearchVertex that was created.
	 * @param lastDirection the direction in which the last SearchVertex came to this one.
	 */
	public static void saveSVLink(SearchVertex sv, int lastDirection)
	{
		if (debugDialog != null)
		{
			if (lastDirection >= 0)
			{
				SVState svs = ensureDebuggingShadow(sv.getLast(), false);
				svs.nextVertices[lastDirection] = sv;
			}
		}
	}

	/**
	 * Method called during search to save cost information about a SearchVertex.
	 * @param sv the SearchVertex that was just analyzed.
	 * @param details a description of the cost and other information.
	 */
	public static void saveSVDetails(SearchVertex sv, String[] details, boolean forcePrinting)
	{
		if (debugDialog != null)
		{
			SVState svs = ensureDebuggingShadow(sv, false);
			svs.details = details;
		}
		if (forcePrinting)
		{
			// print in standard output
			for (String s: details)
				System.out.println(s);
		}
	}

	/**
	 * Method called from user interface to identify a SearchVertex at a given coordinate.
	 * @param evt the coordinates of the mouse.
	 * @return SearchVertex that was clicked.
	 */
	public static SearchVertex findDebugSearchVertex(MouseEvent evt)
	{
		if (debugDialog == null || debugDialog.svInfo == null) return null;
		EditWindow wnd = (EditWindow)evt.getSource();
		if (wnd.getScale() < 25) return null;
		Point2D dbClick = wnd.screenToDatabase(evt.getX(), evt.getY());

		// cycle through objects
		double bestDist = Double.MAX_VALUE;
		SearchVertex bestSV = null;
		for(SearchVertex sv : debugDialog.svInfo.keySet())
		{
			double off = sv.getZ() * layerOffset;
			double dX = sv.getX() + off - dbClick.getX();
			double dY = sv.getY() + off - dbClick.getY();
			double dist = Math.sqrt(dX*dX + dY*dY);
			if (dist < bestDist)
			{
				bestDist = dist;
				bestSV = sv;
			}
		}
		if (bestDist < 1) return bestSV;
		return null;
	}

	/**
	 * Method called from user interface to show when the mouse hovers over a SearchVertex.
	 * @param sv the SearchVertex to highlight.
	 */
	public static void previewSelectedSV(SearchVertex sv, boolean center)
	{
		if (debugDialog == null) return;
		EditWindow wnd = EditWindow.getCurrent();
		Highlighter h = wnd.getRulerHighlighter();
		SVState svs = debugDialog.svInfo.get(sv);
		if (currentSVHighlight != null) currentSVHighlight.setBackgroundColor(null, h);
		if (debugDialog.currentSV != null)
		{
			SVState svsHigh = debugDialog.svInfo.get(debugDialog.currentSV);
			if (svsHigh != null)
			{
				svsHigh.setBackgroundColor(Color.WHITE, h);
				h.finished();
			}
		}
		currentSVHighlight = svs;
		if (currentSVHighlight != null)
		{
			currentSVHighlight.setBackgroundColor(Color.RED, h);
			h.finished();
			if (center)
			{
				Rectangle2D windowBound = wnd.getDisplayedBounds();
				if (sv.getX() < windowBound.getMinX() || sv.getX() > windowBound.getMaxX() ||
					sv.getY() < windowBound.getMinY() || sv.getY() > windowBound.getMaxY())
						wnd.setOffset(new Point2D.Double(sv.getX(), sv.getY()));
			}
			wnd.fullRepaint();
		}
	}

	/**
	 * Method called from user interface to show a SearchVertex that was clicked.
	 * @param sv SearchVertex that was clicked.
	 */
	public static void showSelectedSV(SearchVertex sv)
	{
		if (sv != null)
			debugDialog.seeSelectedSV(sv);
	}

	/************************************* SHOW BLOCKAGES *************************************/

	/**
	 * Method to show blockages in a routing area.
	 * @param nr the NeededRoute to view.
	 */
	private static void showGeometryInArea(NeededRoute nr)
	{
		debuggingType = DebugType.NONE;
		if (debugDialog == null) return;

		EditWindow wnd = EditWindow.getCurrent();
		Cell cell = wnd.getCell();
		Rectangle2D limit = nr.getBounds();
		Highlighter h = wnd.getRulerHighlighter();
		h.clear();
		colorAssigned = 0;
		netColors = new HashMap<Integer,Color>();
		double highestX = Double.MIN_VALUE, highestY = Double.MIN_VALUE;
		debugDialog.setRouteDescription("Netlist information for selected area", null);

		// show the bounds of the route
		showBlockageRect(cell, limit, h, Color.ORANGE);

		// show geometry in area
		Map<Layer,List<SOGBound>> allAssigned = new HashMap<Layer,List<SOGBound>>();
		for(int i=0; i<debugDialog.router.getNumMetals(); i++)
		{
			Layer layer = debugDialog.router.getPrimaryMetalLayer(i);
			List<SOGBound> assigned = new ArrayList<SOGBound>();
			allAssigned.put(layer, assigned);
			for (Iterator<SOGBound> sea = debugDialog.router.searchMetalTree(layer, limit); sea.hasNext();)
			{
				SOGBound sBound = sea.next();
				if (sBound.getNetID() != null && sBound.getNetID().intValue() != 0) assigned.add(sBound); else
				{
					ERectangle drawn = showGeometryPiece(sBound, limit, layer);
					if (drawn != null)
					{
						if (drawn.getMaxX() > highestX) highestX = drawn.getMaxX();
						if (drawn.getMaxY() > highestY) highestY = drawn.getMaxY();
					}
				}
			}
		}
		for(int i=0; i<debugDialog.router.getNumMetals(); i++)
		{
			Layer layer = debugDialog.router.getPrimaryMetalLayer(i);
			List<SOGBound> assigned = allAssigned.get(layer);
			for(SOGBound sBound : assigned)
			{
				ERectangle drawn = showGeometryPiece(sBound, limit, layer);
				if (drawn != null)
				{
					if (drawn.getMaxX() > highestX) highestX = drawn.getMaxX();
					if (drawn.getMaxY() > highestY) highestY = drawn.getMaxY();
				}
			}
		}

		// show key
		double pos = highestY - 2;

		showBlockageRect(cell, new Rectangle2D.Double(highestX+1, pos, 4, 2), h, Color.BLACK);
		h.addMessage(cell, "Detected Blockage", EPoint.fromLambda(highestX+6, pos+1));
		pos -= 3;

		showBlockageRect(cell, new Rectangle2D.Double(highestX+1, pos, 4, 2), h, Color.BLUE);
		h.addMessage(cell, "User-Supplied Blockage", EPoint.fromLambda(highestX+6, pos+1));
		pos -= 3;

		showBlockageRect(cell, new Rectangle2D.Double(highestX+1, pos, 4, 2), h, Color.GRAY);
		h.addMessage(cell, "Endpoint Blockage", EPoint.fromLambda(highestX+6, pos+1));
		pos -= 3;

		for(Integer netIDI : netColors.keySet())
		{
			showBlockageRect(cell, new Rectangle2D.Double(highestX+1, pos, 4, 2), h, netColors.get(netIDI));
			String netName = netNames.get(netIDI);
			if (netName == null) netName = "Net " + netIDI.intValue();
			h.addMessage(cell, netName, EPoint.fromLambda(highestX+6, pos+1));
			pos -= 3;
		}
		h.finished();
		EditWindow.repaintAllContents();
	}

	/**
	 * Method to draw a thick rectangular outline.
	 * @param cell the Cell in which to draw the outline.
	 * @param bounds the rectangle to draw.
	 * @param h the Highlighter to use.
	 * @param col the Color to draw it.
	 */
	private static void showBlockageRect(Cell cell, Rectangle2D bounds, Highlighter h, Color col)
	{
		Point2D p1 = new Point2D.Double(bounds.getMinX(), bounds.getMinY());
		Point2D p2 = new Point2D.Double(bounds.getMinX(), bounds.getMaxY());
		Point2D p3 = new Point2D.Double(bounds.getMaxX(), bounds.getMaxY());
		Point2D p4 = new Point2D.Double(bounds.getMaxX(), bounds.getMinY());
		h.addLine(p1, p2, cell, true, col, false);
		h.addLine(p2, p3, cell, true, col, false);
		h.addLine(p3, p4, cell, true, col, false);
		h.addLine(p4, p1, cell, true, col, false);
	}

	private static ERectangle showGeometryPiece(SOGBound sBound, Rectangle2D limit, Layer lay)
	{
		Integer netIDI;
		MutableInteger mi = sBound.getNetID();
		if (mi == null) netIDI = Integer.valueOf(0); else
			netIDI = Integer.valueOf(mi.intValue());
		Color color = Color.BLACK;
		if (sBound.isPseudoBlockage()) color = Color.GRAY; else
			if (sBound.isUserSuppliedBlockage()) color = Color.BLUE; else
		{
			if (netIDI.intValue() != 0)
			{
				color = netColors.get(netIDI);
				if (color == null)
				{
					netColors.put(netIDI, color = allColors[colorAssigned % allColors.length]);
					colorAssigned++;
				}
			}
		}
		ERectangle draw = sBound.getBounds();
		EditWindow wnd = EditWindow.getCurrent();
		Cell cell = wnd.getCell();
		Highlighter h = wnd.getRulerHighlighter();
		if (sBound instanceof SOGPoly)
		{
			SOGPoly pol = (SOGPoly)sBound;
			PolyBase pb = pol.getPoly();
			Point[] points = pb.getPoints();
			for(int i=0; i<points.length; i++)
			{
				int lastI = (i == 0 ? points.length-1 : i-1);
				h.addLine(points[lastI], points[i], cell, true, color, false);
			}
		} else
		{
			double lX = draw.getMinX();
			double hX = draw.getMaxX();
			double lY = draw.getMinY();
			double hY = draw.getMaxY();
			if (lX < limit.getMinX()) { lX = limit.getMinX();   draw = null; }
			if (hX > limit.getMaxX()) { hX = limit.getMaxX();   draw = null; }
			if (lY < limit.getMinY()) { lY = limit.getMinY();   draw = null; }
			if (hY > limit.getMaxY()) { hY = limit.getMaxY();   draw = null; }
			if (draw == null) draw = ERectangle.fromLambda(lX, lY, hX-lX, hY-lY);
			showBlockageRect(cell, draw, h, color);
		}
		return draw;
	}

	/************************************* DEBUGGING GLOBAL ROUTING *************************************/

	/**
	 * Method called when global routing results are to be viewed.
	 */
	public static void doGlobalRouting()
	{
		debugDialog.router = SeaOfGatesEngineFactory.createSeaOfGatesEngine(SeaOfGatesEngineType.defaultVersion);
		SeaOfGatesOptions prefs = new SeaOfGatesOptions();
		prefs.getOptionsFromPreferences(false);
		debuggingType = DebugType.RUNGLOBALROUTING;
		debugDialog.router.setPrefs(prefs);
		SeaOfGates.seaOfGatesRoute(UserInterfaceMain.getEditingPreferences(), debugDialog.router);
	}

	public static String getDesiredRouteToDebug()
	{
		String selection = (debugDialog != null) ? debugDialog.whichOne.getText().trim() : null;
		return selection;
	}

	public static boolean isTestGlobalRouting() { return debugDialog != null && debuggingType == DebugType.RUNGLOBALROUTING; }

	public static void setGlobalRouting(GlobalRouter gr) { debugDialog.globalRoutingResults = gr; }

	public static void showGlobalRouting()
	{
		EditWindow wnd = EditWindow.getCurrent();
		Highlighter h = wnd.getRulerHighlighter();
		h.clear();

		// show grid lines around buckets
		debugDialog.showGlobalRoutingGrid();

		// show global routes
		debugDialog.showGlobalRoutingPath(null);

		// redraw
		h.finished();
		wnd.repaint();
	}

	/************************************* REWIRE NETWORKS *************************************/

	private static List<RoutesOnNetwork> allSpineRoutes = null;

	private static void showSpines()
	{
		EditWindow wnd = EditWindow.getCurrent();
		Cell cell = wnd.getCell();
		if (cell == null) return;
		highlighter = wnd.getRulerHighlighter();
		allSpineRoutes = null;
		String routeName = debugDialog.whichOne.getText().trim();
		if (routeName.length() == 0)
		{
			Job.getUserInterface().showErrorMessage("Must set name of route first in 'Route to Debug' field.", "Missing Information");
			return;
		}
		new ShowSpineNets(cell, routeName);
	}

	public static class ShowSpineNets extends Job
	{
		private Cell cell;
		private EditingPreferences ep;
		private String routeName;

		public ShowSpineNets(Cell c, String rn)
		{
			super("Show Spines", User.getUserTool(), Job.Type.CHANGE, null, null, Job.Priority.USER);
			cell = c;
			routeName = rn;
			ep = UserInterfaceMain.getEditingPreferences();
			startJob();
		}

		public boolean doIt() throws JobException
		{
			debuggingType = DebugType.SHOWSPINES;
			debugDialog.router = SeaOfGatesEngineFactory.createSeaOfGatesEngine(SeaOfGatesEngineType.defaultVersion);
			SeaOfGatesOptions prefs = new SeaOfGatesOptions();
			prefs.getOptionsFromPreferences(false);
			debugDialog.router.setPrefs(prefs);
			debugDialog.svInfo = new HashMap<SearchVertex,SVState>();
			netNames = new HashMap<Integer,String>();
			svOrder = 1;

			Job job = Job.getRunningJob();
			debugDialog.router.routeIt(SeaOfGatesHandlers.getDefault(cell, debugDialog.router.getPrefs().resultCellName,
				debugDialog.router.getPrefs().contactPlacementAction, job, ep), cell, false, null);
			return true;
		}

		private static final int TAPSIZE = 8;
		private static final int TAPOFFSET = 25;

		@Override
		public void terminateOK()
		{
			EditWindow wnd = EditWindow.getCurrent();
			Highlighter h = wnd.getRulerHighlighter();
			h.clear();
			for(RoutesOnNetwork ron : allSpineRoutes)
			{
				if (!ron.getName().equals(routeName)) continue;

				List<SteinerTreePortPair> pairs = ron.getPairs();
				for(SteinerTreePortPair pair : pairs)
				{
					List<PortInst> taps = pair.getSpineTaps();
					double fX = pair.getPort1().getCenter().getX(), fY = pair.getPort1().getCenter().getY();
					double tX = pair.getPort2().getCenter().getX(), tY = pair.getPort2().getCenter().getY();
					boolean hor = Math.abs(fX-tX) > Math.abs(fY-tY);
					double offX = 0, offY = 0;
					if (hor)
					{
						offY = TAPOFFSET;
						fY = tY = Math.max(fY, tY);
					} else
					{
						offX = -TAPOFFSET;
						fX = tX = Math.min(fX, tX);
					}

					Color col = taps == null ? Color.GREEN : Color.RED;

					h.addLine(EPoint.fromLambda(fX+offX, fY+offY), EPoint.fromLambda(tX+offX, tY+offY), cell, true, col, false);

					// show the end points
					Poly poly = new Poly(new Rectangle2D.Double(pair.getPort1().getCenter().getX()-TAPSIZE, pair.getPort1().getCenter().getY()-TAPSIZE, TAPSIZE*2, TAPSIZE*2));
					poly.setStyle(Poly.Type.FILLED);
					h.addPoly(poly, cell, Color.RED);
					h.addLine(EPoint.fromLambda(fX+offX, fY+offY), pair.getPort1().getCenter(), cell, true, col, false);

					poly = new Poly(new Rectangle2D.Double(pair.getPort2().getCenter().getX()-TAPSIZE, pair.getPort2().getCenter().getY()-TAPSIZE, TAPSIZE*2, TAPSIZE*2));
					poly.setStyle(Poly.Type.FILLED);
					h.addPoly(poly, cell, Color.RED);
					h.addLine(EPoint.fromLambda(tX+offX, tY+offY), pair.getPort2().getCenter(), cell, true, col, false);

					if (taps == null) continue;
					for(PortInst pi : taps)
					{
						EPoint pt = pi.getCenter();
						poly = new Poly(new Rectangle2D.Double(pt.getX()-TAPSIZE, pt.getY()-TAPSIZE, TAPSIZE*2, TAPSIZE*2));
						poly.setStyle(Poly.Type.FILLED);
						h.addPoly(poly, cell, Color.RED);
						if (hor)
						{
							h.addLine(EPoint.fromLambda(pt.getX(), pt.getY()), EPoint.fromLambda(pt.getX(), tY+offY), cell, true, col, false);
						} else
						{
							h.addLine(EPoint.fromLambda(pt.getX(), pt.getY()), EPoint.fromLambda(tX+offX, pt.getY()), cell, true, col, false);
						}
					}
				}
			}
			h.finished();
			wnd.repaint();
		}
	}

	public static void showSpineNetworks(List<RoutesOnNetwork> allRoutes)
	{
		allSpineRoutes = allRoutes;
	}

	/**
	 * Method to tell whether networks are being rewired for least-distance.
	 * @return true if networks are being rewired for least-distance.
	 */
	public static boolean isShowingSpines() { return debugDialog != null && debuggingType == DebugType.SHOWSPINES; }


	/************************************* REWIRE NETWORKS *************************************/

	private static void rewireNets()
	{
		EditWindow wnd = EditWindow.getCurrent();
		Cell cell = wnd.getCell();
		if (cell == null) return;
		highlighter = wnd.getRulerHighlighter();
		new RewireNets(cell);
	}

	public static class RewireNets extends Job
	{
		private Cell cell;
		private EditingPreferences ep;

		public RewireNets(Cell c)
		{
			super("Rewire Networks", User.getUserTool(), Job.Type.CHANGE, null, null, Job.Priority.USER);
			cell = c;
			ep = UserInterfaceMain.getEditingPreferences();
			startJob();
		}

		public boolean doIt() throws JobException
		{
			// get list of selected nets
			List<ArcInst> selected = new ArrayList<ArcInst>();
			for(Iterator<ArcInst> it = cell.getArcs(); it.hasNext(); )
			{
				ArcInst ai = it.next();
				if (ai.getProto() == Generic.tech().unrouted_arc) selected.add(ai);
			}
			if (selected.isEmpty()) return true;

			debuggingType = DebugType.REWIRENETS;
			endADebug = true;
			debugDialog.router = SeaOfGatesEngineFactory.createSeaOfGatesEngine(SeaOfGatesEngineType.defaultVersion);
			SeaOfGatesOptions prefs = new SeaOfGatesOptions();
			prefs.getOptionsFromPreferences(false);
			debugDialog.router.setPrefs(prefs);
			debugDialog.svInfo = new HashMap<SearchVertex,SVState>();
			netNames = new HashMap<Integer,String>();
			svOrder = 1;

			Job job = Job.getRunningJob();
			debugDialog.router.routeIt(SeaOfGatesHandlers.getDefault(cell, debugDialog.router.getPrefs().resultCellName,
				debugDialog.router.getPrefs().contactPlacementAction, job, ep), cell, false, selected);
			return true;
		}
	}

	/**
	 * Method to tell whether networks are being rewired for least-distance.
	 * @return true if networks are being rewired for least-distance.
	 */
	public static boolean isRewireNetworks() { return debugDialog != null && debuggingType == DebugType.REWIRENETS; }

	/************************************* ROUTING DEBUGGING DIALOG *************************************/

	/**
	 * Class to handle the "Routing control" dialog.
	 */
	private static class RoutingDialog extends EModelessDialog
	{
		private SeaOfGatesEngine router;
		private SeaOfGatesCellParameters sogp;
		private Technology tech;
		private Cell cell;
		private Map<SearchVertex,SVState> svInfo;
		private SearchVertex[] seeSV;
		private SearchVertex currentSV;
		private GlobalRouter globalRoutingResults;
		private JLabel routeDescriptionFrom, routeDescriptionTo;
		private JLabel routeResult;
		private JLabel labValue;
		private JLabel grInfo;
		private JComboBox layerToShow;
		private JCheckBox showLayerGrid, routingMode;
		private JTextField whichOne, whichStep;
		private JTextArea[] dirData;
		private JButton[] dirShow;
		private JLabel[] costShow;
		private Font plainFont, boldFont;

		/** Creates new form Debug-Routing */
		public RoutingDialog()
		{
			super(TopLevel.getCurrentJFrame());

			// detect space key
	        final String SPACE_KEY = "space-key";
			KeyStroke space = KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0);
			getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(space, SPACE_KEY);
			getRootPane().getActionMap().put(SPACE_KEY, new AbstractAction()
			{
				public void actionPerformed(ActionEvent event) { spacePressed(); }
			});
			
			seeSV = new SearchVertex[6];
			currentSV = null;
			tech = Technology.getCurrent();
			EditWindow wnd = EditWindow.getCurrent();
			cell = wnd.getCell();
			if (cell != null) sogp = new SeaOfGatesCellParameters(cell);

			// fill in the debug dialog
			getContentPane().setLayout(new GridBagLayout());
			setTitle("Debug Routing");
			setName("");
			addWindowListener(new WindowAdapter() { public void windowClosing(WindowEvent evt) { endDebugging(); } });
			GridBagConstraints gbc;
			int yPos = 0;

			JButton runA = new JButton("Route From A->B");
			runA.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent evt) { showRouting(true); } });
			gbc = new GridBagConstraints();
			gbc.gridx = 0;   gbc.gridy = yPos;
			gbc.weightx = 0.33;
			gbc.insets = new Insets(4, 4, 4, 4);
			getContentPane().add(runA, gbc);

			layerToShow = new JComboBox();
			gbc = new GridBagConstraints();
			gbc.gridx = 1;   gbc.gridy = yPos;
			gbc.weightx = 0.33;
			gbc.anchor = GridBagConstraints.EAST;
			gbc.insets = new Insets(4, 4, 4, 4);
			getContentPane().add(layerToShow, gbc);
			for(Iterator<ArcProto> it = tech.getArcs(); it.hasNext(); )
			{
				ArcProto ap = it.next();
				if (!ap.getFunction().isMetal()) continue;
				String gridData = sogp.getGrid(ap);
				if (gridData == null) continue;
				layerToShow.addItem(ap.getName());
			}

			showLayerGrid = new JCheckBox("Show Grid");
			showLayerGrid.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent evt) { toggleGridDisplay(); } });
			gbc = new GridBagConstraints();
			gbc.gridx = 2;   gbc.gridy = yPos;
			gbc.weightx = 0.33;
			gbc.anchor = GridBagConstraints.WEST;
			gbc.insets = new Insets(4, 4, 4, 4);
			getContentPane().add(showLayerGrid, gbc);

			yPos++;

			JButton runB = new JButton("Route From B->A");
			runB.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent evt) { showRouting(false); } });
			gbc = new GridBagConstraints();
			gbc.gridx = 0;   gbc.gridy = yPos;
			gbc.weightx = 0.33;
			gbc.insets = new Insets(4, 4, 4, 4);
			getContentPane().add(runB, gbc);

			JLabel lab2 = new JLabel("Route to Debug:");
			gbc = new GridBagConstraints();
			gbc.gridx = 1;   gbc.gridy = yPos;
			gbc.weightx = 0.33;
			gbc.anchor = GridBagConstraints.EAST;
			gbc.insets = new Insets(4, 4, 4, 4);
			getContentPane().add(lab2, gbc);

			whichOne = new JTextField("");
			gbc = new GridBagConstraints();
			gbc.gridx = 2;   gbc.gridy = yPos;
			gbc.weightx = 0.33;
			gbc.fill = GridBagConstraints.HORIZONTAL;
			gbc.insets = new Insets(4, 4, 4, 4);
			getContentPane().add(whichOne, gbc);

			yPos++;

			routeDescriptionFrom = new JLabel("");
			gbc = new GridBagConstraints();
			gbc.gridx = 0;   gbc.gridy = yPos;
			gbc.gridwidth = 3;
			gbc.fill = GridBagConstraints.HORIZONTAL;
			gbc.insets = new Insets(4, 4, 4, 4);
			gbc.weightx = 1;
			getContentPane().add(routeDescriptionFrom, gbc);

			yPos++;

			routeDescriptionTo = new JLabel("");
			gbc = new GridBagConstraints();
			gbc.gridx = 0;   gbc.gridy = yPos;
			gbc.gridwidth = 3;
			gbc.fill = GridBagConstraints.HORIZONTAL;
			gbc.insets = new Insets(4, 4, 4, 4);
			gbc.weightx = 1;
			getContentPane().add(routeDescriptionTo, gbc);

			yPos++;

			routeResult = new JLabel("");
			gbc = new GridBagConstraints();
			gbc.gridx = 0;   gbc.gridy = yPos;
			gbc.gridwidth = 3;
			gbc.fill = GridBagConstraints.HORIZONTAL;
			gbc.insets = new Insets(4, 4, 4, 4);
			gbc.weightx = 1;
			getContentPane().add(routeResult, gbc);

			yPos++;

			JPanel panel = makeSVPanel();
			gbc = new GridBagConstraints();
			gbc.gridx = 0;   gbc.gridy = yPos;
			gbc.gridwidth = 3;
			gbc.fill = GridBagConstraints.BOTH;
			gbc.insets = new Insets(4, 4, 4, 4);
			gbc.weightx = 1;  gbc.weighty = 0.5;
			getContentPane().add(panel, gbc);

			yPos++;

			JButton doGlobalRouting = new JButton("Show Global Routing");
			doGlobalRouting.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent evt) { doGlobalRouting(); } });
			gbc = new GridBagConstraints();
			gbc.gridx = 0;   gbc.gridy = yPos;
			gbc.weightx = 0.3;
			gbc.insets = new Insets(4, 4, 4, 4);
			getContentPane().add(doGlobalRouting, gbc);

			JButton rewire = new JButton("Rewire for Routing");
			rewire.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent evt) { rewireNets(); } });
			gbc = new GridBagConstraints();
			gbc.gridx = 1;   gbc.gridy = yPos;
			gbc.weightx = 0.3;
			gbc.insets = new Insets(4, 4, 4, 4);
			getContentPane().add(rewire, gbc);

			JButton showSpines = new JButton("Show Spines");
			showSpines.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent evt) { showSpines(); } });
			gbc = new GridBagConstraints();
			gbc.gridx = 2;   gbc.gridy = yPos;
			gbc.weightx = 0.3;
			gbc.insets = new Insets(4, 4, 4, 4);
			getContentPane().add(showSpines, gbc);

			yPos++;

			JPanel grPanel = new JPanel();
			grPanel.setLayout(new GridBagLayout());
			grPanel.setBorder(BorderFactory.createTitledBorder("Global Routing"));
			gbc = new GridBagConstraints();
			gbc.gridx = 0;   gbc.gridy = yPos++;
			gbc.gridwidth = 3;
			gbc.fill = GridBagConstraints.BOTH;
			gbc.insets = new Insets(4, 4, 4, 4);
			gbc.weightx = 1;
			getContentPane().add(grPanel, gbc);
			grInfo = new JLabel("");
			gbc = new GridBagConstraints();
			gbc.gridx = 0;   gbc.gridy = 0;
			gbc.fill = GridBagConstraints.BOTH;
			gbc.weightx = gbc.weighty = 1;
			gbc.insets = new Insets(4, 4, 4, 4);
			grPanel.add(grInfo, gbc);
			yPos++;

			routingMode = new JCheckBox("Routing Mode");
			routingMode.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent evt) { toggleRoutingMode(); } });
			gbc = new GridBagConstraints();
			gbc.gridx = 1;   gbc.gridy = yPos;
			gbc.insets = new Insets(4, 4, 4, 4);
			getContentPane().add(routingMode, gbc);

			pack();
			finishInitialization();
			setVisible(true);

//			// guess which arc is being routed
//			ArcInst routeAI = null;
//			if (cell != null)
//			{
//				List<String> netsToRoute = sogp.getNetsToRoute();
//				if (netsToRoute != null && netsToRoute.size() > 0)
//				{
//					String netName = netsToRoute.get(0);
//					routeAI = cell.findArc(netName);
//				}
//			}
//			Highlighter h = wnd.getHighlighter();
//			List<ArcInst> arcs = h.getHighlightedArcs();
//			if (arcs.size() == 1)
//			{
//				ArcInst ai = arcs.get(0);
//				if (ai.getProto() == Generic.tech().unrouted_arc)
//					routeAI = ai;
//			}
//			if (routeAI != null)
//				showAandB(routeAI);
		}

		private JPanel makeSVPanel()
		{
			JPanel panel = new JPanel();
			panel.setLayout(new GridBagLayout());
			panel.setBorder(BorderFactory.createTitledBorder("Routing Steps"));
			GridBagConstraints gbc;

			JButton backButton = new JButton("Back");
			backButton.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent evt) { showBack(); } });
			gbc = new GridBagConstraints();
			gbc.gridx = 0;   gbc.gridy = 0;
			gbc.fill = GridBagConstraints.HORIZONTAL;
			gbc.insets = new Insets(4, 4, 4, 4);
			panel.add(backButton, gbc);

			JButton nextButton = new JButton("Next");
			nextButton.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent evt) { showNext(); } });
			gbc = new GridBagConstraints();
			gbc.gridx = 1;   gbc.gridy = 0;
			gbc.fill = GridBagConstraints.HORIZONTAL;
			gbc.insets = new Insets(4, 4, 4, 4);
			panel.add(nextButton, gbc);

			whichStep = new JTextField("");
			whichStep.setColumns(5);
			gbc = new GridBagConstraints();
			gbc.gridx = 2;   gbc.gridy = 0;
			gbc.fill = GridBagConstraints.HORIZONTAL;
			gbc.anchor = GridBagConstraints.EAST;
			gbc.insets = new Insets(4, 4, 4, 1);
			panel.add(whichStep, gbc);

			JButton goButton = new JButton("Show Routing Step");
			goButton.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent evt) { showStepButton(); } });
			gbc = new GridBagConstraints();
			gbc.gridx = 3;   gbc.gridy = 0;
			gbc.anchor = GridBagConstraints.WEST;
			gbc.insets = new Insets(4, 2, 4, 4);
			panel.add(goButton, gbc);

			labValue = new JLabel("");
			gbc = new GridBagConstraints();
			gbc.gridx = 0;   gbc.gridy = 1;
			gbc.gridwidth = 4;
			gbc.weightx = 1;
			gbc.fill = GridBagConstraints.HORIZONTAL;
			gbc.anchor = GridBagConstraints.WEST;
			gbc.insets = new Insets(4, 4, 4, 4);
			panel.add(labValue, gbc);

			dirData = new JTextArea[6];
			dirShow = new JButton[6];
			costShow = new JLabel[6];
			for(int i=0; i<6; i++)
			{
				dirShow[i] = new JButton("See");
				gbc = new GridBagConstraints();
				gbc.gridx = 0;   gbc.gridy = i*2+2;
				gbc.gridheight = 2;
				gbc.anchor = GridBagConstraints.NORTH;
				gbc.fill = GridBagConstraints.HORIZONTAL;
				gbc.insets = new Insets(4, 4, 4, 4);
				panel.add(dirShow[i], gbc);

				String lab = "";
				switch (i)
				{
					case 0: lab = "-X";   break;
					case 1: lab = "+X";   break;
					case 2: lab = "-Y";   break;
					case 3: lab = "+Y";   break;
					case 4: lab = "Down"; break;
					case 5: lab = "Up";   break;
				}
				JLabel dirLabel = new JLabel(lab);
				gbc = new GridBagConstraints();
				gbc.gridx = 1;   gbc.gridy = i*2+2;
				gbc.weightx = 0.1;
				gbc.anchor = GridBagConstraints.SOUTH;
				gbc.fill = GridBagConstraints.HORIZONTAL;
				gbc.insets = new Insets(4, 4, 0, 4);
				panel.add(dirLabel, gbc);

				costShow[i] = new JLabel("");
				gbc = new GridBagConstraints();
				gbc.gridx = 1;   gbc.gridy = i*2+3;
				gbc.weightx = 0.1;
				gbc.anchor = GridBagConstraints.NORTH;
				gbc.insets = new Insets(0, 4, 4, 4);
				panel.add(costShow[i], gbc);

				dirData[i] = new JTextArea("");
				dirData[i].setEditable(false);
				dirData[i].setCursor(null);
				dirData[i].setOpaque(false);
				dirData[i].setFocusable(true);
				dirData[i].setLineWrap(true);
				dirData[i].setFont(UIManager.getFont("Label.font"));
				gbc = new GridBagConstraints();
				gbc.gridx = 2;   gbc.gridy = i*2+2;
				gbc.gridwidth = 2;
				gbc.gridheight = 2;
				gbc.weightx = 0.9;
				gbc.weighty = 0.2;
				gbc.anchor = GridBagConstraints.NORTHWEST;
				gbc.fill = GridBagConstraints.BOTH;
				gbc.insets = new Insets(4, 4, 4, 4);
				panel.add(dirData[i], gbc);
			}
			dirShow[0].addActionListener(new ActionListener() { public void actionPerformed(ActionEvent evt) { see(0); } });
			dirShow[1].addActionListener(new ActionListener() { public void actionPerformed(ActionEvent evt) { see(1); } });
			dirShow[2].addActionListener(new ActionListener() { public void actionPerformed(ActionEvent evt) { see(2); } });
			dirShow[3].addActionListener(new ActionListener() { public void actionPerformed(ActionEvent evt) { see(3); } });
			dirShow[4].addActionListener(new ActionListener() { public void actionPerformed(ActionEvent evt) { see(4); } });
			dirShow[5].addActionListener(new ActionListener() { public void actionPerformed(ActionEvent evt) { see(5); } });
			plainFont = dirShow[0].getFont();
			boldFont = plainFont.deriveFont(Font.BOLD);
			return panel;
		}

		private void setRouteDescription(String desc1, String desc2)
		{
			routeDescriptionFrom.setText(desc1);
			routeDescriptionTo.setText(desc2);
		}

		/**
		 * Method called when user clicks on one of the six "See" buttons to see a next direction.
		 * @param index the index (0-5) of the "See" button that was clicked.
		 */
		private void see(int index)
		{
			SearchVertex sv = seeSV[index];
			if (sv == null) return;
			seeSelectedSV(sv);
		}

		/**
		 * Method to toggle the display of grids.
		 */
		private void toggleGridDisplay()
		{
			String arcName = (String)layerToShow.getSelectedItem();
			ArcProto ap = tech.findArcProto(arcName);
			if (ap == null)
			{
				System.out.println("ERROR: Cannot find arc '" + arcName + " in technology " + tech.getTechName());
				return;
			}
			String grid = sogp.getGrid(ap);
			if (grid == null) return;

			EditWindow wnd = EditWindow.getCurrent();
			if (wnd == null) return;
			Highlighter h = wnd.getHighlighter();
			h.clear();
			if (showLayerGrid.isSelected())
			{
				List<Double> coords = new ArrayList<Double>();
				String[] parts = grid.split(",");
				for(int i=0; i<parts.length; i++)
				{
					String part = parts[i].trim();
					if (part.length() == 0) continue;
					if (!Character.isDigit(part.charAt(part.length()-1)))
						part = part.substring(0, part.length()-1);
					double val = TextUtils.atof(part);
					coords.add(new Double(val));
				}

				boolean hor = true;
				if (sogp.isHorizontalEven())
				{
					if ((ap.getFunction().getLevel()%2) != 0) hor = false;
				} else
				{
					if ((ap.getFunction().getLevel()%2) == 0) hor = false;
				}
				SeaOfGatesCell.showGrid(wnd, h, coords, hor, wnd.getScale(), -1);
			}
			h.finished();
			wnd.repaint();
		}

		/**
		 * Method called when user clicks on the "Back" button to see the previous SearchVertex.
		 */
		private void showBack()
		{
			if (currentSV == null || currentSV.getLast() == null) return;
			seeSelectedSV(currentSV.getLast());
		}

		private void toggleRoutingMode()
		{
			if (routingMode.isSelected())
			{
				// enable routing mode in click/zoom/wire listener
				User.setRoutingMode(true);
				ToolBar.setCursorMode(CursorMode.ROUTING);
			} else
			{
				// disable routing mode in click/zoom/wire listener
				User.setRoutingMode(false);
				if (ToolBar.getCursorMode() == CursorMode.ROUTING)
					ToolBar.setCursorMode(CursorMode.CLICKZOOMWIRE);
			}
		}

		/**
		 * Method called when user clicks on the "Next" button to see the previous SearchVertex.
		 */
		private void showNext()
		{
			if (debugDialog == null || debugDialog.svInfo == null) return;

			// cycle through objects
			SearchVertex foundSV = null;
			if (currentSV ==  null) return;
			SVState svs = svInfo.get(currentSV);
			String stepNum;
			if (svs.message.equals("START")) stepNum = "1"; else
				stepNum = (TextUtils.atoi(svs.message) + 1) + "";
			for(SearchVertex sv : debugDialog.svInfo.keySet())
			{
				svs = svInfo.get(sv);
				if (svs.message.equals(stepNum)) { foundSV = sv;   break; }
			}
			if (foundSV != null)
			{
				seeSelectedSV(foundSV);
			} else
			{
				System.out.println("No Routing Step numbered " + stepNum);
			}
		}

		/**
		 * Method called when user clicks the "Show Routing Step" button to see a specified point in the route plan.
		 */
		private void showStepButton()
		{
			if (debugDialog == null || debugDialog.svInfo == null) return;

			// find the specified routing step
			String stepNum = whichStep.getText().trim();
			SearchVertex foundSV = null;
			for(SearchVertex sv : debugDialog.svInfo.keySet())
			{
				SVState svs = svInfo.get(sv);
				if (svs.message.equals(stepNum)) { foundSV = sv;   break; }
			}
			if (foundSV != null)
			{
				seeSelectedSV(foundSV);
			} else
			{
				System.out.println("No Routing Step numbered " + stepNum);
			}
		}

		/**
		 * Method to load information about a SearchVertex into the dialog.
		 * @param sv the SearchVertex that is being explained.
		 */
		private void seeSelectedSV(SearchVertex sv)
		{
			if (debugDialog == null) return;
			EditWindow wnd = EditWindow.getCurrent();
			Highlighter h = wnd.getRulerHighlighter();
			if (currentSV != null)
			{
				SVState oldSVS = svInfo.get(currentSV);
				if (oldSVS != null) oldSVS.setBackgroundColor(null, h);
			}
			currentSV = sv;
			SVState svs = svInfo.get(sv);
			svs.setBackgroundColor(Color.WHITE, h);

			for(int i=0; i<6; i++)
			{
				dirData[i].setText("");
				dirShow[i].setText("See");
				dirShow[i].setEnabled(false);
				dirShow[i].setFont(plainFont);
				costShow[i].setText("");
			}
			Highlight.Message hMsg = (Highlight.Message)svs.label;
			if (svs.details == null)
			{
				String msg = "At (" + TextUtils.formatDistance(sv.getX()) + "," + TextUtils.formatDistance(sv.getY()) + "," + sv.describeMetal() +
					"), Cost=" + sv.getCost();
				if (sv.getGRBucket() < 0) msg += ", NO Global Routing"; else
					msg += ", Global Routing Bucket: " + sv.getGRBucket();
				if (sv.getLast() != null)
				{
					SVState svsLast = svInfo.get(sv.getLast());
					Highlight.Message hMsgLast = (Highlight.Message)svsLast.label;
					msg += ", previous point " + hMsgLast.getInfo() + " at (" + TextUtils.formatDistance(sv.getLast().getX()) + "," +
						TextUtils.formatDistance(sv.getLast().getY()) + "," + sv.getLast().describeMetal() + ")";
				}
				labValue.setText(hMsg.getInfo() + ": " + msg + ", DID NOT GET PROPAGATED");
			} else
			{
				String lab = hMsg.getInfo() + ": " + svs.details[0];
				if (sv.getLast() != null)
				{
					SVState svsLast = svInfo.get(sv.getLast());
					Highlight.Message hMsgLast = (Highlight.Message)svsLast.label;
					lab += ", previous point " + hMsgLast.getInfo() + " at (" + TextUtils.formatDistance(sv.getLast().getX()) + "," +
						TextUtils.formatDistance(sv.getLast().getY()) + "," + sv.getLast().describeMetal() + ")";
				}
				if (onPath.contains(sv)) lab += ", ON FINAL PATH";
				labValue.setText(lab);
				for(int i=0; i<6; i++)
				{
					if (svs.details[i+1] == null) continue;
					if (svs.details[i+1].indexOf('|') >= 0)
					{
						String leading = "> ";
						String [] subParts = svs.details[i+1].split("\\|");
						String msg = subParts[0];
						for(int j=1; j<subParts.length; j++)
							msg += "\n" + (leading + subParts[j]);
						dirData[i].setText(msg);
					} else
						dirData[i].setText(svs.details[i+1]);

					seeSV[i] = svs.nextVertices[i];
					if (seeSV[i] != null)
					{
						SVState svsNext = svInfo.get(seeSV[i]);
						if (svsNext == null) dirShow[i].setText("?"); else
						{
							Highlight.Message hMsgNext = (Highlight.Message)svsNext.label;
							dirShow[i].setText(hMsgNext.getInfo());
							dirShow[i].setEnabled(true);
							if (onPath.contains(seeSV[i])) dirShow[i].setFont(boldFont);
						}
						costShow[i].setText("Cost: " + seeSV[i].getCost());
					}
				}
			}
			if (sv.getWavefront().getGRDirection() == 0)
			{
				grInfo.setText("No Global Routing data");
			} else
			{
				Wavefront wf = sv.getWavefront();
				NeededRoute nr = wf.getNeededRoute();
				Rectangle2D[] buckets = nr.getGRBuckets();
				Rectangle2D[] orderedBuckets = wf.getOrderedBuckets();
				String msg = "<html>";
				for(int b=0; b<orderedBuckets.length; b++)
					msg += "Bucket "+b+" is "+TextUtils.formatDistance(buckets[b].getMinX())+"&lt;=X&lt;="+TextUtils.formatDistance(buckets[b].getMaxX())+" and "+
						TextUtils.formatDistance(buckets[b].getMinY())+"&lt;=Y&lt;="+TextUtils.formatDistance(buckets[b].getMaxY()) + "<p>";
				for(int b=0; b<orderedBuckets.length; b++)
					msg += "Ordered Bucket "+b+" is "+TextUtils.formatDistance(orderedBuckets[b].getMinX())+"&lt;=X&lt;="+TextUtils.formatDistance(orderedBuckets[b].getMaxX())+" and "+
						TextUtils.formatDistance(orderedBuckets[b].getMinY())+"&lt;=Y&lt;="+TextUtils.formatDistance(orderedBuckets[b].getMaxY()) + "<p>";
				msg += "</html>";
				grInfo.setText(msg);
			}

			wnd.fullRepaint();
			pack();
		}

		protected void escapePressed() { endDebugging(); }

		/**
		 * Method to respond when the space key is pressed.
		 * Finds highlighted text and shows those coordinates.
		 */
		protected void spacePressed()
		{
			Component comp = getFocusOwner();
			if (!(comp instanceof JTextArea)) return;
			JTextArea ta = (JTextArea)comp;
			int start = ta.getSelectionStart();
			int end = ta.getSelectionEnd();
			String addr = ta.getText().substring(start, end).toLowerCase();

			// parse coordinate
			int commaPos = addr.indexOf(',');
			if (commaPos >= 0)
			{
				double x = TextUtils.atofDistance(addr.substring(0, commaPos));
				double y = TextUtils.atofDistance(addr.substring(commaPos+1));
				Point2D p1 = new Point2D.Double(x-5, y-5);
				Point2D p2 = new Point2D.Double(x-5, y+5);
				Point2D p3 = new Point2D.Double(x+5, y+5);
				Point2D p4 = new Point2D.Double(x+5, y-5);

				EditWindow wnd = EditWindow.getCurrent();
				Cell cell = wnd.getCell();
				Highlighter h = wnd.getHighlighter();
				h.clear();
				h.addLine(p1, p3, cell, true, Color.RED, true);
				h.addLine(p2, p4, cell, true, Color.RED, true);
				h.finished();
				wnd.repaint();
				return;
			}

			// parse rectangle
			int andPos = addr.indexOf(" and ");
			int xPos = addr.indexOf("<=x<=");
			int yPos = addr.indexOf("<=y<=");
			if (andPos >= 0 && xPos >= 0 && yPos >= 0)
			{
				double lX = TextUtils.atofDistance(addr.substring(0, xPos));
				double hX = TextUtils.atofDistance(addr.substring(xPos+5, andPos));
				double lY = TextUtils.atofDistance(addr.substring(andPos+5, yPos));
				double hY = TextUtils.atofDistance(addr.substring(yPos+5));
				Point2D p1 = new Point2D.Double(lX, lY);
				Point2D p2 = new Point2D.Double(lX, hY);
				Point2D p3 = new Point2D.Double(hX, hY);
				Point2D p4 = new Point2D.Double(hX, lY);

				EditWindow wnd = EditWindow.getCurrent();
				Cell cell = wnd.getCell();
				Highlighter h = wnd.getHighlighter();
				h.clear();
				h.addLine(p1, p2, cell, true, Color.RED, true);
				h.addLine(p2, p3, cell, true, Color.RED, true);
				h.addLine(p3, p4, cell, true, Color.RED, true);
				h.addLine(p4, p1, cell, true, Color.RED, true);
				h.addLine(p1, p3, cell, true, Color.RED, true);
				h.addLine(p2, p4, cell, true, Color.RED, true);
				h.finished();
				wnd.repaint();
				return;
			}

			// don't know how to parse
			Job.getUserInterface().showInformationMessage("CANNOT PARSE: "+addr, "SELECTION ERROR");
		}

		private void showGlobalRoutingGrid()
		{
			// show grid lines around buckets
			EditWindow wnd = EditWindow.getCurrent();
			Cell cell = wnd.getCell();
			Highlighter h = wnd.getRulerHighlighter();
			ERectangle bounds = cell.getBounds();
			double bucketWidth = bounds.getWidth() / globalRoutingResults.getXBuckets();
			double bucketHeight = bounds.getHeight() / globalRoutingResults.getYBuckets();
			for(int x=0; x<=globalRoutingResults.getXBuckets(); x++)
			{
				double xPos = bounds.getMinX() + x * bucketWidth;
				h.addLine(EPoint.fromLambda(xPos, bounds.getMinY()), EPoint.fromLambda(xPos, bounds.getMaxY()), cell, false, Color.RED, false);
			}
			for(int y=0; y<=globalRoutingResults.getYBuckets(); y++)
			{
				double yPos = bounds.getMinY() + y * bucketHeight;
				h.addLine(EPoint.fromLambda(bounds.getMinX(), yPos), EPoint.fromLambda(bounds.getMaxX(), yPos), cell, false, Color.RED, false);
			}
		}

		private void showGlobalRoutingPath(NeededRoute nr)
		{
			// show global routes
			EditWindow wnd = EditWindow.getCurrent();
			Cell cell = wnd.getCell();
			Highlighter h = wnd.getRulerHighlighter();
			Set<Integer> xUsed = new HashSet<Integer>();
			Set<Integer> yUsed = new HashSet<Integer>();
			for (GRNet net : debugDialog.globalRoutingResults.getNets())
			{
				for (GRWire w : net.getWires())
				{
					if (nr != null && w.getNeededRoute() != nr) continue;
					EPoint p1 = w.getPoint1();
					EPoint p2 = w.getPoint2();
					GRBucket n1 = w.getBucket1();
					GRBucket n2 = w.getBucket2();

					GRBucket prev = null;
					double prevX = 0, prevY = 0;
					for (int i=0; i<w.getNumPathElements(); i++)
					{
						GRBucket n = w.getPathBucket(i);
						Rectangle2D bucketBound = n.getBounds();
						double x = bucketBound.getCenterX(), y = bucketBound.getCenterY();
						boolean adjusted = false;
						if (n == n1) { x = p1.getX();   y = p1.getY();  adjusted = true; }
						if (n == n2) { x = p2.getX();   y = p2.getY();  adjusted = true; }
						if (!adjusted)
						{
							if (i > 0)
							{
								if (w.getPathBucket(i-1).getBounds().getCenterX() == x)
								{
									if (w.getPathBucket(i-1) == n1) x = p1.getX();
									if (w.getPathBucket(i-1) == n2) x = p2.getX();
								}
								if (w.getPathBucket(i-1).getBounds().getCenterY() == y)
								{
									if (w.getPathBucket(i-1) == n1) y = p1.getY();
									if (w.getPathBucket(i-1) == n2) y = p2.getY();
								}
							}
							if (i < w.getNumPathElements()-1)
							{
								if (w.getPathBucket(i+1).getBounds().getCenterX() == x)
								{
									if (w.getPathBucket(i+1) == n1) x = p1.getX();
									if (w.getPathBucket(i+1) == n2) x = p2.getX();
								}
								if (w.getPathBucket(i+1).getBounds().getCenterY() == y)
								{
									if (w.getPathBucket(i+1) == n1) y = p1.getY();
									if (w.getPathBucket(i+1) == n2) y = p2.getY();
								}
							}
							for(;;)
							{
								Integer xi = Integer.valueOf((int)x);
								if (xUsed.contains(xi)) { x++; continue; }
								xUsed.add(xi);
								break;
							}
							for(;;)
							{
								Integer yi = Integer.valueOf((int)y);
								if (yUsed.contains(yi)) { y++; continue; }
								yUsed.add(yi);
								break;
							}
						}
						if (prev != null)
						{
							h.addLine(EPoint.fromLambda(prevX, prevY), EPoint.fromLambda(x, y), cell, false, Color.GREEN, false);
						}
						if (i == 0 || i == w.getNumPathElements()-1)
						{
							int xSize = 2;
							h.addLine(EPoint.fromLambda(x-xSize, y-xSize), EPoint.fromLambda(x+xSize, y+xSize), cell, false, Color.GREEN, false);
							h.addLine(EPoint.fromLambda(x-xSize, y+xSize), EPoint.fromLambda(x+xSize, y-xSize), cell, false, Color.GREEN, false);
						}
						prev = n;
						prevX = x;   prevY = y;
					}
				}
			}
			h.finished();
			wnd.repaint();
		}

		/**
		 * Method to show the resulting path (which may be a failure).
		 * @param result the SearchVertex at the end (may indicate failure).
		 * @param cell the Cell in which routing happened.
		 * @param h the Highlighter for showing the result.
		 */
		private void showPathToGoal(NeededRoute nr, SearchVertex result, Cell cell, Highlighter h)
		{
			EPoint goalCoord = null;
			if (result == SeaOfGatesEngine.svAborted)
			{
				routeResult.setText("Result: Aborted by user");
			} else if (result == SeaOfGatesEngine.svExhausted)
			{
				routeResult.setText("Result: Examined all possibilities");
			} else if (result == SeaOfGatesEngine.svLimited)
			{
				routeResult.setText("Result: Stopped after " + router.getPrefs().complexityLimit + " steps");
			} else
			{
				routeResult.setText("Result: Success!");
				SVState svs = ensureDebuggingShadow(result, false);
				svs.changeLabel("!!GOAL!!", h);
				goalCoord = EPoint.fromLambda(result.getX(), result.getY());
				for(;;)
				{
					SearchVertex svLast = result.getLast();
					if (svLast == null) break;
					if (result.getZ() != svLast.getZ())
					{
						int lowZ = Math.min(result.getZ(), svLast.getZ());
						int highZ = Math.max(result.getZ(), svLast.getZ());
						double lowOff = lowZ * layerOffset;
						double highOff = highZ * layerOffset;
						h.addLine(EPoint.fromLambda(result.getX()+lowOff, result.getY()+lowOff+goalWidth),
							EPoint.fromLambda(result.getX()+highOff-goalWidth, result.getY()+highOff), cell, true, Color.WHITE, false);
						h.addLine(EPoint.fromLambda(result.getX()+lowOff+goalWidth, result.getY()+lowOff),
							EPoint.fromLambda(result.getX()+highOff, result.getY()+highOff-goalWidth), cell, true, Color.WHITE, false);
					} else
					{
						double off = result.getZ() * layerOffset;
						if (result.getX() != svLast.getX())
						{
							// horizontal line
							h.addLine(EPoint.fromLambda(result.getX()+off, result.getY()+off-goalWidth),
								EPoint.fromLambda(svLast.getX()+off, svLast.getY()+off-goalWidth), cell, true, Color.WHITE, false);
							h.addLine(EPoint.fromLambda(result.getX()+off, result.getY()+off+goalWidth),
								EPoint.fromLambda(svLast.getX()+off, svLast.getY()+off+goalWidth), cell, true, Color.WHITE, false);
						} else
						{
							// vertical line
							h.addLine(EPoint.fromLambda(result.getX()+off-goalWidth, result.getY()+off),
								EPoint.fromLambda(svLast.getX()+off-goalWidth, svLast.getY()+off), cell, true, Color.WHITE, false);
							h.addLine(EPoint.fromLambda(result.getX()+off+goalWidth, result.getY()+off),
								EPoint.fromLambda(svLast.getX()+off+goalWidth, svLast.getY()+off), cell, true, Color.WHITE, false);
						}
					}
					result = svLast;
				}
			}

			// show the possible endpoints
			PossibleEndpoints endChoices = endADebug ? nr.getBPossibleEndpoints() :  nr.getAPossibleEndpoints();
			for(PossibleEndpoint pe : endChoices.getEndpoints())
			{
				EPoint pt = pe.getCoord();
				if (goalCoord != null && pt.getX() == goalCoord.getX() && pt.getY() == goalCoord.getY()) continue;
				EPoint ptOff = EPoint.fromLambda(pt.getX() + possibleGoalOffset, pt.getY() + possibleGoalOffset);
				h.addLine(EPoint.fromLambda(ptOff.getX(), ptOff.getY()), EPoint.fromLambda(pt.getX(), pt.getY()), cell, true, Color.BLACK, false);
				h.addMessage(cell, "G", ptOff);
			}
		}

//		private void showAandB(ArcInst ai)
//		{
//			EPoint head = ai.getHeadLocation();
//			EPoint tail = ai.getTailLocation();
//			String fromMsg = "A: (" + TextUtils.formatDistance(head.getX()) + "," + TextUtils.formatDistance(head.getY()) +
//				"): port " + ai.getHeadPortInst().getPortProto().getName() + " of node " + ai.getHeadPortInst().getNodeInst().describe(false);
//			String toMsg = "B: (" + TextUtils.formatDistance(tail.getX()) + "," + TextUtils.formatDistance(tail.getY()) +
//				"): port " + ai.getTailPortInst().getPortProto().getName() + " of node " + ai.getTailPortInst().getNodeInst().describe(false);
//			setRouteDescription(fromMsg, toMsg);
//		}

		private void showSearchVertices(Cell cell, Highlighter h, Wavefront wf)
		{
			PortInst piF = wf.getFromPortInst();
			PortInst piT = wf.getToPortInst();
			NeededRoute nr = wf.getNeededRoute();
			boolean aToB = wf.isAtoB();
			double fromTaperWidth, toTaperWidth, fromTaperLength, toTaperLength;
			if (aToB)
			{
				fromTaperWidth = nr.getATaperWidth();
				toTaperWidth = nr.getBTaperWidth();
				fromTaperLength = nr.getATaperLength();
				toTaperLength = nr.getBTaperLength();
			} else
			{
				fromTaperWidth = nr.getBTaperWidth();
				toTaperWidth = nr.getATaperWidth();
				fromTaperLength = nr.getBTaperLength();
				toTaperLength = nr.getATaperLength();
			}

			String fromMsg = "FROM: (" + TextUtils.formatDistance(wf.getFromX()) + "," + TextUtils.formatDistance(wf.getFromY()) +
				", " + SeaOfGatesEngine.describeMetal(wf.getFromZ(), wf.getFromMask()) + "): port " + piF.getPortProto().getName() + " of node " + piF.getNodeInst().describe(false);
			if (fromTaperLength > 0) fromMsg += ", Taper width is " + TextUtils.formatDistance(fromTaperWidth);
			String toMsg = "TO: (" + TextUtils.formatDistance(wf.getTo().getCenterX()) + "," + TextUtils.formatDistance(wf.getTo().getCenterY()) +
				", " + SeaOfGatesEngine.describeMetal(wf.getToZ(), wf.getToMask()) + "): port " + piT.getPortProto().getName() + " of node " + piT.getNodeInst().describe(false);
			if (toTaperLength > 0) toMsg += ", Taper width is " + TextUtils.formatDistance(toTaperWidth);
			setRouteDescription(fromMsg, toMsg);

			// draw the search vertices
			Map<String,Integer> lowestZ = new HashMap<String,Integer>();
			Map<Integer, Map<Integer,SearchVertex>>[] searchVertexPlanes = wf.getSearchVertexPlanes();
			for(int z=0; z<router.getNumMetals(); z++)
			{
				Map<Integer, Map<Integer,SearchVertex>> plane = searchVertexPlanes[z];
				if (plane == null) continue;
				for(Integer y : plane.keySet())
				{
					Map<Integer,SearchVertex> row = plane.get(y);
					for(Integer x : row.keySet())
					{
						SearchVertex sv = row.get(x);
						SVState svs = ensureDebuggingShadow(sv, false);
						svs.showLabel(h);

						if (sv.getLast() == null) continue;

						if (sv.getZ() != sv.getLast().getZ())
						{
							// draw white line at angle showing change of layer
							int lowZ = Math.min(sv.getZ(), sv.getLast().getZ());
							int highZ = Math.max(sv.getZ(), sv.getLast().getZ());
							double lowOff = lowZ * layerOffset;
							double highOff = highZ * layerOffset;
							h.addLine(EPoint.fromLambda(sv.getX()+lowOff, sv.getY()+lowOff),
								EPoint.fromLambda(sv.getX()+highOff, sv.getY()+highOff), cell, true, Color.WHITE, false);
						} else
						{
							// draw line in proper metal color showing the motion
							double off = sv.getZ() * layerOffset;
							Color col = router.getPrimaryMetalLayer(sv.getZ()).getGraphics().getColor();
							h.addLine(EPoint.fromLambda(sv.getX()+off, sv.getY()+off),
								EPoint.fromLambda(sv.getLast().getX()+off, sv.getLast().getY()+off), cell, false, col, false);
						}

						// remember lowest Z coordinate at this place so that anchor line can be drawn if it is above Metal-1
						String coordLoc = TextUtils.formatDistance(sv.getX()) + "/" + TextUtils.formatDistance(sv.getY());
						Integer height = lowestZ.get(coordLoc);
						int lowZ = Math.min(sv.getZ(), sv.getLast().getZ());
						if (height == null) height = Integer.valueOf(lowZ); else
						{
							int lowest = Math.min(height.intValue(), lowZ);
							height = Integer.valueOf(lowest);
						}
						lowestZ.put(coordLoc, height);
					}
				}
			}

			// draw anchor lines from the lowest Z coordinates if they are not at Metal-1
			for(String loc : lowestZ.keySet())
			{
				Integer height = lowestZ.get(loc);
				if (height.intValue() <= 0) continue;
				String [] locCoords = loc.split("/");
				double x = TextUtils.atof(locCoords[0]);
				double y = TextUtils.atof(locCoords[1]);
				double off = height.intValue() * layerOffset;
				h.addLine(EPoint.fromLambda(x, y), EPoint.fromLambda(x+off, y+off), cell, false, Color.BLACK, false);
			}
		}
	}

	public static SVState ensureDebuggingShadow(SearchVertex sv, boolean start)
	{
		if (debugDialog == null) return null;

		SVState svs = debugDialog.svInfo.get(sv);
		if (svs == null)
		{
			String msg;
			if (start) msg = "START"; else
				msg = (svOrder++) + "";
			svs = new SVState(sv, msg);
			debugDialog.svInfo.put(sv, svs);
		}
		return svs;
	}

	/************************************* SHADOW STRUCTURE WITH ADDITIONAL INFORMATION SearchVertices *************************************/

	private static class SVState
	{
		EPoint anchor;
		String message;
		Highlight label;
		String[] details;
		SearchVertex[] nextVertices = new SearchVertex[6];

		SVState(SearchVertex sv, String msg)
		{
			double off = sv.getZ() * layerOffset;
			anchor = EPoint.fromLambda(sv.getX()+off, sv.getY()+off);
			if (msg == null) msg = sv.describeMetal();
			message = msg;
			label = highlighter.addMessage(cell, message, anchor);
		}

		void showLabel(Highlighter h)
		{
			if (label != null) h.remove(label);
			label = h.addMessage(cell, message, anchor);
		}

		void changeLabel(String msg, Highlighter h)
		{
			message = msg;
			showLabel(h);
		}

		void setBackgroundColor(Color backgroundColor, Highlighter h)
		{
			if (label != null) h.remove(label);
			label = h.addMessage(cell, message, anchor, 0, backgroundColor);
		}
	}

}
