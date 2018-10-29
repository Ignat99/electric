/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: WeakReferences.java
 * Written by: Dmitry Nadezhin.
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
package com.sun.electric.util.collections;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;

/**
 * A pool of weak reference objects, which do not prevent their referents from being
 * made finalizable, finalized, and then reclaimed. This class may be used for keeping
 * a pool of some Listeners. This class is not synchronized
 */
public class WeakReferences<E> {

    private final ArrayList<WeakReference<E>> references = new ArrayList<WeakReference<E>>();

    /**
     * Constructs an empty pool.
     */
    public WeakReferences() {
    }

    /**
     * Appends the specified element to the pool.
     * @param o specified element.
     * @return always true.
     */
    public boolean add(E o) {
        references.add(new WeakReference<E>(o));
        return true;
    }

    /**
     * Removes a single instance of the specified element from this
     * list, if it is present (optional operation).
     * Also purges references whose referents are not alive.
     * @param o speicified element.
     * @return true if element was removed.
     **/
    public boolean remove(E o) {
        for (Iterator<WeakReference<E>> it = references.iterator(); it.hasNext();) {
            WeakReference<E> ref = it.next();
            E i = ref.get();
            if (i == null) {
                it.remove();
                continue;
            }
            if (i == o) {
                it.remove();
                return true;
            }
        }
        return false;
    }

    /**
     * Returns an iterator over live elements in this pool in proper sequence.
     * Also purges refetences whose referents are not alive.
     * @return an iterator over the elements in this pool in proper sequence.
     */
    public Iterator<E> iterator() {
        return iterator(false);
    }

    /**
     * Returns an iterator over live elements in this pool in reverse sequence.
     * Also purges refetences whose referents are not alive.
     * @return an iterator over the elements in this pool in reverse sequence.
     */
    public Iterator<E> reverseIterator() {
        return iterator(true);
    }

    private Iterator<E> iterator(boolean reverse) {
        ArrayList<E> items = new ArrayList<E>();
        for (Iterator<WeakReference<E>> it = references.iterator(); it.hasNext();) {
            WeakReference<E> ref = it.next();
            E i = ref.get();
            if (i != null) {
                items.add(i);
            } else {
                it.remove();
            }
        }
        if (reverse) {
            Collections.reverse(items);
        }
        return items.iterator();
    }
}
