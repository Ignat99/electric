/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Maxwell.java
 * Input/output tool: Maxwell output
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

import com.sun.electric.database.geometry.EGraphics;
import com.sun.electric.database.geometry.Poly;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.HierarchyEnumerator;
import com.sun.electric.database.hierarchy.Nodable;
import com.sun.electric.database.network.Netlist;
import com.sun.electric.database.network.Network;
import com.sun.electric.database.text.Version;
import com.sun.electric.database.topology.ArcInst;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.topology.PortInst;
import com.sun.electric.database.variable.VarContext;
import com.sun.electric.technology.Layer;
import com.sun.electric.technology.TechPool;
import com.sun.electric.technology.Technology;
import com.sun.electric.util.TextUtils;
import com.sun.electric.util.math.FixpTransform;

import java.awt.Color;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Class to generate Maxwell netlists.
 */
public class Maxwell extends Output
{
	Map<Integer,List<Integer>> maxNetMap;
	Map<Integer,String> boxNames;
	int boxNumber;
	private MaxwellPreferences localPrefs;

	public static class MaxwellPreferences extends OutputPreferences
    {
        public Map<Layer,Color> layerColors = new HashMap<Layer,Color>();

        public MaxwellPreferences(boolean factory) {
            super(factory);
            for (Technology tech: TechPool.getThreadTechPool().values()) {
                Color[] transparentColors = factory ? tech.getFactoryTransparentLayerColors() : tech.getTransparentLayerColors();
                for (Iterator<Layer> it = tech.getLayers(); it.hasNext(); ) {
                    Layer layer = it.next();
                    EGraphics graphics = factory ? layer.getFactoryGraphics() : layer.getGraphics();
                    layerColors.put(layer, graphics.getColor(transparentColors));
                }
            }
        }

        public Output doOutput(Cell cell, VarContext context, String filePath)
        {
    		Maxwell out = new Maxwell(this);
    		if (out.openTextOutputStream(filePath)) return out.finishWrite();
    		out.initialize(cell);

    		// enumerate the hierarchy below here
    		Visitor wcVisitor = new Visitor(out);
    		HierarchyEnumerator.enumerateCell(cell, context, wcVisitor);

    		out.terminate();
    		if (out.closeTextOutputStream()) return out.finishWrite();
    		System.out.println(filePath + " written");
            return out.finishWrite();
        }
    }

	/**
	 * Creates a new instance of Maxwell
	 */
	Maxwell(MaxwellPreferences mp) { localPrefs = mp; }

	private void initialize(Cell cell)
	{
		maxNetMap = new HashMap<Integer,List<Integer>>();
		boxNames = new HashMap<Integer,String>();
		boxNumber = 1;

		printWriter.print("# Maxwell netlist for cell " + cell.noLibDescribe() + " from library " + cell.getLibrary().getName() + "\n");
		if (localPrefs.includeDateAndVersionInOutput)
		{
			printWriter.print("# CELL CREATED ON " + TextUtils.formatDate(cell.getCreationDate()) + "\n");
			printWriter.print("# LAST REVISED ON " + TextUtils.formatDate(cell.getRevisionDate()) + "\n");
			printWriter.print("# Generated automatically by the Electric VLSI Design System, version " + Version.getVersion() + "\n");
			printWriter.print("# WRITTEN ON " + TextUtils.formatDate(new Date()) + "\n");
		} else
		{
			printWriter.print("# Generated automatically by the Electric VLSI Design System\n");
		}
		printWriter.print("\n");
		emitCopyright("# ", "");
	}

	private void terminate()
	{
		for(Integer index : maxNetMap.keySet())
		{
			List<Integer> boxList = maxNetMap.get(index);
			if (boxList.size() <= 1) continue;
			printWriter.print("Unite {");
			boolean first = true;
			for(Integer boxNum : boxList)
			{
				if (first) first = false; else
					printWriter.print(" ");
				String boxName = boxNames.get(boxNum);
				printWriter.print("\"" + boxName + "\"");
			}
			printWriter.print("}\n");
		}
	}

	private void writePolygon(Poly poly, int globalNetNum, Network net)
	{
		Layer layer = poly.getLayer();
		if (layer.getTechnology() != Technology.getCurrent()) return;
		Rectangle2D box = poly.getBox();
		if (box == null) return;
		Color color = localPrefs.layerColors.get(layer);
		int red = color.getRed();
		int green = color.getGreen();
		int blue = color.getBlue();
		printWriter.print("NewObjColor " + red + " " + green + " " + blue + "\n");

		// find this network object
		Integer index = new Integer(globalNetNum);
		List<Integer> boxList = maxNetMap.get(index);
		if (boxList == null)
		{
			boxList = new ArrayList<Integer>();
			maxNetMap.put(index, boxList);
		}

		// convert to microns
		double scale = layer.getTechnology().getScale();
		double lX = box.getMinX() * scale / 1000;
		double lY = box.getMinY() * scale / 1000;
		double wid = box.getWidth() * scale / 1000;
		double hei = box.getHeight() * scale / 1000;
		String netName = net.describe(false) + "-" + boxNumber;
		printWriter.print("Box pos3 " + lX + " " + lY + " " + layer.getName() + "-Bot   " +
			wid + " " + hei + " " + layer.getName() + "-Hei \"" + netName + "\"\n");
		Integer boxNum = new Integer(boxNumber);
		boxList.add(boxNum);
		boxNames.put(boxNum, netName);
		boxNumber++;
	}

    private static class Visitor extends HierarchyEnumerator.Visitor
    {
		Maxwell generator;

        public Visitor(Maxwell generator)
        {
			this.generator = generator;
        }

        public boolean enterCell(HierarchyEnumerator.CellInfo info)
        {
			// emit all node polygons
			Netlist netList = info.getNetlist();
			for(Iterator<NodeInst> it = info.getCell().getNodes(); it.hasNext(); )
			{
				NodeInst ni = it.next();
				if (ni.isCellInstance()) continue;
				FixpTransform transRot = ni.rotateOut();
				Technology tech = ni.getProto().getTechnology();
				Poly [] polyList = tech.getShapeOfNode(ni, true, false, null);
				int tot = polyList.length;
				for(int i=0; i<tot; i++)
				{
					Poly poly = polyList[i];
					if (poly.getPort() == null) continue;
					poly.transform(transRot);
					poly.transform(info.getTransformToRoot());

					PortInst pi = ni.findPortInstFromEquivalentProto(poly.getPort());
					Network net = netList.getNetwork(pi);
					if (net == null) continue;
					int globalNetNum = info.getNetID(net);
					generator.writePolygon(poly, globalNetNum, net);
				}
			}

			// emit all arc polygons
			for(Iterator<ArcInst> it = info.getCell().getArcs(); it.hasNext(); )
			{
				ArcInst ai = it.next();
				Technology tech = ai.getProto().getTechnology();
				Poly [] polyList = tech.getShapeOfArc(ai);
				int tot = polyList.length;
				for(int i=0; i<tot; i++)
				{
					Poly poly = polyList[i];
					poly.transform(info.getTransformToRoot());
					Network net = netList.getNetwork(ai, 0);
					int globalNetNum = info.getNetID(net);
					generator.writePolygon(poly, globalNetNum, net);
				}
			}
            return true;
        }

        public void exitCell(HierarchyEnumerator.CellInfo info)
        {
		}

        public boolean visitNodeInst(Nodable no, HierarchyEnumerator.CellInfo info)
        {
            return true;
        }
    }

}
