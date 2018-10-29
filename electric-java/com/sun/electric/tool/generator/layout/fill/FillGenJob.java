/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: FillGenJob.java
 *
 * Copyright (c) 2006, Static Free Software. All rights reserved.
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
package com.sun.electric.tool.generator.layout.fill;

import com.sun.electric.database.EditingPreferences;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.user.ErrorLogger;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.Library;
import com.sun.electric.tool.extract.LayerCoverageTool;

/**
 * User: gg151869
 * Date: Sep 19, 2006
 */
public class FillGenJob extends Job
{

    protected FillGenConfig fillGenConfig;
    protected Cell topCell;
    protected ErrorLogger log;
    private boolean doItNow;
    protected LayerCoverageTool.LayerCoveragePreferences lcp;

    public FillGenJob(Cell cell, FillGenConfig gen, boolean doItNow, LayerCoverageTool.LayerCoveragePreferences lcp)
    {
        super("Fill generator job", null, Type.CHANGE, null, null, Priority.USER);
        this.fillGenConfig = gen;
        this.topCell = cell; // Only if 1 cell is generated.
        this.doItNow = doItNow;
        this.lcp = lcp;

        assert(fillGenConfig.evenLayersHorizontal);

        if (doItNow) // call from regressions
        {
            timer.start();
            try
            {
                if (doIt())
                    terminateOK();

            } catch (Exception e)
            {
                e.printStackTrace();
            }
        }
        else
            startJob(); // queue the job
    }

    public ErrorLogger getLogger() { return log; }

    public Library getAutoFilLibrary()
    {
        return Library.findLibrary(fillGenConfig.fillLibName);
    }

    public void terminateOK()
    {
        log.termLogging(false);
        timer.end();
        int errorCount = log.getNumErrors();
        int warnCount = log.getNumWarnings();
        System.out.println(errorCount + " errors and " + warnCount + " warnings found (took " + timer + ")");
    }

    protected FillGeneratorTool setUpJob()
    {
        FillGeneratorTool fillGen = FillGeneratorTool.getTool();
        fillGen.setConfig(fillGenConfig);

        // logger must be created in server otherwise it won't return the elements.
        log = ErrorLogger.newInstance("Fill");
        if (!doItNow)
            fieldVariableChanged("log");

        fillGenConfig.job = this; // to abort job.
        return fillGen;
    }

    /**
     * Implementation of Job.doIt() running only the doTemplateFill function. GNU version
     * @return true if ran without errors.
     */
    @Override
    public boolean doIt()
    {
        FillGeneratorTool fillGen = setUpJob();
        return doTemplateFill(fillGen, getEditingPreferences());
    }

    public boolean doTemplateFill(FillGeneratorTool fillGen, EditingPreferences ep)
    {
        fillGen.standardMakeFillCell(fillGenConfig.firstLayer, fillGenConfig.lastLayer,
        		                     fillGenConfig.getTechType(), ep, fillGenConfig.perim,
                                     fillGenConfig.cellTiles, false);
        fillGen.makeGallery(getEditingPreferences());
        return true;
    }

}
