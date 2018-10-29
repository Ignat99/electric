/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.sun.electric.util.math;

import java.math.BigInteger;
import java.math.BigDecimal;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author dn146861
 */
public class FixpCoordTest {

    public FixpCoordTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    /**
     * Test of fromFixp method, of class FixpCoord.
     */
    @Test
    public void testFromFixp() {
        System.out.println("fromFixp");
        assertSame(FixpCoord.ZERO, FixpCoord.fromFixp(0));
        long fixp = -1;
        FixpCoord result = FixpCoord.fromFixp(fixp);
        assertEquals(-1L, result.getFixp());
    }

    /**
     * Test of fromLambda method, of class FixpCoord.
     */
    @Test
    public void testFromLambda() {
        System.out.println("fromLambda");
        double lambda = 0.0;
        FixpCoord expResult = FixpCoord.ZERO;
        FixpCoord result = FixpCoord.fromLambda(lambda);
        assertSame(expResult, result);
    }

    /**
     * Test of lambdaToFixp method, of class FixpCoord.
     */
    @Test
    public void testLambdaToFixp() {
        System.out.println("lambdaToFixp");
        testLambdaToFixp(0);
        testLambdaToFixp(1);
        testLambdaToFixp(2);
        testLambdaToFixp((1L << 52) - 1);
        testLambdaToFixp(1L << 52);
        testLambdaToFixp((1L << 52) + 1);
        testLambdaToFixp((1L << 53) - 1);
        testLambdaToFixp(1L << 53);
        testLambdaToFixp((1L << 53) + 1);
        testLambdaToFixp((1L << 54) - 1);
        testLambdaToFixp(1L << 54);
        testLambdaToFixp((1L << 54) + 1);
        testLambdaToFixp((1L << 55) - 1);
        testLambdaToFixp(1L << 55);
        testLambdaToFixp((1L << 55) + 1);
        testLambdaToFixp((1L << 56) - 1);
        testLambdaToFixp(1L << 56);
        testLambdaToFixp((1L << 56) + 1);
        testLambdaToFixp((1L << 57) - 1);
        testLambdaToFixp(1L << 57);
        testLambdaToFixp((1L << 57) + 1);
        testLambdaToFixp(Long.MAX_VALUE);
    }
    
    private void testLambdaToFixp(long fixp) {
        BigInteger bi = BigInteger.valueOf(5).pow(23).multiply(BigInteger.valueOf(fixp).shiftLeft(1).add(BigInteger.valueOf(1)));
        BigDecimal bd = new BigDecimal(bi, 25);
        double d = (fixp + 0.5)/400.0/(1L << 20);
        BigDecimal bd1 = new BigDecimal(d);
        int cmp = bd1.compareTo(bd);
        if (cmp < 0) {
            testLambdaToFixp(d);
            testLambdaToFixp(Math.nextUp(d));
        } else if (cmp > 0) {
            testLambdaToFixp(-Math.nextUp(-d));
            testLambdaToFixp(d);
        } else {
            testLambdaToFixp(-Math.nextUp(-d));
            testLambdaToFixp(d);
            testLambdaToFixp(Math.nextUp(d));
        }
    }
    
    private void testLambdaToFixp(double lambda) {
        BigInteger expected = myLambdaToFixp(lambda);
        if (expected.bitLength() > Long.SIZE - 1) {
            try {
                FixpCoord.lambdaToFixp(lambda);
                fail("Should throw ArithmeticException");
            } catch (ArithmeticException e) {
            }
            try {
                FixpCoord.fromLambda(lambda);
                fail("Should throw ArithmeticException");
            } catch (ArithmeticException e) {
            }
            return;
        }
        assertEquals(expected, BigInteger.valueOf(FixpCoord.lambdaToFixp(lambda)));
        assertEquals(expected, BigInteger.valueOf(FixpCoord.fromLambda(lambda).getFixp()));
    }
    
    private BigInteger myLambdaToFixp(double lambda) {
        BigDecimal bd = new BigDecimal(lambda).multiply(new BigDecimal((double)(400L << 20)));
        BigInteger l, h;
        if (lambda >= 0) {
            l = bd.toBigInteger();
            h = l.add(BigInteger.ONE);
        } else {
            h = bd.toBigInteger();
            l = h.subtract(BigInteger.ONE);
        }
        assert h.subtract(l).equals(BigInteger.ONE);
        BigDecimal bl = new BigDecimal(l, 0);
        BigDecimal bh = new BigDecimal(h, 0);
        assert bl.compareTo(bd) <= 0 && bd.compareTo(bh) <= 0;
        BigDecimal bm = bl.add(bh).divide(BigDecimal.valueOf(2));
        int cmp = bd.compareTo(bm);
        return cmp < 0 || cmp == 0 && !l.testBit(0) ? l : h;
    }

    @Test
    public void testRounds() {
        System.out.println("testRounds");
        for (long l = -10; l <= 10; l++) {
            testRounds((l << 19) - 1);
            testRounds((l << 19));
            testRounds((l << 19) + 1);
        }
    }
            
    
    private void testRounds(long fixp) {
        FixpCoord coord = FixpCoord.fromFixp(fixp);
        checkRound(round(fixp, 20), coord.round(FixpCoord.GRID), FixpCoord.round(fixp, FixpCoord.GRID), coord);
        checkRound(round(fixp, 21), coord.round(FixpCoord.SIZE_GRID), FixpCoord.round(fixp, FixpCoord.SIZE_GRID), coord);
        checkRound(floor(fixp, 20), coord.floor(FixpCoord.GRID), FixpCoord.floor(fixp, FixpCoord.GRID), coord);
        checkRound(floor(fixp, 21), coord.floor(FixpCoord.SIZE_GRID), FixpCoord.floor(fixp, FixpCoord.SIZE_GRID), coord);
        checkRound(ceil(fixp, 20), coord.ceil(FixpCoord.GRID), FixpCoord.ceil(fixp, FixpCoord.GRID), coord);
        checkRound(ceil(fixp, 21), coord.ceil(FixpCoord.SIZE_GRID), FixpCoord.ceil(fixp, FixpCoord.SIZE_GRID), coord);
    }
    
    private void checkRound(long expected, FixpCoord coord, long fixp, FixpCoord orig) {
        assertEquals(expected, fixp);
        assertEquals(expected, coord.getFixp());
        assertEquals(expected / (400.0*(1L << 20)), coord.getLambda(), 0);
        assertEquals(expected / (400.0*(1L << 20)), FixpCoord.fixpToLambda(fixp), 0);
        if (expected == 0) {
            assertSame(ECoord.ZERO, coord);
        } else if (expected == orig.getFixp()) {
            assertSame(orig, coord);
        }
    }
    
    private long round(long fixp, int fractionBits) {
        boolean b = (fixp & (1L << fractionBits)) != 0;
        boolean bh = (fixp & (1L << (fractionBits - 1))) != 0;
        boolean sticky = (fixp & ((1L << (fractionBits - 1)) - 1)) != 0;
        long r = fixp & (-1L << fractionBits);
        if (bh && (sticky || b)) {
            r += 1L << fractionBits;
        }
        return r;
    }
    
    private long floor(long fixp, int fractionBits) {
        return fixp & (-1L << fractionBits);
    }
    
    private long ceil(long fixp, int fractionBits) {
        return (fixp + (1L << fractionBits) - 1) & (-1L << fractionBits);
    }
    
    /**
     * Test of add method, of class FixpCoord.
     */
    @Test
    public void testAdd() {
        System.out.println("add");
        FixpCoord y = FixpCoord.ZERO;
        FixpCoord instance = FixpCoord.ZERO;
        FixpCoord expResult = FixpCoord.ZERO;
        FixpCoord result = instance.add(y);
        assertSame(expResult, result);
    }

    /**
     * Test of subtract method, of class FixpCoord.
     */
    @Test
    public void testSubtract() {
        System.out.println("subtract");
        FixpCoord y = FixpCoord.ZERO;
        FixpCoord instance = FixpCoord.ZERO;
        FixpCoord expResult = FixpCoord.ZERO;
        FixpCoord result = instance.subtract(y);
        assertSame(expResult, result);
    }

    /**
     * Test of multiply method, of class FixpCoord.
     */
    @Test
    public void testMultiply() {
        System.out.println("multiply");
        double scale = 0.0;
        FixpCoord instance = FixpCoord.ZERO;
        FixpCoord expResult = FixpCoord.ZERO;
        FixpCoord result = instance.multiply(scale);
        assertSame(expResult, result);
    }

    /**
     * Test of min method, of class FixpCoord.
     */
    @Test
    public void testMin() {
        System.out.println("min");
        FixpCoord y = FixpCoord.ZERO;
        FixpCoord instance = FixpCoord.ZERO;
        FixpCoord expResult = FixpCoord.ZERO;
        FixpCoord result = instance.min(y);
        assertSame(expResult, result);
    }

    /**
     * Test of max method, of class FixpCoord.
     */
    @Test
    public void testMax() {
        System.out.println("max");
        FixpCoord y = FixpCoord.ZERO;
        FixpCoord instance = FixpCoord.ZERO;
        FixpCoord expResult = FixpCoord.ZERO;
        FixpCoord result = instance.max(y);
        assertSame(expResult, result);
    }

    /**
     * Test of equals method, of class FixpCoord.
     */
    @Test
    public void testEquals() {
        System.out.println("equals");
        Object o = null;
        FixpCoord instance = FixpCoord.ZERO;
        boolean expResult = false;
        boolean result = instance.equals(o);
        assertEquals(expResult, result);
    }

    /**
     * Test of hashCode method, of class FixpCoord.
     */
    @Test
    public void testHashCode() {
        System.out.println("hashCode");
        FixpCoord instance = FixpCoord.ZERO;
        int expResult = 0;
        int result = instance.hashCode();
        assertEquals(expResult, result);
    }

    /**
     * Test of toString method, of class FixpCoord.
     */
    @Test
    public void testToString() {
        System.out.println("toString");
        FixpCoord instance = FixpCoord.ZERO;
        String expResult = "0.0";
        String result = instance.toString();
        assertEquals(expResult, result);
    }
}
