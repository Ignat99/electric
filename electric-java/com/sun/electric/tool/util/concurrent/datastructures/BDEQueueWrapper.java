/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: BDEQueueWrapper.java
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
package com.sun.electric.tool.util.concurrent.datastructures;


/**
 * 
 * Wrapper for double ended data structures. Use this class if you want to
 * provide a IStructure interface to the user.
 * 
 * @param <T>
 * 
 * @author Felix Schmidt
 */
public class BDEQueueWrapper<T> extends IStructure<T> {

	private IDEStructure<T> queue;
	private long owner;

	public BDEQueueWrapper(IDEStructure<T> queue, long owner) {
		this.queue = queue;
		this.owner = owner;
	}

	@Override
	public void add(T item) {
		queue.add(item);
	}

	@Override
	public boolean isEmpty() {
		return queue.isEmpty();
	}

	@Override
	public T remove() {
		long curThread = Thread.currentThread().getId();
		if (curThread == owner) {
			return queue.remove();
		} else {
			return queue.getFromTop();
		}
	}

}
