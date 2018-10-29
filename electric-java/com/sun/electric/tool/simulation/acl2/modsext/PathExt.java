/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Path.java
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

import com.sun.electric.tool.simulation.acl2.mods.Lhatom;
import com.sun.electric.tool.simulation.acl2.mods.Lhrange;
import com.sun.electric.tool.simulation.acl2.mods.Lhs;
import com.sun.electric.tool.simulation.acl2.mods.Name;
import com.sun.electric.tool.simulation.acl2.mods.Path;
import com.sun.electric.tool.simulation.acl2.mods.Util;
import com.sun.electric.tool.simulation.acl2.svex.BigIntegerUtil;
import com.sun.electric.tool.simulation.acl2.svex.Svar;
import com.sun.electric.tool.simulation.acl2.svex.SvarName;
import com.sun.electric.util.acl2.ACL2Object;
import java.math.BigInteger;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 */
public abstract class PathExt implements SvarName
{
    final Path b;

    final ModuleExt parent;
    final int width;
    private final Bit[] bits;
    final Bit[] parentBits;
    Lhs<PathExt> namedLhs;

    PathExt(ModuleExt parent, Path b, int width)
    {
        this.b = b;
        this.parent = parent;
        this.width = width;
        bits = new Bit[width];
        parentBits = new Bit[width];
        for (int bit = 0; bit < width; bit++)
        {
            parentBits[bit] = bits[bit] = new Bit(bit);
        }
        Lhrange<PathExt> range = new Lhrange<>(width, Lhatom.valueOf(parent.sm.getVar(this)));
        namedLhs = new Lhs<>(Collections.singletonList(range));
    }

    @Override
    public boolean isSimpleSvarName()
    {
        return b.isSimpleSvarName();
    }

    public String showFinePortDeps(Map<Object, Set<Object>> graph0, Map<Object, Set<Object>> graph1)
    {
        return parent.showFinePortDeps(bits, graph0, graph1);
    }

    List<Map<Svar<PathExt>, BigInteger>> gatherFineBitDeps(BitSet stateDeps, Map<Object, Set<Object>> graph)
    {
        return parent.gatherFineBitDeps(stateDeps, bits, graph);
    }

    @Override
    public int hashCode() // equals(object o) is default
    {
        return b.hashCode();
    }

    @Override
    public ACL2Object getACL2Object()
    {
        ACL2Object result = b.getACL2Object();
        assert result.hashCode() == hashCode();
        return b.getACL2Object();
    }

    @Override
    public String toString()
    {
        return toString(BigIntegerUtil.logheadMask(getWidth()));
    }

    public abstract WireExt getWire();

    abstract int getIndexInParent();

    public final int getWidth()
    {
        return width;
    }

    public Svar<PathExt> getVar(int delay)
    {
        return parent.sm.getVar(this, delay, false);
    }

    public Bit getBit(int bit)
    {
        return bits[bit];
    }

    Bit getParentBit(int bit)
    {
        return parentBits[bit];
    }

    public static class PortInst extends PathExt
    {
        public final ModExport proto;
        public final WireExt wire;
        public final ModInstExt inst;
        Lhs<PathExt> source;
        Object driver;
        public boolean splitIt;

        PortInst(ModInstExt inst, Path.Scope path, ModExport export)
        {
            super(inst.parent, path, export.wire.getWidth());
            this.proto = export;
            wire = export.wire;
            this.inst = inst;
            assert inst.proto == wire.parent;
        }

        @Override
        public WireExt getWire()
        {
            return wire;
        }

        @Override
        int getIndexInParent()
        {
            return inst.elabModInst.wireOffset + proto.index;
        }

        public boolean isInput()
        {
            return proto.isInput();
        }

        public boolean isOutput()
        {
            return proto.isOutput();
        }

        public Name getProtoName()
        {
            return wire.getName();
        }

        PathExt.Bit getProtoBit(int bit)
        {
            return wire.getBit(bit);
        }

        void setSource(Lhs<PathExt> source)
        {
            Util.check(driver == null);
            Util.check(this.source == null);
            this.source = source;
            setLhs(source);
        }

        void setDriver(DriverExt driver)
        {
            Util.check(source == null);
            Util.check(this.driver == null);
            this.driver = driver;
            for (int bit = 0; bit < getWidth(); bit++)
            {
                assert parentBits[bit] == getBit(bit);
            }
        }

        void setDriver(Lhs<PathExt> driver)
        {
            Util.check(source == null);
            Util.check(this.driver == null);
            this.driver = driver;
            setLhs((Lhs<PathExt>)driver);
        }

        private void setLhs(Lhs<PathExt> lhs)
        {
            Util.check(lhs.width() == getWidth());
            int lsh = 0;
            for (Lhrange<PathExt> range : lhs.ranges)
            {
                Svar<PathExt> svar = range.getVar();
                Util.check(svar.getDelay() == 0);
                WireExt lw = (WireExt)svar.getName();
                for (int i = 0; i < range.getWidth(); i++)
                {
                    parentBits[lsh + i] = lw.getBit(range.getRsh() + i);
                }
                lsh += range.getWidth();
            }
            assert lsh == getWidth();
            namedLhs = lhs;
        }

        DriverExt getDriverExt()
        {
            assert driver instanceof DriverExt;
            return (DriverExt)driver;
        }

        @SuppressWarnings("unchecked")
        Lhs<PathExt> getDriverLhs()
        {
            assert driver instanceof Lhs;
            return (Lhs<PathExt>)driver;
        }

//        @Override
//        public String showFinePortDeps(Map<Object, Set<Object>> graph0, Map<Object, Set<Object>> graph1)
//        {
//            return parent.showFineExportDeps(parentBits, graph0, graph1);
//        }
//
//        @Override
//        List<Map<Svar<PathExt>, BigInteger>> gatherFineBitDeps(BitSet stateDeps, Map<Object, Set<Object>> graph)
//        {
//            return parent.gatherFineBitDeps(stateDeps, parentBits, graph);
//        }
        @Override
        public String toString(BigInteger mask)
        {
            return inst.getInstname() + "." + wire.toString(mask);
        }
    }

    public class Bit
    {
        final int bit;

        private Bit(int bit)
        {
            this.bit = bit;
        }

        public PathExt getPath()
        {
            return PathExt.this;
        }

        @Override
        public String toString()
        {
            return PathExt.this.toString(BigInteger.ONE.shiftLeft(bit));
        }

        @Override
        public boolean equals(Object o)
        {
            if (o instanceof Bit)
            {
                Bit that = (Bit)o;
                return this.getPath().equals(that.getPath())
                    && this.bit == that.bit;
            }
            return false;
        }

        @Override
        public int hashCode()
        {
            int hash = 3;
            hash = 29 * hash + getPath().hashCode();
            hash = 29 * hash + bit;
            return hash;
        }
    }
}
