/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: ImmutableNetLayout.java
 * Written by: Dmitry Nadezhin.
 *
 * Copyright (c) 2011, Static Free Software. All rights reserved.
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
package com.sun.electric.database;

import com.sun.electric.database.id.CellId;
import com.sun.electric.database.id.CellUsage;
import com.sun.electric.database.id.ExportId;
import com.sun.electric.database.id.NodeProtoId;
import com.sun.electric.database.id.PortProtoId;
import com.sun.electric.database.id.PrimitiveNodeId;
import com.sun.electric.database.id.PrimitivePortId;
import com.sun.electric.technology.ArcProto;
import com.sun.electric.technology.PrimitiveNode;
import com.sun.electric.technology.PrimitivePort;
import com.sun.electric.technology.TechPool;
import com.sun.electric.technology.technologies.Artwork;
import com.sun.electric.technology.technologies.Generic;
import com.sun.electric.technology.technologies.Schematics;
import com.sun.electric.util.collections.ImmutableArrayList;

import java.io.PrintStream;

/**
 *
 */
public class ImmutableNet {

    private static boolean DEBUG = false;
    final CellTree cellTree;
    final TechPool techPool;
    final Schematics schemTech;
    final PrimitivePortId busPinPortId;
    final ArcProto busArc;
    final CellBackup cellBackup;
    final CellRevision cellRevision;
    final CellId cellId;
    final ImmutableExport.Iterable exports;
    final ImmutableNodeInst.Iterable nodes;
    final ImmutableArcInst.Iterable arcs;
    final int numExports;
    final int numNodes;
    final int numArcs;
    /** Node offsets. */
    final int[] ni_pi;
    final int arcsOffset;
    final int[] drawns;
    public final int numDrawns;
    public final int numExportedDrawns;
    public final int numConnectedDrawns;

    public ImmutableNet(CellTree cellTree) {
        this.cellTree = cellTree;
        techPool = cellTree.techPool;
        Generic genericTech = techPool.getGeneric();
        Artwork artworkTech = techPool.getArtwork();
        schemTech = techPool.getSchematics();
        PrimitiveNode invisiblePinNode = genericTech != null ? genericTech.invisiblePinNode : null;
        PrimitiveNode simProbeNode = genericTech != null ? genericTech.simProbeNode : null;
        PrimitiveNode pinNode = artworkTech != null ? artworkTech.pinNode : null;
        busPinPortId = schemTech != null ? schemTech.busPinNode.getPort(0).getId() : null;
        busArc = schemTech != null ? schemTech.bus_arc : null;
        cellBackup = cellTree.top;
        cellRevision = cellBackup.cellRevision;
        exports = cellRevision.exports;
        nodes = cellRevision.nodes;
        arcs = cellRevision.arcs;
        numExports = cellRevision.exports.size();
        numNodes = cellRevision.nodes.size();
        numArcs = cellRevision.arcs.size();
        cellId = cellRevision.d.cellId;

        ni_pi = new int[numNodes];
        int offset = numExports;
        for (int nodeIndex = 0; nodeIndex < numNodes; nodeIndex++) {
            ImmutableNodeInst n = nodes.get(nodeIndex);
            ni_pi[nodeIndex] = offset;
            offset += getNumPorts(n.protoId);
        }
        arcsOffset = offset;

        drawns = initMap(arcsOffset + numArcs);
        // Connect exports to their original ports
        for (int exportIndex = 0; exportIndex < cellRevision.exports.size(); exportIndex++) {
            ImmutableExport e = cellRevision.exports.get(exportIndex);
            if (!isIsolated(e.originalPortId)) {
                connectMap(drawns, exportIndex, mapIndex(e.originalNodeId, e.originalPortId));
                if (DEBUG) {
                    System.err.println("Connect export " + exportIndex + " " + mapIndex(e.originalNodeId, e.originalPortId));
                }
            }
        }

        // Connect arcs to their ends
        for (int arcIndex = 0; arcIndex < numArcs; arcIndex++) {
            ImmutableArcInst a = cellRevision.arcs.get(arcIndex);
            ArcProto ap = techPool.getArcProto(a.protoId);
            if (ap.getFunction() == ArcProto.Function.NONELEC) {
                continue;
            }
            if (!(isIsolated(a.tailPortId) || a.tailPortId == busPinPortId && ap != busArc)) {
                connectMap(drawns, arcsOffset + arcIndex, mapIndex(a.tailNodeId, a.tailPortId));
                if (DEBUG) {
                    System.err.println("Connect tail " + (arcsOffset + arcIndex) + " " + mapIndex(a.tailNodeId, a.tailPortId));
                }
            }
            if (!(isIsolated(a.headPortId) || a.headPortId == busPinPortId && ap != busArc)) {
                connectMap(drawns, arcsOffset + arcIndex, mapIndex(a.headNodeId, a.headPortId));
                if (DEBUG) {
                    System.err.println("Connect head " + (arcsOffset + arcIndex) + " " + mapIndex(a.headNodeId, a.headPortId));
                }
            }
        }

        // Internal connections on primitive nodes
        for (int nodeIndex = 0; nodeIndex < numNodes; nodeIndex++) {
            ImmutableNodeInst n = cellRevision.nodes.get(nodeIndex);
            if (!(n.protoId instanceof PrimitiveNodeId)) {
                continue;
            }
            PrimitiveNode pn = techPool.getPrimitiveNode((PrimitiveNodeId) n.protoId);
            if (pn.getNumPorts() <= 1) {
                continue;
            }
            int mapOffset = ni_pi[nodeIndex];
            for (int i = 1; i < pn.getNumPorts(); i++) {
                PrimitivePort ppi = pn.getPort(i);
                for (int j = 0; j < i; j++) {
                    PrimitivePort ppj = pn.getPort(j);
                    if (ppi.getTopology() == ppj.getTopology()) {
                        assert !ppi.isIsolated() && !ppj.isIsolated();
                        connectMap(drawns, mapOffset + i, mapOffset + j);
                        if (DEBUG) {
                            System.err.println("Connect topology " + (mapOffset + i) + " " + (mapOffset + j));
                        }
                    }
                }
            }
        }

        if (DEBUG) {
            printDrawns(System.err, "before closure");
        }
        closureMap(drawns);
        if (DEBUG) {
            printDrawns(System.err, "after closure");
        }

        int curDrawn = 0;
        // Register drawns on exports
        for (int exportIndex = 0; exportIndex < cellRevision.exports.size(); exportIndex++) {
            if (addDrawn(exportIndex, curDrawn)) {
                curDrawn++;
            }
        }
        numExportedDrawns = curDrawn;

        // Register drawns on arcs
        for (int arcIndex = 0; arcIndex < numArcs; arcIndex++) {
            ImmutableArcInst a = cellRevision.arcs.get(arcIndex);
            ArcProto ap = techPool.getArcProto(a.protoId);
            if (ap.getFunction() == ArcProto.Function.NONELEC) {
                nullDrawn(arcsOffset + arcIndex);
            } else if (addDrawn(arcsOffset + arcIndex, curDrawn)) {
                curDrawn++;
            }
        }
        numConnectedDrawns = curDrawn;

        // Register drawns on port insts
        for (int nodeIndex = 0; nodeIndex < numNodes; nodeIndex++) {
            ImmutableNodeInst n = cellRevision.nodes.get(nodeIndex);
            int mapOffset = ni_pi[nodeIndex];
            if (n.protoId instanceof CellId) {
                if (!isIconOfParent(n)) {
                    for (int portIndex = 0, numPortInsts = getNumPorts(n.protoId); portIndex < numPortInsts; portIndex++) {
                        if (addDrawn(mapOffset + portIndex, curDrawn)) {
                            curDrawn++;
                        }
                    }
                    continue;
                }
            } else {
                PrimitiveNode pn = techPool.getPrimitiveNode((PrimitiveNodeId) n.protoId);
                if (!(pn.getFunction() == PrimitiveNode.Function.ART && pn != simProbeNode
                        || pn == pinNode
                        || pn == invisiblePinNode)) {
                    for (int portIndex = 0, numPortInsts = getNumPorts(n.protoId); portIndex < numPortInsts; portIndex++) {
                        if (pn.getPort(portIndex).isIsolated()) {
                            nullDrawn(mapOffset + portIndex);
                        } else if (addDrawn(mapOffset + portIndex, curDrawn)) {
                            curDrawn++;
                        }
                    }
                    continue;
                }
            }
            // Don't make drawns for unconnected port insts in this case
            for (int portIndex = 0, numPortInsts = getNumPorts(n.protoId); portIndex < numPortInsts; portIndex++) {
                addDrawn(mapOffset + portIndex, -1);
            }
        }
        numDrawns = curDrawn;

        for (int i = 0; i < drawns.length; i++) {
            int d = drawns[i];
            assert d < 0;
            drawns[i] = -2 - d;
        }

        if (DEBUG) {
            printDrawns(System.err, "at the end");
        }
    }

    public void printDrawns(PrintStream out, String msg) {
        out.println("Drawns of " + cellId + " " + msg);
        for (int exportIndex = 0; exportIndex < numExports; exportIndex++) {
            ImmutableExport e = exports.get(exportIndex);
            out.println("Export " + e.name + " " + getDrawn(e.exportId) + " " + drawns[exportIndex]);
        }
        for (int nodeIndex = 0; nodeIndex < numNodes; nodeIndex++) {
            ImmutableNodeInst n = nodes.get(nodeIndex);
            int mapOffset = ni_pi[nodeIndex];
            for (int portIndex = 0, numPortInsts = getNumPorts(n.protoId); portIndex < numPortInsts; portIndex++) {
                if (n.protoId instanceof CellId) {
                    ImmutableExport e = getSubTree((CellId) n.protoId).top.cellRevision.exports.get(portIndex);
                    out.println("PortInst " + n.name + " " + e.exportId.parentId + ":" + e.name + " " + getDrawn(n, e.exportId) + " " + drawns[mapOffset + portIndex]);
                } else {
                    PrimitivePort p = techPool.getPrimitiveNode((PrimitiveNodeId) n.protoId).getPort(portIndex);
                    out.println("PortInst " + n.name + " " + n.protoId + ":" + p.getName() + " " + getDrawn(n, p.getId()) + " " + drawns[mapOffset + portIndex]);
                }
            }
        }
        for (int arcIndex = 0; arcIndex < numArcs; arcIndex++) {
            ImmutableArcInst a = arcs.get(arcIndex);
            out.println("Arc " + a.name + " " + getDrawn(a) + " " + drawns[arcsOffset + arcIndex]);
        }
    }

    public int getDrawn(ExportId exportId) {
        assert exportId.parentId == cellId;
        int exportIndex = cellRevision.getExportIndexByExportId(exportId);
        assert exports.get(exportIndex).exportId == exportId;
        return drawns[exportIndex];
    }

    public int getDrawn(ImmutableNodeInst n, PortProtoId portId) {
        int nodeIndex = nodeIndexByNodeId(n.nodeId);
        assert nodes.get(nodeIndex) == n;
        return drawns[mapIndex(n.nodeId, portId)];
    }

    public int getDrawn(ImmutableArcInst a) {
        int arcIndex = arcIndexByArcId(a.arcId);
        assert arcs.get(arcIndex) == a;
        return drawns[arcsOffset + arcIndex];
    }

    private void nullDrawn(int mapIndex) {
        assert drawns[mapIndex] == mapIndex;
        drawns[mapIndex] = -1;
    }

    private boolean addDrawn(int mapIndex, int drawn) {
        if (drawns[mapIndex] < 0) {
            return false;
        }
        int baseIndex = drawns[mapIndex];
        int baseDrawn = drawns[baseIndex];
        if (baseDrawn < 0) {
            drawns[mapIndex] = baseDrawn;
            return false;
        }
        drawns[mapIndex] = drawns[baseIndex] = -2 - drawn;
        return true;
    }

    private int mapIndex(int nodeId, PortProtoId portId) {
        return ni_pi[nodeIndexByNodeId(nodeId)] + getPortIndex(portId);
    }

    int nodeIndexByNodeId(int nodeId) {
        return cellRevision.getNodeIndexByNodeId(nodeId);
    }

    int arcIndexByArcId(int arcId) {
        return cellRevision.getArcIndexByArcId(arcId);
    }

    int getNumPorts(NodeProtoId nodeProtoId) {
        if (nodeProtoId instanceof CellId) {
            return getSubTree((CellId) nodeProtoId).top.cellRevision.exports.size();
        } else {
            return techPool.getPrimitiveNode((PrimitiveNodeId) nodeProtoId).getNumPorts();
        }
    }

    int getPortIndex(PortProtoId portId) {
        if (portId instanceof ExportId) {
            return getSubTree((CellId) portId.parentId).top.cellRevision.getExportIndexByExportId((ExportId) portId);
        } else {
            return techPool.getPrimitivePort((PrimitivePortId) portId).getPortIndex();
        }
    }

    /**
     * Method to tell whether this NodeInst is an icon of its parent.
     * Electric does not allow recursive circuit hierarchies (instances of Cells inside of themselves).
     * However, it does allow one exception: a schematic may contain its own icon for documentation purposes.
     * This method determines whether this NodeInst is such an icon.
     * @return true if this NodeInst is an icon of its parent.
     */
    boolean isIconOfParent(ImmutableNodeInst n) {
        if (!(n.protoId instanceof CellId)) {
            return false;
        }
        CellBackup subCell = getSubTree((CellId) n.protoId).top;
        CellId subCellId = subCell.cellRevision.d.cellId;
        return subCellId.isIcon() && cellId.isSchematic() && subCellId.libId == cellId.libId
                && subCell.cellRevision.d.groupName.equals(cellRevision.d.groupName);
    }

    private CellTree getSubTree(CellId cellId) {
        CellUsage cu = cellTree.top.cellRevision.d.cellId.getUsageIn(cellId);
        return cellTree.subTrees[cu.indexInParent];
    }

    private boolean isIsolated(PortProtoId portId) {
        return (portId instanceof PrimitivePortId && techPool.getPrimitivePort((PrimitivePortId) portId).isIsolated());
    }

    /**
     * Init equivalence map.
     * @param size numeber of elements in equivalence map
     * @return integer array representing equivalence map consisting of disjoint elements.
     */
    static int[] initMap(int size) {
        int[] map = new int[size];
        for (int i = 0; i < map.length; i++) {
            map[i] = i;
        }
        return map;
    }

    /**
     * Merge classes of equivalence map to which elements a1 and a2 belong.
     * Returns true if the classes were different
     */
    static boolean connectMap(int[] map, int a1, int a2) {
        int m1, m2, m;

        for (m1 = a1; map[m1] != m1; m1 = map[m1]);
        for (m2 = a2; map[m2] != m2; m2 = map[m2]);
        boolean changed = m1 != m2;
        m = m1 < m2 ? m1 : m2;

        for (;;) {
            int k = map[a1];
            map[a1] = m;
            if (a1 == k) {
                break;
            }
            a1 = k;
        }
        for (;;) {
            int k = map[a2];
            map[a2] = m;
            if (a2 == k) {
                break;
            }
            a2 = k;
        }
        return changed;
    }

    /**
     * Obtain canonical representation of equivalence map.
     */
    static void closureMap(int[] map) {
        for (int i = 0; i < map.length; i++) {
            map[i] = map[map[i]];
        }
    }
}
