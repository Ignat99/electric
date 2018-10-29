/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: EGraphics.java
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
package com.sun.electric.database.geometry;

import com.sun.electric.technology.Technology;
import com.sun.electric.technology.technologies.Schematics;
import com.sun.electric.util.TextUtils;

import java.awt.Color;
import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Class to define the appearance of a piece of geometry.
 */
public class EGraphics implements Serializable
{
    public static final J3DTransparencyOption DEFAULT_MODE = J3DTransparencyOption.NONE; // 3D default transparency mode DEFAULT_FACTOR
    public static final double DEFAULT_FACTOR = 0.0; // 3D default transparency factor

	private static Map<String,Outline> outlineNames = new HashMap<String,Outline>();

	/**
	 * Class to define the type of outline around a stipple pattern.
	 */
	public static enum Outline
	{
		/** Draw stipple pattern with no outline. */
		NOPAT("None", 0, 32, 1),
		/** Draw stipple pattern with solid outline. */
		PAT_S("Solid", -1, 32, 1),
		/** Draw stipple pattern with solid thick outline. */
		PAT_T1("Solid-Thick", -1, 32, 3),
		/** Draw stipple pattern with solid thicker outline. */
		PAT_T2("Solid-Thicker", -1, 32, 5),
		/** Draw stipple pattern with close dotted outline. */
		PAT_DO1("Dotted-Close", 0x55, 8, 1),
		/** Draw stipple pattern with far dotted outline. */
		PAT_DO2("Dotted-Far", 0x11, 8, 1),
		/** Draw stipple pattern with short dashed outline. */
		PAT_DA1("Dashed-Short", 0x33, 8, 1),
		/** Draw stipple pattern with long dashed outline. */
		PAT_DA2("Dashed-Long", 0xF, 6, 1),
		/** Draw stipple pattern with short dotted-dashed outline. */
		PAT_DD1("Dotted-Dashed-Short", 0x39, 8, 1),
		/** Draw stipple pattern with long dotted-dashed outline. */
		PAT_DD2("Dotted-Dashed-Long", 0xF3, 10, 1),
		/** Draw stipple pattern with close dotted thick outline. */
		PAT_DO1_T1("Dotted-Close-Thick", 0xF, 6, 3),
		/** Draw stipple pattern with far dotted thick outline. */
		PAT_DO2_T1("Dotted-Far-Thick", 0xF, 8, 3),
		/** Draw stipple pattern with dashed thick outline. */
		PAT_DA1_T1("Dashed-Thick", 0x1FFFF, 19, 3),
		/** Draw stipple pattern with close dotted thicker outline. */
		PAT_DO1_T2("Dotted-Close-Thicker", 0x1F, 8, 5),
		/** Draw stipple pattern with far dotted thicker outline. */
		PAT_DO2_T2("Dotted-Far-Thicker", 0x7F, 9, 5);

		private final String name;
		private final int pattern, len;
		private final int thickness;
		private final boolean solid;

		private Outline(String name, int pattern, int len, int thickness)
		{
			this.name = name;
			outlineNames.put(name, this);
			this.pattern = pattern;
			this.len = len;
			this.thickness = thickness;
			this.solid = (pattern == -1);
		}

		public String getName() { return name; }

		public String getConstName() { return name(); }

		public int getIndex() { return ordinal(); }

		public boolean isSolidPattern() { return solid; }

		public int getPattern() { return pattern; }

		public int getLen() { return len; }

		public int getThickness() { return thickness; }

		public static Outline findOutline(int index)
		{
			return Outline.class.getEnumConstants()[index];
		}

		public static Outline findOutline(String name)
		{
			// return valueOf(name);
			return outlineNames.get(name);
		}

		public static List<Outline> getOutlines() { return Arrays.asList(Outline.class.getEnumConstants()); }

        @Override
		public String toString() { return name; }
	}

    public enum J3DTransparencyOption {
        FASTEST,
        NICEST,
        BLENDED,
        SCREEN_DOOR,
        NONE;

        public final int mode = ordinal();
        public static J3DTransparencyOption valueOf(int mode) {
            return J3DTransparencyOption.class.getEnumConstants()[mode];
        }
    }

	/** display: true to use patterns; false for solid */	private final boolean displayPatterned;
	/** printer: true to use patterns; false for solid */	private final boolean printPatterned;
	/** the outline pattern */								private final Outline patternOutline;
	/** transparent layer to use (0 for none) */			private final int transparentLayer;
	/** color to use */										private final Color color;
	/** opacity (0 to 1) of color */						private final double opacity;
	/** whether to draw color in foreground */				private final boolean foreground;
	/** stipple pattern to draw */							private final int [] pattern;
	/** stipple pattern to draw with proper bit order */	private final int [] reversedPattern;
    /** true if pattern is empty */                         private final boolean emptyPattern;
    /** 3D transparency mode */                             private final J3DTransparencyOption transparencyMode;
    /** 3D transparency factor */                           private final double transparencyFactor;

	/**
	 * There are 3 ways to encode color in an integer.
	 * If the lowest bit (FULLRGBBIT) is set, this is a full RGB color in the high 3 bytes.
	 * If the next lowest bit (OPAQUEBIT) is set, this is an "old C Electric" opaque color
	 * (such as WHITE, BLACK, etc., listed below).
	 * If neither of these bits is set, this is a transparent layer
	 * (LAYERT1, LAYERT2, etc., listed below).
	 */
	/** Describes the full RGB escape bit. */				public final static int FULLRGBBIT = 01;
	/** Describes opaque color escape bit. */				public final static int OPAQUEBIT =  02;

	// the opaque colors (all have the OPAQUEBIT set in them)
	/** Describes the color white. */						public final static int WHITE =    0002;
	/** Describes the color black. */						public final static int BLACK =    0006;
	/** Describes the color red. */							public final static int RED =      0012;
	/** Describes the color blue. */						public final static int BLUE =     0016;
	/** Describes the color green. */						public final static int GREEN =    0022;
	/** Describes the color cyan. */						public final static int CYAN =     0026;
	/** Describes the color magenta. */						public final static int MAGENTA =  0032;
	/** Describes the color yellow. */						public final static int YELLOW =   0036;
	/** Describes the cell and port names. */				public final static int CELLTXT =  0042;
	/** Describes the cell outline. */						public final static int CELLOUT =  0046;
	/** Describes the window border color. */				public final static int WINBOR =   0052;
	/** Describes the highlighted window border color. */	public final static int HWINBOR =  0056;
	/** Describes the menu border color. */					public final static int MENBOR =   0062;
	/** Describes the highlighted menu border color. */		public final static int HMENBOR =  0066;
	/** Describes the menu text color. */					public final static int MENTXT =   0072;
	/** Describes the menu glyph color. */					public final static int MENGLY =   0076;
	/** Describes the cursor color. */						public final static int CURSOR =   0102;
	/** Describes the color gray. */						public final static int GRAY =     0106;
	/** Describes the color orange. */						public final static int ORANGE =   0112;
	/** Describes the color purple. */						public final static int PURPLE =   0116;
	/** Describes the color brown. */						public final static int BROWN =    0122;
	/** Describes the color light gray. */					public final static int LGRAY =    0126;
	/** Describes the color dark gray. */					public final static int DGRAY =    0132;
	/** Describes the color light red. */					public final static int LRED =     0136;
	/** Describes the color dark red. */					public final static int DRED =     0142;
	/** Describes the color light green. */					public final static int LGREEN =   0146;
	/** Describes the color dark green. */					public final static int DGREEN =   0152;
	/** Describes the color light blue. */					public final static int LBLUE =    0156;
	/** Describes the color dark blue. */					public final static int DBLUE =    0162;

	// the transparent layers
	/** Describes transparent layer 1. */					private final static int LAYERT1 =      04;
	/** Describes transparent layer 2. */					private final static int LAYERT2 =     010;
	/** Describes transparent layer 3. */					private final static int LAYERT3 =     020;
	/** Describes transparent layer 4. */					private final static int LAYERT4 =     040;
	/** Describes transparent layer 5. */					private final static int LAYERT5 =    0100;
	/** Describes transparent layer 6. */					private final static int LAYERT6 =    0200;
	/** Describes transparent layer 7. */					private final static int LAYERT7 =    0400;
	/** Describes transparent layer 8. */					private final static int LAYERT8 =   01000;
	/** Describes transparent layer 9. */					private final static int LAYERT9 =   02000;
	/** Describes transparent layer 10. */					private final static int LAYERT10 =  04000;
	/** Describes transparent layer 11. */					private final static int LAYERT11 = 010000;
	/** Describes transparent layer 12. */					private final static int LAYERT12 = 020000;
	/** Describes transparent layer 13. */					private final static int LAYERT13 = 040000;
	/** Describes transparent layer 14. */					private final static int LAYERT14 = 100000;

	// Constants used in technologies and in creating an EGraphics
	/** defines the 1st transparent layer. */				private static final int TRANSPARENT_1  =  1;
	/** defines the 2nd transparent layer. */				private static final int TRANSPARENT_2  =  2;
	/** defines the 3rd transparent layer. */				private static final int TRANSPARENT_3  =  3;
	/** defines the 4th transparent layer. */				private static final int TRANSPARENT_4  =  4;
	/** defines the 5th transparent layer. */				private static final int TRANSPARENT_5  =  5;
	/** defines the 6th transparent layer. */				private static final int TRANSPARENT_6  =  6;
	/** defines the 7th transparent layer. */				private static final int TRANSPARENT_7  =  7;
	/** defines the 8th transparent layer. */				private static final int TRANSPARENT_8  =  8;
	/** defines the 9th transparent layer. */				private static final int TRANSPARENT_9  =  9;
	/** defines the 10th transparent layer. */				private static final int TRANSPARENT_10 = 10;
	/** defines the 11th transparent layer. */				private static final int TRANSPARENT_11 = 11;
	/** defines the 12th transparent layer. */				private static final int TRANSPARENT_12 = 12;
	/** defines the 13th transparent layer. */				private static final int TRANSPARENT_13 = 13;
	/** defines the 14th transparent layer. */				private static final int TRANSPARENT_14 = 14;

	public static int getMaxTransparentLayer() { return TRANSPARENT_14; }
	
	/**
	 * Method to create a graphics object.
	 * @param displayPatterned true if drawn with a pattern on the display.
	 * @param printPatterned true if drawn with a pattern on a printer.
	 * @param outlineWhenPatterned the outline texture to use when patterned.
	 * @param transparentLayer the transparent layer number (0 for none).
	 * @param red the red component of this EGraphics.
	 * @param green the green component of this EGraphics.
	 * @param blue the blue component of this EGraphics.
	 * @param opacity the opacity of this EGraphics (1 for opaque, 0 for transparent).
	 * @param foreground the foreground factor of this EGraphics (1 for to be in foreground).
	 * @param pattern the 16x16 stipple pattern of this EGraphics (16 integers).
	 */
	public EGraphics(boolean displayPatterned, boolean printPatterned, Outline outlineWhenPatterned,
		int transparentLayer, int red, int green, int blue, double opacity, boolean foreground, int[] pattern)
    {
        this(displayPatterned, printPatterned, outlineWhenPatterned,
                transparentLayer, red, green, blue, opacity, foreground, pattern, DEFAULT_MODE, DEFAULT_FACTOR);
    }

	/**
	 * Method to create a graphics object.
	 * @param displayPatterned true if drawn with a pattern on the display.
	 * @param printPatterned true if drawn with a pattern on a printer.
	 * @param outlineWhenPatterned the outline texture to use when patterned.
	 * @param transparentLayer the transparent layer number (0 for none).
	 * @param red the red component of this EGraphics.
	 * @param green the green component of this EGraphics.
	 * @param blue the blue component of this EGraphics.
	 * @param opacity the opacity of this EGraphics (1 for opaque, 0 for transparent).
	 * @param foreground the foreground factor of this EGraphics (1 for to be in foreground).
	 * @param pattern the 16x16 stipple pattern of this EGraphics (16 integers).
     * @param transparencyMode 3D transparency mode
     * @param transparencyFactor 3D transparency factor
	 */
	public EGraphics(boolean displayPatterned, boolean printPatterned, Outline outlineWhenPatterned,
		int transparentLayer, int red, int green, int blue, double opacity, boolean foreground, int[] pattern,
        J3DTransparencyOption transparencyMode, double transparencyFactor)
	{
		this.displayPatterned = displayPatterned;
		this.printPatterned = printPatterned;
		this.patternOutline = (outlineWhenPatterned != null) ? outlineWhenPatterned : Outline.NOPAT;
		if (transparentLayer < 0 || transparentLayer > getMaxTransparentLayer()) {
			System.out.println("Graphics transparent color bad: " + transparentLayer);
            transparentLayer = 0;
        }
		this.transparentLayer = transparentLayer;
		if (red < 0 || red > 255 || green < 0 || green > 255 || blue < 0 || blue > 255) {
            System.out.println("Graphics color bad: (" + red + "," + green + "," + blue + ")");
            red = Math.min(Math.max(red, 0), 255);
            green = Math.max(Math.max(green, 0), 255);
            blue = Math.max(Math.max(blue, 0), 255);
        }
		this.color = new Color(red, green, blue);
		this.opacity = validateOpacity(opacity);
		this.foreground = foreground;
		if (pattern != null)
		{
			if (pattern.length != 16)
				throw new IllegalArgumentException("Graphics bad: has " + pattern.length + " pattern entries instead of 16");
	        this.pattern = pattern.clone();
		}
		else 
			this.pattern = new int[16];
        boolean emptyPattern = true;
        for (int i = 0; i < this.pattern.length; i++) {
            int p = this.pattern[i] & 0xFFFF;
            this.pattern[i] = p;
            emptyPattern = emptyPattern && p == 0;
        }
        this.emptyPattern = emptyPattern;
        this.reversedPattern = makeReversedPattern(this.pattern);
        this.transparencyMode = transparencyMode != null ? transparencyMode : J3DTransparencyOption.NONE;
        this.transparencyFactor = transparencyFactor;
	}

	/**
	 * Method to create a graphics object.
	 * @param displayPatterned true if drawn with a pattern on the display.
	 * @param printPatterned true if drawn with a pattern on a printer.
	 * @param outlineWhenPatterned the outline texture to use when patterned.
	 * @param transparentLayer the transparent layer number (0 for none).
	 * @param red the red component of this EGraphics.
	 * @param green the green component of this EGraphics.
	 * @param blue the blue component of this EGraphics.
	 * @param opacity the opacity of this EGraphics (1 for opaque, 0 for transparent).
	 * @param foreground the foreground factor of this EGraphics (1 for to be in foreground).
	 * @param pattern the 16x16 stipple pattern of this EGraphics (16 integers).
	 */
	private EGraphics(boolean displayPatterned, boolean printPatterned, Outline outlineWhenPatterned,
		int transparentLayer, Color opaqueColor, double opacity, boolean foreground, int[] pattern, int[] reversedPattern, boolean emptyPattern,
        J3DTransparencyOption transparencyMode, double transparencyFactor)
	{
		this.displayPatterned = displayPatterned;
		this.printPatterned = printPatterned;
		this.patternOutline = outlineWhenPatterned;
		this.transparentLayer = transparentLayer;
		this.color = opaqueColor;
		this.opacity = validateOpacity(opacity);
		this.foreground = foreground;
        this.pattern = pattern;
        this.reversedPattern = reversedPattern;
        this.emptyPattern = emptyPattern;
        this.transparencyMode = transparencyMode;
        this.transparencyFactor = transparencyFactor;
    }

	private String makePatString(int [] pattern)
	{
		StringBuffer sb = new StringBuffer();
		for(int i=0; i<16; i++)
		{
			if (i > 0) sb.append("/");
			sb.append(Integer.toString(pattern[i]));
		}
		return sb.toString();
	}

	private void parsePatString(String patString, int [] pattern)
	{
		int pos = 0;
		for(int i=0; i<16; i++)
		{
			pattern[i] = TextUtils.atoi(patString.substring(pos)) & 0xFFFF;
			pos = patString.indexOf('/', pos) + 1;
		}
	}

	/**
	 * Method describes how this EGraphics appears on a display.
	 * This EGraphics can be drawn as a solid fill or as a pattern.
	 * @return true to draw this EGraphics patterned on a display.
	 * False to draw this EGraphics as a solid fill on a display.
	 */
	public boolean isPatternedOnDisplay() { return displayPatterned; }

	/**
	 * Method returns EGraphics which differs from this EGraphics by appearance on a display.
	 * This EGraphics can be drawn as a solid fill or as a pattern.
	 * @param p true to draw this EGraphics patterned on a display.
	 * False to draw this EGraphics as a solid fill on a display.
     * @return EGraphics with specified appearance on a display
	 */
	public EGraphics withPatternedOnDisplay(boolean p) {
        if (p == displayPatterned) return this;
        return new EGraphics(p, printPatterned, patternOutline, transparentLayer, color, opacity, foreground,
                pattern, reversedPattern, emptyPattern, transparencyMode, transparencyFactor);
	}

	/**
	 * Method describes how this EGraphics appears on a printer.
	 * This EGraphics can be drawn as a solid fill or as a pattern.
	 * @return true to draw this EGraphics patterned on a printer.
	 * False to draw this EGraphics as a solid fill on a printer.
	 */
	public boolean isPatternedOnPrinter() { return printPatterned; }

	/**
	 * Method returns EGraphics which differs from this EGraphics by appearance on a printer.
	 * This EGraphics can be drawn as a solid fill or as a pattern.
	 * @param p true to draw this EGraphics patterned on a printer.
	 * False to draw this EGraphics as a solid fill on a printer.
     * @return EGraphics with specified appearance on a printer.
	 */
	public EGraphics withPatternedOnPrinter(boolean p) {
        if (p == printPatterned) return this;
        return new EGraphics(displayPatterned, p, patternOutline, transparentLayer, color, opacity, foreground,
                pattern, reversedPattern, emptyPattern, transparencyMode, transparencyFactor);
	}

	/**
	 * Method to tell the type of outline pattern.
	 * When the EGraphics is drawn as a pattern, the outline can be defined more clearly by drawing a line around the edge.
	 * @return the type of outline pattern.
	 */
	public Outline getOutlined() { return patternOutline; }

	/**
	 * Method returns EGraphics which differs from this EGraphics by Outline pattern
	 * When the EGraphics is drawn as a pattern, the outline can be defined more clearly by drawing a line around the edge.
	 * @param o the outline pattern.
     * @return EGraphics with specified outline pattern
	 */
    public EGraphics withOutlined(Outline o) {
        if (o == null)
            o = Outline.NOPAT;
        if (o == patternOutline) return this;
        return new EGraphics(displayPatterned, printPatterned, o, transparentLayer, color, opacity, foreground,
                pattern, reversedPattern, emptyPattern, transparencyMode, transparencyFactor);
    }

	/**
	 * Method to return the transparent layer number associated with this EGraphics.
	 * @return the transparent layer number associated with this EGraphics.
	 * A value of zero means that this EGraphics is not drawn transparently.
	 * Instead, use the "getColor()" method to get its solid color.
	 */
	public int getTransparentLayer() { return transparentLayer; }

	/**
	 * Method returns EGraphics which differs from this EGraphics by transparent Layer.
	 * @param transparentLayer the transparent layer number associated with this EGraphics.
	 * A value of zero means that this EGraphics is not drawn transparently.
	 * Then, use the "setColor()" method to set its solid color.
     * @return EGraphcos with specified transparentLayer
	 */
    public EGraphics withTransparentLayer(int transparentLayer) {
		if (transparentLayer < 0 || transparentLayer > getMaxTransparentLayer()) {
			System.out.println("Graphics transparent color bad: " + transparentLayer);
            transparentLayer = 0;
        }
        if (transparentLayer == this.transparentLayer) return this;
        return new EGraphics(displayPatterned, printPatterned, patternOutline, transparentLayer, color, opacity, foreground,
                pattern, reversedPattern, emptyPattern, transparencyMode, transparencyFactor);
    }

	/**
	 * Method to get the stipple pattern of this EGraphics.
	 * The stipple pattern is a 16 x 16 pattern that is stored in 16 integers.
	 * @return the stipple pattern of this EGraphics.
	 */
	public int [] getPattern() { return pattern; }

	/**
	 * Method to get the reversed stipple pattern of this EGraphics.
	 * The reversed stipple pattern is a 16 x 32 pattern that is stored in 16 integers.
	 * @return the stipple pattern of this EGraphics.
	 */
	public int [] getReversedPattern() { return reversedPattern; }

	/**
	 * Method to get the String representation of the stipple pattern of this EGraphics.
	 * @return the String representation the stipple pattern of this EGraphics.
	 */
	public String getPatternString() { return makePatString(pattern); }

	/**
	 * Returns true when the stipple pattern is empty.
	 * @return true when the stipple pattern is empty.the stipple pattern of this EGraphics.
	 */
    public boolean isEmptyPattern() { return emptyPattern; }
    
	/**
	 * Method returns EGraphics which differs from this EGraphics by stipple pattern.
	 * The stipple pattern is a 16 x 16 pattern that is stored in 16 integers.
	 * @param pattern the stipple pattern of this EGraphics.
     * @return EGraphics with specified stipple pattern.
	 */
    public EGraphics withPattern(int [] pattern) {
		if (pattern.length != 16)
			throw new IllegalArgumentException("Graphics bad: has " + pattern.length + " pattern entries instead of 16");
        pattern = pattern.clone();
        boolean emptyPattern = true;
        for (int i = 0; i < this.pattern.length; i++) {
            int p = pattern[i] & 0xFFFF;
            pattern[i] = p;
            emptyPattern = emptyPattern && p == 0;
        }
        if (Arrays.equals(pattern, this.pattern)) return this;
        int[] reversedPattern = makeReversedPattern(pattern);
        return new EGraphics(displayPatterned, printPatterned, patternOutline, transparentLayer, color, opacity, foreground,
                pattern, reversedPattern, emptyPattern, transparencyMode, transparencyFactor);
    }

	/**
	 * Method returns EGraphics which differs from this EGraphics by stipple pattern.
	 * The stipple pattern is a 16 x 16 pattern that is stored in 16 integers.
	 * @param patternStr text representation of the stipple pattern of this EGraphics.
     * @return EGraphics with specified stipple pattern.
	 */
    public EGraphics withPattern(String patternStr) {
        int[] pattern = new int[16];
        parsePatString(patternStr, pattern);
        return withPattern(pattern);
    }

    private int[] makeReversedPattern(int[] pattern) {
		int[] reversedPattern = new int[16];
		for (int i = 0; i < reversedPattern.length; i++) {
			int shortPattern = pattern[i];
			if ((shortPattern >>> 16) != 0) {
				System.out.println("Graphics bad: has " + Integer.toHexString(shortPattern) + " pattern line");
                shortPattern &= 0xFFFF;
            }
			for (int j = 0; j < 16; j++) {
				if ((shortPattern & (1 << (15 - j))) != 0)
					reversedPattern[i] |= 0x10001 << j;
			}
		}
        return reversedPattern;
    }

	/**
	 * Method to get the opacity of this EGraphics.
	 * Opacity runs from 0 (transparent) to 1 (opaque).
	 * @return the opacity of this EGraphics.
	 */
	public double getOpacity() { return opacity; }

    /**
     * Method to check range of opacity provided.
     * If < 0, reset it to zero.
     * If > 1, reset it to one.
     * @param opacity
     * @return valid opacity
     */
    private static double validateOpacity(double opacity)
    {
        if (opacity < 0)
        {
            System.out.println("Opacity " + opacity + " smaller than 0. Resetting to 0");
            return 0;
        }
        else if (opacity > 1)
        {

            System.out.println("Opacity " + opacity + " bigger than 1. Resetting to 1");
            return 1;
        }
        return opacity;
    }

    /**
	 * Method returns EGraphics which differs from this EGraphics by opacity.
	 * Opacity runs from 0 (transparent) to 1 (opaque).
	 * @param opacity the opacity of this EGraphics.
     * @return EGraphics with specified opacity.
	 */
    public EGraphics withOpacity(double opacity) {
        opacity = validateOpacity(opacity);
        if (opacity == this.opacity) return this;
        return new EGraphics(displayPatterned, printPatterned, patternOutline, transparentLayer, color, opacity, foreground,
                pattern, reversedPattern, emptyPattern, transparencyMode, transparencyFactor);
    }

	/**
	 * Method to get whether this EGraphics should be drawn in the foreground.
	 * The foreground is the main "mix" of layers, such as metal and polysilicon.
	 * The background is typically used by implant and well layers.
	 * @return the whether this EGraphics should be drawn in the foreground.
	 */
	public boolean getForeground() { return foreground; }

	/**
	 * Method returns EGraphics which differs from this EGraphics that it should be drawn in the foreground.
	 * The foreground is the main "mix" of layers, such as metal and polysilicon.
	 * The background is typically used by implant and well layers.
	 * @param f true if this EGraphics should be drawn in the foreground.
     * @return EGraphics with specified foreground
	 */
	public EGraphics withForeground(boolean f) {
        if (f == this.foreground) return this;
        return new EGraphics(displayPatterned, printPatterned, patternOutline, transparentLayer, color, opacity, f,
                pattern, reversedPattern, emptyPattern, transparencyMode, transparencyFactor);
    }

	/**
	 * Method to return the color associated with this EGraphics.
     * Alpha component is determined by opacity
	 * @return the color associated with this EGraphics.
	 */
	public Color getAlphaColor() {
		int alpha = (int)(opacity * 255.0);
		return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
	}

	/**
	 * Method to return the color associated with this EGraphics.
     * Alpha component is 255.
	 * @return the color associated with this EGraphics.
	 */
	public Color getColor() { return color; }

	/**
	 * Method to return the color associated with this EGraphics considering
     * Colors of transparentLayers.
     * @param transparentColors Colors of transparentLayers.
	 * @return the color associated with this EGraphics.
	 */
	public Color getColor(Color[] transparentColors) {
        if (transparentLayer > 0 && transparentLayer <= transparentColors.length)
            return transparentColors[transparentLayer - 1];
        return color;
    }

	/**
	 * Returns the RGB value representing the color associated with this EGraphics.
	 * (Bits 16-23 are red, 8-15 are green, 0-7 are blue).
	 * Alpha/opacity component is 0
	 * @return the RGB value of the color
	 */
	public int getRGB() {
		return color.getRGB() & 0xFFFFFF;
	}

	/**
	 * Method returns EGraphics which differs from this EGraphics by to the associated color.
     * The alpha component of the color is set to full opacity,
     * alpha component of the argument is ignored.
	 * @param color the color to set.
     * @return EGraphics with specified color.
	 */
    public EGraphics withColor(Color color) {
        if (color.getAlpha() != 0xFF)
            color = new Color(color.getRGB());
        if (color.equals(this.color)) return this;
        assert color.getAlpha() == 0xFF;
        return new EGraphics(displayPatterned, printPatterned, patternOutline,
                transparentLayer, new Color(color.getRed(), color.getGreen(), color.getBlue()), opacity, foreground,
                pattern, reversedPattern, emptyPattern, transparencyMode, transparencyFactor);
    }

	/**
	 * Method to convert a color index into a Color.
	 * Color indices are more general than colors, because they can handle
	 * transparent layers, C-Electric-style opaque layers, and full color values.
	 * @param colorIndex the color index to convert.
	 * @param colorMap an optional color map to use for transparent colors (if null, figure it out)
	 * @return a Color that describes the index
	 * Returns null if the index is a transparent layer.
	 */
	public static Color getColorFromIndex(int colorIndex, Color [] colorMap)
	{
		if ((colorIndex&OPAQUEBIT) != 0)
		{
			// an opaque color
			switch (colorIndex)
			{
				case WHITE:   return new Color(255, 255, 255);
				case BLACK:   return new Color(  0,   0,   0);
				case RED:     return new Color(255,   0,   0);
				case BLUE:    return new Color(  0,   0, 255);
				case GREEN:   return new Color(  0, 255,   0);
				case CYAN:    return new Color(  0, 255, 255);
				case MAGENTA: return new Color(255,   0, 255);
				case YELLOW:  return new Color(255, 255,   0);
				case CELLTXT: return new Color(  0,   0,   0);
				case CELLOUT: return new Color(  0,   0,   0);
				case WINBOR:  return new Color(  0,   0,   0);
				case HWINBOR: return new Color(  0, 255,   0);
				case MENBOR:  return new Color(  0,   0,   0);
				case HMENBOR: return new Color(255, 255, 255);
				case MENTXT:  return new Color(  0,   0,   0);
				case MENGLY:  return new Color(  0,   0,   0);
				case CURSOR:  return new Color(  0,   0,   0);
				case GRAY:    return new Color(180, 180, 180);
				case ORANGE:  return new Color(255, 190,   6);
				case PURPLE:  return new Color(186,   0, 255);
				case BROWN:   return new Color(139,  99,  46);
				case LGRAY:   return new Color(230, 230, 230);
				case DGRAY:   return new Color(100, 100, 100);
				case LRED:    return new Color(255, 150, 150);
				case DRED:    return new Color(159,  80,  80);
				case LGREEN:  return new Color(175, 255, 175);
				case DGREEN:  return new Color( 89, 159,  85);
				case LBLUE:   return new Color(150, 150, 255);
				case DBLUE:   return new Color(  2,  15, 159);
			}
			return null;
		}
		if ((colorIndex&FULLRGBBIT) != 0)
		{
			// a full RGB color (opaque)
			return new Color((colorIndex >> 24) & 0xFF, (colorIndex >> 16) & 0xFF, (colorIndex >> 8) & 0xFF);
		}

		// handle transparent colors
		if (colorMap == null)
		{
			Technology curTech = Technology.getCurrent();
			colorMap = curTech.getColorMap();
			if (colorMap == null)
			{
				Technology altTech = Schematics.getDefaultSchematicTechnology();
				if (altTech != curTech)
				{
					colorMap = altTech.getColorMap();
				}
				if (colorMap == null) return null;
			}
		}
		int trueIndex = colorIndex >> 2;
		if (trueIndex < colorMap.length) return colorMap[trueIndex];
		return null;
	}

	/**
	 * Method returns EGraphics which differs from this EGraphics by a "color index".
	 * Color indices are more general than colors, because they can handle
	 * transparent layers, C-Electric-style opaque layers, and full color values.
	 * Artwork nodes and arcs represent individualized color by using color indices.
	 * @param colorIndex the color index to set.
	 */
	public EGraphics withColorIndex(int colorIndex)
	{
		if ((colorIndex&(OPAQUEBIT|FULLRGBBIT)) != 0)
		{
			// an opaque or full RGB color
			return withColor(getColorFromIndex(colorIndex, null)).withTransparentLayer(0);
		}

		// a transparent color
        int transparentLayer = this.transparentLayer;
		if ((colorIndex&LAYERT1) != 0) transparentLayer = TRANSPARENT_1; else
		if ((colorIndex&LAYERT2) != 0) transparentLayer = TRANSPARENT_2; else
		if ((colorIndex&LAYERT3) != 0) transparentLayer = TRANSPARENT_3; else
		if ((colorIndex&LAYERT4) != 0) transparentLayer = TRANSPARENT_4; else
		if ((colorIndex&LAYERT5) != 0) transparentLayer = TRANSPARENT_5; else
		if ((colorIndex&LAYERT6) != 0) transparentLayer = TRANSPARENT_6; else
		if ((colorIndex&LAYERT7) != 0) transparentLayer = TRANSPARENT_7; else
		if ((colorIndex&LAYERT8) != 0) transparentLayer = TRANSPARENT_8; else
		if ((colorIndex&LAYERT9) != 0) transparentLayer = TRANSPARENT_9; else
		if ((colorIndex&LAYERT10) != 0) transparentLayer = TRANSPARENT_10; else
		if ((colorIndex&LAYERT11) != 0) transparentLayer = TRANSPARENT_11; else
		if ((colorIndex&LAYERT12) != 0) transparentLayer = TRANSPARENT_12; else
		if ((colorIndex&LAYERT13) != 0) transparentLayer = TRANSPARENT_13; else
		if ((colorIndex&LAYERT14) != 0) transparentLayer = TRANSPARENT_14;
        return withTransparentLayer(transparentLayer);
	}

	/**
	 * Method to convert a Color to a color index.
	 * Color indices are more general than colors, because they can handle
	 * transparent layers, C-Electric-style opaque layers, and full color values.
	 * Artwork nodes and arcs represent individualized color by using color indices.
	 * @param color a Color object
	 * @return the color index that describes that color.
	 */
	public static int makeIndex(Color color)
	{
		int red   = color.getRed();
		int green = color.getGreen();
		int blue  = color.getBlue();
		int index = (red << 24) | (green << 16) | (blue << 8) | FULLRGBBIT;
		return index;
	}

	/**
	 * Method to convert a transparent layer to a color index.
	 * Color indices are more general than colors, because they can handle
	 * transparent layers, C-Electric-style opaque layers, and full color values.
	 * @param transparentLayer the transparent layer number.
	 * @return the color index that describes that transparent layer.
	 */
	public static int makeIndex(int transparentLayer)
	{
		switch (transparentLayer)
		{
			case TRANSPARENT_1: return LAYERT1;
			case TRANSPARENT_2: return LAYERT2;
			case TRANSPARENT_3: return LAYERT3;
			case TRANSPARENT_4: return LAYERT4;
			case TRANSPARENT_5: return LAYERT5;
			case TRANSPARENT_6: return LAYERT6;
			case TRANSPARENT_7: return LAYERT7;
			case TRANSPARENT_8: return LAYERT8;
			case TRANSPARENT_9: return LAYERT9;
			case TRANSPARENT_10: return LAYERT10;
			case TRANSPARENT_11: return LAYERT11;
			case TRANSPARENT_12: return LAYERT12;
			case TRANSPARENT_13: return LAYERT13;
			case TRANSPARENT_14: return LAYERT14;
		}
		return 0;
	}

	/**
	 * Method to find the index of a color, given its name.
	 * @param name the name of the color.
	 * @return the index of the color.
	 */
	public static int findColorIndex(String name)
	{
		if (name.equals("white"))          return WHITE;
		if (name.equals("black"))          return BLACK;
		if (name.equals("red"))            return RED;
		if (name.equals("blue"))           return BLUE;
		if (name.equals("green"))          return GREEN;
		if (name.equals("cyan"))           return CYAN;
		if (name.equals("magenta"))        return MAGENTA;
		if (name.equals("yellow"))         return YELLOW;
		if (name.equals("gray"))           return GRAY;
		if (name.equals("orange"))         return ORANGE;
		if (name.equals("purple"))         return PURPLE;
		if (name.equals("brown"))          return BROWN;
		if (name.equals("light-gray"))     return LGRAY;
		if (name.equals("dark-gray"))      return DGRAY;
		if (name.equals("light-red"))      return LRED;
		if (name.equals("dark-red"))       return DRED;
		if (name.equals("light-green"))    return LGREEN;
		if (name.equals("dark-green"))     return DGREEN;
		if (name.equals("light-blue"))     return LBLUE;
		if (name.equals("dark-blue"))      return DBLUE;
		if (name.equals("transparent-1"))  return LAYERT1;
		if (name.equals("transparent-2"))  return LAYERT2;
		if (name.equals("transparent-3"))  return LAYERT3;
		if (name.equals("transparent-4"))  return LAYERT4;
		if (name.equals("transparent-5"))  return LAYERT5;
		if (name.equals("transparent-6"))  return LAYERT6;
		if (name.equals("transparent-7"))  return LAYERT7;
		if (name.equals("transparent-8"))  return LAYERT8;
		if (name.equals("transparent-9"))  return LAYERT9;
		if (name.equals("transparent-10")) return LAYERT10;
		if (name.equals("transparent-11")) return LAYERT11;
		if (name.equals("transparent-12")) return LAYERT12;
		return 0;
	}

	/**
	 * Method to tell the name of the color with a given index.
	 * Color indices are more general than colors, because they can handle
	 * transparent layers, C-Electric-style opaque layers, and full color values.
	 * @param colorIndex the color number.
	 * @return the name of that color.
	 */
	public static String getColorIndexName(int colorIndex)
	{
		if ((colorIndex&FULLRGBBIT) != 0)
		{
			int red =   (colorIndex >> 24) & 0xFF;
			int green = (colorIndex >> 16) & 0xFF;
			int blue =  (colorIndex >> 8) & 0xFF;
			return "Color (" + red + "," + green + "," + blue + ")";
		}
		switch (colorIndex)
		{
			case WHITE:    return "white";
			case BLACK:    return "black";
			case RED:      return "red";
			case BLUE:     return "blue";
			case GREEN:    return "green";
			case CYAN:     return "cyan";
			case MAGENTA:  return "magenta";
			case YELLOW:   return "yellow";
			case GRAY:     return "gray";
			case ORANGE:   return "orange";
			case PURPLE:   return "purple";
			case BROWN:    return "brown";
			case LGRAY:    return "light-gray";
			case DGRAY:    return "dark-gray";
			case LRED:     return "light-red";
			case DRED:     return "dark-red";
			case LGREEN:   return "light-green";
			case DGREEN:   return "dark-green";
			case LBLUE:    return "light-blue";
			case DBLUE:    return "dark-blue";
			case LAYERT1:  return "transparent-1";
			case LAYERT2:  return "transparent-2";
			case LAYERT3:  return "transparent-3";
			case LAYERT4:  return "transparent-4";
			case LAYERT5:  return "transparent-5";
			case LAYERT6:  return "transparent-6";
			case LAYERT7:  return "transparent-7";
			case LAYERT8:  return "transparent-8";
			case LAYERT9:  return "transparent-9";
			case LAYERT10: return "transparent-10";
			case LAYERT11: return "transparent-11";
			case LAYERT12: return "transparent-12";
		}
		return "ColorIndex "+colorIndex;
	}

	/**
	 * Method to return the array of color indices.
	 * @return an array of the possible color indices.
	 */
	public static int [] getColorIndices()
	{
		return new int [] {WHITE, BLACK, RED, BLUE, GREEN, CYAN, MAGENTA, YELLOW,
			GRAY, ORANGE, PURPLE, BROWN, LGRAY, DGRAY, LRED, DRED, LGREEN, DGREEN, LBLUE, DBLUE,
			LAYERT1, LAYERT2, LAYERT3, LAYERT4, LAYERT5, LAYERT6, LAYERT7, LAYERT8, LAYERT9,
			LAYERT10, LAYERT11, LAYERT12};
	}

	/**
	 * Method to return the array of transparent color indices.
	 * @return an array of the possible transparent color indices.
	 */
	public static int [] getTransparentColorIndices()
	{
		return new int [] {LAYERT1, LAYERT2, LAYERT3, LAYERT4, LAYERT5, LAYERT6, LAYERT7, LAYERT8, LAYERT9,
			LAYERT10, LAYERT11, LAYERT12};
	}

    /**
	 * Method to return the transparency mode of this EGraphics for the 3D View.
     * Possible values "NONE, "FASTEST", "NICEST", "BLENDED", "SCREEN_DOOR".
	 * @return the transparency mode of this layer for the 3D view.
	 */
	public J3DTransparencyOption getTransparencyMode() { return transparencyMode; }

    /**
	 * Method returns EGraphics which differs form this EGraphics by transparency mode.
	 * Possible values "NONE, "FASTEST", "NICEST", "BLENDED", "SCREEN_DOOR".
	 * @param mode the transparency mode of this layer.
	 */
	public EGraphics withTransparencyMode(J3DTransparencyOption mode) {
        if (mode == null)
            mode = J3DTransparencyOption.NONE;
        if (mode == transparencyMode) return this;
        return new EGraphics(displayPatterned, printPatterned, patternOutline,
                transparentLayer, color, opacity, foreground,
                pattern, reversedPattern, emptyPattern, mode, transparencyFactor);
    }

    /**
	 * Method to return the transparency factor of this EGraphics.
     * Possible values from 0 (opaque) -> 1 (transparent)
	 * @return the transparency factor of this layer for the 3D view.
	 */
	public double getTransparencyFactor() { return transparencyFactor; }

    /**
	 * Method returns EGraphics which differs from this EGraphics by transparency factor.
	 * Layers can have a transparency from 0 (opaque) to 1(transparent).
	 * @param factor the transparency factor of this layer.
     * @return EGraphics with specified transparency factor.
	 */
	public EGraphics withTransparencyFactor(double factor) {
        if (factor == transparencyFactor) return this;
        return new EGraphics(displayPatterned, printPatterned, patternOutline,
                transparentLayer, color, opacity, foreground,
                pattern, reversedPattern, emptyPattern, transparencyMode, factor);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (o instanceof EGraphics) {
            EGraphics that = (EGraphics)o;
            return  this.displayPatterned == that.displayPatterned &&
                    this.printPatterned == that.printPatterned &&
                    this.patternOutline == that.patternOutline &&
                    this.transparentLayer == that.transparentLayer &&
                    this.color.equals(that.color) &&
                    this.opacity == that.opacity &&
                    this.foreground == that.foreground &&
                    Arrays.equals(this.pattern, that.pattern) &&
                    this.transparencyMode == that.transparencyMode &&
                    this.transparencyFactor == that.transparencyFactor;
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = color.hashCode();
        if (pattern != null)
            hash += 79*pattern[0];
        return hash;
    }
}
