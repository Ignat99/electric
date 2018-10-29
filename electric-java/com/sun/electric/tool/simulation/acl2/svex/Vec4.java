/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Vec4.java
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
import com.sun.electric.util.acl2.ACL2Backed;
import com.sun.electric.util.acl2.ACL2Object;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

/**
 * The fundamental 4-valued vector representation used throughout SV expressions.
 * See <http://www.cs.utexas.edu/users/moore/acl2/manuals/current/manual/?topic=SV____4VEC>.
 */
public abstract class Vec4 implements ACL2Backed
{
    private static final Map<Impl, Impl> INTERN = new HashMap<>();

    public static final Vec4 X = valueOf(BigInteger.valueOf(-1), BigInteger.valueOf(0));
    public static final Vec4 Z = valueOf(BigInteger.valueOf(0), BigInteger.valueOf(-1));
    public static final Vec4 X1 = valueOf(BigInteger.valueOf(1), BigInteger.valueOf(0));
    public static final Vec4 Z1 = valueOf(BigInteger.valueOf(0), BigInteger.valueOf(1));

    public abstract boolean isVec2();

    public abstract boolean isIndex();

    public abstract boolean isVec3();

    public abstract BigInteger getUpper();

    public abstract BigInteger getLower();

    public static final int BIT_LIMIT = 1 << 24;

    public static Vec4 valueOf(BigInteger upper, BigInteger lower)
    {
        if (upper.equals(lower))
        {
            return Vec2.valueOf(upper);
        }
        Impl key = new Impl(upper, lower);
        synchronized (INTERN)
        {
            Impl result = INTERN.get(key);
            if (result == null)
            {
                result = key;
                INTERN.put(key, result);
            }
            return result;
        }
    }

    public static Vec4 fromACL2(ACL2Object impl)
    {
        Vec4 result;
        if (consp(impl).bool())
        {
            BigInteger upper = car(impl).bigIntegerValueExact();
            BigInteger lower = cdr(impl).bigIntegerValueExact();
            return valueOf(upper, lower);
        } else
        {
            result = Vec2.valueOf(impl.bigIntegerValueExact());
        }
        assert result.hashCode() == impl.hashCode();
        return result;
    }

    public abstract Vec4 fix3();

    static class Impl extends Vec4
    {
        private final BigInteger upper;
        private final BigInteger lower;
        private final int hashCode;

        Impl(BigInteger upper, BigInteger lower)
        {
            if (upper.equals(lower))
            {
                throw new IllegalArgumentException();
            }
            this.upper = upper;
            this.lower = lower;
            hashCode = ACL2Object.hashCodeOfCons(upper.hashCode(), lower.hashCode());
        }

        @Override
        public boolean isVec2()
        {
            return false;
        }

        @Override
        public boolean isIndex()
        {
            return false;
        }

        @Override
        public boolean isVec3()
        {
            return lower.andNot(upper).signum() == 0;
        }

        @Override
        public BigInteger getUpper()
        {
            return upper;
        }

        @Override
        public BigInteger getLower()
        {
            return lower;
        }

        @Override
        public Vec4 fix3()
        {
            if (isVec3())
            {
                return this;
            } else
            {
                return valueOf(upper.or(lower), upper.and(lower));
            }
        }

        @Override
        public boolean equals(Object o)
        {
            if (o instanceof Impl)
            {
                Impl that = (Impl)o;
                return this.upper.equals(that.upper) && this.lower.equals(that.lower);
            }
            return false;
        }

        @Override
        public int hashCode()
        {
            return hashCode;
        }

        @Override
        public ACL2Object getACL2Object()
        {
            ACL2Object result = hons(ACL2Object.valueOf(upper), ACL2Object.valueOf(lower));
            assert result.hashCode() == hashCode();
            return result;
        }

        @Override
        public String toString()
        {
            return "vec4[" + upper + "," + lower + "]";
        }
    }
}
