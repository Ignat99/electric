/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Update.java
 *
 * Copyright (c) 2003, Static Free Software. All rights reserved.
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

package com.sun.electric.tool.cvspm;

import com.sun.electric.database.hierarchy.Library;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.io.input.LibraryFiles;
import com.sun.electric.tool.io.output.DELIB;
import com.sun.electric.tool.user.User;
import com.sun.electric.tool.user.ui.TopLevel;
import com.sun.electric.tool.user.ui.WindowFrame;
import com.sun.electric.util.TextUtils;

import javax.swing.JOptionPane;
import java.io.*;
import java.util.*;

/**
 * User: gainsley
 * Date: Mar 13, 2006
 */
public class Update {

    public enum UpdateEnum {UPDATE("Update"), STATUS("Status"), ROLLBACK("Roll Back"), ROLLFORWARD("Roll Forward");
        String name;

        UpdateEnum(String n)
        {
            name = n;
        }
        String getMessage() {return name;}
    };

    // ------------------ Update/Status ---------------------

    /**
     * Update all libraries.
     * @param type the type of update to do
     */
    public static void updateProject(UpdateEnum type) {
        List<Library> allLibs = new ArrayList<Library>();
        for (Iterator<Library> it = Library.getLibraries(); it.hasNext(); ) {
            Library lib = it.next();
            if (lib.isHidden()) continue;
            if (!lib.isFromDisk()) continue;
            if (lib.getName().equals("spiceparts")) continue;
            allLibs.add(lib);
        }
        update(allLibs, null, type, true, true);
    }

    /**
     * Update all open libraries.
     * @param type the type of update to do
     */
    public static void updateOpenLibraries(UpdateEnum type) {
        List<Library> allLibs = new ArrayList<Library>();
        for (Iterator<Library> it = Library.getLibraries(); it.hasNext(); ) {
            Library lib = it.next();
            if (lib.isHidden()) continue;
            if (!lib.isFromDisk()) continue;
            if (lib.getName().equals("spiceparts")) continue;
            allLibs.add(lib);
        }
        update(allLibs, null, type, false, true);
    }

    /**
     * Update all Cells from a library.
     * @param lib
     * @param type the type of update to do
     */
    public static void updateLibrary(Library lib, UpdateEnum type) {
        List<Library> libsToUpdate = new ArrayList<Library>();
        libsToUpdate.add(lib);
        update(libsToUpdate, null, type, false, false);
    }

    /**
     * Update a Cell.
     * @param cell
     * @param type the type of update to do
     */
    public static void updateCell(Cell cell, UpdateEnum type) {
        List<Cell> cellsToUpdate = new ArrayList<Cell>();
        cellsToUpdate.add(cell);
        update(null, cellsToUpdate, type, false, false);
    }

    /**
     * Run Update/Status/Rollback on the libraries and cells
     * @param libs
     * @param cells
     * @param type
     * @param updateProject
     * @param checkEditors
     */
    public static void update(List<Library> libs, List<Cell> cells, UpdateEnum type,
                              boolean updateProject, boolean checkEditors) {
        if (libs == null) libs = new ArrayList<Library>();
        if (cells == null) cells = new ArrayList<Cell>();

        // make sure cells are part of a DELIB
        CVSLibrary.LibsCells bad = CVSLibrary.notFromDELIB(cells);
        if (type == UpdateEnum.STATUS) {
            // remove offending cells
            for (Cell cell : bad.cells) cells.remove(cell);
        } else if (bad.cells.size() > 0) {
            CVS.showError("Error: the following Cells are not part of a DELIB library and cannot be acted upon individually",
                    "CVS "+type.getMessage()+" Error", bad.libs, bad.cells);
            return;
        }

        // make sure the selecetd objecs are in cvs
        bad = CVSLibrary.getNotInCVS(libs, cells);
        // for STATUS, remove libraries not in cvs, and also set their state unknown
        if (type == UpdateEnum.STATUS) {
            for (Library lib : bad.libs) {
                libs.remove(lib);
                CVSLibrary.setState(lib, State.UNKNOWN);
            }
            for (Cell cell : bad.cells) {
                cells.remove(cell);
                CVSLibrary.setState(cell, State.UNKNOWN);
            }
        } else if (bad.libs.size() > 0 || bad.cells.size() > 0) {
            // if any of them not in cvs, issue error and abort
            CVS.showError("Error: the following Libraries or Cells are not in CVS",
                    "CVS "+type.getMessage()+" Error", bad.libs, bad.cells);
            return;
        }

        // optimize a little, remove cells from cells list if cell's lib in libs list
        CVSLibrary.LibsCells good = CVSLibrary.consolidate(libs, cells);

        // for update or rollback, make sure they are also not modified
        if (type == UpdateEnum.UPDATE) {
            bad = CVSLibrary.getModified(libs, cells);
            if (bad.libs.size() > 0 || bad.cells.size() > 0) {
                String [] choices = new String [] { "Continue Anyway", "Cancel" };
                int choice = CVS.askForChoice("Warning: Unsaved changes may be lost!  For:",
                        "CVS "+type.getMessage()+" Warning!",
                        bad.libs, bad.cells, choices, choices[1]);
                if (choice == 1) return;
            }
        }
        // issue final warning for rollback
        if (type == UpdateEnum.ROLLBACK) {
            String [] choices = new String [] { "Continue Anyway", "Cancel" };
            int choice = CVS.askForChoice("Warning: Saved and Unsaved changes will be lost!  For:",
                    "CVS "+type.getMessage()+" Warning!",
                    good.libs, good.cells, choices, choices[1]);
            if (choice == 1) return;
        }

        (new UpdateJob(good.cells, good.libs, type, updateProject, checkEditors)).startJob();
    }

    private static class UpdateJob extends Job {
        private List<Cell> cellsToUpdate;
        private List<Library> librariesToUpdate;
        private UpdateEnum type;
        private List<Library> libsToReload;
        private boolean updateProject;                // update whole project
        private int exitVal;
        private boolean inJob;
        private boolean checkEditors;
        private String cvsProgram = CVS.getCVSProgram();
        private String repository = CVS.getRepository();

        /**
         * Update cells and/or libraries.
         * @param cellsToUpdate
         * @param librariesToUpdate
         */
        private UpdateJob(List<Cell> cellsToUpdate, List<Library> librariesToUpdate,
                          UpdateEnum type, boolean updateProject, boolean checkEditors) {
            super("CVS Update Library, " + type.getMessage(), User.getUserTool(),
                ((type==UpdateEnum.STATUS)?Job.Type.CLIENT_EXAMINE:Job.Type.CHANGE), null, null, Job.Priority.USER);
            this.cellsToUpdate = cellsToUpdate;
            this.librariesToUpdate = librariesToUpdate;
            this.type = type;
            this.updateProject = updateProject;
            this.checkEditors = checkEditors;
            exitVal = -1;
            inJob = true;
            if (this.cellsToUpdate == null) this.cellsToUpdate = new ArrayList<Cell>();
            if (this.librariesToUpdate == null) this.librariesToUpdate = new ArrayList<Library>();
        }
        public boolean doIt() {
            String useDir = CVS.getUseDir(librariesToUpdate, cellsToUpdate);
            StringBuffer libs = CVS.getLibraryFiles(librariesToUpdate, useDir);
            StringBuffer cells = CVS.getCellFiles(cellsToUpdate, useDir);

            // disable lastModified for now, since users with older versions
            // of electric will not commit new lastModified file,
            // and then users of new electric will not get updated files
            //
            // Also, last modified won't work, because user can commit cell A,
            // then commit cell B, then rollback cell A.  Electric can't tell
            // whether or not to also rollback lastModified file

            List<File> backupFiles = new ArrayList<File>();
            if (type == UpdateEnum.ROLLFORWARD) {
                // build list of files to back up
                for (Library lib : librariesToUpdate) {
                    File libFile = TextUtils.getFile(lib.getLibFile());
                    if (libFile == null) continue;
                    if (CVS.isDELIB(lib)) {
                        // add all cell files and header file
                        for (Iterator<Cell> it = lib.getCells(); it.hasNext(); ) {
                            backupFiles.add(CVS.getCellFile(it.next()));
                        }
                        backupFiles.add(new File(libFile, DELIB.getHeaderFile()));
                    } else {
                        backupFiles.add(libFile);
                    }
                }
                for (Cell cell : cellsToUpdate) {
                    backupFiles.add(CVS.getCellFile(cell));
                }
                // move all the files out of the way
                for (File f : backupFiles) {
                    File newf = new File(f.getAbsolutePath()+".ecvstemp");
                    if (!f.exists()) continue;
                    if (!f.renameTo(newf)) {
                        System.out.println("Could not rename file "+f+" to "+newf);
                    } else {
                        System.out.println("Renamed "+f+" to "+newf);
                    }
                }
            }

            String updateFiles = libs.toString() + " " + cells.toString();
            if (updateFiles.trim().equals("") && !updateProject) {
                exitVal = 0;
                if (inJob) fieldVariableChanged("exitVal");
                System.out.println("Nothing to "+type.getMessage());
                return true;
            }

            if (updateProject && (type == UpdateEnum.UPDATE || type == UpdateEnum.STATUS)) updateFiles = "";
            StatusResult result = update(cvsProgram, repository, updateFiles, useDir, type);
            commentStatusResult(result, type);
            exitVal = result.getExitVal();

            if (type == UpdateEnum.ROLLFORWARD) {
                // even if update failed, restore user's files
                for (File f : backupFiles) {
                    File newf = new File(f.getAbsolutePath()+".ecvstemp");
                    if (newf.exists())
                        if (!newf.renameTo(f)) {
                            System.out.println("Error: unabled to rename "+newf+" to "+f);
                        } else {
                            System.out.println("Renamed "+newf+" to "+f);
                        }
                }
                // reload status
                result = update(cvsProgram, repository, updateFiles, useDir, UpdateEnum.STATUS);
                commentStatusResult(result, type);
            }

            if (inJob) fieldVariableChanged("exitVal");
            if (exitVal != 0 && exitVal != 1) {
                return true;
            }

            // reload libs if needed
            libsToReload = new ArrayList<Library>();
            if (type != UpdateEnum.STATUS && type != UpdateEnum.ROLLFORWARD) {
                for (Cell cell : result.getCells(State.UPDATE)) {
                    Library lib = cell.getLibrary();
                    if (!libsToReload.contains(lib))
                        libsToReload.add(lib);
                }
                for (int i = 0; i < libsToReload.size(); i++) {
                    Library lib = libsToReload.get(i);
                    String libName = lib.getName();
                    LibraryFiles.reloadLibrary(getEditingPreferences(), lib);
                    libsToReload.set(i, Library.findLibrary(libName));
                }
            }
/*
            if (type == UpdateEnum.ROLLBACK) {
                // turn off edit for rolled back cells
                for (Cell cell : result.getCells(State.UPDATE)) {
                    CVSLibrary.setEditing(cell, false);
                }
            }
*/
            // update states
            updateStates(result, type);
            System.out.println(type.getMessage()+" complete.");
            if (inJob) fieldVariableChanged("libsToReload");
            return true;
        }
        public void terminateOK() {
            if (exitVal != 0 && exitVal != 1) {
                Job.getUserInterface().showErrorMessage("CVS "+type.getMessage()+
                        " Failed (exit status "+exitVal+")!  Please see messages window","CVS "+type.getMessage()+" Failed!");
                return;
            }
            WindowFrame.wantToRedoLibraryTree();
            CVS.fixStaleCellReferences(libsToReload);
            if (checkEditors) {
                Edit.editConsistencyCheck(librariesToUpdate, cellsToUpdate);
            }
        }
    }

    static void statusNoJob(List<Library> libs, List<Cell> cells, boolean updateProject) {
        UpdateJob job = new UpdateJob(cells, libs, UpdateEnum.STATUS, updateProject, false);
        job.inJob = false;
        job.doIt();
    }

    /**
     * Update the given file in the given directory.
     * @param cvsProgram the name of CVS program
     * @param repository the path to CVS repository
     * @param file the name of the file.
     * @param dir the directory.
     * @return parsed output from running CVS.
     */
    protected static StatusResult update(String cvsProgram, String repository, String file, String dir, UpdateEnum type) {
        String command = "-q update -d -P ";
        String message = "Running CVS Update";
        if (type == UpdateEnum.STATUS) {
            command = "-nq update -d -P ";
            message = "Running CVS Status";
        }
        if (type == UpdateEnum.ROLLBACK) {
            command = "-q update -C -P ";
            message = "Rollback from CVS";
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int exitVal = CVS.runCVSCommand(cvsProgram, repository, command+file, message,
                    dir, out);
        LineNumberReader result = new LineNumberReader(new InputStreamReader(new ByteArrayInputStream(out.toByteArray())));
        return parseOutput(result, exitVal);
    }

    private static void updateStates(StatusResult result, UpdateEnum type) {
        for (Cell cell : result.getCells(State.ADDED)) {
            CVSLibrary.setState(cell, State.ADDED);
        }
        for (Cell cell : result.getCells(State.REMOVED)) {
            CVSLibrary.setState(cell, State.REMOVED);
        }
        for (Cell cell : result.getCells(State.MODIFIED)) {
            CVSLibrary.setState(cell, State.MODIFIED);
        }
        for (Cell cell : result.getCells(State.CONFLICT)) {
            CVSLibrary.setState(cell, State.CONFLICT);
        }
        for (Cell cell : result.getCells(State.UPDATE)) {
            if (type == UpdateEnum.STATUS)
                CVSLibrary.setState(cell, State.UPDATE);
            else
                CVSLibrary.setState(cell, State.NONE);
        }
        for (Cell cell : result.getCells(State.UNKNOWN)) {
            CVSLibrary.setState(cell, State.UNKNOWN);
        }

    }

    // -------------------- Rollback ----------------------------

    public static void rollback(Cell cell) {
        int ret = JOptionPane.showConfirmDialog(TopLevel.getCurrentJFrame(),
                "WARNING! Disk file for Cell "+cell.libDescribe()+" will revert to latest CVS version!\n"+
                "All uncommited changes will be lost!!!  Continue anyway?", "Rollback Cell", JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (ret == JOptionPane.NO_OPTION) return;

        updateCell(cell, UpdateEnum.ROLLBACK);
    }

    public static void rollback(Library lib) {
        int ret = JOptionPane.showConfirmDialog(TopLevel.getCurrentJFrame(),
                "WARNING! Disk file(s) for Library"+lib.getName()+" will revert to latest CVS version!\n"+
                "All uncommited changes will be lost!!!  Continue anyway?", "Rollback Library", JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (ret == JOptionPane.NO_OPTION) return;

        updateLibrary(lib, UpdateEnum.ROLLBACK);
    }

    // ---------------------- Output Parsing -------------------------

    /**
     * Parse the output of an 'cvs -nq update' command, which
     * checks the status of the given files.
     * Returns true if all files are up-to-date, false otherwise
     * @param reader
     * @return true if files are up-to-date
     */
    private static StatusResult parseOutput(LineNumberReader reader, int exitVal) {
        StatusResult result = new StatusResult(exitVal);
        for (;;) {
            String line;
            try {
                line = reader.readLine();
            } catch (IOException e) {
                System.out.println(e.getMessage());
                return result;
            }
            if (line == null) break;
            if (line.equals("")) continue;

            String parts[] = line.split("\\s+");
            if (parts.length != 2) continue;
            State state = State.getState(parts[0]);
            if (state == null) continue;
            if (state == State.PATCHED) state = State.UPDATE;

            // find Cell for filename
            String filename = parts[1];
            File file = new File(filename);
            if (filename.toLowerCase().endsWith(".jelib")) {
                // jelib library file, set state of all cells
                String endfile = file.getName();
                Library lib = Library.findLibrary(endfile.substring(0, endfile.length()-6));
                if (lib == null) continue;
                CVSLibrary.setState(lib, state);
            }
            Cell cell = CVS.getCellFromPath(filename);
            if (cell != null) {
                result.addCell(state, cell);
                continue;
            }
            Library lib = CVS.getLibraryFromHeader(filename);
            if (lib != null) {
                result.addLibraryHeaderFile(state, lib);
                continue;
            }
            // default action
            result.addUnknownFile(state, filename);
        }
        return result;
    }

    /**
     * Parse the output of an 'cvs -nq update' command, which
     * checks the status of the given files.
     * Returns true if all files are up-to-date, false otherwise
     */
    public static void commentStatusResult(StatusResult result, UpdateEnum type) {
        boolean allFilesUpToDate = true;
        for (Cell cell : result.getCells(State.ADDED)) {
            System.out.println("Added\t"+cell.libDescribe());
            allFilesUpToDate = false;
        }
        for (Cell cell : result.getCells(State.REMOVED)) {
            System.out.println("Removed\t"+cell.libDescribe());
            allFilesUpToDate = false;
        }
        for (Cell cell : result.getCells(State.MODIFIED)) {
            System.out.println("Modified\t"+cell.libDescribe());
            allFilesUpToDate = false;
        }
        for (String file : result.getUnknownFiles(State.MODIFIED)) {
            System.out.println("Modified\t"+file);
            allFilesUpToDate = false;
        }
        for (Cell cell : result.getCells(State.CONFLICT)) {
            System.out.println("Conflicts\t"+cell.libDescribe());
            allFilesUpToDate = false;
        }
        for (String file : result.getUnknownFiles(State.CONFLICT)) {
            System.out.println("Conflicts\t"+file);
            allFilesUpToDate = false;
        }
        for (Cell cell : result.getCells(State.UPDATE)) {
            if (type == UpdateEnum.STATUS)
                System.out.println("NeedsUpdate\t"+cell.libDescribe());
            if (type == UpdateEnum.UPDATE)
                System.out.println("Updated\t"+cell.libDescribe());
            allFilesUpToDate = false;
        }
        for (String file : result.getUnknownFiles(State.UPDATE)) {
            if (type == UpdateEnum.STATUS)
                System.out.println("NeedsUpdate\t"+file);
            if (type == UpdateEnum.UPDATE)
                System.out.println("Updated\t"+file);
            allFilesUpToDate = false;
        }
        if (type == UpdateEnum.STATUS) {
            if (allFilesUpToDate) System.out.println("All files up-to-date");
            else System.out.println("All other files up-to-date");
        }
    }

    public static class StatusResult {
        private Map<State,List<Cell>> cells;
        private Map<State,List<String>> unknownFiles;
        private Map<State,List<Library>> headerFiles;
        private int exitVal;

        private StatusResult(int exitVal) {
            cells = new HashMap<State,List<Cell>>();
            headerFiles = new HashMap<State,List<Library>>();
            unknownFiles = new HashMap<State,List<String>>();
            this.exitVal = exitVal;
        }
        private void addCell(State state, Cell cell) {
            List<Cell> statecells = cells.get(state);
            if (statecells == null) {
                statecells = new ArrayList<Cell>();
                cells.put(state, statecells);
            }
            statecells.add(cell);
        }
        public List<Cell> getCells(State state) {
            List<Cell> statecells = cells.get(state);
            if (statecells == null)
                statecells = new ArrayList<Cell>();
            return statecells;
        }
        public void addLibraryHeaderFile(State state, Library lib) {
            List<Library> statelibs = headerFiles.get(state);
            if (statelibs == null) {
                statelibs = new ArrayList<Library>();
                headerFiles.put(state, statelibs);
            }
            statelibs.add(lib);
        }
        public List<Library> getLibraryHeaderFiles(State state) {
            List<Library> statelibs = headerFiles.get(state);
            if (statelibs == null)
                statelibs = new ArrayList<Library>();
            return statelibs;
        }
        public void addUnknownFile(State state, String file) {
            List<String> list = unknownFiles.get(state);
            if (list == null) {
                list = new ArrayList<String>();
                unknownFiles.put(state, list);
            }
            list.add(file);
        }
        public List<String> getUnknownFiles(State state) {
            List<String> list = unknownFiles.get(state);
            if (list == null)
                list = new ArrayList<String>();
            return list;
        }
        public int getExitVal() { return exitVal; }
    }

}
