/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Node.java
 * Written by: Christian Julg, Jonas Thedering (Team 1)
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
package com.sun.electric.tool.routing.experimentalAStar1;

/** 
 * The classic A* node
 * 
 * @author Jonas Thedering
 * @author Christian Jülg
 */
public class Node implements Poolable<Node> {
	// need to be readable
	short x, y;
	byte z;		
	
	// g is distance to this, h is heuristic to finish, f is sum of both
	int f, g, h;	// ""
	Node parent;
	// Don't use a list to avoid allocations
	Node[] children;
	byte childCount;
	// Pointer to the next pool object
	private Node tail;
	
	public Node() {
		children = new Node[6]; //4 in x,y dirs, 1 in z-1, 1 in z+1 dir
	}
	
	// Do the initialization here instead of in the constructor, because of reuse
	public void initialize(int x, int y, int z) {
		this.x = (short) x;
		this.y = (short) y;
		this.z = (byte) z;
		parent = null;
		childCount = 0;
	}

	public Node getTail() {
		return tail;
	}

	public void setTail(Node tail) {
		this.tail = tail;
	}
}
