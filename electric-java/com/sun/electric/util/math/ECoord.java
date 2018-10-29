/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: ECoord.java
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

/**
 * Fixed-point number aligned at Electric database grid.
 */
public class ECoord extends FixpCoord {
    
    public static final ECoord MIN_ECOORD = new ECoord(Long.MIN_VALUE);
    public static final ECoord MAX_ECOORD = new ECoord(Long.MIN_VALUE - (1L << FRACTION_BITS));
    
    ECoord(long fixp) {
        super(fixp);
        assert (fixp & FRACTION_MASK) == 0;
    }
    
    /**
     * Create ECoord object from database grid units
     * @param grid coordinate in database grid units
     * @return ECoord object
     */
    public static ECoord fromGrid(long grid) {
        return fromAlignedFixp(grid << FRACTION_BITS);
    }
    
    /**
     * Returns this coordinate in database grid units.
     * @return this coordinate in database grid units.
     */
    public long getGrid() {
        return getFixp() >> FRACTION_BITS;
    }
    
    /**
     * Returns sum of two coordinats.
     * @param y second coordinate.
     * @return sum of two coordinates.
     */
    public ECoord add(ECoord y) {
        long xFixp = this.getFixp();
        long yFixp = y.getFixp();
        if (yFixp == 0) {
            return this;
        } else if (xFixp == 0) {
            return y;
        } else {
            return fromAlignedFixp(xFixp + yFixp);
        }
    }

    /**
     * Returns difference of two coordinats.
     * @param y second coordinate.
     * @return difference of two coordinates.
     */
    public ECoord subtract(ECoord y) {
        long xFixp = this.getFixp();
        long yFixp = y.getFixp();
        if (yFixp == 0) {
            return this;
        } else {
            return fromAlignedFixp(xFixp - yFixp);
        }
    }

    /**
     * Returns product of this coordinate by long multiplier.
     * @param scale multiplier
     * @return product of this coordinate by long multiplier.
     */
    @Override
    public ECoord multiply(long scale) {
        if (scale == 1) {
            return this;
        }
        return fromAlignedFixp(getFixp() * scale);
    }

    /**
     * Returns minimum of two coordinats.
     * @param y second coordinate.
     * @return minimum of two coordinates.
     */
    public ECoord min(ECoord y) {
        return this.getFixp() <= y.getFixp() ? this : y;
    }

    /**
     * Returns maximum of two coordinats.
     * @param y second coordinate.
     * @return maximum of two coordinates.
     */
    public ECoord max(ECoord y) {
        return this.getFixp() >= y.getFixp() ? this : y;
    }

}
