/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: FDRowCol.java
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

import com.sun.electric.database.geometry.Poly;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.JobException;
import com.sun.electric.tool.placement.PlacementAdapter;
import com.sun.electric.tool.user.Highlighter;
import com.sun.electric.tool.user.User;
import com.sun.electric.tool.user.dialogs.EDialog;
import com.sun.electric.tool.user.ui.EditWindow;
import com.sun.electric.tool.user.ui.TopLevel;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.SwingUtilities;

/**
 * Implementation of the force-directed placement algorithm for row/column placement.
 */
public class FDRowCol extends RowCol
{
	// parameters
	protected PlacementParameter numThreadsParam = new PlacementParameter("threads",
		"Number of threads:", 4);
	protected PlacementParameter maxRuntimeParam = new PlacementParameter("runtime",
		"Runtime (in seconds, 0 for no limit):", 240);
	protected PlacementParameter flipAlternateColsRows = new PlacementParameter("flipColRow",
		"Flip alternate columns/rows", true);
	protected PlacementParameter makeStacksEven = new PlacementParameter("makeStacksEven",
		"Force rows/columns to be equal length", true);

	private final static boolean DEBUGMODE = false;

	private static Map<ProxyNode,NodeMotion> motionMap;

	/**
	 * Method to return the name of this placement algorithm.
	 * @return the name of this placement algorithm.
	 */
	public String getAlgorithmName() { return "Force-Directed-Row/Col"; }

	/**
	 * Method to do row/column placement.
	 * @return true if placement completed; false if debugging and placement is not complete.
	 */
	public boolean runRowColPlacement(List<PlacementNode> placementNodes, List<PlacementNetwork> allNetworks)
	{
		setParamterValues(numThreadsParam.getIntValue(), maxRuntimeParam.getIntValue());

		if (DEBUGMODE)
		{
			initializeDebugging(placementNodes, allNetworks);
	        return false;
		}

		// regular FD placement
		boolean stackWeight = true;
		for(;;)
		{
			sortForces(allNetworks, placementNodes, stackWeight);
			ProxyNode nodeToMove = doForceDirected(allNetworks, placementNodes);
			if (nodeToMove == null)
			{
				if (!stackWeight) break;
				stackWeight = false;
			}
		}
		return true;
	}

	private static class NodeMotion
	{
		double dX, dY;
		int numMoved;

		NodeMotion()
		{
			dX = dY = 0;
			numMoved = 0;
		}
	}

	/**
	 * Method that does the actual force-directed placement.
	 * @return true if a move was made; false if not (all done).
	 */
	void sortForces(List<PlacementNetwork> allNetworks, List<PlacementNode> placementNodes, boolean stackWeight)
	{
		// clear the accumulated forces
		motionMap = new HashMap<ProxyNode,NodeMotion>();
		for (ProxyNode pNode : nodesToPlace)
			motionMap.put(pNode, new NodeMotion());

		double [] stackWeights = new double[numStacks];
		double avgStackSize = 0;
		for(int i=0; i<numStacks; i++) avgStackSize += stackSizes[i];
		avgStackSize /= numStacks;
		for(int i=0; i<numStacks; i++)
			stackWeights[i] = stackWeight ? (avgStackSize / stackSizes[i]) : 1;

		// now look at every connection line and accumulate forces on the nodes
		for(PlacementNetwork pNet : allNetworks)
		{
			PlacementPort lastPort = null;
			ProxyNode lastPN = null;
			double lastX = 0, lastY = 0;
			for (PlacementPort port : pNet.getPortsOnNet())
			{
				ProxyNode pn = proxyMap.get(port.getPlacementNode());
				double currX = pn.getPlacementX();
				double currY = pn.getPlacementY();
				Orientation o = pn.getPlacementOrientation();
				if (o == Orientation.X) currX -= port.getOffX(); else
					currX += port.getOffX();
				if (o == Orientation.Y) currY -= port.getOffY(); else
					currY += port.getOffY();
				if (lastPort != null && pn != lastPN)
				{
					double addX = (lastX - currX) * stackWeights[lastPN.getColumnRowIndex()];
					double addY = (lastY - currY) * stackWeights[lastPN.getColumnRowIndex()];
					NodeMotion pnNM = motionMap.get(pn);
					pnNM.dX += addX;   pnNM.dY += addY;   pnNM.numMoved++;

					addX = (currX - lastX) * stackWeights[pn.getColumnRowIndex()];
					addY = (currY - lastY) * stackWeights[pn.getColumnRowIndex()];
					pnNM = motionMap.get(lastPN);
					pnNM.dX += addX;   pnNM.dY += addY;   pnNM.numMoved++;
				}
				lastPort = port;
				lastPN = pn;
				lastX = currX;
				lastY = currY;
			}
		}

		// normalize the force vectors
		for(ProxyNode node : nodesToPlace)
		{
			NodeMotion nodeNM = motionMap.get(node);
			nodeNM.dX /= nodeNM.numMoved;
			nodeNM.dY /= nodeNM.numMoved;
		}

		// sort by strength of force
		Collections.sort(nodesToPlace, new ProxyMovement());
	}

	ProxyNode doForceDirected(List<PlacementNetwork> allNetworks, List<PlacementNode> placementNodes)
	{
		for(ProxyNode biggestMoveNode : nodesToPlace)
		{
			// apply the force and make a move (if it really is good)
			NodeMotion nodeNM = motionMap.get(biggestMoveNode);
			double xPos = biggestMoveNode.getPlacementX() + nodeNM.dX;
			double yPos = biggestMoveNode.getPlacementY() + nodeNM.dY;

			// determine stack numbers for the move
			int oldIndex = biggestMoveNode.getColumnRowIndex();
			int newIndex;
			if (columnPlacement) newIndex = (int)Math.round(xPos / biggestMoveNode.getCellGirth()); else
				newIndex = (int)Math.round(yPos / biggestMoveNode.getCellGirth());
			if (newIndex < 0) newIndex = 0;
			if (newIndex >= numStacks) newIndex = numStacks-1;

			// determine position in the new stack
			double stackPos;
			if (columnPlacement) 
			{
				xPos = newIndex * biggestMoveNode.getCellGirth();
				stackPos = yPos;
			} else
			{
				yPos = newIndex * biggestMoveNode.getCellGirth();
				stackPos = xPos;
			}
			List<ProxyNode> newStack = stackContents[newIndex];
			double stackLoc = 0;
			int newPlaceInStack = 0;
			for( ; newPlaceInStack<newStack.size(); newPlaceInStack++)
			{
				if (stackPos <= stackLoc) break;
				ProxyNode pn = newStack.get(newPlaceInStack);
				stackLoc += pn.getCellSize();
			}

			// ignore if there is no change
			if (newIndex == oldIndex)
			{
				int oldPlaceInStack = stackContents[oldIndex].indexOf(biggestMoveNode);
				if (newPlaceInStack == oldPlaceInStack) continue;
				if (oldPlaceInStack < newPlaceInStack) newPlaceInStack--;
			}

			// suggest the change
			proposeMove(biggestMoveNode, oldIndex, newIndex, newPlaceInStack);

			// determine the network length before and after the change
			double networkMetricBefore = 0, networkMetricAfter = 0;
			for(PlacementNetwork net : allNetworks)
			{
				networkMetricBefore += netLength(net, -1, -1);
				networkMetricAfter += netLength(net, newIndex, oldIndex);
			}
			double gain = networkMetricBefore - networkMetricAfter;
			if (gain > 0)
			{
				// move makes a good change: do it
				implementMove(biggestMoveNode, oldIndex, newIndex, newPlaceInStack);
				return biggestMoveNode;
			}
		}
		return null;
	}

	private class ProxyMovement implements Comparator<ProxyNode>
	{
        public int compare(ProxyNode c1, ProxyNode c2)
        {
			NodeMotion nm1 = motionMap.get(c1);
			NodeMotion nm2 = motionMap.get(c2);
        	double x1 = nm1.dX;
        	double y1 = nm1.dY;
        	double x2 = nm2.dX;
        	double y2 = nm2.dY;
            double r1 = Math.sqrt(x1*x1 + y1*y1);
            double r2 = Math.sqrt(x2*x2 + y2*y2);
            if (r1 == r2) return 0;
            if (r1 < r2) return 1;
            return -1;
        }
    }

	/* ********************************************* DEBUGGING ********************************************* */

	private static List<PlacementNode> debugPlacementNodes;
	private static List<PlacementNetwork> debugAllNetworks;
	private static Map<String, NodeInst> placementMap;
	private static String plannedMoveNodeName;
	private static Map<ProxyNode,PlacementAdapter.PlacementNode> backMap;

	private void initializeDebugging(List<PlacementNode> placementNodes, List<PlacementNetwork> allNetworks)
	{
		debugPlacementNodes = placementNodes;
		debugAllNetworks = allNetworks;

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
		private boolean stackWeight;

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
			stackWeight = true;
		}

		protected void escapePressed() { closeDialog(null); }

		private void planMove()
		{
			if (planned)
			{
		        theBut.setText("Plan Move");
				new MakeIntermediateMove();
				planned = false;
				return;
			}

			theBut.setText("Do Move");
			planned = true;
			ProxyNode biggestMoveNode;
			for(;;)
			{
				sortForces(debugAllNetworks, debugPlacementNodes, stackWeight);
				biggestMoveNode = doForceDirected(debugAllNetworks, debugPlacementNodes);
				if (biggestMoveNode != null) break;
				if (!stackWeight) break;
				stackWeight = false;
			}

			placementMap = PlacementAdapter.getPlacementMap();
			plannedMoveNodeName = null;
			EditWindow wnd = EditWindow.getCurrent();
			Highlighter h = wnd.getHighlighter();
			h.clear();
			for(ProxyNode pn : nodesToPlace)
			{
				NodeInst bmNI = backMap.get(pn).getOriginal();
				NodeInst newNI = placementMap.get(bmNI.getName());
				NodeMotion nodeNM = motionMap.get(pn);
				Rectangle2D area = newNI.getBounds();
				Poly poly = new Poly(area);
				h.addPoly(poly, wnd.getCell(), Color.GREEN);
				Poly polyL = new Poly(
                    Poly.fromLambda(newNI.getBounds().getCenterX(), newNI.getBounds().getCenterY()),
                    Poly.fromLambda(newNI.getBounds().getCenterX()+nodeNM.dX, newNI.getBounds().getCenterY()+nodeNM.dY));
				h.addPoly(polyL, wnd.getCell(), Color.CYAN);
			}

			if (biggestMoveNode != null)
			{
				for (PlacementNode pn : debugPlacementNodes)
				{
					ProxyNode p = proxyMap.get(pn);
					pn.setPlacement(p.getPlacementX(), p.getPlacementY());
					pn.setOrientation(p.getPlacementOrientation());
				}
				NodeMotion nodeNM = motionMap.get(biggestMoveNode);
				NodeInst bmNI = backMap.get(biggestMoveNode).getOriginal();
				NodeInst newNI = placementMap.get(bmNI.getName());
				plannedMoveNodeName = bmNI.getName();
				Rectangle2D area = newNI.getBounds();
				h.addLine(new Point2D.Double(area.getMinX(), area.getMinY()), new Point2D.Double(area.getMinX(), area.getMaxY()), wnd.getCell(), true, false);
				h.addLine(new Point2D.Double(area.getMinX(), area.getMaxY()), new Point2D.Double(area.getMaxX(), area.getMaxY()), wnd.getCell(), true, false);
				h.addLine(new Point2D.Double(area.getMaxX(), area.getMaxY()), new Point2D.Double(area.getMaxX(), area.getMinY()), wnd.getCell(), true, false);
				h.addLine(new Point2D.Double(area.getMaxX(), area.getMinY()), new Point2D.Double(area.getMinX(), area.getMinY()), wnd.getCell(), true, false);
				h.addLine(new Point2D.Double(newNI.getBounds().getCenterX(), newNI.getBounds().getCenterY()),
					new Point2D.Double(newNI.getBounds().getCenterX()+nodeNM.dX, newNI.getBounds().getCenterY()+nodeNM.dY), wnd.getCell(), true, false);
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
			for(PlacementNode plNode : debugPlacementNodes)
			{
				PlacementAdapter.PlacementNode pan = (PlacementAdapter.PlacementNode)plNode;
				NodeInst bmNI = pan.getOriginal();
				NodeInst newNI = placementMap.get(bmNI.getName());

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
				newNI.move(dX, dY);
			}
            return true;
		}

		public void terminateOK()
		{
			NodeInst newNI = placementMap.get(plannedMoveNodeName);
			EditWindow wnd = EditWindow.getCurrent();
			Highlighter h = wnd.getHighlighter();
			h.clear();
			Rectangle2D area = newNI.getBounds();
			h.addArea(area, wnd.getCell());
			h.finished();
		}
	}
}
