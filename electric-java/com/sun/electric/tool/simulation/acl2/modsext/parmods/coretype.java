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
import com.sun.electric.tool.simulation.acl2.mods.ModName;
import com.sun.electric.tool.simulation.acl2.mods.Module;
import com.sun.electric.tool.simulation.acl2.mods.Name;
import com.sun.electric.tool.simulation.acl2.mods.Util;
import com.sun.electric.tool.simulation.acl2.modsext.ParameterizedModule;
import com.sun.electric.util.acl2.ACL2;
import static com.sun.electric.util.acl2.ACL2.*;
import com.sun.electric.util.acl2.ACL2Object;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Verilog module coretype_reg.
 */
public class coretype extends ParameterizedModule
{

    public static final ACL2Object KEYWORD_VL_CORETYPE = ACL2Object.valueOf("KEYWORD", "VL-CORETYPE");
    public static final ACL2Object KEYWORD_VL_LOGIC = ACL2Object.valueOf("KEYWORD", "VL-LOGIC");
    public static final ACL2Object KEYWORD_VL_REG = ACL2Object.valueOf("KEYWORD", "VL-REG");
    public static final ACL2Object KEYWORD_VL_RANGE = ACL2Object.valueOf("KEYWORD", "VL-RANGE");
    public static final ACL2Object KEYWORD_VL_LITERAL = ACL2Object.valueOf("KEYWORD", "VL-LITERAL");
    public static final ACL2Object KEYWORD_VL_CONSTINT = ACL2Object.valueOf("KEYWORD", "VL-CONSTINT");
    public static final ACL2Object KEYWORD_VL_SIGNED = ACL2Object.valueOf("KEYWORD", "VL-SIGNED");

    public static final coretype INSTANCE = new coretype();

    private static ACL2Object mkRange(int val1, int val2)
    {
        return ACL2.hons(ACL2.hons(KEYWORD_VL_RANGE, ACL2.hons(mkLiteral(val1), mkLiteral(val2))), ACL2.NIL);
    }

    private static ACL2Object mkLiteral(int val)
    {
        ACL2Object constint = ACL2.hons(KEYWORD_VL_CONSTINT, ACL2.hons(ACL2.hons(ACL2Object.valueOf(32), ACL2Object.valueOf(val)), ACL2.hons(KEYWORD_VL_SIGNED, ACL2.T)));
        return ACL2.hons(KEYWORD_VL_LITERAL, ACL2.hons(constint, ACL2.hons(ACL2.hons(ACL2Object.valueOf("VL_ORIG_EXPR"), ACL2.hons(KEYWORD_VL_LITERAL, ACL2.hons(constint, ACL2.NIL))), ACL2.NIL)));
    }

    private coretype()
    {
        super("verilog", "coretype");
    }

    @Override
    protected Map<String, ACL2Object> matchModName(ModName modName)
    {
        if (!modName.isCoretype())
        {
            return null;
        }
        ACL2Object impl = modName.getACL2Object();
        if (!matchReg(cdr(impl)))
        {
            return null;
        }
        ACL2Object coretype = car(car(impl));
        ACL2Object pdims = cdr(car(cdr(impl)));
        ACL2Object udims = car(cdr(cdr(impl)));
        ACL2Object pdim = car(pdims);
        ACL2Object udim = car(udims);
        ACL2Object plit1 = car(cdr(pdim));
        ACL2Object plit2 = cdr(cdr(pdim));
        ACL2Object ulit1 = car(cdr(udim));
        ACL2Object ulit2 = cdr(cdr(udim));
        ACL2Object pcint1 = car(cdr(plit1));
        ACL2Object pcint2 = car(cdr(plit2));
        ACL2Object ucint1 = car(cdr(ulit1));
        ACL2Object ucint2 = car(cdr(ulit2));
        assert matchConstint(pcint1);
        assert matchConstint(pcint2);
        assert matchConstint(ucint1);
        assert matchConstint(ucint2);
        int p1 = cdr(car(cdr(pcint1))).intValueExact();
        int p2 = cdr(car(cdr(pcint2))).intValueExact();
        int u1 = cdr(car(cdr(ucint1))).intValueExact();
        int u2 = cdr(car(cdr(ucint2))).intValueExact();
        Map<String, ACL2Object> result = new LinkedHashMap<>();
        result.put("coretype", coretype);
        result.put("p1", ACL2Object.valueOf(p1));
        result.put("p2", ACL2Object.valueOf(p2));
        result.put("u1", ACL2Object.valueOf(u1));
        result.put("u2", ACL2Object.valueOf(u2));
        return result;
    }

    private boolean matchReg(ACL2Object impl)
    {
        ACL2Object coretype = car(car(impl));
        return (coretype.equals(KEYWORD_VL_LOGIC) || coretype.equals(KEYWORD_VL_REG))
            && matchRange(cdr(car(impl)))
            && matchRange(car(cdr(impl)))
            && cdr(cdr(impl)).equals(NIL);
    }

    private boolean matchRange(ACL2Object impl)
    {
        return car(car(impl)).equals(KEYWORD_VL_RANGE)
            && matchLiteral(car(cdr(car(impl))))
            && matchLiteral(cdr(cdr(car(impl))))
            && cdr(impl).equals(NIL);
    }

    private boolean matchLiteral(ACL2Object impl)
    {
        return car(impl).equals(KEYWORD_VL_LITERAL)
            && matchConstint(car(cdr(impl)))
            && car(car(cdr(cdr(impl)))).equals(ACL2Object.valueOf("VL_ORIG_EXPR"));
    }

    private boolean matchConstint(ACL2Object impl)
    {
        return car(impl).equals(KEYWORD_VL_CONSTINT)
            && car(car(cdr(impl))).equals(ACL2Object.valueOf(32))
            && integerp(cdr(car(cdr(impl)))).bool()
            && car(cdr(cdr(impl))).equals(KEYWORD_VL_SIGNED)
            && cdr(cdr(cdr(impl))).equals(T);
    }

    @Override
    protected boolean exportsAreStrings()
    {
        return false;
    }

    @Override
    protected Module<Address> genModule()
    {
        int p1 = getIntParam("p1");
        int p2 = getIntParam("p2");
        int u1 = getIntParam("u1");
        int u2 = getIntParam("u2");
        Util.check(p1 >= p2);
        int width = p1 - p2 + 1;
        int nwords = Math.abs(u2 - u1) + 1;
        Name self = Name.SELF;
        unused(self, nwords * width);
        if (u1 <= u2)
        {
            for (int addr = u1; addr <= u2; addr++)
            {
                Name a = Name.valueOf(addr);
                wire(a, width, p2);
                int offset = width * (nwords - (addr - u1) - 1);
                conn(r(a, width - 1, 0), r(self, offset + width - 1, offset));
            }
        } else
        {
            for (int addr = u1; addr >= u2; addr--)
            {
                Name a = Name.valueOf(addr);
                wire(a, width, p2);
                int offset = width * (addr - u2);
                conn(r(a, width - 1, 0), r(self, offset + width - 1, offset));
            }

        }
        return getModule();
    }

    @Override
    protected int getNumAssigns()
    {
        return 0;
    }

    @Override
    protected int getTotalAssigns()
    {
        return 0;
    }
}
