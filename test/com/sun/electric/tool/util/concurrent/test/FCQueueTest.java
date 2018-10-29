/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: FCQueueTest.java
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

import com.sun.electric.tool.util.concurrent.Parallel;
import com.sun.electric.tool.util.concurrent.datastructures.FCQueue;
import com.sun.electric.tool.util.concurrent.datastructures.IStructure;
import com.sun.electric.tool.util.concurrent.exceptions.PoolExistsException;
import com.sun.electric.tool.util.concurrent.patterns.PForTask;
import com.sun.electric.tool.util.concurrent.patterns.PTask;
import com.sun.electric.tool.util.concurrent.runtime.Scheduler.SchedulingStrategy;
import com.sun.electric.tool.util.concurrent.runtime.Scheduler.UnknownSchedulerException;
import com.sun.electric.tool.util.concurrent.runtime.taskParallel.ThreadPool;
import com.sun.electric.tool.util.concurrent.utils.BlockedRange2D;
import com.sun.electric.tool.util.concurrent.utils.ConcurrentCollectionFactory;
import com.sun.electric.util.ElapseTimer;

/**
 * @author Felix Schmidt
 * 
 */
public class FCQueueTest {

	private static int[][] matA;
	private static int[][] matB;
	private static Integer[][] matCParWS;
	private static Integer[][] matCParFC;
	private static int size = 1000;

	@Test
	public void testMatrixMult() {
		matA = TestHelper.createMatrix(size, size, 100);
		matB = TestHelper.createMatrix(size, size, 100);
		matCParWS = TestHelper.createMatrixIntegerNull(size, size, 100);
		matCParFC = TestHelper.createMatrixIntegerNull(size, size, 100);
		
		this.workStealing();
		this.fcQueue();

	}
	
	@Test
	public void testMatrixMultMultQueues() throws UnknownSchedulerException {
		matA = TestHelper.createMatrix(size, size, 100);
		matB = TestHelper.createMatrix(size, size, 100);
		matCParFC = TestHelper.createMatrixIntegerNull(size, size, 100);
		matCParWS = TestHelper.createMatrixIntegerNull(size, size, 100);
		
		//this.workStealing();
		this.fcQueueMultQueues();

	}

	private void workStealing() {

		try {
			IStructure<PTask> tasks = ConcurrentCollectionFactory.createLockFreeQueue();
			ThreadPool.initialize(tasks, 2);
		} catch (PoolExistsException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		ElapseTimer time = ElapseTimer.createInstance();
		time.start();
		Parallel.For(new BlockedRange2D(0, size, 128, 0, size, 128), new MatrixMultTask(size,
				matCParWS));
		time.end();
		time.print("Work stealing: ");

		ThreadPool.killPool();

	}

	private void fcQueue() {

		FCQueue<PTask> tasks = ConcurrentCollectionFactory.createFCQueue();
		try {
			ThreadPool.initialize(tasks, 2);
		} catch (PoolExistsException e) {
			e.printStackTrace();
		}

		ElapseTimer time = ElapseTimer.createInstance();
		time.start();
		Parallel.For(new BlockedRange2D(0, size, 128, 0, size, 129), new MatrixMultTask(size,
				matCParFC));
		time.end();
		time.print("FCQueue: ");

		ThreadPool.killPool();

	}
	
	private void fcQueueMultQueues() throws UnknownSchedulerException {

		try {
			ThreadPool.initialize(SchedulingStrategy.multipleQueues, 2);
		} catch (PoolExistsException e) {
			e.printStackTrace();
		}

		ElapseTimer time = ElapseTimer.createInstance();
		time.start();
		Parallel.For(new BlockedRange2D(0, size, 128, 0, size, 128), new MatrixMultTask(size,
				matCParFC));
		time.end();
		time.print("FCQueue Multiple: ");

		ThreadPool.killPool();

	}

	public static class MatrixMultTask extends PForTask<BlockedRange2D> {

		private int size;
		private Integer[][] result;

		public MatrixMultTask(int n, Integer[][] result) {
			this.size = n;
			this.result = result;
		}

		@Override
		public void execute() {			
			for (int i = range.row().start(); i < range.row().end(); i++) {
				for (int j = range.col().start(); j < range.col().end(); j++) {
					int sum = 0;
					for (int k = 0; k < this.size; k++) {
						sum += matA[i][k] * matB[k][j];
					}
					synchronized (result[i][j]) {
						result[i][j] = sum;
					}
				}
			}

		}

	}

}
