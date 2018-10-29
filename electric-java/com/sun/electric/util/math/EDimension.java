/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Dimension2D.java
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
package com.sun.electric.util.math;

import java.awt.geom.Dimension2D;
import java.io.Serializable;

/**
 * Class to define a EDimension object.
 * Its coordinates are aligned at Electric database {@link ECoord#SIZE_GRID } .
 */
public class EDimension extends Dimension2D implements Serializable {

    private final ECoord width;
    private final ECoord height;
    private final double lambdaWidth;
    private final double lambdaHeight;

    /**
     * Constructor to build an EDimension.
     * @param width the width of this EDimension.
     * @param height the height of this EDimension.
     */
    public EDimension(double width, double height) {
        this(ECoord.fromLambdaRoundSizeGrid(width), ECoord.fromLambdaRoundSizeGrid(height));
    }

    /**
     * Constructor to build an EDimension.
     * @param width the width of this EDimension.
     * @param height the height of this EDimension.
     */
    public EDimension(ECoord width, ECoord height) {
        if (!width.isExact(ECoord.SIZE_GRID) || !height.isExact(ECoord.SIZE_GRID)) {
            throw new IllegalArgumentException();
        }
        this.width = width;
        this.height = height;
        this.lambdaWidth = width.getLambda();
        this.lambdaHeight = height.getLambda();
    }

    /**
     * Method to return the X size of this EDimension.
     * @return the X size of this EDimension.
     */
    @Override
    public double getWidth() {
        return lambdaWidth;
    }

    /**
     * Method to return the Y size of this EDimension.
     * @return the Y size of this EDimension.
     */
    @Override
    public double getHeight() {
        return lambdaHeight;
    }

    /**
     * Method to return the X size of this EDimension.
     * @return the X size of this EDimensio.
     */
    public ECoord getCoordWidth() {
        return width;
    }

    /**
     * Method to return the Y size of this EDimension.
     * @return the Y size of this EDimension.
     */
    public ECoord getCoordHeight() {
        return height;
    }

    /**
     * Method to return the X size of this EDimension.
     * @return the X size of this EDimension.
     */
    public double getLambdaWidth() {
        return lambdaWidth;
    }

    /**
     * Method to return the Y size of this EDimension.
     * @return the Y size of this EDimension.
     */
    public double getLambdaHeight() {
        return lambdaHeight;
    }

    /**
     * Method to return the X size of this EDimension.
     * @return the X size of this EDimension.
     */
    public long getFixpWidth() {
        return width.getFixp();
    }

    /**
     * Method to return the Y size of this EDimension.
     * @return the Y size of this EDimension.
     */
    public long getFixpHeight() {
        return height.getFixp();
    }

    /**
     * Method to return the X size of this EDimension.
     * @return the X size of this EDimension.
     */
    public long getGridWidth() {
        return width.getGrid();
    }

    /**
     * Method to return the Y size of this EDimension.
     * @return the Y size of this EDimension.
     */
    public long getGridHeight() {
        return height.getGrid();
    }

    /**
     * Method to set the size of this EDimension.
     * param d the new size of this EDimension.
     */
    @Override
    public void setSize(Dimension2D d) {
        throw new UnsupportedOperationException();
    }

    /**
     * Method to set the size of this EDimension.
     * param width the new width of this EDimension.
     * param height the new height of this EDimension.
     */
    @Override
    public void setSize(double width, double height) {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns a printable version of this EDimension.
     * @return a printable version of this EDimension.
     */
    @Override
    public String toString() {
        return "(" + lambdaWidth + "," + lambdaHeight + ")";
    }
}
