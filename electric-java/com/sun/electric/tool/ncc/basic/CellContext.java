/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: CellContext.java
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
package com.sun.electric.tool.ncc.basic;

import java.io.Serializable;

import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.variable.VarContext;

/** A CellContext contains a Cell and a VarContext for
 * evaluating the variables in the Cell. */ 
public class CellContext implements Serializable {
	static final long serialVersionUID = 0;
	
	public final Cell cell;
	public final VarContext context;
	public CellContext(Cell cell, VarContext context) {
		this.cell = cell;
		this.context = context;
	}
	@Override
	public boolean equals(Object o) {
		if (!(o instanceof CellContext)) return false;
		CellContext cc = (CellContext) o;
		return cell==cc.cell && context==cc.context;
	}
	@Override
	public int hashCode() {
		return cell.hashCode() * context.hashCode();
	}
}
