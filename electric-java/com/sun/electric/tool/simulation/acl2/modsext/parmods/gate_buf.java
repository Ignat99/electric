/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: gate_buf.java
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
package com.sun.electric.tool.simulation.acl2.modsext.parmods;

import com.sun.electric.tool.simulation.acl2.mods.Address;
import com.sun.electric.tool.simulation.acl2.mods.IndexName;
import com.sun.electric.tool.simulation.acl2.mods.Lhs;
import com.sun.electric.tool.simulation.acl2.mods.ModName;
import com.sun.electric.tool.simulation.acl2.mods.Module;
import com.sun.electric.tool.simulation.acl2.modsext.ParameterizedModule;
import com.sun.electric.tool.simulation.acl2.svex.SvexManager;
import com.sun.electric.util.acl2.ACL2;
import com.sun.electric.util.acl2.ACL2Object;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Verilog module gate_buf.
 */
public class gate_buf extends ParameterizedModule
{

    public static final ACL2Object KEYWORD_GATE = ACL2Object.valueOf("KEYWORD", "GATE");
    public static final ACL2Object KEYWORD_VL_BUF = ACL2Object.valueOf("KEYWORD", "VL-BUF");

    public static final ModName MODNAME_GATE_BUF_2 = ModName.fromACL2(ACL2.cons(KEYWORD_GATE, ACL2.cons(KEYWORD_VL_BUF, ACL2.cons(ACL2Object.valueOf(2), ACL2.NIL))));
    public static final gate_buf INSTANCE = new gate_buf();

    private gate_buf()
    {
        super("gate", "buf");
    }

    @Override
    protected Map<String, ACL2Object> matchModName(ModName modName)
    {
        return modName.equals(MODNAME_GATE_BUF_2) ? Collections.emptyMap() : null;
    }

    @Override
    protected Module<Address> genModule()
    {
        output("out1", 1);
        input("in", 1);
        assign("out1", 1, unfloat(v("in")));
        return getModule();
    }
}
