/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Invertible.java
 *
 * Copyright (c) 2010, Static Free Software. All rights reserved.
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
 *  An invertible operation on unboxed values.
 *
 *  http://en.wikipedia.org/wiki/Group
 */
public interface InvertibleOperation<S extends Serializable>
    extends Unboxed<S> {

    /**
     *  Compute (buf,ofs)^-1 and write it to (buf_dest,ofs_dest).
     *  MUST support the case where (buf,ofs)==(buf_dest,ofs_dest).
     */
    public void invert(byte[] buf, int ofs,
                       byte[] buf_dest, int ofs_dest);
}
