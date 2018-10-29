/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: SeaOfGatesBase.java
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
package com.sun.electric.tool.routing.seaOfGates;

import com.sun.electric.database.EditingPreferences;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.tool.routing.Routing;
import com.sun.electric.tool.routing.SeaOfGates.SeaOfGatesOptions;
import com.sun.electric.tool.routing.seaOfGates.SeaOfGatesEngineFactory.SeaOfGatesEngineType;
import com.sun.electric.tool.util.test.AbstractJunitBaseClass;
import org.junit.Ignore;

import org.junit.Test;

/**
 * @author Felix Schmidt
 *
 */
public abstract class SeaOfGatesBase extends AbstractJunitBaseClass/*AbstractRoutingBaseClass*/ {

	protected abstract SeaOfGatesEngineType getType();
	
	/* (non-Javadoc)
	 * @see com.sun.electric.tool.routing.AbstractRoutingBaseClass#testRouter()
	 */
    @Ignore
    @Test
	public void testRouter1() throws Exception {
		SeaOfGatesOptions options = new SeaOfGatesOptions();
        options.useParallelRoutes = true;
        options.useParallelFromToRoutes = true;
        testRouter(options);
    }
    
	/* (non-Javadoc)
	 * @see com.sun.electric.tool.routing.AbstractRoutingBaseClass#testRouter()
	 */
    @Ignore
    @Test
	public void testRouter2() throws Exception {
		SeaOfGatesOptions options = new SeaOfGatesOptions();
        options.useParallelRoutes = true;
        options.useParallelFromToRoutes = false;
        testRouter(options);
    }
    
    private void testRouter(SeaOfGatesOptions options) throws Exception {
        System.out.println("=============== " + getType() + " useParallelRoutes=" + options.useParallelRoutes + " useParallelFromToRoutes=" + options.useParallelFromToRoutes);
		Cell cell = this.loadCell("placementTests", "PlacementTest4");
        EditingPreferences ep = new EditingPreferences(true, cell.getTechPool());
		SeaOfGatesEngine router = SeaOfGatesEngineFactory.createSeaOfGatesEngine(getType());
		router.setPrefs(options);
        router.routeIt(SeaOfGatesHandlers.getDefault(cell, null, Routing.SoGContactsStrategy.SOGCONTACTSATTOPLEVEL, null, ep), cell, false);
    }

}
