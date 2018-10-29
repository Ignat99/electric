/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: MeasureListener.java
 *
 * Copyright (c) 2004, Static Free Software. All rights reserved.
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

import com.sun.electric.database.change.DatabaseChangeEvent;
import com.sun.electric.database.geometry.EPoint;
import com.sun.electric.database.geometry.ERectangle;
import com.sun.electric.database.geometry.Poly;
import com.sun.electric.database.geometry.ScreenPoint;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.Nodable;
import com.sun.electric.database.hierarchy.HierarchyEnumerator;
import com.sun.electric.database.text.Name;
import com.sun.electric.database.text.TextUtils;
import com.sun.electric.database.topology.ArcInst;
import com.sun.electric.database.topology.Geometric;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.variable.VarContext;
import com.sun.electric.technology.ArcProto;
import com.sun.electric.technology.PrimitiveNode;
import com.sun.electric.technology.Technology;
import com.sun.electric.tool.extract.GeometrySearch;
import com.sun.electric.tool.user.GraphicsPreferences;
import com.sun.electric.tool.user.Highlight;
import com.sun.electric.tool.user.Highlighter;
import com.sun.electric.tool.user.User;
import com.sun.electric.tool.user.waveform.Panel;
import com.sun.electric.tool.user.waveform.WaveformWindow;
import com.sun.electric.util.ClientOS;
import com.sun.electric.util.math.DBMath;
import com.sun.electric.util.math.FixpTransform;

import java.awt.AWTException;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.swing.SwingUtilities;

/**
 * Class to make measurements in a window.
 */
public class MeasureListener implements WindowFrame.ElectricEventListener
{
	public static MeasureListener theOne = new MeasureListener();

	private static double lastMeasuredDistanceX = 0, lastMeasuredDistanceY = 0;
	private static double lastValidMeasuredDistanceX = 0, lastValidMeasuredDistanceY = 0;
	private static boolean measuring = false; // true if drawing measure line
	private static List<Highlight> lastHighlights = new ArrayList<Highlight>();
	private Point2D dbStart; // start of measure in database units

	private MeasureListener() {}

	public static double getLastMeasuredDistanceX()
	{
		return lastValidMeasuredDistanceX;
	}

	public static double getLastMeasuredDistanceY()
	{
		return lastValidMeasuredDistanceY;
	}

	private void startMeasure(Point2D dbStart)
	{
		lastValidMeasuredDistanceX = lastMeasuredDistanceX;
		lastValidMeasuredDistanceY = lastMeasuredDistanceY;
		this.dbStart = dbStart;
		measuring = true;
		lastHighlights.clear();
	}

	private void dragOutMeasure(EditWindow wnd, Point2D dbPoint)
	{
		if (measuring && dbStart != null)
		{
			// Highlight.clear();
			Point2D start = dbStart;
			Point2D end = dbPoint;
			Highlighter highlighter = wnd.getRulerHighlighter();

			for (Highlight h : lastHighlights)
			{
				highlighter.remove(h);
			}
			lastHighlights.clear();

			// show coords at start and end point
            Cell cell = wnd.getCell();
            if (cell == null) return; // nothing available

            Technology tech = cell.getTechnology();
            if (User.isCadenceMeasurementStyle())
            {
    			ScreenPoint stScreen = wnd.databaseToScreen(start);
    			ScreenPoint enScreen = wnd.databaseToScreen(end);
    			double dbDist = start.distance(end);
    			double screenDist = Math.sqrt((stScreen.getX()-enScreen.getX())*(stScreen.getX()-enScreen.getX()) +
    				(stScreen.getY()-enScreen.getY())*(stScreen.getY()-enScreen.getY()));
    			if (dbDist > 0 && screenDist > 0)
    			{
	    			double dbLargeTickSize = 8 / screenDist * dbDist;
	    			double dbSmallTickSize = 3 / screenDist * dbDist;
	    			int lineAngle = DBMath.figureAngle(start, end);
	    			int tickAngle = (lineAngle + 900) % 3600;

	    			// draw main line and starting/ending tick marks
	    			lastHighlights.add(highlighter.addLine(start, end, cell));
	    			double tickX = start.getX() + DBMath.cos(tickAngle) * dbLargeTickSize;
	    			double tickY = start.getY() + DBMath.sin(tickAngle) * dbLargeTickSize;
	    			Point2D tickLocation = new Point2D.Double(tickX, tickY);
	    			lastHighlights.add(highlighter.addLine(start, tickLocation, cell));
					lastHighlights.add(highlighter.addMessage(cell, "0", tickLocation));
	    			tickX = end.getX() + DBMath.cos(tickAngle) * dbLargeTickSize;
	    			tickY = end.getY() + DBMath.sin(tickAngle) * dbLargeTickSize;
	    			tickLocation = new Point2D.Double(tickX, tickY);
	    			lastHighlights.add(highlighter.addLine(end, tickLocation, cell));
					lastHighlights.add(highlighter.addMessage(cell, TextUtils.formatDistance(dbDist, tech), tickLocation));

					// now do intermediate ticks
					double minorTickDist = Math.min(wnd.getGridXSpacing(), wnd.getGridYSpacing());
					int numMinorTicks = (int)(dbDist / minorTickDist);
					while (numMinorTicks >= screenDist/10)
					{
						minorTickDist *= 2;
						numMinorTicks = (int)(dbDist / minorTickDist);
					}
					for(int i=1; i<=numMinorTicks; i++)
					{
						double distSoFar = minorTickDist * i;
						double minorTickX = start.getX() + distSoFar*DBMath.cos(lineAngle);
						double minorTickY = start.getY() + distSoFar*DBMath.sin(lineAngle);
		    			tickX = minorTickX + DBMath.cos(tickAngle) * dbSmallTickSize;
		    			tickY = minorTickY + DBMath.sin(tickAngle) * dbSmallTickSize;
		    			tickLocation = new Point2D.Double(tickX, tickY);
		    			lastHighlights.add(highlighter.addLine(new Point2D.Double(minorTickX, minorTickY),
		    				tickLocation, cell));
						if (distSoFar > dbDist-minorTickDist) break;
		    			if ((i%5) == 0)
		    			{
							lastHighlights.add(highlighter.addMessage(cell, TextUtils.formatDistance(distSoFar, tech), tickLocation));
		    			}
					}
    			}
            } else
            {
				lastHighlights.add(highlighter.addMessage(cell, "("
					+ TextUtils.formatDistance(start.getX(), tech) + "," + TextUtils.formatDistance(start.getY(), tech)
					+ ")", start));
				lastHighlights.add(highlighter.addMessage(cell, "("
					+ TextUtils.formatDistance(end.getX(), tech) + "," + TextUtils.formatDistance(end.getY(), tech)
					+ ")", end));
				// add in line
				lastHighlights.add(highlighter.addLine(start, end, cell));

				lastMeasuredDistanceX = Math.abs(start.getX() - end.getX());
				lastMeasuredDistanceY = Math.abs(start.getY() - end.getY());
				Point2D center = new Point2D.Double((start.getX() + end.getX()) / 2,
					(start.getY() + end.getY()) / 2);
				double dist = start.distance(end);
				String show = TextUtils.formatDistance(dist, tech) + " (dX="
					+ TextUtils.formatDistance(lastMeasuredDistanceX, tech) + " dY="
					+ TextUtils.formatDistance(lastMeasuredDistanceY, tech) + ")";
				lastHighlights.add(highlighter.addMessage(cell, show, center, 1, null));
            }
			highlighter.finished();
			wnd.clearDoingAreaDrag();
			wnd.repaint();
		}
	}

	public void reset()
    {
        if (measuring) measuring = false;

        // clear measurements in the current window
        WindowFrame wf = WindowFrame.getCurrentWindowFrame();
        if (wf.getContent() instanceof EditWindow)
        {
            EditWindow wnd = (EditWindow)wf.getContent();
	        Highlighter highlighter = wnd.getRulerHighlighter();
	        highlighter.clear();
	        highlighter.finished();
	        wnd.repaint();
        } else if (wf.getContent() instanceof WaveformWindow)
        {
        	WaveformWindow ww = (WaveformWindow)wf.getContent();
        	for(Iterator<Panel> it = ww.getPanels(); it.hasNext(); )
        	{
        		Panel p = it.next();
        		p.clearMeasurements();
        	}
        }
    }

	private void finishMeasure(EditWindow wnd)
	{
		Highlighter highlighter = wnd.getRulerHighlighter();
		if (measuring)
		{
			for (Highlight h : lastHighlights)
			{
				highlighter.remove(h);
			}
			lastHighlights.clear();
			measuring = false;
		} else
		{
			// clear measures from the screen if user cancels twice in a row
			highlighter.clear();
		}
		highlighter.finished();
		wnd.repaint();
	}

	// ------------------------ Mouse Listener Stuff -------------------------

	public void mousePressed(MouseEvent evt)
	{
		boolean ctrl = (evt.getModifiersEx() & MouseEvent.CTRL_DOWN_MASK) != 0;

		if (evt.getSource() instanceof EditWindow)
		{
			int newX = evt.getX();
			int newY = evt.getY();
			EditWindow wnd = (EditWindow)evt.getSource();
			Point2D dbMouse = wnd.screenToDatabase(newX, newY);
			doGridding(wnd, evt, dbMouse);
			if (isLeftMouse(evt))
			{
				if (measuring && ctrl && dbStart != null)
				{
					// orthogonal only
					dbMouse = convertToOrthogonal(dbStart, dbMouse);
				}
				startMeasure(dbMouse);
			}
			if (ClickZoomWireListener.isRightMouse(evt))
			{
				finishMeasure(wnd);
			}
		}
	}

	public void mouseReleased(MouseEvent evt)
	{
		// uncomment this to do measurements by click, drag, release instead of click/click
//		lastValidMeasuredDistanceX = lastMeasuredDistanceX;
//		lastValidMeasuredDistanceY = lastMeasuredDistanceY;
//		measuring = false;
//		lastHighlights.clear();
	}

	public void mouseDragged(MouseEvent evt)
	{
		gridMouse(evt);
		boolean ctrl = (evt.getModifiersEx() & MouseEvent.CTRL_DOWN_MASK) != 0;

		if (evt.getSource() instanceof EditWindow)
		{
			int newX = evt.getX();
			int newY = evt.getY();
			EditWindow wnd = (EditWindow)evt.getSource();
			Point2D dbMouse = wnd.screenToDatabase(newX, newY);
			if (ctrl && dbStart != null)
			{
				dbMouse = convertToOrthogonal(dbStart, dbMouse);
			}
			doGridding(wnd, evt, dbMouse);
			dragOutMeasure(wnd, dbMouse);
		}
	}

	private long gridOffX = 0, gridOffY = 0;
	private Robot robot = null;

	public void mouseMoved(MouseEvent evt)
	{
		mouseDragged(evt);
	}

	public void mouseClicked(MouseEvent evt) {}
	public void mouseEntered(MouseEvent evt) {}
	public void mouseExited(MouseEvent evt) {}
	public void mouseWheelMoved(MouseWheelEvent evt) {}
	public void keyReleased(KeyEvent evt) {}
	public void keyTyped(KeyEvent evt) {}
    public void databaseChanged(DatabaseChangeEvent e) {}

	public void keyPressed(KeyEvent evt)
	{
		int chr = evt.getKeyCode();

		if (evt.getSource() instanceof EditWindow)
		{
			EditWindow wnd = (EditWindow)evt.getSource();

			if (chr == KeyEvent.VK_ESCAPE)
			{
				finishMeasure(wnd);
			}
		}
	}

	/**
	 * See if event is a left mouse click. Platform independent.
	 */
	private boolean isLeftMouse(MouseEvent evt)
	{
		if (ClientOS.isOSMac())
		{
			if (!evt.isMetaDown())
			{
				if ((evt.getModifiers() & MouseEvent.BUTTON1_MASK) == MouseEvent.BUTTON1_MASK) return true;
			}
		} else
		{
			if ((evt.getModifiers() & MouseEvent.BUTTON1_MASK) == MouseEvent.BUTTON1_MASK) return true;
		}
		return false;
	}

	/**
	 * Convert the mousePoint to be orthogonal to the startPoint. Chooses
	 * direction which is orthogonally farther from startPoint
	 * @param startPoint the reference point
	 * @param mousePoint the mouse point
	 * @return a new point orthogonal to startPoint
	 */
	public static Point2D convertToOrthogonal(Point2D startPoint, Point2D mousePoint)
	{
		// move in direction that is farther
		double xdist, ydist;
		xdist = Math.abs(mousePoint.getX() - startPoint.getX());
		ydist = Math.abs(mousePoint.getY() - startPoint.getY());
		if (ydist > xdist)
			return new Point2D.Double(startPoint.getX(), mousePoint.getY());
		return new Point2D.Double(mousePoint.getX(), startPoint.getY());
	}

	/**
	 * Method to grid align the screen units of the mouse.
	 * @param evt
	 */
	private void gridMouse(MouseEvent evt)
	{
		// snap the cursor to the grid
		if (User.isGridAlignMeasurementCursor() && evt.getSource() instanceof EditWindow)
		{
			EditWindow wnd = (EditWindow)evt.getSource();
			long mouseX = evt.getX() + gridOffX;
			long mouseY = evt.getY() + gridOffY;
	        Point2D dbMouse = wnd.screenToDatabase(mouseX, mouseY);
	        Point2D align = new Point2D.Double(dbMouse.getX(), dbMouse.getY());
			EditWindow.gridAlign(align);
			ScreenPoint newPos = wnd.databaseToScreen(align);
			gridOffX = mouseX - newPos.getX();
			gridOffY = mouseY - newPos.getY();
			try {
				if (robot == null) robot = new Robot();
			} catch (AWTException e) {}
			Point newPosPt = new Point(newPos.getIntX(), newPos.getIntY());
			SwingUtilities.convertPointToScreen(newPosPt, wnd);
			if (robot != null) robot.mouseMove(newPosPt.x, newPosPt.y);
		}
	}

	/**
	 * Method to grid align the database units of the mouse coordinates
	 * @param dbMouse the database units to align.
	 */
	private void doGridding(EditWindow wnd, MouseEvent evt, Point2D dbMouse)
	{
		boolean shift = (evt.getModifiersEx() & MouseEvent.SHIFT_DOWN_MASK) != 0;
		if (shift)
		{
			// snap to nearest geometry
			if (wnd != null && wnd.getCell() != null)
			{
				Point2D snapDist = wnd.screenToDatabase(0, 15);
				double snapDistance = Math.sqrt(snapDist.getX()*snapDist.getX() + snapDist.getY()*snapDist.getY());
				GeometrySearch search = new GeometrySearch(wnd, snapDistance);
				List<Line2D> possibleLines = new ArrayList<Line2D>();
				List<Point2D> possiblePoints = new ArrayList<Point2D>();
				search.searchGeometries(wnd.getCell(), dbMouse, possibleLines, possiblePoints);
				Point2D closest = new Point2D.Double();
				double closestDist = Double.MAX_VALUE;
				for(Line2D line : possibleLines)
				{
					Point2D hitPoint = DBMath.closestPointToSegment(line.getP1(), line.getP2(), dbMouse);
					if (hitPoint == null) continue;
					double dist = hitPoint.distance(dbMouse);
					if (dist < closestDist)
					{
						closestDist = dist;
						closest = hitPoint;
					}
				}
				for(Point2D point : possiblePoints)
				{
					double dist = point.distance(dbMouse) / 2;
					if (dist < closestDist)
					{
						closestDist = dist;
						closest = point;
					}
				}
				if (closestDist < snapDistance)
				{
					dbMouse.setLocation(closest);
					return;
				}
			}
		}
		EditWindow.gridAlign(dbMouse);
	}

	/**
	 * Class to search hierarchically near a point and return all visible rectangles found.
	 */
	public class GeometrySearch extends HierarchyEnumerator.Visitor
	{
		private double searchDist;
		private List<Line2D> closeLines;
		private List<Point2D> closePoints;
		private ERectangle geomBBnd;
		private final LayerVisibility lv;

		public GeometrySearch(EditWindow wnd, double snapDistance)
		{
			lv = wnd.getLayerVisibility();
			searchDist = snapDistance;
		}

		/**
		 * Find a Primitive Node or Arc at a point in a cell.  The geometric found may exist down
		 * the hierarchy from the given cell.
		 * @param cell the cell in which the point resides
		 * @param point a point to search under
		 * @param lines a List of lines that are in the vicinity
		 * @param points a List of points that are in the vicinity
		 */
		public void searchGeometries(Cell cell, Point2D point, List<Line2D> lines, List<Point2D> points)
		{
			closeLines = lines;
			closePoints = points;
			geomBBnd = ERectangle.fromLambda(point.getX()-searchDist, point.getY()-searchDist, searchDist*2, searchDist*2);
			HierarchyEnumerator.enumerateCell(cell, VarContext.globalContext, this);
		}

		public boolean enterCell(HierarchyEnumerator.CellInfo info)
		{
			Cell cell = info.getCell();
			FixpTransform xformToRoot = info.getTransformToRoot();
			FixpTransform xformFromRoot = null;
			try
			{
				xformFromRoot = xformToRoot.createInverse();
			} catch (Exception e) { e.printStackTrace(); }
			assert(xformFromRoot != null);
			Rectangle2D rect = new Rectangle2D.Double();
			rect.setRect(geomBBnd);
			DBMath.transformRect(rect, xformFromRoot);

			boolean continueDown = false;
			for(Iterator<Geometric> it = cell.searchIterator(rect, true); it.hasNext(); )
			{
				Geometric geom = it.next();
				if (geom instanceof NodeInst)
				{
					NodeInst oNi = (NodeInst)geom;
					if (oNi.isCellInstance())
					{
						if (!oNi.getNodeInst().isExpanded())
						{
							// instance not expanded: check bounds
							Rectangle2D rectDst = new Rectangle2D.Double();
							xformToRoot.transform(geom.getBounds(), rectDst);
							addRect(rectDst);
						} else
						{
							// instance is expanded, keep searching
							continueDown = true;
						}
					} else
					{
						// primitive found, check all polygons
						Poly [] polys = oNi.getProto().getTechnology().getShapeOfNode(oNi);
						for(int box=0; box<polys.length; box++)
						{
							Poly poly = polys[box];
							if (!lv.isVisible(poly.getLayer())) continue;
							poly.transform(oNi.rotateOut());
							double dist = poly.polyDistance(rect);
							if (dist <= searchDist)
								addPoly(poly, xformToRoot);
						}
					}
				} else
				{
					// arc, ignore arcs that and fully invisible
					ArcInst ai = (ArcInst)geom;
					Poly [] polys = ai.getProto().getTechnology().getShapeOfArc(ai);
					for(int box=0; box<polys.length; box++)
					{
						Poly poly = polys[box];
						if (!lv.isVisible(poly.getLayer())) continue;
						double dist = poly.polyDistance(rect);
						if (dist <= searchDist)
							addPoly(poly, xformToRoot);
					}
				}
			}
			return continueDown;
		}

		private void addPoly(Poly poly, FixpTransform xformToRoot)
		{
			poly.transform(xformToRoot);
        	Point2D[] pts = poly.getPoints();
	        if (pts == null)
	        {
	        	// just a rectangle
				addRect(poly.getBounds2D());
				return;
	        }

	        if (poly.getStyle() == Poly.Type.OPENED || poly.getStyle() == Poly.Type.OPENEDT1 ||
	        	poly.getStyle() == Poly.Type.OPENEDT2 || poly.getStyle() == Poly.Type.OPENEDT3)
	        {
	            for (int i = 1; i < pts.length; i++)
	    			closeLines.add(new Line2D.Double(pts[i-1], pts[i]));
	            for (int i = 0; i < pts.length; i++)
	    			closePoints.add(pts[i]);
	            return;
	        }

	        // filled polygon
            for (int i = 0; i < pts.length; i++)
            {
    			closePoints.add(pts[i]);
    			Point2D lastPt = (i == 0 ? pts[pts.length-1] : pts[i-1]);
    			closeLines.add(new Line2D.Double(lastPt, pts[i]));
            }
		}

		private void addRect(Rectangle2D rect)
		{
			Point2D p1 = new Point2D.Double(rect.getMinX(), rect.getMinY());
			Point2D p2 = new Point2D.Double(rect.getMinX(), rect.getMaxY());
			Point2D p3 = new Point2D.Double(rect.getMaxX(), rect.getMaxY());
			Point2D p4 = new Point2D.Double(rect.getMaxX(), rect.getMinY());
			Line2D l1 = new Line2D.Double(p1, p2);
			Line2D l2 = new Line2D.Double(p2, p3);
			Line2D l3 = new Line2D.Double(p3, p4);
			Line2D l4 = new Line2D.Double(p4, p1);
			closeLines.add(l1);
			closeLines.add(l2);
			closeLines.add(l3);
			closeLines.add(l4);
			closePoints.add(p1);
			closePoints.add(p2);
			closePoints.add(p3);
			closePoints.add(p4);
		}

		public void exitCell(HierarchyEnumerator.CellInfo info) {}

		public boolean visitNodeInst(Nodable no, HierarchyEnumerator.CellInfo info)
		{
			if (!no.getNodeInst().isExpanded()) return false;
			return true;
		}
	}
}
