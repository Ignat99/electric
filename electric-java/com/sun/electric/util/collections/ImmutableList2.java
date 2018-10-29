/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: ImmutableList2.java
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
package com.sun.electric.util.collections;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * @author Felix Schmidt Immutable single-linked list. Empty list is represented
 * by singleton NIL object.
 */
public class ImmutableList2<T> implements Iterable<T> {

    private final ImmutableList2<T> next;
    private final T item;

    private ImmutableList2(ImmutableList2<T> next, T item) {
        this.next = next;
        this.item = item;
    }

    /**
     * Check for emptyness.
     * @return true if this list is empty.
     */
    public boolean isEmpty() {
        return false;
    }

    /**
     * First item
     *
     * @return first item
     */
    public T getFirst() {
        return item;
    }

    /**
     * List without first item
     *
     * @return tail
     */
    public ImmutableList2<T> getTail() {
        return next;
    }

    /**
     * Returns empty list
     *
     * @param <T> type of item
     * @return empty list
     */
    public static <T> ImmutableList2<T> empty() {
        return (ImmutableList2<T>) NIL;
    }

    /**
     * Prepend an item to a list. Old list remains unmodified.
     *
     * @param item item to prepend
     * @return New list that consists of the old list with the item prepended.
     */
    public ImmutableList2<T> addFirst(T item) {
        return new ImmutableList2<T>(this, item);
    }

    /**
     * Returns a list with first occurrence of an item removed. Occurrence is
     * determined by equality.
     *
     * @param item item to remove
     * @return new list without first occurrence of the item.
     */
    public ImmutableList2<T> remove(T item) {
        int count = 0;
        ImmutableList2<T> r = this;
        while (!r.isEmpty() && !(item == null ? r.item == null : item.equals(r.item))) {
            r = r.next;
            count++;
        }
        if (r.isEmpty()) {
            return this;
        }
        r = r.next;
        if (count == 0) {
            return r;
        }
        T[] firstItems = (T[]) new Object[count];
        ImmutableList2<T> l = this;
        for (int i = 0; i < count; i++) {
            firstItems[i] = l.item;
            l = l.next;
        }
        for (int i = count - 1; i >= 0; i--) {
            r = new ImmutableList2<T>(r, firstItems[i]);
        }
        return r;
    }

    /**
     * Reverse a list
     *
     * @return reversed list
     */
    public ImmutableList2<T> reverse() {
        ImmutableList2<T> l = this;
        ImmutableList2<T> r = empty();
        while (!l.isEmpty()) {
            r = new ImmutableList2<T>(r, l.item);
            l = l.next;
        }
        return r;
    }

    /*
     * (non-Javadoc)
     *
     * @see java.lang.Iterable#iterator()
     */
    @Override
    public Iterator<T> iterator() {
        return new ImmutableList2Iterator<T>(this);
    }

    public static class ImmutableList2Iterator<T> implements Iterator<T> {

        private ImmutableList2<T> list;

        public ImmutableList2Iterator(ImmutableList2<T> list) {
            this.list = list;
        }

        /*
         * (non-Javadoc)
         *
         * @see java.util.Iterator#hasNext()
         */
        @Override
        public boolean hasNext() {
            return !list.isEmpty();
        }

        /*
         * (non-Javadoc)
         *
         * @see java.util.Iterator#next()
         */
        @Override
        public T next() {
            if (!list.isEmpty()) {
                T obj = list.item;
                list = list.next;
                return obj;
            }
            throw new NoSuchElementException();
        }

        /*
         * (non-Javadoc)
         *
         * @see java.util.Iterator#remove()
         */
        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }
    /**
     * Empty list is a singleton object
     */
    private static final ImmutableList2<?> NIL = new ImmutableList2(null, null) {
        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public Object getFirst() {
            throw new NoSuchElementException();
        }

        @Override
        public ImmutableList2<?> getTail() {
            throw new NoSuchElementException();
        }

        @Override
        public Iterator<?> iterator() {
            return ArrayIterator.emptyIterator();
        }
    };
}
