/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Poly3D.java
 *
 * Copyright (c) 2011, Static Free Software. All rights reserved.
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
package com.sun.electric.database.geometry;

import java.awt.Color;

/**
 * Class to define a polygon of points that has low/high Z information.
 */
public class Poly3D extends PolyBase
{
	/** the Z coordinates */						private double lowZ, highZ;
	/** color */									private Color color;
	/** transparency (0=opaque, 1=transparent) */	private float transparency;
	/** Text to display (if not null) */			private String text;

	/**
	 * The constructor creates a new Poly given an array of points.
	 * @param points the array of coordinates.
	 * @param lz the low Z coordinate of the polygon.
	 * @param hz the high Z coordinate of the polygon.
	 */
	public Poly3D(Point[] points, double lz, double hz)
	{
		super(points);
		lowZ = lz;
		highZ = hz;
		color = null;
		transparency = 0;
	}

	/**
	 * The constructor creates a new Poly given a rectangle.
	 * @param cX the center X coordinate of the rectangle.
	 * @param cY the center Y coordinate of the rectangle.
	 * @param width the width of the rectangle.
	 * @param height the height of the rectangle.
	 * @param lz the low Z coordinate of the polygon.
	 * @param hz the high Z coordinate of the polygon.
	 */
	public Poly3D(double cX, double cY, double width, double height, double lz, double hz)
	{
		super(cX, cY, width, height);
		lowZ = lz;
		highZ = hz;
		color = null;
		transparency = 0;
	}

	/**
	 * Method to return the high Z coordinate of this Poly.
	 * @return the high Z coordinate of this Poly.
	 */
	public double getHighZ() { return highZ; }

	/**
	 * Method to return the low Z coordinate of this Poly.
	 * @return the low Z coordinate of this Poly.
	 */
	public double getLowZ() { return lowZ; }

	/**
	 * Method to set the high Z coordinate of this Poly.
	 * @param z the high Z coordinate of this Poly.
	 */
	public void setHighZ(double z) { highZ = z; }

	/**
	 * Method to set the low Z coordinate of this Poly.
	 * @param z the low Z coordinate of this Poly.
	 */
	public void setLowZ(double z) { lowZ = z; }

	/**
	 * Method to return the color of this Poly.
	 * @return the color of this Poly.
	 */
	public Color getColor() { return color; }

	/**
	 * Method to set the color of this Poly.
	 * @param c the color of this Poly.
	 */
	public void setColor(Color c) { color = c; }

	/**
	 * Method to return the transparency of this Poly.
	 * Transparency runs from 0.0 for opaque to 1.0 for transparent.
	 * @return the transparency of this Poly.
	 */
	public float getTransparency() { return transparency; }

	/**
	 * Method to set the transparency of this Poly.
	 * Transparency runs from 0.0 for opaque to 1.0 for transparent.
	 * @param t the transparency of this Poly.
	 */
	public void setTransparency(float t) { transparency = t; }

	/**
	 * Method to return the text of this Poly.
	 * If there is text, show it in 3D.  Otherwise, it is a 3D polygon.
	 * @return the text of this Poly.
	 */
	public String getText() { return text; }

	/**
	 * Method to set the text of this Poly.
	 * If there is text, show it in 3D.  Otherwise, it is a 3D polygon.
	 * @param t the text of this Poly.
	 */
	public void setText(String t) { text = t; }
}
