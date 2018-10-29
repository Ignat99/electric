/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: LEF.java
 * Input/output tool: LEF output
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

import com.sun.electric.database.geometry.EPoint;
import com.sun.electric.database.geometry.Poly;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.Export;
import com.sun.electric.database.hierarchy.HierarchyEnumerator;
import com.sun.electric.database.hierarchy.Nodable;
import com.sun.electric.database.network.Netlist;
import com.sun.electric.database.network.Network;
import com.sun.electric.database.prototype.PortCharacteristic;
import com.sun.electric.database.prototype.PortOriginal;
import com.sun.electric.database.prototype.PortProto;
import com.sun.electric.database.text.TextUtils;
import com.sun.electric.database.text.Version;
import com.sun.electric.database.topology.ArcInst;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.topology.PortInst;
import com.sun.electric.database.variable.VarContext;
import com.sun.electric.technology.Layer;
import com.sun.electric.technology.PrimitiveNode;
import com.sun.electric.technology.PrimitivePort;
import com.sun.electric.technology.Technology;
import com.sun.electric.tool.io.IOTool;
import com.sun.electric.tool.ncc.basic.NccCellAnnotations;
import com.sun.electric.util.math.ECoord;
import com.sun.electric.util.math.FixpTransform;
import com.sun.electric.util.math.GenMath;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * This is the netlister for LEF.
 *
 * Note that this writer was built by examining LEF files and reverse-engineering them.
 * It does not claim to be compliant with the LEF specification, but it also does not
 * claim to define a new specification.  It is merely incomplete.
 */
public class LEF extends Output
{
	private Layer currentLayer;
	private Layer metalLayer;
	private Set<NodeInst> nodesSeen;
	private Set<ArcInst> arcsSeen;
	private LEFPreferences localPrefs;

	public static class LEFPreferences extends OutputPreferences
    {
		public boolean ignoreTechnology;
		
        public LEFPreferences(boolean factory) 
        { 
        	super(factory);
        	if (factory)
        	{
        		ignoreTechnology = IOTool.isFactoryLEFIgnoreTechnology();
        	}
        	else
        	{
        		ignoreTechnology = IOTool.isLEFIgnoreTechnology();
        	}
        }

        public Output doOutput(Cell cell, VarContext context, String filePath)
        {
    		LEF out = new LEF(this);

    		// find a metal layer for generic exports
    		out.metalLayer = null;
    		Technology tech = cell.getTechnology();
    		for(Iterator<Layer> it = tech.getLayers(); it.hasNext(); )
    		{
    			Layer layer = it.next();
    			if (layer.getFunction().isMetal())
    			{
    				out.metalLayer = layer;
    				break;
    			}
    		}

    		if (out.openTextOutputStream(filePath)) return out.finishWrite();

    		// first see if this is a standard LEF/DEF two-level hierarchy
    		int levelCount = 1;
    		Set<Cell> writeThese = new TreeSet<Cell>();
    		for(Iterator<NodeInst> it = cell.getNodes(); it.hasNext(); )
    		{
    			NodeInst ni = it.next();
    			if (ni.isCellInstance())
    			{
    				levelCount = 2;
    				Cell subCell = (Cell)ni.getProto();
    				writeThese.add(subCell);
    				for(Iterator<NodeInst> sIt = subCell.getNodes(); sIt.hasNext(); )
    				{
    					NodeInst subNi = sIt.next();
    					if (subNi.isCellInstance())
    					{
    						levelCount = 3;
    						break;
    					}
    				}
    			}
    			if (levelCount > 2) break;
    		}
    		if (levelCount == 2)
    		{
    			// standard 2-level hierarchy: write subcells in LEF and let top cell go in DEF
	    		out.writeLEFHeader(cell);
	    		FixpTransform ident = new FixpTransform();
    			for(Cell c : writeThese)
    			{
    				Netlist netlist = c.getNetlist();
    	    		out.writeCellHeader(netlist);
    	    		out.dumpCellContents(c, ident, false);
    	    		out.writeCellTrailer(c);
    			}
	    		out.writeLEFTrailer();
        		System.out.println("NOTE: Wrote subcells, but did not write the " + cell.describe(false) + " cell. Use DEF export for that.");
    		} else
    		{
    			// nonstandard hierarchy: flatten everything and put it in the LEF file
	    		Netlist netlist = cell.getNetlist(Netlist.ShortResistors.ALL);
	    		out.writeLEFHeader(cell);
	    		out.writeCellHeader(netlist);
	    		HierarchyEnumerator.enumerateCell(netlist, context, new LEFVisitor(out));
	    		out.writeCellTrailer(cell);
	    		out.writeLEFTrailer();
    		}

    		if (out.closeTextOutputStream()) return out.finishWrite();
    		System.out.println(filePath + " written");
            return out.finishWrite();
        }
    }

	/**
	 * Creates a new instance of the LEF netlister.
	 */
	LEF(LEFPreferences lp) { localPrefs = lp; }

	/**
	 * Method to write the LEF file header.
	 * @param cell the top-level Cell being written.
	 */
	private void writeLEFHeader(Cell cell)
	{
		Technology tech = cell.getTechnology();
		double nanometersPerUnit = tech.getScale();
		ECoord resolution = tech.getFactoryResolution();

		// write header information
		if (localPrefs.includeDateAndVersionInOutput)
		{
			printWriter.println("# Electric VLSI Design System, version " + Version.getVersion());
			printWriter.println("# " + TextUtils.formatDate(new Date()));
		} else
		{
			printWriter.println("# Electric VLSI Design System");
		}
		emitCopyright("# ", "");
		printWriter.println();
		printWriter.println("NAMESCASESENSITIVE ON ;");
		printWriter.println("UNITS");
		printWriter.println("  DATABASE MICRONS " + TextUtils.formatDouble(nanometersPerUnit*100) + " ;");
		printWriter.println("END UNITS");
		printWriter.println("MANUFACTURINGGRID " + resolution + " ;");
		printWriter.println();

		if (!localPrefs.ignoreTechnology)
		{
			// write layer information
			for(int i=0; i<tech.getNumMetals(); i++)
			{
				printWriter.println("LAYER M" + (i+1));
				printWriter.println("  TYPE ROUTING ;");
				printWriter.println("END M" + (i+1));
				printWriter.println();
			}
			printWriter.println("LAYER CONT");
			printWriter.println("  TYPE CUT ;");
			printWriter.println("END CONT");
			printWriter.println();
			for(int i=0; i<(tech.getNumMetals()-1); i++)
			{
				printWriter.println("LAYER VIA" + (i+1));
				printWriter.println("  TYPE CUT ;");
				printWriter.println("END VIA" + (i+1));
				printWriter.println();
			}
			for(int i=0; i<3; i++)
			{
				printWriter.println("LAYER POLY" + (i+1));
				printWriter.println("  TYPE MASTERSLICE ;");
				printWriter.println("END POLY" + (i+1));
				printWriter.println();
			}
			printWriter.println("LAYER PDIFF");
			printWriter.println("  TYPE MASTERSLICE ;");
			printWriter.println("END PDIFF");
			printWriter.println();
			printWriter.println("LAYER NDIFF");
			printWriter.println("  TYPE MASTERSLICE ;");
			printWriter.println("END NDIFF");
			printWriter.println();
		}
	}

	/**
	 * Method to write the LEF file trailer.
	 */
	private void writeLEFTrailer()
	{
		printWriter.println("END LIBRARY");
	}

	/**
	 * Method to write the header of a Cell (everything except the nodes and arcs).
	 * @param netList the Netlist of the Cell.
	 */
	private void writeCellHeader(Netlist netList)
	{
		Cell cell = netList.getCell();
		Technology tech = cell.getTechnology();
		
		// write main cell header
		printWriter.println("MACRO " + cell.getName());
		printWriter.println("  CLASS CORE ;");
		printWriter.println("  FOREIGN " + cell.getName() + " 0 0 ;");
		printWriter.println("  ORIGIN 0 0 ;");
		Rectangle2D bounds = cell.getBounds();
		double width = TextUtils.convertDistance(bounds.getWidth(), tech, TextUtils.UnitScale.MICRO);
		double height = TextUtils.convertDistance(bounds.getHeight(), tech, TextUtils.UnitScale.MICRO);
		printWriter.println("  SIZE " + TextUtils.formatDouble(width) + " BY " + TextUtils.formatDouble(height) + " ;");
		printWriter.println("  SYMMETRY X Y ;");
		printWriter.println("  SITE " + cell.getName() + " ;");

		// write all of the metal geometry and ports
		nodesSeen = new HashSet<NodeInst>();
		arcsSeen = new HashSet<ArcInst>();
		
		// Check if exports are connected via NCC annotation as well
        NccCellAnnotations anna = NccCellAnnotations.getAnnotations(cell);
        if (anna != null) {
            // each list contains all name patterns that are be shorted together
            if (anna.getExportsConnected().hasNext()) {
            	printWriter.println("\n### Exports shorted due to NCC annotation 'exportsConnectedByParent':\n");
            }
        }

        // make a map of networks to exports on those networks
		Map<Network,List<Export>> unconnectedExports = new HashMap<Network,List<Export>>();
		Map<List<NccCellAnnotations.NamePattern>,Network> patNetworkMap = new HashMap<List<NccCellAnnotations.NamePattern>,Network>();
		
		for(Iterator<PortProto> it = cell.getPorts(); it.hasNext(); )
		{
			Export e = (Export)it.next();
			Network net = netList.getNetwork(e, 0);
			
			// check if it has an equivalent network where it belongs to by exportsConnectedByParent
			if (anna != null)
			{
				Network match = null;
				String name = e.getName();
				// check if they are connections
				for (Iterator<List<NccCellAnnotations.NamePattern>> pIt = anna.getExportsConnected(); pIt.hasNext(); ) 
				{
					List<NccCellAnnotations.NamePattern> list = pIt.next();
		            // each name pattern can match any number of exports in the cell
		            for (NccCellAnnotations.NamePattern pat : list) 
		            {
		            	if (pat.matches(name)) 
		            	{
		            		match = patNetworkMap.get(list);
		            		if (match == null) // first time
		            		{
		            			patNetworkMap.put(list, net);
		            			match = net;
		            		}
		            		break;
		            	}
		            }
		            if (match != null) break;
				}
				if (match != null)
					net = match;
			}
			
			List<Export> exportsOnNet = unconnectedExports.get(net);
			if (exportsOnNet == null)
			{
				exportsOnNet = new ArrayList<Export>();
				unconnectedExports.put(net, exportsOnNet);
			}
			exportsOnNet.add(e);
		}
        
		List<Network> netsToWrite = new ArrayList<Network>();
		for(Network net : unconnectedExports.keySet())
			netsToWrite.add(net);
		Collections.sort(netsToWrite, new TextUtils.NetworksByName());

		// write exports organized by network connections
		boolean first = true;
        
		for(Network net : netsToWrite)
		{
			List<Export> exportsOnNet = unconnectedExports.get(net);
			Export main = null;
			for(Export e : exportsOnNet)
			{
				if (main == null) main = e; else
				{
					if (main.getName().length() > e.getName().length()) main = e;
				}
			}

			if (first) first = false; else printWriter.println();
			printWriter.println("  PIN " + main.getName());
			Set<NodeInst> nodesUnderExports = new HashSet<NodeInst>();
			PortCharacteristic type = PortCharacteristic.UNKNOWN;
			for(Export e : exportsOnNet)
			{
				type = e.getCharacteristic();
				nodesUnderExports.add(e.getOriginalPort().getNodeInst());
			}
			if (type == PortCharacteristic.IN || type.isClock() || type == PortCharacteristic.REFIN) printWriter.println("    DIRECTION INPUT ;"); else
			if (type == PortCharacteristic.OUT || type == PortCharacteristic.REFOUT) printWriter.println("    DIRECTION OUTPUT ;"); else
			if (type == PortCharacteristic.BIDIR) printWriter.println("    DIRECTION INOUT ;"); else
			if (type == PortCharacteristic.GND) printWriter.println("    DIRECTION INOUT ;\n    USE GROUND ;"); else
			if (type == PortCharacteristic.PWR) printWriter.println("    DIRECTION INOUT ;\n    USE POWER ;");
			for(Export e : exportsOnNet)
			{
				PortOriginal fp = new PortOriginal(e.getOriginalPort());
				NodeInst rni = fp.getBottomNodeInst();
				PrimitivePort rpp = fp.getBottomPortProto();
				FixpTransform trans = fp.getTransformToTop();
				printWriter.println("    PORT");
				currentLayer = null;
				Poly [] polys = tech.getShapeOfNode(rni, true, false, null);
				if (polys.length == 0)
				{
					PrimitiveNode np = (PrimitiveNode)rni.getProto();
					Technology.NodeLayer [] nls = np.getNodeLayers();
					if (nls.length > 0)
					{
						polys = new Poly[1];
						polys[0] = new Poly(rni.getAnchorCenterX(), rni.getAnchorCenterY(), rni.getXSize(), rni.getYSize());
						polys[0].setLayer(nls[0].getLayer());
						polys[0].setPort(rpp);
					}
				}
				for(int i=0; i<polys.length; i++)
				{
					Poly poly = polys[i];
					if (poly.getPort() != rpp) continue;

					// force a valid layer for the port
					String layerName = "";
					Layer layer = poly.getLayer();
					if (layer != null) layerName = io_lefoutlayername(layer);
					if (layerName.length() == 0) poly.setLayer(metalLayer);

					// force a valid box for the port
					poly.transform(trans);
					Rectangle2D polyBounds = poly.getBox();
					if (polyBounds == null)
					{
						EPoint ctr = poly.getCenter();
						Poly newPoly = new Poly(ctr.getX(), ctr.getY(), 0, 0);
						newPoly.setLayer(poly.getLayer());
						poly = newPoly;
					}

					// write the port geometry
					io_lefwritepoly(poly, trans, tech, true);
				}
				if (e == main) io_lefoutspread(cell, net, nodesUnderExports, netList);
				printWriter.println("    END");
			}
			if (main.getCharacteristic() == PortCharacteristic.PWR)
				printWriter.println("    USE POWER ;");
			if (main.getCharacteristic() == PortCharacteristic.GND)
				printWriter.println("    USE GROUND ;");
			printWriter.println("  END " + main.getName());
		}

		// write the obstructions (all of the metal)
		printWriter.println();
		printWriter.println("  OBS");
		currentLayer = null;
	}

	/**
	 * Method to write the trailer LEF for a cell.
	 * @param cell the Cell being closed.
	 */
	private void writeCellTrailer(Cell cell)
	{
	 	printWriter.println("  END");
		printWriter.println("END " + cell.getName());
		printWriter.println();
	}

	/**
	 * Class for flattening the hierarchy when this is not a standard 2-level structure.
	 */
	private static class LEFVisitor extends HierarchyEnumerator.Visitor
	{
		private LEF generator;

		public LEFVisitor(LEF generator)
		{
			this.generator = generator;
		}

		public boolean enterCell(HierarchyEnumerator.CellInfo info)
		{
			Cell cell = info.getCell();
			FixpTransform trans = info.getTransformToRoot();
			generator.dumpCellContents(cell, trans, info.isRootCell());
			return true;
		}

		public void exitCell(HierarchyEnumerator.CellInfo info) {}

		public boolean visitNodeInst(Nodable no, HierarchyEnumerator.CellInfo info) { return true; }
	}

	/**
	 * Method to write the nodes and arcs in a Cell.
	 * @param cell the Cell to write
	 * @param trans a transformation to apply to everything.
	 * @param isRootCell true if this is the top-level cell.
	 */
	private void dumpCellContents(Cell cell, FixpTransform trans, boolean isRootCell)
	{
		for(Iterator<NodeInst> it = cell.getNodes(); it.hasNext(); )
		{
			NodeInst ni = it.next();
			if (ni.isCellInstance()) continue;
			if (isRootCell && nodesSeen.contains(ni)) continue;
			FixpTransform rot = ni.rotateOut(trans);
			Technology tech = ni.getProto().getTechnology();
			Poly [] polys = tech.getShapeOfNode(ni);
			for(int i=0; i<polys.length; i++)
			{
				Poly poly = polys[i];
				io_lefwritepoly(poly, rot, tech, false);
			}
		}

		// write metal layers for all arcs
		for(Iterator<ArcInst> it = cell.getArcs(); it.hasNext(); )
		{
			ArcInst ai = it.next();
			if (isRootCell && arcsSeen.contains(ai)) continue;
			Technology tech = ai.getProto().getTechnology();
			Poly [] polys = tech.getShapeOfArc(ai);
			for(int i=0; i<polys.length; i++)
			{
				Poly poly = polys[i];
				io_lefwritepoly(poly, trans, tech, false);
			}
		}
	}

	/**
	 * Method to write geometry on a specified network.
	 * @param cell the Cell whose geometry is to be written.
	 * @param net the Network in the Cell to write.
	 * @param nodesUnderExports a Set of NodeInsts that are underneath exports and should not be written.
	 * @param netList the Netlist of the Cell.
	 */
	void io_lefoutspread(Cell cell, Network net, Set<NodeInst> nodesUnderExports, Netlist netList)
	{
		for(Iterator<NodeInst> it = cell.getNodes(); it.hasNext(); )
		{
			NodeInst ni = it.next();
			if (ni.isCellInstance()) continue;
			if (nodesUnderExports.contains(ni)) continue;
			PrimitiveNode.Function fun = ni.getFunction();
			if (!fun.isPin() &&
				!fun.isContact() &&
				fun != PrimitiveNode.Function.NODE &&
				// added WELL so that WELL contacts which are part of either
				// VDD or GND nets are not written out as obstructions
				fun != PrimitiveNode.Function.WELL &&
				fun != PrimitiveNode.Function.SUBSTRATE &&
				fun != PrimitiveNode.Function.CONNECT) continue;
			boolean found = true;
			for(Iterator<PortInst> pIt = ni.getPortInsts(); pIt.hasNext(); )
			{
				PortInst pi = pIt.next();
				Network pNet = netList.getNetwork(pi);
				if (pNet != net) { found = false;   break; }
			}
			if (!found) continue;

			// write all layers on this node
			nodesSeen.add(ni);
			FixpTransform trans = ni.rotateOut();
			Technology tech = ni.getProto().getTechnology();
			Poly [] polys = tech.getShapeOfNode(ni);
			for(int i=0; i<polys.length; i++)
			{
				Poly poly = polys[i];
				io_lefwritepoly(poly, trans, tech, true);
			}
		}

		// write metal layers for all arcs
		for(Iterator<ArcInst> it = cell.getArcs(); it.hasNext(); )
		{
			ArcInst ai = it.next();
			Network aNet = netList.getNetwork(ai, 0);
			if (aNet != net) continue;
			arcsSeen.add(ai);
			Technology tech = ai.getProto().getTechnology();
			Poly [] polys = tech.getShapeOfArc(ai);
			for(int i=0; i<polys.length; i++)
			{
				Poly poly = polys[i];
				io_lefwritepoly(poly, GenMath.MATID, tech, true);
			}
		}
	}

	/**
	 * Method to write a polygon to the LEF file.
	 * @param poly the Poly to write.
	 * @param trans the transformation of the polygon.
	 * @param tech the Technology of the polygon.
	 * @param extraIndent true to indent the text.
	 */
	private void io_lefwritepoly(Poly poly, FixpTransform trans, Technology tech, boolean extraIndent)
	{
		Layer layer = poly.getLayer();
		if (layer == null) return;
		String layername = io_lefoutlayername(layer);
		if (layername.length() == 0) return;
		poly.transform(trans);
		Rectangle2D polyBounds = poly.getBox();
		if (polyBounds == null) return;
		double flx = TextUtils.convertDistance(polyBounds.getMinX(), tech, TextUtils.UnitScale.MICRO);
		double fly = TextUtils.convertDistance(polyBounds.getMinY(), tech, TextUtils.UnitScale.MICRO);
		double fhx = TextUtils.convertDistance(polyBounds.getMaxX(), tech, TextUtils.UnitScale.MICRO);
		double fhy = TextUtils.convertDistance(polyBounds.getMaxY(), tech, TextUtils.UnitScale.MICRO);
		if (layer != currentLayer)
		{
			if (extraIndent) printWriter.print("  ");
			printWriter.println("    LAYER " + layername + " ;");
			currentLayer = layer;
		}
		if (extraIndent) printWriter.print("  ");
		printWriter.println("      RECT " + TextUtils.formatDouble(flx) + " " + TextUtils.formatDouble(fly) + " " +
			TextUtils.formatDouble(fhx) + " " + TextUtils.formatDouble(fhy) + " ;");
	}

	private String io_lefoutlayername(Layer layer)
	{
		Layer.Function fun = layer.getFunction();
		if (fun.isMetal()) return "M" + fun.getLevel();
		if (fun == Layer.Function.GATE) return "POLY1";
		if (fun.isPoly()) return "POLY" + fun.getLevel();
		if (fun.isContact())
		{
			int level = fun.getLevel();
			if (level == 1) return "CONT";
			return "VIA" + (level-1);
		}
		if (fun == Layer.Function.DIFFN) return "NDIFF";
		if (fun == Layer.Function.DIFFP) return "PDIFF";
		if (fun == Layer.Function.DIFF) return "DIFF";
		return "";
	}
}
