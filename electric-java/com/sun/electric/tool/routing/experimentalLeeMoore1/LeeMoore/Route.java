/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Route.java
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
package com.sun.electric.tool.routing.experimentalLeeMoore1.LeeMoore;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * This class saves a found route. It contains out of a list of tupels
 */
public class Route {
	LinkedList<Tupel> route = new LinkedList<Tupel>();
	boolean reversed=false;		//start and end point are switched because of multi terminal routing
	
	/**
     * this method is used for multi-terminal routing (not yet functional)
     * @return true if start and end are reversed.
     */
	public boolean isReversed() {
		return reversed;
	}

	/**
	 * this method is used for multi-terminal routing (not yet functional)
	 * @param reversed
	 */
	public void setReversed(boolean reversed) {
		this.reversed = reversed;
	}

	/**
	 *  add a tupel as first element
	 * @param t Tupel to add
	 */
	public void addFieldInFront(Tupel t) {
		route.addFirst(t);
	}

    /**
	 *  add a tupel as last element
	 * @param t Tupel to add
	 */
	public void addFieldAtBack(Tupel t) {
		route.addLast(t);
	}

	/**
	 *  print route
	 */
	public void printRoute() {
		Iterator<Tupel> i = route.iterator();
		while (i.hasNext()) {
			i.next().printTupel();
			if (i.hasNext()) {
				System.out.print("->");
			}
		}
		System.out.print(". Length is " + (route.size() - 1) + "\n");
	}

	/**
	 *  print route for a given list
	 * @param list to print
	 */
	public void printRoute(List<Tupel> list) {
		Iterator<Tupel> i = list.iterator();
		while (i.hasNext()) {
			i.next().printTupel();
			if (i.hasNext()) {
				System.out.print("->");
			}
		}
		System.out.print(". Length is " + (list.size() - 1) + "\n");
	}
	
	public String toString(){
		String ret="";
		Iterator<Tupel> i = route.iterator();
		while (i.hasNext()) {
			ret+=i.next();
			if (i.hasNext()) {
				ret+="->";
			}
		}
		return ret;
	}
	
	/**
	 * generates EdgePoint for each layer and direction change
	 * @return List of Tupels which are edge points of the route
	 */
	public List<Tupel> getEdgePoints() {
        Tupel t;
		List<Tupel> ret = new LinkedList<Tupel>();
		ret.add(route.getFirst());
		int layer=route.getFirst().getLayer();

		for (int i = 1; i < route.size(); i++) {
			t = route.get(i);
			if(t.getLayer()!=layer){
				layer=t.getLayer();
				ret.add(route.get(i-1));
				ret.add(t);
			}
		}
		ret.add(route.getLast());
		return ret;
	}

	/**
	 * Method to get the first tupel.
	 * @return first tupel of route (but doesn't remove it).
	 */
	public Tupel getFirstTupel() {
		return route.peek();
	}

    /**
	 * Method to get the last tupel.
	 * @return last tupel of route (but doesn't remove it)
	 */
	public Tupel getLastTupel() {
		return route.getLast();
	}

	/**
	 * Method to get a list of tupels that build the route.
	 * @return list of tupels that build the route.
	 */
	public List<Tupel> getRoutingList(){
		return route;
	}
}
