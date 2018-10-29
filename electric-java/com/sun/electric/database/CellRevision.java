/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: CellRevision.java
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

import com.sun.electric.database.id.CellId;
import com.sun.electric.database.id.CellUsage;
import com.sun.electric.database.id.ExportId;
import com.sun.electric.database.id.IdReader;
import com.sun.electric.database.id.IdWriter;
import com.sun.electric.database.id.PortProtoId;
import com.sun.electric.database.id.PrimitiveNodeId;
import com.sun.electric.database.id.TechId;
import com.sun.electric.database.text.CellName;
import com.sun.electric.database.variable.Variable;
import com.sun.electric.util.collections.ImmutableArrayList;
import com.sun.electric.util.collections.ImmutableList;
import com.sun.electric.util.memory.ObjSize;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * This class represents Cell data (with all arcs/nodes/exports) as it is saved
 * to disk. This representation should be technology-independent
 */
public abstract class CellRevision {

    public static boolean ALLOW_SUBCELLS_IN_ICON = false;
    public static final CellRevision[] NULL_ARRAY = {};
    public static final ImmutableArrayList<CellRevision> EMPTY_LIST = ImmutableArrayList.of();
    protected static final BitSet EMPTY_BITSET = new BitSet();
    protected static final int[] NULL_INT_ARRAY = {};
    static final CellUsageInfo[] NULL_CELL_USAGE_INFO_ARRAY = {};
    static int cellRevisionsCreated = 0;
    /**
     * Cell persistent data.
     */
    public final ImmutableCell d;
    /**
     * A list of NodeInsts in this Cell.
     */
    public final ImmutableNodeInst.Iterable nodes;
    /**
     * A list of ArcInsts in this Cell.
     */
    public final ImmutableArcInst.Iterable arcs;
    /**
     * Map from chronIndex of Arcs to sortIndex.
     */
    final int arcIndex[];
    /**
     * An array of Exports on the Cell by chronological index.
     */
    public final ImmutableExport.Iterable exports;
    /**
     * Map from chronIndex of Exports to sortIndex.
     */
    int exportIndex[];
    /**
     * TechId usage counts.
     */
    final BitSet techUsages;
    /**
     * CellUsageInfos indexed by CellUsage.indefInParent
     */
    final CellUsageInfo[] cellUsages;
    /**
     * definedExport == [0..definedExportLength) - deletedExports .
     */
    /**
     * Bitmap of defined exports.
     */
    final BitSet definedExports;
    /**
     * Length of defined exports.
     */
    final int definedExportsLength;
    /**
     * Bitmap of deleted exports.
     */
    final BitSet deletedExports;

    /**
     * Creates a new instance of CellRevision
     */
    CellRevision(ImmutableCell d,
            ImmutableNodeInst.Iterable nodes,
            ImmutableArcInst.Iterable arcs, int[] arcIndex,
            ImmutableExport.Iterable exports, int[] exportIndex,
            BitSet techUsages,
            CellUsageInfo[] cellUsages, BitSet definedExports, int definedExportsLength, BitSet deletedExports) {
        this.d = d;
        this.nodes = nodes;
        this.arcs = arcs;
        this.arcIndex = arcIndex;
        this.exports = exports;
        this.exportIndex = exportIndex;
        this.techUsages = techUsages;
        this.cellUsages = cellUsages;
        this.definedExports = definedExports;
        this.definedExportsLength = definedExportsLength;
        this.deletedExports = deletedExports;
        cellRevisionsCreated++;
    }

    protected static BitSet makeTechUsages(TechId techId) {
        BitSet techUsages = new BitSet();
        techUsages.set(techId.techIndex);
        return techUsages;
    }

    public static CellRevisionProvider getProvider() {
        return CellRevisionProvider.INSTANCE;
    }

    /**
     * Creates a new instance of CellRevision
     */
    public static CellRevision newInstance(ImmutableCell d) {
        return getProvider().createCellRevision(d);
    }

    abstract CellRevision lowLevelWith(ImmutableCell d,
            ImmutableNodeInst.Iterable nodes,
            ImmutableArcInst.Iterable arcs, int[] arcIndex,
            ImmutableExport.Iterable exports, int[] exportIndex,
            BitSet techUsages,
            CellUsageInfo[] cellUsages, BitSet definedExports, int definedExportsLength, BitSet deletedExports);

    /**
     * Creates a new instance of CellRevision which differs from this
     * CellRevision by revision date.
     *
     * @param revisionDate new revision date.
     * @return new CellRevision which differs from this CellRevision by revision
     * date.
     */
    public CellRevision withRevisionDate(long revisionDate) {
        if (d.revisionDate == revisionDate) {
            return this;
        }
        return lowLevelWith(this.d.withRevisionDate(revisionDate),
                this.nodes,
                this.arcs, this.arcIndex,
                this.exports, this.exportIndex,
                this.techUsages, this.cellUsages,
                this.definedExports, this.definedExportsLength, this.deletedExports);
    }

    /**
     * Creates a new instance of CellRevision which differs from this
     * CellRevision. Four array parameters are supplied. Each parameter may be
     * null if its contents is the same as in this Snapshot.
     *
     * @param d new persistent data of a cell.
     * @param nodesArray new array of nodes
     * @param arcsArray new array of arcs
     * @param exportsArray new array of exports
     * @return new snapshot which differs froms this Snapshot or this Snapshot.
     * @throws IllegalArgumentException on invariant violation.
     * @throws ArrayOutOfBoundsException on some invariant violations.
     */
    public CellRevision with(ImmutableCell d,
            ImmutableNodeInst[] nodesArray, ImmutableArcInst[] arcsArray, ImmutableExport[] exportsArray) {
        ImmutableNodeInst.Iterable nodes = getProvider().createNodeList(nodesArray, this.nodes);
        ImmutableArcInst.Iterable arcs = getProvider().createArcList(arcsArray, this.arcs);
        ImmutableExport.Iterable exports = getProvider().createExportList(exportsArray, this.exports);
        if (this.d == d && this.nodes == nodes && this.arcs == arcs && this.exports == exports) {
            return this;
        }

        CellId cellId = d.cellId;
        boolean busNamesAllowed = d.busNamesAllowed();
        if (this.d != d) {
            if (d.techId == null) {
                throw new NullPointerException("tech");
            }
//            if (cellId != this.d.cellId)
//                throw new IllegalArgumentException("cellId");
        }

        BitSet techUsages = this.techUsages;
        CellUsageInfo[] cellUsages = this.cellUsages;
        if (this.d.cellId != d.cellId || this.d.techId != d.techId || this.d.getVars() != d.getVars() || nodes != this.nodes || arcs != this.arcs || exports != this.exports) {
            UsageCollector uc = new UsageCollector(d, nodes, arcs, exports);
            techUsages = uc.getTechUsages(this.techUsages);
            cellUsages = uc.getCellUsages(this.cellUsages);
        }
        if (!ALLOW_SUBCELLS_IN_ICON && cellId.isIcon() && cellUsages.length != 0) {
            throw new IllegalArgumentException("Icon contains subcells");
        }

        if (nodes != this.nodes) {
            boolean hasCellCenter = false;
            for (ImmutableNodeInst n : nodes) {
                if (ImmutableNodeInst.isCellCenter(n.protoId)) {
                    if (hasCellCenter) {
                        throw new IllegalArgumentException("Duplicate cell center");
                    }
                    hasCellCenter = true;
                }
                if (!busNamesAllowed && n.name.isBus()) {
                    throw new IllegalArgumentException("arrayedName " + n.name);
                }
            }
        }

        int[] arcIndex = this.arcIndex;
        if (arcs != this.arcs) {
            boolean sameArcIdAndIndex = true;
            boolean sameArcIndex = arcs.size() == this.arcs.size();
            int arcIndexLength = 0;
            int arcInd = 0;
            Iterator<ImmutableArcInst> oldArcs = this.arcs.iterator();
            for (ImmutableArcInst a : arcs) {
                sameArcIdAndIndex = sameArcIdAndIndex && a.arcId == arcInd;
                sameArcIndex = sameArcIndex && a.arcId == oldArcs.next().arcId;
                arcIndexLength = Math.max(arcIndexLength, a.arcId + 1);
                if (!busNamesAllowed && a.name.isBus()) {
                    throw new IllegalArgumentException("arrayedName " + a.name);
                }
                arcInd++;
            }
            if (sameArcIdAndIndex) {
                arcIndex = null;
            } else if (!sameArcIndex) {
                arcIndex = new int[arcIndexLength];
                Arrays.fill(arcIndex, -1);
                arcInd = 0;
                for (ImmutableArcInst a : arcs) {
                    int arcId = a.arcId;
                    if (arcIndex[arcId] >= 0) {
                        throw new IllegalArgumentException("arcChronIndex");
                    }
                    arcIndex[arcId] = arcInd;
                    arcInd++;
                }
                assert !Arrays.equals(this.arcIndex, arcIndex);
            }
        }

        int[] exportIndex = this.exportIndex;
        BitSet definedExports = this.definedExports;
        int definedExportsLength = this.definedExportsLength;
        BitSet deletedExports = this.deletedExports;
        if (exports != this.exports) {
            boolean sameExportIndex = exports.size() == this.exports.size();
            int exportIndexLength = 0;
            int exportInd = 0;
            Iterator<ImmutableExport> oldExports = this.exports.iterator();
            for (ImmutableExport e : exports) {
                if (e.exportId.parentId != cellId) {
                    throw new IllegalArgumentException("exportId");
                }
                if (!busNamesAllowed && e.name.isBus()) {
                    throw new IllegalArgumentException("arrayedName " + e.name);
                }
                int chronIndex = e.exportId.chronIndex;
                sameExportIndex = sameExportIndex && chronIndex == oldExports.next().exportId.chronIndex;
                exportIndexLength = Math.max(exportIndexLength, chronIndex + 1);
                exportInd++;
            }
            if (!sameExportIndex) {
                exportIndex = new int[exportIndexLength];
                Arrays.fill(exportIndex, -1);
                exportInd = 0;
                for (ImmutableExport e : exports) {
                    int chronIndex = e.exportId.chronIndex;
                    if (exportIndex[chronIndex] >= 0) {
                        throw new IllegalArgumentException("exportChronIndex");
                    }
                    exportIndex[chronIndex] = exportInd;
                    //checkPortInst(nodesById.get(e.originalNodeId), e.originalPortId);
                    exportInd++;
                }
                assert !Arrays.equals(this.exportIndex, exportIndex);
                definedExports = new BitSet();
                for (int chronIndex = 0; chronIndex < exportIndex.length; chronIndex++) {
                    if (exportIndex[chronIndex] < 0) {
                        continue;
                    }
                    definedExports.set(chronIndex);
                }
                definedExports = UsageCollector.bitSetWith(this.definedExports, definedExports);
                if (definedExports != this.definedExports) {
                    definedExportsLength = definedExports.length();
                    deletedExports = new BitSet();
                    deletedExports.set(0, definedExportsLength);
                    deletedExports.andNot(definedExports);
                    deletedExports = UsageCollector.bitSetWith(this.deletedExports, deletedExports);
                }
            }
        }

        return lowLevelWith(d, nodes,
                arcs, arcIndex,
                exports, exportIndex,
                techUsages, cellUsages, definedExports, definedExportsLength, deletedExports);
    }

    /**
     * Returns CellRevision which differs from this CellRevision by renamed Ids.
     *
     * @param idMapper a map from old Ids to new Ids.
     * @return CellRevision with renamed Ids.
     */
    CellRevision withRenamedIds(IdMapper idMapper, CellName newGroupName) {
        ImmutableCell d = this.d.withRenamedIds(idMapper).withGroupName(newGroupName);

        ImmutableNodeInst[] nodesArray = null;
        for (int i = 0; i < nodes.size(); i++) {
            ImmutableNodeInst oldNode = nodes.get(i);
            ImmutableNodeInst newNode = oldNode.withRenamedIds(idMapper);
            if (newNode != oldNode && nodesArray == null) {
                nodesArray = new ImmutableNodeInst[nodes.size()];
                for (int j = 0; j < i; j++) {
                    nodesArray[j] = nodes.get(j);
                }
            }
            if (nodesArray != null) {
                nodesArray[i] = newNode;
            }
        }

        ImmutableArcInst[] arcsArray = null;
        for (int i = 0; i < arcs.size(); i++) {
            ImmutableArcInst oldArc = arcs.get(i);
            ImmutableArcInst newArc = oldArc.withRenamedIds(idMapper);
            if (newArc != oldArc && arcsArray == null) {
                arcsArray = new ImmutableArcInst[arcs.size()];
                for (int j = 0; j < i; j++) {
                    arcsArray[j] = arcs.get(j);
                }
            }
            if (arcsArray != null) {
                arcsArray[i] = newArc;
            }
        }

        ImmutableExport[] exportsArray = null;
        for (int i = 0; i < exports.size(); i++) {
            ImmutableExport oldExport = exports.get(i);
            ImmutableExport newExport = oldExport.withRenamedIds(idMapper);
            if (newExport != oldExport && exportsArray == null) {
                exportsArray = new ImmutableExport[exports.size()];
                for (int j = 0; j < i; j++) {
                    exportsArray[j] = exports.get(j);
                }
            }
            if (exportsArray != null) {
                exportsArray[i] = newExport;
            }
        }

        if (this.d == d && nodesArray == null && arcsArray == null && exportsArray == null) {
            return this;
        }
        CellRevision newRevision = with(d, nodesArray, arcsArray, exportsArray);
//        newRevision.check();
        return newRevision;
    }

    /**
     * Returns ImmutableNodeInst by its nodeId.
     *
     * @param nodeId of ImmutableNodeInst.
     * @return ImmutableNodeInst with given nodeId
     * @throws IndexOutOfBoundsException if nodeId is negative
     */
    public ImmutableNodeInst getNodeById(int nodeId) {
        return nodes.getNodeById(nodeId);
    }

    /**
     * Returns sort order index of ImmutableNodeInst by its nodeId.
     *
     * @param nodeId of ImmutableNodeInst.
     * @return sort order index of node
     */
    public int getNodeIndexByNodeId(int nodeId) {
        return nodes.getNodeIndexByNodeId(nodeId);
    }

    /**
     * Returns true an ImmutableNodeInst with specified nodeId is contained in
     * this CellRevision.
     *
     * @param nodeId specified nodeId.
     * @throws IllegalArgumentException if nodeId is negative
     */
    public boolean hasNodeWithId(int nodeId) {
        return nodes.hasNodeWithId(nodeId);
    }

    /**
     * Returns maximum nodeId used by nodes of this CellReversion. Returns -1 if
     * CellRevsison doesn't contatin nodes
     *
     * @return maximum nodeId
     */
    public int getMaxNodeId() {
        return nodes.getMaxNodeId();
    }

    /**
     * Returns ImmutableArcInst by its arcId.
     *
     * @param arcId of ImmutableArcInst.
     * @return ImmutableArcInst with given arcId
     * @throws IndexOutOfBoundsException if arcId is negative
     */
    public ImmutableArcInst getArcById(int arcId) {
        if (arcIndex == null) {
            return arcId < arcs.size() ? arcs.get(arcId) : null;
        }
        if (arcId >= arcIndex.length) {
            return null;
        }
        int arcInd = arcIndex[arcId];
        return arcInd >= 0 ? arcs.get(arcInd) : null;
    }

    /**
     * Returns sort order index of ImmutableArcInst by its arcId.
     *
     * @param arcId of ImmutableArcInst.
     * @return sort order index of arc
     */
    public int getArcIndexByArcId(int arcId) {
        int arcInd = arcIndex != null ? arcIndex[arcId] : arcId;
        assert 0 <= arcInd && arcInd < arcs.size();
        return arcInd;
    }

    /**
     * Returns maximum arcId used by arcs of this CellReversion. Returns -1 if
     * CellRevsison doesn't contatin arcs
     *
     * @return maximum arcId
     */
    public int getMaxArcId() {
        return (arcIndex != null ? arcIndex.length : arcs.size()) - 1;
    }

    /**
     * Returns ImmutableExport by its export id.
     *
     * @param exportId id of export.
     * @return ImmutableExport with this id or null if node doesn't exist.
     */
    public ImmutableExport getExport(ExportId exportId) {
        if (exportId.parentId != d.cellId) {
            throw new IllegalArgumentException();
        }
        int chronIndex = exportId.chronIndex;
        if (chronIndex >= exportIndex.length) {
            return null;
        }
        int portIndex = exportIndex[chronIndex];
        return portIndex >= 0 ? exports.get(portIndex) : null;
    }

    /**
     * Returns sort order index of ImmutableExport by its export id.
     *
     * @param exportId id of export.
     * @return sort order index of export
     */
    public int getExportIndexByExportId(ExportId exportId) {
        if (exportId.parentId != d.cellId) {
            throw new IllegalArgumentException();
        }
        int chronIndex = exportId.chronIndex;
        return chronIndex < exportIndex.length ? exportIndex[chronIndex] : -1;
    }

    /**
     * Returns maximum chronIndex used by exports of this CellReversion. Returns
     * -1 if CellRevsison doesn't contatin exports
     *
     * @return maximum exportChronIndexId
     */
    public int getMaxExportChronIndex() {
        return exportIndex.length - 1;
    }

    /**
     * Returns subcell instance counts, indexed by CellUsage.indexInParent.
     *
     * @return subcell instance counts, indexed by CellUsage.indexInParent.
     */
    public int[] getInstCounts() {
        int l = cellUsages.length;
        while (l > 0 && (cellUsages[l - 1] == null || cellUsages[l - 1].instCount == 0)) {
            l--;
        }
        if (l == 0) {
            return NULL_INT_ARRAY;
        }
        int[] instCounts = new int[l];
        for (int indexInParent = 0; indexInParent < l; indexInParent++) {
            if (cellUsages[indexInParent] != null) {
                instCounts[indexInParent] = cellUsages[indexInParent].instCount;
            }
        }
        return instCounts;
    }

    /**
     * For given CellUsage in this cell returns count of subcell instances.
     *
     * @param u CellUsage.
     * @return count of subcell instances.
     * @throws IllegalArgumentException if CellUsage's parent is not this cell.
     */
    public int getInstCount(CellUsage u) {
        if (u.parentId != d.cellId) {
            throw new IllegalArgumentException();
        }
        if (u.indexInParent >= cellUsages.length) {
            return 0;
        }
        CellUsageInfo cui = cellUsages[u.indexInParent];
        if (cui == null) {
            return 0;
        }
        return cui.instCount;
    }

    /**
     * Returns Set of Technologies used in this CellRevision
     */
    public Set<TechId> getTechUsages() {
        LinkedHashSet<TechId> techUsagesSet = new LinkedHashSet<TechId>();
        for (int techIndex = 0; techIndex < techUsages.length(); techIndex++) {
            if (techUsages.get(techIndex)) {
                techUsagesSet.add(d.cellId.idManager.getTechId(techIndex));
            }
        }
        return techUsagesSet;
    }

    /**
     * Returns true of there are Connections on specified ImmutableNodeInst
     *
     * @param n specified ImmutableNodeInst
     * @return true if there are Connections on specified ImmutableNodeInst
     * @throws IllegalArgumetException if node inst is not linked to this
     * CellRevision
     */
    public abstract boolean hasConnectionsOnNode(ImmutableNodeInst n);

    /**
     * Method to return the number of Connections on specified
     * ImmutableNodeInst.
     *
     * @param n specified ImmutableNodeInst
     * @return the number of Connections on specified ImmutableNodeInst.
     * @throws IllegalArgumetException if node inst is not linked to this
     * CellRevision
     */
    public abstract int getNumConnectionsOnNode(ImmutableNodeInst n);

    /**
     * Method to return a list of arcs connected to specified ImmutableNodeInst.
     *
     * @param headEnds true if i-th arc connects by head end
     * @param n specified ImmutableNodeInst
     * @return a List of connected ImmutableArcInsts
     * @throws IllegalArgumetException if node inst is not linked to this
     * CellRevision
     */
    public abstract List<ImmutableArcInst> getConnectionsOnNode(BitSet headEnds, ImmutableNodeInst n);

    /**
     * Returns true of there are Connections on specified port of specified
     * ImmutableNodeInst
     *
     * @param n specified ImmutableNodeInst
     * @param portId specified port or null
     * @return true if there are Connections on specified port of specified
     * ImmutableNodeInst
     * @throws NullPointerException if n or portId is null
     * @throws IllegalArgumetException if node inst is not linked to this
     * CellRevision
     */
    public abstract boolean hasConnectionsOnPort(ImmutableNodeInst n, PortProtoId portId);

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
    public abstract int getNumConnectionsOnPort(ImmutableNodeInst n, PortProtoId portId);

    /**
     * Method to return a list of arcs connected to specified port of specified
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
    public abstract List<ImmutableArcInst> getConnectionsOnPort(BitSet headEnds, ImmutableNodeInst n, PortProtoId portId);

    /**
     * Returns true of there are Exports on specified NodeInst.
     *
     * @param originalNode specified ImmutableNodeInst.
     * @return true if there are Exports on specified NodeInst.
     * @throws IllegalArgumetException if node inst is not linked to this
     * CellRevision
     */
    public abstract boolean hasExportsOnNode(ImmutableNodeInst originalNode);

    /**
     * Method to return the number of Exports on specified NodeInst.
     *
     * @param originalNode specified ImmutableNodeInst.
     * @return the number of Exports on specified NodeInst.
     * @throws IllegalArgumetException if node inst is not linked to this
     * CellRevision
     */
    public abstract int getNumExportsOnNode(ImmutableNodeInst originalNode);

    /**
     * Method to return an Iterator over all ImmutableExports on specified
     * NodeInst.
     *
     * @param originalNode specified ImmutableNodeInst.
     * @return an Iterator over all ImmutableExports on specified NodeInst.
     * @throws IllegalArgumetException if node inst is not linked to this
     * CellRevision
     */
    public abstract Iterator<ImmutableExport> getExportsOnNode(ImmutableNodeInst originalNode);

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
    public abstract boolean hasExportsOnPort(ImmutableNodeInst originalNode, PortProtoId portId);

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
    public abstract int getNumExportsOnPort(ImmutableNodeInst originalNode, PortProtoId portId);

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
    public abstract Iterator<ImmutableExport> getExportsOnPort(ImmutableNodeInst originalNode, PortProtoId portId);

    /**
     * Compute memory consumption
     * @param objSize ObjSize in this JVM.
     * @param restore restore connectivity data i fnecessary.
     * @return size in bytes
     */
    public abstract long getConnectivityMemorySize(ObjSize objSize, boolean restore);

    /**
     * Compute memory consumption of old connectivity structurs.
     * @param objSize ObjSize in this JVM
     * @return size in bytes, or object count
     */
    public long getOldConnectivityMemorySize(ObjSize objSize) {
        return objSize.sizeOfArray(Integer.TYPE, 2 * arcs.size()) + objSize.sizeOfArray(ImmutableExport.class, exports.size());
    }

    /**
     * Method to determine whether the display of specified pin NodeInst should
     * be supressed. In Schematics technologies, pins are not displayed if there
     * are 1 or 2 connections, but are shown for 0 or 3 or more connections
     * (called "Steiner points").
     *
     * @param pin specified pin ImmutableNodeInst
     * @return true if specieifed pin NodeInst should be supressed.
     */
    public boolean pinUseCount(ImmutableNodeInst pin) {
        int numConnections = getNumConnectionsOnNode(pin);
        if (numConnections > 2) {
            return false;
        }
        if (hasExportsOnNode(pin)) {
            return true;
        }
        if (numConnections == 0) {
            return false;
        }
        return true;
    }

    /**
     * Writes this CellRevision to IdWriter.
     *
     * @param writer where to write.
     */
    void write(IdWriter writer) throws IOException {
        d.write(writer);
        writer.writeInt(nodes.size());
        for (ImmutableNodeInst n : nodes) {
            n.write(writer);
        }
        writer.writeInt(arcs.size());
        for (ImmutableArcInst a : arcs) {
            a.write(writer);
        }
        writer.writeInt(exports.size());
        for (ImmutableExport e : exports) {
            e.write(writer);
        }
    }

    /**
     * Reads CellRevision from SnapshotReader.
     *
     * @param reader where to read.
     */
    static CellRevision read(IdReader reader) throws IOException {
        ImmutableCell d = ImmutableCell.read(reader);
        CellRevision revision = CellRevision.newInstance(d.withoutVariables());

        int nodesLength = reader.readInt();
        ImmutableNodeInst[] nodes = new ImmutableNodeInst[nodesLength];
        for (int i = 0; i < nodesLength; i++) {
            nodes[i] = ImmutableNodeInst.read(reader);
        }

        int arcsLength = reader.readInt();
        ImmutableArcInst[] arcs = new ImmutableArcInst[arcsLength];
        for (int i = 0; i < arcsLength; i++) {
            arcs[i] = ImmutableArcInst.read(reader);
        }

        int exportsLength = reader.readInt();
        ImmutableExport[] exports = new ImmutableExport[exportsLength];
        for (int i = 0; i < exportsLength; i++) {
            exports[i] = ImmutableExport.read(reader);
        }

        revision = revision.with(d, nodes, arcs, exports);
        return revision;
    }

    /**
     * Checks invariant of this CellRevision.
     *
     * @throws AssertionError if invariant is broken.
     */
    public void check() {
        d.check();
        CellId cellId = d.cellId;
        boolean busNamesAllowed = d.busNamesAllowed();
        BitSet checkTechUsages = new BitSet();
        checkTechUsages.set(d.techId.techIndex);
        int[] checkCellUsages = getInstCounts();
        // Check nodes
        nodes.check();
        boolean hasCellCenter = false;
        int nodeInd = 0;
        for (ImmutableNodeInst n : nodes) {
            if (ImmutableNodeInst.isCellCenter(n.protoId)) {
                assert !hasCellCenter;
                hasCellCenter = true;
            }
            assert busNamesAllowed || !n.name.isBus();
            if (n.protoId instanceof CellId) {
                CellId subCellId = (CellId) n.protoId;
                CellUsage u = cellId.getUsageIn(subCellId);
                checkCellUsages[u.indexInParent]--;
                CellUsageInfo cui = cellUsages[u.indexInParent];
                assert cui != null;
                for (int j = 0; j < n.ports.length; j++) {
                    ImmutablePortInst pid = n.ports[j];
                    if (pid == ImmutablePortInst.EMPTY) {
                        continue;
                    }
                    checkPortInst(n, subCellId.getPortId(j));
                }
                if (subCellId.isIcon()) {
                    for (Variable param : n.getDefinedParams()) {
                        assert cui.usedAttributes.get((Variable.AttrKey) param.getKey()) == param.getUnit();
                    }
                    for (Iterator<Variable> it = n.getVariables(); it.hasNext();) {
                        Variable.Key varKey = it.next().getKey();
                        if (varKey.isAttribute()) {
                            assert cui.usedAttributes.get(varKey) == null;
                        }
                    }
                }
            } else {
                TechId techId = ((PrimitiveNodeId) n.protoId).techId;
                checkTechUsages.set(techId.techIndex);
            }
            nodeInd++;
        }
        for (int i = 0; i < checkCellUsages.length; i++) {
            assert checkCellUsages[i] == 0;
        }
        // Check arcs
        ImmutableArcInst.checkList(arcs);
        if (arcIndex != null && arcIndex.length > 0) {
            assert arcIndex[arcIndex.length - 1] >= 0;
            for (int arcId = 0; arcId < arcIndex.length; arcId++) {
                int arcInd = arcIndex[arcId];
                if (arcInd == -1) {
                    continue;
                }
                assert arcs.get(arcInd).arcId == arcId;
            }
        }
        int arcInd = 0;
        for (ImmutableArcInst a : arcs) {
            assert arcIndex != null ? arcIndex[a.arcId] == arcInd : a.arcId == arcInd;
            assert getArcById(a.arcId) == a;
            assert busNamesAllowed || !a.name.isBus();
            checkPortInst(getNodeById(a.tailNodeId), a.tailPortId);
            checkPortInst(getNodeById(a.headNodeId), a.headPortId);

            checkTechUsages.set(a.protoId.techId.techIndex);
            arcInd++;
        }
        // Check exports
        ImmutableExport.checkList(exports);
        if (exportIndex.length > 0) {
            assert exportIndex[exportIndex.length - 1] >= 0;
            for (int chronIndex = 0; chronIndex < exportIndex.length; chronIndex++) {
                int exportInd = exportIndex[chronIndex];
                if (exportInd == -1) {
                    continue;
                }
                assert exports.get(exportInd).exportId.chronIndex == chronIndex;
            }
        }
        assert exportIndex.length == definedExportsLength;
        assert definedExports.length() == definedExportsLength;
        int exportInd = 0;
        for (ImmutableExport e : exports) {
            assert e.exportId.parentId == cellId;
            assert exportIndex[e.exportId.chronIndex] == exportInd;
            assert busNamesAllowed || !e.name.isBus();
            checkPortInst(getNodeById(e.originalNodeId), e.originalPortId);
            exportInd++;
        }
        int exportCount = 0;
        for (int chronIndex = 0; chronIndex < exportIndex.length; chronIndex++) {
            int portIndex = exportIndex[chronIndex];
            if (portIndex == -1) {
                assert !definedExports.get(chronIndex);
                continue;
            }
            assert definedExports.get(chronIndex);
            exportCount++;
            assert exports.get(portIndex).exportId.chronIndex == chronIndex;
        }
        assert exports.size() == exportCount;
        BitSet checkDeleted = new BitSet();
        checkDeleted.set(0, definedExportsLength);
        checkDeleted.andNot(definedExports);
        assert deletedExports.equals(checkDeleted);
        if (definedExports.isEmpty()) {
            assert definedExports == EMPTY_BITSET;
        }
        if (deletedExports.isEmpty()) {
            assert deletedExports == EMPTY_BITSET;
        }
        assert techUsages.equals(checkTechUsages);

        if (!ALLOW_SUBCELLS_IN_ICON && d.cellId.isIcon()) {
            assert cellUsages.length == 0;
        }
        for (int i = 0; i < cellUsages.length; i++) {
            CellUsageInfo cui = cellUsages[i];
            if (cui == null) {
                continue;
            }
            cui.check(d.cellId.getUsageIn(i));
        }
    }

    private void checkPortInst(ImmutableNodeInst node, PortProtoId portId) {
        assert node != null;
        assert portId.getParentId() == node.protoId;
        if (portId instanceof ExportId) {
            checkExportId((ExportId) portId);
        }
    }

    private void checkExportId(ExportId exportId) {
        CellUsage u = d.cellId.getUsageIn(exportId.getParentId());
        assert cellUsages[u.indexInParent].usedExports.get(exportId.getChronIndex());
    }

    public void checkConnectivity() {
        int[] maxConnectedPortInst = new int[getMaxNodeId() + 1];
        for (ImmutableArcInst a : arcs) {
            maxConnectedPortInst[a.tailNodeId] = Math.max(maxConnectedPortInst[a.tailNodeId], a.tailPortId.chronIndex + 1);
            maxConnectedPortInst[a.headNodeId] = Math.max(maxConnectedPortInst[a.headNodeId], a.headPortId.chronIndex + 1);
        }
        for (ImmutableExport e : exports) {
            maxConnectedPortInst[e.originalNodeId] = Math.max(maxConnectedPortInst[e.originalNodeId], e.originalPortId.chronIndex + 1);
        }
        List<List<ImmutableList<Conn>>> conns = new ArrayList<List<ImmutableList<Conn>>>();
        List<List<ImmutableList<ImmutableExport>>> exps = new ArrayList<List<ImmutableList<ImmutableExport>>>();
        for (int nodeId = 0; nodeId <= getMaxNodeId(); nodeId++) {
            List<ImmutableList<Conn>> conns1 = null;
            List<ImmutableList<ImmutableExport>> exps1 = null;
            int m = maxConnectedPortInst[nodeId];
            if (m > 0) {
                conns1 = new ArrayList<ImmutableList<Conn>>(m);
                exps1 = new ArrayList<ImmutableList<ImmutableExport>>(m);
                for (int i = 0; i < m; i++) {
                    conns1.add(ImmutableList.<Conn>empty());
                    exps1.add(ImmutableList.<ImmutableExport>empty());
                }
            }
            conns.add(conns1);
            exps.add(exps1);
        }
        if (this instanceof CellRevisionJ) {
            for (ImmutableArcInst a : arcs) {
                List<ImmutableList<Conn>> tc = conns.get(a.tailNodeId);
                int ti = a.tailPortId.chronIndex;
                tc.set(ti, ImmutableList.addFirst(tc.get(ti), new Conn(a, false)));
                List<ImmutableList<Conn>> hc = conns.get(a.headNodeId);
                int hi = a.headPortId.chronIndex;
                hc.set(hi, ImmutableList.addFirst(hc.get(hi), new Conn(a, true)));
            }
        } else {
            for (int arcId = 0; arcId <= getMaxArcId(); arcId++) {
                ImmutableArcInst a = getArcById(arcId);
                if (a != null) {
                    List<ImmutableList<Conn>> tc = conns.get(a.tailNodeId);
                    int ti = a.tailPortId.chronIndex;
                    tc.set(ti, ImmutableList.addFirst(tc.get(ti), new Conn(a, false)));
                    List<ImmutableList<Conn>> hc = conns.get(a.headNodeId);
                    int hi = a.headPortId.chronIndex;
                    hc.set(hi, ImmutableList.addFirst(hc.get(hi), new Conn(a, true)));
                }
            }
        }
        CellId cellId = d.cellId;
        for (int chronIndex = 0; chronIndex <= getMaxExportChronIndex(); chronIndex++) {
            ImmutableExport e = getExport(cellId.getPortId(chronIndex));
            if (e != null) {
                List<ImmutableList<ImmutableExport>> ec = exps.get(e.originalNodeId);
                int ei = e.originalPortId.chronIndex;
                ec.set(ei, ImmutableList.addFirst(ec.get(ei), e));
            }
        }
        for (ImmutableNodeInst n : nodes) {
            int nodeId = n.nodeId;
            List<ImmutableList<Conn>> cn = conns.get(nodeId);
            List<ImmutableList<ImmutableExport>> ex = exps.get(nodeId);
            int maxChronIndex = cn != null ? cn.size() - 1 : -1;
            BitSet arcHeads = new BitSet();
            List<ImmutableArcInst> arcsOnNode = getConnectionsOnNode(arcHeads, n);
            assert arcsOnNode.equals(getConnectionsOnNode(null, n));
            assert arcHeads.length() <= arcsOnNode.size();
            assert getNumConnectionsOnNode(n) == arcsOnNode.size();
            assert hasConnectionsOnNode(n) == !arcsOnNode.isEmpty();
            Iterator<ImmutableExport> exportsOnNode = getExportsOnNode(n);
            assert hasExportsOnNode(n) == exportsOnNode.hasNext();
            assert maxChronIndex == (ex != null ? ex.size() - 1 : -1);
            int ci = 0;
            int ei = 0;
            for (int chronIndex = 0; chronIndex <= maxChronIndex; chronIndex++) {
                PortProtoId portProtoId = n.protoId.getPortId(chronIndex);
                BitSet arcHeadsP = new BitSet();
                List<ImmutableArcInst> arcsOnPort = getConnectionsOnPort(arcHeadsP, n, portProtoId);
                assert arcsOnPort.equals(getConnectionsOnPort(null, n, portProtoId));
                assert arcHeadsP.length() <= arcsOnPort.size();
                assert getNumConnectionsOnPort(n, portProtoId) == arcsOnPort.size();
                assert hasConnectionsOnPort(n, portProtoId) == !arcsOnPort.isEmpty();
                Iterator<ImmutableExport> exportsOnPort = getExportsOnPort(n, portProtoId);
                assert hasExportsOnPort(n, portProtoId) == exportsOnPort.hasNext();
                int ciP = 0;
                int eiP = 0;
                ImmutableList<Conn> l = ImmutableList.reverse(cn.get(chronIndex));
                if (l != null) {
                    for (Conn conn : l) {
                        ImmutableArcInst a = conn.a;
                        boolean end = conn.end;
                        assert a == arcsOnNode.get(ci) && end == arcHeads.get(ci);
                        assert a == arcsOnPort.get(ciP) && end == arcHeadsP.get(ciP);
                        ci++;
                        ciP++;
                    }
                }
                ImmutableList<ImmutableExport> t = ImmutableList.reverse(ex.get(chronIndex));
                if (t != null) {
                    for (ImmutableExport e : t) {
                        assert e == exportsOnNode.next();
                        assert e == exportsOnPort.next();
                        ei++;
                        eiP++;
                    }
                }
                assert ciP == arcsOnPort.size();
                assert !exportsOnPort.hasNext();
                assert getNumExportsOnPort(n, portProtoId) == eiP;
            }
            assert ci == arcsOnNode.size();
            assert !exportsOnNode.hasNext();
            assert getNumExportsOnNode(n) == ei;
        }
    }

    private static class Conn {

        private final ImmutableArcInst a;
        private final boolean end;

        private Conn(ImmutableArcInst a, boolean end) {
            this.a = a;
            this.end = end;
        }
    }
    private static Field BitSet_words;

    static long sizeOfBitSet(ObjSize objSize, BitSet bs) {
        if (bs == null || bs == EMPTY_BITSET) {
            return 0;
        }
        long size = objSize.sizeOf(bs);
        try {
            if (BitSet_words == null) {
                Class clsBitSet = Class.forName("java.util.BitSet");
                BitSet_words = clsBitSet.getDeclaredField("words");
                BitSet_words.setAccessible(true);
            }
            size += objSize.sizeOf(BitSet_words.get(bs));
        } catch (Exception e) {
            size += (bs.length() + (Long.SIZE - 1)) / Long.SIZE * (Long.SIZE / Byte.SIZE);
        }
        return size;
    }

    public boolean sameExports(CellRevision thatRevision) {
        if (thatRevision == this) {
            return true;
        }
        if (exports.size() != thatRevision.exports.size()) {
            return false;
        }
        for (int i = 0; i < exports.size(); i++) {
            if (exports.get(i).exportId != thatRevision.exports.get(i).exportId) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return d.toString();
    }
}
