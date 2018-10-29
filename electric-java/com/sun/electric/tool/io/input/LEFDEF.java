/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: LEFDEF.java
 * Input/output tool: LEF and DEF helpers
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
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.prototype.NodeProto;
import com.sun.electric.database.prototype.PortProto;
import com.sun.electric.database.text.TextUtils;
import com.sun.electric.database.topology.Geometric;
import com.sun.electric.database.variable.Variable;
import com.sun.electric.technology.ArcProto;
import com.sun.electric.technology.Layer;
import com.sun.electric.technology.PrimitiveNode;
import com.sun.electric.technology.Technology;
import com.sun.electric.technology.Technology.ArcLayer;
import com.sun.electric.technology.Technology.NodeLayer;
import com.sun.electric.technology.technologies.Generic;
import com.sun.electric.tool.io.IOTool;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * This class defines supporting structures and methods for reading of LEF and DEF files.
 */
public class LEFDEF extends Input<Object>
{
	protected static final boolean PLACELEFGEOMETRY = true;
	protected static final boolean PLACELEFEXPORTS = true;

	protected static Map<String,ViaDef> viaDefsFromLEF;
	protected static Map<ArcProto,Double> widthsFromLEF;
	protected static Map<String,Double> layerWidthsFromLEF;
	protected static Map<String,GetLayerInformation> knownLayers;

	// these variables are obsolete
	protected static Variable.Key prXkey = Variable.newKey("ATTR_LEFwidth");
	protected static Variable.Key prYkey = Variable.newKey("ATTR_LEFheight");

	protected Technology curTech;
	private boolean viaDigitsCombine;

	public static Collection<GetLayerInformation> getKnownLayers () {return knownLayers.values();}
	public static Map<String,Double> getLayerWidths() {return layerWidthsFromLEF;}
	
	/**
	 * Class to define Via information for LEF and DEF reading.
	 */
	protected static class ViaDef
	{
		protected String    viaName;
		protected NodeProto via;
		protected GetLayerInformation gLay1, gLay2;
		protected double    sX, sY;

		public ViaDef(String name, NodeProto np)
		{
			viaName = name;
			sX = sY = 0;
			via = np;
			gLay1 = gLay2 = null;
		}
	};

    LEFDEF(EditingPreferences ep) {
        super(ep);
    }
    
	protected void initializeLEFDEF(Technology tech)
	{
		curTech = tech;
		viaDigitsCombine = false;
		knownLayers = new HashMap<String,GetLayerInformation>();
    	viaDefsFromLEF = new HashMap<String,ViaDef>();
	}

	/**
	 * Method to find a layer from its name.
	 * Uses a map of known layers, and analyzes the name if none is found.
	 * @param name the layer name.
	 * @param mask the mask number (may be null).
	 * @return the layer information object.
	 */
	protected GetLayerInformation getLayerInformation(String name, Integer mask)
	{
		return getLayerInformation(name, mask, null);
	}
	
	protected GetLayerInformation getLayerInformation(String name, Integer mask, Technology tech)
	{
		String keyName = name;
		if (mask != null) keyName += Layer.DEFAULT_MASK_NAME + mask;
		GetLayerInformation li = knownLayers.get(keyName);
		if (li != null) return li;

		li = (tech == null) ? new GetLayerInformation(name, mask) : new GetLayerInformation(name, mask, tech);
		knownLayers.put(keyName, li);
		return li;
	}


	/**
	 * Unified function to look for layer associated to LEF/DEF readers
	 * @param layer
	 * @param mask
	 * @param unknownLayerHandling instruction on what do if layer not found
	 * @return GetLayerInformation for the layer.
	 */
	protected GetLayerInformation getLayerBasedOnNameAndMask(String layer, Integer mask, int unknownLayerHandling)
	{
		GetLayerInformation gLay = getLayerInformation(layer, mask);
		if (gLay.pure == null && mask != null)
		{
			gLay = getLayerInformation(layer, null);
			if (gLay != null)
				reportWarning("Layer " + layer + ", mask " + mask + " not found, using non-mask version", null);
		}
		if (gLay.pure == null)
		{
			if (unknownLayerHandling == IOTool.DEFLEFUNKNOWNLAYERIGNORE)
			{
				reportError("Layer " + layer + " not found", null);
				return null;
			}
			else if (unknownLayerHandling == IOTool.DEFLEFUNKNOWNLAYERUSEDRC)
			{
				reportWarning("Layer " + layer + " was replaced by DRC layer", null);
				gLay = getLayerInformation(Generic.tech().drcLay.getName(), null, Generic.tech());
				assert(gLay != null);
			}
			else
				assert(false); // should not reach this
		}
		return gLay;
	}
	
	/**
	 * Class to define layer information for LEF and DEF reading.
	 */
	public class GetLayerInformation
	{
		String name;
		NodeProto pin;
		NodeProto pure;
		ArcProto arc;
		ArcProto.Function arcFun;
		Layer.Function layerFun;
		ArcProto viaArc1, viaArc2;

		public boolean equals(GetLayerInformation other)
		{
			if (other == null) 
				return false; // error here?
			if (layerFun == other.layerFun && arcFun == other.arcFun) return true;
			return false;
		}
		
		public String getName() { return name; }

		public double getWidth(EditingPreferences ep)
		{
			if (arc != null) return arc.getDefaultLambdaBaseWidth(ep);
			if (pure != null) return pure.getDefWidth(ep);
			return 0;
		}

		private NodeProto getPureLayerNode(Integer mask)
		{
			if (curTech == null) return null;
			
			// find the pure layer node with this function
			for(Iterator<Layer> it = curTech.getLayers(); it.hasNext(); )
			{
				Layer lay = it.next();
				if (lay.getFunction() == layerFun)
				{
					if (mask == null) return lay.getPureLayerNode();

					int num2 = lay.getFunction().getMaskColor();
					if (num2 == mask.intValue()) return lay.getPureLayerNode();
				}
			}
			return null;
		}

		GetLayerInformation(String name, Integer mask)
		{
			initialize(name, mask, null);
		}

		GetLayerInformation(String name, Integer mask, String type)
		{
			initialize(name, mask, type);
		}
		
		GetLayerInformation(String name, Integer mask, Technology tech)
		{
			initialize(name, mask, null, tech);
		}

		private void initialize(String name, Integer mask, String type)
		{
			Technology tech = null;
			
			int colonPos = name.indexOf(':');
			if (colonPos < 0) tech = curTech; 
			else
			{
				// given full TECH:LAYER name
				tech = Technology.findTechnology(name.substring(0, colonPos));
				name = name.substring(colonPos+1);
			}
			initialize(name, mask, type, tech);
		}

		private void initialize(String name, Integer mask, String type, Technology tech)
		{
			// initialize
			this.name = name;
			pin = null;
			pure = null;
			arc = null;
			arcFun = ArcProto.Function.UNKNOWN;
			layerFun = Layer.Function.UNKNOWN;
			viaArc1 = viaArc2 = null;
			if (tech != null && mask == null)
			{
//				System.out.println("Importing LEF layer '" + name + "' with technology '" + tech.getTechName() + "'");
				Layer lay = tech.findLayer(name);
				if (lay != null)
				{
					layerFun = lay.getFunction();
					pure = lay.getPureLayerNode();
					for(Iterator<ArcProto> it = tech.getArcs(); it.hasNext(); )
					{
						ArcProto ap = it.next();
						ArcLayer[] parts = ap.getArcLayers();
						for(int i=0; i<parts.length; i++)
						{
							if (parts[i].getLayer() == lay)
							{
								arc = ap;
								arcFun = arc.getFunction();
								pin = arc.findPinProto();
								break;
							}
						}
						if (arc != null) break;
					}
					// if the layer is a via, needs special treatment.
					if (pin == null && name.toUpperCase().startsWith("VIA"))
					{
						if (!setupViaLayer(name.substring(3)))
							System.out.println("ERROR: problems matching VIA '" + name + "' found in technology '" + tech.getTechName() + "'");
					}
					// if the layer is DRC, needs special treatment
					if (lay == Generic.tech().drcLay)
					{
						assert(pin == null);
						arc = Generic.tech().universal_arc;
						arcFun = arc.getFunction();
						pin = arc.findPinProto();
					}
					return;
				}
			}

			// analyze based on generic names
			name = name.toUpperCase();

			// first handle known layer names
			// exact names: NP, PP, NW, PO, PDK
			if (name.startsWith("POLY"))
			{
				setupPolyLayer(name.substring(4), name);
				return;
			}
			if (name.equals("PO"))
			{
				setupPolyLayer(name, name);
				return;
			}
			if (name.startsWith("PDIF"))
			{
				arcFun = ArcProto.Function.DIFFP;
				layerFun = Layer.Function.DIFFP;
				pure = getPureLayerNode(null);
				arc = curTech.getArc(arcFun, 0);
				if (arc != null) pin = arc.findPinProto();
				return;
			}
			if (name.startsWith("NDIF"))
			{
				arcFun = ArcProto.Function.DIFFN;
				layerFun = Layer.Function.DIFFN;
				pure = getPureLayerNode(null);
				arc = curTech.getArc(arcFun, 0);
				if (arc != null) pin = arc.findPinProto();
				return;
			}
			if (name.startsWith("PWEL"))
			{
				layerFun = Layer.Function.WELLP;
				pin = pure = getPureLayerNode(null);
				return;
			}
			if (name.startsWith("NWEL") || name.equals("NW"))
			{
				layerFun = Layer.Function.WELLN;
				pin = pure = getPureLayerNode(null);
				return;
			}
			if (name.equals("DIFF") || name.equals("OD"))
			{
				arcFun = ArcProto.Function.DIFF;
				layerFun = Layer.Function.DIFF;
				pure = getPureLayerNode(null);
				arc = curTech.getArc(arcFun, 0);
				if (arc != null) pin = arc.findPinProto();
				return;
			}
			if (name.equals("NP"))
			{
				layerFun = Layer.Function.IMPLANTN;
				pin = pure = getPureLayerNode(null);
				return;
			}
			if (name.equals("PP"))
			{
				layerFun = Layer.Function.IMPLANTP;
				pin = pure = getPureLayerNode(null);
				return;
			}
			if (name.equals("PDK") || name.equals("AP"))
			{
				layerFun = Layer.Function.ART;
				pin = pure = getPureLayerNode(null);
				return;
			}
			
			if (name.equals("CONT") || name.equals("CON") || name.equals("CO"))
			{
				layerFun = Layer.Function.CONTACT1;
				pin = pure = getPureLayerNode(null);
				return;
			}

			// handle via layers
			int j = 0;
			if (name.startsWith("VIA")) j = 3; else
				if (name.startsWith("V")) j = 1;
			if (j != 0)
			{
				if (setupViaLayer(name.substring(j))) return;
			}

			// handle metal layers
			j = 0;
			if (name.startsWith("METAL")) j = 5; else
				if (name.startsWith("MET")) j = 3; else
					if (name.startsWith("M")) j = 1;
			if (j != 0)
			{
				setupMetalLayer(name.substring(j), mask);
				return;
			}

			// if type is given, use it
			if (type != null)
			{
				if (type.equalsIgnoreCase("masterslice"))
				{
					// PO, NW, OD, PDK have been seen as masterslice.
					name = name.toUpperCase();
					j = 0;
					if (name.startsWith("POLY")) j = 4; else
						if (name.startsWith("P")) j = 1;
					setupPolyLayer(name.substring(j), name);
					return;
				}
				if (type.equalsIgnoreCase("cut"))
				{
					j = 0;
					name = name.toUpperCase();
					if (name.startsWith("VIA")) j = 3; else
						if (name.startsWith("V")) j = 1;
					if (setupViaLayer(name.substring(j))) return;
				}
				if (type.equalsIgnoreCase("routing"))
				{
					j = 0;
					if (name.startsWith("METAL")) j = 5; else
						if (name.startsWith("MET")) j = 3; else
							if (name.startsWith("M")) j = 1;
					name = name.substring(j);
					while (name.length() > 0 && !Character.isDigit(name.charAt(0)))
						name = name.substring(1);
					setupMetalLayer(name, mask);
					return;
				}
			}

			if (curTech != null) // during import LEF for technology -> curTech is null
			{
				// look for a layer that starts with the name
				for(Iterator<Layer> it = curTech.getLayers(); it.hasNext(); )
				{
					Layer lay = it.next();
					if (lay.getName().startsWith(name))
					{
						layerFun = lay.getFunction();
						assignPureNodeBasedOnLay(lay);
						return;
					}
				}
			}
	
			// special cases
			if (name.indexOf("OVERLAP") >= 0)
			{
				if (curTech != null) // curTech exists as reference
				{
					for(Iterator<Layer> it = curTech.getLayers(); it.hasNext(); )
					{
						Layer lay = it.next();
						if (lay.getName().toLowerCase().indexOf("prbound") >= 0)
						{
							layerFun = lay.getFunction();
							assignPureNodeBasedOnLay(lay);
							return;
						}
					}
				}
				// force the PRBOUND definition
				layerFun = Layer.Function.ART;
				pin = pure = getPureLayerNode(null);
				return;
			}
			System.out.println("Error: cannot find a layer in technology " + ((tech != null ) ? tech.getTechName() : "") + " named '" + name + "'");
		}

		private void assignPureNodeBasedOnLay(Layer lay)
		{
			for(Iterator<PrimitiveNode> pIt = curTech.getNodes(); pIt.hasNext(); )
			{
				PrimitiveNode pn = pIt.next();
				if (pn.getFunction() != PrimitiveNode.Function.NODE) continue;
				NodeLayer[] layersOnNode = pn.getNodeLayers();
				if (layersOnNode[0].getLayer() == lay)
				{
					pin = pure = pn;
					return;
				}
			}
			System.out.println("Error: cannot find a pure layer node in technology " + curTech.getTechName() + " for layer '" + lay.getName() + "'");
		}

		private void setupMetalLayer(String name, Integer mask)
		{
			int layNum = TextUtils.atoi(name);
			arcFun = ArcProto.Function.getMetal(layNum);
			int maskNum = (mask == null ? 0 : mask.intValue());
			layerFun = Layer.Function.getMetal(layNum, maskNum);
			if (arcFun == null || layerFun == null || curTech == null) return;

			// find the arc with this function and mask
			arc = curTech.getArc(arcFun, maskNum);
			if (arc != null) pin = arc.findPinProto();

			// find the pure layer node with this function
			pure = getPureLayerNode(mask);
		}

		private void setupPolyLayer(String name, String fullName)
		{
			try
			{
				int layNum = TextUtils.atoi(name);
				if (layNum == 0) layNum = 1; // if curTech == null -> already POLY1 as default
				arcFun = ArcProto.Function.getPoly(layNum);
				layerFun = Layer.Function.getPoly(layNum);
				
				if (arcFun == null || layerFun == null || curTech == null) return;

				// find the arc with this function
				arc = curTech.getArc(arcFun, 0);
				if (arc != null) pin = arc.findPinProto();

				// find the pure layer node with this function
				pure = getPureLayerNode(null);
			} catch (Exception e)
			{
				System.out.println("ERROR Parsing Polysilicon layer '" + fullName + "'");
			}
		}

		/**
		 * Method to setup via information
		 * @param name
		 * @return true if the information was properly setup
		 */
		private boolean setupViaLayer(String name)
		{
			if (curTech == null)
				return true; // done with this setup
			
			// find the two layer functions
			ArcProto.Function aFunc1 = ArcProto.Function.UNKNOWN;
			ArcProto.Function aFunc2 = ArcProto.Function.UNKNOWN;
			if (name.length() <= 0)
			{
				aFunc1 = ArcProto.Function.METAL1;
				aFunc2 = ArcProto.Function.METAL2;
			} else if (name.length() <= 1)
			{
				int level = name.charAt(0) - '0';
				if (level < 0 || level > 9) return false;
				if (level == 0) aFunc1 = ArcProto.Function.getPoly(1); else
					aFunc1 = ArcProto.Function.getMetal(level);
				aFunc2 = ArcProto.Function.getMetal(level + 1);
			} else
			{
				int level1 = name.charAt(0) - '0';
				int level2 = name.charAt(1) - '0';
				if (level1 < 0 || level1 > 9) return false;
				if (level2 < 0 || level2 > 9) return false;
				if (!viaDigitsCombine && level2 <= level1) viaDigitsCombine = true;
				if (viaDigitsCombine)
				{
					level1 = level1*10 + level2;
					level2 = level1 + 1;
				}
				if (level1 == 0) aFunc1 = ArcProto.Function.getPoly(1); else
					aFunc1 = ArcProto.Function.getMetal(level1);
				aFunc2 = ArcProto.Function.getMetal(level2);
			}

			// find the arcprotos that embody these layers
			for(Iterator<ArcProto> it = curTech.getArcs(); it.hasNext(); )
			{
				ArcProto apTry = it.next();
				if (apTry.getFunction() == aFunc1) viaArc1 = apTry;
				if (apTry.getFunction() == aFunc2) viaArc2 = apTry;
			}
			if (viaArc1 == null || viaArc2 == null) return false;

			// find the via that connects these two arcs
			for(Iterator<PrimitiveNode> it = curTech.getNodes(); it.hasNext(); )
			{
				PrimitiveNode np = it.next();
				// must have just one port
				if (np.getNumPorts() != 1) continue;

				// port must connect to both arcs
				PortProto pp = np.getPort(0);
				boolean ap1Found = pp.connectsTo(viaArc1);
				boolean ap2Found = pp.connectsTo(viaArc2);
				if (ap1Found && ap2Found) { pin = np;   break; }
			}

			// find the pure layer node that is the via contact
			if (pin != null)
			{
				// find the layer on this node that is of type "contact"
				PrimitiveNode pNp = (PrimitiveNode)pin;
				Technology.NodeLayer [] nl = pNp.getNodeLayers();
				Layer viaLayer = null;
				for(int i=0; i<nl.length; i++)
				{
					Technology.NodeLayer nLay = nl[i];
					Layer lay = nLay.getLayer();
					Layer.Function fun = lay.getFunction();
					if (fun.isContact()) { viaLayer = lay;   layerFun = fun;   break; }
				}
				if (viaLayer == null) return false;
				pure = viaLayer.getPureLayerNode();
			}
			return true;
		}
	}
	
	// Extra functions
	protected void reportError(String command, Cell cell)
	{
		String msg = "Error on line " + lineReader.getLineNumber() + ": " + command;
		System.out.println(msg);
        errorLogger.logError(msg, cell, 0);
	}

	protected void reportWarning(String command, Cell cell)
	{
		String msg = "Warning on line " + lineReader.getLineNumber() + ": " + command;
		System.out.println(msg);
        errorLogger.logWarning(msg, cell, 0);
	}

	protected void reportWarning(String command, Geometric geom, Cell cell)
	{
		String msg = "Warning on line " + lineReader.getLineNumber() + ": " + command;
		System.out.println(msg);
        errorLogger.logWarning(msg, geom, cell, null, 0);
	}
}
