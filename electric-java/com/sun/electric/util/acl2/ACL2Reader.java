/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: ACL2Reader.java
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

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reader of ACL2 serialized format.
 */
public class ACL2Reader
{
    public final ACL2Object root;
    public final int nNat;
    public final int nInt;
    public final int nRat;
    public final int nComplex;
    public final int nChar;
    public final int nStr;
    public final int nNormStr;
    public final int nPkg;
    public final int nSym;
    public final int nCons;
    public final int nNormCons;

    private static final int MAGIC_V1 = 0xAC120BC7;
    private static final int MAGIC_V2 = 0xAC120BC8;
    static final int MAGIC_V3 = 0xAC120BC9;

    private final int magic;
    private final List<ACL2Object> allObjs = new ArrayList<>();

    private static void check(boolean p)
    {
        if (!p)
        {
            throw new RuntimeException();
        }
    }

    private static BigInteger readInt(DataInputStream in) throws IOException
    {
        BigInteger result = BigInteger.ZERO;
        for (int n = 0;; n++)
        {
            byte b = in.readByte();
            result = result.or(BigInteger.valueOf(b & 0x7F).shiftLeft(n * 7));
            if (b >= 0)
            {
                break;
            }
        }
        return result;
    }

    private String readStr(DataInputStream in) throws IOException
    {
        int len = readInt(in).intValueExact();
        boolean normd = false;
        if (magic >= MAGIC_V3)
        {
            normd = (len & 1) != 0;
            len >>>= 1;
        }
        return readString(in, len);
    }

    private static String readString(DataInputStream in, int len) throws IOException
    {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++)
        {
            sb.append((char)(in.readByte() & 0xFF));
        }
        return sb.toString();
    }

    public ACL2Reader(File f) throws IOException
    {
        HonsManager hm = HonsManager.current.get();
        try (DataInputStream in = new DataInputStream(new FileInputStream(f)))
        {
            magic = in.readInt();
            check(magic >= MAGIC_V1 && magic <= MAGIC_V3);
            if (magic >= MAGIC_V2)
            {
                allObjs.add(ACL2Symbol.NIL);
                allObjs.add(ACL2Symbol.T);
            }
            int len = readInt(in).intValueExact();
            nNat = readInt(in).intValueExact();
            for (int i = 0; i < nNat; i++)
            {
                BigInteger n = readInt(in);
                allObjs.add(ACL2Integer.intern(n, hm));
            }
            int ratsLen = readInt(in).intValueExact();
            int nNegInt = 0;
            for (int i = 0; i < ratsLen; i++)
            {
                BigInteger sign = readInt(in);
                check(sign.equals(BigInteger.ZERO) || sign.equals(BigInteger.ONE));
                BigInteger num = readInt(in);
                BigInteger denom = readInt(in);
                if (sign.signum() != 0)
                {
                    num = num.negate();
                }
                if (denom.equals(BigInteger.ONE))
                {
                    allObjs.add(ACL2Integer.intern(num, hm));
                    nNegInt++;
                } else
                {
                    allObjs.add(ACL2Rational.intern(Rational.valueOf(num, denom), hm));
                }
            }
            nInt = nNat + nNegInt;
            nRat = ratsLen - nNegInt;
            nComplex = readInt(in).intValueExact();
            for (int i = 0; i < nComplex; i++)
            {
                BigInteger signR = readInt(in);
                check(signR.equals(BigInteger.ZERO) || signR.equals(BigInteger.ONE));
                BigInteger numR = readInt(in);
                BigInteger denomR = readInt(in);
                if (signR.signum() != 0)
                {
                    numR = numR.negate();
                }
                Rational re = Rational.valueOf(numR, denomR);
                BigInteger signI = readInt(in);
                check(signI.equals(BigInteger.ZERO) || signI.equals(BigInteger.ONE));
                BigInteger numI = readInt(in);
                BigInteger denomI = readInt(in);
                if (signI.signum() != 0)
                {
                    numI = numI.negate();
                }
                Rational im = Rational.valueOf(numI, denomI);
                allObjs.add(ACL2Complex.intern(new Complex(re, im), hm));
            }
            nChar = readInt(in).intValueExact();
            for (int i = 0; i < nChar; i++)
            {
                char c = (char)(in.readByte() & 0xFF);
                allObjs.add(ACL2Character.intern(c));
            }
            nStr = readInt(in).intValueExact();
            int nNormStrings = 0;
            for (long i = 0; i < nStr; i++)
            {
                int strlen = readInt(in).intValueExact();
                boolean normed = false;
                if (magic >= MAGIC_V3)
                {
                    normed = (strlen & 1) != 0;
                    strlen >>>= 1;
                }
                String s = readString(in, strlen);
                if (normed)
                {
                    nNormStrings++;
                } else
                {
//                    System.out.println("String " + s + " is not normed");
                }
                allObjs.add(normed ? ACL2String.intern(s, hm) : new ACL2String(s));
            }
            nNormStr = nNormStrings;
            nPkg = readInt(in).intValueExact();
            int numSymsTotal = 0;
            for (int i = 0; i < nPkg; i++)
            {
                String pkgName = readStr(in);
                long numSyms = readInt(in).longValueExact();
                for (int j = 0; j < numSyms; j++)
                {
                    String name = readStr(in);
                    allObjs.add(ACL2Object.valueOf(pkgName, name));
                }
                numSymsTotal += numSyms;
            }
            nSym = numSymsTotal;
            nCons = readInt(in).intValueExact();
            int nNormConses = 0;
            for (int i = 0; i < nCons; i++)
            {
                int car = readInt(in).intValueExact();
                int cdr = readInt(in).intValueExact();
                boolean normed = false;
                if (magic >= MAGIC_V2)
                {
                    normed = (car & 1) != 0;
                    car >>>= 1;
                }
                ACL2Object carObj = allObjs.get(car);
                ACL2Object cdrObj = allObjs.get(cdr);
                if (normed)
                {
                    nNormConses++;
                }
                allObjs.add(normed ? ACL2Cons.intern(carObj, cdrObj, hm) : new ACL2Cons(carObj, cdrObj));
            }
            nNormCons = nNormConses;
            if (magic >= MAGIC_V3)
            {
//            System.out.println("FALS");
                for (;;)
                {
                    int fal0 = readInt(in).intValueExact();
                    if (fal0 == 0)
                    {
                        break;
                    }
                    int fal1 = readInt(in).intValueExact();
//                System.out.println(" " + fal0 + " " + fal1);
                }
            }
            int magicEnd = in.readInt();
            check(magicEnd == magic);
            root = allObjs.get(magic >= MAGIC_V2 ? len : len - 1);
        }
    }

    public String getStats()
    {
        return ((nInt + nRat + nComplex + nChar + nStr + nSym) + " atoms and "
            + nCons + " conses. TreeCount=" + treeCount(root, new HashMap<>()));
    }

    private static BigInteger treeCount(ACL2Object top, Map<ACL2Cons, BigInteger> memoize)
    {
        if (top instanceof ACL2Cons)
        {
            ACL2Cons cons = (ACL2Cons)top;
            BigInteger count = memoize.get(cons);
            if (count == null)
            {
                count = BigInteger.ONE
                    .add(treeCount(cons.car, memoize))
                    .add(treeCount(cons.cdr, memoize));
                memoize.put(cons, count);
            }
            return count;
        } else
        {
            return BigInteger.ONE;
        }
    }

}
