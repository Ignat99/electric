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

import com.sun.electric.tool.simulation.acl2.mods.Address;
import com.sun.electric.tool.simulation.acl2.mods.Aliaspair;
import com.sun.electric.tool.simulation.acl2.mods.Assign;
import com.sun.electric.tool.simulation.acl2.mods.Design;
import com.sun.electric.tool.simulation.acl2.mods.Driver;
import com.sun.electric.tool.simulation.acl2.mods.Lhrange;
import com.sun.electric.tool.simulation.acl2.mods.Lhs;
import com.sun.electric.tool.simulation.acl2.mods.ModDb;
import com.sun.electric.tool.simulation.acl2.mods.ModInst;
import com.sun.electric.tool.simulation.acl2.mods.ModName;
import com.sun.electric.tool.simulation.acl2.mods.Module;
import com.sun.electric.tool.simulation.acl2.mods.Path;
import com.sun.electric.tool.simulation.acl2.mods.Wire;
import com.sun.electric.tool.simulation.acl2.svex.Svar;
import com.sun.electric.tool.simulation.acl2.svex.SvarName;
import com.sun.electric.tool.simulation.acl2.svex.SvexFunction;
import static com.sun.electric.util.acl2.ACL2.*;
import com.sun.electric.util.acl2.ACL2Object;
import com.sun.electric.util.acl2.ACL2Reader;
import com.sun.electric.util.acl2.ACL2Writer;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Map;

/**
 * Standalone programs to explore design
 *
 * @param <H> Type of design hints
 */
public class DesignExplore<H extends DesignHints>
{
    Class<H> cls;

    public DesignExplore(Class<H> cls)
    {
        this.cls = cls;
    }

    private static void help()
    {
        System.out.println("  showlibs <sao.file>");
        System.exit(1);
    }

    private void showLibs(String saoFileName, boolean checkElabMod)
    {
        File saoFile = new File(saoFileName);
        try
        {
            ACL2Object.initHonsMananger(saoFile.getName());
            DesignHints designHints = cls.newInstance();
            ACL2Reader sr = new ACL2Reader(saoFile);
            SvarName.Builder<Address> snb = new Address.SvarNameBuilder();
            Design<Address> design = new Design<>(snb, sr.root);
            ModDb modDb = checkElabMod ? new ModDb(design.modalist) : null;
            GenFsmNew gen = new GenFsmNew(designHints);
            gen.scanDesign(design, modDb);
            for (Map.Entry<ModName, Module<Address>> e : design.modalist.entrySet())
            {
                ModName modName = e.getKey();
                Module<Address> m = e.getValue();
                if (gen.modToParMod.containsKey(modName))
                {
                    continue;
                }
                System.out.println(modName);
                if (!modName.isString())
                {
                    System.out.println("!!! Difficult modName");
                }
                for (Wire wire : m.wires)
                {
                    if (!wire.name.isString())
                    {
                        System.out.println("!!! Difficult wire name " + wire.name);
                    }
                }
                for (ModInst inst : m.insts)
                {
                    if (!inst.instname.isString())
                    {
                        System.out.println("!!! Difficult inst name " + inst.instname + " " + inst.modname);
                    }
                }
                for (Assign<Address> assign : m.assigns)
                {
                    if (!easyLhs(assign.lhs, 1))
                    {
                        System.out.println("!!! Difficult assign " + assign.lhs);
                    }
                    checkAssign(assign);
                }
                for (Aliaspair<Address> aliaspair : m.aliaspairs)
                {
                    if (aliaspair.lhs.ranges.size() != 1)
                    {
                        System.out.println("!!! Difficult lhs size " + aliaspair.lhs + " = " + aliaspair.rhs);
                    }
                    if (!easyLhs(aliaspair.lhs, 1))
                    {
                        System.out.println("!!! Difficult lhs " + aliaspair.lhs + " = " + aliaspair.rhs);
                    }
                    if (aliaspair.lhs.ranges.get(0).getVar().getName().path.getDepth() == 1)
                    {
                        if (!easyLhs(aliaspair.rhs, 0))
                        {
                            System.out.println("!!! Difficult rhs " + aliaspair.lhs + " = " + aliaspair.rhs);
                        }
                    } else
                    {
                        if (aliaspair.rhs.ranges.size() != 1)
                        {
                            System.out.println("!!! Difficult rhs size " + aliaspair.lhs + " = " + aliaspair.rhs);
                        }
                        if (!easyLhs(aliaspair.rhs, 1))
                        {
                            System.out.println("!!! Difficult rhs " + aliaspair.lhs + " = " + aliaspair.rhs);
                        }
                        if (aliaspair.rhs.ranges.get(0).getVar().getName().path.getDepth() != 1)
                        {
                            System.out.println("!!! Difficult rhs depth " + aliaspair.lhs + " = " + aliaspair.rhs);
                        }
                    }
                    if (aliaspair.lhs.ranges.get(0).getVar().getName().path.getDepth() == 0)
                    {
                    }
                }
            }
            gen.showLibs();
        } catch (InstantiationException | IllegalAccessException | IOException e)
        {
            System.out.println(e.getMessage());
        } finally
        {
            ACL2Object.closeHonsManager();
        }
    }

    private static boolean easyLhs(Lhs<Address> lhs, int maxDepth)
    {
        for (Lhrange<Address> range : lhs.ranges)
        {
            Svar<Address> svar = range.getVar();
            if (svar == null)
            {
                return false;
            }
            if (svar.getDelay() != 0 || svar.isNonblocking())
            {
                return false;
            }
            Address addr = svar.getName();
            if (addr.index != Address.INDEX_NIL || addr.scope != 0)
            {
                return false;
            }
            Path path = addr.getPath();
            if (path.getDepth() > maxDepth)
            {
                return false;
            }
        }
        return true;
    }

    private static void checkAssign(Assign<Address> assign)
    {
        if (assign.driver.strength != 6 && assign.driver.strength != 0)
        {
            System.out.println("!!! Difficult driver strength " + assign.lhs + " " + assign.driver.strength);
        }
        for (Svar<Address> svar : assign.driver.vars)
        {
            if (svar.getDelay() > 1 || svar.isNonblocking())
            {
                System.out.println("!!! Difficult delay " + assign.lhs + " " + svar);
            }
            Address addr = svar.getName();
            if (addr.index != Address.INDEX_NIL || addr.scope != 0)
            {
                System.out.println("!!! Difficult address " + assign.lhs + " " + svar);
            }
            Path path = addr.getPath();
            if (path.getDepth() != 0)
            {
                System.out.println("!!! Difficult path " + assign.lhs + " " + svar);
            }
        }
    }

    private void strip(String saoFileName)
    {
        try
        {
            File saoFile = new File(saoFileName);

            ACL2Object.initHonsMananger(saoFile.getName());
            ACL2Reader sr = new ACL2Reader(saoFile);
            SvarName.Builder<Address> snb = new Address.SvarNameBuilder();
            Design<Address> design = new Design<>(snb, sr.root);
            for (Module<Address> m : design.modalist.values())
            {
                m.wires.clear();
                m.assigns.clear();
                m.aliaspairs.clear();
            }
            File saoDir = saoFile.getParentFile();
            String outFileName = saoFileName.endsWith(".sao")
                ? saoFileName.substring(0, saoFileName.length() - ".sao".length())
                : saoFileName;
            outFileName += "-stripped.sao";
            File outFile = new File(saoDir, outFileName);
            ACL2Writer.write(design.getACL2Object(), outFile);
        } catch (IOException e)
        {
            System.out.println(e.getMessage());
        } finally
        {
            ACL2Object.closeHonsManager();
        }
    }
    
    private void restoreParameterized(String saoFileName)
    {
        try
        {
            File saoFile = new File(saoFileName);
            
            ACL2Object.initHonsMananger(saoFile.getName());
            ACL2Reader sr = new ACL2Reader(saoFile);
            SvarName.Builder<Address> snb = new Address.SvarNameBuilder();
            Design<Address> design = new Design<>(snb, sr.root);
            DesignHints designHints = cls.newInstance();
            GenFsmNew gen = new GenFsmNew(designHints);
            SvexFunction.isFnSym(NIL); // for proper initialization of class SvexFunction
            for (ModName modName : design.modalist.keySet())
            {
                ParameterizedModule parMod = gen.matchParameterized(modName);
                if (parMod != null) {
                    Module<Address> m = parMod.genModule();
                    design.modalist.put(modName, m);
                }
            }
            File saoDir = saoFile.getParentFile();
            String outFileName = saoFileName.endsWith("-stripped.sao")
                ? saoFileName.substring(0, saoFileName.length() - "-stripped.sao".length())
                : saoFileName.endsWith(".sao")
                ? saoFileName.substring(0, saoFileName.length() - ".sao".length())
                : saoFileName;
            outFileName += "-restored.sao";
            File outFile = new File(saoDir, outFileName);
            ACL2Writer.write(design.getACL2Object(), outFile);
        } catch (InstantiationException | IllegalAccessException | IOException e)
        {
            System.out.println(e.getMessage());
        } finally
        {
            ACL2Object.closeHonsManager();
        }
    }

    private void showMods(String saoFileName, String[] modNames)
    {
        try
        {
            File saoFile = new File(saoFileName);
            ACL2Object.initHonsMananger(saoFile.getName());
            ACL2Reader sr = new ACL2Reader(saoFile);
            SvarName.Builder<Address> snb = new Address.SvarNameBuilder();
            Design<Address> design = new Design<>(snb, sr.root);
            for (String modNameStr : modNames)
            {
                ModName modName = ModName.valueOf(modNameStr);
                showMod(System.out, modName, design.modalist.get(modName));
            }
        } catch (IOException e)
        {
            System.out.println(e.getMessage());
        } finally
        {
            ACL2Object.closeHonsManager();
        }
    }

    public static void showMod(PrintStream out, ModName modName, Module<Address> mod)
    {
        out.println();
        out.println("module " + modName);
        for (Wire wire : mod.wires)
        {
            out.println("  wire " + wire);
        }
        for (ModInst inst : mod.insts)
        {
            out.println("  " + inst.modname + " " + inst.instname);
        }
        for (Assign<Address> assign : mod.assigns)
        {
            Lhs<Address> lhs = assign.lhs;
            Driver<Address> drv = assign.driver;
            out.print("  assign " + lhs + " = ");
            GenFsmNew.printSvex(out, 1, drv.svex);
        }
        for (Aliaspair<Address> aliaspair : mod.aliaspairs)
        {
            Lhs<Address> lhs = aliaspair.lhs;
            Lhs<Address> rhs = aliaspair.rhs;
            out.println("  alias " + lhs + " = " + rhs);
        }
        out.println("endmodule // " + modName);
    }

    public void main(String[] args)
    {
        if (args.length == 0)
        {
            help();
        }
        String command = args[0];
        switch (command)
        {
            case "showlibs":
                if (args.length != 2)
                {
                    help();
                }
                showLibs(args[1], false);
                break;
            case "showlibdb":
                if (args.length != 2)
                {
                    help();
                }
                showLibs(args[1], true);
                break;
            case "strip":
                if (args.length < 1)
                {
                    help();
                }
                strip(args[1]);
                break;
            case "restorepar":
                if (args.length < 1)
                {
                    help();
                }
                restoreParameterized(args[1]);
                break;
            case "showmod":
                if (args.length < 2)
                {
                    help();
                }
                showMods(args[1], Arrays.copyOfRange(args, 2, args.length));
                break;
            default:
                help();
        }
    }
}
