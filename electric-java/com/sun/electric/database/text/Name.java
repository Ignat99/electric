/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Name.java
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
package com.sun.electric.database.text;

import com.sun.electric.util.TextUtils;
import java.util.Comparator;

/**
 * A Name is a text-parsing object for port, node and arc names.
 * These names can use bus notation:<BR>
 * <CENTER>name = username | tempname</CENTER>
 * <CENTER>username = itemname { ',' itemname }</CENTER>
 * <CENTER>itemname = simplename { '[' index ']' }</CENTER>
 * <CENTER>index = indexitem { ',' indexitem ']' }</CENTER>
 * <CENTER>indexitem = simplename | number [':' number]</CENTER><BR>
 * <CENTER>tempname = simplename '@' number </CENTER><BR>
 * <CENTER>simplename = string</CENTER><BR>
 * string doesn't contain '[', ']', ',', ':'.
 * Bus names are expanded into a list of subnames.
 */
public abstract class Name implements Comparable<Name> {

    /** Hash of Names */
    private static volatile Name[] allNames = new Name[1];
    /** count of allocated Names */
    private static int allNamesCount = 0;

    /**
     * Method to return the name object for this string.
     * @param ns given string
     * @return the name object for the string.
     */
    public static Name findName(String ns) {
        if (ns == null) {
            return null;
        }
        String ts = trim(ns);
        return NameImpl.newTrimmedName(ts, ts == ns);
    }

    /**
     * Method to check whether or not string is a valid name.
     * @param ns given string
     * @return the error description or null if string is correct name.
     */
    public static String checkName(String ns) {
        try {
            int flags = checkNameThrow(ns);
            if ((flags & HAS_EMPTIES) != 0) {
                return "has empty subnames";
            }
            return null;
        } catch (NumberFormatException e) {
            return e.getMessage();
        }
    }

    /**
     * Print statistics about Names.
     */
    public static void printStatistics() {
        int validNames = 0;
        int userNames = 0;
        int busCount = 0;
        int busWidth = 0;
        long length = 0;
        for (Name n : allNames) {
            if (n == null) {
                continue;
            }
            length += n.toString().length();
            if (n.isValid()) {
                validNames++;
            }
            if (!n.isTempname()) {
                userNames++;
            }
            if (n.isBus()) {
                busCount++;
                busWidth += n.busWidth();
            }
        }
        System.out.println(allNamesCount + " Names " + length + " chars. " + validNames + " valid " + userNames + " usernames "
                + busCount + " buses with " + busWidth + " elements.");
    }

    /**
     * Compares this Name with the specified Name for order.  Returns a
     * negative integer, zero, or a positive integer as this object is less
     * than, equal to, or greater than the specified object.<p>
     * @param   name the Name to be compared.
     * @return  a negative integer, zero, or a positive integer as this object
     *		is less than, equal to, or greater than the specified object.
     */
    @Override
    public abstract int compareTo(Name name);

    /**
     * Tells whether or not this Name is a valid bus or signal name.
     * @return true if Name is a valid name.
     */
    public abstract boolean isValid();

    /**
     * Tells whether or not this Name is a temporary name
     * @return true if Name is a temporary name.
     */
    public abstract boolean isTempname();

    /**
     * Tells whether Name has duplicate subnames.
     * @return true if Name has duplicate subnames.
     */
    public abstract boolean hasDuplicates();

    /**
     * Tells whether Name has duplicate subnames.
     * @return true if Name has duplicate subnames.
     */
    public abstract boolean hasEmptySubnames();

    /**
     * Tells whether or not this Name is a list of names separated by comma.
     * @return true if Name is a list of names separated by comma.
     */
    public abstract boolean isList();

    /**
     * Tells whether or not this Name is a bus name.
     * @return true if name is a bus name.
     */
    public abstract boolean isBus();

    /**
     * Returns subname of a bus name.
     * @param i an index of subname.
     * @return the view part of a parsed Cell name.
     */
    public abstract Name subname(int i);

    /**
     * Returns number of subnames of a bus.
     * @return the number of subnames of a bus.
     */
    public abstract int busWidth();

    /**
     * Returns basename of temporary Name.
     * Returns null if not temporary Name.
     * @return base of name.
     */
    public abstract Name getBasename();

    /**
     * Returns numerical suffix of temporary Name.
     * Returns -1 if not temporary name.
     * @return numerical suffix.
     */
    public abstract int getNumSuffix();

    /**
     * Returns the name obtained from base of this simple name by adding numerical suffix.
     * Returns null if name is not simple or if i is negative.
     * @param i numerical suffix
     * @return suffixed name.
     */
    public abstract Name findSuffixed(int i);
    
    /**
     * Comparator that compares Names by com.sun.electric.databes.text.TextUtils#STRING_NUMBER_ORDER
     */
    public static final Comparator<Name> STRING_NUMBER_ORDER = new Comparator<Name>() {

        @Override
        public int compare(Name o1, Name o2)
        {
            return TextUtils.STRING_NUMBER_ORDER.compare(o1.toString(), o2.toString());
        }
        
    };
    
    // ------------------ protected and private methods -----------------------
    protected static final int ERROR = 0x01;
    protected static final int LIST = 0x02;
    protected static final int BUS = 0x04;
    protected static final int SIMPLE = 0x08;
    protected static final int TEMP = 0x10;
    protected static final int DUPLICATES = 0x20;
    protected static final int HAS_EMPTIES = 0x40;

    /**
     * Returns the trimmed string for given string.
     * @param ns given string
     * @return trimmed string, or null if argument is null
     */
    private static String trim(String ns) {
        if (ns == null) {
            return null;
        }
        int len = ns.length();
        int newLen = 0;
        for (int i = 0; i < len; i++) {
            if (ns.charAt(i) > ' ') {
                newLen++;
            }
        }
        if (newLen == len) {
            return ns;
        }

        StringBuffer buf = new StringBuffer(newLen);
        for (int i = 0; i < len; i++) {
            if (ns.charAt(i) > ' ') {
                buf.append(ns.charAt(i));
            }
        }
        return buf.toString();
    }

    /**
     * Method to check whether or not string is a valid name.
     * Throws exception on invaliod string
     * @param ns given string
     * @return flags describing the string.
     */
    protected static int checkNameThrow(String ns) throws NumberFormatException {
        int flags = SIMPLE;

        int bracket = -1;
        boolean wasBrackets = false;
        int colon = -1;
        if (ns.length() == 0 || ns.charAt(ns.length() - 1) == ',') {
            flags |= HAS_EMPTIES;
        }
        for (int i = 0; i < ns.length(); i++) {
            char c = ns.charAt(i);
            if (bracket < 0) {
                colon = -1;
                if (c == ']') {
                    throw new NumberFormatException("unmatched ']' in name");
                }
                if (c == ':') {
                    throw new NumberFormatException("':' out of brackets");
                }
                if (c == '[') {
                    bracket = i;
                    flags &= ~SIMPLE;
                    if (i == 0 || ns.charAt(i - 1) == ',') {
                        flags |= HAS_EMPTIES;
                    }
                    wasBrackets = true;
                } else if (c == ',') {
                    flags |= (LIST | BUS);
                    flags &= ~SIMPLE;
                    if (i == 0 || ns.charAt(i - 1) == ',') {
                        flags |= HAS_EMPTIES;
                    }
                    wasBrackets = false;
                } else if (wasBrackets) {
                    throw new NumberFormatException("Wrong character after brackets");
                }
                if (c == '@') {
                    for (int j = i + 1; j < ns.length(); j++) {
                        char cj = ns.charAt(j);
                        if (cj < '0' || cj > '9') {
                            throw new NumberFormatException("Wrong number suffix in temporary name");
                        }
                    }
                    if (i == ns.length() - 1 || ns.charAt(i + 1) == '0' && i != ns.length() - 2) {
                        throw new NumberFormatException("Wrong temporary name");
                    }
                    if ((flags & SIMPLE) == 0) {
                        throw new NumberFormatException("list of temporary names");
                    }
                    Integer.parseInt(ns.substring(i + 1)); // throws exception on bad number
                    assert flags == SIMPLE;
                    return SIMPLE | TEMP;
                }
                continue;
            }
            if (c == '[') {
                throw new NumberFormatException("nested bracket '[' in name");
            }
            if (c == ':') {
                if (colon >= 0) {
                    throw new NumberFormatException("too many ':' inside brackets");
                }
                if (i == bracket + 1) {
                    throw new NumberFormatException("has missing start of index range");
                }
                if (ns.charAt(bracket + 1) == '-') {
                    throw new NumberFormatException("has negative start of index range");
                }
                for (int j = bracket + 1; j < i; j++) {
                    if (!TextUtils.isDigit(ns.charAt(j))) {
                        throw new NumberFormatException("has nonnumeric start of index range");
                    }
                }
                colon = i;
                flags |= BUS;
            }
            if (colon >= 0 && (c == ']' || c == ',')) {
                if (i == colon + 1) {
                    throw new NumberFormatException("has missing end of index range");
                }
                if (ns.charAt(colon + 1) == '-') {
                    throw new NumberFormatException("has negative end of index range");
                }
                for (int j = colon + 1; j < i; j++) {
                    if (!TextUtils.isDigit(ns.charAt(j))) {
                        throw new NumberFormatException("has nonnumeric end of index range");
                    }
                }
                if (Integer.parseInt(ns.substring(bracket + 1, colon)) == Integer.parseInt(ns.substring(colon + 1, i))) {
                    throw new NumberFormatException("has equal start and end indices");
                }
                colon = -1;
            }
            if (c == ']') {
                if (i == bracket + 1) {
                    flags |= HAS_EMPTIES;
                }
                bracket = -1;
            }
            if (c == ',') {
                if (i == bracket + 1) {
                    flags += HAS_EMPTIES;
                }
                bracket = i;
                flags |= BUS;
            }
            if (c == '@') {
                throw new NumberFormatException("'@' in brackets");
            }
        }
        if (bracket != -1) {
            throw new NumberFormatException("Unclosed bracket");
        }
        return flags;
    }
}
