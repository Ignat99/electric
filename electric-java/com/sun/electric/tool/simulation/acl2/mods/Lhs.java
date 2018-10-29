/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Lhs.java
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

import com.sun.electric.tool.simulation.acl2.svex.Svar;
import com.sun.electric.tool.simulation.acl2.svex.SvarName;
import com.sun.electric.tool.simulation.acl2.svex.SvarNameTexter;
import com.sun.electric.tool.simulation.acl2.svex.Svex;
import com.sun.electric.tool.simulation.acl2.svex.SvexManager;
import com.sun.electric.tool.simulation.acl2.svex.SvexQuote;
import com.sun.electric.tool.simulation.acl2.svex.Vec2;
import com.sun.electric.tool.simulation.acl2.svex.Vec4;
import com.sun.electric.tool.simulation.acl2.svex.funs.Vec4Concat;
import com.sun.electric.util.acl2.ACL2;
import static com.sun.electric.util.acl2.ACL2.*;
import com.sun.electric.util.acl2.ACL2Backed;
import com.sun.electric.util.acl2.ACL2Object;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * A shorthand format for an expression consisting of a concatenation of parts of variables.
 * See <http://www.cs.utexas.edu/users/moore/acl2/manuals/current/manual/?topic=SV____LHS>.
 *
 * @param <N> Type of name of Svex variables
 */
public class Lhs<N extends SvarName> implements ACL2Backed
{
    public final List<Lhrange<N>> ranges = new LinkedList<>();
    private final int hashCode;

    private static final Lhs<?> EMPTY = new Lhs<>(Collections.emptyList());

    public Lhs(List<Lhrange<N>> ranges)
    {
        this.ranges.addAll(ranges);
        int hashCode = ACL2Object.HASH_CODE_NIL;
        for (int i = this.ranges.size() - 1; i >= 0; i--)
        {
            Lhrange<N> range = this.ranges.get(i);
            hashCode = ACL2Object.hashCodeOfCons(range.hashCode(), hashCode);
        }
        this.hashCode = hashCode;
    }

    @SuppressWarnings("unchecked")
    public static <N extends SvarName> Lhs<N> empty()
    {
        return (Lhs<N>)EMPTY;
    }

    public static <N extends SvarName> Lhs<N> fromACL2(SvarName.Builder<N> snb, SvexManager<N> sm, ACL2Object impl)
    {
        List<ACL2Object> l = Util.getList(impl, true);
        Util.check(!l.isEmpty());
        List<Lhrange<N>> ranges = new ArrayList<>(l.size());
        for (ACL2Object o : l)
        {
            Lhrange<N> lhr = Lhrange.fromACL2(snb, sm, o);
            ranges.add(lhr);
        }
        return new Lhs<>(ranges);
    }

    public <N1 extends SvarName> Lhs<N1> convertVars(Function<N, N1> renameMap, SvexManager<N1> sm)
    {
        List<Lhrange<N1>> newRanges = new ArrayList<>();
        for (Lhrange<N> range : ranges)
        {
            newRanges.add(range.convertVars(renameMap, sm));
        }
        return new Lhs<>(newRanges);
    }

    public Vec4 eval(Map<Svar<N>, Vec4> env)
    {
        Vec4 result = Vec4.Z;
        for (int i = ranges.size() - 1; i >= 0; i--)
        {
            Lhrange<N> range = ranges.get(i);
            result = Vec4Concat.FUNCTION.apply(
                Vec2.valueOf(range.getWidth()),
                range.eval(env),
                result);
        }
        return result;
    }

    public int width()
    {
        int size = 0;
        for (Lhrange lr : ranges)
        {
            size += lr.getWidth();
        }
        return size;
    }

    public Lhs<N> cons(Lhrange<N> x)
    {
        List<Lhrange<N>> newRanges = new ArrayList<>();
        if (ranges.isEmpty())
        {
            if (x.getVar() != null)
            {
                newRanges.add(x);
            }
        } else
        {
            Lhrange<N> comb = x.combine(ranges.get(0));
            if (comb != null)
            {
                if (ranges.size() > 1 || comb.getVar() != null)
                {
                    newRanges.addAll(ranges);
                    ranges.set(0, comb);
                }
            } else
            {
                newRanges.add(x);
                newRanges.addAll(ranges);
            }
        }
        return new Lhs<>(newRanges);
    }

    public Lhs<N> norm()
    {
        if (isNormp())
        {
            return this;
        }
        List<Lhrange<N>> newRanges = new ArrayList<>();
        newRanges.addAll(ranges);
        for (int i = newRanges.size() - 1; i >= 0; i--)
        {
            Lhrange<N> range = newRanges.get(i);
            if (i == newRanges.size() - 1)
            {
                if (range.getVar() == null)
                {
                    newRanges.remove(i - 1);
                }
            } else
            {
                Lhrange<N> comb = range.combine(newRanges.get(i + 1));
                if (comb != null)
                {
                    newRanges.remove(i + 1);
                    newRanges.set(i, comb);
                }
            }
        }
        if (newRanges.size() == 1 && newRanges.get(0).getVar() == null)
        {
            newRanges.remove(0);
        }
        if (newRanges.equals(ranges))
        {
            isNormp();
        }
        assert !newRanges.equals(ranges);
        Lhs<N> newLhs = new Lhs<>(newRanges);
        assert newLhs.isNormp();
        return newLhs;
    }

    public boolean isNormp()
    {
        Svar<N> prevVar = null;
        int prevBit = -1;
        for (Lhrange<N> range : ranges)
        {
            Svar<N> svar = range.getVar();
            if (svar == null)
            {
                if (prevVar == null && prevBit >= 0)
                {
                    return false;
                }
            } else if (svar.equals(prevVar) && range.getRsh() == prevBit)
            {
                return false;
            }
            prevVar = svar;
            prevBit = range.getRsh() + range.getWidth();
        }
        return prevVar != null || prevBit == -1;
    }

    public Lhs<N> concat(int w, Lhs<N> y)
    {
        List<Lhrange<N>> newRanges = new ArrayList<>();
        int ww = 0;
        for (int i = 0; i < ranges.size() && ww < w; i++)
        {
            Lhrange<N> range = ranges.get(i);
            if (ww + range.getWidth() <= w)
            {
                newRanges.add(range);
                ww += range.getWidth();
            } else
            {
                newRanges.add(new Lhrange<>(w - ww, range.getAtom()));
                ww = w;
            }
        }
        if (ww < w)
        {
            newRanges.add(new Lhrange<>(w - ww, Lhatom.Z()));
        }
        newRanges.addAll(y.ranges);
        return new Lhs<>(newRanges).norm();
    }

    public Lhs<N> rsh(int sh)
    {
        List<Lhrange<N>> newRanges = new ArrayList<>(ranges);
        while (sh > 0 && !newRanges.isEmpty())
        {
            Lhrange<N> range = newRanges.get(0);
            if (sh < range.getWidth())
            {
                Lhatom<N> atom = range.getAtom();
                Svar<N> svar = atom.getVar();
                if (svar != null)
                {
                    atom = Lhatom.valueOf(svar, range.getRsh() + sh);
                }
                newRanges.set(0, new Lhrange<>(range.getWidth() - sh, atom));
                break;
            }
            newRanges.remove(0);
            sh -= range.getWidth();
        }
        return new Lhs<>(newRanges).norm();
    }

    void vars(Collection<Svar<N>> vars)
    {
        for (Lhrange<N> range : ranges)
        {
            range.getAtom().vars(vars);
        }
    }

    public static <N extends SvarName> List<Svar<N>> lhslistVars(List<Lhs<N>> list)
    {
        List<Svar<N>> vars = new ArrayList<>();
        for (Lhs<N> lhs : list)
        {
            lhs.vars(vars);
        }
        return vars;
    }

    Lhrange<N> first()
    {
        Lhs<N> norm = norm();
        return norm.ranges.isEmpty() ? null : norm.ranges.get(0);
    }

    Lhs<N> rest()
    {
        Lhs<N> norm = norm();
        if (norm.ranges.isEmpty())
        {
            return norm;
        }
        LinkedList<Lhrange<N>> newRanges = new LinkedList<>(norm.ranges);
        newRanges.pollFirst();
        return new Lhs<>(newRanges);
    }

    public static class Decomp<N extends SvarName>
    {
        public final Lhrange<N> first;
        public final Lhs<N> rest;

        private Decomp(Lhrange<N> first, Lhs<N> rest)
        {
            this.first = first;
            this.rest = rest;
        }
    }

    public Decomp<N> decomp()
    {
        Lhs<N> norm = norm();
        if (norm.ranges.isEmpty())
        {
            return new Decomp<>(null, norm);
        }
        LinkedList<Lhrange<N>> newRanges = new LinkedList<>(norm.ranges);
        Lhrange<N> first = newRanges.pollFirst();
        Lhs<N> rest = new Lhs<>(newRanges);
        return new Decomp<>(first, rest);
    }

    public Svex<N> toSvex(SvexManager<N> sm)
    {
        Svex<N> svex = SvexQuote.Z();
        for (int i = ranges.size() - 1; i >= 0; i--)
        {
            Lhrange<N> range = ranges.get(i);
            svex = range.getAtom().toSvex(sm).concat(sm, range.getWidth(), svex);
        }
        return svex;
    }

    public static <N extends SvarName> Lhs<N> makeSimpleLhs(int width, int rsh, Svar<N> svar)
    {
        Lhatom<N> atom = Lhatom.valueOf(svar, rsh);
        Lhrange<N> range = new Lhrange<>(width, atom);
        return new Lhs<>(Collections.singletonList(range));
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (o instanceof Lhs)
        {
            Lhs<?> that = (Lhs<?>)o;
            return this.hashCode == that.hashCode
                && this.ranges.equals(that.ranges);
        }
        return false;
    }

    @Override
    public int hashCode()
    {
        return hashCode;
    }

    @Override
    public ACL2Object getACL2Object(Map<ACL2Backed, ACL2Object> backedCache)
    {
        ACL2Object list = NIL;
        for (int i = ranges.size() - 1; i >= 0; i--)
        {
            list = ACL2.hons(ranges.get(i).getACL2Object(backedCache), list);
        }
        assert list.hashCode() == hashCode;
        return list;
    }

    @Override
    public String toString()
    {
        String s = "";
        for (int i = ranges.size() - 1; i >= 0; i--)
        {
            s += ranges.get(i);
            if (i > 0)
            {
                s += ",";
            }
        }
        return s;
    }

    public String toString(SvarNameTexter<N> texter)
    {
        String s = "";
        int offset = width();
        for (int i = ranges.size() - 1; i >= 0; i--)
        {
            s += ranges.get(i).toString(texter);
            if (i > 0)
            {
                s += ",";
            }
        }
        return s;
    }

    @SuppressWarnings("unchecked")
    public static <N extends SvarName> Lhs<N>[] newLhsArray(int length)
    {
        return new Lhs[length];
    }
}
