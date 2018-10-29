/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: MovieCreator.java
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
package com.sun.electric.api.movie;

import java.awt.Dimension;
import java.io.File;
import java.util.List;

/**
 * API to create a movie from a sequence of images.
 */
public interface MovieCreator {

    /**
     * Create a movie from a sequence of images
     * @param outputFile output file for movie
     * @param dim size of the movie
     * @param inputFiles files wuth JPEG imaphes
     */
    public void createFromImages(File outputFile, Dimension dim, List<File> inputFiles);

}
