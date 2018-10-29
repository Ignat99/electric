/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Vec4RevBlocks.java
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Similar to a streaming concatenation operation in SystemVerilog.
 * See<http://www.cs.utexas.edu/users/moore/acl2/manuals/current/manual/?topic=SV____4VEC-REV-BLOCKS>.
 *
 * @param <N> Type of name of Svex variables
 */
public class Vec4RevBlocks<N extends SvarName> extends SvexCall<N>
{
    public static final Function FUNCTION = new Function();
    public final Svex<N> width;
    public final Svex<N> bsz;
    public final Svex<N> x;

    private Vec4RevBlocks(Svex<N> width, Svex<N> bsz, Svex<N> x)
    {
        super(FUNCTION, width, bsz, x);
        this.width = width;
        this.bsz = bsz;
        this.x = x;
    }

    @Override
    public Svex<N> lhsPreproc(SvexManager<N> sm)
    {
        if (width instanceof SvexQuote && bsz instanceof SvexQuote)
        {
            Vec4 wval = ((SvexQuote)width).val;
            Vec4 bval = ((SvexQuote)bsz).val;
            if (wval.isVec2() && bval.isVec2())
            {
                int wv = ((Vec2)wval).getVal().intValueExact();
                int bv = ((Vec2)wval).getVal().intValueExact();
                if (wv >= 0 && bv > 0)
                {
                    Svex<N> zero = SvexQuote.valueOf(0);
                    List<Svex<N>> stackX = new ArrayList<>();
                    Svex<N> x = this.x;
                    int nbits = wv;
                    while (nbits >= bv)
                    {
                        stackX.add(x);
                        x = sm.newCall(Vec4Rsh.FUNCTION, bsz, x);
                        nbits -= bv;
                    }
                    Svex<N> rest = sm.newCall(Vec4Concat.FUNCTION, SvexQuote.valueOf(nbits), x, zero);
                    while (!stackX.isEmpty())
                    {
                        x = stackX.remove(stackX.size() - 1);
                        nbits += bv;
                        x = sm.newCall(Vec4Concat.FUNCTION, bsz, x, zero);
                        rest = sm.newCall(Vec4Concat.FUNCTION, SvexQuote.valueOf(nbits), rest, x);
                    }
                    return rest;
                }
            }
        }
        return super.lhsPreproc(sm);
    }

    public static class Function extends SvexFunction
    {
        private Function()
        {
            super(FunctionSyms.SV_BLKREV, 3, "4vec-rev-blocks");
        }

        @Override
        public <N extends SvarName> Vec4RevBlocks<N> build(Svex<N>[] args)
        {
            return new Vec4RevBlocks<>(args[0], args[1], args[2]);
        }

        @Override
        public Vec4 apply(Vec4... args)
        {
            Vec4 nbits = args[0];
            Vec4 blocksz = args[1];
            Vec4 x = args[1];
            if (nbits.isVec2() && blocksz.isVec2())
            {
                int nbitsVal = ((Vec2)nbits).getVal().intValueExact();
                int blockszVal = ((Vec2)nbits).getVal().intValueExact();
                if (nbitsVal >= 0 && blockszVal > 0)
                {
                    return Vec4.valueOf(
                        revBlocks(nbitsVal, blockszVal, x.getUpper()),
                        revBlocks(nbitsVal, blockszVal, x.getLower()));
                }
            }
            return Vec4.X;
        }

        private BigInteger revBlocks(int nbits, int blocksz, BigInteger x)
        {
            BigInteger mask = BigIntegerUtil.logheadMask(blocksz);
            BigInteger result = BigInteger.ZERO;
            while (nbits >= blocksz)
            {
                result = result.shiftLeft(blocksz).or(x.and(mask));
                x = x.shiftRight(blocksz);
                nbits -= blocksz;
            }
            if (nbits > 0)
            {
                mask = BigInteger.ONE.shiftLeft(nbits).subtract(BigInteger.ONE);
                result = result.shiftLeft(nbits).or(x.and(mask));
            }
            return result;
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
            Svex<N> n = args[0];
            Svex<N> b = args[1];
            Vec4 nVal = n.xeval(xevalMemoize);
            Vec4 bVal = b.xeval(xevalMemoize);
            if (nVal.isVec2() && bVal.isVec2())
            {
                int nv = ((Vec2)nVal).getVal().intValueExact();
                int bv = ((Vec2)bVal).getVal().intValueExact();
                if (nv >= 0 && bv > 0)
                {
                    return new BigInteger[]
                    {
                        BigIntegerUtil.MINUS_ONE, BigIntegerUtil.MINUS_ONE, unrevBlocks(arity, bv, mask)
                    };
                }
            }
            return new BigInteger[]
            {
                BigIntegerUtil.MINUS_ONE, BigIntegerUtil.MINUS_ONE, BigIntegerUtil.MINUS_ONE
            };
        }

        private BigInteger unrevBlocks(int nbits, int blcksz, BigInteger x)
        {
            BigInteger result = BigIntegerUtil.loghead(nbits % blcksz, x);
            for (int n = nbits / blcksz; n >= 0; n--)
            {
                result = BigIntegerUtil.logapp(blcksz, BigIntegerUtil.loghead(blcksz, x), result);
                x = x.shiftRight(blcksz);
            }
            return result;
        }
    }
}
