/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: SimpleChecker.java
 *
 * Copyright (c) 2011, Static Free Software. All rights reserved.
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
package com.sun.electric.plugins.minarea.deltamerge0;

import com.sun.electric.api.minarea.ErrorLogger;
import com.sun.electric.api.minarea.LayoutCell;
import com.sun.electric.api.minarea.ManhattanOrientation;
import com.sun.electric.api.minarea.MinAreaChecker;
import com.sun.electric.api.minarea.geometry.Point;
import com.sun.electric.database.geometry.PolyBase;
import com.sun.electric.database.geometry.bool.DeltaMerge;
import com.sun.electric.database.geometry.bool.PointsSorter;
import com.sun.electric.database.geometry.bool.UnloadPolys;
import com.sun.electric.util.math.DBMath;
import java.awt.geom.Area;

import java.awt.geom.Point2D;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Simple MinAreaChecker
 */
public class SimpleChecker implements MinAreaChecker {

    /**
     * 
     * @return the algorithm name
     */
    @Override
    public String getAlgorithmName() {
        return "DeltaMerge0";
    }

    /**
     * 
     * @return the names and default values of algorithm parameters
     */
    @Override
    public Properties getDefaultParameters() {
        Properties parameters = new Properties();
        parameters.put(MinAreaChecker.REPORT_TILES, Boolean.TRUE);
        return parameters;
    }

    /**
     * @param topCell
     *            top cell of the layout
     * @param minArea
     *            minimal area of valid polygon
     * @param parameters
     *            algorithm parameters
     * @param errorLogger
     *            an API to report violations
     */
    @Override
    public void check(LayoutCell topCell, long minArea, Properties parameters, ErrorLogger errorLogger) {
        new Task(topCell, minArea, parameters, errorLogger);
    }

    private static class Task {

        private final int DEBUG = 0;
        private long totalArea;
        private boolean reportTiles;

        /**
         * @param topCell
         *            top cell of the layout
         * @param minArea
         *            minimal area of valid polygon
         * @param parameters
         *            algorithm parameters
         * @param errorLogger
         *            an API to report violations
         */
        private Task(LayoutCell topCell, long minArea, Properties parameters, ErrorLogger errorLogger) {
            reportTiles = Boolean.parseBoolean(parameters.get(MinAreaChecker.REPORT_TILES).toString());

            PointsSorter ps = new PointsSorter();
            collect(ps, topCell, 0, 0, ManhattanOrientation.R0);
            try {
                ByteArrayOutputStream bout = new ByteArrayOutputStream();
                DataOutputStream out = new DataOutputStream(bout);
                DeltaMerge dm = new DeltaMerge();
                dm.loop(ps, out);
                out.close();
                byte[] ba = bout.toByteArray();
                bout = null;
                DataInputStream inpS = new DataInputStream(new ByteArrayInputStream(ba));
                UnloadPolys up = new UnloadPolys();
                Iterable<PolyBase.PolyBaseTree> trees = up.loop(inpS, false);
                inpS.close();
                totalArea = 0;
                for (PolyBase.PolyBaseTree tree : trees) {
                    traversePolyTree(tree, 0, minArea, errorLogger);
                }
                if (DEBUG >= 1) {
                    System.out.println("Total Area " + totalArea);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private void collect(final PointsSorter ps, LayoutCell cell, final int x, final int y, final ManhattanOrientation orient) {
            int bufSize = Math.max(1, Math.min(16, cell.getNumRectangles()));
            int[] coords = new int[4 * bufSize];
            int ir = 0;
            while (ir < cell.getNumRectangles()) {
                int sz = Math.min(bufSize, cell.getNumRectangles() - ir);
                cell.readRectangleCoords(coords, ir, sz);
                orient.transformRects(coords, 0, sz);
                for (int j = 0; j < sz; j++) {
                    // The coordinates will fit into ints, because bounding box
                    // of LayoutCell is limited within [-MAX_Coord,+MAX_COORD]
                    int lx = coords[j * 4 + 0] + x;
                    int ly = coords[j * 4 + 1] + y;
                    int hx = coords[j * 4 + 2] + x;
                    int hy = coords[j * 4 + 3] + y;
                    if (DEBUG >= 4) {
                        System.out.println(" flat [" + lx + ".." + hx + "]x[" + ly + ".." + hy + "]");
                    }
                    ps.put(lx, ly, hx, hy);
                }
                ir += sz;
            }

            if (cell.getNumSubcells() > 0) {
                cell.traverseSubcellInstances(new LayoutCell.SubcellHandler() {

                    @Override
                    public void apply(LayoutCell subCell, int anchorX, int anchorY, ManhattanOrientation subOrient) {
                        Point p = new Point(anchorX, anchorY).transform(orient);
                        collect(ps, subCell, p.getX() + x, p.getY() + y, orient.concatenate(subOrient));
                    }
                });
            }
        }

        private void traversePolyTree(PolyBase.PolyBaseTree obj, int level, long minArea, ErrorLogger errorLogger) {
            if (level % 2 == 0) {
                PolyBase poly = obj.getPoly();
                if (DEBUG >= 4) {
                    for (Point2D p : poly.getPoints()) {
                        System.out.print(" (" + DBMath.lambdaToGrid(p.getX()) + "," + DBMath.lambdaToGrid(p.getY()) + ")");
                    }
                    System.out.println();
                }
                double area = poly.getArea();
                for (PolyBase.PolyBaseTree son : obj.getSons()) {
                    PolyBase hole = son.getPoly();
                    if (DEBUG >= 4) {
                        System.out.print(" hole");
                        for (Point2D p : hole.getPoints()) {
                            System.out.print(" (" + DBMath.lambdaToGrid(p.getX()) + "," + DBMath.lambdaToGrid(p.getY()) + ")");
                        }
                        System.out.println();
                    }
                    area -= hole.getArea();
                }
                long larea = DBMath.lambdaToGrid(area * DBMath.GRID);
                totalArea += larea;
                if (larea < minArea) {
                    Point2D p = poly.getPoints()[1];
                    Area shape = null;
                    if (reportTiles) {
                        shape = new Area(scale(poly));
                        for (PolyBase.PolyBaseTree son : obj.getSons()) {
                            PolyBase hole = son.getPoly();
                            shape.subtract(new Area(scale(hole)));
                        }
                    }
                    errorLogger.reportMinAreaViolation(larea, (int) DBMath.lambdaToGrid(p.getX()), (int) DBMath.lambdaToGrid(p.getY()), shape);
                }
            }
            for (PolyBase.PolyBaseTree son : obj.getSons()) {
                traversePolyTree(son, level + 1, minArea, errorLogger);
            }
        }

        private static PolyBase scale(PolyBase poly) {
            Point2D[] origPoints = poly.getPoints();
            PolyBase.Point[] newPoints = new PolyBase.Point[origPoints.length];
            for (int i = 0; i < origPoints.length; i++) {
                Point2D p = origPoints[i];
                newPoints[i] = PolyBase.fromLambda(DBMath.lambdaToGrid(p.getX()), DBMath.lambdaToGrid(p.getY()));
            }
            return new PolyBase(newPoints);
        }
    }
}
