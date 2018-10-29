/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: BenchmarkRouter.java
 * Written by: Andreas Uebelhoer, Alexander Bieles, Emre Selegin (Team 6)
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
package com.sun.electric.tool.routing.experimentalLeeMoore1;

import com.sun.electric.tool.routing.RoutingFrame;

public class BenchmarkRouter extends RoutingFrame {
	
	public RoutingParameter numThreads = new RoutingParameter("threads", "Number of threads to use:", 4);
	public RoutingParameter maxRuntime = new RoutingParameter("runtime", "Maximum runtime (seconds):", 300);
	public RoutingParameter enableOutput = new RoutingParameter("output", "Enable console output", false);

	public BenchmarkRouter() {
	}
	
	public String getAlgorithmName() {
		return "BenchmarkRouter";
	}
	
	public void setBenchmarkParameters(int threads, int runtime) {
		numThreads.setTempIntValue(threads);
		maxRuntime.setTempIntValue(runtime);
	}

}
