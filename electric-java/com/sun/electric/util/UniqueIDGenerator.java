/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: UniqueIDGenerator.java
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
package com.sun.electric.util;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread safe unique id generator
 *
 */
public class UniqueIDGenerator {

	private final int start;
	private AtomicInteger current;

	public UniqueIDGenerator(int start) {
		this.start = start;
		this.current = new AtomicInteger(this.start);
	}

	/**
	 * Get unique identifier
	 * @return a unique identifier
	 */
	public int getUniqueId() {
		return this.current.incrementAndGet();
	}
}
