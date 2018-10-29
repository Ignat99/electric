/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: MinArea.java
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
package com.sun.electric.tool.drc;

import com.sun.electric.api.minarea.LayoutCell;
import com.sun.electric.api.minarea.ManhattanOrientation;
import com.sun.electric.api.minarea.MinAreaChecker;
import com.sun.electric.api.minarea.launcher.DefaultLayoutCell;
import com.sun.electric.database.EditingPreferences;
import com.sun.electric.database.ImmutableNodeInst;
import com.sun.electric.database.geometry.EPoint;
import com.sun.electric.database.geometry.bool.VectorCache;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.Library;
import com.sun.electric.database.id.CellId;
import com.sun.electric.database.id.PrimitiveNodeId;
import com.sun.electric.database.text.Name;
import com.sun.electric.database.variable.TextDescriptor;
import com.sun.electric.technology.DRCTemplate;
import com.sun.electric.technology.Layer;
import com.sun.electric.technology.Technology;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.io.FileType;
import com.sun.electric.tool.user.ErrorLogger;
import com.sun.electric.tool.user.User;
import com.sun.electric.tool.user.dialogs.OpenFile;
import com.sun.electric.util.TextUtils;
import com.sun.electric.util.math.DBMath;
import com.sun.electric.util.math.Orientation;
import java.awt.Shape;
import java.awt.geom.PathIterator;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Properties;

/**
 *
 */
public class MinArea {
    // ---- minarea API -------------

    public static void writeMinareaLay() {
        Cell topCell = Job.getUserInterface().needCurrentCell();
        String filePrefix = "";

//        scale = 40;
//        filePrefix = "ser/m_";

//        scale = 4;
//        filePrefix = "ser/q3_";

//        scale = 4;
//        filePrefix = "ser/mips_";

//        scale = 200;
//        filePrefix = "ser/q4p2_";

//        scale = 200;
//        filePrefix = "ser/xnp_";

//        scale = 200;
//        filePrefix = "ser/xnpt_";
        new ConvertCellToVectorCacheJob(topCell, filePrefix).startJob();
    }
    // to write down lay files

    private static class ConvertCellToVectorCacheJob extends Job {

        private Cell topCell;
        private int scale;
        private String filePrefix;
        private transient VectorCache vce;
        private long divisor;
        private int[] result = new int[4];
        private Map<Orientation, ManhattanOrientation> ors = new IdentityHashMap<Orientation, ManhattanOrientation>();

        protected ConvertCellToVectorCacheJob(Cell topCell, String filePrefix) {
            super("try vector cache", User.getUserTool(), Job.Type.CHANGE, null, null, Job.Priority.USER);
            this.topCell = topCell;
            this.filePrefix = filePrefix;
            initOrientations();
        }

        @Override
        public boolean doIt() {
            vce = new VectorCache(getDatabase().backup());
            CellId topCellId = topCell.getId();
            vce.scanLayers(topCellId);
            divisor = 0;
            System.out.println(countInsts(topCellId, new IdentityHashMap<CellId, Long>()) + "  insts");
//            System.out.println("divisor="+divisor);
            for (Layer layer : vce.getLayers()) {
                System.out.print(layer.getFullName());
                if (vce.isBadLayer(layer)) {
                    System.out.println("  NONMANHATTAN  !");
                } else {
                    System.out.println("  " + countLayer(topCell.getId(), layer, new IdentityHashMap<CellId, Long>()) + " rects");
                }
//                System.out.println("divisor="+divisor);
            }
            scale = (int) divisor;
            Map<Layer, String> msgs = new HashMap<Layer, String>();
            for (Layer layer : vce.getLayers()) {
//                if (layer.getFunction().isContact()) {
//                // via*, polyCut, activeCut)
//                    continue;
//                }
                if (vce.isBadLayer(layer)) {
                    continue;
                }
//                System.out.println(layer.getFullName());
                LayoutCell lTop = makeLayoutCell(topCell.getId(), layer, new IdentityHashMap<CellId, LayoutCell>());
                String fileName = filePrefix + layer.getName() + ".lay";
                try {
                    ObjectOutputStream os = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(fileName)));
                    os.writeObject(lTop);
                    os.close();
                    msgs.put(layer, "Exported layer '" + layer.getName() + "' in filename '" + fileName);
                } catch (IOException e) {
                    e.printStackTrace();
                    msgs.put(layer, e.getMessage());
                }
            }
            System.out.println("Layers summary");
            for (Layer layer : vce.getLayers()) {
                System.out.print(layer.getFullName());
                if (vce.isBadLayer(layer)) {
                    System.out.println("  NONMANHATTAN  !");
                } else {
                    System.out.println("  " + msgs.get(layer));
                }
            }
            System.out.println("divisor=" + divisor);
            return true;
        }

        private long countInsts(CellId cellId, Map<CellId, Long> counted) {
            Long cachedCount = counted.get(cellId);
            if (cachedCount != null) {
                return cachedCount.longValue();
            }
            long count = 1;
            for (ImmutableNodeInst n : vce.getSubcells(cellId)) {
                count += countInsts((CellId) n.protoId, counted);
                adjustDivisor(n.anchor.getGridX());
                adjustDivisor(n.anchor.getGridY());
            }
            counted.put(cellId, Long.valueOf(count));
            return count;
        }

        private long countLayer(CellId cellId, Layer layer, Map<CellId, Long> counted) {
            Long cachedCount = counted.get(cellId);
            if (cachedCount != null) {
                return cachedCount.longValue();
            }
            long count = vce.getNumBoxes(cellId, layer);
            for (int i = 0; i < count; i++) {
                vce.getBoxes(cellId, layer, i, 1, result);
                adjustDivisor(result[0]);
                adjustDivisor(result[1]);
                adjustDivisor(result[2]);
                adjustDivisor(result[3]);
            }
            for (ImmutableNodeInst n : vce.getSubcells(cellId)) {
                count += countLayer((CellId) n.protoId, layer, counted);
            }
            counted.put(cellId, Long.valueOf(count));
            return count;
        }

        private LayoutCell makeLayoutCell(CellId eTop, Layer layer, Map<CellId, LayoutCell> made) {
            LayoutCell lc = made.get(eTop);
            if (lc != null) {
                return lc;
            }
            for (ImmutableNodeInst n : vce.getSubcells(eTop)) {
                if (!(n.protoId instanceof CellId)) {
                    continue;
                }
                makeLayoutCell((CellId) n.protoId, layer, made);
            }
//            System.out.println(eTop);
            DefaultLayoutCell lTop = new DefaultLayoutCell(eTop.toString());
            made.put(eTop, lTop);
            lTop.setName(eTop.toString());
            int count = vce.getNumBoxes(eTop, layer);
            for (int i = 0; i < count; i++) {
                vce.getBoxes(eTop, layer, i, 1, result);
                lTop.addRectangle(result[0] / scale, result[1] / scale, result[2] / scale, result[3] / scale);
            }
            for (ImmutableNodeInst n : vce.getSubcells(eTop)) {
                CellId subCellId = (CellId) n.protoId;
                LayoutCell subCell = made.get(subCellId);
                if (subCell.getNumRectangles() == 0 && subCell.getNumSubcells() == 0) {
                    // skip empty subcells
                    continue;
                }
                int anchorX = (int) n.anchor.getGridX() / scale;
                int anchorY = (int) n.anchor.getGridY() / scale;
                ManhattanOrientation mor = ors.get(n.orient.canonic());
                assert mor != null;
                lTop.addSubCell(subCell, anchorX, anchorY, mor);
            }
            return lTop;
        }

        private void adjustDivisor(long v) {
            if (v == 0) {
                return;
            }
            if (v < 0) {
                v = -v;
            }
            if (divisor == 0) {
                divisor = v;
                return;
            }
            while (v % divisor != 0) {
                long t = v % divisor;
                v = divisor;
                divisor = t;
            }
        }

        private void initOrientations() {
            putOrs(ManhattanOrientation.R0, Orientation.IDENT);
            putOrs(ManhattanOrientation.R90, Orientation.R);
            putOrs(ManhattanOrientation.R180, Orientation.RR);
            putOrs(ManhattanOrientation.R270, Orientation.RRR);
            putOrs(ManhattanOrientation.MY, Orientation.YRR);
            putOrs(ManhattanOrientation.MYR90, Orientation.YR);
            putOrs(ManhattanOrientation.MX, Orientation.Y);
            putOrs(ManhattanOrientation.MXR90, Orientation.YRRR);
        }

        private void putOrs(ManhattanOrientation mor, Orientation eor) {
            assert eor.canonic() == eor;
            assert mor.affineTransform().equals(eor.pureRotate());
            ors.put(eor, mor);
        }
    }

    public static void checkMinareaLay() {
        Cell topCell = Job.getUserInterface().needCurrentCell();
        String jarPath = OpenFile.chooseInputFile(FileType.JAR, "Electric build", false, null, false, null);
        if (jarPath == null) return;

        new CheckMinAreaJob(jarPath, topCell).startJob();
    }
    // to write down lay files

    private static class CheckMinAreaJob extends Job {

        private String jarPath;
        private Cell topCell;
        private transient VectorCache vce;
        private int[] result = new int[4];
        private Map<Orientation, ManhattanOrientation> ors = new IdentityHashMap<Orientation, ManhattanOrientation>();

        protected CheckMinAreaJob(String jarPath, Cell topCell) {
            super("try vector cache", User.getUserTool(), Job.Type.CHANGE, null, null, Job.Priority.USER);
            this.jarPath = jarPath;
            this.topCell = topCell;
            initOrientations();
        }

        @Override
        public boolean doIt() {
            URL url = TextUtils.makeURLToFile(jarPath);
            URLClassLoader classLoader = new URLClassLoader(new URL[]{url}, ClassLoader.getSystemClassLoader());
            String[] knownImplementations = {
                "com.sun.electric.plugins.minarea.deltamerge0.SimpleChecker",
                "com.sun.electric.plugins.minarea.deltamerge1.SimpleChecker",
                "com.sun.electric.plugins.minarea.bitmapjava.BitMapMinAreaChecker",
                "com.sun.electric.plugins.minarea.bitmapscala.BitMapMinAreaChecker",
                "com.sun.electric.plugins.minarea.parallelbitmapscala.BitMapMinAreaChecker"
            };
            MinAreaChecker engine = null;
            for (String impl : knownImplementations) {
                try {
                    Class cls = classLoader.loadClass(impl);
                    engine = (MinAreaChecker) cls.newInstance();
                    break;
                } catch (ClassNotFoundException e) {
                } catch (InstantiationException e) {
                    e.printStackTrace();
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
            if (engine == null) {
                System.out.println("Engine not found");
                return false;
            }
            Properties parameters = engine.getDefaultParameters();
            System.out.println("Engine " + engine.getAlgorithmName() + " " + parameters);
            vce = new VectorCache(getDatabase().backup());
            CellId topCellId = topCell.getId();
            vce.scanLayers(topCellId);
            MyErrorLogger errorLogger = new MyErrorLogger();
            for (Layer layer : vce.getLayers()) {
//                if (layer.getFunction().isContact()) {
//                // via*, polyCut, activeCut)
//                    continue;
//                }
                if (vce.isBadLayer(layer)) {
                    System.out.println(layer + " is not manhattan. Skipped");
                    continue;
                }
                DRCTemplate minAreaRule = DRC.getMinValue(layer, DRCTemplate.DRCRuleType.MINAREA);
                if (minAreaRule == null) {
                    continue;
                }
                double minVal = minAreaRule.getValue(0);
                long minArea = DBMath.lambdaToGrid(minVal * DBMath.GRID);
                errorLogger.ruleName = minAreaRule.ruleName;

                LayoutCell lTop = makeLayoutCell(topCell.getId(), layer, new IdentityHashMap<CellId, LayoutCell>());
                engine.check(lTop, minArea, engine.getDefaultParameters(), errorLogger);
            }
            errorLogger.printReports();
            return true;
        }

        private LayoutCell makeLayoutCell(CellId eTop, Layer layer, Map<CellId, LayoutCell> made) {
            LayoutCell lc = made.get(eTop);
            if (lc != null) {
                return lc;
            }
            for (ImmutableNodeInst n : vce.getSubcells(eTop)) {
                if (!(n.protoId instanceof CellId)) {
                    continue;
                }
                makeLayoutCell((CellId) n.protoId, layer, made);
            }
//            System.out.println(eTop);
            DefaultLayoutCell lTop = new DefaultLayoutCell(eTop.toString());
            made.put(eTop, lTop);
            lTop.setName(eTop.toString());
            int count = vce.getNumBoxes(eTop, layer);
            for (int i = 0; i < count; i++) {
                vce.getBoxes(eTop, layer, i, 1, result);
                lTop.addRectangle(result[0], result[1], result[2], result[3]);
            }
            for (ImmutableNodeInst n : vce.getSubcells(eTop)) {
                CellId subCellId = (CellId) n.protoId;
                LayoutCell subCell = made.get(subCellId);
                if (subCell.getNumRectangles() == 0 && subCell.getNumSubcells() == 0) {
                    // skip empty subcells
                    continue;
                }
                int anchorX = (int) n.anchor.getGridX();
                int anchorY = (int) n.anchor.getGridY();
                ManhattanOrientation mor = ors.get(n.orient.canonic());
                assert mor != null;
                lTop.addSubCell(subCell, anchorX, anchorY, mor);
            }
            return lTop;
        }

        private void initOrientations() {
            putOrs(ManhattanOrientation.R0, Orientation.IDENT);
            putOrs(ManhattanOrientation.R90, Orientation.R);
            putOrs(ManhattanOrientation.R180, Orientation.RR);
            putOrs(ManhattanOrientation.R270, Orientation.RRR);
            putOrs(ManhattanOrientation.MY, Orientation.YRR);
            putOrs(ManhattanOrientation.MYR90, Orientation.YR);
            putOrs(ManhattanOrientation.MX, Orientation.Y);
            putOrs(ManhattanOrientation.MXR90, Orientation.YRRR);
        }

        private void putOrs(ManhattanOrientation mor, Orientation eor) {
            assert eor.canonic() == eor;
            assert mor.affineTransform().equals(eor.pureRotate());
            ors.put(eor, mor);
        }

        private class MyErrorLogger implements com.sun.electric.api.minarea.ErrorLogger {
            private ErrorLogger errorLogger = ErrorLogger.newInstance("minarea");
            private String ruleName;
            private int sortKey = 1;

            /**
             * The algorithm uses this method to report about polygon that violates
             * min area rule. The algorithm report actual area of the polygon and
             * vertex with lexigraphically maximal coordinates (x,y).
             * This means that rightmost vertical edges of polygon are choosen,
             * and than the most upper vertex on these edges is choosen.
             * Formally, such point (x,y) is reported that for any other point (x',y') of
             * this polygin:  (x' < x || x' == x && y' < y)
             * @param area the area of violating polygon
             * @param x x-coordinate of lexigraphically largest point of violating polygon
             * @param y y-coordinate of lexigraphically largest point of violating polygon
             * @param shape optional Shape of Polygon
             */
            public void reportMinAreaViolation(long area, int x, int y, Shape shape) {
                if (shape == null) {
                    errorLogger.logError(ruleName, EPoint.fromGrid(x, y), topCell, sortKey);
                } else {
                    ArrayList<EPoint> lines = new ArrayList<EPoint>(); 
                    PathIterator pit = shape.getPathIterator(null);
                    double[] coords = new double[6];
                    EPoint move = null, last = null;
                    while (!pit.isDone()) {
                        switch (pit.currentSegment(coords)) {
                            case PathIterator.SEG_MOVETO:
                                move = last = EPoint.fromGrid((long)coords[0], (long)coords[1]);
                                break;
                            case PathIterator.SEG_LINETO:
                                lines.add(last);
                                last = EPoint.fromGrid((long)coords[0], (long)coords[1]);
                                lines.add(last);
                                break;
                            case PathIterator.SEG_CLOSE:
                                lines.add(last);
                                lines.add(move);
                                move = last = null;
                                break;
                            default:
                                throw new AssertionError();
                        }
                        pit.next();
                    }
                    errorLogger.logMessageWithLines(ruleName, null, lines, topCell, sortKey, true);
                }
            }

            /**
             * 
             */
            public void printReports() {
                errorLogger.termLogging(true);
            }
        }
    }

    public static void readMinareaLay() {
        String layoutFileName = OpenFile.chooseInputFile(null, ".lay file", null);
        Layer layer = Technology.getMocmosTechnology().findLayer("Metal-1");
        long scale = 4;
        new ConvertVectorCacheToCellJob(layoutFileName, layer, scale).startJob();
    }

    // To read in lay files
    private static class ConvertVectorCacheToCellJob extends Job {

        private String layoutFileName;
        private PrimitiveNodeId protoId;
        private Technology tech;
        private long scale;
        private TextDescriptor nameDescriptor;
        private TextDescriptor protoDescriptor;
        private Map<ManhattanOrientation, Orientation> ors = new EnumMap<ManhattanOrientation, Orientation>(ManhattanOrientation.class);
        private Cell topElectricCell;

        protected ConvertVectorCacheToCellJob(String layoutFileName, Layer layer, long scale) {
            super("ConvertLayoutCell", User.getUserTool(), Job.Type.CHANGE, null, null, Job.Priority.USER);
            this.layoutFileName = layoutFileName;
            protoId = layer.getPureLayerNode().getId();
            tech = layer.getTechnology();
            this.scale = scale;
            initOrientations();
        }

        @Override
        public boolean doIt() {
            EditingPreferences ep = getEditingPreferences();
            nameDescriptor = ep.getNodeTextDescriptor();
            protoDescriptor = ep.getInstanceTextDescriptor();
            try {
                if (layoutFileName == null) {
                    return false; // no file uploaded
                }//                File layoutFile = new File(layoutFileName);
                System.out.print("Reading .lay file '" + layoutFileName);
                InputStream is;
//                if (layoutFile.canRead()) {
                is = new FileInputStream(layoutFileName);
//                System.out.println("file " + layoutFileName);
//                } else {
//                    is = com.sun.electric.api.minarea.launcher.Launcher.class.getResourceAsStream(layoutFileName);
//                    System.out.println("resource " + layoutFileName);
//                }
                ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(is));
                LayoutCell topCell = (LayoutCell) in.readObject();
                in.close();
                HashMap<LayoutCell, Cell> allCells = new HashMap<LayoutCell, Cell>();
                convertLayoutCell(topCell, allCells);
                topElectricCell = allCells.get(topCell);
                fieldVariableChanged("topElectricCell");
//            Class algorithmClass = Class.forName(className);
//            MinAreaChecker checker = (MinAreaChecker) algorithmClass.newInstance();
//            System.out.println("topCell " + topCell.getName() + " [" + topCell.getBoundingMinX() + ".."
//                    + topCell.getBoundingMaxX() + "]x[" + topCell.getBoundingMinY() + ".."
//                    + topCell.getBoundingMaxY() + "] minarea=" + minarea);
//            Properties parameters = checker.getDefaultParameters();
//            if (algorithmPropertiesFileName != null) {
//                Reader propertiesReader = new FileReader(algorithmPropertiesFileName);
//                parameters.load(in);
//                propertiesReader.close();
//            }
//            ErrorLogger logger = new ErrorRepositoryLogger();
//            System.out.println("algorithm " + checker.getAlgorithmName() + " parameters:" + parameters);
//            checker.check(topCell, minarea, parameters, logger);
//            logger.printReports();
                return true;
            } catch (IOException e) {
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
//        } catch (InstantiationException e) {
//            e.printStackTrace();
//        } catch (IllegalAccessException e) {
//            e.printStackTrace();
            }
            return false;
        }

        public void terminateOK() {
            Job.getUserInterface().displayCell(topElectricCell);
        }

        private void convertLayoutCell(LayoutCell topCell, final Map<LayoutCell, Cell> converted) {
            if (converted.containsKey(topCell)) {
                return;
            }
            topCell.traverseSubcellInstances(new LayoutCell.SubcellHandler() {

                public void apply(LayoutCell cell, int anchorX, int anchorY, ManhattanOrientation orient) {
                    convertLayoutCell(cell, converted);
                }
            });
            String name = topCell.getName();
            int indexOfColon = name.indexOf(':');
            String libName, cellName;
            if (indexOfColon >= 0) {
                libName = name.substring(0, indexOfColon);
                cellName = name.substring(indexOfColon + 1);
            } else {
                libName = "noname";
                cellName = name;
            }
            if (cellName.indexOf('{') < 0 && cellName.indexOf('}') < 0) {
                cellName += "{lay}";
            }
            Library lib = Library.findLibrary(libName);
            if (lib == null) {
                lib = Library.newInstance(libName, null);
            }
            final Cell eCell = Cell.newInstance(lib, cellName);
            assert eCell.getNumNodes() == 0;
            eCell.setTechnology(tech);
            converted.put(topCell, eCell);
//            System.out.println(topCell.getName());
            topCell.traverseRectangles(new LayoutCell.RectangleHandler() {

                public void apply(int minX, int minY, int maxX, int maxY) {
//                    System.out.println("  [" + minX + ".." + maxX + "]x[" + minY + ".." + maxY + "]");
                    long minXL = minX * scale;
                    long minYL = minY * scale;
                    long maxXL = maxX * scale;
                    long maxYL = maxY * scale;
                    long w = maxXL - minXL;
                    long h = maxYL - minYL;
                    EPoint anchor = EPoint.fromGrid((minXL + maxXL) / 2, (minYL + maxYL) / 2);
                    EPoint size = EPoint.fromGrid(w, h);
                    int nodeId = eCell.getNumNodes();
                    Name name = Name.findName("r@" + nodeId);
                    ImmutableNodeInst n = ImmutableNodeInst.newInstance(nodeId, protoId, name, nameDescriptor,
                            Orientation.IDENT, anchor, size, 0, 0, protoDescriptor);
                    eCell.addNode(n);
                }
            });
            topCell.traverseSubcellInstances(new LayoutCell.SubcellHandler() {

                public void apply(LayoutCell cell, int anchorX, int anchorY, ManhattanOrientation orient) {
//                    System.out.println("  " + cell.getName() + " at (" + anchorX + "," + anchorY + ") " + orient);
                    EPoint anchor = EPoint.fromGrid(anchorX * scale, anchorY * scale);
                    int nodeId = eCell.getNumNodes();
                    Name name = Name.findName("s@" + nodeId);
                    CellId subCellId = converted.get(cell).getId();
                    ImmutableNodeInst n = ImmutableNodeInst.newInstance(nodeId, subCellId, name, nameDescriptor,
                            ors.get(orient), anchor, EPoint.ORIGIN, 0, 0, protoDescriptor);
                    eCell.addNode(n);
                }
            });
        }

        private void initOrientations() {
            putOrs(ManhattanOrientation.R0, Orientation.IDENT);
            putOrs(ManhattanOrientation.R90, Orientation.R);
            putOrs(ManhattanOrientation.R180, Orientation.RR);
            putOrs(ManhattanOrientation.R270, Orientation.RRR);
            putOrs(ManhattanOrientation.MY, Orientation.YRR);
            putOrs(ManhattanOrientation.MYR90, Orientation.YR);
            putOrs(ManhattanOrientation.MX, Orientation.Y);
            putOrs(ManhattanOrientation.MXR90, Orientation.YRRR);
        }

        private void putOrs(ManhattanOrientation mor, Orientation eor) {
            assert eor.canonic() == eor;
            assert mor.affineTransform().equals(eor.pureRotate());
            ors.put(mor, eor);
        }
    }
}
