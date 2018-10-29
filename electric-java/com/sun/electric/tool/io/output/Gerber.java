/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Gerber.java
 * Input/output tool: Gerber output
 * Written by Steven M. Rubin.
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
package com.sun.electric.tool.io.output;

import com.sun.electric.database.geometry.ERectangle;
import com.sun.electric.database.geometry.Poly;
import com.sun.electric.database.geometry.PolyBase;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.HierarchyEnumerator;
import com.sun.electric.database.hierarchy.Nodable;
import com.sun.electric.database.text.Version;
import com.sun.electric.database.topology.ArcInst;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.variable.VarContext;
import com.sun.electric.technology.Layer;
import com.sun.electric.technology.PrimitiveNode;
import com.sun.electric.technology.Technology;
import com.sun.electric.util.TextUtils;
import com.sun.electric.util.math.FixpTransform;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * This class writes files in Gerber format.
 */
public class Gerber extends Output
{
	private static final int LEFTDIGITS = 2;
	private static final int RIGHTDIGITS = 5;

	/** geometries on each layer */	private Map<Layer,List<PolyBase>> cellGeoms;
	private double scaleFactor;
	private double offX, offY;

	private GerberPreferences localPrefs;

	public static class GerberPreferences extends OutputPreferences
    {
        public GerberPreferences(boolean factory)
        {
            super(factory);
        }

        public Output doOutput(Cell cell, VarContext context, String filePath)
        {
			Gerber out = new Gerber(this);
			out.scaleFactor = cell.getTechnology().getScale() / 1000000.0;
			ERectangle bounds = cell.getBounds();
			out.offX = -bounds.getMinX();
			out.offY = -bounds.getMinY();
    		if (out.openTextOutputStream(filePath)) return out.finishWrite();
    		GerberVisitor visitor = out.makeGerberVisitor();

    		// gather all geometry
    		out.start();
    		HierarchyEnumerator.enumerateCell(cell, context, visitor);

    		// write the geometry
    		out.done(cell);

    		if (out.closeTextOutputStream()) return out.finishWrite();
    		System.out.println(filePath + " written");
            return out.finishWrite();
        }
    }

	/** Creates a new instance of Gerber */
	private Gerber(GerberPreferences hp) { localPrefs = hp; }

	protected void start()
	{
		cellGeoms = new TreeMap<Layer,List<PolyBase>>();
	}

	protected void done(Cell cell)
	{
		writeLine("G04 Gerber for cell " + cell.noLibDescribe() + " from library " + cell.getLibrary().getName() + " *");
		if (localPrefs.includeDateAndVersionInOutput)
		{
			writeLine("G04 Cell created on " + TextUtils.formatDate(cell.getCreationDate()) + " *");
			writeLine("G04 Last revised on " + TextUtils.formatDate(cell.getRevisionDate()) + " *");
			writeLine("G04 Generated automatically by the Electric VLSI Design System, version " + Version.getVersion() + " *");
			writeLine("G04 File written on " + TextUtils.formatDate(new Date()) + " *");
		} else
		{
			writeLine("G04 Generated automatically by the Electric VLSI Design System *");
		}
		emitCopyright("G04 ", " *");

		writeLine("%ASAXBY*%");			// axis select
		String numberFormat = "" + LEFTDIGITS + RIGHTDIGITS;
		writeLine("%FSLAX" + numberFormat + "Y" + numberFormat + "*%"); // leading zeroes omitted?, absolute coordinates
		writeLine("%MOMM*%");			// millimeter units
		writeLine("%SFA1.0B1.0*%");		// scale factor
		writeLine("%ADD10C,.025*%");	// Aperture 10 is 0.025 diameter, round
		writeLine("G54D10*");			// select Aperture 10

		// write all geometry collected
		for(Layer layer : cellGeoms.keySet())
		{
			List<PolyBase> geoms = cellGeoms.get(layer);
			writeLine("%LN" + layer.getName() + "*%");
			writeLine("%LPD*%");
			for(PolyBase poly : geoms)
				emitPoly(poly);
		}
		writeLine("M02*");
	}

	/****************************** VISITOR SUBCLASS ******************************/

	private GerberVisitor makeGerberVisitor() { return new GerberVisitor(this); }

	/**
	 * Class to override the Geometry visitor.
	 */
	private class GerberVisitor extends HierarchyEnumerator.Visitor
	{
		private Gerber outGeom;

		GerberVisitor(Gerber outGeom) { this.outGeom = outGeom; }

		public boolean visitNodeInst(Nodable no, HierarchyEnumerator.CellInfo info) { return true; }

		public boolean enterCell(HierarchyEnumerator.CellInfo info) { return true; }

		public void exitCell(HierarchyEnumerator.CellInfo info)
		{
			FixpTransform trans = info.getTransformToRoot();

			// add nodes to cellGeom
			for(Iterator<NodeInst> it = info.getCell().getNodes(); it.hasNext();)
			{
				NodeInst ni = it.next();
				if (!ni.isCellInstance())
				{
					PrimitiveNode prim = (PrimitiveNode)ni.getProto();
					Technology tech = prim.getTechnology();
					Poly[] polys = tech.getShapeOfNode(ni);
					FixpTransform nodeTrans = ni.rotateOut(trans);
					for (int i=0; i<polys.length; i++)
					{
						Poly poly = polys[i];
						poly.transform(nodeTrans);
						List<PolyBase> layerList = getListForLayer(poly.getLayer());
						layerList.add(poly);
					}
				}
			}

			// add arcs to cellGeom
			for(Iterator<ArcInst> it = info.getCell().getArcs(); it.hasNext();)
			{
				ArcInst ai = it.next();
				Technology tech = ai.getProto().getTechnology();
				Poly[] polys = tech.getShapeOfArc(ai);
				for (int i=0; i<polys.length; i++)
				{
					Poly poly = polys[i];
					poly.transform(trans);
					List<PolyBase> layerList = getListForLayer(poly.getLayer());
					layerList.add(poly);
				}
			}
		}

		private List<PolyBase> getListForLayer(Layer layer)
		{
			List<PolyBase> layerList = outGeom.cellGeoms.get(layer);
			if (layerList == null)
				outGeom.cellGeoms.put(layer, layerList = new ArrayList<PolyBase>());
			return layerList;
		}
	}

	/**
	 * Method to plot the polygon "poly"
	 */
	private void emitPoly(PolyBase poly)
	{
		Poly.Type style = poly.getStyle();
		Point2D [] points = poly.getPoints();
		if (style == Poly.Type.FILLED)
		{
			Rectangle2D box = poly.getBox();
			if (box != null)
			{
				if (box.getWidth() == 0)
				{
					if (box.getHeight() != 0) emitLine(box.getMinX(), box.getMinY(), box.getMinX(), box.getMaxY());
					return;
				}
				if (box.getHeight() == 0)
				{
					emitLine(box.getMinX(), box.getMinY(), box.getMaxX(), box.getMinY());
					return;
				}
			}
			if (points.length <= 1) return;
			if (points.length == 2)
			{
				emitLine(points[0].getX(), points[0].getY(), points[1].getX(), points[1].getY());
				return;
			}
			emitFilledPolygon(points);
			return;
		}

		if (style == Poly.Type.CLOSED || style == Poly.Type.OPENED ||
			style == Poly.Type.OPENEDT1 || style == Poly.Type.OPENEDT2 || style == Poly.Type.OPENEDT3)
		{
			Rectangle2D box = poly.getBox();
			if (box != null)
			{
				if (box.getHeight() == 0)
				{
					if (box.getWidth() == 0)
					{
						movePen(box.getMinX(), box.getMinY());
						drawPen(box.getMinX(), box.getMinY());
					} else
					{
						movePen(box.getMinX(), box.getMinY());
						drawPen(box.getMaxX(), box.getMinY());
					}
					return;
				} else if (box.getWidth() == 0)
				{
					movePen(box.getMinX(), box.getMinY());
					drawPen(box.getMinX(), box.getMaxY());
					return;
				}
				movePen(box.getMinX(), box.getMinY());
				drawPen(box.getMinX(), box.getMaxY());
				drawPen(box.getMaxX(), box.getMaxY());
				drawPen(box.getMaxX(), box.getMinY());
				if (style == Poly.Type.CLOSED || points.length == 5)
					drawPen(box.getMinX(), box.getMinY());
				return;
			}
			movePen(points[0].getX(), points[0].getY());
			for (int k = 1; k < points.length; k++)
				drawPen(points[k].getX(), points[k].getY());
			if (style == Poly.Type.CLOSED)
				drawPen(points[0].getX(), points[0].getY());
			return;
		}

		if (style == Poly.Type.VECTORS)
		{
			for(int k=0; k<points.length; k += 2)
				emitLine(points[k].getX(), points[k].getY(), points[k+1].getX(), points[k+1].getY());
			return;
		}

		if (style == Poly.Type.CROSS || style == Poly.Type.BIGCROSS)
		{
			double x = poly.getCenterX();
			double y = poly.getCenterY();
			emitLine(x-5, y, x+5, y);
			emitLine(x, y+5, x, y-5);
			return;
		}

		if (style == Poly.Type.CROSSED)
		{
			Rectangle2D box = poly.getBounds2D();
			emitLine(box.getMinX(), box.getMinY(), box.getMinX(), box.getMaxY());
			emitLine(box.getMinX(), box.getMaxY(), box.getMaxX(), box.getMaxY());
			emitLine(box.getMaxX(), box.getMaxY(), box.getMaxX(), box.getMinY());
			emitLine(box.getMaxX(), box.getMinY(), box.getMinX(), box.getMinY());
			emitLine(box.getMaxX(), box.getMaxY(), box.getMinX(), box.getMinY());
			emitLine(box.getMaxX(), box.getMinY(), box.getMinX(), box.getMaxY());
			return;
		}
	}

	void emitLine(double x1, double y1, double x2, double y2)
	{
		movePen(x1, y1);
		drawPen(x2, y2);
	}

	private void emitFilledPolygon(Point2D [] points)
	{
		if (points.length <= 1) return;

		writeLine("G36*");
		double firstX = points[0].getX();		// save the end point
		double firstY = points[0].getY();
		movePen(firstX, firstY);	// move to the start
		for(int i=1; i<points.length; i++) drawPen(points[i].getX(), points[i].getY());
		int last = points.length - 1;
		if (points[last].getX() != firstX || points[last].getY() != firstY)
			drawPen(firstX, firstY);	// close the polygon
		writeLine("G37*");
	}

	/**
	 * Method to move the pen to a new position.
	 */
	private void movePen(double x, double y)
	{
		writeLine("X" + makeXCoord(x) + "Y" + makeYCoord(y) + "D02*");
	}

	/**
	 * Method to draw from current point to the next.
	 */
	private void drawPen(double x, double y)
	{
		writeLine("X" + makeXCoord(x) + "Y" + makeYCoord(y) + "D01*");
	}

	private String makeXCoord(double v) { return makeCoord(v+offX); }

	private String makeYCoord(double v) { return makeCoord(v+offY); }

	private String makeCoord(double v)
	{
		double trueV = v * scaleFactor;
		String vStr = TextUtils.formatDouble(trueV, RIGHTDIGITS);
		int dotPos = vStr.indexOf('.');
		String leftStr, rightStr;
		if (dotPos < 0)
		{
			leftStr = vStr;
			rightStr = "";
		} else
		{
			leftStr = vStr.substring(0, dotPos);
			rightStr = vStr.substring(dotPos+1);
		}
		while (leftStr.length() < LEFTDIGITS) leftStr = "0" + leftStr;
		if (leftStr.length() > LEFTDIGITS)
		{
			System.out.println("WARNING: Cannot represent the value " + vStr +
				" with just " + LEFTDIGITS + " digits to the left of the decimal point");
			leftStr = leftStr.substring(leftStr.length()-LEFTDIGITS);
		}
		while (rightStr.length() < RIGHTDIGITS) rightStr += "0";
		if (rightStr.length() > RIGHTDIGITS) rightStr = rightStr.substring(0, RIGHTDIGITS);
		return leftStr + rightStr;
	}

	private void writeLine(String line)
	{
		printWriter.print(line + "\n");
	}
}
