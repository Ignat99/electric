/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: TransparentRaster.java
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
 * ERaster for solid transparent layers.
 */
public class TransparentRaster implements ERaster {

    final int[] layerBitMap;
    final int intsPerRow;

    TransparentRaster(int intsPerRow, int[] layerBitMap) {
        this.layerBitMap = layerBitMap;
        this.intsPerRow = intsPerRow;
    }

    public TransparentRaster(int numIntsPerRow, int height) {
        this.intsPerRow = numIntsPerRow;
        layerBitMap = new int[height * numIntsPerRow];
    }

    @Override
    public void fillBox(int lX, int hX, int lY, int hY) {
        int baseIndex = lY * intsPerRow;
        int lIndex = baseIndex + (lX >> 5);
        int hIndex = baseIndex + (hX >> 5);
        if (lIndex == hIndex) {
            int mask = (2 << (hX & 31)) - (1 << (lX & 31));
            for (int y = lY; y < hY; y++) {
                layerBitMap[lIndex] |= mask;
                lIndex += intsPerRow;
            }
        } else {
            int lMask = -(1 << (lX & 31));
            int hMask = (2 << (hX & 31)) - 1;
            for (int y = lY; y <= hY; y++) {
                layerBitMap[lIndex] |= lMask;
                for (int index = lIndex + 1; index < hIndex; index++) {
                    layerBitMap[index] = -1;
                }
                layerBitMap[hIndex] |= hMask;
                lIndex += intsPerRow;
                hIndex += intsPerRow;
            }
        }
    }

    public void eraseBox(int lX, int hX, int lY, int hY) {
        int baseIndex = lY * intsPerRow;
        int lIndex = baseIndex + (lX >> 5);
        int hIndex = baseIndex + (hX >> 5);
        if (lIndex == hIndex) {
            int mask = (2 << (hX & 31)) - (1 << (lX & 31));
            mask = ~mask;
            for (int y = lY; y < hY; y++) {
                layerBitMap[lIndex] &= mask;
                lIndex += intsPerRow;
            }
        } else {
            int lMask = -(1 << (lX & 31));
            int hMask = (2 << (hX & 31)) - 1;
            lMask = ~lMask;
            hMask = ~hMask;
            for (int y = lY; y <= hY; y++) {
                layerBitMap[lIndex] &= lMask;
                for (int index = lIndex + 1; index < hIndex; index++) {
                    layerBitMap[index] = 0;
                }
                layerBitMap[hIndex] &= hMask;
                lIndex += intsPerRow;
                hIndex += intsPerRow;
            }
        }
    }

    public void eraseAll() {
        for (int i = 0; i < layerBitMap.length; i++) {
            layerBitMap[i] = 0;
        }
    }

    @Override
    public void fillHorLine(int y, int lX, int hX) {
        int baseIndex = y * intsPerRow;
        int lIndex = baseIndex + (lX >> 5);
        int hIndex = baseIndex + (hX >> 5);
        if (lIndex == hIndex) {
            layerBitMap[lIndex] |= (2 << (hX & 31)) - (1 << (lX & 31));
        } else {
            layerBitMap[lIndex++] |= -(1 << (lX & 31));
            while (lIndex < hIndex) {
                layerBitMap[lIndex++] |= -1;
            }
            layerBitMap[hIndex] |= (2 << (hX & 31)) - 1;
        }
    }

    @Override
    public void fillVerLine(int x, int lY, int hY) {
        int baseIndex = lY * intsPerRow + (x >> 5);
        int mask = 1 << (x & 31);
        for (int y = lY; y <= hY; y++) {
            layerBitMap[baseIndex] |= mask;
            baseIndex += intsPerRow;
        }
    }

    @Override
    public void fillPoint(int x, int y) {
        layerBitMap[y * intsPerRow + (x >> 5)] |= (1 << (x & 31));
    }

    @Override
    public void drawHorLine(int y, int lX, int hX) {
        int baseIndex = y * intsPerRow;
        int lIndex = baseIndex + (lX >> 5);
        int hIndex = baseIndex + (hX >> 5);
        if (lIndex == hIndex) {
            layerBitMap[lIndex] |= (2 << (hX & 31)) - (1 << (lX & 31));
        } else {
            layerBitMap[lIndex++] |= -(1 << (lX & 31));
            while (lIndex < hIndex) {
                layerBitMap[lIndex++] |= -1;
            }
            layerBitMap[hIndex] |= (2 << (hX & 31)) - 1;
        }
    }

    @Override
    public void drawVerLine(int x, int lY, int hY) {
        int baseIndex = lY * intsPerRow + (x >> 5);
        int mask = 1 << (x & 31);
        for (int y = lY; y <= hY; y++) {
            layerBitMap[baseIndex] |= mask;
            baseIndex += intsPerRow;
        }
    }

    @Override
    public void drawPoint(int x, int y) {
        layerBitMap[y * intsPerRow + (x >> 5)] |= (1 << (x & 31));
    }

    @Override
    public EGraphics.Outline getOutline() {
        return null;
    }

    @Override
    public void copyBits(TransparentRaster src, int minSrcX, int maxSrcX, int minSrcY, int maxSrcY, int dx, int dy) {
        int[] srcLayerBitMap = src.layerBitMap;
        int minDestX = minSrcX + dx;
        int maxDestX = maxSrcX + dx;
        int minDestY = minSrcY + dy;
        //            int maxDestY = maxSrcY + dy;
        int leftShift = dx & 31;
        int rightShift = 32 - leftShift;
        int srcBaseIndex = minSrcY * src.intsPerRow + (minSrcX >> 5);
        int destBaseIndex = minDestY * intsPerRow + (minDestX >> 5);
        int numDestInts = (maxDestX >> 5) - (minDestX >> 5);
        if (numDestInts == 0) {
            // Single destination byte.
            int destMask = (2 << (maxDestX & 31)) - (1 << (minDestX & 31));
            if ((minSrcX >> 5) != (maxSrcX >> 5)) {
                // A pair of source bytes
                for (int srcY = minSrcY; srcY <= maxSrcY; srcY++) {
                    int s0 = srcLayerBitMap[srcBaseIndex];
                    int s1 = srcLayerBitMap[srcBaseIndex + 1];
                    int v = ((s0 >>> rightShift) | (s1 << leftShift)) & destMask;
                    if (v != 0) {
                        layerBitMap[destBaseIndex] |= v;
                    }
                    srcBaseIndex += src.intsPerRow;
                    destBaseIndex += intsPerRow;
                }
            } else if ((minDestX & 31) >= (minSrcX & 31)) {
                // source byte shifted left
                for (int srcY = minSrcY; srcY <= maxSrcY; srcY++) {
                    int s = srcLayerBitMap[srcBaseIndex];
                    int v = (s << leftShift) & destMask;
                    if (v != 0) {
                        layerBitMap[destBaseIndex] |= v;
                    }
                    srcBaseIndex += src.intsPerRow;
                    destBaseIndex += intsPerRow;
                }
            } else {
                // source byte shifted right
                for (int srcY = minSrcY; srcY <= maxSrcY; srcY++) {
                    int s = srcLayerBitMap[srcBaseIndex];
                    int v = (s >>> rightShift) & destMask;
                    if (v != 0) {
                        layerBitMap[destBaseIndex] |= v;
                    }
                    srcBaseIndex += src.intsPerRow;
                    destBaseIndex += intsPerRow;
                }
            }
        } else {
            int minDestMask = -(1 << (minDestX & 31));
            int maxDestMask = (2 << (maxDestX & 31)) - 1;
            int srcIncr = src.intsPerRow - (maxSrcX >> 5) + (minSrcX >> 5) - 1;
            if (leftShift == 0) {
                for (int srcY = minSrcY; srcY <= maxSrcY; srcY++) {
                    assert srcBaseIndex == srcY * src.intsPerRow + (minSrcX >> 5);
                    assert destBaseIndex == (srcY + dy) * intsPerRow + (minDestX >> 5);
                    int v0 = srcLayerBitMap[srcBaseIndex++] & minDestMask;
                    if (v0 != 0) {
                        layerBitMap[destBaseIndex] |= v0;
                    }
                    destBaseIndex++;
                    for (int i = 1; i < numDestInts; i++) {
                        int v = srcLayerBitMap[srcBaseIndex++];
                        if (v != 0) {
                            layerBitMap[destBaseIndex] |= v;
                        }
                        destBaseIndex++;
                    }
                    int vf = srcLayerBitMap[srcBaseIndex++] & maxDestMask;
                    if (vf != 0) {
                        layerBitMap[destBaseIndex] |= vf;
                    }
                    srcBaseIndex += srcIncr;
                    destBaseIndex += (intsPerRow - numDestInts);
                }
            } else if (numDestInts == 2 && (minSrcX >> 5) == (maxSrcX >> 5)) {
                for (int srcY = minSrcY; srcY <= maxSrcY; srcY++) {
                    assert srcBaseIndex == srcY * src.intsPerRow + (minSrcX >> 5);
                    assert destBaseIndex == (srcY + dy) * intsPerRow + (minDestX >> 5);
                    int s = srcLayerBitMap[srcBaseIndex];
                    int b0 = srcLayerBitMap[srcBaseIndex++];
                    int v0 = (s << leftShift) & minDestMask;
                    if (v0 != 0) {
                        layerBitMap[destBaseIndex] |= v0;
                    }
                    int vf = (s >>> rightShift) & maxDestMask;
                    if (vf != 0) {
                        layerBitMap[destBaseIndex + 1] |= vf;
                    }
                    srcBaseIndex += src.intsPerRow;
                    destBaseIndex += intsPerRow;
                }
            } else {
                boolean minSrcPair = leftShift > (minDestX & 31);
                boolean maxSrcPair = leftShift <= (maxDestX & 31);
                for (int srcY = minSrcY; srcY <= maxSrcY; srcY++) {
                    assert srcBaseIndex == srcY * src.intsPerRow + (minSrcX >> 5);
                    assert destBaseIndex == (srcY + dy) * intsPerRow + (minDestX >> 5);
                    int s = minSrcPair ? srcLayerBitMap[srcBaseIndex++] : 0;
                    int b0 = srcLayerBitMap[srcBaseIndex++];
                    int v0 = ((s >>> rightShift) | (b0 << leftShift)) & minDestMask;
                    if (v0 != 0) {
                        layerBitMap[destBaseIndex] |= v0;
                    }
                    destBaseIndex++;
                    s = b0;
                    for (int i = 1; i < numDestInts; i++) {
                        int b = srcLayerBitMap[srcBaseIndex++];
                        int v = (s >>> rightShift) | (b << leftShift);
                        if (v != 0) {
                            layerBitMap[destBaseIndex] |= v;
                        }
                        destBaseIndex++;
                        s = b;
                    }
                    int bf = maxSrcPair ? srcLayerBitMap[srcBaseIndex++] : 0;
                    int vf = ((s >>> rightShift) | (bf << leftShift)) & maxDestMask;
                    if (vf != 0) {
                        layerBitMap[destBaseIndex] |= vf;
                    }
                    srcBaseIndex += srcIncr;
                    destBaseIndex += (intsPerRow - numDestInts);
                }
            }
        }
    }
}
