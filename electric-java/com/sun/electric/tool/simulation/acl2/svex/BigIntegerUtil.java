/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: BigIntegerUtil.java
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

import java.math.BigInteger;

/**
 * BigInteger utilities
 */
public class BigIntegerUtil
{
    public static final BigInteger MINUS_ONE = BigInteger.valueOf(-1);

    public static BigInteger logheadMask(int n)
    {
        if (n <= 0)
        {
            return BigInteger.ZERO;
        }
        return BigInteger.ONE.shiftLeft(n).subtract(BigInteger.ONE);
    }
    
    public static BigInteger loghead(int n, BigInteger x)
    {
        return x.and(logheadMask(n));
    }

    public static BigInteger logtail(int n, BigInteger x)
    {
        if (n <= 0)
        {
            return x;
        }
        return x.shiftRight(n);
    }
    
    public static BigInteger logapp(int size, BigInteger i, BigInteger j)
    {
        if (size <= 0)
        {
            return j;
        }
        return loghead(size, i).or(j.shiftLeft(size));
    }
}
