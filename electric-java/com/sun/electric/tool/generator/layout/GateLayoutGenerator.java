/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Gates.java
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

import com.sun.electric.database.EditingPreferences;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.HierarchyEnumerator;
import com.sun.electric.database.hierarchy.HierarchyEnumerator.CellInfo;
import com.sun.electric.database.hierarchy.Library;
import com.sun.electric.database.hierarchy.Nodable;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.variable.EditWindow_;
import com.sun.electric.database.variable.UserInterface;
import com.sun.electric.database.variable.VarContext;
import com.sun.electric.database.variable.Variable;
import com.sun.electric.technology.Technology;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.JobException;
import com.sun.electric.tool.generator.layout.gates.Inv350;
import com.sun.electric.tool.generator.layout.gates.MoCMOSGenerator;
import com.sun.electric.tool.logicaleffort.LEInst;
import com.sun.electric.tool.user.User;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/*
 * Regression test for gate generators
 */
public class GateLayoutGenerator {

    // specify which gates shouldn't be surrounded by DRC rings
	private static final DrcRings.Filter FILTER = new DrcRings.Filter() {
		@Override
		public boolean skip(NodeInst ni) {
			// well tie cells don't pass DRC with DrcRings
	        return ni.getProto().getName().indexOf("mosWellTie_") != -1;
		}
	};

//	private static void error(boolean pred, String msg) {
//		LayoutLib.error(pred, msg);
//	}

//	private Cell findCell(Library lib, String cellName) {
//		Cell c = lib.findNodeProto(cellName);
//		LayoutLib.error(c==null, "can't find: "+lib+":"+cellName);
//		return c;
//	}
    public static Cell generateCell(Library outputLib, StdCellParams stdCell,
                                    String type, double Xstrength) {
        if (outputLib == null) return null;
        TechType tech = stdCell.getTechType();
        stdCell.setOutputLibrary(outputLib);

        if (Xstrength<0) return null;

        int pwr = type.indexOf("_pwr");
        if (pwr!=-1) type = type.substring(0, pwr);

        Cell c = null;
        if (tech.getTechnology()==Technology.getCMOS90Technology())
        {
            try
            {
                Class<?> cmos90GeneratorClass = Class.forName("com.sun.electric.plugins.tsmc.gates90nm.CMOS90Generator");
                Class<?> [] parameterTypes = new Class[] {String.class, Double.class, StdCellParams.class};
                Method makeGateMethod = cmos90GeneratorClass.getDeclaredMethod("makeGate", parameterTypes);
                c = (Cell)makeGateMethod.invoke(null, new Object[] {type, new Double(Xstrength), stdCell});
             } catch (Exception e)
            {
                 System.out.println("ERROR invoking the CMOS90 gate generator");
            }
//			c = CMOS90Generator.makeGate(pNm, x, stdCell);
        } else if (tech.getTechnology().getTechName().equals("MIMOS_035"))
        {
            if (type.equals("inv")) {
                c = Inv350.makePart(Xstrength, "", stdCell);
            } else {
                System.out.println("Can't generate cell " + type + " for technology " + tech.name());
            }
        } else
        {
            c = MoCMOSGenerator.makeGate(type, Xstrength, stdCell);
        }
        return c;
    }


    /**
     * Generate layout cells from a hierarchical traversal of the
     * schematic cell
     * @param outLib the output library
     * @param cell the schematic cell
     * @param context the context of the schematic cell
     * @param stdCell the standard cell parameters
     * @param topLevelOnly true to generate gates in the top level only,
     * so it does not generate standard cells in sub cells.
     * @return a map of cells generated
     */
    public static Map<Nodable,Cell> generateLayoutFromSchematics(Library outLib,
                                   Cell cell, VarContext context,
                                   StdCellParams stdCell, boolean topLevelOnly) {
        stdCell.setOutputLibrary(outLib);
        GenerateLayoutForGatesInSchematic visitor =
			new GenerateLayoutForGatesInSchematic(stdCell, topLevelOnly);
		HierarchyEnumerator.enumerateCell(cell, context, visitor);
//		HierarchyEnumerator.enumerateCell(cell, context, null, visitor);

		Map<Nodable,Cell> result = visitor.getGeneratedCells();
		if (result.size() > 0)
		{
	        Cell gallery = Gallery.makeGallery(outLib, stdCell.getEditingPreferences());
	        DrcRings.addDrcRings(gallery, FILTER, stdCell);
		}

        return result;
	}

    public static void generateFromSchematicsJob(Technology technology) {
        GenerateFromSchematicsJob job = new GenerateFromSchematicsJob(technology);
        job.startJob();
    }

    public static StdCellParams locoParams(EditingPreferences ep) {
		StdCellParams stdCell = new StdCellParams(Technology.getMocmosTechnology(), ep);
		stdCell.enableNCC("purpleFour");
		stdCell.setSizeQuantizationError(0);
		stdCell.setMaxMosWidth(1000);
		stdCell.setVddY(21);
		stdCell.setGndY(-21);
		stdCell.setNmosWellHeight(42);
		stdCell.setPmosWellHeight(42);
		stdCell.setSimpleName(true);
		return stdCell;
	}

    public static StdCellParams sportParams(EditingPreferences ep) {
        return sportParams(ep, true);
    }

    public static StdCellParams sportParams(EditingPreferences ep, boolean enableNCC) {
        StdCellParams stdCell = new StdCellParams(Technology.getCMOS90Technology(), ep);
        if (enableNCC) stdCell.enableNCC("purpleFour");
        stdCell.setSizeQuantizationError(0);
        stdCell.setMaxMosWidth(1000);
        stdCell.setVddY(24.5);
        stdCell.setGndY(-24.5);
        stdCell.setNmosWellHeight(84);
        stdCell.setPmosWellHeight(84);
        stdCell.setSimpleName(true);
        return stdCell;
    }

    public static StdCellParams dividerParams(Technology technology, EditingPreferences ep) {
        return dividerParams(technology, ep, true);
    }

	public static StdCellParams dividerParams(Technology technology, EditingPreferences ep, boolean enableNCC) {
		StdCellParams stdCell = new StdCellParams(technology, ep);
        if (enableNCC) stdCell.enableNCC("purpleFour");
		stdCell.setSizeQuantizationError(0);
		stdCell.setMaxMosWidth(1000);
		stdCell.setVddY(21);
		stdCell.setGndY(-21);
		stdCell.setNmosWellHeight(84);
		stdCell.setPmosWellHeight(84);
		stdCell.setSimpleName(true);
		return stdCell;
	}

    public static StdCellParams fastProxParams(Technology technology, EditingPreferences ep) {
        StdCellParams stdCell = new StdCellParams(technology, ep);
        stdCell.enableNCC("purpleFour");
        stdCell.setSizeQuantizationError(0);
        stdCell.setMaxMosWidth(1000);
        stdCell.setVddY(24);
        stdCell.setGndY(-24);
        stdCell.setVddWidth(9);
        stdCell.setGndWidth(9);
        stdCell.setNmosWellHeight(60);
        stdCell.setPmosWellHeight(60);
        stdCell.setSimpleName(true);
        return stdCell;
    }

	public static StdCellParams justinParams(Technology technology, EditingPreferences ep) {
		StdCellParams stdCell = new StdCellParams(technology, ep);
		stdCell.enableNCC("purpleFour");
		stdCell.setSizeQuantizationError(0);
		stdCell.setMaxMosWidth(1000);
		stdCell.setVddY(21);
		stdCell.setGndY(-21);
		stdCell.setNmosWellHeight(42);
		stdCell.setPmosWellHeight(42);
		stdCell.setSimpleName(true);
		return stdCell;
	}



    public static class GenerateFromSchematicsJob extends Job {
    	static final long serialVersionUID = 0; 

        private Technology technology;
        private Cell cell;
        private VarContext context;

        public GenerateFromSchematicsJob(Technology technology) {
            super("Generate gate layouts", User.getUserTool(), Job.Type.CHANGE,
                  null, null, Job.Priority.ANALYSIS);
            this.technology = technology;

            UserInterface ui = Job.getUserInterface();
            EditWindow_ wnd = ui.needCurrentEditWindow_();
            if (wnd == null) return;

            cell = wnd.getCell();
            context = wnd.getVarContext();
        }

        @Override
        public boolean doIt() throws JobException {
            EditingPreferences ep = getEditingPreferences();
            String outLibNm = "autoGenLib"+technology;
            Library outLib = LayoutLib.openLibForWrite(outLibNm);

            StdCellParams stdCell;
            if (technology == Technology.getCMOS90Technology()) {
                stdCell = sportParams(ep);
            } else if (technology.getTechName().equals("MIMOS_035")) {
                stdCell = StdCellParams350.invParams(technology, ep);
            } else {
                //stdCell = locoParams(outLib);
                stdCell = dividerParams(technology, ep);
                //stdCell = justinParams(outLib, technology);
            }

            if (cell==null) {
                System.out.println("Please open the schematic for which you " +
                                   "want to generate gate layouts.");
                return false;
            }
            if (!cell.isSchematic()) {
                System.out.println("The current cell isn't a schematic. This " +
                                   "command only works on schematics.");
                return false;
            }
            System.out.println("Generating layouts for gates in the schematic: "+
                               cell.getName()+" and its descendents");
            System.out.println("Output goes to library: " + outLibNm);
            //Library outLib = cell.getLibrary();

            GateLayoutGenerator.generateLayoutFromSchematics(outLib, cell, context, stdCell, false);

            System.out.println("done.");
            return true;
        }
    }
}

/** Traverse a schematic hierarchy and generate Cells that we recognize. */
class GenerateLayoutForGatesInSchematic extends HierarchyEnumerator.Visitor {
	private final StdCellParams stdCell;
	private final boolean DEBUG = false;
    private final boolean topLevelOnly;
    private final Variable.Key ATTR_X;
    private Map<Nodable,Cell> generatedCells;
    private void trace(String s) {
		if (DEBUG) System.out.println(s);
	}
	private void traceln(String s) {
		trace(s);
		trace("\n");
	}
	
	/**
	 * Construct a Visitor that will walk a schematic and generate layout for Cells.
	 * param libraryName the name of the library that contains the Cells for 
	 * which we want to generate layout. For example: "redFour" or "purpleFour".
	 * param cellNames the names of the Cells for which we want to generate layout
	 */
	public GenerateLayoutForGatesInSchematic(StdCellParams stdCell, boolean topLevelOnly) {
		this.stdCell = stdCell;
        this.topLevelOnly = topLevelOnly;
        this.generatedCells = new HashMap<Nodable,Cell>();
        this.ATTR_X = stdCell.getTechType().getAttrX();
    }

	/** @return value of strength attribute "ATTR_X" or -1 if no such
	 * attribute or -2 if attribute exists but has no value. */
	private double getStrength(Nodable no, VarContext context) {
		Variable var = no.getParameterOrVariable(ATTR_X);
		if (var==null) return -1;
		Object val = context.evalVar(var, no);
		if (val==null) return -2;
		//LayoutLib.error(val==null, "strength is null?");
		Job.error(!(val instanceof Number),
				        "strength not number?");
		return ((Number)val).doubleValue();
	}

	private Cell generateCell(Nodable iconInst, CellInfo info) {
        EditingPreferences ep = stdCell.getEditingPreferences();
		VarContext context = info.getContext();
		String pNm = iconInst.getProto().getName();
		double x = getStrength(iconInst, context);
		if (x==-2) {
			System.out.println("no value for strength attribute for Cell: "+
					           pNm+" instance: "+
							   info.getUniqueNodableName(iconInst, "/"));
		}
//		System.out.println("Try : "+pNm+" X="+x+" for instance: "+
//                           info.getUniqueNodableName(iconInst, "/"));
		
		Cell c = GateLayoutGenerator.generateCell(stdCell.getOutputLibrary(), stdCell, pNm, x);
		if (c!=null) {
            // record defining schematic cell if it is sizable
            if (LEInst.getType(iconInst, context) == LEInst.Type.LEGATE) {
                c.newVar(LEInst.ATTR_LEGATE, c.libDescribe(), ep);
            }
            if (LEInst.getType(iconInst, context) == LEInst.Type.LEKEEPER) {
                c.newVar(LEInst.ATTR_LEKEEPER, c.libDescribe(), ep);
            }

			System.out.println("Use: "+pNm+" X="+x+" for instance: "+
			                   info.getUniqueNodableName(iconInst, "/"));
		}
        return c;
    }

	@Override
	public boolean enterCell(CellInfo info) {
		VarContext ctxt = info.getContext();
		traceln("Entering Cell instance: "+ctxt.getInstPath("/"));
		return true; 
	}
	@Override
	public void exitCell(CellInfo info) {
		VarContext ctxt = info.getContext();
		traceln("Leaving Cell instance: "+ctxt.getInstPath("/"));
	}
	@Override
	public boolean visitNodeInst(Nodable no, CellInfo info) {
		// we never generate layout for PrimitiveNodes
		if (no instanceof NodeInst) return false;
		
		trace("considering instance: "+
			  info.getUniqueNodableName(no, "/")+" ... ");
		
		Cell cell = (Cell) no.getProto();
		Library lib = cell.getLibrary();
		String libNm = lib.getName();
		if (libNm.equals("redFour") || libNm.equals("purpleFour") ||
			libNm.equals("power2_gates")) {
			traceln("generate");
			Cell c = generateCell(no, info);
            if (c != null) generatedCells.put(no, c);
            return false;
		}
		traceln("descend");
        if (topLevelOnly) return false;
        return true;
	}

    public Map<Nodable,Cell> getGeneratedCells() { return generatedCells; }
}
