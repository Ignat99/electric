/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Vec2.java
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
package com.sun.electric.tool.simulation.acl2.svex;

import static com.sun.electric.util.acl2.ACL2.*;
import com.sun.electric.util.acl2.ACL2Object;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

/**
 * A 2vec is a 4vec that has no X or Z bits..
 * See <http://www.cs.utexas.edu/users/moore/acl2/manuals/current/manual/?topic=SV____2VEC>.
 */
public class Vec2 extends Vec4
{
    private static final Map<BigInteger, Vec2> INTERN = new HashMap<>();

    public static final Vec2 ZERO = valueOf(0);
    public static final Vec2 ONE = valueOf(1);
    public static final Vec2 MINUS_ONE = valueOf(-1);

    private final BigInteger val;

    private Vec2(BigInteger val)
    {
        if (val == null)
        {
            throw new NullPointerException();
        }
        this.val = val;
    }

    public static Vec2 valueOf(BigInteger val)
    {
        synchronized (INTERN)
        {
            Vec2 result = INTERN.get(val);
            if (result == null)
            {
                result = new Vec2(val);
                INTERN.put(val, result);
            }
            return result;
        }
    }

    public static Vec2 valueOf(long val)
    {
        return valueOf(BigInteger.valueOf(val));
    }

    public static Vec2 valueOf(int val)
    {
        return valueOf(BigInteger.valueOf(val));
    }

    public static Vec2 valueOf(boolean b)
    {
        return b ? MINUS_ONE : ZERO;
    }

    public BigInteger getVal()
    {
        return val;
    }

    @Override
    public boolean isVec2()
    {
        return true;
    }

    @Override
    public boolean isIndex()
    {
        return val.signum() >= 0;
    }

    @Override
    public boolean isVec3()
    {
        return true;
    }

    @Override
    public BigInteger getUpper()
    {
        return val;
    }

    @Override
    public BigInteger getLower()
    {
        return val;
    }

    @Override
    public Vec2 fix3()
    {
        return this;
    }

    @Override
    public boolean equals(Object o)
    {
        return o instanceof Vec2 && val.equals(((Vec2)o).val);
    }

    @Override
    public int hashCode()
    {
        return val.hashCode();
    }

    @Override
    public ACL2Object getACL2Object()
    {
        ACL2Object result = honscopy(ACL2Object.valueOf(val));
        assert result.hashCode() == hashCode();
        return result;
    }

    @Override
    public String toString()
    {
        return val.toString();
    }
}
