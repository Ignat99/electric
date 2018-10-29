/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: VerilogReader.java
 * Input/output tool: reader for Verilog output (.v)
 * Written by Gilda Garreton.
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
package com.sun.electric.tool.io.input.verilog;

import com.sun.electric.database.EditingPreferences;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.Library;
import com.sun.electric.database.id.CellId;
import com.sun.electric.technology.Technology;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.io.IOTool;
import com.sun.electric.tool.io.input.Input;
import com.sun.electric.tool.placement.Placement;
import com.sun.electric.tool.placement.PlacementFrame;
import com.sun.electric.tool.simulation.SimulationTool;
import com.sun.electric.tool.user.CompileVerilogStruct;
import com.sun.electric.tool.user.IconParameters;
import com.sun.electric.util.TextUtils;

import java.io.File;
import java.net.URL;
import java.util.BitSet;
import java.util.Map;

public class VerilogReader extends Input<Object>
{
	/**
	 * Creates a new instance of VerilogReader.
	 */
	public VerilogReader(EditingPreferences ep, VerilogPreferences ap) {
        super(ep);
    }

	public static class VerilogPreferences extends InputPreferences
    {
		public boolean runPlacement = SimulationTool.getFactoryVerilogRunPlacementTool();
		public boolean makeLayoutCells = IOTool.isFactoryVerilogMakeLayoutCells();
		public Placement.PlacementPreferences placementPrefs;
		public IconParameters iconParameters;
        public boolean acl2;

        public VerilogPreferences(boolean factory)
        {
            this(factory, false);
        }

        public VerilogPreferences(boolean factory, boolean acl2)
        {
            super(factory);
            this.acl2 = acl2;
            if (!factory)
            {
                runPlacement = SimulationTool.getVerilogRunPlacementTool();
                makeLayoutCells = IOTool.isVerilogMakeLayoutCells();
            }
            placementPrefs = new Placement.PlacementPreferences(factory);
            iconParameters = IconParameters.makeInstance(!factory);
        }

        @Override
        public Library doInput(URL fileURL, Library lib, Technology tech, EditingPreferences ep, Map<Library,Cell> currentCells,
        	Map<CellId,BitSet> nodesToExpand, Job job)
        {
        	File f = TextUtils.getFile(fileURL);
        	CompileVerilogStruct cvs = acl2
                ? new CompileVerilogStruct(f)
                : new CompileVerilogStruct(f, false, null);
        	Cell cell = cvs.genCell(lib, !makeLayoutCells, ep, iconParameters);

        	// running placement tool if selected
            if (lib != null && runPlacement)
            {
        		PlacementFrame pla = Placement.getCurrentPlacementAlgorithm(placementPrefs);
                Placement.placeCellNoJob(cell, ep, pla, placementPrefs, true, job);
            }
			return lib;
        }
    }

}
