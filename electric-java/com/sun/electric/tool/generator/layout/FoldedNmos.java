/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: FoldedNmos.java
 *
 * Copyright (c) 2003, Static Free Software. All rights reserved.
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
package com.sun.electric.tool.generator.layout;

import com.sun.electric.database.EditingPreferences;
import com.sun.electric.database.hierarchy.Cell;

/** The FoldedNmos class is a layer beneath the gate layout generators.
 * Therefore FoldedNmos should not use StdCellparams.
 * 
 * @author rkao */
public class FoldedNmos extends FoldedMos {
	/** By default the FoldedNmos shifts the diffusion contact to the
	 * top of the transistor */
	public FoldedNmos(double x, double y, int nbFolds, int nbSeries,
                      double gateWidth, Cell f, TechType tech, EditingPreferences ep) {
		super('N', x, y, nbFolds, nbSeries, gateWidth, null, 'T', f, tech, ep);
	}
	public FoldedNmos(double x, double y, int nbFolds, int nbSeries,
                      double gateWidth, GateSpace gateSpace,
                      char justifyDiffCont, Cell f, TechType tech, EditingPreferences ep) {
		super('N', x, y, nbFolds, nbSeries, gateWidth, gateSpace, 
		      justifyDiffCont, f, tech, ep);
	}

}
