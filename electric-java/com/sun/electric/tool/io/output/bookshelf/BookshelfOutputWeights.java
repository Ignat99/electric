/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: BookshelfOutputWeights.java
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
package com.sun.electric.tool.io.output.bookshelf;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Iterator;

import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.variable.Variable;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.io.input.bookshelf.Bookshelf.BookshelfFiles;

/**
 * @author Felix Schmidt
 * 
 */
public class BookshelfOutputWeights extends BookshelfOutputWriter {

	private static final BookshelfFiles fileType = BookshelfFiles.wts;

	private Cell cell;

	/**
	 * @param genericFileName
	 * @param cell
	 */
	public BookshelfOutputWeights(String genericFileName, Cell cell) {
		super(genericFileName, fileType);
		this.cell = cell;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sun.electric.tool.io.output.bookshelf.BookshelfOutputWriter#write()
	 */
	@Override
	public void write() throws IOException {
		
		Job.getUserInterface().setProgressNote("Weights File" + this.fileName);

		PrintWriter writer = new PrintWriter(this.fileName);
		
		writer.println(BookshelfOutput.createBookshelfHeader(fileType));

		for (Iterator<NodeInst> ini = cell.getNodes(); ini.hasNext();) {
			NodeInst ni = ini.next();
			String name = ni.getName();
			int weight = 1;

			Variable var = ni.getVar("weight");
			if (var != null) {
				weight = (Integer) var.getObject();
				writer.println("   " + name + "   " + weight);
			}
		}

		writer.flush();
		writer.close();

	}

}
