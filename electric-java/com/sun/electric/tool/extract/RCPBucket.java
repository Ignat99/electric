/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: RCPBucket.java
 *
 * Copyright (c) 2005, Static Free Software. All rights reserved.
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

package com.sun.electric.tool.extract;

import com.sun.electric.technology.Technology;

/**
 * The main purpose of this class is to store parasitic information as string.
 * It will cover parasitics for Capacitors and Resistors (schematics)
 */
public class RCPBucket implements ExtractedPBucket
{
    private char type;
    public String net1;
    public String net2;
    public double rcValue;

    public RCPBucket(char type, String net1, String net2, double rcValue)
    {
        this.type = type;
        this.net1 = net1;
        this.net2 = net2;
        this.rcValue = rcValue;
    }

    public char getType() {return type;}

    /**
     * Method to be used to retrieve information while printing the deck.
     */
    public String getInfo(Technology tech)
    {
        String info = type + " " + net1 + " " + net2 + " " + rcValue;;
        return info;
    }
}
