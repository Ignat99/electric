/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: FlagConstructorData.java
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
package com.sun.electric.tool.generator.flag;

import com.sun.electric.database.EditingPreferences;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.routing.SeaOfGates;

/** I bundle all the data to be passed by the physical design contructors
 * into a single object because I'd like to be able to add things in the
 * future without changing existing code that may be stored with the design. */
public class FlagConstructorData {
	private final Cell layCell, schCell;
	private final Job job;
    private EditingPreferences ep;
	private SeaOfGates.SeaOfGatesOptions prefs;
	FlagConstructorData(Cell layCell, Cell schCell, Job job, EditingPreferences ep, SeaOfGates.SeaOfGatesOptions prefs) {
		this.layCell = layCell;
		this.schCell = schCell;
		this.job = job;
        this.ep = ep;
		this.prefs = prefs;
	}
	
	// --------------------- public methods ------------------------
	public Cell getLayoutCell() {return layCell;}
	public Cell getSchematicCell() {return schCell;}
	public Job getJob() {return job;}
    public EditingPreferences getEditingPreferences() { return ep; }
	public SeaOfGates.SeaOfGatesOptions getSOGPrefs() {return prefs;}
}
