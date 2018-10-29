/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Sue.java
 * Input/output tool: Sue input
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
import com.sun.electric.database.geometry.EGraphics;
import com.sun.electric.database.geometry.EPoint;
import com.sun.electric.database.geometry.ERectangle;
import com.sun.electric.database.geometry.Poly;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.Export;
import com.sun.electric.database.hierarchy.Library;
import com.sun.electric.database.hierarchy.View;
import com.sun.electric.database.id.CellId;
import com.sun.electric.database.prototype.NodeProto;
import com.sun.electric.database.prototype.PortCharacteristic;
import com.sun.electric.database.prototype.PortOriginal;
import com.sun.electric.database.prototype.PortProto;
import com.sun.electric.database.text.Name;
import com.sun.electric.database.text.TextUtils;
import com.sun.electric.database.topology.ArcInst;
import com.sun.electric.database.topology.Connection;
import com.sun.electric.database.topology.Geometric;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.topology.PortInst;
import com.sun.electric.database.variable.CodeExpression;
import com.sun.electric.database.variable.MutableTextDescriptor;
import com.sun.electric.database.variable.TextDescriptor;
import com.sun.electric.database.variable.Variable;
import com.sun.electric.technology.ArcProto;
import com.sun.electric.technology.PrimitiveNode;
import com.sun.electric.technology.Technology;
import com.sun.electric.technology.technologies.Artwork;
import com.sun.electric.technology.technologies.Generic;
import com.sun.electric.technology.technologies.Schematics;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.io.IOTool;
import com.sun.electric.tool.io.output.Spice;
import com.sun.electric.tool.lang.EvalJavaBsh;
import com.sun.electric.tool.routing.AutoStitch;
import com.sun.electric.tool.routing.AutoStitch.AutoOptions;
import com.sun.electric.tool.user.CircuitChangeJobs;
import com.sun.electric.util.math.FixpTransform;
import com.sun.electric.util.math.Orientation;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This class reads files in Sue files.
 */
public class Sue extends Input<Object>
{

	/** key of Variable holding SUE info. */		private static final Variable.Key SUE_ANNOTATION_KEY = Variable.newKey("ATTR_SUE_info");
	
	/*************** SUE EQUIVALENCES ***************/

	private static class SueExtraWire
	{
		private String  portName;
		private double  xOffset;
		private double  yOffset;

		private SueExtraWire(String portName, double xOffset, double yOffset)
		{
			this.portName = portName;
			this.xOffset = xOffset;
			this.yOffset = yOffset;
		}
	};

	private SueExtraWire [] transistorWires =
	{
		new SueExtraWire("d",  3, 0),
		new SueExtraWire("s", -3, 0),
		new SueExtraWire("g",  0, 4.5)
	};

	private SueExtraWire [] transistor4Wires =
	{
		new SueExtraWire("d",  3,     0),
		new SueExtraWire("s", -3,     0),
		new SueExtraWire("b", -0.25, -2.5),
		new SueExtraWire("g",  0,     4.5)
	};

	private SueExtraWire [] resistorWires =
	{
		new SueExtraWire("a", -3, 0),
		new SueExtraWire("b",  3, 0)
	};

	private SueExtraWire [] capacitorWires =
	{
		new SueExtraWire("a",  0,  1.75),
		new SueExtraWire("b",  0, -1.75)
	};

	private SueExtraWire [] inductorWires =
	{
		new SueExtraWire("a", 0,  3),
		new SueExtraWire("b", 0, -3)
	};

	private SueExtraWire [] twoPortWires =
	{
		new SueExtraWire("a", -11.25,  3.625),
		new SueExtraWire("b", -11.25, -3.625),
		new SueExtraWire("x",  11.25,  3.625),
		new SueExtraWire("y",  11.25, -3.625)
	};

	private static class SueEquiv
	{
		private String                 sueName;
		private NodeProto              intProto;
		private boolean                netateOutput;
		private int                    rotation;
		private boolean                transpose;
		private double                 xOffset;
		private double                 yOffset;
		private PrimitiveNode.Function detailFunct;
		private SueExtraWire        [] extraWires;

		private SueEquiv(String sueName, NodeProto intProto, boolean netateOutput, int rotation, boolean transpose,
			double xOffset, double yOffset, PrimitiveNode.Function detailFunct, SueExtraWire [] extraWires)
		{
			this.sueName = sueName;
			this.intProto = intProto;
			this.netateOutput = netateOutput;
			this.rotation = rotation;
			this.transpose = transpose;
			this.xOffset = xOffset;
			this.yOffset = yOffset;
			this.detailFunct = detailFunct;
			this.extraWires = extraWires;
		}
	};

	private SueEquiv [] sueEquivs =
	{
		//            name         primitive                        NEG     ANG       X     Y      FUNCTION                        EXTRA-WIRES
		new SueEquiv("pmos10",     Schematics.tech().transistorNode, false, 900,false, -2,    0,     PrimitiveNode.Function.TRAPMOS, transistorWires),
		new SueEquiv("nmos10",     Schematics.tech().transistorNode, false, 900,false, -2,    0,     PrimitiveNode.Function.TRANMOS, transistorWires),
		new SueEquiv("pmos4",      Schematics.tech().transistorNode, false, 900,false, -2,    0,     PrimitiveNode.Function.TRAPMOS, transistorWires),
		new SueEquiv("nmos4",      Schematics.tech().transistorNode, false, 900,false, -2,    0,     PrimitiveNode.Function.TRANMOS, transistorWires),
		new SueEquiv("pmos",       Schematics.tech().transistorNode, false, 900,false, -2,    0,     PrimitiveNode.Function.TRAPMOS, transistorWires),
		new SueEquiv("nmos",       Schematics.tech().transistorNode, false, 900,false, -2,    0,     PrimitiveNode.Function.TRANMOS, transistorWires),
		new SueEquiv("capacitor",  Schematics.tech().capacitorNode,  false,   0,false,  0,    0,     null,                           capacitorWires),
		new SueEquiv("resistor",   Schematics.tech().resistorNode,   false, 900,false,  0,    0,     null,                           resistorWires),
		new SueEquiv("inductor",   Schematics.tech().inductorNode,   false,   0,false,  0,    0,     null,                           inductorWires),
		new SueEquiv("cccs",       Schematics.tech().twoportNode,    false,   0,false,  1.25,-6.875, PrimitiveNode.Function.CCCS,    twoPortWires),
		new SueEquiv("ccvs",       Schematics.tech().twoportNode,    false,   0,false,  1.25,-6.875, PrimitiveNode.Function.CCVS,    twoPortWires),
		new SueEquiv("vcvs",       Schematics.tech().twoportNode,    false,   0,false,  1.25,-6.875, PrimitiveNode.Function.VCVS,    twoPortWires),
		new SueEquiv("vccs",       Schematics.tech().twoportNode,    false,   0,false, -1.875,-5,    PrimitiveNode.Function.VCCS,    null)
	};

	private SueEquiv [] sueEquivs4 =
	{
		//            name         primitive                         NEG     ANG       X     Y      FUNCTION                        EXTRA-WIRES
		new SueEquiv("pmos10",     Schematics.tech().transistor4Node, false,   0,true,  -2,    0,     PrimitiveNode.Function.TRAPMOS, transistor4Wires),
		new SueEquiv("nmos10",     Schematics.tech().transistor4Node, false, 900,false, -2,    0,     PrimitiveNode.Function.TRANMOS, transistor4Wires),
		new SueEquiv("pmos4",      Schematics.tech().transistor4Node, false,   0,true,  -2,    0,     PrimitiveNode.Function.TRAPMOS, transistor4Wires),
		new SueEquiv("nmos4",      Schematics.tech().transistor4Node, false, 900,false, -2,    0,     PrimitiveNode.Function.TRANMOS, transistor4Wires),
		new SueEquiv("pmos",       Schematics.tech().transistor4Node, false,   0,true,  -2,    0,     PrimitiveNode.Function.TRAPMOS, transistor4Wires),
		new SueEquiv("nmos",       Schematics.tech().transistor4Node, false, 900,false, -2,    0,     PrimitiveNode.Function.TRANMOS, transistor4Wires),
		new SueEquiv("capacitor",  Schematics.tech().capacitorNode,   false,   0,false,  0,    0,     null,                           capacitorWires),
		new SueEquiv("resistor",   Schematics.tech().resistorNode,    false, 900,false,  0,    0,     null,                           resistorWires),
		new SueEquiv("inductor",   Schematics.tech().inductorNode,    false,   0,false,  0,    0,     null,                           inductorWires),
		new SueEquiv("cccs",       Schematics.tech().twoportNode,     false,   0,false,  1.25,-6.875, PrimitiveNode.Function.CCCS,    twoPortWires),
		new SueEquiv("ccvs",       Schematics.tech().twoportNode,     false,   0,false,  1.25,-6.875, PrimitiveNode.Function.CCVS,    twoPortWires),
		new SueEquiv("vcvs",       Schematics.tech().twoportNode,     false,   0,false,  1.25,-6.875, PrimitiveNode.Function.VCVS,    twoPortWires),
		new SueEquiv("vccs",       Schematics.tech().twoportNode,     false,   0,false, -1.875,-5,    PrimitiveNode.Function.VCCS,    null)
	};

	/*************** SUE WIRES ***************/

	private static class SueWire
	{
		private Point2D  [] pt;
		private PortInst [] pi;
		private ArcProto    proto;

		private SueWire()
		{
			pt = new Point2D[2];
			pi = new PortInst[2];
		}
	};

	/*************** SUE NETWORKS ***************/

	private static class SueNet
	{
		private Point2D  pt;
		private String   label;
	};

	private String         sueLastLine;
	private String         lastLineRead;
	private List<String>   sueDirectories;
	private SuePreferences localPrefs;
	private Job            sueJob;
	private Technology     curTech;

	public static class SuePreferences extends InputPreferences
	{
		public boolean use4PortTransistors;
		public boolean convertExpressions;

		public SuePreferences(boolean factory) {
            super(factory);
            if (!factory)
            {
                use4PortTransistors = IOTool.isSueUses4PortTransistors();
                convertExpressions = IOTool.isSueConvertsExpressions();
            }
        }

		@Override
		public Library doInput(URL fileURL, Library lib, Technology tech, EditingPreferences ep, Map<Library,Cell> currentCells,
			Map<CellId,BitSet> nodesToExpand, Job job)
		{
			Sue in = new Sue(ep, this, job);
			if (in.openTextInput(fileURL)) return null;
			lib = in.importALibrary(lib, tech, currentCells);
			in.closeInput();
			return lib;
		}
	}

	/**
	 * Creates a new instance of Sue.
	 */
	Sue(EditingPreferences ep, SuePreferences ap, Job job) {
        super(ep);
        localPrefs = ap;
        sueJob = job;
    }

	/**
	 * Method to import a library from disk.
	 * @param lib the library to fill
	 * @param currentCells this map will be filled with currentCells in Libraries found in library file
	 * @return the created library (null on error).
	 */
	protected Library importALibrary(Library lib, Technology tech, Map<Library,Cell> currentCells)
	{
		// determine the cell name
		String cellName = lib.getName();
		curTech = tech;

		// initialize the number of directories that need to be searched
		sueDirectories = new ArrayList<String>();

		// determine the current directory
		String topDirName = TextUtils.getFilePath(lib.getLibFile());
		sueDirectories.add(topDirName);

		// find all subdirectories that start with "suelib_" and include them in the search
		File topDir = new File(topDirName);
		String [] fileList = topDir.list();
		String keySueDir = "suelib"; // suelib_ 
		for(int i=0; i<fileList.length; i++)
		{
			if (!fileList[i].startsWith(keySueDir)) continue;
			String dirName = topDirName + fileList[i];
			if (!dirName.endsWith("/")) dirName += "/";
			File subDir = new File(dirName);
			if (subDir.isDirectory()) sueDirectories.add(dirName);
		}

		// see if the current directory is inside of a SUELIB
		int lastSep = topDirName.lastIndexOf('/');
		if (lastSep >= 0 && topDirName.substring(lastSep+1).startsWith(keySueDir))
		{
			String upDirName = topDirName.substring(0, lastSep);
			File upperDir = new File(upDirName);
			String [] upFileList = upperDir.list();
			for(int i=0; i<upFileList.length; i++)
			{
				if (!upFileList[i].startsWith(keySueDir)) continue;
				String dirName = upDirName + upFileList[i];
				File subDir = new File(dirName);
				if (subDir.isDirectory()) sueDirectories.add(dirName);
			}
		}

		// read the file
		try
		{
			Cell topCell = readFile(lib, cellName, lineReader);
			if (topCell == null) return null;
			currentCells.put(lib, topCell);
		} catch (IOException e)
		{
			System.out.println("ERROR reading Sue libraries");
		}

		return lib;
	}

	/**
	 * Method to read the SUE file.
	 */
	private Cell readFile(Library lib, String cellName, LineNumberReader lr)
		throws IOException
	{
		boolean placeIcon = false;
		List<SueWire> sueWires = new ArrayList<SueWire>();
		List<SueNet> sueNets = new ArrayList<SueNet>();
		Cell cell = null;
		Cell schemCell = null;
		Cell iconCell = null;
		lastLineRead = null;
		Point2D iconPt = null;
		List<String> argumentKey = new ArrayList<String>();
		List<String> argumentValue = new ArrayList<String>();
		Set<NodeInst> invertNodeOutput = new HashSet<NodeInst>();
		Map<String,List<NodeInst>> duplicateNames = new HashMap<String,List<NodeInst>>();
		for(;;)
		{
			// get the next line of text
			List<String> keywords = getNextLine(lr);
			if (keywords == null) break;
			int count = keywords.size();
			if (count == 0) continue;
			String keyword0 = keywords.get(0);

			// handle "proc" for defining views
			if (keyword0.equalsIgnoreCase("proc"))
			{
				// write any wires from the last proc
				if (cell != null)
				{
					placeWires(sueWires, sueNets, cell, invertNodeOutput);
					placeNets(sueNets, cell);
					sueWires = new ArrayList<SueWire>();
					sueNets = new ArrayList<SueNet>();
				}

				if (count < 2)
				{
					String msg = "Cell " + cellName + ", line " + lr.getLineNumber() +
							": 'proc' is missing arguments: " + lastLineRead;
					System.out.println(msg);
					errorLogger.logError(msg, cell, -1);
					continue;
				}

				String keyword1 = keywords.get(1);
				if (keyword1.startsWith("SCHEMATIC_"))
				{
					// create the schematic cell
					String subCellName = keyword1.substring(10);
					if (subCellName.equalsIgnoreCase("[get_file_name]"))
						subCellName = cellName;
					subCellName += "{sch}";
					schemCell = cell = Cell.makeInstance(ep, lib, subCellName);
					placeIcon = false;
				} else if (keyword1.startsWith("ICON_"))
				{
					// create the icon cell
					String subCellName = keyword1.substring(5);
					if (subCellName.equalsIgnoreCase("[get_file_name]"))
						subCellName = cellName;
					subCellName += "{ic}";
					iconCell = cell = Cell.makeInstance(ep, lib, subCellName);
					iconCell.setTechnology(Artwork.tech());
				} else
				{
					String msg = "Cell " + cellName + ", line " + lr.getLineNumber() +
							": unknown 'proc' statement: " + lastLineRead;
					System.out.println(msg);
					errorLogger.logError(msg, cell, -1);
				}
				continue;
			}

			// handle "make" for defining components
			if (keyword0.equalsIgnoreCase("make"))
			{
				if (count < 2)
				{
					String msg = "Cell " + cellName + ", line " + lr.getLineNumber() +
							": 'make' is missing arguments: " + lastLineRead;
					System.out.println(msg);
					errorLogger.logError(msg, cell, -1);
					continue;
				}

				// extract parameters
				ParseParameters parP = new ParseParameters(keywords, 2);

				// ignore self-references
				String keyword1 = keywords.get(1);
				if (keyword1.equalsIgnoreCase(cellName))
				{
					if (parP.pt != null)
					{
						// queue icon placement
						iconPt = parP.pt;
						placeIcon = true;
					}
					continue;
				}

				// special case for network names: queue them
				if (keyword1.equalsIgnoreCase("name_net_m") ||
					keyword1.equalsIgnoreCase("name_net_s") ||
					keyword1.equalsIgnoreCase("name_net"))
				{
					if (parP.theName != null)
					{
						SueNet sn = new SueNet();
						sn.pt = parP.pt;
						sn.label = parP.theName;
						sueNets.add(sn);
					}
					continue;
				}

				// first check for special names
				NodeProto proto = null;
				double xOff = 0, yOff = 0;
				PortCharacteristic type = PortCharacteristic.UNKNOWN;
				double xShrink = 0, yShrink = 0;
				boolean invertOutput = false;
				int rotation = 0;
				boolean transpose = false;
				PrimitiveNode.Function detailFunct = null;
				SueExtraWire [] extraWires = null;
				if (keyword1.equalsIgnoreCase("inout"))
				{
					proto = Schematics.tech().offpageNode;
					FixpTransform trans = Orientation.fromC(parP.rot, parP.trn).pureRotate();
					Point2D offPt = new Point2D.Double(2, 0);
					trans.transform(offPt, offPt);
					xOff = offPt.getX();   yOff = offPt.getY();
					type = PortCharacteristic.BIDIR;
				} else if (keyword1.equalsIgnoreCase("input"))
				{
					proto = Schematics.tech().offpageNode;
					FixpTransform trans = Orientation.fromC(parP.rot, parP.trn).pureRotate();
					Point2D offPt = new Point2D.Double(-2, 0);
					trans.transform(offPt, offPt);
					xOff = offPt.getX();   yOff = offPt.getY();
					type = PortCharacteristic.IN;
				} else if (keyword1.equalsIgnoreCase("output"))
				{
					proto = Schematics.tech().offpageNode;
					FixpTransform trans = Orientation.fromC(parP.rot, parP.trn).pureRotate();
					Point2D offPt = new Point2D.Double(2, 0);
					trans.transform(offPt, offPt);
					xOff = offPt.getX();   yOff = offPt.getY();
					type = PortCharacteristic.OUT;
				} else if (keyword1.equalsIgnoreCase("rename_net"))
				{
					proto = Schematics.tech().wirePinNode;
				} else if (keyword1.equalsIgnoreCase("global"))
				{
					Name busName = Name.findName(parP.theName);
					int busWidth = busName.busWidth();
					if (busWidth > 1) proto = Schematics.tech().busPinNode; else
					{
						proto = Schematics.tech().wirePinNode;
						if (parP.theName.equalsIgnoreCase("gnd"))
						{
							FixpTransform trans = Orientation.fromC(parP.rot, parP.trn).pureRotate();
							Point2D offPt = new Point2D.Double(0, -2);
							trans.transform(offPt, offPt);
							xOff = offPt.getX();   yOff = offPt.getY();
							proto = Schematics.tech().groundNode;
							type = PortCharacteristic.GND;
						}
						if (parP.theName.equalsIgnoreCase("vdd"))
						{
							proto = Schematics.tech().powerNode;
							type = PortCharacteristic.PWR;
						}
					}
				} else if (keyword1.equalsIgnoreCase("join_net"))
				{
					proto = Schematics.tech().wireConNode;
					xShrink = -2;
					FixpTransform trans = Orientation.fromC(parP.rot, parP.trn).pureRotate();
					Point2D offPt = new Point2D.Double(1.25, 0);
					trans.transform(offPt, offPt);
					xOff = offPt.getX();   yOff = offPt.getY();
				}

				// now check for internal associations to known primitives
				if (proto == null)
				{
					SueEquiv [] curEquivs = sueEquivs;
					if (localPrefs.use4PortTransistors) curEquivs = sueEquivs4;
					int i = 0;
					for( ; i < curEquivs.length; i++)
						if (keyword1.equalsIgnoreCase(curEquivs[i].sueName)) break;
					if (i < curEquivs.length)
					{
						proto = curEquivs[i].intProto;
						invertOutput = curEquivs[i].netateOutput;
						rotation = curEquivs[i].rotation;
						transpose = curEquivs[i].transpose;
						FixpTransform trans = Orientation.fromC(parP.rot, parP.trn).pureRotate();
						Point2D offPt = new Point2D.Double(curEquivs[i].xOffset, curEquivs[i].yOffset);
						trans.transform(offPt, offPt);
						xOff = offPt.getX();   yOff = offPt.getY();

						if (transpose)
						{
							parP.trn = !parP.trn;
							parP.rot = rotation - parP.rot;
							if (parP.rot < 0) parP.rot += 3600;
						} else
						{
							parP.rot += rotation;
							if (parP.rot >= 3600) parP.rot -= 3600;
						}
						detailFunct = curEquivs[i].detailFunct;
						extraWires = curEquivs[i].extraWires;
					}
				}

				// now check for references to cells
				if (proto == null)
				{
					// find node or read it from disk
					proto = getNodeProto(lib, keyword1);
					if (proto == null)
					{
						// Search first in Electric Libraries already open
						proto = Library.findCellInLibraries(keyword1, View.ICON, null);
						if (proto == null) // not found yet
							proto = readFromDisk(lib, keyword1);
					}

					// set proper offsets for the cell
					if (proto != null && ((Cell)proto).isSchematic())
					{
						Cell np = ((Cell)proto).iconView();
						if (np != null) proto = np;
					}
				}

				// ignore "title" specifications
				if (keyword1.startsWith("title_")) continue;

				// stop now if SUE node is unknown
				if (proto == null)
				{
					String msg = "Cell " + cellName + ", line " + lr.getLineNumber() +
							", cannot create instance of " + keyword1 + " (no Sue object with that name)";
					System.out.println(msg);
					errorLogger.logError(msg, cell, -1);
					continue;
				}

				// create the instance
				double wid = proto.getDefWidth(ep);
				double hei = proto.getDefHeight(ep);
				if (proto instanceof Cell)
				{
					Rectangle2D bounds = ((Cell)proto).getBounds();
					wid = bounds.getWidth();
					hei = bounds.getHeight();
				}
				wid -= xShrink;
				hei -= yShrink;
				Orientation or = Orientation.fromC(parP.rot, parP.trn);
				parP.rot = or.getAngle();
				NodeInst ni = NodeInst.makeInstance(proto, ep, new Point2D.Double(parP.pt.getX() + xOff, parP.pt.getY() + yOff), wid, hei, cell,
					or, null, detailFunct);
				if (ni == null) continue;
				if (invertOutput) invertNodeOutput.add(ni);

				// add any extra wires to the node
				if (extraWires != null)
				{
					for(int i=0; i<extraWires.length; i++)
					{
						PortProto pp = proto.findPortProto(extraWires[i].portName);
						if (pp == null) continue;
						PortInst pi = ni.findPortInstFromProto(pp);
						Poly portPoly = pi.getPoly();
						double x = portPoly.getCenterX();
						double y = portPoly.getCenterY();
						FixpTransform trans = ni.getOrient().pureRotate();
						Point2D dPt = new Point2D.Double(extraWires[i].xOffset, extraWires[i].yOffset);
						trans.transform(dPt, dPt);
						PrimitiveNode wirePin = Schematics.tech().wirePinNode;
						double pinx = x + dPt.getX();
						double piny = y + dPt.getY();
						PortInst ppi = findPinNode(pinx, piny, cell);
						if (ppi == null)
						{
							NodeInst nni = NodeInst.makeInstance(Schematics.tech().wirePinNode, ep, new Point2D.Double(pinx, piny),
								wirePin.getDefWidth(ep), wirePin.getDefHeight(ep), cell);
							if (nni == null) continue;
							ppi = nni.getOnlyPortInst();
						}
						ArcInst ai = ArcInst.makeInstanceBase(Schematics.tech().wire_arc, ep, 0, pi, ppi);
						if (ai == null)
						{
							String msg = "Cell " + cellName + ", line " + lr.getLineNumber() +
									", error adding extra wires to node " + keyword1;
							System.out.println(msg);
							errorLogger.logError(msg, ni, cell, null, -1);
							break;
						}
						if (x != pinx && y != piny) ai.setFixedAngle(false);
					}
				}

				// handle names assigned to the node
				if (parP.theName != null)
				{
					// export a port if this is an input, output, bidirectional
					if (proto == Schematics.tech().offpageNode && parP.theName != null)
					{
						Iterator<PortInst> it = ni.getPortInsts();
						PortInst pi = it.next();
						if (keyword1.equalsIgnoreCase("output")) pi = it.next();
						Export ppt = newExport(cell, pi, parP.theName, type);
						if (ppt == null)
						{
							String msg = "Cell " + cellName + ", line " + lr.getLineNumber() +
								", could not create export " + parP.theName;
							errorLogger.logError(msg, ni, cell, null, -1);
						}
					} else
					{
						// just name the node
						NodeInst oNi = cell.findNode(parP.theName);
						if (oNi == null) 
						{
							if(ni.setName(parP.theName))
							{
								String msg = "Setting node with default name '" + ni.getName() + "' as '" + parP.theName + "' is an invalid name.";
								System.out.println(msg);
								errorLogger.logError(msg, ni, cell, null, -1);
							}
						}
						else
						{
							List<NodeInst> dups = duplicateNames.get(parP.theName);
							if (dups == null)
							{
								dups = new ArrayList<NodeInst>();
								dups.add(ni);
								duplicateNames.put(parP.theName, dups);
							}
							dups.add(oNi);
						}
					}
				}

				// count the variables
				int varCount = 0;
				for(int i=2; i<count; i += 2)
				{
					String keyword = keywords.get(i);
					if (!keyword.startsWith("-")) continue;
					if (keyword.equalsIgnoreCase("-origin") ||
						keyword.equalsIgnoreCase("-orient") ||
						keyword.equalsIgnoreCase("-type") ||
						keyword.equalsIgnoreCase("-name")) continue;
					varCount++;
				}

				// add variables
				int varIndex = 1;
				double varOffset = ni.getYSize() / (varCount+1);
				for(int i=2; i<count; i += 2)
				{
					String keyword = keywords.get(i);
					if (!keyword.startsWith("-")) continue;
					if (keyword.equalsIgnoreCase("-origin") ||
						keyword.equalsIgnoreCase("-orient") ||
						keyword.equalsIgnoreCase("-type") ||
						keyword.equalsIgnoreCase("-name")) continue;

					boolean halveSize = false;
					boolean isParam = false;
					double xpos = 0, ypos = 0;
					String sueVarName = null;
					TextDescriptor.Unit unit = null;
					if (keyword.charAt(1) == 'w')
					{
						sueVarName = "ATTR_width";
						xpos = 2;
						ypos = -4;
					} else if (keyword.charAt(1) == 'l')
					{
						sueVarName = "ATTR_length";
						xpos = -2;
						ypos = -4;
						halveSize = true;
					} else if (keyword.substring(1).equals("capacitance") && proto == Schematics.tech().capacitorNode)
					{
						sueVarName = Schematics.SCHEM_CAPACITANCE.getName();
						unit = TextDescriptor.Unit.CAPACITANCE;
					} else if (keyword.substring(1).equals("resistance") && proto == Schematics.tech().resistorNode)
					{
						sueVarName = Schematics.SCHEM_RESISTANCE.getName();
						unit = TextDescriptor.Unit.RESISTANCE;
					} else if (keyword.substring(1).equals("inductance") && proto == Schematics.tech().inductorNode)
					{
						sueVarName = Schematics.SCHEM_INDUCTANCE.getName();
						unit = TextDescriptor.Unit.INDUCTANCE;
					}
					else
					{
						sueVarName = "ATTR_" + keyword.substring(1);
						if (keyword.substring(1).equals("capacitance"))
							unit = TextDescriptor.Unit.CAPACITANCE;
						else if (keyword.substring(1).equals("resistance"))
							unit = TextDescriptor.Unit.RESISTANCE;
						else if (keyword.substring(1).equals("inductance"))
							unit = TextDescriptor.Unit.INDUCTANCE;
								
						if (sueVarName.indexOf(' ') >= 0)
						{
							String msg = "Cell " + cellName + ", line " + lr.getLineNumber() +
									", bad variable name: " + sueVarName;
							System.out.println(msg);
							errorLogger.logError(msg, ni, cell, null, -1);
							break;
						}
						xpos = 0;
						ypos = ni.getYSize() / 2 - varIndex * varOffset;
						isParam = true;
					}
					Object newObject = null;
					String pt = keywords.get(i+1);
					
					if (keyword.charAt(1) == 'W' && keyword.length() > 2)
					{
						newObject = keyword.substring(2) + ":" + parseExpression(pt);
					} else
					{
						int len = pt.length();
						if (pt.toLowerCase().endsWith("uf") || pt.toLowerCase().endsWith("nf"))
						{
							// Farah as unit
							pt = pt.substring(0, len);
							len--;
						}
						
						TextUtils.UnitScale scale = null;
						
						if (Character.toLowerCase(pt.charAt(len-1)) == 'u')
							scale = TextUtils.UnitScale.MICRO;
						else if (Character.toLowerCase(pt.charAt(len-1)) == 'n')
							scale = TextUtils.UnitScale.NANO;
						else if (Character.toLowerCase(pt.charAt(len-1)) == 'm')
							scale = TextUtils.UnitScale.MILLI;
						
						// found a valid scale
						if (scale != null)
						{
							// unit == null is the original code
							if (unit == null || unit == TextDescriptor.Unit.DISTANCE)
								newObject = new Double(TextUtils.convertFromDistance(TextUtils.atof(pt), curTech, scale));
							else
								newObject = new Double(TextUtils.atof(pt.substring(0, len)));
						}
							
						if (newObject == null && TextUtils.isANumber(pt))
						{
							newObject = new Integer(TextUtils.atoi(pt));
							if (pt.indexOf('.') >= 0 || pt.toLowerCase().indexOf('e') >= 0)
							{
								newObject = new Double(TextUtils.atof(pt));
							}
						}
						if (newObject == null)
						{
							newObject = parseExpression(pt);
						}
					}

					// see if the string should be Java code or Spice
					CodeExpression.Code code = CodeExpression.Code.NONE;
					
					if (newObject instanceof String)
					{
						if (EvalJavaBsh.evalJavaBsh.isValidJava((String)newObject))
							code = CodeExpression.Code.JAVA;
						else if (((String)newObject).startsWith("@"))
							code = CodeExpression.Code.SPICE;
					}

					Variable.Key varKey = Variable.newKey(sueVarName);
					MutableTextDescriptor mtd = new MutableTextDescriptor(ep.getNodeTextDescriptor());
					varIndex++;
					mtd.setOff(xpos, ypos);
					if (halveSize)
					{
						if (mtd.getSize().isAbsolute())
							mtd.setAbsSize((int)(mtd.getSize().getSize() / 2)); else
								mtd.setRelSize(mtd.getSize().getSize() / 2);
					}
					if (unit != null)
						mtd.setUnit(unit);
					if (isParam)
					{
						mtd.setParam(true);
						mtd.setDispPart(TextDescriptor.DispPos.NAMEVALUE);
					}
					Object instObject = newObject;
					if (code != CodeExpression.Code.NONE)
						instObject = Variable.withCode(newObject, code);
					
					ni.newVar(varKey, instObject, TextDescriptor.newTextDescriptor(mtd));

					// make sure the parameter exists in the cell definition
					NodeProto np = ni.getProto();
					if (isParam && ni.isCellInstance())
					{
						Cell cnp = ((Cell)np).contentsView();
						if (cnp == null) cnp = (Cell)np;
						Variable contentsVar = cnp.getParameterOrVariable(varKey);
						if (contentsVar == null && cnp.getCellGroup() != null)
						{
							// really wanted: VTDISPLAYNAMEVALINH
							TextDescriptor td = ep.getCellTextDescriptor().withParam(true).
								withDispPart(TextDescriptor.DispPos.NAMEVALUE);
							newObject = Variable.withCode(newObject, CodeExpression.Code.SPICE);
							Variable newVar = Variable.newInstance(varKey, newObject, td).withInherit(true);
							if (unit != null)
								newVar = newVar.withUnit(unit);
							cnp.getCellGroup().addParam(newVar);
						}
					}
				}
				continue;
			}

			// handle "make_wire" for defining arcs
			if (keyword0.equalsIgnoreCase("make_wire"))
			{
				SueWire sw = new SueWire();
				double fx = convertXCoord(TextUtils.atof(keywords.get(1)));
				double fy = convertYCoord(TextUtils.atof(keywords.get(2)));
				sw.pt[0] = new Point2D.Double(fx, fy);
				double tx = convertXCoord(TextUtils.atof(keywords.get(3)));
				double ty = convertYCoord(TextUtils.atof(keywords.get(4)));
				sw.pt[1] = new Point2D.Double(tx, ty);
				sueWires.add(sw);
				continue;
			}

			// handle "icon_term" for defining ports in icons
			if (keyword0.equalsIgnoreCase("icon_term"))
			{
				ParseParameters parP = new ParseParameters(keywords, 1);
				NodeProto proto = Schematics.tech().busPinNode;
				double pX = proto.getDefWidth(ep);
				double pY = proto.getDefHeight(ep);
				NodeInst ni = NodeInst.makeInstance(proto, ep, parP.pt, pX, pY, cell);
				if (ni == null) continue;

				PortInst pi = ni.getOnlyPortInst();
				Export ppt = Export.newInstance(cell, pi, parP.theName, ep, parP.portType);
				if (ppt == null)
				{
					String msg = "Cell " + cellName + ", line " + lr.getLineNumber() +
							", could not create port " + parP.theName;
					System.out.println(msg);
					errorLogger.logError(msg, ni, cell, null, -1);
				}
				continue;
			}

			// handle "icon_arc" for defining icon curves
			if (keyword0.equalsIgnoreCase("icon_arc"))
			{
				if (count != 9)
				{
					System.out.println("Cell " + cellName + ", line " + lr.getLineNumber() +
						": needs 9 arguments, has " + count + ": " + lastLineRead);
					continue;
				}
				int start = 0;   int extent = 359;
				double p1X = convertXCoord(TextUtils.atof(keywords.get(1)));
				double p1Y = convertYCoord(TextUtils.atof(keywords.get(2)));
				double p2X = convertXCoord(TextUtils.atof(keywords.get(3)));
				double p2Y = convertYCoord(TextUtils.atof(keywords.get(4)));
				if (keywords.get(5).equals("-start")) start = TextUtils.atoi(keywords.get(6));
				if (keywords.get(7).equals("-extent")) extent = TextUtils.atoi(keywords.get(8));

				double sX = Math.abs(p1X - p2X);
				double sY = Math.abs(p1Y - p2Y);
				Point2D ctr = new Point2D.Double((p1X+p2X)/2, (p1Y+p2Y)/2);

				NodeInst ni = NodeInst.makeInstance(Artwork.tech().circleNode, ep, ctr, sX, sY, cell);
				if (ni == null) continue;
				if (extent != 359)
				{
					if (extent < 0)
					{
						start += extent;
						extent = -extent;
					}
					double rExtent = extent+1;
					rExtent = rExtent * Math.PI / 180.0;
					double rstart = start * Math.PI / 180.0;
					ni.setArcDegrees(rstart, rExtent, ep);
				}
				continue;
			}

			// handle "icon_line" for defining icon outlines
			if (keyword0.equalsIgnoreCase("icon_line"))
			{
				List<Point2D> pointList = new ArrayList<Point2D>();
				double x = 0;
				for(int i=1; i<keywords.size(); i++)
				{
					if (keywords.get(i).equals("-tags")) break;
					if ((i%2) != 0)
					{
						x = convertXCoord(TextUtils.atof(keywords.get(i)));
					} else
					{
						double y = convertYCoord(TextUtils.atof(keywords.get(i)));
						pointList.add(new Point2D.Double(x, y));
					}
				}
				int keyCount = pointList.size();
				if (keyCount == 0) continue;

				// determine bounds of icon
				Point2D firstPt = pointList.get(0);
				double lX = firstPt.getX();
				double hX = lX;
				double lY = firstPt.getY();
				double hY = lY;
				for(int i=1; i<keyCount; i++)
				{
					Point2D nextPt = pointList.get(i);
					if (nextPt.getX() < lX) lX = nextPt.getX();
					if (nextPt.getX() > hX) hX = nextPt.getX();
					if (nextPt.getY() < lY) lY = nextPt.getY();
					if (nextPt.getY() > hY) hY = nextPt.getY();
				}
				double cX = (lX + hX) / 2;
				double cY = (lY + hY) / 2;
				Point2D ctr = new Point2D.Double(cX, cY);
				NodeInst ni = NodeInst.makeInstance(Artwork.tech().openedPolygonNode, ep, ctr, hX-lX, hY-lY, cell);
				if (ni == null) return null;
				EPoint [] points = new EPoint[keyCount];
				for(int i=0; i<keyCount; i++)
				{
					Point2D pt = pointList.get(i);
					points[i] = EPoint.fromLambda(pt.getX(), pt.getY());
				}
				ni.setTrace(points);
				continue;
			}

			// handle "icon_setup" for defining variables
			if (keyword0.equalsIgnoreCase("icon_setup"))
			{
				// extract parameters
				String keyword1 = keywords.get(1);
				if (!keyword1.equalsIgnoreCase("$args"))
				{
					String msg = "Cell " + cellName + ", line " + lr.getLineNumber() +
							": has unrecognized 'icon_setup'";
					System.out.println(msg);
					errorLogger.logError(msg, cell, -1);
					continue;
				}
				String pt = keywords.get(2);
				int ptLen = pt.length();
				int ptPos = 0;
				if (ptPos < ptLen && pt.charAt(ptPos) == '{') ptPos++;
				for(;;)
				{
					while (ptPos < ptLen && pt.charAt(ptPos) == ' ') ptPos++;
					if (ptPos >= ptLen || pt.charAt(ptPos) == '}') break;

					// collect up to a space or close curly
					int argStart = ptPos;
					int curly = 0;
					while (ptPos < ptLen)
					{
						char chr = pt.charAt(ptPos);
						if (curly == 0)
						{
							if (chr == ' ' || chr == '}') break;
						}
						if (chr == '{') curly++;
						if (chr == '}') curly--;
						ptPos++;
					}
					String arg = pt.substring(argStart, ptPos++);

					// parse the argument into key and value
					int argPos = 0;
					int argLen = arg.length();
					if (argPos < argLen && arg.charAt(argPos) == '{')
					{
						argPos++;
						if (arg.endsWith("}")) arg = arg.substring(0, --argLen);
					}
					int keyStart = argPos;
					while (argPos < argLen && arg.charAt(argPos) != ' ') argPos++;
					String key = arg.substring(keyStart, argPos);
					while (argPos < argLen && arg.charAt(argPos) == ' ') argPos++;
					String value = arg.substring(argPos);
					if (value.startsWith("{"))
					{
						value = value.substring(1);
						if (value.endsWith("}")) value = value.substring(0, value.length()-1);
					}
					argumentKey.add(key);
					argumentValue.add(value);
				}
				continue;
			}

			// handle "icon_property" for defining icon strings
			if (keyword0.equalsIgnoreCase("icon_property"))
			{
				// extract parameters
				ParseParameters parP = new ParseParameters(keywords, 1);
				String info = parP.theLabel;
				
				// Deal with label information
				if (info != null)
				{
					// substitute parameters
					StringBuffer infstr = new StringBuffer();
					for(int i=0; i<info.length(); i++)
					{
						char chr = info.charAt(i);
						if (chr == '$')
						{
							String partial = info.substring(i+1);
							int j = 0;
							for( ; j<argumentKey.size(); j++)
							{
								String key = argumentKey.get(j);
								if (partial.startsWith(key)) break;
							}
							if (j < argumentKey.size())
							{
								infstr.append(argumentValue.get(j));
								i += argumentKey.get(j).length();
								continue;
							}
						}
						infstr.append(chr);
					}
					info = infstr.toString();
	
					NodeInst ni = NodeInst.makeInstance(Generic.tech().invisiblePinNode, ep, parP.pt, 0, 0, cell);
					if (ni != null)
						ni.newDisplayVar(Artwork.ART_MESSAGE, info, ep);
				}
				
				// get possible resistance/capacitance. They must be placed in the schematics/icons
				if (parP.instructionType == InstructionType.USER && parP.theDefault != null)
				{
					TextDescriptor.Unit unit = null;
					String pt = parP.theDefault;
					int len = pt.length();
					Object newObject = null;
					
					if (parP.theName.equals("resistance"))
					{
						unit = TextDescriptor.Unit.RESISTANCE;
					}
					else if (parP.theName.equals("capacitance"))
					{
						unit = TextDescriptor.Unit.CAPACITANCE;
						if (pt.endsWith("F"))
						{
							pt = pt.substring(0, len);
							len--;
						}
					}
					else if (parP.theName.equals("inductance"))
					{
						unit = TextDescriptor.Unit.INDUCTANCE;
						if (pt.endsWith("H"))
						{
							pt = pt.substring(0, len);
							len--;
						}
					}
					if (unit != null) // found variable
					{
						String sueVarName = "ATTR_" + parP.theName;
						newObject = new Double(TextUtils.atof(pt.substring(0, len))); // *scale.getMultiplier().doubleValue());
						Variable.Key varKey = Variable.newKey(sueVarName);
						MutableTextDescriptor mtd = new MutableTextDescriptor(ep.getNodeTextDescriptor());
						mtd.setUnit(unit);
						mtd.setParam(true);
						mtd.setDispPart(TextDescriptor.DispPos.NAMEVALUE);
						Cell cnp = cell; // cell is the icon cell in this case
						Variable contentsVar = cnp.getParameterOrVariable(varKey);
						
						if (contentsVar == null && cnp.getCellGroup() != null)
						{
							// really wanted: VTDISPLAYNAMEVALINH
							TextDescriptor td = ep.getCellTextDescriptor().withParam(true).
								withDispPart(TextDescriptor.DispPos.NAMEVALUE);
							newObject = Variable.withCode(newObject, CodeExpression.Code.SPICE);
							Variable newVar = Variable.newInstance(varKey, newObject, td).withInherit(true);
							newVar = newVar.withUnit(unit);
							cnp.getCellGroup().addParam(newVar);
						}
					}
				}
				
				// get allegro information as separate variables
				if (parP.instructionType == InstructionType.FIXED)
				{
					// this is sort of a hack for Allegro case to have better control of the pins
					// break the line per pin for better editing.
					ERectangle rect = iconCell.getBounds();
					if (parP.theName.contains("allegro_pins"))
					{
						// header
						info = "-name " + parP.theName + " -text ";
						CircuitChangeJobs.MakeCellAnnotationJob.addAnnotation(iconCell, SUE_ANNOTATION_KEY, 
								info, rect, TextDescriptor.Position.DOWN, ep);
						// break per pin data
						int pos = 0;
						int len = parP.theText.length();
						while (pos < len)
						{
							// look for set of elements {name pins type}
							int start = parP.theText.indexOf("{", pos);
							if (start == -1) break; // done
							if (start < len && parP.theText.charAt(start) == '{') start++;
							// look for matching '}' discarding intermediate pairs of {}
							assert(start > 0); // otherwise I can't do start-1
							int end = start;
							int numCurly = 1;
							while (end < len && numCurly != 0)
							{
								if (parP.theText.charAt(end) == '{') numCurly++;
								else if (parP.theText.charAt(end) == '}') numCurly--;
								end++;
							}
							String elem = parP.theText.substring(start-1, end).trim(); // trim in case there are empty space at the end or start
							pos = end++; // ++ to remove the last '}'
							CircuitChangeJobs.MakeCellAnnotationJob.addAnnotation(iconCell, SUE_ANNOTATION_KEY, 
									elem, rect, TextDescriptor.Position.DOWN, ep);
						}
					}
					else
					{
						info = "-name " + parP.theName + " -text " + parP.theText;
						CircuitChangeJobs.MakeCellAnnotationJob.addAnnotation(iconCell, SUE_ANNOTATION_KEY, 
								info, rect, TextDescriptor.Position.DOWN, ep);
					}
				}
				continue;
			}

			// handle "make_text" for placing strings
			if (keyword0.equalsIgnoreCase("make_text"))
			{
				// extract parameters
				ParseParameters parP = new ParseParameters(keywords, 1);
				if (parP.theText == null) continue;

				NodeInst ni = NodeInst.makeInstance(Generic.tech().invisiblePinNode, ep, parP.pt, 0, 0, cell);
				if (ni == null) continue;
				if (parP.theText.startsWith("^"))
				{
					ni.newVar(Spice.SPICE_CARD_KEY, parP.theText.substring(1),
						ep.getAnnotationTextDescriptor().withColorIndex(EGraphics.BLUE));
				} else
				{
					ni.newDisplayVar(Artwork.ART_MESSAGE, parP.theText, ep);
				}
				continue;
			}

			// ignore known keywords
			if (keyword0.equalsIgnoreCase("icon_title") ||
				keyword0.equalsIgnoreCase("make_line") ||
				keyword0.equalsIgnoreCase("}"))
			{
				continue;
			}

			String msg = "Cell " + cellName + ", line " + lr.getLineNumber() +
					": unknown keyword (" + keyword0 + "): " + lastLineRead;
			System.out.println(msg);
			errorLogger.logError(msg, cell, -1);
		}

		// place an icon instance in the schematic if requested
		if (placeIcon && schemCell != null && iconCell != null)
		{
			Rectangle2D bounds = iconCell.getBounds();
			double wid = bounds.getWidth();
			double hei = bounds.getHeight();
			NodeInst.makeInstance(iconCell, ep, iconPt, wid, hei, schemCell);
		}

		// make warnings about duplicate names
		for(String name : duplicateNames.keySet())
		{
			if (name.equalsIgnoreCase("vdd") || name.equalsIgnoreCase("gnd")) continue;
			List<NodeInst> dups = duplicateNames.get(name);
			StringBuffer buffer = new StringBuffer();
			buffer.append("Cell " + cell.getName() + " has multiple nodes with the same Sue name (" +
				name + "):");
			for(NodeInst ni : dups)
				buffer.append(" " + ni.describe(false));
			String msg = buffer.toString();
			System.out.println(msg);
			errorLogger.logWarning(msg, cell, -1);
		}

		// cleanup the current cell
		if (cell != null)
		{
			placeWires(sueWires, sueNets, cell, invertNodeOutput);
			placeNets(sueNets, cell);

			// autostitch the cell to connect nodes to nodes
			AutoOptions prefs = new AutoOptions(true);
			AutoStitch.runAutoStitch(cell, null, null, sueJob, null, null, false, false, ep, prefs, false, null);
		}

		// return the cell
		if (schemCell != null) return schemCell;
		return iconCell;
	}
	
	/**
	 * Method to create an export in a Cell.
	 * @param cell the cell in which to make an export.
	 * @param pi the PortInst inside the Cell to export.
	 * @param theName the name of the export.
	 * @param pc the characteristics of the export.
	 * The name is modified if it already exists.
	 */
	private Export newExport(Cell cell, PortInst pi, String theName, PortCharacteristic pc)
	{
		String portName = theName;
		for(int i=0; ; i++)
		{
			Export ppt = (Export)cell.findPortProto(portName);
			if (ppt == null)
			{
				return Export.newInstance(cell, pi, portName, ep, pc);
			}

			// make space for modified name
			int openPos = theName.indexOf('[');
			if (openPos < 0) portName = theName + "-" + i; else
			{
				portName = theName.substring(0, openPos) + "-" + i + theName.substring(openPos);
			}
		}
	}

	/**
	 * Method to find the pin at a given coordinate.
	 */
	private PortInst findPinNode(double x, double y, Cell cell)
	{
		Rectangle2D searchBounds = new Rectangle2D.Double(x, y, 0, 0);
		for(Iterator<Geometric> sea = cell.searchIterator(searchBounds); sea.hasNext(); )
		{
			Geometric geom = sea.next();
			if (!(geom instanceof NodeInst)) continue;
			NodeInst ni = (NodeInst)geom;

			// find closest port
			for(Iterator<PortInst> it = ni.getPortInsts(); it.hasNext(); )
			{
				PortInst pi = it.next();
				Poly poly = pi.getPoly();
				if (poly.getCenterX() == x && poly.getCenterY() == y) return pi;
			}
		}
		return null;
	}

	/**
	 * Method to read a SUE library.
	 * @param lib the Library to place the SUE.
	 * @param name the disk file of the SUE.
	 * Returns null if the file is not found or not read properly.
	 */
	private NodeProto readFromDisk(Library lib, String name)
	{
		// look for another "sue" file that describes this cell
		for(String directory : sueDirectories)
		{
			// get the directory
			String subFileName = directory + name + ".sue";

			// see if the file exists in the directory
			LineNumberReader lr = null;
			try
			{
				FileInputStream fis = new FileInputStream(subFileName);
				InputStreamReader is = new InputStreamReader(fis);
				lr = new LineNumberReader(is);
			} catch (FileNotFoundException e)
			{
				continue;
			}
			if (lr == null) continue;

			// read the file
			try
			{
				String saveLastLine = sueLastLine;
				sueLastLine = null;
				readFile(lib, name, lr);
				sueLastLine = saveLastLine;
				Cell cell = lib.findNodeProto(name);
				if (cell != null) return cell;
			} catch (IOException e)
			{
				System.out.println("ERROR reading Sue libraries");
			}
		}
		return null;
	}

	/**
	 * Method to find an icon cell.
	 * @param lib the Library to search.
	 * @param protoName the name of the Cell.
	 */
	private NodeProto getNodeProto(Library lib, String protoName)
	{
		for(Iterator<Cell> it = lib.getCells(); it.hasNext(); )
		{
			Cell cell = it.next();
			if (cell.getName().equalsIgnoreCase(protoName))
			{
				Cell icon = cell.iconView();
				if (icon != null) return icon;
				return cell;
			}
		}
		return null;
	}

	static enum InstructionType {FIXED, USER, COMMENT, NONE};
	
	private static class ParseParameters
	{
		Point2D pt;
		int rot;
		boolean trn;
		PortCharacteristic portType;
		InstructionType instructionType; // either fixed, 
		String theName;
		String theLabel;
		String theText;
		String theDefault;

		/**
		 * Method to parse the "count" parameters in "keywords" and fill in the values
		 * that are found.  Fills in:
		 * "-origin"  placed into "x" and "y"
		 * "-orient"  placed into "rot" and "trn"
		 * "-type"    placed into "type"
		 * "-name"    placed into "thename".
		 * "-label"   placed into "thelabel".
		 * "-text"    placed into "thetext".
		 * "-default" placed into "thedefualt".
		 */
		private ParseParameters(List<String> keywords, int start)
		{
			rot = 0;
			pt = null;
			trn = false;
			portType = PortCharacteristic.UNKNOWN;
			instructionType = InstructionType.NONE;
			theName = null;
			theLabel = null;
			theText = null;
			theDefault = null;

			for(int i=start; i<keywords.size(); i += 2)
			{
				String keyword = keywords.get(i);
				String param = "";
				if (i+1 < keywords.size()) param = keywords.get(i+1);
				if (keyword.equalsIgnoreCase("-origin"))
				{
					int j = 0;
					if (param.charAt(j) == '{') j++;
					double x = TextUtils.atof(param.substring(j));
					while (j < param.length()-1 && !Character.isWhitespace(param.charAt(j))) j++;
					while (j < param.length()-1 && Character.isWhitespace(param.charAt(j))) j++;
					double y = TextUtils.atof(param.substring(j));
					pt = new Point2D.Double(convertXCoord(x), convertYCoord(y));
				}
				if (keyword.equalsIgnoreCase("-orient"))
				{
					if (param.equalsIgnoreCase("R90"))  { rot = 900;  } else
					if (param.equalsIgnoreCase("R270")) { rot = 2700; } else
					if (param.equalsIgnoreCase("RXY"))  { rot = 1800; } else
					if (param.equalsIgnoreCase("RY"))   { rot = 900;  trn = true; } else
					if (param.equalsIgnoreCase("R90X")) { rot = 0;    trn = true; } else
					if (param.equalsIgnoreCase("R90Y")) { rot = 1800; trn = true; } else
					if (param.equalsIgnoreCase("RX"))   { rot = 2700; trn = true; }
					rot = (3600 - rot) % 3600;
				}
				if (keyword.equalsIgnoreCase("-type"))
				{
					if (param.equalsIgnoreCase("input")) portType = PortCharacteristic.IN; else
					if (param.equalsIgnoreCase("output")) portType = PortCharacteristic.OUT; else
					if (param.equalsIgnoreCase("inout")) portType = PortCharacteristic.BIDIR; else
					if (param.equalsIgnoreCase("fixed")) instructionType = InstructionType.FIXED; else
					if (param.equalsIgnoreCase("comment")) instructionType = InstructionType.COMMENT; else
					if (param.equalsIgnoreCase("user")) instructionType = InstructionType.USER;
				}
				if (keyword.equalsIgnoreCase("-name") ||
					keyword.equalsIgnoreCase("-label") ||
					keyword.equalsIgnoreCase("-text") ||
					keyword.equalsIgnoreCase("-default"))
				{
					String infstr = param;
					if (infstr.startsWith("{") && infstr.endsWith("}"))
					{
						int len = infstr.length();
						infstr = infstr.substring(1, len-1);
					}
					if (keyword.equalsIgnoreCase("-name")) theName = infstr; else
						if (keyword.equalsIgnoreCase("-label")) theLabel = infstr; else
							if (keyword.equalsIgnoreCase("-text")) theText = infstr; else
								if (keyword.equalsIgnoreCase("-default")) theDefault = infstr;
				}
			}
		}
	}

	/**
	 * Method to place all SUE wires into the cell.
	 */
	private void placeWires(List<SueWire> sueWires, List<SueNet> sueNets, Cell cell, Set<NodeInst> invertNodeOutput)
	{
		// mark all wire ends as "unassigned", all wire types as unknown
		for(SueWire sw : sueWires)
		{
			sw.pi[0] = sw.pi[1] = null;
			sw.proto = null;
		}

		// examine all network names and assign wire types appropriately
		for(SueNet sn : sueNets)
		{
			for(SueWire sw : sueWires)
			{
				for(int i=0; i<2; i++)
				{
					if (sw.pt[i].getX() == sn.pt.getX() && sw.pt[i].getY() == sn.pt.getY())
					{
						Name snName = Name.findName(sn.label);
						if (snName != null && snName.busWidth() > 1) sw.proto = Schematics.tech().bus_arc; else
							sw.proto = Schematics.tech().wire_arc;
					}
				}
			}
		}

		// find connections that are exactly on existing nodes
		for(SueWire sw : sueWires)
		{
			for(int i=0; i<2; i++)
			{
				if (sw.pi[i] != null) continue;
				for(Iterator<NodeInst> it = cell.getNodes(); it.hasNext(); )
				{
					NodeInst ni = it.next();
					PortInst pi = wiredPort(ni, sw.pt[i], sw.pt[1-i]);
					if (pi == null) continue;
					sw.pi[i] = pi;

					// determine whether this port is a bus
					boolean isBus = false;
					PortOriginal fp = new PortOriginal(pi);
					PortInst bottomPort = fp.getBottomPort();
					NodeInst bottomNi = bottomPort.getNodeInst();
					if (bottomNi.getProto() == Schematics.tech().wireConNode) continue;
					if (!isBus && ni.getProto() == Schematics.tech().offpageNode)
					{
						// see if there is a bus port on this primitive
						for(Iterator<Export> eIt = ni.getExports(); eIt.hasNext(); )
						{
							Export e = eIt.next();
							Name eName = Name.findName(e.getName());
							if (eName.busWidth() > 1) isBus = true;
						}
					}

					if (isBus)
					{
						sw.proto = Schematics.tech().bus_arc;
					} else
					{
						if (sw.proto == null)
							sw.proto = Schematics.tech().wire_arc;
					}
				}
			}
		}

		// now iteratively extend bus wires to connections with others
		boolean propagatedBus = true;
		while (propagatedBus)
		{
			propagatedBus = false;
			for(SueWire sw : sueWires)
			{
				if (sw.proto != Schematics.tech().bus_arc) continue;
				for(SueWire oSw : sueWires)
				{
					if (oSw.proto != null) continue;
					for(int i=0; i<2; i++)
					{
						for(int j=0; j<2; j++)
						{
							if (sw.pt[i].getX() == oSw.pt[j].getX() && sw.pt[i].getY() == oSw.pt[j].getY())
							{
								// common point found: continue the bus request
								oSw.proto = Schematics.tech().bus_arc;
								propagatedBus = true;
							}
						}
					}
				}
			}
		}

		// now make pins where wires meet
		for(SueWire sw : sueWires)
		{
			for(int i=0; i<2; i++)
			{
				if (sw.pi[i] != null) continue;
				NodeProto proto = Schematics.tech().wirePinNode;
				if (sw.proto == Schematics.tech().bus_arc) proto = Schematics.tech().busPinNode;

				// look at all other wires at this point and figure out type of pin to make
				for(SueWire oSw : sueWires)
				{
					if (oSw == sw) continue;
					for(int j=0; j<2; j++)
					{
						if (sw.pt[i].getX() != oSw.pt[j].getX() || sw.pt[i].getY() != oSw.pt[j].getY()) continue;
						if (oSw.pi[j] != null)
						{
							sw.pi[i] = oSw.pi[j];
							break;
						}
						if (oSw.proto == Schematics.tech().bus_arc) proto = Schematics.tech().busPinNode;
					}
					if (sw.pi[i] != null) break;
				}

				// make the pin if it doesn't exist
				if (sw.pi[i] == null)
				{
					// common point found: make a pin
					NodeInst ni = NodeInst.makeInstance(proto, ep, sw.pt[i], proto.getDefWidth(ep), proto.getDefHeight(ep), cell);
					sw.pi[i] = ni.getOnlyPortInst();
				}

				// put that node in all appropriate locations
				for(SueWire oSw : sueWires)
				{
					if (oSw == sw) continue;
					for(int j=0; j<2; j++)
					{
						if (sw.pt[i].getX() != oSw.pt[j].getX() || sw.pt[i].getY() != oSw.pt[j].getY()) continue;
						if (oSw.pi[j] != null) continue;
						oSw.pi[j] = sw.pi[i];
					}
				}
			}
		}

		// make pins at all of the remaining wire ends
		for(SueWire sw : sueWires)
		{
			for(int i=0; i<2; i++)
			{
				if (sw.pi[i] != null) continue;
				sw.pi[i] = findNode(sw.pt[i], sw.pt[1-i], cell, sw.pi[1-i]);
				if (sw.pi[i] == null)
				{
					NodeProto proto = Schematics.tech().wirePinNode;
					if (sw.proto == Schematics.tech().bus_arc) proto = Schematics.tech().busPinNode;
					NodeInst ni = NodeInst.makeInstance(proto, ep, sw.pt[i], proto.getDefWidth(ep), proto.getDefHeight(ep), cell);
					sw.pi[i] = ni.getOnlyPortInst();
				}
			}
		}

		// now make the connections
		for(SueWire sw : sueWires)
		{
			if (sw.proto == null) sw.proto = Schematics.tech().wire_arc;

			// if this is a bus, make sure it can connect */
			if (sw.proto == Schematics.tech().bus_arc)
			{
				for(int i=0; i<2; i++)
				{
					if (!sw.pi[i].getPortProto().getBasePort().connectsTo(Schematics.tech().bus_arc))
					{
						// this end cannot connect: fake the connection
						double px = (sw.pt[0].getX() + sw.pt[1].getX()) / 2;
						double py = (sw.pt[0].getY() + sw.pt[1].getY()) / 2;
						Point2D pt = new Point2D.Double(px, py);
						double xsize = Schematics.tech().busPinNode.getDefWidth(ep);
						double ysize = Schematics.tech().busPinNode.getDefHeight(ep);
						NodeInst ni = NodeInst.makeInstance(Schematics.tech().busPinNode, ep, pt, xsize, ysize, cell);
						if (ni == null) break;
						PortInst pi = ni.getOnlyPortInst();
						ArcInst ai = ArcInst.makeInstanceBase(Generic.tech().unrouted_arc, ep, 0, pi, sw.pi[i]);
						if (ai == null)
						{
							System.out.println("Error making fake connection");
							break;
						}
						sw.pi[i] = pi;
						sw.pt[i] = pt;
					}
				}
			}

			ArcInst ai = ArcInst.makeInstance(sw.proto, ep, sw.pi[0], sw.pi[1], sw.pt[0], sw.pt[1], null);
			if (ai == null)
			{
				System.out.println(cell + ": Could not run a wire from " + sw.pi[0].getNodeInst().describe(true) + " to " +
					sw.pi[1].getNodeInst().describe(true));
				continue;
			}

			// negate the wire if requested
			if (invertNodeOutput.contains(sw.pi[0].getNodeInst()) && sw.pi[0].getPortProto().getName().equals("y"))
				ai.setHeadNegated(true);
			if (invertNodeOutput.contains(sw.pi[1].getNodeInst()) && sw.pi[1].getPortProto().getName().equals("y"))
				ai.setTailNegated(true);
		}

		// now look for implicit connections where "offpage" connectors touch
		for(Iterator<NodeInst> it = cell.getNodes(); it.hasNext(); )
		{
			NodeInst ni = it.next();
			if (ni.getProto() != Schematics.tech().offpageNode) continue;
			if (ni.hasConnections()) continue;
			PortInst pi = ni.getPortInst(1);
			Poly piPoly = pi.getPoly();
			double x = piPoly.getCenterX();
			double y = piPoly.getCenterY();
			Rectangle2D searchBounds = new Rectangle2D.Double(x, y, 0, 0);
			for(Iterator<Geometric> sea = cell.searchIterator(searchBounds); sea.hasNext(); )
			{
				Geometric geom = sea.next();
				if (!(geom instanceof NodeInst)) continue;
				NodeInst oNi = (NodeInst)geom;
				if (oNi == ni) continue;
				boolean wired = false;
				for(Iterator<PortInst> oIt = oNi.getPortInsts(); oIt.hasNext(); )
				{
					PortInst oPi = oIt.next();
					Poly oPiPoly = oPi.getPoly();
					double oX = oPiPoly.getCenterX();
					double oY = oPiPoly.getCenterY();
					if (oX != x || oY != y) continue;
					ArcProto ap = null;
					for(int i=0; i<3; i++)
					{
						switch (i)
						{
							case 0: ap = Schematics.tech().bus_arc;     break;
							case 1: ap = Schematics.tech().wire_arc;    break;
							case 2: ap = Generic.tech().unrouted_arc;   break;
						}
						if (!pi.getPortProto().getBasePort().connectsTo(ap)) continue;
						if (!oPi.getPortProto().getBasePort().connectsTo(ap)) continue;
						break;
					}

					ArcInst.makeInstance(ap, ep, pi, oPi);
					wired = true;
					break;
				}
				if (wired) break;
			}
		}
	}

	/**
	 * Method to find the node at a given coordinate.
	 */
	private PortInst findNode(Point2D pt, Point2D oPt, Cell cell, PortInst notThisPort)
	{
		double slop = 10;
		PortInst bestPi = null;
		double bestDist = Double.MAX_VALUE;
		Rectangle2D searchBounds = new Rectangle2D.Double(pt.getX()-slop, pt.getY()-slop, slop*2, slop*2);
		for(Iterator<Geometric> sea = cell.searchIterator(searchBounds); sea.hasNext(); )
		{
			Geometric geom = sea.next();
			if (!(geom instanceof NodeInst)) continue;
			NodeInst ni = (NodeInst)geom;
			if (notThisPort != null && ni == notThisPort.getNodeInst()) continue;

			// ignore pins
			if (ni.getProto() == Schematics.tech().wirePinNode) continue;

			// find closest port
			for(Iterator<PortInst> it = ni.getPortInsts(); it.hasNext(); )
			{
				PortInst pi = it.next();
				Poly poly = pi.getPoly();
				Rectangle2D bounds = poly.getBounds2D();

				// find out if the line crosses the polygon
				double thisX = oPt.getX();
				double thisY = oPt.getY();
				if (pt.getX() == oPt.getX())
				{
					// line is vertical: look for intersection with polygon
					if (oPt.getX() < bounds.getMinX() || oPt.getX() > bounds.getMaxX()) continue;
					thisX = oPt.getX();
					thisY = bounds.getCenterY();
				} else if (pt.getY() == oPt.getY())
				{
					// line is horizontal: look for intersection with polygon
					if (oPt.getY() < bounds.getMinY() || oPt.getY() > bounds.getMaxY()) continue;
					thisX = bounds.getCenterX();
					thisY = oPt.getY();
				} else
				{
					if (!poly.isInside(oPt)) continue;
				}

				double dist = oPt.distance(new Point2D.Double(thisX, thisY));
				if (bestPi == null || dist < bestDist)
				{
					bestPi = pi;
					bestDist = dist;
				}
			}
		}

		// report the hit
		return bestPi;
	}

	/**
	 * Method to find the port on a node that attaches to the wire with given ends.
	 * @param ni the node to check.
	 * @param pt the end coordinate of the wire which must be on the node.
	 * @param oPt the other end of the wire.
	 * Returns null if not found.
	 */
	private PortInst wiredPort(NodeInst ni, Point2D pt, Point2D oPt)
	{
		for(Iterator<PortInst> it = ni.getPortInsts(); it.hasNext(); )
		{
			PortInst pi = it.next();
			Poly poly = pi.getPoly();
			if (poly.isInside(pt)) return pi;
		}
		if (ni.getTrueCenterX() != pt.getX() ||
			ni.getTrueCenterY() != pt.getY()) return null;

		// find port that is closest to OTHER end
		double bestDist = Double.MAX_VALUE;
		PortInst bestPi = null;
		for(Iterator<PortInst> it = ni.getPortInsts(); it.hasNext(); )
		{
			PortInst pi = it.next();
			Poly poly = pi.getPoly();
			Point2D ctr = new Point2D.Double(poly.getCenterX(), poly.getCenterY());
			double dist = ctr.distance(oPt);
			if (dist > bestDist) continue;
			bestDist = dist;
			bestPi = pi;
		}
		Poly poly = bestPi.getPoly();
		pt.setLocation(poly.getCenterX(), poly.getCenterY());
		return bestPi;
	}

	/**
	 * Method to place all SUE nets into the cell.
	 * @param sueNets the list of nets to add.
	 */
	private void placeNets(List<SueNet> sueNets, Cell cell)
	{
		// 3 passes: qualified labels, unqualified busses, unqualified wires
		for(int pass=0; pass<3; pass++)
		{
			for(SueNet sn : sueNets)
			{
				// unqualified labels (starting with "[") happen second
				if (sn.label.startsWith("["))
				{
					// unqualified label: pass 2 or 3 only
					if (pass == 0) continue;
				} else
				{
					// qualified label: pass 1 only
					if (pass != 0) continue;
				}

				// see if this is a bus
				Name lableName = Name.findName(sn.label);
				boolean isBus = false;
				if (lableName.busWidth() > 1) isBus = true;

				ArcInst bestAi = null;
				double bestDist = Double.MAX_VALUE;
				Rectangle2D searchBounds = new Rectangle2D.Double(sn.pt.getX(), sn.pt.getY(), 0, 0);
				for(Iterator<Geometric> sea = cell.searchIterator(searchBounds); sea.hasNext(); )
				{
					Geometric geom = sea.next();
					if (geom instanceof NodeInst) continue;
					ArcInst ai = (ArcInst)geom;
					if (isBus)
					{
						if (ai.getProto() != Schematics.tech().bus_arc) continue;
					} else
					{
						if (ai.getProto() == Schematics.tech().bus_arc) continue;
					}
					double cx = (ai.getHeadLocation().getX() + ai.getTailLocation().getX()) / 2;
					double cy = (ai.getHeadLocation().getY() + ai.getTailLocation().getY()) / 2;
					Point2D ctr = new Point2D.Double(cx, cy);
					double dist = ctr.distance(sn.pt);
					if (bestAi == null || dist < bestDist)
					{
						bestAi = ai;
						bestDist = dist;
					}
				}
				if (bestAi != null)
				{
					if (pass == 1)
					{
						// only allow busses
						if (bestAi.getProto() != Schematics.tech().bus_arc) continue;
					} else if (pass == 2)
					{
						// disallow busses
						if (bestAi.getProto() == Schematics.tech().bus_arc) continue;
					}
					String netName = sn.label;
					if (netName.startsWith("["))
					{
						// find the proper name of the network
						String busName = findBusName(bestAi);
						if (busName != null)
						{
							netName = busName + netName;
						}
					}
					bestAi.setName(netName, ep);
				}
			}
		}
	}

	/**
	 * Method to start at an ArcInst and search all wires until it finds a named bus.
	 */
	private String findBusName(ArcInst ai)
	{
		Set<ArcInst> arcsSeen = new HashSet<ArcInst>();
		String busName = searchBusName(ai, arcsSeen);
		if (busName == null)
		{
			for(int index=1; ; index++)
			{
				String pseudoBusName = "NET" + index;
				int len = pseudoBusName.length();
				boolean found = false;
				for(Iterator<ArcInst> it = ai.getParent().getArcs(); it.hasNext(); )
				{
					ArcInst oAi = it.next();
					String arcName = oAi.getName();
					if (arcName.equalsIgnoreCase(pseudoBusName)) { found = true;   break; }
					if (arcName.startsWith(pseudoBusName) && arcName.charAt(len) == '[') { found = true;   break; }
				}
				if (!found) return pseudoBusName;
			}
		}
		return busName;
	}

	private String searchBusName(ArcInst ai, Set<ArcInst> arcsSeen)
	{
		arcsSeen.add(ai);
		if (ai.getProto() == Schematics.tech().bus_arc)
		{
			String arcName = ai.getName();
			int openPos = arcName.indexOf('[');
			if (openPos >= 0) arcName = arcName.substring(0, openPos);
			return arcName;
		}
		for(int i=0; i<2; i++)
		{
			NodeInst ni = ai.getPortInst(i).getNodeInst();
			if (ni.getProto() != Schematics.tech().wirePinNode && ni.getProto() != Schematics.tech().busPinNode &&
				ni.getProto() != Schematics.tech().offpageNode) continue;
			if (ni.getProto() == Schematics.tech().busPinNode || ni.getProto() == Schematics.tech().offpageNode)
			{
				// see if there is an arrayed port here
				for(Iterator<Export> it = ni.getExports(); it.hasNext(); )
				{
					Export pp = it.next();
					String busName = pp.getName();
					int openPos = busName.indexOf('[');
					if (openPos >= 0) return busName.substring(0, openPos);
				}
			}
			for(Iterator<Connection> it = ni.getConnections(); it.hasNext(); )
			{
				Connection con = it.next();
				ArcInst oAi = con.getArc();
				if (arcsSeen.contains(oAi)) continue;
				String busName = searchBusName(oAi, arcsSeen);
				if (busName != null) return busName;
			}
		}
		return null;
	}

	/**
	 * Method to read the next line from file and break
	 * it up into space-separated keywords.  Returns the keywords (null on EOF)
	 */
	private List<String> getNextLine(LineNumberReader lr)
		throws IOException
	{
		lastLineRead = null;
		int curlyDepth = 0;
		boolean textOption = false;
		boolean incomplete = false;
		
		for(int lineNo=0; ; lineNo++)
		{
			if (sueLastLine == null)
			{
				sueLastLine = lr.readLine();
				if (sueLastLine == null) return null;
			}
			
			if (lineNo == 0)
			{
				// first line: use it
				lastLineRead = sueLastLine;
			} else
			{
				// subsequent line: use it only if a continuation
				if (!incomplete && (sueLastLine.length() == 0 || sueLastLine.charAt(0) != '+')) break;
				boolean plusSign = sueLastLine.charAt(0) == '+';
				lastLineRead += sueLastLine.substring((plusSign) ? 1 : 0); // extra step to glue lines
			}
			sueLastLine = null;
			
			// count {} pairs. If last one is not closed -> next line
			curlyDepth = 0;
			for(int i=0; i<lastLineRead.length(); i++)
			{
				char pt = lastLineRead.charAt(i);
				if (pt == '{') curlyDepth++;
				if (pt == '}') curlyDepth--;
			}
			if (!textOption)
				textOption = lastLineRead.contains("-text") || lastLineRead.contains("-label") ;
			incomplete = textOption && (curlyDepth % 2 != 0);
		}

		// parse the line
		boolean inBlank = true;
		List<String> keywords = new ArrayList<String>();
		if (lastLineRead.startsWith("#")) return keywords;
		int startIndex = 0;
		curlyDepth = 0;
		int len = lastLineRead.length();
		
		for(int i=0; i<len; i++)
		{
			char pt = lastLineRead.charAt(i);
			if (pt == '{') curlyDepth++;
			if (pt == '}') curlyDepth--;
			if ((pt == ' ' || pt == '\t') && curlyDepth == 0)
			{
				if (!inBlank)
				{
					String keyword = lastLineRead.substring(startIndex, i).trim();
					keywords.add(keyword);
					startIndex = i;
				}
				inBlank = true;
			} else
			{
				if (inBlank)
				{
					startIndex = i;
				}
				inBlank = false;
			}
		}
		String keyword = lastLineRead.substring(startIndex, len).trim();
		if (keyword.length() > 0)
			keywords.add(keyword);
		return keywords;
	}

	/**
	 * Method to examine a SUE expression and add "@" in front of variable names.
	 * Only does this if requested.
	 */
	private String parseExpression(String expression)
	{
		if (!localPrefs.convertExpressions) return expression;
		StringBuffer infstr = new StringBuffer();
		for(int i=0; i<expression.length(); i++)
		{
			int startKey = i;
			while (i < expression.length())
			{
				char chr = expression.charAt(i);
				if (chr == ' ' || chr == '\t' || chr == ',' || chr == '+' ||
					chr == '-' || chr == '*' || chr == '/' || chr == '(' || chr == ')')
						break;
				i++;
			}
			if (i > startKey)
			{
				String keyword = expression.substring(startKey, i);
				if (!TextUtils.isANumber(keyword))
				{
					if (i >= expression.length() || expression.charAt(i) != '(')
						infstr.append('@');
				}
				infstr.append(keyword);
			}
			if (i < expression.length())
			{
				infstr.append(expression.charAt(i));
				i++;
			}
		}
		return infstr.toString();
	}

	/**
	 * Method to convert SUE X coordinate to Electric coordinate.
	 */
	private static double convertXCoord(double x)
	{
		return x / 8;
	}

	/**
	 * Method to convert SUE Y coordinate to Electric coordinate.
	 */
	private static double convertYCoord(double y)
	{
		return -y / 8;
	}
}
