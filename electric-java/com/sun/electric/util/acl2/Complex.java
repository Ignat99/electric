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

import java.math.BigInteger;

/**
 * Package-private Rational numbers
 */
class Complex
{
    final Rational re;
    final Rational im;

    Complex(Rational re, Rational im)
    {
        this.re = re;
        this.im = im;
    }

    int signum()
    {
        int rsig = re.signum();
        return rsig != 0 ? rsig : im.signum();
    }

    boolean isRational()
    {
        return im.signum() == 0;
    }

    Complex negate()
    {
        return new Complex(re.negate(), im.negate());
    }

    Complex inverse()
    {
        Rational sqrInv = re.mul(re).add(im.mul(im)).inverse();
        return new Complex(re.mul(sqrInv), im.negate().mul(sqrInv));
    }

    Complex add(Complex y)
    {
        return new Complex(re.add(y.re), im.add(y.im));
    }

    Complex add(Rational y)
    {
        return new Complex(re.add(y), im);
    }

    Complex add(BigInteger y)
    {
        return new Complex(re.add(y), im);
    }

    Complex mul(Complex y)
    {
        Rational zre = re.mul(y.re).add(im.mul(y.im).negate());
        Rational zim = re.mul(y.im).add(im.mul(y.re));
        return new Complex(zre, zim);
    }

    Complex mul(Rational y)
    {
        Rational zre = re.mul(y);
        Rational zim = im.mul(y);
        return new Complex(zre, zim);
    }

    Complex mul(BigInteger y)
    {
        Rational zre = re.mul(y);
        Rational zim = im.mul(y);
        return new Complex(zre, zim);
    }

    @Override
    public boolean equals(Object o)
    {
        return o instanceof Complex
            && re.equals(((Complex)o).re)
            && im.equals(((Complex)o).im);
    }

    @Override
    public int hashCode()
    {
        int hash = 3;
        hash = 59 * hash + re.hashCode();
        hash = 59 * hash + im.hashCode();
        return hash;
    }

    @Override
    public String toString()
    {
        return "#c(" + re + "," + im + ")";
    }

    int compareTo(Complex y)
    {
        int resig = re.compareTo(y.re);
        return resig != 0 ? resig : im.compareTo(y.im);
    }

    int compareTo(Rational y)
    {
        int resig = re.compareTo(y);
        return resig != 0 ? resig : im.signum();
    }

    int compareTo(BigInteger y)
    {
        int resig = re.compareTo(y);
        return resig != 0 ? resig : im.signum();
    }
}
