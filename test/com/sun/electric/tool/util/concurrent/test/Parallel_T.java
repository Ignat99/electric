/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Parallel_T.java
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

import org.junit.Assert;
import org.junit.Test;

import com.sun.electric.tool.util.concurrent.Parallel;
import com.sun.electric.tool.util.concurrent.datastructures.IStructure;
import com.sun.electric.tool.util.concurrent.datastructures.LockFreeQueue;
import com.sun.electric.tool.util.concurrent.exceptions.PoolExistsException;
import com.sun.electric.tool.util.concurrent.patterns.PTask;
import com.sun.electric.tool.util.concurrent.runtime.Scheduler.UnknownSchedulerException;
import com.sun.electric.tool.util.concurrent.runtime.taskParallel.ThreadPool;
import com.sun.electric.tool.util.concurrent.test.PForJob_T.TestForTask;
import com.sun.electric.tool.util.concurrent.test.PReduceJob_T.PITask;
import com.sun.electric.tool.util.concurrent.test.PWhileJob_T.WhileTestTask;
import com.sun.electric.tool.util.concurrent.utils.BlockedRange1D;
import com.sun.electric.tool.util.concurrent.utils.ConcurrentCollectionFactory;

/**
 * @author Felix Schmidt
 *
 */
public class Parallel_T
{

    @Test
    public void testParallelFor() throws InterruptedException, PoolExistsException, UnknownSchedulerException
    {
        ThreadPool pool = ThreadPool.initialize();
        Parallel.For(new BlockedRange1D(0, 100, 4), new TestForTask());
        pool.shutdown();
    }

    @Test
    public void testParallelForWithQueue() throws InterruptedException, PoolExistsException
    {
        IStructure<PTask> tasks = new LockFreeQueue<PTask>();
        ThreadPool pool = ThreadPool.initialize(tasks);
        Parallel.For(new BlockedRange1D(0, 100, 4), new TestForTask());
        pool.shutdown();
    }

    @Test
    public void testParallelReduce() throws InterruptedException, PoolExistsException, UnknownSchedulerException
    {
        ThreadPool pool = ThreadPool.initialize();
        int stepW = 1000000;
        double step = 1.0 / stepW;
        double pi = Parallel.Reduce(new BlockedRange1D(0, stepW, 4), new PITask(step));
        pool.shutdown();
        Assert.assertEquals(Math.PI, pi, 0.0001);
        System.out.println("pi = " + pi);
    }

    @Test
    public void testParallelReduceWithQueue() throws InterruptedException, PoolExistsException
    {
        IStructure<PTask> tasks = new LockFreeQueue<PTask>();
        ThreadPool pool = ThreadPool.initialize(tasks);
        int stepW = 1000000;
        double step = 1.0 / stepW;
        double pi = Parallel.Reduce(new BlockedRange1D(0, stepW, 4), new PITask(step));
        pool.shutdown();
        Assert.assertEquals(Math.PI, pi, 0.00001);
        System.out.println("pi = " + pi);
    }

    @Test
    public void testParallelWhile() throws PoolExistsException, InterruptedException, UnknownSchedulerException
    {
        ThreadPool pool = ThreadPool.initialize();
        IStructure<Integer> data = ConcurrentCollectionFactory.createLockFreeStack();
        for (int i = 0; i < 10; i++)
            data.add(i);
        Parallel.While(data, new WhileTestTask());
        pool.shutdown();
    }

    @Test
    public void testParallelWhileWithQueue() throws PoolExistsException, InterruptedException
    {
        IStructure<PTask> tasks = new LockFreeQueue<PTask>();
        ThreadPool pool = ThreadPool.initialize(tasks);
        IStructure<Integer> data = ConcurrentCollectionFactory.createLockFreeStack();
        for (int i = 0; i < 10; i++)
            data.add(i);
        Parallel.While(data, new WhileTestTask());
        pool.shutdown();
    }

}
