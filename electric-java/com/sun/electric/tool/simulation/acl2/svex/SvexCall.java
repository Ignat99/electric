/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: SvexCall.java
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

import com.sun.electric.tool.simulation.acl2.mods.Lhs;
import com.sun.electric.tool.simulation.acl2.svex.funs.Vec4SignExt;
import com.sun.electric.tool.simulation.acl2.svex.funs.Vec4ZeroExt;
import static com.sun.electric.util.acl2.ACL2.*;
import com.sun.electric.util.acl2.ACL2Backed;
import com.sun.electric.util.acl2.ACL2Object;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * A function applied to some expressions.
 * See <http://www.cs.utexas.edu/users/moore/acl2/manuals/current/manual/?topic=SV____SVEX-CALL>.
 *
 * @param <N> Type of name of Svex variables
 */
public abstract class SvexCall<N extends SvarName> extends Svex<N>
{
    public final SvexFunction fun;
    protected final Svex<N>[] args;
    private final int hashCode;
    private SvexManager owner;

    @SafeVarargs
    public static <N extends SvarName> SvexCall<N> newCall(SvexFunction fun, Svex<N>... args)
    {
        return fun.build(args);
//        return new SvexCall<>(fun, args);
    }

    @SafeVarargs
    protected SvexCall(SvexFunction fun, Svex<N>... args)
    {
        assert fun.arity == args.length;
        this.fun = fun;
        this.args = args.clone();
        for (Svex<N> arg : this.args)
        {
            if (arg == null)
            {
                throw new NullPointerException();
            }
        }
        int hashCode = ACL2Object.HASH_CODE_NIL;
        for (int i = args.length - 1; i >= 0; i--)
        {
            hashCode = ACL2Object.hashCodeOfCons(args[i].hashCode(), hashCode);
        }
        this.hashCode = ACL2Object.hashCodeOfCons(fun.fn.hashCode(), hashCode);
    }

    void setOwner(SvexManager<N> owner)
    {
        assert this.owner == null;
        this.owner = owner;
    }

    public Svex<N>[] getArgs()
    {
        return args.clone();
    }

    @Override
    public <N1 extends SvarName> Svex<N1> convertVars(Function<N, N1> rename, SvexManager<N1> sm, Map<Svex<N>, Svex<N1>> cache)
    {
        Svex<N1> svex = cache.get(this);
        if (svex == null)
        {
            Svex<N1>[] newArgs = Svex.newSvexArray(fun.arity);
            for (int i = 0; i < fun.arity; i++)
            {
                newArgs[i] = args[i].convertVars(rename, sm, cache);
            }
            svex = sm.newCall(fun, newArgs);
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
            Svex<N>[] newArgs = Svex.newSvexArray(fun.arity);
            for (int i = 0; i < fun.arity; i++)
            {
                newArgs[i] = args[i].addDelay(delay, sm, cache);
            }
            svex = sm.newCall(fun, newArgs);
            cache.put(this, svex);
        }
        return svex;
    }

    /*
    @Override
    public <N1 extends SvarName> Svex<N1> addDelay(int delay, SvexManager<N1> sm, Map<Svex<N>, Svex<N1>> cache)
    {
        Svex<N1> svex = cache.get(this);
        if (svex == null)
        {
            Svex<N1>[] newArgs = Svex.newSvexArray(fun.arity);
            for (int i = 0; i < fun.arity; i++)
            {
                newArgs[i] = args[i].addDelay(delay, sm, cache);
            }
            svex = sm.newCall(fun, newArgs);
            cache.put(this, svex);
        }
        return svex;
    }
     */
    @Override
    protected void collectVarsRev(Set<Svar<N>> result, Set<SvexCall<N>> visited)
    {
        if (visited.add(this))
        {
            for (Svex<N> arg : args)
            {
                arg.collectVarsRev(result, visited);
            }
        }
    }

    @Override
    public <R, D> R accept(Visitor<N, R, D> visitor, D data)
    {
        return visitor.visitCall(fun, args, data);
    }

    @Override
    <R> R traverse(TraverseVisitor<N, R> visitor, Map<Svex<N>, R> cache)
    {
        R result = cache.get(this);
        if (result == null && !cache.containsKey(this))
        {
            R[] argVals = visitor.newVals(args.length);
//            R[] argVals = new R[args.length];
            for (int i = 0; i < args.length; i++)
            {
                argVals[i] = args[i].traverse(visitor, cache);
            }
            result = visitor.visitCall(fun, args, argVals);
            cache.put(this, result);
        }
        return result;
    }

    @Override
    public Vec4 xeval(Map<Svex<N>, Vec4> memoize)
    {
        Vec4 result = memoize.get(this);
        if (result == null)
        {
            result = fun.apply(Svex.listXeval(args, memoize));
            memoize.put(this, result);
        }
        return result;
    }

    @Override
    void toposort(Set<Svex<N>> downTop)
    {
        if (!downTop.contains(this))
        {
            for (Svex<N> arg : args)
            {
                arg.toposort(downTop);
            }
            downTop.add(this);
        }
    }

    @Override
    public Svex<N> patch(Map<Svar<N>, Vec4> subst, SvexManager<N> sm, Map<SvexCall<N>, SvexCall<N>> memoize)
    {
        SvexCall<N> svex = memoize.get(this);
        if (svex == null)
        {
            Svex<N>[] newArgs = Svex.newSvexArray(args.length);
            boolean changed = false;
            for (int i = 0; i < args.length; i++)
            {
                newArgs[i] = args[i].patch(subst, sm, memoize);
                changed = changed || newArgs[i] != args[i];
            }
            svex = changed ? sm.newCall(fun, newArgs) : this;
            memoize.put(this, svex);
        }
        return svex;
    }

    @Override
    public boolean isLhsUnbounded()
    {
        return false;
    }

    @Override
    public boolean isLhs()
    {
        return false;
    }

    @Override
    public Lhs<N> lhsBound(int w)
    {
        return new Lhs<>(Collections.emptyList());
    }

    @Override
    public Lhs<N> toLhs()
    {
        return new Lhs<>(Collections.emptyList());
    }

    @Override
    public MatchConcat<N> matchConcat()
    {
        return null;
    }

    @Override
    public MatchExt<N> matchExt()
    {
        if (fun == Vec4ZeroExt.FUNCTION || fun == Vec4SignExt.FUNCTION)
        {
            Svex<N> width = args[0];
            if (width instanceof SvexQuote)
            {
                Vec4 wval = ((SvexQuote)width).val;
                if (wval.isVec2() && ((Vec2)wval).getVal().signum() >= 0)
                {
                    return new MatchExt<>(((Vec2)wval).getVal().intValueExact(), args[1],
                        fun == Vec4SignExt.FUNCTION);
                }
            }
        }
        return null;
    }

    @Override
    public MatchRsh<N> matchRsh()
    {
        return null;
    }

    /* rewrite.lisp */
    @Override
    void multirefs(Set<SvexCall<N>> seen, Set<SvexCall<N>> multirefs)
    {
        if (seen.contains(this))
        {
            multirefs.add(this);
            return;
        }
        seen.add(this);
        for (Svex<N> arg : args)
        {
            arg.multirefs(seen, multirefs);
        }
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (o instanceof SvexCall)
        {
            SvexCall<?> that = (SvexCall<?>)o;
            if (this.hashCode != that.hashCode)
            {
                return false;
            }
            if (this.owner != null && this.owner == that.owner)
            {
                return false;
            }
            if (!this.fun.equals(that.fun))
            {
                return false;
            }
            return Arrays.equals(this.args, that.args);
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
        ACL2Object result = backedCache.get(this);
        if (result == null)
        {
            result = NIL;
            for (int i = args.length - 1; i >= 0; i--)
            {
                result = hons(args[i].getACL2Object(backedCache), result);
            }
            result = hons(fun.fn, result);
            backedCache.put(this, result);
        }
        assert result.hashCode() == hashCode;
        return result;
    }

}
