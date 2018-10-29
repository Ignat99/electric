/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: SVar.java
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
package com.sun.electric.tool.simulation.acl2.svex;

import static com.sun.electric.util.acl2.ACL2.*;
import com.sun.electric.util.acl2.ACL2Backed;
import com.sun.electric.util.acl2.ACL2Object;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A single variable in a symbolic vector expression.
 * See <http://www.cs.utexas.edu/users/moore/acl2/manuals/current/manual/?topic=SV____SVAR>.
 *
 * @param <N> Type of name of Svex variables
 */
public interface Svar<N extends SvarName> extends ACL2Backed
{
    public static ACL2Object KEYWORD_VAR = ACL2Object.valueOf("KEYWORD", "VAR");

    public default ACL2Object getACL2Name()
    {
        return getName().getACL2Object();
    }

    public abstract N getName();

    public abstract int getDelay();

    public abstract boolean isNonblocking();

    public abstract String toString(BigInteger mask);

    public default String toLispString(int width, int rsh)
    {
        return toString(BigIntegerUtil.logheadMask(width).shiftLeft(rsh));
    }

    public default String toString(SvarNameTexter<N> texter, int width, int rsh)
    {
        String s = texter.toString(getName(), width, rsh);
        if (isNonblocking())
        {
            s = "#?" + getDelay() + " " + s;
        } else if (getDelay() != 0)
        {
            s = "#" + getDelay() + " " + s;
        }
        return s;
    }

    @Override
    public default ACL2Object getACL2Object(Map<ACL2Backed, ACL2Object> backedCache)
    {
        ACL2Object result = backedCache.get(this);
        if (result == null)
        {
            SvarName name = getName();
            ACL2Object nameImpl = name.getACL2Object();
            int delayImpl = getDelay();
            if (isNonblocking())
            {
                delayImpl = ~delayImpl;
            }
            result = name.isSimpleSvarName() && delayImpl == 0
                ? nameImpl
                : hons(KEYWORD_VAR, hons(nameImpl, ACL2Object.valueOf(delayImpl)));
            assert result.hashCode() == hashCode();
            backedCache.put(this, result);
        }
        return result;
    }

    @Override
    public default ACL2Object getACL2Object()
    {
        ACL2Object name = getACL2Name();
        int delay = getDelay();
        boolean nonblocking = isNonblocking();
        if ((stringp(name).bool() || symbolp(name).bool() && !booleanp(name).bool())
            && !nonblocking && delay == 0)
        {
            return name;
        }
        return cons(KEYWORD_VAR, cons(name, ACL2Object.valueOf(nonblocking ? -delay - 1 : delay)));
    }

    @SuppressWarnings("unchecked")
    public static <N extends SvarName> Svar<N>[] newSvarArray(int length)
    {
        return new Svar[length];
    }
}
