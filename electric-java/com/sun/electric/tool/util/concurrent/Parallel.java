/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Parallel.java
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
package com.sun.electric.tool.util.concurrent;

import com.sun.electric.tool.util.concurrent.datastructures.IStructure;
import com.sun.electric.tool.util.concurrent.patterns.PForJob;
import com.sun.electric.tool.util.concurrent.patterns.PForTask;
import com.sun.electric.tool.util.concurrent.patterns.PJob;
import com.sun.electric.tool.util.concurrent.patterns.PReduceJob;
import com.sun.electric.tool.util.concurrent.patterns.PReduceJob.PReduceTask;
import com.sun.electric.tool.util.concurrent.patterns.PWhileJob;
import com.sun.electric.tool.util.concurrent.patterns.PWhileJob.PWhileTask;
import com.sun.electric.tool.util.concurrent.runtime.taskParallel.IThreadPool;
import com.sun.electric.tool.util.concurrent.utils.BlockedRange;

/**
 * This class simplifies the interface for the parallel base patterns
 * 
 * @author Felix Schmidt
 */
public class Parallel {
    
    public static <T extends BlockedRange<T>> void For(T range, PForTask<T> task, IThreadPool pool) {
        (new PForJob<T>(range, task, pool)).execute(); 
    }

	/**
	 * Parallel For Loop (1- and 2-dimensional)
	 * 
	 * @param range
	 *            1- or 2-dimensional
	 * @param task
	 *            task object (body of for loop)
	 */
	public static <T extends BlockedRange<T>> void For(T range, PForTask<T> task) {
		(new PForJob<T>(range, task)).execute();
	}

	/**
	 * Parallel reduce. Reduce is a parallel for loop with a result aggregation
	 * at the end of processing.
	 * 
	 * @param <T>
	 *            return type (implicit)
	 * @param range
	 *            1- or 2-dimensional
	 * @param task
	 *            body of reduce loop
	 * @return aggregated result
	 */
	public static <T, K extends BlockedRange<K>> T Reduce(K range, PReduceTask<T, K> task) {
		PReduceJob<T, K> pReduceJob = new PReduceJob<T, K>(range, task);
		pReduceJob.execute();

		return pReduceJob.getResult();
	}

	/**
	 * Parallel while loop: iterates while elements in the data structure
	 * 
	 * @param <T>
	 *            return type (implicit)
	 * @param data
	 *            data structure for work
	 * @param task
	 *            while loop body
	 */
	public static <T> void While(IStructure<T> data, PWhileTask<T> task) {
		PJob pWhileJob = new PWhileJob<T>(data, task);
		pWhileJob.execute();
	}

}
