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

import java.util.HashMap;
import java.util.Map;

/**
 *
 * @param <N>
 */
public class SvexManager<N extends SvarName>
{
    Map<SvexCall<N>, SvexCall<N>> svexCalls = new HashMap<>();
    Map<N, SvexVar<N>> nondelayedVars = new HashMap<>();
    Map<Svar<N>, SvexVar<N>> delayedVars = new HashMap<>();

    @SafeVarargs
    public final SvexCall<N> newCall(SvexFunction fun, Svex<N>... args)
    {
        SvexCall<N> svex = fun.build(args);
        SvexCall<N> canonic = svexCalls.get(svex);
        if (canonic == null)
        {
            canonic = svex;
            canonic.setOwner(this);
            svexCalls.put(svex, canonic);
        }
        return canonic;
    }

    public SvexVar<N> getSvex(N name)
    {
        SvexVar<N> canonic = nondelayedVars.get(name);
        if (canonic == null)
        {
            Svar<N> svar = new SvarImpl<>(name, 0);
            canonic = new SvexVar<>(svar);
            nondelayedVars.put(name, canonic);
        }
        return canonic;
    }

    public Svar<N> getVar(N name)
    {
        return getSvex(name).svar;
    }

    N getName(N name)
    {
        return getVar(name).getName();
    }

    public SvexVar<N> getSvex(N name, int delay, boolean isNonblocking)
    {
        SvexVar<N> canonic = getSvex(name);
        int delayImpl = isNonblocking ? ~delay : delay;
        if (delayImpl == 0)
        {
            return canonic;
        }
        Svar<N> svar = new SvarImpl<>(canonic.svar.getName(), delayImpl);
        canonic = delayedVars.get(svar);
        if (canonic == null)
        {
            canonic = new SvexVar<>(svar);
            delayedVars.put(svar, canonic);
        }
        return canonic;
    }

    public SvexVar<N> getSvex(Svar<N> svar)
    {
        return getSvex(svar.getName(), svar.getDelay(), svar.isNonblocking());
    }

    public Svar<N> getVar(N name, int delay, boolean isNonblocking)
    {
        return getSvex(name, delay, isNonblocking).svar;
    }
}
