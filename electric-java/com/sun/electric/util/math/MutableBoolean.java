/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: MutableBoolean.java
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

/**
 * Class to define a Boolean object that can be modified.
 */
public class MutableBoolean implements Serializable {

    private boolean value;

    /**
     * Constructor creates a MutableBoolean object with an initial value.
     * @param value the initial value.
     */
    public MutableBoolean(boolean value) {
        this.value = value;
    }

    /**
     * Method to change the value of this MutableBoolean.
     * @param value the new value.
     */
    public void setValue(boolean value) {
        this.value = value;
    }

    /**
     * Method to return the value of this MutableBoolean.
     * @return the current value of this MutableBoolean.
     */
    public boolean booleanValue() {
        return value;
    }

    /**
     * Returns a printable version of this MutableBoolean.
     * @return a printable version of this MutableBoolean.
     */
    @Override
    public String toString() {
        return Boolean.toString(value);
    }
}
