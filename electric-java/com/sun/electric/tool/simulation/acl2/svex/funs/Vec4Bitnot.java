/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Vec4Bitnot.java
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
import com.sun.electric.tool.simulation.acl2.svex.Vec2;
import com.sun.electric.tool.simulation.acl2.svex.Vec4;
import java.math.BigInteger;
import java.util.Map;

/**
 * Bitwise logical NOT of a 4vec.
 * See<http://www.cs.utexas.edu/users/moore/acl2/manuals/current/manual/?topic=SV____4VEC-BITNOT>.
 *
 * @param <N> Type of name of Svex variables
 */
public class Vec4Bitnot<N extends SvarName> extends SvexCall<N>
{
    public static final Function FUNCTION = new Function();
    public final Svex<N> x;

    private Vec4Bitnot(Svex<N> x)
    {
        super(FUNCTION, x);
        this.x = x;
    }

    public static class Function extends SvexFunction
    {
        private Function()
        {
            super(FunctionSyms.SV_BITNOT, 1, "4vec-bitnot");
        }

        @Override
        public <N extends SvarName> Vec4Bitnot<N> build(Svex<N>[] args)
        {
            return new Vec4Bitnot<>(args[0]);
        }

        @Override
        public Vec4 apply(Vec4... args)
        {
            return apply3(args[0].fix3());
        }

        private Vec4 apply3(Vec4 x)
        {
            if (x.isVec2())
            {
                BigInteger xv = ((Vec2)x).getVal();
                return Vec2.valueOf(xv.not());
            }
            return Vec4.valueOf(x.getLower().not(), x.getUpper().not());
        }

        @Override
        protected <N extends SvarName> BigInteger[] svmaskFor(BigInteger mask, Svex<N>[] args, Map<Svex<N>, Vec4> xevalMemoize)
        {
            return new BigInteger[]
            {
                mask
            };
        }
    }
}
