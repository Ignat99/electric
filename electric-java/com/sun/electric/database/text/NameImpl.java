/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: NameImpl.java
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
import com.sun.electric.util.math.GenMath;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
class NameImpl extends Name {

    private static final boolean USE_STRING_NUMBER_ORDER = false;
    
    /** the original name */
    private final String ns;
    /** list of subnames */
    private NameImpl[] subnames;
    /** basename */
    private final NameImpl basename;
    /** numerical suffix */
    private final int numSuffix;
    /** the flags */
    private int flags;
    /** Hash of Names */
    private static volatile NameImpl[] allNames = new NameImpl[1];
    /** count of allocated Names */
    private static int allNamesCount = 0;

    /**
     * Returns a printable version of this Name.
     * @return a printable version of this Name.
     */
    @Override
    public final String toString() {
        return ns;
    }

    /**
     * Compares this Name with the specified Name for order.  Returns a
     * negative integer, zero, or a positive integer as this object is less
     * than, equal to, or greater than the specified object.<p>
     * @param   name the Name to be compared.
     * @return  a negative integer, zero, or a positive integer as this object
     *		is less than, equal to, or greater than the specified object.
     * @throw ClassCastException if argument has other implementation than this
     */
    @Override
    public int compareTo(Name name) {
        if (USE_STRING_NUMBER_ORDER) {
            return TextUtils.STRING_NUMBER_ORDER.compare(ns, ((NameImpl)name).ns);
        } else {
            return ns.compareTo(((NameImpl)name).ns);
        }
    }

    /**
     * Tells whether or not this Name is a valid bus or signal name.
     * @return true if Name is a valid name.
     */
    @Override
    public final boolean isValid() {
        return (flags & ERROR) == 0;
    }

    /**
     * Tells whether or not this Name is a temporary name
     * @return true if Name is a temporary name.
     */
    @Override
    public final boolean isTempname() {
        return (flags & TEMP) != 0;
    }

    /**
     * Tells whether Name has duplicate subnames.
     * @return true if Name has duplicate subnames.
     */
    @Override
    public boolean hasDuplicates() {
        return (flags & DUPLICATES) != 0;
    }

    /**
     * Tells whether Name has duplicate subnames.
     * @return true if Name has duplicate subnames.
     */
    @Override
    public boolean hasEmptySubnames() {
        return (flags & HAS_EMPTIES) != 0;
    }

    /**
     * Tells whether or not this Name is a list of names separated by comma.
     * @return true if Name is a list of names separated by comma.
     */
    @Override
    public boolean isList() {
        return (flags & LIST) != 0;
    }

    /**
     * Tells whether or not this Name is a bus name.
     * @return true if name is a bus name.
     */
    @Override
    public boolean isBus() {
        return subnames != null;
    }

    /**
     * Returns subname of a bus name.
     * @param i an index of subname.
     * @return the view part of a parsed Cell name.
     * @throws IndexOutOfBoudsEsxception if index is out of bounds
     */
    @Override
    public NameImpl subname(int i) {
        if (subnames == null) {
            if (i != 0) {
                throw new IndexOutOfBoundsException();
            }
            return this;
        } else {
            return subnames[i];
        }
    }

    /**
     * Returns number of subnames of a bus.
     * @return the number of subnames of a bus.
     */
    @Override
    public int busWidth() {
        return subnames == null ? 1 : subnames.length;
    }

    /**
     * Returns basename of temporary Name.
     * Returns null if not temporary Name.
     * @return base of name.
     */
    @Override
    public NameImpl getBasename() {
        return basename;
    }

    /**
     * Returns numerical suffix of temporary Name.
     * Returns -1 if not temporary name.
     * @return numerical suffix.
     */
    @Override
    public int getNumSuffix() {
        return numSuffix;
    }

    /**
     * Returns the name obtained from base of this simple name by adding numerical suffix.
     * Returns null if name is not simple or if i is negative.
     * @param i numerical suffix
     * @return suffixed name.
     */
    @Override
    public NameImpl findSuffixed(int i) {
        if (i < 0 || basename == null) {
            return null;
        }
        String basenameString = basename.ns.substring(0, basename.ns.length() - 1);
        return newTrimmedName(basenameString + i, true);
    }
    // ------------------ protected and private methods -----------------------

    /**
     * Returns the name object for this string, assuming that is is trimmed.
     * @param ns given trimmed string
     * @param clone true to clone on reallocation
     * @return the name object for the string.
     */
    /*private*/ static NameImpl newTrimmedName(String ns, boolean clone) {
        return findTrimmedName(ns, true, clone);
    }

    /**
     * Returns the name object for this string, assuming that is is trimmed.
     * @param ns given trimmed string
     * @param create true to allocate new name if not found
     * @param clone true to clone on reallocation
     * @return the name object for the string.
     */
    private static NameImpl findTrimmedName(String ns, boolean create, boolean clone) {
        // The allNames array is created in "rehash" method inside synchronized block.
        // "rehash" fills some entris leaving null in others.
        // All entries filled in rehash() are final.
        // However other threads may change initially null entries to non-null value.
        // This non-null value is final.
        // First we scan a sequence of non-null entries out of synchronized block.
        // It is guaranteed that we see the correct values of non-null entries.

        // Get poiner to hash array locally once to avoid many reads of volatile variable.
        NameImpl[] hash = allNames;

        // We shall try to search a sequence of non-null entries for CellUsage with our protoId.
        int i = ns.hashCode() & 0x7FFFFFFF;
        i %= hash.length;
        for (int j = 1; hash[i] != null; j += 2) {
            NameImpl n = hash[i];

            // We scanned a seqence of non-null entries and found the result.
            // It is correct to return it without synchronization.
            if (n.ns.equals(ns)) {
                return n;
            }

            i += j;
            if (i >= hash.length) {
                i -= hash.length;
            }
        }

        // Need to enter into the synchronized mode.
        synchronized (NameImpl.class) {

            if (hash == allNames && allNames[i] == null) {
                // There we no rehash during our search and the last null entry is really null.
                // So we can safely use results of unsynchronized search.
                if (!create) {
                    return null;
                }

                if (allNamesCount * 2 <= hash.length - 3) {
                    // create a new Name, if enough space in the hash
                    if (clone) {
                        ns = new String(ns);
                        clone = false;
                    }
                    NameImpl n = new NameImpl(ns);
                    if (hash != allNames || hash[i] != null) {
                        return newTrimmedName(ns, false);
                    }
                    hash[i] = n;
                    allNamesCount++;
                    return n;
                }
                // enlarge hash if not 
                rehash();
            }
            // retry in synchronized mode.
            return findTrimmedName(ns, create, clone);
        }
    }

    /**
     * Rehash the allNames hash.
     * @throws IndexOutOfBoundsException on hash overflow.
     * This method may be called only inside synchronized block.
     */
    private static void rehash() {
        NameImpl[] oldHash = allNames;
        int newSize = oldHash.length * 2 + 3;
        if (newSize < 0) {
            throw new IndexOutOfBoundsException();
        }
        NameImpl[] newHash = new NameImpl[GenMath.primeSince(newSize)];
        for (NameImpl n : oldHash) {
            if (n == null) {
                continue;
            }
            int i = n.ns.hashCode() & 0x7FFFFFFF;
            i %= newHash.length;
            for (int j = 1; newHash[i] != null; j += 2) {
                i += j;
                if (i >= newHash.length) {
                    i -= newHash.length;
                }
            }
            newHash[i] = n;
        }
        allNames = newHash;
    }

    /**
     * Constructs a <CODE>Name</CODE> (cannot be called).
     */
    private NameImpl(String s) {
        ns = s;
        int suffix = -1;
        NameImpl base = null;
        try {
            flags = checkNameThrow(ns);
        } catch (NumberFormatException e) {
            flags = ERROR;
        }
        if ((flags & ERROR) == 0 && (flags & TEMP) != 0) {
            int l = ns.length();
            while (l > 0 && TextUtils.isDigit(ns.charAt(l - 1))) {
                l--;
            }
            if (l == ns.length() - 1 && ns.charAt(ns.length() - 1) == '0') {
                base = this;
                suffix = 0;
            } else {
                base = newTrimmedName(ns.substring(0, l) + '0', false);
                suffix = Integer.parseInt(ns.substring(l));
            }
        }
        this.numSuffix = suffix;
        this.basename = base;
        if (flags == ERROR) {
            return;
        }
        if ((flags & BUS) == 0) {
            return;
        }

        // Make subnames
        if (isList()) {
            makeListSubNames();
            return;
        }
        int split = ns.indexOf('[');
        if (split == 0) {
            split = ns.lastIndexOf('[');
        }
        if (split == 0) {
            makeBracketSubNames();
        } else {
            makeSplitSubNames(split);
        }
    }

    /**
     * Makes subnames of a bus whose name is a list of names separated by commas.
     */
    private void makeListSubNames() {
        List<NameImpl> subs = new ArrayList<NameImpl>();
        for (int beg = 0; beg <= ns.length();) {
            int end = beg;
            while (end < ns.length() && ns.charAt(end) != ',') {
                if (ns.charAt(end) == '[') {
                    while (ns.charAt(end) != ']') {
                        end++;
                    }
                }
                end++;
            }
            NameImpl nm = newTrimmedName(ns.substring(beg, end), true);
            for (int j = 0; j < nm.busWidth(); j++) {
                subs.add(nm.subname(j));
            }
            beg = end + 1;
        }
        setSubnames(subs);
    }

    /**
     * Makes subnames of a bus whose name is indices list in brackets.
     */
    private void makeBracketSubNames() {
        List<NameImpl> subs = new ArrayList<NameImpl>();
        for (int beg = 1; beg < ns.length();) {
            int end = ns.indexOf(',', beg);
            if (end < 0) {
                end = ns.length() - 1; /* index of ']' */
            }
            int colon = ns.indexOf(':', beg);
            if (colon < 0 || colon >= end) {
                NameImpl nm = newTrimmedName("[" + ns.substring(beg, end) + "]", false);
                subs.add(nm);
            } else {
                int ind1 = Integer.parseInt(ns.substring(beg, colon));
                int ind2 = Integer.parseInt(ns.substring(colon + 1, end));
                if (ind1 < ind2) {
                    for (int i = ind1; i <= ind2; i++) {
                        subs.add(newTrimmedName("[" + i + "]", false));
                    }
                } else {
                    for (int i = ind1; i >= ind2; i--) {
                        subs.add(newTrimmedName("[" + i + "]", false));
                    }
                }
            }
            beg = end + 1;
        }
        setSubnames(subs);
    }

    private void setSubnames(List<NameImpl> subs) {
        subnames = new NameImpl[subs.size()];
        subs.toArray(subnames);

        // check duplicates
        NameImpl[] sorted = new NameImpl[subs.size()];
        subs.toArray(sorted);
        Arrays.sort(sorted);
        for (int i = 1; i < sorted.length; i++) {
            if (sorted[i].equals(sorted[i - 1])) {
                flags |= DUPLICATES;
                break;
            }
        }
    }

    /**
     * Makes subnames of a bus whose name consists of simpler names.
     * @param split index dividing name into simpler names.
     */
    private void makeSplitSubNames(int split) {
        // if (ns.length() == 0) return;
        if (split < 0 || split >= ns.length()) {
            System.out.println("HEY! string is '" + ns + "' but want index " + split);
            return;
        }
        NameImpl baseName = newTrimmedName(ns.substring(0, split), true);
        NameImpl indexList = newTrimmedName(ns.substring(split), true);
        subnames = new NameImpl[baseName.busWidth() * indexList.busWidth()];
        for (int i = 0; i < baseName.busWidth(); i++) {
            String bs = baseName.subname(i).toString();
            for (int j = 0; j < indexList.busWidth(); j++) {
                String is = indexList.subname(j).toString();
                subnames[i * indexList.busWidth() + j] = newTrimmedName(bs + is, false);
            }
        }
        if (baseName.hasDuplicates() || indexList.hasDuplicates()) {
            flags |= DUPLICATES;
        }
    }
}
