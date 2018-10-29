/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: PForJob_T.java
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

import java.io.BufferedWriter;
import java.io.FileWriter;

import org.junit.Assert;
import org.junit.Test;

import com.sun.electric.tool.util.concurrent.datastructures.FCQueue;
import com.sun.electric.tool.util.concurrent.datastructures.IStructure;
import com.sun.electric.tool.util.concurrent.datastructures.WorkStealingStructure;
import com.sun.electric.tool.util.concurrent.exceptions.PoolExistsException;
import com.sun.electric.tool.util.concurrent.patterns.PForJob;
import com.sun.electric.tool.util.concurrent.patterns.PForTask;
import com.sun.electric.tool.util.concurrent.patterns.PTask;
import com.sun.electric.tool.util.concurrent.runtime.Scheduler;
import com.sun.electric.tool.util.concurrent.runtime.Scheduler.SchedulingStrategy;
import com.sun.electric.tool.util.concurrent.runtime.Scheduler.UnknownSchedulerException;
import com.sun.electric.tool.util.concurrent.runtime.taskParallel.ThreadPool;
import com.sun.electric.tool.util.concurrent.utils.BlockedRange1D;
import com.sun.electric.tool.util.concurrent.utils.BlockedRange2D;
import com.sun.electric.util.UniqueIDGenerator;

public class PForJob_T
{

    @Test
    public void testParallelFor() throws PoolExistsException, InterruptedException, UnknownSchedulerException
    {

        ThreadPool pool = ThreadPool.initialize(SchedulingStrategy.multipleQueues, 2);

        PForJob<BlockedRange1D> pforjob = new PForJob<BlockedRange1D>(new BlockedRange1D(0, 10, 2), new TestForTask());
        pforjob.execute();

        pool.shutdown();

    }

    public static class TestForTask extends PForTask<BlockedRange1D>
    {

        private static UniqueIDGenerator idGen = new UniqueIDGenerator(0);
        private int id = idGen.getUniqueId();

        @Override
        public void execute()
        {
            for (int i = range.start(); i < range.end(); i++)
            {
                System.out.println("task: " + id + ", " + i);
            }

        }

    }

    private static int[][] matA;
    private static int[][] matB;
    private static Integer[][] matCPar;
    private static Integer[][] matCSer;
    private static int size = 4;

    @Test
    public void testMatrixMultiply() throws PoolExistsException, InterruptedException
    {
        matA = TestHelper.createMatrix(size, size, 100);
        matB = TestHelper.createMatrix(size, size, 100);
        matCPar = TestHelper.createMatrixIntegerNull(size, size, 100);
        matCSer = TestHelper.createMatrixIntegerNull(size, size, 100);

        ThreadPool.initialize(new FCQueue<PTask>(), 8);

        long start = System.currentTimeMillis();
        PForJob<BlockedRange2D> pforjob = new PForJob<BlockedRange2D>(new BlockedRange2D(0, size, 10, 0, size, 10),
            new MatrixMultTask(size));
        pforjob.execute();

        long endPar = System.currentTimeMillis() - start;
        System.out.println(endPar);
        ThreadPool.killPool();

        start = System.currentTimeMillis();
        matrixMultSer();
        long endSer = System.currentTimeMillis() - start;
        System.out.println(endSer);
        System.out.println((double)endSer / (double)endPar);

        int nullChecker = 0;
        for (int i = 0; i < size; i++)
        {
            for (int j = 0; j < size; j++)
            {
                Assert.assertEquals(matCPar[i][j], matCSer[i][j]);
                if (matCPar[i][j] == 0)
                    nullChecker++;
            }
        }

        Assert.assertTrue(nullChecker < size * size);
    }

    @Test
    public void testMatrixMultiplyPerformance() throws PoolExistsException, InterruptedException
    {
        int sizePerf = 600;

        matA = TestHelper.createMatrix(sizePerf, sizePerf, 100);
        matB = TestHelper.createMatrix(sizePerf, sizePerf, 100);
        matCPar = TestHelper.createMatrixIntegerNull(sizePerf, sizePerf, 100);
        matCSer = TestHelper.createMatrixIntegerNull(sizePerf, sizePerf, 100);

        ThreadPool pool = ThreadPool.initialize(8);

        long start = System.currentTimeMillis();
        PForJob<BlockedRange2D> pforjob = new PForJob<BlockedRange2D>(new BlockedRange2D(0, sizePerf, 64, 0, sizePerf, 64),
            new MatrixMultTask(sizePerf));
        pforjob.execute();

        long endPar = System.currentTimeMillis() - start;
        System.out.println(endPar);
        pool.shutdown();

        pool = ThreadPool.initialize(1);

        start = System.currentTimeMillis();

        pforjob = new PForJob<BlockedRange2D>(new BlockedRange2D(0, sizePerf, 64, 0, sizePerf, 64),
            new MatrixMultTask(sizePerf));
        pforjob.execute();

        long endSer = System.currentTimeMillis() - start;

        pool.shutdown();

        System.out.println(endSer);
        System.out.println((double)endSer / (double)endPar);
    }

    @Test
    public void testMatrixMultiplyPerformanceWorkStealing() throws PoolExistsException,
        InterruptedException
    {
        int sizePerf = 600;

        matA = TestHelper.createMatrix(sizePerf, sizePerf, 100);
        matB = TestHelper.createMatrix(sizePerf, sizePerf, 100);
        matCPar = TestHelper.createMatrixIntegerNull(sizePerf, sizePerf, 100);
        matCSer = TestHelper.createMatrixIntegerNull(sizePerf, sizePerf, 100);

        IStructure<PTask> taskPool = WorkStealingStructure.createForThreadPool(8);
        ThreadPool pool = ThreadPool.initialize(taskPool, 8);

        long start = System.currentTimeMillis();
        PForJob<BlockedRange2D> pforjob = new PForJob<BlockedRange2D>(new BlockedRange2D(0, sizePerf, 10, 0, sizePerf, 10),
            new MatrixMultTask(sizePerf));
        pforjob.execute();

        long endPar = System.currentTimeMillis() - start;
        System.out.println(endPar);
        pool.shutdown();

        taskPool = WorkStealingStructure.createForThreadPool(1);
        pool = ThreadPool.initialize(taskPool, 1);

        start = System.currentTimeMillis();

        pforjob = new PForJob<BlockedRange2D>(new BlockedRange2D(0, sizePerf, 64, 0, sizePerf, 64),
            new MatrixMultTask(sizePerf));
        pforjob.execute();

        long endSer = System.currentTimeMillis() - start;

        pool.shutdown();

        System.out.println(endSer);
        System.out.println((double)endSer / (double)endPar);
    }

    private void matrixMultSer()
    {
        for (int i = 0; i < size; i++)
        {
            for (int j = 0; j < size; j++)
            {
                for (int k = 0; k < size; k++)
                {
                    matCSer[i][j] += matA[i][k] * matB[k][j];
                }
            }
        }
    }

    public static class MatrixMultTask extends PForTask<BlockedRange2D>
    {

        private int size;

        public MatrixMultTask(int n)
        {
            this.size = n;
        }

        @Override
        public void execute()
        {
            for (int i = range.row().start(); i < range.row().end(); i++)
            {
                for (int j = range.col().start(); j < range.col().end(); j++)
                {
                    int sum = 0;
                    for (int k = 0; k < this.size; k++)
                    {
                        sum += matA[i][k] * matB[k][j];
                    }
                    synchronized (matCPar[i][j])
                    {
                        matCPar[i][j] = sum;
                    }
                }
            }

        }

    }

    public static void main(String[] args) throws Exception
    {

        if (args.length != 5)
        {
            System.out
                .println("Usage: --threads=<#threads> --size=<size> --grain=<grain> --outfile=<outfile> --scheduler=<stack|queue|workStealing>");
            System.exit(1);
        }

        int numThreads = 1;
        int grain = 128;
        String outFile = "";
        SchedulingStrategy schedulingStrategy = null;

        for (String arg : args)
        {
            if (arg.startsWith("--threads"))
            {
                numThreads = TestHelper.extractValueFromArgInteger(arg);
            } else if (arg.startsWith("--size"))
            {
                size = TestHelper.extractValueFromArgInteger(arg);
            } else if (arg.startsWith("--grain"))
            {
                grain = TestHelper.extractValueFromArgInteger(arg);
            } else if (arg.startsWith("--outfile"))
            {
                outFile = TestHelper.extractValueFromArgString(arg);
            } else if (arg.startsWith("--scheduler"))
            {
                String tmpScheduler = TestHelper.extractValueFromArgString(arg);
                try
                {
                    schedulingStrategy = SchedulingStrategy.valueOf(tmpScheduler);
                } catch (Exception ex)
                {
                    System.out.println("No scheduler " + tmpScheduler + " available. Use: "
                        + Scheduler.getAvailableScheduler());
                    System.exit(1);
                }
            } else
            {
                System.out.println("Unexpected Parameter: " + arg);
                System.exit(1);
            }

        }

        matA = TestHelper.createMatrix(size, size, 100);
        matB = TestHelper.createMatrix(size, size, 100);
        matCPar = TestHelper.createMatrixIntegerNull(size, size, 100);

        ThreadPool pool = ThreadPool.initialize(schedulingStrategy, numThreads);

        long start = System.currentTimeMillis();
        PForJob<BlockedRange2D> pforjob = new PForJob<BlockedRange2D>(new BlockedRange2D(0, size, grain, 0, size, grain),
            new MatrixMultTask(size));
        pforjob.execute();

        long endPar = System.currentTimeMillis() - start;
        System.out.println(endPar);

        BufferedWriter bw = new BufferedWriter(new FileWriter(outFile, true));
        bw.write(numThreads + "," + size + "," + grain + "," + String.valueOf(endPar));
        bw.newLine();
        bw.flush();

        pool.shutdown();
    }
}
