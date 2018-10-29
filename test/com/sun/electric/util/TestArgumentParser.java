/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: TestArgumentParser.java
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

import java.util.Map;

import org.junit.Test;

/**
 * @author Schmidt
 *
 */
public class TestArgumentParser {

    @Test
    public void testParser() {
        String[] args = {"--test1=test" , "--test2"};

        Map<String, String> result = ArgumentsParser.parseArguments(args);

        for(Map.Entry<String, String> entry: result.entrySet()) {
            System.out.println("key: " + entry.getKey() + " - value: " + entry.getValue());
        }
    }

}
