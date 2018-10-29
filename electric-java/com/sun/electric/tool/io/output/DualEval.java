/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: DualEval.java
 * Input/output tool: ACL2 DualEval output
 *
 * Copyright (c) 2004, Static Free Software. All rights reserved.
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
import com.sun.electric.database.hierarchy.Export;
import com.sun.electric.database.network.Netlist;
import com.sun.electric.database.network.Network;
import com.sun.electric.database.prototype.PortCharacteristic;
import com.sun.electric.database.text.Name;
import com.sun.electric.database.topology.IconNodeInst;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.topology.PortInst;
import com.sun.electric.database.variable.VarContext;
import com.sun.electric.technology.Technology;
import com.sun.electric.tool.io.IOTool;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Write ACL2 .lisp file with DualEval Network
 */
public class DualEval extends Output
{
    private final List<Cell> downTop = new ArrayList<>();
    private final Map<String, PrimitiveTemplate> primNames = new HashMap<>();

    private DualEvalPreferences localPrefs;

    public static class DualEvalPreferences extends OutputPreferences
    {
        // DXF Settings
        int dxfScale = IOTool.getDXFScale();
        public Technology tech;

        public DualEvalPreferences(boolean factory)
        {
            super(factory);
            tech = Technology.getCurrent();
        }

        @Override
        public Output doOutput(Cell cell, VarContext context, String filePath)
        {
            DualEval out = new DualEval(this);
            if (out.openTextOutputStream(filePath))
                return out.finishWrite();

            out.writeDualEval(cell);

            if (out.closeTextOutputStream())
                return out.finishWrite();
            System.out.println(filePath + " written");
            return out.finishWrite();
        }
    }

    /**
     * Creates a new instance of the DualEval netlister.
     */
    DualEval(DualEval.DualEvalPreferences dp)
    {
        localPrefs = dp;
        initPrims();
    }

    private void writeDualEval(Cell cell)
    {
        enumDownTop(cell, new HashSet<>());
        printWriter.println("(in-package \"ADE\")");
        printWriter.println("(include-book \"network-prelude\")");
        String prevName = "network-prelude";
        printWriter.println("#|");
        for (Cell c : downTop)
        {
            String name = c.getName();
            List<IconNodeInst> nodes = new ArrayList<>();
            for (Iterator<NodeInst> it = c.getNodes(); it.hasNext();)
            {
                NodeInst ni = it.next();
                if (ni instanceof IconNodeInst && !ni.isIconOfParent())
                {
                    nodes.add((IconNodeInst)ni);
                }
            }
            int numGo = calcNumGo(nodes);
            Map<IconNodeInst, Set<IconNodeInst>> graph = makeDepGraph(nodes);
//            printDepGraph(graph);
            Collection<IconNodeInst> sortedNodes = toposort(nodes, graph);
            printWriter.println();
            writeDefconst(c, sortedNodes, numGo, prevName);
            prevName = name;
        }
        printWriter.println("|#");
        for (Cell c : downTop)
        {
            String name = c.getName();
            List<IconNodeInst> nodes = new ArrayList<>();
            for (Iterator<NodeInst> it = c.getNodes(); it.hasNext();)
            {
                NodeInst ni = it.next();
                if (ni instanceof IconNodeInst && !ni.isIconOfParent())
                {
                    nodes.add((IconNodeInst)ni);
                }
            }
            int numGo = calcNumGo(nodes);
            Map<IconNodeInst, Set<IconNodeInst>> graph = makeDepGraph(nodes);
//            printDepGraph(graph);
            Collection<IconNodeInst> sortedNodes = toposort(nodes, graph);
            printWriter.println();
            writeModuleGenerator(c, sortedNodes, numGo);
        }
        printWriter.println();
        printWriter.println("(defn " + cell.getName() + "$netlist ()");
        printWriter.println("  (list*");
        for (int i = downTop.size() - 1; i >= 0; i--)
        {
            printWriter.println("    (" + downTop.get(i).getName() + "*)");
        }
        printWriter.println("    *network-prelude*))");
    }

    private void writeDefconst(Cell c, Collection<IconNodeInst> sortedNodes, int numGo, String prevName)
    {
        String name = c.getName();
        printWriter.println("(defconst *" + name + "*");
        printWriter.println("  (cons");
        printWriter.println("     `(" + name);
        writeExports(c, PortCharacteristic.IN, numGo);
        writeExports(c, PortCharacteristic.OUT, 0);
        writeStates("       (", sortedNodes);
        printWriter.println("       ()");
        writeNodes(sortedNodes);
        printWriter.println("       )");
        printWriter.println("     " + (prevName != null ? "*" + prevName + "*" : "nil") + "))");

    }

    private void writeModuleGenerator(Cell c, Collection<IconNodeInst> sortedNodes, int numGo)
    {
        String name = c.getName();
        printWriter.println("(module-generator");
        printWriter.println("  " + name + "* ()");
        printWriter.println("  '" + name);
        writeExportsGenerator(c, PortCharacteristic.IN, numGo);
        writeExportsGenerator(c, PortCharacteristic.OUT, 0);
        writeStates("  '(", sortedNodes);
        writeNodesGenerator(sortedNodes);
        printWriter.println("  :guard t)");
    }

    private int calcNumGo(Collection<IconNodeInst> nodes)
    {
        int numGo = 0;
        for (IconNodeInst ni : nodes)
        {
            PrimitiveTemplate pt = primNames.get(ni.getProto().getName());
            if (pt != null)
            {
                numGo += pt.numGo;
            }
        }
        return numGo;
    }

    private void writeExports(Cell cell, PortCharacteristic pc, int numGo)
    {
        boolean first = true;
        for (Iterator<Export> it = cell.getExports(); it.hasNext();)
        {
            Export export = it.next();

            if (export.getCharacteristic() == pc)
            {
                if (first)
                {
                    printWriter.print("       `(");
                    first = false;
                } else
                {
                    printWriter.print(" ");
                }
                printWriter.print(export.getName());
            }
        }
        if (numGo == 0)
        {
            if (first)
            {
                printWriter.println("       ()");
            } else
            {
                printWriter.println(")");
            }
        } else if (first)
        {
            printWriter.println("       ,(sis 'go 0 " + numGo + ")");
        } else
        {
            printWriter.println(" . ,(sis 'go 0 " + numGo + "))");
        }
    }

    private void writeExportsGenerator(Cell cell, PortCharacteristic pc, int numGo)
    {
        if (numGo == 0)
        {
            printWriter.print("  (list");
        } else
        {
            printWriter.print("  (list*");
        }
        for (Iterator<Export> it = cell.getExports(); it.hasNext();)
        {
            Export export = it.next();

            if (export.getCharacteristic() == pc)
            {
                printWriter.print(" '" + export.getName());
            }
        }
        if (numGo == 0)
        {
            printWriter.println(")");
        } else
        {
            printWriter.println(" (sis 'go 0 " + numGo + "))");
        }
    }

    private void writeStates(String prefix, Collection<IconNodeInst> nodes)
    {
        printWriter.print(prefix);
        boolean first = true;
        for (IconNodeInst ni : nodes)
        {
            PrimitiveTemplate pt = primNames.get(ni.getProto().getName());
            if (pt != null && pt.hasState)
            {
                if (first)
                {
                    first = false;
                } else
                {
                    printWriter.print(" ");
                }
                printWriter.print(ni.getName());
            }
        }
        printWriter.println(")");
    }

    private Map<IconNodeInst, Set<IconNodeInst>> makeDepGraph(List<IconNodeInst> nodes)
    {
        Map<IconNodeInst, Set<IconNodeInst>> graph = new LinkedHashMap<>();
        for (IconNodeInst ni : nodes)
        {
            Netlist netlist = ni.getParent().getNetlist();
            Cell proto = ni.getProto();
            for (Iterator<Export> it = proto.getExports(); it.hasNext();)
            {
                Export e = it.next();
                if (e.getCharacteristic() == PortCharacteristic.OUT)
                {
                    PortInst pi = ni.findPortInstFromProto(e);
                    Network net = netlist.getNetwork(pi);
                    for (PortInst pi2 : net.getPortsList())
                    {
                        if (!pi.equals(pi2) && pi2.getNodeInst() instanceof IconNodeInst)
                        {
                            IconNodeInst ni2 = (IconNodeInst)pi2.getNodeInst();
                            if (ni2.equals(ni))
                            {
                                continue;
                            }
                            PrimitiveTemplate pt = primNames.get(ni2.getProto().getName());
                            if (pt != null && !pt.propagates)
                            {
                                continue;
                            }
                            Export e2 = (Export)pi2.getPortProto();
                            if (e2.getCharacteristic() != PortCharacteristic.IN)
                            {
                                System.out.println("Multiple drivers of " + net);
                            }
                            Set<IconNodeInst> dep = graph.get(ni2);
                            if (dep == null)
                            {
                                dep = new LinkedHashSet<>();
                                graph.put(ni2, dep);
                            }
                            dep.add(ni);
                        }
                    }
                }
            }
        }
        return graph;
    }

    private void printDepGraph(Map<IconNodeInst, Set<IconNodeInst>> graph)
    {
        for (Map.Entry<IconNodeInst, Set<IconNodeInst>> e : graph.entrySet())
        {
            IconNodeInst niTo = e.getKey();
            Set<IconNodeInst> niFroms = e.getValue();
            System.out.print(niTo.getName() + " <-");
            for (IconNodeInst niFrom : niFroms)
            {
                System.out.print(" " + niFrom.getName());
            }
            System.out.println();
        }
    }

    private Collection<IconNodeInst> toposort(List<IconNodeInst> nodes, Map<IconNodeInst, Set<IconNodeInst>> graph)
    {
        Set<IconNodeInst> visited = new HashSet<>();
        Set<IconNodeInst> sort = new LinkedHashSet<>();
        for (IconNodeInst ni : nodes)
        {
            toposort(ni, graph, visited, sort);
        }
        return sort;
    }

    private void toposort(IconNodeInst top, Map<IconNodeInst, Set<IconNodeInst>> graph, Set<IconNodeInst> visited, Set<IconNodeInst> sort)
    {
        if (sort.contains(top))
        {
            return;
        }
        if (visited.contains(top))
        {
            System.out.println("Combinational loop at " + top);
            return;
        }
        visited.add(top);
        Set<IconNodeInst> deps = graph.get(top);
        if (deps != null)
        {
            for (IconNodeInst dep : deps)
            {
                toposort(dep, graph, visited, sort);
            }
        }
        sort.add(top);
    }

    private void writeNodes(Collection<IconNodeInst> nodes)
    {
        int startGo = 0;
        printWriter.println("       (");
        for (IconNodeInst node : nodes)
        {
            printWriter.print("        (" + node.getName());
            String protoName = node.getProto().getName();
            PrimitiveTemplate pt = primNames.get(protoName);
            if (pt != null)
            {
                printWriter.println(" " + portListStr(node, pt.outputs, 0, startGo)
                    + " " + pt.primName + " "
                    + portListStr(node, pt.inputs, pt.numGo, startGo)
                    + ")");
                startGo += pt.numGo;
            } else
            {
                printWriter.println(" " + portListStr(node, PortCharacteristic.OUT)
                    + " " + protoName + " "
                    + portListStr(node, PortCharacteristic.IN)
                    + ")");
            }
        }
        printWriter.println("       )");
    }

    private void writeNodesGenerator(Collection<IconNodeInst> nodes)
    {
        int startGo = 0;
        printWriter.println("  (list");
        for (IconNodeInst node : nodes)
        {
            String protoName = node.getProto().getName();
            PrimitiveTemplate pt = primNames.get(protoName);
            if (pt == null)
            {
                printWriter.println(
                    "   '(" + node.getName()
                    + " " + portListStr(node, PortCharacteristic.OUT)
                    + " " + protoName
                    + " " + portListStr(node, PortCharacteristic.IN)
                    + ")");
            } else if (pt.numGo == 0)
            {
                printWriter.println(
                    "   '(" + node.getName()
                    + " " + portListStr(node, pt.outputs, 0, startGo)
                    + " " + pt.primName
                    + " " + portListStr(node, pt.inputs, pt.numGo, startGo)
                    + ")");
                startGo += pt.numGo;
            } else
            {
                printWriter.println("   (list '" + node.getName());
                printWriter.println("         '" + portListStr(node, pt.outputs, 0, startGo));
                printWriter.println("         '" + pt.primName);
                printWriter.println("         " + portListStrGen(node, pt.inputs, pt.numGo, startGo) + ")");
                startGo += pt.numGo;
            }
        }
        printWriter.println("   )");
    }

    private String portListStr(IconNodeInst ni, String[] portNames, int numGo, int startGo)
    {
        StringBuilder sb = new StringBuilder();
        Netlist netlist = ni.getParent().getNetlist();
        sb.append("(");
        boolean first = true;
        for (String portName : portNames)
        {
            Network net = netlist.getNetwork(ni.getNodable(0), Name.findName(portName));
            if (first)
            {
                first = false;
            } else
            {
                sb.append(" ");
            }
            sb.append(net.getName());
        }
        if (numGo != 0)
        {
            if (!first)
            {
                sb.append(" ");
            }
            if (numGo != 1)
            {
                throw new UnsupportedOperationException("numGo=" + numGo);
            }
            sb.append(",(si 'go " + startGo + ")");
        }
        sb.append(")");
        return sb.toString();
    }

    private String portListStrGen(IconNodeInst ni, String[] portNames, int numGo, int startGo)
    {
        StringBuilder sb = new StringBuilder();
        Netlist netlist = ni.getParent().getNetlist();
        sb.append("(list");
        boolean first = true;
        for (String portName : portNames)
        {
            Network net = netlist.getNetwork(ni.getNodable(0), Name.findName(portName));
            sb.append(" '").append(net.getName());
        }
        if (numGo != 1)
        {
            throw new UnsupportedOperationException("numGo=" + numGo);
        }
        sb.append(" (si 'go ").append(startGo).append("))");
        return sb.toString();
    }

    private String portListStr(IconNodeInst ni, PortCharacteristic pc)
    {
        StringBuilder sb = new StringBuilder();
        Netlist netlist = ni.getParent().getNetlist();
        sb.append("(");
        boolean first = true;
        for (Iterator<Export> it = ni.getProto().getExports(); it.hasNext();)
        {
            Export e = it.next();
            if (e.getCharacteristic() == pc)
            {
                PortInst pi = ni.findPortInstFromProto(e);
                Network net = netlist.getNetwork(pi);
                if (first)
                {
                    first = false;
                } else
                {
                    sb.append(" ");
                }
                sb.append(net.getName());
            }
        }
        sb.append(")");
        return sb.toString();
    }

    private void enumDownTop(Cell topIcon, Set<Cell> visitedIcons)
    {
        if (visitedIcons.contains(topIcon))
        {
            return;
        }
        visitedIcons.add(topIcon);
        PrimitiveTemplate pt = primNames.get(topIcon.getName());
        if (pt != null)
        {
            return;
        }
        Cell schem = topIcon.getCellGroup().getMainSchematics();
        if (schem != null)
        {
            for (Iterator<NodeInst> it = schem.getNodes(); it.hasNext();)
            {
                NodeInst ni = it.next();

                if (ni instanceof IconNodeInst && !ni.isIconOfParent())
                {
                    enumDownTop((Cell)ni.getProto(), visitedIcons);
                }
            }
        }
        downTop.add(schem != null ? schem : topIcon);
    }

    private static class PrimitiveTemplate
    {
        private final String cellName;
        private final String primName;
        private final String[] outputs;
        private final String[] inputs;
        private final boolean propagates; // outputs combinationally depend on inputs
        private final boolean hasState; // has state
        private final int numGo; // number of go signals

        PrimitiveTemplate(String cellName, String primName,
            String[] outputs, String[] inputs, boolean propagates, int numGo)
        {
            this.cellName = cellName;
            this.primName = primName;
            this.outputs = outputs;
            this.inputs = inputs;
            this.propagates = propagates;
            this.hasState = !propagates;
            this.numGo = numGo;
        }
    }

    private void def(String cellName, String primName,
        String[] outputs, String[] inputs, boolean propagates, int numGo)
    {
        PrimitiveTemplate pt = new PrimitiveTemplate(cellName, primName, outputs, inputs, propagates, numGo);
        primNames.put(cellName, pt);
    }

    private void defComb(String cellName, String primName, String output, String... inputs)
    {
        String[] outputs = new String[]
        {
            output
        };
        def(cellName, primName, outputs, inputs, true, 0);
    }

    private void initPrims()
    {
        defComb("inv", "b-not", "out", "in");
        defComb("invn", "b-not", "out", "in");
        defComb("nand2", "b-nand", "out", "ina", "inb");
        defComb("nor2", "b-nor", "out", "ina", "inb");
        defComb("xor2", "b-xor", "out", "ina", "inb"); // BOZO inputs inaB and inbB are ignored

        def("joint", "joint-cntl",
            new String[]
            {
                "fire"
            },
            new String[]
            {
                "predFull", "succFull"
            },
            true, 1);

        def("linkE", "link-st-e",
            new String[]
            {
                "full"
            },
            new String[]
            {
                "fill", "drain"
            },
            false, 0);
        def("linkF", "link-st-f",
            new String[]
            {
                "full"
            },
            new String[]
            {
                "fill", "drain"
            },
            false, 0);
    }
}
