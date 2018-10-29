/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: MultiThreadedRandomizer.java
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
package com.sun.electric.tool.util.concurrent.runtime;

import java.util.Random;

// FIXME this randomizer is not workable at all with two thread pools

/**
 * In multi-threaded environment it is important for randomizations that each
 * thread has its own randomizer. Each randomizer has its own start seed. So
 * best distribution is possible. This class provides a multi-threaded
 * randomizer. Each thread gets its own.
 * 
 * @author Felix Schmidt
 * 
 */
public class MultiThreadedRandomizer {

	private int numOfCores;
	private ThreadLocal<Random> randomizer;

	/**
	 * Constructor
	 * 
	 * @param numOfCores
	 */
	public MultiThreadedRandomizer(int numOfCores) {
		this.numOfCores = numOfCores;
		randomizer = new ThreadLocal<Random>();
	}

	/**
	 * This function returns a randomizer, which is according to the current
	 * thread. If there is no randomizer available return a new.
	 * 
	 * @return a assigned randomizer or a new one if no available
	 */
	public Random getRandomizer() {
		if(randomizer.get() == null) {
			randomizer.set(new Random(System.currentTimeMillis()));
		}
		
		return randomizer.get();
	}


}
