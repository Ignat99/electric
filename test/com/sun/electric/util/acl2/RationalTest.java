/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: RationalTest.java
 *
 * Copyright (c) 2017, Static Free Software. All rights reserved.
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
package com.sun.electric.util.acl2;

import java.math.BigInteger;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 */
public class RationalTest
{

    public RationalTest()
    {
    }

    @BeforeClass
    public static void setUpClass()
    {
    }

    @AfterClass
    public static void tearDownClass()
    {
    }

    @Before
    public void setUp()
    {
    }

    @After
    public void tearDown()
    {
    }

    /**
     * Test of valueOf method, of class Rational.
     */
    @Test
    public void testValueOf()
    {
        System.out.println("valueOf");
        BigInteger nom = BigInteger.valueOf(16);
        BigInteger den = BigInteger.valueOf(-12);
        Rational expResult = new Rational(BigInteger.valueOf(-4), BigInteger.valueOf(3));
        Rational result = Rational.valueOf(nom, den);
        assertEquals(expResult, result);
    }

    /**
     * Test of signum method, of class Rational.
     */
    @Test
    public void testSignum()
    {
        System.out.println("signum");
        Rational instance = new Rational(BigInteger.valueOf(-5), BigInteger.valueOf(3));
        int expResult = -1;
        int result = instance.signum();
        assertEquals(expResult, result);
    }

    /**
     * Test of isInteger method, of class Rational.
     */
    @Test
    public void testIsInteger()
    {
        System.out.println("isInteger");
        Rational instance = new Rational(BigInteger.valueOf(-5), BigInteger.valueOf(1));
        boolean expResult = true;
        boolean result = instance.isInteger();
        assertEquals(expResult, result);
    }

    /**
     * Test of negate method, of class Rational.
     */
    @Test
    public void testNegate()
    {
        System.out.println("negate");
        Rational instance = new Rational(BigInteger.valueOf(-5), BigInteger.valueOf(3));
        Rational expResult = new Rational(BigInteger.valueOf(5), BigInteger.valueOf(3));
        Rational result = instance.negate();
        assertEquals(expResult, result);
    }

    /**
     * Test of inverse method, of class Rational.
     */
    @Test
    public void testInverse()
    {
        System.out.println("inverse");
        Rational instance = new Rational(BigInteger.valueOf(-5), BigInteger.valueOf(3));
        Rational expResult = new Rational(BigInteger.valueOf(-3), BigInteger.valueOf(5));
        Rational result = instance.inverse();
        assertEquals(expResult, result);
    }

    /**
     * Test of add method, of class Rational.
     */
    @Test
    public void testAdd_Rational()
    {
        System.out.println("add");
        Rational y = new Rational(BigInteger.valueOf(1), BigInteger.valueOf(6));
        Rational instance = new Rational(BigInteger.valueOf(-5), BigInteger.valueOf(3));
        Rational expResult = new Rational(BigInteger.valueOf(-3), BigInteger.valueOf(2));
        Rational result = instance.add(y);
        assertEquals(expResult, result);
    }

    /**
     * Test of add method, of class Rational.
     */
    @Test
    public void testAdd_BigInteger()
    {
        System.out.println("add");
        BigInteger y = BigInteger.valueOf(2);
        Rational instance = new Rational(BigInteger.valueOf(-5), BigInteger.valueOf(3));
        Rational expResult = new Rational(BigInteger.valueOf(1), BigInteger.valueOf(3));
        Rational result = instance.add(y);
        assertEquals(expResult, result);
    }

    /**
     * Test of mul method, of class Rational.
     */
    @Test
    public void testMul_BigInteger()
    {
        System.out.println("mul");
        BigInteger y = BigInteger.valueOf(-9);
        Rational instance = new Rational(BigInteger.valueOf(-5), BigInteger.valueOf(6));
        Rational expResult = new Rational(BigInteger.valueOf(15), BigInteger.valueOf(2));
        Rational result = instance.mul(y);
        assertEquals(expResult, result);
    }

    /**
     * Test of mul method, of class Rational.
     */
    @Test
    public void testMul_Rational()
    {
        System.out.println("mul");
        Rational y = new Rational(BigInteger.valueOf(63), BigInteger.valueOf(55));
        Rational instance = new Rational(BigInteger.valueOf(-5), BigInteger.valueOf(6));
        Rational expResult = new Rational(BigInteger.valueOf(-21), BigInteger.valueOf(22));
        Rational result = instance.mul(y);
        assertEquals(expResult, result);
    }

    /**
     * Test of equals method, of class Rational.
     */
    @Test
    public void testEquals()
    {
        System.out.println("equals");
        Object o = new Rational(BigInteger.valueOf(-5), BigInteger.valueOf(6));
        Rational instance = new Rational(BigInteger.valueOf(-5), BigInteger.valueOf(6));
        boolean expResult = true;
        boolean result = instance.equals(o);
        assertEquals(expResult, result);
    }

    /**
     * Test of toString method, of class Rational.
     */
    @Test
    public void testToString()
    {
        System.out.println("toString");
        Rational instance = new Rational(BigInteger.valueOf(-5), BigInteger.valueOf(6));
        String expResult = "-5/6";
        String result = instance.toString();
        assertEquals(expResult, result);
    }

    /**
     * Test of compareTo method, of class Rational.
     */
    @Test
    public void testCompareTo_Rational()
    {
        System.out.println("compareTo");
        Rational y = new Rational(BigInteger.ZERO, BigInteger.ONE);
        Rational instance = new Rational(BigInteger.valueOf(-5), BigInteger.valueOf(6));
        int expResult = -1;
        int result = instance.compareTo(y);
        assertEquals(expResult, result);
    }

    /**
     * Test of compareTo method, of class Rational.
     */
    @Test
    public void testCompareTo_BigInteger()
    {
        System.out.println("compareTo");
        BigInteger y = BigInteger.valueOf(-2);
        Rational instance = new Rational(BigInteger.valueOf(-5), BigInteger.valueOf(6));;
        int expResult = +1;
        int result = instance.compareTo(y);
        assertEquals(expResult, result);
    }

}
