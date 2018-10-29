/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: DriverExt.java
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
package com.sun.electric.tool.simulation.acl2.modsext;

import com.sun.electric.tool.simulation.acl2.mods.Driver;
import com.sun.electric.tool.simulation.acl2.mods.Lhs;
import com.sun.electric.tool.simulation.acl2.svex.SvarName;
import com.sun.electric.tool.simulation.acl2.svex.Svex;
import com.sun.electric.tool.simulation.acl2.svex.SvexManager;
import com.sun.electric.tool.simulation.acl2.svex.SvexQuote;

/**
 *
 * @param <N>
 */
public class SymbolicDriver<N extends SvarName>
{
    public final DriverExt drv;
    public final int assignIndex;
    private final Lhs<N>[] args;
    private int lsh;
    private int width;
    private int rsh;

    SymbolicDriver(DriverExt drv, int assignIndex, Lhs<N>[] args, int lsh, int width, int rsh)
    {
        if (args.length != drv.getOrigVars().size())
        {
            throw new IllegalArgumentException();
        }
        this.drv = drv;
        this.assignIndex = assignIndex;
        this.args = args;
        this.lsh = lsh;
        this.width = width;
        this.rsh = rsh;
    }

    public Driver<N> makeWideDriver(SvexManager<N> sm)
    {
        return new Driver<>(subst(sm), drv.getStrength());
    }

    public Driver<N> makeDriver(SvexManager<N> sm)
    {
        Svex<N> Z = SvexQuote.Z();
        Svex<N> svex = subst(sm);
        svex = svex.rsh(sm, rsh);
        svex = svex.concat(sm, width, Z);
        svex = Z.concat(sm, lsh, svex);
        return new Driver<>(svex, drv.getStrength());
    }

    private Svex<N> subst(SvexManager<N> sm)
    {
        return drv.subst(args, sm);
    }
}
