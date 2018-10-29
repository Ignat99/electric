/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Wire.java
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
package com.sun.electric.tool.simulation.acl2.mods;

import com.sun.electric.tool.simulation.acl2.svex.BigIntegerUtil;
import static com.sun.electric.util.acl2.ACL2.*;
import com.sun.electric.util.acl2.ACL2Backed;
import com.sun.electric.util.acl2.ACL2Object;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

/**
 * Wire info as stored in an svex module.
 * See <http://www.cs.utexas.edu/users/moore/acl2/manuals/current/manual/?topic=SV____WIRE>.
 */
public class Wire
{
    public final Name name;
    public final int width;
    public final int low_idx;
    public final int delay;
    public final boolean revp;
    public final Wiretype wiretype;

    public Wire(Name name, int width)
    {
        this(name, width, 0, 0, false, Wiretype.WIRE);
    }

    public Wire(Name name, int width, int low_idx, int delay, boolean revp, Wiretype wiretype)
    {
        if (name == null)
        {
            throw new NullPointerException();
        }
        if (width <= 0)
        {
            throw new IllegalArgumentException();
        }
        if (delay < 0)
        {
            throw new IllegalArgumentException();
        }
        if (wiretype == null)
        {
            throw new NullPointerException();
        }
        this.name = name;
        this.width = width;
        this.low_idx = low_idx;
        this.delay = delay;
        this.revp = revp;
        this.wiretype = wiretype;
    }

    public Wire(ACL2Object impl)
    {
        ACL2Object cons0 = car(impl);
        name = Name.fromACL2(car(cons0));
        ACL2Object cons01 = cdr(cons0);
        width = car(cons01).intValueExact();
        Util.check(width >= 1);
        low_idx = cdr(cons01).intValueExact();
        if (consp(cdr(impl)).bool())
        {
            ACL2Object cons1 = cdr(impl);
            Util.check(!NIL.equals(car(cons1)) || !NIL.equals(cdr(cons1)));
            if (symbolp(car(cons1)).bool())
            {
                Util.checkNil(car(cons1));
                delay = 0;
            } else
            {
                delay = car(cons1).intValueExact();
            }
            if (consp(cdr(cons1)).bool())
            {
                ACL2Object cons11 = cdr(cons1);
                if (NIL.equals(car(cons11)))
                {
                    Util.checkNil(car(cons11));
                    revp = false;
                } else
                {
                    revp = true;
                }
                wiretype = Wiretype.valueOf(cdr(cons11));
            } else
            {
                Util.checkNil(cdr(cons1));
                revp = false;
                wiretype = Wiretype.valueOf(NIL);
            }
        } else
        {
            Util.checkNil(cdr(impl));
            delay = 0;
            revp = false;
            wiretype = Wiretype.valueOf(NIL);
        }
    }

    public ACL2Object getACL2Object()
    {
        Map<ACL2Backed, ACL2Object> backedCache = new HashMap<>();
        ACL2Object rep00 = name.getACL2Object(backedCache);
        ACL2Object rep010 = ACL2Object.valueOf(width);
        ACL2Object rep110 = ACL2Object.valueOf(low_idx);
        ACL2Object rep10 = cons(rep010, rep110);
        ACL2Object rep0 = cons(rep00, rep10);
        ACL2Object rep01 = delay > 0 ? ACL2Object.valueOf(delay) : NIL;
        ACL2Object rep011 = ACL2Object.valueOf(revp);
        ACL2Object rep111 = wiretype.getACL2Object();
        ACL2Object rep11 = NIL.equals(rep011) && NIL.equals(rep111) ? NIL : cons(rep011, rep111);
        ACL2Object rep1 = NIL.equals(rep01) && NIL.equals(rep11) ? NIL : cons(rep01, rep11);
        ACL2Object rep = cons(rep0, rep1);
        return rep;
    }

    public int getFirstIndex()
    {
        if (revp)
        {
            throw new UnsupportedOperationException();
        }
        return low_idx + width - 1;
    }

    public int getSecondIndex()
    {
        if (revp)
        {
            throw new UnsupportedOperationException();
        }
        return low_idx;
    }

    public String toString(int width, int rsh)
    {
        if (this.width == 1 && low_idx == 0 && width == 1 && rsh == 0)
        {
            return name.toString();
        }
        if (revp)
        {
            throw new UnsupportedOperationException();
        }
        return name
            + "[" + (width == 1 ? "" : (low_idx + rsh + width - 1) + ":")
            + (low_idx + rsh) + "]";
    }

    public String toString(BigInteger mask)
    {
        if (revp)
        {
            throw new UnsupportedOperationException();
        }
        String s = name.toString();
        if (mask == null)
        {
            mask = BigInteger.ZERO;
        }
        BigInteger maskH = mask.shiftRight(width);
        mask = BigIntegerUtil.loghead(width, mask);
        String indices = "";
        int ind = 0;
        boolean first = true;
        for (;;)
        {
            while (mask.signum() != 0)
            {
                int n = mask.getLowestSetBit();
                assert n >= 0;
                ind += n;
                mask = mask.shiftRight(n);
                int indL = ind;
                if (BigIntegerUtil.MINUS_ONE.equals(mask))
                {
                    if (!first)
                    {
                        indices = "," + indices;
                    }
                    indices = ":" + Integer.toString(low_idx + indL) + indices;
                    break;
                }
                n = mask.not().getLowestSetBit();
                assert n >= 0;
                ind += n;
                mask = mask.shiftRight(n);
                if (indL == 0 && ind == width && width == 1 && maskH.signum() == 0)
                {
                    Util.check(mask.signum() == 0);
                    Util.check(indices.isEmpty());
                    return s;
                }
                Util.check(!revp);
                if (first)
                {
                    first = false;
                } else
                {
                    indices = "," + indices;
                }
                indices = indL == ind - 1 ? Integer.toString(low_idx + indL) + indices
                    : Integer.toString(low_idx + ind - 1) + ":" + Integer.toString(low_idx + indL) + indices;
            }
            if (maskH.signum() == 0)
                break;
            indices = "/*?*/" + indices;
            mask = maskH;
            maskH = BigInteger.ZERO;
        }
        return s + "[" + indices + "]";
    }

    public String toLispString(int width, int rsh)
    {
        return toString(BigIntegerUtil.logheadMask(width).shiftLeft(rsh));
    }

    @Override
    public String toString()
    {
        Util.checkNil(wiretype.getACL2Object());
        Util.check(delay == 0);
        String s = name.toString();
        if (width != 1)
        {
            Util.check(!revp);
            s += "[" + (low_idx + width - 1) + ":" + low_idx + "]";
        } else if (low_idx != 0)
        {
            s += "[" + low_idx + "]";
        }
        return s;
    }

    @Override
    public boolean equals(Object o)
    {
        if (o instanceof Wire)
        {
            Wire that = (Wire)o;
            return this.getACL2Object().equals(that.getACL2Object());
        }
        return false;
    }

    @Override
    public int hashCode()
    {
        int hash = 3;
        hash = 59 * hash + name.hashCode();
        return hash;
    }

}
