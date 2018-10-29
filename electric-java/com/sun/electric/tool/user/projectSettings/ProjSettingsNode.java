/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: ProjSettingsNode.java
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
package com.sun.electric.tool.user.projectSettings;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.Stack;
import java.util.TreeSet;

/**
 * A basic class to hold information on project preferences.
 * This node holds key-value pairs, who values are typically
 * restricted to a few primitive types, and other ProjSettingNodes.
 * <P>
 * This class may be used as is, or may be extended to provide additional,
 * more specific methods for setting/getting project preferences.
 * Only settings accessible by this class' methods will be
 * written to disk.
 * Additionally, the extended class must be public, so that it can be
 * created when the settings are read from a file.
 * <P>
 * Settings are written to the file in the order they are
 * added to this class. This order is consistent and deterministic,
 * and is not sorted after being added.
 * <P>
 * This class also retains the first setting for any key.  This allows
 * the user to get the "initial" or "factory" setting, which is always
 * the first setting applied.
 */
class ProjSettingsNode implements Serializable {

//    private final ProjSettingsNode parent;
//    private final String key;
    private final String path;
    final LinkedHashMap<String, Object> data = new LinkedHashMap<String,Object>();

    /**
     * Create a new default proj settings node
     */
    ProjSettingsNode() {
        path = "";
    }

    /**
     * Create a new default proj settings node
     */
    private ProjSettingsNode(ProjSettingsNode parent, String key) {
//        this.parent = parent;
//        this.key = key;
        path = parent + key + ".";
    }

    /**
     * Returns a path to this ProjSettingsNode from the root.
     * Keys in the path are separated by '.' char.
     * @return path to this ProjSettingsNode from the root.
     */
    public String getPath() {
        return path;
    }
    
    @Override
    public String toString() { return getPath(); }
    
    /**
     * Returns a set of keys, whose order is the
     * order in which keys were added.
     * @return a set of keys in deterministic order
     */
    public Set<String> getKeys() {
        return data.keySet();
    }

//    /**
//     * Set the value for a key.
//     * @param key a string key
//     * @param setting a value
//     */
//    public void putValue(String key, Setting setting) {
//        Object v = data.get(key);
//        Object previousVal = null;
//        if (v instanceof UninitializedPref) {
//            // this overrides pref value, when setting was uninitialized so we couldn't set it before
//            previousVal = ((UninitializedPref)v).value;
//        }
//        data.put(key, setting);
//
//        if (previousVal != null && !equal(previousVal, setting)) {
//            System.out.println("Warning: For key "+key+": project preferences value of "+previousVal+" overrides default of "+setting.getValue());
//            setting.set(previousVal);
//        }
//    }
//
//    public Setting getValue(String key) {
//        Object obj = data.get(key);
//        if (obj instanceof Setting)
//            return (Setting)obj;
//        if (obj == null) return null;
//        //prIllegalRequestError(key);
//        return null;
//    }
//
//    public void putNode(String key, ProjSettingsNode node) {
//        data.put(key, node);
//    }

    public ProjSettingsNode getNode(String key) {
        Object obj = data.get(key);
        if (obj == null) {
            obj = new ProjSettingsNode(this, key);
            data.put(key, obj);
        }
        if (obj instanceof ProjSettingsNode)
            return (ProjSettingsNode)obj;
        //prIllegalRequestError(key);
        return null;
    }

//    private void prIllegalRequestError(String key) {
//        System.out.println("ERROR! Project Preferences key conflict: "+key);
//    }

    // ----------------------------- Protected --------------------------------

    protected Object get(String key) {
        return data.get(key);
    }

    protected void put(String key, Object node) {
        data.put(key, node);
    }

    // ----------------------------- Utility ----------------------------------

    public boolean equals(Object node) {
        if (!(node instanceof ProjSettingsNode)) return false;

        ProjSettingsNode otherNode = (ProjSettingsNode)node;
        Set<String> myKeys = getKeys();
        Set<String> otherKeys = otherNode.getKeys();
        if (myKeys.size() != otherKeys.size()) return false;

        for (String myKey : myKeys) {
            if (!(otherKeys.contains(myKey))) return false;
            Object myObj = get(myKey);
            Object otherObj = otherNode.get(myKey);
            if (myObj.getClass() != otherObj.getClass()) return false;
            if (!myObj.equals(otherObj)) return false;
        }
        return true;
    }

    /**
     * Print any differences between the two nodes
     * @param node the nodes to compare
     * @return true if differences found, false otherwise
     */
    public boolean printDifferences(Object node) {
        return printDifferences(node, new Stack<String>());
    }
    private boolean printDifferences(Object node, Stack<String> context) {
        if (!(node instanceof ProjSettingsNode)) return true;

        boolean differencesFound = false;
        ProjSettingsNode otherNode = (ProjSettingsNode)node;
        Set<String> myKeys = getKeys();
        Set<String> otherKeys = otherNode.getKeys();
        Set<String> allKeys = new TreeSet<String>();
        allKeys.addAll(myKeys);
        allKeys.addAll(otherKeys);

        for (String key : allKeys) {
            if (!myKeys.contains(key)) {
                System.out.println("Warning: Key "+getKey(context, key)+" is missing from other settings");
                differencesFound = true;
                continue;
            }
            if (!otherKeys.contains(key)) {
                System.out.println("Warning: Key "+getKey(context, key)+" is missing from current settings");
                differencesFound = true;
                continue;
            }
            Object myObj = get(key);
            Object otherObj = otherNode.get(key);
            if (myObj.getClass() != otherObj.getClass()) {
                System.out.println("Warning: Value type mismatch for key "+getKey(context, key)+": "+
                        myObj.getClass().getName()+" vs "+otherObj.getClass().getName());
                differencesFound = true;
                continue;
            }
            if (myObj instanceof ProjSettingsNode) {
                context.push(key);
                if (((ProjSettingsNode)myObj).printDifferences(otherObj, context))
                    differencesFound = true;
                context.pop();
            } else if (!myObj.equals(otherObj)) {
                System.out.println("Warning: Values not equal for key "+getKey(context, key)+": "+myObj+" vs "+otherObj);
                differencesFound = true;
            }
        }
        return differencesFound;
    }

    private String getKey(Stack<String> context, String key) {
        return describeContext(context)+"."+key;
    }

    public static String describeContext(Stack<String> context) {
        StringBuffer buf = new StringBuffer();
        boolean first = true;
        for (String name : context) {
            if (first)
                first = false;
            else
                buf.append(".");
            buf.append(name);
        }
        if (buf.length() == 0) return "RootContext";
        return buf.toString();
    }
}
