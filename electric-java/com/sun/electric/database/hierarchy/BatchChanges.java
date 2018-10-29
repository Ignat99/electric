/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: BatchChanges.java
 * Written by: Dmitry Nadezhin.
 *
 * Copyright (c) 2012, Static Free Software. All rights reserved.
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
package com.sun.electric.database.hierarchy;

import com.sun.electric.database.CellRevision;
import com.sun.electric.database.EditingPreferences;
import com.sun.electric.database.ImmutableCell;
import com.sun.electric.database.ImmutableIconInst;
import com.sun.electric.database.ImmutableNodeInst;
import com.sun.electric.database.Snapshot;
import com.sun.electric.database.geometry.EPoint;
import com.sun.electric.database.geometry.ERectangle;
import com.sun.electric.database.id.CellId;
import com.sun.electric.database.id.NodeProtoId;
import com.sun.electric.database.id.PortProtoId;
import com.sun.electric.database.id.PrimitiveNodeId;
import com.sun.electric.database.prototype.NodeProto;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.topology.PortInst;
import com.sun.electric.database.variable.Variable;
import com.sun.electric.technology.PrimitiveNode;
import com.sun.electric.technology.PrimitiveNode.Function;
import com.sun.electric.technology.TechPool;

import java.io.Serializable;
import java.util.Collection;
import java.util.Iterator;

/**
 *
 */
public class BatchChanges
{
    private static final boolean ONLY_MUTABLE_IMPL = false;

    /**
     * Method to replace a batch of nodes "oldNi" with a new one of type "newNp". Also removes any node-specific variables.
     *
     * @param database replace nodes in this database
     * @param replacements a batch of replacement tasks
     * @param allowMissingPorts true to allow replacement to have missing ports and, therefore, delete the arcs that used to be there.
     * @param preserveParameters true to keep parameters on the old node and put them on the new one.
     * @param ep EditingPreferences
     */
    public static void replaceNodeInsts(EDatabase database,
        Collection<NodeReplacement> replacements, boolean allowMissingPorts, boolean preserveParameters, EditingPreferences ep)
    {
        if (ONLY_MUTABLE_IMPL || allowMissingPorts)
        {
            // Replacement in mutable database
            for (NodeReplacement r : replacements)
            {
                NodeInst oldNode = r.getOldNi(database);
                NodeInst newNode = oldNode.doReplace(r, ep, allowMissingPorts, preserveParameters);
                assert newNode != null;
            }
        } else
        {
            // Replacement in immutable database
        	if (!replacements.iterator().hasNext())
                return;
            CellId cellId = replacements.iterator().next().cellId;
            Cell cell = database.getCell(cellId);
            ReplaceBuilder rb = new ReplaceBuilder(database.backup(), ep);
            Snapshot newSnapshot = rb.update(replacements);

//            List<NodeInst> oldNodeInsts = new ArrayList<NodeInst>();
//            for (NodeReplacement r : replacements)
//            {
//                oldNodeInsts.add(cell.getNodeById(r.nodeId));
//            }
            database.checkChanging();
            database.lowLevelSetCanUndoing(true);
            database.undo(newSnapshot);
            database.lowLevelSetCanUndoing(false);
            cell.getLibrary().setChanged();
            rb.logger.debug("Database changed");
        }
    }

    /**
     * Class that represents a task to replace proto of node instance
     */
    public static class NodeReplacement implements Serializable
    {
        public final CellId cellId;
        public final int nodeId;
        public final NodeProtoId newProtoId;
        public final PrimitiveNode.Function newFunction;
        public final PortProtoId[] assoc;
        private final EPoint givenNewSize;

        
        
        /**
         * Constructs an object that represents a task to replace proto of node instance
         *
         * @param ni NodeInst to replace
         * @param newProto new NodeProto
         */
        public NodeReplacement(NodeInst ni, NodeProto newProto, PrimitiveNode.Function newFunction, EPoint newSize)
        {
            cellId = ni.getParent().getId();
            nodeId = ni.getNodeId();
            newProtoId = newProto.getId();
            givenNewSize = newSize;
            if (newFunction == null)
            {
                throw new NullPointerException();
            }
            this.newFunction = newFunction;
            assoc = new PortProtoId[ni.getNumPortInsts()];
        }

		/**
         * Returns NodeInst to be replaced
         *
         * @return NodeInst to be replaced
         */
        public NodeInst getOldNi(EDatabase database)
        {
            return database.getCell(cellId).getNodeById(nodeId);
        }

        public void setAssoc(PortInst oldPi, PortInst newPi)
        {
            NodeInst oldNi = oldPi.getNodeInst();
            assert oldNi.getParent().getId() == cellId;
            assert oldNi.getNodeId() == nodeId;
            assoc[oldPi.getPortIndex()] = newPi != null ? newPi.getPortProto().getId() : null;
        }

        public ImmutableNodeInst newImmutableInst(Snapshot snapshot, EditingPreferences ep)
        {
            TechPool techPool = snapshot.techPool;
            CellRevision cellRevision = snapshot.getCellRevision(cellId);
            ImmutableNodeInst oldD = cellRevision.getNodeById(nodeId);
            NodeProtoId oldProtoId = oldD.protoId;
            EPoint newSize = EPoint.ORIGIN;
            int newTechBits = 0;
            if (newProtoId instanceof PrimitiveNodeId)
            {
                PrimitiveNode pn = techPool.getPrimitiveNode((PrimitiveNodeId)newProtoId);
                if (oldProtoId instanceof PrimitiveNodeId)
                {
                    PrimitiveNode oldPn = techPool.getPrimitiveNode((PrimitiveNodeId)oldProtoId);
                    ERectangle oldBaseRect = oldPn.getBaseRectangle();
                    ERectangle newBaseRect = pn.getBaseRectangle();
                    if (givenNewSize == null)
                    {
                    	newSize = EPoint.fromGrid(
                    			oldD.size.getGridX() + oldBaseRect.getGridWidth() - newBaseRect.getGridWidth(),
                    			oldD.size.getGridY() + oldBaseRect.getGridHeight() - newBaseRect.getGridHeight());
                    }
                    else
                    	newSize = givenNewSize;
                } else
                {
                    newSize = pn.getDefSize(ep);
                }
                newTechBits = pn.getPrimitiveFunctionBits(newFunction);
            }
            ImmutableNodeInst newD = ImmutableNodeInst.newInstance(oldD.nodeId, newProtoId,
                oldD.name, oldD.nameDescriptor, oldD.orient, oldD.anchor, newSize, oldD.flags, newTechBits, oldD.protoDescriptor);
            for (Iterator<Variable> it = oldD.getVariables(); it.hasNext();)
            {
                Variable var = it.next();
                if (var.getKey() == NodeInst.TRACE)
                {
                    newD = newD.withTrace((EPoint[])var.getObject(), oldD.anchor);
                } else
                {
                    newD = newD.withVariable(var);
                }
            }
            if (oldD instanceof ImmutableIconInst && newD instanceof ImmutableIconInst)
            {
                ImmutableCell iconProto = snapshot.getCellRevision((CellId)newD.protoId).d;
                for (Iterator<Variable> it = ((ImmutableIconInst)oldD).getDefinedParameters(); it.hasNext();)
                {
                    Variable param = it.next();
                    Variable iconParam = iconProto.getParameter((Variable.AttrKey)param.getKey());
                    if (iconParam != null)
                    {
                        newD = ((ImmutableIconInst)newD).withParam(param.withUnit(iconParam.getUnit()));
                    }
                }
            }
            return newD;
        }
    }
}
