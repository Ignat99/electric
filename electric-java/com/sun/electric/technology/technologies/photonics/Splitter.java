/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Splitter.java
 *
 * Design taken from spie.org/x90296.xml
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
import com.sun.electric.technology.PrimitiveNode;
import com.sun.electric.technology.PrimitivePort;
import com.sun.electric.technology.Technology;

public class Splitter extends PrimitiveNode
{
	/** default width of Splitter */	private static final double BASEWIDTH = 40;
	/** default height of Splitter */	private static final double BASEHEIGHT = 20;
	/** default angle shift */			private static final double DEFAULTANGLESHIFT = 1;
	/** default horiz shift */			private static final double DEFAULTHORIZSHIFT = 5;

	private SplitterPort pp1, pp2, pp3;

	public Splitter(Photonics tech, Technology.NodeLayer[] layers)
	{
		super("Splitter", tech, EPoint.ORIGIN, EPoint.ORIGIN, null, BASEWIDTH, BASEHEIGHT,
			ERectangle.fromLambda(-BASEWIDTH/2, -BASEHEIGHT/2, BASEWIDTH, BASEHEIGHT),
			ERectangle.fromLambda(-BASEWIDTH/2, -BASEHEIGHT/2, BASEWIDTH, BASEHEIGHT), layers);

		// define the four ports
		pp1 = new SplitterPort(this, "splitter-ll", 180, 1);		// port in lower-left
		pp2 = new SplitterPort(this, "splitter-lr",   0, 2);		// port in lower-right
		pp3 = new SplitterPort(this, "splitter-ur",   0, 3);		// port in upper-right
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
		PLayer[] ol = Photonics.getOpticalLayers(true);
		double spacing = Photonics.photonicsWaveguide.getWidth() + 1;
		double aShift = DEFAULTANGLESHIFT;
		double hShift = DEFAULTHORIZSHIFT;
		for(int i=0; i<ol.length; i++)
		{
			// the lower curve that splits off
			long lowX = -width/2;
			long highX = width/2;
			long lowY = -height/2;
			long highY = 0;
			double lowAngle = -Math.PI/2;
			double highAngle = Math.PI + Math.PI/2;
			drawCurve(b, n, lowX, highX, lowY, highY, lowAngle, highAngle, ol[i]);

			// the upper curve that starts here
			lowX = -width/2 + Photonics.lambdaToFixp(hShift);
			lowY = -height/2 + Photonics.lambdaToFixp(spacing);
			highY = height/2;
			lowAngle = -Math.PI/2 + aShift;
			highAngle = Math.PI/2;
			drawCurve(b, n, lowX, highX, lowY, highY, lowAngle, highAngle, ol[i]);
		}
	}

	private void drawCurve(AbstractShapeBuilder b, ImmutableNodeInst n, long lowX, long highX, long lowY, long highY,
		double lowAngle, double highAngle, PLayer pl)
	{
		long channelWidth = Photonics.lambdaToFixp(pl.getWidth());
		b.setCurNode(n);
		for(int i=0; i<=Photonics.CURVESTEPS; i++)
		{
			double angle = lowAngle + (highAngle-lowAngle) / Photonics.CURVESTEPS * i;
			double sinVal = (Math.sin(angle)+1)/2;
			double x = lowX + (highX-lowX) / Photonics.CURVESTEPS * i;
			double y = lowY + (highY-lowY) * sinVal-channelWidth/2;
			b.pushPoint(x, y);
		}
		for(int i=Photonics.CURVESTEPS; i>=0; i--)
		{
			double angle = lowAngle + (highAngle-lowAngle) / Photonics.CURVESTEPS * i;
			double sinVal = (Math.sin(angle)+1)/2;
			double x = lowX + (highX-lowX) / Photonics.CURVESTEPS * i;
			double y = lowY + (highY-lowY) * sinVal+channelWidth/2;
			b.pushPoint(x, y);
		}
		b.pushPoly(Poly.Type.FILLED, pl.findLayer(), null, null);
	}

	/**
	 * Class that defines a primitive port on the Splitter device.
	 */
	public class SplitterPort extends PrimitivePort
	{
		private final int portNumber;

		SplitterPort(Splitter parent, String portName, int portAngle, int portNumber)
		{
			super(parent, new ArcProto[] {Photonics.opticalArc},
				portName, false, portAngle, 0, 0, PortCharacteristic.UNKNOWN, false, false,
				new EdgeH(0, 0), new EdgeV(0, 0), new EdgeH(0, 0), new EdgeV(0, 0));
			this.portNumber = portNumber;
		}

		@Override
		public void genShape(AbstractShapeBuilder b, ImmutableNodeInst n)
		{
			assert n.protoId == getParent().getId();
			long width = n.size.getFixpX() + Photonics.lambdaToFixp(BASEWIDTH);
			long height = n.size.getFixpY() + Photonics.lambdaToFixp(BASEHEIGHT);
			b.setCurNode(n);
			double x = 0, y = 0;
			switch (portNumber)
			{
				case 1:		// port in lower-left
					x = -width/2;   y = -height/2;   break;
				case 2:		// port in lower-right
					x =  width/2;   y = -height/2;   break;
				case 3:		// port in upper-right
					x =  width/2;   y = height/2;    break;
			}
			b.pushPoint(x, y);
			b.pushPoint(x, y);
			b.pushPoint(x, y);
			b.pushPoint(x, y);
			b.pushPoly(Poly.Type.FILLED, null, null, null);
		}
	}
}