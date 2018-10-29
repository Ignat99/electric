/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: OpticalArc.java
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

import com.sun.electric.database.geometry.Poly;
import com.sun.electric.technology.ArcProto;
import com.sun.electric.technology.Technology;

public class OpticalArc extends ArcProto
{
	public OpticalArc(Photonics tech)
    {
        super(tech, "Optical", 0, ArcProto.Function.UNKNOWN, collectArcLayers(), 5);
        setFactoryFixedAngle(true);
        setFactoryExtended(false);
        setFactoryAngleIncrement(45);
        setWipable();
        tech.addArcProto(this);
    }

    private static Technology.ArcLayer[] collectArcLayers()
    {
    	PLayer[] ol = Photonics.getOpticalLayers(true);
    	Technology.ArcLayer[] retArray = new Technology.ArcLayer[ol.length];
    	for(int i=0; i<ol.length; i++)
    		retArray[i] = new Technology.ArcLayer(ol[i].findLayer(), ol[i].getWidth(), Poly.Type.FILLED);
    	return retArray;    	
    }
}
