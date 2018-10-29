/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: ACL2String.java
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
package com.sun.electric.util.acl2;

import java.util.Map;

/**
 * ACL2 string.
 * This is an atom. Its value is 8-bit ASCII string.
 */
class ACL2String extends ACL2Object
{

    final String s;

    ACL2String(String s)
    {
        this(null, s);
    }

    private ACL2String(HonsManager hm, String s)
    {
        super(hashCodeOf(s), hm);
        for (int i = 0; i < s.length(); i++)
        {
            if (s.charAt(i) >= 0x100)
            {
                throw new IllegalArgumentException();
            }
        }
        this.s = s;
    }

    static ACL2String intern(String s, HonsManager hm)
    {
        Map<String, ACL2String> allNormed = hm.strings;
        ACL2String result = allNormed.get(s);
        if (result == null)
        {
            result = new ACL2String(hm, s);
            allNormed.put(s, result);
        }
        return result;
    }

    @Override
    public String stringValueExact()
    {
        return s;
    }

    @Override
    public String rep()
    {
        return "\"" + s + "\"";
    }

    @Override
    ACL2Object internImpl(HonsManager hm)
    {
        return intern(s, hm);
    }

    @Override
    public boolean equals(Object o)
    {
        if (o == this)
        {
            return true;
        }
        if (o instanceof ACL2String)
        {
            ACL2String that = (ACL2String)o;
            if (this.hashCode == that.hashCode && (this.honsOwner == null || this.honsOwner != that.honsOwner))
            {
                return this.s.equals(that.s);
            }
        }
        return false;
    }
}
