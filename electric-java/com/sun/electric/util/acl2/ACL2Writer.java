/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: ACL2Writer.java
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

import static com.sun.electric.util.acl2.ACL2.*;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Writer of ACL2 serialized format.
 */
public class ACL2Writer
{
    private final ACL2Object top;

    private final Map<ACL2Symbol, Integer> seenSym = new HashMap<>();
    private final Map<ACL2Object, Integer> seenEql = new HashMap<>();
    private final Map<ACL2String, Integer> seenStr = new IdentityHashMap<>();
    private final Map<ACL2Cons, Integer> seenCons = new IdentityHashMap<>();

    private final List<ACL2Integer> naturals = new ArrayList<>();
    private final List<ACL2Object> rationals = new ArrayList<>();
    private final List<ACL2Complex> complexes = new ArrayList<>();
    private final List<ACL2Character> chars = new ArrayList<>();
    private final List<ACL2String> strings = new ArrayList<>();

    private final Map<String, List<ACL2Symbol>> symbols = new LinkedHashMap<>();

    private int freeIndex;

    private DataOutputStream out;

    private ACL2Writer(ACL2Object top)
    {
        this.top = top;
        gatherAtoms(top);
        makeAtomMap();
    }

    private void encode(DataOutputStream out) throws IOException
    {
        this.out = out;
        encodeMagic();
        encodeNat(top.equals(ACL2Symbol.NIL) ? 0 : Math.addExact(freeIndex, seenCons.size()) - 1);
        encodeAtoms();
        encodeNat(seenCons.size());
        encodeConses(top);
        encodeFals();
        encodeMagic();

    }

    public static void write(ACL2Object top, File outName) throws IOException
    {
        ACL2Writer writer = new ACL2Writer(top);
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(outName))))
        {
            writer.encode(out);
        }
    }

    private void gatherAtoms(ACL2Object top)
    {
        while (top instanceof ACL2Cons)
        {
            ACL2Cons cons = (ACL2Cons)top;
            if (seenCons.containsKey(cons))
            {
                return;
            }
            seenCons.put(cons, null);
            gatherAtoms(cons.car);
            top = cons.cdr;
        }
        if (top instanceof ACL2Symbol)
        {
            ACL2Symbol sym = (ACL2Symbol)top;
            if (!sym.equals(ACL2Symbol.NIL)
                && !sym.equals(ACL2Symbol.T)
                && !seenSym.containsKey(sym))
            {
                seenSym.put(sym, null);
                List<ACL2Symbol> pkgSymbols = symbols.get(sym.pkg.name);
                if (pkgSymbols == null)
                {
                    pkgSymbols = new ArrayList<>();
                    symbols.put(sym.pkg.name, pkgSymbols);
                }
                pkgSymbols.add(sym);
            }
        } else if (top instanceof ACL2String)
        {
            ACL2String str = (ACL2String)top;
            if (!seenStr.containsKey(str))
            {
                seenStr.put(str, null);
                strings.add(str);
            }
        } else
        {
            if (!seenEql.containsKey(top))
            {
                seenEql.put(top, null);
                if (top instanceof ACL2Character)
                {
                    chars.add((ACL2Character)top);
                } else if (top instanceof ACL2Integer)
                {
                    ACL2Integer integer = (ACL2Integer)top;
                    if (integer.v.signum() >= 0)
                    {
                        naturals.add(integer);
                    } else
                    {
                        rationals.add(integer);
                    }
                } else if (top instanceof ACL2Rational)
                {
                    rationals.add((ACL2Rational)top);
                } else if (top instanceof ACL2Complex)
                {
                    complexes.add((ACL2Complex)top);
                } else
                {
                    throw new AssertionError();
                }
            }
        }
    }

    private void makeAtomMap()
    {
        freeIndex = 2;
        for (ACL2Integer x : naturals)
        {
            seenEql.put(x, freeIndex++);
        }
        for (ACL2Object x : rationals)
        {
            seenEql.put(x, freeIndex++);
        }
        for (ACL2Complex x : complexes)
        {
            seenEql.put(x, freeIndex++);
        }
        for (ACL2Character x : chars)
        {
            seenEql.put(x, freeIndex++);
        }
        for (ACL2String x : strings)
        {
            seenStr.put(x, freeIndex++);
        }
        for (List<ACL2Symbol> pkgSyms : symbols.values())
        {
            for (ACL2Symbol sym : pkgSyms)
            {
                seenSym.put(sym, freeIndex++);
            }
        }
        seenSym.put(ACL2Symbol.NIL, 0);
        seenSym.put(ACL2Symbol.T, 1);
    }

    private void encodeMagic() throws IOException
    {
        out.writeInt(ACL2Reader.MAGIC_V3);
    }

    private void encodeNat(BigInteger n) throws IOException
    {
        if (n.signum() < 0)
        {
            throw new IllegalArgumentException();
        }
        while (n.bitLength() > 7)
        {
            out.writeByte(n.byteValue() | 0x80);
            n = n.shiftRight(7);
        }
        out.writeByte(n.byteValueExact());
    }

    private void encodeNat(int n) throws IOException
    {
        encodeNat(BigInteger.valueOf(n));
    }

    private void encodeRat(BigInteger numerator, BigInteger denominator) throws IOException
    {
        encodeNat(numerator.signum() < 0 ? 1 : 0);
        encodeNat(numerator.abs());
        encodeNat(denominator);
    }

    private void encodeRat(Rational x) throws IOException
    {
        encodeRat(x.n, x.d);
    }

    private void encodeNats() throws IOException
    {
        encodeNat(naturals.size());
        for (ACL2Integer x : naturals)
        {
            encodeNat(x.v);
        }
    }

    private void encodeRats() throws IOException
    {
        encodeNat(rationals.size());
        for (ACL2Object x : rationals)
        {
            BigInteger numerator = numerator(x).bigIntegerValueExact();
            BigInteger denominator = denominator(x).bigIntegerValueExact();
            encodeRat(numerator, denominator);
        }
    }

    private void encodeComplexes() throws IOException
    {
        encodeNat(complexes.size());
        for (ACL2Complex x : complexes)
        {
            encodeRat(x.v.re);
            encodeRat(x.v.im);
        }
    }

    private void encodeChars() throws IOException
    {
        encodeNat(chars.size());
        for (ACL2Character x : chars)
        {
            out.writeByte(x.c);
        }
    }

    private void encodeStr(boolean normed, String s) throws IOException
    {
        int len = s.length();
        int header = (len << 1) | (normed ? 1 : 0);
        encodeNat(header);
        for (int i = 0; i < len; i++)
        {
            out.writeByte(s.charAt(i));
        }

    }

    private void encodeStr(ACL2String x) throws IOException
    {
        encodeStr(x.isNormed(), x.s);
    }

    private void encodeStrs() throws IOException
    {
        encodeNat(strings.size());
        for (ACL2String x : strings)
        {
            encodeStr(x);
        }
    }

    private void encodePackages() throws IOException
    {
        encodeNat(symbols.size());
        for (Map.Entry<String, List<ACL2Symbol>> e : symbols.entrySet())
        {
            encodeStr(true, e.getKey());
            List<ACL2Symbol> syms = e.getValue();
            encodeNat(syms.size());
            for (ACL2Symbol sym : syms)
            {
                encodeStr(true, sym.nm);
            }
        }
    }

    private void encodeAtoms() throws IOException
    {
        encodeNats();
        encodeRats();
        encodeComplexes();
        encodeChars();
        encodeStrs();
        encodePackages();
    }

    private int encodeConses(ACL2Object x) throws IOException
    {
        List<ACL2Cons> consStack = new ArrayList<>();
        Integer idx = null;
        while (x instanceof ACL2Cons)
        {
            ACL2Cons cons = (ACL2Cons)x;
            idx = seenCons.get(cons);
            if (idx != null)
            {
                break;
            }
            consStack.add(cons);
            x = cons.cdr;
        }
        int endIdx;
        if (x instanceof ACL2Cons)
        {
            endIdx = idx;
        } else if (x instanceof ACL2Symbol)
        {
            endIdx = seenSym.get((ACL2Symbol)x);
        } else if (x instanceof ACL2String)
        {
            endIdx = seenStr.get((ACL2String)x);
        } else
        {
            endIdx = seenEql.get(x);
        }
        while (!consStack.isEmpty())
        {
            ACL2Cons cons = consStack.remove(consStack.size() - 1);
            int carIndex = encodeConses(cons.car);
            int cdrIndex = endIdx;
            boolean normed = cons.isNormed();
            int v2CarIndex = (carIndex << 1) | (normed ? 1 : 0);
            endIdx = freeIndex++;
            seenCons.put(cons, endIdx);
            encodeNat(v2CarIndex);
            encodeNat(cdrIndex);
        }
        return endIdx;
    }

    private int encodeConsesOld(ACL2Object x) throws IOException
    {
        if (x instanceof ACL2Cons)
        {
            ACL2Cons cons = (ACL2Cons)x;
            Integer idx = seenCons.get(cons);
            if (idx == null)
            {
                int carIndex = encodeConses(cons.car);
                int cdrIndex = encodeConses(cons.cdr);
                boolean normed = cons.isNormed();
                int v2CarIndex = (carIndex << 1) | (normed ? 1 : 0);
                idx = freeIndex++;
                seenCons.put(cons, idx);
                encodeNat(v2CarIndex);
                encodeNat(cdrIndex);
            }
            return idx;
        } else if (x instanceof ACL2Symbol)
        {
            return seenSym.get((ACL2Symbol)x);
        } else if (x instanceof ACL2String)
        {
            return seenStr.get((ACL2String)x);
        } else
        {
            return seenEql.get(x);
        }
    }

    private void encodeFals() throws IOException
    {
        encodeNat(0);
    }
}
