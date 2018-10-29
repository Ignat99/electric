/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: CurveListener.java
 *
 * Copyright (c) 2005, Static Free Software. All rights reserved.
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
import com.sun.electric.database.ImmutableArcInst;
import com.sun.electric.database.change.DatabaseChangeEvent;
import com.sun.electric.database.geometry.Poly;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.topology.ArcInst;
import com.sun.electric.database.topology.Geometric;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.JobException;
import com.sun.electric.tool.user.CircuitChangeJobs;
import com.sun.electric.tool.user.Highlighter;
import com.sun.electric.tool.user.User;
import com.sun.electric.util.math.DBMath;
import com.sun.electric.util.math.GenMath;

import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Point2D;
import java.util.List;

/**
 * Class to handle changes to arc curvature.
 */
public class CurveListener implements WindowFrame.ElectricEventListener
{
	private ArcInst curveAI;
	private WindowFrame.ElectricEventListener oldListener;
	private boolean through;

	private CurveListener() {}

	/**
	 * This method sets curvature on the highlighted arc.
	 * @param through true to have arc curve through cursor,
	 * false to have arc curve about cursor.
	 */
	public static void setCurvature(boolean through)
	{
        EditWindow wnd = EditWindow.needCurrent();
        if (wnd == null) return;
        Highlighter highlighter = wnd.getHighlighter();

		List<Geometric> geomList = highlighter.getHighlightedEObjs(true, true);
		if (geomList == null) return;
		if (geomList.size() != 1)
		{
			System.out.println("Select just one arc to setting curvature");
			return;
		}
		Geometric geom = geomList.get(0);
		if (!(geom instanceof ArcInst))
		{
			System.out.println("Select an arc before setting curvature");
			return;
		}
		WindowFrame.ElectricEventListener newListener = null;

		// remember the listener that was there before
		WindowFrame.ElectricEventListener oldListener = WindowFrame.getListener();

		System.out.println("Click to adjust curvature");
		newListener = oldListener;
		if (newListener == null || !(newListener instanceof CurveListener))
		{
			newListener = new CurveListener();
			WindowFrame.setListener(newListener);
		}
		((CurveListener)newListener).curveAI = (ArcInst)geom;
		((CurveListener)newListener).oldListener = oldListener;
		((CurveListener)newListener).through = through;
	}

	/**
	 * This method removes curvature on the currently selected arc.
	 */
	public static void removeCurvature()
	{
        EditWindow wnd = EditWindow.needCurrent();
        if (wnd == null) return;
        Highlighter highlighter = wnd.getHighlighter();

		List<Geometric> geomList = highlighter.getHighlightedEObjs(true, true);
		if (geomList == null) return;
		if (geomList.size() != 1)
		{
			System.out.println("Select just one arc to remove curvature");
			return;
		}
		Geometric geom = geomList.get(0);
		if (!(geom instanceof ArcInst))
		{
			System.out.println("Select an arc before removing curvature");
			return;
		}
		new SetArcCurvature((ArcInst)geom, 0);
	}

	public void mousePressed(MouseEvent evt)
	{
		showHighlight(evt, (EditWindow)evt.getSource());
	}

	public void mouseMoved(MouseEvent evt)
	{
		showHighlight(evt, (EditWindow)evt.getSource());
	}

	public void mouseDragged(MouseEvent evt)
	{
		showHighlight(evt, (EditWindow)evt.getSource());
	}

	public void mouseReleased(MouseEvent evt)
	{
		// restore the listener to the former state
		WindowFrame.setListener(oldListener);
        EditWindow wnd = (EditWindow)evt.getSource();
		showHighlight(null, wnd);

		// handle scaling the selected objects
		Point2D dbPt = wnd.screenToDatabase(evt.getX(), evt.getY());
		double lambdaCurvature;
		if (through)
		{
			lambdaCurvature = curveArcThroughPoint(curveAI, dbPt.getX(), dbPt.getY());
		} else
		{
			lambdaCurvature = curveArcAboutPoint(curveAI, dbPt.getX(), dbPt.getY());
		}
		new SetArcCurvature(curveAI, DBMath.lambdaToGrid(lambdaCurvature));
	}

	public void keyPressed(KeyEvent evt)
	{
		int chr = evt.getKeyCode();
		EditWindow wnd = (EditWindow)evt.getSource();
		Cell cell = wnd.getCell();
        if (cell == null) return;

		// ESCAPE for abort
		if (chr == KeyEvent.VK_ESCAPE)
		{
			// restore the listener to the former state
			WindowFrame.setListener(oldListener);
			showHighlight(null, wnd);
			System.out.println("Aborted");
		}
	}

	public void mouseClicked(MouseEvent evt) {}
	public void mouseEntered(MouseEvent evt) {}
	public void mouseExited(MouseEvent evt) {}
	public void mouseWheelMoved(MouseWheelEvent evt) {}
	public void keyReleased(KeyEvent evt) {}
	public void keyTyped(KeyEvent evt) {}
    public void databaseChanged(DatabaseChangeEvent e) {}

	private void showHighlight(MouseEvent evt, EditWindow wnd)
	{
        Highlighter highlighter = wnd.getHighlighter();

		highlighter.clear();
		if (evt != null)
		{
			Point2D dbPt = wnd.screenToDatabase(evt.getX(), evt.getY());
			double lambdaCurvature;
			if (through)
			{
				lambdaCurvature = curveArcThroughPoint(curveAI, dbPt.getX(), dbPt.getY());
			} else
			{
				lambdaCurvature = curveArcAboutPoint(curveAI, dbPt.getX(), dbPt.getY());
			}
			Poly curvedPoly = curveAI.curvedArcLambdaOutline(Poly.Type.CLOSED, curveAI.getGridBaseWidth(), DBMath.lambdaToGrid(lambdaCurvature));
			if (curvedPoly != null)
				highlighter.addPoly(curvedPoly, curveAI.getParent(), null);
		}
		highlighter.finished();
		wnd.repaint();
	}

	/**
	 * Method to return the curvature for arc "ai" that will allow it to
	 * curve about (xcur, ycur), a center point.
	 */
	private double curveArcAboutPoint(ArcInst ai, double xcur, double ycur)
	{
		// get true center of arc through cursor
		Point2D r0 = ai.getHeadLocation();
		Point2D r1 = ai.getTailLocation();
		int ang = ai.getDefinedAngle();
		double acx = (r0.getX() + r1.getX()) / 2;
		double acy = (r0.getY() + r1.getY()) / 2;
		Point2D ip = GenMath.intersect(new Point2D.Double(xcur, ycur), ang, new Point2D.Double(acx, acy), (ang+900)%3600);
		double r = r0.distance(ip);

		// now see if this point will be re-created
		Point2D [] pts = DBMath.findCenters(r, r0, r1);
		if (pts != null)
		{
			if (Math.abs(pts[0].getX()-ip.getX())+Math.abs(pts[0].getY()-ip.getY()) <
				Math.abs(pts[1].getX()-ip.getX())+Math.abs(pts[1].getY()-ip.getY())) r = -r;
		}
		return r;
	}

	/**
	 * Method to return the curvature for arc "ai" that will allow it to
	 * curve through (xcur, ycur), an edge point.
	 */
	private double curveArcThroughPoint(ArcInst ai, double xcur, double ycur)
	{
		Point2D r0 = ai.getHeadLocation();
		Point2D r1 = ai.getTailLocation();
		double r0x = r0.getX();   double r0y = r0.getY();
		double r1x = r1.getX();   double r1y = r1.getY();
		double r2x = xcur;        double r2y = ycur;
		double r02x = r2x-r0x;    double r02y = r2y-r0y;
		double rpx = r0y-r1y;     double rpy = r1x-r0x;
		double u = r02x;   u *= (r2x-r1x);
		double v = r02y;   v *= (r2y-r1y);
		double t = u + v;
		u = r02x;   u *= rpx;
		v = r02y;   v *= rpy;
		t /= (u + v) * 2.0f;
		double rcx = r0x + (r1x-r0x)/2 + t*rpx;
		double rcy = r0y + (r1y-r0y)/2 + t*rpy;
		Point2D rc = new Point2D.Double(rcx, rcy);

		//now see if this point will be re-created
		double r = r0.distance(rc);
		Point2D [] pts = DBMath.findCenters(r, r0, r1);
		if (pts != null)
		{
			if (Math.abs(pts[0].getX()-rcx) + Math.abs(pts[0].getY()-rcy) <
				Math.abs(pts[1].getX()-rcx) + Math.abs(pts[1].getY()-rcy)) r = -r;
		} else
		{
			rcx = r0x + (r1x-r0x)/2;
			rcy = r0y + (r1y-r0y)/2;
			r = r0.distance(rc) + 1;
		}
		return r;
	}

	private static class SetArcCurvature extends Job
	{
		private ArcInst curveAI;
		private long gridCurvature;

		protected SetArcCurvature(ArcInst curveAI, long gridCurvature)
		{
			super("Set arc curvature", User.getUserTool(), Job.Type.CHANGE, null, null, Job.Priority.USER);
			this.curveAI = curveAI;
			this.gridCurvature = gridCurvature;
			startJob();
		}

        @Override
		public boolean doIt() throws JobException
		{
            EditingPreferences ep = getEditingPreferences();
			// make sure changing the arc is allowed
			if (CircuitChangeJobs.cantEdit(curveAI.getParent(), null, true, false, true) != 0) return false;

			if (gridCurvature == 0)
			{
				if (curveAI.getVar(ImmutableArcInst.ARC_RADIUS) != null)
					curveAI.delVar(ImmutableArcInst.ARC_RADIUS);
			} else
			{
				curveAI.newVar(ImmutableArcInst.ARC_RADIUS, new Double(DBMath.gridToLambda(gridCurvature)), ep);
			}
			curveAI.modify(0, 0, 0, 0);
			return true;
		}
	}

}
