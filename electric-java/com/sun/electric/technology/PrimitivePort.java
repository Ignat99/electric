/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: PrimitivePort.java
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
package com.sun.electric.technology;

import com.sun.electric.database.EObjectInputStream;
import com.sun.electric.database.EObjectOutputStream;
import com.sun.electric.database.ImmutableNodeInst;
import com.sun.electric.database.geometry.EGraphics;
import com.sun.electric.database.geometry.EPoint;
import com.sun.electric.database.geometry.Poly;
import com.sun.electric.database.id.PrimitivePortId;
import com.sun.electric.database.prototype.PortCharacteristic;
import com.sun.electric.database.prototype.PortProto;
import com.sun.electric.database.text.Name;
import com.sun.electric.technology.technologies.Generic;
import com.sun.electric.tool.user.GraphicsPreferences;
import com.sun.electric.util.TextUtils;
import com.sun.electric.util.math.DBMath;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.PrintWriter;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * A PrimitivePort lives in a PrimitiveNode in a Tecnology.
 * It contains a list of ArcProto types that it
 * accepts connections from.
 */
public class PrimitivePort implements PortProto, Comparable<PrimitivePort>, Serializable {
    // ---------------------------- private data --------------------------

    private final PrimitivePortId portId; // The Id of this PrimitivePort.
    private final Name name; // The Name of this PrimitivePort
    private final PrimitiveNode parent; // The parent PrimitiveNode of this PrimitivePort.
    private final int portIndex; // Index of this PrimitivePort in PrimitiveNode ports.
    private final ArcProto portArcs[]; // Immutable list of possible connection types.
    private final EdgeH left;
    private final EdgeV bottom;
    private final EdgeH right;
    private final EdgeV top;
//	private Technology tech;
    private final PortCharacteristic characteristic;
    private final int angle;
    private final int angleRange;
    private final int portTopology;
    private final boolean isolated;
    private final boolean negatable;

    // ---------------------- protected and private methods ----------------
    /**
     * The constructor is only called from the factory method "newInstance".
     */
    protected PrimitivePort(PrimitiveNode parent, ArcProto[] portArcs, String protoName, boolean isSingle,
            int portAngle, int portRange, int portTopology, PortCharacteristic characteristic, boolean isolated, boolean negatable,
            EdgeH left, EdgeV bottom, EdgeH right, EdgeV top) {
        Technology tech = parent.getTechnology();
        this.name = Name.findName(protoName);
        this.portId = parent.getId().newPortId(isSingle ? "" : name.toString());

        this.parent = parent;
        this.portIndex = parent.getNumPorts();
        parent.addPrimitivePort(this);
        if (!Technology.jelibSafeName(protoName)) {
            System.out.println("PrimitivePort name " + protoName + " is not safe to write into JELIB");
        }
        angle = portAngle;
        angleRange = portRange;
        this.portTopology = portTopology;

        // initialize this object
        for (ArcProto ap : portArcs) {
            Technology apTech = ap.getTechnology();
            if (apTech != tech) {
                throw new IllegalArgumentException("portArcs in " + name);
            }
        }
        if (!(tech instanceof Generic)) {
            Generic generic = tech.generic;
            ArcProto[] realPortArcs = new ArcProto[portArcs.length + 3];
            for (int i = 0; i < portArcs.length; i++) {
                realPortArcs[i] = portArcs[i];
            }
            realPortArcs[portArcs.length] = generic.universal_arc;
            realPortArcs[portArcs.length + 1] = generic.invisible_arc;
            realPortArcs[portArcs.length + 2] = generic.unrouted_arc;
            portArcs = realPortArcs;
        }
        this.portArcs = portArcs;
        this.characteristic = characteristic;
        assert left.getMultiplier() < right.getMultiplier() || left.getMultiplier() == right.getMultiplier() && left.getAdder().compareTo(right.getAdder()) <= 0;
        assert bottom.getMultiplier() < top.getMultiplier() || bottom.getMultiplier() == top.getMultiplier() && bottom.getAdder().compareTo(top.getAdder()) <= 0;
        this.left = left;
        this.bottom = bottom;
        this.right = right;
        this.top = top;
        this.isolated = isolated;
        this.negatable = negatable;
    }

    protected Object writeReplace() {
        return new PrimitivePortKey(this);
    }

    private static class PrimitivePortKey extends EObjectInputStream.Key<PrimitivePort> {

        public PrimitivePortKey() {
        }

        private PrimitivePortKey(PrimitivePort pp) {
            super(pp);
        }

        @Override
        public void writeExternal(EObjectOutputStream out, PrimitivePort pp) throws IOException {
            out.writeObject(pp.getParent());
            out.writeInt(pp.getId().chronIndex);
        }

        @Override
        public PrimitivePort readExternal(EObjectInputStream in) throws IOException, ClassNotFoundException {
            PrimitiveNode pn = (PrimitiveNode) in.readObject();
            int chronIndex = in.readInt();
            PrimitivePort pp = pn.getPrimitivePortByChronIndex(chronIndex);
            if (pp == null) {
                throw new InvalidObjectException("primitive port not linked");
            }
            return pp;
        }
    }

    static class Polygonal extends PrimitivePort {

        Polygonal(PrimitiveNode parent, ArcProto[] portArcs, String protoName, boolean isSingle,
                int portAngle, int portRange, int portTopology,
                EdgeH left, EdgeV bottom, EdgeH right, EdgeV top) {
            super(parent, portArcs, protoName, isSingle, portAngle, portRange, portTopology,
                    PortCharacteristic.UNKNOWN, false, false, left, bottom, right, top);
        }

        @Override
        protected void genShape(AbstractShapeBuilder b, ImmutableNodeInst n) {
            // see if there is outline information
            // outline may determine the port
            EPoint[] outline = n.getTrace();
            if (outline != null) {
                int endPortPoly = outline.length;
                for (int i = 1; i < outline.length; i++) {
                    if (outline[i] == null) {
                        endPortPoly = i;
                        break;
                    }
                }
                for (int i = 0; i < endPortPoly; i++) {
                    b.pushPoint(outline[i]);
                }
                Poly.Type style;
                if (getParent().getPrimitiveFunction(n.techBits) == PrimitiveNode.Function.NODE) {
                    style = Poly.Type.FILLED;
                } else {
                    style = Poly.Type.OPENED;
                }
                b.pushPoly(style, null, null, null);
                return;
            }
            super.genShape(b, n);
        }
    }

    static class Serpentine extends PrimitivePort {

        Serpentine(PrimitiveNode parent, ArcProto[] portArcs, String protoName, boolean isSingle,
                int portAngle, int portRange, int portTopology,
                EdgeH left, EdgeV bottom, EdgeH right, EdgeV top) {
            super(parent, portArcs, protoName, isSingle, portAngle, portRange, portTopology,
                    PortCharacteristic.UNKNOWN, false, false, left, bottom, right, top);
        }

        @Override
        protected void genShape(AbstractShapeBuilder b, ImmutableNodeInst n) {
            PrimitiveNode pn = getParent();
            // serpentine transistors use a more complex port determination
            AbstractShapeBuilder.SerpentineTrans std = b.newSerpentineTrans(n, pn, pn.getNodeLayers());
            if (std.hasValidData()) {
                std.fillTransPort(this);
                return;
            }
            super.genShape(b, n);
        }
    }

    /**
     * Method to create a new PrimitivePort from the parameters.
     * @param parent the PrimitiveNode on which this PrimitivePort resides.
     * @param portArcs an array of ArcProtos which can connect to this PrimitivePort.
     * @param protoName the name of this PrimitivePort.
     * It may not have unprintable characters, spaces, or tabs in it.
     * @param portAngle the primary angle that the PrimitivePort faces on the PrimitiveNode (in degrees).
     * This angle is measured counter-clockwise from a right-pointing direction.
     * @param portRange the range about the angle of allowable connections (in degrees).
     * Arcs must connect to this port within this many degrees of the port connection angle.
     * When this value is 180, then all angles are permissible, since arcs
     * can connect at up to 180 degrees in either direction from the port angle.
     * @param portTopology is a small integer that is unique among PrimitivePorts on the PrimitiveNode.
     * When two PrimitivePorts have the same topology number, it indicates that these ports are connected.
     * @param characteristic describes the nature of this PrimitivePort (input, output, power, ground, etc.)
     * @param left is an EdgeH that describes the left side of the port in a scalable way.
     * @param bottom is an EdgeV that describes the bottom side of the port in a scalable way.
     * @param right is an EdgeH that describes the right side of the port in a scalable way.
     * @param top is an EdgeV that describes the top side of the port in a scalable way.
     * @return the newly created PrimitivePort.
     */
    public static PrimitivePort newInstance(PrimitiveNode parent, ArcProto[] portArcs, String protoName,
            int portAngle, int portRange, int portTopology, PortCharacteristic characteristic,
            EdgeH left, EdgeV bottom, EdgeH right, EdgeV top) {
        return newInstance(parent, portArcs, protoName, false,
                portAngle, portRange, portTopology, characteristic, false, false,
                left, bottom, right, top);
    }

    /**
     * Method to create a new PrimitivePort from the parameters.
     * This PrimtivePort will be a single port of the parent PrimitiveNode, so
     * its PrimitivePortId will be the empty string.
     * @param parent the PrimitiveNode on which this PrimitivePort resides.
     * @param portArcs an array of ArcProtos which can connect to this PrimitivePort.
     * @param protoName the name of this PrimitivePort.
     * It may not have unprintable characters, spaces, or tabs in it.
     * @param portAngle the primary angle that the PrimitivePort faces on the PrimitiveNode (in degrees).
     * This angle is measured counter-clockwise from a right-pointing direction.
     * @param portRange the range about the angle of allowable connections (in degrees).
     * Arcs must connect to this port within this many degrees of the port connection angle.
     * When this value is 180, then all angles are permissible, since arcs
     * can connect at up to 180 degrees in either direction from the port angle.
     * @param portTopology is a small integer that is unique among PrimitivePorts on the PrimitiveNode.
     * When two PrimitivePorts have the same topology number, it indicates that these ports are connected.
     * @param characteristic describes the nature of this PrimitivePort (input, output, power, ground, etc.)
     * @param left is an EdgeH that describes the left side of the port in a scalable way.
     * @param bottom is an EdgeV that describes the bottom side of the port in a scalable way.
     * @param right is an EdgeH that describes the right side of the port in a scalable way.
     * @param top is an EdgeV that describes the top side of the port in a scalable way.
     * @return the newly created PrimitivePort.
     */
    public static PrimitivePort single(PrimitiveNode parent, ArcProto[] portArcs, String protoName,
            int portAngle, int portRange, int portTopology, PortCharacteristic characteristic,
            EdgeH left, EdgeV bottom, EdgeH right, EdgeV top) {
        return newInstance(parent, portArcs, protoName, true,
                portAngle, portRange, portTopology, characteristic, false, false,
                left, bottom, right, top);
    }

    /**
     * Method to create a new PrimitivePort from the parameters.
     * @param parent the PrimitiveNode on which this PrimitivePort resides.
     * @param portArcs an array of ArcProtos which can connect to this PrimitivePort.
     * @param protoName the name of this PrimitivePort.
     * It may not have unprintable characters, spaces, or tabs in it.
     * @param isSingle true if this is a single PrimitivePort on PrimitiveNode
     * @param portAngle the primary angle that the PrimitivePort faces on the PrimitiveNode (in degrees).
     * This angle is measured counter-clockwise from a right-pointing direction.
     * @param portRange the range about the angle of allowable connections (in degrees).
     * Arcs must connect to this port within this many degrees of the port connection angle.
     * When this value is 180, then all angles are permissible, since arcs
     * can connect at up to 180 degrees in either direction from the port angle.
     * @param portTopology is a small integer that is unique among PrimitivePorts on the PrimitiveNode.
     * When two PrimitivePorts have the same topology number, it indicates that these ports are connected.
     * @param characteristic describes the nature of this PrimitivePort (input, output, power, ground, etc.)
     * @param isolated  true if PrimtivePort is isolated
     * @param negatable true if PrimitivePort is negatable
     * @param left is an EdgeH that describes the left side of the port in a scalable way.
     * @param bottom is an EdgeV that describes the bottom side of the port in a scalable way.
     * @param right is an EdgeH that describes the right side of the port in a scalable way.
     * @param top is an EdgeV that describes the top side of the port in a scalable way.
     * @return the newly created PrimitivePort.
     */
    public static PrimitivePort newInstance(PrimitiveNode parent, ArcProto[] portArcs,
            String protoName, boolean isSingle,
            int portAngle, int portRange, int portTopology,
            PortCharacteristic characteristic, boolean isolated, boolean negatable,
            EdgeH left, EdgeV bottom, EdgeH right, EdgeV top) {
        return new PrimitivePort(parent, portArcs, protoName, isSingle,
                portAngle, portRange, portTopology, characteristic, isolated, negatable,
                left, bottom, right, top);
    }

    // ------------------------ public methods ------------------------
    /** Method to return PortProtoId of this PrimitivePort.
     * PortProtoId identifies PrimitivePort independently of threads.
     * @return PortProtoId of this PrimtivePort.
     */
    @Override
    public PrimitivePortId getId() {
        return portId;
    }

    /**
     * Method to return the name key of this PrimitivePort.
     * @return the Name key of this PrimitivePort.
     */
    @Override
    public Name getNameKey() {
        return name;
    }

    /**
     * Method to return the name of this PrimitivePort.
     * @return the name of this PrimitivePort.
     */
    @Override
    public String getName() {
        return name.toString();
    }

    /**
     * Method to return the parent NodeProto of this PrimitivePort.
     * @return the parent NodeProto of this PrimitivePort.
     */
    @Override
    public PrimitiveNode getParent() {
        return parent;
    }

    /**
     * Method to get the index of this PrimitivePort.
     * This is a zero-based index of ports on the PrimitiveNode.
     * @return the index of this PrimitivePort.
     */
    @Override
    public int getPortIndex() {
        return portIndex;
    }

    /**
     * Method to return the list of allowable connections on this PrimitivePort.
     * @param allTechs pool of all known technologies
     * @return an array of ArcProtos which can connect to this PrimitivePort.
     */
    public ArcProto[] getConnections(TechPool allTechs) {
        if (parent.getTechnology().isUniversalConnectivityPort(this)) {
            return allTechs.getUnivList();
        }
        return portArcs;
    }

    /**
     * Method to return the list of allowable connections on this PrimitivePort.
     * @return an array of ArcProtos which can connect to this PrimitivePort.
     */
    public ArcProto[] getConnections() {
        if (parent.getTechnology().isUniversalConnectivityPort(this)) {
            return TechPool.getThreadTechPool().getUnivList();
        }
        return portArcs;
    }

    /**
     * Method to return one of allowable connections on this PrimitivePort.
     * @return ArcProto which can connect to this PrimitivePort.
     */
    public ArcProto getConnection() {
        return portArcs[0];
    }

    /**
     * Method to return the base-level port that this PortProto is created from.
     * Since it is a PrimitivePort, it simply returns itself.
     * @return the base-level port that this PortProto is created from (this).
     */
    @Override
    public PrimitivePort getBasePort() {
        return this;
    }

    /**
     * Method to return the left edge of the PrimitivePort as a value that scales with the actual NodeInst.
     * @return an EdgeH object that describes the left edge of the PrimitivePort.
     */
    public EdgeH getLeft() {
        return left;
    }

    /**
     * Method to return the right edge of the PrimitivePort as a value that scales with the actual NodeInst.
     * @return an EdgeH object that describes the right edge of the PrimitivePort.
     */
    public EdgeH getRight() {
        return right;
    }

    /**
     * Method to return the top edge of the PrimitivePort as a value that scales with the actual NodeInst.
     * @return an EdgeV object that describes the top edge of the PrimitivePort.
     */
    public EdgeV getTop() {
        return top;
    }

    /**
     * Method to return the bottom edge of the PrimitivePort as a value that scales with the actual NodeInst.
     * @return an EdgeV object that describes the bottom edge of the PrimitivePort.
     */
    public EdgeV getBottom() {
        return bottom;
    }

    /**
     * Method to return the PortCharacteristic of this PortProto.
     * @return the PortCharacteristic of this PortProto.
     */
    @Override
    public PortCharacteristic getCharacteristic() {
        return characteristic;
    }

    /**
     * Method to determine whether this PrimitivePort is of type Power.
     * This is determined by having the proper PortCharacteristic.
     * @return true if this PrimitivePort is of type Power.
     */
    @Override
    public boolean isPower() {
        return characteristic == PortCharacteristic.PWR;
    }

    /**
     * Method to determine whether this PrimitivePort is of type Ground.
     * This is determined by having the proper PortCharacteristic.
     * @return true if this PrimitivePort is of type Ground.
     */
    @Override
    public boolean isGround() {
        return characteristic == PortCharacteristic.GND;
    }
    /** Set of all well ports */
    private static Set<PortProto> wellPorts = null;

    /**
     * Method to tell whether this portproto is a "well" port on a transistor (for bias connections).
     * @return true if this is a Well port on a transistor.
     */
    public boolean isWellPort() {
        if (wellPorts == null) {
            wellPorts = new HashSet<PortProto>();
            for (Iterator<Technology> it = Technology.getTechnologies(); it.hasNext();) {
                Technology tech = it.next();
                for (Iterator<PrimitiveNode> nIt = tech.getNodes(); nIt.hasNext();) {
                    PrimitiveNode pnp = nIt.next();
                    if (!pnp.getFunction().isFET()) {
                        continue;
                    }
                    for (Iterator<PrimitivePort> pIt = pnp.getPrimitivePorts(); pIt.hasNext();) {
                        PrimitivePort pp = pIt.next();

                        // see if the port connects to active or poly
                        ArcProto[] connections = pp.getConnections();
                        boolean wellPort = false;
                        for (int i = 0; i < connections.length; i++) {
                            ArcProto con = connections[i];
                            if (con.getTechnology() == Generic.tech()) {
                                continue;
                            }
                            if (con.getFunction() == ArcProto.Function.WELL) {
                                wellPort = true;
                                break;
                            }
                        }
                        if (wellPort) {
                            wellPorts.add(pp);
                        }
                    }
                }
            }
        }
        return wellPorts.contains(this);
    }

    /**
     * Method to determine whether this PortProto has a name that suggests Ground.
     * This is determined by either having a name starting with "vss", "gnd", or "ground".
     * @return true if this PortProto has a name that suggests Ground.
     */
    public boolean isNamedGround() {
        String name = TextUtils.canonicString(getName());
        if (name.indexOf("vss") >= 0) {
            return true;
        }
        if (name.indexOf("gnd") >= 0) {
            return true;
        }
        if (name.indexOf("ground") >= 0) {
            return true;
        }
        return false;
    }

    /**
     * Method to return the angle of this PrimitivePort.
     * This is the primary angle that the PrimitivePort faces on the PrimitveNode.
     * @return the angle of this PrimitivePort.
     */
    public int getAngle() {
        return angle;
    }

    /**
     * Method to return the angle range of this PrimitvePort.
     * This is the range about the angle of allowable connections.
     * When this value is 180, then all angles are permissible, since arcs
     * can connect at up to 180 degrees in either direction from the port angle.
     * @return the angle range of this PrimitivePort.
     */
    public int getAngleRange() {
        return angleRange;
    }

    /**
     * Method to get the topology of this PrimitivePort.
     * This is a small integer that is unique among PrimitivePorts on this PrimitiveNode.
     * When two PrimitivePorts have the same topology number, it indicates that these
     * ports are connected.
     * @return the topology of this PrimitvePort.
     */
    public int getTopology() {
        return portTopology;
    }

    /**
     * Method to tell whether this PrimitivePort is isolated.
     * Isolated ports do not electrically connect their arcs.
     * This occurs in the multiple inputs to a schematic gate that all connect to the same port but do not themselves connect.
     * @return true if this PrimitivePort is isolated.
     */
    public boolean isIsolated() {
        return isolated;
    }

    /**
     * Method to tell whether this type of port can be negated.
     * @return true if this type of port can be negated.
     */
    public boolean isNegatable() {
        return negatable;
    }

    /**
     * Method to return true if this PrimitivePort can connect to an arc of a given type.
     * @param arc the ArcProto to test for connectivity.
     * @return true if this PrimitivePort can connect to the arc, false if it can't
     */
    @Override
    public boolean connectsTo(ArcProto arc) {
        for (int i = 0; i < portArcs.length; i++) {
            if (portArcs[i] == arc) {
                return true;
            }
        }
        return parent.getTechnology().isUniversalConnectivityPort(this);
    }

    /**
     * Puts into shape builder s the polygons that describe node "n", given a set of
     * NodeLayer objects to use.
     * This method is overridden by specific PrimitivePorts.
     * @param b shape builder where to put polygons
     * @param n the ImmutableNodeInst that is being described.
     */
    protected void genShape(AbstractShapeBuilder b, ImmutableNodeInst n) {
        b.genShapeOfPort(n, parent, this);
    }

    /**
     * Puts into shape builder s the polygons that describe node "n", given a set of
     * NodeLayer objects to use.
     * This method is overridden by specific PrimitivePorts.
     * @param b shape builder where to put polygons
     * @param n the ImmutableNodeInst that is being described.
     * @param selectPt requests a new location on the port,
     * away from existing arcs, and close to this point.
     * This is useful for "area" ports such as the left side of AND and OR gates.
     * @throws NullPointerException if any argument is null.
     */
    protected void genShape(AbstractShapeBuilder b, ImmutableNodeInst n, Point2D selectPt) {
        if (selectPt == null) {
            throw new NullPointerException();
        }
        genShape(b, n);
    }

    /**
     * Method to compute the color of this PrimitivePort in specified GraphicsPreferences.
     * Combines all arcs that can connect.
     * @param gp specified GraphicsPreferences
     * @return the color to use for this PrimitivePort.
     */
    public Color getPortColor(GraphicsPreferences gp) {
        Technology tech = getParent().getTechnology();
        int numColors = 0;
        int r = 0, g = 0, b = 0;
        for (int i = 0; i < portArcs.length; i++) {
            ArcProto ap = portArcs[i];

            // ignore the generic arcs
            if (ap.getTechnology() != tech) {
                continue;
            }

            // get the arc's color
            Layer layer = ap.getLayer(0);
            EGraphics graphics = gp.getGraphics(layer);
            Color layerCol = graphics.getColor();
            r += layerCol.getRed();
            g += layerCol.getGreen();
            b += layerCol.getBlue();
            numColors++;
        }
        if (numColors == 0) {
            return null;
        }
        return new Color(r / numColors, g / numColors, b / numColors);
    }

    /**
     * Compares PrimtivePorts by their PrimitiveNodes and definition order.
     * @param that the other PrimitivePort.
     * @return a comparison between the PrimitivePorts.
     */
    @Override
    public int compareTo(PrimitivePort that) {
        if (this.parent != that.parent) {
            int cmp = this.parent.compareTo(that.parent);
            if (cmp != 0) {
                return cmp;
            }
        }
        return this.portIndex - that.portIndex;
    }

    /**
     * Returns a printable version of this PrimitivePort.
     * @return a printable version of this PrimitivePort.
     */
    @Override
    public String toString() {
        return "PrimitivePort " + getName();
    }

    void dump(PrintWriter out) {
        double xSize = getParent().getFullRectangle().getWidth();
        double ySize = getParent().getFullRectangle().getHeight();
        out.println("\tport " + getName() + " angle=" + getAngle() + " range=" + getAngleRange() + " topology=" + getTopology() + " " + getCharacteristic());
        out.println("\t\tlm=" + left.getMultiplier() + " la=" + DBMath.round(left.getAdder().getLambda() - left.getMultiplier() * xSize) + " rm=" + right.getMultiplier() + " ra=" + DBMath.round(right.getAdder().getLambda() - right.getMultiplier() * xSize)
                + " bm=" + bottom.getMultiplier() + " ba=" + DBMath.round(bottom.getAdder().getLambda() - bottom.getMultiplier() * ySize) + " tm=" + top.getMultiplier() + " ta=" + DBMath.round(top.getAdder().getLambda() - top.getMultiplier() * ySize));
        out.println("\t\tisolated=" + isolated + " negatable=" + negatable);
        for (ArcProto ap : portArcs) {
            out.println("\t\tportArc " + ap.getName());
        }
    }

    Xml.PrimitivePort makeXml() {
        Xml.PrimitivePort ppd = new Xml.PrimitivePort();
        ppd.name = getName();
        ppd.portAngle = getAngle();
        ppd.portRange = getAngleRange();
        ppd.portTopology = getTopology();

        ppd.lx.k = getLeft().getMultiplier() * 2;
        ppd.lx.addLambda(DBMath.round(getLeft().getAdder().getLambda()));
        ppd.hx.k = getRight().getMultiplier() * 2;
        ppd.hx.addLambda(DBMath.round(getRight().getAdder().getLambda()));
        ppd.ly.k = getBottom().getMultiplier() * 2;
        ppd.ly.addLambda(DBMath.round(getBottom().getAdder().getLambda()));
        ppd.hy.k = getTop().getMultiplier() * 2;
        ppd.hy.addLambda(DBMath.round(getTop().getAdder().getLambda()));

        Technology tech = parent.getTechnology();
        for (ArcProto ap : getConnections()) {
            if (ap.getTechnology() != tech) {
                continue;
            }
            ppd.portArcs.add(ap.getName());
        }
        return ppd;
    }
}
