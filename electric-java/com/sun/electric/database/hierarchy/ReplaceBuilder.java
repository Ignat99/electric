/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: ReplaceBuilder.java
 * Written by: Dmitry Nadezhin.
 *
 * Copyright (c) 2014, Static Free Software. All rights reserved.
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

import com.sun.electric.database.CellBackup;
import com.sun.electric.database.CellRevision;
import com.sun.electric.database.CellTree;
import com.sun.electric.database.EditingPreferences;
import com.sun.electric.database.Environment;
import com.sun.electric.database.ImmutableArcInst;
import com.sun.electric.database.ImmutableCell;
import com.sun.electric.database.ImmutableExport;
import com.sun.electric.database.ImmutableNodeInst;
import com.sun.electric.database.Snapshot;
import com.sun.electric.database.geometry.EPoint;
import com.sun.electric.database.geometry.Poly;
import com.sun.electric.database.id.CellId;
import com.sun.electric.database.id.ExportId;
import com.sun.electric.database.id.NodeProtoId;
import com.sun.electric.database.id.PortProtoId;
import com.sun.electric.database.id.PrimitiveNodeId;
import com.sun.electric.database.id.PrimitivePortId;
import com.sun.electric.database.text.Name;
import com.sun.electric.database.variable.TextDescriptor;
import com.sun.electric.technology.ArcProto;
import com.sun.electric.technology.PrimitiveNode;
import com.sun.electric.technology.TechPool;
import com.sun.electric.tool.Tool;
import com.sun.electric.util.collections.ImmutableArrayList;
import com.sun.electric.util.math.FixpTransform;
import com.sun.electric.util.math.GenMath;
import com.sun.electric.util.math.Orientation;
import java.util.ArrayList;
import java.util.BitSet;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class replaces nodes in immutable database according to Node replacement requests.
 */
public class ReplaceBuilder
{
    final Logger logger = LoggerFactory.getLogger(ReplaceBuilder.class);
    final Snapshot oldSnapshot;
    final EditingPreferences ep;
    final Tool oldTool;
    final Environment oldEnvironment;
    final TechPool oldTechPool;
    final ImmutableArrayList<CellTree> oldCellTrees;

    /**
     * @param oldSnapshot Snapshot to transform
     * @param ep EditingPreferences
     */
    ReplaceBuilder(Snapshot oldSnapshot, EditingPreferences ep)
    {
        this.oldSnapshot = oldSnapshot;
        this.ep = ep;
        oldTool = oldSnapshot.tool;
        oldEnvironment = oldSnapshot.environment;
        oldTechPool = oldEnvironment.techPool;
        oldCellTrees = oldSnapshot.cellTrees;
    }

    /**
     * @param replacements an Iterable of Node replacement requests.
     */
    Snapshot update(Iterable<BatchChanges.NodeReplacement> replacements)
    {
        // Split node replacement requests by CellIds
        Map<CellId, Map<Integer, BatchChanges.NodeReplacement>> map = new HashMap<CellId, Map<Integer, BatchChanges.NodeReplacement>>();
        for (BatchChanges.NodeReplacement r : replacements)
        {
            CellId cellId = r.cellId;
            int nodeId = r.nodeId;
            Map<Integer, BatchChanges.NodeReplacement> map1 = map.get(cellId);
            if (map1 == null)
            {
                map1 = new HashMap<Integer, BatchChanges.NodeReplacement>();
                map.put(cellId, map1);
            }
            map1.put(nodeId, r);
        }
        // Traverse cells fron bottom to top and replace nodes there 
        CellBackup[] curCellBackups = new CellBackup[oldCellTrees.size()];
        for (CellId cellId : oldSnapshot.getCellsDownTop())
        {
            CellBackup oldCellBackup = oldSnapshot.getCell(cellId);
            Map<Integer, BatchChanges.NodeReplacement> map1 = map.get(cellId);
            if (map1 != null)
            {
                CellBuilder cb = new CellBuilder(cellId);
                curCellBackups[cellId.cellIndex] = cb.update(map1);
            } else
            {
                curCellBackups[cellId.cellIndex] = oldCellBackup;
            }
        }
        // Gather updated CellBackups in a new Snapshot
        Snapshot newSnapshot = oldSnapshot.with(oldTool, oldEnvironment, curCellBackups, null);
        logger.debug("Snapshot is ready");
        return newSnapshot;
    }

    /**
     * Class to perform node replacements in a Cell
     */
    class CellBuilder
    {
        final CellId cellId;
        final CellTree oldCellTree;
        final CellBackup oldCellBackup;
        final CellRevision oldCellRevision;
        final ImmutableCell oldCell;
        // nodes
        final BitSet deletedNodeInds = new BitSet();
        int curNodesCount;
        int lastNodeId;
        final Map<Name, MaxNodeSuffix> maxNodeSuffixesOrdered = new TreeMap<Name, MaxNodeSuffix>();
        final Map<Integer, ImmutableNodeInst> replacedNodes = new HashMap<Integer, ImmutableNodeInst>();
        // arcs
        final BitSet deletedArcInds = new BitSet();
        int curArcsCount;
        int lastArcId;
        int arcInsertionPoint;
        int maxArcSuffix = -1;
        final List<ImmutableArcInst> addedArcs = new ArrayList<ImmutableArcInst>();
        final Map<Integer, ImmutableArcInst> replacedArcs = new HashMap<Integer, ImmutableArcInst>();
        // exports
        final BitSet deletedExportInds = new BitSet();
        int curExportsCount;
        final Map<ExportId, ImmutableExport> replacedExports = new HashMap<ExportId, ImmutableExport>();

        /**
         * @param cellId CellId of the Cell
         */
        CellBuilder(CellId cellId)
        {
            this.cellId = cellId;
            oldCellTree = oldSnapshot.getCellTree(cellId);
            oldCellBackup = oldCellTree.top;
            oldCellRevision = oldCellBackup.cellRevision;
            oldCell = oldCellRevision.d;
            // nodes
            curNodesCount = oldCellRevision.nodes.size();
            lastNodeId = oldCellRevision.getMaxNodeId();
            // arcs
            curArcsCount = oldCellRevision.arcs.size();
            lastArcId = oldCellRevision.getMaxArcId();
            arcInsertionPoint = searchArcInsertionPoint(ImmutableArcInst.BASENAME.toString());
            if (arcInsertionPoint > 0)
            {
                Name name = oldCellRevision.arcs.get(arcInsertionPoint - 1).name;
                if (name.isTempname())
                {
                    assert name.getBasename() == ImmutableArcInst.BASENAME;
                    maxArcSuffix = name.getNumSuffix();
                }
            }
            // exports
            curExportsCount = oldCellRevision.exports.size();
        }

        /**
         * Update CellBackup according to a Map from nodeId to NodeReplacement
         *
         * @param replacements a Map from nodeId to NodeReplacement
         * @return updated CellBackup
         */
        CellBackup update(Map<Integer, BatchChanges.NodeReplacement> replacements)
        {
            // replace nodes
            Iterator<ImmutableNodeInst> nit = oldCellRevision.nodes.iterator();
            int nodeInd = 0;
            while (nit.hasNext())
            {
                ImmutableNodeInst n = nit.next();
                BatchChanges.NodeReplacement r = replacements.get(n.nodeId);
                if (r != null)
                    replaceNode(nodeInd, n, r);
                nodeInd++;
            }

            // replace arcs
            Iterator<ImmutableArcInst> ait = oldCellRevision.arcs.iterator();
            int arcInd = 0;
            while (ait.hasNext())
            {
                ImmutableArcInst a = ait.next();
                PortProtoId newTailPortId = portMap(replacements, a.tailNodeId, a.tailPortId);
                PortProtoId newHeadPortId = portMap(replacements, a.headNodeId, a.headPortId);
                if (newTailPortId == null || newHeadPortId == null)
                {
                    deletedArcInds.set(arcInd);
                    curArcsCount--;
                } else if (newTailPortId != a.tailPortId || newHeadPortId != a.headPortId)
                {
                    ImmutableArcInst newA = replaceArc(a, newTailPortId, newHeadPortId);
                    replacedArcs.put(a.arcId, newA);
                }
                arcInd++;
            }

            // replace exports
            Iterator<ImmutableExport> eit = oldCellRevision.exports.iterator();
            int exportInd = 0;
            while (eit.hasNext())
            {
                ImmutableExport e = eit.next();
                PortProtoId newOriginalPortId = portMap(replacements, e.originalNodeId, e.originalPortId);
                if (newOriginalPortId == null)
                {
                    deletedExportInds.set(exportInd);
                    curExportsCount--;
                } else if (newOriginalPortId != e.originalPortId)
                {
                    ImmutableExport newE = e.withOriginalPort(e.originalNodeId, newOriginalPortId);
                    replacedExports.put(e.exportId, newE);
                }
                exportInd++;
            }

            // Create new CellBackup from accumulated changes
            return commit();
        }

        /**
         * Get base name of temporary name of node
         *
         * @param protoId NodeProtoId proto of node
         * @param function Function of node
         * @return base name
         */
        private Name getBaseName(NodeProtoId protoId, PrimitiveNode.Function function)
        {
            if (protoId instanceof CellId)
            {
                CellId cellId = (CellId)protoId;
                return cellId.cellName.getBasename();
            } else
            {
                PrimitiveNodeId pnId = (PrimitiveNodeId)protoId;
                PrimitiveNode pn = oldTechPool.getPrimitiveNode(pnId);
                int newTechBits = pn.getPrimitiveFunctionBits(function);
                return oldTechPool.getPrimitiveNode(pnId).getPrimitiveFunction(newTechBits).getBasename();
            }
        }

        /**
         * Get storage of nodes with temporary names
         *
         * @param protoId NodeProtoId proto of node
         * @param function Function of node
         * @return storage of nodes with temporary names
         */
        private MaxNodeSuffix getMaxSuffix(NodeProtoId protoId, PrimitiveNode.Function function)
        {
            Name basename = getBaseName(protoId, function);
            MaxNodeSuffix ms = maxNodeSuffixesOrdered.get(basename);
            if (ms != null)
            {
                return ms;
            }
            ms = new MaxNodeSuffix(this, basename);
            maxNodeSuffixesOrdered.put(basename, ms);
            return ms;
        }

        /**
         * Replace a node
         *
         * @param nodeInd index in alphanumeric order
         * @param n old node
         * @param r request to replacement
         */
        private void replaceNode(int nodeInd, ImmutableNodeInst n, BatchChanges.NodeReplacement r)
        {
            NodeProtoId newProtoId = r.newProtoId;
            MaxNodeSuffix maxSuffix = null;
            Name newName = n.name;
            if (n.name.isTempname())
            {
                Name baseName = getBaseName(newProtoId, r.newFunction);
                if (baseName != n.name.getBasename())
                {
                    maxSuffix = getMaxSuffix(newProtoId, r.newFunction);
                    newName = maxSuffix.getNextName();
                }
            }
//            EPoint newSize;
//            if (n.protoId instanceof PrimitiveNodeId && newProtoId instanceof PrimitiveNodeId)
//            {
//                ERectangle oldBaseRect = oldTechPool.getPrimitiveNode((PrimitiveNodeId)n.protoId).getBaseRectangle();
//                ERectangle newBaseRect = oldTechPool.getPrimitiveNode((PrimitiveNodeId)newProtoId).getBaseRectangle();
//                newSize = EPoint.fromGrid(
//                    n.size.getGridX() + oldBaseRect.getGridWidth() - newBaseRect.getGridWidth(),
//                    n.size.getGridY() + oldBaseRect.getGridHeight() - newBaseRect.getGridHeight());
//
//            } else if (newProtoId instanceof PrimitiveNodeId)
//            {
//                newSize = oldTechPool.getPrimitiveNode((PrimitiveNodeId)newProtoId).getDefSize(ep);
//            } else
//            {
//                newSize = EPoint.ORIGIN;
//            }
//            int newTechBits = n.techBits;
//            if (newProtoId instanceof PrimitiveNodeId && r.newFunction != null)
//            {
//                newTechBits = oldTechPool.getPrimitiveNode((PrimitiveNodeId)newProtoId).getPrimitiveFunctionBits(r.newFunction);
//            }
            ImmutableNodeInst newN = r.newImmutableInst(oldSnapshot, ep).withName(newName);
//            ImmutableNodeInst newN = ImmutableNodeInst.newInstance(
//                n.nodeId, r.newProtoId,
//                newName, n.nameDescriptor,
//                n.orient, n.anchor, newSize,
//                n.flags, newTechBits, n.protoDescriptor);
//            for (Iterator<Variable> vit = n.getVariables(); vit.hasNext();)
//            {
//                Variable v = vit.next();
//                if (v.getKey() == NodeInst.TRACE)
//                {
//                    newN = newN.withTrace((EPoint[])v.getObject(), n.anchor);
//                } else
//                {
//                    newN = newN.withVariable(v);
//                }
//            }
            if (newName != n.name)
            {
                maxSuffix.add(newN);
                deletedNodeInds.set(nodeInd);
            }
            replacedNodes.put(n.nodeId, newN);
        }

        /**
         * Function to get shape of port
         *
         * @param n node
         * @param portId port proto
         * @return shape of port
         */
        private Poly getShapeOfPort(ImmutableNodeInst n, PortProtoId portId)
        {
            if (n.protoId instanceof CellId)
            {
                CellRevision subCell = oldSnapshot.getCellRevision((CellId)n.protoId);
                ImmutableExport export = subCell.getExport((ExportId)portId);
                ImmutableNodeInst origN = subCell.getNodeById(export.originalNodeId);
                Poly poly = getShapeOfPort(origN, export.originalPortId);
                poly.transform(new FixpTransform(n.anchor, n.orient));
                return poly;
            } else
            {
                Poly.Builder polyBuilder = Poly.newLambdaBuilder();
                return polyBuilder.getShape(n, oldTechPool.getPrimitivePort((PrimitivePortId)portId));
            }
        }

        /**
         * Replace arc
         *
         * @param a old arc
         * @param newTailPortId new tail PortProtoId
         * @param newHeadPortId new head PortProtoId
         * @return ImmutableArcInst with replaced PortProtoIds and Locations
         */
        ImmutableArcInst replaceArc(ImmutableArcInst a, PortProtoId newTailPortId, PortProtoId newHeadPortId)
        {
            // compute end points after nodes on ends were modified
            EPoint newTailPoint = a.tailLocation;
            if (newTailPortId != a.tailPortId)
            {
                assert newTailPortId != null;
                ImmutableNodeInst n = replacedNodes.get(a.tailNodeId);
                Poly poly = getShapeOfPort(n, newTailPortId);
                if (!poly.isInside(a.tailLocation))
                    newTailPoint = poly.getCenter();
            }
            EPoint newHeadPoint = a.headLocation;
            if (newHeadPortId != a.headPortId)
            {
                assert newHeadPortId != null;
                ImmutableNodeInst n = replacedNodes.get(a.headNodeId);
                Poly poly = getShapeOfPort(n, newHeadPortId);
                if (!poly.isInside(a.headLocation))
                    newHeadPoint = poly.getCenter();
            }

            // see if a bend must be made in the wire
            boolean zigzag = a.isFixedAngle()
                && (newTailPoint.getGridX() != newHeadPoint.getGridX() || newTailPoint.getGridY() != newHeadPoint.getGridY())
                && GenMath.figureAngle(newTailPoint, newHeadPoint) % 1800 != a.getDefinedAngle() % 1800;

            // see if a bend can be a straight by some simple manipulations
            if (zigzag && !a.isRigid() && (a.getDefinedAngle() % 900) == 0
                && ((newTailPortId == a.tailPortId) || (newHeadPortId == a.headPortId)))
            {
                // find the node at the other end
                boolean otherIsHead = newHeadPortId == a.headPortId;
                int otherNodeId = otherIsHead ? a.headNodeId : a.tailNodeId;
                ImmutableNodeInst adjustThisNode = oldCellRevision.getNodeById(otherNodeId);
                if (!replacedNodes.containsKey(otherNodeId) && !oldCellRevision.hasExportsOnNode(adjustThisNode))
                {
                    // other end not exported, see if all arcs can be adjusted
                    BitSet headEnds = new BitSet();
                    List<ImmutableArcInst> connList = oldCellRevision.getConnectionsOnNode(headEnds, adjustThisNode);
                    // test other arcs on adjustThisNode
                    boolean adjustable = true;
                    for (ImmutableArcInst oa : connList)
                    {
                        boolean adj =
                            (oa == a) || // skip test for the arc being replaced
                            (oa.tailNodeId != adjustThisNode.nodeId || oa.headNodeId != adjustThisNode.nodeId) && // only one end on adjustThisNode
                            !replacedArcs.containsKey(oa.arcId) && // not replaced yet
                            !oa.isRigid() && // not rigid
                            oa.getDefinedAngle() % 900 == 0 && // manhattan
                            ((a.getDefinedAngle() / 900) & 1) != ((oa.getDefinedAngle() / 900) & 1); // orthogonal
                        adjustable = adjustable && adj;
                    }
                    if (adjustable)
                    {
                        EPoint newPoint;
                        long dX, dY;
                        if (a.getDefinedAngle() % 1800 == 0)
                        {
                            // horizontal arc: move the other node vertically
                            if (otherIsHead)
                            {
                                newPoint = EPoint.fromGrid(newHeadPoint.getGridX(), newTailPoint.getGridY());
                                dX = 0L;
                                dY = newTailPoint.getGridY() - newHeadPoint.getGridY();
                            } else
                            {
                                newPoint = EPoint.fromGrid(newTailPoint.getGridX(), newHeadPoint.getGridY());
                                dX = 0L;
                                dY = newHeadPoint.getGridY() - newTailPoint.getGridY();
                            }
//              dY = newPoint[1 - otherEnd].getY() - newPoint[otherEnd].getY();
//              newPoint[otherEnd] = EPoint.fromLambda(newPoint[otherEnd].getX(), newPoint[1 - otherEnd].getY());
                        } else
                        {
                            // vertical arc: move the other node horizontally
                            if (otherIsHead)
                            {
                                newPoint = EPoint.fromGrid(newTailPoint.getGridX(), newHeadPoint.getGridY());
                                dX = newTailPoint.getGridX() - newHeadPoint.getGridX();
                                dY = 0L;
                            } else
                            {
                                newPoint = EPoint.fromGrid(newHeadPoint.getGridX(), newTailPoint.getGridY());
                                dX = newHeadPoint.getGridX() - newTailPoint.getGridX();
                                dY = 0L;
                            }
//              dX = newPoint[1 - otherEnd].getX() - newPoint[otherEnd].getX();
//              newPoint[otherEnd] = EPoint.fromLambda(newPoint[1 - otherEnd].getX(), newPoint[otherEnd].getY());
                        }
                        if (otherIsHead)
                        {
                            newHeadPoint = newPoint;
                        } else
                        {
                            newTailPoint = newPoint;
                        }

                        // special case where the old arc must be deleted first so that the other node can move
                        assert !replacedNodes.containsKey(adjustThisNode.nodeId);
                        EPoint oldAnchor = adjustThisNode.anchor;
                        EPoint newAnchor = EPoint.fromGrid(oldAnchor.getGridX() + dX, oldAnchor.getGridY() + dY);
                        replacedNodes.put(adjustThisNode.nodeId, adjustThisNode.withAnchor(newAnchor));
                        for (int connInd = 0; connInd < connList.size(); connInd++)
                        {
                            ImmutableArcInst oa = connList.get(connInd);
                            assert !replacedArcs.containsKey(oa.arcId);
                            if (oa != a)
                            {
                                ImmutableArcInst newOA = headEnds.get(connInd) ? oa.withLocations(oa.tailLocation, newPoint) : oa.withLocations(newPoint, oa.headLocation);
                                replacedArcs.put(oa.arcId, newOA);
                            }
                        }
                        return a.withConnections(a.tailNodeId, newTailPortId, a.headNodeId, newHeadPortId).withLocations(newTailPoint, newHeadPoint);
                    }
                }
            }

            if (zigzag)
            {
                // make that two wires
                EPoint c = EPoint.fromGrid(newTailPoint.getGridX(), newHeadPoint.getGridY());
                ArcProto ap = oldTechPool.getArcProto(a.protoId);
                PrimitiveNode pinNp = ap.findOverridablePinProto(ep);
                PrimitivePortId pinPpId = pinNp.getPort(0).getId();
                double psx = pinNp.getDefWidth(ep);
                double psy = pinNp.getDefHeight(ep);

                // create a pin
                int pinId = ++lastNodeId;
                MaxNodeSuffix maxSuffix = getMaxSuffix(pinNp.getId(), PrimitiveNode.Function.UNKNOWN);
                Name name = maxSuffix.getNextName();
                TextDescriptor nameTd = ep.getNodeTextDescriptor();
                Orientation orient = Orientation.IDENT;
                EPoint anchor = c;
                EPoint size = pinNp.getDefSize(ep);
                int flags = 0;
                int techBits = 0;
                TextDescriptor protoTd = ep.getInstanceTextDescriptor();
                ImmutableNodeInst pinN = ImmutableNodeInst.newInstance(pinId, pinNp.getId(), name, nameTd,
                    orient, anchor, size, flags, techBits, protoTd);
                maxSuffix.add(pinN);
                curNodesCount++;

                // create two arcs
                ImmutableArcInst newAi1 = a.withConnections(pinId, pinPpId, a.headNodeId, newHeadPortId).withLocations(c, newHeadPoint);
//                val newAi = ArcInst.newInstanceBase(ai.getProto(), ep, ai.getLambdaBaseWidth(), newPortInst[ArcInst.HEADEND], pinPi, newPoint[ArcInst.HEADEND],
//                        new Point2D.Double(cX, cY), null, ArcInst.DEFAULTANGLE);
                ImmutableArcInst newAi2 = a.withConnections(pinId, pinPpId, a.tailNodeId, newTailPortId).withLocations(c, newTailPoint);
//                ArcInst newAi2 = ArcInst.newInstanceBase(ai.getProto(), ep, ai.getLambdaBaseWidth(), pinPi, newPortInst[ArcInst.TAILEND], new Point2D.Double(cX, cY),
//                        newPoint[ArcInst.TAILEND], null, ArcInst.DEFAULTANGLE);

                // the arc connected to node will save its name, the other arc will obtain temporary name
                int arcId = ++lastArcId;
                assert maxArcSuffix < Integer.MAX_VALUE;
                maxArcSuffix++;
                Name arcName = ImmutableArcInst.BASENAME.findSuffixed(maxArcSuffix);
                if (newTailPortId == a.tailPortId)
                {
                    addedArcs.add(newAi1.withArcId(arcId).withName(arcName));
//                  assert !curArcs.get(arcId);
//                  curArcs.set(arcId);
                    curArcsCount++;
                    return newAi2;
                } else
                {
                    addedArcs.add(newAi2.withArcId(arcId).withName(arcName));
//                  assert !curArcs.get(arcId);
//                  curArcs.set(arcId);
                    curArcsCount++;
                    return newAi1;
                }
            } else
            {
                // replace the arc with another arc
                return a.withConnections(a.tailNodeId, newTailPortId, a.headNodeId, newHeadPortId).withLocations(newTailPoint, newHeadPoint);
            }
        }

        CellBackup commit()
        {
            // commit nodes
            ImmutableNodeInst[] newNodes = new ImmutableNodeInst[curNodesCount];
            Iterator<ImmutableNodeInst> nit = oldCellRevision.nodes.iterator();
            int oldNodeIndex = 0;
            int newNodeIndex = 0;
            for (MaxNodeSuffix maxSuffix : maxNodeSuffixesOrdered.values())
            {
                while (oldNodeIndex < maxSuffix.insertionPoint)
                {
                    ImmutableNodeInst n = nit.next();
                    if (!deletedNodeInds.get(oldNodeIndex))
                    {
                        ImmutableNodeInst newN = replacedNodes.get(n.nodeId);
                        newNodes[newNodeIndex++] = newN != null ? newN : n;
                    }
                    oldNodeIndex++;
                }
                for (ImmutableNodeInst n : maxSuffix.addedNodes)
                {
//          assert(curNodes.get(n.nodeId))
                    newNodes[newNodeIndex++] = n;
                }
            }
            while (oldNodeIndex < oldCellRevision.nodes.size())
            {
                ImmutableNodeInst n = nit.next();
                if (!deletedNodeInds.get(oldNodeIndex))
                {
                    ImmutableNodeInst newN = replacedNodes.get(n.nodeId);
                    newNodes[newNodeIndex++] = newN != null ? newN : n;
                }
                oldNodeIndex++;
            }
            assert !nit.hasNext();
            assert newNodeIndex == newNodes.length;

            // commit arcs
            ImmutableArcInst[] newArcs = new ImmutableArcInst[curArcsCount];
            Iterator<ImmutableArcInst> ait = oldCellRevision.arcs.iterator();
            int oldArcIndex = 0;
            int newArcIndex = 0;
            while (ait.hasNext() && oldArcIndex < arcInsertionPoint)
            {
                ImmutableArcInst a = ait.next();
                if (!deletedArcInds.get(oldArcIndex))
                {
                    ImmutableArcInst newA = replacedArcs.get(a.arcId);
                    newArcs[newArcIndex++] = newA != null ? newA : a;
                }
                oldArcIndex++;
            }
            for (ImmutableArcInst a : addedArcs)
            {
//        assert(curArcs(a.arcId))
                newArcs[newArcIndex++] = a;
            }
            assert oldArcIndex == arcInsertionPoint;
            while (oldArcIndex < oldCellRevision.arcs.size())
            {
                ImmutableArcInst a = ait.next();
                if (!deletedArcInds.get(oldArcIndex))
                {
                    ImmutableArcInst newA = replacedArcs.get(a.arcId);
                    newArcs[newArcIndex++] = newA != null ? newA : a;
                }
                oldArcIndex++;
            }
            assert !ait.hasNext();
            assert newArcIndex == newArcs.length;
            // commit exports
            ImmutableExport[] newExports = new ImmutableExport[curExportsCount];
            Iterator<ImmutableExport> eit = oldCellRevision.exports.iterator();
            int oldExportIndex = 0;
            int newExportIndex = 0;
            while (oldExportIndex < oldCellRevision.exports.size())
            {
                ImmutableExport e = eit.next();
                if (!deletedExportInds.get(oldExportIndex))
                {
                    ImmutableExport newE = replacedExports.get(e.exportId);
                    newExports[newExportIndex++] = newE != null ? newE : e;
                }
                oldExportIndex++;
            }
            assert !eit.hasNext();
            assert newExportIndex == newExports.length;

            return oldCellBackup.with(oldCell, newNodes, newArcs, newExports, oldTechPool);
        }

        /**
         * Find new immutable PortInst
         *
         * @param replacements map from nodeId to NodeReplacement
         * @param nodeId nodeId of node
         * @param ppId PortProtoId of old PortInst
         * @reutn PortProtoId of new PortInst
         */
        PortProtoId portMap(Map<Integer, BatchChanges.NodeReplacement> replacements, int nodeId, PortProtoId ppId)
        {
            BatchChanges.NodeReplacement r = replacements.get(nodeId);
            if (r == null)
                return ppId;
            ImmutableNodeInst oldN = oldCellRevision.getNodeById(nodeId);
            assert ppId.parentId == oldN.protoId;
            int portIndex;
            if (ppId instanceof ExportId)
            {
                ExportId exportId = (ExportId)ppId;
                portIndex = oldSnapshot.getCellRevision(exportId.getParentId()).getExportIndexByExportId(exportId);
            } else
            {
                portIndex = oldTechPool.getPrimitivePort((PrimitivePortId)ppId).getPortIndex();
            }
            return r.assoc[portIndex];
        }

        /**
         * Search a split index in original alphanumerical list of nodes
         * where to put temporary nodes with specified basename
         *
         * @param basename speicified basename
         * @return split index
         */
        int searchNodeInsertionPoint(String basename)
        {
            assert basename.endsWith("@0");
            char nextChar = (char)(basename.charAt(basename.length() - 2) + 1);
            String nextName = basename.substring(0, basename.length() - 2) + nextChar;
            int index = oldCellRevision.nodes.searchByName(nextName);
            return index >= 0 ? index : -(index + 1);
        }

        /**
         * Search a split index in original alphanumerical list of arcs
         * where to put temporary nodes with specified basename
         *
         * @param basename speicified basename
         * @return split index
         */
        private int searchArcInsertionPoint(String basename)
        {
            assert basename.endsWith("@0");
            char nextChar = (char)(basename.charAt(basename.length() - 2) + 1);
            String nextName = basename.substring(0, basename.length() - 2) + nextChar;
            int index = oldCellRevision.arcs.searchByName(nextName);
            return index >= 0 ? index : -(index + 1);
        }
    }

    /**
     * Class stores temp nodes with specified basename
     */
    static class MaxNodeSuffix
    {
        final ReplaceBuilder.CellBuilder b;
        final Name basename;
        int insertionPoint;
        int maxSuffix = -1;
        final List<ImmutableNodeInst> addedNodes = new ArrayList<ImmutableNodeInst>();

        /**
         * @param b CellBuilder where temp nodes are inserted
         * @param basename specified basenaem
         */
        MaxNodeSuffix(ReplaceBuilder.CellBuilder b, Name basename)
        {
            this.b = b;
            this.basename = basename;
            insertionPoint = b.searchNodeInsertionPoint(basename.toString());
            if (insertionPoint > 0)
            {
                Name name = b.oldCellRevision.nodes.get(insertionPoint - 1).name;
                if (name.isTempname() && name.getBasename() == basename)
                {
                    maxSuffix = name.getNumSuffix();
                }
            }
        }

        /**
         * get name of next temporary node
         */
        Name getNextName()
        {
            return basename.findSuffixed(maxSuffix + 1);
        }

        /**
         * insert temporary node and adjust name of next node
         *
         * @param n temporary node
         */
        void add(ImmutableNodeInst n)
        {
            maxSuffix += 1;
            assert n.name.getBasename() == basename;
            assert n.name.getNumSuffix() == maxSuffix;
            addedNodes.add(n);
        }
    }
}
