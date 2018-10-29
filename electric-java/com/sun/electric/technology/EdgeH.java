/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: EdgeH.java
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
 * An EdgeH is a scalable X coordinate that converts a NodeInst bounds to a location inside of that NodeInst.
 * It consists of two numbers: a <I>multiplier</I> and an <I>adder</I>.
 * The resulting location starts at the center of the NodeInst,
 * adds the NodeInst width times the multiplier,
 * adds the adder.
 * <P>
 * For example, the center of the NodeInst simply has multiplier = 0 and adder = 0.
 * The left edge of the NodeInst has multiplier = -0.5 and adder = 0.
 * The point that is 2 left of the right edge has multiplier = 0.5 and adder = -2.
 * The point that is 3 right of the center has multiplier = 0 and adder = 3.
 */
public class EdgeH implements Serializable {

    /** The multiplier (scales the width by this amount). */
    private final double multiplier;
    private final int multiplierInt;
    /** The adder (adds this amount to the scaled width) in grid units. */
    private final ECoord adder;

    /**
     * Constructs an <CODE>EdgeH</CODE> with the specified values.
     * @param multiplier is the multiplier to store in the EdgeV.
     * @param adder is the adder to store in the EdgeV.
     */
    public EdgeH(double multiplier, double adder) {
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
     * Compare to another EdgeH
     * @param other the other EdgeH to compare.
     * @return true if the two have the same values.
     */
    @Override
    public boolean equals(Object other) {
        if (!(other instanceof EdgeH)) {
            return false;
        }
        EdgeH otherE = (EdgeH) other;
        return multiplier == otherE.multiplier && adder.equals(otherE.adder);
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 47 * hash + (int) (Double.doubleToLongBits(this.multiplier) ^ (Double.doubleToLongBits(this.multiplier) >>> 32));
        hash = 47 * hash + (this.adder != null ? this.adder.hashCode() : 0);
        return hash;
    }

    /**
     * Returns the multiplier.
     * This is the amount to scale a NodeInst width.
     * @return the multiplier.
     */
    public double getMultiplier() {
        return multiplier;
    }

    /**
     * Returns the adder as ECoord object.
     * This is the amount to add to a NodeInst width.
     * @return the adder.
     */
    public ECoord getAdder() {
        return adder;
    }
    
    /**
     * Returns the fixed-point value of this EdgeH
     * @param size size of primitive
     * @return the fixed-point value
     */
    public long getFixpValue(EPoint size) {
        long fixpAdder = adder.getFixp();
        switch (multiplierInt) {
            case -1:
                return fixpAdder - (size.getFixpX() >> 1);
            case 0:
                return fixpAdder;
            case 1:
                return fixpAdder + (size.getFixpX() >> 1);
            default:
                return fixpAdder + (long)Math.rint(multiplier * (size.getFixpX()));
        }
    }

    /**
     * Returns the grid value of this EdgeH
     * @param size size of primitive
     * @return the grid value
     */
    public long getGridValue(EPoint size) {
        long gridAdder = adder.getGrid();
        switch (multiplierInt) {
            case -1:
                return gridAdder - (size.getGridX() >> 1);
            case 0:
                return gridAdder;
            case 1:
                return gridAdder + (size.getGridX() >> 1);
            default:
                return gridAdder + (long)Math.rint(multiplier * (size.getGridX()));
        }
    }

    /**
     * Returns EdgeH with the new adder.
     * @param gridAdder the new adder.
     * @return EdgeH with the new adder
     */
    public EdgeH withGridAdder(long gridAdder) {
        if (this.adder.getFixp() == gridAdder) {
            return this;
        }
        return new EdgeH(this.multiplier, DBMath.gridToLambda(gridAdder));
    }

    /**
     * Describes a position that moves left.
     * @param amt the x-coordinate of the position.
     */
    public static EdgeH l(double amt) {
        return new EdgeH(-0.5, amt);
    }

    /**
     * Describes a position that doesnt't move.
     * @param amt the x-coordinate of the position.
     */
    public static EdgeH c(double amt) {
        return new EdgeH(0, amt);
    }

    /**
     * Describes a position that moves right.
     * @param amt the x-coordinate of the position.
     */
    public static EdgeH r(double amt) {
        return new EdgeH(0.5, amt);
    }
    
    public static EdgeH by(double width, double amt) {
        return new EdgeH(amt/2, amt*width/2);
    }
    
    public static EdgeH by0(double amt) {
        return by(0, amt);
    }

    public static EdgeH by2(double amt) {
        return by(2, amt);
    }

    public static EdgeH by3(double amt) {
        return by(3, amt);
    }

    public static EdgeH by4(double amt) {
        return by(4, amt);
    }

    public static EdgeH by6(double amt) {
        return by(6, amt);
    }

    public static EdgeH by8(double amt) {
        return by(8, amt);
    }

    public static EdgeH by10(double amt) {
        return by(10, amt);
    }

    /**
     * Describes a position that is in from the left by a specified amount.
     * @param amt the amount to inset from the left of a NodeInst.
     */
    public static EdgeH fromLeft(double amt) {
        return new EdgeH(-0.5, amt);
    }

    /**
     * Describes a position that is in from the right by a specified amount.
     * @param amt the amount to inset from the right of a NodeInst.
     */
    public static EdgeH fromRight(double amt) {
        return new EdgeH(0.5, -amt);
    }

    /**
     * Describes a position that is away from the center by a specified amount.
     * @param amt the amount to move away from the center of the NodeInst.
     */
    public static EdgeH fromCenter(double amt) {
        return new EdgeH(0.0, amt);
    }

    /**
     * Creates a position that describes the center of the NodeInst.
     * @return a position that describes the center of the NodeInst.
     */
    public static EdgeH makeCenter() {
        return fromCenter(0);
    }

    /**
     * Returns a printable version of this EdgeH.
     * @return a printable version of this EdgeH.
     */
    @Override
    public String toString() {
        return "EdgeH(" + multiplier + "," + adder.getLambda() + ")";
    }
}
