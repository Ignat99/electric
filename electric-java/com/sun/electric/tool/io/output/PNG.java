/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: PNG.java
 * Written by Gilda Garreton.
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
package com.sun.electric.tool.io.output;

import javax.imageio.ImageIO;
import java.io.File;
import java.util.Iterator;
import java.awt.image.BufferedImage;

/**
 * Format to write PNG (Portable Network Graphics) output
 */
public class PNG extends Output
{
	/**
	 * Main entry point for PNG output.
	 * @param img image to export
	 * @param filePath the name of the file to create.
	 */
	public static void writeImage(BufferedImage img, String filePath)
	{
		// just do this file
		//writeCellToFile(cell, context, filePath);
		File tmp = new File(filePath);

		if (!canWriteFormat("PNG"))
		{
			System.out.println("PNG format cannot be generated");
			return;
		}

        try {
            ImageIO.write(img, "PNG", tmp);
            System.out.println(filePath + " written");
        }
        catch (Exception e)
        {
	        e.printStackTrace();
            System.out.println("PNG output '" + filePath + "' cannot be generated");
        }
	}

	/**
	 * Returns true if the specified format name can be written
	 * @param formatName
	 */
    public static boolean canWriteFormat(String formatName) {
        Iterator iter = ImageIO.getImageWritersByFormatName(formatName);
        return iter.hasNext();
    }
}
