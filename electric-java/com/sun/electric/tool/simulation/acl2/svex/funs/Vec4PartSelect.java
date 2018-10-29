/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Vec4PartSelect.java
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
 * Part select operation: select width bits of in starting at lsb.
 * See<http://www.cs.utexas.edu/users/moore/acl2/manuals/current/manual/?topic=SV____4VEC-PART-SELECT>.
 *
 * @param <N> Type of name of Svex variables
 */
public class Vec4PartSelect<N extends SvarName> extends SvexCall<N>
{
    public static final Function FUNCTION = new Function();
    public final Svex<N> lsb;
    public final Svex<N> width;
    public final Svex<N> in;

    private Vec4PartSelect(Svex<N> lsb, Svex<N> width, Svex<N> in)
    {
        super(FUNCTION, lsb, width, in);
        this.lsb = lsb;
        this.width = width;
        this.in = in;
    }

    @Override
    public Svex<N> lhsPreproc(SvexManager<N> sm)
    {
        if (lsb instanceof SvexQuote)
        {
            Vec4 lval = ((SvexQuote)lsb).val;
            if (lval.isVec2())
            {
                int lv = ((Vec2)lval).getVal().intValueExact();
                if (lv >= 0)
                {
                    Svex<N> svexRsh = sm.newCall(Vec4Rsh.FUNCTION, lsb, in.lhsPreproc(sm));
                    return sm.newCall(Vec4Concat.FUNCTION, width, svexRsh, SvexQuote.valueOf(0));
                }
            }
        }
        return super.lhsPreproc(sm);
    }

    public static class Function extends SvexFunction
    {
        private Function()
        {
            super(FunctionSyms.SV_PARTSEL, 3, "4vec-part-select");
        }

        @Override
        public <N extends SvarName> Vec4PartSelect<N> build(Svex<N>[] args)
        {
            return new Vec4PartSelect<>(args[0], args[1], args[2]);
        }

        @Override
        public Vec4 apply(Vec4... args)
        {
            Vec4 lsb = args[0];
            Vec4 width = args[1];
            Vec4 in = args[2];
            if (lsb.isVec2() && width.isVec2())
            {
                int lsbVal = ((Vec2)lsb).getVal().intValueExact();
                int widthVal = ((Vec2)width).getVal().intValueExact();
                if (widthVal >= 0)
                {
                    BigInteger u = in.getUpper().shiftRight(lsbVal);
                    BigInteger l = in.getLower().shiftRight(lsbVal);
                    if (lsbVal < 0)
                    {
                        BigInteger lsbMask = BigInteger.ONE.shiftRight(lsbVal).subtract(BigInteger.ONE);
                        u = u.or(lsbMask);
                        l = l.or(lsbMask);
                    }
                    BigInteger mask = BigIntegerUtil.logheadMask(widthVal);
                    return Vec4.valueOf(u.and(mask), l.and(mask));
                }
            }
            return Vec4.X;
        }

        @Override
        protected <N extends SvarName> BigInteger[] svmaskFor(BigInteger mask, Svex<N>[] args, Map<Svex<N>, Vec4> xevalMemoize)
        {
            if (mask.signum() == 0)
            {
                return new BigInteger[]
                {
                    BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO
                };
            }
            Svex<N> lsb = args[0];
            Svex<N> width = args[1];
            Vec4 lsbVal = lsb.xeval(xevalMemoize);
            Vec4 widthVal = width.xeval(xevalMemoize);
            if (!widthVal.isVec2())
            {
                if (lsbVal.isVec2())
                {
                    int lsbV = ((Vec2)lsbVal).getVal().intValueExact();
                    return new BigInteger[]
                    {
                        BigIntegerUtil.MINUS_ONE, BigIntegerUtil.MINUS_ONE, mask.shiftLeft(lsbV)
                    };
                } else
                {
                    return new BigInteger[]
                    {
                        BigIntegerUtil.MINUS_ONE, BigIntegerUtil.MINUS_ONE, BigIntegerUtil.MINUS_ONE
                    };
                }
            }
            int widthV = ((Vec2)widthVal).getVal().intValueExact();

            if (widthV < 0)
            {
                return new BigInteger[]
                {
                    BigInteger.ZERO, BigIntegerUtil.MINUS_ONE, BigInteger.ZERO
                };
            }

            if (!lsbVal.isVec2())
            {
                if (BigIntegerUtil.loghead(widthV, mask).signum() == 0)
                {
                    return new BigInteger[]
                    {
                        BigIntegerUtil.MINUS_ONE, BigIntegerUtil.MINUS_ONE, BigInteger.ZERO
                    };
                } else
                {
                    return new BigInteger[]
                    {
                        BigIntegerUtil.MINUS_ONE, BigIntegerUtil.MINUS_ONE, BigIntegerUtil.MINUS_ONE
                    };
                }
            }
            int lsbV = ((Vec2)lsbVal).getVal().intValueExact();
            BigInteger xMask = BigIntegerUtil.loghead(widthV, mask).shiftLeft(lsbV);
            return new BigInteger[]
            {
                BigIntegerUtil.MINUS_ONE, BigIntegerUtil.MINUS_ONE, xMask
            };
        }
    }
}
