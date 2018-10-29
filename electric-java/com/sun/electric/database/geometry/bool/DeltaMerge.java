/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: DeltaMerge.java
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

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 *
 */
public class DeltaMerge {

    private static class Segment {
        private Segment next;
        private int y; // begin of the segment
        private int val; // height at points below y
    }

    private static final Segment segLast = new Segment();
    static {
        segLast.y = Integer.MAX_VALUE;
    }

    private Segment segPool;
    private Segment chain;
    private int[] inpA = new int[1];
    private int inpC;
    private int[] outA = new int[1];
    private int outC;

    private PointsSorter ps;

    private int x;

    public DeltaMerge() {
        chain = new Segment();
        chain.next = segLast;
        chain.y = Integer.MIN_VALUE;
    }

    public int loop(PointsSorter ps, DataOutputStream out) {
        try {
        this.ps = ps;
        int totalOutPoints = 0;
        while (getLine()) {
            scanLine();
            totalOutPoints += outC;
            printOut(out);
            printSegments();
            checkSegments();
        }
        out.writeBoolean(false);
        return totalOutPoints;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void printOut(DataOutputStream out) throws IOException {
        if (outC == 0) {
            return;
        }
        out.writeBoolean(true);
//        System.out.print("x=" + DBMath.gridToLambda(x));
        out.writeInt(x);
        out.writeInt(outC);
        for (int i = 0; i < outC; i++) {
            int outVal = outA[i];
            int y = outVal >> 1;
            boolean df = (outVal & 1) != 0;
//            System.out.print(" " + DBMath.gridToLambda(y) + (df?"^":"_"));
            out.writeInt(outVal);
        }
//        System.out.println();
    }

    private boolean getLine() {
        PointsSorter.ScanLine scanLine = ps.fix();
        if  (scanLine == null) {
            return false;
        }
        x = scanLine.x;
        inpA = scanLine.y;
        inpC = scanLine.len;
        return true;
    }

    private void printSegments() {
        for (Segment cp = chain; cp.next != segLast; cp = cp.next) {
//            System.out.println("y="+cp.y+" val="+cp.val);
            assert cp.y < cp.next.y;
            assert cp.val >= 0;
        }
    }

    private void checkSegments() {
        assert chain.y == Integer.MIN_VALUE;
        assert chain.val == 0;
        for (Segment cp = chain; cp.next != segLast; cp = cp.next) {
            assert cp.y < cp.next.y;
            assert cp.val >= 0;
        }
        assert segLast.y == Integer.MAX_VALUE;
        assert segLast.val == 0;
    }

    private void scanLine() {
        assert inpC > 0;
        int inpStep = 0;
        int outStep = 0;
        Segment cp = chain;
        resetOut();
        for (int inpPos = 0; inpPos < inpC; inpPos ++) {
            int y = inpA[inpPos*2 + 0];
            int df = inpA[inpPos*2 + 1];
            assert df != 0;
            assert cp.y < y;

            if (inpStep == 0) {
                while (cp.next.y < y) {
                    cp = cp.next;
                }
            } else {
                while (cp.next.y < y) {
                    Segment p = cp.next;
                    int oldO = p.val == 0 ? 0 : 1;
                    p.val += inpStep;
                    int newO = p.val == 0 ? 0 : 1;
                    int newOutStep = newO - oldO;
                    if (newOutStep != outStep) {
                        putPointOut(p.y, newOutStep - outStep);
                        outStep = newOutStep;
                    }
                    cp = p;
                }
            }
            assert cp.y < y && y <= cp.next.y;
            Segment p;
			if (cp.next.y > y) {
                p = newSegment(cp.next);
				p.y = y;
                p.val = cp.val - inpStep;
                cp.next = p;
			} else {
                p = cp.next;
            }
			inpStep += df;
            assert p.y == y;
            int oldO = p.val == 0 ? 0 : 1;
            p.val += inpStep;
            int newO = p.val == 0 ? 0 : 1;
            int newOutStep = newO - oldO;
            if (newOutStep != outStep) {
                putPointOut(y, newOutStep - outStep);
                outStep = newOutStep;
			}
			if (cp.val == p.val) {
				cp.next = p.next;
                dispSegment(p);
			} else {
                cp = p;
            }
            assert cp.y <= y;
		}
		assert inpStep == 0;
    }

    private void resetOut() {
        outC = 0;
    }

    private void putPointOut(int y, int val) {
        if (outC >= outA.length) {
            int[] newOutA = new int[outA.length*2];
            System.arraycopy(outA, 0, newOutA, 0, outA.length);
            outA = newOutA;
        }
        if (val == +1) {
            outA[outC++] = (y << 1) | 1;
        } else if (val == -1) {
            outA[outC++] = (y << 1);
        } else if (val == +2) {
            outA[outC++] = (y << 1) | 1;
            putPointOut(y, +1);
        } else if (val == -2) {
            outA[outC++] = (y << 1);
            putPointOut(y, -1);
        } else {
            throw new AssertionError();
        }
    }

    private Segment newSegment(Segment next) {
        Segment result;
        if (segPool == null) {
            result = new Segment();
        } else {
            result = segPool;
            segPool = segPool.next;
        }
        result.next = next;
        return result;
    }

    private void dispSegment(Segment p) {
        p.next = segPool;
        segPool = p;
    }
}
