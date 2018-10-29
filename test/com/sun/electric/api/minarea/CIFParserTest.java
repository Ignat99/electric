/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: ManhattanOrientationTest.java
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
package com.sun.electric.api.minarea;

import com.sun.electric.api.minarea.launcher.DefaultLayoutCell;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

import com.sun.electric.api.minarea.geometry.Point;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.URL;
import org.junit.Ignore;

/**
 *
 */
public class CIFParserTest {

    public CIFParserTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    @Ignore
    @Test
    public void writeSerializations() {
        URL url = CIFParserTest.class.getResource("SimpleHierarchy.cif");
        int scaleFactor = 10;
        writeSerialization("BasicAreas_CPG.lay", url, scaleFactor, "CPG", 101);
        writeSerialization("BasicAreas_CMF.lay", url, scaleFactor, "CMF", 101);
        writeSerialization("BasicAreas_CSP.lay", url, scaleFactor, "CSP", 101);
        writeSerialization("SimpleHierarchy_CPG.lay", url, scaleFactor, "CPG", 102);
        writeSerialization("SimpleHierarchy_CMF.lay", url, scaleFactor, "CMF", 102);
        writeSerialization("SimpleHierarchy_CSP.lay", url, scaleFactor, "CSP", 102);
    }

    private static void writeSerialization(String fileName, URL cifUrl, int scaleFactor, String layerSelector, int topCellId) {
        boolean COMPARE = true;
        try {
            if (COMPARE) {
                ByteArrayOutputStream ba = new ByteArrayOutputStream();
                writeSerialization(ba, cifUrl, scaleFactor, layerSelector, topCellId);
                ba.close();
                byte[] result = ba.toByteArray();
                InputStream in = CIFParserTest.class.getResourceAsStream("launcher/" + fileName);
                byte[] expected = new byte[in.available()];
                assertEquals(result.length, in.read(expected));
                assertArrayEquals(expected, result);
            } else {
                FileOutputStream os = new FileOutputStream(fileName);
                writeSerialization(os, cifUrl, scaleFactor, layerSelector, topCellId);
                os.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void writeSerialization(OutputStream os, URL cifUrl, int scaleFactor, String layerSelector, int topCellId) throws IOException {
        boolean DEBUG = false;
        CIF cif;
        GenCIFActions gcif = new GenCIFActions();
        gcif.layerSelector = layerSelector;
        gcif.scaleFactor = scaleFactor;
        if (DEBUG) {
            DebugCIFActions dcif = new DebugCIFActions();
            dcif.out = System.out;
            dcif.impl = gcif;
            cif = new CIF(dcif);
        } else {
            cif = new CIF(gcif);
        }
        cif.openTextInput(cifUrl);
        cif.importALibrary();
        cif.closeInput();

        LayoutCell topCell = gcif.cells.get(Integer.valueOf(topCellId));
        ObjectOutputStream out = new ObjectOutputStream(os);
        out.writeObject(topCell);
        out.close();
    }

    public static class GenCIFActions implements CIF.CIFActions {

        private int scaleFactor;
        private String layerSelector;
        private Map<Integer, DefaultLayoutCell> cells = new HashMap<Integer, DefaultLayoutCell>();
        private DefaultLayoutCell curCell;
        private ManhattanOrientation curOrient;
        // private long[] curTranslate = new long[2];
        private Point curTranslate;
        private boolean isSelectedLayer;

        public void initInterpreter() {
            cells.clear();
            curCell = null;
        }

        public void makeWire(int width/* , path */) {
        }

        public void makeStartDefinition(int symbol, int mtl, int div) {
            Integer symbolObj = Integer.valueOf(symbol);
            assert curCell == null;
            if (cells.containsKey(symbolObj)) {
                throw new IllegalStateException("attempt to redefine symbol " + symbol);
            }
            curCell = new DefaultLayoutCell(symbolObj.toString());
            cells.put(symbolObj, curCell);
            isSelectedLayer = false;
        }

        public void makeEndDefinition() {
            assert curCell != null;
            curCell = null;
        }

        public void makeDeleteDefinition(int n) {
            System.out.println("makeDeleteDefinition not supported");
        }

        public void initTransform() {
            curOrient = ManhattanOrientation.R0;
            curTranslate = new Point(0, 0);
        }

        public void appendTranslate(int xt, int yt) {
            curTranslate = curTranslate.add(new Point(xt, yt));
        }

        public void appendMirrorX() {
            /*
             * MirrorX in CIF means change sign of x-coordinate, this correspond
             * to MY in EDIF notation.
             */
            appendOrient(ManhattanOrientation.MY);
        }

        public void appendMirrorY() {
            /*
             * MirrorY in CIF means change sign of y-coordinate, this correspond
             * to MX in EDIF notation.
             */
            appendOrient(ManhattanOrientation.MX);
        }

        public void appendRotate(int xRot, int yRot) {
            ManhattanOrientation orient;
            if (yRot == 0) {
                if (xRot == 0) {
                    throw new IllegalArgumentException("Zero rotate vector");
                }
                orient = xRot > 0 ? ManhattanOrientation.R0 : ManhattanOrientation.R180;
            } else {
                if (xRot != 0) {
                    throw new UnsupportedOperationException("Unly Manhattan rotations are supported");
                }
                orient = yRot > 0 ? ManhattanOrientation.R90 : ManhattanOrientation.R270;
            }
            appendOrient(orient);
        }

        private void appendOrient(ManhattanOrientation orient) {
            curTranslate = curTranslate.transform(orient);
            curOrient = orient.concatenate(curOrient);
        }

        public void initPath() {
        }

        public void appendPoint(Point p) {
        }

        public void makeCall(int symbol, int lineNumber/* , transform */) {
            DefaultLayoutCell subCell = cells.get(Integer.valueOf(symbol));
            if (subCell == null) {
                throw new IllegalArgumentException("Subcell " + symbol + " not found");
            }
            if (subCell == curCell) {
                throw new IllegalArgumentException("Recursive cell call");
            }
            if (subCell.getNumRectangles() == 0 && subCell.getNumSubcells() == 0) {
                // skip empty subcells
                return;
            }
            if (curTranslate.getX() % scaleFactor != 0 || curTranslate.getY() % scaleFactor != 0) {
                throw new IllegalArgumentException("Scale factor error");
            }
            int anchorX = curTranslate.getX() / scaleFactor;
            int anchorY = curTranslate.getY() / scaleFactor;
            if (curCell != null) {
                curCell.addSubCell(subCell, anchorX, anchorY, curOrient);
            }
        }

        public void makeLayer(String lName) {
            isSelectedLayer = lName.equals(layerSelector);
        }

        public void makeFlash(int diameter, Point center) {
        }

        public void makePolygon(/* path */) {
        }

        public void makeBox(int length, int width, Point center, int xr, int yr) {
            int xl = center.getX() - length / 2;
            int yl = center.getY() - width / 2;
            int xh = center.getX() + length / 2;
            int yh = center.getY() + width / 2;
            if (xl % scaleFactor != 0 || yl % scaleFactor != 0 || xh % scaleFactor != 0 || yh % scaleFactor != 0) {
                throw new IllegalArgumentException("Scale factor error");
            }
            xl /= scaleFactor;
            yl /= scaleFactor;
            xh /= scaleFactor;
            yh /= scaleFactor;

            if (yr != 0 || xr <= 0) {
                throw new UnsupportedOperationException("Rotated boxes are not supported");
            }
            if (curCell != null && isSelectedLayer) {
                curCell.addRectangle(xl, yl, xh, yh);
            }
        }

        public void makeUserComment(int command, String text) {
        }

        public void makeSymbolName(String name) {
            if (curCell != null) {
                curCell.setName(name);
            }
        }

        public void makeInstanceName(String name) {
        }

        public void makeGeomName(String name, Point pt, String lay) {
        }

        public void makeLabel(String name, Point pt) {
        }

        public void processEnd() {
        }

        public void doneInterpreter() {
        }
    }

    public static class DebugCIFActions implements CIF.CIFActions {

        private PrintStream out;
        private CIF.CIFActions impl;

        public void initInterpreter() {
            out.println("initInterpretator();");
            impl.initInterpreter();
        }

        public void makeWire(int width/* , path */) {
            out.println("makeWire(" + width + ");");
            impl.makeWire(width);
        }

        public void makeStartDefinition(int symbol, int mtl, int div) {
            out.println("makeStartDefinition(" + symbol + "," + mtl + "," + div + ");");
            impl.makeStartDefinition(symbol, mtl, div);
        }

        public void makeEndDefinition() {
            out.println("makeEndDefinition();");
            impl.makeEndDefinition();
        }

        public void makeDeleteDefinition(int n) {
            out.print("makeDeleteDefinition(" + n + ");");
            impl.makeDeleteDefinition(n);
        }

        public void initTransform() {
            out.println("initTransform();");
            impl.initTransform();
        }

        public void appendTranslate(int xt, int yt) {
            out.println("appendTranslate(" + xt + "," + yt + ");");
            impl.appendTranslate(xt, yt);
        }

        public void appendMirrorX() {
            out.println("appendMirrorX();");
            impl.appendMirrorX();
        }

        public void appendMirrorY() {
            out.println("appendMirrorY();");
            impl.appendMirrorY();
        }

        public void appendRotate(int xRot, int yRot) {
            out.println("appendRotate(" + xRot + "," + yRot + ");");
            impl.appendRotate(xRot, yRot);
        }

        public void initPath() {
            out.println("initPath();");
            impl.initPath();
        }

        public void appendPoint(Point p) {
            out.println("appendPoint(" + p.getX() + "," + p.getY() + ");");
            impl.appendPoint(p);
        }

        public void makeCall(int symbol, int lineNumber/* , transform */) {
            out.println("makeCall(" + symbol + "," + lineNumber + ");");
            impl.makeCall(symbol, lineNumber);
        }

        public void makeLayer(String lName) {
            out.println("makeLayer(\"" + lName + "\");");
            impl.makeLayer(lName);
        }

        public void makeFlash(int diameter, Point center) {
            out.println("makeFlash(" + diameter + "," + center.getX() + "," + center.getY() + ");");
            impl.makeFlash(diameter, center);
        }

        public void makePolygon(/* path */) {
            out.println("makePolygon();");
            impl.makePolygon();
        }

        public void makeBox(int length, int width, Point center, int xr, int yr) {
            out.println("makeBox(" + length + "," + width + "," + center.getX() + "," + center.getY() + ","
                    + xr + "," + yr + ");");
            impl.makeBox(length, width, center, xr, yr);
        }

        public void makeUserComment(int command, String text) {
            out.println("makeUserComment(" + command + ",\"" + text + "\");");
            impl.makeUserComment(command, text);
        }

        public void makeSymbolName(String name) {
            out.println("makeSymbolName(\"" + name + "\");");
            impl.makeSymbolName(name);
        }

        public void makeInstanceName(String name) {
            out.println("makeInstanceName(\"" + name + "\");");
            impl.makeInstanceName(name);
        }

        public void makeGeomName(String name, Point pt, String lay) {
            out.println("makeGeomName(\"" + name + "\"," + pt.getX() + "," + pt.getY() + ","
                    + (lay != null ? "\"" + lay + "\"" : "null") + ");");
            impl.makeGeomName(name, pt, lay);
        }

        public void makeLabel(String name, Point pt) {
            out.println("makeLableName(\"" + name + "\"," + pt.getX() + "," + pt.getY() + ");");
            impl.makeLabel(name, pt);
        }

        public void processEnd() {
            out.println("processEnd();");
            impl.processEnd();
        }

        public void doneInterpreter() {
            out.println("doneInterpreter();");
            impl.doneInterpreter();
        }
    }

    public static class NullCIFActions implements CIF.CIFActions {

        public void initInterpreter() {
        }

        public void makeWire(int width/* , path */) {
        }

        public void makeStartDefinition(int symbol, int mtl, int div) {
        }

        public void makeEndDefinition() {
        }

        public void makeDeleteDefinition(int n) {
        }

        public void initTransform() {
        }

        public void appendTranslate(int xt, int yt) {
        }

        public void appendMirrorX() {
        }

        public void appendMirrorY() {
        }

        public void appendRotate(int xRot, int yRot) {
        }

        public void initPath() {
        }

        public void appendPoint(Point p) {
        }

        public void makeCall(int symbol, int lineNumber/* , transform */) {
        }

        public void makeLayer(String lName) {
        }

        public void makeFlash(int diameter, Point center) {
        }

        public void makePolygon(/* path */) {
        }

        public void makeBox(int length, int width, Point center, int xr, int yr) {
        }

        public void makeUserComment(int command, String text) {
        }

        public void makeSymbolName(String name) {
        }

        public void makeInstanceName(String name) {
        }

        public void makeGeomName(String name, Point pt, String lay) {
        }

        public void makeLabel(String name, Point pt) {
        }

        public void processEnd() {
        }

        public void doneInterpreter() {
        }
    }
}
