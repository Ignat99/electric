/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: ImmutableArcInstTest.java
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
package com.sun.electric.database;

import com.sun.electric.database.geometry.EGraphics;
import com.sun.electric.database.geometry.EPoint;
import com.sun.electric.database.geometry.Poly;
import com.sun.electric.database.id.ArcProtoId;
import com.sun.electric.database.id.CellId;
import com.sun.electric.database.id.IdManager;
import com.sun.electric.database.id.IdReader;
import com.sun.electric.database.id.IdWriter;
import com.sun.electric.database.id.LibId;
import com.sun.electric.database.id.PrimitiveNodeId;
import com.sun.electric.database.id.PrimitivePortId;
import com.sun.electric.database.id.TechId;
import com.sun.electric.database.text.CellName;
import com.sun.electric.database.text.Name;
import com.sun.electric.database.variable.MutableTextDescriptor;
import com.sun.electric.database.variable.TextDescriptor;
import com.sun.electric.database.variable.Variable;
import com.sun.electric.technology.AbstractShapeBuilder;
import com.sun.electric.technology.ArcProto;
import com.sun.electric.technology.Layer;
import com.sun.electric.technology.PrimitiveNode;
import com.sun.electric.technology.PrimitivePort;
import com.sun.electric.technology.TechFactory;
import com.sun.electric.technology.TechPool;
import com.sun.electric.technology.Technology;
import com.sun.electric.technology.technologies.Artwork;
import com.sun.electric.technology.technologies.Generic;
import com.sun.electric.util.math.DBMath;
import com.sun.electric.util.math.FixpCoord;
import com.sun.electric.util.math.GenMath;
import com.sun.electric.util.math.Orientation;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import org.junit.After;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test of ImmutableArcInst
 */
public class ImmutableArcInstTest {

    private static final long SHAPE_SCALE = 1L << FixpCoord.FRACTION_BITS;
    private IdManager idManager;
    private Generic generic;
    private Artwork artwork;
    private Technology tech;
    private TechPool techPool;
    private TechId techId;
    private PrimitiveNode pn;
    private PrimitiveNodeId pnId;
    private PrimitivePort pp;
    private PrimitivePortId ppId;
    private ArcProto ap;
    private ArcProtoId apId;
    private long apExtend;
    private LibId libId;
    private CellId cellId;
    private ImmutableNodeInst n0, n1;
    private Name nameA0;
    private ImmutableArcInst a0;

    @Before
    public void setUp() throws Exception {
        idManager = new IdManager();
        generic = Generic.newInstance(idManager);
        artwork = (Artwork) TechFactory.getTechFactory("artwork").newInstance(generic);
        tech = TechFactory.getTechFactory("mocmos").newInstance(generic);
        techPool = idManager.getInitialTechPool().withTech(generic).withTech(artwork).withTech(tech);
        pn = tech.findNodeProto("Metal-1-P-Active-Con");
        pnId = pn.getId();
        pp = pn.getPort(0);
        ppId = pp.getId();
        ap = tech.findArcProto("P-Active");
        apId = ap.getId();
        apExtend = DBMath.lambdaToGrid(0);
//        apExtend = DBMath.lambdaToGrid(1.5);
        techId = tech.getId();
        libId = idManager.newLibId("lib");
        cellId = libId.newCellId(CellName.parseName("cell;1{lay}"));
        n0 = ImmutableNodeInst.newInstance(0, pnId, Name.findName("n0"), null, Orientation.IDENT, EPoint.fromLambda(1, 2), EPoint.fromLambda(0, 0), 0, 0, null);
        n1 = ImmutableNodeInst.newInstance(1, pnId, Name.findName("n1"), null, Orientation.IDENT, EPoint.fromLambda(21, 2), EPoint.fromLambda(0, 0), 0, 0, null);
        nameA0 = Name.findName("a0");
        a0 = ImmutableArcInst.newInstance(0, apId, nameA0, null, 0, ppId, n0.anchor, 1, ppId, n1.anchor, apExtend, 0, ImmutableArcInst.DEFAULT_FLAGS);
    }

    @After
    public void tearDown() {
    }

    public static junit.framework.Test suite() {
        return new junit.framework.JUnit4TestAdapter(ImmutableArcInstTest.class);
    }

//    public static class FlagTest {
//
//        public FlagTest(String testName) {
//            super(testName);
//        }
//
//        protected void setUp() throws Exception {
//        }
//
//        protected void tearDown() throws Exception {
//        }
//
//        /**
//         * Test of is method, of class com.sun.electric.database.ImmutableArcInst.Flag.
//         */
//        public void testIs() {
//            System.out.println("is");
//
//            int userBits = 0;
//            ImmutableArcInst.Flag instance = null;
//
//            boolean expResult = true;
//            boolean result = instance.is(userBits);
//            assertEquals(expResult, result);
//
//            // TODO review the generated test code and remove the default call to fail.
//            fail("The test case is a prototype.");
//        }
//
//        /**
//         * Test of set method, of class com.sun.electric.database.ImmutableArcInst.Flag.
//         */
//        public void testSet() {
//            System.out.println("set");
//
//            int userBits = 0;
//            boolean value = true;
//            ImmutableArcInst.Flag instance = null;
//
//            int expResult = 0;
//            int result = instance.set(userBits, value);
//            assertEquals(expResult, result);
//
//            // TODO review the generated test code and remove the default call to fail.
//            fail("The test case is a prototype.");
//        }
//    }
    /**
     * Test of getLambdaLength method, of class com.sun.electric.database.ImmutableArcInst.
     */
    @Test
    public void testGetLambdaLength() {
        System.out.println("getLambdaLength");
        assertEquals(20.0, a0.getLambdaLength(), 0);
    }

    /**
     * Test of getGridLength method, of class com.sun.electric.database.ImmutableArcInst.
     */
    @Test
    public void testGetGridLength() {
        System.out.println("getGridLength");
        assertEquals(20 * DBMath.GRID, a0.getGridLength(), 0);
    }

    /**
     * Test of getAngle method, of class com.sun.electric.database.ImmutableArcInst.
     */
    @Test
    public void testGetAngle() {
        System.out.println("getAngle");
        assertEquals(0, a0.getAngle());
    }

    /**
     * Test of is method, of class com.sun.electric.database.ImmutableArcInst.
     */
    @Test
    public void testIs() {
        System.out.println("is");
        assertFalse(a0.is(ImmutableArcInst.RIGID));
        assertFalse(a0.is(ImmutableArcInst.FIXED_ANGLE));
        assertTrue(a0.is(ImmutableArcInst.SLIDABLE));
        assertFalse(a0.is(ImmutableArcInst.HARD_SELECT));
        assertFalse(a0.is(ImmutableArcInst.BODY_ARROWED));
        assertFalse(a0.is(ImmutableArcInst.TAIL_ARROWED));
        assertFalse(a0.is(ImmutableArcInst.HEAD_ARROWED));
        assertFalse(a0.is(ImmutableArcInst.TAIL_NEGATED));
        assertFalse(a0.is(ImmutableArcInst.HEAD_NEGATED));
        assertTrue(a0.is(ImmutableArcInst.TAIL_EXTENDED));
        assertTrue(a0.is(ImmutableArcInst.HEAD_EXTENDED));
    }

    /**
     * Test of isRigid method, of class com.sun.electric.database.ImmutableArcInst.
     */
    @Test
    public void testIsRigid() {
        System.out.println("isRigid");
        assertFalse(a0.isRigid());
    }

    /**
     * Test of isFixedAngle method, of class com.sun.electric.database.ImmutableArcInst.
     */
    @Test
    public void testIsFixedAngle() {
        System.out.println("isFixedAngle");
        assertFalse(a0.isFixedAngle());
    }

    /**
     * Test of isSlidable method, of class com.sun.electric.database.ImmutableArcInst.
     */
    @Test
    public void testIsSlidable() {
        System.out.println("isSlidable");
        assertTrue(a0.isSlidable());
    }

    /**
     * Test of isHardSelect method, of class com.sun.electric.database.ImmutableArcInst.
     */
    @Test
    public void testIsHardSelect() {
        System.out.println("isHardSelect");
        assertFalse(a0.isHardSelect());
    }

    /**
     * Test of isArrowed method, of class com.sun.electric.database.ImmutableArcInst.
     */
    @Test
    public void testIsArrowed() {
        System.out.println("isArrowed");
        assertFalse(a0.isArrowed(0));
        assertFalse(a0.isArrowed(1));
    }

    /**
     * Test of isArrowed method, of class com.sun.electric.database.ImmutableArcInst.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testIsArrowedFail() {
        System.out.println("isArrowedFail");
        a0.isArrowed(2);
    }

    /**
     * Test of isTailArrowed method, of class com.sun.electric.database.ImmutableArcInst.
     */
    @Test
    public void testIsTailArrowed() {
        System.out.println("isTailArrowed");
        assertFalse(a0.isTailArrowed());
    }

    /**
     * Test of isHeadArrowed method, of class com.sun.electric.database.ImmutableArcInst.
     */
    @Test
    public void testIsHeadArrowed() {
        System.out.println("isHeadArrowed");
        assertFalse(a0.isHeadArrowed());
    }

    /**
     * Test of isBodyArrowed method, of class com.sun.electric.database.ImmutableArcInst.
     */
    @Test
    public void testIsBodyArrowed() {
        System.out.println("isBodyArrowed");
        assertFalse(a0.isBodyArrowed());
    }

    /**
     * Test of isExtended method, of class com.sun.electric.database.ImmutableArcInst.
     */
    @Test
    public void testIsExtended() {
        System.out.println("isExtended");
        assertTrue(a0.isExtended(0));
        assertTrue(a0.isExtended(1));
    }

    /**
     * Test of isExtended method, of class com.sun.electric.database.ImmutableArcInst.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testIsExtendedFail() {
        System.out.println("isExtendedFail");
        a0.isExtended(-1);
    }

    /**
     * Test of isTailExtended method, of class com.sun.electric.database.ImmutableArcInst.
     */
    @Test
    public void testIsTailExtended() {
        System.out.println("isTailExtended");
        assertTrue(a0.isTailExtended());
    }

    /**
     * Test of isHeadExtended method, of class com.sun.electric.database.ImmutableArcInst.
     */
    @Test
    public void testIsHeadExtended() {
        System.out.println("isHeadExtended");
        assertTrue(a0.isHeadExtended());
    }

    /**
     * Test of isNegated method, of class com.sun.electric.database.ImmutableArcInst.
     */
    @Test
    public void testIsNegated() {
        System.out.println("isNegated");
        assertFalse(a0.isNegated(0));
        assertFalse(a0.isNegated(1));
    }

    /**
     * Test of isNegated method, of class com.sun.electric.database.ImmutableArcInst.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testIsNegatedFail() {
        System.out.println("isNegatedFail");
        a0.isExtended(-2);
    }

    /**
     * Test of isTailNegated method, of class com.sun.electric.database.ImmutableArcInst.
     */
    @Test
    public void testIsTailNegated() {
        System.out.println("isTailNegated");
        assertFalse(a0.isTailNegated());
    }

    /**
     * Test of isHeadNegated method, of class com.sun.electric.database.ImmutableArcInst.
     */
    @Test
    public void testIsHeadNegated() {
        System.out.println("isHeadNegated");
        assertFalse(a0.isHeadNegated());
    }

    private boolean isEasyShape(ImmutableArcInst a) {
        return techPool.getArcProto(a.protoId).isEasyShape(a, false);
    }

    /**
     * Test of isEasyShape method, of class com.sun.electric.database.ImmutableArcInst.
     */
    @Test
    public void testIsEasyShape() {
        System.out.println("isEasyShape");
        assertTrue(isEasyShape(a0));
    }

    /**
     * Test of newInstance method, of class com.sun.electric.database.ImmutableArcInst.
     */
    @Test
    public void testNewInstance() {
        System.out.println("newInstance");

        TextDescriptor td = TextDescriptor.newTextDescriptor(new MutableTextDescriptor()).withParam(true);
        ImmutableArcInst a1 = ImmutableArcInst.newInstance(0, apId, nameA0, td, 0, ppId, n0.anchor, 1, ppId, n1.anchor, apExtend, 0, ImmutableArcInst.DEFAULT_FLAGS);
        a1.check();
        assertTrue(a1.nameDescriptor.isDisplay());
        assertFalse(a1.nameDescriptor.isParam());

        ImmutableArcInst a2 = ImmutableArcInst.newInstance(0, apId, nameA0, null, 0, ppId, n0.anchor, 0, ppId, n0.anchor, apExtend, -1, ImmutableArcInst.DEFAULT_FLAGS);
        a2.check();
        assertEquals(-1, a2.getAngle());

        ImmutableArcInst a3 = ImmutableArcInst.newInstance(0, apId, nameA0, null, 1, ppId, n1.anchor, 0, ppId, n0.anchor, apExtend, 0, ImmutableArcInst.DEFAULT_FLAGS);
        a3.check();
        assertEquals(1800, a3.getAngle());

        int flags = ImmutableArcInst.TAIL_NEGATED.set(ImmutableArcInst.DEFAULT_FLAGS, true);
        ImmutableArcInst a4 = ImmutableArcInst.newInstance(0, apId, nameA0, null, 0, ppId, n0.anchor, 1, ppId, n1.anchor, apExtend, 0, flags);
        a4.check();
        assertTrue(a4.isTailNegated());
        assertTrue(a4.is(ImmutableArcInst.TAIL_NEGATED));

        ImmutableArcInst a5 = ImmutableArcInst.newInstance(0, apId, nameA0, null, 1, ppId, n1.anchor, 0, ppId, n0.anchor, -1, 0, ImmutableArcInst.DEFAULT_FLAGS);
        a5.check();
        assertEquals(1800, a5.getAngle());

    }

    /**
     * Test of newInstance method, of class com.sun.electric.database.ImmutableArcInst.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testNewInstanceBadArcId() {
        System.out.println("newInstanceBadArcId");
        ImmutableArcInst.newInstance(-1, apId, nameA0, null, 0, ppId, n0.anchor, 1, ppId, n1.anchor, apExtend, 0, ImmutableArcInst.DEFAULT_FLAGS);
    }

    /**
     * Test of newInstance method, of class com.sun.electric.database.ImmutableArcInst.
     */
    @Test(expected = NullPointerException.class)
    public void testNewInstanceBadProto() {
        System.out.println("newInstanceBadProto");
        ImmutableArcInst.newInstance(0, null, nameA0, null, 0, ppId, n0.anchor, 1, ppId, n1.anchor, apExtend, 0, ImmutableArcInst.DEFAULT_FLAGS);
    }

    /**
     * Test of newInstance method, of class com.sun.electric.database.ImmutableArcInst.
     */
    @Test(expected = NullPointerException.class)
    public void testNewInstanceBadName1() {
        System.out.println("newInstanceBadName1");
        ImmutableArcInst.newInstance(0, apId, null, null, 0, ppId, n0.anchor, 1, ppId, n1.anchor, apExtend, 0, ImmutableArcInst.DEFAULT_FLAGS);
    }

    /**
     * Test of newInstance method, of class com.sun.electric.database.ImmutableArcInst.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testNewInstanceBadName2() {
        System.out.println("newInstanceBadName2");
        ImmutableArcInst.newInstance(0, apId, Name.findName("a:"), null, 0, ppId, n0.anchor, 1, ppId, n1.anchor, apExtend, 0, ImmutableArcInst.DEFAULT_FLAGS);
    }

    /**
     * Test of newInstance method, of class com.sun.electric.database.ImmutableArcInst.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testNewInstanceBadName3() {
        System.out.println("newInstanceBadName3");
        ImmutableArcInst.newInstance(0, apId, Name.findName("a,"), null, 0, ppId, n0.anchor, 1, ppId, n1.anchor, apExtend, 0, ImmutableArcInst.DEFAULT_FLAGS);
    }

    /**
     * Test of newInstance method, of class com.sun.electric.database.ImmutableArcInst.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testNewInstanceBadName4() {
        System.out.println("newInstanceBadName4");
        ImmutableArcInst.newInstance(0, apId, Name.findName("Net@0"), null, 0, ppId, n0.anchor, 1, ppId, n1.anchor, apExtend, 0, ImmutableArcInst.DEFAULT_FLAGS);
    }

    /**
     * Test of newInstance method, of class com.sun.electric.database.ImmutableArcInst.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testNewInstanceBadName5() {
        System.out.println("newInstanceBadName5");
        ImmutableArcInst.newInstance(0, apId, Name.findName("net@0[0:1]"), null, 0, ppId, n0.anchor, 1, ppId, n1.anchor, apExtend, 0, ImmutableArcInst.DEFAULT_FLAGS);
    }

    /**
     * Test of newInstance method, of class com.sun.electric.database.ImmutableArcInst.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testNewInstanceBadTailNodeId() {
        System.out.println("newInstanceBadTailNodeId");
        ImmutableArcInst.newInstance(0, apId, nameA0, null, -1, ppId, n0.anchor, 1, ppId, n1.anchor, apExtend, 0, ImmutableArcInst.DEFAULT_FLAGS);
    }

    /**
     * Test of newInstance method, of class com.sun.electric.database.ImmutableArcInst.
     */
    @Test(expected = NullPointerException.class)
    public void testNewInstanceBadTailPortId() {
        System.out.println("newInstanceBadTailPortId");
        ImmutableArcInst.newInstance(0, apId, nameA0, null, 0, null, n0.anchor, 1, ppId, n1.anchor, apExtend, 0, ImmutableArcInst.DEFAULT_FLAGS);
    }

    /**
     * Test of newInstance method, of class com.sun.electric.database.ImmutableArcInst.
     */
    @Test(expected = NullPointerException.class)
    public void testNewInstanceBadTailLocation() {
        System.out.println("newInstanceBadTailLocation");
        ImmutableArcInst.newInstance(0, apId, nameA0, null, 0, ppId, null, 1, ppId, n1.anchor, apExtend, 0, ImmutableArcInst.DEFAULT_FLAGS);
    }

    /**
     * Test of newInstance method, of class com.sun.electric.database.ImmutableArcInst.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testNewInstanceBadHeadNodeId() {
        System.out.println("newInstanceBadHeadNodeId");
        ImmutableArcInst.newInstance(0, apId, nameA0, null, 0, ppId, n0.anchor, -1, ppId, n1.anchor, apExtend, 0, ImmutableArcInst.DEFAULT_FLAGS);
    }

    /**
     * Test of newInstance method, of class com.sun.electric.database.ImmutableArcInst.
     */
    @Test(expected = NullPointerException.class)
    public void testNewInstanceBadHeadPortId() {
        System.out.println("newInstanceBadHeadPortId");
        ImmutableArcInst.newInstance(0, apId, nameA0, null, 0, ppId, n0.anchor, 1, null, n1.anchor, apExtend, 0, ImmutableArcInst.DEFAULT_FLAGS);
    }

    /**
     * Test of newInstance method, of class com.sun.electric.database.ImmutableArcInst.
     */
    @Test(expected = NullPointerException.class)
    public void testNewInstanceBadHeadLocation() {
        System.out.println("newInstanceBadHeadLocation");
        ImmutableArcInst.newInstance(0, apId, nameA0, null, 0, ppId, n0.anchor, 1, ppId, null, apExtend, 0, ImmutableArcInst.DEFAULT_FLAGS);
    }

    /**
     * Test of newInstance method, of class com.sun.electric.database.ImmutableArcInst.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testNewInstanceBadExtend1() {
        System.out.println("newInstanceBadExtend1");
        ImmutableArcInst.newInstance(0, apId, nameA0, null, 0, ppId, n0.anchor, 1, ppId, n1.anchor, DBMath.lambdaToGrid(3000000), 0, ImmutableArcInst.DEFAULT_FLAGS);
    }

    /**
     * Test of newInstance method, of class com.sun.electric.database.ImmutableArcInst.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testNewInstanceBadExtend2() {
        System.out.println("newInstanceBadExtend2");
        ImmutableArcInst.newInstance(0, apId, nameA0, null, 0, ppId, n0.anchor, 1, ppId, n1.anchor, DBMath.lambdaToGrid(-3000000), 0, ImmutableArcInst.DEFAULT_FLAGS);
    }

    /**
     * Test of withName method, of class com.sun.electric.database.ImmutableArcInst.
     */
    @Test
    public void testWithName() {
        System.out.println("withName");

        assertSame(a0, a0.withName(a0.name));
        a0.check();

        Name nameB = Name.findName("b");
        ImmutableArcInst ab = a0.withName(nameB);
        assertSame(nameA0, a0.name);
        assertSame(nameB, ab.name);
        ab.check();
    }

    /**
     * Test of withName method, of class com.sun.electric.database.ImmutableArcInst.
     */
    @Test(expected = NullPointerException.class)
    public void testWithNameBad1() {
        System.out.println("withNameBad1");
        a0.withName(null);
    }

    /**
     * Test of withName method, of class com.sun.electric.database.ImmutableArcInst.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testWithNameBad2() {
        System.out.println("withNameBad2");
        a0.withName(Name.findName("a:"));
    }

    /**
     * Test of withName method, of class com.sun.electric.database.ImmutableArcInst.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testWithNameBad3() {
        System.out.println("withNameBad3");
        a0.withName(Name.findName("a,"));
    }

    /**
     * Test of withName method, of class com.sun.electric.database.ImmutableArcInst.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testWithNameBad4() {
        System.out.println("withNameBad4");
        a0.withName(Name.findName("Net@0"));
    }

    /**
     * Test of withName method, of class com.sun.electric.database.ImmutableArcInst.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testWithNameBad5() {
        System.out.println("withNameBad5");
        a0.withName(Name.findName("net@0[0:1]"));
    }

    /**
     * Test of withNameDescriptor method, of class com.sun.electric.database.ImmutableArcInst.
     */
    @Test
    public void testWithNameDescriptor() {
        System.out.println("withNameDescriptor");
        assertSame(a0, a0.withNameDescriptor(a0.nameDescriptor));

        TextDescriptor td = TextDescriptor.newTextDescriptor(new MutableTextDescriptor()).withParam(true).withUnderline(true);
        ImmutableArcInst a1 = a0.withNameDescriptor(td);
        a1.check();
        assertTrue(a1.nameDescriptor.isDisplay());
        assertFalse(a1.nameDescriptor.isParam());
        assertTrue(a1.nameDescriptor.isUnderline());
    }

    /**
     * Test of withLocations method, of class com.sun.electric.database.ImmutableArcInst.
     */
    @Test
    public void testWithLocations() {
        System.out.println("withLocations");
        assertSame(a0, a0.withLocations(a0.tailLocation, a0.headLocation));

        ImmutableArcInst a1 = a0.withLocations(n1.anchor, n0.anchor);
        a1.check();
        assertSame(n1.anchor, a1.tailLocation);
        assertSame(n0.anchor, a1.headLocation);
        assertEquals(1800, a1.getAngle());
        assertTrue(isEasyShape(a1));

        ImmutableArcInst a2 = a0.withLocations(n0.anchor, EPoint.fromGrid(1500 * 1000 * 1000, n1.anchor.getGridY()));
        a2.check();
        assertTrue(isEasyShape(a2));
    }

    /**
     * Test of withLocations method, of class com.sun.electric.database.ImmutableArcInst.
     */
    @Test(expected = NullPointerException.class)
    public void testWithLocationsBad() {
        System.out.println("withLocationsBad");
        assertSame(a0, a0.withLocations(a0.tailLocation, null));
    }

    /**
     * Test of withGridExtendOverMin method, of class com.sun.electric.database.ImmutableArcInst.
     */
    @Test
    public void testWithExtendOverMin() {
        System.out.println("withGridExtendOverMin");
        assertSame(a0, a0.withGridExtendOverMin(a0.getGridExtendOverMin()));

        long largeExtendOverMin = 130 * 1000 * 1000;
        ImmutableArcInst a1 = a0.withGridExtendOverMin(largeExtendOverMin);
        assertEquals(largeExtendOverMin, a1.getGridExtendOverMin());
        assertTrue(isEasyShape(a1));

        long smallExtendOverMin = -1;
        ImmutableArcInst a2 = a0.withGridExtendOverMin(smallExtendOverMin);
        assertEquals(smallExtendOverMin, a2.getGridExtendOverMin());
        assertTrue(isEasyShape(a2));
    }

    /**
     * Test of withGridExtendOverMin method, of class com.sun.electric.database.ImmutableArcInst.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testWithGridExtendOverMinBad1() {
        System.out.println("withGridExtendOverMinBad1");
        a0.withGridExtendOverMin(DBMath.lambdaToGrid(3000000));
    }

    /**
     * Test of withGridExtendOverMin method, of class com.sun.electric.database.ImmutableArcInst.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testWithGridExtendOverMinBad2() {
        System.out.println("withGridExtendOverMinBad2");
        a0.withGridExtendOverMin(DBMath.lambdaToGrid(-3000000));
    }

    /**
     * Test of withAngle method, of class com.sun.electric.database.ImmutableArcInst.
     */
    @Test
    public void testWithAngle() {
        System.out.println("withAngle");
        assertSame(a0, a0.withAngle(900)); // If locations are different, angle is calcualted from them

        ImmutableArcInst a1 = a0.withLocations(a0.tailLocation, a0.tailLocation).withAngle(900);
        a1.check();
        assertEquals(900, a1.getAngle());
        assertTrue(isEasyShape(a1));

        ImmutableArcInst a2 = a1.withAngle(-1);
        a2.check();
        assertEquals(-1, a2.getAngle());
        assertTrue(isEasyShape(a2));
    }

    /**
     * Test of withFlags method, of class com.sun.electric.database.ImmutableArcInst.
     */
    @Test
    public void testWithFlags() {
        System.out.println("withFlags");
        assertSame(a0, a0.withFlags(a0.flags));

        assertSame(a0, a0.withFlags(a0.flags | (1 << 31)));
    }

    /**
     * Test of withFlag method, of class com.sun.electric.database.ImmutableArcInst.
     */
    @Test
    public void testWithFlag() {
        System.out.println("withFlag");
        assertTrue(a0.withFlag(ImmutableArcInst.TAIL_NEGATED, true).is(ImmutableArcInst.TAIL_NEGATED)); // layout arc can't have negated end
        assertTrue(a0.withFlag(ImmutableArcInst.HEAD_NEGATED, true).is(ImmutableArcInst.HEAD_NEGATED)); // layout arc can't have negated end

        ImmutableArcInst a1 = a0.withFlag(ImmutableArcInst.BODY_ARROWED, true);
        a1.check();
        assertTrue(a1.is(ImmutableArcInst.BODY_ARROWED));
        assertTrue(a1.isBodyArrowed());
        assertFalse(isEasyShape(a1));
    }

    /**
     * Test of withVariable method, of class com.sun.electric.database.ImmutableArcInst.
     */
    @Test
    public void testWithVariable() {
        System.out.println("withVariable");
        Variable var = Variable.newInstance(Artwork.ART_COLOR, "valueA", TextDescriptor.newTextDescriptor(new MutableTextDescriptor()));
        ImmutableArcInst a1 = a0.withVariable(var);
        a1.check();
        assertEquals(1, a1.getNumVariables());
        assertSame(var, a1.getVar(0));
        assertTrue(isEasyShape(a1));

        Variable var1 = var.withParam(true);
        ImmutableArcInst a2 = a0.withVariable(var1);
        a2.check();
        assertEquals(1, a2.getNumVariables());
        assertSame(Artwork.ART_COLOR, a2.getVar(0).getKey());
        assertSame(var.getObject(), a2.getVar(0).getObject());
        assertFalse(a2.getVar(0).getTextDescriptor().isParam());

        ImmutableArcInst a3 = ImmutableArcInst.newInstance(0, artwork.solidArc.getId(), nameA0, null,
                0, artwork.pinNode.getPort(0).getId(), EPoint.ORIGIN,
                0, artwork.pinNode.getPort(0).getId(), EPoint.ORIGIN,
                0, 0, ImmutableArcInst.DEFAULT_FLAGS);
        a3.check();
        assertTrue(isEasyShape(a3));

        ImmutableArcInst a4 = a3.withVariable(var);
        a4.check();
        assertFalse(isEasyShape(a4));
    }

    /**
     * Test of withoutVariable method, of class com.sun.electric.database.ImmutableArcInst.
     */
    @Test
    public void testWithoutVariable() {
        System.out.println("withoutVariable");
        Variable var = Variable.newInstance(Artwork.ART_COLOR, "valueA", TextDescriptor.newTextDescriptor(new MutableTextDescriptor()));
        ImmutableArcInst a1 = ImmutableArcInst.newInstance(0, artwork.solidArc.getId(), nameA0, null,
                0, artwork.pinNode.getPort(0).getId(), EPoint.ORIGIN,
                0, artwork.pinNode.getPort(0).getId(), EPoint.ORIGIN,
                0, 0, ImmutableArcInst.DEFAULT_FLAGS).withVariable(var);
        a1.check();
        assertFalse(isEasyShape(a1));

        assertSame(a1, a1.withoutVariable(Artwork.ART_PATTERN));

        ImmutableArcInst a2 = a1.withoutVariable(Artwork.ART_COLOR);
        assertEquals(0, a2.getNumVariables());
        assertTrue(isEasyShape(a2));
    }

    /**
     * Test of withRenamedIds method, of class com.sun.electric.database.ImmutableArcInst.
     */
    @Test
    public void testWithRenamedIds() {
        System.out.println("withRenamedIds");
        IdMapper idMapper = new IdMapper();
        assertSame(a0, a0.withRenamedIds(idMapper));
    }

    /**
     * Test of write and read method, of class com.sun.electric.database.ImmutableArcInst.
     */
    @Test
    public void testReadWrite() {
        System.out.println("readReadWrite");

        try {
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
            IdWriter writer = new IdWriter(idManager, new DataOutputStream(byteStream));
            a0.write(writer);
            writer.flush();
            byte[] bytes = byteStream.toByteArray();
            byteStream.reset();

            // First update of mirrorIdManager
            IdReader reader = new IdReader(new DataInputStream(new ByteArrayInputStream(bytes)), idManager);

            // Check mirrorIdManager after first update
            ImmutableArcInst a1 = ImmutableArcInst.read(reader);
            a1.check();
            assertEquals(a0.arcId, a1.arcId);
            assertSame(a0.protoId, a1.protoId);
            assertSame(a0.name, a1.name);
            assertSame(a0.nameDescriptor, a1.nameDescriptor);
            assertEquals(a0.tailNodeId, a1.tailNodeId);
            assertSame(a0.tailPortId, a1.tailPortId);
            assertEquals(a0.tailLocation, a1.tailLocation);
            assertEquals(a0.headNodeId, a1.headNodeId);
            assertSame(a0.headPortId, a1.headPortId);
            assertEquals(a0.headLocation, a1.headLocation);
            assertEquals(a0.getGridExtendOverMin(), a1.getGridExtendOverMin());
            assertEquals(a0.getAngle(), a1.getAngle());
            assertEquals(a0.flags, a1.flags);
        } catch (IOException e) {
            fail(e.getMessage());
        }
    }

    /**
     * Test of hashCodeExceptVariables method, of class com.sun.electric.database.ImmutableArcInst.
     */
    @Test
    public void testHashCodeExceptVariables() {
        System.out.println("hashCodeExceptVariables");
        assertEquals(a0.arcId, a0.hashCodeExceptVariables());
    }

    /**
     * Test of equalsExceptVariables method, of class com.sun.electric.database.ImmutableArcInst.
     */
    @Test
    public void testEqualsExceptVariables() {
        System.out.println("equalsExceptVariables");
        assertTrue(a0.equalsExceptVariables(a0));
        assertFalse(a0.equalsExceptVariables(a0.withName(Name.findName("B"))));
        assertFalse(a0.equalsExceptVariables(a0.withLocations(EPoint.ORIGIN, EPoint.ORIGIN)));
        Variable var = Variable.newInstance(Artwork.ART_COLOR, new Integer(5), TextDescriptor.newTextDescriptor(new MutableTextDescriptor()));
        assertTrue(a0.equalsExceptVariables(a0.withVariable(var)));
    }

    /**
     * Test of makeGridPoly method, of class com.sun.electric.database.ImmutableArcInst.
     */
    @Test
    public void testMakeGridPoly() {
        System.out.println("makeGridPoly");
        ImmutableCell c = ImmutableCell.newInstance(cellId, 0).withTechId(techId);
        ImmutableNodeInst[] nodes = {n0, n1};
        CellBackup cellBackup0 = CellBackup.newInstance(c, techPool).with(c, nodes, null, null, techPool);
        MyBuilder b = new MyBuilder();
        for (int angle = 0; angle < 3600; angle++) {
            EPoint p1 = EPoint.fromLambda(n0.anchor.getLambdaX() + 10 * GenMath.cos(angle), n0.anchor.getLambdaY() + 10 * GenMath.sin(angle));
            ImmutableArcInst a1 = ImmutableArcInst.newInstance(0, apId, nameA0, null, 0, ppId, n0.anchor, 1, ppId, p1, apExtend, 0, ImmutableArcInst.DEFAULT_FLAGS);
            assertEquals(angle, a1.getAngle());
            ImmutableArcInst[] arcs = {a1};
            CellBackup cellBackup = cellBackup0.with(c, null, arcs, null, techPool);
            b.setTest(cellBackup, a1, 1000);
            b.makeGridPoly(a1, 1000, Poly.Type.FILLED, null, null);
        }
    }

    private static class MyBuilder extends AbstractShapeBuilder {

        ImmutableArcInst a;
        double w2;

        void setTest(CellBackup cellBackup, ImmutableArcInst a, long width) {
            setup(cellBackup, null, false, false, false, null);
            this.a = a;
            w2 = width * 0.5;
            pointCount = 0;
        }

        @Override
        public void addPoly(int numPoints, Poly.Type style, Layer layer, EGraphics graphicsOverride, PrimitivePort pp) {
            assertEquals(4, pointCount);
            int angle = a.getAngle();
            assertTrue(angle % 900 != 0);
            long w2x = (long) Math.rint(GenMath.cos(angle) * w2 * SHAPE_SCALE);
            long w2y = (long) Math.rint(GenMath.sin(angle) * w2 * SHAPE_SCALE);
            assertEquals((double) a.tailLocation.getGridX() * SHAPE_SCALE - w2x - w2y, (double) coords[0], 0);
            assertEquals((double) a.tailLocation.getGridY() * SHAPE_SCALE + w2x - w2y, (double) coords[1], 0);
            assertEquals((double) a.tailLocation.getGridX() * SHAPE_SCALE - w2x + w2y, (double) coords[2], 0);
            assertEquals((double) a.tailLocation.getGridY() * SHAPE_SCALE - w2x - w2y, (double) coords[3], 0);
            assertEquals((double) a.headLocation.getGridX() * SHAPE_SCALE + w2x + w2y, (double) coords[4], 0);
            assertEquals((double) a.headLocation.getGridY() * SHAPE_SCALE - w2x + w2y, (double) coords[5], 0);
            assertEquals((double) a.headLocation.getGridX() * SHAPE_SCALE + w2x - w2y, (double) coords[6], 0);
            assertEquals((double) a.headLocation.getGridY() * SHAPE_SCALE + w2x + w2y, (double) coords[7], 0);
            assertNull(pp);
        }

        @Override
        public void addBox(Layer layer) {
            int angle = a.getAngle();
            assertTrue(angle % 900 == 0);
            assertTrue(w2 == (long) w2);
            assertEquals((Math.min(a.tailLocation.getGridX(), a.headLocation.getGridX()) - w2) * SHAPE_SCALE, (double) coords[0], 0);
            assertEquals((Math.min(a.tailLocation.getGridY(), a.headLocation.getGridY()) - w2) * SHAPE_SCALE, (double) coords[1], 0);
            assertEquals((Math.max(a.tailLocation.getGridX(), a.headLocation.getGridX()) + w2) * SHAPE_SCALE, (double) coords[2], 0);
            assertEquals((Math.max(a.tailLocation.getGridY(), a.headLocation.getGridY()) + w2) * SHAPE_SCALE, (double) coords[3], 0);
        }
    }

//    /**
//     * Test of registerShrinkage method, of class com.sun.electric.database.ImmutableArcInst.
//     */
//    @Test public void testRegisterShrinkage() {
//        System.out.println("registerShrinkage");
//
//        int[] shrinkageState = new int[2];
//        a0.registerShrinkage(shrinkageState);
//        assertTrue(shrinkageState[a0.tailNodeId] != 0);
//        assertTrue(shrinkageState[a0.headNodeId] != 0);
//    }
//
//    /**
//     * Test of computeShrink method, of class com.sun.electric.database.ImmutableArcInst.
//     */
//    @Test public void testComputeShrink0() {
//        System.out.println("computeShrink0");
//        int[] shrinkageState = new int[2];
//        a0.registerShrinkage(shrinkageState);
//        assertEquals((short)0, ImmutableArcInst.computeShrink(shrinkageState[0]));
//        assertEquals((short)0, ImmutableArcInst.computeShrink(shrinkageState[1]));
//    }
//
//    /**
//     * Test of computeShrink method, of class com.sun.electric.database.ImmutableArcInst.
//     */
//    @Test public void testComputeShrink1() {
//        System.out.println("computeShrink1");
//        int[] shrinkageState = new int[1];
//        ImmutableArcInst a1 = ImmutableArcInst.newInstance(0, ap, nameA0, null, 0, pp, n0.anchor, 0, pp, n0.anchor, DBMath.lambdaToGrid(15), 300, ImmutableArcInst.DEFAULT_FLAGS);
//        a1.registerShrinkage(shrinkageState);
//        assertEquals((short)0, ImmutableArcInst.computeShrink(shrinkageState[0]));
//    }
//
//    /**
//     * Test of computeShrink method, of class com.sun.electric.database.ImmutableArcInst.
//     */
//    @Test public void testComputeShrink2() {
//        System.out.println("computeShrink2");
//        int[] shrinkageState = new int[3];
//        ImmutableArcInst a1 = ImmutableArcInst.newInstance(0, ap, nameA0, null, 2, pp, n1.anchor, 1, pp, n1.anchor, DBMath.lambdaToGrid(15), 1500, ImmutableArcInst.DEFAULT_FLAGS);
//        a0.registerShrinkage(shrinkageState);
//        a1.registerShrinkage(shrinkageState);
//        assertEquals((short)1502, ImmutableArcInst.computeShrink(shrinkageState[1]));
//    }
//
//    /**
//     * Test of computeShrink method, of class com.sun.electric.database.ImmutableArcInst.
//     */
//    @Test public void testComputeShrink3() {
//        System.out.println("computeShrink3");
//        int[] shrinkageState = new int[3];
//        ImmutableArcInst a1 = ImmutableArcInst.newInstance(0, ap, nameA0, null, 2, pp, n1.anchor, 1, pp, n1.anchor, DBMath.lambdaToGrid(15), 1500, ImmutableArcInst.DEFAULT_FLAGS);
//        a1 = a1.withFlag(ImmutableArcInst.HEAD_EXTENDED, false);
//        a0.registerShrinkage(shrinkageState);
//        a1.registerShrinkage(shrinkageState);
//        assertEquals((short)0, ImmutableArcInst.computeShrink(shrinkageState[1]));
//    }
//
//    /**
//     * Test of computeShrink method, of class com.sun.electric.database.ImmutableArcInst.
//     */
//    @Test public void testComputeShrink4() {
//        System.out.println("computeShrink4");
//        for (int angle1 = 0; angle1 < 3600; angle1 += 100) {
//            EPoint p0 = EPoint.fromLambda(n1.anchor.getLambdaX() - 1000*GenMath.cos(angle1), n1.anchor.getLambdaY() - 1000*GenMath.sin(angle1));
//            ImmutableArcInst a1 = ImmutableArcInst.newInstance(0, ap, nameA0, null, 0, pp, p0, 1, pp, n1.anchor, DBMath.lambdaToGrid(15), 0, ImmutableArcInst.DEFAULT_FLAGS);
//            for (int angle2 = 0; angle2 < 3600; angle2++) {
//                EPoint p2 = EPoint.fromLambda(n1.anchor.getLambdaX() - 1100*GenMath.cos(angle2), n1.anchor.getLambdaY() - 1100*GenMath.sin(angle2));
//                ImmutableArcInst a2 = ImmutableArcInst.newInstance(0, ap, nameA0, null, 1, pp, n1.anchor, 2, pp, p2, DBMath.lambdaToGrid(15), 0, ImmutableArcInst.DEFAULT_FLAGS);
//                int[] shrinkageState = new int[3];
//                a1.registerShrinkage(shrinkageState);
//                a2.registerShrinkage(shrinkageState);
//                assertEquals((short)0, ImmutableArcInst.computeShrink(shrinkageState[0]));
//                assertEquals((short)0, ImmutableArcInst.computeShrink(shrinkageState[2]));
//                short shrink = 0; // EXTEND_90
//                if (angle1 % 900 != 0 || angle2 % 900 != 0) {
//                    int diff = angle1 - angle2;
//                    while (diff < 0) diff += 3600;
//                    while (diff >= 3600) diff -= 3600;
//                    if (diff == 1800)
//                        shrink = 1; // EXTEND_0
//                    else if (diff > 900 && diff < 2700) {
//                        shrink = (short)(2 + (angle1 + angle2) % 3600);
//                    }
//                }
//                assertEquals(shrink,  ImmutableArcInst.computeShrink(shrinkageState[1]));
//            }
//        }
//    }
    /**
     * Test of getRadius method, of class com.sun.electric.database.ImmutableArcInst.
     */
    @Test
    public void testGetRadius() {
        System.out.println("getRadius");
        assertNull(a0.getRadius());
    }

    /**
     * Test of curvedArcGridOutline method, of class com.sun.electric.database.ImmutableArcInst.
     */
    public void testCurvedArcGridOutline() {
        System.out.println("curvedArcGridOutline");

//        Poly.Type style = null;
//        long gridWidth = 0L;
//        long gridRadius = 0L;
//        ImmutableArcInst instance = null;
//
//        Poly expResult = null;
//        Poly result = instance.curvedArcGridOutline(style, gridWidth, gridRadius);
//        assertEquals(expResult, result);

        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }

    /**
     * Test of check method, of class com.sun.electric.database.ImmutableArcInst.
     */
    public void testCheck() {
        System.out.println("check");

        ImmutableArcInst instance = null;

        instance.check();

        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }

    /**
     * Test of getElibBits method, of class com.sun.electric.database.ImmutableArcInst.
     */
    @Test
    public void testGetElibBits() {
        System.out.println("getElibBits");
        // DEFAULT
        toElib(000000000000, ImmutableArcInst.TAIL_EXTENDED, ImmutableArcInst.HEAD_EXTENDED, ImmutableArcInst.SLIDABLE);

        // single bits
        toElib(000040400000);
        toElib(000040400001, ImmutableArcInst.RIGID);
        toElib(000040400002, ImmutableArcInst.FIXED_ANGLE);
        toElib(000000400000, ImmutableArcInst.SLIDABLE);
        toElib(020040400000, ImmutableArcInst.HARD_SELECT);

        // NOEXTEND
        toElib(000040000000, ImmutableArcInst.TAIL_EXTENDED, ImmutableArcInst.HEAD_EXTENDED);
        toElib(000040400000);
        toElib(000044400000, ImmutableArcInst.TAIL_EXTENDED);
        toElib(000050400000, ImmutableArcInst.HEAD_EXTENDED);

        // ISDIRECTIONAL
        toElib(000052000000, ImmutableArcInst.BODY_ARROWED, ImmutableArcInst.TAIL_EXTENDED, ImmutableArcInst.HEAD_EXTENDED);
        toElib(000042000000, ImmutableArcInst.HEAD_ARROWED, ImmutableArcInst.TAIL_EXTENDED, ImmutableArcInst.HEAD_EXTENDED);
        toElib(000042000000, ImmutableArcInst.BODY_ARROWED, ImmutableArcInst.HEAD_ARROWED, ImmutableArcInst.TAIL_EXTENDED, ImmutableArcInst.HEAD_EXTENDED);
        toElib(000062000000, ImmutableArcInst.TAIL_ARROWED, ImmutableArcInst.TAIL_EXTENDED, ImmutableArcInst.HEAD_EXTENDED);
        toElib(000062000000, ImmutableArcInst.BODY_ARROWED, ImmutableArcInst.TAIL_ARROWED, ImmutableArcInst.TAIL_EXTENDED, ImmutableArcInst.HEAD_EXTENDED);
        toElib(000062000000, ImmutableArcInst.BODY_ARROWED, ImmutableArcInst.TAIL_ARROWED, ImmutableArcInst.HEAD_ARROWED, ImmutableArcInst.TAIL_EXTENDED, ImmutableArcInst.HEAD_EXTENDED);
        toElib(000062000000, ImmutableArcInst.TAIL_ARROWED, ImmutableArcInst.HEAD_ARROWED, ImmutableArcInst.TAIL_EXTENDED, ImmutableArcInst.HEAD_EXTENDED);
    }

    private void toElib(int elibFlags, ImmutableArcInst.Flag... flags) {
        int flagBits = 0;
        for (ImmutableArcInst.Flag f : flags) {
            flagBits = f.set(flagBits, true);
        }
        assertEquals(elibFlags, a0.withFlags(flagBits).getElibBits());
    }

    /**
     * Test of flagsFromElib method, of class com.sun.electric.database.ImmutableArcInst.
     */
    @Test
    public void testFlagsFromElib() {
        System.out.println("flagsFromElib");
        // default
        fromElib(000000000000, ImmutableArcInst.TAIL_EXTENDED, ImmutableArcInst.HEAD_EXTENDED, ImmutableArcInst.SLIDABLE);

        // single bits
        fromElib(000040400001, ImmutableArcInst.RIGID);
        fromElib(000040400002, ImmutableArcInst.FIXED_ANGLE);
        fromElib(000000400000, ImmutableArcInst.SLIDABLE);
        fromElib(020040400000, ImmutableArcInst.HARD_SELECT);

        // REVERSEEND, NOTEND0, NOTEND1
        fromElib(000040000000, ImmutableArcInst.TAIL_EXTENDED, ImmutableArcInst.HEAD_EXTENDED);
        fromElib(000044000000, ImmutableArcInst.TAIL_EXTENDED, ImmutableArcInst.HEAD_EXTENDED);
        fromElib(000050000000, ImmutableArcInst.TAIL_EXTENDED, ImmutableArcInst.HEAD_EXTENDED);
        fromElib(000054000000, ImmutableArcInst.TAIL_EXTENDED, ImmutableArcInst.HEAD_EXTENDED);
        fromElib(000060000000, ImmutableArcInst.TAIL_EXTENDED, ImmutableArcInst.HEAD_EXTENDED);
        fromElib(000064000000, ImmutableArcInst.TAIL_EXTENDED, ImmutableArcInst.HEAD_EXTENDED);
        fromElib(000070000000, ImmutableArcInst.TAIL_EXTENDED, ImmutableArcInst.HEAD_EXTENDED);
        fromElib(000074000000, ImmutableArcInst.TAIL_EXTENDED, ImmutableArcInst.HEAD_EXTENDED);

        // NOEXTEND
        fromElib(000040400000);
        fromElib(000044400000, ImmutableArcInst.TAIL_EXTENDED);
        fromElib(000050400000, ImmutableArcInst.HEAD_EXTENDED);
        fromElib(000054400000, ImmutableArcInst.TAIL_EXTENDED, ImmutableArcInst.HEAD_EXTENDED);
        fromElib(000060400000);
        fromElib(000064400000, ImmutableArcInst.TAIL_EXTENDED);
        fromElib(000070400000, ImmutableArcInst.HEAD_EXTENDED);
        fromElib(000074400000, ImmutableArcInst.TAIL_EXTENDED, ImmutableArcInst.HEAD_EXTENDED);

        // ISHEADNEGATED
        fromElib(000040200000, ImmutableArcInst.HEAD_NEGATED, ImmutableArcInst.TAIL_EXTENDED, ImmutableArcInst.HEAD_EXTENDED);
        fromElib(000044200000, ImmutableArcInst.HEAD_NEGATED, ImmutableArcInst.TAIL_EXTENDED, ImmutableArcInst.HEAD_EXTENDED);
        fromElib(000050200000, ImmutableArcInst.HEAD_NEGATED, ImmutableArcInst.TAIL_EXTENDED, ImmutableArcInst.HEAD_EXTENDED);
        fromElib(000054200000, ImmutableArcInst.HEAD_NEGATED, ImmutableArcInst.TAIL_EXTENDED, ImmutableArcInst.HEAD_EXTENDED);
        fromElib(000060200000, ImmutableArcInst.TAIL_NEGATED, ImmutableArcInst.TAIL_EXTENDED, ImmutableArcInst.HEAD_EXTENDED);
        fromElib(000064200000, ImmutableArcInst.TAIL_NEGATED, ImmutableArcInst.TAIL_EXTENDED, ImmutableArcInst.HEAD_EXTENDED);
        fromElib(000070200000, ImmutableArcInst.TAIL_NEGATED, ImmutableArcInst.TAIL_EXTENDED, ImmutableArcInst.HEAD_EXTENDED);
        fromElib(000074200000, ImmutableArcInst.TAIL_NEGATED, ImmutableArcInst.TAIL_EXTENDED, ImmutableArcInst.HEAD_EXTENDED);

        // ISTAILNEGATED
        fromElib(000041000000, ImmutableArcInst.TAIL_NEGATED, ImmutableArcInst.TAIL_EXTENDED, ImmutableArcInst.HEAD_EXTENDED);
        fromElib(000045000000, ImmutableArcInst.TAIL_NEGATED, ImmutableArcInst.TAIL_EXTENDED, ImmutableArcInst.HEAD_EXTENDED);
        fromElib(000051000000, ImmutableArcInst.TAIL_NEGATED, ImmutableArcInst.TAIL_EXTENDED, ImmutableArcInst.HEAD_EXTENDED);
        fromElib(000055000000, ImmutableArcInst.TAIL_NEGATED, ImmutableArcInst.TAIL_EXTENDED, ImmutableArcInst.HEAD_EXTENDED);
        fromElib(000061000000, ImmutableArcInst.HEAD_NEGATED, ImmutableArcInst.TAIL_EXTENDED, ImmutableArcInst.HEAD_EXTENDED);
        fromElib(000065000000, ImmutableArcInst.HEAD_NEGATED, ImmutableArcInst.TAIL_EXTENDED, ImmutableArcInst.HEAD_EXTENDED);
        fromElib(000071000000, ImmutableArcInst.HEAD_NEGATED, ImmutableArcInst.TAIL_EXTENDED, ImmutableArcInst.HEAD_EXTENDED);
        fromElib(000075000000, ImmutableArcInst.HEAD_NEGATED, ImmutableArcInst.TAIL_EXTENDED, ImmutableArcInst.HEAD_EXTENDED);

        // ISHEADNEGATED | ISTAILNEGATED
        fromElib(000041200000, ImmutableArcInst.TAIL_NEGATED, ImmutableArcInst.HEAD_NEGATED, ImmutableArcInst.TAIL_EXTENDED, ImmutableArcInst.HEAD_EXTENDED);
        fromElib(000045200000, ImmutableArcInst.TAIL_NEGATED, ImmutableArcInst.HEAD_NEGATED, ImmutableArcInst.TAIL_EXTENDED, ImmutableArcInst.HEAD_EXTENDED);
        fromElib(000051200000, ImmutableArcInst.TAIL_NEGATED, ImmutableArcInst.HEAD_NEGATED, ImmutableArcInst.TAIL_EXTENDED, ImmutableArcInst.HEAD_EXTENDED);
        fromElib(000055200000, ImmutableArcInst.TAIL_NEGATED, ImmutableArcInst.HEAD_NEGATED, ImmutableArcInst.TAIL_EXTENDED, ImmutableArcInst.HEAD_EXTENDED);
        fromElib(000061200000, ImmutableArcInst.TAIL_NEGATED, ImmutableArcInst.HEAD_NEGATED, ImmutableArcInst.TAIL_EXTENDED, ImmutableArcInst.HEAD_EXTENDED);
        fromElib(000065200000, ImmutableArcInst.TAIL_NEGATED, ImmutableArcInst.HEAD_NEGATED, ImmutableArcInst.TAIL_EXTENDED, ImmutableArcInst.HEAD_EXTENDED);
        fromElib(000071200000, ImmutableArcInst.TAIL_NEGATED, ImmutableArcInst.HEAD_NEGATED, ImmutableArcInst.TAIL_EXTENDED, ImmutableArcInst.HEAD_EXTENDED);
        fromElib(000075200000, ImmutableArcInst.TAIL_NEGATED, ImmutableArcInst.HEAD_NEGATED, ImmutableArcInst.TAIL_EXTENDED, ImmutableArcInst.HEAD_EXTENDED);

        // ISDIRECTIONAL
        fromElib(000042000000, ImmutableArcInst.BODY_ARROWED, ImmutableArcInst.HEAD_ARROWED, ImmutableArcInst.TAIL_EXTENDED, ImmutableArcInst.HEAD_EXTENDED);
        fromElib(000046000000, ImmutableArcInst.BODY_ARROWED, ImmutableArcInst.HEAD_ARROWED, ImmutableArcInst.TAIL_EXTENDED, ImmutableArcInst.HEAD_EXTENDED);
        fromElib(000052000000, ImmutableArcInst.BODY_ARROWED, ImmutableArcInst.TAIL_EXTENDED, ImmutableArcInst.HEAD_EXTENDED);
        fromElib(000056000000, ImmutableArcInst.BODY_ARROWED, ImmutableArcInst.TAIL_EXTENDED, ImmutableArcInst.HEAD_EXTENDED);
        fromElib(000062000000, ImmutableArcInst.BODY_ARROWED, ImmutableArcInst.TAIL_ARROWED, ImmutableArcInst.TAIL_EXTENDED, ImmutableArcInst.HEAD_EXTENDED);
        fromElib(000066000000, ImmutableArcInst.BODY_ARROWED, ImmutableArcInst.TAIL_EXTENDED, ImmutableArcInst.HEAD_EXTENDED);
        fromElib(000072000000, ImmutableArcInst.BODY_ARROWED, ImmutableArcInst.TAIL_ARROWED, ImmutableArcInst.TAIL_EXTENDED, ImmutableArcInst.HEAD_EXTENDED);
        fromElib(000076000000, ImmutableArcInst.BODY_ARROWED, ImmutableArcInst.TAIL_EXTENDED, ImmutableArcInst.HEAD_EXTENDED);
    }

    private void fromElib(int elibFlags, ImmutableArcInst.Flag... flags) {
        int flagBits = 0;
        for (ImmutableArcInst.Flag f : flags) {
            flagBits = f.set(flagBits, true);
        }
        assertEquals(flagBits, ImmutableArcInst.flagsFromElib(elibFlags));
    }

    /**
     * Test of angleFromElib method, of class com.sun.electric.database.ImmutableArcInst.
     */
    @Test
    public void testAngleFromElib() {
        System.out.println("angleFromElib");
        assertEquals(0, ImmutableArcInst.angleFromElib(0));
        assertEquals(0, ImmutableArcInst.angleFromElib((1 << 5) - 1));
        assertEquals(0, ImmutableArcInst.angleFromElib(1 << 14));
        assertEquals(10, ImmutableArcInst.angleFromElib(1 << 5));
        assertEquals(3590, ImmutableArcInst.angleFromElib(359 << 5));
        assertEquals(0, ImmutableArcInst.angleFromElib(360 << 5));
    }
}
