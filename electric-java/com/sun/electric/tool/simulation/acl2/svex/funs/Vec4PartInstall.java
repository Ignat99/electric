/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Vec4PartInstall.java
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
public class Vec4PartInstall<N extends SvarName> extends SvexCall<N>
{
    public static final Function FUNCTION = new Function();
    public final Svex<N> lsb;
    public final Svex<N> width;
    public final Svex<N> in;
    public final Svex<N> val;

    private Vec4PartInstall(Svex<N> lsb, Svex<N> width, Svex<N> in, Svex<N> val)
    {
        super(FUNCTION, lsb, width, in, val);
        this.lsb = lsb;
        this.width = width;
        this.in = in;
        this.val = val;
    }

    public static class Function extends SvexFunction
    {
        private Function()
        {
            super(FunctionSyms.SV_PARTINST, 4, "4vec-part-install");
        }

        @Override
        public <N extends SvarName> Vec4PartInstall<N> build(Svex<N>[] args)
        {
            return new Vec4PartInstall<>(args[0], args[1], args[2], args[3]);
        }

        @Override
        public Vec4 apply(Vec4... args)
        {
            Vec4 lsb = args[0];
            Vec4 width = args[1];
            Vec4 in = args[2];
            Vec4 val = args[3];
            if (lsb.isVec2() && width.isVec2())
            {
                int lsbVal = ((Vec2)lsb).getVal().intValueExact();
                int widthVal = ((Vec2)width).getVal().intValueExact();
                if (widthVal >= 0)
                {
                    BigInteger mask = BigIntegerUtil.logheadMask(widthVal).shiftLeft(lsbVal);
                    BigInteger u = val.getUpper().shiftLeft(lsbVal).and(mask);
                    BigInteger l = val.getLower().shiftLeft(lsbVal).and(mask);
                    return Vec4.valueOf(
                        in.getUpper().andNot(mask).or(u),
                        in.getLower().andNot(mask).or(l));
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
                    BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO
                };
            }
            Svex<N> lsb = args[0];
            Svex<N> width = args[1];
            Vec4 lsbVal = lsb.xeval(xevalMemoize);
            Vec4 widthVal = width.xeval(xevalMemoize);
            if (!widthVal.isVec2())
            {
                return new BigInteger[]
                {
                    BigIntegerUtil.MINUS_ONE, BigIntegerUtil.MINUS_ONE, mask, BigIntegerUtil.MINUS_ONE
                };
            }
            int widthV = ((Vec2)widthVal).getVal().intValueExact();
            if (widthV < 0)
            {
                return new BigInteger[]
                {
                    BigInteger.ZERO, BigIntegerUtil.MINUS_ONE, BigInteger.ZERO, BigInteger.ZERO
                };
            }
            if (!lsbVal.isVec2())
            {
                BigInteger valMask = BigIntegerUtil.loghead(widthV, BigIntegerUtil.MINUS_ONE);
                return new BigInteger[]
                {
                    BigIntegerUtil.MINUS_ONE, BigIntegerUtil.MINUS_ONE, mask, valMask
                };
            }
            int lsbV = ((Vec2)lsbVal).getVal().intValueExact();
            if (lsbV >= 0)
            {
                BigInteger inMask = BigIntegerUtil.logapp(lsbV, mask,
                    BigIntegerUtil.logapp(widthV, BigInteger.ZERO,
                        mask.shiftRight(Math.addExact(widthV, lsbV))));
                BigInteger valMask = BigIntegerUtil.loghead(widthV, mask.shiftRight(lsbV));
                return new BigInteger[]
                {
                    BigIntegerUtil.MINUS_ONE, BigIntegerUtil.MINUS_ONE, inMask, valMask
                };
            }
            if (widthV > Math.negateExact(lsbV))
            {
                BigInteger inMask = BigIntegerUtil.logapp(Math.addExact(widthV, lsbV), BigInteger.ZERO,
                    mask.shiftRight(Math.addExact(widthV, lsbV)));
                BigInteger valMask = BigIntegerUtil.loghead(Math.addExact(widthV, lsbV), mask).shiftRight(lsbV);
                return new BigInteger[]
                {
                    BigIntegerUtil.MINUS_ONE, BigIntegerUtil.MINUS_ONE, inMask, valMask
                };
            }
            return new BigInteger[]
            {
                BigIntegerUtil.MINUS_ONE, BigIntegerUtil.MINUS_ONE, mask, BigInteger.ZERO
            };
        }
    }
}
