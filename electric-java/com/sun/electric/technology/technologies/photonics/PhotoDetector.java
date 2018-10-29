/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: PhotoDetector.java
 *
 * Design taken from www.opticsinfobase.org/oe/fulltext.cfm?uri=oe-18-5-4986
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
 */
package com.sun.electric.technology.technologies.photonics;

import com.sun.electric.database.ImmutableNodeInst;
import com.sun.electric.database.geometry.EPoint;
import com.sun.electric.database.geometry.ERectangle;
import com.sun.electric.database.geometry.Poly;
import com.sun.electric.database.prototype.PortCharacteristic;
import com.sun.electric.technology.AbstractShapeBuilder;
import com.sun.electric.technology.ArcProto;
import com.sun.electric.technology.EdgeH;
import com.sun.electric.technology.EdgeV;
import com.sun.electric.technology.Layer;
import com.sun.electric.technology.PrimitiveNode;
import com.sun.electric.technology.PrimitivePort;
import com.sun.electric.technology.Technology;

public class PhotoDetector extends PrimitiveNode
{
	/** default width of Photo Detector */					private static final double BASEWIDTH = 10;
	/** default height of Photo Detector */					private static final double BASEHEIGHT = 50;
	/** default height of Photo Detector */					private static final double METALWIDTH = 5;
	/** default height of Photo Detector */					private static final double METALGAP = 5;
	/** length of taper at bottom */						private static final double TAPERHEIGHT = 20;
	/** full width of Photo Detector */						private static final double FULLWIDTH = BASEWIDTH+(METALWIDTH+METALGAP)*2;
	/** full width of Photo Detector */						private static final double FULLHEIGHT = BASEHEIGHT;

	/** the primitive ports on this device */				private PrimitivePort pp1, pp2, pp3;

	public PhotoDetector(Photonics tech, Technology.NodeLayer[] layers)
	{
		super("Photo-Detector", tech, EPoint.ORIGIN, EPoint.ORIGIN, null, FULLWIDTH, FULLHEIGHT,
			ERectangle.fromLambda(-FULLWIDTH/2, -FULLHEIGHT/2, FULLWIDTH, FULLHEIGHT),
			ERectangle.fromLambda(-BASEWIDTH/2, -BASEHEIGHT/2, BASEWIDTH, BASEHEIGHT), layers);
		setMinSize(FULLWIDTH, FULLHEIGHT, "");

		pp1 = PrimitivePort.newInstance(this, new ArcProto[] {Photonics.opticalArc}, "pd-bot", 270, 0, 0, PortCharacteristic.UNKNOWN,
			EdgeH.c(0), EdgeV.b(-BASEHEIGHT/2), EdgeH.c(0), EdgeV.b(-BASEHEIGHT/2));
		pp2 = PrimitivePort.newInstance(this, new ArcProto[] {Photonics.metal1Arc}, "pd-lft", 180, 90, 1, PortCharacteristic.UNKNOWN,
			EdgeH.fromLeft(-FULLWIDTH/2), EdgeV.c(0), EdgeH.fromLeft(-FULLWIDTH/2), EdgeV.c(0));
		pp3 = PrimitivePort.newInstance(this, new ArcProto[] {Photonics.metal1Arc}, "pd-rgt", 0, 90, 2, PortCharacteristic.UNKNOWN,
			EdgeH.fromRight(-FULLWIDTH/2), EdgeV.c(0), EdgeH.fromRight(-FULLWIDTH/2), EdgeV.c(0));
		addPrimitivePorts(pp1, pp2, pp3);
	}

	/**
	 * Puts into shape builders the polygons that describe node "n", given a set of
	 * NodeLayer objects to use.
	 * This method is overridden by specific Technologies.
	 * @param b shape builder where to put polygons
	 * @param n the ImmutableNodeInst that is being described.
	 */
	@Override
	public void genShape(AbstractShapeBuilder b, ImmutableNodeInst n)
	{
		assert n.protoId == getId();

		long width = n.size.getFixpX() + Photonics.lambdaToFixp(BASEWIDTH);
		long height = n.size.getFixpY() + Photonics.lambdaToFixp(BASEHEIGHT);

		long taperHeight = Photonics.lambdaToFixp(TAPERHEIGHT);
		long waveguideWidth = Photonics.lambdaToFixp(Photonics.photonicsWaveguide.getWidth());

		PLayer[] ol = Photonics.getOpticalLayers(true);
		for(int l=0; l<ol.length; l++)
		{
			Layer layer = ol[l].findLayer();
			if (layer == null) continue;
			long extend = Photonics.lambdaToFixp(ol[l].getWidth())-waveguideWidth;

			// the main layer above the taper
			b.setCurNode(n);
			b.pushPoint(-width/2-extend/2, -height/2+taperHeight);
			b.pushPoint(-width/2-extend/2,  height/2);
			b.pushPoint( width/2+extend/2,  height/2);
			b.pushPoint( width/2+extend/2, -height/2+taperHeight);
			b.pushPoly(Poly.Type.FILLED, layer, null, null);

			// the taper at the bottom
			b.setCurNode(n);
			b.pushPoint(-width/2-extend/2, -height/2+taperHeight);
			b.pushPoint(-waveguideWidth/2-extend/2, -height/2);
			b.pushPoint( waveguideWidth/2+extend/2, -height/2);
			b.pushPoint( width/2+extend/2, -height/2+taperHeight);
			b.pushPoly(Poly.Type.FILLED, layer, null, null);
		}

		// the row of cuts that alternate sides of metal
		long metalGap = Photonics.lambdaToFixp(METALGAP);
		long metalWidth = Photonics.lambdaToFixp(METALWIDTH);
		long cutSpacing = Photonics.lambdaToFixp(5);
		long cutSize = Photonics.lambdaToFixp(1);
		int numCuts = (int)((height-cutSize) / cutSpacing);
		if ((numCuts%2) == 1) numCuts--;
		long metalLowLeft = height;
		long metalHighLeft = -height;
		long metalLowRight = height;
		long metalHighRight = -height;
		for(int i=0; i<numCuts; i++)
		{
			long yPos = -(numCuts-1)*cutSpacing/2 + i * cutSpacing;
			b.setCurNode(n);
			b.pushPoint(-cutSize/2, yPos-cutSize/2);
			b.pushPoint(-cutSize/2, yPos+cutSize/2);
			b.pushPoint(cutSize/2, yPos+cutSize/2);
			b.pushPoint(cutSize/2, yPos-cutSize/2);
			b.pushPoly(Poly.Type.FILLED, Photonics.polyCutLayer, null, null);

			b.setCurNode(n);
			if ((i%2) == 0)
			{
				// goes to the left
				b.pushPoint(cutSize/2, yPos-cutSize/2);
				b.pushPoint(cutSize/2, yPos+cutSize/2);
				b.pushPoint(-width/2-metalGap, yPos+cutSize/2);
				b.pushPoint(-width/2-metalGap, yPos-cutSize/2);
				if (yPos+cutSize/2 > metalHighLeft) metalHighLeft = yPos+cutSize/2;
				if (yPos-cutSize/2 < metalLowLeft) metalLowLeft = yPos-cutSize/2;
			} else
			{
				// goes to the right
				b.pushPoint(-cutSize/2, yPos-cutSize/2);
				b.pushPoint(-cutSize/2, yPos+cutSize/2);
				b.pushPoint(width/2+metalGap, yPos+cutSize/2);
				b.pushPoint(width/2+metalGap, yPos-cutSize/2);
				if (yPos+cutSize/2 > metalHighRight) metalHighRight = yPos+cutSize/2;
				if (yPos-cutSize/2 < metalLowRight) metalLowRight = yPos-cutSize/2;
			}
			b.pushPoly(Poly.Type.FILLED, Photonics.metal1Layer, null, null);
		}

		// the metal piece on the left
		b.setCurNode(n);
		b.pushPoint(-width/2-metalGap, metalLowLeft);
		b.pushPoint(-width/2-metalGap-metalWidth, metalLowLeft);
		b.pushPoint(-width/2-metalGap-metalWidth, metalHighLeft);
		b.pushPoint(-width/2-metalGap, metalHighLeft);
		b.pushPoly(Poly.Type.FILLED, Photonics.metal1Layer, null, null);

		// the metal piece on the right
		b.setCurNode(n);
		b.pushPoint(width/2+metalGap, metalLowRight);
		b.pushPoint(width/2+metalGap+metalWidth, metalLowRight);
		b.pushPoint(width/2+metalGap+metalWidth, metalHighRight);
		b.pushPoint(width/2+metalGap, metalHighRight);
		b.pushPoly(Poly.Type.FILLED, Photonics.metal1Layer, null, null);
	}
}
