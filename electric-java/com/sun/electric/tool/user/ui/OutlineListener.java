/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: OutlineListener.java
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

import com.sun.electric.database.change.DatabaseChangeEvent;
import com.sun.electric.database.geometry.EPoint;
import com.sun.electric.database.geometry.ScreenPoint;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.technology.PrimitiveNode;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.JobException;
import com.sun.electric.tool.user.CircuitChangeJobs;
import com.sun.electric.tool.user.Highlight;
import com.sun.electric.tool.user.Highlighter;
import com.sun.electric.tool.user.User;
import com.sun.electric.tool.user.menus.EditMenu;
import com.sun.electric.util.math.FixpTransform;

import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Point2D;

/**
 * Class to make changes to the outline information on a node.
 */
public class OutlineListener implements WindowFrame.ElectricEventListener
{
	public static OutlineListener theOne = new OutlineListener();
	private double oldX, oldY;
	private boolean doingMotionDrag;
	private int point;
	private NodeInst outlineNode;
    private Highlighter highlighter;
	private Highlight high;

	private OutlineListener() {}

	public void setNode(Highlight highlight, NodeInst ni)
	{
		EditWindow wnd = EditWindow.getCurrent();
        highlighter = wnd.getHighlighter();
        high = highlight;
        point = 0;

		outlineNode = ni;
		Point2D [] origPoints = outlineNode.getTrace();
		if (origPoints == null)
		{
			// node has no points: fake some
			if (ni.getFunction() == PrimitiveNode.Function.NODE)
			{
				new InitializePoints(this, ni);
				return;
			}
		}

		setPoint(0);
		if (wnd != null) wnd.fullRepaint();
	}

    public void setPoint(int p) {
        if (highlighter != null && high != null) {
            highlighter.setPoint(high, high.getElectricObject(), p);
        }
        if (p < 0) {
            outlineNode = null;
            highlighter = null;
            high = null;
        }
    }

	public void deletePoint()
	{
		// delete a point
		Point2D [] origPoints = outlineNode.getTrace();
		if (origPoints.length <= 2)
		{
			System.out.println("Cannot delete the last point on an outline");
			return;
		}
		FixpTransform trans = outlineNode.rotateOutAboutTrueCenter();
		Point2D [] newPoints = new Point2D[origPoints.length-1];
		int pt = point;
		int j = 0;
		for(int i=0; i<origPoints.length; i++)
		{
			if (i == pt) continue;
			if (origPoints[j] != null)
			{
				newPoints[j] = new Point2D.Double(outlineNode.getAnchorCenterX() + origPoints[i].getX(),
					outlineNode.getAnchorCenterY() + origPoints[i].getY());
				trans.transform(newPoints[j], newPoints[j]);
			}
			j++;
		}
		if (pt > 0) pt--;
		setNewPoints(newPoints, pt);
	}

	/**
	 * Class to initialize the points on an outline node that has no points.
	 */
	private static class InitializePoints extends Job
	{
		private transient OutlineListener listener;
		private NodeInst ni;

		protected InitializePoints(OutlineListener listener, NodeInst ni)
		{
			super("Initialize Outline Points", User.getUserTool(), Job.Type.CHANGE, null, null, Job.Priority.USER);
			this.listener = listener;
			this.ni = ni;
			startJob();
		}

        @Override
		public boolean doIt() throws JobException
		{
			EPoint [] points = new EPoint[4];
			double halfWid = ni.getXSize() / 2;
			double halfHei = ni.getYSize() / 2;
			double cX = ni.getAnchorCenterX();
			double cY = ni.getAnchorCenterY();
			points[0] = EPoint.fromLambda(cX-halfWid, cY-halfHei);
			points[1] = EPoint.fromLambda(cX-halfWid, cY+halfHei);
			points[2] = EPoint.fromLambda(cX+halfWid, cY+halfHei);
			points[3] = EPoint.fromLambda(cX+halfWid, cY-halfHei);
			ni.setTrace(points);
			return true;
		}

        @Override
        public void terminateOK()
        {
			listener.setPoint(0);
			EditWindow wnd = EditWindow.getCurrent();
			if (wnd != null) wnd.fullRepaint();
        }
	}

    @Override
	public void mousePressed(MouseEvent evt)
	{
		int x = evt.getX();
		int y = evt.getY();
		EditWindow wnd = (EditWindow)evt.getSource();
        Highlighter highlighter = wnd.getHighlighter();

		// show "get info" on double-click
		if (evt.getClickCount() == 2)
		{
			if (highlighter.getNumHighlights() >= 1)
			{
				EditMenu.getInfoCommand(true);
				return;
			}
		}

		// right click: add a point
		if (ClickZoomWireListener.isRightMouse(evt))
		{
			// add a point
			FixpTransform trans = outlineNode.rotateOutAboutTrueCenter();
			Point2D [] origPoints = outlineNode.getTrace();
			if (origPoints == null)
			{
				Point2D [] newPoints = new Point2D[1];
				newPoints[0] = new Point2D.Double(outlineNode.getAnchorCenterX(), outlineNode.getAnchorCenterY());
				EditWindow.gridAlign(newPoints[0]);
				setNewPoints(newPoints, 0);
				point = 0;
				oldX = newPoints[point].getX();
				oldY = newPoints[point].getY();
			} else if (origPoints.length == 1)
			{
				Point2D [] newPoints = new Point2D[2];
				newPoints[0] = new Point2D.Double(outlineNode.getAnchorCenterX() + origPoints[0].getX(),
					outlineNode.getAnchorCenterY() + origPoints[0].getY());
				trans.transform(newPoints[0], newPoints[0]);
				EditWindow.gridAlign(newPoints[0]);
				newPoints[1] = new Point2D.Double(newPoints[0].getX() + 2, newPoints[0].getY() + 2);
				EditWindow.gridAlign(newPoints[1]);
				setNewPoints(newPoints, 1);
				point = 1;
				oldX = newPoints[point].getX();
				oldY = newPoints[point].getY();
			} else
			{
                if (point < 0 || point >= origPoints.length) {
                    System.out.println("Must select a point");
                    return;
                }
				Point2D [] newPoints = new Point2D[origPoints.length+1];
				int j = 0;
				for(int i=0; i<origPoints.length; i++)
				{
					// copy the original point
					if (origPoints[i] != null)
					{
						newPoints[j] = new Point2D.Double(outlineNode.getAnchorCenterX() + origPoints[i].getX(),
							outlineNode.getAnchorCenterY() + origPoints[i].getY());
					}
					j++;
					if (i == point)
					{
						// found the selected point, make the insertion
						newPoints[j] = wnd.screenToDatabase(x, y);
						EditWindow.gridAlign(newPoints[j]);
						j++;
					}
				}
				trans.transform(newPoints, 0, newPoints, 0, newPoints.length);
				setNewPoints(newPoints, point+1);
				oldX = newPoints[point+1].getX();
				oldY = newPoints[point+1].getY();
			}
			doingMotionDrag = true;
			wnd.repaint();
			return;
		}

		// standard click-and-drag: see if cursor is over anything
		Point2D pt = wnd.screenToDatabase(x, y);
		Highlight found = highlighter.findObject(pt, wnd, true, false, false, false, true, true, false, true);
		doingMotionDrag = false;
		if (found != null)
		{
			high = highlighter.getOneHighlight();
            assert (high == found);
			outlineNode = (NodeInst)highlighter.getOneElectricObject(NodeInst.class);
			if (high != null && outlineNode != null)
			{
				point = high.getPoint();
				Point2D [] origPoints = outlineNode.getTrace();
				if (origPoints != null)
				{
					doingMotionDrag = true;
					EditWindow.gridAlign(pt);
					oldX = pt.getX();
					oldY = pt.getY();
				}
			}
		}
		wnd.repaint();
	}

	public void mouseReleased(MouseEvent evt)
	{
		EditWindow wnd = (EditWindow)evt.getSource();
        if (wnd == null) return;
        Highlighter highlighter = wnd.getHighlighter();

		// handle moving the selected point
		if (doingMotionDrag)
		{
			doingMotionDrag = false;

			int newX = evt.getX();
			int newY = evt.getY();
			Point2D curPt = wnd.screenToDatabase(newX, newY);
			EditWindow.gridAlign(curPt);
			highlighter.setHighlightOffset(0, 0);

			moveSelectedPoint(curPt.getX() - oldX, curPt.getY() - oldY);
			wnd.fullRepaint();
		}
	}

	public void mouseClicked(MouseEvent evt) {}
	public void mouseEntered(MouseEvent evt) {}
	public void mouseExited(MouseEvent evt) {}
	public void mouseMoved(MouseEvent evt) {}

	public void mouseDragged(MouseEvent evt)
	{
		EditWindow wnd = (EditWindow)evt.getSource();
        if (wnd == null) return;
        Highlighter highlighter = wnd.getHighlighter();

		int newX = evt.getX();
		int newY = evt.getY();
		Point2D curPt = wnd.screenToDatabase(newX, newY);
		EditWindow.gridAlign(curPt);
		ScreenPoint pt = wnd.databaseToScreen(curPt.getX(), curPt.getY());
		ScreenPoint gridPt = wnd.databaseToScreen(oldX, oldY);

		// show moving of the selected point
		if (doingMotionDrag)
		{
			highlighter.setHighlightOffset(pt.getX() - gridPt.getX(), pt.getY() - gridPt.getY());
			wnd.repaint();
			return;
		}
		wnd.repaint();
	}

	public void mouseWheelMoved(MouseWheelEvent evt)
	{
	}

	public void keyPressed(KeyEvent evt)
	{
		int chr = evt.getKeyCode();
		EditWindow wnd = (EditWindow)evt.getSource();
		Cell cell = wnd.getCell();
        if (cell == null) return;

		if (chr == KeyEvent.VK_PERIOD)
		{
			// advance to next point
			Point2D [] origPoints = outlineNode.getTrace();
			int nextPoint = point + 1;
			if (nextPoint >= origPoints.length) nextPoint = 0;
			setPoint(point = nextPoint);
			wnd.repaint();
		} else if (chr == KeyEvent.VK_COMMA)
		{
			// backup to previous point
			Point2D [] origPoints = outlineNode.getTrace();
			int prevPoint = point - 1;
			if (prevPoint < 0) prevPoint = origPoints.length - 1;
			setPoint(point = prevPoint);
			wnd.repaint();
		}
	}

	public void keyReleased(KeyEvent evt) {}
	public void keyTyped(KeyEvent evt) {}
    public void databaseChanged(DatabaseChangeEvent e) {}

	public void moveSelectedPoint(double dx, double dy)
	{
		Point2D [] origPoints = outlineNode.getTrace();
		if (origPoints == null) return;

		Point2D [] newPoints = new Point2D[origPoints.length];
		for(int i=0; i<origPoints.length; i++)
		{
			if (origPoints[i] != null)
				newPoints[i] = new Point2D.Double(outlineNode.getAnchorCenterX() + origPoints[i].getX(),
					outlineNode.getAnchorCenterY() + origPoints[i].getY());
		}
		FixpTransform trans = outlineNode.rotateOutAboutTrueCenter();
		for(int i=0; i<newPoints.length; i++)
		{
			if (newPoints[i] != null)
				trans.transform(newPoints[i], newPoints[i]);
		}
		if (newPoints[point] != null)
			newPoints[point].setLocation(newPoints[point].getX()+dx, newPoints[point].getY()+dy);
		setNewPoints(newPoints, point);
	}

	private void setNewPoints(Point2D [] newPoints, int newPoint)
	{
		EPoint [] pts = new EPoint[newPoints.length];
		for(int i=0; i<newPoints.length; i++)
		{
			if (newPoints[i] != null)
				pts[i] = EPoint.fromLambda(newPoints[i].getX(), newPoints[i].getY());
		}
		new SetPoints(this, outlineNode, pts, newPoint);
	}

	/**
	 * Class to change the points on an outline node.
	 */
	private static class SetPoints extends Job
	{
		private transient OutlineListener listener;
		private NodeInst ni;
		private EPoint [] pts;
		private int newPoint;

		protected SetPoints(OutlineListener listener, NodeInst ni, EPoint [] pts, int newPoint)
		{
			super("Change Outline Points", User.getUserTool(), Job.Type.CHANGE, null, null, Job.Priority.USER);
			this.listener = listener;
			this.ni = ni;
			this.pts = pts;
			this.newPoint = newPoint;
			startJob();
		}

        @Override
		public boolean doIt() throws JobException
		{
			// make sure outline adjustment is allowed
			if (CircuitChangeJobs.cantEdit(ni.getParent(), ni, true, false, true) != 0) return false;

			// get the extent of the data
			ni.setTrace(pts);
			return true;
		}

        @Override
        public void terminateOK()
        {
			listener.setPoint(listener.point = newPoint);
        }
	}

}
