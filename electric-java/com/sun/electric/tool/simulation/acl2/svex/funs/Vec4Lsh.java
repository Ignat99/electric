/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Vec4Lsh.java
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
 * Left “arithmetic” shift of 4vecs.
 * See<http://www.cs.utexas.edu/users/moore/acl2/manuals/current/manual/?topic=SV____4VEC-LSH>.
 *
 * @param <N> Type of name of Svex variables
 */
public class Vec4Lsh<N extends SvarName> extends SvexCall<N>
{
    public static final Function FUNCTION = new Function();
    public final Svex<N> shift;
    public final Svex<N> x;

    private Vec4Lsh(Svex<N> shift, Svex<N> x)
    {
        super(FUNCTION, shift, x);
        this.shift = shift;
        this.x = x;
    }

    public static class Function extends SvexFunction
    {
        private Function()
        {
            super(FunctionSyms.SV_LSH, 2, "4vec-lsh");
        }

        @Override
        public <N extends SvarName> Vec4Lsh<N> build(Svex<N>[] args)
        {
            return new Vec4Lsh<>(args[0], args[1]);
        }

        @Override
        public Vec4 apply(Vec4... args)
        {
            Vec4 shift = args[0];
            Vec4 x = args[1];
            if (shift.isVec2())
            {
                int shiftv = ((Vec2)shift).getVal().intValueExact();
                return shiftCore(shiftv, x);
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
                    BigInteger.ZERO, BigInteger.ZERO
                };
            }
            Svex<N> shift = args[0];
            Vec4 shiftVal = shift.xeval(xevalMemoize);
            if (!shiftVal.isVec2())
            {
                return new BigInteger[]
                {
                    BigIntegerUtil.MINUS_ONE, BigIntegerUtil.MINUS_ONE
                };
            }
            int shiftV = ((Vec2)shiftVal).getVal().intValueExact();
            return new BigInteger[]
            {
                BigIntegerUtil.MINUS_ONE, mask.shiftRight(shiftV)
            };
        }
    }
}
