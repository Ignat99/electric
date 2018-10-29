/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: ACL2Integer.java
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

import java.math.BigInteger;
import java.util.Map;

/**
 * ACL2 integer number.
 */
class ACL2Integer extends ACL2Object
{

    final BigInteger v;

    ACL2Integer(BigInteger v)
    {
        this(null, v);
    }

    private ACL2Integer(HonsManager hm, BigInteger v)
    {
        super(hashCodeOf(v), hm);
        this.v = v;
    }

    static ACL2Integer intern(BigInteger v, HonsManager hm)
    {
        Map<BigInteger, ACL2Integer> allNormed = hm.integers;
        ACL2Integer result = allNormed.get(v);
        if (result == null)
        {
            result = new ACL2Integer(hm, v);
            allNormed.put(v, result);
        }
        return result;
    }

    @Override
    public int intValueExact()
    {
        return v.intValueExact();
    }

    @Override
    public long longValueExact()
    {
        return v.longValueExact();
    }

    @Override
    public BigInteger bigIntegerValueExact()
    {
        return v;
    }

    @Override
    boolean isACL2Number()
    {
        return true;
    }

    @Override
    Rational ratfix()
    {
        return Rational.valueOf(v, BigInteger.ONE);
    }

    @Override
    ACL2Object unaryMinus()
    {
        return new ACL2Integer(v.negate());
    }

    @Override
    ACL2Object unarySlash()
    {
        int sig = v.signum();
        if (sig > 0)
        {
            return v.bitLength() <= 1 ? this : new ACL2Rational(Rational.valueOf(BigInteger.ONE, v));
        }
        if (sig < 0)
        {
            BigInteger a = v.negate();
            return a.bitLength() <= 1 ? this : new ACL2Rational(Rational.valueOf(BigInteger.valueOf(-1), a));
        }
        return this;
    }

    @Override
    ACL2Object binaryPlus(ACL2Object y)
    {
        return v.signum() == 0 ? y.fix() : y.binaryPlus(this);
    }

    @Override
    ACL2Object binaryPlus(ACL2Integer y)
    {
        return v.signum() == 0 ? y : new ACL2Integer(v.add(y.v));
    }

    @Override
    ACL2Object binaryPlus(ACL2Rational y)
    {
        return v.signum() == 0 ? y : y.binaryPlus(this);
    }

    @Override
    ACL2Object binaryPlus(ACL2Complex y)
    {
        return v.signum() == 0 ? y : y.binaryPlus(this);
    }

    @Override
    ACL2Object binaryStar(ACL2Object y)
    {
        return v.signum() == 0 ? this : y.binaryStar(this);
    }

    @Override
    ACL2Object binaryStar(ACL2Integer y)
    {
        return v.signum() == 0 ? this : new ACL2Integer(v.multiply(y.v));
    }

    @Override
    ACL2Object binaryStar(ACL2Rational y)
    {
        return v.signum() == 0 ? this : y.binaryStar(this);
    }

    @Override
    ACL2Object binaryStar(ACL2Complex y)
    {
        return v.signum() == 0 ? this : y.binaryStar(this);
    }

    @Override
    int signum()
    {
        return v.signum();
    }

    @Override
    int compareTo(ACL2Object y)
    {
        return v.signum() == 0 ? -y.signum() : -y.compareTo(this);
    }

    @Override
    int compareTo(ACL2Integer y)
    {
        return v.signum() == 0 ? -y.signum() : v.compareTo(y.v);
    }

    @Override
    int compareTo(ACL2Rational y)
    {
        return v.signum() == 0 ? -y.signum() : -y.compareTo(this);
    }

    @Override
    int compareTo(ACL2Complex y)
    {
        return v.signum() == 0 ? -y.signum() : -y.compareTo(this);
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
        if (o instanceof ACL2Integer)
        {
            ACL2Integer that = (ACL2Integer)o;
            if (this.hashCode == that.hashCode && (this.honsOwner == null || this.honsOwner != that.honsOwner))
            {
                return this.v.equals(that.v);
            }
        }
        return false;
    }
}
