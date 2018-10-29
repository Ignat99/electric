/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: SweepSignal.java
 *
 * Copyright (c) 2004, Static Free Software. All rights reserved.
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
package com.sun.electric.tool.user.waveform;

import java.util.Iterator;

/**
 * Class to define a swept signal.
 */
public class SweepSignal
{
	private String name;
	private WaveformWindow ww;
	private boolean included;
	private int sweepIndex;

	public SweepSignal(String name, WaveformWindow ww)
	{
		this.name = name;
		this.ww = ww;
		included = true;
	}

	public String toString()
	{
		String ret = name;
		if (included)
		{
			ret += " >>>>> INCLUDED";
			if (ww.getHighlightedSweep() == sweepIndex)
				ret += " !!!!";
		} else
		{
			ret += " ----- EXCLUDED";
		}
		return ret;
	}

	public void setIncluded(boolean included, boolean update)
	{
		if (this.included == included) return;
		this.included = included;
		if (update)
		{
			for(Iterator<Panel> it = ww.getPanels(); it.hasNext(); )
			{
				Panel wp = it.next();
				wp.repaintWithRulers();
			}
		}
	}

    public String getName() { return name; }

	public boolean isIncluded() { return included; }

	public void highlight()
	{
		ww.setHighlightedSweep(sweepIndex);
		for(Iterator<Panel> it = ww.getPanels(); it.hasNext(); )
		{
			Panel wp = it.next();
			wp.repaintWithRulers();
		}
	}
}
