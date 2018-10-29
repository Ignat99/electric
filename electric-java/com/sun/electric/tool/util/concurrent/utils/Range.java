/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Range.java
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
package com.sun.electric.tool.util.concurrent.utils;

/**
 * 
 * This class provides a interface for parallel for parameters
 * 
 * @author Felix Schmidt
 *
 */
public class Range {
	protected int start;
	protected int end;
	protected int step;

	public Range(int start, int end, int step) {
		super();
		this.start = start;
		this.end = end;
		this.step = step;
	}

	public int start() {
		return start;
	}

	public int end() {
		return end;
	}

	public int step() {
		return step;
	}
	
	/**
	 * 
	 * @param r1
	 * @param r2
	 * @return the union of the two given Ranges.
	 */
	public static Range union(Range r1, Range r2) {
		return null;
	}
	
	/**
	 * 
	 * @param r1
	 * @param r2
	 * @return the intersection of the two given Ranges.
	 */
	public static Range intersect(Range r1, Range r2) {
		Range result = null;
		
		Range tmpLower = (r1.start < r2.start) ? r1 : r2;
		Range tmpUpper = (r1.start < r2.start) ? r2 : r1;
		
		
		
		return result;
	}
	
	/**
	 * 
	 * @param value
	 * @return true if the value is in this range.
	 */
	public boolean contains(int value) {
		return false;
	}
}
