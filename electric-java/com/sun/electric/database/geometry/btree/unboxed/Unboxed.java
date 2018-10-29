/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: BTree.java
 *
 * Copyright (c) 2009, Static Free Software. All rights reserved.
 *
 * Electric(tm) is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * Electric(tm) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sun.electric.database.geometry.btree.unboxed;

import java.io.*;

/**
 *  A class which knows how to intrepret sequences of bytes as an
 *  object.
 *
 *  We use this singleton class which performs operations (such as
 *  comparisons) directly on the byte[]'s rather than using some
 *  instance of Serializable&Comparable because the latter approach
 *  puts heavy stress on the garbage collector and -- much more
 *  importantly -- the memory subsystem.  By not instantiating
 *  heavyweight objects for the keys we let the whole program live
 *  lower in the meomory hierarchy.  GC is nice, except in your
 *  innermost loops.
 */
public interface Unboxed<V extends Serializable> {

    /** Return the size, in bytes, of a value; all values must have the same size */
    public int getSize();

    /** Deserialize a value; <b>need not be compatible with <tt>V.readObject()</tt></b>! */
    public V deserialize(byte[] buf, int ofs);
    
    /** Serialize a value; <b>need not be compatible with <tt>V.writeObject()</tt></b>! */
    public void serialize(V v, byte[] buf, int ofs);

}
