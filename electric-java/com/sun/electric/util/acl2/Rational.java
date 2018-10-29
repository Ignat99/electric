/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Rational.java
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
class Rational implements Comparable<Rational>
{
    final BigInteger n;
    final BigInteger d;

    Rational(BigInteger n, BigInteger d)
    {
        if (d.signum() <= 0)
        {
            throw new IllegalArgumentException();
        }
        assert n.gcd(d).equals(BigInteger.ONE);
        if (d.bitLength() <= 1)
        {
            d = BigInteger.ONE;
        }
        this.n = n;
        this.d = d;
    }

    static Rational valueOf(BigInteger num, BigInteger den)
    {
        if (den.signum() < 0) {
            num = num.negate();
            den = den.negate();
        }
        BigInteger gcd = num.gcd(den);
        if (gcd.bitLength() > 1) {
            num = num.divide(gcd);
            den = den.divide(gcd);
        }
        return new Rational(num, den);
    }

    int signum()
    {
        return n.signum();
    }

    boolean isInteger()
    {
        return d == BigInteger.ONE;
    }

    Rational negate()
    {
        return n.signum() == 0 ? this : new Rational(n.negate(), d);
    }

    Rational inverse()
    {
        int sig = signum();
        if (sig > 0)
        {
            return new Rational(d, n);
        }
        if (sig < 0)
        {
            return new Rational(d.negate(), n.negate());
        }
        return this;
    }

    Rational add(Rational y)
    {
        if (signum() == 0)
        {
            return y;
        }
        if (y.signum() == 0)
        {
            return this;
        }

        BigInteger gcd = d.gcd(y.d);

        BigInteger xd = gcd.bitLength() <= 1 ? d : d.divide(gcd);
        BigInteger yd = gcd.bitLength() <= 1 ? y.d : y.d.divide(gcd);

        BigInteger num = n.multiply(yd).add((y.n.multiply(xd)));
        BigInteger den = d.multiply(yd);

        gcd = num.gcd(den);
        if (gcd.bitLength() > 1)
        {
            num = num.divide(gcd);
            den = den.divide(gcd);
        }

        return new Rational(num, den);
    }

    Rational add(BigInteger y)
    {
        return new Rational(y.multiply(d).add(n), d);
    }

    Rational mul(BigInteger y)
    {
        if (signum() == 0)
        {
            return this;
        }

        BigInteger gcd = d.gcd(y);

        BigInteger num = (gcd.bitLength() <= 1 ? y : y.divide(gcd)).multiply(n);
        BigInteger den = gcd.bitLength() <= 1 ? d : d.divide(gcd);

        return new Rational(num, den);
    }

    Rational mul(Rational y)
    {
        if (signum() == 0)
        {
            return this;
        }
        if (y.signum() == 0)
        {
            return y;
        }

        BigInteger gcd1 = n.gcd(y.d);
        BigInteger gcd2 = d.gcd(y.n);

        BigInteger nom = (gcd1.bitLength() <= 1 ? n : n.divide(gcd1))
            .multiply(gcd2.bitLength() <= 1 ? y.n : y.n.divide(gcd2));
        BigInteger den = (gcd2.bitLength() <= 1 ? d : d.divide(gcd2))
            .multiply(gcd1.bitLength() <= 1 ? y.d : y.d.divide(gcd1));

        return new Rational(nom, den);
    }

    @Override
    public boolean equals(Object o)
    {
        return o instanceof Rational
            && n.equals(((Rational)o).n)
            && d.equals(((Rational)o).d);
    }

    @Override
    public int hashCode()
    {
        int hash = 7;
        hash = 71 * hash + n.hashCode();
        hash = 71 * hash + d.hashCode();
        return hash;
    }

    @Override
    public String toString()
    {
        return n.toString() + "/" + d.toString();
    }

    @Override
    public int compareTo(Rational y)
    {
        if (signum() != y.signum())
        {
            return signum();
        }
        return n.multiply(y.d).compareTo(d.multiply(y.n));
    }

    public int compareTo(BigInteger y)
    {
        if (signum() != y.signum())
        {
            return signum();
        }
        return n.compareTo(d.multiply(y));
    }
}
