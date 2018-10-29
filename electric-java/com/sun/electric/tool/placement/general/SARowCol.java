/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: SARowCol.java
 * Written by Steven M. Rubin
 *
 * Copyright (c) 2011, Static Free Software. All rights reserved.
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
package com.sun.electric.tool.placement.general;

import java.text.DecimalFormat;
import java.util.List;
import java.util.Random;

/**
 * Implementation of the simulated annealing placement algorithm for row/column placement.
 */
public class SARowCol extends RowCol
{
	// parameters
	protected PlacementParameter numThreadsParam = new PlacementParameter("threads",
		"Number of threads:", 4);
	protected PlacementParameter maxRuntimeParam = new PlacementParameter("runtime",
		"Runtime (in seconds, 0 for no limit):", 240);
	protected PlacementParameter flipAlternateColsRows = new PlacementParameter("flipColRow",
		"Flip alternate columns/rows", true);
	protected PlacementParameter makeStacksEven = new PlacementParameter("makeStacksEven",
		"Force rows/columns to be equal length", true);

	/** current "temperature" for allowing bad moves */						private double temperature;
	/** starting time of run (for termination and temperature changes) */	private long timestampStart;
	/** number of moves per iteration of algorithm */						private int stepsPerUpdate;
	/** number of temperature steps before completion */					private int numTemperatureSteps;
	/** number of temperature steps completed so far */						private int numStepsDone;
	/** for statistics gathering */											private int better, worse, worseAccepted;

	/**
	 * Method to return the name of this placement algorithm.
	 * @return the name of this placement algorithm.
	 */
	public String getAlgorithmName() { return "Simulated-Annealing-Row/Col"; }

	/**
	 * Method to do row/column placement.
	 * @return true if placement completed; false if debugging and placement is not complete.
	 */
	public boolean runRowColPlacement(List<PlacementNode> placementNodes, List<PlacementNetwork> allNetworks)
	{
		// make sure there aren't too many threads
		int threadCount = numThreadsParam.getIntValue();
		if (threadCount >= numStacks/2)
		{
			threadCount = numStacks/2-1;
			if (threadCount <= 0) threadCount = 1;
			System.out.println("Note: Using only " + threadCount + " threads");
		}
		setParamterValues(threadCount, maxRuntimeParam.getIntValue());

		// simulated annealing: set initialize temperature
		stepsPerUpdate = (int)Math.sqrt(nodesToPlace.size());
		temperature = 2000.0;
		numTemperatureSteps = countTemperatureSteps(temperature);
		numStepsDone = 0;
		timestampStart = System.currentTimeMillis();

		// initialize statistics
		better = worse = worseAccepted = 0;

		// start the Simulated Annealing threads
		SimulatedAnnealing[] threads = new SimulatedAnnealing[numOfThreads];
		for (int n = 0; n < numOfThreads; n++)
		{
			threads[n] = new SimulatedAnnealing();
			threads[n].start();
		}

		// wait for the threads to finish
		for (int i = 0; i < numOfThreads; i++)
		{
			try
			{
				threads[i].join();
			} catch (InterruptedException e)
			{
				e.printStackTrace();
			}
		}

		// show results
		DecimalFormat formater = new DecimalFormat("###,###,###");
		String betterStr = formater.format(better);
		String worseStr = formater.format(worse);
		String worseAcceptedStr = formater.format(worseAccepted);
		System.out.println("Made " + betterStr + " moves that improve results, " +
			worseStr + " moves that worsened it (and " + worseAcceptedStr + " were accepted)");
		return true;
	}

	/**
	 * Class that does the actual simulated annealing. All instances of this
	 * class share the same data set. Simulated annealing goes like this:
	 *
	 * - find a measure of how good a placement is (the total net length)
	 * - define a "starting temperature"
	 * - given a placement, do something that could change this measure
	 *   (move nodes or swap them)
	 * - if the measure is better, fine. If not the probability of accepting this
	 *   depends on the temperature and on how much the placement is worse than
	 *   before. The higher the temperature, the higher the probability of bad
	 *   moves being accepted
	 * - decrease temperature
	 * - repeat until temperature reaches 0
	 *
	 * The effects of this implementations are:
	 * - The less likely it is that a move is accepted, the better the speedup
	 * - The more threads started, the more likely it is moves are conflicting when
	 *   accepted because they work with the same data
	 * - starting more threads than there are cores is most likely useless (?)
	 */
	class SimulatedAnnealing extends Thread
	{
		Random rand = new Random();

		public void run()
		{
			// Thread wont stop until temperature is below 1
			while (temperature > 1)
			{
				// Try some perturbations before checking if temperature has to be lowered
				for (int step = 0; step < stepsPerUpdate; step++)
				{
					// decide whether to work in two stacks or one
					ProxyNode node = null;
					int oldIndex, newIndex;
					int r = rand.nextInt(numStacks);
					if (r == 0)
					{
						// work within one stack
						for(;;)
						{
							oldIndex = lockRandomStack();
							if (oldIndex < 0) continue;
							if (stackContents[oldIndex].size() <= 1) { releaseStack(oldIndex);  continue; }
							break;
						}
						newIndex = oldIndex;
						node = getRandomNode(oldIndex);
					} else
					{
						// work between two stacks
						for(;;)
						{
							oldIndex = lockRandomStack();
							if (oldIndex < 0) continue;
							newIndex = lockRandomStack();
							if (newIndex < 0) { releaseStack(oldIndex);  continue; }

							// accept choices if stacks have something in them
							if (stackContents[oldIndex].size() > 1 && stackContents[newIndex].size() > 1) break;
							releaseStack(oldIndex);
							releaseStack(newIndex);
						}
						node = getRandomNode(oldIndex);

						// swap stacks if it makes the lengths become more uniform
						boolean doSwap = stackSizes[newIndex] > stackSizes[oldIndex];

//						// do not swap stacks during the first 75% of the time
//						if (numStepsDone < numTemperatureSteps * 3 / 4) doSwap = false;
//
//						// do force swap to prevent zero-size stacks
//						if (stackContents[oldIndex].size() <= 1) doSwap = true;

						// swap if requested
						if (doSwap)
						{
							int swap = oldIndex;   oldIndex = newIndex;   newIndex = swap;
							node = getRandomNode(oldIndex);
						}
					}

					int newPlaceInStack = rand.nextInt(stackContents[newIndex].size()+1);
					if (newIndex == oldIndex)
					{
						if (stackContents[newIndex].size() >= 2)
						{
							int oldPlaceInStack = stackContents[oldIndex].indexOf(node);
							for(;;)
							{
								if (oldPlaceInStack < newPlaceInStack) newPlaceInStack--;
								if (stackContents[newIndex].get(newPlaceInStack) != node) break;
								newPlaceInStack = rand.nextInt(stackContents[newIndex].size()+1);
							}
						}
					}

					// determine the network length before the move
					double networkMetricBefore = 0;
					for(PlacementNetwork net : node.getNets())
						networkMetricBefore += netLength(net, -1, -1);

					// make the move (set in temporary "proposed" variables)
					proposeMove(node, oldIndex, newIndex, newPlaceInStack);

					// determine the network length after the move
					double networkMetricAfter = 0;
					for (PlacementNetwork net : node.getNets())
						networkMetricAfter += netLength(net, newIndex, oldIndex);

					// the worse the gain of a perturbation, the lower the probability that this perturbation
					// is actually applied (positive gains are always accepted)
					double gain = networkMetricBefore - networkMetricAfter;
					if (gain < 0) worse++; else better++;
					if (gain > 0 || Math.exp(gain / temperature) >= Math.random())
					{
						if (gain < 0) worseAccepted++;
						implementMove(node, oldIndex, newIndex, newPlaceInStack);
					}
					releaseStack(oldIndex);
					if (newIndex != oldIndex) releaseStack(newIndex);
				}

				update();
			}
		}
	}

	/**
	 * Method that counts how often the temperature will be decreased before going below 1.
	 */
	private int countTemperatureSteps(double startingTemperature)
	{
		double temp = startingTemperature;
		int steps = 0;
		while (temp > 1)
		{
			steps++;
			temp = coolDown(temp);
		}
		return steps;
	}

	/**
	 * Method to calculate the cooling of the simulated annealing process.
	 * @param temp the current temperature
	 * @return the lowered temperature
	 */
	private double coolDown(double temp)
	{
		return temp * 0.99 - 0.1;
	}

	/**
	 * Method that does temperature and time control. Threads
	 * should call this periodically.
	 */
	private void update()
	{
		// adjust how many moves to try before the next temperature decrease
		if (runtime > 0)
		{
			// use the time to calculate new temperature
			long elapsedTime = System.currentTimeMillis() - timestampStart;
			double fractionDone = elapsedTime / (runtime * 1000.0);
			int stepsToDo = (int)(numTemperatureSteps * fractionDone);
			for( ; numStepsDone < stepsToDo; numStepsDone++)
				temperature = coolDown(temperature);
		} else
		{
			temperature = coolDown(temperature);
		}
	}
}
