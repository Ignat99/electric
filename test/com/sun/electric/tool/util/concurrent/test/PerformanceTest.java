/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: PerformanceTest.java
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

import java.io.IOException;
import java.util.Random;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import com.sun.electric.tool.util.concurrent.Parallel;
import com.sun.electric.tool.util.concurrent.datastructures.WorkStealingStructure;
import com.sun.electric.tool.util.concurrent.debug.StealTracker;
import com.sun.electric.tool.util.concurrent.exceptions.PoolExistsException;
import com.sun.electric.tool.util.concurrent.patterns.PForTask;
import com.sun.electric.tool.util.concurrent.patterns.PReduceJob.PReduceTask;
import com.sun.electric.tool.util.concurrent.runtime.taskParallel.ThreadPool;
import com.sun.electric.tool.util.concurrent.utils.BlockedRange1D;

/**
 * @author Felix Schmidt
 *
 */
public class PerformanceTest
{

    private double[] data;
    private static final int size = 1000000000 / 8;
    private float[][] matA;
    private static final int matSize = 15000;
    private static final int NUMBER_OF_THREADS = 8;

    @Ignore
    @Test
    public void testSum() throws InterruptedException, PoolExistsException
    {

        System.out.println("init ...");

        Random rand = new Random(System.currentTimeMillis());
        data = new double[size];
        Integer sersum = 0;
        for (int i = 0; i < size; i++)
        {
            data[i] = rand.nextDouble();
        }

        System.out.println("serial ...");

        long start = System.currentTimeMillis();

        sersum = calcSum();

        long ser = System.currentTimeMillis() - start;

        start = System.currentTimeMillis();

        int sersum2 = 0;
        for (int i = 0; i < size; i++)
        {
            sersum2 += data[i];
        }

        long ser2 = System.currentTimeMillis() - start;

        System.out.println("parallel ...");

        ThreadPool.initialize(WorkStealingStructure.createForThreadPool(NUMBER_OF_THREADS), NUMBER_OF_THREADS);

        start = System.currentTimeMillis();

        Integer parsum = Parallel.Reduce(new BlockedRange1D(0, size, size / NUMBER_OF_THREADS), new SumTask());

        long par = System.currentTimeMillis() - start;

        ThreadPool.getThreadPool().shutdown();
        data = null;

        Assert.assertEquals(sersum, parsum);

        System.out.println("sersum " + sersum + " time: " + ser);
        System.out.println("sersum2 " + sersum2 + " time: " + ser2);
        System.out.println("parsum " + parsum + " time: " + par);
        System.out.println("speedup: " + ((double)ser / (double)par));

        StealTracker.getInstance().printStatistics();

    }

    @Ignore
    @Test
    public void testMatrixTranspose() throws PoolExistsException, InterruptedException
    {
        System.out.println("init ...");
        matA = TestHelper.createMatrix(matSize, matSize);

        ThreadPool.initialize(WorkStealingStructure.createForThreadPool(1), 1);

        System.out.println("serial ...");
        long start = System.currentTimeMillis();

        Parallel.For(new BlockedRange1D(0, matSize, 256), new TransposeMatrix());

        long ser = System.currentTimeMillis() - start;

        ThreadPool.getThreadPool().shutdown();

        ThreadPool.initialize(WorkStealingStructure.createForThreadPool(NUMBER_OF_THREADS), NUMBER_OF_THREADS);

        System.out.println("parallel ...");
        start = System.currentTimeMillis();

        Parallel.For(new BlockedRange1D(0, matSize, 256), new TransposeMatrix());

        long par = System.currentTimeMillis() - start;

        ThreadPool.getThreadPool().shutdown();
        matA = null;

        System.out.println(" ser time   : " + ser);
        System.out.println(" par time   : " + par);
        System.out.println(" speedup: " + ((double)ser / (double)par));

        StealTracker.getInstance().printStatistics();
    }

    public static void main(String[] args) throws InterruptedException, PoolExistsException, IOException
    {

        PerformanceTest test = new PerformanceTest();

        System.in.read();

        test.testSum();

    }

    private int calcSum()
    {
        int sum = 0;
        for (int i = 0; i < size; i++)
        {
            sum += data[i];
        }
        return sum;
    }

    public class TransposeMatrix extends PForTask<BlockedRange1D>
    {

        /*
         * (non-Javadoc)
         * 
         * @see
         * com.sun.electric.tool.util.concurrent.patterns.PForJob.PForTask#execute
         * (com.sun.electric.tool.util.concurrent.patterns.PForJob.BlockedRange)
         */
        @Override
        public void execute()
        {

            for (int i = range.start(); i < range.end(); i++)
            {
                for (int j = 0; j < i - 1; j++)
                {
                    float tmp = matA[j][i];
                    matA[j][i] = matA[i][j];
                    matA[i][j] = tmp;
                }
            }

        }

    }

    public class SumTask extends PReduceTask<Integer, BlockedRange1D>
    {

        private int localSum;

        /*
         * (non-Javadoc)
         * 
         * @see
         * com.sun.electric.tool.util.concurrent.patterns.PReduceJob.PReduceTask
         * #reduce(com.sun.electric.tool.util.concurrent.patterns.PReduceJob.
         * PReduceTask)
         */
        @Override
        public synchronized Integer reduce(PReduceTask<Integer, BlockedRange1D> other)
        {
            SumTask tmpOther = (SumTask)other;
            if (!this.equals(tmpOther))
            {
                localSum += tmpOther.localSum;
            }

            return localSum;
        }

        /*
         * (non-Javadoc)
         * 
         * @see
         * com.sun.electric.tool.util.concurrent.patterns.PForJob.PForTask#execute
         * (com.sun.electric.tool.util.concurrent.patterns.PForJob.BlockedRange)
         */
        @Override
        public void execute()
        {
            for (int i = range.start(); i < range.end(); i++)
            {
                localSum += data[i];
            }
        }

    }

}
