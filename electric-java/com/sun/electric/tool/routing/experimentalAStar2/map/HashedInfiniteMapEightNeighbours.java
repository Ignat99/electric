/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: HashedInfiniteMapEightNeighbours.java
 * Written by: Christian Harnisch, Ingo Besenfelder, Michael Neumann (Team 3)
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
package com.sun.electric.tool.routing.experimentalAStar2.map;

import java.util.HashMap;

import com.sun.electric.tool.routing.experimentalAStar2.algorithm.AStarMapBase;
import com.sun.electric.tool.routing.experimentalAStar2.algorithm.AStarMapVisitorBase;
import com.sun.electric.tool.routing.experimentalAStar2.algorithm.AStarNodeBase;
import com.sun.electric.tool.routing.experimentalAStar2.memorymanager.ObjectPool;

/**
 * This map is mostly the same as the HashedInfiniteMap, with the difference
 * that it returns neighbour tiles in all eight directions rather than just
 * four.
 */
public class HashedInfiniteMapEightNeighbours<T extends AStarNodeBase<T>> extends HashedInfiniteMap<T>
{
  /**
   * @param nodePool
   */
  public HashedInfiniteMapEightNeighbours(ObjectPool<T> nodePool)
  {
    super(nodePool);
  }

  @Override
  public void visitNeighboursOf(T origin, AStarMapVisitorBase<T> visitor)
  {
    visitNeighboursOf8(origin, visitor);
  }

//  @SuppressWarnings("unchecked")
  public AStarMapBase<T> clone()
  {
    HashedInfiniteMapEightNeighbours<T> newMap = new HashedInfiniteMapEightNeighbours<T>(this.nodePool);
    // Cast needed: clone() returns an Object
    newMap.blockedMap = (HashMap<String, Boolean>) blockedMap.clone();
    return newMap;
  }

}
