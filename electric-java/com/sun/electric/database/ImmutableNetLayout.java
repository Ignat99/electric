/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: ImmutableNetLayout.java
 * Written by: Dmitry Nadezhin.
 *
 * Copyright (c) 2009, Static Free Software. All rights reserved.
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
import com.sun.electric.database.id.NodeProtoId;
import com.sun.electric.database.id.PortProtoId;
import com.sun.electric.database.id.PrimitiveNodeId;
import com.sun.electric.database.network.Netlist;
import com.sun.electric.technology.ArcProto;
import com.sun.electric.technology.PrimitiveNode;
import com.sun.electric.technology.PrimitivePort;
import com.sun.electric.util.collections.ArrayIterator;

import java.util.Arrays;
import java.util.IdentityHashMap;

/**
 *
 */
public class ImmutableNetLayout extends ImmutableNet {

    /**
     * Equivalence of ports.
     * equivPorts.size == ports.size.
     * equivPorts[i] contains minimal index among ports of its group.
     */
    final int[] equivPortsN;
    final int[] equivPortsP;
    final int[] equivPortsA;
    private final IdentityHashMap<NodeProtoId, NodeProtoInfo> nodeProtoInfos = new IdentityHashMap<NodeProtoId, NodeProtoInfo>();
    private final IdentityHashMap<PortProtoId, Void> isolatedPorts = new IdentityHashMap<PortProtoId, Void>();
    /** */
    final int[] netMap;

    public ImmutableNetLayout(CellTree cellTree) {
        super(cellTree);

        // initNetMap
        int nodeBase = numExports;
        for (ImmutableNodeInst n : cellRevision.nodes) {
//            ni_pi[nodeIndexByNodeId(n.nodeId)] = nodeBase;
            NodeProtoInfo npi = nodeProtoInfos.get(n.protoId);
            if (npi == null) {
                npi = new NodeProtoInfo(cellTree, n.protoId);
                nodeProtoInfos.put(n.protoId, npi);
                if (n.protoId instanceof PrimitiveNodeId) {
                    PrimitiveNode pn = techPool.getPrimitiveNode((PrimitiveNodeId) n.protoId);
                    if (pn.getTechnology() == schemTech) {
                        for (PrimitivePort pp : ArrayIterator.i2i(pn.getPrimitivePorts())) {
                            if (pp.isIsolated()) {
                                isolatedPorts.put(pp.getId(), null);
                            }
                        }
                    }
                }
            }
            nodeBase += npi.numPorts;
        }
        netMap = initMap(nodeBase);

        connectExports();
        connectArcs();

        equivPortsN = computePortsN();
        equivPortsP = computePortsP();
        equivPortsA = computePortsA();

        int[] drawnMap = initMap(numExports + numDrawns);
        for (int exportIndex = 0; exportIndex < numExports; exportIndex++) {
            connectMap(drawnMap, exportIndex, numExports + drawns[exportIndex]);
        }

        for (int nodeIndex = 0; nodeIndex < numNodes; nodeIndex++) {
            ImmutableNodeInst n = nodes.get(nodeIndex);
            NodeProtoInfo npi = nodeProtoInfos.get(n.protoId);
            if (npi.numPorts == 1) {
                continue;
            }
            connectInternals(drawnMap, numExports, ni_pi[nodeIndex], npi.equivPortsN);
        }
        checkEquivPorts(equivPortsN, drawnMap);

        for (int nodeIndex = 0; nodeIndex < numNodes; nodeIndex++) {
            ImmutableNodeInst n = nodes.get(nodeIndex);
            NodeProtoInfo npi = nodeProtoInfos.get(n.protoId);
            if (npi.numPorts == 1 || npi.equivPortsP == npi.equivPortsN) {
                continue;
            }
            connectInternals(drawnMap, numExports, ni_pi[nodeIndex], npi.equivPortsP);
        }
        checkEquivPorts(equivPortsP, drawnMap);

        for (int nodeIndex = 0; nodeIndex < numNodes; nodeIndex++) {
            ImmutableNodeInst n = nodes.get(nodeIndex);
            NodeProtoInfo npi = nodeProtoInfos.get(n.protoId);
            if (npi.numPorts == 1 || npi.equivPortsA == npi.equivPortsP) {
                continue;
            }
            connectInternals(drawnMap, numExports, ni_pi[nodeIndex], npi.equivPortsA);
        }
        checkEquivPorts(equivPortsA, drawnMap);
    }

    private void checkEquivPorts(int[] equivPorts, int[] drawnMap) {
        closureMap(drawnMap);
        assert equivPorts.length == numExports;
        for (int exportIndex = 0; exportIndex < equivPorts.length; exportIndex++) {
            assert equivPorts[exportIndex] == drawnMap[exportIndex];
        }
    }

    private int[] computePortsN() {
        for (ImmutableNodeInst n : cellRevision.nodes) {
            NodeProtoInfo npi = nodeProtoInfos.get(n.protoId);
            if (npi.numPorts == 1) {
                continue;
            }
            connectInternals(n, npi.equivPortsN);
        }
        return equivMap(null);
    }

    private int[] computePortsP() {
        PrimitiveNodeId schemResistor = schemTech != null ? schemTech.resistorNode.getId() : null;

        boolean changed = false;
        for (ImmutableNodeInst n : cellRevision.nodes) {
            NodeProtoInfo npi = nodeProtoInfos.get(n.protoId);
            if (npi.equivPortsP == npi.equivPortsN || n.protoId == schemResistor && n.techBits != 0) {
                continue;
            }
            if (connectInternals(n, npi.equivPortsP)) {
                changed = true;
            }
        }
        return changed ? equivMap(equivPortsN) : equivPortsN;
    }

    private int[] computePortsA() {
        PrimitiveNodeId schemResistor = schemTech != null ? schemTech.resistorNode.getId() : null;
        boolean changed = false;
        for (ImmutableNodeInst n : cellRevision.nodes) {
            NodeProtoInfo npi = nodeProtoInfos.get(n.protoId);
            if (npi.equivPortsA == npi.equivPortsP && (n.protoId != schemResistor || n.techBits == 0)) {
                continue;
            }
            if (connectInternals(n, npi.equivPortsA)) {
                changed = true;
            }
        }
        return changed ? equivMap(equivPortsP) : equivPortsP;
    }

    private boolean connectInternals(ImmutableNodeInst n, int[] equivPorts) {
        boolean changed = false;
        int nodeBase = ni_pi[nodeIndexByNodeId(n.nodeId)];
        for (int i = 1; i < equivPorts.length; i++) {
            int eq = equivPorts[i];
            if (eq != i && connectMap(netMap, nodeBase + i, nodeBase + eq)) {
                changed = true;
            }
        }
        return changed;
    }

    public int[] getNetMap(Netlist.ShortResistors shortResistors) {
        int[] map = initMap(numDrawns);
        for (int nodeIndex = 0; nodeIndex < numNodes; nodeIndex++) {
            ImmutableNodeInst n = nodes.get(nodeIndex);
            NodeProtoInfo npi = nodeProtoInfos.get(n.protoId);
            if (npi.numPorts == 1) {
                continue;
            }
            int[] equivPorts;
            switch (shortResistors) {
                case NO:
                    equivPorts = npi.equivPortsN;
                    break;
                case PARASITIC:
                    equivPorts = npi.equivPortsP;
                    break;
                case ALL:
                    equivPorts = npi.equivPortsA;
                    break;
                default:
                    throw new AssertionError();
            }
            connectInternals(map, 0, ni_pi[nodeIndex], equivPorts);
        }
        closureMap(map);
        return map;
    }

    private boolean connectInternals(int[] map, int drawnsStart, int nodeBase, int[] equivPorts) {
        boolean changed = false;
        for (int i = 1; i < equivPorts.length; i++) {
            int eq = equivPorts[i];
            if (eq == i) {
                continue;
            }
            if (connectMap(map, drawnsStart + drawns[nodeBase + i], drawnsStart + drawns[nodeBase + eq])) {
                changed = true;
            }
        }
        return changed;
    }
    private static final int[] EQUIV_PORTS_1 = {0};

    private static class NodeProtoInfo {

        final int numPorts;
        final int[] equivPortsN;
        final int[] equivPortsP;
        final int[] equivPortsA;
        final int[] portIndexByChron;
        final EquivPorts subNet;

        private NodeProtoInfo(CellTree cellTree, NodeProtoId nodeProtoId) {
            if (nodeProtoId instanceof CellId) {
                CellUsage cu = cellTree.top.cellRevision.d.cellId.getUsageIn((CellId) nodeProtoId);
                CellTree subTree = cellTree.subTrees[cu.indexInParent];
                subNet = subTree.getEquivPorts();
                numPorts = subNet.numExports;
                equivPortsN = subNet.equivPortsN;
                equivPortsP = subNet.equivPortsP;
                equivPortsA = subNet.equivPortsA;
                portIndexByChron = subTree.top.cellRevision.exportIndex;
            } else {
                subNet = null;
                PrimitiveNode pn = cellTree.techPool.getPrimitiveNode((PrimitiveNodeId) nodeProtoId);
                numPorts = pn.getNumPorts();
                int maxChronId = -1;
                for (int portIndex = 0; portIndex < pn.getNumPorts(); portIndex++) {
                    PrimitivePort pp = pn.getPort(portIndex);
                    maxChronId = Math.max(maxChronId, pp.getId().chronIndex);
                }
                portIndexByChron = new int[maxChronId + 1];
                Arrays.fill(portIndexByChron, -1);
                for (int portIndex = 0; portIndex < pn.getNumPorts(); portIndex++) {
                    PrimitivePort pp = pn.getPort(portIndex);
                    portIndexByChron[pp.getId().chronIndex] = portIndex;
                }
                if (pn.getNumPorts() == 1) {
                    equivPortsN = equivPortsP = equivPortsA = EQUIV_PORTS_1;
                } else {
                    equivPortsN = initMap(pn.getNumPorts());
                    for (int i = 0; i < pn.getNumPorts(); i++) {
                        for (int j = i + 1; j < pn.getNumPorts(); j++) {
                            if (pn.getPort(i).getTopology() == pn.getPort(j).getTopology()) {
                                connectMap(equivPortsN, i, j);
                            }
                        }
                    }
                    closureMap(equivPortsN);
                    PrimitiveNode.Function fun = pn.getFunction();
                    if (fun == PrimitiveNode.Function.RESIST) {
                        assert equivPortsN.length == 2;
                        equivPortsP = equivPortsA = new int[]{0, 0};
                    } else if (fun.isComplexResistor()) {
                        equivPortsP = equivPortsN;
                        equivPortsA = new int[]{0, 0};
                    } else {
                        equivPortsP = equivPortsA = equivPortsN;
                    }
                }
            }
        }
    }

    private void connectExports() {
        for (int exportIndex = 0; exportIndex < cellRevision.exports.size(); exportIndex++) {
            ImmutableExport e = cellRevision.exports.get(exportIndex);
            connectMap(netMap, exportIndex, mapIndex(e.originalNodeId, e.originalPortId));
        }
    }

    private void connectArcs() {
        for (ImmutableArcInst a : cellRevision.arcs) {
            ArcProto ap = techPool.getArcProto(a.protoId);
            if (ap.getFunction() == ArcProto.Function.NONELEC) {
                continue;
            }
            if (!isolatedPorts.isEmpty() && (isolatedPorts.containsKey(a.tailPortId) || isolatedPorts.containsKey(a.headPortId))) {
                continue;
            }
            if ((a.tailPortId == busPinPortId || a.headPortId == busPinPortId) && ap != busArc) {
                continue;
            }
            connectMap(netMap, mapIndex(a.tailNodeId, a.tailPortId), mapIndex(a.headNodeId, a.headPortId));
        }
    }

    private int mapIndex(int nodeId, PortProtoId portId) {
        NodeProtoInfo npi = nodeProtoInfos.get(portId.parentId);
        return ni_pi[nodeIndexByNodeId(nodeId)] + npi.portIndexByChron[portId.chronIndex];
    }

    private int[] equivMap(int[] oldMap) {
        closureMap(netMap);

        if (oldMap != null && oldMap.length == numExports) {
            boolean eq = true;
            for (int i = 0; i < numExports; i++) {
                if (netMap[i] != oldMap[i]) {
                    eq = false;
                    break;
                }
            }
            if (eq) {
                return oldMap;
            }
        }
        int[] map = new int[numExports];
        System.arraycopy(netMap, 0, map, 0, numExports);
        return map;
    }
}
