/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: BTree.java
 *
 * Copyright (c) 2009, Static Free Software. All rights reserved.
 *
 * Electric(tm) is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * Electric(tm) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sun.electric.database.geometry.btree;

import java.io.*;

/**
 *  Stores and retrieves fixed-size byte sequences indexed by a page
 *  number; page numbers are guaranteed to be contiguous starting at
 *  zero.
 */
public abstract class PageStorage {

    private final int pageSize;

    public PageStorage(int pageSize) { this.pageSize = pageSize; }

    /** returns the size, in bytes, of each page */
    public final int getPageSize() { return pageSize; }

    /** creates a new page with undefined contents; returns its pageid */
    public abstract int  createPage();

    /** returns the number of pages; all pageids strictly less than this are valid */
    public abstract int  getNumPages();

    /** writes a page; throws an exception if the page did not exist */ 
    public abstract void writePage(int pageid, byte[] buf, int ofs);

    /** reads a page */
    public abstract void readPage(int pageid, byte[] buf, int ofs);

    /** ensure that the designated page is written to nonvolatile storage */
    public abstract void fsync(int pageid);
    
    /** close the PageStorage; invocation of any other methods after close() has undefined results */
    public abstract void close();
}
