/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: DrcRing.java
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
package com.sun.electric.tool.generator.layout.gates;

import java.awt.geom.Rectangle2D;

import com.sun.electric.database.EditingPreferences;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.prototype.NodeProto;
import com.sun.electric.database.topology.PortInst;
import com.sun.electric.technology.ArcProto;
import com.sun.electric.technology.Technology;
import com.sun.electric.tool.generator.layout.LayoutLib;
import com.sun.electric.tool.generator.layout.StdCellParams;
import com.sun.electric.tool.generator.layout.TechType;
import com.sun.electric.util.math.DBMath;

/** Create a ring in layers p1 and m1 - m5.  This ring is useful for
 * testing DRC correctness of my gate libraries.  I will draw a ring
 * around every gate and then run DRC. */
public class DrcRing {
//  private static void error(boolean pred, String msg) {
//    LayoutLib.error(pred, msg);
//  }

  /** Draw a rectangular ring from arc.  The space inside the ring has
   * width w and height h.  Position the ring so that the bottom left
   * inside corner is at (0, 0).  All edges are on a 0.5 lambda grid
   * as long as w and h are multiples of 0.5 lambda. */
  private static void drawRing(ArcProto arc, double w, double h, Cell f, EditingPreferences ep) {
      if (arc == null) {
          return;
      }
    // metal-5 minimum width is 6 lambda
    double arcW = 6;

    NodeProto pin = arc.findOverridablePinProto(ep);
    double pinLoX = - arcW/2;
    double pinHiX = w + arcW/2;
    double pinLoY = - arcW/2;
    double pinHiY = h + arcW/2;
    /*
    double pinLoX = -w/2 - arcW/2;
    double pinHiX = w/2 + arcW/2;
    double pinLoY = -h/2 - arcW/2;
    double pinHiY = h/2 + arcW/2;
    */
	double defSz = LayoutLib.DEF_SIZE;
    PortInst blPort = LayoutLib.newNodeInst(pin, ep, pinLoX, pinLoY, defSz, defSz,
                                            0, f).getOnlyPortInst();
    PortInst brPort = LayoutLib.newNodeInst(pin, ep, pinHiX, pinLoY, defSz, defSz,
                                            0, f).getOnlyPortInst();
    PortInst tlPort = LayoutLib.newNodeInst(pin, ep, pinLoX, pinHiY, defSz, defSz,
                                            0, f).getOnlyPortInst();
    PortInst trPort = LayoutLib.newNodeInst(pin, ep, pinHiX, pinHiY, defSz, defSz,
                                            0, f).getOnlyPortInst();
    LayoutLib.newArcInst(arc, ep, arcW, blPort, brPort);
    LayoutLib.newArcInst(arc, ep, arcW, tlPort, trPort);
    LayoutLib.newArcInst(arc, ep, arcW, blPort, tlPort);
    LayoutLib.newArcInst(arc, ep, arcW, brPort, trPort);
  }

  private static void drawRing(NodeProto np, double w, double h, double thickness, Cell f, char half, double pwellRatio, EditingPreferences ep) {
      double arcW = thickness;

      double pinLoX = -arcW;
      double pinHiX = w;
      double pinLoY = 0;
      double pinHiY = h;
      double lrHeight = h;
      if (half == 'T') {
          pinLoY = DBMath.round(h*pwellRatio);
          lrHeight = DBMath.round(h*pwellRatio);
      }
      if (half == 'B') {
          pinHiY = DBMath.round(h*(1-pwellRatio));
          lrHeight = DBMath.round(h*(1-pwellRatio));
      }
      Rectangle2D left = new Rectangle2D.Double(pinLoX, pinLoY, arcW, lrHeight);
      Rectangle2D right = new Rectangle2D.Double(pinHiX, pinLoY, arcW, lrHeight);
      Rectangle2D top = new Rectangle2D.Double(pinLoX, pinHiY, w + 2*arcW, arcW);
      Rectangle2D bottom = new Rectangle2D.Double(pinLoX, pinLoY - arcW, w + 2*arcW, arcW);
      LayoutLib.roundBounds(left);
      LayoutLib.roundBounds(right);
      LayoutLib.roundBounds(top);
      LayoutLib.roundBounds(bottom);

      LayoutLib.newNodeInst(np, ep, left.getCenterX(), left.getCenterY(), left.getWidth(), left.getHeight(), 0, f);
      LayoutLib.newNodeInst(np, ep, right.getCenterX(), right.getCenterY(), right.getWidth(), right.getHeight(), 0, f);
      if (half != 'B')
          LayoutLib.newNodeInst(np, ep, top.getCenterX(), top.getCenterY(), top.getWidth(), top.getHeight(), 0, f);
      if (half != 'T')
          LayoutLib.newNodeInst(np, ep, bottom.getCenterX(), bottom.getCenterY(), bottom.getWidth(), bottom.getHeight(), 0, f);
  }

  /** Draw rings with inside width w and inside height h. The ring
   * position places the bottom left inside corner at (0, 0) */
  public static Cell makePart(double w, double h, StdCellParams stdCell) {
	  TechType tech = stdCell.getTechType();
      EditingPreferences ep = stdCell.getEditingPreferences();
    String nm = "drcRing_W"+w+"_H"+h+"{lay}";

    Cell ring = stdCell.findPart(nm);
    if (ring!=null) return ring;
    ring = stdCell.newPart(nm);
    boolean is90nm = ring.getTechnology() == Technology.getCMOS90Technology();
    if (is90nm) {
//    if (Tech.is90nm()) {
        // ignore poly, but put in select rings
        double pwellRatio = stdCell.getPmosWellHeight()/(stdCell.getNmosWellHeight()+stdCell.getPmosWellHeight());
        drawRing(tech.pselNode(), w, h, 4.8, ring, 'B', pwellRatio, ep);
        drawRing(tech.nselNode(), w, h, 4.8, ring, 'T', pwellRatio, ep);
    } else {
        drawRing(tech.p1(), w, h, ring, ep);
    }
    drawRing(tech.m1(), w, h, ring, ep);
    drawRing(tech.m2(), w, h, ring, ep);
    drawRing(tech.m3(), w, h, ring, ep);
    drawRing(tech.m4(), w, h, ring, ep);
    drawRing(tech.m5(), w, h, ring, ep);

    return ring;
  }
}

