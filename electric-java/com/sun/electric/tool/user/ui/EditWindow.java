/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: EditWindow.java
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
package com.sun.electric.tool.user.ui;

import com.sun.electric.database.EditingPreferences;
import com.sun.electric.database.Snapshot;
import com.sun.electric.database.SnapshotAnalyze;
import com.sun.electric.database.change.DatabaseChangeEvent;
import com.sun.electric.database.change.DatabaseChangeListener;
import com.sun.electric.database.geometry.EPoint;
import com.sun.electric.database.geometry.ERectangle;
import com.sun.electric.database.geometry.Poly;
import com.sun.electric.database.geometry.ScreenPoint;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.Cell.CellGroup;
import com.sun.electric.database.hierarchy.EDatabase;
import com.sun.electric.database.hierarchy.Export;
import com.sun.electric.database.hierarchy.Library;
import com.sun.electric.database.hierarchy.Nodable;
import com.sun.electric.database.hierarchy.View;
import com.sun.electric.database.id.CellId;
import com.sun.electric.database.network.Netlist;
import com.sun.electric.database.network.Network;
import com.sun.electric.database.prototype.NodeProto;
import com.sun.electric.database.text.Name;
import com.sun.electric.database.text.TextUtils;
import com.sun.electric.database.topology.ArcInst;
import com.sun.electric.database.topology.Geometric;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.topology.PortInst;
import com.sun.electric.database.topology.RTNode;
import com.sun.electric.database.variable.CodeExpression;
import com.sun.electric.database.variable.EditWindow_;
import com.sun.electric.database.variable.ElectricObject;
import com.sun.electric.database.variable.TextDescriptor;
import com.sun.electric.database.variable.VarContext;
import com.sun.electric.database.variable.Variable;
import com.sun.electric.technology.PrimitiveNode;
import com.sun.electric.technology.Technology;
import com.sun.electric.technology.technologies.Generic;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.JobException;
import com.sun.electric.tool.io.output.PNG;
import com.sun.electric.tool.user.ActivityLogger;
import com.sun.electric.tool.user.GraphicsPreferences;
import com.sun.electric.tool.user.Highlight;
import com.sun.electric.tool.user.HighlightListener;
import com.sun.electric.tool.user.Highlighter;
import com.sun.electric.tool.user.User;
import com.sun.electric.tool.user.UserInterfaceMain;
import com.sun.electric.tool.user.dialogs.GetInfoText;
import com.sun.electric.tool.user.dialogs.SelectObject;
import com.sun.electric.tool.user.redisplay.AbstractDrawing;
import com.sun.electric.tool.user.redisplay.PixelDrawing;
import com.sun.electric.tool.user.redisplay.VectorCache;
import com.sun.electric.tool.user.waveform.WaveformWindow;
import com.sun.electric.util.*;
import com.sun.electric.util.math.DBMath;
import com.sun.electric.util.math.FixpRectangle;
import com.sun.electric.util.math.FixpTransform;
import com.sun.electric.util.math.Orientation;

import java.util.*;
import java.lang.ref.*;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.font.LineMetrics;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.PrinterJob;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.print.PrintService;
import javax.print.attribute.standard.ColorSupported;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollBar;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.tree.MutableTreeNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class defines an editing window for displaying circuitry.
 * It implements WindowContent, which means it can be in the main part of a window
 * (to the right of the explorer panel).
 */
public class EditWindow extends JPanel
	implements EditWindow_, WindowContent, MouseMotionListener, MouseListener, MouseWheelListener, KeyListener,
		HighlightListener, DatabaseChangeListener
{
	/** the window scale */									private double scale;
	private double scale_, factorX, factorY;
	/** the requested window scale */						private double scaleRequested;
	/** the global text scale in this window */				private double globalTextScale;
	/** the default font in this window */					private String defaultFont;
	/** the window offset */								private double offx = 0, offy = 0;
	/** the requested window offset */						private double offxRequested, offyRequested;
	/** the size of the window (in pixels) */				private Dimension sz;
	/** the display cache for this window */				private AbstractDrawing drawing;
	/** the half-sizes of the window (in pixels) */			private int szHalfWidth, szHalfHeight;
	/** the cell that is in the window */					private Cell cell;
	/** the page number (for multipage schematics) */		private int pageNumber;
	/** list of edit-in-place text in this window */		private List<GetInfoText.EditInPlaceListener> inPlaceTextObjects = new ArrayList<GetInfoText.EditInPlaceListener>();

	/** true if doing down-in-place display */				private boolean inPlaceDisplay;
	/** transform from screen to cell (down-in-place only) */	private FixpTransform intoCell = new FixpTransform();
	/** transform from cell to screen (down-in-place only) */	private FixpTransform outofCell = new FixpTransform();
	/** path to cell being edited (down-in-place only) */	private List<NodeInst> inPlaceDescent = new ArrayList<NodeInst>();

	/** true if repaint was requested for this EditWindow */volatile boolean repaintRequest;
	/** full instantiate bounds for next drawing  */		private ERectangle fullInstantiateBounds;

	/** Cell's VarContext */								private VarContext cellVarContext;
	/** the window frame containing this EditWindow */		private WindowFrame wf;
	/** the overall panel with display area and sliders */	private JPanel overall;

	/** the bottom scrollbar on the edit window. */			private JScrollBar bottomScrollBar;
	/** the right scrollbar on the edit window. */			private JScrollBar rightScrollBar;

	/** true if showing grid in this window */				private boolean showGrid = false;
    /** true if resolution warning has to be printed */     private boolean showGridWarning = false;
    /** X spacing of grid dots in this window */			private double gridXSpacing;
	/** Y spacing of grid dots in this window */			private double gridYSpacing;

	/** true if doing object-selection drag */				private boolean doingAreaDrag = false;
	/** starting screen point for drags in this window */	private Point startDrag = new Point();
	/** ending screen point for drags in this window */		private Point endDrag = new Point();

	/** true if drawing popup cloud */						private boolean showPopupCloud = false;
//	/** Strings to write to popup cloud */					private List<String> popupCloudText;
//	/** lower left corner of popup cloud */					private Point2D popupCloudPoint;

    /** LayerVisibility for this EditWindow */              private LayerVisibility lv = LayerVisibility.getLayerVisibility();
	/** Highlighter for this window */						private Highlighter highlighter;
	/** Mouse-over Highlighter for this window */			private Highlighter mouseOverHighlighter;
	/** Ruler Highlighter for this window */				private Highlighter rulerHighlighter;
	/** selectable text in this window */					private RTNode<Highlighter.TextHighlightBound> textInCell;

	/** navigate through saved views */						private EditWindowFocusBrowser viewBrowser;

	/** synchronization lock */								private static final Object lock = new Object();
	/** scheduled or running rendering job */				private static RenderJob runningNow = null;
	/** Logger of this package. */							private static Logger logger = LoggerFactory.getLogger(EditWindow.class);
    /** Timer object for pulsating errors */                private Timer pulsatingTimer;

	private static final int SCROLLBARRESOLUTION = 200;

	/** for drawing selection boxes */	private static final BasicStroke selectionLine = new BasicStroke(
		1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[] {2}, 3);
	/** for outlining down-hierarchy in-place bounds */	private static final BasicStroke inPlaceMarker = new BasicStroke(2);

	private static EditWindowDropTarget editWindowDropTarget = new EditWindowDropTarget();

	// ************************************* CONSTRUCTION *************************************

	// constructor
	private EditWindow(Cell cell, WindowFrame wf, Dimension approxSZ)
	{
		this.cell = cell;
		this.pageNumber = 0;
		this.wf = wf;
		setDrawingAlgorithm();
		this.gridXSpacing = User.getDefGridXSpacing();
		this.gridYSpacing = User.getDefGridYSpacing();
		inPlaceDisplay = false;
		viewBrowser = new EditWindowFocusBrowser(this);

		sz = approxSZ;
		if (sz == null) sz = new Dimension(500, 500);
		szHalfWidth = sz.width / 2;
		szHalfHeight = sz.height / 2;
		setSize(sz.width, sz.height);
		setPreferredSize(sz);

		scale = scaleRequested = 1;
		scale_ = (float)(scale/DBMath.GRID);
		factorX = (float)(offx*DBMath.GRID - szHalfWidth/scale_);
		factorY = (float)(offy*DBMath.GRID + szHalfHeight/scale_);
		textInCell = null;
		globalTextScale = User.getGlobalTextScale();
		defaultFont = User.getDefaultFont();

		// the total panel in the edit window
		overall = new JPanel();
		overall.setLayout(new GridBagLayout());

		// the horizontal scroll bar
		int thumbSize = SCROLLBARRESOLUTION / 20;
		bottomScrollBar = new JScrollBar(JScrollBar.HORIZONTAL, SCROLLBARRESOLUTION/2, thumbSize, 0, SCROLLBARRESOLUTION+thumbSize);
		bottomScrollBar.setBlockIncrement(SCROLLBARRESOLUTION / 5);
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.gridx = 0;   gbc.gridy = 1;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		overall.add(bottomScrollBar, gbc);
		bottomScrollBar.addAdjustmentListener(new ScrollAdjustmentListener(this));
		bottomScrollBar.setValue(bottomScrollBar.getMaximum()/2);

		// the vertical scroll bar in the edit window
		rightScrollBar = new JScrollBar(JScrollBar.VERTICAL, SCROLLBARRESOLUTION/2, thumbSize, 0, SCROLLBARRESOLUTION+thumbSize);
		rightScrollBar.setBlockIncrement(SCROLLBARRESOLUTION / 5);
		gbc = new GridBagConstraints();
		gbc.gridx = 1;   gbc.gridy = 0;
		gbc.fill = GridBagConstraints.VERTICAL;
		overall.add(rightScrollBar, gbc);
		rightScrollBar.addAdjustmentListener(new ScrollAdjustmentListener(this));
		rightScrollBar.setValue(rightScrollBar.getMaximum()/2);

		// put this object's display up
		gbc = new GridBagConstraints();
		gbc.gridx = 0;   gbc.gridy = 0;
		gbc.fill = GridBagConstraints.BOTH;
		gbc.weightx = gbc.weighty = 1;
		overall.add(this, gbc);
		setOpaque(true);
		setLayout(null);

		// a drop target for the signal panel
		new DropTarget(this, DnDConstants.ACTION_LINK, editWindowDropTarget, true);

		//setAutoscrolls(true);

		installHighlighters();

        pulsatingTimer = new Timer(100, new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent evt) {
                if (User.isErrorHighlightingPulsate()) {
                    for (Highlight h : getHighlighter().getDifficultHighlights()) {
                        if (h.isError) {
                            EditWindow.super.repaint();
                            return;
                        }
                    }
                }
            }
        });
        pulsatingTimer.start();

		if (wf != null)
		{
			// make a highlighter for this window
			UserInterfaceMain.addDatabaseChangeListener(this);
			Highlighter.addHighlightListener(this);
			setCell(cell, VarContext.globalContext, null);
		}
	}

	private void setDrawingAlgorithm() {
		drawing = AbstractDrawing.createDrawing(this, drawing, cell);
		LayerTab layerTab = getWindowFrame().getLayersTab();
		if (layerTab != null)
			layerTab.setDisplayAlgorithm(drawing.hasOpacity());
	}

	private void installHighlighters()
	{
		// see if this cell is displayed elsewhere
		highlighter = mouseOverHighlighter = rulerHighlighter = null;
		for(Iterator<WindowFrame> it = WindowFrame.getWindows(); it.hasNext(); )
		{
			WindowFrame oWf = it.next();
			if (oWf.getContent() instanceof EditWindow)
			{
				EditWindow oWnd = (EditWindow)oWf.getContent();
				if (oWnd == this) continue;
				if (oWnd.getCell() == cell)
				{
					highlighter = oWnd.highlighter;
					mouseOverHighlighter = oWnd.mouseOverHighlighter;
					rulerHighlighter = oWnd.rulerHighlighter;
					break;
				}
			}
		}
		if (highlighter == null)
		{
			// not shown elsewhere: create highlighters
			highlighter = new Highlighter(Highlighter.SELECT_HIGHLIGHTER, wf);
			mouseOverHighlighter = new Highlighter(Highlighter.MOUSEOVER_HIGHLIGHTER, wf);
			rulerHighlighter = new Highlighter(Highlighter.RULER_HIGHLIGHTER, wf);
		}

		// add listeners --> BE SURE to remove listeners in finished()
		addKeyListener(this);
		addMouseListener(this);
		addMouseMotionListener(this);
		addMouseWheelListener(this);
   }

	private void uninstallHighlighters()
	{
		removeKeyListener(this);
		removeMouseListener(this);
		removeMouseMotionListener(this);
		removeMouseWheelListener(this);

		// see if the highlighters are used elsewhere
		boolean used = false;
		for(Iterator<WindowFrame> it = WindowFrame.getWindows(); it.hasNext(); )
		{
			WindowFrame oWf = it.next();
			if (oWf.getContent() instanceof EditWindow)
			{
				EditWindow oWnd = (EditWindow)oWf.getContent();
				if (oWnd == this) continue;
				if (oWnd.getCell() == cell)
				{
					used = true;
					break;
				}
			}
		}

		if (!used)
		{
			highlighter.delete();
			mouseOverHighlighter.delete();
			rulerHighlighter.delete();
		}
	}

	/**
	 * Factory method to create a new EditWindow with a given cell, in a given WindowFrame.
	 * @param cell the cell in this EditWindow.
	 * @param wf the WindowFrame that this EditWindow lives in.
	 * @param approxSZ the approximate size of this EditWindow (in pixels).
	 * @return the new EditWindow.
	 */
	public static EditWindow CreateElectricDoc(Cell cell, WindowFrame wf, Dimension approxSZ)
	{
		EditWindow ui = new EditWindow(cell, wf, approxSZ);
		return ui;
	}

	// ************************************* EVENT LISTENERS *************************************

	private int lastXPosition, lastYPosition;

	// the MouseListener events
    @Override
	public void mousePressed(MouseEvent evt)
	{
		requestFocus();
		UserInterfaceMain.userCommandIssued();
		lastXPosition = evt.getX();   lastYPosition = evt.getY();
		showCoordinates(evt);
		WindowFrame.getMouseListener().mousePressed(evt);
	}

    @Override
	public void mouseReleased(MouseEvent evt)
	{
		lastXPosition = evt.getX();   lastYPosition = evt.getY();
		showCoordinates(evt);
		WindowFrame.getMouseListener().mouseReleased(evt);
	}

    @Override
	public void mouseClicked(MouseEvent evt)
	{
		lastXPosition = evt.getX();   lastYPosition = evt.getY();
		WindowFrame.getMouseListener().mouseClicked(evt);
	}

    @Override
	public void mouseEntered(MouseEvent evt)
	{
		lastXPosition = evt.getX();   lastYPosition = evt.getY();
		showCoordinates(evt);
		WindowFrame.getMouseListener().mouseEntered(evt);
	}

    @Override
	public void mouseExited(MouseEvent evt)
	{
		lastXPosition = evt.getX();   lastYPosition = evt.getY();
		WindowFrame.getMouseListener().mouseExited(evt);
	}

	// the MouseMotionListener events
    @Override
	public void mouseMoved(MouseEvent evt)
	{
		lastXPosition = evt.getX();   lastYPosition = evt.getY();
		showCoordinates(evt);
		WindowFrame.getMouseMotionListenerListener().mouseMoved(evt);
	}

    @Override
	public void mouseDragged(MouseEvent evt)
	{
		lastXPosition = evt.getX();   lastYPosition = evt.getY();
		showCoordinates(evt);
		WindowFrame.getMouseMotionListenerListener().mouseDragged(evt);
	}

	private void showCoordinates(MouseEvent evt)
	{
		EditWindow wnd = (EditWindow)evt.getSource();
		if (wnd.getCell() == null) StatusBar.setCoordinates(null, wnd.wf); else
		{
			Point2D pt = wnd.screenToDatabase(evt.getX(), evt.getY());
			EditWindow.gridAlign(pt);
			Technology tech = wnd.getCell().getTechnology();

			if (User.isShowHierarchicalCursorCoordinates())
			{
				// if the current VarContext is not the global one, user is "down hierarchy"
				String path = null;
				if (cellVarContext != VarContext.globalContext)
				{
					Point2D ptPath = new Point2D.Double(pt.getX(), pt.getY());
					VarContext vc = cellVarContext;
					boolean first = true;
					NodeInst ni = null;
					path = "";
					while (vc != VarContext.globalContext)
					{
						Nodable no = vc.getNodable();
						ni = no.getNodeInst();
						path = ni.getParent().getName() + "[" + ni.getName() + "]" + (first ? "" : " / ") + path;
						if (first) first = false;
						FixpTransform trans = ni.translateOut(ni.rotateOut());
						trans.transform(ptPath, ptPath);
						vc = vc.pop();
					}
					if (ni.getParent().isSchematic()) {
						path = "Location is " + ni.getParent() + " / " + path;
					} else {
						path = "Location in " + ni.getParent() + " / " + path + " is (" +
							TextUtils.formatDistance(ptPath.getX(), tech) + ", " + TextUtils.formatDistance(ptPath.getY(), tech) + ")";
					}
				}
				StatusBar.setHierarchicalCoordinates(path, wnd.wf);
			}

			StatusBar.setCoordinates("(" + TextUtils.formatDistance(pt.getX(), tech) + ", " + TextUtils.formatDistance(pt.getY(), tech) + ")", wnd.wf);
		}
	}

	// the MouseWheelListener events
    @Override
	public void mouseWheelMoved(MouseWheelEvent evt) { WindowFrame.getMouseWheelListenerListener().mouseWheelMoved(evt); }

	// the KeyListener events
    @Override
	public void keyPressed(KeyEvent evt)
	{
		UserInterfaceMain.userCommandIssued();
		WindowFrame.getKeyListenerListener().keyPressed(evt);
	}

    @Override
	public void keyReleased(KeyEvent evt) { WindowFrame.getKeyListenerListener().keyReleased(evt); }

    @Override
	public void keyTyped(KeyEvent evt) { WindowFrame.getKeyListenerListener().keyTyped(evt); }

    @Override
	public void highlightChanged(Highlighter which) {
    	if (getHighlighter() != which) return;
        repaint();
	}

	/**
	 * Called when by a Highlighter when it loses focus. The argument
	 * is the Highlighter that has gained focus (may be null).
	 * @param highlighterGainedFocus the highlighter for the current window (may be null).
	 */
    @Override
	public void highlighterLostFocus(Highlighter highlighterGainedFocus) {}

	public Point getLastMousePosition()
	{
		return new Point(lastXPosition, lastYPosition);
	}

	// ************************************* WHEN DROPPING A CELL NAME FROM THE EXPLORER TREE *************************************

	/**
	 * Method to figure out which cell in a cell group should be used when dragging the group
	 * onto this EditWindow.
	 * @param group the cell group dragged.
	 * @return the Cell in the group to drag into this EditWindow.
	 */
	private Cell whichCellInGroup(CellGroup group)
	{
		if (cell == null) return null;
		for(Iterator<Cell> it = group.getCells(); it.hasNext(); )
		{
			Cell c = it.next();
			View cV = c.getView();
			if (cV == View.DOC)
				continue; // skip document
			if (cV == View.ICON)
			{
				if (cell.getView() == View.SCHEMATIC || cell.getView() == View.ICON) return c;
			} else if (cV != View.SCHEMATIC)
			{
				if (cell.getView() != View.SCHEMATIC && cell.getView() != View.ICON) return c;
			}
		}
		return null;
	}

	/**
	 * Method to set the highlight to show the outline of a node that will be placed in this EditWindow.
	 * @param toDraw the object to draw (a NodeInst or a NodeProto).
	 * @param oldx the X position (on the screen) of the outline.
	 * @param oldy the Y position (on the screen) of the outline.
	 * @param rotation an extra rotation that is being applied (show it if nonzero).
	 */
	public void showDraggedBox(Object toDraw, int oldx, int oldy, int rotation)
	{
		// erase it
		Highlighter highlighter = getHighlighter();
		highlighter.clear();

		// draw it
		Point2D drawnLoc = screenToDatabase(oldx, oldy);
		EditWindow.gridAlign(drawnLoc);
		NodeProto np = null;
		if (toDraw instanceof CellGroup)
		{
			// figure out which cell in the group should be dragged
			np = whichCellInGroup((CellGroup)toDraw);
		}
		if (toDraw instanceof NodeInst)
		{
			NodeInst ni = (NodeInst)toDraw;
			np = ni.getProto();
		}
		if (toDraw instanceof NodeProto)
		{
			np = (NodeProto)toDraw;
		}
		int defAngle = 0;
		if (toDraw instanceof NodeInst)
		{
			NodeInst ni = (NodeInst)toDraw;
			defAngle = ni.getAngle();
		}
		if (toDraw instanceof PrimitiveNode)
		{
			defAngle = User.getNewNodeRotation();
		}
		if (np != null)
		{
			// zoom the window to fit the placed node (if appropriate)
			zoomWindowToFitCellInstance(np);

			Poly poly = null;
			Orientation orient = Orientation.fromJava(defAngle, defAngle >= 3600, false);
			if (np instanceof Cell)
			{
				Cell placeCell = (Cell)np;
				Rectangle2D cellBounds = placeCell.getBounds();
				poly = new Poly(cellBounds);

				FixpTransform rotate = orient.pureRotate();
				FixpTransform translate = new FixpTransform();
				translate.setToTranslation(drawnLoc.getX(), drawnLoc.getY());
				rotate.preConcatenate(translate);
				poly.transform(rotate);
			} else
			{
                PrimitiveNode pn = (PrimitiveNode)np;

                // New code that should consider "Fix highlighting when place asymmetrical primitive"
                EditingPreferences ep = getEditingPreferences();
                ERectangle baseRectangle = pn.getBaseRectangle();
                poly = new Poly(
                        drawnLoc.getX() + baseRectangle.getLambdaCenterX(),
                        drawnLoc.getY() + baseRectangle.getLambdaCenterY(),
                        baseRectangle.getLambdaWidth() + pn.getDefSize(ep).getLambdaX(),
                        baseRectangle.getLambdaHeight() + pn.getDefSize(ep).getLambdaY());

                FixpTransform trans = orient.rotateAbout(drawnLoc.getX(), drawnLoc.getY());
				poly.transform(trans);
			}
			Point2D [] points = poly.getPoints();
			for(int i=0; i<points.length; i++)
			{
				int last = i-1;
				if (i == 0) last = points.length - 1;
				highlighter.addLine(points[last], points[i], getCell());
			}
			if (rotation != 0)
			{
				highlighter.addMessage(cell, "Rotated " + rotation, poly.getCenter());
			}
			repaint();
		}
		highlighter.finished();
	}

	/**
	 * Method to zoom this window to fit a placed node (if appropriate).
	 * If the placed object is a cell instance that is larger than the window,
	 * and the window is empty, zoom out to fit.
	 * @param np the node being placed.
	 */
	public void zoomWindowToFitCellInstance(NodeProto np)
	{
		Cell parent = getCell();
		if (parent == null) return;
		boolean empty = true;
		if (parent.getNumArcs() > 0) empty = false;
		if (parent.getNumNodes() > 1) empty = false; else
		{
			if (parent.getNumNodes() == 1)
			{
				NodeInst onlyNi = parent.getNode(0);
				if (!Generic.isCellCenter(onlyNi)) empty = false;
			}
		}
		if (empty && np instanceof Cell)
		{
			// placing instance of cell into empty cell: see if scaling is necessary
			Rectangle2D cellBounds = ((Cell)np).getBounds();
			Rectangle2D screenBounds = displayableBounds();
			if (cellBounds.getWidth() > screenBounds.getWidth() ||
				cellBounds.getHeight() > screenBounds.getHeight())
			{
				double scaleX = cellBounds.getWidth() / (screenBounds.getWidth() * 0.9);
				double scaleY = cellBounds.getHeight() / (screenBounds.getHeight() * 0.9);
				double scale = Math.max(scaleX, scaleY);
				setScale(getScale() / scale);
			}
		}
	}

	/**
	 * Class to define a custom data flavor that packages a NodeProto to create.
	 */
	public static class NodeProtoDataFlavor extends DataFlavor
	{
		private Cell cell;
		private Cell.CellGroup group;
		private ExplorerTree originalTree;

		NodeProtoDataFlavor(Cell cell, Cell.CellGroup group, ExplorerTree originalTree)
		{
			super(NodeProto.class, "electric/instance");
			this.cell = cell;
			this.group = group;
			this.originalTree = originalTree;
		}

		public Object getFlavorObject()
		{
			if (cell != null) return cell;
			return group;
		}

		public ExplorerTree getOriginalTree() { return originalTree; }
	}

	/**
	 * Class to define a custom transferable that packages a Cell or Group.
	 */
	public static class NodeProtoTransferable implements Transferable
	{
		private Cell cell;
		private Cell.CellGroup group;
		private NodeProtoDataFlavor df;

		public NodeProtoTransferable(Object obj, ExplorerTree tree)
		{
			if (obj instanceof Cell)
			{
				cell = (Cell)obj;
				group = cell.getCellGroup();
			} else if (obj instanceof Cell.CellGroup)
			{
				group = (Cell.CellGroup)obj;
			}
			df = new NodeProtoDataFlavor(cell, group, tree);
		}

		public boolean isValid() { return group != null; }

        @Override
		public DataFlavor[] getTransferDataFlavors()
		{
			DataFlavor [] it = new DataFlavor[1];
			it[0] = df;
			return it;
		}

        @Override
		public boolean isDataFlavorSupported(DataFlavor flavor)
		{
			if (flavor == df) return true;
			return false;
		}

        @Override
		public Object getTransferData(DataFlavor flavor)
		{
			if (flavor != df) return null;
			if (cell != null) return cell;
			return group;
		}
	}

	/**
	 * Class for catching drags into the edit window.
	 * These drags come from the Explorer tree (when a cell name is dragged to place an instance).
	 */
	private static class EditWindowDropTarget implements DropTargetListener
	{
        @Override
		public void dragEnter(DropTargetDragEvent e)
		{
			dragAction(e);
		}

        @Override
		public void dragOver(DropTargetDragEvent e)
		{
			dragAction(e);
		}

		private Object getDraggedObject(DataFlavor [] flavors)
		{
			if (flavors.length > 0)
			{
				if (flavors[0] instanceof NodeProtoDataFlavor)
				{
					NodeProtoDataFlavor npdf = (NodeProtoDataFlavor)flavors[0];
					Object obj = npdf.getFlavorObject();
					return obj;
				}
			}
			return null;
		}

		private void dragAction(DropTargetDragEvent e)
		{
			Object obj = getDraggedObject(e.getCurrentDataFlavors());
			if (obj != null)
			{
				int action = e.getDropAction();
				e.acceptDrag(action);

				// determine the window
				DropTarget dt = (DropTarget)e.getSource();
				if (dt.getComponent() instanceof JPanel)
				{
					EditWindow wnd = (EditWindow)dt.getComponent();
					wnd.showDraggedBox(obj, e.getLocation().x, e.getLocation().y, 0);
				}
				return;
			}
		}

        @Override
		public void dropActionChanged(DropTargetDragEvent e)
		{
			e.acceptDrag(e.getDropAction());
		}

        @Override
		public void dragExit(DropTargetEvent e) {}

        @Override
		public void drop(DropTargetDropEvent dtde)
		{
			dtde.acceptDrop(DnDConstants.ACTION_LINK);
			Object obj = getDraggedObject(dtde.getCurrentDataFlavors());
			if (obj == null)
			{
				dtde.dropComplete(false);
				return;
			}

			// determine the window
			DropTarget dt = (DropTarget)dtde.getSource();
			if (!(dt.getComponent() instanceof JPanel))
			{
				dtde.dropComplete(false);
				return;
			}
			EditWindow wnd = (EditWindow)dt.getComponent();
			Point2D where = wnd.screenToDatabase(dtde.getLocation().x, dtde.getLocation().y);
			EditWindow.gridAlign(where);
			wnd.getHighlighter().clear();

			NodeInst ni = null;
			NodeProto np = null;
			int defAngle = 0;
			if (obj instanceof CellGroup)
			{
				np = wnd.whichCellInGroup((CellGroup)obj);
			} else if (obj instanceof NodeProto)
			{
				np = (NodeProto)obj;
				if (np instanceof PrimitiveNode)
					defAngle = User.getNewNodeRotation();
			} else if (obj instanceof NodeInst)
			{
				ni = (NodeInst)obj;
				np = ni.getProto();
			} else if (obj instanceof Cell.CellGroup)
			{
				Cell.CellGroup gp = (Cell.CellGroup)obj;
				View view = wnd.getCell().getView();
				if (view == View.SCHEMATIC)
					view = View.ICON;
				for (Iterator<Cell> itG = gp.getCells(); itG.hasNext();)
				{
					Cell c = itG.next();
					if (c.getView() == view)
					{
						np = c; // found
						break;
					}
				}
				if (np == null)
					System.out.println("No " + view + " type found in the dragged group '" + gp.getName() + "'");
			} else if (obj instanceof String)
			{
				String str = (String)obj;
				if (str.startsWith("LOADCELL "))
				{
					String cellName = str.substring(9);
					np = Cell.findNodeProto(cellName);
				}
			}
			if (np != null) // doesn't make sense to call this job if nothing is selected
				new PaletteFrame.PlaceNewNode(wnd, "Create Node", np, ni, defAngle, where, wnd.getCell(), null, false);
		}
	}

	// ************************************* INFORMATION *************************************

	/**
	 * Method to return the top-level JPanel for this EditWindow.
	 * The actual EditWindow object is below the top level, surrounded by scroll bars.
	 * @return the top-level JPanel for this EditWindow.
	 */
    @Override
	public JPanel getPanel() { return overall; }

	/**
	 * Method to return the location of this window on the user's screens.
	 * @return the location of this window on the user's screens.
	 */
    @Override
	public Point getScreenLocationOfCorner() { return overall.getLocationOnScreen(); }

	/**
	 * Method to return the current EditWindow.
	 * @return the current EditWindow (null if none).
	 */
	public static EditWindow getCurrent()
	{
		WindowFrame wf = WindowFrame.getCurrentWindowFrame(false);
		if (wf == null) return null;
		if (wf.getContent() instanceof EditWindow) return (EditWindow)wf.getContent();
		return null;
	}

	/**
	 * Method to return the current EditWindow.
	 * @return the current EditWindow.
	 * If there is none, an error message is displayed and it returns null.
	 */
	public static EditWindow needCurrent()
	{
		WindowFrame wf = WindowFrame.getCurrentWindowFrame(false);
		if (wf != null)
		{
			if (wf.getContent() instanceof EditWindow) return (EditWindow)wf.getContent();
		}
		System.out.println("There is no current graphical editing window for this operation");
		return null;
	}

	// ************************************* EDITWINDOW METHODS *************************************

	/**
	 * Method to return the cell that is shown in this window.
	 * @return the cell that is shown in this window.
	 */
    @Override
	public Cell getCell() { return cell; }

	/**
	 * Method to set the page number that is shown in this window.
	 * Only applies to multi-page schematics.
	 * @param pageNumber the page number that is shown in this window (0-based).
	 */
	public void setMultiPageNumber(int pageNumber)
	{
		if (this.pageNumber == pageNumber) return;
		this.pageNumber = pageNumber;
		setWindowTitle();
		fillScreen();
	}

	/**
	 * Method to return the page number that is shown in this window.
	 * Only applies to multi-page schematics.
	 * @return the page number that is shown in this window (0-based).
	 */
	public int getMultiPageNumber() { return pageNumber; }

	/**
	 * Method to tell whether this EditWindow is displaying a cell "in-place".
	 * In-place display implies that the user has descended into a lower-level
	 * cell while requesting that the upper-level remain displayed.
	 * @return true if this EditWindow is displaying a cell "in-place".
	 */
	public boolean isInPlaceEdit() { return inPlaceDisplay; }

	/**
	 * Method to return the top-level cell for "in-place" display.
	 * In-place display implies that the user has descended into a lower-level
	 * cell while requesting that the upper-level remain displayed.
	 * The top-level cell is the original cell that is remaining displayed.
	 * @return the top-level cell for "in-place" display.
	 */
	public Cell getInPlaceEditTopCell() { return inPlaceDisplay ? inPlaceDescent.get(0).getParent() : cell; }

	/**
	 * Method to return a List of NodeInsts to the cell being in-place edited.
	 * In-place display implies that the user has descended into a lower-level
	 * cell while requesting that the upper-level remain displayed.
	 * @return a List of NodeInsts to the cell being in-place edited.
	 */
	public List<NodeInst> getInPlaceEditNodePath() { return inPlaceDescent; }

	/**
	 * Method to set the List of NodeInsts to the cell being in-place edited.
	 * In-place display implies that the user has descended into a lower-level
	 * cell while requesting that the upper-level remain displayed.
	 * @param da DisplayAttributes of EditWindow.
	 */
	private void setInPlaceEditNodePath(WindowFrame.DisplayAttributes da) {
		inPlaceDescent = da.inPlaceDescent;
		intoCell = da.getIntoCellTransform();
		outofCell = da.getOutofCellTransform();
		inPlaceDisplay = !inPlaceDescent.isEmpty();
	}

	/**
	 * Method to return the transformation matrix from the displayed top-level cell to the current cell.
	 * In-place display implies that the user has descended into a lower-level
	 * cell while requesting that the upper-level remain displayed.
	 * @return the transformation matrix from the displayed top-level cell to the current cell.
	 */
	public FixpTransform getInPlaceTransformIn() { return intoCell; }

	/**
	 * Method to return the transformation matrix from the current cell to the displayed top-level cell.
	 * In-place display implies that the user has descended into a lower-level
	 * cell while requesting that the upper-level remain displayed.
	 * @return the transformation matrix from the current cell to the displayed top-level cell.
	 */
	public FixpTransform getInPlaceTransformOut() { return outofCell; }

	/**
	 * Get the highlighter for this WindowContent.
	 * @return the highlighter.
	 */
    @Override
	public Highlighter getHighlighter() { return highlighter; }

	/**
	 * Get the mouse over highlighter for this EditWindow.
	 * @return the mouse over highlighter.
	 */
	public Highlighter getMouseOverHighlighter() { return mouseOverHighlighter; }

	/**
	 * Get the ruler highlighter for this EditWindow (for measurement).
	 * @return the ruler highlighter.
	 */
	public Highlighter getRulerHighlighter() { return rulerHighlighter; }

	/**
	 * Get the RTree with <b>all</b> text in this Cell, not just the visible text.
	 * @return the RTree with all text in this Cell.
	 */
	public RTNode<Highlighter.TextHighlightBound> getTextInCell() { return textInCell; }

	/**
	 * Set the RTree with <b>all</b> text in this Cell, not just the visible text.
	 * @param tic the RTree with all text in this Cell.
	 */
	public void setTextInCell(RTNode<Highlighter.TextHighlightBound> tic) { textInCell = tic; }

	/**
	 * Method to return the WindowFrame in which this EditWindow resides.
	 * @return the WindowFrame in which this EditWindow resides.
	 */
	public WindowFrame getWindowFrame() { return wf; }

	/**
	 * Method to set the cell that is shown in the window to "cell".
	 */
    @Override
	public void setCell(Cell cell, VarContext context, WindowFrame.DisplayAttributes displayAttributes)
	{
		// by default record history and fill screen
		// However, when navigating through history, don't want to record new history objects.
		if (context == null) context = VarContext.globalContext;
		boolean fillTheScreen = false;
		if (displayAttributes == null)
		{
			displayAttributes = new WindowFrame.DisplayAttributes(getScale(), getOffset().getX(), getOffset().getY(),
				new ArrayList<NodeInst>());
			fillTheScreen = true;
		}

		// recalculate the screen size
		sz = getSize();
		szHalfWidth = sz.width / 2;
		szHalfHeight = sz.height / 2;

		showCell(cell, context, fillTheScreen, displayAttributes);
	}

	/**
	 * Method to show a cell with ports, display factors, etc.
	 */
	private void showCell(Cell cell, VarContext context, boolean fillTheScreen,
		WindowFrame.DisplayAttributes displayAttributes)
	{
		// record current history before switching to new cell
		wf.saveCurrentCellHistoryState();

		// remove highlighters from the window
		uninstallHighlighters();

		// set new values
		boolean cellChanged = this.cell != cell;
		this.cell = cell;
		textInCell = null;
		setInPlaceEditNodePath(displayAttributes);
		this.pageNumber = 0;
		cellVarContext = context;
		if (cell != null) {
			Library lib = cell.getLibrary();
			lib.setCurCell(cell);
		}
		setDrawingAlgorithm();

		// add new highlighters from the window
		installHighlighters();
		viewBrowser.clear();
		setWindowTitle();
		if (cell != null && cellChanged)
		{
			if (wf != null && wf == WindowFrame.getCurrentWindowFrame(false))
			{
				// if auto-switching technology, do it
				WindowFrame.autoTechnologySwitch(cell, wf);
			}
		}
		if (fillTheScreen) fillScreen(); else
		{
			setScale(displayAttributes.scale);
			setOffset(new Point2D.Double(displayAttributes.offX, displayAttributes.offY));
		}

		if (cell != null && User.isCheckCellDates()) cell.checkCellDates();

		// clear list of cross-probed levels for this EditWindow
		clearCrossProbeLevels();

		// update cell information in the status bar
		StatusBar.updateStatusBar();
	}

	/**
	 * Method to set the window title.
	 */
    @Override
	public void setWindowTitle()
	{
		if (wf == null) return;
		wf.setTitle(wf.composeTitle(cell, "", pageNumber));
	}

	/**
	 * Method to find an EditWindow that is displaying a given cell.
	 * @param cell the Cell to find.
	 * @return the EditWindow showing that cell, or null if none found.
	 */
	public static EditWindow findWindow(Cell cell)
	{
		for(Iterator<WindowFrame> it = WindowFrame.getWindows(); it.hasNext(); )
		{
			WindowFrame wf = it.next();
			WindowContent content = wf.getContent();
			if (!(content instanceof EditWindow)) continue;
			if (content.getCell() == cell) return (EditWindow)content;
		}
		return null;
	}

	/**
	 * Method to bring to the front a WindowFrame associated to a given Cell.
	 * If no WindowFrame is found, a new WindowFrame will be created and displayed
	 * @param c the Cell in the window to raise.
	 * @param varC the Context of that window.
	 * @return the EditWindow of the cell that was found or created.
	 */
	public static EditWindow showEditWindowForCell(Cell c, VarContext varC)
	{
		for(Iterator<WindowFrame> it2 = WindowFrame.getWindows(); it2.hasNext(); )
		{
			WindowFrame wf = it2.next();
			WindowContent content = wf.getContent();
			if (c != content.getCell())
				continue;
			if (!(content instanceof EditWindow))
				continue;
			EditWindow wnd = (EditWindow)content;
			if (varC != null) // it has to be an EditWindow class
			{
				// VarContexts must match
				if (!varC.equals(wnd.getVarContext()))
					continue;
			}
			WindowFrame.showFrame(wf);
			return wnd;
		}

		// If no window is found, then create one
		WindowFrame wf = WindowFrame.createEditWindow(c);
		EditWindow wnd = (EditWindow)wf.getContent();
		wnd.setCell(c, varC, null);
		return wnd;
	}

    @Override
	public List<MutableTreeNode> loadExplorerTrees()
	{
		return wf.loadDefaultExplorerTree();
	}

    @Override
    public void loadTechnologies() {
        lv = LayerVisibility.getLayerVisibility();
        setDrawingAlgorithm();
    }

	/**
	 * Method to get rid of this EditWindow.  Called by WindowFrame when
	 * that windowFrame gets closed.
	 */
    @Override
	public void finished()
	{
		// remove myself from listener list
		uninstallHighlighters();
        pulsatingTimer.stop();
		UserInterfaceMain.removeDatabaseChangeListener(this);
		Highlighter.removeHighlightListener(this);
        UserInterfaceMain.freeHighlights(this);
	}

	// ************************************* SCROLLING *************************************

	/**
	 * Method to return the scroll bar resolution.
	 * This is the extent of the JScrollBar.
	 * @return the scroll bar resolution.
	 */
	public static int getScrollBarResolution() { return SCROLLBARRESOLUTION; }

	/**
	 * This class handles changes to the edit window scroll bars.
	 */
	private static class ScrollAdjustmentListener implements AdjustmentListener
	{
		/** A weak reference to the WindowFrame */
		EditWindow wnd;

		ScrollAdjustmentListener(EditWindow wnd)
		{
			super();
			this.wnd = wnd;
		}

        @Override
		public void adjustmentValueChanged(AdjustmentEvent e)
		{
			if (e.getSource() == wnd.getBottomScrollBar() && wnd.getCell() != null)
				wnd.bottomScrollChanged(e.getValue());
			if (e.getSource() == wnd.getRightScrollBar() && wnd.getCell() != null)
				wnd.rightScrollChanged(e.getValue());
		}
	}

	/**
	 * Method to return the horizontal scroll bar at the bottom of the edit window.
	 * @return the horizontal scroll bar at the bottom of the edit window.
	 */
	public JScrollBar getBottomScrollBar() { return bottomScrollBar; }

	/**
	 * Method to return the vertical scroll bar at the right side of the edit window.
	 * @return the vertical scroll bar at the right side of the edit window.
	 */
	public JScrollBar getRightScrollBar() { return rightScrollBar; }

	// ************************************* REPAINT *************************************

	/**
	 * Method to repaint this EditWindow.
	 * Composites the image (taken from the PixelDrawing object)
	 * with the grid, highlight, and any dragging rectangle.
	 */
    @Override
	public void paintComponent(Graphics graphics)
	{
		// to enable keys to be received
		if (wf == null) return;

		Graphics2D g = (Graphics2D)graphics;
		if (cell == null) {
			g.setColor(new Color(User.getColor(User.ColorPrefType.BACKGROUND)));
			g.fillRect(0, 0, getWidth(), getHeight());
			String msg = "No cell in this window";
			Font f = new Font(User.getDefaultFont(), Font.BOLD, 18);
			g.setFont(f);
			g.setColor(new Color(User.getColor(User.ColorPrefType.TEXT)));
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g.drawString(msg, (getWidth() - g.getFontMetrics(f).stringWidth(msg))/2, getHeight()/2);
			return;
		}

		logger.debug("paintComponent {}", getCell());

        Dimension sz = getSize();
		if (!drawing.paintComponent(g, lv, sz)) {
			fullRepaint();
			g.setColor(new Color(User.getColor(User.ColorPrefType.BACKGROUND)));
			g.fillRect(0, 0, getWidth(), getHeight());
			logger.debug("paintComponent - scheduled fullRepaint from Drawing {}", drawing);
			return;
		}
		logger.debug("paintComponent - offscreen is drawn");

        // this is done be AbstractDrawing.setupEditWindowCoordinates now
//		this.sz = sz;
//		szHalfWidth = sz.width / 2;
//		szHalfHeight = sz.height / 2;
//		if (scale != drawing.da.scale || offx != drawing.da.offX || offy != drawing.da.offY)
//			textInCell = null;
//		scale = drawing.da.scale;
//		scale_ = (float)(scale/DBMath.GRID);
//		offx = drawing.da.offX;
//		offy = drawing.da.offY;
//		factorX = (float)(offx*DBMath.GRID - szHalfWidth/scale_);
//		factorY = (float)(offy*DBMath.GRID + szHalfHeight/scale_);

		setScrollPosition();			// redraw scroll bars

		// set the default text size (for highlighting, etc)
		Font f = new Font(User.getDefaultFont(), Font.PLAIN, (int)(10*globalTextScale));
		g.setFont(f);

		// add cross-probed level display
		showCrossProbeLevels(g);

        try {
            // add in the frame if present
            drawCellFrame(g);

            if (rulerHighlighter.getNumHighlights() > 0) {
                try {
                    // Draw ruler text in a little large font
                    Font fr = new Font(User.getDefaultFont(), Font.PLAIN, (int) (15 * globalTextScale));
                    g.setFont(fr);
                    rulerHighlighter.showHighlights(this, g, false);
                } finally {
                    g.setFont(f);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

		// add in drag area
		if (doingAreaDrag) showDragBox(g);

		// add in popup cloud
		if (showPopupCloud) drawPopupCloud(g);

		// add in shadow if doing in-place editing
		if (inPlaceDisplay)
		{
			Rectangle2D bounds = cell.getBounds();
			ScreenPoint i1 = databaseToScreen(bounds.getMinX(), bounds.getMinY());
			ScreenPoint i2 = databaseToScreen(bounds.getMinX(), bounds.getMaxY());
			ScreenPoint i3 = databaseToScreen(bounds.getMaxX(), bounds.getMaxY());
			ScreenPoint i4 = databaseToScreen(bounds.getMaxX(), bounds.getMinY());

			// shade everything else except for the cell being edited
			if (User.isDimUpperLevelWhenDownInPlace())
			{
				Polygon innerPoly = new Polygon();
				innerPoly.addPoint(i1.getIntX(), i1.getIntY());
				innerPoly.addPoint(i2.getIntX(), i2.getIntY());
				innerPoly.addPoint(i3.getIntX(), i3.getIntY());
				innerPoly.addPoint(i4.getIntX(), i4.getIntY());
				Area outerArea = new Area(new Rectangle(0, 0, sz.width, sz.height));
				Area innerArea = new Area(innerPoly);
				outerArea.subtract(innerArea);
				g.setColor(new Color(128, 128, 128, 128));
				g.fill(outerArea);
			}

			// draw a red box around the cell being edited
			g.setStroke(inPlaceMarker);
			g.setColor(new Color(User.getColor(User.ColorPrefType.DOWNINPLACEBORDER)));
			int lX = Math.min(Math.min(i1.getIntX(), i2.getIntX()), Math.min(i3.getIntX(), i4.getIntX()));
			int hX = Math.max(Math.max(i1.getIntX(), i2.getIntX()), Math.max(i3.getIntX(), i4.getIntX()));
			int lY = Math.min(Math.min(i1.getIntY(), i2.getIntY()), Math.min(i3.getIntY(), i4.getIntY()));
			int hY = Math.max(Math.max(i1.getIntY(), i2.getIntY()), Math.max(i3.getIntY(), i4.getIntY()));
			g.drawLine(lX-1, lY-1, lX-1, hY+1);
			g.drawLine(lX-1, hY+1, hX+1, hY+1);
			g.drawLine(hX+1, hY+1, hX+1, lY-1);
			g.drawLine(hX+1, lY-1, lX-1, lY-1);
		}

		// see if anything else is queued
		if (scale != scaleRequested || offx != offxRequested || offy != offyRequested || !getSize().equals(sz))
		{
			textInCell = null;
			fullRepaint();
            if (logger.isDebugEnabled()) {
                StringBuilder sb = new StringBuilder();
                if (scale != scaleRequested) {
                    sb.append(" scale ");
                    sb.append(scale);
                    sb.append(" -> ");
                    sb.append(scaleRequested);
                }
                if (offx != offxRequested) {
                    sb.append(" offx ");
                    sb.append(offx);
                    sb.append(" -> ");
                    sb.append(offxRequested);

                }
                if (offy != offxRequested) {
                    sb.append(" offy ");
                    sb.append(offy);
                    sb.append(" -> ");
                    sb.append(offyRequested);

                }
                logger.debug("paintComponent - end & fullRepaint {}", sb);
            }
		} else {
            logger.debug("paintComponent - end");
        }
	}

	/**
	 * Method to store a new "in-place" text editing object on this EditWindow.
	 * @param tl the Listener that is now sitting on top of this EditWindow.
	 */
	public void addInPlaceTextObject(GetInfoText.EditInPlaceListener tl)
	{
		inPlaceTextObjects.add(tl);
		add(tl.getTextComponent());
	}

	/**
	 * Method to return the current "in-place" text editing object on this EditWindow.
	 * @return the current "in-place" text editing object on this EditWindow.
	 */
	public GetInfoText.EditInPlaceListener getInPlaceTextObject()
	{
		if (inPlaceTextObjects.size() == 0) return null;
		return inPlaceTextObjects.get(inPlaceTextObjects.size()-1);
	}

	/**
	 * Method to remove a "in-place" text editing object from this EditWindow.
	 * @param tl the Listener that is no longer sitting on top of this EditWindow.
	 */
	public void removeInPlaceTextObject(GetInfoText.EditInPlaceListener tl)
	{
		inPlaceTextObjects.remove(tl);
		remove(tl.getTextComponent());
	}

	/**
	 * Method to remove all in-place text objects in this window.
	 * Called when the window pans or zooms and the text objects are no longer in the proper place.
	 */
	public void removeAllInPlaceTextObjects()
	{
		List<GetInfoText.EditInPlaceListener> allTextObjects = new ArrayList<GetInfoText.EditInPlaceListener>();
		for(GetInfoText.EditInPlaceListener eip : inPlaceTextObjects)
			allTextObjects.add(eip);
		for(GetInfoText.EditInPlaceListener tl : allTextObjects)
		{
			tl.closeEditInPlace();
		}
	}

	/**
	 * Method requests that every EditWindow be redrawn, including a change of display algorithm.
	 */
	public static void displayAlgorithmChanged()
	{
		for(Iterator<WindowFrame> it = WindowFrame.getWindows(); it.hasNext(); )
		{
			WindowFrame wf = it.next();
			WindowContent content = wf.getContent();
			if (!(content instanceof EditWindow)) continue;
			EditWindow wnd = (EditWindow)content;
			wnd.setDrawingAlgorithm();
			wnd.fullRepaint();
		}
	}

    public LayerVisibility getLayerVisibility() {
        return lv;
    }

    public EditingPreferences getEditingPreferences() {
        return UserInterfaceMain.getEditingPreferences();
    }

    public GraphicsPreferences getGraphicsPreferences() {
        return UserInterfaceMain.getGraphicsPreferences();
    }

    public void setLayerVisibility(LayerVisibility lv) {
        this.lv = lv;
        if (drawing.visibilityChanged())
                fullRepaint();
        else
            repaint();
        wf.set3DLayerVisibility(lv);
    }

	/**
	 * Method requests that every EditWindow be redrawn, including a re-rendering of its contents.
	 */
	public static void repaintAllContents()
	{
		for(Iterator<WindowFrame> it = WindowFrame.getWindows(); it.hasNext(); )
		{
			WindowFrame wf = it.next();
			WindowContent content = wf.getContent();
			if (!(content instanceof EditWindow)) continue;
			EditWindow wnd = (EditWindow)content;
			wnd.fullRepaint();
		}
	}

	/**
	 * Method signals that every EditWindow be redrawn after Layer visibility change
	 */
	public static void setLayerVisibilityAll(LayerVisibility lv)
	{
		for(Iterator<WindowFrame> it = WindowFrame.getWindows(); it.hasNext(); )
		{
			WindowFrame wf = it.next();
			WindowContent content = wf.getContent();
			if (!(content instanceof EditWindow)) continue;
			EditWindow wnd = (EditWindow)content;
			wnd.setLayerVisibility(lv);
		}
	}

	/**
	 * Method requests that every EditWindow be redrawn, without re-rendering the offscreen contents.
	 */
	public static void repaintAll()
	{
		for(Iterator<WindowFrame> it = WindowFrame.getWindows(); it.hasNext(); )
		{
			WindowFrame wf = it.next();
			WindowContent content = wf.getContent();
			if (!(content instanceof EditWindow)) continue;
			EditWindow wnd = (EditWindow)content;
			wnd.repaint();
		}
	}

	/**
	 * Method requests that this EditWindow be redrawn, including a re-rendering of the contents.
	 */
    @Override
	public void fullRepaint() {
		repaintContents(null, false);
	}

	/**
	 * Method requests that this EditWindow be redrawn, including a re-rendering of the contents.
	 * @param bounds the area to redraw (null to draw everything).
	 * @param fullInstantiate true to display to the bottom of the hierarchy (for peeking).
	 */
    @Override
    public void repaintContents(ERectangle bounds, boolean fullInstantiate) {
        repaintContents(bounds, fullInstantiate, new AbstractDrawing.DrawingPreferences(), UserInterfaceMain.getGraphicsPreferences());
    }

	/**
	 * Method requests that this EditWindow be redrawn, including a re-rendering of the contents.
	 * @param bounds the area to redraw (null to draw everything).
	 * @param fullInstantiate true to display to the bottom of the hierarchy (for peeking).
	 */
	private void repaintContents(ERectangle bounds, boolean fullInstantiate, AbstractDrawing.DrawingPreferences dp, GraphicsPreferences gp)
	{
		// start rendering thread
		if (wf == null) return;
		if (cell == null) {
			repaint();
			return;
		}
        synchronized (lock) {
            if (runningNow != null && repaintRequest && !fullInstantiate) {
                return;
            }
        }

		logger.debug("repaintContents");

		// do the redraw in a separate thread
		if (fullInstantiate) {
            FixpRectangle b = FixpRectangle.from(bounds);
			DBMath.transformRect(b, outofCell);
			fullInstantiateBounds = ERectangle.fromLambda(b);
		}
		invokeRenderJob(this, dp, gp);

		logger.debug("repaintContents - end");
	}

	public static void invokeRenderJob() {
		invokeRenderJob(null, new AbstractDrawing.DrawingPreferences(), UserInterfaceMain.getGraphicsPreferences());
	}

	private static void invokeRenderJob(EditWindow wnd, AbstractDrawing.DrawingPreferences dp, GraphicsPreferences gp) {
    	logger.debug("invokeRenderJob {}", wnd != null ? wnd.getCell() : null);
		synchronized(lock)
		{
			if (wnd != null) {
				wnd.drawing.abortRendering();
                wnd.repaintRequest = true;
			}
			if (runningNow != null) {
				runningNow.hasTasks = true;
				logger.debug("invokeRenderJob running now");
				return;
			}
			runningNow = new RenderJob(gp, dp);
		}
		runningNow.startJob();
		logger.debug("invokeRenderJob starting job");
	}

	/**
	 * This class queues requests to re-render a window.
	 */
	private static class RenderJob extends Job
	{
		private static Snapshot oldSnapshot = EDatabase.clientDatabase().getInitialSnapshot();
		volatile boolean hasTasks;
        private GraphicsPreferences gp;
        private AbstractDrawing.DrawingPreferences dp;

		protected RenderJob(GraphicsPreferences gp, AbstractDrawing.DrawingPreferences dp)
		{
			super("Display", User.getUserTool(), Job.Type.CLIENT_EXAMINE, null, null, Job.Priority.USER);
            this.gp = gp;
            this.dp = dp;
		}

        @Override
		public boolean doIt() throws JobException {
			logger.debug("RenderJob.doIt");
			try {
				for (;;) {
					hasTasks = false;
					Snapshot snapshot = EDatabase.clientDatabase().backup();
					if (snapshot != oldSnapshot) {
						endBatch(dp, gp, new SnapshotAnalyze(oldSnapshot, snapshot));
						oldSnapshot = snapshot;
					}
					EditWindow wnd = null;
					for (Iterator<WindowFrame> it = WindowFrame.getWindows(); it.hasNext();) {
						WindowFrame wf = it.next();
						WindowContent wc = wf.getContent();
						if (wc instanceof EditWindow && ((EditWindow) wc).repaintRequest) {
							wnd = (EditWindow) wc;
							break;
						}
					}
					if (wnd == null) {
						break;
					}
                    synchronized (lock) {
                        wnd.repaintRequest = false;
                    }
					render(wnd);
				}
			} finally {
				RenderJob j = null;
				synchronized (lock) {
					if (hasTasks) {
						runningNow = j = new RenderJob(gp, dp);
					} else {
						runningNow = null;
					}
				}
				if (j != null) {
					assert j == runningNow;
					j.startJob();
				}
			}

			logger.debug("RenderJob.doIt - return");
			return true;
		}

		private void render(EditWindow wnd)
				throws JobException {
			logger.debug("RenderJob.render");

			// do the hard work of re-rendering the image
			ERectangle bounds = null;
			boolean fullInstantiate = false;
			if (wnd.fullInstantiateBounds != null) {
				fullInstantiate = true;
				bounds = wnd.fullInstantiateBounds;
				wnd.fullInstantiateBounds = null;
			} else if (bounds == null) {
				// see if a real bounds is defined in the cell
				bounds = User.getChangedInWindow(wnd);
			}
			if (bounds != null) {
				User.clearChangedInWindow(wnd);
			}
			WindowFrame.DisplayAttributes da = new WindowFrame.DisplayAttributes(wnd.scaleRequested,
				wnd.offxRequested, wnd.offyRequested, wnd.inPlaceDescent);
			wnd.drawing.render(wnd.getSize(), da, gp, dp, fullInstantiate, bounds);
			wnd.repaint();
			logger.debug("RenderJob.render - exit");
		}
	}

	public void testJogl() {
		drawing.testJogl();
	}

	void opacityChanged() {
		drawing.opacityChanged();
	}

	// ************************************* SIMULATION CROSSPROBE LEVEL DISPLAY *************************************

	private static class CrossProbe
	{
		boolean isLine;
		Point2D start, end;
		Rectangle2D box;
		Color color;
	};

	private List<CrossProbe> crossProbeObjects = new ArrayList<CrossProbe>();

	/**
	 * Method to clear the list of cross-probed levels in this EditWindow.
	 * Cross-probed levels are displays of the current simulation value
	 * at a point in the display, and come from the Waveform Window.
	 */
	public void clearCrossProbeLevels()
	{
		crossProbeObjects.clear();
	}

	/**
	 * Method to tell whether there is any cross-probed data in this EditWindow.
	 * Cross-probed levels are displays of the current simulation value
	 * at a point in the display, and come from the Waveform Window.
	 * @return true if there are any cross-probed data displays in this EditWindow.
	 */
	public boolean hasCrossProbeData()
	{
		if (crossProbeObjects.size() > 0) return true;
		return false;
	}

	/**
	 * Method to add a line to the list of cross-probed levels in this EditWindow.
	 * Cross-probed levels are displays of the current simulation value
	 * at a point in the display, and come from the Waveform Window.
	 * @param start the starting point of the line.
	 * @param end the ending point of the line.
	 * @param color the color of the line.
	 */
	public void addCrossProbeLine(Point2D start, Point2D end, Color color)
	{
		CrossProbe cp = new CrossProbe();
		cp.isLine = true;
		cp.start = start;
		cp.end = end;
		cp.color = color;
		crossProbeObjects.add(cp);
	}

	/**
	 * Method to add a box to the list of cross-probed levels in this EditWindow.
	 * Cross-probed levels are displays of the current simulation value
	 * at a point in the display, and come from the Waveform Window.
	 * @param box the bounds of the box.
	 * @param color the color of the box.
	 */
	public void addCrossProbeBox(Rectangle2D box, Color color)
	{
		CrossProbe cp = new CrossProbe();
		cp.isLine = false;
		cp.box = box;
		cp.color = color;
		crossProbeObjects.add(cp);
	}

	private void showCrossProbeLevels(Graphics g)
	{
		for(CrossProbe cp : crossProbeObjects)
		{
			g.setColor(cp.color);
			if (cp.isLine)
			{
				// draw a line
				ScreenPoint pS = databaseToScreen(cp.start);
				ScreenPoint pE = databaseToScreen(cp.end);
				g.drawLine(pS.getIntX(), pS.getIntY(), pE.getIntX(), pE.getIntY());
			} else
			{
				// draw a box
				ScreenPoint pS = databaseToScreen(cp.box.getMinX(), cp.box.getMinY());
				ScreenPoint pE = databaseToScreen(cp.box.getMaxX(), cp.box.getMaxY());
				int lX = Math.min(pS.getIntX(), pE.getIntX());
				int lY = Math.min(pS.getIntY(), pE.getIntY());
				int wid = Math.abs(pS.getIntX() - pE.getIntX());
				int hei = Math.abs(pS.getIntY() - pE.getIntY());
				g.fillRect(lX, lY, wid, hei);
			}
		}
	}

	// ************************************* DRAG BOX *************************************

	public boolean isDoingAreaDrag() { return doingAreaDrag; }

	public void setDoingAreaDrag() { doingAreaDrag = true; }

	public void clearDoingAreaDrag() { doingAreaDrag = false; }

	public Point getStartDrag() { return startDrag; }

	public void setStartDrag(int x, int y) { startDrag.setLocation(x, y); }

	public Point getEndDrag() { return endDrag; }

	public void setEndDrag(int x, int y) { endDrag.setLocation(x, y); }

	private void showDragBox(Graphics g)
	{
		int lX = (int)Math.min(startDrag.getX(), endDrag.getX());
		int hX = (int)Math.max(startDrag.getX(), endDrag.getX());
		int lY = (int)Math.min(startDrag.getY(), endDrag.getY());
		int hY = (int)Math.max(startDrag.getY(), endDrag.getY());
		Graphics2D g2 = (Graphics2D)g;
		g2.setStroke(selectionLine);
		g.setColor(new Color(User.getColor(User.ColorPrefType.HIGHLIGHT)));
		g.drawLine(lX, lY, lX, hY);
		g.drawLine(lX, hY, hX, hY);
		g.drawLine(hX, hY, hX, lY);
		g.drawLine(hX, lY, lX, lY);
	}

	// ************************************* CELL FRAME *************************************

	/**
	 * Method to render the cell frame directly to the Graphics.
	 */
	private void drawCellFrame(Graphics g)
	{
		DisplayedFrame df = new DisplayedFrame(cell, g, this);
		df.renderFrame();
	}

	/**
	 * Class for rendering a cell frame.
	 * Extends Cell.FrameDescription and provides hooks for drawing to a Graphics.
	 */
	private static class DisplayedFrame extends Cell.FrameDescription
	{
		private Graphics g;
		private EditWindow wnd;
		private Color lineColor, textColor;

		/**
		 * Constructor for cell frame rendering.
		 * @param cell the Cell that is having a frame drawn.
		 * @param g the Graphics to which to draw the frame.
		 * @param wnd the EditWindow in which this is being drawn.
		 */
		public DisplayedFrame(Cell cell, Graphics g, EditWindow wnd)
		{
			super(cell, wnd.pageNumber);
			this.g = g;
			this.wnd = wnd;
			lineColor = new Color(User.getColor(User.ColorPrefType.INSTANCE));
			textColor = new Color(User.getColor(User.ColorPrefType.TEXT));
		}

		/**
		 * Method to draw a line in a frame.
		 * @param from the starting point of the line (in database units).
		 * @param to the ending point of the line (in database units).
		 */
        @Override
		public void showFrameLine(Point2D from, Point2D to)
		{
			g.setColor(lineColor);
			ScreenPoint f = wnd.databaseToScreen(from);
			ScreenPoint t = wnd.databaseToScreen(to);
			g.drawLine(f.getIntX(), f.getIntY(), t.getIntX(), t.getIntY());
		}

		/**
		 * Method to draw text in a frame.
		 * @param ctr the anchor point of the text.
		 * @param size the size of the text (in database units).
		 * @param maxWid the maximum width of the text (ignored if zero).
		 * @param maxHei the maximum height of the text (ignored if zero).
		 * @param string the text to be displayed.
		 */
        @Override
		public void showFrameText(Point2D ctr, double size, double maxWid, double maxHei, String string)
		{
			// convert text size to screen points
			ScreenPoint sizeVector = wnd.deltaDatabaseToScreen(size, size);
			int initialHeight = Math.abs(sizeVector.getIntY());

			// get the font
			Font font = new Font(User.getDefaultFont(), Font.PLAIN, initialHeight);
			g.setFont(font);
			g.setColor(textColor);
			FontRenderContext frc = new FontRenderContext(null, true, true);

			// convert the message to glyphs
			GlyphVector gv = font.createGlyphVector(frc, string);
			LineMetrics lm = font.getLineMetrics(string, frc);
			Rectangle rect = gv.getOutline(0, lm.getAscent()-lm.getLeading()).getBounds();
			double width = rect.width;
			double height = lm.getHeight();
			Point2D databaseSize = wnd.deltaScreenToDatabase((int)width, (int)height);
			double dbWidth = Math.abs(databaseSize.getX());
			double dbHeight = Math.abs(databaseSize.getY());
			if (maxWid > 0 && maxHei > 0 && (dbWidth > maxWid || dbHeight > maxHei))
			{
				double scale = Math.min(maxWid / dbWidth, maxHei / dbHeight);
				font = new Font(User.getDefaultFont(), Font.PLAIN, (int)(initialHeight*scale));
				if (font != null)
				{
					gv = font.createGlyphVector(frc, string);
					lm = font.getLineMetrics(string, frc);
					rect = gv.getOutline(0, lm.getAscent()-lm.getLeading()).getBounds();
					width = rect.width;
					height = lm.getHeight();
				}
			}

			// render the text
			Graphics2D g2 = (Graphics2D)g;
			ScreenPoint p = wnd.databaseToScreen(ctr);
			g2.drawGlyphVector(gv, (float)(p.getX() - width/2), (float)(p.getY() + height/2 - lm.getDescent()));
		}
	}

	// ************************************* GRID *************************************

	/**
	 * Method to set the display of a grid in this window.
	 * @param showG true to show the grid.
	 */
	public void setGrid(boolean showG)
	{
		this.showGrid = showG;
        this.showGridWarning = showG;

        fullRepaint();
	}

    /**
	 * Method to return the state of grid display in this window.
	 * @return true if the grid is displayed in this window.
	 */
    @Override
	public boolean isGrid() { return showGrid; }

    /**
     * Method to print warning message in case grid is not displayed
     * due to the resolution in this window.
     */
    public void printGridWarning()
    {
        if (showGridWarning)
        {
            System.out.println("Toggle grid is on but grid is not drawn due to the resolution");
            showGridWarning = false;
        }
    }

    /**
	 * Method to return the distance between grid dots in the X direction.
	 * @return the distance between grid dots in the X direction.
	 */
    @Override
	public double getGridXSpacing() { return gridXSpacing; }
	/**
	 * Method to set the distance between grid dots in the X direction.
	 * @param spacing the distance between grid dots in the X direction.
	 */
	public void setGridXSpacing(double spacing) { gridXSpacing = spacing; }

	/**
	 * Method to return the distance between grid dots in the Y direction.
	 * @return the distance between grid dots in the Y direction.
	 */
    @Override
	public double getGridYSpacing() { return gridYSpacing; }
	/**
	 * Method to set the distance between grid dots in the Y direction.
	 * @param spacing the distance between grid dots in the Y direction.
	 */
	public void setGridYSpacing(double spacing) { gridYSpacing = spacing; }

	/**
	 * Method to return a rectangle in database coordinates that covers the viewable extent of this window.
	 * @return a rectangle that describes the viewable extent of this window (database coordinates).
	 */
	public Rectangle2D displayableBounds()
	{
		Point2D low = screenToDatabase(0, 0);
		Point2D high = screenToDatabase(sz.width-1, sz.height-1);
		double lowX = Math.min(low.getX(), high.getX());
		double lowY = Math.min(low.getY(), high.getY());
		double sizeX = Math.abs(high.getX()-low.getX());
		double sizeY = Math.abs(high.getY()-low.getY());
		Rectangle2D bounds = new Rectangle2D.Double(lowX, lowY, sizeX, sizeY);
		return bounds;
	}

	// *************************** SEARCHING FOR TEXT ***************************

	/** Information about String search */			private StringSearch textSearch = new StringSearch();

	private static class StringSearch implements Serializable
	{
		/** list of all found strings in the cell */	private List<StringsInCell> foundInCell;
		/** the currently reported string index */		private int currentFindPosition;

		private static Pattern getPattern(String search, boolean caseSensitive)
		{
			int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE+Pattern.UNICODE_CASE;
			Pattern p = null;

			try
			{
				p = Pattern.compile(search, flags);
			} catch (Exception e)
			{
				System.out.println("Error in regular expression '" + search + "'");
				System.out.println(e.getMessage());
			}
			return p;
		}

		private void searchTextNodes(Cell cell, String search, boolean caseSensitive, boolean regExp, Set<TextUtils.WhatToSearch> whatToSearch,
			CodeExpression.Code codeRestr, TextDescriptor.Unit unitRestr, Pattern pattern, Set<Geometric> examineThis)
		{
			boolean doTemp = whatToSearch.contains(TextUtils.WhatToSearch.TEMP_NAMES);
			TextUtils.WhatToSearch what = get(whatToSearch, TextUtils.WhatToSearch.NODE_NAME);
			TextUtils.WhatToSearch whatVar = get(whatToSearch, TextUtils.WhatToSearch.NODE_VAR);
			for(Iterator<NodeInst> it = cell.getNodes(); it.hasNext(); )
			{
				NodeInst ni = it.next();
				if (examineThis != null && !examineThis.contains(ni)) continue;
				if (what != null)
				{
					Name name = ni.getNameKey();
					if (doTemp || !name.isTempname())
					{
						findAllMatches(ni, NodeInst.NODE_NAME, 0, name.toString(),
							search, caseSensitive, regExp, pattern, codeRestr, unitRestr);
					}
				}
				if (whatVar != null)
					addVariableTextToList(ni, search, caseSensitive,regExp, pattern, codeRestr, unitRestr);
			}
		}

		private void searchTextArcs(Cell cell, String search, boolean caseSensitive, boolean regExp, Set<TextUtils.WhatToSearch> whatToSearch,
			CodeExpression.Code codeRestr, TextDescriptor.Unit unitRestr, Pattern pattern, Set<Geometric> examineThis)
		{
			boolean doTemp = whatToSearch.contains(TextUtils.WhatToSearch.TEMP_NAMES);
			TextUtils.WhatToSearch what = get(whatToSearch, TextUtils.WhatToSearch.ARC_NAME);
			TextUtils.WhatToSearch whatVar = get(whatToSearch, TextUtils.WhatToSearch.ARC_VAR);
			for(Iterator<ArcInst> it = cell.getArcs(); it.hasNext(); )
			{
				ArcInst ai = it.next();
				if (examineThis != null && !examineThis.contains(ai)) continue;
				if (what != null)
				{
					Name name = ai.getNameKey();
					if (doTemp || !name.isTempname())
					{
						findAllMatches(ai, ArcInst.ARC_NAME, 0, name.toString(),
							search, caseSensitive, regExp, pattern, codeRestr, unitRestr);
					}
				}
				if (whatVar != null)
					addVariableTextToList(ai, search, caseSensitive, regExp, pattern, codeRestr, unitRestr);
			}
		}

		private void searchTextExports(Cell cell, String search, boolean caseSensitive, boolean regExp, Set<TextUtils.WhatToSearch> whatToSearch,
			CodeExpression.Code codeRestr, TextDescriptor.Unit unitRestr, Pattern pattern, Set<Geometric> examineThis)
		{
			TextUtils.WhatToSearch what = get(whatToSearch, TextUtils.WhatToSearch.EXPORT_NAME);
			TextUtils.WhatToSearch whatVar = get(whatToSearch, TextUtils.WhatToSearch.EXPORT_VAR);
			for(Iterator<Export> it = cell.getExports(); it.hasNext(); )
			{
				Export pp = it.next();
				if (examineThis != null && !examineThis.contains(pp.getOriginalPort().getNodeInst())) continue;
				if (what != null)
				{
					Name name = pp.getNameKey();
					findAllMatches(pp, Export.EXPORT_NAME, 0, name.toString(),
						search, caseSensitive, regExp, pattern, codeRestr, unitRestr);
				}
				if (whatVar != null)
					addVariableTextToList(pp, search, caseSensitive, regExp, pattern, codeRestr, unitRestr);
			}
		}

		private void searchTextCellVars(Cell cell, String search, boolean caseSensitive, boolean regExp, Set<TextUtils.WhatToSearch> whatToSearch,
			CodeExpression.Code codeRestr, TextDescriptor.Unit unitRestr, Pattern pattern, Rectangle2D highBounds)
		{
			TextUtils.WhatToSearch whatVar = get(whatToSearch, TextUtils.WhatToSearch.CELL_VAR);
			if (whatVar != null)
			{
				for(Iterator<Variable> it = cell.getParametersAndVariables(); it.hasNext(); )
				{
					Variable var = it.next();
					if (!var.isDisplay()) continue;
					if (highBounds != null)
					{
						if (var.getXOff() < highBounds.getMinX() || var.getXOff() > highBounds.getMaxX() ||
							var.getYOff() < highBounds.getMinY() || var.getYOff() > highBounds.getMaxY()) continue;
					}
					findAllMatches(cell, var.getKey(), -1, var.getPureValue(-1),
							search, caseSensitive, regExp, pattern, codeRestr, unitRestr);
				}
			}
		}

		/**
		 * Method to change a string to another.
		 * @param index the entry in the array of replacements to change.
		 * @param rep the new string.
		 * @param cell the Cell in which these strings reside.
         * @param ep EditingPreferences with default TextDescriptors
		 */
		private void changeOneText(int index, String rep, Cell cell, EditingPreferences ep)
		{
			if (index < 0 || index >= foundInCell.size()) return;
			StringsInCell sic = foundInCell.get(index);
			String oldString = sic.theLine;
			String newString;
			if (sic.regExpSearch != null)
			{
				Pattern p = Pattern.compile(sic.regExpSearch);
				Matcher m = p.matcher(oldString);
				boolean found = m.find(sic.startPosition);
				Job.error(!found, "regExp find before replace failed");
				try
				{
					StringBuffer ns = new StringBuffer();
					m.appendReplacement(ns, rep);
					m.appendTail(ns);
					newString = ns.toString();
				} catch (Exception e)
				{
					System.out.println("Regular expression replace failed");
					newString = oldString;
				}
			} else
			{
				newString = oldString.substring(0, sic.startPosition) + rep + oldString.substring(sic.endPosition);
			}
			printChange(sic, newString);
			if (sic.object == null)
			{
				// cell variable name name
				if (cell.isParam(sic.key))
				{
					CellGroup cg = cell.getCellGroup();
					if (cg != null) cg.updateParamText((Variable.AttrKey)sic.key, newString);
				} else
					cell.updateVarText(sic.key, newString, ep);
			} else
			{
				if (sic.key == NodeInst.NODE_NAME)
				{
					// node name
					NodeInst ni = (NodeInst)sic.object;
					ni.setName(newString);
				} else if (sic.key == ArcInst.ARC_NAME)
				{
					// arc name
					ArcInst ai = (ArcInst)sic.object;
					ai.setName(newString, ep);
				} else if (sic.key == Export.EXPORT_NAME)
				{
					// export name
					Export pp = (Export)sic.object;
					pp.rename(newString);
				} else
				{
					// text on a variable
					ElectricObject base = (ElectricObject)sic.object;
					Variable var = base.getParameterOrVariable(sic.key);
					Object obj = var.getObject();
					if (obj instanceof String || obj instanceof CodeExpression)
					{
						base.updateVarText(sic.key, newString, ep);
					} else if (obj instanceof String[])
					{
						String [] oldLines = (String [])obj;
						String [] newLines = new String[oldLines.length];
						for(int i=0; i<oldLines.length; i++)
						{
							if (i == sic.lineInVariable) newLines[i] = newString; else
								newLines[i] = oldLines[i];
						}
						base.updateVar(sic.key, newLines, ep);
					}
				}
			}

			// because the replacement changes things, must update other search strings that point to the same place
			int delta = newString.length() - oldString.length();
			for(StringsInCell oSIC : foundInCell)
			{
				if (oSIC == sic) continue;
				if (oSIC.object != sic.object) continue;
				if (oSIC.key != sic.key) continue;
				if (oSIC.lineInVariable != sic.lineInVariable) continue;

				// part of the same string: update it
				oSIC.theLine = newString;
				if (oSIC.startPosition > sic.startPosition)
				{
					oSIC.startPosition += delta;
					oSIC.endPosition += delta;
				}
			}
		}

		private void replaceAllText(String replace, Cell cell, EditingPreferences ep)
		{
			if (foundInCell.isEmpty())
			{
				Toolkit.getDefaultToolkit().beep();
			} else
			{
				for(int i = 0; i < foundInCell.size(); i++)
					changeOneText(i, replace, cell, ep);
				System.out.println("Replaced " + foundInCell.size() + " times");
			}
		}

		/**
		 * Method to initialize for a new text
		 * @param search the string to locate.
		 * @param caseSensitive true to match only where the case is the same.
		 * @param regExp true if the search string is a regular expression.
		 * @param whatToSearch a collection of text types to consider.
		 * @param codeRestr a restriction on types of Code to consider (null to consider all Code values).
		 * @param unitRestr a restriction on types of Units to consider (null to consider all Unit values).
		 * @param highlightedOnly true to search only in the highlighted area.
		 * @param wnd the window in which search is being done (used if "highlightedOnly" is true).
		 */
		public void initTextSearch(Cell cell, String search, boolean caseSensitive, boolean regExp,
			Set<TextUtils.WhatToSearch> whatToSearch, CodeExpression.Code codeRestr, TextDescriptor.Unit unitRestr,
			boolean highlightedOnly, EditWindow wnd)
		{
			foundInCell = new ArrayList<StringsInCell>();
			if (cell == null)
			{
				System.out.println("No current Cell");
				return;
			}

			Pattern pattern = null;

			if (regExp)
			{
				pattern = getPattern(search, caseSensitive);
				if (pattern == null) return; // error
			}

			// see if searching the highlighted area only
			Set<Geometric> examineThis = null;
			Rectangle2D highBounds = null;
			if (highlightedOnly)
			{
				examineThis = new HashSet<Geometric>();
				highBounds = wnd.getHighlightedArea();
				List<Geometric> objs = wnd.getHighlighter().getHighlightedEObjs(true, true);
				for(Geometric obj : objs) examineThis.add(obj);
System.out.println("CONSIDERING "+examineThis.size()+" OBJECTS:");
for(Geometric g : examineThis) System.out.println("    "+g.describe(false));
//				if (highBounds != null)
//				{
//					List<Highlight> inArea = Highlighter.findAllInArea(wnd.getHighlighter(), cell, false,
//						false, false, true, true, highBounds, wnd);
//					for(Highlight h : inArea)
//					{
//						ElectricObject eo = h.getElectricObject();
//						if (eo instanceof PortInst) eo = ((PortInst)eo).getNodeInst();
//						if (eo instanceof Geometric) examineThis.add((Geometric)eo);
//					}
//				}
			}

			// initialize the search
			searchTextNodes(cell, search, caseSensitive, regExp, whatToSearch, codeRestr, unitRestr, pattern, examineThis);
			searchTextArcs(cell, search, caseSensitive, regExp, whatToSearch, codeRestr, unitRestr, pattern, examineThis);
			searchTextExports(cell, search, caseSensitive, regExp, whatToSearch, codeRestr, unitRestr, pattern, examineThis);
			searchTextCellVars(cell, search, caseSensitive, regExp, whatToSearch, codeRestr, unitRestr, pattern, highBounds);
			if (foundInCell.size() == 0) System.out.println("Nothing found");
			currentFindPosition = -1;
		}

		/**
		 * Method to find the next occurrence of a string.
		 * @param reverse true to find in the reverse direction.
		 * @return true if something was found.
		 */
		private boolean findNextText(Cell cell, Highlighter highlighter, boolean reverse)
		{
			int curPos = currentFindPosition;
			currentFindPosition = -1;
			for(int i=0; i<foundInCell.size(); i++)
			{
				if (reverse)
				{
					curPos--;
					if (curPos < 0) curPos = foundInCell.size()-1;
				} else
				{
					curPos++;
					if (curPos >= foundInCell.size()) curPos = 0;
				}
				if (!foundInCell.get(curPos).replaced)
				{
					currentFindPosition = curPos;
					break;
				}
			}
			if (currentFindPosition < 0) return false;
			StringsInCell sic = foundInCell.get(currentFindPosition);

			highlighter.clear();

			printFind(sic);
			if (sic.object == null)
			{
				highlighter.addText(cell, cell, sic.key);
			} else
			{
				ElectricObject eObj = (ElectricObject)sic.object;
				Variable.Key key = sic.key;
				if (key == null)
				{
					if (eObj instanceof Export) key = Export.EXPORT_NAME;
					else if (eObj instanceof ArcInst) key = ArcInst.ARC_NAME;
					else if (eObj instanceof NodeInst) key = NodeInst.NODE_NAME;
					assert(key != null);
				}
				highlighter.addText(eObj, cell, key);
			}
			highlighter.finished();
			return true;
		}

		/**
		 * Method to find all strings on a given database string, and add matches to the list.
		 * @param object the Object on which the string resides.
		 * @param key the Variable.key on which the string resides.
		 * @param lineInVariable the line number in arrayed variables.
		 * @param theLine the actual string from the database.
		 * @param search the string to find.
		 * @param caseSensitive true to do a case-sensitive
		 */
		private void findAllMatches(ElectricObject object, Variable.Key key, int lineInVariable,
			String theLine, String search, boolean caseSensitive, boolean regExp, Pattern p,
			CodeExpression.Code codeRestr, TextDescriptor.Unit unitRestr)
		{
			if (codeRestr != null || unitRestr != null)
			{
				Variable var = object.getVar(key);
				if (var != null)
				{
					if (codeRestr != null && var.getCode() != codeRestr) return;
					if (unitRestr != null && var.getUnit() != unitRestr) return;
				}
			}
			Matcher m = (p != null) ? p.matcher(theLine) : null; // p != null -> regExp

			for(int startPos = 0; ; )
			{
				int endPos;
				if (regExp)
				{
					boolean found = m.find();
					if (!found) break;
					startPos = m.start();
					endPos = m.end();
				} else
				{
					startPos = TextUtils.findStringInString(theLine, search, startPos, caseSensitive, false);
					if (startPos < 0) break;
					endPos = startPos + search.length();
				}
				String regExpSearch = regExp ? search : null;
				foundInCell.add(new StringsInCell(object instanceof Cell ? null : object, key, lineInVariable, theLine,
					startPos, endPos, regExpSearch));
				startPos = endPos;
			}
		}

		/**
		 * Method to all all displayable variable strings to the list of strings in the Cell.
		 * @param eObj the ElectricObject on which variables should be examined.
		 * @param search the string to find on the text.
		 * @param caseSensitive true to do a case-sensitive.
		 * @param regExp true to use regular-expression parsing.
		 * @param p the pattern to find.
		 * @param codeRestr a restriction on types of Code to consider (null to consider all Code values).
		 * @param unitRestr a restriction on types of Units to consider (null to consider all Unit values).
		 */
		private void addVariableTextToList(ElectricObject eObj, String search, boolean caseSensitive,
			boolean regExp, Pattern p, CodeExpression.Code codeRestr, TextDescriptor.Unit unitRestr)
		{
			for(Iterator<Variable> it = eObj.getParametersAndVariables(); it.hasNext(); )
			{
				Variable var = it.next();
				if (!var.isDisplay()) continue;
				Object obj = var.getObject();
				if (obj instanceof String || obj instanceof CodeExpression)
				{
					findAllMatches(eObj, var.getKey(), -1, obj.toString(), search, caseSensitive, regExp, p, codeRestr, unitRestr);
				} else if (obj instanceof String[])
				{
					String [] strings = (String [])obj;
					for(int i=0; i<strings.length; i++)
					{
						findAllMatches(eObj, var.getKey(), i, strings[i], search, caseSensitive, regExp, p, codeRestr, unitRestr);
					}
				}
			}
		}
	}

	/**
	 * Class to define a string found in a cell.
	 */
	private static class StringsInCell implements Serializable
	{
		/** the object that the string resides on */	final Object object;
		/** the Variable that the string resides on */	final Variable.Key key;
		/** the original string. */						String theLine;
		/** the line number in arrayed variables */		final int lineInVariable;
		/** the starting character position */			int startPosition;
		/** the ending character position */			int endPosition;
		/** the Regular Expression searched for */		final String regExpSearch;
		/** true if the replacement has been done */	boolean replaced;

		StringsInCell(Object object, Variable.Key key, int lineInVariable, String theLine, int startPosition,
			int endPosition, String regExpSearch)
		{
			this.object = object;
			assert(key!=null);
			this.key = key;
			this.lineInVariable = lineInVariable;
			this.theLine = theLine;
			this.startPosition = startPosition;
			this.endPosition = endPosition;
			this.regExpSearch = regExpSearch;
			this.replaced = false;
		}

        @Override
		public String toString() { return "StringsInCell obj="+object+" var="+key+
			/*" name="+name+*/" line="+lineInVariable+" start="+startPosition+" end="+endPosition+" msg="+theLine; }
	}

	private static TextUtils.WhatToSearch get(Set<TextUtils.WhatToSearch> whatToSearch, TextUtils.WhatToSearch what) {
		if (whatToSearch.contains(what)) return what;
		return null;
	}

	/**
	 * Method to initialize for a new text
	 * @param search the string to locate.
	 * @param caseSensitive true to match only where the case is the same.
	 * @param regExp true if the search string is a regular expression.
	 * @param whatToSearch a collection of text types to consider.
	 * @param codeRestr a restriction on types of Code to consider (null to consider all Code values).
	 * @param unitRestr a restriction on types of Units to consider (null to consider all Unit values).
	 * @param highlightedOnly true to search only in the highlighted area.
	 */
    @Override
	public void initTextSearch(String search, boolean caseSensitive, boolean regExp,
		Set<TextUtils.WhatToSearch> whatToSearch, CodeExpression.Code codeRestr, TextDescriptor.Unit unitRestr,
		boolean highlightedOnly)
	{
		textSearch.initTextSearch(cell, search, caseSensitive, regExp, whatToSearch, codeRestr, unitRestr, highlightedOnly, this);
	}

	private static String repeatChar(char c, int num) {
		StringBuffer sb = new StringBuffer();
		for (int i=0; i<num; i++) sb.append(c);
		return sb.toString();
	}

	private static void printFind(StringsInCell sic) {
		String foundHdr = "Found  "+sic.key+": ";
		String foundStr = sic.theLine;
		String highlightHdr = repeatChar(' ', foundHdr.length()+sic.startPosition);
		String highlight =	repeatChar('^', sic.endPosition-sic.startPosition);

		System.out.println(foundHdr+foundStr+"\n"+
						   highlightHdr+highlight);
	}

	/**
	 * Method to find the next occurrence of a string.
	 * @param reverse true to find in the reverse direction.
	 * @return true if something was found.
	 */
    @Override
	public boolean findNextText(boolean reverse)
	{
		return textSearch.findNextText(cell, highlighter, reverse);
	}

	/**
	 * Method to replace the text that was just selected with findNextText().
	 * @param replace the new text to replace.
	 */
    @Override
	public void replaceText(String replace)
	{
		int pos = textSearch.currentFindPosition;
		if (pos < 0 || pos >= textSearch.foundInCell.size()) return;
		StringsInCell sic = textSearch.foundInCell.get(pos);
		if (sic.replaced) return;

		// mark this replacement done
		sic.replaced = true;

		new ReplaceTextJob(this, replace);
	}

	/**
	 * Method to replace all selected text.
	 * @param replace the new text to replace everywhere.
	 */
    @Override
	public void replaceAllText(String replace)
	{
		// remove replacements already done
		for(int i = textSearch.foundInCell.size()-1; i >= 0; i--)
		{
			StringsInCell sic = textSearch.foundInCell.get(i);
			if (sic.replaced) textSearch.foundInCell.remove(i);
		}

		// mark all of these changes as done
		for(StringsInCell sic : textSearch.foundInCell) sic.replaced = true;

		// replace everything
		new ReplaceAllTextJob(this, replace);
	}

	/**
	 * Class to change text in a new thread.
	 */
	private static class ReplaceTextJob extends Job
	{
		private String replace;
		private Cell cell;
		private StringSearch search;

		private ReplaceTextJob(EditWindow wnd, String replace)
		{
			super("Replace Text", User.getUserTool(), Job.Type.CHANGE, null, null, Job.Priority.USER);
			this.search = wnd.textSearch;
			this.cell = wnd.cell;
			this.replace = replace;
			startJob();
		}

        @Override
		public boolean doIt() throws JobException
		{
			search.changeOneText(search.currentFindPosition, replace, cell, getEditingPreferences());
			fieldVariableChanged("search");
			return true;
		}
	}

	/**
	 * Class to change text in a new thread.
	 */
	private static class ReplaceAllTextJob extends Job
	{
		private StringSearch search;
		private String replace;
		private Cell cell;

		public ReplaceAllTextJob(EditWindow wnd, String replace)
		{
			super("Replace All Text", User.getUserTool(), Job.Type.CHANGE, null, null, Job.Priority.USER);
			this.search = wnd.textSearch;
			this.replace = replace;
			this.cell = wnd.cell;
			startJob();
		}

        @Override
		public boolean doIt() throws JobException
		{
			search.replaceAllText(replace, cell, getEditingPreferences());
			fieldVariableChanged("search");
			return true;
		}
	}

	private static void printChange(StringsInCell sic, String newString)
	{
		String foundHdr = "Change "+sic.key+": ";
		String foundStr = sic.theLine;
		String replaceHdr = "  ->  ";
		String replaceStr = newString;
		String highlightHdr = repeatChar(' ', foundHdr.length()+sic.startPosition);
		String highlightStr = repeatChar('^', sic.endPosition-sic.startPosition);
		System.out.println(foundHdr + foundStr + replaceHdr+replaceStr + "\n" + highlightHdr + highlightStr);
	}

	// ************************************* POPUP CLOUD *************************************

	public boolean getShowPopupCloud() { return showPopupCloud; }

	public void setShowPopupCloud(List<String> text, Point2D point)
	{
		showPopupCloud = true;
//		popupCloudText = text;
//		popupCloudPoint = point;
	}

	public void clearShowPopupCloud() { showPopupCloud = false; }

	private void drawPopupCloud(Graphics2D g)
	{
		// JKG NOTE: disabled for now
		// must decide whether or not this is useful
		/*
		if (popupCloudText == null || popupCloudText.size() == 0) return;
		// draw cloud
		float yspacing = 5;
		float x = (float)popupCloudPoint.getX() + 25;
		float y = (float)popupCloudPoint.getY() + 10 + yspacing;
		for (int i=0; i<popupCloudText.size(); i++) {
			GlyphVector glyph = getFont().createGlyphVector(g.getFontRenderContext(), popupCloudText.get(i));
			g.drawGlyphVector(glyph, x, y);
			y += glyph.getVisualBounds().getHeight() + yspacing;
		}
		*/
	}

	// ************************************* WINDOW ZOOM AND PAN *************************************

	/**
	 * Method to return the size of this EditWindow.
	 * @return a Dimension with the size of this EditWindow.
	 */
	public Dimension getScreenSize() { return sz; }

	/**
	 * Method to return the scale factor for this window.
	 * @return the scale factor for this window.
	 */
    @Override
	public double getScale() { return scale; }

	/**
	 * Method to return the text scale factor for this window.
	 * @return the text scale factor for this window.
	 */
    @Override
	public double getGlobalTextScale() { return globalTextScale; }

    /**
     * Method to return the default font for this window.
     * @return the default font for this window.
     */
    @Override
    public String getDefaultFont() { return defaultFont; }

	/**
	 * Method to set the text scale factor for this window.
	 * @param gts the text scale factor for this window.
	 */
	public void setGlobalTextScale(double gts) { globalTextScale = gts; }

	/**
	 * Method to set the scale factor for this window.
	 * @param scale the scale factor for this window.
	 */
    @Override
	public void setScale(double scale)
	{
		if (scale <= 0)
			throw new IllegalArgumentException("Negative window scale");
		scaleRequested = scale;
		removeAllInPlaceTextObjects();
	}

	/**
	 * Method to return the offset factor for this window.
	 * @return the offset factor for this window.
	 */
    @Override
	public Point2D getOffset() { return new Point2D.Double(offx, offy); }

	/**
	 * Method to return the offset factor for this window.
	 * If new offsets are queued, this gets the ultimate offset value.
	 * @return the offset factor for this window.
	 */
	public Point2D getScheduledOffset() { return new Point2D.Double(offxRequested, offyRequested); }

	/**
	 * Method to set the offset factor for this window.
	 * @param off the offset factor for this window.
	 */
    @Override
	public void setOffset(Point2D off)
	{
		offxRequested = off.getX();
		offyRequested = off.getY();
		removeAllInPlaceTextObjects();
	}

	private void setScreenBounds(Rectangle2D bounds)
	{
		double width = bounds.getWidth();
		double height = bounds.getHeight();
		if (width == 0) width = 2;
		if (height == 0) height = 2;
		double scalex = sz.width/width * 0.9;
		double scaley = sz.height/height * 0.9;
		setScale(Math.min(scalex, scaley));
		setOffset(new Point2D.Double(bounds.getCenterX(), bounds.getCenterY()));
	}

	/**
	 * Method to return the extent of the screen display.
	 */
    @Override
	public Rectangle2D getDisplayedBounds()
	{
		double width = sz.width/scale;
		double height = sz.height/scale;
		return new Rectangle2D.Double(offx - width/2, offy - height/2, width, height);
	}

	private static final double scrollPagePercent = 0.2;
	// ignore programmatic scroll changes. Only respond to user scroll changes
	private boolean ignoreScrollChange = false;
	private static final int scrollRangeMult = 100; // when zoomed in, this prevents rounding from causing problems

	/**
	 * New version of setScrollPosition.  Attempts to provides means of scrolling
	 * out of cell bounds.
	 */
	public void setScrollPosition() {
		if (!SwingUtilities.isEventDispatchThread()) {
			SwingUtilities.invokeLater(new Runnable() {
                @Override
				public void run() { setScrollPositionUnsafe(); }
			});
		} else
			setScrollPositionUnsafe();
	}

	/**
	 * New version of setScrollPosition.  Attempts to provides means of scrolling
	 * out of cell bounds.  This is the Swing unsafe version
	 */
	private void setScrollPositionUnsafe()
	{
		if (bottomScrollBar == null || rightScrollBar == null) return;
		bottomScrollBar.setEnabled(cell != null);
		rightScrollBar.setEnabled(cell != null);

		if (cell == null) return;

		Rectangle2D cellBounds = getInPlaceEditTopCell().getBounds();
		if (cellBounds == null) return;
		Rectangle2D viewBounds = displayableBounds();

		// scroll bar is being repositioned: ignore the change events it generates
		ignoreScrollChange = true;

		double width = (viewBounds.getWidth() < cellBounds.getWidth()) ? viewBounds.getWidth() : cellBounds.getWidth();
		double height = (viewBounds.getHeight() < cellBounds.getHeight()) ? viewBounds.getHeight() : cellBounds.getHeight();

		Point2D dbPt = new Point2D.Double(offx, offy);
		double oX = dbPt.getX();   double oY = dbPt.getY();

		if (!bottomScrollBar.getValueIsAdjusting())
		{
			int value = (int)((oX-0.5*width)*scrollRangeMult);
			int extent = (int)(width*scrollRangeMult);
			int min = (int)((cellBounds.getX() - scrollPagePercent*cellBounds.getWidth())*scrollRangeMult);
			int max = (int)(((cellBounds.getX()+cellBounds.getWidth()) + scrollPagePercent*cellBounds.getWidth())*scrollRangeMult);
			bottomScrollBar.getModel().setRangeProperties(value, extent, min, max, false);
			bottomScrollBar.setUnitIncrement((int)(0.05*viewBounds.getWidth()*scrollRangeMult));
			bottomScrollBar.setBlockIncrement((int)(scrollPagePercent*viewBounds.getWidth()*scrollRangeMult));
		}
		if (!rightScrollBar.getValueIsAdjusting())
		{
			int value = (int)((-oY-0.5*height)*scrollRangeMult);
			int extent = (int)(height*scrollRangeMult);
			int min = (int)((-((cellBounds.getY()+cellBounds.getHeight()) + scrollPagePercent*cellBounds.getHeight()))*scrollRangeMult);
			int max = (int)((-(cellBounds.getY() - scrollPagePercent*cellBounds.getHeight()))*scrollRangeMult);
			rightScrollBar.getModel().setRangeProperties(value, extent, min, max, false);
			rightScrollBar.setUnitIncrement((int)(0.05*viewBounds.getHeight()*scrollRangeMult));
			rightScrollBar.setBlockIncrement((int)(scrollPagePercent*viewBounds.getHeight()*scrollRangeMult));
		}

		ignoreScrollChange = false;
	}

    @Override
	public void bottomScrollChanged(int value)
	{
		if (cell == null) return;
		if (ignoreScrollChange) return;

		Point2D dbPt = new Point2D.Double(offx, offy);
		double oY = dbPt.getY();

		double val = (double)value/(double)scrollRangeMult;
		Rectangle2D cellBounds = getInPlaceEditTopCell().getBounds();
		Rectangle2D viewBounds = displayableBounds();
		double width = (viewBounds.getWidth() < cellBounds.getWidth()) ? viewBounds.getWidth() : cellBounds.getWidth();
		double newoffx = val+0.5*width;			// new offset
		Point2D offset = new Point2D.Double(newoffx, oY);
		setOffset(offset);
		getSavedFocusBrowser().updateCurrentFocus();
		fullRepaint();
	}

    @Override
	public void rightScrollChanged(int value)
	{
		if (cell == null) return;
		if (ignoreScrollChange) return;

		Point2D dbPt = new Point2D.Double(offx, offy);
		double oX = dbPt.getX();

		double val = (double)value/(double)scrollRangeMult;
		Rectangle2D cellBounds = getInPlaceEditTopCell().getBounds();
		Rectangle2D viewBounds = displayableBounds();
		double height = (viewBounds.getHeight() < cellBounds.getHeight()) ? viewBounds.getHeight() : cellBounds.getHeight();
		double newoffy = -(val+0.5*height);
		Point2D offset = new Point2D.Double(oX, newoffy);
		setOffset(offset);
		getSavedFocusBrowser().updateCurrentFocus();
		fullRepaint();
	}

	/**
	 * Method to focus the screen so that an area fills it.
	 * @param bounds the area to make fill the screen.
	 */
	public void focusScreen(Rectangle2D bounds)
	{
		if (bounds == null) return;
		if (inPlaceDisplay)
		{
			Point2D llPt = new Point2D.Double(bounds.getMinX(), bounds.getMinY());
			Point2D urPt = new Point2D.Double(bounds.getMaxX(), bounds.getMaxY());
			outofCell.transform(llPt, llPt);
			outofCell.transform(urPt, urPt);
			double lX = Math.min(llPt.getX(), urPt.getX());
			double hX = Math.max(llPt.getX(), urPt.getX());
			double lY = Math.min(llPt.getY(), urPt.getY());
			double hY = Math.max(llPt.getY(), urPt.getY());
			bounds = new Rectangle2D.Double(lX, lY, hX-lX, hY-lY);
		}
		setScreenBounds(bounds);
		setScrollPosition();
		getSavedFocusBrowser().saveCurrentFocus();
		fullRepaint();
	}

	/**
	 * Method to compute the bounds of the cell in this EditWindow.
	 * Bounds includes factors such as frame size and large text.
	 * @return the bounds of the cell in this EditWindow.
	 */
    @Override
	public Rectangle2D getBoundsInWindow()
	{
		Rectangle2D cellBounds = cell.getBounds();
		Dimension d = new Dimension();
		int frameFactor = Cell.FrameDescription.getCellFrameInfo(cell, d);
		Rectangle2D frameBounds = new Rectangle2D.Double(-d.getWidth()/2, -d.getHeight()/2, d.getWidth(), d.getHeight());
		if (frameFactor == 0)
		{
			cellBounds = frameBounds;
			if (cell.isMultiPage())
			{
				double offY = pageNumber * Cell.FrameDescription.MULTIPAGESEPARATION;
				cellBounds.setRect(cellBounds.getMinX(), cellBounds.getMinY() + offY, cellBounds.getWidth(), cellBounds.getHeight());
			}
		} else
		{
			if (cellBounds.getWidth() == 0 && cellBounds.getHeight() == 0)
			{
				int defaultCellSize = 60;
				cellBounds = new Rectangle2D.Double(cellBounds.getCenterX()-defaultCellSize/2,
					cellBounds.getCenterY()-defaultCellSize/2, defaultCellSize, defaultCellSize);
			}

			// make sure text fits
//			double oldScale = getScale();
//			Point2D oldOffset = getOffset();
			setScreenBounds(cellBounds);
			EditWindowSmall ew0 = new EditWindowSmall(this);
//			if (scale != scaleRequested) textInCell = null;
//			scale = scaleRequested;
//			scale_ = (float)(scale/DBMath.GRID);
//			factorX = (float)(offx*DBMath.GRID - szHalfWidth/scale_);
//			factorY = (float)(offy*DBMath.GRID + szHalfHeight/scale_);
			Rectangle2D relativeTextBounds = cell.getRelativeTextBounds(ew0);
			if (relativeTextBounds != null)
			{
				Rectangle2D newCellBounds = new Rectangle2D.Double();
				Rectangle2D.union(relativeTextBounds, cellBounds, newCellBounds);

				// do it twice more to get closer to the actual text size
				for(int i=0; i<2; i++)
				{
					setScreenBounds(newCellBounds);
					ew0 = new EditWindowSmall(this);
//					if (scale != scaleRequested) textInCell = null;
//					scale = scaleRequested;
//					scale_ = (float)(scale/DBMath.GRID);
//					factorX = (float)(offx*DBMath.GRID - szHalfWidth/scale_);
//					factorY = (float)(offy*DBMath.GRID + szHalfHeight/scale_);
					relativeTextBounds = cell.getRelativeTextBounds(ew0);
					if (relativeTextBounds != null)
						Rectangle2D.union(relativeTextBounds, cellBounds, newCellBounds);
				}
				cellBounds = newCellBounds;
			}
//			setScale(oldScale);
//			setOffset(oldOffset);

			// make sure title box fits (if there is just a title box)
			if (frameFactor == 1)
			{
				Rectangle2D.union(frameBounds, cellBounds, frameBounds);
				cellBounds = frameBounds;
			}
		}
		return cellBounds;
	}

	// Highlighting methods for the EditWindow_ interface
    @Override
	public Highlight addElectricObject(ElectricObject eObj, Cell cell)
	{
		return highlighter.addElectricObject(eObj, cell);
	}

    @Override
	public Rectangle2D getHighlightedArea()
	{
		return highlighter.getHighlightedArea(this);
	}

    @Override
	public Highlight addHighlightArea(Rectangle2D rect, Cell cell)
	{
		return highlighter.addArea(rect, cell);
	}

    @Override
	public Highlight addHighlightLine(Point2D pt1, Point2D pt2, Cell cell, boolean thick, boolean isError)
	{
		return highlighter.addLine(pt1, pt2, cell, thick, isError);
	}

    @Override
	public Highlight addHighlightMessage(Cell cell, String message, Point2D loc)
	{
		return highlighter.addMessage(cell, message, loc);
	}

    @Override
	public Highlight addHighlightText(ElectricObject eObj, Cell cell, Variable.Key varKey)
	{
		return highlighter.addText(eObj, cell, varKey);
	}

    @Override
	public ElectricObject getOneElectricObject(Class<?> clz) { return highlighter.getOneElectricObject(clz); }

    @Override
	public List<Geometric> getHighlightedEObjs(boolean wantNodes, boolean wantArcs)
	{
		return highlighter.getHighlightedEObjs(wantNodes, wantArcs);
	}

    @Override
	public Set<Network> getHighlightedNetworks()
	{
		return highlighter.getHighlightedNetworks();
	}

    @Override
	public Point2D getHighlightOffset()
	{
		return highlighter.getHighlightOffset();
	}

    @Override
	public void setHighlightOffset(int dX, int dY)
	{
		highlighter.setHighlightOffset(dX, dY);
	}

    @Override
	public List<Highlight> saveHighlightList()
	{
		List<Highlight> saveList = new ArrayList<Highlight>();
		for(Highlight h : highlighter.getHighlights())
			saveList.add(h);
		return saveList;
	}

    @Override
	public void restoreHighlightList(List<Highlight> list)
	{
		highlighter.setHighlightListGeneral(list);
	}

    @Override
	public void removeHighlight(Highlight h)
    {
		highlighter.remove(h);
    }

    @Override
	public void clearHighlighting()
	{
		highlighter.clear();
	}

    @Override
	public void finishedHighlighting()
	{
		highlighter.finished();
	}

	/**
	 * Method to pan and zoom the screen so that the entire cell is displayed.
	 */
    @Override
	public void fillScreen()
	{
		if (cell != null)
		{
			if (!cell.getView().isTextView())
			{
				Rectangle2D cellBounds = getBoundsInWindow();
				focusScreen(cellBounds);
				return;
			}
		}
		getSavedFocusBrowser().saveCurrentFocus();
		repaint();
	}

    @Override
	public void zoomOutContents()
	{
		double scale = getScale();
		setScale(scale / 2);
		getSavedFocusBrowser().saveCurrentFocus();
		fullRepaint();
	}

    @Override
	public void zoomInContents()
	{
		double scale = getScale();
		setScale(scale * 2);
		getSavedFocusBrowser().saveCurrentFocus();
		fullRepaint();
	}

    @Override
	public void focusOnHighlighted()
	{
		// focus on highlighting
		Rectangle2D bounds = highlighter.getHighlightedArea(this);
		focusScreen(bounds);
	}

	/**
	 * Get the Saved View Browser for this Edit Window
	 */
	public EditWindowFocusBrowser getSavedFocusBrowser() { return viewBrowser; }

	// ************************************* HIERARCHY TRAVERSAL *************************************

	/**
	 * Get the window's VarContext
	 * @return the current VarContext
	 */
    @Override
	public VarContext getVarContext() { return cellVarContext; }

	/**
	 * Push into an instance (go down the hierarchy)
	 * @param keepFocus true to keep the zoom and scale in the new window.
	 * @param newWindow true to create a new window for the cell.
	 * @param inPlace true to descend "in-place" showing the higher levels.
	 */
	public void downHierarchy(boolean keepFocus, boolean newWindow, boolean inPlace)
	{
		// get highlighted
		Highlight h = highlighter.getOneHighlight();
		if (h == null) return;
		ElectricObject eobj = h.getElectricObject();

		NodeInst ni = null;
		PortInst pi = null;

		// see if a nodeinst was highlighted (true if text on nodeinst was highlighted)
		if (eobj instanceof NodeInst) ni = (NodeInst)eobj;

		// see if portinst was highlighted
		if (eobj instanceof PortInst)
		{
			pi = (PortInst)eobj;
			ni = pi.getNodeInst();
		}
		if (ni == null)
		{
			System.out.println("Must select a Node to descend into");
			return;
		}
		if (!ni.isCellInstance())
		{
			System.out.println("Can only descend into cell instances");
			return;
		}
		Cell cell = (Cell)ni.getProto();
		Cell schCell = cell.getEquivalent();

		// special case: if cell is icon of current cell, descend into icon
		if (this.cell == schCell) schCell = cell;
		if (schCell == null) schCell = cell;

		// when editing in-place, always descend to the icon
		if (inPlace) schCell = cell;

		// determine display factors for new cell
		double offX = offx;
		double offY = offy;
		if (keepFocus)
		{
			offX -= ni.getAnchorCenterX();
			offY -= ni.getAnchorCenterY();
		}

		// handle in-place display
		List<NodeInst> newInPlaceDescent = new ArrayList<NodeInst>();
		if (inPlace)
		{
			newInPlaceDescent.addAll(inPlaceDescent);
			newInPlaceDescent.add(ni);
		}
		WindowFrame.DisplayAttributes da = new WindowFrame.DisplayAttributes(scale, offX, offY, newInPlaceDescent);

		// for stacked NodeInsts, must choose which one
		Nodable desiredNO = null;
		List<Nodable> possibleNodables = new ArrayList<Nodable>();
		Netlist nl = ni.getParent().getNetlist();
		if (nl == null)
		{
			System.out.println("Netlist is not ready");
			return;
		}
		for(Iterator<Nodable> it = nl.getNodables(); it.hasNext(); )
		{
			Nodable no = it.next();
			if (no.getNodeInst() == ni)
			{
				possibleNodables.add(no);
			}
		}
		if (possibleNodables.size() > 1)
		{
			desiredNO = possibleNodables.get(0);

			// see if there are any waveform windows
			boolean promptUser = isArrayedContextMatter(desiredNO);
			if (promptUser)
			{
				String [] manyOptions = new String[possibleNodables.size()];
				int i = 0;
				for(Nodable no : possibleNodables)
					manyOptions[i++] = no.getName();
				String chosen = (String)JOptionPane.showInputDialog(TopLevel.getCurrentJFrame(), "Descend into which node?",
					"Choose a Node", JOptionPane.QUESTION_MESSAGE, null, manyOptions, manyOptions[0]);
				if (chosen == null) return;
				for(Nodable no : possibleNodables)
				{
					if (no.getName().equals(chosen)) { desiredNO = no;   break; }
				}
			}
		}

		// do the descent
		boolean redisplay = true;
		if (inPlace) redisplay = false;
		if (keepFocus) redisplay = false;
		EditWindow newWND = this;
		if (newWindow)
		{
			WindowFrame newWF = WindowFrame.createEditWindow(schCell);
			newWND = (EditWindow)newWF.getContent();
		}
		else
			SelectObject.selectObjectDialog(schCell, true);

		VarContext vc;
		if (desiredNO != null)
		{
			vc = cellVarContext.push(desiredNO);
		} else
		{
			vc = cellVarContext.push(ni);
		}
		newWND.showCell(schCell, vc, redisplay, da);
		newWND.getWindowFrame().addToHistory(schCell, vc, da);
		if (!redisplay) fullRepaint();
		clearSubCellCache();

		// if highlighted was a port instance, then highlight the corresponding export
		if (pi != null)
		{
			Export schExport = schCell.findExport(pi.getPortProto().getName());
			if (schExport != null)
			{
				PortInst origPort = schExport.getOriginalPort();
				newWND.highlighter.addElectricObject(origPort, schCell);
				newWND.highlighter.finished();
			}
		}
	}

	/**
	 * Returns true if, in terms of context, it matters which index
	 * of the nodable we push into.
	 * @param no the nodable
	 * @return true if we need to ask the user which index to descent into,
	 * false otherwise.
	 */
	public static boolean isArrayedContextMatter(Nodable no)
	{
		// matters if the user requested it
		if (User.isPromptForIndexWhenDescending()) return true;

		// matters if there is a waveform window open
		for(Iterator<WindowFrame> it = WindowFrame.getWindows(); it.hasNext(); )
		{
			WindowFrame wf = it.next();
			if (wf.getContent() instanceof WaveformWindow) return true;
		}

		// if Logical Effort "getdrive" is called
		for (Iterator<Variable> it = no.getDefinedParameters(); it.hasNext(); ) {
			Variable var = it.next();
			Object obj = var.getObject();
			if (obj instanceof CodeExpression) {
				String str = obj.toString();
				if (str.matches(".*LE\\.getdrive.*")) return true;
			}
		}

		// does not matter
		return false;
	}

	/**
	 * Pop out of an instance (go up the hierarchy)
	 */
	public void upHierarchy(boolean keepFocus)
	{
		if (cell == null) return;
		Cell oldCell = cell;

		// when doing in-place display, always go up keeping the focus
		if (inPlaceDisplay) keepFocus = true;

		// determine which export is selected so it can be shown in the upper level
		Export selectedExport = null;
		Set<Network> nets = highlighter.getHighlightedNetworks();
		for(Network net : nets)
		{
			if (net == null)
			{
				if (Job.getDebug())
					System.out.println("Getting null net in EditWindow::upHierarchy. Check this case");
				continue;
			}
			for(Iterator<Export> eIt = net.getExports(); eIt.hasNext(); )
			{
				Export e = eIt.next();
				selectedExport = e;
				break;
			}
			if (selectedExport != null) break;
		}
        if (selectedExport != null && !selectedExport.isLinked()) {
            selectedExport = null;
        }

		try {
			Nodable no = cellVarContext.getNodable();
			if (no != null && no.getNodeInst().isLinked())
			{
                NodeInst ni = no.getNodeInst();
                if (selectedExport != null && selectedExport.getParent() != ni.getProto())
                {
                    if (ni.isCellInstance() && selectedExport.getParent() == ((Cell)ni.getProto()).getEquivalent())
                    {
                    	Cell otherCell = (Cell)ni.getProto();
                    	selectedExport = selectedExport.findEquivalent(otherCell);
                    } else
                        selectedExport = null;
                }
				Cell parent = no.getParent();

				// see if this was in history, if so, restore offset and scale
				// search backwards to get most recent entry
				// search history **before** calling setCell, otherwise we find
				// the history record for the cell we just switched to
				VarContext context = cellVarContext.pop();
				int historyIndex = wf.findCellHistoryIndex(parent, context);
				if (historyIndex >= 0)
				{
					// found previous in cell history: show it
					WindowFrame.CellHistory foundHistory = wf.getCellHistoryList().get(historyIndex);
					foundHistory.setContext(context);
					if (selectedExport != null)
						foundHistory.setSelPort(ni.findPortInstFromEquivalentProto(selectedExport));
					if (keepFocus)
					{
						double newX = offx, newY = offy;
						if (!inPlaceDisplay)
						{
							Point2D curCtr = new Point2D.Double(offx, offy);
							FixpTransform up = ni.rotateOut(no.getNodeInst().translateOut());
							up.transform(curCtr, curCtr);
							newX = curCtr.getX();
							newY = curCtr.getY();
						}
						List<NodeInst> oldInPlaceDescent = foundHistory.getDisplayAttributes().inPlaceDescent;
						foundHistory.setDisplayAttributes(new WindowFrame.DisplayAttributes(scale, newX, newY, oldInPlaceDescent));
					}
					wf.setCellByHistory(historyIndex);
				} else
				{
					// no previous history: make one up
					List<NodeInst> newInPlaceDescent = new ArrayList<NodeInst>(inPlaceDescent);
					if (!newInPlaceDescent.isEmpty())
						newInPlaceDescent.remove(newInPlaceDescent.size() - 1);
					double newX = offx, newY = offy;
					if (keepFocus)
					{
						Point2D curCtr = new Point2D.Double(offx, offy);
						FixpTransform up = ni.rotateOut(ni.translateOut());
						up.transform(curCtr, curCtr);
						newX = curCtr.getX();
						newY = curCtr.getY();
					}
					WindowFrame.DisplayAttributes da = new WindowFrame.DisplayAttributes(scale, newX, newY, newInPlaceDescent);
					showCell(parent, context, true, da);
					wf.addToHistory(parent, context, da);

					// highlight node we came from
					PortInst pi = null;
					if (selectedExport != null)
						pi = ni.findPortInstFromEquivalentProto(selectedExport);
					highlighter.clear();
					if (pi != null)
						highlighter.addElectricObject(pi, parent);
					else
						highlighter.addElectricObject(ni, parent);
				}
				clearSubCellCache();

				// highlight portinst selected at the time, if any
				SelectObject.selectObjectDialog(parent, true);
				return;
			}

			// no parent - if icon, go to schematic view
			if (cell.isIcon())
			{
				Cell schCell = cell.getEquivalent();
				if (schCell != null)
				{
					WindowFrame.DisplayAttributes da = new WindowFrame.DisplayAttributes(getScale(),
						getOffset().getX(), getOffset().getY(), new ArrayList<NodeInst>());
					wf.addToHistory(schCell, VarContext.globalContext, da);

					setCell(schCell, VarContext.globalContext, null);
					SelectObject.selectObjectDialog(schCell, true);
					return;
				}
			}

			// find all possible parents in all libraries
			Set<Cell> found = new HashSet<Cell>();
			for(Iterator<NodeInst> it = cell.getInstancesOf(); it.hasNext(); )
			{
				NodeInst ni = it.next();
				Cell parent = ni.getParent();
				if (parent.getLibrary().isHidden()) continue;
				found.add(parent);
			}
			if (cell.isSchematic())
			{
				for(Cell iconView : cell.getCellsInGroup())
				{
					if (iconView.getView() != View.ICON) continue;
					for(Iterator<NodeInst> it = iconView.getInstancesOf(); it.hasNext(); )
					{
						NodeInst ni = it.next();
						if (ni.isIconOfParent()) continue;
						Cell parent = ni.getParent();
						if (parent.getLibrary().isHidden()) continue;
						found.add(parent);
					}
				}
			}

			// see what was found
			if (found.size() == 0)
			{
				// no parent cell
				System.out.println("Not in any cells");
			} else if (found.size() == 1)
			{
				// just one parent cell: show it
				Cell parent = found.iterator().next();

				// find the instance in the parent
				NodeInst theOne = null;
				for (Iterator<NodeInst> it = parent.getNodes(); it.hasNext(); )
				{
					NodeInst ni = it.next();
					if (ni.isCellInstance())
					{
						Cell nodeCell = (Cell)ni.getProto();
						if (nodeCell == oldCell || nodeCell.isIconOf(oldCell))
						{
							theOne = ni;
							break;
						}
					}
				}

				double newX = offx, newY = offy;
				if (keepFocus)
				{
					Point2D curCtr = new Point2D.Double(offx, offy);
					FixpTransform up = theOne.rotateOut(theOne.translateOut());
					up.transform(curCtr, curCtr);
					newX = curCtr.getX();
					newY = curCtr.getY();
				}
				WindowFrame.DisplayAttributes da = new WindowFrame.DisplayAttributes(getScale(), newX, newY, new ArrayList<NodeInst>());
				wf.addToHistory(parent, VarContext.globalContext, da);
				setCell(parent, VarContext.globalContext, da);
				if (!keepFocus) fillScreen();

				// highlight instance
				if (theOne != null)
				{
					highlighter.clear();
					if (selectedExport != null)
						highlighter.addElectricObject(theOne.findPortInstFromEquivalentProto(selectedExport), parent); else
							highlighter.addElectricObject(theOne, parent);
					highlighter.finished();
				}
				SelectObject.selectObjectDialog(parent, true);
			} else
			{
				// prompt the user to choose a parent cell

				// BUG #436: The following code creates and displays a popup menu
				// with a list of cell names caused by an ambiguous "Up Hierarchy" command.
				// The popup does not allow the up/down arrows to scroll through it.
				// Unfortunately, this is a known bug in Java's JPopupMenu object.
				// I also tried: JPopupMenu.setDefaultLightWeightPopupEnabled(false);
				JPopupMenu parents = new JPopupMenu("parents");
				for(Cell parent : found)
				{
					String cellName = parent.describe(false);
					JMenuItem menuItem = new JMenuItem(cellName);
					menuItem.addActionListener(new UpHierarchyPopupListener(keepFocus));
					parents.add(menuItem);
				}
				parents.show(overall, 0, 0);
			}
		} catch (NullPointerException e)
		{
			ActivityLogger.logException(e);
		}
	}

	private class UpHierarchyPopupListener implements ActionListener
	{
		private boolean keepFocus;

		UpHierarchyPopupListener(boolean k) { keepFocus = k; }

		/**
		 * Respond to an action performed, in this case change the current cell
		 * when the user clicks on an entry in the upHierarchy popup menu.
		 */
        @Override
		public void actionPerformed(ActionEvent e)
		{
			JMenuItem source = (JMenuItem)e.getSource();
			// extract library and cell from string
			Cell cell = (Cell)Cell.findNodeProto(source.getText());
			if (cell == null) return;
			Cell currentCell = getCell();

			// find one of these nodes in the upper cell
			NodeInst theOne = null;
			for (Iterator<NodeInst> it = cell.getNodes(); it.hasNext(); )
			{
				NodeInst ni = it.next();
				if (ni.isCellInstance())
				{
					Cell nodeCell = (Cell)ni.getProto();
					if (nodeCell == currentCell)
					{
						theOne = ni;
						break;
					}
					if (nodeCell.isIconOf(currentCell))
					{
						theOne = ni;
						break;
					}
				}
			}

			double newX = offx, newY = offy;
			if (keepFocus)
			{
				Point2D curCtr = new Point2D.Double(offx, offy);
				FixpTransform up = theOne.rotateOut(theOne.translateOut());
				up.transform(curCtr, curCtr);
				newX = curCtr.getX();
				newY = curCtr.getY();
			}
			WindowFrame.DisplayAttributes da = new WindowFrame.DisplayAttributes(getScale(), newX, newY, new ArrayList<NodeInst>());
			wf.addToHistory(cell, VarContext.globalContext, da);
			setCell(cell, VarContext.globalContext, da);
			if (!keepFocus) fillScreen();

			// Highlight an instance of cell we came from in current cell
			highlighter.clear();
			if (theOne != null) highlighter.addElectricObject(theOne, cell);
			highlighter.finished();
		}
	}

	/**
	 * Method to clear the cache of expanded subcells.
	 * This is used by layer visibility which, when changed, causes everything to be redrawn.
	 */
	public static void clearSubCellCache()
	{
		AbstractDrawing.clearSubCellCache(true);
	}

	/**
	 * Handles database changes of a Job.
	 * @param undoRedo true if Job was Undo/Redo job.
	 */
	private static void endBatch(AbstractDrawing.DrawingPreferences dp, GraphicsPreferences gp, SnapshotAnalyze sa) {
		// Mark cells for redraw
		Set<CellId> topCells = new HashSet<CellId>();
		for(Iterator<WindowFrame> wit = WindowFrame.getWindows(); wit.hasNext(); )
		{
			WindowFrame wf = wit.next();
			WindowContent content = wf.getContent();
			if (!(content instanceof EditWindow)) continue;
			Cell winCell = content.getCell();
			if (winCell == null) continue;
			if (!winCell.isLinked()) continue;
			topCells.add(winCell.getId());
		}

		Set<CellId> changedVisibility = VectorCache.theCache.updateChange(topCells, sa);
		for (CellId cellId : changedVisibility) {
			Cell cell = VectorCache.theCache.database.getCell(cellId);
			EditWindow.forceRedraw(cell);
		}
		for(Iterator<WindowFrame> wit = WindowFrame.getWindows(); wit.hasNext(); )
		{
			WindowFrame wf = wit.next();
			WindowContent content = wf.getContent();
			if (!(content instanceof EditWindow)) continue;
			Cell winCell = content.getCell();
			if (winCell == null) continue;
			EditWindow wnd = (EditWindow)content;
			if (changedVisibility.contains(winCell.getId()))
				wnd.repaintContents(null, false, dp, gp);
		}
	}

	/**
	 * Method to recurse flag all windows showing a cell to redraw.
	 * @param cell the Cell that changed.
	 */
	public static void expansionChanged(Cell cell)
	{
		if (User.getDisplayAlgorithm() != 0)
			return;
		Set<Cell> marked = new HashSet<Cell>();
		markCellForRedrawRecursively(cell, marked);

		for(Iterator<WindowFrame> wit = WindowFrame.getWindows(); wit.hasNext(); )
		{
			WindowFrame wf = wit.next();
			WindowContent content = wf.getContent();
			if (!(content instanceof EditWindow)) continue;
			Cell winCell = content.getCell();
			if (marked.contains(winCell))
			{
				EditWindow wnd = (EditWindow)content;
				wnd.fullRepaint();
			}
		}
	}

	private static void markCellForRedrawRecursively(Cell cell, Set<Cell> marked) {
		if (marked.contains(cell)) return;
		marked.add(cell);
		// recurse up the hierarchy so that all windows showing the cell get redrawn
		for(Iterator<NodeInst> it = cell.getInstancesOf(); it.hasNext(); )
		{
			NodeInst ni = it.next();
			if (ni.isExpanded())
				markCellForRedrawRecursively(ni.getParent(), marked);
		}
	}

	private static void forceRedraw(Cell cell)
	{
		AbstractDrawing.forceRedraw(cell);
	}

	// ************************************* COORDINATES *************************************

    /**
     * Low-level Method to setup transformation between database and screen coordinates.
     * Used by redisplay engine to report about redisplay
     * @param sz size offscreen (in pixels)
     * @param da DisplayAttributs with scale, position, and in-place info
     */
    public void setupCoordinates(Dimension sz, WindowFrame.DisplayAttributes da) {
        this.sz = sz;
        szHalfWidth = sz.width / 2;
        szHalfHeight = sz.height / 2;
        if (scale != drawing.da.scale || offx != drawing.da.offX || offy != drawing.da.offY) {
            textInCell = null;
        }
        scale = da.scale;
        scale_ = (float) (scale / DBMath.GRID);
        offx = da.offX;
        offy = da.offY;
        factorX = (float) (offx * DBMath.GRID - szHalfWidth / scale_);
        factorY = (float) (offy * DBMath.GRID + szHalfHeight / scale_);
    }

	/**
	 * Method to convert a screen coordinate to database coordinates.
	 * @param screenX the X coordinate (on the screen in this EditWindow).
	 * @param screenY the Y coordinate (on the screen in this EditWindow).
	 * @return the coordinate of that point in database units.
	 */
	public Point2D screenToDatabase(long screenX, long screenY)
	{
		double dbX = (screenX - szHalfWidth) / scale + offx;
		double dbY = (szHalfHeight - screenY) / scale + offy;
		Point2D dbPt = new Point2D.Double(dbX, dbY);

		// if doing in-place display, transform into the proper cell
		if (inPlaceDisplay)
	   		intoCell.transform(dbPt, dbPt);

		return dbPt;
	}

	/**
	 * Method to convert a rectangle in screen units to a rectangle in
	 * database units.
	 * @param screenRect the rectangle to convert
	 * @return the same rectangle in database units
	 */
	public Rectangle2D screenToDatabase(Rectangle2D screenRect)
	{
		Point2D anchor = screenToDatabase((int)screenRect.getX(), (int)screenRect.getY());
		Point2D size = deltaScreenToDatabase((int)screenRect.getWidth(), (int)screenRect.getHeight());

		// note that lower left corner in screen units is upper left corner in database units, so compensate for that here
		return new Rectangle2D.Double(anchor.getX(), anchor.getY()+size.getY(), size.getX(), -size.getY());
	}

	/**
	 * Method to convert a screen distance to a database distance.
	 * @param screenDX the X coordinate change (on the screen in this EditWindow).
	 * @param screenDY the Y coordinate change (on the screen in this EditWindow).
	 * @return the distance in database units.
	 */
	public Point2D deltaScreenToDatabase(int screenDX, int screenDY)
	{
		Point2D origin = screenToDatabase(0, 0);
		Point2D pt = screenToDatabase(screenDX, screenDY);
		return new Point2D.Double(pt.getX() - origin.getX(), pt.getY() - origin.getY());
	}

	/**
	 * Method to convert a database coordinate to screen coordinates.
	 * @param dbX the X coordinate (in database units).
	 * @param dbY the Y coordinate (in database units).
	 * @return the ScreenPoint in which to store the screen coordinates.
	 */
    @Override
	public ScreenPoint databaseToScreen(double dbX, double dbY)
	{
		// if doing in-place display, transform out of the proper cell
		if (inPlaceDisplay)
		{
			Point2D dbPt = new Point2D.Double(dbX, dbY);
	   		outofCell.transform(dbPt, dbPt);
	   		dbX = dbPt.getX();
	   		dbY = dbPt.getY();
		}
		double scrX = szHalfWidth + (dbX - offx) * scale;
		double scrY = szHalfHeight - (dbY - offy) * scale;
		long x = (long)(scrX >= 0 ? scrX + 0.5 : scrX - 0.5);
		long y = (long)(scrY >= 0 ? scrY + 0.5 : scrY - 0.5);
		return new ScreenPoint(x, y);
	}

	/**
	 * Method to convert a database coordinate to screen coordinates.
	 * @param db the coordinate (in database units).
	 * @return the coordinate on the screen.
	 */
	public ScreenPoint databaseToScreen(Point2D db)
	{
   		return databaseToScreen(db.getX(), db.getY());
	}

	/**
	 * Method to convert a database rectangle to screen coordinates.
	 * @param db the rectangle (in database units).
	 * @return the rectangle on the screen.
	 */
	public Rectangle databaseToScreen(Rectangle2D db)
	{
		ScreenPoint llPt = databaseToScreen(db.getMinX(), db.getMinY());
		ScreenPoint urPt = databaseToScreen(db.getMaxX(), db.getMaxY());
		long screenLX = llPt.getX();
		long screenHX = urPt.getX();
		long screenLY = llPt.getY();
		long screenHY = urPt.getY();
		if (screenHX < screenLX) { long swap = screenHX;   screenHX = screenLX; screenLX = swap; }
		if (screenHY < screenLY) { long swap = screenHY;   screenHY = screenLY; screenLY = swap; }
		return new Rectangle((int)screenLX, (int)screenLY, (int)(screenHX-screenLX+1), (int)(screenHY-screenLY+1));
	}

	/**
	 * Method to convert a database distance to a screen distance.
	 * @param dbDX the X change (in database units).
	 * @param dbDY the Y change (in database units).
	 * @return the distance on the screen.
	 */
	public ScreenPoint deltaDatabaseToScreen(double dbDX, double dbDY)
	{
		ScreenPoint origin = databaseToScreen(0, 0);
		ScreenPoint pt = databaseToScreen(dbDX, dbDY);
		return new ScreenPoint(pt.getX() - origin.getX(), pt.getY() - origin.getY());
	}

	/**
	 * Method to convert a database grid coordinate to screen coordinates.
	 * @param dbX the X coordinate (in database grid units).
	 * @param dbY the Y coordinate (in database grid units).
	 * @param result the Point in which to store the screen coordinates.
	 */
	public void gridToScreen(long dbX, long dbY, Point result)
	{
		double scrX = (dbX - factorX) * scale_;
		double scrY = (factorY - dbY) * scale_;
		result.x = (int)(scrX >= 0 ? scrX + 0.5 : scrX - 0.5);
		result.y = (int)(scrY >= 0 ? scrY + 0.5 : scrY - 0.5);
	}

	/**
	 * Method to snap a point to the nearest database-space grid unit.
	 * @param pt the point to be snapped.
	 */
	public static void gridAlign(Point2D pt)
	{
		DBMath.gridAlign(pt, User.getAlignmentToGrid());
	}

	/**
	 * Method to snap a point to the nearest database-space grid unit.
	 * @param pt the point to be snapped.
	 * @param direction -1 if X and Y coordinates, 0 if only X and 1 if only Y
	 */
	public static void gridAlignSize(Point2D pt, int direction)
	{
		DBMath.gridAlign(pt, User.getAlignmentToGrid(), direction);
	}

	// ************************************* TEXT *************************************

	/**
	 * Method to find the size in database units for text of a given point size in this EditWindow.
	 * The scale of this EditWindow is used to determine the actual unit size.
	 * @param pointSize the size of the text in points.
	 * @return the database size (in grid units) of the text.
	 */
	public double getTextUnitSize(double pointSize)
	{
		return pointSize / scale;
	}

	/**
	 * Method to find the size in points (actual screen units) for text of a given database size in this EditWindow.
	 * The scale of this EditWindow is used to determine the actual screen size.
	 * @param dbSize the size of the text in database grid-units.
	 * @return the screen size (in points) of the text.
	 */
	public double getTextScreenSize(double dbSize)
	{
		return dbSize * scale;
	}

	public static int getDefaultFontSize() { return TextDescriptor.getDefaultFontSize(); }

	/**
	 * Method to get a Font to use for a given TextDescriptor in this EditWindow.
	 * @param descript the TextDescriptor.
	 * @return the Font to use (returns null if the text is too small to display).
	 */
	public Font getFont(TextDescriptor descript)
	{
		return descript != null ? descript.getFont(this, PixelDrawing.MINIMUMTEXTSIZE) : TextDescriptor.getDefaultFont(this);
	}

	/**
	 * Method to get the height of text given a TextDescriptor in this EditWindow.
	 * @param descript the TextDescriptor.
	 * @return the height of the text.
	 */
	public double getFontHeight(TextDescriptor descript)
	{
		double size = getDefaultFontSize();
		if (descript != null)
			size = descript.getTrueSize(this);
		return size;
	}

    private LRUCache<Map.Entry<String,Font>,Rectangle2D> cache =
        new LRUCache<Map.Entry<String,Font>,Rectangle2D>(16 * 1024) {
        @Override protected Rectangle2D cacheMiss(Map.Entry<String,Font> key) {
            String text = key.getKey();
            Font   font = key.getValue();
            return getGlyphs(text, font).getVisualBounds();
        }
    };

	public Rectangle2D getGlyphBounds(String text, Font font) { return cache.get(new AbstractMap.SimpleEntry<String, Font>(text,font)); }

	/**
	 * Method to convert a string and descriptor to a GlyphVector.
	 * @param text the string to convert.
	 * @param font the Font to use.
	 * @return a GlyphVector describing the text.
	 */
	public GlyphVector getGlyphs(String text, Font font)
	{
		// make a glyph vector for the desired text
		return TextDescriptor.getGlyphs(text, font);
	}

    @Override
	public void databaseChanged(DatabaseChangeEvent e)
	{
		if (cell != null && e.objectChanged(cell))
			setWindowTitle();

		// if cell was deleted, set cell to null
		if (cell != null && !cell.isLinked())
		{
			showCell(null, VarContext.globalContext, true, null);
			wf.cellHistoryGoBack();
		}

		// recalculate text positions
		textInCell = null;
	}

	/**
	 * Method to export directly PNG file.
	 * @param ep printable object.
	 * @param filePath
	 */
    @Override
	public void writeImage(ElectricPrinter ep, String filePath)
	{
		BufferedImage img = getPrintImage(ep);
		PNG.writeImage(img, filePath);
	}

	/**
	 * Method to initialize for printing.
	 * @param ep the ElectricPrinter object.
	 * @param pageFormat information about the print job.
	 * @return Always true.
	 */
    @Override
	public boolean initializePrinting(ElectricPrinter ep, PageFormat pageFormat)
	{
		int scaleFactor = ep.getDesiredDPI() / 72;
		if (scaleFactor > 2) scaleFactor = 2; else
			if (scaleFactor <= 0) scaleFactor = 1;
		int pageWid = (int)pageFormat.getImageableWidth() * scaleFactor;
		int pageHei = (int)pageFormat.getImageableHeight() * scaleFactor;
		setSize(pageWid, pageHei);
		validate();
		repaint();
		return true;
	}

	/**
	 * Method to print window using offscreen canvas.
	 * @param ep printable object.
	 * @return the image to print (null on error).
	 */
    @Override
	public BufferedImage getPrintImage(ElectricPrinter ep)
	{
		if (getCell() == null) return null;
		Rectangle2D cellBounds = ep.getRenderArea();
		if (cellBounds == null) cellBounds = getBoundsInWindow();
		return getPrintImageFromData(ep, this, cellBounds, getCell(), getVarContext(), lv, false);		
	}

	/**
	 * Method to print window using offscreen canvas.
	 * @param ep printable object.
	 * @return the image to print (null on error).
	 */
	public static BufferedImage getPrintImageFromData(ElectricPrinter ep, EditWindow wnd,
		Rectangle2D cellBounds, Cell cell, VarContext vc, LayerVisibility lv, boolean isDisplay)
	{
		int scaleFactor = ep.getDesiredDPI() / 72;
		if (scaleFactor > 2) scaleFactor = 2; else
			if (scaleFactor <= 0) scaleFactor = 1;
		int wid = (int)ep.getPageFormat().getImageableWidth() * scaleFactor;
		int hei = (int)ep.getPageFormat().getImageableHeight() * scaleFactor;

		BufferedImage img = ep.getBufferedImage();
		if (img == null)
		{
			// change window size
			PixelDrawing offscreen = new PixelDrawing(new Dimension(wid, hei));
			offscreen.setWindow(ep.getWindow());

			// prepare for printing
			PrinterJob pj = ep.getPrintJob();
			if (pj == null)
			{
				System.out.println("Can't get PrintJob in getPrintImage()");
				return null;
			}
			PrintService ps = pj.getPrintService();
			if (ps == null)
			{
				System.out.println("Can't get PrintService in getPrintImage()");
				return null;
			}
			ColorSupported cs = ps.getAttribute(ColorSupported.class);
			int printMode = 1;
			if (cs == null || cs.getValue() == 0) printMode = 2;
			if (isDisplay) printMode = 0;
			offscreen.setPrintingMode(printMode);
			offscreen.setBackgroundColor(Color.WHITE);
            // don't set User color here otherwise it generates an assertion in UserInterfaceMain.java:910
//			int oldBackgroundColor = User.getColor(User.ColorPrefType.BACKGROUND);
//			User.setColor(User.ColorPrefType.BACKGROUND, 0xFFFFFF);

			// initialize drawing
			cellBounds = new Rectangle2D.Double(cellBounds.getMinX()-1, cellBounds.getMinY()-1, cellBounds.getWidth()+2, cellBounds.getHeight()+2);
			double width = cellBounds.getWidth();
			double height = cellBounds.getHeight();
			if (width == 0) width = 2;
			if (height == 0) height = 2;
			double scalex = wid/width;
			double scaley = hei/height;
			double scale = Math.min(scalex, scaley);
			EPoint offset = EPoint.fromLambda(cellBounds.getCenterX(), cellBounds.getCenterY());

			offscreen.printImage(scale, offset, cell, vc, ep, lv);
			img = offscreen.getBufferedImage();
			ep.setBufferedImage(img);

			// restore display state
			offscreen.setPrintingMode(0);
            // don't set User color here otherwise it generates an assertion in UserInterfaceMain.java:910
//			User.setColor(User.ColorPrefType.BACKGROUND, oldBackgroundColor);
		}

		// copy the image to the page if graphics is not null
		Graphics2D g2d = (Graphics2D)ep.getGraphics();
		if (g2d != null)
		{
			AffineTransform saveAT = g2d.getTransform();
			int ix = (int)ep.getPageFormat().getImageableX() * scaleFactor;
			int iy = (int)ep.getPageFormat().getImageableY() * scaleFactor;
			g2d.scale(1.0 / scaleFactor, 1.0 / scaleFactor);
			g2d.drawImage(img, ix, iy, null);

			if (wnd != null)
			{
				// save window factors
				Point2D saveOffset = wnd.getOffset();
				double saveScale = wnd.scale;
				int saveHalfWid = wnd.szHalfWidth;
				int saveHalfHei = wnd.szHalfHeight;

				// compute proper window factors to center the frame
				Rectangle2D boundsInWindow = wnd.getBoundsInWindow();
				double width = boundsInWindow.getWidth();
				double height = boundsInWindow.getHeight();
				if (width == 0) width = 2;
				if (height == 0) height = 2;
				wnd.scale = wnd.scaleRequested = Math.min(wid/width, hei/height);
				wnd.scale_ = (float)(wnd.scale/DBMath.GRID);
				wnd.offx = wnd.offy = wnd.offxRequested = wnd.offyRequested = 0;
				wnd.szHalfWidth = ix + wid / 2;
				wnd.szHalfHeight = iy + hei / 2;
				wnd.factorX = (float)(wnd.offx*DBMath.GRID - wnd.szHalfWidth/wnd.scale_);
				wnd.factorY = (float)(wnd.offy*DBMath.GRID + wnd.szHalfHeight/wnd.scale_);

				// draw the frame
				g2d.setColor(Color.BLACK);
				wnd.drawCellFrame(g2d);

				// restore window factors
				wnd.scale = wnd.scaleRequested = saveScale;
				wnd.scale_ = (float)(wnd.scale/DBMath.GRID);
				wnd.szHalfWidth = saveHalfWid;
				wnd.szHalfHeight = saveHalfHei;
				wnd.offx = wnd.offxRequested = saveOffset.getX();
				wnd.offy = wnd.offyRequested = saveOffset.getY();
				wnd.factorX = (float)(wnd.offx*DBMath.GRID - wnd.szHalfWidth/wnd.scale_);
				wnd.factorY = (float)(wnd.offy*DBMath.GRID + wnd.szHalfHeight/wnd.scale_);
			}
			g2d.setTransform(saveAT);
		}
		return img;
	}

	/**
	 * Method to pan along X or Y according to fixed amount of ticks
	 * @param direction 0 is X and 1 is Y
	 * @param panningAmounts
	 * @param ticks
	 */
    @Override
	public void panXOrY(int direction, double[] panningAmounts, int ticks)
	{
		Cell cell = getCell();
		if (cell == null) return;
		Dimension dim = getSize();
		double panningAmount = panningAmounts[User.getPanningDistance()];
		double value = (direction == 0) ? dim.width : dim.height;
		double mult = value * panningAmount / getScale();
		if (mult == 0) mult = 1;
		Point2D wndOffset = getOffset();
		Point2D newOffset = (direction == 0) ?
				new Point2D.Double(wndOffset.getX() - mult*ticks, wndOffset.getY()) :
				new Point2D.Double(wndOffset.getX(), wndOffset.getY() - mult*ticks);
		setOffset(newOffset);
		getSavedFocusBrowser().updateCurrentFocus();
		fullRepaint();
	}

	/**
	 * Method to shift the window so that the current cursor location becomes the center.
	 */
    @Override
	public void centerCursor()
	{
		Point pt = getLastMousePosition();
		Point2D center = screenToDatabase(pt.x, pt.y);
		setOffset(center);
		getSavedFocusBrowser().updateCurrentFocus();
		fullRepaint();
	}

}
