/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: CIF.java
 * Input/output tool: CIF output
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

import com.sun.electric.database.geometry.Poly;
import com.sun.electric.database.geometry.PolyBase;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.HierarchyEnumerator;
import com.sun.electric.database.hierarchy.Nodable;
import com.sun.electric.database.text.Version;
import com.sun.electric.database.topology.Geometric;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.topology.NodeInst.ExpansionState;
import com.sun.electric.database.variable.VarContext;
import com.sun.electric.technology.Layer;
import com.sun.electric.technology.Technology;
import com.sun.electric.tool.io.IOTool;
import com.sun.electric.util.TextUtils;
import com.sun.electric.util.math.DBMath;
import com.sun.electric.util.math.FixpTransform;
import com.sun.electric.util.math.GenMath;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Class to write CIF output to disk.
 */
public class CIF extends Geometry
{
	// preferences

	// CRC checksum stuff
	/** checksum */										private int crcChecksum;
	/** CRC table */									private int [] crcTab = new int[256];
	/** keep track of chars */							private boolean crcPrevIsCharSep;
	/** number chars written */							private int crcNumChars;
	/** MOSIS initial key */							private static final int[] crcRow =
		{0x04C11DB7, 0x09823B6E, 0x130476DC, 0x2608EDB8, 0x4C11DB70, 0x9823B6E0, 0x34867077, 0x690CE0EE};

	// CIF output data
	/** cell number */									private int cellNumber = 100;
	/** cell to cell number map */						private Map<Cell,Integer> cellNumbers;
	/** scale factor from internal units. */			private double scaleFactor;

	/** illegal characters in names (not really illegal but can cause problems) */
	private static final String badNameChars = ":{}/\\";
	private CIFPreferences localPrefs;

	public static class CIFPreferences extends OutputPreferences
    {
        // CIF Settings
		int cifScale = IOTool.getCIFOutScaleFactor();
		boolean cifOutInstantiatesTopLevel = IOTool.isCIFOutInstantiatesTopLevel();
		boolean cifOutMergesBoxes = IOTool.isCIFOutMergesBoxes();
		boolean cifOutMimicsDisplay = IOTool.isCIFOutMimicsDisplay();
		ExpansionState expansionState;

        public CIFPreferences(boolean factory, Cell cell)
        {
        	super(factory);
            if (factory) expansionState = new ExpansionState(null, ExpansionState.JUSTTHISCELL); else
            	expansionState = new ExpansionState(cell, ExpansionState.JUSTTHISHIERARCHY);
        }

        @Override
        public Output doOutput(Cell cell, VarContext context, String filePath)
        {
    		CIF out = new CIF(this, cell.getTechnology().getScale());
    		if (!out.openTextOutputStream(filePath)) // no error
            {
                CIFVisitor visitor = out.makeCIFVisitor(getMaxHierDepth(cell));
                if (!out.writeCell(cell, context, visitor)) // no error
                {
                    if (!out.closeTextOutputStream()) // no error
                    {
                        System.out.println(filePath + " written");
                        if (out.errorLogger.getNumErrors() != 0)
                            System.out.println(out.errorLogger.getNumErrors() + " CIF RESOLUTION ERRORS FOUND");
                    }
                }
            }
            return out.finishWrite();
        }
    }

	/**
	 * Creates a new instance of CIF
	 */
	CIF(CIFPreferences cp, double techScale)
	{
		localPrefs = cp;
        cellNumbers = new HashMap<Cell,Integer>();

		// scale is in centimicrons, technology scale is in nanometers
		scaleFactor =  techScale / 10 * localPrefs.cifScale;
//		scaleFactor = Technology.getCurrent().getScale() / 10 * localPrefs.cifScale;
    }

	protected void start()
	{
		if (localPrefs.includeDateAndVersionInOutput)
		{
			writeLine("( Electric VLSI Design System, version " + Version.getVersion() + " );");
			Date now = new Date();
			writeLine("( written on " + TextUtils.formatDate(now) + " );");
		} else
		{
			writeLine("( Electric VLSI Design System );");
		}
		emitCopyright("( ", " );");

		// initialize CRC checksum info
		for (int i=0; i<crcTab.length; i++)
		{
			crcTab[i] = 0;
			for (int j=0; j<8; j++)
			{
				if (((1 << j) & i) != 0)
					crcTab[i] = crcTab[i] ^ crcRow[j];
			}
			crcTab[i] &= 0xFFFFFFFF; // unnecessary in java, int is always 32 bits
		}
		crcNumChars = 1;
		crcPrevIsCharSep = true;
		crcChecksum = crcTab[' '];
	}

	protected void done()
	{
		if (localPrefs.cifOutInstantiatesTopLevel)
			writeLine("C " + cellNumber + ";");
		writeLine("E");

		// if output disables popups, tell the error logger
		if (localPrefs.isDisablePopups()) errorLogger.disablePopups();

		// finish up CRC stuff
		if (!crcPrevIsCharSep)
		{
			crcChecksum = (crcChecksum << 8) ^ crcTab[((crcChecksum >> 24) ^ ' ') & 0xFF];
			crcNumChars++;
		}
		int bytesread = crcNumChars;
		while (bytesread > 0)
		{
			crcChecksum = (crcChecksum << 8) ^ crcTab[((crcChecksum >> 24) ^ bytesread) & 0xFF];
			bytesread >>= 8;
		}
		crcChecksum = ~crcChecksum & 0xFFFFFFFF;
		System.out.println("MOSIS CRC: " + GenMath.unsignedIntValue(crcChecksum) + " " + crcNumChars);
	}

	/**
	 * Method to write cellGeom
	 */
	protected void writeCellGeom(CellGeom cellGeom)
	{
		cellNumber++;
		writeLine("DS " + cellNumber + " 1 " + localPrefs.cifScale + ";");
		String cellName = (cellGeom.nonUniqueName ? (cellGeom.cell.getLibrary().getName() + ":") : "") +
			cellGeom.cell.getName() + ";";

		// remove bad chars from cell name
		StringBuffer sb = new StringBuffer();
		for (int i=0; i<cellName.length(); i++)
		{
			char ch = cellName.charAt(i);
			if (badNameChars.indexOf(ch) != -1) ch = '_';
			sb.append(ch);
		}
		writeLine("9 " + sb.toString());
		cellNumbers.put(cellGeom.cell, new Integer(cellNumber));

		// write all polys by Layer
		Set<Layer> layers = cellGeom.polyMap.keySet();
		for (Layer layer : layers)
		{
			if (writeLayer(layer)) continue;
			List<Object> polyList = cellGeom.polyMap.get(layer);
			for (Object polyObj : polyList)
			{
				if (includeGeometric())
				{
					PolyWithGeom pg = (PolyWithGeom)polyObj;
					Poly poly = pg.poly;
					writePoly(poly, cellGeom.cell, pg.geom);
				} else
				{
					PolyBase poly = (PolyBase)polyObj;
					writePoly(poly, cellGeom.cell, null);
				}
			}
		}

		// write all instances
		for (Iterator<NodeInst> noIt = cellGeom.cell.getNodes(); noIt.hasNext(); )
		{
			NodeInst ni = noIt.next();
			if (ni.isCellInstance())
				writeNodable(ni);
		}
		writeLine("DF;");
	}

	/**
	 * method to determine whether or not to merge geometry
	 */
	protected boolean mergeGeom(int hierLevelsFromBottom)
	{
		return localPrefs.cifOutMergesBoxes;
	}

	/**
	 * Method to determine whether or not to include the original Geometric with a Poly.
	 * Only includes Geometric information if NOT merging boxes, because if merging
	 * boxes, then the original Geometric information is lost.
	 */
	protected boolean includeGeometric()
	{
		return !localPrefs.cifOutMergesBoxes;
	}

	/**
	 * Method to emit the current layer number.
	 * @return true if the layer is invalid.
	 */
	private boolean writeLayer(Layer layer)
	{
		String layName = layer.getCIFLayer();
		if (layName == null || layName.equals("")) return true;
		writeLine("L " + layName + ";");
		return false;
	}

	private void writePoly(PolyBase poly, Cell cell, Geometric geom)
	{
		Point2D [] points = poly.getPoints();

		if (poly.getStyle() == Poly.Type.DISC || poly.getStyle() == Poly.Type.CIRCLE)
		{
			checkResolution(poly, cell, geom);
			double r = points[0].distance(points[1]);
			if (r <= 0) return;			// ignore zero size geometry
			int radius = scale(r) * 2;
			int x = scale(points[0].getX());
			int y = scale(points[0].getY());
			String line = " R " + radius + " " + x + " " + y + ";";
			writeLine(line);
		} else
		{
			// ignore zero size geometry
			Rectangle2D bounds = poly.getBounds2D();
			if (bounds.getHeight() <= 0 || bounds.getWidth() <= 0) return;

			// simple case if poly is a box
			Rectangle2D box = poly.getBox();
			if (box != null)
			{
				checkPointResolution(box.getWidth(), box.getHeight(), cell, geom, poly.getLayer(), poly);
				checkPointResolution(box.getCenterX(), box.getCenterY(), cell, geom, poly.getLayer(), poly);
				int width = scale(box.getWidth());
				int height = scale(box.getHeight());
				int x = scale(box.getCenterX());
				int y = scale(box.getCenterY());
				String line = " B " + width + " " + height + " " + x + " " + y + ";";
				writeLine(line);
				return;
			}

			// not a box
			checkResolution(poly, cell, geom);
			StringBuffer line = new StringBuffer(" P");
			for (int i=0; i<points.length; i++)
			{
				int x = scale(points[i].getX());
				int y = scale(points[i].getY());
				line.append(" " + x + " " + y);
			}
			line.append(";");
			writeLine(line.toString());
		}
	}

	private void writeNodable(NodeInst ni)
	{
		Cell cell = (Cell)ni.getProto();

		// if mimicking display and unexpanded, draw outline
		if (!localPrefs.expansionState.isExpanded(ni) && localPrefs.cifOutMimicsDisplay)
		{
			Rectangle2D bounds = ni.getBounds();
			Poly poly = new Poly(bounds.getCenterX(), bounds.getCenterY(), ni.getXSize(), ni.getYSize());
			FixpTransform localPureTrans = ni.rotateOutAboutTrueCenter();
			poly.transform(localPureTrans);
			Point2D [] points = poly.getPoints();
			String line = "0V";
			for(int i=0; i<4; i++)
				line += " " + scale(points[i].getX()) + " " + scale(points[i].getY());
			line += " " + scale(points[0].getX()) + " " + scale(points[0].getY());
			writeLine(line + ";");
			writeLine("2C \"" + cell.describe(false) + "\" T " + scale(bounds.getCenterX()) + " " +
				scale(bounds.getCenterY()) + ";");
			return;
		}

		// write a call to the cell
		int cellNum = cellNumbers.get(cell).intValue();
		int rotx = (int)(DBMath.cos(ni.getAngle()) * 100);
		int roty = (int)(DBMath.sin(ni.getAngle()) * 100);
		String line = "C " + cellNum + " R " + rotx + " " + roty;
		if (ni.isMirroredAboutXAxis()) line += " M Y";
		if (ni.isMirroredAboutYAxis()) line += " M X";
		line += " T " + scale(ni.getAnchorCenterX()) + " " + scale(ni.getAnchorCenterY());
		writeLine(line + ";");
	}

	/**
	 * Write a line to the CIF file, and accumulate
	 * checksum information.
	 */
	private void writeLine(String line)
	{
		line = line + '\n';
		printWriter.print(line);

		// CRC checksum stuff
		for (int i=0; i < line.length(); i++)
		{
			char c = line.charAt(i);
			if (c > ' ')
			{
				crcChecksum = (crcChecksum << 8) ^ crcTab[((crcChecksum >> 24) ^ c) & 0xFF];
				crcPrevIsCharSep = false;
			} else if (!crcPrevIsCharSep)
			{
				crcChecksum = (crcChecksum << 8) ^ crcTab[((crcChecksum >> 24) ^ ' ') & 0xFF];
				crcPrevIsCharSep = true;
			}
			crcNumChars++;
		}
	}

	/**
	 * Method to scale Electric units to CIF units
	 */
	private int scale(double n)
	{
		return (int)(scaleFactor * n);
	}

	/**
	 * Check Poly for CIF Resolution Errors
	 */
	private void checkResolution(PolyBase poly, Cell cell, Geometric geom)
	{
		Point2D [] points = poly.getPoints();
		for (int i=0; i<points.length; i++)
		{
			double x = points[i].getX();
			double y = points[i].getY();
			if (checkPointResolution(x, y, cell, geom, poly.getLayer(), poly)) return;
		}
	}

	private boolean checkPointResolution(double x, double y, Cell cell, Geometric geom, Layer layer, PolyBase poly)
	{
		// first check for CIF resolution
		boolean badPoints = false;
		double scaleX = x * scaleFactor;
		double scaleY = y * scaleFactor;
		if (Math.round(scaleX) != scaleX || Math.round(scaleY) != scaleY)
		{
			System.out.println("Warning: resolution is too fine: (" + scaleX + "," + scaleY + ") should be (" +
				Math.round(scaleX) + "," + Math.round(scaleY) + ")");
			badPoints = true;
		}

		boolean badResolution = false;
//		if (minAllowedResolution != 0)
//		{
////		if ((x % minAllowedResolution) != 0 || (y % minAllowedResolution) != 0)
//			if (DBMath.hasRemainder(x, minAllowedResolution) || DBMath.hasRemainder(y, minAllowedResolution))
//			{
//				badResolution = true;
//			}
//		}

		boolean error = badPoints || badResolution;
		if (error)
		{
			// there was an error, for now print error
			String layerName;
			if (layer == null)
			{
				layerName = "**UNKNOWN**";
			} else
			{
				layerName = layer.getName();
			}
			String msg = "Resolution less than CIF allows on layer " + layerName + ": (" + x + "," + y + ") not on " + (1/scaleFactor) + " grid";
			if (geom != null)
			{
				errorLogger.logError(msg, geom, cell, null, layer.getIndex());
			} else
			{
				errorLogger.logError(msg, poly, cell, layer.getIndex());
			}
		}
		return error;
	}

	/****************************** VISITOR SUBCLASS ******************************/

	private CIFVisitor makeCIFVisitor(int maxDepth)
	{
		CIFVisitor visitor = new CIFVisitor(this, maxDepth);
		return visitor;
	}

	/**
	 * Class to override the Geometry visitor and add bloating to all polygons.
	 * Currently, no bloating is being done.
	 */
	private class CIFVisitor extends Geometry.Visitor
	{
		CIFVisitor(Geometry outGeom, int maxHierDepth)
		{
			super(outGeom, maxHierDepth);
		}

		/**
		 * Overriding this class allows us to stop descending into unexpanded instances in "mimic" mode.
		 */
		public boolean visitNodeInst(Nodable no, HierarchyEnumerator.CellInfo info)
		{
			NodeInst ni = (NodeInst)no;
			if (ni.isCellInstance())
			{
				if (!localPrefs.expansionState.isExpanded(ni) && localPrefs.cifOutMimicsDisplay)
				{
					return false;
				}
			}
			return super.visitNodeInst(no, info);
		}
	}

}
