/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: PoolWorkerStrategy.java
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
package com.sun.electric.tool.util.concurrent.runtime.taskParallel;

import com.sun.electric.tool.util.concurrent.datastructures.IStructure;
import com.sun.electric.tool.util.concurrent.patterns.PTask;
import com.sun.electric.tool.util.concurrent.runtime.WorkerStrategy;

/**
 * @author Felix Schmidt
 *
 */
public abstract class PoolWorkerStrategy extends WorkerStrategy {
	
	protected volatile boolean pleaseWait = false;
	private IStructure<PTask> taskPool = null;
	
	public void pleaseWait() {
		this.pleaseWait = true;
	}

	public void pleaseWakeUp() {
		this.pleaseWait = false;
	}

	public synchronized void checkForWait() {
		while (pleaseWait) {
			try {
				this.wait();
			} catch (Exception e) {}
		}
	}
	
	public void trigger() {

	}
	
	/**
	 * @param taskPool the taskPool to set
	 */
	protected void setTaskPool(IStructure<PTask> taskPool) {
		this.taskPool = taskPool;
	}

	/**
	 * @return the taskPool
	 */
	protected IStructure<PTask> getTaskPool() {
		return taskPool;
	}

}
