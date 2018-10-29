/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Vec4Rsh.java
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

import com.sun.electric.tool.simulation.acl2.mods.Lhs;
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
 * Right “arithmetic” shift of 4vecs.
 * See<http://www.cs.utexas.edu/users/moore/acl2/manuals/current/manual/?topic=SV____4VEC-RSH>.
 * @param <N> Type of name of Svex variables
 */
public class Vec4Rsh<N extends SvarName> extends SvexCall<N>
{
    public static final Function FUNCTION = new Function();
    public final Svex<N> shift;
    public final Svex<N> x;

    private Vec4Rsh(Svex<N> shift, Svex<N> x)
    {
        super(FUNCTION, shift, x);
        this.shift = shift;
        this.x = x;
    }

    @Override
    public boolean isLhsUnbounded()
    {
        return shift instanceof SvexQuote && ((SvexQuote<N>)shift).val.isIndex()
            && x.isLhsUnbounded();
    }

    @Override
    public boolean isLhs()
    {
        return shift instanceof SvexQuote && ((SvexQuote<N>)shift).val.isIndex() && x.isLhs();
    }

    @Override
    public Lhs<N> lhsBound(int w)
    {
        Vec2 shVal = (Vec2)((SvexQuote<N>)shift).val;
        int shv = shVal.getVal().intValueExact();
        return x.lhsBound(w + shv).rsh(shv);
    }

    @Override
    public Lhs<N> toLhs()
    {
        Vec2 shVal = (Vec2)((SvexQuote<N>)shift).val;
        int shv = shVal.getVal().intValueExact();
        return x.toLhs().rsh(shv);
    }

    @Override
    public MatchRsh<N> matchRsh()
    {
        if (shift instanceof SvexQuote)
        {
            Vec4 sval = ((SvexQuote)shift).val;
            if (sval.isVec2() && ((Vec2)sval).getVal().signum() >= 0)
            {
                return new MatchRsh<>(((Vec2)sval).getVal().intValueExact(), args[1]);
            }
        }
        return super.matchRsh();
    }

    @Override
    public Svex<N> lhsrewriteAux(SvexManager<N> sm, int shift, int w)
    {
        if (this.shift instanceof SvexQuote)
        {
            Vec4 sval = ((SvexQuote)this.shift).val;
            if (sval.isVec2())
            {
                int sv = ((Vec2)sval).getVal().intValueExact();
                if (sv >= 0) {
                    return x.lhsrewriteAux(sm, shift + sv, w).rsh(sm, sv);
                }
            }
        }
        return super.lhsrewriteAux(sm, shift, w);
    }

    @Override
    public Svex<N> lhsPreproc(SvexManager<N> sm)
    {
        Svex<N> newShift = shift.lhsPreproc(sm);
        Svex<N> newX = x.lhsPreproc(sm);
        return sm.newCall(Vec4Rsh.FUNCTION, newShift, newX);
    }

    public static class Function extends SvexFunction
    {
        private Function()
        {
            super(FunctionSyms.SV_RSH, 2, "4vec-rsh");
        }

        @Override
        public <N extends SvarName> Vec4Rsh<N> build(Svex<N>[] args)
        {
            return new Vec4Rsh<>(args[0], args[1]);
        }

        @Override
        public <N extends SvarName> Svex<N> callStar(SvexManager<N> sm, Svex<N>[] args)
        {
            assert args.length == 2;
            Svex<N> sh = args[0];
            if (sh instanceof SvexQuote)
            {
                Vec4 shVal = ((SvexQuote)sh).val;
                if (shVal.isVec2())
                {
                    BigInteger shV = ((Vec2)shVal).getVal();
                    if (shV.signum() >= 0)
                    {
                        return args[1].rsh(sm, shV.intValueExact());
                    }
                }
            }
            return super.callStar(sm, args);
        }

        @Override
        public Vec4 apply(Vec4... args)
        {
            Vec4 shift = args[0];
            Vec4 x = args[1];
            if (shift.isVec2())
            {
                int shiftv = ((Vec2)shift).getVal().intValueExact();
                return shiftCore(Math.negateExact(shiftv), x);
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
                BigIntegerUtil.MINUS_ONE, mask.shiftLeft(shiftV)
            };
        }
    }
}
