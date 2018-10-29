/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: BitMapMinAreaChecker.scala
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
package com.sun.electric.api.minarea.geometry;

import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.util.List;

/**
 * Convertors to Shape
 */
public class Shapes {

    /**
     * Create Shape from a List of tiles.
     * Each tile is represented as 4-element int array (minX,minY,maxX,maxY).
     * @param tiles List of tiles
     * @return equivalent Shape
     */
    public static Shape fromTiles(List<int[]> tiles) {
        Area area = new Area();
        for (int tile[] : tiles) {
            int x = tile[0];
            int y = tile[1];
            int w = tile[2] - x;
            int h = tile[3] - y;
            area.add(new Area(new Rectangle(x, y, w, h)));
        }
        return area;
    }

    /**
     * Create Shape from a List of tiles.
     * Each tile is represented as 4 elements of int array (minX,minY,maxX,maxY).
     * @param tiles array with coordinates of tiles.
     * @return equivalent Shape
     */
    public static Shape fromTiles(int[] tiles) {
        Area area = new Area();
        for (int i = 0; i * 4 < tiles.length; i++) {
            int x = tiles[i * 4 + 0];
            int y = tiles[i * 4 + 1];
            int w = tiles[i * 4 + 2] - x;
            int h = tiles[i * 4 + 3] - y;
            area.add(new Area(new Rectangle(x, y, w, h)));
        }
        return area;
    }

    /**
     * Find vertex of a Shapewith lexigraphically maximal coordinates (x,y).
     * Formally, such Vertex (x,y) is reported that for any other Vertex (x',y') of
     * this shape: (x' < x || x' == x && y' < y)
     * @param shape input Shape
     * @return lixigraphical maximal vertex
     * @throws UnsupportedOperationException when Shape contains non-linear curves
     */
    public static Point2D maxVertex(Shape shape) {
        double xm = Double.NEGATIVE_INFINITY;
        double ym = Double.NEGATIVE_INFINITY;
        double coords[] = new double[6];
        PathIterator pit = shape.getPathIterator(null);
        while (!pit.isDone()) {
            switch (pit.currentSegment(coords)) {
                case PathIterator.SEG_MOVETO:
                case PathIterator.SEG_LINETO:
                    double xs = coords[0];
                    double ys = coords[1];
                    if (xs > xm || xs == xm && ys > ym) {
                        xm = xs;
                        ym = ys;
                    }
                    break;
                case PathIterator.SEG_CLOSE:
                    break;
                default:
                    throw new UnsupportedOperationException();
            }
            pit.next();
        }
        return new Point2D.Double(xm, ym);
    }
}
