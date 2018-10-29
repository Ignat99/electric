/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: MutableInteger.java
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
package com.sun.electric.util.math;

import java.io.Serializable;
import java.util.Map;

/**
 * Class to define an Integer-like object that can be modified.
 */
public class MutableInteger implements Serializable {

    private int value;

    /**
     * Constructor creates a MutableInteger object with an initial value.
     * @param value the initial value.
     */
    public MutableInteger(int value) {
        this.value = value;
    }

    /**
     * Method to change the value of this MutableInteger.
     * @param value the new value.
     */
    public void setValue(int value) {
        this.value = value;
    }

    /**
     * Method to add the value of this MutableInteger.
     * @param value the value to add.
     */
    public void addValue(int value) {
        this.value += value;
    }

    /**
     * Method to increment this MutableInteger by 1.
     */
    public void increment() {
        value++;
    }

    /**
     * Method to increment this MutableInteger by 1.
     */
    public void decrement() {
        value--;
    }

    /**
     * Method to return the value of this MutableInteger.
     * @return the current value of this MutableInteger.
     */
    public int intValue() {
        return value;
    }

    /**
     * Returns a printable version of this MutableInteger.
     * @return a printable version of this MutableInteger.
     */
    @Override
    public String toString() {
        return Integer.toString(value);
    }

    /**
     * Increments count to object in a bag.
     * If object was not in a bag, it will be added.
     * @param bag Map implementing Bag.
     * @param key object to add to bag.
     */
    public static <T> void addToBag(Map<T, MutableInteger> bag, T key) {
        addToBag(bag, key, 1);
    }

    /**
     * Adds to bag another bag.
     * @param bag bag to update.
     * @param otherBag bag used for update.
     */
    public static <T> void addToBag(Map<T, MutableInteger> bag,
            Map<T, MutableInteger> otherBag) {
        for (Map.Entry<T, MutableInteger> e : otherBag.entrySet()) {
            MutableInteger count = e.getValue();
            addToBag(bag, e.getKey(), count.intValue());
        }
    }

    /**
     * Adds to count of object in a bag.
     * If object was not in a bag, it will be added.
     * @param bag Map implementing Bag.
     * @param key object in a bag.
     * @param c count to add to bag.
     */
    public static <T> void addToBag(Map<T, MutableInteger> bag, T key, int c) {
        MutableInteger count = bag.get(key);
        if (count == null) {
            count = new MutableInteger(0);
            bag.put(key, count);
        }
        count.setValue(count.intValue() + c);
    }

    /**
     * Method to return the a value at a location in a collection.
     * @param bag the collection (a Map).
     * @param key a key to an entry in the collection.
     * @return the value at that key.
     */
    public static <T> int countInBag(Map<T, MutableInteger> bag, T key) {
        MutableInteger count = bag.get(key);
        return count != null ? count.intValue() : 0;
    }
}
