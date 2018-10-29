/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: MoCMOS.java
 *
 * Copyright (c) 2003, Static Free Software. All rights reserved.
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
package com.sun.electric.technology.technologies;

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
import com.sun.electric.technology.TechFactory;
import com.sun.electric.technology.Technology;
import com.sun.electric.technology.Xml;
import com.sun.electric.util.math.DBMath;
import com.sun.electric.util.math.FixpCoord;
import java.util.Map;

/**
 *
 */
public class Dummy extends Technology
{
    private static final double BASESIZE = 10;
    private static final long BASELINE_FIXP = (long)(BASESIZE * DBMath.GRID * (1L << FixpCoord.FRACTION_BITS));
    private final Layer lMetal1;
    private final ArcProto m1;
    private final ArcProto[] portArcs;

    public Dummy(Generic generic, TechFactory techFactory, Map<TechFactory.Param, Object> techParams, Xml.Technology t)
    {
        super(generic, techFactory, techParams, t);
        lMetal1 = findLayer("Metal-1");
        m1 = findArcProto("Metal-1");
        portArcs = new ArcProto[]
        {
            m1
        };
        makeBigBlueBox();
    }

    private void makeBigBlueBox()
    {
        ERectangle baseRectangle = ERectangle.fromLambda(-0.5 * BASESIZE, -0.5 * BASESIZE, BASESIZE, BASESIZE);
        ERectangle fullRectangle = baseRectangle;
        Technology.NodeLayer[] nodeLayers =
        {
            // Dummy NodeLayer to check visibility.
            new Technology.NodeLayer(lMetal1, 0, Poly.Type.FILLED, Technology.NodeLayer.BOX, new Technology.TechPoint[0])
        };
        BigBlueNode pnp = new BigBlueNode(fullRectangle, baseRectangle, nodeLayers);
    }

    private class BigBlueNode extends PrimitiveNode
    {
        BigBlueNode(ERectangle fullRectangle, ERectangle baseRectangle, Technology.NodeLayer[] layers)
        {
            super("BigBlueNode", Dummy.this, EPoint.ORIGIN, EPoint.ORIGIN, null, BASESIZE, BASESIZE,
                fullRectangle, baseRectangle, layers);
            // add in our own ports (this part fails)
            addPrimitivePorts(
                new BluePort(this, "p-1", 0, +1, +1),
                new BluePort(this, "p-2", 180, -1, +1),
                new BluePort(this, "p-3", 0, +1, -1),
                new BluePort(this, "p-4", 180, -1, -1));
            check();
        }

        @Override
        public void genShape(AbstractShapeBuilder b, ImmutableNodeInst n)
        {
            assert n.protoId == getId();

            long width = n.size.getFixpX() + BASELINE_FIXP;
            long height = n.size.getFixpY() + BASELINE_FIXP;

            b.setCurNode(n);
            b.pushPoint(-width / 2, -height / 2);
            b.pushPoint(-width / 2, height / 2);
            b.pushPoint(width / 2, height / 2);
            b.pushPoint(width / 2, -height / 2);
            b.pushPoly(Poly.Type.FILLED, lMetal1, null, null);
        }
    }

    private class BluePort extends PrimitivePort
    {
        private double xm;
        private double ym;

        BluePort(BigBlueNode parent, String portName, int portAngle, double xm, double ym)
        {
            super(parent, portArcs,
                portName, false, portAngle, 45, 0, PortCharacteristic.UNKNOWN, false, false,
                new EdgeH(xm, BASESIZE / 2 * Math.min(xm, 0.5*xm)),
                new EdgeV(ym, BASESIZE / 2 * Math.min(ym, 0.5*ym)),
                new EdgeH(xm, BASESIZE / 2 * Math.max(xm, 0.5*xm)),
                new EdgeV(ym, BASESIZE / 2 * Math.max(ym, 0.5*ym)));
            this.xm = xm;
            this.ym = ym;
        }

        @Override
        public void genShape(AbstractShapeBuilder b, ImmutableNodeInst n)
        {
            assert n.protoId == getParent().getId();
            
            long width = n.size.getFixpX() + BASELINE_FIXP;
            long height = n.size.getFixpY() + BASELINE_FIXP;
            
            long x = (long)Math.rint(xm * 0.5 * width);
            long y = (long)Math.rint(ym * 0.5 * height);

            b.setCurNode(n);
            b.pushPoint(x, y);
            b.pushPoint(x >> 1, y);
            b.pushPoint(x, y >> 1);
            b.pushPoly(Poly.Type.FILLED, null, null, null);
        }
    }
}
