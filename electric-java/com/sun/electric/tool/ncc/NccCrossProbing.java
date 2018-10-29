/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: NccCrossProbing.java
 *
 * Copyright (c) 2005, Static Free Software. All rights reserved.
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
package com.sun.electric.tool.ncc;

import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.HierarchyEnumerator;
import com.sun.electric.database.hierarchy.Nodable;
import com.sun.electric.database.hierarchy.View;
import com.sun.electric.database.network.NetworkTool;
import com.sun.electric.database.variable.VarContext;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.io.output.CellModelPrefs;
import com.sun.electric.tool.ncc.result.NccResult;
import com.sun.electric.tool.ncc.result.NccResults;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class NccCrossProbing extends HierarchyEnumerator.Visitor {

    private Map<Cell,Integer> visitedCells = new HashMap<Cell,Integer>();
    private static Map<Cell,NccResult> results = new HashMap<Cell,NccResult>(); // keys are sch cells

    public static NccResult getResults(Cell cell) {
        return results.get(cell);
    }
    /** Run NCC in order to display layout simulation waveforms from a schematic.
     * @param cell the root schematic cell
     * @param context the VarContext for evaluating variables
     */
    public static void runNccSchematicCrossProbing(Cell cell, VarContext context) {
        if (!cell.isSchematic()) {
            System.out.println("Error: NCC for Schematic Cross-Probing must be run on a schematic");
            return;
        }
        results.clear();
        (new NccCrossProbeJob(cell, context)).startJob();
    }
    private static class NccCrossProbeJob extends Job {
        private Cell cell;
        private VarContext context;
        private NccCrossProbing visitor;
        private NccCrossProbeJob(Cell cell, VarContext context) {
            super("NccCrossProb", NetworkTool.getNetworkTool(), Job.Type.CLIENT_EXAMINE, null, null, Job.Priority.ANALYSIS);
            visitor = new NccCrossProbing();
            this.cell = cell;
            this.context = context;
        }
        @Override
        public boolean doIt() {
            HierarchyEnumerator.enumerateCell(cell, context, visitor);
            return true;
        }
    }

    // =====================================================================
    @Override
    public boolean enterCell(HierarchyEnumerator.CellInfo info) {
        Cell cell = info.getCell();

        if (visitedCells.containsKey(cell)) return false;
        visitedCells.put(cell, null);

        if (!cell.isSchematic()) {
            System.out.println("Why is there layout cell "+cell.describe(false)+" inside of a schematic?");
            return false;
        }

        // check if schematic has enumerate layout annotation
        if (!CellModelPrefs.spiceModelPrefs.isUseLayoutView(cell))
            return true;

        Cell layCell = cell.otherView(View.LAYOUT);
        if (layCell == null) return true;

        // run NCC
        NccOptions options = new NccOptions();
        options.operation = NccOptions.FLAT_TOP_CELL;
        options.checkSizes = false;
        options.maxMatchedEquivRecsToPrint = 0;
        options.maxMismatchedEquivRecsToPrint = 0;
        options.maxEquivRecMembersToPrint = 0;
        options.howMuchStatus = 0;
        NccResults result = Ncc.compare(cell, info.getContext(), layCell, info.getContext(), options);
        if (!result.match()) {
            System.out.println("Flat NCC of "+cell.describe(false)+" vs "+layCell.describe(false)+" Failed!\n"+
                    "   Its ayout will not be used for cross-probing.");
        } else {
            System.out.println("Flat NCC of "+cell.describe(false)+" vs "+layCell.describe(false)+" Passed");
            results.put(cell, result.getResultFromRootCells());
        }

        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }
    @Override
    public void exitCell(HierarchyEnumerator.CellInfo info) {
        //To change body of implemented methods use File | Settings | File Templates.
    }
    @Override
    public boolean visitNodeInst(Nodable ni, HierarchyEnumerator.CellInfo info) {
        return true;  //To change body of implemented methods use File | Settings | File Templates.
    }
}
