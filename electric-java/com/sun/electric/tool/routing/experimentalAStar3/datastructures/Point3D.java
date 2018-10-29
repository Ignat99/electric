/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Point3D.java
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
package com.sun.electric.tool.routing.experimentalAStar3.datastructures;

public class Point3D
{
  private int x;

  private int y;

  private int z;

  public Point3D(int x, int y, int z)
  {
    this.x = x;
    this.y = y;
    this.z = z;
  }

  public int getX()
  {
    return this.x;
  }

  public int getY()
  {
    return this.y;
  }

  public int getZ()
  {
    return this.z;
  }

  public void setX(int x)
  {
    this.x = x;
  }

  public void setY(int y)
  {
    this.y = y;
  }

  public void setZ(int z)
  {
    this.z = z;
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.lang.Object#toString()
   */
  @Override
  public String toString()
  {
    return "Point3D [x=" + x + ", y=" + y + ", z=" + z + "]";
  }
}
