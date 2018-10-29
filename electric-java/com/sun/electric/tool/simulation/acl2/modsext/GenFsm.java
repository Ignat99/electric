/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: GenFsm.java
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

import com.sun.electric.tool.simulation.acl2.mods.Lhrange;
import com.sun.electric.tool.simulation.acl2.mods.Lhs;
import com.sun.electric.tool.simulation.acl2.mods.Name;
import com.sun.electric.tool.simulation.acl2.mods.Util;
import com.sun.electric.tool.simulation.acl2.svex.Svar;
import com.sun.electric.tool.simulation.acl2.svex.Svex;
import com.sun.electric.tool.simulation.acl2.svex.SvexCall;
import com.sun.electric.tool.simulation.acl2.svex.SvexQuote;
import com.sun.electric.tool.simulation.acl2.svex.SvexVar;
import com.sun.electric.tool.simulation.acl2.svex.Vec2;
import com.sun.electric.tool.simulation.acl2.svex.Vec4;
import static com.sun.electric.util.acl2.ACL2.*;
import com.sun.electric.util.acl2.ACL2Reader;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Generate next-state function of control block as ACL2 text
 */
public abstract class GenFsm extends GenBase
{

    private static final Vec4 X16 = Vec4.valueOf(BigInteger.valueOf(0xffff), BigInteger.valueOf(0));

    private String projName;
    private final Set<WireExt> knownWires = new HashSet<>();
    private final List<WireExt> sortedWires = new ArrayList<>();
    private final Map<WireExt, Map<Lhs<PathExt>, DriverExt>> wireDrivers = new HashMap<>();
    private final Map<WireExt, Set<WireExt>> wireDependencies = new HashMap<>();

    protected abstract boolean ignore_wire(WireExt w);

    private static WireExt searchWire(ModuleExt m, String name)
    {
        Name nm = Name.valueOf(name);
        for (WireExt w : m.wires)
        {
            if (w.getName().equals(nm))
            {
                return w;
            }
        }
        throw new RuntimeException();
    }

    private void topSort(WireExt w)
    {
        if (knownWires.contains(w))
        {
            return;
        }
        Set<WireExt> deps = wireDependencies.get(w);
        if (deps == null)
        {
            System.out.println("Unknown " + w);
        } else
        {
            for (WireExt wd : deps)
            {
                topSort(wd);
            }
        }
        knownWires.add(w);
        sortedWires.add(w);
    }

    private void genCurrentState(Name instname, int ffWidth, Lhrange<PathExt> lr, int rsh)
    {
        Svar<PathExt> svar = lr.getVar();
        WireExt lw = (WireExt)svar.getName();
        WireExt w = lw;
        if (lr.getWidth() != w.getWidth())
        {
            throw new UnsupportedOperationException();
        }
        String s = w.getName().toString();
        assert svar.getDelay() == 0;
        assert !svar.isNonblocking();
        s();
        s("(define " + s + "-ext");
        sb(" ((st " + projName + "-st-p)");
        sb("  (in " + projName + "-in-p))");
        e();
        s(" :returns (" + s + " unsigned-" + w.getWidth() + "-p)");
        s(" (declare (ignore in))");
        String ff = "(" + projName + "-st->" + instname.toString() + " st)";
        if (w.getWidth() == ffWidth)
        {
            // ff
        } else if (w.getWidth() == 1)
        {
            ff = "(logbit " + rsh + " " + ff + ")";
        } else if (rsh == 0)
        {
            ff = "(loghead " + w.getWidth() + " " + ff + ")";
        } else
        {
            ff = "(loghead " + w.getWidth() + " (logtail " + rsh + " " + ff + "))";
        }
        s(" " + ff + ")");
        e();
    }

    protected void showSvex(Svex<PathExt> sv)
    {
        if (sv instanceof SvexQuote)
        {
            Vec4 val = ((SvexQuote<PathExt>)sv).val;
            if (val.isVec2())
            {
                s("" + ((Vec2)val).getVal());
            } else
            {
                Util.check(val.equals(Vec4.X) || val.equals(X16));
                s("0");
            }
        } else if (sv instanceof SvexVar)
        {
            WireExt lw = (WireExt)((SvexVar<PathExt>)sv).svar.getName();
            WireExt w = lw;
            String s = w.getName().toString();
            s("(" + s + "-ext st in)");
        } else if (sv instanceof SvexCall)
        {
            SvexCall<PathExt> sc = (SvexCall<PathExt>)sv;
            String nm = symbol_name(sc.fun.fn).stringValueExact();
            String lnm = "<" + nm + ">";
            boolean boolBit = false;
            boolean neBit = false;
            switch (nm)
            {
                case "ID":
                    break;
                case "BITSEL":
                    break;
                case "UNFLOAT":
                    break;
                case "BITNOT":
                    lnm = "lognot";
                    break;
                case "BITAND":
                    lnm = "logand";
                    break;
                case "BITOR":
                    lnm = "logior";
                    break;
                case "BITXOR":
                    lnm = "logxor";
                    break;
                case "RES":
                    break;
                case "RESAND":
                    break;
                case "RESOR":
                    break;
                case "OVERRIDE":
                    break;
                case "ONP":
                    break;
                case "OFFP":
                    break;
                case "UAND":
                    break;
                case "UOR":
                    break;
                case "UXOR":
                    break;
                case "ZEROX":
                    lnm = "loghead";
                    break;
                case "SIGNX":
                    lnm = "loghead";
                    break;
                case "CONCAT":
                    lnm = "logapp";
                    break;
                case "BLKREV":
                    break;
                case "RSH":
                    break;
                case "LSH":
                    break;
                case "+":
                    lnm = "+";
                    break;
                case "B-":
                    lnm = "-";
                    break;
                case "U-":
                    break;
                case "*":
                    lnm = "*";
                    break;
                case "/":
                    break;
                case "%":
                    break;
                case "XDET":
                    break;
                case "<":
                    lnm = "<";
                    boolBit = true;
                    break;
                case "==":
                    lnm = "=";
                    boolBit = true;
                    break;
                case "===":
                    break;
                case "==?":
                    break;
                case "SAFER-==?":
                    break;
                case "==??":
                    break;
                case "CLOG2":
                    break;
                case "POW":
                    break;
                case "?":
                    lnm = "if";
                    neBit = true;
                    break;
                case "?*":
                    break;
                case "BIT?":
                    break;
                case "PARTSEL":
                    lnm = "partsel";
                    break;
                case "PARTINST":
                    break;
                default:
                    Util.check(false);
            }
            if (boolBit)
            {
                s("(bool->bit (" + lnm);
            } else
            {
                s("(" + lnm);
            }
            b();
            for (Svex<PathExt> arg : sc.getArgs())
            {
                if (neBit)
                {
                    s("(not (= ");
                    showSvex(arg);
                    out.print(" 0))");
                    neBit = false;
                } else
                {
                    showSvex(arg);
                }
            }
            out.print(')');
            if (boolBit)
            {
                out.print(')');
            }
            e();
        } else
        {
            assert false;
        }
    }

    protected void genDummyWireBody(String s, Map<Lhs<PathExt>, DriverExt> drv)
    {
        showSvex(drv.values().iterator().next().getOrigSvex());
    }

    private void genDummyWire(WireExt w)
    {
        String s = w.getName().toString();
        s();
        s("(define " + s + "-ext");
        sb("((st " + projName + "-st-p)");
        sb("(in " + projName + "-in-p))");
        e();
        s(" :returns (" + s + " unsigned-" + w.getWidth() + "-p)");
//        s(" (declare (ignore st in))");
        s(";");
        Set<WireExt> deps = wireDependencies.get(w);
        for (WireExt wd : deps)
        {
            out.print(" " + wd);
        }
        Map<Lhs<PathExt>, DriverExt> drv = wireDrivers.get(w);
        if (drv != null && !drv.isEmpty())
        {
            genDummyWireBody(s, drv);
        } else
        {
            s("0");
        }
        out.print(')');
        e();
    }

    protected String getGatedClock(String s)
    {
        return null;
    }

    protected String[] getGatedClocks()
    {
        return new String[]
        {
        };
    }

    private void genNextFlipFlop(Name instname, int ffWidth, Lhs<PathExt> r)
    {
        String s = instname.toString();
        s();
        s("(define " + s + "-next");
        sb("((st " + projName + "-st-p)");
        sb("(in " + projName + "-in-p))");
        e();
        s(":returns (" + s + " unsigned-" + ffWidth + "-p)");
        String clk = getGatedClock(s);
        if (clk != null)
        {
            s("(if (= (" + clk + "-ext st in) 1)");
            sb("(" + projName + "-st->" + s + " st)");
            e();
        }
        int rsh = ffWidth;
        for (int i = 0; i < r.ranges.size() - 1; i++)
        {
            Lhrange<PathExt> lr = r.ranges.get(r.ranges.size() - 1 - i);
            rsh -= lr.getWidth();
            sb("(logapp " + rsh);
        }
        assert rsh == r.ranges.get(0).getWidth();
        for (int i = r.ranges.size() - 1; i >= 0; i--)
        {
            Lhrange<PathExt> lr = r.ranges.get(r.ranges.size() - 1 - i);
            WireExt lw = (WireExt)lr.getVar().getName();
            String atomStr = "(" + lw.toString() + "-ext st in)";
            if (lr.getRsh() != 0)
            {
                atomStr = "(logtail " + lr.getRsh() + " " + atomStr + ")";
            }
            if (i == 0 && lr.getWidth() != lw.getWidth() - lr.getRsh())
            {
                atomStr = "(loghead " + lr.getWidth() + " " + atomStr + ")";
            }
            s(atomStr);
            if (i != r.ranges.size() - 1)
            {
                out.print(")");
            }
            if (i == 0)
            {
                out.print(")");
                if (clk != null)
                {
                    out.print(")");
                }
            }
            out.print(" ; " + lr);
            e();
        }
    }

    protected abstract boolean isFlipFlopIn(String modname, String wireName);

    protected abstract boolean isFlipFlopOut(String modname, String wireName);

    private void genNextState(ModuleExt m)
    {
        s("(define next-state");
        sb("((st " + projName + "-st-p)");
        sb("(in " + projName + "-in-p))");
        e();
        s(":returns (nst " + projName + "-st-p)");
        s("(make-" + projName + "-st");
        b();
        for (Map.Entry<Lhs<PathExt>, Lhs<PathExt>> e1 : m.aliaspairs.entrySet())
        {
            Lhs<PathExt> l = e1.getKey();
            assert l.ranges.size() == 1;
            Lhrange<PathExt> lr = l.ranges.get(0);
            assert lr.getRsh() == 0;
            if (lr.getVar().getName() instanceof PathExt.PortInst)
            {
                Svar<PathExt> svar = lr.getVar();
                PathExt.PortInst pi = (PathExt.PortInst)svar.getName();
                assert svar.getDelay() == 0;
                assert !svar.isNonblocking();
                ModInstExt inst = pi.inst;
                if (isFlipFlopOut(inst.getModname().toString(),
                    pi.getProtoName().toString()))
                {
                    String s = inst.getInstname().toString();
                    s(":" + s + " (" + s + "-next st in)");
                }
            }
        }
        out.print("))");
        e();
        e();
    }

    public void gen(ModuleExt m)
    {
        genAux();
        s();

        s("; Primary inputs");
        for (WireExt w : m.wires)
        {
            if (!w.isAssigned() && w.used && !ignore_wire(w))
            {
                knownWires.add(w);
                genInput(w);
            }
        }
        s();

        s("; Current state");
        for (Map.Entry<Lhs<PathExt>, Lhs<PathExt>> e1 : m.aliaspairs.entrySet())
        {
            Lhs<PathExt> l = e1.getKey();
            Lhs<PathExt> r = e1.getValue();
            assert l.ranges.size() == 1;
            Lhrange<PathExt> lr = l.ranges.get(0);
            assert lr.getRsh() == 0;
            Svar<PathExt> lVar = lr.getVar();
            if (lVar.getName() instanceof PathExt.PortInst)
            {
                PathExt.PortInst pi = (PathExt.PortInst)lVar.getName();
                assert lVar.getDelay() == 0;
                assert !lVar.isNonblocking();
                ModInstExt inst = pi.inst;
                int ffWidth = pi.getWidth();
                if (isFlipFlopOut(inst.getModname().toString(),
                    pi.getProtoName().toString()))
                {
                    int rsh = 0;
                    for (Lhrange<PathExt> lr1 : r.ranges)
                    {
                        Svar<PathExt> svar = lr1.getVar();
                        assert svar.getDelay() == 0;
                        assert !svar.isNonblocking();
                        WireExt lw = (WireExt)svar.getName();
                        knownWires.add(lw);
                        genCurrentState(inst.getInstname(), ffWidth, lr1, rsh);
                        rsh += lr1.getWidth();
                    }
                }
            }
        }
        out.println();

        for (Map.Entry<Lhs<PathExt>, DriverExt> e1 : m.assigns.entrySet())
        {
            Lhs<PathExt> l = e1.getKey();
            DriverExt d = e1.getValue();
            assert !l.ranges.isEmpty();
            for (int i = 0; i < l.ranges.size(); i++)
            {
                Lhrange<PathExt> lr = l.ranges.get(i);
                Svar<PathExt> svar = lr.getVar();
                WireExt lw = (WireExt)svar.getName();
//                if (svar.inst != null)
//                {
//                    System.out.println("Inst " + svar);
//                    continue;
//                }
                Map<Lhs<PathExt>, DriverExt> drv = wireDrivers.get(lw);
                if (drv == null)
                {
                    drv = new LinkedHashMap<>();
                    wireDrivers.put(lw, drv);
                }
                drv.put(l, d);
                Set<WireExt> dep = wireDependencies.get(lw);
                if (dep == null)
                {
                    dep = new LinkedHashSet<>();
                    wireDependencies.put(lw, dep);
                } else
                {
                    System.out.println("Twice " + lw);
                }
                List<Svar<PathExt>> deps = d.getOrigVars();
                for (Svar<PathExt> sv : deps)
                {
                    dep.add(lw);
                }
            }
        }

        for (Map.Entry<Lhs<PathExt>, Lhs<PathExt>> e1 : m.aliaspairs.entrySet())
        {
            Lhs<PathExt> l = e1.getKey();
            Lhs<PathExt> r = e1.getValue();
            assert l.ranges.size() == 1;
            Lhrange<PathExt> lr = l.ranges.get(0);
            assert lr.getRsh() == 0;
            Svar<PathExt> lVar = lr.getVar();
            if (lVar.getName() instanceof PathExt.PortInst)
            {
                PathExt.PortInst pi = (PathExt.PortInst)lVar.getName();
                assert lVar.getDelay() == 0;
                assert !lVar.isNonblocking();
                ModInstExt inst = pi.inst;
                if (isFlipFlopIn(inst.getModname().toString(),
                    pi.getProtoName().toString()))
                {
                    for (Lhrange<PathExt> lr1 : r.ranges)
                    {
                        Svar<PathExt> svar = lr1.getVar();
                        assert svar.getDelay() == 0;
                        assert !svar.isNonblocking();
                        WireExt lw = (WireExt)svar.getName();
                        topSort(lw);
                    }
                }
            }
        }
        for (String clock : getGatedClocks())
        {
            topSort(searchWire(m, clock));
        }

        s("; Wires");
        for (WireExt w : sortedWires)
        {
            genDummyWire(w);
        }
        s();

        s("; New state");
        for (Map.Entry<Lhs<PathExt>, Lhs<PathExt>> e1 : m.aliaspairs.entrySet())
        {
            Lhs<PathExt> l = e1.getKey();
            Lhs<PathExt> r = e1.getValue();
            assert l.ranges.size() == 1;
            Lhrange<PathExt> lr = l.ranges.get(0);
            assert lr.getRsh() == 0;
            Svar<PathExt> lVar = lr.getVar();
            if (lVar.getName() instanceof PathExt.PortInst)
            {
                PathExt.PortInst pi = (PathExt.PortInst)lVar.getName();
                assert lVar.getDelay() == 0;
                assert !lVar.isNonblocking();
                ModInstExt inst = pi.inst;
                int ffWidth = pi.getWidth();
                if (isFlipFlopIn(inst.getModname().toString(),
                    pi.getProtoName().toString()))
                {
                    genNextFlipFlop(inst.getInstname(), ffWidth, r);
                }
            }
        }
        s();

        genNextState(m);
        s();
    }

    public void gen(File saoFile, String outFileName) throws IOException
    {
        ACL2Reader sr = new ACL2Reader(saoFile);
        DesignExt design = new DesignExt(sr.root);
        ModuleExt m = design.downTop.get(design.getTop());
        projName = design.getTop().toString();
        try (PrintStream out = new PrintStream(outFileName))
        {
            this.out = out;
            gen(m);
        } finally
        {
            this.out = null;
        }
    }

    protected void genAux()
    {
        s("(in-package \"ACL2\")");
        s();
        s("(include-book \"" + projName + "-state\")");
        s("(local (include-book \"ihs/logops-lemmas\" :dir :system))");

        s();
        s("(define partsel");
        sb("((lsb natp)");
        sb("(width natp)");
        s("(in integerp))");
        e();
        s("(loghead width (logtail lsb in)))");
        e();
        s();
        s("(local (in-theory (disable unsigned-byte-p loghead logtail logapp lognot logior (tau-system))))");
        s();
    }

    protected void genInput(WireExt w)
    {
        String s = w.getName().toString();
        s();
        s("(define " + s + "-ext");
        sb("((st " + projName + "-st-p)");
        sb("(in " + projName + "-in-p))");
        e();
        s(":returns (" + s + " unsigned-" + w.getWidth() + "-p)");
        s("(declare (ignore st))");
        s("(" + projName + "-in->" + s + " in))");
        e();
    }
}
