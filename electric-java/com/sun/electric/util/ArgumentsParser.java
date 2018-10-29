/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: ArgumentsParser.java
 *
 * Copyright (c) 2010, Static Free Software. All rights reserved.
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

import java.util.HashMap;
import java.util.Map;

/**
 * @author Schmidt
 *
 */
public class ArgumentsParser {
    public static interface ArgumentEnum {
        public boolean isOptional();
    }

    public static Map<String, String> parseArguments(String... args) {
        Map<String, String> result = new HashMap<String, String>();

        for (String arg : args) {
            String key = extractArgumentName(arg);
            String value = extractValue(arg);
            result.put(key, value);
        }

        return result;
    }

    private static String extractArgumentName(String arg) {
    	// check if any "-" is in the beginning of the argument
    	// Can't look for the last position with lastIndex since path
    	// might contain "-" as name
    	int pos = 0;
    	boolean prevV = true; // keep looking if the previous was "-"
    	char[] array = arg.toCharArray();
    	while (prevV && pos < array.length)
    	{
    		prevV = array[pos] == '-';
    		if (prevV)
    			pos++;
    	}
    	assert(pos > -1);
//    	int index = arg.lastIndexOf("-"); // position of last "-" if any
    	int index = pos;
    	assert(index > -1);
    	int indexe = arg.indexOf("=");
    	// indexe == -1 -> options without argument like -help
        if (indexe != -1) {
            return arg.substring(index, indexe);
        } else {
            return arg.substring(index);
        }
    }

    private static String extractValue(String arg) {
    	int indexe = arg.indexOf("=");
        if (indexe != -1) {
            return arg.substring(indexe + 1);
        } else {
            return null;
        }
    }
}
