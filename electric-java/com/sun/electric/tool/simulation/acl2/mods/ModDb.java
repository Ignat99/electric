/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: ModDb.java
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

import com.sun.electric.tool.simulation.acl2.svex.SvexManager;
import static com.sun.electric.util.acl2.ACL2.*;
import com.sun.electric.util.acl2.ACL2Backed;
import com.sun.electric.util.acl2.ACL2Object;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SV module Database.
 * See <http://www.cs.utexas.edu/users/moore/acl2/manuals/current/manual/?topic=SV____MODDB>.
 */
public class ModDb
{
    final List<ElabMod> mods = new ArrayList<>();
    final Map<ModName, ElabMod> modnameIdxes = new HashMap<>();

    public ModDb(ModName modName, Map<ModName, Module<Address>> modalist)
    {
        moduleToDb(modName, modalist);
    }

    public ModDb(Map<ModName, Module<Address>> modalist)
    {
        for (ModName modName : modalist.keySet())
        {
            moduleToDb(modName, modalist);
        }
    }

    public ModDb(ElabMod elabMod, ModDb origDb)
    {
        moduleToDb(elabMod, origDb.modnameIdxes);
    }

    public int nMods()
    {
        return mods.size();
    }

    public ElabMod getMod(int modIdx)
    {
        return mods.get(modIdx);
    }

    public ElabMod topMod()
    {
        return getMod(nMods() - 1);
    }

    public ElabMod modnameGetIndex(ModName modName)
    {
        return modnameIdxes.get(modName);
    }

    public Map<ModName, Module<Address>> modalistNamedToIndex(Map<ModName, Module<Address>> modalist)
    {
        Map<ModName, Module<Address>> result = new LinkedHashMap<>();
        for (Map.Entry<ModName, Module<Address>> e : modalist.entrySet())
        {
            ModName modName = e.getKey();
            Module<Address> module = e.getValue();
            ElabMod modIdx = modnameIdxes.get(modName);
            Module<Address> newModule = modIdx.moduleNamedToIndex(module);
            result.put(modName, newModule);
        }
        return result;
    }

    private void moduleToDb(ModName modName, Map<ModName, Module<Address>> modalist)
    {
        ElabMod elabMod = modnameIdxes.get(modName);
        if (elabMod != null)
        {
            return;
        }
        if (modnameIdxes.containsKey(modName))
        {
            throw new IllegalArgumentException("Module loop " + modName);
        }
        modnameIdxes.put(modName, null);
        Module<Address> module = modalist.get(modName);
        if (module == null)
        {
            throw new IllegalArgumentException("Module not found " + modName);
        }
        for (ModInst modInst : module.insts)
        {
            moduleToDb(modInst.modname, modalist);
        }
        elabMod = new ElabMod(modName, module, modnameIdxes);
        mods.add(elabMod);
        ElabMod old = modnameIdxes.put(modName, elabMod);
        assert old == null;
    }

    private void moduleToDb(ElabMod elabMod, Map<ModName, ElabMod> origModDb)
    {
        ElabMod newElabMod = modnameIdxes.get(elabMod.modName);
        if (newElabMod != null)
        {
            Util.check(newElabMod == elabMod);
            return;
        }
        modnameIdxes.put(elabMod.modName, elabMod);

        for (ModInst modInst : elabMod.origMod.insts)
        {
            moduleToDb(origModDb.get(modInst.modname), origModDb);
        }
        mods.add(elabMod);
    }

    public static class FlattenResult
    {
        public final List<Aliaspair<IndexName>> aliaspairs = new ArrayList<>();
        public final List<Assign<IndexName>> assigns = new ArrayList<>();
        public final SvexManager<IndexName> sm = new SvexManager<>();
        public LhsArr aliases;

        public ACL2Object aliaspairsToACL2Object()
        {
            Map<ACL2Backed, ACL2Object> backedCache = new HashMap<>();
            ACL2Object alist = NIL;
            for (Aliaspair<IndexName> aliaspair : aliaspairs)
            {
                alist = cons(cons(aliaspair.lhs.getACL2Object(backedCache),
                    aliaspair.rhs.getACL2Object(backedCache)), alist);
            }
            return Util.revList(alist);
        }

        public ACL2Object assignsToACL2Object()
        {
            Map<ACL2Backed, ACL2Object> backedCache = new HashMap<>();
            ACL2Object alist = NIL;
            for (Assign<IndexName> assign : assigns)
            {
                alist = cons(cons(assign.lhs.getACL2Object(backedCache),
                    assign.driver.getACL2Object(backedCache)), alist);
            }
            return Util.revList(alist);
        }

        public ACL2Object aliasesToACL2Object()
        {
            return aliases.collectAliasesAsACL2Objects();
        }
    }

}
