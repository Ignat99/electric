/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: MinAreaChecker.java
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
package com.sun.electric.api.minarea;

import java.util.Properties;

/**
 *
 */
public interface MinAreaChecker {

    /**
     * Name of parameter that specifies number of working threads.
     */
    public static final String NUM_THREADS = "NumThreads";
    /**
     * Name of parameter that specifies recommended size of a stripe.
     */
    public static final String RECTS_PER_STRIPE = "RectsPerStripe";
    /**
     * Name of parameter that specifies to report tiles of error polygon.
     */
    public static final String REPORT_TILES = "ReportTiles";

    /**
     * 
     * @return the algorithm name
     */
    public String getAlgorithmName();

    /**
     * 
     * @return the names and default values of algorithm parameters
     */
    public Properties getDefaultParameters();

    /**
     * @param topCell top cell of the layout
     * @param minArea minimal area of valid polygon
     * @param parameters algorithm parameters
     * @param errorLogger an API to report violations
     */
    public void check(LayoutCell topCell, long minArea, Properties parameters, ErrorLogger errorLogger);
}
