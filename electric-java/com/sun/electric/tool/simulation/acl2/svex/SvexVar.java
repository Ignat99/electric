/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: SvexVar.java
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

import com.sun.electric.tool.simulation.acl2.mods.Lhatom;
import com.sun.electric.tool.simulation.acl2.mods.Lhrange;
import com.sun.electric.tool.simulation.acl2.mods.Lhs;
import com.sun.electric.util.acl2.ACL2Backed;
import com.sun.electric.util.acl2.ACL2Object;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * A variable, which represents a 4vec.
 * See <http://www.cs.utexas.edu/users/moore/acl2/manuals/current/manual/?topic=SV____SVEX-VAR>.
 *
 * @param <N> Type of name of Svex variables
 */
public class SvexVar<N extends SvarName> extends Svex<N>
{
    public Svar<N> svar;

    public SvexVar(Svar<N> svar)
    {
        if (svar == null)
        {
            throw new NullPointerException();
        }
        this.svar = svar;
    }

    @Override
    public <N1 extends SvarName> Svex<N1> convertVars(Function<N, N1> rename, SvexManager<N1> sm, Map<Svex<N>, Svex<N1>> cache)
    {
        Svex<N1> svex = cache.get(this);
        if (svex == null)
        {
            N1 newName = rename.apply(svar.getName());
            svex = sm.getSvex(newName, svar.getDelay(), svar.isNonblocking());
            cache.put(this, svex);
        }
        return svex;
    }

    @Override
    public Svex<N> addDelay(int delay, SvexManager<N> sm, Map<Svex<N>, Svex<N>> cache)
    {
        Svex<N> svex = cache.get(this);
        if (svex == null)
        {
            svex = sm.getSvex(svar.getName(), delay + svar.getDelay(), svar.isNonblocking());
            cache.put(this, svex);
        }
        return svex;
    }

    /*
    @Override
    public <N1 extends SvarName> Svex<N1> addDelay(int delay, Svar.Builder<N1> builder, SvexManager<N1> sm, Map<Svex<N>, Svex<N1>> cache)
    {
        Svex<N1> svex = cache.get(this);
        if (svex == null)
        {
            svex = sm.getSvex(svar.getName(), delay + svar.getDelay(), svar.isNonblocking());
            cache.put(this, svex);
        }
        return svex;
    }
     */
    @Override
    protected void collectVarsRev(Set<Svar<N>> result, Set<SvexCall<N>> visited)
    {
        result.add(svar);
    }

    @Override
    public <R, D> R accept(Visitor<N, R, D> visitor, D data)
    {
        return visitor.visitVar(svar, data);
    }

    @Override
    <R> R traverse(TraverseVisitor<N, R> visitor, Map<Svex<N>, R> cache)
    {
        R result = cache.get(this);
        if (result == null)
        {
            result = visitor.visitVar(svar);
            cache.put(this, result);
        }
        return result;
    }

    @Override
    public Vec4 xeval(Map<Svex<N>, Vec4> memoize)
    {
        return Vec4.X;
    }

    @Override
    public Svex<N> patch(Map<Svar<N>, Vec4> subst, SvexManager<N> sm, Map<SvexCall<N>, SvexCall<N>> memoize)
    {
        Vec4 val = subst.get(svar);
        return val != null ? SvexQuote.valueOf(val) : this;
    }

    @Override
    public boolean isLhsUnbounded()
    {
        return true;
    }

    @Override
    public boolean isLhs()
    {
        return false;
    }

    @Override
    public Lhs<N> lhsBound(int w)
    {
        Lhatom<N> atom = Lhatom.valueOf(svar);
        Lhrange<N> range = new Lhrange<>(w, atom);
        return new Lhs<>(Collections.singletonList(range));
    }

    @Override
    public Lhs<N> toLhs()
    {
        return new Lhs<>(Collections.emptyList());
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (o instanceof SvexVar)
        {
            SvexVar<?> that = (SvexVar<?>)o;
            return this.hashCode() == that.hashCode()
                && this.svar.equals(that.svar);
        }
        return false;
    }

    @Override
    public int hashCode()
    {
        return svar.hashCode();
    }

    @Override
    public ACL2Object getACL2Object(Map<ACL2Backed, ACL2Object> backedCache)
    {
        return svar.getACL2Object(backedCache);
    }
}
