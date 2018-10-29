/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: EdgeV.java
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
package com.sun.electric.technology;

import com.sun.electric.database.geometry.EPoint;
import com.sun.electric.util.math.DBMath;
import com.sun.electric.util.math.ECoord;

import java.io.Serializable;

/**
 * An EdgeV is a scalable Y coordinate that converts a NodeInst bounds to a location inside of that NodeInst.
 * It consists of two numbers: a <I>multiplier</I> and an <I>adder</I>.
 * The resulting location starts at the center of the NodeInst,
 * adds the NodeInst height times the multiplier,
 * adds the adder.
 * <P>
 * For example, the center of the NodeInst simply has multiplier = 0 and adder = 0.
 * The bottom of the NodeInst has multiplier = -0.5 and adder = 0.
 * The point that is 2 below the top has multiplier = 0.5 and adder = -2.
 * The point that is 3 above the center has multiplier = 0 and adder = 3.
 */
public class EdgeV implements Serializable {

    /** The multiplier (scales the height by this amount). */
    private final double multiplier;
    private final int multiplierInt;
    /** The adder (adds this amount to the scaled width) in grid units. */
    private final ECoord adder;

    /**
     * Constructs an <CODE>EdgeV</CODE> with the specified values.
     * @param multiplier is the multiplier to store in the EdgeV.
     * @param adder is the adder to store in the EdgeV.
     */
    public EdgeV(double multiplier, double adder) {
        this.multiplier = multiplier;
        if (multiplier == 0.0) {
            multiplierInt = 0;
        } else if (multiplier == +0.5) {
            multiplierInt = 1;
        } else if (multiplier == -0.5) {
            multiplierInt = -1;
        } else {
            multiplierInt = Integer.MIN_VALUE;
        }
        this.adder = ECoord.fromLambdaRoundGrid(adder);
    }

    /**
     * Compare to another EdgeV
     * @param other the other EdgeV to compare.
     * @return true if the two have the same values.
     */
    @Override
    public boolean equals(Object other) {
        if (!(other instanceof EdgeV)) {
            return false;
        }
        EdgeV otherE = (EdgeV) other;
        return multiplier == otherE.multiplier && adder.equals(otherE.adder);
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 17 * hash + (int) (Double.doubleToLongBits(this.multiplier) ^ (Double.doubleToLongBits(this.multiplier) >>> 32));
        hash = 17 * hash + (this.adder != null ? this.adder.hashCode() : 0);
        return hash;
    }

    /**
     * Returns the multiplier.
     * This is the amount to scale a NodeInst height.
     * @return the multiplier.
     */
    public double getMultiplier() {
        return multiplier;
    }

    /**
     * Returns the adder as ECoord object.
     * This is the amount to add to a NodeInst height.
     * @return the adder.
     */
    public ECoord getAdder() {
        return adder;
    }

    /**
     * Returns the fixed-point value of this EdgeV
     * @param size size of primitive
     * @return the fixed-point value
     */
    public long getFixpValue(EPoint size) {
        long fixpAdder = adder.getFixp();
        switch (multiplierInt) {
            case -1:
                return fixpAdder - (size.getFixpY() >> 1);
            case 0:
                return fixpAdder;
            case 1:
                return fixpAdder + (size.getFixpY() >> 1);
            default:
                return fixpAdder + (long)Math.rint(multiplier * (size.getFixpY()));
        }
    }

    /**
     * Returns the grid value of this EdgeV
     * @param size size of primitive
     * @return the grid value
     */
    public long getGridValue(EPoint size) {
        long gridAdder = adder.getGrid();
        switch (multiplierInt) {
            case -1:
                return gridAdder - (size.getGridY() >> 1);
            case 0:
                return gridAdder;
            case 1:
                return gridAdder + (size.getGridY() >> 1);
            default:
                return gridAdder + (long)Math.rint(multiplier * (size.getGridY()));
        }
    }

    /**
     * Returns EdgeV with the new adder.
     * @param gridAdder the new adder.
     * @return EdgeV with the new adder
     */
    public EdgeV withGridAdder(long gridAdder) {
        if (this.adder.getFixp() == gridAdder) {
            return this;
        }
        return new EdgeV(this.multiplier, DBMath.gridToLambda(gridAdder));
    }

    /**
     * Describes a position that moves bottom.
     * @param amt the y-coordinate of the position.
     */
    public static EdgeV b(double amt) {
        return new EdgeV(-0.5, amt);
    }

    /**
     * Describes a position that doesnt't move.
     * @param amt the y-coordinate of the position.
     */
    public static EdgeV c(double amt) {
        return new EdgeV(0, amt);
    }

    /**
     * Describes a position that moves top.
     * @param amt the y-coordinate of the position.
     */
    public static EdgeV t(double amt) {
        return new EdgeV(0.5, amt);
    }

    public static EdgeV by(double width, double amt) {
        return new EdgeV(amt/2, amt*width/2);
    }
    
    public static EdgeV by0(double amt) {
        return by(0, amt);
    }

    public static EdgeV by2(double amt) {
        return by(2, amt);
    }

    public static EdgeV by3(double amt) {
        return by(3, amt);
    }

    public static EdgeV by4(double amt) {
        return by(4, amt);
    }

    public static EdgeV by6(double amt) {
        return by(6, amt);
    }

    public static EdgeV by10(double amt) {
        return by(10, amt);
    }

    /**
     * Describes a position that is in from the top by a specified amount.
     * @param amt the amount to inset from the top of a NodeInst.
     */
    public static EdgeV fromTop(double amt) {
        return new EdgeV(0.5, -amt);
    }

    /**
     * Describes a position that is in from the bottom by a specified amount.
     * @param amt the amount to inset from the bottom of a NodeInst.
     */
    public static EdgeV fromBottom(double amt) {
        return new EdgeV(-0.5, amt);
    }

    /**
     * Describes a position that is away from the center by a specified amount.
     * @param amt the amount to move away from the center of the NodeInst.
     */
    public static EdgeV fromCenter(double amt) {
        return new EdgeV(0.0, amt);
    }

    /**
     * Creates a position that describes the center of the NodeInst.
     * @return a position that describes the center of the NodeInst.
     */
    public static EdgeV makeCenter() {
        return fromCenter(0);
    }

    /**
     * Returns a printable version of this EdgeV.
     * @return a printable version of this EdgeV.
     */
    @Override
    public String toString() {
        return "EdgeV(" + multiplier + "," + adder.getLambda() + ")";
    }
}
