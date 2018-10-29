/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: VectorCache.java
 * Written by Dmitry Nadezhin.
 *
 * Copyright (c) 2010, Static Free Software. All rights reserved.
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
package com.sun.electric.database.geometry.bool;

import com.sun.electric.database.CellBackup;
import com.sun.electric.database.CellRevision;
import com.sun.electric.database.ImmutableArcInst;
import com.sun.electric.database.ImmutableExport;
import com.sun.electric.database.ImmutableIconInst;
import com.sun.electric.database.ImmutableNodeInst;
import com.sun.electric.database.Snapshot;
import com.sun.electric.database.geometry.EGraphics;
import com.sun.electric.database.geometry.ERectangle;
import com.sun.electric.database.geometry.Poly;
import com.sun.electric.database.id.CellId;
import com.sun.electric.database.id.PrimitiveNodeId;
import com.sun.electric.database.id.PrimitivePortId;
import com.sun.electric.database.id.TechId;
import com.sun.electric.database.variable.TextDescriptor;
import com.sun.electric.database.variable.Variable;
import com.sun.electric.technology.AbstractShapeBuilder;
import com.sun.electric.technology.Layer;
import com.sun.electric.technology.PrimitiveNode;
import com.sun.electric.technology.PrimitivePort;
import com.sun.electric.technology.TechPool;
import com.sun.electric.technology.Technology;
import com.sun.electric.util.math.FixpCoord;
import com.sun.electric.util.math.Orientation;

import java.awt.Point;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 *
 */
public class VectorCache {

    private static boolean DEBUG = false;
    private static final boolean USE_ELECTRICAL = false;
    private static final boolean WIPE_PINS = true;
    private Set<Layer> layers = new TreeSet<Layer>();
    private Set<Layer> badLayers = new HashSet<Layer>();
    private final Snapshot snapshot;
    private final TechPool techPool;
    private HashMap<CellId, MyVectorCell> cells = new HashMap<CellId, MyVectorCell>();
    private final PrimitivePortId busPinPortId;
    private final PrimitiveNodeId cellCenterId;
    private final PrimitiveNodeId essentialBoundsId;
    /** local shape builder */
    private final ShapeBuilder shapeBuilder = new ShapeBuilder();
    /** List of VectorManhattanBuilders */
    private final HashMap<Layer, VectorManhattanBuilder> boxBuilders = new HashMap<Layer, VectorManhattanBuilder>();
    private static final int[] NULL_INT_ARRAY = {};

    private static class CellLayer {

        final Layer layer;
        int[] boxCoords = NULL_INT_ARRAY;
        ERectangle localBounds;
        ArrayList<MyVectorPolygon> polys = new ArrayList<MyVectorPolygon>();

        private CellLayer(Layer layer) {
            this.layer = layer;
        }

        private void setBoxCoords(int[] boxCoords) {
            this.boxCoords = boxCoords;
            int lX = Integer.MAX_VALUE, lY = Integer.MAX_VALUE, hX = Integer.MIN_VALUE, hY = Integer.MIN_VALUE;
            for (int i = 0; i < boxCoords.length; i += 4) {
                lX = Math.min(lX, boxCoords[i + 0]);
                lY = Math.min(lY, boxCoords[i + 1]);
                hX = Math.max(hX, boxCoords[i + 2]);
                hY = Math.max(hY, boxCoords[i + 3]);
            }
            localBounds = ERectangle.fromGrid(lX, hY, hX - (long) lX, hY - (long) lY);
        }
    }

    private class MyVectorCell {

        private final TechId techId;
        private final TreeMap<Layer, CellLayer> layers = new TreeMap<Layer, CellLayer>();
        private final ArrayList<ImmutableNodeInst> subCells = new ArrayList<ImmutableNodeInst>();
        private final List<ImmutableNodeInst> unmodifiebleSubCells = Collections.unmodifiableList(subCells);

        MyVectorCell(CellId cellId) {
            CellBackup cellBackup = snapshot.getCell(cellId);
            techId = cellBackup.cellRevision.d.techId;
            Technology tech = techPool.getTech(techId);
            if (isCellParameterized(cellBackup.cellRevision)) {
                throw new IllegalArgumentException();
            }

            long startTime = DEBUG ? System.currentTimeMillis() : 0;

            for (VectorManhattanBuilder b : boxBuilders.values()) {
                b.clear();
            }
            // draw all arcs
            shapeBuilder.setup(cellBackup, Orientation.IDENT, USE_ELECTRICAL, WIPE_PINS, false, null);
            shapeBuilder.polyLayer = null;
            for (Layer layer : tech.getLayersSortedByRule(Layer.LayerSortingType.ByHeight)) {
                if (layer.getFunction() == Layer.Function.POLY1) {
                    shapeBuilder.polyLayer = layer;
                }
            }
            for (ImmutableArcInst a : cellBackup.cellRevision.arcs) {
                shapeBuilder.genShapeOfArc(a);
            }

            // draw all primitive nodes
            for (ImmutableNodeInst n : cellBackup.cellRevision.nodes) {
                if (n.protoId instanceof CellId) {
                    if (!n.orient.isManhattan()) {
                        throw new IllegalArgumentException();
                    }
                    subCells.add(n);
                } else {
                    boolean hideOnLowLevel = n.is(ImmutableNodeInst.VIS_INSIDE) || n.protoId == cellCenterId
                            || n.protoId == essentialBoundsId;
                    if (!hideOnLowLevel) {
                        PrimitiveNode pn = techPool.getPrimitiveNode((PrimitiveNodeId) n.protoId);
                        pn.genShape(shapeBuilder, n);
                    }
                }
            }

            addBoxesFromBuilder(this, boxBuilders);

            if (DEBUG) {
                long stopTime = System.currentTimeMillis();
                System.out.println((stopTime - startTime) + " init " + cellBackup.cellRevision.d.cellId);
            }
        }

        private CellLayer getCellLayer(Layer layer) {
            CellLayer cellLayer = layers.get(layer);
            if (cellLayer == null) {
                cellLayer = new CellLayer(layer);
                layers.put(layer, cellLayer);
            }
            return cellLayer;
        }
    }

    public Collection<Layer> getLayers() {
        return new ArrayList<Layer>(layers);
    }

    public Collection<Layer> getBadLayers() {
        return new ArrayList<Layer>(badLayers);
    }

    public boolean isBadLayer(Layer layer) {
        return badLayers.contains(layer);
    }

    public VectorCache(Snapshot snapshot) {
        this.snapshot = snapshot;
        techPool = snapshot.getTechPool();
        busPinPortId = techPool.getSchematics().busPinNode.getPort(0).getId();
        cellCenterId = techPool.getGeneric().cellCenterNode.getId();
        essentialBoundsId = techPool.getGeneric().essentialBoundsNode.getId();
    }

    public void scanLayers(CellId topCellId) {
        HashSet<CellId> visited = new HashSet<CellId>();
        scanLayers(topCellId, visited);
    }

    public List<ImmutableNodeInst> getSubcells(CellId cellId) {
        return findVectorCell(cellId).unmodifiebleSubCells;
    }

    public int getNumBoxes(CellId cellId, Layer layer) {
        CellLayer cellLayer = findVectorCell(cellId).layers.get(layer);
        return cellLayer != null ? cellLayer.boxCoords.length / 4 : 0;
    }

    public int getNumFlatBoxes(CellId cellId, Layer layer) {
        return getNumFlatBoxes(cellId, layer, new HashMap<CellId, Integer>());
    }

    public ERectangle getLocalBounds(CellId cellId, Layer layer) {
        CellLayer cellLayer = findVectorCell(cellId).layers.get(layer);
        return cellLayer != null ? cellLayer.localBounds : null;
    }

    public void getBoxes(CellId cellId, Layer layer, int offset, int size, int[] result) {
        CellLayer cellLayer = findVectorCell(cellId).layers.get(layer);
        if (cellLayer == null || offset < 0 || size < 0 || offset + size > cellLayer.boxCoords.length / 4 || size > result.length / 4) {
            throw new IndexOutOfBoundsException();
        }
        System.arraycopy(cellLayer.boxCoords, offset * 4, result, 0, size * 4);
    }

    public static interface PutRectangle {

        public void put(int lx, int ly, int hx, int hy);
    }

    public void collectLayer(Layer layer, CellId cellId, boolean rotate, PutRectangle putRectangle) {
        Orientation orient = (rotate ? Orientation.XR : Orientation.IDENT).canonic();
        collectLayer(layer, findVectorCell(cellId), new Point(0, 0), orient, putRectangle);
    }

    private void scanLayers(CellId cellId, HashSet<CellId> visited) {
        if (!visited.add(cellId)) {
            return;
        }
        MyVectorCell mvc = findVectorCell(cellId);
        for (ImmutableNodeInst n : mvc.subCells) {
            scanLayers((CellId) n.protoId, visited);
        }
    }

    private MyVectorCell findVectorCell(CellId cellId) {
        MyVectorCell mvc = cells.get(cellId);
        if (mvc == null) {
            mvc = new MyVectorCell(cellId);
            cells.put(cellId, mvc);
        }
        return mvc;
    }

    private int getNumFlatBoxes(CellId cellId, Layer layer, HashMap<CellId, Integer> numFlatBoxes) {
        Integer num = numFlatBoxes.get(cellId);
        if (num == null) {
            int count = getNumBoxes(cellId, layer);
            for (ImmutableNodeInst n : getSubcells(cellId)) {
                count += getNumFlatBoxes((CellId) n.protoId, layer, numFlatBoxes);
            }
            num = Integer.valueOf(count);
            numFlatBoxes.put(cellId, num);
        }
        return num.intValue();
    }

    private void addBoxesFromBuilder(MyVectorCell vc, HashMap<Layer, VectorManhattanBuilder> boxBuilders) {
        for (Map.Entry<Layer, VectorManhattanBuilder> e : boxBuilders.entrySet()) {
            Layer layer = e.getKey();
            VectorManhattanBuilder b = e.getValue();
            if (b.size == 0) {
                continue;
            }
            CellLayer cellLayer = vc.getCellLayer(layer);
            assert cellLayer.boxCoords.length == 0;
            cellLayer.setBoxCoords(b.toArray());
        }
    }

    private void collectLayer(Layer layer, MyVectorCell vc, Point anchor, Orientation orient, PutRectangle putRectangle) {
        int[] coords = new int[4];
        CellLayer cellLayer = vc.layers.get(layer);
        if (cellLayer != null) {
            int[] boxCoords = cellLayer.boxCoords;
            for (int i = 0; i < boxCoords.length; i += 4) {
                coords[0] = boxCoords[i + 0];
                coords[1] = boxCoords[i + 1];
                coords[2] = boxCoords[i + 2];
                coords[3] = boxCoords[i + 3];
                orient.rectangleBounds(coords);

                int lx = anchor.x + coords[0];
                int ly = anchor.y + coords[1];
                int hx = anchor.x + coords[2];
                int hy = anchor.y + coords[3];
                assert lx <= hx && ly <= hy;

                putRectangle.put(lx, ly, hx, hy);
            }
        }
        for (ImmutableNodeInst n : vc.subCells) {
            assert n.orient.isManhattan();
            coords[0] = (int) n.anchor.getGridX();
            coords[1] = (int) n.anchor.getGridY();
            orient.transformPoints(1, coords);
            Orientation subOrient = orient.concatenate(n.orient).canonic();
            MyVectorCell subCell = findVectorCell((CellId) n.protoId);
            collectLayer(layer, subCell, new Point(anchor.x + coords[0], anchor.y + coords[1]), subOrient, putRectangle);
        }
    }

    /**
     * Method to tell whether a Cell is parameterized.
     * Code is taken from tool.drc.Quick.checkEnumerateProtos
     * Could also use the code in tool.io.output.Spice.checkIfParameterized
     * @param cellRevision the Cell to examine
     * @return true if the cell has parameters
     */
    private boolean isCellParameterized(CellRevision cellRevision) {
        if (cellRevision.d.getNumParameters() > 0) {
            return true;
        }

        // look for any Java coded stuff (Logical Effort calls)
        for (ImmutableNodeInst n : cellRevision.nodes) {
            if (n instanceof ImmutableIconInst) {
                for (Iterator<Variable> vIt = ((ImmutableIconInst) n).getDefinedParameters(); vIt.hasNext();) {
                    Variable var = vIt.next();
                    if (var.isCode()) {
                        return true;
                    }
                }
            }
            for (Iterator<Variable> vIt = n.getVariables(); vIt.hasNext();) {
                Variable var = vIt.next();
                if (var.isCode()) {
                    return true;
                }
            }
        }
        for (ImmutableArcInst a : cellRevision.arcs) {
            for (Iterator<Variable> vIt = a.getVariables(); vIt.hasNext();) {
                Variable var = vIt.next();
                if (var.isCode()) {
                    return true;
                }
            }
        }

        // bus pin appearance depends on parent Cell
        for (ImmutableExport e : cellRevision.exports) {
            if (e.originalPortId == busPinPortId) {
                return true;
            }
        }
        return false;
    }

    private class ShapeBuilder extends AbstractShapeBuilder {

        private Layer polyLayer;

        @Override
        public void pushPoly(Poly.Type style, Layer layer, EGraphics graphicsOverride, PrimitivePort pp) {
            if (layer.getFunction() == Layer.Function.GATE && polyLayer != null) {
                layer = polyLayer;
            }
            super.pushPoly(style, layer, null, null);
        }

        @Override
        public void addPoly(int numPoints, Poly.Type style, Layer layer, EGraphics graphicsOverride, PrimitivePort pp) {
            if (numPoints == 2) {
                return;
            }
            if (style == Poly.Type.FILLED && graphicsOverride == null && pp == null && isManhattan(numPoints, layer)) {
                return;
            }
            badLayer(layer);
        }

        @Override
        public void addTextPoly(int numPoints, Poly.Type style, Layer layer, PrimitivePort pp, String message, TextDescriptor descriptor) {
            badLayer(layer);
        }

        @Override
        public void addBox(Layer layer) {
            // convert coordinates
            int lX = (int) (coords[0] >> FixpCoord.FRACTION_BITS);
            int lY = (int) (coords[1] >> FixpCoord.FRACTION_BITS);
            int hX = (int) (coords[2] >> FixpCoord.FRACTION_BITS);
            int hY = (int) (coords[3] >> FixpCoord.FRACTION_BITS);
            if (coords[0] == (((long) lX) << FixpCoord.FRACTION_BITS)
                    && coords[1] == (((long) lY) << FixpCoord.FRACTION_BITS)
                    && coords[2] == (((long) hX) << FixpCoord.FRACTION_BITS)
                    && coords[3] == (((long) hY) << FixpCoord.FRACTION_BITS)) {
                layers.add(layer);
                putBox(layer, boxBuilders, lX, lY, hX, hY);
            } else {
                badLayer(layer);
            }
        }

        private boolean isManhattan(int numPoints, Layer layer) {
            if (numPoints % 2 != 0) {
                return false;
            }

//            System.out.println("numPoints="+numPoints);
            TreeMap<Integer, Integer> xcoords = new TreeMap<Integer, Integer>();
            TreeMap<Integer, Integer> ycoords = new TreeMap<Integer, Integer>();
            int minI = 0;
            int minX = (int) (coords[0] >> FixpCoord.FRACTION_BITS);
            int minY = (int) (coords[1] >> FixpCoord.FRACTION_BITS);
            if ((((long) minX) << FixpCoord.FRACTION_BITS) != coords[0] || (((long) minY) << FixpCoord.FRACTION_BITS) != coords[1]) {
                return false;
            }
            xcoords.put(Integer.valueOf(minX), null);
            ycoords.put(Integer.valueOf(minY), null);
            for (int i = 1; i < numPoints; i++) {
                int x = (int) (coords[i * 2 + 0] >> FixpCoord.FRACTION_BITS);
                int y = (int) (coords[i * 2 + 1] >> FixpCoord.FRACTION_BITS);
                if ((((long) x) << FixpCoord.FRACTION_BITS) != coords[i * 2 + 0] || (((long) y) << FixpCoord.FRACTION_BITS) != coords[i * 2 + 1]) {
                    return false;
                }
                if (x < minX || x == minX && y < minY) {
                    minI = i;
                    minX = x;
                    minY = y;
                }
                xcoords.put(Integer.valueOf(x), null);
                ycoords.put(Integer.valueOf(y), null);
            }
            int[] xvals = new int[xcoords.size()];
            int ix = 0;
            for (Map.Entry<Integer, Integer> e : xcoords.entrySet()) {
                int xv = e.getKey();
                xvals[ix] = xv;
                e.setValue(Integer.valueOf(ix));
//                System.out.println("x"+ix+"="+xv);
                ix++;
            }
            int[] yvals = new int[ycoords.size()];
            int iy = 0;
            for (Map.Entry<Integer, Integer> e : ycoords.entrySet()) {
                int yv = e.getKey();
                yvals[iy] = yv;
                e.setValue(Integer.valueOf(iy));
//                System.out.println("y"+iy+"="+yv);
                iy++;
            }

            List<List<Long>> accum = new ArrayList<List<Long>>();
            for (int i = 0; i < xcoords.size(); i++) {
                accum.add(new ArrayList<Long>());
            }
            boolean clockwise = (coords[(minI + 1) % numPoints * 2] >> FixpCoord.FRACTION_BITS) != minX;
            for (int i = 0; i < numPoints / 2; i++) {
                int x0 = (int) (coords[(minI + i * 2) % numPoints * 2 + 0] >> FixpCoord.FRACTION_BITS);
                int y0 = (int) (coords[(minI + i * 2) % numPoints * 2 + 1] >> FixpCoord.FRACTION_BITS);
                int x1 = (int) (coords[(minI + i * 2 + 1) % numPoints * 2 + 0] >> FixpCoord.FRACTION_BITS);
                int y1 = (int) (coords[(minI + i * 2 + 1) % numPoints * 2 + 1] >> FixpCoord.FRACTION_BITS);
                int x2 = (int) (coords[(minI + i * 2 + 2) % numPoints * 2 + 0] >> FixpCoord.FRACTION_BITS);
                int y2 = (int) (coords[(minI + i * 2 + 2) % numPoints * 2 + 1] >> FixpCoord.FRACTION_BITS);
                int yi, x0i, x1i;
                if (clockwise) {
                    if (x1 == x0 || y1 != y0 || y2 == y1 || x2 != x1) {
                        return false;
                    }
                    yi = ycoords.get(y0);
                    x0i = xcoords.get(x0);
                    x1i = xcoords.get(x1);
                } else {
                    if (x1 != x0 || y1 == y0 || y2 != y1 || x2 == x1) {
                        return false;
                    }
                    yi = ycoords.get(y1);
                    x0i = xcoords.get(x2);
                    x1i = xcoords.get(x1);
                }
                assert x0i != x1i;
                if (x0i < x1i) {
                    for (int x = x0i; x < x1i; x++) {
                        accum.get(x).add((((long) yi) << 1));
                    }
                } else {
                    for (int x = x1i; x < x0i; x++) {
                        accum.get(x).add((((long) yi) << 1) | 1);
                    }
                }
            }
            for (int x = 0; x < accum.size(); x++) {
//                System.out.print("a" + x + ":");
                List<Long> yl = accum.get(x);
                Collections.sort(yl);
                Integer prevY = null;
                for (Long y : yl) {
                    int thisY = (int) (y >> 1);
                    if ((y & 1) != 0) {
                        if (prevY == null) {
                            return false;
                        }
                        for (int yi = prevY; yi < thisY; yi++) {
                            putBox(layer, boxBuilders, xvals[x], yvals[yi], xvals[x + 1], yvals[yi + 1]);
                        }
//                        System.out.print(" " + thisY + ")");
                        prevY = null;
                    } else {
                        if (prevY != null) {
                            return false;
                        }
                        prevY = thisY;
//                        System.out.print(" (" + thisY);
                    }
                }
//                System.out.println();
            }
            return true;
        }
    }

    private void badLayer(Layer layer) {
        if (badLayers.add(layer)) {
            layer = layer;
        }
    }

    private static void putBox(Layer layer, HashMap<Layer, VectorManhattanBuilder> boxBuilders, int lX, int lY, int hX, int hY) {
        VectorManhattanBuilder b = boxBuilders.get(layer);
        if (b == null) {
            b = new VectorManhattanBuilder();
            boxBuilders.put(layer, b);
        }
        assert lX <= hX && lY <= hY;
        if (lX < hX && lY < hY) {
            b.add(lX, lY, hX, hY);
        }
    }

    static class MyVectorPolygon {

        final Layer layer;
        final Poly.Type style;
        final Point2D[] points;

        private MyVectorPolygon(Poly.Type style, Layer layer, Point2D[] points) {
            this.layer = layer;
            this.style = style;
            this.points = points;
        }
    }

    /**
     * Class which collects boxes for VectorManhattan.
     */
    static class VectorManhattanBuilder {

        /** Number of boxes. */
        int size; // number of boxes
        /** Coordiantes of boxes. */
        int[] coords = new int[4];

        private void add(int lX, int lY, int hX, int hY) {
            if (size * 4 >= coords.length) {
                int[] newCoords = new int[coords.length * 2];
                System.arraycopy(coords, 0, newCoords, 0, coords.length);
                coords = newCoords;
            }
            int i = size * 4;
            coords[i] = lX;
            coords[i + 1] = lY;
            coords[i + 2] = hX;
            coords[i + 3] = hY;
            size++;
        }

        int[] toArray() {
            int[] a = new int[size * 4];
            System.arraycopy(coords, 0, a, 0, a.length);
            return a;
        }

        private void clear() {
            size = 0;
        }
    }
}
