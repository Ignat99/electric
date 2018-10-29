/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: OrientationTest.java
 * Written by: Dmitry Nadezhin, Sun Microsystems.
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
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sun.electric.util.math;

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

import static org.junit.Assert.*;
import org.junit.Test;

/**
 * Unit test of Orientation
 */
public class OrientationTest {

    public static junit.framework.Test suite() {
        return new junit.framework.JUnit4TestAdapter(OrientationTest.class);
    }

    /**
     * Test of fromJava method, of class com.sun.electric.database.geometry.Orientation.
     */
    @Test
    public void testFromJava() {
        System.out.println("testFromJava");

        for (int iX = 0; iX <= 1; iX++) {
            boolean mirrorX = iX != 0;
            for (int iY = 0; iY <= 1; iY++) {
                boolean mirrorY = iY != 0;
                for (int iA = -200; iA < 3800; iA++) {
                    Orientation or = Orientation.fromJava(iA, mirrorX, mirrorY);
                    assertEquals((iA + 3600) % 3600, or.getAngle());
                    assertEquals(mirrorX, or.isXMirrored());
                    assertEquals(mirrorY, or.isYMirrored());

                    Orientation orC = Orientation.fromC(or.getCAngle(), or.isCTranspose());
                    assertEquals(or.getCAngle(), orC.getCAngle());
                    assertEquals(or.isCTranspose(), orC.isCTranspose());
                    assertEquals(or.getAngle(), mirrorX ? (orC.getAngle() + 1800) % 3600 : orC.getAngle());
                    assertEquals(false, orC.isXMirrored());
                    assertEquals(mirrorX ^ mirrorY, orC.isYMirrored());
                    assertSame(or.canonic(), orC);

                    assertTrue(or.pureRotate().equals(orC.pureRotate()));
                    AffineTransform af = AffineTransform.getScaleInstance(mirrorX ? -1 : 1, mirrorY ? -1 : 1);
                    af.rotate(iA * Math.PI / 1800.0);
//                    System.out.println("angle=" + iA + " mX=" + mirrorX + " mY=" + mirrorY);
                    assertTransformsEquals(af, or.pureRotate(), Math.ulp(1));
                }
            }
        }
    }

    /**
     * Test of fromC method, of class com.sun.electric.database.geometry.Orientation.
     */
    @Test
    public void testFromC() {
        System.out.println("testFromC");

        for (int iT = 0; iT <= 1; iT++) {
            boolean cTranspose = iT != 0;
            for (int cAngle = -200; cAngle < 3800; cAngle++) {
                Orientation or = Orientation.fromC(cAngle, cTranspose);
                assertEquals((cAngle + 3600) % 3600, or.getCAngle());
                assertEquals(cTranspose, or.isCTranspose());
                assertEquals(false, or.isXMirrored());
                assertEquals(cTranspose, or.isYMirrored());
                assertEquals(cTranspose ? (cAngle + 900) % 3600 : (cAngle + 3600) % 3600, or.getAngle());

                Orientation orJ = Orientation.fromJava(or.getAngle(), or.isXMirrored(), or.isYMirrored());
                assertEquals(or.getCAngle(), orJ.getCAngle());
                assertEquals(or.isCTranspose(), orJ.isCTranspose());
            }
        }
    }

    /**
     * Test of concatenate method, of class com.sun.electric.database.geometry.Orientation.
     */
    @Test
    public void testConcatenate() {
        System.out.println("testConcatenate");

        Orientation[] or = makeConcatTests();

        for (int i = 0; i < or.length; i++) {
            Orientation orI = or[i];
            AffineTransform afI = orI.pureRotate();
            for (int j = 0; j < or.length; j++) {
                Orientation orJ = or[j];
                AffineTransform afJ = orJ.pureRotate();
                Orientation orC = orI.concatenate(orJ);
                AffineTransform afC = (AffineTransform) afI.clone();
                afC.concatenate(afJ);
//                System.out.println("i=" + i + " j=" + j);
//                System.out.println("orI=" + orI + " afI=" + afI);
//                System.out.println("orJ=" + orJ + " afJ=" + afJ);
//                System.out.println("orC=" + orC + " afC=" + orC.trans);
//                System.out.println("" + afC);
                assertTransformsEquals(afC, orC.pureRotate(), Math.ulp(1));
            }
        }
    }

//    /**
//     * Test of getPreConcatenate method, of class com.sun.electric.database.geometry.Orientation.
//     */
//    public void testGetPreConcatenate() {
//        System.out.println("testGetPreConcatenate");
//
//        Orientation[] or = makeConcatTests();
//
//        for (int i = 0; i < or.length; i++) {
//            Orientation orI = or[i];
//            AffineTransform afI = orI.pureRotate();
//            for (int j = 0; j < or.length; j++) {
//                Orientation orJ = or[j];
//                AffineTransform afJ = orJ.pureRotate();
//                Orientation orC = orI.getPreConcatenate(orJ);
//                AffineTransform afC = (AffineTransform)afI.clone();
//                afC.preConcatenate(afJ);
//                assertEquals(afC, orC.pureRotate(), Math.ulp(1));
//            }
//        }
//    }
    private Orientation[] makeConcatTests() {
        Orientation[] or = new Orientation[36 * 4];
        for (int iX = 0; iX <= 1; iX++) {
            boolean mirrorX = iX != 0;
            for (int iY = 0; iY <= 1; iY++) {
                boolean mirrorY = iY != 0;
                for (int iA = 0; iA < 36; iA++) {
                    or[iA * 4 + iX * 2 + iY * 1] = Orientation.fromJava(iA * 100, mirrorX, mirrorY);
                }
            }
        }
        return or;
    }

    private void assertTransformsEquals(AffineTransform expected, AffineTransform actual, double delta) {
//        System.out.println("expected=" + expected);
//        System.out.println("actual=" + actual);
        double[] expectedM = new double[6];
        double[] actualM = new double[expectedM.length];
        expected.getMatrix(expectedM);
        actual.getMatrix(actualM);
        for (int i = 0; i < expectedM.length; i++) {
            assertEquals(expectedM[i], actualM[i], delta);
        }
    }

//    /**
//     * Test of getCAngle method, of class com.sun.electric.database.geometry.Orientation.
//     */
//    public void testGetCAngle() {
//        System.out.println("testGetCAngle");
//
//        // TODO add your test code below by replacing the default call to fail.
//        fail("The test case is empty.");
//    }
//
//    /**
//     * Test of isCTranspose method, of class com.sun.electric.database.geometry.Orientation.
//     */
//    public void testIsCTranspose() {
//        System.out.println("testIsCTranspose");
//
//        // TODO add your test code below by replacing the default call to fail.
//        fail("The test case is empty.");
//    }
//
//    /**
//     * Test of getAngle method, of class com.sun.electric.database.geometry.Orientation.
//     */
//    public void testGetAngle() {
//        System.out.println("testGetAngle");
//
//        // TODO add your test code below by replacing the default call to fail.
//        fail("The test case is empty.");
//    }
//
//    /**
//     * Test of isXMirrored method, of class com.sun.electric.database.geometry.Orientation.
//     */
//    public void testIsXMirrored() {
//        System.out.println("testIsXMirrored");
//
//        // TODO add your test code below by replacing the default call to fail.
//        fail("The test case is empty.");
//    }
//
//    /**
//     * Test of isYMirrored method, of class com.sun.electric.database.geometry.Orientation.
//     */
//    public void testIsYMirrored() {
//        System.out.println("testIsYMirrored");
//
//        // TODO add your test code below by replacing the default call to fail.
//        fail("The test case is empty.");
//    }
//
    @Test
    public void testManhatten() {
        System.out.println("testManhatten");
        int[] src = {-7, 1, 3, 9};
        int[] dst = new int[4];

        for (Orientation orient : allManhatten()) {
            assertTrue(orient.isManhattan());
            AffineTransform pureRotate = orient.pureRotate();
            System.arraycopy(src, 0, dst, 0, 4);
            orient.transformPoints(2, dst);
            for (int i = 0; i < 2; i++) {
                Point2D p = new Point2D.Double(src[i * 2], src[i * 2 + 1]);
                pureRotate.transform(p, p);
                assertEquals(p.getX(), (double) dst[i * 2], 0);
                assertEquals(p.getY(), (double) dst[i * 2 + 1], 0);
            }
            Rectangle2D rect = new Rectangle2D.Double();
            orient.rectangleBounds(src[0], src[1], src[2], src[3], 0, 0, rect);
            assertEquals(rect.getX(), Math.min(dst[0], dst[2]), 0);
            assertEquals(rect.getY(), Math.min(dst[1], dst[3]), 0);
            assertEquals(rect.getWidth(), Math.abs(dst[0] - dst[2]), 0);
            assertEquals(rect.getHeight(), Math.abs(dst[1] - dst[3]), 0);
            System.arraycopy(src, 0, dst, 0, 4);
            orient.rectangleBounds(dst);
            assertEquals(dst[0], rect.getMinX(), 0);
            assertEquals(dst[1], rect.getMinY(), 0);
            assertEquals(dst[2], rect.getMaxX(), 0);
            assertEquals(dst[3], rect.getMaxY(), 0);
        }
    }

    private Orientation[] allManhatten() {
        return new Orientation[]{
                    Orientation.IDENT,
                    Orientation.R,
                    Orientation.RR,
                    Orientation.RRR,
                    Orientation.X,
                    Orientation.XR,
                    Orientation.XRR,
                    Orientation.XRRR,
                    Orientation.Y,
                    Orientation.YR,
                    Orientation.YRR,
                    Orientation.YRRR,
                    Orientation.XY,
                    Orientation.XYR,
                    Orientation.XYRR,
                    Orientation.XYRRR
                };
    }

    /**
     * Test of toJelibString method, of class com.sun.electric.database.geometry.Orientation.
     */
    @Test
    public void testToJelibString() {
        System.out.println("testToJelibString");

        assertEquals("", Orientation.fromJava(0, false, false).toJelibString());
        assertEquals("R", Orientation.fromJava(900, false, false).toJelibString());
        assertEquals("RR", Orientation.fromJava(1800, false, false).toJelibString());
        assertEquals("RRR", Orientation.fromJava(2700, false, false).toJelibString());
        assertEquals("X", Orientation.fromJava(0, true, false).toJelibString());
        assertEquals("XR", Orientation.fromJava(900, true, false).toJelibString());
        assertEquals("XRR", Orientation.fromJava(1800, true, false).toJelibString());
        assertEquals("XRRR", Orientation.fromJava(2700, true, false).toJelibString());
        assertEquals("Y", Orientation.fromJava(0, false, true).toJelibString());
        assertEquals("YR", Orientation.fromJava(900, false, true).toJelibString());
        assertEquals("YRR", Orientation.fromJava(1800, false, true).toJelibString());
        assertEquals("YRRR", Orientation.fromJava(2700, false, true).toJelibString());
        assertEquals("XY", Orientation.fromJava(0, true, true).toJelibString());
        assertEquals("XYR", Orientation.fromJava(900, true, true).toJelibString());
        assertEquals("XYRR", Orientation.fromJava(1800, true, true).toJelibString());
        assertEquals("XYRRR", Orientation.fromJava(2700, true, true).toJelibString());
    }

    /**
     * Test of toString method, of class com.sun.electric.database.geometry.Orientation.
     */
    @Test
    public void testToString() {
        System.out.println("testToString");

        assertEquals("", Orientation.fromJava(0, false, false).toString());
        assertEquals("R", Orientation.fromJava(900, false, false).toString());
        assertEquals("RR", Orientation.fromJava(1800, false, false).toString());
        assertEquals("RRR", Orientation.fromJava(2700, false, false).toString());
        assertEquals("X", Orientation.fromJava(0, true, false).toString());
        assertEquals("XR", Orientation.fromJava(900, true, false).toString());
        assertEquals("XRR", Orientation.fromJava(1800, true, false).toString());
        assertEquals("XRRR", Orientation.fromJava(2700, true, false).toString());
        assertEquals("Y", Orientation.fromJava(0, false, true).toString());
        assertEquals("YR", Orientation.fromJava(900, false, true).toString());
        assertEquals("YRR", Orientation.fromJava(1800, false, true).toString());
        assertEquals("YRRR", Orientation.fromJava(2700, false, true).toString());
        assertEquals("XY", Orientation.fromJava(0, true, true).toString());
        assertEquals("XYR", Orientation.fromJava(900, true, true).toString());
        assertEquals("XYRR", Orientation.fromJava(1800, true, true).toString());
        assertEquals("XYRRR", Orientation.fromJava(2700, true, true).toString());
    }
}
