/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: FDIrregular.java
 * Written by Steven M. Rubin
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
package com.sun.electric.tool.placement.general;

import com.sun.electric.database.EditingPreferences;
import com.sun.electric.database.ImmutableArcInst;
import com.sun.electric.database.geometry.EPoint;
import com.sun.electric.database.geometry.ERectangle;
import com.sun.electric.database.geometry.Poly;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.prototype.NodeProto;
import com.sun.electric.database.topology.ArcInst;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.topology.PortInst;
import com.sun.electric.database.topology.RTBounds;
import com.sun.electric.database.topology.RTNode;
import com.sun.electric.database.topology.SteinerTree.SteinerTreePortPair;
import com.sun.electric.database.variable.TextDescriptor;
import com.sun.electric.technology.technologies.Generic;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.JobException;
import com.sun.electric.tool.placement.PlacementAdapter;
import com.sun.electric.tool.placement.PlacementFrame;
import com.sun.electric.tool.placement.PlacementAdapter.PlacementExport;
import com.sun.electric.tool.simulation.test.TextUtils;
import com.sun.electric.tool.user.Highlighter;
import com.sun.electric.tool.user.User;
import com.sun.electric.tool.user.dialogs.EDialog;
import com.sun.electric.tool.user.ui.EditWindow;
import com.sun.electric.tool.user.ui.TopLevel;
import com.sun.electric.util.math.DBMath;
import com.sun.electric.util.math.GenMath;
import com.sun.electric.util.math.MutableDouble;
import com.sun.electric.util.math.Orientation;

import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.SwingUtilities;

/**
 * Placement algorithms that does irregular (not row/column) placement.
 *
 * TO-DO list:
 *   JUST DELETE THIS!!!!
 *   Sort multiple moves differently (possibly an order dependent on node clusters)
 *   compact after each move
 */
public class FDIrregular extends PlacementFrame
{
	/** list of proxy nodes */									protected List<ProxyNode> nodesToPlace;
	/** map from original PlacementNodes to proxy nodes */		protected Map<PlacementNode,ProxyNode> proxyMap;

	private final static boolean DEBUGSTATUS = true;
	private final static boolean DEBUGMODE = true;
	private final static boolean DEBUGPLOW = false;

//	protected PlacementParameter numThreadsParam = new PlacementParameter("threads",
//		"Number of threads:", 4);
//	protected PlacementParameter maxRuntimeParam = new PlacementParameter("runtime",
//		"Runtime (in seconds, 0 for no limit):", 240);
//	protected PlacementParameter canRotate = new PlacementParameter("canRotate",
//		"Allow node rotation", true);

	/**
	 * Method to return the name of this placement algorithm.
	 * @return the name of this placement algorithm.
	 */
	public String getAlgorithmName() { return "Force-directed-Irregular"; }

	/**
	 * Method to do placement by simulated annealing.
	 * @param placementNodes a list of all nodes that are to be placed.
	 * @param allNetworks a list of all networks that connect the nodes.
	 * @param cellName the name of the cell being placed.
	 */
	public void runPlacement(List<PlacementNode> placementNodes, List<PlacementNetwork> allNetworks,
		List<PlacementExport> exportsToPlace, String cellName, Job job)
	{
		setParamterValues(4, 240);

		// create proxies for placement nodes and insert in lists and maps
		nodesToPlace = new ArrayList<ProxyNode>(placementNodes.size());
		proxyMap = new HashMap<PlacementNode, ProxyNode>();
		for (PlacementNode p : placementNodes)
		{
			ProxyNode proxy = new ProxyNode(p);
			nodesToPlace.add(proxy);
			proxyMap.put(p, proxy);
		}

		// make sure initial placement is non-overlapping
		RTNode<PlaceBound> rTree = makeRTree();
		for(ProxyNode pNode : nodesToPlace)
		{
			for(ProxyNode pn : nodesToPlace) pn.clearProposed();
			rTree = plow(pNode, 0, 0, rTree, null);
			for(ProxyNode pn : nodesToPlace)
			{
				if (pn.isProposed()) pn.acceptProposed();
			}
		}

		runIrregularPlacement(placementNodes, allNetworks);

		// apply the placement of the proxies to the actual nodes
		for (PlacementNode pn : placementNodes)
		{
			ProxyNode p = proxyMap.get(pn);
			pn.setPlacement(p.getX(), p.getY());
			pn.setOrientation(p.getOrientation());
		}
	}

	private void runIrregularPlacement(List<PlacementNode> placementNodes, List<PlacementNetwork> allNetworks)
	{
		// convert network list to "connection" list
		List<SteinerTreePortPair> allConnections = new ArrayList<SteinerTreePortPair>();
		for(PlacementNetwork pNet : allNetworks)
		{
			List<SteinerTreePortPair> cons = PlacementAdapter.getOptimalConnections(pNet);
			for(SteinerTreePortPair con : cons) allConnections.add(con);
		}

		if (DEBUGMODE)
		{
			initializeDebugging(placementNodes, allConnections);
			return;
		}

		// non-debug FD placement
		for(;;)
		{
			double changed = placementStep(allConnections);
			if (DBMath.round(changed) <= 0) break;
			if (DEBUGSTATUS) System.out.println("  STEP IMPROVED BY "+TextUtils.formatDouble(changed));
		}
	}

	/**
	 * Method to make a change to the placement.
	 * @param allNetworks list of all networks being placed.
	 * @return the improvement in network metric for this step (-1 for no improvement).
	 */
	private double placementStep(List<SteinerTreePortPair> allConnections)
	{
		if (DEBUGSTATUS) System.out.println("+++ PLACEMENT STEP +++");
		ProxyPair importantMove = sortForces(allConnections);
		if (importantMove != null)
		{
			// force this move
			if (DEBUGSTATUS) System.out.println("  FORCING MOVE OF "+importantMove.nodeToMove+" BY ("+
				TextUtils.formatDouble(importantMove.nodeToMove.getForceX())+","+
				TextUtils.formatDouble(importantMove.nodeToMove.getForceY())+")");
			for(ProxyNode pn : nodesToPlace) pn.clearProposed();
			RTNode<PlaceBound> rTree = makeRTree();
			plow(importantMove.nodeToMove, importantMove.nodeToMove.getForceX(), importantMove.nodeToMove.getForceY(), rTree,
				importantMove.otherNode);

//			double smallestHPWL = adjustOrientation(importantMove, allConnections);

			for(ProxyNode pn : nodesToPlace)
			{
				if (pn.isProposed()) pn.acceptProposed();
			}
			return 1;
		}

		// get network size before change
		double networkMetricBefore = getCurrentHPWL(allConnections);

		// first try making multiple moves at once
		double networkMetricAfter = doForceDirectedMultiple(allConnections, networkMetricBefore);
		if (networkMetricAfter >= 0) return networkMetricBefore - networkMetricAfter;

		// cannot make multiple moves: see if any single move improves things
		networkMetricAfter = doForceDirected(allConnections, networkMetricBefore);
		if (networkMetricAfter >= 0) return networkMetricBefore - networkMetricAfter;

		// no moves work: try compacting empty space
		if (DEBUGSTATUS) System.out.println("  TRYING TO FILL EMPTY SPACE");
		fillEmptySpace();
		networkMetricAfter = doForceDirected(allConnections, networkMetricBefore);
		if (networkMetricAfter >= 0) return networkMetricBefore - networkMetricAfter;
		if (DEBUGSTATUS) System.out.println("  CANNOT FIND ANYTHING TO DO");

		return -1;
	}

	private static class ProxyPair
	{
		ProxyNode nodeToMove;
		ProxyNode otherNode;

		ProxyPair(ProxyNode me, ProxyNode you)
		{
			nodeToMove = me;
			otherNode = you;
		}
	}

	/**
	 * Method that computes the forces and sorts the node list by greatest force.
	 */
	private ProxyPair sortForces(List<SteinerTreePortPair> allConnections)
	{
		// clear the accumulated forces
		for(ProxyNode pNode : nodesToPlace) pNode.clearForceVector();

		// now look at every connection line and accumulate forces on the nodes
		for(SteinerTreePortPair pCon : allConnections)
		{
			PlacementPort p1 = (PlacementPort)pCon.getPort1();
			ProxyNode pn1 = proxyMap.get(p1.getPlacementNode());
			Orientation o1 = pn1.getOrientation();
			Point2D off1 = o1.transformPoint(new Point2D.Double(p1.getOffX(), p1.getOffY()));
			double x1 = pn1.getX() + off1.getX();
			double y1 = pn1.getY() + off1.getY();

			PlacementPort p2 = (PlacementPort)pCon.getPort2();
			ProxyNode pn2 = proxyMap.get(p2.getPlacementNode());
			Orientation o2 = pn2.getOrientation();
			Point2D off2 = o2.transformPoint(new Point2D.Double(p2.getOffX(), p2.getOffY()));
			double x2 = pn2.getX() + off2.getX();
			double y2 = pn2.getY() + off2.getY();

//System.out.println("FORCE LINE FROM "+pn1+" TO "+pn2+" GOES FROM ("+TextUtils.formatDouble(x1)+","+TextUtils.formatDouble(y1)+") TO ("+
//	TextUtils.formatDouble(x2)+","+TextUtils.formatDouble(y2)+")");
			// clip the lines to the bounds of the nodes
			Point2D from = new Point2D.Double(x1, y1);
			Point2D to = new Point2D.Double(x2, y2);
			double lX1 = pn1.getX() - pn1.getWidth()/2;
			double hX1 = pn1.getX() + pn1.getWidth()/2;
			double lY1 = pn1.getY() - pn1.getHeight()/2;
			double hY1 = pn1.getY() + pn1.getHeight()/2;
			GenMath.clipLine(to, from, lX1, hX1, lY1, hY1);
			x1 = to.getX();  y1 = to.getY();
			to.setLocation(x2, y2);

			double lX2 = pn2.getX() - pn2.getWidth()/2;
			double hX2 = pn2.getX() + pn2.getWidth()/2;
			double lY2 = pn2.getY() - pn2.getHeight()/2;
			double hY2 = pn2.getY() + pn2.getHeight()/2;
			GenMath.clipLine(from, to, lX2, hX2, lY2, hY2);
			x2 = from.getX();  y2 = from.getY();

			// if force is zero, see if a single X/Y force is appropriate
			if (x1 == x2 && y1 == y2)
			{
				if (lX1 == hX2 || lX2 == hX1)
				{
					// cells abut left/right: allow the full Y displacement
					y1 = pn1.getY() + off1.getY();
					y2 = pn2.getY() + off2.getY();
				} else if (lY1 == hY2 || lY2 == hY1)
				{
					// cells abut top/bottom: allow the full X displacement
					x1 = pn1.getX() + off1.getX();
					x2 = pn2.getX() + off2.getX();
				}
			}
//System.out.println("   CLIPS TO GO FROM ("+TextUtils.formatDouble(x1)+","+TextUtils.formatDouble(y1)+") TO ("+
//	TextUtils.formatDouble(x2)+","+TextUtils.formatDouble(y2)+")");

			pn1.accumulateForce(x2 - x1, y2 - y1, pn2);
			pn2.accumulateForce(x1 - x2, y1 - y2, pn1);
		}

		// normalize the force vectors
		ProxyNode bestImportantMove = null, bestOther = null;
		double bestDist = 0;
		for(ProxyNode node : nodesToPlace)
		{
//if (DEBUGSTATUS) System.out.println("FORCE ON NODE "+node+" IS ("+node.dX+"/"+node.numMoved+", "+node.dY+"/"+node.numMoved+")");
			ProxyNode other = node.normalizeForce();
			if (other != null)
			{
				double dist = Math.sqrt(node.getForceX()*node.getForceX() + node.getForceY()*node.getForceY());
				if (dist > bestDist)
				{
					bestDist = dist;
					bestImportantMove = node;
					bestOther = other;
				}
			}
		}

		if (bestImportantMove != null) return new ProxyPair(bestImportantMove, bestOther);

		// sort by strength of force
		Collections.sort(nodesToPlace, new ProxyMovement());
		return null;
	}

	private void fillEmptySpace()
	{
		RTNode<PlaceBound> rTree = makeRTree();
		for(ProxyNode node : nodesToPlace)
		{
			if (!node.hasForce()) continue;
			Point2D delta = new Point2D.Double(node.getForceX(), node.getForceY());

			greatestMove(node, delta, rTree);
			if (Math.abs(delta.getX()) < Math.abs(delta.getY()))
			{
				node.setForce(0, delta.getY());
			} else
			{
				node.setForce(delta.getX(), 0);
			}
		}

		// sort by strength of force
		Collections.sort(nodesToPlace, new ProxyMovement());
	}

	private void greatestMove(ProxyNode node, Point2D delta, RTNode<PlaceBound> rTree)
	{
		double dX = delta.getX();
		double dY = delta.getY();
		double nodeLX = node.getX() - node.getWidth()/2;
		double nodeLY = node.getY() - node.getHeight()/2;
		if (dX < 0)
		{
			// see how far to the left the node can move
			ERectangle search = ERectangle.fromLambda(nodeLX + dX, nodeLY, node.getWidth()-dX, node.getHeight());
			for (Iterator<PlaceBound> sea = new RTNode.Search<PlaceBound>(search, rTree, true); sea.hasNext();)
			{
				PlaceBound sBound = sea.next();
				if (sBound.pn == node) continue;
				if (sBound.bound.getMinY() >= search.getMaxY() || sBound.bound.getMaxY() <= search.getMinY()) continue;
				double amt = -(DBMath.round(nodeLX) - sBound.bound.getMaxX());
				if (amt > 0) amt = 0;
				if (amt > dX) dX = amt;
			}
		} else if (dX > 0)
		{
			// see how far to the right the node can move
			ERectangle search = ERectangle.fromLambda(nodeLX, nodeLY, node.getWidth()+dX, node.getHeight());
			for (Iterator<PlaceBound> sea = new RTNode.Search<PlaceBound>(search, rTree, true); sea.hasNext();)
			{
				PlaceBound sBound = sea.next();
				if (sBound.pn == node) continue;
				if (sBound.bound.getMinY() >= search.getMaxY() || sBound.bound.getMaxY() <= search.getMinY()) continue;
				double amt = sBound.bound.getMinX() - DBMath.round(nodeLX + node.getWidth());
				if (amt < 0) amt = 0;
				if (amt < dX) dX = amt;
			}
		}
		if (dY < 0)
		{
			// see how far down the node can move
			ERectangle search = ERectangle.fromLambda(nodeLX, nodeLY + dY, node.getWidth(), node.getHeight()-dY);
			for (Iterator<PlaceBound> sea = new RTNode.Search<PlaceBound>(search, rTree, true); sea.hasNext();)
			{
				PlaceBound sBound = sea.next();
				if (sBound.pn == node) continue;
				if (sBound.bound.getMinX() >= search.getMaxX() || sBound.bound.getMaxX() <= search.getMinX()) continue;
				double amt = -(DBMath.round(nodeLY) - sBound.bound.getMaxY());
				if (amt > 0) amt = 0;
				if (amt > dY) dY = amt;
			}
		} else if (dY > 0)
		{
			// see how far up the node can move
			ERectangle search = ERectangle.fromLambda(nodeLX, nodeLY, node.getWidth(), node.getHeight()+dY);
			for (Iterator<PlaceBound> sea = new RTNode.Search<PlaceBound>(search, rTree, true); sea.hasNext();)
			{
				PlaceBound sBound = sea.next();
				if (sBound.pn == node) continue;
				if (sBound.bound.getMinX() >= search.getMaxX() || sBound.bound.getMaxX() <= search.getMinX()) continue;
				double amt = sBound.bound.getMinY() - DBMath.round(nodeLY + node.getHeight());
				if (amt < 0) amt = 0;
				if (amt < dY) dY = amt;
			}
		}
		delta.setLocation(dX, dY);
	}

	private double doForceDirectedMultiple(List<SteinerTreePortPair> allConnections, double networkMetricBefore)
	{
		// determine the network length before the change
		for(ProxyNode pn : nodesToPlace) pn.saveOriginalConfiguration();
		RTNode<PlaceBound> rTree = makeRTree();
		int bestStep = -1;
		double bestGain = 0;
		for(int i=0; i<nodesToPlace.size(); i++)
		{
			// apply the force and make a move
			ProxyNode biggestMoveNode = nodesToPlace.get(i);
			for(ProxyNode pn : nodesToPlace) pn.clearProposed();

			// suggest the change
			rTree = plow(biggestMoveNode, biggestMoveNode.getForceX(), biggestMoveNode.getForceY(), rTree, null);

			// determine the network length after the change
			double smallestHPWL = adjustOrientation(biggestMoveNode, allConnections);

			// now adjust future moves by the amount that this change already did
			for(ProxyNode pn : nodesToPlace)
			{
				if (pn.isProposed())
				{
					pn.adjustForce(pn.getX() - pn.getProposedX(), pn.getY() - pn.getProposedY());
					pn.acceptProposed();
				}
			}

			// see if it is a good move
			double gain = networkMetricBefore - smallestHPWL;
			if (gain > bestGain)
			{
				bestStep = i;
				bestGain = gain;
			}
		}

		// restore original state
		for(ProxyNode pn : nodesToPlace) pn.restoreOriginalConfiguration();

		// if an improvement was made, do it
		if (bestStep > 0)
		{
//bestStep = Math.min(115,bestStep);		// works with 114, not with 115
			if (DEBUGSTATUS) System.out.println("  MAKING "+(bestStep+1)+" MULTIPLE MOVES");
			rTree = makeRTree();
			for(int i=0; i<=bestStep; i++)
			{
				// apply the force and make a move
				ProxyNode biggestMoveNode = nodesToPlace.get(i);
				for(ProxyNode pn : nodesToPlace) pn.clearProposed();

				// suggest the change
				rTree = plow(biggestMoveNode, biggestMoveNode.getForceX(), biggestMoveNode.getForceY(), rTree, null);

				// figure out whether to rotate or not
				adjustOrientation(biggestMoveNode, allConnections);

				// now adjust future moves by the amount that this change already did
				for(ProxyNode pn : nodesToPlace)
				{
					if (pn.isProposed())
					{
						pn.adjustForce(pn.getX() - pn.getProposedX(), pn.getY() - pn.getProposedY());
						pn.acceptProposed();
					}
				}
//placementMap = PlacementAdapter.getPlacementMap();
//plannedMoveNodeName = null;
//EditWindow wnd = EditWindow.getCurrent();
//Highlighter h = wnd.getHighlighter();
//h.clear();
//for(ProxyNode pn : nodesToPlace)
//{
//	NodeInst bmNI = backMap.get(pn).getOriginal();
//	NodeInst newNI = placementMap.get(bmNI.getName());
//	if (newNI == null) continue;
//	Rectangle2D oldArea = new Rectangle2D.Double(pn.getX()-pn.getWidth()/2, pn.getY()-pn.getHeight()/2, pn.getWidth(), pn.getHeight());
//	Poly oldPoly = new Poly(oldArea);
//	Rectangle2D newArea = new Rectangle2D.Double(pn.getProposedX()-pn.getProposedWidth()/2, pn.getProposedY()-pn.getProposedHeight()/2, pn.getProposedWidth(), pn.getProposedHeight());
//	Poly newPoly = new Poly(newArea);
//	h.addPoly(oldPoly, wnd.getCell(), Color.GREEN);
//	h.addPoly(newPoly, wnd.getCell(), pn.getOrientation() != pn.getProposedOrientation() ? Color.CYAN : Color.WHITE);
//	Point2D [] pts = new Point2D[2];
//	pts[0] = new Point2D.Double(oldArea.getCenterX(), oldArea.getCenterY());
//	pts[1] = new Point2D.Double(newArea.getCenterX(), newArea.getCenterY());
//	Poly polyL = new Poly(pts);
//	h.addPoly(polyL, wnd.getCell(), Color.RED);
//}
//h.finished();
if (DEBUGPLOW) System.out.println("++++++++++++++NODE "+biggestMoveNode+" MOVED+++++++++++++++++++");
//if (!Job.getUserInterface().confirmMessage("NODE "+biggestMoveNode+" MOVED")) break;
			}
			double networkMetricAfter = networkMetricBefore - bestGain;
			return networkMetricAfter;
		}
		if (DEBUGSTATUS) System.out.println("  CANNOT MAKE MULTIPLE MOVES");
		return -1;
	}

	private double doForceDirected(List<SteinerTreePortPair> allConnections, double networkMetricBefore)
	{
		for(ProxyNode biggestMoveNode : nodesToPlace)
		{
			// apply the force and make a move (if it really is good)
			for(ProxyNode pn : nodesToPlace) pn.clearProposed();

			// suggest the change
			RTNode<PlaceBound> rTree = makeRTree();
			rTree = plow(biggestMoveNode, biggestMoveNode.getForceX(), biggestMoveNode.getForceY(), rTree, null);

			// determine the network length after the change
			double smallestHPWL = adjustOrientation(biggestMoveNode, allConnections);

			// see if it is a good move
			double gain = networkMetricBefore - smallestHPWL;
			if (gain > 0)
			{
				// move makes a good change: do it
				for(ProxyNode pn : nodesToPlace)
				{
					if (pn.isProposed())
						pn.acceptProposed();
				}
				if (DEBUGSTATUS) System.out.println("  MAKING SINGLE MOVE OF NODE "+biggestMoveNode);
				return smallestHPWL;
			}
		}
		if (DEBUGSTATUS) System.out.println("  CANNOT MAKE A SINGLE MOVE");
		return -1;
	}

	private double adjustOrientation(ProxyNode biggestMoveNode, List<SteinerTreePortPair> allConnections)
	{
		// determine the network length after the change
		MutableDouble networkMetricAfter = new MutableDouble(0);
		MutableDouble networkMetricAfterR180 = new MutableDouble(0);
		MutableDouble networkMetricAfterFx = new MutableDouble(0);
		MutableDouble networkMetricAfterFy = new MutableDouble(0);
		for(SteinerTreePortPair con : allConnections)
		{
			netLength(con, biggestMoveNode, networkMetricAfter, networkMetricAfterR180, networkMetricAfterFx, networkMetricAfterFy);
		}
		double smallestHPWL = networkMetricAfter.doubleValue();
//		if (canRotate.getBooleanValue())
		{
			double unrotHPWL = smallestHPWL;
			smallestHPWL = Math.min(Math.min(smallestHPWL, networkMetricAfterR180.doubleValue()),
				Math.min(networkMetricAfterFx.doubleValue(), networkMetricAfterFy.doubleValue()));
			if (unrotHPWL != smallestHPWL)
			{
				if (networkMetricAfterR180.doubleValue() == smallestHPWL)
				{
					biggestMoveNode.setProposedOrientationChange(Orientation.RR);
				} else if (networkMetricAfterFx.doubleValue() == smallestHPWL)
				{
					biggestMoveNode.setProposedOrientationChange(Orientation.X);
				} else if (networkMetricAfterFy.doubleValue() == smallestHPWL)
				{
					biggestMoveNode.setProposedOrientationChange(Orientation.Y);
				}
			}
		}
		return smallestHPWL;
	}

	private RTNode<PlaceBound> makeRTree()
	{
		RTNode<PlaceBound> root = RTNode.makeTopLevel();
		for(ProxyNode pNode : nodesToPlace)
		{
			ERectangle bounds = ERectangle.fromLambda(pNode.getX() - pNode.getWidth()/2, pNode.getY() - pNode.getHeight()/2, pNode.getWidth(), pNode.getHeight());
			pNode.setRTNode(bounds);
			root = RTNode.linkGeom(null, root, pNode.getRTNode());
		}
		return root;
	}

	private RTNode<PlaceBound> plow(ProxyNode pNode, double dX, double dY, RTNode<PlaceBound> rTree, ProxyNode fixed)
	{
		// propose the node move
		double prevX = pNode.getProposedX(), prevY = pNode.getProposedY();
		if (!pNode.isProposed())
			pNode.setProposedOrientationChange(Orientation.IDENT);
		pNode.setProposed(pNode.getProposedX() + dX, pNode.getProposedY() + dY);
		if (DEBUGPLOW) System.out.println("PLOWING NODE "+pNode+" BY ("+dX+","+dY+")");

		// move the node in the R-Tree
		rTree = RTNode.unLinkGeom(null, rTree, pNode.getRTNode());
		ERectangle pBound = ERectangle.fromLambda(pNode.getProposedX() - pNode.getWidth()/2,
			pNode.getProposedY() - pNode.getHeight()/2, pNode.getWidth(), pNode.getHeight());
		pNode.setRTNode(pBound);
		rTree = RTNode.linkGeom(null, rTree, pNode.getRTNode());

		ERectangle search = ERectangle.fromLambda(Math.min(prevX, pNode.getProposedX()) - pNode.getWidth()/2,
			Math.min(prevY, pNode.getProposedY()) - pNode.getHeight()/2,
			pNode.getWidth() + Math.abs(dX), pNode.getHeight() + Math.abs(dY));
		boolean blocked = true;
		while (blocked)
		{
			blocked = false;

			// recompute the node bounds because it might have changed
			pBound = ERectangle.fromLambda(pNode.getProposedX() - pNode.getWidth()/2,
				pNode.getProposedY() - pNode.getHeight()/2, pNode.getWidth(), pNode.getHeight());

			// look for an intersecting node
			for (Iterator<PlaceBound> sea = new RTNode.Search<PlaceBound>(search, rTree, true); sea.hasNext();)
			{
				PlaceBound sBound = sea.next();
				ProxyNode inArea = sBound.pn;
				if (inArea == pNode) continue;
				if (pBound.getMinX() >= sBound.bound.getMaxX() ||
					pBound.getMaxX() <= sBound.bound.getMinX() ||
					pBound.getMinY() >= sBound.bound.getMaxY() ||
					pBound.getMaxY() <= sBound.bound.getMinY()) continue;

				// figure out which way to move the blocking node
				double leftMotion = sBound.bound.getMaxX() - pBound.getMinX();
				if (blocksFixed(pNode, -leftMotion, 0, fixed)) leftMotion = Double.MAX_VALUE;
				double rightMotion = pBound.getMaxX() - sBound.bound.getMinX();
				if (blocksFixed(pNode, rightMotion, 0, fixed)) rightMotion = Double.MAX_VALUE;
				double downMotion = sBound.bound.getMaxY() - pBound.getMinY();
				if (blocksFixed(pNode, 0, -downMotion, fixed)) downMotion = Double.MAX_VALUE;
				double upMotion = pBound.getMaxY() - sBound.bound.getMinY();
				if (blocksFixed(pNode, 0, upMotion, fixed)) upMotion = Double.MAX_VALUE;
				double leastMotion = Math.min(Math.min(leftMotion, rightMotion), Math.min(upMotion, downMotion));
				if (leftMotion == leastMotion)
				{
					// move the other block left to keep it away
					if (DEBUGPLOW) System.out.println("  MOVE "+inArea+" "+leftMotion+" LEFT (Because its right edge is "+
						sBound.bound.getMaxX()+" and "+pNode+" left edge is "+pBound.getMinX()+")");
					rTree = plow(inArea, -leftMotion, 0, rTree, null);
				} else if (rightMotion == leastMotion)
				{
					// move the other block right to keep it away
					if (DEBUGPLOW) System.out.println("  MOVE "+inArea+" "+rightMotion+" RIGHT (Because its left edge is "+
						sBound.bound.getMinX()+" and "+pNode+" right edge is "+pBound.getMaxX()+")");
					rTree = plow(inArea, rightMotion, 0, rTree, null);
				} else if (upMotion == leastMotion)
				{
					// move the other block up to keep it away
					if (DEBUGPLOW) System.out.println("  MOVE "+inArea+" "+upMotion+" UP (Because its bottom edge is "+
						sBound.bound.getMinY()+" and "+pNode+" top edge is "+pBound.getMaxY()+")");
					rTree = plow(inArea, 0, upMotion, rTree, null);
				} else if (downMotion == leastMotion)
				{
					// move the other block down to keep it away
					if (DEBUGPLOW) System.out.println("  MOVE "+inArea+" "+downMotion+" DOWN (Because its top edge is "+
						sBound.bound.getMaxY()+" and "+pNode+" bottom edge is "+pBound.getMinY()+")");
					rTree = plow(inArea, 0, -downMotion, rTree, null);
				}
				blocked = true;
				break;
			}
		}
		return rTree;
	}

	/**
	 * Method to determine whether the motion of a ProxyNode intersects another.
	 * @param pn the ProxyNode being moved.
	 * @param dX the change in its X position.
	 * @param dY the change in its Y position.
	 * @param fixed the other ProxyNode being tested for intersection.
	 * @return true if the change makes the nodes intersect.
	 */
	private boolean blocksFixed(ProxyNode pn, double dX, double dY, ProxyNode fixed)
	{
		if (fixed == null) return false;
		double pnLX = pn.getProposedX() + dX - pn.getProposedWidth()/2;
		double pnHX = pn.getProposedX() + dX + pn.getProposedWidth()/2;
		double pnLY = pn.getProposedY() + dY - pn.getProposedHeight()/2;
		double pnHY = pn.getProposedY() + dY + pn.getProposedHeight()/2;
		double fixLX = fixed.getProposedX() - fixed.getProposedWidth()/2;
		double fixHX = fixed.getProposedX() + fixed.getProposedWidth()/2;
		double fixLY = fixed.getProposedY() - fixed.getProposedHeight()/2;
		double fixHY = fixed.getProposedY() + fixed.getProposedHeight()/2;
		if (pnLX >= fixHX || pnHX <= fixLX || pnLY >= fixHY || pnHY <= fixLY) return false;
		return true;
	}

	private double getCurrentHPWL(List<SteinerTreePortPair> allConnections)
	{
		double hpwl = 0;
		for(SteinerTreePortPair con : allConnections)
		{
			PlacementPort p1 = (PlacementPort)con.getPort1();
			ProxyNode pn1 = proxyMap.get(p1.getPlacementNode());
			Point2D off1 = pn1.getOrientation().transformPoint(new Point2D.Double(p1.getOffX(), p1.getOffY()));
			double x1 = pn1.getX() + off1.getX(), y1 = pn1.getY() + off1.getY();

			PlacementPort p2 = (PlacementPort)con.getPort2();
			ProxyNode pn2 = proxyMap.get(p2.getPlacementNode());
			Point2D off2 = pn2.getOrientation().transformPoint(new Point2D.Double(p2.getOffX(), p2.getOffY()));
			double x2 = pn2.getX() + off2.getX(), y2 = pn2.getY() + off2.getY();

			hpwl += Math.sqrt((x1-x2)*(x1-x2) + (y1-y2)*(y1-y2));
		}
		return hpwl;
	}

	/**
	 * Method that calculates the bounding box net length approximation for a given net.
	 * It hashes the nodes of the ports in the nets to its proxies.
	 * Also, it may substitute a node with another one just for this calculation
	 * @param net the PlacementNetwork to analyze.
	 * @return the length of the connections on the network.
	 */
	private void netLength(SteinerTreePortPair con, ProxyNode current,
		MutableDouble unrot, MutableDouble rot180, MutableDouble flipX, MutableDouble flipY)
	{
		PlacementPort p1 = (PlacementPort)con.getPort1();
		ProxyNode pn1 = proxyMap.get(p1.getPlacementNode());
		double x1 = pn1.getProposedX(), y1 = pn1.getProposedY();
		Point2D pureOffset1 = new Point2D.Double(p1.getOffX(), p1.getOffY());
		Orientation o1 = pn1.getProposedOrientation();

		PlacementPort p2 = (PlacementPort)con.getPort2();
		ProxyNode pn2 = proxyMap.get(p2.getPlacementNode());
		double x2 = pn2.getProposedX(), y2 = pn2.getProposedY();
		Point2D pureOffset2 = new Point2D.Double(p2.getOffX(), p2.getOffY());
		Orientation o2 = pn2.getProposedOrientation();

		// handle unaltered rotation
		Point2D off1 = o1.transformPoint(pureOffset1);
		double x1U = x1 + off1.getX(), y1U = y1 + off1.getY();
		Point2D off2 = o2.transformPoint(pureOffset2);
		double x2U = x2 + off2.getX(), y2U = y2 + off2.getY();

		double dist = Math.sqrt((x1U-x2U)*(x1U-x2U) + (y1U-y2U)*(y1U-y2U));
		unrot.setValue(unrot.doubleValue() + dist);

		if (pn1 == current)
		{
			Orientation o180 = rot180(o1);
			Point2D off180 = o180.transformPoint(pureOffset1);
			double x1R180 = x1 + off180.getX(), y1R180 = y1 + off180.getY();
			double dist180 = Math.sqrt((x1R180-x2U)*(x1R180-x2U) + (y1R180-y2U)*(y1R180-y2U));
			rot180.setValue(rot180.doubleValue() + dist180);

			Orientation oFx = flipX(o1);
			Point2D offFx = oFx.transformPoint(pureOffset1);
			double x1Fx = x1 + offFx.getX(), y1Fx = y1 + offFx.getY();
			double distFx = Math.sqrt((x1Fx-x2U)*(x1Fx-x2U) + (y1Fx-y2U)*(y1Fx-y2U));
			flipX.setValue(flipX.doubleValue() + distFx);

			Orientation oFy = flipY(o1);
			Point2D offFy = oFy.transformPoint(pureOffset1);
			double x1Fy = x1 + offFy.getX(), y1Fy = y1 + offFy.getY();
			double distFy = Math.sqrt((x1Fy-x2U)*(x1Fy-x2U) + (y1Fy-y2U)*(y1Fy-y2U));
			flipY.setValue(flipY.doubleValue() + distFy);
		} else if (pn2 == current)
		{
			Orientation o180 = rot180(o2);
			Point2D off180 = o180.transformPoint(pureOffset2);
			double x2R180 = x2 + off180.getX(), y2R180 = y2 + off180.getY();
			double dist180 = Math.sqrt((x2R180-x1U)*(x2R180-x1U) + (y2R180-y1U)*(y2R180-y1U));
			rot180.setValue(rot180.doubleValue() + dist180);

			Orientation oFx = flipX(o2);
			Point2D offFx = oFx.transformPoint(pureOffset2);
			double x2Fx = x2 + offFx.getX(), y2Fx = y2 + offFx.getY();
			double distFx = Math.sqrt((x2Fx-x1U)*(x2Fx-x1U) + (y2Fx-y1U)*(y2Fx-y1U));
			flipX.setValue(flipX.doubleValue() + distFx);

			Orientation oFy = flipY(o2);
			Point2D offFy = oFy.transformPoint(pureOffset2);
			double x2Fy = x2 + offFy.getX(), y2Fy = y2 + offFy.getY();
			double distFy = Math.sqrt((x2Fy-x1U)*(x2Fy-x1U) + (y2Fy-y1U)*(y2Fy-y1U));
			flipY.setValue(flipY.doubleValue() + distFy);
		} else
		{
			rot180.setValue(unrot.doubleValue() + dist);
			flipX.setValue(unrot.doubleValue() + dist);
			flipY.setValue(unrot.doubleValue() + dist);
		}
	}

	private Orientation rot180(Orientation o)
	{
		return Orientation.RR.concatenate(o);
	}

	private Orientation flipX(Orientation o)
	{
		return Orientation.X.concatenate(o);
	}

	private Orientation flipY(Orientation o)
	{
		return Orientation.Y.concatenate(o);
	}

	private class ProxyMovement implements Comparator<ProxyNode>
	{
		public int compare(ProxyNode c1, ProxyNode c2)
		{
			double x1 = c1.getForceX();
			double y1 = c1.getForceY();
			double x2 = c2.getForceX();
			double y2 = c2.getForceY();
			double r1 = Math.sqrt(x1*x1 + y1*y1);
			double r2 = Math.sqrt(x2*x2 + y2*y2);
			if (r1 == r2) return 0;
			if (r1 < r2) return 1;
			return -1;
		}
	}

	/**
	 * Class to define an R-Tree leaf node for geometry in the placement data structure.
	 */
	private static class PlaceBound implements RTBounds
	{
		private ERectangle bound;
		private ProxyNode pn;

		PlaceBound(ERectangle bound, ProxyNode pn)
		{
			this.bound = bound;
			this.pn = pn;
		}

        @Override
		public ERectangle getBounds() { return bound; }

        @Override
		public String toString() { return "Node " + pn; }
	}

	/*************************************** Proxy Node **********************************/

	/**
	 * This class is a proxy for the actual PlacementNode.
	 * It can be moved around and rotated without touching the actual PlacementNode.
	 */
	class ProxyNode
	{
		// current state
		private double x, y;						// current location
		private Orientation orientation;			// orientation of the placement node
		private double width, height;				// height of the placement node

		// proposed state
		private boolean moved;						// true if a proposed state has been made
		private double newX, newY;					// proposed location when testing rearrangement
		private Orientation newOrientation;			// proposed orientation
		private Orientation deltaOrientation;		// proposed change of orientation
		private double newWidth, newHeight;			// proposed node size (if it was rotated)

		// preserved state to restore multiple changes
		private double origX, origY;				// original location before multiple changes
		private Orientation origOrientation;		// original orientation before multiple changes
		private double origWidth, origHeight;		// original node size before multiple changes
		private double origDX, origDY;				// original forces on the node after force sorting

		// computation of forces on the node
		private double dX, dY;						// forces on the node
		private int numMoved;						// number of forces on the node
		private double[] dXQ, dYQ;
		private int[] numMovedQ;
		private ProxyNode[] otherQ;

		private PlacementNode original;				// original Placement node that this is shadowing
		private PlaceBound rtNode;

		/**
		 * Constructor to create a ProxyNode
		 * @param node the PlacementNode that should is being shadowed.
		 */
		public ProxyNode(PlacementNode node)
		{
			original = node;
			NodeInst ni = ((PlacementAdapter.PlacementNode)node).getOriginal();

			x = DBMath.round(ni.getTrueCenterX());
			y = DBMath.round(ni.getTrueCenterY());
			orientation = ni.getOrient();

			NodeProto np = ((PlacementAdapter.PlacementNode)node).getType();
			Rectangle2D spacing = null;
			if (np instanceof Cell)
				spacing = ((Cell)np).findEssentialBounds();

			if (spacing == null)
			{
				width = node.getWidth();
				height = node.getHeight();
			} else
			{
				width = spacing.getWidth();
				height = spacing.getHeight();
			}
			if (ni.getOrient().getAngle() == 900 || ni.getOrient().getAngle() == 2700)
			{
				double swap = width;   width = height;   height = swap;
			}
			dXQ = new double[8];
			dYQ = new double[8];
			numMovedQ = new int[8];
			otherQ = new ProxyNode[8];
		}

		public String toString()
		{
			NodeInst ni = ((PlacementAdapter.PlacementNode)original).getOriginal();
			return ni.describe(false);
		}

		public String getNodeName()
		{
			NodeInst ni = ((PlacementAdapter.PlacementNode)original).getOriginal();
			return ni.getName();
		}

		// ----------------------------- FORCES -----------------------------

		public void clearForceVector()
		{
			dX = dY = 0;
			numMoved = 0;
			for(int i=0; i<8; i++)
			{
				dXQ[i] = dYQ[i] = 0;
				numMovedQ[i] = 0;
				otherQ[i] = null;
			}
		}

		public void accumulateForce(double addX, double addY, ProxyNode other)
		{
			dX += addX;   dY += addY;   numMoved++;

			int quadrant = -1;
			if (addX == 0)
			{
				if (addY > 0) quadrant = 2; else
					if (addY < 0) quadrant = 6;
			} else if (addX > 0)
			{
				if (addY == 0) quadrant = 0; else
					if (addY > 0) quadrant = 1; else
						quadrant = 7;
			} else
			{
				if (addY == 0) quadrant = 4; else
					if (addY > 0) quadrant = 3; else
						quadrant = 5;
			}
			if (quadrant >= 0)
			{
				dXQ[quadrant] += addX;
				dYQ[quadrant] += addY;
				if (numMovedQ[quadrant] == 0) otherQ[quadrant] = other; else
				{
					if (otherQ[quadrant] != other) otherQ[quadrant] = null;
				}
				numMovedQ[quadrant]++;
			}
		}

		public void adjustForce(double offX, double offY)
		{
			dX += offX;
			dY += offY;
		}

		public void setForce(double x, double y)
		{
			dX = x;
			dY = y;
		}

		public ProxyNode normalizeForce()
		{
			if (numMoved != 0)
			{
				dX /= numMoved;
				dY /= numMoved;
			}

			// a significant move if it connects by just one link
			if (numMoved == 1 && (dX != 0 || dY != 0))
			{
				for(int i=0; i<8; i++) if (otherQ[i] != null)
					return otherQ[i];
			}

			int biggestQuadrant = -1;
			int numInBiggestQuadrant = 0;
			for(int i=0; i<8; i++)
			{
				if (otherQ[i] != null && numMovedQ[i] > numInBiggestQuadrant)
				{
					numInBiggestQuadrant = numMovedQ[i];
					biggestQuadrant = i;
				}
				if (numMovedQ[i] > 0)
				{
					dXQ[i] /= numMovedQ[i];
					dYQ[i] /= numMovedQ[i];
				}
			}
			if (biggestQuadrant >= 0)
			{
				if (numInBiggestQuadrant*2 > numMoved)
				{
					dX = dXQ[biggestQuadrant];
					dY = dYQ[biggestQuadrant];
					return otherQ[biggestQuadrant];
				}
			}
			return null;
		}

		public boolean hasForce() { return numMoved != 0; }

		public double getForceX() { return DBMath.round(dX); }

		public double getForceY() { return DBMath.round(dY); }

		// ----------------------------- COORDINATES -----------------------------

		/**
		 * Method to get the X-coordinate of this ProxyNode.
		 * @return the X-coordinate of this ProxyNode.
		 */
		public double getX() { return x; }

		/**
		 * Method to get the Y-coordinate of this ProxyNode.
		 * @return the Y-coordinate of this ProxyNode.
		 */
		public double getY() { return y; }

		/**
		 * Method to get the width of this ProxyNode.
		 * @return the width of this ProxyNode.
		 */
		public double getWidth() { return width; }

		/**
		 * Method to get the height of this ProxyNode.
		 * @return the height of this ProxyNode.
		 */
		public double getHeight() { return height; }

		/**
		 * Method to get the orientation of this ProxyNode.
		 * @return the orientation of this ProxyNode.
		 */
		public Orientation getOrientation() { return orientation; }

		public void setRTNode(ERectangle bounds)
		{
			rtNode = new PlaceBound(bounds, this);
		}

		public PlaceBound getRTNode() { return rtNode; }

		// ----------------------------- PROPOSED COORDINATE CHANGES -----------------------------

		public void clearProposed() { moved = false; }

		public boolean isProposed() { return moved; }

		public void setProposed(double x, double y)
		{
			newX = DBMath.round(x);
			newY = DBMath.round(y);
			moved = true;
		}

		public void setProposedOrientationChange(Orientation delta)
		{
			deltaOrientation = delta;
			newOrientation = delta.concatenate(orientation);
			int deltaAng = Math.abs(orientation.getAngle() - newOrientation.getAngle());
			if (deltaAng == 900 || deltaAng == 2700)
			{
				newWidth = height;
				newHeight = width;
			} else
			{
				newWidth = width;
				newHeight = height;
			}
		}

		/**
		 * Method to get the proposed X-coordinate of this ProxyNode.
		 * @return the proposed X-coordinate of this ProxyNode.
		 */
		public double getProposedX()
		{
			if (moved) return newX;
			return x;
		}

		/**
		 * Method to get the proposed Y-coordinate of this ProxyNode.
		 * @return the proposed Y-coordinate of this ProxyNode.
		 */
		public double getProposedY()
		{
			if (moved) return newY;
			return y;
		}

		/**
		 * Method to get the proposed orientation of this ProxyNode.
		 * @return the proposed orientation of this ProxyNode.
		 */
		public Orientation getProposedOrientation()
		{
			if (moved) return newOrientation;
			return orientation;
		}

		/**
		 * Method to get the proposed orientation of this ProxyNode.
		 * @return the proposed orientation of this ProxyNode.
		 */
		public Orientation getProposedOrientationChange()
		{
			if (moved) return deltaOrientation;
			return Orientation.IDENT;
		}

		/**
		 * Method to get the width of this ProxyNode.
		 * @return the width of this ProxyNode.
		 */
		public double getProposedWidth()
		{
			if (moved) return newWidth;
			return width;
		}

		/**
		 * Method to get the height of this ProxyNode.
		 * @return the height of this ProxyNode.
		 */
		public double getProposedHeight()
		{
			if (moved) return newHeight;
			return height;
		}

		/**
		 * Method that sets the node to the proposed position.
		 */
		public void acceptProposed()
		{
			x = newX;
			y = newY;
			orientation = newOrientation;
			width = newWidth;
			height = newHeight;
			moved = false;
		}

		// ----------------------------- STATE BACKUP -----------------------------

		public void saveOriginalConfiguration()
		{
			origX = x;            origY = y;
			origOrientation = orientation;
			origWidth = width;    origHeight = height;
			origDX = dX;          origDY = dY;
		}

		public void restoreOriginalConfiguration()
		{
			x = origX;            y = origY;
			orientation = origOrientation;
			width = origWidth;    height = origHeight;
			dX = origDX;          dY = origDY;
		}
	}

	/* ********************************************* DEBUGGING ********************************************* */

	private static List<PlacementNode> debugPlacementNodes;
	private static List<SteinerTreePortPair> debugAllConnections;
	private static Map<String, NodeInst> placementMap;
	private static String plannedMoveNodeName;
	private static Map<ProxyNode,PlacementAdapter.PlacementNode> backMap;

	private void initializeDebugging(List<PlacementNode> placementNodes, List<SteinerTreePortPair> allConnections)
	{
		debugPlacementNodes = placementNodes;
		debugAllConnections = allConnections;

		backMap = new HashMap<ProxyNode,PlacementAdapter.PlacementNode>();
		for (PlacementNode pn : placementNodes)
		{
			PlacementAdapter.PlacementNode pnReal = (PlacementAdapter.PlacementNode)pn;
			backMap.put(proxyMap.get(pn), pnReal);
		}

		SwingUtilities.invokeLater(new Runnable() {
			public void run() { new PlacementProgress(); }
		});
	}

	/**
	 * Class to debug placement.
	 */
	public class PlacementProgress extends EDialog
	{
		private JButton theBut;
		private boolean planned;

		public PlacementProgress()
		{
			super(TopLevel.getCurrentJFrame(), false);

			GridBagConstraints gridBagConstraints;
			getContentPane().setLayout(new GridBagLayout());
			setTitle("Debug Placement");
			setName("");
			addWindowListener(new WindowAdapter()
			{
				public void windowClosing(WindowEvent evt) { closeDialog(evt); }
			});

			theBut = new JButton("Plan Move");
			theBut.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent evt) { planMove(); }
			});
			gridBagConstraints = new GridBagConstraints();
			gridBagConstraints.gridx = 0;
			gridBagConstraints.gridy = 0;
			gridBagConstraints.insets = new Insets(4, 4, 4, 4);
			getContentPane().add(theBut, gridBagConstraints);

			pack();
			finishInitialization();
			setVisible(true);
			planned = false;
		}

		protected void escapePressed() { closeDialog(null); }

		private void planMove()
		{
			if (planned)
			{
				theBut.setText("Plan Move");
				for (PlacementNode pn : debugPlacementNodes)
				{
					ProxyNode p = proxyMap.get(pn);
					pn.setPlacement(p.getX(), p.getY());
					pn.setOrientation(p.getOrientation());
				}
				new MakeIntermediateMove();
				planned = false;
				return;
			}

			placementMap = PlacementAdapter.getPlacementMap();
			theBut.setText("Do Move");
			planned = true;
			double changed = placementStep(debugAllConnections);
			if (DBMath.round(changed) <= 0) return;
			if (DEBUGSTATUS) System.out.println("  STEP IMPROVED BY "+TextUtils.formatDouble(changed));

			plannedMoveNodeName = null;
			EditWindow wnd = EditWindow.getCurrent();
			Highlighter h = wnd.getHighlighter();
			h.clear();
			for(ProxyNode pn : nodesToPlace)
			{
				NodeInst bmNI = backMap.get(pn).getOriginal();
				NodeInst newNI = placementMap.get(bmNI.getName());
				if (newNI == null) continue;
				Rectangle2D area = newNI.getBounds();
				Poly poly = new Poly(area);
				h.addPoly(poly, wnd.getCell(), Color.GREEN);
				Poly polyL = new Poly(Poly.fromLambda(area.getCenterX(), area.getCenterY()), Poly.fromLambda(pn.x, pn.y));
				h.addPoly(polyL, wnd.getCell(), Color.CYAN);
			}
			h.finished();
		}

		/** Closes the dialog */
		private void closeDialog(WindowEvent evt)
		{
			setVisible(false);
			dispose();
		}
	}

	private static class MakeIntermediateMove extends Job
	{
		private MakeIntermediateMove()
		{
			super("Place cells", User.getUserTool(), Job.Type.CHANGE, null, null, Job.Priority.USER);
			startJob();
		}

		public boolean doIt() throws JobException
		{
            EditingPreferences ep = getEditingPreferences();
			Cell cell = null;
			for(PlacementNode plNode : debugPlacementNodes)
			{
				PlacementAdapter.PlacementNode pan = (PlacementAdapter.PlacementNode)plNode;
				NodeInst bmNI = pan.getOriginal();
				if (bmNI == null) continue;
				NodeInst newNI = placementMap.get(bmNI.getName());
				if (newNI == null) continue;

				double xPos = plNode.getPlacementX();
				double yPos = plNode.getPlacementY();
				Orientation orient = plNode.getPlacementOrientation();
				if (pan.getOriginal().isCellInstance())
				{
					Cell placementCell = (Cell)pan.getOriginal().getProto();
					Rectangle2D bounds = placementCell.getBounds();
					Point2D centerOffset = new Point2D.Double(bounds.getCenterX(), bounds.getCenterY());
					orient.pureRotate().transform(centerOffset, centerOffset);
					xPos -= centerOffset.getX();
					yPos -= centerOffset.getY();
				}
				if (newNI.getAnchorCenterX() == xPos && newNI.getAnchorCenterY() == yPos)
					continue;

				double dX = xPos - newNI.getAnchorCenterX();
				double dY = yPos - newNI.getAnchorCenterY();
				boolean flipX = newNI.getOrient().isXMirrored() != plNode.getPlacementOrientation().isXMirrored();
				boolean flipY = newNI.getOrient().isYMirrored() != plNode.getPlacementOrientation().isYMirrored();
				int deltaAngle = plNode.getPlacementOrientation().getAngle() - newNI.getOrient().getAngle();
				Orientation deltaO = Orientation.fromJava(deltaAngle, flipX, flipY);
				newNI.modifyInstance(dX, dY, 0, 0, deltaO);
				cell = newNI.getParent();
			}

			if (cell != null)
			{
				// rip out all arcs
				List<ArcInst> allArcs = new ArrayList<ArcInst>();
				for(Iterator<ArcInst> it = cell.getArcs(); it.hasNext(); ) allArcs.add(it.next());
				for(ArcInst ai : allArcs) ai.kill();

				// redo arcs in optimal way
				ImmutableArcInst a = Generic.tech().unrouted_arc.getDefaultInst(ep);
				long gridExtend = a.getGridExtendOverMin();
				for(SteinerTreePortPair pc : debugAllConnections)
				{
					PlacementPort thisPp1 = (PlacementPort)pc.getPort1();
					PlacementNode plNode1 = thisPp1.getPlacementNode();
					PlacementAdapter.PlacementNode pan1 = (PlacementAdapter.PlacementNode)plNode1;
					NodeInst bmNI1 = pan1.getOriginal();
					NodeInst newNi1 = placementMap.get(bmNI1.getName());
					PlacementAdapter.PlacementPort pp1 = (PlacementAdapter.PlacementPort)thisPp1;
					PortInst thisPi1 = newNi1.findPortInstFromEquivalentProto(pp1.getPortProto());
					EPoint pt1 = thisPi1.getCenter();

					PlacementPort thisPp2 = (PlacementPort)pc.getPort2();
					PlacementNode plNode2 = thisPp2.getPlacementNode();
					PlacementAdapter.PlacementNode pan2 = (PlacementAdapter.PlacementNode)plNode2;
					NodeInst bmNI2 = pan2.getOriginal();
					NodeInst newNi2 = placementMap.get(bmNI2.getName());
					PlacementAdapter.PlacementPort pp2 = (PlacementAdapter.PlacementPort)thisPp2;
					PortInst thisPi2 = newNi2.findPortInstFromEquivalentProto(pp2.getPortProto());
					EPoint pt2 = thisPi2.getCenter();

                    TextDescriptor td = ep.getArcTextDescriptor();
					ArcInst.newInstanceNoCheck(cell, Generic.tech().unrouted_arc, null, td, thisPi1, thisPi2,
						pt1, pt2, gridExtend, ArcInst.DEFAULTANGLE, a.flags);
				}
			}
			return true;
		}

		public void terminateOK()
		{
			NodeInst newNI = placementMap.get(plannedMoveNodeName);
			EditWindow wnd = EditWindow.getCurrent();
			Highlighter h = wnd.getHighlighter();
			h.clear();
			if (newNI != null)
			{
				Rectangle2D area = newNI.getBounds();
				h.addArea(area, wnd.getCell());
			}
			h.finished();
		}
	}
}
