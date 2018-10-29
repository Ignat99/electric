/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: GratingCoupler.java
 *
 * Design taken from www.opticsinfobase.org/oe/fulltext.cfm?uri=oe-21-19-21961
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
import com.sun.electric.database.variable.Variable;
import com.sun.electric.technology.AbstractShapeBuilder;
import com.sun.electric.technology.ArcProto;
import com.sun.electric.technology.EdgeH;
import com.sun.electric.technology.EdgeV;
import com.sun.electric.technology.Layer;
import com.sun.electric.technology.PrimitiveNode;
import com.sun.electric.technology.PrimitivePort;
import com.sun.electric.technology.Technology;
import com.sun.electric.technology.Technology.ExtraField;

public class GratingCoupler extends PrimitiveNode
{
	/** default size of Grating Coupler */				private static final double BASESIZE = 20;
	/** default degrees of a circle that this fills */	private static final double DEFDEGREES = 180;

	/** Variable key for storing the angle of a grating coupler */
	private static final Variable.Key COUPLER_ANGLE = Variable.newKey("SIPHOTONICS_GratingCouplerAngle");

	private PrimitivePort pp;

	public GratingCoupler(Photonics tech, Technology.NodeLayer[] layers)
	{
		super("Grating-Coupler", tech, EPoint.ORIGIN, EPoint.ORIGIN, null, BASESIZE, BASESIZE,
			ERectangle.fromLambda(-BASESIZE/2, -BASESIZE/2, BASESIZE, BASESIZE),
			ERectangle.fromLambda(-BASESIZE/2, -BASESIZE/2, BASESIZE, BASESIZE), layers);

		// define the Grating Coupler angle parameter
		new ExtraField(this, COUPLER_ANGLE, "Angle of Fan");

		// add a port
		pp = PrimitivePort.single(this, new ArcProto[] {Photonics.opticalArc}, "g-c", 90, 0, 0, PortCharacteristic.UNKNOWN,
			EdgeH.c(0), EdgeV.c(0), EdgeH.c(0), EdgeV.c(0));
		addPrimitivePorts(pp);
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

		// get the angle of this Grating Coupler
		double degrees = DEFDEGREES;
        Double userDegrees = n.getVarValue(COUPLER_ANGLE, Double.class);
        if (userDegrees != null) degrees = userDegrees.doubleValue();
        double halfDegrees = degrees / 2;

		long radius = (n.size.getFixpY() + Photonics.lambdaToFixp(BASESIZE)) / 2;
		double botRightAngle = (270 + halfDegrees) * Math.PI / 180;
		double botLeftAngle = (270 - halfDegrees) * Math.PI / 180;

		// the surrounding layers
		PLayer [] ol = Photonics.getOpticalLayers(false);
		for(int l=0; l<ol.length; l++)
		{
			Layer layer = ol[l].findLayer();
			if (layer == null) continue;
			b.setCurNode(n);
			b.pushPoint(0, 0);
			for(int i=0; i<=Photonics.CURVESTEPS; i++)
			{
				double angle = botLeftAngle + (botRightAngle - botLeftAngle) / Photonics.CURVESTEPS * i;
				double x = radius * Math.cos(angle);
				double y = radius * Math.sin(angle);
				b.pushPoint(x, y);
			}
			b.pushPoly(Poly.Type.FILLED, layer, null, null);
		}
		
		// the waveguide rings
		PLayer trench = Photonics.photonicsWaveguide;
		Layer trenchLay = trench.findLayer();
		double trenchWidth = trench.getWidth();
		double ringRadius = 0;
		long one = Photonics.lambdaToFixp(1);
		for(;;)
		{
			ringRadius += trenchWidth * one;
			double botRadius = ringRadius + trenchWidth * one;
			if (botRadius > radius) break;
			b.setCurNode(n);
			for(int i=0; i<=Photonics.CURVESTEPS; i++)
			{
				double angle = botLeftAngle + (botRightAngle - botLeftAngle) / Photonics.CURVESTEPS * i;
				double x = ringRadius * Math.cos(angle);
				double y = ringRadius * Math.sin(angle);
				b.pushPoint(x, y);
			}
			ringRadius = botRadius;
			for(int i=Photonics.CURVESTEPS; i>=0; i--)
			{
				double angle = botLeftAngle + (botRightAngle - botLeftAngle) / Photonics.CURVESTEPS * i;
				double x = ringRadius * Math.cos(angle);
				double y = ringRadius * Math.sin(angle);
				b.pushPoint(x, y);
			}
			b.pushPoly(Poly.Type.FILLED, trenchLay, null, null);
		}
    }

}
