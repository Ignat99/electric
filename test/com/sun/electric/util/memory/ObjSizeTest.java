/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: ObjSizeTest.java
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
 * along with Electric(tm); see the file COPYING.  If not, write to
 * the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
 * Boston, Mass 02111-1307, USA.
 */
package com.sun.electric.util.memory;

import com.sun.electric.database.geometry.EPoint;
import com.sun.electric.util.collections.ImmutableList;
import com.sun.electric.util.collections.ImmutableList2;
import java.awt.Point;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Vector;
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
public class ObjSizeTest {

    public ObjSizeTest() {
    }

    @BeforeClass
    public static void setUpClass() {
    }

    @AfterClass
    public static void tearDownClass() {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    /**
     * Test of detect method, of class ObjSize.
     */
    @Test
    public void testCurrent() {
        assertNotNull(ObjSize.current());
    }

    /**
     * Test of calcSize method, of class ObjSize.
     */
    @Test
    public void testSize() {
        System.out.println("Size");
        check(new Object(), 8, 16, 16);
        check(new Point(0, 0), 16, 24, 24);
        check(EPoint.fromLambda(0, 1), 16, 24, 24);
        check(EPoint.fromLambda(0, 1e9), 24, 32, 32);
    }

    /**
     * Test of calcSize method, of class ObjSize.
     */
    @Test
    public void testListHeasderSize() {
        System.out.println("ListHeaderSize");
        check(new ArrayList<String>(), 24, 24, 40);
        check(new Vector<String>(), 24, 32, 40);
//        check(new LinkedList<String>(), 24, 24, 40);
        check(ImmutableList.addFirst(ImmutableList.empty(), ""), 16, 24, 32);
        check(ImmutableList2.empty().addFirst(""), 16, 24, 32);
    }
    
    /**
     * Test of calcArraySize method, of class ObjSize.
     */
    @Test
    public void testBooleanArraySize() {
        System.out.println("BooleanArraySize");
        check(new boolean[0], 16, 16, 24);
        check(new boolean[1], 16, 24, 32);
        check(new boolean[2], 16, 24, 32);
        check(new boolean[3], 16, 24, 32);
        check(new boolean[4], 16, 24, 32);
        check(new boolean[5], 24, 24, 32);
        check(new boolean[6], 24, 24, 32);
        check(new boolean[7], 24, 24, 32);
        check(new boolean[8], 24, 24, 32);
        check(new boolean[9], 24, 32, 40);
    }

    public static void check(Object obj, long sizeJDK32, long sizeJDK64Compressed, long sizeJDK64Uncompressed) {
        assertEquals(sizeJDK32, ObjSize.JDK32.sizeOf(obj));
        assertEquals(sizeJDK64Compressed, ObjSize.JDK64Compressed.sizeOf(obj));
        assertEquals(sizeJDK64Uncompressed, ObjSize.JDK64Uncompressed.sizeOf(obj));
        Class cls = obj.getClass();
        if (cls.isArray()) {
            int len = Array.getLength(obj);
            Class itemType = cls.getComponentType();
            assertEquals(sizeJDK32, ObjSize.JDK32.sizeOfArray(itemType, len));
            assertEquals(sizeJDK64Compressed, ObjSize.JDK64Compressed.sizeOfArray(itemType, len));
            assertEquals(sizeJDK64Uncompressed, ObjSize.JDK64Uncompressed.sizeOfArray(itemType, len));
        } else {
            assertEquals(sizeJDK32, ObjSize.JDK32.sizeOfClassInstance(cls));
            assertEquals(sizeJDK64Compressed, ObjSize.JDK64Compressed.sizeOfClassInstance(cls));
            assertEquals(sizeJDK64Uncompressed, ObjSize.JDK64Uncompressed.sizeOfClassInstance(cls));
        }
    }
}
