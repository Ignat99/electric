/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: PForJob.java
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

import java.util.List;

import com.sun.electric.tool.util.concurrent.runtime.taskParallel.IThreadPool;
import com.sun.electric.tool.util.concurrent.utils.BlockedRange;

/**
 * 
 * Runtime for parallel for
 * 
 * @author Felix Schmidt
 * 
 */
public class PForJob<T extends BlockedRange<T>> extends PJob {

	/**
	 * Constructor for 1- and 2-dimensional parallel for loops
	 * 
	 * @param range
	 * @param task
	 */
	public PForJob(T range, PForTask<T> task) {
		super();
		this.add(new SplitIntoTasks<T>(this, range, task), PJob.SERIAL);
	}

	public PForJob(T range, PForTask<T> task, IThreadPool pool) {
		super(pool);
		this.add(new SplitIntoTasks<T>(this, range, task), PJob.SERIAL);
	}

	/**
	 * 
	 * Task to create parallel for tasks (internal)
	 * 
	 */
	public final static class SplitIntoTasks<T extends BlockedRange<T>> extends PTask {

		private T range;
		private PForTask<T> task;

		public SplitIntoTasks(PJob job, T range, PForTask<T> task) {
			super(job);
			this.range = range;
			this.task = task;
		}

		/**
		 * This is the executor method of SplitIntoTasks. New for tasks will be
		 * created while a new range is available
		 */
		@Override
		public void execute() {
			int threadNum = job.getThreadPool().getPoolSize();
			for (int i = 0; i < threadNum; i++) {
				job.add(new SplitterTask<T>(job, range, task, i, threadNum));
			}
		}
	}

	public final static class SplitterTask<T extends BlockedRange<T>> extends PTask {
		private T range;
		private PForTask<T> task;

		public SplitterTask(PJob job, T range, PForTask<T> task, int number, int total) {
			super(job);
			this.range = range.createInstance(number, total);
			this.task = task;
		}

		/**
		 * This is the executor method of SplitIntoTasks. New for tasks will be
		 * created while a new range is available
		 */
		@SuppressWarnings("unchecked")
		@Override
		public void execute() {
			List<T> tmpRange;

			int step = job.getThreadPool().getPoolSize();
			while (((tmpRange = range.splitBlockedRange(step))) != null) {
				for (T tr : tmpRange) {
					try {
						PForTask<T> taskObj = (PForTask<T>) task.clone();
						taskObj.setRange(tr);
						taskObj.setPJob(job);
						job.add(taskObj, PJob.SERIAL);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
		}
	}

}
