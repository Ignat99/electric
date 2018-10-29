/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: ErrorToElectricReporter.java
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
package com.sun.electric.api.minarea.errorLogging;

import java.awt.geom.PathIterator;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;

import com.sun.electric.api.minarea.launcher.Launcher;

/**
 * @author Felix Schmidt
 * 
 */
public class ErrorToElectricReporter extends AbstractErrorLogger {

    private String topCellName;

    public ErrorToElectricReporter(String topCellName) {
        this.topCellName = topCellName;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.sun.electric.api.minarea.MinAreaChecker.ErrorLogger#printReports()
     */
    @Override
    public void printReports(long time) {

        try {
            String pid = Launcher.getPID();
            String fileName = "errorLog." + pid + ".xml";
            BufferedReader br = new BufferedReader(new InputStreamReader(
                    ErrorToElectricReporter.class.getResourceAsStream("loggerHeader.xml")));
            PrintStream pstream = new PrintStream(new File(fileName));

            String line = null;
            while ((line = br.readLine()) != null) {
                pstream.println(line);
            }

            pstream.println("<ErrorLogger errorSystem=\"MinArea DRC\">");
            String cellName = this.topCellName;
            if (cellName.indexOf('{') == -1 && cellName.indexOf('}') == -1) {
                cellName += "{lay}";
            }
            pstream.println("\t<GroupLog message=\"" + cellName + "\">");

            sortViolations();
            for (ErrorToElectricReporter.MinAreaViolation violation : violations) {
                pstream.println("\t\t<MessageLog message=\"Minimum Area Violation: actual = "
                        + String.valueOf(violation.getMinArea())
                        + "\" cellName=\""
                        + cellName + "\">");
                if (violation.getShape() == null) {
                    pstream.println("\t\t\t<ERRORTYPEPOINT pt=\"("
                            + scaleCoord(violation.getX()) + "," + scaleCoord(violation.getY())
                            + ")\" cellName=\"" + cellName + "\"/>");
                } else {

                    pstream.println("\t\t\t<ERRORTYPEPOLY cellName=\"" + cellName
                            + "\">");

                    PathIterator pit = violation.getShape().getPathIterator(null);
                    double[] coords = new double[6];
                    double moveX = Double.NaN, moveY = Double.NaN, lastX = Double.NaN, lastY = Double.NaN;
                    while (!pit.isDone()) {
                        switch (pit.currentSegment(coords)) {
                            case PathIterator.SEG_MOVETO:
                                moveX = lastX = coords[0];
                                moveY = lastY = coords[1];
                                break;
                            case PathIterator.SEG_LINETO:
                                reportLine(lastX, lastY, coords[0], coords[1], cellName, pstream);
                                lastX = coords[0];
                                lastY = coords[1];
                                break;
                            case PathIterator.SEG_CLOSE:
                                reportLine(lastX, lastY, moveX, moveY, cellName, pstream);
                                moveX = lastX = moveY = lastY = Double.NaN;
                                break;
                            default:
                                throw new AssertionError();
                        }
                        pit.next();
                    }

                    pstream.println("\t\t\t</ERRORTYPEPOLY>");
                }
                pstream.println("\t\t</MessageLog>");
            }

            pstream.println("\t</GroupLog>");
            pstream.println("</ErrorLogger>");

            br.close();
            pstream.flush();
            pstream.close();

            System.out.println("Output file written: " + fileName);
        } catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }

//    private void reportRectangle(int data[], String cellName,
//            PrintStream pstream) {
//        pstream.println("\t\t\t<ERRORTYPETHICKLINE p1=\"("
//                + scaleCoord(data[0]) + "," + scaleCoord(data[1])
//                + ")\" p2=\"(" + scaleCoord(data[2]) + ","
//                + scaleCoord(data[1]) + ")\" cellName=\"" + cellName + "\"/>");
//        pstream.println("\t\t\t<ERRORTYPETHICKLINE p1=\"("
//                + scaleCoord(data[2]) + "," + scaleCoord(data[1])
//                + ")\" p2=\"(" + scaleCoord(data[2]) + ","
//                + scaleCoord(data[3]) + ")\" cellName=\"" + cellName + "\"/>");
//        pstream.println("\t\t\t<ERRORTYPETHICKLINE p1=\"("
//                + scaleCoord(data[2]) + "," + scaleCoord(data[3])
//                + ")\" p2=\"(" + scaleCoord(data[0]) + ","
//                + scaleCoord(data[3]) + ")\" cellName=\"" + cellName + "\"/>");
//        pstream.println("\t\t\t<ERRORTYPETHICKLINE p1=\"("
//                + scaleCoord(data[0]) + "," + scaleCoord(data[3])
//                + ")\" p2=\"(" + scaleCoord(data[0]) + ","
//                + scaleCoord(data[1]) + ")\" cellName=\"" + cellName + "\"/>");
//    }
    private void reportLine(double x1, double y1, double x2, double y2, String cellName, PrintStream pstream) {
        pstream.println("\t\t\t<ERRORTYPETHICKLINE p1=\"("
                + scaleCoord(x1) + "," + scaleCoord(y1)
                + ")\" p2=\"(" + scaleCoord(x2) + ","
                + scaleCoord(y2) + ")\" cellName=\"" + cellName + "\"/>");
    }

    private static double scaleCoord(double coord) {
        return coord / 100.;
    }
}
