/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: LayoutMergerHierImpl.java
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

import com.sun.electric.database.CellTree;
import com.sun.electric.database.ImmutableNodeInst;
import com.sun.electric.database.geometry.PolyBase.PolyBaseTree;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.id.CellId;
import com.sun.electric.technology.Layer;
import com.sun.electric.util.ElapseTimer;
import com.sun.electric.util.math.Orientation;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 *
 */
public class LayoutMergerHierImpl implements LayoutMerger
{
    final VectorCache vectorCache;
    private final Cell topCell;
    private static final int TMP_FILE_THRESHOLD = 1000000;
    private static final int[] NULL_INT_ARRAY =
    {
    };

    static class Factory extends LayoutMergerFactory
    {
        @Override
        public LayoutMerger newMerger(Cell topCell)
        {
            return new LayoutMergerHierImpl(topCell);
        }
    }

    LayoutMergerHierImpl(Cell topCell)
    {
        vectorCache = new VectorCache(topCell.getDatabase().backup());
        vectorCache.scanLayers(topCell.getId());
        this.topCell = topCell;
    }

    @Override
    public Collection<Layer> getLayers()
    {
        return vectorCache.getLayers();
    }

    @Override
    public boolean canMerge(Layer layer)
    {
        return !vectorCache.isBadLayer(layer);
    }

    Collection<CellTree> downTop(CellTree top)
    {
        LinkedHashSet<CellTree> result = new LinkedHashSet<CellTree>();
        downTop(result, top);
        return result;
    }

    private void downTop(LinkedHashSet<CellTree> result, CellTree t)
    {

        if (!result.contains(t))
        {
            for (CellTree subTree : t.getSubTrees())
            {
                if (subTree != null)
                {
                    downTop(result, subTree);
                }
            }
            result.add(t);
        }
    }

    byte[] mergeLocalLayerToByteArray(CellId cellId, Layer layer) throws IOException
    {
        int numBoxes = vectorCache.getNumBoxes(cellId, layer);
        if (numBoxes == 0)
        {
            return null;
        }
        int[] boxCoords = new int[4];
        PointsSorter ps = new PointsSorter();
        for (int i = 0; i < numBoxes; i++)
        {
            vectorCache.getBoxes(cellId, layer, i, 1, boxCoords);
            int lx = boxCoords[0];
            int ly = boxCoords[1];
            int hx = boxCoords[2];
            int hy = boxCoords[3];
            ps.put(lx, ly, hx, hy);
        }

        DeltaMerge dm = new DeltaMerge();
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bout);
        dm.loop(ps, out);
        byte[] ba = bout.toByteArray();
        out.close();

        return ba;
    }

    int[] byteArray2coordArray(byte[] ba)
    {
        try
        {
            DataInputStream inpS = new DataInputStream(new ByteArrayInputStream(ba));
            List<Integer> pos = new ArrayList<Integer>();
            List<Integer> neg = new ArrayList<Integer>();
            while (inpS.readBoolean())
            {
                int x = inpS.readInt();
                int count = inpS.readInt();
                for (int i = 0; i < count; i++)
                {
                    int yp = inpS.readInt();
                    int y = yp >> 1;
                    boolean positive = (yp & 1) != 0;
                    List<Integer> b = positive ? pos : neg;
                    b.add(x);
                    b.add(y);
                }
            }
            assert pos.size() == neg.size();
            int sz = pos.size();
            int[] result = new int[sz * 2];
            for (int i = 0; i < sz; i++)
            {
                result[i] = pos.get(i);
                result[sz + i] = neg.get(i);
            }
            return result;
        } catch (IOException e)
        {
            throw new AssertionError();
        }
    }

    void mergeLayer(Map<CellId, int[]> mergedCoords, CellId topCellId, Layer layer, boolean rotate, DataOutputStream out)
    {
        PointsSorter ps = new PointsSorter();
        int[] coordsBuf = new int[1024];
        Orientation topOrient = (rotate ? Orientation.XR : Orientation.IDENT).canonic();
        ElapseTimer timer1 = ElapseTimer.createInstance().start();
        coordsBuf = collectLayer(mergedCoords, ps, coordsBuf, topCellId, 0, 0, topOrient);
        timer1.end();
        ElapseTimer timer2 = ElapseTimer.createInstance().start();
        DeltaMerge dm = new DeltaMerge();
        int outPoints = dm.loop(ps, out);
        timer2.end();
        System.out.println(layer + " " + ps.size() + "->" + outPoints + " points"
            + ", merge=" + timer1 + " sec"
            + ", tree=" + timer2 + " sec");
    }

    private int[] collectLayer(Map<CellId, int[]> mergedCoords, PointsSorter ps, int[] coordsBuf, CellId cellId, int x, int y, Orientation orient)
    {
        int[] coords = mergedCoords.get(cellId);
        if (coords.length != 0)
        {
            if (coordsBuf.length < coords.length)
            {
                int newLen = coordsBuf.length;
                while (newLen < coords.length)
                {
                    newLen *= 2;
                }
                coordsBuf = new int[newLen];
            }
            int numPoints = coords.length / 2;
            assert numPoints % 2 == 0;
            orient.transformPoints(numPoints, coords, coordsBuf);
            boolean orientRot = orient.getCAngle() != 0 && orient.getCAngle() != 1800;
            boolean positive = !orientRot;
            for (int i = 0; i < numPoints; i++)
            {
                if (i * 2 == numPoints)
                {
                    positive = !positive;
                }
                ps.put(x + coordsBuf[i * 2 + 0], y + coordsBuf[i * 2 + 1], positive);
            }
        }
        List<ImmutableNodeInst> subCells = vectorCache.getSubcells(cellId);
        for (ImmutableNodeInst n : subCells)
        {
            assert n.orient.isManhattan();
            coordsBuf[0] = (int)n.anchor.getGridX();
            coordsBuf[1] = (int)n.anchor.getGridY();
            orient.transformPoints(1, coordsBuf);
            Orientation subOrient = orient.concatenate(n.orient).canonic();
            CellId subCellId = (CellId)n.protoId;
            coordsBuf = collectLayer(mergedCoords, ps, coordsBuf, subCellId, x + coordsBuf[0], y + coordsBuf[1], subOrient);
        }
        return coordsBuf;
    }

    void flattenAndMergeLayer(Layer layer, DataOutputStream out) throws IOException
    {
        Collection<CellTree> dt = downTop(topCell.tree());
        Map<CellId, int[]> mergedCoords = new LinkedHashMap<CellId, int[]>();
        for (CellTree t : dt)
        {
            CellId cellId = t.top.cellRevision.d.cellId;
            byte[] ba = mergeLocalLayerToByteArray(cellId, layer);
            if (ba != null)
            {
                mergedCoords.put(cellId, byteArray2coordArray(ba));
            } else
            {
                mergedCoords.put(cellId, NULL_INT_ARRAY);
            }
        }
        boolean rotate = false;
        mergeLayer(mergedCoords, topCell.getId(), layer, rotate, out);
    }

    byte[] mergeInMemory(Layer layer) throws IOException
    {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bout);
        flattenAndMergeLayer(layer, out);
        out.close();
        return bout.toByteArray();
    }

    File mergeInFile(Layer layer) throws IOException
    {
        File file = File.createTempFile("Electric", "DRC");
        DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)));
        flattenAndMergeLayer(layer, out);
        out.close();
        return file;
    }

    @Override
    public Iterable<PolyBaseTree> merge(Layer layer)
    {
        try
        {
            boolean inMemory = vectorCache.getNumFlatBoxes(topCell.getId(), layer) <= TMP_FILE_THRESHOLD;
            Iterable<PolyBaseTree> trees;
            if (inMemory)
            {
                byte[] ba = mergeInMemory(layer);
                DataInputStream inpS = new DataInputStream(new ByteArrayInputStream(ba));
                UnloadPolys up = new UnloadPolys();
                trees = up.loop(inpS, false);
                inpS.close();
            } else
            {
                File file = mergeInFile(layer);
                DataInputStream inpS = new DataInputStream(new BufferedInputStream(new FileInputStream(file)));
                UnloadPolys up = new UnloadPolys();
                trees = up.loop(inpS, false);
                inpS.close();
                file.delete();
            }
            return trees;
        } catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }
}
