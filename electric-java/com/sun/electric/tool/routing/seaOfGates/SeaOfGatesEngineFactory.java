/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: SeaOfGatesFactory.java
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
package com.sun.electric.tool.routing.seaOfGates;

/**
 * @author Felix Schmidt
 *
 */
public class SeaOfGatesEngineFactory {

    public enum SeaOfGatesEngineType {
        defaultVersion, oldThreads, batchInfrastructure, batchSemaphore
    }

    /**
     * Create a SeaOfGates version using the default version
     *
     * @return a SeaOfGates engine.
     */
    public static SeaOfGatesEngine createSeaOfGatesEngine() {
        return createSeaOfGatesEngine(SeaOfGatesEngineType.defaultVersion);
    }

    public static SeaOfGatesEngine createSeaOfGatesEngine(SeaOfGatesEngineType version) {
        SeaOfGatesEngine result = null;

        switch (version) {
            case batchInfrastructure:
            case batchSemaphore:
                return new SeaOfGatesEngineNonoverlappingBatch(version);
            default:
                return new SeaOfGatesEngineOld();
        }
    }
}
