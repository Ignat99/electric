/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: StackedViasAmountMetric.java
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.sun.electric.database.geometry.EPoint;
import com.sun.electric.database.prototype.NodeProto;
import com.sun.electric.database.topology.Connection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.network.Netlist;
import com.sun.electric.database.network.Network;
import com.sun.electric.database.topology.ArcInst;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.topology.PortInst;

/**
 * @author Felix Schmidt
 * 
 *         This metric is part of the routing quality metric
 * 
 */
public class StackedViasAmountMetric extends RoutingMetric<Integer> {

	private static Logger logger = LoggerFactory.getLogger(StackedViasAmountMetric.class);

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sun.electric.tool.routing.metrics.RoutingMetric#calculate(com.sun
	 * .electric.database.hierarchy.Cell)
	 */
	public Integer calculate(Cell cell) {
		Integer result = 0;

		Map<Network,List<NodeInst>> contactsOnNets = new HashMap<Network,List<NodeInst>>();
		Netlist nl = cell.getNetlist();
        for (Iterator<NodeInst> it = cell.getNodes(); it.hasNext();) {
            NodeInst ni = it.next();
			if (!ni.getFunction().isContact()) continue;
			Network net = nl.getNetwork(ni, ni.getOnlyPortInst().getPortProto(), 0);
			List<NodeInst> contactsOnNet = contactsOnNets.get(net);
			if (contactsOnNet == null) contactsOnNets.put(net, contactsOnNet = new ArrayList<NodeInst>());
			contactsOnNet.add(ni);
        }		
		
		for (Iterator<Network> it = cell.getNetlist().getNetworks(); it.hasNext();) {
			Network net = it.next();
			List<NodeInst> contactsOnNet = contactsOnNets.get(net);
			if (contactsOnNet == null) continue;
            HashMap<Integer,Boolean> visitedPairs = new HashMap<Integer,Boolean>();
			for (NodeInst node : contactsOnNet) {
				//logger.trace("process contact: " + node.getName());
				PortInst port = node.getOnlyPortInst();
                NodeProto np = node.getProto();
                EPoint center = port.getCenter();
                result += isPortStacked(node, np, port, center, visitedPairs);
			}
		}

		return result;
	}

    private int isPortStacked(NodeInst ni, NodeProto np, PortInst port, EPoint center, HashMap<Integer,Boolean> visitedPairs)
    {
        int count = 0;
        int index1 = ni.hashCode();

        for (Iterator<Connection> connIt = port.getConnections(); connIt.hasNext();)
        {
            Connection con = connIt.next();
            int thatEndIndex = 1 - con.getEndIndex();
            PortInst p = con.getArc().getConnection(thatEndIndex).getPortInst();
            NodeInst otherNi = p.getNodeInst();
            if (otherNi.getProto() == np)
                continue;

            int index2 = otherNi.hashCode();
            int index = (index1<index2) ? (index1 ^ index2) : (index2 ^ index1);


            if (otherNi.getFunction().isContact())
            {
                if (visitedPairs.get(index) != null) // already analyzed
                    continue;
                visitedPairs.put(index, true);

                EPoint cen = p.getCenter();
                if (cen.equals(center))
                {
                    count++;
                }
            }
        }
        return count;
    }
}
