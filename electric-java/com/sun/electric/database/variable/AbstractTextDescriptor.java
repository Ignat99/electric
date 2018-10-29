/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: AbstractTextDescriptor.java
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

import com.sun.electric.database.geometry.Poly;
import com.sun.electric.tool.user.User;
import com.sun.electric.util.math.DBMath;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * This class describes how variable text appears.
 * <P>
 * This class should be thread-safe
 */
abstract class AbstractTextDescriptor implements Serializable {
    /*private*/ static final long VTPOSITION = 017L;		/* 0: position of text relative to point */
    /*private*/ static final int VTPOSITIONSH = 0;		/* 0: right shift of VTPOSITION */
    /*private*/ static final int VTPOSCENT = 0;		/* 0:   text centered about point */
    /*private*/ static final int VTPOSUP = 1;		/* 0:   text centered above point */
    /*private*/ static final int VTPOSDOWN = 2;		/* 0:   text centered below point */
    /*private*/ static final int VTPOSLEFT = 3;		/* 0:   text centered to left of point */
    /*private*/ static final int VTPOSRIGHT = 4;		/* 0:   text centered to right of point */
    /*private*/ static final int VTPOSUPLEFT = 5;		/* 0:   text centered to upper-left of point */
    /*private*/ static final int VTPOSUPRIGHT = 6;		/* 0:   text centered to upper-right of point */
    /*private*/ static final int VTPOSDOWNLEFT = 7;		/* 0:   text centered to lower-left of point */
    /*private*/ static final int VTPOSDOWNRIGHT = 8;		/* 0:   text centered to lower-right of point */
    /*private*/ static final int VTPOSBOXED = 9;		/* 0:   text centered and limited to object size */
    /*private*/ static final long VTDISPLAYPART = 060L;		/* 0: bits telling what to display */
    /*private*/ static final int VTDISPLAYPARTSH = 4;		/* 0: right shift of VTDISPLAYPART */
    /*private*/ static final int VTDISPLAYVALUE = 0;		/* 0:   display value */
    /*private*/ static final int VTDISPLAYNAMEVALUE = 2;		/* 0:   display name and value */
    /*private*/ static final int VTDISPLAYNAMEVALINH = 1;		/* 0:   display name, value, 1-level inherit */
    /*private*/ static final int VTDISPLAYNAMEVALINHALL = 3;		/* 0:   display name, value, any inherit */
    /*private*/ static final long VTITALIC = 0100L;		/* 0: set for italic text */
    /*private*/ static final long VTBOLD = 0200L;		/* 0: set for bold text */
    /*private*/ static final long VTUNDERLINE = 0400L;		/* 0: set for underline text */
    /*private*/ static final long VTISPARAMETER = 01000L;		/* 0: attribute is parameter (nodeinst only) */
    /*private*/ static final long VTINTERIOR = 02000L;		/* 0: text only appears inside cell */
    /*private*/ static final long VTINHERIT = 04000L;		/* 0: set to inherit value from proto to inst */
    /*private*/ static final long VTXOFF = 07770000L;		/* 0: X offset of text */
    /*private*/ static final int VTXOFFSH = 12;		/* 0: right shift of VTXOFF */
    /*private*/ static final long VTXOFFNEG = 010000000L;		/* 0: set if X offset is negative */
    /*private*/ static final long VTYOFF = 017760000000L;		/* 0: Y offset of text */
    /*private*/ static final int VTYOFFSH = 22;		/* 0: right shift of VTYOFF */
    /*private*/ static final long VTYOFFNEG = 020000000000L;		/* 0: set if Y offset is negative */
    /*private*/ static final int VTOFFMASKWID = 9;		/* 0: Width of VTXOFF and VTYOFF */
    /*private*/ static final long VTSIZE = 077777L << 32;		/* 1: size of text */
    /*private*/ static final int VTSIZESH = 0 + 32;		/* 1: right shift of VTSIZE */
    /*private*/ static final long VTFACE = 017700000L << 32;		/* 1: face of text */
    /*private*/ static final int VTFACESH = 15 + 32;		/* 1: right shift of VTFACE */
    /*private*/ static final int VTMAXFACE = 127;		/* 1: maximum value of VTFACE field */
    /*private*/ static final long VTROTATION = 060000000L << 32;		/* 1: rotation of text */
    /*private*/ static final int VTROTATIONSH = 22 + 32;		/* 1: right shift of VTROTATION */
    /*private*/ static final long VTOFFSCALE = 03700000000L << 32;		/* 1: scale of text offset */
    /*private*/ static final int VTOFFSCALESH = 24 + 32;		/* 1: right shift of VTOFFSCALE */
    /*private*/ static final long VTUNITS = 034000000000L << 32;		/* 1: units of text */
    /*private*/ static final int VTUNITSHMASK = 07;		/* 1: mask of this value after being shifted down */
    /*private*/ static final int VTUNITSSH = 29 + 32;		/* 1: right shift of VTUNITS */
    /*private*/ static final int VTUNITSNONE = 0;		/* 1:   units: none */
    /*private*/ static final int VTUNITSRES = 1;		/* 1:   units: resistance */
    /*private*/ static final int VTUNITSCAP = 2;		/* 1:   units: capacitance */
    /*private*/ static final int VTUNITSIND = 3;		/* 1:   units: inductance */
    /*private*/ static final int VTUNITSCUR = 4;		/* 1:   units: current */
    /*private*/ static final int VTUNITSVOLT = 5;		/* 1:   units: voltage */
    /*private*/ static final int VTUNITSDIST = 6;		/* 1:   units: distance */
    /*private*/ static final int VTUNITSTIME = 7;		/* 1:   units: time */

    /*private*/ static final int VTOFFMAX = (1 << VTOFFMASKWID) - 1;	/* 0: Maximal value of unshifted VTXOFF and VTYOFF */
    /*private*/ static final int VTSCALEMAX = (int) (VTOFFSCALE >> VTOFFSCALESH);	/* 1: Maximal value of unshifted VTOFFSCALE */

    /** Semantic bits - those bits which are meaningful in non-displayable text descriptors. */
    static long VTSEMANTIC = VTISPARAMETER | VTINTERIOR | VTINHERIT | VTUNITS;
    // Variable flags in C Electric
    /** display variable (uses textdescript field) */	/*private*/ static final int VDISPLAY = 0100;

    /** enumeration which represents text visibility .*/
    public enum Display {

        /** Non-displayable variable */
        NONE,
        /** Displayable variable which is temporary hidden */
        HIDDEN,
        /** Displayable variable which is shown */
        SHOWN;
    };

    /**
     * Position is a typesafe enum class that describes the text position of a Variable.
     * The Position describes the "anchor point" of the text,
     * which is the point on the text that is attached to the object and does not move.
     * For example, when the Position is CENT, then the center of the text is fixed,
     * and as the text grows, it expands uniformly about the center.
     * When the Position is UP, the text is centered above the anchor point, and as the text grows
     * it expands upward from the bottom-center point.
     */
    public static class Position {

        private final String name;
        private final int index;
        private final Poly.Type pt;
        private static final List<Position> positions = new ArrayList<Position>();

        private Position(String name, int index, Poly.Type pt) {
            this.name = name;
            this.index = index;
            this.pt = pt;
            positions.add(index, this);
        }

        /**
         * Method to return the integer equivalent of this Position.
         * @return the integer equivalent of this Position.
         */
        public int getIndex() {
            return index;
        }

        /**
         * Method to return the Poly.Type to use for this Position.
         * The Poly.Type will vary through the 9 "anchor point" locations:
         * center, left, right, up, down, up-left, up-right, down-left, down-right.
         * @return the Poly.Type to use for this Position.
         */
        public Poly.Type getPolyType() {
            return pt;
        }

        /**
         * Return Position moved in specified direction.
         * @param dx if positive move right, if negative move left.
         * @param dy if positive move up, if negative move down.
         * @return the moved position.
         */
        public Position align(int dx, int dy) {
            Position p = this;
            if (dx > 0) {
                if (p == CENT || p == RIGHT || p == LEFT) {
                    p = RIGHT;
                } else if (p == UP || p == UPRIGHT || p == UPLEFT) {
                    p = UPRIGHT;
                } else if (p == DOWN || p == DOWNRIGHT || p == DOWNLEFT) {
                    p = DOWNRIGHT;
                }
            } else if (dx < 0) {
                if (p == CENT || p == RIGHT || p == LEFT) {
                    p = LEFT;
                } else if (p == UP || p == UPRIGHT || p == UPLEFT) {
                    p = UPLEFT;
                } else if (p == DOWN || p == DOWNRIGHT || p == DOWNLEFT) {
                    p = DOWNLEFT;
                }
            }
            if (dy > 0) {
                if (p == CENT || p == UP || p == DOWN) {
                    p = UP;
                } else if (p == RIGHT || p == UPRIGHT || p == DOWNRIGHT) {
                    p = UPRIGHT;
                } else if (p == LEFT || p == UPLEFT || p == DOWNLEFT) {
                    p = UPLEFT;
                }
            } else if (dy < 0) {
                if (p == CENT || p == UP || p == DOWN) {
                    p = DOWN;
                } else if (p == RIGHT || p == UPRIGHT || p == DOWNRIGHT) {
                    p = DOWNRIGHT;
                } else if (p == LEFT || p == UPLEFT || p == DOWNLEFT) {
                    p = DOWNLEFT;
                }
            }
            return p;
        }

        /**
         * Method to return the Position to use for the given Poly.Type.
         * The Poly.Type will vary through the 9 "anchor point" locations:
         * center, left, right, up, down, up-left, up-right, down-left, down-right.
         * @return the Position to use for the given Poly.Type.
         */
        public static Position getPosition(Poly.Type type) {
            for (Position pos : positions) {
                if (type == pos.pt) {
                    return pos;
                }
            }
            return CENT;
        }

        /**
         * Method to return the number Positions.
         * @return the number of Positions.
         */
        public static int getNumPositions() {
            return positions.size();
        }

        /**
         * Method to return the Position at a given index.
         * @param index the Position number desired.
         * @return the Position at a given index.
         */
        public static Position getPositionAt(int index) {
            return positions.get(index);
        }

        /**
         * Get an iterator over all Positions
         */
        public static Iterator<Position> getPositions() {
            return Collections.unmodifiableList(positions).iterator();
        }

        /**
         * Returns a printable version of this Position.
         * @return a printable version of this Position.
         */
        public String toString() {
            return name;
        }
        /**
         * Describes text centered at a point.
         */
        public static final Position CENT = new Position("centered", VTPOSCENT, Poly.Type.TEXTCENT);
        /**
         * Describes text centered above a point.
         */
        public static final Position UP = new Position("bottom", VTPOSUP, Poly.Type.TEXTBOT);
        /**
         * Describes text centered below a point.
         */
        public static final Position DOWN = new Position("top", VTPOSDOWN, Poly.Type.TEXTTOP);
        /**
         * Describes text centered to left of a point.
         */
        public static final Position LEFT = new Position("right", VTPOSLEFT, Poly.Type.TEXTRIGHT);
        /**
         * Describes text centered to right of a point.
         */
        public static final Position RIGHT = new Position("left", VTPOSRIGHT, Poly.Type.TEXTLEFT);
        /**
         * Describes text centered to upper-left of a point.
         */
        public static final Position UPLEFT = new Position("lower-right", VTPOSUPLEFT, Poly.Type.TEXTBOTRIGHT);
        /**
         * Describes text centered to upper-right of a point.
         */
        public static final Position UPRIGHT = new Position("lower-left", VTPOSUPRIGHT, Poly.Type.TEXTBOTLEFT);
        /**
         * Describes text centered to lower-left of a point.
         */
        public static final Position DOWNLEFT = new Position("upper-right", VTPOSDOWNLEFT, Poly.Type.TEXTTOPRIGHT);
        /**
         * Describes text centered to lower-right of a point.
         */
        public static final Position DOWNRIGHT = new Position("upper-left", VTPOSDOWNRIGHT, Poly.Type.TEXTTOPLEFT);
        /**
         * Describes text centered and limited to the object size.
         * This means that the text may shrink in size or clip letters if necessary.
         */
        public static final Position BOXED = new Position("boxed", VTPOSBOXED, Poly.Type.TEXTBOX);
    }

    /**
     * DispPos is a typesafe enum class that describes text's display position on a Variable.
     */
    public static class DispPos {

        private final String name;
        private final int index;
        private static final List<DispPos> positions = new ArrayList<DispPos>();

        private DispPos(String name, int index) {
            this.name = name;
            this.index = index;
            positions.add(this);
        }

        /**
         * Method to return the integer equivalent of this DispPos.
         * @return the integer equivalent of this DispPos.
         */
        public int getIndex() {
            return index;
        }

        /**
         * Method to return the name of this DispPos.
         * It is used in popup menus.
         * @return the name of this DispPos.
         */
        public String getName() {
            return name;
        }

        /**
         * Method to return the number DispPos.
         * @return the number DispPos.
         */
        public static int getNumShowStyles() {
            return positions.size();
        }

        /**
         * Method to return the DispPos at a given index.
         * @param index the DispPos number desired.
         * @return the DispPos at a given index.
         */
        public static DispPos getShowStylesAt(int index) {
            for (DispPos d : positions) {
                if (d.index == index) {
                    return d;
                }
            }
            return NAMEVALUE;
        }

        /**
         * Get an iterator over all show styles.
         * @return an iterator over the list of show styles
         */
        public static Iterator<DispPos> getShowStyles() {
            return Collections.unmodifiableList(positions).iterator();
        }

        /**
         * Returns a printable version of this DispPos.
         * @return a printable version of this DispPos.
         */
        public String toString() {
            return name;
        }
        /**
         * Describes a Variable that displays its value.
         */
        public static final DispPos VALUE = new DispPos("value", VTDISPLAYVALUE);
        /**
         * Describes a Variable that displays its name, value, 1-level inherit.
         * The form of the display is "ATTR=VALUE;def=DEFAULT";
         */
        //public static final DispPos NAMEVALINH = new DispPos("name=inherit;def=value", VTDISPLAYNAMEVALINH);
        /**
         * Describes a Variable that displays its name and value.
         * The form of the display is "ATTR=VALUE";
         */
        public static final DispPos NAMEVALUE = new DispPos("name=value", VTDISPLAYNAMEVALUE);
        /**
         * Describes a Variable that displays its name, value, any inherit.
         * The form of the display is "ATTR=VALUE;def=DEFAULT";
         */
        //public static final DispPos NAMEVALINHALL = new DispPos("name=inheritAll;def=value", VTDISPLAYNAMEVALINHALL);
    }

    /**
     * Size is a class that describes text's size on a Variable.
     * Text size can be absolute (in points) or relative (in quarter grid units).
     */
    public static class Size {

        /** The minimu size of text (in points). */
        public static final int TXTMINPOINTS = 1;
        /** The maximum size of text (in points). */
        public static final int TXTMAXPOINTS = 63;
        /** The minimu size of text (in grid units). */
        public static final double TXTMINQGRID = 0.25;
        /** The maximum size of text (in grid units). */
        public static final double TXTMAXQGRID = 127.75;
        /** Default font size. */
        private static final int DEFAULT_FONT_SIZE = 14;
        /*private*/ static final int TXTQGRIDSH = 6;
        private final boolean absolute;
        private final double size;
        private final int bits;

        private Size(double size, boolean absolute) {
            this.size = size;
            this.absolute = absolute;
            if (absolute) {
                this.bits = (int) size;
            } else {
                this.bits = ((int) (size * 4.0)) << TXTQGRIDSH;
            }
        }

        /**
         * Method to return bits associated with this text Size.
         * @return bits associated with this text Size.
         */
        /*private*/ int getBits() {
            return bits;
        }

        /**
         * Method to return a Size object that describes a relative text size (in grid units).
         * The size must be between 1 and 63 points.
         * @param size the size in units.
         * Returns null if the size is invalid.
         */
        public static Size newAbsSize(int size) {
            if (size < TXTMINPOINTS || size > TXTMAXPOINTS) {
                return null;
            }
            return new Size(size, true);
        }

        /**
         * Method to determine if a Size object is between valid text size values.
         * The size must be between 0.25 and 127.75 grid units (in .25 increments).
         * @param size
         * @return true if the size is value
         */
        public static boolean isValidRelSize(double size)
        {
        	return (!(size < TXTMINQGRID || size > TXTMAXQGRID));
        }
        
        /**
         * Method to return a Size object that describes an absolute point text size.
         * The size must be between 0.25 and 127.75 grid units (in .25 increments).
         * @param size the size in points.
         * Returns null if the size is invalid.
         */
        public static Size newRelSize(double size) {
//            if (size < TXTMINQGRID || size > TXTMAXQGRID) {
            if (!isValidRelSize(size)) {
                return null;
            }
            return new Size(size, false);
        }

        /**
         * Method to return text Size value (in points or units).
         * @return the text Size value (in points or units).
         */
        public double getSize() {
            return size;
        }

        /**
         * Method to tell whether this text Size is absolute or relative.
         * @return true if this text size is absolute
         */
        public boolean isAbsolute() {
            return absolute;
        }

        /**
         * Method to tell whether this Size is the same as another.
         * @return true if they are equal.
         */
        public boolean equals(Object that) {
            if (that instanceof Size) {
                Size other = (Size) that;
                return this.absolute == other.absolute && DBMath.doublesEqual(this.size, other.size);
            }
            return false;
        }

        /**
         * Returns a printable version of this Size.
         * @return a printable version of this Size.
         */
        public String toString() {
            return "Text Size";
        }
    }

    /**
     * Rotation is a typesafe enum class that describes text's rotation in a Variable.
     */
    public static class Rotation {

        private final int angle;
        private final int index;
        private final String name;
        private static final List<Rotation> rotations = new ArrayList<Rotation>();

        private Rotation(int angle, int index, String name) {
            this.angle = angle;
            this.index = index;
            this.name = name;
            rotations.add(index, this);
        }

        /**
         * Method to return the integer equivalent of this DispPos.
         * This is zero-based.
         * @return the integer equivalent of this DispPos.
         */
        public int getIndex() {
            return index;
        }

        /**
         * Method to return the description of this DispPos.
         * It appears in popup menus.
         * @return the description of this DispPos.
         */
        public String getDescription() {
            return name;
        }

        /**
         * Get the angle of this rotation.
         * @return the angle of this rotation.
         */
        public int getAngle() {
            return angle;
        }

        /**
         * Get the Rotation for the given angle.
         * @param angle the angle.
         * @return a Rotation for the given angle, or null if non exists.
         */
        public static Rotation getRotation(int angle) {
            for (Rotation rot : rotations) {
                if (rot.getAngle() == angle) {
                    return rot;
                }
            }
            return null;
        }

        /**
         * Method to return the number Rotations.
         * @return the number Rotations.
         */
        public static int getNumRotations() {
            return rotations.size();
        }

        /**
         * Method to return the Rotation at a given index.
         * @param index the Rotation number desired.
         * @return the Rotation at a given index.
         */
        public static Rotation getRotationAt(int index) {
            return rotations.get(index);
        }

        /**
         * Get an iterator over all rotations
         * @return an iterator over all rotations
         */
        public static Iterator<Rotation> getRotations() {
            return Collections.unmodifiableList(rotations).iterator();
        }

        /**
         * Returns a printable version of this Rotation.
         * @return a printable version of this Rotation.
         */
        public String toString() {
            return "Text Rotation " + angle;
        }
        /** Describes a Rotation of 0 degrees. */
        public static final Rotation ROT0 =
                new Rotation(0, 0, "None");
        /** Describes a Rotation of 90 degrees. */
        public static final Rotation ROT90 =
                new Rotation(90, 1, "90 degrees counterclockwise");
        /** Describes a Rotation of 180 degrees. */
        public static final Rotation ROT180 =
                new Rotation(180, 2, "180 degrees");
        /** Describes a Rotation of 270 degrees. */
        public static final Rotation ROT270 =
                new Rotation(270, 3, "90 degrees clockwise");
    }

    /**
     * Unit is a typesafe enum class that describes text's units on a Variable.
     */
    public static class Unit {

        private final String description, name, postfixChar;
        private final int index;
        private static final List<Unit> units = new ArrayList<Unit>();

        private Unit(String description, int index, String name, String postfixChar) {
            this.description = description;
            this.index = index;
            this.name = name;
            this.postfixChar = postfixChar;
            units.add(index, this);
        }

        /**
         * Method to return the integer equivalent of this Unit.
         * This is zero-based.
         * @return the integer equivalent of this Unit.
         */
        public int getIndex() {
            return index;
        }

        /**
         * Method to return the description of this Unit.
         * It appears in popup menus.
         * For example, the name of the Time unit is "time".
         * @return the description of this Unit.
         */
        public String getDescription() {
            return description;
        }

        /**
         * Method to return the name of this Unit.
         * For example, the name of the Time unit is "Second".
         * @return the description of this Unit.
         */
        public String getName() {
            return name;
        }

        /**
         * Method to return the characters to add to the end of this Unit.
         * For example, distance uses "m" for meters.
         * @return the characters to add to the end of this Unit.
         */
        public String getPostfixChar() {
            return postfixChar;
        }

        /**
         * Method to return the number Units.
         * @return the number Units.
         */
        public static int getNumUnits() {
            return units.size();
        }

        /**
         * Method to return the Unit at a given index.
         * @param index the Unit number desired.
         * @return the Unit at a given index.
         */
        public static Unit getUnitAt(int index) {
            return units.get(index);
        }

        /**
         * Get an iterator over all units.
         * @return an iterator over the list of unit types.
         */
        public static Iterator<Unit> getUnits() {
            return Collections.unmodifiableList(units).iterator();
        }

        /**
         * Returns a printable version of this Unit.
         * @return a printable version of this Unit.
         */
        public String toString() {
            return name;
        }
        /** Describes no units. */
        public static final Unit NONE = new Unit("none", VTUNITSNONE, "", "");
        /** Describes resistance units. */
        public static final Unit RESISTANCE = new Unit("resistance", VTUNITSRES, "Ohm", "o");
        /** Describes capacitance units. */
        public static final Unit CAPACITANCE = new Unit("capacitance", VTUNITSCAP, "Farad", "f");
        /** Describes inductance units. */
        public static final Unit INDUCTANCE = new Unit("inductance", VTUNITSIND, "Henry", "h");
        /** Describes current units. */
        public static final Unit CURRENT = new Unit("current", VTUNITSCUR, "Ampere", "a");
        /** Describes voltage units. */
        public static final Unit VOLTAGE = new Unit("voltage", VTUNITSVOLT, "Volt", "v");
        /** Describes distance units. */
        public static final Unit DISTANCE = new Unit("distance", VTUNITSDIST, "Meter", "m");
        /** Describes time units. */
        public static final Unit TIME = new Unit("time", VTUNITSTIME, "Second", "s");
    }

    /**
     * ActiveFont is a class that describes fonts currently in use.
     */
    public static class ActiveFont {

        private String fontName;
        private int index;
        private static int indexCount = 0;
        private static final Map<String, ActiveFont> fontMap = new HashMap<String, ActiveFont>();
        private static final List<ActiveFont> fontList = new ArrayList<ActiveFont>();

        private ActiveFont(String fontName) {
            indexCount++;
            this.index = indexCount;
            this.fontName = fontName;
            fontMap.put(fontName, this);
            fontList.add(this);
        }

        /**
         * Method to return the maximum index value for ActiveFonts.
         * @return the maximum index value.
         * ActiveFonts will have indices ranging from 1 to this value.
         */
        public static int getMaxIndex() {
            return indexCount;
        }

        /**
         * Method to return the index for this ActiveFont.
         * @return the index of this ActiveFont.
         * The index value is 1-based, because font 0 is the "default font".
         */
        public int getIndex() {
            return index;
        }

        /**
         * Method to return the font name associated with this ActiveFont.
         * @return the font name associated with this ActiveFont.
         */
        public String getName() {
            return fontName;
        }

        /**
         * Method to return the ActiveFont with a given name.
         * @param fontName the name of the font.
         * @return an ActiveFont object.  If there is no ActiveFont
         * associated with this fontname, one is created.
         */
        public static ActiveFont findActiveFont(String fontName) {
            ActiveFont af = fontMap.get(fontName);
            if (af != null) {
                return af;
            }
            if (indexCount >= VTMAXFACE) {
                System.out.println("Too many fonts. Using default instead of " + fontName);
                return null;
            }
            return new ActiveFont(fontName);
        }

        /**
         * Method to return the ActiveFont with a given index.
         * @param index the index number (1-based) of the ActiveFont.
         * @return the ActiveFont with this index.  Returns null if there is none.
         */
        public static ActiveFont findActiveFont(int index) {
            if (index <= 0) {
                return null;
            }
            if (index > fontList.size()) {
                return null;
            }
            ActiveFont af = fontList.get(index - 1);
            return af;
        }

        /**
         * Returns a printable version of this ActiveFont.
         * @return a printable version of this ActiveFont.
         */
        public String toString() {
            return fontName;
        }
    }

    AbstractTextDescriptor() {
    }

    public enum TextType {

        /**
         * Text that resides on nodes.
         * This text includes the node name and any parameters or attributes on it.
         */
        NODE("Node", 4),
        /**
         * Text that resides on arcs.
         * This text includes the arc name and any parameters or attributes on it.
         */
        ARC("Arc", 4),
        /**
         * Text that resides on ports.
         * This text includes the port name and any parameters or attributes on it.
         */
        PORT("Port", 4),
        /**
         * Text that resides on exports.
         * This text includes the export name and any parameters or attributes on it.
         */
        EXPORT("Export", 8),
        /**
         * Annotation text.
         * Annotation text is not attached to any node or arc, but appears to move freely about the cell.
         * In implementation, they are displayable Variables on Generic:invisible-pin nodes.
         */
        ANNOTATION("Annotation", 4),
        /**
         * The name of on cell instances.
         * The default is "true".
         * @return true if the system should draw the name of on cell instances.
         */
        INSTANCE("Instance", 16),
        /**
         * Text that resides on the cell.
         * This includes the current cell's parameters or attributes (for example, spice templates).
         */
        CELL("Cell", 4);
        private final String purpose;
        private final TextDescriptor factoryTextDescriptor;

        public String getKey(String prefix) {
            return prefix + purpose;
        }

        public TextDescriptor getFactoryTextDescriptor() {
            return factoryTextDescriptor;
        }

        private TextType(String purpose, int initialSize) {
            this.purpose = purpose;
            MutableTextDescriptor mtd = new MutableTextDescriptor();
            mtd.setRelSize(initialSize * 0.25);
            mtd.setDisplay(true);
            factoryTextDescriptor = TextDescriptor.newTextDescriptor(mtd);
        }
    }

    /**
     * Returns a hash code for this <code>TextDescriptor</code>.
     * @return  a hash code value for this TextDescriptor.
     */
    @Override
    public int hashCode() {
        return lowLevelGet0() ^ lowLevelGet1() ^ getColorIndex() ^ getDisplay().hashCode();
    }

    /**
     * Compares this text descriptor to the specified object.
     * The result is <code>true</code> if and only if the argument is not
     * <code>null</code> and is a <code>TextDescriptor</code> object with
     * the same fields.
     *
     * @param   anObject   the object to compare this <code>TextDescriptor</code>
     *                     against.
     * @return  <code>true</code> if the <code>TextDescriptor</code> are equal;
     *          <code>false</code> otherwise.
     */
    @Override
    public boolean equals(Object anObject) {
        if (this == anObject) {
            return true;
        }
        if (anObject instanceof AbstractTextDescriptor) {
            AbstractTextDescriptor td = (AbstractTextDescriptor) anObject;
            return lowLevelGet() == td.lowLevelGet() && getColorIndex() == td.getColorIndex()
                    && getDisplay() == td.getDisplay();
        }
        return false;
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
    public abstract long lowLevelGet();

    /**
     * Low-level method to get the first word of the bits in the TextDescriptor.
     * These bits are a collection of flags that are more sensibly accessed
     * through special methods.
     * This general access to the bits is required because the ELIB
     * file format stores it as a full integer.
     * This should not normally be called by any other part of the system.
     * @return the first word of the bits in the TextDescriptor.
     */
    public int lowLevelGet0() {
        return (int) lowLevelGet();
    }

    /**
     * Low-level method to get the second word of the bits in the TextDescriptor.
     * These bits are a collection of flags that are more sensibly accessed
     * through special methods.
     * This general access to the bits is required because the ELIB
     * file format stores it as a full integer.
     * This should not normally be called by any other part of the system.
     * @return the second word of the bits in the TextDescriptor.
     */
    public int lowLevelGet1() {
        return (int) (lowLevelGet() >> 32);
    }

    private int getField(long mask, int shift) {
        return (int) ((lowLevelGet() & mask) >> shift);
    }

    private boolean isFlag(long mask) {
        return (lowLevelGet() & mask) != 0;
    }

    /**
     * Method to return mode how this TextDescriptor is displayable.
     * @return Display mode how this TextDescriptor is displayable.
     */
    public abstract Display getDisplay();

    /**
     * Method to return true if this TextDescriptor is displayable.
     * @return true if this TextDescriptor is displayable.
     */
    public boolean isDisplay() {
        return getDisplay() == Display.SHOWN;
    }

    /**
     * Method to return the text position of the TextDescriptor.
     * The text position describes the "anchor point" of the text,
     * which is the point on the text that is attached to the object and does not move.
     * @return the text position of the TextDescriptor.
     */
    public Position getPos() {
        int pos = getField(VTPOSITION, VTPOSITIONSH);
        if (pos >= Position.getNumPositions()) {
            pos = 0;
        }
        return Position.getPositionAt(pos);
    }

    /**
     * Returns true if this ImmutableTextDescriptor describes absolute text.
     * Text may be either absolute text (in points) or relative text (in quarter units).
     * @return true if this ImmutableTextDescriptor describes absolute text.
     */
    public boolean isAbsoluteSize() {
        int textSize = getField(VTSIZE, VTSIZESH);
        return textSize > 0 && textSize <= Size.TXTMAXPOINTS;
    }

    /**
     * Method to return the text size of the text in this TextDescriptor.
     * This is a Size object that can describe either absolute text (in points)
     * or relative text (in quarter units).
     * @return the text size of the text in this TextDescriptor.
     */
    public Size getSize() {
        int textSize = getField(VTSIZE, VTSIZESH);
        if (textSize == 0) {
            return Size.newRelSize(1);
        }
        if (textSize <= Size.TXTMAXPOINTS) {
            return Size.newAbsSize(textSize);
        }
        int sizeValue = textSize >> Size.TXTQGRIDSH;
        double size = sizeValue * 0.25;
        return Size.newRelSize(size);
    }

    public static int getDefaultFontSize() {
        return Size.DEFAULT_FONT_SIZE;
    }

    /**
     * Method to find the true size in points for this TextDescriptor in a given EditWindow0.
     * If the TextDescriptor is already Absolute (in points) nothing needs to be done.
     * Otherwise, the scale of the EditWindow0 is used to determine the acutal point size.
     * @param wnd the EditWindow0 in which drawing will occur.
     * @return the point size of the text described by this TextDescriptor.
     */
    public double getTrueSize(EditWindow0 wnd) {
        if (wnd != null) {
            return getTrueSize(wnd.getScale(), wnd);
        }
        int textSize = getField(VTSIZE, VTSIZESH);
        double trueSize = textSize > 0 && textSize <= Size.TXTMAXPOINTS ? textSize : Size.DEFAULT_FONT_SIZE;
        return trueSize * User.getGlobalTextScale();
    }

    /**
     * Method to find the true size in points for this TextDescriptor in a given scale.
     * If the TextDescriptor is already Absolute (in points) nothing needs to be done.
     * Otherwise, the scale is used to determine the acutal point size.
     * @param scale scale to draw.
     * @param wnd the EditWindow0 in which drawing will occur.
     * @return the point size of the text described by this TextDescriptor.
     */
    public double getTrueSize(double scale, EditWindow0 wnd) {
        double trueSize;
        int textSize = getField(VTSIZE, VTSIZESH);
        if (textSize == 0) {
            // relative 1
            trueSize = scale;
        } else if (textSize <= Size.TXTMAXPOINTS) {
            // absolute
            trueSize = textSize;
        } else {
            // relative
            trueSize = (textSize >> Size.TXTQGRIDSH) * 0.25 * scale;
        }
        return trueSize * (wnd == null ? User.getGlobalTextScale() : wnd.getGlobalTextScale());
    }

    /**
     * Method to return the text font of the TextDescriptor.
     * @return the text font of the TextDescriptor.
     */
    public int getFace() {
        return getField(VTFACE, VTFACESH);
    }

    /**
     * Method to get a Font to use for this TextDescriptor in a given EditWindow.
     * @param wnd the EditWindow0 in which drawing will occur.
     * @param minimalTextSize Return null for texts smaller than this
     * @return the Font to use (returns null if the text is too small to display).
     */
    public Font getFont(EditWindow0 wnd, int minimalTextSize) {
        int fontStyle = Font.PLAIN;
        String fontName = wnd == null ? User.getDefaultFont() : wnd.getDefaultFont();
        int size = (int) getTrueSize(wnd);
        if (size <= 0) {
            size = 1;
        }
        if (size < minimalTextSize) {
            return null;
        }

        if (isItalic()) {
            fontStyle |= Font.ITALIC;
        }
        if (isBold()) {
            fontStyle |= Font.BOLD;
        }
        int fontIndex = getFace();
        if (fontIndex != 0) {
            TextDescriptor.ActiveFont af = TextDescriptor.ActiveFont.findActiveFont(fontIndex);
            if (af != null) {
                fontName = af.getName();
            }
        }
        Font font = new Font(fontName, fontStyle, size);
        return font;
    }

    /**
     * Method to get a default Font to use.
     * @param wnd the EditWindow0 to use with font information (may be null).
     * @return the Font to use (returns null if the text is too small to display).
     */
    public static Font getDefaultFont(EditWindow0 wnd) {
    	String fontName = wnd == null ? User.getDefaultFont() : wnd.getDefaultFont();
        return new Font(fontName, Font.PLAIN, TextDescriptor.getDefaultFontSize());
    }

    /**
     * Method to convert a string and descriptor to a GlyphVector.
     * @param text the string to convert.
     * @param font the Font to use.
     * @return a GlyphVector describing the text.
     */
    public static GlyphVector getGlyphs(String text, Font font) {
        // make a glyph vector for the desired text
        FontRenderContext frc = new FontRenderContext(null, false, false);
        GlyphVector gv = font.createGlyphVector(frc, text);
        return gv;
    }

    /**
     * Method to return the text rotation of the TextDescriptor.
     * There are only 4 rotations: 0, 90 degrees, 180 degrees, and 270 degrees.
     * @return the text rotation of the TextDescriptor.
     */
    public Rotation getRotation() {
        return Rotation.getRotationAt(getField(VTROTATION, VTROTATIONSH));
    }

    /**
     * Method to return the text display part of the TextDescriptor.
     * @return the text display part of the TextDescriptor.
     */
    public DispPos getDispPart() {
        return DispPos.getShowStylesAt(getField(VTDISPLAYPART, VTDISPLAYPARTSH));
    }

    /**
     * Method to return true if the text in the TextDescriptor is italic.
     * @return true if the text in the TextDescriptor is italic.
     */
    public boolean isItalic() {
        return isFlag(VTITALIC);
    }

    /**
     * Method to return true if the text in the TextDescriptor is bold.
     * @return true if the text in the TextDescriptor is bold.
     */
    public boolean isBold() {
        return isFlag(VTBOLD);
    }

    /**
     * Method to return true if the text in the TextDescriptor is underlined.
     * @return true if the text in the TextDescriptor is underlined.
     */
    public boolean isUnderline() {
        return isFlag(VTUNDERLINE);
    }

    /**
     * Method to return true if the text in the TextDescriptor is interior.
     * Interior text is not seen at higher levels of the hierarchy.
     * @return true if the text in the TextDescriptor is interior.
     */
    public boolean isInterior() {
        return isFlag(VTINTERIOR);
    }

    /**
     * Method to return true if the text in the TextDescriptor is inheritable.
     * Inheritable variables copy their contents from prototype to instance.
     * Only Variables on NodeProto and PortProto objects can be inheritable.
     * When a NodeInst is created, any inheritable Variables on its NodeProto are automatically
     * created on that NodeInst.
     * @return true if the text in the TextDescriptor is inheritable.
     */
    public boolean isInherit() {
        return isFlag(VTINHERIT);
    }

    /**
     * Method to return true if the text in the TextDescriptor is a parameter.
     * Parameters are those Variables that have values on instances which are
     * passed down the hierarchy into the contents.
     * Parameters can only exist on NodeInst objects.
     * @return true if the text in the TextDescriptor is a parameter.
     */
    public boolean isParam() {
        return isFlag(VTISPARAMETER);
    }

    /**
     * Method to return the X offset of the text in the TextDescriptor.
     * @return the X offset of the text in the TextDescriptor.
     */
    public double getXOff() {
        int offset = getField(VTXOFF, VTXOFFSH);
        if (isFlag(VTXOFFNEG)) {
            offset = -offset;
        }
        int scale = getOffScale() + 1;
        return ((double) offset * scale / 4);
    }

    /**
     * Method to return the Y offset of the text in the TextDescriptor.
     * @return the Y offset of the text in the TextDescriptor.
     */
    public double getYOff() {
        int offset = getField(VTYOFF, VTYOFFSH);
        if (isFlag(VTYOFFNEG)) {
            offset = -offset;
        }
        int scale = getOffScale() + 1;
        return ((double) offset * scale / 4);
    }

    /** Method to return the offset scale of the text in the text descriptor. */
    private int getOffScale() {
        return getField(VTOFFSCALE, VTOFFSCALESH);
    }

    /**
     * Method to return the Unit of the TextDescriptor.
     * Unit describes the type of real-world unit to apply to the value.
     * For example, if this value is in volts, the Unit tells whether the value
     * is volts, millivolts, microvolts, etc.
     * @return the Unit of the TextDescriptor.
     */
    public Unit getUnit() {
        return Unit.getUnitAt(getField(VTUNITS, VTUNITSSH) & VTUNITSHMASK);
    }

    /**
     * Method to return the color index of the TextDescriptor.
     * Color indices are more general than colors, because they can handle
     * transparent layers, C-Electric-style opaque layers, and full color values.
     * Methods in "EGraphics" manipulate color indices.
     * @return the color index of the TextDescriptor.
     */
    public abstract int getColorIndex();
}
