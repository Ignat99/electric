/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: AbstractErrorLogger.java
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
package com.sun.electric.api.minarea.errorLogging;

import java.awt.Shape;
import java.awt.geom.Point2D;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import com.sun.electric.api.minarea.ErrorLogger;
import com.sun.electric.api.minarea.geometry.Shapes;

public abstract class AbstractErrorLogger implements ErrorLogger {

    protected class MinAreaViolation implements Comparable<MinAreaViolation> {

        private final long minArea;
        private final int x;
        private final int y;
        private final Shape shape;

        public MinAreaViolation(long minArea, int x, int y, Shape shape) {
            this.minArea = minArea;
            this.x = x;
            this.y = y;
            this.shape = shape;
            if (shape != null) {
                Point2D maxVertex = Shapes.maxVertex(shape);
                if (x != maxVertex.getX() || y != maxVertex.getY()) {
                    throw new IllegalArgumentException();
                }
            }
        }

        public long getMinArea() {
            return this.minArea;
        }

        public int getX() {
            return this.x;
        }

        public int getY() {
            return this.y;
        }

        public Shape getShape() {
            return this.shape;
        }

        @Override
        public String toString() {
            return new StringBuilder().append("(").append(x).append(",").append(y).append(")").toString();
        }

        public int compareTo(MinAreaViolation v2) {
            if (this.x > v2.x) {
                return 1;
            }
            if (this.x < v2.x) {
                return -1;
            }
            if (this.y > v2.y) {
                return 1;
            }
            if (this.y < v2.y) {
                return -1;
            }
            return 0;
        }
    }
    protected List<AbstractErrorLogger.MinAreaViolation> violations = new LinkedList<AbstractErrorLogger.MinAreaViolation>();

    public synchronized void reportMinAreaViolation(long area, int x, int y, Shape shape) {
        violations.add(new MinAreaViolation(area, x, y, shape));
    }

    public abstract void printReports(long time);

    protected synchronized void sortViolations() {
        Collections.sort(violations);
    }
}
