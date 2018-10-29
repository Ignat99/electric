/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Photonics.java
 *
 * Copyright (c) 2015, Static Free Software. All rights reserved.
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
 *
 * The "Photonics" technology contains generic code for abstract photonics technologies as described in various papers:
 *     www.opticsinfobase.org/oe/fulltext.cfm?uri=oe-21-19-21961
 *     www.opticsinfobase.org/oe/fulltext.cfm?uri=oe-18-5-4986
 *     spie.org/x90296.xml
 */
package com.sun.electric.technology.technologies.photonics;

import com.sun.electric.database.geometry.Poly;
import com.sun.electric.database.variable.Variable;
import com.sun.electric.technology.ArcProto;
import com.sun.electric.technology.Layer;
import com.sun.electric.technology.TechFactory;
import com.sun.electric.technology.Technology;
import com.sun.electric.technology.Xml;
import com.sun.electric.technology.technologies.Generic;
import com.sun.electric.util.math.DBMath;
import com.sun.electric.util.math.FixpCoord;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class Photonics extends Technology
{
	// the definition of a waveguide, with layers and surrounds
	public static PLayer photonicsWaveguide = new PLayer("photonics-waveguide", 1);
	public static PLayer photonicsSide      = new PLayer("photonics-side",      5);
	public static PLayer photonicsTop       = new PLayer("photonics-top",       5);
	public static PLayer photonicsBottom    = new PLayer("photonics-bottom",    5);

	/** number of steps in a curve */	public static final int CURVESTEPS = 128;
	/** the metal-1 layer */			public static Layer metal1Layer = null;
	/** the poly-cut layer */			public static Layer polyCutLayer = null;
	/** the metal-1 arc */				public static ArcProto metal1Arc = null;
	/** the optical layer */			public static ArcProto opticalArc = null;

	private static Photonics thisTech = null;
	public static final Variable.Key extraInfoKey = Variable.newKey("ATTR_ExtraInfo");

	public Photonics(Generic generic, TechFactory techFactory, Map<TechFactory.Param,Object> techParams, Xml.Technology t)
	{
		super(generic, techFactory, techParams, t);
		thisTech = this;
		setStaticTechnology();

		// build the optical arc
		opticalArc = new OpticalArc(this);

		// get layers
		metal1Layer = findLayer("m1");
		polyCutLayer = findLayer("poly-cut");
		if (metal1Layer == null || polyCutLayer == null)
			System.out.println("WARNING: Missing Layers in Photonics technology");

		// get arcs
		metal1Arc = findArcProto("metal-1");
		if (metal1Arc == null)
			System.out.println("WARNING: Missing Metal-1 arc in Photonics technology");

		// make dummy NodeLayer to check visibility
		Technology.NodeLayer[] nodeLayers =
		{
			new Technology.NodeLayer(metal1Layer, 0, Poly.Type.FILLED, Technology.NodeLayer.BOX, new Technology.TechPoint[0])
		};

		// load the simple optical primitives (pins and corners)
		new OpticalPin("Optical-Pin", thisTech, nodeLayers);
		new OpticalCorner("Optical-Corner-90", thisTech, nodeLayers, 90);
		new OpticalCorner("Optical-Corner-45", thisTech, nodeLayers, 45);

		// load the more advanced optical primitives
		new GratingCoupler(thisTech, nodeLayers);
		new PhotoDetector(thisTech, nodeLayers);
		new Ring(thisTech, nodeLayers);
		new Splitter(thisTech, nodeLayers);

		// build component palette
		loadFactoryMenuPalette(Photonics.class.getResource("PhotonicsMenu.xml"));
	}

    /**
     * Method to return the Photonics Technology object.
     */
    public static Photonics tech() { return thisTech; }

    /**
	 * This method is called from TechFactory by reflection. Don't remove.
	 * Returns a list of TechFactory.Params affecting this Technology
	 * @return list of TechFactory.Params affecting this Technology
	 */
	public static List<TechFactory.Param> getTechParams()
	{
		return Arrays.asList();
	}

	/**
	 * This method is called from TechFactory by reflection. Don't remove.
	 * Returns patched XML description of this Technology for specified technology parameters
	 * @param params values of technology parameters
	 * @return patched XML description of this Technology
	 */
	public static Xml.Technology getPatchedXml(Map<TechFactory.Param,Object> params)
	{
		Xml.Technology tech = Xml.parseTechnology(Photonics.class.getResource("Photonics.xml"));
		return tech;
	}

	/**
	 * Method to convert from database units to fixed point units, used when rendering.
	 * @param l Electric units.
	 * @return fixed point units.
	 */
	public static long lambdaToFixp(double l)
	{
		return (long)(l * DBMath.GRID * (1L << FixpCoord.FRACTION_BITS));
	}

	/**
	 * Method to return an array of PLayer objects used in an optical track.
	 * @param includeWaveguide true to include the waveguide layer,
	 * false to list just the surrounding layers.
	 * @return an array of PLayer objects used in an optical track.
	 */
	public static PLayer[] getOpticalLayers(boolean includeWaveguide)
	{
		List<PLayer> wgb = new ArrayList<PLayer>();
		if (includeWaveguide) wgb.add(photonicsWaveguide);
		wgb.add(photonicsBottom);
		wgb.add(photonicsSide);
		wgb.add(photonicsTop);
		PLayer[] ret = new PLayer[wgb.size()];
		for(int i=0; i<wgb.size(); i++) ret[i] = wgb.get(i);
		return ret;
	}
}
