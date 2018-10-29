/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Wiretype.java
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

import static com.sun.electric.util.acl2.ACL2.*;
import com.sun.electric.util.acl2.ACL2Object;

/**
 *
 * Wiretype as defined in an svex wire.
 * See <http://www.cs.utexas.edu/users/moore/acl2/manuals/current/manual/?topic=SV____WIRETYPE>.
 */
public enum Wiretype
{
    WIRE, SUPPLY0, SUPPLY1, WAND, WOR, TRI0, TRI1, TRIREG;

    private final ACL2Object impl;

    private Wiretype()
    {
        impl = name().equals("WIRE") ? NIL : ACL2Object.valueOf("KEYWORD", name());
    }

    public ACL2Object getACL2Object()
    {
        return impl;
    }

    public static Wiretype valueOf(ACL2Object impl)
    {
        for (Wiretype wt : Wiretype.values())
        {
            if (wt.impl.equals(impl))
            {
                return wt;
            }
        }
        throw new IllegalArgumentException();
    }
}
