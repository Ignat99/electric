/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Vec4WildeqSafe.java
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
import com.sun.electric.tool.simulation.acl2.svex.Vec4;
import java.math.BigInteger;
import java.util.Map;

/**
 * True if for every pair of corresponding bits of a and b, either they are equal or the bit from b is Z.
 * See<http://www.cs.utexas.edu/users/moore/acl2/manuals/current/manual/?topic=SV____4VEC-WILDEQ-SAFE>.
 *
 * @param <N> Type of name of Svex variables
 */
public class Vec4WildeqSafe<N extends SvarName> extends SvexCall<N>
{
    public static final Function FUNCTION = new Function();
    public final Svex<N> x;
    public final Svex<N> y;

    private Vec4WildeqSafe(Svex<N> x, Svex<N> y)
    {
        super(FUNCTION, x, y);
        this.x = x;
        this.y = y;
    }

    public static class Function extends SvexFunction
    {
        private Function()
        {
            super(FunctionSyms.SV_SAFER_EQ_EQ_QUEST, 2, "4vec-wildeq-safe");
        }

        @Override
        public <N extends SvarName> Vec4WildeqSafe<N> build(Svex<N>[] args)
        {
            return new Vec4WildeqSafe<>(args[0], args[1]);
        }

        @Override
        public Vec4 apply(Vec4... args)
        {
            Vec4 a = args[0];
            Vec4 b = args[1];
            BigInteger zMask = b.getLower().andNot(b.getUpper());
            return eq(a, b, zMask);
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
            Svex<N> b = args[1];
            Vec4 bVal = b.xeval(xevalMemoize);
            BigInteger bNonZ = bVal.getLower().andNot(bVal.getUpper()).not();
            return new BigInteger[]
            {

                bNonZ, BigIntegerUtil.MINUS_ONE
            };
        }
    }
}
