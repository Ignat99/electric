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
package com.sun.electric.tool.simulation.acl2.mods;

import com.sun.electric.tool.simulation.acl2.svex.Svar;
import com.sun.electric.tool.simulation.acl2.svex.SvarName;
import com.sun.electric.tool.simulation.acl2.svex.Svex;
import com.sun.electric.tool.simulation.acl2.svex.SvexManager;
import static com.sun.electric.util.acl2.ACL2.*;
import com.sun.electric.util.acl2.ACL2Backed;
import com.sun.electric.util.acl2.ACL2Object;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SV module.
 * See <http://www.cs.utexas.edu/users/moore/acl2/manuals/current/manual/?topic=SV____MODULE>.
 *
 * @param <N> Type of name of Svex variables
 */
public class Module<N extends SvarName>
{
    public final SvexManager<N> sm;

    public final List<Wire> wires = new ArrayList<>();
    public final List<ModInst> insts = new ArrayList<>();
    public final List<Assign<N>> assigns = new ArrayList<>();
    public final List<Aliaspair<N>> aliaspairs = new ArrayList<>();

    public Module(SvexManager<N> sm, Collection<Wire> wires, Collection<ModInst> insts,
        Collection<Assign<N>> assigns, Collection<Aliaspair<N>> aliaspairs)
    {
        this.sm = sm;
        this.wires.addAll(wires);
        this.insts.addAll(insts);
        this.assigns.addAll(assigns);
        this.aliaspairs.addAll(aliaspairs);
    }

    static <N extends SvarName> Module<N> fromACL2(SvarName.Builder<N> snb, ACL2Object impl)
    {
        SvexManager<N> sm = new SvexManager<>();
        List<ACL2Object> fields = Util.getList(impl, true);
        Util.check(fields.size() == 4);
        ACL2Object pair;

        pair = fields.get(0);
        Util.check(car(pair).equals(Util.SV_WIRES));
        List<Wire> wires = new ArrayList<>();
        for (ACL2Object o : Util.getList(cdr(pair), true))
        {
            Wire wire = new Wire(o);
            wires.add(wire);
        }

        pair = fields.get(1);
        Util.check(car(pair).equals(Util.SV_INSTS));
        List<ModInst> insts = new ArrayList<>();
        for (ACL2Object o : Util.getList(cdr(pair), true))
        {
            ModInst modInst = ModInst.fromACL2(o);
            insts.add(modInst);
        }
        pair = fields.get(2);
        Util.check(car(pair).equals(Util.SV_ASSIGNS));
        List<Assign<N>> assigns = new ArrayList<>();
        Map<ACL2Object, Svex<N>> svexCache = new HashMap<>();
        for (ACL2Object o : Util.getList(cdr(pair), true))
        {
            pair = o;
            Lhs<N> lhs = Lhs.fromACL2(snb, sm, car(pair));
            Driver<N> driver = Driver.fromACL2(snb, sm, cdr(pair), svexCache);
            Assign<N> assign = new Assign<>(lhs, driver);
            assigns.add(assign);
        }

        pair = fields.get(3);
        Util.check(car(pair).equals(Util.SV_ALIASPAIRS));
        List<Aliaspair<N>> aliaspairs = new ArrayList<>();
        for (ACL2Object o : Util.getList(cdr(pair), true))
        {
            pair = o;
            Lhs<N> lhs = Lhs.fromACL2(snb, sm, car(pair));
            Lhs<N> rhs = Lhs.fromACL2(snb, sm, cdr(pair));
            Aliaspair<N> aliaspair = new Aliaspair<>(lhs, rhs);
            aliaspairs.add(aliaspair);
        }

        return new Module<>(sm, wires, insts, assigns, aliaspairs);
    }

    public ACL2Object getACL2Object()
    {
        Map<ACL2Backed, ACL2Object> backedCache = new HashMap<>();
        ACL2Object wiresList = NIL;
        for (int i = wires.size() - 1; i >= 0; i--)
        {
            wiresList = cons(wires.get(i).getACL2Object(), wiresList);
        }
        ACL2Object instsList = NIL;
        for (int i = insts.size() - 1; i >= 0; i--)
        {
            instsList = cons(insts.get(i).getACL2Object(backedCache), instsList);
        }
        ACL2Object assignsList = NIL;
        for (int i = assigns.size() - 1; i >= 0; i--)
        {
            Assign<N> assign = assigns.get(i);
            assignsList = cons(cons(assign.lhs.getACL2Object(backedCache),
                assign.driver.getACL2Object(backedCache)), assignsList);
        }
        ACL2Object aliasesList = NIL;
        for (int i = aliaspairs.size() - 1; i >= 0; i--)
        {
            Aliaspair<N> aliaspair = aliaspairs.get(i);
            aliasesList = cons(cons(aliaspair.lhs.getACL2Object(backedCache),
                aliaspair.rhs.getACL2Object(backedCache)), aliasesList);
        }
        return cons(cons(Util.SV_WIRES, wiresList),
            cons(cons(Util.SV_INSTS, instsList),
                cons(cons(Util.SV_ASSIGNS, assignsList),
                    cons(cons(Util.SV_ALIASPAIRS, aliasesList),
                        NIL))));
    }

    @Override
    public boolean equals(Object o)
    {
        if (o instanceof Module)
        {
            Module<?> that = (Module<?>)o;
            return this.wires.equals(that.wires)
                && this.insts.equals(that.insts)
                && this.assigns.equals(that.assigns)
                && this.aliaspairs.equals(that.aliaspairs);
        }
        return false;
    }

    @Override
    public int hashCode()
    {
        int hash = 3;
        hash = 89 * hash + wires.hashCode();
        hash = 89 * hash + insts.hashCode();
        hash = 89 * hash + assigns.hashCode();
        hash = 89 * hash + aliaspairs.hashCode();
        return hash;
    }

    void vars(Collection<Svar<N>> vars)
    {
        for (Assign<N> assign : assigns)
        {
            assign.lhs.vars(vars);
            assign.driver.vars(vars);
        }
        for (Aliaspair<N> aliaspair : aliaspairs)
        {
            aliaspair.lhs.vars(vars);
            aliaspair.rhs.vars(vars);
        }
    }

}
