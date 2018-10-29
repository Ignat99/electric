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
 * ERaster for solid opaque layers.
 */
class OpaqueRaster implements ERaster {

    int[] opaqueData;
    int width;
    int col;

    OpaqueRaster(int[] opaqueData, int width, int col) {
        this.opaqueData = opaqueData;
        this.width = width;
        this.col = col;
    }

    @Override
    public void fillBox(int lX, int hX, int lY, int hY) {
        int baseIndex = lY * width;
        for (int y = lY; y <= hY; y++) {
            for (int x = lX; x <= hX; x++) {
                opaqueData[baseIndex + x] = col;
            }
            baseIndex += width;
        }
    }

    @Override
    public void fillHorLine(int y, int lX, int hX) {
        int baseIndex = y * width + lX;
        for (int x = lX; x <= hX; x++) {
            opaqueData[baseIndex++] = col;
        }
    }

    @Override
    public void fillVerLine(int x, int lY, int hY) {
        int baseIndex = lY * width + x;
        for (int y = lY; y <= hY; y++) {
            opaqueData[baseIndex] = col;
            baseIndex += width;
        }
    }

    @Override
    public void fillPoint(int x, int y) {
        opaqueData[y * width + x] = col;
    }

    @Override
    public void drawHorLine(int y, int lX, int hX) {
        int baseIndex = y * width + lX;
        for (int x = lX; x <= hX; x++) {
            opaqueData[baseIndex++] = col;
        }
    }

    @Override
    public void drawVerLine(int x, int lY, int hY) {
        int baseIndex = lY * width + x;
        for (int y = lY; y <= hY; y++) {
            opaqueData[baseIndex] = col;
            baseIndex += width;
        }
    }

    @Override
    public void drawPoint(int x, int y) {
        opaqueData[y * width + x] = col;
    }

    @Override
    public EGraphics.Outline getOutline() {
        return null;
    }

    @Override
    public void copyBits(TransparentRaster src, int minSrcX, int maxSrcX, int minSrcY, int maxSrcY, int dx, int dy) {
        int[] srcLayerBitMap = src.layerBitMap;
        for (int srcY = minSrcY; srcY <= maxSrcY; srcY++) {
            int destY = srcY + dy;
            int destBase = destY * width;
            int srcBaseIndex = srcY * src.intsPerRow;
            for (int srcX = minSrcX; srcX <= maxSrcX; srcX++) {
                int destX = srcX + dx;
                if ((srcLayerBitMap[srcBaseIndex + (srcX >> 5)] & (1 << (srcX & 31))) != 0) {
                    opaqueData[destBase + destX] = col;
                }
            }
        }
    }
}
