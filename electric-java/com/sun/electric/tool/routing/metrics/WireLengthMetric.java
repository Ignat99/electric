/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: WireLengthMetric.java
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

import com.sun.electric.database.geometry.Poly;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.network.Netlist;
import com.sun.electric.database.network.Network;
import com.sun.electric.database.prototype.PortProto;
import com.sun.electric.database.topology.ArcInst;
import com.sun.electric.database.topology.Connection;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.topology.PortInst;
import com.sun.electric.technology.ArcProto;
import com.sun.electric.technology.Layer;
import com.sun.electric.technology.PrimitivePort;
import com.sun.electric.technology.technologies.Generic;
import com.sun.electric.tool.Job;

import java.util.Iterator;

/**
 * @author Felix Schmidt
 * Modified by Gilda G
 *
 */
public class WireLengthMetric extends RoutingMetric<Double> {

	/* (non-Javadoc)
	 * @see com.sun.electric.tool.routing.metrics.RoutingMetric#calculate(com.sun.electric.database.hierarchy.Cell)
	 */
	public Double calculate(Cell cell) {
		return processNets(cell, 0.0);
	}

	/* (non-Javadoc)
	 * @see com.sun.electric.tool.routing.metrics.RoutingMetric#reduce(java.lang.Object, com.sun.electric.database.topology.ArcInst)
	 */
	@Override
	protected Double reduce(Double result, ArcInst instance, Network net)
    {
        double val = (instance.getProto() != Generic.tech().unrouted_arc) ? instance.getLambdaLength() : 0;
		return result + val;
	}

	private Double reduce(Double result, NodeInst instance, Network net)
	{
		double val = 0;
		// doesn't consider subcells
		if (!instance.isCellInstance())
		{
			Cell parent = instance.getParent();
			Netlist netlist = parent.getNetlist();
			boolean found = false;
			
			// looking for the layer where net port is
			for(Iterator<Connection> it = instance.getConnections(); it.hasNext() && !found;)
			{
				Connection con = it.next();
				Layer layer = con.getArc().getProto().getLayer(0); // it might not be index=0
				PortInst pi = con.getPortInst();
				Network localNet = netlist.getNetwork(pi);
				if (net != localNet) continue;
				Layer.Function.Set functionSet = new Layer.Function.Set();
				functionSet.add(layer);
				Poly[] pols = parent.getTechnology().getShapeOfNode(instance, true, false, functionSet);
				// pols.length == 0 could come from unrouted arcs.
				if (pols.length == 1)
					val = pols[0].getMaxSize();
//				else if (Job.getDebug())
//					System.out.println("Check this case in WireLengthMetric:reduce for net - # of polys: " + pols.length);
				found = true;
			}
			if (!found)
			{
				// no arcs from node
				for (Iterator<PortInst> itPort = instance.getPortInsts(); itPort.hasNext() && !found;)
				{
					PortInst pi = itPort.next();
					Network localNet = netlist.getNetwork(pi);
					if (net != localNet) continue;
					PortProto pp = pi.getPortProto();
					if (!(pp instanceof PrimitivePort)) continue; // check this case
					PrimitivePort ppp = (PrimitivePort)pp;
					ArcProto a1 = ppp.getConnections()[0]; // arbitrarily choosing first item
					assert(a1.getArcLayers().length == 1);
					Layer layer =  a1.getArcLayers()[0].getLayer();
					Layer.Function.Set functionSet = new Layer.Function.Set();
					functionSet.add(layer);
					Poly[] pols = parent.getTechnology().getShapeOfNode(instance, true, false, functionSet);
					// pols.length == 0 could come from unrouted arcs.
					if (pols.length == 1)
						val = pols[0].getMaxSize();
//					else if (Job.getDebug())
//						System.out.println("Check this case in WireLengthMetric:reduce for net - # of polys: " + pols.length);
						
					found = true;
				}
			}
			assert(found); // when is not true?
		}
		return result + val;
	}
	
    @Override
	protected Double reduce(Double result, Network net)
    {
    	// arcs
        for(Iterator<ArcInst> arcIt = net.getArcs(); arcIt.hasNext();)
			result = reduce(result, arcIt.next(), net);
        // nodes
        for(Iterator<NodeInst> nodeIt = net.getNodes(); nodeIt.hasNext();)
        	result = reduce(result, nodeIt.next(), net);
        return result;
	}
}
