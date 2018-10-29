/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: ImmutableNodeInst.java
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

import com.sun.electric.database.geometry.EPoint;
import com.sun.electric.database.id.CellId;
import com.sun.electric.database.id.ExportId;
import com.sun.electric.database.id.IdReader;
import com.sun.electric.database.id.IdWriter;
import com.sun.electric.database.id.NodeProtoId;
import com.sun.electric.database.id.PortProtoId;
import com.sun.electric.database.id.PrimitiveNodeId;
import com.sun.electric.database.text.Name;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.variable.TextDescriptor;
import com.sun.electric.database.variable.Variable;
import com.sun.electric.technology.Technology;
import com.sun.electric.technology.technologies.Artwork;
import com.sun.electric.util.TextUtils;
import com.sun.electric.util.collections.ArrayIterator;
import com.sun.electric.util.math.Orientation;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Immutable class ImmutableNodeInst represents a node instance.
 *
 * @promise "requiresColor DBChanger;" for with*(**) | newInstance(**)
 * @promise "requiresColor (DBChanger | DBExaminer | AWT);" for check()
 */
public class ImmutableNodeInst extends ImmutableElectricObject {

    /**
     * Class to access user bits of ImmutableNodeInst.
     */
    public static class Flag {

        private final int mask;

        private Flag(int mask) {
            this.mask = mask;
        }

        /**
         * Returns true if this Flag is set in userBits.
         * @param userBits user bits.
         * @return true if this Flag is set in userBits;
         */
        public boolean is(int userBits) {
            return (userBits & mask) != 0;
        }

        /**
         * Updates this flag in userBits.
         * @param userBits old user bits.
         * @param value new value of flag.
         * @return updates userBits.
         */
        public int set(int userBits, boolean value) {
            return value ? userBits | mask : userBits & ~mask;
        }
    }

    /**
     * Immutable list of ImmutableNodeInsts
     */
    public static interface Iterable extends java.lang.Iterable<ImmutableNodeInst> {

        /**
         * Returns <tt>true</tt> if this list contains no ImmutableNodeInsts.
         * @return <tt>true</tt> if this list contains no ImmutableNodeInsts.
         */
        public boolean isEmpty();

        /**
         * Returns the number of ImmutableNodeInsts in this list.
         * @return the number of ImmutableNodeInsts in this list
         */
        public int size();

        /**
         * Returns the ImmutableNodeInst at the specified position in this list.
         *
         * @param index index of the element to return
         * @return the element at the specified position in this list
         * @throws IndexOutOfBoundsException if the index is out of range
         * (<tt>index &lt; 0 || index &gt;= size()</tt>)
         */
        public ImmutableNodeInst get(int index);

        @Override
        public Iterator<ImmutableNodeInst> iterator();

        /**
         * Searches the nodes for the specified name using the binary
         * search algorithm.
         * @param name the name to be searched.
         * @return index of the search name, if it is contained in the arcs;
         * otherwise, <tt>(-(<i>insertion point</i>) - 1)</tt>. The
         * <i>insertion point</i> is defined as the point at which the
         * ImmutableNodeInst would be inserted into the list: the index of the first
         * element greater than the name, or <tt>size()</tt>, if all
         * elements in the list are less than the specified name. Note
         * that this guarantees that the return value will be &gt;= 0 if
         * and only if the ImmutableNodeInst is found.
         */
        public int searchByName(String name);

        /**
         * Returns ImmutableNodeInst by its nodeId.
         *
         * @param nodeId of ImmutableNodeInst.
         * @return ImmutableNodeInst with given nodeId
         * @throws IndexOutOfBoundsException if nodeId is negative
         */
        public ImmutableNodeInst getNodeById(int nodeId);

        /**
         * Returns sort order index of ImmutableNodeInst by its nodeId.
         *
         * @param nodeId of ImmutableNodeInst.
         * @return sort order index of node
         */
        public int getNodeIndexByNodeId(int nodeId);

        /**
         * Returns true an ImmutableNodeInst with specified nodeId is contained in
         * this CellRevision.
         *
         * @param nodeId specified nodeId.
         * @throws IllegalArgumentException if nodeId is negative
         */
        public boolean hasNodeWithId(int nodeId);

        /**
         * Returns maximum nodeId used by nodes of this CellReversion. Returns -1 if
         * CellRevsison doesn't contatin nodes
         *
         * @return maximum nodeId
         */
        public int getMaxNodeId();

        /**
         * Checks invariant of this CellRevision.
         *
         * @throws AssertionError if invariant is broken.
         */
        public void check();
    }
// -------------------------- constants --------------------------------
//	/** node is not in use */								private static final int DEADN =                     01;
//	/** node has text that is far away */					private static final int NHASFARTEXT =               02;
//	/** if on, draw node expanded */						private static final int NEXPAND =                   04;
//	/** set if node not drawn due to wiping arcs */			private static final int NWIPED =                   010;
//	/** set if node is to be drawn shortened */				private static final int NSHORT =                   020;
//  used by database:                                                                                      0140
//	/** if on, this nodeinst is marked for death */			private static final int KILLN =                   0200;
//	/** nodeinst re-drawing is scheduled */					private static final int REWANTN =                 0400;
//	/** only local nodeinst re-drawing desired */			private static final int RELOCLN =                01000;
//	/** transparent nodeinst re-draw is done */				private static final int RETDONN =                02000;
//	/** opaque nodeinst re-draw is done */					private static final int REODONN =                04000;
//	/** general flag used in spreading and highlighting */	private static final int NODEFLAGBIT =           010000;
//	/** if on, nodeinst wants to be (un)expanded */			private static final int WANTEXP =               020000;
//	/** temporary flag for nodeinst display */				private static final int TEMPFLG =               040000;
    /**
     * set if hard to select
     */
    private static final int HARDSELECTN = 0100000;
    /**
     * set if node only visible inside cell
     */
    private static final int NVISIBLEINSIDE = 040000000;
    /**
     * technology-specific bits for primitives
     */
    private static final int NTECHBITS = 037400000;
    /**
     * right-shift of NTECHBITS
     */
    private static final int NTECHBITSSH = 17;
    /**
     * set if node is locked (can't be changed)
     */
    private static final int NILOCKED = 0100000000;
    private static final int FLAG_BITS = HARDSELECTN | NVISIBLEINSIDE | NILOCKED;
    private static final int HARD_SHAPE_MASK = 0x0001;
//    private static final int HARD_SELECT_MASK = 0x01;
//    private static final int VIS_INSIDE_MASK  = 0x02;
//    private static final int LOCKED_MASK      = 0x04;
//    private static final int DATABASE_FLAGS   = 0x07;
    private final static TextDescriptor traceTd = TextDescriptor.TextType.NODE.getFactoryTextDescriptor().withDisplay(false);
    /**
     * Method to set an ImmutableNodeInst to be hard-to-select.
     * Hard-to-select ImmutableNodeInsts cannot be selected by clicking on them.
     * Instead, the "special select" command must be given.
     */
    public static final Flag HARD_SELECT = new Flag(HARDSELECTN);
    /**
     * Flag to set an ImmutableNodeInst to be visible-inside.
     * An ImmutableNodeInst that is "visible inside" is only drawn when viewing inside of the Cell.
     * It is not visible from outside (meaning from higher-up the hierarchy).
     */
    public static final Flag VIS_INSIDE = new Flag(NVISIBLEINSIDE);
    /**
     * Method to set this ImmutableNodeInst to be locked.
     * Locked ImmutableNodeInsts cannot be modified or deleted.
     */
    public static final Flag LOCKED = new Flag(NILOCKED);
    public final static ImmutableNodeInst[] NULL_ARRAY = {};
    /**
     * id of this NodeInst in parent.
     */
    public final int nodeId;
    /**
     * Prototype id.
     */
    public final NodeProtoId protoId;
    /**
     * name of this ImmutableNodeInst.
     */
    public final Name name;
    /**
     * The text descriptor of name of ImmutableNodeInst.
     */
    public final TextDescriptor nameDescriptor;
    /**
     * Orientation of this ImmutableNodeInst.
     */
    public final Orientation orient;
    /**
     * anchor coordinate of this ImmutableNodeInst.
     */
    public final EPoint anchor;
    /**
     * size of this ImmutableNodeInst.
     */
    public final EPoint size;
    /**
     * Tech specific bits for this ImmutableNodeInsts.
     */
    public final byte techBits;
    /**
     * Text descriptor of prototype name.
     */
    public final TextDescriptor protoDescriptor;
    /**
     * Variables on PortInsts.
     */
    final ImmutablePortInst[] ports;
    public static Comparator<ImmutableNodeInst> NAME_ORDER = new Comparator<ImmutableNodeInst>() {
        @Override
        public int compare(ImmutableNodeInst n1, ImmutableNodeInst n2) {
            return TextUtils.STRING_NUMBER_ORDER.compare(n1.name.toString(), n2.name.toString());
        }
    };

    /**
     * The private constructor of ImmutableNodeInst. Use the factory "newInstance" instead.
     * @param nodeId id of this NodeInst in parent.
     * @param protoId the NodeProtoId of which this is an instance.
     * @param name name of new ImmutableNodeInst.
     * @param nameDescriptor TextDescriptor of name of this ImmutableNodeInst.
     * @param orient Orientation of this ImmutableNodeInst.
     * @param anchor the anchor location of this ImmutableNodeInst.
     * @param size the size of this ImmutableNodeInst.
     * @param flags flag bits for thisImmutableNdoeIsnt.
     * @param techBits tech specific bits of this ImmutableNodeInst.
     * @param protoDescriptor TextDescriptor of prototype name of this ImmutableNodeInst
     * @param vars array of Variables of this ImmutableNodeInst
     * @param ports array of ImmutablePortInsts of this ImmutableNodeInst
     */
    ImmutableNodeInst(int nodeId, NodeProtoId protoId, Name name, TextDescriptor nameDescriptor,
            Orientation orient, EPoint anchor, EPoint size,
            int flags, byte techBits, TextDescriptor protoDescriptor, Variable[] vars, ImmutablePortInst[] ports) {
        super(vars, flags);
        this.nodeId = nodeId;
        this.protoId = protoId;
        this.name = name;
        this.nameDescriptor = nameDescriptor;
        this.orient = orient;
        this.anchor = anchor;
        this.size = size;
        this.techBits = techBits;
        this.protoDescriptor = protoDescriptor;
        this.ports = ports;
    }

    /**
     * Returns new ImmutableNodeInst or ImmutableIconInst object.
     * @param nodeId id of this NodeInst in parent.
     * @param protoId the NodeProtoId of which this is an instance.
     * @param name name of new ImmutableNodeInst.
     * @param nameDescriptor TextDescriptor of name of this ImmutableNodeInst.
     * @param orient Orientation of this ImmutableNodeInst.
     * @param anchor the anchor location of this ImmutableNodeInst.
     * @param size the size of this ImmutableNodeInst.
     * @param flags flag bits for thisImmutableNdoeIsnt.
     * @param techBits tech specific bits of this ImmutableNodeInst.
     * @param protoDescriptor TextDescriptor of prototype name of this ImmutableNodeInst
     * @param vars array of Variables of this ImmutableNodeInst
     * @param params a map of parameter values of this ImmutableNodeInst
     * @return new ImmutableNodeInst object.
     */
    static ImmutableNodeInst newInstance(int nodeId, NodeProtoId protoId, Name name, TextDescriptor nameDescriptor,
            Orientation orient, EPoint anchor, EPoint size,
            int flags, byte techBits, TextDescriptor protoDescriptor,
            Variable[] vars, ImmutablePortInst[] ports, Variable[] params) {
        if (protoId instanceof CellId && ((CellId) protoId).isIcon()) {
            return new ImmutableIconInst(nodeId, protoId, name, nameDescriptor,
                    orient, anchor, size, flags, techBits, protoDescriptor,
                    vars, ports, params);
        } else {
            assert params == Variable.NULL_ARRAY;
            return new ImmutableNodeInst(nodeId, protoId, name, nameDescriptor,
                    orient, anchor, size, flags, techBits, protoDescriptor,
                    vars, ports);
        }
    }

    /**
     * Returns new ImmutableNodeInst or ImmutableIconInst object.
     * @param nodeId id of this NodeInst in parent.
     * @param protoId the NodeProtoId of which this is an instance.
     * @param name name of new ImmutableNodeInst.
     * @param nameDescriptor TextDescriptor of name of this ImmutableNodeInst.
     * @param orient Orientation of this ImmutableNodeInst.
     * @param anchor the anchor location of this ImmutableNodeInst.
     * @param size the size of this ImmutableNodeInst.
     * @param flags flags of this NodeInst.
     * @param techBits bits associated to different technologies
     * @param protoDescriptor TextDescriptor of name of this ImmutableNodeInst
     * @return new ImmutableNodeInst object.
     * @throws NullPointerException if protoId, name, orient or anchor is null.
     * @throws IllegalArgumentException if nodeId or size is bad.
     */
    public static ImmutableNodeInst newInstance(int nodeId, NodeProtoId protoId, Name name, TextDescriptor nameDescriptor,
            Orientation orient, EPoint anchor, EPoint size,
            int flags, int techBits, TextDescriptor protoDescriptor) {
        if (nodeId < 0) {
            throw new IllegalArgumentException("nodeId");
        }
        if (protoId == null) {
            throw new NullPointerException("protoId");
        }
        if (name == null) {
            throw new NullPointerException("name");
        }
        boolean isIcon = protoId instanceof CellId && ((CellId) protoId).isIcon();
        if (!name.isValid() || name.hasEmptySubnames()
                || name.isBus() && (name.isTempname() || !isIcon)) {
            throw new IllegalArgumentException("bad name: " + name);
        }
        if (name.hasDuplicates()) {
            throw new IllegalArgumentException("name");
        }
        if (nameDescriptor != null) {
            nameDescriptor = nameDescriptor.withoutParam();
        }
        if (orient == null) {
            throw new NullPointerException("orient");
        }
        if (anchor == null) {
            throw new NullPointerException("anchor");
        }
        if (size == null) {
            throw new NullPointerException("size");
        }
//        if (size.getGridX() < 0 || size.getGridY() < 0) throw new IllegalArgumentException("size");
        if (protoId instanceof CellId) {
            size = EPoint.ORIGIN;
        }
        if (isCellCenter(protoId)) {
            orient = Orientation.IDENT;
            anchor = EPoint.ORIGIN;
            size = EPoint.ORIGIN;
        }
        flags &= FLAG_BITS;

        techBits &= NTECHBITS >> NTECHBITSSH;
        if (protoDescriptor != null) {
            protoDescriptor = protoDescriptor.withDisplayWithoutParam();
        }
        return newInstance(nodeId, protoId, name, nameDescriptor,
                orient, anchor, size, flags, (byte) techBits, protoDescriptor,
                Variable.NULL_ARRAY, ImmutablePortInst.NULL_ARRAY, Variable.NULL_ARRAY);
    }

//	/**
//	 * Returns ImmutableNodeInst which differs from this ImmutableNodeInst by protoId.
//	 * @param protoId node protoId.
//	 * @return ImmutableNodeInst which differs from this ImmutableNodeInst by protoId.
//	 * @throws ArrayIndexOutOfBoundsException if protoId is negative.
//	 */
//	public ImmutableNodeInst withProto(int protoId) {
//		if (this.protoId == protoId) return this;
//		if (protoId < 0) throw new ArrayIndexOutOfBoundsException(protoId);
//		return new ImmutableNodeInst(protoId, this.name, this.duplicate, this.nameDescriptor,
//                this.orient, this.anchor, this.width, this.height, this.userBits, this.protoDescriptor);
//	}
    /**
     * Returns ImmutableNodeInst which differs from this ImmutableNodeInst by name.
     * @param name node name key.
     * @return ImmutableNodeInst which differs from this ImmutableNodeInst by name.
     * @throws NullPointerException if name is null.
     */
    public ImmutableNodeInst withName(Name name) {
        if (this.name.toString().equals(name.toString())) {
            return this;
        }
        if (name == null) {
            throw new NullPointerException("name");
        }
        if (!name.isValid() || name.hasEmptySubnames()
                || name.isBus() && (name.isTempname() || !(this instanceof ImmutableIconInst))) {
            throw new IllegalArgumentException("name");
        }
        if (name.hasDuplicates()) {
            throw new IllegalArgumentException("name");
        }
        return newInstance(this.nodeId, this.protoId, name, this.nameDescriptor,
                this.orient, this.anchor, this.size, this.flags, this.techBits, this.protoDescriptor,
                getVars(), this.ports, getDefinedParams());
    }

    /**
     * Returns ImmutableNodeInst which differs from this ImmutableNodeInst by name descriptor.
     * @param nameDescriptor TextDescriptor of name
     * @return ImmutableNodeInst which differs from this ImmutableNodeInst by name descriptor.
     */
    public ImmutableNodeInst withNameDescriptor(TextDescriptor nameDescriptor) {
        if (nameDescriptor != null) {
            nameDescriptor = nameDescriptor.withoutParam();
        }
        if (this.nameDescriptor == nameDescriptor) {
            return this;
        }
        return newInstance(this.nodeId, this.protoId, this.name, nameDescriptor,
                this.orient, this.anchor, this.size, this.flags, this.techBits, this.protoDescriptor,
                getVars(), this.ports, getDefinedParams());
    }

    /**
     * Returns ImmutableNodeInst which differs from this ImmutableNodeInst by orientation.
     * @param orient Orientation.
     * @return ImmutableNodeInst which differs from this ImmutableNodeInst by orientation.
     * @throws NullPointerException if orient is null.
     */
    public ImmutableNodeInst withOrient(Orientation orient) {
        if (this.orient == orient) {
            return this;
        }
        if (orient == null) {
            throw new NullPointerException("orient");
        }
        if (getTrace() != null) {
            throw new IllegalArgumentException();
        }
        if (isCellCenter(protoId)) {
            return this;
        }
        return newInstance(this.nodeId, this.protoId, this.name, this.nameDescriptor,
                orient, this.anchor, this.size, this.flags, this.techBits, this.protoDescriptor,
                getVars(), this.ports, getDefinedParams());
    }

    /**
     * Returns ImmutableNodeInst which differs from this ImmutableNodeInst by anchor point.
     * @param anchor node anchor point.
     * @return ImmutableNodeInst which differs from this ImmutableNodeInst by anchor point.
     * @throws NullPointerException if anchor is null.
     */
    public ImmutableNodeInst withAnchor(EPoint anchor) {
        if (this.anchor.equals(anchor)) {
            return this;
        }
        if (anchor == null) {
            throw new NullPointerException("anchor");
        }
        if (isCellCenter(protoId)) {
            return this;
        }
        return newInstance(this.nodeId, this.protoId, this.name, this.nameDescriptor,
                this.orient, anchor, this.size, this.flags, this.techBits, this.protoDescriptor,
                getVars(), this.ports, getDefinedParams());
    }

    /**
     * Returns ImmutableNodeInst which differs from this ImmutableNodeInst by size.
     * @param size a point with x as width and y as height.
     * @return ImmutableNodeInst which differs from this ImmutableNodeInst by size.
     * @throws IllegalArgumentException if width or height is negative.
     */
    public ImmutableNodeInst withSize(EPoint size) {
        if (this.size.equals(size)) {
            return this;
        }
        if (size == null) {
            throw new NullPointerException("size");
        }
//        if (size.getGridX() < 0 || size.getGridY() < 0) throw new IllegalArgumentException("size is " + size);
        if (isCellCenter(protoId)) {
            return this;
        }
        if (protoId instanceof CellId) {
            return this;
        }
        if (getTrace() != null) {
            return this;
        }
        return newInstance(this.nodeId, this.protoId, this.name, this.nameDescriptor,
                this.orient, this.anchor, size, this.flags, this.techBits, this.protoDescriptor,
                getVars(), this.ports, getDefinedParams());
    }

    /**
     * Returns ImmutableNodeInst which differs from this ImmutableNodeInst by flag bits.
     * @param flags flag bits defined by ImmutableNodeInst.Flag.
     * @return ImmutableNodeInst which differs from this ImmutableNodeInst by flag bit.
     */
    private ImmutableNodeInst withFlags(int flags) {
        flags = updateHardShape(flags & FLAG_BITS, getVars());
        if (this.flags == flags) {
            return this;
        }
        return newInstance(this.nodeId, this.protoId, this.name, this.nameDescriptor,
                this.orient, this.anchor, this.size, flags, this.techBits, this.protoDescriptor,
                getVars(), this.ports, getDefinedParams());
    }

    /**
     * Returns ImmutableNodeInst which differs from this ImmutableNodeInst by state bits.
     * State bits are flags and tech-specific bits.
     * @param d another ImmutableNodeInst where to take state bits.
     * @return ImmutableNodeInst which differs from this ImmutableNodeInst by state bit.
     */
    public ImmutableNodeInst withStateBits(ImmutableNodeInst d) {
        return withFlags(d.flags).withTechSpecific(d.techBits);
    }

    /**
     * Returns ImmutableNodeInst which differs from this ImmutableNodeInst by flag bit.
     * @param flag Flag selector.
     * @param value new value of flag.
     * @return ImmutableNodeInst which differs from this ImmutableNodeInst by flag bit.
     */
    public ImmutableNodeInst withFlag(Flag flag, boolean value) {
        return withFlags(flag.set(this.flags, value));
    }

    /**
     * Returns ImmutableNodeInst which differs from this ImmutableNodeInst by tech specific bits.
     * This is mostly used by the Schematics technology which allows variations
     * on a NodeInst to be stored.
     * For example, the Transistor primitive uses these bits to distinguish nMOS, pMOS, etc.
     * @param techBits the Technology-specific value to store on this NodeInst.
     * @return ImmutableNodeInst which differs from this ImmutableNodeInst by tech bits.
     */
    public ImmutableNodeInst withTechSpecific(int techBits) {
        techBits &= NTECHBITS >> NTECHBITSSH;
        if (this.techBits == techBits) {
            return this;
        }
        return newInstance(this.nodeId, this.protoId, this.name, this.nameDescriptor,
                this.orient, this.anchor, this.size, this.flags, (byte) techBits, this.protoDescriptor,
                getVars(), this.ports, getDefinedParams());
    }

    /**
     * Returns ImmutableNodeInst which differs from this ImmutableNodeInst by proto descriptor.
     * @param protoDescriptor TextDescriptor of proto
     * @return ImmutableNodeInst which differs from this ImmutableNodeInst by proto descriptor.
     */
    public ImmutableNodeInst withProtoDescriptor(TextDescriptor protoDescriptor) {
        if (protoDescriptor != null) {
            protoDescriptor = protoDescriptor.withDisplayWithoutParam();
        }
        if (this.protoDescriptor == protoDescriptor) {
            return this;
        }
        return newInstance(this.nodeId, this.protoId, this.name, this.nameDescriptor,
                this.orient, this.anchor, this.size, this.flags, this.techBits, protoDescriptor,
                getVars(), this.ports, getDefinedParams());
    }

    /**
     * Returns ImmutableNodeInst which differs from this ImmutableNodeInst by additional Variable.
     * If this ImmutableNideInst has Variable with the same key as new, the old variable will not be in new
     * ImmutableNodeInst.
     * @param var additional Variable.
     * @return ImmutableNodeInst with additional Variable.
     * @throws NullPointerException if var is null
     */
    public ImmutableNodeInst withVariable(Variable var) {
        if (var.getKey() == NodeInst.TRACE) {
            throw new IllegalArgumentException();
        }
        Variable[] vars = arrayWithVariable(var.withParam(false).withInherit(false));
        if (this.getVars() == vars) {
            return this;
        }
//        EPoint size = this.size;
//        if (var.getKey() == NodeInst.TRACE
//                && protoId instanceof PrimitiveNodeId && !isCellCenter(protoId)) {
//            Object value = var.getObject();
//            if (value instanceof EPoint[]) {
//                EPoint newSize = calcTraceSize((EPoint[]) value);
//                if (!newSize.equals(size)) {
//                    size = newSize;
//                }
//            }
//        }
        int flags = updateHardShape(this.flags, vars);
        return newInstance(this.nodeId, this.protoId, this.name, this.nameDescriptor,
                this.orient, this.anchor, this.size, flags, this.techBits, this.protoDescriptor,
                vars, this.ports, getDefinedParams());
    }

    /**
     * Returns ImmutableNodeInst which differs from this ImmutableNodeInst by removing Variable
     * with the specified key. Returns this ImmutableNodeInst if it doesn't contain variable with the specified key.
     * @param key Variable Key to remove.
     * @return ImmutableNodeInst without Variable with the specified key.
     * @throws NullPointerException if key is null
     */
    public ImmutableNodeInst withoutVariable(Variable.Key key) {
        Variable[] vars = arrayWithoutVariable(key);
        if (this.getVars() == vars) {
            return this;
        }
        int flags = updateHardShape(this.flags, vars);
        return newInstance(this.nodeId, this.protoId, this.name, this.nameDescriptor,
                this.orient, this.anchor, this.size, flags, this.techBits, this.protoDescriptor,
                vars, this.ports, getDefinedParams());
    }

    /**
     * Returns true if this ImmutableNodeInst doesn't have customized contact variables.
     * @return true if this ImmutableNodeInst doesn't have customized contact variables.
     */
    public boolean isEasyShape() {
        return (flags & HARD_SHAPE_MASK) == 0;
    }

    private static int updateHardShape(int flags, Variable[] vars) {
        boolean hasHardVars =
                searchVar(vars, Technology.NodeLayer.CUT_SPACING) >= 0
                || searchVar(vars, Technology.NodeLayer.CUT_ALIGNMENT) >= 0
                || searchVar(vars, Technology.NodeLayer.METAL_OFFSETS) >= 0
                || searchVar(vars, Technology.NodeLayer.CARBON_NANOTUBE_COUNT) >= 0
                || searchVar(vars, Technology.NodeLayer.CARBON_NANOTUBE_PITCH) >= 0;
        return hasHardVars ? flags | HARD_SHAPE_MASK : flags & ~HARD_SHAPE_MASK;
    }

    /**
     * Returns ImmutableNodeInst which differs from this ImmutableNodeInst by renamed Ids.
     * @param idMapper a map from old Ids to new Ids.
     * @return ImmutableNodeInst with renamed Ids.
     */
    ImmutableNodeInst withRenamedIds(IdMapper idMapper) {
        Variable[] vars = arrayWithRenamedIds(idMapper);
        NodeProtoId protoId = this.protoId;
        ImmutablePortInst[] ports = portsWithRenamedIds(idMapper);
        if (protoId instanceof CellId) {
            protoId = idMapper.get((CellId) protoId);
        }
        if (getVars() == vars && this.protoId == protoId && this.ports == ports) {
            return this;
        }
        return newInstance(this.nodeId, protoId, this.name, this.nameDescriptor,
                this.orient, this.anchor, this.size, this.flags, this.techBits, this.protoDescriptor,
                vars, ports, arrayWithRenamedIds(getDefinedParams(), idMapper));
    }

    /**
     * Returns array of ImmutablePortInst which differs from array of this ImmutableNodeInst by renamed Ids.
     * Returns array of this ImmutableNodeInst if it doesn't contain renamed Ids.
     * @param idMapper a map from old Ids to new Ids.
     * @return array of ImmutablePortInst with renamed Ids.
     */
    private ImmutablePortInst[] portsWithRenamedIds(IdMapper idMapper) {
        if (ports.length == 0) {
            assert ports == ImmutablePortInst.NULL_ARRAY;
            return ports;
        }
        if (protoId instanceof CellId) {
            boolean chronIndexChanged = false;
            int maxChronIndex = -1;
            CellId subCellId = (CellId) protoId;
            for (int chronIndex = 0; chronIndex < ports.length; chronIndex++) {
                ImmutablePortInst oldPort = ports[chronIndex];
                if (oldPort == ImmutablePortInst.EMPTY) {
                    continue;
                }
                ExportId oldExportId = subCellId.getPortId(chronIndex);
                assert oldExportId.chronIndex == chronIndex;
                ExportId newExportId = idMapper.get(oldExportId);
                maxChronIndex = Math.max(maxChronIndex, newExportId.chronIndex);
                if (newExportId.chronIndex != chronIndex) {
                    chronIndexChanged = true;
                }
            }
            if (chronIndexChanged) {
                ImmutablePortInst[] newPorts = new ImmutablePortInst[maxChronIndex + 1];
                assert newPorts.length > 0;
                Arrays.fill(newPorts, ImmutablePortInst.EMPTY);
                for (int chronIndex = 0; chronIndex < ports.length; chronIndex++) {
                    ImmutablePortInst oldPort = ports[chronIndex];
                    if (oldPort == ImmutablePortInst.EMPTY) {
                        continue;
                    }
                    newPorts[idMapper.get(subCellId.getPortId(chronIndex)).chronIndex] = oldPort.withRenamedIds(idMapper);
                }
                return newPorts;
            }
        }

        ImmutablePortInst[] newPorts = null;
        for (int i = 0; i < ports.length; i++) {
            ImmutablePortInst oldPort = ports[i];
            ImmutablePortInst newPort = oldPort.withRenamedIds(idMapper);
            if (newPort != oldPort && newPorts == null) {
                newPorts = new ImmutablePortInst[ports.length];
                System.arraycopy(ports, 0, newPorts, 0, i);
            }
            if (newPorts != null) {
                newPorts[i] = newPort;
            }
        }
        return newPorts != null ? newPorts : ports;
    }

    /**
     * Returns ImmutableNodeInst which differs from this ImmutableNodeInst by additional Variable on PortInst.
     * If this ImmutableNideInst has Variable on PortInst with the same key as new, the old variable will not be in new
     * ImmutableNodeInst.
     * @param portProtoId PortProtoId of port instance.
     * @return ImmutableNodeInst with additional Variable.
     * @throws NullPointerException if var is null
     */
    public ImmutableNodeInst withPortInst(PortProtoId portProtoId, ImmutablePortInst portInst) {
        if (portProtoId.getParentId() != protoId) {
            throw new IllegalArgumentException("portProtoId");
        }
        int portChronIndex = portProtoId.getChronIndex();
        ImmutablePortInst[] newPorts;
        if (portChronIndex < ports.length) {
            if (ports[portChronIndex] == portInst) {
                return this;
            }
            if (portInst == ImmutablePortInst.EMPTY && portChronIndex == ports.length - 1) {
                int newLength = ports.length - 1;
                while (newLength > 0 && ports[newLength - 1] == ImmutablePortInst.EMPTY) {
                    newLength--;
                }
                if (newLength > 0) {
                    newPorts = new ImmutablePortInst[newLength];
                    System.arraycopy(ports, 0, newPorts, 0, newLength);
                } else {
                    newPorts = ImmutablePortInst.NULL_ARRAY;
                }
            } else {
                newPorts = ports.clone();
                newPorts[portChronIndex] = portInst;
            }
        } else {
            if (portInst == ImmutablePortInst.EMPTY) {
                return this;
            }
            newPorts = new ImmutablePortInst[portChronIndex + 1];
            System.arraycopy(ports, 0, newPorts, 0, ports.length);
            Arrays.fill(newPorts, ports.length, portChronIndex, ImmutablePortInst.EMPTY);
            newPorts[portChronIndex] = portInst;
        }
        return newInstance(this.nodeId, this.protoId, this.name, this.nameDescriptor,
                this.orient, this.anchor, this.size, this.flags, this.techBits, this.protoDescriptor,
                getVars(), newPorts, getDefinedParams());
    }

    /**
     * Returns true if this ImmutableNodeInst was named by user.
     * @return true if this ImmutableNodeInst was named by user.
     */
    public boolean isUsernamed() {
        return !name.isTempname();
    }

    /**
     * Method to tell whether this NodeInst is a cell instance.
     * @return true if this NodeInst is a cell instance, false if it is a primitive
     */
    public boolean isCellInstance() {
        return protoId instanceof CellId;
    }

    /**
     * Returns ImmutablePortInst of this ImmutableNodeInst with the specified PortProtoId.
     * @param portProtoId PortProtoId of port instance.
     * @return ImmutablePortInst of this ImmutableNodeInst with the specified PortProtoId.
     * @throws NullPointerException if portProtoId is null.
     * @throws IlleagalArgumentException if parent of portProtoId is not protoId of this ImmutableNodeInst.
     */
    public ImmutablePortInst getPortInst(PortProtoId portProtoId) {
        if (portProtoId.getParentId() != protoId) {
            throw new IllegalArgumentException("portProtoId");
        }
        int portChronIndex = portProtoId.getChronIndex();
        return portChronIndex < ports.length ? ports[portChronIndex] : ImmutablePortInst.EMPTY;
    }

    /**
     * Returns an Iterator over all PortProtoIds such that the correspondent PortInst on this
     * ImmutablePortInst has variables.
     * @return an Iterator over all PortProtoIds with variables.
     * @throws NullPointerException if portProtoId is null.
     * @throws IlleagalArgumentException if parent of portProtoId is not protoId of this ImmutableNodeInst.
     */
    public Iterator<PortProtoId> getPortsWithVariables() {
        if (ports.length == 0) {
            Iterator<PortProtoId> emptyIterator = ArrayIterator.emptyIterator();
            return emptyIterator;
        }
        return new PortInstIterator();
    }

    private class PortInstIterator implements Iterator<PortProtoId> {

        int chronIndex;
        PortProtoId next;

        PortInstIterator() {
            getNext();
        }

        @Override
        public boolean hasNext() {
            return next != null;
        }

        @Override
        public PortProtoId next() {
            PortProtoId result = next;
            if (result == null) {
                throw new NoSuchElementException();
            }
            getNext();
            return result;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

        private void getNext() {
            PortProtoId next = null;
            for (; chronIndex < ports.length; chronIndex++) {
                if (ports[chronIndex] != ImmutablePortInst.EMPTY) {
                    next = protoId.getPortId(chronIndex++);
                    break;
                }
            }
            this.next = next;
        }
    }

    /**
     * Returns true if this ImmutableNodeInst has variables on port instances.
     * @return true if this ImmutableNodeInst has variables on port instances.
     */
    public boolean hasPortInstVariables() {
        return ports.length > 0;
    }

//    /**
//     * Returns flags of this ImmutableNodeInst.
//     * This flags are defined by ImmutableNodeInst.Flag .
//     * @return flags of this ImmutableNodeInst.
//     */
//    public int getFlags() { return flags; }
    /**
     * Tests specific flag is set on this ImmutableNodeInst.
     * @param flag flag selector.
     * @return true if specific flag is set,
     */
    public boolean is(Flag flag) {
        return flag.is(flags);
    }

//	/**
//	 * Method to return the Technology-specific value on this ImmutableNodeInst.
//	 * This is mostly used by the Schematics technology which allows variations
//	 * on a NodeInst to be stored.
//	 * For example, the Transistor primitive uses these bits to distinguish nMOS, pMOS, etc.
//	 * @return the Technology-specific value on this ImmutableNodeInst.
//	 */
//	public byte getTechSpecific() { return techBits; }
    Variable[] getDefinedParams() {
        return Variable.NULL_ARRAY;
    }

    /**
     * Writes this ImmutableNodeInst to IdWriter.
     * @param writer where to write.
     */
    @Override
    void write(IdWriter writer) throws IOException {
        writer.writeNodeId(nodeId);
        writer.writeNodeProtoId(protoId);
        writer.writeNameKey(name);
        writer.writeTextDescriptor(nameDescriptor);
        writer.writeOrientation(orient);
        writer.writePoint(anchor);
        writer.writePoint(size);
        writer.writeInt(flags);
        writer.writeByte(techBits);
        writer.writeTextDescriptor(protoDescriptor);
        for (int i = ports.length - 1; i >= 0; i--) {
            if (ports[i] == ImmutablePortInst.EMPTY) {
                continue;
            }
            writer.writeInt(i);
            ports[i].writeVars(writer);
        }
        writer.writeInt(-1);
        super.write(writer);
    }

    /**
     * Reads ImmutableNodeInst from SnapshotReader.
     * @param reader where to read.
     */
    static ImmutableNodeInst read(IdReader reader) throws IOException {
        int nodeId = reader.readNodeId();
        NodeProtoId protoId = reader.readNodeProtoId();
        Name name = reader.readNameKey();
        TextDescriptor nameDescriptor = reader.readTextDescriptor();
        Orientation orient = reader.readOrientation();
        EPoint anchor = reader.readPoint();
        EPoint size = reader.readPoint();
        int flags = reader.readInt();
        byte techBits = reader.readByte();
        TextDescriptor protoDescriptor = reader.readTextDescriptor();
        ImmutablePortInst[] ports = ImmutablePortInst.NULL_ARRAY;
        for (;;) {
            int i = reader.readInt();
            if (i == -1) {
                break;
            }
            if (i >= ports.length) {
                ImmutablePortInst[] newPorts = new ImmutablePortInst[i + 1];
                System.arraycopy(ports, 0, newPorts, 0, ports.length);
                Arrays.fill(newPorts, ports.length, newPorts.length, ImmutablePortInst.EMPTY);
                ports = newPorts;
            }
            ports[i] = ImmutablePortInst.read(reader);
        }
        boolean hasVars = reader.readBoolean();
        Variable[] vars = hasVars ? readVars(reader) : Variable.NULL_ARRAY;
        Variable[] params = Variable.NULL_ARRAY;
        if (protoId instanceof CellId && ((CellId) protoId).isIcon()) {
            params = readVars(reader);
        }
        return newInstance(nodeId, protoId, name, nameDescriptor, orient, anchor, size,
                flags, techBits, protoDescriptor,
                vars, ports, params);
    }

    /**
     * Return a hash code value for fields of this object.
     * Variables of objects are not compared
     */
    @Override
    public int hashCodeExceptVariables() {
        return nodeId;
    }

    /**
     * Indicates whether fields of other ImmutableElectricObject are equal to fields of this object.
     * Variables of objects are not compared.
     * @param o other ImmutableElectricObject.
     * @return true if fields of objects are equal.
     */
    @Override
    public boolean equalsExceptVariables(ImmutableElectricObject o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ImmutableNodeInst)) {
            return false;
        }
        ImmutableNodeInst that = (ImmutableNodeInst) o;
        return this.nodeId == that.nodeId && this.protoId == that.protoId
                && this.name == that.name && this.nameDescriptor == that.nameDescriptor
                && this.orient == that.orient && this.anchor.equals(that.anchor) && this.size.equals(that.size)
                && this.flags == that.flags && this.techBits == that.techBits
                && this.protoDescriptor == that.protoDescriptor;
    }

    /**
     * Checks invariant of this ImmutableNodeInst.
     * @throws AssertionError if invariant is broken.
     */
    public void check() {
        super.check(false);
        assert nodeId >= 0;
        assert protoId != null;
        boolean isIcon = protoId instanceof CellId && ((CellId) protoId).isIcon();
        assert getClass() == (isIcon ? ImmutableIconInst.class : ImmutableNodeInst.class);
        assert name != null;
        assert name.isValid() && !name.hasEmptySubnames();
        assert !name.isBus() || isIcon && !name.isTempname();
        assert !name.hasDuplicates();
        if (nameDescriptor != null) {
            assert /*nameDescriptor.isDisplay() &&*/ !nameDescriptor.isParam();
        }
        assert orient != null;
        assert anchor != null;
        assert size != null;
        Variable traceVar = getVar(NodeInst.TRACE);
        if (traceVar != null) {
            assert protoId instanceof PrimitiveNodeId && !isCellCenter(protoId);
            assert orient == Orientation.IDENT;
            assert calcTraceSize((EPoint[]) traceVar.getObject()).equals(size);
        }
        assert (flags & ~(FLAG_BITS | HARD_SHAPE_MASK)) == 0;
        assert isEasyShape()
                == (getVar(Technology.NodeLayer.CUT_SPACING) == null
                && getVar(Technology.NodeLayer.CUT_ALIGNMENT) == null
                && getVar(Technology.NodeLayer.METAL_OFFSETS) == null
                && getVar(Technology.NodeLayer.CARBON_NANOTUBE_COUNT) == null
                && getVar(Technology.NodeLayer.METAL_OFFSETS) == null);
        assert (techBits & ~(NTECHBITS >> NTECHBITSSH)) == 0;
        if (protoDescriptor != null) {
            assert protoDescriptor.isDisplay() && !protoDescriptor.isParam();
        }
        if (protoId instanceof CellId) {
            assert size == EPoint.ORIGIN;
        }
        if (isCellCenter(protoId)) {
            assert orient == Orientation.IDENT && anchor == EPoint.ORIGIN && size == EPoint.ORIGIN;
        }
        for (int i = 0; i < ports.length; i++) {
            ImmutablePortInst portInst = ports[i];
            if (portInst.getNumVariables() != 0) {
                portInst.check();
            } else {
                assert portInst == ImmutablePortInst.EMPTY;
            }
        }
        if (ports.length > 0) {
            assert ports[ports.length - 1].getNumVariables() > 0;
        }
    }

    public static boolean isCellCenter(NodeProtoId protoId) {
        if (!(protoId instanceof PrimitiveNodeId)) {
            return false;
        }
        return ((PrimitiveNodeId) protoId).fullName.equals("generic:Facet-Center");
    }

    /**
     * Returns ELIB user bits of this ImmutableNodeInst in ELIB.
     * @return ELIB user bits of this ImmutableNodeInst.
     */
    public int getElibBits() {
        return flags | (techBits << NTECHBITSSH);
    }

    /**
     * Get flag bits from ELIB user bits.
     * @param elibBits ELIB user bits.
     * @return flag bits.
     */
    public static int flagsFromElib(int elibBits) {
        return elibBits & FLAG_BITS;
    }

    /**
     * Get tech specific bits from ELIB user bits.
     * @param elibBits ELIB user bits.
     * @return tech specific bits.
     */
    public static int techSpecificFromElib(int elibBits) {
        return (elibBits & NTECHBITS) >> NTECHBITSSH;
    }

    /**
     * Method to return the "outline" information on this ImmutableNodeInst.
     * Outline information is a set of coordinate points that further
     * refines the NodeInst description. It is typically used in
     * Artwork primitives to give them a precise shape. It is also
     * used by pure-layer nodes in all layout technologies to allow
     * them to take any shape. It is even used by many MOS
     * transistors to allow a precise gate path to be specified.
     * @return an array of EPoint in database coordinates.
     */
    public EPoint[] getTrace() {
        Variable var = getVar(NodeInst.TRACE);
        if (var == null) {
            return null;
        }
        Object obj = var.getObject();
        if (obj instanceof EPoint[]) {
            return (EPoint[]) obj;
        }
        return null;
    }

    /**
     * Method to set the "outline" information on this NodeInst.
     * Outline information is a set of coordinate points that further
     * refines the NodeInst description. It is typically used in
     * Artwork primitives to give them a precise shape. It is also
     * used by pure-layer nodes in all layout technologies to allow
     * them to take any shape. It is even used by many MOS
     * transistors to allow a precise gate path to be specified.
     * @param points an array of EPoint values in database coordinates.
     * These are not relative to the center of the node, but are actual coordinates of the outline.
     * @param pAnchor if not-nul then coordinate are relative to this point.
     */
    public ImmutableNodeInst withTrace(EPoint[] points, EPoint pAnchor) {
        long lX = points[0].getGridX();
        long hX = lX;
        long lY = points[0].getGridY();
        long hY = lY;
        for (int i = 1; i < points.length; i++) {
            if (points[i] == null) {
                continue;
            }
            long x = points[i].getGridX();
            lX = Math.min(lX, x);
            hX = Math.max(hX, x);
            long y = points[i].getGridY();
            lY = Math.min(lY, y);
            hY = Math.max(hY, y);
        }
        long mX = lX + hX;
        long mY = lY + hY;
        if (pAnchor != null) {
            mX += pAnchor.getGridX() * 2;
            mY += pAnchor.getGridY() * 2;
        }
        if ((mX & 1) != 0) {
            mX += (mX >> 1) & 1;
        }
        if ((mY & 1) != 0) {
            mY += (mY >> 1) & 1;
        }
        mX >>= 1;
        mY >>= 1;
        EPoint newAnchor;
        if (mX == anchor.getGridX() && mY == anchor.getGridY()) {
            newAnchor = anchor;
        } else {
            newAnchor = EPoint.fromGrid(mX, mY);
        }
        long dx = -mX;
        long dy = -mY;
        if (pAnchor != null) {
            dx += pAnchor.getGridX();
            dy += pAnchor.getGridY();
        }
        EPoint[] newPoints = new EPoint[points.length];
        if (dx == 0 && dy == 0) {
            System.arraycopy(points, 0, newPoints, 0, points.length);
        } else {
            for (int i = 0; i < newPoints.length; i++) {
                if (points[i] != null) {
                    newPoints[i] = EPoint.fromGrid(points[i].getGridX() + dx, points[i].getGridY() + dy);
                }
            }
        }
        Variable var = Variable.newInstance(NodeInst.TRACE, newPoints, traceTd);
        Variable oldVar = getVar(NodeInst.TRACE);
        if (var.equals(oldVar)) {
            var = oldVar;
        }
        if (newAnchor == anchor && var == oldVar) {
            return this;
        }
        Variable[] vars = arrayWithVariable(Variable.newInstance(NodeInst.TRACE, newPoints, traceTd));
        EPoint newSize = calcTraceSize(newPoints);
        if (newSize.equals(this.size)) {
            newSize = this.size;
        }
        return newInstance(this.nodeId, this.protoId, this.name, this.nameDescriptor,
                Orientation.IDENT, newAnchor, newSize, this.flags, this.techBits, this.protoDescriptor,
                vars, this.ports, getDefinedParams());
    }

    private static EPoint calcTraceSize(EPoint[] trace) {
        if (trace.length == 0) {
            return EPoint.ORIGIN;
        }
        long minX = Long.MAX_VALUE;
        long maxX = Long.MIN_VALUE;
        long minY = Long.MAX_VALUE;
        long maxY = Long.MIN_VALUE;
        for (EPoint p : trace) {
            if (p == null) {
                continue;
            }
            minX = Math.min(minX, p.getGridX());
            maxX = Math.max(maxX, p.getGridX());
            minY = Math.min(minY, p.getGridY());
            maxY = Math.max(maxY, p.getGridY());
        }
        long w = maxX - minX;
        if ((w & 1) != 0) {
            w++;
        }
        long h = maxY - minY;
        if ((h & 1) != 0) {
            h++;
        }
        return EPoint.fromGrid(w, h);
    }

    /**
     * Method to return the starting and ending angle of an arc described by this ImmutableNodeInst.
     * These values can be found in the "ART_degrees" variable on the ImmutableNodeInst.
     * @return a 2-long double array with the starting offset in the first entry (a value in radians)
     * and the amount of curvature in the second entry (in radians).
     * If the ImmutableNodeInst does not have circular information, both values are set to zero.
     */
    public double[] getArcDegrees() {
        double[] returnValues = new double[2];
        if (!(protoId instanceof PrimitiveNodeId)) {
            return returnValues;
        }
//		if (protoType != Artwork.tech().circleNode && protoType != Artwork.tech().thickCircleNode) return returnValues;

        Variable var = getVar(Artwork.ART_DEGREES);
        if (var != null) {
            Object addr = var.getObject();
            if (addr instanceof Integer) {
                Integer iAddr = (Integer) addr;
                returnValues[0] = 0.0;
                returnValues[1] = iAddr.intValue() * Math.PI / 1800.0;
            } else if (addr instanceof Float[]) {
                Float[] fAddr = (Float[]) addr;
                returnValues[0] = fAddr[0].doubleValue();
                returnValues[1] = fAddr[1].doubleValue();
            }
        }
        return returnValues;
    }

    /**
     * Method to return the length of this serpentine transistor.
     * @return the transistor's length
     * Returns -1 if this is not a serpentine transistor, or if the length cannot be found.
     */
    public double getSerpentineTransistorLength() {
        Variable var = getVar(NodeInst.TRANSISTOR_LENGTH_KEY);
        if (var == null) {
            return -1;
        }
        Object obj = var.getObject();
        if (obj instanceof Integer) {
            // C Electric stored this as a "fraction", scaled by 120
            return ((Integer) obj).intValue() / 120;
        }
        if (obj instanceof Double) {
            return ((Double) obj).doubleValue();
        }
        if (obj instanceof String) {
            return TextUtils.atof((String) obj);
        }
        return -1;
    }
}
