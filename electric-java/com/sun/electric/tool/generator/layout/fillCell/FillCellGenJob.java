/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: FillCellGenJob.java
 *
 * Copyright (c) 2006, Static Free Software. All rights reserved.
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
package com.sun.electric.tool.generator.layout.fillCell;

import java.awt.geom.Area;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.sun.electric.database.EditingPreferences;
import com.sun.electric.database.geometry.EPoint;
import com.sun.electric.database.geometry.GeometryHandler;
import com.sun.electric.database.geometry.Poly;
import com.sun.electric.database.geometry.PolyBase;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.Export;
import com.sun.electric.database.hierarchy.HierarchyEnumerator;
import com.sun.electric.database.hierarchy.Library;
import com.sun.electric.database.hierarchy.Nodable;
import com.sun.electric.database.network.Netlist;
import com.sun.electric.database.network.Network;
import com.sun.electric.database.prototype.NodeProto;
import com.sun.electric.database.prototype.PortCharacteristic;
import com.sun.electric.database.prototype.PortProto;
import com.sun.electric.database.topology.ArcInst;
import com.sun.electric.database.topology.Connection;
import com.sun.electric.database.topology.Geometric;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.topology.PortInst;
import com.sun.electric.database.variable.VarContext;
import com.sun.electric.technology.ArcProto;
import com.sun.electric.technology.Layer;
import com.sun.electric.technology.PrimitiveNode;
import com.sun.electric.technology.PrimitivePort;
import com.sun.electric.technology.Technology;
import com.sun.electric.technology.technologies.Generic;
import com.sun.electric.tool.extract.LayerCoverageTool;
import com.sun.electric.tool.generator.layout.LayoutLib;
import com.sun.electric.tool.generator.layout.TechType;
import com.sun.electric.tool.generator.layout.fill.FillGenConfig;
import com.sun.electric.tool.generator.layout.fill.FillGenJob;
import com.sun.electric.tool.generator.layout.fill.FillGeneratorTool;
import com.sun.electric.tool.generator.layout.fill.G;
import com.sun.electric.tool.generator.layout.fill.VddGndStraps;
import com.sun.electric.tool.routing.InteractiveRouter;
import com.sun.electric.tool.routing.Route;
import com.sun.electric.tool.routing.Router;
import com.sun.electric.tool.routing.Routing.SoGContactsStrategy;
import com.sun.electric.tool.routing.SimpleWirer;
import com.sun.electric.tool.routing.seaOfGates.SeaOfGatesEngine;
import com.sun.electric.tool.routing.seaOfGates.SeaOfGatesEngineFactory;
import com.sun.electric.tool.routing.seaOfGates.SeaOfGatesHandlers;
import com.sun.electric.util.math.DBMath;
import com.sun.electric.util.math.FixpTransform;
import com.sun.electric.util.math.GenMath;

/****************************** JOB ******************************/

public class FillCellGenJob extends FillGenJob {
	public FillCellGenJob(Cell cell, FillGenConfig gen, boolean doItNow,
			LayerCoverageTool.LayerCoveragePreferences lcp) {
		super(cell, gen, doItNow, lcp);
	}

	/**
	 * Method to obtain the PrimitiveNode layer holding this export. It travels
	 * along hierarchy until reaches the PrimitiveNode leaf containing the
	 * export. Only metal layers are selected.
	 *
	 * @param ex
	 * @return Non pseudo layer for the given export
	 */
	public static Layer getMetalLayerFromExport(PortProto ex) {
		PortProto po = ex;

		if (ex instanceof Export) {
			PortInst pi = ((Export) ex).getOriginalPort();
			po = pi.getPortProto();
		}
		if (po instanceof Export)
			return getMetalLayerFromExport(po);
		if (po instanceof PrimitivePort) {
			PrimitivePort pp = (PrimitivePort) po;
			PrimitiveNode node = pp.getParent();
			// Search for at least m2
			for (Iterator<Layer> it = node.getLayerIterator(); it.hasNext();) {
				Layer layer = it.next();
				Layer.Function func = layer.getFunction();
				// Exclude metal1
				if (func.isMetal() && func != Layer.Function.METAL1)
					return layer;
			}
		}
		return null;
	}

	private List<PortConfig> searchPortList() {
		// Searching common power/gnd connections and skip the ones are in the
		// same network
		// Don't change List by Set otherwise the sequence given by Set is not
		// deterministic and hard to debug
		List<PortInst> portList = new ArrayList<PortInst>();
		Netlist topCellNetlist = topCell.getNetlist();
		List<Export> exportList = new ArrayList<Export>();

		for (Iterator<NodeInst> it = topCell.getNodes(); it.hasNext();) {
			NodeInst ni = it.next();

			if (!ni.isCellInstance()) {
				// for (Iterator<PortInst> itP = ni.getPortInsts();
				// itP.hasNext(); )
				// {
				// PortInst p = itP.next();
				//
				// if (!p.getPortProto().isGround() &&
				// !p.getPortProto().isPower())
				// continue;
				// // Simple case
				// portList.add(p);
				// }
				for (Iterator<Export> itE = ni.getExports(); itE.hasNext();) {
					Export e = itE.next();
					if (!e.isGround() && !e.isPower())
						continue;
					portList.add(e.getOriginalPort());
					exportList.add(e);
				}
			} else {
				Cell cell = (Cell) ni.getProto();
				Netlist netlist = cell.getNetlist();
				List<PortInst> list = new ArrayList<PortInst>();
				List<Network> nets = new ArrayList<Network>();
				List<Export> eList = new ArrayList<Export>();
				boolean foundVdd = false;
				boolean foundGnd = false;

				for (Iterator<PortInst> itP = ni.getPortInsts(); itP.hasNext() && (!foundVdd || !foundGnd);) {
					PortInst p = itP.next();

					if (!p.getPortProto().isGround() && !p.getPortProto().isPower())
						continue;
					if ((foundVdd && p.getPortProto().isPower()) || (foundGnd && p.getPortProto().isGround())) {
						System.out.println("Skipping export " + p + " in " + ni);
						continue;
					}

					// If subcell has two exports on the same network, it
					// assumes they are connected inside
					// and therefore only one of them is checked
					assert (p.getPortProto() instanceof Export);
					Export ex = (Export) p.getPortProto();
					Network net = netlist.getNetwork(ex.getOriginalPort());
					Network topNet = topCellNetlist.getNetwork(p);
					Cell fillCell = null;

					// search for possible existing fill already defined
					// for (Iterator<Nodable> itN =
					// topNet.getNetlist().getNodables(); itN.hasNext(); )
					// {
					// Nodable no = itN.next();
					// if (ni == no) continue; // skip itself
					// if (!no.isCellInstance()) continue; // skip any flat
					// PrimitiveNode?
					// Cell c = (Cell)no.getProto();
					// if (c == p.getNodeInst().getProto()) // skip port parent
					// continue;
					// if (c.getName().indexOf("fill") == -1) continue; // not a
					// fill cell
					// fillCell = c;
					// break;
					// }
					// if fillCell is not null -> cover by a fill cell
					if (fillCell == null && !nets.contains(net)) {
						list.add(p);
						nets.add(net);
						nets.add(net);
						eList.add(ex);
						if (p.getPortProto().isPower())
							foundVdd = true;
						else if (p.getPortProto().isGround())
							foundGnd = true;
					} else System.out.println("Skipping export " + p + " in " + ni);
				}
				portList.addAll(list);
				exportList.addAll(eList);
			}
		}

		// searching for exclusion regions. If port is inside these regions,
		// then it will be removed.
		// Search them in a chunk of ports. It should be faster
		// ObjectQTree tree = new ObjectQTree(topCell.getBounds());
		// List<Rectangle2D> searchBoxes = new ArrayList<Rectangle2D>(); // list
		// of AFG boxes to use.
		//
		// for (Iterator<NodeInst> it = topCell.getNodes(); it.hasNext(); )
		// {
		// NodeInst ni = it.next();
		// NodeProto np = ni.getProto();
		// if (np == Generic.tech.afgNode)
		// searchBoxes.add(ni.getBounds());
		// }
		// if (searchBoxes.size() > 0)
		// {
		// for (PortInst p : portList)
		// {
		// tree.add(p, p.getBounds());
		// }
		// for (Rectangle2D rect : searchBoxes)
		// {
		// Set set = tree.find(rect);
		// portList.removeAll(set);
		// }
		// }
		List<PortConfig> plList = new ArrayList<PortConfig>();

		assert (portList.size() == exportList.size());

		for (int i = 0; i < exportList.size(); i++) {
			PortInst p = portList.get(i);

			Layer l = getMetalLayerFromExport(p.getPortProto());
			// Checking that pin is on metal port
			if (l != null)
				plList.add(new PortConfig(p, exportList.get(i), l));
		}
		// for (PortInst p : portList)
		// {
		// plList.add(new PortConfig(p,
		// getMetalLayerFromExport(p.getPortProto())));
		// }
		return plList;
	}

	private List<Cell> searchPossibleMaster() {
		Cell masterCell = null;
		List<Cell> secondMasterCell = new ArrayList<Cell>();
		List<Cell> list = null;

		for (Iterator<Library> it = Library.getLibraries(); it.hasNext();) {
			Library lib = it.next();
			for (Iterator<Cell> itC = lib.getCells(); itC.hasNext();) {
				Cell c = itC.next();
				if (c.getVar("FILL_MASTER") != null)
					masterCell = c;
				else if (c.getVar("FILL_MASTER_ALTERNATIVE") != null)
					secondMasterCell.add(c);
			}
		}

		if (masterCell != null) {
			list = new ArrayList<Cell>();
			list.add(masterCell);
			list.addAll(secondMasterCell);
		} else System.out.println("No master found for Fill Generator");
		return list;
	}

    @Override
	public boolean doIt() {
        EditingPreferences ep = getEditingPreferences();
		FillCellTool fillGen = (FillCellTool) setUpJob();

		boolean result = (fillGenConfig.fillType == FillGeneratorTool.FillTypeEnum.TEMPLATE) ? doTemplateFill(fillGen, ep)
				: doFillOnCell(fillGen);
		return result;
	}

	protected static Cell detectOverlappingBars(Cell cell, Cell master, Cell empty,
			FixpTransform fillTransUp, HashSet<NodeInst> nodesToRemove, HashSet<ArcInst> arcsToRemove,
			Cell topCell, NodeInst[] ignore, double drcSpacing, int level) {
		List<Layer.Function> tmp = new ArrayList<Layer.Function>();

		// Check if any metalXY must be removed
		for (Iterator<NodeInst> itNode = cell.getNodes(); itNode.hasNext();) {
			NodeInst ni = itNode.next();

			if (NodeInst.isSpecialNode(ni))
				continue;

			tmp.clear();
			NodeProto np = ni.getProto();

			// Only one level of hierarchy otherwise it gets too complicated
			if (ni.isCellInstance()) {
				Cell c = (Cell) ni.getProto();
				FixpTransform subTransUp = ni.transformOut(fillTransUp);
				HashSet<NodeInst> nodesToRemoveSub = new HashSet<NodeInst>();
				HashSet<ArcInst> arcsToRemoveSub = new HashSet<ArcInst>();

				Cell tmpCell = detectOverlappingBars(c, master, empty, subTransUp, nodesToRemoveSub,
						arcsToRemoveSub, topCell, ignore, drcSpacing, ++level);
				if (tmpCell == empty || tmpCell == null) {
					// return true. Better to not include this master due to
					// complexity of the subcells.
					// not sure what to delete
					nodesToRemoveSub.clear();
					arcsToRemoveSub.clear();
					return empty;
				}
				continue;
			}
			PrimitiveNode pn = (PrimitiveNode) np;
			if (pn.getFunction().isPin())
				continue; // pins have pseudo layers

			for (Technology.NodeLayer tlayer : pn.getNodeLayers()) {
				tmp.add(tlayer.getLayer().getFunction());
			}
			Rectangle2D rect = getSearchRectangle(ni.getBounds(), fillTransUp, drcSpacing);
			assert (ignore.length == 0);
			if (searchCollision(topCell, rect, new Layer.Function.Set(tmp), null, new Object[] { cell, ni },
					master, null)) {
				// Just for testing
				if (LOCALDEBUGFLAG) {
					rect = getSearchRectangle(ni.getBounds(), fillTransUp, drcSpacing);
					searchCollision(topCell, rect, new Layer.Function.Set(tmp), null,
							new Object[] { cell, ni }, master, null);
				}
				// Direct on last top fill cell
				nodesToRemove.add(ni);
				for (Iterator<Connection> itC = ni.getConnections(); itC.hasNext();) {
					Connection c = itC.next();
					arcsToRemove.add(c.getArc());
				}
			}
		}

		// Checking if any arc in FillCell collides with rest of the cells
		Netlist netlist = cell.getNetlist();
		for (Iterator<ArcInst> itArc = cell.getArcs(); itArc.hasNext();) {
			ArcInst ai = itArc.next();
			Layer.Function.Set thisLayer = new Layer.Function.Set(ai.getProto().getLayer(0)
					.getFunction());
			// Searching box must reflect DRC constrains
			Rectangle2D rect = getSearchRectangle(ai.getBounds(), fillTransUp, drcSpacing);
			assert (ignore.length == 0);
			Network net = netlist.getNetwork(ai, 0);
			if (searchCollision(topCell, rect, thisLayer, null, new Object[] { cell, ai }, master, net)) {
				// For testing
				if (LOCALDEBUGFLAG) {
					rect = getSearchRectangle(ai.getBounds(), fillTransUp, drcSpacing);
					searchCollision(topCell, rect, thisLayer, null, new Object[] { cell, ai }, master, net);
				}
				arcsToRemove.add(ai);
				// Remove exports and pins as well
				// nodesToRemove.add(ai.getTail().getPortInst().getNodeInst());
				// nodesToRemove.add(ai.getHead().getPortInst().getNodeInst());
			}
		}

		if (level == 0) {
			Set<ArcProto> names = new HashSet<ArcProto>();
			// Check if there are not contacts or ping that don't have at least
			// one or zero arc per metal
			// If they don't have, then they are floating
			for (Iterator<NodeInst> itNode = cell.getNodes(); itNode.hasNext();) {
				NodeInst ni = itNode.next();

				if (Generic.isSpecialGenericNode(ni))
					continue; // Can't skip pins

				// For removal
				if (nodesToRemove.contains(ni))
					continue;

				int minNum = (ni.getProto().getFunction().isPin() || ni.isCellInstance()) ? 0 : 1;
				// At least should have connections to both layers
				names.clear();
				boolean found = false;
				for (Iterator<Connection> itC = ni.getConnections(); itC.hasNext();) {
					Connection c = itC.next();
					ArcInst ai = c.getArc();
					if (arcsToRemove.contains(ai))
						continue; // marked for deletion
					names.add(ai.getProto());
					if (names.size() > minNum) // found at least two different
												// arcs
					{
						found = true;
						break;
					}
				}
				if (!found) // element could be deleted
					nodesToRemove.add(ni);
			}
		}

		return (nodesToRemove.size() > 0 || arcsToRemove.size() > 0) ? null : master;
	}

	/**
	 * Method to determine if new contact will overlap with other metals in the
	 * configuration
	 *
	 * @param parent
	 * @param nodeBounds
	 * @param p
	 * @param ignores
	 *            NodeInst instances to ignore
	 * @param master
	 * @param theNet
	 * @return true if a collision was found
	 */
	protected static boolean searchCollision(Cell parent, Rectangle2D nodeBounds,
			Layer.Function.Set theseLayers, PortConfig p, Object[] ignores, Cell master, Network theNet) {
		// Not checking if they belong to the same net!. If yes, ignore the
		// collision
		Rectangle2D subBound = new Rectangle2D.Double();
		Netlist netlist = parent.getNetlist();

		for (int i = 0; i < ignores.length; i++) {
			if (parent == ignores[i]) {
				return false;
			}
		}
		if (master != null && searchSubCellInMasterCell(master, parent))
			return false;

		for (Iterator<Geometric> it = parent.searchIterator(nodeBounds, false); it.hasNext();) {
			Geometric geom = it.next();

			if (p != null && geom == p.p.getNodeInst())
				continue; // port belongs to this node

			boolean ignoreThis = false;
			for (int i = 0; i < ignores.length; i++) {
				if (geom == ignores[i]) {
					ignoreThis = true;
					break;
				}
			}
			if (ignoreThis)
				continue; // ignore the cell. E.g. fillNi, connectionNi

			if (geom instanceof NodeInst) {
				NodeInst ni = (NodeInst) geom;

				if (NodeInst.isSpecialNode(ni))
					continue;

				// ignore nodes that are not primitive
				if (ni.isCellInstance()) {
					// instance found: look inside it for offending geometry
					FixpTransform extra = ni.transformIn();
					subBound.setRect(nodeBounds);
					DBMath.transformRect(subBound, extra);

					if (searchCollision((Cell) ni.getProto(), subBound, theseLayers, p, ignores, master,
							theNet))
						return true;
				} else {
					if (p != null) {
						boolean found = false;
						for (Iterator<PortInst> itP = ni.getPortInsts(); itP.hasNext();) {
							PortInst port = itP.next();
							Network net = netlist.getNetwork(port);
							// They export the same, power or gnd so no worries
							// about overlapping
							if (net.findExportWithSameCharacteristic(p.e) != null) {
								found = true;
								break;
							}
						}
						if (found)
							continue; // no match in network type
					}

					Poly[] subPolyList = parent.getTechnology().getShapeOfNode(ni, true, true, theseLayers);
					// Overlap found
					if (subPolyList.length > 0)
						return true;
				}
			} else {
				ArcInst ai = (ArcInst) geom;
				Network net = netlist.getNetwork(ai, 0);

				// They export the same, power or gnd so no worries about
				// overlapping
				if (p != null && net.findExportWithSameCharacteristic(p.e) != null)
					continue; // no match in network type

				if (theNet != null && net.getName().startsWith(theNet.getName())) // net.doTheyHaveSameCharacteristic(theNet))
					continue; // they belong to the same network

				Poly[] subPolyList = parent.getTechnology().getShapeOfArc(ai, theseLayers);

				// Something overlaps
				if (subPolyList.length > 0)
					return true;
			}
		}
		return false;
	}

	public static Rectangle2D getSearchRectangle(Rectangle2D bnd, FixpTransform fillTransUp,
			double drcSpacing) {
		Rectangle2D rect = new Rectangle2D.Double(bnd.getX() - drcSpacing, bnd.getY() - drcSpacing, bnd
				.getWidth()
				+ 2 * drcSpacing, bnd.getHeight() + 2 * drcSpacing);
		if (fillTransUp != null)
			DBMath.transformRect(rect, fillTransUp);
		return rect;
	}

	/**
	 * Method to check if a subCell used in another cell is also part of the
	 * master cell. This avoids conflicts while detecting collisions.
	 *
	 * @param master
	 * @param cell
	 * @return true if cell is used in master cell
	 */
	private static boolean searchSubCellInMasterCell(Cell master, Cell cell) {
		// checking if parent is already part of the fill master
		for (Iterator<NodeInst> itNi = master.getNodes(); itNi.hasNext();) {
			NodeInst ni = itNi.next();
			if (ni.isCellInstance()) {
				Cell thisCell = (Cell) ni.getProto();
				if (thisCell == cell)
					return true; // found in master
			}
		}
		return false;
	}

	// Collect exclusion area for fill generator
	private class Visitor extends HierarchyEnumerator.Visitor {
		private Area exclusionArea = new Area();
		private int level = -1;
		private int maxLevel = 0; // 3 for island_top, 3 or 2 for bridge_core

		public Visitor(int l) {
			this.maxLevel = l;
		}

        @Override
		public boolean enterCell(HierarchyEnumerator.CellInfo info) {
			if (level > (maxLevel + 1))
				return false; // only the first 2 levels
			level++;
			return true;
		}

        @Override
		public void exitCell(HierarchyEnumerator.CellInfo info) {
			level--;
		}

        @Override
		public boolean visitNodeInst(Nodable no, HierarchyEnumerator.CellInfo info) {
			NodeProto np = no.getProto();
			if (!(np == Generic.tech().afgNode || fillGenConfig.onlyAround && no.isCellInstance()))
				return false; // nothing to do
			if (level > maxLevel) // picking only in this level
				return false;
			if (level == maxLevel) // 3 for island_top
			{
				FixpTransform extra = info.getTransformToRoot();
				NodeInst ni = (NodeInst) no;
				Rectangle2D rect = (Rectangle2D) ni.getBounds().clone();
				DBMath.transformRect(rect, extra);
				exclusionArea.add(new Area(rect));
			}
			return true;
		}
	}

	// private void addExclusionAreas(Cell cell, Area exclusionArea,
	// AffineTransform toTop, int level)
	// {
	// if (level > 1) return; // stop here
	//
	// for (Iterator<NodeInst> it = cell.getNodes(); it.hasNext(); )
	// {
	// NodeInst ni = it.next();
	// NodeProto np = ni.getProto();
	// // Creates fill only arounds the top cells
	// if (np == Generic.tech().afgNode || fillGenConfig.onlyAround &&
	// ni.isCellInstance())
	// {
	// AffineTransform extra = ni.transformOut(toTop);
	// if (level == 0)
	// addExclusionAreas((Cell)ni.getProto(), exclusionArea, extra, level+1);
	// else
	// {
	// Rectangle2D rect = (Rectangle2D)ni.getBounds().clone();
	// DBMath.transformRect(rect, extra);
	// exclusionArea.add(new Area(rect));
	// // exclusionArea.add(new Area(ni.getBounds()));
	// }
	// }
	// }
	// }

	/**
	 * Method to search for the closest export to a given PortInst in the
	 * topCell. A hash map is kept to avoid calculation of the center point
	 * every time the export is in consideration.
	 *
	 * @return closest Export.
	 */
	private Export searchForClosestPort(PortInst pi, List<Export> list, Map<Export, Point2D> centerMap,
			FixpTransform conTransOut) {
		double closest = Double.MAX_VALUE;
		EPoint piCenter = pi.getCenter();
		Export found = null;

		for (Export ex : list) {
			Point2D point = centerMap.get(ex);
			if (point == null) // first time checking this export
			{
				point = ex.getOriginalPort().getCenter();
				Point2D newPoint = new Point2D.Double(0, 0);
				conTransOut.transform(point, newPoint); // location of export in
														// top cell
				point = newPoint;
				centerMap.put(ex, newPoint);
			}
			double dist = point.distance(piCenter);
			if (dist < closest) {
				found = ex;
				closest = dist;
			}
		}
		return found;
	}

	public boolean doFillOnCell(FillCellTool fillGen) {
		EditingPreferences ep = getEditingPreferences();
		// Searching for possible master
		List<Cell> masters = null;

		if (fillGen.config.useMaster) {
			masters = searchPossibleMaster();
			if (masters == null) {
				System.out.println("No master found. Either mark one or select create in pulldown menu");
				return false;
			}
		}

		// Creating fills only for layers found in exports
		List<PortConfig> portList = searchPortList();

		// otherwise pins at edges increase cell sizes and
		// FillRouter.connectCoincident(portInsts)
		// does work
		// G.DEF_SIZE = 0;
		List<Rectangle2D> topBoxList = new ArrayList<Rectangle2D>();
		topBoxList.add(topCell.getBounds()); // topBox might change if
												// predefined pitch is included
												// in master cell

		Visitor areaVisitor = new Visitor(fillGenConfig.level);
		HierarchyEnumerator.enumerateCell(topCell, VarContext.globalContext, areaVisitor);
		TechType techType = fillGenConfig.getTechType();

		Cell fillCell = (fillGenConfig.hierarchy) ? fillGen.treeMakeFillCell(fillGenConfig, ep, topCell, masters,
				topBoxList, areaVisitor.exclusionArea) : fillGen.standardMakeFillCell(
				fillGenConfig.firstLayer, fillGenConfig.lastLayer, techType, ep, fillGenConfig.perim,
				fillGenConfig.cellTiles, true);
		fillCell.setTechnology(topCell.getTechnology());

		if (topCell == null) /* || portList == null || portList.size() == 0) */
			return false; // not sure if false is correct

		Cell connectionCell = Cell.newInstance(topCell.getLibrary(), topCell.getName() + "fill{lay}");
		connectionCell.setTechnology(topCell.getTechnology());

		Rectangle2D fillBnd = fillCell.getBounds();
		double essentialX = fillBnd.getWidth() / 2;
		double essentialY = fillBnd.getHeight() / 2;
		LayoutLib.newNodeInst(techType.essentialBounds(), ep, -essentialX, -essentialY, G.DEF_SIZE, G.DEF_SIZE,
				180, connectionCell);
		LayoutLib.newNodeInst(techType.essentialBounds(), ep, essentialX, essentialY, G.DEF_SIZE, G.DEF_SIZE, 0,
				connectionCell);

		// Adding the connection cell into topCell
		assert (topBoxList.size() == 1);
		Rectangle2D bnd = topBoxList.get(0);
		NodeInst conNi = LayoutLib.newNodeInst(connectionCell, ep, bnd.getCenterX(), bnd.getCenterY(), fillBnd
				.getWidth(), fillBnd.getHeight(), 0, topCell);

		// Adding the fill cell into connectionCell
		Rectangle2D conBnd = connectionCell.getBounds();
		NodeInst fillNi = LayoutLib.newNodeInst(fillCell, ep, conBnd.getCenterX() - fillBnd.getWidth() / 2
				- fillBnd.getX(), conBnd.getCenterY() - fillBnd.getHeight() / 2 - fillBnd.getY(), fillBnd
				.getWidth(), fillBnd.getHeight(), 0, connectionCell);

		FixpTransform conTransOut = conNi.transformOut();
		FixpTransform fillTransOutToCon = fillNi.transformOut(); // Don't want
																	// to
																	// calculate
																	// transformation
																	// to top
		FixpTransform fillTransIn = fillNi.transformIn(conNi.transformIn());

		InteractiveRouter router = new SimpleWirer(ep);
		router.setTool(FillGeneratorTool.getTool());
		boolean rotated = (fillGen.masters.get(0) != null && fillGen.masters.get(0).getVar("ROTATED_MASTER") != null);
		FillGenJobContainer container = new FillGenJobContainer(router, fillCell, fillNi, connectionCell,
				conNi, fillGenConfig.drcSpacingRule, rotated);

		// Checking if any arc in FillCell collides with rest of the cells
		if (!fillGenConfig.hierarchy) {
			FixpTransform fillTransOut = fillNi.transformOut(conTransOut);
			removeOverlappingBars(container, fillTransOut);
		}

		// Export all fillCell exports in connectCell before extra exports are
		// added into fillCell
		List<Export> gndList = new ArrayList<Export>();
		List<Export> vddList = new ArrayList<Export>();
		for (Iterator<Export> it = container.fillCell.getExports(); it.hasNext();) {
			Export export = it.next();
			PortInst p = container.fillNi.findPortInstFromProto(export);
			Export e = Export.newInstance(container.connectionCell, p, p.getPortProto().getName(), ep);
			e.setCharacteristic(p.getPortProto().getCharacteristic());
			switch (p.getPortProto().getCharacteristic()) {
			case GND:
				gndList.add(e);
				break;
			case PWR:
				vddList.add(e);
				break;
			default:
				assert (false); // invalid case
			}
		}
		if (vddList.isEmpty() && gndList.isEmpty()) {
			System.out.println("Error: No vdd/gnd exports in master cell.");
			return false;
		}

		// List<PolyBase> polyList = new ArrayList<PolyBase>();
		// List<Geometric> gList = new ArrayList<Geometric>();
		List<Object> errorList = new ArrayList<Object>();

		if (fillGenConfig.fillCellType == FillGenConfig.FillGenType.ONLYSKILL) {
			return true;
		} else if (fillGenConfig.fillCellType == FillGenConfig.FillGenType.SEAGATES) {
			// trying Sea-of-Gates router for now
			SeaOfGatesEngine seaGrouter = SeaOfGatesEngineFactory.createSeaOfGatesEngine();
			List<ArcInst> arcsToRoute = new ArrayList<ArcInst>();
			Map<Export, Point2D> centerMapGnd = new HashMap<Export, Point2D>(gndList.size());
			Map<Export, Point2D> centerMapVdd = new HashMap<Export, Point2D>(vddList.size());

			for (PortConfig p : portList) {
				// On exports for now only
				Export ex = p.e;
				// if (p.p.getPortProto() instanceof Export)
				{
					// Export ex = (Export)p.p.getPortProto();
					List<Export> list = (ex.getCharacteristic() == PortCharacteristic.GND) ? gndList
							: vddList;
					Export fillE = null;
					// Search for the closest pin in
					if (ex.getCharacteristic() == PortCharacteristic.GND)
						fillE = searchForClosestPort(p.p, gndList, centerMapGnd, conTransOut);
					else fillE = searchForClosestPort(p.p, vddList, centerMapVdd, conTransOut);

					if (fillE == null) {
						System.out.println("It couldn't find closest port for " + p.e.getName());
						continue;
					}
					PortInst pi = conNi.findPortInst(fillE.getName());
					assert (pi != null);

					ArcProto ap = Generic.tech().unrouted_arc;
					ArcInst ai = ArcInst.newInstanceBase(ap, ep, ap.getDefaultLambdaBaseWidth(ep), pi, p.p);
					arcsToRoute.add(ai);
				}

			}

			seaGrouter.setPrefs(lcp.seaIfGatesPrefs);
			seaGrouter.routeIt(SeaOfGatesHandlers.getDefault(topCell, null, SoGContactsStrategy.SOGCONTACTSATTOPLEVEL, this, ep), topCell, false, arcsToRoute);
			return true;
		}

		// First attempt if ports are below a power/ground bars
		for (PortConfig p : portList) {
			Rectangle2D rect = null;
			// Transformation of the cell instance containing this port
			FixpTransform trans = null; // null if the port is on the top cell

			if (p.p.getPortProto() instanceof Export) {
				Export ex = (Export) p.p.getPortProto();
				assert (ex == p.e); // Should I get rid of this condition and
									// works only with p.e or just p.p?
				Cell exportCell = (Cell) p.p.getNodeInst().getProto();
				// Supposed to work only with metal layers
				// This is extremely expensive
				if (!fillGenConfig.onlyAround)
					rect = LayerCoverageTool.getGeometryOnNetwork(exportCell, ex.getOriginalPort(), p.l, lcp);
				else {
					// This recta
					rect = p.pPoly.getBounds2D();
					// DBMath.transformRect(rect,
					// p.p.getNodeInst().transformIn()); // bring export down to
					// the subcell
				}
				trans = p.p.getNodeInst().transformOut();
			} else // port on pins
			rect = (Rectangle2D) p.p.getNodeInst().getBounds().clone(); // just
																		// to be
																		// cloned
																		// due
																		// to
																		// changes
																		// inside
																		// function

			// Looking to detect any possible contact based on overlap between
			// this geometry and fill
			Rectangle2D backupRect = (Rectangle2D) rect.clone();
			NodeInst added = null;

			// polyList.clear();
			// gList.clear();
			// polyList.add(p.pPoly);
			// gList.add(p.p.getNodeInst());
			errorList.clear();
			errorList.add(p.pPoly);
			errorList.add(p.p.getNodeInst());

			if (!fillGenConfig.onlyAround) {
				added = addAllPossibleContacts(container, p, rect, trans, fillTransIn, fillTransOutToCon,
						conTransOut, areaVisitor.exclusionArea);

				if (added != null) {
					log.logMessage(p.p.describe(false) + " connected", errorList, topCell, 0, false);
					continue;
				}
			}

			// Trying the closest arc
			rect = backupRect;
			rect = p.pPoly.getBounds2D();
			// double searchWidth =
			// fillGen.masters.get(0).getBounds().getWidth();
			// rect = new Rectangle2D.Double(rect.getX()-searchWidth/2,
			// rect.getY(), rect.getWidth()+searchWidth/2,
			// backupRect.getHeight());
			rect = new Rectangle2D.Double(rect.getX() - fillGenConfig.gap, rect.getY(), rect.getWidth()
					+ fillGenConfig.gap * 2, backupRect.getHeight());
			added = addAllPossibleContactsOverPort(container, p, rect, null, // trans,
					fillTransIn, null, true);

			if (added == null) // trying extension with different width
			{
				double searchWidth = fillGen.masters.get(0).getBounds().getWidth();
				rect = p.pPoly.getBounds2D();
				rect = new Rectangle2D.Double(rect.getX() - searchWidth / 2, rect.getY(), rect.getWidth()
						+ searchWidth / 2, backupRect.getHeight());
				added = addAllPossibleContactsOverPort(container, p, rect, null, // trans,
						fillTransIn, null, false);
			}

			if (added != null) {
				log.logMessage(p.p.describe(false) + " connected by extension", errorList, topCell, 0, false);
				continue;
			} else {
				log.logMessage(p.p.describe(false) + " not connected", errorList, topCell, 0, true);
			}
		}

		// Checking if ports not falling over power/gnd bars can be connected
		// using existing contacts
		// along same X axis
		// PortInst[] ports = new PortInst[portNotReadList.size()];
		// portNotReadList.toArray(ports);
		// portNotReadList.clear();
		// Rectangle2D[] rects = new Rectangle2D[ports.length];
		// bndNotReadList.toArray(rects);
		// bndNotReadList.clear();
		//
		// for (int i = 0; i < ports.length; i++)
		// {
		// PortInst p = ports[i];
		// Rectangle2D portBnd = rects[i];
		// NodeInst minNi = connectToExistingContacts(p, portBnd,
		// fillContactList, fillPortInstList);
		//
		// if (minNi != null)
		// {
		// int index = fillContactList.indexOf(minNi);
		// PortInst fillNiPort = fillPortInstList.get(index);
		// // Connecting the export in the top cell
		// Route exportRoute = router.planRoute(topCell, p, fillNiPort,
		// new Point2D.Double(p.getBounds().getCenterX(),
		// p.getBounds().getCenterY()), null, false);
		// Router.createRouteNoJob(exportRoute, topCell, true, false, null);
		// }
		// else
		// {
		// portNotReadList.add(p);
		// bndNotReadList.add(rects[i]);
		// }
		// }
		//
		// // If nothing works, try to insert contacts in location with same Y
		// // Cleaning fillContacts so it doesn't try again with the same sets
		// fillPortInstList.clear();
		// fillContactList.clear();
		// for (int i = 0; i < portNotReadList.size(); i++)
		// {
		// PortInst p = portNotReadList.get(i);
		// Rectangle2D r = bndNotReadList.get(i);
		// double newWid = r.getWidth()+globalWidth;
		// Rectangle2D rect = new Rectangle2D.Double(r.getX()-newWid, r.getY(),
		// 2*newWid, r.getHeight()); // copy the rectangle to add extra width
		//
		// // Check possible new contacts added
		// NodeInst minNi = connectToExistingContacts(p, rect, fillContactList,
		// fillPortInstList);
		// if (minNi != null)
		// {
		// int index = fillContactList.indexOf(minNi);
		// PortInst fillNiPort = fillPortInstList.get(index);
		// // Connecting the export in the top cell
		// Route exportRoute = router.planRoute(topCell, p, fillNiPort,
		// new Point2D.Double(p.getBounds().getCenterX(),
		// p.getBounds().getCenterY()), null, false);
		// Router.createRouteNoJob(exportRoute, topCell, true, false, null);
		// }
		// else
		// {
		// // Searching arcs again
		// Geometric geom = routeToClosestArc(container, p, rect, 10,
		// fillTransOut);
		// if (geom == null)
		// {
		// ErrorLogger.MessageLog l = log.logError(p.describe(false) +
		// " not connected", topCell, 0);
		// l.addPoly(p.getPoly(), true, topCell);
		// if (p.getPortProto() instanceof Export)
		// l.addExport((Export)p.getPortProto(), true, topCell, null);
		// l.addGeom(p.getNodeInst(), true, fillCell, null);
		// }
		// }
		// }
		return true;
	}

	/**
	 * Method to detect which fill nodes are overlapping in the top cell.
	 *
	 * @param cell
	 * @param fillTransUp
	 *            matrix
	 */
	private boolean detectOverlappingBars(Cell cell, FixpTransform fillTransUp,
			HashSet<Geometric> nodesToRemove, FillGenJobContainer container) {
		List<Layer.Function> tmp = new ArrayList<Layer.Function>();

		// Check if any metalXY must be removed
		for (Iterator<NodeInst> itNode = cell.getNodes(); itNode.hasNext();) {
			NodeInst ni = itNode.next();

			if (NodeInst.isSpecialNode(ni))
				continue;

			tmp.clear();
			NodeProto np = ni.getProto();
			if (ni.isCellInstance()) {
				Cell subCell = (Cell) ni.getProto();
				FixpTransform subTransUp = ni.transformOut(fillTransUp);
				// No need of checking the rest of the elements if first one is
				// detected.
				if (detectOverlappingBars(subCell, subTransUp, nodesToRemove, container)) {
					if (cell == container.fillCell)
						nodesToRemove.add(ni);
					else return true;
				}
				continue;
			}
			PrimitiveNode pn = (PrimitiveNode) np;
			if (pn.getFunction().isPin())
				continue; // pins have pseudo layers

			for (Technology.NodeLayer tlayer : pn.getNodeLayers()) {
				tmp.add(tlayer.getLayer().getFunction());
			}
			Rectangle2D rect = getSearchRectangle(ni.getBounds(), fillTransUp, container.drcSpacing);
			if (searchCollision(topCell, rect, new Layer.Function.Set(tmp), null, new NodeInst[] {
					container.fillNi, container.connectionNi }, null, null)) {
				// Direct on last top fill cell
				if (cell == container.fillCell)
					nodesToRemove.add(ni);
				else return true; // time to delete parent NodeInst
			}
		}

		// Checking if any arc in FillCell collides with rest of the cells
		for (Iterator<ArcInst> itArc = cell.getArcs(); itArc.hasNext();) {
			ArcInst ai = itArc.next();
			Layer.Function.Set thisLayer = new Layer.Function.Set(ai.getProto().getLayer(0).getFunction());
			// Searching box must reflect DRC constrains
			Rectangle2D rect = getSearchRectangle(ai.getBounds(), fillTransUp, container.drcSpacing);
			if (searchCollision(topCell, rect, thisLayer, null, new NodeInst[] { container.fillNi,
					container.connectionNi }, null, null)) {
				if (cell == container.fillCell) {
					nodesToRemove.add(ai);
					// Remove exports and pins as well
					nodesToRemove.add(ai.getTail().getPortInst().getNodeInst());
					nodesToRemove.add(ai.getHead().getPortInst().getNodeInst());
				} else return true; // time to delete parent NodeInst.
			}
		}
		return false;
	}

	private void removeOverlappingBars(FillGenJobContainer container, FixpTransform fillTransOut) {
		// Check if any metalXY must be removed
		HashSet<Geometric> nodesToRemove = new HashSet<Geometric>();

		// This function should replace NodeInsts for temporary cells that don't
		// have elements overlapping
		// the standard fill cells.
		// DRC conditions to detect overlap otherwise too many elements/cells
		// might be discarded.
		detectOverlappingBars(container.fillCell, fillTransOut, nodesToRemove, container);

		for (Geometric geo : nodesToRemove) {
			System.out.println("Removing " + geo);
			if (geo instanceof NodeInst)
				((NodeInst) geo).kill();
			else ((ArcInst) geo).kill();
		}
	}

	// private NodeInst connectToExistingContacts(PortInst p, Rectangle2D
	// portBnd,
	// List<NodeInst> fillContactList, List<PortInst> fillPortInstList)
	// {
	// double minDist = Double.POSITIVE_INFINITY;
	// NodeInst minNi = null;
	//
	// for (int j = 0; j < fillContactList.size(); j++)
	// {
	// NodeInst ni = fillContactList.get(j);
	// PortInst fillNiPort = fillPortInstList.get(j);
	// // Checking only the X distance between a placed contact and the port
	// Rectangle2D contBox = ni.getBounds();
	//
	// // check if contact is connected to the same grid
	// if (fillNiPort.getPortProto().getCharacteristic() !=
	// p.getPortProto().getCharacteristic())
	// continue; // no match in network type
	//
	// // If they are not aligned on Y, discard
	// if (!DBMath.areEquals(contBox.getCenterY(), portBnd.getCenterY()))
	// continue;
	// double pdx = Math.abs(Math.max(contBox.getMinX()-portBnd.getMaxX(),
	// portBnd.getMinX()-contBox.getMaxX()));
	// if (pdx < minDist)
	// {
	// minNi = ni;
	// minDist = pdx;
	// }
	// }
	// return minNi;
	// }

	private class FillGenJobContainer {
		InteractiveRouter router;
		Cell fillCell, connectionCell;
		NodeInst fillNi, connectionNi;
		List<PortInst> fillPortInstList;
		List<NodeInst> fillContactList;
		double drcSpacing;
		boolean rotated; // tmp fix

		FillGenJobContainer(InteractiveRouter r, Cell fC, NodeInst fNi, Cell cC, NodeInst cNi,
				double drcSpacing, boolean rotated) {
			this.router = r;
			this.fillCell = fC;
			this.fillNi = fNi;
			this.connectionCell = cC;
			this.connectionNi = cNi;
			this.fillPortInstList = new ArrayList<PortInst>();
			this.fillContactList = new ArrayList<NodeInst>();
			this.drcSpacing = drcSpacing;
			this.rotated = rotated;
		}
	}

	/**
	 * THIS METHOD ASSUMES contactAreaOrig is horizontal!
	 */
	/**
	 * Method to search for all overlaps with metal bars in the fill
	 *
	 * @param searchCell
	 * @param rotated
	 *            true if original fill cell is rotated. This should be a
	 *            temporary fix.
	 * @param handler
	 *            structure containing elements that overlap with the given
	 *            contactAreaOrig
	 * @param closestHandler
	 *            structure containing elements that are close to the given
	 *            contactAreaOrig
	 * @param p
	 * @param contactAreaOrig
	 * @param downTrans
	 * @param upTrans
	 * @return true if overlap was found
	 */
	private boolean searchOverlapHierarchically(Cell searchCell, boolean rotated, GeometryHandler handler,
			GeometryHandler closestHandler, PortConfig p, Rectangle2D contactAreaOrig,
			FixpTransform downTrans, FixpTransform upTrans) {
        EditingPreferences ep = getEditingPreferences();
        Rectangle2D contactArea = new Rectangle2D.Double(contactAreaOrig.getX(), contactAreaOrig.getY(), 
        		contactAreaOrig.getWidth(), contactAreaOrig.getHeight()); // contactAreaOrig.clone())
		DBMath.transformRect(contactArea, downTrans);
		Netlist fillNetlist = searchCell.getNetlist();
		double contactAreaHeight = contactArea.getHeight();
		double contactAreaWidth = contactArea.getWidth();
		// Give high priority to lower arcs
		HashMap<Layer, List<ArcInst>> protoMap = new HashMap<Layer, List<ArcInst>>();
		boolean noIntermediateCells = false;
		TechType techType = fillGenConfig.getTechType();

		for (Iterator<Geometric> it = searchCell.searchIterator(contactArea); it.hasNext();) {
			// Check if there is a contact on that place already!
			Geometric geom = it.next();

			if (geom instanceof NodeInst) {
				NodeInst ni = (NodeInst) geom;
				if (!ni.isCellInstance())
					continue;

				FixpTransform fillIn = ni.transformIn();
				FixpTransform fillUp = ni.transformOut(upTrans);
				// In case of being a cell
				if (searchOverlapHierarchically((Cell) ni.getProto(), rotated, handler, closestHandler, p,
						contactArea, fillIn, fillUp))
					noIntermediateCells = true;
				continue;
			}

			ArcInst ai = (ArcInst) geom;
			ArcProto ap = ai.getProto();
			Network arcNet = fillNetlist.getNetwork(ai, 0);

			// No export with the same characteristic found in this netlist
			if (arcNet.findExportWithSameCharacteristic(p.e) == null)
				continue; // no match in network type

			if (ap == techType.m2() || ap == techType.m1() || !ap.getFunction().isMetal())
				continue;

			// if (ap != Tech.m3)
			// {
			// System.out.println("picking  metal");
			// continue; // Only metal 3 arcs
			// }

			// Adding now
			Layer layer = ap.getLayerIterator().next();
			List<ArcInst> list = protoMap.get(layer);
			if (list == null) {
				list = new ArrayList<ArcInst>();
				protoMap.put(layer, list);
			}
			list.add(ai);
		}

		if (noIntermediateCells)
			return true; // done already down in the hierarchy

		// Assign priority to lower metal bars. eg m3 instead of m4
		Set<Layer> results = protoMap.keySet();
		List<Layer> listOfLayers = new ArrayList<Layer>(results.size());
		listOfLayers.addAll(results);
        Layer.getLayersSortedByRule(listOfLayers, Layer.LayerSortingType.ByName);
		double closestDist = Double.POSITIVE_INFINITY;
		Rectangle2D closestRect = null;

		// Give priority to port layer (p.l)
		int index = listOfLayers.indexOf(p.l);
		if (index > -1) {
			Layer first = listOfLayers.get(0);
			listOfLayers.set(0, p.l);
			listOfLayers.set(index, first);
		}

		// Now select possible pins
		for (Layer layer : listOfLayers) {
			ArcProto ap = findArcProtoFromLayer(layer);
			boolean horizontalBar = (rotated) ? (ap == techType.m3() || ap == techType.m5())
					: (ap == techType.m4() || ap == techType.m6());
			PrimitiveNode defaultContact = null;

			if (horizontalBar) {
				// continue;
				if ((!rotated && ap == techType.m4()) || (rotated && ap == techType.m3()))
					defaultContact = techType.m3m4();
				else if ((!rotated && ap == techType.m6()) || (rotated && ap == techType.m5()))
					defaultContact = techType.m4m5();
				else assert (false);
			}

			boolean found = false;

			Layer theLayer = null;
			for (ArcInst ai : protoMap.get(layer)) {
				Rectangle2D geomBnd = ai.getBounds();
				theLayer = layer;

				// Add only the piece that overlap. If more than 1 arc covers
				// the same area -> only 1 contact
				// will be added.
				Rectangle2D newElem = null;
				double usefulBar, newElemMin, newElemMax, areaMin, areaMax, geoMin, geoMax;

				if (horizontalBar) {
					if (layer != p.l)
						continue; // only when they match in layer so the same
									// contacts can be used
					usefulBar = geomBnd.getHeight();
					// search for the contacts m3m4 at least
					Network net = fillNetlist.getNetwork(ai, 0);
					List<NodeInst> nodes = new ArrayList<NodeInst>();

					// get contact nodes in the same network
					for (Iterator<NodeInst> it = searchCell.getNodes(); it.hasNext();) {
						NodeInst ni = it.next();
						Rectangle2D r = ni.getBounds();
						// only contacts
						if (!ni.getProto().getFunction().isContact())
							continue;
						// Only those that overlap 100% with the contact
						// otherwise it would add zig-zag extra metals
						if (!r.intersects(geomBnd))
							continue;
						for (Iterator<PortInst> pit = ni.getPortInsts(); pit.hasNext();) {
							PortInst pi = pit.next();
							// Only those
							if (fillNetlist.getNetwork(pi) == net) {
								nodes.add(ni);
								break; // stop the loop here
							}
						}
					}
					// No contact on that bar
					if (nodes.size() == 0)
						newElem = new Rectangle2D.Double(contactArea.getX(), geomBnd.getY(), defaultContact
								.getDefWidth(ep), usefulBar);
					else {
						// better if find a vertical bar by closest distance
						// continue;
						// search for closest distance or I could add all!!
						// Taking the first element for now
						NodeInst ni = nodes.get(0);
						// Check lower layer of the contact so it won't add
						// unnecessary contacts
						PrimitiveNode np = (PrimitiveNode) ni.getProto();
						// layerTmpList.clear();
						// for (Iterator<Layer> it = np.getLayerIterator();
						// it.hasNext(); )
						// {
						// Layer l = it.next();
						// if (l.getFunction().isMetal())
						// layerTmpList.add(l);
						// }
						// Collections.sort(layerTmpList,
						// Layer.layerSortByName);
						// theLayer = layerTmpList.get(0);
						Rectangle2D r = ni.getBounds();
						double contactW = ni.getXSizeWithoutOffset();
						double contactH = ni.getYSizeWithoutOffset();
						r = new Rectangle2D.Double(r.getCenterX() - contactW / 2, contactArea.getY(),
								contactW, contactAreaHeight);
						geomBnd = r;
						newElem = geomBnd;
					}
					newElemMin = newElem.getMinY();
					newElemMax = newElem.getMaxY();
					areaMin = contactArea.getMinY();
					areaMax = contactArea.getMaxY();
					geoMin = geomBnd.getMinY();
					geoMax = geomBnd.getMaxY();
				} else {
					if (rotated) {
						usefulBar = geomBnd.getHeight();
						newElem = new Rectangle2D.Double(contactArea.getX(), geomBnd.getY(),
								contactAreaWidth, usefulBar);
						newElemMin = newElem.getMinY();
						newElemMax = newElem.getMaxY();
						areaMin = contactArea.getMinY();
						areaMax = contactArea.getMaxY();
						geoMin = geomBnd.getMinY();
						geoMax = geomBnd.getMaxY();
					} else {
						usefulBar = geomBnd.getWidth();
						newElem = new Rectangle2D.Double(geomBnd.getX(), contactArea.getY(), usefulBar,
								contactAreaHeight);
						newElemMin = newElem.getMinX();
						newElemMax = newElem.getMaxX();
						areaMin = contactArea.getMinX();
						areaMax = contactArea.getMaxX();
						geoMin = geomBnd.getMinX();
						geoMax = geomBnd.getMaxX();
					}
				}

				// Don't consider no overlapping areas
				if (newElemMax < areaMin || areaMax < newElemMin)
					continue;
				boolean containMin = newElemMin <= areaMin && areaMin <= newElemMax;
				boolean containMax = newElemMin <= areaMax && areaMax <= newElemMax;

				// Either end is not contained otherwise the contact is fully
				// contained by the arc
				if (!containMin || !containMax) {
					// Getting the intersection along X/Y axis. Along YX it
					// should cover completely
					assert (geoMin == newElemMin);
					assert (geoMax == newElemMax);

					double min = Math.max(geoMin, areaMin);
					double max = Math.min(geoMax, areaMax);
					double diff = max - min;
					double overlap = (diff) / usefulBar;
					// Checking if new element is completely inside the
					// contactArea otherwise routeToClosestArc could add
					// the missing contact
					if (overlap < fillGenConfig.minOverlap) {
						System.out.println("Not enough overlap (" + overlap + ") in " + ai + " to cover "
								+ p.p);
						double val = Math.abs(diff);
						if (closestDist > val) // only in this case the elements
												// are close enough but not
												// touching
						{
							closestDist = val;
							closestRect = newElem;
						}
						continue;
					}
				}
				// Transforming geometry up to fillCell coordinates
				DBMath.transformRect(newElem, upTrans);
				// Adding element
				handler.add(theLayer, newElem);
				found = true;
			}
			if (found)
				return true; // only one set for now if something overlapping
								// was found

			if (horizontalBar || closestRect == null)
				continue;
			// trying with closest vertical arcs
			// Transforming geometry up to fillCell coordinates
			DBMath.transformRect(closestRect, upTrans);
			// Adding element
			closestHandler.add(theLayer, closestRect);
			// return true;
		}
		return false;
	}

	private static class FillGenArcConnect {
		Rectangle2D rect;
		ArcInst ai;

		FillGenArcConnect(Rectangle2D r, ArcInst ai) {
			this.rect = r;
			this.ai = ai;
		}
	}

	/**
	 * Second try
	 *
	 * @param searchCell
	 * @param rotated
	 * @param p
	 * @param contactAreaOrig
	 * @param downTrans
	 * @param upTrans
	 * @return Export object that overlaps with the given geometry
	 */
	private Export searchOverlapHierarchicallyOverPort(FillGenJobContainer container, Cell searchCell,
			boolean rotated, PortConfig p, Rectangle2D contactAreaOrig, FixpTransform downTrans,
			FixpTransform upTrans, boolean noClosestPin) {
        EditingPreferences ep = getEditingPreferences();
		Rectangle2D contactArea = (Rectangle2D) contactAreaOrig.clone();
		DBMath.transformRect(contactArea, downTrans);
		Netlist fillNetlist = searchCell.getNetlist();
		double contactAreaHeight = contactArea.getHeight();
		double contactAreaWidth = contactArea.getWidth();
		// Give high priority to lower arcs
		HashMap<Layer, List<ArcInst>> protoMap = new HashMap<Layer, List<ArcInst>>();
		boolean noIntermediateCells = false;
		TechType techType = fillGenConfig.getTechType();

		for (Iterator<Geometric> it = searchCell.searchIterator(contactArea); it.hasNext();) {
			// Check if there is a contact on that place already!
			Geometric geom = it.next();

			if (geom instanceof NodeInst) {
				NodeInst ni = (NodeInst) geom;
				if (!ni.isCellInstance())
					continue;

				FixpTransform fillIn = ni.transformIn();
				FixpTransform fillUp = ni.transformOut(upTrans);
				// In case of being a cell
				Export export = searchOverlapHierarchicallyOverPort(container, (Cell) ni.getProto(), rotated,
						p, contactArea, fillIn, fillUp, noClosestPin);
				if (export != null) {
					PortInst pinPort = ni.findPortInstFromProto(export);
					Export pinExport = Export.newInstance(searchCell, pinPort, "proj-" + p.e.getName(), ep);
					pinExport.setCharacteristic(p.e.getCharacteristic());
					noIntermediateCells = true;
					return pinExport;
				}
				continue;
			}

			ArcInst ai = (ArcInst) geom;
			ArcProto ap = ai.getProto();
			Network arcNet = fillNetlist.getNetwork(ai, 0);

			// No export with the same characteristic found in this netlist
			if (arcNet.findExportWithSameCharacteristic(p.e) == null)
				continue; // no match in network type

			if (ap == techType.m2() || ap == techType.m1() || !ap.getFunction().isMetal())
				continue;

			// Adding now
			Layer layer = ap.getLayerIterator().next();
			List<ArcInst> list = protoMap.get(layer);
			if (list == null) {
				list = new ArrayList<ArcInst>();
				protoMap.put(layer, list);
			}
			list.add(ai);
		}

		if (noIntermediateCells)
			assert (false); // it shouldn't reach this point?

		// Assign priority to lower metal bars. eg m3 instead of m4
		Set<Layer> results = protoMap.keySet();
		List<Layer> listOfLayers = new ArrayList<Layer>(results.size());
		listOfLayers.addAll(results);
        Layer.getLayersSortedByRule(listOfLayers, Layer.LayerSortingType.ByName);
		double closestDist = Double.POSITIVE_INFINITY;
		Rectangle2D closestRect = null;
		ArcInst closestArc = null;

		// Give priority to port layer (p.l)
		int index = listOfLayers.indexOf(p.l);
		if (index > -1) {
			Layer first = listOfLayers.get(0);
			listOfLayers.set(0, p.l);
			listOfLayers.set(index, first);
		}

		// Now select possible pins
		for (Layer layer : listOfLayers) {
			ArcProto ap = findArcProtoFromLayer(layer);
			boolean horizontalBar = (rotated) ? (ap == techType.m3() || ap == techType.m5())
					: (ap == techType.m4() || ap == techType.m6());
			PrimitiveNode defaultContact = null;

			if (horizontalBar) {
				// continue;
				if ((!rotated && ap == techType.m4()) || (rotated && ap == techType.m3()))
					defaultContact = techType.m3m4();
				else if ((!rotated && ap == techType.m6()) || (rotated && ap == techType.m5()))
					defaultContact = techType.m4m5();
				else assert (false);
			}

			// Layer theLayer = null;
			// sort the elements in the list otherwise the results are not
			// deterministic
			List<ArcInst> theSortedList = protoMap.get(layer);
			Collections.sort(theSortedList, new ArcInst.ArcsByLength());
			for (ArcInst ai : theSortedList) // protoMap.get(layer))
			{
				Rectangle2D geomBnd = ai.getBounds();
				// Layer theLayer = layer;

				// Add only the piece that overlap. If more than 1 arc covers
				// the same area -> only 1 contact
				// will be added.
				Rectangle2D newElem = null;
				double usefulBar, newElemMin, newElemMax, areaMin, areaMax, geoMin, geoMax;

				if (horizontalBar) {
					if (layer != p.l)
						continue; // only when they match in layer so the same
									// contacts can be used
					usefulBar = geomBnd.getHeight();
					// search for the contacts m3m4 at least
					Network net = fillNetlist.getNetwork(ai, 0);
					List<NodeInst> nodes = new ArrayList<NodeInst>();

					// get contact nodes in the same network
					for (Iterator<NodeInst> it = searchCell.getNodes(); it.hasNext();) {
						NodeInst ni = it.next();
						Rectangle2D r = ni.getBounds();
						// only contacts
						if (!ni.getProto().getFunction().isContact())
							continue;
						// Only those that overlap 100% with the contact
						// otherwise it would add zig-zag extra metals
						if (!r.intersects(geomBnd))
							continue;
						for (Iterator<PortInst> pit = ni.getPortInsts(); pit.hasNext();) {
							PortInst pi = pit.next();
							// Only those
							if (fillNetlist.getNetwork(pi) == net) {
								nodes.add(ni);
								break; // stop the loop here
							}
						}
					}
					// No contact on that bar
					if (nodes.size() == 0)
						newElem = new Rectangle2D.Double(contactArea.getX(), geomBnd.getY(), defaultContact
								.getDefWidth(ep), usefulBar);
					else {
						// better if find a vertical bar by closest distance
						// continue;
						// search for closest distance or I could add all!!
						// Taking the first element for now
						NodeInst ni = nodes.get(0);
						// Check lower layer of the contact so it won't add
						// unnecessary contacts
						PrimitiveNode np = (PrimitiveNode) ni.getProto();
						// layerTmpList.clear();
						// for (Iterator<Layer> it = np.getLayerIterator();
						// it.hasNext(); )
						// {
						// Layer l = it.next();
						// if (l.getFunction().isMetal())
						// layerTmpList.add(l);
						// }
						// Collections.sort(layerTmpList,
						// Layer.layerSortByName);
						// theLayer = layerTmpList.get(0);
						Rectangle2D r = ni.getBounds();
						double contactW = ni.getXSizeWithoutOffset();
						double contactH = ni.getYSizeWithoutOffset();
						r = new Rectangle2D.Double(r.getCenterX() - contactW / 2, contactArea.getY(),
								contactW, contactAreaHeight);
						geomBnd = r;
						newElem = geomBnd;
					}
					newElemMin = newElem.getMinY();
					newElemMax = newElem.getMaxY();
					areaMin = contactArea.getMinY();
					areaMax = contactArea.getMaxY();
					geoMin = geomBnd.getMinY();
					geoMax = geomBnd.getMaxY();
				} else {
					if (rotated) {
						usefulBar = geomBnd.getHeight();
						newElem = new Rectangle2D.Double(contactArea.getX(), geomBnd.getY(),
								contactAreaWidth, usefulBar);
						newElemMin = newElem.getMinY();
						newElemMax = newElem.getMaxY();
						areaMin = contactArea.getMinY();
						areaMax = contactArea.getMaxY();
						geoMin = geomBnd.getMinY();
						geoMax = geomBnd.getMaxY();
					} else {
						usefulBar = geomBnd.getWidth();
						newElem = new Rectangle2D.Double(geomBnd.getX(), contactArea.getY(), usefulBar,
								contactAreaHeight);
						newElemMin = newElem.getMinX();
						newElemMax = newElem.getMaxX();
						areaMin = contactArea.getMinX();
						areaMax = contactArea.getMaxX();
						geoMin = geomBnd.getMinX();
						geoMax = geomBnd.getMaxX();
					}
				}

				// Don't consider no overlapping areas
				if (newElemMax < areaMin || areaMax < newElemMin)
					continue;
				boolean containMin = newElemMin <= areaMin && areaMin <= newElemMax;
				boolean containMax = newElemMin <= areaMax && areaMax <= newElemMax;

				// Either end is not contained otherwise the contact is fully
				// contained by the arc
				if (!containMin || !containMax) {
					// Getting the intersection along X/Y axis. Along YX it
					// should cover completely
					assert (geoMin == newElemMin);
					assert (geoMax == newElemMax);

					double min = Math.max(geoMin, areaMin);
					double max = Math.min(geoMax, areaMax);
					double diff = max - min;
					double overlap = (diff) / usefulBar;
					// Checking if new element is completely inside the
					// contactArea otherwise routeToClosestArc could add
					// the missing contact
					if (overlap < fillGenConfig.minOverlap || !noClosestPin) {
						if (noClosestPin)
							System.out.println("Not enough overlap (" + overlap + ") in " + ai + " to cover "
									+ p.p);
						double val = Math.abs(diff);
						if (closestDist > val) // only in this case the elements
												// are close enough but not
												// touching
						{
							closestDist = val;
							closestRect = newElem;
							closestArc = ai;
						}
						continue;
					}
				}

				if (noClosestPin) {
					closestDist = 0; // found
					closestRect = newElem;
					closestArc = ai;
					break;
				}
			}

			if (noClosestPin && closestRect != null)
				break;
			if (horizontalBar || closestRect == null)
				continue;
		}
		if (closestRect != null) {
			if (!noClosestPin)
				System.out.println("Selecting a closest arc!");
			PrimitiveNode thePin = findPrimitiveNodeFromLayer(closestArc.getProto().getLayerIterator().next());
			NodeInst pinOnArc = LayoutLib.newNodeInst(thePin, ep, closestRect.getCenterX(), closestRect
					.getCenterY(), thePin.getDefWidth(ep), contactAreaHeight, 0, closestArc.getParent());
			EPoint center = pinOnArc.getOnlyPortInst().getCenter();
			Route exportRoute = container.router.planRoute(closestArc.getParent(), closestArc.getPortInst(0),
					pinOnArc.getOnlyPortInst(), center, null, ep, false, false, null, null);
			Map<ArcProto, Integer> arcsCreatedMap = new HashMap<ArcProto, Integer>();
			Map<NodeProto, Integer> nodesCreatedMap = new HashMap<NodeProto, Integer>();
			Router.createRouteNoJob(exportRoute, closestArc.getParent(), arcsCreatedMap, nodesCreatedMap, ep);
			Export pinExport = Export.newInstance(closestArc.getParent(), pinOnArc.getOnlyPortInst(), "proj-"
					+ p.e.getName(), ep);
			pinExport.setCharacteristic(p.e.getCharacteristic());
			return pinExport;
		}
		// I might work with the best case
		return null;
	}

	/**
	 * Method to find corresponding metal pin associated to the given layer
	 *
	 * @param layer
	 * @return PrimitiveNode pin for the given layer
	 */
	private static PrimitiveNode findPrimitiveNodeFromLayer(Layer layer) {
		for (PrimitiveNode pin : VddGndStraps.PINS) {
			if (pin != null && layer == pin.getLayerIterator().next()) {
				return pin; // found
			}
		}
		return null;
	}

	/**
	 * Method to find corresponding metal arc associated to the given layer
	 *
	 * @param layer
	 * @return Primitive of a metal arc
	 */
	private static ArcProto findArcProtoFromLayer(Layer layer) {
		for (ArcProto arc : VddGndStraps.METALS) {
			if (arc != null && layer == arc.getLayerIterator().next()) {
				return arc; // found
			}
		}
		return null;
	}

	/**
	 * Method to add all possible contacts in connection cell based on the
	 * overlap of a given metal2 area and fill cell. THIS ONLY WORK if first
	 * fill bar is vertical
	 *
	 * @param container
	 * @param p
	 * @param contactArea
	 * @param nodeTransOut
	 *            null if the port is on the top cell
	 * @param fillTransOutToCon
	 * @return Node instance of a contact added.
	 */
	private NodeInst addAllPossibleContacts(FillGenJobContainer container, PortConfig p,
			Rectangle2D contactArea, FixpTransform nodeTransOut, FixpTransform fillTransIn,
			FixpTransform fillTransOutToCon, FixpTransform conTransOut, Area exclusionArea) {
        EditingPreferences ep = getEditingPreferences();
		// Until this point, contactArea is at the fillCell level
		// Contact area will contain the remaining are to check
		double contactAreaHeight = contactArea.getHeight();

		NodeInst added = null;
		// Transforming rectangle with gnd/power metal into the connection cell
		if (nodeTransOut != null)
			DBMath.transformRect(contactArea, nodeTransOut);
		if (exclusionArea != null && exclusionArea.intersects(contactArea))
			return null; // can't connect here.

		GeometryHandler overlapHandler = GeometryHandler.createGeometryHandler(
				GeometryHandler.GHMode.ALGO_SWEEP, 1);
		GeometryHandler closestHandler = GeometryHandler.createGeometryHandler(
				GeometryHandler.GHMode.ALGO_SWEEP, 1);
		GeometryHandler handler;

		searchOverlapHierarchically(container.fillCell, container.rotated, overlapHandler, closestHandler, p,
				contactArea, fillTransIn, GenMath.MATID);
		handler = overlapHandler;
		handler.postProcess(false);
		closestHandler.postProcess(false);

		Set<Layer> overlapResults = handler.getKeySet();
		Set<Layer> closestResults = closestHandler.getKeySet();

		List<Layer> listOfLayers = new ArrayList<Layer>(overlapResults.size());
		listOfLayers.addAll(overlapResults);
		listOfLayers.addAll(closestResults);
        Layer.getLayersSortedByRule(listOfLayers, Layer.LayerSortingType.ByName);

		int size = listOfLayers.size();

		// assert(size <= 1); // Must contain only m3

		if (size == 0)
			return null;

		Rectangle2D portInConFill = new Rectangle2D.Double();
		portInConFill.setRect(p.pPoly.getBounds2D());
		DBMath.transformRect(portInConFill, fillTransIn);
		TechType techType = fillGenConfig.getTechType();

		// Creating the corresponding export in connectionNi (projection pin)
		// This should be done only once!
		PrimitiveNode thePin = findPrimitiveNodeFromLayer(p.l);
		assert (thePin != null);
		assert (thePin != techType.m1pin()); // should start from m2
		NodeInst pinNode = null;
		PortInst pin = null;

		// Loop along all possible connections (different layers)
		// from both GeometricHandler structures.
		for (Layer layer : listOfLayers) {
			if (!layer.getFunction().isMetal())
				continue; // in case of active arcs!
			Collection set = handler.getObjects(layer, false, true);

			if (set == null || set.size() == 0) // information from
												// closestHandling
				set = closestHandler.getObjects(layer, false, true);

			if (!(set != null && set.size() > 0))
				System.out.println("Assert error");
			// assert (set != null && set.size() > 0);
			if (set == null) {
				System.out.println("Null set");
				continue;
			}
			// Get connecting metal contact (PrimitiveNode) starting from
			// techPin up to the power/vdd bar found
			List<Layer.Function> fillLayers = new ArrayList<Layer.Function>();
			PrimitiveNode topPin = findPrimitiveNodeFromLayer(layer);
			PrimitiveNode topContact = null;
			int start = -1;
			int end = -1;
			for (int i = 0; i < VddGndStraps.PINS.length; i++) {
				if (start == -1 && VddGndStraps.PINS[i] == thePin)
					start = i;
				if (end == -1 && VddGndStraps.PINS[i] == topPin)
					end = i;
			}
			if (start > end) {
				int tmp = start;
				start = end;
				end = tmp;
			}
			for (int i = start; i <= end; i++) {
				fillLayers.add(VddGndStraps.PINS[i].getLayerIterator().next().getFunction());
				if (i < end)
					topContact = VddGndStraps.fillContacts[i];
			}

			// assert(topContact != null);
			boolean horizontalBar = (container.rotated) ? (topPin == techType.m3pin() || topPin == techType
					.m5pin()) : (topPin == techType.m4pin() || topPin == techType.m6pin());

			Layer.Function.Set fillLayersSet = new Layer.Function.Set(fillLayers);
			for (Iterator it = set.iterator(); it.hasNext();) {
				// ALGO_SWEEP retrieves only PolyBase
				PolyBase poly = (PolyBase) it.next();
				Rectangle2D newElemFill = poly.getBounds2D();
				double newElemFillWidth = newElemFill.getWidth();
				double newElemFillHeight = newElemFill.getHeight();

				// Location of new element in fillCell
				Rectangle2D newElemConnect = (Rectangle2D) newElemFill.clone();
				DBMath.transformRect(newElemConnect, fillTransOutToCon);

				// Location of new contact from top cell
				Rectangle2D newElemTop = (Rectangle2D) newElemConnect.clone();
				DBMath.transformRect(newElemTop, conTransOut);

				// Get connecting metal contact (PrimitiveNode) starting from
				// techPin up to the power/vdd bar found
				// Search if there is a collision with existing nodes/arcs
				if (searchCollision(topCell, newElemTop, fillLayersSet, p, new NodeInst[] { container.fillNi,
						container.connectionNi }, null, null))
					continue;

				// The first time but only after at least one element can be
				// placed
				if (pinNode == null) {
					pinNode = LayoutLib.newNodeInst(thePin, ep, portInConFill.getCenterX(), portInConFill
							.getCenterY(), thePin.getDefWidth(ep), contactAreaHeight, 0,
							container.connectionCell);
					pin = pinNode.getOnlyPortInst();
				}

				if (topContact != null) {
					// adding contact
					// center if the overlapping was found by just overlapping
					// of the vertical metal bar
					// boolean center = horizontalBar ||
					// newElemConnect.getCenterY() != contactArea.getCenterY();
					added = horizontalBar ? LayoutLib.newNodeInst(topContact, ep, newElemConnect.getCenterX(),
							newElemConnect.getCenterY(), newElemFillWidth, newElemFillHeight, 0,
							container.connectionCell) : LayoutLib.newNodeInst(topContact, ep, newElemConnect
							.getCenterX(), newElemConnect.getCenterY(), newElemFillWidth, contactAreaHeight,
							0, container.connectionCell);
				} else // on the same layer as thePin
				added = pinNode;

				container.fillContactList.add(added);

				// route new pin instance in connectioNi with new contact
				Route pinExportRoute = container.router.planRoute(container.connectionCell, pin, added
						.getOnlyPortInst(), new Point2D.Double(portInConFill.getCenterX(), portInConFill
						.getCenterY()), null, ep, false, false, null, null);
				Map<ArcProto, Integer> arcsCreatedMap = new HashMap<ArcProto, Integer>();
				Map<NodeProto, Integer> nodesCreatedMap = new HashMap<NodeProto, Integer>();
				Router.createRouteNoJob(pinExportRoute, container.connectionCell, arcsCreatedMap,
						nodesCreatedMap, ep);

				// It was removed by the vertical router
				if (!pin.isLinked()) {
					pinNode = pinExportRoute.getStart().getNodeInst();
					pin = pinExportRoute.getStart().getPortInst();
				}

				// Adding the connection to the fill via the exports.
				// Looking for closest export in fillCell.
				PortInst fillNiPort = null;
				double minDistance = Double.POSITIVE_INFINITY;

				for (Iterator<Export> e = container.fillNi.getExports(); e.hasNext();) {
					Export exp = e.next();
					PortInst port = exp.getOriginalPort();

					// The port characteristics must be identical
					if (port.getPortProto().getCharacteristic() != p.e.getCharacteristic())
						continue;

					Rectangle2D geo = port.getPoly().getBounds2D();
					assert (fillGenConfig.evenLayersHorizontal);
					double deltaX = geo.getCenterX() - newElemConnect.getCenterX();
					double deltaY = geo.getCenterY() - newElemConnect.getCenterY();

					boolean condition = (horizontalBar) ? DBMath.isInBetween(geo.getCenterY(), newElemConnect
							.getMinY(), newElemConnect.getMaxY()) : DBMath.isInBetween(geo.getCenterX(),
							newElemConnect.getMinX(), newElemConnect.getMaxX());
					if (!condition)
						continue; // only align with this so it could guarantee
									// correct arc (M3)
					double dist = Math.sqrt(deltaX * deltaX + deltaY * deltaY);
					if (DBMath.isGreaterThan(minDistance, dist)) {
						minDistance = dist;
						fillNiPort = port;
					}
				}
				if (fillNiPort != null) {
					EPoint center = fillNiPort.getCenter();
					Route exportRoute = container.router.planRoute(container.connectionCell, added
							.getOnlyPortInst(), fillNiPort, center, null, ep, false, false, null, null);
					Router.createRouteNoJob(exportRoute, container.connectionCell, arcsCreatedMap,
							nodesCreatedMap, ep);
				}
			}

			// Done at the end so extra connections would not produce
			// collisions.
			// Routing the new contact to topCell in connectNi instead of top
			// cell
			// Export connect projected pin in ConnectionCell
			if (pinNode != null) // at least done for one
			{
				Export pinExport = Export.newInstance(container.connectionCell, pin, "proj-" + p.e.getName(), ep);
				assert (pinExport != null);
				pinExport.setCharacteristic(p.e.getCharacteristic());
				// Connect projected pin in ConnectionCell with real port
				PortInst pinPort = container.connectionNi.findPortInstFromProto(pinExport);
				Route conTopExportRoute = container.router.planRoute(topCell, p.p, pinPort,
						new Point2D.Double(p.pPoly.getBounds2D().getCenterX(), p.pPoly.getBounds2D()
								.getCenterY()), null, ep, false, false, null, null);
				Map<ArcProto, Integer> arcsCreatedMap = new HashMap<ArcProto, Integer>();
				Map<NodeProto, Integer> nodesCreatedMap = new HashMap<NodeProto, Integer>();
				Router.createRouteNoJob(conTopExportRoute, topCell, arcsCreatedMap, nodesCreatedMap, ep);

				return added;
			}
		}
		return null;
	}

	/**
	 * Method to add all possible contacts in connection cell based on the
	 * overlap of a given metal2 area and fill cell. THIS ONLY WORK if first
	 * fill bar is vertical
	 *
	 * @param container
	 * @param p
	 * @param contactArea
	 * @param nodeTransOut
	 *            null if the port is on the top cell
	 * @return Node instance of a metal contact added.
	 */
	private NodeInst addAllPossibleContactsOverPort(FillGenJobContainer container, PortConfig p,
			Rectangle2D contactArea, FixpTransform nodeTransOut, FixpTransform fillTransIn,
			Area exclusionArea, boolean noClosestPin) {
        EditingPreferences ep = getEditingPreferences();
		// Until this point, contactArea is at the fillCell level
		// Contact area will contain the remaining are to check
		double contactAreaHeight = contactArea.getHeight();

		// Transforming rectangle with gnd/power metal into the connection cell
		if (nodeTransOut != null)
			DBMath.transformRect(contactArea, nodeTransOut);
		if (exclusionArea != null && exclusionArea.intersects(contactArea))
			return null; // can't connect here.

		Export fillExport = searchOverlapHierarchicallyOverPort(container, container.fillCell,
				container.rotated, p, contactArea, fillTransIn, GenMath.MATID, noClosestPin);

		if (fillExport == null)
			return null;

		// fillExpor in connectionCell
		PortInst pinPort = container.fillNi.findPortInstFromProto(fillExport);

		// Creating the corresponding export in connectionNi (projection pin)
		// This should be done only once!
		PrimitiveNode thePin = findPrimitiveNodeFromLayer(p.l);
		TechType techType = fillGenConfig.getTechType();
		assert (thePin != null);
		assert (thePin != techType.m1pin()); // should start from m2
		Rectangle2D portInConFill = new Rectangle2D.Double();
		portInConFill.setRect(p.pPoly.getBounds2D());
		DBMath.transformRect(portInConFill, fillTransIn);
		NodeInst pinNode = LayoutLib.newNodeInst(thePin, ep, portInConFill.getCenterX(), portInConFill
				.getCenterY(), thePin.getDefWidth(ep), contactAreaHeight, 0, container.connectionCell);
		PortInst pin = pinNode.getOnlyPortInst();

		// route new pin instance in connectioNi with new contact
		Route pinExportRoute = container.router.planRoute(container.connectionCell, pin, pinPort,
				new Point2D.Double(portInConFill.getCenterX(), portInConFill.getCenterY()), null, ep, false,
				false, null, null);
		Map<ArcProto, Integer> arcsCreatedMap = new HashMap<ArcProto, Integer>();
		Map<NodeProto, Integer> nodesCreatedMap = new HashMap<NodeProto, Integer>();
		Router.createRouteNoJob(pinExportRoute, container.connectionCell, arcsCreatedMap, nodesCreatedMap, ep);

		// Connecting with top cell

		Export pinExport = Export.newInstance(container.connectionCell, pin, "proj-" + p.e.getName(), ep);
		// assert(pinExport != null);
		if (pinExport != null) {
			pinExport.setCharacteristic(p.e.getCharacteristic());
			// Connect projected pin in ConnectionCell with real port
			PortInst pinTopPort = container.connectionNi.findPortInstFromProto(pinExport);
			Route conTopExportRoute = container.router
					.planRoute(topCell, p.p, pinTopPort, new Point2D.Double(p.pPoly.getBounds2D()
							.getCenterX(), p.pPoly.getBounds2D().getCenterY()), null, ep, false, false, null,
							null);
			Router.createRouteNoJob(conTopExportRoute, topCell, arcsCreatedMap, nodesCreatedMap, ep);
		} else System.out.println("Error here");
		return pinNode;
	}

	/**
	 * Auxiliary class to hold port and its corresponding layer so no extra
	 * calculation has to be done later.
	 */
	protected static class PortConfig {
		PortInst p;
		Export e;
		Layer l;
		Poly pPoly; // to speed up the progress because PortInst.getPoly() calls
					// getShape()

		PortConfig(PortInst p, Export e, Layer l) {
			this.e = e;
			this.p = p;
			this.pPoly = p.getPoly();
			this.l = l;
		}
	}
}
