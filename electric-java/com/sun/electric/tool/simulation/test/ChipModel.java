/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: ChipModel.java
 * Written by Jonathan Gainsley.
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
package com.sun.electric.tool.simulation.test;

import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import java.io.*;

/**
 * The ChipModel is meant to abstract the underlying device under test. This
 * device may be a real chip, hooked up to measurement devices in the lab,
 * or it may be a Nanosim simulation, driven by Nanosim models of measurement
 * devices.
 */
public interface ChipModel {

    /**
     * Wait for the specified number of seconds. During this
     * time the chip will run, assuming any activity is set to run
     * on the chip.
     * @param seconds the number of seconds to wait.
     */
    public void wait(float seconds);

    /**
     * Wait for the specified number of nanoseconds. During this
     * time the chip will run, assuming any activity is set to run
     * on the chip.
     * @param nanoseconds the number of nanoseconds to wait.
     */
    public void waitNS(double nanoseconds);

    /**
     * Wait for the specified number of picoseconds. During this
     * time the chip will run, assuming any activity is set to run
     * on the chip.
     * @param picoseconds the number of picoseconds to wait.
     */
    public void waitPS(double picoseconds);

    

}
