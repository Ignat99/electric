/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: RiverTest.java
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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.topology.ArcInst;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.util.test.TstUserInterface;

/**
 * @author Felix Schmidt
 * 
 */
public class RiverTest extends AbstractRoutingBaseClass {

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sun.electric.tool.routing.AbstractRoutingBaseClass#getRoutingFrame()
	 */
	@Override
	protected RoutingFrame getRoutingFrame() {
		// TODO Auto-generated method stub
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.electric.tool.routing.AbstractRoutingBaseClass#testRouter()
	 */
	@Override
	public void testRouter() throws Exception {
		Cell cell = this.loadCell("placementTests", "PlacementTest4");
		TstUserInterface testUI = (TstUserInterface) Job.getUserInterface();
		testUI.setCurrentCell(cell);

        EditingPreferences ep = new EditingPreferences(true, cell.getTechPool());
		River router = new River(ep);
		List<ArcInst> allArcs = new ArrayList<ArcInst>();
		for (Iterator<ArcInst> it = cell.getArcs(); it.hasNext();)
			allArcs.add(it.next());
		router.river(cell, allArcs);
	}
}