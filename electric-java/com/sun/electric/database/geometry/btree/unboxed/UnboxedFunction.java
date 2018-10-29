/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: UnboxedFunction.java
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
 *  A function from one Unboxed to another.
 */
public interface UnboxedFunction<A extends Serializable, B extends Serializable> {

    /**
     *  The function <b>MUST</b> support situations where the argument
     *  and return buffers overlap.
     */
    public void call(byte[] buf_arg, int ofs_arg,
                     byte[] buf_result, int ofs_result);

}
