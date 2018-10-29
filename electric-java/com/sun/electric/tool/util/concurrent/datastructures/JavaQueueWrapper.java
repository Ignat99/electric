/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: JavaDataStructureWrapper.java
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

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.sun.electric.tool.util.concurrent.utils.ConcurrentCollectionFactory;

/**
 * @author Felix Schmidt
 * 
 */
public class JavaQueueWrapper<T> extends IStructure<T> {

	private Queue<T> internalStructure;

	public JavaQueueWrapper(Queue<T> collection) {
		this.internalStructure = collection;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.electric.tool.util.IStructure#add(java.lang.Object)
	 */
	@Override
	public void add(T item) {
		internalStructure.offer(item);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.electric.tool.util.IStructure#isEmpty()
	 */
	@Override
	public boolean isEmpty() {
		return (internalStructure.size() == 0);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.electric.tool.util.IStructure#remove()
	 */
	@Override
	public T remove() {
		return internalStructure.poll();
	}

	public static <T> JavaQueueWrapper<T> createConcurrentQueue() {
		ConcurrentLinkedQueue<T> tasks = ConcurrentCollectionFactory.createConcurrentLinkedQueue();
		return new JavaQueueWrapper<T>(tasks);
	}

}
