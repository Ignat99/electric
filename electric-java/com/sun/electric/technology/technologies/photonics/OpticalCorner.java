/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: OpticalCorner.java
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

public class OpticalCorner extends PrimitiveNode
{
	/** default size of Optical Corner */	private static final double BASESIZE = 10;
	private int angle;
	private PrimitivePort pp1, pp2;

	public OpticalCorner(String protoName, Photonics tech, Technology.NodeLayer[] layers, int angle)
	{
		super(protoName, tech, EPoint.ORIGIN, EPoint.ORIGIN, null, BASESIZE, BASESIZE,
			ERectangle.fromLambda(-BASESIZE/2, -BASESIZE/2, BASESIZE, BASESIZE),
			ERectangle.fromLambda(-BASESIZE/2, -BASESIZE/2, BASESIZE, BASESIZE), layers);

		this.angle = angle;
		if (angle == 90)
		{
			// 90-degree bend
			pp1 = PrimitivePort.newInstance(this, new ArcProto[] {Photonics.opticalArc}, "c1", 270, 0, 0, PortCharacteristic.UNKNOWN,
				EdgeH.r(BASESIZE/2), EdgeV.b(-BASESIZE/2), EdgeH.r(BASESIZE/2), EdgeV.b(-BASESIZE/2));
			pp2 = PrimitivePort.newInstance(this, new ArcProto[] {Photonics.opticalArc}, "c2", 180, 0, 0, PortCharacteristic.UNKNOWN,
				EdgeH.l(-BASESIZE/2), EdgeV.t(BASESIZE/2), EdgeH.l(-BASESIZE/2), EdgeV.t(BASESIZE/2));
		} else
		{
			// 45-degree bend
			pp1 = PrimitivePort.newInstance(this, new ArcProto[] {Photonics.opticalArc}, "c1", 270, 0, 0, PortCharacteristic.UNKNOWN,
				EdgeH.r(BASESIZE/2), EdgeV.b(-BASESIZE/2), EdgeH.r(BASESIZE/2), EdgeV.b(-BASESIZE/2));
			pp2 = PrimitivePort.newInstance(this, new ArcProto[] {Photonics.opticalArc}, "c2", 135, 0, 0, PortCharacteristic.UNKNOWN,
				EdgeH.r(2.07), EdgeV.t(2.07), EdgeH.r(2.07), EdgeV.t(2.07));
		}
		addPrimitivePorts(pp1, pp2);
		setCurvedPin();
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

		long width = n.size.getFixpX() + Photonics.lambdaToFixp(BASESIZE);
		long height = n.size.getFixpY() + Photonics.lambdaToFixp(BASESIZE);
		PLayer trench = Photonics.photonicsWaveguide;
		Layer otrenchLay = trench.findLayer();
		long one = Photonics.lambdaToFixp(1);

		// non-curved optical trench
		if (width == 0 || height == 0)
		{
			b.setCurNode(n);
			double extraWidth = trench.getWidth() - BASESIZE;
			double halfWidth = (width + extraWidth*one)/2;
			double halfHeight = (height + extraWidth*one)/2;
			b.pushPoint(-halfWidth, -halfHeight);
			b.pushPoint(-halfWidth, halfHeight);
			b.pushPoint(halfWidth, halfHeight);
			b.pushPoint(halfWidth, -halfHeight);
			b.pushPoly(Poly.Type.CROSSED, otrenchLay, null, null);
			return;
		}

		long halfWidth = width >> 1;
		long halfHeight = height >> 1;
		double trenchHalfWidth = trench.getWidth()/2;
		double outerWid = width + trenchHalfWidth*one;
		double outerHei = height + trenchHalfWidth*one;
		double innerWid = width - trenchHalfWidth*one;
		double innerHei = height - trenchHalfWidth*one;

		// curved optical trench: the main light path
		int numSteps = Photonics.CURVESTEPS * angle / 360;
		int halfWay, numPasses;
		if (b.isElectrical())
		{
			// "electrical" layers need two pieces, each connected to one side of the curve
			numPasses = 2;
			halfWay = numSteps/2;
		} else
		{
			// regular layers draw it in one piece
			numPasses = 1;
			halfWay = numSteps;
		}
		for(int e=0; e<numPasses; e++)
		{
			b.setCurNode(n);
			int startPt, endPt;
			PrimitivePort pp;
			if (e == 0) { startPt = 0;  endPt = halfWay;  pp = pp1; } else
				{ startPt = halfWay;  endPt = numSteps;  pp = pp2; }
			for(int i=startPt; i<=endPt; i++)
			{
				double angle = Math.PI * 2 / Photonics.CURVESTEPS * i;
				long x = (long)(Math.cos(angle) * outerWid) - halfWidth;
				long y = (long)(Math.sin(angle) * outerHei) - halfHeight;
				b.pushPoint(x, y);
			}
			for(int i=endPt; i>=startPt; i--)
			{
				double angle = Math.PI * 2 / Photonics.CURVESTEPS * i;
				long x = (long)(Math.cos(angle) * innerWid) - halfWidth;
				long y = (long)(Math.sin(angle) * innerHei) - halfHeight;
				b.pushPoint(x, y);
			}
			b.pushPoly(Poly.Type.FILLED, otrenchLay, null, pp);
		}

		// Optical: the light path surround
		PLayer[] ol = Photonics.getOpticalLayers(false);
		for(int l=0; l<ol.length; l++)
		{
			Layer layer = ol[l].findLayer();
			if (layer == null) continue;
			double surroundHalfWidth = ol[l].getWidth() / 2;
			outerWid = width  + surroundHalfWidth*one;
			outerHei = height + surroundHalfWidth*one;
			innerWid = width  - surroundHalfWidth*one;
			innerHei = height - surroundHalfWidth*one;
			b.setCurNode(n);
			for(int i=0; i<=numSteps; i++)
			{
				double angle = Math.PI * 2 / Photonics.CURVESTEPS * i;
				long x = (long)(Math.cos(angle) * outerWid) - halfWidth;
				long y = (long)(Math.sin(angle) * outerHei) - halfHeight;
				b.pushPoint(x, y);
			}
			for(int i=numSteps; i>=0; i--)
			{
				double angle = Math.PI * 2 / Photonics.CURVESTEPS * i;
				long x = (long)(Math.cos(angle) * innerWid) - halfWidth;
				long y = (long)(Math.sin(angle) * innerHei) - halfHeight;
				b.pushPoint(x, y);
			}
			b.pushPoly(Poly.Type.FILLED, layer, null, null);
		}
	}
}

