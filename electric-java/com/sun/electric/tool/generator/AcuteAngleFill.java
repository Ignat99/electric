/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: AcuteAngleFill.java
 *
 * Copyright (c) 2012, Static Free Software. All rights reserved.
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
package com.sun.electric.tool.generator;

import com.sun.electric.database.geometry.EPoint;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.topology.ArcInst;
import com.sun.electric.database.topology.Connection;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.variable.Variable;
import com.sun.electric.technology.Layer;
import com.sun.electric.technology.PrimitiveNode;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.JobException;
import com.sun.electric.tool.user.Highlighter;
import com.sun.electric.tool.user.User;
import com.sun.electric.tool.user.ui.EditWindow;
import com.sun.electric.util.math.GenMath;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Class to fill in the corners of acute angles
 */
public class AcuteAngleFill extends Job
{
	private Cell cell;
	private List<NodeInst> addedPolygons;

	private static final Variable.Key ACUTE_FILL_KEY = Variable.newKey("NODE_ACUTE_FILL");

	public AcuteAngleFill(Cell cell)
	{
		super("Acute Angle Fill", User.getUserTool(), Job.Type.CHANGE, null, null, Job.Priority.USER);
		this.cell = cell;
		addedPolygons = new ArrayList<NodeInst>(); 
		startJob();
	}

	@Override
	public boolean doIt()
		throws JobException
	{
		// first remove all previous acute fills
		List<NodeInst> killThese = new ArrayList<NodeInst>();
		for(Iterator<NodeInst> it = cell.getNodes(); it.hasNext(); )
		{
			NodeInst ni = it.next();
			Variable var = ni.getVar(ACUTE_FILL_KEY);
			if (var != null) killThese.add(ni);
		}
		for(NodeInst ni : killThese) ni.kill();
		if (killThese.size() > 0) System.out.println("Removing " + killThese.size() + " previous acute angle fill polygons");

		// now gather a list of pairs of arcs that need acute fill
		List<ArcPair> acutePairs = new ArrayList<ArcPair>();
		for(Iterator<NodeInst> it = cell.getNodes(); it.hasNext(); )
		{
			NodeInst ni = it.next();
			if (!ni.getFunction().isPin()) continue;

			// see if pin has nonManhattan arcs on it
			boolean allManhattan = true;
			for(Iterator<Connection> cIt = ni.getConnections(); cIt.hasNext(); )
			{
				Connection con = cIt.next();
				if ((con.getArc().getAngle() % 900) != 0) allManhattan = false;
			}
			if (allManhattan) continue;

			// gather all arcs connected to the pin
			List<ArcInst> arcsOnNode = new ArrayList<ArcInst>();
			for(Iterator<Connection> cIt = ni.getConnections(); cIt.hasNext(); )
			{
				Connection con = cIt.next();
				arcsOnNode.add(con.getArc());
			}

			for(ArcInst ai : arcsOnNode)
			{
				if ((ai.getAngle() % 900) == 0) continue;
				for(ArcInst aiOther : arcsOnNode)
				{
					if (ai == aiOther) continue;
					if (ai.getProto() != aiOther.getProto()) continue;
					EPoint e1 = null, e2 = null;
					if (ai.getHeadLocation().equals(ni.getAnchorCenter())) e1 = ai.getTailLocation(); else
						if (ai.getTailLocation().equals(ni.getAnchorCenter())) e1 = ai.getHeadLocation();
					if (aiOther.getHeadLocation().equals(ni.getAnchorCenter())) e2 = aiOther.getTailLocation(); else
						if (aiOther.getTailLocation().equals(ni.getAnchorCenter())) e2 = aiOther.getHeadLocation();
					if (e1 == null || e2 == null)
					{
						System.out.println("WARNING: Arcs " + ai.describe(false) + " and " + aiOther.describe(false) +
							" do not meet at the pin center (" + ni.getAnchorCenterX() + "," + ni.getAnchorCenterY() + ")");
						continue;
					}

					int angle1 = GenMath.figureAngle(ni.getAnchorCenter(), e1);
					int angle2 = GenMath.figureAngle(ni.getAnchorCenter(), e2);
					int angleDiff = Math.abs(angle1 - angle2);
					if ((angleDiff > 0 && angleDiff < 900) || (angleDiff > 2700 && angleDiff < 3600))
					{
						// found an acute angle
						ArcPair apNew = new ArcPair(ai, aiOther, ni);
						for(ArcPair ap : acutePairs)
						{
							if (ap.equals(apNew)) { apNew = null;  break; }
						}
						if (apNew != null) acutePairs.add(apNew);
					}
				}
			}
		}

		// add fill to the arcs that need them
		for(ArcPair ap : acutePairs)
		{
			NodeInst ni = ap.getNode();
			EPoint ctr = ni.getAnchorCenter();
			EPoint e1, e2;
			if (ap.getArc1().getHeadPortInst().getNodeInst() == ni) e1 = ap.getArc1().getTailLocation(); else
				e1 = ap.getArc1().getHeadLocation();
			if (ap.getArc2().getHeadPortInst().getNodeInst() == ni) e2 = ap.getArc2().getTailLocation(); else
				e2 = ap.getArc2().getHeadLocation();
			int angle1 = GenMath.figureAngle(ctr, e1);
			int angle2 = GenMath.figureAngle(ctr, e2);

			// find angle of fill and angle of center of fill
			int angleDiff = Math.abs(angle1 - angle2);
			if (angleDiff > 2700 && angleDiff < 3600) angleDiff = 3600 - angleDiff;
			int centerAngle = ((angle1 + angle2)/2) % 3600;
			if (Math.abs(centerAngle-angle1) >= 900 && Math.abs(centerAngle-angle2) >= 900)
				centerAngle = (centerAngle + 1800) % 3600;

			// find distance along sides of acute angle for placing fill
			double a = angleDiff / 1800.0 * Math.PI;
			double distanceOut = ap.getArc1().getLambdaBaseWidth() / Math.sin(a/2);

			// compute a point along the centerline between the arcs that will help find the vertex of the acute bend
			double centerDistance = distanceOut * 40;
			double optimalPointX = ctr.getX() + centerDistance * GenMath.cos(centerAngle);
			double optimalPointY = ctr.getY() + centerDistance * GenMath.sin(centerAngle);
			Point2D optimal = new Point2D.Double(optimalPointX, optimalPointY);

			// compute the two possible intersection points of the two edges of the arcs
			double halfWidth1 = ap.getArc1().getLambdaBaseWidth() / 2;
			double halfWidth2 = ap.getArc2().getLambdaBaseWidth() / 2;
			int rightAngle1 = (angle1 + 900) % 3600;
			int leftAngle1 = (angle1 + 2700) % 3600;
			int rightAngle2 = (angle2 + 900) % 3600;
			int leftAngle2 = (angle2 + 2700) % 3600;
			double right1X = ctr.getX() + halfWidth1 * GenMath.cos(rightAngle1);
			double right1Y = ctr.getY() + halfWidth1 * GenMath.sin(rightAngle1);
			Point2D right1 = new Point2D.Double(right1X, right1Y);
			double left1X = ctr.getX() + halfWidth1 * GenMath.cos(leftAngle1);
			double left1Y = ctr.getY() + halfWidth1 * GenMath.sin(leftAngle1);
			Point2D left1 = new Point2D.Double(left1X, left1Y);
			double right2X = ctr.getX() + halfWidth2 * GenMath.cos(rightAngle2);
			double right2Y = ctr.getY() + halfWidth2 * GenMath.sin(rightAngle2);
			Point2D right2 = new Point2D.Double(right2X, right2Y);
			double left2X = ctr.getX() + halfWidth2 * GenMath.cos(leftAngle2);
			double left2Y = ctr.getY() + halfWidth2 * GenMath.sin(leftAngle2);
			Point2D left2 = new Point2D.Double(left2X, left2Y);
			Point2D interA = GenMath.intersect(right1, angle1, left2, angle2);
			Point2D interB = GenMath.intersect(right2, angle2, left1, angle1);
			double distA = interA.distance(optimal);
			double distB = interB.distance(optimal);
			Point2D acuteVertex = (distA < distB ? interA : interB);

			// determine vertices along sides of acute angle
			double vert1X = acuteVertex.getX() + distanceOut * GenMath.cos(angle1);
			double vert1Y = acuteVertex.getY() + distanceOut * GenMath.sin(angle1);
			Point2D vert1 = new Point2D.Double(vert1X, vert1Y);
			double vert2X = acuteVertex.getX() + distanceOut * GenMath.cos(angle2);
			double vert2Y = acuteVertex.getY() + distanceOut * GenMath.sin(angle2);
			Point2D vert2 = new Point2D.Double(vert2X, vert2Y);

			// find intersection point
			Point2D apex = GenMath.intersect(vert2, rightAngle1, vert1, rightAngle2);

			// place the fill polygon
			Layer arcLayer = ap.getArc1().getProto().getLayer(0);
			PrimitiveNode pnp = arcLayer.getPureLayerNode();
			EPoint [] points = new EPoint[4];
			points[0] = EPoint.fromLambda(acuteVertex.getX(), acuteVertex.getY());
			points[1] = EPoint.fromLambda(vert1.getX(), vert1.getY());
			points[2] = EPoint.fromLambda(apex.getX(), apex.getY());
			points[3] = EPoint.fromLambda(vert2.getX(), vert2.getY());
			double lX = points[0].getX(), hX = points[0].getX(), lY = points[0].getY(), hY = points[0].getY();
			for(int i=1; i<4; i++)
			{
				if (points[i].getX() < lX) lX = points[i].getX();
				if (points[i].getX() > hX) hX = points[i].getX();
				if (points[i].getY() < lY) lY = points[i].getY();
				if (points[i].getY() > hY) hY = points[i].getY();
			}
			EPoint fillCenter = EPoint.fromLambda((lX+hX)/2, (lY+hY)/2);
			NodeInst fillNi = NodeInst.makeInstance(pnp, getEditingPreferences(), fillCenter, hX-lX, hY-lY, cell);
			fillNi.setTrace(points);
			fillNi.newVar(ACUTE_FILL_KEY, Boolean.TRUE, getEditingPreferences());
			addedPolygons.add(fillNi);
		}
		fieldVariableChanged("addedPolygons");
		return true;
	}

	public void terminateOK()
	{
		// show any added fill polygons
		if (addedPolygons.size() > 0)
		{
			System.out.println("Added " + addedPolygons.size() + " acute angle fill polygons");
			EditWindow wnd = EditWindow.getCurrent();
			if (wnd != null)
			{
				Highlighter highlighter = wnd.getHighlighter();
				highlighter.clear();
				for(NodeInst ni : addedPolygons)
					highlighter.addElectricObject(ni, cell);
				highlighter.finished();
			}
		}
	}

	/**
	 * Class to store a pair of arcs that are at an acute angle
	 */
	private static class ArcPair
	{
		private ArcInst ai1, ai2;
		private NodeInst ni;

		public ArcPair(ArcInst ai1, ArcInst ai2, NodeInst ni)
		{
			this.ai1 = ai1;
			this.ai2 = ai2;
			this.ni = ni;
		}

		public ArcInst getArc1() { return ai1; }

		public ArcInst getArc2() { return ai2; }

		public NodeInst getNode() { return ni; }

		public boolean equals(ArcPair other)
		{
			if (ai1 == other.ai1 && ai2 == other.ai2) return true;
			if (ai1 == other.ai2 && ai2 == other.ai1) return true;
			return false;
		}
	}
}
