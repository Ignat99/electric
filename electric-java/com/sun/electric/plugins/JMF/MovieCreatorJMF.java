/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: MovieCreatorJMF.java
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
package com.sun.electric.plugins.JMF;

import com.sun.electric.api.movie.MovieCreator;
import com.sun.electric.tool.Job;

import java.awt.Dimension;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of MovieCreator API using Java Media Frame.
 */
public class MovieCreatorJMF implements MovieCreator {
    
    public MovieCreatorJMF() {
        // Check if JMF is available
    	try 
    	{
    		String version = javax.media.Manager.getVersion();
    	}
    	catch (Exception e)
    	{
    		if (Job.getDebug())
    			System.out.println("No javax.media.Manager, exception");
    	}
    	catch (Error r)
    	{
    		if (Job.getDebug())
    			System.out.println("No javax.media.Manager");
    	}
    }

    /**
     * Create a movie from a sequence of images
     * @param outputFile output file for movie
     * @param dim size of the movie
     * @param inputFiles files wuth JPEG imaphes
     */
    public void createFromImages(File outputFile, Dimension dim, List<File> inputFiles) {
        List<String> inputFileNames = new ArrayList<String>();
        for (File file: inputFiles) {
            inputFileNames.add(file.getAbsolutePath());
        }
        JMFImageToMovie.createMovie(outputFile.getAbsolutePath(), dim, inputFileNames);
    }

}
