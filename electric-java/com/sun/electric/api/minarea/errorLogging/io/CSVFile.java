/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: CSVFile.java
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

import java.util.ArrayList;
import java.util.List;

/**
 * @author Schmidt
 * 
 */
public class CSVFile {

    private static final Character NEWLINE = '\n';
    private List<String> header;
    private List<List<String>> lines;
    private Character separator;

    public CSVFile(Character separator) {
        this.separator = separator;
    }

    public List<String> getHeader() {
        return header;
    }

    public void setHeader(List<String> header) {
        this.header = header;
    }

    public List<List<String>> getLines() {
        return lines;
    }

    public void addLine(List<String> line) {
        if (this.lines == null) {
            this.lines = new ArrayList<List<String>>();
        }

        this.lines.add(line);
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();

        if (this.header != null) {
            builder.append(createLineEntry(header));
            builder.append(CSVFile.NEWLINE);
        }

        if (this.lines != null) {
            for (List<String> line : lines) {
                builder.append(createLineEntry(line));
                builder.append(CSVFile.NEWLINE);
            }
        }

        return builder.substring(0, builder.length());
    }

    private String createLineEntry(List<String> line) {
        StringBuilder builder = new StringBuilder();

        for (String elem : line) {
            builder.append(elem);
            builder.append(separator);
        }

        return builder.substring(0, builder.length() - 1);
    }
}
