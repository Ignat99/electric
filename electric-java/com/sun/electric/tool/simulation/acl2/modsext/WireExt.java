/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: WireExt.java
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
import com.sun.electric.tool.simulation.acl2.mods.Wire;
import com.sun.electric.tool.simulation.acl2.mods.Wiretype;
import com.sun.electric.tool.simulation.acl2.svex.Svar;
import java.math.BigInteger;
import java.util.Collections;
import java.util.Comparator;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Wire info as stored in an svex module.
 * See <http://www.cs.utexas.edu/users/moore/acl2/manuals/current/manual/?topic=SV____WIRE>.
 */
public class WireExt extends PathExt
{
    public final Wire b;

    final int index;

    public boolean used;
    public ModExport exported;
    BigInteger assignedBits;
    final SortedMap<Lhrange<PathExt>, WireDriver> drivers = new TreeMap<>(LHRANGE_COMPARATOR);

    WireExt(ModuleExt parent, Wire b, int index)
    {
        super(parent, new Path.Wire(b.name), b.width);
        this.b = b;
        this.index = index;
        if (!b.name.isString())
        {
            Util.check(b.name.isInteger() || b.name.equals(Name.SELF));
            Util.check(parent.modName.isCoretype());
        }
        Util.check(b.delay == 0);
        Util.check(!b.revp);
//        if (b.low_idx != 0) {
//            System.out.println("Wire " + this + " in " + parent);
//        }
    }

    public Name getName()
    {
        return b.name;
    }

    public int getLowIdx()
    {
        return b.low_idx;
    }

    public int getDelay()
    {
        return b.delay;
    }

    public boolean isRev()
    {
        return b.revp;
    }

    public Wiretype getWiretype()
    {
        return b.wiretype;
    }

    public int getFirstIndex()
    {
        return b.getFirstIndex();
    }

    public int getSecondIndex()
    {
        return b.getSecondIndex();
    }

    @Override
    public WireExt getWire()
    {
        return this;
    }

    @Override
    int getIndexInParent()
    {
        return index;
    }

    public void markUsed()
    {
        used = true;
    }

    public String toString(int width, int rsh)
    {
        return b.toString(width, rsh);
    }

    @Override
    public String toString(BigInteger mask)
    {
        return b.toString(mask);
    }

    public String toLispString(int width, int rsh)
    {
        return b.toLispString(width, rsh);
    }

    @Override
    public boolean equals(Object o)
    {
        if (o instanceof WireExt)
        {
            WireExt that = (WireExt)o;
            return this.getName().equals(that.getName())
                && this.parent == that.parent;
        }
        return false;
    }

    @Override
    public String toString()
    {
        return b.toString();
    }

    public void markAssigned(BigInteger assignedBits)
    {
        if (assignedBits.signum() == 0)
        {
            return;
        }
        Util.check(assignedBits.signum() >= 0 && assignedBits.bitLength() <= getWidth());
        if (this.assignedBits == null)
        {
            this.assignedBits = BigInteger.ZERO;
        }
        if (assignedBits.and(this.assignedBits).signum() != 0)
        {
            System.out.println(this + " has multiple assignement");
        }
        this.assignedBits = this.assignedBits.or(assignedBits);
    }

    public boolean isAssigned()
    {
        return assignedBits != null;
    }

    public boolean isExport()
    {
        return exported != null;
    }

    public ModExport getExport()
    {
        return exported;
    }

    public boolean isInput()
    {
        return isExport() && !isAssigned();
    }

    public boolean isOutput()
    {
        return isExport() && isAssigned();
    }

    public BigInteger getAssignedBits()
    {
        return assignedBits != null ? assignedBits : BigInteger.ZERO;
    }

    /**
     * @param lr Lhrange from left-hand side. Must be this wire without delay
     * @param lsh offset in driver bits
     * @param driver either DriverExt or PathExt.PortInst
     */
    public void addDriver(Lhrange<PathExt> lr, int lsh, Object driver)
    {
        Svar<PathExt> svar = lr.getVar();
        assert svar.getDelay() == 0;
        WireExt lw = (WireExt)svar.getName();
        Util.check(lw == this);
        WireDriver wd;
        if (driver instanceof DriverExt)
        {
            wd = new WireDriver(lsh, (DriverExt)driver);
        } else if (driver instanceof PathExt.PortInst)
        {
            wd = new WireDriver(lsh, (PathExt.PortInst)driver);
        } else if (driver instanceof WireExt)
        {
            WireExt inp = (WireExt)driver;
            wd = new WireDriver(lsh, inp);
            for (int bit = 0; bit < lr.getWidth(); bit++)
            {
                lw.parentBits[lr.getRsh() + bit] = inp.getBit(bit);
            }
            Lhrange<PathExt> range = new Lhrange<>(width, Lhatom.valueOf(inp.getVar(0), lsh));
            namedLhs = new Lhs<>(Collections.singletonList(range));
        } else
        {
            throw new UnsupportedOperationException();
        }
        WireDriver old = drivers.put(lr, wd);
        Util.check(old == null);
    }

    /**
     * WireDriver is used together with Lhrange.
     * It says that Lhrange.width bits are driven either by
     * assignement driver[width+:lsh] or by port inst pi[width+:lsh] (without delay).
     */
    public static class WireDriver
    {
        public final int lsh;
        public final DriverExt driver;
        public final PathExt.PortInst pi;
        public final WireExt inp;

        WireDriver(int lsh, DriverExt driver)
        {
            this.lsh = lsh;
            this.driver = driver;
            pi = null;
            inp = null;
        }

        WireDriver(int lsh, PathExt.PortInst pi)
        {
            this.lsh = lsh;
            driver = null;
            this.pi = pi;
            inp = null;
        }

        WireDriver(int lsh, WireExt inp)
        {
            this.lsh = lsh;
            driver = null;
            pi = null;
            this.inp = inp;
        }
    }

    private static final Comparator<Lhrange> LHRANGE_COMPARATOR
        = (Lhrange o1, Lhrange o2) -> Integer.compare(o1.getRsh(), o2.getRsh());
}
