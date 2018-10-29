/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: WorkStealingStructure.java
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
package com.sun.electric.tool.util.concurrent.datastructures;

import java.util.List;
import java.util.Map;

import com.sun.electric.tool.util.concurrent.debug.Debug;
import com.sun.electric.tool.util.concurrent.debug.StealTracker;
import com.sun.electric.tool.util.concurrent.patterns.PJob;
import com.sun.electric.tool.util.concurrent.patterns.PTask;
import com.sun.electric.tool.util.concurrent.runtime.MultiThreadedRandomizer;
import com.sun.electric.tool.util.concurrent.utils.ConcurrentCollectionFactory;

/**
 * This data structure is a wrapper for work stealing. Each worker has a own
 * data queue. The methods add and remove pick one data queue (own, specific,
 * random) to retrieve or add a element. This data structure is thread-safe.
 * 
 * @author Felix Schmidt
 * 
 */
public class WorkStealingStructure<T> extends IStructure<T> implements IWorkStealing {

	// data queues: each worker has its own worker queue
	protected Map<Long, IDEStructure<T>> dataQueues;
	// map a operating system thread ID to a data queue
	protected Map<Long, Long> dataQueuesMapping;
	// free internal ids are used for assigning operating system thread IDs to
	// data queues
	private List<Long> freeInternalIds;
	protected MultiThreadedRandomizer randomizer;
	private StealTracker stealTracker;
	private boolean debug;

	public WorkStealingStructure(int numOfThreads) {
		this(numOfThreads, false);
	}

	public WorkStealingStructure(int numOfThreads,  boolean debug) {
		dataQueues = ConcurrentCollectionFactory.createConcurrentHashMap();
		dataQueuesMapping = ConcurrentCollectionFactory.createConcurrentHashMap();
		freeInternalIds = ConcurrentCollectionFactory.createConcurrentList();
		this.randomizer = new MultiThreadedRandomizer(numOfThreads);
		stealTracker = StealTracker.getInstance();

		for (long i = 0; i < numOfThreads; i++) {
			freeInternalIds.add(i);
			// dataQueues.put(i, ConcurrentCollectionFactory.createUnboundedDoubleEndedQueue(this.clazz));
			//dataQueues.put(i, new DEListWrapper<T>());
			dataQueues.put(i, new UnboundedDEQueue<T>(4));
		}

		this.debug = debug;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.electric.tool.util.IStructure#add(java.lang.Object)
	 */
	@Override
	public void add(T item) {
		this.add(item, PJob.SERIAL);
	}

	/**
	 * Add a item to a data queue <br>
	 * <b>Algorithm</b><br>
	 * <ul>
	 * <li>get operating system thread ID</li>
	 * <li>get local queue (mapping)</li>
	 * <li>assign item to own queue if available</li>
	 * <li>otherwise pick a random data queue</li><br>
	 * <li>if i != -1, then add item to given data queue</li>
	 * </ul>
	 * 
	 * @param item
	 *            add this item to one data queue
	 * @param i
	 *            if you want to add the item to a data queue of your choice use
	 *            the number of the data queue [0..numOfThreads], otherwise (-1,
	 *            PJob.SERIAL) add it to the own data queue or pick a random one
	 */
	@Override
	public void add(T item, int i) {

		Long osThreadId = getThreadId();
		Long localQueueId = dataQueuesMapping.get(osThreadId);

		if (i == PJob.SERIAL) {
			if (localQueueId != null) {
				IDEStructure<T> ownQueue = dataQueues.get(localQueueId);
				if (ownQueue != null) {
					ownQueue.add(item);
					return;
				}
			}
			int foreignQueue = randomizer.getRandomizer().nextInt(dataQueues.size());
			dataQueues.get(Long.valueOf(foreignQueue)).add(item);
		} else {
			dataQueues.get(Long.valueOf(i)).add(item);
		}

	}

	protected Long getThreadId() {
		return Thread.currentThread().getId();
	}

	/*
	 * 
	 * (non-Javadoc)
	 * 
	 * @see com.sun.electric.tool.util.IStructure#isEmpty()
	 */
	@Override
	public boolean isEmpty() {
		IDEStructure<T> ownQueue = dataQueues.get(getThreadId());
		if (ownQueue != null) {
			return ownQueue.isEmpty();
		}
		return false;
	}

	/**
	 * Remove a item from one data queue
	 * 
	 * <b>Algorithm</b><br>
	 * <ul>
	 * <li>get operating system thread ID</li>
	 * <li>get local queue (mapping)</li>
	 * <li>remove item from own queue</li>
	 * <li>if the item is equal to null pick a random victim queue (iterate over
	 * all queues)</li>
	 * <li>return item</li>
	 * </ul>
	 * 
	 * @return a item from one queue, or null, if all queues are empty
	 */
	@Override
	public T remove() {
		Long osThreadId = getThreadId();
		Long localQueueId = dataQueuesMapping.get(osThreadId);

		if (localQueueId == null) {
			throw new Error("Thread not registered");
		}

		T result = null;

		IDEStructure<T> ownQueue = dataQueues.get(localQueueId);
		if (ownQueue != null) {
			result = ownQueue.remove();
		}

		for (int i = 1; result == null && i < dataQueues.size(); i++) {
			result = dataQueues.get(Long.valueOf(i + localQueueId) % dataQueues.size()).getFromTop();
			if (result == null) {
				int foreigner = randomizer.getRandomizer().nextInt(dataQueues.size());
				result = dataQueues.get(Long.valueOf(foreigner)).getFromTop();
			}
			if (result != null && this.debug)
				stealTracker.countSteal();
		}

		return result;
	}

	/**
	 * Add a thread to the data queue mapping
	 */
	@Override
	public synchronized void registerThread() {
		if (freeInternalIds.size() > 0) {
			Long myId = freeInternalIds.remove(0);
			dataQueuesMapping.put(getThreadId(), myId);
		}
	}

	/**
	 * Factory method for creating a WorkStealingStructure for the ThreadPool
	 * 
	 * @param numOfThreads
	 * @return initialized WorkStealingStructure
	 */
	public static WorkStealingStructure<PTask> createForThreadPool(int numOfThreads) {
		return new WorkStealingStructure<PTask>(numOfThreads, Debug.isDebug());
	}
}
