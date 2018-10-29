/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: J3DCanvas3D.java
 * Written by Gilda Garreton.
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

package com.sun.electric.plugins.j3d.utils;

import com.sun.electric.api.movie.MovieCreator;
import com.sun.electric.plugins.j3d.ui.J3DMenu;

import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;
import javax.media.j3d.Canvas3D;
import javax.media.j3d.GraphicsContext3D;
import javax.media.j3d.ImageComponent;
import javax.media.j3d.ImageComponent2D;
import javax.media.j3d.Raster;
import javax.vecmath.Point3f;

/** Inspired in example found in www.j3d.org
 * @author  Gilda Garreton
 * @version 0.1
*/

public class J3DCanvas3D extends Canvas3D  {

    public String filePath = null;
    public boolean writePNG_;
    public BufferedImage img;
    private int count;
    public boolean movieMode;

    public J3DCanvas3D(GraphicsConfiguration gc)
    {
	    super(gc);
    }

    public void renderField( int fieldDesc )
	{
        try {
            super.renderField( fieldDesc );
        } catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    List<File> inputFiles = new ArrayList<File>();
    public void saveMovie(File file)
    {
    	MovieCreator mc = J3DMenu.getMovieCreator();
    	if (mc == null) return;
        mc.createFromImages(file, getSize(), inputFiles);
    }

    public void resetMoveFrames()
    {
        inputFiles.clear();
    }

    public void postSwap()
    {
        if(writePNG_)
        {
            Dimension dim = getSize();
            GraphicsContext3D  ctx = getGraphicsContext3D();
            // The raster components need all be set!

            Raster ras = new Raster(
                       new Point3f(-1.0f, -1.0f, -1.0f),
               Raster.RASTER_COLOR,
               0,0,
               dim.width, dim.height,
               new ImageComponent2D(
                                 ImageComponent.FORMAT_RGB,
                     new BufferedImage(dim.width, dim.height,
                               BufferedImage.TYPE_INT_RGB)),
               null);

            ctx.readRaster(ras);

            // Now strip out the image info
            img = ras.getImage().getImage();
            writePNG_ = false;

            if (movieMode)
            {
                try
                {
                    File capture = new File("Capture" + count + ".jpg");
                    inputFiles.add(capture);
                    FileOutputStream out = new FileOutputStream(capture);

                    javax.imageio.ImageIO.write(img, "jpg", out);

//                    JPEGImageEncoder encoder = JPEGCodec.createJPEGEncoder(out);
//                     JPEGEncodeParam param = encoder.getDefaultJPEGEncodeParam(img);
//                     param.setQuality(0.75f,false); // 75% quality for the JPEG
//                     encoder.setJPEGEncodeParam(param);
//                     encoder.encode(img);
                     out.close();
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
                count++;
            }
            else if (filePath != null)  // for png export
            {
                File tmp = new File(filePath);
                try
                {
                    ImageIO.write(img, "PNG", tmp);
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }

                filePath = null;
            }
        }

    }
}
