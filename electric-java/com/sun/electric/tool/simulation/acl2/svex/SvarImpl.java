/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: SVarImpl.java
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

import com.sun.electric.tool.simulation.acl2.mods.Util;
import static com.sun.electric.util.acl2.ACL2.*;
import com.sun.electric.util.acl2.ACL2Object;
import java.math.BigInteger;

/**
 * Implementation of a single variable in a symbolic vector expression.
 * See <http://www.cs.utexas.edu/users/moore/acl2/manuals/current/manual/?topic=SV____SVAR>.
 *
 * @param <N> Type of name of Svex variables
 */
public class SvarImpl<N extends SvarName> implements Svar<N>
{
    private final N name;
    private final int delayImpl;
    private final int hashCode;

    SvarImpl(N name, int delayImpl)
    {
        this.name = name;
        this.delayImpl = delayImpl;
        if (name.isSimpleSvarName() && delayImpl == 0)
        {
            hashCode = name.hashCode();
        } else
        {
            hashCode = ACL2Object.hashCodeOfCons(KEYWORD_VAR.hashCode(),
                ACL2Object.hashCodeOfCons(name.hashCode(), ACL2Object.hashCodeOf(delayImpl)));
        }
    }

    @Override
    public N getName()
    {
        return name;
    }

    @Override
    public ACL2Object getACL2Name()
    {
        return getName().getACL2Object();
    }

    @Override
    public int getDelay()
    {
        return delayImpl >= 0 ? delayImpl : ~delayImpl;
    }

    @Override
    public boolean isNonblocking()
    {
        return delayImpl < 0;
    }

    public static <N extends SvarName> Svar<N> fromACL2(SvarName.Builder<N> snb, SvexManager<N> sm, ACL2Object impl)
    {
        ACL2Object nameImpl;
        int delayImpl;
        if (consp(impl).bool())
        {
            Util.check(car(impl).equals(KEYWORD_VAR));
            nameImpl = car(cdr(impl));
            delayImpl = cdr(cdr(impl)).intValueExact();
        } else
        {
            nameImpl = impl;
            delayImpl = 0;
        }
        N name = snb.fromACL2(nameImpl);
        boolean isNonblocking = delayImpl < 0;
        int delay = isNonblocking ? ~delayImpl : delayImpl;
        return sm.getVar(name, delay, isNonblocking);
    }

    /*
    static <N extends SvarName> Svar<N> fromACL2(Svar.Builder<N> sb, ACL2Object impl, Map<ACL2Object, Svex<N>> cache)
    {
        assert !cache.containsKey(impl);
        N name;
        int delayImpl;
        if (consp(impl).bool())
        {
            Util.check(car(impl).equals(KEYWORD_VAR));
            ACL2Object nameImpl = car(cdr(impl));
            delayImpl = cdr(cdr(impl)).intValueExact();
            if (delayImpl != 0)
            {
                boolean nameIsSimple = stringp(nameImpl).bool() || symbolp(nameImpl).bool() && !booleanp(nameImpl).bool();
                ACL2Object impl0 = nameIsSimple ? nameImpl : cons(KEYWORD_VAR, cons(nameImpl, ACL2Object.valueOf(0)));
                SvexVar<N> svex0 = (SvexVar<N>)cache.get(impl0);
                if (svex0 != null)
                {
                    name = svex0.svar.getName();
                } else
                {
                    name = sb.newName(nameImpl);
                    Svar<N> svar = new SvarImpl<>(name, 0, impl0);
                    cache.put(impl0, new SvexVar<>(svar));
                }
            } else
            {
                name = sb.newName(nameImpl);
            }
        } else
        {
            name = sb.newName(impl);
            delayImpl = 0;
        }
        Svar<N> svar = new SvarImpl<>(name, delayImpl, impl);
        return svar;
//        Svex<N> svex = new SvexVar<>(svar);
//        cache.put(impl, svex);
//        return svex;
    }
     */
    @Override
    public boolean equals(Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (o instanceof SvarImpl)
        {
            SvarImpl that = (SvarImpl)o;
            return this.hashCode == that.hashCode
                && this.name.equals(that.name)
                && this.delayImpl == that.delayImpl;
        }
        return false;
    }

    @Override
    public int hashCode()
    {
        return hashCode;
    }

    @Override
    public ACL2Object getACL2Object()
    {
        if (name.isSimpleSvarName() && delayImpl == 0)
        {
            return name.getACL2Object();
        } else
        {
            return hons(KEYWORD_VAR,
                hons(name.getACL2Object(), ACL2Object.valueOf(delayImpl)));
        }
    }

    @Override
    public String toString()
    {
        return toString(null);
    }

    @Override
    public String toString(BigInteger mask)
    {
        String s = mask != null ? name.toString(mask) : name.toString();
        if (isNonblocking())
        {
            s = "#?" + getDelay() + " " + s;
        } else if (getDelay() != 0)
        {
            s = "#" + getDelay() + " " + s;
        }
        return s;
    }
}
