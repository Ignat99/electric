/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: WorkPartition.java
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

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * This class encapsulates several routing parts assigned to this partition as well as the borders of the partition.
 */
public class WorkPartition {
	ThreadBorders tb;
	int id;
	
	ConcurrentLinkedQueue<RoutingPart> routingParts=new ConcurrentLinkedQueue<RoutingPart>();
	
	WorkPartition(int id){
		this.id=id;
	}
	
	/**
	 * Add a routing part to this partition.
	 * @param rp RoutingPart
	 */
	public void addWork(RoutingPart rp){
		routingParts.add(rp);
	}
	
	/**
	 * Set the borders for this partition.
	 * @param tb borders
	 */
	public void setThreadBorders(ThreadBorders tb){
		this.tb=tb;
	}
}
