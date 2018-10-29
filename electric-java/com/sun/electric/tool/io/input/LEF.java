/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: LEF.java
 * Input/output tool: LEF (Library Exchange Format) reader
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
package com.sun.electric.tool.io.input;

import com.sun.electric.database.EditingPreferences;
import com.sun.electric.database.geometry.EPoint;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.Export;
import com.sun.electric.database.hierarchy.Library;
import com.sun.electric.database.id.CellId;
import com.sun.electric.database.prototype.NodeProto;
import com.sun.electric.database.prototype.PortCharacteristic;
import com.sun.electric.database.prototype.PortProto;
import com.sun.electric.database.text.TextUtils;
import com.sun.electric.database.topology.ArcInst;
import com.sun.electric.database.topology.Geometric;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.topology.PortInst;
import com.sun.electric.technology.ArcProto;
import com.sun.electric.technology.Layer;
import com.sun.electric.technology.PrimitiveNode;
import com.sun.electric.technology.Technology;
import com.sun.electric.technology.technologies.Generic;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.io.IOTool;
import com.sun.electric.util.math.DBMath;
import com.sun.electric.util.math.MutableBoolean;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * This class reads files in LEF files.
 * <BR>
 * Note that this reader was built by examining LEF files and reverse-engineering them.
 * It does not claim to be compliant with the LEF specification, but it also does not
 * claim to define a new specification.  It is merely incomplete.
 */
public class LEF extends LEFDEF
{
	protected static final boolean PLACEGEOMETRY = true;
	protected static final boolean PLACEEXPORTS = true;
	protected static final boolean PLACEONLYMETAL = false;

	/*************** LEF PATHS ***************/

	private static class LEFPath
	{
		private Point2D []  pt;
		private NodeInst [] ni;
		private double      width;
		private ArcProto    arc;
		private LEFPath     nextLEFPath;

		private LEFPath()
		{
			pt = new Point2D[2];
			ni = new NodeInst[2];
		}
	}
	private LEFPreferences localPrefs;

	public static class LEFPreferences extends InputPreferences
    {
		private boolean ignoreUngeneratedPins;
		public boolean ignoreTechnology;
		public boolean continueReading; // looking for ways to keep reading in case of some errors.
		public double overallscale = 1;
		public int unknownLayerHandling;

		public LEFPreferences(boolean factory)
		{
			super(factory);
			continueReading = false;
        	if (factory)
        	{
    			ignoreUngeneratedPins = IOTool.isFactoryLEFIgnoreUngeneratedPins();
    			ignoreTechnology = IOTool.isFactoryLEFIgnoreTechnology();
    			unknownLayerHandling = IOTool.getFactoryLEFInUnknownLayerHandling();
        	} else
            {
    			ignoreUngeneratedPins = IOTool.isLEFIgnoreUngeneratedPins();
    			ignoreTechnology = IOTool.isLEFIgnoreTechnology();
    			unknownLayerHandling = IOTool.getLEFInUnknownLayerHandling();
            }
		}

        @Override
        public Library doInput(URL fileURL, Library lib, Technology tech, EditingPreferences ep, Map<Library,Cell> currentCells, Map<CellId,BitSet> nodesToExpand, Job job)
        {
        	LEF in = new LEF(ep, this);
			if (in.openTextInput(fileURL)) return null;
			lib = in.importALibrary(lib, tech, currentCells);
			in.closeInput();
			return lib;
        }
        
        public boolean doTechInput(URL fileURL, EditingPreferences ep)
        {
        	LEF in = new LEF(ep, this);
			if (in.openTextInput(fileURL)) return false;
			boolean result = in.importTechFile();
			in.closeInput();
			return result;
        }
    }

	/**
	 * Creates a new instance of LEF.
	 */
	LEF(EditingPreferences ep, LEFPreferences ap) {
        super(ep);
        localPrefs = ap;
    }

	/**
	 * Method to import a library from disk.
	 * @param lib the library to fill
     * @param currentCells this map will be filled with currentCells in Libraries found in library file
	 * @return the created library (null on error).
	 */
    @Override
	protected Library importALibrary(Library lib, Technology tech, Map<Library,Cell> currentCells)
	{
		// remove any vias in the globals
    	initializeLEFDEF(tech);
		widthsFromLEF = new HashMap<ArcProto,Double>();
		initKeywordParsing();

		try
		{
            if (readFile(lib)) return null; // error during reading
        } catch (IOException e)
		{
        	reportError("ERROR reading LEF libraries", null);
		}
		return lib;
	}

	/**
	 * Helper method for keyword processing which removes comments.
	 * @param line a line of text just read.
	 * @return the line after comments have been removed.
	 */
	protected String preprocessLine(String line)
	{
		int sharpPos = line.indexOf('#');
		if (sharpPos >= 0) return line.substring(0, sharpPos);
		return line;
	}

	/**
	 * Method to read the LEF file.
	 * @return true on error.
	 */
	private boolean readFile(Library lib)
		throws IOException
	{
		for(;;)
		{
			// get the next keyword
			String key = getAKeyword();
			if (key == null) break;
			if (key.equalsIgnoreCase("LAYER"))
			{
				if (readLayer()) return true;
			}
			if (key.equalsIgnoreCase("MACRO"))
			{
				if (readMacro(lib)) return true;
			}
			if (key.equalsIgnoreCase("VIA"))
			{
				if (readVia(lib)) return true;
			}
			if (key.equalsIgnoreCase("VIARULE") || key.equalsIgnoreCase("SITE") ||
				key.equalsIgnoreCase("ARRAY"))
			{
				String name = getAKeyword();
				ignoreToEnd(name, null);
				continue;
			}
			if (key.equalsIgnoreCase("SPACING") || key.equalsIgnoreCase("PROPERTYDEFINITIONS"))
			{
				ignoreToEnd(key, null);
				continue;
			}
			if (key.equalsIgnoreCase("MINFEATURE"))
			{
				ignoreToSemicolon(key, null);
				continue;
			}
		}
		return false;
	}

	private boolean readVia(Library lib)
		throws IOException
	{
		// get the via name
		String viaName = getAKeyword();
		if (viaName == null) return true;

		// create a new via definition
		ViaDef vd = new ViaDef(viaName, null);
		viaDefsFromLEF.put(viaName.toLowerCase(), vd);

		Cell cell = null;
		// It doesn't add a cell associated with the VIA if the technology 
		// should be ignored.
		if (PLACEGEOMETRY && !localPrefs.ignoreTechnology)
		{
			String cellName = viaName + "{lay}";
			cell = createCell(lib, cellName);
			if (cell == null)
			{
				reportError("Cannot create via cell '" + cellName + "'", null);
				return true;
			}
		}

		boolean ignoreDefault = true;
		GetLayerInformation li = null;
		double cutSizeX = 0, cutSizeY = 0, cutSpacingX = 0, cutSpacingY = 0;
		double leftEnclosure = 0, rightEnclosure = 0, topEnclosure = 0, bottomEnclosure = 0;
		int rowColX = 0, rowColY = 0;
		GetLayerInformation lowMetalLayer = null, highMetalLayer = null, viaLayer = null;
		for(;;)
		{
			// get the next keyword
			String key = getAKeyword();
			if (key == null) return true;
			if (ignoreDefault)
			{
				ignoreDefault = false;
				if (key.equalsIgnoreCase("DEFAULT")) continue;
			}
			if (key.equalsIgnoreCase("END"))
			{
				key = getAKeyword();
				break;
			}
			if (key.equalsIgnoreCase("CUTSIZE"))
			{
				// get the X cut size
				key = getAKeyword();
				if (key == null) return true;
				cutSizeX = convertLEFString(key);

				// get the Y cut size
				key = getAKeyword();
				if (key == null) return true;
				cutSizeY = convertLEFString(key);
				continue;
			}
			if (key.equalsIgnoreCase("CUTSPACING"))
			{
				// get the X cut spacing
				key = getAKeyword();
				if (key == null) return true;
				cutSpacingX = convertLEFString(key);

				// get the Y cut size
				key = getAKeyword();
				if (key == null) return true;
				cutSpacingY = convertLEFString(key);
				continue;
			}
			if (key.equalsIgnoreCase("ENCLOSURE"))
			{
				// get the left via enclosure
				key = getAKeyword();
				if (key == null) return true;
				leftEnclosure = convertLEFString(key);

				// get the bottom via enclosure
				key = getAKeyword();
				if (key == null) return true;
				bottomEnclosure = convertLEFString(key);

				// get the right via enclosure
				key = getAKeyword();
				if (key == null) return true;
				rightEnclosure = convertLEFString(key);

				// get the top via enclosure
				key = getAKeyword();
				if (key == null) return true;
				topEnclosure = convertLEFString(key);
				continue;
			}
			if (key.equalsIgnoreCase("ROWCOL"))
			{
				// get the X via repeat
				key = getAKeyword();
				if (key == null) return true;
				rowColX = TextUtils.atoi(key);

				// get the Y via repeat
				key = getAKeyword();
				if (key == null) return true;
				rowColY = TextUtils.atoi(key);
				continue;
			}
			if (key.equalsIgnoreCase("LAYERS"))
			{
				// get the lower metal layer
				key = getAKeyword();
				if (key == null) return true;
				lowMetalLayer = getLayerInformation(key, null);
				if (lowMetalLayer.pure == null)
				{
					reportError("Layer " + key + " not found", cell);
					return true;
				}

				// get the via layer
				key = getAKeyword();
				if (key == null) return true;
				viaLayer = getLayerInformation(key, null);
				if (viaLayer.pure == null)
				{
					reportError("Layer " + key + " not found", cell);
					return true;
				}

				// get the high metal layer
				key = getAKeyword();
				if (key == null) return true;
				highMetalLayer = getLayerInformation(key, null);
				if (highMetalLayer.pure == null)
				{
					reportError("Layer " + key + " not found", cell);
					return true;
				}
				continue;
			}
			if (key.equalsIgnoreCase("RESISTANCE"))
			{
				if (ignoreToSemicolon(key, cell)) return true;
				continue;
			}
			if (key.equalsIgnoreCase("LAYER"))
			{
				key = getAKeyword();
				if (key == null) return true;
				li = getLayerInformation(key, null);
				if (li.arc != null)
				{
					if (vd.gLay1 == null) vd.gLay1 = li; else
						vd.gLay2 = li;
				}
				if (ignoreToSemicolon("LAYER", cell)) return true;
				continue;
			}
			if (key.equalsIgnoreCase("RECT"))
			{
				// handle definition of a via rectangle
				key = getAKeyword();
				if (key == null) return true;
				double lX = convertLEFString(key);

				key = getAKeyword();
				if (key == null) return true;
				double lY = convertLEFString(key);

				key = getAKeyword();
				if (key == null) return true;
				double hX = convertLEFString(key);

				key = getAKeyword();
				if (key == null) return true;
				double hY = convertLEFString(key);

				// accumulate largest layer size
				if (hX-lX > vd.sX) vd.sX = hX - lX;
				if (hY-lY > vd.sY) vd.sY = hY - lY;

				// create the geometry
				if (cell != null)
				{
					NodeProto np = li.pure;
					if (np == null)
					{
						reportError(" No layer '" + li.name + "' defined for RECT", cell);
						return true;
					}
					if (!PLACEONLYMETAL || li.layerFun.isMetal())
					{
						Point2D ctr = new Point2D.Double((lX+hX)/2, (lY+hY)/2);
						double sX = Math.abs(hX - lX);
						double sY = Math.abs(hY - lY);
						NodeInst ni = NodeInst.makeInstance(np, ep, ctr, sX, sY, cell);
						if (ni == null)
						{
							reportError("Cannot create node for RECT", cell);
							return true;
						}
					}
				}

				if (ignoreToSemicolon("RECT", cell)) return true;
				continue;
			}
		}
		if (cell != null)
		{
			Point2D ctr = new Point2D.Double(0, 0);
			PrimitiveNode pnp = Generic.tech().universalPinNode;
			NodeInst ni = NodeInst.makeInstance(pnp, ep, ctr, pnp.getDefWidth(ep), pnp.getDefHeight(ep), cell);
			PortInst pi = ni.getOnlyPortInst();
			Export e = Export.newInstance(cell, pi, "viaPort", ep);
			if (vd.gLay1 != null && vd.gLay2 != null)
			{
				String[] preferredArcs = new String[2];
				preferredArcs[0] = vd.gLay1.arc.getFullName();
				preferredArcs[1] = vd.gLay2.arc.getFullName();
				e.newVar(Export.EXPORT_PREFERRED_ARCS, preferredArcs, ep);
			}

			// see if cut rules are defined
			if (rowColX > 0 && rowColY > 0 && lowMetalLayer != null && highMetalLayer != null && viaLayer != null)
			{
				// the via layers
				double cutsWidth = rowColX * cutSizeX + (rowColX-1) * cutSpacingX;
				double cutsHeight = rowColY * cutSizeY + (rowColY-1) * cutSpacingY;
				for(int x=0; x<rowColX; x++)
				{
					for(int y=0; y<rowColY; y++)
					{
						double xP = -cutsWidth/2 + x*(cutSizeX + cutSpacingX) + cutSizeX/2;
						double yP = -cutsHeight/2 + y*(cutSizeY + cutSpacingY) + cutSizeY/2;
						ni = NodeInst.makeInstance(viaLayer.pure, ep, EPoint.fromLambda(xP, yP), cutSizeX, cutSizeY, cell);
						if (ni == null) return true;
					}
				}

				// the via layer
				double sX = cutsWidth + leftEnclosure + rightEnclosure;
				double sY = cutsHeight + topEnclosure + bottomEnclosure;
				double cX = (rightEnclosure - leftEnclosure) / 2;
				double cY = (topEnclosure - bottomEnclosure) / 2;
				EPoint metCtr = EPoint.fromLambda(cX, cY);
				ni = NodeInst.makeInstance(lowMetalLayer.pure, ep, metCtr, sX, sY, cell);
				if (ni == null) return true;
				ni = NodeInst.makeInstance(highMetalLayer.pure, ep, metCtr, sX, sY, cell);
				if (ni == null) return true;
			}
		}
		if (vd.gLay1 != null && vd.gLay2 != null)
		{
			for(Iterator<PrimitiveNode> it = curTech.getNodes(); it.hasNext(); )
			{
				PrimitiveNode np = it.next();
				if (!np.getFunction().isContact()) continue;
				PortProto pp = np.getPort(0);
				if (pp.connectsTo(vd.gLay1.arc) && pp.connectsTo(vd.gLay2.arc))
				{
					vd.via = np;
					break;
				}
			}
		}
		return false;
	}

	private Cell createCell(Library lib, String cellName)
	{
		Cell cell = Cell.makeInstance(ep, lib, cellName);
		if (cell == null)
		{
			reportError("Cannot create cell '" + cellName + "'", null);
			return null;
		}

		cell.setTechnology(curTech);
		return cell;
	}
	
	private boolean readMacro(Library lib)
		throws IOException
	{
		String cellName = getAKeyword();
		if (cellName == null)
		{
			reportError("EOF parsing MACRO header", null);
			return true;
		}
		cellName = cellName + (PLACEGEOMETRY ? "{lay}" : "{lay.sk}");
		Cell cell = createCell(lib, cellName);
		if (cell == null)
		{
			return true;
		}
		
		for(;;)
		{
			// get the next keyword
			String key = getAKeyword();
			if (key == null)
			{
				reportError("EOF parsing MACRO", cell);
				return true;
			}

			if (key.equalsIgnoreCase("END"))
			{
				key = getAKeyword();
				break;
			}

			if (key.equalsIgnoreCase("SOURCE") || key.equalsIgnoreCase("FOREIGN") ||
				key.equalsIgnoreCase("SYMMETRY") || key.equalsIgnoreCase("SITE") ||
				key.equalsIgnoreCase("CLASS") || key.equalsIgnoreCase("LEQ") ||
				key.equalsIgnoreCase("POWER") || key.equalsIgnoreCase("PROPERTY"))
			{
				if (ignoreToSemicolon(key, cell)) return true;
				continue;
			}

			if (key.equalsIgnoreCase("ORIGIN"))
			{
				key = getAKeyword();
				if (key == null)
				{
					reportError("EOF reading ORIGIN X", cell);
					return true;
				}
				double oX = convertLEFString(key);

				key = getAKeyword();
				if (key == null)
				{
					reportError("EOF reading ORIGIN Y", cell);
					return true;
				}
				double oY = convertLEFString(key);
				if (ignoreToSemicolon("ORIGIN", cell)) return true;

				// create or move the cell-center node
				NodeInst ccNi = null;
				for(Iterator<NodeInst> it = cell.getNodes(); it.hasNext(); )
				{
					NodeInst ni = it.next();
					if (Generic.isCellCenter(ni)) { ccNi = ni;   break; }
				}
				if (ccNi == null)
				{
					double sX = Generic.tech().cellCenterNode.getDefWidth(ep);
					double sY = Generic.tech().cellCenterNode.getDefHeight(ep);
					ccNi = NodeInst.makeInstance(Generic.tech().cellCenterNode, ep, new Point2D.Double(oX, oY), sX, sY, cell);
					if (ccNi == null)
					{
						reportError("Cannot create cell center node", cell);
						return true;
					}
					ccNi.setHardSelect();
					ccNi.setVisInside();
				} else
				{
					double dX = oX - ccNi.getTrueCenterX();
					double dY = oY - ccNi.getTrueCenterY();
					ccNi.move(dX, dY);
				}
				continue;
			}

			if (key.equalsIgnoreCase("SIZE"))
			{
				key = getAKeyword();
				if (key == null)
				{
					reportError("EOF reading SIZE X", cell);
					return true;
				}
				double wid = convertLEFString(key);		// get width

				key = getAKeyword();
				if (key == null)
				{
					reportError("EOF reading SIZE 'BY'", cell);
					return true;
				}
				if (!key.equalsIgnoreCase("BY"))
				{
					reportError("Expected 'by' in SIZE", cell);
					return true;
				}

				key = getAKeyword();
				if (key == null)
				{
					reportError("EOF reading SIZE Y", cell);
					return true;
				}
				double hei = convertLEFString(key);		// get height

				// this data is ignored
				cell.newVar(prXkey, new Double(wid), ep);
				cell.newVar(prYkey, new Double(hei), ep);

				if (ignoreToSemicolon("SIZE", cell)) return true;

				if (!PLACEGEOMETRY)
				{
					Point2D ctr = new Point2D.Double(wid/2, hei/2);
					NodeInst.makeInstance(Generic.tech().invisiblePinNode, ep, ctr, wid, hei, cell);
				}
				continue;
			}

			if (key.equalsIgnoreCase("PIN"))
			{
				if (readPin(cell)) return true;
				continue;
			}

			if (key.equalsIgnoreCase("OBS"))
			{
				if (readObs(cell)) return true;
				continue;
			}

			reportError("Unknown MACRO keyword (" + key + ")", cell);
			return true;
		}
		return false;
	}

	private boolean readObs(Cell cell)
		throws IOException
	{
		NodeProto np = null;
		GetLayerInformation li = null;
		for(;;)
		{
			String key = getAKeyword();
			if (key == null)
			{
				reportError("EOF parsing OBS", cell);
				return true;
			}

			if (key.equalsIgnoreCase("END")) break;

			if (key.equalsIgnoreCase("LAYER"))
			{
				key = getAKeyword();
				if (key == null)
				{
					reportError("EOF reading LAYER clause", cell);
					return true;
				}
				li = getLayerBasedOnNameAndMask(key, null, localPrefs.unknownLayerHandling);
				if (li != null) np = li.pure;
				if (li == null || li.layerFun == Layer.Function.UNKNOWN || np == null)
				{
					reportError("Unknown layer name (" + key + ")", cell);
					return true;
				}
				if (ignoreToSemicolon("LAYER", cell)) return true;
				continue;
			}

			if (key.equalsIgnoreCase("RECT"))
			{
				key = getAKeyword();
				if (key == null)
				{
					reportError("EOF reading RECT low X", cell);
					return true;
				}
				double lX = convertLEFString(key);

				key = getAKeyword();
				if (key == null)
				{
					reportError("EOF reading RECT low Y", cell);
					return true;
				}
				double lY = convertLEFString(key);

				key = getAKeyword();
				if (key == null)
				{
					reportError("EOF reading RECT high X", cell);
					return true;
				}
				double hX = convertLEFString(key);

				key = getAKeyword();
				if (key == null)
				{
					reportError("EOF reading RECT high Y", cell);
					return true;
				}
				double hY = convertLEFString(key);

				if (ignoreToSemicolon("RECT", cell)) return true;

				// make the obstruction
				if (PLACEGEOMETRY)
				{
					if (np == null)
					{
						reportError("No layers for RECT", cell);
						return true;
					}
					if (PLACEONLYMETAL && !li.layerFun.isMetal()) continue;
					Point2D ctr = new Point2D.Double((lX+hX)/2, (lY+hY)/2);
					double sX = Math.abs(hX - lX);
					double sY = Math.abs(hY - lY);
					NodeInst ni = NodeInst.makeInstance(np, ep, ctr, sX, sY, cell);
					if (ni == null)
					{
						reportError("Cannot create node for RECT", cell);
						return true;
					}
				}
				continue;
			}

			if (key.equalsIgnoreCase("POLYGON"))
			{
				// gather the points in the polygon
				List<Point2D> points = readPolygon(cell);
				if (points == null) return true;

				// make the pin
				if (PLACEGEOMETRY)
				{
					if (np == null)
					{
						reportError("No layers for POLYGON", cell);
						return true;
					}
					if (PLACEONLYMETAL && !li.layerFun.isMetal()) continue;

					// compute the bounding box
					double lX = 0, lY = 0, hX = 0, hY = 0;
					for(int i=0; i<points.size(); i++)
					{
						Point2D pt = points.get(i);
						if (i == 0)
						{
							lX = hX = pt.getX();
							lY = hY = pt.getY();
						} else
						{
							if (pt.getX() < lX) lX = pt.getX();
							if (pt.getX() > hX) hX = pt.getX();
							if (pt.getY() < lY) lY = pt.getY();
							if (pt.getY() > hY) hY = pt.getY();
						}
					}

					// create the pure-layer node with the outline information
					Point2D ctr = new Point2D.Double((lX+hX)/2, (lY+hY)/2);
					double sX = Math.abs(hX - lX);
					double sY = Math.abs(hY - lY);
					NodeInst ni = NodeInst.makeInstance(np, ep, ctr, sX, sY, cell);
					if (ni == null)
					{
						reportError("Cannot create pin for POLYGON", cell);
						return true;
					}
					Point2D [] outline = new Point2D[points.size()];
					for(int i=0; i<points.size(); i++)
						outline[i] = EPoint.fromLambda(points.get(i).getX() - ctr.getX(), points.get(i).getY() - ctr.getY());
					ni.setTrace(outline);
				}
				continue;
			}
		}
		return false;
	}

	private boolean readPin(Cell cell)
		throws IOException
	{
		// get the pin name
		String key = getAKeyword();
		if (key == null)
		{
			reportError("EOF parsing PIN name", cell);
			return true;
		}
		String pinName = key.replace('<', '[').replace('>', ']');
		MutableBoolean pinMade = new MutableBoolean(false);
		
		PortCharacteristic useCharacteristics = PortCharacteristic.UNKNOWN;
		PortCharacteristic portCharacteristics = PortCharacteristic.UNKNOWN;
		for(;;)
		{
			key = getAKeyword();
			if (key == null)
			{
				reportError("EOF parsing PIN", cell);
				return true;
			}

			if (key.equalsIgnoreCase("END"))
			{
				key = getAKeyword();
				break;
			}

			if (key.equalsIgnoreCase("SHAPE") || key.equalsIgnoreCase("CAPACITANCE") ||
				key.equalsIgnoreCase("ANTENNASIZE") || key.equalsIgnoreCase("ANTENNADIFFAREA") ||
				key.equalsIgnoreCase("ANTENNAMODEL") || key.equalsIgnoreCase("ANTENNAGATEAREA") ||
				key.equalsIgnoreCase("ANTENNAPARTIALCUTAREA") || key.equalsIgnoreCase("ANTENNAMAXAREACAR") ||
				key.equalsIgnoreCase("ANTENNAMAXCUTCAR") || key.equalsIgnoreCase("PROPERTY"))
			{
				if (ignoreToSemicolon(key, cell)) return true;
				continue;
			}

			if (key.equalsIgnoreCase("USE"))
			{
				key = getAKeyword();
				if (key == null)
				{
					reportError("EOF reading USE clause", cell);
					return true;
				}
				if (key.equalsIgnoreCase("POWER")) useCharacteristics = PortCharacteristic.PWR; else
				if (key.equalsIgnoreCase("GROUND")) useCharacteristics = PortCharacteristic.GND; else
				if (key.equalsIgnoreCase("CLOCK")) useCharacteristics = PortCharacteristic.CLK; else
				if (!key.equalsIgnoreCase("SIGNAL") && !key.equalsIgnoreCase("DATA"))
				{
					reportError("Unknown USE keyword (" + key + ")", cell);
				}
				if (ignoreToSemicolon("USE", cell)) return true;
				continue;
			}

			if (key.equalsIgnoreCase("DIRECTION"))
			{
				key = getAKeyword();
				if (key == null)
				{
					reportError("EOF reading DIRECTION clause", cell);
					return true;
				}
				if (key.equalsIgnoreCase("INPUT")) portCharacteristics = PortCharacteristic.IN; else
				if (key.equalsIgnoreCase("OUTPUT")) portCharacteristics = PortCharacteristic.OUT; else
				if (key.equalsIgnoreCase("INOUT")) portCharacteristics = PortCharacteristic.BIDIR; else
				{
					reportError("Unknown DIRECTION keyword (" + key + ")", cell);
				}
				if (ignoreToSemicolon("DIRECTION", cell)) return true;
				continue;
			}

			if (key.equalsIgnoreCase("PORT"))
			{
				if (useCharacteristics != PortCharacteristic.UNKNOWN) portCharacteristics = useCharacteristics;
				if (readPort(cell, pinName, portCharacteristics, pinMade)) 
				{
					if (this.localPrefs.continueReading)
						continue;
					return true;
				}
				continue;
			}

			reportError("Unknown PIN keyword (" + key + ")", cell);
			return true;
		}
		if (!pinMade.booleanValue() && !localPrefs.ignoreUngeneratedPins)
		{
			Point2D ctr = new Point2D.Double(0, 0);
			double sX = 0;
			double sY = 0;
			PrimitiveNode pureNp = Generic.tech().universalPinNode;
			NodeInst ni = NodeInst.makeInstance(pureNp, ep, ctr, sX, sY, cell);
			if (ni == null)
			{
				reportError("Cannot create universal pin for RECT", cell);
				return true;
			}
			newPort(cell, ni, pureNp.getPort(0), pinName);
			reportWarning("Pin " + pinName + " in macro " + cell.describe(false) + " has no location, presuming (0,0)", ni, cell);
		}
		return false;
	}

	private boolean readPort(Cell cell, String portname, PortCharacteristic portCharacteristics, MutableBoolean pinMade)
		throws IOException
	{
		ArcProto ap = null;
		NodeProto pureNp = null;
		LEFPath lefPaths = null;
		boolean first = true;
		double intWidth = 0;
		double lastIntX = 0, lastIntY = 0;
		Point2D singlePathPoint = null;
		GetLayerInformation li = null;
		for(;;)
		{
			String key = getAKeyword();
			if (key == null)
			{
				reportError("EOF parsing PORT", cell);
				return true;
			}

			if (key.equalsIgnoreCase("END"))
			{
				break;
			}
			if (key.equalsIgnoreCase("CLASS"))
			{
				if (ignoreToSemicolon("LAYER", cell)) return true;
				continue;
			}
			if (key.equalsIgnoreCase("LAYER"))
			{
				key = getAKeyword();
				if (key == null)
				{
					reportError("EOF reading LAYER clause", cell);
					return true;
				}
				li = getLayerInformation(key, null);
				ap = li.arc;
				pureNp = li.pure;
				if (ignoreToSemicolon("LAYER", cell)) return true;
				continue;
			}

			if (key.equalsIgnoreCase("WIDTH"))
			{
				key = getAKeyword();
				if (key == null)
				{
					reportError("EOF reading WIDTH clause", cell);
					return true;
				}
				intWidth = convertLEFString(key);
				if (ignoreToSemicolon("WIDTH", cell)) return true;
				continue;
			}

			if (key.equalsIgnoreCase("POLYGON"))
			{
				// gather the points in the polygon
				List<Point2D> points = readPolygon(cell);
				if (points == null) return true;

				if (pureNp == null)
				{
					reportError("No layers for POLYGON", cell);
					return true;
				}
				if (PLACEONLYMETAL && !li.layerFun.isMetal()) continue;

				// make the pin
				pinMade.setValue(true);
				if (PLACEEXPORTS)
				{
					// compute the bounding box
					double lX = 0, lY = 0, hX = 0, hY = 0;
					for(int i=0; i<points.size(); i++)
					{
						Point2D pt = points.get(i);
						if (i == 0)
						{
							lX = hX = pt.getX();
							lY = hY = pt.getY();
						} else
						{
							if (pt.getX() < lX) lX = pt.getX();
							if (pt.getX() > hX) hX = pt.getX();
							if (pt.getY() < lY) lY = pt.getY();
							if (pt.getY() > hY) hY = pt.getY();
						}
					}

					// create the pure-layer node with the outline information
					Point2D ctr = new Point2D.Double((lX+hX)/2, (lY+hY)/2);
					double sX = Math.abs(hX - lX);
					double sY = Math.abs(hY - lY);
					NodeInst ni = NodeInst.makeInstance(pureNp, ep, ctr, sX, sY, cell);
					if (ni == null)
					{
						reportError("Cannot create pin for POLYGON", cell);
						return true;
					}
					Point2D [] outline = new Point2D[points.size()];
					for(int i=0; i<points.size(); i++)
						outline[i] = EPoint.fromLambda(points.get(i).getX() - ctr.getX(), points.get(i).getY() - ctr.getY());
					ni.setTrace(outline);

					if (first)
					{
						// create the port on the first pin
						first = false;
						Export pp = newPort(cell, ni, pureNp.getPort(0), portname);
						if (pp != null) pp.setCharacteristic(portCharacteristics);
					}
				}
				continue;
			}
			
			if (key.equalsIgnoreCase("RECT"))
			{
				key = getAKeyword();
				if (key == null)
				{
					reportError("EOF reading RECT low X", cell);
					return true;
				}
				double lX = convertLEFString(key);

				key = getAKeyword();
				if (key == null)
				{
					reportError("EOF reading RECT low Y", cell);
					return true;
				}
				double lY = convertLEFString(key);

				key = getAKeyword();
				if (key == null)
				{
					reportError("EOF reading RECT high X", cell);
					return true;
				}
				double hX = convertLEFString(key);

				key = getAKeyword();
				if (key == null)
				{
					reportError("EOF reading RECT high Y", cell);
					return true;
				}
				double hY = convertLEFString(key);

				if (ignoreToSemicolon("RECT", cell)) return true;

				if (pureNp == null)
				{
					reportError("No layers for RECT", cell);
					return true;
				}
				if (PLACEONLYMETAL && !li.layerFun.isMetal()) continue;

				// make the pin
				pinMade.setValue(true);
				if (PLACEEXPORTS)
				{
					Point2D ctr = new Point2D.Double((lX+hX)/2, (lY+hY)/2);
					double sX = Math.abs(hX - lX);
					double sY = Math.abs(hY - lY);
					NodeInst ni = NodeInst.makeInstance(pureNp, ep, ctr, sX, sY, cell);
					if (ni == null)
					{
						reportError("Cannot create pin for RECT", cell);
						return true;
					}

					if (first)
					{
						// create the port on the first pin
						first = false;
						Export pp = newPort(cell, ni, pureNp.getPort(0), portname);
						if (pp != null) pp.setCharacteristic(portCharacteristics);
					}
				}
				continue;
			}

			if (key.equalsIgnoreCase("PATH"))
			{
				if (ap == null)
				{
					reportError("No arc associated with layer '" + li.name + "' for PATH definition", cell);
					return true;
				}
				for(int i=0; ; i++)
				{
					key = getAKeyword();
					if (key == null)
					{
						reportError("EOF reading PATH clause", cell);
						return true;
					}
					if (key.equals(";")) break;
					double intx = convertLEFString(key);

					key = getAKeyword();
					if (key == null)
					{
						reportError("EOF reading PATH clause", cell);
						return true;
					}
					double inty = convertLEFString(key);

					// plot this point
					if (i == 0) singlePathPoint = new Point2D.Double(intx, inty); else
					{
						// queue path
						LEFPath lp = new LEFPath();
						lp.pt[0] = new Point2D.Double(lastIntX, lastIntY);
						lp.pt[1] = new Point2D.Double(intx, inty);
						lp.ni[0] = null;        lp.ni[1] = null;
						lp.width = intWidth;
						lp.arc = ap;
						lp.nextLEFPath = lefPaths;
						lefPaths = lp;
					}
					lastIntX = intx;   lastIntY = inty;
				}
				continue;
			}

			if (key.equalsIgnoreCase("VIA"))
			{
				// get the coordinates
				key = getAKeyword();
				if (key == null)
				{
					reportError("EOF reading VIA clause", cell);
					return true;
				}
				double intX = convertLEFString(key);

				key = getAKeyword();
				if (key == null)
				{
					reportError("EOF reading VIA clause", cell);
					return true;
				}
				double intY = convertLEFString(key);

				// find the proper via
				key = getAKeyword();
				li = getLayerInformation(key, null);
				if (li.pin == null)
				{
					reportError("No Via in current technology for '" + key + "'", cell);
					return true;
				}
				if (ignoreToSemicolon("VIA", cell)) return true;

				// create the via
				if (PLACEGEOMETRY)
				{
					double sX = li.pin.getDefWidth(ep);
					double sY = li.pin.getDefHeight(ep);
					NodeInst ni = NodeInst.makeInstance(li.pin, ep, new Point2D.Double(intX, intY), sX, sY, cell);
					if (ni == null)
					{
						reportError("Cannot create VIA for PATH", cell);
						return true;
					}
				}
				continue;
			}

			reportError("Unknown PORT keyword (" + key + ")", cell);
			return true;
		}

		if (!PLACEGEOMETRY) return false;

		// look for paths that end at vias
		for(LEFPath lp = lefPaths; lp != null; lp = lp.nextLEFPath)
		{
			for(int i=0; i<2; i++)
			{
				if (lp.ni[i] != null) continue;
				Rectangle2D bounds = new Rectangle2D.Double(lp.pt[i].getX(), lp.pt[i].getY(), 0, 0);
				for(Iterator<Geometric> sea = cell.searchIterator(bounds); sea.hasNext(); )
				{
					Geometric geom = sea.next();
					if (!(geom instanceof NodeInst)) continue;
					NodeInst ni = (NodeInst)geom;
					if (!DBMath.areEquals(ni.getTrueCenter(), lp.pt[i])) continue;
					lp.ni[i] = ni;
					break;
				}
				if (lp.ni[i] == null) continue;

				// use this via at other paths which meet here
				for(LEFPath oLp = lefPaths; oLp != null; oLp = oLp.nextLEFPath)
				{
					for(int j=0; j<2; j++)
					{
						if (oLp.ni[j] != null) continue;
						if (!DBMath.areEquals(oLp.pt[j], lp.pt[i])) continue;
						oLp.ni[j] = lp.ni[i];
					}
				}
			}
		}

		// create pins at all other path ends
		for(LEFPath lp = lefPaths; lp != null; lp = lp.nextLEFPath)
		{
			for(int i=0; i<2; i++)
			{
				if (lp.ni[i] != null) continue;
				pinMade.setValue(true);
				NodeProto pin = lp.arc.findPinProto();
				if (pin == null) continue;
				double sX = pin.getDefWidth(ep);
				double sY = pin.getDefHeight(ep);
				lp.ni[i] = NodeInst.makeInstance(pin, ep, lp.pt[i], sX, sY, cell);
				if (lp.ni[i] == null)
				{
					reportError("Cannot create pin for PATH", cell);
					return true;
				}

				if (first)
				{
					// create the port on the first pin
					first = false;
					Export pp = newPort(cell, lp.ni[i], pin.getPort(0), portname);
					if (pp != null) pp.setCharacteristic(portCharacteristics);
				}

				// use this pin at other paths which meet here
				for(LEFPath oLp = lefPaths; oLp != null; oLp = oLp.nextLEFPath)
				{
					for(int j=0; j<2; j++)
					{
						if (oLp.ni[j] != null) continue;
						if (!DBMath.areEquals(oLp.pt[j], lp.pt[i])) continue;
						if (oLp.arc != lp.arc) continue; 
						oLp.ni[j] = lp.ni[i];
					}
				}
			}
		}

		// now instantiate the paths
		for(LEFPath lp = lefPaths; lp != null; lp = lp.nextLEFPath)
		{
			PortInst head = lp.ni[0].getPortInst(0);
			PortInst tail = lp.ni[1].getPortInst(0);
			Point2D headPt = lp.pt[0];
			Point2D tailPt = lp.pt[1];
			ArcInst ai = ArcInst.makeInstanceBase(lp.arc, ep, lp.width, head, tail, headPt, tailPt, null);
			if (ai == null)
			{
				reportError("Cannot create " + lp.arc.describe() + " arc for PATH", cell);
				return true;
			}
		}

		if (lefPaths == null && singlePathPoint != null && ap != null && first)
		{
			// path was a single point: plot it
			NodeProto pin = ap.findPinProto();
			if (pin != null)
			{
				pinMade.setValue(true);
				double sX = pin.getDefWidth(ep);
				double sY = pin.getDefHeight(ep);
				NodeInst ni = NodeInst.makeInstance(pin, ep, singlePathPoint, sX, sY, cell);
				if (ni == null)
				{
					reportError("Cannot create pin for PATH", cell);
					return true;
				}

				// create the port on the pin
				Export pp = newPort(cell, ni, pin.getPort(0), portname);
				if (pp != null) pp.setCharacteristic(portCharacteristics);
			}
		}
		return false;
	}

	/**
	 * Method to create an Export.
	 * @param cell the cell in which to create the export.
	 * @param ni the NodeInst to export.
	 * @param pp the PortProto on the NodeInst to export.
	 * @param thename the name of the export.
	 * @return the new Export.
	 * The name is modified if it already exists.
	 */
	private Export newPort(Cell cell, NodeInst ni, PortProto pp, String thename)
	{
		String portName = thename;
		String newName = null;
		for(int i=0; ; i++)
		{
			Export e = (Export)cell.findPortProto(portName);
			if (e == null)
			{
				PortInst pi = ni.findPortInstFromProto(pp);
				Export ex = Export.newInstance(cell, pi, portName, ep);
				return ex;
			}

			// make space for modified name
			int sqPos = thename.indexOf('[');
			if (sqPos < 0) newName = thename + "-" + i; else
				newName = thename.substring(0, sqPos) + "-" + i + thename.substring(sqPos);
			portName = newName;
		}
	}

	private boolean readLayer()
		throws IOException
	{
		String layerName = getAKeyword();
		if (layerName == null)
		{
			reportError("EOF parsing LAYER header", null);
			return true;
		}

		String layerType = null;
		double defWidth = -1;
		for(;;)
		{
			// get the next keyword
			String key = getAKeyword();
			if (key == null)
			{
				reportError("EOF parsing LAYER", null);
				return true;
			}

			if (key.equalsIgnoreCase("END"))
			{
				key = getAKeyword();
				break;
			}

			if (key.equalsIgnoreCase("WIDTH"))
			{
				key = getAKeyword();
				if (key == null)
				{
					reportError("EOF reading WIDTH", null);
					return true;
				}
				defWidth = convertLEFString(key);
				if (ignoreToSemicolon("WIDTH", null)) return true;
				continue;
			}

			if (key.equalsIgnoreCase("TYPE"))
			{
				layerType = getAKeyword();
				if (ignoreToSemicolon("TYPE", null)) return true;
				continue;
			}

			if (key.equalsIgnoreCase("SPACING") || key.equalsIgnoreCase("PITCH") ||
				key.equalsIgnoreCase("DIRECTION") || key.equalsIgnoreCase("CAPACITANCE") ||
				key.equalsIgnoreCase("RESISTANCE"))
			{
				if (ignoreToSemicolon(key, null)) return true;
				continue;
			}
		}

		GetLayerInformation li = new GetLayerInformation(layerName, null, layerType);
		knownLayers.put(layerName, li);
		ArcProto ap = li.arc;
		if (defWidth > 0)
		{
			if (ap != null) widthsFromLEF.put(ap, new Double(defWidth));
			else 
			{ 
				if (layerWidthsFromLEF == null)
					layerWidthsFromLEF = new HashMap<String,Double>();
				layerWidthsFromLEF.put(layerName, new Double(defWidth));
			}
		}
		return false;
	}

	private List<Point2D> readPolygon(Cell cell)
		throws IOException
	{
		// gather the points in the polygon
		List<Point2D> points = new ArrayList<Point2D>();
		for(;;)
		{
			String key = getAKeyword();
			if (key == null)
			{
				reportError("EOF reading POLYGON X coordinate", cell);
				return null;
			}
			if (key.equals(";")) break;
			if (points.size() == 0 && key.equalsIgnoreCase("ITERATE")) continue;
			double x = convertLEFString(key);

			key = getAKeyword();
			if (key == null)
			{
				reportError("EOF reading POLYGON Y coordinate", cell);
				return null;
			}
			if (key.equals(";")) break;
			double y = convertLEFString(key);
			points.add(new Point2D.Double(x, y));
		}
		return points;
	}

	private boolean ignoreToSemicolon(String command, Cell cell)
		throws IOException
	{
		// ignore up to the next semicolon
		for(;;)
		{
			String key = getAKeyword();
			if (key == null)
			{
				reportError("EOF parsing " + command, cell);
				return true;
			}
			if (key.equals(";")) break;
		}
		return false;
	}

	private boolean ignoreToEnd(String endName, Cell cell)
		throws IOException
	{
		// ignore up to "END endName"
		boolean findEnd = true;
		for(;;)
		{
			String key = getAKeyword();
			if (key == null)
			{
				reportError("EOF parsing " + endName, cell);
				return true;
			}
			if (findEnd && key.equalsIgnoreCase("END"))
			{
				key = getAKeyword();
				if (key == null)
				{
					reportError("EOF parsing " + endName, cell);
					return true;
				}
				if (key.equals(endName)) break;
				continue;
			}
			if (key.equals(";")) findEnd = true; else findEnd = false;
		}
		return false;
	}

	private double convertLEFString(String key)
	{
		double v = TextUtils.atof(key) * localPrefs.overallscale;
		return TextUtils.convertFromDistance(v, curTech, TextUtils.UnitScale.MICRO);
	}
	
	// reading LEF tech file here to reuse functionality
	/**
	 * Method to import a library from disk.
	 * @return true if successful, false on error.
	 */
	protected boolean importTechFile()
	{
		// remove any vias in the globals
    	initializeLEFDEF(null);
    	layerWidthsFromLEF = new HashMap<String,Double>();
		initKeywordParsing();

		try
		{
            if (readTechnologyFile()) return false; // error during reading
        } catch (IOException e)
		{
        	reportError("ERROR reading LEF tech file", null);
			return false;
		}
		return true;
	}
    
	private boolean readTechnologyFile() throws IOException
	{
		for(;;)
		{
			// get the next keyword
			String key = getAKeyword();
			if (key == null) break;
			if (key.equalsIgnoreCase("LAYER"))
			{
				if (readLayer()) return true;
				continue;
			}
//			if (key.equalsIgnoreCase("VIA"))
//			{
//				ignoreToEnd(key);
//				continue;
//			}
			if (key.equalsIgnoreCase("VIA") || key.equalsIgnoreCase("VIARULE") || key.equalsIgnoreCase("SITE") ||
				key.equalsIgnoreCase("ARRAY"))
			{
				String name = getAKeyword();
				ignoreToEnd(name, null);
				continue;
			}
			// header information in tech file
			if (key.equalsIgnoreCase("VERSION") || key.equalsIgnoreCase("NAMESCASESENSITIVE") ||
				key.equalsIgnoreCase("BUSBITCHARS") || 
				key.equalsIgnoreCase("DIVIDERCHAR") || key.equalsIgnoreCase("MANUFACTURINGGRID"))
			{
				ignoreToSemicolon(key, null);
				continue;
			}
			if (key.equalsIgnoreCase("UNITS"))
			{
				ignoreToEnd(key, null);
				continue;
			}
		}
		return false;
	}
}
