/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: CellRevisionJ.java
 * Written by: Dmitry Nadezhin.
 *
 * Copyright (c) 2005, Static Free Software. All rights reserved.
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

import com.sun.electric.database.id.PortProtoId;
import com.sun.electric.util.collections.ArrayIterator;
import com.sun.electric.util.memory.ObjSize;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * Implementation of CellRevision in Java. Connectivity is represented by
 * SoftReferenced long arrays.
 */
class CellRevisionJ extends CellRevision {

    private static final int BIN_SORT_THRESHOLD = 32;
    /**
     * connections in format "(arcIndex << 1) | (arcEnd & 1)" sorted by original
     * PortInst.
     */
    private volatile SoftReference<int[]> connectionsRef = new SoftReference<int[]>(null);
    /**
     * ImmutableExports sorted by original PortInst.
     */
    private volatile SoftReference<ImmutableExport[]> exportIndexByOriginalPortRef = new SoftReference<ImmutableExport[]>(null);

    /**
     * Creates a new instance of CellRevision
     */
    protected CellRevisionJ(ImmutableCell d,
            ImmutableNodeInst.Iterable nodes,
            ImmutableArcInst.Iterable arcs, int[] arcIndex,
            ImmutableExport.Iterable exports, int[] exportIndex,
            BitSet techUsages,
            CellUsageInfo[] cellUsages, BitSet definedExports, int definedExportsLength, BitSet deletedExports) {
        super(d, nodes, arcs, arcIndex, exports, exportIndex,
                techUsages, cellUsages, definedExports, definedExportsLength, deletedExports);
    }

    /**
     * Creates a new instance of CellRevision
     */
    protected CellRevisionJ(ImmutableCell d) {
        this(d, CellRevision.getProvider().createNodeList(ImmutableNodeInst.NULL_ARRAY, null),
                CellRevision.getProvider().createArcList(ImmutableArcInst.NULL_ARRAY, null), NULL_INT_ARRAY,
                CellRevision.getProvider().createExportList(ImmutableExport.NULL_ARRAY, null), NULL_INT_ARRAY,
                CellRevision.makeTechUsages(d.techId), NULL_CELL_USAGE_INFO_ARRAY, EMPTY_BITSET, 0, EMPTY_BITSET);
        if (d.techId == null) {
            throw new NullPointerException("techId");
        }
    }

    @Override
    protected CellRevision lowLevelWith(ImmutableCell d,
            ImmutableNodeInst.Iterable nodes,
            ImmutableArcInst.Iterable arcs, int[] arcIndex,
            ImmutableExport.Iterable exports, int[] exportIndex,
            BitSet techUsages,
            CellUsageInfo[] cellUsages, BitSet definedExports, int definedExportsLength, BitSet deletedExports) {
        CellRevisionJ newCellRevision = new CellRevisionJ(d, nodes, arcs, arcIndex, exports, exportIndex, techUsages, cellUsages, definedExports, definedExportsLength, deletedExports);
        if (this.nodes == nodes) {
            if (this.arcs == arcs) {
                assert this.arcIndex == arcIndex;
                newCellRevision.connectionsRef = connectionsRef;
            }
            if (this.exports == exports) {
                assert this.exportIndex == exportIndex;
                assert this.definedExports == definedExports && this.definedExportsLength == definedExportsLength && this.deletedExports == deletedExports;
                newCellRevision.exportIndexByOriginalPortRef = exportIndexByOriginalPortRef;
            }
        }
        return newCellRevision;
    }

    /**
     * Returns true of there are Connections on specified ImmutableNodeInst
     * connected either to specified port or to all ports
     *
     * @param n specified ImmutableNodeInst
     * @param portId specified port or null
     * @return true if there are Connections on specified ImmutableNodeInst amd
     * specified port.
     * @throws IllegalArgumetException if node inst is not linked to this
     * CellRevision
     */
    @Override
    public boolean hasConnectionsOnNode(ImmutableNodeInst n) {
        if (getNodeById(n.nodeId) != n) {
            throw new IllegalArgumentException();
        }
        int connections[] = getConnections();
        int chronIndex = 0;
        int i = searchConnectionByPort(connections, n.nodeId, chronIndex);
        if (i >= connections.length) {
            return false;
        }
        int con = connections[i];
        ImmutableArcInst a = arcs.get(con >>> 1);
        boolean end = (con & 1) != 0;
        int nodeId = end ? a.headNodeId : a.tailNodeId;
        return nodeId == n.nodeId;
    }

    /**
     * Method to return the number of Connections on specified
     * ImmutableNodeInst.
     *
     * @param n specified ImmutableNodeInst
     * @return the number of Connections on specified ImmutableNodeInst.
     * @throws IllegalArgumetException if node inst is not linked to this
     * CellRevision
     */
    @Override
    public int getNumConnectionsOnNode(ImmutableNodeInst n) {
        if (getNodeById(n.nodeId) != n) {
            throw new IllegalArgumentException();
        }
        int connections[] = getConnections();
        int myNodeId = n.nodeId;
        int i = searchConnectionByPort(connections, myNodeId, 0);
        int j = i;
        for (; j < connections.length; j++) {
            int con = connections[j];
            ImmutableArcInst a = arcs.get(con >>> 1);
            boolean end = (con & 1) != 0;
            int nodeId = end ? a.headNodeId : a.tailNodeId;
            if (nodeId != myNodeId) {
                break;
            }
        }
        return j - i;
    }

    /**
     * Method to return a list of arcs connected to specified ImmutableNodeInst.
     *
     * @param headEnds true if i-th arc connects by head end
     * @param n specified ImmutableNodeInst
     * @return a List of connected ImmutableArcInsts
     * @throws IllegalArgumetException if node inst is not linked to this
     * CellRevision
     */
    @Override
    public List<ImmutableArcInst> getConnectionsOnNode(BitSet headEnds, ImmutableNodeInst n) {
        if (getNodeById(n.nodeId) != n) {
            throw new IllegalArgumentException();
        }
        int connections[] = getConnections();
        ArrayList<ImmutableArcInst> result = null;
        if (headEnds != null) {
            headEnds.clear();
        }
        int myNodeId = n.nodeId;
        int chronIndex = 0;
        int i = searchConnectionByPort(connections, myNodeId, chronIndex);
        int j = i;
        for (; j < connections.length; j++) {
            int con = connections[j];
            ImmutableArcInst a = arcs.get(con >>> 1);
            boolean end = (con & 1) != 0;
            int nodeId = end ? a.headNodeId : a.tailNodeId;
            if (nodeId != myNodeId) {
                break;
            }
            if (result == null) {
                result = new ArrayList<ImmutableArcInst>();
            }
            if (headEnds != null && end) {
                headEnds.set(result.size());
            }
            result.add(a);
        }
        return result != null ? result : Collections.<ImmutableArcInst>emptyList();
    }

    /**
     * Returns true of there are Connections on specified ImmutableNodeInst
     * connected either to specified port or to all ports
     *
     * @param n specified ImmutableNodeInst
     * @param portId specified port or null
     * @return true if there are Connections on specified ImmutableNodeInst amd
     * specified port.
     * @throws IllegalArgumetException if node inst is not linked to this
     * CellRevision
     */
    @Override
    public boolean hasConnectionsOnPort(ImmutableNodeInst n, PortProtoId portId) {
        if (getNodeById(n.nodeId) != n || portId.parentId != n.protoId) {
            throw new IllegalArgumentException();
        }
        int connections[] = getConnections();
        int chronIndex = portId.chronIndex;
        int i = searchConnectionByPort(connections, n.nodeId, chronIndex);
        if (i >= connections.length) {
            return false;
        }
        int con = connections[i];
        ImmutableArcInst a = arcs.get(con >>> 1);
        boolean end = (con & 1) != 0;
        int nodeId = end ? a.headNodeId : a.tailNodeId;
        if (nodeId != n.nodeId) {
            return false;
        }
        return portId == null || portId == (end ? a.headPortId : a.tailPortId);
    }

    /**
     * Method to return the number of Connections on specified port of specified
     * ImmutableNodeInst.
     *
     * @param n specified ImmutableNodeInst
     * @param portId specified port or null
     * @return the number of Connections on specified port of specified
     * ImmutableNodeInst.
     * @throws NullPointerException if n or portId is null
     * @throws IllegalArgumetException if node inst is not linked to this
     * CellRevision
     */
    @Override
    public int getNumConnectionsOnPort(ImmutableNodeInst n, PortProtoId portId) {
        if (getNodeById(n.nodeId) != n || portId.parentId != n.protoId) {
            throw new IllegalArgumentException();
        }
        int connections[] = getConnections();
        int myNodeId = n.nodeId;
        int myChronIndex = portId.chronIndex;
        int i = searchConnectionByPort(connections, myNodeId, myChronIndex);
        int j = i;
        for (; j < connections.length; j++) {
            int con = connections[j];
            ImmutableArcInst a = arcs.get(con >>> 1);
            boolean end = (con & 1) != 0;
            int nodeId = end ? a.headNodeId : a.tailNodeId;
            if (nodeId != myNodeId) {
                break;
            }
            int chronIndex = end ? a.headPortId.chronIndex : a.tailPortId.chronIndex;
            if (chronIndex != myChronIndex) {
                break;
            }
        }
        return j - i;

    }

    /**
     * Method to return a list of arcs connected to speciefed port of specified
     * ImmutableNodeInst.
     *
     * @param headEnds true if i-th arc connects by head end
     * @param n specified ImmutableNodeInst
     * @param portId specified port
     * @return a List of connected ImmutableArcInsts
     * @throws NullPointerException if n or portId is null
     * @throws IllegalArgumetException if node inst is not linked to this
     * CellRevision
     */
    @Override
    public List<ImmutableArcInst> getConnectionsOnPort(BitSet headEnds, ImmutableNodeInst n, PortProtoId portId) {
        if (getNodeById(n.nodeId) != n || portId.parentId != n.protoId) {
            throw new IllegalArgumentException();
        }
        int connections[] = getConnections();
        ArrayList<ImmutableArcInst> result = null;
        if (headEnds != null) {
            headEnds.clear();
        }
        int myNodeId = n.nodeId;
        assert portId.parentId == n.protoId;
        int chronIndex = portId.chronIndex;
        int i = searchConnectionByPort(connections, myNodeId, chronIndex);
        int j = i;
        for (; j < connections.length; j++) {
            int con = connections[j];
            ImmutableArcInst a = arcs.get(con >>> 1);
            boolean end = (con & 1) != 0;
            int nodeId = end ? a.headNodeId : a.tailNodeId;
            if (nodeId != myNodeId) {
                break;
            }
            PortProtoId endProtoId = end ? a.headPortId : a.tailPortId;
            if (endProtoId.getChronIndex() != chronIndex) {
                break;
            }
            if (result == null) {
                result = new ArrayList<ImmutableArcInst>();
            }
            if (headEnds != null && end) {
                headEnds.set(result.size());
            }
            result.add(a);
        }
        return result != null ? result : Collections.<ImmutableArcInst>emptyList();
    }

    private int searchConnectionByPort(int[] connections, int nodeId, int chronIndex) {
        int low = 0;
        int high = connections.length - 1;
        while (low <= high) {
            int mid = (low + high) >> 1; // try in a middle
            int con = connections[mid];
            ImmutableArcInst a = arcs.get(con >>> 1);
            boolean end = (con & 1) != 0;
            int endNodeId = end ? a.headNodeId : a.tailNodeId;
            int cmp = endNodeId - nodeId;
            if (cmp == 0) {
                PortProtoId portId = end ? a.headPortId : a.tailPortId;
                cmp = portId.getChronIndex() - chronIndex;
            }

            if (cmp < 0) {
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return low;
    }

    private int[] getConnections() {
        int[] connections = connectionsRef.get();
        return connections != null ? connections : makeConnections();
    }

    /**
     * Compute connections using sortConnections
     *
     * @return connections
     */
    private int[] makeConnections() {
        // Put connections into buckets by nodeId.
        int[] connections = new int[arcs.size() * 2];
        int[] connectionsByNodeId = new int[getMaxNodeId() + 1];
        for (ImmutableArcInst a : arcs) {
            connectionsByNodeId[a.headNodeId]++;
            connectionsByNodeId[a.tailNodeId]++;
        }
        int sum = 0;
        for (int nodeId = 0; nodeId < connectionsByNodeId.length; nodeId++) {
            int start = sum;
            sum += connectionsByNodeId[nodeId];
            connectionsByNodeId[nodeId] = start;
        }
        for (int i = 0; i < arcs.size(); i++) {
            ImmutableArcInst a = arcs.get(i);
            connections[connectionsByNodeId[a.tailNodeId]++] = i * 2;
            connections[connectionsByNodeId[a.headNodeId]++] = i * 2 + 1;
        }

        // Sort each bucket by portId.
        sum = 0;
        for (int nodeId = 0; nodeId < connectionsByNodeId.length; nodeId++) {
            int start = sum;
            sum = connectionsByNodeId[nodeId];
            if (sum - 1 > start) {
                sortConnections(connections, start, sum - 1);
            }
        }
        connectionsRef = new SoftReference<int[]>(connections);
        return connections;
    }

    private void sortConnections(int[] connections, int l, int r) {
        while (r - l > BIN_SORT_THRESHOLD) {
            int x = connections[(l + r) >>> 1];
            ImmutableArcInst ax = arcs.get(x >>> 1);
            boolean endx = (x & 1) != 0;
            PortProtoId portIdX = endx ? ax.headPortId : ax.tailPortId;
            int chronIndexX = portIdX.getChronIndex();
            int i = l, j = r;
            do {
                // while (compareConnections(connections[i], x) < 0) i++;
                for (;;) {
                    int con = connections[i];
                    ImmutableArcInst a = arcs.get(con >>> 1);
                    boolean end = (con & 1) != 0;
                    PortProtoId portId = end ? a.headPortId : a.tailPortId;
                    if (portId.getChronIndex() > chronIndexX) {
                        break;
                    }
                    if (portId.getChronIndex() == chronIndexX && con >= x) {
                        break;
                    }
                    i++;
                }

                // while (compareConnections(x, connections[j]) < 0) j--;
                for (;;) {
                    int con = connections[j];
                    ImmutableArcInst a = arcs.get(con >>> 1);
                    boolean end = (con & 1) != 0;
                    PortProtoId portId = end ? a.headPortId : a.tailPortId;
                    if (chronIndexX > portId.getChronIndex()) {
                        break;
                    }
                    if (chronIndexX == portId.getChronIndex() && x >= con) {
                        break;
                    }
                    j--;
                }

                if (i <= j) {
                    int w = connections[i];
                    connections[i] = connections[j];
                    connections[j] = w;
                    i++;
                    j--;
                }
            } while (i <= j);
            if (j - l < r - i) {
                sortConnections(connections, l, j);
                l = i;
            } else {
                sortConnections(connections, i, r);
                r = j;
            }
        }
        binarySort(connections, l, r + 1);
    }

    /**
     * This is a specifalized version of {@link java.utils.TimSort#binarySort}.
     *
     * Sorts the specified portion of the specified array using a binary
     * insertion sort. This is the best method for sorting small numbers of
     * elements. It requires O(n log n) compares, but O(n^2) data movement
     * (worst case).
     *
     * If the initial part of the specified range is already sorted, this method
     * can take advantage of it: the method assumes that the elements from index
     * {@code lo}, inclusive, to {@code start}, exclusive are already sorted.
     *
     * @param a the array in which a range is to be sorted
     * @param lo the index of the first element in the range to be sorted
     * @param hi the index after the last element in the range to be sorted
     * @param start the index of the first element in the range that is not
     * already known to be sorted (@code lo <= start <= hi}
     * @param c comparator to used for the sort
     */
    @SuppressWarnings("fallthrough")
    private void binarySort(int[] connections, int lo, int hi) {
        assert lo <= hi;
        int start = lo + 1;
        if (start >= hi) {
            return;
        }
        int conS;
        ImmutableArcInst aS;
        PortProtoId portIdS;
        int conL = connections[lo];
        ImmutableArcInst aL = arcs.get(conL >>> 1);
        PortProtoId portIdL = (conL & 1) != 0 ? aL.headPortId : aL.tailPortId;
        for (;;) {
            conS = connections[start];
            aS = arcs.get(conS >>> 1);
            portIdS = (conS & 1) != 0 ? aS.headPortId : aS.tailPortId;
            int cmp = portIdS.chronIndex - portIdL.chronIndex;
            if (cmp < 0) {
                break;
            }
            if (cmp == 0 && conS < conL) {
                break;
            }
            start++;
            if (start >= hi) {
                return;
            }
            conL = conS;
            aL = aS;
            portIdL = portIdS;
        }
        for (;;) {
            assert start < hi;
            // Set left (and right) to the index where a[start] (pivot) belongs
            int left = lo;
            int right = start;
            assert left <= right;
            /*
             * Invariants:
             *   pivot >= all in [lo, left).
             *   pivot <  all in [right, start).
             */
            while (left < right) {
                int mid = (left + right) >>> 1;
                int conM = connections[mid];
                ImmutableArcInst aM = arcs.get(conM >>> 1);
                PortProtoId portIdM = (conM & 1) != 0 ? aM.headPortId : aM.tailPortId;
                int cmp = portIdS.chronIndex - portIdM.chronIndex;
                if (cmp == 0) {
                    cmp = conS - conM;
                }
                if (cmp < 0) {
                    right = mid;
                } else {
                    left = mid + 1;
                }
            }
            assert left == right;

            /*
             * The invariants still hold: pivot >= all in [lo, left) and
             * pivot < all in [left, start), so pivot belongs at left.  Note
             * that if there are elements equal to pivot, left points to the
             * first slot after them -- that's why this sort is stable.
             * Slide elements over to make room for pivot.
             */
            int n = start - left;  // The number of elements to move
            // Switch is just an optimization for arraycopy in default case
            switch (n) {
                case 2:
                    connections[left + 2] = connections[left + 1];
                case 1:
                    connections[left + 1] = connections[left];
                    break;
                default:
                    System.arraycopy(connections, left, connections, left + 1, n);
            }
            connections[left] = conS;

            start++;
            if (start >= hi) {
                return;
            }
            conS = connections[start];
            aS = arcs.get(conS >>> 1);
            portIdS = (conS & 1) != 0 ? aS.headPortId : aS.tailPortId;
        }
    }

    private int compareConnections(int con1, int con2) {
        ImmutableArcInst a1 = arcs.get(con1 >>> 1);
        ImmutableArcInst a2 = arcs.get(con2 >>> 1);
        boolean end1 = (con1 & 1) != 0;
        boolean end2 = (con2 & 1) != 0;
        int nodeId1 = end1 ? a1.headNodeId : a1.tailNodeId;
        int nodeId2 = end2 ? a2.headNodeId : a2.tailNodeId;
        int cmp = nodeId1 - nodeId2;
        if (cmp != 0) {
            return cmp;
        }
        PortProtoId portId1 = end1 ? a1.headPortId : a1.tailPortId;
        PortProtoId portId2 = end2 ? a2.headPortId : a2.tailPortId;
        cmp = portId1.getChronIndex() - portId2.getChronIndex();
        if (cmp != 0) {
            return cmp;
        }
        return con1 - con2;
    }

    /**
     * Returns true of there are Exports on specified NodeInst.
     *
     * @param originalNode specified ImmutableNodeInst.
     * @return true if there are Exports on specified NodeInst.
     * @throws IllegalArgumetException if node inst is not linked to this
     * CellRevision
     */
    @Override
    public boolean hasExportsOnNode(ImmutableNodeInst originalNode) {
        if (getNodeById(originalNode.nodeId) != originalNode) {
            throw new IllegalArgumentException();
        }
        ImmutableExport[] exportIndexByOriginalPort = getExportIndexByOriginalPort();
        int startIndex = searchExportByOriginalPort(exportIndexByOriginalPort, originalNode.nodeId, 0);
        if (startIndex >= exportIndexByOriginalPort.length) {
            return false;
        }
        ImmutableExport e = exportIndexByOriginalPort[startIndex];
        return e.originalNodeId == originalNode.nodeId;
    }

    /**
     * Method to return the number of Exports on specified NodeInst.
     *
     * @param originalNode specified ImmutableNodeInst.
     * @return the number of Exports on specified NodeInst.
     * @throws IllegalArgumetException if node inst is not linked to this
     * CellRevision
     */
    @Override
    public int getNumExportsOnNode(ImmutableNodeInst originalNode) {
        int originalNodeId = originalNode.nodeId;
        if (getNodeById(originalNodeId) != originalNode) {
            throw new IllegalArgumentException();
        }
        ImmutableExport[] exportIndexByOriginalPort = getExportIndexByOriginalPort();
        int startIndex = searchExportByOriginalPort(exportIndexByOriginalPort, originalNodeId, 0);
        int j = startIndex;
        for (; j < exportIndexByOriginalPort.length; j++) {
            ImmutableExport e = exportIndexByOriginalPort[j];
            if (e.originalNodeId != originalNodeId) {
                break;
            }
        }
        return j - startIndex;
    }

    /**
     * Method to return an Iterator over all ImmutableExports on specified
     * NodeInst.
     *
     * @param originalNode specified ImmutableNodeInst.
     * @return an Iterator over all ImmutableExports on specified NodeInst.
     * @throws IllegalArgumetException if node inst is not linked to this
     * CellRevision
     */
    @Override
    public Iterator<ImmutableExport> getExportsOnNode(ImmutableNodeInst originalNode) {
        int originalNodeId = originalNode.nodeId;
        if (getNodeById(originalNodeId) != originalNode) {
            throw new IllegalArgumentException();
        }
        ImmutableExport[] exportIndexByOriginalPort = getExportIndexByOriginalPort();
        int startIndex = searchExportByOriginalPort(exportIndexByOriginalPort, originalNodeId, 0);
        int j = startIndex;
        for (; j < exportIndexByOriginalPort.length; j++) {
            ImmutableExport e = exportIndexByOriginalPort[j];
            if (e.originalNodeId != originalNodeId) {
                break;
            }
        }
        return ArrayIterator.iterator(exportIndexByOriginalPort, startIndex, j);
    }

    /**
     * Returns true of there are Exports on specified port of specified
     * ImmutableNodeInst.
     *
     * @param originalNode specified ImmutableNodeInst.
     * @param portId specified port
     * @return true if there are Exports on specified NodeInst.
     * @throws NullPointerException if n or portId is null
     * @throws IllegalArgumetException if node inst is not linked to this
     * CellRevision
     */
    @Override
    public boolean hasExportsOnPort(ImmutableNodeInst originalNode, PortProtoId portId) {
        int originalNodeId = originalNode.nodeId;
        if (getNodeById(originalNodeId) != originalNode || portId.parentId != originalNode.protoId) {
            throw new IllegalArgumentException();
        }
        ImmutableExport[] exportIndexByOriginalPort = getExportIndexByOriginalPort();
        int startIndex = searchExportByOriginalPort(exportIndexByOriginalPort, originalNodeId, portId.chronIndex);
        if (startIndex >= exportIndexByOriginalPort.length) {
            return false;
        }
        ImmutableExport e = exportIndexByOriginalPort[startIndex];
        return e.originalNodeId == originalNodeId && e.originalPortId == portId;
    }

    /**
     * Method to return the number of Exports on specified port of specified
     * ImmutableNodeInst.
     *
     * @param originalNode specified ImmutableNodeInst.
     * @param portId specified port
     * @return the number of Exports on specified NodeInst.
     * @throws NullPointerException if n or portId is null
     * @throws IllegalArgumetException if node inst is not linked to this
     * CellRevision
     */
    @Override
    public int getNumExportsOnPort(ImmutableNodeInst originalNode, PortProtoId portId) {
        int originalNodeId = originalNode.nodeId;
        if (getNodeById(originalNodeId) != originalNode || portId.parentId != originalNode.protoId) {
            throw new IllegalArgumentException();
        }
        ImmutableExport[] exportIndexByOriginalPort = getExportIndexByOriginalPort();
        int startIndex = searchExportByOriginalPort(exportIndexByOriginalPort, originalNodeId, portId.chronIndex);
        int j = startIndex;
        for (; j < exportIndexByOriginalPort.length; j++) {
            ImmutableExport e = exportIndexByOriginalPort[j];
            if (e.originalNodeId != originalNodeId || e.originalPortId != portId) {
                break;
            }
        }
        return j - startIndex;

    }

    /**
     * Method to return an Iterator over all ImmutableExports on specified port
     * of specified ImmutableNodeInst.
     *
     * @param originalNode specified ImmutableNodeInst.
     * @param portId specified port
     * @return an Iterator over all ImmutableExports on specified NodeInst.
     * @throws NullPointerException if n or portId is null
     * @throws IllegalArgumetException if node inst is not linked to this
     * CellRevision
     */
    @Override
    public Iterator<ImmutableExport> getExportsOnPort(ImmutableNodeInst originalNode, PortProtoId portId) {
        int originalNodeId = originalNode.nodeId;
        if (getNodeById(originalNodeId) != originalNode || portId.parentId != originalNode.protoId) {
            throw new IllegalArgumentException();
        }
        ImmutableExport[] exportIndexByOriginalPort = getExportIndexByOriginalPort();
        int startIndex = searchExportByOriginalPort(exportIndexByOriginalPort, originalNodeId, portId.chronIndex);
        int j = startIndex;
        for (; j < exportIndexByOriginalPort.length; j++) {
            ImmutableExport e = exportIndexByOriginalPort[j];
            if (e.originalNodeId != originalNodeId || e.originalPortId != portId) {
                break;
            }
        }
        return ArrayIterator.iterator(exportIndexByOriginalPort, startIndex, j);
    }

    private static int searchExportByOriginalPort(ImmutableExport[] exportIndexByOriginalPort, int originalNodeId, int originalChronIndex) {
        int low = 0;
        int high = exportIndexByOriginalPort.length - 1;
        while (low <= high) {
            int mid = (low + high) >> 1; // try in a middle
            ImmutableExport e = exportIndexByOriginalPort[mid];
            int cmp = e.originalNodeId - originalNodeId;
            if (cmp == 0) {
                cmp = e.originalPortId.getChronIndex() >= originalChronIndex ? 1 : -1;
            }

            if (cmp < 0) {
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return low;
    }

    private ImmutableExport[] getExportIndexByOriginalPort() {
        ImmutableExport[] exportIndexByOriginalPort = exportIndexByOriginalPortRef.get();
        return exportIndexByOriginalPort != null ? exportIndexByOriginalPort : makeExportIndexByOriginalPort();

    }

    /**
     * Compute exportIndexByOriginalPort using Arrays.sort
     *
     * @return exportIndexByOriginalPort
     */
    private ImmutableExport[] makeExportIndexByOriginalPort() {
        ImmutableExport[] exportIndexByOriginalPort = exports.toArray();
        Arrays.sort(exportIndexByOriginalPort, BY_ORIGINAL_PORT);
        exportIndexByOriginalPortRef = new SoftReference<ImmutableExport[]>(exportIndexByOriginalPort);
        return exportIndexByOriginalPort;
    }
    private static final Comparator<ImmutableExport> BY_ORIGINAL_PORT = new Comparator<ImmutableExport>() {
        @Override
        public int compare(ImmutableExport e1, ImmutableExport e2) {
            int result = e1.originalNodeId - e2.originalNodeId;
            if (result != 0) {
                return result;
            }
            result = e1.originalPortId.getChronIndex() - e2.originalPortId.getChronIndex();
            if (result != 0) {
                return result;
            }
            return e1.exportId.chronIndex - e2.exportId.chronIndex;
        }
    };

    /**
     * Compute memory consumption
     * @param objSize ObjSDize in this JVM, or null to count objects
     * @param restore restore connectivity data i fnecessary.
     * @return size in bytes, or object count
     */
    @Override
    public long getConnectivityMemorySize(ObjSize objSize, boolean restore) {
        long s = 0;
        int[] connections = restore ? getConnections() : connectionsRef.get();
        if (connections != null) {
            s += objSize.sizeOf(connections);
        }
        ImmutableExport[] exportIndexByOriginalPort = restore ? getExportIndexByOriginalPort() : exportIndexByOriginalPortRef.get();
        if (exportIndexByOriginalPort != null) {
            s += objSize.sizeOf(exportIndexByOriginalPort);
        }
        return s;
    }

    /**
     * Checks invariant of this CellRevision.
     *
     * @throws AssertionError if invariant is broken.
     */
    @Override
    public void check() {
        super.check();

        // check arc conectivity
        int[] connections = connectionsRef.get();
        if (connections != null) {
            assert connections.length == arcs.size() * 2;
            for (int i = 1; i < connections.length; i++) {
                assert compareConnections(connections[i - 1], connections[i]) < 0;
            }
        }
        // check export connectivity
        ImmutableExport[] exportIndexByOriginalPort = exportIndexByOriginalPortRef.get();
        if (exportIndexByOriginalPort != null) {
            assert exportIndexByOriginalPort.length == exports.size();
            ImmutableExport prevE = null;
            for (ImmutableExport e : exportIndexByOriginalPort) {
                if (prevE != null) {
                    assert BY_ORIGINAL_PORT.compare(prevE, e) < 0;
                }
                assert e == getExport(e.exportId);
                prevE = e;
            }
        }
        // general connectivity check
        if (connections != null && exportIndexByOriginalPort != null) {
            checkConnectivity();
        }
    }
}
