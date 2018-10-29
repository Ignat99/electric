/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: BitMapMinAreaChecker.java
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
package com.sun.electric.plugins.minarea.bitmapjava;

import com.sun.electric.api.minarea.ErrorLogger;
import com.sun.electric.api.minarea.LayoutCell;
import com.sun.electric.api.minarea.ManhattanOrientation;
import com.sun.electric.api.minarea.MinAreaChecker;
import com.sun.electric.api.minarea.geometry.Point;
import com.sun.electric.api.minarea.geometry.Shapes;

import java.awt.Shape;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Properties;
import java.util.Stack;
import java.util.TreeSet;

/**
 *
 */
public class BitMapMinAreaChecker implements MinAreaChecker {

    /**
     * 
     * @return the algorithm name
     */
    @Override
    public String getAlgorithmName() {
        return "BitMapJava";
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
     * @param topCell top cell of the layout
     * @param minArea minimal area of valid polygon
     * @param parameters algorithm parameters
     * @param errorLogger an API to report violations
     */
    @Override
    public void check(LayoutCell topCell, long minArea, Properties parameters, ErrorLogger errorLogger) {
        new Task(topCell, minArea, parameters, errorLogger);
    }

    private static class Task {

        private final int DEBUG = 0;
        private long totalArea;
        private long polyArea;
        // stack of tiles to fill polygon
        private Stack<Point> stack = new Stack<Point>();
        // polygon tiles
        private ArrayList<Point> curPolyBB = new ArrayList<Point>();
        private boolean reportTiles;
        int[] xa;
        int[] ya;
        BitSet[] bitMap;

        // traverse flattened rectangles
        private static void flattenRects(LayoutCell top, LayoutCell.RectangleHandler proc) {
            flatten(top, 0, 0, ManhattanOrientation.R0, proc);
        }

        private static void flatten(LayoutCell t, final int x, final int y, final ManhattanOrientation orient, final LayoutCell.RectangleHandler proc) {
            final int[] a = new int[4];
            t.traverseRectangles(new LayoutCell.RectangleHandler() {

                @Override
                public void apply(int minX, int minY, int maxX, int maxY) {
                    a[0] = minX;
                    a[1] = minY;
                    a[2] = maxX;
                    a[3] = maxY;
                    orient.transformRects(a, 0, 1);
                    proc.apply(a[0] + x, a[1] + y, a[2] + x, a[3] + y);
                }
            });
            t.traverseSubcellInstances(new LayoutCell.SubcellHandler() {

                @Override
                public void apply(LayoutCell subCell, int anchorX, int anchorY, ManhattanOrientation subOrient) {
                    a[0] = anchorX;
                    a[1] = anchorY;
                    orient.transformPoints(a, 0, 1);
                    flatten(subCell, a[0] + x, a[1] + y, orient.concatenate(subOrient), proc);
                }
            });
        }

        /**
         * @param topCell top cell of the layout
         * @param minArea minimal area of valid polygon
         * @param parameters algorithm parameters
         * @param errorLogger an API to report violations
         */
        private Task(LayoutCell topCell, long minArea, Properties parameters, ErrorLogger errorLogger) {

            reportTiles = Boolean.parseBoolean(parameters.get(MinAreaChecker.REPORT_TILES).toString());

            // find unique coordinates
            final TreeSet<Integer> xcoords = new TreeSet<Integer>();
            final TreeSet<Integer> ycoords = new TreeSet<Integer>();
            flattenRects(topCell, new LayoutCell.RectangleHandler() {

                @Override
                public void apply(int minX, int minY, int maxX, int maxY) {
                    if (DEBUG >= 4) {
                        System.out.println(" flat [" + minX + ".." + maxX + "]x[" + minY + ".." + maxY + "]");
                    }
                    xcoords.add(minX);
                    xcoords.add(maxX);
                    ycoords.add(minY);
                    ycoords.add(maxY);
                }
            });

            int xsize = xcoords.size() - 1;
            int ysize = ycoords.size() - 1;

            // xa,ya maps coordinate index to coordinate value
            // xm,ym maps coordinate value to coordinate index
            xa = new int[xsize + 1];
            ya = new int[ysize + 1];
            final HashMap<Integer, Integer> xm = new HashMap<Integer, Integer>();
            final HashMap<Integer, Integer> ym = new HashMap<Integer, Integer>();
            for (Integer x : xcoords) {
                xa[xm.size()] = x;
                xm.put(x, xm.size());
            }
            for (Integer y : ycoords) {
                ya[ym.size()] = y;
                ym.put(y, ym.size());
            }

            // fill bit map
            bitMap = new BitSet[xsize];
            for (int i = 0; i < bitMap.length; i++) {
                bitMap[i] = new BitSet();
            }
            flattenRects(topCell, new LayoutCell.RectangleHandler() {

                @Override
                public void apply(int minX, int minY, int maxX, int maxY) {
                    int ymin = ym.get(minY);
                    int ymax = ym.get(maxY);
                    for (int x = xm.get(minX), xmax = xm.get(maxX); x < xmax; x++) {
                        bitMap[x].set(ymin, ymax);
                    }
                }
            });

            if (DEBUG >= 4) {
                System.out.println("xcoords=" + xcoords);
                System.out.println("ycoords=" + ycoords);
                printBitMap(bitMap, xsize, ysize);
            }

            // find polygons in reverse lexicographical order
            totalArea = 0;
            for (int x = xsize - 1; x >= 0; x--) {
                for (int y = ysize - 1; y >= 0; y--) {
                    if (bitMap[x].get(y)) {
                        polyArea = 0;
                        assert curPolyBB.isEmpty();
                        // find polygon area and erase polygon from bit map
                        pushTile(x, y);
                        while (!stack.isEmpty()) {
                            Point p = stack.peek();
                            if (p.getX() - 1 >= 0 && bitMap[p.getX() - 1].get(p.getY())) {
                                pushTile(p.getX() - 1, p.getY());
                            } else if (p.getX() + 1 < xsize && bitMap[p.getX() + 1].get(p.getY())) {
                                pushTile(p.getX() + 1, p.getY());
                            } else if (p.getY() - 1 >= 0 && bitMap[p.getX()].get(p.getY() - 1)) {
                                pushTile(p.getX(), p.getY() - 1);
                            } else if (p.getY() + 1 < ysize && bitMap[p.getX()].get(p.getY() + 1)) {
                                pushTile(p.getX(), p.getY() + 1);
                            } else {
                                stack.pop();
                            }
                        }
                        totalArea += polyArea;
                        if (polyArea < minArea) {
                            Shape shape = null;
                            if (reportTiles) {
                                int[] tiles = new int[curPolyBB.size() * 4];
                                for (int i = 0; i < curPolyBB.size(); i++) {
                                    Point p = curPolyBB.get(i);
                                    int xi = p.getX();
                                    int yi = p.getY();
                                    tiles[i * 4 + 0] = xa[xi];
                                    tiles[i * 4 + 1] = ya[yi];
                                    tiles[i * 4 + 2] = xa[xi + 1];
                                    tiles[i * 4 + 3] = ya[yi + 1];
                                }
                                shape = Shapes.fromTiles(tiles);
                            }
                            errorLogger.reportMinAreaViolation(polyArea, xa[x + 1], ya[y + 1], shape);
                        }
                        curPolyBB.clear();
                    }
                }
            }
            if (DEBUG >= 1) {
                System.out.println("Total Area " + totalArea);
            }
        }

        private static void printBitMap(BitSet[] bitMap, int xsize, int ysize) {
            for (int y = ysize - 1; y >= 0; y--) {
                for (int x = 0; x < xsize; x++) {
                    System.out.print(bitMap[x].get(y) ? 'X' : ' ');
                }
                System.out.println();
            }
        }

        private void pushTile(int x, int y) {
            long w = xa[x + 1] - xa[x];
            long h = ya[y + 1] - ya[y];
            polyArea += w * h;
            bitMap[x].clear(y);
            Point p = new Point(x, y);
            if (reportTiles) {
                curPolyBB.add(p);
            }
            stack.push(p);
        }
    }
}
