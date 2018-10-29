/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: FixpCoord.java
 * Written by: Dmitry Nadezhin.
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
package com.sun.electric.util.math;

import java.awt.geom.Point2D;
import java.io.Serializable;

/**
 * Immutable class that represents fixed-point coordinates and distances.
 * Electric API measures coordinates in double lambda unit.
 * Coordinates in the Electric database are aligned at grid .
 * The number of grid steps per lambda unit is {@link #GRIDS_IN_LAMBDA}.
 * Internal computations (like non-manhattan rotation) can produce
 * coordinates that are not grid-aligned.
 * They are represented as fixed-point long numbers.
 * Their fractional bits can represent fraction of the grid step.
 */
public class FixpCoord implements Serializable, Comparable<FixpCoord> {

    /**
     * Number of fractional bits in fixed-point numbers.
     */
    public static final int FRACTION_BITS = 20;
    /**
     * Multiplier from grid values to fixed-point values.
     */
    public static final double FIXP_SCALE = 1L << FRACTION_BITS;
    /**
     * Number of database grid steps in lambda unit.
     */
    public static final long GRIDS_IN_LAMBDA = 400;
    /**
     * Zero coordinate.
     */
    public static final ECoord ZERO = new ECoord(0);
    /**
     * Fixed-point unit.
     */
    public static final FixpCoord FIXP = new FixpCoord(1);
    /**
     * Database grid unit.
     */
    public static final ECoord GRID = new ECoord(1L << FRACTION_BITS);
    /**
     * Database sizes is aligned at size grid.
     * Its step is two database grid steps.
     */
    public static final ECoord SIZE_GRID = new ECoord(2L << FRACTION_BITS);
    /**
     * Lambda unit. Database sizes is aligned at size grid.
     * Its step is two database grid steps.
     */
    public static final ECoord LAMBDA = new ECoord(GRIDS_IN_LAMBDA << FRACTION_BITS);
    // private constants
    /**
     * Lambda unit expressed as fixed-point number.
     */
    private static final long LAMBDA_UNIT = GRIDS_IN_LAMBDA << FRACTION_BITS;
    static final long FRACTION_MASK = (1L << FRACTION_BITS) - 1;
    private static final long SIZE_GRID_MASK = (1L << (FRACTION_BITS + 1)) - 1;
    private static final long HALF_MASK = (1L << (FRACTION_BITS - 1)) - 1;
    private static final int GRIDS_SIGNIFICAND = 25;
    private static final int GRIDS_EXPONENT = 4;
    // DoubleConsts
    private static final int SIGNIFICAND_WIDTH = 53;
    private static final long SIGN_BIT_MASK = Long.MIN_VALUE;
    private static final long SIGNIF_BIT_MASK = (1L << (SIGNIFICAND_WIDTH - 1)) - 1;
    private static final long EXP_BIT_MASK = -1L & ~(SIGN_BIT_MASK | SIGNIF_BIT_MASK);
    private static final int EXP_BIAS = Double.MAX_EXPONENT;

    static {
        assert GRIDS_SIGNIFICAND >= 1 && GRIDS_SIGNIFICAND < (1 << 10);
        assert GRIDS_IN_LAMBDA == (GRIDS_SIGNIFICAND << GRIDS_EXPONENT);
    }
    /**
     * Fixed-point value.
     */
    private final long fixp;

    FixpCoord(long fixp) {
        this.fixp = fixp;
    }

    /**
     * Creates FixpCoord object from fixed-point long.
     * @param fixp fixed-point long
     * @return FixpCoord object.
     */
    public static FixpCoord fromFixp(long fixp) {
        if (fixp == 0) {
            return ZERO;
        } else if ((fixp & FRACTION_MASK) == 0) {
            return new ECoord(fixp);
        } else {
            return new FixpCoord(fixp);
        }
    }

    static ECoord fromAlignedFixp(long fixp) {
        return fixp == 0 ? ZERO : new ECoord(fixp);
    }

    public static FixpCoord fromLambda(double lambda) {
        if (lambda == 0) {
            return ZERO;
        }
        return fromFixp(lambdaToFixp(lambda));
    }

    public static ECoord fromLambdaRoundGrid(double lambda) {
        if (lambda == 0) {
            return ZERO;
        }
        return fromAlignedFixp(lambdaToGridFixp(lambda));
    }

    public static ECoord fromLambdaRoundSizeGrid(double lambda) {
        if (lambda == 0) {
            return ZERO;
        }
        return fromAlignedFixp(lambdaToSizeGridFixp(lambda));
    }

    public static long lambdaToSizeGridFixp(double lambda) {
        return lambdaRound(lambda, SIZE_GRID);
    }

    public static long lambdaToGridFixp(double lambda) {
        return lambdaRound(lambda, GRID);
    }

    public static long lambdaToFixp(double lambda) {
        long ieeeBits = Double.doubleToRawLongBits(lambda);
        int biasedExp = (int) ((ieeeBits & EXP_BIT_MASK) >> (SIGNIFICAND_WIDTH - 1));
        int q = biasedExp + (GRIDS_EXPONENT + FRACTION_BITS - EXP_BIAS - (SIGNIFICAND_WIDTH - 1));
        long significand = ieeeBits & SIGNIF_BIT_MASK | (1L << (SIGNIFICAND_WIDTH - 1));
        long signMul = significand * GRIDS_SIGNIFICAND;
        long fixp;
        if (q < 0) {
            int shift = -q;
            if (q <= -Long.SIZE) {
                return 0;
            }
            fixp = (((signMul - (((~signMul) >> shift) & 1)) >> (shift - 1)) + 1) >> 1;
        } else {
            if (q > Long.SIZE - 1 || (signMul & (-1L << Long.SIZE - 1 - q)) != 0) {
                throw new ArithmeticException();
            }
            fixp = signMul << q;
            assert fixp > 0;
        }
        return ieeeBits >= 0 ? fixp : -fixp;
    }

    /**
     * Returns true if this coordinate is aligned at a grid specified by parameter
     * @param resolution grid step
     * @return true if this coordinate is a multiple of <code>resolution</code>
     */
    public boolean isExact(ECoord resolution) {
        return isMultiple(fixp, resolution);
    }

    public ECoord round(ECoord resolution) {
        long newFixp = round(fixp, resolution);
        return newFixp == fixp ? (ECoord) this : fromAlignedFixp(newFixp);
    }

    public ECoord floor(ECoord resolution) {
        long newFixp = floor(fixp, resolution);
        return newFixp == fixp ? (ECoord) this : fromAlignedFixp(newFixp);
    }

    public ECoord ceil(ECoord resolution) {
        long newFixp = ceil(fixp, resolution);
        return newFixp == fixp ? (ECoord) this : fromAlignedFixp(newFixp);
    }

    /**
     * Returns true if the first coordinate is the multiple of the second.
     * @param fixp the first coordinate as fixed-number long
     * @param resolution the second coordinare
     * @return true if <code>fixp</code> is the multiple of <code>resolution</code>
     */
    public static boolean isMultiple(long fixp, ECoord resolution) {
        return GenMath.isMultiple(fixp, resolution.getFixp());
    }

    public static long lambdaRound(double lambda, ECoord resolution) {
        return roundLambda(lambda, resolution.getFixp(), GRIDS_SIGNIFICAND, GRIDS_EXPONENT);
    }

    private static long roundLambda(double lambda, long res, int scaleSignificand, int scaleExponent) {
        long ieeeBits = Double.doubleToRawLongBits(lambda);
        int biasedExp = (int) ((ieeeBits & EXP_BIT_MASK) >> (SIGNIFICAND_WIDTH - 1));
        int q = biasedExp + (scaleExponent + (FRACTION_BITS - EXP_BIAS - (SIGNIFICAND_WIDTH - 1)));
        long significand = ieeeBits & SIGNIF_BIT_MASK | (1L << (SIGNIFICAND_WIDTH - 1));
        long signMul = significand * scaleSignificand;
        long fixp;
        if (q < 0) {
            int shift = -q;
            if (shift >= Long.SIZE || res > (signMul >> (shift - 1))) {
                return 0;
            }
            long newFixp = GenMath.roundToMultiple(signMul, res << shift);
//            long newFixp;
//            if (res == Long.lowestOneBit(res)) {
//                newFixp = roundShift(signMul, res << shift);
//            } else {
//                newFixp = roundToMultipleHalfEven(signMul, res << shift);
//            }
            newFixp >>>= shift;
            return ieeeBits >= 0 ? newFixp : -newFixp;
        } else {
            if (q > Long.SIZE - 1 || (signMul & (-1L << Long.SIZE - 1 - q)) != 0) {
                throw new ArithmeticException();
            }
            fixp = GenMath.roundToMultiple(signMul << q, res);
            assert fixp > 0;
            return ieeeBits >= 0 ? fixp : -fixp;
        }
    }

    public static long round(long fixp, ECoord resolution) {
        return GenMath.roundToMultiple(fixp, resolution.getFixp());
    }

    public static long floor(long fixp, ECoord resolution) {
        return GenMath.roundToMultipleFloor(fixp, resolution.getFixp());
    }

    public static long ceil(long fixp, ECoord resolution) {
        return GenMath.roundToMultipleCeiling(fixp, resolution.getFixp());
    }

    public int signum() {
        return fixp > 0 ? +1 : fixp < 0 ? -1 : 0;
    }

    /**
     * Returns this number measured in lambda units.
     * @return this number measured in lambda units.
     */
    public double getLambda() {
        return fixpToLambda(fixp);
    }

    public static double fixpToLambda(long fixp) {
        return fixp / (double) LAMBDA_UNIT;
    }

    public static double fixpToGridDouble(long fixp) {
        return fixp * (1.0 / (1L << FRACTION_BITS));
    }
    
    public static Point2D.Double fixpToGridPoint(long fixpX, long fixpY) {
        return new Point2D.Double(fixpToGridDouble(fixpX), fixpToGridDouble(fixpY));
    }

    public static Point2D.Double fixpToLambdaPoint(long fixpX, long fixpY) {
        return new Point2D.Double(fixpToLambda(fixpX), fixpToLambda(fixpY));
    }

    /**
     * Returns this number measured in fixed-point units.
     * @return this number measured in fixed-point units.
     */
    public long getFixp() {
        return fixp;
    }

    /**
     * Returns sum of two coordinats.
     * @param y second coordinate.
     * @return sum of two coordinates.
     */
    public FixpCoord add(FixpCoord y) {
        if (y.fixp == 0) {
            return this;
        } else if (this.fixp == 0) {
            return y;
        } else {
            return fromFixp(this.fixp + y.fixp);
        }
    }

    /**
     * Returns difference of two coordinats.
     * @param y second coordinate.
     * @return difference of two coordinates.
     */
    public FixpCoord subtract(FixpCoord y) {
        if (y.fixp == 0) {
            return this;
        } else {
            return fromFixp(this.fixp - y.fixp);
        }
    }

    /**
     * Returns product of this coordinate by double multiplier.
     * @param scale multiplier
     * @return product of this coordinate by double multiplier.
     */
    public FixpCoord multiply(double scale) {
        if (scale == 1.0) {
            return this;
        }
        return fromFixp((long) Math.rint(fixp * scale));
    }

    /**
     * Returns product of this coordinate by long multiplier.
     * @param scale multiplier
     * @return product of this coordinate by long multiplier.
     */
    public FixpCoord multiply(long scale) {
        if (scale == 1) {
            return this;
        }
        return fromFixp(fixp * scale);
    }

    /**
     * Returns minimum of two coordinats.
     * @param y second coordinate.
     * @return minimum of two coordinates.
     */
    public FixpCoord min(FixpCoord y) {
        return this.fixp <= y.fixp ? this : y;
    }

    /**
     * Returns maximum of two coordinats.
     * @param y second coordinate.
     * @return maximum of two coordinates.
     */
    public FixpCoord max(FixpCoord y) {
        return this.fixp >= y.fixp ? this : y;
    }

    /**
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object o) {
        return o instanceof FixpCoord && this.fixp == ((FixpCoord) o).fixp;
    }

    /**
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        return (int) fixp;
    }

    /**
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return Double.toString(getLambda());
    }

    /**
     * @see java.lang.Comparable#compareTo(java.lang.Object)
     */
    @Override
    public int compareTo(FixpCoord that) {
        return this.fixp < that.fixp ? -1 : this.fixp == that.fixp ? 0 : 1;
    }
}
