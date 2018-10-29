/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: ImmutableTextDescriptor.java
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
package com.sun.electric.database.variable;

import java.util.HashMap;

/**
 * This class describes how variable text appears.
 * <P>
 * This class should be thread-safe
 */
public class TextDescriptor extends AbstractTextDescriptor {

    /** the text descriptor is displayable */
    private final Display display;
    /** the bits of the text descriptor */
    private final long bits;
    /** the color of the text descriptor */
    private final int colorIndex;
    /** cache of all TextDescriptors */
    private static final HashMap<TextDescriptor, TextDescriptor> allDescriptors = new HashMap<TextDescriptor, TextDescriptor>();
    /** empty text descriptor. Size is relative 1.0. isDisplay is true. */
    public static final TextDescriptor EMPTY = newTextDescriptor(new MutableTextDescriptor());

    /**
     * The constructor creates canonized copy of anotherTextDescriptor.
     * @param descriptor another descriptor.
     */
    private TextDescriptor(AbstractTextDescriptor descriptor) {
        Display display = descriptor.getDisplay();
        long bits = descriptor.lowLevelGet();

        // Convert invalid VTPOSITION to default VTPOSCENT
        if (((bits & VTPOSITION) >> VTPOSITIONSH) > VTPOSBOXED) {
            bits = (bits & ~VTPOSITION) | (VTPOSCENT << VTPOSITIONSH);
        }
        // Convert VTDISPLAYNAMEVALINH and VTDISPLAYNAMEVALINHALL to VTDISPLAYNAMEVALUE
        if ((bits & VTDISPLAYPART) != 0) {
            bits = (bits & ~VTDISPLAYPART) | (VTDISPLAYNAMEVALUE << VTDISPLAYPARTSH);
        }
        // Convert zero VTSIZE to RelSize(1)
        if ((bits & VTSIZE) == 0) {
            bits |= (4L << Size.TXTQGRIDSH) << VTSIZESH;
        }
        // clear unused bits if non-displayable
        if (display == Display.NONE) {
            bits &= VTSEMANTIC;
        }
        // clear signs of zero-offsets
        boolean zeroXOff = (bits & VTXOFF) == 0;
        boolean zeroYOff = (bits & VTYOFF) == 0;
        if (zeroXOff) {
            bits &= ~VTXOFFNEG;
        }
        if (zeroYOff) {
            bits &= ~VTYOFFNEG;
        }
        if (zeroXOff && zeroYOff) {
            bits &= ~VTOFFSCALE;
        }

        this.display = display;
        this.bits = bits;
        this.colorIndex = display != Display.NONE ? descriptor.getColorIndex() : 0;
    }

    private Object readResolve() {
        return getUniqueTextDescriptor(this);
    }

    public static TextDescriptor newTextDescriptor(AbstractTextDescriptor td) {
        if (td instanceof TextDescriptor) {
            return (TextDescriptor) td;
        }
        return getUniqueTextDescriptor(td);
    }

    private synchronized static TextDescriptor getUniqueTextDescriptor(AbstractTextDescriptor td) {
        TextDescriptor cacheTd = allDescriptors.get(td);
        if (cacheTd != null) {
            return cacheTd;
        }
        TextDescriptor itd = new TextDescriptor(td);
        if (!itd.equals(td)) {
            // is canonized text descriptor already here ?
            cacheTd = allDescriptors.get(itd);
            if (cacheTd != null) {
                return cacheTd;
            }
        }
        allDescriptors.put(itd, itd);
        return itd;
    }

    /**
     * Returns TextDescriptor which differs from this TextDescriptor by displayable flag.
     * Displayable Variables are shown with the object.
     * @param state true, if new TextDescriptor is displayable.
     * @return TextDescriptor which differs from this TextDescriptor by displayable flag.
     */
    public TextDescriptor withDisplay(boolean state) {
        if (isDisplay() == state) {
            return this;
        }
        MutableTextDescriptor td = new MutableTextDescriptor(this);
        td.setDisplay(state ? Display.SHOWN : Display.NONE);
        return newTextDescriptor(td);
    }

    /**
     * Returns TextDescriptor which differs from this TextDescriptor by displayable mode.
     * Displayable Variables are shown with the object.
     * @param display new displayable mode.
     * @return TextDescriptor which differs from this TextDescriptor by displayable mode.
     */
    public TextDescriptor withDisplay(Display display) {
        if (getDisplay() == display) {
            return this;
        }
        MutableTextDescriptor td = new MutableTextDescriptor(this);
        td.setDisplay(display);
        return newTextDescriptor(td);
    }

    /**
     * Returns TextDescriptor which differs from this TextDescriptor by position.
     * The text position describes the "anchor point" of the text,
     * which is the point on the text that is attached to the object and does not move.
     * @param p the text position of new TextDescriptor.
     * @return TextDescriptor which differs from this TextDescriptor by position.
     * @throws NullPointerException if p is null.
     */
    public TextDescriptor withPos(Position p) {
        if (p == null) {
            throw new NullPointerException();
        }
        if (getPos() == p) {
            return this;
        }
        MutableTextDescriptor td = new MutableTextDescriptor(this);
        td.setPos(p);
        return newTextDescriptor(td);
    }

    /**
     * Returns TextDescriptor which differs from this TextDescriptor by text size.
     * New size is absolute size (in points).
     * The size must be between 1 and 63 points.
     * @param s the point size of new TextDescriptor.
     * @return TextDescriptor which differs from this TextDescriptor by text size.
     */
    public TextDescriptor withAbsSize(int s) {
        MutableTextDescriptor td = new MutableTextDescriptor(this);
        td.setAbsSize(s);
        return newTextDescriptor(td);
    }

    /**
     * Method to determine if a Size object is between valid text size values.
     * The size must be between 0.25 and 127.75 grid units (in .25 increments).
     * @param size
     * @return true if the size is value
     */
    public static boolean isValidRelSize(double size)
    {
    	return (Size.isValidRelSize(size));
    }
    
    /**
     * Returns TextDescriptor which differs from this TextDescriptor by text size.
     * New size is a relative size (in units).
     * The size must be between 0.25 and 127.75 grid units (in .25 increments).
     * @param s the unit size of new TextDescriptor.
     * @return TextDescriptor which differs from this TextDescriptor by text size.
     */
    public TextDescriptor withRelSize(double s) {
        MutableTextDescriptor td = new MutableTextDescriptor(this);
        td.setRelSize(s);
        return newTextDescriptor(td);
    }

    /**
     * Returns TextDescriptor which differs from this TextDescriptor by text font.
     * @param f the text font of new TextDescriptor.
     * @return TextDescriptor which differs from this TextDescriptor by text font.
     */
    public TextDescriptor withFace(int f) {
        if (getFace() == f) {
            return this;
        }
        MutableTextDescriptor td = new MutableTextDescriptor(this);
        td.setFace(f);
        return newTextDescriptor(td);
    }

    /**
     * Returns TextDescriptor which differs from this TextDescriptor by rotation.
     * There are only 4 rotations: 0, 90 degrees, 180 degrees, and 270 degrees.
     * @param r the text rotation of new TextDescriptor.
     * @return TextDescriptor which differs from this TextDescriptor by rotation.
     */
    public TextDescriptor withRotation(Rotation r) {
        if (r == null) {
            r = Rotation.ROT0;
        }
        if (getRotation() == r) {
            return this;
        }
        MutableTextDescriptor td = new MutableTextDescriptor(this);
        td.setRotation(r);
        return newTextDescriptor(td);
    }

    /**
     * Returns TextDescriptor which differs from this TextDescriptor by display part.
     * @param dispPos the text display part of new TextDescriptor.
     * @return TextDescriptor which differs from this TextDescriptor by display part.
     * @throws NullPointerException if dispPos is null
     */
    public TextDescriptor withDispPart(DispPos dispPos) {
        if (dispPos == null) {
            throw new NullPointerException();
        }
        if (getDispPart() == dispPos) {
            return this;
        }
        MutableTextDescriptor td = new MutableTextDescriptor(this);
        td.setDispPart(dispPos);
        return newTextDescriptor(td);
    }

    /**
     * Returns TextDescriptor which differs from this TextDescriptor by italic flag.
     * @param state true if text of new TextDescriptor is italic.
     * @return TextDescriptor which differs from this TextDescriptor by italic flag.
     */
    public TextDescriptor withItalic(boolean state) {
        if (isItalic() == state) {
            return this;
        }
        MutableTextDescriptor td = new MutableTextDescriptor(this);
        td.setItalic(state);
        return newTextDescriptor(td);
    }

    /**
     * Returns TextDescriptor which differs from this TextDescriptor by bold flag.
     * @param state true if text of new TextDescriptor is bold.
     * @return TextDescriptor which differs from this TextDescriptor by bold flag.
     */
    public TextDescriptor withBold(boolean state) {
        if (isBold() == state) {
            return this;
        }
        MutableTextDescriptor td = new MutableTextDescriptor(this);
        td.setBold(state);
        return newTextDescriptor(td);
    }

    /**
     * Returns TextDescriptor which differs from this TextDescriptor by underline flag.
     * @param state true if text of new TextDescriptor is underlined.
     * @return TextDescriptor which differs from this TextDescriptor by underline flag.
     */
    public TextDescriptor withUnderline(boolean state) {
        if (isUnderline() == state) {
            return this;
        }
        MutableTextDescriptor td = new MutableTextDescriptor(this);
        td.setUnderline(state);
        return newTextDescriptor(td);
    }

    /**
     * Returns TextDescriptor which differs from this TextDescriptor by interior flag.
     * Interior text is not seen at higher levels of the hierarchy.
     * @param state true if text with new TextDescriptor is interior.
     * @return TextDescriptor which differs from this TextDescriptor by interior flag.
     */
    public TextDescriptor withInterior(boolean state) {
        if (isInterior() == state) {
            return this;
        }
        MutableTextDescriptor td = new MutableTextDescriptor(this);
        td.setInterior(state);
        return newTextDescriptor(td);
    }

    /**
     * Returns TextDescriptor which differs from this TextDescriptor by inheritable flag.
     * Inheritable variables copy their contents from prototype to instance.
     * Only Variables on NodeProto and PortProto objects can be inheritable.
     * When a NodeInst is created, any inheritable Variables on its NodeProto are automatically
     * created on that NodeInst.
     * @param state true if Variable with new TextDescriptor is inheritable.
     * @return TextDescriptor which differs from this TextDescriptor by inheritable flag.
     */
    public TextDescriptor withInherit(boolean state) {
        if (isInherit() == state) {
            return this;
        }
        MutableTextDescriptor td = new MutableTextDescriptor(this);
        td.setInherit(state);
        return newTextDescriptor(td);
    }

    /**
     * Returns TextDescriptor which differs from this TextDescriptor by parameter flag.
     * Parameters are those Variables that have values on instances which are
     * passed down the hierarchy into the contents.
     * Parameters can only exist on Cell objects.
     * @param state true if Variable with new TextDescriptor is parameter.
     * @return TextDescriptor which deffers from this TextDescriptor by parameter flag.
     */
    public TextDescriptor withParam(boolean state) {
        if (isParam() == state) {
            return this;
        }
        MutableTextDescriptor td = new MutableTextDescriptor(this);
        td.setParam(state);
        return newTextDescriptor(td);
    }

    /**
     * Returns TextDescriptor which differs from this TextDescriptor by
     * X and Y offsets of the text in the Variable's TextDescriptor.
     * The values are scaled by 4, so a value of 3 indicates a shift of 0.75 and a value of 4 shifts by 1.
     * @param xd the X offset of the text in new Variable's TextDescriptor.
     * @param yd the Y offset of the text in new Variable's TextDescriptor.
     * @return TextDescriptor which differs from this TextDescriptor by
     * X and Y offsets of the text in the Variable's TextDescriptor.
     */
    public TextDescriptor withOff(double xd, double yd) {
        if (getXOff() == xd && getYOff() == yd) {
            return this;
        }
        MutableTextDescriptor td = new MutableTextDescriptor(this);
        td.setOff(xd, yd);
        return newTextDescriptor(td);
    }

    /**
     * Returns TextDescriptor which differs from this TextDescriptor by unit.
     * Unit describe the type of real-world unit to apply to the value.
     * For example, if this value is in volts, the Unit tells whether the value
     * is volts, millivolts, microvolts, etc.
     * @param u the Unit of new TextDescriptor.
     * @return TextDescriptor which differs from this TextDescriptor by unit.
     */
    public TextDescriptor withUnit(Unit u) {
        if (u == null) {
            u = Unit.NONE;
        }
        if (getUnit() == u) {
            return this;
        }
        MutableTextDescriptor td = new MutableTextDescriptor(this);
        td.setUnit(u);
        return newTextDescriptor(td);
    }

    /**
     * Returns TextDescriptor which differs from this TextDescriptor by colorIndex.
     * Color indices are more general than colors, because they can handle
     * transparent layers, C-Electric-style opaque layers, and full color values.
     * Methods in "EGraphics" manipulate color indices.
     * @param colorIndex color index of new TextDescriptor.
     * @return TextDescriptor which differs from this TextDescriptor by colorIndex.
     */
    public TextDescriptor withColorIndex(int colorIndex) {
        if (getColorIndex() == colorIndex) {
            return this;
        }
        MutableTextDescriptor td = new MutableTextDescriptor(this);
        td.setColorIndex(colorIndex);
        return newTextDescriptor(td);
    }

    public TextDescriptor withDisplayWithoutParam() {
        if (isDisplay() && !isParam()) {
            return this;
        }
        MutableTextDescriptor mtd = new MutableTextDescriptor(this);
        mtd.setDisplay(Display.SHOWN);
        mtd.setParam(false);
        return newTextDescriptor(mtd);
    }

    public TextDescriptor withoutParam() {
        if (/*isDisplay() &&*/!isParam()) {
            return this;
        }
        MutableTextDescriptor mtd = new MutableTextDescriptor(this);
        /*mtd.setDisplay(Display.SHOWN);*/
        mtd.setParam(false);
        return newTextDescriptor(mtd);
    }

    public static int cacheSize() {
        return allDescriptors.size();
    }

    /**
     * Method to return mode how this TextDescriptor is displayable.
     * @return Display mode how this TextDescriptor is displayable.
     */
    @Override
    public Display getDisplay() {
        return display;
    }

    /**
     * Low-level method to get the bits in the TextDescriptor.
     * These bits are a collection of flags that are more sensibly accessed
     * through special methods.
     * This general access to the bits is required because the ELIB
     * file format stores it as a full integer.
     * This should not normally be called by any other part of the system.
     * @return the bits in the TextDescriptor.
     */
    @Override
    public long lowLevelGet() {
        return bits;
    }

    /**
     * Method to return the color index of the TextDescriptor.
     * Color indices are more general than colors, because they can handle
     * transparent layers, C-Electric-style opaque layers, and full color values.
     * Methods in "EGraphics" manipulate color indices.
     * @return the color index of the TextDescriptor.
     */
    @Override
    public int getColorIndex() {
        return colorIndex;
    }
}
