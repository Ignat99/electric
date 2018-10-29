/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: DesignExt.java
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

import com.sun.electric.tool.simulation.acl2.mods.Address;
import com.sun.electric.tool.simulation.acl2.mods.Design;
import com.sun.electric.tool.simulation.acl2.mods.ElabMod;
import com.sun.electric.tool.simulation.acl2.mods.ModDb;
import com.sun.electric.tool.simulation.acl2.mods.ModInst;
import com.sun.electric.tool.simulation.acl2.mods.ModName;
import com.sun.electric.tool.simulation.acl2.mods.Module;
import com.sun.electric.tool.simulation.acl2.mods.Util;
import com.sun.electric.util.TextUtils;
import com.sun.electric.util.acl2.ACL2Object;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * SVEX design.
 * See <http://www.cs.utexas.edu/users/moore/acl2/manuals/current/manual/?topic=SV____DESIGN>.
 */
public class DesignExt
{
    public final Design<Address> b;
    public final DesignHints designHints;
    public final ModDb moddb;

    public final Map<ModName, ModuleExt> downTop = new LinkedHashMap<>();
    public final Map<ModName, ModuleExt> topDown = new LinkedHashMap<>();

    final List<ParameterizedModule> paremterizedModules;

    public DesignExt(ACL2Object impl)
    {
        this(impl, new DesignHints.Dummy());
    }

    public DesignExt(ACL2Object impl, DesignHints designHints)
    {
        this(new Design<>(new Address.SvarNameBuilder(), impl), designHints);
    }

    public DesignExt(Design<Address> b, DesignHints designHints)
    {
        this.b = b;
        this.designHints = designHints;
        paremterizedModules = designHints.getParameterizedModules();

        moddb = new ModDb(b.top, b.modalist);
//        Util.check(moddb.nMods() == b.modalist.size());

        addToDownTop(b.top);
//        for (ModName mn : b.modalist.keySet())
//        {
//            addToDownTop(mn);
//        }
//        Util.check(downTop.size() == b.modalist.size());
        int modidx = 0;
        for (Iterator<ModName> it = downTop.keySet().iterator(); it.hasNext(); modidx++)
        {
            ElabMod elabMod = moddb.getMod(modidx);
            Util.check(elabMod.modidxGetName().equals(it.next()));
        }
        assert modidx == moddb.nMods();

        List<ModName> keys = new ArrayList<>(downTop.keySet());
        for (int i = keys.size() - 1; i >= 0; i--)
        {
            ModName key = keys.get(i);
            topDown.put(key, downTop.get(key));
        }
        Util.check(topDown.size() == downTop.size());

        topDown.get(b.top).markTop(designHints.getExportNames(b.top));
        Map<String, Integer> globalCounts = new TreeMap<>();
        for (ModuleExt m : topDown.values())
        {
            m.markDown(globalCounts);
        }

        for (Map.Entry<ModName, ModuleExt> e : downTop.entrySet())
        {
            ModName modName = e.getKey();
            ModuleExt m = e.getValue();
            String[] portInstancesToSplit = designHints.getPortInstancesToSplit(modName);
            if (portInstancesToSplit != null)
            {
                m.markPortInstancesToSplit(portInstancesToSplit);
            }
            int[] driversToSplit = designHints.getDriversToSplit(modName);
            if (driversToSplit != null)
            {
                m.markDriversToSplit(driversToSplit);
            }
        }

        List<Map.Entry<String, Integer>> filteredGlobalCounts = new ArrayList<>();
        for (Map.Entry<String, Integer> e : globalCounts.entrySet())
        {
            String canonicGlobal = TextUtils.canonicString(e.getKey());
            if (canonicGlobal.contains("clk") || canonicGlobal.contains("clock"))
            {
                filteredGlobalCounts.add(e);
            }
        }
        if (!globalCounts.isEmpty())
        {
            Collections.sort(filteredGlobalCounts, new Comparator<Map.Entry<String, Integer>>()
            {
                @Override
                public int compare(Map.Entry<String, Integer> o1, Map.Entry<String, Integer> o2)
                {
                    return Integer.compare(o2.getValue(), o1.getValue());
                }

            });
            System.out.print("Probable clocks:");
            int n = 0;
            for (Map.Entry<String, Integer> e : filteredGlobalCounts)
            {
                String global = e.getKey();
                Integer count = e.getValue();
                if (n++ >= 5)
                {
                    break;
                }
                System.out.print(" " + global + "(" + count + ")");
            }
            System.out.println();
        }
    }

    private void addToDownTop(ModName mn)
    {
        if (downTop.containsKey(mn))
        {
            return;
        }
        Module<Address> module = b.modalist.get(mn);
        for (ModInst modInst : module.insts)
        {
            addToDownTop(modInst.modname);
        }
        ModuleExt m = new ModuleExt(this, mn);
        ModuleExt old = downTop.put(mn, m);
        Util.check(old == null);
    }

    public ModName getTop()
    {
        return b.top;
    }

    public void computeCombinationalInputs(String clockName)
    {
        for (ModuleExt m : downTop.values())
        {
            m.computeCombinationalInputs(clockName);
        }
    }
}
