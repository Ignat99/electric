/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: StealTracker.java
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
package com.sun.electric.tool.util.concurrent.debug;


/**
 * @author Felix Schmidt
 * 
 */
public class StealTracker implements IDebug {

	private static StealTracker instance = new StealTracker();
	private volatile int stealCounter = 0;

	private StealTracker() {

	}

	public static StealTracker getInstance() {
		return StealTracker.instance;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.electric.tool.util.concurrent.debug.IDebug#printStatistics()
	 */
	public void printStatistics() {
		System.out.println("Steal counter: " + stealCounter);
	}

	public void countSteal() {
		this.stealCounter++;
	}

	public int getStealCounter() {
		return this.stealCounter;
	}

}
