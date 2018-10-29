/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: HPGL.java
 * Input/output tool: HPGL output
 * Written by Steven M. Rubin.
 *
 * Copyright (c) 2004, Static Free Software. All rights reserved.
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

import com.sun.electric.database.EditingPreferences;
import com.sun.electric.database.geometry.EGraphics;
import com.sun.electric.database.geometry.Poly;
import com.sun.electric.database.geometry.PolyBase;
import com.sun.electric.database.geometry.PolyMerge;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.Export;
import com.sun.electric.database.hierarchy.HierarchyEnumerator;
import com.sun.electric.database.hierarchy.Nodable;
import com.sun.electric.database.topology.ArcInst;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.topology.NodeInst.ExpansionState;
import com.sun.electric.database.variable.EditWindow0;
import com.sun.electric.database.variable.EditWindow_;
import com.sun.electric.database.variable.TextDescriptor;
import com.sun.electric.database.variable.UserInterface;
import com.sun.electric.database.variable.VarContext;
import com.sun.electric.technology.ArcProto;
import com.sun.electric.technology.Layer;
import com.sun.electric.technology.PrimitiveNode;
import com.sun.electric.technology.TechPool;
import com.sun.electric.technology.Technology;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.user.GraphicsPreferences;
import com.sun.electric.util.TextUtils;
import com.sun.electric.util.math.FixpTransform;
import com.sun.electric.util.math.GenMath;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * This class writes files in HPGL/2 format.
 */
public class HPGL extends Output
{
	/** Scale to ensure that everything is integer */ private static final double SCALE = 100;

	/** conversion from Layers to pen numbers */	private HashMap<Layer,List<PolyBase>> cellGeoms;
	/** conversion from Colors to pen numbers */	private LinkedHashMap<Color,Integer>  penNumbers = new LinkedHashMap<Color,Integer>();
	/** the current line type */					private int         currentLineType;
	/** the current pen number */					private int         currentPen;
	/** if fill info written for the current pen */	private boolean     fillEmitted;

	private static class PenColor
	{
		/** line type (0=solid, 1=dotted, 2-6=dashed) */			private int lineType;
		/** fill type (0=none, 1=solid, 3=lines 4=cross-hatched) */	private int fillType;
		/** fill distance between lines (fillType 3 or 4 only) */	private int fillDist;
		/** fill angle of lines (fillType 3 or 4 only) */			private int fillAngle;
	};

	private PenColor [] penColorTable;
    private final EditingPreferences ep;
	private HPGLPreferences localPrefs;

	public static class HPGLPreferences extends OutputPreferences
    {
		boolean textVisibilityOnExport;
		int exportDisplayLevel;
        Map<Layer,Color> layerColors = new HashMap<Layer,Color>();
        EditWindow0.EditWindowSmall wnd;
        Rectangle2D printBounds;
        GraphicsPreferences gp;
        ExpansionState expansionState;

        public HPGLPreferences(boolean factory, Cell cell)
        {
            super(factory);
            gp = new GraphicsPreferences(factory);
        	textVisibilityOnExport = gp.isTextVisibilityOn(TextDescriptor.TextType.EXPORT);
    		exportDisplayLevel = gp.exportDisplayLevel;

            for (Technology tech: TechPool.getThreadTechPool().values()) {
                Color[] transparentColors = factory ? tech.getFactoryTransparentLayerColors() : tech.getTransparentLayerColors();
                for (Iterator<Layer> it = tech.getLayers(); it.hasNext(); ) {
                    Layer layer = it.next();
                    EGraphics graphics = factory ? layer.getFactoryGraphics() : layer.getGraphics();
                    layerColors.put(layer, graphics.getColor(transparentColors));
                }
            }
            if (factory) expansionState = new ExpansionState(null, ExpansionState.JUSTTHISCELL); else
            {
    			expansionState = new ExpansionState(cell, ExpansionState.JUSTTHISHIERARCHY);
                fillPrefs(cell);
            }
        }

        private void fillPrefs(Cell cell)
        {
    		// determine the window to use for text scaling
			UserInterface ui = Job.getUserInterface();
			EditWindow_ localWnd = ui.getCurrentEditWindow_();
			wnd = new EditWindow0.EditWindowSmall(localWnd);
			printBounds = getAreaToPrint(cell, false, localWnd);
        }

        @Override
        public Output doOutput(Cell cell, VarContext context, String filePath) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Output doOutput(Cell cell, VarContext context, String filePath, EditingPreferences ep)
        {
			if (printBounds == null) return null;
    		HPGL out = new HPGL(ep, this);
    		if (out.openTextOutputStream(filePath)) return out.finishWrite();
    		HPGLVisitor visitor = out.makeHPGLVisitor();

    		// gather all geometry
    		out.start();
    		HierarchyEnumerator.enumerateCell(cell, context, visitor);

    		// write the geometry
    		out.done();

    		if (out.closeTextOutputStream()) return out.finishWrite();
    		System.out.println(filePath + " written");
            return out.finishWrite();
        }
    }

	/** Creates a new instance of HPGL */
	private HPGL(EditingPreferences ep, HPGLPreferences hp) {
        this.ep = ep;
        localPrefs = hp;
    }

	protected void start()
	{
		cellGeoms = new HashMap<Layer,List<PolyBase>>();
		currentLineType = -1;
		currentPen = -1;
		fillEmitted = false;

		// initialize pen information
		initPenData();
	}

	protected void done()
	{
		// HPGL/2 setup and defaults
		writeLine("\033%0BBPIN");
		writeLine("LA1,4,2,4QLMC0");

		// setup pens and create the mapping between Layers and HPGL pen numbers
		for(Map.Entry<Layer,List<PolyBase>> e: cellGeoms.entrySet())
		{
			Layer layer = e.getKey();
			List<PolyBase> geoms = e.getValue();
            Color col;
			if (layer == null) col = Color.BLACK; else
			{
				col = localPrefs.layerColors.get(layer);
				if (col == null) continue;
			}
            getPenNumber(col);

            for (PolyBase poly: geoms) {
                if (poly instanceof Poly) {
                  EGraphics graphicsOverride = ((Poly)poly).getGraphicsOverride();
                  if (graphicsOverride != null)
                      getPenNumber(graphicsOverride.getColor());
                }
            }
		}
		writeLine("NP" + penNumbers.size());
        for (Map.Entry<Color,Integer> e: penNumbers.entrySet()) {
            Color col = e.getKey();
            int index = e.getValue().intValue();
			int r = col.getRed();
			int g = col.getGreen();
			int b = col.getBlue();
			writeLine("PC" + index + "," + r + "," + g + "," + b);
        }

		// set default location of "P1" and "P2" points on the plotter
		writeLine("IP;");
		writeLine("SC" + makeCoord(localPrefs.printBounds.getMinX()) + ",1," + makeCoord(localPrefs.printBounds.getMinY()) + ",1,2;");

		// write all geometry collected
		for(Map.Entry<Layer,List<PolyBase>> e: cellGeoms.entrySet())
		{
			List<PolyBase> geoms = e.getValue();
			for (PolyBase poly : geoms)
				emitPoly(poly);
		}

		// HPGL/2 termination
		writeLine("PUSP0PG;");
	}

	/****************************** VISITOR SUBCLASS ******************************/

	private HPGLVisitor makeHPGLVisitor()
	{
		HPGLVisitor visitor = new HPGLVisitor(this);
		return visitor;
	}

	/**
	 * Class to override the Geometry visitor and add bloating to all polygons.
	 * Currently, no bloating is being done.
	 */
	private class HPGLVisitor extends HierarchyEnumerator.Visitor
	{
		private HPGL outGeom;

		HPGLVisitor(HPGL outGeom) { this.outGeom = outGeom; }

		/**
		 * Traverses the visible hierarchy.
		 */
		public boolean visitNodeInst(Nodable no, HierarchyEnumerator.CellInfo info)
		{
			if (no.isCellInstance())
			{
				NodeInst ni = no.getNodeInst();
				if (!localPrefs.expansionState.isExpanded(ni)) return false;
			}
			return true;
		}

		public boolean enterCell(HierarchyEnumerator.CellInfo info)
		{
			return true;
		}

		public void exitCell(HierarchyEnumerator.CellInfo info)
		{
			FixpTransform trans = info.getTransformToRoot();

			// prepare to merge geometry in this cell
			PolyMerge merge = new PolyMerge();

			// add nodes to cellGeom
			Cell cell = info.getCell();
			for(Iterator<NodeInst> it = cell.getNodes(); it.hasNext();)
			{
				NodeInst ni = it.next();
				FixpTransform nodeTrans = ni.rotateOut(trans);
				if (!ni.isCellInstance())
				{
					PrimitiveNode prim = (PrimitiveNode)ni.getProto();
					Technology tech = prim.getTechnology();
					Poly[] polys = tech.getShapeOfNode(ni);
					addPolys(polys, nodeTrans, merge);
                    Poly[] textPolys = ni.getDisplayableVariables(localPrefs.wnd, localPrefs.gp.isShowTempNames());
					addPolys(textPolys, nodeTrans, merge);
				} else
				{
					if (!localPrefs.expansionState.isExpanded(ni))
					{
						Cell subCell = (Cell)ni.getProto();
						FixpTransform subTrans = ni.translateOut(nodeTrans);
						Rectangle2D bounds = subCell.getBounds();
						Poly poly = new Poly(bounds.getCenterX(), bounds.getCenterY(), ni.getXSize(), ni.getYSize());
						poly.transform(subTrans);
						poly.setStyle(Poly.Type.CLOSED);
						List<PolyBase> layerList = getListForLayer(null);
						layerList.add(poly);

						poly = new Poly(bounds.getCenterX(), bounds.getCenterY(), ni.getXSize(), ni.getYSize());
						poly.transform(subTrans);
						poly.setStyle(Poly.Type.TEXTBOX);
						TextDescriptor td = ep.getInstanceTextDescriptor().withAbsSize(24);
						poly.setTextDescriptor(td);
						poly.setString(ni.getProto().describe(false));
						layerList.add(poly);
					}
				}

				// draw any exports from the node
				if (info.isRootCell() && localPrefs.textVisibilityOnExport)
				{
					for(Iterator<Export> eIt = ni.getExports(); eIt.hasNext(); )
					{
						Export e = eIt.next();
						Poly poly = e.getNamePoly();
						List<PolyBase> layerList = getListForLayer(null);
						if (localPrefs.exportDisplayLevel == 2)
						{
							// draw port as a cross
							poly.setStyle(Poly.Type.CROSS);
							layerList.add(poly);
						} else
						{
							// draw port as text
							if (localPrefs.exportDisplayLevel == 1)
							{
								// use shorter port name
								String portName = e.getShortName();
								poly.setString(portName);
							}
							layerList.add(poly);
						}
					}
				}
			}

			// add arcs to cellGeom
			for(Iterator<ArcInst> it = cell.getArcs(); it.hasNext();)
			{
				ArcInst ai = it.next();
				ArcProto ap = ai.getProto();
				Technology tech = ap.getTechnology();
				addPolys(tech.getShapeOfArc(ai), trans, merge);
				addPolys(ai.getDisplayableVariables(localPrefs.wnd, localPrefs.gp.isShowTempNames()), trans, merge);
			}

			// extract merged data and add it to overall geometry
            for (Layer layer : merge.getKeySet())
			{
				List<PolyBase> layerList = getListForLayer(layer);
				List<PolyBase> geom = merge.getMergedPoints(layer, true);
				for(PolyBase poly : geom)
					layerList.add(poly);
			}
		}

		/** add polys to cell geometry */
		private void addPolys(Poly[] polys, FixpTransform trans, PolyMerge merge)
		{
			for (int i=0; i<polys.length; i++)
			{
				Poly poly = polys[i];
				poly.transform(trans);
				Layer layer = poly.getLayer();
				if (layer == null || poly.getStyle() != Poly.Type.FILLED)
				{
					List<PolyBase> layerList = getListForLayer(layer);
					layerList.add(poly);
					continue;
				}

				merge.addPolygon(layer, poly);
			}
		}

		private List<PolyBase> getListForLayer(Layer layer)
		{
			List<PolyBase> layerList = outGeom.cellGeoms.get(layer);
			if (layerList == null)
			{
				layerList = new ArrayList<PolyBase>();
				outGeom.cellGeoms.put(layer, layerList);
			}
			return layerList;
		}
	}

	/**
	 * Method to plot the polygon "poly"
	 */
	private void emitPoly(PolyBase poly)
	{
		// ignore null layers
		Layer layer = poly.getLayer();
        Color col = localPrefs.layerColors.get(layer);
        if (poly instanceof Poly) {
            EGraphics graphicsOverride = ((Poly)poly).getGraphicsOverride();
            if (graphicsOverride != null)
                col = graphicsOverride.getColor();
        }
		Poly.Type style = poly.getStyle();
		Point2D [] points = poly.getPoints();
		if (style == Poly.Type.FILLED)
		{
			Rectangle2D box = poly.getBox();
			if (box != null)
			{
				if (box.getWidth() == 0)
				{
					if (box.getHeight() != 0) emitLine(box.getMinX(), box.getMinY(), box.getMinX(), box.getMaxY(), col);
					return;
				}
				if (box.getHeight() == 0)
				{
					emitLine(box.getMinX(), box.getMinY(), box.getMaxX(), box.getMinY(), col);
					return;
				}
			}
			if (points.length <= 1) return;
			if (points.length == 2)
			{
				emitLine(points[0].getX(), points[0].getY(), points[1].getX(), points[1].getY(), col);
				return;
			}
			emitFilledPolygon(points, col);
			return;
		}

		if (style == Poly.Type.CLOSED || style == Poly.Type.OPENED ||
			style == Poly.Type.OPENEDT1 || style == Poly.Type.OPENEDT2 || style == Poly.Type.OPENEDT3)
		{
			Rectangle2D box = poly.getBox();
			if (box != null)
			{
				emitLine(box.getMinX(), box.getMinY(), box.getMinX(), box.getMaxY(), col);
				emitLine(box.getMinX(), box.getMaxY(), box.getMaxX(), box.getMaxY(), col);
				emitLine(box.getMaxX(), box.getMaxY(), box.getMaxX(), box.getMinY(), col);
				if (style == Poly.Type.CLOSED || points.length == 5)
					emitLine(box.getMaxX(), box.getMinY(), box.getMinX(), box.getMinY(), col);
				return;
			}
			for (int k = 1; k < points.length; k++)
				emitLine(points[k-1].getX(), points[k-1].getY(), points[k].getX(), points[k].getY(), col);
			if (style == Poly.Type.CLOSED)
			{
				int k = points.length - 1;
				emitLine(points[k].getX(), points[k].getY(), points[0].getX(), points[0].getY(), col);
			}
			return;
		}

		if (style == Poly.Type.VECTORS)
		{
			for(int k=0; k<points.length; k += 2)
				emitLine(points[k].getX(), points[k].getY(), points[k+1].getX(), points[k+1].getY(), col);
			return;
		}

		if (style == Poly.Type.CROSS || style == Poly.Type.BIGCROSS)
		{
			double x = poly.getCenterX();
			double y = poly.getCenterY();
			emitLine(x-5, y, x+5, y, col);
			emitLine(x, y+5, x, y-5, col);
			return;
		}

		if (style == Poly.Type.CROSSED)
		{
			Rectangle2D box = poly.getBounds2D();
			emitLine(box.getMinX(), box.getMinY(), box.getMinX(), box.getMaxY(), col);
			emitLine(box.getMinX(), box.getMaxY(), box.getMaxX(), box.getMaxY(), col);
			emitLine(box.getMaxX(), box.getMaxY(), box.getMaxX(), box.getMinY(), col);
			emitLine(box.getMaxX(), box.getMinY(), box.getMinX(), box.getMinY(), col);
			emitLine(box.getMaxX(), box.getMaxY(), box.getMinX(), box.getMinY(), col);
			emitLine(box.getMaxX(), box.getMinY(), box.getMinX(), box.getMaxY(), col);
			return;
		}

		if (style == Poly.Type.DISC)
		{
			// filled disc: plot it and its outline
			emitDisc(points[0], points[1], col);
			style = Poly.Type.CIRCLE;
		}

		if (style == Poly.Type.CIRCLE || style == Poly.Type.THICKCIRCLE)
		{
			emitCircle(points[0], points[1], col);
			return;
		}

		if (style == Poly.Type.CIRCLEARC || style == Poly.Type.THICKCIRCLEARC)
		{
			emitArc(points[0], points[1], points[2], col);
			return;
		}

		if (style.isText())
		{
			EditWindow_ wnd = null;
			Poly textPoly = (Poly)poly;
			double size = textPoly.getTextDescriptor().getTrueSize(wnd);
			Rectangle2D box = textPoly.getBounds2D();
			emitText(style, box.getMinX(), box.getMaxX(), box.getMinY(), box.getMaxY(), size, textPoly.getString(), col);
			return;
		}
	}

	void emitLine(double x1, double y1, double x2, double y2, Color col)
	{
		doPenSelection(col);
		movePen(x1, y1);
		drawPen(x2, y2);
	}

	private void emitArc(Point2D center, Point2D p1, Point2D p2, Color col)
	{
		double startAngle = GenMath.figureAngle(center, p1);
		double endAngle = GenMath.figureAngle(center, p2);
		double amt;
		if (startAngle > endAngle) amt = (startAngle - endAngle + 5) / 10; else
			amt = (startAngle - endAngle + 3600 + 5) / 10;
		doPenSelection(col);
		movePen(p1.getX(), p1.getY());
		writeLine("PD;");
		writeLine("AA " + makeCoord(center.getX()) + " " + makeCoord(center.getY()) +
			" " + ((int)-amt) + ";");
		writeLine("PU;");
	}

	private void emitCircle(Point2D at, Point2D e, Color col)
	{
		double radius = at.distance(e);
		doPenSelection(col);
		movePen(at.getX(), at.getY());
		writeLine("PD;");
		writeLine("CI " + makeCoord(radius) + ";");
		writeLine("PU;");
	}

	private void emitDisc(Point2D at, Point2D e, Color col)
	{
		int fillType = doFillSelection(col);

		double radius = at.distance(e);
		movePen(at.getX(), at.getY());
		writeLine("PD;");
		writeLine("PM;");
		writeLine("CI " + makeCoord(radius) + ";");
		writeLine("PM2;");
		if (fillType != 0) writeLine("FP;");
		if (fillType != 1) writeLine("EP;");
		writeLine("PU;");
	}

	private void emitFilledPolygon(Point2D [] points, Color col)
	{
		if (points.length <= 1) return;
		int fillType = doFillSelection(col);

		double firstX = points[0].getX();		// save the end point
		double firstY = points[0].getY();
		movePen(firstX, firstY);	// move to the start
		writeLine("PM;");
		for(int i=1; i<points.length; i++) drawPen(points[i].getX(), points[i].getY());
		drawPen(firstX, firstY);	// close the polygon

		writeLine("PM2;");
		if (fillType != 0) writeLine("FP;");
		if (fillType != 1) writeLine("EP;");
	}

	private void emitText(Poly.Type type, double xl, double xh, double yl, double yh, double size,
		String text, Color col)
	{
		writeLine("SI " + TextUtils.formatDouble(size*0.01/1.3) + "," +
			TextUtils.formatDouble(size*0.01) + ";");
		doPenSelection(col);

		if (type == Poly.Type.TEXTBOTLEFT)
		{
			movePen(xl, yl);
			writeLine("LO1;");
		} else if (type == Poly.Type.TEXTLEFT)
		{
			movePen(xl, (yl+yh)/2);
			writeLine("LO2;");
		} else if (type == Poly.Type.TEXTTOPLEFT)
		{
			movePen(xh, yl);
			writeLine("LO3;");
		} else if (type == Poly.Type.TEXTBOT)
		{
			movePen((xl+xh)/2, yl);
			writeLine("LO4;");
		} else if (type == Poly.Type.TEXTCENT || type == Poly.Type.TEXTBOX)
		{
			movePen((xl+xh)/2, (yl+yh)/2);
			writeLine("LO5;");
		} else if (type == Poly.Type.TEXTTOP)
		{
			movePen((xl+xh)/2, yh);
			writeLine("LO6;");
		} else if (type == Poly.Type.TEXTBOTRIGHT)
		{
			movePen(xh, yl);
			writeLine("LO7;");
		} else if (type == Poly.Type.TEXTRIGHT)
		{
			movePen(xh, (yl+yh)/2);
			writeLine("LO8;");
		} else if (type == Poly.Type.TEXTTOPRIGHT)
		{
			movePen(xh, yh);
			writeLine("LO9;");
		}

		writeLine("LB " + text + "\003");
	}

	/**
	 * Method to setup pen information.
	 */
	private void initPenData()
	{
		penColorTable = new PenColor[256];
		for(int i=0; i<256; i++)
		{
			penColorTable[i] = new PenColor();
			penColorTable[i].lineType = 0;
			penColorTable[i].fillType = 3;
			penColorTable[i].fillDist = makeCoord(i * 2);
			penColorTable[i].fillAngle = (i * 10) % 360;
		}
	}

	/**
	 * Method to accept a color from 0 to 255, and returns either an opaque
	 * color (refer to egraphics.h) or a transparent color.  In other words, this
	 * function masks out the bits used by the grid and highlight.  In cases where
	 * a color is a combination of transparent colors, only one of the transparent
	 * colors is returned.  This approximation is only significant in cases where two
	 * identical transparent objects exactly overlap each other.  For our
	 * applications, this will be rare.
	 */
	private int getPenNumber(Color color)
	{
		Integer ind = penNumbers.get(color);
		if (ind == null) {
            ind = Integer.valueOf(penNumbers.size() + 1);
            penNumbers.put(color, ind);
        }
		return ind.intValue();
	}

	private int doFillSelection(Color col)
	{
		doPenSelection(col);
		int fillType = penColorTable[currentPen].fillType;
		if (!fillEmitted)
		{
			int fillAngle = penColorTable[currentPen].fillAngle;
			int fillDist = penColorTable[currentPen].fillDist;
			writeLine("FT " + fillType + "," + fillDist + "," + fillAngle + ";");
			fillEmitted = true;
		}
		return fillType;
	}

	/**
	 * based upon the current value of "color", this function will select the
	 * proper entry from the pen table, and select an appropriate pen from the
	 * penColorTable.  The appropriate pen and line type is then selected.
	 */
	private void doPenSelection(Color col)
	{
		int pen = getPenNumber(col);
		int desiredPen = pen;
		int lineType = penColorTable[pen].lineType;

		// check to see if pen is defined
		if (desiredPen < 1) desiredPen = 1;

		// select new pen, pen 0 returns pen to carousel and does not get new pen
		if (desiredPen != currentPen)
		{
			writeLine("SP" + desiredPen + ";");
			currentPen = desiredPen;
			fillEmitted = false;
		}

		// set line type, line type 0 defaults to solid
		if (lineType != currentLineType)
		{
			if (lineType == 0) writeLine("LT;"); else
				writeLine("LT" + lineType + ";");
			currentLineType = lineType;
		}
	}

	/**
	 * Method to move the pen to a new position
	 * This has been changed - no longer check to see if we are already there.
	 * This change adds a little to the output file length, but reduces the
	 * amount of code significantly.  The plotter spends most of its time moving
	 * the pen, not reading commands. Therefore, this should be no problem.
	 */
	private void movePen(double x, double y)
	{
		writeLine("PU" + makeCoord(x) + "," + makeCoord(y) + ";");
	}

	/**
	 * Method to draw from current point to the next, assume PA already issued
	 * Changed this so that a PD (pen down) instruction is issued.  Then we
	 * can use this in several other functions.
	 */
	private void drawPen(double x, double y)
	{
		writeLine("PD" + makeCoord(x) + "," + makeCoord(y) + ";");
	}

	private int makeCoord(double v) { return (int)Math.round(v * SCALE); }

	private void writeLine(String line)
	{
		printWriter.print(line + "\r\n");
	}
}
