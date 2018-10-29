/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: UnboundedDEQueue.java
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

import java.util.concurrent.atomic.AtomicInteger;


/**
 * Unbounded double ended data structure - thread safe - unbounded
 * 
 * @author Felix Schmidt
 */
public class UnboundedDEQueue<T> extends IDEStructure<T> {

	private volatile CircularArray<T> elements;
	private volatile int bottom;
	private AtomicInteger top;

	/**
	 * Constructor
	 */
	public UnboundedDEQueue(int LOG_CAPACITY) {
		elements = new CircularArray<T>(LOG_CAPACITY);
		top = new AtomicInteger(0);
		bottom = 0;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sun.electric.tool.placement.forceDirected2.utils.concurrent.IDEStructure
	 * #getFromTop()
	 */
	@Override
	public T getFromTop() {
		int oldTop = top.get();
		int newTop = oldTop + 1;
		int oldBottom = bottom;
		int size = oldBottom - oldTop;
		if (size <= 0) {
			return null;
		}
		T elem = elements.get(oldTop);
		if (top.compareAndSet(oldTop, newTop))
			return elem;
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sun.electric.tool.placement.forceDirected2.utils.concurrent.IStructure
	 * #add(java.lang.Object)
	 */
	@Override
	public void add(T item) {
		int oldBottom = bottom;
		int oldTop = top.get();
		CircularArray<T> currentElements = elements;
		int size = oldBottom - top.get();
		if (size >= currentElements.getCapacity() - 1) {
			currentElements = currentElements.resize(oldBottom, oldTop);
			elements = currentElements;
		}
		elements.add(item, oldBottom);
		bottom = oldBottom + 1;

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sun.electric.tool.placement.forceDirected2.utils.concurrent.IStructure
	 * #get()
	 */
	@Override
	public T remove() {
		bottom--;
		int oldTop = top.get();
		int newTop = oldTop + 1;
		int size = bottom - oldTop;
		if (size < 0) {
			bottom = oldTop;
			return null;
		}
		T item = elements.get(bottom);
		if (size > 0) {
			return item;
		}
		if (!top.compareAndSet(oldTop, newTop)) {
			item = null;
		}
		bottom = oldTop + 1;
		return item;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sun.electric.tool.placement.forceDirected2.utils.concurrent.IStructure
	 * #isEmpty()
	 */
	@Override
	public boolean isEmpty() {
		return (bottom <= top.get());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.electric.tool.util.IDEStructure#isFull()
	 */
	@Override
	@Deprecated
	public boolean isFull() {
		throw new UnsupportedOperationException();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.electric.tool.util.IDEStructure#tryAdd(java.lang.Object)
	 */
	@Override
	@Deprecated
	public boolean tryAdd(T item) {
		throw new UnsupportedOperationException();
	}

}
