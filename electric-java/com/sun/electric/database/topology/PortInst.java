/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: PortInst.java
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
package com.sun.electric.database.topology;

import com.sun.electric.database.ImmutablePortInst;
import com.sun.electric.database.geometry.EPoint;
import com.sun.electric.database.geometry.Poly;
import com.sun.electric.database.hierarchy.EDatabase;
import com.sun.electric.database.hierarchy.Export;
import com.sun.electric.database.prototype.PortProto;
import com.sun.electric.database.text.Name;
import com.sun.electric.database.variable.EditWindow0;
import com.sun.electric.database.variable.ElectricObject;
import com.sun.electric.database.variable.Variable;
import com.sun.electric.util.math.FixpRectangle;

import java.awt.geom.Rectangle2D;
import java.io.InvalidObjectException;
import java.io.NotSerializableException;
import java.io.ObjectStreamException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * The PortInst class represents an instance of a Port.  It is the
 * combination of a NodeInst and a PortProto.
 * <P>
 * This class is thread-safe.
 */
public class PortInst extends ElectricObject implements SteinerTree.SteinerTreePort {
    // ------------------------ private data ------------------------

    private final NodeInst nodeInst;
    private final PortProto portProto;

    // -------------------protected or private methods ---------------
    private PortInst(PortProto portProto, NodeInst nodeInst) {
        this.portProto = portProto;
        this.nodeInst = nodeInst;
    }

    private Object writeReplace() throws NotSerializableException {
        if (!isLinked()) {
            throw new NotSerializableException(this + " not linked");
        }
        return this;
    }

    private Object readResolve() throws ObjectStreamException {
        if (nodeInst.getProto() != portProto.getParent()) {
            throw new InvalidObjectException("PortInst");
        }
        PortInst pi = nodeInst.findPortInstFromProto(portProto);
        if (pi == null) {
            throw new InvalidObjectException("PortInst");
        }
        return pi;
    }

    /**
     * Returns persistent data of this ElectricObject with Variables.
     * @return persistent data of this ElectricObject.
     */
    @Override
    public ImmutablePortInst getD() {
        return nodeInst.getD().getPortInst(portProto.getId());
    }

    /**
     * Method to add a Variable on this PortInst.
     * It may add repaired copy of this Variable in some cases.
     * @param var Variable to add.
     */
    public void addVar(Variable var) {
        nodeInst.addVar(portProto.getId(), var);
    }

    /**
     * Method to delete a Variable from this PortInst.
     * @param key the key of the Variable to delete.
     */
    public void delVar(Variable.Key key) {
        nodeInst.delVar(portProto.getId(), key);
    }

    /**
     * Method to delete all Variables of this PortInst.
     */
    public void delVars() {
        nodeInst.delVars(portProto.getId());
        assert getNumVariables() == 0;
    }

    // ------------------------ public methods -------------------------
    /**
     * Method to create a PortInst object.
     * @param portProto the PortProto on the prototype of the NodeInst.
     * @param nodeInst the NodeInst that owns the port.
     * @return the newly created PortInst.
     */
    public static PortInst newInstance(PortProto portProto, NodeInst nodeInst) {
        PortInst pi = new PortInst(portProto, nodeInst);
        return pi;
    }

    /**
     * Method to return the NodeInst that this PortInst resides on.
     * @return the NodeInst that this PortInst resides on.
     */
    public NodeInst getNodeInst() {
        return nodeInst;
    }

    /**
     * Method to return the PortProto that this PortInst is an instance of.
     * @return the PortProto that this PortInst is an instance of.
     */
    public PortProto getPortProto() {
        return portProto;
    }

    /**
     * Method to get the index of this PortInst in NodeInst ports.
     * @return index of this PortInst in NodeInst ports.
     */
    public final int getPortIndex() {
        return portProto.getPortIndex();
    }

    /**
     * Returns true of there are Connections on this PortInst.
     * @return true if there are Connections on this PortInst.
     * @throws IllegalArgumetException if node inst is not linked to this CellRevision
     */
    public boolean hasConnections() {
        return nodeInst.hasConnections(portProto.getId());
    }

    /**
     * Get iterator of all Connections
     * that connect to this PortInst
     * @return an iterator over associated Connections
     * @throws IllegalArgumetException if node inst is not linked to this CellRevision
     */
    public Iterator<Connection> getConnections() {
        return nodeInst.getConnections(portProto.getId());
    }

    /**
     * Get iterator of all Exports
     * that connect to this PortInst
     * @return an iterator over associated Exports
     * @throws IllegalArgumetException if node inst is not linked to this CellRevision
     */
    public Iterator<Export> getExports() {
        return nodeInst.getExports(portProto.getId());
    }

    /**
     * Method to return the bounds of this PortInst.
     * The bounds are determined by getting the Poly and bounding it.
     * @return the bounds of this PortInst.
     */
    public FixpRectangle getBounds() {
        return getPoly().getBounds2D();
    }

    public EPoint getCenter() {
        return getPoly().getCenter();
    }

    /**
     * Method to return the Poly that describes this PortInst.
     * @return the Poly that describes this PortInst.
     */
    public Poly getPoly() {
        return nodeInst.getShapeOfPort(portProto);
    }

    /**
     * Method to add all displayable Variables on this PortInsts to an array of Poly objects.
     * @param rect a rectangle describing the bounds of the NodeInst on which the PortInsts reside.
     * @param polys a list of Poly objects that will be filled with the displayable Variables.
     * @param wnd window in which the Variables will be displayed.
     * @param multipleStrings true to break multiline text into multiple Polys.
     * @param showTempNames show temporary names on nodes and arcs
     */
    @Override
    public void addDisplayableVariables(Rectangle2D rect, List<Poly> polys, EditWindow0 wnd, boolean multipleStrings, boolean showTempNames) {
        int startOfMyPolys = polys.size();
        super.addDisplayableVariables(getBounds(), polys, wnd, multipleStrings, showTempNames);
        for (int i = startOfMyPolys; i < polys.size(); i++) {
            polys.get(i).setPort(getPortProto());
        }
    }

    /**
     * Method to describe this NodeInst as a string.
     * @param withQuotes to wrap description between quotes
     * @return a description of this NodeInst as a string.
     */
    public String describe(boolean withQuotes) {
        String info = nodeInst.describe(false) + "." + portProto.getName();
        return (withQuotes) ? "'" + info + "'" : info;
    }

    /**
     * Returns a printable version of this PortInst.
     * @return a printable version of this PortInst.
     */
    public String toString() {
        return "port " + describe(true);
    }

    /**
     * This function is to compare PortInst elements. Initiative CrossLibCopy
     * @param obj Object to compare to
     * @param buffer To store comparison messages in case of failure
     * @return True if objects represent same PortInst
     */
    public boolean compare(Object obj, StringBuffer buffer) {
        if (this == obj) {
            return (true);
        }

        // Better if compare classes? but it will crash with obj=null
        if (obj == null || getClass() != obj.getClass()) {
            return (false);
        }

        PortInst no = (PortInst) obj;
        Set<Connection> noCheckAgain = new HashSet<Connection>();
        for (Iterator<Connection> it = getConnections(); it.hasNext();) {
            Connection c = it.next();
            boolean found = false;
            for (Iterator<Connection> noIt = no.getConnections(); noIt.hasNext();) {
                Connection noC = noIt.next();
                if (noCheckAgain.contains(noC)) {
                    continue;
                }
                if (c.getLocation().equals(noC.getLocation())) {
                    found = true;
                    noCheckAgain.add(noC);
                    break;
                }
            }

            // No correspoding NodeInst found
            if (!found) {
                if (buffer != null) {
                    buffer.append("No corresponding port " + this + " found in " + no + " at the location " + c.getLocation() + " \n");
                }
                return (false);
            }
        }

        // @TODO GVG Check this
        // Just compare connections?? or just poly for now?
        Poly poly = getPoly();
        Poly noPoly = no.getPoly();
        boolean check = poly.compare(noPoly, buffer);

        if (!check && buffer != null) {
            buffer.append("No same ports detected in " + portProto.getName() + " and " + no.getPortProto().getName() + "\n");
        }
        return (check);
    }

    /**
     * Overrides ElectricObject.isLinked().  This is because a PortInst is a derived
     * database object, and is never explicitly linked or unlinked.  It represents a NodeInst
     * and PortProto pair. So, this method really returns it's nodeinst's isLinked()
     * value.
     * @return true if the object is linked into the database, false if not.
     */
    public boolean isLinked() {
        try {
            return nodeInst != null && nodeInst.isLinked() && nodeInst.getPortInst(getPortIndex()) == this;
        } catch (IndexOutOfBoundsException e) {
            return false;
        }
    }

    /**
     * Returns database to which this PortInst belongs.
     * Some objects are not in database, for example Geometrics in PaletteFrame.
     * Method returns null for non-database objects.
     * @return database to which this PortInst belongs.
     */
    public EDatabase getDatabase() {
        return nodeInst.getDatabase();
    }

    public Poly computeTextPoly(EditWindow0 wnd, Variable var, Name name) {
        Poly poly = null;
        if (var != null) {
            Rectangle2D bounds = getPoly().getBounds2D();
            LinkedList<Poly> polys = new LinkedList<Poly>();
            addPolyList(polys, var, bounds.getCenterX(), bounds.getCenterY(), wnd, false);
            if (!polys.isEmpty()) {
                poly = polys.getFirst();
                poly.transform(getNodeInst().rotateOut());
            }
        }
        if (poly != null) {
            poly.setExactTextBounds(wnd, this);
        }
        return poly;
    }
}
