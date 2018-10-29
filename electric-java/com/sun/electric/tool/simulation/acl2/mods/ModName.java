/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: ModName.java
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
import com.sun.electric.util.acl2.ACL2Backed;
import com.sun.electric.util.acl2.ACL2Object;

import java.util.HashMap;
import java.util.Map;

/**
 * A type for names of modules and other hierarchical scopes.
 * See <http://www.cs.utexas.edu/users/moore/acl2/manuals/current/manual/?topic=SV____MODNAME>.
 */
public abstract class ModName implements ACL2Backed
{
    public static final ACL2Object KEYWORD_VL_CORETYPE = ACL2Object.valueOf("KEYWORD", "VL-CORETYPE");
    public static final ACL2Object KEYWORD_GATE = ACL2Object.valueOf("KEWWORD", "GATE");

    private static final Map<ACL2Object, ModName> INTERN = new HashMap<>();

    public boolean isString()
    {
        return false;
    }

    public boolean isCoretype()
    {
        return false;
    }

    public boolean isGate()
    {
        return false;
    }

    public static ModName valueOf(String name)
    {
        return fromACL2(ACL2Object.valueOf(name));
    }

    public static ModName fromACL2(ACL2Object o)
    {
        synchronized (INTERN)
        {
            ModName mn = INTERN.get(o);
            if (mn == null)
            {
                if (stringp(o).bool())
                {
                    mn = new StringImpl(o.stringValueExact());
                } else
                {
                    mn = new MiscImpl(o);
                }
                INTERN.put(o, mn);
            }
            return mn;
        }
    }

    public abstract String toLispString();

    private static class StringImpl extends ModName
    {
        private final String s;

        StringImpl(String s)
        {
            this.s = s;
        }

        @Override
        public ACL2Object getACL2Object()
        {
            return honscopy(ACL2Object.valueOf(s));
        }

        @Override
        public boolean isString()
        {
            return true;
        }

        @Override
        public int hashCode()
        {
            return ACL2Object.hashCodeOf(s);
        }

        @Override
        public boolean equals(Object o)
        {
            return o instanceof StringImpl && s.equals(((StringImpl)o).s);
        }

        @Override
        public String toString()
        {
            return s;
        }

        @Override
        public String toLispString()
        {
            return "\"" + s + "\"";
        }
    }

    private static class MiscImpl extends ModName
    {
        private ACL2Object impl;

        MiscImpl(ACL2Object impl)
        {
            if (stringp(impl).bool() || impl.equals(NIL))
            {
                throw new IllegalArgumentException();
            }
            this.impl = impl;
        }

        @Override
        public ACL2Object getACL2Object()
        {
            return honscopy(impl);
        }

        @Override
        public boolean isCoretype()
        {
            return car(impl).equals(ACL2Object.valueOf("KEYWORD", "VL-CORETYPE"));
        }

        @Override
        public boolean isGate()
        {
            return car(impl).equals(ACL2Object.valueOf("KEYWORD", "GATE"));
        }

        @Override
        public int hashCode()
        {
            return impl.hashCode();
        }

        @Override
        public boolean equals(Object o)
        {
            return o instanceof MiscImpl && impl.equals(((MiscImpl)o).impl);
        }

        @Override
        public String toString()
        {
            return "'" + impl.rep();
        }

        @Override
        public String toLispString()
        {
            return "'" + impl.rep();
        }
    }

}
