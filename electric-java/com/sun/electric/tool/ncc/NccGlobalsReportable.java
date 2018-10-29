/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: NccGlobalsReportable.java
 *
 * Copyright (c) 2005, Static Free Software. All rights reserved.
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
package com.sun.electric.tool.ncc;

import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.HierarchyEnumerator.NetNameProxy;
import com.sun.electric.database.hierarchy.HierarchyEnumerator.NodableNameProxy;
import com.sun.electric.database.variable.VarContext;
import com.sun.electric.tool.ncc.result.BenchmarkResults;
import com.sun.electric.tool.user.ncc.NccGuiInfo;

public interface NccGlobalsReportable {
	NetNameProxy[][] getEquivalentNets();
	NodableNameProxy[][] getEquivalentNodes();
	Cell[] getRootCells();
	String[] getRootCellNames();
	VarContext[] getRootContexts();
	NccOptions getOptions();
	int[] getPartCounts();
	int[] getPortCounts();
	int[] getWireCounts();
	boolean[] cantBuildNetlistBits();
	NccGuiInfo getNccGuiInfo();
	BenchmarkResults getBenchmarkResults();

}
