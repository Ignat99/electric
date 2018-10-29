/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: SchedulingTest.java
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

import java.util.Random;

import org.junit.Ignore;
import org.junit.Test;

import com.sun.electric.tool.util.concurrent.Parallel;
import com.sun.electric.tool.util.concurrent.datastructures.IStructure;
import com.sun.electric.tool.util.concurrent.datastructures.WorkStealingStructure;
import com.sun.electric.tool.util.concurrent.debug.Debug;
import com.sun.electric.tool.util.concurrent.debug.StealTracker;
import com.sun.electric.tool.util.concurrent.exceptions.PoolExistsException;
import com.sun.electric.tool.util.concurrent.patterns.PForTask;
import com.sun.electric.tool.util.concurrent.patterns.PTask;
import com.sun.electric.tool.util.concurrent.runtime.Scheduler;
import com.sun.electric.tool.util.concurrent.runtime.taskParallel.ThreadPool;
import com.sun.electric.tool.util.concurrent.utils.BlockedRange2D;
import com.sun.electric.tool.util.concurrent.utils.ConcurrentCollectionFactory;
import com.sun.electric.util.ElapseTimer;

/**
 * @author Felix Schmidt
 * 
 */
public class SchedulingTest {

	private static int[][] matA;
	private static int[][] matB;
	private static Integer[][] matCPar;
	private static Integer[][] matCSer;
	private static int size = 700;
	private static final int numOfThreads = 8;

	@Ignore
	@Test
	public void balancingTest() throws PoolExistsException,
			InterruptedException {
		Debug.setDebug(true);

		Random rand = new Random(System.currentTimeMillis());

		matA = new int[size][size];
		matB = new int[size][size];
		matCPar = new Integer[size][size];
		matCSer = new Integer[size][size];

		for (int i = 0; i < size; i++) {
			for (int j = 0; j < size; j++) {
				matA[i][j] = rand.nextInt(100);
				matB[i][j] = rand.nextInt(100);
				matCPar[i][j] = 0;
				matCSer[i][j] = 0;
			}
		}

		System.out.println("==============================================");
		System.out.println("==                  Queue                   ==");

		IStructure<PTask> structure = ConcurrentCollectionFactory
				.createLockFreeQueue();
		ElapseTimer tQueue = this.runMatrixMultiplication(structure);

		System.out.println("==============================================");
		System.out.println("==============================================");
		System.out.println("==                  Stack                   ==");

		structure = ConcurrentCollectionFactory.createLockFreeStack();
		ElapseTimer tStack = this.runMatrixMultiplication(structure);

		System.out.println("==============================================");
		System.out.println("==============================================");
		System.out.println("==                Stealing                  ==");

		structure = WorkStealingStructure.createForThreadPool(numOfThreads);
		ElapseTimer tSteal = this.runMatrixMultiplication(structure);
		System.out.println("steals: "
				+ StealTracker.getInstance().getStealCounter());

		System.out.println("==============================================");

		System.out.println("Queue:    " + tQueue.toString());
		System.out.println("Stack:    " + tStack.toString());
		System.out.println("Stealing: " + tSteal.toString());
	}

	private ElapseTimer runMatrixMultiplication(IStructure<PTask> structure)
			throws PoolExistsException, InterruptedException {
		ElapseTimer timer = ElapseTimer.createInstance();
		ThreadPool pool = ThreadPool.initialize(structure, numOfThreads);
		timer.start();
		Parallel.For(new BlockedRange2D(0, size, 64, 0, size, 64),
				new MatrixMultTask(size));
		timer.end();
		pool.shutdown();
		return timer;
	}

	public static class MatrixMultTask extends PForTask<BlockedRange2D> {

		private int size;

		public MatrixMultTask(int n) {
			this.size = n;
		}

		@Override
		public void execute() {
			for (int i = range.row().start(); i < range.row().end(); i++) {
				for (int j = range.col().start(); j < range.col().end(); j++) {
					for (int k = 0; k < this.size; k++) {
						matCPar[i][j] += matA[i][k] * matB[k][j];
					}
				}
			}

		}

	}

	@Test
	public void testGetSchedulers() {
		System.out.println(Scheduler.getAvailableScheduler());
	}

}
