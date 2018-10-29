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
 *  An unboxed which is <tt>Comparable</tt>, and for which the
 *  comparison can be performed directly on the unboxed form.
 */
public interface UnboxedComparable<V extends Serializable & Comparable>
    extends Unboxed<V> {

    /** Same as Comparable.compare(), but operates directly on serialized representation */
    public int compare(byte[] buf1, int ofs1, byte[] buf2, int ofs2);
  
}
