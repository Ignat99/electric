/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Ring.java
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

public class Ring extends PrimitiveNode
{
	private static final double BASESIZE = 30;

	private PrimitivePort ppC;

	public Ring(Photonics tech, Technology.NodeLayer[] layers)
	{
		super("Ring", tech, EPoint.ORIGIN, EPoint.ORIGIN, null, BASESIZE, BASESIZE,
			ERectangle.fromLambda(-BASESIZE/2, -BASESIZE/2, BASESIZE, BASESIZE),
			ERectangle.fromLambda(-BASESIZE/2, -BASESIZE/2, BASESIZE, BASESIZE), layers);
		setSquare();
		ppC = PrimitivePort.single(this, new ArcProto[] {Photonics.opticalArc}, "ring-ctr", 90, 0, 0, PortCharacteristic.UNKNOWN,
			EdgeH.c(0), EdgeV.t(BASESIZE/2), EdgeH.c(0), EdgeV.t(BASESIZE/2));
		addPrimitivePorts(ppC);
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

		long radius = (n.size.getFixpX()+Photonics.lambdaToFixp(BASESIZE)) >> 1;

		PLayer[] ol = Photonics.getOpticalLayers(true);
		for(int l=0; l<ol.length; l++)
		{
			Layer layer = ol[l].findLayer();
			if (layer == null) continue;
			long extend = Photonics.lambdaToFixp(ol[l].getWidth())/2;
			b.setCurNode(n);
			for(int i=0; i<=Photonics.CURVESTEPS; i++)
			{
				double angle = Math.PI * 2 / Photonics.CURVESTEPS * i;
				long x = (long)(Math.cos(angle) * (radius+extend));
				long y = (long)(Math.sin(angle) * (radius+extend));
				b.pushPoint(x, y);
			}
			for(int i=Photonics.CURVESTEPS; i>=0; i--)
			{
				double angle = Math.PI * 2 / Photonics.CURVESTEPS * i;
				long x = (long)(Math.cos(angle) * (radius-extend));
				long y = (long)(Math.sin(angle) * (radius-extend));
				b.pushPoint(x, y);
			}
			PrimitivePort pp = (ol[l] == Photonics.photonicsWaveguide) ? ppC : null;
			b.pushPoly(Poly.Type.FILLED, layer, null, pp);
		}
	}
}
