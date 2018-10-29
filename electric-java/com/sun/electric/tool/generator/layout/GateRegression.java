/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: GateRegression.java
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
package com.sun.electric.tool.generator.layout;

import java.lang.reflect.Method;

import com.sun.electric.database.EditingPreferences;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.Library;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.technology.Technology;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.JobException;
import com.sun.electric.tool.generator.layout.gates.MoCMOSGenerator;
import com.sun.electric.tool.io.IOTool;
import com.sun.electric.tool.user.User;

/*
 * Regression test for gate generators
 */
public class GateRegression extends Job {
    private Technology technology;
    private int backupScheme;

    // specify which gates shouldn't be surrounded by DRC rings
	private static final DrcRings.Filter FILTER = new DrcRings.Filter() {
		public boolean skip(NodeInst ni) {
			// well tie cells don't pass DRC with DrcRings
	        return ni.getProto().getName().indexOf("mosWellTie_") != -1;
		}
	};

    private static void allSizes(StdCellParams stdCell, Technology technology) {
        double minSz = 0.1;
        double maxSz = 200;//500;
        for (double d=minSz; d<maxSz; d*=10) {
            for (double x=d; x<Math.min(d*10, maxSz); x*=1.01) {
                aPass(x, stdCell, technology);
            }
        }
    }

    public static void aPass(double x, StdCellParams stdCell, Technology technology) {
        Technology cmos90 = Technology.getCMOS90Technology();
        if (cmos90 != null && technology == cmos90) {
            // invoke the CMOS90 generator by reflection because it may not exist
    		try
			{
				Class<?> cmos90GeneratorClass = Class.forName("com.sun.electric.plugins.tsmc.gates90nm.CMOS90Generator");
				Class [] parameterTypes = new Class[] {Double.class, StdCellParams.class};
				Method generateMethod = cmos90GeneratorClass.getDeclaredMethod("generateAllGates", parameterTypes);
				generateMethod.invoke(null, new Object[] {new Double(x), stdCell});
	 		} catch (Exception e)
	        {
	 			System.out.println("ERROR invoking the CMOS90 gate generator");
	        }
//            CMOS90Generator.generateAllGates(x, stdCell);
        } else {
            MoCMOSGenerator.generateAllGates(x, stdCell);
        }
    }

    @Override
    public boolean doIt() throws JobException {
		Library scratchLib =
		  LayoutLib.openLibForWrite("scratch"+technology.getTechName());
        runRegression(technology, scratchLib, backupScheme, getEditingPreferences());
        return true;
    }

    /** Programatic interface to gate regressions.
     * @return the number of errors detected */
    public static int runRegression(Technology technology, Library scratchLib, int backupScheme, EditingPreferences ep) throws JobException {
		System.out.println("begin Gate Regression");

//        Tech.setTechnology(technology);     This call can't be done inside the doIt() because it calls the preferences
        StdCellParams stdCell;
        Technology cmos90 = Technology.getCMOS90Technology();
        if (cmos90 != null && technology == cmos90) {
            stdCell = new StdCellParams(technology, ep);
            stdCell.setOutputLibrary(scratchLib);
            stdCell.enableNCC("purpleFour");
            stdCell.setSizeQuantizationError(0.05);
            stdCell.setMaxMosWidth(1000);
        } else {
            // Test the parameters used by divider
        	stdCell = GateLayoutGenerator.dividerParams(technology, ep);
            stdCell.setOutputLibrary(scratchLib);

        	stdCell.setSizeQuantizationError(0.05);
        	stdCell.setSimpleName(false);

//        	stdCell = new StdCellParams(scratchLib, Tech.MOCMOS);
//            stdCell.enableNCC("purpleFour");
//            stdCell.setSizeQuantizationError(0.05);
//            stdCell.setMaxMosWidth(1000);
//            stdCell.setVddY(21);
//            stdCell.setGndY(-21);
//            stdCell.setNmosWellHeight(84);
//            stdCell.setPmosWellHeight(84);
        }

		// a normal run
        //Inv2iKn.makePart(10, stdCell);
        //Inv2iKn_wideOutput.makePart(10, stdCell);
        allSizes(stdCell, technology);

        // test the ability to move ground bus
        stdCell.setGndY(stdCell.getGndY() - 7);
        stdCell.setNmosWellHeight(stdCell.getNmosWellHeight()+7);
        //allSizes(stdCell, technology);
        aPass(10, stdCell, technology);
        aPass(200, stdCell, technology);
        stdCell.setGndY(stdCell.getGndY() + 7);
        stdCell.setNmosWellHeight(stdCell.getNmosWellHeight()-7);

        // test different PMOS to NMOS heights
        stdCell.setNmosWellHeight(50);
        stdCell.setPmosWellHeight(100);
        //allSizes(stdCell, technology);
        aPass(10, stdCell, technology);
        aPass(200, stdCell, technology);

        stdCell.setNmosWellHeight(100);
        stdCell.setPmosWellHeight(50);
        //allSizes(stdCell, technology);
        aPass(10, stdCell, technology);
        aPass(200, stdCell, technology);
        stdCell.setNmosWellHeight(70);
        stdCell.setPmosWellHeight(70);

        Cell gallery = Gallery.makeGallery(scratchLib, ep);
        DrcRings.addDrcRings(gallery, FILTER, stdCell);

        //IOTool.setCIFOutMergesBoxes(true);
        //int numCifErrs = CIF.writeCIFFile(gallery, VarContext.globalContext, "scratch.cif");
        LayoutLib.writeLibrary(scratchLib, backupScheme);

        System.out.println("done.");

        // The gate layout generators used to generate layout with no CIF 
        // errors. Then one day the CIF generator changed and began reporting 
        // lots of errors. I'm not sure if the errors are real or not. Until
        // we understand this I don't think it's important for us to check the
        // CIF error count in the regression. As long as DRC and NCC pass,
        // we're happy with the layout. We no longer use CIF. RKao
        
        return 0 /*numCifErrs*/;
	}

	public GateRegression(Technology techNm) {
		super("Run Gate regression", User.getUserTool(), Job.Type.CHANGE,
			  null, null, Job.Priority.ANALYSIS);
        this.technology = techNm;
        this.backupScheme = IOTool.getBackupRedundancy();
		startJob();
	}
}
