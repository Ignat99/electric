/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: BookshelfOutputWriter.java
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

import com.sun.electric.tool.io.input.bookshelf.Bookshelf.BookshelfFiles;

/**
 * @author Felix Schmidt
 * 
 */
public abstract class BookshelfOutputWriter {

	protected String fileName;

	public String getFileName() {
		return fileName;
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

	/**
	 * 
	 */
	public BookshelfOutputWriter(String genericFileName, BookshelfFiles fileType) {
		this.fileName = genericFileName + "." + fileType.toString();
	}

	public abstract void write() throws IOException;

}
