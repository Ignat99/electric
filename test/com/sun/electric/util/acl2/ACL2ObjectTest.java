/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: ACL2ObjectTest.java
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
public class ACL2ObjectTest
{

    public ACL2ObjectTest()
    {
    }

    @BeforeClass
    public static void setUpClass()
    {
        ACL2Object.initHonsMananger("ACL2ObjectTest");
    }

    @AfterClass
    public static void tearDownClass()
    {
        ACL2Object.closeHonsManager();
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
     * Test of valueOf method, of class ACL2Object.
     */
    @Test
    public void testValueOf_BigInteger()
    {
        System.out.println("valueOf");
        BigInteger v = BigInteger.valueOf(3).shiftLeft(70);
        ACL2Object expResult = new ACL2Integer(BigInteger.valueOf(3).shiftLeft(70));
        ACL2Object result = ACL2Object.valueOf(v);
        assertEquals(expResult, result);
    }

    /**
     * Test of valueOf method, of class ACL2Object.
     */
    @Test
    public void testValueOf_long()
    {
        System.out.println("valueOf");
        long v = -45L;
        ACL2Object expResult = new ACL2Integer(BigInteger.valueOf(-45));
        ACL2Object result = ACL2Object.valueOf(v);
        assertEquals(expResult, result);
    }

    /**
     * Test of valueOf method, of class ACL2Object.
     */
    @Test
    public void testValueOf_int()
    {
        System.out.println("valueOf");
        int v = -37;
        ACL2Object expResult = new ACL2Integer(BigInteger.valueOf(-37));
        ACL2Object result = ACL2Object.valueOf(v);
        assertEquals(expResult, result);
    }

    /**
     * Test of valueOf method, of class ACL2Object.
     */
    @Test
    public void testValueOf_Rational()
    {
        System.out.println("valueOf");
        Rational r = new Rational(BigInteger.valueOf(7), BigInteger.ONE);
        ACL2Object expResult = new ACL2Integer(BigInteger.valueOf(7));
        ACL2Object result = ACL2Object.valueOf(r);
        assertEquals(expResult, result);
    }

    /**
     * Test of valueOf method, of class ACL2Object.
     */
    @Test
    public void testValueOf_Complex()
    {
        System.out.println("valueOf");
        Rational re = new Rational(BigInteger.valueOf(3), BigInteger.valueOf(2));
        Rational im = new Rational(BigInteger.ZERO, BigInteger.ONE);
        Complex c = new Complex(re, im);
        ACL2Object expResult = new ACL2Rational(new Rational(BigInteger.valueOf(3), BigInteger.valueOf(2)));
        ACL2Object result = ACL2Object.valueOf(c);
        assertEquals(expResult, result);
    }

    /**
     * Test of valueOf method, of class ACL2Object.
     */
    @Test
    public void testValueOf_String_String()
    {
        System.out.println("valueOf");
        String pk = "KEYWORD";
        String nm = "VAR";
        ACL2Object expResult = ACL2Object.valueOf(pk, nm);
        ACL2Object result = ACL2Object.valueOf(pk, nm);
        assertSame(expResult, result);
    }

    /**
     * Test of valueOf method, of class ACL2Object.
     */
    @Test
    public void testValueOf_boolean()
    {
        System.out.println("valueOf");
        boolean v = true;
        ACL2Object expResult = ACL2Object.valueOf("COMMON-LISP", "T");
        ACL2Object result = ACL2Object.valueOf(v);
        assertEquals(expResult, result);
    }

    /**
     * Test of bool method, of class ACL2Object.
     */
    @Test
    public void testBool()
    {
        System.out.println("bool");
        ACL2Object instance = ACL2Object.valueOf("KEYWORD", "VAR");
        boolean expResult = true;
        boolean result = instance.bool();
        assertEquals(expResult, result);
    }

    /**
     * Test of intValueExact method, of class ACL2Object.
     */
    @Test
    public void testIntValueExact()
    {
        System.out.println("intValueExact");
        ACL2Object instance = new ACL2Integer(BigInteger.valueOf(-56));
        int expResult = -56;
        int result = instance.intValueExact();
        assertEquals(expResult, result);
    }

    /**
     * Test of longValueExact method, of class ACL2Object.
     */
    @Test
    public void testLongValueExact()
    {
        System.out.println("longValueExact");
        ACL2Object instance = new ACL2Integer(BigInteger.valueOf(-10000000000L));
        long expResult = -10000000000L;
        long result = instance.longValueExact();
        assertEquals(expResult, result);
    }

    /**
     * Test of stringValueExact method, of class ACL2Object.
     */
    @Test
    public void testStringValueExact()
    {
        System.out.println("stringValueExact");
        ACL2Object instance = new ACL2String("CAFEBABE");
        String expResult = "CAFEBABE";
        String result = instance.stringValueExact();
        assertEquals(expResult, result);
    }

    /**
     * Test of isACL2Number method, of class ACL2Object.
     */
    @Test
    public void testIsACL2Number()
    {
        System.out.println("isACL2Number");
        ACL2Object instance = new ACL2Rational(new Rational(BigInteger.valueOf(3), BigInteger.valueOf(2)));
        boolean expResult = true;
        boolean result = instance.isACL2Number();
        assertEquals(expResult, result);
    }

    /**
     * Test of fix method, of class ACL2Object.
     */
    @Test
    public void testFix()
    {
        System.out.println("fix");
        ACL2Object instance = ACL2.NIL;
        ACL2Object expResult = new ACL2Integer(BigInteger.valueOf(0));
        ACL2Object result = instance.fix();
        assertEquals(expResult, result);
    }

    /**
     * Test of ratfix method, of class ACL2Object.
     */
    @Test
    public void testRatfix()
    {
        System.out.println("ratfix");
        ACL2Object instance = ACL2.NIL;
        Rational expResult = new Rational(BigInteger.ZERO, BigInteger.ONE);
        Rational result = instance.ratfix();
        assertEquals(expResult, result);
    }

    /**
     * Test of unaryMinus method, of class ACL2Object.
     */
    @Test
    public void testUnaryMinus()
    {
        System.out.println("unaryMinus");
        ACL2Object instance = new ACL2Rational(new Rational(BigInteger.valueOf(-5), BigInteger.valueOf(3)));
        ACL2Object expResult = new ACL2Rational(new Rational(BigInteger.valueOf(5), BigInteger.valueOf(3)));
        ACL2Object result = instance.unaryMinus();
        assertEquals(expResult, result);
    }

    /**
     * Test of unarySlash method, of class ACL2Object.
     */
    @Test
    public void testUnarySlash()
    {
        System.out.println("unarySlash");
        ACL2Object instance = new ACL2Integer(BigInteger.valueOf(-3));
        ACL2Object expResult = new ACL2Rational(new Rational(BigInteger.valueOf(-1), BigInteger.valueOf(3)));
        ACL2Object result = instance.unarySlash();
        assertEquals(expResult, result);
    }

    /**
     * Test of binaryPlus method, of class ACL2Object.
     */
    @Test
    public void testBinaryPlus_ACL2Object()
    {
        System.out.println("binaryPlus");
        ACL2Object y = new ACL2Integer(BigInteger.valueOf(2));
        ACL2Object instance = new ACL2Rational(new Rational(BigInteger.valueOf(-5), BigInteger.valueOf(3)));
        ACL2Object expResult = new ACL2Rational(new Rational(BigInteger.ONE, BigInteger.valueOf(3)));
        ACL2Object result = instance.binaryPlus(y);
        assertEquals(expResult, result);
    }

    /**
     * Test of binaryPlus method, of class ACL2Object.
     */
    @Test
    public void testBinaryPlus_ACL2Integer()
    {
        System.out.println("binaryPlus");
        ACL2Integer y = new ACL2Integer(BigInteger.valueOf(7));
        ACL2Object instance = ACL2.NIL;
        ACL2Object expResult = new ACL2Integer(BigInteger.valueOf(7));
        ACL2Object result = instance.binaryPlus(y);
        assertEquals(expResult, result);
    }

    /**
     * Test of binaryPlus method, of class ACL2Object.
     */
    @Test
    public void testBinaryPlus_ACL2Rational()
    {
        System.out.println("binaryPlus");
        ACL2Rational y = new ACL2Rational(new Rational(BigInteger.valueOf(-1), BigInteger.valueOf(3)));
        ACL2Object instance = new ACL2Rational(new Rational(BigInteger.valueOf(-5), BigInteger.valueOf(3)));
        ACL2Object expResult = new ACL2Integer(BigInteger.valueOf(-2));
        ACL2Object result = instance.binaryPlus(y);
        assertEquals(expResult, result);
    }

    /**
     * Test of binaryPlus method, of class ACL2Object.
     */
    @Test
    public void testBinaryPlus_ACL2Complex()
    {
        System.out.println("binaryPlus");
        ACL2Complex y = new ACL2Complex(
            new Complex(
                new Rational(BigInteger.valueOf(-1), BigInteger.valueOf(5)),
                new Rational(BigInteger.valueOf(5), BigInteger.valueOf(3))));
        ACL2Object instance = new ACL2Rational(new Rational(BigInteger.valueOf(1), BigInteger.valueOf(5)));
        ACL2Object expResult = new ACL2Complex(
            new Complex(
                new Rational(BigInteger.ZERO, BigInteger.ONE),
                new Rational(BigInteger.valueOf(5), BigInteger.valueOf(3))));
        ACL2Object result = instance.binaryPlus(y);
        assertEquals(expResult, result);
    }

    /**
     * Test of binaryStar method, of class ACL2Object.
     */
    @Test
    public void testBinaryStar_ACL2Object()
    {
        System.out.println("binaryStar");
        ACL2Object y = ACL2.NIL;
        ACL2Object instance = new ACL2Integer(BigInteger.valueOf(5));
        ACL2Object expResult = new ACL2Integer(BigInteger.valueOf(5));
        ACL2Object result = instance.binaryStar(y);
        assertEquals(expResult, result);
    }

    /**
     * Test of binaryStar method, of class ACL2Object.
     */
    @Test
    public void testBinaryStar_ACL2Integer()
    {
        System.out.println("binaryStar");
        ACL2Integer y = new ACL2Integer(BigInteger.valueOf(-5));
        ACL2Object instance = new ACL2Rational(new Rational(BigInteger.valueOf(-3), BigInteger.valueOf(5)));
        ACL2Object expResult = new ACL2Integer(BigInteger.valueOf(3));
        ACL2Object result = instance.binaryStar(y);
        assertEquals(expResult, result);
    }

    /**
     * Test of binaryStar method, of class ACL2Object.
     */
    @Test
    public void testBinaryStar_ACL2Rational()
    {
        System.out.println("binaryStar");
        ACL2Rational y = new ACL2Rational(new Rational(BigInteger.valueOf(-1), BigInteger.valueOf(3)));
        ACL2Object instance = new ACL2Integer(BigInteger.valueOf(-12));
        ACL2Object expResult = new ACL2Integer(BigInteger.valueOf(4));
        ACL2Object result = instance.binaryStar(y);
        assertEquals(expResult, result);
    }

    /**
     * Test of binaryStar method, of class ACL2Object.
     */
    @Test
    public void testBinaryStar_ACL2Complex()
    {
        System.out.println("binaryStar");
        ACL2Complex y = new ACL2Complex(
            new Complex(
                new Rational(BigInteger.ZERO, BigInteger.ONE),
                new Rational(BigInteger.ONE, BigInteger.ONE)));
        ACL2Object instance = new ACL2Complex(
            new Complex(
                new Rational(BigInteger.ZERO, BigInteger.ONE),
                new Rational(BigInteger.valueOf(-5), BigInteger.ONE)));
        ACL2Object expResult = new ACL2Integer(BigInteger.valueOf(5));
        ACL2Object result = instance.binaryStar(y);
        assertEquals(expResult, result);
    }

    /**
     * Test of signum method, of class ACL2Object.
     */
    @Test
    public void testSignum()
    {
        System.out.println("signum");
        ACL2Object instance = new ACL2Complex(
            new Complex(
                new Rational(BigInteger.ZERO, BigInteger.ONE),
                new Rational(BigInteger.ONE, BigInteger.ONE)));
        int expResult = 1;
        int result = instance.signum();
        assertEquals(expResult, result);
    }

    /**
     * Test of compareTo method, of class ACL2Object.
     */
    @Test
    public void testCompareTo_ACL2Object()
    {
        System.out.println("compareTo");
        ACL2Object y = ACL2.NIL;
        ACL2Object instance = ACL2.T;
        int expResult = 0;
        int result = instance.compareTo(y);
        assertEquals(expResult, result);
    }

    /**
     * Test of compareTo method, of class ACL2Object.
     */
    @Test
    public void testCompareTo_ACL2Integer()
    {
        System.out.println("compareTo");
        ACL2Integer y = new ACL2Integer(BigInteger.valueOf(5));
        ACL2Object instance = new ACL2Rational(new Rational(BigInteger.valueOf(14), BigInteger.valueOf(3)));
        int expResult = -1;
        int result = instance.compareTo(y);
        assertEquals(expResult, result);
    }

    /**
     * Test of compareTo method, of class ACL2Object.
     */
    @Test
    public void testCompareTo_ACL2Rational()
    {
        System.out.println("compareTo");
        ACL2Rational y = new ACL2Rational(new Rational(BigInteger.valueOf(2), BigInteger.valueOf(3)));
        ACL2Object instance = new ACL2Rational(new Rational(BigInteger.valueOf(3), BigInteger.valueOf(4)));
        int expResult = 1;
        int result = instance.compareTo(y);
        assertEquals(expResult, result);
    }

    /**
     * Test of compareTo method, of class ACL2Object.
     */
    @Test
    public void testCompareTo_ACL2Complex()
    {
        System.out.println("compareTo");
        ACL2Complex y = new ACL2Complex(
            new Complex(
                new Rational(BigInteger.ZERO, BigInteger.ONE),
                new Rational(BigInteger.ONE, BigInteger.ONE)));
        ACL2Object instance = new ACL2Integer(BigInteger.valueOf(-1));
        int expResult = -1;
        int result = instance.compareTo(y);
        assertEquals(expResult, result);
    }

    /**
     * Test of rep method, of class ACL2Object.
     */
    @Test
    public void testRep()
    {
        System.out.println("rep");
        ACL2Object instance = ACL2.NIL;
        String expResult = "COMMON-LISP::NIL";
        String result = instance.rep();
        assertEquals(expResult, result);
    }
}
