/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: TreeTiledCell.java
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
package com.sun.electric.tool.generator.layout.fillCell;

import com.sun.electric.database.EditingPreferences;
import com.sun.electric.tool.generator.layout.fill.TiledCell;
import com.sun.electric.tool.generator.layout.fill.FillGenConfig;
import com.sun.electric.tool.generator.layout.fill.G;
import com.sun.electric.tool.generator.layout.LayoutLib;
import com.sun.electric.tool.generator.layout.TechType;
import com.sun.electric.util.math.DBMath;
import com.sun.electric.util.math.GenMath;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.Library;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.topology.ArcInst;

import com.sun.electric.util.math.FixpTransform;
import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Collections;
import java.awt.geom.Rectangle2D;
import java.awt.geom.Area;

/**
 * User: gg151869
 * Date: Sep 19, 2006
 */
public class TreeTiledCell extends TiledCell {
    public TreeTiledCell(FillGenConfig conf, EditingPreferences ep)
    {
        super(conf, ep);
    }

    enum CutType {NONE,X,Y,XY}

    /**
     * Method to calculate number of master sizes to cover using binary division or the intersection approach
     * @param size
     * @param binary
     * @return number of master cells
     */
    private static int getFillDimension(int size, boolean binary)
    {
        if (size <=2) return size;
        if (binary)
        {
            // calculate the closest upper 2^n so the qTree will be perfectely balanced.
            double val = Math.sqrt(size);
            val = Math.ceil(val);
            return (int)Math.pow(2,val);
        }
        // otherwise just the closest even number
        if (size%2 != 0) //odd number
        {
            size -= 1;

        }
        return size;
    }

    /**
     * ----------------------------
     * |     2      |      3      |
     * ----------------------------
     * |     0      |      1      |
     * ----------------------------
     * @param masters
     * @param empty
     * @param autoLib
     * @param box  bounding box representing region to refine. (0,0) is the top left corner
     * @param topCell
     * @param area
     * @return true if refined elements form std cell
     */
    private boolean refine(List<Cell> masters, Rectangle2D masterBnd, Cell empty, Library autoLib,
                           Rectangle2D box, boolean isPlanHorizontal,
                           List<Cell> newElems, Cell topCell, Area area)
    {
        double masterW = masterBnd.getWidth();
        double masterH = masterBnd.getHeight();
        double w = box.getWidth(), wHalf = w/2;
        double h = box.getHeight(), hHalf = h/2;
        double x = box.getX();
        double y = box.getY();
        double refineOnX = w/masterW;
        double refineOnY = h/masterH;
        // refineOnX and refineOnY must be multiple of masterW/masterH
        if (DBMath.hasRemainder(w, masterW) || DBMath.hasRemainder(h, masterH))
            System.out.println("not good refinement");

        if (config.job != null && config.job.checkAbort())
            return false;

        int cutX = (int)Math.ceil(refineOnX);
        int cutY = (int)Math.ceil(refineOnY);
        assert(cutX > 0 && cutY > 0);
        int cutXOnLeft = getFillDimension((int)(refineOnX/2), false);
        int cutXOnRight = cutX - cutXOnLeft; // get exact number of cuts
        int cutYOnBottom = getFillDimension((int)(refineOnY/2), false);
        int cutYOnTop = cutY - cutYOnBottom; // get exact number of cuts

        // Each cut will have a copy of the cell to use
        List<Cell> childrenList = new ArrayList<Cell>();
        boolean stdCell = true;
        List<Rectangle2D> boxes = new ArrayList<Rectangle2D>();
        CutType cut = CutType.NONE;
        Rectangle2D bb = null;
        List<String> namesList = new ArrayList<String>();

        if (cutX > 1 && cutY > 1) // refine on XY
        {
            cut = CutType.XY;
            double[] widths = null, heights = null;
            double[] centerX = null, centerY = null;

            if (config.binary)
            {
                widths = new double[]{wHalf, wHalf, wHalf, wHalf};
                heights = new double[]{hHalf, hHalf, hHalf, hHalf};
                centerX = new double[]{box.getCenterX(), box.getCenterX(), box.getCenterX(), box.getCenterX()};
                centerY = new double[]{box.getCenterY(), box.getCenterY(), box.getCenterY(), box.getCenterY()};
            }
            else
            {
                double tmpXLeft = cutXOnLeft*masterW, tmpYOnBottom = cutYOnBottom*masterH;
                widths = new double[]{tmpXLeft, cutXOnRight*masterW};
                heights = new double[]{tmpYOnBottom, cutYOnTop*masterH};
                centerX = new double[]{x, x+tmpXLeft};
                centerY = new double[]{y, y+tmpYOnBottom};
            }

            for (int i = 0; i < 4; i++)
            {
                int xPos = i%2;
                int yPos = i/2;
                bb = GenMath.getQTreeBox(x, y, widths[xPos], heights[yPos], centerX[xPos], centerY[yPos], i);
                boxes.add(bb);
                if (!refine(masters, masterBnd, empty, autoLib, bb, isPlanHorizontal, childrenList,
                        topCell, area))
                    stdCell = false;
            }
        }
        else if (cutX > 1) // only on X
        {
            cut = CutType.X;
            double[] widths = null, centerX = null;

            if (config.binary)
            {
                widths = new double[]{wHalf, wHalf};
                centerX = new double[]{box.getCenterX(), box.getCenterX()};
            }
            else
            {
                double tmpXLeft = cutXOnLeft*masterW;
                widths = new double[]{tmpXLeft, cutXOnRight*masterW};
                centerX = new double[]{x, x+tmpXLeft};
            }

            for (int i = 0; i < 2; i++)
            {
                bb = GenMath.getQTreeBox(x, y, widths[i], h, centerX[i], box.getCenterY(), i);
                boxes.add(bb);
                if (!refine(masters, masterBnd, empty, autoLib, bb, isPlanHorizontal, childrenList,
                        topCell, area))
                    stdCell = false;
            }
        }
        else if (cutY > 1) // only on Y
        {
            cut = CutType.Y;
            double[] heights = null, centerY = null;

            if (config.binary)
            {
                heights = new double[]{hHalf, hHalf};
                centerY = new double[]{box.getCenterY(), box.getCenterY()};
            }
            else
            {
                double tmpYOnBottom = cutYOnBottom*masterH;
                heights = new double[]{tmpYOnBottom, cutYOnTop*masterH};
                centerY = new double[]{y, y+tmpYOnBottom};
            }
            for (int i = 0; i < 2; i++)
            {
                bb = GenMath.getQTreeBox(x, y, w, heights[i], box.getCenterX(), centerY[i], i*2);
                boxes.add(bb);
                if (!refine(masters, masterBnd, empty, autoLib, bb, isPlanHorizontal, childrenList,
                        topCell, area))
                    stdCell = false;
            }
        } else {

            if (config.job != null && config.job.checkAbort())
                return false;

            // nothing refined, qTree leave
            HashSet<NodeInst> nodesToRemove = new HashSet<NodeInst>();
            HashSet<ArcInst> arcsToRemove = new HashSet<ArcInst>();

            // test all possible masters starting from the one in index=0
            boolean excluded = false;
            Cell theMasterCell = null, bestFitCell = null;
            int bestFitNum = Integer.MAX_VALUE;

            for (Cell master : masters)
            {
                // Translation from master cell center to actual location in the qTree
                /*		[   1    0    tx  ]
                 *		[   0    1    ty  ]
                 *		[   0    0    1   ]
                 */
                Rectangle2D box1 = new Rectangle2D.Double(box.getMinX()+config.gap, box.getMinY()+config.gap,
                        box.getWidth()-config.gap*2, box.getHeight()-config.gap*2);
                Rectangle2D essentialBnd = master.findEssentialBounds();
                if (essentialBnd == null) essentialBnd = master.getBounds();
                Rectangle2D masterRealBnd = master.getBounds();
                FixpTransform fillTransUp = FixpTransform.getTranslateInstance(
                        box.getCenterX() - masterRealBnd.getCenterX(),
                        box.getCenterY() - masterRealBnd.getCenterY());
//                boolean isExcluded1 = area.intersects(box);
                boolean isExcluded = area.intersects(box1);

//                if (box.getMinX() > -5680 && box.getMinY() > 3680) //&& box.getMinY() > 2784)
//                    System.out.println("Here ");

    //            if (isExcluded != isExcluded1)
    //                System.out.println("HHH");
                // Testing if all points are completely inside
    //            boolean anotherTest = area.contains(box1.getMinX(), box1.getMinY());
    //            if (!anotherTest) anotherTest = area.contains(box1.getMinX(), box1.getMaxY());
    //            if (!anotherTest) anotherTest = area.contains(box1.getMaxX(), box1.getMaxY());
    //            if (!anotherTest) anotherTest = area.contains(box1.getMaxX(), box1.getMinY());
    //            if (isExcluded != anotherTest)
    //                System.out.println("HHH dddd");
                Cell c = null;

                if (isExcluded)
                {

                    theMasterCell = empty;  // the best is the empty cell as the area is excluded
                    excluded = true;
                    break;
                }
                else
                {
                    c = FillCellGenJob.detectOverlappingBars(master, master, empty,
                            fillTransUp, nodesToRemove, arcsToRemove,
                            topCell, new NodeInst[] {}, config.drcSpacingRule, 0);
                    if (c != empty && c != null)
                    {
                        theMasterCell = c;
                        break; // perfect fit
                    }
                    else
                    {
                        // keeping the best option
                        int numToRemove = arcsToRemove.size() + nodesToRemove.size();
                        if (c != empty && numToRemove < bestFitNum)
                        {
                            bestFitCell = master;
                            bestFitNum = numToRemove;
                        }
                    }
                }
            }

            stdCell = true;

            if (theMasterCell == null)
            {
                assert(!excluded);
                boolean isEmptyCell = excluded || bestFitCell == null || arcsToRemove.size() == bestFitCell.getNumArcs();
                Cell dummyCell = null;

                if (isEmptyCell)
                    dummyCell = empty;
                else
                {
                    // Replace by dummy cell for now
                    String dummyName = "dummy";
                    // Collect names from nodes and arcs to remove if the cell is not empty
                    namesList.clear();
                    for (NodeInst ni : nodesToRemove)
                    {
                        // Not deleting the pins otherwise cells can be connected.
                        // List should not contain pins
                        if (!ni.getProto().getFunction().isPin())
                            namesList.add(ni.getName());
                    }
//                    for (ArcInst ai : arcsToRemove)
//                    {
//                        namesList.add(ai.getName());
//                    }
                    Collections.sort(namesList);
                    int code = namesList.toString().hashCode();

                    dummyName +=code+"{lay}";
                    // Place dummy cells in same location as the empty cells. It should be in "autoFillLib"
                    dummyCell = empty.getLibrary().findNodeProto(dummyName);// master.getLibrary().findNodeProto(dummyName);

                    if (dummyCell == null)
                    {
                        // Creating empty/dummy Master cell or look for an existing one
                        dummyCell = Cell.copyNodeProto(bestFitCell, bestFitCell.getLibrary(), dummyName, true);
//                        LayoutLib.newNodeInst(Tech.essentialBounds, -masterW/2, -masterH/2,
//                                              G.DEF_SIZE, G.DEF_SIZE, 180, dummyCell);
//                        LayoutLib.newNodeInst(Tech.essentialBounds, masterW/2, masterW/2,
//                                              G.DEF_SIZE, G.DEF_SIZE, 0, dummyCell);

                        // Time to delete the elements overlapping with the rest of the top cell
                        for (NodeInst ni : nodesToRemove)
                        {
                            NodeInst d = dummyCell.findNode(ni.getName());
                            if (d != null)
                                d.kill();
                        }

                        for (ArcInst ai : arcsToRemove)
                        {
                            ArcInst d = dummyCell.findArc(ai.getName());
                            // d is null if a NodeInst connected was killed.
                            if (d != null)
                                d.kill();
                        }
                        // if cell is now empty
                        if (dummyCell.getNumArcs() == 0) // now is empty
                        {
                            dummyCell.kill();
                            dummyCell = empty;
                            isEmptyCell = true;
                        }
                    }
                }
                newElems.add(dummyCell);
                stdCell = isEmptyCell;
            }
            else
                newElems.add(theMasterCell);
            return stdCell;
        }



        if (config.job != null && config.job.checkAbort())
            return false;

        Cell tiledCell = null;
        String tileName = null;
        boolean stdC = true;
        boolean readStdC = true;
        assert(childrenList.size() > 1);
        Cell template = childrenList.get(0);
        for (int i = 1; i < childrenList.size(); i++)
        {
            if (template != childrenList.get(i))
            {
                stdC = false;
                readStdC = false;
                break;
            }
        }

//        if (readStdC != stdCell)
//            System.out.println("They are not std");

        stdCell = readStdC;

        // Search by names
        if (!stdCell)
        {
            stdCell = stdC;
        }

        stdC = stdCell;

        if (stdCell)
        {
            String rootName = childrenList.get(0).getName();
            if (childrenList.get(0) == masters.get(0))
                rootName = masters.get(0).getName();
            else if (childrenList.get(0) == empty)
                rootName = empty.getName();
            tileName = rootName+"_"+w+"x"+h+"{lay}";
            tiledCell = empty.getLibrary().findNodeProto(tileName);
        }
        else
        {
            // Better to name based on children found
            namesList.clear();
            for (Cell c : childrenList)
            {
                namesList.add(c.getName());
            }
            namesList.add(cut.toString());
            // you can't sort names otherwise you look order in the qtree
//            Collections.sort(namesList);
            int code = namesList.toString().hashCode();
//            tileName = "dummy"+master.getName()+"_"+w+"x"+h+"("+x+","+y+"){lay}";
            tileName = "dummy"+masters.get(0).getName()+"_"+w+"x"+h+"("+code+"){lay}";
            tiledCell = empty.getLibrary().findNodeProto(tileName);
            if  (tiledCell != null)
            {
                stdC = true;
            }
        }

        if (tiledCell == null || !stdC)
        {
            tiledCell = Cell.newInstance(empty.getLibrary(), tileName);
            TechType techType = config.getTechType();
            LayoutLib.newNodeInst(techType.essentialBounds(), ep, -w/2, -h/2,
                                  G.DEF_SIZE, G.DEF_SIZE, 180, tiledCell);
            LayoutLib.newNodeInst(techType.essentialBounds(), ep, w/2, h/2,
                                  G.DEF_SIZE, G.DEF_SIZE, 0, tiledCell);

            NodeInst[][] rows = null;

            // if it could use previous std fill cells
            switch (cut)
            {
                case X:
                    rows = new NodeInst[1][2];
                    break;
                case Y:
                    rows = new NodeInst[2][1];
                    break;
                case XY:
                    rows = new NodeInst[2][2];
                    break;
            }
            assert(boxes.size() == childrenList.size());

            for (int i = 0; i < boxes.size(); i++)
            {
                Rectangle2D b = boxes.get(i);
                double cenX = b.getCenterX() - box.getCenterX();
                double cenY = b.getCenterY() - box.getCenterY();
                NodeInst node = LayoutLib.newNodeInst(childrenList.get(i), ep, cenX, cenY,
                        b.getWidth(), b.getHeight(), 0, tiledCell);
                switch (cut)
                {
                    case X:
                        rows[0][i] = node;
                        break;
                    case Y:
                        rows[i][0] = node;
                        break;
                    case XY:
                        rows[(int)i/2][i%2] = node;
                }
            }

            connectAllPortInsts(config.getTechType(), ep, tiledCell);
            exportUnconnectedPortInsts(rows, isPlanHorizontal, tiledCell);
        }

        newElems.add(tiledCell);
        return stdCell;
    }

    /**
     *  Method to set up conditions for qTree refinement
     */
    public Cell makeQTreeCell(List<Cell> masters, Cell empty, Library autoLib,
                              int tileOnX, int tileOnY, boolean isPlanHorizontal,
                              Cell topCell,
                              List<Rectangle2D> topBoxList, Area area)
    {
        double fillWidth = tileOnX * config.minTileSizeX;
        double fillHeight = tileOnY * config.minTileSizeY;
        // in case of binary division, make sure the division is 2^N
        if (config.binary)
        {
            int powerX = getFillDimension(tileOnX, true);
            int powerY = getFillDimension(tileOnY, true);
            fillWidth = powerX*config.minTileSizeX;
            fillHeight = powerY*config.minTileSizeY;
        }

        // topBox its width/height must be multiply of master.getWidth()/Height()
        Rectangle2D topEssential = topCell.findEssentialBounds();
//        Rectangle2D topBox = new Rectangle2D.Double(topCell.getBounds().getCenterX()-fillWidth/2,
//                topCell.getBounds().getCenterY()-fillHeight/2, fillWidth, fillHeight);
        // Center bounding box using the left-bottom corner
        Rectangle2D topBox = new Rectangle2D.Double(topCell.getBounds().getX() + config.gap,
                topCell.getBounds().getY() + config.gap, fillWidth, fillHeight);
        if (topEssential != null) // make correction
        {
            topBox = new Rectangle2D.Double(topEssential.getX(), topEssential.getY(), fillWidth, fillHeight);
        }

        topBoxList.clear(); // remove original value
        topBoxList.add(topBox); // I need information for setting

        // refine recursively
        List<Cell> newElems = new ArrayList<Cell>();
        Rectangle2D essentialBnd = masters.get(0).findEssentialBounds();
        if (essentialBnd == null)
            essentialBnd = masters.get(0).getBounds();
        refine(masters, essentialBnd, empty, autoLib, topBox, isPlanHorizontal, newElems,
                topCell, area);
        assert(newElems.size()==1);
        return newElems.iterator().next();
    }
}
