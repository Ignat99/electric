/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: DisjointSet.java
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
package com.sun.electric.database.geometry.btree;

import java.io.*;
import java.util.*;

/**
 *  A <a href=http://en.wikipedia.org/wiki/Disjoint-set_data_structure>Disjoint
 *  Set</a> data structure.  This is more or less a HashSet which partitions
 *  its contents into zero or more disjoint equivalence classes.
 *
 *  XXX: ought to implement union-by-rank optimization
 *
 *  @author Adam Megacz
 */
public class EquivalenceClasses<V> {

    private final HashMap<V,V> map = new HashMap<V,V>();

    private V getRoot(V v) {
        V v2 = map.get(v);
        if (v2==null) return null;
        if (v2==v) return v2;
        v2 = getRoot(v2);
        map.put(v,v2);
        return v2;
    }

    /** add a new item, placing it in its own (singleton) equivalence class */
    public void insert(V v) {
        if (map.get(v)!=null) return;
        map.put(v,v);
    }

    /** merge the equivalence classes inhabited by v1 and v2 */
    public void merge(V v1, V v2) {
        if (map.get(v1)==null) insert(v1);
        if (map.get(v2)==null) insert(v2);
        map.put(getRoot(v1), getRoot(v2));
    }

    /** return true if v1 and v2 inhabit the same equivalence class */
    public boolean isEquivalent(V v1, V v2) {
        return getRoot(v1)==getRoot(v2);
    }

}
