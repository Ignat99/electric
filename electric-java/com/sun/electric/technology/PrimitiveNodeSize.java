/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: TransistorSize.java
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

package com.sun.electric.technology;

import com.sun.electric.database.text.TextUtils;
import com.sun.electric.database.variable.VarContext;

/**
 * Holds the Width and Length of a PrimitiveNode that is a transistor.
 * This holds the width, length, and area as objects, because the width and length,
 * may be specified as strings if they are java code, or just numbers.
 */
public class PrimitiveNodeSize {
    protected final Object width; // by default width is along X
    protected final Object length;// by default length is along Y
    private final boolean widthAlongX; // by default the poly is aligned along X. Poly aligned X => width == X, length == Y

    /**
	 * Constructor creates a PrimitiveNodeSize with a given size.
	 * @param width the width of the PrimitiveNodeSize.
     * @param length the length of the PrimitiveNodeSize.
     * @param widthOnX
     */
    public PrimitiveNodeSize(Object width, Object length, boolean widthOnX) {
        this.width = width;
        this.length = length;
        this.widthAlongX = widthOnX;
    }

    /**
	 * Method to return the width of this TransistorSize.
	 * @return the width of this TransistorSize.
	 */
    public Object getWidth() {return width;}

	/**
	 * Method to return the length of this TransistorSize.
	 * @return the length of this TransistorSize.
	 */
    public Object getLength() {return length; }
    /**
     * Gets the width *ONLY IF* the width can be converted to a double.
     * i.e. it is a Number or a parsable String. If it is some other type,
     * this method returns zero.
     * @return the width.
     */
    public double getDoubleWidth() {
    	return VarContext.objectToDouble(width, 0);
    }

    /**
     * Gets the length *ONLY IF* the length can be converted to a double.
     * i.e. it is a Number or a parsable String. If it is some other type,
     * this method returns zero.
     * @return the length.
     */
    public double getDoubleLength() {
        return VarContext.objectToDouble(length, 0);
    }

    /**
     * Method to get correct value along X axis. This is critical for resistors
     * and transistors whose poly is along Y
     * @return the correct X value.
     */
    public double getDoubleAlongX()
    {
        return (widthAlongX) ? getDoubleWidth() : getDoubleLength();
    }

    /**
     * Method to get correct value along Y axis. This is critical for resistors
     * and transistors whose poly is along Y.
     * @return correct Y value.
     */
    public double getDoubleAlongY()
    {
        return (!widthAlongX) ? getDoubleWidth() : getDoubleLength();
    }

    /**
     * Method to return the actual width of the element based on the object
     * used to store the information. Most of the time is a Double but
     * Schematics might use different values depending on the Varialbles stored.
     * @return String represented the value
     */
    public String getWidthInString()
    {
        double width = getDoubleWidth();
        Object obj = getWidth();
        if (width == 0 && obj != null)
        {
            return obj.toString();
        }
        else
            return TextUtils.formatDistance(width);
    }

    /**
     * Method to return the actual length of the element based on the object
     * used to store the information. Most of the time is a Double but
     * Schematics might use different values depending on the Varialbles stored.
     * @return String represented the value
     */
    public String getLengthInString()
    {
        double length = getDoubleLength();
        Object obj = getLength();
        if (length == 0 && obj != null)
        {
            return obj.toString();
        }
        else
            return TextUtils.formatDistance(length);
    }
}
