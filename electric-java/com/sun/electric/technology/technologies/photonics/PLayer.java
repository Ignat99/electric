/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: PLayer.java
 *
 * Copyright (c) 2015, Static Free Software. All rights reserved.
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
package com.sun.electric.technology.technologies.photonics;

import com.sun.electric.technology.Layer;
import com.sun.electric.technology.Technology;

import java.io.Serializable;
import java.util.Iterator;

/**
 * Class to define photonics layers that package an Electric Layer and a surround width.
 */
public class PLayer implements Serializable
{
	private String layerName;
	private double width;
	private boolean layerFound;
	private Layer layer;

	/**
	 * Constructor that defines a layer name and a surround width in an optical track.
	 * @param n the name of the Electric Layer.
	 * @param w the width of the layer when in an optical track.
	 */
	public PLayer(String n, double w)
	{
		layerName = n;
		width = w;
		layerFound = false;
		layer = null;
	}

	/**
	 * Method to return the width of this PLayer when run in an optical track.
	 * @return the width of this PLayer when run in an optical track.
	 */
	public double getWidth() { return width; }

	/**
	 * Method to find an Electric Layer in the Photonics technology.
	 * @return the Layer (null if not found).
	 */
	public Layer findLayer()
	{
		if (layerFound) return layer;
		Technology tech = Photonics.tech();
		if (tech != null)
		{
			for(Iterator<Layer> it = tech.getLayers(); it.hasNext(); )
			{
				Layer lay = it.next();
				if (lay.getName().equals(layerName))
				{
					layer = lay;
					layerFound = true;
					return lay;
				}
			}
		}
		System.out.println("ERROR: Cannot find layer " + layerName);
		layerFound = true;
		return layer;
	}
}
