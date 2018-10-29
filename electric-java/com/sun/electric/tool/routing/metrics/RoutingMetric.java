/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: RoutingMetric.java
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
package com.sun.electric.tool.routing.metrics;

import java.util.Iterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.network.Network;
import com.sun.electric.database.topology.ArcInst;
import com.sun.electric.tool.Job;

/**
 * @author Felix Schmidt
 *
 */
public abstract class RoutingMetric<T> {
	
	private static Logger logger = LoggerFactory.getLogger(RoutingMetric.class);

	public abstract T calculate(Cell cell);
	public T calculate(Network net) {return null;}
	
	protected T processNets(Cell cell, T startValue) {
		T result = startValue;
	    boolean debug = Job.getDebug();

        if (cell == null) return result;
		
		for(Iterator<Network> it = cell.getNetlist().getNetworks(); it.hasNext();) {
			Network net = it.next();
            if (debug)
			    logger.trace("process net: " + net.getName());
            result = reduce(result, net);
			
//			for(Iterator<ArcInst> arcIt = net.getArcs(); arcIt.hasNext();) {
//				result = reduce(result, arcIt.next(), net);
//			}
		}
		
		return result;
	}
	
	protected T reduce(T result, ArcInst instance, Network net)
    {
		// [fschmidt] method not required here
		throw new UnsupportedOperationException();
	}

    protected T reduce(T result, Network net)
    {
        throw new UnsupportedOperationException();
    }
}
