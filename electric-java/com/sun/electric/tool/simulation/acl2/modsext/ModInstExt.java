/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: ModInstExt.java
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

import com.sun.electric.tool.simulation.acl2.mods.ElabMod;
import com.sun.electric.tool.simulation.acl2.mods.ModInst;
import com.sun.electric.tool.simulation.acl2.mods.ModName;
import com.sun.electric.tool.simulation.acl2.mods.Name;
import com.sun.electric.tool.simulation.acl2.mods.Path;
import com.sun.electric.tool.simulation.acl2.mods.Util;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import java.util.Map;

/**
 * SV module instance.
 * See <http://www.cs.utexas.edu/users/moore/acl2/manuals/current/manual/?topic=SV____MODINST>.
 */
public class ModInstExt
{
    public final ModInst b;

    final ModuleExt parent;
    final ModuleExt proto;
    final Map<Name, PathExt.PortInst> portInstsIndex = new HashMap<>();
    final ElabMod.ElabModInst elabModInst;
    List<PathExt.PortInst> portInsts;

    ModInstExt(ModuleExt parent, ModInst b, int instIndex, Map<ModName, ModuleExt> downTop)
    {
        this.b = b;
        this.parent = parent;
        proto = downTop.get(b.modname);
        Util.check(proto != null);
        elabModInst = parent.elabMod.getInst(instIndex);
    }

    public Name getInstname()
    {
        return b.instname;
    }

    public ModName getModname()
    {
        return b.modname;
    }

    PathExt.PortInst newPortInst(Path.Scope path)
    {
        assert path.namespace.equals(getInstname());
        Path.Wire pathWire = (Path.Wire)path.subpath;
//        assert getInstname().getACL2Object().equals(car(name));
//        WireExt wire = proto.wiresIndex.get(new Name(cdr(name)));
//        PathExt.PortInst pi = portInsts.get(wire.getName());
        PathExt.PortInst pi = portInstsIndex.get(pathWire.name);
        if (pi == null)
        {
            WireExt protoWire = proto.wiresIndex.get(pathWire.name);
            ModExport export = proto.makeExport(protoWire);
            pi = new PathExt.PortInst(this, path, export);
            portInstsIndex.put(pathWire.name, pi);
        }
        return pi;
    }

    @Override
    public String toString()
    {
        return b.toString();
    }

    void checkExports()
    {
        assert portInsts == null;
        portInsts = new ArrayList<>();
        for (WireExt export : proto.wires)
        {
            if (export.isExport())
            {
                PathExt.PortInst pi = portInstsIndex.get(export.b.name);
                Util.check(pi != null && (pi.source != null || pi.driver != null));
                Util.check(pi.source == null || pi.driver == null);
                portInsts.add(pi);
            }
        }
    }
}
