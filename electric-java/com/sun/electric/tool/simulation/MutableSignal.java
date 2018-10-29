/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: MutableSignal.java
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
package com.sun.electric.tool.simulation;

/**
 * A Signal to which one may add and remove samples.
 */
public abstract class MutableSignal<SS extends Sample> extends Signal<SS>
{

    public MutableSignal(SignalCollection sc, Stimuli sd, String signalName, String signalContext, boolean digital)
    {
        super(sc, sd, signalName, signalContext, digital);
    }

    public abstract SS   getSample(double time);
    public abstract void addSample(double time, SS sample);
    public abstract void replaceSample(double time, SS sample);
}
