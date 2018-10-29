/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: BottomUpPlace.java
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

import com.sun.electric.database.geometry.EGraphics;
import com.sun.electric.database.geometry.EPoint;
import com.sun.electric.database.geometry.ERectangle;
import com.sun.electric.database.geometry.Poly;
import com.sun.electric.database.geometry.PolyBase;
import com.sun.electric.database.geometry.PolyMerge;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.Library;
import com.sun.electric.database.prototype.NodeProto;
import com.sun.electric.database.topology.ArcInst;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.topology.RTBounds;
import com.sun.electric.database.topology.RTNode;
import com.sun.electric.database.topology.SteinerTree.SteinerTreePortPair;
import com.sun.electric.database.variable.TextDescriptor;
import com.sun.electric.technology.PrimitiveNode;
import com.sun.electric.technology.technologies.Artwork;
import com.sun.electric.technology.technologies.Generic;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.placement.PlacementAdapter;
import com.sun.electric.tool.placement.PlacementAdapter.PlacementExport;
import com.sun.electric.tool.placement.PlacementFrameElectric;
import com.sun.electric.tool.placement.general.BottomUpPartition.PNPair;
import com.sun.electric.tool.simulation.test.TextUtils;
import com.sun.electric.util.math.DBMath;
import com.sun.electric.util.math.Orientation;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Placement algorithms that does bottom-up placement.
 * It finds the best "affinity" between two cells and glues them together into a "cluster".
 * It repeats this, gluing cells and clusters together, until the cell is placed.
 * One step of "gluing" a cluster may attach it in a way that overlaps other glued objects.
 * When this happens, the objects are "plowed" (shoved apart) to make room.
 *
 * Each step of the placement takes a previous placement and offers a number of "proposals"
 * for what to do in the next step. This includes various ways that the clusters can be glued.
 * At the end of the placement step, there are many proposals. These proposals are kept in a "trellis"
 * which is a limited list of possible next steps. Only the top candidates are kept.
 *
 * TO-DO list:
 *   Tune RotationTest.isBetter()
 */
public class BottomUpPlace extends PlacementFrameElectric
{
	private final static boolean DEBUGPROGRESS = false;

	/** the size of the trellis when placing */							private final static int DEFAULTTRELLISSIZE = 20;
	/** the size of the trellis when compressing */						private final static int COMPRESSIONTRELLISSIZE = 5;
	/** HPWL loss cannot be this percent worse than area improvement */	private final static int COMPRESSIONIMPROVEMENTTHRESHOLD = 7;

	private final static int GROUPPAIRSBYDENSITY = 0;
	private final static int GROUPPAIRSBYSIZE = 1;
	private final static int GROUPPAIRSFLAT = 2;
	private static int pairGrouping = GROUPPAIRSBYDENSITY;

	private static double boundWeight = 2, aspectRatioWeight = 2;
	private static int trellisWidth = DEFAULTTRELLISSIZE;

	private static List<List<PNPair>> orderedPairGroups;
	private static Map<PlacementNode,List<SteinerTreePortPair>> consOnNodes;

	protected PlacementParameter numThreadsParam = new PlacementParameter("threads",
		"Number of threads:", 4);
	protected PlacementParameter maxRuntimeParam = new PlacementParameter("runtime",
		"Runtime (in seconds, 0 for no limit):", 240);
	protected PlacementParameter canRotate = new PlacementParameter("canRotate",
		"Allow node rotation", false);

	/**
	 * Method to return the name of this placement algorithm.
	 * @return the name of this placement algorithm.
	 */
	@Override
	public String getAlgorithmName() { return "Bottom-Up-Place"; }

	/**
	 * Method to set internal factors when testing out different ways to use this placer.
	 * @param b the importance of bounds when computing placement quality.
	 * @param ar the importance of aspect ratio when computing placement quality.
	 * @param tr the size of the trellis when doing placement.
	 */
	public static void setWeights(double b, double ar, int tr)
	{
		boundWeight = b;
		aspectRatioWeight = ar;
		trellisWidth = tr;
	}

	/**
	 * Method to do placement.
	 * @param placementNodes a list of all nodes that are to be placed.
	 * @param allNetworks a list of all networks that connect the nodes.
	 * @param cellName the name of the cell being placed.
	 */
	@Override
	public void runPlacement(List<PlacementNode> placementNodes, List<PlacementNetwork> allNetworks,
		List<PlacementExport> exportsToPlace, String cellName, Job job)
	{
		setParamterValues(numThreadsParam.getIntValue(), maxRuntimeParam.getIntValue());

		// ignore trivial placement problems
		if (placementNodes.size() < 2) return;

		// figure out the density of connections
		prepareClustering(placementNodes, allNetworks);

		// create the initial placement proposal
		ProposedPlacement initialProposal = new ProposedPlacement(placementNodes);
		List<ProposedPlacement> currentProposals = new ArrayList<ProposedPlacement>();
		currentProposals.add(initialProposal);

		// do the bottom-up clustering and make lists of node-pairs to be combined
		Set<PlacementNode> macroNodes = new HashSet<PlacementNode>();
		Map<Double,List<PNPair>> allDensities = BottomUpPartition.makeClusteredPairs(placementNodes, allNetworks, macroNodes);

		// analyze instance sizes and see if they can be split into big/small
		Map<Double,List<PlacementNode>> instSizes = new TreeMap<Double,List<PlacementNode>>();
		for(PlacementNode pn : placementNodes)
		{
			Double size = new Double(pn.getWidth() * pn.getHeight());
			List<PlacementNode> nodes = instSizes.get(size);
			if (nodes == null) instSizes.put(size, nodes = new ArrayList<PlacementNode>());
			nodes.add(pn);
		}
		int si = instSizes.size();
		double[] bigSizes = new double[si];
		for(Double size : instSizes.keySet()) bigSizes[--si] = size.doubleValue();
		Set<PlacementNode> bigNodes = new HashSet<PlacementNode>();
		for(int i=0; i<bigSizes.length; i++)
		{
			if (i != 0 && bigSizes[i] <= bigSizes[i-1]/2) break;
			List<PlacementNode> nodes = instSizes.get(new Double(bigSizes[i]));
			for(PlacementNode pn : nodes) bigNodes.add(pn);
		}

		// sort the pairs by density
		orderedPairGroups = new ArrayList<List<PNPair>>();
		int di = allDensities.size();
		double[] clusteringDensities = new double[di];
		for(Double density : allDensities.keySet()) clusteringDensities[--di] = density.doubleValue();
		switch (pairGrouping)
		{
			case GROUPPAIRSFLAT:
				List<PNPair> thisPairSet = new ArrayList<PNPair>();
				for(int i=0; i<allDensities.size(); i++)
				{
					List<PNPair> pairsAtLevel = allDensities.get(new Double(clusteringDensities[i]));
					for(PNPair pnp : pairsAtLevel) thisPairSet.add(pnp);
				}
				if (thisPairSet.size() > 0) orderedPairGroups.add(thisPairSet);
				break;

			case GROUPPAIRSBYSIZE:
				// first the big-size group
				thisPairSet = new ArrayList<PNPair>();
				for(int i=0; i<allDensities.size(); i++)
				{
					List<PNPair> pairsAtLevel = allDensities.get(new Double(clusteringDensities[i]));
					for(PNPair pnp : pairsAtLevel)
					{
						if (bigNodes.contains(pnp.n1) && bigNodes.contains(pnp.n2)) thisPairSet.add(pnp);
					}
				}
				if (thisPairSet.size() > 0) orderedPairGroups.add(thisPairSet);

				// next the small-size group
				thisPairSet = new ArrayList<PNPair>();
				for(int i=0; i<allDensities.size(); i++)
				{
					List<PNPair> pairsAtLevel = allDensities.get(new Double(clusteringDensities[i]));
					for(PNPair pnp : pairsAtLevel)
					{
						if (!bigNodes.contains(pnp.n1) || !bigNodes.contains(pnp.n2)) thisPairSet.add(pnp);
					}
				}
				if (thisPairSet.size() > 0) orderedPairGroups.add(thisPairSet);
				break;

			case GROUPPAIRSBYDENSITY:
				// first the big-size group
				for(int i=0; i<allDensities.size(); i++)
				{
					List<PNPair> pairsAtLevel = allDensities.get(new Double(clusteringDensities[i]));
					thisPairSet = new ArrayList<PNPair>();
					for(PNPair pnp : pairsAtLevel)
					{
						if (bigNodes.contains(pnp.n1) && bigNodes.contains(pnp.n2)) thisPairSet.add(pnp);
					}
					if (thisPairSet.size() > 0) orderedPairGroups.add(thisPairSet);
				}

				// next the small-size group
				for(int i=0; i<allDensities.size(); i++)
				{
					List<PNPair> pairsAtLevel = allDensities.get(new Double(clusteringDensities[i]));
					thisPairSet = new ArrayList<PNPair>();
					for(PNPair pnp : pairsAtLevel)
					{
						if (!bigNodes.contains(pnp.n1) || !bigNodes.contains(pnp.n2)) thisPairSet.add(pnp);
					}
					if (thisPairSet.size() > 0) orderedPairGroups.add(thisPairSet);
				}
				break;
		}

		System.out.println(bigNodes.size() + " out of " + placementNodes.size() + " nodes are 'big'");
		String groupingName = "";
		switch (pairGrouping)
		{
			case GROUPPAIRSFLAT:      groupingName = "Flat";  break;
			case GROUPPAIRSBYSIZE:    groupingName = "Size";  break;
			case GROUPPAIRSBYDENSITY: groupingName = "Density";  break;
		}
		if (Job.getDebug())
		{
			System.out.println("Group " + orderedPairGroups.size() + " pairs by: " + groupingName +
				" (bound-weight=" + TextUtils.formatDouble(boundWeight) + " aspect-ratio-weight=" + TextUtils.formatDouble(aspectRatioWeight) +
				" trellis-size=" + trellisWidth + ")");
		}

		// do the placement
		long start = System.currentTimeMillis();
		for(;;)
		{
			// is time up?
			if (maxRuntimeParam.getIntValue() > 0)
			{
				int time = (int)((System.currentTimeMillis() - start) / 1000);
				if (time > maxRuntimeParam.getIntValue())
				{
					System.out.println("Refinement exceeded time limit");
					break;
				}
			}

			// the trellis
			List<ProposedPlacement> newProposals = new ArrayList<ProposedPlacement>();

			// examine the previous trellis and build the next step
			for(ProposedPlacement curProp : currentProposals)
			{
				// get suggested next steps for this one
				List<ProposedPlacement> nextProposals = curProp.clusteringPlacementStep(1);
				for(int i=0; i<nextProposals.size(); i++)
				{
					ProposedPlacement pp = nextProposals.get(i);
					pp.computeQuality(allNetworks);
//					if (bestPlacement == null || pp.quality < bestPlacement.quality)
//						bestPlacement = pp;
					newProposals.add(pp);
				}
			}
			if (newProposals.isEmpty()) break;

			// take only the best proposals for the next step
			Collections.sort(newProposals);
			while(newProposals.size() > trellisWidth)
				newProposals.remove(newProposals.size()-1);
			if (DEBUGPROGRESS)
			{
				for(ProposedPlacement pp : newProposals)
					showProposal(pp);
			}

			int fewestClusters = Integer.MAX_VALUE;
			for(ProposedPlacement pp : currentProposals)
				if (pp.allClusters.size() < fewestClusters) fewestClusters = pp.allClusters.size();

			if ((fewestClusters%10) == 0)
				System.out.println("Merging " + fewestClusters + " clusters");
			if (newProposals.isEmpty()) break;
			currentProposals = newProposals;
		}

		// get the final placement
		if (currentProposals.size() == 0)
		{
			System.out.println("No best placement could be found");
			return;
		}

		// pack any unconnected clusters together
		ProposedPlacement bestPlacement = currentProposals.get(0);
		if (bestPlacement.allClusters.size() > 1)
		{
			System.out.println("Combining " + bestPlacement.allClusters.size() + " unmerged clusters");
			bestPlacement.combineClusters();
		}

		// compact the final proposal
		int maxPasses = placementNodes.size();
		boolean compacted = false;
		List<ProposedPlacement> compactionTrellis = new ArrayList<ProposedPlacement>();
		compactionTrellis.add(bestPlacement);
		Rectangle2D bestBound = bestPlacement.getBounds(placementNodes);
		double bestArea = bestBound.getWidth() * bestBound.getHeight();
		double bestHPWL = bestPlacement.hpwl;
//		ProposedPlacement bestEverHPWLPlacement = bestPlacement;
//		double bestEverHPWL = bestPlacement.hpwl;
		DecimalFormat formatter = new DecimalFormat("#,###");
		for(int pass=0; pass<=maxPasses; pass++)
		{
			List<ProposedPlacement> nextTrellis = new ArrayList<ProposedPlacement>();
			for(ProposedPlacement pp : compactionTrellis)
			{
				List<ProposedPlacement> proposals = compact(placementNodes, pp, allNetworks);
				for(ProposedPlacement nextPP : proposals) nextTrellis.add(nextPP);
			}
			Collections.sort(nextTrellis, new SortPlacementByHPWL());

			// limit trellis size
			while(nextTrellis.size() > COMPRESSIONTRELLISSIZE)
				nextTrellis.remove(nextTrellis.size()-1);
			if (nextTrellis.size() == 0) break;
			ProposedPlacement bestSoFar = compactionTrellis.get(0);

			// if HPWL loss is worse than area improvement, don't compact
			Rectangle2D thisBound = bestSoFar.getBounds(placementNodes);
			double thisArea = thisBound.getWidth() * thisBound.getHeight();
			double areaImprovement = bestArea / thisArea * 100 - 100;
			double hpwlImprovement = bestHPWL / bestSoFar.hpwl * 100 - 100;
			if (areaImprovement > hpwlImprovement+COMPRESSIONIMPROVEMENTTHRESHOLD) break;

//			if (bestSoFar.hpwl < bestEverHPWL)
//			{
//				bestEverHPWL = bestSoFar.hpwl;
//				bestEverHPWLPlacement = bestSoFar;
//			}
			compactionTrellis = nextTrellis;
			bestArea = thisArea;
			bestHPWL = bestSoFar.hpwl;
			System.out.println("Compacted. HPWL now " + formatter.format(Math.round(bestSoFar.hpwl)) +
				" (improved by " + Math.round(hpwlImprovement) + "%), area now " + formatter.format(Math.round(bestArea)) +
				", improved by " + Math.round(areaImprovement) + "%)");
			compacted = true;
		}
		System.out.println("Could not compact placement" + (compacted ? " further" : ""));
		bestPlacement = compactionTrellis.get(0);
//bestPlacement = bestEverHPWLPlacement;

		// apply the final placement to the actual nodes
		bestPlacement.applyProposal(placementNodes);
	}

	private static class CompactionEdge implements Comparable
	{
		double improvement;
		Rectangle2D bound;
		Set<PlacementNode> moveSet;
		List<PlacementNode> toMove;
		String side;

		public CompactionEdge(String side, List<CompactionEdge> edges)
		{
			bound = null;
			moveSet = new HashSet<PlacementNode>();
			toMove = new ArrayList<PlacementNode>();
			this.side = side;
			if (edges != null) edges.add(this);
		}

		@Override
		public int compareTo(Object o)
		{
			if (o instanceof CompactionEdge)
			{
				CompactionEdge other = (CompactionEdge)o;
				if (improvement < other.improvement) return 1;
				if (improvement > other.improvement) return -1;
			}
			return 0;
		}
	}

///////////////////////////////////////////////// START DEBUGGING
//public static void debugIt()
//{
//	UserInterface ui = Job.getUserInterface();
//	Cell cell = ui.needCurrentCell();
//	if (cell == null) return;
//	BottomUpPlace bup = new BottomUpPlace();
//	bup.debugRects(cell);
//}
//
//private void debugRects(Cell cell)
//{
//	List<PlacementNode> nodesToPlace = CollectionFactory.createArrayList();
//	for (Iterator<NodeInst> it = cell.getNodes(); it.hasNext();)
//	{
//		NodeInst ni = it.next();
//		if (!ni.isCellInstance()) continue;
//		PlacementNode plNode = new PlacementAdapter.PlacementNode(ni, null, null, ni.getTechSpecific(), ni.getXSize(), ni.getYSize(),
//			new ArrayList<PlacementAdapter.PlacementPort>(), ni.isLocked());
//		nodesToPlace.add(plNode);
//	}
//	ProposedPlacement proposal = new ProposedPlacement(nodesToPlace);
//	System.out.println("COMPACTING "+nodesToPlace.size()+" NODES");
//	compact(nodesToPlace, proposal, null);
//}
///////////////////////////////////////////////// END DEBUGGING

	/**
	 * Method to compact the placement
	 * @param placementNodes List of PlacementNode objects in the cell.
	 * @param proposal the best proposed placement of those nodes.
	 * @param allNetworks the Networks that connect the nodes.
	 * @return true if compaction failed (either because no space could be saved,
	 * or because the space saving wasn't worth it).
	 */
	private List<ProposedPlacement> compact(List<PlacementNode> placementNodes, ProposedPlacement proposal, List<PlacementNetwork> allNetworks)
	{
		List<ProposedPlacement> possibileSteps = new ArrayList<ProposedPlacement>();
		
		// get the bounds of the cell
		Rectangle2D initialBound = proposal.getBounds(placementNodes);
		double initialArea = initialBound.getWidth() * initialBound.getHeight();

		// create the edges
		List<CompactionEdge> edges = new ArrayList<CompactionEdge>();
		CompactionEdge leftEdge = new CompactionEdge("left", edges);
		CompactionEdge rightEdge = new CompactionEdge("right", edges);
		CompactionEdge topEdge = new CompactionEdge("top", edges);
		CompactionEdge bottomEdge = new CompactionEdge("bottom", edges);

		// find the items at the edges
		for (PlacementNode pn : placementNodes)
		{
			ProxyNode p = proposal.proxyMap.get(pn);
			double lX = p.getX() - p.getWidth()/2;
			double hX = lX + p.getWidth();
			double lY = p.getY() - p.getHeight()/2;
			double hY = lY + p.getHeight();
			if (DBMath.areEquals(lX, initialBound.getMinX()))
			{
				if (!leftEdge.moveSet.contains(pn)) { leftEdge.moveSet.add(pn);  leftEdge.toMove.add(pn); }
			}
			if (DBMath.areEquals(hX, initialBound.getMaxX()))
			{
				if (!rightEdge.moveSet.contains(pn)) { rightEdge.moveSet.add(pn);  rightEdge.toMove.add(pn); }
			}
			if (DBMath.areEquals(lY, initialBound.getMinY()))
			{
				if (!bottomEdge.moveSet.contains(pn)) { bottomEdge.moveSet.add(pn);  bottomEdge.toMove.add(pn); }
			}
			if (DBMath.areEquals(hY, initialBound.getMaxY()))
			{
				if (!topEdge.moveSet.contains(pn)) { topEdge.moveSet.add(pn);  topEdge.toMove.add(pn); }
			}
		}

		// now find the bounds with the edges removed
		for (PlacementNode pn : placementNodes)
		{
			ProxyNode p = proposal.proxyMap.get(pn);
			double lX = p.getX() - p.getWidth()/2;
			double hX = lX + p.getWidth();
			double lY = p.getY() - p.getHeight()/2;
			double hY = lY + p.getHeight();
			Rectangle2D bound = new Rectangle2D.Double(lX, lY, hX-lX, hY-lY);
			for(CompactionEdge edge : edges)
			{
				if (!edge.moveSet.contains(pn))
				{
					if (edge.bound == null) edge.bound = bound; else
						edge.bound = edge.bound.createUnion(bound);
				}
			}
		}

		// compute relative value of each edge and sort the edges
		for(CompactionEdge edge : edges)
		{
			if (edge.bound == null) edge.improvement = 0; else
				edge.improvement = initialArea / (edge.bound.getWidth() * edge.bound.getHeight());
		}
		Collections.sort(edges);

		// try edges in proper order
		for(CompactionEdge edge : edges)
		{
			// make a new proposal
			ProposedPlacement newProposal = new ProposedPlacement(proposal, true);

			// make a list of empty rectangles in the cell
			List<ERectangle> rectangles = new ArrayList<ERectangle>();
			for (PlacementNode pn : placementNodes)
			{
				if (edge.moveSet.contains(pn)) continue;
				ProxyNode p = newProposal.proxyMap.get(pn);
				double lX = p.getX() - p.getWidth()/2;
				double hX = lX + p.getWidth();
				double lY = p.getY() - p.getHeight()/2;
				double hY = lY + p.getHeight();
				ERectangle rect = ERectangle.fromLambda(lX, lY, hX-lX, hY-lY);
				rectangles.add(rect);
			}
			if (rectangles.size() == 0) continue;
			ERectangle edgeBound = ERectangle.fromLambda(edge.bound);

			// move the objects
			boolean failed = false;
			for(int i=0; i<edge.toMove.size(); i++)
			{
				PlacementNode pln = edge.toMove.get(i);
				ProxyNode pNode = newProposal.proxyMap.get(pln);

				FindEmptyRects fer = new FindEmptyRects();
				List<ERectangle> emptySpace = fer.findEmptySpace(rectangles, edgeBound);

///////////////////////////////////////////////// START DEBUGGING
//if (allNetworks == null)
//{
//	System.out.println("EMPTY SPACE CALCULATION RETURNED "+emptySpace.size()+" RECTANGLES, IGNORING "+edge.side+" EDGE");
//	System.out.println("FULL GOOD:");
//	for(ERectangle full : rectangles)
//		System.out.println("  FULL SPACE: "+full.getMinX()+"<=X<="+full.getMaxX()+" / "+full.getMinY()+"<=Y<="+full.getMaxY());
//	System.out.println("EMPTY GOOD:");
//	for(ERectangle empty : emptySpace)
//		System.out.println("  EMPTY SPACE: "+empty.getMinX()+"<=X<="+empty.getMaxX()+" / "+empty.getMinY()+"<=Y<="+empty.getMaxY());
//	UserInterface ui = Job.getUserInterface();
//	Cell cell = ui.getCurrentCell();
//	for(ERectangle r : emptySpace)
//	{
//		double lowX = r.getMinX();
//		double lowY = r.getMinY();
//		double highX = r.getMaxX();
//		double highY = r.getMaxY();
//		ui.getCurrentEditWindow_().addHighlightArea(r, cell);
//		ui.getCurrentEditWindow_().addHighlightLine(new Point2D.Double(lowX, lowY), new Point2D.Double(highX, highY), cell, false, false);
//		ui.getCurrentEditWindow_().addHighlightLine(new Point2D.Double(lowX, highY), new Point2D.Double(highX, lowY), cell, false, false);
//	}
//	ui.getCurrentEditWindow_().finishedHighlighting();
//	return true;
//}
///////////////////////////////////////////////// END DEBUGGING
				double bestQuality = Double.MAX_VALUE;
				double bestX = pNode.getX(), bestY = pNode.getY();
				for(ERectangle empty : emptySpace)
				{
					if (pNode.getWidth() > empty.getWidth() || pNode.getHeight() > empty.getHeight()) continue;
					double tryX, tryY;

					if (empty.getMinX() > edge.bound.getMinX() && empty.getMinY() > edge.bound.getMinY())
					{
						tryX = empty.getMinX() + pNode.getWidth()/2;
						tryY = empty.getMinY() + pNode.getHeight()/2;
						pNode.setLocation(tryX, tryY);
						newProposal.computeQuality(allNetworks);
						if (newProposal.hpwl < bestQuality) { bestQuality = newProposal.hpwl; bestX = tryX;  bestY = tryY; }
					}

					if (empty.getMinX() > edge.bound.getMinX() && empty.getMaxY() < edge.bound.getMaxY())
					{
						tryX = empty.getMinX() + pNode.getWidth()/2;
						tryY = empty.getMaxY() - pNode.getHeight()/2;
						pNode.setLocation(tryX, tryY);
						newProposal.computeQuality(allNetworks);
						if (newProposal.hpwl < bestQuality) { bestQuality = newProposal.hpwl; bestX = tryX;  bestY = tryY; }
					}

					if (empty.getMaxX() < edge.bound.getMaxX() && empty.getMinY() > edge.bound.getMinY())
					{
						tryX = empty.getMaxX() - pNode.getWidth()/2;
						tryY = empty.getMinY() + pNode.getHeight()/2;
						pNode.setLocation(tryX, tryY);
						newProposal.computeQuality(allNetworks);
						if (newProposal.hpwl < bestQuality) { bestQuality = newProposal.hpwl; bestX = tryX;  bestY = tryY; }
					}

					if (empty.getMaxX() < edge.bound.getMaxX() && empty.getMaxY() < edge.bound.getMaxY())
					{
						tryX = empty.getMaxX() - pNode.getWidth()/2;
						tryY = empty.getMaxY() - pNode.getHeight()/2;
						pNode.setLocation(tryX, tryY);
						newProposal.computeQuality(allNetworks);
						if (newProposal.hpwl < bestQuality) { bestQuality = newProposal.hpwl; bestX = tryX;  bestY = tryY; }
					}
				}

				if (bestQuality == Double.MAX_VALUE)
				{
					// failed to compact
					failed = true;
					break;
				}

				// make the move
				pNode.setLocation(bestX, bestY);
				double lX = pNode.getX() - pNode.getWidth()/2;
				double hX = lX + pNode.getWidth();
				double lY = pNode.getY() - pNode.getHeight()/2;
				double hY = lY + pNode.getHeight();
				ERectangle rect = ERectangle.fromLambda(lX, lY, hX-lX, hY-lY);
				rectangles.add(rect);
			}
			if (failed) continue;

//System.out.println("  CREATED NEW PROPOSAL "+newProposal.proposalNumber+" THAT MOVED "+edge.toMove.size()+" CELLS FROM "+edge.side+
//	" NEW BOUND IS "+edgeBound.getMinX()+"<=X<="+edgeBound.getMaxX()+" / "+edgeBound.getMinY()+"<=Y<="+edgeBound.getMaxY());
//Rectangle2D finalBound = null;
//for (PlacementNode pn : placementNodes)
//{
//	ProxyNode p = newProposal.proxyMap.get(pn);
//	double lX = p.getX() - p.getWidth()/2;
//	double hX = lX + p.getWidth();
//	double lY = p.getY() - p.getHeight()/2;
//	double hY = lY + p.getHeight();
//	Rectangle2D bound = new Rectangle2D.Double(lX, lY, hX-lX, hY-lY);
//	if (finalBound == null) finalBound = bound; else
//		finalBound = finalBound.createUnion(bound);
//}
//System.out.println("     FINAL BOUND IS "+finalBound.getMinX()+"<=X<="+finalBound.getMaxX()+" / "+finalBound.getMinY()+"<=Y<="+finalBound.getMaxY());

			newProposal.computeQuality(allNetworks);
			possibileSteps.add(newProposal);
		}
		return possibileSteps;
	}

	private void prepareClustering(List<PlacementNode> placementNodes, List<PlacementNetwork> allNetworks)
	{
		// figure out what connections are on each node
		consOnNodes = new HashMap<PlacementNode,List<SteinerTreePortPair>>();
		for(PlacementNetwork pNet : allNetworks)
		{
			List<SteinerTreePortPair> cons = PlacementAdapter.getOptimalConnections(pNet);
			for(SteinerTreePortPair con : cons)
			{
				PlacementPort p1 = (PlacementPort)con.getPort1();
				PlacementNode n1 = p1.getPlacementNode();
				PlacementPort p2 = (PlacementPort)con.getPort2();
				PlacementNode n2 = p2.getPlacementNode();
				List<SteinerTreePortPair> consOnN1 = consOnNodes.get(n1);
				if (consOnN1 == null) consOnNodes.put(n1, consOnN1 = new ArrayList<SteinerTreePortPair>());
				List<SteinerTreePortPair> consOnN2 = consOnNodes.get(n2);
				if (consOnN2 == null) consOnNodes.put(n2, consOnN2 = new ArrayList<SteinerTreePortPair>());
				consOnN1.add(con);
				if (n2 != n1) consOnN2.add(con);
			}
		}
	}

	private static int proposedPlacementIndex = 1;

	private class ProposedPlacement implements Comparable<ProposedPlacement>
	{
		/** list of proxy nodes */									private List<ProxyNode> nodesToPlace;
		/** map from original PlacementNodes to proxy nodes */		private Map<PlacementNode,ProxyNode> proxyMap;
		/** list of all proxy clusters */							private List<ProxyCluster> allClusters;
		/** map from ProxyNodes to their ProxyClusters */			private Map<ProxyNode,ProxyCluster> clusterMap;
		/** current grouping position being resolved */				private int groupPosition;
		/** HPWL of this ProposedPlacement */						private double hpwl;
		/** quality of this ProposedPlacement */					private double quality;
		/** unique index of this ProposedPlacement */				private int proposalNumber;
		/** unique index of previous ProposedPlacement */			private int parentProposalNumber;
		/** the nodes that were moved */							private PNPair movedPair;
		/** for internal debugging */								private List<String> furtherExplanation;

		ProposedPlacement()
		{
			nodesToPlace = new ArrayList<ProxyNode>();
			proxyMap = new HashMap<PlacementNode,ProxyNode>();
			allClusters = new ArrayList<ProxyCluster>();
			clusterMap = new HashMap<ProxyNode,ProxyCluster>();
			proposalNumber = proposedPlacementIndex++;

			// uncomment the next line to gather debugging information during placement
//			furtherExplanation = new ArrayList<String>();
		}

		ProposedPlacement(List<PlacementNode> placementNodes)
		{
			this();
			groupPosition = 0;
			for (int i=0; i<placementNodes.size(); i++)
			{
				// make a ProxyNode to shadow this PlacementNode
				PlacementNode p = placementNodes.get(i);
				ProxyNode proxy = new ProxyNode(p);
				nodesToPlace.add(proxy);
				proxyMap.put(p, proxy);

				// make a cluster for this ProxyNode
				ProxyCluster pCluster = new ProxyCluster();
				clusterMap.put(proxy, pCluster);
				pCluster.clusterIndex = i;
				allClusters.add(pCluster);
				pCluster.add(proxy);
			}
		}

		ProposedPlacement(ProposedPlacement copyIt, boolean duplicateProxys)
		{
			this();
			if (duplicateProxys)
			{
				for(PlacementNode pn : copyIt.proxyMap.keySet())
				{
					ProxyNode pNode = copyIt.proxyMap.get(pn);
					ProxyNode copyPN = new ProxyNode(pNode);
					nodesToPlace.add(copyPN);
					proxyMap.put(pn, copyPN);
				}
			} else
			{
				for(ProxyNode pn : copyIt.nodesToPlace) nodesToPlace.add(pn);
				for(PlacementNode pn : copyIt.proxyMap.keySet()) proxyMap.put(pn, copyIt.proxyMap.get(pn));
			}
			for(ProxyCluster pc : copyIt.allClusters) allClusters.add(pc);
			for(ProxyNode pn : copyIt.clusterMap.keySet()) clusterMap.put(pn, copyIt.clusterMap.get(pn));
			groupPosition = copyIt.groupPosition;
			parentProposalNumber = copyIt.proposalNumber;
		}

		@Override
		public int compareTo(ProposedPlacement pp)
		{
			if (quality > pp.quality) return 1;
			if (quality < pp.quality) return -1;
			return 0;
		}

		public Rectangle2D getBounds(List<PlacementNode> placementNodes)
		{
			Rectangle2D initialBound = null;
			for (PlacementNode pn : placementNodes)
			{
				ProxyNode p = proxyMap.get(pn);
				double lX = p.getX() - p.getWidth()/2;
				double hX = lX + p.getWidth();
				double lY = p.getY() - p.getHeight()/2;
				double hY = lY + p.getHeight();
				Rectangle2D bound = new Rectangle2D.Double(lX, lY, hX-lX, hY-lY);
				if (initialBound == null) initialBound = bound; else
					initialBound = initialBound.createUnion(bound);
			}
			return initialBound;
		}

		public double computeQuality(List<PlacementNetwork> allNetworks)
		{
			hpwl = 0;
			for(PlacementNetwork pNet : allNetworks)
			{
				double lXTot = Double.MAX_VALUE;
				double hXTot = -Double.MAX_VALUE;
				double lYTot = Double.MAX_VALUE;
				double hYTot = -Double.MAX_VALUE;
				for(PlacementPort pPort : pNet.getPortsOnNet())
				{
					ProxyNode pxn = proxyMap.get(pPort.getPlacementNode());
					Orientation o = pxn.getOrientation();
					Point2D off = o.transformPoint(new Point2D.Double(pPort.getOffX(), pPort.getOffY()));
					double x = pxn.getX() + off.getX();
					double y = pxn.getY() + off.getY();
					if (x < lXTot) lXTot = x;
					if (x > hXTot) hXTot = x;
					if (y < lYTot) lYTot = y;
					if (y > hYTot) hYTot = y;
				}
				double h = ((hXTot - lXTot) + (hYTot - lYTot)) / 2;
				hpwl += h;
			}
if (false)
{
	// consider aspect-ratio factor
	double totLX=0, totHX=0, totLY=0, totHY=0;
	boolean first = true;
	for(ProxyCluster cluster : allClusters)
	{
		if (first)
		{
			totLX = cluster.bound.getMinX();
			totHX = cluster.bound.getMaxX();
			totLY = cluster.bound.getMinY();
			totHY = cluster.bound.getMaxY();
			first = false;
		} else
		{
			totLX = Math.min(totLX, cluster.bound.getMinX());
			totHX = Math.max(totHX, cluster.bound.getMaxX());
			totLY = Math.min(totLY, cluster.bound.getMinY());
			totHY = Math.max(totHY, cluster.bound.getMaxY());
		}
	}
	double wid = totHX - totLX;
	double hei = totHY - totLY;
	double aspectRatioFactor = wid < hei ? hei / wid : wid / hei;
	//uncomment the next line to allow aspect-ratio considerations to blend with HPWL
	quality = hpwl * aspectRatioFactor;
} else
{
	double aspectRatioFactor = 1;
	for(ProxyCluster cluster : allClusters)
	{
		if (cluster.getNodesInCluster().size() <= 1) continue;
		double aspectRatio = cluster.bound.getWidth() > cluster.bound.getHeight() ?
			cluster.bound.getHeight() / cluster.bound.getWidth() :
				cluster.bound.getWidth() / cluster.bound.getHeight();
		aspectRatioFactor *= aspectRatio;
	}
	//uncomment the next line to allow aspect-ratio considerations to blend with HPWL
	quality = hpwl * aspectRatioFactor;
}
			return quality;
		}

		/**
		 * Method to make a new version of a ProxyCluster.
		 * @param cluster the ProxyCluster to duplicate.
		 */
		public ProxyCluster copyCluster(ProxyCluster cluster)
		{
			ProxyCluster newCluster = new ProxyCluster();
			newCluster.clusterIndex = cluster.clusterIndex;
			allClusters.set(cluster.clusterIndex, newCluster);

			newCluster.bound = ERectangle.fromLambda(cluster.bound.getX(), cluster.bound.getY(),
				cluster.bound.getWidth(), cluster.bound.getHeight());

			List<ProxyNode> newClusterList = new ArrayList<ProxyNode>();
			for(ProxyNode pn : cluster.getNodesInCluster())
			{
				ProxyNode copyPN = new ProxyNode(pn);
				nodesToPlace.remove(pn);
				nodesToPlace.add(copyPN);

				proxyMap.put(pn.original, copyPN);

				newClusterList.add(copyPN);
				clusterMap.remove(pn);
				clusterMap.put(copyPN, newCluster);
			}
			newCluster.nodesInCluster = newClusterList;
			return newCluster;
		}

		private List<ProposedPlacement> clusteringPlacementStep(int clustersToMerge)
		{
			// make a list of new choices
			List<ProposedPlacement> choices = new ArrayList<ProposedPlacement>();
			while (choices.size() == 0)
			{
				if (groupPosition >= orderedPairGroups.size()) break;
				for(PNPair pnp : orderedPairGroups.get(groupPosition))
				{
					ProxyNode pnp1 = proxyMap.get(pnp.n1);
					ProxyNode pnp2 = proxyMap.get(pnp.n2);
					ProxyCluster cluster1 = clusterMap.get(pnp1);
					ProxyCluster cluster2 = clusterMap.get(pnp2);
					if (cluster1 == cluster2) continue;
String explanation = "MOVED NODE: "+pnp1.getNodeName() + " AT ("+pnp1.getX()+","+pnp1.getY()+") TO NODE " + pnp2.getNodeName() + " AT ("+pnp2.getX()+","+pnp2.getY()+")";

					List<RotationTest> allTests = new ArrayList<RotationTest>();
					for(int forceDir=0; forceDir<4; forceDir++)
					{
						RotationTest spinI = new RotationTest(this, pnp1, pnp2, Orientation.IDENT, forceDir);
						allTests.add(spinI);

						// test flips
						allTests.add(new RotationTest(this, pnp1, pnp2, Orientation.X, forceDir));
						allTests.add(new RotationTest(this, pnp1, pnp2, Orientation.Y, forceDir));
						allTests.add(new RotationTest(this, pnp1, pnp2, Orientation.RR, forceDir));
						if (canRotate.getBooleanValue())
						{
							// test rotations
							allTests.add(new RotationTest(this, pnp1, pnp2, Orientation.R, forceDir));
							allTests.add(new RotationTest(this, pnp1, pnp2, Orientation.RRR, forceDir));
							allTests.add(new RotationTest(this, pnp1, pnp2, Orientation.YR, forceDir));
							allTests.add(new RotationTest(this, pnp1, pnp2, Orientation.YRRR, forceDir));
						}
					}
					Collections.sort(allTests);
					RotationTest bestTest = allTests.get(0);
					ProposedPlacement newPP = new ProposedPlacement(this, false);
					if (newPP.furtherExplanation != null) newPP.furtherExplanation.add(explanation);
					makeProposal(newPP, bestTest, pnp, cluster1, cluster2);
					choices.add(newPP);
					newPP.movedPair = pnp;

//					RotationTest secondBestTest = allTests.get(1);
//					newPP = makeProposal(secondBestTest, pnp, cluster1, cluster2);
//					choices.add(newPP);
				}
				groupPosition++;
			}
			return choices;
		}

		private void makeProposal(ProposedPlacement newPP, RotationTest bestTest, PNPair pnp, ProxyCluster cluster1, ProxyCluster cluster2)
		{
			double dX = bestTest.getDelta().getX();
			double dY = bestTest.getDelta().getY();

			// make a new ProposedPlacement for this change
//System.out.println("  NEXT LEVEL DOWN ("+newPP.resolutionLevel+") WILL HAVE "+newPP.pairsToResolve.size()+" PAIRS TO EVALUATE");
//System.out.println("COPYING CLUSTERS "+cluster1.clusterIndex+" AND "+cluster2.clusterIndex);
			cluster1 = newPP.copyCluster(cluster1);
			cluster2 = newPP.copyCluster(cluster2);

			// move the cluster
			cluster1.rotate(bestTest.getOrientation());
			for(ProxyNode pNode : cluster1.getNodesInCluster())
			{
				pNode.setLocation(pNode.getX() + dX, pNode.getY() + dY);
				if (newPP.furtherExplanation != null)
					newPP.furtherExplanation.add("MOVE "+pNode.getNodeName()+" ("+dX+","+dY+"), ROT="+bestTest.getOrientation()+
						" TO ("+pNode.getX()+","+pNode.getY()+") ROTATED "+bestTest.getOrientation());
//System.out.println("SO MOVED "+pNode+" TO ("+pNode.getX()+","+pNode.getY()+") ORIENTATION "+pNode.getOrientation());
			}

			// copy contents of cluster2 into cluster1
			for(ProxyNode pNode : cluster2.getNodesInCluster())
			{
				cluster1.add(pNode);
				newPP.clusterMap.put(pNode, cluster1);
			}
			cluster1.computeBounds();

			// delete cluster2
			int lastIndex = newPP.allClusters.size() - 1;
			if (cluster2.clusterIndex < lastIndex)
			{
				ProxyCluster newLastCluster = newPP.copyCluster(newPP.allClusters.get(lastIndex));
				if (cluster1.clusterIndex == lastIndex) cluster1 = newLastCluster;
				newLastCluster.clusterIndex = cluster2.clusterIndex;
				newPP.allClusters.set(newLastCluster.clusterIndex, newLastCluster);
			}
			newPP.allClusters.remove(lastIndex);

			// use RTree to make sure clusters don't overlap
			RTNode<ProxyCluster> rTree = newPP.makeClusterRTree();
			int cluster1Index = cluster1.clusterIndex;
			rTree = newPP.plowCluster(cluster1Index, 0, 0, rTree, true, true, true, true);
			for(int i=0; i<newPP.allClusters.size(); i++)
			{
				if (i == cluster1Index) continue;
				rTree = newPP.plowCluster(i, 0, 0, rTree, true, true, true, true);
			}
		}

		public void applyProposal(List<PlacementNode> placementNodes)
		{
			for (PlacementNode pn : placementNodes)
			{
				ProxyNode p = proxyMap.get(pn);
				pn.setPlacement(p.getX(), p.getY());
				pn.setOrientation(p.getOrientation());
			}
		}

		private RTNode<ProxyCluster> makeClusterRTree()
		{
			RTNode<ProxyCluster> root = RTNode.makeTopLevel();
			for(ProxyCluster pCluster : allClusters)
				root = RTNode.linkGeom(null, root, pCluster);
			return root;
		}

		private RTNode<ProxyCluster> plowCluster(int clusterIndex, double dX, double dY, RTNode<ProxyCluster> rTree,
			boolean lDir, boolean rDir, boolean uDir, boolean dDir)
		{
			// propose the node move
			ProxyCluster pCluster = allClusters.get(clusterIndex);
			ERectangle oldBounds = pCluster.getBounds();
			double prevX = oldBounds.getCenterX(), prevY = oldBounds.getCenterY();
			if (furtherExplanation != null)
				furtherExplanation.add("PLOWING "+pCluster+" BY ("+TextUtils.formatDouble(dX)+","+TextUtils.formatDouble(dY)+")");

			// move the node in the R-Tree
			if (dX != 0 || dY != 0)
			{
				rTree = RTNode.unLinkGeom(null, rTree, pCluster);
				pCluster = copyCluster(pCluster);

				for(ProxyNode pNode : pCluster.getNodesInCluster())
				{
					pNode.setLocation(pNode.getX() + dX, pNode.getY() + dY);
				}
				pCluster.computeBounds();
				if (furtherExplanation != null)
					furtherExplanation.add("CLUSTER BOUNDS NOW "+TextUtils.formatDouble(pCluster.getBounds().getMinX())+"<=X<="+
						TextUtils.formatDouble(pCluster.getBounds().getMaxX())+" AND "+TextUtils.formatDouble(pCluster.getBounds().getMinY())+
							"<=Y<="+TextUtils.formatDouble(pCluster.getBounds().getMaxY()));
				rTree = RTNode.linkGeom(null, rTree, pCluster);
			}
			ERectangle pBound = pCluster.getBounds();

			ERectangle search = ERectangle.fromLambda(Math.min(prevX, prevX+dX) - oldBounds.getWidth()/2,
				Math.min(prevY, prevY+dY) - oldBounds.getHeight()/2,
				oldBounds.getWidth() + Math.abs(dX), oldBounds.getHeight() + Math.abs(dY));
			boolean blocked = true;
			while (blocked)
			{
				blocked = false;

				// re-get the cluster bounds because it might have changed
				pBound = pCluster.getBounds();

				// look for an intersecting node
				for (Iterator<ProxyCluster> sea = new RTNode.Search<ProxyCluster>(search, rTree, true); sea.hasNext();)
				{
					ProxyCluster inArea = sea.next();
					if (inArea == pCluster) continue;
					ERectangle sBound = inArea.getBounds();
					if (pBound.getMinX() >= sBound.getMaxX() || pBound.getMaxX() <= sBound.getMinX() ||
						pBound.getMinY() >= sBound.getMaxY() || pBound.getMaxY() <= sBound.getMinY()) continue;

					// figure out which way to move the blocking node
					double leftMotion = sBound.getMaxX() - pBound.getMinX();
					double rightMotion = pBound.getMaxX() - sBound.getMinX();
					double downMotion = sBound.getMaxY() - pBound.getMinY();
					double upMotion = pBound.getMaxY() - sBound.getMinY();
					if (!lDir) leftMotion = Double.MAX_VALUE;
					if (!rDir) rightMotion = Double.MAX_VALUE;
					if (!dDir) downMotion = Double.MAX_VALUE;
					if (!uDir) upMotion = Double.MAX_VALUE;
					if (furtherExplanation != null)
					{
						String exp = "  INTERSECTS "+inArea+" LEFT=";
						if (leftMotion == Double.MAX_VALUE) exp += "INFINITE"; else exp +=TextUtils.formatDouble(leftMotion);
						exp += " RIGHT=";
						if (rightMotion == Double.MAX_VALUE) exp += "INFINITE"; else exp +=TextUtils.formatDouble(rightMotion);
						exp += " UP=";
						if (upMotion == Double.MAX_VALUE) exp += "INFINITE"; else exp +=TextUtils.formatDouble(upMotion);
						exp += " DOWN=";
						if (downMotion == Double.MAX_VALUE) exp += "INFINITE"; else exp +=TextUtils.formatDouble(downMotion);
						furtherExplanation.add(exp);
					}
					double leastMotion = Math.min(Math.min(leftMotion, rightMotion), Math.min(upMotion, downMotion));
					if (leftMotion == leastMotion)
					{
						// move the other block left to keep it away
						if (furtherExplanation != null)
							furtherExplanation.add("  MOVE "+inArea+" "+TextUtils.formatDouble(leftMotion)+" LEFT (Because its right edge is "+
								TextUtils.formatDouble(sBound.getMaxX())+" and "+pCluster+" left edge is "+TextUtils.formatDouble(pBound.getMinX())+")");
						rTree = plowCluster(inArea.clusterIndex, -leftMotion, 0, rTree, lDir, false, false, false);
					} else if (rightMotion == leastMotion)
					{
						// move the other block right to keep it away
						if (furtherExplanation != null)
							furtherExplanation.add("  MOVE "+inArea+" "+TextUtils.formatDouble(rightMotion)+" RIGHT (Because its left edge is "+
								TextUtils.formatDouble(sBound.getMinX())+" and "+pCluster+" right edge is "+TextUtils.formatDouble(pBound.getMaxX())+")");
						rTree = plowCluster(inArea.clusterIndex, rightMotion, 0, rTree, false, rDir, false, false);
					} else if (upMotion == leastMotion)
					{
						// move the other block up to keep it away
						if (furtherExplanation != null)
							furtherExplanation.add("  MOVE "+inArea+" "+TextUtils.formatDouble(upMotion)+" UP (Because its bottom edge is "+
								TextUtils.formatDouble(sBound.getMinY())+" and "+pCluster+" top edge is "+TextUtils.formatDouble(pBound.getMaxY())+")");
						rTree = plowCluster(inArea.clusterIndex, 0, upMotion, rTree, false, false, uDir, false);
					} else if (downMotion == leastMotion)
					{
						// move the other block down to keep it away
						if (furtherExplanation != null)
							furtherExplanation.add("  MOVE "+inArea+" "+TextUtils.formatDouble(downMotion)+" DOWN (Because its top edge is "+
								TextUtils.formatDouble(sBound.getMaxY())+" and "+pCluster+" bottom edge is "+TextUtils.formatDouble(pBound.getMinY())+")");
						rTree = plowCluster(inArea.clusterIndex, 0, -downMotion, rTree, false, false, false, dDir);
					}
					blocked = true;
					break;
				}
			}
			return rTree;
		}

		/**
		 * Method to combine all clusters into one that is optimally sized.
		 * The assumption is that everything is clustered at this point,
		 * so if there are multiple clusters, they are not connected to each other.
		 */
		private void combineClusters()
		{
			// sort clusters by size
			Collections.sort(allClusters);

			// the largest one (first one) is the "main" cluster
			ProxyCluster mainCluster = allClusters.get(0);

			// gather all X and Y coordinates in the main cluster
			Set<Double> xCoords = new TreeSet<Double>();
			Set<Double> yCoords = new TreeSet<Double>();
			for(ProxyNode pNode : mainCluster.getNodesInCluster())
			{
				xCoords.add(new Double(pNode.getX() - pNode.getWidth()/2));
				xCoords.add(new Double(pNode.getX() + pNode.getWidth()/2));
				yCoords.add(new Double(pNode.getY() - pNode.getHeight()/2));
				yCoords.add(new Double(pNode.getY() + pNode.getHeight()/2));
			}

			// iterate over remaining clusters from largest on down
			int otherClusterIndex = 1;
			while (allClusters.size() > otherClusterIndex)
			{
				ProxyCluster otherCluster = allClusters.get(otherClusterIndex);

				// try all of these coordinates and find the one that expands the main cluster least
				double bestArea = -1;
				double bestDX = 0, bestDY = 0;
				// TODO: why not try X pushing, too?
//				for(Double x : xCoords)
//				{
//					for(int forceDir=0; forceDir<4; forceDir++)
//					{
//						double dX = x.doubleValue() - otherCluster.bound.getMaxX();
//						double dY = mainCluster.bound.getCenterY() - otherCluster.bound.getCenterY();
//						EPoint newDelta = pushCluster(otherCluster, mainCluster, dX, dY, forceDir, null);
//						double area = mainCluster.getAreaWithOtherShiftedCluster(otherCluster, newDelta);
//						if (bestArea < 0 || area < bestArea)
//						{
//							bestArea = area;
//							bestDX = newDelta.getX();
//							bestDY = newDelta.getY();
//						}
//					}
//				}
				for(Double y : yCoords)
				{
					for(int forceDir=0; forceDir<4; forceDir++)
					{
						double dX = mainCluster.bound.getCenterX() - otherCluster.bound.getCenterX();
						double dY = y.doubleValue() - otherCluster.bound.getMaxY();
						EPoint newDelta = pushCluster(otherCluster, mainCluster, dX, dY, forceDir, null);
						double area = mainCluster.getAreaWithOtherShiftedCluster(otherCluster, newDelta);
						if (bestArea < 0 || area < bestArea)
						{
							bestArea = area;
							bestDX = newDelta.getX();
							bestDY = newDelta.getY();
						}
					}
				}

				// move cluster, add it to the list of coordinates
				for(ProxyNode pNode : otherCluster.getNodesInCluster())
				{
					pNode.setLocation(pNode.getX() + bestDX, pNode.getY() + bestDY);
					xCoords.add(new Double(pNode.getX() - pNode.getWidth()/2));
					xCoords.add(new Double(pNode.getX() + pNode.getWidth()/2));
					yCoords.add(new Double(pNode.getY() - pNode.getHeight()/2));
					yCoords.add(new Double(pNode.getY() + pNode.getHeight()/2));
				}

				// merge the clusters
				for(ProxyNode pNode : otherCluster.getNodesInCluster())
				{
					mainCluster.add(pNode);
					clusterMap.put(pNode, mainCluster);
				}
				allClusters.remove(otherCluster);
				mainCluster.computeBounds();
			}
		}

//		public void describe(String title)
//		{
//			System.out.print(title+" PLACEMENT "+getObjName(this)+" HAS "+nodesToPlace.size()+" NODES:");
//			for(ProxyNode pn : nodesToPlace) System.out.print(" "+getObjName(pn)+"="+pn.getNodeName()+"/C="+getObjName(clusterMap.get(pn)));
//			System.out.println();
//			System.out.print("  HAS "+allClusters.size()+" CLUSTERS:");
//			for(ProxyCluster pc : allClusters)
//			{
//				System.out.print(" "+getObjName(pc));
//				String sep = "[";
//				for(ProxyNode pn : pc.nodesInCluster) { System.out.print(sep+getObjName(pn)+"="+pn.getNodeName());  sep = ", "; }
//				if (sep.equals("[")) System.out.print(sep);
//				System.out.print("]");
//			}
//			System.out.println();
//		}
	}

	/**
	 * Comparator class for sorting ProposedPlacement by pure HPWL.
	 */
	private static class SortPlacementByHPWL implements Comparator<ProposedPlacement>
	{
		public int compare(ProposedPlacement pp1, ProposedPlacement pp2)
		{
			if (pp1.hpwl < pp2.hpwl) return -1; 
			if (pp1.hpwl > pp2.hpwl) return 1; 
			return 0;
		}
	}

	/**
	 * Class to test a connection of two clusters
	 */
	private class RotationTest implements Comparable<RotationTest>
	{
		private double metric;
		private Point2D delta;
		private ERectangle overallBound;
		private Orientation spin;
		private ProxyNode beingMoved;
		ProxyCluster cluster1, cluster2;

		/**
		 * Constructor builds a test of the pairing of two nodes.
		 * @param pnp the pairing.
		 * @param spin the Orientation change of the first node.
		 */
		public RotationTest(ProposedPlacement pp, ProxyNode pn1, ProxyNode pn2, Orientation spin, int forceDir)
		{
			beingMoved = pn1;
			this.spin = spin;
			delta = new Point2D.Double();
			cluster1 = pp.clusterMap.get(pn1);
			cluster2 = pp.clusterMap.get(pn2);
			metric = bringTogether(pp, pn1, pn2, delta, spin, forceDir);
		}

		@Override
		public String toString()
		{
			String msg = "MOVE "+beingMoved+" R="+spin.toString()+" TO ("+(beingMoved.getX()+delta.getX())+","+
				(beingMoved.getY()+delta.getY())+")";
			return msg;
		}

		@Override
		public int compareTo(RotationTest other)
		{
			double metricImprovement = (other.metric - metric) / Math.max(other.metric, metric);

			double thisArea = overallBound.getWidth() * overallBound.getHeight();
			double otherArea = other.overallBound.getWidth() * other.overallBound.getHeight();
			double boundImprovement = (otherArea - thisArea) / Math.max(thisArea, otherArea);
			double aspectRatioThis = overallBound.getWidth() > overallBound.getHeight() ?
				overallBound.getHeight() / overallBound.getWidth() :
					overallBound.getWidth() / overallBound.getHeight();
			double aspectRatioOther = other.overallBound.getWidth() > other.overallBound.getHeight() ?
				other.overallBound.getHeight() / other.overallBound.getWidth() :
					other.overallBound.getWidth() / other.overallBound.getHeight();
			double aspectRatioImprovement = (aspectRatioThis - aspectRatioOther) / Math.max(aspectRatioOther, aspectRatioThis);
boundImprovement *= boundWeight;
aspectRatioImprovement *= aspectRatioWeight;
			double totalImprovement = metricImprovement + boundImprovement + aspectRatioImprovement;
			if (totalImprovement < 0) return 1;
			if (totalImprovement > 0) return -1;
			return 0;
		}

		public Point2D getDelta() { return delta; }

		public Orientation getOrientation() { return spin; }

		private double bringTogether(ProposedPlacement pp, ProxyNode px1, ProxyNode px2, Point2D delta, Orientation spin1, int forceDir)
		{
			ComputeForce cf = new ComputeForce(pp, px1, px2);

			// save information for nodes in cluster 1
			for(ProxyNode pn : cluster1.nodesInCluster) pn.saveValues();

			// rotate cluster1 appropriately
			cluster1.rotate(spin1);

			// figure out how to move these clusters together
			List<SteinerTreePortPair> consOnC1 = new ArrayList<SteinerTreePortPair>();
			for(ProxyNode pn : cluster1.nodesInCluster)
			{
				for(SteinerTreePortPair pc : consOnNodes.get(pn.original)) consOnC1.add(pc);
			}
			Set<PlacementNode> nodesInC1 = new HashSet<PlacementNode>();
			for(ProxyNode pn : cluster1.nodesInCluster) nodesInC1.add(pn.original);
			Set<PlacementNode> nodesInC2 = new HashSet<PlacementNode>();
			for(ProxyNode pn : cluster2.nodesInCluster) nodesInC2.add(pn.original);

			List<SteinerTreePortPair> validConnections = new ArrayList<SteinerTreePortPair>();
			for(SteinerTreePortPair con : consOnC1)
			{
				PlacementPort pp1 = (PlacementPort)con.getPort1();
				PlacementPort pp2 = (PlacementPort)con.getPort2();
				boolean linksTheNodes = false;
				if (nodesInC1.contains(pp1.getPlacementNode()) && nodesInC2.contains(pp2.getPlacementNode())) linksTheNodes = true;
				if (nodesInC1.contains(pp2.getPlacementNode()) && nodesInC2.contains(pp1.getPlacementNode())) linksTheNodes = true;
				if (linksTheNodes) validConnections.add(con);
			}

			for(SteinerTreePortPair con : validConnections)
			{
				PlacementPort pp1 = (PlacementPort)con.getPort1();
				PlacementPort pp2 = (PlacementPort)con.getPort2();
				ProxyNode pxn1 = pp.proxyMap.get(pp1.getPlacementNode());
				Orientation o1 = pxn1.getOrientation();
				Point2D off1 = o1.transformPoint(new Point2D.Double(pp1.getOffX(), pp1.getOffY()));
				double x1 = pxn1.getX() + off1.getX();
				double y1 = pxn1.getY() + off1.getY();
				ProxyNode pxn2 = pp.proxyMap.get(pp2.getPlacementNode());
				Orientation o2 = pxn2.getOrientation();
				Point2D off2 = o2.transformPoint(new Point2D.Double(pp2.getOffX(), pp2.getOffY()));
				double x2 = pxn2.getX() + off2.getX();
				double y2 = pxn2.getY() + off2.getY();
				// move pn1 on top of pn2 and then back it off the minimal amount
				double dX = x2 - x1;
				double dY = y2 - y1;

				// if this is power/ground, double the force
				boolean isOnRail = pp1.getPlacementNetwork().isOnRail();

				if (pxn1 == px1)
				{
					cf.accumulateForce(dX, dY, isOnRail);
				} else if (pxn2 == px1)
				{
					cf.accumulateForce(-dX, -dY, isOnRail);
				}
			}

			// normalize the force vectors
			cf.normalizeForce();
			double dX = cf.getForceX();
			double dY = cf.getForceY();

			// propose the node move
			EPoint newDelta = pushCluster(cluster1, cluster2, dX, dY, forceDir, null);
			dX = newDelta.getX();
			dY = newDelta.getY();
			delta.setLocation(newDelta);

			// determine length of connections
			double totalLen = 0;
			for(SteinerTreePortPair con : validConnections)
			{
				PlacementPort pp1 = (PlacementPort)con.getPort1();
				PlacementPort pp2 = (PlacementPort)con.getPort2();
				ProxyNode pxn1 = pp.proxyMap.get(pp1.getPlacementNode());
				ProxyNode pxn2 = pp.proxyMap.get(pp2.getPlacementNode());
				double x1 = pxn1.getX(), y1 = pxn1.getY();
				double x2 = pxn2.getX(), y2 = pxn2.getY();
				if (px1 == pxn1) { x1 += dX;  y1 += dY; } else { x2 += dX;  y2 += dY; }

				Orientation o1 = pxn1.getOrientation();
				Point2D off1 = o1.transformPoint(new Point2D.Double(pp1.getOffX(), pp1.getOffY()));
				x1 += off1.getX();
				y1 += off1.getY();

				Orientation o2 = pxn2.getOrientation();
				Point2D off2 = o2.transformPoint(new Point2D.Double(pp2.getOffX(), pp2.getOffY()));
				x2 += off2.getX();
				y2 += off2.getY();

				// move pn1 on top of pn2 and then back it off the minimal amount
				double dist = Math.sqrt((x2-x1)*(x2-x1) + (y2-y1)*(y2-y1));

				// if this is not power/ground, double the distance
				if (!pp1.getPlacementNetwork().isOnRail())
					dist *= 2;
				totalLen += dist;
			}

			// determine the area of the combined clusters
			double lXTot = cluster2.getBounds().getMinX();
			double hXTot = cluster2.getBounds().getMaxX();
			double lYTot = cluster2.getBounds().getMinY();
			double hYTot = cluster2.getBounds().getMaxY();
			for(ProxyNode pn : cluster1.nodesInCluster)
			{
				double lX = pn.getX() - pn.getWidth()/2 + dX;
				double hX = lX + pn.getWidth();
				double lY = pn.getY() - pn.getHeight()/2 + dY;
				double hY = lY + pn.getHeight();
				if (lX < lXTot) lXTot = lX;
				if (hX > hXTot) hXTot = hX;
				if (lY < lYTot) lYTot = lY;
				if (hY > hYTot) hYTot = hY;
			}
			overallBound = ERectangle.fromLambda(lXTot, lYTot, hXTot-lXTot, hYTot-lYTot);

			// restore information for nodes in cluster
			for(ProxyNode pn : cluster1.nodesInCluster) pn.restoreValues();
			cluster1.computeBounds();

			return totalLen;
		}
	}

	/*************************************** Force Computation **********************************/

	private class ComputeForce
	{
		private double dX, dY;						// forces on the node
		private int numMoved;						// number of forces on the node
		private double[] dXQ, dYQ;
		private int[] numMovedQ;
		private ProxyNode px1, px2;
		private List<EPoint> perfectAlignment;
		private List<EPoint> imperfectAlignment;

		public ComputeForce(ProposedPlacement pp, ProxyNode px1, ProxyNode px2)
		{
			this.px1 = px1;
			this.px2 = px2;
			dXQ = new double[8];
			dYQ = new double[8];
			numMovedQ = new int[8];
			perfectAlignment = new ArrayList<EPoint>();
			imperfectAlignment = new ArrayList<EPoint>();
		}

		public void accumulateForce(double addX, double addY, boolean isOnRail)
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
				numMovedQ[quadrant]++;
			}

			boolean alignedPerfectly = false;
			double top1 = px1.getY() + px1.getHeight()/2 + addY;
			double bot1 = px1.getY() - px1.getHeight()/2 + addY;
			double right1 = px1.getX() + px1.getWidth()/2 + addX;
			double left1 = px1.getX() - px1.getWidth()/2 + addX;
			double top2 = px2.getY() + px2.getHeight()/2;
			double bot2 = px2.getY() - px2.getHeight()/2;
			double right2 = px2.getX() + px2.getWidth()/2;
			double left2 = px2.getX() - px2.getWidth()/2;
			if (DBMath.areEquals(top1, top2) && DBMath.areEquals(bot1, bot2)) alignedPerfectly = true;
			if (DBMath.areEquals(right1, right2) && DBMath.areEquals(left1, left2)) alignedPerfectly = true;
			if (isOnRail) alignedPerfectly = true;
			if (alignedPerfectly)
				perfectAlignment.add(EPoint.fromLambda(addX, addY));
		}

		public void normalizeForce()
		{
			if (numMoved != 0)
			{
				dX /= numMoved;
				dY /= numMoved;
			}
			if (numMoved > 1)
			{
				int biggestQuadrant = -1;
				int numInBiggestQuadrant = 0;
				for(int i=0; i<8; i++)
				{
					if (numMovedQ[i] > numInBiggestQuadrant)
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
					}
				}
			}
			if (perfectAlignment.size() > 0 || imperfectAlignment.size() > 0)
			{
				double bestDist = Double.MAX_VALUE;
				EPoint bestMove = null;
				for(EPoint move : perfectAlignment)
				{
					double offX = dX - move.getX();
					double offY = dY - move.getY();
					double dist = Math.sqrt(offX*offX + offY*offY);
					if (dist < bestDist)
					{
						bestDist = dist;
						bestMove = move;
					}
				}
				if (perfectAlignment.isEmpty())
				{
					for(EPoint move : imperfectAlignment)
					{
						double offX = dX - move.getX();
						double offY = dY - move.getY();
						double dist = Math.sqrt(offX*offX + offY*offY);
						if (dist < bestDist)
						{
							bestDist = dist;
							bestMove = move;
						}
					}
				}
				if (bestMove != null)
				{
					dX = bestMove.getX();
					dY = bestMove.getY();
				}
			}
		}

		public double getForceX() { return DBMath.round(dX); }

		public double getForceY() { return DBMath.round(dY); }
	}

	/**
	 * Method to determine how to adjust a cluster when a different one is moved.
	 * @param pc1 the cluster being moved.
	 * @param pc2 the cluster being affected.
	 * @param dX the X amount that the first cluster is being moved.
	 * @param dY the Y amount that the first cluster is being moved.
	 * @return an EPoint with the actual motion for the cluster being moved.
	 */
	private EPoint pushCluster(ProxyCluster pcMoving, ProxyCluster pcFixed, double dX, double dY, int forceDir, ProposedPlacement debug)
	{
		// make an R-Tree with the non-moving cluster
		RTNode<ProxyNode> root = RTNode.makeTopLevel();
		for(ProxyNode pnFixed : pcFixed.getNodesInCluster())
			root = RTNode.linkGeom(null, root, pnFixed);

		double bestLeftMotion = 0, bestRightMotion = 0, bestUpMotion = 0, bestDownMotion = 0;
		boolean tryNegLeft = true, tryNegRight = true, tryNegUp = true, tryNegDown = true;
		boolean intersects = true;
		while (intersects)
		{
			intersects = false;
			for(ProxyNode pnMoving : pcMoving.getNodesInCluster())
			{
				// see if moving cluster must move left to avoid fixed cluster
				ERectangle boundMovingLeft = ERectangle.fromLambda(pnMoving.getX() + dX - bestLeftMotion - pnMoving.getWidth()/2,
					pnMoving.getY() + dY - pnMoving.getHeight()/2, pnMoving.getWidth(), pnMoving.getHeight());
				for (Iterator<ProxyNode> sea = new RTNode.Search<ProxyNode>(boundMovingLeft, root, true); sea.hasNext();)
				{
					ERectangle boundFixed = sea.next().getBounds();
					if (boundFixed.getMaxX() > boundMovingLeft.getMinX() && boundFixed.getMinX() < boundMovingLeft.getMaxX() &&
						boundFixed.getMaxY() > boundMovingLeft.getMinY() && boundFixed.getMinY() < boundMovingLeft.getMaxY())
					{
						bestLeftMotion += boundMovingLeft.getMaxX() - boundFixed.getMinX();
						intersects = true;
						tryNegLeft = false;
						break;
					}
				}
				if (bestLeftMotion <= 0 && tryNegLeft)
				{
					// may have to shift right to pack closely
					bestLeftMotion -= pcMoving.getNodesInCluster().get(0).getWidth();
					if (pcMoving.getBounds().getMinX() - bestLeftMotion >= pcFixed.getBounds().getMaxX())
					{
						bestLeftMotion = 0;
						tryNegLeft = false;
					}
					intersects = true;
				}

				// see if moving cluster must move right to avoid fixed cluster
				ERectangle boundMovingRight = ERectangle.fromLambda(pnMoving.getX() + dX + bestRightMotion - pnMoving.getWidth()/2,
					pnMoving.getY() + dY - pnMoving.getHeight()/2, pnMoving.getWidth(), pnMoving.getHeight());
				for (Iterator<ProxyNode> sea = new RTNode.Search<ProxyNode>(boundMovingRight, root, true); sea.hasNext();)
				{
					ERectangle boundFixed = sea.next().getBounds();
					if (boundFixed.getMaxX() > boundMovingRight.getMinX() && boundFixed.getMinX() < boundMovingRight.getMaxX() &&
						boundFixed.getMaxY() > boundMovingRight.getMinY() && boundFixed.getMinY() < boundMovingRight.getMaxY())
					{
						bestRightMotion += boundFixed.getMaxX() - boundMovingRight.getMinX();
						intersects = true;
						tryNegRight = false;
						break;
					}
				}
				if (bestRightMotion <= 0 && tryNegRight)
				{
					// may have to shift left to pack closely
					bestRightMotion -= pcMoving.getNodesInCluster().get(0).getWidth();
					if (pcMoving.getBounds().getMaxX() + bestRightMotion <= pcFixed.getBounds().getMinX())
					{
						bestRightMotion = 0;
						tryNegRight = false;
					}
					intersects = true;
				}

				// see if moving cluster must move up to avoid fixed cluster
				ERectangle boundMovingUp = ERectangle.fromLambda(pnMoving.getX() + dX - pnMoving.getWidth()/2,
					pnMoving.getY() + dY + bestUpMotion - pnMoving.getHeight()/2, pnMoving.getWidth(), pnMoving.getHeight());
				for (Iterator<ProxyNode> sea = new RTNode.Search<ProxyNode>(boundMovingUp, root, true); sea.hasNext();)
				{
					ERectangle boundFixed = sea.next().getBounds();
					if (boundFixed.getMaxX() > boundMovingUp.getMinX() && boundFixed.getMinX() < boundMovingUp.getMaxX() &&
						boundFixed.getMaxY() > boundMovingUp.getMinY() && boundFixed.getMinY() < boundMovingUp.getMaxY())
					{
						bestUpMotion += boundFixed.getMaxY() - boundMovingUp.getMinY();
						intersects = true;
						tryNegUp = false;
						break;
					}
				}
				if (bestUpMotion <= 0 && tryNegUp)
				{
					// may have to shift down to pack closely
					bestUpMotion -= pcMoving.getNodesInCluster().get(0).getHeight();
					if (pcMoving.getBounds().getMaxY() + bestUpMotion <= pcFixed.getBounds().getMinY())
					{
						bestUpMotion = 0;
						tryNegUp = false;
					}
					intersects = true;
				}

				// see if moving cluster must move down to avoid fixed cluster
				ERectangle boundMovingDown = ERectangle.fromLambda(pnMoving.getX() + dX - pnMoving.getWidth()/2,
					pnMoving.getY() + dY - bestDownMotion - pnMoving.getHeight()/2, pnMoving.getWidth(), pnMoving.getHeight());
				for (Iterator<ProxyNode> sea = new RTNode.Search<ProxyNode>(boundMovingDown, root, true); sea.hasNext();)
				{
					ProxyNode pnf = sea.next();
					ERectangle boundFixed = pnf.getBounds();
					if (boundFixed.getMaxX() > boundMovingDown.getMinX() && boundFixed.getMinX() < boundMovingDown.getMaxX() &&
						boundFixed.getMaxY() > boundMovingDown.getMinY() && boundFixed.getMinY() < boundMovingDown.getMaxY())
					{
						bestDownMotion += boundMovingDown.getMaxY() - boundFixed.getMinY();
						intersects = true;
						tryNegDown = false;
						break;
					}
				}
				if (bestDownMotion <= 0 && tryNegDown)
				{
					// may have to shift up to pack closely
					bestDownMotion -= pcMoving.getNodesInCluster().get(0).getHeight();
					if (pcMoving.getBounds().getMinY() - bestDownMotion >= pcFixed.getBounds().getMaxY())
					{
						bestDownMotion = 0;
						tryNegDown = false;
					}
					intersects = true;
				}

				if (intersects) break;
			}
		}

		double moveX = 0, moveY = 0;
		switch (forceDir)
		{
			case 0:			// left
				moveX = -bestLeftMotion;  break;
			case 1:			// right
				moveX = bestRightMotion;  break;
			case 2:			// up
				moveY = bestUpMotion;     break;
			case 3:			// down
				moveY = -bestDownMotion;  break;
			default:
				if (bestLeftMotion < bestRightMotion) moveX = -bestLeftMotion; else moveX = bestRightMotion;
				if (bestDownMotion < bestUpMotion) moveY = -bestDownMotion; else moveY = bestUpMotion;
				if (Math.abs(moveX) > Math.abs(moveY)) moveX = 0; else moveY = 0;
				break;
		}
		return EPoint.fromLambda(moveX+dX, moveY+dY);
	}

	/*************************************** Proxy Cluster **********************************/

	private static class ProxyCluster implements RTBounds, Comparable<ProxyCluster>
	{
		private List<ProxyNode> nodesInCluster;
		private ERectangle bound;
		private int clusterIndex;

		ProxyCluster()
		{
			nodesInCluster = new ArrayList<ProxyNode>();
		}

		@Override
		public int compareTo(ProxyCluster pcO)
		{
			double sizeA = bound.getWidth() * bound.getHeight();
			double sizeB = pcO.bound.getWidth() * pcO.bound.getHeight();
			if (sizeA > sizeB) return -1;
			if (sizeA < sizeB) return 1;
			return 0;
		}

		@Override
		public ERectangle getBounds() { return bound; }

		public List<ProxyNode> getNodesInCluster() { return nodesInCluster; }

		public void computeBounds()
		{
			double lXAll=0, hXAll=0, lYAll=0, hYAll=0;
			for(int i=0; i<nodesInCluster.size(); i++)
			{
				ProxyNode pn = nodesInCluster.get(i);
				double lX = pn.getX() - pn.getWidth()/2;
				double hX = lX + pn.getWidth();
				double lY = pn.getY() - pn.getHeight()/2;
				double hY = lY + pn.getHeight();
				if (i == 0)
				{
					lXAll = lX;   hXAll = hX;
					lYAll = lY;   hYAll = hY;
				} else
				{
					if (lX < lXAll) lXAll = lX;
					if (hX > hXAll) hXAll = hX;
					if (lY < lYAll) lYAll = lY;
					if (hY > hYAll) hYAll = hY;
				}
			}

			// add slop so that there are no odd cluster edges
			long l = (long)Math.ceil(lXAll * DBMath.GRID);
			if ((l&1) != 0) l--;
			lXAll = l / DBMath.GRID;

			l = (long)Math.ceil(hXAll * DBMath.GRID);
			if ((l&1) != 0) l++;
			hXAll = l / DBMath.GRID;

			l = (long)Math.ceil(lYAll * DBMath.GRID);
			if ((l&1) != 0) l--;
			lYAll = l / DBMath.GRID;

			l = (long)Math.ceil(hYAll * DBMath.GRID);
			if ((l&1) != 0) l++;
			hYAll = l / DBMath.GRID;

			bound = ERectangle.fromLambda(lXAll, lYAll, hXAll-lXAll, hYAll-lYAll);
		}

		public double getAreaWithOtherShiftedCluster(ProxyCluster other, EPoint delta)
		{
			double lXAll=bound.getMinX(), hXAll=bound.getMaxX(), lYAll=bound.getMinY(), hYAll=bound.getMaxY();
			for(int i=0; i<other.getNodesInCluster().size(); i++)
			{
				ProxyNode pn = other.getNodesInCluster().get(i);
				double lX = pn.getX() + delta.getX() - pn.getWidth()/2;
				double hX = lX + pn.getWidth();
				double lY = pn.getY() + delta.getY() - pn.getHeight()/2;
				double hY = lY + pn.getHeight();
				if (lX < lXAll) lXAll = lX;
				if (hX > hXAll) hXAll = hX;
				if (lY < lYAll) lYAll = lY;
				if (hY > hYAll) hYAll = hY;
			}
			double area = (hXAll - lXAll) * (hYAll - lYAll);
			return area;
		}

		public void rotate(Orientation spin)
		{
			if (spin == Orientation.IDENT) return;
			for(ProxyNode pn : nodesInCluster)
			{
				double ox = pn.getX() - bound.getCenterX();
				double oy = pn.getY() - bound.getCenterY();
				Point2D newOff = spin.transformPoint(new Point2D.Double(ox, oy));
				pn.setLocation(newOff.getX() + bound.getCenterX(), newOff.getY() + bound.getCenterY());
				pn.changeOrientation(spin);
			}
			computeBounds();
		}

		public void add(ProxyNode pNode)
		{
			nodesInCluster.add(pNode);
			ERectangle b = ERectangle.fromLambda(pNode.getX() - pNode.getWidth()/2,
				pNode.getY() - pNode.getHeight()/2, pNode.getWidth(), pNode.getHeight());
			if (nodesInCluster.size() == 1)
			{
				bound = b;
			} else
			{
				bound = (ERectangle)bound.createUnion(b);
			}
		}

		@Override
		public String toString()
		{
			String str = "CLUSTER";
			String sep = " ";
			for(ProxyNode pNode : nodesInCluster)
			{
				str += sep + pNode.getNodeName();
				sep = "/";
			}
			return str;
		}
	}

	/*************************************** Proxy Node **********************************/

	/**
	 * This class is a proxy for the actual PlacementNode.
	 * It can be moved around and rotated without touching the actual PlacementNode.
	 */
	private class ProxyNode implements RTBounds
	{
		// current state
		private double x, y;						// current location
		private Orientation orientation;			// orientation of the placement node
		private double width, height;				// height of the placement node

		// saved values for temporary adjustments
		private double xSave, ySave;				// current location
		private Orientation orientationSave;		// orientation of the placement node
		private double widthSave, heightSave;		// height of the placement node

		private ERectangle bounds;
		private PlacementNode original;

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
			computeBounds();
		}

		/**
		 * Constructor to duplicate a ProxyNode.
		 * @param pn the ProxyNode to copy.
		 */
		public ProxyNode(ProxyNode pn)
		{
			x = pn.x;
			y = pn.y;
			orientation = pn.orientation;
			width = pn.width;
			height = pn.height;
			original = pn.original;
			computeBounds();
		}

		@Override
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

		public void setLocation(double x, double y)
		{
			this.x = DBMath.round(x);
			this.y = DBMath.round(y);
			computeBounds();
		}

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

		public void changeOrientation(Orientation delta)
		{
			Orientation newOrientation = delta.concatenate(orientation);
			int deltaAng = Math.abs(orientation.getAngle() - newOrientation.getAngle());
			if (deltaAng == 900 || deltaAng == 2700)
			{
				double swap = width;   width = height;   height = swap;
				computeBounds();
			}
			orientation = newOrientation;
		}

		@Override
		public ERectangle getBounds() { return bounds; }

		private void computeBounds()
		{
			bounds = ERectangle.fromLambda(x - width/2, y - height/2, width, height);
		}

		// ----------------------------- MISCELLANEOUS -----------------------------

		public void saveValues()
		{
			xSave = x;   ySave = y;
			orientationSave = orientation;
			widthSave = width;   heightSave = height;
		}

		public void restoreValues()
		{
			x = xSave;   y = ySave;
			orientation = orientationSave;
			width = widthSave;   height = heightSave;
			computeBounds();
		}
	}

	/* ********************************************* DEBUGGING ********************************************* */

	private static final int INDENTPOLY = 5;
	private static int showIndex = 0;
	private void showProposal(ProposedPlacement pp)
	{
		showIndex++;
		Cell cell = Cell.newInstance(Library.getCurrent(), "DEBUG"+showIndex);
		Point2D fromPt = new Point2D.Double(0, 0);
		Point2D toPt = new Point2D.Double(0, 0);
		ProxyNode fromPN = pp.proxyMap.get(pp.movedPair.n1);
		ProxyNode toPN = pp.proxyMap.get(pp.movedPair.n2);
		for(ProxyNode pn : pp.nodesToPlace)
		{
			NodeInst ni = ((PlacementAdapter.PlacementNode)pn.original).getOriginal();
			double width = ni.getProto().getDefWidth(ep);
			double height = ni.getProto().getDefHeight(ep);
			double xPos = pn.getX();
			double yPos = pn.getY();
			if (ni.isCellInstance())
			{
				Cell placementCell = (Cell)ni.getProto();
				Rectangle2D bounds = placementCell.getBounds();
				Point2D centerOffset = new Point2D.Double(bounds.getCenterX(), bounds.getCenterY());
				pn.getOrientation().pureRotate().transform(centerOffset, centerOffset);
				xPos -= centerOffset.getX();
				yPos -= centerOffset.getY();
			}
			EPoint ctr = EPoint.fromLambda(xPos, yPos);
			if (pn == fromPN) fromPt = ctr;
			if (pn == toPN) toPt = ctr;
			NodeInst newNI = NodeInst.makeInstance(ni.getProto(), ep, ctr, width, height, cell, pn.getOrientation(), ni.getName());
			TextDescriptor td = ep.getNodeTextDescriptor().withDisplay(true).withRelSize(20);
			newNI.setTextDescriptor(NodeInst.NODE_NAME, td);
		}

		// show all clusters
		for(ProxyCluster cluster : pp.allClusters)
		{
			if (cluster.getNodesInCluster().size() <= 1) continue;
			PolyMerge merge = new PolyMerge();
			for(ProxyNode pn : cluster.getNodesInCluster())
			{
				Poly poly = new Poly(pn.getBounds());
				merge.add(Artwork.tech().defaultLayer, poly);
			}
			List<PolyBase> polys = merge.getMergedPoints(Artwork.tech().defaultLayer, true);
			for(PolyBase pb : polys)
			{
				Point2D [] oldPts = pb.getPoints();
				EPoint [] newPts = new EPoint[oldPts.length];
				double cX = pb.getCenterX();
				double cY = pb.getCenterY();
				for(int i=0; i<oldPts.length; i++)
				{
					double nX = oldPts[i].getX();
					double nY = oldPts[i].getY();
					if (nX < cX) nX += INDENTPOLY; else
						if (nX > cX) nX -= INDENTPOLY;
					if (nY < cY) nY += INDENTPOLY; else
						if (nY > cY) nY -= INDENTPOLY;
					newPts[i] = EPoint.fromLambda(nX, nY);
				}
				NodeInst newNI = NodeInst.makeInstance(Artwork.tech().openedPolygonNode, ep, EPoint.fromLambda(cX, cY),
					pb.getBounds2D().getWidth()-INDENTPOLY*2, pb.getBounds2D().getHeight()-INDENTPOLY*2, cell);
				newNI.setTrace(newPts);
				newNI.newVar(Artwork.ART_COLOR, new Integer(EGraphics.RED), ep);
			}
		}

		// show what was moved
		NodeInst arrowHead = NodeInst.makeInstance(Artwork.tech().pinNode, ep, fromPt, 0, 0, cell);
		NodeInst arrowTail = NodeInst.makeInstance(Artwork.tech().pinNode, ep, toPt, 0, 0, cell);
		ArcInst ai = ArcInst.makeInstance(Artwork.tech().thickerArc, ep, arrowHead.getOnlyPortInst(), arrowTail.getOnlyPortInst());
		ai.newVar(Artwork.ART_COLOR, new Integer(EGraphics.GREEN), ep);
		int angle = DBMath.figureAngle(toPt, fromPt);
		int ang1 = (angle + 450) % 3600;
		int ang2 = (angle + 3150) % 3600;
		double cX = (fromPt.getX() + toPt.getX()) / 2;
		double cY = (fromPt.getY() + toPt.getY()) / 2;
		double len = DBMath.distBetweenPoints(fromPt, toPt) / 5;
		double x1 = cX + DBMath.cos(ang1) * len;
		double y1 = cY + DBMath.sin(ang1) * len;
		double x2 = cX + DBMath.cos(ang2) * len;
		double y2 = cY + DBMath.sin(ang2) * len;
		NodeInst arrowCtr = NodeInst.makeInstance(Artwork.tech().pinNode, ep, EPoint.fromLambda(cX, cY), 0, 0, cell);
		NodeInst arrowEnd1 = NodeInst.makeInstance(Artwork.tech().pinNode, ep, EPoint.fromLambda(x1, y1), 0, 0, cell);
		NodeInst arrowEnd2 = NodeInst.makeInstance(Artwork.tech().pinNode, ep, EPoint.fromLambda(x2, y2), 0, 0, cell);
		ai = ArcInst.makeInstance(Artwork.tech().thickerArc, ep, arrowCtr.getOnlyPortInst(), arrowEnd1.getOnlyPortInst());
		ai.newVar(Artwork.ART_COLOR, new Integer(EGraphics.GREEN), ep);
		ai = ArcInst.makeInstance(Artwork.tech().thickerArc, ep, arrowCtr.getOnlyPortInst(), arrowEnd2.getOnlyPortInst());
		ai.newVar(Artwork.ART_COLOR, new Integer(EGraphics.GREEN), ep);

		// write text at top describing what happened
		double x = cell.getBounds().getCenterX();
		double y = cell.getBounds().getMaxY() + 10;
		PrimitiveNode np = Generic.tech().invisiblePinNode;
		for(int i=pp.furtherExplanation.size()-1; i >= 0; i--)
		{
			NodeInst ni = NodeInst.makeInstance(np, ep,EPoint.fromLambda(x, y), 0, 0, cell);
			TextDescriptor td = ep.getAnnotationTextDescriptor().withDisplay(true).withRelSize(10);
			ni.newVar(Artwork.ART_MESSAGE, pp.furtherExplanation.get(i), td);
			y += 10;
		}
		NodeInst ni = NodeInst.makeInstance(np, ep, EPoint.fromLambda(x, y+5), 0, 0, cell);
		String msg = "Proposal "+pp.proposalNumber+" from parent proposal "+pp.parentProposalNumber;
		TextDescriptor td = ep.getAnnotationTextDescriptor().withDisplay(true).withRelSize(15);
		ni.newVar(Artwork.ART_MESSAGE, msg, td);
	}
}
