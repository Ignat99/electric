/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: EmptyPatternedOutlinedTransparentRaster.java
 *
 * Copyright (c) 2006, Static Free Software. All rights reserved.
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

package com.sun.electric.tool.user.redisplay;

import com.sun.electric.database.geometry.EGraphics;

/**
 *
 * @author dn146861
 */
public class EmptyPatternedOutlinedTransparentRaster extends TransparentRaster {
    
    final EGraphics.Outline outline;
    
    EmptyPatternedOutlinedTransparentRaster(int[] layerBitMap, int intsPerRow, EGraphics.Outline outline) {
        super(intsPerRow, layerBitMap);
        this.outline = outline;
    }

    @Override
    public void fillBox(int lX, int hX, int lY, int hY) {
    }

    @Override
    public void fillHorLine(int y, int lX, int hX) {
    }

    @Override
    public void fillVerLine(int x, int lY, int hY) {
    }

    @Override
    public void fillPoint(int x, int y) {
    }

    @Override
    public EGraphics.Outline getOutline() {
        return outline;
    }
}
