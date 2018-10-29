/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Topology.java
 * Written by: Dmitry Nadezhin.
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
package com.sun.electric.database.topology;

import com.sun.electric.database.CellRevision;
import com.sun.electric.database.ImmutableArcInst;
import com.sun.electric.database.ImmutableNodeInst;
import com.sun.electric.database.constraint.Constraints;
import com.sun.electric.database.geometry.ERectangle;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.EDatabase;
import com.sun.electric.database.hierarchy.Export;
import com.sun.electric.database.hierarchy.Nodable;
import com.sun.electric.database.id.CellId;
import com.sun.electric.database.id.CellUsage;
import com.sun.electric.database.id.PortProtoId;
import com.sun.electric.database.prototype.NodeProto;
import com.sun.electric.database.prototype.PortProto;
import com.sun.electric.database.text.Name;
import com.sun.electric.technology.BoundsBuilder;
import com.sun.electric.technology.technologies.Generic;
import com.sun.electric.tool.Job;
import com.sun.electric.util.TextUtils;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * A class to manage nodes and arcs of a Cell.
 */
public class Topology {

    private class MaxSuffix {

        int v = 0;
    }
    /** Owner cell of this Topology. */
    final Cell cell;
    /** The Cell's essential-bounds. */
    private final ArrayList<NodeInst> essenBounds = new ArrayList<NodeInst>();
    /** Chronological list of NodeInsts in this Cell. */
    private final ArrayList<NodeInst> chronNodes = new ArrayList<NodeInst>();
    /** A list of NodeInsts in this Cell. */
    private final ArrayList<NodeInst> nodes = new ArrayList<NodeInst>();
    /** A map from canonic String to Integer maximal numeric suffix */
    private final HashMap<String, MaxSuffix> maxSuffix = new HashMap<String, MaxSuffix>();
    /** A maximal suffix of temporary arc name. */
    private int maxArcSuffix = -1;
    /** Chronological list of ArcInst in this Cell. */
    private final ArrayList<ArcInst> chronArcs = new ArrayList<ArcInst>();
    /** A list of ArcInsts in this Cell. */
    private final ArrayList<ArcInst> arcs = new ArrayList<ArcInst>();
    /** True if arc bounds are valid. */
    boolean validArcBounds;
    /** The geometric data structure. */
    private RTNode<Geometric> rTree = RTNode.makeTopLevel();
    /** True of RTree matches node/arc sizes */
    private boolean rTreeFresh;

    /** Creates a new instance of Topology */
    public Topology(Cell cell, boolean loadBackup) {
        this.cell = cell;
        if (loadBackup) {
            CellRevision cellRevision = cell.backup().cellRevision;
            updateNodes(true, cellRevision, null, cell.lowLevelExpandedNodes());
            updateArcs(cellRevision);
        }
    }

    /**
     * Method to return the parent Cell of this Topology.
     * @return the parent Cell of this Topology.
     */
    public Cell getCell() {
        return cell;
    }
    
    /****************************** NODES ******************************/
    /**
     * Method to return an Iterator over all NodeInst objects in this Cell.
     * @return an Iterator over all NodeInst objects in this Cell.
     */
    public synchronized Iterator<NodeInst> getNodes() {
        ArrayList<NodeInst> nodesCopy = new ArrayList<NodeInst>(nodes);
        return nodesCopy.iterator();
    }

    /**
     * Method to return an Iterator over all NodeInst objects in this Cell.
     * @return an Iterator over all NodeInst objects in this Cell.
     */
    public synchronized Iterator<Nodable> getNodables() {
        ArrayList<Nodable> nodesCopy = new ArrayList<Nodable>(nodes);
        return nodesCopy.iterator();
    }

    /**
     * Method to return the number of NodeInst objects in this Cell.
     * @return the number of NodeInst objects in this Cell.
     */
    public int getNumNodes() {
        return nodes.size();
    }

    /**
     * Method to return the NodeInst at specified position.
     * @param nodeIndex specified position of NodeInst.
     * @return the NodeInst at specified position.
     */
    public final NodeInst getNode(int nodeIndex) {
        return nodes.get(nodeIndex);
    }

    /**
     * Method to return the NodeInst by its chronological index.
     * @param nodeId chronological index of NodeInst.
     * @return the NodeInst with specified chronological index.
     */
    public NodeInst getNodeById(int nodeId) {
        return nodeId < chronNodes.size() ? chronNodes.get(nodeId) : null;
    }

    /**
     * Update PortInsts of all instances of specified Cell accoding to pattern.
     * Pattern contains an element for each Export.
     * If Export was just created, the element contains -1.
     * For old Exports the element contains old index of the Export.
     * @param pattern array with elements describing new PortInsts.
     */
    public void updatePortInsts(Cell proto, int[] pattern) {
        for (NodeInst ni : nodes) {
            if (ni.getProto() == proto) {
                ni.updatePortInsts(pattern);
                ni.check();
            }
        }
    }

    /**
     * Method to return the PortInst by nodeId and PortProtoId.
     * @param nodeId specified NodeId.
     * @param portProtoId
     * @return the PortInst at specified position..
     */
    public PortInst getPortInst(int nodeId, PortProtoId portProtoId) {
        NodeInst ni = chronNodes.get(nodeId);
        assert ni.getD().protoId == portProtoId.getParentId();
        NodeProto np = ni.getProto();
        PortProto pp = np.getPort(portProtoId);
        PortInst pi = ni.getPortInst(pp.getPortIndex());
        assert pi.getNodeInst().getNodeId() == nodeId;
        assert pi.getPortProto().getId() == portProtoId;
        return pi;
    }

    /**
     * Method to find a named NodeInst on this Cell.
     * @param name the name of the NodeInst.
     * @return the NodeInst.  Returns null if none with that name are found.
     */
    public NodeInst findNode(String name) {
        int nodeIndex = searchNode(name);
        return nodeIndex >= 0 ? nodes.get(nodeIndex) : null;
    }

    /**
     * Method to unlink a set of these NodeInsts from this Cell.
     * @param killedNodes a set of NodeInsts to kill.
     */
    public void killNodes(Set<NodeInst> killedNodes) {
        if (killedNodes.isEmpty()) {
            return;
        }
        for (NodeInst ni : killedNodes) {
            if (ni.getParent() != cell) {
                throw new IllegalArgumentException("parent");
            }
        }
        Set<ArcInst> arcsToKill = new HashSet<ArcInst>();
        for (Iterator<ArcInst> it = getArcs(); it.hasNext();) {
            ArcInst ai = it.next();
            if (killedNodes.contains(ai.getTailPortInst().getNodeInst()) || killedNodes.contains(ai.getHeadPortInst().getNodeInst())) {
                arcsToKill.add(ai);
            }

        }
        Set<Export> exportsToKill = new HashSet<Export>();
        for (Iterator<Export> it = cell.getExports(); it.hasNext();) {
            Export export = it.next();
            if (killedNodes.contains(export.getOriginalPort().getNodeInst())) {
                exportsToKill.add(export);
            }
        }

        for (ArcInst ai : arcsToKill) {
            ai.kill();
        }
        cell.killExports(exportsToKill);

        for (NodeInst ni : killedNodes) {
            if (!ni.isLinked()) {
                continue;
            }
            // remove this node from the cell
            removeNode(ni);

            // handle change control, constraint, and broadcast
            Constraints.getCurrent().killObject(ni);
        }
    }

    public ImmutableNodeInst[] backupNodes(ImmutableNodeInst.Iterable oldNodes) {
        ImmutableNodeInst[] newNodes = new ImmutableNodeInst[nodes.size()];
        boolean changed = nodes.size() != oldNodes.size();
        for (int i = 0; i < nodes.size(); i++) {
            NodeInst ni = nodes.get(i);
            ImmutableNodeInst d = ni.getD();
            changed = changed || oldNodes.get(i) != d;
            newNodes[i] = d;
        }
        return changed ? newNodes : null;
    }

    public boolean updateNodes(boolean full, CellRevision newRevision, BitSet exportsModified, BitSet expandedNodes) {
        boolean expandStatusModified = false;
        // Update NodeInsts
        nodes.clear();
        essenBounds.clear();
        maxSuffix.clear();
        BitSet newNodeIds = new BitSet();
        for (int i = 0; i < newRevision.nodes.size(); i++) {
            ImmutableNodeInst d = newRevision.nodes.get(i);
            newNodeIds.set(d.nodeId);
            while (d.nodeId >= chronNodes.size()) {
                chronNodes.add(null);
            }
            NodeInst ni = chronNodes.get(d.nodeId);
            if (ni != null && ni.getProto().getId().isIcon() == d.protoId.isIcon()) {
                NodeProto oldProto = ni.getProto();
                ni.setDInUndo(d);
                if (ni.getProto() != oldProto) {
                    ni.updatePortInsts(true);
                } else if (ni.isCellInstance()) {
                    int subCellIndex = ((Cell) ni.getProto()).getCellIndex();
                    if (full || exportsModified != null && exportsModified.get(subCellIndex)) {
                        ni.updatePortInsts(full);
                    }
                }
            } else {
                ni = NodeInst.lowLevelNewInstance(this, d);
                chronNodes.set(d.nodeId, ni);
                if (ni.isCellInstance()) {
                    Cell subCell = (Cell) ni.getProto();
                    boolean oldEx = expandedNodes.get(d.nodeId);
                    // Remember previous user'setup
                    expandedNodes.set(d.nodeId, oldEx || subCell.isWantExpanded());
                    expandStatusModified = true;
                }
            }
            nodes.add(ni);
            updateMaxSuffix(ni);
            if (Generic.isEssentialBnd(ni)) {
                essenBounds.add(ni);
            }
        }
        assert nodes.size() == newRevision.nodes.size();

        int nodeCount = 0;
        for (int nodeId = 0; nodeId < chronNodes.size(); nodeId++) {
            NodeInst ni = chronNodes.get(nodeId);
            if (newNodeIds.get(nodeId)) {
                assert ni != null;
                nodeCount++;
                continue;
            }
            if (ni == null) {
                continue;
            }
            chronNodes.set(nodeId, null);
        }
        assert nodeCount == nodes.size();
        return expandStatusModified;
    }

    public void updateSubCells(BitSet exportsModified, BitSet boundsModified) {
        unfreshRTree();
        for (int i = 0; i < nodes.size(); i++) {
            NodeInst ni = nodes.get(i);
            if (!ni.isCellInstance()) {
                continue;
            }
            int subCellIndex = ((Cell) ni.getProto()).getCellIndex();
            if (exportsModified != null && exportsModified.get(subCellIndex)) {
                ni.updatePortInsts(false);
            }
            if (boundsModified != null && boundsModified.get(subCellIndex)) {
                ni.redoGeometric();
            }
        }
    }

    /**
     * Method to add a new NodeInst to the cell.
     * @param newNodes the NodeInsts to be included in the cell.
     */
    public void addNodes(List<NodeInst> newNodes) {
        cell.checkChanging();

        int oldI = nodes.size() - 1;
        for (int i = 0; i < newNodes.size(); i++) {
            nodes.add(null);
        }
        int newI = newNodes.size() - 1;
        int outI = nodes.size() - 1;
        while (oldI >= 0 && newI >= 0) {
            int cmp = TextUtils.STRING_NUMBER_ORDER.compare(nodes.get(oldI).getName(), newNodes.get(newI).getName());
            assert cmp != 0;
            NodeInst ni = cmp > 0 ? nodes.get(oldI--) : newNodes.get(newI--);
            nodes.set(outI--, ni);
        }
        while (oldI >= 0) {
            NodeInst ni = nodes.get(oldI--);
            nodes.set(outI--, ni);
        }
        while (newI >= 0) {
            NodeInst ni = newNodes.get(newI--);
            nodes.set(outI--, ni);
        }
        assert oldI == -1 && newI == -1 && outI == -1;
        for (int i = 1; i < nodes.size() - 1; i++) {
            assert TextUtils.STRING_NUMBER_ORDER.compare(nodes.get(i - 1).getName(), nodes.get(i).getName()) < 0;
        }
        for (NodeInst ni : newNodes) {
            int nodeId = ni.getNodeId();
            while (chronNodes.size() <= nodeId) {
                chronNodes.add(null);
            }
            assert chronNodes.get(nodeId) == null;
            chronNodes.set(nodeId, ni);

            updateMaxSuffix(ni);

            // make additional checks to keep circuit up-to-date
            if (Generic.isEssentialBnd(ni)) {
                essenBounds.add(ni);
            }
        }
        unfreshRTree();
    }

    /**
     * Method to add a new NodeInst to the cell.
     * @param ni the NodeInst to be included in the cell.
     * @return true on failure
     */
    public int addNode(NodeInst ni) {
        cell.checkChanging();

        addNodeName(ni);
        int nodeId = ni.getNodeId();
        while (chronNodes.size() <= nodeId) {
            chronNodes.add(null);
        }
        assert chronNodes.get(nodeId) == null;
        chronNodes.set(nodeId, ni);

        // make additional checks to keep circuit up-to-date
        if (Generic.isEssentialBnd(ni)) {
            essenBounds.add(ni);
        }

        unfreshRTree();

        return nodeId;
    }

    /**
     * Method to add a new NodeInst to the name index of this cell.
     * @param ni the NodeInst to be included tp the name index in the cell.
     */
    void addNodeName(NodeInst ni) {
        int nodeIndex = searchNode(ni.getName());
        assert nodeIndex < 0;
        nodeIndex = -nodeIndex - 1;
        nodes.add(nodeIndex, ni);
        updateMaxSuffix(ni);
    }

    /**
     * add temp name of NodeInst to maxSuffix map.
     * @param ni NodeInst.
     */
    private void updateMaxSuffix(NodeInst ni) {
        Name name = ni.getNameKey();
        if (!name.isTempname()) {
            return;
        }

        Name basename = name.getBasename();
        String basenameString = basename.toString();
        MaxSuffix ms = maxSuffix.get(basenameString);
        if (ms == null) {
            ms = new MaxSuffix();
            maxSuffix.put(basenameString, ms);
        }
        int numSuffix = name.getNumSuffix();
        if (numSuffix > ms.v) {
            ms.v = numSuffix;
        }
    }

    /**
     * Method to return unique autoname for NodeInst in this cell.
     * @param basename base name of autoname
     * @return autoname
     */
    Name getNodeAutoname(Name basename) {
        String basenameString = basename.toString();
        MaxSuffix ms = maxSuffix.get(basenameString);
        Name name;
        if (ms == null) {
            ms = new MaxSuffix();
            maxSuffix.put(basenameString, ms);
            name = basename.findSuffixed(0);
        } else {
            ms.v++;
            name = basename.findSuffixed(ms.v);
        }
        assert searchNode(name.toString()) < 0;
        return name;
    }

    /**
     * Method to remove an NodeInst from the cell.
     * @param ni the NodeInst to be removed from the cell.
     */
    public void removeNode(NodeInst ni) {
        assert ni.topology == this;
        essenBounds.remove(ni);
        removeNodeName(ni);
        int nodeId = ni.getNodeId();
        assert chronNodes.get(nodeId) == ni;
        chronNodes.set(nodeId, null);
        unfreshRTree();
    }

    /**
     * Method to remove an NodeInst from the name index of this cell.
     * @param ni the NodeInst to be removed from the cell.
     */
    void removeNodeName(NodeInst ni) {
        int nodeIndex = searchNode(ni.getName());
        NodeInst removedNi = nodes.remove(nodeIndex);
        assert removedNi == ni;
    }

    /**
     * Searches the nodes for the specified name using the binary
     * search algorithm.
     * @param name the name to be searched.
     * @return index of the search name, if it is contained in the nodes;
     *	       otherwise, <tt>(-(<i>insertion point</i>) - 1)</tt>.  The
     *	       <i>insertion point</i> is defined as the point at which the
     *	       NodeInst would be inserted into the list: the index of the first
     *	       element greater than the name, or <tt>nodes.size()</tt>, if all
     *	       elements in the list are less than the specified name.  Note
     *	       that this guarantees that the return value will be &gt;= 0 if
     *	       and only if the NodeInst is found.
     */
    private int searchNode(String name) {
        int low = 0;
        int high = nodes.size() - 1;
        int pick = high; // initially try the last postition
        while (low <= high) {
            NodeInst ni = nodes.get(pick);
            int cmp = TextUtils.STRING_NUMBER_ORDER.compare(ni.getName(), name);

            if (cmp < 0) {
                low = pick + 1;
            } else if (cmp > 0) {
                high = pick - 1;
            } else {
                return pick; // NodeInst found
            }
            pick = (low + high) >> 1; // try in a middle
        }
        return -(low + 1);  // NodeInst not found.
    }

    /**
     * Method to compute the "essential bounds" of this Cell.
     * It looks for NodeInst objects in the cell that are of the type
     * "generic:Essential-Bounds" and builds a rectangle from their locations.
     * @return the bounding area of the essential bounds.
     * Returns null if an essential bounds cannot be determined.
     */
    public Rectangle2D findEssentialBounds() {
        if (essenBounds.size() < 2) {
            return null;
        }
        double minX = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;

        for (int i = 0; i < essenBounds.size(); i++) {
            NodeInst ni = essenBounds.get(i);
            minX = Math.min(minX, ni.getTrueCenterX());
            maxX = Math.max(maxX, ni.getTrueCenterX());
            minY = Math.min(minY, ni.getTrueCenterY());
            maxY = Math.max(maxY, ni.getTrueCenterY());
        }

        return new Rectangle2D.Double(minX, minY, maxX - minX, maxY - minY);
    }

    /****************************** ARCS ******************************/
    /**
     * Method to return an Iterator over all ArcInst objects in this Cell.
     * @return an Iterator over all ArcInst objects in this Cell.
     */
    public synchronized Iterator<ArcInst> getArcs() {
        ArrayList<ArcInst> arcsCopy = new ArrayList<ArcInst>(arcs);
        return arcsCopy.iterator();
    }

    /**
     * Method to return the number of ArcInst objects in this Cell.
     * @return the number of ArcInst objects in this Cell.
     */
    public int getNumArcs() {
        return arcs.size();
    }

    /**
     * Method to return the ArcInst at specified position.
     * @param arcIndex specified position of ArcInst.
     * @return the ArcInst at specified position..
     */
    public final ArcInst getArc(int arcIndex) {
        return arcs.get(arcIndex);
    }

    /**
     * Method to return the ArcInst by its chronological index.
     * @param arcId chronological index of ArcInst.
     * @return the ArcInst with specified chronological index.
     */
    public ArcInst getArcById(int arcId) {
        return arcId < chronArcs.size() ? chronArcs.get(arcId) : null;
    }

    /**
     * Method to find a named ArcInst on this Cell.
     * @param name the name of the ArcInst.
     * @return the ArcInst.  Returns null if none with that name are found.
     */
    public ArcInst findArc(String name) {
        int arcIndex = searchArc(name, 0);
        if (arcIndex >= 0) {
            return arcs.get(arcIndex);
        }
        arcIndex = -arcIndex - 1;
        if (arcIndex < arcs.size()) {
            ArcInst ai = arcs.get(arcIndex);
            if (ai.getName().equals(name)) {
                return ai;
            }
        }
        return null;
    }

    /**
     * Method to add a new ArcInst to the cell.
     * @param ai the ArcInst to be included in the cell.
     */
    void addArc(ArcInst ai) {
        cell.setTopologyModified();
        validArcBounds = false;
        unfreshRTree();

        int arcIndex = searchArc(ai);
        assert arcIndex < 0;
        arcIndex = -arcIndex - 1;
        arcs.add(arcIndex, ai);


        int arcId = ai.getArcId();
        while (chronArcs.size() <= arcId) {
            chronArcs.add(null);
        }
        assert chronArcs.get(arcId) == null;
        chronArcs.set(arcId, ai);

        // update maximal arc name suffux temporary name
        if (ai.isUsernamed()) {
            return;
        }
        Name name = ai.getNameKey();
        assert name.getBasename() == ImmutableArcInst.BASENAME;
        maxArcSuffix = Math.max(maxArcSuffix, name.getNumSuffix());
    }

    /**
     * Method to return unique autoname for ArcInst in this cell.
     * @return a unique autoname for ArcInst in this cell.
     */
    Name getArcAutoname() {
        if (maxArcSuffix < Integer.MAX_VALUE) {
            return ImmutableArcInst.BASENAME.findSuffixed(++maxArcSuffix);
        }
        for (int i = 0;; i++) {
            Name name = ImmutableArcInst.BASENAME.findSuffixed(i);
            if (!hasTempArcName(name)) {
                return name;
            }
        }
    }

    /**
     * Method check if ArcInst with specified temporary name key exists in a cell.
     * @param name specified temporary name key.
     */
    boolean hasTempArcName(Name name) {
        return name.isTempname() && findArc(name.toString()) != null;
    }

    /**
     * Method to remove an ArcInst from the cell.
     * @param ai the ArcInst to be removed from the cell.
     */
    void removeArc(ArcInst ai) {
        cell.checkChanging();
        cell.setTopologyModified();
        unfreshRTree();

        assert ai.isLinked();
        int arcIndex = searchArc(ai);
        ArcInst removedAi = arcs.remove(arcIndex);
        assert removedAi == ai;

        int arcId = ai.getArcId();
        assert chronArcs.get(arcId) == ai;
        chronArcs.set(arcId, null);
    }

    public ImmutableArcInst[] backupArcs(ImmutableArcInst.Iterable oldArcs) {
        ImmutableArcInst[] newArcs = new ImmutableArcInst[arcs.size()];
        boolean changed = arcs.size() != oldArcs.size();
        for (int i = 0; i < arcs.size(); i++) {
            ArcInst ai = arcs.get(i);
            ImmutableArcInst d = ai.getD();
            changed = changed || oldArcs.get(i) != d;
            newArcs[i] = d;
        }
        return changed ? newArcs : null;
    }

    public void updateArcs(CellRevision newRevision) {
        validArcBounds = false;
        arcs.clear();
        maxArcSuffix = -1;
        for (int i = 0; i < newRevision.arcs.size(); i++) {
            ImmutableArcInst d = newRevision.arcs.get(i);
            while (d.arcId >= chronArcs.size()) {
                chronArcs.add(null);
            }
            ArcInst ai = chronArcs.get(d.arcId);
            PortInst headPi = getPortInst(d.headNodeId, d.headPortId);
            PortInst tailPi = getPortInst(d.tailNodeId, d.tailPortId);
            if (ai != null && (/*!full ||*/ai.getHeadPortInst() == headPi && ai.getTailPortInst() == tailPi)) {
                ai.setDInUndo(d);
            } else {
                ai = new ArcInst(this, d, headPi, tailPi);
                chronArcs.set(d.arcId, ai);
            }
            arcs.add(ai);
            if (!ai.isUsernamed()) {
                Name name = ai.getNameKey();
                assert name.getBasename() == ImmutableArcInst.BASENAME;
                maxArcSuffix = Math.max(maxArcSuffix, name.getNumSuffix());
            }
        }

        int arcCount = 0;
        for (int i = 0; i < chronArcs.size(); i++) {
            ArcInst ai = chronArcs.get(i);
            if (ai == null) {
                continue;
            }
            int arcIndex = searchArc(ai);
            if (arcIndex < 0 || arcIndex >= arcs.size() || ai != arcs.get(arcIndex)) {
                chronArcs.set(i, null);
                continue;
            }
            arcCount++;
        }
        assert arcCount == arcs.size();
    }

    /**
     * Low-level routine.
     */
    void computeArcBounds() {
        long[] gridCoords = new long[4];
        BoundsBuilder b = new BoundsBuilder(cell.getTechPool());
        for (int arcIndex = 0; arcIndex < arcs.size(); arcIndex++) {
            ArcInst ai = arcs.get(arcIndex);
            ai.computeBounds(b, gridCoords);
        }
        validArcBounds = true;
    }

    private int searchArc(ArcInst ai) {
        return searchArc(ai.getName(), ai.getArcId());
    }

    /**
     * Searches the arcs for the specified (name,arcId) using the binary
     * search algorithm.
     * @param name the name to be searched.
     * @param arcId the arcId index to be searched.
     * @return index of the search name, if it is contained in the arcs;
     *	       otherwise, <tt>(-(<i>insertion point</i>) - 1)</tt>.  The
     *	       <i>insertion point</i> is defined as the point at which the
     *	       ArcInst would be inserted into the list: the index of the first
     *	       element greater than the name, or <tt>arcs.size()</tt>, if all
     *	       elements in the list are less than the specified name.  Note
     *	       that this guarantees that the return value will be &gt;= 0 if
     *	       and only if the ArcInst is found.
     */
    private int searchArc(String name, int arcId) {
        int low = 0;
        int high = arcs.size() - 1;
        int pick = high; // initially try the last postition

        while (low <= high) {
            ArcInst ai = arcs.get(pick);
            int cmp = TextUtils.STRING_NUMBER_ORDER.compare(ai.getName(), name);
            if (cmp == 0) {
                cmp = ai.getArcId() - arcId;
            }

            if (cmp < 0) {
                low = pick + 1;
            } else if (cmp > 0) {
                high = pick - 1;
            } else {
                return pick; // ArcInst found
            }
            pick = (low + high) >> 1; // try in a middle
        }
        return -(low + 1);  // ArcInst not found.
    }

    /****************************** GRAPHICS ******************************/
    /**
     * Method to return an interator over all RTBounds objects in a given area of this Cell that allows
     * to ignore elements touching the area.
     * Note that Geometric objects implement RTBounds, so for database searches, the iterator
     * returns Geometrics (NodeInsts and ArcInsts).
     * @param bounds the specified area to search.
     * @param includeEdges true if RTBounds objects along edges are considered in.
     * @return an iterator over all of the RTBounds objects in that area.
     */
    public Iterator<Geometric> searchIterator(Rectangle2D bounds, boolean includeEdges) {
        return new RTNode.Search<Geometric>(bounds, getRTree(), includeEdges);
    }

    void setArcsDirty() {
        cell.setTopologyModified();
        validArcBounds = false;
        unfreshRTree();
    }

    public void unfreshRTree() {
        rTreeFresh = false;
    }

    /**
     * Method to R-Tree of this Cell.
     * The R-Tree organizes all of the Geometric objects spatially for quick search.
     * @return R-Tree of this Cell.
     */
    private RTNode<Geometric> getRTree() {
        if (rTreeFresh) {
            return rTree;
        }
        rebuildRTree();
        rTreeFresh = true;
        return rTree;
    }

    private void rebuildRTree() {
//        long startTime = System.currentTimeMillis();
        if (!validArcBounds) {
            computeArcBounds();
        }
        CellId cellId = cell.getId();
        RTNode<Geometric> root = RTNode.makeTopLevel();
        for (Iterator<NodeInst> it = cell.getNodes(); it.hasNext();) {
            NodeInst ni = it.next();
            root = RTNode.linkGeom(cellId, root, ni);
        }
        for (Iterator<ArcInst> it = cell.getArcs(); it.hasNext();) {
            ArcInst ai = it.next();
            root = RTNode.linkGeom(cellId, root, ai);
        }
        root.checkRTree(0, cellId);
        rTree = root;
        rTreeFresh = true;
//        long stopTime = System.currentTimeMillis();
//        if (Job.getDebug()) System.out.println("Rebuilding R-Tree in " + this + " took " + (stopTime - startTime) + " msec");
    }
    
    private void rebuildRTree2() {
        long startTime = System.currentTimeMillis();
        BitSet remainingNodes = new BitSet();
        BitSet remainingArcs = new BitSet();
        removeRTNodes(remainingNodes, remainingArcs, rTree);
        CellId cellId = cell.getId();
        for (Iterator<NodeInst> it = cell.getNodes(); it.hasNext();) {
            NodeInst ni = it.next();
            if (remainingNodes.get(ni.getNodeId())) {
                continue;
            }
            rTree = RTNode.linkGeom(cellId, rTree, ni);
        }
        for (Iterator<ArcInst> it = cell.getArcs(); it.hasNext();) {
            ArcInst ai = it.next();
            if (remainingArcs.get(ai.getArcId())) {
                continue;
            }
            rTree = RTNode.linkGeom(cellId, rTree, ai);
        }
        rTree.checkRTree(0, cellId);
        rTreeFresh = true;
        long stopTime = System.currentTimeMillis();
        if (Job.getDebug()) System.out.println("Rebuilding R-Tree in " + cell + " took " + (stopTime - startTime) + " msec");
    }
    
    /**
     * Method to remove entry "ind" from this R-tree node in cell "cell"
     */
    private void removeRTNodes(BitSet remainingNodes, BitSet remainingArcs, RTNode<Geometric> top) {
        long fixpMinX = Long.MAX_VALUE;
        long fixpMinY = Long.MAX_VALUE;
        long fixpMaxX = Long.MIN_VALUE;
        long fixpMaxY = Long.MIN_VALUE;
        if (top.getFlag()) {
            int j = 0;
            for (int i = 0; i < top.getTotal(); i++) {
                Geometric geom = top.getChildLeaf(j);
                ERectangle bounds;
                if (geom instanceof NodeInst) {
                    NodeInst ni = (NodeInst) geom;
                    if (ni != getNodeById(ni.getNodeId())) {
                        continue;
                    }
                    bounds = geom.getBounds();
                    if (!top.contains(bounds)) {
                        continue;
                    }
                    remainingNodes.set(ni.getNodeId());
                } else {
                    ArcInst ai = (ArcInst) geom;
                    if (ai != getArcById(ai.getArcId())) {
                        continue;
                    }
                    bounds = geom.getBounds();
                    if (!top.contains(bounds)) {
                        continue;
                    }
                    remainingArcs.set(ai.getArcId());
                }
                fixpMinX = Math.min(fixpMinX, bounds.getFixpMinX());
                fixpMinY = Math.min(fixpMinY, bounds.getFixpMinY());
                fixpMaxX = Math.max(fixpMaxX, bounds.getFixpMaxX());
                fixpMaxY = Math.max(fixpMaxY, bounds.getFixpMaxY());
                top.setChild(j++, geom);
            }
            top.setTotal(j);
        } else {
            int j = 0;
            for (int i = 0; i < top.getTotal(); i++) {
                RTNode<Geometric> child = top.getChildTree(i);
                removeRTNodes(remainingNodes, remainingArcs, child);
                if (child.getTotal() == 0) {
                    continue;
                }
                fixpMinX = Math.min(fixpMinX, child.getFixpMinX());
                fixpMinY = Math.min(fixpMinY, child.getFixpMinY());
                fixpMaxX = Math.max(fixpMaxX, child.getFixpMaxX());
                fixpMaxY = Math.max(fixpMaxY, child.getFixpMaxY());
                top.setChild(j++, child);
            }
            top.setTotal(j);
        }
        if (top.getTotal() == 0) {
            fixpMinX = fixpMinY = fixpMaxX = fixpMaxY = 0;
        }
        top.setFixpLow(fixpMinX, fixpMinY, fixpMaxX, fixpMaxY);
    }

    /**
     * Method to check invariants in this Cell.
     * @exception AssertionError if invariants are not valid
     */
    public void check(int[] cellUsages) {
        // check arcs
        ArcInst prevAi = null;
        BoundsBuilder b = new BoundsBuilder(cell.getTechPool());
        for (int arcIndex = 0; arcIndex < arcs.size(); arcIndex++) {
            ArcInst ai = arcs.get(arcIndex);
            ImmutableArcInst a = ai.getD();
            assert ai.getParent() == cell;
            assert chronArcs.get(a.arcId) == ai;
            if (prevAi != null) {
                int cmp = TextUtils.STRING_NUMBER_ORDER.compare(prevAi.getName(), ai.getName());
                assert cmp <= 0;
                if (cmp == 0) {
                    assert prevAi.getArcId() < a.arcId;
                }
            }
            assert ai.getHeadPortInst() == cell.getPortInst(a.headNodeId, a.headPortId);
            assert ai.getTailPortInst() == cell.getPortInst(a.tailNodeId, a.tailPortId);
            if (validArcBounds) {
                ai.check(b);
            }
            prevAi = ai;
        }
        for (int arcId = 0; arcId < chronArcs.size(); arcId++) {
            ArcInst ai = chronArcs.get(arcId);
            if (ai == null) {
                continue;
            }
            assert ai.getArcId() == arcId;
            int arcIndex = searchArc(ai);
            assert ai == arcs.get(arcIndex);
        }

        // check nodes
        NodeInst prevNi = null;
        EDatabase database = cell.getDatabase();
        CellId cellId = cell.getId();
        int[] usages = new int[cellId.numUsagesIn()];
        for (int nodeIndex = 0; nodeIndex < nodes.size(); nodeIndex++) {
            NodeInst ni = nodes.get(nodeIndex);
            ImmutableNodeInst n = ni.getD();
            assert ni.getParent() == cell;
            assert chronNodes.get(n.nodeId) == ni;
            if (prevNi != null) {
                assert TextUtils.STRING_NUMBER_ORDER.compare(prevNi.getName(), ni.getName()) < 0;
            }
            if (ni.isCellInstance()) {
                Cell subCell = (Cell) ni.getProto();
                assert subCell.isLinked();
                assert subCell.getDatabase() == database;
                CellUsage u = cellId.getUsageIn(subCell.getId());
                usages[u.indexInParent]++;
            }
            ni.check();
            prevNi = ni;
        }
        for (int nodeId = 0; nodeId < chronNodes.size(); nodeId++) {
            NodeInst ni = chronNodes.get(nodeId);
            if (ni == null) {
                continue;
            }
            assert ni.getNodeId() == nodeId;
            assert ni == nodes.get(searchNode(ni.getName()));
        }

        // check node usages
        for (int i = 0; i < cellUsages.length; i++) {
            assert cellUsages[i] == usages[i];
        }
        for (int i = cellUsages.length; i < usages.length; i++) {
            assert usages[i] == 0;
        }

        if (rTreeFresh) {
            rTree.checkRTree(0, cell.getId());
        }
    }
}
