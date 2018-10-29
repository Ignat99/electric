/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: IndexName.java
 *
 * Copyright (c) 2017, Static Free Software. All rights reserved.
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
package com.sun.electric.tool.simulation.acl2.mods;

import com.sun.electric.tool.simulation.acl2.svex.SvarName;
import static com.sun.electric.util.acl2.ACL2.*;
import com.sun.electric.util.acl2.ACL2Object;
import java.math.BigInteger;

/**
 *
 */
public class IndexName extends Name implements SvarName
{
    private final int index;

    public static ElabMod curElabMod = null;

    IndexName(int index)
    {
        if (index < 0)
        {
            throw new IllegalArgumentException();
        }
        this.index = index;
    }

    public static IndexName valueOf(int index)
    {
        return new IndexName(index);
    }

    @Override
    public boolean isInteger()
    {
        return true;
    }

    @Override
    public boolean isSimpleSvarName()
    {
        return false;
    }

    @Override
    public boolean equals(Object o)
    {
        return o instanceof IndexName && index == ((IndexName)o).index;
    }

    @Override
    public int hashCode()
    {
        return ACL2Object.hashCodeOf(index);
    }

    @Override
    public ACL2Object getACL2Object()
    {
        return honscopy(ACL2Object.valueOf(index));
    }

    @Override
    public String toString()
    {
        return toString(null);
    }

    @Override
    public String toString(BigInteger mask)
    {
        String s = "{" + index;
        if (mask != null)
        {
            s += "#" + mask.toString(16);
        }
        s += "}";
        if (curElabMod != null)
        {
            s += curElabMod.wireidxToPath(getIndex());
        }
        return s;
    }

    @Override
    public String toLispString()
    {
        return "'" + index;
    }

    public Name asName()
    {
        return Name.valueOf(index);
    }

    public Path asPath()
    {
        return Path.simplePath(asName());
    }

    public Address asAddress()
    {
        return new Address(asPath());
    }

    public int getIndex()
    {
        return index;
    }
}
