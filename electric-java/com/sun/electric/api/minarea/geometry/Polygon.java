/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Polygon.java
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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Immutable Polygon implementation
 * 
 * @author Felix Schmidt
 * 
 */
public class Polygon implements Serializable {

    public static interface PolygonUnionStrategy {

        public Polygon union(Polygon poly1, Polygon poly2);
    }

    public static interface PolygonIntersectStrategy {

        public Polygon intersect(Polygon poly1, Polygon poly2);

        public boolean intersectionTest(Polygon poly1, Polygon poly2);
    }
    protected List<Point> points;

    public Polygon(Point... points) {
        this.points = Arrays.asList(points);
    }

    public Rectangle getBoundingBox() {
        int lx = Integer.MAX_VALUE;
        int ly = Integer.MAX_VALUE;
        int hx = Integer.MIN_VALUE;
        int hy = Integer.MIN_VALUE;

        for (Point pt : points) {
            if (pt.getX() < lx) {
                lx = pt.getX();
            }
            if (pt.getY() < ly) {
                ly = pt.getY();
            }
            if (pt.getX() > hx) {
                hx = pt.getX();
            }
            if (pt.getY() > hy) {
                hy = pt.getY();
            }
        }

        return new Rectangle(new Point(lx, ly), new Point(hx, hy));
    }

    public List<Edge> extractEdges() {
        List<Edge> result = new ArrayList<Edge>();

        for (int i = 0; i < points.size(); i++) {
            result.add(new Edge(points.get(i), points.get((i + 1) % points.size())));
        }

        return result;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("Polygon = (\n");
        for (Point pt : points) {
            builder.append("   ");
            builder.append(pt.toString());
        }
        builder.append(")");
        return builder.toString();
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((points == null) ? 0 : points.hashCode());
        return result;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        Polygon other = (Polygon) obj;
        if (points == null) {
            if (other.points != null) {
                return false;
            }
        } else if (!points.equals(other.points)) {
            return false;
        }
        return true;
    }

    /**
     * 
     * @author Felix Schmidt
     * 
     */
    public static class Rectangle extends Polygon implements Serializable {

        /**
         * Constructor to build a Rectangle from two points.
         * @param min the minimum point values (lowest X/Y).
         * @param max the maximum point values (highest X/Y).
         */
        public Rectangle(Point min, Point max) {
            if (min.getX() >= max.getX() || min.getY() >= max.getY()) {
                throw new IllegalArgumentException();
            }

            this.points = Arrays.asList(min, max);
        }

        public Rectangle(int minX, int minY, int maxX, int maxY) {
            this(new Point(minX, minY), new Point(maxX, maxY));
        }

        /**
         * Method to return the minimum X/Y in a Point.
         * @return  the minimum X/Y in a Point.
         */
        public Point getMin() {
            return points.get(0);
        }

        /**
         * Method to return the maximum X/Y in a Point.
         * @return the maximum X/Y in a Point.
         */
        public Point getMax() {
            return points.get(1);
        }

        /**
         * Method to return the width of this Polygon.
         * @return return the width of this Polygon.
         */
        public int width() {
            return Math.abs(points.get(1).getX() - points.get(0).getX());
        }

        /**
         * Method to return the height of this Polygon.
         * @return return the height of this Polygon.
         */
        public int height() {
            return Math.abs(points.get(1).getY() - points.get(0).getY());
        }

        @Override
        public List<Edge> extractEdges() {
            return this.transformToPolygon().extractEdges();
        }

        public Polygon transformToPolygon() {
            Point top = new Point(getMax().getX(), getMin().getY());
            Point bottom = new Point(getMin().getX(), getMax().getY());
            return new Polygon(getMin(), bottom, getMax(), top);
        }

        public int[] toArray() {
            return new int[]{getMin().getX(), getMin().getY(), getMax().getX(), getMax().getY()};
        }

        /*
         * (non-Javadoc)
         * 
         * @see com.sun.electric.api.minarea.geometry.Polygon#toString()
         */
        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("Rectangle = (\n");
            builder.append("  ");
            for (Point pt : points) {
                builder.append(" ");
                builder.append(pt.toString());
                builder.append(" ");
                builder.append("x");
            }

            builder.replace(builder.length() - 2, builder.length(), "");

            builder.append(")");
            return builder.toString();
        }
    }

    /**
     * 
     * @author Felix Schmidt
     * 
     */
    public static class PolygonHole extends Polygon implements Serializable {

        /**
         * 
         * @param points
         */
        public PolygonHole(Point... points) {
            super(points);
        }
    }
}
