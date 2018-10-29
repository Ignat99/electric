/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: ModuleExt.java
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
import com.sun.electric.tool.simulation.acl2.mods.Aliaspair;
import com.sun.electric.tool.simulation.acl2.mods.Assign;
import com.sun.electric.tool.simulation.acl2.mods.Driver;
import com.sun.electric.tool.simulation.acl2.mods.ElabMod;
import com.sun.electric.tool.simulation.acl2.mods.IndexName;
import com.sun.electric.tool.simulation.acl2.mods.Lhatom;
import com.sun.electric.tool.simulation.acl2.mods.Lhrange;
import com.sun.electric.tool.simulation.acl2.mods.Lhs;
import com.sun.electric.tool.simulation.acl2.mods.ModDb;
import com.sun.electric.tool.simulation.acl2.mods.ModInst;
import com.sun.electric.tool.simulation.acl2.mods.ModName;
import com.sun.electric.tool.simulation.acl2.mods.Module;
import com.sun.electric.tool.simulation.acl2.mods.Name;
import com.sun.electric.tool.simulation.acl2.mods.Path;
import com.sun.electric.tool.simulation.acl2.mods.Wire;
import com.sun.electric.tool.simulation.acl2.mods.Util;
import com.sun.electric.tool.simulation.acl2.svex.BigIntegerUtil;
import com.sun.electric.tool.simulation.acl2.svex.Svar;
import com.sun.electric.tool.simulation.acl2.svex.Svex;
import com.sun.electric.tool.simulation.acl2.svex.SvexCall;
import com.sun.electric.tool.simulation.acl2.svex.SvexManager;
import com.sun.electric.tool.simulation.acl2.svex.SvexQuote;
import com.sun.electric.tool.simulation.acl2.svex.SvexVar;
import com.sun.electric.tool.simulation.acl2.svex.Vec2;
import com.sun.electric.tool.simulation.acl2.svex.Vec4;
import com.sun.electric.tool.simulation.acl2.svex.funs.Vec4Res;
import com.sun.electric.util.acl2.ACL2Backed;
import com.sun.electric.util.acl2.ACL2Object;
import java.math.BigInteger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * SV module.
 * See <http://www.cs.utexas.edu/users/moore/acl2/manuals/current/manual/?topic=SV____MODULE>.
 */
public class ModuleExt /*extends SvarImpl.Builder<PathExt>*/ implements Comparator<Svar<PathExt>>
{
    // State marker in dependency graphs
    static final String STATE = "STATE";

    public final DesignExt design;
    public final ModName modName;
    public final Module<Address> b;
    public final ParameterizedModule parMod;
    public final List<WireExt> wires = new ArrayList<>();
    public final List<ModInstExt> insts = new ArrayList<>();
    public final Map<Lhs<PathExt>, DriverExt> assigns = new LinkedHashMap<>();
    public final Map<Lhs<PathExt>, Lhs<PathExt>> aliaspairs = new LinkedHashMap<>();

    final Map<Name, WireExt> wiresIndex = new HashMap<>();
    final Map<Name, ModInstExt> instsIndex = new HashMap<>();
    public final SvexManager<PathExt> sm = new SvexManager<>();
    final ElabMod elabMod;
    final List<ModExport> exports = new ArrayList();
    int useCount;
    boolean isTop;

    final boolean hasSvtvState;
    boolean hasPhaseState, hasCycleState;
    final Set<WireExt> stateWires = new LinkedHashSet<>();
    final Map<Svar<PathExt>, BigInteger> stateVars0 = new LinkedHashMap<>();
    final Map<Svar<PathExt>, BigInteger> stateVars1 = new LinkedHashMap<>();

    final Function<Address, PathExt> rename = new Function<Address, PathExt>()
    {
        @Override
        public PathExt apply(Address address)
        {
            assert address.index == Address.INDEX_NIL;
            assert address.scope == 0;
            Path path = address.path;
            if (path instanceof Path.Wire)
            {
                Path.Wire pw = (Path.Wire)path;
                WireExt wire = wiresIndex.get(pw.name);
                assert wire != null;
                return wire;
            } else
            {
                Path.Scope ps = (Path.Scope)path;
                ModInstExt inst = instsIndex.get(ps.namespace);
                assert inst != null;
                return inst.newPortInst(ps);
            }
        }
    };

    ModuleExt(DesignExt design, ModName modName)
    {
        this.design = design;
        this.modName = modName;
        b = design.b.modalist.get(modName);
        elabMod = design.moddb.modnameGetIndex(modName);

        for (Wire wire : b.wires)
        {
            WireExt w = new WireExt(this, wire, wires.size());
            wires.add(w);
            WireExt old = wiresIndex.put(w.getName(), w);
            Util.check(old == null);
        }

        boolean hasSvtvState = false;
        for (ModInst modInst : b.insts)
        {
            ModInstExt mi = new ModInstExt(this, modInst, insts.size(), design.downTop);
            insts.add(mi);
            ModInstExt old = instsIndex.put(mi.getInstname(), mi);
            Util.check(old == null);
            if (mi.proto.hasSvtvState)
            {
                hasSvtvState = true;
            }
        }

        Map<Svex<Address>, Svex<PathExt>> svexCache = new HashMap<>();
        int driverCount = 0;
        for (Assign<Address> assign : b.assigns)
        {
            Lhs<PathExt> lhs = assign.lhs.convertVars(rename, sm);
            Driver<PathExt> driver = assign.driver.convertVars(rename, sm, svexCache);
            DriverExt drv = new DriverExt(this, driver, "dr" + driverCount++);
            DriverExt old = assigns.put(lhs, drv);
            Util.check(old == null);

            int lsh = 0;
            for (Lhrange<PathExt> lhr : lhs.ranges)
            {
                Svar<PathExt> svar = lhr.getVar();
                if (svar.getName() instanceof WireExt)
                {
                    ((WireExt)svar.getName()).addDriver(lhr, lsh, drv);
                } else
                {
                    assert lhs.ranges.size() == 1 && lhr.getRsh() == 0 && lsh == 0;
//                    drv.setSource(lhr);
                    ((PathExt.PortInst)svar.getName()).setDriver(drv);
                }
                lsh += lhr.getWidth();
            }
            markAssigned(lhs, BigIntegerUtil.MINUS_ONE);
            drv.setSource(lhs);
            drv.markUsed();
            for (Svar<PathExt> svar : drv.getOrigVars())
            {
                if (svar.getDelay() != 0)
                {
                    Util.check(svar.getDelay() == 1);
                    WireExt lw = (WireExt)svar.getName();
                    stateWires.add(lw);
                }
            }
        }
        this.hasSvtvState = hasSvtvState || !stateWires.isEmpty();

        for (Aliaspair<Address> aliaspair : b.aliaspairs)
        {
            Lhs<PathExt> lhs = aliaspair.lhs.convertVars(rename, sm);
            Lhs<PathExt> rhs = aliaspair.rhs.convertVars(rename, sm);
            Util.check(lhs.ranges.size() == 1);
            Lhrange<PathExt> lhsRange = lhs.ranges.get(0);
            Util.check(lhsRange.getRsh() == 0);
            Svar<PathExt> lhsVar = lhsRange.getVar();
            Util.check(lhsVar.getDelay() == 0 && !lhsVar.isNonblocking());
            Lhs old = aliaspairs.put(lhs, rhs);
            Util.check(old == null);
            Util.check(lhs.width() == rhs.width());
            if (lhsVar.getName() instanceof PathExt.PortInst)
            {
                PathExt.PortInst pi = (PathExt.PortInst)lhsVar.getName();
                Util.check(lhsRange.getWidth() == pi.getWidth());
                int lsh = 0;
                for (Lhrange<PathExt> rhsRange : rhs.ranges)
                {
                    Svar<PathExt> rhsVar = rhsRange.getVar();
                    Util.check(rhsVar.getName() instanceof WireExt);
                    WireExt lw = (WireExt)rhsVar.getName();
                    if (pi.isOutput())
                    {
                        lw.addDriver(rhsRange, lsh, pi);
                    }
                    lsh += rhsRange.getWidth();
                }
                if (pi.isOutput())
                {
                    pi.setSource(rhs);
                    BigInteger assignedBits = pi.wire.getAssignedBits();
                    Util.check(assignedBits.equals(BigIntegerUtil.logheadMask(pi.getWidth())));
                    markAssigned(rhs, assignedBits);
                } else
                {
                    pi.setDriver(rhs);
                }
                if (pi.wire.used)
                {
                    for (Lhrange<PathExt> lr : rhs.ranges)
                    {
                        Svar<PathExt> name = lr.getVar();
                        if (name != null)
                        {
                            Util.check(name.getName() instanceof WireExt);
                            ((WireExt)name.getName()).markUsed();
                        }
                    }
                }
            } else
            {
                WireExt lw = (WireExt)lhsVar.getName();
                if (lw.getName().isString())
                {
                    Util.check(rhs.ranges.size() == 1);
                    PathExt.PortInst rhsPi = (PathExt.PortInst)rhs.ranges.get(0).getVar().getName();
                    Util.check(rhsPi.inst.getModname().isCoretype());
                    rhsPi.setDriver(lhs);
//                    System.out.println("lw string " + lw + " in " + modName);
                } else if (lw.getName().isInteger())
                {
                    Util.check(modName.isCoretype());
                    Util.check(rhs.ranges.size() == 1);
                    Lhrange<PathExt> rhsRange = rhs.ranges.get(0);
                    WireExt rhsLw = (WireExt)rhsRange.getVar().getName();
                    WireExt selfWire = rhsLw;
                    assert selfWire != null;
                    assert selfWire.index < lw.index;
                    assert lhsRange.getRsh() == 0;
                    lw.addDriver(lhsRange, rhsRange.getRsh(), selfWire);
                } else
                {
                    Util.check(false);
                }
            }
        }
        ParameterizedModule parMod = null;
        for (ParameterizedModule parModule : design.paremterizedModules)
        {
            if (parModule.setCurBuilder(modName, b.sm))
            {
                assert parMod == null;
                parMod = parModule;
                Module<Address> genM = parModule.genModule();
                if (genM == null)
                {
                    System.out.println("Module specializition is unfamiliar " + modName);
                } else if (!genM.equals(b))
                {
                    System.out.println("Module mismatch " + modName);
                } else
                {
                    Util.check(parModule.getNumInsts() == elabMod.modNInsts());
                    Util.check(parModule.getNumAssigns() == elabMod.modNAssigns());
                    Util.check(parModule.getTotalInsts() == elabMod.modTotalInsts());
                    Util.check(parModule.getTotalAssigns() == elabMod.modTotalAssigns());
                }
            }
        }
        this.parMod = parMod;
    }

    public static void markAssigned(Lhs<PathExt> lhs, BigInteger assignedBits)
    {
        for (Lhrange<PathExt> lr : lhs.ranges)
        {
            Svar<PathExt> name = lr.getVar();
            if (name != null)
            {
                BigInteger assignedBitsRange = BigIntegerUtil.loghead(lr.getWidth(), assignedBits);
                int rsh = lr.getRsh();
                assignedBitsRange = assignedBitsRange.shiftLeft(rsh);
                if (name.getName() instanceof WireExt)
                {
                    WireExt lw = (WireExt)name.getName();
                    lw.markAssigned(assignedBitsRange);
                } else
                {
                    Util.check(lhs.ranges.size() == 1);
                    Util.check(lr.getRsh() == 0);
                    PathExt.PortInst pi = (PathExt.PortInst)name.getName();
                    Util.check(assignedBitsRange.signum() >= 0 && assignedBitsRange.bitLength() <= pi.getWidth());
                    Util.check(pi.isInput());
//                    Util.check(assignedBitsRange.and(pi.wire.getAssignedBits()).signum() == 0);
                }
            }
            assignedBits = assignedBits.shiftRight(lr.getWidth());
        }
    }

    void markTop(String[] exportNames)
    {
        isTop = true;
        useCount = 1;
        List<String> exportList = null;
        if (exportNames != null)
        {
            exportList = Arrays.asList(exportNames);
        }
        int numExported = 0;
        for (WireExt w : wires)
        {
            if (exportList == null || exportList.indexOf(w.getName().toString()) >= 0)
            {
                ModExport export = makeExport(w);
                numExported++;
                if (w.getWidth() == 1 && w.getLowIdx() == 0)
                {
                    export.global = w.getName().toString();
                }
            }
        }
        Util.check(numExported == (exportNames != null ? exportNames.length : wires.size()));
    }

    void markDown(Map<String, Integer> globalCounts)
    {
        for (ModInstExt mi : insts)
        {
            mi.proto.useCount += useCount;
        }
        for (Map.Entry<Lhs<PathExt>, Lhs<PathExt>> e1 : aliaspairs.entrySet())
        {
            Lhs<PathExt> lhs = e1.getKey();
            Lhs<PathExt> rhs = e1.getValue();
            if (rhs.ranges.size() == 1
                && rhs.ranges.get(0).getWidth() == 1
                && rhs.ranges.get(0).getVar() != null
                && rhs.ranges.get(0).getRsh() == 0)
            {
                Svar<PathExt> svar = rhs.ranges.get(0).getVar();
                if (svar.getName() instanceof WireExt)
                {
                    WireExt w = ((WireExt)svar.getName());
                    ModExport export = w.getExport();
                    if (export != null && export.isGlobal()
                        && lhs.ranges.size() == 1
                        && lhs.ranges.get(0).getWidth() == 1
                        && lhs.ranges.get(0).getVar() != null
                        && rhs.ranges.get(0).getRsh() == 0)
                    {
                        Svar<PathExt> svar1 = lhs.ranges.get(0).getVar();
                        if (svar1.getName() instanceof PathExt.PortInst)
                        {
                            ((PathExt.PortInst)svar1.getName()).proto.markGlobal(export.global);
                        }
                    }
                }
            }
        }
        for (ModExport export : exports)
        {
            if (export.isGlobal())
            {
                Integer count = globalCounts.get(export.global);
                globalCounts.put(export.global, count == null ? 1 : count + 1);
            }
        }
    }

    ModExport makeExport(WireExt wire)
    {
        assert wire.parent == this && wires.get(wire.index) == wire;
        while (exports.size() <= wire.index)
        {
            exports.add(null);
        }
        ModExport export = exports.get(wire.index);
        if (export == null)
        {
            export = new ModExport(wire);
            wire.exported = export;
            exports.set(wire.index, export);
        }
        return export;
    }

    void checkExports()
    {
        boolean prevIsExport = true;
        for (WireExt wire : wires)
        {
            if (wire.isExport())
            {
                if (!prevIsExport)
                {
                    System.out.println("Module " + modName + " export " + wire + " is not at the beginning");
                    Util.check(false);
                    prevIsExport = true;
                }
            } else
            {
                prevIsExport = false;
            }
        }
        for (ModExport export : exports)
        {
            Util.check(export != null);
        }
        for (ModInstExt inst : insts)
        {
            inst.checkExports();
        }
    }

    public Svar<IndexName> absindexed(Svar<PathExt> x, SvexManager<IndexName> sm)
    {
        PathExt i = x.getName();
        int index = i.getIndexInParent();
        return sm.getVar(IndexName.valueOf(index), x.getDelay(), x.isNonblocking());
    }

    public Svex<IndexName> absindexed(Svex<PathExt> x, SvexManager<IndexName> sm,
        Map<Svex<PathExt>, Svex<IndexName>> svexCache)
    {
        Svex<IndexName> result = svexCache.get(x);
        if (result == null)
        {
            if (x instanceof SvexVar)
            {
                SvexVar<PathExt> xv = (SvexVar<PathExt>)x;
                Svar<IndexName> name = absindexed(xv.svar, sm);
                result = new SvexVar<>(name);
            } else if (x instanceof SvexQuote)
            {
                result = SvexQuote.valueOf(((SvexQuote<PathExt>)x).val);
            } else
            {
                SvexCall<PathExt> sc = (SvexCall<PathExt>)x;
                Svex<PathExt>[] args = sc.getArgs();
                Svex<IndexName>[] newArgs = Svex.newSvexArray(args.length);
                for (int i = 0; i < args.length; i++)
                {
                    newArgs[i] = absindexed(args[i], sm, svexCache);
                }
                result = sm.newCall(sc.fun, newArgs);
            }
            svexCache.put(x, result);
        }
        return result;
    }

    private Lhs<IndexName> absindexed(Lhs<PathExt> x, SvexManager<IndexName> sm)
    {
        List<Lhrange<IndexName>> newRanges = new ArrayList<>();
        for (Lhrange<PathExt> range : x.ranges)
        {
            Svar<PathExt> svar = range.getVar();
            Lhatom<IndexName> newAtom;
            if (svar != null)
            {
                Svar<IndexName> newSvar = absindexed(svar, sm);
                newAtom = Lhatom.valueOf(newSvar, range.getRsh());
            } else
            {
                newAtom = Lhatom.Z();
            }
            Lhrange<IndexName> newRange = new Lhrange<>(range.getWidth(), newAtom);
            newRanges.add(newRange);
        }
        return new Lhs<>(newRanges);
    }

    private PathExt findPathExtByIndex(int i)
    {
        if (i < wires.size())
        {
            return wires.get(i);
        }
        int instIndex = elabMod.wireFindInst(i);
        ModInstExt inst = insts.get(instIndex);
        return inst.portInsts.get(i - elabMod.getInst(instIndex).wireOffset);
    }

    private Svar<PathExt> indexedToNamedExt(Svar<IndexName> var)
    {
        int idx = var.getName().getIndex();
        PathExt name = findPathExtByIndex(idx);
        return sm.getVar(name);
    }

    public Lhs<PathExt> indexedToNamedExt(Lhs<IndexName> lhs)
    {
        List<Lhrange<PathExt>> newRanges = new ArrayList<>();
        for (Lhrange<IndexName> range : lhs.ranges)
        {
            Lhatom<PathExt> newAtom;
            Svar<IndexName> svar = range.getVar();
            if (svar == null)
            {
                newAtom = Lhatom.Z();
            } else
            {
                newAtom = Lhatom.valueOf(indexedToNamedExt(svar), range.getRsh());
            }
            newRanges.add(new Lhrange<>(range.getWidth(), newAtom));
        }
        return new Lhs<>(newRanges);
    }

    private Lhs<IndexName> flattenLhs(Lhs<PathExt> lhs, int offset, List<Lhs<IndexName>> portMap, SvexManager<IndexName> sm)
    {
        List<Lhrange<IndexName>> newRanges = new LinkedList<>();
        for (Lhrange<PathExt> range : lhs.ranges)
        {
            int wid = range.getWidth();
            int rsh = range.getRsh();
            Svar<PathExt> svar = range.getVar();
            assert svar.getDelay() == 0 && !svar.isNonblocking();
            int index = svar.getName().getIndexInParent();
            Lhs<IndexName> portLhs = index < portMap.size() ? portMap.get(index) : null;
            if (portLhs != null)
            {
                for (Lhrange<IndexName> portRange : portLhs.ranges)
                {
                    assert wid > 0;
                    int portWid = portRange.getWidth();
                    if (portWid > rsh)
                    {
                        Svar<IndexName> portSvar = portRange.getVar();
                        int w = Math.min(portWid - rsh, wid);
                        Lhrange<IndexName> newRange = rsh == 0 && w == portWid
                            ? portRange
                            : new Lhrange<>(w, Lhatom.valueOf(portSvar, portRange.getRsh() + rsh));
                        newRanges.add(newRange);
                        wid -= w;
                        rsh = 0;
                        if (wid <= 0)
                        {
                            break;
                        }
                    } else
                    {
                        rsh -= portWid;
                    }
                }
                assert rsh == 0 && wid == 0;
            } else
            {
                IndexName name = IndexName.valueOf(offset + index);
                Svar<IndexName> newSvar = sm.getVar(name);
                newRanges.add(new Lhrange<>(wid, Lhatom.valueOf(newSvar, rsh)));
            }
        }
        return new Lhs<>(newRanges).norm();
    }

    private void makeAliases(List<Lhs<IndexName>> portMap, List<Lhs<IndexName>> arr, SvexManager<IndexName> sm, boolean useParMods)
    {
        int wireOffset = arr.size();
        for (WireExt wire : wires)
        {
            arr.add(flattenLhs(wire.namedLhs, wireOffset, portMap, sm));
        }
        for (ModInstExt inst : insts)
        {
            List<Lhs<IndexName>> newPortMap = new LinkedList<>();
            for (PathExt.PortInst pi : inst.portInsts)
            {
                Lhs<IndexName> newLhs = flattenLhs(pi.namedLhs, wireOffset, portMap, sm);
                newPortMap.add(newLhs);
            }
            inst.proto.makeAliases(newPortMap, arr, sm, useParMods);
        }
    }

    private void testAliases(ModDb.FlattenResult flattenResult)
    {
        Map<ACL2Backed, ACL2Object> backedCache = new HashMap<>();
        List<Lhs<IndexName>> arr = new ArrayList<>();
        List<Lhs<IndexName>> topPortMap = new ArrayList<>();
        for (ModExport export : exports)
        {
            Lhs<IndexName> lhs = flattenLhs(export.wire.namedLhs, 0, Collections.emptyList(), flattenResult.sm);
            assert lhs.ranges.size() == 1 && lhs.ranges.get(0).getVar().getName().getIndex() == export.index;
            topPortMap.add(lhs);
        }
        makeAliases(topPortMap, arr, flattenResult.sm, true);
        Util.check(flattenResult.aliases.size() == arr.size());
        for (int i = 0; i < arr.size(); i++)
        {
            Lhs<IndexName> flatLhs = flattenResult.aliases.getAlias(i);
            Lhs<IndexName> hierLhs = arr.get(i);
            Util.check(flatLhs.equals(hierLhs));
            Util.check(flatLhs.getACL2Object(backedCache).equals(hierLhs.getACL2Object(backedCache)));
        }
    }

    private void makeNormAssignsSymbolic(int wireOffset, int assignOffset, List<Lhs<IndexName>> portMap,
        Map<Lhs<IndexName>, SymbolicDriver<IndexName>> normAssigns, SvexManager<IndexName> sm)
    {
        int assignI = 0;
        for (Map.Entry<Lhs<PathExt>, DriverExt> e : assigns.entrySet())
        {
            Lhs<PathExt> lhs = e.getKey();
            DriverExt drv = e.getValue();
            Lhs<IndexName> newLhs = flattenLhs(lhs, wireOffset, portMap, sm);
            List<Svar<PathExt>> drvVars = drv.getOrigVars();
            Lhs<IndexName>[] args = Lhs.newLhsArray(drvVars.size());
            for (int i = 0; i < drvVars.size(); i++)
            {
                Svar<PathExt> svar = drvVars.get(i);
                PathExt pathExt = svar.getName();
                args[i] = flattenLhs(pathExt.namedLhs, wireOffset, portMap, sm);
            }
            SymbolicDriver<IndexName> sDrv = new SymbolicDriver<>(drv, assignOffset + assignI, args, 0, drv.getWidth(), 0);
            normAssigns.put(newLhs, sDrv);
            assignI++;
        }
        for (ModInstExt inst : insts)
        {
            List<Lhs<IndexName>> newPortMap = new LinkedList<>();
            for (PathExt.PortInst pi : inst.portInsts)
            {
                Lhs<IndexName> newLhs = flattenLhs(pi.namedLhs, wireOffset, portMap, sm);
                newPortMap.add(newLhs);
            }
            inst.proto.makeNormAssignsSymbolic(wireOffset + inst.elabModInst.wireOffset,
                assignOffset + inst.elabModInst.assignOffset,
                newPortMap, normAssigns, sm);
        }
    }

    private void testNormAssigns(Compile<IndexName> compile, SvexManager<IndexName> sm)
    {
        Map<ACL2Backed, ACL2Object> backedCache = new HashMap<>();
        Map<Lhs<IndexName>, SymbolicDriver<IndexName>> normAssignsSym = new LinkedHashMap<>();
        makeNormAssignsSymbolic(0, 0, Collections.emptyList(), normAssignsSym, sm);
        Util.check(normAssignsSym.size() == compile.normAssigns.size());
        Iterator<Map.Entry<Lhs<IndexName>, SymbolicDriver<IndexName>>> iter1
            = normAssignsSym.entrySet().iterator();
        Iterator<Assign<IndexName>> iter2
            = compile.normAssigns.iterator();
        while (iter1.hasNext() || iter2.hasNext())
        {
            Map.Entry<Lhs<IndexName>, SymbolicDriver<IndexName>> e1 = iter1.next();
            Lhs<IndexName> lhs1 = e1.getKey();
            SymbolicDriver<IndexName> sDrv1 = e1.getValue();
            Assign<IndexName> e2 = iter2.next();
            Lhs<IndexName> lhs2 = e2.lhs;
            Driver<IndexName> drv2 = e2.driver;
            Util.check(lhs1.getACL2Object(backedCache).equals(lhs2.getACL2Object(backedCache)));
            Driver<IndexName> drv1 = sDrv1.makeWideDriver(sm);
            Util.check(drv1.getACL2Object(backedCache).equals(drv2.getACL2Object(backedCache)));
        }
        assert !iter1.hasNext() && !iter2.hasNext();
    }

    private void assignsToNetassigns(int wireOffset, int assignOffset, List<Lhs<IndexName>> portMap,
        Map<Lhs<IndexName>, SymbolicDriver<IndexName>> symNormAssignsRev,
        Map<Svar<IndexName>, List<SymbolicDriver<IndexName>>> symNetassignsRev,
        SvexManager<IndexName> sm)
    {
        for (int i = insts.size() - 1; i >= 0; i--)
        {
            ModInstExt inst = insts.get(i);

            List<Lhs<IndexName>> newPortMap = new LinkedList<>();
            for (PathExt.PortInst pi : inst.portInsts)
            {
                Lhs<IndexName> newLhs = flattenLhs(pi.namedLhs, wireOffset, portMap, sm);
                newPortMap.add(newLhs);
            }
            inst.proto.assignsToNetassigns(wireOffset + inst.elabModInst.wireOffset,
                assignOffset + inst.elabModInst.assignOffset,
                newPortMap,
                symNormAssignsRev, symNetassignsRev, sm);
        }
        List<Map.Entry<Lhs<PathExt>, DriverExt>> localAssignsEntries = new ArrayList<>(assigns.entrySet());
        for (int assignI = localAssignsEntries.size() - 1; assignI >= 0; assignI--)
        {
            Map.Entry<Lhs<PathExt>, DriverExt> e = localAssignsEntries.get(assignI);
            Lhs<PathExt> oldLhs = e.getKey();
            DriverExt oldDrv = e.getValue();
            Lhs<IndexName> lhs = flattenLhs(oldLhs, wireOffset, portMap, sm);
            List<Svar<PathExt>> drvVars = oldDrv.getOrigVars();
            Lhs<IndexName>[] args = Lhs.newLhsArray(drvVars.size());
            for (int j = 0; j < drvVars.size(); j++)
            {
                Svar<PathExt> svar = drvVars.get(j);
                PathExt pathExt = svar.getName();
                args[j] = flattenLhs(pathExt.namedLhs, wireOffset, portMap, sm);
            }
            assert lhs.isNormp();
            SymbolicDriver<IndexName> sDrv = new SymbolicDriver<>(oldDrv, assignOffset + assignI,
                args, 0, lhs.width(), 0);
            symNormAssignsRev.put(lhs, sDrv);
            int offset = lhs.width();
            for (int j = lhs.ranges.size() - 1; j >= 0; j--)
            {
                Lhrange<IndexName> range = lhs.ranges.get(j);
                offset -= range.getWidth();
                Svar<IndexName> svar = range.getVar();
                if (svar != null)
                {
                    sDrv = new SymbolicDriver<>(oldDrv, assignOffset + assignI,
                        args, range.getRsh(), range.getWidth(), offset);
                    List<SymbolicDriver<IndexName>> sDrivers = symNetassignsRev.get(svar);
                    if (sDrivers == null)
                    {
                        sDrivers = new LinkedList<>();
                        symNetassignsRev.put(svar, sDrivers);
                    }
                    sDrivers.add(sDrv);
                }
            }
        }
    }

    private void testAssignsToNetassigns(Compile<IndexName> compile, SvexManager<IndexName> sm)
    {
        Map<Lhs<IndexName>, SymbolicDriver<IndexName>> symNormAssignsRev = new LinkedHashMap<>();
        Map<Svar<IndexName>, List<SymbolicDriver<IndexName>>> symNetassignsRev = new LinkedHashMap<>();
        assignsToNetassigns(0, 0, Collections.emptyList(), symNormAssignsRev, symNetassignsRev, sm);

        List<Map.Entry<Lhs<IndexName>, SymbolicDriver<IndexName>>> symNormAssignsEntries
            = new ArrayList<>(symNormAssignsRev.entrySet());
        assert symNormAssignsEntries.size() == compile.normAssigns.size();
        int normI = symNormAssignsEntries.size();
        for (Assign<IndexName> assign1 : compile.normAssigns)
        {
            Lhs<IndexName> lhs1 = assign1.lhs;
            Driver<IndexName> drv1 = assign1.driver;
            Map.Entry<Lhs<IndexName>, SymbolicDriver<IndexName>> e2 = symNormAssignsEntries.get(--normI);
            Lhs<IndexName> lhs2 = e2.getKey();
            SymbolicDriver<IndexName> sDrv2 = e2.getValue();
            assert lhs1.equals(lhs2);
            assert lhs1.getACL2Object().equals(lhs2.getACL2Object());
            Driver<IndexName> drv2 = sDrv2.makeWideDriver(sm);
            assert drv1.equals(drv2);
            assert drv1.getACL2Object().equals(drv2.getACL2Object());
        }
        assert normI == 0;

        List<Map.Entry<Svar<IndexName>, List<SymbolicDriver<IndexName>>>> symNetassignsEntries
            = new ArrayList<>(symNetassignsRev.entrySet());
        assert symNetassignsEntries.size() == compile.resAssigns.size();
        int netI = symNetassignsEntries.size();
        Iterator<Map.Entry<Svar<IndexName>, Svex<IndexName>>> iter = compile.resAssigns.entrySet().iterator();
        for (Map.Entry<Svar<IndexName>, List<Driver<IndexName>>> e1 : compile.netAssigns.entrySet())
        {
            Svar<IndexName> svar1 = e1.getKey();
            List<Driver<IndexName>> l1 = e1.getValue();
            Map.Entry<Svar<IndexName>, List<SymbolicDriver<IndexName>>> e2 = symNetassignsEntries.get(--netI);
            Svar<IndexName> svar2 = e2.getKey();
            List<SymbolicDriver<IndexName>> l2 = e2.getValue();
            Util.check(svar1.equals(svar2));
            Map.Entry<Svar<IndexName>, Svex<IndexName>> e3 = iter.next();
            Svar<IndexName> svar3 = e3.getKey();
            Svex<IndexName> svex3 = e3.getValue();
            Util.check(svar1.equals(svar3));
            assert l1.size() == l2.size();
            assert !l1.isEmpty();
            Svex<IndexName> svexRes = null;
            for (int j = l1.size() - 1; j >= 0; j--)
            {
                Driver<IndexName> d1 = l1.get(j);
                SymbolicDriver<IndexName> d2 = l2.get(j);
                Driver<IndexName> newDrv = d2.makeDriver(sm);
                Util.check(d1.equals(newDrv));
                Util.check(newDrv.strength == Driver.DEFAULT_STRENGTH);
                if (svexRes == null)
                {
                    svexRes = newDrv.svex;
                } else
                {
                    svexRes = SvexCall.newCall(Vec4Res.FUNCTION, newDrv.svex, svexRes);
                }
            }
            Util.check(svexRes.equals(svex3));
            Util.check(svexRes.getACL2Object().equals(svex3.getACL2Object()));
        }
        assert netI == 0;
    }

    void testAliasesAndCompile()
    {
        IndexName.curElabMod = elabMod;
        ModDb.FlattenResult flattenResult = elabMod.svexmodFlatten(design.b.modalist);
        SvexManager<IndexName> sm = flattenResult.sm;
        Compile<IndexName> compile = new Compile(flattenResult.aliases.getArr(), flattenResult.assigns, sm);

        testAliases(flattenResult);
        testNormAssigns(compile, sm);
        testAssignsToNetassigns(compile, sm);
        IndexName.curElabMod = null;
    }

    void computeCombinationalInputs(String global)
    {
        checkExports();
        testAliasesAndCompile();
        computeDriverDeps(global, false);
        computeDriverDeps(global, true);
        for (Map.Entry<Svar<PathExt>, BigInteger> e : stateVars0.entrySet())
        {
            Svar<PathExt> svar = e.getKey();
            BigInteger mask0 = e.getValue();
            Util.check(svar.getDelay() == 1);
            WireExt lw = (WireExt)svar.getName();
            Util.check(stateWires.contains(lw));
            Util.check(mask0.equals(BigIntegerUtil.logheadMask(lw.getWidth())));
        }
        for (Map.Entry<Svar<PathExt>, BigInteger> e : stateVars1.entrySet())
        {
            Svar<PathExt> svar = e.getKey();
            BigInteger mask1 = e.getValue();
            Util.check(svar.getDelay() == 1);
            WireExt lw = (WireExt)svar.getName();
            Util.check(stateWires.contains(lw));
            Util.check(mask1.equals(BigIntegerUtil.logheadMask(lw.getWidth())));
        }
        hasPhaseState = !stateVars0.isEmpty() || !stateVars1.isEmpty();
        hasCycleState = !stateVars0.isEmpty();
        for (ModInstExt inst : insts)
        {
            if (inst.proto.hasPhaseState)
            {
                hasPhaseState = true;
            }
            if (inst.proto.hasCycleState)
            {
                hasCycleState = true;
            }
        }

        Map<Object, Set<Object>> fineGraph0 = computeFineDepsGraph(false);
        Map<Object, Set<Object>> fineGraph1 = computeFineDepsGraph(true);
        Map<Object, Set<Object>> fineClosure0 = closure(fineGraph0);
        Map<Object, Set<Object>> fineClosure1 = closure(fineGraph1);
//        System.out.println("=== " + modName + " fineDeps0");
//        showGraph(fineGraph0);
//        System.out.println("=== " + modName + " fineClosure0");
//        showGraph(fineClosure0);
//        System.out.println("=== " + modName + " fineDeps1");
//        showGraph(fineGraph1);
//        System.out.println("=== " + modName + " fineClosure1");
//        showGraph(fineClosure1);
        for (ModExport out : exports)
        {
            if (out.isOutput())
            {
                out.setFineDeps(false, fineClosure0);
                out.setFineDeps(true, fineClosure1);
            }
        }
        Map<Object, Set<Object>> fineTransdep0 = transdep(fineGraph0);
        Map<Object, Set<Object>> fineTransdep1 = transdep(fineGraph1);
        markInstancesToSplit(fineTransdep0, false);
        markInstancesToSplit(fineTransdep1, true);

        Map<Object, Set<Object>> crudeGraph0 = computeDepsGraph(false);
        Map<Object, Set<Object>> crudeGraph1 = computeDepsGraph(true);
        Map<Object, Set<Object>> crudeClosure0 = closure(crudeGraph0);
        Map<Object, Set<Object>> crudeClosure1 = closure(crudeGraph1);
        for (ModExport out : exports)
        {
            if (out.isOutput())
            {
                out.crudePortStateDep0 = gatherDep(out.crudePortDeps0, out.wire, crudeClosure0);
                out.crudePortStateDep1 = gatherDep(out.crudePortDeps1, out.wire, crudeClosure1);
            }
        }
    }

    private void computeDriverDeps(String global, boolean clkOne)
    {
        Map<Svar<PathExt>, Vec4> patchEnv = makePatchEnv(global, clkOne ? Vec2.ONE : Vec2.ZERO);
        Map<SvexCall<PathExt>, SvexCall<PathExt>> patchMemoize = new HashMap<>();
        for (Map.Entry<Lhs<PathExt>, DriverExt> e : assigns.entrySet())
        {
            Lhs<PathExt> l = e.getKey();
            DriverExt d = e.getValue();
            d.computeDeps(l.width(), clkOne, patchEnv, patchMemoize);
        }
    }

    private Map<Svar<PathExt>, Vec4> makePatchEnv(String global, Vec4 globalVal)
    {
        Map<Svar<PathExt>, Vec4> env = new HashMap<>();
        for (ModExport export : exports)
        {
            if (export.isGlobal() && export.global.equals(global))
            {
                env.put(export.wire.getVar(0), globalVal);
            }
        }
        return env;
    }

    Map<Object, Set<Object>> computeDepsGraph(boolean clockHigh)
    {
        Map<Object, Set<Object>> graph = new LinkedHashMap<>();
        for (WireExt w : wires)
        {
            if (!w.isInput())
            {
                BigInteger mask = BigIntegerUtil.logheadMask(w.getWidth());
                Set<Object> outputDeps = new LinkedHashSet<>();
                addWireDeps(w.getVar(0), mask, outputDeps);
                graph.put(w, outputDeps);
            }
        }
        for (ModInstExt mi : insts)
        {
            for (PathExt.PortInst piOut : mi.portInsts)
            {
                if (piOut.isOutput())
                {
                    if (piOut.splitIt)
                    {
                        BitSet fineBitStateDeps = piOut.proto.getFineBitStateDeps(clockHigh);
                        List<Map<Svar<PathExt>, BigInteger>> fineBitDeps = piOut.proto.getFineBitDeps(clockHigh);
                        for (int bit = 0; bit < piOut.getWidth(); bit++)
                        {
                            PathExt.Bit pb = piOut.getBit(bit);
                            boolean fineBitStateDep = fineBitStateDeps.get(bit);
                            Map<Svar<PathExt>, BigInteger> fineBitDep = fineBitDeps.get(bit);
                            putPortInsDeps(pb, mi, fineBitStateDep, fineBitDep, graph);
                        }
                    } else
                    {
//                        Set<WireExt> crudeCombinationalInputs = piOut.wire.getCrudeCombinationalInputs(clockHigh);
//                        Set<Object> deps = new LinkedHashSet<>();
//                        if (piOut.wire.getCrudeStateArg(clockHigh))
//                        {
//                            deps.add(STATE);
//                        }
//                        for (WireExt wire : crudeCombinationalInputs)
//                        {
//                            BigInteger mask = BigIntegerUtil.logheadMask(wire.getWidth());
//                            PathExt.PortInst piIn = mi.portInsts.get(wire.getName());
//                            addPortInDeps(piIn, mask, deps);
//                        }
//                        graph.put(piOut, deps);
                        boolean finePortStateDeps = piOut.proto.getCrudePortStateDeps(clockHigh);
                        Map<Svar<PathExt>, BigInteger> finePortDeps = piOut.proto.getCrudePortDeps(clockHigh);
                        putPortInsDeps(piOut, mi, finePortStateDeps, finePortDeps, graph);
                    }
                }
            }
        }
        for (Map.Entry<Lhs<PathExt>, DriverExt> e1 : assigns.entrySet())
        {
            Lhs<PathExt> l = e1.getKey();
            DriverExt d = e1.getValue();
            if (d.splitIt)
            {
//                assert l.ranges.size() == 1;
//                Lhrange<PathExt> lrange = l.ranges.get(0);
//                assert lrange.getRsh() == 0;
//                Svar<PathExt> svar = lrange.getVar();
//                assert svar.getDelay() == 0;
                List<Map<Svar<PathExt>, BigInteger>> fineDeps = d.getFineBitLocDeps(clockHigh);
                assert fineDeps.size() == l.width();
                for (int bit = 0; bit < l.width(); bit++)
                {
                    Map<Svar<PathExt>, BigInteger> fineDep = fineDeps.get(bit);
                    putVarMasksDeps(d.getBit(bit), fineDep, graph);
                }
            } else
            {
                assert !l.ranges.isEmpty();
                putVarMasksDeps(d, d.getCrudeDeps(clockHigh), graph);
            }
        }
        return graph;
    }

    private void putPortInsDeps(Object node, ModInstExt mi, boolean state, Map<Svar<PathExt>, BigInteger> portMasks, Map<Object, Set<Object>> graph)
    {
        Set<Object> deps = new LinkedHashSet<>();
        if (state)
        {
            deps.add(STATE);
        }
        for (Map.Entry<Svar<PathExt>, BigInteger> e : portMasks.entrySet())
        {
            Svar<PathExt> svar = e.getKey();
            BigInteger mask = e.getValue();
            WireExt lw = (WireExt)svar.getName();
            PathExt.PortInst piIn = mi.portInstsIndex.get(lw.getName());
            addPortInDeps(piIn, mask, deps);
        }
        graph.put(node, deps);
    }

    private void putVarMasksDeps(Object node, Map<Svar<PathExt>, BigInteger> varMasks, Map<Object, Set<Object>> graph)
    {
        Set<Object> deps = new LinkedHashSet<>();
        for (Map.Entry<Svar<PathExt>, BigInteger> e : varMasks.entrySet())
        {
            Svar<PathExt> svar = e.getKey();
            BigInteger mask = e.getValue();
            addWireDeps(svar, mask, deps);
        }
        graph.put(node, deps);
    }

    private void addPortInDeps(PathExt.PortInst piIn, BigInteger mask, Set<Object> deps)
    {
        if (piIn.driver instanceof DriverExt)
        {
            addDriverDeps(piIn.getDriverExt(), mask, deps);
        } else
        {
            addLhsDeps(piIn.getDriverLhs(), mask, deps);
        }
    }

    private void addLhsDeps(Lhs<PathExt> lhs, BigInteger mask, Set<Object> deps)
    {
        for (Lhrange<PathExt> lr : lhs.ranges)
        {
            BigInteger mask1 = BigIntegerUtil.loghead(lr.getWidth(), mask).shiftLeft(lr.getRsh());
            addWireDeps(lr.getVar(), mask1, deps);
            mask = mask.shiftRight(lr.getWidth());
        }
    }

    private void addWireDeps(Svar<PathExt> svar, BigInteger mask, Set<Object> deps)
    {
        if (mask.signum() == 0)
        {
            return;
        }
        if (svar.getDelay() != 0)
        {
            deps.add(STATE);
            return;
        }
        WireExt lw = (WireExt)svar.getName();
        if (lw.isInput())
        {
            for (int bit = 0; bit < lw.getWidth(); bit++)
            {
                if (mask.testBit(bit))
                {
                    deps.add(lw.getBit(bit));
                }
            }
            return;
        }
        for (Map.Entry<Lhrange<PathExt>, WireExt.WireDriver> e : lw.drivers.entrySet())
        {
            Lhrange<PathExt> lhr = e.getKey();
            WireExt.WireDriver wd = e.getValue();
            BigInteger mask1 = BigIntegerUtil.loghead(lhr.getWidth(), mask.shiftRight(lhr.getRsh()));
            if (lhr.getVar().getDelay() == 0 && mask1.signum() > 0)
            {
                if (wd.driver != null)
                {
                    addDriverDeps(wd.driver, mask1.shiftLeft(wd.lsh), deps);
                }
                if (wd.pi != null)
                {
                    assert wd.pi.isOutput();
                    if (wd.pi.splitIt)
                    {
                        for (int i = 0; i < lhr.getWidth(); i++)
                        {
                            if (mask1.testBit(i))
                            {
                                deps.add(wd.pi.getBit(wd.lsh + i));
                            }
                        }
                    } else
                    {
                        deps.add(wd.pi);
                    }
                }
                if (wd.inp != null)
                {
                    assert wd.inp.getExport().isInput();
                    for (int i = 0; i < lhr.getWidth(); i++)
                    {
                        if (mask1.testBit(i))
                        {
                            deps.add(wd.inp.getBit(wd.lsh + i));
                        }
                    }

                }

            }
        }
    }

    private void addDriverDeps(DriverExt driver, BigInteger mask, Set<Object> deps)
    {
        if (driver.splitIt)
        {
            for (int bit = 0; bit < driver.getWidth(); bit++)
            {
                if (mask.testBit(bit))
                {
                    deps.add(driver.getBit(bit));
                }
            }
        } else if (mask.signum() > 0)
        {
            deps.add(driver);
        }
    }

    Map<Object, Set<Object>> computeFineDepsGraph(boolean clockHigh)
    {
        Map<Object, Set<Object>> graph = new LinkedHashMap<>();
        for (WireExt w : wires)
        {
            if (!w.isInput())
            {
                for (Map.Entry<Lhrange<PathExt>, WireExt.WireDriver> e : w.drivers.entrySet())
                {
                    Lhrange<PathExt> range = e.getKey();
                    WireExt.WireDriver wd = e.getValue();
                    if (wd.inp != null)
                    {
                        assert wd.inp.getExport().isInput();
                        for (int bit = 0; bit < range.getWidth(); bit++)
                        {
                            graph.put(w.getBit(range.getRsh() + bit), Collections.singleton(wd.inp.getBit(wd.lsh + bit)));
                        }
                    }
                }
                BigInteger assignedBits = w.getAssignedBits();
                for (int bit = 0; bit < w.getWidth(); bit++)
                {
                    if (!assignedBits.testBit(bit))
                    {
                        PathExt.Bit pb = w.getBit(bit);
                        graph.put(pb, Collections.emptySet());
                    }
                }
            }
        }
        for (ModInstExt inst : insts)
        {
            for (PathExt.PortInst pi : inst.portInsts)
            {
                if (pi.isOutput())
                {
                    assert pi.source.width() == pi.getWidth();
                    assert pi.driver == null;
                    BitSet fineBitStateTransDeps = pi.proto.getFineBitStateDeps(clockHigh);
                    List<Map<Svar<PathExt>, BigInteger>> fineBitTransDeps = pi.proto.getFineBitDeps(clockHigh);
                    for (int bitOut = 0; bitOut < pi.getWidth(); bitOut++)
                    {
                        Set<Object> dep = new LinkedHashSet<>();
                        if (fineBitStateTransDeps.get(bitOut))
                        {
                            dep.add(STATE);
                        }
                        Map<Svar<PathExt>, BigInteger> fineBitDep = fineBitTransDeps.get(bitOut);
                        for (Map.Entry<Svar<PathExt>, BigInteger> e1 : fineBitDep.entrySet())
                        {
                            WireExt lw = (WireExt)e1.getKey().getName();
                            BigInteger mask = e1.getValue();
                            PathExt.PortInst piIn = inst.portInstsIndex.get(lw.getName());
                            Util.check(piIn != null);
                            for (int bitIn = 0; bitIn < piIn.getWidth(); bitIn++)
                            {
                                assert piIn.getBit(bitIn) != null;
                                if (mask.testBit(bitIn))
                                {
                                    dep.add(piIn.getBit(bitIn));
                                }
                            }
                        }
                        graph.put(pi.getBit(bitOut), dep);
                        graph.put(pi.getParentBit(bitOut), Collections.singleton(pi.getBit(bitOut)));
                    }

                } else
                {
                    assert pi.isInput();
                    assert pi.source == null;
                    if (pi.driver instanceof DriverExt)
                    {
                        for (int bit = 0; bit < pi.getWidth(); bit++)
                        {
                            Util.check(pi.getBit(bit) == pi.getParentBit(bit));
                        }
                    } else
                    {
                        assert pi.driver instanceof Lhs;
                        for (int bit = 0; bit < pi.getWidth(); bit++)
                        {
                            graph.put(pi.getBit(bit), Collections.singleton(pi.getParentBit(bit)));
                        }
                    }
                }
            }
        }
        for (DriverExt drv : assigns.values())
        {
            for (int bitDrv = 0; bitDrv < drv.getWidth(); bitDrv++)
            {
                Set<Object> dep = new LinkedHashSet<>();
                Map<Svar<PathExt>, BigInteger> varMasks = drv.getFineBitLocDeps(clockHigh).get(bitDrv);
                for (Map.Entry<Svar<PathExt>, BigInteger> e : varMasks.entrySet())
                {
                    Svar<PathExt> svar = e.getKey();
                    BigInteger maskIn = e.getValue();
                    if (svar.getDelay() == 0)
                    {
                        PathExt pathExt = svar.getName();
                        assert maskIn.signum() >= 0;
                        for (int bitIn = 0; bitIn < maskIn.bitLength(); bitIn++)
                        {
                            if (maskIn.testBit(bitIn))
                            {
                                PathExt.Bit pb = pathExt.getBit(bitIn);
                                assert pb != null;
                                dep.add(pb);
                            }
                        }
                    } else
                    {
                        dep.add(STATE);
                    }
                }
                graph.put(drv.getBit(bitDrv), dep);
            }
        }
        return graph;
    }

    private void markInstancesToSplit(Map<Object, Set<Object>> transdep, boolean clockHigh)
    {
        for (ModInstExt inst : insts)
        {
            for (PathExt.PortInst piOut : inst.portInsts)
            {
                if (piOut.isOutput())
                {
                    Set<Object> inputDeps = new HashSet<>();
                    Map<Svar<PathExt>, BigInteger> crudePortDeps = piOut.proto.getCrudePortDeps(clockHigh);
                    for (Map.Entry<Svar<PathExt>, BigInteger> e : crudePortDeps.entrySet())
                    {
                        Svar<PathExt> svar = e.getKey();
                        BigInteger mask = e.getValue();
                        WireExt lw = (WireExt)svar.getName();
                        PathExt.PortInst piIn = inst.portInstsIndex.get(lw.getName());
                        for (int bit = 0; bit < piIn.getWidth(); bit++)
                        {
                            Set<Object> deps = transdep.get(piIn.getBit(bit));
                            if (deps != null)
                            {
                                inputDeps.addAll(deps);
                            }
                        }
                    }
                    /*
                    Set<WireExt> crudeInputs = piOut.wire.getCrudeCombinationalInputs(clockHigh);
                    Set<Object> inputDeps = new HashSet<>();
                    for (WireExt crudeInput : crudeInputs)
                    {
                        PathExt.PortInst piIn = inst.portInsts.get(crudeInput.getName());
                        for (int bit = 0; bit < piIn.getWidth(); bit++)
                        {
                            Set<Object> deps = transdep.get(piIn.getBit(bit));
                            if (deps != null)
                            {
                                inputDeps.addAll(deps);
                            }
                        }
                    }
                     */
                    Set<PathExt.Bit> outDeps = new LinkedHashSet<>();
                    for (int bit = 0; bit < piOut.getWidth(); bit++)
                    {
                        if (inputDeps.contains(piOut.getBit(bit)))
                        {
                            outDeps.add(piOut.getProtoBit(bit));
                        }
                    }
                    if (!outDeps.isEmpty())
                    {
                        piOut.splitIt = true;
//                        System.out.println(modName + " " + piOut + " clock=" + (clockHigh ? "1" : "0") + " some combinational inputs depend on this output:");
//                        System.out.print("   ");
//                        for (WireExt.Bit wireBit : outDeps)
//                        {
//                            System.out.print(" " + wireBit);
//                        }
//                        System.out.println();
                    }
                }
            }
        }
        for (Map.Entry<Lhs<PathExt>, DriverExt> e : assigns.entrySet())
        {
            Lhs<PathExt> lhs = e.getKey();
            DriverExt drv = e.getValue();
            Set<Object> inputDeps = new HashSet<>();
            Map<Svar<PathExt>, BigInteger> crudeDeps = drv.getCrudeDeps(clockHigh);
            for (Map.Entry<Svar<PathExt>, BigInteger> e1 : crudeDeps.entrySet())
            {
                Svar<PathExt> svar = e1.getKey();
                BigInteger mask = e1.getValue();
                if (svar.getDelay() != 0)
                {
                    continue;
                }
                PathExt pathExt = svar.getName();
                for (int bit = 0; bit < pathExt.getWidth(); bit++)
                {
                    if (mask.testBit(bit))
                    {
                        Set<Object> deps = transdep.get(pathExt.getBit(bit));
                        if (deps != null)
                        {
                            inputDeps.addAll(deps);
                        }
                    }
                }
            }
            Set<PathExt.Bit> outDeps = new LinkedHashSet<>();
            for (int bit = 0; bit < lhs.width(); bit++)
            {
                PathExt.Bit pb = drv.getBit(bit);
                assert pb != null;
                if (inputDeps.contains(pb))
                {
                    outDeps.add(pb);
                }
            }
            if (!outDeps.isEmpty())
            {
                drv.splitIt = true;
//                System.out.println(modName + " " + drv.name + " clock=" + (clockHigh ? "1" : "0") + " some combinational inputs depend on these outputs:");
//                System.out.print("   ");
//                for (WireExt.Bit wireBit : outDeps)
//                {
//                    System.out.print(" " + wireBit);
//                }
//                System.out.println();
            }
        }
    }

    void markPortInstancesToSplit(String[] portInstancesToSplit)
    {
        for (String portInstanceToSplit : portInstancesToSplit)
        {
            int indexOfDot = portInstanceToSplit.indexOf('.');
            String instStr = portInstanceToSplit.substring(0, indexOfDot);
            String portStr = portInstanceToSplit.substring(indexOfDot + 1);
            Name instName = Name.fromACL2(ACL2Object.valueOf(instStr));
            Name portName = Name.fromACL2(ACL2Object.valueOf(portStr));
            ModInstExt inst = instsIndex.get(instName);
            PathExt.PortInst pi = inst.portInstsIndex.get(portName);
            pi.splitIt = true;
        }
    }

    void markDriversToSplit(int[] driversToSplit)
    {
        for (int driverToSplit : driversToSplit)
        {
            Iterator<DriverExt> it = assigns.values().iterator();
            for (int i = 0; i < driverToSplit; i++)
            {
                it.next();
            }
            it.next().splitIt = true;
        }
    }

    static Map<Svar<PathExt>, BigInteger> combineDeps(List<Map<Svar<PathExt>, BigInteger>> deps)
    {
        Map<Svar<PathExt>, BigInteger> result = new LinkedHashMap<>();
        for (Map<Svar<PathExt>, BigInteger> dep : deps)
        {
            for (Map.Entry<Svar<PathExt>, BigInteger> e : dep.entrySet())
            {
                Svar<PathExt> svar = e.getKey();
                BigInteger mask = e.getValue();
                if (mask.signum() > 0)
                {
                    BigInteger oldMask = result.get(svar);
                    if (oldMask == null)
                    {
                        oldMask = BigInteger.ZERO;
                    }
                    result.put(svar, oldMask.or(mask));
                }
            }
        }
        return result;
    }

    void showGraph(Map<Object, Set<Object>> graph)
    {
        for (Map.Entry<Object, Set<Object>> e : graph.entrySet())
        {
            System.out.print(e.getKey() + " <=");
            for (Object o : e.getValue())
            {
                System.out.print(" " + o);
            }
            System.out.println();
        }
    }

    public String showFinePortDeps(PathExt.Bit[] pathBits, Map<Object, Set<Object>> graph0, Map<Object, Set<Object>> graph1)
    {
        BitSet fineBitState0 = new BitSet();
        BitSet fineBitState1 = new BitSet();
        List<Map<Svar<PathExt>, BigInteger>> fineBitDeps0 = gatherFineBitDeps(fineBitState0, pathBits, graph0);
        List<Map<Svar<PathExt>, BigInteger>> fineBitDeps1 = gatherFineBitDeps(fineBitState1, pathBits, graph1);

        boolean fineState0 = !fineBitState0.isEmpty();
        Map<Svar<PathExt>, BigInteger> fineExportDeps0 = sortDeps(combineDeps(fineBitDeps0));
        boolean fineState1 = !fineBitState1.isEmpty();
        Map<Svar<PathExt>, BigInteger> fineExportDeps1 = sortDeps(combineDeps(fineBitDeps1));

        return showFineDeps(fineState0, fineExportDeps0, fineState1, fineExportDeps1);
    }

    public String showCrudePortDeps(Object node, Map<Object, Set<Object>> graph0, Map<Object, Set<Object>> graph1)
    {
        Map<Svar<PathExt>, BigInteger> dep0 = new LinkedHashMap<>();
        Map<Svar<PathExt>, BigInteger> dep1 = new LinkedHashMap<>();
        boolean stateDep0 = gatherDep(dep0, node, graph0);
        boolean stateDep1 = gatherDep(dep1, node, graph1);
        return showFineDeps(stateDep0, sortDeps(dep0), stateDep1, sortDeps(dep1));
    }

    public static String showFineDeps(
        BitSet stateDeps0,
        List<Map<Svar<PathExt>, BigInteger>> deps0,
        BitSet stateDeps1,
        List<Map<Svar<PathExt>, BigInteger>> deps1,
        int bit)
    {
        return showFineDeps(
            stateDeps0.get(bit), deps0.get(bit),
            stateDeps1.get(bit), deps1.get(bit));
    }

    public static String showFineDeps(
        boolean stateDep0,
        Map<Svar<PathExt>, BigInteger> dep0,
        boolean stateDep1,
        Map<Svar<PathExt>, BigInteger> dep1)
    {
        if (dep0.equals(dep1) && stateDep0 == stateDep1)
        {
            return showFineDeps(stateDep0, dep0);
        } else
        {
            return "0=>" + showFineDeps(stateDep0, dep0)
                + " | 1=>" + showFineDeps(stateDep1, dep1);
        }
    }

    private static String showFineDeps(boolean stateDep, Map<Svar<PathExt>, BigInteger> dep)
    {
        String s = stateDep ? "STATE" : "";
        for (Map.Entry<Svar<PathExt>, BigInteger> e : dep.entrySet())
        {
            Svar<PathExt> svar = e.getKey();
            BigInteger mask = e.getValue();
            if (!s.isEmpty())
            {
                s += ",";
            }
            s += svar.toString(mask);
        }
        return s;
    }

    List<Map<Svar<PathExt>, BigInteger>> gatherFineBitDeps(BitSet stateDeps, PathExt.Bit[] pathBits, Map<Object, Set<Object>> graph)
    {
        List<Map<Svar<PathExt>, BigInteger>> fineDeps = new ArrayList<>();
        stateDeps.clear();
        for (int bit = 0; bit < pathBits.length; bit++)
        {
            Map<Svar<PathExt>, BigInteger> fineDep = new LinkedHashMap<>();
            if (gatherDep(fineDep, pathBits[bit], graph))
            {
                stateDeps.set(bit);
            }
//            for (Object o : graph.get(pathBits[bit]))
//            {
//                if (o.equals(ModuleExt.STATE))
//                {
//                    stateDeps.set(bit);
//                    continue;
//                }
//                PathExt.Bit pbIn = (PathExt.Bit)o;
//                BigInteger mask = fineDep.get(pbIn.getPath().getVar(0));
//                if (mask == null)
//                {
//                    mask = BigInteger.ZERO;
//                }
//                fineDep.put(pbIn.getPath().getVar(0), mask.setBit(pbIn.bit));
//            }
            fineDeps.add(fineDep);
        }
        return fineDeps;
    }

    boolean gatherDep(Map<Svar<PathExt>, BigInteger> dep, Object node, Map<Object, Set<Object>> graph)
    {
        boolean state = false;
        dep.clear();
        for (Object o : graph.get(node))
        {
            if (o.equals(ModuleExt.STATE))
            {
                state = true;
            } else if (o instanceof PathExt)
            {
                PathExt pathExt = (PathExt)o;
                dep.put(pathExt.getVar(0), BigIntegerUtil.logheadMask(pathExt.getWidth()));
            } else if (o instanceof DriverExt)
            {
                DriverExt drv = (DriverExt)o;
                for (int bit = 0; bit < drv.getWidth(); bit++)
                {
                    gatherBitDep(dep, drv.getBit(bit));
                }
            } else
            {
                PathExt.Bit pb = (PathExt.Bit)o;
                gatherBitDep(dep, pb);
            }
        }
        return state;
    }

    void gatherBitDep(Map<Svar<PathExt>, BigInteger> dep, PathExt.Bit pb)
    {
        BigInteger mask = dep.get(pb.getPath().getVar(0));
        if (mask == null)
        {
            mask = BigInteger.ZERO;
        }
        dep.put(pb.getPath().getVar(0), mask.setBit(pb.bit));
    }

    Map<Svar<PathExt>, BigInteger> sortDeps(Map<Svar<PathExt>, BigInteger> deps)
    {
        Map<Svar<PathExt>, BigInteger> sortedDeps = new TreeMap<>(this);
        sortedDeps.putAll(deps);
        assert sortedDeps.size() == deps.size();
        return sortedDeps;
//        Map<Svar<PathExt>, BigInteger> sortedDeps = new LinkedHashMap<>();
//        for (WireExt wire : wires)
//        {
//            Svar<PathExt> svar = wire.getVar(1);
//            BigInteger mask = deps.get(svar);
//            if (mask != null && mask.signum() > 0)
//            {
//                sortedDeps.put(svar, mask);
//            }
//        }
//        for (WireExt wire : wires)
//        {
//            Svar<PathExt> svar = wire.getVar(0);
//            BigInteger mask = deps.get(svar);
//            if (mask != null && mask.signum() > 0)
//            {
//                sortedDeps.put(svar, mask);
//            }
//        }
//        for (Map.Entry<Svar<PathExt>, BigInteger> e : deps.entrySet())
//        {
//            Svar<PathExt> svar = e.getKey();
//            BigInteger mask = e.getValue();
//            assert mask.signum() > 0;
//            sortedDeps.put(svar, mask);
//        }
//        assert sortedDeps.equals(deps);
//        return sortedDeps;
    }

    Set<WireExt> sortWires(Set<WireExt> wires)
    {
        Set<WireExt> sortedWires = new LinkedHashSet<>();
        for (WireExt wire : this.wires)
        {
            if (wires.contains(wire))
            {
                sortedWires.add(wire);
            }
        }
        for (WireExt wire : wires)
        {
            sortedWires.add(wire);
        }
        assert sortedWires.equals(wires);
        return sortedWires;
    }

    Map<Object, Set<Object>> closure(Map<Object, Set<Object>> rel)
    {
        Map<Object, Set<Object>> closure = new HashMap<>();
        Set<Object> visited = new HashSet<>();
        for (Object key : rel.keySet())
        {
            closure(key, rel, closure, visited);
        }
        Util.check(closure.size() == visited.size());
        return closure;
    }

    private Set<Object> closure(Object top,
        Map<Object, Set<Object>> rel,
        Map<Object, Set<Object>> closure,
        Set<Object> visited)
    {
        Set<Object> ret = closure.get(top);
        if (ret == null)
        {
            boolean ok = visited.add(top);
            if (!ok)
            {
                System.out.println("CombinationalLoop!!! in " + top + " of " + modName);
                return Collections.singleton(top);
            }
            Set<Object> dep = rel.get(top);
            if (dep == null)
            {
                ret = Collections.singleton(top);
            } else
            {
                ret = new LinkedHashSet<>();
                for (Object svar : dep)
                {
                    ret.addAll(closure(svar, rel, closure, visited));
                }
            }
            closure.put(top, ret);
        }
        return ret;
    }

    Map<Object, Set<Object>> transdep(Map<Object, Set<Object>> rel)
    {
        Map<Object, Set<Object>> transdep = new HashMap<>();
        Set<Object> visited = new HashSet<>();
        for (Object key : rel.keySet())
        {
            transdep(key, rel, transdep, visited);
        }
        Util.check(transdep.size() == visited.size());
        return transdep;
    }

    private Set<Object> transdep(Object top,
        Map<Object, Set<Object>> rel,
        Map<Object, Set<Object>> transdep,
        Set<Object> visited)
    {
        Set<Object> ret = transdep.get(top);
        if (ret == null)
        {
            boolean ok = visited.add(top);
            if (!ok)
            {
                System.out.println("CombinationalLoop!!! in " + top + " of " + modName);
                return Collections.singleton(top);
            }
            Set<Object> dep = rel.get(top);
            if (dep == null)
            {
                ret = Collections.singleton(top);
            } else
            {
                ret = new LinkedHashSet<>();
                ret.add(top);
                for (Object svar : dep)
                {
                    ret.addAll(transdep(svar, rel, transdep, visited));
                }
            }
            transdep.put(top, ret);
        }
        return ret;
    }

    /*
    @Override
    public PathExt newName(ACL2Object nameImpl)
    {
        Path path = Path.fromACL2(nameImpl);
        if (path instanceof Path.Wire)
        {
            Path.Wire pathWire = (Path.Wire)path;
            return wiresIndex.get(pathWire.name);
        } else
        {
            Path.Scope pathScope = (Path.Scope)path;
//            Path.Wire subPathWire = (Path.Wire) pathScope.subpath;
            ModInstExt inst = instsIndex.get(pathScope.namespace);
            return inst.newPortInst(pathScope);
        }
    }
     */
    @Override
    public int compare(Svar<PathExt> o1, Svar<PathExt> o2)
    {
        if (o1.getDelay() > o2.getDelay())
            return -1;
        if (o1.getDelay() < o2.getDelay())
            return 1;
        PathExt p1 = o1.getName();
        PathExt p2 = o2.getName();
        if (p1 instanceof WireExt)
        {
            if (p2 instanceof WireExt)
            {
                WireExt lw1 = (WireExt)p1;
                WireExt lw2 = (WireExt)p2;
                return Integer.compare(lw1.index, lw2.index);
            } else
            {
                return -1;
            }
        } else
        {
            if (p2 instanceof WireExt)
            {
                return 1;
            } else
            {
                PathExt.PortInst pi1 = (PathExt.PortInst)p1;
                PathExt.PortInst pi2 = (PathExt.PortInst)p2;
                String s1 = pi1.inst.getInstname().toString();
                String s2 = pi2.inst.getInstname().toString();
                int res = s1.compareTo(s2);
                if (res != 0)
                {
                    return res;
                }
                return Integer.compare(pi1.proto.index, pi2.proto.index);
            }
        }
    }
}
