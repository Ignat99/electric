/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: ResistorCurrent.java
 * Written by Tom O'Neill.
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
package com.sun.electric.tool.simulation.test;

/**
 * Infers current through a resistor from a voltage measurement, implements
 * <code>CurrentReadable</code>.
 */
public class ResistorCurrent implements CurrentReadable {

    /** Resistance in Ohms */
    public float ohms;

    /** Readout of voltage across resistor */
    public VoltageReadable voltmeter;
    
    /**
     * Create object to indirectly measure current through a resistor.
     * 
     * @param ohms Resistance in Ohms
     * @param voltmeter Readout of voltage across resistor 
     */
    public ResistorCurrent(float ohms, VoltageReadable voltmeter) {
        super();
        this.ohms = ohms;
        this.voltmeter = voltmeter;
    }
    

    /**
     * Returns voltage across resistor divided by resistance.
     * 
     * @return current through the resistor
     * @see com.sun.electric.tool.simulation.test.CurrentReadable#readCurrent()
     */
    public float readCurrent() {
        return voltmeter.readVoltage()/ohms;
    }
    
    /**
     * Returns voltage across resistor divided by resistance.
     * 
     * @return current through the resistor
     * @see com.sun.electric.tool.simulation.test.CurrentReadable#readCurrent()
     */
    public float readCurrent(float ampsExpected, float ampsResolution) {
        Infrastructure.fatal("NOt implemented yet");
        return 0.f;
    }
    
    /** Unit test */
    public static void main(String[] args) {
    }

}
