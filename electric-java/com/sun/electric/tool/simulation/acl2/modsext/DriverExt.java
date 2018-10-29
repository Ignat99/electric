/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: DriverExt.java
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
package com.sun.electric.tool.simulation.acl2.modsext;

import com.sun.electric.tool.simulation.acl2.mods.Driver;
import com.sun.electric.tool.simulation.acl2.mods.Lhrange;
import com.sun.electric.tool.simulation.acl2.mods.Lhs;
import com.sun.electric.tool.simulation.acl2.mods.Util;
import com.sun.electric.tool.simulation.acl2.svex.BigIntegerUtil;
import com.sun.electric.tool.simulation.acl2.svex.Svar;
import com.sun.electric.tool.simulation.acl2.svex.SvarName;
import com.sun.electric.tool.simulation.acl2.svex.Svex;
import com.sun.electric.tool.simulation.acl2.svex.SvexCall;
import com.sun.electric.tool.simulation.acl2.svex.SvexFunction;
import com.sun.electric.tool.simulation.acl2.svex.SvexManager;
import com.sun.electric.tool.simulation.acl2.svex.SvexQuote;
import com.sun.electric.tool.simulation.acl2.svex.SvexVar;
import com.sun.electric.tool.simulation.acl2.svex.Vec2;
import com.sun.electric.tool.simulation.acl2.svex.Vec4;
import com.sun.electric.tool.simulation.acl2.svex.funs.Vec4Concat;
import com.sun.electric.tool.simulation.acl2.svex.funs.Vec4Rsh;
import com.sun.electric.tool.simulation.acl2.svex.funs.Vec4SignExt;
import com.sun.electric.tool.simulation.acl2.svex.funs.Vec4ZeroExt;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import java.util.Set;

/**
 * Driver - SVEX expression with strength.
 * See <http://www.cs.utexas.edu/users/moore/acl2/manuals/current/manual/?topic=SV____DRIVER>.
 */
public class DriverExt
{
    final ModuleExt parent;

    private final Driver<PathExt> b;
    final String name;

    private final Svex<PathExt> normSvex;
    private final Set<Svex<PathExt>> normSvexRecalc = new HashSet<>();
    private final List<Svar<PathExt>> normVars;

    Map<Svar<PathExt>, BigInteger> crudeDeps0;
    Map<Svar<PathExt>, BigInteger> crudeDeps1;
    final List<Map<Svar<PathExt>, BigInteger>> fineBitLocDeps0 = new ArrayList<>();
    final List<Map<Svar<PathExt>, BigInteger>> fineBitLocDeps1 = new ArrayList<>();

    PathExt.Bit[] pathBits;
    boolean splitIt;

    DriverExt(ModuleExt parent, Driver<PathExt> b, String name)
    {
        this.parent = parent;
        this.b = b;
        this.name = name;
        Util.check(b.strength == 6);
        for (Svar<PathExt> svar : b.vars)
        {
            Util.check(svar.getName() instanceof WireExt);
        }
        normSvex = normAssign(b.svex, parent.sm, normSvexRecalc);
        normVars = normSvex.collectVars();
//        checkNormSvex(normSvex);
    }

    public Svex<PathExt> getOrigSvex()
    {
        return b.svex;
    }

    public List<Svar<PathExt>> getOrigVars()
    {
        return b.vars;
    }

    public Svex<PathExt> getNormSvex()
    {
        return normSvex;
    }

    public List<Svar<PathExt>> getNormVars()
    {
        return normVars;
    }

    public int getStrength()
    {
        return b.strength;
    }

    public int getWidth()
    {
        return pathBits.length;
    }

    public PathExt.Bit getBit(int bit)
    {
        return pathBits[bit];
    }

    /**
     * Substitute Lhs into expression and normalize
     *
     * @param <N> SvarName of new Svex
     * @param args Lhs for variables in the same order as getOrigVars()
     * @param sm SvexManager
     * @return expression after substitution and normalization
     */
    public <N extends SvarName> Svex<N> subst(Lhs<N>[] args, SvexManager<N> sm)
    {
        Svex<N> svexOrig = substOrig(args, sm);
        Svex<N> svexNorm = substNorm(args, sm);
        if (svexOrig != svexNorm)
        {
            System.out.print("ORIG:");
            GenFsmNew.printSvex(System.out, 2, getOrigSvex());
            System.out.print("NORM:");
            GenFsmNew.printSvex(System.out, 2, getNormSvex());
            System.out.print("ORIG:");
            GenFsmNew.printSvex(System.out, 2, svexOrig);
            System.out.print("NORM:");
            GenFsmNew.printSvex(System.out, 2, svexNorm);
            substNorm(args, sm);
        }
//        assert svexOrig == svexNorm;
        return svexOrig;
    }

    @Override
    public String toString()
    {
        assert getStrength() == 6;
        return name != null ? name : getOrigSvex().toString();
    }

    void setSource(Lhs<PathExt> lhs)
    {
        assert pathBits == null;
        pathBits = new PathExt.Bit[lhs.width()];
        int lsh = 0;
        for (Lhrange<PathExt> range : lhs.ranges)
        {
            Svar<PathExt> svar = range.getVar();
            Util.check(svar.getDelay() == 0);
            PathExt pathExt = svar.getName();
            for (int bit = 0; bit < range.getWidth(); bit++)
            {
                pathBits[lsh + bit] = pathExt.getBit(range.getRsh() + bit);
                if (pathExt instanceof PathExt.PortInst)
                {
                    Util.check(pathBits[lsh + bit] == ((PathExt.PortInst)pathExt).getParentBit(lsh + bit));
                }
            }
            lsh += range.getWidth();
        }
    }

    void markUsed()
    {
        for (Svar<PathExt> svar : getOrigVars())
        {
            ((WireExt)svar.getName()).markUsed();
        }
    }

    Map<Svar<PathExt>, BigInteger> getCrudeDeps(boolean clockHigh)
    {
        return clockHigh ? crudeDeps1 : crudeDeps0;
    }

    List<Map<Svar<PathExt>, BigInteger>> getFineBitLocDeps(boolean clockHigh)
    {
        return clockHigh ? fineBitLocDeps1 : fineBitLocDeps0;
    }

    /**
     * Compute crude and fine dependencies of this driver.
     * Driver SV expression is patched by assumption about clock value
     *
     * @param width width of this driver used by left-hand side
     * @param clkVal which clock value is assumed in the patch
     * @param env environment for the patch
     * @param patchMemoize memoization cache for patch
     */
    void computeDeps(int width, boolean clkVal, Map<Svar<PathExt>, Vec4> env,
        Map<SvexCall<PathExt>, SvexCall<PathExt>> patchMemoize)
    {
        Svex<PathExt> patched = getOrigSvex().patch(env, parent.sm, patchMemoize);
        BigInteger mask = BigIntegerUtil.logheadMask(width);
        Map<Svar<PathExt>, BigInteger> varsWithMasks = patched.collectVarsWithMasks(mask, true);
        if (clkVal)
        {
            crudeDeps1 = varsWithMasks;
        } else
        {
            crudeDeps0 = varsWithMasks;
        }
        Map<Svar<PathExt>, BigInteger> crudeDepsCheck = new HashMap<>();
        List<Map<Svar<PathExt>, BigInteger>> fineDeps = getFineBitLocDeps(clkVal);
        fineDeps.clear();
        for (int bit = 0; bit < width; bit++)
        {
            mask = BigInteger.ONE.shiftLeft(bit);
            Map<Svar<PathExt>, BigInteger> bitVarsWithMasks = patched.collectVarsWithMasks(mask, true);
            fineDeps.add(bitVarsWithMasks);
            for (Map.Entry<Svar<PathExt>, BigInteger> e : bitVarsWithMasks.entrySet())
            {
                Svar<PathExt> svar = e.getKey();
                mask = e.getValue();
                if (mask == null || mask.signum() == 0)
                {
                    continue;
                }
                if (svar.getDelay() != 0)
                {
                    assert svar.getDelay() == 1;
                    Map<Svar<PathExt>, BigInteger> stateVars = clkVal ? parent.stateVars1 : parent.stateVars0;
                    BigInteger oldMask = stateVars.get(svar);
                    if (oldMask == null)
                    {
                        oldMask = BigInteger.ZERO;
                    }
                    stateVars.put(svar, oldMask.or(mask));
                }
                BigInteger crudeMask = crudeDepsCheck.get(svar);
                if (crudeMask == null)
                {
                    crudeMask = BigInteger.ZERO;
                }
                crudeDepsCheck.put(svar, crudeMask.or(mask));
            }
        }
        Util.check(varsWithMasks.equals(crudeDepsCheck));
    }

    public String showFinePortDeps(Map<Object, Set<Object>> graph0, Map<Object, Set<Object>> graph1)
    {
        return parent.showFinePortDeps(pathBits, graph0, graph1);
    }

    List<Map<Svar<PathExt>, BigInteger>> gatherFineBitDeps(BitSet stateDeps, Map<Object, Set<Object>> graph)
    {
        return parent.gatherFineBitDeps(stateDeps, pathBits, graph);
    }

    /**
     * Substitute Lhs into expression and normalize using original Svex
     *
     * @param <N> SvarName of new Svex
     * @param args Lhs for variables in the same order as getOrigVars()
     * @param sm SvexManager
     * @return expression after substitution and normalization
     */
    private <N extends SvarName> Svex<N> substOrig(Lhs<N>[] args, SvexManager<N> sm)
    {
        Svex.TraverseVisitor<PathExt, Svex<N>> visitor = new Svex.TraverseVisitor<PathExt, Svex<N>>()
        {
            @Override
            public Svex<N> visitQuote(Vec4 val)
            {
                return SvexQuote.valueOf(val);
            }

            @Override
            public Svex<N> visitVar(Svar<PathExt> svar)
            {
                int iArg = getOrigVars().indexOf(svar);
                Lhs<N> arg = args[iArg];
                Map<Svex<N>, Svex<N>> addDelayCache = new HashMap<>();
                return arg.toSvex(sm).addDelay(svar.getDelay(), sm, addDelayCache);
            }

            @Override
            public Svex<N> visitCall(SvexFunction fun, Svex<PathExt>[] args, Svex<N>[] argVals)
            {
                return fun.<N>callStar(sm, argVals);
            }

            @Override
            public Svex<N>[] newVals(int arity)
            {
                return Svex.newSvexArray(arity);
            }
        };
        return getOrigSvex().traverse(visitor);
    }

    private Svex<PathExt> normAssign(Svex<PathExt> top, SvexManager<PathExt> sm, Set<Svex<PathExt>> recalcSet)
    {
        Svex.TraverseVisitor<PathExt, Svex<PathExt>> visitor
            = new Svex.TraverseVisitor<PathExt, Svex<PathExt>>()
        {
            @Override
            public Svex<PathExt> visitQuote(Vec4 val)
            {
                return SvexQuote.valueOf(val);
            }

            @Override
            public Svex<PathExt> visitVar(Svar<PathExt> svar)
            {
                Svex<PathExt> svex = sm.getSvex(svar);
                recalcSet.add(svex);
                return svex;
            }

            @Override
            public Svex<PathExt> visitCall(SvexFunction fun, Svex<PathExt>[] args, Svex<PathExt>[] argVals)
            {
                if (fun == Vec4Rsh.FUNCTION && args[0] instanceof SvexQuote && args[1] instanceof SvexVar)
                {
                    assert argVals[0] == args[0];
                    Vec4 sh = ((SvexQuote<PathExt>)args[0]).val;
                    if (sh.isIndex())
                    {
                        int shVal = ((Vec2)sh).getVal().intValueExact();
                        assert shVal > 0;
                        Svar<PathExt> svar = ((SvexVar<PathExt>)args[1]).svar;
                        int width = svar.getName().getWidth();
                        if (shVal >= width)
                        {
                            return SvexQuote.Z();
                        }
                    }
                }
                Svex<PathExt> svex = fun.callStar(sm, argVals);
                if ((fun == Vec4Rsh.FUNCTION
                    || fun == Vec4Concat.FUNCTION
                    || fun == Vec4ZeroExt.FUNCTION
                    || fun == Vec4SignExt.FUNCTION)
                    && args[0] instanceof SvexQuote)
                {
                    for (Svex<PathExt> argVal : argVals)
                    {
                        if (recalcSet.contains(argVal))
                        {
                            recalcSet.add(svex);
                            break;
                        }
                    }
                }
                return svex;
            }

            @Override
            public Svex<PathExt>[] newVals(int arity)
            {
                return Svex.newSvexArray(arity);
            }

        };
        return top.traverse(visitor);
    }

    /**
     * Substitute Lhs into expression and normalize using normalized Svex
     *
     * @param <N> SvarName of new Svex
     * @param lhses Lhs for variables in the same order as getOrigVars()
     * @param sm SvexManager
     * @return expression after substitution and normalization
     */
    private <N extends SvarName> Svex<N> substNorm(Lhs<N>[] lhses, SvexManager<N> sm)
    {
        Svex<N> Z = SvexQuote.Z();
        Svex.TraverseVisitor<PathExt, Svex<N>> visitor = new Svex.TraverseVisitor<PathExt, Svex<N>>()
        {
            @Override
            public Svex<N> visitQuote(Vec4 val)
            {
                return SvexQuote.valueOf(val);
            }

            @Override
            public Svex<N> visitVar(Svar<PathExt> svar)
            {
                int iArg = getOrigVars().indexOf(svar);
                Lhs<N> arg = lhses[iArg];
                Map<Svex<N>, Svex<N>> addDelayCache = new HashMap<>();
                return arg.toSvex(sm).addDelay(svar.getDelay(), sm, addDelayCache);
            }

            @Override
            public Svex<N> visitCall(SvexFunction fun, Svex<PathExt>[] args, Svex<N>[] argVals)
            {
                if (fun == Vec4Rsh.FUNCTION
                    && args[0] instanceof SvexQuote
                    && normSvexRecalc.contains(args[1]))
                {
                    assert argVals[0] == args[0];
                    Vec4 sh = ((SvexQuote<N>)args[0]).val;
                    if (sh.isIndex())
                    {
                        int shVal = ((Vec2)sh).getVal().intValueExact();
                        assert shVal > 0;
                        return argVals[1].rsh(sm, shVal);
                    }
                } else if (fun == Vec4Concat.FUNCTION
                    && args[0] instanceof SvexQuote
                    && normSvexRecalc.contains(args[1]))
                {
                    assert argVals[0] == args[0];
                    Vec4 w = ((SvexQuote<N>)args[0]).val;
                    if (w.isIndex())
                    {
                        int wVal = ((Vec2)w).getVal().intValueExact();
                        return argVals[1].concat(sm, wVal, argVals[2]);
                    }
                } else if (fun == Vec4ZeroExt.FUNCTION
                    && args[0] instanceof SvexQuote
                    && normSvexRecalc.contains(args[1]))
                {
                    assert argVals[0] == args[0];
                    Vec4 w = ((SvexQuote<N>)args[0]).val;
                    if (w.isIndex())
                    {
                        int wVal = ((Vec2)w).getVal().intValueExact();
                        return argVals[1].zerox(sm, wVal);
                    }
                } else if (fun == Vec4SignExt.FUNCTION
                    && args[0] instanceof SvexQuote
                    && normSvexRecalc.contains(args[1]))
                {
                    assert argVals[0] == args[0];
                    Vec4 w = ((SvexQuote<N>)args[0]).val;
                    if (w.isIndex())
                    {
                        int wVal = ((Vec2)w).getVal().intValueExact();
                        return argVals[1].signx(sm, wVal);
                    }
                }
                return sm.newCall(fun, argVals);
            }

            @Override
            public Svex<N>[] newVals(int arity)
            {
                return Svex.newSvexArray(arity);
            }
        };
        return getNormSvex().traverse(visitor);
    }
}
