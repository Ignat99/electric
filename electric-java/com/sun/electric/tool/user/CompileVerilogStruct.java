/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: CompileVerilogStruct.java
 * Compile Structural Verilog to a netlist
 *
 * Copyright (c) 2010, Static Free Software. All rights reserved.
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
package com.sun.electric.tool.user;

import com.sun.electric.database.EditingPreferences;
import com.sun.electric.database.geometry.EPoint;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.Export;
import com.sun.electric.database.hierarchy.Library;
import com.sun.electric.database.hierarchy.View;
import com.sun.electric.database.network.Netlist;
import com.sun.electric.database.network.Network;
import com.sun.electric.database.prototype.NodeProto;
import com.sun.electric.database.prototype.PortCharacteristic;
import com.sun.electric.database.prototype.PortProto;
import com.sun.electric.database.topology.ArcInst;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.topology.PortInst;
import com.sun.electric.database.variable.TextDescriptor;
import com.sun.electric.technology.ArcProto;
import com.sun.electric.technology.PrimitiveNode;
import com.sun.electric.technology.technologies.Artwork;
import com.sun.electric.technology.technologies.Generic;
import com.sun.electric.technology.technologies.Schematics;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.JobException;
import com.sun.electric.tool.simulation.acl2.mods.Lhrange;
import com.sun.electric.tool.simulation.acl2.mods.Lhs;
import com.sun.electric.tool.simulation.acl2.mods.ModName;
import com.sun.electric.tool.simulation.acl2.mods.Name;
import com.sun.electric.tool.simulation.acl2.modsext.DesignExt;
import com.sun.electric.tool.simulation.acl2.modsext.DriverExt;
import com.sun.electric.tool.simulation.acl2.modsext.ModInstExt;
import com.sun.electric.tool.simulation.acl2.modsext.ModuleExt;
import com.sun.electric.tool.simulation.acl2.modsext.PathExt;
import com.sun.electric.tool.simulation.acl2.modsext.WireExt;
import com.sun.electric.tool.simulation.acl2.svex.BigIntegerUtil;
import com.sun.electric.tool.simulation.acl2.svex.Svar;
import com.sun.electric.util.TextUtils;
import com.sun.electric.util.acl2.ACL2Object;
import com.sun.electric.util.acl2.ACL2Reader;
import com.sun.electric.util.math.DBMath;
import com.sun.electric.util.math.Orientation;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This is the structural Verilog compiler.
 */
public class CompileVerilogStruct
{
    private List<VModule> allModules;
    private int errorCount;
    private ErrorLogger errorLogger;
    private boolean hasParentLogger;
    private boolean verbose;
    private boolean hasErrors;
    private VModule curModule;
    private List<TokenList> tList;
    private int tokenIndex;

    /**
     * ******** Modules ************************************************
     */
    /**
     * Class to define a Verilog Module (or Primitive)
     */
    public class VModule
    {
        /**
         * name of entity
         */
        private String name;
        /**
         * true if cell in Verilog
         */
        private boolean defined;
        /**
         * true if a primitive
         */
        private boolean primitive;
        /**
         * cell of this module
         */
        private Cell cell;
        /**
         * list of ports
         */
        private List<VExport> ports;
        /**
         * list of internal wires
         */
        private List<String> wires;
        /**
         * list of instances
         */
        private List<VInstance> instances;
        /**
         * list of assignments
         */
        private Map<String, String> assignments;
        /**
         * networks in the module
         */
        private Map<String, List<VPort>> allNetworks;

        VModule(String name, boolean defined, boolean primitive)
        {
            this.name = name;
            this.defined = defined;
            this.primitive = primitive;
            cell = null;
            ports = new ArrayList<VExport>();
            wires = new ArrayList<String>();
            instances = new ArrayList<VInstance>();
            allNetworks = new HashMap<String, List<VPort>>();
            assignments = new HashMap<String, String>();
            allModules.add(this);
        }

        /**
         * Method to tell whether this VModule is a Verilog Module or Primitive.
         *
         * @return true if it is a Primitive.
         */
        public boolean isPrimitive()
        {
            return primitive;
        }

        /**
         * Method to return the name of this VModule.
         *
         * @return the Verilog module name.
         */
        public String getName()
        {
            return name;
        }

        /**
         * Method to return a List of Instances inside of this Verilog Module.
         *
         * @return a List of VInstance objects inside of this VModule.
         */
        public List<VInstance> getInstances()
        {
            return instances;
        }

        /**
         * Method to return a List of exports inside of this Verilog Module.
         *
         * @return a list of VExport objects inside of this VModule.
         */
        public List<VExport> getPorts()
        {
            return ports;
        }

        /**
         * Method to tell if a cell is already defined in the module.
         *
         * @return false if the cell is not defined
         */
        public boolean isDefined()
        {
            return defined;
        }

        /**
         * Method to return the Electric Cell associated with this VModule.
         *
         * @return the Cell (null if none found).
         */
        public Cell getCell()
        {
            return cell;
        }
    };

    /**
     * ******** Exports on Modules *********************************
     */
    private static final int MODE_UNKNOWN = 0;
    private static final int MODE_IN = 1;
    private static final int MODE_OUT = 2;
    private static final int MODE_INOUT = 3;

    /**
     * Class to define ports on Verilog Modules.
     * These are "formal ports", defined in the module header.
     */
    public static class VExport
    {
        /**
         * name of port
         */
        private String name;
        /**
         * mode of port
         */
        private int mode;
        /**
         * range of port
         */
        private int firstIndex, secondIndex;

        public VExport(String name)
        {
            this.name = name;
            mode = MODE_UNKNOWN;
            firstIndex = secondIndex = -1;
        }

        /**
         * Method to return the name of this module port.
         *
         * @return the name of this VExport.
         */
        public String getName()
        {
            return name;
        }

        /**
         * Method to tell whether this port is bussed.
         *
         * @return true if there are multiple signals on this VExport.
         */
        public boolean isBus()
        {
            return firstIndex >= 0;
        }
    };

    /**
     * ******** Instances *********************************************
     */
    /**
     * Class to define an instance or transistor, found inside of a Verilog module.
     */
    public static class VInstance
    {
        private VModule module;
        private PrimitiveNode.Function fun;
        private String instanceName;
        private Map<VPort, String[]> ports;
        private String[] verilogAssignInputs;

        /**
         * Constructor for a cell instance.
         *
         * @param module the parent module.
         * @param instanceName the name of the cell instance.
         */
        public VInstance(VModule module, String instanceName)
        {
            this.module = module;
            this.fun = null;
            this.instanceName = instanceName;
            ports = new HashMap<VPort, String[]>();
        }

        /**
         * Constructor for a transistor.
         *
         * @param fun the transistor type.
         * @param instanceName the name of the transistor instance.
         */
        public VInstance(PrimitiveNode.Function fun, String instanceName)
        {
            this.module = null;
            this.fun = fun;
            this.instanceName = instanceName;
            ports = new HashMap<VPort, String[]>();
        }

        /**
         * Constructor for a Verilog assign.
         *
         * @param fun the transistor type.
         * @param verilogAssignInputs names of wires used in assigns right-hand sidde expresssion.
         */
        public VInstance(String instanceName, String[] verilogAssignInputs)
        {
            this.module = null;
            this.fun = PrimitiveNode.Function.GATEAND;
            this.instanceName = instanceName;
            ports = new HashMap<VPort, String[]>();
            this.verilogAssignInputs = verilogAssignInputs;
        }

        /**
         * Method to add a new port on this VInstance.
         *
         * @param lp the local port ("actual port") found on the instance.
         * @param signalNames a list of signal names for that port.
         */
        public void addConnection(VPort lp, String[] signalNames)
        {
            ports.put(lp, signalNames);
        }

        /**
         * Method to return the sub-module that defines this instance.
         *
         * @return a sub-VModule that is the prototype of this VInstance.
         * (null if this is a transistor).
         */
        public VModule getModule()
        {
            return module;
        }

        /**
         * Method to return the transistor function that defines this instance.
         *
         * @return a PrimitiveNode.Function description of the transistor.
         * (null if this is a cell instance).
         */
        public PrimitiveNode.Function getFunction()
        {
            return fun;
        }
    }

    /**
     * ******** Ports on Instances *********************************
     */
    /**
     * Class to define a port on a VInstance.
     */
    public static class VPort
    {
        /**
         * Instance
         */
        private VInstance in;
        /**
         * name of port
         */
        private String portName;
        /**
         * true if part of a bus
         */
        private boolean onBus;

        public VPort(VInstance in, String portName, boolean onBus)
        {
            this.in = in;
            this.portName = portName;
            this.onBus = onBus;
        }
    }

    /**
     * The constructor compiles the Verilog in disk file.
     *
     * @param f the disk file.
     * @param verbose true to give progress while compiling.
     */
    public CompileVerilogStruct(File f, boolean verbose, ErrorLogger logger)
    {
        this.verbose = verbose;
        try
        {
            InputStreamReader is = new InputStreamReader(new FileInputStream(f));
            LineNumberReader lineReader = new LineNumberReader(is);
            List<String> stringList = new ArrayList<String>();
            for (;;)
            {
                String line = lineReader.readLine();
                if (line == null)
                    break;
                stringList.add(line);
            }
            lineReader.close();
            String[] strings = new String[stringList.size()];
            for (int i = 0; i < stringList.size(); i++)
                strings[i] = stringList.get(i);
            processVerilog(strings, logger);
        } catch (IOException e)
        {
            System.out.println("Error reading file: " + e.getMessage());
            hasErrors = true;
            return;
        }
    }

    /**
     * The constructor compiles the Verilog in a Verilog-view Cell.
     *
     * @param verilogCell the Cell with the Verilog text.
     * @param verbose true to give progress while compiling.
     */
    public CompileVerilogStruct(Cell verilogCell, boolean verbose)
    {
        this.verbose = verbose;
        String[] strings = verilogCell.getTextViewContents();
        if (strings == null)
        {
            System.out.println("Cell " + verilogCell.describe(true) + " has no text in it");
            return;
        }
        processVerilog(strings, null);
    }

    /**
     * The constructor compiles the Verilog in an array of Strings.
     *
     * @param strings the Verilog text.
     * @param verbose true to give progress while compiling.
     */
    public CompileVerilogStruct(String[] strings, boolean verbose)
    {
        this.verbose = verbose;
        processVerilog(strings, null);
    }

    /**
     * The constructor compiles ACL2 Verilog in a Verilog-view Cell.
     *
     * @param saoFile the serialized ACL2 file
     */
    public CompileVerilogStruct(File saoFile)
    {
        try
        {
            ACL2Object.initHonsMananger(saoFile.getName());
            ACL2Reader sr = new ACL2Reader(saoFile);
            DesignExt design = new DesignExt(sr.root);
            Map<ModName, VModule> modulesByModName = new HashMap<>();
            allModules = new ArrayList<>();
            for (Map.Entry<ModName, ModuleExt> e : design.downTop.entrySet())
            {
                ModName modName = e.getKey();
                ModuleExt m = e.getValue();
                VModule vModule;
                if (modName.isString())
                {
                    vModule = new VModule(modName.toString(), true, false);
                } else if (modName.isGate())
                {
                    vModule = new VModule(modName.toString(), true, true);
                } else
                {
                    continue;
                }
                for (WireExt wire : m.wires)
                {
                    if (wire.isExport())
                    {
                        VExport vExport = new VExport(wire.getName().toString());
                        if (wire.isAssigned())
                        {
                            vExport.mode = MODE_OUT;
                        } else if (wire.used)
                        {
                            vExport.mode = MODE_IN;
                        }
                        int firstIndex = wire.getFirstIndex();
                        int secondIndex = wire.getSecondIndex();
                        if (firstIndex != 0 || secondIndex != 0)
                        {
                            vExport.firstIndex = firstIndex;
                            vExport.secondIndex = secondIndex;
                        }
                        vModule.ports.add(vExport);
                    } else
                    {
                        vModule.wires.add(wire.toLispString(wire.getWidth(), 0));
                    }
                }
                Map<Name, VInstance> instances = new HashMap<>();
                for (ModInstExt inst : m.insts)
                {
                    VModule module = modulesByModName.get(inst.getModname());
                    if (module != null)
                    {
                        String instanceName = inst.getInstname().toString();
                        VInstance vInstance = new VInstance(module, instanceName);
                        vModule.instances.add(vInstance);
                        VInstance old = instances.put(inst.getInstname(), vInstance);
                        assert old == null;
                    }
                }
                for (Map.Entry<Lhs<PathExt>, DriverExt> e1 : m.assigns.entrySet())
                {
                    Lhs<PathExt> lhs = e1.getKey();
                    DriverExt rhs = e1.getValue();
                    String instanceName = lhs.toString();
                    instanceName = null;
                    Map<Svar<PathExt>, BigInteger> inputs = rhs.getOrigSvex().collectVarsWithMasks(BigIntegerUtil.logheadMask(lhs.width()), true);
                    String[] assignInputs = new String[inputs.size()];
                    int i = 0;
                    for (Map.Entry<Svar<PathExt>, BigInteger> e2 : inputs.entrySet())
                    {
                        WireExt lw = (WireExt)e2.getKey().getName();
                        BigInteger mask = e2.getValue();
                        assignInputs[i++] = lw.toString(mask);
                    }
                    VInstance vInstance = new VInstance(instanceName, assignInputs);
                    vModule.instances.add(vInstance);
                    String portName = "y";
                    String[] signals = new String[]
                    {
                        toElectricString(lhs)
                    };
                    VPort vPort = new VPort(vInstance, portName, lhs.width() > 1);
                    assert !vInstance.ports.containsKey(vPort);
                    vInstance.addConnection(vPort, signals);
                }
                for (Map.Entry<Lhs<PathExt>, Lhs<PathExt>> e1 : m.aliaspairs.entrySet())
                {
                    Lhs<PathExt> lhs = e1.getKey();
                    Lhs<PathExt> rhs = e1.getValue();
                    assert lhs.ranges.size() == 1;
                    if (!(lhs.ranges.get(0).getVar().getName() instanceof PathExt.PortInst))
                    {
                        continue;
                    }
                    PathExt.PortInst pi = (PathExt.PortInst)lhs.ranges.get(0).getVar().getName();
                    VInstance vInstance = instances.get(pi.inst.getInstname());
                    if (vInstance == null)
                    {
                        continue;
                    }
                    String portName = pi.wire.toLispString(pi.wire.getWidth(), 0);
                    String[] signals = new String[rhs.ranges.size()];
                    for (int i = 0; i < rhs.ranges.size(); i++)
                    {
                        Lhrange<PathExt> lr = rhs.ranges.get(i);
                        signals[signals.length - i - 1] = toElectricString(lr);
                    }
                    VPort vPort = new VPort(vInstance, portName, signals.length > 1);
                    assert !vInstance.ports.containsKey(vPort);
                    vInstance.addConnection(vPort, signals);
                }
                modulesByModName.put(modName, vModule);
            }
        } catch (IOException e)
        {
            System.out.println("Error reading file: " + e.getMessage());
            hasErrors = true;
            return;
        } finally
        {
            ACL2Object.closeHonsManager();
        }
        processModules();
    }

    private static String toElectricString(Lhs<PathExt> lhs)
    {
        String s = "";
        for (int i = lhs.ranges.size() - 1; i >= 0; i--)
        {
            Lhrange<PathExt> lr = lhs.ranges.get(i);
            s += toElectricString(lr);
            if (i > 0)
            {
                s += ",";
            }
        }
        return s;
    }

    private static String toElectricString(Lhrange<PathExt> lr)
    {
        Svar<PathExt> name = lr.getVar();
        if (name == null)
        {
            throw new UnsupportedOperationException();
        }
        return name.toLispString(lr.getWidth(), lr.getRsh());
    }

    /**
     * Method to report the validity of the Verilog.
     *
     * @return true if the Verilog compile found errors.
     */
    public boolean hadErrors()
    {
        return hasErrors;
    }

    /**
     * Method to return a list of Verilog Modules.
     *
     * @return a List of VModule objects found in the Verilog.
     */
    public List<VModule> getModules()
    {
        return allModules;
    }

    private void processVerilog(String[] strings, ErrorLogger logger)
    {
        if (verbose)
        {
            Job.getUserInterface().startProgressDialog("Compiling Verilog", null);
            Job.getUserInterface().setProgressNote("Scanning...");
        }
        allModules = new ArrayList<VModule>();
        tList = new ArrayList<TokenList>();
        errorCount = 0;
        errorLogger = logger;
        hasParentLogger = logger != null;
        hasErrors = false;
        doScanner(strings);
        if (verbose)
        {
            Job.getUserInterface().setProgressNote("Parsing...");
            Job.getUserInterface().setProgressValue(0);
        }
        doParser();
        if (verbose)
            Job.getUserInterface().stopProgressDialog();

        processModules();

        if (errorLogger != null && !hasParentLogger)
            errorLogger.termLogging(true);
//		dumpData();
    }

    private void processModules()
    {
        // make network list
        for (VModule module : allModules)
        {
            // create dummy cell if it isn't defined
            if (!module.defined)
            {
                // find the cell
                for (Library lib : Library.getVisibleLibraries())
                {
                    for (Iterator<Cell> it = lib.getCells(); it.hasNext();)
                    {
                        Cell libCell = it.next();
                        if (libCell.getName().equals(module.name))
                        {
                            module.cell = libCell.otherView(View.LAYOUT);
                            if (module.cell == null)
                            {
                                module.cell = libCell;
                                break;
                            }
                        }
                    }
                    if (module.cell != null)
                        break;
                }
            }

            // remove assigned wire names
            for (int i = 0; i < module.wires.size(); i++)
            {
                String wire = module.wires.get(i);
                String assignedName = module.assignments.get(wire);
                if (assignedName != null)
                {
                    module.wires.remove(i);
                    i--;
                }
            }

            for (VInstance in : module.instances)
            {
                for (VPort lp : in.ports.keySet())
                {
                    String[] signalNames = in.ports.get(lp);
                    for (int i = 0; i < signalNames.length; i++)
                    {
                        String assignedName = module.assignments.get(signalNames[i]);
                        if (assignedName == null)
                            assignedName = signalNames[i];
                        List<VPort> portsOnNet = module.allNetworks.get(assignedName);
                        if (portsOnNet == null)
                            module.allNetworks.put(assignedName, portsOnNet = new ArrayList<VPort>());
                        portsOnNet.add(lp);
                    }
                }
            }
        }
    }

//	private void dumpData()
//	{
//		// write what was found
//		for(VModule vm : allModules)
//		{
//			System.out.println();
//			System.out.print("++++ MODULE "+vm.name+"(");
//			boolean first = true;
//			for(VExport fp : vm.ports)
//			{
//				if (!first) System.out.print(", ");
//				first = false;
//				System.out.print(fp.name);
//				if (fp.firstIndex != -1 && fp.secondIndex != -1)
//					System.out.print("[" + fp.firstIndex + ":" + fp.secondIndex + "]");
//				switch (fp.mode)
//				{
//					case MODE_IN:    System.out.print(" input");   break;
//					case MODE_OUT:   System.out.print(" output");  break;
//					case MODE_INOUT: System.out.print(" inout");   break;
//				}
//			}
//			System.out.println(")");
//			if (!vm.defined)
//			{
//				if (vm.cell == null) System.out.println("     CELL NOT FOUND"); else
//					System.out.println("     CELL IS "+vm.cell.describe(false));
//			}
//			for(VInstance in : vm.instances)
//			{
//				System.out.print("     INSTANCE "+in.instanceName+" OF CELL "+in.module.name+"(");
//				first = true;
//				for(VPort lp : in.ports.keySet())
//				{
//					if (!first) System.out.print(", ");
//					first = false;
//					String[] netNames = in.ports.get(lp);
//					if (netNames.length == 1) System.out.print(lp.portName+"="+netNames[0]); else
//					{
//						System.out.print("[");
//						for(int i=0; i<netNames.length; i++)
//						{
//							if (i != 0) System.out.print(",");
//							System.out.print(netNames[i]);
//						}
//						System.out.print("]");
//					}
//				}
//				System.out.println(")");
//			}
//			for(String netName : vm.allNetworks.keySet())
//			{
//				System.out.print("     NETWORK " + netName + " ON");
//				List<VPort> ports = vm.allNetworks.get(netName);
//				for(VPort lp : ports)
//					System.out.print(" " + lp.in.instanceName+":"+lp.portName);
//				System.out.println();
//			}
//		}
//	}
    /**
     * Method to report whether the Verilog compile was successful.
     *
     * @return true if there were errors.
     */
    public boolean hasErrors()
    {
        return hasErrors;
    }

    ;

	/******************************** THE VERILOG SCANNER ********************************/

	private static class TokenType
    {
        private String name, str;

        private TokenType(String name, String str)
        {
            this.name = name;
            this.str = str;
        }

        public String getName()
        {
            return name;
        }

        public String getChar()
        {
            return str;
        }

        public static final TokenType LEFTPAREN = new TokenType("Left Parenthesis", "(");
        public static final TokenType RIGHTPAREN = new TokenType("Right Parenthesis", ")");
        public static final TokenType LEFTBRACKET = new TokenType("Left Bracket", "[");
        public static final TokenType RIGHTBRACKET = new TokenType("Right Bracket", "]");
        public static final TokenType LEFTBRACE = new TokenType("Left Brace", "{");
        public static final TokenType RIGHTBRACE = new TokenType("Right Brace", "}");
        public static final TokenType SLASH = new TokenType("Forward Slash", "/");
        public static final TokenType COMMA = new TokenType("Comma", ",");
        public static final TokenType MINUS = new TokenType("Minus", "-");
        public static final TokenType AMPERSAND = new TokenType("Ampersand", "&");
        public static final TokenType VERTICALBAR = new TokenType("Vertical Bar", "|");
        public static final TokenType PERIOD = new TokenType("Period", ".");
        public static final TokenType APOSTROPHE = new TokenType("Apostrophe", "'");
        public static final TokenType QUESTION = new TokenType("Question", "?");
        public static final TokenType HASH = new TokenType("Hash", "#");
        public static final TokenType COLON = new TokenType("Colon", ":");
        public static final TokenType SEMICOLON = new TokenType("Semicolon", ";");
        public static final TokenType ATSIGN = new TokenType("At Sign", "@");
        public static final TokenType EQUALS = new TokenType("Equals", "=");
        public static final TokenType DOUBLEDOT = new TokenType("DotDot", "..");
        public static final TokenType VARASSIGN = new TokenType("Assign", "=>");
        public static final TokenType UNKNOWN = new TokenType("Unknown", "");
        public static final TokenType IDENTIFIER = new TokenType("Identifier", "");
        public static final TokenType KEYWORD = new TokenType("Keyword", "");
        public static final TokenType DECIMAL = new TokenType("Decimal Number", "");
        public static final TokenType BITS = new TokenType("Bit Sequence", "");
        public static final TokenType CHAR = new TokenType("Character", "");
        public static final TokenType STRING = new TokenType("String", "");
        public static final TokenType TILDE = new TokenType("TILDE", "~");
    }

    private class TokenList
    {
        /**
         * token number
         */
        private TokenType type;
        /**
         * NULL if delimiter,
         * pointer to global name space if identifier,
         * pointer to keyword table if keyword,
         * pointer to string if decimal literal,
         * pointer to string if based literal,
         * value of character if character literal,
         * pointer to string if string literal,
         * pointer to string if bit string literal
         */
        private Object pointer;
        /**
         * TRUE if space before next token
         */
        private boolean space;
        /**
         * line number token occurred
         */
        private int lineNum;

        private TokenList(TokenType type, Object pointer, int lineNum, boolean space)
        {
            this.type = type;
            this.pointer = pointer;
            this.lineNum = lineNum;
            this.space = true;
            tList.add(this);
        }

        public int makeMessageLine(StringBuffer buffer)
        {
            int index = tList.indexOf(this);
            int lineNumber = this.lineNum;

            // back up to start of line
            while (index > 0 && tList.get(index - 1).lineNum == lineNumber)
                index--;

            // form line in buffer
            int pointer = 0;
            for (int i = index; i < tList.size(); i++)
            {
                TokenList tok = tList.get(i);
                if (tok.lineNum != lineNumber)
                    break;
                if (tok == this)
                    pointer = buffer.length();
                buffer.append(tok.toString());
                if (tok.space)
                    buffer.append(" ");
            }
            return pointer;
        }

        public String toString()
        {
            if (type == TokenType.STRING)
                return "\"" + pointer + "\" ";
            if (type == TokenType.KEYWORD)
                return ((VKeyword)pointer).name;
            if (type == TokenType.DECIMAL)
                return (String)pointer;
            if (type == TokenType.BITS)
                return (String)pointer;
            if (type == TokenType.CHAR)
                return ((Character)pointer).charValue() + "";
            if (type == TokenType.IDENTIFIER)
            {
                if (pointer == null)
                    return "NULL";
                return pointer.toString();
            }
            return type.getChar();
        }
    }

    private void resetTokenListPointer()
    {
        tokenIndex = 0;
    }

    private TokenList getNextToken()
    {
        if (tokenIndex >= tList.size())
            return null;
        TokenList token = tList.get(tokenIndex++);
        return token;
    }

    private TokenList peekNextToken()
    {
        if (tokenIndex >= tList.size())
            return null;
        return tList.get(tokenIndex);
    }

    private TokenType getTokenType(TokenList token)
    {
        if (token == null)
            return TokenType.UNKNOWN;
        return token.type;
    }

    private TokenList needNextToken(TokenType type)
    {
        TokenList token = getNextToken();
        if (token == null)
        {
            reportErrorMsg(null, "End of file encountered");
            return null;
        }
        if (token.type != type)
        {
            reportErrorMsg(token, "Expecting a " + type.getName());
            parseToSemicolon();
            return null;
        }
        return token;
    }

    /**
     * ******** Keywords *****************************************
     */
    private static class VKeyword
    {
        /**
         * string defining keyword
         */
        private String name;
        private static List<VKeyword> theKeywords = new ArrayList<VKeyword>();

        VKeyword(String name)
        {
            this.name = name;
            theKeywords.add(this);
        }

        public static VKeyword findKeyword(String tString)
        {
            for (VKeyword vk : theKeywords)
            {
                if (vk.name.equals(tString))
                    return vk;
            }
            return null;
        }

        public static final VKeyword ALWAYS = new VKeyword("always");
        public static final VKeyword ANALOG = new VKeyword("analog");
        public static final VKeyword ASSIGN = new VKeyword("assign");
        public static final VKeyword BEGIN = new VKeyword("begin");
        public static final VKeyword ELECTRICAL = new VKeyword("electrical");
        public static final VKeyword ELSE = new VKeyword("else");
        public static final VKeyword END = new VKeyword("end");
        public static final VKeyword ENDMODULE = new VKeyword("endmodule");
        public static final VKeyword ENDPRIMITIVE = new VKeyword("endprimitive");
        public static final VKeyword ENDSPECIFY = new VKeyword("endspecify");
        public static final VKeyword ENDTABLE = new VKeyword("endtable");
        public static final VKeyword IF = new VKeyword("if");
        public static final VKeyword INITIAL = new VKeyword("initial");
        public static final VKeyword INOUT = new VKeyword("inout");
        public static final VKeyword INPUT = new VKeyword("input");
        public static final VKeyword LOGIC = new VKeyword("logic");
        public static final VKeyword MODULE = new VKeyword("module");
        public static final VKeyword OUTPUT = new VKeyword("output");
        public static final VKeyword PARAMETER = new VKeyword("parameter");
        public static final VKeyword PRIMITIVE = new VKeyword("primitive");
        public static final VKeyword REAL = new VKeyword("real");
        public static final VKeyword REG = new VKeyword("reg");
        public static final VKeyword SPECIFY = new VKeyword("specify");
        public static final VKeyword SUPPLY = new VKeyword("supply");
        public static final VKeyword SUPPLY0 = new VKeyword("supply0");
        public static final VKeyword SUPPLY1 = new VKeyword("supply1");
        public static final VKeyword TABLE = new VKeyword("table");
        public static final VKeyword TRANIF0 = new VKeyword("tranif0");
        public static final VKeyword TRANIF1 = new VKeyword("tranif1");
        public static final VKeyword WIRE = new VKeyword("wire");
    };

    /**
     * Method to do lexical scanning of input Verilog and create token list.
     */
    private void doScanner(String[] strings)
    {
        String buf = "";
        int bufPos = 0;
        int lineNum = 0;
        boolean space = false;
        for (;;)
        {
            if (bufPos >= buf.length())
            {
                if (lineNum >= strings.length)
                    return;
                buf = strings[lineNum++];
                if (verbose && (lineNum % 100) == 0)
                    Job.getUserInterface().setProgressValue(lineNum * 100 / strings.length);
                bufPos = 0;
                space = true;
            } else
            {
                if (Character.isWhitespace(buf.charAt(bufPos)))
                    space = true;
                else
                    space = false;
            }
            while (bufPos < buf.length() && Character.isWhitespace(buf.charAt(bufPos)))
                bufPos++;
            if (bufPos >= buf.length())
                continue;
            char c = buf.charAt(bufPos);
            if (Character.isLetter(c))
            {
                // could be identifier (keyword) or bit string literal
                int end = bufPos;
                for (; end < buf.length(); end++)
                {
                    char eChar = buf.charAt(end);
                    if (!Character.isLetterOrDigit(eChar) && eChar != '_')
                        break;
                }

                // got alphanumeric from c to end - 1
                VKeyword key = VKeyword.findKeyword(buf.substring(bufPos, end));
                if (key != null)
                {
                    new TokenList(TokenType.KEYWORD, key, lineNum, space);
                } else
                {
                    String ident = buf.substring(bufPos, end);
                    new TokenList(TokenType.IDENTIFIER, ident, lineNum, space);
                }
                bufPos = end;
            } else if (TextUtils.isDigit(c))
            {
                // could be decimal or based literal
                int end = bufPos + 1;
                for (; end < buf.length(); end++)
                {
                    char eChar = buf.charAt(end);
                    if (!TextUtils.isDigit(eChar) && eChar != '_')
                        break;
                }

                // got numeric from c to end - 1
                new TokenList(TokenType.DECIMAL, buf.substring(bufPos, end), lineNum, space);
                bufPos = end;
            } else
            {
                switch (c)
                {
                    case '\\':
                        // backslash starts a quoted identifier
                        int end = bufPos + 1;
                        while (end < buf.length() && buf.charAt(end) != '\n')
                        {
                            if (Character.isWhitespace(buf.charAt(end)))
                                break;
                            end++;
                        }
                        // identifier from c + 1 to end - 1
                        String ident = buf.substring(bufPos + 1, end);
                        new TokenList(TokenType.IDENTIFIER, ident, lineNum, space);
                        bufPos = end;
                        break;
                    case '/':
                        // got a slash...look for "//" comment
                        end = bufPos + 1;
                        if (end < buf.length() && buf.charAt(end) == '/')
                        {
                            // single-line comment: skip to end of line
                            while (end < buf.length() && buf.charAt(end) != '\n')
                                end++;
                            if (end < buf.length() && buf.charAt(end) == '\n')
                                end++;
                            bufPos = end;
                            break;
                        }
                        if (end < buf.length() && buf.charAt(end) == '*')
                        {
                            // multi-line comment: skip to terminator
                            bufPos = end + 1;
                            for (;;)
                            {
                                if (bufPos < buf.length() - 1 && buf.charAt(bufPos) == '*' && buf.charAt(bufPos + 1) == '/')
                                {
                                    bufPos += 2;
                                    break;
                                }
                                bufPos++;
                                if (bufPos >= buf.length() - 1)
                                {
                                    if (lineNum >= strings.length)
                                        return;
                                    buf = strings[lineNum++];
                                    if (verbose && (lineNum % 100) == 0)
                                        Job.getUserInterface().setProgressValue(lineNum * 100 / strings.length);
                                    bufPos = 0;
                                    space = true;
                                }
                            }
                            break;
                        }

                        // not a comment: put the token back
                        new TokenList(TokenType.SLASH, null, lineNum, space);
                        bufPos++;
                        break;
                    case '"':
                        // got a start of a string
                        end = bufPos + 1;
                        while (end < buf.length() && buf.charAt(end) != '\n')
                        {
                            if (buf.charAt(end) == '"')
                            {
                                if (end + 1 < buf.length() && buf.charAt(end + 1) == '"')
                                    end++;
                                else
                                    break;
                            }
                            end++;
                        }
                        // string from c + 1 to end - 1
                        String newString = buf.substring(bufPos + 1, end);
                        newString.replaceAll("\"\"", "\"");
                        new TokenList(TokenType.STRING, newString, lineNum, space);
                        if (buf.charAt(end) == '"')
                            end++;
                        bufPos = end;
                        break;
                    case '`':
                        // single-line command: skip to end of line
                        end = bufPos + 1;
                        while (end < buf.length() && buf.charAt(end) != '\n')
                            end++;
                        if (end < buf.length() && buf.charAt(end) == '\n')
                            end++;
                        bufPos = end;
                        break;
                    case '\'':
                        // character literal
                        if (bufPos + 2 < buf.length() && buf.charAt(bufPos + 2) == '\'')
                        {
                            new TokenList(TokenType.CHAR, new Character(buf.charAt(bufPos + 1)), lineNum, space);
                            bufPos += 3;
                        } else
                        {
                            if (tList.size() > 0 && bufPos < buf.length() - 1)
                            {
                                TokenList prevTL = tList.get(tList.size() - 1);
                                char nextC = buf.charAt(bufPos + 1);
                                if (prevTL.type == TokenType.DECIMAL && Character.toLowerCase(nextC) == 'b')
                                {
                                    StringBuffer sb = new StringBuffer();
                                    bufPos += 2;
                                    while (bufPos < buf.length())
                                    {
                                        char chr = Character.toLowerCase(buf.charAt(bufPos));
                                        if (chr != '0' && chr != '1' && chr != 'x' && chr != 'z')
                                            break;
                                        sb.append(chr);
                                        bufPos++;
                                    }
                                    int len = TextUtils.atoi(prevTL.toString());
                                    if (sb.length() < len)
                                    {
                                        int modulo = sb.length();
                                        if (modulo == 0)
                                        {
                                            reportErrorMsg(prevTL, "Zero-length bitstring");
                                            sb.append('0');
                                            modulo = 1;
                                        }
                                        for (int i = sb.length(); i < len; i++)
                                            sb.append(sb.charAt(i % modulo));
                                    }
                                    if (len > sb.length())
                                        sb.delete(len, sb.length());
                                    String bitSequence = sb.toString();
                                    tList.remove(tList.size() - 1);
                                    new TokenList(TokenType.BITS, bitSequence, lineNum, space);
                                    break;
                                }
                            }
                            new TokenList(TokenType.APOSTROPHE, null, lineNum, space);
                            bufPos++;
                        }
                        break;
                    case '(':
                        new TokenList(TokenType.LEFTPAREN, null, lineNum, space);
                        bufPos++;
                        break;
                    case ')':
                        new TokenList(TokenType.RIGHTPAREN, null, lineNum, space);
                        bufPos++;
                        break;
                    case '[':
                        new TokenList(TokenType.LEFTBRACKET, null, lineNum, space);
                        bufPos++;
                        break;
                    case ']':
                        new TokenList(TokenType.RIGHTBRACKET, null, lineNum, space);
                        bufPos++;
                        break;
                    case '{':
                        new TokenList(TokenType.LEFTBRACE, null, lineNum, space);
                        bufPos++;
                        break;
                    case '}':
                        new TokenList(TokenType.RIGHTBRACE, null, lineNum, space);
                        bufPos++;
                        break;
                    case ',':
                        new TokenList(TokenType.COMMA, null, lineNum, space);
                        bufPos++;
                        break;
                    case '?':
                        new TokenList(TokenType.QUESTION, null, lineNum, space);
                        bufPos++;
                        break;
                    case '&':
                        new TokenList(TokenType.AMPERSAND, null, lineNum, space);
                        bufPos++;
                        break;
                    case '|':
                        new TokenList(TokenType.VERTICALBAR, null, lineNum, space);
                        bufPos++;
                        break;
                    case '#':
                        new TokenList(TokenType.HASH, null, lineNum, space);
                        bufPos++;
                        break;
                    case '~':
                        new TokenList(TokenType.TILDE, null, lineNum, space);
                        bufPos++;
                        break;
                    case '@':
                        new TokenList(TokenType.ATSIGN, null, lineNum, space);
                        bufPos++;
                        break;
                    case '=':
                        new TokenList(TokenType.EQUALS, null, lineNum, space);
                        bufPos++;
                        break;
                    case '-':
                        if (bufPos + 1 < buf.length() && buf.charAt(bufPos + 1) == '-')
                        {
                            // got a comment, throw away rest of line
                            bufPos = buf.length();
                        } else
                        {
                            // got a minus sign
                            new TokenList(TokenType.MINUS, null, lineNum, space);
                            bufPos++;
                        }
                        break;
                    case '.':
                        // could be PERIOD or DOUBLEDOT
                        if (bufPos + 1 < buf.length() && buf.charAt(bufPos + 1) == '.')
                        {
                            new TokenList(TokenType.DOUBLEDOT, null, lineNum, space);
                            bufPos += 2;
                        } else
                        {
                            new TokenList(TokenType.PERIOD, null, lineNum, space);
                            bufPos++;
                        }
                        break;
                    case ':':
                        // could be COLON or VARASSIGN
                        if (bufPos + 1 < buf.length() && buf.charAt(bufPos + 1) == '=')
                        {
                            new TokenList(TokenType.VARASSIGN, null, lineNum, space);
                            bufPos += 2;
                        } else
                        {
                            new TokenList(TokenType.COLON, null, lineNum, space);
                            bufPos++;
                        }
                        break;
                    case ';':
                        new TokenList(TokenType.SEMICOLON, null, lineNum, space);
                        bufPos++;
                        break;
                    default:
                        new TokenList(TokenType.UNKNOWN, null, lineNum, space);
                        bufPos++;
                        break;
                }
            }
        }
    }

    /**
     * ****************************** THE VERILOG PARSER *******************************
     */
    /**
     * Method to parse the token list.
     * Reports on any syntax errors and create the required syntax trees.
     */
    private void doParser()
    {
        curModule = null;
        resetTokenListPointer();
        int tokenCount = 0;
        for (;;)
        {
            TokenList token = getNextToken();
            if (token == null)
                break;

            tokenCount++;
            if (verbose && (tokenCount % 100) == 0)
                Job.getUserInterface().setProgressValue(tokenIndex * 100 / tList.size());

            if (token.type == TokenType.KEYWORD)
            {
                VKeyword vk = (VKeyword)token.pointer;
                if (vk == VKeyword.MODULE || vk == VKeyword.PRIMITIVE)
                {
                    curModule = parseModule(vk == VKeyword.PRIMITIVE);
                    if (curModule == null)
                        reportErrorMsg(token, "module not found");
                    continue;
                }

                if (vk == VKeyword.ENDMODULE || vk == VKeyword.ENDPRIMITIVE)
                {
                    curModule = null;
                    continue;
                }

                if (vk == VKeyword.INPUT || vk == VKeyword.OUTPUT || vk == VKeyword.INOUT
                    || vk == VKeyword.WIRE || vk == VKeyword.SUPPLY || vk == VKeyword.SUPPLY0
                    || vk == VKeyword.SUPPLY1)
                {
                    parseDeclare(token);
                    continue;
                }

                if (vk == VKeyword.TRANIF0 || vk == VKeyword.TRANIF1)
                {
                    VInstance inst = parseGate(token, vk == VKeyword.TRANIF0 ? PrimitiveNode.Function.TRAPMOS : PrimitiveNode.Function.TRANMOS);
                    if (inst != null)
                        curModule.instances.add(inst);
                    continue;
                }

                if (vk == VKeyword.ASSIGN)
                {
                    parseAssign();
                    continue;
                }

                if (vk == VKeyword.LOGIC || vk == VKeyword.REAL
                    || vk == VKeyword.REG || vk == VKeyword.ELECTRICAL || vk == VKeyword.PARAMETER)
                {
                    parseToSemicolon();
                    continue;
                }

                if (vk == VKeyword.ANALOG || vk == VKeyword.INITIAL)
                {
                    ignoreNextStatement();
                    continue;
                }

                if (vk == VKeyword.TABLE)
                {
                    ignoreToKeyword(VKeyword.ENDTABLE);
                    continue;
                }

                if (vk == VKeyword.SPECIFY)
                {
                    ignoreToKeyword(VKeyword.ENDSPECIFY);
                    continue;
                }

                if (vk == VKeyword.ALWAYS)
                {
                    ignoreAlwaysStatement();
                    continue;
                }

                if (vk == VKeyword.BEGIN)
                {
                    ignoreToKeyword(VKeyword.END);
                    continue;
                }
                reportErrorMsg(token, "Unknown keyword");
            } else if (token.type == TokenType.IDENTIFIER)
            {
                // identifier: parse as an instance declaration
                if (curModule == null)
                {
                    reportErrorMsg(token, "Instance declaration is not inside a Module");
                    parseToSemicolon();
                    break;
                }
                VInstance inst = parseInstance(token);
                if (inst != null)
                    curModule.instances.add(inst);
            } else
            {
                reportErrorMsg(token, "Expecting an identifier");
                parseToSemicolon();
            }
        }

        // fill in ports for modules that were not generated
        for (VModule module : allModules)
        {
            for (VInstance in : module.instances)
            {
                if (in.module == null)
                    continue;
                for (VPort lp : in.ports.keySet())
                {
                    boolean found = false;
                    for (VExport subPort : in.module.ports)
                    {
                        if (subPort.name.equals(lp.portName))
                        {
                            found = true;
                            break;
                        }
                    }
                    if (!found)
                    {
                        VExport fp = new VExport(lp.portName);
                        in.module.ports.add(fp);
                    }
                }
            }
        }
    }

    /**
     * Method to parse a module description of the form:
     * module IDENTIFIER (FORMAL_PORT_LIST);
     * FORMAL_PORT_LIST ::= [IDENTIFIER {, IDENTIFIER}]
     */
    private VModule parseModule(boolean primitive)
    {
        // check for entity IDENTIFIER
        TokenList token = needNextToken(TokenType.IDENTIFIER);
        if (token == null)
            return null;
        String name = (String)token.pointer;
        VModule module = findModule(name);
        if (module == null)
            module = new VModule(name, true, primitive);
        else
        {
            if (module.defined)
            {
                reportWarningMsg(token, "Module already defined but redefined with new information");
            }
            module.defined = true;
            module.primitive = primitive;
        }

        // check for opening bracket of FORMAL_PORT_LIST
        token = needNextToken(TokenType.LEFTPAREN);
        if (token == null)
            return null;

        // gather FORMAL_PORT_LIST
        for (;;)
        {
            token = needNextToken(TokenType.IDENTIFIER);
            if (token == null)
                return null;
            VExport port = new VExport((String)token.pointer);
            module.ports.add(port);

            token = getNextToken();
            if (getTokenType(token) != TokenType.COMMA)
                break;
        }

        // check for closing bracket of FORMAL_PORT_LIST
        if (getTokenType(token) != TokenType.RIGHTPAREN)
        {
            reportErrorMsg(token, "Expecting a right parenthesis");
            parseToSemicolon();
            return null;
        }

        // check for SEMICOLON
        token = needNextToken(TokenType.SEMICOLON);
        if (token == null)
            return null;

        return module;
    }

    private void parseDeclare(TokenList declareToken)
    {
        if (curModule == null)
        {
            reportErrorMsg(declareToken, "Not in a module");
            parseToSemicolon();
            return;
        }
        int mode = MODE_IN;
        VKeyword vk = (VKeyword)declareToken.pointer;
        if (vk == VKeyword.OUTPUT)
            mode = MODE_OUT;
        else if (vk == VKeyword.INOUT)
            mode = MODE_INOUT;
        TokenList token = getNextToken();
        int firstRange = -1, secondRange = -1;
        if (getTokenType(token) == TokenType.LEFTBRACKET)
        {
            // a bus of bits
            token = getNextToken();
            firstRange = TextUtils.atoi((String)token.pointer);

            token = needNextToken(TokenType.COLON);
            if (token == null)
                return;

            token = getNextToken();
            secondRange = TextUtils.atoi((String)token.pointer);

            token = needNextToken(TokenType.RIGHTBRACKET);
            if (token == null)
                return;
            token = getNextToken();
        }

        // now get the list of identifiers
        for (;;)
        {
            if (getTokenType(token) != TokenType.IDENTIFIER)
            {
                reportErrorMsg(token, "Expected identifier");
                parseToSemicolon();
                return;
            }
            String idName = (String)token.pointer;
            boolean found = false;
            if (vk == VKeyword.WIRE || vk == VKeyword.SUPPLY || vk == VKeyword.SUPPLY0 || vk == VKeyword.SUPPLY1)
            {
                if (firstRange != -1 && secondRange != -1)
                {
                    if (firstRange > secondRange)
                    {
                        for (int i = firstRange; i >= secondRange; i--)
                        {
                            String realName = idName + "[" + i + "]";
                            if (curModule.wires.contains(realName))
                            {
                                reportErrorMsg(token, "Identifier " + realName + " defined twice");
                                parseToSemicolon();
                                return;
                            }
                            curModule.wires.add(realName);
                        }
                    } else
                    {
                        for (int i = firstRange; i <= secondRange; i++)
                        {
                            String realName = idName + "[" + i + "]";
                            if (curModule.wires.contains(realName))
                            {
                                reportErrorMsg(token, "Identifier " + realName + " defined twice");
                                parseToSemicolon();
                                return;
                            }
                            curModule.wires.add(realName);
                        }
                    }
                } else
                {
                    if (curModule.wires.contains(idName))
                    {
                        reportErrorMsg(token, "Identifier defined twice");
                        parseToSemicolon();
                        return;
                    }
                    curModule.wires.add(idName);
                }
            } else
            {
                for (VExport fp : curModule.ports)
                {
                    if (fp.name.equals(idName))
                    {
                        fp.mode = mode;
                        fp.firstIndex = firstRange;
                        fp.secondIndex = secondRange;
                        found = true;
                        break;
                    }
                }
                if (!found)
                {
                    reportErrorMsg(token, "Unknown identifier");
                    parseToSemicolon();
                    return;
                }
            }

            // look for comma
            token = getNextToken();
            if (getTokenType(token) == TokenType.COMMA)
            {
                token = getNextToken();
                continue;
            }
            if (getTokenType(token) == TokenType.SEMICOLON)
                break;
            reportErrorMsg(token, "Unknown separator between identifiers");
            parseToSemicolon();
            return;
        }
    }

    private void parseAssign()
    {
        // get first identifier
        TokenList token = getNextToken();

        // check whether there is a delay statement before the identifier
        if (getTokenType(token) == TokenType.HASH)
        {
            // skip the delay value
            token = needNextToken(TokenType.DECIMAL);

            token = needNextToken(TokenType.IDENTIFIER);
        }

        String[] firstNames;
        if (getTokenType(token) == TokenType.LEFTBRACE)
        {
            List<String> strings = new ArrayList<String>();
            parseOpenBrace(strings);
            firstNames = new String[strings.size()];
            for (int i = 0; i < strings.size(); i++)
                firstNames[i] = strings.get(i);
        } else
        {
            if (getTokenType(token) != TokenType.IDENTIFIER && getTokenType(token) != TokenType.BITS)
                return;
            firstNames = getSignalNames(token);
        }

        // get equals sign
        token = needNextToken(TokenType.EQUALS);
        if (token == null)
            return;

        // get second identifier
        token = getNextToken();

        // ignore the tilde case
        if (getTokenType(token) == TokenType.TILDE)
            token = getNextToken();

        // ignore stuff in parentheses
        if (getTokenType(token) == TokenType.LEFTPAREN)
        {
            for (;;)
            {
                token = getNextToken();
                if (getTokenType(token) == TokenType.RIGHTPAREN)
                    break;
            }
            token = needNextToken(TokenType.SEMICOLON);
            return; // skipping
        }

        // handle stuff inside braces
        String[] secondNames;
        if (getTokenType(token) == TokenType.LEFTBRACE)
        {
            List<String> strings = new ArrayList<String>();
            parseOpenBrace(strings);
            secondNames = new String[strings.size()];
            for (int i = 0; i < strings.size(); i++)
                secondNames[i] = strings.get(i);
        } else
        {
            if (getTokenType(token) != TokenType.IDENTIFIER && getTokenType(token) != TokenType.BITS)
                return;
            secondNames = getSignalNames(token);
        }

        // get semicolon
        token = needNextToken(TokenType.SEMICOLON);
        if (token == null)
            return;

        // if first name is longer, replicate second name
        if (firstNames.length > secondNames.length)
        {
            String[] newSecondNames = new String[firstNames.length];
            for (int i = 0; i < firstNames.length; i++)
                newSecondNames[i] = secondNames[i % secondNames.length];
            secondNames = newSecondNames;
        }

        // equate the symbols
        if (firstNames.length != secondNames.length)
        {
            reportErrorMsg(token, "Assigning unequal length busses (first part is " + firstNames.length
                + " long, second part is " + secondNames.length + " long)");
            return;
        }
        for (int i = 0; i < firstNames.length; i++)
        {
            curModule.assignments.put(secondNames[i], firstNames[i]);
        }
    }

    /**
     * Method to parse what follows an open brace.
     * Or it could be a repeat clause, such as {64{X}}.
     * This could be a list of objects, such as {A, B, C}.
     * And of course, there could be a repeat clause inside of the list.
     *
     * @param strings the list of strings found in the clause.
     */
    private void parseOpenBrace(List<String> strings)
    {
        TokenList token = getNextToken();

        // if there is a number inside the brace, it is a repeat clause
        if (getTokenType(token) == TokenType.DECIMAL)
        {
            if (getRepeatClause(strings, token))
                parseToSemicolon();
            return;
        }

        for (;;)
        {
            if (getTokenType(token) == TokenType.IDENTIFIER || getTokenType(token) == TokenType.BITS)
            {
                String[] parts = getSignalNames(token);
                for (int i = 0; i < parts.length; i++)
                    strings.add(parts[i]);
            } else if (getTokenType(token) == TokenType.LEFTBRACE)
            {
                // handle format repeat clause
                token = needNextToken(TokenType.DECIMAL);
                if (token == null)
                {
                    parseToSemicolon();
                    return;
                }
                if (getRepeatClause(strings, token))
                {
                    parseToSemicolon();
                    return;
                }
            } else
            {
                reportErrorMsg(token, "Unknown element in list");
                parseToSemicolon();
                return;
            }

            // see if there is more
            token = getNextToken();
            if (getTokenType(token) == TokenType.COMMA)
            {
                token = getNextToken();
                continue;
            }
            if (getTokenType(token) == TokenType.RIGHTBRACE)
                break;
            reportErrorMsg(token, "Unknown separator in list");
            parseToSemicolon();
            break;
        }
    }

    /**
     * Method to handle format {number{identifier}}
     *
     * @param strings the list of Strings to put the symbols into.
     * @return true if an error is found.
     */
    private boolean getRepeatClause(List<String> strings, TokenList token)
    {
        int len = TextUtils.atoi(token.toString());

        token = needNextToken(TokenType.LEFTBRACE);
        if (token == null)
            return true;

        token = getNextToken();
        if (getTokenType(token) == TokenType.IDENTIFIER || getTokenType(token) == TokenType.BITS)
        {
            String[] parts = getSignalNames(token);
            for (int j = 0; j < len; j++)
            {
                for (int i = 0; i < parts.length; i++)
                    strings.add(parts[i]);
            }
        }

        token = needNextToken(TokenType.RIGHTBRACE);
        if (token == null)
            return true;

        token = needNextToken(TokenType.RIGHTBRACE);
        if (token == null)
            return true;
        return false;
    }

    private VInstance parseInstance(TokenList token)
    {
        // get the cell name from the first token
        String cellName = (String)token.pointer;

        // may be parameter list #(...)
        token = getNextToken();
        if (getTokenType(token) == TokenType.HASH)
        {
            // ignore what is in parenthesis
            token = getNextToken();
            if (getTokenType(token) == TokenType.LEFTPAREN)
            {
                for (;;)
                {
                    token = getNextToken();
                    if (getTokenType(token) == TokenType.RIGHTPAREN)
                        break;
                }
            }
            token = getNextToken();
        }

        // get the instance name
        if (getTokenType(token) != TokenType.IDENTIFIER)
        {
            reportErrorMsg(token, "Expecting an instance name identifier");
            parseToSemicolon();
            return null;
        }
        String instanceName = (String)token.pointer;

        // must then be followed by an open parenthesis to start the argument list
        token = needNextToken(TokenType.LEFTPAREN);
        if (token == null)
            return null;

        VModule module = findModule(cellName);
        if (module == null)
            module = new VModule(cellName, false, false);
        VInstance inst = new VInstance(module, instanceName);

        // get the arguments
        int argNum = 1;
        for (;;)
        {
            token = getNextToken();
            if (getTokenType(token) == TokenType.RIGHTPAREN)
                break;

            // guess at the name of the next port
            String portName = "ARG" + argNum;
            argNum++;
            if (getTokenType(token) == TokenType.PERIOD)
            {
                token = needNextToken(TokenType.IDENTIFIER);
                if (token == null)
                    return null;
                portName = (String)token.pointer;

                token = needNextToken(TokenType.LEFTPAREN);
                if (token == null)
                    return null;

                // either an identifier or a group of identifiers enclosed in braces
                token = getNextToken();
                if (getTokenType(token) == TokenType.LEFTBRACE)
                {
                    List<String> signalNames = new ArrayList<String>();
                    parseOpenBrace(signalNames);
                    String[] sigNames = new String[signalNames.size()];
                    for (int i = 0; i < signalNames.size(); i++)
                        sigNames[i] = signalNames.get(i);
                    inst.addConnection(new VPort(inst, portName, true), sigNames);
                } else
                {
                    // single port
                    if (getTokenType(token) != TokenType.IDENTIFIER && getTokenType(token) != TokenType.BITS)
                    {
                        reportErrorMsg(token, "Expecting an identifier");
                        parseToSemicolon();
                        return null;
                    }
                    String[] sigNames = getSignalNames(token);
                    inst.addConnection(new VPort(inst, portName, sigNames.length > 1), sigNames);
                }
                token = needNextToken(TokenType.RIGHTPAREN);
                if (token == null)
                    return null;
            } else if (getTokenType(token) == TokenType.IDENTIFIER)
            {
                String[] sigNames = getSignalNames(token);
                inst.addConnection(new VPort(inst, portName, sigNames.length > 1), sigNames);
            } else
            {
                reportErrorMsg(token, "Unknown separator between identifiers");
                parseToSemicolon();
                return null;
            }
            token = getNextToken();
            if (getTokenType(token) == TokenType.RIGHTPAREN)
                break;
            if (getTokenType(token) != TokenType.COMMA)
            {
                reportErrorMsg(token, "Expecting a comma");
                parseToSemicolon();
                return null;
            }
        }

        // must end with a semicolon
        token = needNextToken(TokenType.SEMICOLON);
        if (token == null)
            return null;
        return inst;
    }

    private VInstance parseGate(TokenList declareToken, PrimitiveNode.Function fun)
    {
        if (curModule == null)
        {
            reportErrorMsg(declareToken, "Not in a module");
            parseToSemicolon();
            return null;
        }

        // get the instance name from the second token
        TokenList token = needNextToken(TokenType.IDENTIFIER);
        if (token == null)
            return null;
        String instanceName = (String)token.pointer;

        // must then be followed by an open parenthesis to start the argument list
        token = needNextToken(TokenType.LEFTPAREN);
        if (token == null)
            return null;

        VInstance inst = new VInstance(fun, instanceName);

        // get the arguments
        int argNum = 1;
        for (;;)
        {
            token = getNextToken();
            if (getTokenType(token) == TokenType.RIGHTPAREN)
                break;

            // guess at the name of the next port
            String portName = null;
            switch (argNum++)
            {
                case 1:
                    portName = "s";
                    break;
                case 2:
                    portName = "d";
                    break;
                case 3:
                    portName = "g";
                    break;
            }
            if (getTokenType(token) == TokenType.IDENTIFIER)
            {
                String[] sigNames = getSignalNames(token);
                inst.addConnection(new VPort(inst, portName, sigNames.length > 1), sigNames);
            } else
            {
                reportErrorMsg(token, "Unknown separator between identifiers");
                parseToSemicolon();
                return null;
            }
            token = getNextToken();
            if (getTokenType(token) == TokenType.RIGHTPAREN)
                break;
            if (getTokenType(token) != TokenType.COMMA)
            {
                reportErrorMsg(token, "Expecting a comma");
                parseToSemicolon();
                return null;
            }
        }

        // must end with a semicolon
        token = needNextToken(TokenType.SEMICOLON);
        if (token == null)
            return null;
        return inst;
    }

    private String[] getSignalNames(TokenList token)
    {
        List<String> signalNames = new ArrayList<String>();
        if (getTokenType(token) == TokenType.BITS)
        {
            String bitString = token.toString();
            for (int i = 0; i < bitString.length(); i++)
            {
                if (bitString.charAt(i) == '0')
                    signalNames.add("gnd");
                else if (bitString.charAt(i) == '1')
                    signalNames.add("vdd");
                else
                    signalNames.add(bitString.charAt(i) + "");
            }
        } else if (getTokenType(token) == TokenType.IDENTIFIER)
        {
            String signalName = (String)token.pointer;

            // see if it is indexed
            TokenList next = peekNextToken();
            if (getTokenType(next) == TokenType.LEFTBRACKET)
            {
                // indexed signal
                getNextToken();
                TokenList index = needNextToken(TokenType.DECIMAL);
                if (index == null)
                    return new String[0];
                TokenList nxt = getNextToken();
                if (getTokenType(nxt) == TokenType.COLON)
                {
                    TokenList index2 = needNextToken(TokenType.DECIMAL);
                    if (index2 == null)
                        return new String[0];
                    TokenList cls = needNextToken(TokenType.RIGHTBRACKET);
                    if (cls == null)
                        return new String[0];
                    int startIndex = TextUtils.atoi((String)index.pointer);
                    int endIndex = TextUtils.atoi((String)index2.pointer);
                    if (startIndex < endIndex)
                    {
                        for (int i = startIndex; i <= endIndex; i++)
                            signalNames.add(signalName + "[" + i + "]");
                    } else
                    {
                        for (int i = startIndex; i >= endIndex; i--)
                            signalNames.add(signalName + "[" + i + "]");
                    }
                } else if (getTokenType(nxt) == TokenType.RIGHTBRACKET)
                {
                    signalNames.add(signalName + "[" + (String)index.pointer + "]");
                } else
                {
                    return new String[0];
                }
            } else
            {
                // see if the name is a bus
                boolean foundBus = false;
                for (String wire : curModule.wires)
                {
                    if (wire.startsWith(signalName) && wire.length() > signalName.length() && wire.charAt(signalName.length()) == '[')
                    {
                        signalNames.add(wire);
                        foundBus = true;
                    }
                }
                if (!foundBus)
                {
                    for (VExport fp : curModule.ports)
                    {
                        if (fp.name.equals(signalName))
                        {
                            if (fp.firstIndex < fp.secondIndex)
                            {
                                foundBus = true;
                                for (int i = fp.firstIndex; i <= fp.secondIndex; i++)
                                    signalNames.add(signalName + "[" + i + "]");
                            } else if (fp.firstIndex > fp.secondIndex)
                            {
                                foundBus = true;
                                for (int i = fp.firstIndex; i >= fp.secondIndex; i--)
                                    signalNames.add(signalName + "[" + i + "]");
                            }
                        }
                    }
                }
                if (!foundBus)
                    signalNames.add(signalName);
            }
        }
        String[] sigNames = new String[signalNames.size()];
        for (int i = 0; i < signalNames.size(); i++)
            sigNames[i] = signalNames.get(i);
        return sigNames;
    }

    private VModule findModule(String name)
    {
        for (VModule mod : allModules)
            if (mod.name.equals(name))
                return mod;
        return null;
    }

    /**
     * Ignore an "always" statement which has the form:
     * always @(XXXX) STATEMENT
     */
    private void ignoreAlwaysStatement()
    {
        // ignore the at sign
        TokenList token = needNextToken(TokenType.ATSIGN);
        if (token == null)
            return;

        // next scan the condition inside parentheses
        ignoreParentheticalClause();

        // now ignore the always statement
        ignoreNextStatement();
    }

    /**
     * Ignore an "if" statement which has the form:
     * if (XXXX) STATEMENT [else STATEMENT]
     */
    private void ignoreIfStatement()
    {
        // first scan the condition inside parentheses
        ignoreParentheticalClause();

        // now ignore the "then" statement and, optionally, the "else" statement
        ignoreNextStatement();
        TokenList token = peekNextToken();
        if (token.type == TokenType.KEYWORD && (VKeyword)token.pointer == VKeyword.ELSE)
        {
            getNextToken();
            ignoreNextStatement();
        }
    }

    /**
     * Method to ignore up to the next semicolon.
     */
    private void parseToSemicolon()
    {
        for (;;)
        {
            TokenList token = getNextToken();
            if (token == null)
                break;
            if (token.type == TokenType.SEMICOLON)
                break;
        }
    }

    /**
     * Ignore the next statement.
     * If the statement is "begin", parse to the "end".
     * If the statement is "if", parse it.
     */
    private void ignoreNextStatement()
    {
        for (;;)
        {
            TokenList token = getNextToken();
            if (token.type == TokenType.SEMICOLON)
                break;
            if (token.type != TokenType.KEYWORD)
                continue;
            VKeyword vk = (VKeyword)token.pointer;
            if (vk == VKeyword.BEGIN)
            {
                ignoreUntilEndOfStatement(VKeyword.END, 0);
                break;
            }
            if (vk == VKeyword.IF)
            {
                ignoreIfStatement();
                break;
            }
        }
    }

    /**
     * Method to ignore a clause in parentheses.
     */
    private void ignoreParentheticalClause()
    {
        int numParens = 0;
        for (;;)
        {
            TokenList token = getNextToken();
            if (token.type == TokenType.LEFTPAREN)
            {
                numParens++;
                continue;
            }
            if (token.type == TokenType.RIGHTPAREN)
            {
                numParens--;
                if (numParens > 0)
                    continue;
                break;
            }
        }
    }

    private void ignoreToKeyword(VKeyword keyword)
    {
        ignoreUntilEndOfStatement(keyword, 0);
    }

    /**
     * Method to ignore up to a given keyword.
     *
     * @param keyword the terminating keyword to find.
     * @param nestedLoop the level of "begin/end" nesting.
     */
    private void ignoreUntilEndOfStatement(VKeyword keyword, int nestedLoop)
    {
        for (;;)
        {
            TokenList token = getNextToken();
            if (token == null)
                return;
            if (token.type != TokenType.KEYWORD)
                continue;

            VKeyword vk = (VKeyword)token.pointer;
            if (vk == VKeyword.BEGIN)
            {
                // ignore the next nested loop
                ignoreUntilEndOfStatement(VKeyword.END, nestedLoop + 1);
                continue;
            }
            if (vk == keyword && nestedLoop == 0)
                return;
        }
    }

    private void reportWarningMsg(TokenList tList, String warnMsg)
    {
        if (tList == null)
        {
            String msg = "WARNING " + warnMsg;
            if (verbose)
                System.out.println(msg);
            else
            {
                if (errorLogger == null)
                    errorLogger = ErrorLogger.newInstance("Compile Verilog");
                errorLogger.logError(msg, 0);
            }
            return;
        }
        String msg = "WARNING on line " + tList.lineNum + ", " + warnMsg + ":";
        StringBuffer buffer = new StringBuffer();
        int pointer = tList.makeMessageLine(buffer);
        if (verbose)
        {
            System.out.println(msg);

            // print out line
            System.out.println(buffer.toString());

            // print out pointer
            buffer = new StringBuffer();
            for (int i = 0; i < pointer; i++)
                buffer.append(" ");
            System.out.println(buffer.toString() + "^");
        } else
        {
            if (errorLogger == null)
                errorLogger = ErrorLogger.newInstance("Compile Verilog");
            errorLogger.logWarning(msg + " " + buffer.toString(), null, 0);
        }
    }

    private void reportErrorMsg(TokenList tList, String errMsg)
    {
        hasErrors = true;
        errorCount++;
        if (errorCount == 30)
        {
            String msg = "TOO MANY ERRORS...LISTING NO MORE";
            if (verbose)
                System.out.println(msg);
            else
            {
                if (errorLogger == null)
                    errorLogger = ErrorLogger.newInstance("Compile Verilog");
                errorLogger.logError(msg, 0);
            }
        }
        if (errorCount >= 30)
            return;
        if (tList == null)
        {
            String msg = "ERROR " + errMsg;
            if (verbose)
                System.out.println(msg);
            else
            {
                if (errorLogger == null)
                    errorLogger = ErrorLogger.newInstance("Compile Verilog");
                errorLogger.logError(msg, 0);
            }
            return;
        }
        String msg = "ERROR on line " + tList.lineNum + ", " + errMsg + ":";
        StringBuffer buffer = new StringBuffer();
        int pointer = tList.makeMessageLine(buffer);
        if (verbose)
        {
            System.out.println(msg);

            // print out line
            System.out.println(buffer.toString());

            // print out pointer
            buffer = new StringBuffer();
            for (int i = 0; i < pointer; i++)
                buffer.append(" ");
            System.out.println(buffer.toString() + "^");
        } else
        {
            if (errorLogger == null)
                errorLogger = ErrorLogger.newInstance("Compile Verilog");
            errorLogger.logError(msg + " " + buffer.toString(), 0);
        }
    }

    /**
     * ****************************** THE RATS-NEST CELL GENERATOR *******************************
     */
    /**
     * Method to generate a cell that represents this netlist.
     *
     * @param destLib destination library.
     * @param schematic true to make schematics; false for layout
     */
    public Cell genCell(Library destLib, boolean schematic, EditingPreferences ep, IconParameters ip)
    {
        if (hasErrors())
            return null;
        Map<NodeProto, Map<PortProto, Point2D>> portLocMap = new HashMap<NodeProto, Map<PortProto, Point2D>>();

        if (verbose)
            Job.getUserInterface().startProgressDialog("Building Rats-Nest Cells", null);

        // first create the undefined Cells
        List<VModule> secondPass = new ArrayList<VModule>();
        for (VModule mod : allModules)
        {
            if (!mod.isDefined() && mod.cell == null)
            {
                System.out.println("Creating dummy cell for module " + mod.name);
                mod.cell = Cell.makeInstance(ep, destLib, mod.name + "{ic}");
                double YSPACING = 4;
                double yPos = YSPACING / 2;
                for (VExport port : mod.ports)
                {
                    String portName = port.getName();
                    if (port.isBus())
                        portName += "[" + port.firstIndex + ":" + port.secondIndex + "]";
                    PrimitiveNode pnp = Generic.tech().universalPinNode;
                    double width = pnp.getDefaultLambdaBaseWidth(ep);
                    double height = pnp.getDefaultLambdaBaseHeight(ep);
                    Point2D center = new Point2D.Double(-YSPACING, yPos);
                    yPos += YSPACING;
                    NodeInst ni = NodeInst.makeInstance(pnp, ep, center, width, height, mod.cell);
                    PortCharacteristic characteristic = PortCharacteristic.UNKNOWN;
                    switch (port.mode)
                    {
                        case MODE_IN:
                            characteristic = PortCharacteristic.IN;
                            break;
                        case MODE_OUT:
                            characteristic = PortCharacteristic.OUT;
                            break;
                        case MODE_INOUT:
                            characteristic = PortCharacteristic.BIDIR;
                            break;
                    }
                    Export e = Export.newInstance(mod.cell, ni.getOnlyPortInst(), portName, ep, characteristic);
                    TextDescriptor td = e.getTextDescriptor(Export.EXPORT_NAME).withPos(TextDescriptor.Position.LEFT);
                    e.setTextDescriptor(Export.EXPORT_NAME, td);
                }
                double xSize = YSPACING * 2;
                double ySize = yPos - YSPACING / 2;
                NodeInst bbNi = NodeInst.newInstance(Artwork.tech().openedThickerPolygonNode, ep, new Point2D.Double(0, ySize / 2), xSize, ySize, mod.cell);
                if (bbNi == null)
                    return null;
                EPoint[] boxOutline = new EPoint[5];
                boxOutline[0] = EPoint.fromLambda(-xSize / 2, 0);
                boxOutline[1] = EPoint.fromLambda(-xSize / 2, ySize);
                boxOutline[2] = EPoint.fromLambda(xSize / 2, ySize);
                boxOutline[3] = EPoint.fromLambda(xSize / 2, 0);
                boxOutline[4] = EPoint.fromLambda(-xSize / 2, 0);
                bbNi.setTrace(boxOutline);

                // put the original cell name on it
                TextDescriptor td = ep.getAnnotationTextDescriptor().withRelSize(ep.getIconGenBodyTextSize());
                bbNi.newVar(Schematics.SCHEM_FUNCTION, mod.getName(), td);
                continue;
            }
            secondPass.add(mod);
        }

        Cell cell = null;
        for (VModule mod : secondPass)
        {
            String cellName = mod.name + (schematic ? "{sch}" : "{lay}");
            System.out.println("Creating cell " + cellName);
            if (verbose)
            {
                Job.getUserInterface().setProgressNote("Creating Nodes in Cell " + cellName);
                Job.getUserInterface().setProgressValue(0);
            }
            cell = Cell.makeInstance(ep, destLib, cellName);
            if (cell == null)
            {
                if (verbose)
                    Job.getUserInterface().stopProgressDialog();
                return null;
            }

            // first place the instances
            double GAP = 15;
            double x = 0;
            double y = 0;
            double highest = 0;
            double totalSize = 0;
            for (VInstance in : mod.instances)
            {
                if (in.verilogAssignInputs != null)
                {
                    assert in.module == null;
                    PrimitiveNode andNP = Schematics.tech().andNode;
                    double width = andNP.getDefWidth(ep);
                    double height = andNP.getDefHeight(ep);
                    if (in.verilogAssignInputs.length > 2)
                    {
                        height += andNP.getAutoGrowth().getLambdaHeight() * (in.verilogAssignInputs.length - 2);
                    }
                    totalSize += (width + GAP) * (height + GAP);

                } else if (in.module == null)
                {
                    NodeProto tranNP = Schematics.tech().transistorNode;
                    double width = tranNP.getDefWidth(ep);
                    double height = tranNP.getDefHeight(ep);
                    totalSize += (width + GAP) * (height + GAP);
                } else
                {
                    Cell subCell = in.module.cell;
                    if (subCell == null)
                        continue;
                    double width = subCell.getDefWidth();
                    double height = subCell.getDefHeight();
                    totalSize += (width + GAP) * (height + GAP);
                }
            }
            double cellSize = Math.sqrt(totalSize);
            Map<VInstance, NodeInst> placed = new HashMap<VInstance, NodeInst>();
            int instancesPlaced = 0;
            for (VInstance in : mod.instances)
            {
                NodeInst ni = null;
                NodeProto np;
                double width = 0, height = 0;
                if (in.module != null)
                {
                    np = in.module.cell;
                    if (np != null)
                    {
                        width = np.getDefWidth(ep);
                        height = np.getDefHeight(ep);
                        ni = NodeInst.makeInstance(np, ep, EPoint.fromLambda(x, y), width, height, cell,
                            Orientation.IDENT, in.instanceName);
                    }
                } else if (in.verilogAssignInputs != null)
                {
                    np = Schematics.tech().andNode;
                    width = np.getDefWidth(ep);
                    height = np.getDefHeight(ep);
                    if (in.verilogAssignInputs.length > 2)
                    {
                        height += Schematics.tech().andNode.getAutoGrowth().getLambdaHeight() * (in.verilogAssignInputs.length - 2);
                    }
                    ni = NodeInst.makeInstance(np, ep, EPoint.fromLambda(x, y), width, height, cell, Orientation.IDENT, in.instanceName, in.fun);
                } else
                {
                    np = Schematics.tech().transistorNode;
                    width = np.getDefWidth(ep);
                    height = np.getDefHeight(ep);
                    ni = NodeInst.makeInstance(np, ep, EPoint.fromLambda(x, y), width, height, cell, Orientation.R, in.instanceName, in.fun);
                }
                if (ni == null)
                    continue;
                placed.put(in, ni);

                // cache port locations on this instance
                Map<PortProto, Point2D> portMap = portLocMap.get(np);
                if (portMap == null)
                {
                    portMap = new HashMap<PortProto, Point2D>();
                    portLocMap.put(np, portMap);
                    for (Iterator<PortInst> it = ni.getPortInsts(); it.hasNext();)
                    {
                        PortInst pi = it.next();
                        PortProto pp = pi.getPortProto();
                        EPoint ept = pi.getCenter();
                        Point2D pt = new Point2D.Double(ept.getX() - ni.getAnchorCenterX(), ept.getY() - ni.getAnchorCenterY());
                        portMap.put(pp, pt);
                    }
                }
                instancesPlaced++;
                if (verbose && (instancesPlaced % 100) == 0)
                    Job.getUserInterface().setProgressValue(instancesPlaced * 100 / mod.instances.size());

                // advance the placement
                x += width + GAP;
                highest = Math.max(highest, height);
                if (x >= cellSize)
                {
                    x = 0;
                    y += highest + GAP;
                    highest = 0;
                }
            }
            if (instancesPlaced > 0)
                System.out.println("Placed " + instancesPlaced + " instances");

            // now remove wires that do not make useful connections
            Netlist nl = cell.getNetlist();
            Set<VInstance> notFound = new HashSet<VInstance>();
            for (String netName : mod.allNetworks.keySet())
            {
                List<VPort> ports = mod.allNetworks.get(netName);
                Set<Network> used = new HashSet<Network>();
                for (int i = 0; i < ports.size(); i++)
                {
                    VPort lp = ports.get(i);
                    NodeInst ni = placed.get(lp.in);
                    if (ni == null)
                    {
                        hasErrors = true;
                        notFound.add(lp.in);
                        continue;
                    }
                    PortInst pi = findPortOnNode(ni, lp.portName);
                    if (pi == null)
                        return null;
                    if (getPortWidth(pi, nl) > 1)
                        continue;
                    Network net = nl.getNetwork(pi);
                    if (used.contains(net))
                    {
                        ports.remove(i);
                        i--;
                        continue;
                    }
                    used.add(net);
                }
            }

            // give errors about missing instances
            for (VInstance in : notFound)
            {
                System.out.println("ERROR: Cannot find instance " + in.instanceName + " of module " + in.module.name);
            }

            // now wire the instances
            if (verbose)
            {
                Job.getUserInterface().setProgressNote("Creating Arcs in Cell " + cellName);
                Job.getUserInterface().setProgressValue(0);
            }
            int total = 0;
            for (String netName : mod.allNetworks.keySet())
            {
                List<VPort> ports = mod.allNetworks.get(netName);
                for (int i = 1; i < ports.size(); i++)
                    total++;
            }

            // create stubs on instances where busses connect
            for (VInstance in : mod.instances)
            {
                if (in.verilogAssignInputs != null)
                {
                    NodeInst ni = placed.get(in);
                    PortInst pi = ni.findPortInst("a");
                    for (int i = 0; i < in.verilogAssignInputs.length; i++)
                    {
                        EPoint piLoc = EPoint.fromLambda(ni.getAnchorCenterX() - 4,
                            ni.getAnchorCenterY() + (0.5 * (in.verilogAssignInputs.length - 1) - i) * 4);
                        EPoint busPinLoc = EPoint.fromLambda(piLoc.getX() - 5, piLoc.getY());
                        PrimitiveNode np = Schematics.tech().busPinNode;
                        ArcProto ap = Schematics.tech().bus_arc;
                        String name = in.verilogAssignInputs[i];
                        if (!name.contains(",") && !name.contains(":"))
                        {
                            np = Schematics.tech().wirePinNode;
                            ap = Schematics.tech().wire_arc;
                        }
                        NodeInst stubPin = NodeInst.makeInstance(np, ep, busPinLoc, np.getDefWidth(ep), np.getDefHeight(ep), cell);
                        ArcInst.makeInstance(ap, ep, pi, stubPin.getOnlyPortInst(), piLoc, busPinLoc, name);
                    }
                    pi = ni.findPortInst("y");
                    EPoint piLoc = pi.getCenter();
                    EPoint busPinLoc = EPoint.fromLambda(piLoc.getLambdaX() + 5, piLoc.getLambdaY());
                    assert in.ports.size() == 1;
                    VPort vPort = in.ports.keySet().iterator().next();
                    String[] signals = in.ports.values().iterator().next();
                    assert signals.length == 1;
                    PrimitiveNode np = Schematics.tech().busPinNode;
                    ArcProto ap = Schematics.tech().bus_arc;
                    String name = signals[0];
                    if (!name.contains(",") && !name.contains(":"))
                    {
                        np = Schematics.tech().wirePinNode;
                        ap = Schematics.tech().wire_arc;
                    }
                    NodeInst stubPin = NodeInst.makeInstance(np, ep, busPinLoc, np.getDefWidth(ep), np.getDefHeight(ep), cell);
                    ArcInst.makeInstance(ap, ep, pi, stubPin.getOnlyPortInst(), piLoc, busPinLoc, name);
                }
                if (in.module == null)
                    continue;
                for (VPort lp : in.ports.keySet())
                {
                    String[] signals = in.ports.get(lp);
                    if (signals.length == 1)
                        continue;
                    boolean allScalar = true;
                    for (int i = 0; i < signals.length; i++)
                        if (signals[i].indexOf('[') >= 0)
                        {
                            allScalar = false;
                            break;
                        }
                    if (allScalar)
                        continue;
                    NodeInst ni = placed.get(in);
                    if (ni == null)
                        continue;
                    PortProto pp = null;
                    for (Iterator<PortProto> it = ni.getProto().getPorts(); it.hasNext();)
                    {
                        PortProto ppTry = it.next();
                        String tryName = ppTry.getName();
                        if (tryName.equals(lp.portName))
                        {
                            pp = ppTry;
                            break;
                        }
                        if (tryName.startsWith(lp.portName) && tryName.length() > lp.portName.length()
                            && tryName.charAt(lp.portName.length()) == '[')
                        {
                            pp = ppTry;
                            break;
                        }
                    }
                    if (pp == null)
                    {
                        System.out.println("Cannot find port " + lp.portName + " on cell " + ni.getProto().describe(false));
                        continue;
                    }
                    if (pp == null)
                        continue;
                    PortInst pi = ni.findPortInstFromEquivalentProto(pp);
                    EPoint piLoc = pi.getCenter();
                    double dX = 0, dY = 0;
                    double leftDist = Math.abs(piLoc.getX() - ni.getBounds().getMinX());
                    double rightDist = Math.abs(piLoc.getX() - ni.getBounds().getMaxX());
                    double downDist = Math.abs(piLoc.getY() - ni.getBounds().getMinY());
                    double upDist = Math.abs(piLoc.getY() - ni.getBounds().getMaxY());
                    double minDist = Math.min(Math.min(leftDist, rightDist), Math.min(upDist, downDist));
                    if (minDist == leftDist)
                        dX = -5;
                    else if (minDist == rightDist)
                        dX = 5;
                    else if (minDist == upDist)
                        dY = 5;
                    else
                        dY = -5;
                    EPoint busPinLoc = EPoint.fromLambda(piLoc.getX() + dX, piLoc.getY() + dY);
                    NodeProto np;
                    ArcProto ap;
                    String name;
                    if (signals.length == 1)
                    {
                        np = Schematics.tech().wirePinNode;
                        ap = Schematics.tech().wire_arc;
                        if (!pi.getPortProto().getBasePort().connectsTo(ap))
                        {
                            ap = pi.getPortProto().getBasePort().getConnections()[0];
                            np = ap.findPinProto();
                        }
                        name = signals[0];
                    } else
                    {
                        np = Schematics.tech().busPinNode;
                        ap = Schematics.tech().bus_arc;
                        name = makeBusName(signals);
                    }
                    NodeInst stubPin = NodeInst.makeInstance(np, ep, busPinLoc, np.getDefWidth(ep), np.getDefHeight(ep), cell);
                    ArcInst.makeInstance(ap, ep, pi, stubPin.getOnlyPortInst(), piLoc, busPinLoc, name);
                }
            }

            // create arcs
            int count = 0;
            Map<String, PortInst> portMap = new HashMap<String, PortInst>();
            for (String netName : mod.allNetworks.keySet())
            {
                List<VPort> ports = mod.allNetworks.get(netName);
                List<VPort> scalarPorts = new ArrayList<VPort>();
                for (VPort lp : ports)
                    if (!lp.onBus)
                        scalarPorts.add(lp);
                if (scalarPorts.size() == 1)
                {
                    VPort port = scalarPorts.get(0);
                    NodeInst ni = placed.get(port.in);
                    if (ni == null)
                        continue;
                    PortInst pi = findPortOnNode(ni, port.portName);
                    if (pi != null)
                        portMap.put(netName, pi);
                }
                if (scalarPorts.size() == 1)
                {
                    // if not connecting two instances, see if it connects to an export
                    boolean exported = false;
                    for (VExport e : mod.ports)
                    {
                        if (e.firstIndex == e.secondIndex)
                        {
                            if (netName.equals(e.getName()))
                            {
                                exported = true;
                                break;
                            }
                        } else
                        {
                            for (int i = Math.min(e.firstIndex, e.secondIndex); i <= Math.max(e.firstIndex, e.secondIndex); i++)
                            {
                                if (netName.equals(e.getName() + "[" + i + "]"))
                                {
                                    exported = true;
                                    break;
                                }
                            }
                        }
                    }
                    if (exported)
                    {
                        // make a stub so the network connects to the port
                        NodeProto np = Schematics.tech().wirePinNode;
                        ArcProto ap = Schematics.tech().wire_arc;
                        String name = netName;
                        VPort vPort = scalarPorts.get(0);
                        NodeInst ni = placed.get(vPort.in);
                        PortInst pi = findPortOnNode(ni, vPort.portName);
                        EPoint portLoc = pi.getCenter();
                        Point2D pinLoc = new Point2D.Double(portLoc.getX() - 5, portLoc.getY());
                        NodeInst stubPin = NodeInst.makeInstance(np, ep, pinLoc, np.getDefWidth(ep), np.getDefHeight(ep), cell);
                        ArcInst.makeInstance(ap, ep, pi, stubPin.getOnlyPortInst(), portLoc, pinLoc, name);
                    }
                }
                for (int i = 1; i < scalarPorts.size(); i++)
                {
                    VPort fromPort = scalarPorts.get(i - 1);
                    VPort toPort = scalarPorts.get(i);
                    NodeInst fromNi = placed.get(fromPort.in);
                    NodeInst toNi = placed.get(toPort.in);
                    if (fromNi == null || toNi == null)
                        continue;
                    PortInst fromPi = findPortOnNode(fromNi, fromPort.portName);
                    if (fromPi == null)
                        return null;
                    PortInst toPi = findPortOnNode(toNi, toPort.portName);
                    if (toPi == null)
                        return null;
                    portMap.put(netName, fromPi);
                    portMap.put(netName, toPi);
                    EPoint fromCtr = getPortCenter(fromPi, portLocMap);
                    EPoint toCtr = getPortCenter(toPi, portLocMap);
                    ArcProto ap = Generic.tech().unrouted_arc;
                    if (schematic)
                    {
                        if (fromPi.getPortProto().getBasePort().connectsTo(Schematics.tech().wire_arc)
                            && toPi.getPortProto().getBasePort().connectsTo(Schematics.tech().wire_arc))
                            ap = Schematics.tech().wire_arc;
                    }
                    ArcInst ai = ArcInst.makeInstance(ap, ep, fromPi, toPi, fromCtr, toCtr, netName);
                    if (ai != null && schematic)
                        ai.setFixedAngle(false);
                    count++;
                    if (verbose && (count % 100) == 0)
                        Job.getUserInterface().setProgressValue(count * 100 / total);
                }
            }

            // make exports
            Map<String, PortInst> allExports = new HashMap<String, PortInst>();
            for (VExport port : mod.ports)
            {
                PortCharacteristic pc = null;
                switch (port.mode)
                {
                    case MODE_UNKNOWN:
                        pc = PortCharacteristic.UNKNOWN;
                        break;
                    case MODE_IN:
                        pc = PortCharacteristic.IN;
                        break;
                    case MODE_OUT:
                        pc = PortCharacteristic.OUT;
                        break;
                    case MODE_INOUT:
                        pc = PortCharacteristic.BIDIR;
                        break;
                }

                String portName = port.name;
                if (port.firstIndex != port.secondIndex)
                {
                    if (schematic)
                        portName += "[" + port.firstIndex + ":" + port.secondIndex + "]";
                    else
                    {
                        int low = Math.min(port.firstIndex, port.secondIndex);
                        int high = Math.max(port.firstIndex, port.secondIndex);
                        for (int i = low; i <= high; i++)
                        {
                            String thisPortName = portName + "[" + i + "]";
                            PortInst pi = portMap.get(thisPortName);
                            if (pi == null)
                            {
                                List<VPort> ports = mod.allNetworks.get(thisPortName);
                                if (ports != null && ports.size() > 0)
                                {
                                    VPort lp = ports.get(0);
                                    NodeInst ni = placed.get(lp.in);
                                    if (ni != null)
                                        pi = ni.findPortInst(lp.portName);
                                }
                                if (pi == null)
                                {
                                    NodeProto np = Generic.tech().universalPinNode;
                                    NodeInst ni = NodeInst.makeInstance(np, ep, EPoint.fromLambda(x, y), np.getDefWidth(ep), np.getDefHeight(ep), cell);
                                    pi = ni.getOnlyPortInst();
                                    x += np.getDefWidth(ep) * 5 + GAP;
                                    highest = Math.max(highest, np.getDefHeight(ep) * 5);
                                    if (x >= cellSize)
                                    {
                                        x = 0;
                                        y += highest + GAP;
                                        highest = 0;
                                    }
                                }
                            }
                            Export.newInstance(cell, pi, thisPortName, ep, pc);
                            allExports.put(thisPortName, pi);
                        }
                        continue;
                    }
                }
                PortInst pi = portMap.get(portName);
                if (pi == null)
                {
                    List<VPort> ports = mod.allNetworks.get(portName);
                    if (ports != null)
                    {
                        VPort lp = ports.get(0);
                        NodeInst ni = placed.get(lp.in);
                        if (lp != null && ni != null)
                            pi = ni.findPortInst(lp.portName);
                    }
                    if (pi == null)
                    {
                        NodeProto np = Generic.tech().universalPinNode;
                        NodeInst ni = NodeInst.makeInstance(np, ep, EPoint.fromLambda(x, y), np.getDefWidth(ep), np.getDefHeight(ep), cell);
                        pi = ni.getOnlyPortInst();
                        x += np.getDefWidth(ep) * 5 + GAP;
                        highest = Math.max(highest, np.getDefHeight(ep) * 5);
                        if (x >= cellSize)
                        {
                            x = 0;
                            y += highest + GAP;
                            highest = 0;
                        }
                    }
                }
                Export.newInstance(cell, pi, portName, ep, pc);
                allExports.put(portName, pi);
            }

            // connect assigned exports
            for (String name2 : mod.assignments.keySet())
            {
                PortInst pi2 = allExports.get(name2);
                if (pi2 == null)
                    continue;
                String name1 = mod.assignments.get(name2);
                PortInst pi1 = allExports.get(name1);
                if (pi1 == null)
                    continue;

                ArcProto ap = Generic.tech().unrouted_arc;
                ArcInst.makeInstance(ap, ep, pi1, pi2);
            }

            // make an icon if this is a schematic
            if (schematic)
            {
                try
                {
                    Cell iconCell = ip.makeIconForCell(cell, ep);
                    mod.cell = iconCell;
                    int exampleLocation = ep.getIconGenInstanceLocation();
                    if (exampleLocation != 4)
                    {
                        // place an icon in the schematic
                        Point2D iconPos = new Point2D.Double(0, 0);
                        Rectangle2D cellBounds = cell.getBounds();
                        Rectangle2D iconBounds = iconCell.getBounds();
                        double halfWidth = iconBounds.getWidth() / 2;
                        double halfHeight = iconBounds.getHeight() / 2;
                        switch (exampleLocation)
                        {
                            case 0:		// upper-right
                                iconPos.setLocation(cellBounds.getMaxX() + halfWidth, cellBounds.getMaxY() + halfHeight);
                                break;
                            case 1:		// upper-left
                                iconPos.setLocation(cellBounds.getMinX() - halfWidth, cellBounds.getMaxY() + halfHeight);
                                break;
                            case 2:		// lower-right
                                iconPos.setLocation(cellBounds.getMaxX() + halfWidth, cellBounds.getMinY() - halfHeight);
                                break;
                            case 3:		// lower-left
                                iconPos.setLocation(cellBounds.getMinX() - halfWidth, cellBounds.getMinY() - halfHeight);
                                break;
                        }
                        DBMath.gridAlign(iconPos, ep.getAlignmentToGrid());
                        double px = iconCell.getBounds().getWidth();
                        double py = iconCell.getBounds().getHeight();
                        NodeInst.makeInstance(iconCell, ep, iconPos, px, py, cell);
                    }
                } catch (JobException e)
                {
                }
            }

            if (count > 0)
                System.out.println("Created " + count + " wires");
            if (verbose)
                Job.getUserInterface().stopProgressDialog();
        }
        if (verbose)
            Job.getUserInterface().stopProgressDialog();
        return cell;
    }

    private String makeBusName(String[] signals)
    {
        boolean breakBus = false;
        int startIndex = 0, lastIndex = 0;
        int dir = 0;
        int braPos = signals[0].indexOf('[');
        String prefix = null;
        if (braPos >= 0)
        {
            prefix = signals[0].substring(0, braPos);
            for (int i = 0; i < signals.length; i++)
            {
                braPos = signals[i].indexOf('[');
                if (braPos < 0)
                {
                    breakBus = true;
                    break;
                }
                String pre = signals[i].substring(0, braPos);
                if (!pre.equals(prefix))
                {
                    breakBus = true;
                    break;
                }
                int cloPos = signals[i].indexOf(']', braPos);
                if (cloPos < 0)
                {
                    breakBus = true;
                    break;
                }
                String index = signals[i].substring(braPos + 1, cloPos);
                if (!TextUtils.isANumber(index))
                {
                    breakBus = true;
                    break;
                }
                int ind = TextUtils.atoi(index);
                if (cloPos + 1 != signals[i].length())
                {
                    breakBus = true;
                    break;
                }
                if (i == 0)
                {
                    startIndex = ind;
                    continue;
                }
                if (i == 1)
                {
                    if (ind == startIndex + 1)
                        dir = 1;
                    else if (ind == startIndex - 1)
                        dir = -1;
                    else
                    {
                        breakBus = true;
                        break;
                    }
                } else
                {
                    if (ind != lastIndex + dir)
                    {
                        breakBus = true;
                        break;
                    }
                }
                lastIndex = ind;
            }
        }

        if (breakBus)
        {
            String name = "";
            for (int i = 0; i < signals.length; i++)
            {
                if (i > 0)
                    name += ",";
                name += signals[i];
            }
            return name;
        }
        return prefix + "[" + startIndex + ":" + lastIndex + "]";
    }

    private int getPortWidth(PortInst pi, Netlist nl)
    {
        int busWidth = 1;
        if (pi.getNodeInst().isCellInstance())
            busWidth = nl.getBusWidth((Export)pi.getPortProto());
        return busWidth;
    }

    private PortInst findPortOnNode(NodeInst ni, String portName)
    {
        PortInst pi = ni.findPortInst(portName);
        if (pi != null)
            return pi;

        String desiredName = portName;
        int bracketPos = desiredName.indexOf('[');
        if (bracketPos >= 0)
            desiredName = desiredName.substring(0, bracketPos);
        for (Iterator<PortInst> it = ni.getPortInsts(); it.hasNext();)
        {
            PortInst pInst = it.next();
            String pName = pInst.getPortProto().getName();
            bracketPos = pName.indexOf('[');
            if (bracketPos >= 0)
                pName = pName.substring(0, bracketPos);
            if (pName.equals(desiredName))
            {
                if (pi != null)
                {
                    pi = null;
                    break;
                }
                pi = pInst;
            }
        }
        if (pi != null)
            return pi;

        hasErrors = true;
        System.out.println("Cannot find port " + portName + " on node " + ni.describe(false));
        System.out.println("Check errors reported.");
        if (verbose)
            Job.getUserInterface().stopProgressDialog();
        return null;
    }

    /**
     * Method to cache the center of ports.
     * This is necessary because the call to "PortInst.getCenter()" is expensive.
     * This method assumes that all nodes are unrotated.
     *
     * @param pi the PortInst being requested.
     * @param portLocMap a caching map for port locations.
     * @return the center of the PortInst.
     */
    private EPoint getPortCenter(PortInst pi, Map<NodeProto, Map<PortProto, Point2D>> portLocMap)
    {
        NodeInst ni = pi.getNodeInst();
        Map<PortProto, Point2D> portMap = portLocMap.get(ni.getProto());
        Point2D pt = portMap.get(pi.getPortProto());
        return EPoint.fromLambda(pt.getX() + ni.getAnchorCenterX(), pt.getY() + ni.getAnchorCenterY());
    }

    /**
     * ****************************** THE ALS NETLIST GENERATOR *******************************
     */
    /**
     * Method to generate an ALS (simulation) netlist.
     *
     * @param destLib destination library.
     * @return a List of strings with the netlist.
     */
    public List<String> getALSNetlist(Library destLib)
    {
        // now produce the netlist
        if (hasErrors)
            return null;

        // print file header
        List<String> netlist = new ArrayList<String>();
        netlist.add("#*************************************************");
        netlist.add("#  ALS Netlist file");
        netlist.add("#");
        if (User.isIncludeDateAndVersionInOutput())
            netlist.add("#  File Creation:    " + TextUtils.formatDate(new Date()));
        netlist.add("#*************************************************");
        netlist.add("");

        // determine top level cell
        for (VModule mod : allModules)
        {
            if (mod.defined)
                genALSInterface(mod, netlist);
        }

        // print closing line of output file
        netlist.add("#********* End of netlist *******************");
        return netlist;
    }

    /**
     * Method to generate the ALS description for the specified model.
     *
     * @param module module to analyze
     * @param netlist the List of strings to create.
     */
    private void genALSInterface(VModule module, List<String> netlist)
    {
        // write this entity
        String modLine = "model " + module.name + "(";
        boolean first = true;
        for (VExport fp : module.ports)
        {
            for (int i = fp.firstIndex; i <= fp.secondIndex; i++)
            {
                if (!first)
                    modLine += ", ";
                first = false;
                modLine += fp.name;
                if (i != -1)
                    modLine += "_" + i + "_";
            }
        }
        modLine += ")";
        netlist.add(modLine);

        // write instances
        for (VInstance in : module.instances)
        {
            first = true;
            String inName = in.instanceName.replaceAll("/", "_").replaceAll("\\[", "_").replaceAll("\\]", "_");
            String inLine = inName + ": " + in.module.name + "(";
            for (VPort lp : in.ports.keySet())
            {
                if (!first)
                    inLine += ", ";
                first = false;
                String[] signalNames = in.ports.get(lp);
                for (int i = 0; i < signalNames.length; i++)
                {
                    String name = signalNames[i].replaceAll("/", "_").replaceAll("\\[", "_").replaceAll("\\]", "_");
                    inLine += name;
                }
            }
            inLine += ")";
            netlist.add(inLine);
        }
        netlist.add("");
    }

    /**
     * ****************************** THE QUISC NETLIST GENERATOR *******************************
     */
    /**
     * Method to generate a QUISC (silicon compiler) netlist.
     *
     * @param destLib destination library.
     * @param isIncludeDateAndVersionInOutput include date and version in output
     * @return a List of strings with the netlist.
     */
    public List<String> getQUISCNetlist(Library destLib, boolean isIncludeDateAndVersionInOutput)
    {
        // now produce the netlist
        if (hasErrors)
            return null;
        List<String> netlist = new ArrayList<String>();

        // print file header
        netlist.add("!*************************************************");
        netlist.add("!  QUISC Command file");
        netlist.add("!");
        if (isIncludeDateAndVersionInOutput)
            netlist.add("!  File Creation:    " + TextUtils.formatDate(new Date()));
        netlist.add("!-------------------------------------------------");
        netlist.add("");

        // determine top level cell
        for (VModule mod : allModules)
        {
            if (mod.defined)
                genQuiscInterface(mod, netlist);
        }

        // print closing line of output file
        netlist.add("!********* End of command file *******************");
        return netlist;
    }

    /**
     * Method to generate the QUISC description for the specified model.
     *
     * @param module module to analyze
     * @param netlist the List of strings to create.
     */
    private void genQuiscInterface(VModule module, List<String> netlist)
    {
        // write this entity
        netlist.add("create cell " + module.name);

        // write instances
        for (VInstance in : module.instances)
        {
            if (in.module == null)
                netlist.add("create instance " + in.instanceName + " " + in.fun.getName());
            else
                netlist.add("create instance " + in.instanceName + " " + in.module.name);
        }

        for (String netName : module.allNetworks.keySet())
        {
            List<VPort> ports = module.allNetworks.get(netName);
            VPort last = null;
            for (VPort lp : ports)
            {
                if (last != null)
                    netlist.add("connect " + last.in.instanceName + " " + last.portName + " " + lp.in.instanceName + " " + lp.portName);
                last = lp;
            }
        }

        // create export list
        for (VExport port : module.ports)
        {
            for (int i = port.firstIndex; i <= port.secondIndex; i++)
            {
                String name = port.name;
                if (i != -1)
                    name += "[" + i + "]";
                boolean found = false;
                for (VInstance in : module.instances)
                {
                    for (VPort lp : in.ports.keySet())
                    {
                        String[] signalNames = in.ports.get(lp);
                        for (int j = 0; j < signalNames.length; j++)
                        {
                            if (signalNames[j].equals(name))
                            {
                                String line = "export " + in.instanceName + " " + lp.portName + " " + name + " ";
                                switch (port.mode)
                                {
                                    case MODE_UNKNOWN:
                                        line += "unknown";
                                        break;
                                    case MODE_IN:
                                        line += "input";
                                        break;
                                    case MODE_OUT:
                                        line += "output";
                                        break;
                                    case MODE_INOUT:
                                        line += "inout";
                                        break;
                                }
                                netlist.add(line);
                                found = true;
                                break;
                            }
                        }
                        if (found)
                            break;
                    }
                    if (found)
                        break;
                }
                if (!found)
                    netlist.add("! DID NOT FIND EXPORT " + name);
            }
        }
    }
}
