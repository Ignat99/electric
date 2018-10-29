/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: ACL2Rational.java
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
 * ACL2 rational number.
 * Its value is a rational number but not an integer number.
 */
class ACL2Rational extends ACL2Object
{
    final Rational v;

    ACL2Rational(Rational v)
    {
        this(null, v);
    }

    private ACL2Rational(HonsManager hm, Rational v)
    {
        super(v.hashCode(), hm);
        if (v.isInteger())
        {
            throw new IllegalArgumentException();
        }
        this.v = v;
    }

    static ACL2Rational intern(Rational v, HonsManager hm)
    {
        Map<Rational, ACL2Rational> allNormed = hm.rationals;
        ACL2Rational result = allNormed.get(v);
        if (result == null)
        {
            result = new ACL2Rational(hm, v);
            allNormed.put(v, result);
        }
        return result;
    }

    @Override
    boolean isACL2Number()
    {
        return true;
    }

    @Override
    Rational ratfix()
    {
        return v;
    }

    @Override
    ACL2Object unaryMinus()
    {
        return valueOf(v.negate());
    }

    @Override
    ACL2Object unarySlash()
    {
        return valueOf(v.inverse());
    }

    @Override
    ACL2Object binaryPlus(ACL2Object y)
    {
        return y.binaryPlus(this);
    }

    @Override
    ACL2Object binaryPlus(ACL2Integer y)
    {
        return new ACL2Rational(v.add(y.v));
    }

    @Override
    ACL2Object binaryPlus(ACL2Rational y)
    {
        return valueOf(v.add(y.v));
    }

    @Override
    ACL2Object binaryPlus(ACL2Complex y)
    {
        return y.binaryPlus(this);
    }

    @Override
    ACL2Object binaryStar(ACL2Object y)
    {
        return y.binaryStar(this);
    }

    @Override
    ACL2Object binaryStar(ACL2Integer y)
    {
        return valueOf(v.mul(y.v));
    }

    @Override
    ACL2Object binaryStar(ACL2Rational y)
    {
        return valueOf(v.mul(y.v));
    }

    @Override
    ACL2Object binaryStar(ACL2Complex y)
    {
        return y.binaryStar(this);
    }

    @Override
    int signum()
    {
        return v.signum();
    }

    @Override
    int compareTo(ACL2Object y)
    {
        return -y.compareTo(this);
    }

    @Override
    int compareTo(ACL2Integer y)
    {
        return v.compareTo(y.v);
    }

    @Override
    int compareTo(ACL2Rational y)
    {
        return v.compareTo(y.v);
    }

    @Override
    int compareTo(ACL2Complex y)
    {
        return -y.compareTo(this);
    }

    @Override
    public String rep()
    {
        return v.toString();
    }

    @Override
    ACL2Object internImpl(HonsManager hm)
    {
        return intern(v, hm);
    }

    @Override
    public boolean equals(Object o)
    {
        if (o == this)
        {
            return true;
        }
        if (o instanceof ACL2Rational)
        {
            ACL2Rational that = (ACL2Rational)o;
            if (this.hashCode == that.hashCode && (this.honsOwner == null || this.honsOwner != that.honsOwner))
            {
                return this.v.equals(that.v);
            }
        }
        return false;
    }
}
