/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: NetValues.java
 * Author: Felix Schmidt
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
package com.sun.electric.tool.erc.wellcheck;

public class NetValues {
	private int index;

	private static int indexValues;
	public static int numberOfMerges = 0;

	public static synchronized void reset() {
		indexValues = 0;
	}

	static synchronized int getFreeIndex() {
		return indexValues++;
	}

	public int getIndex() {
		return index;
	}

	public NetValues() {
		index = getFreeIndex();
	}

	public synchronized void merge(NetValues other) {
		if (this.index == other.index)
			return;
		other.index = this.index;

		numberOfMerges++;
	}
}
