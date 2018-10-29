/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: ELIB.java
 * Input/output tool: ELIB Library output
 * Written by Steven M. Rubin.
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
package com.sun.electric.tool.io.output;

import com.sun.electric.database.CellBackup;
import com.sun.electric.database.CellRevision;
import com.sun.electric.database.ImmutableArcInst;
import com.sun.electric.database.ImmutableCell;
import com.sun.electric.database.ImmutableElectricObject;
import com.sun.electric.database.ImmutableExport;
import com.sun.electric.database.ImmutableIconInst;
import com.sun.electric.database.ImmutableLibrary;
import com.sun.electric.database.ImmutableNodeInst;
import com.sun.electric.database.ImmutablePortInst;
import com.sun.electric.database.LibraryBackup;
import com.sun.electric.database.Snapshot;
import com.sun.electric.database.geometry.EPoint;
import com.sun.electric.database.geometry.ERectangle;
import com.sun.electric.database.hierarchy.Library;
import com.sun.electric.database.hierarchy.View;
import com.sun.electric.database.id.ArcProtoId;
import com.sun.electric.database.id.CellId;
import com.sun.electric.database.id.ExportId;
import com.sun.electric.database.id.IdManager;
import com.sun.electric.database.id.LibId;
import com.sun.electric.database.id.NodeProtoId;
import com.sun.electric.database.id.PortProtoId;
import com.sun.electric.database.id.PrimitiveNodeId;
import com.sun.electric.database.id.TechId;
import com.sun.electric.database.prototype.PortProto;
import com.sun.electric.database.text.CellName;
import com.sun.electric.database.text.Setting;
import com.sun.electric.database.text.Version;
import com.sun.electric.database.topology.ArcInst;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.variable.CodeExpression;
import com.sun.electric.database.variable.TextDescriptor;
import com.sun.electric.database.variable.Variable;
import com.sun.electric.technology.ArcProto;
import com.sun.electric.technology.PrimitiveNode;
import com.sun.electric.technology.PrimitivePort;
import com.sun.electric.technology.TechPool;
import com.sun.electric.technology.Technology;
import com.sun.electric.technology.technologies.Generic;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.Tool;
import com.sun.electric.tool.io.ELIBConstants;
import com.sun.electric.util.TextUtils;
import com.sun.electric.util.math.DBMath;
import com.sun.electric.util.math.Orientation;

import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * This class writes files in binary (.elib) format.
 */
public class ELIB extends Output {

    /** Snapshot being saved. */
    Snapshot snapshot;
    /** IdManager of Snapshot being saved. */
    IdManager idManager;
    /** TechPool of Snapshot being saved. */
    TechPool techPool;
    /** Library being saved. */
    LibId theLibId;
    /** Map of referenced objects for library files */
    HashMap<Object, Integer> objInfo;
    /** List of referenced Technologies */
    final ArrayList<Technology> technologies = new ArrayList<Technology>();
    /** Maps memory face index to disk face index */
    private int[] faceMap = new int[TextDescriptor.ActiveFont.getMaxIndex() + 1];
    /** Name space of variable names */
    private TreeMap<String, Short> nameSpace;
    /** Scale for irrelevant technologies. */
    private double irrelevantScale;
    /** map with "next in cell group" pointers */
    private HashMap<CellId, CellId> cellInSameGroup = new HashMap<CellId, CellId>();
    /** true to write a 6.XX compatible library (MAGIC11) */
    private boolean compatibleWith6;
    /** map to assign indices to cell names (for 6.XX) */
    private TreeMap<String, Integer> cellIndexMap = new TreeMap<String, Integer>(TextUtils.STRING_NUMBER_ORDER);
    /** Project preferences. */
    private HashMap<Setting, Object> projectSettings = new HashMap<Setting, Object>();
    /** size correctors for technologies */
    private HashMap<TechId, Technology.SizeCorrector> sizeCorrectors = new HashMap<TechId, Technology.SizeCorrector>();
    /** Topological sort of cells in library to be written */
    private LinkedHashMap<CellId, Integer> cellOrdering = new LinkedHashMap<CellId, Integer>();
    /** Map from nodeId to nodeIndex for current Cell. */
    int[] nodeIndexByNodeId;
    ArrayList<CellRevision> localCells = new ArrayList<CellRevision>();
    ArrayList<CellRevision> externalCells = new ArrayList<CellRevision>();
    int nodeIndex = 0;
    int portProtoIndex = 0;
    int nodeProtoIndex = 0;
    int arcIndex = 0;
    int primNodeProtoIndex = 0;
    int primPortProtoIndex = 0;
    int arcProtoIndex = 0;
    int toolCount = 0;
    int[] primNodeCounts;
    int[] primArcCounts;
    int[] groupRenumber;

    ELIB() {
    }

    public void write6Compatible() {
        compatibleWith6 = true;
    }

    // ----------------------- public methods -------------------------------
    /**
     * Method to write a Library in binary (.elib) format.
     * @param snapshot Snapshot to be written
     * @param theLibId the Library to be written.
     */
    protected boolean writeLib(Snapshot snapshot, LibId theLibId) {
        this.snapshot = snapshot;
        idManager = snapshot.idManager;
        techPool = snapshot.techPool;
        this.theLibId = theLibId;

        // Gather objects referenced from Cells
        objInfo = new HashMap<Object, Integer>();
        nameSpace = new TreeMap<String, Short>(TextUtils.STRING_NUMBER_ORDER);
        for (CellBackup cellBackup : snapshot.cellBackups) {
            if (cellBackup == null) {
                continue;
            }
            CellRevision cellRevision = cellBackup.cellRevision;
            CellId cellId = cellRevision.d.cellId;
            if (cellId.libId != theLibId) {
                continue;
            }
            gatherCell(cellRevision.d.cellId);

            for (ImmutableNodeInst n : cellRevision.nodes) {
                NodeProtoId np = n.protoId;
                if (np instanceof CellId) {
                    gatherCell((CellId) np);
                } else {
                    gatherObj(np);
                    gatherTech(((PrimitiveNodeId) np).techId);
                }
                if (n.hasPortInstVariables()) {
                    for (Iterator<PortProtoId> it = n.getPortsWithVariables(); it.hasNext();) {
                        PortProtoId portId = it.next();
                        gatherVariables(portId.getName(snapshot), n.getPortInst(portId));
                    }
                }
                gatherVariables(null, n);
                gatherFont(n.nameDescriptor);
                gatherFont(n.protoDescriptor);
            }

            for (ImmutableArcInst a : cellRevision.arcs) {
                ArcProtoId apId = a.protoId;
                gatherObj(apId);
                gatherTech(apId.techId);
                //gatherObj(ai.getHead().getPortInst().getPortProto());
                //gatherObj(ai.getTail().getPortInst().getPortProto());
                gatherVariables(null, a);
                gatherFont(a.nameDescriptor);
            }

            for (ImmutableExport e : cellRevision.exports) {
                //gatherObj(e.getOriginalPort().getPortProto());
                gatherVariables(null, e);
                gatherFont(e.nameDescriptor);
            }

            gatherVariables(null, cellRevision.d);
        }
        gatherVariables(null, snapshot.getLib(theLibId).d);

        // Gather objects refetenced from preferences
        for (Iterator<Tool> it = Tool.getTools(); it.hasNext();) {
            gatherSettings(it.next());
        }

        for (Iterator<Technology> it = Technology.getTechnologies(); it.hasNext();) {
            Technology tech = it.next();
            TechId techId = tech.getId();
            if (!objInfo.containsKey(techId)) {
                continue;
            }
            technologies.add(tech);
            gatherSettings(tech);
        }

        putNameSpace(Library.FONT_ASSOCIATIONS.getName());
        putNameSpace(NodeInst.NODE_NAME.getName());
        putNameSpace(ArcInst.ARC_NAME.getName());
        short varIndex = 0;
        for (Map.Entry<String, Short> e : nameSpace.entrySet()) {
            e.setValue(new Short(varIndex++));
        }

        // make sure all technologies with irrelevant scale information have the same scale value
        for (Technology tech : technologies) {
            if (tech.isScaleRelevant()) {
                continue;
            }
            if (tech == Generic.tech()) {
                continue;
            }
            irrelevantScale = Math.max(irrelevantScale, tech.getScale());
        }

        // Sort CellIds
        TreeMap<String, LibId> sortedLibIds = new TreeMap<String, LibId>(TextUtils.STRING_NUMBER_ORDER);
        HashMap<LibId, TreeMap<CellName, CellId>> sortedCellIds = new HashMap<LibId, TreeMap<CellName, CellId>>();
        for (CellBackup cellBackup : snapshot.cellBackups) {
            if (cellBackup == null) {
                continue;
            }
            CellId cellId = cellBackup.cellRevision.d.cellId;
            if (!objInfo.containsKey(cellId)) {
                continue;
            }
            LibId libId = cellId.libId;
            sortedLibIds.put(libId.libName, libId);
            TreeMap<CellName, CellId> sortedCellIdsInLibrary = sortedCellIds.get(libId);
            if (sortedCellIdsInLibrary == null) {
                sortedCellIdsInLibrary = new TreeMap<CellName, CellId>();
                sortedCellIds.put(libId, sortedCellIdsInLibrary);
            }
            sortedCellIdsInLibrary.put(cellId.cellName, cellId);
        }
        for (CellId cellId : sortedCellIds.get(theLibId).values()) {
            localCells.add(snapshot.getCellRevision(cellId));
        }

        // count and number the cells, nodes, arcs, and ports in this library
        int maxGroup = -1;
        ArrayList<TreeMap<CellName, CellId>> cellGroups_ = new ArrayList<TreeMap<CellName, CellId>>();
        for (CellRevision cellRevision : localCells) {
            maxGroup = Math.max(maxGroup, snapshot.getCellGroupIndex(cellRevision.d.cellId));
        }
        groupRenumber = new int[maxGroup + 1];

        for (CellRevision cellRevision : localCells) {
            CellId cellId = cellRevision.d.cellId;
            cellOrdering.put(cellId, new Integer(nodeProtoIndex));
            putObjIndex(cellId, nodeProtoIndex++);
            for (ImmutableExport e : cellRevision.exports) {
                putObjIndex(e.exportId, portProtoIndex++);
            }
            nodeIndex += cellRevision.nodes.size();
            arcIndex += cellRevision.arcs.size();

            int snapshotGroup = snapshot.getCellGroupIndex(cellId);
            int elibGroup = groupRenumber[snapshotGroup];
            if (elibGroup == 0) {
                cellGroups_.add(new TreeMap<CellName, CellId>());
                elibGroup = cellGroups_.size();
                groupRenumber[snapshotGroup] = elibGroup;
            }
            cellGroups_.get(elibGroup - 1).put(cellId.cellName, cellId);

            // gather proto name if creating version-6-compatible output
            if (compatibleWith6) {
                String protoName = cellId.cellName.getName();
                if (!cellIndexMap.containsKey(protoName)) {
                    cellIndexMap.put(protoName, null);
                }
            }
        }
        for (int i = 0; i < cellGroups_.size(); i++) {
            TreeMap<CellName, CellId> cellGroup_ = cellGroups_.get(i);
            Iterator<CellId> git = cellGroup_.values().iterator();
            CellId firstCellInGroup = git.next();
            CellId lastCellInGroup = firstCellInGroup;
            while (git.hasNext()) {
                CellId cellInGroup = git.next();
//                assert cellInSameGroup.get(lastCellInGroup) == cellInGroup;
                cellInSameGroup.put(lastCellInGroup, cellInGroup);
                lastCellInGroup = cellInGroup;
            }
//            assert cellInSameGroup.get(lastCellInGroup) == firstCellInGroup;
            cellInSameGroup.put(lastCellInGroup, firstCellInGroup);
        }

        // count and number the cells in other libraries
        for (LibId libId : sortedLibIds.values()) {
            if (libId == theLibId) {
                continue;
            }
            for (CellId cellId : sortedCellIds.get(libId).values()) {
                assert objInfo.containsKey(cellId);
                CellRevision cellRevision = snapshot.getCellRevision(cellId);
                externalCells.add(cellRevision);
                putObjIndex(cellId, nodeProtoIndex++);
                for (ImmutableExport e : cellRevision.exports) {
                    putObjIndex(e.exportId, portProtoIndex++);
                }

                // gather proto name if creating version-6-compatible output
                if (compatibleWith6) {
                    String protoName = cellId.cellName.getName();
                    if (!cellIndexMap.containsKey(protoName)) {
                        cellIndexMap.put(protoName, null);
                    }
                }
            }
        }

        // count the number of primitives
        primNodeCounts = new int[technologies.size()];
        primArcCounts = new int[technologies.size()];
        for (int techCount = 0; techCount < technologies.size(); techCount++) {
            Technology tech = technologies.get(techCount);
            TechId techId = tech.getId();
            putObjIndex(techId, techCount);
            int primNodeStart = primNodeProtoIndex;
            for (Iterator<PrimitiveNode> nit = tech.getNodes(); nit.hasNext();) {
                PrimitiveNode np = nit.next();
                if (!objInfo.containsKey(np.getId())) {
                    continue;
                }
                putObjIndex(np.getId(), -2 - primNodeProtoIndex++);
                for (Iterator<PortProto> pit = np.getPorts(); pit.hasNext();) {
                    PrimitivePort pp = (PrimitivePort) pit.next();
                    putObjIndex(pp.getId(), -2 - primPortProtoIndex++);
                }
            }
            primNodeCounts[techCount] = primNodeProtoIndex - primNodeStart;
            int primArcStart = arcProtoIndex;
            for (Iterator<ArcProto> ait = tech.getArcs(); ait.hasNext();) {
                ArcProtoId apId = ait.next().getId();
                if (!objInfo.containsKey(apId)) {
                    continue;
                }
                putObjIndex(apId, -2 - arcProtoIndex++);
            }
            primArcCounts[techCount] = arcProtoIndex - primArcStart;
        }

        // count the number of tools
        for (Iterator<Tool> it = Tool.getTools(); it.hasNext();) {
            Tool tool = it.next();
            if (!objInfo.containsKey(tool)) {
                continue;
            }
            toolCount++;
        }

        try {
            return writeTheLibrary();
        } catch (IOException e) {
            System.out.println("End of file reached while writing " + filePath);
            return true;
        }
    }

    /**
     * Method to write the .elib file.
     * Returns true on error.
     */
    boolean writeTheLibrary() throws IOException {
        // write the header
        int magic = ELIBConstants.MAGIC13;
        if (compatibleWith6) {
            magic = ELIBConstants.MAGIC11;
        }
        writeBigInteger(magic);
        writeByte((byte) 2);		// size of Short
        writeByte((byte) 4);		// size of Int
        writeByte((byte) 1);		// size of Char

        // write number of objects
        writeBigInteger(toolCount);
        writeBigInteger(technologies.size());
        writeBigInteger(primNodeProtoIndex);
        writeBigInteger(primPortProtoIndex);
        writeBigInteger(arcProtoIndex);
        writeBigInteger(nodeProtoIndex);
        writeBigInteger(nodeIndex);
        writeBigInteger(portProtoIndex);
        writeBigInteger(arcIndex);
        writeBigInteger(0);

        // write count of cells if creating version-6-compatible output
        int cellCount = 0;
        if (compatibleWith6) {
            for (Map.Entry<String, Integer> e : cellIndexMap.entrySet()) {
                e.setValue(new Integer(cellCount++));
            }
            writeBigInteger(cellCount);
        }

        // write the current cell
        writeObj(null);

        // write the version number
        writeString(Version.getVersion().toOldStyleString());

        // number the views and write nonstandard ones
        putObjIndex(View.UNKNOWN, -1);
        putObjIndex(View.LAYOUT, -2);
        putObjIndex(View.SCHEMATIC, -3);
        putObjIndex(View.ICON, -4);
        putObjIndex(View.DOCWAVE, -5);				// unknown in C
        putObjIndex(View.LAYOUTSKEL, -6);			// unknown in C
        putObjIndex(View.VHDL, -7);
        putObjIndex(View.NETLIST, -8);
        putObjIndex(View.DOC, -9);
        putObjIndex(View.NETLISTNETLISP, -10);		// unknown in C
        putObjIndex(View.NETLISTALS, -11);			// unknown in C
        putObjIndex(View.NETLISTQUISC, -12);		// unknown in C
        putObjIndex(View.NETLISTRSIM, -13);			// unknown in C
        putObjIndex(View.NETLISTSILOS, -14);		// unknown in C
        putObjIndex(View.VERILOG, -15);
        List<View> viewsToSave = new ArrayList<View>();
        for (Iterator<View> it = View.getViews(); it.hasNext();) {
            View view = it.next();
            if (objInfo.get(view) != null) {
                continue;
            }
            if (!objInfo.containsKey(view)) {
                continue;
            }
            viewsToSave.add(view);
            putObjIndex(view, viewsToSave.size());
        }
        writeBigInteger(viewsToSave.size());
        for (View view : viewsToSave) {
            writeString(view.getFullName());
            writeString(view.getAbbreviation());
        }

        // write total number of arcinsts, nodeinsts, and ports in each cell
        for (CellId cellId : cellOrdering.keySet()) {
            CellRevision cellRevision = snapshot.getCellRevision(cellId);
            writeBigInteger(cellRevision.arcs.size());
            writeBigInteger(cellRevision.nodes.size());
            writeBigInteger(cellRevision.exports.size());
        }

        // write dummy numbers of arcinsts and nodeinst; count ports for external cells
        for (CellRevision cellRevision : externalCells) {
            CellId cellId = cellRevision.d.cellId;
            if (!objInfo.containsKey(cellId)) {
                continue;
            }
            writeBigInteger(-1);
            writeBigInteger(-1);
            writeBigInteger(cellRevision.exports.size());
        }

        // write the names of technologies and primitive prototypes
        for (int techCount = 0; techCount < technologies.size(); techCount++) {
            Technology tech = technologies.get(techCount);

            // write the technology name
            writeString(tech.getTechName());

            // write the primitive node prototypes
            writeBigInteger(primNodeCounts[techCount]);
            for (Iterator<PrimitiveNode> nit = tech.getNodes(); nit.hasNext();) {
                PrimitiveNode np = nit.next();
                if (!objInfo.containsKey(np.getId())) {
                    continue;
                }

                // write the primitive node prototype name
                writeString(np.getName());
                writeBigInteger(np.getNumPorts());
                for (Iterator<PortProto> pit = np.getPorts(); pit.hasNext();) {
                    PrimitivePort pp = (PrimitivePort) pit.next();
                    writeString(pp.getName());
                }
            }

            // write the primitive arc prototype names
            writeBigInteger(primArcCounts[techCount]);
            for (Iterator<ArcProto> ait = tech.getArcs(); ait.hasNext();) {
                ArcProtoId apId = ait.next().getId();
                if (!objInfo.containsKey(apId)) {
                    continue;
                }
                writeString(apId.name);
            }
        }

        // write the names of the tools
        for (Iterator<Tool> it = Tool.getTools(); it.hasNext();) {
            Tool tool = it.next();
            if (!objInfo.containsKey(tool)) {
                continue;
            }
            writeString(tool.getName());
        }

        // write the userbits for the library
        writeBigInteger(0);
        //writeBigInteger(lib.lowLevelGetUserBits());

        // write the tool scale values
        for (Technology tech : technologies) {
            writeBigInteger(gridCoordToElib(tech, DBMath.GRID));
//			writeBigInteger((int)Math.round(tech.getScale()*2));
        }

        // write the global namespace
        writeNameSpace();

        // write the library variables and font association that preserves the font names
        writeVariables(snapshot.getLib(theLibId).d);

        // write the tool variables
        for (Iterator<Tool> it = Tool.getTools(); it.hasNext();) {
            Tool tool = it.next();
            if (!objInfo.containsKey(tool)) {
                continue;
            }
            writeMeaningPrefs(tool);
        }

        // write the variables on technologies
        for (Technology tech : technologies) {
            writeMeaningPrefs(tech);
        }

        // write the dummy primitive variables
        int numDummyVariables = arcProtoIndex + primNodeProtoIndex + primPortProtoIndex;
        for (int i = 0; i < numDummyVariables; i++) {
            writeNoVariables();
        }

        // write the dummy view variables
        writeBigInteger(0);

        // write cells if creating version-6-compatible output
        if (compatibleWith6) {
            for (String cellName : cellIndexMap.keySet()) {
                writeString(cellName);
                writeNoVariables();
            }
        }

        // write all of the cells in this library
        nodeIndex = 0;
        for (CellId cellId : cellOrdering.keySet()) {
            CellRevision cellRevision = snapshot.getCellRevision(cellId);
            startCell(cellRevision, nodeIndex);
            writeNodeProto(cellRevision);
            nodeIndex += cellRevision.nodes.size();
        }

        // write all of the cells in external libraries
        writeExternalCells();

        // write all of the arcs and nodes in this library
        nodeIndex = 0;
        arcIndex = 0;
        for (CellId cellId : cellOrdering.keySet()) {
            CellRevision cellRevision = snapshot.getCellRevision(cellId);
            startCell(cellRevision, nodeIndex);
            writeArcs(cellRevision);
            writeNodes(snapshot.getCell(cellId), arcIndex);
            nodeIndex += cellRevision.nodes.size();
            arcIndex += cellRevision.arcs.size();
        }
        // library written successfully
        return false;
    }

    /**
     * Gather variables of ElectricObject into objInfo map.
     * @param eObj ElectricObject with variables.
     */
    private void gatherVariables(String portName, ImmutableElectricObject d) {
        if (d instanceof ImmutableCell) {
            for (Iterator<Variable> it = ((ImmutableCell) d).getParameters(); it.hasNext();) {
                Variable param = it.next();
                gatherVariable(portName, param);
            }
        } else if (d instanceof ImmutableIconInst) {
            for (Iterator<Variable> it = ((ImmutableIconInst) d).getDefinedParameters(); it.hasNext();) {
                Variable param = it.next();
                gatherVariable(portName, param);
            }
        }
        for (Iterator<Variable> it = d.getVariables(); it.hasNext();) {
            Variable var = it.next();
            gatherVariable(portName, var);
        }
    }

    /**
     * Gather variable into objInfo map.
     * @param var variable.
     */
    private void gatherVariable(String portName, Variable var) {
        Object value = var.getObject();
        if (nameSpace != null) {
            putNameSpace(diskName(portName, var));
        }
        gatherFont(var.getTextDescriptor());
        int length = value instanceof Object[] ? ((Object[]) value).length : 1;
        for (int i = 0; i < length; i++) {
            Object v = value instanceof Object[] ? ((Object[]) value)[i] : value;
            if (v == null) {
                continue;
            }
            if (v instanceof TechId || v instanceof Tool) {
                gatherObj(v);
            } else if (v instanceof PrimitiveNodeId) {
                gatherObj(v);
                gatherTech(((PrimitiveNodeId) v).techId);
            } else if (v instanceof PrimitivePort) {
                PrimitiveNode pn = ((PrimitivePort) v).getParent();
                gatherObj(pn.getId());
                gatherTech(pn.getTechnology().getId());
            } else if (v instanceof ArcProto) {
                gatherObj(v);
                gatherTech(((ArcProto) v).getId().techId);
            } else if (v instanceof CellId) {
                CellId cellId = (CellId) v;
                if (snapshot.getCell(cellId) != null) {
                    gatherCell(cellId);
                }
            } else if (v instanceof ExportId) {
                ExportId exportId = (ExportId) v;
                CellRevision cellRevision = snapshot.getCellRevision(exportId.getParentId());
                if (cellRevision != null && cellRevision.getExport(exportId) != null) {
                    gatherObj(exportId);
                    gatherCell(exportId.getParentId());
                }
            }
        }
    }

    /**
     * Gather project preferences attached to object into objInfo map.
     * @param obj Object with attached project preferences.
     */
    private void gatherSettings(Object obj) {
        Setting.Group group = null;
        if (obj instanceof Tool) {
            group = ((Tool) obj).getProjectSettings();
        } else if (obj instanceof Technology) {
            group = ((Technology) obj).getProjectSettings();
        }
        for (Setting setting : group.getDiskSettings(snapshot.getSettings()).keySet()) {
            gatherObj(obj);
            String name = setting.getPrefName();
            if (nameSpace != null) {
                putNameSpace(name);
            }
        }
    }

    /**
     * Gather TechId of Technology into objInfo map.
     * @param tech Technology to examine.
     */
    private void gatherTech(TechId techId) {
        gatherObj(techId);
    }

    /**
     * Gather Cell, its Library and its font into objInfo map.
     * @param cell Cell to examine.
     */
    private void gatherCell(CellId cellId) {
        gatherObj(cellId);
        gatherObj(cellId.libId);
        gatherObj(cellId.cellName.getView());
    }

    /**
     * Gather object into objInfo map.
     * @param obj Object to put.
     */
    private void gatherObj(Object obj) {
        objInfo.put(obj, null);
    }

    /**
     * Put index of object into objInfo map.
     * @param obj Object to put.
     * @param index index of object.
     */
    private void putObjIndex(Object obj, int index) {
        objInfo.put(obj, new Integer(index));
    }

    /**
     * Put string into variable name space.
     * @param name name to put.
     */
    private void putNameSpace(String name) {
        nameSpace.put(name, null);
    }

    /**
     * Gather ActiveFont object of the TextDescriptor into objInfo map.
     * @param td TextDescriptor to examine.
     */
    private void gatherFont(TextDescriptor td) {
        int face = td.getFace();
        faceMap[face] = -1;
    }

    /**
     * Method to gather all font settings in a Library.
     * The method examines all TextDescriptors that might be saved with the Library
     * and returns an array of Strings that describes the font associations.
     * Each String is of the format NUMBER/FONTNAME where NUMBER is the font number
     * in the TextDescriptor and FONTNAME is the font name.
     * @return font association array or null.
     */
    String[] createFontAssociation() {
        TreeMap<String, TextDescriptor.ActiveFont> sortedFonts = new TreeMap<String, TextDescriptor.ActiveFont>();
        for (int face = 1; face < faceMap.length; face++) {
            if (faceMap[face] == 0) {
                continue;
            }
            TextDescriptor.ActiveFont af = TextDescriptor.ActiveFont.findActiveFont(face);
            sortedFonts.put(af.getName(), af);
        }
        if (sortedFonts.size() == 0) {
            return null;
        }
        String[] fontAssociation = new String[sortedFonts.size()];
        int elibFace = 0;
        for (TextDescriptor.ActiveFont af : sortedFonts.values()) {
            elibFace++;
            faceMap[af.getIndex()] = elibFace;
            fontAssociation[elibFace - 1] = elibFace + "/" + af.getName();
        }
        return fontAssociation;
    }

    void startCell(CellRevision cellRevision, int baseNodeIndex) {
        int maxNodeId = -1;
        for (ImmutableNodeInst n : cellRevision.nodes) {
            maxNodeId = Math.max(maxNodeId, n.nodeId);
        }
        nodeIndexByNodeId = new int[maxNodeId + 1];
        for (int nodeIndex = 0; nodeIndex < cellRevision.nodes.size(); nodeIndex++) {
            ImmutableNodeInst n = cellRevision.nodes.get(nodeIndex);
            nodeIndexByNodeId[n.nodeId] = nodeIndex + baseNodeIndex;
        }
    }

    // --------------------------------- OBJECT CONVERSION ---------------------------------
    void writeExternalCells() throws IOException {
        for (int i = 0; i < externalCells.size(); i++) {
            CellRevision cellRevision = externalCells.get(i);
            CellId cellId = cellRevision.d.cellId;
            writeTxt("***cell: " + i);  // TXT only
            writeCellInfo(cellRevision);

            LibraryBackup libBackup = snapshot.getLib(cellId.libId);
            URL fileUrl = libBackup.d.libFile;
            String filePath = fileUrl != null ? fileUrl.getPath() : libBackup.d.libId.libName;
            writeTxt("externallibrary: \"" + filePath + "\""); // TXT only
            if (this instanceof ReadableDump) {
                continue;
            }
            writeString(filePath);

            // write the number of portprotos on this nodeproto
            writeBigInteger(cellRevision.exports.size());
            for (ImmutableExport e : cellRevision.exports) {
                // write the portproto name
                writeString(e.name.toString());
            }
        }
    }

    void writeCellInfo(CellRevision cellRevision) throws IOException {
        CellId cellId = cellRevision.d.cellId;

        if (this instanceof ReadableDump) {
            writeTxt("name: " + cellId.cellName.getName() + cellId.cellName.getView().getAbbreviationExtension());
        } else {
            if (compatibleWith6) {
                // write cell index if creating version-6-compatible output
                Integer cellIndex = cellIndexMap.get(cellId.cellName.getName());
                writeBigInteger(cellIndex.intValue());
            } else {
                // write cell information
                writeString(cellId.cellName.getName());

                // write the "next in cell group" pointer
                writeObj(cellInSameGroup.get(cellId));

                // write the "next in continuation" pointer
                writeObj(null);
            }

            // write the view information
            writeObj(cellId.cellName.getView());
        }
        writeInt("version: ", cellId.cellName.getVersion());

        writeInt("creationdate: ", (int) (cellRevision.d.creationDate / 1000));
        writeInt("revisiondate: ", (int) (cellRevision.d.revisionDate / 1000));

        // write the nodeproto bounding box
        ERectangle bounds = snapshot.getCellBounds(cellId);
        writeGridCoord(cellRevision, "lowx: ", bounds.getGridMinX());
        writeGridCoord(cellRevision, "highx: ", bounds.getGridMaxX());
        writeGridCoord(cellRevision, "lowy: ", bounds.getGridMinY());
        writeGridCoord(cellRevision, "highy: ", bounds.getGridMaxY());
//        Technology tech = cellBackup.d.tech;
//        int lowX = (int)Math.round((bounds.getLambdaMinX() * tech.getScale()*2));
//        int highX = (int)Math.round((bounds.getLambdaMaxX() * tech.getScale()*2));
//        int lowY = (int)Math.round((bounds.getLambdaMinY() * tech.getScale()*2));
//        int highY = (int)Math.round((bounds.getLambdaMaxY() * tech.getScale()*2));
//        writeInt("lowx: ", lowX);
//        writeInt("highx: ", highX);
//        writeInt("lowy: ", lowY);
//        writeInt("highy: ", highY);
    }

    private void writeNodeProto(CellRevision cellRevision) throws IOException {
        writeCellInfo(cellRevision);

        writeExports(cellRevision);
        // write tool information
        writeBigInteger(0);		// was "adirty"
        writeBigInteger(cellRevision.d.flags & ELIBConstants.CELL_BITS);

        // write variable information
        writeVariables(cellRevision.d);
    }

    void writeNodes(CellBackup cellBackup, int arcBase) throws IOException {
//		Technology tech = cellBackup.d.tech;
        CellRevision cellRevision = cellBackup.cellRevision;

        for (int nodeIndex = 0; nodeIndex < cellRevision.nodes.size(); nodeIndex++) {
            ImmutableNodeInst n = cellRevision.nodes.get(nodeIndex);
            writeTxt("**node: " + nodeIndex);

            // write descriptive information
//            int lowX, highX, lowY, highY;
            double trueCenterX, trueCenterY, xSize, ySize;
            NodeProtoId protoId = n.protoId;
            writeObj(protoId);
            if (protoId instanceof CellId) {
                writeTxt("type: [" + objInfo.get(protoId).intValue() + "]");
                ERectangle bounds = snapshot.getCellBounds((CellId) protoId);
                Rectangle2D dstBounds = new Rectangle2D.Double();
                n.orient.rectangleBounds(bounds.getGridMinX(), bounds.getGridMinY(), bounds.getGridMaxX(), bounds.getGridMaxY(), n.anchor.getGridX(), n.anchor.getGridY(), dstBounds);
                trueCenterX = dstBounds.getCenterX();
                trueCenterY = dstBounds.getCenterY();
                xSize = bounds.getGridWidth();
                ySize = bounds.getGridHeight();

//                lowX = (int)Math.round((trueCenterX - xSize/2) * tech.getScale()*2);
//                highX = (int)Math.round((trueCenterX + xSize/2) * tech.getScale()*2);
//                lowY = (int)Math.round((trueCenterY - ySize/2) * tech.getScale()*2);
//                highY = (int)Math.round((trueCenterY + ySize/2) * tech.getScale()*2);
            } else {
                PrimitiveNodeId pn = (PrimitiveNodeId) protoId;
                trueCenterX = n.anchor.getGridX();
                trueCenterY = n.anchor.getGridY();
                EPoint size = getSizeCorrector(pn.techId).getSizeToDisk(n);
                xSize = size.getGridX();
                ySize = size.getGridY();
                writeTxt("type: " + pn.fullName);
//                lowX = (int)Math.round((n.anchor.getLambdaX() - n.size.getLambdaX()/2) * tech.getScale()*2);
//                highX = (int)Math.round((n.anchor.getLambdaX() + n.size.getLambdaX()/2) * tech.getScale()*2);
//                lowY = (int)Math.round((n.anchor.getLambdaY() - n.size.getLambdaY()/2) * tech.getScale()*2);
//                highY = (int)Math.round((n.anchor.getLambdaY() + n.size.getLambdaY()/2) * tech.getScale()*2);
            }
            writeGridCoord(cellRevision, "lowx: ", trueCenterX - xSize * 0.5);
            writeGridCoord(cellRevision, "lowy: ", trueCenterY - ySize * 0.5);
            writeGridCoord(cellRevision, "highx: ", trueCenterX + xSize * 0.5);
            writeGridCoord(cellRevision, "highy: ", trueCenterY + ySize * 0.5);
//            writeInt("lowx: ", lowX);
//            writeInt("lowy: ", lowY);
//            writeInt("highx: ", highX);
//            writeInt("highy: ", highY);

            // write anchor point too
            if (protoId instanceof CellId && !compatibleWith6) {
                writeGridCoord(cellRevision, null, n.anchor.getGridX());
                writeGridCoord(cellRevision, null, n.anchor.getGridY());
//                int anchorX = (int)Math.round(n.anchor.getLambdaX() * tech.getScale() * 2);
//                int anchorY = (int)Math.round(n.anchor.getLambdaY() * tech.getScale() * 2);
//                writeBigInteger(anchorX);
//                writeBigInteger(anchorY);
            }

            Orientation or = n.orient;
            int transpose = 0;
            int rotation;
            if (compatibleWith6) {
                rotation = or.getCAngle();
                transpose = or.isCTranspose() ? 1 : 0;
            } else {
                rotation = or.getAngle();
                if (or.isXMirrored()) {
                    transpose |= 2;
                }
                if (or.isYMirrored()) {
                    transpose |= 4;
                }
            }
            writeOrientation(rotation, transpose);

            TextDescriptor td = protoId instanceof CellId ? n.protoDescriptor : null;
            writeTextDescriptor(-1, td);

            if (this instanceof ReadableDump) {
                // write the tool information
                writeInt("userbits: ", n.getElibBits());

                // write variable information and arc name
                writeVariables(n);

                writeConnectionsAndReExports(cellBackup, arcBase, n);
            } else {
                writeConnectionsAndReExports(cellBackup, arcBase, n);

                // write the tool information
                writeBigInteger(n.getElibBits());

                // write variable information and arc name
                writeVariables(n);
            }
        }
    }

    private void writeConnectionsAndReExports(CellBackup cellBackup, int arcBase, ImmutableNodeInst n) throws IOException {
        CellRevision cellRevision = cellBackup.cellRevision;
        TreeSet<ElibConnection> sortedConnections = new TreeSet<ElibConnection>();
        BitSet headEnds = new BitSet();
        List<ImmutableArcInst> arcs = cellRevision.getConnectionsOnNode(headEnds, n);
        for (int i = 0; i < arcs.size(); i++) {
            ImmutableArcInst a = arcs.get(i);
            boolean isHead = headEnds.get(i);
            PortProtoId portId = isHead ? a.headPortId : a.tailPortId;
            sortedConnections.add(new ElibConnection(portId, a.arcId, isHead, cellRevision.getArcIndexByArcId(a.arcId)));
        }
        assert sortedConnections.size() == arcs.size();

        writeBigInteger(sortedConnections.size());
        for (ElibConnection c : sortedConnections) {
            writeConnection(c.portId, arcBase + c.arcIndex, c.isHead ? 1 : 0);
        }

        // write the exports
        int numExports = cellRevision.getNumExportsOnNode(n);
        writeBigInteger(numExports); // only ELIB
        if (numExports > 0) {
            // must write exports in proper order
            TreeMap<String, ImmutableExport> sortedExports = new TreeMap<String, ImmutableExport>();
            for (Iterator<ImmutableExport> it = cellRevision.getExportsOnNode(n); it.hasNext();) {
                ImmutableExport e = it.next();
                sortedExports.put(e.exportId.externalId, e);
            }
            for (ImmutableExport e : sortedExports.values()) {
                writeReExport(e);
            }
        }
    }

    private static class ElibConnection implements Comparable<ElibConnection> {

        private final PortProtoId portId;
        private final int arcId;
        private final boolean isHead;
        private final int arcIndex;

        private ElibConnection(PortProtoId portId, int arcId, boolean isHead, int arcIndex) {
            this.portId = portId;
            this.arcId = arcId;
            this.isHead = isHead;
            this.arcIndex = arcIndex;
        }

        public int compareTo(ElibConnection that) {
            int cmp = TextUtils.STRING_NUMBER_ORDER.compare(this.portId.externalId, that.portId.externalId);
            if (cmp != 0) {
                return cmp;
            }
            cmp = this.arcId - that.arcId;
            if (cmp != 0) {
                return cmp;
            }
            if (this.isHead && !that.isHead) {
                return 1;
            }
            if (!this.isHead && that.isHead) {
                return -1;
            }
            return 0;
        }
    }

    void writeArcs(CellRevision cellRevision) throws IOException {
//        double gridScale = cellRevision.d.tech.getScale()*2/DBMath.GRID;

        for (int arcIndex = 0; arcIndex < cellRevision.arcs.size(); arcIndex++) {
            ImmutableArcInst a = cellRevision.arcs.get(arcIndex);

            writeTxt("**arc: " + arcIndex); // TXT only
            // write the arcproto pointer
            writeObj(a.protoId); // ELIB only
            writeTxt("type: " + a.protoId.fullName); // TXT only

            // write basic arcinst information
            int userBits = a.getElibBits();
            long arcWidth = getSizeCorrector(a.protoId.techId).getWidthToDisk(a);
            writeGridCoord(cellRevision, "width: ", arcWidth);
            writeTxt("length: " + (int) Math.round(a.getGridLength() * getScale(cellRevision.d.techId) * 2 / DBMath.GRID));
//            writeInt("width: ", (int)Math.round(a.getGridFullWidth() * gridScale));
//            writeTxt("length: " + (int)Math.round(a.getGridLength() * gridScale));
            writeTxt("userbits: " + userBits); // only TXT

            // write the arcinst tail information
            writeTxt("*end: 0");
            writeGridCoord(cellRevision, "xpos: ", a.tailLocation.getGridX());
            writeGridCoord(cellRevision, "ypos: ", a.tailLocation.getGridY());
//            writeInt("xpos: ", (int)Math.round(a.tailLocation.getGridX() * gridScale));
//            writeInt("ypos: ", (int)Math.round(a.tailLocation.getGridY() * gridScale));
            writeInt("node: ", nodeIndexByNodeId[a.tailNodeId]);
            writeTxt("nodeport: " + a.tailPortId.getName(snapshot));

            // write the arcinst head information
            writeTxt("*end: 1");
            writeGridCoord(cellRevision, "xpos: ", a.headLocation.getGridX());
            writeGridCoord(cellRevision, "ypos: ", a.headLocation.getGridY());
//            writeInt("xpos: ", (int)Math.round(a.headLocation.getGridX() * gridScale));
//            writeInt("ypos: ", (int)Math.round(a.headLocation.getGridY() * gridScale));
            writeInt("node: ", nodeIndexByNodeId[a.headNodeId]);
            writeTxt("nodeport: " + a.headPortId.getName(snapshot));

            // write the arcinst's tool information
            writeBigInteger(userBits); // ELIB only

            // write variable information and arc name
            writeVariables(a);
        }
    }

    void writeExports(CellRevision cellRevision) throws IOException {
        writeBigInteger(cellRevision.exports.size());

        for (int exportIndex = 0; exportIndex < cellRevision.exports.size(); exportIndex++) {
            ImmutableExport e = cellRevision.exports.get(exportIndex);
            writeTxt("**porttype: " + exportIndex); // TXT only

            // write the connecting subnodeinst for this portproto
            writeInt("subnode: ", nodeIndexByNodeId[e.originalNodeId]);
            // write the portproto index in the subnodeinst
            writeObj(e.originalPortId); // ELIB only
            writeTxt("subport: " + e.originalPortId.getName(snapshot)); // TXT only

            // write the portproto name
            if (!(this instanceof ReadableDump)) {
                writeString(e.name.toString()); // ELIB only
            }
            writeTxt("name: " + e.name.toString()); // TXT only

            // write the text descriptor
            writeTextDescriptor(-1, e.nameDescriptor);

            // write the portproto tool information
            writeInt("userbits: ", e.getElibBits());

            // write variable information
            writeVariables(e);
        }
    }

    void writeOrientation(int angle, int transpose) throws IOException {
        writeBigInteger(transpose);
        writeBigInteger(angle);
    }

    void writeConnection(PortProtoId portId, int arcIndex, int connIndex) throws IOException {
        writeBigInteger((arcIndex << 1) | connIndex);

        // write the portinst prototype
        writeObj(portId);

        // write the variable information
        writeNoVariables();

    }

    void writeReExport(ImmutableExport e) throws IOException {
        writeObj(e.exportId);
        // write the portinst prototype
        writeObj(e.originalPortId);
        // write the variable information
        writeNoVariables();
    }

    // --------------------------------- VARIABLES ---------------------------------
    /**
     * Method to write the global namespace.  returns true upon error
     */
    private void writeNameSpace() throws IOException {
        if (nameSpace.size() > Short.MAX_VALUE) {
            Job.getUserInterface().showErrorMessage(
                    "ERROR! Too many unique variable names\n"
                    + "The ELIB format cannot handle this many unique variables names\n"
                    + "Either delete the excess variables, or save to a readable dump",
                    "Error saving ELIB file");
            throw new IOException("Variable.Key index too large");
        }
        writeBigInteger(nameSpace.size());
        for (String str : nameSpace.keySet()) {
            writeString(str);
        }
    }

    /**
     * Method to write an empty set of variables.
     */
    private void writeNoVariables() throws IOException {
        writeBigInteger(0);
    }

    /**
     * Method to write a set of object variables.  returns negative upon error and
     * otherwise returns the number of variables write
     */
    void writeVariables(ImmutableElectricObject d) throws IOException {
        // write the number of Variables
        int count = d.getNumVariables();
        if (d instanceof ImmutableCell) {
            count += ((ImmutableCell) d).getNumParameters();
        } else if (d instanceof ImmutableIconInst) {
            count += ((ImmutableIconInst) d).getNumDefinedParameters();
        }
        Variable.Key additionalVarKey = null;
        int additionalVarType = ELIBConstants.VSTRING;
        TextDescriptor additionalTextDescriptor = null;
        Object additionalVarValue = null;
        if (d instanceof ImmutableNodeInst) {
            ImmutableNodeInst n = (ImmutableNodeInst) d;
            for (Iterator<PortProtoId> pit = n.getPortsWithVariables(); pit.hasNext();) {
                PortProtoId portId = pit.next();
                count += n.getPortInst(portId).getNumVariables();
            }
            additionalVarKey = NodeInst.NODE_NAME;
            if (n.isUsernamed()) {
                additionalVarType |= ELIBConstants.VDISPLAY;
                additionalTextDescriptor = n.nameDescriptor;
            }
            additionalVarValue = n.name.toString();
        } else if (d instanceof ImmutableArcInst) {
            ImmutableArcInst a = (ImmutableArcInst) d;
            additionalVarKey = ArcInst.ARC_NAME;
            if (a.isUsernamed()) {
                additionalVarType |= ELIBConstants.VDISPLAY;
                additionalTextDescriptor = a.nameDescriptor;
            }
            additionalVarValue = a.name.toString();
        } else if (d instanceof ImmutableLibrary) {
            String[] fontAssociation = createFontAssociation();
            if (fontAssociation != null) {
                additionalVarKey = Library.FONT_ASSOCIATIONS;
                additionalVarType |= ELIBConstants.VISARRAY | (fontAssociation.length << ELIBConstants.VLENGTHSH);
                additionalVarValue = fontAssociation;
            }
        }
        if (additionalVarKey != null) {
            count++;
        }

        writeInt("variables: ", count);

        if (d instanceof ImmutableCell) {
            // write the parameters
            for (Iterator<Variable> it = ((ImmutableCell) d).getParameters(); it.hasNext();) {
                Variable var = it.next();
                writeVariable(null, var);
            }
        } else if (d instanceof ImmutableIconInst) {
            for (Iterator<Variable> it = ((ImmutableIconInst) d).getDefinedParameters(); it.hasNext();) {
                Variable var = it.next();
                writeVariable(null, var);
            }
        }

        // write the variables
        for (Iterator<Variable> it = d.getVariables(); it.hasNext();) {
            Variable var = it.next();
            writeVariable(null, var);
        }

        // write variables on PortInsts
        if (d instanceof ImmutableNodeInst) {
            ImmutableNodeInst n = (ImmutableNodeInst) d;
            if (n.hasPortInstVariables()) {
                TreeMap<String, ImmutablePortInst> portVars = new TreeMap<String, ImmutablePortInst>(TextUtils.STRING_NUMBER_ORDER);
                for (Iterator<PortProtoId> pit = n.getPortsWithVariables(); pit.hasNext();) {
                    PortProtoId portId = pit.next();
                    portVars.put(portId.getName(snapshot), n.getPortInst(portId));
                }
                for (Map.Entry<String, ImmutablePortInst> e : portVars.entrySet()) {
                    String portName = e.getKey();
                    ImmutablePortInst p = e.getValue();
                    for (Iterator<Variable> it = p.getVariables(); it.hasNext();) {
                        Variable var = it.next();
                        writeVariable(portName, var);
                    }
                }
            }
        }

        // write the additional variable
        if (additionalVarKey != null) {
            writeVariableName(additionalVarKey.getName());
            writeTextDescriptor(additionalVarType, additionalTextDescriptor);
            writeVarValue(additionalVarValue);
        }
    }

    /**
     * Method to write an object variables.
     * @param portName if this variable is on PortInst, then its name otherwise null.
     * @param var variable
     */
    void writeVariable(String portName, Variable var) throws IOException {
        // create the "type" field
        Object varObj = var.getObject();

        // special case for "trace" information on NodeInsts
        if (varObj instanceof EPoint[]) {
            EPoint[] points = (EPoint[]) varObj;
            int len = points.length * 2;
            Float[] newPoints = new Float[len];
            for (int j = 0; j < points.length; j++) {
                if (points[j] != null) {
                    newPoints[j * 2] = new Float(points[j].getLambdaX());
                    newPoints[j * 2 + 1] = new Float(points[j].getLambdaY());
                } else {
                    newPoints[j * 2] = newPoints[j * 2 + 1] = Float.valueOf(Float.NaN);
                }
            }
            varObj = newPoints;
        } else if (varObj instanceof EPoint) {
            EPoint p = (EPoint) varObj;
            varObj = new Float[]{new Float(p.getLambdaX()), new Float(p.getLambdaY())};
        }

        int type = ELIBConstants.getVarType(varObj);
        if (compatibleWith6 && type == ELIBConstants.VDOUBLE) {
            type = ELIBConstants.VFLOAT;
        }
        assert type != 0;
        if (var.isDisplay()) {
            type |= ELIBConstants.VDISPLAY;
        }
        if (varObj instanceof Object[]) {
            Object[] objList = (Object[]) varObj;
            // This doesn't seem to work properly for trace
//			if (objList.length > 0)
            type |= ELIBConstants.VISARRAY | (objList.length << ELIBConstants.VLENGTHSH);
        }
        // Only string variables may have language code bits.
        assert (type & ELIBConstants.VTYPE) == ELIBConstants.VSTRING || (type & (ELIBConstants.VCODE1 | ELIBConstants.VCODE2)) == 0;

        // write the text descriptor
        writeVariableName(diskName(portName, var));
        writeTextDescriptor(type, var.getTextDescriptor());
        writeVarValue(varObj);
    }

    /**
     * Method to write a set of project preferences.
     */
    void writeMeaningPrefs(Object obj) throws IOException {
        Setting.Group group = null;
        if (obj instanceof Tool) {
            group = ((Tool) obj).getProjectSettings();
        } else if (obj instanceof Technology) {
            group = ((Technology) obj).getProjectSettings();
        }
        Map<Setting, Object> settings = group.getDiskSettings(snapshot.getSettings());
        writeInt("variables: ", settings.size());
        for (Map.Entry<Setting, Object> e : settings.entrySet()) {
            Setting setting = e.getKey();
            Object varObj = e.getValue();
            projectSettings.put(setting, varObj);
            // create the "type" field
            if (varObj instanceof Boolean) {
                varObj = Integer.valueOf(((Boolean) varObj).booleanValue() ? 1 : 0);
            }
            int type = ELIBConstants.getVarType(varObj);
            if (compatibleWith6 && type == ELIBConstants.VDOUBLE) {
                type = ELIBConstants.VFLOAT;
            }
            writeVariableName(setting.getPrefName());
            writeTextDescriptor(type, null);
            writeVarValue(varObj);
        }
    }

    /**
     * Method to write the value of variable.
     * @param obj value of variable.
     */
    void writeVarValue(Object varObj) throws IOException {
        if (varObj instanceof Object[]) {
            Object[] objList = (Object[]) varObj;
            int len = objList.length;
            writeBigInteger(len);
            for (int i = 0; i < len; i++) {
                Object oneObj = objList[i];
                putOutVar(oneObj);
            }
        } else {
            putOutVar(varObj);
        }
    }

    /**
     * Helper method to write a variable at address "addr" of type "ty".
     * Returns zero if OK, negative on memory error, positive if there were
     * correctable problems in the write.
     */
    private void putOutVar(Object obj) throws IOException {
        if (obj instanceof String) {
            writeString((String) obj);
            return;
        }
        if (obj instanceof CodeExpression) {
            writeString(((CodeExpression) obj).getExpr());
            return;
        }
        if (obj instanceof Double) {
            if (compatibleWith6) {
                writeFloat(((Double) obj).floatValue());
            } else {
                writeDouble(((Double) obj).doubleValue());
            }
            return;
        }
        if (obj instanceof Float) {
            writeFloat(((Float) obj).floatValue());
            return;
        }
        if (obj instanceof Long) {
            writeBigInteger(((Long) obj).intValue());
            return;
        }
        if (obj instanceof Integer) {
            writeBigInteger(((Integer) obj).intValue());
            return;
        }
        if (obj instanceof Short) {
            writeSmallInteger(((Short) obj).shortValue());
            return;
        }
        if (obj instanceof Byte) {
            writeByte(((Byte) obj).byteValue());
            return;
        }
        if (obj instanceof Boolean) {
            writeByte(((Boolean) obj).booleanValue() ? (byte) 1 : (byte) 0);
            return;
        }
        if (obj instanceof Tool) {
            Tool tool = (Tool) obj;
            writeBigInteger(tool.getIndex());
            return;
        }
        if (obj instanceof LibId) {
            LibId libId = (LibId) obj;
            writeString(libId.libName);
            return;
        }
        writeObj(obj);
    }

    /**
     * Method to write a text descriptor (possibly with variable bits).
     * Face of text descriptor is mapped according to "faceMap".
     * @param varBits variableBits or -1.
     * @param td TextDescriptor to write.
     */
    private void writeTextDescriptor(int varBits, TextDescriptor td) throws IOException {
        int td0;
        int td1;
        if (td != null) {
            td0 = td.lowLevelGet0();
            td1 = td.lowLevelGet1();
            // Convert font face
            if ((td1 & ELIBConstants.VTFACE) != 0) {
                int face = (td1 & ELIBConstants.VTFACE) >> ELIBConstants.VTFACESH;
                td1 = (td1 & ~ELIBConstants.VTFACE) | (faceMap[face] << ELIBConstants.VTFACESH);
            }
        } else {
            td0 = 0;
            td1 = 0;
        }
        writeTextDescriptor(varBits, td0, td1);
    }

    /**
     * Method to write a text descriptor (possibly with variable bits).
     * Face of text descriptor is mapped according to "faceMap".
     * @param varBits variableBits or -1.
     * @param td0 first word of TextDescriptor to write.
     * @param td1 first word of TextDescriptor to write.
     */
    void writeTextDescriptor(int varBits, int td0, int td1) throws IOException {
        if (varBits != -1) {
            writeBigInteger(varBits);
        }
        writeBigInteger(td0);
        writeBigInteger(td1);
    }

    /**
     * Method to write a disk index of Object.
     * Index is obtained fron objInfo map.
     * @param obj Object to write
     */
    void writeObj(Object obj) throws IOException {
        int objIndex = -1;
        if (obj != null) {
            Integer i = objInfo.get(obj);
            if (i != null) {
                objIndex = i.intValue();
            }
        }
        writeBigInteger(objIndex);
    }

    /**
     * Method to write a coordinate as (4 bytes) integer to the ouput stream.
     * @param cellBackup cell to determine scale
     * @param keyword keywork fro ReadableDump
     * @param gridCoord coordinate in grid units.
     */
    private void writeGridCoord(CellBackup cellBackup, String keyword, double gridCoord) throws IOException {
        writeGridCoord(cellBackup.cellRevision, keyword, gridCoord);
    }

    /**
     * Method to write a coordinate as (4 bytes) integer to the ouput stream.
     * @param cellRevision cell to determine scale
     * @param keyword keywork fro ReadableDump
     * @param gridCoord coordinate in grid units.
     */
    private void writeGridCoord(CellRevision cellRevision, String keyword, double gridCoord) throws IOException {
//        int i = gridCoordToElib(cellBackup.d.tech, gridCoord);
        Technology tech = snapshot.getTech(cellRevision.d.techId);
        writeInt(keyword, gridCoordToElib(tech, gridCoord));
    }

    int gridCoordToElib(Technology tech, double gridCoord) {
        return (int) Math.round(gridCoord * getScale(tech) * 2 / DBMath.GRID);
    }

    double getScale(TechId techId) {
        return getScale(snapshot.getTech(techId));
    }

    double getScale(Technology tech) {
        return tech.isScaleRelevant() || tech == Generic.tech() ? tech.getScale() : irrelevantScale;
    }

    private Technology.SizeCorrector getSizeCorrector(TechId techId) {
        Technology.SizeCorrector corrector = sizeCorrectors.get(techId);
        if (corrector == null) {
            corrector = techPool.getTech(techId).getSizeCorrector(Version.getVersion(), projectSettings, false, false);
            sizeCorrectors.put(techId, corrector);
        }
        return corrector;
    }

    /**
     * Method to write an integer (4 bytes) to the ouput stream.
     * @param keyword keywork fro ReadableDump
     * @parma i integer value.
     */
    void writeInt(String keyword, int i) throws IOException {
        writeBigInteger(i);
    }

    /**
     * Method to write a text into ReadableDump stream.
     * @param txt a text to write into ReadableDump stream.
     */
    void writeTxt(String txt) throws IOException {
    }

    /**
     * Method to write a disk index of variable name.
     * Index is obtained from the nameSpace map.
     * @param name Variable Key to write
     */
    void writeVariableName(String name) throws IOException {
        short varNameIndex = nameSpace.get(name).shortValue();
        writeSmallInteger(varNameIndex);
    }

    // --------------------------------- LOW-LEVEL OUTPUT ---------------------------------
    /**
     * Method to write a single byte from the input stream and return it.
     */
    private void writeByte(byte b)
            throws IOException {
        dataOutputStream.write(b);
    }

    /**
     * Method to write an integer (4 bytes) from the input stream and return it.
     */
    void writeBigInteger(int i)
            throws IOException {
        dataOutputStream.writeInt(i);
    }

    /**
     * Method to write a float (4 bytes) from the input stream and return it.
     */
    private void writeFloat(float f)
            throws IOException {
        dataOutputStream.writeFloat(f);
    }

    /**
     * Method to write a double (8 bytes) from the input stream and return it.
     */
    private void writeDouble(double d)
            throws IOException {
        dataOutputStream.writeDouble(d);
    }

    /**
     * Method to write an short (2 bytes) from the input stream and return it.
     */
    private void writeSmallInteger(short s)
            throws IOException {
        dataOutputStream.writeShort(s);
    }

    /**
     * Method to write a string from the input stream and return it.
     */
    private void writeString(String s)
            throws IOException {
        // disk and memory match: write the data
        int len = s.length();
        writeBigInteger(len);
        dataOutputStream.write(s.getBytes(), 0, len);
    }
}
