/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: LhsArr.java
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
package com.sun.electric.tool.simulation.acl2.mods;

import com.sun.electric.tool.simulation.acl2.svex.BigIntegerUtil;
import com.sun.electric.tool.simulation.acl2.svex.Svar;
import com.sun.electric.tool.simulation.acl2.svex.Svex;
import com.sun.electric.tool.simulation.acl2.svex.SvexManager;
import static com.sun.electric.util.acl2.ACL2.*;
import com.sun.electric.util.acl2.ACL2Backed;
import com.sun.electric.util.acl2.ACL2Object;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * See <http://www.cs.utexas.edu/users/moore/acl2/manuals/current/manual/?topic=SV____LHSARR>
 */
public class LhsArr
{
    private final List<Lhs<IndexName>> arr;

    LhsArr(int totalWires)
    {
        arr = new ArrayList<>(totalWires);
        while (arr.size() < totalWires)
        {
            arr.add(null);
        }
    }

    public int size()
    {
        return arr.size();
    }

    public Lhs<IndexName> getLhs(int i)
    {
        return arr.get(i);
    }

    public Lhs<IndexName> getAlias(int i)
    {
        return arr.get(i);
    }

    public void setAlias(int i, Lhs<IndexName> x)
    {
        arr.set(i, x);
    }

    public List<Lhs<IndexName>> getArr()
    {
        return arr;
    }

    public List<Svar<IndexName>> aliasesVars()
    {
        List<Svar<IndexName>> vars = new ArrayList<>();
        for (int i = arr.size() - 1; i >= 0; i--)
        {
            getLhs(i).vars(vars);
        }
        return vars;
    }

    public Svex<IndexName>[] toSvexarr(SvexManager<IndexName> sm)
    {
        Svex<IndexName>[] result = Svex.newSvexArray(arr.size());
        for (int i = 0; i < arr.size(); i++)
        {
            result[i] = arr.get(i).toSvex(sm);
        }
        return result;
    }

    public static boolean svarIsBounded(Svar<IndexName> x, int bound)
    {
        return x.getName().getIndex() < bound;
    }

    public static boolean lhatomIsNormordered(int bound, int offset, Lhatom<IndexName> x)
    {
        Svar<IndexName> svar = x.getVar();
        if (svar == null)
        {
            return true;
        }
        int idx = svar.getName().getIndex();
        return idx < bound || idx == bound && x.getRsh() <= offset;
    }

    public static boolean lhsVarsIsNormordered(int bound, int offset, Lhs<IndexName> x)
    {
        for (Lhrange<IndexName> range : x.ranges)
        {
            if (!lhatomIsNormordered(bound, offset, range.getAtom()))
            {
                return false;
            }
            offset += range.getWidth();
        }
        return true;
    }

    public boolean isNormordered()
    {
        for (int n = 0; n < arr.size(); n++)
        {
            Lhs<IndexName> lhs = getAlias(n);
            if (!lhsVarsIsNormordered(n, 0, lhs))
            {
                return false;
            }
        }
        return true;
    }

    private static void lhsCheckMasks(Lhs<IndexName> x,
        Map<Svar<IndexName>, BigInteger> maskAcc, Map<Svar<IndexName>, BigInteger> confAcc)
    {
        Lhs<IndexName> norm = x.norm();
        for (Lhrange<IndexName> range : norm.ranges)
        {
            Svar<IndexName> svar = range.getVar();
            if (svar != null)
            {
                BigInteger firstMask = BigIntegerUtil.logheadMask(range.getWidth()).shiftLeft(range.getRsh());
                BigInteger varMask = maskAcc.get(svar);
                if (varMask == null)
                {
                    varMask = BigInteger.ZERO;
                }
                BigInteger conflict = firstMask.and(varMask);
                maskAcc.put(svar, firstMask.or(varMask));
                if (conflict.signum() != 0)
                {
                    BigInteger oldConflict = confAcc.get(svar);
                    if (oldConflict == null)
                    {
                        oldConflict = BigInteger.ZERO;
                    }
                    confAcc.put(svar, conflict.or(oldConflict));
                }
            }
        }
    }

    public static void assignsCheckMasks(Map<Lhs<IndexName>, Driver<IndexName>> assigns,
        Map<Svar<IndexName>, BigInteger> maskAcc, Map<Svar<IndexName>, BigInteger> confAcc)
    {
        for (Lhs<IndexName> lhs : assigns.keySet())
        {
            lhsCheckMasks(lhs, maskAcc, confAcc);
        }
    }

    public Lhs<IndexName> aliasCanonicalize(Lhs<IndexName> x, int idx, int w, int offset)
    {
        Lhs.Decomp<IndexName> decomp = x.decomp();
        if (decomp.first == null || w == 0)
        {
            return new Lhs<>(Collections.emptyList());
        }
        int blockw = Math.min(w, decomp.first.getWidth());
        Lhs<IndexName> first = aliasCanonicalize(decomp.first, idx, blockw, offset);
        Lhs<IndexName> rest = aliasCanonicalize(decomp.rest, idx, w - blockw, offset + blockw);
        return first.concat(blockw, rest);
    }

    private Lhs<IndexName> aliasCanonicalize(Lhrange<IndexName> x, int idx, int w, int offset)
    {
        Svar<IndexName> svar = x.getVar();
        if (svar == null)
        {
            return new Lhs<>(Collections.emptyList());
        }
        int vidx = svar.getName().getIndex();
        if (vidx == idx && x.getRsh() == offset)
        {
            return new Lhs<>(Collections.singletonList(new Lhrange<>(w, x.getAtom())));
        }
        Lhs<IndexName> rsh = getAlias(vidx).rsh(x.getRsh());
        return aliasCanonicalize(rsh, vidx, w, x.getRsh());
    }

    public Lhs<IndexName> aliasCanonicalizeReplace(Lhs<IndexName> x, int idx, int w, int offset)
    {
        Lhs.Decomp<IndexName> decomp = x.decomp();
        if (decomp.first == null || w == 0)
        {
            return new Lhs<>(Collections.emptyList());
        }
        int blockw = Math.min(w, decomp.first.getWidth());
        Lhs<IndexName> newFirst = aliasCanonicalizeReplace(decomp.first, idx, blockw, offset);
        Lhs<IndexName> newRest = aliasCanonicalize(decomp.rest, idx, w - blockw, offset + blockw);
        return newFirst.concat(blockw, newRest);
    }

    private Lhs<IndexName> aliasCanonicalizeReplace(Lhrange<IndexName> x, int idx, int w, int offset)
    {
        Svar<IndexName> svar = x.getVar();
        if (svar == null)
        {
            return new Lhs<>(Collections.emptyList());
        }
        int vidx = svar.getName().getIndex();
        if (vidx == idx && x.getRsh() == offset)
        {
            return new Lhs<>(Collections.singletonList(new Lhrange<>(w, x.getAtom())));
        }
        Lhs<IndexName> wholeLhs = getAlias(vidx);
        Lhs<IndexName> leftPart = wholeLhs.rsh(x.getRsh());
        Lhs<IndexName> canon = aliasCanonicalizeReplace(leftPart, vidx, w, x.getRsh());
        Lhs<IndexName> leftPart2 = leftPart.rsh(w);
        Lhs<IndexName> wholeCanon = wholeLhs.concat(x.getRsh(), canon.concat(w, leftPart2));
        setAlias(vidx, wholeCanon);
        return canon;
    }

    private Lhs<IndexName> replaceRange(int start, int w, Lhs<IndexName> repl, Lhs<IndexName> x)
    {
        return x.concat(start, repl.concat(w, x.rsh(start + w)));
    }

    private void pairsSetAliases(Lhs<IndexName> x, Lhs<IndexName> y)
    {
        Lhs.Decomp<IndexName> dx = x.decomp();
        Lhs.Decomp<IndexName> dy = y.decomp();
        if (dx.first == null || dy.first == null)
        {
            return;
        }
        Lhrange<IndexName> xf = dx.first;
        Lhrange<IndexName> yf = dy.first;
        int blkw;
        Lhs<IndexName> nextX, nextY;
        if (xf.getWidth() < yf.getWidth())
        {
            blkw = xf.getWidth();
            nextX = dx.rest;
            nextY = y.rsh(xf.getWidth());
        } else if (yf.getWidth() < xf.getWidth())
        {
            blkw = yf.getWidth();
            nextX = x.rsh(yf.getWidth());
            nextY = dy.rest;
        } else
        {
            blkw = xf.getWidth();
            nextX = dx.rest;
            nextY = dy.rest;
        }
        Svar<IndexName> varx = xf.getVar();
        Svar<IndexName> vary = yf.getVar();
        if (varx == null || vary == null || varx.equals(vary))
        {
            pairsSetAliases(dx.rest, dy.rest);
            return;
        }
        int xidx = varx.getName().getIndex();
        int yidx = vary.getName().getIndex();
        int lessIdx, lessOffset, grIdx, grOffset;
        if (xidx < yidx)
        {
            lessIdx = xidx;
            lessOffset = xf.getRsh();
            grIdx = yidx;
            grOffset = yf.getRsh();
        } else if (yidx < xidx)
        {
            lessIdx = yidx;
            lessOffset = yf.getRsh();
            grIdx = xidx;
            grOffset = xf.getRsh();
        } else if (xf.getRsh() < yf.getRsh())
        {
            lessIdx = xidx;
            lessOffset = xf.getRsh();
            grIdx = yidx;
            grOffset = yf.getRsh();
        } else
        {
            lessIdx = yidx;
            lessOffset = yf.getRsh();
            grIdx = xidx;
            grOffset = xf.getRsh();
        }
        Lhs<IndexName> greaterFull = getAlias(grIdx);
        Lhs<IndexName> lessFull = getAlias(lessIdx);
        Lhs<IndexName> lessRange = lessFull.rsh(lessOffset);
        Lhs<IndexName> greaterNew = replaceRange(grOffset, blkw, lessRange, greaterFull);
        setAlias(grIdx, greaterNew);
        pairsSetAliases(nextX, nextY);
    }

    public Lhs<IndexName> aliasCanonicalizeTop(Lhs<IndexName> x)
    {
        Lhs.Decomp<IndexName> decomp = x.decomp();
        if (decomp.first == null)
        {
            return new Lhs<>(Collections.emptyList());
        }
        Lhrange<IndexName> first = decomp.first;
        Svar<IndexName> svar = first.getVar();
        if (svar == null)
        {
            return aliasCanonicalizeTop(decomp.rest);
        }
        int idx = svar.getName().getIndex();
        Lhs<IndexName> low = aliasCanonicalize(
            getAlias(idx).rsh(first.getRsh()), idx, first.getWidth(), first.getRsh());
        Lhs<IndexName> high = aliasCanonicalizeTop(decomp.rest);
        return low.concat(first.getWidth(), high);
    }

    public Lhs<IndexName> aliasCanonicalizeReplaceTop(Lhs<IndexName> x)
    {
        Lhs.Decomp<IndexName> decomp = x.decomp();
        if (decomp.first == null)
        {
            return new Lhs<>(Collections.emptyList());
        }
        Lhrange<IndexName> first = decomp.first;
        Svar<IndexName> svar = first.getVar();
        if (svar == null)
        {
            Lhs<IndexName> restX = aliasCanonicalizeTop(decomp.rest);
            return restX.cons(first);
        }
        int idx = svar.getName().getIndex();
        Lhs<IndexName> alias = getAlias(idx);
        Lhs<IndexName> firstX = aliasCanonicalizeReplace(
            alias.rsh(first.getRsh()),
            idx, first.getWidth(), first.getRsh());
        Lhs<IndexName> restX = aliasCanonicalizeReplaceTop(decomp.rest);
        return firstX.concat(first.getWidth(), restX);
    }

    private void addPair(Lhs<IndexName> x, Lhs<IndexName> y)
    {
        Lhs<IndexName> xCanon = aliasCanonicalizeTop(x);
        Lhs<IndexName> yCanon = aliasCanonicalizeTop(y);
        pairsSetAliases(xCanon, yCanon);
        aliasCanonicalizeReplaceTop(x);
        aliasCanonicalizeReplaceTop(y);
    }

    private void finishCanonicalize()
    {
        for (int n = 0; n < arr.size(); n++)
        {
            Lhs<IndexName> lhs = getAlias(n);
            Lhs<IndexName> canon = aliasCanonicalize(lhs, n, lhs.width(), 0);
            setAlias(n, canon);
        }
    }

    private void empty()
    {
        arr.clear();
    }

    public void putPairs(Collection<Aliaspair<IndexName>> aliaspairs)
    {
        for (Aliaspair<IndexName> aliaspair : aliaspairs)
        {
            addPair(aliaspair.lhs, aliaspair.rhs);
        }
    }

    public void canonicalizeAliasPairs(Collection<Aliaspair<IndexName>> aliaspairs)
    {
        putPairs(aliaspairs);
        finishCanonicalize();
    }

    public ACL2Object collectAliasesAsACL2Objects()
    {
        Map<ACL2Backed, ACL2Object> backedCache = new HashMap<>();
        ACL2Object result = NIL;
        for (int i = arr.size() - 1; i >= 0; i--)
        {
            result = cons(arr.get(i).getACL2Object(backedCache), result);
        }
        return result;
    }
}
