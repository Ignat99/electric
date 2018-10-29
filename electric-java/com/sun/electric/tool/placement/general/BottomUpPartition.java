/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: BottomUpPartition.java
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

import com.sun.electric.database.geometry.EPoint;
import com.sun.electric.database.geometry.ERectangle;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.Export;
import com.sun.electric.database.hierarchy.Library;
import com.sun.electric.database.topology.ArcInst;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.topology.PortInst;
import com.sun.electric.database.topology.SteinerTree.SteinerTreePortPair;
import com.sun.electric.technology.technologies.Generic;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.placement.PlacementAdapter;
import com.sun.electric.tool.placement.PlacementAdapter.PlacementExport;
import com.sun.electric.tool.placement.PlacementFrameElectric;
import com.sun.electric.util.math.MutableDouble;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Implementation of the bottom-up partition placement algorithm.
 */
public class BottomUpPartition extends PlacementFrameElectric
{
	/**
	 * Method to return the name of this placement algorithm.
	 * @return the name of this placement algorithm.
	 */
    @Override
	public String getAlgorithmName() { return "Bottom-Up-Partition"; }

	/**
	 * Method to do placement by simulated annealing.
	 * @param placementNodes a list of all nodes that are to be placed.
	 * @param allNetworks a list of all networks that connect the nodes.
	 * @param cellName the name of the cell being placed.
	 * @param job the Job (for testing abort).
	 */
    @Override
	public void runPlacement(List<PlacementNode> placementNodes, List<PlacementNetwork> allNetworks,
		List<PlacementExport> exportsToPlace, String cellName, Job job)
	{
		doBottomUp(placementNodes, allNetworks, exportsToPlace);

		// set "failure" so that the original cell doesn't get rebuilt
		setFailure(true);
	}

	public Library doBottomUp(List<PlacementNode> placementNodes, List<PlacementNetwork> allNetworks,
		List<PlacementExport> exportsToPlace)
	{
		// look for macro cells
		double girth = getStandardCellSize(placementNodes, null);
		double minMacroArea = girth * girth * 100;
		Set<PlacementNode> macroNodes = new HashSet<PlacementNode>();
		for(PlacementNode pNode : placementNodes)
		{
			if (pNode.getWidth()*pNode.getHeight() >= minMacroArea) macroNodes.add(pNode);
		}
		System.out.println("Found " + macroNodes.size() + " macro cells");

		// organize the nodes by the density of connections
		Map<Double,List<PNPair>> allDensities = makeClusteredPairs(placementNodes, allNetworks, macroNodes);
		System.out.println("Created cell clustering");

		int di = allDensities.size();
		double[] densities = new double[di];
		for(Double density : allDensities.keySet()) densities[--di] = density.doubleValue();

		// now scan the connectivity map and cluster the densest connections
		int maxClusterSize = (int)Math.ceil(Math.sqrt(placementNodes.size()));
		Map<PlacementNode,FPCluster> nodeClusters = new HashMap<PlacementNode,FPCluster>();
		List<FPCluster> allClusters = new ArrayList<FPCluster>();
		for(int i=0; i<densities.length; i++)
		{
			double curDensity = densities[i];
			List<PNPair> pairs = allDensities.get(new Double(curDensity));
			for(PNPair pair : pairs)
			{
				// cluster these two nodes
				PlacementNode pNode = pair.n1;
				PlacementNode oNode = pair.n2;
				FPCluster pCluster = nodeClusters.get(pNode);
				FPCluster oCluster = nodeClusters.get(oNode);
				if (pCluster == null && oCluster == null)
				{
					pCluster = new FPCluster();
					allClusters.add(pCluster);
					pCluster.addNode(pNode);
					pCluster.addNode(oNode);
					nodeClusters.put(pNode, pCluster);
					nodeClusters.put(oNode, pCluster);
				} else if (pCluster == null && oCluster != null)
				{
					oCluster.addNode(pNode);
					nodeClusters.put(pNode, oCluster);
				} else if (pCluster != null && oCluster == null)
				{
					pCluster.addNode(oNode);
					nodeClusters.put(oNode, pCluster);
				} else
				{
					if (pCluster == oCluster) continue;

					// merge the clusters?
					if (pCluster.getSize() + oCluster.getSize() < maxClusterSize)
					{
						if (pCluster.getSize() < oCluster.getSize())
						{
							FPCluster swap = pCluster;   pCluster = oCluster;   oCluster = swap;
						}
						for(PlacementNode pn : oCluster.getMembers())
						{
							pCluster.addNode(pn);
							nodeClusters.put(pn, pCluster);
						}
						allClusters.remove(oCluster);
					}
				}
			}
		}

		// make a list of nodes that were not clustered
		List<PlacementNode> unconnectedNodes = new ArrayList<PlacementNode>();
		for(PlacementNode pn : placementNodes)
		{
			if (macroNodes.contains(pn)) continue;
			FPCluster cluster = nodeClusters.get(pn);
			if (cluster == null) unconnectedNodes.add(pn);
		}

		// create new library for restructured cell
		Library lib = null;
		for(int i=1; i<1000; i++)
		{
			String libName = "PlacementResult" + i;
			if (Library.findLibrary(libName) == null)
			{
				lib = Library.newInstance(libName, null);
				break;
			}
		}

		// build cluster cells
		Map<PlacementNetwork,String> exportNames = new HashMap<PlacementNetwork,String>();
		int exportNum = 1;
		List<Cell> clusterCells = new ArrayList<Cell>();
		System.out.println("Generating top-level cell and " + allClusters.size() + " intermediate cells");
		for(int i=0; i<=allClusters.size(); i++)
		{
			Cell newCell;
			Collection<PlacementNode> nodesToPlace;
			String cellName;
			if (i == allClusters.size())
			{
				// building cluster cell for unclustered nodes
				cellName = "UNCLUSTERED{lay}";
				nodesToPlace = unconnectedNodes;
			} else
			{
				// build a regular cluster cell
				cellName = "CLUSTER"+(i+1)+"{lay}";
				nodesToPlace = allClusters.get(i).getMembers();
			}
			if (nodesToPlace.size() == 0) continue;
			newCell = Cell.makeInstance(ep, lib, cellName);
			clusterCells.add(newCell);

			// place all nodes in the cluster cell
			Map<PlacementNode,NodeInst> generatedNodes = new HashMap<PlacementNode,NodeInst>();
			placeList(nodesToPlace, newCell, generatedNodes);

			// keep track of networks used on these nodes
			Set<PlacementNetwork> usedNetworks = new HashSet<PlacementNetwork>();
			for(PlacementNode pn : nodesToPlace)
			{
				for(PlacementPort port : pn.getPorts())
				{
					PlacementNetwork pNet = port.getPlacementNetwork();
					if (pNet != null) usedNetworks.add(pNet);
				}
			}

			// now wire the cluster cell internally
			for(PlacementNetwork pNet : usedNetworks)
			{
				PortInst lastPI = null;
				boolean exportNet = false;
				for(PlacementPort pPort : pNet.getPortsOnNet())
				{
					PlacementNode pNode = pPort.getPlacementNode();
					NodeInst ni = generatedNodes.get(pNode);
					if (ni == null) { exportNet = true;  continue; }
					PlacementAdapter.PlacementPort app = (PlacementAdapter.PlacementPort)pPort;
					PortInst pi = ni.findPortInstFromEquivalentProto(app.getPortProto());
					if (lastPI != null)
						ArcInst.makeInstance(Generic.tech().unrouted_arc, ep, lastPI, pi);
					lastPI = pi;
				}
				if (exportNet)
				{
					// network goes outside of cell: export it
					for(PlacementExport pe : exportsToPlace)
					{
						if (pe.getPort().getPlacementNetwork() == pNet)
						{
							exportsToPlace.remove(pe);
							break;
						}
					}
					String exportName = exportNames.get(pNet);
					if (exportName == null)
					{
						exportName = "E" + exportNum;
						exportNum++;
						exportNames.put(pNet, exportName);
					}
					Export.newInstance(newCell, lastPI, exportName, ep);
				}
			}

			for(PlacementExport pe : exportsToPlace)
			{
				PlacementPort pp = pe.getPort();
				NodeInst ni = generatedNodes.get(pp.getPlacementNode());
				if (ni == null) continue;
				PlacementAdapter.PlacementPort app = (PlacementAdapter.PlacementPort)pp;
				PortInst pi = ni.findPortInstFromEquivalentProto(app.getPortProto());
				Export.newInstance(newCell, pi, pe.getName(), ep);
			}
		}

		// make the top-level cell with all cluster instances
		Cell newCell = Cell.makeInstance(ep, lib, "ALLCLUSTERS{lay}");

		// place all macro cells in the top-level cell
		Map<PlacementNode,NodeInst> placedMacroCells = new HashMap<PlacementNode,NodeInst>();
		placeList(macroNodes, newCell, placedMacroCells);

		// place cluster instances in the top-level cell
		double gap = 5;
		Map<Cell,NodeInst> clusterInstances = new HashMap<Cell,NodeInst>();
		int cellsPerRow = (int)Math.sqrt(clusterCells.size());
		double xPos = 0, yPos = 0;
		double maxHeight = 0;
		int cellNum = 0;
		for(Cell subCell : clusterCells)
		{
			ERectangle rect = subCell.getBounds();
			EPoint ctr = makeEPoint(xPos-rect.getCenterX()+subCell.getDefWidth()/2,
				yPos-rect.getCenterY()+subCell.getDefHeight()/2);
			NodeInst ni = NodeInst.makeInstance(subCell, ep, ctr, subCell.getDefWidth(), subCell.getDefHeight(), newCell);
			clusterInstances.put(subCell, ni);

			// update location of next instance
			xPos += rect.getWidth() + gap;
			if (rect.getHeight() > maxHeight) maxHeight = rect.getHeight();
			cellNum++;
			if (cellNum >= cellsPerRow)
			{
				cellNum = 0;
				xPos = 0;
				yPos += maxHeight + gap;
				maxHeight = 0;
			}
		}

		// wire the top-level cell
		for(PlacementNetwork pNet : exportNames.keySet())
		{
			String exportName = exportNames.get(pNet);
			PortInst lastPI = null;
			for(Cell subCell : clusterCells)
			{
				Export e = subCell.findExport(exportName);
				if (e == null) continue;
				NodeInst ni = clusterInstances.get(subCell);
				PortInst pi = ni.findPortInstFromEquivalentProto(e);
				if (lastPI != null)
					ArcInst.makeInstance(Generic.tech().unrouted_arc, ep, lastPI, pi);
				lastPI = pi;
			}
			for(PlacementPort pPort : pNet.getPortsOnNet())
			{
				PlacementNode pNode = pPort.getPlacementNode();
				if (!macroNodes.contains(pNode)) continue;
				NodeInst ni = placedMacroCells.get(pNode);
				PlacementAdapter.PlacementPort app = (PlacementAdapter.PlacementPort)pPort;
				PortInst pi = ni.findPortInstFromEquivalentProto(app.getPortProto());
				if (lastPI != null)
					ArcInst.makeInstance(Generic.tech().unrouted_arc, ep, lastPI, pi);
				lastPI = pi;
			}
		}

		// also wire macro cells together (when they don't connect to a cluster cell)
		Set<PlacementNetwork> stillToWire = new HashSet<PlacementNetwork>();
		for(PlacementNode pNode : macroNodes)
		{
			for(PlacementPort pPort : pNode.getPorts())
			{
				PlacementNetwork pNet = pPort.getPlacementNetwork();
				if (pNet != null && exportNames.get(pNet) == null) stillToWire.add(pNet);
			}
		}
		for(PlacementNetwork pNet : stillToWire)
		{
			PortInst lastPI = null;
			for(PlacementPort pPort : pNet.getPortsOnNet())
			{
				PlacementNode pNode = pPort.getPlacementNode();
				if (!macroNodes.contains(pNode)) continue;
				NodeInst ni = placedMacroCells.get(pNode);
				PlacementAdapter.PlacementPort app = (PlacementAdapter.PlacementPort)pPort;
				PortInst pi = ni.findPortInstFromEquivalentProto(app.getPortProto());
				if (lastPI != null)
					ArcInst.makeInstance(Generic.tech().unrouted_arc, ep, lastPI, pi);
				lastPI = pi;
			}
		}
		return lib;
	}

private static final boolean NEWCLUSTERMETHOD = true;

	public static Map<Double,List<PNPair>> makeClusteredPairs(List<PlacementNode> placementNodes,
		List<PlacementNetwork> allNetworks, Set<PlacementNode> macroNodes)
	{
		// make ordering map so that pairs are added only once
		Map<PlacementNode,Integer> ordering = new HashMap<PlacementNode,Integer>();
		for(int i=0; i<placementNodes.size(); i++) ordering.put(placementNodes.get(i), Integer.valueOf(i));

		// compute the area of the largest and smallest nodes
		double largestArea = 0, smallestArea = Double.MAX_VALUE;
		for(PlacementNode pn : placementNodes)
		{
			double area = pn.getWidth() * pn.getHeight();
			if (area < smallestArea) smallestArea = area;
			if (area > largestArea) largestArea = area;
		}

		// build a connectivity map showing density of connections between all non-macro nodes
		Map<PlacementNode,Map<PlacementNode,MutableDouble>> connectivityMap;
		connectivityMap = new HashMap<PlacementNode,Map<PlacementNode,MutableDouble>>();
		Map<PlacementNetwork,List<SteinerTreePortPair>> allConnections = new HashMap<PlacementNetwork,List<SteinerTreePortPair>>();
		Job.getUserInterface().startProgressDialog("Computing steiner trees", null);
		Job.getUserInterface().setProgressValue(0);
		int totalNetworks = allNetworks.size();
		for(int i=0; i<totalNetworks; i++)
		{
			PlacementNetwork pNet = allNetworks.get(i);
			allConnections.put(pNet, PlacementAdapter.getOptimalConnections(pNet));
			if ((i % 100) == 99)
				Job.getUserInterface().setProgressValue(i * 100 / totalNetworks);
		}
		Job.getUserInterface().stopProgressDialog();

		if (NEWCLUSTERMETHOD)
		{
			// the new way
			for(PlacementNetwork pNet : allNetworks)
			{
				List<SteinerTreePortPair> cons = allConnections.get(pNet);
				for(SteinerTreePortPair con : cons)
				{
					PlacementPort pPort = (PlacementPort)con.getPort1();
					PlacementNode pNode = pPort.getPlacementNode();
					if (macroNodes.contains(pNode)) continue;
					PlacementPort oPort = (PlacementPort)con.getPort2();
					PlacementNode oNode = oPort.getPlacementNode();
					if (macroNodes.contains(oNode)) continue;
					if (oNode == pNode) continue;

					// order them properly
					Integer pNodeIndex = ordering.get(pNode);
					Integer oNodeIndex = ordering.get(oNode);
					PlacementNode realPNode, realONode;
					if (pNodeIndex.intValue() < oNodeIndex.intValue())
					{
						realPNode = pNode;
						realONode = oNode;
					} else
					{
						realPNode = oNode;
						realONode = pNode;
					}

					// figure out the ratio of connections exclusively between these two nodes
					int totalMatches = 0, partialMatches = 0;
					Set<PlacementNetwork> possibleNetworks = new HashSet<PlacementNetwork>();
					for(PlacementPort pNet2 : realPNode.getPorts())
						possibleNetworks.add(pNet2.getPlacementNetwork());
					for(PlacementPort pNet2 : realONode.getPorts())
						possibleNetworks.add(pNet2.getPlacementNetwork());
					for(PlacementNetwork pNet2 : possibleNetworks)
					{
						List<SteinerTreePortPair> cons2 = allConnections.get(pNet2);
						if (cons2 == null) continue;
						for(SteinerTreePortPair con2 : cons2)
						{
							PlacementPort pPort1 = (PlacementPort)con2.getPort1();
							PlacementNode pNode1 = pPort1.getPlacementNode();
							PlacementPort pPort2 = (PlacementPort)con2.getPort2();
							PlacementNode pNode2 = pPort2.getPlacementNode();
							if (pNode2 == pNode1) continue;
							boolean match1 = (pNode1 == pNode) || (pNode1 == oNode);
							boolean match2 = (pNode2 == pNode) || (pNode2 == oNode);
							if (match1 && match2) totalMatches++; else
								if (match1 || match2) partialMatches++;
						}
					}

					// remember connection between nodes
					Map<PlacementNode,MutableDouble> nodeMap = connectivityMap.get(realPNode);
					if (nodeMap == null) connectivityMap.put(realPNode, nodeMap = new HashMap<PlacementNode,MutableDouble>());
					MutableDouble count = nodeMap.get(realONode);
					if (count == null) nodeMap.put(realONode, count = new MutableDouble(0));
					double amt = 1.0 / pNet.getPortsOnNet().size();
					if (partialMatches > 0)
					{
						double matchWeight = (double)totalMatches / (double)partialMatches;
						amt *= matchWeight;
					}
//					double pSizeRatio = (realPNode.getWidth() * realPNode.getHeight()) / largestArea;
//					double oSizeRatio = (realONode.getWidth() * realONode.getHeight()) / largestArea;
//					amt *= pSizeRatio * oSizeRatio;
					count.setValue(count.doubleValue() + amt);
				}
			}
		} else
		{
			// the old way
			for(PlacementNetwork pNet : allNetworks)
			{
				for(PlacementPort pPort : pNet.getPortsOnNet())
				{
					PlacementNode pNode = pPort.getPlacementNode();
					if (macroNodes.contains(pNode)) continue;
					Integer pNodeIndex = ordering.get(pNode);
					for(PlacementPort oPort : pNet.getPortsOnNet())
					{
						if (oPort == pPort) continue;
						PlacementNode oNode = oPort.getPlacementNode();
						if (oNode == pNode) continue;
						if (macroNodes.contains(oNode)) continue;

						// order them properly
						Integer oNodeIndex = ordering.get(oNode);
						PlacementNode realPNode, realONode;
						if (pNodeIndex.intValue() < oNodeIndex.intValue())
						{
							realPNode = pNode;
							realONode = oNode;
						} else
						{
							realPNode = oNode;
							realONode = pNode;
						}

						// remember connection between nodes
						Map<PlacementNode,MutableDouble> nodeMap = connectivityMap.get(realPNode);
						if (nodeMap == null) connectivityMap.put(realPNode, nodeMap = new HashMap<PlacementNode,MutableDouble>());
						MutableDouble count = nodeMap.get(realONode);
						if (count == null) nodeMap.put(realONode, count = new MutableDouble(0));
						double amt = 1.0 / pNet.getPortsOnNet().size();
						count.setValue(count.doubleValue() + amt);
					}
				}
			}
		}

		// now scan the connectivity map and find the ordering of density
		Map<Double,List<PNPair>> allDensities = new TreeMap<Double,List<PNPair>>();
		for(PlacementNode pNode : connectivityMap.keySet())
		{
			Map<PlacementNode,MutableDouble> nodeMap = connectivityMap.get(pNode);
			for(PlacementNode oNode : nodeMap.keySet())
			{
				MutableDouble md = nodeMap.get(oNode);
				Double key = new Double(md.doubleValue());
				List<PNPair> pairs = allDensities.get(key);
				if (pairs == null) allDensities.put(key, pairs = new ArrayList<PNPair>());
				pairs.add(new PNPair(pNode, oNode));
			}
		}
		for(Double d : allDensities.keySet())
		{
			List<PNPair> pairs = allDensities.get(d);
			Collections.sort(pairs, new PNPairOrdering());
		}

		return allDensities;
	}

    public static class PNPairOrdering implements Comparator<PNPair>
    {
        /**
         * Method to sort PNPair Objects by their ordering.
         */
        @Override
        public int compare(PNPair pnpA, PNPair pnpB)
        {
			PlacementAdapter.PlacementNode panA1 = (PlacementAdapter.PlacementNode)pnpA.n1;
			NodeInst niA1 = panA1.getOriginal();
			PlacementAdapter.PlacementNode panB1 = (PlacementAdapter.PlacementNode)pnpB.n1;
			NodeInst niB1 = panB1.getOriginal();
        	int diff = niA1.getName().compareTo(niB1.getName());
        	if (diff != 0) return diff;

			PlacementAdapter.PlacementNode panA2 = (PlacementAdapter.PlacementNode)pnpA.n2;
			NodeInst niA2 = panA2.getOriginal();
			PlacementAdapter.PlacementNode panB2 = (PlacementAdapter.PlacementNode)pnpB.n2;
			NodeInst niB2 = panB2.getOriginal();
        	diff = niA2.getName().compareTo(niB2.getName());
            return diff;
        }
    }

	/**
	 * Method to place instances inside of a cell.
	 * @param nodesToPlace the instances to place.
	 * @param cell the cell in which to place the instances.
	 * @param assignment a map to load with PlacementNode to NodeInst connections (can be null).
	 */
	private void placeList(Collection<PlacementNode> nodesToPlace, Cell cell, Map<PlacementNode,NodeInst> assignment)
	{
		double maxWid = 0, maxHei = 0;
		for(PlacementNode pNode : nodesToPlace)
		{
			PlacementAdapter.PlacementNode apn = (PlacementAdapter.PlacementNode)pNode;
			NodeInst ni = apn.getOriginal();
			boolean flipped = false;
			if (ni.getOrient().isXMirrored() == ni.getOrient().isYMirrored())
			{
				if (ni.getOrient().getAngle() == 90 || ni.getOrient().getAngle() == 2700) flipped = true;
			} else
			{
				if (ni.getOrient().getAngle() == 0 || ni.getOrient().getAngle() == 1800) flipped = true;
			}
			if (flipped)
			{
				if (pNode.getWidth() > maxHei) maxHei = pNode.getWidth();
				if (pNode.getHeight() > maxWid) maxWid = pNode.getHeight();
			} else
			{
				if (pNode.getWidth() > maxWid) maxWid = pNode.getWidth();
				if (pNode.getHeight() > maxHei) maxHei = pNode.getHeight();
			}
		}
		double spacing = Math.max(maxWid+1, maxHei+1);

		// place cluster instances in the top-level cell
		int cellsPerRow = (int)Math.sqrt(nodesToPlace.size());
		double xPos = 0, yPos = 0;
		int cellNum = 0;
		for(PlacementNode pNode : nodesToPlace)
		{
			PlacementAdapter.PlacementNode apn = (PlacementAdapter.PlacementNode)pNode;
			NodeInst ni = apn.getOriginal();
			ERectangle rect;
			if (ni.isCellInstance()) rect = ((Cell)ni.getProto()).getBounds(); else
			{
				rect = ni.getBounds();
//System.out.println("PRIMITIVE "+ni.describe(false)+" HAS BOUNDS "+rect.getWidth()+"x"+rect.getHeight()+" AND SIZE "+ni.getXSize()+"x"+ni.getYSize());
			}
			EPoint ctr = makeEPoint(xPos-rect.getCenterX(), yPos-rect.getCenterY());
			NodeInst newNi = NodeInst.makeInstance(ni.getProto(), ep, ctr, ni.getXSize(), ni.getYSize(),
				cell, ni.getOrient(), ni.getName());
			if (assignment != null) assignment.put(pNode, newNi);

			// update location of next instance
			xPos += spacing;
			cellNum++;
			if (cellNum >= cellsPerRow)
			{
				cellNum = 0;
				xPos = 0;
				yPos += spacing;
			}
		}
	}

	/**
	 * Method to construct an EPoint from coordinates.
	 * Clips the coordinates if necessary.
	 * @param x the X coordinate.
	 * @param y the Y coordinate.
	 * @return an EPoint with those coordinates.
	 */
	private EPoint makeEPoint(double x, double y)
	{
		double maxValue = Math.round(Integer.MAX_VALUE / 500);
		if (x >= maxValue) { System.out.println("X VALUE CLIPPED FROM "+x+" TO "+maxValue);   x = maxValue; }
		if (x <= -maxValue) { System.out.println("X VALUE CLIPPED FROM "+x+" TO "+(-maxValue));   x = -maxValue; }
		if (y >= maxValue) { System.out.println("Y VALUE CLIPPED FROM "+y+" TO "+maxValue);   y = maxValue; }
		if (y <= -maxValue) { System.out.println("Y VALUE CLIPPED FROM "+y+" TO "+(-maxValue));   y = -maxValue; }
		try
		{
			return EPoint.fromLambda(x, y);
		} catch (IllegalArgumentException e)
		{
			System.out.println("ERROR: bad instance location (" + x + "," + y + "): " + e.getMessage());
		}
		return EPoint.fromLambda(0, 0);
	}

	/**
	 * Class to hold two PlacementNodes that should be clustered together.
	 */
	public static class PNPair
	{
		PlacementNode n1, n2;
		PNPair(PlacementNode pn1, PlacementNode pn2)
		{
			PlacementAdapter.PlacementNode pan1 = (PlacementAdapter.PlacementNode)pn1;
			NodeInst ni1 = pan1.getOriginal();
			PlacementAdapter.PlacementNode pan2 = (PlacementAdapter.PlacementNode)pn2;
			NodeInst ni2 = pan2.getOriginal();
			if (ni1.getName().compareTo(ni2.getName()) < 0)
			{
				n1 = pn1;   n2 = pn2;
			} else
			{
				n1 = pn2;   n2 = pn1;
			}
		}
	}

	/**
	 * Class to represent a collection of PlacementNodes that will be clustered together.
	 */
	private static class FPCluster
	{
		private Set<PlacementNode> nodesInCluster;

		FPCluster()
		{
			nodesInCluster = new HashSet<PlacementNode>();
		}

		Set<PlacementNode> getMembers() { return nodesInCluster; }

		int getSize() { return nodesInCluster.size(); }

		void addNode(PlacementNode pn) { nodesInCluster.add(pn); }
	}

}
