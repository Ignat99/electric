/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Schematic.java
 *
 * Copyright (c) 2004, Static Free Software. All rights reserved.
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
package com.sun.electric.tool.drc;

import com.sun.electric.database.geometry.EPoint;
import com.sun.electric.database.geometry.ERectangle;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.Export;
import com.sun.electric.database.hierarchy.View;
import com.sun.electric.database.network.Netlist;
import com.sun.electric.database.network.Network;
import com.sun.electric.database.prototype.NodeProto;
import com.sun.electric.database.prototype.PortProto;
import com.sun.electric.database.text.Name;
import com.sun.electric.database.topology.*;
import com.sun.electric.database.variable.ElectricObject;
import com.sun.electric.database.variable.Variable;
import com.sun.electric.technology.PrimitiveNode;
import com.sun.electric.technology.technologies.Artwork;
import com.sun.electric.technology.technologies.Generic;
import com.sun.electric.technology.technologies.Schematics;
import com.sun.electric.tool.user.ErrorLogger;
import com.sun.electric.util.TextUtils;
import com.sun.electric.util.math.GenMath;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.*;

/**
 * Class to do schematic design-rule checking. Examines artwork of a schematic
 * for sensibility.
 */
public class Schematic {
    // Cells, nodes and arcs

    private Set<ElectricObject> nodesChecked = new HashSet<ElectricObject>();
    private ErrorLogger errorLogger;
    private Map<Geometric, List<Variable>> newVariables = new HashMap<Geometric, List<Variable>>();

    public static void doCheck(ErrorLogger errorLog, Cell cell, Geometric[] geomsToCheck, DRC.DRCPreferences dp) {
        Schematic s = new Schematic();
        s.errorLogger = errorLog;
        s.checkSchematicCellRecursively(cell, geomsToCheck);
        DRC.addDRCUpdate(0, null, null, null, null, s.newVariables, dp);
    }

    private Cell isACellToCheck(Geometric geo) {
        if (geo instanceof NodeInst) {
            NodeInst ni = (NodeInst) geo;

            // ignore documentation icon
            if (ni.isIconOfParent()) {
                return null;
            }

            if (!ni.isCellInstance()) {
                return null;
            }
            Cell subCell = (Cell) ni.getProto();

            Cell contentsCell = subCell.contentsView();
            if (contentsCell == null) {
                contentsCell = subCell;
            }
            if (nodesChecked.contains(contentsCell)) {
                return null;
            }
            return contentsCell;
        }
        return null;
    }

    private void checkSchematicCellRecursively(Cell cell, Geometric[] geomsToCheck) {
        nodesChecked.add(cell);

        // ignore if not a schematic
        if (!cell.isSchematic() && cell.getTechnology() != Schematics.tech()) {
            return;
        }

        // recursively check contents in case of hierchically checking
        if (geomsToCheck == null) {
            for (Iterator<NodeInst> it = cell.getNodes(); it.hasNext();) {
                NodeInst ni = it.next();
                Cell contentsCell = isACellToCheck(ni);
                if (contentsCell != null) {
                    checkSchematicCellRecursively(contentsCell, geomsToCheck);
                }
            }
        } else {
            for (Geometric geo : geomsToCheck) {
                Cell contentsCell = isACellToCheck(geo);

                if (contentsCell != null) {
                    checkSchematicCellRecursively(contentsCell, geomsToCheck);
                }
            }
        }

        // now check this cell
        System.out.println("Checking schematic " + cell);
        ErrorGrouper eg = new ErrorGrouper(cell);
        checkSchematicCell(cell, false, geomsToCheck, eg);
    }
    private int cellIndexCounter;

    private class ErrorGrouper {

        private boolean inited;
        private int cellIndex;
        private Cell cell;

        ErrorGrouper(Cell cell) {
            inited = false;
            cellIndex = cellIndexCounter++;
            this.cell = cell;
        }

        public int getSortKey() {
            if (!inited) {
                inited = true;
                errorLogger.setGroupName(cellIndex, cell.getName());
            }
            return cellIndex;
        }
    }

    private void checkSchematicCell(Cell cell, boolean justThis, Geometric[] geomsToCheck, ErrorGrouper eg) {
        int initialErrorCount = errorLogger.getNumErrors();
        Netlist netlist = cell.getNetlist();

        // Normal hierarchically geometry
        if (geomsToCheck == null) {
            for (Iterator<NodeInst> it = cell.getNodes(); it.hasNext();) {
                NodeInst ni = it.next();
                if (!ni.isCellInstance()
                        && ni.getProto().getTechnology() == Generic.tech()) {
                    continue;
                }
                schematicDoCheck(netlist, ni, eg);
            }
            for (Iterator<ArcInst> it = cell.getArcs(); it.hasNext();) {
                ArcInst ai = it.next();
                schematicDoCheck(netlist, ai, eg);
            }
        } else {
            for (Geometric geo : geomsToCheck) {
                schematicDoCheck(netlist, geo, eg);
            }
        }

        checkCaseInsensitiveNetworks(netlist, eg);
        checkArrayedIconsConflicts(cell, eg);

        int errorCount = errorLogger.getNumErrors();
        int thisErrors = errorCount - initialErrorCount;
        String indent = "   ";
        if (justThis) {
            indent = "";
        }
        if (thisErrors == 0) {
            System.out.println(indent + "No errors found");
        } else {
            System.out.println(indent + thisErrors + " errors found");
        }
        if (justThis) {
            errorLogger.termLogging(true);
        }
    }

    /**
     * Method to add all variables of a given NodeInst that must be added after
     * Schematics DRC job is done.
     */
    private void addVariable(NodeInst ni, Variable var) {
        List<Variable> list = newVariables.get(ni);

        if (list == null) // first time
        {
            list = new ArrayList<Variable>();
            newVariables.put(ni, list);
        }
        list.add(var);
    }

    /**
     * Method to check schematic object "geom".
     */
    private void schematicDoCheck(Netlist netlist, Geometric geom, ErrorGrouper eg) {
        // Checked already
        if (nodesChecked.contains(geom)) {
            return;
        }
        nodesChecked.add(geom);

        Cell cell = geom.getParent();
        if (geom instanceof NodeInst) {
            NodeInst ni = (NodeInst) geom;
            NodeProto np = ni.getProto();

            // check for bus pins that don't connect to any bus arcs
            if (np == Schematics.tech().busPinNode) {
                // proceed only if it has no exports on it
                if (!ni.hasExports()) {
                    // must not connect to any bus arcs
                    boolean found = false;
                    for (Iterator<Connection> it = ni.getConnections(); it.hasNext();) {
                        Connection con = it.next();
                        if (con.getArc().getProto() == Schematics.tech().bus_arc) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        errorLogger.logError("Bus pin does not connect to any bus arcs", geom, cell, null, eg.getSortKey());
                        return;
                    }
                }

                // make a list of all bus networks at the pin
                Set<Network> onPin = new HashSet<Network>();
                boolean hadBusses = false;
                for (Iterator<Connection> it = ni.getConnections(); it.hasNext();) {
                    Connection con = it.next();
                    ArcInst ai = con.getArc();
                    if (ai.getProto() != Schematics.tech().bus_arc) {
                        continue;
                    }
                    hadBusses = true;
                    int wid = netlist.getBusWidth(ai);
                    for (int i = 0; i < wid; i++) {
                        Network net = netlist.getNetwork(ai, i);
                        onPin.add(net);
                    }
                }

                // flag any wire arcs not connected to each other through the bus pin
                List<Geometric> geomList = null;
                for (Iterator<Connection> it = ni.getConnections(); it.hasNext();) {
                    Connection con = it.next();
                    ArcInst ai = con.getArc();
                    if (ai.getProto() != Schematics.tech().wire_arc) {
                        continue;
                    }
                    Network net = netlist.getNetwork(ai, 0);
                    if (onPin.contains(net)) {
                        continue;
                    }
                    if (geomList == null) {
                        geomList = new ArrayList<Geometric>();
                    }
                    geomList.add(ai);
                }
                if (geomList != null) {
                    geomList.add(ni);
                    String msg;
                    if (hadBusses) {
                        msg = "Wire arcs do not connect to bus through a bus pin";
                    } else {
                        msg = "Wire arcs do not connect to each other through a bus pin";
                    }
                    errorLogger.logMessage(msg, geomList, cell, eg.getSortKey(), true);
                    return;
                }
            }

            // check all pins
            if (np.getFunction().isPin()) {
                // may be stranded if there are no exports or arcs
                if (!ni.hasExports() && !ni.hasConnections()) {
                    // see if the pin has displayable variables on it
                    boolean found = false;
                    for (Iterator<Variable> it = ni.getVariables(); it.hasNext();) {
                        Variable var = it.next();
                        if (var.isDisplay()) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        errorLogger.logError("Stranded pin (not connected or exported)", geom, cell, null, eg.getSortKey());
                        return;
                    }
                }

                if (ni.isInlinePin()) {
                    errorLogger.logError("Unnecessary pin (between 2 arcs)", geom, cell, null, eg.getSortKey());
                    return;
                }

                Point2D pinLoc = ni.invisiblePinWithOffsetText(false);
                if (pinLoc != null) {
                    List<Geometric> geomList = new ArrayList<Geometric>();
                    List<EPoint> ptList = new ArrayList<EPoint>();
                    geomList.add(geom);
                    ptList.add(ni.getAnchorCenter());
                    ptList.add(EPoint.fromLambda(pinLoc.getX(), pinLoc.getY()));
                    errorLogger.logMessageWithLines("Invisible pin has text in different location",
                            geomList, ptList, cell, eg.getSortKey(), true);
                    return;
                }
            }

            // check parameters
            if (np instanceof Cell) {
                Cell instCell = (Cell) np;
                Cell contentsCell = instCell.contentsView();
                if (contentsCell == null) {
                    contentsCell = instCell;
                }

                // ensure that this node matches the parameter list
                for (Iterator<Variable> it = ni.getDefinedParameters(); it.hasNext();) {
                    Variable var = it.next();
                    assert ni.isParam(var.getKey());

                    Variable foundVar = contentsCell.getParameter(var.getKey());
                    if (foundVar == null) {
                        // this node's parameter is no longer on the cell: delete from instance
                        String trueVarName = var.getTrueName();
                        errorLogger.logError("Parameter '" + trueVarName + "' on " + ni
                                + " is invalid", geom, cell, null, eg.getSortKey());
                    } else {
                        // this node's parameter is still on the cell: make sure units are OK
                        if (var.getUnit() != foundVar.getUnit()) {
                            String trueVarName = var.getTrueName();
                            errorLogger.logError("Parameter '" + trueVarName + "' on " + ni
                                    + " had incorrect units (now fixed)", geom, cell, null, eg.getSortKey());
                            addVariable(ni, var.withUnit(foundVar.getUnit()));
                        }

                        // make sure visibility is OK
                        if (foundVar.isInterior()) {
                            if (var.isDisplay()) {
                                String trueVarName = var.getTrueName();
                                errorLogger.logError("Parameter '" + trueVarName + "' on " + ni
                                        + " should not be visible (now fixed)", geom, cell, null, eg.getSortKey());
                                addVariable(ni, var.withDisplay(false));
                            }
                        } else {
                            if (!var.isDisplay()) {
                                String trueVarName = var.getTrueName();
                                errorLogger.logError("Parameter '" + trueVarName + "' on " + ni
                                        + " should be visible (now fixed)", geom, cell, null, eg.getSortKey());
                                addVariable(ni, var.withDisplay(true));
                            }
                        }
                    }
                }

                // make sure instance name isn't the same as a network in the cell
                String nodeName = ni.getName();
                for (Iterator<Network> it = netlist.getNetworks(); it.hasNext();) {
                    Network net = it.next();
                    if (net.hasName(nodeName)) {
                        errorLogger.logError("Node " + ni + " is named '" + nodeName
                                + "' which conflicts with a network name in this cell", geom, cell, null, eg.getSortKey());
                        break;
                    }
                }
            }

            // check all exports for proper icon/schematics characteristics match
            Cell parentCell = ni.getParent();
            for (Cell iconCell : parentCell.getCellsInGroup()) {
                if (iconCell.getView() != View.ICON) {
                    continue;
                }
                for (Iterator<Export> it = ni.getExports(); it.hasNext();) {
                    Export e = it.next();
                    List<Export> allExports = e.findAllEquivalents(iconCell, false);
                    for (Export iconExport : allExports) {
                        if (e.getCharacteristic() != iconExport.getCharacteristic()) {
                            errorLogger.logError("Export '" + e.getName() + "' on " + ni
                                    + " is " + e.getCharacteristic().getFullName()
                                    + " but export in icon cell " + iconCell.describe(false) + " is "
                                    + iconExport.getCharacteristic().getFullName(), geom, cell, null, eg.getSortKey());
                        }
                    }
                }
            }

            // check for port overlap
            checkPortOverlap(netlist, ni, eg);
        } else {
            ArcInst ai = (ArcInst) geom;

            // check for being floating if it does not have a visible name on it
            boolean checkDangle = false;

            if (Artwork.isArtworkArc(ai.getProto())) {
                return; // ignore artwork arcs
            }
            Name arcName = ai.getNameKey();
            if (arcName == null || arcName.isTempname()) {
                checkDangle = true;
            }
            if (checkDangle) {
                // do not check for dangle when busses are on named networks
                if (ai.getProto() == Schematics.tech().bus_arc) {
                    Name name = netlist.getBusName(ai);
                    if (name != null && !name.isTempname()) {
                        checkDangle = false;
                    }
                }
            }
            if (checkDangle) {
                // check to see if this arc is floating
                for (int i = 0; i < 2; i++) {
                    NodeInst ni = ai.getPortInst(i).getNodeInst();

                    // OK if not a pin
                    if (!ni.getProto().getFunction().isPin()) {
                        continue;
                    }

                    // OK if it has exports on it
                    if (ni.hasExports()) {
                        continue;
                    }

                    // OK if it connects to more than 1 arc
                    if (ni.getNumConnections() != 1) {
                        continue;
                    }

                    // the arc dangles
                    errorLogger.logError("Arc dangles", geom, cell, null, eg.getSortKey());
                    return;
                }
            }

            // check to see if its width is sensible
            int signals = netlist.getBusWidth(ai);
            if (signals < 1) {
                signals = 1;
            }
            for (int i = 0; i < 2; i++) {
                PortInst pi = ai.getPortInst(i);
                NodeInst ni = pi.getNodeInst();
                if (!ni.isCellInstance()) {
                    continue;
                }
                Cell subNp = (Cell) ni.getProto();
                PortProto pp = pi.getPortProto();

                Cell np = subNp.contentsView();
                if (np != null) {
                	PortProto ppEquiv = ((Export) pi.getPortProto()).findEquivalent(np);
                	if (ppEquiv == null || ppEquiv == pi.getPortProto()) {
                        List<Geometric> geomList = new ArrayList<Geometric>();
                        geomList.add(geom);
                        geomList.add(ni);
                        errorLogger.logMessage("Arc " + ai.describe(true) + " connects to "
                                + pi.getPortProto() + " of " + ni + ", but there is no equivalent port in " + np,
                                geomList, cell, eg.getSortKey(), true);
                        continue;
                    }
                }

                int portWidth = netlist.getBusWidth((Export) pp);
                if (portWidth < 1) {
                    portWidth = 1;
                }
                int nodeSize = ni.getNameKey().busWidth();
                if (nodeSize <= 0) {
                    nodeSize = 1;
                }
                if (signals != portWidth && signals != portWidth * nodeSize) {
                    List<Geometric> geomList = new ArrayList<Geometric>();
                    geomList.add(geom);
                    geomList.add(ni);
                    errorLogger.logMessage("Arc " + ai.describe(true) + " (" + signals + " wide) connects to "
                            + pp + " of " + ni + " (" + portWidth + " wide)", geomList, cell, eg.getSortKey(), true);
                }
            }

            // check to see if it covers a pin
            Rectangle2D rect = ai.getBounds();
            Network net = netlist.getNetwork(ai, 0);
            for (Iterator<Geometric> sea = cell.searchIterator(rect); sea.hasNext();) {
                Geometric oGeom = sea.next();
                if (oGeom instanceof NodeInst) {
                    NodeInst ni = (NodeInst) oGeom;

                    // must be a pin on an unconnected network
                    if (ni.getFunction() != PrimitiveNode.Function.PIN) {
                        continue;
                    }
                    if (ni.getProto().getTechnology() == Generic.tech()) {
                        continue;
                    }
                    Network oNet = netlist.getNetwork(ni.getOnlyPortInst());
                    if (net == oNet) {
                        continue;
                    }

                    // if it is an oversize bus pin, allow it
//	            	Rectangle2D bound = ni.getBounds();
//                    if (bound.getWidth() > 0 || bound.getHeight() > 0) continue;
                    long[] gridCoords = new long[4];
                    ((PrimitiveNode) ni.getProto()).genElibBounds(cell.backup(), ni.getD(), gridCoords);
                    ERectangle bound = ERectangle.fromGrid(gridCoords[0], gridCoords[1],
                            gridCoords[2] - gridCoords[0], gridCoords[3] - gridCoords[1]);
                    if (bound.getGridWidth() > 0 || bound.getGridHeight() > 0) {
                        continue;
                    }
//	            	Rectangle2D bound = ni.getBounds();
//            		if (ni.getProto() == Schematics.tech().busPinNode)
//            		{
//		            	PrimitiveNode pnp = (PrimitiveNode)ni.getProto();
//		            	ERectangle pBounds = pnp.getBaseRectangle();
//		            	if (bound.getWidth() > pBounds.getWidth() || bound.getHeight() > pBounds.getHeight()) continue;
//            		}

                    // error if it is on the line of this arc
                    Point2D ctr = new Point2D.Double(bound.getCenterX(), bound.getCenterY());
                    if (GenMath.isOnLine(ai.getHeadLocation(), ai.getTailLocation(), ctr)) {
                        List<Geometric> geomList = new ArrayList<Geometric>();
                        geomList.add(ai);
                        geomList.add(ni);
                        errorLogger.logMessage("Pin " + ni.describe(false) + " touches arc " + ai.describe(true) + " but does not connect to it ",
                                geomList, cell, eg.getSortKey(), true);
                    }
                }
            }
        }
    }

    /**
     * Method to check whether any port on a node overlaps others without
     * connecting.
     */
    private void checkPortOverlap(Netlist netlist, NodeInst ni, ErrorGrouper eg) {
        if (ni.getProto().getTechnology() == Generic.tech()
                || ni.getProto().getTechnology() == Artwork.tech()) {
            return;
        }
        Cell cell = ni.getParent();
        for (Iterator<PortInst> it = ni.getPortInsts(); it.hasNext();) {
            PortInst pi = it.next();
            Network net = netlist.getNetwork(pi);
            Rectangle2D bounds = pi.getPoly().getBounds2D();
            for (Iterator<Geometric> sIt = cell.searchIterator(bounds); sIt.hasNext();) {
                Geometric oGeom = sIt.next();
                if (!(oGeom instanceof NodeInst)) {
                    continue;
                }
                NodeInst oNi = (NodeInst) oGeom;
                if (ni == oNi) {
                    continue;
                }
                if (ni.getNodeId() > oNi.getNodeId()) {
                    continue;
                }
                if (oNi.getProto().getTechnology() == Generic.tech()
                        || oNi.getProto().getTechnology() == Artwork.tech()) {
                    continue;
                }

                // see if ports touch
                for (Iterator<PortInst> pIt = oNi.getPortInsts(); pIt.hasNext();) {
                    PortInst oPi = pIt.next();
                    Rectangle2D oBounds = oPi.getPoly().getBounds2D();
                    if (bounds.getMaxX() < oBounds.getMinX()) {
                        continue;
                    }
                    if (bounds.getMinX() > oBounds.getMaxX()) {
                        continue;
                    }
                    if (bounds.getMaxY() < oBounds.getMinY()) {
                        continue;
                    }
                    if (bounds.getMinY() > oBounds.getMaxY()) {
                        continue;
                    }

                    // see if they are connected
                    if (net == netlist.getNetwork(oPi)) {
                        continue;
                    }

                    // report the error
                    List<Geometric> geomList = new ArrayList<Geometric>();
                    geomList.add(ni);
                    geomList.add(oNi);
                    errorLogger.logMessage("Nodes '" + ni + "' '" + oNi + "' have touching ports that are not connected",
                            geomList, cell, eg.getSortKey(), true);
                    return;
                }
            }
        }
    }

    private void checkCaseInsensitiveNetworks(Netlist netlist, ErrorGrouper eg) {
        Cell cell = netlist.getCell();
        HashMap<String, Network> canonicToNetwork = new HashMap<String, Network>();
        for (Iterator<Network> it = netlist.getNetworks(); it.hasNext();) {
            Network net = it.next();
            for (Iterator<String> sit = net.getNames(); sit.hasNext();) {
                String s = sit.next();
                String cs = TextUtils.canonicString(s);
                Network net1 = canonicToNetwork.get(cs);
                if (net1 == null) {
                    canonicToNetwork.put(cs, net);
                } else if (net1 != net) {
                    String message = "Network: Schematic " + cell.libDescribe() + " doesn't connect " + net + " and " + net1;
                    boolean sameName = net1.hasName(s);
                    if (sameName) {
                        message += " Like-named Global and Export may be connected in future releases";
                    }
                    System.out.println(message);
                    List<Geometric> geomList = new ArrayList<Geometric>();
                    push(geomList, net);
                    push(geomList, net1);
                    errorLogger.logMessage(message, geomList, cell, eg.getSortKey(), sameName);
                }
            }
        }
    }

    private void checkArrayedIconsConflicts(Cell cell, ErrorGrouper eg) {
        IdentityHashMap<Name, NodeInst> name2node = new IdentityHashMap<Name, NodeInst>();
        for (Iterator<NodeInst> it = cell.getNodes(); it.hasNext();) {
            NodeInst ni = it.next();
            Name n = ni.getNameKey();
            if (n.isTempname()) {
                continue;
            }
            for (int arrayIndex = 0; arrayIndex < n.busWidth(); arrayIndex++) {
                Name subName = n.subname(arrayIndex);
                NodeInst oni = name2node.get(subName);
                if (oni != null) {
                    String msg = "Network: " + cell + " has instances " + ni + " and "
                            + oni + " with same name <" + subName + ">";
                    System.out.println(msg);
                    List<Geometric> geomList = Arrays.<Geometric>asList(ni, oni);
                    errorLogger.logMessage(msg, geomList, cell, eg.getSortKey(), true);
                }
            }
        }
    }

    private void push(List<Geometric> geomList, Network net) {
        Iterator<Export> eit = net.getExports();
        if (eit.hasNext()) {
            geomList.add(eit.next().getOriginalPort().getNodeInst());
            return;
        }
        Iterator<ArcInst> ait = net.getArcs();
        if (ait.hasNext()) {
            geomList.add(ait.next());
            return;
        }
        Iterator<PortInst> pit = net.getPorts();
        if (pit.hasNext()) {
            geomList.add(pit.next().getNodeInst());
            return;
        }
    }
}
