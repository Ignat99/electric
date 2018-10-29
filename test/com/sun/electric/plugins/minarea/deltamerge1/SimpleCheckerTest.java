/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.sun.electric.plugins.minarea.deltamerge1;

import java.awt.Shape;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.util.Properties;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

import com.sun.electric.api.minarea.launcher.Launcher;
import com.sun.electric.api.minarea.LayoutCell;
import com.sun.electric.api.minarea.ErrorLogger;
import com.sun.electric.api.minarea.MinAreaChecker;
/**
 *
 * @author dn146861
 */
public class SimpleCheckerTest {
//
//    private static MinAreaChecker minAreaChecker;
//    
//    public SimpleCheckerTest() {
//    }
//
//    @BeforeClass
//    public static void setUpClass() throws Exception {
//        minAreaChecker = new SimpleChecker();
//    }
//
//    @AfterClass
//    public static void tearDownClass() throws Exception {
//        minAreaChecker = null;
//    }
//
//    @Before
//    public void setUp() {
//    }
//
//    @After
//    public void tearDown() {
//    }
//
//    /**
//     * Test of getAlgorithmName method, of class BitMapMinAreaChecker.
//     */
//    @Test
//    public void testGetAlgorithmName() {
//        System.out.println("getAlgorithmName");
//        MinAreaChecker instance = new SimpleChecker();
//        String expResult = "DeltaMerge1";
//        String result = instance.getAlgorithmName();
//        assertEquals(expResult, result);
//    }
//
//    /**
//     * Test of getDefaultParameters method, of class BitMapMinAreaChecker.
//     */
//    @Test
//    public void testGetDefaultParameters() {
//        System.out.println("getDefaultParameters");
//        MinAreaChecker instance = new SimpleChecker();
//        Properties expResult = new Properties();
//        expResult.put(MinAreaChecker.REPORT_TILES, Boolean.TRUE);
//        expResult.put(MinAreaChecker.RECTS_PER_STRIPE, 100000L);
//
//        Properties result = instance.getDefaultParameters();
//        assertEquals(expResult, result);
//    }
//
//    /**
//     * Test of check method, of class BitMapMinAreaChecker.
//     */
//    @Test
//    public void testCheck() {
//        System.out.println("check");
//
//        testOne("BasicAreas_CPG.lay", 0, 0, 0);
//        testOne("BasicAreas_CPG.lay", 44, 0, 0);
//        testOne("BasicAreas_CPG.lay", 45, 1, 44);
//        testOne("BasicAreas_CPG.lay", 160, 1, 44);
//        testOne("BasicAreas_CPG.lay", 161, 2, 204);
//        testOne("BasicAreas_CPG.lay", Long.MAX_VALUE, 2, 204);
//                
//        testOne("SimpleHierarchy_CMF.lay", 0, 0, 0);
//        testOne("SimpleHierarchy_CMF.lay", 162, 0, 0);
//        testOne("SimpleHierarchy_CMF.lay", 163, 1, 162);
//        testOne("SimpleHierarchy_CMF.lay", 240, 1, 162);
//        testOne("SimpleHierarchy_CMF.lay", 241, 2, 402);
//        testOne("SimpleHierarchy_CMF.lay", Long.MAX_VALUE, 2, 402);
//    }
//    
//    private void testOne(String layoutFileName, long minArea, int expectedNumViolations, long expectedTotalArea) {
//        InputStream is = Launcher.class.getResourceAsStream(layoutFileName);
//        LayoutCell topCell = null;
//        try {
//            ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(is));
//            topCell = (LayoutCell) in.readObject();
//            in.close();
//        } catch (Exception e) {
//            fail("Can't read " + layoutFileName);
//        }
//        Properties parameters = minAreaChecker.getDefaultParameters();
//        MyErrorLogger errorLogger = new MyErrorLogger();
//        minAreaChecker.check(topCell, minArea, parameters, errorLogger);
//        assertEquals(expectedTotalArea, errorLogger.totalArea);
//        assertEquals(expectedNumViolations, errorLogger.numViolations);
//    }
//    
//    private static class MyErrorLogger implements ErrorLogger {
//        private int numViolations = 0;
//        private long totalArea = 0;
//
//        @Override
//        public void reportMinAreaViolation(long area, int x, int y, Shape shape) {
//            totalArea += area;
//            numViolations++;
//        }
//    }
}