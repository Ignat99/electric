/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: DESkipListWrapper.java
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

import java.util.List;

import com.sun.electric.tool.util.concurrent.utils.ConcurrentCollectionFactory;

/**
 * @author Felix Schmidt
 * 
 */
public class DEListWrapper<T> extends IDEStructure<T> {

	private List<T> data = ConcurrentCollectionFactory.createConcurrentList();

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.electric.tool.util.IDEStructure#getFromTop()
	 */
	@Override
	public synchronized T getFromTop() {
		if (data.size() > 0) {
			return data.remove(0);
		}
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.electric.tool.util.IDEStructure#isFull()
	 */
	@Override
	public boolean isFull() {
		// TODO Auto-generated method stub
		return false;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.electric.tool.util.IDEStructure#tryAdd(java.lang.Object)
	 */
	@Override
	public boolean tryAdd(T item) {
		throw new UnsupportedOperationException();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.electric.tool.util.IStructure#add(java.lang.Object)
	 */
	@Override
	public synchronized void add(T item) {
		data.add(item);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.electric.tool.util.IStructure#isEmpty()
	 */
	@Override
	public synchronized boolean isEmpty() {
		return data.size() == 0;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.electric.tool.util.IStructure#remove()
	 */
	@Override
	public synchronized T remove() {
		if (data.size() > 0) {
			return data.remove(data.size() - 1);
		}
		return null;
	}

}
