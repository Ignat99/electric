/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Population.java
 * Written by Team 3: Christian Wittner, Ivan Dimitrov
 * 
 * This code has been developed at the Karlsruhe Institute of Technology (KIT), Germany, 
 * as part of the course "Multicore Programming in Practice: Tools, Models, and Languages".
 * Contact instructor: Dr. Victor Pankratius (pankratius@ipd.uka.de)
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
package com.sun.electric.tool.placement.genetic1;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Population {

	public List<Chromosome> chromosomes;
	Random randomGenerator;

	public Random getRandomGenerator() {
		return randomGenerator;
	}

	public void setRandomGenerator(Random randomGenerator) {
		this.randomGenerator = randomGenerator;
	}

	public Population(int size) {
		chromosomes = new ArrayList<Chromosome>(size);
	}
	
	public double getBest_fitness() {
		double best_fitness = Double.MAX_VALUE;
		
		for (Chromosome c : chromosomes) {
			assert (c.fitness.doubleValue() > 0);
			if (c.fitness.doubleValue() < best_fitness)
				best_fitness = c.fitness.doubleValue();
		}
		
		return best_fitness;
	}

	public void evaluate(Metric metric, GenePlacement placement) {
		

		// place altered cells before calculating metric
		for (Chromosome c : chromosomes) {
			assert (c.altered || c.fitness.doubleValue() > 0);
			placement.placeChromosome(c);
		}

		metric.evaluate(chromosomes);

		
	}

}
