/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: CalibreDrcErrors.java
 *
 * Copyright (c) 2005, Static Free Software. All rights reserved.
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

import com.sun.electric.database.geometry.EPoint;
import com.sun.electric.database.geometry.PolyBase;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.variable.UserInterface;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.user.ErrorLogger;

import java.awt.Shape;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;

/**
 * This reads an ASCII Calibre DRC error database
 * produced by running Calibre DRC.
 * It shows these errors on the specified cells in
 * Electric.
 */
public class CalibreDrcErrors {

    private int scale;
    private String topCellName;
    private Cell topCell;
    private ErrorLogger logger;
    private BufferedReader in;
    private List<DrcRuleViolation> ruleViolations;            // list of DrcRuleViolations
    private int lineno;
    private Map<Cell,String> mangledNames;
    private String type;
    private String filename;
    private boolean noPopupMessages;

    private static final String spaces = "[\\s\\t ]+";

    /**
     * Create a new CalibreDrcError class, read the errors in,
     * and then convert them to the ErrorLogger.
     * @param filename the ASCII calibre drc results database file
     * @return number of errors. Negative number in case of valid data.
     */
    public static int importErrors(String filename, Map<Cell,String> mangledNames, String type, boolean noPopupMessages) {
        BufferedReader in;
        try {
            FileReader reader = new FileReader(filename);
            in = new BufferedReader(reader);
        } catch (IOException e) {
            System.out.println("Error importing "+type+" Errors: "+e.getMessage());
            return -1;
        }

        if (in == null) return -1;
        CalibreDrcErrors errors = new CalibreDrcErrors(in, mangledNames, type, noPopupMessages);
        errors.filename = filename;
        // read first line
        if (!errors.readTop()) return -1;
        // read all rule violations
        if (!errors.readRules()) return -1;
        // finish
        return errors.done();
    }

    // Constructor
    private CalibreDrcErrors(BufferedReader in, Map<Cell,String> mangledNames, String type, boolean noPopupMessages) {
        assert(in != null);
        this.in = in;
        lineno = 0;
        ruleViolations = new ArrayList<DrcRuleViolation>();
        this.mangledNames = mangledNames;
        this.type = type;
        this.noPopupMessages = noPopupMessages;
    }

    // read the cell name and precision, if any
    private boolean readTop() {
        scale = 1000;
        String line;
        try {
            line = readLine(true);
        } catch (IOException e) {
            System.out.println("Error reading first line of file: "+e.getMessage());
            return false;
        }
        if (line == null) return false;

        String [] parts = line.trim().split(spaces);
        if (parts.length == 1) {
            topCellName = parts[0];
        } else if (parts.length == 2) {
            topCellName = parts[0];
            try {
                scale = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                System.out.println("Error converting precision '"+parts[1]+"' to a number, using default of "+scale);
                return false;
            }
        } else {
            // error
            System.out.println("Error on first line: Expected cell name and precision, or 'drc'");
            return false;
        }
        topCell = getCell(topCellName, mangledNames);
        if (topCell == null) {
            System.out.println("Cannot find cell "+topCellName+" specified in error file, line number "+lineno+", aborting");
            return false;
        }
        return true;
    }

    // read all Rule violations in the file
    private boolean readRules() {
        // read all errors
        try {
            while(true) {
                DrcRuleViolation v = readRule();
                if (v == null) break;
                if (v.errors.size() != 0) // something to include
                    ruleViolations.add(v);
            }
        } catch (IOException e) {
            System.out.println("Error reading file: "+e.getMessage());
            return false;
        }
        return true;
    }

    /**
     * Read a logged error.  Return false if there was a problem, or nothing left to read.
     * @return true if read ok, false if error or End of File
     */
    private DrcRuleViolation readRule() throws IOException {
        // read the first line: the rule name
        String ruleName = readLine(false);
        if (ruleName == null) return null;     // EOF, no more errors

        // read the header start line, tells us how many header lines there are
        Header header = readHeader();
        if (header == null) return null;

        // read the rest of the header
        for (int i=0; i<header.headerLength; i++) {
            String s = readLine();
            if (s == null) return null;
            header.addHeaderLine(s);
        }
        if (header.comment.length() == 0) header.comment.append(ruleName);

        DrcRuleViolation v = new DrcRuleViolation(ruleName, header);

        // read shapes describing errors
        incell = topCell;
        for (int i=0; i<header.currentDrcResultsCount; i++) {
            DrcError drc = readErrorShape();
            if (drc == null) break;
            v.addError(drc);
        }
        return v;
    }
    private Cell incell = null;


    // read the header of the rule violation
    private Header readHeader() throws IOException {
        String headerStart = readLine();
        if (headerStart == null) return null;

        StringTokenizer tokenizer = new StringTokenizer(headerStart);
        Header header = null;
        try {
            String cur = tokenizer.nextToken();
            String orig = tokenizer.nextToken();
            String len = tokenizer.nextToken();
            int icur = Integer.parseInt(cur);
            int iorig = Integer.parseInt(orig);
            int ilen = Integer.parseInt(len);
            header = new Header(icur, iorig, ilen);
        } catch (NoSuchElementException e) {
            System.out.println("Error parsing header start line, expected three integers on line number "+lineno+": "+headerStart);
            return null;
        } catch (NumberFormatException e) {
            System.out.println("Error converting count strings to integers on header start line, line number "+lineno+": "+headerStart);
            return null;
        }
        return header;
    }

    // populate a list of error shapes. return false on error.
    private DrcError readErrorShape() throws IOException {
        // we need to peek ahead, as it is unspecified how many error shapes there
        // may be
        String nextLine = readLine().trim();
        boolean boole = nextLine.startsWith("e") ? true : false;
        boolean boolp = nextLine.startsWith("p") ? true : false;

        if (boole || boolp) {
            String [] parts = nextLine.split(spaces);
            if (parts.length != 3) {
                System.out.println("Error on shape: expected ordinal and count numbers, line number "+lineno+": "+nextLine);
                return null;
            }
//            int ordinal = 0;
            int lines = 0;
            try {
//                ordinal = Integer.parseInt(parts[1]);
                lines = Integer.parseInt(parts[2]);
            } catch (NumberFormatException e) {
                System.out.println("Error on shape: expected ordinal and count numbers, line number "+lineno+": "+nextLine);
                return null;
            }
            // need to peek ahead to see if next line specifies a subcell
            nextLine = readLine().trim();
            if (nextLine.startsWith("CN")) {
                parts = nextLine.split(spaces);
                if (parts.length < 3) {
                    System.out.println("Error reading CN line, expected at least three fields, line number "+lineno+": "+nextLine);
                    return null;
                }
                String cellname = parts[1];
                String coordSpace = parts[2];
                if (coordSpace.equals("c")) {
                    // coords are in sub cell coord system
                    incell = getCell(cellname, mangledNames);
                    if (incell == null) incell = topCell;
                }
                nextLine = readLine();
                if (nextLine.startsWith("CL")) {
                	// Skip it! 
                	nextLine = readLine();
                }
            }

            DrcError drc = new DrcError(incell);
            double lambdaScale = incell.getTechnology().getScale() / 1000;
            // parse list of edges if this is edges shape
            if (boole) {
                for (int i=0; i<lines; i++) {
                    if (i != 0)             // skip first line read, done already when we looked for CN
                        nextLine = readLine();
                    Shape s = parseErrorEdge(nextLine, lambdaScale);
                    if (s == null) return drc;
                    drc.addShape(s);
                }
            }
            // parse list of poly vertices if this is a poly
            else {
                // boolp
                PolyBase.Point [] points = new PolyBase.Point[lines];
                for (int i=0; i<lines; i++) {
                    if (i != 0)             // skip first line read, done already when we looked for CN
                        nextLine = readLine();
                    if (nextLine.startsWith("SN")) {
                        nextLine = readLine();
                    }
                    if (!parseErrorPoint(nextLine, points, i, lambdaScale))
                        return null;
                }
                Shape s = new PolyBase(points);
                drc.addShape(s);
            }
            return drc;
        }
        else {
            System.out.println("Error, expected Edge or Poly definition on line number "+lineno+": "+nextLine);
        }
        return null;
    }

    // parse a line specifying an edge, and add it to a list of shapes
    // lambdaScale: divide microns by this number to get lambda
    private Shape parseErrorEdge(String line, double lambdaScale) {
        String [] vals = line.trim().split(spaces);
        if (vals.length != 4) {
            System.out.println("Error, bad format for edge on line number "+lineno+": "+line);
            return null;
        }
        try {
            double x1 = (double)Integer.parseInt(vals[0])/(double)scale/lambdaScale;
            double y1 = (double)Integer.parseInt(vals[1])/(double)scale/lambdaScale;
            double x2 = (double)Integer.parseInt(vals[2])/(double)scale/lambdaScale;
            double y2 = (double)Integer.parseInt(vals[3])/(double)scale/lambdaScale;
            Shape line2d = new Line2D.Double(x1, y1, x2, y2);
            return line2d;
        } catch (NumberFormatException e) {
            System.out.println("Error, bad format for edge on line number "+lineno+": "+line);
            return null;
        }
    }

    // parse a line specifying a polygon vertex, and add it to a list of points
    private boolean parseErrorPoint(String line, PolyBase.Point [] points, int point, double lambdaScale) {
        String [] vals = line.trim().split(spaces);
        if (vals.length != 2) {
            System.out.println("Error, bad format for poly vertex on line number "+lineno+": "+line);
            return false;
        }
        try {
            double x1 = (double)Integer.parseInt(vals[0])/(double)scale/lambdaScale;
            double y1 = (double)Integer.parseInt(vals[1])/(double)scale/lambdaScale;
            PolyBase.Point p = PolyBase.from(new Point2D.Double(x1, y1));
            points[point] = p;
            return true;
        } catch (NumberFormatException e) {
            System.out.println("Error, bad format for poly vertex on line number "+lineno+": "+line);
            return false;
        }
    }

    /**
     * Method to create Logger
     * @return number of errors if found
     */
    private int done() {
        try {
            in.close();
        } catch (IOException e) {}

        // populate error logger
        logger = ErrorLogger.newInstance("Calibre "+type+" Errors");
        int sortKey = 0;
        int count = 0;
        for (Iterator<DrcRuleViolation> it = ruleViolations.iterator(); it.hasNext(); ) {
            DrcRuleViolation v = it.next();
            String ruleDesc = v.header.comment.toString().replaceAll("\\n", ";");
            if (!ruleDesc.contains(v.ruleNumber)) ruleDesc = v.ruleNumber + ": " + ruleDesc;
            int y = 1;
            for (Iterator<DrcError> it2 = v.errors.iterator(); it2.hasNext(); ) {
                DrcError drcError = it2.next();
                Cell cell = drcError.cell;
//                List<Object> l = new ArrayList<Object>();
                List<EPoint> lineList = new ArrayList<EPoint>();
                List<PolyBase> polyList = new ArrayList<PolyBase>();
                for (Iterator<Shape> it3 = drcError.shapes.iterator(); it3.hasNext(); ) {
                    Shape shape = it3.next();
                    if (shape instanceof Line2D) {
                        Line2D line = (Line2D)shape;
                        lineList.add(EPoint.fromLambda(line.getX1(), line.getY1()));
                        lineList.add(EPoint.fromLambda(line.getX2(), line.getY2()));
                    } else if (shape instanceof PolyBase) {
                        PolyBase poly = (PolyBase)shape;
                        polyList.add(poly);
                    } else {
                        System.out.println("Unsupported drc error shape "+shape);
                    }
                }
                logger.logMessageWithLines(y+". "+cell.getName()+": "+ruleDesc, polyList,
                    lineList, cell, sortKey, true);
                y++;
                count++;
            }
            logger.setGroupName(sortKey, "(" + (y-1) + ") " + ruleDesc);
            sortKey++;
        }
        System.out.println(type+" Imported "+count+" errors from "+filename);
        if (count == 0 && !noPopupMessages) {
        	Job.getUserInterface().showInformationMessage(type+" Imported Zero Errors", type+" Import Complete");
        }
        logger.termLogging(!noPopupMessages);
        return logger.getNumErrors();
    }

    // -----------------------------------------------------------------------------

    private static class DrcRuleViolation {
        private final String ruleNumber;
        private final Header header;
        private final List<DrcError> errors;              // list of DrcErrors

        private DrcRuleViolation(String ruleNumber, Header header) {
            this.ruleNumber = ruleNumber;
            this.header = header;
            this.errors = new ArrayList<DrcError>();
        }
        private void addError(DrcError error) {
            errors.add(error);
        }
    }

    private static class DrcError {
        private final Cell cell;
        private final List<Shape> shapes;              // list of shapes

        private DrcError(Cell cell) {
            this.cell = cell;
            this.shapes = new ArrayList<Shape>();
        }
        private void addShape(Shape shape) {
            shapes.add(shape);
        }
    }

    private static class Header {
        private final int currentDrcResultsCount;
//        private final int originalDrcResultsCount;
        private final int headerLength;         // does not include headerStart line
//        private String ruleFilePath;
//        private String ruleFileTitle;
        private StringBuffer comment;

        private Header(int currentDrcResultsCount, int originalDrcResultsCount, int headerLength) {
            this.currentDrcResultsCount = currentDrcResultsCount;
//            this.originalDrcResultsCount = originalDrcResultsCount;
            this.headerLength = headerLength;
            comment = new StringBuffer();
        }

        public void addHeaderLine(String line) {
            if (line.startsWith("Rule File Pathname")) {
//                ruleFilePath = line;
            } else if (line.startsWith("Rule File Title")) {
//                ruleFileTitle = line;
            } else {
                if (comment.length() != 0) {
                    // already a line added
                    comment.append("\n");
                }
                comment.append(line);
            }
        }
    }

    // ---------------------------------------------------------

    private String readLine() throws IOException {
        return readLine(true);
    }
    private String readLine(boolean errorOnEOF) throws IOException {
        // if in is null we ignore
        if (in == null) return null;

        String line = in.readLine();
        if (line == null && errorOnEOF) {
            System.out.println("Unexpected End of File!");
            in = null;          // ignore rest of readLine requests
            return null;
        }
        lineno++;
        return line;
    }

    public static Cell getCell(String cellName, Map<Cell,String> mangledNames) {
        List<Cell> matchedNames = new ArrayList<Cell>();
        for (Map.Entry<Cell,String> entry : mangledNames.entrySet()) {
            String name = entry.getValue();
            if (name.equals(cellName))
                matchedNames.add(entry.getKey());
        }
        if (matchedNames.size() == 0) return null;
        if (matchedNames.size() == 1) return matchedNames.get(0);

        // more than one match, ask user to choose, or just return the first one in non-interactive mode
        UserInterface ui = Job.getUserInterface();
        String choices[] = new String[matchedNames.size()];
        for (int i=0; i<choices.length; i++) {
            choices[i] = matchedNames.get(i).describe(false);
        }
        int c = ui.askForChoice("Multiple cells matches, please choose one for \""+cellName+"\":",
                "Ambiguity Found", choices, choices[0]);
        return matchedNames.get(c);
    }

/*
    public static Cell getCell(String cellName) {
        // try blind search
        for (Iterator<Library> it = Library.getLibraries(); it.hasNext(); ) {
            Library lib = (Library)it.next();
            Cell c = lib.findNodeProto(cellName+"{lay}");
            if (c != null && (c instanceof Cell))
                return c;
        }
        // assume libname.cellname format
        if (cellName.indexOf(GDS.concatStr) != -1) {
            String libname = cellName.substring(0, cellName.indexOf(GDS.concatStr));
            String name = cellName.substring(cellName.indexOf(GDS.concatStr)+1, cellName.length());
            Library lib = Library.findLibrary(libname);
            if (lib == null) {
                // lib name may have been truncated
                for (Iterator<Library> it = Library.getLibraries(); it.hasNext(); ) {
                    Library l = (Library)it.next();
                    if (l.getName().startsWith(libname)) {
                        lib = l;
                        break;
                    }
                }
            }
            if (lib != null) {
                Cell c = lib.findNodeProto(name+"{lay}");
                if (c != null && (c instanceof Cell))
                    return c;
                // try taking off any ending _###
                if (name.matches("(.+?)_\\d+")) {
                    int underscore = name.lastIndexOf('_');
                    c = lib.findNodeProto(name.substring(0, underscore)+"{lay}");
                    if (c != null && (c instanceof Cell))
                        return c;
                }
            }
        }
        return null;
    }
*/

    // ======================================================================================

    // DRC Density results

    public static void readDensityErrors(Cell cell, File drcDirectory) {
        if (drcDirectory == null || !drcDirectory.exists() || !drcDirectory.isDirectory()) {
            System.out.println("DRC density errors: no such directory: "+drcDirectory.getAbsolutePath());
            return;
        }
        ErrorLogger logger = ErrorLogger.newInstance("Calibre DRC Density Values");
        int sortKey = 0;
        double scale = cell.getTechnology().getScale();
        for (File file : drcDirectory.listFiles()) {
            if (file.getName().endsWith(".density")) {
                try {
                    FileReader reader = new FileReader(file);
                    BufferedReader in = new BufferedReader(reader);
                    String line = null;
                    int lineno = 0;
                    boolean first = true;
                    while ( (line = in.readLine()) != null) {
                        lineno++;
                        if (line.equals("")) continue;
                        String [] parts = line.split("[ ]+");
                        if (parts.length != 5) {
                            System.out.println("Ignoring line "+file.getName()+"."+lineno+": "+line);
                            continue;
                        }
                        if (first) {
                            logger.setGroupName(sortKey, file.getName());
                            first = false;
                        }
                        double x1, y1, x2, y2;
                        x1 = Double.valueOf(parts[0]).doubleValue() / scale * 1000;
                        y1 = Double.valueOf(parts[1]).doubleValue() / scale * 1000;
                        x2 = Double.valueOf(parts[2]).doubleValue() / scale * 1000;
                        y2 = Double.valueOf(parts[3]).doubleValue() / scale * 1000;
                        PolyBase poly = new PolyBase(
                                PolyBase.fromLambda(x1, y1),
                                PolyBase.fromLambda(x1, y2),
                                PolyBase.fromLambda(x2, y2),
                                PolyBase.fromLambda(x2, y1));
                        logger.logError(file.getName()+": "+parts[4], poly, cell, sortKey);
                    }
                    if (!first) sortKey++;
                    reader.close();
                } catch (IOException e) {
                    System.out.println("Error read file "+file.getName()+": "+e.getMessage());
                }
            }
        }
        logger.termLogging(false);
    }
}
