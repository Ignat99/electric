/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: TraceSvtvJobs.java
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
import com.sun.electric.tool.simulation.acl2.mods.ElabMod;
import com.sun.electric.tool.simulation.acl2.mods.IndexName;
import com.sun.electric.tool.simulation.acl2.mods.Lhs;
import com.sun.electric.tool.simulation.acl2.mods.ModDb;
import com.sun.electric.tool.simulation.acl2.mods.ModName;
import com.sun.electric.tool.simulation.acl2.mods.Path;
import com.sun.electric.tool.simulation.acl2.mods.Util;
import com.sun.electric.tool.simulation.acl2.svex.Svar;
import com.sun.electric.tool.simulation.acl2.svex.SvarImpl;
import com.sun.electric.tool.simulation.acl2.svex.SvarName;
import com.sun.electric.tool.simulation.acl2.svex.SvarNameTexter;
import com.sun.electric.tool.simulation.acl2.svex.Svex;
import com.sun.electric.tool.simulation.acl2.svex.SvexCall;
import com.sun.electric.tool.simulation.acl2.svex.SvexManager;
import com.sun.electric.tool.simulation.acl2.svex.SvexQuote;
import com.sun.electric.tool.simulation.acl2.svex.Vec2;
import com.sun.electric.tool.simulation.acl2.svex.funs.Vec4Concat;
import com.sun.electric.tool.user.User;
import static com.sun.electric.util.acl2.ACL2.*;
import com.sun.electric.util.acl2.ACL2Object;
import com.sun.electric.util.acl2.ACL2Reader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 */
public class TraceSvtvJobs
{
    private static final ACL2Object KEYWORD_PHASE = ACL2Object.valueOf("KEYWORD", "PHASE");

    public static <H extends DesignHints> void makeTraceSvtv(Class<H> cls, File saoFile, String designName)
    {
        new MakeTraceSvtvJob(cls, saoFile, designName).startJob();
    }

    private static class MakeTraceSvtvJob<H extends DesignHints> extends Job
    {
        private final Class<H> cls;
        private final File saoFile;
        private final String designName;

        private MakeTraceSvtvJob(Class<H> cls, File saoFile, String designName)
        {
            super("Make SVTV Trace", User.getUserTool(), Job.Type.SERVER_EXAMINE, null, null, Job.Priority.USER);
            this.cls = cls;
            this.saoFile = saoFile;
            this.designName = designName;
        }

        @Override
        public boolean doIt() throws JobException
        {
            try
            {
                ACL2Object.initHonsMananger(designName);
                DesignHints designHints = cls.newInstance();
                ACL2Reader sr = new ACL2Reader(saoFile);
                DesignExt design = new DesignExt(sr.root, designHints);
                for (ModuleExt mod : design.downTop.values())
                {
                    if (mod.parMod != null)
                    {
                        continue;
                    }
                    ModName modName = mod.modName;
//                GenFsmNew gen = new GenFsmNew(designHints);
//                gen.scanDesign(design);
//                String clockName = designHints.getGlobalClock();
//                design.computeCombinationalInputs(clockName);
                    File outDir = saoFile.getParentFile();

                    List<String> lines = new ArrayList<>();
                    try (LineNumberReader in = new LineNumberReader(
                        new InputStreamReader(ACL2DesignJobs.class.getResourceAsStream("defsvtv-trace.dat"))))
                    {
                        String line;
                        while ((line = in.readLine()) != null)
                        {
                            lines.add(line);
                        }
                    }
                    File outFile = new File(outDir, modName + "-defsvtv-trace.lisp");
                    try (PrintStream out = new PrintStream(outFile))
                    {
                        for (String line : lines)
                        {
                            if (line.contains("$INPUT$"))
                            {
                                for (ModExport export : mod.exports)
                                {
                                    if (export.isInput())
                                        out.println(line.replace("$INPUT$", export.wire.getName().toString()));
                                }
                            } else if (line.contains("$OUTPUT"))
                            {
                                for (ModExport export : mod.exports)
                                {
                                    if (export.isOutput())
                                        out.println(line.replace("$OUTPUT$", export.wire.getName().toString()));
                                }
                            } else
                            {
                                out.println(line.replace("$DESIGN$", designName).replace("$MODNAME$", modName.toString()));
                            }
                        }
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
    }

    public static <H extends DesignHints> void readTraceSvtv(Class<H> cls, File saoFile)
    {
        new ReadTraceSvtvJob(cls, saoFile).startJob();
    }

    private static class ReadTraceSvtvJob<H extends DesignHints> extends Job
    {
        private final Class<H> cls;
        private final File saoFile;

        private DesignExt design;
        private SvarName.Builder<Address> snb;
        private SvarName.Builder<Address> modifiedSnb;
        private SvexManager<Address> sm;
        private Map<ACL2Object, Svex<Address>> svexCache;

        private ReadTraceSvtvJob(Class<H> cls, File saoFile)
        {
            super("Read SVTV Ttace", User.getUserTool(), Job.Type.SERVER_EXAMINE, null, null, Job.Priority.USER);
            this.cls = cls;
            this.saoFile = saoFile;
        }

        @Override
        public boolean doIt() throws JobException
        {
            snb = new Address.SvarNameBuilder();
            sm = new SvexManager<>();
            svexCache = new HashMap<>();
            try
            {
                ACL2Object.initHonsMananger(saoFile.getName());
                DesignHints designHints = cls.newInstance();
                ACL2Reader sr = new ACL2Reader(saoFile);

                List<ACL2Object> traceList = Util.getList(sr.root, true);
                Util.check(traceList.size() == 26);
                Util.check(traceList.get(0).equals(ACL2Object.valueOf("KEYWORD", "DESIGN")));
                design = new DesignExt(traceList.get(1), designHints);
                Util.check(traceList.get(2).equals(ACL2Object.valueOf("KEYWORD", "OVERRIDDEN-ASSIGNS")));
                Map<Svar<Address>, Svex<Address>> overriddenAssigns = readSvexAlist(traceList.get(3));
                Util.check(traceList.get(4).equals(ACL2Object.valueOf("KEYWORD", "DELAYS")));
                Map<Svar<Address>, Svar<Address>> delays = readSvarMap(traceList.get(5));
                Util.check(traceList.get(6).equals(ACL2Object.valueOf("KEYWORD", "REWRITTEN-ASSIGNS")));
                Map<Svar<Address>, Svex<Address>> rewrittenAssigns = readSvexAlist(traceList.get(7));
                Util.check(traceList.get(8).equals(ACL2Object.valueOf("KEYWORD", "RAW-UPDATES")));
                Map<Svar<Address>, Svex<Address>> rawUpdates = readSvexAlist(traceList.get(9));
                Util.check(traceList.get(10).equals(ACL2Object.valueOf("KEYWORD", "UPDATES0")));
                Map<Svar<Address>, Svex<Address>> update0 = readSvexAlist(traceList.get(11));
                Util.check(traceList.get(12).equals(ACL2Object.valueOf("KEYWORD", "REST")));
                Map<Svar<Address>, Svex<Address>> rest = readSvexAlist(traceList.get(13));
                Util.check(traceList.get(14).equals(ACL2Object.valueOf("KEYWORD", "RES1")));
                Map<Svar<Address>, Svex<Address>> res1 = readSvexAlist(traceList.get(15));
                Util.check(traceList.get(16).equals(ACL2Object.valueOf("KEYWORD", "RES1-UPDATES")));
                Map<Svar<Address>, Svex<Address>> res1updates = readSvexAlist(traceList.get(17));
                Util.check(traceList.get(18).equals(ACL2Object.valueOf("KEYWORD", "RES1-UPDATES2")));
                Map<Svar<Address>, Svex<Address>> res1updates2 = readSvexAlist(traceList.get(19));
                Util.check(traceList.get(20).equals(ACL2Object.valueOf("KEYWORD", "UPDATES")));
                Map<Svar<Address>, Svex<Address>> updates = readSvexAlist(traceList.get(21));
                Util.check(traceList.get(22).equals(ACL2Object.valueOf("KEYWORD", "NEXT-STATES")));
                Map<Svar<Address>, Svex<Address>> nextStates = readSvexAlist(traceList.get(23));
                Util.check(traceList.get(24).equals(ACL2Object.valueOf("KEYWORD", "SVTV")));
                List<ACL2Object> svtvList = Util.getList(traceList.get(25), true);
                Util.check(car(svtvList.get(0)).equals(ACL2Object.valueOf("SV", "NAME")));
                String designName = symbol_name(cdr(svtvList.get(0))).stringValueExact();
                Util.check(designName.endsWith("-svtv"));
                designName = designName.substring(0, designName.length() - "-svtv".length());
                System.out.println(designName);
                Util.check(car(svtvList.get(1)).equals(ACL2Object.valueOf("SV", "OUTEXPRS")));
                Util.check(car(svtvList.get(2)).equals(ACL2Object.valueOf("SV", "NEXTSTATE")));
                Util.check(car(svtvList.get(3)).equals(ACL2Object.valueOf("SV", "INMASKS")));
                Util.check(car(svtvList.get(4)).equals(ACL2Object.valueOf("SV", "OUTMASKS")));
                Util.check(car(svtvList.get(5)).equals(ACL2Object.valueOf("SV", "ORIG-INS")));
                Util.check(car(svtvList.get(6)).equals(ACL2Object.valueOf("SV", "ORIG-OVERRIDES")));
                Util.check(car(svtvList.get(7)).equals(ACL2Object.valueOf("SV", "ORIG-OUTS")));
                Util.check(car(svtvList.get(8)).equals(ACL2Object.valueOf("SV", "ORIG-INTERNALS")));
                Util.check(car(svtvList.get(9)).equals(ACL2Object.valueOf("SV", "EXPANDED-INS")));
                Util.check(car(svtvList.get(10)).equals(ACL2Object.valueOf("SV", "EXPANDED-OVERRIDES")));
                Util.check(car(svtvList.get(11)).equals(ACL2Object.valueOf("SV", "NPHASES")));
                assert svtvList == svtvList;

                ModuleExt topMod = design.downTop.get(design.b.top);
                ElabMod topElabMod = topMod.elabMod;
                ElabMod.ModScope topScope = new ElabMod.ModScope(topElabMod);
                IndexName.curElabMod = topMod.elabMod;
                ModDb.FlattenResult flattenResult = topMod.elabMod.svexmodFlatten(design.b.modalist);

                List<Lhs<Address>> namedAliases = topScope.aliasesToAddress(flattenResult.aliases, sm);
                Compile<Address> compile = new Compile(namedAliases, flattenResult.assigns, sm);

                modifiedSnb = new ModifiedAddressBuilder(topMod.exports);
                Map<Svar<Address>, Svex<Address>> svtvOutExprs = readSvexAlist(cdr(svtvList.get(1)), modifiedSnb);
                Map<Svar<Address>, Svex<Address>> svtvNextState = readSvexAlist(cdr(svtvList.get(2)), modifiedSnb);

                Util.check(compile.resAssigns.equals(overriddenAssigns));
                checkSvexAlist(compile.resAssigns, traceList.get(3));
                Util.check(compile.resDelays.equals(delays));
                Util.check(compile.resDelaysAsACL2Object().equals(traceList.get(5)));
                Util.check(compile.resAssigns.keySet().equals(rewrittenAssigns.keySet()));
                checkSvarKeys(compile.resAssigns, traceList.get(7));
                Util.check(compile.resAssigns.keySet().equals(rawUpdates.keySet()));
                Util.check(compile.resAssigns.keySet().equals(update0.keySet()));
                checkSvarKeys(rawUpdates, traceList.get(11));
                Util.check(compile.resAssigns.keySet().equals(updates.keySet()));
                checkSvarKeys(rawUpdates, traceList.get(21));
                Util.check(compile.resDelays.keySet().equals(nextStates.keySet()));
                checkSvarKeys(compile.resDelays, traceList.get(23));

                Iterator<Map.Entry<Svar<Address>, Svex<Address>>> svtvOutExprsIter = svtvOutExprs.entrySet().iterator();
                for (ModExport export : topMod.exports)
                {
                    if (export.isOutput())
                    {
                        Map.Entry<Svar<Address>, Svex<Address>> e = svtvOutExprsIter.next();
                        Svar<Address> svar = e.getKey();
                        Svex<Address> svex = e.getValue();
                        Util.check(svar.getDelay() == 0 && !svar.isNonblocking());
                        Address address = svar.getName();
                        Util.check(address.index == Address.INDEX_NIL && address.scope == 0);
                        Path.Wire pathWire = (Path.Wire)address.getPath();
                        Util.check(pathWire.name.equals(export.wire.getName()));
                    }
                }
                Util.check(!svtvOutExprsIter.hasNext());

                File outDir = saoFile.getParentFile();
                printAliases(namedAliases, new File(outDir, designName + "-aliases.txt"));
                printSvexarr(compile.svexarr, new File(outDir, designName + "-svexarr.txt"));
                printSvexalist(overriddenAssigns, new File(outDir, designName + "-overriddenAssigns.txt"));
                printVars(delays.keySet(), new File(outDir, designName + "-delays.txt"));
                printSvexalist(rewrittenAssigns, new File(outDir, designName + "-rewrittenAssigns.txt"));
                printSvexalist(rawUpdates, new File(outDir, designName + "-rawUpdates.txt"));
                printSvexalist(update0, new File(outDir, designName + "-update0.txt"));
                printSvexalist(rest, new File(outDir, designName + "-rest.txt"));
                printSvexalist(res1, new File(outDir, designName + "-res1.txt"));
                printSvexalist(res1updates, new File(outDir, designName + "-res1updates.txt"));
                printSvexalist(res1updates2, new File(outDir, designName + "-res1updates2.txt"));
                printSvexalist(updates, new File(outDir, designName + "-updates.txt"));
                printSvexalist(nextStates, new File(outDir, designName + "-nextState.txt"));
                printSvexalist(svtvOutExprs, new File(outDir, designName + "-svtvOutExprs.txt"));
                printSvexalist(svtvNextState, new File(outDir, designName + "-svtvNextState.txt"));


                /*
                DesignExt design = new DesignExt(sr.root, designHints);
                ModuleExt topMod = design.downTop.get(design.getTop());
//                GenFsmNew gen = new GenFsmNew(designHints);
//                gen.scanDesign(design);
//                String clockName = designHints.getGlobalClock();
//                design.computeCombinationalInputs(clockName);
                File outDir = saoFile.getParentFile();

                List<String> lines = new ArrayList<>();
                try (LineNumberReader in = new LineNumberReader(
                    new InputStreamReader(ACL2DesignJobs.class.getResourceAsStream("defsvtv-trace.dat"))))
                {
                    String line;
                    while ((line = in.readLine()) != null)
                    {
                        lines.add(line);
                    }
                }

                File outFile = new File(outDir, designName + "-defsvtv-trace.lisp");
                try (PrintStream out = new PrintStream(outFile))
                {
                    for (String line : lines)
                    {
                        if (line.contains("$INPUT$"))
                        {
                            for (ModExport export : topMod.exports)
                            {
                                if (export.isInput())
                                    out.println(line.replace("$INPUT$", export.wire.toString()));
                            }
                        } else if (line.contains("$OUTPUT"))
                        {
                            for (ModExport export : topMod.exports)
                            {
                                if (export.isOutput())
                                    out.println(line.replace("$OUTPUT$", export.wire.toString()));
                            }
                        } else
                        {
                            out.println(line.replace("$DESIGN$", designName));
                        }
                    }
                }
                 */
            } catch (InstantiationException | IllegalAccessException | IOException e)
            {
                return false;
            } finally
            {
                IndexName.curElabMod = null;
                ACL2Object.closeHonsManager();
            }
            return true;
        }

        private Map<Svar<Address>, Svex<Address>> readSvexAlist(ACL2Object l)
        {
            return readSvexAlist(l, snb);
        }

        private Map<Svar<Address>, Svex<Address>> readSvexAlist(ACL2Object l, SvarName.Builder<Address> snb)
        {
            Map<Svar<Address>, Svex<Address>> result = new LinkedHashMap<>();
            for (ACL2Object pair : Util.getList(l, true))
            {
                Svar<Address> svar = SvarImpl.fromACL2(snb, sm, car(pair));
                Svex<Address> svex = Svex.fromACL2(snb, sm, cdr(pair), svexCache);
                Svex<Address> old = result.put(svar, svex);
                Util.check(old == null);
            }
            return result;
        }

        private Map<Svar<Address>, Svar<Address>> readSvarMap(ACL2Object l)
        {
            Map<Svar<Address>, Svar<Address>> result = new LinkedHashMap<>();
            for (ACL2Object pair : Util.getList(l, true))
            {
                Svar<Address> svarKey = SvarImpl.fromACL2(snb, sm, car(pair));
                Svar<Address> svarValue = SvarImpl.fromACL2(snb, sm, cdr(pair));
                Svar<Address> old = result.put(svarKey, svarValue);
                Util.check(old == null);
            }
            return result;
        }

        private void checkSvexAlist(Map<Svar<Address>, Svex<Address>> svexAlist, ACL2Object l)
        {
            for (Map.Entry<Svar<Address>, Svex<Address>> e : svexAlist.entrySet())
            {
                Util.checkNotNil(consp(l));
                ACL2Object pair = car(l);
                Util.check(e.getKey().getACL2Object().equals(car(pair)));
                Util.check(e.getValue().getACL2Object().equals(cdr(pair)));
                l = cdr(l);
            }
            Util.checkNil(l);
        }

        private <V> void checkSvarKeys(Map<Svar<Address>, V> map, ACL2Object l)
        {
            List<ACL2Object> list = Util.getList(l, true);
            Util.check(map.size() == list.size());
            int i = 0;
            for (Svar<Address> svar : map.keySet())
            {
                ACL2Object pair = list.get(i);
                Util.checkNotNil(consp(pair));
                Util.check(svar.getACL2Object().equals(car(pair)));
                i++;
            }
            assert i == list.size();
        }

        void printAliases(List<Lhs<Address>> aliases, File outFile) throws FileNotFoundException
        {
            ElabMod topMod = design.moddb.topMod();
            assert aliases.size() == topMod.modTotalWires();
            SvarNameTexter<Address> texter = topMod.getAddressTexter();
            try (PrintStream out = new PrintStream(outFile))
            {
                for (int i = 0; i < topMod.modTotalWires(); i++)
                {
                    Lhs<Address> lhs = aliases.get(i);
                    out.println(i + ": " + topMod.wireidxToPath(i) + " = " + lhs.toString(texter));
                }
            }
        }

        void printSvexarr(Svex<Address>[] svexarr, File outFile) throws FileNotFoundException
        {
            ElabMod topMod = design.moddb.topMod();
            assert svexarr.length == topMod.modTotalWires();
            SvarNameTexter<Address> texter = topMod.getAddressTexter();
            try (PrintStream out = new PrintStream(outFile))
            {
                for (int i = 0; i < topMod.modTotalWires(); i++)
                {
                    Svex<Address> svex = svexarr[i];
                    String name = topMod.wireidxToPath(i).toString();
                    out.print(i + ": " + name);
                    GenFsmNew.printSvex(out, 1, svex);
                    out.println();
                }
            }
        }

        void printVars(Set<Svar<Address>> vars, File outFile) throws FileNotFoundException
        {
            try (PrintStream out = new PrintStream(outFile))
            {
                for (Svar<Address> svar : vars)
                {
                    out.println(svar);
                }
            }
        }

        void printSvexalist(Map<Svar<Address>, Svex<Address>> alist, File outFile) throws FileNotFoundException
        {
            Map<Svex<Address>, String> names = new HashMap<>();
            try (PrintStream out = new PrintStream(outFile))
            {
                for (Map.Entry<Svar<Address>, Svex<Address>> e : alist.entrySet())
                {
                    Svar<Address> svar = e.getKey();
                    Svex<Address> svex = e.getValue();
                    names.put(svex, svar.toString());
                    int lsh = 0;
                    for (;;)
                    {
                        if (!(svex instanceof SvexCall))
                        {
                            break;
                        }
                        SvexCall<Address> sc = (SvexCall<Address>)svex;
                        if (sc.fun != Vec4Concat.FUNCTION)
                        {
                            break;
                        }
                        Svex<Address>[] args = sc.getArgs();
                        if (!(args[0] instanceof SvexQuote))
                        {
                            break;
                        }
                        SvexQuote<Address> w = (SvexQuote<Address>)args[0];
                        if (!(w.val.isIndex()))
                        {
                            break;
                        }
                        int wVal = ((Vec2)w.val).getVal().intValueExact();
                        if (args[1] instanceof SvexCall)
                        {
                            String svarName = svar + "[" + (lsh + wVal - 1) + ":" + lsh + "]";
                            names.put(args[1], svarName);
                        }
                        svex = args[2];
                        lsh += wVal;
                    }
                }
                Set<SvexCall<Address>> multirefs = Svex.multirefs(names.keySet());
                for (Svex<Address> svex : names.keySet())
                {
                    if (svex instanceof SvexCall)
                    {
                        multirefs.add((SvexCall<Address>)svex);
                    }
                }
                for (Map.Entry<Svar<Address>, Svex<Address>> e : alist.entrySet())
                {
                    Svar<Address> svar = e.getKey();
                    Svex<Address> svex = e.getValue();
                    int lsh = 0;
                    for (;;)
                    {
                        if (!(svex instanceof SvexCall))
                        {
                            break;
                        }
                        SvexCall<Address> sc = (SvexCall<Address>)svex;
                        if (sc.fun != Vec4Concat.FUNCTION)
                        {
                            break;
                        }
                        Svex<Address>[] args = sc.getArgs();
                        if (!(args[0] instanceof SvexQuote))
                        {
                            break;
                        }
                        SvexQuote<Address> w = (SvexQuote<Address>)args[0];
                        if (!(w.val.isIndex()))
                        {
                            break;
                        }
                        int wVal = ((Vec2)w.val).getVal().intValueExact();
                        if (args[1] instanceof SvexCall)
                        {
                            String svarName = svar + "[" + (lsh + wVal - 1) + ":" + lsh + "]";
                            out.print(svarName);
                            printSvex(out, sc.getArgs()[1], multirefs, names, svarName, 1);
                            out.println();
                        }
                        svex = args[2];
                        lsh += wVal;
                    }
                }
                for (Map.Entry<Svar<Address>, Svex<Address>> e : alist.entrySet())
                {
                    Svar<Address> svar = e.getKey();
                    Svex<Address> svex = e.getValue();
                    String svarName = svar.toString();
                    out.print(svarName);
                    printSvex(out, svex, multirefs, names, svarName, 1);
                    out.println();
                }
            }

        }

        private void printSvex(PrintStream out, Svex<Address> top, Set<SvexCall<Address>> multirefs,
            Map<Svex<Address>, String> names, String multirefPrefix, int indent)
        {
            GenFsmNew.printSvex(out, indent, top, multirefs, names, multirefPrefix);
        }
    }

    private static class ModifiedAddressBuilder extends Address.SvarNameBuilder
    {
        private final Map<ACL2Object, ACL2Object> patchMap = new HashMap<>();

        ModifiedAddressBuilder(List<ModExport> exports)
        {
            for (ModExport export : exports)
            {
                String nameStr = export.wire.getName().toString();
                ACL2Object sym = ACL2Object.valueOf("SV", nameStr);
                ACL2Object str = symbol_name(sym);
                patchMap.put(sym, str);
            }
        }

        @Override
        public Address fromACL2(ACL2Object nameImpl)
        {
            ACL2Object patch = patchMap.get(nameImpl);
            if (patch == null && consp(nameImpl).bool() && car(nameImpl).equals(KEYWORD_PHASE))
            {
                patch = car(cdr(nameImpl));
                Util.checkNotNil(stringp(patch));
            }
            return super.fromACL2(patch != null ? patch : nameImpl);
        }

    }

    public static <H extends DesignHints> void testTraceSvtv(Class<H> cls, File saoFile)
    {
        new TestTraceSvtvJob(cls, saoFile).startJob();
    }

    private static class TestTraceSvtvJob<H extends DesignHints> extends Job
    {
        private final Class<H> cls;
        private final File saoFile;

        private DesignExt design;
        private SvarName.Builder<Address> snb;
        private SvarName.Builder<Address> modifiedSnb;
        private SvexManager<Address> sm;
        private Map<ACL2Object, Svex<Address>> svexCache;

        private TestTraceSvtvJob(Class<H> cls, File saoFile)
        {
            super("Test SVTV Ttace", User.getUserTool(), Job.Type.SERVER_EXAMINE, null, null, Job.Priority.USER);
            this.cls = cls;
            this.saoFile = saoFile;
        }

        @Override
        public boolean doIt() throws JobException
        {
            snb = new Address.SvarNameBuilder();
            sm = new SvexManager<>();
            svexCache = new HashMap<>();
            try
            {
                ACL2Object.initHonsMananger(saoFile.getName());
                DesignHints designHints = cls.newInstance();
                ACL2Reader sr = new ACL2Reader(saoFile);

                List<ACL2Object> traceList = Util.getList(sr.root, true);
                Util.check(traceList.size() == 26);
                Util.check(traceList.get(0).equals(ACL2Object.valueOf("KEYWORD", "DESIGN")));
                design = new DesignExt(traceList.get(1), designHints);
                Util.check(traceList.get(2).equals(ACL2Object.valueOf("KEYWORD", "OVERRIDDEN-ASSIGNS")));
//                Map<Svar<Address>, Svex<Address>> overriddenAssigns = readSvexAlist(traceList.get(3));
                Util.check(traceList.get(4).equals(ACL2Object.valueOf("KEYWORD", "DELAYS")));
//                Map<Svar<Address>, Svar<Address>> delays = readSvarMap(traceList.get(5));
                Util.check(traceList.get(6).equals(ACL2Object.valueOf("KEYWORD", "REWRITTEN-ASSIGNS")));
//                Map<Svar<Address>, Svex<Address>> rewrittenAssigns = readSvexAlist(traceList.get(7));
                Util.check(traceList.get(8).equals(ACL2Object.valueOf("KEYWORD", "RAW-UPDATES")));
//                Map<Svar<Address>, Svex<Address>> rawUpdates = readSvexAlist(traceList.get(9));
                Util.check(traceList.get(10).equals(ACL2Object.valueOf("KEYWORD", "UPDATES0")));
//                Map<Svar<Address>, Svex<Address>> update0 = readSvexAlist(traceList.get(11));
                Util.check(traceList.get(12).equals(ACL2Object.valueOf("KEYWORD", "REST")));
//                Map<Svar<Address>, Svex<Address>> rest = readSvexAlist(traceList.get(13));
                Util.check(traceList.get(14).equals(ACL2Object.valueOf("KEYWORD", "RES1")));
//                Map<Svar<Address>, Svex<Address>> res1 = readSvexAlist(traceList.get(15));
                Util.check(traceList.get(16).equals(ACL2Object.valueOf("KEYWORD", "RES1-UPDATES")));
//                Map<Svar<Address>, Svex<Address>> res1updates = readSvexAlist(traceList.get(17));
                Util.check(traceList.get(18).equals(ACL2Object.valueOf("KEYWORD", "RES1-UPDATES2")));
//                Map<Svar<Address>, Svex<Address>> res1updates2 = readSvexAlist(traceList.get(19));
                Util.check(traceList.get(20).equals(ACL2Object.valueOf("KEYWORD", "UPDATES")));
                Map<Svar<Address>, Svex<Address>> updates = readSvexAlist(traceList.get(21));
                Util.check(traceList.get(22).equals(ACL2Object.valueOf("KEYWORD", "NEXT-STATES")));
//                Map<Svar<Address>, Svex<Address>> nextStates = readSvexAlist(traceList.get(23));
                Util.check(traceList.get(24).equals(ACL2Object.valueOf("KEYWORD", "SVTV")));
                List<ACL2Object> svtvList = Util.getList(traceList.get(25), true);
                Util.check(car(svtvList.get(0)).equals(ACL2Object.valueOf("SV", "NAME")));
                String designName = symbol_name(cdr(svtvList.get(0))).stringValueExact();
                Util.check(designName.endsWith("-svtv"));
                designName = designName.substring(0, designName.length() - "-svtv".length());
                System.out.println(designName);
                Util.check(car(svtvList.get(1)).equals(ACL2Object.valueOf("SV", "OUTEXPRS")));
                Util.check(car(svtvList.get(2)).equals(ACL2Object.valueOf("SV", "NEXTSTATE")));
                Util.check(car(svtvList.get(3)).equals(ACL2Object.valueOf("SV", "INMASKS")));
                Util.check(car(svtvList.get(4)).equals(ACL2Object.valueOf("SV", "OUTMASKS")));
                Util.check(car(svtvList.get(5)).equals(ACL2Object.valueOf("SV", "ORIG-INS")));
                Util.check(car(svtvList.get(6)).equals(ACL2Object.valueOf("SV", "ORIG-OVERRIDES")));
                Util.check(car(svtvList.get(7)).equals(ACL2Object.valueOf("SV", "ORIG-OUTS")));
                Util.check(car(svtvList.get(8)).equals(ACL2Object.valueOf("SV", "ORIG-INTERNALS")));
                Util.check(car(svtvList.get(9)).equals(ACL2Object.valueOf("SV", "EXPANDED-INS")));
                Util.check(car(svtvList.get(10)).equals(ACL2Object.valueOf("SV", "EXPANDED-OVERRIDES")));
                Util.check(car(svtvList.get(11)).equals(ACL2Object.valueOf("SV", "NPHASES")));
                assert svtvList == svtvList;

                ModuleExt topMod = design.downTop.get(design.b.top);
//                ElabMod topElabMod = topMod.elabMod;
//                ElabMod.ModScope topScope = new ElabMod.ModScope(topElabMod);
//                IndexName.curElabMod = topMod.elabMod;
//                ModDb.FlattenResult flattenResult = topMod.elabMod.svexmodFlatten(design.b.modalist);

//                List<Lhs<Address>> namedAliases = topScope.aliasesToAddress(flattenResult.aliases, sm);
//                Compile<Address> compile = new Compile(namedAliases, flattenResult.assigns, sm);
                modifiedSnb = new ModifiedAddressBuilder(topMod.exports);
                Map<Svar<Address>, Svex<Address>> svtvOutExprs = readSvexAlist(cdr(svtvList.get(1)), modifiedSnb);
                Map<Svar<Address>, Svex<Address>> svtvNextState = readSvexAlist(cdr(svtvList.get(2)), modifiedSnb);
                System.out.println("Design name " + designName);
                System.out.println("Top mod " + design.b.top);
                System.out.println("Outs:");
                for (Svar<Address> svar : svtvOutExprs.keySet())
                {
                    System.out.println(svar);
                }
                System.out.println("States:");
                for (Svar<Address> svar : svtvNextState.keySet())
                {
                    System.out.println(svar);
                }
                designHints.testSvtv(design.b.top, updates, svtvOutExprs, svtvNextState, sm);
                /*
                Util.check(compile.resAssigns.equals(overriddenAssigns));
                checkSvexAlist(compile.resAssigns, traceList.get(3));
                Util.check(compile.resDelays.equals(delays));
                Util.check(compile.resDelaysAsACL2Object().equals(traceList.get(5)));
                Util.check(compile.resAssigns.keySet().equals(rewrittenAssigns.keySet()));
                checkSvarKeys(compile.resAssigns, traceList.get(7));
                Util.check(compile.resAssigns.keySet().equals(rawUpdates.keySet()));
                Util.check(compile.resAssigns.keySet().equals(update0.keySet()));
                checkSvarKeys(rawUpdates, traceList.get(11));
                Util.check(compile.resAssigns.keySet().equals(updates.keySet()));
                checkSvarKeys(rawUpdates, traceList.get(21));
                Util.check(compile.resDelays.keySet().equals(nextStates.keySet()));
                checkSvarKeys(compile.resDelays, traceList.get(23));

                Iterator<Map.Entry<Svar<Address>, Svex<Address>>> svtvOutExprsIter = svtvOutExprs.entrySet().iterator();
                for (ModExport export : topMod.exports)
                {
                    if (export.isOutput())
                    {
                        Map.Entry<Svar<Address>, Svex<Address>> e = svtvOutExprsIter.next();
                        Svar<Address> svar = e.getKey();
                        Svex<Address> svex = e.getValue();
                        Util.check(svar.getDelay() == 0 && !svar.isNonblocking());
                        Address address = svar.getName();
                        Util.check(address.index == Address.INDEX_NIL && address.scope == 0);
                        Path.Wire pathWire = (Path.Wire)address.getPath();
                        Util.check(pathWire.name.equals(export.wire.getName()));
                    }
                }
                Util.check(!svtvOutExprsIter.hasNext());

                File outDir = saoFile.getParentFile();
                printAliases(namedAliases, new File(outDir, designName + "-aliases.txt"));
                printSvexarr(compile.svexarr, new File(outDir, designName + "-svexarr.txt"));
                printSvexalist(overriddenAssigns, new File(outDir, designName + "-overriddenAssigns.txt"));
                printVars(delays.keySet(), new File(outDir, designName + "-delays.txt"));
                printSvexalist(rewrittenAssigns, new File(outDir, designName + "-rewrittenAssigns.txt"));
                printSvexalist(rawUpdates, new File(outDir, designName + "-rawUpdates.txt"));
                printSvexalist(update0, new File(outDir, designName + "-update0.txt"));
                printSvexalist(rest, new File(outDir, designName + "-rest.txt"));
                printSvexalist(res1, new File(outDir, designName + "-res1.txt"));
                printSvexalist(res1updates, new File(outDir, designName + "-res1updates.txt"));
                printSvexalist(res1updates2, new File(outDir, designName + "-res1updates2.txt"));
                printSvexalist(updates, new File(outDir, designName + "-updates.txt"));
                printVars(nextStates.keySet(), new File(outDir, designName + "-nextState.txt"));
                printVars(svtvOutExprs.keySet(), new File(outDir, designName + "-svtvOutExprs.txt"));
                printVars(svtvNextState.keySet(), new File(outDir, designName + "-svtvNextState.txt"));
                 */
            } catch (InstantiationException | IllegalAccessException | IOException e)
            {
                return false;
            } finally
            {
//                IndexName.curElabMod = null;
                ACL2Object.closeHonsManager();
            }
            return true;
        }

        private Map<Svar<Address>, Svex<Address>> readSvexAlist(ACL2Object l)
        {
            return readSvexAlist(l, snb);
        }

        private Map<Svar<Address>, Svex<Address>> readSvexAlist(ACL2Object l, SvarName.Builder<Address> snb)
        {
            Map<Svar<Address>, Svex<Address>> result = new LinkedHashMap<>();
            for (ACL2Object pair : Util.getList(l, true))
            {
                Svar<Address> svar = SvarImpl.fromACL2(snb, sm, car(pair));
                Svex<Address> svex = Svex.fromACL2(snb, sm, cdr(pair), svexCache);
                Svex<Address> old = result.put(svar, svex);
                Util.check(old == null);
            }
            return result;
        }

        private Map<Svar<Address>, Svar<Address>> readSvarMap(ACL2Object l)
        {
            Map<Svar<Address>, Svar<Address>> result = new LinkedHashMap<>();
            for (ACL2Object pair : Util.getList(l, true))
            {
                Svar<Address> svarKey = SvarImpl.fromACL2(snb, sm, car(pair));
                Svar<Address> svarValue = SvarImpl.fromACL2(snb, sm, cdr(pair));
                Svar<Address> old = result.put(svarKey, svarValue);
                Util.check(old == null);
            }
            return result;
        }
    }
}
