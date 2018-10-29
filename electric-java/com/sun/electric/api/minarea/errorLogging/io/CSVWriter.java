/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: CVSWriter.java
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
package com.sun.electric.api.minarea.errorLogging.io;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;

/**
 * @author Schmidt
 * 
 */
public class CSVWriter extends PrintStream {

    /**
     * @param file
     * @throws FileNotFoundException
     */
    public CSVWriter(File file) throws FileNotFoundException {
        super(file);
        // TODO Auto-generated constructor stub
    }

    /**
     * @param file
     * @throws FileNotFoundException
     */
    public void printCSVFile(CSVFile file) {
        this.print(file.toString());
    }
}
