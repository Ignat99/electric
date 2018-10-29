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
package com.sun.electric.database.text;

import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.View;
import com.sun.electric.database.network.Network;
import com.sun.electric.database.topology.Connection;
import com.sun.electric.database.variable.TextDescriptor;
import com.sun.electric.technology.Technology;
import com.sun.electric.tool.lang.EvalJavaBsh;
import com.sun.electric.tool.user.User;

import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

/**
 * This class is a collection of text utilities.
 */
public class TextUtils extends com.sun.electric.util.TextUtils {

    /**
     * Method to parse the floating-point number in a string, assuming that it is a distance value in the current technology.
     * @param text the string to convert to a double.
     * @param tech the technology to use for the conversion.
     * If it is not a layout technology, then use pure numbers.
     * @return the numeric value in internal database units.
     */
    public static double atofDistance(String text, Technology tech) {
        if (tech != null && tech.isLayout()) {
            return atof(text, null, TextDescriptor.Unit.DISTANCE, tech);
        }
        return atof(text);
    }

    /**
     * Method to parse the floating-point number in a string, assuming that it is a distance value in the current technology.
     * @param text the string to convert to a double.
     * @return the numeric value in internal database units.
     */
    public static double atofDistance(String text) {
        return atof(text, null, TextDescriptor.Unit.DISTANCE, Technology.getCurrent());
    }

    /**
     * Method to parse the floating-point number in a string, using a default value if no number can be determined,
     * and presuming a type of unit.
     * @param text the string to convert to a double.
     * @param defaultVal the value to return if the string cannot be converted to a double.
     * If 'defaultVal' is null and the text cannot be converted to a number, the method returns 0.
     * @param unitType the type of unit being examined (handles postfix characters).
     * @return the numeric value.
     */
    public static double atof(String text, Double defaultVal, TextDescriptor.Unit unitType, Technology tech) {
        if (unitType != null) {
            // strip off postfix characters if present
            String pf = unitType.getPostfixChar();
            if (text.endsWith(pf)) {
                text = text.substring(0, text.length() - pf.length());
            }
        }

        if (unitType == TextDescriptor.Unit.DISTANCE) {
            UnitScale us = User.getDistanceUnits();
            if (us != null) {
                // remove commas that denote 1000's separators
                text = text.replaceAll(",", "");
                double v = 0;
                try {
                    v = parsePostFixNumber(text, us).doubleValue();
                } catch (NumberFormatException ex) {
                    v = atof(text, defaultVal);
                }
                return v / tech.getScale() / UnitScale.NANO.getMultiplier().doubleValue();
            }
        }
        return atof(text, defaultVal);
    }

    /**
     * Method to get the numeric value of a string that may be an expression.
     * @param expression the string that may be an expression.
     * @return the numeric value of the expression.
     * This method uses the Bean Shell to evaluate non-numeric strings.
     */
    public static double getValueOfExpression(String expression) {
        if (isANumber(expression)) {
            double res = atof(expression);
            return res;
        }
        Object o = EvalJavaBsh.evalJavaBsh.doEvalLine(expression);
        if (o == null) {
            return 0;
        }
        if (o instanceof Double) {
            return ((Double) o).doubleValue();
        }
        if (o instanceof Integer) {
            return ((Integer) o).intValue();
        }
        return 0;
    }

    /**
     * Method to convert a distance to a string, using scale from the current technology if necessary.
     * If the value has no precision past the decimal, none will be shown.
     * If the units are not scalable, then appropriate values will be shown
     * @param v the distance value to format.
     * @return the string representation of the number.
     */
    public static String formatDistance(double v, Technology tech) {
        if (tech != null && tech.isLayout()) {
            return displayedUnits(v, TextDescriptor.Unit.DISTANCE, User.getDistanceUnits(), tech);
        }
        return formatDouble(v);
    }

    /**
     * Method to convert a distance to a string, using scale from the current technology if necessary.
     * If the value has no precision past the decimal, none will be shown.
     * If the units are not scalable, then appropriate values will be shown
     * @param v the distance value to format.
     * @return the string representation of the number.
     */
    public static String formatDistance(double v) {
        return displayedUnits(v, TextDescriptor.Unit.DISTANCE, User.getDistanceUnits(), Technology.getCurrent());
    }

    /**
     * Method to convert a database coordinate into real spacing.
     * @param value the database coordinate to convert.
     * @param tech the technology to use for conversion (provides a real scaling).
     * @param unitScale the type of unit desired.
     * @return the database coordinate in the desired units.
     * For example, if the given technology has a scale of 200 nanometers per unit,
     * and the value 7 is given, then that is 1.4 microns (1400 nanometers).
     * If the desired units are UnitScale.MICRO, then the returned value will be 1.4.
     */
    public static double convertDistance(double value, Technology tech, UnitScale unitScale) {
        double scale = tech.getScale();
        double distanceScale = 0.000000001 / unitScale.getMultiplier().doubleValue() * scale;
        return value * distanceScale;
    }

    /**
     * Method to convert real spacing into a database coordinate.
     * @param value the real distance to convert.
     * @param tech the technology to use for conversion (provides a real scaling).
     * @param unitScale the type of unit desired.
     * @return the real spacing in the database units.
     * For example, if the given technology has a scale of 200 nanometers per unit,
     * and the value 1.6 is given with the scale UnitScale.MICRO, then that is 1.6 microns (1600 nanometers).
     * Since the technology has 200 nanometers per unit, this converts to 8 units.
     */
    public static double convertFromDistance(double value, Technology tech, UnitScale unitScale) {
        double scale = (tech != null) ? tech.getScale() : 1;
        double distanceScale = 0.000000001 / unitScale.getMultiplier().doubleValue() * scale;
        return value / distanceScale;
    }

    /**
     * Method to express "value" as a string in "unittype" electrical units.
     * The scale of the units is in "unitscale".
     */
    private static String displayedUnits(double value, TextDescriptor.Unit unitType, UnitScale unitScale, Technology tech) {
        if (unitType == TextDescriptor.Unit.DISTANCE && unitScale != null) {
            value *= UnitScale.NANO.getMultiplier().doubleValue() * tech.getScale();
        }
        String postFix = "";
        if (unitScale != null) {
            value /= unitScale.getMultiplier().doubleValue();
            postFix = unitScale.getPostFix() + unitType.getPostfixChar();
        }
        return formatDouble(value) + postFix;
    }

    /**
     * Method to convert a floating point value to a string, given that it is a particular type of unit.
     * Each unit has a default scale.  For example, if Capacitance value 0.0000012 is being converted, and
     * Capacitance is currently using microFarads, then the result will be "1.2m".
     * If, however, capacitance is currently using milliFarads, the result will be 0.0012u".
     * @param value the floating point value.
     * @param units the type of unit.
     * @return a string describing that value in the current unit.
     */
    public static String makeUnits(double value, TextDescriptor.Unit units) {
        if (units == TextDescriptor.Unit.NONE) {
            return formatDouble(value);
        }
        if (units == TextDescriptor.Unit.DISTANCE) {
            return displayedUnits(value, units, User.getDistanceUnits(), Technology.getCurrent());
        }
//		if (units == TextDescriptor.Unit.RESISTANCE)
//			return displayedUnits(value, units, User.getResistanceUnits());
//		if (units == TextDescriptor.Unit.CAPACITANCE)
//			return displayedUnits(value, units, User.getCapacitanceUnits());
//		if (units == TextDescriptor.Unit.INDUCTANCE)
//			return displayedUnits(value, units, User.getInductanceUnits());
//		if (units == TextDescriptor.Unit.CURRENT)
//			return displayedUnits(value, units, User.getAmperageUnits());
//		if (units == TextDescriptor.Unit.VOLTAGE)
//			return displayedUnits(value, units, User.getVoltageUnits());
//		if (units == TextDescriptor.Unit.TIME)
//			return displayedUnits(value, units, User.getTimeUnits());
        return (formatDoublePostFix(value));
    }

    /****************************** FOR SORTING OBJECTS ******************************/
    /**
     * Comparator class for sorting Objects by their string name.
     */
    public static class ObjectsByToString implements Comparator<Object> {

        /**
         * Method to sort Objects by their string name.
         */
        @Override
        public int compare(Object o1, Object o2) {
            String s1 = o1.toString();
            String s2 = o2.toString();
            return s1.compareToIgnoreCase(s2);
        }
    }

    /**
     * Comparator class for sorting Cells by their view order.
     */
    public static class CellsByView implements Comparator<Cell> {

        /**
         * Method to sort Cells by their view order.
         */
        @Override
        public int compare(Cell c1, Cell c2) {
            View v1 = c1.getView();
            View v2 = c2.getView();
            return v1.getOrder() - v2.getOrder();
        }
    }

    /**
     * Comparator class for sorting Cells by their version number.
     */
    public static class CellsByVersion implements Comparator<Cell> {

        /**
         * Method to sort Cells by their version number.
         */
        @Override
        public int compare(Cell c1, Cell c2) {
            return c2.getVersion() - c1.getVersion();
        }
    }

    /**
     * Comparator class for sorting Cells by their name (NOT considering numbers in the names).
     */
    public static class CellsByName implements Comparator<Cell> {

        /**
         * Method to sort Cells by their name.
         */
        @Override
        public int compare(Cell c1, Cell c2) {
            String r1 = c1.getName();
            String r2 = c2.getName();
            return r1.compareTo(r2);
        }
    }

    /**
     * Comparator class for sorting Cells by their date.
     */
    public static class CellsByDate implements Comparator<Cell> {

        /**
         * Method to sort Cells by their date.
         */
        @Override
        public int compare(Cell c1, Cell c2) {
            Date r1 = c1.getRevisionDate();
            Date r2 = c2.getRevisionDate();
            return r1.compareTo(r2);
        }
    }

    /**
     * Comparator class for sorting Preferences by their name.
     */
    public static class PrefsByName implements Comparator<Pref> {

        /**
         * Method to sort Preferences by their name.
         */
        public int compare(Pref p1, Pref p2) {
            String s1 = p1.getPrefName();
            String s2 = p2.getPrefName();
            return s1.compareToIgnoreCase(s2);
        }
    }

    /**
     * Comparator class for sorting Networks by their name.
     */
    public static class NetworksByName implements Comparator<Network> {

        /**
         * Method to sort Networks by their name.
         */
        @Override
        public int compare(Network n1, Network n2) {
            String s1 = n1.describe(false);
            String s2 = n2.describe(false);
            return s1.compareToIgnoreCase(s2);
        }
    }
    public static final Comparator<Connection> CONNECTIONS_ORDER = new Comparator<Connection>() {

        @Override
        public int compare(Connection c1, Connection c2) {
            int i1 = c1.getPortInst().getPortProto().getPortIndex();
            int i2 = c2.getPortInst().getPortProto().getPortIndex();
            int cmp = i1 - i2;
            if (cmp != 0) {
                return cmp;
            }
            cmp = c1.getArc().getArcId() - c2.getArc().getArcId();
            if (cmp != 0) {
                return cmp;
            }
            return c1.getEndIndex() - c2.getEndIndex();
        }
    };

    /**
     * Class to define the kind of text string to search
     */
    public enum WhatToSearch {

        ARC_NAME("Arc Name"),
        ARC_VAR("Arc Variable"),
        NODE_NAME("Node Name"),
        NODE_VAR("Node Variable"),
        EXPORT_NAME("Export Name"),
        EXPORT_VAR("Export Variable"),
        CELL_VAR("Cell Name"),
        TEMP_NAMES(null);
        private String descriptionOfObjectFound;

        private WhatToSearch(String descriptionOfObjectFound) {
            this.descriptionOfObjectFound = descriptionOfObjectFound;
        }

        public String toString() {
            return descriptionOfObjectFound;
        }
    }
    private static Set<String> missingComponentNames = new HashSet<String>();
    private static Set<String> missingPrivateComponentNames = new HashSet<String>();
    private static Set<String> missingTechnologyNames = new HashSet<String>();

    /**
     * Method to report a missing component, not found in the classpath.
     * @param name a missing component, not found in the classpath.
     */
    public static void recordMissingComponent(String name) {
        missingComponentNames.add(name);
    }

    /**
     * Method to report a missing technologies, not found in the classpath.
     * @param name a missing technologies, not found in the classpath.
     */
    public static void recordMissingTechnology(String name) {
        missingTechnologyNames.add(name);
    }

    /**
     * Method to report a missing private component, not found in the classpath.
     * Private components are those not publicly available.
     * @param name a missing private component, not found in the classpath.
     */
    public static void recordMissingPrivateComponent(String name) {
        missingPrivateComponentNames.add(name);
    }

    /**
     * Method to return a list of components that were not found in Electric plugins.
     * @return a list of components that were not found in Electric plugins.
     */
    public static Set<String> getMissingComponentNames() {
        if (missingTechnologyNames.size() > 0) {
            String techNames = null;
            for (String tech : missingTechnologyNames) {
                if (techNames == null) {
                    techNames = "Technologies (";
                } else {
                    techNames += ", ";
                }
                techNames += tech;
            }
            techNames += ")";
            missingComponentNames.add(techNames);
        }
        return missingComponentNames;
    }

    /**
     * Method to return a list of private (internal) components that were not found in Electric plugins.
     * @return a list of private (internal) components that were not found in Electric plugins.
     */
    public static Set<String> getMissingPrivateComponentNames() {
        return missingPrivateComponentNames;
    }
    
    /**
     * Method to standardized the headers used in output files generated by Electric
     * @param cell the Cell being emitted.
     * @param startDelim the starting text before comments.
     * @param endDelim the ending text after comments.
     * @param includeDate true to include dates in the header.
     * @param includeVersion true to include Electric's version in the header.
     * @return the standard header to use.
     */
    public static String generateFileHeader(Cell cell, String startDelim, String endDelim, 
			boolean includeDate, boolean includeVersion)
    {
    	StringBuffer s = new StringBuffer(100);
		if (includeDate || includeVersion)
		{
			if (cell != null)
			{
				s.append(startDelim + " Cell created on " + TextUtils.formatDate(cell.getCreationDate()) + " " + endDelim + "\n");
				s.append(startDelim + " Cell last revised on " + TextUtils.formatDate(cell.getRevisionDate()) + " " + endDelim + "\n");
			}
			s.append(startDelim + " Written");
			
			if (includeDate)
				s.append(" on " + TextUtils.formatDate(new Date()) + " " + endDelim + "\n" + startDelim);
			s.append(" by Electric VLSI Design System");
			if (includeVersion)
				s.append(", version " + Version.getVersion());
			s.append(" " + endDelim + "\n");
		} else
		{
			s.append(startDelim + " Written by Electric VLSI Design System " + endDelim + "\n");
		}
		return s.toString();
    }
}
