/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: GDS.java
 * Input/output tool: GDS output
 * Original C Code written by Sid Penstone, Queens University
 * Translated to Java by Steven M. Rubin.
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
import com.sun.electric.database.hierarchy.Export;
import com.sun.electric.database.hierarchy.Nodable;
import com.sun.electric.database.hierarchy.View;
import com.sun.electric.database.prototype.PortCharacteristic;
import com.sun.electric.database.prototype.PortProto;
import com.sun.electric.database.topology.ArcInst;
import com.sun.electric.database.topology.Geometric;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.variable.UserInterface;
import com.sun.electric.database.variable.VarContext;
import com.sun.electric.database.variable.Variable;
import com.sun.electric.database.variable.Variable.Key;
import com.sun.electric.technology.ArcProto;
import com.sun.electric.technology.GDSLayers;
import com.sun.electric.technology.GDSLayers.GDSLayerType;
import com.sun.electric.technology.Layer;
import com.sun.electric.technology.PrimitiveNode;
import com.sun.electric.technology.Technology;
import com.sun.electric.technology.technologies.Artwork;
import com.sun.electric.technology.technologies.Generic;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.io.GDSReader;
import com.sun.electric.tool.io.IOTool;
import com.sun.electric.tool.io.input.Input;
import com.sun.electric.tool.ncc.basic.NccCellAnnotations;
import com.sun.electric.tool.user.ui.LayerVisibility;
import com.sun.electric.util.TextUtils;
import com.sun.electric.util.math.DBMath;
import com.sun.electric.util.math.FixpTransform;

import java.awt.Point;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * This class writes files in GDS format.
 */
public class GDS extends Geometry
{
	private static final boolean NEWUNITS = true;

	/** key of Variable with high voltage text. */		public static final Variable.Key GDS_TEXT_HV_KEY = Variable.newKey("ATTR_GDS_text_HV");
	/** key of Variable with GDS text text. */			public static final Variable.Key GDS_TEXT_KEY = Variable.newKey("ATTR_GDS_text");

	/** old key of Variable with high voltage text. */	public static final Variable.Key OLD_HIGH_VOLTAGE_KEY = Variable.newKey("ATTR_high_voltage");
	/** old key of Variable with GDS text text. */		public static final Variable.Key OLD_GDS_TEXT_KEY = Variable.newKey("GDS_text");

	private static final int GDSVERSION        =      3;
	private static final int BYTEMASK          =   0xFF;
	private static final int DSIZE             =    512;		/* data block */
	private static final int EXPORTPRESENTATION=      0;		/* centered (was 8 for bottom-left) */

	// GDSII bit assignments in STRANS record
	private static final int STRANS_REFLX      = 0x8000;
//	private static final int STRANS_ABSA       =    0x2;

	// data type codes
	private static final int DTYP_NONE         =      0;

	// header codes
	private static final short HDR_HEADER      = 0x0002;
	private static final short HDR_BGNLIB      = 0x0102;
	private static final short HDR_LIBNAME     = 0x0206;
	private static final short HDR_UNITS       = 0x0305;
	private static final short HDR_ENDLIB      = 0x0400;
	private static final short HDR_BGNSTR      = 0x0502;
	private static final short HDR_STRNAME     = 0x0606;
	private static final short HDR_ENDSTR      = 0x0700;
	private static final short HDR_BOUNDARY    = 0x0800;
	private static final short HDR_PATH        = 0x0900;
	private static final short HDR_SREF        = 0x0A00;
//	private static final short HDR_AREF        = 0x0B00;
	private static final short HDR_TEXT        = 0x0C00;
	private static final short HDR_LAYER       = 0x0D02;
	private static final short HDR_DATATYPE    = 0x0E02;
	private static final short HDR_XY          = 0x1003;
	private static final short HDR_ENDEL       = 0x1100;
	private static final short HDR_SNAME       = 0x1206;
	private static final short HDR_TEXTTYPE    = 0x1602;
	private static final short HDR_PRESENTATION= 0x1701;
	private static final short HDR_STRING	   = 0x1906;
	private static final short HDR_STRANS      = 0x1A01;
	private static final short HDR_MAG         = 0x1B05;
	private static final short HDR_ANGLE       = 0x1C05;
	private static final short HDR_PROPATTR    = 0x2B02;
	private static final short HDR_PROPVALUE   = 0x2C06;

	// Header byte counts
	private static final short HDR_N_BGNLIB    =     28;
	private static final short HDR_N_UNITS     =     20;
	private static final short HDR_N_ANGLE     =     12;
	private static final short HDR_N_MAG       =     12;

	// Maximum string sizes
	private static final int HDR_M_ASCII       =    256;

	/** for buffering output data */			private static byte [] dataBufferGDS = new byte[DSIZE];
	/** for buffering output data */			private static byte [] emptyBuffer = new byte[DSIZE];
	/** Current layer for gds output */			private static GDSLayers currentLayerNumbers;
	/** Position of next byte in the buffer */	private static int bufferPosition;
	/** Number data buffers output so far */	private static int blockCount;
	/** constant for GDS units */				private double scaleFactor;
	/** true if rounding caused inaccuracies */	private int inaccurate;
	/** cell naming map */						private Map<Cell,String> cellNames;
	/** cells that have been written */			private Set<Cell> writtenCells;
	/** cell names that have been written */	private Set<String> writtenCellNames;
	/** layer number map */						private Map<Layer,GDSLayers> layerNumbers;
	/** separator string for lib + cell concatenated cell names */  public static final String concatStr = ".";
	/** Name remapping if NCC annotation */		private Map<String,Set<String>> nameRemapping;
	private GDSPreferences localPrefs;

	public static class GDSPreferences extends OutputPreferences
	{
		// GDS Settings
		/** write pins at Export locations? */
		public boolean writeExportPins = IOTool.isGDSOutWritesExportPins();
		/** converts bracket to underscores in export names.*/
		public boolean convertBracketsInExports = IOTool.getGDSOutputConvertsBracketsInExports();
		public boolean collapseVddGndPinNames = IOTool.isGDSOutColapseVddGndPinNames();
		public boolean writeExportCharacteristics = IOTool.isGDSOutWriteExportCharacteristicsSetting();
		int outDefaultTextLayer = IOTool.getGDSDefaultTextLayer();
		boolean outMergesBoxes = IOTool.isGDSOutMergesBoxes();
		public int cellNameLenMax = IOTool.getGDSCellNameLenMax();
		public boolean outUpperCase = IOTool.isGDSOutUpperCase();
        
        boolean includeText;
		boolean convertNCCExportsConnectedByParentPins;
        boolean writeAllCells;
        public boolean flatDesign;
        boolean onlyVisibleLayers;
        boolean[] visibility;
        double precision, unitsPerMeter;

        public GDSPreferences(boolean factory, Cell cell)
		{
			super(factory);
			if (factory) 
			{
				writeAllCells = IOTool.isFactoryGDSWritesEntireLibrary();
				flatDesign = IOTool.isFactoryGDSFlatDesign();
				includeText = IOTool.isFactoryGDSIncludesText();
				precision = IOTool.getFactoryGDSOutputPrecision();
				unitsPerMeter = IOTool.getFactoryGDSOutputUnitsPerMeter();
				convertNCCExportsConnectedByParentPins = IOTool.getFactoryGDSConvertNCCExportsConnectedByParentPins();
				onlyVisibleLayers = IOTool.isFactoryGDSOnlyInvisibleLayers();
			} else
			{
				writeAllCells = IOTool.isGDSWritesEntireLibrary();
				flatDesign = IOTool.isGDSFlatDesign();
				includeText = IOTool.isGDSIncludesText();
				precision = IOTool.getGDSOutputPrecision();
				unitsPerMeter = IOTool.getGDSOutputUnitsPerMeter();
				convertNCCExportsConnectedByParentPins = IOTool.getGDSConvertNCCExportsConnectedByParentPins();
				onlyVisibleLayers = IOTool.isGDSOnlyInvisibleLayers();
			}
			if (onlyVisibleLayers && cell != null)
				visibility = LayerVisibility.getLayerVisibility().getTechDataArray()[cell.getTechnology().getId().techIndex];
		}

		@Override
		public Output doOutput(Cell cell, VarContext context, String filePath)
		{
			if (cell.getView() != View.LAYOUT && cell.getView() != View.LAYOUTSKEL && cell.getView() != View.LAYOUTCOMP)
			{
				System.out.println("Can only write GDS for layout cells");
				return null;
			}
			
			GDS out = new GDS(this);
			if (out.openBinaryOutputStream(filePath)) return null;
			out.writtenCells = new HashSet<Cell>();
			out.writtenCellNames = new HashSet<String>();
			if (flatDesign)
			{
				// separate code for flattening hierarchy
				out.topCell = cell;
				out.start();
				out.writtenCells.add(cell);
				out.outputBeginStruct(cell);
				Set<String> exportsUsed = new HashSet<String>();
				out.writeRecursively(cell, DBMath.MATID, exportsUsed);
				out.outputHeader(HDR_ENDSTR, 0);
			} else
			{
				BloatVisitor visitor = out.makeBloatVisitor(getMaxHierDepth(cell));
				if (out.writeCell(cell, context, visitor)) return null;

				if (writeAllCells)
				{
					for(Iterator<Cell> it = cell.getLibrary().getCells(); it.hasNext(); )
					{
						Cell c = it.next();
						if (c.getView() == View.ICON || c.getView() == View.SCHEMATIC || c.getView().isTextView()) continue;
						if (out.writtenCells.contains(c)) continue;
						CellGeom cellGeom = new CellGeom(c, null);
						cellGeom.addNodesAndArcs();
						out.writeCellGeom(cellGeom);
					}
				}
			}
			out.outputHeader(HDR_ENDLIB, 0);
			out.doneWritingOutput();
			if (out.closeBinaryOutputStream()) return null;
			System.out.println(filePath + " written");

			// warn if library name was changed
			String topCellName = cell.getName();
			String mangledTopCellName = makeGDSName(topCellName, HDR_M_ASCII, outUpperCase);
			if (!topCellName.equals(mangledTopCellName))
				out.reportWarning("Warning: library name in this file is " + mangledTopCellName +
					" (special characters were changed)");
			return out.finishWrite();
	   }
	}

	private GDS(GDSPreferences gp)
	{
		localPrefs = gp;
	}

	protected void start()
	{
		initOutput();
		outputBeginLibrary(topCell);
	}

	protected void done()
	{
		if (inaccurate > 0)
		{
			String msg = "WARNING: GDS Export encountered problems because of small feature sizes and coarse accuracy settings.";
			msg += " It is recommended that the 'Units/meter' GDS preference be increased by a factor of " + inaccurate;
			Job.getUserInterface().showInformationMessage(msg, "Potential GDS Export Problem");
		}
	}

	/** Method to write cellGeom */
	protected void writeCellGeom(CellGeom cellGeom)
	{
		// write this cell
		Cell cell = cellGeom.cell;

		// if this is a skeleton cell, include the original GDS
		if (cell.getView() == View.LAYOUTSKEL)
		{
			Variable var = cell.getVar(com.sun.electric.tool.io.input.GDS.SKELETON_ORIGIN);
			if (var != null)
			{
				String fileName = (String)var.getObject();
				File fullGDSFile = new File(fileName);
				if (fullGDSFile.exists())
				{
					emitFullGDS(cell, fileName);
					return;
				} else
				{
					System.out.println("Warning: Original GDS for cell " + cell.describe(false) + " (file " + fileName + ") is missing...using cell contents");
				}
			}
		}

		writtenCells.add(cell);
		if (outputBeginStruct(cell)) return;
		boolean renamePins = (cell == topCell && localPrefs.convertNCCExportsConnectedByParentPins);
		boolean colapseGndVddNames = (cell == topCell && localPrefs.collapseVddGndPinNames);

		if (renamePins)
		{
			// rename pins to allow external LVS programs to virtually connect nets as specified
			// by the NCC annotation exportsConnectedByParent
			NccCellAnnotations annotations = NccCellAnnotations.getAnnotations(cell);
			if (annotations == null)
				renamePins = false;
			else
				nameRemapping = createExportNameMap(annotations, cell);
		}

		// write all polys by Layer
		Set<Layer> layers = cellGeom.polyMap.keySet();
		for (Layer layer : layers)
		{
			// No technology associated, case when art elements are added in layout
			// layer.getTechnology() == Generic.tech for layer Glyph
			if (skipLayer(layer, false)) continue;
			if (!selectLayer(layer))
			{
				System.out.println("Skipping " + layer + " in GDS output");
				continue;
			}
			List<Object> polyList = cellGeom.polyMap.get(layer);
			for (Object obj : polyList)
			{
				PolyBase poly = (PolyBase)obj;
				int layerNum = currentLayerNumbers.getLayerNumber(GDSLayerType.DRAWING);
				int layerType = currentLayerNumbers.getLayerType(GDSLayerType.DRAWING);
				writePoly(poly, layerNum, layerType);
			}
		}

		// write all instances
		for (Nodable no : cellGeom.nodables)
		{
			writeNodable(no);
		}

		// now write exports
		if (localPrefs.outDefaultTextLayer >= 0 && localPrefs.writeExportPins)
		{
			for(Iterator<PortProto> it = cell.getPorts(); it.hasNext(); )
			{
				Export pp = (Export)it.next();

//				// find the node at the bottom of this export
//				PortOriginal fp = new PortOriginal(pp.getOriginalPort());
//				PortInst bottomPort = fp.getBottomPort();
//				NodeInst bottomNi = bottomPort.getNodeInst();
//
//				// find the layer associated with this node
//				PrimitiveNode pNp = (PrimitiveNode)bottomNi.getProto();
//				Technology.NodeLayer [] nLay = pNp.getNodeLayers();
//				Layer layer = nLay[0].getLayer();
//
//				// Skipping the export if main layer is not visible
//				if (skipLayer(layer, false)) continue;
//				
//				selectLayer(layer);

				// write out the pin
				Poly portPoly = pp.getPoly();
				String portName = pp.getName();
				if (renamePins)
				{
					Set<String> nameSet = nameRemapping.get(portName);
					if (nameSet != null)
					{
						portName = nameSet.iterator().next();
						portName = portName + ":" + portName;
					}
				}
				if (localPrefs.convertBracketsInExports)
				{
					// convert brackets to underscores
					portName = portName.replaceAll("[\\[\\]]", "_");
				}
				if (colapseGndVddNames)
				{
					String tmp = portName.toLowerCase();
					// Detecting string in lower case and later search for "_"
					if ((tmp.startsWith("vdd_") || tmp.startsWith("gnd_")) && TextUtils.isANumber(tmp.substring(4)))
						portName = portName.substring(0, portName.indexOf("_"));
				}
//				writeExport(pp, portName, portPoly.getCenterX(), portPoly.getCenterY());
				for(ArcProto ap : pp.getBasePort().getConnections())
				{
					for(Technology.ArcLayer al : ap.getArcLayers())
					{
						Layer layer = al.getLayer();
						// skip the export if main layer is not visible
						if (skipLayer(layer, false)) continue;
						selectLayer(layer);
                        writeExport(pp, portName, portPoly.getCenterX(), portPoly.getCenterY());
					}
				}
			}
		}

        if (localPrefs.includeText) {
            for (Iterator<NodeInst> it = cell.getNodes(); it.hasNext(); ) {
                NodeInst ni = it.next();
                
                if (!isTextNode(ni)) continue;
                
                // this is a text annotation
                PrimitiveNode pNp = (PrimitiveNode)ni.getProto();
                Technology.NodeLayer [] nLay = pNp.getNodeLayers();
                Layer layer = nLay[0].getLayer();
                if (skipLayer(layer, true)) continue;
//                boolean selectedLayer = selectLayer(layer);

                int layerNum = -1, layerType = -1;
                if (!selectLayer(layer))
                {
                	if (localPrefs.outDefaultTextLayer == 0) // 0 -> equivalent to "" in String
                	{
                		System.out.println("Skipping " + layer + " in GDS output");
                		continue;
                	}
                	layerNum = localPrefs.outDefaultTextLayer;
                	layerType = 0;
                }
                else
                {
	                if (currentLayerNumbers.hasLayerType(GDSLayerType.TEXT))
	                {
	                	layerNum = currentLayerNumbers.getLayerNumber(GDSLayerType.TEXT);
	                	layerType = currentLayerNumbers.getLayerType(GDSLayerType.TEXT);
	                	System.out.println("Using real text type! for layer '" + layer.getName() + "'");
	                }
	                else
	                {
						layerNum = currentLayerNumbers.getLayerNumber(GDSLayerType.DRAWING);
						layerType = currentLayerNumbers.getLayerType(GDSLayerType.DRAWING);
	                }
                }
                // check if there is a variable ART_MESSAGE with containing the text.
                Variable var = ni.getVar(Artwork.ART_MESSAGE);
                String name = ni.getName();
                
                if (var != null)
                {
                	Object obj = var.getObject();
                	name = obj.toString();
                }
                writeTextOnLayer(name, layerNum, layerType, ni.getAnchorCenterX(), ni.getAnchorCenterY(),
                        renamePins, colapseGndVddNames);
            }
        }
        
        // include voltage for nodes and arcs
        for (Iterator<NodeInst> it = cell.getNodes(); it.hasNext(); ) {
            NodeInst ni = it.next();
            writeSpecialText(ni, ni.getAnchorCenterX(), ni.getAnchorCenterY(), GDS_TEXT_HV_KEY, OLD_HIGH_VOLTAGE_KEY, GDSLayerType.HIGHVOLTAGE);
            writeSpecialText(ni, ni.getAnchorCenterX(), ni.getAnchorCenterY(), GDS_TEXT_KEY, OLD_GDS_TEXT_KEY, GDSLayerType.TEXT);
        }
        for (Iterator<ArcInst> it = cell.getArcs(); it.hasNext(); ) {
            ArcInst ai = it.next();
            writeSpecialText(ai, ai.getTrueCenterX(), ai.getTrueCenterY(), GDS_TEXT_HV_KEY, OLD_HIGH_VOLTAGE_KEY, GDSLayerType.HIGHVOLTAGE);
            writeSpecialText(ai, ai.getTrueCenterX(), ai.getTrueCenterY(), GDS_TEXT_KEY, OLD_GDS_TEXT_KEY, GDSLayerType.TEXT);
        }
        outputHeader(HDR_ENDSTR, 0);
	}

	private void writeSpecialText(Geometric geom, double x, double y, Key key, Key keyOld, GDSLayerType type)
	{
        Variable var = geom.getVar(key);
        if (var == null) var = geom.getVar(keyOld);
        if (var != null)
        {
        	List<Layer> layers = new ArrayList<Layer>();
        	if (geom instanceof NodeInst)
        	{
                PrimitiveNode pNp = (PrimitiveNode)((NodeInst)geom).getProto();
                Technology.NodeLayer [] nLay = pNp.getNodeLayers();
                for (Technology.NodeLayer nl : nLay)
                	layers.add(nl.getLayer());
        	} else
        	{
	        	ArcProto ap = ((ArcInst)geom).getProto();
                Technology.ArcLayer [] nLay = ap.getArcLayers();
                for (Technology.ArcLayer nl : nLay)
                	layers.add(nl.getLayer());
        	}
            for (Layer layer : layers)
            {
                if (skipLayer(layer, false)) continue;
                
                if (!selectLayer(layer))
                {
                    System.out.println("Skipping " + layer + " in GDS output");
                    continue;
                }
                int layerNum = -1, layerType = -1;
	            if (currentLayerNumbers.hasLayerType(type))
	            {
	            	layerNum = currentLayerNumbers.getLayerNumber(type);
	            	layerType = currentLayerNumbers.getLayerType(type);
	            } else
	            {
	            	System.out.println("Skipping voltage variable " + layer + " in GDS output");
	            	continue;
	            }
	            writeTextOnLayer(var.getPureValue(0), layerNum, layerType, x, y, false, false);
            }
        }
	}

	/**
	 * Method for writing a cell in "flat" style: recursively dumping all geometry
	 * @param cell the cell in the hierarchy being written.
	 * @param trans the transformation to this cell.
	 */
	private void writeRecursively(Cell cell, FixpTransform trans, Set<String> exportsUsed)
	{
		for(Iterator<NodeInst> it = cell.getNodes(); it.hasNext(); )
		{
			NodeInst ni = it.next();
			FixpTransform subRot = ni.rotateOut();
			subRot.preConcatenate(trans);
			if (ni.isCellInstance())
			{
				Cell subCell = (Cell)ni.getProto();
				FixpTransform subTrans = ni.translateOut();
				subTrans.preConcatenate(subRot);
				writeRecursively(subCell, subTrans, exportsUsed);
			} else
			{
				Technology tech = ni.getProto().getTechnology();
				Poly[] polys = tech.getShapeOfNode(ni);
				for(int i=0; i<polys.length; i++)
				{
					Poly poly = polys[i];
					poly.transform(subRot);
					Layer layer = poly.getLayer();
					if (skipLayer(layer, false)) continue;
					if (selectLayer(layer))
					{
						int layerNum = currentLayerNumbers.getLayerNumber(GDSLayerType.DRAWING);
						int layerType = currentLayerNumbers.getLayerType(GDSLayerType.DRAWING);
						writePoly(poly, layerNum, layerType);
					}
				}

				Point2D pt = new Point2D.Double(0, 0);
				trans.transform(ni.getAnchorCenter(), pt);
				writeSpecialText(ni, pt.getX(), pt.getY(), GDS_TEXT_HV_KEY, OLD_HIGH_VOLTAGE_KEY, GDSLayerType.HIGHVOLTAGE);
				writeSpecialText(ni, pt.getX(), pt.getY(), GDS_TEXT_KEY, OLD_GDS_TEXT_KEY, GDSLayerType.TEXT);
			}
		}
		for(Iterator<ArcInst> it = cell.getArcs(); it.hasNext(); )
		{
			ArcInst ai = it.next();
			Technology tech = ai.getProto().getTechnology();
			Poly[] polys = tech.getShapeOfArc(ai);
			for(int i=0; i<polys.length; i++)
			{
				Poly poly = polys[i];
				poly.transform(trans);
				Layer layer = poly.getLayer();
				if (skipLayer(layer, false)) continue;
				if (selectLayer(layer))
				{
					int layerNum = currentLayerNumbers.getLayerNumber(GDSLayerType.DRAWING);
					int layerType = currentLayerNumbers.getLayerType(GDSLayerType.DRAWING);
					writePoly(poly, layerNum, layerType);
				}
			}

			Point2D pt = new Point2D.Double(0, 0);
			trans.transform(ai.getTrueCenter(), pt);
			writeSpecialText(ai, pt.getX(), pt.getY(), GDS_TEXT_HV_KEY, OLD_HIGH_VOLTAGE_KEY, GDSLayerType.HIGHVOLTAGE);
			writeSpecialText(ai, pt.getX(), pt.getY(), GDS_TEXT_KEY, OLD_GDS_TEXT_KEY, GDSLayerType.TEXT);
		}

		// write exports
		if (localPrefs.outDefaultTextLayer >= 0 && localPrefs.writeExportPins)
		{
			for(Iterator<Export> eIt = cell.getExports(); eIt.hasNext(); )
			{
				Export pp = eIt.next();

//				// find the node at the bottom of this export
//				PortOriginal fp = new PortOriginal(pp.getOriginalPort());
//				PortInst bottomPort = fp.getBottomPort();
//				NodeInst bottomNi = bottomPort.getNodeInst();
//
//				// find the layer associated with this node
//				PrimitiveNode pNp = (PrimitiveNode)bottomNi.getProto();
//				Technology.NodeLayer [] nLay = pNp.getNodeLayers();
//				Layer layer = nLay[0].getLayer();
//				
//				// skip the export if main layer in not visible
//				if (skipLayer(layer, false)) continue;
//				
//				selectLayer(layer);

				// write out the pin
				Poly portPoly = pp.getPoly();
				portPoly.transform(trans);
				String portName = pp.getName();
				if (localPrefs.convertBracketsInExports)
				{
					// convert brackets to underscores
					portName = portName.replaceAll("[\\[\\]]", "_");
				}
				int suffixNumber = 0;
				String fullPortName = portName;
				for(;;)
				{
					if (suffixNumber != 0) fullPortName = portName + "_" + suffixNumber;
					if (!exportsUsed.contains(fullPortName)) break;
					suffixNumber++;
				}
				exportsUsed.add(fullPortName);
//				writeExport(pp, fullPortName, portPoly.getCenterX(), portPoly.getCenterY());

				// find the layer associated with this node
				for(ArcProto ap : pp.getBasePort().getConnections())
				{
					for(Technology.ArcLayer al : ap.getArcLayers())
					{
						Layer layer = al.getLayer();
						// skip the export if main layer in not visible
						if (skipLayer(layer, false)) continue;
						selectLayer(layer);
						writeExport(pp, fullPortName, portPoly.getCenterX(), portPoly.getCenterY());
					}
				}
			}
		}
	}

	/**
	 * Method to determine whether a layer should be skipped and not to be included in the GDS output
	 * @param allowGeneric TODO
	 */
	private boolean skipLayer(Layer layer, boolean allowGeneric)
	{
		// No technology associated, case when art elements are added in layout
		// layer.getTechnology() == Generic.tech for layer Glyph
		if (layer == null || layer.getTechnology() == null || 
				layer.getTechnology() == Generic.tech() && !allowGeneric ||
				layer.getTechnology() == Artwork.tech()) // ignore artwork elements
			return true;
		
		// check visibility. localPrefs.visibility=null -> no associated cell when preferences was created
		if (!localPrefs.onlyVisibleLayers || localPrefs.visibility == null)
			return false; // always visible
		return (!localPrefs.visibility[layer.getIndex()]);
	}

	/**
	 * Method to write an "included" GDS file into the GDS output to replace a Cell's actual structure.
	 * @param cell the Cell being replaced.
	 * @param fullGDSFile the path to the GDS file.
	 */
	private void emitFullGDS(Cell cell, String fullGDSFile)
	{
		String oldNote = null;
		UserInterface ui = Job.getUserInterface();
		if (ui != null)
		{
			oldNote = ui.getProgressNote();
			ui.setProgressNote("Copying included GDS for cell " + cell.describe(false));
		}

		System.out.println("Replacing cell " + cell.describe(false) + " with original GDS data in file " + fullGDSFile);
		InputStream inputStream = null;
		try
		{
			URL fileURL = TextUtils.makeURLToFile(fullGDSFile);
			URLConnection urlCon = fileURL.openConnection();
			String contentLength = urlCon.getHeaderField("content-length");
			long fileLength = -1;
			try {
				fileLength = Long.parseLong(contentLength);
			} catch (Exception e) {}
			inputStream = urlCon.getInputStream();

			BufferedInputStream bufStrm = new BufferedInputStream(inputStream, Input.READ_BUFFER_SIZE);
			DataInputStream dataInputStream = new DataInputStream(bufStrm);
			GDSReader gdsRead = new GDSReader(fullGDSFile, dataInputStream, fileLength);

			// copy stream to the output (when restoring skeleton cells)
			for(;;)
			{
				gdsRead.getToken();
				if (gdsRead.getTokenType() == GDSReader.GDS_ENDLIB) break;
				if (gdsRead.getTokenType() == GDSReader.GDS_BGNSTR)
				{
					short[] bgnstrData = new short[12];
					for(int i=0; i<12; i++)
					{
						gdsRead.getToken();
						bgnstrData[i] = (short)gdsRead.getShortValue();
					}

					gdsRead.getToken();
					if (gdsRead.getTokenType() != GDSReader.GDS_STRNAME)
					{
						System.out.println("Begin structure statement is missing");
						break;
					}
					gdsRead.getToken();
					if (gdsRead.getTokenType() != GDSReader.GDS_IDENT)
					{
						System.out.println("Structure name is missing");
						break;
					}
					String name = gdsRead.getStringValue();
					boolean copyIt = !writtenCellNames.contains(name);
					if (copyIt)
					{
						writtenCellNames.add(name);
						System.out.println("    Copying cell " + name);
						outputHeader(HDR_BGNSTR, 0);
						for(int i=0; i<12; i++) outputShort(bgnstrData[i]);
						outputName(HDR_STRNAME, name, localPrefs.cellNameLenMax);
					} else
					{
						System.out.println("    Skipping cell " + name);
					}
					
					// read to the end of the cell
					for(;;)
					{
						gdsRead.getToken();
						if (copyIt)
						{
							outputShort(gdsRead.getLastDataWord());
							outputByte(gdsRead.getLastRecordType());
							outputByte(gdsRead.getLastDataType());
						}
						if (gdsRead.getTokenType() == GDSReader.GDS_ENDSTR) break;
						while (gdsRead.getRemainingDataCount() > 0)
						{
							byte b = gdsRead.getByte();
							if (copyIt) outputByte(b);
						}					
					}
					continue;
				}
			}
		} catch (Exception e)
		{
            System.out.println("ERROR reading included GDS file " + fullGDSFile + " for cell " + cell.describe(false) + ": " + e.getMessage());
			return;
		}
		if (inputStream != null) { try { inputStream.close(); } catch (IOException e) {} }
		if (ui != null) ui.setProgressNote(oldNote);
	}

	private boolean isTextNode(NodeInst ni)
	{
        if (!(ni.getProto() instanceof PrimitiveNode)) return false;
        if (ni.getXSize() != 0 || ni.getYSize() != 0) return false;
        if (!ni.getNameKey().isTempname()) return false;
        if (ni.getProto() != Generic.tech().invisiblePinNode) return false;
        return true;
    }

    private Map<String,Set<String>> createExportNameMap(NccCellAnnotations ann, Cell cell)
	{
		Map<String,Set<String>> nameMap = new HashMap<String,Set<String>>();
		for (Iterator<List<NccCellAnnotations.NamePattern>> it2 = ann.getExportsConnected(); it2.hasNext(); )
		{
			List<NccCellAnnotations.NamePattern> list = it2.next();
			// list of all patterns that should be connected
			Set<String> connectedExports = new TreeSet<String>(new StringComparator());
			for (NccCellAnnotations.NamePattern pat : list)
			{
				for (Iterator<PortProto> it = cell.getPorts(); it.hasNext(); )
				{
					Export e = (Export)it.next();
					String name = e.getName();
					if (pat.matches(name))
					{
						connectedExports.add(name);
						nameMap.put(name, connectedExports);
					}
				}
                for (Iterator<NodeInst> it = cell.getNodes(); it.hasNext(); )
                {
                    NodeInst ni = it.next();
                    if (!isTextNode(ni)) continue;
                    String name = ni.getName();
                    if (pat.matches(name))
                    {
                        connectedExports.add(name);
                        nameMap.put(name, connectedExports);
                    }
                }
            }
		}
		return nameMap;
	}

	private static class StringComparator implements Comparator<String>
	{
		/**
		 * Method to sort Objects by their string name.
		 */
		public int compare(String s1, String s2)
		{
			return s1.compareTo(s2);
		}
		public boolean equals(Object obj)
		{
			return (this == obj);
		}
	}

	private void writeExport(Export pp, String portName, double x, double y)
	{
		int layer = localPrefs.outDefaultTextLayer, type = 0;
		if (currentLayerNumbers.hasLayerType(GDSLayerType.PIN))
		{
			layer = currentLayerNumbers.getLayerNumber(GDSLayerType.PIN);
			type = currentLayerNumbers.getLayerType(GDSLayerType.PIN);
		}
		outputHeader(HDR_TEXT, 0);
		outputHeader(HDR_LAYER, layer);
		outputHeader(HDR_TEXTTYPE, type);
		outputHeader(HDR_PRESENTATION, EXPORTPRESENTATION);

		// now the orientation
		NodeInst ni = pp.getOriginalPort().getNodeInst();
		int transValue = 0;
		int angle = ni.getAngle();
		if (ni.isXMirrored() != ni.isYMirrored()) transValue |= STRANS_REFLX;
		if (ni.isYMirrored()) angle = (3600 - angle)%3600;
		if (ni.isXMirrored()) angle = (1800 - angle + 3600)%3600;
		outputHeader(HDR_STRANS, transValue);

		// reduce the size of export text by a factor of 2
		outputMag(0.5);
		outputAngle(angle);
		outputShort((short)12);
		outputShort(HDR_XY);
		outputInt(scaleDBUnit(x));
		outputInt(scaleDBUnit(y));

		// now the string
		outputString(portName, HDR_STRING);
		outputHeader(HDR_ENDEL, 0);

		// output the export characteristics
		if (localPrefs.writeExportCharacteristics)
		{
			outputHeader(HDR_PROPATTR, 1);
			String charString = "TBLR " + portName + " " + portName + " ";
			if (pp.getCharacteristic() == PortCharacteristic.IN) charString += "input"; else
				if (pp.getCharacteristic() == PortCharacteristic.OUT) charString += "output"; else
					if (pp.getCharacteristic() == PortCharacteristic.BIDIR) charString += "inputOutput"; else
						charString += "unknown";
			outputString(charString, HDR_PROPVALUE);
			outputHeader(HDR_ENDEL, 0);
		}
	}

    private void writeTextOnLayer(String str, int layer, int type, double x, double y, boolean remapNames, boolean colapseGndVddNames)
    {
        outputHeader(HDR_TEXT, 0);
        outputHeader(HDR_LAYER, layer);
        outputHeader(HDR_TEXTTYPE, type);
        outputHeader(HDR_PRESENTATION, EXPORTPRESENTATION);

        // now the orientation
        int transValue = 0;
        outputHeader(HDR_STRANS, transValue);

        // reduce the size of export text by a factor of 2
        outputMag(0.5);
        outputAngle(0);
        outputShort((short)12);
        outputShort(HDR_XY);
        outputInt(scaleDBUnit(x));
        outputInt(scaleDBUnit(y));

        if (remapNames)
        {
            Set<String> nameSet = nameRemapping.get(str);
            if (nameSet != null)
            {
                str = nameSet.iterator().next();
                str = str + ":" + str;
                //System.out.println("Remapping export "+pp.getName()+" to "+str);
            }
        }
        if (localPrefs.convertBracketsInExports)
        {
            // convert brackets to underscores
            str = str.replaceAll("[\\[\\]]", "_");
        }
        if (colapseGndVddNames)
        {
            String tmp = str.toLowerCase();
            // Detecting string in lower case and later search for "_"
			if ((tmp.startsWith("vdd_") || tmp.startsWith("gnd_")) && TextUtils.isANumber(tmp.substring(4)))
                str = str.substring(0, str.indexOf("_"));
        }
        outputString(str, HDR_STRING);
        outputHeader(HDR_ENDEL, 0);
    }

	/**
	 * Method to determine whether or not to merge geometry.
	 */
	protected boolean mergeGeom(int hierLevelsFromBottom)
	{
		return localPrefs.outMergesBoxes;
	}

	/**
	 * Method to determine whether or not to include the original Geometric with a Poly
	 */
	protected boolean includeGeometric() { return false; }

	private boolean selectLayer(Layer layer)
	{
		GDSLayers numbers = layerNumbers.get(layer);
		if (numbers == null)
		{
			Technology tech = layer.getTechnology();
			for (Iterator<Layer> it = tech.getLayers(); it.hasNext(); )
			{
				Layer l = it.next();
				layerNumbers.put(l, GDSLayers.EMPTY);
			}
			for (Map.Entry<Layer,String> e: tech.getGDSLayers().entrySet())
			{
				Layer l = e.getKey();
				String gdsLayer = e.getValue();
				layerNumbers.put(l, GDSLayers.parseLayerString(gdsLayer));
			}
			numbers = layerNumbers.get(layer);
		}

		// might be null because Artwork layers are auto-generated and not in the Technology list
		if (numbers == null) numbers = GDSLayers.EMPTY;
		currentLayerNumbers = numbers;

		// validLayer false if layerName = "" like for pseudo metals
		return numbers.hasLayerType(GDSLayerType.DRAWING);
	}

	protected void writePoly(PolyBase poly, int layerNumber, int layerType)
	{
		// ignore negative layer numbers
		if (layerNumber < 0) return;

		Point2D [] points = poly.getPoints();
		if (poly.getStyle() == Poly.Type.DISC)
		{
			// Make a square of the size of the diameter
			double r = points[0].distance(points[1]);
			if (r <= 0) return;
			Poly newPoly = new Poly(points[0].getX(), points[0].getY(), r*2, r*2);
			outputBoundary(newPoly, layerNumber, layerType);
			return;
		}

		Rectangle2D polyBounds = poly.getBox();
		if (polyBounds != null)
		{
			// rectangular Manhattan shape: make sure it has positive area
			if (polyBounds.getWidth() == 0 || polyBounds.getHeight() == 0)
                return;

			outputBoundary(poly, layerNumber, layerType);
			return;
		}

		// non-Manhattan or worse .. direct output
		if (points.length == 1)
		{
			reportWarning("WARNING: Single point cannot be written in GDS-II");
			return;
		}
		if (points.length > 200)
		{
			reportWarning("WARNING: GDS-II Polygons may not have more than 200 points (this has " + points.length + ")");
			return;
		}
		if (points.length == 2) outputPath(poly, layerNumber, layerType); else
		outputBoundary(poly, layerNumber, layerType);
	}

	protected void writeNodable(Nodable no)
	{
		NodeInst ni = (NodeInst)no; // In layout cell all Nodables are NodeInsts
		Cell subCell = (Cell)ni.getProto();

		// figure out transformation
		int transValue = 0;
		int angle = ni.getAngle();
		if (ni.isXMirrored() != ni.isYMirrored()) transValue |= STRANS_REFLX;
		if (ni.isYMirrored()) angle = (3600 - angle)%3600;
		if (ni.isXMirrored()) angle = (1800 - angle + 3600)%3600;

		// write a call to a cell
		String name = cellNames.get(subCell);
		if (name == null && subCell.getView() == View.LAYOUTSKEL)
			name = subCell.getName();
		if (name == null) return;
		outputHeader(HDR_SREF, 0);
		outputName(HDR_SNAME, name, localPrefs.cellNameLenMax);
		outputHeader(HDR_STRANS, transValue);
		outputAngle(angle);
		outputShort((short)12);
		outputShort(HDR_XY);
		outputInt(scaleDBUnit(ni.getAnchorCenterX()));
		outputInt(scaleDBUnit(ni.getAnchorCenterY()));
		outputHeader(HDR_ENDEL, 0);
	}

	/****************************** VISITOR SUBCLASS ******************************/

	private BloatVisitor makeBloatVisitor(int maxDepth)
	{
		BloatVisitor visitor = new BloatVisitor(this, maxDepth);
		return visitor;
	}

	/**
	 * Class to override the Geometry visitor and add bloating to all polygons.
	 * Currently, no bloating is being done.
	 */
	private class BloatVisitor extends Geometry.Visitor
	{
		BloatVisitor(Geometry outGeom, int maxHierDepth)
		{
			super(outGeom, maxHierDepth);
		}

		public void addNodeInst(NodeInst ni, FixpTransform trans)
		{
			PrimitiveNode prim = (PrimitiveNode)ni.getProto();
			if (prim.isPin()) return; // skipping pin. Before it was done by detecting pseudo
			Technology tech = prim.getTechnology();
			Poly [] polys = tech.getShapeOfNode(ni);
			Layer firstLayer = null;
			for (int i=0; i<polys.length; i++)
			{
				Poly poly = polys[i];
				Layer thisLayer = poly.getLayer();
				if (thisLayer != null && firstLayer == null) firstLayer = thisLayer;
				if (poly.getStyle().isText())
				{
					// dump this text field
					outputHeader(HDR_TEXT, 0);
					if (firstLayer != null) selectLayer(firstLayer);
					int layerNum = currentLayerNumbers.getLayerNumber(GDSLayerType.DRAWING);
					int layerType = currentLayerNumbers.getLayerType(GDSLayerType.DRAWING);
					outputHeader(HDR_LAYER, layerNum);
					outputHeader(HDR_TEXTTYPE, layerType);
					outputHeader(HDR_PRESENTATION, EXPORTPRESENTATION);

					// figure out transformation
					int transValue = 0;
					int angle = ni.getAngle();
					if (ni.isXMirrored() != ni.isYMirrored()) transValue |= STRANS_REFLX;
					if (ni.isYMirrored()) angle = (3600 - angle)%3600;
					if (ni.isXMirrored()) angle = (1800 - angle + 3600)%3600;

					outputHeader(HDR_STRANS, transValue);
					outputAngle(angle);
					outputShort((short)12);
					outputShort(HDR_XY);
					Point2D [] points = poly.getPoints();
					outputInt(scaleDBUnit(points[0].getX()));
					outputInt(scaleDBUnit(points[0].getY()));

					// now the string
					String str = poly.getString();
					outputString(str, HDR_STRING);
					outputHeader(HDR_ENDEL, 0);
				}
				poly.transform(trans);
			}
			cellGeom.addPolys(polys, ni);
		}

		public void addArcInst(ArcInst ai)
		{
			ArcProto ap = ai.getProto();
			Technology tech = ap.getTechnology();
			Poly [] polys = tech.getShapeOfArc(ai);
			cellGeom.addPolys(polys, ai);
		}
	}

	/*************************** GDS OUTPUT ROUTINES ***************************/

	/**
	 * Method to initialize various fields, get some standard values
	 */
	private void initOutput()
	{
		blockCount = 0;
		bufferPosition = 0;

		// all zeroes
		for (int i=0; i<DSIZE; i++) emptyBuffer[i] = 0;

		Technology tech = topCell.getTechnology();
		scaleFactor = tech.getScale() * localPrefs.unitsPerMeter / 1000000000.0;
		inaccurate = 0;
//		tech.getScale() = 10 (measured in nanometers)
//			so 210nm is 21 lambda
//		localPrefs.precision = 1000;
//		localPrefs.unitsPerMeter = 1,000,000,000
//			sp 210nm is 21 lambda, so write 21 * unitsPerMeter / 1,000,000,000
		layerNumbers = new HashMap<Layer,GDSLayers>();
		nameRemapping = new HashMap<String,Set<String>>();

		// cache the layers in this technology
		boolean foundValid = false;
		for(Iterator<Layer> it = tech.getLayers(); it.hasNext(); )
		{
			Layer layer = it.next();
			if (selectLayer(layer)) foundValid = true;
		}
		if (!foundValid)
		{
			reportWarning("Warning: there are no GDS II layers defined for the " +
			tech.getTechName() + " technology");
		}

		// make a hashmap of all names to use for cells
		cellNames = new HashMap<Cell,String>();
		buildUniqueNames(topCell, cellNames, localPrefs.cellNameLenMax, localPrefs.outUpperCase);
		if (localPrefs.writeAllCells)
		{
			for(Iterator<Cell> it = topCell.getLibrary().getCells(); it.hasNext(); )
			{
				Cell c = it.next();
				if (c.getView() == View.ICON || c.getView() == View.SCHEMATIC || c.getView().isTextView()) continue;
				if (cellNames.containsKey(c)) continue;
				buildUniqueNames(c, cellNames, localPrefs.cellNameLenMax, localPrefs.outUpperCase);
			}
		}
	}

	/**
	 * Recursive method to add all cells in the hierarchy to the hashMap
	 * with unique names.
	 * @param cell the cell whose nodes and sub-node cells will be given unique names.
	 * @param cellNames a hashmap, key: cell, value: unique name (String).
	 */
	public static void buildUniqueNames(Cell cell, Map<Cell,String> cellNames, int maxLen, boolean upperCase)
	{
		if (!cellNames.containsKey(cell))
			cellNames.put(cell, makeUniqueName(cell, cellNames, maxLen, upperCase));
		for(Iterator<NodeInst> it = cell.getNodes(); it.hasNext(); )
		{
			NodeInst ni = it.next();
			if (ni.isCellInstance())
			{
				Cell c = (Cell)ni.getProto();
				Cell cproto = c.contentsView();
				if (cproto == null) cproto = c;
				if (!cellNames.containsKey(cproto))
					cellNames.put(cproto, makeUniqueName(cproto, cellNames, maxLen, upperCase));
				if (ni.isIconOfParent()) continue;
				buildUniqueNames(cproto, cellNames, maxLen, upperCase);
			}
		}
	}

	public static String makeUniqueName(Cell cell, Map<Cell,String> cellNames, int maxLen, boolean upperCase)
	{
		String name = makeGDSName(cell.getName(), maxLen, upperCase);
		if (cell.getNewestVersion() != cell)
			name += "_" + cell.getVersion();

		// see if the name is unique
		String baseName = name;
		Collection<String> existing = cellNames.values();

		// try prepending the library name first
		if (existing.contains(name))
		{
			int liblen = maxLen - (name.length() + concatStr.length());  // space for library name
			if (liblen > 0)
			{
				String lib = cell.getLibrary().getName();
				liblen = (liblen > lib.length()) ? lib.length() : liblen;
				String libname = lib.substring(0, liblen) + concatStr + name;
				if (!existing.contains(libname))
				{
					System.out.println("Warning: GDSII out renaming cell "+cell.describe(false)+" to "+libname);
					return libname;
				}
				baseName = libname;
			}
		}
		for(int index = 1; ; index++)
		{
			if (!existing.contains(name)) break;
			name = baseName + "_" + index;
			int extra = name.length() - maxLen;
			if (extra > 0)
			{
				name = baseName.substring(0, baseName.length()-extra);
				name +="_" + index;
			}
		}

		if (!name.equals(cell.getName()))
			System.out.println("Warning: GDSII out renaming cell "+cell.describe(false)+" to "+name);
		return name;
	}

	/**
	 * function to create proper GDSII names with restricted character set
	 * from input string.
	 * Uses only 'A'-'Z', '_', $, ?, and '0'-'9'
	 */
	private static String makeGDSName(String str, int maxLen, boolean upperCase)
	{
		// filter the name string for the GDS output cell
		StringBuffer ret = new StringBuffer();
		int max = str.length();
		if (max > maxLen-3) max = maxLen-3;
		for(int k=0; k<max; k++)
		{
			char ch = str.charAt(k);
			if (upperCase) ch = Character.toUpperCase(ch);
			if (ch != '$' && !TextUtils.isDigit(ch) && ch != '?' && !Character.isLetter(ch))
				ch = '_';
			ret.append(ch);
		}
		return ret.toString();
	}

	/**
	 * Get the name map. GDS output may mangle cell names
	 * because of all cells occupy the same name space (no libraries).
	 */
	public Map<Cell,String> getCellNames() { return cellNames; }

	/**
	 * Close the file, pad to make the file match the tape format
	 */
	private void doneWritingOutput()
	{
		try
		{
			// Write out the current buffer
			if (bufferPosition > 0)
			{
				// Pack with zeroes
				for (int i = bufferPosition; i < DSIZE; i++) dataBufferGDS[i] = 0;
				dataOutputStream.write(dataBufferGDS, 0, DSIZE);
				blockCount++;
			}

			//  Pad to 2048
			while (blockCount%4 != 0)
			{
				dataOutputStream.write(emptyBuffer, 0, DSIZE);
				blockCount++;
			}
		} catch (IOException e)
		{
			reportError("End of file reached while finishing GDS");
		}
	}

	/**
	 * Method to write a library header, get the date information
	 */
	private void outputBeginLibrary(Cell cell)
	{
		outputHeader(HDR_HEADER, GDSVERSION);
		outputHeader(HDR_BGNLIB, 0);
		outputDate(cell.getCreationDate());
		outputDate(cell.getRevisionDate());
		outputName(HDR_LIBNAME, makeGDSName(cell.getName(), HDR_M_ASCII, localPrefs.outUpperCase), HDR_M_ASCII);
		outputShort(HDR_N_UNITS);
		outputShort(HDR_UNITS);

		/* GDS floating point values - -
		 * 0x3E418937,0x4BC6A7EF = 0.001
		 * 0x3944B82F,0xA09B5A53 = 1e-9
		 * 0x3F28F5C2,0x8F5C28F6 = 0.01
		 * 0x3A2AF31D,0xC4611874 = 1e-8
		 */
		// set units
		if (NEWUNITS)
		{
			double userUnitsPerDBUnit = 1.0 / localPrefs.precision;
			double dbUnitsInMeters = 1.0 / localPrefs.unitsPerMeter;
			outputDouble(userUnitsPerDBUnit);
			outputDouble(dbUnitsInMeters);
		} else
		{		
			outputDouble(1e-3);
			outputDouble(1.0e-9);
		}
	}

	boolean outputBeginStruct(Cell cell)
	{
		String name = cellNames.get(cell);
		if (name == null)
		{
			reportWarning("Warning, sub"+cell+" in hierarchy is not the same view" +
				" as top level cell");
			name = makeUniqueName(cell, cellNames, localPrefs.cellNameLenMax, localPrefs.outUpperCase);
			cellNames.put(cell, name);
		}

		// do not write duplicate names
		if (writtenCellNames.contains(name))
		{
			System.out.println("Ignoring cell " + cell.describe(false) + " because it is already included in GDS file");
			return true;
		}
		writtenCellNames.add(name);

		outputHeader(HDR_BGNSTR, 0);
		outputDate(cell.getCreationDate());
		outputDate(cell.getRevisionDate());
		outputName(HDR_STRNAME, name, localPrefs.cellNameLenMax);
		return false;
	}

	/**
	 * Method to output date information
	 */
	private void outputDate(Date val)
	{
		short [] date = new short[6];

		Calendar cal = Calendar.getInstance();
		cal.setTime(val);
		int year = cal.get(Calendar.YEAR) - 1900;
		date[0] = (short)year;
		date[1] = (short)cal.get(Calendar.MONTH);
		date[2] = (short)cal.get(Calendar.DAY_OF_MONTH);
		date[3] = (short)cal.get(Calendar.HOUR);
		date[4] = (short)cal.get(Calendar.MINUTE);
		date[5] = (short)cal.get(Calendar.SECOND);
		outputShortArray(date, 6);
	}

	/**
	 * Write a simple header, with a fixed length
	 * Enter with the header as argument, the routine will output
	 * the count, the header, and the argument (if present) in p1.
	 */
	private void outputHeader(short header, int p1)
	{
		int type = header & BYTEMASK;
		short count = 4;
		if (type != DTYP_NONE)
		{
			switch (header)
			{
				case HDR_HEADER:
				case HDR_LAYER:		// two byte signed integer
				case HDR_DATATYPE:	// two byte signed integer
				case HDR_TEXTTYPE:
				case HDR_PROPATTR:	// two byte signed integer
				case HDR_STRANS:
				case HDR_PRESENTATION:
					count = 6;
					break;
				case HDR_BGNSTR:
				case HDR_BGNLIB:
					count = HDR_N_BGNLIB;
					break;
				case HDR_UNITS:
					count = HDR_N_UNITS;
					break;
				default:
					reportError("No entry for header " + header);
					return;
			}
		}
		outputShort(count);
		outputShort(header);
		if (type == DTYP_NONE) return;
		if (count == 6) outputShort((short)p1);
		if (count == 8) outputInt(p1);
	}

	/**
	 * Add a name (STRNAME, LIBNAME, etc.) to the file. The header
	 * to be used is in header; the string starts at p1
	 * if there is an odd number of bytes, then output the 0 at
	 * the end of the string as a pad. The maximum length of string is "max"
	 */
	private void outputName(short header, String p1, int max)
	{
		outputString(p1, header, max);
	}

	/**
	 * Method to output an angle as part of a STRANS
	 */
	private void outputAngle(int ang)
	{
		double gdfloat = ang / 10.0;
		outputShort(HDR_N_ANGLE);
		outputShort(HDR_ANGLE);
		outputDouble(gdfloat);
	}

	/**
	 * Method to output a magnification as part of a STRANS
	 */
	private void outputMag(double scale)
	{
		outputShort(HDR_N_MAG);
		outputShort(HDR_MAG);
		outputDouble(scale);
	}

	private List<Point> reducePolygon(PolyBase poly)
	{
		List<Point> pts = new ArrayList<Point>();
		Point2D [] points = poly.getPoints();
		int lastX = scaleDBUnit(points[0].getX());
		int lastY = scaleDBUnit(points[0].getY());
		int firstX = lastX;
		int firstY = lastY;
		pts.add(new Point(lastX, lastY));
		for(int i=1; i<points.length; i++)
		{
			int x = scaleDBUnit(points[i].getX());
			int y = scaleDBUnit(points[i].getY());
			if (x == lastX && y == lastY) continue;
			lastX = x;
			lastY = y;
			pts.add(new Point(lastX, lastY));
		}
		if (pts.size() > 2)
		{
			Point endPoint = pts.get(pts.size()-1);
			if (firstX == endPoint.x && firstY == endPoint.y)
				pts.remove(pts.size()-1);
		}
		return pts;
	}

	/**
	 * Method to output the pairs of XY points to the file
	 */
	private void outputBoundary(PolyBase poly, int layerNumber, int layerType)
	{
		// remove redundant points
		List<Point> reducedPoints = reducePolygon(poly);
		int count = reducedPoints.size();
		if (count <= 2) return;
		int start = 0;
//System.out.println("TESTING "+count+" POINTS");
		// find out whether this polygon has a hole in it
		Map<String,List<Integer>> polyMap = new HashMap<String,List<Integer>>();
		for(int i=0; i<count; i++)
		{
			Point pt = reducedPoints.get(i);
			String polyKey = pt.x + "x" + pt.y;
			List<Integer> coordIndices = polyMap.get(polyKey);
			if (coordIndices == null) polyMap.put(polyKey, coordIndices = new ArrayList<Integer>());
			coordIndices.add(Integer.valueOf(i));
		}
		List<Integer> firstPair = null, secondPair = null;
		for(String polyKey : polyMap.keySet())
		{
			List<Integer> pairs = polyMap.get(polyKey);
//System.out.println("COORDINATE "+polyKey+" FOUND "+pairs.size()+" TIMES");
			if (pairs.size() < 2) continue;
			if (pairs.size() > 2)
			{
				firstPair = secondPair = null;
				break;
			}
			if (pairs.contains(Integer.valueOf(0)) && pairs.contains(Integer.valueOf(count-1))) continue;
			if (firstPair == null) firstPair = pairs; else
			{
				if (secondPair == null) secondPair = pairs; else
				{
					firstPair = secondPair = null;
					break;
				}
			}
		}
		boolean polyWithHole = false;
		if (firstPair != null && secondPair != null)
		{
			int index1A = firstPair.get(0).intValue();
			int index1B = firstPair.get(1).intValue();
			int index2A = secondPair.get(0).intValue();
			int index2B = secondPair.get(1).intValue();
			if (nextTo(index1A, index2A, count) && nextTo(index1B, index2B, count)) polyWithHole = true; else
				if (nextTo(index1A, index2B, count) && nextTo(index1B, index2A, count)) polyWithHole = true;
//System.out.println("SO FOUND TWO PAIRS: "+index1A+" / "+index1B+" AND "+index2A+" / "+index2B);
		}
//System.out.println("POLYWITHHOLE="+polyWithHole);

		for(;;)
		{
			// look for a closed section
			int sofar = start+1;
			if (polyWithHole) sofar = count; else
			{
				for( ; sofar<count; sofar++)
				{
					if (reducedPoints.get(sofar).x == reducedPoints.get(start).x &&
						reducedPoints.get(sofar).y == reducedPoints.get(start).y) break;
				}
			}
			outputHeader(HDR_BOUNDARY, 0);
			outputHeader(HDR_LAYER, layerNumber);
			outputHeader(HDR_DATATYPE, layerType);
			outputShort((short)(8 * ((sofar-start)+1) + 4));
			outputShort(HDR_XY);
			for (int i = start; i <= sofar; i++)
			{
				int j = i;
				if (i == sofar) j = start;
				outputInt(reducedPoints.get(j).x);
				outputInt(reducedPoints.get(j).y);
			}
			outputHeader(HDR_ENDEL, 0);
			if (sofar >= count) break;
			start = sofar;
		}
	}

	private boolean nextTo(int a, int b, int total)
	{
		if (((a+1)%total) == b || ((b+1)%total) == a) return true;
		return false;
	}

	private void outputPath(PolyBase poly, int layerNumber, int layerType)
	{
		// remove redundant points
		List<Point> reducedPoints = reducePolygon(poly);
		int numPoints = reducedPoints.size();
		if (numPoints <= 2) return;

		outputHeader(HDR_PATH, 0);
		outputHeader(HDR_LAYER, layerNumber);
		outputHeader(HDR_DATATYPE, layerType);
		int count = 8 * numPoints + 4;
		outputShort((short)count);
		outputShort(HDR_XY);
		for (int i = 0; i < numPoints; i ++)
		{
			outputInt(reducedPoints.get(i).x);
			outputInt(reducedPoints.get(i).y);
		}
		outputHeader(HDR_ENDEL, 0);
	}

	/**
	 * Method to add one byte to the file
	 */
	private void outputByte(byte val)
	{
		dataBufferGDS[bufferPosition++] = val;
		if (bufferPosition >= DSIZE)
		{
			try
			{
				dataOutputStream.write(dataBufferGDS, 0, DSIZE);
			} catch (IOException e)
			{
				reportError("End of file reached while writing GDS");
			}
			blockCount++;
			bufferPosition = 0;
		}
	}

	private int scaleDBUnit(double dbunit)
	{
		// scale according to technology
		double scaled = scaleFactor*dbunit;

		// round to nearest nanometer
		int unit = (int)Math.round(scaled);
		if (unit != scaled)
		{
			int accurateScale = 10;
			for(int i=0; i<10; i++)
			{
				double scaledScale = scaled * accurateScale;
				long scaledUnit = (long)Math.round(scaledScale);
				if (scaledUnit == scaledScale) break;
				accurateScale *= 10;
			}
			if (accurateScale > inaccurate) inaccurate = accurateScale;
		}
		return unit;
	}

	/*************************** GDS LOW-LEVEL OUTPUT ROUTINES ***************************/

	/**
	 * Method to add a 2-byte integer to the output
	 */
	private void outputShort(short val)
	{
		outputByte((byte)((val>>8)&BYTEMASK));
		outputByte((byte)(val&BYTEMASK));
	}

	/**
	 * Method to add a 4-byte integer to the output
	 */
	private void outputInt(int val)
	{
		outputShort((short)(val>>16));
		outputShort((short)val);
	}

	/**
	 * Method to add an array of 2 byte integers to the output.
	 * @param ptr the array.
	 * @param n the array length.
	 */
	private void outputShortArray(short [] ptr, int n)
	{
		for (int i = 0; i < n; i++) outputShort(ptr[i]);
	}

	private void outputString(String str, short header)
	{
		// The usual maximum length for string is 512, though names etc may need to be shorter
		outputString(str, header, 512);
	}

	/**
	 * String of n bytes
	 * Revised 90-11-23 to convert to upper case (SRP)
	 */
	private void outputString(String str, short header, int max)
	{
		int charsToUse = str.length();
		if (charsToUse > max) charsToUse = max;

		// round up string length to the nearest integer
		int j = charsToUse;
		if ((j % 2) != 0)
		{
			j = (j / 2)*2 + 2;
		}
		assert( (j%2) == 0);

		// pad with a blank
		outputShort((short)(4+j));
		outputShort(header);

		int i = 0;
		if (localPrefs.outUpperCase)
		{
			// convert to upper case
			for( ; i<charsToUse; i++)
				outputByte((byte)Character.toUpperCase(str.charAt(i)));
		} else
		{
			for( ; i<charsToUse; i++)
				outputByte((byte)str.charAt(i));
		}
		for ( ; i < j; i++)
			outputByte((byte)0);
	}

	/**
	 * Method to write a GDSII representation of a double.
	 * New conversion code contributed by Tom Valine <tomv@transmeta.com>.
	 * @param data the double to process.
	 */
	public void outputDouble(double data)
	{
		if (data == 0.0)
		{
			for(int i=0; i<8; i++) outputByte((byte)0);
			return;
		}
		BigDecimal reg = new BigDecimal(data).setScale(64, BigDecimal.ROUND_HALF_EVEN);

		boolean negSign = false;
		if (reg.doubleValue() < 0)
		{
			negSign = true;
			reg = reg.negate();
		}

		int exponent = 64;
		for(; (reg.doubleValue() < 0.0625) && (exponent > 0); exponent--)
			reg = reg.multiply(new BigDecimal(16.0));
		if (exponent == 0) System.out.println("Exponent underflow");
		for(; (reg.doubleValue() >= 1) && (exponent < 128); exponent++)
			reg = reg.divide(new BigDecimal(16.0), BigDecimal.ROUND_HALF_EVEN);
		if (exponent > 127) System.out.println("Exponent overflow");
		if (negSign) exponent |= 0x00000080;
		BigDecimal f_mantissa = reg.subtract(new BigDecimal(reg.intValue()));
		for(int i = 0; i < 56; i++)
			f_mantissa = f_mantissa.multiply(new BigDecimal(2.0));
		long mantissa = f_mantissa.longValue();
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		baos.write(exponent);
		for(int i = 6; i >= 0; i--)
			baos.write((int)((mantissa >> (i * 8)) & 0xFF));
		byte [] result = baos.toByteArray();
		for(int i=0; i<8; i++) outputByte(result[i]);
	}

//	/**
//	 * Method to write a GDSII representation of a double.
//	 * Original C-Electric code (no longer used).
//	 * @param data the double to process.
//	 */
//	private void outputDouble(double a)
//	{
//		int [] ret = new int[2];
//
//		// handle default
//		if (a == 0)
//		{
//			ret[0] = 0x40000000;
//			ret[1] = 0;
//			outputIntArray(ret, 2);
//			return;
//		}
//
//		// identify sign
//		double temp = a;
//		boolean negsign = false;
//		if (temp < 0)
//		{
//			negsign = true;
//			temp = -temp;
//		}
//
//		// establish the excess-64 exponent value
//		int exponent = 64;
//
//		// scale the exponent and mantissa
//		for (; temp < 0.0625 && exponent > 0; exponent--) temp *= 16.0;
//
//		if (exponent == 0) System.out.println("Exponent underflow");
//
//		for (; temp >= 1 && exponent < 128; exponent++) temp /= 16.0;
//
//		if (exponent > 127) System.out.println("Exponent overflow");
//
//		// set the sign
//		if (negsign) exponent |= 0x80;
//
//		// convert temp to 7-byte binary integer
//		double top = temp;
//		for (int i = 0; i < 24; i++) top *= 2;
//		int highmantissa = (int)top;
//		double frac = top - highmantissa;
//		for (int i = 0; i < 32; i++) frac *= 2;
//		ret[0] = highmantissa | (exponent<<24);
//		ret[1] = (int)frac;
//		outputIntArray(ret, 2);
//	}
//
//	/**
//	 * Method to add an array of 4 byte integers or floating numbers to the output.
//	 * @param ptr the array.
//	 * @param n the array length.
//	 */
//	private void outputIntArray(int [] ptr, int n)
//	{
//		for (int i = 0; i < n; i++) outputInt(ptr[i]);
//	}
}
