/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: HighlightConnectivity.java
 * Module to find topology quickly
 * Written by: Steven M. Rubin
 *
 * Copyright (c) 2016, Static Free Software. All rights reserved.
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
import com.sun.electric.database.geometry.ERectangle;
import com.sun.electric.database.geometry.Poly;
import com.sun.electric.database.geometry.PolyBase;
import com.sun.electric.database.geometry.PolyBase.Point;
import com.sun.electric.database.geometry.PolyMerge;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.topology.ArcInst;
import com.sun.electric.database.topology.Geometric;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.topology.RTBounds;
import com.sun.electric.database.topology.RTNode;
import com.sun.electric.database.variable.ElectricObject;
import com.sun.electric.database.variable.Variable;
import com.sun.electric.technology.ArcProto;
import com.sun.electric.technology.Layer;
import com.sun.electric.technology.PrimitiveNode;
import com.sun.electric.technology.Technology;
import com.sun.electric.technology.Technology.ArcLayer;
import com.sun.electric.technology.Technology.NodeLayer;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.JobException;
import com.sun.electric.tool.user.ui.EditWindow;
import com.sun.electric.util.math.DBMath;
import com.sun.electric.util.math.FixpTransform;
import com.sun.electric.util.math.MutableInteger;
import com.sun.electric.util.math.Orientation;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Class to do quick extraction of a cell, comparing metals and vias to establish topology.
 */
public class HighlightConnectivity
{
	/** Cell in which routing occurs. */						private Cell cell;
	private List<ConnectingLayer> allConnectingLayers;
	private List<ConnectingVia> allConnectingVias;
	/** layer that removes metal in the technology. */			private Map<Layer,Layer> removeLayers;
	/** the extracted topology */								private String [] topology;
	/** key of Variable holding cell topology. */				public static final Variable.Key TOPOLOGY_QUICK_DATA = Variable.newKey("USER_topology_quick");
	/** key of Variable holding date of cell topology. */		public static final Variable.Key TOPOLOGY_QUICK_DATE = Variable.newKey("USER_topology_quick_date");

	public HighlightConnectivity(Cell cell)
	{
		this.cell = cell;
		
		// see if stored topology is present and valid
		Variable varData = cell.getVar(TOPOLOGY_QUICK_DATA);
		if (varData != null)
		{
			Variable varDate = cell.getVar(TOPOLOGY_QUICK_DATE);
			if (varDate != null)
			{
				Long t = (Long)varDate.getObject();
				Date topologyDate = new Date(t.longValue());
				if (topologyDate.after(cell.getRevisionDate()))
				{
					// use data stored in cell
					topology = (String[])varData.getObject();
					return;
				}
			}
		}

		// must compute topology. Start by gathering design rules and layers
		initializeDesignRules();

		// build R-Trees for all relevant layers
		buildRTrees();

		// traverse R-Trees to determine connectivity
		extractRTrees();

		// debug: show results
//		showGeometryInArea();

		// gather connected geometry
		Rectangle2D limit = cell.getBounds();
		Map<Integer,StringBuffer> geomsOnNet = new HashMap<Integer,StringBuffer>();
		for(ConnectingLayer cl : allConnectingLayers)
		{
			for (Iterator<QCBound> sea = cl.tree.search(limit); sea.hasNext();)
			{
				QCBound sBound = sea.next();
				Geometric geom = sBound.getTopGeom();
				if (geom == null) continue;
				int thisNet = 0;
				if (sBound.getNetID() != null && sBound.getNetID().intValue() != 0) thisNet = sBound.getNetID().intValue();
				if (thisNet != 0)
				{
					Integer thisNetInt = new Integer(thisNet);
					StringBuffer sb = geomsOnNet.get(thisNetInt);
					if (sb == null) geomsOnNet.put(thisNetInt, sb = new StringBuffer());
					if (sb.length() != 0) sb.append("/");
					if (geom instanceof NodeInst)
					{
						NodeInst ni = (NodeInst)geom;
						sb.append("N" + ni.getName());
					} else
					{
						ArcInst ai = (ArcInst)geom;
						sb.append("A" + ai.getName());
					}
					ERectangle geomRect = geom.getBounds();
					ERectangle rTreeRect = sBound.getBounds();
					if (geomRect.equals(sBound.getBounds())) continue;
					sb.append("[" + rTreeRect.getMinX() + ";" + rTreeRect.getMinY() + ";" + rTreeRect.getWidth() + ";" + rTreeRect.getHeight() + "]");
				}
			}
		}

		// store network information
		int numNets = geomsOnNet.size();
		topology = new String[numNets];
		int netNum = 0;
		for(Integer key : geomsOnNet.keySet())
		{
			StringBuffer sb = geomsOnNet.get(key);
			topology[netNum++] = sb.toString();
		}
		new StoreTopology(cell, topology);
	}

	/**
	 * Method to return the extracted topology.
	 * This is an array of Strings, each string listing all of the node and arc names that are connected to each other.
	 * @return the extracted topology.
	 */
	public String[] getTopology() { return topology; }

	/**
	 * Class to save computed topology in a Cell.
	 */
	public static class StoreTopology extends Job
	{
		private Cell cell;
		private String[] topology;

		public StoreTopology(Cell cell, String[] topology)
		{
			super("Store quick topology", User.getUserTool(), Job.Type.CHANGE, null, null, Job.Priority.USER);
			this.cell = cell;
			this.topology = topology;
			startJob();
		}

		public boolean doIt() throws JobException
		{
			// get current revision date of the cell
			Date revDate = cell.getRevisionDate();

			// store the data
			Date nowDate = new Date();
			cell.newVar(TOPOLOGY_QUICK_DATE, new Long(nowDate.getTime()), getEditingPreferences());
			cell.newVar(TOPOLOGY_QUICK_DATA, topology, getEditingPreferences());

			// reset revision date to ignore this change
            cell.lowLevelSetRevisionDate(revDate);
			return true;
		}
	}

	/***************************** METHODS TO BUILD THE R-TREE *****************************/

	private void buildRTrees()
	{
		// recursively add all polygons in the cell
		Map<Layer,List<Rectangle2D>> removeGeometry = new HashMap<Layer,List<Rectangle2D>>();
		List<EPoint> linesInNonMahnattan = new ArrayList<EPoint>();
		boolean nonMan = addArea(cell, true, Orientation.IDENT.pureRotate(), linesInNonMahnattan, removeGeometry);
		if (nonMan)
			System.out.println("Non-Manhattan geometry found");

		// now remove any geometry that was covered by a removal layer
		for(Layer cutLayer : removeGeometry.keySet())
		{
			Layer primaryLayer = removeLayers.get(cutLayer);
			List<Rectangle2D> removeRects = removeGeometry.get(cutLayer);
			ConnectingLayer thisCL = null;
			for(ConnectingLayer cl : allConnectingLayers)
			{
				if (cl.layers.contains(primaryLayer)) { thisCL = cl;   break; }
			}
			if (thisCL == null) continue;
			GeometryTree bTree = thisCL.tree;
			for(Rectangle2D rect : removeRects)
			{
				List<QCBound> thingsThatGetRemoved = new ArrayList<QCBound>();
				for (Iterator<QCBound> sea = bTree.search(rect); sea.hasNext();)
				{
					QCBound sBound = sea.next();
					Rectangle2D bound = sBound.getBounds();
					if (bound.getMaxX() <= rect.getMinX() || bound.getMinX() >= rect.getMaxX() ||
						bound.getMaxY() <= rect.getMinY() || bound.getMinY() >= rect.getMaxY()) continue;
					thingsThatGetRemoved.add(sBound);
				}

				// remove those R-Tree elements that get cut
				RTNode<QCBound> rootFixp = bTree.getRoot();
				for(QCBound s : thingsThatGetRemoved)
				{
					RTNode<QCBound> newRootFixp = RTNode.unLinkGeom(null, rootFixp, s);
					if (newRootFixp != rootFixp) bTree.setRoot(rootFixp = newRootFixp);
				}

				// now reinsert geometry that wasn't removed
				for(QCBound s : thingsThatGetRemoved)
				{
					PolyMerge merge = new PolyMerge();
					merge.addRectangle(cutLayer, s.getBounds());
					merge.subtract(cutLayer, new Poly(rect));
					List<PolyBase> remaining = merge.getMergedPoints(cutLayer, true);
					for(PolyBase pb : remaining)
					{
						ERectangle reducedBound = ERectangle.fromLambda(pb.getBounds2D());
						QCBound qcb = new QCBound(reducedBound, s.getNetID(), s.getTopGeom());
						RTNode<QCBound> newRootFixp = RTNode.linkGeom(null, rootFixp, qcb);
						if (newRootFixp != rootFixp) bTree.setRoot(rootFixp = newRootFixp);
					}
				}
			}
		}
	}

	/**
	 * Method to add geometry to the blockage R-Trees.
	 * @param cell
	 * @param transToTop
	 * @param topLevel
	 * @param nextNetNumber
	 * @param linesInNonMahnattan List to store non-Manhattan geometry
	 * @return true if some of the geometry is nonmanhattan (and may cause problems)
	 */
	private boolean addArea(Cell cell, boolean topLevel, FixpTransform transToTop,
		List<EPoint> linesInNonMahnattan, Map<Layer,List<Rectangle2D>> removeGeometry)
	{
		// first add nodes
		boolean hasNonmanhattan = false;
		for(Iterator<NodeInst> it = cell.getNodes(); it.hasNext(); )
		{
			NodeInst ni = it.next();
			if (ni.isCellInstance())
			{
				FixpTransform transBack = ni.transformOut(transToTop);
				addArea((Cell)ni.getProto(), false, transBack, linesInNonMahnattan, removeGeometry);
			} else
			{
				PrimitiveNode pNp = (PrimitiveNode)ni.getProto();
				if (pNp.getFunction() == PrimitiveNode.Function.PIN) continue;
				FixpTransform nodeTrans = ni.rotateOut(transToTop);
				Technology tech = pNp.getTechnology();
				Poly[] nodeInstPolyList = tech.getShapeOfNode(ni, true, false, null);
				for (int i = 0; i < nodeInstPolyList.length; i++)
				{
					PolyBase poly = nodeInstPolyList[i];
					if (addLayer(poly, topLevel ? ni : null, nodeTrans, linesInNonMahnattan, removeGeometry)) hasNonmanhattan = true;
				}
			}
		}

		// next add arcs
		for(Iterator<ArcInst> it = cell.getArcs(); it.hasNext(); )
		{
			ArcInst ai = it.next();
			Technology tech = ai.getProto().getTechnology();
			PolyBase[] polys = tech.getShapeOfArc(ai);
			for (int i = 0; i < polys.length; i++)
			{
				PolyBase poly = polys[i];
				if (addLayer(poly, topLevel ? ai : null, transToTop, linesInNonMahnattan, removeGeometry)) hasNonmanhattan = true;
			}
		}
		return hasNonmanhattan;
	}

	/**
	 * Method to add geometry to the R-Tree.
	 * @param poly the polygon to add (only rectangles are added, so the bounds is used).
	 * @param trans a transformation matrix to apply to the polygon.
	 * @param netID the global network ID of the geometry.
	 * (converted to non-pseudo and stored). False to ignore pseudo-layers.
	 * @param lockTree true to lock the R-Tree before accessing it (when doing parallel routing).
	 * @return true if the geometry is nonmanhattan (and may cause problems).
	 */
	private boolean addLayer(PolyBase poly, Geometric topGeom, FixpTransform trans, List<EPoint> linesInNonMahnattan, Map<Layer,List<Rectangle2D>> removeGeometry)
	{
		boolean isNonmanhattan = false;
		Layer layer = poly.getLayer();

		// save any removal geometry
		Layer removeLay = removeLayers.get(layer);
		if (removeLay != null)
		{
			List<Rectangle2D> geomsToRemove = removeGeometry.get(layer);
			if (geomsToRemove == null) removeGeometry.put(layer, geomsToRemove = new ArrayList<Rectangle2D>());
			poly.transform(trans);
			Rectangle2D bounds = poly.getBox();
			if (bounds == null) return true;
			geomsToRemove.add(bounds);
			return false;
		}

		for(ConnectingLayer cl : allConnectingLayers)
		{
			if (cl.layers.contains(layer))
			{
				// ignore polygons that aren't solid filled areas
				if (poly.getStyle() != Poly.Type.FILLED) return false;
				poly.transform(trans);
				Rectangle2D bounds = poly.getBox();
				if (bounds == null)
				{
					addPolygon(poly, cl, topGeom);
					Point[] points = poly.getPoints();
					for (int i=1; i<points.length; i++)
					{
						if (points[i-1].getX() != points[i].getX() && points[i-1].getY() != points[i].getY())
						{
							isNonmanhattan = true;
							linesInNonMahnattan.add(EPoint.fromLambda(points[i-1].getX(), points[i-1].getY()));
							linesInNonMahnattan.add(EPoint.fromLambda(points[i].getX(), points[i].getY()));
						}
					}
				} else
				{
					addRectangle(bounds, cl, topGeom);
				}
			}
		}

		for(ConnectingVia cv : allConnectingVias)
		{
			if (layer == cv.viaLayer)
			{
				Rectangle2D bounds = poly.getBounds2D();
				DBMath.transformRect(bounds, trans);
				addVia(ERectangle.fromLambda(bounds), cv);
			}
		}
		return isNonmanhattan;
	}

	/**
	 * Method to add a rectangle to the metal R-Tree.
	 * @param bounds the rectangle to add.
	 * @param layer the metal layer on which to add the rectangle.
	 * @param netID the global network ID of the geometry.
	 * @param lockTree true to lock the R-Tree before accessing it (when doing parallel routing).
	 */
	private QCBound addRectangle(Rectangle2D bounds, ConnectingLayer cl, Geometric topGeom)
	{
		QCBound qcb = null;
		GeometryTree bTree = cl.tree;

		// avoid duplication
		List<QCBound> removeThese = null;
		for (Iterator<QCBound> sea = bTree.search(bounds); sea.hasNext(); )
		{
			QCBound sBound = sea.next();
			if (sBound instanceof QCPoly) continue;

			// if an existing bound is bigger than new one, ignore this
			ERectangle r = sBound.getBounds();
			if (r.getMinX() <= bounds.getMinX() &&
				r.getMaxX() >= bounds.getMaxX() &&
				r.getMinY() <= bounds.getMinY() &&
				r.getMaxY() >= bounds.getMaxY()) return null;

			// if new one is bigger than an existing bound, remove existing one
			if (bounds.getMinX() <= r.getMinX() &&
				bounds.getMaxX() >= r.getMaxX() &&
				bounds.getMinY() <= r.getMinY() &&
				bounds.getMaxY() >= r.getMaxY())
			{
				if (removeThese == null) removeThese = new ArrayList<QCBound>();
				removeThese.add(sBound);
			}
		}

		if (removeThese != null)
		{
			RTNode<QCBound> rootFixp = bTree.getRoot();
			for(QCBound s : removeThese)
			{
				RTNode<QCBound> newRootFixp = RTNode.unLinkGeom(null, rootFixp, s);
				if (newRootFixp != rootFixp) bTree.setRoot(rootFixp = newRootFixp);
			}
		}

		qcb = new QCBound(ERectangle.fromLambda(bounds), null, topGeom);
		RTNode<QCBound> rootFixp = bTree.getRoot();
		if (rootFixp == null)
		{
			rootFixp = RTNode.makeTopLevel();
			bTree.setRoot(rootFixp);
		}
		RTNode<QCBound> newRootFixp = RTNode.linkGeom(null, rootFixp, qcb);
		if (newRootFixp != rootFixp) bTree.setRoot(newRootFixp);
		return qcb;
	}

	/**
	 * Method to add a polygon to the metal R-Tree.
	 * @param poly the polygon to add.
	 * @param layer the metal layer on which to add the rectangle.
	 * @param netID the global network ID of the geometry.
	 * @param lockTree true to lock the R-Tree before accessing it (when doing parallel routing).
	 */
	private void addPolygon(PolyBase poly, ConnectingLayer cl, Geometric topGeom)
	{
		GeometryTree bTree = cl.tree;
		QCBound qcb = new QCPoly(ERectangle.fromLambda(poly.getBounds2D()), null, poly, topGeom);
		RTNode<QCBound> rootFixp = bTree.getRoot();
		if (rootFixp == null)
		{
			rootFixp = RTNode.makeTopLevel();
			bTree.setRoot(rootFixp);
		}
		RTNode<QCBound> newRootFixp = RTNode.linkGeom(null, rootFixp, qcb);
		if (newRootFixp != rootFixp) bTree.setRoot(newRootFixp);
	}

	/**
	 * Method to add a point to the via R-Tree.
	 * @param loc the point to add.
	 * @param layer the via layer on which to add the point.
	 * @param netID the global network ID of the geometry.
	 * @param lockTree true to lock the R-Tree before accessing it (when doing parallel routing).
	 */
	private void addVia(ERectangle rect, ConnectingVia cv)
	{
		GeometryTree bTree = cv.tree;

		// remove duplicate vias and favor colored ones
		for(Iterator<QCBound> it = bTree.search(rect); it.hasNext(); )
		{
			QCBound qcb = it.next();
			if (qcb.getBounds().getCenterX() == rect.getCenterX() && qcb.getBounds().getCenterY() == rect.getCenterY())
			{
				return;
			}
		}

		QCBound qcb = new QCVia(rect, null);
		RTNode<QCBound> rootFixp = bTree.getRoot();
		if (rootFixp == null)
		{
			rootFixp = RTNode.makeTopLevel();
			bTree.setRoot(rootFixp);
		}
		RTNode<QCBound> newRootFixp = RTNode.linkGeom(null, rootFixp, qcb);
		if (newRootFixp != rootFixp) bTree.setRoot(newRootFixp);
	}

	/***************************** METHODS TO TRAVERSE THE R-TREE *****************************/

	Map<Integer,List<MutableInteger>> netIDsByValue = new HashMap<Integer,List<MutableInteger>>();
	
	private void extractRTrees()
	{
		int netID = 1;
		for(ConnectingLayer cl : allConnectingLayers)
		{
			for (Iterator<QCBound> sea = cl.tree.search(cell.getBounds()); sea.hasNext();)
			{
				QCBound sBound = sea.next();
				if (sBound.getNetID() != null) continue;
				MutableInteger mi = new MutableInteger(netID++);
				sBound.setNetID(mi);
				growArea(sBound, cl, sBound.getNetID());
			}
		}
	}

	private void growArea(QCBound sBound, ConnectingLayer cl, MutableInteger idNumber)
	{
		GeometryTree metalTree = cl.tree;
		Rectangle2D bound = sBound.getBounds();
		for (Iterator<QCBound> sea = metalTree.search(bound); sea.hasNext(); )
		{
			QCBound subBound = sea.next();
			if (subBound == sBound) continue;
			if (sBound instanceof QCPoly || subBound instanceof QCPoly)
			{
				// make sure they really intersect
				if (!doesIntersect(sBound, subBound)) continue;
			}
			if (subBound.getNetID() == null)
			{
				subBound.setNetID(idNumber);
				growArea(subBound, cl, idNumber);
				continue;
			}
			subBound.updateNetID(idNumber, netIDsByValue);
		}

		// find vias that connect this to a different layer
		for(ConnectingVia cv : allConnectingVias)
		{
			if (cl != cv.layer1 && cl != cv.layer2) continue;
			ConnectingLayer otherLayer = (cl == cv.layer1 ? cv.layer2 : cv.layer1); 
			GeometryTree viaTree = cv.tree;
			if (!viaTree.isEmpty())
			{
				for (Iterator<QCBound> sea = viaTree.search(bound); sea.hasNext(); )
				{
					QCVia subBound = (QCVia)sea.next();
					if (sBound instanceof QCPoly)
					{
						// make sure they really intersect
						if (!doesIntersect(sBound, subBound)) continue;
					}
					if (subBound.getNetID() == null)
					{
						subBound.setNetID(idNumber);
						growPoint(subBound.getBounds().getCenterX(), subBound.getBounds().getCenterY(), otherLayer, idNumber);
						continue;
					}
					subBound.updateNetID(idNumber, netIDsByValue);
				}
			}
		}
	}

	/**
	 * Method to accumulate a list of blockage rectangles that are at a given coordinate.
	 * @param x the X coordinate.
	 * @param y the Y coordinate.
	 * @param layerNum the metal layer number (0-based).
	 * @param idNumber the network number being propagated.
	 * @return true if this network number is already at the coordinate.
	 */
	private boolean growPoint(double x, double y, ConnectingLayer layer, MutableInteger idNumber)
	{
		Rectangle2D search = new Rectangle2D.Double(x, y, 0, 0);
		GeometryTree bTree = layer.tree;
		if (bTree.isEmpty()) return false;
		boolean foundNet = false;
		for (Iterator<QCBound> sea = bTree.search(search); sea.hasNext();)
		{
			QCBound subBound = sea.next();
			if (!subBound.containsPoint(x, y)) continue;
			if (subBound.getNetID() == null)
			{
				subBound.setNetID(idNumber);
				growArea(subBound, layer, idNumber);
				continue;
			} else
			{
				if (!subBound.isSameBasicNet(idNumber)) 
					subBound.updateNetID(idNumber, netIDsByValue);
			}
		}
		return foundNet;
	}

	private boolean doesIntersect(QCBound bound1, QCBound bound2)
	{
		// first see if the polygons are Manhattan
		if (!bound1.isManhattan() || !bound2.isManhattan()) return true;

		EPoint[] points1;
		if (bound1 instanceof QCPoly)
		{
			QCPoly p = (QCPoly)bound1;
			Point[] po = p.poly.getPoints();
			points1 = new EPoint[po.length];
			for(int i=0; i<po.length; i++) points1[i] = EPoint.fromLambda(po[i].getX(), po[i].getY());
		} else
		{
			points1 = new EPoint[5];
			ERectangle r = bound1.getBounds();
			points1[0] = EPoint.fromLambda(r.getMinX(), r.getMinY());
			points1[1] = EPoint.fromLambda(r.getMinX(), r.getMaxY());
			points1[2] = EPoint.fromLambda(r.getMaxX(), r.getMaxY());
			points1[3] = EPoint.fromLambda(r.getMaxX(), r.getMinY());
			points1[4] = EPoint.fromLambda(r.getMinX(), r.getMinY());
		}

		EPoint[] points2;
		if (bound2 instanceof QCPoly)
		{
			QCPoly p = (QCPoly)bound2;
			Point[] po = p.poly.getPoints();
			points2 = new EPoint[po.length];
			for(int i=0; i<po.length; i++) points2[i] = EPoint.fromLambda(po[i].getX(), po[i].getY());
		} else
		{
			points2 = new EPoint[5];
			ERectangle r = bound2.getBounds();
			points2[0] = EPoint.fromLambda(r.getMinX(), r.getMinY());
			points2[1] = EPoint.fromLambda(r.getMinX(), r.getMaxY());
			points2[2] = EPoint.fromLambda(r.getMaxX(), r.getMaxY());
			points2[3] = EPoint.fromLambda(r.getMaxX(), r.getMinY());
			points2[4] = EPoint.fromLambda(r.getMinX(), r.getMinY());
		}

		// now look for line intersections
		for(int i=1; i<points1.length; i++)
		{
			EPoint p1a = points1[i-1];
			EPoint p1b = points1[i];
			if (p1a.getX() == p1b.getX() && p1a.getY() == p1b.getY()) continue;
			double l1X = Math.min(p1a.getX(), p1b.getX());
			double h1X = Math.max(p1a.getX(), p1b.getX());
			double l1Y = Math.min(p1a.getY(), p1b.getY());
			double h1Y = Math.max(p1a.getY(), p1b.getY());
			for(int j=1; j<points2.length; j++)
			{
				EPoint p2a = points2[j-1];
				EPoint p2b = points2[j];
				if (p2a.getX() == p2b.getX() && p2a.getY() == p2b.getY()) continue;
				double l2X = Math.min(p2a.getX(), p2b.getX());
				double h2X = Math.max(p2a.getX(), p2b.getX());
				double l2Y = Math.min(p2a.getY(), p2b.getY());
				double h2Y = Math.max(p2a.getY(), p2b.getY());

				if (l1X == h1X)
				{
					// line 1 is vertical
					if (l2X == h2X)
					{
						// both lines are vertical
						if (l1X != l2X) continue;
						if (h1Y > l2Y && h2Y > l1Y) return true;
						continue;
					}

					// line one vertical, line two horizontal
					if (l1X > l2X && l1X < h2X && l2Y > l1Y && l2Y < h1Y) return true;
					continue;
				} else
				{
					// line 1 is horizontal
					if (l2Y == h2Y)
					{
						// both lines are horizontal
						if (l1Y != l2Y) continue;
						if (h1X > l2X && h2X > l1X) return true;
						continue;
					}

					// line one horizontal, line two vertical
					if (l1Y > l2Y && l1Y < h2Y && l2X > l1X && l2X < h1X) return true;
					continue;
				}
			}
		}

		// no intersection. Check for complete surround
		Poly p1 = new Poly(points1);
		if (p1.contains(points2[0])) return true;

		Poly p2 = new Poly(points2);
		if (p2.contains(points1[0])) return true;

		// they do not intersect
		return false;
	}

	/***************************** METHODS TO DISPLAY THE R-TREE *****************************/

	/**
	 * Method to show blockages in a routing area.
	 * @param nr the NeededRoute to view.
	 */
	private void showGeometryInArea()
	{
		EditWindow wnd = EditWindow.getCurrent();
		Cell cell = wnd.getCell();
		Highlighter h = wnd.getRulerHighlighter();
		h.clear();
		Map<Integer,Color> netColors = new HashMap<Integer,Color>();
		MutableInteger colorAssigned = new MutableInteger(0);
		double highestX = Double.MIN_VALUE, highestY = Double.MIN_VALUE;

		// show geometry in area
		Rectangle2D limit = cell.getBounds();
		Map<Layer,List<QCBound>> allAssigned = new HashMap<Layer,List<QCBound>>();
		for(ConnectingLayer cl : allConnectingLayers)
		{
			Layer layer = cl.primaryLayer;
			List<QCBound> assigned = new ArrayList<QCBound>();
			allAssigned.put(layer, assigned);
			for (Iterator<QCBound> sea = cl.tree.search(limit); sea.hasNext();)
			{
				QCBound sBound = sea.next();
				if (sBound.getNetID() != null && sBound.getNetID().intValue() != 0) assigned.add(sBound); else
				{
					ERectangle drawn = showGeometryPiece(sBound, limit, layer, netColors, colorAssigned);
					if (drawn != null)
					{
						if (drawn.getMaxX() > highestX) highestX = drawn.getMaxX();
						if (drawn.getMaxY() > highestY) highestY = drawn.getMaxY();
					}
				}
			}
		}
		for(ConnectingLayer cl : allConnectingLayers)
		{
			Layer layer = cl.primaryLayer;
			List<QCBound> assigned = allAssigned.get(layer);
			for(QCBound sBound : assigned)
			{
				ERectangle drawn = showGeometryPiece(sBound, limit, layer, netColors, colorAssigned);
				if (drawn != null)
				{
					if (drawn.getMaxX() > highestX) highestX = drawn.getMaxX();
					if (drawn.getMaxY() > highestY) highestY = drawn.getMaxY();
				}
			}
		}

		// show key
		double pos = highestY - 2;
		for(Integer netIDI : netColors.keySet())
		{
			showBlockageRect(cell, new Rectangle2D.Double(highestX+1, pos, 4, 2), h, netColors.get(netIDI));
			String netName = "Net " + netIDI.intValue();
			h.addMessage(cell, netName, EPoint.fromLambda(highestX+6, pos+1));
			pos -= 3;
		}
		h.finished();
		EditWindow.repaintAllContents();
	}

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

	private ERectangle showGeometryPiece(QCBound sBound, Rectangle2D limit, Layer lay, Map<Integer,Color> netColors, MutableInteger colorAssigned)
	{
		Integer netIDI;
		MutableInteger mi = sBound.getNetID();
		if (mi == null) netIDI = Integer.valueOf(0); else
			netIDI = Integer.valueOf(mi.intValue());
		Color color = Color.BLACK;
		if (netIDI.intValue() != 0)
		{
			color = netColors.get(netIDI);
			if (color == null)
			{
				netColors.put(netIDI, color = allColors[colorAssigned.intValue() % allColors.length]);
				colorAssigned.increment();
			}
		}
		ERectangle draw = sBound.getBounds();
		EditWindow wnd = EditWindow.getCurrent();
		Cell cell = wnd.getCell();
		Highlighter h = wnd.getRulerHighlighter();
		if (sBound instanceof QCPoly)
		{
			QCPoly pol = (QCPoly)sBound;
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

	/**
	 * Method to draw a thick rectangular outline.
	 * @param cell the Cell in which to draw the outline.
	 * @param bounds the rectangle to draw.
	 * @param h the Highlighter to use.
	 * @param col the Color to draw it.
	 */
	private void showBlockageRect(Cell cell, Rectangle2D bounds, Highlighter h, Color col)
	{
//		Poly poly = new Poly(bounds);
//		poly.setStyle(Poly.Type.FILLED);
//		h.addPoly(poly, cell, col);

		Point2D p1 = new Point2D.Double(bounds.getMinX(), bounds.getMinY());
		Point2D p2 = new Point2D.Double(bounds.getMinX(), bounds.getMaxY());
		Point2D p3 = new Point2D.Double(bounds.getMaxX(), bounds.getMaxY());
		Point2D p4 = new Point2D.Double(bounds.getMaxX(), bounds.getMinY());
		h.addLine(p1, p2, cell, true, col, false);
		h.addLine(p2, p3, cell, true, col, false);
		h.addLine(p3, p4, cell, true, col, false);
		h.addLine(p4, p1, cell, true, col, false);

		h.addLine(p1, p3, cell, true, col, false);
		h.addLine(p2, p4, cell, true, col, false);
	}

	/***************************** INITIALIZATION *****************************/

	/**
	 * Method to initialize technology information, including design rules.
	 * @return true on error.
	 */
	private boolean initializeDesignRules()
	{
		Technology tech = cell.getTechnology();
		allConnectingLayers = new ArrayList<ConnectingLayer>();
		allConnectingVias = new ArrayList<ConnectingVia>();

		// find the metal layers
		for(Iterator<Layer> it = tech.getLayers(); it.hasNext(); )
		{
			Layer lay = it.next();
			if (!lay.getFunction().isMetal()) continue;
			if ((lay.getFunctionExtras()&Layer.Function.CUTLAYER) != 0) continue;
			int metNum = lay.getFunction().getLevel() - 1;
			boolean found = false;
			for(ConnectingLayer cl : allConnectingLayers)
			{
				for(Layer otherLayer : cl.layers)
				{
					if (otherLayer.getFunction().isMetal() && otherLayer.getFunction().getLevel()-1 == metNum)
					{
						cl.layers.add(lay);
						found = true;
						break;
					}
				}
				if (found) break;
			}
			if (!found)
			{
				ConnectingLayer cl = new ConnectingLayer();
				cl.layers.add(lay);
				allConnectingLayers.add(cl);
			}
		}
		for(ConnectingLayer cl : allConnectingLayers)
		{
			for(Layer lay : cl.layers)
			{
				int colorNum = lay.getFunction().getMaskColor();
				if (colorNum == 0) cl.primaryLayer = lay;
			}
			if (cl.primaryLayer == null) cl.primaryLayer = cl.layers.get(0);
		}

		// find the poly layers
		for(Iterator<Layer> it = tech.getLayers(); it.hasNext(); )
		{
			Layer lay = it.next();
			if (!lay.getFunction().isPoly()) continue;
			if ((lay.getFunctionExtras()&Layer.Function.CUTLAYER) != 0) continue;
			ConnectingLayer cl = new ConnectingLayer();
			cl.layers.add(lay);
			cl.primaryLayer = lay;
			allConnectingLayers.add(cl);
		}

		// find all arcs related to these layers
		for(ConnectingLayer cl : allConnectingLayers)
		{
			for(Layer lay : cl.layers)
			{
				for(Iterator<ArcProto> it = tech.getArcs(); it.hasNext(); )
				{
					ArcProto ap = it.next();
					ArcLayer[] arcLayers = ap.getArcLayers();
					for(int i=0; i<arcLayers.length; i++)
					{
						ArcLayer al = arcLayers[i];
						if (al.getLayer() == lay)
						{
							cl.arcs.add(ap);
							break;
						}
					}
				}
			}
		}

		// now gather the via information
		for (Iterator<PrimitiveNode> it = tech.getNodes(); it.hasNext();)
		{
			PrimitiveNode np = it.next();
			if (!np.getFunction().isContact()) continue;
			
			// this is a contact
			NodeLayer[] nodeLayers = np.getNodeLayers();
			Layer viaLayer = null;
			for(int i=0; i<nodeLayers.length; i++)
			{
				NodeLayer nl = nodeLayers[i];
				if (!nl.getLayer().getFunction().isContact()) continue;
				viaLayer = nl.getLayer();
				break;
			}
			if (viaLayer == null)
			{
				System.out.println("WARNING: Contact " + np.describe(false) + " has no via layer in it");
				continue;
			}

			ConnectingVia thisCV = null;
			for(ConnectingVia cv : allConnectingVias)
			{
				if (cv.viaLayer == viaLayer) { thisCV = cv;   break; }
			}
			if (thisCV == null)
			{
				thisCV = new ConnectingVia();
				thisCV.viaLayer = viaLayer;
				allConnectingVias.add(thisCV);
			}

			// find connecting arcs
			ArcProto[] conns = np.getPort(0).getConnections();
			List<ArcProto> validArcs = new ArrayList<ArcProto>();
			for(int i=0; i<conns.length; i++)
			{
				ArcProto ap = conns[i];
				if (ap.getTechnology() != tech) continue;
				validArcs.add(ap);
			}
			if (validArcs.size() != 2) System.out.println("WARNING: Node "+np.describe(false)+" connects to " + validArcs.size() + " arcs (should be 2)"); else
			{
				ConnectingLayer lay1 = null, lay2 = null;
				for(ArcProto ap : validArcs)
				{
					for(ConnectingLayer cl : allConnectingLayers)
					{
						if (cl.arcs.contains(ap))
						{
							if (lay1 == null) lay1 = cl; else
								lay2 = cl;
							break;
						}
					}
				}
				if (lay1 == null || lay2 == null)
				{
					System.out.println("WARNING: Could not find layers for arcs " + validArcs.get(0).describe() + " and " + validArcs.get(1).describe());
					continue;
				}
				if (thisCV.layer1 != null)
				{
					// validate the layers
					if ((thisCV.layer1 != lay1 || thisCV.layer2 != lay2) &&
						(thisCV.layer1 != lay2 || thisCV.layer2 != lay1))
					{
						System.out.println("WARNING: Via "+thisCV.viaLayer.getName() + " connects layers " +
							thisCV.layer1.primaryLayer.getName() + " and " + thisCV.layer2.primaryLayer.getName() + " but contact " +
							np.describe(false) + " joins layers " + lay1.primaryLayer.getName() + " and " + lay2.primaryLayer.getName());
					}
				} else
				{
					thisCV.layer1 = lay1;
					thisCV.layer2 = lay2;
				}
			}
		}

		// find the layers that cut others
		removeLayers = new HashMap<Layer,Layer>();
		for(Iterator<Layer> it = tech.getLayers(); it.hasNext(); )
		{
			Layer lay = it.next();
			int extra = lay.getFunctionExtras();
			if ((extra&Layer.Function.CUTLAYER) == 0) continue;
			if (lay.getFunction().isMetal())
			{
				int metNum = lay.getFunction().getLevel() - 1;
				boolean found = false;
				for(ConnectingLayer cl : allConnectingLayers)
				{
					for(Layer otherLayer : cl.layers)
					{
						if (otherLayer.getFunction().isMetal() && otherLayer.getFunction().getLevel()-1 == metNum)
						{
							removeLayers.put(lay, cl.primaryLayer);
							found = true;
							break;
						}
					}
					if (found) break;
				}
			} else if (lay.getFunction().isPoly())
			{
				int polyNum = lay.getFunction().getLevel() - 1;
				boolean found = false;
				for(ConnectingLayer cl : allConnectingLayers)
				{
					for(Layer otherLayer : cl.layers)
					{
						if (otherLayer.getFunction().isPoly() && otherLayer.getFunction().getLevel()-1 == polyNum)
						{
							removeLayers.put(lay, cl.primaryLayer);
							found = true;
							break;
						}
					}
					if (found) break;
				}
			}
		}

//for(ConnectingLayer cl : allConnectingLayers)
//{
//	System.out.print("LAYER "+cl.primaryLayer.getName()+" INCLUDES LAYERS:");
//	for(Layer lay : cl.layers) System.out.print(" "+lay.getName());
//	System.out.println();
//	System.out.print("   AND ARCS:");
//	for(ArcProto ap : cl.arcs) System.out.print(" "+ap.describe());
//	System.out.println();
//}
//for(ConnectingVia cv : allConnectingVias)
//	System.out.println("VIA LAYER "+cv.viaLayer.getName() + " CONNECTS LAYERS "+cv.layer1+" AND "+cv.layer2);
//for(Layer lay : removeLayers.keySet())
//{
//	Layer otherLay = removeLayers.get(lay);
//	System.out.println("LAYER "+lay.getName()+" CUTS LAYER "+otherLay.getName());
//}

		return false;
	}

	/***************************** STRUCTURES *****************************/

	class ConnectingLayer
	{
		private Layer primaryLayer;
		private List<Layer> layers;
		private List<ArcProto> arcs;
		private GeometryTree tree;

		ConnectingLayer()
		{
			layers = new ArrayList<Layer>();
			arcs = new ArrayList<ArcProto>();
			tree = new GeometryTree(null);
		}
	}

	class ConnectingVia
	{
		private Layer viaLayer;
		private ConnectingLayer layer1, layer2;
		private GeometryTree tree;

		ConnectingVia()
		{
			tree = new GeometryTree(null);
		}
	}

	private static class GeometryTree
	{
		private RTNode<QCBound> root;

//		public static GeometryTree emptyTree = new GeometryTree(null);

		private GeometryTree(RTNode<QCBound> root) { this.root = root; }

		private RTNode<QCBound> getRoot() { return root; }

		private void setRoot(RTNode<QCBound> root) { this.root = root; }

		private boolean isEmpty() { return root == null; }

		private Iterator<QCBound> search(Rectangle2D searchArea)
		{
			if (root == null)
				return Collections.<QCBound>emptyList().iterator();
			Iterator<QCBound> it = new RTNode.Search<QCBound>(searchArea, root, true);
			return it;
		}
	}

	public static class QCNetID
	{
		private MutableInteger netID;
		private Geometric topGeom;

		QCNetID(MutableInteger netID, Geometric topGeom)
		{
			this.netID = netID;
			this.topGeom = topGeom;
		}

		/**
		 * Method to return the global network ID for this QCNetID.
		 * Numbers > 0 are normal network IDs.
		 * Numbers <= 0 are blockages added around the ends of routes.
		 * @return the global network ID for this QCNetID.
		 */
		public MutableInteger getNetID() { return netID; }

		/**
		 * Method to return the Geometric object associated with this QCNetID.
		 * @return the Geometric object associated with this QCNetID.
		 */
		public Geometric getTopGeom() { return topGeom; }

		/**
		 * Method to set the global network ID for this QCNetID.
		 * @param n the global network ID for this QCNetID.
		 */
		public void setNetID(MutableInteger n)
		{
			netID = n;
		}

		public void updateNetID(MutableInteger n, Map<Integer,List<MutableInteger>> netIDsByValue)
		{
			if (isSameBasicNet(n)) return;

			// update all MutableIntegers with the old value
			List<MutableInteger> oldNetIDs = netIDsByValue.get(Integer.valueOf(netID.intValue()));
			if (oldNetIDs == null) return;
			Integer netIDI = Integer.valueOf(n.intValue());
			List<MutableInteger> newNetIDs = netIDsByValue.get(netIDI);
			if (newNetIDs == null) netIDsByValue.put(netIDI, newNetIDs = new ArrayList<MutableInteger>());
			for(MutableInteger mi : oldNetIDs)
			{
				mi.setValue(n.intValue());
				newNetIDs.add(mi);
			}
			oldNetIDs.clear();
		}

		/**
		 * Method to tell whether this QCNetID is on a given network.
		 * Network numbers are encoded integers, where some values indicate
		 * variations on the type of network (for example, the area near routing points
		 * is marked with a "pseudo" blockage that keeps the area clear).
		 * @param otherNetID the network ID of the other net.
		 * @return true if this and the other net IDs are equivalent.
		 */
		public boolean isSameBasicNet(MutableInteger otherNetID)
		{
			int netValue = 0;
			if (netID != null) netValue = netID.intValue();
			if (netValue == otherNetID.intValue()) return true;
			return false;
		}
	}

	/**
	 * Class to define an R-Tree leaf node for geometry in the data structure.
	 */
	public static class QCBound extends QCNetID implements RTBounds
	{
		private ERectangle bound;

		QCBound(ERectangle bound, MutableInteger netID, Geometric topGeom)
		{
			super(netID, topGeom);
			this.bound = bound;
		}

		@Override
		public ERectangle getBounds() { return bound; }

		public boolean containsPoint(double x, double y)
		{
			return x >= bound.getMinX() && x <= bound.getMaxX() && y >= bound.getMinY() && y <= bound.getMaxY();
		}

		public boolean isManhattan() { return true; }

		@Override
		public String toString() { return "Geometry on net " + getNetID(); }
	}

	public static class QCPoly extends QCBound
	{
		private PolyBase poly;

		QCPoly(ERectangle bound, MutableInteger netID, PolyBase poly, Geometric topGeom)
		{
			super(bound, netID, topGeom);
			this.poly = poly;
		}

		public boolean containsPoint(double x, double y)
		{
			return poly.isInside(new Point2D.Double(x, y));
		}

		public boolean isManhattan()
		{
			Point[] pts = poly.getPoints();
			for(int i=1; i<pts.length; i++)
			{
				if (pts[i].getX() != pts[i-1].getX() && pts[i].getY() != pts[i-1].getY()) return false;
			}
			return true;
		}

		public PolyBase getPoly() { return poly; }
	}

	/**
	 * Class to define an R-Tree leaf node for vias in the geometric data structure.
	 */
	public static class QCVia extends QCBound
	{
		QCVia(ERectangle rect, MutableInteger netID)
		{
			super(rect, netID, null);
		}

		@Override
		public String toString() { return "Via on net " + getNetID(); }
	}

}
