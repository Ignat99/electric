/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Interval.java
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
package com.sun.electric.tool.generator.flag.router;

public class Interval {
	private double min, max;
	Interval(double min, double max) {this.min=min; this.max=max;}
	/** Merge two overlapping blockages into one */
	public void merge(double min, double max) {
		this.min = Math.min(this.min, min);
		this.max = Math.max(this.max, max); 
	}
	public double getMin() {return min;}
	public double getMax() {return max;}
	public String toString() {
		return "["+min+", "+max+"]";
	}
}
