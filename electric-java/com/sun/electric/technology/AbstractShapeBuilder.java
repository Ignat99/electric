/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: AbstractShapeBuilder.java
 * Written by: Dmitry Nadezhin
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
 * GNU General Public License for more details.q
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sun.electric.technology;

import com.sun.electric.database.CellBackup;
import com.sun.electric.database.CellRevision;
import com.sun.electric.database.CellTree;
import com.sun.electric.database.ImmutableArcInst;
import com.sun.electric.database.ImmutableElectricObject;
import com.sun.electric.database.ImmutableExport;
import com.sun.electric.database.ImmutableNodeInst;
import com.sun.electric.database.geometry.EGraphics;
import com.sun.electric.database.geometry.EPoint;
import com.sun.electric.database.geometry.Poly;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.id.NodeProtoId;
import com.sun.electric.database.id.PortProtoId;
import com.sun.electric.database.id.PrimitiveNodeId;
import com.sun.electric.database.prototype.PortProto;
import com.sun.electric.database.variable.TextDescriptor;
import com.sun.electric.database.variable.VarContext;
import com.sun.electric.database.variable.Variable;
import com.sun.electric.util.collections.ArrayIterator;
import com.sun.electric.util.math.DBMath;
import com.sun.electric.util.math.FixpCoord;
import com.sun.electric.util.math.GenMath;
import com.sun.electric.util.math.MutableInteger;
import com.sun.electric.util.math.Orientation;

import java.awt.geom.Point2D;
import java.util.BitSet;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * A support class to build shapes of arcs and nodes.
 */
public abstract class AbstractShapeBuilder {

    private Layer.Function.Set onlyTheseLayers;
    private boolean reasonable;
    private boolean wipePins;
    private boolean electrical;
    private final boolean rotateNodes;
    private Orientation orient;
    protected long[] coords = new long[8];
    protected int pointCount;
    private CellBackup cellBackup;
    private Shrinkage shrinkage;
    private TechPool techPool;
    private ImmutableNodeInst curNode;
    private ImmutableArcInst curArc;

    /** Creates a new instance of AbstractShapeBuilder */
    public AbstractShapeBuilder() {
        this(true);
    }

    public AbstractShapeBuilder(boolean rotateNodes) {
        this.rotateNodes = rotateNodes;
    }

    public void setup(TechPool techPool) {
        cellBackup = null;
        shrinkage = null;
        this.techPool = techPool;
        orient = null;
        electrical = false;
        wipePins = false;
        reasonable = false;
        onlyTheseLayers = null;
    }

    public void setup(Cell cell) {
        setup(cell.backup(), null, false, true, false, null);
    }

    public void setup(CellTree cellTree, Orientation orient, boolean electrical, boolean wipePins, boolean reasonable, Layer.Function.Set onlyTheseLayers) {
        setup(cellTree.top, orient, electrical, wipePins, reasonable, onlyTheseLayers);
        techPool = cellTree.techPool;
    }

    public void setup(CellBackup cellBackup, Orientation orient, boolean electrical, boolean wipePins, boolean reasonable, Layer.Function.Set onlyTheseLayers) {
        this.cellBackup = cellBackup;
        this.shrinkage = cellBackup.getShrinkage();
        this.techPool = cellBackup.techPool;
        if (orient == null || orient.isIdent()) {
            this.orient = null;
        } else {
            this.orient = orient.canonic();
        }
        this.electrical = electrical;
        this.wipePins = wipePins;
        this.reasonable = reasonable;
        this.onlyTheseLayers = onlyTheseLayers;
        pointCount = 0;
        curNode = null;
        curArc = null;
    }

    public boolean isWipePins() {
        return wipePins;
    }

    public boolean isElectrical() {
        return electrical;
    }

    public boolean isReasonable() {
        return reasonable;
    }

    public boolean skipLayer(Layer layer) {
        return onlyTheseLayers != null && !onlyTheseLayers.contains(layer.getFunction(), layer.getFunctionExtras());
    }

    public CellBackup getCellBackup() {
        return cellBackup;
    }

    public boolean pinUseCount(ImmutableNodeInst n) {
        return cellBackup != null && cellBackup.cellRevision.pinUseCount(n);
    }

    public boolean hasExportsOnNode(ImmutableNodeInst n) {
        return cellBackup != null && cellBackup.cellRevision.hasExportsOnNode(n);
    }

    public Iterator<ImmutableExport> getExportsOnNode(ImmutableNodeInst originalNode) {
        return cellBackup != null
                ? cellBackup.cellRevision.getExportsOnNode(originalNode)
                : ArrayIterator.<ImmutableExport>emptyIterator();
    }

    public List<ImmutableArcInst> getConnections(BitSet headEnds, ImmutableNodeInst n) {
        return cellBackup != null ? cellBackup.cellRevision.getConnectionsOnNode(headEnds, n) : Collections.<ImmutableArcInst>emptyList();
    }

    public List<ImmutableArcInst> getConnections(BitSet headEnds, ImmutableNodeInst n, PortProtoId portId) {
        return cellBackup != null ? cellBackup.cellRevision.getConnectionsOnPort(headEnds, n, portId) : Collections.<ImmutableArcInst>emptyList();
    }

    public boolean isWiped(ImmutableNodeInst n) {
        return cellBackup != null && cellBackup.isWiped(n);
    }

    public Shrinkage getShrinkage() {
        return shrinkage;
    }

    public TechPool getTechPool() {
        return techPool;
    }

    public void genShapeOfArc(ImmutableArcInst a) {
        curNode = null;
        curArc = a;
        if (genShapeEasy(a)) {
            return;
        }
        pointCount = 0;
        assert curNode == null;
        techPool.getArcProto(a.protoId).getShapeOfArc(this, a);
    }

    public void setCurNode(ImmutableNodeInst n) {
        pointCount = 0;
        curNode = n;
        curArc = null;
    }

    public ImmutableElectricObject getCurObj() {
        if (curNode != null) return curNode;
        return curArc;
    }

    /**
     * Returns the polygons that describe node "n", given a set of
     * NodeLayer objects to use.
     * This method is called by the specific Technology overrides of getShapeOfNode().
     * @param n the ImmutableNodeInst that is being described.
     * @param np PrimitiveNode prototype of give ImmutableNodeInst in TechPool of Memoization
     * @param primLayers an array of NodeLayer objects to convert to Poly objects.
     * @param graphicsOverride the graphics override to use for all generated polygons (if not null).
     * The prototype of this NodeInst must be a PrimitiveNode and not a Cell.
     */
    public void genShapeOfNode(ImmutableNodeInst n, PrimitiveNode np, Technology.NodeLayer[] primLayers, EGraphics graphicsOverride) {
        pointCount = 0;
        curNode = n;
        curArc = null;
        // add in the basic polygons
        for (int i = 0; i < primLayers.length; i++) {
            Technology.NodeLayer primLayer = primLayers[i];
            Layer layer = primLayer.getLayer();
            if (skipLayer(layer)) {
                continue;
            }
            Poly.Type style = primLayer.getStyle();
            PrimitivePort pp = primLayer.getPort(np);
            if (layer.isCarbonNanotubeLayer()
                    && (np.getFunction() == PrimitiveNode.Function.TRANMOSCN || np.getFunction() == PrimitiveNode.Function.TRAPMOSCN)) {
                CarbonNanotube cnd = new CarbonNanotube(n, primLayer);
                for (int j = 0; j < cnd.numTubes; j++) {
                    cnd.fillCutPoly(j, style, layer, pp);
                }
                assert graphicsOverride == null;
                continue;
            }

            int representation = primLayer.getRepresentation();
            if (representation == Technology.NodeLayer.BOX) {
                EdgeH leftEdge = primLayer.getLeftEdge();
                EdgeH rightEdge = primLayer.getRightEdge();
                EdgeV topEdge = primLayer.getTopEdge();
                EdgeV bottomEdge = primLayer.getBottomEdge();
                long portLowX = leftEdge.getFixpValue(n.size);
                long portHighX = rightEdge.getFixpValue(n.size);
                long portLowY = bottomEdge.getFixpValue(n.size);
                long portHighY = topEdge.getFixpValue(n.size);
                pushPoint(portLowX, portLowY);
                pushPoint(portHighX, portLowY);
                pushPoint(portHighX, portHighY);
                pushPoint(portLowX, portHighY);
            } else if (representation == Technology.NodeLayer.POINTS) {
                Technology.TechPoint[] points = primLayer.getPoints();
                for (int j = 0; j < points.length; j++) {
                    long x = points[j].getX().getFixpValue(n.size);
                    long y = points[j].getY().getFixpValue(n.size);
                    pushPoint(x, y);
                }
            } else if (representation == Technology.NodeLayer.MULTICUTBOX) {
                MultiCutData mcd = new MultiCutData(n, primLayer);
                int numExtraLayers = reasonable ? mcd.cutsReasonable : mcd.cutsTotal;
                for (int j = 0; j < numExtraLayers; j++) {
                    mcd.fillCutPoly(j, style, layer, pp);
                }
                assert graphicsOverride == null;
                continue;
            }

            if (style.isText()) {
                assert graphicsOverride == null;
                pushTextPoly(style, layer, pp, primLayer.getMessage(), primLayer.getDescriptor());
            } else {
                pushPoly(style, layer, graphicsOverride, pp);
            }
        }
    }

    public void pushOutlineSegment(EPoint[] outline, int offset, int count,
            boolean removeCoincidentPoints, boolean removeSameStartEnd) {
        if (removeSameStartEnd) {
            while (count > 1 && outline[offset + count - 1].equals(outline[0])) {
                count--;
            }
        }
        if (removeCoincidentPoints) {
            EPoint prevP = null;
            for (int i = 0; i < count; i++) {
                EPoint p = outline[offset + i];
                if (prevP != null && p.equals(prevP)) {
                    continue;
                }
                pushPoint(p);
                prevP = p;
            }
        } else {
            for (int i = 0; i < count; i++) {
                pushPoint(outline[offset + i]);
            }
        }
    }

    public void genShapeOfPort(ImmutableNodeInst n, PrimitivePort pp) {
        pointCount = 0;
        curNode = n;
        curArc = null;
        pp.genShape(this, n);
    }

    public void genShapeOfPort(ImmutableNodeInst n, PrimitivePort pp, Point2D selectPt) {
        if (selectPt == null) {
            throw new NullPointerException();
        }
        pointCount = 0;
        curNode = n;
        curArc = null;
        pp.genShape(this, n, selectPt);
    }

    /**
     * Puts into shape builder s the polygons that describes port "pp" of node "n".
     * This method is overridden by specific Technologies.
     * @param n the ImmutableNodeInst that is being described.
     * @param pn prototype of the ImmutableNodeInst in this Technology
     * @param pp PrimitivePort
     */
    public void genShapeOfPort(ImmutableNodeInst n, PrimitiveNode pn, PrimitivePort pp) {
        // standard port computation
        long portLowX = pp.getLeft().getFixpValue(n.size);
        long portHighX = pp.getRight().getFixpValue(n.size);
        long portLowY = pp.getBottom().getFixpValue(n.size);
        long portHighY = pp.getTop().getFixpValue(n.size);
        pushPoint(portLowX, portLowY);
        pushPoint(portHighX, portLowY);
        pushPoint(portHighX, portHighY);
        pushPoint(portLowX, portHighY);
        pushPoly(Poly.Type.FILLED, null, null, null);
    }

    /**
     * Method to fill in an AbstractShapeBuilder a polygon that describes this ImmutableArcInst in grid units.
     * The polygon is described by its width, and style.
     * @param a the arc information.
     * @param gridWidth the gridWidth of the Poly.
     * @param style the style of the Poly.
     * @param layer layer of the Poly
     * @param graphicsOverride graphics override of the Poly
     */
    public void makeGridPoly(ImmutableArcInst a, long gridWidth, Poly.Type style, Layer layer, EGraphics graphicsOverride) {
        // zero-width polygons are simply lines
        if (gridWidth <= 0) {
            pushPoint(a.tailLocation);
            pushPoint(a.headLocation);
            if (style == Poly.Type.FILLED) {
                style = Poly.Type.OPENED;
            }
            pushPoly(style, layer, graphicsOverride, null);
            return;
        }

        // make the polygon
        long w2 = gridWidth << (FixpCoord.FRACTION_BITS - 1);
        short shrinkT, shrinkH;
        if (shrinkage == null) {
            shrinkT = a.isTailExtended() ? Shrinkage.EXTEND_90 : Shrinkage.EXTEND_0;
            shrinkH = a.isHeadExtended() ? Shrinkage.EXTEND_90 : Shrinkage.EXTEND_0;
        } else {
            shrinkT = a.isTailExtended() ? shrinkage.get(a.tailNodeId) : Shrinkage.EXTEND_0;
            shrinkH = a.isHeadExtended() ? shrinkage.get(a.headNodeId) : Shrinkage.EXTEND_0;
        }

        int angle = a.getDefinedAngle();
        long w2x = (long) GenMath.rint(w2 * GenMath.cos(angle));
        long w2y = (long) GenMath.rint(w2 * GenMath.sin(angle));
        long tx = 0;
        long ty = 0;
        if (shrinkT == Shrinkage.EXTEND_90) {
            tx = -w2x;
            ty = -w2y;
        } else if (shrinkT != Shrinkage.EXTEND_0) {
            int oppAngle = a.getOppositeAngle();
            if (oppAngle == -1) {
                oppAngle = 0;
            }
            Poly.Point e = computeExtension(w2, -w2x, -w2y, oppAngle, shrinkT);
            tx = e.getFixpX();
            ty = e.getFixpY();
        }
        long hx = 0;
        long hy = 0;
        if (shrinkH == Shrinkage.EXTEND_90) {
            hx = w2x;
            hy = w2y;
        } else if (shrinkH != Shrinkage.EXTEND_0) {
            Poly.Point e = computeExtension(w2, w2x, w2y, angle, shrinkH);
            hx = e.getFixpX();
            hy = e.getFixpY();
        }

        pushPoint(a.tailLocation, tx - w2y, ty + w2x);
        pushPoint(a.tailLocation, tx + w2y, ty - w2x);
        pushPoint(a.headLocation, hx + w2y, hy - w2x);
        pushPoint(a.headLocation, hx - w2y, hy + w2x);

        // somewhat simpler if rectangle is Manhattan
        if (gridWidth != 0 && style.isOpened()) {
            pushPoint(a.tailLocation, tx - w2y, ty + w2x);
        }
        pushPoly(style, layer, graphicsOverride, null);
    }

    /**
     * Computes extension vector of wire,
     */
    private static Poly.Point computeExtension(long w2, long ix1, long iy1, int angle, short shrink) {
    	int shrinkBits = shrink & Shrinkage.EXTEND_CONTROL_MASK;
        int valueFromBits = shrink >>> Shrinkage.EXTEND_VALUE_SHIFT;
        if (shrinkBits == Shrinkage.EXTEND_ALL_BUT_ONE) {
        	if (angle == valueFromBits) shrinkBits = Shrinkage.EXTEND_0; else
        		shrinkBits = Shrinkage.EXTEND_90;
        }
        if (shrinkBits == Shrinkage.EXTEND_90) {
            return Poly.fromFixp(ix1, iy1);
        }
        if (shrinkBits == Shrinkage.EXTEND_0) {
            return Poly.fromFixp(0, 0);
        }
        int angle2 = angle - 1350;
        if (shrinkBits == Shrinkage.EXTEND_ANY)
	        angle2 = valueFromBits - angle;
        if (angle2 < 0) angle2 += 3600;
        double x1 = ix1;
        double y1 = iy1;
        double s1;
        if (y1 == 0) {
            if (x1 > 0) {
                s1 = x1;
                x1 = 1;
            } else if (x1 < 0) {
                s1 = -x1;
                x1 = -1;
            } else {
                return Poly.fromFixp(0, 0);
            }
        } else if (x1 == 0) {
            if (y1 > 0) {
                s1 = y1;
                y1 = 1;
            } else {
                s1 = -y1;
                y1 = -1;
            }
        } else {
            s1 = x1 * x1 + y1 * y1;
        }

        double x2 = GenMath.rint(w2 * GenMath.cos(angle2));
        double y2 = GenMath.rint(w2 * GenMath.sin(angle2));
        double s2;
        if (y2 == 0) {
            if (x2 > 0) {
                s2 = x2;
                x2 = 1;
            } else if (x2 < 0) {
                s2 = -x2;
                x2 = -1;
            } else {
                return Poly.fromFixp(0, 0);
            }
        } else if (x2 == 0) {
            if (y2 > 0) {
                s2 = y2;
                y2 = 1;
            } else {
                s2 = -y2;
                y2 = -1;
            }
        } else {
            s2 = x2 * x2 + y2 * y2;
        }

        double det = x1 * y2 - y1 * x2;
        if (det == 0) {
            return Poly.fromFixp(0, 0);
        }
        double x = (x2 * s1 + x1 * s2) / det;
        double y = (y2 * s1 + y1 * s2) / det;
        long lx = (long) GenMath.rint(x);
        long ly = (long) GenMath.rint(y);
        lx = lx + iy1;
        ly = ly - ix1;
        if (det < 0) {
            lx = -lx;
            ly = -ly;
        }
        return Poly.fromFixp(lx, ly);
    }

    /**
     * Generate shape of this ImmutableArcInst in easy case.
     * @param a the arc information.
     * @return true if shape was generated.
     */
    private boolean genShapeEasy(ImmutableArcInst a) {
        ArcProto protoType = techPool.getArcProto(a.protoId);
        if (cellBackup != null ? cellBackup.isHardArc(a.arcId) : !protoType.isEasyShape(a, false)) {
            return false;
        }
        long gridExtendOverMin = a.getGridExtendOverMin();
        long minLayerExtend = gridExtendOverMin + protoType.getMinLayerExtend().getGrid();
        if (minLayerExtend == 0) {
            assert protoType.getNumArcLayers() == 1;
            Technology.ArcLayer primLayer = protoType.getArcLayer(0);
            Layer layer = primLayer.getLayer();
            if (skipLayer(layer)) {
                return true;
            }
            Poly.Type style = primLayer.getStyle();
            if (style == Poly.Type.FILLED) {
                style = Poly.Type.OPENED;
            }
            coords[0] = a.tailLocation.getGridX() << FixpCoord.FRACTION_BITS;
            coords[1] = a.tailLocation.getGridY() << FixpCoord.FRACTION_BITS;
            coords[2] = a.headLocation.getGridX() << FixpCoord.FRACTION_BITS;
            coords[3] = a.headLocation.getGridY() << FixpCoord.FRACTION_BITS;
            assert curNode == null;
            if (orient != null && orient.canonic() != Orientation.IDENT) {
                orient.transformPoints(2, coords);
            }
            addPoly(2, style, layer, null, null);
            assert pointCount == 0;
            return true;
        }
        boolean tailExtended, headExtended;
        if (shrinkage == null) {
            tailExtended = a.isTailExtended();
            headExtended = a.isHeadExtended();
        } else {
            tailExtended = false;
            if (a.isTailExtended()) {
                short shrinkT = shrinkage.get(a.tailNodeId);
                if (shrinkT == Shrinkage.EXTEND_90) {
                    tailExtended = true;
                } else if (shrinkT != Shrinkage.EXTEND_0) {
                    return false;
                }
            }
            headExtended = false;
            if (a.isHeadExtended()) {
                short shrinkH = shrinkage.get(a.headNodeId);
                if (shrinkH == Shrinkage.EXTEND_90) {
                    headExtended = true;
                } else if (shrinkH != Shrinkage.EXTEND_0) {
                    return false;
                }
            }
        }
        for (int i = 0, n = protoType.getNumArcLayers(); i < n; i++) {
            Technology.ArcLayer primLayer = protoType.getArcLayer(i);
            Layer layer = primLayer.getLayer();
            assert primLayer.getStyle() == Poly.Type.FILLED;
            if (skipLayer(layer)) {
                continue;
            }
            a.makeFixpBox(coords, tailExtended, headExtended, gridExtendOverMin + protoType.getLayerExtend(i).getGrid());
            pushBox(layer);
        }
        return true;
    }

    /**
     * Technologies use this method to push a point into the point buffer
     * @param p Electric point
     * @param fixpX x-displacement in fixed-point units
     * @param fixpY y-displacement in fixed-point units
     */
    public void pushPoint(EPoint p, long fixpX, long fixpY) {
        pushPoint(p.getFixpX() + fixpX, p.getFixpY() + fixpY);
    }

    /**
     * Technologies use this method to push a point into the point buffer
     * @param p Electric point
     * @param fixpX x-displacement in fixed-point units
     * @param fixpY y-displacement in fixed-point units
     */
    public void pushPoint(EPoint p, double fixpX, double fixpY) {
        pushPoint(p, (long) Math.rint(fixpX), (long) Math.rint(fixpY));
    }

    /**
     * Technologies use this method to push a point into the point buffer
     * @param fixpX x-displacement in grid units
     * @param fixpY y-displacement in grid units
     */
    public void pushPoint(double fixpX, double fixpY) {
        pushPoint((long) GenMath.rint(fixpX), (long) GenMath.rint(fixpY));
    }

    /**
     * Technologies use this method to push a point into the point buffer
     * @param p Electric point
     */
    public void pushPoint(EPoint p) {
        pushPoint(p.getGridX() << FixpCoord.FRACTION_BITS, p.getGridY() << FixpCoord.FRACTION_BITS);
    }

    public void pushPoint(long fixpX, long fixpY) {
        if (pointCount * 2 >= coords.length) {
            resize();
        }
        coords[pointCount * 2] = fixpX;
        coords[pointCount * 2 + 1] = fixpY;
        pointCount++;
    }

    private void resize() {
        long[] newCoords = new long[coords.length * 2];
        System.arraycopy(coords, 0, newCoords, 0, coords.length);
        coords = newCoords;
    }

    /**
     * Technologies use this method to emit a Poly from points in the points buffer.
     * @param style style of Poly
     * @param layer layer of Poly
     * @param graphicsOverride optional graphics override
     * @param pp port connected to this Poly
     */
    public void pushPoly(Poly.Type style, Layer layer, EGraphics graphicsOverride, PrimitivePort pp) {
        if (!electrical) {
            pp = null;
        }
        transformCoords(style);
        if (style == Poly.Type.FILLED && pointCount == 4 && graphicsOverride == null && pp == null) {
            if (coords[0] == coords[2] && coords[4] == coords[6]
                    && coords[1] == coords[7] && coords[3] == coords[5]
                    || coords[0] == coords[6] && coords[2] == coords[4]
                    && coords[1] == coords[3] && coords[5] == coords[7]) {
                long lx = Math.min(coords[0], coords[4]);
                long hx = Math.max(coords[0], coords[4]);
                long ly = Math.min(coords[1], coords[5]);
                long hy = Math.max(coords[1], coords[5]);
                pointCount = 0;
                coords[0] = lx;
                coords[1] = ly;
                coords[2] = hx;
                coords[3] = hy;
                addBox(layer);
                return;
            }
        }
        addPoly(pointCount, style, layer, graphicsOverride, pp);
        pointCount = 0;
    }

    /**
     * Technologies use this method to emit a text Poly from points in the points buffer.
     * @param style style of Poly
     * @param layer layer of Poly
     * @param pp port connected to this Poly
     * @param message text message
     * @param descriptor text descriptor
     */
    public void pushTextPoly(Poly.Type style, Layer layer, PrimitivePort pp, String message, TextDescriptor descriptor) {
        if (!electrical) {
            pp = null;
        }
        transformCoords(style);
        addTextPoly(pointCount, style, layer, pp, message, descriptor);
        pointCount = 0;
    }

    private void transformCoords(Poly.Type style) {
        if (curNode != null) {
            if (rotateNodes && !curNode.orient.isIdent()) {
                // special case for Poly type CIRCLEARC and THICKCIRCLEARC: if transposing, reverse points
                if ((style == Poly.Type.CIRCLEARC || style == Poly.Type.THICKCIRCLEARC)
                        && curNode.orient.canonic().isCTranspose()) {
                    assert pointCount == 3;
                    long t;
                    t = coords[2];
                    coords[2] = coords[4];
                    coords[4] = t;
                    t = coords[3];
                    coords[3] = coords[5];
                    coords[5] = t;
                }
                curNode.orient.transformPoints(pointCount, coords);
            }
            long anchorX = curNode.anchor.getGridX() << FixpCoord.FRACTION_BITS;
            long anchorY = curNode.anchor.getGridY() << FixpCoord.FRACTION_BITS;
            for (int i = 0; i < pointCount; i++) {
                coords[i * 2 + 0] += anchorX;
                coords[i * 2 + 1] += anchorY;
            }
        }
        if (orient != null) {
            // special case for Poly type CIRCLEARC and THICKCIRCLEARC: if transposing, reverse points
            if ((style == Poly.Type.CIRCLEARC || style == Poly.Type.THICKCIRCLEARC)
                    && orient.canonic().isCTranspose()) {
                assert pointCount == 3;
                long t;
                t = coords[2];
                coords[2] = coords[4];
                coords[4] = t;
                t = coords[3];
                coords[3] = coords[5];
                coords[5] = t;
            }

            orient.transformPoints(pointCount, coords);
        }
    }

    private void pushBox(Layer layer) {
        assert pointCount == 0;
        if (curNode != null && !curNode.orient.isManhattan() || orient != null && !orient.isManhattan()) {
            long lx = coords[0];
            long ly = coords[1];
            long hx = coords[2];
            long hy = coords[3];
            pushPoint(lx, ly);
            pushPoint(hx, ly);
            pushPoint(hx, hy);
            pushPoint(lx, hy);
            pushPoly(Poly.Type.FILLED, layer, null, null);
            return;
        }
        if (curNode != null) {
            if (rotateNodes && !curNode.orient.isIdent()) {
                curNode.orient.rectangleBounds(coords);
            }
            long anchorX = curNode.anchor.getGridX();
            long anchorY = curNode.anchor.getGridY();
            coords[0] += anchorX;
            coords[1] += anchorY;
            coords[2] += anchorX;
            coords[3] += anchorY;
        }
        if (orient != null) {
            orient.rectangleBounds(coords);
        }
        addBox(layer);
    }

    /**
     * Subclasses of AbstractShapeBuilder redefine this method to register transformed text Poly.
     * Its fixed-point i-th x coordinate is at coords[i*2 + 0].
     * Its fixed-point i-th x coordinate is at coords[i*2 + 1].
     * The dummy implementation redirects text poly to the plain #addPoly method.
     * @param numPoints number of points
     * @param style style of Poly
     * @param layer layer of Poly
     * @param pp port connected to this Poly
     * @param message text message
     * @param descriptor text descriptor
     */
    public void addTextPoly(int numPoints, Poly.Type style, Layer layer, PrimitivePort pp, String message, TextDescriptor descriptor) {
        addPoly(numPoints, style, layer, null, pp);
    }

    /**
     * Subclasses of AbstractShapeBuilder redefine this method to register transformed Poly.
     * Its fixed-point i-th x coordinate is at coords[i*2 + 0].
     * Its fixed-point i-th x coordinate is at coords[i*2 + 1].
     * @param numPoints number of points
     * @param style style of Poly
     * @param layer layer of Poly
     * @param graphicsOverride optional graphics override
     * @param pp port connected to this Poly
     */
    protected abstract void addPoly(int numPoints, Poly.Type style, Layer layer, EGraphics graphicsOverride, PrimitivePort pp);

    /**
     * Subclasses of AbstractShapeBuilder redefine this method to register transformed box.
     * Its fixed-point lower x coordinate is at coords[0].
     * Its fixed-point lower y coordinate is at coords[1].
     * Its fixed-point higher x coordinate is at coords[2].
     * Its fixed-point higher y coordinate is at coords[3].
     * Its style is Poly.Type.FILLED.
     * @param layer layer of the box
     */
    protected abstract void addBox(Layer layer);

    public static class Shrinkage {

    	private static final boolean NEW_WAY = true;

        /** mask for control values */									private static final int EXTEND_CONTROL_MASK = 7;
    	/** extend all arcs on node by full amount (half width) */		public static final short EXTEND_90 = 0;
        /** do not extend any arcs on node */							public static final short EXTEND_0 = 1;
        /** extend all arcs on node by partial (for 45-degree joins) */	public static final short EXTEND_45 = 2;
        /** extend arcs fully; do not extension one at listed value */	private static final short EXTEND_ALL_BUT_ONE = 3;
        /** extend arcs arbitrarily (listed value is sum of two arc angles) */	private static final short EXTEND_ANY = 4;
        /** shift amount to get the listed value */						private static final int EXTEND_VALUE_SHIFT = 3;

        private static final int ANGLE_SHIFT = 12;
        private static final int ANGLE_MASK = (1 << ANGLE_SHIFT) - 1;
        private static final int ONE_NONMANHATTAN_MASK = 1 << (ANGLE_SHIFT * 2);
        private static final int ANGLE_DIAGONAL_MASK = ONE_NONMANHATTAN_MASK << 1;
        private static final int ANGLE_45_MASK = ANGLE_DIAGONAL_MASK << 1;
        private static final int ANGLE_COUNT_SHIFT = ANGLE_SHIFT * 2 + 3;
        private final short[] shrink;

        public Shrinkage() {
            shrink = new short[0];
        }

        public Shrinkage(CellBackup cellBackup) {
            CellRevision cellRevision = cellBackup.cellRevision;
            TechPool techPool = cellBackup.techPool;
            int maxNodeId = -1;
            for (int nodeIndex = 0; nodeIndex < cellRevision.nodes.size(); nodeIndex++) {
                maxNodeId = Math.max(maxNodeId, cellRevision.nodes.get(nodeIndex).nodeId);
            }
            int[] angles = new int[maxNodeId + 1];
            for (ImmutableArcInst a : cellRevision.arcs) {
                ArcProto ap = techPool.getArcProto(a.protoId);
                if (a.getGridExtendOverMin() + ap.getMaxLayerExtend().getGrid() == 0) {
                    continue;
                }
                if (a.tailNodeId == a.headNodeId && a.tailPortId == a.headPortId) {
                    // Fake register for full shrinkage
                    registerArcEnd(angles, a.tailNodeId, 0, false, false);
                    continue;
                }
                boolean is90 = a.isManhattan();
                registerArcEnd(angles, a.tailNodeId, a.getOppositeAngle(), is90, a.isTailExtended());
                registerArcEnd(angles, a.headNodeId, a.getAngle(), is90, a.isHeadExtended());
            }
            short[] shrink = new short[maxNodeId + 1];
            for (int nodeIndex = 0; nodeIndex < cellRevision.nodes.size(); nodeIndex++) {
                ImmutableNodeInst n = cellRevision.nodes.get(nodeIndex);
                shrink[n.nodeId] = EXTEND_90;
                NodeProtoId np = n.protoId;
                if (np instanceof PrimitiveNodeId) {
                  PrimitiveNode pnp = techPool.getPrimitiveNode((PrimitiveNodeId)np);
                  if (pnp.getFunction() == PrimitiveNode.Function.PIN ||
                	  pnp.getFunction() == PrimitiveNode.Function.CONTACT) {
                	  if (np instanceof PrimitiveNodeId && pnp.isArcsShrink()) {
                		  shrink[n.nodeId] = computeShrink(angles[n.nodeId]);
                		}
                    }
                }
//  String name = n.name.toString();
//  if (!name.startsWith("pin") && !name.startsWith("node")) {
//		int bits = shrink[n.nodeId] & EXTEND_CONTROL_MASK;
//		int value = shrink[n.nodeId] >>> EXTEND_VALUE_SHIFT;
//		switch (bits) {
//			case EXTEND_90: System.out.println("NODE "+n.name+" HAS NO SHRINKAGE"); break;
//			case EXTEND_0: System.out.println("NODE "+n.name+" HAS FULL SHRINKAGE"); break;
//			case EXTEND_45: System.out.println("NODE "+n.name+" HAS 45-DEGREE SHRINKAGE"); break;
//			case EXTEND_ALL_BUT_ONE: System.out.println("NODE "+n.name+" HAS NO SHRINKAGE EXCEPT ARC AT ANGLE "+value); break;
//			case EXTEND_ANY: System.out.println("NODE "+n.name+" HAS ARBITRARY SHRINKAGE FOR ANGLES THAT ADD UP TO "+value); break;
//			default: System.out.println("NODE "+n.name+" HAS UNKNOWN SHRINKAGE!!!!!!!!"); break;
//	    }
//  }
            }
            this.shrink = shrink;
        }

        /**
         * Method to tell the "end shrink" factors on all arcs on a specified ImmutableNodeInst.
         * EXTEND_90 indicates no shortening (extend the arcs fully, by half their width).
         * EXTEND_0 indicates full shortening (no extension).
         * EXTEND_45 indicates partial shortening (for 45-degree joins).
         * EXTEND_ALL_BUT_ONE indicates no shortening for all arcs EXCEPT the one at the listed angle, which is gets no extension.
         * EXTEND_ANY indicates shortening of two arcs whose sum is the listed value (modulo 3600).
         * If this ImmutableNodeInst is a pin which can "isArcsShrink" and this pin connects
         * exactly two arcs with extended ends and angle between arcs is acute.
         * The "listed value" is found by shifting the bits by EXTEND_VALUE_SHIFT and has the range [0..3600).
         * @param nodeId nodeId of specified ImmutableNodeInst
         * @return shrink factor of specified ImmutableNodeInst is wiped.
         */
        public short get(int nodeId) {
            return nodeId < shrink.length ? shrink[nodeId] : 0;
        }

        private void registerArcEnd(int[] angles, int nodeId, int angle, boolean is90, boolean extended) {
            // consider undefined angles to be horizontal
            if (angle == -1) {
                angle = 0;
            }
            assert angle >= 0 && angle < 3600;
            int ang = angles[nodeId];
            if (extended) {
                int count = ang >>> ANGLE_COUNT_SHIFT;
                switch (count) {
                    case 0:
                        ang |= angle;
                        ang += (1 << ANGLE_COUNT_SHIFT);
                        break;
                    case 1:
                		int ang0 = ang & ANGLE_MASK;
                        ang |= (angle << ANGLE_SHIFT);
                        ang += (1 << ANGLE_COUNT_SHIFT);
                    	if (NEW_WAY) {
                    		int da = Math.abs(ang0 - angle);
                    		if (is45(da)) ang |= ANGLE_45_MASK;
                    	}
                        break;
                    case 2:
                    	if (NEW_WAY) {
                    		int ang0Orig = ang & ANGLE_MASK;
                    		int ang1Orig = (ang >> ANGLE_SHIFT) & ANGLE_MASK;
							MutableInteger ang0MI = new MutableInteger(ang0Orig);
                            MutableInteger ang1MI = new MutableInteger(ang1Orig);
//System.out.print("REDUCING ANGLES "+ang0Orig+" AND "+ang1Orig+" AND "+angle);
                            int extraBits = reduceThreeAngles(ang0MI, ang1MI, angle);
//System.out.println(" ....... IT BECOMES "+ang0MI.intValue()+" AND "+ang1MI.intValue()+" WITH RETURN="+extraBits);
                            if (extraBits != -1)
								ang = extraBits | (ang & ANGLE_DIAGONAL_MASK) | (1 << ANGLE_COUNT_SHIFT) |
									ang0MI.intValue() | (ang1MI.intValue() << ANGLE_SHIFT);
                    	}
                    	ang += (1 << ANGLE_COUNT_SHIFT);
                        break;
                }
                if (!is90) {
                    ang |= ANGLE_DIAGONAL_MASK;
                }
            } else {
                ang |= (3 << ANGLE_COUNT_SHIFT);
            }
            angles[nodeId] = ang;
        }

        /**
         * Method to reduce three different arc angles down to two for the purposes of shrinkage.
         * Reduction happens for common or in-line angles.
         * @param ang1 the first angle (new angle returned in here).
         * @param ang2 the second angle (new angle returned in here).
         * @param ang3 the third angle.
         * @return extra bits to include in angle factor (-1 if it cannot be reduced to 2 angles).
         */
        private int reduceThreeAngles(MutableInteger ang1MI, MutableInteger ang2MI, int ang3)
        {
        	int ang1 = ang1MI.intValue();
        	int ang2 = ang2MI.intValue();
        	int extraBits = 0;
        	if ((ang1%900) != 0 || (ang2%900) != 0 || (ang3%900) != 0)
        	{
        		if ((ang1%450) == 0 && (ang2%450) == 0 && (ang3%450) == 0) extraBits = ANGLE_45_MASK;
        	}

        	// first check for duplicate angles
        	if (ang1 == ang3 || ang2 == ang3) return extraBits;
        	if (ang1 == ang2) { ang1MI.setValue(ang3); return extraBits; }

        	// now check for right or inline angles
        	if ((ang1%900) == (ang3%900)) {
                int da1 = Math.abs(ang1 - ang2);
                int da2 = Math.abs(ang3 - ang2);
                if (is45(da1) && is45(da2)) { ang1MI.setValue(ang2);  return ONE_NONMANHATTAN_MASK; }
                if (900 < da1 && da1 < 2700) return extraBits;
                if (900 < da2 && da2 < 2700) { ang1MI.setValue(ang3);  return extraBits; }
        	}
        	if ((ang2%900) == (ang3%900)) {
                int da1 = Math.abs(ang2 - ang1);
                int da2 = Math.abs(ang3 - ang1);
                if (is45(da1) && is45(da2)) return ONE_NONMANHATTAN_MASK;
                if (900 < da1 && da1 < 2700) return extraBits;
                if (900 < da2 && da2 < 2700) { ang2MI.setValue(ang3);  return extraBits; }
        	}
        	if ((ang1%900) == (ang2%900)) {
                int da1 = Math.abs(ang1 - ang3);
                int da2 = Math.abs(ang2 - ang3);
                if (is45(da1) && is45(da2)) { ang1MI.setValue(ang3);  return ONE_NONMANHATTAN_MASK; }
                if (900 < da1 && da1 < 2700) { ang2MI.setValue(ang3);  return extraBits; }
                if (900 < da2 && da2 < 2700) { ang1MI.setValue(ang3);  return extraBits; }
        	}
        	return -1;
        }

        private boolean is45(int angle) {
        	return angle == 450 || angle == 3150;
        }

        static short computeShrink(int angs) {
            boolean hasAny = (angs & ANGLE_DIAGONAL_MASK) != 0;
            boolean hasAll45 = (angs & ANGLE_45_MASK) != 0;
            boolean hasOneNoShrink = (angs & ONE_NONMANHATTAN_MASK) != 0;
            int count = angs >>> ANGLE_COUNT_SHIFT;

            if (count == 2) {
                int ang0 = angs & ANGLE_MASK;
            	if (NEW_WAY) {
            		if (hasOneNoShrink) return (short) (EXTEND_ALL_BUT_ONE | (ang0 << EXTEND_VALUE_SHIFT));
            		if (hasAll45) return EXTEND_45;
            	}
	            if (hasAny) {
	                int ang1 = (angs >> ANGLE_SHIFT) & ANGLE_MASK;
	                int da = ang0 > ang1 ? ang0 - ang1 : ang1 - ang0;
	                if (da == 900 || da == 2700) {
	                    return EXTEND_90;
	                }
	                if (da == 1800) {
	                    return EXTEND_0;
	                }
	                if (900 < da && da < 2700) {
	                    int a = ang0 + ang1;
	                    if (a >= 3600) {
	                        a -= 3600;
	                    }
	                    return (short) (EXTEND_ANY | (a << EXTEND_VALUE_SHIFT));
	                }
	            }
            }
            return EXTEND_90;
        }
    }

    /**
     * Class CarbonNanotube determines the location of carbon nanotube rails in the transistor.
     */
    private class CarbonNanotube {

        private ImmutableNodeInst niD;
        private Technology.NodeLayer tubeLayer;
        private int numTubes;
        private long tubeSpacing;

        /**
         * Constructor to initialize for carbon nanotube rails.
         */
        private CarbonNanotube(ImmutableNodeInst niD, Technology.NodeLayer tubeLayer) {
            this.niD = niD;
            this.tubeLayer = tubeLayer;
            numTubes = 10;
            Variable var = niD.getVar(Technology.NodeLayer.CARBON_NANOTUBE_COUNT);
            if (var != null) {
                numTubes = ((Integer) var.getObject()).intValue();
            }
            tubeSpacing = -1;
            var = niD.getVar(Technology.NodeLayer.CARBON_NANOTUBE_PITCH);
            if (var != null) {
                tubeSpacing = DBMath.lambdaToGrid(((Double) var.getObject()).doubleValue());
            }
        }

        /**
         * Method to fill in the rails of the carbon nanotube transistor.
         * Node is in "ni" and the nanotube number (0 based) is in "r".
         */
        private void fillCutPoly(int r, Poly.Type style, Layer layer, PrimitivePort pp) {
            Technology.TechPoint[] techPoints = tubeLayer.getPoints();
            long lx = techPoints[0].getX().getGridValue(niD.size);
            long hx = techPoints[1].getX().getGridValue(niD.size);
            long ly = techPoints[0].getY().getGridValue(niD.size);
            long hy = techPoints[1].getY().getGridValue(niD.size);
            if (tubeSpacing < 0) {
                tubeSpacing = (hx - lx) / (numTubes * 2 - 1);
            }
            long tubeDia = (hx - lx - (numTubes - 1) * tubeSpacing) / numTubes;
//            long tubeHalfHeight = (fixpHY - fixpLY) / 2;
//System.out.println("LAYER FROM "+lx+"<=X<="+hx+" AND "+ly+"<=Y<="+hy+" TUBE SPACING="+tubeSpacing+" TUBE DIAMETER="+tubeDia);
            long cX = lx + (tubeDia >> 1) + (tubeDia + tubeSpacing) * r;
//            long cY = 0; // + (ly + hy)>>1;
            long lX = cX - (tubeDia >> 1);
            long hX = cX + (tubeDia >> 1);
//            long lY = cY - tubeHalfHeight;
//            long hY = cY + tubeHalfHeight;
            long lY = ly;
            long hY = hy;
//System.out.println("   SO TUBE "+r+", CENTERED AT ("+cX+","+cY+") IS FROM "+lX+"<=X<="+hX+" AND "+lY+"<=Y<="+hY);
            pushPoint(lX << FixpCoord.FRACTION_BITS, lY << FixpCoord.FRACTION_BITS);
            pushPoint(hX << FixpCoord.FRACTION_BITS, lY << FixpCoord.FRACTION_BITS);
            pushPoint(hX << FixpCoord.FRACTION_BITS, hY << FixpCoord.FRACTION_BITS);
            pushPoint(lX << FixpCoord.FRACTION_BITS, hY << FixpCoord.FRACTION_BITS);
            pushPoly(style, layer, null, pp);
        }
    }

    /**
     * Class MultiCutData determines the locations of cuts in a multi-cut contact node.
     */
    private class MultiCutData {

        /** the size of each cut */
        private long cutSizeX, cutSizeY;
        /** the separation between cuts */
        private long cutSep;
        /** the separation between cuts */
        private long cutSep1D;
        /** the separation between cuts in 3-neighboring or more cases */
        private long cutSep2D;
        /** the number of cuts in X and Y */
        private int cutsX, cutsY;
        /** the total number of cuts */
        private int cutsTotal;
        /** the "reasonable" number of cuts (around the outside only) */
        private int cutsReasonable;
        /** the X coordinate of the leftmost cut's center */
        private long cutBaseX;
        /** the Y coordinate of the topmost cut's center */
        private long cutBaseY;
        /** the lowest X cut that will be shifted to the left */
        private long cutShiftLeftXPos;
        /** the lowest X cut that will be shifted to the right */
        private long cutShiftRightXPos;
        /** the X cut that will not be shifted (because it is the center cut) */
        private long cutShiftNoneXPos;
        /** the lowest Y cut that will be shifted down */
        private long cutShiftDownYPos;
        /** the lowest Y cut that will be shifted up */
        private long cutShiftUpYPos;
        /** the Y cut that will not be shifted (because it is the center cut) */
        private long cutShiftNoneYPos;
        /** the amount X cuts will be shifted to the left */
        private long cutShiftLeftXAmt;
        /** the amount X cuts will be shifted to the right */
        private long cutShiftRightXAmt;
        /** the amount Y cuts will be shifted down */
        private long cutShiftDownYAmt;
        /** the amount Y cuts will be shifted up */
        private long cutShiftUpYAmt;
        /** cut position of last top-edge cut (for interior-cut elimination) */
        private double cutTopEdge;
        /** cut position of last left-edge cut  (for interior-cut elimination) */
        private double cutLeftEdge;
        /** cut position of last right-edge cut  (for interior-cut elimination) */
        private double cutRightEdge;

        /**
         * Constructor to initialize for multiple cuts.
         */
        private MultiCutData(ImmutableNodeInst niD, Technology.NodeLayer cutLayer) {
            calculateInternalData(niD, cutLayer);
        }

        /**
         * Constructor to initialize for multiple cuts.
         * @param niD the NodeInst with multiple cuts.
         */
        private MultiCutData(ImmutableNodeInst niD, TechPool techPool) {
            calculateInternalData(niD, techPool.getPrimitiveNode((PrimitiveNodeId) niD.protoId).findMulticut());
        }

        private void calculateInternalData(ImmutableNodeInst niD, Technology.NodeLayer cutLayer) {
            assert cutLayer.getRepresentation() == Technology.NodeLayer.MULTICUTBOX;
            Technology.TechPoint[] techPoints = cutLayer.getPoints();
            long lx = techPoints[0].getX().getGridValue(niD.size);
            long hx = techPoints[1].getX().getGridValue(niD.size);
            long ly = techPoints[0].getY().getGridValue(niD.size);
            long hy = techPoints[1].getY().getGridValue(niD.size);
            cutSizeX = cutLayer.getMulticutSizeX().getGrid();
            cutSizeY = cutLayer.getMulticutSizeY().getGrid();
            cutSep1D = cutLayer.getMulticutSep1D().getGrid();
            cutSep2D = cutLayer.getMulticutSep2D().getGrid();
            if (!niD.isEasyShape()) {
                // get the value of the cut spacing
                Variable var = niD.getVar(Technology.NodeLayer.CUT_SPACING);
                if (var != null) {
                    double spacingD = VarContext.objectToDouble(var.getObject(), -1);
                    if (spacingD != -1) {
                        cutSep1D = cutSep2D = DBMath.lambdaToGrid(spacingD);
                    }
                }
            }

            // determine the actual node size
            cutBaseX = (lx + hx) >> 1;
            cutBaseY = (ly + hy) >> 1;
            long cutAreaWidth = hx - lx;
            long cutAreaHeight = hy - ly;

            // number of cuts depends on the size of cut area
            // It is always allowed 1 since it should be valid by construction.
            int oneDcutsX = getNumCutsAlong(cutAreaWidth, cutSizeX, cutSep1D);
            int oneDcutsY = getNumCutsAlong(cutAreaHeight, cutSizeY, cutSep1D);
            
            // check if configuration gives 2D cuts
            cutSep = cutSep1D;
            cutsX = oneDcutsX;
            cutsY = oneDcutsY;
            if (cutsX > 1 && cutsY > 1) {
                // recompute number of cuts for 2D spacing
                int twoDcutsX= getNumCutsAlong(cutAreaWidth, cutSizeX, cutSep2D);
                int twoDcutsY = getNumCutsAlong(cutAreaHeight, cutSizeY, cutSep2D);
                
                cutSep = cutSep2D;
                cutsX = twoDcutsX;
                cutsY = twoDcutsY;
                
                if (cutsX == 1 || cutsY == 1) {
                    // 1D separation sees a 2D grid, but 2D separation sees a linear array: use 1D linear settings
                    cutSep = cutSep1D;
                    if (cutAreaWidth > cutAreaHeight) {
                        cutsX = oneDcutsX;
                    } else {
                        cutsY = oneDcutsY;
                    }
                }
            }
            if (cutsX <= 0) {
                cutsX = 1;
            }
            if (cutsY <= 0) {
                cutsY = 1;
            }

            // compute spacing rules
            cutShiftLeftXPos = cutsX;
            cutShiftRightXPos = cutsX;
            cutShiftDownYPos = cutsY;
            cutShiftUpYPos = cutsY;
            cutShiftNoneXPos = -1;
            cutShiftNoneYPos = -1;
            if (!niD.isEasyShape()) {
                Integer cutAlignment = niD.getVarValue(Technology.NodeLayer.CUT_ALIGNMENT, Integer.class);
                if (cutAlignment != null) {
                    if (cutAlignment.intValue() == Technology.NodeLayer.MULTICUT_SPREAD) {
                        // spread cuts to edge, leaving gap in center
                        cutShiftLeftXPos = 0;
                        cutShiftDownYPos = 0;
                        cutShiftLeftXAmt = (1 - cutsX) * (cutSizeX + cutSep) / 2 - lx;
                        cutShiftDownYAmt = (1 - cutsY) * (cutSizeY + cutSep) / 2 - ly;

                        cutShiftRightXPos = cutsX / 2;
                        cutShiftUpYPos = cutsY / 2;
                        cutShiftRightXAmt = hx - (cutsX - 1) * (cutSizeX + cutSep) / 2;
                        cutShiftUpYAmt = hy - (cutsY - 1) * (cutSizeY + cutSep) / 2;
                        if ((cutsX & 1) != 0) {
                            cutShiftNoneXPos = cutsX / 2;
                        }
                        if ((cutsY & 1) != 0) {
                            cutShiftNoneYPos = cutsY / 2;
                        }
                    } else if (cutAlignment.intValue() == Technology.NodeLayer.MULTICUT_CORNER) {
                        // shift cuts to lower edge
                        cutShiftLeftXPos = 0;
                        cutShiftDownYPos = 0;
                        cutShiftLeftXAmt = (1 - cutsX) * (cutSizeX + cutSep) / 2 - lx;
                        cutShiftDownYAmt = (1 - cutsY) * (cutSizeY + cutSep) / 2 - ly;
                    }
                }
            }

            cutsReasonable = cutsTotal = cutsX * cutsY;
            if (cutsTotal != 1) {
                // prepare for the multiple contact cut locations
                if (cutsX > 2 && cutsY > 2) {
                    cutsReasonable = cutsX * 2 + (cutsY - 2) * 2;
                    cutTopEdge = cutsX * 2;
                    cutLeftEdge = cutsX * 2 + cutsY - 2;
                    cutRightEdge = cutsX * 2 + (cutsY - 2) * 2;
                }
            }
        }

        private int getNumCutsAlong(long cutArea, long cutSize, long cutSep)
        {
            int cuts = 1 + (int) (cutArea / (cutSize + cutSep));
            // checking if remainder is bigger that cutSizeX/Y
//            long reminder = cutArea % (cutSize + cutSep);
//            // if not enough -> one less
//            if (reminder < cutSize)
//            	cuts--;
            if (cuts == 0 && cutArea == 0)
            	cuts = 1; // force one at least 
            return cuts;
        }

        /**
         * Method to fill in the contact cuts based on anchor information.
         */
        private void fillCutPoly(int cut, Poly.Type style, Layer layer, PrimitivePort pp) {
            long cX = cutBaseX;
            long cY = cutBaseY;
            if (cutsX > 1 || cutsY > 1) {
                if (cutsX > 2 && cutsY > 2) {
                    // rearrange cuts so that the initial ones go around the outside
                    if (cut < cutsX) {
                        // bottom edge: it's ok as is
                    } else if (cut < cutTopEdge) {
                        // top edge: shift up
                        cut += cutsX * (cutsY - 2);
                    } else if (cut < cutLeftEdge) {
                        // left edge: rearrange
                        cut = (int) ((cut - cutTopEdge) * cutsX + cutsX);
                    } else if (cut < cutRightEdge) {
                        // right edge: rearrange
                        cut = (int) ((cut - cutLeftEdge) * cutsX + cutsX * 2 - 1);
                    } else {
                        // center: rearrange and scale down
                        cut = cut - (int) cutRightEdge;
                        int cutx = cut % (cutsX - 2);
                        int cuty = cut / (cutsX - 2);
                        cut = cuty * cutsX + cutx + cutsX + 1;
                    }
                }

                // locate the X center of the cut
                if (cutsX != 1) {
                    int cutNum = cut % cutsX;
                    cX += (cutNum * 2 - (cutsX - 1)) * (cutSizeX + cutSep) * 0.5;
                    if (cutNum != cutShiftNoneXPos) {
                        if (cutNum >= cutShiftRightXPos) {
                            cX += cutShiftRightXAmt;
                        } else if (cutNum >= cutShiftLeftXPos) {
                            cX -= cutShiftLeftXAmt;
                        }
                    }
                }

                // locate the Y center of the cut
                if (cutsY != 1) {
                    int cutNum = cut / cutsX;
                    cY += (cutNum * 2 - (cutsY - 1)) * (cutSizeY + cutSep) * 0.5;
                    if (cutNum != cutShiftNoneYPos) {
                        if (cutNum >= cutShiftUpYPos) {
                            cY += cutShiftUpYAmt;
                        } else if (cutNum >= cutShiftDownYPos) {
                            cY -= cutShiftDownYAmt;
                        }
                    }
                }
            }
            long lX = (cX - (cutSizeX >> 1)) << FixpCoord.FRACTION_BITS;
            long hX = (cX + (cutSizeX >> 1)) << FixpCoord.FRACTION_BITS;
            long lY = (cY - (cutSizeY >> 1)) << FixpCoord.FRACTION_BITS;
            long hY = (cY + (cutSizeY >> 1)) << FixpCoord.FRACTION_BITS;
            pushPoint(lX, lY);
            pushPoint(hX, lY);
            pushPoint(hX, hY);
            pushPoint(lX, hY);
            pushPoly(style, layer, null, pp);
        }
    }

    SerpentineTrans newSerpentineTrans(ImmutableNodeInst niD, PrimitiveNode protoType, Technology.NodeLayer[] pLayers) {
        return new SerpentineTrans(niD, protoType, pLayers);
    }

    /**
     * Class SerpentineTrans here.
     */
    class SerpentineTrans {

        private static final int LEFTANGLE = 900;
        private static final int RIGHTANGLE = 2700;
//        /** the ImmutableNodeInst that is this serpentine transistor */
//        private ImmutableNodeInst theNode;
        /** the prototype of this serpentine transistor */
        private PrimitiveNode theProto;
        /** the number of polygons that make up this serpentine transistor */
        int layersTotal;
        /** the number of segments in this serpentine transistor */
        private int numSegments;
        /** the extra gate width of this serpentine transistor */
        private double extraScale;
        /** the node layers that make up this serpentine transistor */
        private Technology.NodeLayer[] primLayers;
        /** the gate coordinates for this serpentine transistor */
        private EPoint[] points;
        /** the defining values for this serpentine transistor */
        private double[] specialValues;
        /** true if there are separate field and gate polys */
        private boolean fieldPolyOnEndsOnly;
        /** counter for filling the polygons of the serpentine transistor */
        private int fillBox;

        /**
         * Constructor throws initialize for a serpentine transistor.
         * @param niD the NodeInst with a serpentine transistor.
         */
        private SerpentineTrans(ImmutableNodeInst niD, PrimitiveNode protoType, Technology.NodeLayer[] pLayers) {
//            theNode = niD;
            layersTotal = 0;
            points = niD.getTrace();
            if (points != null) {
                if (points.length < 2) {
                    points = null;
                }
            }
            if (points != null) {
                theProto = protoType;
                specialValues = theProto.getSpecialValues();
                primLayers = pLayers;
                int count = primLayers.length;
                numSegments = points.length - 1;
                layersTotal = count;
//				layersTotal = count * numSegments;

                extraScale = 0;
                double length = niD.getSerpentineTransistorLength();
                if (length > 0) {
                    extraScale = (length - specialValues[3]) / 2;
                }

                // see if there are separate field and gate poly layers
                fieldPolyOnEndsOnly = false;
                int numFieldPoly = 0, numGatePoly = 0;
                for (int i = 0; i < count; i++) {
                    if (primLayers[i].getLayer().getFunction().isPoly()) {
                        if (primLayers[i].getLayer().getFunction() == Layer.Function.GATE) {
                            numGatePoly++;
                        } else {
                            numFieldPoly++;
                        }
                    }
                }
                if (numFieldPoly > 0 && numGatePoly > 0) {
                    // when there are both field and gate poly elements, use field poly only on the ends
                    fieldPolyOnEndsOnly = true;
//					layersTotal = (count-numFieldPoly) * numSegments + numFieldPoly;
                }
            }
        }

        /**
         * Method to tell whether this SerpentineTrans object has valid outline information.
         * @return true if the data exists.
         */
        boolean hasValidData() {
            return points != null;
        }

        /**
         * Method to start the filling of polygons in the serpentine transistor.
         * Call this before repeated calls to "fillTransPoly".
         */
        void initTransPolyFilling() {
            fillBox = 0;
        }

        /**
         * Method to describe a box of a serpentine transistor.
         * If the variable "trace" exists on the node, get that
         * x/y/x/y information as the centerline of the serpentine path.  The outline is
         * placed in the polygon "poly".
         * NOTE: For each trace segment, the left hand side of the trace
         * will contain the polygons that appear ABOVE the gate in the node
         * definition. That is, the "top" port and diffusion will be above a
         * gate segment that extends from left to right, and on the left of a
         * segment that goes from bottom to top.
         */
        void fillTransPoly() {
            int element = fillBox++;
            Technology.NodeLayer primLayer = primLayers[element];
            Layer layer = primLayer.getLayer();
            if (skipLayer(layer)) {
                return;
            }
            double extendt = primLayer.getSerpentineExtentT().getLambda();
            double extendb = primLayer.getSerpentineExtentB().getLambda();

            // if field poly appears only on the ends of the transistor, ignore interior requests
            boolean extendEnds = true;
            if (fieldPolyOnEndsOnly) {
                if (layer.getFunction().isPoly()) {
                    if (layer.getFunction() == Layer.Function.GATE) {
                        // found the gate poly: do not extend it
                        extendEnds = false;
                    } else {
                        // found piece of field poly
                        if (extendt != 0) {
                            // first endcap: extend "thissg" 180 degrees back
                            int thissg = 0;
                            int nextsg = 1;
                            Point2D thisPt = points[thissg];
                            Point2D nextPt = points[nextsg];
                            int angle = DBMath.figureAngle(thisPt, nextPt);
                            nextPt = thisPt;
                            int ang = angle + 1800;
                            thisPt = DBMath.addPoints(thisPt, DBMath.cos(ang) * extendt, DBMath.sin(ang) * extendt);
                            buildSerpentinePoly(element, 0, numSegments, thisPt, nextPt, angle);
                            return;
                        } else if (extendb != 0) {
                            // last endcap: extend "next" 0 degrees forward
                            int thissg = numSegments - 1;
                            int nextsg = numSegments;
                            Point2D thisPt = points[thissg];
                            Point2D nextPt = points[nextsg];
                            int angle = DBMath.figureAngle(thisPt, nextPt);
                            thisPt = nextPt;
                            nextPt = DBMath.addPoints(nextPt, DBMath.cos(angle) * extendb, DBMath.sin(angle) * extendb);
                            buildSerpentinePoly(element, 0, numSegments, thisPt, nextPt, angle);
                            return;
                        }
                    }
                }
            }

            // fill the polygon
            Point2D[] outPoints = new Point2D.Double[(numSegments + 1) * 2];
            for (int segment = 0; segment < numSegments; segment++) {
                int thissg = segment;
                int nextsg = segment + 1;
                Point2D thisPt = points[thissg];
                Point2D nextPt = points[nextsg];
                int angle = DBMath.figureAngle(thisPt, nextPt);
                if (extendEnds) {
                    if (thissg == 0) {
                        // extend "thissg" 180 degrees back
                        int ang = angle + 1800;
                        thisPt = DBMath.addPoints(thisPt, DBMath.cos(ang) * extendt, DBMath.sin(ang) * extendt);
                    }
                    if (nextsg == numSegments) {
                        // extend "next" 0 degrees forward
                        nextPt = DBMath.addPoints(nextPt, DBMath.cos(angle) * extendb, DBMath.sin(angle) * extendb);
                    }
                }

                // see if nonstandard width is specified
                double lwid = primLayer.getSerpentineLWidth().getLambda();
                double rwid = primLayer.getSerpentineRWidth().getLambda();
                lwid += extraScale;
                rwid += extraScale;

                // compute endpoints of line parallel to and left of center line
                int ang = angle + LEFTANGLE;
                double sin = DBMath.sin(ang) * lwid;
                double cos = DBMath.cos(ang) * lwid;
                Point2D thisL = DBMath.addPoints(thisPt, cos, sin);
                Point2D nextL = DBMath.addPoints(nextPt, cos, sin);

                // compute endpoints of line parallel to and right of center line
                ang = angle + RIGHTANGLE;
                sin = DBMath.sin(ang) * rwid;
                cos = DBMath.cos(ang) * rwid;
                Point2D thisR = DBMath.addPoints(thisPt, cos, sin);
                Point2D nextR = DBMath.addPoints(nextPt, cos, sin);

                // determine proper intersection of this and the previous segment
                if (thissg != 0) {
                    Point2D otherPt = points[thissg - 1];
                    int otherang = DBMath.figureAngle(otherPt, thisPt);
                    if (otherang != angle) {
                        ang = otherang + LEFTANGLE;
                        thisL = DBMath.intersect(DBMath.addPoints(thisPt, DBMath.cos(ang) * lwid, DBMath.sin(ang) * lwid),
                                otherang, thisL, angle);
                        ang = otherang + RIGHTANGLE;
                        thisR = DBMath.intersect(DBMath.addPoints(thisPt, DBMath.cos(ang) * rwid, DBMath.sin(ang) * rwid),
                                otherang, thisR, angle);
                    }
                }

                // determine proper intersection of this and the next segment
                if (nextsg != numSegments) {
                    Point2D otherPt = points[nextsg + 1];
                    int otherang = DBMath.figureAngle(nextPt, otherPt);
                    if (otherang != angle) {
                        ang = otherang + LEFTANGLE;
                        Point2D newPtL = DBMath.addPoints(nextPt, DBMath.cos(ang) * lwid, DBMath.sin(ang) * lwid);
                        nextL = DBMath.intersect(newPtL, otherang, nextL, angle);
                        ang = otherang + RIGHTANGLE;
                        Point2D newPtR = DBMath.addPoints(nextPt, DBMath.cos(ang) * rwid, DBMath.sin(ang) * rwid);
                        nextR = DBMath.intersect(newPtR, otherang, nextR, angle);
                    }
                }

                // fill the polygon
                if (segment == 0) {
                    // fill in the first two points
                    outPoints[0] = thisL;
                    outPoints[1] = nextL;
                    outPoints[(numSegments + 1) * 2 - 2] = nextR;
                    outPoints[(numSegments + 1) * 2 - 1] = thisR;
                } else {
                    outPoints[segment + 1] = nextL;
                    outPoints[(numSegments + 1) * 2 - 2 - segment] = nextR;
                }
            }

            for (Point2D point : outPoints) {
                pushPoint(FixpCoord.lambdaToFixp(point.getX()), FixpCoord.lambdaToFixp(point.getY()));
            }
            pushPoly(primLayer.getStyle(), layer, null, primLayer.getPort(theProto));
        }

        private void buildSerpentinePoly(int element, int thissg, int nextsg, Point2D thisPt, Point2D nextPt, int angle) {
            // see if nonstandard width is specified
            Technology.NodeLayer primLayer = primLayers[element];
            double lwid = primLayer.getSerpentineLWidth().getLambda();
            double rwid = primLayer.getSerpentineRWidth().getLambda();
            lwid += extraScale;
            rwid += extraScale;

            // compute endpoints of line parallel to and left of center line
            int ang = angle + LEFTANGLE;
            double sin = DBMath.sin(ang) * lwid;
            double cos = DBMath.cos(ang) * lwid;
            Point2D thisL = DBMath.addPoints(thisPt, cos, sin);
            Point2D nextL = DBMath.addPoints(nextPt, cos, sin);

            // compute endpoints of line parallel to and right of center line
            ang = angle + RIGHTANGLE;
            sin = DBMath.sin(ang) * rwid;
            cos = DBMath.cos(ang) * rwid;
            Point2D thisR = DBMath.addPoints(thisPt, cos, sin);
            Point2D nextR = DBMath.addPoints(nextPt, cos, sin);

            // determine proper intersection of this and the previous segment
            if (thissg != 0) {
                Point2D otherPt = points[thissg - 1];
                int otherang = DBMath.figureAngle(otherPt, thisPt);
                if (otherang != angle) {
                    ang = otherang + LEFTANGLE;
                    thisL = DBMath.intersect(DBMath.addPoints(thisPt, DBMath.cos(ang) * lwid, DBMath.sin(ang) * lwid),
                            otherang, thisL, angle);
                    ang = otherang + RIGHTANGLE;
                    thisR = DBMath.intersect(DBMath.addPoints(thisPt, DBMath.cos(ang) * rwid, DBMath.sin(ang) * rwid),
                            otherang, thisR, angle);
                }
            }

            // determine proper intersection of this and the next segment
            if (nextsg != numSegments) {
                Point2D otherPt = points[nextsg + 1];
                int otherang = DBMath.figureAngle(nextPt, otherPt);
                if (otherang != angle) {
                    ang = otherang + LEFTANGLE;
                    Point2D newPtL = DBMath.addPoints(nextPt, DBMath.cos(ang) * lwid, DBMath.sin(ang) * lwid);
                    nextL = DBMath.intersect(newPtL, otherang, nextL, angle);
                    ang = otherang + RIGHTANGLE;
                    Point2D newPtR = DBMath.addPoints(nextPt, DBMath.cos(ang) * rwid, DBMath.sin(ang) * rwid);
                    nextR = DBMath.intersect(newPtR, otherang, nextR, angle);
                }
            }

            // fill the polygon
            pushPoint(FixpCoord.lambdaToFixp(thisL.getX()), FixpCoord.lambdaToFixp(thisL.getY()));
            pushPoint(FixpCoord.lambdaToFixp(thisR.getX()), FixpCoord.lambdaToFixp(thisR.getY()));
            pushPoint(FixpCoord.lambdaToFixp(nextR.getX()), FixpCoord.lambdaToFixp(nextR.getY()));
            pushPoint(FixpCoord.lambdaToFixp(nextL.getX()), FixpCoord.lambdaToFixp(nextL.getY()));
            pushPoly(primLayer.getStyle(), primLayer.getLayer(), null, primLayer.getPort(theProto));
        }

        /**
         * Method to describe a port in a transistor that is part of a serpentine path.
         * The port path is shrunk by "diffInset" in the length and is pushed "diffExtend" from the centerline.
         * The default width of the transistor is "defWid".
         * The assumptions about directions are:
         * Segments have port 1 to the left, and port 3 to the right of the gate trace.
         * Port 0, the "left-hand" end of the gate, appears at the starting
         * end of the first trace segment; port 2, the "right-hand" end of the gate,
         * appears at the end of the last trace segment.  Port 3 is drawn as a
         * reflection of port 1 around the trace.
         * The poly ports are extended "polyExtend" beyond the appropriate end of the trace
         * and are inset by "polyInset" from the polysilicon edge.
         * The diffusion ports are extended "diffExtend" from the polysilicon edge
         * and set in "diffInset" from the ends of the trace segment.
         */
        void fillTransPort(PortProto pp) {
            double diffInset = specialValues[1];
            double diffExtend = specialValues[2];
            double defWid = specialValues[3] + extraScale;
            double polyInset = specialValues[4];
            double polyExtend = specialValues[5];

            // prepare to fill the serpentine transistor port
            int total = points.length;

            // determine which port is being described
            int which = 0;
            for (Iterator<PortProto> it = theProto.getPorts(); it.hasNext();) {
                PortProto lpp = it.next();
                if (lpp == pp) {
                    break;
                }
                which++;
            }
            assert which == pp.getPortIndex();

            // ports 0 and 2 are poly (simple)
            if (which == 0) {
                Point2D thisPt = new Point2D.Double(points[0].getX(), points[0].getY());
                Point2D nextPt = new Point2D.Double(points[1].getX(), points[1].getY());
                int angle = DBMath.figureAngle(thisPt, nextPt);
                int ang = (angle + 1800) % 3600;
                thisPt.setLocation(thisPt.getX() + DBMath.cos(ang) * polyExtend,
                        thisPt.getY() + DBMath.sin(ang) * polyExtend);

                ang = (angle + LEFTANGLE) % 3600;
                Point2D end1 = new Point2D.Double(thisPt.getX() + DBMath.cos(ang) * (defWid / 2 - polyInset),
                        thisPt.getY() + DBMath.sin(ang) * (defWid / 2 - polyInset));

                ang = (angle + RIGHTANGLE) % 3600;
                Point2D end2 = new Point2D.Double(thisPt.getX() + DBMath.cos(ang) * (defWid / 2 - polyInset),
                        thisPt.getY() + DBMath.sin(ang) * (defWid / 2 - polyInset));

                pushPoint(FixpCoord.lambdaToFixp(end1.getX()), FixpCoord.lambdaToFixp(end1.getY()));
                pushPoint(FixpCoord.lambdaToFixp(end2.getX()), FixpCoord.lambdaToFixp(end2.getY()));
                pushPoly(Poly.Type.OPENED, null, null, null);
                return;
//				Point2D [] portPoints = new Point2D.Double[2];
//				portPoints[0] = end1;
//				portPoints[1] = end2;
//				trans.transform(portPoints, 0, portPoints, 0, 2);
//				Poly retPoly = new Poly(portPoints);
//				retPoly.setStyle(Poly.Type.OPENED);
//				return retPoly;
            }
            if (which == 2) {
                Point2D thisPt = new Point2D.Double(points[total - 1].getX(), points[total - 1].getY());
                Point2D nextPt = new Point2D.Double(points[total - 2].getX(), points[total - 2].getY());
                int angle = DBMath.figureAngle(thisPt, nextPt);
                int ang = (angle + 1800) % 3600;
                thisPt.setLocation(thisPt.getX() + DBMath.cos(ang) * polyExtend,
                        thisPt.getY() + DBMath.sin(ang) * polyExtend);

                ang = (angle + LEFTANGLE) % 3600;
                Point2D end1 = new Point2D.Double(thisPt.getX() + DBMath.cos(ang) * (defWid / 2 - polyInset),
                        thisPt.getY() + DBMath.sin(ang) * (defWid / 2 - polyInset));

                ang = (angle + RIGHTANGLE) % 3600;
                Point2D end2 = new Point2D.Double(thisPt.getX() + DBMath.cos(ang) * (defWid / 2 - polyInset),
                        thisPt.getY() + DBMath.sin(ang) * (defWid / 2 - polyInset));

                pushPoint(FixpCoord.lambdaToFixp(end1.getX()), FixpCoord.lambdaToFixp(end1.getY()));
                pushPoint(FixpCoord.lambdaToFixp(end2.getX()), FixpCoord.lambdaToFixp(end2.getY()));
                pushPoly(Poly.Type.OPENED, null, null, null);
                return;
//				Point2D [] portPoints = new Point2D.Double[2];
//				portPoints[0] = end1;
//				portPoints[1] = end2;
//				trans.transform(portPoints, 0, portPoints, 0, 2);
//				Poly retPoly = new Poly(portPoints);
//				retPoly.setStyle(Poly.Type.OPENED);
//				return retPoly;
            }

            // port 3 is the negated path side of port 1
            if (which == 3) {
                diffExtend = -diffExtend;
                defWid = -defWid;
            }

            // extra port on some n-transistors
            if (which == 4) {
                diffExtend = defWid = 0;
            }

            Point2D[] portPoints = new Point2D.Double[total];
            Point2D lastPoint = null;
            int lastAngle = 0;
            for (int nextIndex = 1; nextIndex < total; nextIndex++) {
                int thisIndex = nextIndex - 1;
                Point2D thisPt = new Point2D.Double(points[thisIndex].getX(), points[thisIndex].getY());
                Point2D nextPt = new Point2D.Double(points[nextIndex].getX(), points[nextIndex].getY());
                int angle = DBMath.figureAngle(thisPt, nextPt);

                // determine the points
                if (thisIndex == 0) {
                    // extend "this" 0 degrees forward
                    thisPt.setLocation(thisPt.getX() + DBMath.cos(angle) * diffInset,
                            thisPt.getY() + DBMath.sin(angle) * diffInset);
                }
                if (nextIndex == total - 1) {
                    // extend "next" 180 degrees back
                    int backAng = (angle + 1800) % 3600;
                    nextPt.setLocation(nextPt.getX() + DBMath.cos(backAng) * diffInset,
                            nextPt.getY() + DBMath.sin(backAng) * diffInset);
                }

                // compute endpoints of line parallel to center line
                int ang = (angle + LEFTANGLE) % 3600;
                double sine = DBMath.sin(ang);
                double cosine = DBMath.cos(ang);
                thisPt.setLocation(thisPt.getX() + cosine * (defWid / 2 + diffExtend),
                        thisPt.getY() + sine * (defWid / 2 + diffExtend));
                nextPt.setLocation(nextPt.getX() + cosine * (defWid / 2 + diffExtend),
                        nextPt.getY() + sine * (defWid / 2 + diffExtend));

                if (thisIndex != 0) {
                    // compute intersection of this and previous line
                    thisPt = DBMath.intersect(lastPoint, lastAngle, thisPt, angle);
                }
                portPoints[thisIndex] = thisPt;
                lastPoint = thisPt;
                lastAngle = angle;
                if (nextIndex == total - 1) {
                    portPoints[nextIndex] = nextPt;
                }
            }
            for (Point2D point : portPoints) {
                pushPoint(FixpCoord.lambdaToFixp(point.getX()), FixpCoord.lambdaToFixp(point.getY()));
            }
            pushPoly(Poly.Type.OPENED, null, null, null);
//			if (total > 0)
//				trans.transform(portPoints, 0, portPoints, 0, total);
//			Poly retPoly = new Poly(portPoints);
//			retPoly.setStyle(Poly.Type.OPENED);
//			return retPoly;
        }
    }
}
