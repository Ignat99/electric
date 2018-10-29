/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Vec4ZeroExt.java
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
package com.sun.electric.tool.simulation.acl2.svex.funs;

import com.sun.electric.tool.simulation.acl2.svex.BigIntegerUtil;
import com.sun.electric.tool.simulation.acl2.svex.SvarName;
import com.sun.electric.tool.simulation.acl2.svex.Svex;
import com.sun.electric.tool.simulation.acl2.svex.SvexCall;
import com.sun.electric.tool.simulation.acl2.svex.SvexFunction;
import com.sun.electric.tool.simulation.acl2.svex.SvexManager;
import com.sun.electric.tool.simulation.acl2.svex.SvexQuote;
import com.sun.electric.tool.simulation.acl2.svex.Vec2;
import com.sun.electric.tool.simulation.acl2.svex.Vec4;
import java.math.BigInteger;
import java.util.Map;

/**
 * Like loghead for 4vecs; the width is also a 4vec.
 * See<http://www.cs.utexas.edu/users/moore/acl2/manuals/current/manual/?topic=SV____4VEC-ZERO-EXT>.
 *
 * @param <N> Type of name of Svex variables
 */
public class Vec4ZeroExt<N extends SvarName> extends SvexCall<N>
{
    public static final Function FUNCTION = new Function();
    public final Svex<N> width;
    public final Svex<N> x;

    private Vec4ZeroExt(Svex<N> width, Svex<N> x)
    {
        super(FUNCTION, width, x);
        this.width = width;
        this.x = x;
    }

    @Override
    public MatchExt<N> matchExt()
    {
        if (width instanceof SvexQuote)
        {
            Vec4 wval = ((SvexQuote)width).val;
            if (wval.isVec2() && ((Vec2)wval).getVal().signum() >= 0)
            {
                return new MatchExt<>(((Vec2)wval).getVal().intValueExact(), args[1], false);
            }
        }
        return null;
    }

    @Override
    public Svex<N> lhsPreproc(SvexManager<N> sm)
    {
        Svex<N> newWidth = width.lhsPreproc(sm);
        Svex<N> newX = x.lhsPreproc(sm);
        return sm.newCall(Vec4Concat.FUNCTION, newWidth, newX, SvexQuote.valueOf(0));
    }

    public static class Function extends SvexFunction
    {
        private Function()
        {
            super(FunctionSyms.SV_ZEROX, 2, "4vec-zero-ext");
        }

        @Override
        public <N extends SvarName> Vec4ZeroExt<N> build(Svex<N>[] args)
        {
            return new Vec4ZeroExt<>(args[0], args[1]);
        }

        @Override
        public <N extends SvarName> Svex<N> callStar(SvexManager<N> sm, Svex<N>[] args)
        {
            assert args.length == 2;
            Svex<N> width = args[0];
            if (width instanceof SvexQuote)
            {
                Vec4 wVal = ((SvexQuote)width).val;
                if (wVal.isVec2())
                {
                    BigInteger wV = ((Vec2)wVal).getVal();
                    if (wV.signum() >= 0)
                    {
                        return args[1].zerox(sm, wV.intValueExact());
                    }
                }
            }
            return super.callStar(sm, args);
        }

        @Override
        public Vec4 apply(Vec4... args)
        {
            Vec4 width = args[0];
            Vec4 x = args[1];
            if (width.isVec2())
            {
                int wval = ((Vec2)width).getVal().intValueExact();
                if (wval >= 0)
                {
                    if (wval >= Vec4.BIT_LIMIT)
                    {
                        if (x.getUpper().signum() < 0 || x.getLower().signum() < 0)
                        {
                            throw new IllegalArgumentException("very large integer");

                        }
                    }
                    BigInteger mask = BigIntegerUtil.logheadMask(wval);
                    if (x.isVec2())
                    {
                        BigInteger xv = ((Vec2)x).getVal();
                        return Vec2.valueOf(xv.and(mask));
                    }
                    return Vec4.valueOf(
                        x.getUpper().and(mask),
                        x.getLower().and(mask));
                }
            }
            return Vec4.X;
        }

        @Override
        protected <N extends SvarName> BigInteger[] svmaskFor(BigInteger mask, Svex<N>[] args, Map<Svex<N>, Vec4> xevalMemoize)
        {
            Svex<N> width = args[0];
            BigInteger nMask = v4maskAllOrNone(mask);
            Vec4 widthVal = width.xeval(xevalMemoize);
            if (!widthVal.isVec2())
            {
                return new BigInteger[]
                {
                    nMask, nMask
                };
            }
            int widthV = ((Vec2)widthVal).getVal().intValueExact();
            if (widthV < 0)
            {
                return new BigInteger[]
                {
                    nMask, BigInteger.ZERO
                };
            }
            BigInteger widthMask = BigInteger.ONE.shiftLeft(widthV).subtract(BigInteger.ONE);
            return new BigInteger[]
            {
                nMask, mask.and(widthMask)
            };
        }
    }
}
