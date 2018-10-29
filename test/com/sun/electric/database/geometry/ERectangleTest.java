/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: ERectangleTest.java
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
package com.sun.electric.database.geometry;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test of ERectangle
 */
public class ERectangleTest {

    ERectangle rect;

    @Before
    public void setUp() throws Exception {
        rect = ERectangle.fromLambda(100, 100, 10, 20);
    }

    public static junit.framework.Test suite() {
        return new junit.framework.JUnit4TestAdapter(ERectangleTest.class);
    }

    /**
     * Test of setRect method, of class com.sun.electric.database.geometry.ERectangle.
     */
    @Test
    public void testSerialization() {
        try {
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
            ObjectOutputStream out = new ObjectOutputStream(byteStream);
            out.writeObject(rect);
            out.close();
            byte[] serializedRect = byteStream.toByteArray();

            ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(serializedRect));
            ERectangle r = (ERectangle) in.readObject();
            in.close();

            assertEquals(rect, r);
        } catch (IOException e) {
            fail(e.getMessage());
        } catch (ClassNotFoundException e) {
            fail(e.getMessage());
        }

    }
}
