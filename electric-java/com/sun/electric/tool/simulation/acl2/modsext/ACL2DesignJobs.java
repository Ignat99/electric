/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: ACL2DesignJobs.java
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

import com.sun.electric.tool.Job;
import com.sun.electric.tool.JobException;
import com.sun.electric.tool.simulation.acl2.mods.Address;
import com.sun.electric.tool.simulation.acl2.mods.Design;
import com.sun.electric.tool.simulation.acl2.mods.ElabMod;
import com.sun.electric.tool.simulation.acl2.mods.IndexName;
import com.sun.electric.tool.simulation.acl2.mods.Lhrange;
import com.sun.electric.tool.simulation.acl2.mods.Lhs;
import com.sun.electric.tool.simulation.acl2.mods.ModDb;
import com.sun.electric.tool.simulation.acl2.mods.ModName;
import com.sun.electric.tool.simulation.acl2.mods.Module;
import com.sun.electric.tool.simulation.acl2.mods.Path;
import com.sun.electric.tool.simulation.acl2.mods.Util;
import com.sun.electric.tool.simulation.acl2.svex.BigIntegerUtil;
import com.sun.electric.tool.simulation.acl2.svex.Svar;
import com.sun.electric.tool.simulation.acl2.svex.SvarName;
import com.sun.electric.tool.simulation.acl2.svex.Svex;
import com.sun.electric.tool.simulation.acl2.svex.SvexCall;
import com.sun.electric.tool.simulation.acl2.svex.SvexFunction;
import com.sun.electric.tool.simulation.acl2.svex.SvexManager;
import com.sun.electric.tool.simulation.acl2.svex.SvexQuote;
import com.sun.electric.tool.simulation.acl2.svex.SvexVar;
import com.sun.electric.tool.simulation.acl2.svex.Vec2;
import com.sun.electric.tool.simulation.acl2.svex.Vec4;
import com.sun.electric.tool.user.User;
import static com.sun.electric.util.acl2.ACL2.*;
import com.sun.electric.util.acl2.ACL2Backed;
import com.sun.electric.util.acl2.ACL2Object;
import com.sun.electric.util.acl2.ACL2Reader;
import com.sun.electric.util.acl2.ACL2Writer;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;

import java.io.PrintStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Dump serialized file with SVEX design
 */
public class ACL2DesignJobs
{
    private static final int VERBOSE_DUMP = 1;

    public static <H extends DesignHints> void dump(Class<H> cls, File saoFile, String outFileName)
    {
        new DumpDesignJob(cls, saoFile, outFileName).startJob();
    }

    private static class DumpDesignJob<H extends DesignHints> extends Job
    {
        private final Class<H> cls;
        private final File saoFile;
        private final String outFileName;

        private DumpDesignJob(Class<H> cls, File saoFile, String outFileName)
        {
            super("Dump SV Design", User.getUserTool(), Job.Type.SERVER_EXAMINE, null, null, Job.Priority.USER);
            this.cls = cls;
            this.saoFile = saoFile;
            this.outFileName = outFileName;
        }

        @Override
        public boolean doIt() throws JobException
        {
            try
            {
                ACL2Object.initHonsMananger(saoFile.getName());
                DesignHints designHints = cls.newInstance();
                ACL2Reader sr = new ACL2Reader(saoFile);
                DesignExt design = new DesignExt(sr.root, designHints);
                GenFsmNew gen = new GenFsmNew(designHints);
                gen.scanDesign(design.b);
                String clockName = designHints.getGlobalClock();
                design.computeCombinationalInputs(clockName);
                try (PrintStream out = new PrintStream(outFileName))
                {
                    for (Map.Entry<ParameterizedModule, Map<String, ModName>> e : gen.parModuleInstances.entrySet())
                    {
                        ParameterizedModule parMod = e.getKey();
                        Map<String, ModName> specializations = e.getValue();
                        dumpModules(out, design, parMod, specializations.values());
                    }
                    for (ModName modName : design.downTop.keySet())
                    {
                        if (gen.modToParMod.containsKey(modName))
                        {
                            continue;
                        }
                        dumpModules(out, design, null, Collections.singleton(modName));
                    }
                    ElabMod topMod = design.moddb.topMod();
                    out.println("// design.top=" + design.getTop());
                    out.println(topMod.modTotalWires() + " wires " + topMod.modTotalBits() + " bits");
                    if (clockName != null)
                    {
                        out.println("// clock=" + clockName);
                    }
                }
            } catch (InstantiationException | IllegalAccessException | IOException e)
            {
                return false;
            } finally
            {
                ACL2Object.closeHonsManager();
            }
            return true;
        }

        private void dumpModules(PrintStream out, DesignExt design, ParameterizedModule parMod, Collection<ModName> modNames)
        {
            for (ModName modName : modNames)
            {
                ModuleExt m = design.downTop.get(modName);
                Map<Object, Set<Object>> crudeGraph0 = m.computeDepsGraph(false);
                Map<Object, Set<Object>> crudeGraph1 = m.computeDepsGraph(true);
                Map<Object, Set<Object>> crudeClosure0 = m.closure(crudeGraph0);
                Map<Object, Set<Object>> crudeClosure1 = m.closure(crudeGraph1);
                Map<Object, Set<Object>> fineGraph0 = m.computeFineDepsGraph(false);
                Map<Object, Set<Object>> fineGraph1 = m.computeFineDepsGraph(true);
                Map<Object, Set<Object>> fineClosure0 = m.closure(fineGraph0);
                Map<Object, Set<Object>> fineClosure1 = m.closure(fineGraph1);

                out.println("module " + modName + " // has "
                    + m.wires.size() + " wires "
                    + m.insts.size() + " insts "
                    + m.assigns.size() + " assigns "
                    + m.aliaspairs.size() + " aliaspairs "
                    + m.useCount + " useCount");
                out.println(" wires");
                if (m.stateWires.isEmpty())
                {
                    assert m.stateVars0.isEmpty();
                    assert m.stateVars1.isEmpty();
                } else
                {
                    out.println("  // state wires: " + m.stateWires);
                    out.println("  // state " + ModuleExt.showFineDeps(false, m.stateVars0, false, m.stateVars1));
                }
                for (WireExt w : m.wires)
                {
                    if (w.isAssigned())
                    {
                        out.print(w.used ? "  out    " : "  output ");
                        if (w.isAssigned() && !BigIntegerUtil.logheadMask(w.getWidth()).equals(w.getAssignedBits()))
                        {
                            out.print("#x" + w.getAssignedBits().toString(16));
                        }
                    } else
                    {
                        Util.check(w.getAssignedBits().signum() == 0);
                        out.print(w.used ? "  input  " : "  unused ");
                    }
                    ModExport export = w.getExport();
                    out.print(export == null ? "  " : export.isGlobal() ? "! " : "* ");
                    out.print(w + " //");

                    for (Map.Entry<Lhrange<PathExt>, WireExt.WireDriver> e1 : w.drivers.entrySet())
                    {
                        Lhrange<PathExt> lhr = e1.getKey();
                        WireExt.WireDriver wd = e1.getValue();
                        out.print(" " + lhr + "<=");
                        if (wd.driver != null)
                        {
                            out.print(wd.driver.name);
                            if (lhr.getWidth() != wd.driver.getWidth() || wd.lsh != 0)
                            {
                                out.print("[" + (wd.lsh + lhr.getWidth() - 1) + ":" + wd.lsh + "]");
                            }
                        }
                        if (wd.pi != null)
                        {
                            out.print(wd.pi.toString(BigIntegerUtil.logheadMask(lhr.getWidth()).shiftLeft(wd.lsh)));
                        }
                        if (wd.inp != null)
                        {
                            out.print(wd.inp.toString(BigIntegerUtil.logheadMask(lhr.getWidth()).shiftLeft(wd.lsh)));
                        }
                    }
                    out.println();
//                    if (w.path.indexedLhs.ranges.size() > 1)
//                    {
//                        out.println("   // aliases-lhs " + w.path.namedLhs);
//                    }
                    if (!w.isInput())
                    {
                        if (w.isOutput())
                        {
                            String fineStr = w.showFinePortDeps(fineClosure0, fineClosure1);
                            String crudeStr = m.showCrudePortDeps(w, crudeClosure0, crudeClosure1);
                            out.println("   // fine  export depends* " + fineStr);
                            if (!fineStr.equals(crudeStr))
                            {
                                out.println("   // crude export depends* " + crudeStr);
                            }
//                            boolean fineStateDeps0 = w.getFinePortStateDeps(false);
//                            Map<Svar<PathExt>, BigInteger> fineDeps0 = w.getFinePortDeps(false);
//                            boolean fineStateDeps1 = w.getFinePortStateDeps(true);
//                            Map<Svar<PathExt>, BigInteger> fineDeps1 = w.getFinePortDeps(true);
//                            boolean crudeStateDeps0 = w.getCrudePortStateDeps(false);
//                            Map<Svar<PathExt>, BigInteger> crudeDeps0 = w.getCrudePortDeps(false);
//                            boolean crudeStateDeps1 = w.getCrudePortStateDeps(true);
//                            Map<Svar<PathExt>, BigInteger> crudeDeps1 = w.getCrudePortDeps(true);
//                            if (fineStateDeps0 != crudeStateDeps0 || !fineDeps0.keySet().equals(crudeDeps0.keySet())
//                                || fineStateDeps1 != crudeStateDeps1 || !fineDeps1.keySet().equals(crudeDeps1.keySet()))
//                            {
//                                out.println("Different Exports!!!");
//                            }
                        }
                        if (VERBOSE_DUMP >= 1)
                        {
                            out.println("    // fine  depends  on " + w.showFinePortDeps(fineGraph0, fineGraph1));
                            out.println("    // fine  depends* on " + w.showFinePortDeps(fineClosure0, fineClosure1));
                            out.println("    // crude depends  on " + m.showCrudePortDeps(w, crudeGraph0, crudeGraph1));
                            out.println("    // crude depends* on " + m.showCrudePortDeps(w, crudeClosure0, crudeClosure1));
//                            out.println("    // crude depends  on " + showCrude(crudeGraph0.get(w), crudeGraph1.get(w)));
//                            out.println("    // crude depends* on " + showCrude(crudeClosure0.get(w), crudeClosure1.get(w)));
                        }
                        if (VERBOSE_DUMP >= 2)
                        {
                            BitSet fineStateLocDeps0 = new BitSet();
                            BitSet fineStateLocDeps1 = new BitSet();
                            BitSet fineStateTransDeps0 = new BitSet();
                            BitSet fineStateTransDeps1 = new BitSet();
                            List<Map<Svar<PathExt>, BigInteger>> fineDeps0 = w.gatherFineBitDeps(fineStateLocDeps0, fineGraph0);
                            List<Map<Svar<PathExt>, BigInteger>> fineDeps1 = w.gatherFineBitDeps(fineStateLocDeps1, fineGraph1);
                            List<Map<Svar<PathExt>, BigInteger>> closureDeps0 = w.gatherFineBitDeps(fineStateTransDeps0, fineClosure0);
                            List<Map<Svar<PathExt>, BigInteger>> closureDeps1 = w.gatherFineBitDeps(fineStateTransDeps1, fineClosure1);
                            for (int bit = 0; bit < w.getWidth(); bit++)
                            {
                                PathExt.Bit pb = w.getBit(bit);
                                out.println("    // " + pb + " depends on "
                                    + ModuleExt.showFineDeps(fineStateLocDeps0, fineDeps0, fineStateLocDeps1, fineDeps1, bit));
                            }
                            for (int bit = 0; bit < w.getWidth(); bit++)
                            {
                                PathExt.Bit pb = w.getBit(bit);
                                out.println("    // " + pb + " depends* on "
                                    + ModuleExt.showFineDeps(fineStateTransDeps0, closureDeps0, fineStateTransDeps1, closureDeps1, bit));
                            }
                        }
                    }
                }
                out.println("// insts");
                for (ModInstExt mi : m.insts)
                {
                    out.println("  " + mi.getModname() + " " + mi.getInstname() + " (");
                    boolean hasExports = false;
                    for (PathExt.PortInst piIn : mi.portInsts)
                    {
                        if (!piIn.isInput())
                        {
                            continue;
                        }
                        if (hasExports)
                        {
                            out.print("   ,");
                        } else
                        {
                            out.print("    ");
                            hasExports = true;
                        }
                        out.println("." + piIn.getProtoName() + "(" + piIn.driver + ")");
                        if (VERBOSE_DUMP >= 1)
                        {
                            out.println("    // fine  depends  on " + piIn.showFinePortDeps(fineGraph0, fineGraph1));
                            out.println("    // fine  depends* on " + piIn.showFinePortDeps(fineClosure0, fineClosure1));
                        }
                        if (VERBOSE_DUMP >= 2)
                        {
                            BitSet fineStateLocDeps0 = new BitSet();
                            BitSet fineStateLocDeps1 = new BitSet();
                            BitSet fineStateTransDeps0 = new BitSet();
                            BitSet fineStateTransDeps1 = new BitSet();
                            List<Map<Svar<PathExt>, BigInteger>> fineDeps0 = piIn.gatherFineBitDeps(fineStateLocDeps0, fineGraph0);
                            List<Map<Svar<PathExt>, BigInteger>> fineDeps1 = piIn.gatherFineBitDeps(fineStateLocDeps1, fineGraph1);
                            List<Map<Svar<PathExt>, BigInteger>> closureDeps0 = piIn.gatherFineBitDeps(fineStateTransDeps0, fineClosure0);
                            List<Map<Svar<PathExt>, BigInteger>> closureDeps1 = piIn.gatherFineBitDeps(fineStateTransDeps1, fineClosure1);
                            for (int bit = 0; bit < piIn.getWidth(); bit++)
                            {
                                PathExt.Bit pb = piIn.getBit(bit);
                                out.println("    // fine  " + pb + " depends  on "
                                    + ModuleExt.showFineDeps(fineStateLocDeps0, fineDeps0, fineStateLocDeps1, fineDeps1, bit));
//                                if (piOut.splitIt)
//                                {
//                                    out.println("    // crude " + pb + " depends  on " + showCrude(crudeGraph0.get(pb), crudeGraph1.get(pb)));
//                                }
                            }
                            for (int bit = 0; bit < piIn.getWidth(); bit++)
                            {
                                PathExt.Bit pb = piIn.getBit(bit);
                                out.println("    // fine  " + pb + " depends* on "
                                    + ModuleExt.showFineDeps(fineStateLocDeps0, closureDeps0, fineStateLocDeps1, closureDeps1, bit));
//                                if (piOut.splitIt)
//                                {
//                                    out.println("    // crude " + pb + " depends* on " + showCrude(crudeClosure0.get(pb), crudeClosure1.get(pb)));
//                                }
                            }
                        }
                    }
                    out.println("   //");
                    for (PathExt.PortInst piOut : mi.portInsts)
                    {
                        if (!piOut.isOutput())
                        {
                            continue;
                        }
                        if (hasExports)
                        {
                            out.print("   ,");
                        } else
                        {
                            out.print("    ");
                            hasExports = true;
                        }
                        out.println("." + piOut.getProtoName() + "(" + piOut.source + ")");
                        if (piOut.splitIt)
                        {
                            out.println("    // SPLIT");
                        }
                        if (VERBOSE_DUMP >= 1)
                        {
                            out.println("    // fine  depends  on " + piOut.showFinePortDeps(fineGraph0, fineGraph1));
                            out.println("    // fine  depends* on " + piOut.showFinePortDeps(fineClosure0, fineClosure1));
                            if (!piOut.splitIt)
                            {
                                out.println("    // crude depends  on " + m.showCrudePortDeps(piOut, crudeGraph0, crudeGraph1));
                                out.println("    // crude depends* on " + m.showCrudePortDeps(piOut, crudeClosure0, crudeClosure1));
//                                out.println("    // crude depends  on " + showCrude(crudeGraph0.get(piOut), crudeGraph1.get(piOut)));
//                                out.println("    // crude depends* on " + showCrude(crudeClosure0.get(piOut), crudeClosure1.get(piOut)));
                            }
                        }
                        if (VERBOSE_DUMP >= 2 || VERBOSE_DUMP >= 1 && piOut.splitIt)
                        {
                            BitSet fineStateLocDeps0 = new BitSet();
                            BitSet fineStateLocDeps1 = new BitSet();
                            BitSet fineStateTransDeps0 = new BitSet();
                            BitSet fineStateTransDeps1 = new BitSet();
                            List<Map<Svar<PathExt>, BigInteger>> fineDeps0 = piOut.gatherFineBitDeps(fineStateLocDeps0, fineGraph0);
                            List<Map<Svar<PathExt>, BigInteger>> fineDeps1 = piOut.gatherFineBitDeps(fineStateLocDeps1, fineGraph1);
                            List<Map<Svar<PathExt>, BigInteger>> closureDeps0 = piOut.gatherFineBitDeps(fineStateTransDeps0, fineClosure0);
                            List<Map<Svar<PathExt>, BigInteger>> closureDeps1 = piOut.gatherFineBitDeps(fineStateTransDeps1, fineClosure1);
                            for (int bit = 0; bit < piOut.getWidth(); bit++)
                            {
                                PathExt.Bit pb = piOut.getBit(bit);
                                out.println("    // fine  " + pb + " depends  on "
                                    + ModuleExt.showFineDeps(fineStateLocDeps0, fineDeps0, fineStateLocDeps1, fineDeps1, bit));
                                if (piOut.splitIt)
                                {
                                    out.println("    // crude " + pb + " depends  on "
                                        + m.showCrudePortDeps(pb, crudeGraph0, crudeGraph1));
//                                    out.println("    // crude " + pb + " depends  on " + showCrude(crudeGraph0.get(pb), crudeGraph1.get(pb)));
                                }
                            }
                            for (int bit = 0; bit < piOut.getWidth(); bit++)
                            {
                                PathExt.Bit pb = piOut.getBit(bit);
                                out.println("    // fine  " + pb + " depends* on "
                                    + ModuleExt.showFineDeps(fineStateLocDeps0, closureDeps0, fineStateLocDeps1, closureDeps1, bit));
                                if (piOut.splitIt)
                                {
                                    out.println("    // crude " + pb + " depends* on "
                                        + m.showCrudePortDeps(pb, crudeClosure0, crudeClosure1));
//                                    out.println("    // crude " + pb + " depends* on " + showCrude(crudeClosure0.get(pb), crudeClosure1.get(pb)));
                                }
                            }
                        }
                    }
                    out.println("  );");
                }
                out.println(" assigns");
                for (Map.Entry<Lhs<PathExt>, DriverExt> e1 : m.assigns.entrySet())
                {
                    Lhs<PathExt> l = e1.getKey();
                    DriverExt d = e1.getValue();
                    assert !l.ranges.isEmpty();
                    for (int i = 0; i < l.ranges.size(); i++)
                    {
                        Lhrange<PathExt> lr = l.ranges.get(i);
                        Svar<PathExt> svar = lr.getVar();
                        assert svar.getDelay() == 0;
                        assert !svar.isNonblocking();
                        out.print((i == 0 ? "  " : ",") + lr);
                    }

                    out.print(" = " + d.getOrigSvex());
                    BigInteger complexity = d.getOrigSvex().traverse(new Svex.TraverseVisitor<PathExt, BigInteger>()
                    {
                        @Override
                        public BigInteger visitQuote(Vec4 val)
                        {
                            return BigInteger.ZERO;
                        }

                        @Override
                        public BigInteger visitVar(Svar<PathExt> svar)
                        {
                            return BigInteger.ZERO;
                        }

                        @Override
                        public BigInteger visitCall(SvexFunction fun, Svex<PathExt>[] args, BigInteger[] argVals)
                        {
                            BigInteger result = BigInteger.ONE;
                            for (BigInteger argVal : argVals)
                            {
                                result = result.add(argVal);
                            }
                            return result;
                        }

                        @Override
                        public BigInteger[] newVals(int arity)
                        {
                            return new BigInteger[arity];
                        }
                    });
                    out.println(" // " + (complexity.bitLength() > 11 ? "COMPLEX " : "") + complexity + " " + d.toString());
                    if (d.splitIt)
                    {
                        out.println(" // SPLIT");
                    }
                    if (VERBOSE_DUMP >= 1)
                    {
                        out.println("    // fine  depends  on " + d.showFinePortDeps(fineGraph0, fineGraph1));
                        out.println("    // fine  depends* on " + d.showFinePortDeps(fineClosure0, fineClosure1));
                        if (!d.splitIt)
                        {
                            out.println("    // crude depends  on " + m.showCrudePortDeps(d, crudeGraph0, crudeGraph1));
                            out.println("    // crude depends* on " + m.showCrudePortDeps(d, crudeClosure0, crudeClosure1));
//                            out.println("    // crude depends  on " + showCrude(crudeGraph0.get(d), crudeGraph1.get(d)));
//                            out.println("    // crude depends* on " + showCrude(crudeClosure0.get(d), crudeClosure1.get(d)));
                        }
                    }
                    if (VERBOSE_DUMP >= 2 || VERBOSE_DUMP >= 1 && d.splitIt)
                    {
                        BitSet fineStateLocDeps0 = new BitSet();
                        BitSet fineStateLocDeps1 = new BitSet();
                        BitSet fineStateTransDeps0 = new BitSet();
                        BitSet fineStateTransDeps1 = new BitSet();
                        List<Map<Svar<PathExt>, BigInteger>> fineDeps0 = d.gatherFineBitDeps(fineStateLocDeps0, fineGraph0);
                        List<Map<Svar<PathExt>, BigInteger>> fineDeps1 = d.gatherFineBitDeps(fineStateLocDeps1, fineGraph1);
                        List<Map<Svar<PathExt>, BigInteger>> closureDeps0 = d.gatherFineBitDeps(fineStateTransDeps0, fineClosure0);
                        List<Map<Svar<PathExt>, BigInteger>> closureDeps1 = d.gatherFineBitDeps(fineStateTransDeps1, fineClosure1);
                        for (int bit = 0; bit < l.width(); bit++)
                        {
                            PathExt.Bit pb = d.getBit(bit);
                            out.println("    // fine  " + pb + " depends on "
                                + ModuleExt.showFineDeps(fineStateLocDeps0, fineDeps0, fineStateLocDeps1, fineDeps1, bit));
                            if (d.splitIt)
                            {
                                out.println("    // crude " + pb + " depends  on " + m.showCrudePortDeps(pb, crudeGraph0, crudeGraph1));
//                                out.println("    // crude " + pb + " depends  on " + showCrude(crudeGraph0.get(pb), crudeGraph1.get(pb)));
                            }
                        }
                        for (int bit = 0; bit < l.width(); bit++)
                        {
                            PathExt.Bit pb = d.getBit(bit);
                            out.println("    // fine  " + pb + " depends* on "
                                + ModuleExt.showFineDeps(fineStateTransDeps0, closureDeps0, fineStateTransDeps1, closureDeps1, bit));
                            if (d.splitIt)
                            {
                                out.println("    // crude " + pb + " depends* on " + m.showCrudePortDeps(pb, crudeClosure0, crudeClosure1));
//                                out.println("    // crude " + pb + " depends* on " + showCrude(crudeClosure0.get(pb), crudeClosure1.get(pb)));
                            }
                        }
                    }
                    if (VERBOSE_DUMP >= 1)
                    {
                        GenFsmNew.printSvex(out, 2, d.getNormSvex());
                        if (!d.getNormVars().equals(d.getOrigVars()))
                        {
                            out.println("**** DIFFERENT NORM VARS ****");
                            out.println("orig: " + d.getOrigVars());
                            out.println("norm: " + d.getNormVars());
                        }
                    }
//                            out.println("    // 0 depends on " + graph0.get(d.name));
//                            out.println("    // 1 depends on " + graph1.get(d.name));
//                            out.println("    // 0 closure " + closure0.get(d.name));
//                            out.println("    // 1 closure " + closure1.get(d.name));
                }
//                        out.println(" aliaspairs");
                for (Map.Entry<Lhs<PathExt>, Lhs<PathExt>> e1 : m.aliaspairs.entrySet())
                {
                    Lhs<PathExt> l = e1.getKey();
                    Lhs<PathExt> r = e1.getValue();
                    assert l.ranges.size() == 1;
                    Lhrange<PathExt> lr = l.ranges.get(0);
                    assert lr.getRsh() == 0;
                    Svar<PathExt> svar = lr.getVar();
                    assert svar.getDelay() == 0;
                    assert !svar.isNonblocking();
                    if (svar.getName() instanceof PathExt.PortInst)
                    {
                        continue;
                    }
                    out.print("  // alias " + lr + " <->");
                    for (Lhrange<PathExt> lr1 : r.ranges)
                    {
                        svar = lr1.getVar();
                        assert svar.getDelay() == 0;
                        assert !svar.isNonblocking();
                        out.print(" " + lr1);
                    }
                    out.println();
                }
                out.println("endmodule // " + modName);
                out.println();
            }
        }

    }

    public static void genAlu(File saoFile, String outFileName)
    {
        new GenFsmJob<>(Alu.class,
            saoFile, outFileName).startJob();

    }

    public static class GenFsmJob<T extends GenFsm> extends Job
    {
        private final Class<T> cls;
        private final File saoFile;
        private final String outFileName;

        public GenFsmJob(Class<T> cls, File saoFile, String outFileName)
        {
            super("Gen Fsm in ACL2", User.getUserTool(), Job.Type.SERVER_EXAMINE, null, null, Job.Priority.USER);
            this.cls = cls;
            this.saoFile = saoFile;
            this.outFileName = outFileName;
        }

        @Override
        public boolean doIt() throws JobException
        {
            try
            {
                ACL2Object.initHonsMananger(outFileName);
                GenFsm gen = cls.newInstance();
                gen.gen(saoFile, outFileName);
            } catch (InstantiationException | IllegalAccessException | IOException e)
            {
                System.out.println(e.getMessage());
                return false;
            } finally
            {
                ACL2Object.closeHonsManager();
            }
            return true;
        }
    }

    public static void showTutorialSvexLibs(File saoFile)
    {
        new ShowSvexLibsJob<>(TutorialHints.class, saoFile).startJob();
    }

    public static class ShowSvexLibsJob<H extends DesignHints> extends Job
    {
        private final Class<H> cls;
        private final File saoFile;

        public ShowSvexLibsJob(Class<H> cls, File saoFile)
        {
            super("Show used Svex Libs", User.getUserTool(), Job.Type.SERVER_EXAMINE, null, null, Job.Priority.USER);
            this.cls = cls;
            this.saoFile = saoFile;
        }

        public static <H extends DesignHints> boolean doItNoJob(Class<H> cls, File saoFile)
        {
            try
            {
                ACL2Object.initHonsMananger(saoFile.getName());
                DesignHints designHints = cls.newInstance();
                GenFsmNew gen = new GenFsmNew(designHints);
                gen.scanLib(saoFile);
                gen.showLibs();
            } catch (InstantiationException | IllegalAccessException | IOException e)
            {
                System.out.println(e.getMessage());
                return false;
            } finally
            {
                ACL2Object.closeHonsManager();
            }
            return true;

        }

        @Override
        public boolean doIt() throws JobException
        {
            return doItNoJob(cls, saoFile);
        }
    }

    public static <H extends DesignHints> void compareSvexLibs(Class<H> cls, File[] saoFiles)
    {
        new CompareSvexLibsJob(cls, saoFiles).startJob();

    }

    private static class CompareSvexLibsJob<H extends DesignHints> extends Job
    {
        private final Class<H> cls;
        private final File[] saoFiles;

        public CompareSvexLibsJob(Class<H> cls, File[] saoFiles)
        {
            super("Compare Svex Libs", User.getUserTool(), Job.Type.SERVER_EXAMINE, null, null, Job.Priority.USER);
            this.cls = cls;
            this.saoFiles = saoFiles;
        }

        @Override
        public boolean doIt() throws JobException
        {
            try
            {
                ACL2Object.initHonsMananger("Compare Svex Libs");
                DesignHints designHints = cls.newInstance();
                GenFsmNew gen = new GenFsmNew(designHints);
                Map<ModName, Module<Address>> modMap = new HashMap<>();
                for (File saoFile : saoFiles)
                {
                    System.out.println(saoFile);
                    gen.scanLib(saoFile);
                    ACL2Reader sr = new ACL2Reader(saoFile);
                    SvarName.Builder<Address> snb = new Address.SvarNameBuilder();
                    Design<Address> design = new Design<>(snb, sr.root);
                    for (Map.Entry<ModName, Module<Address>> e : design.modalist.entrySet())
                    {
                        ModName modName = e.getKey();
                        Module<Address> newM = e.getValue();
                        Module<Address> oldM = modMap.get(modName);
                        if (oldM != null)
                        {
                            if (newM.equals(oldM))
                            {
                                assert newM.getACL2Object().equals(oldM.getACL2Object());
                            } else
                            {
                                System.out.println("Defferent module " + modName + " in " + saoFile);
                            }
                        } else
                        {
                            modMap.put(modName, newM);
                        }
                    }
                }
                gen.showLibs();
            } catch (InstantiationException | IllegalAccessException | IOException e)
            {
                System.out.println(e.getMessage());
                return false;
            } finally
            {
                ACL2Object.closeHonsManager();
            }
            return true;
        }
    }

    public static void dedup(File saoFile, String designName, String outFileName)
    {
        new DedupSvexJob(saoFile, designName, outFileName).startJob();

    }

    private static class DedupSvexJob extends Job
    {
        private final File saoFile;
        private final String designName;
        private final String outFileName;

        private DedupSvexJob(File saoFile, String designName, String outFileName)
        {
            super("Dedup SVEX in Design", User.getUserTool(), Job.Type.SERVER_EXAMINE, null, null, Job.Priority.USER);
            this.saoFile = saoFile;
            this.designName = designName;
            this.outFileName = outFileName;
        }

        @Override
        public boolean doIt() throws JobException
        {
            try
            {
                ACL2Object.initHonsMananger(designName);
                ACL2Reader sr = new ACL2Reader(saoFile);
                DesignExt design = new DesignExt(sr.root);
                Map<ACL2Backed, ACL2Object> backedCache = new HashMap<>();
                Map<Svex<PathExt>, String> svexLabels = new LinkedHashMap<>();
                Map<Svex<PathExt>, BigInteger> svexSizes = new HashMap<>();
                try (PrintStream out = new PrintStream(outFileName))
                {
                    out.println("(in-package \"SV\")");
                    out.println("(include-book \"std/util/defrule\" :dir :system)");
                    out.println("(include-book \"std/util/defconsts\" :dir :system)");
                    out.println("(include-book \"centaur/sv/mods/svmods\" :dir :system)");
                    out.println("(include-book \"centaur/sv/svex/svex\" :dir :system)");
                    out.println("(include-book \"centaur/sv/svex/rewrite\" :dir :system)");
                    out.println("(include-book \"centaur/sv/svex/xeval\" :dir :system)");
                    out.println();
                    out.println("(defconsts (*" + designName + "* state)");
                    out.println("  (serialize-read \"" + designName + ".sao\"))");
                    out.println();
                    out.println("(local (defn extract-labels (labels acc)");
                    out.println("  (if (atom labels)");
                    out.println("     ()");
                    out.println("    (cons (cdr (hons-get (car labels) acc))");
                    out.println("          (extract-labels (cdr labels) acc)))))");
                    out.println();
                    out.println("(local (defun from-dedup (x acc)");
                    out.println("  (if (atom x)");
                    out.println("       acc");
                    out.println("    (let* ((line (car x))");
                    out.println("           (label (car line))");
                    out.println("           (kind (cadr line))");
                    out.println("           (args (cddr line)))");
                    out.println("      (from-dedup");
                    out.println("        (cdr x)");
                    out.println("        (hons-acons");
                    out.println("          label");
                    out.println("          (case kind");
                    out.println("            (:quote (make-svex-quote :val (car args)))");
                    out.println("            (:var (make-svex-var :name (car args)))");
                    out.println("            (:call (make-svex-call :fn (car args)");
                    out.println("                                   :args (extract-labels (cdr args) acc))))");
                    out.println("          acc))))))");
                    out.println();
                    out.println("(defconsts (*" + designName + "-dedup*)");
                    out.println(" (from-dedup `(");
                    for (ModuleExt m : design.downTop.values())
                    {
                        for (DriverExt dr : m.assigns.values())
                        {
                            genDedup(out, dr.getOrigSvex(), svexLabels, svexSizes);
                        }
                    }
                    out.println(" ) ()))");
                    out.println();
                    for (Map.Entry<ModName, ModuleExt> e : design.downTop.entrySet())
                    {
                        ModName mn = e.getKey();
                        ModuleExt m = e.getValue();
                        out.println();
                        out.println("(local (defun |check-" + mn + "| ()");
                        out.println("  (let ((m (cdr (assoc-equal '" + mn + " (design->modalist *" + designName + "*)))))");
                        out.println("    (equal (hons-copy (strip-cars (strip-cdrs (module->assigns m))))");
                        out.print("           (extract-labels '(");
                        for (DriverExt dr : m.assigns.values())
                        {
                            out.print(" " + svexLabels.get(dr.getOrigSvex()));
                        }
                        out.println(")");
                        out.println("                           *" + designName + "-dedup*)))))");
                    }
                    out.println();
                    out.println("(rule");
                    out.println("  (and");
                    for (ModName mn : design.downTop.keySet())
                    {
                        out.println("    (|check-" + mn + "|)");
                    }
                    out.println("))");
                    out.println();
                    out.println("(defconsts (*" + designName + "-xeval*) '(");
                    Map<Svex<PathExt>, Vec4> xevalMemoize = new HashMap<>();
                    for (Map.Entry<Svex<PathExt>, String> e : svexLabels.entrySet())
                    {
                        Svex<PathExt> svex = e.getKey();
                        String label = e.getValue();
                        Vec4 xeval = svex.xeval(xevalMemoize);
                        out.println("  (" + label + " . " + xeval.getACL2Object(backedCache).rep() + ")");
                    }
                    out.println(" ))");
                    out.println();
                    out.println("(local (defun check-xeval (alist dedup)");
                    out.println("  (or (atom alist)");
                    out.println("      (and (equal (svex-xeval (cdr (hons-get (caar alist) dedup)))");
                    out.println("                  (cdar alist))");
                    out.println("           (check-xeval (cdr alist) dedup)))))");
                    out.println();
                    out.println("(rule");
                    out.println("  (check-xeval *" + designName + "-xeval* *" + designName + "-dedup*))");
                    out.println();
                    out.println("(defconsts (*" + designName + "-toposort*) '(");
                    for (Map.Entry<Svex<PathExt>, String> e : svexLabels.entrySet())
                    {
                        Svex<PathExt> svex = e.getKey();
                        String label = e.getValue();
                        Svex<PathExt>[] toposort = svex.toposort();
                        Util.check(toposort[0].equals(svex));
                        out.print("  (" + label);
                        for (int i = 1; i < toposort.length; i++)
                        {
                            out.print(" " + svexLabels.get(toposort[i]));
                        }
                        out.println(")");
                    }
                    out.println(" ))");
                    out.println();
                    out.println("(local (defun check-toposort (list dedup)");
                    out.println("  (or (atom list)");
                    out.println("      (b* ((toposort (extract-labels (car list) dedup))");
                    out.println("           ((mv sort ?contents) (svex-toposort (car toposort) () ()))");
                    out.println("           (sort (hons-copy sort)))");
                    out.println("        (and (equal sort toposort)");
                    out.println("             (check-toposort (cdr list) dedup))))))");
                    out.println();
                    out.println("(rule");
                    out.println("  (check-toposort *" + designName + "-toposort* *" + designName + "-dedup*))");
                    out.println();
                    out.println("(defconsts (*" + designName + "-masks*) '(");
                    for (Map.Entry<Svex<PathExt>, String> e : svexLabels.entrySet())
                    {
                        Svex<PathExt> svex = e.getKey();
                        String label = e.getValue();
                        Svex<PathExt>[] toposort = svex.toposort();
                        Util.check(toposort[0].equals(svex));
                        Map<Svex<PathExt>, BigInteger> masks = svex.maskAlist(BigIntegerUtil.MINUS_ONE);
                        out.print("  (" + label);
                        for (int i = 0; i < toposort.length; i++)
                        {
                            BigInteger mask = masks.get(toposort[i]);
                            if (mask == null)
                            {
                                mask = BigInteger.ZERO;
                            }
                            out.print(" #x" + mask.toString(16));
                        }
                        out.println(")");
                    }
                    out.println(" ))");
                    out.println();
                    out.println("(local (defun toposort-label (label dedup)");
                    out.println("  (b* ((svex (cdr (hons-get label dedup)))");
                    out.println("       ((mv toposort ?contents) (svex-toposort svex () ())))");
                    out.println("    toposort)))");
                    out.println();
                    out.println("(local (defun comp-masks (toposort mask-al)");
                    out.println("  (if (atom toposort)");
                    out.println("      ()");
                    out.println("    (cons (svex-mask-lookup (car toposort) mask-al)");
                    out.println("          (comp-masks (cdr toposort) mask-al)))))");
                    out.println();
                    out.println("(local (defun masks-label (label dedup)");
                    out.println("  (b* ((svex (cdr (hons-get label dedup)))");
                    out.println("       (toposort (toposort-label label dedup))");
                    out.println("       (mask-al (svexlist-mask-alist (list svex))))");
                    out.println("    (comp-masks toposort mask-al))))");
                    out.println();
                    out.println("(local (defun show-line (line dedup)");
                    out.println("  (list");
                    out.println("   :line line");
                    out.println("   :toposort (toposort-label (car line) dedup)");
                    out.println("   :masks (masks-label (car line) dedup)");
                    out.println("   :ok (equal (masks-label (car line) dedup) (cdr line)))))");
                    out.println();
                    out.println("(local (defun check-masks (masks-lines dedup)");
                    out.println("  (or (atom masks-lines)");
                    out.println("      (and (let ((line (car masks-lines)))");
                    out.println("             (equal (masks-label (car line) dedup) (cdr line)))");
                    out.println("           (check-masks (cdr masks-lines) dedup)))))");
                    out.println();
                    out.println("(rule");
                    out.println("  (check-masks *" + designName + "-masks* *" + designName + "-dedup*))");
                }
            } catch (IOException e)
            {
                return false;
            } finally
            {
                ACL2Object.closeHonsManager();
            }
            return true;
        }

        private <N extends SvarName> BigInteger computeSize(Svex<N> svex, Map<Svex<N>, BigInteger> sizes)
        {
            BigInteger size = sizes.get(svex);
            if (size == null)
            {
                if (svex instanceof SvexCall)
                {
                    size = BigInteger.ONE;
                    for (Svex<N> arg : ((SvexCall<N>)svex).getArgs())
                    {
                        size = size.add(computeSize(arg, sizes));
                    }
                } else
                {
                    size = BigInteger.ONE;
                }
                sizes.put(svex, size);
            }
            return size;
        }

        private String genDedup(PrintStream out, Svex<PathExt> svex,
            Map<Svex<PathExt>, String> svexLabels, Map<Svex<PathExt>, BigInteger> svexSizes)
        {
            String label = svexLabels.get(svex);
            if (label == null)
            {
                if (svex instanceof SvexQuote)
                {
                    SvexQuote<PathExt> sq = (SvexQuote<PathExt>)svex;
                    label = "l" + svexLabels.size();
                    svexLabels.put(svex, label);
                    out.print(" (" + label + " :quote ");
                    Vec4 val = sq.val;
                    if (val instanceof Vec2)
                    {
                        out.print("#x" + ((Vec2)val).getVal().toString(16));
                    } else
                    {
                        out.print("(#x" + val.getUpper().toString(16) + " . #x" + val.getLower().toString(16) + ")");
                    }
                    out.println(")");
                } else if (svex instanceof SvexVar)
                {
                    SvexVar<PathExt> sv = (SvexVar<PathExt>)svex;
                    label = "l" + svexLabels.size();
                    svexLabels.put(svex, label);
                    out.print(" (" + label + " :var ,(make-svar :name ");
                    if (sv.svar.getName() instanceof PathExt.PortInst)
                    {
                        PathExt.PortInst pi = (PathExt.PortInst)sv.svar.getName();
                        out.print("'(" + pi.inst.getInstname().toLispString() + " . " + pi.getProtoName().toLispString() + ")");
                    } else
                    {
                        WireExt lw = (WireExt)sv.svar.getName();
                        out.print(lw.getName().toLispString());
                        if (sv.svar.getDelay() != 0)
                        {
                            out.print(" :delay " + sv.svar.getDelay());
                        }
                    }
                    out.println("))");
                } else
                {
                    SvexCall<PathExt> sc = (SvexCall<PathExt>)svex;
                    Svex<PathExt>[] args = sc.getArgs();
                    String[] labels = new String[args.length];
                    for (int i = 0; i < labels.length; i++)
                    {
                        labels[i] = genDedup(out, args[i], svexLabels, svexSizes);
                    }
                    label = "l" + svexLabels.size();
                    svexLabels.put(svex, label);
                    out.print(" (" + label + " :call " + symbol_name(sc.fun.fn).stringValueExact());
                    for (String l : labels)
                    {
                        out.print(" " + l);
                    }
                    out.println(") ; " + computeSize(svex, svexSizes));
                }
            }
            return label;
        }
    }

    public static void showAssigns(File saoFile, String designName, String outFileName)
    {
        new ShowAssignsJob(saoFile, designName, outFileName).startJob();

    }

    private static class ShowAssignsJob extends Job
    {
        private final File saoFile;
        private final String designName;
        private final String outFileName;

        private ShowAssignsJob(File saoFile, String designName, String outFileName)
        {
            super("Show SVEX assigns", User.getUserTool(), Job.Type.SERVER_EXAMINE, null, null, Job.Priority.USER);
            this.saoFile = saoFile;
            this.designName = designName;
            this.outFileName = outFileName;
        }

        @Override
        public boolean doIt() throws JobException
        {
            try
            {
                ACL2Object.initHonsMananger(designName);
                ACL2Reader sr = new ACL2Reader(saoFile);
                DesignExt design = new DesignExt(sr.root);
                try (PrintStream out = new PrintStream(outFileName))
                {
                    out.println("(in-package \"SV\")");
                    out.println("(include-book \"std/util/defconsts\" :dir :system)");
                    out.println("(include-book \"std/util/define\" :dir :system)");
                    out.println("(include-book \"std/util/defrule\" :dir :system)");
                    out.println("(include-book \"centaur/sv/mods/svmods\" :dir :system)");
                    out.println("(include-book \"centaur/sv/svex/svex\" :dir :system)");
                    out.println("(include-book \"centaur/sv/svex/rewrite\" :dir :system)");
//                    out.println("(include-book \"centaur/sv/svex/xeval\" :dir :system)");
                    out.println();
                    out.println("(defconsts (*" + designName + "* state)");
                    out.println("  (serialize-read \"" + designName + ".sao\"))");
                    out.println();
                    out.println("(local (define filter-vars");
                    out.println("  ((toposort svexlist-p)");
                    out.println("   (mask-al svex-mask-alist-p))");
                    out.println("  :returns (filtered svex-mask-alist-p)");
                    out.println("  (and (consp toposort)");
                    out.println("       (let ((svex (svex-fix (car toposort)))");
                    out.println("             (rest (cdr toposort)))");
                    out.println("         (svex-case svex");
                    out.println("           :quote (filter-vars rest mask-al)");
                    out.println("           :call (filter-vars rest mask-al)");
                    out.println("           :var (cons (cons svex (svex-mask-lookup svex mask-al))");
                    out.println("                      (filter-vars rest mask-al)))))))");
                    out.println();
                    out.println("(local (define compute-driver-masks");
                    out.println("  ((x svexlist-p))");
                    out.println("  (and (consp x)");
                    out.println("       (b* ((svex (car x))");
                    out.println("            ((mv toposort ?contents) (svex-toposort svex () ()))");
                    out.println("            (mask-al (svexlist-mask-alist (list svex))))");
                    out.println("         (cons (rev (filter-vars toposort mask-al))");
                    out.println("               (compute-driver-masks (cdr x)))))))");

                    for (Map.Entry<ModName, ModuleExt> e : design.downTop.entrySet())
                    {
                        ModName mn = e.getKey();
                        ModuleExt m = e.getValue();
                        out.println();
                        out.println("(local (define |check-" + mn + "| ()");
                        out.println("  (let ((m (cdr (assoc-equal \"" + mn + "\" (design->modalist *" + designName + "*)))))");
                        out.println("    (equal (compute-driver-masks (hons-copy (strip-cars (strip-cdrs (module->assigns m))))) `(");
                        for (Map.Entry<Lhs<PathExt>, DriverExt> e1 : m.assigns.entrySet())
                        {
                            Lhs<PathExt> l = e1.getKey();
                            DriverExt d = e1.getValue();

                            List<Svar<PathExt>> vars = d.getOrigVars();
                            Map<Svex<PathExt>, BigInteger> masks = d.getOrigSvex().maskAlist(BigIntegerUtil.MINUS_ONE);
                            out.print("      (;");
                            assert !l.ranges.isEmpty();
                            for (int i = 0; i < l.ranges.size(); i++)
                            {
                                Lhrange<PathExt> lr = l.ranges.get(i);
                                Svar<PathExt> svar = lr.getVar();
                                assert svar.getDelay() == 0;
                                assert !svar.isNonblocking();
                                out.print((i == 0 ? "  " : ",") + lr);
                            }
                            out.println();
                            for (Svar<PathExt> var : vars)
                            {
                                WireExt lw = (WireExt)var.getName();
                                Svex<PathExt> svex = new SvexVar<>(var);
                                BigInteger mask = masks.get(svex);
                                if (mask == null)
                                {
                                    mask = BigInteger.ZERO;
                                }
                                out.print("        (");
                                String rep = lw.getName().toLispString();
                                if (var.getDelay() == 0)
                                {
                                    out.print(rep);
                                } else
                                {
                                    out.print(",(make-svar :name " + rep + " :delay " + var.getDelay() + ")");
                                }
                                out.println(" . #x" + mask.toString(16) + ")");
                            }
                            out.print("      )");
                        }
                        out.println(" )))))");
                    }
                    out.println();
                    out.println("(rule");
                    out.println("  (and");
                    for (ModName mn : design.downTop.keySet())
                    {
                        out.println("    (|check-" + mn + "|)");
                    }
                    out.println("))");

                }
            } catch (IOException e)
            {
                return false;
            } finally
            {
                ACL2Object.closeHonsManager();
            }
            return true;
        }
    }

    public static void namedToIndexed(File saoFile, String designName)
    {
        new NamedToIndexedJob(saoFile, designName).startJob();
    }

    private static class NamedToIndexedJob extends Job
    {
        private final File saoFile;
        private final String designName;

        private NamedToIndexedJob(File saoFile, String designName)
        {
            super("Named->Indexed", User.getUserTool(), Job.Type.SERVER_EXAMINE, null, null, Job.Priority.USER);
            this.saoFile = saoFile;
            this.designName = designName;
        }

        @Override
        public boolean doIt() throws JobException
        {
            try
            {
                ACL2Object.initHonsMananger(designName);
                ACL2Reader sr = new ACL2Reader(saoFile);
                SvarName.Builder<Address> snb = new Address.SvarNameBuilder();
                Design<Address> design = new Design<>(snb, sr.root);
                ModDb db = new ModDb(design.top, design.modalist);
                IndexName.curElabMod = db.topMod();
                Map<ModName, Module<Address>> indexedMods = db.modalistNamedToIndex(design.modalist);
                ElabMod topIdx = db.modnameGetIndex(design.top);
                ModDb.FlattenResult flattenResult = topIdx.svexmodFlatten(indexedMods);
                ACL2Object indexedAlist = NIL;
                for (Map.Entry<ModName, Module<Address>> e : indexedMods.entrySet())
                {
                    indexedAlist = cons(cons(e.getKey().getACL2Object(), e.getValue().getACL2Object()), indexedAlist);
                }
                indexedAlist = Util.revList(indexedAlist);
                ACL2Object aliaspairsAlist = flattenResult.aliaspairsToACL2Object();
                ACL2Object assignsAlist = flattenResult.assignsToACL2Object();
                ACL2Object aliasesList = flattenResult.aliasesToACL2Object();
                ACL2Object results
                    = cons(indexedAlist,
                        cons(aliaspairsAlist,
                            cons(assignsAlist,
                                cons(aliasesList, NIL))));
                File outDir = saoFile.getParentFile();
                File saoIndexedFile = new File(outDir, designName + "-indexed.sao");
                ACL2Writer.write(results, saoIndexedFile);
                List<String> lines = new ArrayList<>();
                try (LineNumberReader in = new LineNumberReader(
                    new InputStreamReader(ACL2DesignJobs.class.getResourceAsStream("design-indexed.dat"))))
                {
                    String line;
                    while ((line = in.readLine()) != null)
                    {
                        lines.add(line);
                    }
                }
                File outFile = new File(outDir, designName + "-indexed.lisp");
                try (PrintStream out = new PrintStream(outFile))
                {
                    for (String line : lines)
                    {
                        out.println(line.replace("$DESIGN$", designName));
                    }
                }
            } catch (IOException e)
            {
                return false;
            } finally
            {
                IndexName.curElabMod = null;
                ACL2Object.closeHonsManager();
            }
            return true;
        }
    }

    public static void normalizeAssigns(File saoFile, String designName, boolean isIndexed)
    {
        new NormalizeAssignsJob(saoFile, designName, isIndexed).startJob();
    }

    private static class NormalizeAssignsJob extends Job
    {
        private final File saoFile;
        private final String designName;
        private final boolean isIndexed;

        private NormalizeAssignsJob(File saoFile, String designName, boolean isIndexed)
        {
            super("Named->Indexed", User.getUserTool(), Job.Type.SERVER_EXAMINE, null, null, Job.Priority.USER);
            this.saoFile = saoFile;
            this.designName = designName;
            this.isIndexed = isIndexed;
        }

        @Override
        public boolean doIt() throws JobException
        {
            try
            {
                ACL2Object.initHonsMananger(designName);
                ACL2Reader sr = new ACL2Reader(saoFile);
                SvarName.Builder<Address> snb = new Address.SvarNameBuilder();
                Design<Address> design = new Design<>(snb, sr.root);
                ModDb db = new ModDb(design.top, design.modalist);
                IndexName.curElabMod = db.topMod();
                Map<ModName, Module<Address>> indexedMods = db.modalistNamedToIndex(design.modalist);
                ElabMod topElabMod = db.modnameGetIndex(design.top);
                ElabMod.ModScope topScope = new ElabMod.ModScope(topElabMod);
                ModDb.FlattenResult flattenResult = topElabMod.svexmodFlatten(indexedMods);
                ACL2Object indexedAssignsAlist = flattenResult.assignsToACL2Object();
                ACL2Object indexedAliasesList = flattenResult.aliasesToACL2Object();
                ACL2Object namedAliasesList = indexedAliasesList;
                ACL2Object svexarrList, normAssigns, netAssigns, resAssigns, resDelays;
                if (isIndexed)
                {
                    Compile<IndexName> compile = new Compile(flattenResult.aliases.getArr(),
                        flattenResult.assigns, flattenResult.sm);
                    svexarrList = compile.svexarrAsACL2Object();
                    normAssigns = compile.normAssignsAsACL2Object();
                    netAssigns = compile.netAssignsAsACL2Object();
                    resAssigns = compile.resAssignsAsACL2Object();
                    resDelays = compile.resDelaysAsACL2Object();
                } else
                {
                    SvexManager<Path> sm = new SvexManager<>();
                    List<Lhs<Path>> namedAliases = topScope.aliasesToPath(flattenResult.aliases, sm);
                    Compile<Path> compile = new Compile(namedAliases, flattenResult.assigns, sm);

                    namedAliasesList = topScope.aliasesToNamedACL2Object(flattenResult.aliases, sm);

                    svexarrList = compile.svexarrAsACL2Object();
                    normAssigns = compile.normAssignsAsACL2Object();
                    netAssigns = compile.netAssignsAsACL2Object();
                    resAssigns = compile.resAssignsAsACL2Object();
                    resDelays = compile.resDelaysAsACL2Object();
                }
                ACL2Object results
                    = cons(indexedAssignsAlist,
                        cons(indexedAliasesList,
                            cons(namedAliasesList,
                                cons(svexarrList,
                                    cons(normAssigns,
                                        cons(netAssigns,
                                            cons(resAssigns,
                                                cons(resDelays, NIL))))))));

                File outDir = saoFile.getParentFile();
                File saoIndexedFile = new File(outDir, designName + "-svex-normalize-assigns.sao");
                ACL2Writer.write(results, saoIndexedFile);
                List<String> lines = new ArrayList<>();
                try (LineNumberReader in = new LineNumberReader(
                    new InputStreamReader(ACL2DesignJobs.class.getResourceAsStream("svex-normalize-assigns.dat"))))
                {
                    String line;
                    while ((line = in.readLine()) != null)
                    {
                        lines.add(line);
                    }
                }

                File outFile = new File(outDir, designName + "-svex-normalized-assigns.lisp");
                try (PrintStream out = new PrintStream(outFile))
                {
                    String indexedStr = isIndexed ? "t" : "nil";
                    for (String line : lines)
                    {
                        out.println(line
                            .replace("$DESIGN$", designName)
                            .replace("$INDEXED$", indexedStr));
                    }
                }
            } catch (IOException e)
            {
                return false;
            } finally
            {
                IndexName.curElabMod = null;
                ACL2Object.closeHonsManager();
            }
            return true;
        }
    }

    private static class Alu extends GenFsm
    {
        private static String[] inputs =
        {
            "opcode",
            "abus",
            "bbus"
        };

        public Alu()
        {
        }

        @Override
        protected boolean ignore_wire(WireExt w)
        {
            String s = w.getName().toString();
            for (String is : inputs)
            {
                if (is.equals(s))
                {
                    return false;
                }
            }
            return true;
        }

        @Override
        protected boolean isFlipFlopIn(String modname, String wireName)
        {
            return modname.startsWith("flop$width=")
                && wireName.equals("d");
        }

        @Override
        protected boolean isFlipFlopOut(String modname, String wireName)
        {
            return modname.startsWith("flop$width=")
                && wireName.equals("q");
        }
    }

}
