/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: AbstractJunitTest.java
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
package com.sun.electric.tool.util.test;

import org.junit.Assert;
import org.junit.Test;

import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.Library;
import com.sun.electric.database.hierarchy.View;

/**
 * @author Felix Schmidt
 * 
 */
public class AbstractJunitTest extends AbstractJunitBaseClass {

	@Test
	public void testLoadLibrary() throws Exception {
		Library lib = this.loadLibrary("testLib");

		Assert.assertNotNull(lib);
	}

	@Test
	public void testLoadCellLayout() throws Exception {
		Cell cell = this.loadCell("testLib", "testCell");
		Assert.assertNotNull(cell);
		Assert.assertTrue(cell.getView().equals(View.LAYOUT));
	}

	@Test
	public void testLoadCellSchematic() throws Exception {
		Cell cell = this.loadCell("testLib", "testSch");
		Assert.assertNotNull(cell);
		Assert.assertTrue(cell.getView().equals(View.SCHEMATIC));
	}

}
