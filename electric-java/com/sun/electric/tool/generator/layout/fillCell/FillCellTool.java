/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: FillCellTool.java
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
import com.sun.electric.tool.generator.layout.fill.ExportConfig;
import com.sun.electric.tool.generator.layout.fill.FillGeneratorTool;
import com.sun.electric.tool.generator.layout.fill.FillGenConfig;
import com.sun.electric.tool.generator.layout.fill.G;
import com.sun.electric.tool.generator.layout.LayoutLib;
import com.sun.electric.tool.generator.layout.TechType;
import com.sun.electric.tool.Job;
import com.sun.electric.database.text.Pref;
import com.sun.electric.database.hierarchy.Cell;

import java.util.List;
import java.util.ArrayList;
import java.awt.geom.Rectangle2D;
import java.awt.geom.Area;

/**
 * User: gg151869
 * Date: Sep 15, 2006
 */
public class FillCellTool extends FillGeneratorTool {

    public FillCellTool()
    {
        super();
    }

    /****************************** PREFERENCES ******************************/

    public enum FillCellMode
    {
        NONE(-1),
        FLAT(0),
        BINARY(1),
        ADAPTIVE(2);
        private final int mode;
        FillCellMode(int m) { mode = m; }
        static FillCellMode find(int mode)
        {
            for (FillCellMode m : FillCellMode.values())
            {
                if (m.mode == mode) return m;
            }
            return NONE;
        }
    }

    private static Pref cacheFillCellMode;
    /**
     * Method to retrieve how a given cell must filled.
     * The default is FLAT.
     * @return value representing the algorithm to use for filling a given cell.
     */
    public static FillCellTool.FillCellMode getFillCellMode()
    {
        if (cacheFillCellMode == null)
        {
            cacheFillCellMode = Pref.makeIntPref("FillCellMode", getTool().prefs, FillCellTool.FillCellMode.FLAT.mode);
        }
        return FillCellTool.FillCellMode.find(cacheFillCellMode.getInt());
    }
    /**
     * Method to set mode how a given cell must filled.
     * @param mode value representing the algorithm to use for filling a given cell.
     */
    public static void setFillCellMode(FillCellTool.FillCellMode mode) { cacheFillCellMode.setInt(mode.mode); }

    private static Pref cacheFillRouterMode;
    /**
     * Method to retrieve mode to select router to fill the given cell.
     * The default is FULL.
     * @return value representing the routing algorithm to use for connecting a given cell.
     */
    public static FillGenConfig.FillGenType getFillRouterMode()
    {
        if (cacheFillRouterMode == null)
        {
            cacheFillRouterMode = Pref.makeIntPref("FillRouterMode",
                    getTool().prefs,FillGenConfig.FillGenType.INTERNAL.getMode());
        }
        return FillGenConfig.FillGenType.find(cacheFillRouterMode.getInt());
    }
    /**
     * Method to set mode to select router to fill the given cell.
     * @param mode value representing the routing algorithm to use for connecting a given cell.
     */
    public static void setFillRouterMode(FillGenConfig.FillGenType mode) { cacheFillRouterMode.setInt(mode.getMode()); }

    private static Pref cacheFillCellCreateMaster;
    /**
     * Method to tell whether FillGeneratorTool will generate a master cell or use a given one.
     * The default is "true".
     * @return true if FillGeneratorTool should generate a master cell instead of use a given one.
     */
    public static boolean isFillCellCreateMasterOn()
    {
        if (cacheFillCellCreateMaster == null)
        {
           cacheFillCellCreateMaster = Pref.makeBooleanPref("FillCellCreateMaster", getTool().prefs, true);
        }
        return cacheFillCellCreateMaster.getBoolean();
    }
    /**
     * Method to set whether FillGeneratorTool will generate a master cell or use a given one.
     * @param on true if FillGeneratorTool should generate a master cell instead of use a given one.
     */
    public static void setFillCellCreateMasterOn(boolean on) { cacheFillCellCreateMaster.setBoolean(on); }

    private Cell treeMakeAndTileCell(TechType tech, EditingPreferences ep, List<Cell> masters, boolean isPlanHorizontal, 
    		Cell topCell, List<Rectangle2D> topBoxList, Area area)
    {
        // Create an empty cell for cases where all nodes/arcs are moved due to collision
        Cell empty = Cell.newInstance(lib, "empty"+masters.get(0).getName()+"{lay}");
        empty.setTechnology(topCell.getTechnology());
        double cellWidth = masters.get(0).getBounds().getWidth();
        double cellHeight = masters.get(0).getBounds().getHeight();
        LayoutLib.newNodeInst(tech.essentialBounds(), ep,
                              -cellWidth/2, -cellHeight/2,
                              G.DEF_SIZE, G.DEF_SIZE, 180, empty);
        LayoutLib.newNodeInst(tech.essentialBounds(), ep,
                              cellWidth/2, cellHeight/2,
                              G.DEF_SIZE, G.DEF_SIZE, 0, empty);

        int tileOnX = (int)Math.ceil(config.targetW/config.minTileSizeX);
        int tileOnY = (int)Math.ceil(config.targetH/config.minTileSizeY);

        TreeTiledCell t = new TreeTiledCell(config, ep);
        Cell topFill = t.makeQTreeCell(masters, empty, lib, tileOnX, tileOnY, isPlanHorizontal, 
                topCell, topBoxList, area);
        topFill.setTechnology(topCell.getTechnology());
        return topFill;
    }

    /** Similar to standardMakeFillCell but it generates hierarchical fills with a qTree
     * @return Top fill cell
     */
    protected Cell treeMakeFillCell(FillGenConfig config, EditingPreferences ep, Cell topCell,
                                    List<Cell> givenMasters, List<Rectangle2D> topBoxList, Area area)
    {
        boolean metalFlex = true;
        int loLayer = config.firstLayer;
        int hiLayer = config.lastLayer;
        ExportConfig exportConfig = config.perim;
        initFillParameters(metalFlex, true, ep);
        TechType tech = config.getTechType();

        masters = givenMasters;

        if (masters == null)
        {
            Job.error(loLayer<1, "loLayer must be >=1");
            int maxNumMetals = tech.getNumMetals();
            Job.error(hiLayer>maxNumMetals, "hiLayer must be <=" + maxNumMetals);
            Job.error(loLayer>hiLayer, "loLayer must be <= hiLayer");
            masters = new ArrayList<Cell>();
            Cell master = makeFillCell(lib, plans, loLayer, hiLayer, capCell, 
            		                   tech, ep, exportConfig,
            		                   metalFlex, true);
            masters.add(master);
        }
        else
        {
            // must adjust minSize
            Rectangle2D r = masters.get(0).findEssentialBounds(); // must use essential elements to match the edges
            if (r == null)
                r = masters.get(0).getBounds();
            config.minTileSizeX = r.getWidth();
            config.minTileSizeY = r.getHeight();
        }
        Cell cell = treeMakeAndTileCell(tech, ep, masters, getOrientation(), topCell, topBoxList, area);

        return cell;
    }

}
