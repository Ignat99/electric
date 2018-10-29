/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: CellArray.java
 * Written by: Adam Megacz
 *
 * Copyright (c) 2014 Static Free Software
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
 * along with Electric(tm); see the file COPYING.  If not, write to
 * the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
 * Boston, Mass 02111-1307, USA.
 */
package com.sun.electric.tool.io.input;

import com.sun.electric.database.EditingPreferences;
import com.sun.electric.database.geometry.EPoint;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.Library;
import com.sun.electric.database.prototype.NodeProto;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.util.TextUtils;
import com.sun.electric.util.math.FixpCoord;
import com.sun.electric.util.math.Orientation;

import java.util.HashMap;
import java.util.Map;

/**
 * This class builds large instance arrays by creating a
 * logarithmic-depth tree of bisections; this makes it possible to
 * import complex foundry-provided cells (such as IO pads) without
 * creating cripplingly-huge cells.
 *
 * For example, a 256x256 GDS array reference will result in four
 * instances of a 128x12 cell; the 128x128 cell will contain four
 * instances of a 64x64 cell, and so on.
 *
 * If the requested dimensions are not square then only the larger
 * dimension is bisected; for example a 256x8 array contains a pair of
 * 128x8 instances (not four 128x4 instances) -- this ensures that the
 * minimum possible amount of non-square cells are created and
 * maximizes the reuse of cells.
 *
 * If one or both of the requested dimensions are not a power of two
 * then that dimension is partitioned into two unequal parts: one
 * which is the largest possible power of two and the other which
 * contains the remainder (for example, 9x1 becomes 8x1+1x1).  This
 * too ensures that the minimum amount of non-power-of-two-dimension
 * cells are created, which also maximizes reuse of cells.
 *
 * Arrays smaller than 4x4 are realized directly.
 */

public class CellArrayBuilder {

    public final Library theLibrary;

    public CellArrayBuilder(Library theLibrary) { this.theLibrary = theLibrary; }

    private static HashMap<Map.Entry<NodeProto, String>, CellArray> cellArrayCache =
        new HashMap<Map.Entry<NodeProto, String>, CellArray>();

    public class CellArray {

        public final NodeProto proto;
        public final int cols;
        public final int rows;
        public final FixpCoord colspace;
        public final FixpCoord rowspace;
        private Cell cell = null;

        public CellArray(NodeProto proto, int cols, int rows, FixpCoord colspace, FixpCoord rowspace) {
            this.proto = proto;
            this.cols = cols;
            this.rows = rows;
            this.colspace = colspace;
            this.rowspace = rowspace;
        }

        public Cell makeCell(EditingPreferences ep) {
            if (cell != null) return cell;
            String name = proto.getName();
            if (name.indexOf('{') != -1) name = name.substring(0, name.indexOf('{'));
            name += "_" + makeArrayName(cols, rows, colspace, rowspace) + "{lay}";
            this.cell = Cell.newInstance(theLibrary, name);
            if (cell == null) throw new RuntimeException("Cell.newInstance("+name+") returned null");
            EPoint bottomLeftInstance = EPoint.ORIGIN;
            if (rows < 4 && cols < 4) {
                // leaf cell of the bisection hierarchy
                buildFlatArray(proto, cell, bottomLeftInstance, Orientation.fromAngle(0), cols, rows, colspace, rowspace, ep);
            } else {
                // non-leaf cell of the bisection hierarchy
                buildArrayBisected(proto, cell, bottomLeftInstance, Orientation.fromAngle(0), cols, rows, colspace, rowspace, ep);
            }
            return cell;
        }
    }

    private String makeArrayName(int cols, int rows, FixpCoord colspace, FixpCoord rowspace) {
    	return cols + "x" + rows + "sep" + TextUtils.formatDouble(colspace.getLambda()) + "x" + TextUtils.formatDouble(rowspace.getLambda());
    }

    private CellArray getCellArray(NodeProto proto, int cols, int rows, FixpCoord colspace, FixpCoord rowspace) {
        Map.Entry<NodeProto, String> key =
            new java.util.AbstractMap.SimpleEntry(proto, makeArrayName(cols, rows, colspace, rowspace));
        CellArray ret = cellArrayCache.get(key);
        if (ret == null) {
            ret = new CellArray(proto, cols, rows, colspace, rowspace);
            cellArrayCache.put(key, ret);
        }
        return ret;
    }

    private boolean isPowerOfTwo(int x) {
        // dumb
        for(int i=0; i<32; i++)
            if (x == (1<<i))
                return true;
        return false;
    }

    public void buildArrayUsingSubcells(NodeProto proto, Cell parent, EPoint bottomLeftInstanceLocation, Orientation orient,
                                        int cols, int rows, FixpCoord colspace, FixpCoord rowspace, EditingPreferences ep) {

        // if there are an even number of rows and columns this is a straightforward instantiation
        if (isPowerOfTwo(cols) && isPowerOfTwo(rows)) {

            Cell arrCell;
            EPoint loc = bottomLeftInstanceLocation;

            Orientation corient = orient.canonic();

            switch(corient.getAngle()) {
                case 2700:
                case 900:
                    arrCell = getCellArray(proto, rows, cols, rowspace, colspace).makeCell(ep);
                    break;
                case 1800:
                case 000:
                    arrCell = getCellArray(proto, cols, rows, colspace, rowspace).makeCell(ep);
                    break;
                default: throw new Error("got rotation of " + corient.getAngle());
            }

            switch(corient.getAngle()) {
                case 2700:
                    loc = corient.isYMirrored() ? loc : EPoint.fromFixp(loc.getCoordX().getFixp(), rowspace.multiply(rows-1).add(loc.getCoordY()).getFixp());
                    break;
                case 1800:
                    loc = EPoint.fromFixp(loc.getCoordX().add(colspace.multiply(cols-1)).getFixp(), loc.getCoordY().getFixp());
                    loc = corient.isYMirrored() ? loc : EPoint.fromFixp(loc.getCoordX().getFixp(), rowspace.multiply(rows-1).add(loc.getCoordY()).getFixp());
                    break;
                case 900:
                    loc = EPoint.fromFixp(loc.getCoordX().add(colspace.multiply(cols-1)).getFixp(), loc.getCoordY().getFixp());
                    loc = !corient.isYMirrored() ? loc : EPoint.fromFixp(loc.getCoordX().getFixp(), rowspace.multiply(rows-1).add(loc.getCoordY()).getFixp());
                    break;
                case 0:
                    loc = !corient.isYMirrored() ? loc : EPoint.fromFixp(loc.getCoordX().getFixp(), rowspace.multiply(rows-1).add(loc.getCoordY()).getFixp());
                    break;
                default: throw new Error("got rotation of " + corient.getAngle());
            }
            NodeInst.makeInstance(arrCell, ep, loc, arrCell.getDefWidth(null), arrCell.getDefHeight(null), parent, orient, null);

        } else {
            // otherwise be more intelligent
            buildArrayBisected(proto, parent, bottomLeftInstanceLocation, orient, cols, rows, colspace, rowspace, ep);
        }
    }

    /** makes an array with subcells */
    public void buildArrayBisected(NodeProto proto, Cell parent, EPoint bottomLeftInstanceLocation, Orientation orient,
                                   int cols, int rows, FixpCoord colspace, FixpCoord rowspace, EditingPreferences ep) {

        for(int x=0; x<cols; ) {

            int width = 1;
            while ((width<<1)+x <= cols && (width<<1)<=(cols>=rows?cols/2:cols)) width = width<<1;

            for(int y=0; y<rows; ) {

                int height = 1;
                while ((height<<1)+y <= rows && (height<<1)<=(rows>=cols?rows/2:rows)) height = height<<1;

                buildArrayUsingSubcells(proto, parent,
                                        EPoint.fromFixp(bottomLeftInstanceLocation.getCoordX().add(colspace.multiply(x)).getFixp(),
                                                        bottomLeftInstanceLocation.getCoordY().add(rowspace.multiply(y)).getFixp()),
                                        orient,
                                        width, height,
                                        colspace, rowspace, ep);

                y += height;
            }
            x += width;
        }
    }

    /** makes an array the "dumb way" */
    public void buildFlatArray(NodeProto proto, Cell parent, EPoint bottomLeftInstanceLocation, Orientation orient,
                               int cols, int rows, FixpCoord colspace, FixpCoord rowspace, EditingPreferences ep) {
        FixpCoord ptcX = bottomLeftInstanceLocation.getCoordX();
        FixpCoord ptcY = bottomLeftInstanceLocation.getCoordY();
        for (int ic = 0; ic < cols; ic++) {
            FixpCoord ptX = ptcX;
            FixpCoord ptY = ptcY;
            for (int ir = 0; ir < rows; ir++) {
                EPoint loc = EPoint.fromFixp(ptX.getFixp(), ptY.getFixp());
                NodeInst ni = NodeInst.makeInstance(proto,ep,loc,proto.getDefWidth(null), proto.getDefHeight(null), parent, orient, null);
                ptY = ptY.add(rowspace);
            }
            ptcX = ptcX.add(colspace);
        }
    }

    /** makes an array as intelligently as possible */
    public void buildArray(NodeProto proto, Cell parent,
                           EPoint startLoc, Orientation orient,
                           int cols, int rows,
                           FixpCoord colspace, FixpCoord rowspace, EditingPreferences ep) {
        if (cols<1) throw new Error();
        if (rows<1) throw new Error();
        if (colspace.signum()<0) {
            colspace = colspace.multiply(-1.0);
            startLoc = EPoint.fromFixp(startLoc.getCoordX().add(colspace.multiply(-1*(cols-1))).getFixp(), startLoc.getCoordY().getFixp());
        }
        if (rowspace.signum()<0) {
            rowspace = rowspace.multiply(-1.0);
            startLoc = EPoint.fromFixp(startLoc.getCoordX().getFixp(), startLoc.getCoordY().add(rowspace.multiply(-1*(rows-1))).getFixp());
        }

        /*
        // for debugging
        System.err.println("buildArray " + proto +
                           " "   + cols                             + "x" + rows +
                           " @ " + colspace.getLambda()             + ":" + rowspace.getLambda() +
                           " + " + startLoc.getCoordX().getLambda() + "," + startLoc.getCoordY().getLambda());
        */

        if (rows < 4 && cols < 4) {
            // small array; build explicitly
            buildFlatArray(proto, parent, startLoc, orient, cols, rows, colspace, rowspace, ep);
        } else {
            buildArrayUsingSubcells(proto, parent, startLoc, orient, cols, rows, colspace, rowspace, ep);
        }
    }
}
