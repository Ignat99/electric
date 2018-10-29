/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: STL.java
 * Input/output tool: STL output
 * Written by Gilda Garreton 
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
package com.sun.electric.tool.io.output;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.sun.electric.database.geometry.Poly;
import com.sun.electric.database.geometry.PolyBase.Point;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.prototype.NodeProto;
import com.sun.electric.database.topology.ArcInst;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.variable.VarContext;
import com.sun.electric.technology.Layer;
import com.sun.electric.technology.Technology;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.user.ui.LayerVisibility;
import com.sun.electric.util.math.FixpRectangle;
import com.sun.electric.util.math.FixpTransform;

public class STL extends Output {

	private STLPreferences localPrefs;
	
	public static class STLPreferences extends OutputPreferences
    {
        public Technology tech;
        public Map<Layer,Layer> visibleLayers = new HashMap<Layer,Layer>();

        public STLPreferences(boolean factory) {
            super(factory);
        }

        @Override
        public Output doOutput(Cell cell, VarContext context, String filePath)
        {
    		STL out = new STL(this);

    		// Technology has to be taken from the cell to analyze.
            tech = cell.getTechnology();
            // lv == null -> comes from regression so far
            LayerVisibility lv = (Job.isClientThread()) ? LayerVisibility.getLayerVisibility() : null;
            
            for (Iterator<Layer> it = tech.getLayers(); it.hasNext(); )
            {
            	Layer l = it.next();
            	if (lv == null || lv.isVisible(l))
            		visibleLayers.put(l, l);
            }
            
    		if (out.openTextOutputStream(filePath)) return out.finishWrite();

    		out.writeSTL(cell);

    		if (out.closeTextOutputStream()) return out.finishWrite();
    		System.out.println(filePath + " written");
            return out.finishWrite();
        }
    }
	
	/**
	 * Creates a new instance of the STL netlister.
	 */
	STL(STLPreferences dp) { localPrefs = dp; }
	
	private void writeSTL(Cell cell)
	{
		printWriter.print("solid electric_" + cell.getName() + "\n");
		
		// only for flat cell for now. Enummerator should be used in case of hierarchy
		for(Iterator<NodeInst> it = cell.getNodes(); it.hasNext(); )
		{
			NodeInst ni = it.next();
			NodeProto np = ni.getProto();
			if (ni.isCellInstance())
				continue; // skipping subscell for now

			if (NodeInst.isSpecialNode(ni)) continue; // no pins or other elements
			
			Technology tech = ni.getProto().getTechnology();
			FixpTransform bound = ni.rotateOut();
            Poly [] polyList = tech.getShapeOfNode(ni);
            String name = ni.getName();
            
            for (Poly poly : polyList)
            {
            	poly.transform(bound);
            	writePoly(name, poly);
            }
		}
		
		for (Iterator<ArcInst> it = cell.getArcs(); it.hasNext();)
		{
			ArcInst ai = it.next();
			Technology tech = ai.getProto().getTechnology();
            Poly [] polyList = tech.getShapeOfArc(ai);
            String name = ai.getName();
            for (Poly poly : polyList)
            	writePoly(name, poly);
			
		}
		printWriter.print("endsolid\n"); 
	}
	
	private void writePoly(String name, Poly poly)
	{
		Layer l = poly.getLayer();
		
		if (localPrefs.visibleLayers.get(l) == null) return; // invisible layer
		
		Point[] pts = poly.getPoints();
		
		if (pts.length != 4) // not a rectangular face -> triangulation would be needed
		{
			System.out.println("Warning: skipping non-rectangular face in '" + name + "'");
			return;
		}
		FixpRectangle rect = poly.getBounds2D();
		double minz = l.getDistance();
		double maxz = l.getDepth();
		
//		System.out.println("Layer " + l + " minz " + minz + " maxz " + maxz);
		
		// face xy bottom (minz)
		// First triangle
		printWriter.print("\tfacet normal 0.0 0.0 -1.0\n");
		printWriter.print("\t\touter loop\n");
		printWriter.print("\t\t\tvertex " + rect.getLambdaMinX() + " " + rect.getLambdaMinY() + " " + minz + "\n");
		printWriter.print("\t\t\tvertex " + rect.getLambdaMaxX() + " " + rect.getLambdaMaxY() + " " + minz + "\n");
		printWriter.print("\t\t\tvertex " + rect.getLambdaMaxX() + " " + rect.getLambdaMinY() + " " + minz + "\n");
		printWriter.print("\t\tendloop\n");
		printWriter.print("\tendfacet\n");
		// second triangle
		printWriter.print("\tfacet normal 0.0 0.0 -1.0\n");
		printWriter.print("\t\touter loop\n");
		printWriter.print("\t\t\tvertex " + rect.getLambdaMinX() + " " + rect.getLambdaMinY() + " " + minz + "\n");
		printWriter.print("\t\t\tvertex " + rect.getLambdaMinX() + " " + rect.getLambdaMaxY() + " " + minz + "\n");
		printWriter.print("\t\t\tvertex " + rect.getLambdaMaxX() + " " + rect.getLambdaMaxY() + " " + minz + "\n");
		printWriter.print("\t\tendloop\n");
		printWriter.print("\tendfacet\n");
		// face xy top (maxz)
		printWriter.print("\tfacet normal 0.0 0.0 1.0\n");
		printWriter.print("\t\touter loop\n");
		printWriter.print("\t\t\tvertex " + rect.getLambdaMinX() + " " + rect.getLambdaMinY() + " " + maxz + "\n");
		printWriter.print("\t\t\tvertex " + rect.getLambdaMaxX() + " " + rect.getLambdaMinY() + " " + maxz + "\n");
		printWriter.print("\t\t\tvertex " + rect.getLambdaMaxX() + " " + rect.getLambdaMaxY() + " " + maxz + "\n");
		printWriter.print("\t\tendloop\n");
		printWriter.print("\tendfacet\n");
		// second triangle
		printWriter.print("\tfacet normal 0.0 0.0 1.0\n");
		printWriter.print("\t\touter loop\n");
		printWriter.print("\t\t\tvertex " + rect.getLambdaMinX() + " " + rect.getLambdaMinY() + " " + maxz + "\n");
		printWriter.print("\t\t\tvertex " + rect.getLambdaMaxX() + " " + rect.getLambdaMaxY() + " " + maxz + "\n");
		printWriter.print("\t\t\tvertex " + rect.getLambdaMinX() + " " + rect.getLambdaMaxY() + " " + maxz + "\n");
		printWriter.print("\t\tendloop\n");
		printWriter.print("\tendfacet\n");
		
		// face xz bottom (miny)
		// first triangle
		printWriter.print("\tfacet normal 0.0 -1.0 0.0\n");
		printWriter.print("\t\touter loop\n");
		printWriter.print("\t\t\tvertex " + rect.getLambdaMinX() + " " + rect.getLambdaMinY() + " " + minz + "\n");
		printWriter.print("\t\t\tvertex " + rect.getLambdaMaxX() + " " + rect.getLambdaMinY() + " " + minz + "\n");
		printWriter.print("\t\t\tvertex " + rect.getLambdaMinX() + " " + rect.getLambdaMinY() + " " + maxz + "\n");
		printWriter.print("\t\tendloop\n");
		printWriter.print("\tendfacet\n");
		// second triangle
		printWriter.print("\tfacet normal 0.0 -1.0 0.0\n");
		printWriter.print("\t\touter loop\n");
		printWriter.print("\t\t\tvertex " + rect.getLambdaMinX() + " " + rect.getLambdaMinY() + " " + maxz + "\n");
		printWriter.print("\t\t\tvertex " + rect.getLambdaMaxX() + " " + rect.getLambdaMinY() + " " + minz + "\n");
		printWriter.print("\t\t\tvertex " + rect.getLambdaMaxX() + " " + rect.getLambdaMinY() + " " + maxz + "\n");
		printWriter.print("\t\tendloop\n");
		printWriter.print("\tendfacet\n");
		// face xz top (maxy)
		// first triangle
		printWriter.print("\tfacet normal 0.0 1.0 0.0\n");
		printWriter.print("\t\touter loop\n");
		printWriter.print("\t\t\tvertex " + rect.getLambdaMinX() + " " + rect.getLambdaMaxY() + " " + minz + "\n");
		printWriter.print("\t\t\tvertex " + rect.getLambdaMinX() + " " + rect.getLambdaMaxY() + " " + maxz + "\n");
		printWriter.print("\t\t\tvertex " + rect.getLambdaMaxX() + " " + rect.getLambdaMaxY() + " " + minz + "\n");
		printWriter.print("\t\tendloop\n");
		printWriter.print("\tendfacet\n");
		// second triangle
		printWriter.print("\tfacet normal 0.0 1.0 0.0\n");
		printWriter.print("\t\touter loop\n");
		printWriter.print("\t\t\tvertex " + rect.getLambdaMinX() + " " + rect.getLambdaMaxY() + " " + maxz + "\n");
		printWriter.print("\t\t\tvertex " + rect.getLambdaMaxX() + " " + rect.getLambdaMaxY() + " " + maxz + "\n");
		printWriter.print("\t\t\tvertex " + rect.getLambdaMaxX() + " " + rect.getLambdaMaxY() + " " + minz + "\n");
		printWriter.print("\t\tendloop\n");
		printWriter.print("\tendfacet\n");

		// face yz bottom (minx)
		// first triangle
		printWriter.print("\tfacet normal -1.0 0.0 0.0\n");
		printWriter.print("\t\touter loop\n");
		printWriter.print("\t\t\tvertex " + rect.getLambdaMinX() + " " + rect.getLambdaMinY() + " " + minz + "\n");
		printWriter.print("\t\t\tvertex " + rect.getLambdaMinX() + " " + rect.getLambdaMaxY() + " " + maxz + "\n");
		printWriter.print("\t\t\tvertex " + rect.getLambdaMinX() + " " + rect.getLambdaMaxY() + " " + minz + "\n");
		printWriter.print("\t\tendloop\n");
		printWriter.print("\tendfacet\n");
		// second triangle
		printWriter.print("\tfacet normal -1.0 0.0 0.0\n");
		printWriter.print("\t\touter loop\n");
		printWriter.print("\t\t\tvertex " + rect.getLambdaMinX() + " " + rect.getLambdaMinY() + " " + minz + "\n");
		printWriter.print("\t\t\tvertex " + rect.getLambdaMinX() + " " + rect.getLambdaMinY() + " " + maxz + "\n");
		printWriter.print("\t\t\tvertex " + rect.getLambdaMinX() + " " + rect.getLambdaMaxY() + " " + maxz + "\n");
		printWriter.print("\t\tendloop\n");
		printWriter.print("\tendfacet\n");
		// face yz bottom (maxx)
		// first triangle
		printWriter.print("\tfacet normal 1.0 0.0 0.0\n");
		printWriter.print("\t\touter loop\n");
		printWriter.print("\t\t\tvertex " + rect.getLambdaMaxX() + " " + rect.getLambdaMinY() + " " + minz + "\n");
		printWriter.print("\t\t\tvertex " + rect.getLambdaMaxX() + " " + rect.getLambdaMaxY() + " " + minz + "\n");
		printWriter.print("\t\t\tvertex " + rect.getLambdaMaxX() + " " + rect.getLambdaMaxY() + " " + maxz + "\n");
		printWriter.print("\t\tendloop\n");
		printWriter.print("\tendfacet\n");
		// second triangle
		printWriter.print("\tfacet normal 1.0 0.0 0.0\n");
		printWriter.print("\t\touter loop\n");
		printWriter.print("\t\t\tvertex " + rect.getLambdaMaxX() + " " + rect.getLambdaMinY() + " " + minz + "\n");
		printWriter.print("\t\t\tvertex " + rect.getLambdaMaxX() + " " + rect.getLambdaMaxY() + " " + maxz + "\n");
		printWriter.print("\t\t\tvertex " + rect.getLambdaMaxX() + " " + rect.getLambdaMinY() + " " + maxz + "\n");
		printWriter.print("\t\tendloop\n");
		printWriter.print("\tendfacet\n");
	}
}
