/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: LRUCache.java
 *
 * Copyright (c) 2014 Static Free Software
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
 *
 * This file (LRUCache.java) is also licensed under the BSD license,
 * at your option.
 */
package com.sun.electric.util;
import java.util.*;

/** A simple cache with an LRU (Least-Recently-Used) eviction policy; extend this class and override its sole abstract method. */
public abstract class LRUCache<K,V> {

    private final int cacheSize;
    private final LinkedHashMap<K,V> map;

    public LRUCache(final int cacheSize) {
        this.cacheSize = cacheSize;
        this.map = new LinkedHashMap<K,V>(cacheSize, 0.75f, true) {
            @Override protected boolean removeEldestEntry (Map.Entry<K,V> eldest) {
                return size() > cacheSize;
            }
        };
    }

    /** you implement this, it is called on cache misses */
    protected abstract V cacheMiss(K key);

    public V get(K key) {
        V ret = map.get(key);
        if (ret == null) ret = cacheMiss(key);
        map.put(key, ret); // always re-put to make this the most-recently-used entry
        return ret;
    }

}
