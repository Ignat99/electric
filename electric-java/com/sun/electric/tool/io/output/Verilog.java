/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Verilog.java
 * Input/output tool: Verilog Netlist output
 * Written by Steven M. Rubin.
 * VerilogA by Min Hao Zhu, Villanova University.
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
package com.sun.electric.tool.io.output;

import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.EDatabase;
import com.sun.electric.database.hierarchy.Export;
import com.sun.electric.database.hierarchy.HierarchyEnumerator;
import com.sun.electric.database.hierarchy.Library;
import com.sun.electric.database.hierarchy.Nodable;
import com.sun.electric.database.hierarchy.View;
import com.sun.electric.database.network.Global;
import com.sun.electric.database.network.Netlist;
import com.sun.electric.database.network.Network;
import com.sun.electric.database.prototype.NodeProto;
import com.sun.electric.database.prototype.PortCharacteristic;
import com.sun.electric.database.prototype.PortProto;
import com.sun.electric.database.text.Version;
import com.sun.electric.database.topology.ArcInst;
import com.sun.electric.database.topology.Connection;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.topology.PortInst;
import com.sun.electric.database.variable.CodeExpression;
import com.sun.electric.database.variable.VarContext;
import com.sun.electric.database.variable.Variable;
import com.sun.electric.technology.PrimitiveNode;
import com.sun.electric.technology.technologies.Generic;
import com.sun.electric.technology.technologies.Schematics;
import com.sun.electric.tool.generator.sclibrary.SCLibraryGen;
import com.sun.electric.tool.io.input.verilog.VerilogReader;
import com.sun.electric.tool.simulation.SimulationTool;
import com.sun.electric.tool.user.CompileVerilogStruct;
import com.sun.electric.tool.user.dialogs.BusParameters;
import com.sun.electric.util.TextUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This is the Simulation Interface tool.
 */
public class Verilog extends Topology
{
	/** maximum size of output line */						private static final int MAXDECLARATIONWIDTH = 80;
	/** name of inverters generated from negated wires */	private static final String IMPLICITINVERTERNODENAME = "Imp";
	/** name of signals generated from negated wires */		private static final String IMPLICITINVERTERSIGNAME = "ImpInv";

	/** key of Variable holding verilog code. */			public static final Variable.Key VERILOG_CODE_KEY = Variable.newKey("VERILOG_code");
	/** key of Variable holding verilog declarations. */	public static final Variable.Key VERILOG_DECLARATION_KEY = Variable.newKey("VERILOG_declaration");
    /** key of Variable holding verilog parameters. */		public static final Variable.Key VERILOG_PARAMETER_KEY = Variable.newKey("VERILOG_parameter");
    /** key of Variable holding verilog code that is
     ** external to the module. */							public static final Variable.Key VERILOG_EXTERNAL_CODE_KEY = Variable.newKey("VERILOG_external_code");
	/** key of Variable holding verilog wire time. */		public static final Variable.Key WIRE_TYPE_KEY = Variable.newKey("SIM_verilog_wire_type");
	/** key of Variable holding verilog templates. */		public static final Variable.Key VERILOG_TEMPLATE_KEY = Variable.newKey("ATTR_verilog_template");
    /** key of Variable holding verilog defparams. */		public static final Variable.Key VERILOG_DEFPARAM_KEY = Variable.newKey("ATTR_verilog_defparam");
	/** key of Variable holding file name with Verilog. */	public static final Variable.Key VERILOG_BEHAVE_FILE_KEY = Variable.newKey("SIM_verilog_behave_file");
	/** those cells that have overridden models */			private Set<Cell> modelOverrides = new HashSet<Cell>();
	/** those cells that have modules defined */			private Map<String,String> definedModules = new HashMap<String,String>(); // key: module name, value: source description
	/** those cells that have primitives defined */			private Map<Cell,CompileVerilogStruct.VModule> definedPrimitives = new HashMap<Cell,CompileVerilogStruct.VModule>(); // key: module name, value: VerilogModule
	/** those cells which are empty */						private Map<Cell,Cell> emptyModules = new HashMap<Cell,Cell>();
	/** map of cells that are or contain standard cells */  private SCLibraryGen.StandardCellHierarchy standardCells = new SCLibraryGen.StandardCellHierarchy();
    /** file we are writing to */                           private String filePath;
	/** A set of keywords that are reserved in Verilog */	private Set<String> reservedWords;
	/** A set of Cells whose "External Code" is written */	private Set<Cell> externalCodeWritten;
	/** A set of signals declared by the user */			private Set<String> previouslyDeclared;
	private VerilogPreferences localPrefs;

	public static class VerilogPreferences extends OutputPreferences
    {
        // Verilog Settings
		public boolean useTrireg = SimulationTool.getVerilogUseTrireg();
		public boolean useAssign = SimulationTool.getVerilogUseAssign();
		public boolean useVerilogA = false;
		public boolean useBehavioralConstructs = false;
		public boolean ignoreVerilogViews = false;
        // Verilog factory Preferences
		public boolean stopAtStandardCells = SimulationTool.getFactoryVerilogStopAtStandardCells();
        public boolean netlistNonstandardCells = SimulationTool.getFactoryVerilogNetlistNonstandardCells();
        public boolean parameterizeModuleNames = SimulationTool.getFactoryVerilogParameterizeModuleNames();
		public boolean writeModuleForEachIcon = SimulationTool.isFactoryVerilogWriteModuleForEachIcon();
		public boolean noEmptyModules = SimulationTool.isFactoryVerilogNoWriteEmptyModules();
        public Map<Cell,String> modelFiles = Collections.emptyMap();

        // Verilog output preferences need VerilogInputPreferences in case of doc cells with Verilog code
        public VerilogReader.VerilogPreferences inputPrefs;

        public VerilogPreferences(boolean useVerilogA) { this(false, useVerilogA); }
        public VerilogPreferences(boolean factory, boolean useVerilogA) {
            super(factory);
            inputPrefs = new VerilogReader.VerilogPreferences(factory);
            this.useVerilogA = useVerilogA;
            if (!factory)
                fillPrefs();
        }

		private void fillPrefs()
        {
            // Verilog current Preferences
			stopAtStandardCells = SimulationTool.getVerilogStopAtStandardCells();
            netlistNonstandardCells = SimulationTool.getVerilogNetlistNonstandardCells();
            parameterizeModuleNames = SimulationTool.getVerilogParameterizeModuleNames();
			writeModuleForEachIcon = SimulationTool.isVerilogWriteModuleForEachIcon();
			noEmptyModules = SimulationTool.isVerilogNoWriteEmptyModules();
            modelFiles = CellModelPrefs.verilogModelPrefs.getUnfilteredFileNames(EDatabase.clientDatabase());
		}

        @Override
        public Output doOutput(Cell cell, VarContext context, String filePath)
        {
    		Verilog out = new Verilog(this);
            if (out.openTextOutputStream(filePath)) return out.finishWrite();
            out.filePath = filePath;
    		if (out.writeCell(cell, context)) return out.finishWrite();
    		if (out.closeTextOutputStream()) return out.finishWrite();
    		System.out.println(filePath + " written");
            return out.finishWrite();
        }
    }

	/**
	 * Creates a new instance of Verilog
	 */
	Verilog(VerilogPreferences vp)
	{
		localPrefs = vp;

		reservedWords = new HashSet<String>();
		reservedWords.add("always");
		reservedWords.add("and");
		reservedWords.add("assign");
		reservedWords.add("attribute");
		reservedWords.add("begin");
		reservedWords.add("buf");
		reservedWords.add("bufif0");
		reservedWords.add("bufif1");
		reservedWords.add("case");
		reservedWords.add("casex");
		reservedWords.add("casez");
		reservedWords.add("cmos");
		reservedWords.add("deassign");
		reservedWords.add("default");
		reservedWords.add("defpram");
		reservedWords.add("disable");
		reservedWords.add("edge");
		if (localPrefs.useVerilogA) reservedWords.add("electrical");
		reservedWords.add("else");
		reservedWords.add("end");
		reservedWords.add("endattribute");
		reservedWords.add("endcase");
		reservedWords.add("endfunction");
		reservedWords.add("endmodule");
		reservedWords.add("endprimitive");
		reservedWords.add("endspecify");
		reservedWords.add("endtable");
		reservedWords.add("endtask");
		reservedWords.add("event");
		reservedWords.add("for");
		reservedWords.add("force");
		reservedWords.add("forever");
		reservedWords.add("fork");
		reservedWords.add("function");
		reservedWords.add("highz0");
		reservedWords.add("highz1");
		reservedWords.add("if");
		reservedWords.add("initial");
		reservedWords.add("inout");
		reservedWords.add("input");
		reservedWords.add("integer");
		reservedWords.add("join");
		reservedWords.add("large");
		reservedWords.add("macromodule");
		reservedWords.add("meduim");
		reservedWords.add("module");
		reservedWords.add("nand");
		reservedWords.add("negedge");
		reservedWords.add("nmos");
		reservedWords.add("nor");
		reservedWords.add("not");
		reservedWords.add("notif0");
		reservedWords.add("notif1");
		reservedWords.add("or");
		reservedWords.add("output");
		reservedWords.add("parameter");
		reservedWords.add("pmos");
		reservedWords.add("posedge");
		reservedWords.add("primitive");
		reservedWords.add("pull0");
		reservedWords.add("pull1");
		reservedWords.add("pulldown");
		reservedWords.add("pullup");
		reservedWords.add("rcmos");
		reservedWords.add("real");
		reservedWords.add("realtime");
		reservedWords.add("reg");
		reservedWords.add("release");
		reservedWords.add("repeat");
		reservedWords.add("rtranif1");
		reservedWords.add("scalared");
		reservedWords.add("signed");
		reservedWords.add("small");
		reservedWords.add("specify");
		reservedWords.add("specpram");
		reservedWords.add("strength");
		reservedWords.add("strong0");
		reservedWords.add("strong1");
		reservedWords.add("supply0");
		reservedWords.add("supply1");
		reservedWords.add("table");
		reservedWords.add("task");
		reservedWords.add("time");
		reservedWords.add("tran");
		reservedWords.add("tranif0");
		reservedWords.add("tranif1");
		reservedWords.add("tri");
		reservedWords.add("tri0");
		reservedWords.add("tri1");
		reservedWords.add("triand");
		reservedWords.add("trior");
		reservedWords.add("trireg");
		reservedWords.add("unsigned");
		reservedWords.add("vectored");
		reservedWords.add("wait");
		reservedWords.add("wand");
		reservedWords.add("weak0");
		reservedWords.add("weak1");
		reservedWords.add("while");
		reservedWords.add("wire");
		reservedWords.add("wor");
		reservedWords.add("xnor");
		reservedWords.add("xor");
		if (localPrefs.useVerilogA) reservedWords.add("PCNFET");
		if (localPrefs.useVerilogA) reservedWords.add("NCNFET");
	}

	protected void start()
	{
		// parameters to the output-line-length limit and how to break long lines
		setOutputWidth(MAXDECLARATIONWIDTH, false);
		setContinuationString("      ");

		// write header information
		String typeOfVerilog = localPrefs.useVerilogA ? "VerilogA" : "Verilog";
		printWriter.println("/* " + typeOfVerilog + " for " + topCell + " from " + topCell.getLibrary() + " */");
		emitCopyright("/* ", " */");
		if (localPrefs.includeDateAndVersionInOutput)
		{
			printWriter.println("/* Created on " + TextUtils.formatDate(topCell.getCreationDate()) + " */");
			printWriter.println("/* Last revised on " + TextUtils.formatDate(topCell.getRevisionDate()) + " */");
			printWriter.println("/* Written on " + TextUtils.formatDate(new Date()) +
				" by Electric VLSI Design System, version " + Version.getVersion() + " */");
		} else
		{
			printWriter.println("/* Written by Electric VLSI Design System */");
		}

		// add in any user-specified defines
		externalCodeWritten = new HashSet<Cell>();
		includeTypedCode(topCell, VERILOG_EXTERNAL_CODE_KEY, "external code", null);

		if (localPrefs.stopAtStandardCells) {
			// enumerate to find which cells contain standard cells
			HierarchyEnumerator.enumerateCell(topCell, VarContext.globalContext, standardCells);
            if (!localPrefs.netlistNonstandardCells) {
                for (Cell acell : standardCells.getDoesNotContainStandardCellsInHier()) {
                    reportWarning("Warning: Not netlisting cell "+acell.describe(false)+" because it does not contain any standard cells.");
                }
            }
            if (standardCells.getNameConflict()) {
				System.out.println("Name conflicts found, please see above messages");
			}
		}
	}

	protected void done()
	{
    	// Remove this call that auto-overrides the new user visible preference
		// SimulationTool.setVerilogStopAtStandardCells(false);
	}

	protected boolean skipCellAndSubcells(Cell cell) {

        // do not netlist contents of standard cells
        // also, if writing a standard cell netlist, ignore all verilog views, verilog templates, etc.
        if (localPrefs.stopAtStandardCells) {
            if (SCLibraryGen.isStandardCell(cell))
                return true;

            if (!localPrefs.netlistNonstandardCells) {
                if (standardCells.containsStandardCell(cell))
                    return false;
                else
                    return true;
            }
        }

		// do not write modules for cells with verilog_templates defined
		// also skip their subcells
		if (cell.getVar(VERILOG_TEMPLATE_KEY) != null) {
			return true;
		}

		// also skip subcells if a behavioral file specified.
		// If one specified, write it out here and skip both cell and subcells
        String unfilteredFileName = localPrefs.modelFiles.get(cell);
		if (CellModelPrefs.isUseModelFromFile(unfilteredFileName)) {
			String fileName = CellModelPrefs.getModelFile(unfilteredFileName);
            if (filePath.equals(fileName)) {
                reportError("Error: Use Model From File file path for cell "+cell.describe(false)+" is the same as the file being written, skipping.");
                return false;
            }

            // check that data from file is consistent
			File f = new File(fileName);
			CompileVerilogStruct cvs = new CompileVerilogStruct(f, false, errorLogger);
			if (cvs.hadErrors())
			{
				reportError("Warning: errors found while examining Verilog in file: " + fileName);
			} else
			{
				if (!checkIncludedData(cvs, cell, fileName))
					reportError("Warning: inconsistencies found while examining Verilog in file: " + fileName);
			}

			if (!modelOverrides.contains(cell))
			{
				printWriter.println("`include \"" + fileName + "\"");
				modelOverrides.add(cell);
			}
			return true;
		}

		// use library behavior if it is available
		Cell verViewCell = cell.otherView(View.VERILOG);
		if (verViewCell != null && !localPrefs.ignoreVerilogViews && CellModelPrefs.verilogModelPrefs.isUseVerilogView(cell))
		{
			String [] stringArray = verViewCell.getTextViewContents();
			if (stringArray != null)
			{
				if (stringArray.length > 0) {
					String line = stringArray[0].toLowerCase();
					if (line.startsWith("do not use")) {
						return false;
					}
				}

				CompileVerilogStruct cvs = new CompileVerilogStruct(stringArray, false);
				if (cvs.hadErrors())
				{
					System.out.println("Warning: errors found while examining Verilog View of cell "+cell.describe(false));
				} else
				{
					if (!checkIncludedData(cvs, cell, null))
						System.out.println("Warning: inconsistencies found while examining Verilog View of cell "+cell.describe(false));
				}

				// write to output file
				System.out.println("Info: Netlisting Verilog view of "+cell.describe(false));
				printWriter.println();
				printWriter.println("/* Verilog view of " + verViewCell.libDescribe() + " */");
				for(int i=0; i<stringArray.length; i++)
					printWriter.println(stringArray[i]);
			}
			return true;
		}

		return false;
	}

	/**
	 * Since the Verilog netlister should write a separate copy of schematic cells for each icon,
	 * this override returns true.
	 */
	protected boolean isWriteCopyForEachIcon() { return localPrefs.writeModuleForEachIcon; }

	/**
	 * Method to write cellGeom
	 */
	protected void writeCellTopology(Cell cell, String cellName, CellNetInfo cni, VarContext context, Topology.MyCellInfo info)
	{
		if (cell == topCell) {
			// gather all global signal names
			Netlist netList = cni.getNetList();
			Global.Set globals = netList.getGlobals();
			int globalSize = globals.size();

			// see if any globals besides power and ground to write
			List<Global> globalsToWrite = new ArrayList<Global>();
			List<PortCharacteristic> globalsPortCharacteristics = new ArrayList<PortCharacteristic>();
			for (int i=0; i<globalSize; i++)
			{
				Global global = globals.get(i);
				if (global == Global.power || global == Global.ground) continue;
				globalsToWrite.add(global);
				PortCharacteristic pc = globals.getCharacteristic(global);
				globalsPortCharacteristics.add(pc);
			}

			if (globalsToWrite.size() > 0)
			{
				printWriter.println("\nmodule glbl();");
				for(int i=0; i<globalsToWrite.size(); i++)
				{
					Global global = globalsToWrite.get(i);
					PortCharacteristic pc = globalsPortCharacteristics.get(i);
					String sigType;
					if (localPrefs.useTrireg) sigType = "trireg"; else sigType = "wire";
					if (localPrefs.useVerilogA) sigType = "electrical";
					if (pc == PortCharacteristic.PWR) sigType = "supply1"; else
						if (pc == PortCharacteristic.GND) sigType = "supply0";
					printWriter.println("    " + sigType + " " + global.getName() + ";");
				}
				printWriter.println("endmodule");
			}
		}

		// prepare arcs to store implicit inverters
		Map<ArcInst,Integer> implicitHeadInverters = new HashMap<ArcInst,Integer>();
		Map<ArcInst,Integer> implicitTailInverters = new HashMap<ArcInst,Integer>();
		int impInvCount = 0;
		for(Iterator<ArcInst> it = cell.getArcs(); it.hasNext(); )
		{
			ArcInst ai = it.next();
			for(int e=0; e<2; e++)
			{
				if (!ai.isNegated(e)) continue;
				PortInst pi = ai.getPortInst(e);
				NodeInst ni = pi.getNodeInst();
				if (ni.getProto() == Schematics.tech().bufferNode || ni.getProto() == Schematics.tech().andNode ||
					ni.getProto() == Schematics.tech().orNode || ni.getProto() == Schematics.tech().xorNode)
				{
					if (localPrefs.useAssign) continue;
					if (pi.getPortProto().getName().equals("y")) continue;
				}

				// must create implicit inverter here
				if (e == ArcInst.HEADEND) implicitHeadInverters.put(ai, new Integer(impInvCount)); else
					implicitTailInverters.put(ai, new Integer(impInvCount));
				if (ai.getProto() != Schematics.tech().bus_arc) impInvCount++; else
				{
					int wid = cni.getNetList().getBusWidth(ai);
					impInvCount += wid;
				}
			}
		}

		// gather networks in the cell
		Netlist netList = cni.getNetList();

		// Collecting information for module in case it is empty and
		// should not be written
		StringBuffer moduleStr = new StringBuffer();
        // add in any user-specified defines
        includeTypedCode(cell, VERILOG_EXTERNAL_CODE_KEY, "external code", moduleStr);

		// write the module header
		writeLine("", moduleStr); // printWriter.println();
		StringBuffer sb = new StringBuffer();
		sb.append("module " + cellName + "(");
		boolean first = true;
		int flagvdd = 0, flaggnd = 0;
		for(Iterator<CellAggregateSignal> it = cni.getCellAggregateSignals(); it.hasNext(); )
		{
			CellAggregateSignal cas = it.next();
			if (cas.getExport() == null) continue;
			if (cas.getName() == "vdd") flagvdd++;
			if (cas.getName() == "gnd") flaggnd++;
			if (cas.getLowIndex() <= cas.getHighIndex() && cas.getIndices() != null)
			{
				// fragmented bus: write individual signals
				// check http://www.asic-world.com/verilog/syntax1.html in case of questions
				int [] indices = cas.getIndices();
				for(int i=0; i<indices.length; i++)
				{
					int ind = i;
					if (cas.isDescending()) ind = indices.length - i - 1;
					if (!first) sb.append(",");
					sb.append(" \\" + cas.getName() + "[" + indices[ind] + "] ");
					first = false;
				}
			} else
			{
				// simple name, add to module header
				if (!first) sb.append(", ");
				sb.append(cas.getName());
				first = false;
			}
		}
		if (localPrefs.useVerilogA)
		{
			if (flagvdd == 0) sb.append(", vdd");
			if (flaggnd == 0) sb.append(", gnd");
		}
		sb.append(");\n");
		writeWidthLimited(sb.toString(), moduleStr);
		definedModules.put(cellName, "Cell "+cell.libDescribe());

		// add in known parameters if not mangling and if writing behavioral verilog
		Set<String> knownParameterNames = new HashSet<String>();
		Set<String> knownParameterNamesLC = new HashSet<String>();
		if (!localPrefs.parameterizeModuleNames && localPrefs.useBehavioralConstructs)
		{
			for(Iterator<Variable> it = cell.getParameters(); it.hasNext(); )
			{
				Variable var = it.next();
				String eval = var.describe(context, cell);
				int equalPos = eval.indexOf('=');
				String pureNum = (equalPos >= 0) ? eval.substring(equalPos+1) : eval;
				try
				{
		            Number n = TextUtils.parsePostFixNumber(pureNum, null);
		            double v = n.doubleValue();
			        if (v != 0)
			            eval = eval.substring(0, equalPos) + "=" + v;
				} catch (NumberFormatException e) {}
				if (eval.trim().length() > 0)
				{
		            writeWidthLimited("  parameter " + eval + ";\n", moduleStr);
					knownParameterNames.add(var.getTrueName());
					knownParameterNamesLC.add(var.getTrueName().toLowerCase());
				}
			}
		}

		// add in any user-specified parameters
        includeTypedCode(cell, VERILOG_PARAMETER_KEY, "parameters", moduleStr);

		// look for "wire/trireg" overrides
		for(Iterator<ArcInst> it = cell.getArcs(); it.hasNext(); )
		{
			ArcInst ai = it.next();
			Variable var = ai.getVar(WIRE_TYPE_KEY);
			if (var == null) continue;
			String wireType = var.getObject().toString();
			int overrideValue = 0;
			if (wireType.equalsIgnoreCase("wire")) overrideValue = 1; else
				if (wireType.equalsIgnoreCase("trireg")) overrideValue = 2;
			int busWidth = netList.getBusWidth(ai);
			for(int i=0; i<busWidth; i++)
			{
				Network net = netList.getNetwork(ai, i);
				CellSignal cs = cni.getCellSignal(net);
				if (cs == null) continue;
				cs.getAggregateSignal().setFlags(overrideValue);
			}
		}

		// write description of formal parameters to module (pass 1)
		if (localPrefs.useVerilogA)
		{
			first = true;
			int flagvdd1 = 0, flaggnd1 = 0;
			for(Iterator<CellAggregateSignal> it = cni.getCellAggregateSignals(); it.hasNext(); )
			{
				CellAggregateSignal cas = it.next();
				Export pp = cas.getExport();
				if (pp == null) continue;
			
				String portType = "input";
				if (pp.getCharacteristic() == PortCharacteristic.PWR) flagvdd1++;
				if (pp.getCharacteristic() == PortCharacteristic.GND) flaggnd1++;
				if (pp.getCharacteristic() == PortCharacteristic.UNKNOWN) portType = "unknown";
				if (pp.getCharacteristic() == PortCharacteristic.REFIN) portType = "refin";
				if (pp.getCharacteristic() == PortCharacteristic.REFBASE) portType = "refbase";
				if (pp.getCharacteristic() == PortCharacteristic.OUT) continue;
				if (pp.getCharacteristic() == PortCharacteristic.BIDIR) continue;
				if (pp.getCharacteristic() == PortCharacteristic.REFOUT) continue;
				sb = new StringBuffer();
				sb.append("  " + portType);

				if (cas.getLowIndex() > cas.getHighIndex())
				{
					if (pp.getCharacteristic() == PortCharacteristic.PWR ) sb.append(" vdd;");
					else if (pp.getCharacteristic() == PortCharacteristic.GND) sb.append(" gnd;");
					else sb.append(" " + cas.getName() + ";");
				} else
				{
					int [] indices = cas.getIndices();
					if (indices != null)
					{
						for(int i=0; i<indices.length; i++)
						{
							int ind = i;
							if (cas.isDescending()) ind = indices.length - i - 1;
							if (i != 0) sb.append(",");
							sb.append(" \\" + cas.getName() + "[" + indices[ind] + "] ");
						}
						sb.append(";");
					} else
					{
						int low = cas.getLowIndex(), high = cas.getHighIndex();
						if (cas.isDescending())
						{
							low = cas.getHighIndex();   high = cas.getLowIndex();
						}
						sb.append(" [" + low + ":" + high + "] " + cas.getName() + ";");
					}
				}
				if (cas.getFlags() != 0)
					sb.append("  electrical" + " " + cas.getName() + ";");
				sb.append("\n");
				writeWidthLimited(sb.toString(), moduleStr);
				first = false;
			}
			if (flagvdd1 == 0) printWriter.println("  input vdd;");
			if (flaggnd1 == 0) printWriter.println("  input gnd;");
		}

		// write description of formal parameters to module (pass 2)
		first = true;
		for(Iterator<CellAggregateSignal> it = cni.getCellAggregateSignals(); it.hasNext(); )
		{
			CellAggregateSignal cas = it.next();
			Export pp = cas.getExport();
			if (pp == null) continue;

			String portType;
			if (localPrefs.useVerilogA)
			{
				if (pp.getCharacteristic() == PortCharacteristic.IN) continue;
				if (pp.getCharacteristic() == PortCharacteristic.PWR) continue;
				if (pp.getCharacteristic() == PortCharacteristic.GND) continue;
				if (pp.getCharacteristic() == PortCharacteristic.CLK) continue;
				if (pp.getCharacteristic() == PortCharacteristic.C1) continue;
				if (pp.getCharacteristic() == PortCharacteristic.C2) continue;
				if (pp.getCharacteristic() == PortCharacteristic.C3) continue;
				if (pp.getCharacteristic() == PortCharacteristic.C4) continue;
				if (pp.getCharacteristic() == PortCharacteristic.C5) continue;
				if (pp.getCharacteristic() == PortCharacteristic.C6) continue;
				if (pp.getCharacteristic() == PortCharacteristic.UNKNOWN) continue;
				if (pp.getCharacteristic() == PortCharacteristic.REFIN) continue;
				if (pp.getCharacteristic() == PortCharacteristic.REFBASE) continue;
				portType = "output";
				if (pp.getCharacteristic() == PortCharacteristic.OUT) portType = "output";
				if (pp.getCharacteristic() == PortCharacteristic.BIDIR) portType = "inout";
			    if (pp.getCharacteristic() == PortCharacteristic.REFOUT) portType = "refout";
			} else
			{
				portType = "input";
				if (pp.getCharacteristic() == PortCharacteristic.OUT) portType = "output";
				if (pp.getCharacteristic() == PortCharacteristic.BIDIR) portType = "inout";
			}
			sb = new StringBuffer();
			sb.append("  " + portType);

			if (cas.getLowIndex() > cas.getHighIndex())
			{
				sb.append(" " + cas.getName() + ";");
			} else
			{
				int [] indices = cas.getIndices();
				if (indices != null)
				{
					for(int i=0; i<indices.length; i++)
					{
						int ind = i;
						if (cas.isDescending()) ind = indices.length - i - 1;
						if (i != 0) sb.append(",");
						sb.append(" \\" + cas.getName() + "[" + indices[ind] + "] ");
					}
					sb.append(";");
				} else
				{
					int low = cas.getLowIndex(), high = cas.getHighIndex();
					if (cas.isDescending())
					{
						low = cas.getHighIndex();   high = cas.getLowIndex();
					}
					sb.append(" [" + low + ":" + high + "] " + cas.getName() + ";");
				}
			}
			if (cas.getFlags() != 0)
			{
				if (localPrefs.useVerilogA) sb.append("  electrical"); else
				{
					if (cas.getFlags() == 1) sb.append("  wire"); else
						sb.append("  trireg");
				}
				sb.append(" " + cas.getName() + ";");
			}
			sb.append("\n");
			writeWidthLimited(sb.toString(), moduleStr);
			first = false;
		}

		// write electrical nodes for CNFETs
		int flagvdd2 = 0, flaggnd2 = 0;
		if (localPrefs.useVerilogA)
		{
			for(Iterator<CellAggregateSignal> it = cni.getCellAggregateSignals(); it.hasNext(); )
			{
				CellAggregateSignal cas = it.next();
				Export pp = cas.getExport();
				if (pp == null) continue;
			
				String portType = "electrical";
				if ((pp.getCharacteristic() == PortCharacteristic.PWR)) flagvdd2++; else
					if (( pp.getCharacteristic() == PortCharacteristic.GND)) flaggnd2++;
				StringBuffer sb2 = new StringBuffer();
				sb2.append("  " + portType);
				if (cas.getLowIndex() > cas.getHighIndex())
				{
					sb2.append(" " + cas.getName() + ";");
				} else
				{
					int [] indices = cas.getIndices();
					if (indices != null)
					{
						for(int i=0; i<indices.length; i++)
						{
							int ind = i;
							if (cas.isDescending()) ind = indices.length - i - 1;
							if (i != 0) sb2.append(",");
							sb2.append(" \\" + cas.getName() + "[" + indices[ind] + "] ");
						}
						sb2.append(";");
					} else
					{
						int low = cas.getLowIndex(), high = cas.getHighIndex();
						if (cas.isDescending())
						{
							low = cas.getHighIndex();   high = cas.getLowIndex();
						}
						sb2.append(" [" + low + ":" + high + "] " + cas.getName() + ";");
					}
				}
				if (cas.getFlags() != 0)
				{
					if (cas.getFlags() == 1) sb2.append("  electrical"); else
						sb2.append("  electrical");
					sb2.append(" " + cas.getName() + ";");
					
				}
				sb2.append("\n");
				writeWidthLimited(sb2.toString(), moduleStr);
				first = false;
			}
		}
		if (!first) writeLine("", moduleStr); // printWriter.println();

		// if writing standard cell netlist, do not netlist contents of standard cells
		if (localPrefs.stopAtStandardCells)
		{
			if (SCLibraryGen.isStandardCell(cell)) {
				writeLine("endmodule   /* " + cellName + " */", moduleStr);
				return;
			}
		}

		// describe power and ground nets
		if (localPrefs.useVerilogA)
		{
			if (flagvdd2 == 0) writeLine("  electrical vdd;", moduleStr); // printWriter.println("  electrical vdd;");
			if (flaggnd2 == 0) writeLine("  electrical gnd;", moduleStr); // printWriter.println("  electrical gnd;");
		} else
		{
			if (cni.getPowerNet() != null) writeLine("  supply1 vdd;", moduleStr); // printWriter.println("  supply1 vdd;");
			if (cni.getGroundNet() != null) writeLine("  supply0 gnd;", moduleStr); // printWriter.println("  supply0 gnd;");
		}

		// determine whether to use "wire" or "trireg" for networks
		String wireType;
		if (localPrefs.useVerilogA)
		{
			wireType = "electrical";
		} else
		{
			wireType = "wire";
			if (localPrefs.useTrireg) wireType = "trireg";
		}

		// write "wire/trireg" declarations for internal single-wide signals
		int localWires = 0;
		Map<String,List<String>> internalSignals = new HashMap<String,List<String>>();
		for(Iterator<CellAggregateSignal> it = cni.getCellAggregateSignals(); it.hasNext(); )
		{
			CellAggregateSignal cas = it.next();
			if (cas.getExport() != null) continue;
			if (cas.isSupply()) continue;
			if (cas.getLowIndex() <= cas.getHighIndex()) continue;
			if (cas.isGlobal()) continue;

			String impSigName = wireType;
			if (cas.getFlags() != 0)
			{
				if (localPrefs.useVerilogA)
				{
					impSigName = "electrical";
				} else
				{
					if (cas.getFlags() == 1) impSigName = "wire"; else
						impSigName = "trireg";
				}
			}
			List<String> sigs = internalSignals.get(impSigName);
			if (sigs == null) internalSignals.put(impSigName, sigs = new ArrayList<String>());
			if (!sigs.contains(cas.getName()))
				sigs.add(cas.getName());
			localWires++;
		}
		for(String decType : internalSignals.keySet())
		{
			List<String> sigs = internalSignals.get(decType);
			if (sigs.size() == 0) continue;
			Collections.sort(sigs, TextUtils.STRING_NUMBER_ORDER);
			initDeclaration("  " + decType);
			for(String st : sigs) addDeclaration(st, moduleStr);
			termDeclaration(moduleStr);
		}

		// write "wire/trireg" declarations for internal busses
    	previouslyDeclared = new HashSet<String>();
		List<String> intBusses = new ArrayList<String>();
		for(Iterator<CellAggregateSignal> it = cni.getCellAggregateSignals(); it.hasNext(); )
		{
			CellAggregateSignal cas = it.next();
			if (cas.getExport() != null) continue;
			if (cas.isSupply()) continue;
			if (cas.getLowIndex() > cas.getHighIndex()) continue;
			if (cas.isGlobal()) continue;
			if (previouslyDeclared.contains(cas.getName())) continue;

			int [] indices = cas.getIndices();
			if (indices != null)
			{
				for(int i=0; i<indices.length; i++)
				{
					int ind = i;
					if (cas.isDescending()) ind = indices.length - i - 1;
					intBusses.add("  " + wireType + " \\" + cas.getName() + "[" + indices[ind] + "] ;");
				}
			} else
			{
				if (cas.isDescending())
				{
					intBusses.add("  " + wireType + " [" + cas.getHighIndex() + ":" + cas.getLowIndex() + "] " + cas.getName() + ";");
				} else
				{
					intBusses.add("  " + wireType + " [" + cas.getLowIndex() + ":" + cas.getHighIndex() + "] " + cas.getName() + ";");
				}
			}
			localWires++;
		}
		if (intBusses.size() > 0)
		{
			Collections.sort(intBusses, TextUtils.STRING_NUMBER_ORDER);
			for(String st : intBusses) writeLine(st, moduleStr);
		}
		if (localWires != 0) writeLine("", moduleStr);

		// add "wire" declarations for implicit inverters
		if (impInvCount > 0)
		{
			initDeclaration("  " + wireType);
			for(int i=0; i<impInvCount; i++)
			{
				String impsigname = IMPLICITINVERTERSIGNAME + i;
				addDeclaration(impsigname, moduleStr);
			}
			termDeclaration(moduleStr);
		}
		
		// add in any user-specified declarations and code
        if (!localPrefs.stopAtStandardCells || localPrefs.netlistNonstandardCells) {
            // STA does not like general verilog code (like and #delay out InA InB etc)
            first = includeTypedCode(cell, VERILOG_DECLARATION_KEY, "declarations", moduleStr);
		    first |= includeTypedCode(cell, VERILOG_CODE_KEY, "code", moduleStr);
		    if (!first)
		    {
		    	String verilogType = localPrefs.useVerilogA ? "VerilogA" : "Verilog";
		    	writeLine("  /* automatically generated " + verilogType + " */", moduleStr); // printWriter.println("  /* automatically generated " + verilogType + " */");
		    }
        }
        
        // accumulate port connectivity information for port directional consistency check
		Map<Network,Set<PortInst>> instancePortsOnNet = new HashMap<Network,Set<PortInst>>();
		int instanceCount = 0;
		
		// look at every node in this cell
		for(Iterator<Nodable> nIt = netList.getNodables(); nIt.hasNext(); )
		{
			Nodable no = nIt.next();
			NodeProto niProto = no.getProto();

			// not interested in passive nodes (ports electrically connected)
			PrimitiveNode.Function nodeType = PrimitiveNode.Function.UNKNOWN;
			if (!no.isCellInstance())
			{
				NodeInst ni = (NodeInst)no;
				Iterator<PortInst> pIt = ni.getPortInsts();
				if (pIt.hasNext())
				{
					boolean allConnected = true;
					PortInst firstPi = pIt.next();
					Network firstNet = netList.getNetwork(firstPi);
					for( ; pIt.hasNext(); )
					{
						PortInst pi = pIt.next();
						Network thisNet = netList.getNetwork(pi);
						if (thisNet != firstNet) { allConnected = false;   break; }
					}
					if (allConnected) continue;
				}
				nodeType = ni.getFunction();

				// special case: verilog should ignore R L C etc.
				if (nodeType.isResistor() || // == PrimitiveNode.Function.RESIST ||
					nodeType.isCapacitor() ||  // == PrimitiveNode.Function.CAPAC || nodeType == PrimitiveNode.Function.ECAPAC ||
					nodeType == PrimitiveNode.Function.INDUCT ||
					nodeType == PrimitiveNode.Function.DIODE || nodeType == PrimitiveNode.Function.DIODEZ)
						continue;
			}

			// look for a Verilog template on the prototype
			if (no.isCellInstance())
			{
				// Check if cell instance doesn't represent an empty module
				if (localPrefs.noEmptyModules && emptyModules.get(no.getProto()) != null)
				{
					System.out.println("Skipping " + no.getName() + " in " + cellName  + " since it is empty module");
					continue;
				}
										
                // if writing standard cell netlist, do not write out cells that
				// do not contain standard cells
				if (localPrefs.stopAtStandardCells && !localPrefs.netlistNonstandardCells)
				{
					if (!standardCells.containsStandardCell((Cell)niProto) &&
						!SCLibraryGen.isStandardCell((Cell)niProto)) continue;
				}

				Variable varTemplate = ((Cell)niProto).getVar(VERILOG_TEMPLATE_KEY);
				if (varTemplate != null)
				{
					if (varTemplate.getObject() instanceof String []) {
						String [] lines = (String [])varTemplate.getObject();
						writeWidthLimited("  /* begin Verilog_template for "+no.getProto().describe(false)+"*/\n", moduleStr);
						for (int i=0; i<lines.length; i++) {
							writeTemplate((Cell)niProto, cell, lines[i], no, cni, context,
								knownParameterNames, knownParameterNamesLC, moduleStr);
						}
						writeWidthLimited("  // end Verilog_template\n", moduleStr);
					} else {
						// special case: do not write out string "//"
						if (!((String)varTemplate.getObject()).equals("//")) {
							writeWidthLimited("  /* begin Verilog_template for "+no.getProto().describe(false)+"*/\n", moduleStr);
							writeTemplate((Cell)niProto, cell, (String)varTemplate.getObject(), no, cni, context,
								knownParameterNames, knownParameterNamesLC, moduleStr);
							writeWidthLimited("  // end Verilog_template\n", moduleStr);
						}
					}
					instanceCount++;
					continue;
				}
			}

            // look for a Verilog defparam template on the prototype
            // Defparam templates provide a mechanism for prepending per instance
            // parameters on verilog declarations
            if (no.isCellInstance())
            {
                Variable varDefparamTemplate = ((Cell)niProto).getVar(VERILOG_DEFPARAM_KEY);
                if (varDefparamTemplate != null)
                {
                    if (varDefparamTemplate.getObject() instanceof String []) {
                        String [] lines = (String [])varDefparamTemplate.getObject();
                        // writeWidthLimited("  /* begin Verilog_defparam for "+no.getProto().describe(false)+" */\n");
                        boolean firstDefparam = true;
                        for (int i=0; i<lines.length; i++) {
                        	String defparam = new String();
                        	defparam = writeDefparam(lines[i], no, context);
                        	if (defparam.length() != 0)
                        	{
                        		if (firstDefparam)
                        		{
                        			writeWidthLimited("  /* begin Verilog_defparam for "+no.getProto().describe(false)+" */\n", moduleStr);
                        			firstDefparam = false;
                        		}
                        		writeWidthLimited(defparam, moduleStr);
                        	}
                        }
                        if (!firstDefparam)
                        {
                        	writeWidthLimited("  // end Verilog_defparam\n", moduleStr);
                        }
                    } else
                    {
                        // special case: do not write out string "//"
                        if (!((String)varDefparamTemplate.getObject()).equals("//"))
                        {
                        	String defparam = new String();
                        	defparam = writeDefparam((String)varDefparamTemplate.getObject(), no, context);
                        	if (defparam.length() != 0)
                        	{
                            	writeWidthLimited("  /* begin Verilog_defparam for "+no.getProto().describe(false)+"*/\n", moduleStr);
                        		writeWidthLimited(defparam, moduleStr);
                                writeWidthLimited("  // end Verilog_defparam\n", moduleStr);
                        	}

                        }
                    }
                }
            }

			// use "assign" statement if requested
			if (localPrefs.useAssign)
			{
				if (nodeType == PrimitiveNode.Function.GATEAND || nodeType == PrimitiveNode.Function.GATEOR ||
					nodeType == PrimitiveNode.Function.GATEXOR || nodeType == PrimitiveNode.Function.BUFFER)
				{
					// assign possible: determine operator
					String op = "";
					if (nodeType == PrimitiveNode.Function.GATEAND) op = " & "; else
					if (nodeType == PrimitiveNode.Function.GATEOR) op = " | "; else
					if (nodeType == PrimitiveNode.Function.GATEXOR) op = " ^ ";

					// write a line describing this signal
					StringBuffer infstr = new StringBuffer();
					boolean wholeNegated = false;
					first = true;
					NodeInst ni = (NodeInst)no;
					for(int i=0; i<2; i++)
					{
						for(Iterator<Connection> cIt = ni.getConnections(); cIt.hasNext(); )
						{
							Connection con = cIt.next();
							PortInst pi = con.getPortInst();
							if (i == 0)
							{
								if (!pi.getPortProto().getName().equals("y")) continue;
							} else
							{
								if (!pi.getPortProto().getName().equals("a")) continue;
							}

							// determine the network name at this port
							ArcInst ai = con.getArc();
							Network net = netList.getNetwork(ai, 0);
							CellSignal cs = cni.getCellSignal(net);
							String sigName = getSignalName(cs);

							// see if this end is negated
							boolean isNegated = false;
							if (ai.isTailNegated() || ai.isHeadNegated()) isNegated = true;

							// write the port name
							if (i == 0)
							{
								// got the output port: do the left-side of the "assign"
								infstr.append("assign " + sigName + " = ");
								if (isNegated)
								{
									infstr.append("~(");
									wholeNegated = true;
								}
								break;
							}

							if (!first)
								infstr.append(op);
							first = false;
							if (isNegated) infstr.append("~");
							infstr.append(sigName);
						}
					}
					if (wholeNegated)
						infstr.append(")");
					infstr.append(";\n");
					writeWidthLimited(infstr.toString(), moduleStr);
					continue;
				}
			}

			// get the name of the node
			int implicitPorts = 0;
			boolean dropBias = false;
			String nodeName = "", trueNodeName = "";
			if (no.isCellInstance())
			{
				// make sure there are contents for this cell instance
				if (((Cell)niProto).isIcon()) continue;

				nodeName = trueNodeName = parameterizedName(no, context);

				// make sure to use the correct icon cell name if there are more than one
                NodeInst ni = no.getNodeInst();
                if (ni != null)
                {
                	String alternateSubCellName = getIconCellName((Cell)ni.getProto());
                	if (alternateSubCellName != null) nodeName = alternateSubCellName;
                }

                // cells defined as "primitives" in Verilog View must have implicit port ordering
            	if (definedPrimitives.containsKey(niProto))
            		implicitPorts = 3;
			} else
			{
				// convert 4-port transistors to 3-port
				PrimitiveNode.Function threePortEquiv = nodeType.make3PortTransistor();
				if (threePortEquiv != null)
				{
					nodeType = threePortEquiv;  dropBias = true;
				}

				if (nodeType.isNTypeTransistor())
				{
					implicitPorts = 2;
					nodeName = "tranif1";
					if (nodeType == PrimitiveNode.Function.TRANMOSCN) nodeName = "NCNFET";
					Variable varWeakNode = ((NodeInst)no).getVar(SimulationTool.WEAK_NODE_KEY);
					if (varWeakNode != null) nodeName = "rtranif1";
				} else if (nodeType.isPTypeTransistor())
				{
					implicitPorts = 2;
					nodeName = "tranif0";
					if (nodeType == PrimitiveNode.Function.TRAPMOSCN) nodeName = "PCNFET";
					Variable varWeakNode = ((NodeInst)no).getVar(SimulationTool.WEAK_NODE_KEY);
					if (varWeakNode != null) nodeName = "rtranif0";
				} else if (nodeType == PrimitiveNode.Function.GATEAND)
				{
					implicitPorts = 1;
					nodeName = chooseNodeName((NodeInst)no, "and", "nand");
				} else if (nodeType == PrimitiveNode.Function.GATEOR)
				{
					implicitPorts = 1;
					nodeName = chooseNodeName((NodeInst)no, "or", "nor");
				} else if (nodeType == PrimitiveNode.Function.GATEXOR)
				{
					implicitPorts = 1;
					nodeName = chooseNodeName((NodeInst)no, "xor", "xnor");
				} else if (nodeType == PrimitiveNode.Function.BUFFER)
				{
					implicitPorts = 1;
					nodeName = chooseNodeName((NodeInst)no, "buf", "not");
				}
				trueNodeName = nodeName;
			}
			if (nodeName.length() == 0) continue;

			// write the type of the node
			StringBuffer infstr = new StringBuffer();
			String instName = nameNoIndices(no.getName());

			// make sure instance name doesn't duplicate net name
			boolean clean = false;
			while (!clean)
			{
				clean = true;
				for(Iterator<CellAggregateSignal> it = cni.getCellAggregateSignals(); it.hasNext(); )
				{
					CellAggregateSignal cas = it.next();
					if (cas.getName().equals(instName)) { clean = false;   break; }
				}
				if (!clean) instName += "_";
			}

			// write node name
			infstr.append("  " + nodeName + " ");

			// write parameters if not embedded in cell name
			if (!canParameterizeNames())
			{
				// if there are parameters, write them now
				boolean found = false;
				for(Iterator<Variable> it = no.getDefinedParameters(); it.hasNext(); )
				{
					Variable var = it.next();
					String eval = var.describe(context, no.getNodeInst());
					if (eval == null) continue;
					int eqPos = eval.indexOf("=");
					if (eqPos < 0) continue;
					String varName = eval.substring(0, eqPos);
					if (!no.isCellInstance() || localPrefs.useBehavioralConstructs ||
						!SCLibraryGen.isStandardCell((Cell)no.getProto()))
					{
						// special test situation for JonL/JonG (remove next line for normal function)
						if (!varName.equalsIgnoreCase("delay"))
							continue;
					}
					if (!found) infstr.append("#("); else infstr.append(",");
					found = true;
					infstr.append("." + varName + "(" + eval.substring(eqPos+1) + ")");
				}
				if (found) infstr.append(") ");
			}

			// write instance name
			infstr.append(instName + "(");

			// write the rest of the ports
			first = true;
			NodeInst ni = null;
			switch (implicitPorts)
			{
				case 0:		// explicit ports (for cell instances)
					CellNetInfo subCni = getCellNetInfo(trueNodeName);
					for(Iterator<CellAggregateSignal> sIt = subCni.getCellAggregateSignals(); sIt.hasNext(); )
					{
						CellAggregateSignal cas = sIt.next();

						// ignore networks that aren't exported
						Export pp = cas.getExport();
						if (pp == null) continue;

						if (first) first = false; else
							infstr.append(", ");
                        NodeInst subNi = no.getNodeInst();
						if (cas.getLowIndex() > cas.getHighIndex())
						{
							// single signal
							infstr.append("." + cas.getName() + "(");
							int ind = cas.getExportIndex();
							Network net = netList.getNetwork(no, pp, ind);
                            if (pp.getParent() != subNi.getProto()) {
                            	Cell altCell = (Cell)subNi.getProto();
                            	Export altE = pp.findEquivalent(altCell);
                                //pp = pp.findEquivalent((Cell)subNi.getProto());
                            	if (altE == null)
                                {
                                	System.out.println("Warning: Cell " + pp.getParent().describe(false) + ", entry " + ind +
                                		" of port " + pp.getName() + " has no equivalent in cell " + altCell.describe(false));
                                	continue;
                                }
                            	pp = altE;
                            }
							PortInst pi = subNi.findPortInstFromEquivalentProto(pp);
							CellSignal cs = cni.getCellSignal(net);
						   	infstr.append(getSignalName(cs));
							infstr.append(")");
							accumulatePortConnectivity(instancePortsOnNet, net, pi);
						} else
						{
							int [] indices = cas.getIndices();
							if (indices != null)
							{
								// broken bus internally, write signals individually
								for(int i=0; i<indices.length; i++)
								{
									int ind = i;
									if (cas.isDescending()) ind = indices.length - i - 1;
									CellSignal cInnerSig = cas.getSignal(ind);
                                    Network net = netList.getNetwork(no, cInnerSig.getExport(), cInnerSig.getExportIndex());
                                    CellSignal outerSignal = cni.getCellSignal(net);
                                    Export subPP = cInnerSig.getExport();
                                    if (subPP.getParent() != subNi.getProto())
                                    	subPP = subPP.findEquivalent((Cell)subNi.getProto());
        							PortInst pi = subNi.findPortInstFromEquivalentProto(subPP);
									accumulatePortConnectivity(instancePortsOnNet, net, pi);

									if (i > 0) infstr.append(", ");
									infstr.append(".\\" + cas.getName() + "[" + indices[ind] + "] (");
								   	infstr.append(getSignalName(outerSignal));
									infstr.append(")");
								}
							} else
							{
								// simple bus, write signals
								int total = cas.getNumSignals();
								CellSignal [] outerSignalList = new CellSignal[total];
								for(int j=0; j<total; j++)
								{
									CellSignal cInnerSig = cas.getSignal(j);
									Export e = cas.getExport();

									// BUG #2098 fixed by adding this next line
									if (cInnerSig.getExport() != null) e = cInnerSig.getExport();
									int ind = cInnerSig.getExportIndex();
									Network net = netList.getNetwork(no, e, ind);
									outerSignalList[j] = cni.getCellSignal(net);
                                    if (e.getParent() != subNi.getProto()) {
                                    	Cell altCell = (Cell)subNi.getProto();
                                        Export altE = e.findEquivalent(altCell);
                                        if (altE == null)
                                        {
                                        	System.out.println("Warning: Cell " + e.getParent().describe(false) + ", entry " + ind +
                                        		" of port " + e.getName() + " has no equivalent in cell " + altCell.describe(false));
                                        	continue;
                                        }
                                        e = altE;
                                    }
        							PortInst pi = subNi.findPortInstFromEquivalentProto(e);
									accumulatePortConnectivity(instancePortsOnNet, net, pi);
								}
								writeBus(outerSignalList, total, cas.isDescending(),
									cas.getName(), cni.getPowerNet(), cni.getGroundNet(), infstr);
							}
						}
					}
					infstr.append(");");
					break;

				case 1:		// and/or gate: write ports in the proper order
					ni = (NodeInst)no;
					for(int i=0; i<2; i++)
					{
						for(Iterator<Connection> cIt = ni.getConnections(); cIt.hasNext(); )
						{
							Connection con = cIt.next();
							PortInst pi = con.getPortInst();
							if (i == 0)
							{
								if (!pi.getPortProto().getName().equals("y")) continue;
							} else
							{
								if (!pi.getPortProto().getName().equals("a")) continue;
							}
							if (first) first = false; else
								infstr.append(", ");
							ArcInst ai = con.getArc();
							Network net = netList.getNetwork(ai, 0);
							CellSignal cs = cni.getCellSignal(net);
							if (cs == null) continue;
							String sigName = getSignalName(cs);
							if (i != 0 && con.isNegated())
							{
								// this input is negated: write the implicit inverter
								Integer invIndex;
								if (con.getEndIndex() == ArcInst.HEADEND) invIndex = implicitHeadInverters.get(ai); else
									invIndex = implicitTailInverters.get(ai);
								if (invIndex != null)
								{
									String invSigName = IMPLICITINVERTERSIGNAME + invIndex.intValue();
									printWriter.println("  inv " + IMPLICITINVERTERNODENAME +
										invIndex.intValue() + " (" + invSigName + ", " + sigName + ");");
									sigName = invSigName;
								}
							}
							infstr.append(sigName);
						}
					}
					infstr.append(");");
					break;

				case 2:		// transistors: write ports in the proper order: s/d/g
					ni = (NodeInst)no;
					Network gateNet = netList.getNetwork(ni.getTransistorGatePort());
					for(int i=0; i<2; i++)
					{
						boolean didGate = false;
						for(Iterator<PortInst> pIt = ni.getPortInsts(); pIt.hasNext(); )
						{
							PortInst pi = pIt.next();
							Network net = netList.getNetwork(pi);
							if (dropBias && pi.getPortProto().getName().equals("b")) continue;
							CellSignal cs = cni.getCellSignal(net);
							if (cs == null) continue;
							if (i == 0)
							{
								if (net == gateNet) continue;
							} else
							{
								if (net != gateNet) continue;
								if (didGate) continue;
								didGate = true;
							}
							if (first) first = false; else
								infstr.append(", ");

							String sigName = getSignalName(cs);
							infstr.append(sigName);
						}
					}
					if (localPrefs.useVerilogA)
					{
						if (nodeType.isNTypeTransistor()) infstr.append(", gnd");
						if (nodeType.isPTypeTransistor()) infstr.append(", vdd");
					}
					infstr.append(");");
					break;
				case 3:			// implicit ports ordering for cells defined as primitives in Verilog View
					ni = no.getNodeInst();
					CompileVerilogStruct.VModule module = definedPrimitives.get(niProto);
					if (module == null) break;
					System.out.print(cell.getName()+" ports: ");
					for (CompileVerilogStruct.VExport port : module.getPorts()) {
						if (port.isBus()) {
							// Bill thinks bussed ports are not allowed for primitives
							reportError("Error: bussed ports not allowed on Verilog primitives: "+niProto.getName());
						} else {
							String portName = port.getName();
							PortInst pi = ni.findPortInst(portName);
							Network net = netList.getNetwork(no, pi.getPortProto().getNameKey().subname(0));
							CellSignal cs = cni.getCellSignal(net);
							String sigName = getSignalName(cs);

							if (first) first = false; else
								infstr.append(", ");
							infstr.append(sigName);
							System.out.print(portName+" ");
						}
					}
					System.out.println();
					infstr.append(");");
					break;
			}
			infstr.append("\n");
			writeWidthLimited(infstr.toString(), moduleStr);
			instanceCount++;
		}
		writeLine("endmodule   /* " + cellName + " */", moduleStr); // printWriter.println("endmodule   /* " + cellName + " */");
		
		if (instanceCount == 0)
			emptyModules.put(cell, cell);
		
		// forcing the writing when there are templates as we can't know if it is empty.
		if (!localPrefs.noEmptyModules || instanceCount != 0)
			printWriter.print(moduleStr);

		// check export direction consistency (inconsistent directions can cause opens in some tools)
		for(Iterator<Network> it = netList.getNetworks(); it.hasNext(); )
		{
			Network net = it.next();
//			int numInputs = 0;
			List<PortInst> outputPoints = new ArrayList<PortInst>();
			Set<PortInst> subports = instancePortsOnNet.get(net);
			if (subports == null) continue;
			for(PortInst pi : subports)
			{
				PortProto pp = pi.getPortProto();
				PortCharacteristic subtype = pp.getCharacteristic();
//				if (subtype == PortCharacteristic.IN) numInputs++; else
					if (subtype == PortCharacteristic.OUT) outputPoints.add(pi);
			}
			List<Export> inputExports = new ArrayList<Export>();
			boolean outExported = false;
			for(Iterator<Export> eIt = net.getExports(); eIt.hasNext(); )
			{
				Export e = eIt.next();
				PortCharacteristic subtype = e.getCharacteristic();
				if (subtype == PortCharacteristic.IN) inputExports.add(e); else
					if (subtype == PortCharacteristic.OUT) outExported = true;				
			}

			// make sure there are not multiple stimuli driving this network
			int numDriving = outputPoints.size();
			if (inputExports.size() > 0) numDriving++;
			if (numDriving > 1)
			{
				String errMsg = "Warning: Cell " + cell.describe(false) + ", network '" + net.getName() +
					"' has multiple outputs driving it:";
				boolean firstMention = true;
				for(PortInst pi : outputPoints)
				{
					if (!firstMention) errMsg += " /";
					firstMention = false;
					errMsg += " node " + pi.getNodeInst().describe(false) + " port " + pi.getPortProto().getName();
				}
				for(Export e : inputExports)
				{
					if (!firstMention) errMsg += " /";
					firstMention = false;
					errMsg += " export " + e.getName();
				}
				System.out.println(errMsg);
				continue;
			}

			// make sure the network is driven
			if (outExported && numDriving != 1)
			{
				String errMsg = "Warning: Cell " + cell.describe(false) + ", network '" + net.getName() +
					"' has exported ports but nothing driving it";
				System.out.println(errMsg);
				continue;
			}
		}
	}

	private String getSignalName(CellSignal cs)
	{
		CellAggregateSignal cas = cs.getAggregateSignal();
		if (cas == null) return cs.getName();
		if (cas.getIndices() == null) return cs.getName();
		return " \\" + cs.getName() + " ";
	}

	private String chooseNodeName(NodeInst ni, String positive, String negative)
	{
		for(Iterator<Connection> aIt = ni.getConnections(); aIt.hasNext(); )
		{
			Connection con = aIt.next();
			if (con.isNegated() &&
				con.getPortInst().getPortProto().getName().equals("y"))
					return negative;
		}
		return positive;
	}

	private void writeTemplate(Cell subCell, Cell cell, String line, Nodable no, CellNetInfo cni, VarContext context,
		Set<String> knownParameterNames, Set<String> knownParameterNamesLC, StringBuffer buffer)
	{
		// special case for Verilog templates
		Netlist netList = cni.getNetList();
		StringBuffer infstr = new StringBuffer();
		infstr.append("  ");
		for(int pt = 0; pt < line.length(); pt++)
		{
			char chr = line.charAt(pt);
			if (chr != '$' || pt+1 >= line.length() || line.charAt(pt+1) != '(')
			{
				// process normal character
				infstr.append(chr);
				continue;
			}

			int startpt = pt + 2;
			for(pt = startpt; pt < line.length(); pt++)
				if (line.charAt(pt) == ')') break;
			String paramName = line.substring(startpt, pt);
			PortProto pp = no.getProto().findPortProto(paramName);
			String nodeName = parameterizedName(no, context);
			CellNetInfo subCni = getCellNetInfo(nodeName);

			// see if aggregate signal matches port
			CellAggregateSignal cas = null;
			CellSignal netcs = null;

			if (pp != null && subCni != null) {
				// it matches the whole port (may be a bussed port)
				for (Iterator<CellAggregateSignal> it = subCni.getCellAggregateSignals(); it.hasNext(); ) {
					CellAggregateSignal cas2 = it.next();
					if (cas2.getExport() == pp) {
						cas = cas2;
						break;
					}
					if (netList.sameNetwork(no, pp, no, cas2.getExport())) {
						// this will be true if there are two exports connected together
						// in the subcell, and we are searching for the export name that is not used.
						cas = cas2;
						break;
					}
				}
			} else {
				// maybe it is a single bit in an bussed export
				for (Iterator<PortProto> it = no.getProto().getPorts(); it.hasNext(); ) {
					PortProto ppcheck = it.next();
					for (int i=0; i<ppcheck.getNameKey().busWidth(); i++) {
						if (paramName.equals(ppcheck.getNameKey().subname(i).toString())) {
							Network net = netList.getNetwork(no, ppcheck, i);
							netcs = cni.getCellSignal(net);
							break;
						}
					}
				}
			}

			if (cas != null) {
				// this code is copied from instantiated
				if (cas.getLowIndex() > cas.getHighIndex())
				{
					// single signal
					Network net = netList.getNetwork(no, pp, cas.getExportIndex());
					CellSignal cs = cni.getCellSignal(net);
					infstr.append(getSignalName(cs));
				} else
				{
					int total = cas.getNumSignals();
					CellSignal [] outerSignalList = new CellSignal[total];
					for(int j=0; j<total; j++)
					{
						CellSignal cInnerSig = cas.getSignal(j);
						Network net = netList.getNetwork(no, cas.getExport(), cInnerSig.getExportIndex());
						outerSignalList[j] = cni.getCellSignal(net);
					}
					writeBus(outerSignalList, total, cas.isDescending(),
						null, cni.getPowerNet(), cni.getGroundNet(), infstr);
				}
			} else if (netcs != null) {
				infstr.append(getSignalName(netcs));
			} else if (paramName.equalsIgnoreCase("node_name"))
			{
				infstr.append(getSafeNetName(no.getName(), true));
			} else
			{
				// no port name found, look for variable name
				Variable var = null;
				Variable.Key varKey = Variable.findKey("ATTR_" + paramName);
				if (varKey != null) {
					var = no.getParameterOrVariable(varKey);
				}
				if (var == null) infstr.append("??"); else
				{
					// use actual parameter name in behavioral verilog
					String varName = var.getTrueName();
					boolean useValue = true;
					if (localPrefs.useBehavioralConstructs)
					{
						if (knownParameterNames.contains(varName)) useValue = false;
						if (useValue && knownParameterNamesLC.contains(varName.toLowerCase()))
						{
							String trueVarName = null;
							for(String tvn : knownParameterNames)
								if (tvn.toLowerCase().equals(varName.toLowerCase())) trueVarName = tvn;
							System.out.println("WARNING: Template for cell " + subCell.describe(false) + " uses variable '" + varName +
								"' but calling cell " + cell.describe(false) + " has variable '" + trueVarName + "'");
						}
					}
					if (useValue)
					{
						infstr.append(context.evalVar(var));
					} else
					{
						infstr.append(varName);
					}
				}
			}
		}
		infstr.append("\n");
		writeWidthLimited(infstr.toString(), buffer);
	}

    /**
     * writeDefparam is a specialized version of writeTemplate.  This function will
     * take a Electric Variable (Attribute) and write it out as a parameter.
     * @param line
     * @param no
     * @param context
     * @return the parameter string.
     */
    private String writeDefparam(String line, Nodable no, VarContext context)
    {
        // special case for Verilog defparams
        StringBuffer infstr = new StringBuffer();
        // Setup a standard template for defparams
        infstr.append("  defparam ");
        // Get the nodes instance name
        infstr.append(getSafeNetName(no.getName(), true));
        infstr.append(".");
        // Prepend whatever text is on the line, assuming it is a a variable per line
        // Garbage will be generated otherwise
        for(int pt = 0; pt < line.length(); pt++)
        {
            char chr = line.charAt(pt);
            if (chr != '$' || pt+1 >= line.length() || line.charAt(pt+1) != '(')
            {
                // process normal character
                infstr.append(chr);
                continue;
            }

            int startpt = pt + 2;
            for(pt = startpt; pt < line.length(); pt++)
                if (line.charAt(pt) == ')') break;
            String paramName = line.substring(startpt, pt);
            // Check the current parameter value against the default parameter value
			// only overwrite if they are different.
            if (!(no.getProto() instanceof Cell))
            {
            	reportError("Illegal attempt to replace a variable.");
            	return "";
            }
			String defaultValue = replaceVariable(paramName, (Cell) no.getProto());
            String paramValue = replaceVariable(paramName, no, context);

            if (paramValue == "" || paramValue.equals(defaultValue)) return "";
        	infstr.append(paramName);
        	infstr.append(" = ");
            infstr.append(paramValue);
        	infstr.append(";");
        }
        infstr.append("\n");
        return infstr.toString();
    }

    /**
	 * replaceVariable - Replace Electric variables (attributes) with their
	 * values on instances of a cell. Given that left and right parentheses are not
	 * valid characters in Verilog identifiers, any Verilog code that matches
	 * $([a-zA-Z0-9_$]*) is not a valid sequence. This allows us to extract any
	 * electric variable and replace it with the attribute value.
	 * Additionally, search the value of the Electric variable for a nested
	 * variable and replace that as well.
	 * Added for ArchGen Plugin - BVE
	 *
	 * @param varName
	 * @param no
	 * @param context
	 * @return the Variable string.
	 */
    private String replaceVariable(String varName, Nodable no, VarContext context)
    {
    	// FIXIT - BVE should this be getSafeCellName
    	if (varName.equalsIgnoreCase("node_name"))
    		return getSafeNetName(no.getName(), true);

		// look for variable name
		Variable var = null;
		Variable.Key varKey = Variable.findKey("ATTR_" + varName);
		if (varKey != null)
		{
			var = no.getVar(varKey);
			if (var == null) var = no.getParameter(varKey);
        }
		if (var == null) return "";
		String val = String.valueOf(context.evalVar(var));

		// Semi-recursive call to search the value of a variable for a nested variable.
		return replaceVarInString(val, no.getParent());
    }

    /**
	 * replaceVariable - Replace Electric variables (attributes) with their
	 * values on definitions of a cell. Given that left and right parentheses are not
	 * valid characters in Verilog identifiers, any verilog code that matches
	 * $([a-zA-Z0-9_$]*) is not a valid sequence. This allows us to extract any
	 * electric variable and replace it with the attribute value.
	 * Added for ArchGen Plugin - BVE
	 *
	 * @param varName
	 * @param cell
	 * @return the Variable string.
	 */
    private String replaceVariable(String varName, Cell cell)
    {
    	// FIXIT - BVE should this be getSafeCellName
    	if (varName.equalsIgnoreCase("node_name"))
    		return getSafeNetName(cell.getName(), true);

    	// look for variable name
		Variable var = null;
		Variable.Key varKey = Variable.findKey("ATTR_" + varName);
		if (varKey != null) {
			var = cell.getVar(varKey);
			if (var == null) var = cell.getParameter(varKey);
        }
		if (var == null) return "";

		// Copied this code from VarContext.java - evalVarRecurse
        CodeExpression.Code code = var.getCode();
        Object value = var.getObject();

        if ((code == CodeExpression.Code.JAVA) ||
        	(code == CodeExpression.Code.TCL) ||
        	(code == CodeExpression.Code.SPICE)) return "";

        return String.valueOf(value);
    }

    /**
	 * replaceVarInString will search a string and replace any electric variable
	 * with its attribute or parameter value, if it has one.
	 * Added for ArchGen Plugin - BVE
	 *
	 * @param line
	 * @param cell
	 * @return the Variable string.
	 */
    private String replaceVarInString(String line, Cell cell)
    {
        StringBuffer infstr = new StringBuffer();
        // Search the line for any electric variables
        for(int pt = 0; pt < line.length(); pt++)
        {
            char chr = line.charAt(pt);
            if (chr != '$' || pt+1 >= line.length() || line.charAt(pt+1) != '(')
            {
                // process normal character
                infstr.append(chr);
                continue;
            }

            int startpt = pt + 2;
            for(pt = startpt; pt < line.length(); pt++)
                if (line.charAt(pt) == ')') break;
            String paramName = line.substring(startpt, pt);
            String paramValue = replaceVariable(paramName, cell);

            // If the parameter doesn't have a value, then look in the bus parameters
            if (paramValue != "") {
            	infstr.append(paramValue);
            }else {
            	paramValue = BusParameters.replaceBusParameterInt("$("+paramName+")");
                // If the parameter isn't a bus parameter, then just leave the paramName
            	if (paramValue != "") {
            		infstr.append(paramValue);
            	} else
            	{
            		infstr.append("$(");
            		infstr.append(paramName);
            		infstr.append(")");
            	}
            }
        }
        return infstr.toString();
    }

	/**
	 * Method to add a bus of signals named "name" to the infinite string "infstr".  If "name" is zero,
	 * do not include the ".NAME()" wrapper.  The signals are in "outerSignalList" and range in index from
	 * "lowindex" to "highindex".  They are described by a bus with characteristic "tempval"
	 * (low bit is on if the bus descends).  Any unconnected networks can be numbered starting at
	 * "*unconnectednet".  The power and grounds nets are "pwrnet" and "gndnet".
	 */
	private void writeBus(CellSignal [] outerSignalList, int total, boolean descending,
						String name, Network pwrNet, Network gndNet, StringBuffer infstr)
	{
		// array signal: see if it gets split out
		boolean breakBus = false;

		// bus cannot have pwr/gnd, must be connected
		int numExported = 0, numInternal = 0;
		for(int j=0; j<total; j++)
		{
			CellSignal cs = outerSignalList[j];
			CellAggregateSignal cas = cs.getAggregateSignal();
			if (cas != null && cas.getIndices() != null) { breakBus = true;   break; }
			if (cs.isPower() || cs.isGround()) { breakBus = true;   break; }
			if (cs.isExported()) numExported++; else
				numInternal++;
		}

		// must be all exported or all internal, not a mix
		if (numExported > 0 && numInternal > 0) breakBus = true;

		if (!breakBus)
		{
			// see if all of the nets on this bus are distinct
			int j = 1;
			for( ; j<total; j++)
			{
				CellSignal cs = outerSignalList[j];
				int k = 0;
				for( ; k<j; k++)
				{
					CellSignal oCs = outerSignalList[k];
					if (cs == oCs) break;
				}
				if (k < j) break;
			}
			if (j < total) breakBus = true; else
			{
				// bus entries must have the same root name and go in order
				String lastnetname = null;
				for(j=0; j<total; j++)
				{
					CellSignal wl = outerSignalList[j];
					String thisnetname = getSignalName(wl);
					if (wl.isDescending())
					{
						if (!descending) break;
					} else
					{
						if (descending) break;
					}

					int openSquare = thisnetname.indexOf('[');
					if (openSquare < 0) break;
					if (j > 0)
					{
						int li = 0;
						for( ; li < lastnetname.length(); li++)
						{
							if (thisnetname.charAt(li) != lastnetname.charAt(li)) break;
							if (lastnetname.charAt(li) == '[') break;
						}
						if (lastnetname.charAt(li) != '[' || thisnetname.charAt(li) != '[') break;
						int thisIndex = TextUtils.atoi(thisnetname.substring(li+1));
						int lastIndex = TextUtils.atoi(lastnetname.substring(li+1));
						if (thisIndex != lastIndex + 1) break;
					}
					lastnetname = thisnetname;
				}
				if (j < total) breakBus = true;
			}
		}

		if (name != null) infstr.append("." + name + "(");
		if (breakBus)
		{
			infstr.append("{");
			int start = 0, end = total-1;
			int order = 1;
			if (descending)
			{
				start = total-1;
				end = 0;
				order = -1;
			}
			for(int j=start; ; j += order)
			{
				if (j != start)
					infstr.append(", ");
				CellSignal cs = outerSignalList[j];
				infstr.append(getSignalName(cs));
				if (j == end) break;
			}
			infstr.append("}");
		} else
		{
			CellSignal lastCs = outerSignalList[0];
			String lastNetName = getSignalName(lastCs);
			int openSquare = lastNetName.indexOf('[');
			CellSignal cs = outerSignalList[total-1];
			String netName = getSignalName(cs);
			int i = 0;
			for( ; i<netName.length(); i++)
			{
				if (netName.charAt(i) == '[') break;
				infstr.append(netName.charAt(i));
			}
			if (descending)
			{
				int first = TextUtils.atoi(netName.substring(i+1));
				int second = TextUtils.atoi(lastNetName.substring(openSquare+1));
//				if (first == second) infstr.append("[" + first + "]"); else
					infstr.append("[" + first + ":" + second + "]");
			} else
			{
				int first = TextUtils.atoi(netName.substring(i+1));
				int second = TextUtils.atoi(lastNetName.substring(openSquare+1));
//				if (first == second) infstr.append("[" + first + "]"); else
					infstr.append("[" + second + ":" + first + "]");
			}
		}
		if (name != null) infstr.append(")");
	}

	/**
	 * Method to add text from all nodes in cell "np"
	 * (which have "verilogkey" text on them)
	 * to that text to the output file.  Returns true if anything
	 * was found.
	 */
	private boolean includeTypedCode(Cell cell, Variable.Key verilogkey, String descript, StringBuffer moduleStr)
	{
		if (verilogkey == VERILOG_EXTERNAL_CODE_KEY)
		{
			if (externalCodeWritten.contains(cell)) return false;
			externalCodeWritten.add(cell);
		}

		// write out any directly-typed Verilog code
		boolean first = true;
		for(Iterator<NodeInst> it = cell.getNodes(); it.hasNext(); )
		{
			NodeInst ni = it.next();
			if (ni.getProto() != Generic.tech().invisiblePinNode) continue;
			Variable var = ni.getVar(verilogkey);
			if (var == null) continue;
			if (!var.isDisplay()) continue;
			Object obj = var.getObject();
			if (!(obj instanceof String) && !(obj instanceof String [])) continue;
			if (first)
			{
				first = false;
            	writeLine("  /* user-specified Verilog " + descript + " */", moduleStr);
			}
			if (obj instanceof String)
			{
            	String tmp = replaceVarInString((String)obj, cell);
				if (verilogkey == VERILOG_DECLARATION_KEY) examineDeclaration(tmp);
            	writeLine("  " + tmp, moduleStr);
			} else
			{
				String [] stringArray = (String [])obj;
				int len = stringArray.length;
				for(int i=0; i<len; i++) {
                	String tmp = replaceVarInString(stringArray[i], cell);
    				if (verilogkey == VERILOG_DECLARATION_KEY) examineDeclaration(tmp);
                	writeLine("  " + tmp, moduleStr);
                }
			}
		}
		if (!first) writeLine("", moduleStr);
		return first;
	}
	
	private void writeLine(String str, StringBuffer strBuff)
	{
		if (strBuff == null)
			printWriter.println(str);
		else
			strBuff.append(str + "\n");
	}
	
	/**
	 * Method to parse a "declaration" line and make a list of declared signals
	 * so that they aren't declared again by the netlister.
	 * @param line the line in the "Verilog declaration" code.
	 */
	private void examineDeclaration(String line)
	{
		line = line.trim();
		if (line.startsWith("electrical")) examineSignals(line.substring(10).trim());
		if (line.startsWith("trireg")) examineSignals(line.substring(6).trim());
		if (line.startsWith("wire")) examineSignals(line.substring(4).trim());
		if (line.startsWith("reg")) examineSignals(line.substring(3).trim());
	}

	private void examineSignals(String line)
	{
		String[] sigNames = line.split(",");
		for(int i=0; i<sigNames.length; i++)
		{
			String sigName = sigNames[i].trim();
			if (sigName.endsWith(";")) sigName = sigName.substring(0, sigName.length()-1).trim();
			previouslyDeclared.add(sigName);
		}
	}

	private StringBuffer sim_verDeclarationLine;
	private int sim_verdeclarationprefix;

	/**
	 * Method to initialize the collection of signal names in a declaration.
	 * The declaration starts with the string "header".
	 */
	private void initDeclaration(String header)
	{
		sim_verDeclarationLine = new StringBuffer();
		sim_verDeclarationLine.append(header);
		sim_verdeclarationprefix = header.length();
	}

	/**
	 * Method to add a signal name to the collection of signal names in a declaration.
	 */
	private void addDeclaration(String signame, StringBuffer buffer)
	{
		if (sim_verDeclarationLine.length() + signame.length() + 3 > MAXDECLARATIONWIDTH)
		{
			writeLine(sim_verDeclarationLine.toString() + ";", buffer);
			sim_verDeclarationLine.delete(sim_verdeclarationprefix, sim_verDeclarationLine.length());
		}
		if (sim_verDeclarationLine.length() != sim_verdeclarationprefix)
			sim_verDeclarationLine.append(",");
		sim_verDeclarationLine.append(" " + signame);
	}

	/**
	 * Method to terminate the collection of signal names in a declaration
	 * and write the declaration to the Verilog file.
	 */
	private void termDeclaration(StringBuffer buffer)
	{
		if (buffer != null) 
			buffer.append(sim_verDeclarationLine.toString() + ";\n");
		else
			printWriter.println(sim_verDeclarationLine.toString() + ";");
	}

	/**
	 * Method to adjust name "p" and return the string.
	 * This code removes all index indicators and other special characters, turning
	 * them into "_".
	 */
	private String nameNoIndices(String p)
	{
		StringBuffer sb = new StringBuffer();
		if (TextUtils.isDigit(p.charAt(0))) sb.append('_');
		for(int i=0; i<p.length(); i++)
		{
			char chr = p.charAt(i);
			if (!TextUtils.isLetterOrDigit(chr) && chr != '_' && chr != '$') chr = '_';
			sb.append(chr);
		}
		return sb.toString();
	}

	/****************************** SUBCLASSED METHODS FOR THE TOPOLOGY ANALYZER ******************************/

	/**
	 * Method to adjust a cell name to be safe for Verilog output.
	 * @param name the cell name.
	 * @return the name, adjusted for Verilog output.
	 */
	protected String getSafeCellName(String name)
	{
		String n = getSafeNetName(name, false);
		// [ and ] are not allowed in cell names
		return n.replaceAll("[\\[\\]]", "_");
	}

	/** Method to return the proper name of Power */
	protected String getPowerName(Network net)
	{
		if (net != null)
		{
			// favor "vdd" if it is present
			for(Iterator<String> it = net.getNames(); it.hasNext(); )
			{
				String netName = it.next();
				if (netName.equalsIgnoreCase("vdd")) return netName;
			}
		}
		return null;
	}

	/** Method to return the proper name of Ground */
	protected String getGroundName(Network net)
	{
		if (net != null)
		{
			// favor "gnd" if it is present
			for(Iterator<String> it = net.getNames(); it.hasNext(); )
			{
				String netName = it.next();
				if (netName.equalsIgnoreCase("gnd")) return netName;
			}
		}
		return null;
	}

	/** Method to return the proper name of a Global signal */
	protected String getGlobalName(Global glob) { return "glbl." + glob.getName(); }

	/** Method to report that export names DO take precedence over
	 * arc names when determining the name of the network. */
	protected boolean isNetworksUseExportedNames() { return true; }

	/** Method to report that library names ARE always prepended to cell names. */
	protected boolean isLibraryNameAlwaysAddedToCellName() { return true; }

	/** Method to report that aggregate names (busses) ARE used. */
	protected boolean isAggregateNamesSupported() { return true; }

	/** Abstract method to decide whether aggregate names (busses) can have gaps in their ranges. */
	protected boolean isAggregateNameGapsSupported() { return true; }

	/** Method to report whether input and output names are separated. */
	protected boolean isSeparateInputAndOutput() { return true; }

	/** Abstract method to decide whether netlister is case-sensitive (Verilog) or not (Spice). */
	protected boolean isCaseSensitive() { return true; }

	/**
	 * Method to adjust a network name to be safe for Verilog output.
	 * Verilog does permit a digit in the first location; prepend a "_" if found.
	 * Verilog only permits the "_" and "$" characters: all others are converted to "_".
	 * Verilog does not permit non-numeric indices, so "P[A]" is converted to "P_A_"
	 * Verilog does not permit multidimensional arrays, so "P[1][2]" is converted to "P_1_[2]"
	 *   and "P[1][T]" is converted to "P_1__T_"
	 * @param bus true if this is a bus name.
	 */
	protected String getSafeNetName(String name, boolean bus)
	{
		// simple names are trivially accepted as is
		boolean allAlnum = true;
		int len = name.length();
		if (len == 0) return name;
		int openSquareCount = 0;
		int openSquarePos = 0;
		for(int i=0; i<len; i++)
		{
			char chr = name.charAt(i);
			if (chr == '[') { openSquareCount++;   openSquarePos = i; }
			if (!TextUtils.isLetterOrDigit(chr)) allAlnum = false;
			if (i == 0 && TextUtils.isDigit(chr)) allAlnum = false;
		}
		if (!allAlnum || !Character.isLetter(name.charAt(0)))
		{
			// if there are indexed values, make sure they are numeric
			if (openSquareCount == 1)
			{
				if (openSquarePos+1 >= name.length() ||
					!Character.isDigit(name.charAt(openSquarePos+1))) openSquareCount = 0;
			}
			if (bus) openSquareCount = 0;

			StringBuffer sb = new StringBuffer();
			for(int t=0; t<name.length(); t++)
			{
				char chr = name.charAt(t);
				if (chr == '[' || chr == ']')
				{
					if (openSquareCount == 1) sb.append(chr); else
					{
						sb.append('_');

						// merge two underscores into one
//						if (t+1 < name.length() && chr == ']' && name.charAt(t+1) == '[') t++;
					}
				} else
				{
					if (t == 0 && TextUtils.isDigit(chr)) sb.append('_');
					if (TextUtils.isLetterOrDigit(chr) || chr == '$')
					{
						sb.append(chr);
					} else
					{
						if (t == 4 && name.substring(0, 5).equals("glbl.")) sb.append('.'); else
							sb.append('_');
					}
				}
			}
			name = sb.toString();
		}

		// make sure it isn't a reserved word
		if (reservedWords.contains(name)) name = "_" + name;

		return name;
	}

	/** Tell the Hierarchy enumerator how to short resistors */
	@Override
	protected Netlist.ShortResistors getShortResistors() { return Netlist.ShortResistors.PARASITIC; }

	/**
	 * Method to tell whether the topological analysis should mangle cell names that are parameterized.
	 */
	protected boolean canParameterizeNames() {
		if (localPrefs.stopAtStandardCells && !localPrefs.netlistNonstandardCells)
			return false;

		// Check user preference
		if (localPrefs.parameterizeModuleNames) return true;
		return false;
	}

	private static final String [] verilogGates = new String [] {
		"and",
		"nand",
		"or",
		"nor",
		"xor",
		"xnor",
		"buf",
		"bufif0",
		"bufif1",
		"not",
		"notif0",
		"notif1",
		"pulldown",
		"pullup",
		"nmos",
		"rnmos",
		"pmos",
		"rpmos",
		"cmos",
		"rcmos",
		"tran",
		"rtran",
		"tranif0",
		"rtranif0",
		"tranif1",
		"rtranif1",
	};

	/**
	 * Perform some checks on parsed Verilog data, including the
	 * check that module is defined for the cell it is replacing.
	 * Some checks are different depending upon if the source is
	 * a Verilog View or an included external file.
	 * @param data the parsed Verilog data
	 * @param cell the cell to be replaced (must be verilog view if replacing Verilog View)
	 * @param includeFile the included file (null if replacing Verilog View)
	 * @return false if there was an error
	 */
	private boolean checkIncludedData(CompileVerilogStruct data, Cell cell, String includeFile) {
		// make sure there is module for current cell
		List<CompileVerilogStruct.VModule> modules = data.getModules();
		CompileVerilogStruct.VModule main = null, alternative = null;
		for (CompileVerilogStruct.VModule mod : modules)
		{
			if (mod.getName().equals(getVerilogName(cell)))
				main = mod;
			else {
				Cell.CellGroup grp = cell.getCellGroup(); // if cell group is available
				if (grp != null && mod.getName().equals(grp.getName()))
					alternative = mod;
			}
		}
		if (main == null) main = alternative;
		if (main == null) {
			reportError("Error! Expected Verilog module definition '" + getVerilogName(cell) +
				" in Verilog View: "+cell.libDescribe());
			return false;
		}
        if (main.isPrimitive())
        	definedPrimitives.put(cell, main);

		String source = includeFile == null ? "Verilog View for " + cell.libDescribe() :
			"Include file: " + includeFile;

		// check that modules have not already been defined
		for (CompileVerilogStruct.VModule mod : modules) {
			String prevSource = definedModules.get(mod.getName());
			if (mod.getCell() != null) {
				if (prevSource != null) {
					reportError("Error, module "+mod.getName() + " already defined from: " + prevSource);
				}
				else
					definedModules.put(mod.getName(), source);
			}
		}

		// make sure ports for module match ports for cell
//		Collection<VerilogData.VerilogPort> ports = main.getPorts();
		// not sure how to do this right now

		// check for undefined instances, search libraries for them
		for (CompileVerilogStruct.VModule mod : modules)
		{
			for (CompileVerilogStruct.VInstance inst : mod.getInstances())
			{
				CompileVerilogStruct.VModule instMod = inst.getModule();
				if (instMod == null || instMod.getCell() != null || instMod.isDefined())
					continue;	   // found in file, continue

				// check if primitive
				String moduleName = instMod.getName();
				boolean primitiveGate = false;
				for (String s : verilogGates)
				{
					if (s.equals(moduleName.toLowerCase()))
					{
						primitiveGate = true;
						break;
					}
				}
				if (primitiveGate) continue;

				// undefined, look in modules already written
				String found = definedModules.get(moduleName);
				if (found == null && includeFile == null) {
					// search libraries for it
					Cell missingCell = findCell(moduleName, View.VERILOG);
					if (missingCell == null) {
						reportError("Error: Undefined reference to module " + moduleName + ", and no matching cell found");
						continue;
					}
					// name map might be wrong at for this new enumeration
					System.out.println("Info: Netlisting cell " + missingCell.libDescribe() + " as instanced in: " + source);
					HierarchyEnumerator.enumerateCell(missingCell, VarContext.globalContext,
						new Visitor(this), getShortResistors());
				}
			}
		}
		return true;
	}

	/**
	 * Get the Verilog-style name for a cell.
	 * @param cell the cell to name.
	 * @return the name of the cell.
	 */
	private String getVerilogName(Cell cell) {
        // this should mirror the code in parameterizedName(), minus the parameter stuff
        String uniqueCellName = getUniqueCellName(cell);
        if (uniqueCellName != null) return uniqueCellName;
        // otherwise, use old code
        String safeCellName = getSafeCellName(cell.getName());
		if (!safeCellName.startsWith("_")) safeCellName = "__" + safeCellName;
		return cell.getLibrary().getName() + safeCellName;
    }

	/**
	 * Find a cell corresponding to the Verilog-style name
	 * of a cell. See
	 * {@link Verilog#getVerilogName(com.sun.electric.database.hierarchy.Cell)}.
	 * @param verilogName the Verilog-style name of the cell
	 * @param preferredView the preferred cell view. Schematic if null.
	 * @return the cell, or null if not found
	 */
	public static Cell findCell(String verilogName, View preferredView)
    {
        Cell cell = null;
        String [] parts = verilogName.split("__");

        // Assuming Library_Cell format
        if (parts.length == 2)
        {
            Library lib = Library.findLibrary(parts[0]);
            if (lib == null) {
                System.out.println("Cannot find library "+parts[0]+" for Verilog-style module name: "+verilogName);
                return null;
            }
            if (preferredView == null) preferredView = View.SCHEMATIC;
            cell = lib.findNodeProto(parts[1]);

            if (cell == null) {
                System.out.println("Cannot find Cell "+parts[1]+" in Library "+parts[0]+" for Verilog-style module name: "+verilogName);
                return null;
		    }
        }
        else
        {
            // just try to find first cell with name
            cell = Library.findCellInLibraries(verilogName, View.SCHEMATIC, null);
            if (cell == null) {
                System.out.println("Cannot find Cell '" + verilogName +"' for Verilog-style module name: "+verilogName);
                return null;
		    }
        }
		Cell preferred = cell.otherView(preferredView);
		if (preferred != null) return preferred;

		preferred = cell.getMainSchematicInGroup();
		if (preferred != null) return preferred;

		return cell;
	}

	private static void accumulatePortConnectivity(Map<Network,Set<PortInst>> instancePortsOnNet, Network net, PortInst pi)
	{
		Set<PortInst> list = instancePortsOnNet.get(net);
		if (list == null) instancePortsOnNet.put(net, list = new HashSet<PortInst>());
		list.add(pi);
	}
}
