/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Geometry.java
 * Input/output tool: superclass for output modules that write pure geometry.
 * Written by Steven M. Rubin.
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
package com.sun.electric.tool.io.output;

import com.sun.electric.database.geometry.GeometryHandler;
import com.sun.electric.database.geometry.Poly;
import com.sun.electric.database.geometry.PolyBase;
import com.sun.electric.database.geometry.GeometryHandler.GHMode;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.EDatabase;
import com.sun.electric.database.hierarchy.HierarchyEnumerator;
import com.sun.electric.database.hierarchy.Library;
import com.sun.electric.database.hierarchy.Nodable;
import com.sun.electric.database.id.CellUsage;
import com.sun.electric.database.prototype.NodeProto;
import com.sun.electric.database.topology.ArcInst;
import com.sun.electric.database.topology.Geometric;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.variable.VarContext;
import com.sun.electric.technology.ArcProto;
import com.sun.electric.technology.Layer;
import com.sun.electric.technology.PrimitiveNode;
import com.sun.electric.technology.Technology;
import com.sun.electric.technology.technologies.Generic;
import com.sun.electric.util.math.FixpTransform;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Base class for writing geometry to a file
 */
public abstract class Geometry extends Output
{
    /** number of unique cells processed */             protected int numVisited;
    /** number of unique cells to process */            protected int numCells;
    /** top-level cell being processed */				protected Cell topCell;

    /** HashMap of all CellGeoms */                     protected HashMap<Cell,CellGeom> cellGeoms;

	/**
	 * Class for managing polygons that are associated with geometry.
	 */
    protected static class PolyWithGeom
	{
    	Poly poly;
    	Geometric geom;

    	PolyWithGeom(Poly poly, Geometric geom)
		{
    		this.poly = poly;
    		this.geom = geom;
		}
	}

    /** Creates a new instance of Geometry */
    Geometry()
    {
    }

    /**
     * Write cell to file
     * @return true on error
     */
    public boolean writeCell(Cell cell, VarContext context)
    {
		writeCell(cell, context, new Visitor(this, getMaxHierDepth(cell)));
		return false;
	}

    /**
     * Write cell to file
     * @return true on error
     */
    public boolean writeCell(Cell cell, VarContext context, Visitor visitor)
    {
		// see how many cells we have to write, for progress indication
		numVisited = 0;
		numCells = HierarchyEnumerator.getNumUniqueChildCells(cell) + 1;
		topCell = cell;
		cellGeoms = new HashMap<Cell,CellGeom>();

		// write out cells
		start();
		HierarchyEnumerator.enumerateCell(cell, context, visitor);
//		HierarchyEnumerator.enumerateCell(cell, context, null, visitor);
		done();
		return false;
    }


    /** Abstract method called before hierarchy traversal */
    protected abstract void start();

    /** Abstract method called after traversal */
    protected abstract void done();

    /** Abstract method to write CellGeom to disk */
    protected abstract void writeCellGeom(CellGeom cellGeom);

    /** Overridable method to determine whether or not to merge geometry */
    protected boolean mergeGeom(int hierLevelsFromBottom) { return false; }

    /** Overridable method to determine whether or not to include the original Geometric with a Poly */
    protected boolean includeGeometric() { return false; }

    /**
     * Class to store polygon geometry of a cell
     */
    public static class CellGeom
    {
        /** HashMap of Poly(gons) in this Cell, keyed by Layer, all polys per layer stored as a List */
															protected HashMap<Layer,List<Object>> polyMap;
        /** Nodables (instances) in this Cell */			protected List<Nodable> nodables;
        /** Cell */											protected Cell cell;
		/** true if cell name used in other libraries */	protected boolean nonUniqueName;
		private Geometry geometry;

        /** Constructor */
        protected CellGeom(Cell cell, Geometry geometry)
        {
            polyMap = new HashMap<Layer,List<Object>>();
            nodables = new ArrayList<Nodable>();
			this.cell = cell;
			this.geometry = geometry;
			nonUniqueName = false;
			checkLayoutCell();
			determineUniqueness();
        }

        private void determineUniqueness()
        {
    		for(Iterator<Library> lIt = Library.getLibraries(); lIt.hasNext(); )
    		{
    			Library lib = lIt.next();
    			if (lib.isHidden()) continue;
    			if (lib == cell.getLibrary()) continue;
    			for(Iterator<Cell> cIt = lib.getCells(); cIt.hasNext(); )
    			{
    				Cell oCell = cIt.next();
    				if (cell.getView() != oCell.getView()) continue;
    				if (!cell.getName().equalsIgnoreCase(oCell.getName())) continue;
    				nonUniqueName = true;
    				break;
    			}
    			if (nonUniqueName) break;
    		}
        }

		private void checkLayoutCell()
		{
			NodeProto universalPin = Generic.tech().universalPinNode;
            int numUniversalPins = 0;
			for (int i = 0, numNodes = cell.getNumNodes(); i < numNodes; i++) {
				NodeInst ni = cell.getNode(i);
				NodeProto np = ni.getProto();
				if (np == universalPin) numUniversalPins++;
			}
            if (numUniversalPins > 0)
				System.out.println("Geometry: Layout " + cell + " has " + numUniversalPins +	" " + universalPin.describe(true) + " nodes");

			ArcProto universalArc = Generic.tech().universal_arc;
			int numUniversalArcs = 0;
			ArcProto unroutedArc = Generic.tech().unrouted_arc;
			int numUnroutedArcs = 0;
			for (Iterator<ArcInst> it = cell.getArcs(); it.hasNext(); )
			{
				ArcInst ai = it.next();
				ArcProto ap = ai.getProto();
				if (ap == universalArc) numUniversalArcs++;
				if (ap == unroutedArc) numUnroutedArcs++;
			}
			if (numUniversalArcs > 0)
				System.out.println("Geometry: Layout " + cell + " has " + numUniversalArcs + " " + universalArc.describe() + " arcs");
			if (numUnroutedArcs > 0)
				System.out.println("Geometry: Layout " + cell + " has " + numUnroutedArcs + " " + unroutedArc.describe() + " arcs");
		}

	    public void addNodesAndArcs()
	    {
	    	for(Iterator<Nodable> it = cell.getNodables(); it.hasNext(); )
	    	{
	    		Nodable no = it.next();
		        if (!no.isCellInstance())
				{
					NodeInst ni = (NodeInst)no;
					// don't copy Cell-Centers
					if (Generic.isCellCenter(ni)) continue;
		            FixpTransform trans = ni.rotateOut();
		            addNodeInst(ni, trans);
				} else
				{
					// else just a cell
			        nodables.add(no);
				}
	    	}

	    	for (Iterator<ArcInst> it = cell.getArcs(); it.hasNext();)
			{
	    		ArcInst ai = it.next();
				addArcInst(ai);
	        }
	    }

		public void addNodeInst(NodeInst ni, FixpTransform trans)
		{
			PrimitiveNode prim = (PrimitiveNode)ni.getProto();
			Technology tech = prim.getTechnology();
			Poly [] polys = tech.getShapeOfNode(ni);
			for (int i=0; i<polys.length; i++)
				polys[i].transform(trans);
			addPolys(polys, ni);
		}

		public void addArcInst(ArcInst ai)
		{
			ArcProto ap = ai.getProto();
			Technology tech = ap.getTechnology();
			Poly [] polys = tech.getShapeOfArc(ai);
			addPolys(polys, ai);
		}

        /** add polys to cell geometry */
        protected void addPolys(Poly[] polys, Geometric geom)
        {
            for (int i=0; i<polys.length; i++)
            {
                List<Object> list = polyMap.get(polys[i].getLayer());
                if (list == null)
                {
                    list = new ArrayList<Object>();
                   	polyMap.put(polys[i].getLayer(), list);
                }
                if (geometry != null && geometry.includeGeometric())
                {
                	PolyWithGeom pg = new PolyWithGeom(polys[i], geom);
                    list.add(pg);
                } else
                {
                    list.add(polys[i]);
                }
            }
        }

        public void mergeCellGeom()
        {
    		GeometryHandler gMerge = GeometryHandler.createGeometryHandler(GHMode.ALGO_SWEEP, 1000);
//    		PolyMerge pMerge = new PolyMerge();
    		Set<Layer> layers = polyMap.keySet();
    		for (Layer layer : layers)
    		{
    			List<Object> polyList = polyMap.get(layer);
    			for (Object polyObj : polyList)
    			{
    				Poly poly = (Poly)polyObj;
//    				pMerge.addPolygon(layer, poly);
    				gMerge.add(layer, poly);
    			}
    		}
            gMerge.postProcess(true);
    		for (Layer layer : layers)
    		{
//    			List polys = pMerge.getMergedPoints(layer, true);
    			Collection<PolyBase> polysC = gMerge.getObjects(layer, false, false);
    			List<Object> polys = new ArrayList<Object>();
    			for(PolyBase pb : polysC) polys.add(pb);
    			polyMap.put(layer, polys);
    		}
        }
    }

    //------------------HierarchyEnumerator.Visitor Implementation----------------------

    public class Visitor extends HierarchyEnumerator.Visitor
    {
        /** Geometry object this Visitor is enumerating for */	private Geometry outGeom;
        /** Current cellGeom */                                 protected CellGeom cellGeom = null;
        /** Geometry stack when descending hierarchy */			private CellGeom [] outGeomStack;
        /** hierarchy max depth */                              private int maxHierDepth;
        /** current hierarchy depth */                          private int curHierDepth;

        public Visitor(Geometry outGeom, int maxHierDepth)
        {
			this.outGeom = outGeom;
			this.maxHierDepth = maxHierDepth;
			this.outGeomStack = new CellGeom[maxHierDepth+1];
			curHierDepth = 0;
        }

        public boolean enterCell(HierarchyEnumerator.CellInfo info)
        {
        	if (curHierDepth > maxHierDepth) return false;
			outGeomStack[curHierDepth] = cellGeom;
			Cell cell = info.getCell();
            if (cellGeoms.containsKey(cell)) return false;    // already processed this Cell
            cellGeom = new CellGeom(cell, outGeom);
            cellGeoms.put(info.getCell(), cellGeom);
            curHierDepth++;
            return true;
        }

        public void exitCell(HierarchyEnumerator.CellInfo info)
        {
            // add arcs to cellGeom
    		for (Iterator<ArcInst> it = info.getCell().getArcs(); it.hasNext();)
			{
        		ArcInst ai = it.next();
				addArcInst(ai);
            }

            boolean merge = outGeom.mergeGeom(maxHierDepth - curHierDepth);
			if (merge)
				cellGeom.mergeCellGeom();

            // write cell
            outGeom.writeCellGeom(cellGeom);

            curHierDepth--;
            cellGeom = outGeomStack[curHierDepth];
        }

        public boolean visitNodeInst(Nodable no, HierarchyEnumerator.CellInfo info)
        {
            if (!no.isCellInstance())
			{
    			NodeInst ni = (NodeInst)no;
    			// don't copy Cell-Centers
    			if (Generic.isCellCenter(ni)) return false;
                FixpTransform trans = ni.rotateOut();
                addNodeInst(ni, trans);
                return false;
    		}

			// else just a cell
            cellGeom.nodables.add(no);
            return true;
        }

		public void addNodeInst(NodeInst ni, FixpTransform trans)
		{
			cellGeom.addNodeInst(ni, trans);
		}

		public void addArcInst(ArcInst ai)
		{
			cellGeom.addArcInst(ai);
		}
    }

    //----------------------------Utility Methods--------------------------------------

    /** get the max hierarchical depth of the hierarchy */
    public static int getMaxHierDepth(Cell cell)
    {
        return hierCellsRecurse(cell, 0, 0);
    }

    /** Recursive method used to traverse down hierarchy */
    private static int hierCellsRecurse(Cell cell, int depth, int maxDepth)
    {
        if (depth > maxDepth) maxDepth = depth;
        EDatabase database = cell.getDatabase();
        for (Iterator<CellUsage> uit = cell.getUsagesIn(); uit.hasNext();)
        {
            CellUsage u = uit.next();
            Cell subCell = u.getProto(database);
            if (subCell.isIcon()) continue;
            maxDepth = hierCellsRecurse(subCell, depth+1, maxDepth);
        }
        return maxDepth;
    }

}
