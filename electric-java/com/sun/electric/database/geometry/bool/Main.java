/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Main.java
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

import com.sun.electric.Main.UserInterfaceDummy;
import com.sun.electric.database.CellRevision;
import com.sun.electric.database.CellTree;
import com.sun.electric.database.EditingPreferences;
import com.sun.electric.database.ImmutableNodeInst;
import com.sun.electric.database.geometry.PolyBase;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.EDatabase;
import com.sun.electric.database.hierarchy.Library;
import com.sun.electric.database.id.CellId;
import com.sun.electric.database.id.CellId;
import com.sun.electric.database.id.IdManager;
import com.sun.electric.database.text.Pref;
import com.sun.electric.database.variable.TextDescriptor;
import com.sun.electric.technology.Layer;
import com.sun.electric.technology.TechPool;
import com.sun.electric.technology.Technology;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.Tool;
import com.sun.electric.tool.io.FileType;
import com.sun.electric.tool.io.input.LibraryFiles;
import com.sun.electric.util.TextUtils;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 *
 */
public class Main
{
    static void initElectric()
    {
        TextDescriptor.cacheSize();
        Tool.initAllTools();
        Pref.lockCreation();

        EDatabase database = new EDatabase(IdManager.stdIdManager.getInitialSnapshot(), "serverDB");
        Job.setUserInterface(new UserInterfaceDummy()
        {
            @Override
            public EDatabase getDatabase()
            {
                return EDatabase.serverDatabase();
            }

            @Override
            public Cell getCurrentCell()
            {
                return null;
            }
        });
        EDatabase.setServerDatabase(database);
        database.lock(true);
        Technology.initPreinstalledTechnologies(database, Technology.getParamValuesByXmlPath());
    }

    static Library loadLibrary(String libPath)
    {
        EDatabase database = EDatabase.serverDatabase();
        database.lowLevelBeginChanging(null);
        try
        {
            URL libUrl = TextUtils.makeURLToFile(libPath);
            String libName = TextUtils.getFileNameWithoutExtension(libUrl);
            FileType fileType = libPath.endsWith(".delib") ? FileType.DELIB : FileType.JELIB;
            EditingPreferences ep = new EditingPreferences(true, TechPool.getThreadTechPool());
            return LibraryFiles.readLibrary(ep, libUrl, libName, fileType, true);
        } finally
        {
            database.backup();
            database.lowLevelEndChanging();
        }
    }

    static Cell loadCell(String libFile, String cellName)
    {
        return loadLibrary(libFile).findNodeProto(cellName);
    }

    static interface CellTreeFun
    {
        int apply(CellTree cellTree);
    }

    static interface CellRevisionFun
    {
        int apply(CellRevision cellRevision);
    }

    static interface PolyBaseFun
    {
        int apply(PolyBase polyBase);
    }

    static int countHier(CellTree top, CellTreeFun localCount)
    {
        int sum = 0;
        for (CellTree t : downTop(top))
        {
            sum += localCount.apply(t);
        }
        return sum;
    }

    static Map<CellId, Integer> countFlat(CellTree top, CellTreeFun localCount)
    {
        Collection<CellTree> cells = downTop(top);
        Map<CellId, Integer> result = new LinkedHashMap<CellId, Integer>();
        for (CellTree t : cells)
        {
            List<ImmutableNodeInst> nodes = new ArrayList<ImmutableNodeInst>();
            for (ImmutableNodeInst n : t.top.cellRevision.nodes)
            {
                if (n.protoId instanceof CellId)
                {
                    nodes.add(n);
                }
            }
            int c = localCount.apply(t);
            for (ImmutableNodeInst n : nodes)
            {
                c += result.get((CellId)n.protoId);
            }
            result.put(t.top.cellRevision.d.cellId, c);
        }
        return result;
    }

    static Collection<CellTree> downTop(CellTree top)
    {
        Collection<CellTree> result = new LinkedHashSet<CellTree>();
        downTop(top, result);
        return result;
    }

    private static void downTop(CellTree t, Collection<CellTree> result)
    {
        if (!result.contains(t))
        {
            for (CellTree subTree : t.getSubTrees())
            {
                downTop(subTree, result);
            }
            result.add(t);
        }
    }

    static Iterable<PolyBase.PolyBaseTree> byteArray2tree(byte[] ba) throws IOException
    {
        DataInputStream inpS = new DataInputStream(new ByteArrayInputStream(ba));
        UnloadPolys up = new UnloadPolys();
        Iterable<PolyBase.PolyBaseTree> trees = up.loop(inpS, false);
        inpS.close();
        return trees;
    }

    static int treesSize(Iterable<PolyBase.PolyBaseTree> ts, PolyBaseFun localCount)
    {
        int sum = 0;
        for (PolyBase.PolyBaseTree t : ts)
        {
            sum += treeSize(t, localCount);
        }
        return sum;
    }

    static int treeSize(PolyBase.PolyBaseTree t, PolyBaseFun localCount)
    {
        Iterable<PolyBase.PolyBaseTree> l = t.getSons();
        int sonCount = treesSize(l, localCount);
        return localCount.apply(t.getPoly()) + sonCount;
    }

    static void hugeFile() throws IOException
    {
        File file = File.createTempFile("Electric", "DRC", new File("."));
        file.deleteOnExit();
        FileOutputStream out = new FileOutputStream(file);
        byte[] b = new byte[1 << 20];
        for (int i = 0; i < 8000; i++)
        {
            out.write(b);
        }
        out.close();
        file.delete();
    }

    static int countHier(Cell topCell, final CellRevisionFun localCount)
    {
        return countHier(topCell.tree(), new CellTreeFun()
        {
            @Override
            public int apply(CellTree t)
            {
                return localCount.apply(t.top.cellRevision);
            }
        });
    }

    static int countFlat(Cell topCell, final CellRevisionFun localCount)
    {
        return countFlat(topCell.tree(), new CellTreeFun()
        {
            @Override
            public int apply(CellTree t)
            {
                return localCount.apply(t.top.cellRevision);
            }
        }).get(topCell.getId());
    }

    public static void main(String[] args) throws IOException
    {
//    hugeFile();
        initElectric();

        String libPath = args[0];
        String topCellName = args[1];
        Cell topCell = loadCell(libPath, topCellName);
        LayoutMergerHierImpl layoutMerger = new LayoutMergerHierImpl(topCell);

        Collection<CellTree> dt = downTop(topCell.tree());
        assert (dt.equals(layoutMerger.downTop(topCell.tree())));
        System.out.println("downTop " + dt.size());
        for (CellTree t : dt)
        {
            System.out.println(t);
        }
        final VectorCache vectorCache = layoutMerger.vectorCache;
        System.out.println(countHier(topCell, new CellRevisionFun()
        {
            @Override
            public int apply(CellRevision r)
            {
                return 1;
            }
        }) + " cells");
        System.out.println(countHier(topCell, new CellRevisionFun()
        {
            @Override
            public int apply(CellRevision r)
            {
                return vectorCache.getSubcells(r.d.cellId).size();
            }
        }) + " subCells");
        System.out.println(countHier(topCell, new CellRevisionFun()
        {
            @Override
            public int apply(CellRevision r)
            {
                return r.nodes.size();
            }
        }) + " nodes");
        System.out.println(countHier(topCell, new CellRevisionFun()
        {
            @Override
            public int apply(CellRevision r)
            {
                return r.arcs.size();
            }
        }) + " arcs");
        System.out.println(countHier(topCell, new CellRevisionFun()
        {
            @Override
            public int apply(CellRevision r)
            {
                return r.exports.size();
            }
        }) + " exports");

        System.out.println(countFlat(topCell, new CellRevisionFun()
        {
            @Override
            public int apply(CellRevision r)
            {
                return 1;
            }
        }) + " cell insts");
        System.out.println(countFlat(topCell, new CellRevisionFun()
        {
            @Override
            public int apply(CellRevision r)
            {
                return vectorCache.getSubcells(r.d.cellId).size();
            }
        }) + " subCells");
        System.out.println(countFlat(topCell, new CellRevisionFun()
        {
            @Override
            public int apply(CellRevision r)
            {
                return r.nodes.size();

            }
        }) + " node insts");
        System.out.println(countFlat(topCell, new CellRevisionFun()
        {
            @Override
            public int apply(CellRevision r)
            {
                return r.arcs.size();
            }
        }) + " arc insts");
        System.out.println(countFlat(topCell, new CellRevisionFun()
        {
            @Override
            public int apply(CellRevision r)
            {
                return r.exports.size();
            }
        }) + " export insts");

        vectorCache.scanLayers(topCell.getId());
        Collection<Layer> layers = vectorCache.getLayers();
        for (final Layer layer : layers)
        {
            System.out.println();
            System.out.println(layer);
            System.out.println(countHier(topCell, new CellRevisionFun()
            {
                @Override
                public int apply(CellRevision r)
                {
                    return vectorCache.getNumBoxes(r.d.cellId, layer) * 4;
                }
            }) + " points");
            System.out.println(countFlat(topCell, new CellRevisionFun()
            {
                @Override
                public int apply(CellRevision r)
                {
                    return vectorCache.getNumBoxes(r.d.cellId, layer)
                        * 4;
                }
            }) + " point insts");
            final Map<CellId, Iterable<PolyBase.PolyBaseTree>> mergedTrees = new LinkedHashMap<CellId, Iterable<PolyBase.PolyBaseTree>>();
            Map<CellId, int[]> mergedCoords = new LinkedHashMap<CellId, int[]>();
            for (CellTree t : dt)
            {
                CellId cellId = t.top.cellRevision.d.cellId;
                byte[] ba = layoutMerger.mergeLocalLayerToByteArray(cellId, layer);
                if (ba != null)
                {
                    mergedTrees.put(cellId, byteArray2tree(ba));
                    mergedCoords.put(cellId, layoutMerger.byteArray2coordArray(ba));
                } else
                {
                    mergedTrees.put(cellId, Collections.<PolyBase.PolyBaseTree>emptyList());
                    mergedCoords.put(cellId, new int[]
                        {
                        });
                }
            }
            System.out.println(countHier(topCell, new CellRevisionFun()
            {
                @Override
                public int apply(CellRevision r)
                {
                    return treesSize(mergedTrees.get(r.d.cellId), new PolyBaseFun()
                    {
                        @Override
                        public int apply(PolyBase p)
                        {
                            return 1;
                        }
                    });
                }
            }) + " merged polygons");
            System.out.println(countFlat(topCell, new CellRevisionFun()
            {
                @Override
                public int apply(CellRevision r)
                {
                    return treesSize(mergedTrees.get(r.d.cellId), new PolyBaseFun()
                    {
                        @Override
                        public int apply(PolyBase p)
                        {
                            return 1;
                        }
                    });
                }
            }) + " merged polygon insts");
            System.out.println(countHier(topCell, new CellRevisionFun()
            {
                @Override
                public int apply(CellRevision r)
                {

                    return treesSize(mergedTrees.get(r.d.cellId), new PolyBaseFun()
                    {
                        @Override
                        public int apply(PolyBase p)
                        {
                            return p.getPoints().length;
                        }
                    });
                }
            }) + " merged points");
            System.out.println(countFlat(topCell, new CellRevisionFun()
            {
                public int apply(CellRevision r)
                {
                    return treesSize(mergedTrees.get(r.d.cellId), new PolyBaseFun()
                    {
                        public int apply(PolyBase p)
                        {
                            return p.getPoints().length;
                        }
                    });
                }
            }) + " merged point insts");

            boolean rotate = false;
            String fileName = layer.getName() + ".dm";
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(fileName)));
            out.writeBoolean(rotate);
            layoutMerger.mergeLayer(mergedCoords, topCell.getId(), layer, rotate, out);
            out.close();
            DataInputStream inpS = new DataInputStream(new BufferedInputStream(new FileInputStream(fileName)));
            assert inpS.readBoolean() == rotate;
            UnloadPolys up = new UnloadPolys();
            Iterable<PolyBase.PolyBaseTree> trees = up.loop(inpS, false);
            System.out.println(treesSize(trees, new PolyBaseFun()
            {
                @Override
                public int apply(PolyBase p)
                {
                    return 1;
                }
            }) + " merged polygons");
            System.out.println(treesSize(trees, new PolyBaseFun()
            {
                @Override
                public int apply(PolyBase p)
                {
                    return p.getPoints().length;
                }
            }) + " merged polygons");
            inpS.close();
        }
    }
}
