/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Util.java
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
import java.util.ArrayList;
import java.util.List;

/**
 * Utilities for SVEX design
 */
public class Util
{
    public static final ACL2Object SV_TOP = ACL2Object.valueOf("SV", "TOP");
    public static final ACL2Object SV_MODALIST = ACL2Object.valueOf("SV", "MODALIST");
    public static final ACL2Object SV_WIRES = ACL2Object.valueOf("SV", "WIRES");
    public static final ACL2Object SV_INSTS = ACL2Object.valueOf("SV", "INSTS");
    public static final ACL2Object SV_ASSIGNS = ACL2Object.valueOf("SV", "ASSIGNS");
    public static final ACL2Object SV_ALIASPAIRS = ACL2Object.valueOf("SV", "ALIASPAIRS");

    public static final ACL2Object KEYWORD_VAR = ACL2Object.valueOf("KEYWORD", "VAR");
    public static final ACL2Object KEYWORD_Z = ACL2Object.valueOf("KEYWORD", "Z");
    public static final ACL2Object KEYWORD_SELF = ACL2Object.valueOf("KEYWORD", "SELF");
    public static final ACL2Object KEYWORD_ANONYMOIUS = ACL2Object.valueOf("KEYWORD", "ANONYMOIUS");

    public static void check(boolean p)
    {
        if (!p)
        {
            throw new RuntimeException();
        }
    }

    public static void checkNil(ACL2Object x)
    {
        check(NIL.equals(x));
    }

    public static void checkNotNil(ACL2Object x)
    {
        check(!NIL.equals(x));
    }

    public static List<ACL2Object> getList(ACL2Object l, boolean trueList)
    {
        List<ACL2Object> result = new ArrayList<>();
        while (consp(l).bool())
        {
            result.add(car(l));
            l = cdr(l);
        }
        if (trueList)
        {
            checkNil(l);
        }
        return result;
    }

    public static ACL2Object revList(ACL2Object list)
    {
        ACL2Object rev = NIL;
        while (consp(list).bool())
        {
            rev = cons(car(list), rev);
            list = cdr(list);
        }
        return rev;
    }

}
