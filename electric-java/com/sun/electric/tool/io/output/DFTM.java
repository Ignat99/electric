/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: DFTM.java
 * Input/output tool: DFTM (Data flow/Transactional Memory) netlist output
 * Written by Steven M. Rubin.
 *
 * Copyright (c) 2013, Static Free Software. All rights reserved.
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
package com.sun.electric.tool.io.output;

import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.topology.ArcInst;
import com.sun.electric.database.variable.VarContext;
import com.sun.electric.technology.technologies.Generic;

import java.awt.geom.Point2D;
import java.util.Iterator;

/**
 * Class to write DFTM output to disk.
 */
public class DFTM extends Geometry
{
	private DFTMPreferences localPrefs;

	public static class DFTMPreferences extends OutputPreferences
	{
		public DFTMPreferences(boolean factory)
		{
			super(factory);
		}

		@Override
		public Output doOutput(Cell cell, VarContext context, String filePath)
		{
			DFTM out = new DFTM(this, cell.getTechnology().getScale());
			if (!out.openTextOutputStream(filePath)) // no error
			{
				// separate code for flattening hierarchy
				out.topCell = cell;
				out.start();
				for(Iterator<ArcInst> it = cell.getArcs(); it.hasNext(); )
				{
					ArcInst ai = it.next();
					if (ai.getProto() == Generic.tech().unrouted_arc)
					{
						Point2D head = new Point2D.Double(ai.getHeadLocation().getX(), ai.getHeadLocation().getY());
						Point2D tail = new Point2D.Double(ai.getTailLocation().getX(), ai.getTailLocation().getY());
						out.printWriter.println("P " + Math.round(head.getX()) + " " + Math.round(head.getY()) + " " +
							Math.round(tail.getX()) + " " + Math.round(tail.getY()));
					}
				}
			}
			out.closeTextOutputStream();
			return out.finishWrite();
		}
	}

	/**
	 * Creates a new instance of DFTM
	 */
	DFTM(DFTMPreferences cp, double techScale)
	{
		localPrefs = cp;
	}

	protected void start()
	{
//		if (localPrefs.includeDateAndVersionInOutput)
//		{
//			printWriter.print("( Electric VLSI Design System, version " + Version.getVersion() + " );");
//			Date now = new Date();
//			printWriter.print("( written on " + TextUtils.formatDate(now) + " );");
//		} else
//		{
//			printWriter.print("( Electric VLSI Design System );");
//		}
//		emitCopyright("( ", " );");
	}

	protected void done()
	{
	}

	/**
	 * Method to write cellGeom
	 */
	protected void writeCellGeom(CellGeom cellGeom)
	{
	}
}
