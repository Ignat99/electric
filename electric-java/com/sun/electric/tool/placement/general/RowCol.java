/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: RowCol.java
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

import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.prototype.NodeProto;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.placement.PlacementAdapter;
import com.sun.electric.tool.placement.PlacementFrame;
import com.sun.electric.tool.placement.PlacementAdapter.PlacementExport;
import com.sun.electric.util.math.Orientation;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

/**
 * Parent class for placement algorithms that do row/column placement.
 */
public class RowCol extends PlacementFrame
{
	/** list of proxy nodes */												protected List<ProxyNode> nodesToPlace;
	/** map from original PlacementNodes to proxy nodes */					protected Map<PlacementNode,ProxyNode> proxyMap;
	/** true if doing column placement */									protected boolean columnPlacement;
	/** number of stacks of cells */										protected int numStacks;
	/** the contents of the stacks */										protected List<ProxyNode>[] stackContents;
	/** the height (of columns) or width (of rows) */						protected double[] stackSizes;
	/** X coordinates (of columns) or Y coordinates (of rows) */			protected double[] stackCoords;
	/** indicator of stack usage in a thread */								protected boolean[] stacksBusy;
	/** true to even stacks after placement */								protected boolean makeStacksEven;
	/** true to flip alternate stacks */									protected boolean flipAlternateColsRows;
	private Random randNum = new Random();

	/**
	 * Method to return the name of this placement algorithm.
	 * @return the name of this placement algorithm.
	 */
	public String getAlgorithmName() { return "?"; }

	/**
	 * Method to do placement by simulated annealing.
	 * @param placementNodes a list of all nodes that are to be placed.
	 * @param allNetworks a list of all networks that connect the nodes.
	 * @param cellName the name of the cell being placed.
	 */
	public void runPlacement(List<PlacementNode> placementNodes, List<PlacementNetwork> allNetworks, List<PlacementExport> exportsToPlace,
		String cellName, Job job)
	{
		// determine whether this is row or column placement
		makeStacksEven = getBooleanParam("makeStacksEven");
		flipAlternateColsRows = getBooleanParam("flipColRow");
		Set<Double> widths = new TreeSet<Double>();
		Set<Double> heights = new TreeSet<Double>();
		Boolean useColumns = isColumnPlacement(placementNodes, widths, heights, false);
		if (useColumns == null)
		{
			// there are no common widths or heights: cannot do row/column placement
			System.out.println("Not all cells have a common width or height: do not know how to place.  Sorry.");
			if (widths.size() < heights.size())
			{
				System.out.print("  (Did find " + widths.size() + " common widths:");
				for(Double d : widths) System.out.print(" " + d);
				System.out.println(")");
			} else
			{
				System.out.print("  (Did find " + heights.size() + " common heights:");
				for(Double d : heights) System.out.print(" " + d);
				System.out.println(")");
			}
			setFailure(true);
			return;	
		}
		columnPlacement = useColumns.booleanValue();

		// create proxies for placement nodes and insert in lists and maps
		nodesToPlace = new ArrayList<ProxyNode>(placementNodes.size());
		proxyMap = new HashMap<PlacementNode, ProxyNode>();
		for (PlacementNode p : placementNodes)
		{
			ProxyNode proxy = new ProxyNode(p);
			nodesToPlace.add(proxy);
			proxyMap.put(p, proxy);
		}

		// create an initial layout
		initLayout();

		// do the specific row/column placement
		boolean complete = runRowColPlacement(placementNodes, allNetworks);

		// even the stacks if requested
		if (complete && makeStacksEven)
		{
			System.out.println("  Making the stacks have even height");
			evenAllStacks(allNetworks);
		}

		// apply the placement of the proxies to the actual nodes
		for (PlacementNode pn : placementNodes)
		{
			ProxyNode p = proxyMap.get(pn);
			pn.setPlacement(p.getPlacementX(), p.getPlacementY());
			pn.setOrientation(p.getPlacementOrientation());
		}
	}

	protected boolean runRowColPlacement(List<PlacementNode> placementNodes, List<PlacementNetwork> allNetworks) { return true; }

	boolean getBooleanParam(String name)
	{
		List<PlacementParameter> params = getParameters();
		for(PlacementParameter pp : params)
		{
			if (pp.getParameterName().equals(name)) return pp.getBooleanValue();
		}
		return false;
	}

	/**
	 * Method that generates an initial node placement.
	 */
	private void initLayout()
	{
		double totalSize = 0;
		double maxGirth = 0;
		for(ProxyNode plNode : nodesToPlace)
		{
			totalSize += plNode.getCellSize();
			maxGirth = Math.max(maxGirth, plNode.getCellGirth());
		}
		double avgCellSize = totalSize / nodesToPlace.size();
		double avgStackSize = Math.sqrt(totalSize * maxGirth);
		numStacks = (int)Math.round(totalSize / avgStackSize);
		int numPerStack = (int)Math.ceil(nodesToPlace.size() / (double)numStacks);
		stackCoords = new double[numStacks];
		stackContents = new List[numStacks];
		stackSizes = new double[numStacks];
		stacksBusy = new boolean[numStacks];
		for(int i=0; i<numStacks; i++)
		{
			stackContents[i] = new ArrayList<ProxyNode>();
			stackSizes[i] = 0;
			stacksBusy[i] = false;
		}

		double girthPos = -maxGirth;
		double stackPos = 0;
		int stackIndex = -1;
		for (int i = 0; i < nodesToPlace.size(); i++)
		{
			if ((i % numPerStack) == 0)
			{
				girthPos += maxGirth;
				stackIndex++;
				stackPos = 0;
				stackCoords[stackIndex] = girthPos;
			}
			ProxyNode plNode = nodesToPlace.get(i);
			Orientation o = getOrientation(stackIndex);
			if (columnPlacement) plNode.setPlacement(girthPos, stackPos, stackIndex, o, true); else
				plNode.setPlacement(stackPos, girthPos, stackIndex, o, true);
			stackPos += avgCellSize;

			List<ProxyNode> pnList = stackContents[stackIndex];
			stackSizes[stackIndex] += plNode.getCellSize();
			pnList.add(plNode);
		}

		for(int i=0; i<numStacks; i++)
		{
			List<ProxyNode> pnList = stackContents[i];
			Collections.sort(pnList);
			evenStack(i);
		}
	}

	/**
	 * Method to determine whether this is row or column placement.
	 * @param placementNodes the nodes to be placed.
	 * @return TRUE to do column placement (all nodes are fixed width);
	 * FALSE to do row placement (all nodes are fixed height);
	 * null if nodes are mixed size.
	 */
	public static Boolean isColumnPlacement(List<PlacementNode> placementNodes, Set<Double> widths, Set<Double> heights, boolean quiet)
	{
		// make sure cells have the same girth
		Double commonWid = new Double(-1), commonHei = new Double(-1);
		for(PlacementNode p : placementNodes)
		{
			PlacementAdapter.PlacementNode papn = (PlacementAdapter.PlacementNode)p;
			if (papn.getOriginal() == null)
			{
				System.out.println("Original node of '" + papn + "' not found in column placement");
				continue;
			}
			if (!papn.getOriginal().isCellInstance()) continue;
			if (widths != null) widths.add(new Double(p.getWidth()));
			if (heights != null) heights.add(new Double(p.getHeight()));
			if (commonWid != null)
			{
				if (commonWid.doubleValue() < 0) commonWid = new Double(p.getWidth());
				if (commonWid.doubleValue() != p.getWidth()) commonWid = null;
			}
			if (commonHei != null)
			{
				if (commonHei.doubleValue() < 0) commonHei = new Double(p.getHeight());
				if (commonHei.doubleValue() != p.getHeight()) commonHei = null;
			}
		}
		if (commonWid == null && commonHei == null)
		{
			// there are no common widths or heights
			return null;	
		}

		Boolean useColumns = Boolean.FALSE;
		if (commonWid != null && commonHei == null)
		{
			useColumns = Boolean.TRUE;
			if (!quiet) System.out.println("  Doing placement in columns");
		} else if (commonWid == null && commonHei != null)
		{
			if (!quiet) System.out.println("  Doing placement in rows");
		} else
		{
			if (!quiet) System.out.println("  All cells have same size: presuming row-based placement");
		}
		return useColumns;
	}

	private void evenAllStacks(List<PlacementNetwork> allNetworks)
	{
		boolean [] stackConsidered = new boolean[numStacks];
		for(;;)
		{
			// find initial network length
			double initialLength = netLength(allNetworks);
			int bestOtherStack = -1;
			int placeInOtherStack = -1;
			double bestGain = 0;
			ProxyNode nodeToMove = null;
			for(int i=0; i<numStacks; i++) stackConsidered[i] = false;

			// now look at all tall stacks and find something that can be moved out of them
			for(;;)
			{
				// find tallest stack that hasn't already been considered
				int tallestStack = -1;
				for(int i=0; i<numStacks; i++)
				{
					if (stackConsidered[i]) continue;
					if (tallestStack < 0 || stackSizes[i] > stackSizes[tallestStack])
						tallestStack = i;
				}
				if (tallestStack < 0) break;
				stackConsidered[tallestStack] = true;

				// look at all nodes in the tall stack
				for(ProxyNode pn : stackContents[tallestStack])
				{
					double size = pn.getCellSize();

					// find another stack in which this node could go to even the sizes
					for(int i=0; i<numStacks; i++)
					{
						if (i == tallestStack) continue;
						if (stackSizes[i] + size > stackSizes[tallestStack] - size) continue;

						// could go in this stack: look for the best place for it
						for(int j=0; j<stackContents[i].size(); j++)
						{
							proposeMove(pn, tallestStack, i, j);

							double proposedLength = 0;
							for(PlacementNetwork net : allNetworks)
								proposedLength += netLength(net, tallestStack, i);
							double gain = initialLength - proposedLength;
							if (gain > bestGain)
							{
								bestOtherStack = i;
								placeInOtherStack = j;
								bestGain = gain;
								nodeToMove = pn;
							}
						}
					}
				}
				if (bestGain > 0)
				{
					// make the move
					proposeMove(nodeToMove, tallestStack, bestOtherStack, placeInOtherStack);
					implementMove(nodeToMove, tallestStack, bestOtherStack, placeInOtherStack);
					break;
				}
			}
			if (bestGain == 0) break;
		}
	}

	private Orientation getOrientation(int index)
	{
		Orientation o = Orientation.IDENT;
		if (flipAlternateColsRows && (index%2) != 0)
		{
			if (columnPlacement) return Orientation.X;
			return Orientation.Y;
		}
		return o;
	}

	protected void implementMove(ProxyNode node, int oldIndex, int newIndex, int newPlaceInStack)
	{
		remove(node);
		node.setPlacement(node.getProposedX(), node.getProposedY(), node.getProposedIndex(),
			getOrientation(node.getProposedIndex()), true);
		put(node, newIndex, newPlaceInStack);
		evenStack(oldIndex);
		if (newIndex != oldIndex) evenStack(newIndex);
	}

	protected void proposeMove(ProxyNode node, int oldIndex, int newIndex, int newPlaceInStack)
	{
		Orientation newOrient = getOrientation(newIndex);
		if (oldIndex != newIndex)
		{
			// set proposed locations in the old stack without the moved node
			double bottom = 0;
			List<ProxyNode> pnList = stackContents[oldIndex];
			for(ProxyNode pn : pnList)
			{
				if (pn == node) continue;
				double x = pn.getPlacementX();
				double y = pn.getPlacementY();
				double size = pn.getCellSize();
				if (columnPlacement) y = bottom + size/2; else
					x = bottom + size/2;
				bottom += size;
				pn.setProposed(x, y, pn.getColumnRowIndex(), pn.getPlacementOrientation());
			}

			// set proposed locations in the new stack with the moved node
			bottom = 0;
			boolean notInserted = true;
			List<ProxyNode> newList = stackContents[newIndex];
			for(int i=0; i<newList.size(); i++)
			{
				ProxyNode pn = newList.get(i);
				Orientation o = pn.getPlacementOrientation();
				double x = pn.getPlacementX();
				double y = pn.getPlacementY();
				if (notInserted && i == newPlaceInStack)
				{
					if (columnPlacement) x = stackCoords[newIndex]; else
						y = stackCoords[newIndex];
					pn = node;
					o = newOrient;
					i--;
					notInserted = false;
				}
				double size = pn.getCellSize();
				if (columnPlacement) y = bottom + size/2; else
					x = bottom + size/2;
				bottom += size;
				pn.setProposed(x, y, newIndex, o);
			}
			if (notInserted)
			{
				double size = node.getCellSize();
				if (columnPlacement)
					node.setProposed(stackCoords[newIndex], bottom + size/2, newIndex, newOrient); else
						node.setProposed(bottom + size/2, stackCoords[newIndex], newIndex, newOrient);
			}
		} else
		{
			// redo the new stack with the moved node
			double bottom = 0;
			boolean notInserted = true;
			int movedNodes = 0;
			for(int i=0; i<stackContents[newIndex].size(); i++)
			{
				ProxyNode pn = stackContents[newIndex].get(i);
				if (pn == node) continue;
				if (notInserted && movedNodes == newPlaceInStack)
				{
					pn = node;
					i--;
					notInserted = false;
				}
				double x = pn.getPlacementX();
				double y = pn.getPlacementY();
				double size = pn.getCellSize();
				if (columnPlacement) y = bottom + size/2; else
					x = bottom + size/2;
				bottom += size;
				pn.setProposed(x, y, newIndex, pn.getPlacementOrientation());
				movedNodes++;
			}
			if (notInserted)
			{
				double size = node.getCellSize();
				if (columnPlacement)
					node.setProposed(node.getPlacementX(), bottom + size/2, newIndex, newOrient); else
						node.setProposed(bottom + size/2, node.getPlacementY(), newIndex, newOrient);
			}
		}
	}

	protected synchronized int lockRandomStack()
	{
		int index = randNum.nextInt(numStacks);
		for(int i=0; i<numStacks; i++)
		{
			if (!stacksBusy[index])
			{
				stacksBusy[index] = true;
				return index;
			}
			index = (index+1) % numStacks;
		}
		return -1;
	}

	protected void releaseStack(int index)
	{
		stacksBusy[index] = false;
	}

	/**
	 * Method that approximates the conductor length of a set of nets when proxies are used
	 * @param networks the PlacementNetworks to analyze.
	 * @return the length of the connections on the networks.
	 */
	private double netLength(List<PlacementNetwork> networks)
	{
		double length = 0;
		for(PlacementNetwork net : networks)
			length += netLength(net, -1, -1);
		return length;
	}

	/**
	 * Method that calculates the bounding box net length approximation for a given net.
	 * It hashes the nodes of the ports in the nets to its proxies.
	 * Also, it may substitute a node with another one just for this calculation
	 * @param net the PlacementNetwork to analyze.
	 * @param workingIndex1 a stack number that is being "proposed".
	 * @param workingIndex2 another stack number that is being "proposed".
	 * @return the length of the connections on the network.
	 */
	protected double netLength(PlacementNetwork net, int workingIndex1, int workingIndex2)
	{
		double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
		for (PlacementPort port : net.getPortsOnNet())
		{
			ProxyNode pn = proxyMap.get(port.getPlacementNode());
			double currX, currY;
			Orientation o;
			if (pn.getColumnRowIndex() == workingIndex1 || pn.getColumnRowIndex() == workingIndex2)
			{
				currX = pn.getProposedX();
				currY = pn.getProposedY();
				o = pn.getProposedOrientation();
			} else
			{
				currX = pn.getPlacementX();
				currY = pn.getPlacementY();
				o = pn.getPlacementOrientation();
			}
			if (o == Orientation.X) currX -= port.getOffX(); else
				currX += port.getOffX();
			if (o == Orientation.Y) currY -= port.getOffY(); else
				currY += port.getOffY();
			if (currX < minX) minX = currX;
			if (currX > maxX) maxX = currX;
			if (currY < minY) minY = currY;
			if (currY > maxY) maxY = currY;
		}
		return (maxX - minX) + (maxY - minY);
	}

	/**
	 * Method that finds a random ProxyNode in a specific stack.
	 * @param index the stack index.
	 * @return a random ProxyNode.
	 */
	protected ProxyNode getRandomNode(int index)
	{
		List<ProxyNode> pnList = stackContents[index];
		return pnList.get(randNum.nextInt(pnList.size()));
	}

	/**
	 * Adds a node to the stack lists.
	 * @param node the ProxyNode to add to the stack lists.
	 */
	private void put(ProxyNode node, int index, int position)
	{
		List<ProxyNode> pnList = stackContents[index];
		pnList.add(position, node);
		stackSizes[index] += node.getCellSize();
	}

	/**
	 * Removes a node from all stack lists.
	 * @param node the ProxyNode to remove.
	 */
	private void remove(ProxyNode node)
	{
		int index = node.getColumnRowIndex();
		List<ProxyNode> pnList = stackContents[index];
		if (!pnList.remove(node))
		{
			System.out.println("ERROR: could not remove node from stack "+index);
		}
		stackSizes[index] -= node.getCellSize();
	}

	/**
	 * Method to rearrange a stack so that all cells touch.
	 * @param index the stack index.
	 */
	private void evenStack(int index)
	{
		double bottom = 0;
		List<ProxyNode> pnList = stackContents[index];
		for(ProxyNode pn : pnList)
		{
			double x = pn.getPlacementX();
			double y = pn.getPlacementY();
			double size = pn.getCellSize();
			if (columnPlacement) y = bottom + size/2; else
				x = bottom + size/2;
			bottom += size;
			pn.setPlacement(x, y, index, pn.getPlacementOrientation(), true);
		}
	}

	/*************************************** Proxy Node **********************************/

	/**
	 * This class is a proxy for the actual PlacementNode.
	 * It can be moved around and rotated without touching the actual PlacementNode.
	 */
	class ProxyNode implements Comparable<ProxyNode>
	{
		private double x, y;						// current location
		private double newX, newY;					// proposed location when testing rearrangement
		private int index;							// stack number (row or column)
		private int proposedIndex;					// proposed stack number (row or column)
		private Orientation orientation;			// orientation of the placement node
		private Orientation proposedOrientation;	// proposed orientation
		private double width = 0;					// width of the placement node
		private double height = 0;					// height of the placement node
		private List<PlacementNetwork> nets = new ArrayList<PlacementNetwork>();

		/**
		 * Constructor to create a ProxyNode
		 * @param node the PlacementNode that should is being shadowed.
		 */
		public ProxyNode(PlacementNode node)
		{
			x = node.getPlacementX();
			y = node.getPlacementY();

			NodeProto np = ((com.sun.electric.tool.placement.PlacementAdapter.PlacementNode)node).getType();
			Rectangle2D spacing = null;
			if (np instanceof Cell)
			{
				spacing = ((Cell)np).findEssentialBounds();
			}

			if (spacing == null)
			{
				width = node.getWidth();
				height = node.getHeight();
			} else
			{
				width = spacing.getWidth();
				height = spacing.getHeight();
			}

			// create a list of all nets that node belongs to
			for(PlacementPort p : node.getPorts())
			{
				if (!nets.contains(p.getPlacementNetwork()) && p.getPlacementNetwork() != null)
					nets.add(p.getPlacementNetwork());
			}
			orientation = node.getPlacementOrientation();
		}

		public void setProposed(double x, double y, int ind, Orientation o)
		{
			newX = x;
			newY = y;
			proposedIndex = ind;
			proposedOrientation = o;
		}

		/**
		 * Method to get the proposed X-coordinate of this ProxyNode.
		 * @return the proposed X-coordinate of this ProxyNode.
		 */
		public double getProposedX() { return newX; }

		/**
		 * Method to get the proposed Y-coordinate of this ProxyNode.
		 * @return the proposed Y-coordinate of this ProxyNode.
		 */
		public double getProposedY() { return newY; }

		/**
		 * Method to get the proposed stack index of this ProxyNode.
		 * @return the proposed stack index of this ProxyNode.
		 */
		public int getProposedIndex() { return proposedIndex; }

		/**
		 * Method to get the proposed orientation of this ProxyNode.
		 * @return the proposed orientation of this ProxyNode.
		 */
		public Orientation getProposedOrientation() { return proposedOrientation; }

		/**
		 * Method that sets the node to a new position.
		 * @param x the X coordinate.
		 * @param y the Y coordinate.
		 * @param ind the column/row index.
		 */
		public void setPlacement(double x, double y, int ind, Orientation o, boolean check)
		{
			if (check)
			{
				double req = stackCoords[ind];
				if (columnPlacement)
				{
					if (x != req)
						System.out.println("Moving node from ("+this.x+"["+this.index+"],"+this.y+") to ("+x+"["+ind+"],"+y+") BUT STACK "+ind+" IS AT "+req);
				} else
				{
					if (y != req)
						System.out.println("Moving node from ("+this.x+","+this.y+"["+this.index+"]) to ("+x+","+y+"["+ind+"]) BUT STACK "+ind+" IS AT "+req);
				}
				Orientation oReq = getOrientation(ind);
				if (o != oReq)
					System.out.println("Rotating node from ("+this.x+","+this.y+")[S="+this.index+" O="+orientation.toString()+
						"] to ("+x+","+y+")[S="+ind+" O="+o.toString()+"] BUT O SHOULD BE '"+oReq.toString()+"'");
			}

			this.x = x;
			this.y = y;
			index = ind;
			orientation = o;
		}

		/**
		 * Method that returns the column or row number, when doing column/row-based placement.
		 * @return the column/row index.
		 */
		public int getColumnRowIndex() { return index; }

		/**
		 * Method to get a list of nets this node belongs to.
		 * This is more convenient than iterating over the ports.
		 * Also it only contains nets that are not ignored (e.g. because they are too huge).
		 * @return the list of nets this node belongs to.
		 */
		public List<PlacementNetwork> getNets() { return nets; }

		/**
		 * Method to get the X-coordinate of this ProxyNode.
		 * @return the X-coordinate of this ProxyNode.
		 */
		public double getPlacementX() { return x; }

		/**
		 * Method to get the Y-coordinate of this ProxyNode.
		 * @return the Y-coordinate of this ProxyNode.
		 */
		public double getPlacementY() { return y; }

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
		 * Method to get the size of this ProxyNode along the stack dimension.
		 * For column-based placement, this is the height;
		 * for row-based placement, this is the width;
		 * @return the size of this ProxyNode.
		 */
		public double getCellSize()
		{
			if (columnPlacement) return height;
			return width;
		}

		/**
		 * Method to get the girth of this ProxyNode, which separation of the stacks.
		 * For column-based placement, this is the width;
		 * for row-based placement, this is the height;
		 * @return the girth of this ProxyNode.
		 */
		public double getCellGirth()
		{
			if (columnPlacement) return width;
			return height;
		}

		/**
		 * Method to get the orientation of this ProxyNode.
		 * @return the orientation of this ProxyNode.
		 */
		public Orientation getPlacementOrientation() { return orientation; }

		public int compareTo(ProxyNode o)
		{
			if (columnPlacement)
			{
				double y1 = getPlacementY();
				double y2 = o.getPlacementY();
				if (y1 < y2) return 1;
				if (y1 > y2) return -1;
			} else
			{
				double x1 = getPlacementX();
				double x2 = o.getPlacementX();
				if (x1 < x2) return 1;
				if (x1 > x2) return -1;
			}
			return 0;
		}
	}
}
