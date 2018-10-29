/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: MutableDouble.java
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

/**
 * Class to define an Double-like object that can be modified.
 */
public class MutableDouble {

    private double value;

    /**
     * Constructor creates a MutableDouble object with an initial value.
     * @param value the initial value.
     */
    public MutableDouble(double value) {
        this.value = value;
    }

    /**
     * Method to change the value of this MutableDouble.
     * @param value the new value.
     */
    public void setValue(double value) {
        this.value = value;
    }

    /**
     * Method to return the value of this MutableDouble.
     * @return the current value of this MutableDouble.
     */
    public double doubleValue() {
        return value;
    }

    /**
     * Returns a printable version of this MutableDouble.
     * @return a printable version of this MutableDouble.
     */
    @Override
    public String toString() {
        return Double.toString(value);
    }
}
