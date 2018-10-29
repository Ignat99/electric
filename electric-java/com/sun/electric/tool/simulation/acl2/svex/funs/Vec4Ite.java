/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Vec4Ite.java
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
 * Atomic if-then-else of 4vecs; doesn’t unfloat then/else values.
 * See<http://www.cs.utexas.edu/users/moore/acl2/manuals/current/manual/?topic=SV____4VEC-_F3>.
 *
 * @param <N> Type of name of Svex variables
 */
public class Vec4Ite<N extends SvarName> extends SvexCall<N>
{
    public static final Function FUNCTION = new Function();
    public final Svex<N> test;
    public final Svex<N> then;
    public final Svex<N> els;

    private Vec4Ite(Svex<N> test, Svex<N> then, Svex<N> els)
    {
        super(FUNCTION, test, then, els);
        this.test = test;
        this.then = then;
        this.els = els;
    }

    public static class Function extends SvexFunction
    {
        private Function()
        {
            super(FunctionSyms.SV_QUEST, 3, "4vec-?");
        }

        @Override
        public <N extends SvarName> Vec4Ite<N> build(Svex<N>[] args)
        {
            return new Vec4Ite<>(args[0], args[1], args[2]);
        }

        @Override
        public Vec4 apply(Vec4... args)
        {
            return apply3(args[0].fix3(), args[1], args[2]);
        }

        private Vec4 apply3(Vec4 test, Vec4 th, Vec4 el)
        {
            if (test.isVec2())
            {
                BigInteger testv = ((Vec2)test).getVal();
                return testv.signum() != 0 ? th : el;
            }
            if (test.getUpper().signum() == 0)
            {
                return el;
            }
            if (test.getLower().signum() != 0)
            {
                return th;
            }
            return Vec4.valueOf(
                th.getUpper().or(el.getUpper()).or(th.getLower()).or(el.getLower()),
                th.getUpper().and(el.getUpper()).and(th.getLower()).and(el.getLower()));
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
            Svex<N> test = args[0];
            Svex<N> th = args[1];
            Svex<N> el = args[2];
            Vec4 testVal = test.xeval(xevalMemoize);
            BigInteger testOnes = testVal.getUpper().and(testVal.getLower());
            if (testOnes.signum() != 0)
            {
                return new BigInteger[]
                {
                    testOnes, mask, BigInteger.ZERO
                };
            }
            if (testVal.getUpper().signum() == 0 && testVal.getLower().signum() == 0)
            {
                return new BigInteger[]
                {
                    BigIntegerUtil.MINUS_ONE, BigInteger.ZERO, mask
                };
            }
            if (branchesSameUnderMask(mask, th, el, xevalMemoize))
            {
                return new BigInteger[]
                {
                    BigInteger.ZERO, mask, mask
                };
            } else
            {
                return new BigInteger[]
                {
                    BigIntegerUtil.MINUS_ONE, mask, mask
                };
            }
        }
    }
}
