/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: BoundsBuilder.java
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
package com.sun.electric.technology;

import com.sun.electric.database.CellBackup;
import com.sun.electric.database.ImmutableArcInst;
import com.sun.electric.database.geometry.EGraphics;
import com.sun.electric.database.geometry.EPoint;
import com.sun.electric.database.geometry.ERectangle;
import com.sun.electric.database.geometry.Poly;
import com.sun.electric.util.math.FixpCoord;
import com.sun.electric.util.math.GenMath;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

/**
 * A support class to build shapes of arcs and nodes.
 */
public class BoundsBuilder extends AbstractShapeBuilder {

    private long fixpMinX, fixpMinY, fixpMaxX, fixpMaxY;

    public BoundsBuilder(TechPool techPool) {
        setup(techPool);
        clear();
    }

    public BoundsBuilder(CellBackup cellBackup) {
        setup(cellBackup, null, false, true, false, null);
        clear();
    }

    public void clear() {
        fixpMinX = fixpMinY = Long.MAX_VALUE;
        fixpMaxX = fixpMaxY = Long.MIN_VALUE;
    }

    /**
     * Generate bounds of this ImmutableArcInst in easy case.
     * @param a ImmutableArcInst to examine.
     * @param gridCoords grid coordinates to fill.
     * @return true if bounds were generated.
     */
    public boolean genBoundsEasy(ImmutableArcInst a, long[] gridCoords) {
        ArcProto protoType = getTechPool().getArcProto(a.protoId);
        CellBackup cellBackup = getCellBackup();
        if (cellBackup != null ? cellBackup.isHardArc(a.arcId) : !protoType.isEasyShape(a, false)) {
            return false;
        }
        long gridExtendOverMin = a.getGridExtendOverMin();
        long minLayerExtend = gridExtendOverMin + protoType.getMinLayerExtend().getGrid();
        if (minLayerExtend == 0) {
            assert protoType.getNumArcLayers() == 1;
            long x1 = a.tailLocation.getGridX();
            long y1 = a.tailLocation.getGridY();
            long x2 = a.headLocation.getGridX();
            long y2 = a.headLocation.getGridY();
            if (x1 <= x2) {
                gridCoords[0] = x1;
                gridCoords[2] = x2;
            } else {
                gridCoords[0] = x2;
                gridCoords[2] = x1;
            }
            if (y1 <= y2) {
                gridCoords[1] = y1;
                gridCoords[3] = y2;
            } else {
                gridCoords[1] = y2;
                gridCoords[3] = y1;
            }
        } else {
            boolean tailExtended, headExtended;
            AbstractShapeBuilder.Shrinkage shrinkage = getShrinkage();
            if (shrinkage == null) {
                tailExtended = a.isTailExtended();
                headExtended = a.isHeadExtended();
            } else {
                tailExtended = false;
                if (a.isTailExtended()) {
                    short shrinkT = getShrinkage().get(a.tailNodeId);
                    if (shrinkT == AbstractShapeBuilder.Shrinkage.EXTEND_90) {
                        tailExtended = true;
                    } else if (shrinkT != AbstractShapeBuilder.Shrinkage.EXTEND_0) {
                        return false;
                    }
                }
                headExtended = false;
                if (a.isHeadExtended()) {
                    short shrinkH = getShrinkage().get(a.headNodeId);
                    if (shrinkH == AbstractShapeBuilder.Shrinkage.EXTEND_90) {
                        headExtended = true;
                    } else if (shrinkH != AbstractShapeBuilder.Shrinkage.EXTEND_0) {
                        return false;
                    }
                }
            }
            a.makeGridBox(gridCoords, tailExtended, headExtended, gridExtendOverMin + protoType.getMaxLayerExtend().getGrid());
        }
        return true;
    }

    public ERectangle makeBounds() {
        if (fixpMinX <= fixpMaxX) {
            long gridMinX = fixpMinX >> FixpCoord.FRACTION_BITS;
            long gridMinY = fixpMinY >> FixpCoord.FRACTION_BITS;
            long gridMaxX = -((-fixpMaxX) >> FixpCoord.FRACTION_BITS);
            long gridMaxY = -((-fixpMaxY) >> FixpCoord.FRACTION_BITS);
            return ERectangle.fromGrid(gridMinX, gridMinY, gridMaxX - gridMinX, gridMaxY - gridMinY);
        } else {
            return null;
        }
    }

    public ERectangle makeBounds(EPoint anchor, ERectangle oldBounds) {
        long gridMinX, gridMinY, gridMaxX, gridMaxY;
        if (fixpMinX <= fixpMaxX) {
            gridMinX = fixpMinX >> FixpCoord.FRACTION_BITS;
            gridMinY = fixpMinY >> FixpCoord.FRACTION_BITS;
            gridMaxX = -((-fixpMaxX) >> FixpCoord.FRACTION_BITS);
            gridMaxY = -((-fixpMaxY) >> FixpCoord.FRACTION_BITS);
        } else {
            gridMinX = gridMaxX = anchor.getGridX();
            gridMinY = gridMaxY = anchor.getGridX();
        }
        if (oldBounds != null
                && gridMinX == oldBounds.getGridMinX() && gridMinY == oldBounds.getGridMinY()
                && gridMaxX == oldBounds.getGridMaxX() && gridMaxY == oldBounds.getGridMaxY()) {
            return oldBounds;
        }
        return ERectangle.fromGrid(gridMinX, gridMinY, gridMaxX - gridMinX, gridMaxY - gridMinY);
    }

    @Override
    public void addPoly(int numPoints, Poly.Type style, Layer layer, EGraphics graphicsOverride, PrimitivePort pp) {
        if (style == Poly.Type.CIRCLEARC || style == Poly.Type.THICKCIRCLEARC) {
            Point2D.Double p0 = new Point2D.Double(coords[0], coords[1]);
            Point2D.Double p1 = new Point2D.Double(coords[2], coords[3]);
            Point2D.Double p2 = new Point2D.Double(coords[4], coords[5]);
            Rectangle2D bounds = GenMath.arcBBox(p1, p2, p0);
            if (bounds.getMinX() < fixpMinX) {
                fixpMinX = (long) Math.rint(bounds.getMinX());
                if (bounds.getMinX() < fixpMinX) {
                    fixpMinX--;
                }
            }
            if (bounds.getMinY() < fixpMinY) {
                fixpMinY = (long) Math.rint(bounds.getMinY());
                if (bounds.getMinY() < fixpMinY) {
                    fixpMinY--;
                }
            }
            if (bounds.getMaxX() > fixpMaxX) {
                fixpMaxX = (long) Math.rint(bounds.getMaxX());
                if (bounds.getMaxX() > fixpMaxX) {
                    fixpMaxX++;
                }
            }
            if (bounds.getMaxY() > fixpMaxY) {
                fixpMaxY = (long) Math.rint(bounds.getMaxY());
                if (bounds.getMaxY() > fixpMaxY) {
                    fixpMaxY++;
                }
            }
            return;
        }

        if (style == Poly.Type.CIRCLE || style == Poly.Type.THICKCIRCLE || style == Poly.Type.DISC) {
            long fixpCX = coords[0];
            long fixpCY = coords[1];
            long radius;
            if (fixpCX == coords[2]) {
                radius = Math.abs(coords[3] - fixpCY);
            } else if (fixpCY == coords[3]) {
                radius = Math.abs(coords[2] - fixpCX);
            } else {
                radius = GenMath.ceilLong(Point2D.distance(fixpCX, fixpCY, coords[2], coords[3]));
            }
            fixpMinX = Math.min(fixpMinX, fixpCX - radius);
            fixpMinY = Math.min(fixpMinY, fixpCY - radius);
            fixpMaxX = Math.max(fixpMaxX, fixpCX + radius);
            fixpMaxY = Math.max(fixpMaxY, fixpCY + radius);
            return;
        }
        for (int i = 0; i < numPoints; i++) {
            long x = coords[i * 2];
            long y = coords[i * 2 + 1];
            fixpMinX = Math.min(fixpMinX, x);
            fixpMinY = Math.min(fixpMinY, y);
            fixpMaxX = Math.max(fixpMaxX, x);
            fixpMaxY = Math.max(fixpMaxY, y);
        }
    }

    @Override
    public void addBox(Layer layer) {
        fixpMinX = Math.min(fixpMinX, coords[0]);
        fixpMinY = Math.min(fixpMinY, coords[1]);
        fixpMaxX = Math.max(fixpMaxX, coords[2]);
        fixpMaxY = Math.max(fixpMaxY, coords[3]);
    }
}
