/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: LayoutCellLoader.java
 *
 * Copyright (c) 2011, Static Free Software. All rights reserved.
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
package com.sun.electric.api.minarea;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;

/**
 * Loader from ".lay" format
 */
public class LayoutCellLoader {

    private LayoutCellLoader() {
    }

    /**
     * Load LayoutCell hierarchy from serialized stream
     * @param in input stream
     * @return top LayoutCell loaded from input stream
     * @throws ClassNotFoundException
     * @throws IOException 
     */
    public static LayoutCell load(InputStream in) throws ClassNotFoundException, IOException {
        ObjectInputStream oin = new ObjectInputStream(new BufferedInputStream(in));
        return (LayoutCell) oin.readObject();
    }
}
