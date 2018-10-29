/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: SeaOfGatesCellBuilder.java
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
package com.sun.electric.tool.routing.seaOfGates;

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
import com.sun.electric.database.geometry.EGraphics;
import com.sun.electric.database.geometry.EPoint;
import com.sun.electric.database.geometry.Poly;
import com.sun.electric.database.hierarchy.View;
import com.sun.electric.database.id.ArcProtoId;
import com.sun.electric.database.id.CellId;
import com.sun.electric.database.id.ExportId;
import com.sun.electric.database.id.NodeProtoId;
import com.sun.electric.database.id.PortProtoId;
import com.sun.electric.database.id.PrimitiveNodeId;
import com.sun.electric.database.prototype.PortCharacteristic;
import com.sun.electric.database.text.CellName;
import com.sun.electric.database.text.Name;
import com.sun.electric.database.variable.TextDescriptor;
import com.sun.electric.technology.AbstractShapeBuilder;
import com.sun.electric.technology.Layer;
import com.sun.electric.technology.PrimitiveNode;
import com.sun.electric.technology.PrimitivePort;
import com.sun.electric.technology.TechPool;
import com.sun.electric.technology.technologies.Generic;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.Tool;
import com.sun.electric.tool.routing.Routing.SoGContactsStrategy;
import com.sun.electric.tool.routing.seaOfGates.SeaOfGatesHandlers.ContactKey;
import com.sun.electric.tool.routing.seaOfGates.SeaOfGatesHandlers.ContactTemplate;
import com.sun.electric.util.math.Orientation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Class for creating final circuitry in Sea of Gates router.
 * @author Dmitry Nadezhin
 */
class SeaOfGatesCellBuilder {

	private static final boolean FINDEXISTINGCELLS = true;

	private final EditingPreferences ep;

	// oldSnapshot
    private final Snapshot oldSnapshot;
    private final Tool oldTool;
    private final Environment oldEnvironment;
    private final CellTree oldCellTree;
    private final CellBackup oldCellBackup;
    private final TechPool oldTechPool;
    private final CellRevision oldCellRevision;
    private final ImmutableCell oldCell;
    final CellId cellId;
    final String resultCellName;
    final SoGContactsStrategy contactPlacementAction;

    // curSnapshot
    private Snapshot curSnapshot;
    private final List<CellBackup> curCellBackups;
    private CellBackup curCellBackup;
    private Map<ContactKey,ContactTemplate> contactTemplates = new HashMap<ContactKey,ContactTemplate>();

    // nodes
    private final BitSet curNodes = new BitSet();
    private int curNodesCount;
    private int lastNodeId;
    private final TreeMap<Name, MaxNodeSuffix> maxNodeSuffixesOrdered = new TreeMap<Name, MaxNodeSuffix>(Name.STRING_NUMBER_ORDER);
    private final IdentityHashMap<PrimitiveNodeId, MaxNodeSuffix> maxNodeSuffixes = new IdentityHashMap<PrimitiveNodeId, MaxNodeSuffix>();
    private final IdentityHashMap<SeaOfGatesEngine.RouteNode, ImmutableNodeInst> addedNodesToNodeInst = new IdentityHashMap<SeaOfGatesEngine.RouteNode, ImmutableNodeInst>();
    private final IdentityHashMap<SeaOfGatesEngine.RouteNode, PortProtoId> addedNodesToPortProtoId = new IdentityHashMap<SeaOfGatesEngine.RouteNode, PortProtoId>();

    // arcs
    private final List<ImmutableArcInst> curArcs = new ArrayList<ImmutableArcInst>();
    private int maxArcSuffix;

    // result CellBackup
    private final MyShapeBuilder resultShapeBuilder;

    SeaOfGatesCellBuilder(Snapshot snapshot, CellId cellId, String resultCellName, SoGContactsStrategy contactPlacementAction, EditingPreferences ep) {
        this.ep = ep;

        this.cellId = cellId;
        this.resultCellName = resultCellName;
        this.contactPlacementAction = contactPlacementAction;

        oldSnapshot = snapshot;
        oldTool = oldSnapshot.tool;
        oldEnvironment = oldSnapshot.environment;
        oldCellTree = snapshot.getCellTree(cellId);
        oldCellBackup = oldCellTree.top;
        oldTechPool = oldCellTree.techPool;
        oldCellRevision = oldCellBackup.cellRevision;
        oldCell = oldCellRevision.d;

        curCellBackups = new ArrayList<CellBackup>();
        if (resultCellName != null) {
            CellId resultCellId = cellId.libId.newCellId(CellName.newName(resultCellName, View.LAYOUT, 1));
            while (curCellBackups.size() <= resultCellId.cellIndex) {
                curCellBackups.add(null);
            }
            resultShapeBuilder = new MyShapeBuilder(resultCellId);
        } else {
            resultShapeBuilder = null;
        }
        for (CellTree cellTree : oldSnapshot.cellTrees) {
            if (cellTree != null) {
                int cellIndex = cellTree.top.cellRevision.d.cellId.cellIndex;
                while (curCellBackups.size() <= cellIndex) {
                    curCellBackups.add(null);
                }
                curCellBackups.set(cellIndex, cellTree.top);
            }
        }

        lastNodeId = -1;
        for (ImmutableNodeInst n : oldCellRevision.nodes) {
            int nodeId = n.nodeId;
            assert !curNodes.get(nodeId);
            curNodes.set(nodeId);
            lastNodeId = Math.max(lastNodeId, nodeId);
        }
        curNodesCount = oldCellRevision.nodes.size();
        if (resultShapeBuilder != null) {
            CellId resultCellId = resultShapeBuilder.cellId;
            curCellBackups.set(resultCellId.cellIndex, resultShapeBuilder.commit());
            // Instantiate result cell into original cell
            int nodeId = ++lastNodeId;
            Name baseName = resultCellId.cellName.getBasename();
            MaxNodeSuffix maxSuffix = maxNodeSuffixesOrdered.get(baseName);
            if (maxSuffix == null) {
                maxSuffix = new MaxNodeSuffix(this, baseName);
                maxNodeSuffixesOrdered.put(baseName, maxSuffix);
            }
            Name name = maxSuffix.getNextName();
            TextDescriptor nameTd = ep.getNodeTextDescriptor();
            Orientation orient = Orientation.IDENT;
            EPoint anchor = EPoint.ORIGIN;
            EPoint size = EPoint.ORIGIN;
            int flags = 0;
            int techBits = 0;
            TextDescriptor protoTd = ep.getInstanceTextDescriptor();
            ImmutableNodeInst n = ImmutableNodeInst.newInstance(nodeId, resultCellId, name, nameTd,
                orient, anchor, size, flags, techBits, protoTd);
            maxSuffix.add(n);
            assert !curNodes.get(nodeId);
            curNodes.set(nodeId);
            curNodesCount++;
        }

        maxArcSuffix = -1;
        for (ImmutableArcInst a : oldCellRevision.arcs) {
            int arcId = a.arcId;
            while (curArcs.size() <= arcId)
                curArcs.add(null);
            assert curArcs.get(arcId) == null;
            curArcs.set(arcId, a);
            Name name = a.name;
            if (name.isTempname()) {
                assert name.getBasename() == ImmutableArcInst.BASENAME;
                maxArcSuffix = Math.max(maxArcSuffix, name.getNumSuffix());
            }
        }

        curSnapshot = oldSnapshot;
        curCellBackup = oldCellBackup;
    }

	/**
	 * Method to find a contact template to describe a given primitive node, when encapsulated
	 * @param pNp the contact to package.
	 * @param size the size of the contact.
     * @param techBits technology bits of the contact
	 * @return a ContactTemplate that describes that packaged contact.
	 */
	private ContactTemplate getTemplateForContact(PrimitiveNode pNp, EPoint size, int techBits)
    {
		SeaOfGatesHandlers.ContactKey contactKey = new SeaOfGatesHandlers.ContactKey(pNp, size, techBits);
        return contactTemplates.get(contactKey);
    }

	/**
	 * Method to create a CellId to describe a given primitive node, when encapsulated
	 * @param pNp the contact to package.
	 * @param size the size of the contact.
     * @param techBits technology bits of the contact
	 * @return a ContactTemplate that describes that packaged contact.
	 */
	private void makeTemplateForContact(PrimitiveNode pNp, EPoint size, int techBits)
    {
        assert pNp.getFunction().isContact();
        SeaOfGatesHandlers.ContactKey contactKey = new SeaOfGatesHandlers.ContactKey(pNp, size, techBits);

        Name nodeName = pNp.getPrimitiveFunction(techBits).getBasename().findSuffixed(0);
        nodeName = (contactPlacementAction == SoGContactsStrategy.SOGCONTACTSUSEEXISTINGSUBCELLS) ?
        		pNp.getPrimitiveFunction(techBits).getBasename().findSuffixed(0) : Name.findName(contactKey.toString());
        CellBackup contactCellBackup = null;
        Orientation contactTemplateOrientation = Orientation.IDENT;
        if (FINDEXISTINGCELLS && contactPlacementAction != SoGContactsStrategy.SOGCONTACTSFORCESUBCELLS)
        {
            ImmutableNodeInst n = ImmutableNodeInst.newInstance(0, pNp.getId(), nodeName, TextDescriptor.EMPTY, Orientation.IDENT,
                EPoint.ORIGIN, size, 0, techBits, TextDescriptor.EMPTY);
            MyShapeBuilder conShape = new MyShapeBuilder();
            pNp.genShape(conShape, n);
            MyShapeBuilder conShapeRot = new MyShapeBuilder();
            pNp.genShape(conShapeRot, n.withOrient(Orientation.R));
            for (CellBackup cellBackup: curCellBackups)
            {
                if (cellBackup == null) continue;
                if (cellBackup.cellRevision.d.cellId.libId != cellId.libId) continue;
                if (doesCellMatch(cellBackup, conShape))
                {
                    contactCellBackup = cellBackup;
                    break;
                }
                if (doesCellMatch(cellBackup, conShapeRot))
                {
                    contactCellBackup = cellBackup;
                    contactTemplateOrientation = Orientation.R;
                    break;
                }
            }
        }

        CellId contactCellId;
        if (contactCellBackup != null)
        {
            // Contact cell found
            contactCellId = contactCellBackup.cellRevision.d.cellId;
            if (!contactCellBackup.cellRevision.exports.isEmpty())
            {
                // Contact cell has exports, Pick the first.
                ExportId eId = contactCellBackup.cellRevision.exports.iterator().next().exportId;
                SeaOfGatesHandlers.ContactTemplate contactTemplate = new SeaOfGatesHandlers.ContactTemplate(contactCellId, contactTemplateOrientation, eId);
                contactTemplates.put(contactKey, contactTemplate);
                return;
            }
            // Instantiate universal pin instead of pure layer node
            pNp = Generic.tech().universalPinNode;
            nodeName = (contactPlacementAction == SoGContactsStrategy.SOGCONTACTSUSEEXISTINGSUBCELLS) ?
            		pNp.getPrimitiveFunction(techBits).getBasename().findSuffixed(0) : Name.findName(contactKey.toString());
        } else
        {
        	if (contactPlacementAction == SoGContactsStrategy.SOGCONTACTSUSEEXISTINGSUBCELLS) return;
            // Create a new contact cell
        	// Use new name strategy here
            contactCellId = cellId.libId.newCellId(contactKey.getDefaultCellName());
            while (curCellBackups.size() <= contactCellId.cellIndex) {
                curCellBackups.add(null);
            }
            ImmutableCell c = ImmutableCell.newInstance(contactCellId, System.currentTimeMillis()).withTechId(oldCell.techId);
            contactCellBackup = CellBackup.newInstance(c, oldTechPool);
            curCellBackups.set(contactCellId.cellIndex, contactCellBackup);
        }

        // Create ImmutableNodeInst
        int nodeId = contactCellBackup.cellRevision.getMaxNodeId() + 1;
        TextDescriptor nameTd = ep.getNodeTextDescriptor();
        EPoint anchor = EPoint.ORIGIN;
        int flags = 0;
        TextDescriptor protoTd = ep.getInstanceTextDescriptor();
        ImmutableNodeInst n =
            ImmutableNodeInst.newInstance(nodeId, pNp.getId(), nodeName, nameTd, Orientation.IDENT,
                anchor, size, flags, techBits, protoTd);

        // Create ImmuatbleExport
        String portName = "port";
        ExportId exportId = contactCellId.newPortId(portName);
        TextDescriptor portTd = ep.getExportTextDescriptor();
        boolean alwaysDrawn = false;
        boolean bodyOnly = false;
        ImmutableExport e = ImmutableExport.newInstance(exportId, Name.findName(portName), portTd,
            nodeId, pNp.getPort(0).getId(), alwaysDrawn, bodyOnly, PortCharacteristic.UNKNOWN);

        // Insert node instance into new nodes array
        ImmutableNodeInst[] nodesArray = new ImmutableNodeInst[contactCellBackup.cellRevision.nodes.size() + 1];
        ImmutableNodeInst nodeToInsert = n;
        int nodeIndex = 0;
        for (Iterator<ImmutableNodeInst> it = contactCellBackup.cellRevision.nodes.iterator(); it.hasNext(); )
        {
            ImmutableNodeInst nextN = it.next();
            if (nodeToInsert != null && Name.STRING_NUMBER_ORDER.compare(nodeToInsert.name, nextN.name) <= 0)
            {
                assert nodeToInsert.name != nextN.name;
                nodesArray[nodeIndex++] = nodeToInsert;
                nodeToInsert = null;
            }
            nodesArray[nodeIndex++] = nextN;
        }
        if (nodeToInsert != null)
        {
            nodesArray[nodeIndex++] = nodeToInsert;
        }
        assert nodeIndex == nodesArray.length;

        // Update CellBackup
        ImmutableExport[] exportsArray = { e };
        contactCellBackup = contactCellBackup.with(contactCellBackup.cellRevision.d,
            nodesArray, null, exportsArray, oldTechPool);
        curCellBackups.set(contactCellId.cellIndex, contactCellBackup);

        // save ContactTemplate
        SeaOfGatesHandlers.ContactTemplate contactTemplate = new SeaOfGatesHandlers.ContactTemplate(contactCellId, contactTemplateOrientation, exportId);
        contactTemplates.put(contactKey, contactTemplate);
    }

    private boolean doesCellMatch(CellBackup contactCellBackup, MyShapeBuilder sb)
    {
        List<ImmutableNodeInst> shapeNodes = sb.nodes;
        BitSet matched = new BitSet();
        for (ImmutableNodeInst n: contactCellBackup.cellRevision.nodes)
        {
            if (!(n.protoId instanceof PrimitiveNodeId)) continue;
            PrimitiveNode pn = oldTechPool.getPrimitiveNode((PrimitiveNodeId) n.protoId);
            if (pn.getTechnology() == Generic.tech()) continue;
            if (pn.getFunction() != PrimitiveNode.Function.NODE) return false;
            if (n.getTrace() != null) return false;
            boolean foundMatch = false;
			for(int i=0; i<shapeNodes.size(); i++)
			{
				if (matched.get(i)) continue;
                ImmutableNodeInst sn = shapeNodes.get(i);
                if (n.protoId != sn.protoId) continue;
                if (!n.size.equals(sn.size)) continue;
                if (!n.anchor.equals(sn.anchor)) continue;
                matched.set(i);
				foundMatch = true;
				break;
			}
            if (!foundMatch)
                return false;
        }
        return matched.cardinality() == shapeNodes.size();
    }

	/**
     * Method to instantiate RouteResolution
     * @param resolution RouteResolution
     */
    synchronized void instantiate(SeaOfGatesEngine.RouteResolution resolution) {
    	// if placing contacts in subcells, create them now
    	if (contactPlacementAction != SoGContactsStrategy.SOGCONTACTSATTOPLEVEL)
    	{
	        for (SeaOfGatesEngine.RouteNode rn : resolution.nodesToRoute)
	        {
	            if (rn.exists()) continue;
	            PrimitiveNodeId protoId = (PrimitiveNodeId) rn.getProtoId();
	            PrimitiveNode pNp = oldTechPool.getPrimitiveNode(protoId);
	            if (!pNp.getFunction().isContact()) continue;
	            SeaOfGatesHandlers.ContactTemplate contactTemplate = getTemplateForContact(pNp, rn.getSize(), rn.getTechBits());
                if (contactTemplate == null)
                {
                    makeTemplateForContact(pNp, rn.getSize(), rn.getTechBits());
                }
	        }
    	}

        // if placing results in original cell (typical) then delete unrouted arcs in that cell
        if (resultShapeBuilder == null)
        {
            for (int nodeId : resolution.nodesIDsToKill)
            {
                assert curNodes.get(nodeId);
                curNodes.clear(nodeId);
                curNodesCount--;
            }

            for (int arcId : resolution.arcsIDsToKill)
            {
                assert curArcs.get(arcId) != null;
                curArcs.set(arcId, null);
            }
        }

        for (SeaOfGatesEngine.RouteNode rn : resolution.nodesToRoute) {
            if (rn.exists()) {
                continue;
            }
            int nodeId = ++lastNodeId;
            PrimitiveNodeId pnId = (PrimitiveNodeId) rn.getProtoId();
            NodeProtoId protoId = pnId;
            PortProtoId ppId = pnId.getPortId(0);

            // use subcells if requested
            PrimitiveNode pNp = oldTechPool.getPrimitiveNode(pnId);
            Orientation orient = Orientation.IDENT;
            if (pNp.getFunction().isContact())
            {
            	SeaOfGatesHandlers.ContactTemplate contactTemplate = getTemplateForContact(pNp, rn.getSize(), rn.getTechBits());
	            if (contactTemplate != null)
	            {
	            	// place cell "c" instead of primitive "protoId"
                    protoId = contactTemplate.cellId;
                    orient = contactTemplate.orient;
                    ppId = contactTemplate.exportId;
	            }
            }

            MaxNodeSuffix maxSuffix = maxNodeSuffixes.get(pnId);
            if (maxSuffix == null) {
                Name baseName = rn.getBaseName();
                maxSuffix = maxNodeSuffixesOrdered.get(baseName);
                if (maxSuffix == null) {
                    maxSuffix = new MaxNodeSuffix(this, baseName);
                    maxNodeSuffixesOrdered.put(baseName, maxSuffix);
                }
                maxNodeSuffixes.put(pnId, maxSuffix);
            }
            Name name = maxSuffix.getNextName();
            TextDescriptor nameTd = ep.getNodeTextDescriptor();
            orient = rn.getOrient().concatenate(orient);
            EPoint anchor = rn.getLoc();
            EPoint size = rn.getSize();
            int flags = 0;
            int techBits = rn.getTechBits();
            TextDescriptor protoTd = ep.getInstanceTextDescriptor();
            if (resultShapeBuilder != null && protoId != pnId) {
                nodeId = resultShapeBuilder.getNextNodeId();
                name = resultShapeBuilder.getNextName();
            }
            ImmutableNodeInst n = ImmutableNodeInst.newInstance(nodeId, protoId, name, nameTd,
                orient, anchor, size, flags, techBits, protoTd);
            if (resultShapeBuilder != null) {
                if (protoId != pnId) {
                    resultShapeBuilder.nodes.add(n);
                } else {
					int startNodeId = resultShapeBuilder.getNextNodeId();
                    oldTechPool.getPrimitiveNode(pnId).genShape(resultShapeBuilder, n);
					n = resultShapeBuilder.nodes.get(startNodeId);
                }
            } else {
              maxSuffix.add(n);
              assert !curNodes.get(nodeId);
              curNodes.set(nodeId);
              curNodesCount++;
            }
            rn.setTapConnection(n);
            addedNodesToNodeInst.put(rn, n);
            addedNodesToPortProtoId.put(rn, ppId);
        }

        for (SeaOfGatesEngine.RouteArc ra : resolution.arcsToRoute) {
            int arcId = curArcs.size();
            ArcProtoId protoId = ra.getProtoId();
            assert maxArcSuffix < Integer.MAX_VALUE;
            Name name = null;
            Name newName = null;
            if (ra.getName() != null) newName = Name.findName(ra.getName());
            if (newName != null && !newName.isTempname())
        	{
        		name = newName;
            } else
            {
            	name = ImmutableArcInst.BASENAME.findSuffixed(++maxArcSuffix);
            }
            TextDescriptor nameTd = ep.getArcTextDescriptor();

            SeaOfGatesEngine.RouteNode tail = ra.getTail();
            int tailNodeId = tail.exists() ? tail.getNodeId() : addedNodesToNodeInst.get(tail).nodeId;
            PortProtoId tailProtoId = tail.exists() ? tail.getPortProtoId() : addedNodesToPortProtoId.get(tail);
            EPoint tailLocation = tail.getLoc();

            SeaOfGatesEngine.RouteNode head = ra.getHead();
            int headNodeId = head.exists() ? head.getNodeId() : addedNodesToNodeInst.get(head).nodeId;
            PortProtoId headProtoId = head.exists() ? head.getPortProtoId() : addedNodesToPortProtoId.get(head);
            EPoint headLocation = head.getLoc();

            long gridExtendOverMin = ra.getGridExtendOverMin();
            int angle = ImmutableArcInst.DEFAULTANGLE;
            int flags = ra.getFlags(ep);

            ImmutableArcInst a = ImmutableArcInst.newInstance(arcId, protoId, name, nameTd,
                tailNodeId, tailProtoId, tailLocation,
                headNodeId, headProtoId, headLocation,
                gridExtendOverMin, angle, flags);
            if (resultShapeBuilder != null) {
                resultShapeBuilder.genShapeOfArc(a);
            } else {
                curArcs.add(a);
            }
        }

    	// if routing into original cell, add unrouted arcs that didn't get routed
        if (resultShapeBuilder != null) return;
        ArcProtoId unroutedId = oldTechPool.getGeneric().unrouted_arc.getId();
        long unroutedGridExtend = oldTechPool.getGeneric().unrouted_arc.getDefaultInst(ep).getGridExtendOverMin();
        int unroutedFlags = oldTechPool.getGeneric().unrouted_arc.getDefaultInst(ep).flags;
        Set<String> namesGiven = new HashSet<String>();
        for (SeaOfGatesEngine.RouteAddUnrouted rau : resolution.unroutedToAdd.keySet()) {
            int arcId = curArcs.size();
            ArcProtoId protoId = unroutedId;

            String newName = resolution.unroutedToAdd.get(rau);
            int breakPos = newName.indexOf(' ');
            if (breakPos > 0) newName = newName.substring(0, breakPos);
            Name name;
            if (namesGiven.contains(newName))
            {
				assert maxArcSuffix < Integer.MAX_VALUE;
				name = ImmutableArcInst.BASENAME.findSuffixed(++maxArcSuffix);
            } else
            {
                name = Name.findName(newName);
            	namesGiven.add(newName);
            }
            TextDescriptor nameTd = ep.getArcTextDescriptor();

            int tailNodeId = rau.getTailId();
            PortProtoId tailProtoId = rau.getTailPortProtoId();
            EPoint tailLocation = rau.getTailLocation();

            int headNodeId = rau.getHeadId();
            PortProtoId headProtoId = rau.getHeadPortProtoId();
            EPoint headLocation = rau.getHeadLocation();

            long gridExtendOverMin = unroutedGridExtend;
            int angle = ImmutableArcInst.DEFAULTANGLE;
            int flags = unroutedFlags;

            ImmutableArcInst a = ImmutableArcInst.newInstance(arcId, protoId, name, nameTd,
                    tailNodeId, tailProtoId, tailLocation,
                    headNodeId, headProtoId, headLocation,
                    gridExtendOverMin, angle, flags);
            curArcs.add(a);
        }
        resolution.clearRoutes();
    }

    private class MyShapeBuilder extends AbstractShapeBuilder {
        private final CellId cellId;
        private final TextDescriptor nameTd = ep.getNodeTextDescriptor();
        private final TextDescriptor protoTd = ep.getInstanceTextDescriptor();
        private CellBackup cellBackup;
        private final List<ImmutableNodeInst> nodes = new ArrayList<ImmutableNodeInst>();

        MyShapeBuilder() {
            cellId = null;
            cellBackup = null;
        }

        MyShapeBuilder(CellId cellId) {
            setup(oldTechPool);
            this.cellId = cellId;
            Date creationDate = new Date();
            ImmutableCell resultCell = ImmutableCell.newInstance(cellId, creationDate.getTime()).withTechId(oldCellBackup.cellRevision.d.techId);
            cellBackup = CellBackup.newInstance(resultCell, oldTechPool);
//            nodes.add(ImmutableNodeInst.newInstance(0, Generic.tech().cellCenterNode.getId(),
//                Name.findName("art@0"), nameTd,
//                Orientation.IDENT, EPoint.ORIGIN, EPoint.ORIGIN,
//                0, 0, protoTd));
        }

        int getNextNodeId() {
            return nodes.size();
        }

        Name getNextName() {
            return Name.findName("plnode@" + getNextNodeId());
        }

        @Override
        protected void addPoly(int numPoints, Poly.Type style, Layer layer, EGraphics graphicsOverride, PrimitivePort pp)
        {
        	if (style != Poly.Type.CROSSED || numPoints == 0)
        	{
        		if (Job.getDebug())
        			System.out.println("ERROR: Poly style is not cross or numPoints is zero: " + style + ", " + numPoints);
        		return; // something is wrong here.
        	}
            assert style == Poly.Type.CROSSED && numPoints > 0;
            long fixpLX = Long.MAX_VALUE;
            long fixpLY = Long.MAX_VALUE;
            long fixpHX = Long.MIN_VALUE;
            long fixpHY = Long.MIN_VALUE;
            for (int i = 0; i < numPoints; i++) {
                long x = coords[i*2];
                long y = coords[i*2 + 1];
                fixpLX = Math.min(fixpLX, x);
                fixpLY = Math.min(fixpLY, y);
                fixpHX = Math.max(fixpHX, x);
                fixpHY = Math.max(fixpHY, y);
            }
            assert fixpLX <= fixpHX && fixpLY <= fixpHY;
            int nodeId = getNextNodeId();
            PrimitiveNodeId protoId = layer.getPureLayerNode().getId();
            Name name = getNextName();
            EPoint anchor = EPoint.fromFixp((fixpLX + fixpHX) >> 1, (fixpLY + fixpHY) >> 1);
            EPoint size = EPoint.fromFixp(fixpHX - fixpLX, fixpHY - fixpLY);
            nodes.add(ImmutableNodeInst.newInstance(nodeId, protoId,
                name, nameTd,
                Orientation.IDENT, anchor, size,
                0, 0, protoTd));
        }

        @Override
        protected void addBox(Layer layer)
        {
            int nodeId = getNextNodeId();
            PrimitiveNodeId protoId = layer.getPureLayerNode().getId();
            Name name = getNextName();
            long fixpLX = coords[0];
            long fixpLY = coords[1];
            long fixpHX = coords[2];
            long fixpHY = coords[3];
            EPoint anchor = EPoint.fromFixp((fixpLX + fixpHX) >> 1, (fixpLY + fixpHY) >> 1);
            EPoint size = EPoint.fromFixp(fixpHX - fixpLX, fixpHY - fixpLY);
            nodes.add(ImmutableNodeInst.newInstance(nodeId, protoId,
                name, nameTd,
                Orientation.IDENT, anchor, size,
                0, 0, protoTd));
        }

        CellBackup commit() {
            cellBackup = cellBackup.with(cellBackup.cellRevision.d,
                nodes.toArray(new ImmutableNodeInst[nodes.size()]),
                null, null, oldTechPool);
            return cellBackup;
        }
    }

    Snapshot commit() {
        ImmutableNodeInst[] newNodes;
        ImmutableArcInst[] newArcs;

        synchronized (this) {
            newNodes = new ImmutableNodeInst[curNodesCount];
            int oldNodeIndex = 0;
            int newNodeIndex = 0;
            for (MaxNodeSuffix maxSuffix : maxNodeSuffixesOrdered.values()) {
                while (oldNodeIndex < maxSuffix.insertionPoint) {
                    ImmutableNodeInst n = oldCellRevision.nodes.get(oldNodeIndex++);
                    if (curNodes.get(n.nodeId)) {
                        newNodes[newNodeIndex++] = n;
                    }
                }
                for (ImmutableNodeInst n : maxSuffix.addedNodes) {
                    assert curNodes.get(n.nodeId);
                    newNodes[newNodeIndex++] = n;
                }
            }
            while (oldNodeIndex < oldCellRevision.nodes.size()) {
                ImmutableNodeInst n = oldCellRevision.nodes.get(oldNodeIndex++);
                if (curNodes.get(n.nodeId)) {
                    newNodes[newNodeIndex++] = n;
                }
            }
            assert newNodeIndex == newNodes.length;

            int arcCount = 0;
            for (ImmutableArcInst a: curArcs) {
                if (a != null)
                    arcCount++;
            }
            newArcs = new ImmutableArcInst[arcCount];
            arcCount = 0;
            for (ImmutableArcInst a: curArcs) {
                if (a != null)
                    newArcs[arcCount++] = a;
            }
            assert arcCount == newArcs.length;
            Arrays.sort(newArcs, ImmutableArcInst.ARCS_ORDER);
        }

        curCellBackup = curCellBackup.with(oldCell, newNodes, newArcs, null, oldTechPool);
        curCellBackups.set(cellId.cellIndex, curCellBackup);
        if (resultShapeBuilder != null) {
            curCellBackups.set(resultShapeBuilder.cellId.cellIndex, resultShapeBuilder.commit());
        }
        curSnapshot = curSnapshot.with(oldTool, oldEnvironment, curCellBackups.toArray(CellBackup.NULL_ARRAY), null);

        return curSnapshot;
    }

    private static class MaxNodeSuffix {

        final Name basename;
        final int insertionPoint;
        int maxSuffix;
        List<ImmutableNodeInst> addedNodes = new ArrayList<ImmutableNodeInst>();

        private MaxNodeSuffix(SeaOfGatesCellBuilder b, Name basename) {
            this.basename = basename;
            insertionPoint = b.searchNodeInsertionPoint(basename.toString());
            maxSuffix = -1;
            if (insertionPoint > 0) {
                Name name = b.oldCellRevision.nodes.get(insertionPoint - 1).name;
                if (name.isTempname() && name.getBasename() == basename) {
                    maxSuffix = name.getNumSuffix();
                }

            }
        }

        private Name getNextName() {
            return basename.findSuffixed(maxSuffix + 1);
        }

        private void add(ImmutableNodeInst n) {
            maxSuffix++;
            assert n.name.getBasename() == basename;
            assert n.name.getNumSuffix() == maxSuffix;
            addedNodes.add(n);
        }
    }

    private int searchNodeInsertionPoint(String basename) {
        assert basename.endsWith("@0");
        char nextChar = (char) (basename.charAt(basename.length() - 2) + 1);
        String nextName = basename.substring(0, basename.length() - 2) + nextChar;
        int index = oldCellRevision.nodes.searchByName(nextName);
        return index >= 0 ? index : -(index + 1);
    }

//    private int searchArcInsertionPoint(String basename) {
//        assert basename.endsWith("@0");
//        char nextChar = (char) (basename.charAt(basename.length() - 2) + 1);
//        String nextName = basename.substring(0, basename.length() - 2) + nextChar;
//        int index = oldCellRevision.arcs.searchByName(nextName);
//        return index >= 0 ? index : -(index + 1);
//    }
}
