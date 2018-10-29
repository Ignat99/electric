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

import com.sun.electric.database.Environment;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Subclass of SeaOfGatesEngine to use original scheme for thread allocation.
 */
public class SeaOfGatesEngineOld extends SeaOfGatesEngine
{
	Rectangle2D[] threadAreas;
	int threadCount;
	List<NeededRoute> myList;
	int totalRoutes, routesDone;

    Rectangle2D pendingArea;
    List<Runnable> pendingRunnables = new ArrayList<Runnable>();

	/*
	 * (non-Javadoc)
	 *
	 * @see com.sun.electric.tool.routing.seaOfGates.SeaOfGatesEngine#doRoutingParallel(
	 *   int, java.util.List)
	 * )
	 */
	@Override
	protected void doRoutingParallel(int numberOfThreads, List<NeededRoute> allRoutes)
	{
		debug("Do parallel routing with raw threads");

		// create list of routes
		myList = new ArrayList<NeededRoute>(allRoutes);
		routesDone = 0;
		totalRoutes = allRoutes.size();

		// create threads and other threading data structures
		RouteInThread[] threads = new RouteInThread[numberOfThreads];
    	threadCount = numberOfThreads;
		threadAreas = new Rectangle2D[numberOfThreads];
		Semaphore outSem = new Semaphore(0);
		for (int i=0; i<numberOfThreads; i++)
		{
			threads[i] = new RouteInThread("Route #" + (i + 1), getEnvironment(), i, outSem);
            threads[i].start();
		}

        // now wait for routing threads to finish
        int timeOut = 20;
        for (;;) {
            try {
                if (outSem.tryAcquire(threadCount, timeOut, TimeUnit.MILLISECONDS)) {
                    break;
                }
                flush();
            } catch (InterruptedException e) {
            }
        }
	}

	private synchronized Runnable getNext(int threadNumber)
	{
		if (checkAbort())
		{
			info("Sea-of-gates routing aborted thread " + threadNumber);
			return null;
		}

		threadAreas[threadNumber] = null;
        if (pendingRunnables.isEmpty()) {
            for (int i = 0; i < myList.size(); i++)
            {
                NeededRoute nr = myList.get(i);
                boolean isBlocked = false;
                for(int t=0; t<threadCount; t++)
                {
                    if (threadAreas[t] == null) continue;
                    if (threadAreas[t].intersects(nr.getBounds()))
                    {
                        isBlocked = true;
                        break;
                    }
                }
                if (isBlocked) continue;

                // say what is happening
                trace("Thread " + (threadNumber+1) + " routing " + nr.getName() + "...");
                setProgressNote(nr.getName());

                myList.remove(i);

                // route it
                Runnable[] runnables = findPath(nr);
                if (runnables == null || runnables.length == 0) {
                    routesDone++;
                    continue;
                }
                routesDone -= runnables.length - 1;
    			// this route can be done: start it
                pendingArea = nr.getBounds();
                pendingRunnables.addAll(Arrays.asList(runnables));
                break;
            }
        }

        if (pendingRunnables.isEmpty()) return null;
		threadAreas[threadNumber] = pendingArea;
		return pendingRunnables.remove(0);
	}

	private class RouteInThread extends Thread
	{
		private Semaphore whenDone;
		private Environment env;
		private int threadNumber;

		public RouteInThread(String name, Environment env, int threadNumber, Semaphore whenDone)
		{
			super(name);
			this.env = env;
			this.threadNumber = threadNumber;
			this.whenDone = whenDone;
		}

        @Override
		public void run()
		{
			Environment.setThreadEnvironment(env);

			for(;;)
			{
                // get next available route to run
                Runnable runnable = getNext(threadNumber);
                if (runnable == null)
                {
                    whenDone.release();
                    break;
                }

                runnable.run();

                // done: now handle the results
                routesDone++;
                setProgressValue(routesDone, totalRoutes);
			}
		}
	}
}
