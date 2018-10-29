/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: DesignHints.java
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
import com.sun.electric.tool.simulation.acl2.mods.ModName;
import com.sun.electric.tool.simulation.acl2.mods.Name;
import com.sun.electric.tool.simulation.acl2.mods.Path;
import com.sun.electric.tool.simulation.acl2.mods.Util;
import com.sun.electric.tool.simulation.acl2.svex.BigIntegerUtil;
import com.sun.electric.tool.simulation.acl2.svex.Svar;
import com.sun.electric.tool.simulation.acl2.svex.Svex;
import com.sun.electric.tool.simulation.acl2.svex.SvexManager;
import com.sun.electric.tool.simulation.acl2.svex.Vec2;
import com.sun.electric.tool.simulation.acl2.svex.Vec4;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Hints to generate FSM for particular design
 */
public interface DesignHints
{
    List<ParameterizedModule> getParameterizedModules();

    String getGlobalClock();

    String[] getExportNames(ModName modName);

    String[] getPortInstancesToSplit(ModName modName);

    int[] getDriversToSplit(ModName modName);

    default void testSvtv(ModName modName,
        Map<Svar<Address>, Svex<Address>> updates,
        Map<Svar<Address>, Svex<Address>> svtvOutExprs,
        Map<Svar<Address>, Svex<Address>> svtvNextStates,
        SvexManager<Address> sm)
    {
        System.out.println("Don't know how to test " + modName);
    }

    static void putEnv(SvexManager<Address> sm, Map<Svar<Address>, Vec4> env, String varName, long val)
    {
        Svar<Address> svar = sm.getVar(Address.valueOf(Path.simplePath(Name.valueOf(varName))));
        env.put(svar, Vec2.valueOf(val));
    }

    static void putUpdate(SvexManager<Address> sm, Map<Svar<Address>, Vec4> env, String varName, long val, int width)
    {
        Svar<Address> svar = sm.getVar(Address.valueOf(Path.simplePath(Name.valueOf(varName))));
        env.put(svar, makeZ(val, width));
    }

    static void putState(SvexManager<Address> sm, Map<Svar<Address>, Vec4> env, String varName, long val, int width)
    {
        Path path;
        int indexDot = varName.indexOf('.');
        if (indexDot < 0)
        {
            path = Path.simplePath(Name.valueOf(varName));
        } else
        {
            List<Name> scopes = new ArrayList<>();
            while (indexDot >= 0)
            {
                scopes.add(Name.valueOf(varName.substring(0, indexDot)));
                varName = varName.substring(indexDot + 1);
                indexDot = varName.indexOf('.');
            }
            path = Path.makePath(scopes, Name.valueOf(varName));
        }
        Svar<Address> svar = sm.getVar(Address.valueOf(path), 1, false);
        env.put(svar, makeZ(val, width));
    }

    static Vec4 makeZ(long val, int width)
    {
        return makeZ(BigInteger.valueOf(val), width);
    }

    static Vec4 makeZ(BigInteger val, int width)
    {
        Util.check(val.signum() >= 0 && val.bitLength() <= width);
        return Vec4.valueOf(val, BigIntegerUtil.MINUS_ONE.shiftLeft(width).or(val));
    }

    static void test(SvexManager<Address> sm, String clkName, String scanClkName,
        Map<Svar<Address>, Svex<Address>> svtvOutExprs,
        Map<Svar<Address>, Svex<Address>> svtvNextStates,
        Map<Svar<Address>, Vec4> env0,
        Map<Svar<Address>, Vec4> expectedOut,
        Map<Svar<Address>, Vec4> expectedState)
    {
        Address clkAddress = Address.valueOf(Path.simplePath(Name.valueOf(clkName)));
        Svar<Address> clk = sm.getVar(clkAddress);
        Svar<Address> clkDelayed = sm.getVar(clkAddress, 1, false);
        Svar<Address> scanClk = null;
        Svar<Address> scanClkDelayed = null;
        if (scanClkName != null)
        {
            Address scanClkAddress = Address.valueOf(Path.simplePath(Name.valueOf(scanClkName)));
            scanClk = sm.getVar(scanClkAddress);
            scanClkDelayed = sm.getVar(scanClkAddress, 1, false);
        }
        env0.put(clkDelayed, Vec2.ONE);
        env0.put(clk, Vec2.ZERO);
        if (scanClkName != null)
        {
            env0.put(scanClkDelayed, Vec2.ZERO);
            env0.put(scanClk, Vec2.ZERO);
        }
        for (Map.Entry<Svar<Address>, Vec4> e : expectedOut.entrySet())
        {
            Svar<Address> svar = e.getKey();
            Vec4 val = e.getValue();
            if (!svtvOutExprs.get(svar).eval(env0).equals(val))
            {
                Vec4 svtvVal = svtvOutExprs.get(svar).eval(env0);
                System.out.println(svtvVal + " " + val);
            }
            Util.check(svtvOutExprs.get(svar).eval(env0).equals(val));
        }

        Map<Svar<Address>, Vec4> env1 = new HashMap<>();
        env1.put(clkDelayed, Vec2.ZERO);
        env1.put(clk, Vec2.ONE);
        if (scanClkName != null)
        {
            env1.put(scanClkDelayed, Vec2.ZERO);
            env1.put(scanClk, Vec2.ZERO);
        }
        for (Map.Entry<Svar<Address>, Svex<Address>> e : svtvNextStates.entrySet())
        {
            Svar<Address> svar = e.getKey();
            Svex<Address> svex = e.getValue();
            Vec4 val = svex.eval(env0);
            env1.put(svar, val);
        }

        for (Map.Entry<Svar<Address>, Vec4> e : expectedState.entrySet())
        {
            Svar<Address> svar = e.getKey();
            Vec4 val = e.getValue();
            if (!svtvNextStates.get(svar).eval(env1).equals(val))
            {
                Vec4 svtvVal = svtvNextStates.get(svar).eval(env1);
                System.out.println(svtvVal + " " + val);
            }
            Util.check(svtvNextStates.get(svar).eval(env1).equals(val));
        }
    }

    public static class Dummy implements DesignHints
    {
        @Override
        public List<ParameterizedModule> getParameterizedModules()
        {
            return Collections.emptyList();
        }

        @Override
        public String getGlobalClock()
        {
            return null;
        }

        @Override
        public String[] getExportNames(ModName modName)
        {
            return null;
        }

        @Override
        public String[] getPortInstancesToSplit(ModName modName)
        {
            return null;
        }

        @Override
        public int[] getDriversToSplit(ModName modName)
        {
            return null;
        }
    }
}
