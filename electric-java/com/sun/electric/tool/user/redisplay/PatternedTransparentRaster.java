/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: ERaster.java
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
 * ERaster for patterned transparent layers.
 */
class PatternedTransparentRaster extends TransparentRaster {

    final int[] pattern;
    final EGraphics.Outline outline;

    PatternedTransparentRaster(int[] layerBitMap, int intsPerRow, int[] pattern, EGraphics.Outline outline) {
        super(intsPerRow, layerBitMap);
        this.pattern = pattern;
        this.outline = outline;
    }

    @Override
    public void fillBox(int lX, int hX, int lY, int hY) {
        int baseIndex = lY * intsPerRow;
        int lIndex = baseIndex + (lX >> 5);
        int hIndex = baseIndex + (hX >> 5);
        if (lIndex == hIndex) {
            int mask = (2 << (hX & 31)) - (1 << (lX & 31));
            for (int y = lY; y < hY; y++) {
                int pat = mask & pattern[y & 15];
                if (pat != 0) {
                    layerBitMap[lIndex] |= pat;
                }
                lIndex += intsPerRow;
            }
        } else {
            int lMask = -(1 << (lX & 31));
            int hMask = (2 << (hX & 31)) - 1;
            for (int y = lY; y <= hY; y++) {
                int pat = pattern[y & 15];
                if (pat != 0) {
                    layerBitMap[lIndex] |= lMask & pat;
                    for (int index = lIndex + 1; index < hIndex; index++) {
                        layerBitMap[index] |= pat;
                    }
                    layerBitMap[hIndex] |= hMask & pat;
                }
                lIndex += intsPerRow;
                hIndex += intsPerRow;
            }
        }
    }

    @Override
    public void fillHorLine(int y, int lX, int hX) {
        int pat = pattern[y & 15];
        if (pat == 0) {
            return;
        }
        int baseIndex = y * intsPerRow;
        int lIndex = baseIndex + (lX >> 5);
        int hIndex = baseIndex + (hX >> 5);
        if (lIndex == hIndex) {
            int mask = pat & ((2 << (hX & 31)) - (1 << (lX & 31)));
            if (mask != 0) {
                layerBitMap[lIndex] |= mask;
            }
        } else {
            layerBitMap[lIndex++] |= pat & (-(1 << (lX & 31)));
            while (lIndex < hIndex) {
                layerBitMap[lIndex++] |= pat;
            }
            layerBitMap[hIndex] |= pat & ((2 << (hX & 31)) - 1);
        }
    }

    @Override
    public void fillVerLine(int x, int lY, int hY) {
        int baseIndex = lY * intsPerRow + (x >> 5);
        int mask = 1 << (x & 31);
        for (int y = lY; y <= hY; y++) {
            if ((pattern[y & 15] & mask) != 0) {
                layerBitMap[baseIndex] |= mask;
            }
            baseIndex += intsPerRow;
        }
    }

    @Override
    public void fillPoint(int x, int y) {
        int mask = (1 << (x & 31)) & pattern[y & 15];
        if (mask != 0) {
            layerBitMap[y * intsPerRow + (x >> 5)] |= mask;
        }
    }

    @Override
    public EGraphics.Outline getOutline() {
        return outline;
    }

    @Override
    public void copyBits(TransparentRaster src, int minSrcX, int maxSrcX, int minSrcY, int maxSrcY, int dx, int dy) {
        int[] srcLayerBitMap = src.layerBitMap;
        assert (minSrcY + dy) * intsPerRow + ((minSrcX + dx) >> 5) >= 0;
        assert (maxSrcY + dy) * intsPerRow + ((maxSrcX + dx) >> 5) < layerBitMap.length;
        for (int srcY = minSrcY; srcY <= maxSrcY; srcY++) {
            int destY = srcY + dy;
            int pat = pattern[destY & 15];
            if (pat == 0) {
                continue;
            }
            int srcBaseIndex = srcY * src.intsPerRow;
            int destBaseIndex = destY * intsPerRow;
            for (int srcX = minSrcX; srcX <= maxSrcX; srcX++) {
                int destX = srcX + dx;
                if ((srcLayerBitMap[srcBaseIndex + (srcX >> 5)] & (1 << (srcX & 31))) != 0) {
                    int destMask = 1 << (destX & 31);
                    if ((pat & destMask) != 0) {
                        layerBitMap[destBaseIndex + (destX >> 5)] |= destMask;
                    }
                }
            }
        }
    }
}
