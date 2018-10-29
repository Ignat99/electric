/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: ImmutableList.java
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
 * @author Felix Schmidt
 * Immutable single-linked list.
 * Empty list is represented by null.
 */
public class ImmutableList<T> implements Iterable<T> {

    private final ImmutableList<T> next;
    private final T item;

    private ImmutableList(ImmutableList<T> next, T item) {
        this.next = next;
        this.item = item;
    }
    
    /**
     * First item
     * @return first item
     */
    public T getFirst() {
        return item;
    }
    
    /**
     * List without first item
     * @return tail 
     */
    public ImmutableList<T> getTail() {
        return next;
    }

    /**
     * Returns empty list
     * @param <T> type of item
     * @return empty list
     */
    public static <T> ImmutableList<T> empty() {
        return null;
    }
    
    /**
     * Prepend an item to a list. Old list remains unmodified.
     * @param <T> type of item
     * @param list a list
     * @param item item to prepend
     * @return New list that consists of the old list with the item prepended.
     */
    public static <T> ImmutableList<T> addFirst(ImmutableList<T> list, T item) {
        return new ImmutableList<T>(list, item);
    }

    /**
     * Returns a list with first occurance of an item removed.
     * Occurance is determined by reference identity, not by equality.
     * @param <T> type of item
     * @param list original list
     * @param item item to remove
     * @return new list without first occurance of the item.
     */
    public static <T> ImmutableList<T> remove(ImmutableList<T> list, T item) {
        if (list == null) {
            return null;
        }
        return list.remove(item);
    }

    /**
     * Reverse a list
     *
     * @param <T> type of items
     * @param l list to reverse
     * @return reversed list
     */
    public static <T> ImmutableList<T> reverse(ImmutableList<T> l) {
        ImmutableList<T> r = null;
        while (l != null) {
            r = new ImmutableList<T>(r, l.item);
            l = l.next;
        }
        return r;
    }
    
    private ImmutableList<T> remove(T target) {
        if (this.item == target) {
            return this.next;
        } else {
            ImmutableList<T> new_next = remove(this.next, target);
            if (new_next == this.next) {
                return this;
            }
            return new ImmutableList<T>(new_next, item);
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see java.lang.Iterable#iterator()
     */
    @Override
    public Iterator<T> iterator() {
        return new ImmutableListIterator<T>(this);
    }

    public static class ImmutableListIterator<T> implements Iterator<T> {

        private ImmutableList<T> list;

        public ImmutableListIterator(ImmutableList<T> list) {
            this.list = list;
        }

        /*
         * (non-Javadoc)
         *
         * @see java.util.Iterator#hasNext()
         */
        @Override
        public boolean hasNext() {
            return list != null;
        }

        /*
         * (non-Javadoc)
         *
         * @see java.util.Iterator#next()
         */
        @Override
        public T next() {
            if (list != null) {
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
}
