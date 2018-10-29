/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: DEF.java
 * Input/output tool: DEF (Design Exchange Format) reader
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
import com.sun.electric.database.ImmutableArcInst;
import com.sun.electric.database.geometry.EPoint;
import com.sun.electric.database.geometry.ERectangle;
import com.sun.electric.database.geometry.Poly;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.Export;
import com.sun.electric.database.hierarchy.Library;
import com.sun.electric.database.hierarchy.View;
import com.sun.electric.database.id.CellId;
import com.sun.electric.database.network.Netlist;
import com.sun.electric.database.network.Network;
import com.sun.electric.database.prototype.NodeProto;
import com.sun.electric.database.prototype.PortCharacteristic;
import com.sun.electric.database.prototype.PortProto;
import com.sun.electric.database.text.CellName;
import com.sun.electric.database.text.TextUtils;
import com.sun.electric.database.topology.ArcInst;
import com.sun.electric.database.topology.Geometric;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.topology.PortInst;
import com.sun.electric.database.topology.RTBounds;
import com.sun.electric.database.topology.RTNode;
import com.sun.electric.database.variable.ElectricObject;
import com.sun.electric.database.variable.TextDescriptor;
import com.sun.electric.database.variable.Variable;
import com.sun.electric.technology.ArcProto;
import com.sun.electric.technology.Layer;
import com.sun.electric.technology.PrimitiveNode;
import com.sun.electric.technology.SizeOffset;
import com.sun.electric.technology.Technology;
import com.sun.electric.technology.technologies.Generic;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.io.IOTool;
import com.sun.electric.util.math.DBMath;
import com.sun.electric.util.math.FixpRectangle;
import com.sun.electric.util.math.FixpTransform;
import com.sun.electric.util.math.Orientation;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class reads files in DEF files.
 * <BR>
 * Note that this reader was built by examining DEF files and reverse-engineering them.
 * It does not claim to be compliant with the DEF specification, but it also does not
 * claim to define a new specification.  It is merely incomplete.
 *
 * R. Reese (RBR) - modified Spring 2007 to be able to import a DEF file to a currently
 * opened View. The intended use is for the Views to either be layout or schematic.
 * If the view is layout, then all geometry is input and unrouted net connections
 * are used to maintain connectivity between logical nets and physical geometries.
 * At some point in the future, these unrouted nets need to be cleaned up, but for
 * now, the use of unrouted nets allows the layout to pass DRC and to be simulated.
 * Can also import to a schematic view - this creates a hodge-podge of icons in the
 * schematic view but net connections are correct so NCC can be used to check
 * layout vs. schematic. This is useful in a hierarchical design where part of the
 * design is imported DEF (say, a standard cell layout), and the rest of the design
 * is manual layout. Having a schematic view for the imported DEF allows NCC to
 * complain less when checking the design.
 */
public class DEF extends LEFDEF
{
	// debugging control
	private static final int LIMITNETS = -1;		// for specialnets, 3710 eliminates all globals; 3717 includes one global (VSB)

	private static final boolean READCOMPONENTS = true;
	private static final boolean READPINS = true;
	private static final boolean READBLOCKAGES = true;
	private static final boolean READSPECIALNETS = true;
	private static final boolean READNETS = true;

	// special controls to limit the area being read so that nothing above MAXX/MAXY is accepted
	private static final boolean LIMITINGAREA = false;
	private static final double MAXX = 79100;
	private static final double MAXY = 200000;

	private double scaleUnits;
	private Map<String,ViaGenerator> allViaGenerators;
	private Map<String,ViaDef> cellViaDefs;
	private Map<String,PortInst> specialNetsHT = null;
	private Map<String,PortInst> normalNetsHT = null;
	private Map<String,NodeInst> instanceMap = null;
	private NodeInst dummyNodeInst = null;
	private boolean schImport = false;
	private Job job;
	private Map<Cell,Map<String,String>> cellModifiedNetNames = new HashMap<Cell,Map<String,String>>();
	private Map<Cell,Set<String>> cellOriginalNetNames = new HashMap<Cell,Set<String>>();
	private Map<Cell,Long> cellAutonames = new HashMap<Cell,Long>();

	private Pattern pat_starleftbracket = Pattern.compile(".*\\\\"+ "\\[");
	private Pattern pat_leftbracket = Pattern.compile("\\\\"+ "\\[");
	private Pattern pat_starrightbracket = Pattern.compile(".*\\\\"+ "\\]");
	private Pattern pat_rightbracket = Pattern.compile("\\\\"+ "\\]");

	private DEFPreferences localPrefs;

	public static class DEFPreferences extends InputPreferences
	{
		public boolean physicalPlacement;
		public boolean ignorePhysicalInNets;
		public boolean usePureLayerNodes;
		public boolean logicalPlacement;
		public boolean ignoreLogicalInSpecialNets;
		public boolean makeDummyCells;
		public boolean ignoreUngeneratedPins;
		public double overallscale = 1;
		public boolean ignoreViasBlock;
		public int unknownLayerHandling;
		public boolean connectByGDSName;
		public boolean connectAndPlaceAllPins;

		public DEFPreferences(boolean factory)
		{
			super(factory);
			if (factory)
			{
				physicalPlacement = IOTool.isFactoryDEFPhysicalPlacement();
				ignorePhysicalInNets = IOTool.isFactoryDEFIgnorePhysicalInNets();
				usePureLayerNodes = IOTool.isFactoryDEFUsePureLayerNodes();
				logicalPlacement = IOTool.isFactoryDEFLogicalPlacement();
				ignoreLogicalInSpecialNets = IOTool.isFactoryDEFIgnoreLogicalInSpecialNets();
				makeDummyCells = IOTool.isFactoryDEFMakeDummyCells();
				ignoreUngeneratedPins = IOTool.isFactoryDEFIgnoreUngeneratedPins();
				ignoreViasBlock = IOTool.isFactoryDEFIgnoreViasBlock();
				unknownLayerHandling = IOTool.getFactoryDEFInUnknownLayerHandling();
				connectByGDSName = IOTool.isFactoryDEFConnectByGDSNames();
				connectAndPlaceAllPins = IOTool.isFactoryDEFPlaceAndConnectAllPins();
			} else
			{
				physicalPlacement = IOTool.isDEFPhysicalPlacement();
				ignorePhysicalInNets = IOTool.isDEFIgnorePhysicalInNets();
				usePureLayerNodes = IOTool.isDEFUsePureLayerNodes();
				logicalPlacement = IOTool.isDEFLogicalPlacement();
				ignoreLogicalInSpecialNets = IOTool.isDEFIgnoreLogicalInSpecialNets();
				makeDummyCells = IOTool.isDEFMakeDummyCells();
				ignoreUngeneratedPins = IOTool.isDEFIgnoreUngeneratedPins();
				ignoreViasBlock = IOTool.isDEFIgnoreViasBlock();
				unknownLayerHandling = IOTool.getDEFInUnknownLayerHandling();
				connectByGDSName = IOTool.isDEFConnectByGDSNames();
				connectAndPlaceAllPins = IOTool.isDEFPlaceAndConnectAllPins();
			}
		}

		@Override
		public Library doInput(URL fileURL, Library lib, Technology tech, EditingPreferences ep, Map<Library,Cell> currentCells, Map<CellId,BitSet> nodesToExpand, Job job)
		{
			DEF in = new DEF(ep, this);
			in.job = job;
			if (in.openTextInput(fileURL)) return null;
			lib = in.importALibrary(lib, tech, currentCells);
			in.closeInput();
			return lib;
		}
	}

	/**
	 * Creates a new instance of DEF.
	 */
	DEF(EditingPreferences ep, DEFPreferences ap)
	{
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
		initKeywordParsing();
		scaleUnits = 1000;
		allViaGenerators = new HashMap<String,ViaGenerator>();
		cellViaDefs = new HashMap<String,ViaDef>();
		instanceMap = new HashMap<String,NodeInst>();
		initializeLEFDEF(tech);

		// read the file
		try
		{
			if (readFile(lib, currentCells)) return null; // error during reading
		} catch (IOException e)
		{
			System.out.println("ERROR reading DEF libraries");
		}
		return lib;
	}

	protected String preprocessLine(String line)
	{
		int sharpPos = line.indexOf('#');
		if (sharpPos >= 0) return line.substring(0, sharpPos);
		return line;
	}

	/**
	 * Method to read the DEF file.
	 * @return true on error.
	 */
	private boolean readFile(Library lib, Map<Library,Cell> currentCells)
		throws IOException
	{
		Cell cell = null;
		for(;;)
		{
			if (job != null && job.checkAbort())
			{
				System.out.println("DEF import aborted!");
				break;
			}

			// get the next keyword
			String key = getAKeyword();
			if (key == null) break;

			// ignore keywords that are on a single-line
			if (key.equalsIgnoreCase("BUSBITCHARS") ||
				key.equalsIgnoreCase("COMPONENTMASKSHIFT") ||
				key.equalsIgnoreCase("DIEAREA") ||
				key.equalsIgnoreCase("DIVIDERCHAR") ||
				key.equalsIgnoreCase("GCELLGRID") ||
				key.equalsIgnoreCase("HISTORY") ||
				key.equalsIgnoreCase("NAMESCASESENSITIVE") ||
				key.equalsIgnoreCase("ROW") ||
				key.equalsIgnoreCase("TECHNOLOGY") ||
				key.equalsIgnoreCase("TRACKS") ||
				key.equalsIgnoreCase("VERSION"))
			{
				if (ignoreToSemicolon(key, cell)) return true;
				continue;
			}

			// ignore keywords that are in a block of lines
			if (key.equalsIgnoreCase("DEFAULTCAP") ||
				key.equalsIgnoreCase("GROUPS") ||
				key.equalsIgnoreCase("NONDEFAULTRULES") ||
				key.equalsIgnoreCase("PROPERTYDEFINITIONS") ||
				key.equalsIgnoreCase("REGIONS"))
			{
				if (ignoreBlock(key, cell)) return true;
				continue;
			}

			if (key.equalsIgnoreCase("DESIGN"))
			{
				String cellName = mustGetKeyword("DESIGN", cell);
				if (cellName == null) return true;

				// RBR - first, see if Cell name is equal to current cells it exists then read into cell
				cell = currentCells.get(lib);
				if (!Input.isNewLibraryCreated())
				{
					// reading into current cell, current library
					if (cell == null || !cell.getCellName().getName().equals(cellName))
					{
						cell = lib.findNodeProto(cellName);
						if (cell == null)
						{
							cell = Cell.makeInstance(ep, lib, cellName + "{lay}");
						}
					}
					View cellView = cell.getCellName().getView();
					if (cellView.getAbbreviation().equals("sch"))
					{
						schImport = true; // special flag when importing into schematic view
					}
				} else if (cell == null || !cell.getCellName().getName().equals(cellName))
				{
					// does not equal current cell, so make instance
					cell = Cell.makeInstance(ep, lib, cellName + "{lay}");
				}

				if (cell == null)
				{
					reportError("Cannot create cell '" + cellName + "'", cell);
					return true;
				}
				cell.setTechnology(curTech);
				Netlist theNetList = cell.getNetlist();
				/// cache list of network names.
				Set<String> netNames = new HashSet<String>();
				
				for (Iterator<Network> itN = theNetList.getNetworks(); itN.hasNext();)
				{
					Network n = itN.next();
					netNames.add(n.getName());
				}
				cellOriginalNetNames.put(cell, netNames);
				
				if (ignoreToSemicolon("DESIGN", cell)) return true;
				continue;
			}

			if (key.equalsIgnoreCase("UNITS"))
			{
				if (readUnits(cell)) return true;
				continue;
			}

			if (key.equalsIgnoreCase("VIAS"))
			{
				if (!localPrefs.ignoreViasBlock)
				{
					if (readVias(lib, cell)) return true;
				}
				else
				{
					if (ignoreBlock(key, cell)) return true;
				}
				continue;
			}

			if (key.equalsIgnoreCase("COMPONENTS"))
			{
				if (READCOMPONENTS)
				{
					reportSection("COMPONENTS");
					if (readComponents(cell, lib)) return true;
				} else
				{
					if (ignoreBlock(key, cell)) return true;
				}
				continue;
			}

			if (key.equalsIgnoreCase("PINS"))
			{
				if (READPINS)
				{
					reportSection("PINS");
					if (readPins(cell)) return true;
				} else
				{
					if (ignoreBlock(key, cell)) return true;
				}
				continue;
			}

			if (key.equalsIgnoreCase("BLOCKAGES"))
			{
				if (READBLOCKAGES)
				{
					reportSection("BLOCKAGES");
					if (readBlockages(cell)) return true;
				} else
				{
					if (ignoreBlock(key, cell)) return true;
				}
				continue;
			}

			if (key.equalsIgnoreCase("SPECIALNETS"))
			{
				if (READSPECIALNETS && (localPrefs.logicalPlacement || localPrefs.physicalPlacement))
				{
					reportSection("SPECIALNETS");
					boolean fail = readNets(cell, true, lib);
					if (fail) return true;
				} else
				{
					if (ignoreBlock(key, cell)) return true;
				}
				continue;
			}

			if (key.equalsIgnoreCase("NETS"))
			{
				if (READNETS && (localPrefs.logicalPlacement || localPrefs.physicalPlacement))
				{
					reportSection("NETS");
					boolean fail = readNets(cell, false, lib);
					if (fail) return true;
				} else
				{
					if (ignoreBlock(key, cell)) return true;
				}
				if (ignoreBlock(key, cell)) return true;
				continue;
			}

			if (key.equalsIgnoreCase("END"))
			{
				key = getAKeyword();
				break;
			}

			reportError("Unknown top-level keyword: " + key, cell);
		}
		return false;
	}

	private void reportSection(String name)
	{
		if (Job.getDebug())
		{
			long pct = byteCount * 100L / fileLength;
			System.out.println("Reading " + name + " starting at " + pct + "%");
		}
	}

	/*************** BLOCKAGES ***************/

	private boolean readBlockages(Cell cell)
		throws IOException
	{
		if (ignoreToSemicolon("BLOCKAGES", cell)) return true;
		for(;;)
		{
			if (job != null && job.checkAbort())
			{
				System.out.println("DEF import aborted!");
				return true;
			}

			// get the next keyword
			String key = mustGetKeyword("BLOCKAGES", cell);
			if (key == null) return true;
			if (key.equals("-"))
			{
				key = mustGetKeyword("BLOCKAGES", cell);
				NodeProto np = null;
				if (key.equalsIgnoreCase("PLACEMENT"))
				{
					np = Generic.tech().drcNode;
				} else if (key.equalsIgnoreCase("LAYER"))
				{
					key = mustGetKeyword("BLOCKAGES", cell);
					GetLayerInformation li = getLayerInformation(key, null);
					if (li.pin == null)
					{
						reportError("Unknown blockage layer (" + key + ")", cell);
						return true;
					}
					np = li.pin;
				}
				key = mustGetKeyword("BLOCKAGES", cell);
				if (key == null) return true;
				if (key.equalsIgnoreCase("+"))
				{
					key = mustGetKeyword("BLOCKAGES", cell);
					if (key == null) return true;
					if (key.equalsIgnoreCase("SOFT"))
					{
						key = mustGetKeyword("BLOCKAGES", cell);
						if (key == null) return true;						
					} else
					{
						reportError("Unknown Placement keyword in Blockages section (" + key + ")", cell);
						return true;
					}
				}
				if (key.equalsIgnoreCase("RECT"))
				{
					Point2D ll = readCoordinate(cell, false);
					if (ll == null) return true;
					Point2D ur = readCoordinate(cell, false);
					if (ur == null) return true;

					// create the blockage
					double sX = Math.abs(ll.getX() - ur.getX());
					double sY = Math.abs(ll.getY() - ur.getY());
					double cX = (ll.getX() + ur.getX()) / 2;
					double cY = (ll.getY() + ur.getY()) / 2;
					EPoint loc = EPoint.fromLambda(cX, cY);
					if (acceptNode(loc, sX, sY))
					{
						if (LIMITINGAREA)
						{
							double lX = loc.getX() - sX/2;
							double hX = loc.getX() + sX/2;
							double lY = loc.getY() - sY/2;
							double hY = loc.getY() + sY/2;
							if (hX > MAXX) hX = MAXX;
							if (hY > MAXY) hY = MAXY;
							loc = EPoint.fromLambda((lX+hX)/2, (lY+hY)/2);
							sX = hX - lX;
							sY = hY - lY;
						}
						if (makeNode(np, loc, sX, sY, cell) == null) return true;
					}
				} else
				{
					reportError("Expected RECT in BLOCKAGES section", cell);
					return true;
				}
				if (ignoreToSemicolon(key, cell)) return true;
				continue;
			}

			if (key.equalsIgnoreCase("END"))
			{
				key = getAKeyword();
				break;
			}

			// ignore the keyword
			if (ignoreToSemicolon(key, cell)) return true;
		}
		return false;
	}

	/*************** PINS ***************/

	private boolean readPins(Cell cell)
		throws IOException
	{
		if (ignoreToSemicolon("PINS", cell)) return true;
		for(;;)
		{
			if (job != null && job.checkAbort())
			{
				System.out.println("DEF import aborted!");
				return true;
			}

			// get the next keyword
			String key = mustGetKeyword("PINs", cell);
			if (key == null) return true;
			if (key.equals("-"))
			{
				if (readPin(cell)) return true;
				continue;
			}

			if (key.equalsIgnoreCase("END"))
			{
				key = getAKeyword();
				break;
			}

			// ignore the keyword
			if (ignoreToSemicolon(key, cell)) return true;
		}
		return false;
	}

	private static class PinPlacement
	{
		Point2D ll, ur;
		Point2D xy;
		Orientation orient;
		NodeProto np;

		public PinPlacement()
		{
			ll = new Point2D.Double(0, 0);
			ur = new Point2D.Double(0, 0);
			xy = new Point2D.Double(0, 0);
			orient = Orientation.IDENT;
			np = null;
		}

		public PinPlacement(PinPlacement copy)
		{
			ll = copy.ll;
			ur = copy.ur;
			xy = copy.xy;
			orient = copy.orient;
			np = copy.np;
		}

		public boolean equals(Object o)
		{
			PinPlacement other = (PinPlacement)o;
			if (ll.getX() != other.ll.getX()) return false;
			if (ll.getY() != other.ll.getY()) return false;
			if (ur.getX() != other.ur.getX()) return false;
			if (ur.getY() != other.ur.getY()) return false;
			if (xy.getX() != other.xy.getX()) return false;
			if (xy.getY() != other.xy.getY()) return false;
			if (orient != other.orient) return false;
			if (np != other.np) return false;
			return true;
		}
	}

	private boolean readPin(Cell cell)
		throws IOException
	{
		// get the pin name
		String key = mustGetKeyword("PIN", cell);
		if (key == null) return true;
		String pinName = translateDefName(key);
		PortCharacteristic portCharacteristic = null;

		List<PinPlacement> pinsToMake = new ArrayList<PinPlacement>();
		PinPlacement curPin = new PinPlacement();
		for(;;)
		{
			// get the next keyword
			key = mustGetKeyword("PIN", cell);
			if (key == null) return true;
			if (key.equals("+"))
			{
				key = mustGetKeyword("PIN", cell);
				if (key == null) return true;
				if (key.equalsIgnoreCase("NET"))
				{
					key = mustGetKeyword("net name", cell);
					if (key == null) return true;
					continue;
				}
				if (key.equalsIgnoreCase("DIRECTION"))
				{
					key = mustGetKeyword("DIRECTION", cell);
					if (key == null) return true;
					if (key.equalsIgnoreCase("INPUT")) portCharacteristic = PortCharacteristic.IN; else
					if (key.equalsIgnoreCase("OUTPUT")) portCharacteristic = PortCharacteristic.OUT; else
					if (key.equalsIgnoreCase("INOUT")) portCharacteristic = PortCharacteristic.BIDIR; else
					if (key.equalsIgnoreCase("FEEDTHRU")) portCharacteristic = PortCharacteristic.BIDIR; else
					{
						reportError("Unknown direction (" + key + ")", cell);
						return true;
					}
					continue;
				}
				if (key.equalsIgnoreCase("USE"))
				{
					key = mustGetKeyword("USE", cell);
					if (key == null) return true;
					if (key.equalsIgnoreCase("SIGNAL")) ; else
					if (key.equalsIgnoreCase("POWER")) portCharacteristic = PortCharacteristic.PWR; else
					if (key.equalsIgnoreCase("GROUND")) portCharacteristic = PortCharacteristic.GND; else
					if (key.equalsIgnoreCase("CLOCK")) portCharacteristic = PortCharacteristic.CLK; else
					if (key.equalsIgnoreCase("TIEOFF")) ; else
					if (key.equalsIgnoreCase("ANALOG")) ; else
					{
						reportError("Unknown usage (" + key + ")", cell);
						return true;
					}
					continue;
				}
				if (key.equalsIgnoreCase("LAYER"))
				{
					String layer = mustGetKeyword("LAYER", cell);
					if (layer == null) return true;

					// handle optional MASK keyword
					Integer mask = null;
					key = mustGetKeyword("coordinate", cell);
					if (key == null) return true;
					if (key.equalsIgnoreCase("MASK"))
					{
						// get mask value
						String maskStr = mustGetKeyword("Mask", cell);
						if (maskStr == null) return true;
						mask = Integer.valueOf(TextUtils.atoi(maskStr));

						// now continue with getting coordinate
						key = mustGetKeyword("coordinate", cell);
						if (key == null) return true;
					}

					// figure out what the layer/mask really is
					if (!schImport)
					{
						GetLayerInformation li = getLayerBasedOnNameAndMask(layer, mask, localPrefs.unknownLayerHandling);
						if (li.pin == null)
						{
							reportError("Unknown pin for layer " + layer + (mask == null ? "" : ", mask "+mask), cell);
							return true;
						}
						curPin.np = li.pin;
					}

					if (!key.equals("("))
					{
						reportError("Expected '(' in coordinate", cell);
						return true;
					}
					curPin.ll = readCoordinate(cell, true);
					if (curPin.ll == null) return true;
					curPin.ur = readCoordinate(cell, false);
					if (curPin.ur == null) return true;
					continue;
				}
				if (key.equalsIgnoreCase("PLACED") || key.equalsIgnoreCase("FIXED"))
				{
					// get pin location and orientation
					curPin.xy = readCoordinate(cell, false);
					if (curPin.xy == null) return true;
					curPin.orient = new GetOrientation(cell).orient;
					if (curPin.np != null)
					{
						// remember this PinPlacement
						pinsToMake.add(curPin);
						curPin = new PinPlacement(curPin);
					}
					continue;
				}
				continue;
			}

			if (key.equals(";"))
				break;
		}
		if (schImport)
		{
			ArcProto apTry = null;
			for(Iterator<ArcProto> it = curTech.getArcs(); it.hasNext(); )
			{
				apTry = it.next();
				if (apTry.getName().equals("wire")) break;
			}
			if (apTry == null)
			{
				reportError("Unable to resolve pin component", cell);
				return true;
			}
			for(Iterator<PrimitiveNode> it = curTech.getNodes(); it.hasNext(); )
			{
				PrimitiveNode loc_np = it.next();
				// must have just one port
				if (loc_np.getNumPorts() != 1) continue;

				// port must connect to both arcs
				PortProto pp = loc_np.getPort(0);
				if (pp.connectsTo(apTry)) { curPin.np = loc_np;   break; }
			}
		}

		// if pin is ungenerated and placement is requested, put it at the origin
		if (curPin.xy == null && !localPrefs.ignoreUngeneratedPins)
			curPin.xy = new Point2D.Double(0, 0);

		// now make list of pins to place
		if (curPin.np != null && curPin.xy != null) pinsToMake.add(curPin);

		// special case when all export (PIN) geometries must be instantiated and connected
		if (localPrefs.connectAndPlaceAllPins)
		{
			// eliminate redundant pins
			for(int i=1; i<pinsToMake.size(); i++)
			{
				PinPlacement last = pinsToMake.get(i-1);
				PinPlacement current = pinsToMake.get(i);
				if (!last.equals(current)) continue;
				pinsToMake.remove(i);
				i--;
			}

			// place the pins
			PortInst lastPi = null;
			for(PinPlacement cp : pinsToMake)
			{
				// determine the pin size
				FixpTransform trans = cp.orient.pureRotate();
				trans.transform(cp.ll, cp.ll);
				trans.transform(cp.ur, cp.ur);
				double sX = Math.abs(cp.ll.getX() - cp.ur.getX());
				double sY = Math.abs(cp.ll.getY() - cp.ur.getY());
				double cX = (cp.ll.getX() + cp.ur.getX()) / 2 + cp.xy.getX();
				double cY = (cp.ll.getY() + cp.ur.getY()) / 2 + cp.xy.getY();

				// make the pin
				EPoint loc = EPoint.fromLambda(cX, cY);
				if (!acceptNode(loc, sX, sY)) continue;
				if (LIMITINGAREA)
				{
					double lX = loc.getX() - sX/2;
					double hX = loc.getX() + sX/2;
					double lY = loc.getY() - sY/2;
					double hY = loc.getY() + sY/2;
					if (hX > MAXX) hX = MAXX;
					if (hY > MAXY) hY = MAXY;
					loc = EPoint.fromLambda((lX+hX)/2, (lY+hY)/2);
					sX = hX - lX;
					sY = hY - lY;
				}

				// see if pin exists at this location
				NodeInst existingNI = null;
				for(Iterator<NodeInst> it = cell.getNodes(); it.hasNext(); )
				{
					NodeInst ni = it.next();
					if (ni.getProto() != cp.np) continue;
					if (!ni.getProto().getFunction().isPin()) continue;
					if (!DBMath.areEquals(ni.getAnchorCenterX(), cX) || !DBMath.areEquals(ni.getAnchorCenterY(), cY)) continue;
					existingNI = ni;
					break;
				}
				PortInst pi;
				if (existingNI != null)
				{
					// pin found at this location: reuse
					pi = existingNI.getOnlyPortInst();
				} else
				{
					// no pin at this location: create it
					String newPinName = ElectricObject.uniqueObjectName(pinName, cell, Export.class, false, true);
					NodeInst ni = makeNode(cp.np, loc, sX, sY, cell);
					if (ni == null) return true;
					pi = ni.findPortInstFromProto(cp.np.getPort(0));
					Export e = Export.newInstance(cell, pi, newPinName, ep, portCharacteristic);
					if (e == null)
					{
						reportError("Unable to create pin " + newPinName, cell);
						return true;
					}
				}
				if (lastPi != null)
					makeUnroutedConnection(lastPi, pi, null);
				lastPi = pi;
				continue;
			}
		} else
		{
			// place only the current pin, determine its size
			if (curPin.np == null)
			{
				reportError("No layer given for pin", cell);
				return false;
			}
			FixpTransform trans = curPin.orient.pureRotate();
			trans.transform(curPin.ll, curPin.ll);
			trans.transform(curPin.ur, curPin.ur);
			double sX = Math.abs(curPin.ll.getX() - curPin.ur.getX());
			double sY = Math.abs(curPin.ll.getY() - curPin.ur.getY());
			double cX = (curPin.ll.getX() + curPin.ur.getX()) / 2 + curPin.xy.getX();
			double cY = (curPin.ll.getY() + curPin.ur.getY()) / 2 + curPin.xy.getY();

			// make the pin
			EPoint loc = EPoint.fromLambda(cX, cY);
			if (!acceptNode(loc, sX, sY)) return false;
			if (LIMITINGAREA)
			{
				double lX = loc.getX() - sX/2;
				double hX = loc.getX() + sX/2;
				double lY = loc.getY() - sY/2;
				double hY = loc.getY() + sY/2;
				if (hX > MAXX) hX = MAXX;
				if (hY > MAXY) hY = MAXY;
				loc = EPoint.fromLambda((lX+hX)/2, (lY+hY)/2);
				sX = hX - lX;
				sY = hY - lY;
			}
			// default case where only one export (PIN) is created
			Export ex = cell.findExport(pinName);
			if (ex != null)
			{
				NodeInst existingNI = ex.getOriginalPort().getNodeInst();
				if (existingNI.getProto() == curPin.np)
				{
					if (!DBMath.areEquals(existingNI.getAnchorCenterX(), cX) || !DBMath.areEquals(existingNI.getAnchorCenterY(), cY) ||
						!DBMath.areEquals(existingNI.getLambdaBaseXSize(), sX) || !DBMath.areEquals(existingNI.getLambdaBaseYSize(), sY))
					{
						String msg = "Cell already has an export named '" + pinName + "' that is different.";
						if (!DBMath.areEquals(existingNI.getAnchorCenterX(), cX) || !DBMath.areEquals(existingNI.getAnchorCenterY(), cY))
							msg += " Center was (" + TextUtils.formatDistance(existingNI.getAnchorCenterX()) + "," +
								TextUtils.formatDistance(existingNI.getAnchorCenterY()) + ") but now is (" + TextUtils.formatDistance(cX) +
								"," + TextUtils.formatDistance(cY) + ").";
						if (!DBMath.areEquals(existingNI.getLambdaBaseXSize(), sX) || !DBMath.areEquals(existingNI.getLambdaBaseYSize(), sY))
							msg += " Size was " + TextUtils.formatDistance(existingNI.getLambdaBaseXSize()) + "X" +
								TextUtils.formatDistance(existingNI.getLambdaBaseYSize()) + " but now is " + TextUtils.formatDistance(sX) +
								"X" + TextUtils.formatDistance(sY);
						reportWarning(msg, existingNI, cell);
						double dX = cX - existingNI.getAnchorCenterX();
						double dY = cY - existingNI.getAnchorCenterY();
						double dXSize = sX - existingNI.getLambdaBaseXSize();
						double dYSize = sY - existingNI.getLambdaBaseYSize();
						existingNI.modifyInstance(dX, dY, dXSize, dYSize, Orientation.IDENT);
					}
					return false;
				}
				String newPinName = ElectricObject.uniqueObjectName(pinName, cell, Export.class, false, true);
				reportWarning("Cell already has an export named '" + pinName + "' on node " +
					existingNI.getProto().describe(false) + ". Making new export '" + newPinName + "' on node " + curPin.np.describe(false),
						existingNI, cell);
				pinName = newPinName;
			}

			// create the pin and export
			NodeInst ni = makeNode(curPin.np, loc, sX, sY, cell);
			if (ni == null) return true;
			PortInst pi = ni.findPortInstFromProto(curPin.np.getPort(0));
			Export e = Export.newInstance(cell, pi, pinName, ep, portCharacteristic);
			if (e == null)
			{
				reportError("Unable to create pin name", cell);
				return true;
			}
		}

		return false;
	}

	/*************** COMPONENTS ***************/

	private boolean readComponents(Cell cell, Library lib)
		throws IOException
	{
		if (ignoreToSemicolon("COMPONENTS", cell)) return true;
		for(;;)
		{
			if (job != null && job.checkAbort())
			{
				System.out.println("DEF import aborted!");
				return true;
		    }

            // get the next keyword
			String key = mustGetKeyword("COMPONENTs", cell);
			if (key == null) return true;
			if (key.equals("-"))
			{
				if (readComponent(cell, lib)) return true;
				continue;
			}

			if (key.equalsIgnoreCase("END"))
			{
				key = getAKeyword();
				break;
			}

			// ignore the keyword
			if (ignoreToSemicolon(key, cell)) return true;
		}
		return false;
	}

	private Map<String,Cell> dummyCells = new HashMap<String,Cell>();

	private Cell makeDummyCell(String name, Library lib)
	{
		Cell cell = dummyCells.get(name);
		if (cell != null) return cell;

		reportWarning("Cell " + name + " not found: making a dummy cell for it", cell);
		cell = Cell.makeInstance(ep, lib, name);
		dummyCells.put(name, cell);

		PrimitiveNode cornerNp = Generic.tech().essentialBoundsNode;
		double cornerWid = cornerNp.getDefWidth(ep);
		double cornerHei = cornerNp.getDefHeight(ep);
		NodeInst.makeInstance(cornerNp, ep, EPoint.fromLambda(-50, -50), cornerWid, cornerHei, cell, Orientation.RR, null);
		NodeInst.makeInstance(cornerNp, ep, EPoint.fromLambda(50, 50), cornerWid, cornerHei, cell);

		PrimitiveNode portNp = Generic.tech().universalPinNode;
		double portWid = portNp.getDefWidth(ep);
		double portHei = portNp.getDefHeight(ep);
		NodeInst ni = NodeInst.makeInstance(portNp, ep, EPoint.fromLambda(0, 0), portWid, portHei, cell);
		Export.newInstance(cell, ni.getOnlyPortInst(), "dummyPort", ep);
		return cell;
	}

	/**
	 * cell is the parent cell
	 */
	private boolean readComponent(Cell cell, Library lib)
		throws IOException
	{
		// get the component name and model name
		String key = mustGetKeyword("COMPONENT", cell);
		if (key == null) return true;
		String compName = key;
		String compNameLC = compName.toLowerCase();
		key = mustGetKeyword("COMPONENT", cell);
		if (key == null) return true;
		String modelName = key;
		// find the named cell
		Cell np;
		if (cell.getView() != null)
		{
			np = getNodeProto(modelName, cell.getLibrary(), cell);
		} else
		{
			/* cell does not have a view yet, have no idea
			 * what view we need, so just get the first one
			 */
			np = getNodeProto(modelName, cell.getLibrary());
		}
		if (np == null)
		{
			if (localPrefs.makeDummyCells)
			{
				np = makeDummyCell(modelName, lib);
			} else
			{
				reportError("Unknown cell (" + modelName + ").  To allow this, use DEF Preferences and check 'Make dummy cells for unknown cells'", cell);
				return true;
			}
		}

		double nx = 0,  ny = 0;
		boolean hasLocation = false;
		double sX = np.getDefWidth();
		double sY = np.getDefHeight();
		Orientation or = Orientation.IDENT;
		for(;;)
		{
			// get the next keyword
			key = mustGetKeyword("COMPONENT", cell);
			if (key == null) return true;
			if (key.equals("+"))
			{
				key = mustGetKeyword("COMPONENT", cell);
				if (key == null) return true;
				if (key.equalsIgnoreCase("PLACED") || 
					key.equalsIgnoreCase("FIXED") || key.equalsIgnoreCase("COVER"))
				{
					// handle placement
					Point2D pt = readCoordinate(cell, false);
					if (pt == null) return true;
					nx = pt.getX();
					ny = pt.getY();
					hasLocation = true;
					or = FetchOrientation(cell);
					continue;
				}
				continue;
			}

			if (key.equals(";")) break;
		}

		EPoint loc = EPoint.fromLambda(nx, ny);
		if (acceptNode(loc, sX, sY))
		{
			NodeInst ni = makeNodeMoreInfo(np, loc, sX, sY, cell, or, compName);
			if (ni == null) return true;
			instanceMap.put(compNameLC, ni);
			if (!hasLocation)
			{
				reportWarning("Instance " + compName + " of model " + modelName +
					" has no location in cell " + cell.describe(false) + ". Placing it at (0,0)", ni, cell);
			}
		} else
		{
			if (dummyNodeInst == null) dummyNodeInst = NodeInst.makeDummyInstance(np, ep);
			instanceMap.put(compNameLC, dummyNodeInst);
		}
		return false;
	}

	/*************** NETS ***************/
	private boolean readNets(Cell cell, boolean special, Library lib)
		throws IOException
	{
		if (special) specialNetsHT = new HashMap<String,PortInst>();
			else normalNetsHT = new HashMap<String,PortInst>();
		initNets();

		// get the number of nets
		int numNets = 0;
		String key = mustGetKeyword("NETs", cell);
		if (key == null) return true;
		if (TextUtils.isANumber(key)) numNets = TextUtils.atoi(key);
		if (!key.equals(";"))
		{
			if (ignoreToSemicolon(key, cell)) return true;
		}

		for(int net = 1; ; net++)
		{
			if (LIMITNETS > 0 && net >= LIMITNETS)
			{
				ignoreBlock(special ? "SPECIALNETS" : "NETS", cell);
				return false;
			}
            if (job != null && job.checkAbort())
            {
            	System.out.println("DEF import aborted!");
            	return true;
            }

			// get the next keyword
			key = mustGetKeyword("NETs", cell);
			if (key == null) return true;
			if (key.equals("-"))
			{
				boolean fail = readNet(cell, special, net, numNets, lib);
				if (fail) return true;
				continue;
			}
			if (key.equalsIgnoreCase("END"))
			{
				key = getAKeyword();
				break;
			}
		}
		connectSpecialNormalNets();
		return false;
	}

	/**
	 * Look for special nets that need to be merged with normal nets
	 * Synopsys Astro router places patches of metal in special nets
	 * to cover normal nets as a method of filling notches
	 */
	private void connectSpecialNormalNets()
	{
		if (specialNetsHT == null) return;
		if (normalNetsHT == null) return;
		if (!localPrefs.logicalPlacement) return;
		for (String netName : specialNetsHT.keySet())
		{
			PortInst specPi = specialNetsHT.get(netName);
			PortInst normalPi = null;
			if (normalNetsHT.containsKey(netName))
			{
				normalPi = normalNetsHT.get(netName);
				if (normalPi != null)
				{
					// create a logical net between these two points
					if (makeUnroutedConnection(specPi, normalPi, null)) return;
				}
			}
		}
	}

	private boolean readNet(Cell cell, boolean special, int netNum, int totalNets, Library lib)
		throws IOException
	{
		if (schImport && special)
		{
			// when doing schematic import, ignore special nets
			ignoreToSemicolon("NET", cell);
			return false;
		}

		// get the net name
		String key = mustGetKeyword("NET", cell);
		if (key == null) return true;
		String netName = translateDefName(key);    // save this so net can be placed in hash table
		String arcNameToUse = (!special) ? netName : null;
		
		// Checking first if net name was already modified due to name conflicts
		Map<String,String> map = cellModifiedNetNames.get(cell);
		String newN = null;
		
		if (map != null)
			newN = map.get(netName);
		if (newN == null) // no conflict found yet
		{
			Set<String> names = cellOriginalNetNames.get(cell);
			boolean existingName = false;
			if (names != null)
				existingName = names.contains(netName);
			if (existingName) // name already taken
			{
				ArcInst ai = cell.findArc(netName);
				
				if (ai != null)
				{
                    for (;;) {
                        Long l = cellAutonames.get(cell);
                        l = Long.valueOf(l == null ? 0 : l.longValue() + 1);
                        cellAutonames.put(cell, l);
                        newN = l + netName;
                        if (cell.findArc(newN) == null) {
                            break;
                        }
                    }
					if (Job.getDebug())
						System.out.println("Net name already taken '" + netName + "'. Using '" + newN + "'");
					if (map == null)
					{
						map = new HashMap<String,String>();
						cellModifiedNetNames.put(cell, map);
					}
					map.put(netName, newN);
				}
			}
			else // new name should be added to the original name sets
			{
				if (names == null)
				{
					names = new HashSet<String>();
					cellOriginalNetNames.put(cell, names);
				}
				names.add(netName);
			}
		}
		else
		{
			// name already in used -> prefer to assign null name
			newN = null;
			arcNameToUse = null;
		}
		if (newN != null)
			arcNameToUse = newN; // new name to use.

		// get the next keyword
		key = mustGetKeyword("NET", cell);
		if (key == null) return true;

		// scan the "net" statement
		boolean adjustPinLocPi = false;
		boolean adjustPinLocLastPi = false;
		boolean wantPinPairs = true;
		boolean doingRect = false;
		boolean connectAllComponents = false;
		String wildcardPort = null;
		double lastX = 0, lastY = 0;
		double curX = 0, curY = 0;
		double specialWidth = 0;
		ArcProto.Function specialFunction = null;
		boolean pathStart = true;
		PortInst lastLogPi = null;
		PortInst lastPi = null;
		EPoint lastPT = null;
		boolean foundCoord = false;
		boolean stackedViaFlag = false;

		GetLayerInformation li = null;
		String currentLayer = null;
		Integer currentMask = null;
		for(;;)
		{
            if (job != null && job.checkAbort())
            {
            	System.out.println("DEF import aborted!");
            	return true;
            }
            
			// examine the next keyword
			if (key.equals(";"))
			{
				if (lastPi != null)
				{
					// remember at least one physical port instance for this net!
					if (special) specialNetsHT.put(netName, lastPi);
						else normalNetsHT.put(netName, lastPi);
				}
				if (lastLogPi != null && lastPi != null && localPrefs.logicalPlacement)
				{
					// connect logical network and physical network so that DRC passes
					if (!localPrefs.ignoreLogicalInSpecialNets || !special)
					{
						boolean fail = makeUnroutedConnection(lastPi, lastLogPi, arcNameToUse);
						arcNameToUse = null;
						if (fail) return true;
					}
				}
				currentMask = null; // clean the mask here
				break;
			}

			if (key.equals("+"))
			{
				wantPinPairs = false;
				doingRect = false;
				specialWidth = 0;
				specialFunction = null;
				
				if (schImport)
				{
					// ignore the remainder
					ignoreToSemicolon("NET", cell);
					break;
				}
				key = mustGetKeyword("NET", cell);
				if (key == null) return true;

				if (key.equalsIgnoreCase("USE"))
				{
					// ignore "USE" keyword
					key = mustGetKeyword("NET", cell);
					if (key == null) return true;
				} else  if (key.equalsIgnoreCase("SHIELDNET"))
				{
					// ignore "SHIELDNET" keyword
					key = mustGetKeyword("NET", cell);
					if (key == null) return true;
				} else if (key.equalsIgnoreCase("ROUTED"))
				{
					// handle "ROUTED" keyword
					currentLayer = mustGetKeyword("NET", cell);
					if (currentLayer == null) return true;
					li = getLayerInformation(currentLayer, currentMask);
					if (li.pin == null)
					{
						reportError("Unknown routed net layer " + currentLayer + (currentMask == null ? "" : ", mask "+currentMask), cell);
						return true;
					}
					pathStart = true;
					if (special)
					{
						// special nets have width here
						key = mustGetKeyword("NET", cell);
						if (key == null) return true;
						specialWidth = convertDEFString(key);
						specialFunction = li.arcFun;
					}
				} else if (key.equalsIgnoreCase("POLYGON"))
				{
					// handle "POLYGON" keyword
					currentLayer = mustGetKeyword("NET", cell);
					if (currentLayer == null) return true;
					li = getLayerInformation(currentLayer, currentMask);
					if (li.pin == null)
					{
						reportError("Unknown polygon layer " + currentLayer + (currentMask == null ? "" : ", mask "+currentMask), cell);
						return true;
					}
					List<Point2D> coords = new ArrayList<Point2D>();
					for(;;)
					{
						// see if the next is an open parenthesis
						key = mustGetKeyword("NET", cell);
						if (key == null) return true;
						if (!key.equals("(")) break;
						
						// get the X coordinate
						key = mustGetKeyword("NET", cell);
						if (key == null) return true;
						curX = convertDEFString(key);

						// get the Y coordinate
						key = mustGetKeyword("NET", cell);
						if (key == null) return true;
						curY = convertDEFString(key);

						// get the close parentheses
						key = mustGetKeyword("NET", cell);
						if (key == null) return true;
						if (!key.equals(")"))
						{
							reportError("Expected ')' of polygon coordinate pair", cell);
							return true;
						}
						coords.add(new Point2D.Double(curX, curY));
					}
					if (coords.size() > 2)
					{
						double lX = coords.get(0).getX();
						double hX = coords.get(0).getX();
						double lY = coords.get(0).getY();
						double hY = coords.get(0).getY();
						for(int i=1; i<coords.size(); i++)
						{
							if (coords.get(i).getX() < lX) lX = coords.get(i).getX();
							if (coords.get(i).getX() > hX) hX = coords.get(i).getX();
							if (coords.get(i).getY() < lY) lY = coords.get(i).getY();
							if (coords.get(i).getY() > hY) hY = coords.get(i).getY();
						}
						EPoint loc = EPoint.fromLambda((lX+hX)/2, (lY+hY)/2);
						EPoint[] ptrace = new EPoint[coords.size()];
						for(int i=0; i<coords.size(); i++)
							ptrace[i] = EPoint.fromLambda(coords.get(i).getX() - loc.getX(), coords.get(i).getY() - loc.getY());
						NodeInst newNi = makeNode(li, loc, hX-lX, hY-lY, cell);
						if (newNi == null) return true;
	            		newNi.setTraceRelative(ptrace, loc, Orientation.IDENT);
					}
					continue;
				} else if (key.equalsIgnoreCase("RECT"))
				{
					// handle "RECT" keyword
					currentLayer = mustGetKeyword("NET", cell);
					if (currentLayer == null) return true;
					li = getLayerInformation(currentLayer, currentMask);
					if (li.pin == null)
					{
						reportError("Unknown pin associated to net rect layer " + currentLayer + (currentMask == null ? "" : ", mask "+currentMask), cell);
						return true;
					}
					pathStart = true;
					doingRect = true;
					currentMask = null; // clean variable once information is used
				} else if (key.equalsIgnoreCase("FIXED"))
				{
					// handle "FIXED" keyword
					currentLayer = mustGetKeyword("NET", cell);
					if (currentLayer == null) return true;
					li = getLayerBasedOnNameAndMask(currentLayer, currentMask, localPrefs.unknownLayerHandling);
					if (li == null || li.pin == null)
					{
						reportError("Unknown fixed net layer " + currentLayer + (currentMask == null ? "" : ", mask "+currentMask), cell);
						return true;
					}
					pathStart = true;
					if (special)
					{
						// special nets have width here
						key = mustGetKeyword("NET", cell);
						if (key == null) return true;
						specialWidth = convertDEFString(key);
						specialFunction = li.arcFun;
					}
				} else if (key.equalsIgnoreCase("SHIELD"))
				{
					// handle "SHIELD" keyword: ignore the shield net name
					key = mustGetKeyword("NET", cell);
					if (key == null) return true;

					currentLayer = mustGetKeyword("NET", cell);
					if (currentLayer == null) return true;
					li = getLayerInformation(currentLayer, currentMask);
					if (li.pin == null)
					{
						reportError("Unknown shield net layer " + currentLayer + (currentMask == null ? "" : ", mask "+currentMask), cell);
						return true;
					}
					pathStart = true;
					if (special)
					{
						// special nets have width here
						key = mustGetKeyword("NET", cell);
						if (key == null) return true;
						specialWidth = convertDEFString(key);
						specialFunction = li.arcFun;
					}
				} else if (key.equalsIgnoreCase("SHAPE"))
				{
					// handle "SHAPE" keyword
					key = mustGetKeyword("NET", cell);
					if (key == null) return true;
				} else if (key.equalsIgnoreCase("SOURCE"))
				{
					// handle "SOURCE" keyword
					key = mustGetKeyword("NET", cell);
					if (key == null) return true;
				} else if (key.equalsIgnoreCase("ORIGINAL"))
				{
					// handle "ORIGINAL" keyword
					key = mustGetKeyword("NET", cell);
					if (key == null) return true;
				} else if (key.equalsIgnoreCase("NONDEFAULTRULE"))
				{
					// ignore "NONDEFAULTRULE" keyword
					key = mustGetKeyword("NET", cell);
					if (key == null) return true;
				} else if (key.equalsIgnoreCase("MASK"))
				{
					// get "MASK" keyword
					String maskStr = mustGetKeyword("NET", cell);
					if (maskStr == null) return true;
					currentMask = Integer.valueOf(TextUtils.atoi(maskStr));

					// redo the layer computation
					if (currentLayer != null)
					{
						li = getLayerInformation(currentLayer, currentMask);
						if (li.pin == null)
						{
							reportError("Unknown mask layer " + currentLayer + (currentMask == null ? "" : ", mask "+currentMask), cell);
							return true;
						}
						if (special) specialFunction = li.arcFun;
					}
				} else
				{
					reportError("Cannot handle '" + key + "' nets", cell);
					return true;
				}

				// get next keyword
				key = mustGetKeyword("NET", cell);
				if (key == null) return true;
				continue;
			}

			// if still parsing initial pin pairs, do so
			if (wantPinPairs)
			{
				// it must be the "(" of a pin pair
				if (!key.equals("("))
				{
					reportError("Expected '(' of pin pair", cell);
					return true;
				}

				// get the pin names
				key = mustGetKeyword("NET", cell);
				if (key == null) return true;
				PortInst pi = null;
				if (key.equalsIgnoreCase("PIN"))
				{
					// find the export
					key = mustGetKeyword("NET", cell);
					if (key == null) return true;
					key = translateDefName(key);
					Export pp = (Export)cell.findPortProto(key);
					if (pp != null) pi = pp.getOriginalPort(); else
					{
						if (!LIMITINGAREA)
						{
							reportWarning("Unknown pin '" + key + "' on cell '" + cell.describe(false) + "'", cell);
							if (ignoreToSemicolon("NETS", cell)) return true;
							return false;
						}
					}
				} else
				{
					NodeInst found = null;
					if (key.equals("*")) connectAllComponents = true; else
					{
						connectAllComponents = false;
						String lcKey = key.toLowerCase();
						found = instanceMap.get(lcKey);
						if (found == null)
						{
							reportError("Unknown component '" + key + "' on cell '" + cell.describe(false) + "'", cell);
							return true;
						}
					}

					// get the port name
					key = mustGetKeyword("NET", cell);
					if (key == null) return true;
					if (connectAllComponents) wildcardPort = key; else
					{
						if (found != dummyNodeInst)
						{
							PortProto pp = found.getProto().findPortProto(key);
							if (pp == null)
							{
								pp = found.getProto().findPortProto("dummyPort");
								if (pp == null)
								{
									if (!localPrefs.ignoreUngeneratedPins) // if the pin is not generated by LEF -> won't be found here
									{
										reportError("Unknown port '" + key + "' on component " + found, cell);
										return true;
									}
									else
										reportWarning("Unknown port '" + key + "' on component " + found, cell);
								}
							}
							if (pp != null)
								pi = found.findPortInstFromProto(pp);
						}
					}
				}

				// get the close parentheses
				key = mustGetKeyword("NET", cell);
				if (key == null) return true;
				if (!key.equals(")"))
				{
					reportError("Expected ')' of pin pair", cell);
					return true;
				}
				if (pi != null)
				{
					if (localPrefs.logicalPlacement)
					{
						if (!localPrefs.ignoreLogicalInSpecialNets || !special)
						{
							if (connectAllComponents)
							{
								// must connect all components in netlist
								pi = connectGlobal(cell, wildcardPort);
								if (pi == null) return true;
							} else
							{
								if (lastLogPi != null)
								{
									boolean fail = makeUnroutedConnection(pi, lastLogPi, arcNameToUse);
									arcNameToUse = null;
									if (fail) return true;
								}
							}
						}
					}
					lastLogPi = pi;
				}

				// get the next keyword and continue parsing
				key = mustGetKeyword("NET", cell);
				if (key == null) return true;
				
				// ignore virtual location
				if (key.equalsIgnoreCase("VIRTUAL"))
				{
					// ignore virtual coordinates
					key = mustGetKeyword("NET", cell);
					if (key == null) return true;
					if (!key.startsWith("("))
					{
						reportError("Expected VIRTUAL coordinates", cell);
						return true;
					}
					for(;;)
					{
						key = mustGetKeyword("NET", cell);
						if (key == null) return true;
						if (key.endsWith(")")) break;
					}
					key = mustGetKeyword("NET", cell);
					if (key == null) return true;
				}
				continue;
			}

			// handle "new" start of coordinate trace
			if (key.equalsIgnoreCase("NEW"))
			{
				// Connect last created segment to logical network
				if (lastLogPi != null && lastPi != null && localPrefs.logicalPlacement)
				{
					// connect logical network and physical network so that DRC passes
					if (!localPrefs.ignoreLogicalInSpecialNets || !special)
					{
						boolean fail = makeUnroutedConnection(lastPi, lastLogPi, arcNameToUse);
						arcNameToUse = null;
						if (fail) return true;
					}
				}

				currentLayer = mustGetKeyword("NET", cell);
				if (currentLayer == null) return true;
				li = getLayerInformation(currentLayer, currentMask);
				if (li.pin == null)
				{
					reportError("Unknown new net layer " + currentLayer + (currentMask == null ? "" : ", mask "+currentMask), cell);
					return true;
				}
				pathStart = true;
				key = mustGetKeyword("NET", cell);
				if (key == null) return true;
				if (special)
				{
					// specialnets have width here
					specialWidth = convertDEFString(key);
					specialFunction = li.arcFun;
					
					// get the next keyword
					key = mustGetKeyword("NET", cell);
					if (key == null) return true;
				}
				continue;
			}

			if (!stackedViaFlag) foundCoord = false;

			if (key.equals("("))
			{
				// get the X coordinate
				foundCoord = true;
				key = mustGetKeyword("NET", cell);
				if (key == null) return true;
				if (key.equals("*")) curX = lastX; else
				{
					curX = convertDEFString(key);
				}

				// get the Y coordinate
				key = mustGetKeyword("NET", cell);
				if (key == null) return true;
				if (key.equals("*")) curY = lastY; else
				{
					curY = convertDEFString(key);
				}

				// get the close parentheses
				key = mustGetKeyword("NET", cell);
				if (key == null) return true;

				// could be an extension factor
				if (TextUtils.isANumber(key))
				{
					// ignore extension
					key = mustGetKeyword("NET", cell);
					if (key == null) return true;
				}

				// must be a close parentheses
				if (!key.equals(")"))
				{
					reportError("Expected ')' of coordinate pair", cell);
					return true;
				}
			}

			/*
			 * if stackedViaFlag is set, then we have already fetched
			 * this Via key word, so don't fetch next keyword
			 */
			if (!stackedViaFlag)
			{
				// get the next keyword
				key = mustGetKeyword("NET", cell);
				if (key == null) return true;

				if (key.equalsIgnoreCase("MASK"))
				{
					// get "MASK" value
					String maskStr = mustGetKeyword("NET", cell);
					if (maskStr == null) return true;
					currentMask = Integer.valueOf(TextUtils.atoi(maskStr));

					// redo the layer computation
					if (currentLayer != null)
					{
						li = getLayerInformation(currentLayer, currentMask);
						if (li.pin == null)
						{
							reportError("Unknown mask layer " + currentLayer + (currentMask == null ? "" : ", mask "+currentMask), cell);
							return true;
						}
						if (special) specialFunction = li.arcFun;
					}

					// get the next keyword
					key = mustGetKeyword("NET", cell);
					if (key == null) return true;
				}
			}

			// see if it is a via name
			ViaDef vd = findViaDef(key, lib, currentMask);

			// stop now if not placing physical nets
			if (!localPrefs.physicalPlacement || schImport)
			{
				// ignore the next keyword if a via name is coming
				if (vd != null)
				{
					key = mustGetKeyword("NET", cell);
					if (key == null) return true;
				}
				continue;
			}

			// if a via is mentioned next, use it
			PortInst pi = null;
			EPoint piPT = null;
			boolean placedVia = false;
			if (vd != null)
			{
				// place the via at this location
				double sX = vd.sX;
				double sY = vd.sY;
				if (vd.via == null)
				{
					reportError("Cannot create via '" + vd.viaName + "'", cell);
					return true;
				}

				// see if there is a connection point here when starting a path
				if (pathStart)
				{
					if (!localPrefs.usePureLayerNodes)
						lastPi = findConnection(curX, curY, li.arc, cell, null);
					EPoint loc = EPoint.fromLambda(curX, curY);
					if (acceptNode(loc, 0, 0)) lastPT = loc;
				}

				// create the via
				SizeOffset so = vd.via.getProtoSizeOffset();
				sX += so.getLowXOffset() + so.getHighXOffset();
				sY += so.getLowYOffset() + so.getHighYOffset();
				EPoint loc = EPoint.fromLambda(curX, curY);
				if (acceptNode(loc, sX, sY))
				{
					NodeInst ni = makeNode(vd.via, loc, sX, sY, cell);
					if (ni == null) return true;
					if (ni.getNumPortInsts() > 0) pi = ni.getPortInst(0);
					piPT = EPoint.fromLambda(curX, curY);

					// if the path starts with a via, wire it
					double width = li.getWidth(ep);
					if (special) 
					{
						ensureArcFunctions(specialFunction, li.arcFun);
						width = specialWidth;
					}
					else
					{
						if (widthsFromLEF != null)
						{
							// get the width from the LEF file
							Double wid = widthsFromLEF.get(li.arc);
							if (wid != null) width = wid.doubleValue();
						}
					}
					if (li.arc == null || localPrefs.usePureLayerNodes)
					{
						if ((pathStart || doingRect) && lastPT != null && foundCoord)
						{
							if (!localPrefs.ignorePhysicalInNets || special)
							{
								if (lastPT.getX() != piPT.getX() || lastPT.getY() != piPT.getY())
								{
									double lX = Math.min(lastPT.getX(), piPT.getX()) - width/2;
									double hX = Math.max(lastPT.getX(), piPT.getX()) + width/2;
									double lY = Math.min(lastPT.getY(), piPT.getY()) - width/2;
									double hY = Math.max(lastPT.getY(), piPT.getY()) + width/2;
									if (LIMITINGAREA)
									{
										if (hX > MAXX) hX = MAXX;
										if (hY > MAXY) hY = MAXY;
									}
									EPoint locNi = EPoint.fromLambda((lX+hX)/2, (lY+hY)/2);
									double sXNi = hX - lX;
									double sYNi = hY - lY;
									NodeInst newNi = makeNode(li, locNi, sXNi, sYNi, cell);
									if (newNi == null) return true;
								}
							}
						}
					} else
					{
						if (pathStart && lastPi != null && foundCoord)
						{
							if (!localPrefs.ignorePhysicalInNets || special)
							{
								boolean fail = makeConnection(cell, li.arc, width, lastPi, pi, lastPT, piPT, arcNameToUse);
								arcNameToUse = null;
								if (fail) return true;
							}
						}
					}
				}

				// remember that a via was placed
				placedVia = true;

				// get the next keyword
				key = mustGetKeyword("NET", cell);
				if (key == null) return true;

				// check if next key is yet another via
				ViaDef vdStack = findViaDef(key, lib, currentMask);
				if (vdStack == null) stackedViaFlag = false;
				else stackedViaFlag = true;
			} else
			{
				// no via mentioned: just make a pin
				// this pin center will have to be adjusted if special! RBR
				if (li == null)
				{
					reportError("No Layer specified for pin", cell);
					return true;
				}
				EPoint testPT = EPoint.fromLambda(curX, curY);
				if (acceptNode(testPT, 0, 0))
				{
					if (!localPrefs.usePureLayerNodes)
					{
						pi = getPin(curX, curY, li, cell);
						if (pi == null) return true;
					}
					piPT = testPT;
				}
				adjustPinLocPi = true;
			}
			if (!foundCoord) continue;

			// run the wire
			if (!pathStart)
			{
				// make sure that this arc can connect to the current pin
				if (localPrefs.usePureLayerNodes)
				{
					if (piPT == null)
					{
						EPoint loc = EPoint.fromLambda(curX, curY);
						if (acceptNode(loc, 0, 0)) piPT = loc;
					}
				} else
				{
					if (pi == null || !pi.getPortProto().connectsTo(li.arc))
					{
						NodeProto np = li.pin;
						double sX = np.getDefWidth(ep);
						double sY = np.getDefHeight(ep);
						EPoint loc = EPoint.fromLambda(curX, curY);
						if (acceptNode(loc, sX, sY))
						{
							NodeInst ni = makeNode(np, loc, sX, sY, cell);
							if (ni == null) return true;
							pi = ni.getOnlyPortInst();
							piPT = EPoint.fromLambda(curX, curY);
						}
					}
				}

				// run the wire
				double width = li.getWidth(ep);
				//width = 0;
				
				if (special) 
				{
					if (specialWidth != 0)
					{
						ensureArcFunctions(specialFunction, li.arcFun);
						width = specialWidth;
					}
				}
				else
				{
					if (widthsFromLEF != null)
					{
						// get the width from the LEF file
						Double wid = widthsFromLEF.get(li.arc);
						if (wid != null) width = wid.doubleValue();
					}
				}
				if (adjustPinLocLastPi && special)
				{
					// starting pin; have to adjust the last pin location
					double dX = 0;
					double dY = 0;
					if (curX != lastX)
					{
						// horizontal route
						dX = width/2;  // default, adjust left
						if (curX < lastX) dX = -dX; // route runs right to left, adjust right
					}
					if (curY != lastY)
					{
						// vertical route
						dY = width/2; // default, adjust up
						if (curY < lastY) dY = -dY; // route runs top to bottom, adjust down
					}
					if (lastPi != null) lastPi.getNodeInst().move(dX, dY);
					if (lastPT != null) lastPT = EPoint.fromLambda(lastPT.getX()+dX, lastPT.getY()+dY);
					adjustPinLocLastPi = false;
				}

				/* note that this adjust is opposite of previous since
				 * this pin is on the end of the wire instead of the beginning
				 */
				if (!doingRect && adjustPinLocPi && special)
				{
					// ending pin; have to adjust the last pin location
					double dX = 0;
					double dY = 0;
					if (curX != lastX)
					{
						// horizontal route
						dX = -width/2;  // default, adjust right
						if (curX < lastX) dX = -dX; // route runs right to left, adjust left
					}
					if (curY != lastY)
					{
						// vertical route
						dY = -width/2; // default, adjust down
						if (curY < lastY) dY = -dY; // route runs top to bottom, adjust up
					}
					if (pi != null) pi.getNodeInst().move(dX, dY);
					if (piPT != null) piPT = EPoint.fromLambda(piPT.getX()+dX, piPT.getY()+dY);
					adjustPinLocPi = false;
				}

				if (!localPrefs.ignorePhysicalInNets || special)
				{
					if (doingRect || li.arc == null || localPrefs.usePureLayerNodes)
					{
						if (lastPT != null && piPT != null)
						{
							if (lastPT.getX() != piPT.getX() || lastPT.getY() != piPT.getY())
							{
								double widthToUse = width;
								if (doingRect) widthToUse = 0;
								double lX = Math.min(lastPT.getX(), piPT.getX()) - widthToUse/2;
								double hX = Math.max(lastPT.getX(), piPT.getX()) + widthToUse/2;
								double lY = Math.min(lastPT.getY(), piPT.getY()) - widthToUse/2;
								double hY = Math.max(lastPT.getY(), piPT.getY()) + widthToUse/2;
								if (LIMITINGAREA)
								{
									if (hX > MAXX) hX = MAXX;
									if (hY > MAXY) hY = MAXY;
								}
								EPoint locNi = EPoint.fromLambda((lX+hX)/2, (lY+hY)/2);
								double sXNi = hX - lX;
								double sYNi = hY - lY;
								NodeInst newNi = makeNode(li, locNi, sXNi, sYNi, cell);
								if (newNi == null) return true;
							}
						}
					} else
					{
						boolean fail = makeConnection(cell, li.arc, width, lastPi, pi, lastPT, piPT, arcNameToUse);
						arcNameToUse = null;
						if (fail) return true;
					}
				}
			}
			lastX = curX;   lastY = curY;
			pathStart = false;
			lastPi = pi;
			lastPT = piPT;
			adjustPinLocLastPi = adjustPinLocPi;
			adjustPinLocPi = false;

			// switch layers to the other one supported by the via
			if (placedVia)
			{
				if (li.equals(vd.gLay1))
				{
					li = vd.gLay2;
				} else if (li.equals(vd.gLay2))
				{
					li = vd.gLay1;
				}
			}

			// if the path ends here, connect it
			if (key.equalsIgnoreCase("NEW") || key.equals(";"))
			{
				// see if there is a connection point here when starting a path
				double width = li.getWidth(ep);
				if (special) 
				{
					if (specialWidth != 0)
					{
						ensureArcFunctions(specialFunction, li.arcFun);
						width = specialWidth;
					}
				}
				else
				{
					if (widthsFromLEF != null)
					{
						// get the width from the LEF file
						Double wid = widthsFromLEF.get(li.arc);
						if (wid != null) width = wid.doubleValue();
					}
				}
				if (li.arc == null || localPrefs.usePureLayerNodes)
				{
					if (piPT != null)
					{
						if (!localPrefs.ignorePhysicalInNets || special)
						{
							if (curX != piPT.getX() || curY != piPT.getY())
							{
								double lX = Math.min(curX, piPT.getX()) - width/2;
								double hX = Math.max(curX, piPT.getX()) + width/2;
								double lY = Math.min(curY, piPT.getY()) - width/2;
								double hY = Math.max(curY, piPT.getY()) + width/2;
								if (LIMITINGAREA)
								{
									if (hX > MAXX) hX = MAXX;
									if (hY > MAXY) hY = MAXY;
								}
								EPoint locNi = EPoint.fromLambda((lX+hX)/2, (lY+hY)/2);
								double sXNi = hX - lX;
								double sYNi = hY - lY;
								NodeProto np = li.pure; // arc.getLayer(0).getPureLayerNode();
								NodeInst newNi = makeNode(np, locNi, sXNi, sYNi, cell);
								if (newNi == null) return true;
							}
						}
					}
				} else
				{
					if (pi != null)
					{
						PortInst nextPi = findConnection(curX, curY, li.arc, cell, pi.getNodeInst());

						// if the path starts with a via, wire it
						if (nextPi != null)
						{
							if (!localPrefs.ignorePhysicalInNets || special)
							{
								boolean fail = makeConnection(cell, li.arc, width, pi, nextPi, piPT, EPoint.fromLambda(curX, curY), arcNameToUse);
								arcNameToUse = null;
								if (fail) return true;
							}
						}
					}
				}
			}
		}
		return false;
	}

	private PortInst connectGlobal(Cell cell, String portName)
	{
		PortInst lastPi = null;
		for(Iterator<NodeInst> it = cell.getNodes(); it.hasNext(); )
		{
			NodeInst ni = it.next();
			PortProto pp = ni.getProto().findPortProto(portName);
			if (pp == null) continue;
			PortInst pi = ni.findPortInstFromProto(pp);
			EPoint pt = pi.getCenter();
			if (!acceptNode(pt, 0, 0)) continue;
			if (lastPi != null)
			{
				// do connection
				boolean fail = makeUnroutedConnection(pi, lastPi, null);
				if (fail) return null;
			}
			lastPi = pi;
		}
		return lastPi;
	}

	/*************** VIAS ***************/

	private boolean readVias(Library lib, Cell cell)
		throws IOException
	{
		if (ignoreToSemicolon("VIAS", cell)) return true;
		for(;;)
		{
			// get the next keyword
			String key = mustGetKeyword("VIAs", cell);
			if (key == null) return true;
			if (key.equals("-"))
			{
				if (readVia(lib)) return true;
				continue;
			}

			if (key.equalsIgnoreCase("END"))
			{
				key = getAKeyword();
				break;
			}

			// ignore the keyword
			if (ignoreToSemicolon(key, cell)) return true;
		}
		return false;
	}

	private class ViaRect
	{
		String layer;
		Point2D ll, ur;
	}

	private class ViaGenerator
	{
		double cutSizeX, cutSizeY, cutSpacingX, cutSpacingY;
		double leftEnclosure, rightEnclosure, topEnclosure, bottomEnclosure;
		int rowColX, rowColY;
		String metalLayer1, metalLayer2;
		List<ViaRect> rects;
		GetLayerInformation viaLayer;
		ViaDef noMaskDef;
		Map<Integer,ViaDef> maskDefs;

		ViaGenerator()
		{
			cutSizeX = cutSizeY = cutSpacingX = cutSpacingY = 0;
			leftEnclosure = rightEnclosure = topEnclosure = bottomEnclosure = 0;
			rowColX = rowColY = 0;
			metalLayer1 = metalLayer2 = null;
			rects = new ArrayList<ViaRect>();
			viaLayer = null;
			noMaskDef= null;
			maskDefs = new HashMap<Integer,ViaDef>();
		}
	}

	private boolean readVia(Library lib)
		throws IOException
	{
		if (schImport)
		{
			ignoreToSemicolon("VIA", null);
			return false;
		}

		// get the via name
		String key = mustGetKeyword("VIA", null);
		if (key == null) return true;

		ViaGenerator vg = new ViaGenerator();
		allViaGenerators.put(key.toLowerCase(), vg);

		// get the via information
		for(;;)
		{
			// get the next keyword
			key = mustGetKeyword("VIA", null);
			if (key == null) return true;
			if (key.equals("+"))
			{
				key = mustGetKeyword("VIA", null);
				if (key == null) return true;
				if (key.equalsIgnoreCase("CUTSIZE"))
				{
					// get the X cut size
					key = mustGetKeyword("VIA", null);
					if (key == null) return true;
					vg.cutSizeX = convertDEFString(key);

					// get the Y cut size
					key = mustGetKeyword("VIA", null);
					if (key == null) return true;
					vg.cutSizeY = convertDEFString(key);
					continue;
				}
				if (key.equalsIgnoreCase("CUTSPACING"))
				{
					// get the X cut spacing
					key = mustGetKeyword("VIA", null);
					if (key == null) return true;
					vg.cutSpacingX = convertDEFString(key);

					// get the Y cut size
					key = mustGetKeyword("VIA", null);
					if (key == null) return true;
					vg.cutSpacingY = convertDEFString(key);
					continue;
				}
				if (key.equalsIgnoreCase("ENCLOSURE"))
				{
					// get the left via enclosure
					key = mustGetKeyword("VIA", null);
					if (key == null) return true;
					vg.leftEnclosure = convertDEFString(key);

					// get the bottom via enclosure
					key = mustGetKeyword("VIA", null);
					if (key == null) return true;
					vg.bottomEnclosure = convertDEFString(key);

					// get the right via enclosure
					key = mustGetKeyword("VIA", null);
					if (key == null) return true;
					vg.rightEnclosure = convertDEFString(key);

					// get the top via enclosure
					key = mustGetKeyword("VIA", null);
					if (key == null) return true;
					vg.topEnclosure = convertDEFString(key);
					continue;
				}
				if (key.equalsIgnoreCase("ROWCOL"))
				{
					// get the X via repeat
					key = mustGetKeyword("VIA", null);
					if (key == null) return true;
					vg.rowColX = TextUtils.atoi(key);

					// get the Y via repeat
					key = mustGetKeyword("VIA", null);
					if (key == null) return true;
					vg.rowColY = TextUtils.atoi(key);
					continue;
				}
				if (key.equalsIgnoreCase("LAYERS"))
				{
					// get the lower metal layer
					key = mustGetKeyword("VIA", null);
					if (key == null) return true;
					vg.metalLayer1 = key;

					// get the via layer
					key = mustGetKeyword("VIA", null);
					if (key == null) return true;
					vg.viaLayer = getLayerBasedOnNameAndMask(key, null, localPrefs.unknownLayerHandling);
					if (vg.viaLayer == null || vg.viaLayer.pure == null)
					{
						reportError("Layer " + key + " not found", null);
						return true;
					}

					// get the high metal layer
					key = mustGetKeyword("VIA", null);
					if (key == null) return true;
					vg.metalLayer2 = key;
					continue;
				}
				if (key.equalsIgnoreCase("RECT"))
				{
					// handle definition of a via rectangle
					key = mustGetKeyword("VIA", null);
					if (key == null) return true;
					ViaRect vr = new ViaRect();
					vr.layer = key;
					vr.ll = readCoordinate(null, false);
					if (vr.ll == null) return true;
					vr.ur = readCoordinate(null, false);
					if (vr.ur == null) return true;
					vg.rects.add(vr);
					continue;
				}
			}

			if (key.equals(";")) break;
		}
		return false;
	}

	/*************** UNITS ***************/

	private boolean readUnits(Cell cell)
		throws IOException
	{
		// get the "DISTANCE" keyword
		String key = mustGetKeyword("UNITS", cell);
		if (key == null) return true;
		if (!key.equalsIgnoreCase("DISTANCE"))
		{
			reportError("Expected 'DISTANCE' after 'UNITS'", cell);
			return true;
		}

		// get the "MICRONS" keyword
		key = mustGetKeyword("UNITS", cell);
		if (key == null) return true;
		if (!key.equalsIgnoreCase("MICRONS"))
		{
			reportError("Expected 'MICRONS' after 'UNITS'", cell);
			return true;
		}

		// get the amount
		key = mustGetKeyword("UNITS", cell);
		if (key == null) return true;
		scaleUnits = TextUtils.atof(key) * localPrefs.overallscale;

		// ignore the keyword
		if (ignoreToSemicolon("UNITS", cell)) return true;
		return false;
	}

	/*************** SUPPORT ***************/

	private boolean ignoreToSemicolon(String command, Cell cell)
		throws IOException
	{
		// ignore up to the next semicolon
		for(;;)
		{
			String key = mustGetKeyword(command, cell);
			if (key == null) return true;
			if (key.equals(";")) break;
		}
		return false;
	}

	private boolean ignoreBlock(String command, Cell cell)
		throws IOException
	{
		for(;;)
		{
			// get the next keyword
			String key = mustGetKeyword(command, cell);
			if (key == null) return true;

			if (key.equalsIgnoreCase("END"))
			{
				getAKeyword();
				break;
			}
		}
		return false;
	}

	/**
	 * Method to read coordinates of the form: ( X Y )
	 * @param cell the Cell in which this is being read
	 * @param sawOpenParen true to ignore the open parenthesis (already parsed)
	 * @return a Point2D with the coordinates
	 * @throws IOException
	 */
	private Point2D readCoordinate(Cell cell, boolean sawOpenParen)
		throws IOException
	{
		if (!sawOpenParen)
		{
			// get "("
			String key = mustGetKeyword("coordinate", cell);
			if (key == null) return null;
			if (!key.equals("("))
			{
				reportError("Expected '(' in coordinate", cell);
				return null;
			}
		}

		// get X
		String key = mustGetKeyword("coordinate", cell);
		if (key == null) return null;
		double x = convertDEFString(key);

		// get Y
		key = mustGetKeyword("coordinate", cell);
		if (key == null) return null;
		double y = convertDEFString(key);

		// get ")"
		key = mustGetKeyword("coordinate", cell);
		if (key == null) return null;

		// allow an extension factor
		if (TextUtils.isANumber(key))
		{
			key = mustGetKeyword("coordinate", cell);
			if (key == null) return null;
		}
		if (!key.equals(")"))
		{
			reportError("Expected ')' in coordinate", cell);
			return null;
		}
		return new Point2D.Double(x, y);
	}

	private String mustGetKeyword(String where, Cell cell)
		throws IOException
	{
		String key = getAKeyword();
		if (key == null) reportError("EOF parsing " + where  + " at line " + lineReader.getLineNumber(), cell);
		return key;
	}

	private double convertDEFString(String key)
	{
		double v = TextUtils.atof(key) / scaleUnits;
		return TextUtils.convertFromDistance(v, curTech, TextUtils.UnitScale.MICRO);
	}

	/**
	 * Find nodeProto with same view as the parent cell
	 */
	private Cell getNodeProto(String name, Library curlib, Cell parent)
	{
		// first see if this cell is in the current library
		CellName cn;
		if (schImport)
		{
			cn = CellName.newName(name, View.ICON,0);
		} else
		{
			cn = CellName.newName(name, parent.getView(),0);
		}
		Cell cell = curlib.findNodeProto(cn.toString());
		if (cell != null) return cell;

		// now look in other libraries
		for(Iterator<Library> it = Library.getLibraries(); it.hasNext(); )
		{
			Library lib = it.next();
			if (lib.isHidden()) continue;
			if (lib == curlib) continue;
			cell = lib.findNodeProto(name);
			if (cell != null)
				return cell;
		}
		return null;
	}

	private Cell getNodeProto(String name, Library curlib)
	{
		// first see if this cell is in the current library
		Cell cell = curlib.findNodeProto(name);
		if (cell != null) return cell;

		// now look in other libraries
		for(Iterator<Library> it = Library.getLibraries(); it.hasNext(); )
		{
			Library lib = it.next();
			if (lib.isHidden()) continue;
			if (lib == curlib) continue;
			cell = lib.findNodeProto(name);
			if (cell != null) return cell;
		}
		return null;
	}

	// RBR - temporary method until I figure out why in Java 6.0 my use of GetOrientation generates a compile error
	private Orientation FetchOrientation(Cell cell) throws IOException
	{
		String key = mustGetKeyword("orientation", cell);
		if (key == null) return null;
		int angle;
		boolean transpose = false;
		if (key.equalsIgnoreCase("N"))  { angle = 0;    } else
		if (key.equalsIgnoreCase("S"))  { angle = 1800; } else
		if (key.equalsIgnoreCase("E"))  { angle = 2700; } else
		if (key.equalsIgnoreCase("W"))  { angle = 900;  } else
		if (key.equalsIgnoreCase("FN")) { angle = 900;   transpose = true; } else
		if (key.equalsIgnoreCase("FS")) { angle = 2700;  transpose = true; } else
		if (key.equalsIgnoreCase("FE")) { angle = 1800;  transpose = true; } else
		if (key.equalsIgnoreCase("FW")) { angle = 0;     transpose = true; } else
		{
			reportError("Unknown orientation (" + key + ")", cell);
			return null;
		}
		return (Orientation.fromC(angle, transpose));
	}

	private class GetOrientation
	{
		private Orientation orient;

		private GetOrientation(Cell cell)
			throws IOException
		{
			String key = mustGetKeyword("orientation", cell);
			if (key == null) return;
			int angle;
			boolean transpose = false;
			if (key.equalsIgnoreCase("N")) { angle = 0; } else
			if (key.equalsIgnoreCase("S")) { angle = 1800; } else
			if (key.equalsIgnoreCase("E")) { angle = 2700; } else
			if (key.equalsIgnoreCase("W")) { angle = 900; } else
			if (key.equalsIgnoreCase("FN")) { angle = 900;  transpose = true; } else
			if (key.equalsIgnoreCase("FS")) { angle = 2700; transpose = true; } else
			if (key.equalsIgnoreCase("FE")) { angle = 1800; transpose = true; } else
			if (key.equalsIgnoreCase("FW")) { angle = 0;    transpose = true; } else
			{
				reportError("Unknown orientation (" + key + ")", cell);
				return;
			}
			orient = Orientation.fromC(angle, transpose);
		}
	}

	private boolean acceptNode(EPoint loc, double sX, double sY)
	{
		if (LIMITINGAREA)
		{
			double lX = loc.getX() - sX/2;
			double lY = loc.getY() - sY/2;
			if (lX <= MAXX && lY <= MAXY) return true;
			return false;
		}
		return true;
	}

	private NodeInst makeNodeMoreInfo(NodeProto np, EPoint loc, double sX, double sY, Cell cell, Orientation or, String name)
	{
		// check if node exists first
		NodeInst ni = cell.findNode(name);
		if (ni != null)
			return ni;

		Rectangle2D searchBounds = new Rectangle2D.Double(loc.getX(), loc.getY(), 0, 0);
		for(Iterator<Geometric> it = cell.searchIterator(searchBounds); it.hasNext(); )
		{
			Geometric geom = it.next();
			if (geom instanceof NodeInst)
			{
				ni = (NodeInst)geom;
				if (ni.getProto() == np)
				{
					ERectangle bound = ni.getBounds();
					double expectedX = bound.getMinX();
					double expectedY = bound.getMinY();
					if (np instanceof Cell)
					{
						expectedX -= ((Cell)np).getBounds().getMinX();
						expectedY -= ((Cell)np).getBounds().getMinY();
					}
					if (expectedX == loc.getX() && expectedY == loc.getY())
					{
						if (Job.getDebug())
							System.out.println("Replacing previous name of "+np.describe(false) + " instance '" 
						+ ni.getName() + "' by '" + name + "'");
						ni.setName(name);
						return ni;
					}
				}
			}
		}

		// creating one
		ni = NodeInst.makeInstance(np, ep, loc, sX, sY, cell, or, name);
		if (ni == null)
		{
			reportError("Unable to create node named '" + name + "'", cell);
			return null;
		}

		// adjust position to force lower-left node corner to be at the specified location
		if (np instanceof Cell)
		{
			Cell subCell = (Cell)np;
			ERectangle bound = ni.getBounds();
			double diffX = loc.getX() - bound.getMinX() + subCell.getBounds().getMinX();
			double diffY = loc.getY() - bound.getMinY() + subCell.getBounds().getMinY();
			if (diffX != 0 || diffY != 0)
				ni.move(diffX, diffY);
		}
		return ni;
	}

	private NodeInst makeNode(NodeProto np, EPoint loc, double sX, double sY, Cell cell)
	{
		NodeInst ni = NodeInst.makeInstance(np, ep, loc, sX, sY, cell);
		if (ni == null)
		{
			reportError("Unable to create node of type "+np, cell);
			return null;
		}
		return ni;
	}
	
	private NodeInst makeNode(GetLayerInformation li, EPoint loc, double sX, double sY, Cell cell)
	{
		if (li.pure == null)
		{
			reportError("Unable to create node for layer '" + li.name + "'", cell);
			return null;
		}
		return makeNode(li.pure, loc, sX, sY, cell);
	}

	private boolean makeUnroutedConnection(PortInst pi1, PortInst pi2, String name)
	{
		if (pi1 == null || pi2 == null) return false;

		// look for other connections that also need to be made
		if (localPrefs.connectByGDSName && !pi1.getPortProto().getName().startsWith("VDD") && !pi1.getPortProto().getName().startsWith("VSS"))
		{
			NodeInst ni1 = pi1.getNodeInst();
			PortProto pe1 = pi1.getPortProto();
			NodeInst ni2 = pi2.getNodeInst();
			PortProto pe2 = pi2.getPortProto();
			if (ni1.isCellInstance())
			{
				Cell cell1 = (Cell)ni1.getProto();
				if (((Export)pe1).getVar(GDS.ORIGINAL_EXPORT_NAME) == null)
				{
					String pn1 = pe1.getName();
					for(Iterator<Export> it = cell1.getExports(); it.hasNext(); )
					{
						Export e = it.next();
						if (e == pe1 || e == pe2) continue;
						Variable var = e.getVar(GDS.ORIGINAL_EXPORT_NAME);
						if (var == null || !((String)var.getObject()).equals(pn1)) continue;
	
						// found other export to connect by name
						PortInst pi = ni1.findPortInstFromEquivalentProto(e);
						makeSingleUnroutedConnection(pi, pi2, null);
					}
				}
			}

			if (ni2.isCellInstance())
			{
				Cell cell2 = (Cell)ni2.getProto();
				if (((Export)pe2).getVar(GDS.ORIGINAL_EXPORT_NAME) == null)
				{
					String pn2 = pe2.getName();
					for(Iterator<Export> it = cell2.getExports(); it.hasNext(); )
					{
						Export e = it.next();
						if (e == pe1 || e == pe2) continue;
						Variable var = e.getVar(GDS.ORIGINAL_EXPORT_NAME);
						if (var == null || !((String)var.getObject()).equals(pn2)) continue;
	
						// found other export to connect by name
						PortInst pi = ni2.findPortInstFromEquivalentProto(e);
						makeSingleUnroutedConnection(pi1, pi, null);
					}
				}
			}
		}

		// create the requested connection
		return makeSingleUnroutedConnection(pi1, pi2, name);
	}

	private boolean makeSingleUnroutedConnection(PortInst pi1, PortInst pi2, String name)
	{
		if (LIMITINGAREA)
		{
			EPoint pt1 = pi1.getCenter();
			EPoint pt2 = pi2.getCenter();
			double lX = Math.min(pt1.getX(), pt2.getX());
			double lY = Math.min(pt1.getY(), pt2.getY());
			if (lX > MAXX || lY > MAXY) return false;
		}

		// if ports are irregular so that centers aren't inside polygons, choose a point that is
        Poly poly1 = pi1.getPoly();
        Point2D pt1 = poly1.getCenter();
        if (!poly1.isInside(pt1))
        {
        	Point2D[] pts = poly1.getPoints();
        	pt1 = pts[0];
        }
        Poly poly2 = pi2.getPoly();
        Point2D pt2 = poly2.getCenter();
        if (!poly2.isInside(pt2))
        {
        	Point2D[] pts = poly2.getPoints();
        	pt2 = pts[0];
        }

        ArcInst ai = ArcInst.makeInstance(Generic.tech().unrouted_arc, ep, pi1, pi2, pt1, pt2, name);
		if (ai == null)
		{
	        Cell cell = pi1.getNodeInst().getParent();
			System.out.println("Warning: could not create unrouted arc from (" + pt1.getX() + "," + pt1.getY() +
				") to (" + pt2.getX() + "," + pt2.getY() + ") in cell " + cell.describe(false) + ", ignoring coordinates");
	        ai = ArcInst.makeInstance(Generic.tech().unrouted_arc, ep, pi1, pi2);
	        if (ai == null)
	        {
				reportError("Could not create unrouted arc from port " + pi1.getPortProto().getName() + " of node " +
					pi1.getNodeInst().describe(false) + " to port " + pi2.getPortProto().getName() + " of node " +
					pi2.getNodeInst().describe(false), cell);
				return true;
	        }
	        ai.setName(name, ep);
		}
		return false;
	}

	private boolean makeConnection(Cell cell, ArcProto ap, double width, PortInst pi1, PortInst pi2, EPoint pt1, EPoint pt2, String name)
	{
		if (pi1 == null || pi2 == null) return false;
		if (LIMITINGAREA)
		{
			double lX = Math.min(pt1.getX(), pt2.getX());
			double lY = Math.min(pt1.getY(), pt2.getY());
			if (lX > MAXX || lY > MAXY) return false;
		}

		long gridExtendOverMin = DBMath.lambdaToGrid(0.5 * width) - ap.getBaseExtend().getGrid();
		TextDescriptor nameDescriptor = ep.getArcTextDescriptor();
		ArcInst ai = ArcInst.newInstanceNoCheck(cell, ap, name, nameDescriptor, pi1, pi2, pt1, pt2, gridExtendOverMin, ArcInst.DEFAULTANGLE, ImmutableArcInst.DEFAULT_FLAGS);

		if (ai == null)
		{
			reportError("Could not create arc", cell);
			return true;
		}
		return false;
	}
	
	/**
	 * Method to find a ViaDef given its name.
	 * Also searches Via definitions that were read in the LEF file.
	 * @param key the name of the ViaDef.
	 * @return the ViaDef (null if not found).
	 */
	private ViaDef findViaDef(String viaName, Library lib, Integer mask)
	{
		if (viaName.equals(";")) return null;
		String lcName = viaName.toLowerCase();

		// see if this via is from a VIA definition in the DEF file
		ViaGenerator vg = allViaGenerators.get(lcName);
		if (vg != null)
		{
			// see if the via cell already exists
			ViaDef vd = null;
			Cell cell = null;
			boolean build = false;
			if (mask == null)
			{
				if (vg.noMaskDef == null)
				{
					cell = Cell.makeInstance(ep, lib, viaName + "{lay}");
					vg.noMaskDef = new ViaDef(viaName, cell);
					build = true;
				}
				vd = vg.noMaskDef;
			} else
			{
				if (vg.maskDefs.get(mask) == null)
				{
					cell = Cell.makeInstance(ep, lib, viaName + Layer.DEFAULT_MASK_NAME + mask + "{lay}");
					vg.maskDefs.put(mask, new ViaDef(viaName, cell));
					build = true;
				}
				vd = vg.maskDefs.get(mask);
			}
			if (build)
			{
				// place a universal export in the via
				NodeInst ni = makeNode(Generic.tech().universalPinNode, EPoint.fromLambda(0, 0), 0, 0, cell);
				if (ni == null) return null;
				Export e = Export.newInstance(cell, ni.getOnlyPortInst(), "viaPort", ep, PortCharacteristic.UNKNOWN);
				if (e == null)
				{
					reportError("Unable to create export in " + viaName + " via", cell);
					return null;
				}

				// see if cut rules are defined
				if (vg.rowColX > 0 && vg.rowColY > 0 && vg.metalLayer1 != null && vg.metalLayer2 != null && vg.viaLayer != null)
				{
					// the via layers
					double cutsWidth = vg.rowColX * vg.cutSizeX + (vg.rowColX-1) * vg.cutSpacingX;
					double cutsHeight = vg.rowColY * vg.cutSizeY + (vg.rowColY-1) * vg.cutSpacingY;
					for(int x=0; x<vg.rowColX; x++)
					{
						for(int y=0; y<vg.rowColY; y++)
						{
							double xP = -cutsWidth/2 + x*(vg.cutSizeX + vg.cutSpacingX) + vg.cutSizeX/2;
							double yP = -cutsHeight/2 + y*(vg.cutSizeY + vg.cutSpacingY) + vg.cutSizeY/2;
							ni = makeNode(vg.viaLayer, EPoint.fromLambda(xP, yP), vg.cutSizeX, vg.cutSizeY, cell);
							if (ni == null) return null;
						}
					}

					// the via layer
					double sX = cutsWidth + vg.leftEnclosure + vg.rightEnclosure;
					double sY = cutsHeight + vg.topEnclosure + vg.bottomEnclosure;
					double cX = (vg.rightEnclosure - vg.leftEnclosure) / 2;
					double cY = (vg.topEnclosure - vg.bottomEnclosure) / 2;
					EPoint metCtr = EPoint.fromLambda(cX, cY);
					
					vd.gLay1 = getLayerBasedOnNameAndMask(vg.metalLayer1, mask, localPrefs.unknownLayerHandling);
					if (vd.gLay1 == null) return null;
					ni = makeNode(vd.gLay1, metCtr, sX, sY, cell);
					if (ni == null) return null;
					
					vd.gLay2 = getLayerBasedOnNameAndMask(vg.metalLayer2, mask, localPrefs.unknownLayerHandling);
					if (vd.gLay2 == null) return null;
					ni = makeNode(vd.gLay2, metCtr, sX, sY, cell);
					if (ni == null) return null;
				}
				for(ViaRect vr : vg.rects)
				{
					double sX = Math.abs(vr.ll.getX() - vr.ur.getX());
					double sY = Math.abs(vr.ll.getY() - vr.ur.getY());
					double cX = (vr.ll.getX() + vr.ur.getX()) / 2;
					double cY = (vr.ll.getY() + vr.ur.getY()) / 2;
					GetLayerInformation li = getLayerInformation(vr.layer, mask);
					if (li.pure == null && mask != null)
					{
						li = getLayerInformation(vr.layer, null);
						if (li.pure != null)
							reportWarning("Layer " + vr.layer + ", mask " + mask + " not found, using non-mask version", null);
					}
					if (li.pure == null)
					{
						reportError("Layer " + vr.layer + " not found", null);
						return null;
					}
					if (li.layerFun.isMetal())
					{
						if (vd.gLay1 == null) vd.gLay1 = li; else
							vd.gLay2 = li;
					}
					ni = makeNode(li, EPoint.fromLambda(cX, cY), sX, sY, cell);
					if (ni == null) return null;
				}

				// add a variable on the export that identifies the two metal layers
				if (vd.gLay1 != null && vd.gLay2 != null)
				{
					String[] preferredArcs = new String[2];
					preferredArcs[0] = vd.gLay1.arc.getFullName();
					preferredArcs[1] = vd.gLay2.arc.getFullName();
					e.newVar(Export.EXPORT_PREFERRED_ARCS, preferredArcs, ep);
				}

				// set the via size
				vd.sX = vd.via.getDefWidth(ep);
				vd.sY = vd.via.getDefHeight(ep);
			}
			return vd;
		}

		// see if this via is a cell in an existing library
		ViaDef vd = cellViaDefs.get(lcName);
		if (vd != null) return vd;

		// see if the via name is from the LEF file
		if (viaDefsFromLEF != null)
		{
			vd = viaDefsFromLEF.get(lcName);
			if (vd != null) return vd;
		}

		// see if the via name is a cell
        for (Iterator<Library> it = Library.getLibraries(); it.hasNext(); )
        {
        	Library aLib = it.next();
            if (aLib.isHidden()) continue;
            Cell cell = aLib.findNodeProto(viaName);
            if (cell != null)
            {
        		ViaDef vDef = new ViaDef(viaName, cell);
        		cellViaDefs.put(lcName, vDef);
            	vDef.sX = cell.getBounds().getWidth();
            	vDef.sY = cell.getBounds().getHeight();
            	if (cell.getNumPorts() > 0)
            	{
            		Export e = cell.getPort(0);
            		Variable var = e.getVar(Export.EXPORT_PREFERRED_ARCS);
            		if (var != null)
            		{
            			String[] preferredArcs = (String[])var.getObject();
            			vDef.gLay1 = getLayerInformation(preferredArcs[0], null);
            			vDef.gLay2 = getLayerInformation(preferredArcs[1], null);
            		} else
            		{
            			ArcProto[] cons = e.getBasePort().getConnections();
            			for(int i=0; i<cons.length; i++)
            			{
            				if (cons[i].getTechnology() != Generic.tech())
            				{
            					if (vDef.gLay1 == null) vDef.gLay1 = getLayerInformation(cons[i].getName(), null); else
            						if (vDef.gLay2 == null) vDef.gLay2 = getLayerInformation(cons[i].getName(), null);
            				}
            			}
            		}
            	}
            	return vDef;
            }
		}

		return null;
	}

	private static final boolean NEWPORTSTORAGE = false;

	private Map<Double,List<NodeInst>> portHT = null;
	private RTNode<PortInstBound> portRoot;

	private void initNets()
	{
		if (NEWPORTSTORAGE)
		{
			portRoot = RTNode.makeTopLevel();
		} else
		{
			portHT = new HashMap<Double,List<NodeInst>>();
		}
	}

	private static class PortInstBound implements RTBounds
	{
		private final PortInst pi;
		private final FixpRectangle bound;

		PortInstBound(PortInst p, FixpRectangle b)
		{
			pi = p;
			bound = b;
		}

        @Override
		public FixpRectangle getBounds() { return bound; }
	}

	/**
	 * Method to look for a connection to arcs of type "ap" in cell "cell"
	 * at (x, y).  The connection can not be on "not" (if it is not null).
	 * If found, return the PortInst.
	 */
	private PortInst findConnection(double x, double y, ArcProto ap, Cell cell, NodeInst noti)
	{
		if (NEWPORTSTORAGE)
		{
			// the new way, uses R-Trees
			Rectangle2D search = new Rectangle2D.Double(x, y, 0, 0);
			for (Iterator<PortInstBound> sea = new RTNode.Search<PortInstBound>(search, portRoot, true); sea.hasNext();)
			{
				PortInstBound inArea = sea.next();
				NodeInst ni = inArea.pi.getNodeInst();
				if (ni == noti) continue;
				for(Iterator<PortInst> it = ni.getPortInsts(); it.hasNext(); )
				{
					PortInst pi = it.next();
					if (!pi.getPortProto().connectsTo(ap)) continue;
					Poly poly = pi.getPoly();
					return pi;
				}
			}
			return null;
		} else
		{
			// the old way (faster, doesn't find ports on existing nodes)
			Double key = new Double(x+y);
			List<NodeInst> pl = portHT.get(key);
			if (pl != null)
			{
				Point2D pt = new Point2D.Double(x, y);
				for (NodeInst ni : pl)
				{
					if (ni == noti) continue;
					for(Iterator<PortInst> it = ni.getPortInsts(); it.hasNext(); )
					{
						PortInst pi = it.next();
						if (!pi.getPortProto().connectsTo(ap)) continue;
						Poly poly = pi.getPoly();
						if (poly.isInside(pt)) return pi;
					}
				}
			}
			return null;
		}
	}

	/**
	 * Method to find a connection at a given location and type.
	 * If nothing is found at that location, create a pin.
	 * @param x the X coordinate of the connection.
	 * @param y the Y coordinate of the connection.
	 * @param ap the ArcProto that must connect at that location.
	 * @param cell the Cell in which to look.
	 * @return the PortInst of the connection site (null on error).
	 */
	private PortInst getPin(double x, double y, GetLayerInformation li, Cell cell)
	{
		// if there is an existing connection, return it
		PortInst pi = findConnection(x, y, li.arc, cell, null);
		if (pi != null) return pi;

		// nothing found at this location: create a pin
		NodeProto pin = li.pin; //ap.findPinProto();
		double sX = pin.getDefWidth(ep);
		double sY = pin.getDefHeight(ep);
		NodeInst ni = makeNode(pin, EPoint.fromLambda(x, y), sX, sY, cell);
		if (ni == null) return null;
		pi = ni.getOnlyPortInst();

		if (NEWPORTSTORAGE)
		{
			// store this pin in the data structure (new way)
			portRoot = RTNode.linkGeom(null, portRoot, new PortInstBound(pi, pi.getBounds()));
		} else
		{
			// store this pin in the data structure (old way)
			Double key = new Double(x+y);
			List<NodeInst> pl = portHT.get(key);
			if (pl == null) portHT.put(key, pl = new ArrayList<NodeInst>());
			pl.add(ni);
		}

		return pi;
	}

	private String translateDefName(String name)
	{
		// remove square brackets if they are quoted with backslashes
		int quotedBracketPos = name.indexOf("\\[");
		if (quotedBracketPos >= 0)
		{
			name = name.replaceAll("\\\\\\[", "_").replaceAll("\\\\\\]", "_");
		}

		Matcher m_starleftbracket = pat_starleftbracket.matcher(name);
		Matcher m_starrightbracket = pat_starrightbracket.matcher(name);

		if (m_starleftbracket.matches() || m_starrightbracket.matches())
		{
			String tmpa, tmpb;
			Matcher m_leftbracket = pat_leftbracket.matcher(name);

			tmpa = m_leftbracket.replaceAll("[");
			Matcher m_rightbracket = pat_rightbracket.matcher(tmpa);
			tmpb = m_rightbracket.replaceAll("]");
			return(tmpb);
		}
		return name;
	}

	private void ensureArcFunctions(ArcProto.Function exp, ArcProto.Function got)
	{
		if (exp == got) return;
		System.out.println("WARNING: Arc function " + got + " found at line " + lineReader.getLineNumber() +
			" but expected function " + exp);
	}
}
