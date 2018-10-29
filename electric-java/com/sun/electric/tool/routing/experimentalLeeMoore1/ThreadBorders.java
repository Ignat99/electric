/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: ThreadBorders.java
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

public class ThreadBorders{
	private int lowIndexX,  highIndexX,  lowIndexY,  highIndexY;
	/**
	 * 
	 * @param lowIndexX
	 * @param highIndexX
	 * @param lowIndexY
	 * @param highIndexY
	 */
	public ThreadBorders(int lowIndexX, int highIndexX, int lowIndexY, int highIndexY ){
		this.highIndexX = highIndexX;
		this.highIndexY = highIndexY;
		this.lowIndexX = lowIndexX;
		this.lowIndexY = lowIndexY;
	}
	public int getLowIndexX(){return lowIndexX;}
	public int getHighIndexX(){return highIndexX;}
	public int getLowIndexY(){return lowIndexY;}
	public int getHighIndexY(){return highIndexY;}
	public String toString(){
		return "[("+lowIndexX+","+highIndexX+");("+lowIndexY+","+highIndexY+")]";
	}
}