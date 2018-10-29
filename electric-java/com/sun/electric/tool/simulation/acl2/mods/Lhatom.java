/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Lhatom.java
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
import com.sun.electric.tool.simulation.acl2.svex.SvarImpl;
import com.sun.electric.tool.simulation.acl2.svex.SvarName;
import com.sun.electric.tool.simulation.acl2.svex.SvarNameTexter;
import com.sun.electric.tool.simulation.acl2.svex.Svex;
import com.sun.electric.tool.simulation.acl2.svex.SvexManager;
import com.sun.electric.tool.simulation.acl2.svex.SvexQuote;
import com.sun.electric.tool.simulation.acl2.svex.SvexVar;
import com.sun.electric.tool.simulation.acl2.svex.Vec2;
import com.sun.electric.tool.simulation.acl2.svex.Vec4;
import com.sun.electric.tool.simulation.acl2.svex.funs.Vec4Rsh;
import static com.sun.electric.util.acl2.ACL2.*;
import com.sun.electric.util.acl2.ACL2Backed;
import com.sun.electric.util.acl2.ACL2Object;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * An SVar or X at left-hand side of SVEX assignment.
 * See <http://www.cs.utexas.edu/users/moore/acl2/manuals/current/manual/?topic=SV____LHATOM>.
 *
 * @param <N> Type of name of Svex Variables
 */
public abstract class Lhatom<N extends SvarName> implements ACL2Backed
{
    private static final Lhatom<?> Z = new Z<>();

    public static <N extends SvarName> Lhatom<N> valueOf(Svar<N> svar)
    {
        return valueOf(svar, 0);
    }

    public static <N extends SvarName> Lhatom<N> valueOf(Svar<N> svar, int rsh)
    {
        return new Var<>(svar, rsh);
    }

    @SuppressWarnings("unchecked")
    public static <N extends SvarName> Lhatom<N> Z()
    {
        return (Lhatom<N>)Z;
    }

    public static <N extends SvarName> Lhatom<N> fromACL2(SvarName.Builder<N> snb, SvexManager<N> sm, ACL2Object impl)
    {
        if (impl.equals(Util.KEYWORD_Z))
        {
            return Z();
        }
        ACL2Object nameImpl = impl;
        int rsh = 0;
        if (consp(impl).bool() && !(car(impl).equals(Util.KEYWORD_VAR) && consp(cdr(impl)).bool()))
        {
            nameImpl = car(impl);
            rsh = cdr(impl).intValueExact();
            Util.check(rsh >= 0);
        }
        Svar<N> svar = SvarImpl.fromACL2(snb, sm, nameImpl);
        return valueOf(svar, rsh);
    }

    public abstract Svar<N> getVar();

    public abstract int getRsh();

    public abstract <N1 extends SvarName> Lhatom<N1> convertVars(Function<N, N1> rename, SvexManager<N1> sm);

    public abstract Vec4 eval(Map<Svar<N>, Vec4> env);

    public abstract Svex<N> toSvex(SvexManager<N> sm);

    abstract void vars(Collection<Svar<N>> vars);

    @Override
    public String toString()
    {
        return getACL2Object().rep();
    }

    public abstract String toString(SvarNameTexter<N> texter, int width);

    @Override
    public boolean equals(Object o)
    {
        if (o instanceof Lhatom)
        {
            Lhatom<?> that = (Lhatom<?>)o;
            return Objects.equals(this.getVar(), that.getVar())
                && this.getRsh() == that.getRsh();
        }
        return false;
    }

    @Override
    public int hashCode()
    {
        return Objects.hashCode(getVar()) * 13 + getRsh();
    }

    private static class Z<N extends SvarName> extends Lhatom<N>
    {
        private Z()
        {

        }

        @Override
        public int hashCode()
        {
            return Util.KEYWORD_Z.hashCode();
        }

        @Override
        public ACL2Object getACL2Object()
        {
            return Util.KEYWORD_Z;
        }

        @Override
        public String toString(SvarNameTexter<N> texter, int width)
        {
            return width + "'Z";
        }

        @Override
        public Svar<N> getVar()
        {
            return null;
        }

        @Override
        public int getRsh()
        {
            return 0;
        }

        @Override
        public <N1 extends SvarName> Lhatom<N1> convertVars(Function<N, N1> rename, SvexManager<N1> sm)
        {
            return Z();
        }

        @Override
        public Vec4 eval(Map<Svar<N>, Vec4> env)
        {
            return Vec4.Z;
        }

        @Override
        public Svex<N> toSvex(SvexManager<N> sm)
        {
            return SvexQuote.Z();
        }

        @Override
        void vars(Collection<Svar<N>> vars)
        {
        }
    }

    private static class Var<N extends SvarName> extends Lhatom<N>
    {
        final Svar<N> name;
        final int rsh;
        final int hashCode;

        private Var(Svar<N> name, int rsh)
        {
            if (name == null)
            {
                throw new NullPointerException();
            }
            if (rsh < 0)
            {
                throw new IllegalArgumentException();
            }
            this.name = name;
            this.rsh = rsh;
            int nameHashCode = name.hashCode();
            boolean nameIsZ = nameHashCode == Util.KEYWORD_Z.hashCode()
                && name.getACL2Object().equals(Util.KEYWORD_Z);
            hashCode = rsh == 0 && !nameIsZ
                ? nameHashCode
                : ACL2Object.hashCodeOfCons(nameHashCode, ACL2Object.hashCodeOf(rsh));
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o)
            {
                return true;
            }
            if (o instanceof Var)
            {
                Var that = (Var)o;
                return this.hashCode == that.hashCode
                    && this.name.equals(that.name)
                    && this.rsh == that.rsh;
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
            ACL2Object nameImpl = name.getACL2Object(backedCache);
            ACL2Object result = rsh == 0 && !Util.KEYWORD_Z.equals(nameImpl)
                ? nameImpl
                : hons(nameImpl, ACL2Object.valueOf(rsh));
            assert result.hashCode() == hashCode;
            return result;
        }

        @Override
        public String toString(SvarNameTexter<N> texter, int width)
        {
            return name.toString(texter, width, rsh);
        }

        @Override
        public Svar<N> getVar()
        {
            return name;
        }

        @Override
        public int getRsh()
        {
            return rsh;
        }

        @Override
        public <N1 extends SvarName> Lhatom<N1> convertVars(Function<N, N1> rename, SvexManager<N1> sm)
        {
            N1 newName = rename.apply(name.getName());
            Svar<N1> newVar = sm.getVar(newName);
            return valueOf(newVar, rsh);
        }

        @Override
        public Vec4 eval(Map<Svar<N>, Vec4> env)
        {
            Vec4 sh = Vec2.valueOf(rsh);
            Vec4 x = env.getOrDefault(name, Vec4.X);
            return Vec4Rsh.FUNCTION.apply(sh, x);
        }

        @Override
        public Svex<N> toSvex(SvexManager<N> sm)
        {
            Svex<N> svexVar = new SvexVar<>(name);
            return rsh != 0 ? svexVar.rsh(sm, rsh) : svexVar;
        }

        @Override
        void vars(Collection<Svar<N>> vars)
        {
            vars.add(name);
        }
    }
}
