/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: ConcurrentCollectionFactory.java
 *
 * Copyright (c) 2010, Static Free Software. All rights reserved.
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
package com.sun.electric.util;

import com.sun.electric.util.collections.ImmutableList;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * This class provides factory methods for creating data structures. The
 * intension is that the generic generation of data structures <T> should be
 * hidden to make the code readable.
 *
 * @author Felix Schmidt
 *
 */
public class CollectionFactory {

    private CollectionFactory() {
    }

    public static <T> ArrayList<T> createArrayList() {
        return new ArrayList<T>();
    }

    /**
     * Create a new hash set
     *
     * @param <T>
     * @return HashSet of type T
     */
    public static <T> HashSet<T> createHashSet() {
        return new HashSet<T>();
    }

    @SuppressWarnings("unchecked")
    public static <T> List<T> createConcurrentList() {
        return (List<T>) Collections.synchronizedList(createArrayList());
    }

    public static <T> ConcurrentLinkedQueue<T> createConcurrentLinkedQueue() {
        return new ConcurrentLinkedQueue<T>();
    }

    public static <T, V> ConcurrentHashMap<T, V> createConcurrentHashMap() {
        return new ConcurrentHashMap<T, V>();
    }

    @SuppressWarnings("unchecked")
    public static <T> Set<T> createConcurrentHashSet() {
        return (Set<T>) Collections.synchronizedSet(createHashSet());
    }

    public static <T> Set<T> copySetToConcurrent(Set<T> source) {

        Set<T> result = createConcurrentHashSet();

        doCopyCollection(source, result);

        return result;

    }

    public static <T> LinkedList<T> createLinkedList() {
        return new LinkedList<T>();
    }

    private static <T> void doCopyCollection(Collection<T> source, Collection<T> dest) {
        for (Iterator<T> it = source.iterator(); it.hasNext();) {
            dest.add(it.next());
        }
    }

    public static <T> T threadSafeListGet(int index, List<T> list) {
        synchronized (list) {
            return list.get(index);
        }
    }

    public static <T> T threadSafeListRemove(int index, List<T> list) {
        synchronized (list) {
            return list.remove(index);
        }
    }

    /**
     *
     * @param <T>
     * @param item
     * @param list
     */
    public static <T> void threadSafeListAdd(T item, List<T> list) {
        synchronized (list) {
            list.add(item);
        }
    }

    public static <T, K> HashMap<T, K> createHashMap() {
        return new HashMap<T, K>();
    }

    public static <T, K> LinkedHashMap<T, K> createLinkedHashMap() {
        return new LinkedHashMap<T, K>();
    }

    /**
     *
     * @param <T>
     * @param source
     * @return a copy of this.
     */
    public static <T> Set<T> copySet(Set<T> source) {
        Set<T> result = CollectionFactory.createHashSet();

        doCopyCollection(source, result);

        return result;
    }

    public static <T> Set<T> copyListToSet(List<T> source) {
        Set<T> result = CollectionFactory.createHashSet();

        doCopyCollection(source, result);

        return result;
    }

    public static <T> List<T> copySetToList(Set<T> source) {
        List<T> result = CollectionFactory.createArrayList();

        doCopyCollection(source, result);

        return result;
    }

    public static <T> ImmutableList<T> copyListToImmutableList(List<T> source) {
        ImmutableList<T> immutableList = null;
        for (T element : source) {
            immutableList = ImmutableList.addFirst(immutableList, element);
        }
        return immutableList;
    }

    public static <T> T[] arrayMerge(T[]... arrays) {

        Class objectClass = null;
        int count = 0;
        for (T[] array : arrays) {
            if (array != null) {
                count += array.length;
                if (array.length > 0) {
                    objectClass = array[0].getClass();
                }
            }
        }
        if (objectClass == null) {
            return null;
        }
        List<T> mergedList = new ArrayList<T>();

        for (T[] array : arrays) {
            if (array != null) {
                mergedList.addAll(Arrays.asList(array));
            }
        }

        return mergedList.toArray((T[]) Array.newInstance(objectClass, count));

    }
}
