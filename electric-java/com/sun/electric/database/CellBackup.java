/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: CellBackup.java
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

import com.sun.electric.database.geometry.ERectangle;
import com.sun.electric.database.id.IdManager;
import com.sun.electric.database.id.IdReader;
import com.sun.electric.database.id.IdWriter;
import com.sun.electric.database.id.NodeProtoId;
import com.sun.electric.database.id.PrimitiveNodeId;
import com.sun.electric.database.text.CellName;
import com.sun.electric.database.variable.TextDescriptor;
import com.sun.electric.database.variable.Variable;
import com.sun.electric.technology.AbstractShapeBuilder;
import com.sun.electric.technology.ArcProto;
import com.sun.electric.technology.BoundsBuilder;
import com.sun.electric.technology.PrimitiveNode;
import com.sun.electric.technology.TechPool;
import com.sun.electric.technology.Technology;
import com.sun.electric.technology.technologies.Generic;
import com.sun.electric.util.collections.ImmutableArrayList;

import java.io.IOException;
import java.util.BitSet;
import java.util.Iterator;

/**
 * CellBackup is a pair of CellRevision and TechPool.
 * It caches data that can be calculated when Technology is already
 * known, but subcells are unknown.
 */
public class CellBackup {

    public static final CellBackup[] NULL_ARRAY = {};
    public static final ImmutableArrayList<CellBackup> EMPTY_LIST = ImmutableArrayList.of();
    static int cellBackupsCreated = 0;
    /**
     * Cell data.
     */
    public final CellRevision cellRevision;
    /**
     * Technologies mapping
     */
    public final TechPool techPool;
    /**
     * "Modified" flag of the Cell.
     */
    public final boolean modified;
    /**
     * Set of nodeIds of wiped pin
     */
    private BitSet wiped;
    /**
     * Set of arcIds with hars shape calculation
     */
    private BitSet hardArcs;
    /**
     * Arc shrinkage data
     */
    private AbstractShapeBuilder.Shrinkage shrinkage;
    /**
     * Bounds of primitive arcs in this Cell.
     */
    private ERectangle primitiveBounds;

    /**
     * Creates a new instance of CellBackup
     */
    private CellBackup(CellRevision cellRevision, TechPool techPool, boolean modified) {
        this.cellRevision = cellRevision;
        this.techPool = techPool;
        this.modified = modified;
        cellBackupsCreated++;
    }

    /**
     * Creates a new instance of CellBackup
     */
    public static CellBackup newInstance(ImmutableCell d, TechPool techPool) {
        if (d.cellId.idManager != techPool.idManager) {
            throw new IllegalArgumentException();
        }
        if (techPool.getTech(d.techId) == null) {
            throw new IllegalArgumentException();
        }
        CellRevision cellRevision = CellRevision.newInstance(d);
        TechPool restrictedPool = techPool.restrict(cellRevision.techUsages, techPool);
        return new CellBackup(cellRevision, restrictedPool, true);
    }

    /**
     * Creates a new instance of CellBackup which differs from this CellBackup.
     * Four array parameters are supplied. Each parameter may be null if its contents is the same as in this Snapshot.
     * @param d new persistent data of a cell.
     * @param nodesArray new array of nodes
     * @param arcsArray new array of arcs
     * @param exportsArray new array of exports
     * @param superPool TechPool which defines all used technologies
     * @return new snapshot which differs froms this Snapshot or this Snapshot.
     * @throws IllegalArgumentException on invariant violation.
     * @throws ArrayOutOfBoundsException on some invariant violations.
     */
    public CellBackup with(ImmutableCell d,
            ImmutableNodeInst[] nodesArray, ImmutableArcInst[] arcsArray, ImmutableExport[] exportsArray,
            TechPool superPool) {
        CellRevision newRevision = cellRevision.with(d, nodesArray, arcsArray, exportsArray);
        TechPool restrictedPool = superPool.restrict(newRevision.techUsages, techPool);
        if (newRevision == cellRevision && restrictedPool == techPool) {
            return this;
        }
        if (arcsArray != null) {
            for (ImmutableArcInst a : arcsArray) {
                if (a != null && !a.check(restrictedPool)) {
                    throw new IllegalArgumentException("arc " + a.name + " is not compatible with TechPool");
                }
            }
        }
        return new CellBackup(newRevision, restrictedPool, modified || newRevision != cellRevision);
    }

    /**
     * Creates a new instance of CellBackup which differs from this CellBackup by revision date.
     * @param revisionDate new revision date.
     * @return new CellBackup which differs from this CellBackup by revision date.
     */
    public CellBackup withRevisionDate(long revisionDate) {
        CellRevision newRevision = cellRevision.withRevisionDate(revisionDate);
        if (newRevision == cellRevision) {
            return this;
        }
        return new CellBackup(newRevision, this.techPool, true);
    }

    /**
     * Creates a new instance of CellBackup with modified flag off.
     * @return new snapshot which differs froms this Snapshot or this Snapshot.
     */
    public CellBackup withoutModified() {
        if (!this.modified) {
            return this;
        }
        return new CellBackup(this.cellRevision, this.techPool, false);
    }

    /**
     * Returns CellBackup which differs from this CellBackup by TechPool.
     * @param techPool technology map.
     * @return CellBackup with new TechPool.
     */
    public CellBackup withTechPool(TechPool techPool) {
        TechPool restrictedPool = techPool.restrict(cellRevision.techUsages, this.techPool);
        if (this.techPool == restrictedPool) {
            return this;
        }
        if (techPool.idManager != this.techPool.idManager) {
            throw new IllegalArgumentException();
        }
//        for (Technology tech: this.techPool.values()) {
//            if (techPool.get(tech.getId()) != tech)
//                throw new IllegalArgumentException();
//        }
        return new CellBackup(this.cellRevision, restrictedPool, this.modified);
    }

    /**
     * Returns CellBackup which differs from this CellBackup by renamed Ids.
     * @param idMapper a map from old Ids to new Ids.
     * @return CellBackup with renamed Ids.
     */
    CellBackup withRenamedIds(IdMapper idMapper, CellName newGroupName) {
        CellRevision newRevision = cellRevision.withRenamedIds(idMapper, newGroupName);
        if (newRevision == cellRevision) {
            return this;
        }
        return new CellBackup(newRevision, this.techPool, true);
    }

    /**
     * Writes this CellBackup to IdWriter.
     * @param writer where to write.
     */
    void write(IdWriter writer) throws IOException {
        cellRevision.write(writer);
        writer.writeBoolean(modified);
    }

    /**
     * Reads CellBackup from SnapshotReader.
     * @param reader where to read.
     */
    static CellBackup read(IdReader reader, TechPool techPool) throws IOException {
        CellRevision newRevision = CellRevision.read(reader);
        boolean modified = reader.readBoolean();
        TechPool restrictedPool = techPool.restrict(newRevision.techUsages, techPool);
        return new CellBackup(newRevision, restrictedPool, modified);
    }

    /**
     * Checks invariant of this CellBackup.
     * @throws AssertionError if invariant is broken.
     */
    public void check() {
        cellRevision.check();
        IdManager idManager = cellRevision.d.cellId.idManager;
        assert techPool.idManager == idManager;
        BitSet techUsages = new BitSet();
        for (Technology tech : techPool.values()) {
            int techIndex = tech.getId().techIndex;
            assert !techUsages.get(techIndex);
            techUsages.set(techIndex);
        }
        assert techUsages.equals(cellRevision.techUsages);
//        for (int techIndex = 0; techIndex < cellRevision.techUsages.length(); techIndex++) {
//            if (cellRevision.techUsages.get(techIndex))
//                assert techPool.getTech(idManager.getTechId(techIndex)) != null;
//        }
        for (ImmutableArcInst a : cellRevision.arcs) {
            if (a != null) {
                a.check(techPool);
            }
        }
    }

    @Override
    public String toString() {
        return cellRevision.toString();
    }

    /**
     * Returns data for arc shrinkage computation.
     * @return data for arc shrinkage computation.
     */
    public AbstractShapeBuilder.Shrinkage getShrinkage() {
        if (shrinkage == null) {
            shrinkage = new AbstractShapeBuilder.Shrinkage(this);
        }
        return shrinkage;
    }

    /**
     * Returns bounds of all primitive arcs in this Cell or null if there are not primitives.
     * @return bounds of all primitive arcs or null.
     */
    public ERectangle getPrimitiveBounds() {
        ERectangle primitiveBounds = this.primitiveBounds;
        if (primitiveBounds != null) {
            return primitiveBounds;
        }
        return this.primitiveBounds = computePrimitiveBounds();
    }

    public ERectangle computePrimitiveBounds() {
        ERectangle primitiveArcBounds = computePrimitiveBoundsOfArcs();
        long gridMinX = Long.MAX_VALUE, gridMinY = Long.MAX_VALUE, gridMaxX = Long.MIN_VALUE, gridMaxY = Long.MIN_VALUE;
        long[] gridCoords = new long[4];
        for (ImmutableNodeInst n : cellRevision.nodes) {
            if (!(n.protoId instanceof PrimitiveNodeId)) {
                continue;
            }
            PrimitiveNode pn = techPool.getPrimitiveNode((PrimitiveNodeId) n.protoId);

            // special case: do not include "cell center" primitives from Generic
            if (pn == Generic.tech().cellCenterNode) {
                continue;
            }

            // special case for invisible pins: do not include if inheritable or interior-only
            if (pn == Generic.tech().invisiblePinNode) {
                boolean found = false;
                for (Iterator<Variable> it = n.getVariables(); it.hasNext();) {
                    Variable var = it.next();
                    if (var.isDisplay()) {
                        TextDescriptor td = var.getTextDescriptor();
                        if (td.isInterior() || td.isInherit()) {
                            found = true;
                            break;
                        }
                    }
                }
                if (found) {
                    continue;
                }
            }

            pn.genBounds(n, gridCoords);
            gridMinX = Math.min(gridMinX, gridCoords[0]);
            gridMinY = Math.min(gridMinY, gridCoords[1]);
            gridMaxX = Math.max(gridMaxX, gridCoords[2]);
            gridMaxY = Math.max(gridMaxY, gridCoords[3]);
        }

        if (gridMinX > gridMaxX || gridMinY > gridMaxY) {
            return primitiveArcBounds;
        }
        if (primitiveArcBounds != null) {
            gridMinX = Math.min(gridMinX, primitiveArcBounds.getGridMinX());
            gridMaxX = Math.max(gridMaxX, primitiveArcBounds.getGridMaxX());
            gridMinY = Math.min(gridMinY, primitiveArcBounds.getGridMinY());
            gridMaxY = Math.max(gridMaxY, primitiveArcBounds.getGridMaxY());
        }
        return ERectangle.fromGrid(gridMinX, gridMinY, gridMaxX - gridMinX, gridMaxY - gridMinY);
    }

    ERectangle getElibPrimitiveBounds() {
        ERectangle primitiveArcBounds = computePrimitiveBoundsOfArcs();
        long gridMinX = Long.MAX_VALUE, gridMinY = Long.MAX_VALUE, gridMaxX = Long.MIN_VALUE, gridMaxY = Long.MIN_VALUE;
        long[] gridCoords = new long[4];
        for (ImmutableNodeInst n : cellRevision.nodes) {
            if (!(n.protoId instanceof PrimitiveNodeId)) {
                continue;
            }
            PrimitiveNode pn = techPool.getPrimitiveNode((PrimitiveNodeId) n.protoId);

            // special case: do not include "cell center" primitives from Generic
            if (pn == Generic.tech().cellCenterNode) {
                continue;
            }

            // special case for invisible pins: do not include if inheritable or interior-only
            if (pn == Generic.tech().invisiblePinNode) {
                boolean found = false;
                for (Iterator<Variable> it = n.getVariables(); it.hasNext();) {
                    Variable var = it.next();
                    if (var.isDisplay()) {
                        TextDescriptor td = var.getTextDescriptor();
                        if (td.isInterior() || td.isInherit()) {
                            found = true;
                            break;
                        }
                    }
                }
                if (found) {
                    continue;
                }
            }

            pn.genElibBounds(this, n, gridCoords);
            gridMinX = Math.min(gridMinX, gridCoords[0]);
            gridMinY = Math.min(gridMinY, gridCoords[1]);
            gridMaxX = Math.max(gridMaxX, gridCoords[2]);
            gridMaxY = Math.max(gridMaxY, gridCoords[3]);
        }

        if (gridMinX > gridMaxX || gridMinY > gridMaxY) {
            return primitiveArcBounds;
        }
        if (primitiveArcBounds != null) {
            gridMinX = Math.min(gridMinX, primitiveArcBounds.getGridMinX());
            gridMaxX = Math.max(gridMaxX, primitiveArcBounds.getGridMaxX());
            gridMinY = Math.min(gridMinY, primitiveArcBounds.getGridMinY());
            gridMaxY = Math.max(gridMaxY, primitiveArcBounds.getGridMaxY());
        }
        return ERectangle.fromGrid(gridMinX, gridMinY, gridMaxX - gridMinX, gridMaxY - gridMinY);
    }

    private ERectangle computePrimitiveBoundsOfArcs() {
        ImmutableArcInst.Iterable arcs = cellRevision.arcs;
        if (arcs.isEmpty()) {
            return null;
        }
        long gridMinX = Long.MAX_VALUE, gridMinY = Long.MAX_VALUE, gridMaxX = Long.MIN_VALUE, gridMaxY = Long.MIN_VALUE;
        long[] gridCoords = new long[4];
        BoundsBuilder boundsBuilder = new BoundsBuilder(techPool);
        for (ImmutableArcInst a : arcs) {
            if (boundsBuilder.genBoundsEasy(a, gridCoords)) {
                gridMinX = Math.min(gridMinX, gridCoords[0]);
                gridMinY = Math.min(gridMinY, gridCoords[1]);
                gridMaxX = Math.max(gridMaxX, gridCoords[2]);
                gridMaxY = Math.max(gridMaxY, gridCoords[3]);
                continue;
            }
            boundsBuilder.genShapeOfArc(a);
        }
        ERectangle bounds = boundsBuilder.makeBounds();
        if (bounds != null) {
            gridMinX = Math.min(gridMinX, bounds.getGridMinX());
            gridMinY = Math.min(gridMinY, bounds.getGridMinY());
            gridMaxX = Math.max(gridMaxX, bounds.getGridMaxX());
            gridMaxY = Math.max(gridMaxY, bounds.getGridMaxY());
        }
        assert gridMinX <= gridMaxX && gridMinY <= gridMaxY;
        long gridW = gridMaxX - gridMinX;
        long gridH = gridMaxY - gridMinY;
        return ERectangle.fromGrid(gridMinX, gridMinY, gridW, gridH);
    }

    /**
     * Method to tell whether the specified ImmutableNodeInst is wiped.
     * Wiped ImmutableNodeInsts are erased. Typically, pin ImmutableNodeInsts can be wiped.
     * This means that when an arc connects to the pin, it is no longer drawn.
     * In order for a ImmutableNodeInst to be wiped, its prototype must have the "setArcsWipe" state,
     * and the arcs connected to it must have "setWipable" in their prototype.
     * @param n specified ImmutableNodeInst
     * @return true if specified ImmutableNodeInst is wiped.
     */
    public boolean isWiped(ImmutableNodeInst n) {
        if (wiped == null) {
            wiped = makeWiped();
        }
        return wiped.get(n.nodeId);
    }

    private BitSet makeWiped() {
        BitSet wiped = new BitSet();
        for (ImmutableArcInst a : cellRevision.arcs) {
            ArcProto ap = techPool.getArcProto(a.protoId);
            // wipe status
            if (ap.isWipable()) {
                wiped.set(a.tailNodeId);
                wiped.set(a.headNodeId);
            }
        }

        for (ImmutableNodeInst n: cellRevision.nodes) {
            NodeProtoId np = n.protoId;
            if (!(np instanceof PrimitiveNodeId && techPool.getPrimitiveNode((PrimitiveNodeId) np).isArcsWipe())) {
                wiped.clear(n.nodeId);
            }
        }
        return wiped;

    }

    /**
     * Method to tell whether the specified ImmutableArcInst is hard to calculate shape.
     * @param arcId arcId of ImmtableArcInst
     * @return true if specified ImmutableArcInst is hard to calcualte shape
     */
    public boolean isHardArc(int arcId) {
        if (hardArcs == null) {
            hardArcs = makeHardArcs();
        }
        return hardArcs.get(arcId);
    }

    private BitSet makeHardArcs() {
        BitSet hardArcs = new BitSet();
        for (ImmutableArcInst a : cellRevision.arcs) {
            ArcProto ap = techPool.getArcProto(a.protoId);
            // hard arcs
            if (!ap.isEasyShape(a, false)) {
                hardArcs.set(a.arcId);
            }
        }
        return hardArcs;
    }
}
