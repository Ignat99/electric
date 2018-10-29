/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: NetSchem.java
 * Written by: Dmitry Nadezhin.
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
package com.sun.electric.database.network;

import com.sun.electric.database.CellTree;
import com.sun.electric.database.EquivPorts;
import com.sun.electric.database.EquivalentSchematicExports;
import com.sun.electric.database.Snapshot;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.Export;
import com.sun.electric.database.hierarchy.Nodable;
import com.sun.electric.database.id.CellId;
import com.sun.electric.database.prototype.NodeProto;
import com.sun.electric.database.prototype.PortCharacteristic;
import com.sun.electric.database.prototype.PortProto;
import com.sun.electric.database.text.Name;
import com.sun.electric.database.topology.ArcInst;
import com.sun.electric.database.topology.Connection;
import com.sun.electric.database.topology.IconNodeInst;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.topology.PortInst;
import com.sun.electric.technology.ArcProto;
import com.sun.electric.technology.PrimitiveNode;
import com.sun.electric.technology.technologies.Schematics;
import com.sun.electric.tool.Job;
import com.sun.electric.util.math.MutableInteger;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * This is the mirror of group of Icon and Schematic cells in Network tool.
 */
class NetSchem extends NetCell {

    private class IconInst {

        final NodeInst nodeInst;
        final boolean iconOfParent;
        final int numNodables;
        final int netMapOffset;
        final int numExtendedExports;
        final EquivalentSchematicExports eq;

        private IconInst(NodeInst nodeInst, int nodeOffset) {
            Cell proto = (Cell) nodeInst.getProto();
            assert proto.isIcon() || proto.isSchematic();
            this.nodeInst = nodeInst;
            iconOfParent = nodeInst.isIconOfParent();
            numNodables = iconOfParent ? -1 : nodeInst.getNameKey().busWidth();
//            iconNodables = iconOfParent ? null : new Nodable[nodeInst.getNameKey().busWidth()];
            this.netMapOffset = nodeOffset;
            eq = iconOfParent ? null : nodeInst.getDatabase().backup().getEquivExports(proto.getId());
            this.numExtendedExports = iconOfParent ? -1000000 : eq.implementation.numExpandedExports;
        }
        
        private Nodable getNodable(int arrayIndex) {
            assert numNodables == nodeInst.getNumNodables();
            return nodeInst.getNodable(arrayIndex);
        }
 
        private int getNetMapOffset(Nodable proxy) {
            int arrayIndex = proxy.getNodableArrayIndex();
            assert getNodable(arrayIndex) == proxy;
            return netMapOffset + arrayIndex * numExtendedExports;
        }
    }

    /* Implementation of this NetSchem. */ NetSchem implementation;
    /**
     * Node offsets.
     */
    int[] drawnOffsets;
    /**
     * Info for instances of Icons
     */
    IconInst[] iconInsts;
    /**
     * IconInsts with global rebindes.
     */
    IdentityHashMap<IconInst, Set<Global>> iconInstExcludeGlobals;
    /**
     *      */
    Global.Set globals = Global.Set.empty;
    /**
     *      */
    int[] portOffsets = new int[1];
    /**
     *      */
    int netNamesOffset;
    /**
     *      */
    Name[] drawnNames;
    /**
     *      */
    int[] drawnWidths;
    private IdentityHashMap<Name, Integer> exportNameMapOffsets;
    private CellTree cellTree;
    private CellId mainSchemId;
    private IdentityHashMap<CellId, EquivalentSchematicExports> eqExports;

    NetSchem(Cell cell) {
        super(cell);
        setImplementation(this);
        if (cell.isIcon()) {
            Cell mainSchematics = cell.getMainSchematicInGroup();
            if (mainSchematics != null) {
                NetSchem mainSchem = new NetSchem(mainSchematics);
                setImplementation(mainSchem);
            }
        }
    }

// 	NetSchem(Cell.CellGroup cellGroup) {
// 		super(cellGroup.getMainSchematics());
// 	}
    private void setImplementation(NetSchem implementation) {
        if (this.implementation == implementation) {
            return;
        }
        this.implementation = implementation;
//        updatePortImplementation();
    }

//    private boolean updatePortImplementation() {
//        boolean changed = false;
//        int numPorts = cell.getNumPorts();
//        if (portImplementation == null || portImplementation.length != numPorts) {
//            changed = true;
//            portImplementation = new int[numPorts];
//        }
//        Cell c = implementation.cell;
//        for (int i = 0; i < numPorts; i++) {
//            Export e = cell.getPort(i);
//            int equivIndex = -1;
//            if (c != null) {
//                Export equiv = e.getEquivalentPort(c);
//                if (equiv != null) {
//                    equivIndex = equiv.getPortIndex();
//                }
//            }
//            if (portImplementation[i] != equivIndex) {
//                changed = true;
//                portImplementation[i] = equivIndex;
//            }
//            if (equivIndex < 0) {
//                String msg = cell + ": Icon port <" + e.getNameKey() + "> has no equivalent port";
//                System.out.println(msg);
//                networkManager.pushHighlight(e);
//                networkManager.logError(msg, NetworkTool.errorSortPorts);
//            }
//        }
//        if (c != null && numPorts != c.getNumPorts()) {
//            for (int i = 0; i < c.getNumPorts(); i++) {
//                Export e = c.getPort(i);
//                if (e.getEquivalentPort(cell) == null) {
//                    String msg = c + ": Schematic port <" + e.getNameKey() + "> has no equivalent port in " + cell;
//                    System.out.println(msg);
//                    networkManager.pushHighlight(e);
//                    networkManager.logError(msg, NetworkTool.errorSortPorts);
//                }
//            }
//        }
//        return changed;
//    }
//    @Override
//    int getPortOffset(int portIndex, int busIndex) {
//        portIndex = portImplementation[portIndex];
//        if (portIndex < 0) {
//            return -1;
//        }
//        int portOffset = implementation.portOffsets[portIndex] + busIndex;
//        if (busIndex < 0 || portOffset >= implementation.portOffsets[portIndex + 1]) {
//            return -1;
//        }
//        return portOffset;
//    }
    @Override
    NetSchem getSchem() {
        return implementation;
    }

    /**
     * Get an iterator over all of the Nodables of this Cell.
     */
    @Override
    Iterator<Nodable> getNodables() {
        ArrayList<Nodable> nodables = new ArrayList<Nodable>();
        for (Iterator<NodeInst> it = cell.getNodes(); it.hasNext();) {
            NodeInst ni = it.next();
            if (iconInsts[ni.getNodeId()] != null) {
                continue;
            }
            nodables.add(ni);
        }
        for (Iterator<NodeInst> it = cell.getNodes(); it.hasNext();) {
            NodeInst ni = it.next();
            IconInst iconInst = iconInsts[ni.getNodeId()];
            if (iconInst == null || iconInst.iconOfParent) {
                continue;
            }
            for (int i = 0; i < iconInst.numNodables; i++) {
                Nodable proxy = iconInst.getNodable(i);
                assert proxy != null;
                nodables.add(proxy);
            }
        }
        return nodables.iterator();
    }

    /**
     * Get a set of global signal in this Cell and its descendants.
     */
    @Override
    Global.Set getGlobals() {
        return globals;
    }

    /*
     * Get offset in networks map for given global signal.
     */
    @Override
    int getNetMapOffset(Global global) {
        return globals.indexOf(global);
    }

    /**
     * Get offset in networks map for given global of nodable.
     * @param no nodable.
     * @param global global signal.
     * @return offset in networks map.
     */
    @Override
    int getNetMapOffset(Nodable no, Global global) {
        if (no == null) {
            return -1;
        }
        int nodeId = no.getNodeInst().getNodeId();
        if (nodeId < 0 || nodeId >= iconInsts.length) {
            return -1;
        }
        IconInst iconInst = iconInsts[nodeId];
        if (iconInst == null || iconInst.iconOfParent) {
            return -1;
        }
        assert !(no instanceof NodeInst)
                || no == iconInst.nodeInst && ((Cell) iconInst.nodeInst.getProto()).isSchematic();
        int indexOfGlobal = iconInst.eq.implementation.globals.indexOf(global);
        if (indexOfGlobal < 0) {
            return -1;
        }
        return iconInst.getNetMapOffset(no) + indexOfGlobal;
    }

    /**
     * Get offset in networks map for given subnetwork of nodable.
     * @param no nodable.
     * @param equivPortIndex index of entry in equivPortsX
     * @return offset in networks map.
     */
    @Override
    int getNetMapOffset(Nodable no, int equivPortIndex) {
        int nodeId = no.getNodeInst().getNodeId();
        if (nodeId < 0 || nodeId >= iconInsts.length) {
            return -1;
        }
        IconInst iconInst = iconInsts[nodeId];
        if (iconInst == null || iconInst.iconOfParent) {
            return -1;
        }
        return iconInst.getNetMapOffset(no) + equivPortIndex;
    }

    /**
     * Get offset in networks map for given port instance.
     * @throws IndexOutOfBoundsException if busIndex is incorrect
     */
    @Override
    int getNetMapOffset(Nodable no, PortProto portProto, int busIndex) {
        if (no == null) {
            return -1;
        }
        int nodeId = no.getNodeInst().getNodeId();
        if (nodeId < 0 || nodeId >= iconInsts.length) {
            return -1;
        }
        IconInst iconInst = iconInsts[nodeId];
        if (iconInst == null) {
            // primitive or layout cell
            int drawn = drawns[ni_pi[nodeId] + portProto.getPortIndex()];
            if (drawn < 0) {
                return -1;
            }
            if (busIndex < 0 || busIndex >= drawnWidths[drawn]) {
                return -1;
            }
            assert portProto.getParent() == ((NodeInst) no).getProto();
            return drawnOffsets[drawn] + busIndex;
        } else if (iconInst.iconOfParent) {
            return -1;
        } else {
            // icon or schematic cell
            Cell subCell = (Cell) portProto.getParent();
            assert no.getProto() == subCell;
            EquivalentSchematicExports eq = iconInst.eq;
            if (no instanceof IconNodeInst) {
                int portWidth = portProto.getNameKey().busWidth();
                Nodable no1 = ((IconNodeInst) no).getNodable(busIndex / portWidth);
                busIndex = busIndex % portWidth;
                if (Job.getDebug()) {
                    System.out.println("IconNodeInst " + no + " is passed to getNodeIndex. Replaced by IconNodeable " + no1);
                }
                assert eq.cellId == subCell.getId();
                no = no1;
            } else {
                assert eq.implementation.cellId == subCell.getId();
            }
            Name exportName = portProto.getNameKey().subname(busIndex);
            int portOffset = eq.implementation.getExportNameMapOffset(exportName);
            if (portOffset < 0) {
                return -1;
            }
            return iconInst.getNetMapOffset(no) + portOffset;
        }
    }

    /*
     * Get offset in networks map for given port instance.
     */
    @Override
    int getNetMapOffset(Nodable no, Name portName) {
        if (no == null) {
            return -1;
        }
        int nodeId = no.getNodeInst().getNodeId();
        if (nodeId < 0 || nodeId >= iconInsts.length) {
            return -1;
        }
        IconInst iconInst = iconInsts[nodeId];
        if (iconInst == null) {
            // primitive or layout cell
            if (cell.getNodeById(nodeId) != no) {
                throw new IllegalArgumentException();
            }
            PortProto portProto = no.getProto().findPortProto(portName);
            int drawn = drawns[ni_pi[nodeId] + portProto.getPortIndex()];
            if (drawn < 0) {
                return -1;
            }
            if (drawnWidths[drawn] != 1) {
                throw new IllegalArgumentException();
            }
            assert portProto.getParent() == ((NodeInst) no).getProto();
            return drawnOffsets[drawn];
        } else if (iconInst.iconOfParent) {
            throw new IllegalArgumentException();
//            return -1;
        } else {
            // icon or schematic cell
            if (cell.getNodeById(nodeId).getNodable(no.getNodableArrayIndex()) != no) {
                throw new IllegalArgumentException();
            }
            assert !(no instanceof IconNodeInst);
            Cell subCell = (Cell) no.getProto();
            EquivalentSchematicExports eq = iconInst.eq;
            assert eq.implementation.cellId == subCell.getId();
            int portOffset = eq.implementation.getExportNameMapOffset(portName);
            if (portOffset < 0) {
                return -1;
            }
            return iconInst.getNetMapOffset(no) + portOffset;
        }
    }

    /**
     * Method to return the port width of port of the Nodable.
     * @return the either the port width.
     */
    @Override
    int getBusWidth(Nodable no, PortProto portProto) {
        if (no instanceof NodeInst) {
            NodeInst ni = (NodeInst) no;
            int drawn = drawns[ni_pi[ni.getNodeId()] + portProto.getPortIndex()];
            if (drawn < 0) {
                return 0;
            }
            return drawnWidths[drawn];
        } else {
            return portProto.getNameKey().busWidth();
        }
    }

    /*
     * Get offset in networks map for given export.
     */
    @Override
    int getNetMapOffset(Export export, int busIndex) {
        int drawn = drawns[export.getPortIndex()];
        if (drawn < 0) {
            return -1;
        }
        if (busIndex < 0 || busIndex >= drawnWidths[drawn]) {
            return -1;
        }
        return drawnOffsets[drawn] + busIndex;
    }

    /*
     * Get offset in networks map for given export name.
     */
    @Override
    int getNetMapOffset(Name exportName) {
        assert !exportName.isBus();
        if (exportNameMapOffsets == null) {
            buildExportNameMapOffsets();
        }
        Integer objResult = exportNameMapOffsets.get(exportName);
        return objResult != null ? objResult.intValue() : -1;
    }

    private void buildExportNameMapOffsets() {
        IdentityHashMap<Name, Integer> map = new IdentityHashMap<Name, Integer>();
        for (int exportIndex = 0; exportIndex < cell.getNumPorts(); exportIndex++) {
            Export e = cell.getPort(exportIndex);
            for (int busIndex = 0; busIndex < e.getNameKey().busWidth(); busIndex++) {
                Name exportName = e.getNameKey().subname(busIndex);
                if (map.containsKey(exportName)) {
                    continue;
                }
                map.put(exportName, Integer.valueOf(portOffsets[exportIndex] + busIndex));
            }
        }
        exportNameMapOffsets = map;
    }

    /*
     * Get offset in networks map for given arc.
     */
    @Override
    int getNetMapOffset(ArcInst ai, int busIndex) {
        int drawn = getArcDrawn(ai);
        if (drawn < 0) {
            return -1;
        }
        if (busIndex < 0 || busIndex >= drawnWidths[drawn]) {
            return -1;
        }
        return drawnOffsets[drawn] + busIndex;
    }

    /**
     * Method to return either the network name or the bus name on this ArcInst.
     * @return the either the network name or the bus n1ame on this ArcInst.
     */
    @Override
    Name getBusName(ArcInst ai) {
        int drawn = getArcDrawn(ai);
        return drawnNames[drawn];
    }

    /**
     * Method to return the bus width on this ArcInst.
     * @return the either the bus width on this ArcInst.
     */
    @Override
    public int getBusWidth(ArcInst ai) {
//        if ((flags & VALID) == 0) {
//            redoNetworks();
//        }
        int drawn = getArcDrawn(ai);
        if (drawn < 0) {
            return 0;
        }
        return drawnWidths[drawn];
    }

    private boolean initNodables() {
        int numNodes = cell.getNumNodes();
        Global.Buf globalBuf = new Global.Buf();
        Map<NodeInst, Set<Global>> nodeInstExcludeGlobal = null;
        for (int i = 0; i < numNodes; i++) {
            NodeInst ni = cell.getNode(i);
            NodeProto np = ni.getProto();
            if (ni.isCellInstance()) {
                Cell subCell = (Cell) np;
                if (!(subCell.isIcon() || subCell.isSchematic())) {
                    if (ni.getNameKey().isBus()) {
                        String msg = cell + ": Array name <" + ni.getNameKey() + "> can be assigned only to icon nodes";
                        System.out.println(msg);
                        networkManager.pushHighlight(ni);
                        networkManager.logError(msg, NetworkTool.errorSortNodes);
                    }
                    continue;
                }
                if (ni.getNameKey().hasDuplicates()) {
                    String msg = cell + ": Node name <" + ni.getNameKey() + "> has duplicate subnames";
                    System.out.println(msg);
                    networkManager.pushHighlight(ni);
                    networkManager.logError(msg, NetworkTool.errorSortNodes);
                }
                if (ni.isIconOfParent()) {
                    continue;
                }
                EquivalentSchematicExports eq = database.backup().getEquivExports(subCell.getId());
                Global.Set gs = eq.implementation.globals;

                // Check for rebinding globals
                if (eq.implementation.globalPartitions != null) {
                    int numPortInsts = np.getNumPorts();
                    Set<Global> gb = null;
                    for (int j = 0; j < numPortInsts; j++) {
                        PortInst pi = ni.getPortInst(j);
                        int piOffset = getPortInstOffset(pi);
                        int drawn = drawns[piOffset];
                        if (drawn < 0 || drawn >= numConnectedDrawns) {
                            continue;
                        }
                        Export e = (Export) pi.getPortProto();
                        Name busName = e.getNameKey();
                        for (int busIndex = 0; busIndex < busName.busWidth(); busIndex++) {
                            Name exportName = busName.subname(busIndex);
                            Global.Set globalsOnElement = eq.implementation.globalPartitions.get(exportName);
                            if (globalsOnElement == null) {
                                continue;
                            }
                            if (gb == null) {
                                gb = new HashSet<Global>();
                            }
                            for (int l = 0; l < globalsOnElement.size(); l++) {
                                Global g = globalsOnElement.get(l);
                                gb.add(g);
                            }
                        }
                    }
                    if (gb != null) {
                        // remember excluded globals for this NodeInst
                        if (nodeInstExcludeGlobal == null) {
                            nodeInstExcludeGlobal = new HashMap<NodeInst, Set<Global>>();
                        }
                        nodeInstExcludeGlobal.put(ni, gb);
                        // fix Set of globals
                        gs = gs.remove(gb.iterator());
                    }
                }

                String errorMsg = globalBuf.addToBuf(gs);
                if (errorMsg != null) {
                    String msg = "Network: " + cell + " has globals with conflicting characteristic " + errorMsg;
                    System.out.println(msg);
                    networkManager.logError(msg, NetworkTool.errorSortNetworks);
                    // TODO: what to highlight?
                    // log.addGeom(shared[i].nodeInst, true, 0, null);
                }
            } else {
                Global g = globalInst(ni);
                if (g != null) {
                    PortCharacteristic characteristic;
                    if (g == Global.ground) {
                        characteristic = PortCharacteristic.GND;
                    } else if (g == Global.power) {
                        characteristic = PortCharacteristic.PWR;
                    } else {
                        characteristic = PortCharacteristic.findCharacteristic(ni.getTechSpecific());
                        if (characteristic == null) {
                            String msg = "Network: " + cell + " has global " + g.getName()
                                    + " with unknown characteristic bits";
                            System.out.println(msg);
                            networkManager.pushHighlight(ni);
                            networkManager.logError(msg, NetworkTool.errorSortNetworks);
                            characteristic = PortCharacteristic.UNKNOWN;
                        }
                    }
                    String errorMsg = globalBuf.addToBuf(g, characteristic);
                    if (errorMsg != null) {
                        String msg = "Network: " + cell + " has global with conflicting characteristic " + errorMsg;
                        System.out.println(msg);
                        networkManager.logError(msg, NetworkTool.errorSortNetworks);
                        //log.addGeom(shared[i].nodeInst, true, 0, null);
                    }
                }
            }
        }
        Global.Set newGlobals = globalBuf.getBuf();
        boolean changed = false;
        if (globals != newGlobals) {
            changed = true;
            globals = newGlobals;
            if (NetworkTool.debug) {
                System.out.println(cell + " has " + globals);
            }
        }
        int mapOffset = portOffsets[0] = globals.size();
        int numPorts = cell.getNumPorts();
        for (int i = 1; i <= numPorts; i++) {
            Export export = cell.getPort(i - 1);
            if (NetworkTool.debug) {
                System.out.println(export + " " + portOffsets[i - 1]);
            }
            mapOffset += export.getNameKey().busWidth();
            if (portOffsets[i] != mapOffset) {
                changed = true;
                portOffsets[i] = mapOffset;
            }
        }
        if (equivPortsN == null || equivPortsN.length != mapOffset) {
            equivPortsN = new int[mapOffset];
            equivPortsP = new int[mapOffset];
            equivPortsA = new int[mapOffset];
        }

        for (int i = 0; i < numDrawns; i++) {
            drawnOffsets[i] = mapOffset;
            mapOffset += drawnWidths[i];
            if (NetworkTool.debug) {
                System.out.println("Drawn " + i + " has offset " + drawnOffsets[i]);
            }
        }
        int maxNodeId = cell.backup().cellRevision.getMaxNodeId();
        iconInsts = new IconInst[maxNodeId + 1];
        iconInstExcludeGlobals = null;
        for (int n = 0; n < numNodes; n++) {
            NodeInst ni = cell.getNode(n);
            if (!ni.isCellInstance()) {
                continue;
            }
            Cell iconCell = (Cell) ni.getProto();
            if (!(iconCell.isIcon() || iconCell.isSchematic())) {
                continue;
            }
            IconInst iconInst = new IconInst(ni, mapOffset);
            iconInsts[ni.getNodeId()] = iconInst;
            if (ni.isIconOfParent()) {
                assert iconInst.iconOfParent && iconInst.numNodables == -1;
                continue;
            }
            Set<Global> gs = nodeInstExcludeGlobal != null ? nodeInstExcludeGlobal.get(ni) : null; // exclude set of globals
            if (gs != null) {
                if (iconInstExcludeGlobals == null) {
                    iconInstExcludeGlobals = new IdentityHashMap<IconInst, Set<Global>>();
                }
                iconInstExcludeGlobals.put(iconInst, gs);
            }

            assert iconInst.numNodables == ni.getNameKey().busWidth();
            for (int i = 0; i < ni.getNameKey().busWidth(); i++) {
                Nodable proxy;
                if (ni instanceof IconNodeInst) {
                    proxy = ((IconNodeInst) ni).getNodable(i);
                } else {
                    assert ni.getNameKey().busWidth() == 1;
                    proxy = ni;
                }
//                iconInst.iconNodables[i] = proxy;
                assert iconInst.getNetMapOffset(proxy) == mapOffset;
                mapOffset += iconInst.numExtendedExports;
            }
        }
        netNamesOffset = mapOffset;
        if (NetworkTool.debug) {
            System.out.println("netNamesOffset=" + netNamesOffset);
        }
        return changed;
    }

    private static Global globalInst(NodeInst ni) {
        NodeProto np = ni.getProto();
        if (np == Schematics.tech().groundNode) {
            return Global.ground;
        }
        if (np == Schematics.tech().powerNode) {
            return Global.power;
        }
        if (np == Schematics.tech().globalNode) {
            String globalName = ni.getVarValue(Schematics.SCHEM_GLOBAL_NAME, String.class);
            if (globalName != null) {
                return Global.newGlobal(globalName);
            }
        }
        return null;
    }

    void calcDrawnWidths() {
        Arrays.fill(drawnNames, null);
        Arrays.fill(drawnWidths, -1);
        int numPorts = cell.getNumPorts();
        int numNodes = cell.getNumNodes();
        int numArcs = cell.getNumArcs();

        for (int i = 0; i < numPorts; i++) {
            int drawn = drawns[i];
            Name name = cell.getPort(i).getNameKey();
            int newWidth = name.busWidth();
            int oldWidth = drawnWidths[drawn];
            if (oldWidth < 0) {
                drawnNames[drawn] = name;
                drawnWidths[drawn] = newWidth;
                continue;
            }
            if (oldWidth != newWidth) {
                reportDrawnWidthError(cell.getPort(i), null, drawnNames[drawn].toString(), name.toString());
                if (oldWidth < newWidth) {
                    drawnNames[drawn] = name;
                    drawnWidths[drawn] = newWidth;
                }
            }
        }
        int arcIndex = 0;
        for (Iterator<ArcInst> it = cell.getArcs(); arcIndex < numArcs; arcIndex++) {
            ArcInst ai = it.next();
            int drawn = drawns[arcsOffset + arcIndex];
            if (drawn < 0) {
                continue;
            }
            Name name = ai.getNameKey();
            if (name.isTempname()) {
                continue;
            }
            int newWidth = name.busWidth();
            int oldWidth = drawnWidths[drawn];
            if (oldWidth < 0) {
                drawnNames[drawn] = name;
                drawnWidths[drawn] = newWidth;
                continue;
            }
            if (oldWidth != newWidth) {
                reportDrawnWidthError(null, ai, drawnNames[drawn].toString(), name.toString());
                if (oldWidth < newWidth) {
                    drawnNames[drawn] = name;
                    drawnWidths[drawn] = newWidth;
                }
            }
        }
        ArcProto busArc = Schematics.tech().bus_arc;
        arcIndex = 0;
        for (Iterator<ArcInst> it = cell.getArcs(); arcIndex < numArcs; arcIndex++) {
            ArcInst ai = it.next();
            int drawn = drawns[arcsOffset + arcIndex];
            if (drawn < 0) {
                continue;
            }
            Name name = ai.getNameKey();
            if (!name.isTempname()) {
                continue;
            }
            int oldWidth = drawnWidths[drawn];
            if (oldWidth < 0) {
                drawnNames[drawn] = name;
                if (ai.getProto() != busArc) {
                    drawnWidths[drawn] = 1;
                }
            }
        }
        for (int i = 0; i < numNodes; i++) {
            NodeInst ni = cell.getNode(i);
            NodeProto np = ni.getProto();
            if (!ni.isCellInstance()) {
                if (np.getFunction().isPin()) {
                    continue;
                }
                if (np == Schematics.tech().offpageNode) {
                    continue;
                }
            }
            int numPortInsts = np.getNumPorts();
            for (int j = 0; j < numPortInsts; j++) {
                PortInst pi = ni.getPortInst(j);
                int drawn = drawns[getPortInstOffset(pi)];
                if (drawn < 0) {
                    continue;
                }
                int oldWidth = drawnWidths[drawn];
                int newWidth = 1;
                if (ni.isCellInstance()) {
                    Cell subCell = (Cell) np;
                    if (subCell.isIcon() || subCell.isSchematic()) {
                        int arraySize = subCell.isIcon() ? ni.getNameKey().busWidth() : 1;
                        int portWidth = pi.getPortProto().getNameKey().busWidth();
                        if (oldWidth == portWidth) {
                            continue;
                        }
                        newWidth = arraySize * portWidth;
                    }
                }
                if (oldWidth < 0) {
                    drawnWidths[drawn] = newWidth;
                    continue;
                }
                if (oldWidth != newWidth) {
                    String msg = "Network: Schematic " + cell + " has net <"
                            + drawnNames[drawn] + "> with width conflict in connection " + pi.describe(true);
                    System.out.println(msg);
                    networkManager.pushHighlight(pi);
                    networkManager.logError(msg, NetworkTool.errorSortNetworks);
                }
            }
        }
        for (int i = 0; i < drawnWidths.length; i++) {
            if (drawnWidths[i] < 1) {
                drawnWidths[i] = 1;
            }
            if (NetworkTool.debug) {
                System.out.println("Drawn " + i + " " + (drawnNames[i] != null ? drawnNames[i].toString() : "") + " has width " + drawnWidths[i]);
            }
        }
    }

    // this method will not be called often because user will fix error, so it's not
    // very efficient.
    void reportDrawnWidthError(Export pp, ArcInst ai, String firstname, String badname) {
        // first occurrence is initial width which all subsequents are compared to
        int numPorts = cell.getNumPorts();
        int numArcs = cell.getNumArcs();

        String msg = "Network: Schematic " + cell + " has net with conflict width of names <"
                + firstname + "> and <" + badname + ">";
        System.out.println(msg);

        boolean originalFound = false;
        for (int i = 0; i < numPorts; i++) {
            String name = cell.getPort(i).getName();
            if (name.equals(firstname)) {
                networkManager.pushHighlight(cell.getPort(i));
                originalFound = true;
                break;
            }
        }
        if (!originalFound) {
            for (Iterator<ArcInst> it = cell.getArcs(); it.hasNext();) {
                ArcInst oai = it.next();
                String name = oai.getName();
                if (name.equals(firstname)) {
                    networkManager.pushHighlight(oai);
                    break;
                }
            }
        }
        if (ai != null) {
            networkManager.pushHighlight(ai);
        }
        if (pp != null) {
            networkManager.pushHighlight(pp);
        }
        networkManager.logError(msg, NetworkTool.errorSortNetworks);
    }

    @Override
    void addNetNames(Name name, Export e, ArcInst ai) {
        for (int i = 0; i < name.busWidth(); i++) {
            addNetName(name.subname(i), e, ai);
        }
    }

    private void localConnections(int netMap[]) {

        // Exports
        int numExports = cell.getNumPorts();
        for (int k = 0; k < numExports; k++) {
            Export e = cell.getPort(k);
            int portOffset = portOffsets[k];
            Name expNm = e.getNameKey();
            int busWidth = expNm.busWidth();
            int drawn = drawns[k];
            int drawnOffset = drawnOffsets[drawn];
            for (int i = 0; i < busWidth; i++) {
                Netlist.connectMap(netMap, portOffset + i, drawnOffset + (busWidth == drawnWidths[drawn] ? i : i % drawnWidths[drawn]));
                MutableInteger nn = netNames.get(expNm.subname(i));
                Netlist.connectMap(netMap, portOffset + i, netNamesOffset + nn.intValue());
            }
        }

        // PortInsts
        int numNodes = cell.getNumNodes();
        for (int k = 0; k < numNodes; k++) {
            NodeInst ni = cell.getNode(k);
            int nodeId = ni.getNodeId();
            NodeProto np = ni.getProto();
            if (!ni.isCellInstance()) {
                // Connect global primitives
                Global g = globalInst(ni);
                if (g != null) {
                    int drawn = drawns[ni_pi[nodeId]];
                    Netlist.connectMap(netMap, globals.indexOf(g), drawnOffsets[drawn]);
                }
                if (np == Schematics.tech().wireConNode) {
                    connectWireCon(netMap, ni);
                }
                continue;
            }
            IconInst iconInst = iconInsts[nodeId];
            if (iconInst == null || iconInst.iconOfParent) {
                continue;
            }
            Cell subCell = (Cell) np;
            assert subCell.isIcon() || subCell.isSchematic();
            EquivalentSchematicExports eq = iconInst.eq;
            Name nodeName = ni.getNameKey();
            int arraySize = nodeName.busWidth();
            int numPorts = np.getNumPorts();
            for (int m = 0; m < numPorts; m++) {
                Export e = (Export) np.getPort(m);
                Name busExportName = e.getNameKey();
                int busWidth = busExportName.busWidth();
                int drawn = drawns[ni_pi[nodeId] + m];
                if (drawn < 0) {
                    continue;
                }
                int width = drawnWidths[drawn];
                if (width != busWidth && width != busWidth * arraySize) {
                    continue;
                }
                assert arraySize == iconInst.numNodables;
                for (int arrayIndex = 0; arrayIndex < arraySize; arrayIndex++) {
                    int nodeOffset = iconInst.netMapOffset + arrayIndex * iconInst.numExtendedExports;
                    int busOffset = drawnOffsets[drawn];
                    if (width != busWidth) {
                        busOffset += busWidth * arrayIndex;
                    }
                    for (int j = 0; j < busWidth; j++) {
                        Name exportName = busExportName.subname(j);
                        int portOffset = eq.implementation.getExportNameMapOffset(exportName);
                        if (portOffset < 0) {
                            continue;
                        }
                        Netlist.connectMap(netMap, busOffset + j, nodeOffset + portOffset);
                    }
                }
            }
        }

        // Arcs
        int numArcs = cell.getNumArcs(), arcIndex = 0;
        for (Iterator<ArcInst> it = cell.getArcs(); arcIndex < numArcs; arcIndex++) {
            ArcInst ai = it.next();
            int drawn = drawns[arcsOffset + arcIndex];
            if (drawn < 0) {
                continue;
            }
            if (!ai.isUsernamed()) {
                continue;
            }
            int busWidth = drawnWidths[drawn];
            Name arcNm = ai.getNameKey();
            if (arcNm.busWidth() != busWidth) {
                continue;
            }
            int drawnOffset = drawnOffsets[drawn];
            for (int i = 0; i < busWidth; i++) {
                MutableInteger nn = netNames.get(arcNm.subname(i));
                Netlist.connectMap(netMap, drawnOffset + i, netNamesOffset + nn.intValue());
            }
        }

        // Globals of proxies
        for (Iterator<NodeInst> it = cell.getNodes(); it.hasNext();) {
            NodeInst ni = it.next();
            IconInst iconInst = iconInsts[ni.getNodeId()];
            if (iconInst == null || iconInst.iconOfParent) {
                continue;
            }
            Set<Global> excludeGlobals = null;
            if (iconInstExcludeGlobals != null) {
                excludeGlobals = iconInstExcludeGlobals.get(iconInst);
            }
            for (int k = 0; k < iconInst.numNodables; k++) {
                EquivalentSchematicExports eq = iconInst.eq.implementation;
                assert eq.implementation == eq;
                int numGlobals = eq.portOffsets[0];
                if (numGlobals == 0) {
                    continue;
                }
                int nodableOffset = iconInst.netMapOffset + k * iconInst.numExtendedExports;
                for (int i = 0; i < numGlobals; i++) {
                    Global g = eq.globals.get(i);
                    if (excludeGlobals != null && excludeGlobals.contains(g)) {
                        continue;
                    }
                    Netlist.connectMap(netMap, this.globals.indexOf(g), nodableOffset + i);
                }
            }
        }

        Netlist.closureMap(netMap);
    }

    private void pushName(Name name) {
        for (Iterator<Export> it = cell.getExports(); it.hasNext();) {
            Export e = it.next();
            Name n = e.getNameKey();
            for (int i = 0; i < n.busWidth(); i++) {
                if (n.subname(i) == name) {
                    networkManager.pushHighlight(e);
                    return;
                }
            }
        }
        for (Iterator<ArcInst> it = cell.getArcs(); it.hasNext();) {
            ArcInst ai = it.next();
            Name n = ai.getNameKey();
            for (int i = 0; i < n.busWidth(); i++) {
                if (n.subname(i) == name) {
                    networkManager.pushHighlight(ai);
                    return;
                }
            }
        }
    }

    private void connectWireCon(int[] netMap, NodeInst ni) {
        ArcInst ai1 = null;
        ArcInst ai2 = null;
        for (Iterator<Connection> it = ni.getConnections(); it.hasNext();) {
            Connection con = it.next();
            ArcInst ai = con.getArc();
            if (ai1 == null) {
                ai1 = ai;
            } else if (ai2 == null) {
                ai2 = ai;
            } else {
                String msg = "Network: Schematic " + cell + " has connector " + ni
                        + " which merges more than two arcs";
                System.out.println(msg);
                networkManager.pushHighlight(ni);
                networkManager.logError(msg, NetworkTool.errorSortNetworks);
                return;
            }
        }
        if (ai2 == null || ai1 == ai2) {
            return;
        }
        int large = getArcDrawn(ai1);
        int small = getArcDrawn(ai2);
        if (large < 0 || small < 0) {
            return;
        }
        if (drawnWidths[small] > drawnWidths[large]) {
            int temp = small;
            small = large;
            large = temp;
        }
        for (int i = 0; i < drawnWidths[large]; i++) {
            Netlist.connectMap(netMap, drawnOffsets[large] + i, drawnOffsets[small] + (i % drawnWidths[small]));
        }
    }

    private void internalConnections(int[] netMapF, int[] netMapP, int[] netMapA) {
        int numNodes = cell.getNumNodes();
        for (int k = 0; k < numNodes; k++) {
            NodeInst ni = cell.getNode(k);
            int nodeId = ni.getNodeId();
            int nodeOffset = ni_pi[nodeId];
            NodeProto np = ni.getProto();
            if (!ni.isCellInstance()) {
                PrimitiveNode.Function fun = ni.getFunction();
                if (fun == PrimitiveNode.Function.RESIST) {
                	assert drawns.length > 1;
                    Netlist.connectMap(netMapP, drawnOffsets[drawns[nodeOffset]], drawnOffsets[drawns[nodeOffset + 1]]);
                    Netlist.connectMap(netMapA, drawnOffsets[drawns[nodeOffset]], drawnOffsets[drawns[nodeOffset + 1]]);
                } else if (fun.isComplexResistor()) {
                	assert drawns.length > 1;
                    Netlist.connectMap(netMapA, drawnOffsets[drawns[nodeOffset]], drawnOffsets[drawns[nodeOffset + 1]]);
                }
                continue;
            }
            IconInst iconInst = iconInsts[nodeId];
            if (iconInst != null) {
                continue;
            }
            Cell subCell = (Cell) np;
            assert !(subCell.isIcon() || subCell.isSchematic());
            EquivPorts eq = cell.tree().getEquivPorts();
            int[] eqN = eq.getEquivPortsN();
            int[] eqP = eq.getEquivPortsP();
            int[] eqA = eq.getEquivPortsA();
            int numPorts = eq.getNumExports();
            assert eqN.length == numPorts && eqP.length == numPorts && eqA.length == numPorts;
            for (int i = 0; i < numPorts; i++) {
                int di = drawns[nodeOffset + i];
                if (di < 0) {
                    continue;
                }
                int jN = eqN[i];
                if (i != jN) {
                    int dj = drawns[nodeOffset + jN];
                    if (dj >= 0) {
                        Netlist.connectMap(netMapF, drawnOffsets[di], drawnOffsets[dj]);
                    }
                }
                int jP = eqP[i];
                if (i != jP) {
                    int dj = drawns[nodeOffset + jP];
                    if (dj >= 0) {
                        Netlist.connectMap(netMapP, drawnOffsets[di], drawnOffsets[dj]);
                    }
                }
                int jA = eqA[i];
                if (i != jA) {
                    int dj = drawns[nodeOffset + jA];
                    if (dj >= 0) {
                        Netlist.connectMap(netMapA, drawnOffsets[di], drawnOffsets[dj]);
                    }
                }
            }
        }
        for (Iterator<NodeInst> it = cell.getNodes(); it.hasNext();) {
            NodeInst ni = it.next();
            IconInst iconInst = iconInsts[ni.getNodeId()];
            if (iconInst == null || iconInst.iconOfParent) {
                continue;
            }
            for (int k = 0; k < iconInst.numNodables; k++) {
                EquivalentSchematicExports eq = iconInst.eq.implementation;
                assert eq.implementation == eq;
                int[] eqN = eq.getEquivPortsN();
                int[] eqP = eq.getEquivPortsP();
                int[] eqA = eq.getEquivPortsA();
                int nodableOffset = iconInst.netMapOffset + k * iconInst.numExtendedExports;
                for (int i = 0; i < eqN.length; i++) {
                    int io = nodableOffset + i;

                    int jF = eqN[i];
                    if (i != jF) {
                        Netlist.connectMap(netMapF, io, nodableOffset + jF);
                    }

                    int jP = eqP[i];
                    if (i != jP) {
                        Netlist.connectMap(netMapP, io, nodableOffset + jP);
                    }

                    int jA = eqA[i];
                    if (i != jA) {
                        Netlist.connectMap(netMapA, io, nodableOffset + jA);
                    }
                }
            }
        }
    }

    private void buildNetworkLists(int[] netMapF) {
        netlistN = new NetlistImpl(this, equivPortsN.length, netMapF);
        int equivPortIndex = 0;
        for (int i = 0; i < globals.size(); i++) {
            Global global = globals.get(i);
            int netIndex = netlistN.getNetIndex(global);
            netlistN.addUserName(netIndex, global.getNameKey(), true);
            netlistN.setEquivPortIndexByNetIndex(equivPortIndex++, netIndex);
        }
        for (Iterator<Export> it = cell.getExports(); it.hasNext();) {
            Export e = it.next();
            for (int busIndex = 0; busIndex < e.getNameKey().busWidth(); busIndex++) {
                netlistN.setEquivPortIndexByNetIndex(equivPortIndex++, netlistN.getNetIndex(e, busIndex));
            }
        }

        for (Map.Entry<Name, MutableInteger> e : netNames.entrySet()) {
            Name name = e.getKey();
            int index = e.getValue().intValue();
            if (index < 0 || index >= exportedNetNameCount) {
                continue;
            }
            netlistN.addUserName(netlistN.getNetIndexByMap(netNamesOffset + index), name, true);
        }
        for (Map.Entry<Name, MutableInteger> e : netNames.entrySet()) {
            Name name = e.getKey();
            int index = e.getValue().intValue();
            if (index < exportedNetNameCount) {
                continue;
            }
            netlistN.addUserName(netlistN.getNetIndexByMap(netNamesOffset + index), name, false);
        }

        // add temporary names to unnamed nets
        int numArcs = cell.getNumArcs(), arcIndex = 0;
        for (Iterator<ArcInst> it = cell.getArcs(); arcIndex < numArcs; arcIndex++) {
            ArcInst ai = it.next();
            int drawn = drawns[arcsOffset + arcIndex];
            if (drawn < 0) {
                continue;
            }
            for (int j = 0; j < drawnWidths[drawn]; j++) {
                int netIndexN = netlistN.getNetIndex(ai, j);
                if (netIndexN >= 0 && netlistN.hasNames(netIndexN)) {
                    netIndexN = -1;
                }

                if (netIndexN < 0) {
                    continue;
                }
                if (drawnNames[drawn] == null) {
                    continue;
                }
                String netName;
                if (drawnWidths[drawn] == 1) {
                    netName = drawnNames[drawn].toString();
                } else if (drawnNames[drawn].isTempname()) {
                    int busIndex = NetworkTool.isBusAscendingInNetlistEngine() ? j : drawnWidths[drawn] - 1 - j;
                    netName = drawnNames[drawn].toString() + "[" + busIndex + "]";
                } else {
                    netName = drawnNames[drawn].subname(j).toString();
                }

                if (netIndexN >= 0) {
                    netlistN.addTempName(netIndexN, netName);
                }
            }
        }

        // add temporary names to unconnected ports
        for (Iterator<Nodable> it = getNodables(); it.hasNext();) {
            Nodable no = it.next();
            NodeProto np = no.getProto();
            for (int i = 0, numPorts = np.getNumPorts(); i < numPorts; i++) {
                PortProto pp = np.getPort(i);
                for (int k = 0, busWidth = pp.getNameKey().busWidth(); k < busWidth; k++) {
                    int netIndexN = netlistN.getNetIndex(no, pp, k);
                    if (netIndexN >= 0 && !netlistN.hasNames(netIndexN)) {
                        netlistN.addTempName(netIndexN, no.getName() + "." + pp.getNameKey().subname(k));
                    }
                }
            }
        }

        // add temporary names to unconnected ports
        for (int n = 0, numNodes = cell.getNumNodes(); n < numNodes; n++) {
            NodeInst ni = cell.getNode(n);
            int nodeId = ni.getNodeId();
            NodeProto np = ni.getProto();
            int arraySize = ni.getNameKey().busWidth();
            for (int i = 0, numPorts = np.getNumPorts(); i < numPorts; i++) {
                PortProto pp = np.getPort(i);
                int drawn = drawns[ni_pi[nodeId] + i];
                if (drawn < 0) {
                    continue;
                }
                int busWidth = pp.getNameKey().busWidth();
                int drawnWidth = drawnWidths[drawn];
                for (int l = 0; l < drawnWidth; l++) {
                    int netIndexN = netlistN.getNetIndexByMap(drawnOffsets[drawn] + l);
                    if (netIndexN >= 0 && !netlistN.hasNames(netIndexN)) {
                        int arrayIndex = (l / busWidth) % arraySize;
                        int busIndex = l % busWidth;
                        netlistN.addTempName(netIndexN, ni.getNameKey().subname(arrayIndex) + "." + pp.getNameKey().subname(busIndex));
                    }
                }
            }
        }

        // check names and equivPortIndexByNetIndex map
        for (int i = 0, numNetworks = netlistN.getNumNetworks(); i < numNetworks; i++) {
            assert netlistN.hasNames(i);
            assert netlistN.isExported(i) == (i < netlistN.getNumExternalNetworks());
            if (netlistN.isExported(i)) {
                int equivPortInd = netlistN.getEquivPortIndexByNetIndex(i);
                assert equivPortInd >= 0 && equivPortInd < equivPortIndex;
            }
        }

        /*
         // debug info
         System.out.println("BuildNetworkList "+cell);
         for (int kk = 0; kk < 2; kk++) {
         Netlist netlist;
         if (kk == 0) {
         netlist = netlistF;
         System.out.println("NetlistF");
         } else {
         netlist = netlistT;
         System.out.println("NetlistT");
         }
         int i = 0;
         for (int l = 0; l < netlist.networks.length; l++) {
         Network network = netlist.networks[l];
         if (network == null) continue;
         String s = "";
         for (Iterator<String> sit = network.getNames(); sit.hasNext(); )
         {
         String n = sit.next();
         s += "/"+ n;
         }
         System.out.println("    "+i+"    "+s);
         i++;
         for (int k = 0; k < globals.size(); k++) {
         if (netlist.nm_net[netlist.netMap[k]] != l) continue;
         System.out.println("\t" + globals.get(k));
         }
         int numPorts = cell.getNumPorts();
         for (int k = 0; k < numPorts; k++) {
         Export e = (Export) cell.getPort(k);
         for (int j = 0; j < e.getNameKey().busWidth(); j++) {
         if (netlist.nm_net[netlist.netMap[portOffsets[k] + j]] != l) continue;
         System.out.println("\t" + e + " [" + j + "]");
         }
         }
         for (int k = 0; k < numDrawns; k++) {
         for (int j = 0; j < drawnWidths[k]; j++) {
         int ind = drawnOffsets[k] + j;
         int netInd = netlist.netMap[ind];
         if (netlist.nm_net[netlist.netMap[drawnOffsets[k] + j]] != l) continue;
         System.out.println("\tDrawn " + k + " [" + j + "]");
         }
         }
         for (Iterator<NetName> it = netNames.values().iterator(); it.hasNext();) {
         NetName nn = it.next();
         if (netlist.nm_net[netlist.netMap[netNamesOffset + nn.index]] != l) continue;
         System.out.println("\tNetName " + nn.name);
         }
         }
         }
         */
    }

    /**
     * Update map of equivalent ports newEquivPort.
     */
    private boolean updateInterface() {
        boolean changed = false;
        for (int i = 0; i < equivPortsN.length; i++) {
            if (equivPortsN[i] != netlistN.netMap[i]) {
                changed = true;
                equivPortsN[i] = netlistN.netMap[i];
            }
            if (equivPortsP[i] != netlistP.netMap[i]) {
                changed = true;
                equivPortsP[i] = netlistP.netMap[i];
            }
            if (equivPortsA[i] != netlistA.netMap[i]) {
                changed = true;
                equivPortsA[i] = netlistA.netMap[i];
            }
        }
        return changed;
    }

    void updateSchematic() {
        synchronized (networkManager) {
            Snapshot oldSnapshot = expectedSnapshot.get();
            Snapshot newSnapshot = database.backup();
            if (oldSnapshot == newSnapshot) {
                return;
            }
            CellId cellId = cell.getId();
            assert cellId.isIcon() || cellId.isSchematic();
            // Check that old Netlists are still valid
            if (cellTree != newSnapshot.getCellTree(cellId)
                    || mainSchemId != newSnapshot.getMainSchematics(cellId)
                    || mainSchemId != cellId && mainSchemId != null && !eqExports.get(mainSchemId).equals(newSnapshot.getEquivExports(mainSchemId))
                    || !sameEqivPortsOfSubcells(newSnapshot, cell.getId())) {

                // clear errors for cell
                networkManager.startErrorLogging(cell);
                try {
                    exportNameMapOffsets = null;
                    makeDrawns();
                    // Gather port and arc names
                    initNetnames();

                    redoNetworks1();
                } finally {
                    networkManager.finishErrorLogging();
                }

                // Save info taht will help this this Netlists is still valid
                cellTree = newSnapshot.getCellTree(cellId);
                mainSchemId = newSnapshot.getMainSchematics(cellId);
                eqExports = new IdentityHashMap<CellId, EquivalentSchematicExports>();
                if (mainSchemId != null) {
                    eqExports.put(mainSchemId, newSnapshot.getEquivExports(mainSchemId));
                }
                for (CellTree subTree : cellTree.getSubTrees()) {
                    if (subTree == null) {
                        continue;
                    }
                    CellId subCellId = subTree.top.cellRevision.d.cellId;
                    if (subCellId.isIcon() || subCellId.isSchematic()) {
                        eqExports.put(subCellId, newSnapshot.getEquivExports(subCellId));
                    }
                }
            }
            expectedSnapshot = new WeakReference<Snapshot>(newSnapshot);
        }
    }

    private boolean sameEqivPortsOfSubcells(Snapshot newSnapshot, CellId cellId) {
        for (CellTree subTree : newSnapshot.getCellTree(cellId).getSubTrees()) {
            if (subTree == null) {
                continue;
            }
            CellId subCellId = subTree.top.cellRevision.d.cellId;
            if (subCellId.isIcon()) {
                if (cellId.isSchematic() && newSnapshot.getCellGroupIndex(cellId) == newSnapshot.getCellGroupIndex(subCellId)) {
                    // Icon of parent
                    continue;
                }
            } else if (!subCellId.isSchematic()) {
                continue;
            }
            if (!newSnapshot.getEquivExports(subCellId).equals(eqExports.get(subCellId))) {
                return false;
            }
        }
        return true;
    }

    @Override
    boolean redoNetworks1() {
//		System.out.println("redoNetworks1 on " + cell);
        int numPorts = cell.getNumPorts();
        if (portOffsets.length != numPorts + 1) {
            portOffsets = new int[numPorts + 1];
        }

        /* Set index of NodeInsts */
        if (drawnNames == null || drawnNames.length != numDrawns) {
            drawnNames = new Name[numDrawns];
            drawnWidths = new int[numDrawns];
            drawnOffsets = new int[numDrawns];
        }
        calcDrawnWidths();

        boolean changed = initNodables();
        // Gather port and arc names
        int mapSize = netNamesOffset + netNames.size();
//        HashMap/*<Cell,Netlist>*/ subNetlistsF = new HashMap/*<Cell,Netlist>*/();
//        for (Iterator it = getNodables(); it.hasNext(); ) {
//            Nodable no = it.next();
//            if (!no.isCellInstance()) continue;
//            Cell subCell = (Cell)no.getProto();
//            subNetlistsF.put(subCell, networkManager.getNetlist(subCell, false));
//        }
        int[] netMapF = Netlist.initMap(mapSize);
        localConnections(netMapF);

//        HashMap/*<Cell,Netlist>*/ subNetlistsT = new HashMap/*<Cell,Netlist>*/();
//        for (Iterator<Nodable> it = getNodables(); it.hasNext(); ) {
//            Nodable no = it.next();
//            if (!no.isCellInstance()) continue;
//            Cell subCell = (Cell)no.getProto();
//            subNetlistsT.put(subCell, networkManager.getNetlist(subCell, true));
//        }
        int[] netMapP = netMapF.clone();
        int[] netMapA = netMapF.clone();
        internalConnections(netMapF, netMapP, netMapA);
        buildNetworkLists(netMapF);
        assert equivPortsP.length == equivPortsN.length;
        netlistP = new NetlistShorted(netlistN, Netlist.ShortResistors.PARASITIC, netMapP);
        assert equivPortsA.length == equivPortsN.length;
        netlistA = new NetlistShorted(netlistN, Netlist.ShortResistors.ALL, netMapA);
//        if (updatePortImplementation()) {
//            changed = true;
//        }
        if (updateInterface()) {
            changed = true;
        }
        if (Job.getDebug()) {
            EquivalentSchematicExports eq = cell.getDatabase().backup().getEquivExports(cell.getId());
//            assert Arrays.equals(ni_pi, netSchem.ni_pi);
//            assert arcsOffset == netSchem.arcsOffset;
//            assert Arrays.equals(drawns, netSchem.drawns);
//            assert Arrays.equals(drawnWidths, netSchem.drawnWidths);
//            assert Arrays.equals(drawnNames, netSchem.drawnNames);
//            assert Arrays.equals(drawnOffsets, netSchem.drawnOffsets);
//            assert Arrays.equals(nodeOffsets, netSchem.nodeOffsets);

            assert globals.equals(eq.getGlobals());
//            assert Arrays.equals(portOffsets, netSchem.portOffsets);
            assert Arrays.equals(equivPortsN, eq.getEquivPortsN());
            assert Arrays.equals(equivPortsP, eq.getEquivPortsP());
            assert Arrays.equals(equivPortsA, eq.getEquivPortsA());
        }
        return changed;
    }

    /**
     * Update netlists to current Snapshot
     * Check if specified Netlist is no more fresh
     * @param netlist
     * @return true specified Netlist is no more fresh
     */
    @Override
    boolean obsolete(Netlist netlist) {
        Netlist newNetlist = getNetlist(netlist.shortResistors);
        netlistN.expectedSnapshot = netlistP.expectedSnapshot = netlistA.expectedSnapshot = expectedSnapshot;
        return newNetlist != netlist;
    }
}
