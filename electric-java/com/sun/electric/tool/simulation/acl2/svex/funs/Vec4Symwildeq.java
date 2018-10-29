/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Vec4Symwildeq.java
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
 * Symmetric wildcard equality: true if for every pair of corresponding bits of a and b,
 * either they are equal or the bit from either a or b is Z.
 * See<http://www.cs.utexas.edu/users/moore/acl2/manuals/current/manual/?topic=SV____4VEC-SYMWILDEQ>.
 *
 * @param <N> Type of name of Svex variables
 */
public class Vec4Symwildeq<N extends SvarName> extends SvexCall<N>
{
    public static final Function FUNCTION = new Function();
    public final Svex<N> x;
    public final Svex<N> y;

    private Vec4Symwildeq(Svex<N> x, Svex<N> y)
    {
        super(FUNCTION, x, y);
        this.x = x;
        this.y = y;
    }

    public static class Function extends SvexFunction
    {
        private Function()
        {
            super(FunctionSyms.SV_EQ_EQ_QUEST_QUEST, 2, "4vec-symwildeq");
        }

        @Override
        public <N extends SvarName> Vec4Symwildeq<N> build(Svex<N>[] args)
        {
            return new Vec4Symwildeq<>(args[0], args[1]);
        }

        @Override
        public Vec4 apply(Vec4... args)
        {
            Vec4 a = args[0];
            Vec4 b = args[1];
            BigInteger zMask = b.getLower().andNot(b.getUpper())
                .or(a.getLower().andNot(a.getUpper()));
            return eq(a, b, zMask);
        }

        @Override
        protected <N extends SvarName> BigInteger[] svmaskFor(BigInteger mask, Svex<N>[] args, Map<Svex<N>, Vec4> xevalMemoize)
        {
            Svex<N> a = args[0];
            Svex<N> b = args[1];
            Vec4 aVal = a.xeval(xevalMemoize);
            Vec4 bVal = b.xeval(xevalMemoize);
            BigInteger aIsZ = aVal.getLower().andNot(aVal.getUpper());
            BigInteger bIsZ = bVal.getLower().andNot(bVal.getUpper());
            BigInteger bothAreZ = aIsZ.and(bIsZ);
            if (bothAreZ.signum() == 0)
            {
                return new BigInteger[]
                {
                    bIsZ.not(), aIsZ.not()
                };
            }
            BigInteger bX = bVal.getUpper().andNot(bVal.getLower());
            if (bX.signum() == 0)
            {
                return new BigInteger[]
                {
                    bIsZ.not(), aIsZ.andNot(bothAreZ).not()
                };
            } else
            {
                return new BigInteger[]
                {
                    bIsZ.andNot(bothAreZ).not(), aIsZ.not()
                };
            }
        }
    }
}
