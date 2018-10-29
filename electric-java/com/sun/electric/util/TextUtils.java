/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: TextUtils.java
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
package com.sun.electric.util;

import com.sun.electric.database.EditingPreferences;
import com.sun.electric.tool.Job;

import java.awt.Color;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.StringTokenizer;
import java.util.TimeZone;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * This class is a collection of text utilities.
 */
public class TextUtils {

    /**
     * Determines if the specified character is a ISO-LATIN-1 digit
     * (<code>'0'</code> through <code>'9'</code>).
     * <p>
     * This can be method instead of Character, if we are not ready
     * to handle Arabi-Indic, Devanagaru and other digits.
     *
     * @param   ch   the character to be tested.
     * @return  <code>true</code> if the character is a ISO-LATIN-1 digit;
     *          <code>false</code> otherwise.
     * @see     java.lang.Character#isDigit(char)
     */
    public static boolean isDigit(char ch) {
        return '0' <= ch && ch <= '9';
    }

    /**
     * Determines if the specified character is a letter or digit.
     * <p>
     * A character is considered to be a letter or digit if either
     * <code>Character.isLetter(char ch)</code> or
     * <code>TextUtils.isDigit(char ch)</code> returns
     * <code>true</code> for the character.
     *
     * @param   ch   the character to be tested.
     * @return  <code>true</code> if the character is a letter or digit;
     *          <code>false</code> otherwise.
     * @see     TextUtils#isDigit(char)
     * @see     java.lang.Character#isJavaLetterOrDigit(char)
     * @see     java.lang.Character#isLetter(char)
     */
    public static boolean isLetterOrDigit(char ch) {
        return isDigit(ch) || Character.isLetter(ch);
    }

    /**
     * Returns canonical character for ignore-case comparison.
     * This is the same as Character.toLowerCase(Character.toUpperCase(ch)).
     * @param ch given char.
     * @return canonical character for the given char.
     */
    public static char canonicChar(char ch) {
        if (ch <= 'Z') {
            if (ch >= 'A') {
                ch += 'a' - 'A';
            }
        } else {
            if (ch >= '\u0080') {
                ch = Character.toLowerCase(Character.toUpperCase(ch));
            }
        }
        return ch;
    }

    /**
     * Returns canonical string for ignore-case comparison.
     * FORALL String s1, s2: s1.equalsIgnoreCase(s2) == canonicString(s1).equals(canonicString(s2)
     * FORALL String s: canonicString(canonicString(s)).equals(canonicString(s))
     * @param s given String
     * @return canonical String
     * Simple "toLowerCase" is not sufficient.
     * For example ("\u0131").equalsIgnoreCase("i") , but Character.toLowerCase('\u0131') == '\u0131' .
     */
    public static String canonicString(String s) {
        int i = 0;
        for (; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (canonicChar(ch) != ch) {
                break;
            }
        }
        if (i == s.length()) {
            return s;
        }

        char[] chars = s.toCharArray();
        for (; i < s.length(); i++) {
            chars[i] = canonicChar(chars[i]);
        }
        return new String(chars);
    }

//	static {
//		for (int i = Character.MIN_VALUE; i <= Character.MAX_VALUE; i++) {
//			char ch = (char)i;
//			char toLower = Character.toLowerCase(ch);
//			char toUpper = Character.toUpperCase(ch);
//			char canonic = canonicChar(toUpper);
//			if (canonic != toLower) {
//				System.out.println(ch + " " + Integer.toHexString(ch) +
//						" lower " + toLower + " " + Integer.toHexString(toLower) +
//						" upper " + toUpper + " " + Integer.toHexString(toUpper) +
//						" canonic " + canonic + " " + Integer.toHexString(canonic));
//				assert Character.toLowerCase(Character.toUpperCase(canonic)) == canonic;
//			}
//		}
//	}
    /**
     * Method to determine if one string is a subset of another, but case-insensitive.
     * @param main the main string.
     * @param with the substring.
     * @return true if the main string starts with the substring, ignoring case.
     */
    public static boolean startsWithIgnoreCase(String main, String with) {
        int mainLen = main.length();
        int withLen = with.length();
        if (withLen > mainLen) {
            return false;
        }
        for (int i = 0; i < withLen; i++) {
            char mainChr = canonicChar(main.charAt(i));
            char withChr = canonicChar(with.charAt(i));
            if (mainChr != withChr) {
                return false;
            }
        }
        return true;
    }

    /**
     * Method to parse the floating-point number in a string.
     * There is one reason to use this method instead of Double.parseDouble:
     * this method does not throw an exception if the number is invalid (or blank).
     * @param text the string with a number in it.
     * @return the numeric value.
     */
    public static double atof(String text) {
        return atof(text, null);
    }

    /**
     * Method to parse the floating-point number in a string, using a default value if no number can be determined.
     * @param text the string to convert to a double.
     * @param defaultVal the value to return if the string cannot be converted to a double.
     * If 'defaultVal' is null and the text cannot be converted to a number, the method returns 0.
     * @return the numeric value.
     */
    public static double atof(String text, Double defaultVal) {
        // remove commas that denote 1000's separators
        text = text.replaceAll(",", "");

        double v = 0;
        try {
            Number n = parsePostFixNumber(text, null);
            v = n.doubleValue();
        } catch (NumberFormatException ex) {
            int start = 0;
            while (start < text.length() && text.charAt(start) == ' ') {
                start++;
            }
            int end = start;

            // allow initial + or -
            if (end < text.length() && (text.charAt(end) == '-' || text.charAt(end) == '+')) {
                end++;
            }

            // allow digits
            while (end < text.length() && TextUtils.isDigit(text.charAt(end))) {
                end++;
            }

            // allow decimal point and digits beyond it
            if (end < text.length() && text.charAt(end) == '.') {
                end++;
                while (end < text.length() && TextUtils.isDigit(text.charAt(end))) {
                    end++;
                }
            }

            // allow exponent
            if (end < text.length() && (text.charAt(end) == 'e' || text.charAt(end) == 'E')) {
                end++;
                if (end < text.length() && (text.charAt(end) == '-' || text.charAt(end) == '+')) {
                    end++;
                }
                while (end < text.length() && TextUtils.isDigit(text.charAt(end))) {
                    end++;
                }
            }

            if (end <= start) {
                if (defaultVal != null) {
                    return defaultVal.doubleValue();
                }
                return 0;
            }
            try {
                v = Double.parseDouble(text.substring(start, end - start));
            } catch (NumberFormatException e) {
                v = 0;
            }
        }
        return v;
    }

    /**
     * Method to parse the number in a string.
     * <P>
     * There are many reasons to use this method instead of Integer.parseInt...
     * <UL>
     * <LI>This method can handle any radix.
     *     If the number begins with "0", presume base 8.
     *     If the number begins with "0b", presume base 2.
     *     If the number begins with "0x", presume base 16.
     *     Otherwise presume base 10.
     * <LI>This method can handle numbers that affect the sign bit.
     *     If you give 0xFFFFFFFF to Integer.parseInt, you get a numberFormatPostFix exception.
     *     This method properly returns -1.
     * <LI>This method does not require that the entire string be part of the number.
     *     If there is extra text after the end, Integer.parseInt fails (for example "123xx").
     * <LI>This method does not throw an exception if the number is invalid (or blank).
     * </UL>
     * @param s the string with a number in it.
     * @return the numeric value.
     */
    public static int atoi(String s) {
        return atoi(s, 0, 0);
    }

    /**
     * Method to parse the number in a string.
     * See the comments for "atoi(String s)" for reasons why this method exists.
     * @param s the string with a number in it.
     * @param pos the starting position in the string to find the number.
     * @return the numeric value.
     */
    public static int atoi(String s, int pos) {
        return atoi(s, pos, 0);
    }

    /**
     * Method to parse the number in a string.
     * See the comments for "atoi(String s)" for reasons why this method exists.
     * @param s the string with a number in it.
     * @param pos the starting position in the string to find the number.
     * @param base the forced base of the number (0 to determine it automatically).
     * @return the numeric value.
     */
    public static int atoi(String s, int pos, int base) {
        int num = 0;
        int sign = 1;
        int len = s.length();
        if (pos < len && s.charAt(pos) == '-') {
            pos++;
            sign = -1;
        }
        if (base == 0) {
            base = 10;
            if (pos < len && s.charAt(pos) == '0') {
                pos++;
                base = 8;
                if (pos < len && (s.charAt(pos) == 'x' || s.charAt(pos) == 'X')) {
                    pos++;
                    base = 16;
                } else if (pos < len && (s.charAt(pos) == 'b' || s.charAt(pos) == 'B')) {
                    pos++;
                    base = 2;
                }
            }
        }
        for (; pos < len; pos++) {
            char cat = s.charAt(pos);
            int digit = Character.digit(cat, base);
            if (digit < 0) {
                break;
            }
            num = num * base + digit;
// 			if ((cat >= 'a' && cat <= 'f') || (cat >= 'A' && cat <= 'F'))
// 			{
// 				if (base != 16) break;
// 				num = num * 16;
// 				if (cat >= 'a' && cat <= 'f') num += cat - 'a' + 10; else
// 					num += cat - 'A' + 10;
// 				continue;
// 			}
//			if (!TextUtils.isDigit(cat)) break;
//			if (cat >= '8' && base == 8) break;
//			num = num * base + cat - '0';
        }
        return (num * sign);
    }

    /**
     * Method to convert a string with color values into an array of colors.
     * @param str the string, with colors separated by "/" and the RGB values in
     * a color separated by ",".  For example, "255,0,0/0,0,255" describes two
     * colors: red and blue.
     * @return an array of Color values.
     */
    public static Color[] getTransparentColors(String str) {
        String[] colorNames = str.split("/");
        Color[] colors = new Color[colorNames.length];
        for (int i = 0; i < colorNames.length; i++) {
            String colorName = colorNames[i].trim();
            String[] rgb = colorName.split(",");
            if (rgb.length != 3) {
                return null;
            }
            int r = TextUtils.atoi(rgb[0]);
            int g = TextUtils.atoi(rgb[1]);
            int b = TextUtils.atoi(rgb[2]);
            colors[i] = new Color(r, g, b);
        }
        return colors;
    }

    private static NumberFormat numberFormatPostFix = null;

    /**
     * Method to convert a double to a string.
     * Also scales number and appends appropriate postfix UnitScale string.
     * @param v the double value to format.
     * @return the string representation of the number.
     */
    public static String formatDoublePostFix(double v) {
        return formatDoublePostFix(v, EditingPreferences.getInstance().getUnitsPrecision());
    }

    /**
     * Method to convert a double to a string.
     * Also scales number and appends appropriate postfix UnitScale string.
     * @param v the double value to format.
     * @param numFractions the number of digits to the right of the decimal point.
     * @return the string representation of the number.
     */
    public static String formatDoublePostFix(double v, int numFractions) {
        if (numberFormatPostFix == null) {
            numberFormatPostFix = NumberFormat.getInstance(Locale.US);
            try {
                DecimalFormat d = (DecimalFormat) numberFormatPostFix;
                d.setDecimalSeparatorAlwaysShown(false);
                d.setGroupingSize(300);	 // make it so comma (1000's separator) is never used
            } catch (Exception e) {
            }
        }

        // get editing preferences to find precision of numbers
        numberFormatPostFix.setMaximumFractionDigits(numFractions); // 3 originally
        int unitScaleIndex = 0;
        if (v != 0) {
//            while ((Math.abs(v) >= 1000000) && (unitScaleIndex > UnitScale.UNIT_BASE)) {
            while ((Math.abs(v) >= 1000) && (unitScaleIndex > UnitScale.UNIT_BASE)) {
                v /= 1000;
                unitScaleIndex--;
            }
            while ((Math.abs(v) < 0.1) && (unitScaleIndex < UnitScale.UNIT_END)) {
                v *= 1000;
                unitScaleIndex++;
            }

            // if number still out of range, adjust decimal formatting
            if (Math.abs(v) < 0.1) {
                int maxDecimals = 3;
                double v2 = Math.abs(v);
                while (v2 < 0.1) {
                    maxDecimals++;
                    v2 *= 10;
                }
                numberFormatPostFix.setMaximumFractionDigits(maxDecimals);
            }
        }
        UnitScale u = UnitScale.findFromIndex(unitScaleIndex);
        String result = numberFormatPostFix.format(v);
        return result + u.getPostFix();
    }
    private static NumberFormat numberFormatSpecific = null;

    /**
     * Method to convert a double to a string.
     * If the double has no precision past the decimal, none will be shown.
     * @param v the double value to format.
     * @return the string representation of the number.
     */
    public static String formatDouble(double v) {
        // get editing preferences to find precision of numbers
    	EditingPreferences ep = EditingPreferences.getInstance();
    	if (ep == null) return formatDouble(v, 3);
        return formatDouble(v, ep.getUnitsPrecision());
    }

    /**
     * Method to convert a double to a string.
     * It will show up to 'numFractions' digits past the decimal point if numFractions is greater
     * than zero. If numFractions is 0, it will show infinite (as far as doubles go) precision.
     * If the double has no precision past the decimal, none will be shown.
     * This method is now thread safe.
     * @param v the double value to format.
     * @param numFractions the number of digits to the right of the decimal point.
     * @return the string representation of the number.
     */
    public static synchronized String formatDouble(double v, int numFractions) {
        if (numberFormatSpecific == null) {
            numberFormatSpecific = NumberFormat.getInstance(Locale.US);
            if (numberFormatSpecific != null) {
                numberFormatSpecific.setGroupingUsed(false);
            }
            try {
                DecimalFormat d = (DecimalFormat) numberFormatSpecific;
//				DecimalFormat d = (DecimalFormat)numberFormatPostFix;
                d.setDecimalSeparatorAlwaysShown(false);
            } catch (Exception e) {
            }

        }
        if (numFractions == 0) {
            numberFormatSpecific.setMaximumFractionDigits(340);
        } else {
            numberFormatSpecific.setMaximumFractionDigits(numFractions);
        }
        return numberFormatSpecific.format(v);
    }
    private static SimpleDateFormat simpleDateFormat = new SimpleDateFormat("EEE MMM dd, yyyy HH:mm:ss");

    /**
     * Method to convert a Date to a String using local TimeZone.
     * @param date the date to format.
     * @return the string representation of the date.
     */
    public static String formatDate(Date date) {
        return simpleDateFormat.format(date);
    }
    // SMR removed this method because the initialization of the "PST" time zone only works in the USA
    private static SimpleDateFormat simpleDateFormatGMT = new SimpleDateFormat("EEE MMMM dd, yyyy HH:mm:ss zzz");

    static {
        simpleDateFormatGMT.setTimeZone(TimeZone.getTimeZone("GMT"));
    }

    /**
     * Method to convert a Date to a String using GMT TimeZone.
     * @param date the date to format.
     * @return the string representation of the date.
     */
    public static String formatDateGMT(Date date) {
        return simpleDateFormatGMT.format(date);
    }

    /**
     * Method to converts a floating point number into engineering units such as pico, micro, milli, etc.
     * @param value floating point value to be converted to engineering notation.
     */
    public static String convertToEngineeringNotation(double value) {
        return convertToEngineeringNotation(value, "", 9999);
    }

    /**
     * Method to converts a floating point number into engineering units such as pico, micro, milli, etc.
     * @param value floating point value to be converted to engineering notation.
     * @param unit a unit string to append to the result (null for none).
     */
    public static String convertToEngineeringNotation(double value, String unit) {
        return convertToEngineeringNotation(value, unit, 9999);
    }

    /**
     * Method to converts a floating point number into engineering units such as pico, micro, milli, etc.
     * @param time floating point value to be converted to engineering notation.
     * @param unit a unit string to append to the result (null for none).
     * @param precpower decimal power of necessary time precision.
     * Use a very large number to ignore this factor (9999).
     */
    private static class ConversionRange {

        String postfix;
        int power;
        double scale;

        ConversionRange(String p, int pow, double s) {
            postfix = p;
            power = pow;
            scale = s;
        }
    }
    private static ConversionRange[] allRanges = new ConversionRange[]{
        // Although the extremes (yocto and yotta) are defined in the literature,
        // they aren't common in circuits (at this time) and so they are commented out.
        // Add them and more as their use in circuitry becomes common.
        //		new ConversionRange("y", -24, 1.0E27),		// yocto
        new ConversionRange("z", -21, 1.0E24), // zepto
        new ConversionRange("a", -18, 1.0E21), // atto
        new ConversionRange("f", -15, 1.0E18), // femto
        new ConversionRange("p", -12, 1.0E15), // pico
        new ConversionRange("n", -9, 1.0E12), // nano
        new ConversionRange("u", -6, 1.0E9), // micro
        new ConversionRange("m", -3, 1.0E6), // milli
        new ConversionRange("", 0, 1.0E3), // no scale
        new ConversionRange("k", 3, 1.0E0), // kilo
        new ConversionRange("M", 6, 1.0E-3), // mega
        new ConversionRange("G", 9, 1.0E-6), // giga
        new ConversionRange("T", 12, 1.0E-9), // tera
        new ConversionRange("P", 15, 1.0E-12), // peta
        new ConversionRange("E", 18, 1.0E-15), // exa
        new ConversionRange("Z", 21, 1.0E-18) // zetta
    //		new ConversionRange("Y",  24, 1.0E-21)		// yotta
    };
    private static final double LOOKS_LIKE_ZERO = 1.0 / (allRanges[0].scale * 1.0E4);
    private static final double SMALLEST_JUST_PRINT = 1.0 / (allRanges[0].scale * 1.0);
    private static final double LARGEST_JUST_PRINT = 1.0 / allRanges[allRanges.length - 1].scale * 1.0E4;

    public static String convertToEngineeringNotation(double time, String unit, int precpower) {
        String negative = "";
        if (time < 0.0) {
            negative = "-";
            time = -time;
        }
        String unitPostfix = unit;
        if (unitPostfix == null) {
            unitPostfix = "";
        }

        // if the value is too tiny, just call it zero
        if (time < LOOKS_LIKE_ZERO) {
            return "0" + unitPostfix;
        }

        // if the value is out of range, use normal formatting for it
        if (time < SMALLEST_JUST_PRINT || time >= LARGEST_JUST_PRINT) {
            return negative + TextUtils.formatDouble(time) + unitPostfix;
        }

        // get proper time unit to use
        String secType = "";
        int rangePos = -1;
        long intTime = 0;
        double scaled = 0;
        for (int i = 0; i < allRanges.length; i++) {
            scaled = time * allRanges[i].scale;
            intTime = Math.round(scaled);
            if (i == allRanges.length - 1 || (scaled < 2000000.0 && intTime < 100000)) {
                if (unit == null) {
                    if (allRanges[i].power != 0) {
                        secType = "e" + allRanges[i].power;
                    }
                } else {
                    secType = allRanges[i].postfix + unitPostfix;
                }
                rangePos = i;
                break;
            }
        }

        if (precpower >= allRanges[rangePos].power) {
            // if the value is too tiny, just call it zero
            if (precpower < 1000 && precpower - 4 > allRanges[rangePos].power) {
                return "0" + unitPostfix;
            }

            long timeleft = intTime / 1000;
            long timeright = intTime % 1000;
            if (timeright == 0) {
                return negative + timeleft + secType;
            }
            if ((timeright % 100) == 0) {
                return negative + timeleft + "." + timeright / 100 + secType;
            }
            if ((timeright % 10) == 0) {
                String tensDigit = "";
                if (timeright < 100) {
                    tensDigit = "0";
                }
                return negative + timeleft + "." + tensDigit + timeright / 10 + secType;
            }
            String tensDigit = "";
            if (timeright < 10) {
                tensDigit = "00";
            } else if (timeright < 100) {
                tensDigit = "0";
            }
            return negative + timeleft + "." + tensDigit + timeright + secType;
        }

        // does not fit into 3-digit range easily: drop down a factor of 1000 and use bigger numbers
        int digits = allRanges[rangePos].power - precpower;
        if (rangePos > 0) {
            rangePos--;
            if (unit == null) {
                if (allRanges[rangePos].power != 0) {
                    secType = "e" + allRanges[rangePos].power;
                }
            } else {
                secType = allRanges[rangePos].postfix + unitPostfix;
            }
            digits += 3;
        } else {
            scaled /= 1000;
        }
        String numPart = TextUtils.formatDouble(scaled, digits);
        if (numPart.indexOf('.') >= 0) {
            while (numPart.endsWith("0")) {
                numPart = numPart.substring(0, numPart.length() - 1);
            }
            if (numPart.endsWith(".")) {
                numPart = numPart.substring(0, numPart.length() - 1);
            }
        }
        return negative + numPart + secType;
    }

    /**
     * Method to convert an integer to a string that is left-padded with spaces
     * @param value the integer value.
     * @param width the minimum field width.
     * If the result is less than this, extra spaces are added to the beginning.
     * @return a string describing the integer.
     */
    public static String toBlankPaddedString(int value, int width) {
        String msg = Integer.toString(value);
        while (msg.length() < width) {
            msg = " " + msg;
        }
        return msg;
    }

    /**
     * Method to determine whether or not a string is a number.
     * This method allows hexadecimal numbers as well as those with exponents.
     * @param pp the string to test.
     * @return true if it is a number.
     */
    public static boolean isANumber(String pp) {
        if (pp == null) {
            return false;
        }
        // ignore the minus sign
        int i = 0;
        int len = pp.length();
        if (i < len && (pp.charAt(i) == '+' || pp.charAt(i) == '-')) {
            i++;
        }

        // special case for hexadecimal prefix
        boolean xflag = false;
        if (i < len - 1 && pp.charAt(i) == '0' && (pp.charAt(i + 1) == 'x' || pp.charAt(i + 1) == 'X')) {
            i += 2;
            xflag = true;
        }

        boolean founddigits = false;
        if (xflag) {
            while (i < len && (TextUtils.isDigit(pp.charAt(i))
                    || pp.charAt(i) == 'a' || pp.charAt(i) == 'A'
                    || pp.charAt(i) == 'b' || pp.charAt(i) == 'B'
                    || pp.charAt(i) == 'c' || pp.charAt(i) == 'C'
                    || pp.charAt(i) == 'd' || pp.charAt(i) == 'D'
                    || pp.charAt(i) == 'e' || pp.charAt(i) == 'E'
                    || pp.charAt(i) == 'f' || pp.charAt(i) == 'F')) {
                i++;
                founddigits = true;
            }
        } else {
            while (i < len && (TextUtils.isDigit(pp.charAt(i)) || pp.charAt(i) == '.')) {
                if (pp.charAt(i) != '.') {
                    founddigits = true;
                }
                i++;
            }
        }
        if (!founddigits) {
            return false;
        }
        if (i == len) {
            return true;
        }

        // handle exponent of floating point numbers
        if (xflag) {
            return false;
        }
        if (pp.charAt(i) != 'e' && pp.charAt(i) != 'E') {
            return false;
        }
        i++;
        if (i == len) {
            return false;
        }
        if (pp.charAt(i) == '+' || pp.charAt(i) == '-') {
            i++;
        }
        if (i == len) {
            return false;
        }
        while (i < len && TextUtils.isDigit(pp.charAt(i))) {
            i++;
        }
        if (i == len) {
            return true;
        }

        return false;
    }

    /**
     * Method to determine whether or not a string is a postfix formatted
     * number, such as 1.02f.
     * @param pp the string to test.
     * @return true if it is a postfix number.
     */
    public static boolean isANumberPostFix(String pp) {
        // ignore the minus sign
        int i = 0;
        int len = pp.length();
        if (i < len && (pp.charAt(i) == '+' || pp.charAt(i) == '-')) {
            i++;
        }

        boolean founddigits = false;
        while (i < len && (TextUtils.isDigit(pp.charAt(i)) || pp.charAt(i) == '.')) {
            if (pp.charAt(i) != '.') {
                founddigits = true;
            }
            i++;
        }

        if (!founddigits) {
            return false;
        }
        if (i == len) {
            return true;
        }

        // handle post fix character (spice format)
        if (i + 1 == len) {
            char c = Character.toLowerCase(pp.charAt(i));
            if (c == 'g' || c == 'k' || c == 'm' || c == 'u'
                    || c == 'n' || c == 'p' || c == 'f') {
                return true;
            }
        } else if (pp.substring(i).toLowerCase().equals("meg")) {
            return true;
        }

        return false;
    }

    /**
     * Method to find a string inside of another string.
     * @param string the main string being searched.
     * @param search the string being located in the main string.
     * @param startingPos the starting position in the main string to look (0 to search the whole string).
     * @param caseSensitive true to do a case-sensitive search.
     * @param reverse true to search from the back of the string.
     * @return the position of the search string.  Returns negative if the string is not found.
     */
    public static int findStringInString(String string, String search, int startingPos, boolean caseSensitive, boolean reverse) {
        if (caseSensitive) {
            // case-sensitive search
            int i = 0;
            if (reverse) {
                i = string.lastIndexOf(search, startingPos);
            } else {
                i = string.indexOf(search, startingPos);
            }
            return i;
        }

        // case-insensitive search
        if (startingPos > 0) {
            string = string.substring(startingPos);
        }
        String stringLC = canonicString(string);
        String searchLC = canonicString(search);
        int i = 0;
        if (reverse) {
            i = stringLC.lastIndexOf(searchLC);
        } else {
            i = stringLC.indexOf(searchLC);
        }
        if (i >= 0) {
            i += startingPos;
        }
        return i;
    }

    /**
     * Method to break a line into keywords, separated by white space or comma
     * @param line the string to tokenize.
     * @param delim the delimiters.
     * @return an array of Strings for each keyword on the line.
     */
    public static String[] parseString(String line, String delim) {
        StringTokenizer st = new StringTokenizer(line, delim);
        int total = st.countTokens();
        String[] strings = new String[total];
        for (int i = 0; i < total; i++) {
            strings[i] = st.nextToken().trim();
        }
        return strings;
    }

    /**
     * Unit is a typesafe enum class that describes a unit scale (metric factors of 10).
     */
    public static class UnitScale {

        private final String name;
        private final int index;
        private final String postFix;
        private final Number multiplier;
        private final boolean ignoreCase; // to determine if postfix can be treated with equalsIgnoreCase()

        private UnitScale(String name, int index, String postFix, boolean ignoreCase, Number multiplier) {
            this.name = name;
            this.index = index;
            this.postFix = postFix;
            this.ignoreCase = ignoreCase;
            this.multiplier = multiplier;
        }

        /**
         * Method to return the name of this UnitScale.
         * The name can be prepended to a type, for example the name "Milli" can be put in front of "Meter".
         * @return the name of this UnitScale.
         */
        public String getName() {
            return name;
        }

        /**
         * Method to convert this UnitScale to an integer.
         * Used when storing these as preferences.
         * @return the index of this UnitScale.
         */
        public int getIndex() {
            return index;
        }

        /**
         * Get the string representing the postfix associated with this unit scale
         * @return the post fix string
         */
        public String getPostFix() {
            return postFix;
        }

        /**
         * Get the multiplier value associated with this unit scale.
         * @return the multiplier. May be an Integer (values >= 1) or a Double (values <= 1)
         */
        public Number getMultiplier() {
            return multiplier;
        }

        /**
         * Method to compare suffixes depending if ignoreCase matters.
         * @param suffix suffix to compare with UnitScale
         * @return true if they are equal
         */
        public boolean equalsTo(String suffix)
        {
            if (postFix.equals("")) return false; // ignore the NONE suffix case
            // postfix is same length or longer than string
            // assume that string has already extra characters representing the number. Not clear
        	int len = suffix.length();
            if (postFix.length() > len) return false;

            String sSuffix = suffix.substring(len-postFix.length(), len);

        	return (this.ignoreCase ? sSuffix.equalsIgnoreCase(postFix) : sSuffix.equals(postFix));
        }

        /**
         * Method to convert the index value to a UnitScale.
         * Used when storing these as preferences.
         * @param index the index of the UnitScale.
         * @return the indexed UnitScale.
         */
        public static UnitScale findFromIndex(int index) {
            int i = index - UNIT_BASE;
            if (i < 0 || i >= allUnits.length) {
                return NONE;
            }
            return allUnits[i];
        }

        public static UnitScale findUnitScaleFromPostfix(String str)
        {
    		// get list of scales, sorted by the length of the postfix string
        	if (sortedUnits == null)
        	{
	    		sortedUnits = new ArrayList<UnitScale>();
	    		for(int i=0; i<allUnits.length; i++) sortedUnits.add(allUnits[i]);
	    		Collections.sort(sortedUnits, new UnitsByPostfixLength());
        	}

        	for (UnitScale u : sortedUnits)
        		if (u.equalsTo(str)) return u;

        	// special case: ending in "M" means "MEG"
        	if (MEGA2.equalsTo(str)) return MEGA2;

        	// suffix unknown, return default
        	return NONE;
        }

        /**
         * Method to return a list of all scales.
         * @return an array of all scales.
         */
        public static UnitScale[] getUnitScales() {
            return allUnits;
        }

        /**
         * Returns a printable version of this Unit.
         * @return a printable version of this Unit.
         */
        public String toString() {
            return name;
        }
        /** The largest unit value. */
        private static final int UNIT_BASE = -7; // Zetta
        /** The smallest unit value. */
        private static final int UNIT_END = 5; // only until femto

        /** Describes zetta scale (1 sextillion, 10 to the 21st). Must match case. */
        public static final UnitScale ZETTA = new UnitScale("Zetta", -7, "Z",   false, new BigInteger("1000000000000000000000"));
        /** Describes exa scale (1 quintillion, 10 to the 18th). */
        public static final UnitScale EXA   = new UnitScale("Exa",   -6, "E",   true,  new Long("1000000000000000000"));
        /** Describes peta scale (1 quadrillion, 10 to the 15th). Must match case. */
        public static final UnitScale PETA  = new UnitScale("Peta",  -5, "P",   false, new Long("1000000000000000"));
        /** Describes tera scale (1 trillion, 10 to the 12th). */
        public static final UnitScale TERA  = new UnitScale("Tera",  -4, "T",   true,  new Long("1000000000000"));
        /** Describes giga scale (1 billion, 10 to the 9th). */
        public static final UnitScale GIGA  = new UnitScale("Giga",  -3, "G",   true,  new Integer(1000000000));
        /** Describes mega scale (1 million, 10 to the 6th). */
        public static final UnitScale MEGA  = new UnitScale("Mega",  -2, "MEG", true,  new Integer(1000000));
        /** Describes mega scale (1 million, 10 to the 6th). Must match case. */
        public static final UnitScale MEGA2 = new UnitScale("Mega",  -2, "M",   false, new Integer(1000000));
        /** Describes kilo scale (1 thousand, 10 to the 3rd). */
        public static final UnitScale KILO  = new UnitScale("Kilo",  -1, "k",   true,  new Integer(1000));
        /** Describes unit scale (1). */
        public static final UnitScale NONE  = new UnitScale("",       0, "",    true,  new Integer(1));
        /** Describes milli scale (1 thousandth, 10 to the -3rd). Must match case. */
        public static final UnitScale MILLI = new UnitScale("Milli",  1, "m",   false, new Double(0.001));
        /** Describes micro scale (1 millionth, 10 to the -6th). */
        public static final UnitScale MICRO = new UnitScale("Micro",  2, "u",   true,  new Double(0.000001));
        /** Describes nano scale (1 billionth, 10 to the -9th). */
        public static final UnitScale NANO  = new UnitScale("Nano",   3, "n",   true,  new Double(0.000000001));
        /** Describes pico scale (1 trillionth, 10 to the -12th). Must match case. */
        public static final UnitScale PICO  = new UnitScale("Pico",   4, "p",   false, new Double(0.000000000001));
        /** Describes femto scale (1 quadrillionth, 10 to the -15th). */
        public static final UnitScale FEMTO = new UnitScale("Femto",  5, "f",   true,  new Double(0.000000000000001));
        /** Describes atto scale (1 quintillionth, 10 to the -18th). */
        public static final UnitScale ATTO  = new UnitScale("Atto",   6, "a",   true,  new Double(0.000000000000000001));
        /** Describes zepto scale (1 sextillionth, 10 to the -21st). Must match case. */
        public static final UnitScale ZEPTO = new UnitScale("Zepto",  7, "z",   false, new Double(0.000000000000000000001));
//      /** Describes yocto scale (1 septillionth, 10 to the -24th). */
//      public static final UnitScale YOCTO = new UnitScale("Yocto",  8, "y",   true,  new Double(0.000000000000000000000001));
        private final static UnitScale[] allUnits = {
        	ZETTA, EXA, PETA, TERA, GIGA, MEGA, KILO, NONE, MILLI, MICRO, NANO, PICO, FEMTO, ATTO, ZEPTO
        };
        private static List<UnitScale> sortedUnits = null;
    }

	/**
	 * Comparator class for sorting Units by the length of their postfix.
	 */
	private static class UnitsByPostfixLength implements Comparator<TextUtils.UnitScale>
	{
		/**
		 * Method to sort Units by their postfix length.
		 */
		public int compare(TextUtils.UnitScale u1, TextUtils.UnitScale u2)
		{
			String s1 = u1.getPostFix();
			String s2 = u2.getPostFix();
			return s2.length()- s1.length();
		}
	}

    /**
     * Try to parse the user input String s into a Number. Conversion into the following formats
     * is tried in order. If a conversion is successful, that object is returned.
     * If no conversions are successful, this throws a NumberFormatException.
     * This method removes any UnitScale postfix, and scales the number accordingly.
     * No characters in the string are ignored - the string in its entirety (except removed postfix) must be
     * able to be parsed into the Number by the usual Integer.parseInt(), Double.parseDouble() methods.
     * <P>Formats: Integer, Long, Double
     * @param s the string to parse.
     * @param us the UnitScale to presume if none are given (null for no scaling).
     * @return a Number that represents the string in its entirety
     * @throws NumberFormatException if the String is not a parsable Number.
     */
    public static Number parsePostFixNumber(String s, UnitScale us) throws NumberFormatException {
        // remove character denoting multiplier at end, if any

        // remove commas that denote 1000's separators
        s = s.replaceAll(",", "");

        Number n = null;									// the number
        Number m = null;									// the multiplier
        if (us != null) {
            m = us.getMultiplier();
        }

        UnitScale u = UnitScale.findUnitScaleFromPostfix(s);
        if (u != UnitScale.NONE)
        {
            m = u.getMultiplier();
            String sub = s.substring(0, s.length() - u.getPostFix().length());
            // try to convert substring to a number
            try {
                n = parseNumber(sub);
            } catch (NumberFormatException e) {}
        }

        // if no valid postfix found, just parse number
        if (n == null) {
            n = parseNumber(s);
        }

        if (m != null) {
            if ((m instanceof Integer) && (m.intValue() == 1)) {
                return n;
            }

            if ((n instanceof Integer) && (m instanceof Integer)) {
                return new Integer(n.intValue() * m.intValue());
            }
            if ((n instanceof Long) && (m instanceof Integer)) {
                return new Long(n.longValue() * m.longValue());
            }
            return new Double(n.doubleValue() * m.doubleValue());
        }
        return n;
    }

    /**
     * Try to parse the user input String s into a Number. Conversion into the following formats
     * is tried in order. If a conversion is successful, that object is returned.
     * If no conversions are successful, this throws a NumberFormatException.
     * No characters in the string are ignored - the string in its entirety must be
     * able to be parsed into the Number by the usual Integer.parseInt(), Double.parseDouble() methods.
     * <P>Formats: Integer, Long, Double
     * @param s the string to parse
     * @return a Number that represents the string in its entirety
     * @throws NumberFormatException if the String is not a parsable Number.
     */
    private static Number parseNumber(String s) throws NumberFormatException {
        Number n = null;
        try {
            n = new Integer(s);
        } catch (NumberFormatException e) {
            // elib format does not know what a Long is
            //try {
            //	n = new Long(s);
            //} catch (NumberFormatException ee) {
            try {
                n = new Double(s);
            } catch (NumberFormatException eee) {
            }
            //}
        }
        if (n == null) {
            NumberFormatException e = new NumberFormatException(s + "cannot be parsed into a Number");
            throw e;
        }
        return n;
    }

    /**
     * Method to print a very long string.
     * The string is broken sensibly.
     */
    public static void printLongString(String str) {
        String prefix = "";
        while (str.length() > 80) {
            int i = 80;
            for (; i > 0; i--) {
                if (str.charAt(i) == ' ' || str.charAt(i) == ',') {
                    break;
                }
            }
            if (i <= 0) {
                i = 80;
            }
            if (str.charAt(i) == ',') {
                i++;
            }
            System.out.println(prefix + str.substring(0, i));
            if (str.charAt(i) == ' ') {
                i++;
            }
            str = str.substring(i);
            prefix = "   ";
        }
        System.out.println(prefix + str);
    }

//	/**
//	 * Method to compare two names and give a sort order.
//	 * The comparison considers numbers in numeric order so that the
//	 * string "in10" comes after the string "in9".
//	 *
//	 * Formal definition of order.
//	 * Lets insert in string's character sequence number at start of digit sequences.
//	 * Consider that numbers in the sequence are less than chars.
//	 *
//	 * Examples below are in increasing order:
//	 *   ""           { }
//	 *   "0"          {  0, '0' }
//	 *   "9"          {  9, '9' }
//	 *   "10"         { 10, '1', '0' }
//	 *   "2147483648" { 2147483648, '2', '1', '4', '7', '4', '8', '3', '6', '4', '8' }
//	 *   " "          { ' ' }
//	 *   "-"          { '-' }
//	 *   "-1"         { '-', 1, '1' }
//	 *   "-2"         { '-', 2, '2' }
//	 *   "a"          { 'a' }
//	 *   "a0"         { 'a',  0, '0' }
//	 *   "a0-0"       { 'a',  0, '0', '-', 0, '0' }
//	 *   "a00"        { 'a',  0, '0', '0' }
//	 *   "a0a"        { 'a',  0, '0', 'a' }
//	 *   "a01"        { 'a',  1, '0', '1' }
//	 *   "a1"         { 'a',  1, '1' }
//	 *   "in"         { 'i', 'n' }
//	 *   "in1"        { 'i', 'n',  1, '1' }
//	 *   "in1a"       { 'i', 'n',  1, '1', 'a' }
//	 *   "in9"        { 'i', 'n',  9, '9' }
//	 *   "in10"       { 'i', 'n', 10, '1', '0' }
//	 *   "in!"        { 'i', 'n', '!' }
//	 *   "ina"        { 'i , 'n', 'a' }
//	 *
//	 * @param name1 the first string.
//	 * @param name2 the second string.
//	 * @return 0 if they are equal, nonzero according to order.
//	 */
//	public static int nameSameNumeric(String name1, String name2) {
//		return STRING_NUMBER_ORDER.compare(name1, name2);
//	}
    /**
     * Method to set the string stored on the system clipboard.
     * @param text the new text for the clipboard. If text is null, the contents is clean.
     */
    public static void setTextOnClipboard(String text) {
        // put the text in the clipboard
        java.awt.datatransfer.Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
        Transferable transferable = new StringSelection(text);
        cb.setContents(transferable, null);
    }

    /**
     * Method for obtaining the string on the system clipboard.
     * @return the string on the system clipboard (returns a null string if nothing is found).
     */
    public static String getTextOnClipboard() {
        String result = null;
        try {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            Transferable contents = clipboard.getContents(null);
            
            if (contents != null && contents.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                result = (String) contents.getTransferData(DataFlavor.stringFlavor);
            }
        } catch (UnsupportedFlavorException ex) {
            ex.printStackTrace();
        } catch (IOException ex) {
            // known problem on MacOSX
            String osName = System.getProperty("os.name").toLowerCase();
            boolean isOSMac = osName.startsWith("mac");
            if (!isOSMac || 
            		!(ex.getMessage().toLowerCase().contains("system clipboard data unavailable") ||
            		  ex.getMessage().toLowerCase().contains("font transform has nan position")))
            {
        		System.out.println("Error in Clipboard: '" + ex.getMessage() + "");
            	if (Job.getDebug())
            		ex.printStackTrace();
            }
        }
        return result;
    }

    public static PrintWriter openPrintWriterFromFileName(String fileP, boolean printMsg)
    {
    	PrintWriter printWriter = null;
	    try
		{
	        //printWriter = new PrintWriter(new BufferedWriter(new FileWriter(fileP)));
	        // Extra step with URL is needed for MacOSX paths.
	        URL fileURL = TextUtils.makeURLToFile(fileP);
	        printWriter = new PrintWriter(new BufferedWriter(new FileWriter(TextUtils.getFile(fileURL))));
	    } catch (IOException e)
		{
	    	if (printMsg)
	    		System.out.println("Error opening " + fileP+": "+e.getMessage());
	    }
	    return printWriter;
    }

    /**
     * Method to convert a file path to a URL.
     * @param fileName the path to the file.
     * @return the URL to that file (null on error).
     */
    public static URL makeURLToFile(String fileName) {
        if (fileName.startsWith("file://")) {
            fileName = fileName.substring(6);
        }
        if (fileName.startsWith("file:/")) {
            fileName = fileName.substring(5);
        }

//        // fix file names with spaces encoded (for example "%20")
//        try {
//            fileName = URLDecoder.decode(fileName, "UTF-8");
//        } catch (UnsupportedEncodingException e) {
//        }
        fileName = decodeString(fileName);

        File file = new File(fileName);
        try {
            return file.toURI().toURL();
//			return file.toURL(); // deprecated in Java 1.6
        } catch (java.net.MalformedURLException e) {
            System.out.println("Cannot find file " + fileName);
        }
        return null;
    }

    /**
     * Method to decode names with spaces encoded  (for example "%20")
     * @param fileName Original file name
     * @return modified file name
     */
    public static String decodeString(String fileName)
    {
        // fix file names with spaces encoded (for example "%20")
        try {
            fileName = URLDecoder.decode(fileName, "UTF-8");
        } catch (UnsupportedEncodingException e) {
        }
        return fileName;
    }

    /**
     * Get the file for the URL. The code
     * <code>
     * new File(url.getPath())
     * </code>
     * returns an illegal leading slash on windows,
     * and has forward slashes instead of back slashes.
     * This method generates the correct File using
     * <code>
     * new File(url.toURI())
     * </code>
     * <P>
     * use <code>getPath()</code> on the returned File
     * to get the correct String file path.
     * <P>
     * This should only be needed when running an external process under
     * windows with command line arguments containing file paths. Otherwise,
     * the Java IO code does the correct conversion.
     *
     * @param url the URL to convert to a File.
     * @return the File.  Will return null if
     * URL does not point to a file.
     */
    public static File getFile(URL url) {
        try {
            return new File(url.toURI());
        } catch (java.net.URISyntaxException e) {
            System.out.println("URL -> File conversion error: " + e.getMessage());
            return new File(url.getPath());
        } catch (java.lang.IllegalArgumentException e) {
            // Libraries available in the jar fall in this case. Error message only in debug mode
//        	if (Job.getDebug())
//        		System.out.println("URL -> File conversion error: " + e.getMessage());
            return null;
        }
    }

    /**
     * Method to convert a URL to a string.
     * @param url the URL
     * @return a String that is the path to that URL.
     */
    public static String URLtoString(URL url) {
        if (url == null) {
            System.out.println("Null URL in TextUtils.URLtoString");
            return "";
        }

        String filePath = url.getFile();

        // use proper URI to ensure valid path name
        try {
            URI uri = new URI(filePath);
            filePath = uri.getPath();
        } catch (URISyntaxException e) {
        }

        filePath = decodeString(filePath);
//        // fix encoded file names (for example, with "%20" instead of spaces)
//        try {
//            filePath = URLDecoder.decode(filePath, "UTF-8");
//        } catch (UnsupportedEncodingException e) {
//        }

        return filePath;
    }

    /**
     * Method to return the directory path part of a URL (excluding the file name).
     * For example, the URL "file:/users/strubin/gates.elib" has the directory part "/users/strubin/".
     * @param url the URL to the file.
     * @return the directory path part (including the trailing "/", ":", or "\").
     * If there is no directory part, returns "".
     */
    public static String getFilePath(URL url) {
        if (url == null) {
            return "";
        }
        String filePath = URLtoString(url);

        // special case of .delib files, which are directories, but we want them to appear as files
        File file = new File(filePath);
        if (file.getName().toLowerCase().endsWith(".delib")) {
            filePath = file.getPath();
        }
        //if (filePath.toLowerCase().endsWith(".delib"+File.separator))
        //	filePath = filePath.substring(0, filePath.length()-1);  // remove trailing '/'
        int backSlashPos = filePath.lastIndexOf('\\');
        int colonPos = filePath.lastIndexOf(':');
        int slashPos = filePath.lastIndexOf('/');
        int charPos = Math.max(backSlashPos, Math.max(colonPos, slashPos));
        if (charPos < 0) {
            return "";
        }
        return filePath.substring(0, charPos + 1);
    }

    /**
     * Method to return the pure file name of a URL.
     * The pure file name excludes the directory path and the extension.
     * It is used to find the library name from a URL.
     * For example, the URL "file:/users/strubin/gates.elib" has the pure file name "gates".
     * @param url the URL to the file.
     * @return the pure file name.
     */
    public static String getFileNameWithoutExtension(URL url) {
        return getFileNameWithoutExtension(URLtoString(url), false);
    }

    /**
     * Method to return the pure file name of a file path.
     * The pure file name excludes the directory path and the extension.
     * It is used to find the library name from a file path.
     * For example, the file path "file:/users/strubin/gates.elib" has the pure file name "gates".
     * @param fileName full name of file.
     * @return the pure file name.
     */
    public static String getFileNameWithoutExtension(String fileName) {
    	return getFileNameWithoutExtension(fileName, false);
    }

    /**
     * Method to return the pure file name of a file path.
     * The pure file name excludes the directory path and the extension.
     * It is used to find the library name from a file path.
     * For example, the file path "file:/users/strubin/gates.elib" has the pure file name "gates".
     * @param fileName full name of file.
     * @param onlyElectricLibExtensions true to insist that the extension be only ".elib", ".jelib", or ".txt".
     * All other extensions are not stripped-off.
     * @return the pure file name.
     */
    public static String getFileNameWithoutExtension(String fileName, boolean onlyElectricLibExtensions) {
        fileName = getFileNameWithoutPath(fileName);

        int dotPos = fileName.lastIndexOf('.');
        if (dotPos >= 0) {
        	if (onlyElectricLibExtensions) {
        		String extension = fileName.substring(dotPos+1).toLowerCase();
        		if (extension.equals("jelib") || extension.equals("elib") || extension.equals("txt"))
                    fileName = fileName.substring(0, dotPos);
        	} else {
        		fileName = fileName.substring(0, dotPos);
        	}
        }

        // make sure the file name is legal
        StringBuffer buf = null;
        for (int i = 0; i < fileName.length(); i++) {
            char ch = fileName.charAt(i);
            if (isBadCellNameCharacter(ch)) {
                if (buf == null) {
                    buf = new StringBuffer();
                    buf.append(fileName.substring(0, i));
                }
                buf.append('-');
                continue;
            } else if (buf != null) {
                buf.append(ch);
            }
        }
        if (buf != null) {
            String newS = buf.toString();
            System.out.println("File name " + fileName + " was converted to " + newS);
            return newS;
        }
        return fileName;
    }

    /**
     * Method to strip the path from a file path.
     * For example, the file path "file:/users/strubin/gates.jelib" becomes "gates.jelib".
     * @param fileName original file path, including directory.
     * @return pure file name without directory path information.
     */
    public static String getFileNameWithoutPath(String fileName) {
        while (fileName.endsWith("\\") || fileName.endsWith(":") || fileName.endsWith("/")) {
            fileName = fileName.substring(0, fileName.length() - 1);
        }
        int backSlashPos = fileName.lastIndexOf('\\');
        int colonPos = fileName.lastIndexOf(':');
        int slashPos = fileName.lastIndexOf('/');
        int charPos = Math.max(backSlashPos, Math.max(colonPos, slashPos));
        if (charPos >= 0) {
            fileName = fileName.substring(charPos + 1);
        }
        return fileName;
    }

    /**
     * Method to return the extension of the file in a URL.
     * The extension is the part after the last dot.
     * For example, the URL "file:/users/strubin/gates.elib" has the extension "elib".
     * @param url the URL to the file.
     * @return the extension of the file ("" if none).
     */
    public static String getExtension(URL url) {
        if (url == null) {
            return "";
        }
        String fileName = URLtoString(url);
        return getExtension(fileName);
    }

    /**
     * Method to return the extension of the file.
     * The extension is the part after the last dot.
     * For example, the URL "file:/users/strubin/gates.elib" has the extension "elib".
     * @param fileName String containing the file name.
     * @return the extension of the file ("" if none).
     */
    public static String getExtension(String fileName) {
        if (fileName.endsWith("/")) { // to handle "XXX.delib/"
            fileName = fileName.substring(0, fileName.length() - 1);
        }
        int dotPos = fileName.lastIndexOf('.');
        if (dotPos < 0) {
            return "";
        }
        return fileName.substring(dotPos + 1);
    }

    /**
     * Method to open an input stream to a URL.
     * @param url the URL to the file.
     * @return the InputStream, or null if the file cannot be found.
     */
    public static InputStream getURLStream(URL url) {
        return getURLStream(url, null);
    }

    /**
     * Method to open an input stream to a URL.
     * @param url the URL to the file.
     * @param errorMsg a string buffer in which to print any error message. If null,
     * any error message is printed to System.out
     * @return the InputStream, or null if the file cannot be found.
     */
    public static InputStream getURLStream(URL url, StringBuffer errorMsg) {
        if (url != null) {
            try {
                return url.openStream();
            } catch (IOException e) {
                if (errorMsg != null) {
                    errorMsg.append("Error: cannot open " + e.getMessage() + "\n");
                } else {
                    System.out.println("Error: cannot open " + e.getMessage());
                }
            }
        }
        return null;
    }

    /**
     * Method to tell whether a given URL exists.
     * @param url the URL in question.
     * @return true if the file exists.
     */
    public static boolean URLExists(URL url) {
        return URLExists(url, null);
    }

    /**
     * Method to tell whether a given URL exists.
     * @param url the URL in question.
     * @param errorMsg a string builder in which to print any error message.
     * If null, errors are not printed.
     * @return true if the file exists.
     */
    public static boolean URLExists(URL url, StringBuilder errorMsg) {
        if (url == null) {
            return false;
        }

        try {
            URLConnection con = url.openConnection();
            con.connect();
//            int conLength = con.getContentLength();
            String conType = con.getContentType();
            con.getInputStream().close();
//            if (conLength < 0) {
        	if (conType == null) {
                if (errorMsg != null) {
                    errorMsg.append("Error: cannot open due to invalid content length detected in input file \n");
                }
                return false;
            }
        } catch (Exception e) {
            if (errorMsg != null) {
                errorMsg.append("Error: cannot open " + e.getMessage() + "\n");
            }
            return false;
        }
        return true;
    }

    /**
     * Method to examine a path and return all resources found there.
     * @param resourceName the path to examine.
     * @return a List of all resource names found there.
     */
    public static List<String> getAllResources(String resourceName) {
        List<String> retval = new ArrayList<String>();
        String cp = System.getProperty("java.class.path", ".");
        String[] cpElements = cp.split(File.pathSeparator);
        for (String cpElement : cpElements) {
            String classPath = cpElement + File.separator + resourceName.replace('.', File.separatorChar);
            File file = new File(classPath);
            if (file.isDirectory()) {
                retval.addAll(getResourcesFromDirectory(file));
            } else {
                File jarFile = new File(cpElement);
                retval.addAll(getResourcesFromJarFile(jarFile, resourceName.replace('.', '/')));
            }
        }
        return retval;
    }

    private static List<String> getResourcesFromJarFile(File file, String resName) {
        List<String> retval = new ArrayList<String>();
        try {
            ZipFile zf = new ZipFile(file);
            Enumeration<? extends ZipEntry> e = zf.entries();
            while (e.hasMoreElements()) {
                ZipEntry ze = e.nextElement();
                String entry = ze.getName();
                if (entry.startsWith(resName)) {
                    retval.add(entry.substring(resName.length() + 1));
                }
            }
            zf.close();
        } catch (IOException e) {
        }
        return retval;
    }

    private static List<String> getResourcesFromDirectory(File directory) {
        List<String> retval = new ArrayList<String>();
        File[] fileList = directory.listFiles();
        for (File file : fileList) {
            if (file.isDirectory()) {
                retval.addAll(getResourcesFromDirectory(file));
            } else {
                String fileName = file.getName();
                retval.add(fileName);
            }
        }
        return retval;
    }
    /****************************** FOR SORTING OBJECTS ******************************/
    /**
     * A comparator object for sorting Strings that may have numbers in them.
     * Created once because it is used often.
     */
    public static final Comparator<String> STRING_NUMBER_ORDER = new Comparator<String>() {

        /**
         * Method to compare two names and give a sort order.
         * The comparison considers numbers in numeric order so that the
         * string "in10" comes after the string "in9".
         *
         * Formal definition of order.
         * Lets insert in string's character sequence number at start of digit sequences.
         * Consider that numbers in the sequence are less than chars.
         *
         * Examples below are in increasing order:
         *   ""           { }
         *   "0"          {  0, '0' }
         *   "9"          {  9, '9' }
         *   "10"         { 10, '1', '0' }
         *   "2147483648" { 2147483648, '2', '1', '4', '7', '4', '8', '3', '6', '4', '8' }
         *   " "          { ' ' }
         *   "-"          { '-' }
         *   "-1"         { '-', 1, '1' }
         *   "-2"         { '-', 2, '2' }
         *   "a"          { 'a' }
         *   "a0"         { 'a',  0, '0' }
         *   "a0-0"       { 'a',  0, '0', '-', 0, '0' }
         *   "a00"        { 'a',  0, '0', '0' }
         *   "a0a"        { 'a',  0, '0', 'a' }
         *   "a01"        { 'a',  1, '0', '1' }
         *   "a1"         { 'a',  1, '1' }
         *   "a[1]"       { 'a', '[', 1, '1', ']' }
         *   "a[10]"      { 'a', '[', 10, '1', '0', ']' }
         *   "in"         { 'i', 'n' }
         *   "in1"        { 'i', 'n',  1, '1' }
         *   "in1a"       { 'i', 'n',  1, '1', 'a' }
         *   "in9"        { 'i', 'n',  9, '9' }
         *   "in10"       { 'i', 'n', 10, '1', '0' }
         *   "in!"        { 'i', 'n', '!' }
         *   "ina"        { 'i , 'n', 'a' }
         *
         * @param o1 the first string.
         * @param o2 the second string.
         * @return 0 if they are equal, nonzero according to order.
         */
        public int compare(String name1, String name2) {
            int len1 = name1.length();
            int len2 = name2.length();
            int extent = Math.min(len1, len2);
            for (int pos = 0; pos < extent; pos++) {
                char ch1 = name1.charAt(pos);
                char ch2 = name2.charAt(pos);
                if (ch1 != ch2) {
                    int digit1 = digit(ch1);
                    int digit2 = digit(ch2);
                    if (digit1 >= 0 || digit2 >= 0) {
                        int pos1 = pos + 1, pos2 = pos + 1; // Positions in string to compare

                        // One char is digit, another is not. Is previous digit ?
                        int digit = pos > 0 ? digit(name1.charAt(--pos)) : -1;
                        if (digit < 0 && (digit1 < 0 || digit2 < 0)) {
                            // Previous is not digit. Number is less than non-number.
                            return digit2 - digit1;
                        }
                        // Are previous digits all zeros ?
                        while (digit == 0) {
                            digit = pos > 0 ? digit(name1.charAt(--pos)) : -1;
                        }
                        if (digit < 0) {
                            // All previous digits are zeros. Skip zeros further.
                            while (digit1 == 0) {
                                digit1 = pos1 < len1 ? digit(name1.charAt(pos1++)) : -1;
                            }
                            while (digit2 == 0) {
                                digit2 = pos2 < len2 ? digit(name2.charAt(pos2++)) : -1;
                            }
                        }

                        // skip matching digits
                        while (digit1 == digit2 && digit1 >= 0) {
                            digit1 = pos1 < len1 ? digit(name1.charAt(pos1++)) : -1;
                            digit2 = pos2 < len2 ? digit(name2.charAt(pos2++)) : -1;
                        }

                        boolean dig1 = digit1 >= 0;
                        boolean dig2 = digit2 >= 0;
                        for (int i = 0; dig1 && dig2; i++) {
                            dig1 = pos1 + i < len1 && digit(name1.charAt(pos1 + i)) >= 0;
                            dig2 = pos2 + i < len2 && digit(name2.charAt(pos2 + i)) >= 0;
                        }
                        if (dig1 != dig2) {
                            return dig1 ? 1 : -1;
                        }
                        if (digit1 != digit2) {
                            return digit1 - digit2;
                        }
                    }
                    return ch1 - ch2;
                }
            }
            return len1 - len2;
        }
    };

    private static int digit(char ch) {
        if (ch < '\u0080') {
            return ch >= '0' && ch <= '9' ? ch - '0' : -1;
        }
        return Character.digit((int) ch, 10);
    }

//	/**
//	 * Test of STRING_NUMBER_ORDER.
//	 */
// 	private static String[] numericStrings = {
// 		"",           // { }
// 		"0",          // {  0, '0' }
// 		"0-0",        // {  0, '0', '-', 0, '0' }
// 		"00",         // {  0, '0', '0' }
// 		"0a",         // {  0, '0', 'a' }
// 		"01",         // {  1, '0', '1' }
// 		"1",          // {  1, '1' }
// 		"9",          // {  9, '9' }
// 		"10",         // { 10, '1', '0' }
// 		"12",         // { 12, '1', '2' }
// 		"102",        // { 102, '1', '0', '2' }
// 		"2147483648", // { 2147483648, '2', '1', '4', '7', '4', '8', '3', '6', '4', '8' }
// 		" ",          // { ' ' }
// 		"-",          // { '-' }
// 		"-1",         // { '-', 1, '1' }
// 		"-2",         // { '-', 2, '2' }
// 		"a",          // { 'a' }
// 		"a0",         // { 'a',  0, '0' }
// 		"a0-0",       // { 'a',  0, '0', '-', 0, '0' }
// 		"a00",        // { 'a',  0, '0', '0' }
// 		"a0a",        // { 'a',  0, '0', 'a' }
// 		"a01",        // { 'a',  1, '0', '1' }
// 		"a1",         // { 'a',  1, '1' }
//		"a[1]",       // { 'a', '[', 1, '1', ']' }
//		"a[10]",      // { 'a', '[', 10, '1', '0', ']' }
// 		"in",         // { 'i', 'n' }
// 		"in1",        // { 'i', 'n',  1, '1' }
// 		"in1a",       // { 'i', 'n',  1, '1', 'a' }
// 		"in9",        // { 'i', 'n',  9, '9' }
// 		"in10",       // { 'i', 'n', 10, '1', '0' }
// 		"in!",        // { 'i', 'n', '!' }
// 		"ina"         // { 'i , 'n', 'a' }
// 	};
//
// 	static {
// 		for (int i = 0; i < numericStrings.length; i++)
// 		{
// 			for (int j = 0; j < numericStrings.length; j++)
// 			{
// 				String s1 = numericStrings[i];
// 				String s2 = numericStrings[j];
// 				int cmp = STRING_NUMBER_ORDER.compare(s1, s2);
// 				if (i == j && cmp != 0 || i < j && cmp >= 0 || i > j && cmp <= 0)
// 					System.out.println("Error in TextUtils.nameSameNumeric(\"" +
// 						s1 + "\", \"" + s2 + "\") = " + cmp);
// 			}
// 		}
// 	}
    /**
     * Comparator class for sorting Objects by their string name.
     */
    public static class ObjectsByToString implements Comparator<Object> {

        /**
         * Method to sort Objects by their string name.
         */
        public int compare(Object o1, Object o2) {
            String s1 = o1.toString();
            String s2 = o2.toString();
            return s1.compareToIgnoreCase(s2);
        }
    }

    /**
     * Method to replace all special characters in the instance name coming from external files such as"/"..
     * @param n
     * @param onlyBrackets
     * @param correctBrackets
     * @return String where characters "/", "[", "]" are replaced by "_". "\" is removed.
     */
    public static String correctName(String n, boolean onlyBrackets, boolean correctBrackets) {
        int index;

        // removing brackets only if ] is not the last item in the string
        // It doesn't correct brackets if {a[1],b[2],c[3]} in VerilogReading
        if (correctBrackets) {
            index = n.indexOf("]");
            if (index != -1 && index < n.length() - 1) {
                n = n.replace('[', '-');
                n = n.replace("]", "-");
            }
        }
        if (onlyBrackets) {
            return n;
        }

        // First replace "/" for "-"
        index = n.indexOf("/");
        if (index != -1) {
            n = n.replaceAll("/", "_");
        }
        // Remove possible space character representing as \
//		index = n.indexOf("\\");
//		if (index != -1)
//		{
////            assert(false); // detect this before
//			n = n.substring(index+1);
//		}
        return n;
    }

    /**
     * Method to tell whether a character is invalid in Cell and Library names.
     * @param chr the character in question.
     * @return true if it is invalid; false if it is acceptable.
     */
	public static boolean isBadCellNameCharacter(char chr)
	{
	    if (Character.isWhitespace(chr) || chr == ':' || chr == ';' || 
	    		chr == '{' || chr == '}' || chr == '|' /*|| chr == '/'*/) return true;
	    return false;
	}

	/**
	 * Method to tell whether name is a valid layer or node name
	 * @param s string to validate
	 * @return true if the layer name is valid
	 */
	public static boolean isValidLayerName(String s)
	{
		return s.length() > 0 && s.indexOf('.') == -1;
	}
}
