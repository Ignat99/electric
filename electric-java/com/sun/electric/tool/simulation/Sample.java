/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Signal.java
 *
 * Copyright (c) 2009, Static Free Software. All rights reserved.
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
package com.sun.electric.tool.simulation;

import java.io.Serializable;

/**
 *  A Sample is the data assigned to each point in time by a
 *  Signal.  Subclasses of Sample MUST BE IMMUTABLE.
 */
public interface Sample extends Serializable
{
    /** returns true iff this sample is "X" (unknown) */
    public boolean isLogicX();

    /** returns true iff this sample is "Z" (unconnected) */
    public boolean isLogicZ();

    public double getMinValue();

    public double getMaxValue();

    /**
     *  Return the least upper bound of this and s.  This is sort of
     *  like max(this,s) except that the result might be greater than
     *  either s or this if the class in question is a partial order
     *  rather than a linear order.
     */
    public Sample lub(Sample s);

    /**
     *  Return the greatest lower bound of this and s.  This is sort of
     *  like min(this,s) except that the result might be less than
     *  either s or this if the class in question is a partial order
     *  rather than a linear order.
     */
    public Sample glb(Sample s);
}

