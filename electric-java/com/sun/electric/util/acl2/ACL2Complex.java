/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Complex.java
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
 * ACL2 complex-rational number.
 * Its value is a complex number with rational real part and rational nonzero imaginary part.
 */
class ACL2Complex extends ACL2Object
{
    final Complex v;

    ACL2Complex(Complex v)
    {
        this(null, v);
    }

    private ACL2Complex(HonsManager hm, Complex v)
    {
        super(v.hashCode(), hm);
        if (v.isRational())
        {
            throw new IllegalArgumentException();
        }
        this.v = v;
    }

    static ACL2Complex intern(Complex v, HonsManager hm)
    {
        Map<Complex, ACL2Complex> allNormed = hm.complexes;
        ACL2Complex result = allNormed.get(v);
        if (result == null)
        {
            result = new ACL2Complex(hm, v);
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
    ACL2Object unaryMinus()
    {
        return new ACL2Complex(v.negate());
    }

    @Override
    ACL2Object unarySlash()
    {
        return new ACL2Complex(v.inverse());
    }

    @Override
    ACL2Object binaryPlus(ACL2Object y)
    {
        return y.binaryPlus(this);
    }

    @Override
    ACL2Object binaryPlus(ACL2Integer y)
    {
        return new ACL2Complex(v.add(y.v));
    }

    @Override
    ACL2Object binaryPlus(ACL2Rational y)
    {
        return new ACL2Complex(v.add(y.v));
    }

    @Override
    ACL2Object binaryPlus(ACL2Complex y)
    {
        return valueOf(v.add(y.v));
    }

    @Override
    ACL2Object binaryStar(ACL2Object y)
    {
        return y.binaryStar(this);
    }

    @Override
    ACL2Object binaryStar(ACL2Integer y)
    {
        return new ACL2Complex(v.mul(y.v));
    }

    @Override
    ACL2Object binaryStar(ACL2Rational y)
    {
        return new ACL2Complex(v.mul(y.v));
    }

    @Override
    ACL2Object binaryStar(ACL2Complex y)
    {
        return valueOf(v.mul(y.v));
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
        return v.compareTo(y.v);
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
        if (o instanceof ACL2Complex)
        {
            ACL2Complex that = (ACL2Complex)o;
            if (this.hashCode == that.hashCode && (this.honsOwner == null || this.honsOwner != that.honsOwner))
            {
                return this.v.equals(that.v);
            }
        }
        return false;
    }
}
