/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: TstUserInterface.java
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

import com.sun.electric.Main.UserInterfaceDummy;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.EDatabase;

/**
 * @author Felix Schmidt
 * 
 */
public class TstUserInterface extends UserInterfaceDummy {

	private Cell curCell = null;

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.electric.Main.UserInterfaceDummy#getDatabase()
	 */
	@Override
	public EDatabase getDatabase() {
		return EDatabase.serverDatabase();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.electric.Main.UserInterfaceDummy#getCurrentCell()
	 */
	@Override
	public Cell getCurrentCell() {
		if (this.curCell == null)
			return super.getCurrentCell();
		else
			return this.curCell;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.electric.Main.UserInterfaceDummy#needCurrentCell()
	 */
	@Override
	public Cell needCurrentCell() {
		if (this.curCell == null)
			return super.needCurrentCell();
		else
			return this.curCell;
	}

	public void setCurrentCell(Cell cell) {
		this.curCell = cell;
	}

}
