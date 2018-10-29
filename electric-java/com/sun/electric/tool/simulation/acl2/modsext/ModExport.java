/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: ModExport.java
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

import com.sun.electric.tool.simulation.acl2.svex.Svar;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Exported wire of a module
 */
public class ModExport
{
    public final ModuleExt parent;
    public final WireExt wire;
    public final int index;
    public String global;

    private final BitSet fineBitStateDeps0 = new BitSet();
    private final BitSet fineBitStateDeps1 = new BitSet();
    private final List<Map<Svar<PathExt>, BigInteger>> fineBitDeps0 = new ArrayList<>();
    private final List<Map<Svar<PathExt>, BigInteger>> fineBitDeps1 = new ArrayList<>();

    final Map<Svar<PathExt>, BigInteger> crudePortDeps0 = new LinkedHashMap<>();
    final Map<Svar<PathExt>, BigInteger> crudePortDeps1 = new LinkedHashMap<>();
    boolean crudePortStateDep0;
    boolean crudePortStateDep1;

    ModExport(WireExt wire)
    {
        parent = wire.parent;
        this.wire = wire;
        index = wire.index;
    }

    public int getWidth()
    {
        return wire.getWidth();
    }

    public boolean isInput()
    {
        return wire.isInput();
    }

    public boolean isOutput()
    {
        return wire.isOutput();
    }

    public void markGlobal(String name)
    {
        if (global == null)
        {
            global = name;
        } else if (!global.equals(name))
        {
            global = "";
        }
    }

    public boolean isGlobal()
    {
        return global != null && !global.isEmpty();
    }

    BitSet getFineBitStateDeps(boolean clockHigh)
    {
        return clockHigh ? fineBitStateDeps1 : fineBitStateDeps0;
    }

    List<Map<Svar<PathExt>, BigInteger>> getFineBitDeps(boolean clockHigh)
    {
        return clockHigh ? fineBitDeps1 : fineBitDeps0;
    }

    boolean getFinePortStateDeps(boolean clcokHigh)
    {
        return !getFineBitStateDeps(clcokHigh).isEmpty();
    }

    Map<Svar<PathExt>, BigInteger> getFinePortDeps(boolean clockHigh)
    {
        return parent.sortDeps(ModuleExt.combineDeps(getFineBitDeps(clockHigh)));
    }

    void setFineDeps(boolean clockHigh, Map<Object, Set<Object>> closure)
    {
        if (clockHigh)
        {
            fineBitDeps1.addAll(wire.gatherFineBitDeps(fineBitStateDeps1, closure));
        } else
        {
            fineBitDeps0.addAll(wire.gatherFineBitDeps(fineBitStateDeps0, closure));
        }
    }

    boolean getCrudePortStateDeps(boolean clockHigh)
    {
        return clockHigh ? crudePortStateDep1 : crudePortStateDep0;
    }

    Map<Svar<PathExt>, BigInteger> getCrudePortDeps(boolean clockHigh)
    {
        return clockHigh ? crudePortDeps1 : crudePortDeps0;
    }

}
