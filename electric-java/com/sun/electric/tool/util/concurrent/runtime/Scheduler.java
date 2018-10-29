/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Scheduler.java
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

import com.sun.electric.tool.util.concurrent.datastructures.IStructure;
import com.sun.electric.tool.util.concurrent.datastructures.MultipleQueuesStructure;
import com.sun.electric.tool.util.concurrent.datastructures.WorkStealingStructure;
import com.sun.electric.tool.util.concurrent.patterns.PTask;
import com.sun.electric.tool.util.concurrent.utils.ConcurrentCollectionFactory;

/**
 * @author fs239085
 * 
 */
public class Scheduler {

	public enum SchedulingStrategy {
		queue, stack, workStealing, multipleQueues, fcQueue;
	}

	public static IStructure<PTask> createScheduler(SchedulingStrategy strategy, int numOfThreads)
			throws UnknownSchedulerException {
		IStructure<PTask> result = null;

		if (strategy.equals(SchedulingStrategy.queue)) {
			result = ConcurrentCollectionFactory.createLockFreeQueue();
		} else if (strategy.equals(SchedulingStrategy.stack)) {
			result = ConcurrentCollectionFactory.createLockFreeStack();
		} else if (strategy.equals(SchedulingStrategy.workStealing)) {
			result = WorkStealingStructure.createForThreadPool(numOfThreads);
		} else if (strategy.equals(SchedulingStrategy.multipleQueues)) {
			result = new MultipleQueuesStructure<PTask>(numOfThreads);
		} else if (strategy.equals(SchedulingStrategy.fcQueue)) {
			result = ConcurrentCollectionFactory.createFCQueue();
		} else {
			throw new UnknownSchedulerException();
		}

		return result;
	}

	public static class UnknownSchedulerException extends Exception {

	}

	public static String getAvailableScheduler() {
		StringBuilder builder = new StringBuilder();
		for (SchedulingStrategy strategy : SchedulingStrategy.values()) {
			builder.append(strategy.toString());
			builder.append(", ");
		}
		return builder.substring(0, builder.length() - 2);
	}

}
