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

import com.sun.electric.tool.simulation.acl2.svex.Svar;
import com.sun.electric.tool.simulation.acl2.svex.SvarNameTexter;
import com.sun.electric.tool.simulation.acl2.svex.Svex;
import com.sun.electric.tool.simulation.acl2.svex.SvexCall;
import com.sun.electric.tool.simulation.acl2.svex.SvexManager;
import com.sun.electric.tool.simulation.acl2.svex.SvexQuote;
import com.sun.electric.tool.simulation.acl2.svex.SvexVar;
import static com.sun.electric.util.acl2.ACL2.*;
import com.sun.electric.util.acl2.ACL2Object;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * See<http://www.cs.utexas.edu/users/moore/acl2/manuals/current/manual/?topic=SV____ELAB-MOD>
 */
public class ElabMod
{
//    final int index;
    final ModName modName;
    final Wire[] wireTable;
    final Map<Name, Integer> wireNameIdxes = new HashMap<>();
    final int numBits;
    final ElabModInst[] modInstTable;
    final Map<Name, ElabModInst> modInstNameIdxes = new HashMap<>();
    final int totalWires;
    final int totalBits;
    final int totalInsts;
    final int totalAssigns; // Extension
    final Module<Address> origMod;
    final int modMeas;

    ElabMod(ModName modName, Module<Address> origMod, Map<ModName, ElabMod> modnameIdxes)
    {
//        this.index = index;
        this.modName = modName;
        this.origMod = origMod;
        wireTable = origMod.wires.toArray(new Wire[origMod.wires.size()]);
        int numBits = 0;
        for (int i = 0; i < wireTable.length; i++)
        {
            Wire wire = wireTable[i];
            Integer old = wireNameIdxes.put(wire.name, i);
            assert old == null;
            numBits += wire.width;
        }
        this.numBits = numBits;
        modInstTable = new ElabModInst[origMod.insts.size()];
        int wireOfs = wireTable.length;
        int bitOfs = numBits;
        int instOfs = modInstTable.length;
        int assignOfs = origMod.assigns.size();
        int meas = 1;
        for (int i = 0; i < origMod.insts.size(); i++)
        {
            ModInst modInst = origMod.insts.get(i);
            ElabMod modidx = modnameIdxes.get(modInst.modname);
            if (modidx == null)
            {
                throw new IllegalArgumentException();
            }
            ElabModInst elabModInst = new ElabModInst(i, modInst.instname, modidx, wireOfs, instOfs, assignOfs, bitOfs);
            modInstTable[i] = elabModInst;
            ElabModInst old = modInstNameIdxes.put(modInst.instname, elabModInst);
            assert old == null;
            wireOfs += modidx.totalWires;
            bitOfs += modidx.totalBits;
            instOfs += modidx.totalInsts;
            assignOfs += modidx.totalAssigns;
            meas += 1 + elabModInst.instMeas;
        }
        totalWires = wireOfs;
        totalBits = bitOfs;
        totalInsts = instOfs;
        totalAssigns = assignOfs;
        modMeas = meas;
    }

    public ModName modidxGetName()
    {
        return modName;
    }

    /**
     * See <http://www.cs.utexas.edu/users/moore/acl2/manuals/current/manual/?topic=SV____MODDB-MOD-NWIRES>
     *
     * @return number of local wires in the module
     */
    public int modNWires()
    {
        return wireTable.length;
    }

    /**
     * See <http://www.cs.utexas.edu/users/moore/acl2/manuals/current/manual/?topic=SV____MODDB-MOD-NINSTS>
     *
     * @return number of local insts in the module
     */
    public int modNInsts()
    {
        return modInstTable.length;
    }

    public int modNAssigns()
    {
        return origMod.assigns.size();
    }

    public int modNBits()
    {
        return numBits;
    }

    /**
     * See <http://www.cs.utexas.edu/users/moore/acl2/manuals/current/manual/?topic=SV____MODDB-MOD-TOTALWIRES>
     *
     * @return number of total wires in the module
     */
    public int modTotalWires()
    {
        return totalWires;
    }

    /**
     * See <http://www.cs.utexas.edu/users/moore/acl2/manuals/current/manual/?topic=SV____MODDB-MOD-TOTALINSTS>
     *
     * @return number of total instances in the module
     */
    public int modTotalInsts()
    {
        return totalInsts;
    }

    /**
     * @return number of total assigns in the module
     */
    public int modTotalAssigns()
    {
        return totalAssigns;
    }

    /**
     * @return number of total bits in the module
     */
    public int modTotalBits()
    {
        return totalBits;
    }

    public ElabModInst getInst(int instIndex)
    {
        return modInstTable[instIndex];
    }

    public int wireFindInst(int wire)
    {
        if (wire < wireTable.length || wire >= totalWires)
        {
            throw new IllegalArgumentException();
        }
        int minInst = 0;
        int maxInst = modInstTable.length;
        while (maxInst > 1 + minInst)
        {
            int guess = (maxInst - minInst) >> 1;
            int pivot = minInst + guess;
            int pivotOffset = modInstTable[pivot].wireOffset;
            if (wire < pivotOffset)
            {
                maxInst = pivot;
            } else
            {
                minInst = pivot;
            }
        }
        return minInst;
    }

    public int instFindInst(int inst)
    {
        if (inst < modInstTable.length || inst >= totalInsts)
        {
            throw new IllegalArgumentException();
        }
        int minInst = 0;
        int maxInst = modInstTable.length;
        while (maxInst > 1 + minInst)
        {
            int guess = (maxInst - minInst) >> 1;
            int pivot = minInst + guess;
            int pivotOffset = modInstTable[pivot].instOffset;
            if (inst < pivotOffset)
            {
                maxInst = pivot;
            } else
            {
                minInst = pivot;
            }
        }
        return minInst;
    }

    int assignFindInst(int assign)
    {
        if (assign < origMod.assigns.size() || assign >= totalAssigns)
        {
            throw new IllegalArgumentException();
        }
        int minInst = 0;
        int maxInst = modInstTable.length;
        while (maxInst > 1 + minInst)
        {
            int guess = (maxInst - minInst) >> 1;
            int pivot = minInst + guess;
            int pivotOffset = modInstTable[pivot].assignOffset;
            if (assign < pivotOffset)
            {
                maxInst = pivot;
            } else
            {
                minInst = pivot;
            }
        }
        return minInst;
    }

    /**
     * Convert a wire index to a path relative to the module it’s in
     * See <http://www.cs.utexas.edu/users/moore/acl2/manuals/current/manual/?topic=SV____MODDB-WIREIDX-_E3PATH>
     *
     * @param wireidx
     * @return
     */
    public Path wireidxToPath(int wireidx)
    {
        if (wireidx < 0 || wireidx >= totalWires)
        {
            throw new IllegalArgumentException();
        }
        List<Name> stack = new LinkedList<>();
        ElabMod elabMod = this;
        while (wireidx >= elabMod.wireTable.length)
        {
            int instIdx = elabMod.wireFindInst(wireidx);
            ElabModInst elabModInst = elabMod.modInstTable[instIdx];
            stack.add(elabModInst.instName);
            wireidx -= elabModInst.wireOffset;
            elabMod = elabModInst.modidx;
        }
        return Path.makePath(stack, elabMod.wireTable[wireidx].name);
    }

    public Path[] wireidxToPaths(int[] wires)
    {
        Path[] result = new Path[wires.length];
        for (int i = 0; i < wires.length; i++)
        {
            result[i] = wireidxToPath(wires[i]);
        }
        return result;
    }

    public ModInst instIndexToInstDecl(int instidx)
    {
        ElabMod elabMod = this;
        while (instidx >= elabMod.modInstTable.length)
        {
            int instIdx = elabMod.instFindInst(instidx);
            ElabModInst elabModInst = elabMod.modInstTable[instIdx];
            instidx -= elabModInst.instOffset;
            elabMod = elabModInst.modidx;
        }
        return elabMod.origMod.insts.get(instidx);
    }

    /**
     * See <http://www.cs.utexas.edu/users/moore/acl2/manuals/current/manual/?topic=SV____MODDB-PATH-_E3WIREIDX>
     *
     * @param path
     * @return
     */
    public int pathToWireIdx(Path path)
    {
        int wireOffset = 0;
        ElabMod elabMod = this;
        while (path instanceof Path.Scope)
        {
            Path.Scope pathScope = (Path.Scope)path;
            ElabModInst instidx = elabMod.modInstNameIdxes.get(pathScope.namespace);
            if (instidx == null)
            {
                throw new RuntimeException("In module " + elabMod.modName + ": missing: " + pathScope.namespace);
            }
            wireOffset += instidx.wireOffset;
            elabMod = instidx.modidx;
            path = pathScope.subpath;
        }
        Path.Wire pathWire = (Path.Wire)path;
        Integer wireIdx = elabMod.wireNameIdxes.get(pathWire.name);
        if (wireIdx == null)
        {
            throw new RuntimeException("In module " + elabMod.modName + ": missing: " + pathWire.name);
        }
        return wireOffset + wireIdx;
    }

    /**
     * See <http://www.cs.utexas.edu/users/moore/acl2/manuals/current/manual/?topic=SV____MODDB-PATH-_E3WIREIDX>
     *
     * @param path
     * @return
     */
    public Wire pathToWireDecl(Path path)
    {
        ElabMod elabMod = this;
        while (path instanceof Path.Scope)
        {
            Path.Scope pathScope = (Path.Scope)path;
            ElabModInst instidx = elabMod.modInstNameIdxes.get(pathScope.namespace);
            if (instidx == null)
            {
                throw new RuntimeException("In module " + elabMod.modName + ": missing: " + pathScope.namespace);
            }
            elabMod = instidx.modidx;
            path = pathScope.subpath;
        }
        Path.Wire pathWire = (Path.Wire)path;
        Integer wireIdx = elabMod.wireNameIdxes.get(pathWire.name);
        if (wireIdx == null)
        {
            throw new RuntimeException("In module " + elabMod.modName + ": missing: " + pathWire.name);
        }
        return elabMod.wireTable[wireIdx];
    }

    private String pathToString(Path path, int width, int rsh)
    {
        Wire wire = pathToWireDecl(path);
        String prefix = "";
        while (path instanceof Path.Scope)
        {
            Path.Scope ps = (Path.Scope)path;
            prefix += ps.namespace + ".";
            path = ps.subpath;
        }
        Path.Wire pw = (Path.Wire)path;
        assert pw.name.equals(wire.name);
        return prefix + wire.toString(width, rsh);
    }

    public SvarNameTexter<IndexName> getIndexNameTexter()
    {
        return new SvarNameTexter<IndexName>()
        {
            @Override
            public String toString(IndexName name, int width, int rsh)
            {
                return pathToString(wireidxToPath(name.getIndex()), width, rsh);
            }
        };
    }

    public SvarNameTexter<Path> getPathTexter()
    {
        return new SvarNameTexter<Path>()
        {
            @Override
            public String toString(Path path, int width, int rsh)
            {
                return pathToString(path, width, rsh);
            }
        };
    }

    public SvarNameTexter<Address> getAddressTexter()
    {
        return new SvarNameTexter<Address>()
        {
            @Override
            public String toString(Address address, int width, int rsh)
            {
                String s = pathToString(address.getPath(), width, rsh);
                if (address.index != Address.INDEX_NIL)
                {
                    s = "{" + address.index + "}" + s;
                }
                if (address.scope == Address.SCOPE_ROOT)
                {
                    s = "/" + s;
                } else
                {
                    for (int i = 0; i < address.scope; i++)
                    {
                        s = "../" + s;
                    }
                }
                return s;
            }
        };
    }

    public Svar<Address> svarNamedToIndexed(Svar<Address> svar, SvexManager<Address> sm)
    {
        Address addr = svar.getName();
        int idx = addr.scope != 0 ? Address.INDEX_NIL : pathToWireIdx(addr.getPath());
        if (addr.index == idx)
        {
            return svar;
        }
        Address newAddr = new Address(addr.getPath(), idx, addr.getScope());
        return sm.getVar(newAddr, svar.getDelay(), svar.isNonblocking());
    }

    private Svex<Address> svexNamedToIndex(Svex<Address> x, SvexManager<Address> sm, Map<Svex<Address>, Svex<Address>> svexCache)
    {
        Svex<Address> result = svexCache.get(x);
        if (result == null)
        {
            if (x instanceof SvexVar)
            {
                SvexVar<Address> xv = (SvexVar<Address>)x;
                Svar<Address> name = svarNamedToIndexed(xv.svar, sm);
                result = new SvexVar<>(name);
            } else if (x instanceof SvexQuote)
            {
                result = x;
            } else
            {
                SvexCall<Address> sc = (SvexCall<Address>)x;
                Svex<Address>[] args = sc.getArgs();
                Svex<Address>[] newArgs = Svex.newSvexArray(args.length);
                for (int i = 0; i < args.length; i++)
                {
                    newArgs[i] = svexNamedToIndex(args[i], sm, svexCache);
                }
                result = sm.newCall(sc.fun, newArgs);
            }
            svexCache.put(x, result);
        }
        return result;
    }

    private Lhs<Address> lhsNamedToIndex(Lhs<Address> x, SvexManager<Address> sm)
    {
        List<Lhrange<Address>> newRanges = new ArrayList<>();
        for (Lhrange<Address> range : x.ranges)
        {
            Svar<Address> svar = range.getVar();
            if (svar != null)
            {
                svar = svarNamedToIndexed(svar, sm);
                Lhatom<Address> atom = Lhatom.valueOf(svar, range.getRsh());
                range = new Lhrange<>(range.getWidth(), atom);
            }
            newRanges.add(range);
        }
        return new Lhs<>(newRanges);
    }

    Module<Address> moduleNamedToIndex(Module<Address> m)
    {
        SvexManager<Address> sm = new SvexManager<>();
        Map<Svex<Address>, Svex<Address>> svexCache = new HashMap<>();
        List<Assign<Address>> newAssigns = new ArrayList<>();
        for (Assign<Address> assign : m.assigns)
        {
            Lhs<Address> newLhs = lhsNamedToIndex(assign.lhs, sm);
            Driver<Address> driver = assign.driver;
            Svex<Address> newSvex = svexNamedToIndex(driver.svex, sm, svexCache);
            Driver<Address> newDriver = new Driver<>(newSvex, driver.strength);
            Assign<Address> newAssign = new Assign<>(newLhs, newDriver);
            newAssigns.add(newAssign);
        }
        List<Aliaspair<Address>> newAliasepairs = new ArrayList<>();
        for (Aliaspair<Address> aliaspair : m.aliaspairs)
        {
            Lhs<Address> newLhs = lhsNamedToIndex(aliaspair.lhs, sm);
            Lhs<Address> newRhs = lhsNamedToIndex(aliaspair.rhs, sm);
            Aliaspair<Address> newAliaspair = new Aliaspair<>(newLhs, newRhs);
            newAliasepairs.add(newAliaspair);
        }
        return new Module<>(sm, m.wires, m.insts, newAssigns, newAliasepairs);
    }

    void initializeAliases(int offset, SvexManager<IndexName> sm, LhsArr aliases)
    {
        for (Wire wire : wireTable)
        {
            IndexName name = IndexName.valueOf(offset);
            Svar<IndexName> svar = sm.getVar(name);
            Lhatom<IndexName> atom = Lhatom.valueOf(svar);
            Lhrange<IndexName> range = new Lhrange<>(wire.width, atom);
            Lhs<IndexName> lhs = new Lhs<>(Collections.singletonList(range));
            aliases.setAlias(offset, lhs);
            offset++;
        }
    }

    void initialAliases(int offset, SvexManager<IndexName> sm, LhsArr aliases)
    {
        initializeAliases(offset, sm, aliases);
        for (ElabModInst inst : modInstTable)
        {
            inst.modidx.initialAliases(offset + inst.wireOffset, sm, aliases);
        }
    }

    public ModDb.FlattenResult svexmodFlatten(Map<ModName, Module<Address>> modalist)
    {
        ElabMod.ModScope modScope = new ElabMod.ModScope(this);
        ModDb.FlattenResult result = new ModDb.FlattenResult();
        SvexManager<IndexName> sm = result.sm;
        modScope.svexmodFlatten(modalist, result);
        result.aliases = initialAliases(sm);
        result.aliases.canonicalizeAliasPairs(result.aliaspairs);
        return result;
    }

    LhsArr initialAliases(SvexManager<IndexName> sm)
    {
        LhsArr aliases = new LhsArr(totalWires);
        initialAliases(0, sm, aliases);
        return aliases;
    }

    public static class ElabModInst
    {
        final int instIndex;
        final Name instName;
        final ElabMod modidx;
        public final int wireOffset;
        final int instOffset;
        public final int assignOffset;
        final int bitOffset;
        final int instMeas;

        ElabModInst(int instIndex, Name instName, ElabMod modidx, int wireOffset, int instOffset, int assignOffset, int bitOffset)
        {
            this.instIndex = instIndex;
            this.instName = instName;
            this.modidx = modidx;
            this.wireOffset = wireOffset;
            this.instOffset = instOffset;
            this.assignOffset = assignOffset;
            this.bitOffset = bitOffset;
            instMeas = modidx.modMeas + 1;
        }
    }

    public static class ModScope
    {
        final ElabMod modIdx;
        final int wireOffset;
        final int instOffset;
        final int assignOffset;
        final int bitOffset;
        final ModScope upper;

        public ModScope(ElabMod modIdx)
        {
            this(modIdx, 0, 0, 0, 0, null);
        }

        private ModScope(ElabMod modIdx, int wireOffset, int instOffset, int assignOffset, int bitOffset, ModScope upper)
        {
            this.modIdx = modIdx;
            this.wireOffset = wireOffset;
            this.instOffset = instOffset;
            this.assignOffset = assignOffset;
            this.bitOffset = bitOffset;
            this.upper = upper;
        }

        public ElabMod getMod()
        {
            return modIdx;
        }

        boolean okp()
        {
//            if (mods.get(modIdx.index) != modIdx)
//            {
//                return false;
//            }
            if (upper == null)
            {
                return wireOffset == 0 && instOffset == 0;
            } else
            {
                return upper.okp()
                    && upper.wireOffset <= wireOffset
                    && modIdx.totalWires + wireOffset <= upper.modIdx.totalWires + upper.wireOffset
                    && upper.instOffset <= instOffset
                    && modIdx.totalInsts + instOffset >= upper.modIdx.totalInsts + upper.instOffset;
            }
        }

        ModScope pushFrame(int instidx)
        {
            ElabMod.ElabModInst elabModInst = modIdx.modInstTable[instidx];
            return new ModScope(elabModInst.modidx,
                elabModInst.wireOffset + wireOffset,
                elabModInst.instOffset + instOffset,
                elabModInst.assignOffset + assignOffset,
                elabModInst.bitOffset + bitOffset,
                this);
        }

        ModScope top()
        {
            ModScope result = this;
            while (result.upper != null)
            {
                result = result.upper;
            }
            return result;
        }

        ModScope nth(int n)
        {
            ModScope result = this;
            while (n > 0 && result.upper != null)
            {
                result = result.upper;
            }
            return result;
        }

        int addressToWireindex(Address addr)
        {
            ModScope scope1 = addr.scope == Address.SCOPE_ROOT ? top() : nth(addr.scope);
            return scope1.pathToWireindex(addr.path);
        }

        Wire addressToWiredecl(Address addr)
        {
            ModScope scope1 = addr.scope == Address.SCOPE_ROOT ? top() : nth(addr.scope);
            return scope1.pathToWiredecl(addr.path);
        }

        int pathToWireindex(Path path)
        {
            int localIdx = modIdx.pathToWireIdx(path);
            return localIdx + wireOffset;
        }

        Wire pathToWiredecl(Path path)
        {
            return modIdx.pathToWireDecl(path);
        }

        public int localBound()
        {
            return modIdx.wireTable.length;
        }

        public int totalBound()
        {
            return modIdx.totalWires;
        }

        public Svar<IndexName> absindexed(Svar<Address> x, SvexManager<IndexName> sm)
        {
            Address i = x.getName();
            int index = i.index >= 0 ? wireOffset + i.index : addressToWireindex(i);
            return sm.getVar(IndexName.valueOf(index), x.getDelay(), x.isNonblocking());
        }

        public Svex<IndexName> absindexed(Svex<Address> x, SvexManager<IndexName> sm,
            Map<Svex<Address>, Svex<IndexName>> svexCache)
        {
            Svex<IndexName> result = svexCache.get(x);
            if (result == null)
            {
                if (x instanceof SvexVar)
                {
                    SvexVar<Address> xv = (SvexVar<Address>)x;
                    Svar<IndexName> name = absindexed(xv.svar, sm);
                    result = new SvexVar<>(name);
                } else if (x instanceof SvexQuote)
                {
                    result = SvexQuote.valueOf(((SvexQuote<Address>)x).val);
                } else
                {
                    SvexCall<Address> sc = (SvexCall<Address>)x;
                    Svex<Address>[] args = sc.getArgs();
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

        private Lhs<IndexName> absindexed(Lhs<Address> x, SvexManager<IndexName> sm)
        {
            List<Lhrange<IndexName>> newRanges = new ArrayList<>();
            for (Lhrange<Address> range : x.ranges)
            {
                Svar<Address> svar = range.getVar();
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

        private void svexmodFlatten(Map<ModName, Module<Address>> modalist, ModDb.FlattenResult result)
        {
            SvexManager<IndexName> sm = result.sm;
            Map<Svex<Address>, Svex<IndexName>> svexCache = new HashMap<>();
            Module<Address> m = modIdx.origMod;
            for (Assign<Address> assign : m.assigns)
            {
                Lhs<IndexName> newLhs = absindexed(assign.lhs, sm);
                Driver<Address> driver = assign.driver;
                Svex<IndexName> newSvex = absindexed(driver.svex, sm, svexCache);
                Driver<IndexName> newDriver = new Driver<>(newSvex, driver.strength);
                Assign<IndexName> newAssign = new Assign<>(newLhs, newDriver);
                result.assigns.add(newAssign);
            }
            for (Aliaspair<Address> aliaspair : m.aliaspairs)
            {
                Lhs<IndexName> newLhs = absindexed(aliaspair.lhs, sm);
                Lhs<IndexName> newRhs = absindexed(aliaspair.rhs, sm);
                Aliaspair<IndexName> newAliaspair = new Aliaspair<>(newLhs, newRhs);
                result.aliaspairs.add(newAliaspair);
            }
            for (int instidx = 0; instidx < m.insts.size(); instidx++)
            {
                ModScope instScope = pushFrame(instidx);
                instScope.svexmodFlatten(modalist, result);
            }
        }

        private Svar<Path> indexedToPath(Svar<IndexName> var, SvexManager<Path> sm)
        {
            int idx = var.getName().getIndex();
            Path name = modIdx.wireidxToPath(idx);
            return sm.getVar(name);
        }

        private Svar<Address> indexedToAddress(Svar<IndexName> var, SvexManager<Address> sm)
        {
            int idx = var.getName().getIndex();
            Path path = modIdx.wireidxToPath(idx);
            Address address = new Address(path);
            return sm.getVar(address);
        }

        public Lhs<Path> indexedToPath(Lhs<IndexName> lhs, SvexManager<Path> sm)
        {
            List<Lhrange<Path>> newRanges = new ArrayList<>();
            for (Lhrange<IndexName> range : lhs.ranges)
            {
                Lhatom<Path> newAtom;
                Svar<IndexName> svar = range.getVar();
                if (svar == null)
                {
                    newAtom = Lhatom.Z();
                } else
                {
                    newAtom = Lhatom.valueOf(indexedToPath(svar, sm), range.getRsh());
                }
                newRanges.add(new Lhrange<>(range.getWidth(), newAtom));
            }
            return new Lhs<>(newRanges);
        }

        public Lhs<Address> indexedToAddress(Lhs<IndexName> lhs, SvexManager<Address> sm)
        {
            List<Lhrange<Address>> newRanges = new ArrayList<>();
            for (Lhrange<IndexName> range : lhs.ranges)
            {
                Lhatom<Address> newAtom;
                Svar<IndexName> svar = range.getVar();
                if (svar == null)
                {
                    newAtom = Lhatom.Z();
                } else
                {
                    newAtom = Lhatom.valueOf(indexedToAddress(svar, sm), range.getRsh());
                }
                newRanges.add(new Lhrange<>(range.getWidth(), newAtom));
            }
            return new Lhs<>(newRanges);
        }

        public List<Lhs<Path>> aliasesToPath(LhsArr aliases, SvexManager<Path> sm)
        {
            List<Lhs<Path>> result = new ArrayList<>(aliases.size());
            for (int i = 0; i < aliases.size(); i++)
            {
                result.add(ModScope.this.indexedToPath(aliases.getAlias(i), sm));
            }
            return result;
        }

        public List<Lhs<Address>> aliasesToAddress(LhsArr aliases, SvexManager<Address> sm)
        {
            List<Lhs<Address>> result = new ArrayList<>(aliases.size());
            for (int i = 0; i < aliases.size(); i++)
            {
                result.add(indexedToAddress(aliases.getAlias(i), sm));
            }
            return result;
        }

        public ACL2Object aliasesToNamedACL2Object(LhsArr aliases, SvexManager<Path> sm)
        {
            List<Lhs<Path>> namedAliases = aliasesToPath(aliases, sm);
            ACL2Object result = NIL;
            for (int i = namedAliases.size() - 1; i >= 0; i--)
            {
                result = cons(namedAliases.get(i).getACL2Object(), result);
            }
            return result;
        }
    }
}
