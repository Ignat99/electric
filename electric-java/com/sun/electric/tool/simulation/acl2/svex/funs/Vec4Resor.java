/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Vec4Resor.java
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

import com.sun.electric.tool.simulation.acl2.svex.SvarName;
import com.sun.electric.tool.simulation.acl2.svex.Svex;
import com.sun.electric.tool.simulation.acl2.svex.SvexCall;
import com.sun.electric.tool.simulation.acl2.svex.SvexFunction;
import com.sun.electric.tool.simulation.acl2.svex.Vec4;
import java.math.BigInteger;
import java.util.Map;

/**
 * Bitwise wired OR resolution of two 4vecs.
 * See<http://www.cs.utexas.edu/users/moore/acl2/manuals/current/manual/?topic=SV____4VEC-RESOR>.
 *
 * @param <N> Type of name of Svex variables
 */
public class Vec4Resor<N extends SvarName> extends SvexCall<N>
{
    public static final Function FUNCTION = new Function();
    public final Svex<N> x;
    public final Svex<N> y;

    private Vec4Resor(Svex<N> x, Svex<N> y)
    {
        super(FUNCTION, x, y);
        this.x = x;
        this.y = y;
    }

    public static class Function extends SvexFunction
    {
        private Function()
        {
            super(FunctionSyms.SV_RESOR, 2, "4vec-resor");
        }

        @Override
        public <N extends SvarName> Vec4Resor<N> build(Svex<N>[] args)
        {
            return new Vec4Resor<>(args[0], args[1]);
        }

        @Override
        public Vec4 apply(Vec4... args)
        {
            Vec4 x = args[0];
            Vec4 y = args[1];
            return Vec4.valueOf(
                x.getUpper().or(y.getUpper()),
                x.getUpper().and(x.getLower())
                    .or(y.getUpper().and(y.getLower())
                        .or(x.getLower().and(y.getLower()))));
        }

        @Override
        protected <N extends SvarName> BigInteger[] svmaskFor(BigInteger mask, Svex<N>[] args, Map<Svex<N>, Vec4> xevalMemoize)
        {
            Svex<N> x = args[0];
            Svex<N> y = args[1];
            Vec4 xv = x.xeval(xevalMemoize);
            Vec4 yv = y.xeval(xevalMemoize);
            BigInteger xOne = xv.getUpper().and(xv.getLower());
            BigInteger yOne = yv.getUpper().and(yv.getLower());
            BigInteger sharedOnes = xOne.and(yOne).and(mask);
            BigInteger xmNonone = mask.andNot(xOne);
            BigInteger ymNonone = mask.andNot(yOne);
            if (sharedOnes.signum() == 0)
            {
                return new BigInteger[]
                {
                    ymNonone, xmNonone
                };
            }
            BigInteger yX = yv.getUpper().andNot(yv.getLower());
            BigInteger ymX = mask.and(yX);
            if (ymX.signum() == 0)
            {
                return new BigInteger[]
                {
                    ymNonone, xmNonone.or(sharedOnes)
                };
            }
            return new BigInteger[]
            {
                ymNonone.or(sharedOnes), xmNonone
            };
        }
    }
}
