/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Info.java
 * Technology Editor, information superclass
 * Written by Steven M. Rubin.
 *
 * Copyright (c) 2005, Static Free Software. All rights reserved.
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
package com.sun.electric.tool.user.tecEdit;

import com.sun.electric.database.EditingPreferences;
import com.sun.electric.database.geometry.EGraphics;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.Library;
import com.sun.electric.technology.ArcProto;
import com.sun.electric.database.prototype.NodeProto;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.variable.Variable;
import com.sun.electric.technology.Layer;
import com.sun.electric.technology.PrimitiveNode;
import com.sun.electric.technology.technologies.Artwork;
import com.sun.electric.technology.technologies.Generic;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * This class is the superclass for all information extraction classes in the Technology Editor.
 */
public class Info
{
	/*
	 * the meaning of OPTION_KEY on nodes
	 * Note that these values are stored in the technology libraries and therefore cannot be changed.
	 * Gaps in the table are where older values became obsolete.
	 * Do not reuse lower numbers when creating a new attribute: add at the end
	 * (as Ivan Sutherland likes to say, numbers are cheap).
	 */
	/** transparency layer (layer cell) */						static final int LAYERTRANSPARENCY =  1;
	/** style (layer cell) */									static final int LAYERSTYLE        =  2;
	/** CIF name (layer cell) */								static final int LAYERCIF          =  3;
	/** function (layer cell) */								static final int LAYERFUNCTION     =  4;
	/** letters (layer cell) */									static final int LAYERLETTERS      =  5;
	/** pattern (layer cell) */									static final int LAYERPATTERN      =  6;
	/** pattern control (layer cell) */							static final int LAYERPATCONT      =  7;
	/** patch of layer (node/arc cell) */						static final int LAYERPATCH        =  8;
	/** function (arc cell) */									static final int ARCFUNCTION       =  9;
	/** function (node cell) */									static final int NODEFUNCTION      = 10;
	/** fixed-angle (arc cell) */								static final int ARCFIXANG         = 11;
	/** wipes pins (arc cell) */								static final int ARCWIPESPINS      = 12;
	/** end extension (arc cell) */								static final int ARCNOEXTEND       = 13;
	/** scale (info cell) */									static final int TECHSCALE         = 14;
	/** description (info cell) */								static final int TECHDESCRIPT      = 15;
	/** serpentine MOS trans (node cell) */						static final int NODESERPENTINE    = 16;
	/** DRC minimum width (layer cell, OBSOLETE) */				static final int LAYERDRCMINWID    = 17;
	/** port object (node cell) */								static final int PORTOBJ           = 18;
	/** highlight object (node/arc cell) */						public static final int HIGHLIGHTOBJ      = 19;
	/** Calma GDS-II layer (layer cell) */						static final int LAYERGDS          = 20;
	/** square node (node cell) */								static final int NODESQUARE        = 21;
	/** pin node can disappear (node cell) */					static final int NODEWIPES         = 22;
	/** increment for arc angles (arc cell) */					static final int ARCINC            = 23;
	/** separation of multiple contact cuts (node cell) */		static final int NODEMULTICUT      = 24;
	/** lockable primitive (node cell) */						static final int NODELOCKABLE      = 25;
	/** grab point object (node cell) */						static final int CENTEROBJ         = 26;
	/** SPICE resistance (layer cell) */						static final int LAYERSPIRES       = 27;
	/** SPICE capacitance (layer cell) */						static final int LAYERSPICAP       = 28;
	/** SPICE edge capacitance (layer cell) */					static final int LAYERSPIECAP      = 29;
	/** DXF layer (layer cell) */								static final int LAYERDXF          = 30;
	/** 3D height (layer cell) */								static final int LAYER3DHEIGHT     = 31;
	/** 3D thickness (layer cell) */							static final int LAYER3DTHICK      = 32;
	/** color (layer cell) */									static final int LAYERCOLOR        = 33;
	/** clear the pattern (layer cell) */						static final int LAYERPATCLEAR     = 34;
	/** invert the pattern (layer cell) */						static final int LAYERPATINVERT    = 35;
	/** copy the pattern (layer cell) */						static final int LAYERPATCOPY      = 36;
	/** copy the pattern (layer cell) */						static final int LAYERPATPASTE     = 37;
	/** Minimum resistance of SPICE elements (info cell) */		static final int TECHSPICEMINRES   = 38;
	/** Minimum capacitance of SPICE elements (info cell) */	static final int TECHSPICEMINCAP   = 39;
	/** Maximum antenna ratio (arc cell) */						static final int ARCANTENNARATIO   = 40;
	/** Desired coverage percentage (layer cell) */				static final int LAYERCOVERAGE     = 41;
	/** gate shrinkage, in um (info cell) */					static final int TECHGATESHRINK    = 42;
	/** true if gate is included in resistance (info cell) */	static final int TECHGATEINCLUDED  = 43;
	/** true to include the ground network (info cell) */		static final int TECHGROUNDINCLUDED= 44;
	/** the transparent colors (info cell) */					static final int TECHTRANSPCOLORS  = 45;
	/** short name (info cell) */								static final int TECHSHORTNAME     = 46;
	/** default foundry name (info cell) */						static final int TECHFOUNDRY       = 47;
	/** default number of metals (info cell) */					static final int TECHDEFMETALS     = 48;
	/** maximum series resistance (info cell) */				static final int TECHMAXSERIESRES  = 49;
	/** spice level 1 header (info cell) */                     static final int TECHSPICELEVEL1   = 50;
	/** spice level 2 header (info cell) */                     static final int TECHSPICELEVEL2   = 51;
	/** spice level 3 header (info cell) */                     static final int TECHSPICELEVEL3   = 52;
    /** is technology scale relevant (info cell) */             static final int TECHSCALERELEVANT = 53;
    /** array of distances (connected case) (info cell) */      static final int TECHCONDIST       = 54;
    /** array of distances (unconnected case) (info cell) */    static final int TECHUNCONDIST     = 55;
    /** description of menu palette (info cell) */              static final int TECHPALETTE       = 56;
	/** curvable (arc cell) */                                  static final int ARCCURVABLE       = 57;
	/** shrinks arcs (node cell) */                             static final int NODESHRINKSARCS   = 58;
    /** 3D transparency mode (layer cell) */                    static final int LAYER3DMODE       = 59;
    /** 3D transparency factor (layer cell) */                  static final int LAYER3DFACTOR     = 60;
    /** Spice template (node cell) */                           static final int NODESPICETEMPLATE = 61;
	/** Elib width offset (arc cell) */                         static final int ARCWIDTHOFFSET    = 62;
	/** Factory min resolution (info tech) */                   static final int TECHRESOLUTION = 63;


    /** key of Variable holding layer information. */	public static final Variable.Key LAYER_KEY = Variable.newKey("EDTEC_layer");
	/** key of Variable holding option information. */	public static final Variable.Key OPTION_KEY = Variable.newKey("EDTEC_option");
	/** key of Variable holding component menu info. */	public static final Variable.Key COMPMENU_KEY = Variable.newKey("EDTEC_componentmenu");
	/** key of Variable holding arc ordering. */		static final Variable.Key ARCSEQUENCE_KEY = Variable.newKey("EDTEC_arcsequence");
	/** key of Variable holding node ordering. */		static final Variable.Key NODESEQUENCE_KEY = Variable.newKey("EDTEC_nodesequence");
	/** key of Variable holding layer ordering. */		static final Variable.Key LAYERSEQUENCE_KEY = Variable.newKey("EDTEC_layersequence");
	/** key of Variable marking geometry as min-size. */static final Variable.Key MINSIZEBOX_KEY = Variable.newKey("EDTEC_minbox");
	/** key of Variable holding port name. */			static final Variable.Key PORTNAME_KEY = Variable.newKey("EDTEC_portname");
	/** key of Variable holding port angle. */			static final Variable.Key PORTANGLE_KEY = Variable.newKey("EDTEC_portangle");
	/** key of Variable holding port range. */			static final Variable.Key PORTRANGE_KEY = Variable.newKey("EDTEC_portrange");
	/** key of Variable holding arc connection list. */	static final Variable.Key CONNECTION_KEY = Variable.newKey("EDTEC_connects");
	/** key of Variable with color map table. */		static final Variable.Key COLORMAP_KEY = Variable.newKey("EDTEC_colormap");
	/** key of Variable with color map table. */		static final Variable.Key DEPENDENTLIB_KEY = Variable.newKey("EDTEC_dependent_libraries");
	/** key of Variable with transparent color list. */	static final Variable.Key TRANSLAYER_KEY = Variable.newKey("EDTEC_transparent_layers");
	/** key of Variable holding port meaning. */		static final Variable.Key PORTMEANING_KEY = Variable.newKey("EDTEC_portmeaning");

	/**
	 * Class for describing special text in a cell
	 */
	protected static class SpecialTextDescr
	{
		NodeInst ni;
		Object   value;
		int      extra;
		double   x, y;
		int      funct;

		protected SpecialTextDescr(double x, double y, int funct)
		{
			ni = null;
			value = null;
			this.x = x;
			this.y = y;
			this.funct = funct;
		}
	};

	/**
	 * Method to create special text geometry described by "table" in cell "np".
	 */
	protected static void createSpecialText(Cell np, SpecialTextDescr [] table, EditingPreferences ep)
	{
		// don't create any nodes already there
		for(int i=0; i < table.length; i++) table[i].ni = null;
		for(Iterator<NodeInst> it = np.getNodes(); it.hasNext(); )
		{
			NodeInst ni = it.next();
			Variable var = ni.getVar(OPTION_KEY);
			if (var == null) continue;
			foundNodeForFunction(ni, ((Integer)var.getObject()).intValue(), table);
		}

		for(int i=0; i < table.length; i++)
		{
			if (table[i].ni != null) continue;
			table[i].ni = NodeInst.makeInstance(Generic.tech().invisiblePinNode, ep, new Point2D.Double(table[i].x, table[i].y), 0, 0, np);
			if (table[i].ni == null) return;
			switch (table[i].funct)
			{
				case TECHSHORTNAME:
					table[i].ni.newDisplayVar(Artwork.ART_MESSAGE,
						"ShortName: " + (String)table[i].value, ep);
					break;
				case TECHSCALE:
					table[i].ni.newDisplayVar(Artwork.ART_MESSAGE,
						"Scale: " + ((Double)table[i].value).doubleValue(), ep);
					break;
				case TECHFOUNDRY:
					table[i].ni.newDisplayVar(Artwork.ART_MESSAGE,
						"DefaultFoundry: " + (String)table[i].value, ep);
					break;
				case TECHDEFMETALS:
					table[i].ni.newDisplayVar(Artwork.ART_MESSAGE,
						"Default Number Of Metals: " + ((Integer)table[i].value).intValue(), ep);
					break;
				case TECHDESCRIPT:
					table[i].ni.newDisplayVar(Artwork.ART_MESSAGE,
						"Description: " + (String)table[i].value, ep);
					break;
				case TECHSPICEMINRES:
					table[i].ni.newDisplayVar(Artwork.ART_MESSAGE,
						"Minimum Resistance: " + ((Double)table[i].value).doubleValue(), ep);
					break;
				case TECHSPICEMINCAP:
					table[i].ni.newDisplayVar(Artwork.ART_MESSAGE,
						"Minimum Capacitance: " + ((Double)table[i].value).doubleValue(), ep);
					break;
				case TECHMAXSERIESRES:
					table[i].ni.newDisplayVar(Artwork.ART_MESSAGE,
						"Max Series Resistance: " + ((Double)table[i].value).doubleValue(), ep);
					break;
				case TECHGATESHRINK:
					table[i].ni.newDisplayVar(Artwork.ART_MESSAGE,
						"Gate Shrinkage: " + ((Double)table[i].value).doubleValue(), ep);
					break;
				case TECHGATEINCLUDED:
					table[i].ni.newDisplayVar(Artwork.ART_MESSAGE,
						"Gates Included in Resistance: " + (((Boolean)table[i].value).booleanValue() ? "Yes" : "No"), ep);
					break;
				case TECHGROUNDINCLUDED:
					table[i].ni.newDisplayVar(Artwork.ART_MESSAGE,
						"Parasitics Includes Ground: " + (((Boolean)table[i].value).booleanValue() ? "Yes" : "No"), ep);
					break;
				case TECHTRANSPCOLORS:
					table[i].ni.newVar(TRANSLAYER_KEY, GeneralInfo.makeTransparentColorsLine((Color [])table[i].value), ep);
					table[i].ni.newDisplayVar(Artwork.ART_MESSAGE,
						"Transparent Colors", ep);
					break;

				case LAYERFUNCTION:
					table[i].ni.newDisplayVar(Artwork.ART_MESSAGE,
						"Function: " + LayerInfo.makeLayerFunctionName((Layer.Function)table[i].value, table[i].extra), ep);
					break;
				case LAYERCOLOR:
					EGraphics desc = (EGraphics)table[i].value;
					table[i].ni.newDisplayVar(Artwork.ART_MESSAGE,
						"Color: " + desc.getColor().getRed() + "," + desc.getColor().getGreen() + "," +
							desc.getColor().getBlue() + ", " + desc.getOpacity() + "," + (desc.getForeground() ? "on" : "off"), ep);
					break;
				case LAYERTRANSPARENCY:
					desc = (EGraphics)table[i].value;
					table[i].ni.newDisplayVar(Artwork.ART_MESSAGE,
						"Transparency: " + (desc.getTransparentLayer() == 0 ? "none" : "layer-" + desc.getTransparentLayer()), ep);
					break;
				case LAYERSTYLE:
					desc = (EGraphics)table[i].value;
					String str = "Style: ";
					if (desc.isPatternedOnDisplay())
					{
						EGraphics.Outline o = desc.getOutlined();
						str += "Patterned/Outline=" + o.getName();
					} else
					{
						str += "Solid";
					}
					if (!desc.isPatternedOnPrinter()) str += ",PrintSolid";
					table[i].ni.newDisplayVar(Artwork.ART_MESSAGE, str, ep);
					break;
				case LAYERCIF:
					table[i].ni.newDisplayVar(Artwork.ART_MESSAGE,
						"CIF Layer: " + (String)table[i].value, ep);
					break;
				case LAYERGDS:
					table[i].ni.newDisplayVar(Artwork.ART_MESSAGE,
						"GDS-II Layer: " + (String)table[i].value, ep);
					break;
				case LAYERSPIRES:
					table[i].ni.newDisplayVar(Artwork.ART_MESSAGE,
						"SPICE Resistance: " + ((Double)table[i].value).doubleValue(), ep);
					break;
				case LAYERSPICAP:
					table[i].ni.newDisplayVar(Artwork.ART_MESSAGE,
						"SPICE Capacitance: " + ((Double)table[i].value).doubleValue(), ep);
					break;
				case LAYERSPIECAP:
					table[i].ni.newDisplayVar(Artwork.ART_MESSAGE,
						"SPICE Edge Capacitance: " + ((Double)table[i].value).doubleValue(), ep);
					break;
				case LAYER3DHEIGHT:
					table[i].ni.newDisplayVar(Artwork.ART_MESSAGE,
						"3D Height: " + ((Double)table[i].value).doubleValue(), ep);
					break;
				case LAYER3DTHICK:
					table[i].ni.newDisplayVar(Artwork.ART_MESSAGE,
						"3D Thickness: " + ((Double)table[i].value).doubleValue(), ep);
					break;
				case LAYERCOVERAGE:
					table[i].ni.newDisplayVar(Artwork.ART_MESSAGE,
						"Coverage percent: " + ((Double)table[i].value).doubleValue(), ep);
					break;

				case ARCFUNCTION:
					table[i].ni.newDisplayVar(Artwork.ART_MESSAGE,
						"Function: " + ((ArcProto.Function)table[i].value).toString(), ep);
					break;
				case ARCFIXANG:
					table[i].ni.newDisplayVar(Artwork.ART_MESSAGE,
						"Fixed-angle: " + (((Boolean)table[i].value).booleanValue() ? "Yes" : "No"), ep);
					break;
				case ARCWIPESPINS:
					table[i].ni.newDisplayVar(Artwork.ART_MESSAGE,
						"Wipes pins: "  + (((Boolean)table[i].value).booleanValue() ? "Yes" : "No"), ep);
					break;
				case ARCNOEXTEND:
					table[i].ni.newDisplayVar(Artwork.ART_MESSAGE,
						"Extend arcs: " + (((Boolean)table[i].value).booleanValue() ? "No" : "Yes"), ep);
					break;
				case ARCINC:
					table[i].ni.newDisplayVar(Artwork.ART_MESSAGE,
						"Angle increment: " + ((Integer)table[i].value).intValue(), ep);
					break;
				case ARCANTENNARATIO:
					table[i].ni.newDisplayVar(Artwork.ART_MESSAGE,
						"Antenna Ratio: " + ((Double)table[i].value).doubleValue(), ep);
					break;
                case ARCWIDTHOFFSET:
                	table[i].ni.newDisplayVar(Artwork.ART_MESSAGE,
						"ELIB width offset: " + ((Double)table[i].value).doubleValue(), ep);
                    break;
				case NODEFUNCTION:
					table[i].ni.newDisplayVar(Artwork.ART_MESSAGE,
						"Function: " + ((PrimitiveNode.Function)table[i].value).toString(), ep);
					break;
				case NODESERPENTINE:
					table[i].ni.newDisplayVar(Artwork.ART_MESSAGE,
						"Serpentine transistor: " + (((Boolean)table[i].value).booleanValue() ? "Yes" : "No"), ep);
					break;
				case NODESQUARE:
					table[i].ni.newDisplayVar(Artwork.ART_MESSAGE,
						"Square node: " + (((Boolean)table[i].value).booleanValue() ? "Yes" : "No"), ep);
					break;
				case NODEWIPES:
					table[i].ni.newDisplayVar(Artwork.ART_MESSAGE,
						"Invisible with 1 or 2 arcs: " + (((Boolean)table[i].value).booleanValue() ? "Yes" : "No"), ep);
					break;
				case NODELOCKABLE:
					table[i].ni.newDisplayVar(Artwork.ART_MESSAGE,
						"Lockable: " + (((Boolean)table[i].value).booleanValue() ? "Yes" : "No"), ep);
					break;
				case NODESPICETEMPLATE:
					table[i].ni.newDisplayVar(Artwork.ART_MESSAGE,
						"Spice template: " + (table[i].value == null ? "" : table[i].value), ep);
					break;
			}
			table[i].ni.newVar(OPTION_KEY, new Integer(table[i].funct), ep);
		}
	}

	private static void foundNodeForFunction(NodeInst ni, int func, Info.SpecialTextDescr [] table)
	{
		for(int i=0; i<table.length; i++)
		{
			if (table[i].funct == func)
			{
				table[i].ni = ni;
				return;
			}
		}
	}

	protected static void loadTableEntry(SpecialTextDescr [] table, int func, Object value)
	{
		for(int i=0; i<table.length; i++)
		{
			if (func == table[i].funct)
			{
				table[i].value = value;
				return;
			}
		}
	}

	/**
	 * Method to get the list of libraries that are used in the construction
	 * of library "lib".  Returns an array of libraries, terminated with "lib".
	 */
	static Library [] getDependentLibraries(Library lib)
	{
		// get list of dependent libraries
		List<Library> dependentLibs = new ArrayList<Library>();
		Variable var = lib.getVar(Info.DEPENDENTLIB_KEY);
		if (var != null)
		{
			String [] libNames = (String [])var.getObject();
			for(int i=0; i<libNames.length; i++)
			{
				String pt = libNames[i];
				Library dLib = Library.findLibrary(pt);
				if (dLib == null)
				{
					System.out.println("Cannot find dependent technology library " + pt + ", ignoring");
					continue;
				}
				if (dLib == lib)
				{
					System.out.println("Library '" + lib.getName() + "' cannot depend on itself, ignoring dependency");
					continue;
				}
				dependentLibs.add(dLib);
			}
		}
		dependentLibs.add(lib);
		Library [] theLibs = new Library[dependentLibs.size()];
		for(int i=0; i<dependentLibs.size(); i++)
			theLibs[i] = dependentLibs.get(i);
		return theLibs;
	}

	/**
	 * general-purpose method to scan the libraries in "dependentlibs",
	 * looking for cells that begin with the string "match".  It then uses the
	 * variable "seqname" on the last library to determine an ordering of the cells.
	 * Then, it returns the cells in an array.
	 */
	static Cell [] findCellSequence(Library [] dependentlibs, String match, Variable.Key seqKey)
	{
		// look backwards through libraries for the appropriate cells
		List<Cell> npList = new ArrayList<Cell>();
		for(int i=dependentlibs.length-1; i>=0; i--)
		{
			Library olderlib = dependentlibs[i];
			for(Iterator<Cell> it = olderlib.getCells(); it.hasNext(); )
			{
				Cell np = it.next();
				if (!np.getName().startsWith(match)) continue;

				// see if this cell is used in a later library
				boolean foundInLater = false;
				for(int j=i+1; j<dependentlibs.length; j++)
				{
					Library laterLib = dependentlibs[j];
					for(Iterator<Cell> oIt = laterLib.getCells(); oIt.hasNext(); )
					{
						Cell lNp = oIt.next();
						if (!lNp.getName().equals(np.getName())) continue;
						foundInLater = true;

						// got older and later version of same cell: check dates
						if (lNp.getRevisionDate().before(np.getRevisionDate()))
							System.out.println("Warning: " + olderlib + " has newer " + np.getName() +
								" than " + laterLib);
						break;
					}
					if (foundInLater) break;
				}

				// if no later library has this, add to total
				if (!foundInLater) npList.add(np);
			}
		}

		// if there is no sequence, simply return the list
		Variable var = dependentlibs[dependentlibs.length-1].getVar(seqKey);
//		if (var == null) return (Cell [])npList.toArray();

		// build a new list with the sequence
		List<Cell> sequence = new ArrayList<Cell>();
		String [] sequenceNames = var != null ? (String [])var.getObject() : new String[0];
		for(int i=0; i<sequenceNames.length; i++)
		{
			Cell foundCell = null;
			for(int l = 0; l < npList.size(); l++)
			{
				Cell np = npList.get(l);
				if (np.getName().substring(match.length()).equals(sequenceNames[i])) { foundCell = np;   break; }
			}
			if (foundCell != null)
			{
				sequence.add(foundCell);
				npList.remove(foundCell);
			}
		}
		for(Cell c: npList)
			sequence.add(c);
		Cell [] theCells = new Cell[sequence.size()];
		for(int i=0; i<sequence.size(); i++)
			theCells[i] = sequence.get(i);
		return theCells;
	}

	/**
	 * Method to return the name of the technology-edit port on node "ni".  Typically,
	 * this is stored on the PORTNAME_KEY variable, but it may also be the node's name.
	 */
	static String getPortName(NodeInst ni)
	{
		Variable var = ni.getVar(PORTNAME_KEY);
		if (var != null) return (String)var.getObject();
		var = ni.getVar(NodeInst.NODE_NAME);
		if (var != null) return (String)var.getObject();
		return null;
	}

	static String getValueOnNode(NodeInst ni)
	{
		String initial = ni.getVarValue(Artwork.ART_MESSAGE, String.class, "");
        int colonPos = initial.indexOf(':');
		if (colonPos > 0) initial = initial.substring(colonPos+2);
		return initial;
	}

	static String getSampleName(NodeProto layerCell)
	{
		if (layerCell == Generic.tech().portNode) return "PORT";
		if (layerCell == Generic.tech().cellCenterNode) return "GRAB";
		if (layerCell == null) return "HIGHLIGHT";
		return layerCell.getName().substring(6);
	}
}
