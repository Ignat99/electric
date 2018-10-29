/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: AbstractRoutingBaseClass.java
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
package com.sun.electric.tool.routing;

import com.sun.electric.database.EditingPreferences;
import org.junit.Test;

import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.tool.util.test.AbstractJunitBaseClass;

/**
 * @author Felix Schmidt
 * 
 */
public abstract class AbstractRoutingBaseClass extends AbstractJunitBaseClass {

	protected int testRoutingAlgorithm(RoutingFrame router, String libName, String cellName, String fileName)
			throws Exception {
		Cell cell = this.loadCell(libName, cellName, fileName);
		return testRoutingAlgorithm(router, cell);
	}

	protected int testRoutingAlgorithm(RoutingFrame router, Cell cell) {
        EditingPreferences ep = new EditingPreferences(true, cell.getTechPool());
		return router.doRouting(cell, ep, new RoutingFrame.RoutingPrefs(true));
	}

	protected abstract RoutingFrame getRoutingFrame();

	@Test
	public void testRouter() throws Exception {
		Cell cell = this.loadCell("placementTests", "PlacementTest4");
		this.testRoutingAlgorithm(getRoutingFrame(), cell);
	}

}
