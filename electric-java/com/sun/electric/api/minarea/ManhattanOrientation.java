/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: ManhattanOrientation.java
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
package com.sun.electric.api.minarea;

import java.awt.geom.AffineTransform;

/**
 * Enumeration to specify Manhattan orientation.
 * There are 8 Manhattan orientations.
 * The names of the constants are standard EDIF orientations.
 */
public enum ManhattanOrientation {

    /**
     * This picture shows transformation of letter 'F' by
     * all 8 manhattan transformations.
     * 
     *     MY R0
     *  R90     MXR90
     * MYR90    R270
     *   R180 MX
     * 
     *   XXXXX XXXXX
     *       X X
     * X    XX XX    X
     * X     X X     X
     * X     X X     X
     * X X         X X
     * XXXXX     XXXXX
     * 
     * XXXXX     XXXXX
     * X X         X X
     * X     X X     X
     * X     X X     X
     * X    XX XX    X
     *       X X
     *   XXXXX XXXXX
     */
    R0 {

        public AffineTransform affineTransform() {
            return new AffineTransform(1, 0, 0, 1, 0, 0);
        }

        public void transformPoints(int[] coords, int offset, int count) {
        }

        public void transformRects(int[] coords, int offset, int count) {
        }
    },
    R90 {

        public AffineTransform affineTransform() {
            return new AffineTransform(0, 1, -1, 0, 0, 0);
        }

        public void transformPoints(int[] coords, int offset, int count) {
            for (int i = 0; i < count; i++) {
                int x = coords[offset + i * 2 + 0];
                int y = coords[offset + i * 2 + 1];
                coords[offset + i * 2 + 0] = -y;
                coords[offset + i * 2 + 1] = x;
            }
        }

        public void transformRects(int[] coords, int offset, int count) {
            for (int i = 0; i < count; i++) {
                int lx = coords[offset + i * 4 + 0];
                int ly = coords[offset + i * 4 + 1];
                int hx = coords[offset + i * 4 + 2];
                int hy = coords[offset + i * 4 + 3];
                coords[offset + i * 4 + 0] = -hy;
                coords[offset + i * 4 + 1] = lx;
                coords[offset + i * 4 + 2] = -ly;
                coords[offset + i * 4 + 3] = hx;
            }
        }
    },
    R180 {

        public AffineTransform affineTransform() {
            return new AffineTransform(-1, 0, 0, -1, 0, 0);
        }

        public void transformPoints(int[] coords, int offset, int count) {
            for (int i = 0; i < count; i++) {
                int x = coords[offset + i * 2 + 0];
                int y = coords[offset + i * 2 + 1];
                coords[offset + i * 2 + 0] = -x;
                coords[offset + i * 2 + 1] = -y;
            }
        }

        public void transformRects(int[] coords, int offset, int count) {
            for (int i = 0; i < count; i++) {
                int lx = coords[offset + i * 4 + 0];
                int ly = coords[offset + i * 4 + 1];
                int hx = coords[offset + i * 4 + 2];
                int hy = coords[offset + i * 4 + 3];
                coords[offset + i * 4 + 0] = -hx;
                coords[offset + i * 4 + 1] = -hy;
                coords[offset + i * 4 + 2] = -lx;
                coords[offset + i * 4 + 3] = -ly;
            }
        }
    },
    R270 {

        public AffineTransform affineTransform() {
            return new AffineTransform(0, -1, 1, 0, 0, 0);
        }

        public void transformPoints(int[] coords, int offset, int count) {
            for (int i = 0; i < count; i++) {
                int x = coords[offset + i * 2 + 0];
                int y = coords[offset + i * 2 + 1];
                coords[offset + i * 2 + 0] = y;
                coords[offset + i * 2 + 1] = -x;
            }
        }

        public void transformRects(int[] coords, int offset, int count) {
            for (int i = 0; i < count; i++) {
                int lx = coords[offset + i * 4 + 0];
                int ly = coords[offset + i * 4 + 1];
                int hx = coords[offset + i * 4 + 2];
                int hy = coords[offset + i * 4 + 3];
                coords[offset + i * 4 + 0] = ly;
                coords[offset + i * 4 + 1] = -hx;
                coords[offset + i * 4 + 2] = hy;
                coords[offset + i * 4 + 3] = -lx;
            }
        }
    },
    MY {

        public AffineTransform affineTransform() {
            return new AffineTransform(-1, 0, 0, 1, 0, 0);
        }

        public void transformPoints(int[] coords, int offset, int count) {
            for (int i = 0; i < count; i++) {
                int x = coords[offset + i * 2 + 0];
                coords[offset + i * 2 + 0] = -x;
            }
        }

        public void transformRects(int[] coords, int offset, int count) {
            for (int i = 0; i < count; i++) {
                int lx = coords[offset + i * 4 + 0];
                int hx = coords[offset + i * 4 + 2];
                coords[offset + i * 4 + 0] = -hx;
                coords[offset + i * 4 + 2] = -lx;
            }
        }
    },
    MYR90 {

        public AffineTransform affineTransform() {
            return new AffineTransform(0, -1, -1, 0, 0, 0);
        }

        public void transformPoints(int[] coords, int offset, int count) {
            for (int i = 0; i < count; i++) {
                int x = coords[offset + i * 2 + 0];
                int y = coords[offset + i * 2 + 1];
                coords[offset + i * 2 + 0] = -y;
                coords[offset + i * 2 + 1] = -x;
            }
        }

        public void transformRects(int[] coords, int offset, int count) {
            for (int i = 0; i < count; i++) {
                int lx = coords[offset + i * 4 + 0];
                int ly = coords[offset + i * 4 + 1];
                int hx = coords[offset + i * 4 + 2];
                int hy = coords[offset + i * 4 + 3];
                coords[offset + i * 4 + 0] = -hy;
                coords[offset + i * 4 + 1] = -hx;
                coords[offset + i * 4 + 2] = -ly;
                coords[offset + i * 4 + 3] = -lx;
            }
        }
    },
    MX {

        public AffineTransform affineTransform() {
            return new AffineTransform(1, 0, 0, -1, 0, 0);
        }

        public void transformPoints(int[] coords, int offset, int count) {
            for (int i = 0; i < count; i++) {
                int y = coords[offset + i * 2 + 1];
                coords[offset + i * 2 + 1] = -y;
            }
        }

        public void transformRects(int[] coords, int offset, int count) {
            for (int i = 0; i < count; i++) {
                int ly = coords[offset + i * 4 + 1];
                int hy = coords[offset + i * 4 + 3];
                coords[offset + i * 4 + 1] = -hy;
                coords[offset + i * 4 + 3] = -ly;
            }
        }
    },
    MXR90 {

        public AffineTransform affineTransform() {
            return new AffineTransform(0, 1, 1, 0, 0, 0);
        }

        public void transformPoints(int[] coords, int offset, int count) {
            for (int i = 0; i < count; i++) {
                int x = coords[offset + i * 2 + 0];
                int y = coords[offset + i * 2 + 1];
                coords[offset + i * 2 + 0] = y;
                coords[offset + i * 2 + 1] = x;
            }
        }

        public void transformRects(int[] coords, int offset, int count) {
            for (int i = 0; i < count; i++) {
                int lx = coords[offset + i * 4 + 0];
                int ly = coords[offset + i * 4 + 1];
                int hx = coords[offset + i * 4 + 2];
                int hy = coords[offset + i * 4 + 3];
                coords[offset + i * 4 + 0] = ly;
                coords[offset + i * 4 + 1] = lx;
                coords[offset + i * 4 + 2] = hy;
                coords[offset + i * 4 + 3] = hx;
            }
        }
    };

    public abstract AffineTransform affineTransform();

    public abstract void transformPoints(int[] coords, int offset, int count);

    public abstract void transformRects(int[] coords, int offset, int count);

    public ManhattanOrientation concatenate(ManhattanOrientation other) {
        return concatenate[ordinal() * 8 + other.ordinal()];
    }
    private static final ManhattanOrientation[] concatenate = {
        /*R0*/R0, R90, R180, R270, MY, MYR90, MX, MXR90,
        /*R90*/ R90, R180, R270, R0, MYR90, MX, MXR90, MY,
        /*R180*/ R180, R270, R0, R90, MX, MXR90, MY, MYR90,
        /*R270*/ R270, R0, R90, R180, MXR90, MY, MYR90, MX,
        /*MY*/ MY, MXR90, MX, MYR90, R0, R270, R180, R90,
        /*MYR90*/ MYR90, MY, MXR90, MX, R90, R0, R270, R180,
        /*MX*/ MX, MYR90, MY, MXR90, R180, R90, R0, R270,
        /*MXR90*/ MXR90, MX, MYR90, MY, R270, R180, R90, R0
    };
}
