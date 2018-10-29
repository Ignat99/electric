/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: SeaOfGatesEngineOld.java
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
package com.sun.electric.tool.routing.seaOfGates;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

import com.sun.electric.tool.util.concurrent.exceptions.PoolExistsException;
import com.sun.electric.tool.util.concurrent.patterns.PJob;
import com.sun.electric.tool.util.concurrent.patterns.PTask;
import com.sun.electric.tool.util.concurrent.runtime.Scheduler.SchedulingStrategy;
import com.sun.electric.tool.util.concurrent.runtime.Scheduler.UnknownSchedulerException;
import com.sun.electric.tool.util.concurrent.runtime.taskParallel.ThreadPool;
import com.sun.electric.tool.util.concurrent.runtime.taskParallel.ThreadPool.ThreadPoolType;
import com.sun.electric.util.CollectionFactory;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * @author Felix Schmidt
 *
 */
class SeaOfGatesEngineNonoverlappingBatch extends SeaOfGatesEngine
{
    private final SeaOfGatesEngineFactory.SeaOfGatesEngineType version;

    public SeaOfGatesEngineNonoverlappingBatch(SeaOfGatesEngineFactory.SeaOfGatesEngineType version) {
        this.version = version;
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * com.sun.electric.tool.routing.seaOfGates.SeaOfGatesEngine#doRoutingParallel
     * (int, java.util.List)
     */
    @Override
    protected void doRoutingParallel(int numberOfThreads, List<NeededRoute> allRoutes)
    {
        debug("Do routing parallel with new parallel Infrastructure 3");

        JoinExecutor executor;
        switch (version) {
            case batchSemaphore:
                executor = new SemaphoreExecutor(numberOfThreads);
                break;
            case batchInfrastructure:
                executor = new InfrastructureExecutor(numberOfThreads);
                break;
            default:
                throw new AssertionError();
        }

        List<NeededRoute> routesToDo = CollectionFactory.createArrayList();

        // create list of routes and blocked areas
        List<NeededRoute> myList = new ArrayList<NeededRoute>(allRoutes);
        List<Rectangle2D> blocked = new ArrayList<Rectangle2D>();

        // now run the threads
        int totalRoutes = allRoutes.size();
        int routesDone = 0;
        while (myList.size() > 0)
        {
			if (checkAbort())
			{
                info("Sea-of-gates routing aborted");
                break;
			}
            int threadAssign = 0;
            blocked.clear();
            for (int i = 0; i < myList.size(); i++)
            {
                NeededRoute nr = myList.get(i);
                boolean isBlocked = false;
                for (Rectangle2D block : blocked)
                {
                    if (block.intersects(nr.getBounds()))
                    {
                        isBlocked = true;
                        break;
                    }
                }
                if (isBlocked) continue;

                myList.remove(i);

                // this route can be done: start it
                Runnable[] runnables = findPath(nr);
                if (runnables == null || runnables.length == 0) {
                    continue;
                }
                blocked.add(nr.getBounds());
                routesToDo.add(nr);
                for (Runnable runnable: runnables) {
                    executor.execute(runnable);
                }
                threadAssign++;
            }

            trace("process " + threadAssign + " routes in parallel");
            executor.join();
            flush();

            // all done, now clean resources
            routesToDo.clear();

            routesDone += threadAssign;
            setProgressValue(routesDone, totalRoutes);
        }

        routesToDo.clear();

        executor.shutdown();
    }

    /**
     * All three methods are called from the same thread
     */
    private abstract static class JoinExecutor implements Executor {
        public abstract void execute(Runnable runnable);
        abstract void join();
        abstract void shutdown();
    }

    private static class SemaphoreExecutor extends JoinExecutor {
        private final ExecutorService executor;
        private final Semaphore sem = new Semaphore(0);
        private int joinCount;

        private SemaphoreExecutor(int parallelism) {
            executor = Executors.newFixedThreadPool(parallelism);
        }

        @Override
        public void execute(final Runnable command) {
            joinCount++;
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    command.run();
                    sem.release();
                }
            });
        }

        @Override
        public void join() {
            sem.acquireUninterruptibly(joinCount);
            joinCount = 0;
        }

        @Override
        public void shutdown() {
            executor.shutdown();
            try {
                executor.awaitTermination(1, TimeUnit.DAYS);
            } catch (InterruptedException e) {
            }
        }
    }

    private static class InfrastructureExecutor extends JoinExecutor {
        private ThreadPool pools;
        private PJob seaOfGatesJob;

        InfrastructureExecutor(int parallelism) {
            try
            {
                pools = ThreadPool.initialize(SchedulingStrategy.stack, parallelism, ThreadPoolType.userDefined);
            } catch (PoolExistsException e1)
            {
            } catch (UnknownSchedulerException e)
            {
                e.printStackTrace();
            }

            seaOfGatesJob = new PJob();

            // non-blocking execute
            seaOfGatesJob.execute(false);
        }

        @Override
        public void execute(final Runnable runnable) {
            seaOfGatesJob.add(new PTask(seaOfGatesJob) {
                @Override
                public void execute() {
                    runnable.run();
                }
            });
        }

        @Override
        void join() {
            seaOfGatesJob.join();
        }

        @Override
        void shutdown() {
            join();
            try
            {
                ThreadPool.getThreadPool().shutdown();
            } catch (InterruptedException e)
            {
                e.printStackTrace();
            }
        }

    }
}
