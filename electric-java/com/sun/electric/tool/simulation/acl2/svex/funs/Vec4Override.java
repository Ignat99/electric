/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Vec4Override.java
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
 * Resolution for when one signal is stronger than the other.
 * See<http://www.cs.utexas.edu/users/moore/acl2/manuals/current/manual/?topic=SV____4VEC-OVERRIDE>.
 *
 * @param <N> Type of name of Svex variables
 */
public class Vec4Override<N extends SvarName> extends SvexCall<N>
{
    public static final Function FUNCTION = new Function();
    public final Svex<N> x;
    public final Svex<N> y;

    private Vec4Override(Svex<N> x, Svex<N> y)
    {
        super(FUNCTION, x, y);
        this.x = x;
        this.y = y;
    }

    public static class Function extends SvexFunction
    {
        private Function()
        {
            super(FunctionSyms.SV_OVERRIDE, 2, "4vec-override");
        }

        @Override
        public <N extends SvarName> Vec4Override<N> build(Svex<N>[] args)
        {
            return new Vec4Override<>(args[0], args[1]);
        }

        @Override
        public Vec4 apply(Vec4... args)
        {
            Vec4 strong = args[0];
            Vec4 weak = args[1];
            return Vec4.valueOf(
                strong.getLower().and(weak.getUpper()).or(strong.getUpper()),
                strong.getUpper().or(weak.getLower()).and(strong.getLower()));
        }

        @Override
        protected <N extends SvarName> BigInteger[] svmaskFor(BigInteger mask, Svex<N>[] args, Map<Svex<N>, Vec4> xevalMemoize)
        {
            Svex<N> strong = args[0];
            Vec4 sVal = strong.xeval(xevalMemoize);
            BigInteger strongNonbool = sVal.getUpper().xor(sVal.getLower());
            BigInteger weakMask = mask.and(strongNonbool);
            return new BigInteger[]
            {
                mask, weakMask
            };
        }
    }
}
