/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Compile.java
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

import com.sun.electric.tool.simulation.acl2.mods.Assign;
import com.sun.electric.tool.simulation.acl2.mods.Driver;
import com.sun.electric.tool.simulation.acl2.mods.IndexName;
import com.sun.electric.tool.simulation.acl2.mods.Lhatom;
import com.sun.electric.tool.simulation.acl2.mods.Lhrange;
import com.sun.electric.tool.simulation.acl2.mods.Lhs;
import com.sun.electric.tool.simulation.acl2.mods.Util;
import com.sun.electric.tool.simulation.acl2.svex.Svar;
import com.sun.electric.tool.simulation.acl2.svex.SvarName;
import com.sun.electric.tool.simulation.acl2.svex.Svex;
import com.sun.electric.tool.simulation.acl2.svex.SvexCall;
import com.sun.electric.tool.simulation.acl2.svex.SvexManager;
import com.sun.electric.tool.simulation.acl2.svex.SvexQuote;
import com.sun.electric.tool.simulation.acl2.svex.SvexVar;
import com.sun.electric.tool.simulation.acl2.svex.funs.Vec4Res;
import static com.sun.electric.util.acl2.ACL2.*;
import com.sun.electric.util.acl2.ACL2Backed;
import com.sun.electric.util.acl2.ACL2Object;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 *
 * @param <N> Type of name of Svex variables
 */
public class Compile<N extends SvarName>
{
    private final List<Lhs<N>> aliases;
    private final SvexManager<N> sm;
    public final Svex<N>[] svexarr;
    public final List<Assign<N>> normAssigns;
    public final Map<Svar<N>, List<Driver<N>>> netAssigns;
    public final Map<Svar<N>, Svex<N>> resAssigns;
    public final Map<Svar<N>, Svar<N>> resDelays;

    Compile(List<Lhs<N>> aliases, List<Assign<IndexName>> assigns, SvexManager<N> sm)
    {
        this.aliases = aliases;
        this.sm = sm;
        svexarr = Svex.newSvexArray(aliases.size());
        for (int i = 0; i < aliases.size(); i++)
        {
            svexarr[i] = aliases.get(i).toSvex(sm);
        }
        Map<SvexCall<IndexName>, Svex<N>> memoize = new HashMap<>();
        normAssigns = assignsSubst(assigns, memoize);
        netAssigns = assignsToNetassigns(normAssigns);
        resAssigns = netassignsResolves(netAssigns);
        resDelays = collectDelays(Svex.collectVarsRev(resAssigns.values()), sm);
    }

    Lhs<N> aliasNorm(Lhs<IndexName> x)
    {
        Lhs.Decomp<IndexName> decomp = x.decomp();
        if (decomp.first == null)
        {
            return new Lhs<>(Collections.emptyList());
        }
        Lhrange<IndexName> first = decomp.first;
        Svar<IndexName> svar = first.getVar();
        if (svar == null)
        {
            Lhrange<N> newFirst = new Lhrange<>(first.getWidth(), Lhatom.Z());
            return aliasNorm(decomp.rest).cons(newFirst);
        }
        int idx = svar.getName().getIndex();
        Lhs<N> low = aliases.get(idx).rsh(first.getRsh());
        Lhs<N> high = aliasNorm(decomp.rest);
        return low.concat(first.getWidth(), high);
    }

    private Svex<N> svexSubstFromSvexarr(Svex<IndexName> x, Map<SvexCall<IndexName>, Svex<N>> memoize)
    {
        if (x instanceof SvexVar)
        {
            SvexVar<IndexName> sv = (SvexVar<IndexName>)x;
            Svex<N> svex = svexarr[sv.svar.getName().getIndex()];
            Map<Svex<N>, Svex<N>> addDelayCache = new HashMap<>();
            return svex.addDelay(sv.svar.getDelay(), sm, addDelayCache);
        } else if (x instanceof SvexQuote)
        {
            return SvexQuote.valueOf(((SvexQuote)x).val);
        } else
        {
            SvexCall<IndexName> sc = (SvexCall<IndexName>)x;
            Svex<N> newX = memoize.get(sc);
            if (newX == null)
            {
                Svex<IndexName>[] args = sc.getArgs();
                Svex<N>[] newArgs = Svex.newSvexArray(args.length);
                for (int i = 0; i < args.length; i++)
                {
                    newArgs[i] = svexSubstFromSvexarr(args[i], memoize);
                }
                newX = sc.fun.<N>callStar(sm, newArgs);
                memoize.put(sc, newX);
            }
            return newX;
        }
    }

    private List<Assign<N>> assignsSubst(Collection<Assign<IndexName>> assigns,
        Map<SvexCall<IndexName>, Svex<N>> memoize)
    {
        List<Assign<N>> newAssigns = new ArrayList<>();
        for (Assign<IndexName> assign : assigns)
        {
            Lhs<IndexName> lhs = assign.lhs;
            Driver<IndexName> drv = assign.driver;
            Lhs<N> newLhs = aliasNorm(lhs);
            Svex<N> val = svexSubstFromSvexarr(drv.svex, memoize);
            Driver<N> newDrv = new Driver<>(val, drv.strength);
            Assign<N> newAssign = new Assign<>(newLhs, newDrv);
            newAssigns.add(newAssign);
        }
        assert newAssigns.size() == assigns.size();
        return newAssigns;
    }

    private Map<Svar<N>, List<Driver<N>>> assignsToNetassigns(List<Assign<N>> assigns)
    {
        Svex<N> Z = SvexQuote.Z();
        Map<Svar<N>, List<Driver<N>>> netassignsRev = new LinkedHashMap<>();
        for (int i = assigns.size() - 1; i >= 0; i--)
        {
            Assign<N> assign = assigns.get(i);
            Lhs<N> lhs = assign.lhs;
            Driver<N> drv = assign.driver;
            assert lhs.isNormp();
            int offset = lhs.width();
            for (int j = lhs.ranges.size() - 1; j >= 0; j--)
            {
                Lhrange<N> range = lhs.ranges.get(j);
                offset -= range.getWidth();
                Svar<N> svar = range.getVar();
                if (svar != null)
                {
                    Svex<N> svex = drv.svex.rsh(sm, offset);
                    svex = svex.concat(sm, range.getWidth(), Z);
                    svex = Z.concat(sm, range.getRsh(), svex);
                    Driver<N> newDrv = new Driver<>(svex, drv.strength);
                    List<Driver<N>> drivers = netassignsRev.get(svar);
                    if (drivers == null)
                    {
                        drivers = new LinkedList<>();
                        netassignsRev.put(svar, drivers);
                    }
                    drivers.add(newDrv);
                }
            }
        }
        List<Map.Entry<Svar<N>, List<Driver<N>>>> netassignsEntries = new ArrayList<>();
        netassignsEntries.addAll(netassignsRev.entrySet());
        Map<Svar<N>, List<Driver<N>>> netassigns = new LinkedHashMap<>();
        for (int i = netassignsEntries.size() - 1; i >= 0; i--)
        {
            Map.Entry<Svar<N>, List<Driver<N>>> e = netassignsEntries.get(i);
            netassigns.put(e.getKey(), e.getValue());
        }
        return netassigns;
    }

    private Map<Svar<N>, Svex<N>> netassignsResolves(Map<Svar<N>, List<Driver<N>>> netassigns)
    {
        Svex<N> Z = SvexQuote.Z();
        Map<Svar<N>, Svex<N>> resolves = new LinkedHashMap<>();
        for (Map.Entry<Svar<N>, List<Driver<N>>> e : netassigns.entrySet())
        {
            Svar<N> svar = e.getKey();
            List<Driver<N>> drivers = e.getValue();
            Util.check(svar.getDelay() == 0);
            assert !drivers.isEmpty();
            Svex<N> svex = drivers.get(drivers.size() - 1).svex;
            for (int i = drivers.size() - 2; i >= 0; i--)
            {
                svex = sm.newCall(Vec4Res.FUNCTION, drivers.get(i).svex, svex);
            }
            resolves.put(svar, svex);
        }
        return resolves;
    }

    public static <N extends SvarName> Map<Svar<N>, Svar<N>> collectDelays(Collection<Svar<N>> svarsRev, SvexManager<N> sm)
    {
        Map<Svar<N>, Svar<N>> result = new LinkedHashMap<>();
        List<Svar<N>> svarsList = new ArrayList<>(svarsRev);
        for (int i = svarsList.size() - 1; i >= 0; i--)
        {
            Svar<N> svar = svarsList.get(i);
            while (svar.getDelay() > 0)
            {
                Svar<N> sv = sm.getVar(svar.getName(), svar.getDelay() - 1, false);
                result.put(svar, sv);
                svar = sv;
            }
        }
        return result;
    }

    public ACL2Object svexarrAsACL2Object()
    {
        ACL2Object result = NIL;
        for (int i = svexarr.length - 1; i >= 0; i--)
        {
            result = cons(svexarr[i].getACL2Object(), result);
        }
        return result;
    }

    public ACL2Object normAssignsAsACL2Object()
    {
        Map<ACL2Backed, ACL2Object> backedCache = new HashMap<>();
        ACL2Object result = NIL;
        for (Assign<N> assign : normAssigns)
        {
            Lhs<N> lhs = assign.lhs;
            Driver<N> drv = assign.driver;
            result = cons(cons(lhs.getACL2Object(backedCache), drv.getACL2Object(backedCache)), result);
        }
        return Util.revList(result);
    }

    public ACL2Object netAssignsAsACL2Object()
    {
        Map<ACL2Backed, ACL2Object> backedCache = new HashMap<>();
        ACL2Object result = NIL;
        for (Map.Entry<Svar<N>, List<Driver<N>>> e : netAssigns.entrySet())
        {
            Svar<N> svar = e.getKey();
            List<Driver<N>> drivers = e.getValue();
            ACL2Object acl2Drivers = NIL;
            for (int i = drivers.size() - 1; i >= 0; i--)
            {
                Driver<N> drv = drivers.get(i);
                acl2Drivers = cons(drv.getACL2Object(backedCache), acl2Drivers);
            }
            result = cons(cons(svar.getACL2Object(backedCache), acl2Drivers), result);
        }
        return Util.revList(result);
    }

    public ACL2Object resAssignsAsACL2Object()
    {
        Map<ACL2Backed, ACL2Object> backedCache = new HashMap<>();
        ACL2Object result = NIL;
        for (Map.Entry<Svar<N>, Svex<N>> e : resAssigns.entrySet())
        {
            Svar<N> svar = e.getKey();
            Svex<N> svex = e.getValue();
            result = cons(cons(svar.getACL2Object(backedCache),
                svex.getACL2Object(backedCache)), result);
        }
        return Util.revList(result);
    }

    public ACL2Object resDelaysAsACL2Object()
    {
        Map<ACL2Backed, ACL2Object> backedCache = new HashMap<>();
        ACL2Object result = NIL;
        for (Map.Entry<Svar<N>, Svar<N>> e : resDelays.entrySet())
        {
            Svar<N> svarDelayed = e.getKey();
            Svar<N> svar = e.getValue();
            result = cons(cons(svarDelayed.getACL2Object(backedCache),
                svar.getACL2Object(backedCache)), result);
        }
        return Util.revList(result);
    }
}
