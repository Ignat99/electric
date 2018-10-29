/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: EditWindow0.java
 *
 * Copyright (c) 2003, Static Free Software. All rights reserved.
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
package com.sun.electric.database.variable;

import com.sun.electric.tool.user.GraphicsPreferences;

import java.io.Serializable;
import java.awt.*;
import java.awt.geom.*;

/**
 * This interface gives a limited access to EditWindow necessary
 * for calculating a shape of some primitives.
 */
public interface EditWindow0 {

    /**
     * Get the window's VarContext
     * @return the current VarContext
     */
    public VarContext getVarContext();

    /**
     * Method to return the scale factor for this window.
     * @return the scale factor for this window.
     */
    public double getScale();

    /**
     * Method to return the text scale factor for this window.
     * @return the text scale factor for this window.
     */
    public double getGlobalTextScale();

    /**
     * Method to return the default font for this window.
     * @return the default font for this window.
     */
    public String getDefaultFont();

    public Rectangle2D getGlyphBounds(String text, Font font);

    /**
     * Class to encapsulate the minimal EditWindow0 data needed to pass into Jobs.
     */
    public static class EditWindowSmall implements EditWindow0, Serializable
    {

        private VarContext context;
        private double scale;
        private double globalScale;
        private String defaultFont;

        public EditWindowSmall(EditWindow_ wnd)
        {
        	if (wnd == null)
        	{
                context = null;
                scale = 1;
                globalScale = 1;
                defaultFont = GraphicsPreferences.FACTORY_DEFAULT_FONT;
        	} else
        	{
	            context = wnd.getVarContext();
	            scale = wnd.getScale();
	            globalScale = wnd.getGlobalTextScale();
	            defaultFont = wnd.getDefaultFont();
        	}
        }

        public Rectangle2D getGlyphBounds(String text, Font font) { return null; }

        /**
         * Get the window's VarContext
         * @return the current VarContext
         */
        public VarContext getVarContext() { return context; }

        /**
         * Method to return the scale factor for this window.
         * @return the scale factor for this window.
         */
        public double getScale() { return scale; }

        /**
         * Method to return the text scale factor for this window.
         * @return the text scale factor for this window.
         */
        public double getGlobalTextScale() { return globalScale; }

        /**
         * Method to return the default font for this window.
         * @return the default font for this window.
         */
        public String getDefaultFont() { return defaultFont; }
    }
}
