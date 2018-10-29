/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: PublicDebugMenu.java
 *
 * Copyright (c) 2011, Static Free Software. All rights reserved.
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
package com.sun.electric.tool.user.menus;

import com.sun.electric.database.EditingPreferences;
import com.sun.electric.database.Snapshot;
import static com.sun.electric.tool.user.menus.EMenuItem.SEPARATOR;

import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.EDatabase;
import com.sun.electric.database.id.CellId;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.JobException;
import com.sun.electric.tool.drc.MinArea;
import com.sun.electric.tool.io.FileType;
import com.sun.electric.tool.io.input.JELIB2;
import com.sun.electric.tool.io.input.spicenetlist.SpiceNetlistReader;
import com.sun.electric.tool.routing.Routing;
import com.sun.electric.tool.routing.SeaOfGates;
import com.sun.electric.tool.routing.seaOfGates.SeaOfGatesEngine;
import com.sun.electric.tool.routing.seaOfGates.SeaOfGatesEngineFactory;
import com.sun.electric.tool.routing.seaOfGates.SeaOfGatesHandlers;
import com.sun.electric.tool.simulation.acl2.modsext.ACL2DesignJobs;
import com.sun.electric.tool.simulation.acl2.modsext.DesignHints;
import com.sun.electric.tool.simulation.acl2.modsext.GenFsmNew;
import com.sun.electric.tool.simulation.acl2.modsext.TraceSvtvJobs;
import com.sun.electric.tool.simulation.acl2.modsext.TutorialHints;
import com.sun.electric.tool.user.User;
import com.sun.electric.tool.user.UserInterfaceMain;
import com.sun.electric.tool.user.dialogs.OpenFile;
import static com.sun.electric.tool.user.menus.FileMenu.importLibraryCommand;
import com.sun.electric.util.TextUtils;
import com.sun.electric.util.acl2.ACL2Object;
import com.sun.electric.util.acl2.ACL2Reader;
import com.sun.electric.util.acl2.GenPkgImports;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URL;

/**
 *
 * @author Felix Schmdit
 *
 */
public class PublicDebugMenu
{

    static EMenuItem makeMenu()
    {
        return new EMenu("Debug",
            // SEPARATOR,
            new EMenu("ACL2",
                new EMenuItem("Gen phase FSM for Tutorial")
            {
                @Override
                public void run()
                {
                    String saoPath = OpenFile.chooseInputFile(FileType.SAO, "Serialized SVEX design", false);
                    if (saoPath == null)
                        return;
                    URL fileURL = TextUtils.makeURLToFile(saoPath);
                    File f = TextUtils.getFile(fileURL);
                    String designName = TextUtils.getFileNameWithoutExtension(saoPath);
                    GenFsmNew.genFsm(TutorialHints.class, f, designName);
                }
            },
                new EMenuItem("Import _SAO...")
            {
                public void run()
                {
                    importLibraryCommand(FileType.SAO, false, false, false, false);
                }
            },
                new EMenuItem("Count objects in SAO file")
            {
                @Override
                public void run()
                {
                    String saoPath = OpenFile.chooseInputFile(FileType.SAO, "Serialized ACL2", false);
                    if (saoPath == null)
                        return;
                    URL fileURL = TextUtils.makeURLToFile(saoPath);
                    File f = TextUtils.getFile(fileURL);
                    String saoName = TextUtils.getFileNameWithoutPath(saoPath);
                    try
                    {
                        ACL2Object.initHonsMananger(saoName);
                        System.out.println(saoName + " contains " + new ACL2Reader(f).getStats());
                    } catch (IOException e)
                    {
                        System.out.println(e);
                    } finally
                    {
                        ACL2Object.closeHonsManager();
                    }
                }
            },
                new EMenuItem("Generate pkg-imports.dat")
            {
                @Override
                public void run()
                {
                    String saoPath = OpenFile.chooseInputFile(FileType.SAO, "Serialized pkg-imports", false);
                    if (saoPath == null)
                        return;
                    URL fileURL = TextUtils.makeURLToFile(saoPath);
                    File f = TextUtils.getFile(fileURL);
                    GenPkgImports.gen(f);
                }
            },
                new EMenuItem("Dump SVEX design")
            {
                @Override
                public void run()
                {
                    String saoPath = OpenFile.chooseInputFile(FileType.SAO, "Serialized SVEX design", false);
                    if (saoPath == null)
                        return;
                    URL fileURL = TextUtils.makeURLToFile(saoPath);
                    File f = TextUtils.getFile(fileURL);
                    String defaultOutName = User.getWorkingDirectory()
                        + File.separator + TextUtils.getFileNameWithoutExtension(saoPath) + ".txt";
                    String outPath = OpenFile.chooseOutputFile(FileType.TEXT, "SVEX design dump", defaultOutName);
                    if (outPath == null)
                        return;
                    ACL2DesignJobs.dump(DesignHints.Dummy.class, f, outPath);
                }
            },
                new EMenuItem("Dump SVEX design from Tutorial")
            {
                @Override
                public void run()
                {
                    String saoPath = OpenFile.chooseInputFile(FileType.SAO, "Serialized SVEX design", false);
                    if (saoPath == null)
                        return;
                    URL fileURL = TextUtils.makeURLToFile(saoPath);
                    File f = TextUtils.getFile(fileURL);
                    String defaultOutName = User.getWorkingDirectory()
                        + File.separator + TextUtils.getFileNameWithoutExtension(saoPath) + ".txt";
                    String outPath = OpenFile.chooseOutputFile(FileType.TEXT, "SVEX design dump", defaultOutName);
                    if (outPath == null)
                        return;
                    ACL2DesignJobs.dump(TutorialHints.class, f, outPath);
                }
            },
                new EMenuItem("Make Trace Svtv script for Tutorial")
            {
                @Override
                public void run()
                {
                    String saoPath = OpenFile.chooseInputFile(FileType.SAO, "Serialized SVEX design", false);
                    if (saoPath == null)
                        return;
                    URL fileURL = TextUtils.makeURLToFile(saoPath);
                    File f = TextUtils.getFile(fileURL);
                    String designName = TextUtils.getFileNameWithoutExtension(saoPath);
                    TraceSvtvJobs.makeTraceSvtv(TutorialHints.class, f, designName);
                }
            },
                new EMenuItem("Read Trace Svtv for Tutorial")
            {
                @Override
                public void run()
                {
                    String saoPath = OpenFile.chooseInputFile(FileType.SAO, "Serialized SVEX design", false);
                    if (saoPath == null)
                        return;
                    URL fileURL = TextUtils.makeURLToFile(saoPath);
                    File f = TextUtils.getFile(fileURL);
                    TraceSvtvJobs.readTraceSvtv(TutorialHints.class, f);
                }
            },
                new EMenuItem("Test Trace Svtv for Tutorial")
            {
                @Override
                public void run()
                {
                    String saoPath = OpenFile.chooseInputFile(FileType.SAO, "Serialized SVEX design", false);
                    if (saoPath == null)
                        return;
                    URL fileURL = TextUtils.makeURLToFile(saoPath);
                    File f = TextUtils.getFile(fileURL);
                    TraceSvtvJobs.testTraceSvtv(TutorialHints.class, f);
                }
            },
                new EMenuItem("Dedup SVEX design")
            {
                @Override
                public void run()
                {
                    String saoPath = OpenFile.chooseInputFile(FileType.SAO, "Serialized SVEX design", false);
                    if (saoPath == null)
                        return;
                    URL fileURL = TextUtils.makeURLToFile(saoPath);
                    File f = TextUtils.getFile(fileURL);
                    String designName = TextUtils.getFileNameWithoutExtension(saoPath);
                    String defaultOutName = User.getWorkingDirectory()
                        + File.separator + designName + "-dedup.lisp";
                    String outPath = OpenFile.chooseOutputFile(FileType.LISP, "LISP with deduplication of SVEX", defaultOutName);
                    if (outPath == null)
                        return;
                    ACL2DesignJobs.dedup(f, designName, outPath);
                }
            },
                new EMenuItem("Show SVEX assigns")
            {
                @Override
                public void run()
                {
                    String saoPath = OpenFile.chooseInputFile(FileType.SAO, "Serialized SVEX design", false);
                    if (saoPath == null)
                        return;
                    URL fileURL = TextUtils.makeURLToFile(saoPath);
                    File f = TextUtils.getFile(fileURL);
                    String designName = TextUtils.getFileNameWithoutExtension(saoPath);
                    String defaultOutName = User.getWorkingDirectory()
                        + File.separator + designName + "-assignes.lisp";
                    String outPath = OpenFile.chooseOutputFile(FileType.LISP, "LISP with assigns of SVEX", defaultOutName);
                    if (outPath == null)
                        return;
                    ACL2DesignJobs.showAssigns(f, designName, outPath);
                }
            },
                new EMenuItem("Named -> Indexed")
            {
                @Override
                public void run()
                {
                    String saoPath = OpenFile.chooseInputFile(FileType.SAO, "Serialized SVEX design", false);
                    if (saoPath == null)
                        return;
                    URL fileURL = TextUtils.makeURLToFile(saoPath);
                    File f = TextUtils.getFile(fileURL);
                    String designName = TextUtils.getFileNameWithoutExtension(saoPath);
                    ACL2DesignJobs.namedToIndexed(f, designName);
                }
            },
                new EMenuItem("Normalize named assigns")
            {
                @Override
                public void run()
                {
                    String saoPath = OpenFile.chooseInputFile(FileType.SAO, "Serialized SVEX design", false);
                    if (saoPath == null)
                        return;
                    URL fileURL = TextUtils.makeURLToFile(saoPath);
                    File f = TextUtils.getFile(fileURL);
                    String designName = TextUtils.getFileNameWithoutExtension(saoPath);
                    ACL2DesignJobs.normalizeAssigns(f, designName, false);
                }
            },
                new EMenuItem("Normalize indexed assigns")
            {
                @Override
                public void run()
                {
                    String saoPath = OpenFile.chooseInputFile(FileType.SAO, "Serialized SVEX design", false);
                    if (saoPath == null)
                        return;
                    URL fileURL = TextUtils.makeURLToFile(saoPath);
                    File f = TextUtils.getFile(fileURL);
                    String designName = TextUtils.getFileNameWithoutExtension(saoPath);
                    ACL2DesignJobs.normalizeAssigns(f, designName, true);
                }
            },
                new EMenuItem("Gen FSM for ALU")
            {
                @Override
                public void run()
                {
                    String saoPath = OpenFile.chooseInputFile(FileType.SAO, "Serialized ALU design", false);
                    if (saoPath == null)
                        return;
                    URL fileURL = TextUtils.makeURLToFile(saoPath);
                    File f = TextUtils.getFile(fileURL);
                    String defaultOutName = "alu-auto.lisp";
                    String outPath = OpenFile.chooseOutputFile(FileType.LISP, "ALU FSM in ACL2", defaultOutName);
                    if (outPath == null)
                        return;
                    ACL2DesignJobs.genAlu(f, outPath);
                }
            },
                new EMenuItem("Show Tutorial Libs")
            {
                @Override
                public void run()
                {
                    String saoPath = OpenFile.chooseInputFile(FileType.SAO, "Serialized Tutorial design", false);
                    if (saoPath == null)
                        return;
                    URL fileURL = TextUtils.makeURLToFile(saoPath);
                    File f = TextUtils.getFile(fileURL);
                    ACL2DesignJobs.showTutorialSvexLibs(f);
                }
            }),
            new EMenu("Spice",
                new EMenuItem("Read Spice")
            {
                @Override
                public void run()
                {
                    String spicePath = OpenFile.chooseInputFile(FileType.SPICE, "Spice deck", false);
                    if (spicePath == null)
                        return;
                    SpiceNetlistReader reader = new SpiceNetlistReader(true);
                    try
                    {
                        reader.readFile(spicePath, true);
                    } catch (FileNotFoundException e)
                    {
                        System.out.println(e.getMessage());
                    }

                    reader.writeFile("/tmp/output.spi");
                }
            }),
            new EMenu("DRC",
                new EMenuItem("Check _Minimum Area...")
            {
                public void run()
                {
                    MinArea.checkMinareaLay();
                }
            }, new EMenuItem("Import Minimum Area _Test...")
            {
                public void run()
                {
                    MinArea.readMinareaLay();
                }
            }, new EMenuItem("E_xport Minimum Area Test...")
            {
                public void run()
                {
                    MinArea.writeMinareaLay();
                }
            }),
            new EMenu("Fast JELIB reader",
                jelibItem("Database", true, true, true, true, true),
                jelibItem("Snapshot", true, true, true, true, false),
                jelibItem("primitiveBounds", true, true, true, false, false),
                jelibItem("doBackup", true, true, false, false, false),
                jelibItem("instantiate", true, false, false, false, false),
                jelibItem("only parse", false, false, false, false, false)),
            new EMenu("SeaOfGatesRouter",
                sogItem("Animation", SeaOfGatesHandlers.Save.SAVE_SNAPSHOTS),
                sogItem("Partial-Animation", SeaOfGatesHandlers.Save.SAVE_PERIODIC),
                sogItem("Once", SeaOfGatesHandlers.Save.SAVE_ONCE),
                SEPARATOR,
                sogItem("Dummy on CHANGE", Job.Type.CHANGE),
                sogItem("Dummy on SERVER_EXAMINE", Job.Type.SERVER_EXAMINE),
                sogItem("Dummy on CLIENT_EXAMINE", Job.Type.CLIENT_EXAMINE),
                new EMenuItem("Dummy on client")
            {
                @Override
                public void run()
                {
                    Cell cell = Job.getUserInterface().needCurrentCell();
                    if (cell == null)
                        return;
                    routeIt(cell, UserInterfaceMain.getEditingPreferences(), System.out);
                }
            },
                new EMenuItem("Dummy in Thread")
            {
                @Override
                public void run()
                {
                    Cell cell = Job.getUserInterface().needCurrentCell();
                    if (cell == null)
                        return;
                    new SeaOfGatesThread(cell, UserInterfaceMain.getEditingPreferences()).start();
                }
            }));
    }

    // Dima's menu items
    private static EMenuItem jelibItem(String text,
        final boolean instantiate,
        final boolean doBackup,
        final boolean getPrimitiveBounds,
        final boolean doSnapshot,
        final boolean doDatabase)
    {
        return new EMenuItem(text)
        {

            @Override
            public void run()
            {
                JELIB2.newJelibReader(instantiate, doBackup, getPrimitiveBounds, doSnapshot, doDatabase);
            }
        };
    }

    private static EMenuItem sogItem(String text, final SeaOfGatesHandlers.Save save)
    {
        return new EMenuItem(text)
        {

            @Override
            public void run()
            {
                Cell cell = Job.getUserInterface().needCurrentCell();
                if (cell != null)
                {
                    SeaOfGatesHandlers.startInJob(cell, null, SeaOfGatesEngineFactory.SeaOfGatesEngineType.defaultVersion, save);
                }
            }
        };
    }

    private static EMenuItem sogItem(String text, final Job.Type jobType)
    {
        return new EMenuItem(text)
        {

            @Override
            public void run()
            {
                Cell cell = Job.getUserInterface().needCurrentCell();
                if (cell != null)
                {
                    new SeaOfGatesJob(cell, jobType).startJob();
                }
            }
        };
    }

    /**
     * Class to run sea-of-gates routing in a separate Job.
     */
    private static class SeaOfGatesJob extends Job
    {

        private final Cell cell;

        protected SeaOfGatesJob(Cell cell, Job.Type jobType)
        {
            super("Sea-Of-Gates Route", Routing.getRoutingTool(), jobType, null, null, Job.Priority.USER);
            this.cell = cell;
        }

        @Override
        public boolean doIt() throws JobException
        {
            routeIt(cell, getEditingPreferences(), System.out);
            return true;
        }
    }

    private static class SeaOfGatesThread extends Thread
    {
        private final Snapshot snapshot;
        private final CellId cellId;
        private final EditingPreferences ep;

        private SeaOfGatesThread(Cell cell, EditingPreferences ep)
        {
            snapshot = cell.getDatabase().backup();
            cellId = cell.getId();
            this.ep = ep;
        }

        @Override
        public void run()
        {
            EDatabase database = new EDatabase(snapshot, "dummy");
            Cell cell = database.getCell(cellId);
            routeIt(cell, ep, System.err);
        }
    }

    private static void routeIt(Cell cell, EditingPreferences ep, PrintStream out)
    {
        SeaOfGatesEngine router = SeaOfGatesEngineFactory.createSeaOfGatesEngine();
        SeaOfGates.SeaOfGatesOptions prefs = new SeaOfGates.SeaOfGatesOptions();
        prefs.useParallelRoutes = true;
        router.setPrefs(prefs);
        SeaOfGatesEngine.Handler handler = SeaOfGatesHandlers.getDummy(ep, out);
        router.routeIt(handler, cell, false);
    }
}
