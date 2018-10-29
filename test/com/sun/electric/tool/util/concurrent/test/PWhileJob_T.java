/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: PWhileJob_T.java
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
package com.sun.electric.tool.util.concurrent.test;

import org.junit.Test;

import com.sun.electric.tool.util.concurrent.datastructures.IStructure;
import com.sun.electric.tool.util.concurrent.exceptions.PoolExistsException;
import com.sun.electric.tool.util.concurrent.patterns.PJob;
import com.sun.electric.tool.util.concurrent.patterns.PWhileJob;
import com.sun.electric.tool.util.concurrent.patterns.PWhileJob.PWhileTask;
import com.sun.electric.tool.util.concurrent.runtime.Scheduler.UnknownSchedulerException;
import com.sun.electric.tool.util.concurrent.runtime.taskParallel.ThreadPool;
import com.sun.electric.tool.util.concurrent.utils.ConcurrentCollectionFactory;

/**
 * @author Felix Schmidt
 * 
 */
public class PWhileJob_T {

	@Test
	public void testPWhileJob() throws PoolExistsException, InterruptedException, UnknownSchedulerException {
		ThreadPool.initialize();

		IStructure<Integer> data = ConcurrentCollectionFactory.createLockFreeStack();

		for (int i = 0; i < 100; i++) {
			data.add(new Integer(i));
		}

		PJob whileJob = new PWhileJob<Integer>(data, new WhileTestTask());
		whileJob.execute();

		ThreadPool.getThreadPool().shutdown();
	}

	public static class WhileTestTask extends PWhileTask<Integer> {

		/*
		 * (non-Javadoc)
		 * 
		 * @see
		 * com.sun.electric.tool.util.concurrent.patterns.PWhileJob.PWhileTask
		 * #execute(java.lang.Object)
		 */
		@Override
		public void execute(Integer item) {

			System.out.println(item);

		}

	}

}
