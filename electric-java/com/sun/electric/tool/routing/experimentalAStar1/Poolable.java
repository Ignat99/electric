/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Poolable.java
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
 * Objects of a class that implements this interface can be held in an object pool
 * 
 * @author Jonas Thedering
 * @author Christian Jülg
 */
public interface Poolable<T> {
	/** Returns the next item of the linked list */
	public T getTail();
	/** Sets the next linked list item */
	public void setTail(T tail);
}
