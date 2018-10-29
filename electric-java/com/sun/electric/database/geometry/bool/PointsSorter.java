/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: PointsSorter.java
 * Written by Dmitry Nadezhin.
 *
 * Copyright (c) 2010, Static Free Software. All rights reserved.
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
package com.sun.electric.database.geometry.bool;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 *
 */
public class PointsSorter
{
    private static final int LIMIT_TO_SAVE = 1 << 24;
    private static final int LIMIT_TO_SPLIT = 1 << 25;
    int pointsOut;
    long[] points = new long[1];
    int pointsInFiles;
    List<File> files = new ArrayList<File>();
    DataInputStream[] inps;
    long[] inpH;
    int curPoint;
    int[] outA = new int[2];
    int outC;
    ScanLine outS = new ScanLine();
    boolean fixed;

    public void put(int lx, int ly, int hx, int hy)
    {
        put(lx, ly, true);
        put(lx, hy, false);
        put(hx, ly, false);
        put(hx, hy, true);
    }

    public void put(int x, int y, boolean positive)
    {
        fixed = false;
        if (pointsOut >= points.length)
        {
            if (pointsOut >= LIMIT_TO_SPLIT)
            {
                saveToFile();
                assert pointsOut == 0;
            } else
            {
                long[] newPoints = new long[points.length * 2];
                System.arraycopy(points, 0, newPoints, 0, points.length);
                points = newPoints;
            }
        }
        if (x == Integer.MAX_VALUE || y < -0x40000000 || y > 0x3fffffff)
        {
            throw new IllegalArgumentException();
        }
        long p = (((long)x) << 32) | (((y + 0x40000000) << 1) & 0xfffffffeL);
        if (positive)
        {
            p |= 1;
        }
        points[pointsOut] = p;
        pointsOut += 1;
    }

    public ScanLine fix()
    {
        if (!fixed)
        {
            if (pointsOut != 0 && (!files.isEmpty() || pointsOut > LIMIT_TO_SAVE))
            {
                saveToFile();
            } else
            {
                Arrays.sort(points, 0, pointsOut);
            }
            fixed = true;
        }
        try
        {
            return files.isEmpty() ? getFromArray() : getFromStreams();
        } catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    private void saveToFile()
    {
        try
        {
            Arrays.sort(points, 0, pointsOut);
            File file = File.createTempFile("Electric", "DRC");
            file.deleteOnExit();
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)));
            for (int i = 0; i < pointsOut; i++)
            {
                out.writeLong(points[i]);
            }
            out.writeLong(Long.MAX_VALUE);
            out.close();
            files.add(file);
            pointsInFiles += pointsOut;
            pointsOut = 0;
        } catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    public int size()
    {
        return pointsInFiles + pointsOut;
    }

    void putOutBuf(int y, int d)
    {
        if (d == 0)
        {
            return;
        }
        if (outC * 2 >= outA.length)
        {
            int[] newOutA = new int[outA.length * 2];
            System.arraycopy(outA, 0, newOutA, 0, outA.length);
            outA = newOutA;
        }
        outA[outC * 2 + 0] = y;
        outA[outC * 2 + 1] = d;
        outC += 1;
    }

    void reset()
    {
        curPoint = 0;
    }

    ScanLine getFromArray()
    {
        outC = 0;
        int curX = 0;
        int curY = 0;
        int curD = 0;

        while (curPoint < pointsOut)
        {
            long p = points[curPoint];
            int x = (int)(p >> 32);
            int y = (0x80000000 + (int)(p)) >> 1;
            int d = (p & 1) != 0 ? 1 : -1;
            if (outC == 0 && curD == 0)
            {
                curX = x;
                curY = y;
                curD = d;
            } else if (x != curX)
            {
                putOutBuf(curY, curD);
                assert outC != 0;
                outS.x = curX;
                outS.y = outA;
                outS.len = outC;
                return outS;
            } else if (y != curY)
            {
                putOutBuf(curY, curD);
                curY = y;
                curD = d;
            } else
            {
                curD += d;
            }
            curPoint += 1;
        }
        if (outC == 0 && curD == 0)
        {
            return null;
        } else
        {
            putOutBuf(curY, curD);
            assert outC != 0;
            outS.x = curX;
            outS.y = outA;
            outS.len = outC;
            return outS;
        }
    }

    ScanLine getFromStreams() throws IOException
    {
        if (inps == null)
        {
            inps = new DataInputStream[files.size()];
            inpH = new long[files.size()];
            for (int i = 0; i < files.size(); i++)
            {
                DataInputStream inp = new DataInputStream(new BufferedInputStream(new FileInputStream(files.get(i))));
                inps[i] = inp;
                inpH[i] = inp.readLong();
            }
        }
        outC = 0;
        int curX = 0;
        int curY = 0;
        int curD = 0;

        long minL = Long.MAX_VALUE;
        int minI = -1;
        for (int i = 0; i < inpH.length; i++)
        {
            if (inpH[i] < minL)
            {
                minL = inpH[i];
                minI = i;
            }
        }
        while (minL != Long.MAX_VALUE)
        {
            long p = minL;
            int x = (int)(p >> 32);
            int y = (0x80000000 + (int)(p)) >> 1;
            int d = (p & 1) != 0 ? 1 : -1;
            if (outC == 0 && curD == 0)
            {
                curX = x;
                curY = y;
                curD = d;
            } else if (x != curX)
            {
                putOutBuf(curY, curD);
                assert outC != 0;
                outS.x = curX;
                outS.y = outA;
                outS.len = outC;
                return outS;
            } else if (y != curY)
            {
                putOutBuf(curY, curD);
                curY = y;
                curD = d;
            } else
            {
                curD += d;
            }
            curPoint += 1;

            long l = inps[minI].readLong();
            inpH[minI] = l;
            if (l == Long.MAX_VALUE)
            {
                inps[minI].close();
                files.get(minI).delete();
                inps[minI] = null;
            }
            minL = Long.MAX_VALUE;
            minI = -1;
            for (int i = 0; i < inpH.length; i++)
            {
                if (inpH[i] < minL)
                {
                    minL = inpH[i];
                    minI = i;
                }
            }
        }
        if (outC == 0 && curD == 0)
        {
            return null;
        } else
        {
            putOutBuf(curY, curD);
            assert outC != 0;
            outS.x = curX;
            outS.y = outA;
            outS.len = outC;
            return outS;
        }
    }

    public static class ScanLine
    {
        public int x;
        public int[] y;
        public int len;
    }
}
