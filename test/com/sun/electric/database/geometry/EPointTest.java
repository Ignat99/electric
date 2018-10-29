/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: EPointTest.java
 * Written by: Dmitry Nadezhin, Sun Microsystems.
 *
 * Copyright (c) 2006, Static Free Software. All rights reserved.
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
package com.sun.electric.database.geometry;

import java.awt.geom.Point2D;

import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import com.sun.electric.util.math.DBMath;

/**
 * Unit test of EPoint
 */
public class EPointTest {

    private EPoint p0;

    @Before
    public void setUp() throws Exception {
        p0 = EPoint.fromGrid(10, 20);
    }

    public static junit.framework.Test suite() {
        return new junit.framework.JUnit4TestAdapter(EPointTest.class);
    }

    /**
     * Test of fromLambda method, of class com.sun.electric.database.geometry.EPoint.
     */
    @Test
    public void testFromLambda() {
        System.out.println("fromLambda");
        assertEquals(p0, EPoint.fromLambda(10 / DBMath.GRID, 20 / DBMath.GRID));
    }

    /**
     * Test of fromGrid method, of class com.sun.electric.database.geometry.EPoint.
     */
    @Test
    public void testFromGrid() {
        System.out.println("fromGrid");
        assertSame(EPoint.ORIGIN, EPoint.fromGrid(0, 0));
    }

    /**
     * Test of snap method, of class com.sun.electric.database.geometry.EPoint.
     */
    @Test
    public void testSnap() {
        System.out.println("snap");
        assertSame(p0, EPoint.snap(p0));
    }

    /**
     * Test of getX method, of class com.sun.electric.database.geometry.EPoint.
     */
    @Test
    public void testGetX() {
        System.out.println("getX");
        assertEquals(10 / DBMath.GRID, p0.getX(), 0);
    }

    /**
     * Test of getY method, of class com.sun.electric.database.geometry.EPoint.
     */
    @Test
    public void testGetY() {
        System.out.println("getY");
        assertEquals(20 / DBMath.GRID, p0.getY(), 0);
    }

    /**
     * Test of getLambdaX method, of class com.sun.electric.database.geometry.EPoint.
     */
    @Test
    public void testGetLambdaX() {
        System.out.println("getLambdaX");
        assertEquals(10 / DBMath.GRID, p0.getLambdaX(), 0);
    }

    /**
     * Test of getLambdaY method, of class com.sun.electric.database.geometry.EPoint.
     */
    @Test
    public void testGetLambdaY() {
        System.out.println("getLambdaY");
        assertEquals(20 / DBMath.GRID, p0.getLambdaY(), 0);
    }

    /**
     * Test of getGridX method, of class com.sun.electric.database.geometry.EPoint.
     */
    @Test
    public void testGetGridX() {
        System.out.println("getGridX");
        assertEquals(10L, p0.getGridX());
    }

    /**
     * Test of getGridY method, of class com.sun.electric.database.geometry.EPoint.
     */
    @Test
    public void testGetGridY() {
        System.out.println("getGridY");
        assertEquals(20L, p0.getGridY());
    }

    /**
     * Test of setLocation method, of class com.sun.electric.database.geometry.EPoint.
     */
    @Test(expected = UnsupportedOperationException.class)
    public void testSetLocation() {
        System.out.println("setLocation");
        p0.setLocation(1, 2);
    }

    /**
     * Test of lambdaMutable method, of class com.sun.electric.database.geometry.EPoint.
     */
    @Test
    public void testLambdaMutable() {
        System.out.println("lambdaMutable");
        Point2D.Double result = p0.lambdaMutable();
        assertTrue(result instanceof Point2D.Double);
        assertEquals(new Point2D.Double(10 / DBMath.GRID, 20 / DBMath.GRID), result);
    }

    /**
     * Test of gridMutable method, of class com.sun.electric.database.geometry.EPoint.
     */
    @Test
    public void testGridMutable() {
        System.out.println("gridMutable");
        Point2D.Double result = p0.gridMutable();
        assertTrue(result instanceof Point2D.Double);
        assertEquals(new Point2D.Double(10, 20), result);
    }

    /**
     * Test of lambdaDistance method, of class com.sun.electric.database.geometry.EPoint.
     */
    @Test
    public void testLambdaDistance() {
        System.out.println("lambdaDistance");
        assertEquals(Math.sqrt(500) / DBMath.GRID, p0.lambdaDistance(EPoint.ORIGIN), 0);
    }

    /**
     * Test of gridDistance method, of class com.sun.electric.database.geometry.EPoint.
     */
    @Test
    public void testGridDistance() {
        System.out.println("gridDistance");
        assertEquals(Math.sqrt(500), p0.gridDistance(EPoint.ORIGIN), 0);
    }

    /**
     * Test of toString method, of class com.sun.electric.database.geometry.EPoint.
     */
    @Test
    public void testToString() {
        System.out.println("toString");
        assertEquals("EPointInt[" + (10 / DBMath.GRID) + ", " + (20 / DBMath.GRID) + "]", p0.toString());
    }
}
