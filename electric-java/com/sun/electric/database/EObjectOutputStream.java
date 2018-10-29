/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: EObjectOutputStream.java
 * Written by: Dmitry Nadezhin.
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
package com.sun.electric.database;

import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.EDatabase;
import com.sun.electric.database.hierarchy.Nodable;
import com.sun.electric.database.hierarchy.View;
import com.sun.electric.database.id.IdManager;
import com.sun.electric.database.network.Netlist;
import com.sun.electric.database.network.Network;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.variable.ElectricObject;
import com.sun.electric.database.variable.MutableTextDescriptor;
import com.sun.electric.database.variable.TextDescriptor;
import com.sun.electric.database.variable.Variable;
import com.sun.electric.tool.Tool;

import java.awt.Component;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.NotSerializableException;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamException;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.Iterator;

/**
 * ObjectOutputStream which repalaces Electric objects by serializable key objects.
 * On reading key objects are resolved to Electric objects again.
 */
public class EObjectOutputStream extends ObjectOutputStream {

    private final EDatabase database;

    public EObjectOutputStream(OutputStream out, EDatabase database) throws IOException {
        super(out);
        enableReplaceObject(true);
        this.database = database;
    }

    public EDatabase getDatabase() {
        return database;
    }

    public IdManager getIdManager() {
        return database.getIdManager();
    }

    /**
     * This method will allow trusted subclasses of ObjectOutputStream to
     * substitute one object for another during serialization. Replacing
     * objects is disabled until enableReplaceObject is called. The
     * enableReplaceObject method checks that the stream requesting to do
     * replacement can be trusted.  The first occurrence of each object written
     * into the serialization stream is passed to replaceObject.  Subsequent
     * references to the object are replaced by the object returned by the
     * original call to replaceObject.  To ensure that the private state of
     * objects is not unintentionally exposed, only trusted streams may use
     * replaceObject.
     *
     * <p>The ObjectOutputStream.writeObject method takes a parameter of type
     * Object (as opposed to type Serializable) to allow for cases where
     * non-serializable objects are replaced by serializable ones.
     *
     * <p>When a subclass is replacing objects it must insure that either a
     * complementary substitution must be made during deserialization or that
     * the substituted object is compatible with every field where the
     * reference will be stored.  Objects whose type is not a subclass of the
     * type of the field or array element abort the serialization by raising an
     * exception and the object is not be stored.
     *
     * <p>This method is called only once when each object is first
     * encountered.  All subsequent references to the object will be redirected
     * to the new object. This method should return the object to be
     * substituted or the original object.
     *
     * <p>Null can be returned as the object to be substituted, but may cause
     * NullReferenceException in classes that contain references to the
     * original object since they may be expecting an object instead of
     * null.
     *
     * @param	obj the object to be replaced
     * @return	the alternate object that replaced the specified one
     * @throws	IOException Any exception thrown by the underlying
     * 		OutputStream.
     */
    @Override
    protected Object replaceObject(Object obj) throws IOException {
        if (obj instanceof ElectricObject && ((ElectricObject) obj).getDatabase() != database) {
            throw new NotSerializableException("other database");
        }
        if (obj instanceof View) {
            return new EView((View) obj);
        }
        if (obj instanceof Tool) {
            return new ETool((Tool) obj);
        }
        if (obj instanceof Variable.Key) {
            return new EVariableKey((Variable.Key) obj);
        }
        if (obj instanceof TextDescriptor) {
            return new ETextDescriptor((TextDescriptor) obj);
        }
        if (obj instanceof Network) {
            return new ENetwork((Network) obj, database);
        }
        if (obj instanceof Nodable && !(obj instanceof NodeInst)) {
            return new ENodable((Nodable) obj, database);
        }

        if (obj instanceof Component) {
            throw new Error("Found AWT class " + obj.getClass() + " in serialized object");
        }

        return obj;
    }

    private static class EView implements Serializable {

        String abbreviation;

        private EView(View view) {
            abbreviation = view.getAbbreviation();
        }

        private Object readResolve() throws ObjectStreamException {
            View view = View.findView(abbreviation);
            if (view == null) {
                throw new InvalidObjectException("View");
            }
            return view;
        }
    }

    private static class ETool implements Serializable {

        String toolName;

        private ETool(Tool tool) {
            toolName = tool.getName();
        }

        private Object readResolve() throws ObjectStreamException {
            Tool tool = Tool.findTool(toolName);
            if (tool == null) {
                throw new InvalidObjectException("Tool");
            }
            return tool;
        }
    }

    private static class EVariableKey implements Serializable {

        String varName;

        private EVariableKey(Variable.Key varKey) {
            varName = varKey.toString();
        }

        private Object readResolve() throws ObjectStreamException {
            return Variable.newKey(varName);
        }
    }

    private static class ETextDescriptor implements Serializable {

        /** the text descriptor is displayable */
        private boolean display;
        /** the bits of the text descriptor */
        private long bits;
        /** the color of the text descriptor */
        private int colorIndex;
        /** the name of font of text descriptor */
        private String fontName;

        private ETextDescriptor(TextDescriptor td) {
            display = td.isDisplay();
            bits = td.lowLevelGet();
            colorIndex = td.getColorIndex();
            int face = td.getFace();
            if (face != 0) {
                fontName = TextDescriptor.ActiveFont.findActiveFont(face).toString();
            }
        }

        private Object readResolve() throws ObjectStreamException {
            MutableTextDescriptor mtd = new MutableTextDescriptor(bits, colorIndex, display);
            int face = 0;
            if (fontName != null) {
                face = TextDescriptor.ActiveFont.findActiveFont(fontName).getIndex();
            }
            mtd.setFace(face);
            TextDescriptor td = TextDescriptor.newTextDescriptor(mtd);
            if (td == null) {
                throw new InvalidObjectException("TextDescriptor");
            }
            return td;
        }
    }

    private static class ENetwork implements Serializable {

        Cell cell;
        int netIndex;
        Netlist.ShortResistors shortResistors;

        private ENetwork(Network net, EDatabase database) throws NotSerializableException {
            cell = net.getParent();
            if (cell.getDatabase() != database || !cell.isLinked()) {
                throw new NotSerializableException(cell + " not linked");
            }
            netIndex = net.getNetIndex();
            shortResistors = net.getNetlist().getShortResistors();
            if (cell.getNetlist(shortResistors).getNetwork(netIndex) != net) {
                throw new NotSerializableException(net + " not linked");
            }
        }

        private Object readResolve() throws ObjectStreamException {
            Netlist netlist = cell.getNetlist(shortResistors);
            Network net = netlist.getNetwork(netIndex);
            // It is necessary to check that it is the same Netlist
            if (net == null) {
                throw new InvalidObjectException("Network");
            }
            return net;
        }
    }

    private static class ENodable implements Serializable {

        Cell cell;
        String nodableName;

        private ENodable(Nodable no, EDatabase database) throws NotSerializableException {
            cell = no.getParent();
            if (cell.getDatabase() != database || !cell.isLinked()) {
                throw new NotSerializableException(cell + " not linked");
            }
            nodableName = no.getName();
        }

        private Object readResolve() throws ObjectStreamException {
            Netlist netlist = cell.getNetlist();
            Nodable nodable = null;
            for (Iterator<Nodable> it = netlist.getNodables(); it.hasNext();) {
                Nodable no = it.next();
                if (no.getName().equals(nodableName)) {
                    nodable = no;
                    break;
                }
            }
            if (nodable == null) {
                throw new InvalidObjectException("Nodable");
            }
            return nodable;
        }
    }
}
