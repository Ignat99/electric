/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Driver.java
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

import com.sun.electric.tool.simulation.acl2.svex.Svar;
import com.sun.electric.tool.simulation.acl2.svex.SvarName;
import com.sun.electric.tool.simulation.acl2.svex.Svex;
import com.sun.electric.tool.simulation.acl2.svex.SvexManager;
import static com.sun.electric.util.acl2.ACL2.*;
import com.sun.electric.util.acl2.ACL2Backed;
import com.sun.electric.util.acl2.ACL2Object;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Driver - SVEX expression with strength.
 * See <http://www.cs.utexas.edu/users/moore/acl2/manuals/current/manual/?topic=SV____DRIVER>.
 *
 * @param <N> Type of name of Svex variables
 */
public class Driver<N extends SvarName> implements ACL2Backed
{
    public static final int DEFAULT_STRENGTH = 6;

    public final Svex<N> svex;
    public final int strength;
    private final int hashCode;

    public final List<Svar<N>> vars;

    public Driver(Svex<N> svex)
    {
        this(svex, DEFAULT_STRENGTH);
    }

    public Driver(Svex<N> svex, int strength)
    {
        this.svex = svex;
        this.strength = strength;
        vars = svex.collectVars();
        hashCode = ACL2Object.hashCodeOfCons(svex.hashCode(), ACL2Object.hashCodeOf(strength));
    }

    static <N extends SvarName> Driver<N> fromACL2(SvarName.Builder<N> snb, SvexManager<N> sm, ACL2Object impl,
        Map<ACL2Object, Svex<N>> svexCache)
    {
        Svex<N> svex = Svex.fromACL2(snb, sm, car(impl), svexCache);
        int strength = cdr(impl).intValueExact();
        return new Driver<>(svex, strength);
    }

    public <N1 extends SvarName> Driver<N1> convertVars(Function<N, N1> rename, SvexManager<N1> sm,
        Map<Svex<N>, Svex<N1>> svexCache)
    {
        Svex<N1> newSvex = svex.convertVars(rename, sm, svexCache);
        return new Driver<>(newSvex, strength);
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (o instanceof Driver)
        {
            Driver<?> that = (Driver<?>)o;
            return this.hashCode == that.hashCode
                && this.svex.equals(that.svex)
                && this.strength == that.strength;
        }
        return false;
    }

    @Override
    public int hashCode()
    {
        return hashCode;
    }

    @Override
    public ACL2Object getACL2Object(Map<ACL2Backed, ACL2Object> backedCache)
    {
        ACL2Object result = backedCache.get(this);
        if (result == null)
        {
            result = hons(svex.getACL2Object(backedCache), ACL2Object.valueOf(strength));
            backedCache.put(this, result);
        }
        assert result.hashCode() == hashCode;
        return result;
    }

    void vars(Collection<Svar<N>> vars)
    {
        vars.addAll(this.vars);
    }
}
