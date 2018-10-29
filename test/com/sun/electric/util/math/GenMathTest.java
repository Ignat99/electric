/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: GenMathTest.java
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

import java.awt.geom.Point2D;
import java.math.BigDecimal;

import java.math.BigInteger;
import java.math.RoundingMode;
import org.junit.Assert;
import static org.junit.Assert.*;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Unit tets of GenMath
 */
public class GenMathTest
{

    private static final double GRID = FixpCoord.GRIDS_IN_LAMBDA;

    private static double gridToLambda(double gridValue)
    {
        return gridValue / GRID;
    }

    private static long lambdaToGrid(double lambdaValue)
    {
        double x = lambdaValue * GRID;
        return (long)(x >= 0 ? x + GenMath.HALF : x - GenMath.HALF);
    }

    private static long lambdaToSizeGrid(double lambdaValue)
    {
        double x = lambdaValue * (GRID / 2);
        long l = (long)(x >= 0 ? x + GenMath.HALF : x - GenMath.HALF);
        return l << 1;
    }

    private static double round(double lambdaValue)
    {
        double x = lambdaValue * GRID;
        long l = (long)(x >= 0 ? x + GenMath.HALF : x - GenMath.HALF);
        return l / GRID;
    }

    private static double roundShapeCoord(double v)
    {
        double LARGE = 1L << 32;
        return v >= 0 ? (v + LARGE) - LARGE : (v - LARGE) + LARGE;
    }
    private double[] doubleValues;
    private long[] longValues;
    private int[] intValues;

    public static junit.framework.Test suite()
    {
        return new junit.framework.JUnit4TestAdapter(GenMathTest.class);
    }

    // public static class MutableIntegerTest extends TestCase {
    //
    // public MutableIntegerTest(java.lang.String testName) {
    //
    // super(testName);
    // }
    //
    // protected void setUp() throws Exception {
    // }
    //
    // protected void tearDown() throws Exception {
    // }
    //
    // public static Test suite() {
    // TestSuite suite = new TestSuite(MutableIntegerTest.class);
    //
    // return suite;
    // }
    //
    // /**
    // * Test of setValue method, of class
    // com.sun.electric.database.geometry.GenMath.MutableInteger.
    // */
    // public void testSetValue() {
    // System.out.println("testSetValue");
    //
    // // TODO add your test code below by replacing the default call to fail.
    // fail("The test case is empty.");
    // }
    //
    // /**
    // * Test of increment method, of class
    // com.sun.electric.database.geometry.GenMath.MutableInteger.
    // */
    // public void testIncrement() {
    // System.out.println("testIncrement");
    //
    // // TODO add your test code below by replacing the default call to fail.
    // fail("The test case is empty.");
    // }
    //
    // /**
    // * Test of intValue method, of class
    // com.sun.electric.database.geometry.GenMath.MutableInteger.
    // */
    // public void testIntValue() {
    // System.out.println("testIntValue");
    //
    // // TODO add your test code below by replacing the default call to fail.
    // fail("The test case is empty.");
    // }
    //
    // /**
    // * Test of toString method, of class
    // com.sun.electric.database.geometry.GenMath.MutableInteger.
    // */
    // public void testToString() {
    // System.out.println("testToString");
    //
    // // TODO add your test code below by replacing the default call to fail.
    // fail("The test case is empty.");
    // }
    // }
    //
    //
    // public static class MutableDoubleTest extends TestCase {
    //
    // public MutableDoubleTest(java.lang.String testName) {
    //
    // super(testName);
    // }
    //
    // protected void setUp() throws Exception {
    // }
    //
    // protected void tearDown() throws Exception {
    // }
    //
    // public static Test suite() {
    // TestSuite suite = new TestSuite(MutableDoubleTest.class);
    //
    // return suite;
    // }
    //
    // /**
    // * Test of setValue method, of class
    // com.sun.electric.database.geometry.GenMath.MutableDouble.
    // */
    // public void testSetValue() {
    // System.out.println("testSetValue");
    //
    // // TODO add your test code below by replacing the default call to fail.
    // fail("The test case is empty.");
    // }
    //
    // /**
    // * Test of doubleValue method, of class
    // com.sun.electric.database.geometry.GenMath.MutableDouble.
    // */
    // public void testDoubleValue() {
    // System.out.println("testDoubleValue");
    //
    // // TODO add your test code below by replacing the default call to fail.
    // fail("The test case is empty.");
    // }
    //
    // /**
    // * Test of toString method, of class
    // com.sun.electric.database.geometry.GenMath.MutableDouble.
    // */
    // public void testToString() {
    // System.out.println("testToString");
    //
    // // TODO add your test code below by replacing the default call to fail.
    // fail("The test case is empty.");
    // }
    // }
    //
    //
    // public static Test suite() {
    // TestSuite suite = new TestSuite(GenMathTest.class);
    //
    // return suite;
    // }
    //
    // /**
    // * Test of addToBag method, of class
    // com.sun.electric.database.geometry.GenMath.
    // */
    // public void testAddToBag() {
    // System.out.println("testAddToBag");
    //
    // // TODO add your test code below by replacing the default call to fail.
    // fail("The test case is empty.");
    // }
    //
    // /**
    // * Test of countInBag method, of class
    // com.sun.electric.database.geometry.GenMath.
    // */
    // public void testCountInBag() {
    // System.out.println("testCountInBag");
    //
    // // TODO add your test code below by replacing the default call to fail.
    // fail("The test case is empty.");
    // }
    //
    // /**
    // * Test of objectsReallyEqual method, of class
    // com.sun.electric.database.geometry.GenMath.
    // */
    // public void testObjectsReallyEqual() {
    // System.out.println("testObjectsReallyEqual");
    //
    // // TODO add your test code below by replacing the default call to fail.
    // fail("The test case is empty.");
    // }
    /**
     * Test of figureAngle methods, of class
     * com.sun.electric.database.geometry.GenMath.
     */
    @Test
    public void testFigureAngle()
    {
        System.out.println("testFigureAngle");

        assertEquals(0, GenMath.figureAngle(0.0, 0.0));
        assertEquals(0, GenMath.figureAngle(0.0, -0.0));
        assertEquals(0, GenMath.figureAngle(-0.0, 0.0));
        assertEquals(0, GenMath.figureAngle(-0.0, -0.0));
        assertEquals(0, GenMath.figureAngle(Double.NaN, 0));
        assertEquals(0, GenMath.figureAngle(Double.NaN, 100));
        assertEquals(0, GenMath.figureAngle(Double.NaN, -100));
        assertEquals(0, GenMath.figureAngle(0, Double.NaN));
        assertEquals(0, GenMath.figureAngle(100, Double.NaN));
        assertEquals(0, GenMath.figureAngle(-100, Double.NaN));

        assertEquals(0, GenMath.figureAngle(Double.POSITIVE_INFINITY, Double.MAX_VALUE));
        assertEquals(0, GenMath.figureAngle(Double.POSITIVE_INFINITY, -Double.MAX_VALUE));
        assertEquals(1800, GenMath.figureAngle(Double.NEGATIVE_INFINITY, Double.MAX_VALUE));
        assertEquals(1800, GenMath.figureAngle(Double.NEGATIVE_INFINITY, -Double.MAX_VALUE));
        assertEquals(900, GenMath.figureAngle(Double.MAX_VALUE, Double.POSITIVE_INFINITY));
        assertEquals(900, GenMath.figureAngle(-Double.MAX_VALUE, Double.POSITIVE_INFINITY));
        assertEquals(2700, GenMath.figureAngle(Double.MAX_VALUE, Double.NEGATIVE_INFINITY));
        assertEquals(2700, GenMath.figureAngle(-Double.MAX_VALUE, Double.NEGATIVE_INFINITY));

        assertEquals(450, GenMath.figureAngle(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY));
        assertEquals(1350, GenMath.figureAngle(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY));
        assertEquals(2250, GenMath.figureAngle(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY));
        assertEquals(3150, GenMath.figureAngle(Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY));

        double r1 = 0.1;
        assertEquals(0, GenMath.figureAngle(r1, 0));
        assertEquals(450, GenMath.figureAngle(r1, r1));
        assertEquals(900, GenMath.figureAngle(0, r1));
        assertEquals(1350, GenMath.figureAngle(-r1, r1));
        assertEquals(1800, GenMath.figureAngle(-r1, 0));
        assertEquals(2250, GenMath.figureAngle(-r1, -r1));
        assertEquals(2700, GenMath.figureAngle(0, -r1));
        assertEquals(3150, GenMath.figureAngle(r1, -r1));

        double x0 = -0.08;
        double y0 = -0.07;
        Point2D p0 = new Point2D.Double(x0, y0);
        double r2 = Double.MAX_VALUE;
        for (int i = 0; i < 3600; i++)
        {
            double a = i * Math.PI / 1800;
            assertEquals(i, GenMath.figureAngle(r1 * Math.cos(a), r1 * Math.sin(a)));
            assertEquals(i, GenMath.figureAngle(r2 * Math.cos(a), r2 * Math.sin(a)));
            assertEquals(i, GenMath.figureAngle(p0, new Point2D.Double(x0 + r1 * Math.cos(a), y0 + r1
                * Math.sin(a))));
        }
    }

    /**
     * Test of isMultiple method, of class
     * com.sun.electric.database.geometry.GenMath.
     */
    @Test
    public void testIsMultiple()
    {
        System.out.println("isMultiple");
        long[] divisors = new long[]
        {
            0, 1, -1, 2, -2, 3, -3, 4, -4, Long.MAX_VALUE, Long.MIN_VALUE
        };
        long[] xs = new long[]
        {
            0, 1, -1, 2, -2, 3, -3, 4, -4, 5, -5, 6, -6, Long.MAX_VALUE, Long.MIN_VALUE
        };
        for (long divisor : divisors)
        {
            for (long x : xs)
            {
                testMultiple(x, divisor);
            }
        }
    }

    private void testMultiple(long x, long divisor)
    {
        assertEquals(isMultiple(x, divisor), GenMath.isMultiple(x, divisor));
        for (RoundingMode rm : RoundingMode.values())
        {
            BigInteger expected = roundToMultiple(BigInteger.valueOf(x), BigInteger.valueOf(divisor), rm);
            if (expected != null && expected.bitLength() >= Long.SIZE)
            {
                expected = null;
            }
            if (expected != null)
            {
                assertEquals(expected, BigInteger.valueOf(GenMath.roundToMultiple(x, divisor, rm)));
                switch (rm)
                {
                    case CEILING:
                        assertEquals(expected, BigInteger.valueOf(GenMath.roundToMultipleCeiling(x, divisor)));
                        break;
                    case FLOOR:
                        assertEquals(expected, BigInteger.valueOf(GenMath.roundToMultipleFloor(x, divisor)));
                        break;
                    case HALF_EVEN:
                        assertEquals(expected, BigInteger.valueOf(GenMath.roundToMultiple(x, divisor)));
                        break;
                }
            } else
            {
                try
                {
                    GenMath.roundToMultiple(x, divisor, rm);
                    fail("ArithmeticException expected");
                } catch (ArithmeticException e)
                {
                }
                try
                {
                    switch (rm)
                    {
                        case CEILING:
                            GenMath.roundToMultipleCeiling(x, divisor);
                            fail("ArithmeticException expected");
                            break;
                        case FLOOR:
                            GenMath.roundToMultipleFloor(x, divisor);
                            fail("ArithmeticException expected");
                            break;
                        case HALF_EVEN:
                            GenMath.roundToMultiple(x, divisor);
                            fail("ArithmeticException expected");
                            break;
                    }
                } catch (ArithmeticException e)
                {
                }
            }
        }
    }

    private boolean isMultiple(long x, long divisor)
    {
        return divisor == 0 ? x == 0 : x % divisor == 0;
    }

    private BigInteger roundToMultiple(BigInteger x, BigInteger divisor, RoundingMode rm)
    {
        if (divisor.signum() == 0)
        {
            if (rm.equals(RoundingMode.UNNECESSARY) && x.signum() != 0)
            {
                return null;
            }
            return BigInteger.ZERO;
        }
        divisor = divisor.abs();
        BigInteger[] qr = x.abs().divideAndRemainder(divisor);
        BigInteger q = qr[0];
        BigInteger r = qr[1];
        int cmpHalf = r.shiftLeft(1).compareTo(divisor);
        assert r.signum() >= 0 && r.compareTo(divisor) < 0;
        boolean correction = false;
        switch (rm)
        {
            case UP:
                correction = r.signum() > 0;
                break;
            case DOWN:
                correction = false;
                break;
            case CEILING:
                correction = x.signum() > 0 && r.signum() > 0;
                break;
            case FLOOR:
                correction = x.signum() < 0 && r.signum() > 0;
                break;
            case HALF_UP:
                correction = cmpHalf >= 0;
                break;
            case HALF_DOWN:
                correction = cmpHalf > 0;
                break;
            case HALF_EVEN:
                correction = cmpHalf > 0 || cmpHalf == 0 && q.testBit(0);
                break;
            default:
                if (r.signum() != 0)
                {
                    return null;
                }
        }
        BigInteger result = q.multiply(divisor);
        if (correction)
        {
            result = result.add(divisor);
        }
        if (x.signum() < 0)
        {
            result = result.negate();
        }
        return result;
    }

    /**
     * Test of round method, of class
     * com.sun.electric.database.geometry.GenMath.
     */
    @Test
    public void testRoundHalf()
    {
        System.out.println("testRoundHalf");
        assertEquals(0L, GenMath.roundLong(Double.NaN));
        assertEquals(Long.MIN_VALUE, GenMath.roundLong(Double.NEGATIVE_INFINITY));
        assertEquals(Long.MAX_VALUE, GenMath.roundLong(Double.POSITIVE_INFINITY));
        assertEquals(Long.MIN_VALUE, GenMath.roundLong(-Double.MAX_VALUE));
        assertEquals(Long.MAX_VALUE, GenMath.roundLong(Double.MAX_VALUE));

        assertEquals(Long.MIN_VALUE, GenMath.floorLong(Double.NaN));
        assertEquals(Long.MIN_VALUE, GenMath.floorLong(Double.NEGATIVE_INFINITY));
        assertEquals(Long.MAX_VALUE, GenMath.floorLong(Double.POSITIVE_INFINITY));
        assertEquals(Long.MIN_VALUE, GenMath.floorLong(-Double.MAX_VALUE));
        assertEquals(Long.MAX_VALUE, GenMath.floorLong(Double.MAX_VALUE));

        assertEquals(Long.MAX_VALUE, GenMath.ceilLong(Double.NaN));
        assertEquals(Long.MIN_VALUE, GenMath.ceilLong(Double.NEGATIVE_INFINITY));
        assertEquals(Long.MAX_VALUE, GenMath.ceilLong(Double.POSITIVE_INFINITY));
        assertEquals(Long.MIN_VALUE, GenMath.ceilLong(-Double.MAX_VALUE));
        assertEquals(Long.MAX_VALUE, GenMath.ceilLong(Double.MAX_VALUE));

        assertEquals(0, GenMath.roundInt(Double.NaN));
        assertEquals(Integer.MIN_VALUE, GenMath.roundInt(Double.NEGATIVE_INFINITY));
        assertEquals(Integer.MAX_VALUE, GenMath.roundInt(Double.POSITIVE_INFINITY));
        assertEquals(Integer.MIN_VALUE, GenMath.roundInt(-Double.MAX_VALUE));
        assertEquals(Integer.MAX_VALUE, GenMath.roundInt(Double.MAX_VALUE));

        testRound2(0.0);
        testRound2(Double.MIN_VALUE);
        double x = 1.0;
        for (int i = 0; i <= Long.SIZE; i++)
        {
            testRound2(x - 1.0);
            testRound2(x - 0.5);
            testRound2(x);
            testRound2(x + 0.5);
            testRound2(x + 1.0);
            x *= 2;
        }
    }

    private void testRound2(double x)
    {
        testRound3(x);
        testRound3(-x);
    }

    private void testRound3(double x)
    {
        checkRound(MutableInterval.prev(x));
        checkRound(x);
        checkRound(MutableInterval.next(x));

        x = x / GRID;
        checkRound(MutableInterval.prev(x));
        checkRound(x);
        checkRound(MutableInterval.next(x));
    }

    private void checkRound(double x)
    {
        checkRoundLong(x);
        checkFloorLong(x);
        checkCeilLong(x);
        checkRoundInt(x);
        checkFloorInt(x);
        checkCeilInt(x);

        assertEquals(x / GRID, gridToLambda(x), 0);
        assertEquals(GenMath.roundLong(x * GRID), lambdaToGrid(x));
        assertEquals(GenMath.roundLong(x * (GRID / 2)) * 2, lambdaToSizeGrid(x));
        assertEquals(GenMath.roundLong(x * GRID) / GRID, round(x), 0);
    }

    private void checkRoundLong(double x)
    {
        long l = GenMath.roundLong(x);
        BigDecimal bx = new BigDecimal(x);
        BigDecimal bl = new BigDecimal(l);
        int cl = bx.compareTo(bl.add(new BigDecimal(-0.5)));
        int cr = bx.compareTo(bl.add(new BigDecimal(0.5)));
        if (l != Long.MIN_VALUE)
        {
            assertTrue((cl & 1) == 0 ? cl >= 0 : cl > 0);
        }
        if (l != Long.MAX_VALUE)
        {
            assertTrue((cr & 1) == 0 ? cr <= 0 : cr < 0);
        }
    }

    private void checkFloorLong(double x)
    {
        long l = GenMath.floorLong(x);
        BigDecimal bx = new BigDecimal(x);
        BigDecimal bl = new BigDecimal(l);
        int cl = bx.compareTo(bl);
        int cr = bx.compareTo(bl.add(new BigDecimal(1.0)));
        if (l != Long.MIN_VALUE)
        {
            assertTrue(cl >= 0);
        }
        if (l != Long.MAX_VALUE)
        {
            assertTrue(cr < 0);
        }
    }

    private void checkCeilLong(double x)
    {
        long l = GenMath.ceilLong(x);
        BigDecimal bx = new BigDecimal(x);
        BigDecimal bl = new BigDecimal(l);
        int cl = bx.compareTo(bl.add(new BigDecimal(-1.0)));
        int cr = bx.compareTo(bl);
        if (l != Long.MIN_VALUE)
        {
            assertTrue(cl > 0);
        }
        if (l != Long.MAX_VALUE)
        {
            assertTrue(cr <= 0);
        }
    }

    private void checkRoundInt(double x)
    {
        int l = GenMath.roundInt(x);
        BigDecimal bx = new BigDecimal(x);
        BigDecimal bl = new BigDecimal(l);
        int cl = bx.compareTo(bl.add(new BigDecimal(-0.5)));
        int cr = bx.compareTo(bl.add(new BigDecimal(0.5)));
        if (l != Integer.MIN_VALUE)
        {
            assertTrue((cl & 1) == 0 ? cl >= 0 : cl > 0);
        }
        if (l != Integer.MAX_VALUE)
        {
            assertTrue((cr & 1) == 0 ? cr <= 0 : cr < 0);
        }
    }

    private void checkFloorInt(double x)
    {
        int l = GenMath.floorInt(x);
        BigDecimal bx = new BigDecimal(x);
        BigDecimal bl = new BigDecimal(l);
        int cl = bx.compareTo(bl);
        int cr = bx.compareTo(bl.add(new BigDecimal(1.0)));
        if (l != Integer.MIN_VALUE)
        {
            assertTrue(cl >= 0);
        }
        if (l != Integer.MAX_VALUE)
        {
            assertTrue(cr < 0);
        }
    }

    private void checkCeilInt(double x)
    {
        int l = GenMath.ceilInt(x);
        BigDecimal bx = new BigDecimal(x);
        BigDecimal bl = new BigDecimal(l);
        int cl = bx.compareTo(bl.add(new BigDecimal(-1.0)));
        int cr = bx.compareTo(bl);
        if (l != Integer.MIN_VALUE)
        {
            assertTrue(cl > 0);
        }
        if (l != Integer.MAX_VALUE)
        {
            assertTrue(cr <= 0);
        }
    }

    @Ignore
    @Test
    public void benchRound()
    {
        System.out.println("benchRound");
        int len = 1000;
        doubleValues = new double[len];
        longValues = new long[len];
        intValues = new int[len];
        for (int i = 0; i < len; i++)
        {
            doubleValues[i] = i;
            longValues[i] = i;
            intValues[i] = i;
            // if ((i & 1) != 0)
            // doubleValues[i] = -doubleValues[i];
        }
        BenchLoop[] benches =
        {
            new BenchLoop("DoubleSum")
            {

                long loop()
                {
                    double[] values = GenMathTest.this.doubleValues;
                    double s = 0;
                    for (double x : values)
                    {
                        s = s + x;
                    }
                    return (long)s;
                }
            },
            new BenchLoop("DoubleMult")
            {

                long loop()
                {
                    double[] values = GenMathTest.this.doubleValues;
                    double s = 0;
                    for (double x : values)
                    {
                        s += x * GRID;
                    }
                    return (long)s;
                }
            },
            new BenchLoop("DoubleDiv")
            {

                long loop()
                {
                    double[] values = GenMathTest.this.doubleValues;
                    double s = 0;
                    for (double x : values)
                    {
                        s = s + x / GRID;
                    }
                    return (long)s;
                }
            },
            new BenchLoop("Math.rint")
            {

                long loop()
                {
                    double[] values = GenMathTest.this.doubleValues;
                    double s = 0;
                    for (double x : values)
                    {
                        s = s + Math.rint(x);
                    }
                    return (long)s;
                }
            },
            new BenchLoop("DBMath.rint")
            {

                long loop()
                {
                    double[] values = GenMathTest.this.doubleValues;
                    double s = 0;
                    for (double x : values)
                    {
                        s = s + GenMath.rint(x);
                    }
                    return (long)s;
                }
            },
            new BenchLoop("long DBMath.rint")
            {

                long loop()
                {
                    double[] values = GenMathTest.this.doubleValues;
                    long s = 0;
                    for (double x : values)
                    {
                        s = s + (long)GenMath.rint(x);
                    }
                    return s;
                }
            },
            new BenchLoop("DBMath.roundShapeCoord")
            {

                long loop()
                {
                    double[] values = GenMathTest.this.doubleValues;
                    double s = 0;
                    for (double x : values)
                    {
                        s = s + roundShapeCoord(x);
                    }
                    return (long)s;
                }
            },
            null,
            new BenchLoop("DBMathRound")
            {

                long loop()
                {
                    double[] values = GenMathTest.this.doubleValues;
                    double s = 0;
                    for (double x : values)
                    {
                        s = s + round(x);
                    }
                    return (long)s;
                }
            },
            new BenchLoop("DBMathLambdaToGrid")
            {

                long loop()
                {
                    double[] values = GenMathTest.this.doubleValues;
                    long s = 0;
                    for (double x : values)
                    {
                        long lx = lambdaToGrid(x);
                        s = s + lx;
                    }
                    return s;
                }
            },
            new BenchLoop("DBMathLambdaToSizeGrid")
            {

                long loop()
                {
                    double[] values = GenMathTest.this.doubleValues;
                    long s = 0;
                    for (double x : values)
                    {
                        long lx = lambdaToSizeGrid(x);
                        s = s + lx;
                    }
                    return s;
                }
            },
            new BenchLoop("DBMathGridToLambda")
            {

                long loop()
                {
                    long[] values = GenMathTest.this.longValues;
                    double s = 0;
                    for (long l : values)
                    {
                        double x = gridToLambda(l);
                        s = s + x;
                    }
                    return (long)s;
                }
            },
            null,
            new BenchLoop("LongSum")
            {

                long loop()
                {
                    long[] values = GenMathTest.this.longValues;
                    long s = 0;
                    for (long x : values)
                    {
                        s = s + x;
                    }
                    return s;
                }
            },
            new BenchLoop("LangToLong")
            {

                long loop()
                {
                    double[] values = GenMathTest.this.doubleValues;
                    long s = 0;
                    for (double x : values)
                    {
                        long lx = (long)x;
                        s = s + lx;
                    }
                    return s;
                }
            },
            new BenchLoop("GenMathRoundLong")
            {

                long loop()
                {
                    double[] values = GenMathTest.this.doubleValues;
                    long s = 0;
                    for (double x : values)
                    {
                        long lx = GenMath.roundLong(x);
                        s = s + lx;
                    }
                    return s;
                }
            },
            new BenchLoop("GenMathFloorLong")
            {

                long loop()
                {
                    double[] values = GenMathTest.this.doubleValues;
                    long s = 0;
                    for (double x : values)
                    {
                        long lx = GenMath.floorLong(x);
                        s = s + lx;
                    }
                    return s;
                }
            },
            new BenchLoop("GenMathCeilLong")
            {

                long loop()
                {
                    double[] values = GenMathTest.this.doubleValues;
                    long s = 0;
                    for (double x : values)
                    {
                        long lx = GenMath.ceilLong(x);
                        s = s + lx;
                    }
                    return s;
                }
            },
            new BenchLoop("MathRound")
            {

                long loop()
                {
                    double[] values = GenMathTest.this.doubleValues;
                    long s = 0;
                    for (double x : values)
                    {
                        long lx = Math.round(x);
                        s = s + lx;
                    }
                    return s;
                }
            },
            new BenchLoop("MathFloorLong")
            {

                long loop()
                {
                    double[] values = GenMathTest.this.doubleValues;
                    long s = 0;
                    for (double x : values)
                    {
                        long lx = (long)Math.floor(x);
                        s = s + lx;
                    }
                    return s;
                }
            },
            new BenchLoop("MathCeilLong")
            {

                long loop()
                {
                    double[] values = GenMathTest.this.doubleValues;
                    long s = 0;
                    for (double x : values)
                    {
                        long lx = (long)Math.ceil(x);
                        s = s + lx;
                    }
                    return s;
                }
            },
            null,
            new BenchLoop("IntSum")
            {

                long loop()
                {
                    int[] values = GenMathTest.this.intValues;
                    int s = 0;
                    for (int x : values)
                    {
                        s = s + x;
                    }
                    return s;
                }
            },
            new BenchLoop("LangToInt")
            {

                long loop()
                {
                    double[] values = GenMathTest.this.doubleValues;
                    int s = 0;
                    for (double x : values)
                    {
                        int lx = (int)x;
                        s = s + lx;
                    }
                    return s;
                }
            },
            new BenchLoop("GenMathRoundInt")
            {

                long loop()
                {
                    double[] values = GenMathTest.this.doubleValues;
                    int s = 0;
                    for (double x : values)
                    {
                        int lx = GenMath.roundInt(x);
                        s = s + lx;
                    }
                    return s;
                }
            },
            new BenchLoop("GenMathFloorInt")
            {

                long loop()
                {
                    double[] values = GenMathTest.this.doubleValues;
                    int s = 0;
                    for (double x : values)
                    {
                        int lx = GenMath.floorInt(x);
                        s = s + lx;
                    }
                    return s;
                }
            },
            new BenchLoop("GenMathCeilInt")
            {

                long loop()
                {
                    double[] values = GenMathTest.this.doubleValues;
                    int s = 0;
                    for (double x : values)
                    {
                        int lx = GenMath.ceilInt(x);
                        s = s + lx;
                    }
                    return s;
                }
            },
            new BenchLoop("MathFloorInt")
            {

                long loop()
                {
                    double[] values = GenMathTest.this.doubleValues;
                    int s = 0;
                    for (double x : values)
                    {
                        int lx = (int)Math.floor(x);
                        s = s + lx;
                    }
                    return s;
                }
            },
            new BenchLoop("MathCeilInt")
            {

                long loop()
                {
                    double[] values = GenMathTest.this.doubleValues;
                    int s = 0;
                    for (double x : values)
                    {
                        int lx = (int)Math.ceil(x);
                        s = s + lx;
                    }
                    return s;
                }
            },
            null,
            new BenchLoop("isMultiple")
            {

                long loop()
                {
                    long[] values = GenMathTest.this.longValues;
                    int s = 0;
                    for (long x : values)
                    {
                        boolean r = GenMath.isMultiple(x, 16);
                        if (r)
                        {
                            s = s + 1;
                        }
                    }
                    return s;
                }
            },
        };
        doBenchmarks(benches, 20000, 100000);
    }

    private static void doBenchmarks(BenchLoop[] benches, int warmLoops, int benchLoops)
    {
        System.out.println("benchLoops=" + benchLoops + " warmLoops=" + warmLoops);
        for (BenchLoop bench : benches)
        {
            if (bench == null)
            {
                System.out.println();
                continue;
            }
            long s = 0;
            for (int k = 0; k < warmLoops; k++)
            {
                s += bench.loop();
            }
            long startTime = System.currentTimeMillis();
            for (int k = 0; k < benchLoops; k++)
            {
                s += bench.loop();
            }
            long stopTime = System.currentTimeMillis();
            System.out.println(bench.name + " t=" + (stopTime - startTime) + " s=" + s);
        }
    }

    private abstract class BenchLoop
    {

        String name;

        BenchLoop(String name)
        {
            this.name = name;
        }

        abstract long loop();
    }

    /**
     * Test of sin and cos method, of class
     * com.sun.electric.database.geometry.GenMath.
     */
    @Test
    public void testSinCos()
    {
        System.out.println("testSinCos");

        assertEquals(1.0, GenMath.cosSmall(0), 0);
        assertEquals(0.0, GenMath.sinSmall(0), 0);
        assertEquals(0.5, GenMath.sinSmall(300), 0);
        assertEquals(Math.sqrt(0.5), GenMath.cosSmall(450), 0);
        assertEquals(Math.sqrt(0.5), GenMath.sinSmall(450), 0);
        assertEquals(0.0, GenMath.cosSmall(900), 0);
        assertEquals(1.0, GenMath.sinSmall(900), 0);
        for (int angle = 0; angle < 900; angle++)
        {
            assertEquals(Math.cos(angle * Math.PI / 1800), GenMath.cosSmall(angle), 1.25 * Math.ulp(1.0));
        }
    }

    /**
     * Test of sin and cos method, of class
     * com.sun.electric.database.geometry.GenMath.
     */
    @Test
    public void testPolarXY()
    {
        System.out.println("testPolarXY");

        for (int len = 0; len < 1000; len++)
        {
            for (int angle = 0; angle < 3600; angle++)
            {
                long xy = GenMath.polarToXY(len, angle);
                int x = GenMath.getX(xy);
                int y = GenMath.getY(xy);
                long r2 = x * (long)x + y * (long)y;
                if (angle % 900 == 0)
                {
                    assertTrue(r2 == len * (long)len);
                } else
                {
                    assertTrue(r2 >= len * (long)len);
                    assertTrue(r2 < (len + Math.sqrt(2)) * (len + Math.sqrt(2)));
                }
            }
        }
    }

    /**
     * Test of packXY method, of class
     * com.sun.electric.database.geometry.GenMath.
     */
    @Test
    public void testPackXY()
    {
        System.out.println("testPackXY");

        assertEquals(0L, GenMath.packXY(0, 0));
        assertEquals(0x7FFFFFFFL, GenMath.packXY(Integer.MAX_VALUE, 0));
        assertEquals(0x80000000L, GenMath.packXY(Integer.MIN_VALUE, 0));
        assertEquals(0xFFFFFFFFL, GenMath.packXY(-1, 0));
        assertEquals(0x7FFFFFFF00000000L, GenMath.packXY(0, Integer.MAX_VALUE));
        assertEquals(0x8000000000000000L, GenMath.packXY(0, Integer.MIN_VALUE));
        assertEquals(0xFFFFFFFF00000000L, GenMath.packXY(0, -1));
        assertEquals(0x7FFFFFFFFFFFFFFFL, GenMath.packXY(-1, Integer.MAX_VALUE));
        assertEquals(0x8000000000000001L, GenMath.packXY(1, Integer.MIN_VALUE));
        assertEquals(0xFFFFFFFFFFFFFFFFL, GenMath.packXY(-1, -1));
    }

    public void benchPackXY()
    {
        int[] values = new int[10000];
        for (int i = 0; i < values.length; i++)
        {
            values[i] = i;
        }
        for (int k = 0; k < 5; k++)
        {
            loopPackXY(values);
        }
        long startTime = System.currentTimeMillis();
        long s = loopPackXY(values);
        long stopTime = System.currentTimeMillis();
        System.out.println("t=" + (stopTime - startTime) + " s=" + s);
    }

    private long loopPackXY(int[] values)
    {
        long s = 0;
        for (int x : values)
        {
            for (int y : values)
            {
                long xy = GenMath.packXY(x, y);
                s ^= xy;
            }
        }
        return s;
    }

    /**
     * Test of getX method, of class com.sun.electric.database.geometry.GenMath.
     */
    @Test
    public void testGetXY()
    {
        System.out.println("testGetXY");

        int[] values =
        {
            0, -1, -2, 1, 2, Integer.MIN_VALUE, Integer.MAX_VALUE
        };
        for (int x : values)
        {
            for (int y : values)
            {
                long xy = GenMath.packXY(x, y);
                assertEquals(x, GenMath.getX(xy));
                assertEquals(y, GenMath.getY(xy));
            }
        }
    }

    public void benchGetX()
    {
        long[] values = new long[1000000];
        for (int x = 0; x < 1000; x++)
        {
            for (int y = 0; y < 1000; y++)
            {
                values[x + y * 1000] = GenMath.packXY(x, y);
            }
        }
        for (int k = 0; k < 5; k++)
        {
            loopGetX(values);
        }
        long startTime = System.currentTimeMillis();
        int s = loopGetX(values);
        long stopTime = System.currentTimeMillis();
        System.out.println("t=" + (stopTime - startTime) + " s=" + s);
    }

    private int loopGetX(long[] values)
    {
        int s = 0;
        for (long xy = 0; xy < 1000000000; xy++)
        {
            s += GenMath.getX(xy);
        }
        return s;
    }

    /**
     * Test of unsignedIntValue method, of class
     * com.sun.electric.database.geometry.GenMath.
     */
    @Test
    public void testUnsignedIntValue()
    {
        System.out.println("testUnsignedIntValue");

        assertEquals(0L, GenMath.unsignedIntValue(0));
        assertEquals(0x7FFFFFFFL, GenMath.unsignedIntValue(Integer.MAX_VALUE));
        assertEquals(0x80000000L, GenMath.unsignedIntValue(Integer.MIN_VALUE));
        assertEquals(0xFFFFFFFFL, GenMath.unsignedIntValue(-1));
    }

    /**
     * Test of isSmallInt methods, of class
     * com.sun.electric.database.geometry.GenMath.
     */
    @Test
    public void testIsSmallInt()
    {
        System.out.println("testIsSmallInt");

        assertFalse(GenMath.isSmallInt(0x80000000));
        assertFalse(GenMath.isSmallInt(0xBFFFFFFF));
        assertTrue(GenMath.isSmallInt(0xC0000000));
        assertTrue(GenMath.isSmallInt(0xFFFFFFFF));
        assertTrue(GenMath.isSmallInt(0x00000000));
        assertTrue(GenMath.isSmallInt(0x3FFFFFFF));
        assertFalse(GenMath.isSmallInt(0x40000000));
        assertFalse(GenMath.isSmallInt(0x7FFFFFFF));

        assertFalse(GenMath.isSmallInt(0x8000000000000000L));
        assertFalse(GenMath.isSmallInt(0xFFFFFFFE00000000L));
        assertFalse(GenMath.isSmallInt(0xFFFFFFFF80000000L));
        assertFalse(GenMath.isSmallInt(0xFFFFFFFFBFFFFFFFL));
        assertTrue(GenMath.isSmallInt(0xFFFFFFFFC0000000L));
        assertTrue(GenMath.isSmallInt(0xFFFFFFFFFFFFFFFFL));
        assertTrue(GenMath.isSmallInt(0x0000000000000000L));
        assertTrue(GenMath.isSmallInt(0x000000003FFFFFFFL));
        assertFalse(GenMath.isSmallInt(0x0000000040000000L));
        assertFalse(GenMath.isSmallInt(0x000000007FFFFFFFL));
        assertFalse(GenMath.isSmallInt(0x0000000080000000L));
        assertFalse(GenMath.isSmallInt(0x00000000FFFFFFFFL));
        assertFalse(GenMath.isSmallInt(0x0000000100000000L));
        assertFalse(GenMath.isSmallInt(0x7FFFFFFFFFFFFFFFL));
    }

    /**
     * Test of primeSince method, of class
     * com.sun.electric.database.geometry.GenMath.
     */
    @Test
    public void testPrimeSince()
    {
        System.out.println("testPrimeSince");

        int prime = GenMath.primeSince(Integer.MIN_VALUE);
        for (;;)
        {
            assertPrime(prime);
            assertEquals(prime, GenMath.primeSince(prime - 1));
            assertEquals(prime, GenMath.primeSince(prime));
            if (prime == Integer.MAX_VALUE)
            {
                break;
            }
            int newPrime = GenMath.primeSince(prime + 1);
            assertTrue(newPrime >= prime + 1);
            prime = newPrime;
        }
    }

    private void assertPrime(int p)
    {
        assertTrue(p >= 2);
        if (p == 2)
        {
            return;
        }
        assertTrue(p % 2 != 0);
        for (int i = 3; i <= p / i; i += 2)
        {
            assertTrue(p % i != 0);
        }
    }

    @Test
    public void testVarianceEqualDistance()
    {

        double[] values =
        {
            1.0, 2.0, 3.0
        };
        Assert.assertEquals(0.6666666666666666, GenMath.varianceEqualDistribution(values, GenMath.meanEqualDistribution(values)), 0.0000);

        double[] values2 =
        {
            2.0, 3.0, 5.0, 8.0, 10.0, 17.3
        };
        Assert.assertEquals(26.54583333333333, GenMath.varianceEqualDistribution(values2, GenMath.meanEqualDistribution(values2)), 0.00);

    }

    @Test
    public void testMeanEqualDistance()
    {

        double[] values =
        {
            1.0, 2.0, 3.0
        };
        Assert.assertEquals(2.0, GenMath.meanEqualDistribution(values), 0.00);

        double[] values2 =
        {
            2.0, 3.0, 5.0, 8.0, 10.0, 17.3
        };
        Assert.assertEquals(7.55, GenMath.meanEqualDistribution(values2), 0.00);

    }

    @Test
    public void testStandardDeviationEqualDistance()
    {

        double[] values =
        {
            1.0, 2.0, 3.0
        };
        Assert.assertEquals(0.816496580927726, GenMath.standardDeviation(values), 0.00);

        double[] values2 =
        {
            2.0, 3.0, 5.0, 8.0, 10.0, 17.3
        };
        Assert.assertEquals(5.152264874143539, GenMath.standardDeviation(values2), 0.00);

    }
}
