/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
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
import java.util.*;

/**
 *  Note that Map.Entry<A,B> is just the Java idiom for Pair<A,B>
 *  (there is no standard generic Pair).
 *
 *  JDK1.6 has AbstractMap.SimpleImmutableEntry for this.
 */
public class Pair<A,B> implements Serializable, Map.Entry<A,B> {
    private final A a;
    private final B b;
    public Pair(A a, B b) { this.a = a; this.b = b; }
    public B setValue(B b) { throw new Error("don't do this"); }
    public A getKey() { return a; }
    public B getValue() { return b; }
    public int hashCode() { return a.hashCode() ^ b.hashCode(); }
    public boolean equals(Object o) {
        if (o==null || !(o instanceof Pair)) return false;
        Pair<A,B> sme = (Pair<A,B>)o;
        return a.equals(sme.a) && b.equals(sme.b);
    }
}
