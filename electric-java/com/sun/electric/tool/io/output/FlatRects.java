/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: FlatRects.java
 * Input/output tool: Rectangle text output
 * Written by Steven M. Rubin.
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
package com.sun.electric.tool.io.output;

import com.sun.electric.database.geometry.Poly;
import com.sun.electric.database.geometry.PolyBase;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.HierarchyEnumerator;
import com.sun.electric.database.hierarchy.Nodable;
import com.sun.electric.database.text.TextUtils;
import com.sun.electric.database.topology.ArcInst;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.variable.VarContext;
import com.sun.electric.technology.PrimitiveNode;
import com.sun.electric.technology.Technology;
import com.sun.electric.tool.Job;
import com.sun.electric.util.math.FixpTransform;

import java.awt.geom.Rectangle2D;
import java.util.Iterator;

/**
 * This class writes cells as a flattened collection of rectangles.
 */
public class FlatRects extends Output
{
	public static class FlatRectPreferences extends OutputPreferences
    {
		String formatSpec;

		public FlatRectPreferences(boolean factory)
        {
            super(factory);
            formatSpec = Job.getUserInterface().askForInput("Specify format of rectangles:", "Rectangle Factors", "%l %x %y %w %h");
        }

        public Output doOutput(Cell cell, VarContext context, String filePath)
        {
    		FlatRects out = new FlatRects();
    		if (out.openTextOutputStream(filePath)) return out.finishWrite();
    		out.printWriter.println("; Cell " + cell.noLibDescribe() + " from Library " + cell.getLibrary().getName());
    		out.printWriter.println("; Generated automatically by the Electric VLSI Design System");
    		out.printWriter.println("; Rectangles are described as: " + formatSpec);
    		out.printWriter.println("; Where %l is layer, %x is X of center, %y is Y of center, %w is width, %h is height");

    		// gather all geometry
    		FlatRectVisitor visitor = out.makeRectanglesVisitor(this);
    		out.start();
    		HierarchyEnumerator.enumerateCell(cell, context, visitor);
    		out.done(cell);

    		if (out.closeTextOutputStream()) return out.finishWrite();
    		System.out.println(filePath + " written");
            return out.finishWrite();
        }
    }

	/** Creates a new instance of Rectangles */
	private FlatRects() {}

	protected void start() {}

	protected void done(Cell cell) {}

	/****************************** VISITOR SUBCLASS ******************************/

	private FlatRectVisitor makeRectanglesVisitor(FlatRectPreferences rp) { return new FlatRectVisitor(rp); }

	/**
	 * Class to override the Geometry visitor.
	 */
	private class FlatRectVisitor extends HierarchyEnumerator.Visitor
	{
		private FlatRectPreferences localPrefs;

		FlatRectVisitor(FlatRectPreferences rp) { localPrefs = rp; }

		public boolean visitNodeInst(Nodable no, HierarchyEnumerator.CellInfo info) { return true; }

		public boolean enterCell(HierarchyEnumerator.CellInfo info) { return true; }

		public void exitCell(HierarchyEnumerator.CellInfo info)
		{
			FixpTransform transToTop = info.getTransformToRoot();

			for(Iterator<NodeInst> it = info.getCell().getNodes(); it.hasNext();)
			{
				NodeInst ni = it.next();
				if (!ni.isCellInstance())
				{
					PrimitiveNode prim = (PrimitiveNode)ni.getProto();
					Technology tech = prim.getTechnology();
					Poly[] polys = tech.getShapeOfNode(ni);
					FixpTransform nodeTrans = ni.rotateOut(transToTop);
					for (int i=0; i<polys.length; i++)
					{
						Poly poly = polys[i];
						poly.transform(nodeTrans);
						emitPoly(poly);
					}
				}
			}

			for(Iterator<ArcInst> it = info.getCell().getArcs(); it.hasNext();)
			{
				ArcInst ai = it.next();
				Technology tech = ai.getProto().getTechnology();
				Poly[] polys = tech.getShapeOfArc(ai);
				for (int i=0; i<polys.length; i++)
				{
					Poly poly = polys[i];
					poly.transform(transToTop);
					emitPoly(poly);
				}
			}
		}

		/**
		 * Method to write the polygon "poly"
		 */
		private void emitPoly(PolyBase poly)
		{
			Poly.Type style = poly.getStyle();
			if (style == Poly.Type.FILLED)
			{
				Rectangle2D box = poly.getBox();
				if (box == null) return;
				if (box.getWidth() == 0) return;
				if (box.getHeight() == 0) return;
				String replaceString = localPrefs.formatSpec;
				replaceString = replaceString.replaceAll("%l", poly.getLayer().getName());
				replaceString = replaceString.replaceAll("%x", TextUtils.formatDistance(box.getCenterX()));
				replaceString = replaceString.replaceAll("%y", TextUtils.formatDistance(box.getCenterY()));
				replaceString = replaceString.replaceAll("%w", TextUtils.formatDistance(box.getWidth()));
				replaceString = replaceString.replaceAll("%h", TextUtils.formatDistance(box.getHeight()));
				printWriter.println(replaceString);
			}
		}
	}
}
