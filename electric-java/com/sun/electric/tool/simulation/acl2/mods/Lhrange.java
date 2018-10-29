/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Lhrange.java
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
import com.sun.electric.tool.simulation.acl2.svex.SvarName;
import com.sun.electric.tool.simulation.acl2.svex.SvarNameTexter;
import com.sun.electric.tool.simulation.acl2.svex.Svex;
import com.sun.electric.tool.simulation.acl2.svex.SvexManager;
import com.sun.electric.tool.simulation.acl2.svex.SvexQuote;
import com.sun.electric.tool.simulation.acl2.svex.Vec2;
import com.sun.electric.tool.simulation.acl2.svex.Vec4;
import com.sun.electric.tool.simulation.acl2.svex.funs.Vec4Concat;
import static com.sun.electric.util.acl2.ACL2.*;
import com.sun.electric.util.acl2.ACL2Backed;
import com.sun.electric.util.acl2.ACL2Object;
import java.util.Map;
import java.util.function.Function;

/**
 * An atom with width from left-hand side of SVEX assignment.
 * See <http://www.cs.utexas.edu/users/moore/acl2/manuals/current/manual/?topic=SV____LHRANGE>.
 *
 * @param <N> Type of name of Svex variables
 */
public class Lhrange<N extends SvarName> implements ACL2Backed
{
    private final int w;
    private final Lhatom<N> atom;
    private final int hashCode;

    public Lhrange(int w, Lhatom<N> atom)
    {
        this.w = w;
        this.atom = atom;
        hashCode = w == 1 ? atom.hashCode() : ACL2Object.hashCodeOfCons(ACL2Object.hashCodeOf(w), atom.hashCode());
    }

    public static <N extends SvarName> Lhrange<N> fromACL2(SvarName.Builder<N> snb, SvexManager<N> sm, ACL2Object impl)
    {
        int w = 1;
        ACL2Object atomImpl = impl;
        if (consp(impl).bool() && integerp(car(impl)).bool())
        {
            w = car(impl).intValueExact();
            atomImpl = cdr(impl);
        }
        Lhatom<N> atom = Lhatom.fromACL2(snb, sm, atomImpl);
        return new Lhrange<>(w, atom);
    }

    public <N1 extends SvarName> Lhrange<N1> convertVars(Function<N, N1> renameMap, SvexManager<N1> sm)
    {
        return new Lhrange<>(w, atom.convertVars(renameMap, sm));
    }

    public Vec4 eval(Map<Svar<N>, Vec4> env)
    {
        return Vec4Concat.FUNCTION.apply(Vec2.valueOf(w), atom.eval(env), Vec4.Z);
    }

    public Svex<N> toSvex(SvexManager<N> sm)
    {
        Svex<N>[] args = Svex.newSvexArray(3);
        args[0] = SvexQuote.valueOf(w);
        args[1] = atom.toSvex(sm);
        args[2] = SvexQuote.Z();
        return sm.newCall(Vec4Concat.FUNCTION, args);
    }

    public Lhatom<N> nextbit()
    {
        Svar<N> svar = atom.getVar();
        return svar != null ? Lhatom.valueOf(svar, w + atom.getRsh()) : atom;
    }

    public boolean combinable(Lhatom<N> y)
    {
        Svar<N> vx = getVar();
        Svar<N> vy = y.getVar();
        if (vx == null)
        {
            return vy == null;
        } else
        {
            return vx.equals(vy) && y.getRsh() == getRsh() + w;
        }
    }

    public Lhrange<N> combine(Lhrange<N> y)
    {
        Svar<N> vx = getVar();
        Svar<N> vy = y.getVar();
        if (vx == null)
        {
            if (vy == null)
            {
                return new Lhrange<>(w + y.w, atom);
            }
        } else if (vx.equals(vy) && y.getRsh() == getRsh() + w)
        {
            return new Lhrange<>(w + y.w, atom);
        }
        return null;
    }

    public Lhatom<N> getAtom()
    {
        return atom;
    }

    public Svar<N> getVar()
    {
        return atom.getVar();
    }

    public int getWidth()
    {
        return w;
    }

    public int getRsh()
    {
        return atom.getRsh();
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (o instanceof Lhrange)
        {
            Lhrange<?> that = (Lhrange<?>)o;
            return this.hashCode == that.hashCode
                && this.atom.equals(that.atom)
                && this.w == that.w;
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
        ACL2Object atomImpl = atom.getACL2Object(backedCache);
        ACL2Object result = w == 1 ? atomImpl : hons(ACL2Object.valueOf(w), atomImpl);
        assert result.hashCode() == hashCode;
        return result;
    }

    @Override
    public String toString()
    {
        Svar<N> name = getVar();
        if (name != null)
        {
            return name.toString(BigIntegerUtil.logheadMask(getWidth()).shiftLeft(getRsh()));
        } else
        {
            return w + "'Z";
        }
    }

    public String toString(SvarNameTexter<N> texter)
    {
        return atom.toString(texter, w);
    }
}
