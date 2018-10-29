/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: PWhileJob.java
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
package com.sun.electric.tool.util.concurrent.patterns;

import com.sun.electric.tool.util.concurrent.datastructures.IStructure;

/**
 * 
 * While job, do the work parallel while there are elements in the queue
 * 
 * @param <T>
 * 
 * @author Felix Schmidt
 */
public class PWhileJob<T> extends PJob {

	private IStructure<T> items;
	private PWhileTask<T> task;

	public PWhileJob(IStructure<T> items, PWhileTask<T> task) {
		this.items = items;
		this.task = task;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.electric.tool.util.concurrent.patterns.PJob#execute()
	 */
	@Override
	public void execute() {

		int numOfThreads = this.pool.getPoolSize();

		for (int i = 0; i < numOfThreads; i++) {
			this.add(new WhileTaskWrapper<T>(this, task, items), i);
		}

		super.execute();
	}

	public abstract static class PWhileTask<T> extends PTask implements Cloneable {

		/**
		 * @param job
		 */
		public PWhileTask(PJob job) {
			super(job);
		}

		public PWhileTask() {
			super(null);
		}

		public abstract void execute(T item);

		@Override
		public final void execute() {
		}
	}

	/**
	 * 
	 * Wrapper class for {@link PWhileTask} (internal)
	 * 
	 * @param <T>
	 */
	public final static class WhileTaskWrapper<T> extends PTask {

		private IStructure<T> items;
		private PWhileTask<T> task;

		/**
		 * @param job
		 */
		public WhileTaskWrapper(PJob job, PWhileTask<T> task, IStructure<T> items) {
			super(job);
			this.items = items;
			this.task = task;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see com.sun.electric.tool.util.concurrent.patterns.PTask#execute()
		 */
		@SuppressWarnings("unchecked")
		@Override
		public void execute() {
			T item = null;

			item = items.remove();

			while (item != null) {
				try {
					PWhileTask<T> tmp = (PWhileTask<T>) task.clone();
					tmp.execute(item);
					item = items.remove();
				} catch (CloneNotSupportedException e) {
					e.printStackTrace();
				}
			}
			return;
		}
	}

}
