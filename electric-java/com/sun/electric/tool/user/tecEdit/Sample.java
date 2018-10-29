/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Sample.java
 * Technology Editor, helper class during conversion of libraries to technologies
 * Written by Steven M. Rubin.
 *
 * Copyright (c) 2005, Static Free Software. All rights reserved.
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
package com.sun.electric.tool.user.tecEdit;

import com.sun.electric.database.prototype.NodeProto;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.technology.Technology;

import java.io.Serializable;

/**
 * This class defines graphical layer samples during conversion of libraries to technologies.
 */
public class Sample implements Serializable
{
	NodeInst  node;					/* true node used for sample */
	NodeProto layer;				/* type of node used for sample */
	double    xPos, yPos;			/* center of sample */
	Sample    assoc;				/* associated sample in first example */

	Technology.TechPoint [] values;	/* points that describe the sample */
	String    msg;					/* string (null if none) */

	Example   parent;				/* example containing this sample */
}
