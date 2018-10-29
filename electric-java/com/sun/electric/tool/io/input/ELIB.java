/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: ELIB.java
 * Input/output tool: ELIB Library input
 * Written by Steven M. Rubin.
 *
 * Copyright (c) 2003, Static Free Software. All rights reserved.
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
package com.sun.electric.tool.io.input;

import com.sun.electric.database.EditingPreferences;
import com.sun.electric.database.ImmutableArcInst;
import com.sun.electric.database.ImmutableExport;
import com.sun.electric.database.geometry.EPoint;
import com.sun.electric.database.geometry.Poly;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.Export;
import com.sun.electric.database.hierarchy.Library;
import com.sun.electric.database.hierarchy.View;
import com.sun.electric.database.id.ArcProtoId;
import com.sun.electric.database.id.CellId;
import com.sun.electric.database.id.ExportId;
import com.sun.electric.database.id.IdManager;
import com.sun.electric.database.id.LibId;
import com.sun.electric.database.id.NodeProtoId;
import com.sun.electric.database.id.PrimitiveNodeId;
import com.sun.electric.database.id.PrimitivePortId;
import com.sun.electric.database.id.TechId;
import com.sun.electric.database.prototype.NodeProto;
import com.sun.electric.database.prototype.PortCharacteristic;
import com.sun.electric.database.prototype.PortProto;
import com.sun.electric.database.text.Name;
import com.sun.electric.database.text.Version;
import com.sun.electric.database.topology.ArcInst;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.topology.PortInst;
import com.sun.electric.database.variable.CodeExpression;
import com.sun.electric.database.variable.TextDescriptor;
import com.sun.electric.database.variable.Variable;
import com.sun.electric.technology.ArcProto;
import com.sun.electric.technology.PrimitiveNode;
import com.sun.electric.technology.PrimitivePort;
import com.sun.electric.technology.Technology;
import com.sun.electric.technology.technologies.Artwork;
import com.sun.electric.technology.technologies.Generic;
import com.sun.electric.technology.technologies.Schematics;
import com.sun.electric.tool.Tool;
import com.sun.electric.tool.io.ELIBConstants;
import com.sun.electric.tool.io.FileType;
import com.sun.electric.tool.ncc.basic.TransitiveRelation;
import com.sun.electric.tool.user.ErrorLogger;
import com.sun.electric.util.TextUtils;
import com.sun.electric.util.math.FixpTransform;
import com.sun.electric.util.math.GenMath;
import com.sun.electric.util.math.Orientation;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * This class reads files in binary (.elib) format.
 */
public class ELIB extends LibraryFiles {

    public static class Header implements Comparable, Serializable {

        /** current header */
        static final Header DEFAULT = new Header(ELIBConstants.MAGIC13, ByteOrder.BIG_ENDIAN, 4, 2, 1);
        /** the magic number in the library file */
        private final int magic;
        // characteristics of the file
        /** byte order on disk */
        private transient ByteOrder byteOrder;
        /** true if BIG_ENDIAN byte order on disk */
        private final boolean bytesSwapped;
        /** the size of a "big" integer on disk (4 or more bytes) */
        private final int sizeOfBig;
        /** the size of a "small" integer on disk (2 or more bytes) */
        private final int sizeOfSmall;
        /** the size of a character on disk (1 or 2 bytes) */
        private final int sizeOfChar;
        /** string description */
        private final String s;

        Header(int magic, ByteOrder byteOrder, int sizeOfBig, int sizeOfSmall, int sizeOfChar) {
            this.magic = magic;
            this.byteOrder = byteOrder;
            this.bytesSwapped = (byteOrder == ByteOrder.BIG_ENDIAN);
            this.sizeOfBig = sizeOfBig;
            this.sizeOfSmall = sizeOfSmall;
            this.sizeOfChar = sizeOfChar;

            int magicNum = ELIBConstants.MAGIC1 + 2 - magic;
            String s = "MAGIC" + (magicNum / 2);
            if (magicNum % 2 != 0) {
                s += "?";
            }
            if (byteOrder != ByteOrder.BIG_ENDIAN) {
                s += "L";
            }
            if (sizeOfBig != 4) {
                s += "I" + sizeOfBig;
            }
            if (sizeOfSmall != 2) {
                s += "S" + sizeOfSmall;
            }
            if (sizeOfChar != 1) {
                s += "C" + sizeOfChar;
            }
            this.s = s;
        }

        private void readObject(ObjectInputStream s)
                throws IOException, ClassNotFoundException {
            s.defaultReadObject();
            byteOrder = bytesSwapped ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN;
        }

        /**
         * Returns a hash code for this <code>Header</code>.
         * @return  a hash code value for this Header.
         */
        @Override
        public int hashCode() {
            return s.hashCode();
        }

        /**
         * Compares this Header object to the specified object.  The result is
         * <code>true</code> if and only if the argument is not
         * <code>null</code> and is an <code>Header</code> object that
         * contains the same <code>version</code>, <code>view</code> and case-insensitive <code>name</code> as this Header.
         *
         * @param   obj   the object to compare with.
         * @return  <code>true</code> if the objects are the same;
         *          <code>false</code> otherwise.
         */
        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Header) {
                Header h = (Header) obj;
                return s.equals(h.s);
            }
            return false;
        }

        /**
         * Compares two <code>Header</code> objects.
         * @param   o   the object to be compared.
         * @return	the result of comparision.
         */
        public int compareTo(Object o) {
            Header h = (Header) o;
            return TextUtils.STRING_NUMBER_ORDER.compare(s, h.s);
        }

        /**
         * Returns string description of this Header.
         * @return the string description of this Header.
         */
        @Override
        public String toString() {
            return s;
        }
    }
    // ------------------------- private data ----------------------------
    /** header of the file */
    private Header header;
    // statistics about the file
    /** the number of integers on disk that got clipped during input */
    private int clippedIntegers;
    // the tool information
    /** the number of tools in the file */
    private int toolCount;
    /** the number of tools in older files */
    private int toolBCount;
    /** list of all tools in the library */
    private Tool[] toolList;
    /** list of all tool-related errors in the library */
    private String[] toolError;
    /** list of all tool variables */
    private Variable[][] toolVars;
    /** the library userbits */
    private int libUserBits;
    /** the library variables */
    private Variable[] libVars;
    // the technology information
    /** the number of technologies in the file */
    private int techCount;
    /** list of all TechIds in the library */
    private TechId[] techIdList;
    /** list of all Technologies in the library */
    private Technology[] techList;
    /** list of all technology-related errors in the library */
    private String[] techError;
    /** list of all technology variables in the library */
    private Variable[][] techVars;
    /** list of technology lambda values in the library */
    private int[] techLambda;
    /** scale factors for each technology in the library */
    private HashMap<Technology, Double> techScale = new HashMap<Technology, Double>();
    /** the number of ArcProtos in the file */
    private int arcProtoCount;
    /** list of all ArcProtoIds in the library */
    private ArcProtoId[] arcProtoIdList;
    /** list of all ArcProtos in the library */
    private ArcProto[] arcProtoList;
    /** list of all ArcProto-related errors in the library */
    private String[] arcProtoError;
    /** the number of primitive NodeProtos in the file */
    private int primNodeProtoCount;
    /** list of all PrimitiveNodeIds in the library */
    private PrimitiveNodeId[] primitiveNodeIdList;
    /** list of all Primitive NodeProtos in the library */
    private PrimitiveNode[] primNodeProtoList;
    /** list of the primitive-NodeProto-related errors in the library */
    private boolean[] primNodeProtoError;
    /** list of the original primitive NodeProtos in the library */
    private String[] primNodeProtoOrig;
    /** list of all NodeProto technologies in the library */
    private int[] primNodeProtoTech;
    /** the number of primitive PortProtos in the file */
    private int primPortProtoCount;
    /** list of all PrimitivePortIds in the library */
    private PrimitivePortId[] primitivePortIdList;
    /** list of all Primitive PortProtos in the library */
    private PrimitivePort[] primPortProtoList;
    /** list of all Primitive-PortProto-related errors in the library */
    private String[] primPortProtoError;
    // the cell information
    /** the number of Cells in the file */
    private int cellCount;
    /** list of all former cells in the library */
    private FakeCell[] fakeCellList;
    /** list of Cell full names in the library */
    private String[] cellProtoName;
    /** list of Cell creation dates in the library */
    private int[] cellCreationDate;
    /** list of Cell revision dates in the library */
    private int[] cellRevisionDate;
    /** list of Cell user bits in the library */
    private int[] cellUserBits;
    /** list of Cell variables in the library */
    private Variable[][] cellVars;
    /** list of Cell low X coordinates in the library */
    private int[] cellLowX;
    /** list of Cell high X coordinates in the library */
    private int[] cellHighX;
    /** list of Cell low Y coordinates in the library */
    private int[] cellLowY;
    /** list of Cell high Y coordinates in the library */
    private int[] cellHighY;
    /** list of Cell library paths in the library */
    private String[] cellLibraryPath;
    /** list of number of NodeInsts in each Cell of the library */
    private int[] nodeCounts;
    /** index of first NodeInst in each cell of the library */
    private int[] firstNodeIndex;
    /** list of number of ArcInsts in each Cell of the library */
    private int[] arcCounts;
    /** index of first ArcInst in each cell of the library */
    private int[] firstArcIndex;
    /** list of all Exports in each Cell of the library */
    private int[] portCounts;
    /** index of first Export in each cell of the library */
    private int[] firstPortIndex;
    /** X center each cell of the library */
    private int[] cellXOff;
    /** Y center each cell of the library */
    private int[] cellYOff;
    /** a next cell index in the same next group of the library */
    private int[] cellNextInCellGroup;
    /** true if this x-lib cell ref is satisfied */
    private boolean[] xLibRefSatisfied;
    /** mapping view indices to views */
    private HashMap<Integer, View> viewMapping;
    // the NodeInsts in the library
    /** the number of NodeInsts in the library */
    private int nodeCount;
    /** All data for NodeInsts in each Cell. */
    private LibraryFiles.NodeInstList nodeInstList;
    /** List of prototypes of the NodeInsts in the library */
    private int[] nodeTypeList;
    // the ArcInsts in the library
    /** the number of ArcInsts in the library */
    private int arcCount;
    /** list of all ArcInsts in the library */
    private ArcInst[] arcList;
    /** list of the prototype of the ArcInsts in the library */
    private int[] arcTypeList;
    /** list of the Names of the ArcInsts in the library */
    private String[] arcNameList;
    /** list of the name descriptors of the ArcInsts in the library */
    private TextDescriptor[] arcNameDescriptorList;
    /** list of the width of the ArcInsts in the library */
    private int[] arcWidthList;
    /** list of the head X of the ArcInsts in the library */
    private int[] arcHeadXPosList;
    /** list of the head Y of the ArcInsts in the library */
    private int[] arcHeadYPosList;
    /** list of the head node of the ArcInsts in the library */
    private int[] arcHeadNodeList;
    /** list of the head port of the ArcInsts in the library */
    private int[] arcHeadPortList;
    /** list of the tail X of the ArcInsts in the library */
    private int[] arcTailXPosList;
    /** list of the tail Y of the ArcInsts in the library */
    private int[] arcTailYPosList;
    /** list of the tail node of the ArcInsts in the library */
    private int[] arcTailNodeList;
    /** list of the tail port of the ArcInsts in the library */
    private int[] arcTailPortList;
    /** list of the user flags on the ArcInsts in the library */
    private int[] arcUserBits;
    /** list of the variables on the ArcInsts in the library */
    private Variable[][] arcVariables;
    // the Exports in the library
    /** the number of Exports in the library */
    private int exportCount;
    /** counter for Exports in the library */
    private int exportIndex;
    /** list of all Exports in the library */
    private Object[] exportList;
    /** list of NodeInsts that are origins of Exports in the library */
    private int[] exportSubNodeList;
    /** list of PortProtos that are origins of Exports in the library */
    private int[] exportSubPortList;
    /** list of Export names in the library */
    private String[] exportNameList;
    /** list of Export name descriptors in the library */
    private TextDescriptor[] exportNameDescriptors;
    /** list of Export userbits in the library */
    private int[] exportUserbits;
    /** list of variables on Exports in the library */
    private Variable[][] exportVariables;
    // the geometric information (only used for old format files)
    /** the number of Geometrics in the file */
    private int geomCount;
    /** list of all Geometric types in the library */
    private boolean[] geomType;
    /** list of all Geometric up-pointers in the library */
    private int[] geomMoreUp;
    // the variable information
    /** variable names possibly in the library */
    private String varNames[];
    /** variable keys possibly in the library */
    private Variable.Key[] varKeys;
    /** true to convert all text descriptor values */
    private boolean convertTextDescriptors;
    /** true to require text descriptor values */
    private boolean alwaysTextDescriptors;

    /**
     * This class is used to convert old "facet" style Libraries to pure Cell Libraries.
     */
    private static class FakeCell {

        String cellName;
        NodeProto firstInCell;
    }

    ELIB(EditingPreferences ep) {
        super(ep);
    }

    // ----------------------- public methods -------------------------------
    /**
     * Method to read a Library from disk.
     * This method is for reading full Electric libraries in ELIB, JELIB, and Readable Dump format.
     * @param fileURL the URL to the disk file.
     * @return the read Library, or null if an error occurred.
     */
//	public static synchronized Header readLibraryHeader(URL fileURL, ErrorLogger errorLogger)
//	{
//		try {
//			ELIB in = new ELIB();
//			if (in.openBinaryInput(fileURL)) return null;
//			Header header = in.readHeader();
//			// read the library
//			in.closeInput();
//			return header;
//        } catch (Exception e)
//		{
//            errorLogger.logError("Error " + e + " on " + fileURL, -1);
//        }
//		return null;
//    }
    /**
     * Method to read a Library from disk.
     * This method is for reading full Electric libraries in ELIB, JELIB, and Readable Dump format.
     * @param fileURL the URL to the disk file.
     * @return the read Library, or null if an error occurred.
     */
    public static synchronized boolean readStatistics(URL fileURL, ErrorLogger errorLogger,
            LibraryStatistics.FileContents fc, EditingPreferences ep) {
        try {
            ELIB in = new ELIB(null);
            if (in.openBinaryInput(fileURL)) {
                return true;
            }
            boolean error = in.readTheLibrary(true, fc, ep);
            // read the library
            in.closeInput();
            return error;
        } catch (Exception e) {
            errorLogger.logError("Error " + e + " on " + fileURL, -1);
            e.printStackTrace();
        }
        return true;
    }

    @Override
    protected boolean readProjectSettings(EditingPreferences ep) {
        try {
            if (readTheLibrary(true, null, ep)) {
                return true;
            }
            createLibraryCells(true, ep);
            return false;
        } catch (IOException e) {
            System.out.println("End of file reached while reading " + filePath);
            return true;
        }
    }

    /**
     * Method to read the .elib file.
     * returns true on error.
     */
    @Override
    boolean readTheLibrary(boolean onlyProjectSettings, LibraryStatistics.FileContents fc, EditingPreferences ep)
            throws IOException {
        // initialize
        clippedIntegers = 0;
        byteCount = 0;

        // read the magic number and determine whether bytes are swapped
        header = readHeader();
        if (header == null) {
            System.out.println("Error reading header");
            return true;
        }
        if (fc != null) {
            fc.header = header;
        }

        // get count of objects in the file
        toolCount = readBigInteger();
        techCount = readBigInteger();
        primNodeProtoCount = readBigInteger();
        primPortProtoCount = readBigInteger();
        arcProtoCount = readBigInteger();
        nodeProtoCount = readBigInteger();
        nodeCount = readBigInteger();
        exportCount = readBigInteger();
        arcCount = readBigInteger();
        geomCount = readBigInteger();
        if (header.magic <= ELIBConstants.MAGIC9 && header.magic >= ELIBConstants.MAGIC11) {
            // versions 9 through 11 stored a "cell count"
            cellCount = readBigInteger();
        } else {
            cellCount = nodeProtoCount;
        }
        readBigInteger();		// ignore current cell

        // get the Electric version (version 8 and later)
        String versionString;
        if (header.magic <= ELIBConstants.MAGIC8) {
            versionString = readString();
        } else {
            versionString = "3.35";
        }
        version = Version.parseVersion(versionString);
        if (fc != null) {
            fc.version = version;
        }

        // for versions before 6.03q, convert MOSIS CMOS technology names
        convertMosisCmosTechnologies = version.compareTo(Version.parseVersion("6.03q")) < 0;

        // for versions before 6.04c, convert text descriptor values
        convertTextDescriptors = version.compareTo(Version.parseVersion("6.04c")) < 0;

        // for versions 6.05x and later, always have text descriptor values
        alwaysTextDescriptors = version.compareTo(Version.parseVersion("6.05x")) >= 0;

        // for Electric version 4 or earlier, scale lambda by 20
        scaleLambdaBy20 = version.compareTo(Version.parseVersion("5")) < 0;

        // mirror bits
        rotationMirrorBits = version.compareTo(Version.parseVersion("7.01")) >= 0;

        // get the newly created views (version 9 and later)
        viewMapping = new HashMap<Integer, View>();
        viewMapping.put(new Integer(-1), View.UNKNOWN);
        viewMapping.put(new Integer(-2), View.LAYOUT);
        viewMapping.put(new Integer(-3), View.SCHEMATIC);
        viewMapping.put(new Integer(-4), View.ICON);
        viewMapping.put(new Integer(-5), View.DOCWAVE);
        viewMapping.put(new Integer(-6), View.LAYOUTSKEL);
        viewMapping.put(new Integer(-7), View.VHDL);
        viewMapping.put(new Integer(-8), View.NETLIST);
        viewMapping.put(new Integer(-9), View.DOC);
        viewMapping.put(new Integer(-10), View.NETLISTNETLISP);
        viewMapping.put(new Integer(-11), View.NETLISTALS);
        viewMapping.put(new Integer(-12), View.NETLISTQUISC);
        viewMapping.put(new Integer(-13), View.NETLISTRSIM);
        viewMapping.put(new Integer(-14), View.NETLISTSILOS);
        viewMapping.put(new Integer(-15), View.VERILOG);
        viewMapping.put(new Integer(-16), View.LAYOUTCOMP);
        if (header.magic <= ELIBConstants.MAGIC9) {
            int numExtraViews = readBigInteger();
            for (int i = 0; i < numExtraViews; i++) {
                String viewName = readString();
                String viewShortName = readString();
                View view = View.findView(viewName);
                if (view == null) {
                    // special conversion from old view names
                    view = findOldViewName(viewName);
                    if (view == null && !onlyProjectSettings) {
                        view = View.newInstance(viewName, viewShortName);
                        if (view == null) {
                            return true;
                        }
                    }
                }
                viewMapping.put(new Integer(i + 1), view);
            }
        }

        // get the number of toolbits to ignore
        if (header.magic <= ELIBConstants.MAGIC3 && header.magic >= ELIBConstants.MAGIC6) {
            // versions 3, 4, 5, and 6 find this in the file
            toolBCount = readBigInteger();
        } else {
            // versions 1 and 2 compute this (versions 7 and later ignore it)
            toolBCount = toolCount;
        }

        // allocate pointers for the Technologies
        techIdList = new TechId[techCount];
        techList = new Technology[techCount];
        techError = new String[techCount];
        techVars = new Variable[techCount][];
        techLambda = new int[techCount];
        arcProtoIdList = new ArcProtoId[arcProtoCount];
        arcProtoList = new ArcProto[arcProtoCount];
        arcProtoError = new String[arcProtoCount];
        primitiveNodeIdList = new PrimitiveNodeId[primNodeProtoCount];
        primNodeProtoList = new PrimitiveNode[primNodeProtoCount];
        primNodeProtoError = new boolean[primNodeProtoCount];
        primNodeProtoOrig = new String[primNodeProtoCount];
        primNodeProtoTech = new int[primNodeProtoCount];
        primitivePortIdList = new PrimitivePortId[primPortProtoCount];
        primPortProtoList = new PrimitivePort[primPortProtoCount];
        primPortProtoError = new String[primPortProtoCount];

        // allocate pointers for the Tools
        toolList = new Tool[toolCount];
        toolError = new String[toolCount];
        toolVars = new Variable[toolCount][];

        // allocate pointers for the Cells
        nodeProtoList = new Cell[nodeProtoCount];
        cellProtoName = new String[nodeProtoCount];
        cellCreationDate = new int[nodeProtoCount];
        cellRevisionDate = new int[nodeProtoCount];
        cellUserBits = new int[nodeProtoCount];
        cellVars = new Variable[nodeProtoCount][];
        cellLowX = new int[nodeProtoCount];
        cellHighX = new int[nodeProtoCount];
        cellLowY = new int[nodeProtoCount];
        cellHighY = new int[nodeProtoCount];
        cellLibraryPath = new String[nodeProtoCount];
        nodeCounts = new int[nodeProtoCount];
        firstNodeIndex = new int[nodeProtoCount + 1];
        arcCounts = new int[nodeProtoCount];
        firstArcIndex = new int[nodeProtoCount + 1];
        portCounts = new int[nodeProtoCount];
        firstPortIndex = new int[nodeProtoCount];
        cellLambda = new double[nodeProtoCount];
        cellXOff = new int[nodeProtoCount];
        cellYOff = new int[nodeProtoCount];
        cellNextInCellGroup = new int[nodeProtoCount];
        xLibRefSatisfied = new boolean[nodeProtoCount];
        Arrays.fill(cellNextInCellGroup, -1);

        // allocate pointers for the NodeInsts
        boolean hasAnchor = header.magic <= ELIBConstants.MAGIC13;
        nodeInstList = new LibraryFiles.NodeInstList(nodeCount, hasAnchor);
        nodeTypeList = new int[nodeCount];

        // allocate pointers for the ArcInsts
        arcList = new ArcInst[arcCount];
        arcTypeList = new int[arcCount];
        arcNameList = new String[arcCount];
        arcNameDescriptorList = new TextDescriptor[arcCount];
        arcWidthList = new int[arcCount];
        arcHeadXPosList = new int[arcCount];
        arcHeadYPosList = new int[arcCount];
        arcHeadNodeList = new int[arcCount];
        arcHeadPortList = new int[arcCount];
        arcTailXPosList = new int[arcCount];
        arcTailYPosList = new int[arcCount];
        arcTailNodeList = new int[arcCount];
        arcTailPortList = new int[arcCount];
        arcUserBits = new int[arcCount];
        arcVariables = new Variable[arcCount][];
        for (int i = 0; i < arcCount; i++) {
            arcHeadNodeList[i] = -1;
            arcHeadPortList[i] = -1;
            arcTailNodeList[i] = -1;
            arcTailPortList[i] = -1;
            arcNameList[i] = null;
            arcUserBits[i] = 0;
        }

        // allocate pointers for the Exports
        exportList = new Object[exportCount];
        exportSubNodeList = new int[exportCount];
        exportSubPortList = new int[exportCount];
        exportNameList = new String[exportCount];
        exportNameDescriptors = new TextDescriptor[exportCount];
        exportUserbits = new int[exportCount];
        exportVariables = new Variable[exportCount][];

        // versions 9 to 11 allocate fake-cell pointers
        if (header.magic <= ELIBConstants.MAGIC9 && header.magic >= ELIBConstants.MAGIC11) {
            fakeCellList = new FakeCell[cellCount];
            for (int i = 0; i < cellCount; i++) {
                fakeCellList[i] = new FakeCell();
            }
        }

        // versions 4 and earlier allocate geometric pointers
        if (header.magic > ELIBConstants.MAGIC5) {
            geomType = new boolean[geomCount];
            geomMoreUp = new int[geomCount];
        }

        // get number of arcinsts and nodeinsts in each cell
        if (header.magic != ELIBConstants.MAGIC1) {
            // versions 2 and later find this in the file
            int nodeInstPos = 0, arcInstPos = 0, portProtoPos = 0;
            for (int i = 0; i < nodeProtoCount; i++) {
                arcCounts[i] = readBigInteger();
                nodeCounts[i] = readBigInteger();
                portCounts[i] = readBigInteger();

                // the arc and node counts are negative for external cell references
                if (arcCounts[i] > 0 || nodeCounts[i] > 0) {
                    arcInstPos += arcCounts[i];
                    nodeInstPos += nodeCounts[i];
                }
                portProtoPos += portCounts[i];
            }

            // verify that the number of node instances is equal to the total in the file
            if (nodeInstPos != nodeCount) {
                System.out.println("Error: cells have " + nodeInstPos + " nodes but library has " + nodeCount);
                return true;
            }
            if (arcInstPos != arcCount) {
                System.out.println("Error: cells have " + arcInstPos + " arcs but library has " + arcCount);
                return true;
            }
            if (portProtoPos != exportCount) {
                System.out.println("Error: cells have " + portProtoPos + " ports but library has " + exportCount);
                return true;
            }
        } else {
            // version 1 computes this information
            arcCounts[0] = arcCount;
            nodeCounts[0] = nodeCount;
            portCounts[0] = exportCount;
            for (int i = 1; i < nodeProtoCount; i++) {
                arcCounts[i] = nodeCounts[i] = portCounts[i] = 0;
            }
        }

//		// allocate all cells in the library
//		for(int i=0; i<nodeProtoCount; i++)
//		{
//			if (arcCounts[i] < 0 && nodeCounts[i] < 0)
//			{
//				// this cell is from an external library
//				nodeProtoList[i] = null;
//				xLibRefSatisfied[i] = false;
//			} else
//			{
//				nodeProtoList[i] = Cell.lowLevelAllocate(lib);
//				if (nodeProtoList[i] == null) return true;
//				xLibRefSatisfied[i] = true;
//			}
//		}

        // setup pointers for technology and primitive ids
        IdManager idManager = fc != null ? fc.idManager() : this.idManager;
        primNodeProtoCount = 0;
        primPortProtoCount = 0;
        arcProtoCount = 0;
        for (int techIndex = 0; techIndex < techCount; techIndex++) {
            // get the technology
            String name = readString();
            TechId techId = idManager.newTechId(name);
            techIdList[techIndex] = techId;

            // get the number of primitive node prototypes
            int numPrimNodes = readBigInteger();
            for (int j = 0; j < numPrimNodes; j++) {
                name = readString();
                PrimitiveNodeId primitiveNodeId = techId.newPrimitiveNodeId(name);
                primitiveNodeIdList[primNodeProtoCount] = primitiveNodeId;
                primNodeProtoTech[primNodeProtoCount] = techIndex;

                // get the number of primitive port prototypes
                int numPrimPorts = readBigInteger();
                for (int i = 0; i < numPrimPorts; i++) {
                    name = readString();
                    PrimitivePortId primitivePortId = primitiveNodeId.newPortId(name);
                    primitivePortIdList[primPortProtoCount++] = primitivePortId;
                }
                primNodeProtoCount++;
            }

            // get the number of arc prototypes
            int numArcProtos = readBigInteger();
            for (int j = 0; j < numArcProtos; j++) {
                name = readString();
                ArcProtoId arcProtoId = techId.newArcProtoId(name);
                arcProtoIdList[arcProtoCount++] = arcProtoId;
            }
        }

        // setup pointers for tools
        for (int i = 0; i < toolCount; i++) {
            String name = readString();
            toolError[i] = null;
            Tool t = Tool.findTool(name);
            if (t == null) {
                toolError[i] = name;
            }
            toolList[i] = t;
        }
        if (header.magic <= ELIBConstants.MAGIC3 && header.magic >= ELIBConstants.MAGIC6) {
            // versions 3, 4, 5, and 6 must ignore toolbits associations
            for (int i = 0; i < toolBCount; i++) {
                readString();
            }
        }

        // get the library userbits
        if (header.magic <= ELIBConstants.MAGIC7) {
            // version 7 and later simply read the relevant data
            libUserBits = readBigInteger();
        } else {
            // version 6 and earlier must sift through the information
            if (toolBCount >= 1) {
                libUserBits = readBigInteger();
            }
            for (int i = 1; i < toolBCount; i++) {
                readBigInteger();
            }
        }

        // get the lambda values in the library
        for (int i = 0; i < techCount; i++) {
            techLambda[i] = readBigInteger();
        }

        // read the global namespace
        readNameSpace();

        // read the library variables
        libVars = readVariables();
        for (int i = 0; i < libVars.length; i++) {
            Variable var = libVars[i];
            if (var == null || var.getKey() != Library.FONT_ASSOCIATIONS) {
                continue;
            }
            Object value = var.getObject();
            if (!(value instanceof String[])) {
                continue;
            }
            setFontNames((String[]) value);
            libVars[i] = null;
        }

        // read the tool variables
        for (int i = 0; i < toolCount; i++) {
            toolVars[i] = readVariables();
        }

        // read the technology variables
        for (int i = 0; i < techCount; i++) {
            techVars[i] = readVariables();
        }

        // read the arcproto variables
        for (int i = 0; i < arcProtoCount; i++) {
            readVariables();
        }

        // read the primitive nodeproto variables
        for (int i = 0; i < primNodeProtoCount; i++) {
            readVariables();
        }

        // read the primitive portproto variables
        for (int i = 0; i < primPortProtoCount; i++) {
            readVariables();
        }

        // read the view variables (version 9 and later)
        if (header.magic <= ELIBConstants.MAGIC9) {
            int count = readBigInteger();
            for (int i = 0; i < count; i++) {
                int j = readBigInteger();
                View v = getView(j);
                if (v == null) {
                    System.out.println("View index " + j + " not found in ELIB:readTheLibrary()");
                }
                readVariables();
            }
        }

        // setup fake cell structures (version 9 to 11)
        if (header.magic <= ELIBConstants.MAGIC9 && header.magic >= ELIBConstants.MAGIC11) {
            for (int i = 0; i < cellCount; i++) {
                String thecellname = readString();
                readVariables();

                fakeCellList[i].cellName = convertCellName(thecellname);
            }
        }

        // read the cells
        exportIndex = 0;
        for (int i = 0; i < nodeProtoCount; i++) {
            if (arcCounts[i] < 0 && nodeCounts[i] < 0) {
                continue;
            }
            if (readNodeProto(i)) {
                System.out.println("Error reading cell");
                return true;
            }
        }

        // now read external cells
        for (int i = 0; i < nodeProtoCount; i++) {
            if (arcCounts[i] >= 0 || nodeCounts[i] >= 0) {
                continue;
            }
            if (readExternalNodeProto(i)) {
                System.out.println("Error reading external cell");
                return true;
            }
        }

        if (fc != null) {
            for (int cellIndex = 0; cellIndex < nodeProtoCount; cellIndex++) {
                if (arcCounts[cellIndex] >= 0 || nodeCounts[cellIndex] >= 0) {
                    fc.localCells.add(cellProtoName[cellIndex]);
                } else {
                    fc.externalCells.add(new LibraryStatistics.ExternalCell(cellLibraryPath[cellIndex], null, cellProtoName[cellIndex]));
                }

            }
            return false;
        }
        // read the cell contents: arcs and nodes
        int nodeIndex = 0, arcIndex = 0, geomIndex = 0;
        for (int cellIndex = 0; cellIndex < nodeProtoCount; cellIndex++) {
            Cell cell = nodeProtoList[cellIndex];
            firstNodeIndex[cellIndex] = nodeIndex;
            firstArcIndex[cellIndex] = arcIndex;
            if (header.magic > ELIBConstants.MAGIC5) {
                // versions 4 and earlier must read some geometric information
                int j = geomIndex;
                readGeom(geomType, geomMoreUp, j);
                j++;
                readGeom(geomType, geomMoreUp, j);
                j++;
                int top = j;
                readGeom(geomType, geomMoreUp, j);
                j++;
                int bot = j;
                readGeom(geomType, geomMoreUp, j);
                j++;
                for (;;) {
                    readGeom(geomType, geomMoreUp, j);
                    j++;
                    if (geomMoreUp[j - 1] == top) {
                        break;
                    }
                }
                geomIndex = j;
                for (int look = bot; look != top; look = geomMoreUp[look]) {
                    if (!geomType[look]) {
                        if (readArcInst(arcIndex)) {
                            System.out.println("Error reading arc");
                            Input.errorLogger.logError("Error reading arc index " + arcIndex, cell, 1);
                            return true;
                        }
                        arcIndex++;
                    } else {
                        if (readNodeInst(nodeIndex, cellIndex)) {
                            System.out.println("Error reading node");
                            Input.errorLogger.logError("Error reading node index " + nodeIndex, cell, 1);
                            return true;
                        }
                        nodeIndex++;
                    }
                }
            } else {
                // version 5 and later find the arcs and nodes in linear order
                for (int j = 0; j < arcCounts[cellIndex]; j++) {
                    if (readArcInst(arcIndex)) {
                        System.out.println("Error reading arc");
                        Input.errorLogger.logError("Error reading arc index " + arcIndex, cell, 1);
                        return true;
                    }
                    arcIndex++;
                }
                for (int j = 0; j < nodeCounts[cellIndex]; j++) {
                    if (readNodeInst(nodeIndex, cellIndex)) {
                        System.out.println("Error reading node index " + nodeIndex + " in " + cell + " of " + lib);
                        Input.errorLogger.logError("Error reading node index " + nodeIndex + " in " + cell + " of " + lib, cell, 1);
                        return true;
                    }
                    nodeIndex++;
                }
            }
        }
        firstNodeIndex[nodeProtoCount] = nodeIndex;
        firstArcIndex[nodeProtoCount] = arcIndex;

        // library read successfully
        if (LibraryFiles.VERBOSE) {
            System.out.println("Binary: finished reading data for " + lib);
        }
        return false;
    }

    @Override
    Map<Cell, Variable[]> createLibraryCells(boolean onlyProjectSettings, EditingPreferences ep) {
        // setup pointers for technologies and primitives
        primNodeProtoCount = 0;
        primPortProtoCount = 0;
        arcProtoCount = 0;
        for (int techIndex = 0; techIndex < techCount; techIndex++) {
            // get the technology
            TechId techId = techIdList[techIndex];
            String name = techId.techName;
            Technology tech = findTechnologyName(name);
            boolean imosconv = false;
            if (name.equals("imos")) {
                tech = Technology.getMocmosTechnology();
                imosconv = true;
            }
            if (tech == null) {
                // cannot figure it out: just pick the generic technology
                tech = Generic.tech();
                techError[techIndex] = name;
            } else {
                techError[techIndex] = null;
            }
            techList[techIndex] = tech;

            // get the number of primitive node prototypes
            while (primNodeProtoCount < primitiveNodeIdList.length) {
                PrimitiveNodeId primitiveNodeId = primitiveNodeIdList[primNodeProtoCount];
                if (primitiveNodeId.techId != techId) {
                    break;
                }

                primNodeProtoOrig[primNodeProtoCount] = null;
                primNodeProtoError[primNodeProtoCount] = false;
                name = primitiveNodeId.name;
                if (imosconv) {
                    name = name.substring(6);
                }
                PrimitiveNode pnp = tech.findNodeProto(name);
                if (pnp == null) {
                    // automatic conversion of "Active-Node" in to "P-Active-Node" (MOSIS CMOS)
                    if (name.equals("Active-Node")) {
                        pnp = tech.findNodeProto("P-Active-Node");
                    }
                }
                if (pnp == null) {
                    boolean advise = true;

                    // look for substring name match at start of name
                    for (Iterator<PrimitiveNode> it = tech.getNodes(); it.hasNext();) {
                        PrimitiveNode opnp = it.next();
                        String primName = opnp.getName();
                        if (primName.startsWith(name) || name.startsWith(primName)) {
                            pnp = opnp;
                            break;
                        }
                    }

                    // look for substring match at end of name
                    if (pnp == null) {
                        for (Iterator<PrimitiveNode> it = tech.getNodes(); it.hasNext();) {
                            PrimitiveNode opnp = it.next();
                            String primName = opnp.getName();
                            if (primName.endsWith(name) || name.endsWith(primName)) {
                                pnp = opnp;
                                break;
                            }
                        }
                    }

                    // special cases: convert special primitives that are known to the technologies
                    if (pnp == null) {
                        pnp = tech.convertOldNodeName(name);
                        if (pnp != null) {
                            advise = false;
                        }
                    }

                    // give up and use first primitive in this technology
                    if (pnp == null) {
                        Iterator<PrimitiveNode> it = tech.getNodes();
                        pnp = it.next();
                    }

                    // construct the error message
                    if (advise) {
                        String errorMessage;
                        if (techError[techIndex] != null) {
                            errorMessage = techError[techIndex];
                        } else {
                            errorMessage = tech.getTechName();
                        }
                        errorMessage += ":" + name;
                        primNodeProtoOrig[primNodeProtoCount] = errorMessage;
                        primNodeProtoError[primNodeProtoCount] = true;
                    }
                }
                primNodeProtoTech[primNodeProtoCount] = techIndex;
                primNodeProtoList[primNodeProtoCount] = pnp;

                // get the number of primitive port prototypes
                while (primPortProtoCount < primitivePortIdList.length) {
                    PrimitivePortId primitivePortId = primitivePortIdList[primPortProtoCount];
                    if (primitivePortId.parentId != primitiveNodeId) {
                        break;
                    }

                    primPortProtoError[primPortProtoCount] = null;
                    name = primitivePortId.externalId;
                    PrimitivePort pp = (PrimitivePort) pnp.findPortProto(name);

                    // convert special port names
                    if (pp == null) {
                        pp = tech.convertOldPortName(name, pnp);
                    }

                    if (pp == null) {
                        Iterator<PrimitivePort> it = pnp.getPrimitivePorts();
                        if (it.hasNext()) {
                            pp = it.next();
                            if (!primNodeProtoError[primNodeProtoCount]) {
                                String errorMessage = name + " on ";
                                if (primNodeProtoOrig[primNodeProtoCount] != null) {
                                    errorMessage += primNodeProtoOrig[primNodeProtoCount];
                                } else {
                                    if (techError[techIndex] != null) {
                                        errorMessage += techError[techIndex];
                                    } else {
                                        errorMessage += tech.getTechName();
                                    }
                                    errorMessage += ":" + pnp.getName();
                                }
                                primPortProtoError[primPortProtoCount] = errorMessage;
                            }
                        }
                    }
                    primPortProtoList[primPortProtoCount++] = pp;
                }
                primNodeProtoCount++;
            }

            // get the number of arc prototypes
            while (arcProtoCount < arcProtoIdList.length) {
                ArcProtoId arcProtoId = arcProtoIdList[arcProtoCount];
                if (arcProtoId.techId != techId) {
                    break;
                }

                arcProtoError[arcProtoCount] = null;
                name = arcProtoId.name;
                if (imosconv) {
                    name = name.substring(6);
                }
                ArcProto ap = tech.findArcProto(name);
                if (ap == null) {
                    ap = tech.convertOldArcName(name);
                }
                if (ap == null) {
                    Iterator<ArcProto> it = tech.getArcs();
                    ap = it.next();
                    String errorMessage;
                    if (techError[techIndex] != null) {
                        errorMessage = techError[techIndex];
                    } else {
                        errorMessage = tech.getTechName();
                    }
                    errorMessage += ":" + name;
                    arcProtoError[arcProtoCount] = errorMessage;
                }
                arcProtoList[arcProtoCount++] = ap;
            }
        }

        // get the lambda values in the library
        for (int i = 0; i < techCount; i++) {
            int lambda = techLambda[i];
            if (techError[i] != null) {
                continue;
            }
            Technology tech = techList[i];

            // for Electric version 4 or earlier, scale lambda by 20
            if (scaleLambdaBy20) {
                lambda *= 20;
            }

            techScale.put(tech, Double.valueOf(lambda));
            String varName = tech.getScaleVariableName();
            Variable var = Variable.newInstance(Variable.newKey(varName), new Double(lambda / 2), TextDescriptor.EMPTY);
            realizeMeaningPrefs(tech, new Variable[]{var});
        }

        // read the tool variables
        for (int i = 0; i < toolCount; i++) {
            Tool tool = toolList[i];
            if (tool != null) {
                realizeMeaningPrefs(tool, toolVars[i]);
            }
        }

        // read the technology variables
        for (int i = 0; i < techCount; i++) {
            Technology tech = techList[i];
            if (tech != null) {
                realizeMeaningPrefs(tech, techVars[i]);
            }
//				getTechList(i);
        }

        if (onlyProjectSettings) {
            return null;
        }

        // erase the current database
        lib.erase();

        lib.lowLevelSetUserBits(libUserBits);
        lib.setFromDisk();
        lib.setVersion(version);
        realizeVariables(lib, libVars);

        // read the cells
        HashMap<Cell, Variable[]> originalVars = new HashMap<Cell, Variable[]>();
        for (int i = 0; i < nodeProtoCount; i++) {
            if (arcCounts[i] < 0 && nodeCounts[i] < 0) {
                continue;
            }
            xLibRefSatisfied[i] = true;
            realizeNodeProto(i, originalVars);
        }

        // collect the cells by common protoName and by "nextInCellGroup" relation
        TransitiveRelation<Object> transitive = new TransitiveRelation<Object>();
        HashMap<String, String> protoNames = new HashMap<String, String>();
        for (int cellIndex = 0; cellIndex < nodeProtoCount; cellIndex++) {
            Cell cell = nodeProtoList[cellIndex];
            if (cell == null || cell.getLibrary() != lib) {
                continue;
            }
            String protoName = protoNames.get(cell.getName());
            if (protoName == null) {
                protoName = cell.getName();
                protoNames.put(protoName, protoName);
            }
            transitive.theseAreRelated(cell, protoName);
            Cell otherCell = null;
            int nextInCell = cellNextInCellGroup[cellIndex];
            if (nextInCell >= 0) {
                otherCell = nodeProtoList[nextInCell];
            }
            if (otherCell != null && cell.getLibrary() == lib) {
                transitive.theseAreRelated(cell, otherCell);
            }
        }

//		// link the cells
//		for(int cellIndex=0; cellIndex<nodeProtoCount; cellIndex++)
//		{
//			Cell cell = nodeProtoList[cellIndex];
//			if (cell == null) continue;
//			cell.lowLevelLink();
//		}

        // join the cell groups
        for (Iterator<Set<Object>> git = transitive.getSetsOfRelatives(); git.hasNext();) {
            Set<Object> group = git.next();
            Cell firstCell = null;
            for (Object o : group) {
                if (!(o instanceof Cell)) {
                    continue;
                }
                Cell cell = (Cell) o;
                if (firstCell == null) {
                    firstCell = cell;
                } else {
                    cell.joinGroup(firstCell);
                }
            }
        }

        // now read external cells
        for (int i = 0; i < nodeProtoCount; i++) {
            Cell cell = nodeProtoList[i];
            if (cell != null) {
                continue;
            }
            realizeExternalNodeProto(lib, i);
        }

        // warn if any dummy cells were read in
        for (Iterator<Cell> it = lib.getCells(); it.hasNext();) {
            Cell c = it.next();
            if (c.getVar(IO_DUMMY_OBJECT) != null) {
                System.out.println("WARNING: " + lib + " contains DUMMY cell " + c.noLibDescribe());
            }
        }
        return originalVars;
    }

    @Override
    Variable[] findVarsOnExampleIcon(Cell parentCell, Cell iconCell) {
        // get information about this cell
        int cellIndex;
        for (cellIndex = 0; cellIndex < nodeProtoList.length; cellIndex++) {
            if (nodeProtoList[cellIndex] == parentCell) {
                break;
            }
        }
        if (cellIndex + 1 >= firstNodeIndex.length) {
            return null;
        }
        int startNode = firstNodeIndex[cellIndex];
        int endNode = firstNodeIndex[cellIndex + 1];

        for (int i = startNode; i < endNode; i++) {
            NodeProto np = convertNodeProto(nodeTypeList[i]);
            if (np == iconCell) {
                return nodeInstList.vars[i];
            }
        }
        return null;
    }

    // *************************** THE CELL CLEANUP INTERFACE ***************************
    /**
     * Method to recursively create the contents of each cell in the library.
     */
    protected void realizeCellsRecursively(Cell cell, HashSet<Cell> recursiveSetupFlag, HashSet<Cell> patchedCells, String scaledCellName, double scale) {
        // do not realize cross-library references
        if (cell.getLibrary() != lib) {
            return;
        }

        // skip if dummy cell, already created
        boolean dummyCell = (cell.getVar(IO_DUMMY_OBJECT) != null) ? true : false;
        if (dummyCell) {
            return;
        }

        // get information about this cell
        int cellIndex = cell.getTempInt();
        if (cellIndex + 1 >= firstNodeIndex.length) {
            return;
        }
        int startNode = firstNodeIndex[cellIndex];
        int endNode = firstNodeIndex[cellIndex + 1];

        // recursively ensure that external library references are satisfied
        for (int i = startNode; i < endNode; i++) {
            NodeProto np = nodeInstList.protoType[i];
            if (np instanceof PrimitiveNode) {
                continue;
            }
            Cell subCell = (Cell) np;
            if (subCell.getLibrary() == lib) {
                continue;
            }

            // subcell: make sure that cell is setup
            for (int cI = 0; cI < nodeProtoCount; cI++) {
                if (nodeProtoList[cI] != subCell) {
                    continue;
                }
                if (xLibRefSatisfied[cI]) {
                    break;
                }

                // make sure that cell is properly built
                if (!recursiveSetupFlag.contains(subCell)) {
                    LibraryFiles reader = getReaderForLib(subCell.getLibrary());
                    if (reader != null) {
                        reader.realizeCellsRecursively(subCell, recursiveSetupFlag, patchedCells, null, 0);
                    }
                }

                int startPort = firstPortIndex[cI];
                int endPort = startPort + portCounts[cI];
                for (int j = startPort; j < endPort; j++) {
                    Object obj = exportList[j];
                    Export pp = null;
                    Cell otherCell = null;
                    if (obj instanceof Cell) {
                        otherCell = (Cell) obj;
                        pp = (Export) findPortProto(otherCell, exportNameList[j]);
//						pp = otherCell.findExport(exportNameList[j]);
                        if (pp != null) {
                            exportList[j] = pp;
                        }
                    }
                }
                xLibRefSatisfied[cI] = true;
                break;
            }
        }

        // recursively scan the nodes to the bottom and only proceed when everything below is built
        scanNodesForRecursion(cell, recursiveSetupFlag, patchedCells, nodeInstList.protoType, startNode, endNode);

        // report progress
        if (LibraryFiles.VERBOSE) {
            if (scaledCellName == null) {
                System.out.println("Binary: Doing contents of " + cell + " in " + lib);
            } else {
                System.out.println("Binary: Scaling (by " + scale + ") contents of " + cell + " in " + lib);
            }
        }
        cellsConstructed++;
        setProgressValue(cellsConstructed * 100 / totalCells);
//		if (progress != null) progress.setProgress(cellsConstructed * 100 / totalCells);

        double lambda = cellLambda[cellIndex];

        // if scaling, actually construct the cell
        if (scaledCellName != null) {
            Cell oldCell = cell;
            cell = Cell.newInstance(cell.getLibrary(), scaledCellName);
            cell.setTempInt(cellIndex);
            recursiveSetupFlag.add(cell);
            cell.joinGroup(oldCell);
            scaledCells.add(cell);

            lambda /= scale;
        } else {
            scale = 1;
        }

        // finish initializing the NodeInsts in the cell: start with the cell-center
        int xoff = 0, yoff = 0;
        for (int i = startNode; i < endNode; i++) {
            NodeProto np = nodeInstList.protoType[i];
            if (np == Generic.tech().cellCenterNode) {
                realizeNode(nodeInstList, i, xoff, yoff, lambda, cell, np);
                xoff = (nodeInstList.lowX[i] + nodeInstList.highX[i]) / 2;
                yoff = (nodeInstList.lowY[i] + nodeInstList.highY[i]) / 2;
                break;
            }
        }
        cellXOff[cellIndex] = xoff;
        cellYOff[cellIndex] = yoff;

        // finish creating the rest of the NodeInsts
        for (int i = startNode; i < endNode; i++) {
            NodeProto np = nodeInstList.protoType[i];
            if (np == Generic.tech().cellCenterNode) {
                continue;
            }
            if (np instanceof Cell) {
                np = scaleCell(i, lambda, cell, recursiveSetupFlag, patchedCells);
            }
            realizeNode(nodeInstList, i, xoff, yoff, lambda, cell, np);
        }

        // do the exports now
        realizeExports(cell, cellIndex, scaledCellName);

        // do the arcs now
        realizeArcs(cell, cellIndex, scaledCellName, scale);

//		// restore the node pointers if this was a scaled cell construction
//		if (scaledCellName != null)
//		{
//			int j = 0;
//			for(int i=startNode; i<endNode; i++)
//				nodeInstList.theNode[i] = oldNodes[j++];
//		}
//        cell.loadExpandStatus();
    }

    protected boolean spreadLambda(Cell cell, int cellIndex) {
        boolean changed = false;
        int startNode = firstNodeIndex[cellIndex];
        int endNode = firstNodeIndex[cellIndex + 1];
        double thisLambda = cellLambda[cellIndex];
        for (int i = startNode; i < endNode; i++) {
            NodeProto np = nodeInstList.protoType[i];
            if (np instanceof PrimitiveNode) {
                continue;
            }
            Cell subCell = (Cell) np;

            // ignore dummy cells, they are created in the default lambda
            if (subCell.getVar(IO_DUMMY_OBJECT) != null) {
                continue;
            }

            LibraryFiles reader = this;
            if (subCell.getLibrary() != lib) {
                reader = getReaderForLib(subCell.getLibrary());
                if (reader == null) {
                    continue;
                }
            }
            int subCellIndex = subCell.getTempInt();
//			if (subCellIndex < 0 || subCellIndex >= reader.cellLambda.length)
//			{
//				System.out.println("Index is "+subCellIndex+" but limit is "+reader.cellLambda.length);
//				continue;
//			}
            double subLambda = reader.cellLambda[subCellIndex];
            if (subLambda < thisLambda && cell.isSchematic() && subCell.isIcon()) {
                reader.cellLambda[subCellIndex] = thisLambda;
                changed = true;
            }
        }
        return changed;
    }

    protected void computeTech(Cell cell, Set uncomputedCells) {
        uncomputedCells.remove(cell);
        int cellIndex = 0;
        for (; cellIndex < nodeProtoCount && nodeProtoList[cellIndex] != cell; cellIndex++);
        if (cellIndex >= nodeProtoCount) {
            return;
        }

        int startNode = firstNodeIndex[cellIndex];
        int endNode = firstNodeIndex[cellIndex + 1];

        // recursively ensure that subcells's technologies are computed
        for (int i = startNode; i < endNode; i++) {
            NodeProto np = convertNodeProto(nodeTypeList[i]);
            nodeInstList.protoType[i] = np;
            if (!uncomputedCells.contains(np)) {
                continue;
            }
            Cell subCell = (Cell) np;
            LibraryFiles reader = getReaderForLib(subCell.getLibrary());
            if (reader != null) {
                reader.computeTech(subCell, uncomputedCells);
            }
        }

        int startArc = firstArcIndex[cellIndex];
        int endArc = firstArcIndex[cellIndex + 1];
        ArcProto[] arcTypes = new ArcProto[endArc - startArc];
        for (int i = 0; i < arcTypes.length; i++) {
            arcTypes[i] = convertArcProto(arcTypeList[i]);
        }

        Technology cellTech = Technology.whatTechnology(cell, nodeInstList.protoType, startNode, endNode, arcTypes);
        cell.setTechnology(cellTech);
    }

    protected double computeLambda(Cell cell, int cellIndex) {
//		double lambda = 1.0;
// 		int startNode = firstNodeIndex[cellIndex];
// 		int endNode = firstNodeIndex[cellIndex+1];
// 		int startArc = firstArcIndex[cellIndex];
// 		int endArc = firstArcIndex[cellIndex+1];
// 		Technology cellTech = Technology.whatTechnology(cell, nodeInstList.protoType, startNode, endNode, arcTypeList, startArc, endArc);
// 		cell.setTechnology(cellTech);
        Technology cellTech = cell.getTechnology();
        return cellTech != null ? getScale(cellTech) : 1.0;
    }

    private double getScale(Technology tech) {
        Double scale = techScale.get(tech);
        return scale != null ? scale : tech.getScale();
    }

    protected boolean canScale() {
        return true;
    }

    private void realizeExports(Cell cell, int cellIndex, String scaledCellName) {
        // finish initializing the Exports in the cell
        int startPort = firstPortIndex[cellIndex];
        int endPort = startPort + portCounts[cellIndex];
        CellId cellId = cell.getId();

        // Try to create ExportIds in alphanumeric order
        TreeSet<String> exportNames = new TreeSet<String>(TextUtils.STRING_NUMBER_ORDER);
        for (int i = startPort; i < endPort; i++) {
            exportNames.add(exportNameList[i]);
        }
        for (String exportName : exportNames) {
            cellId.newPortId(exportName);
        }

        for (int i = startPort; i < endPort; i++) {
//			if (exportList[i] instanceof Cell)
//			{
//				Cell otherCell = (Cell)exportList[i];
//				Export pp = otherCell.findExport(exportNameList[i]);
//				if (pp != null) exportList[i] = pp;
//			}
//			if (!(exportList[i] instanceof Export))
//            {
//                // could be missing because this is a dummy cell
//                if (cell.getVar(IO_DUMMY_OBJECT) != null)
//                    continue;               // don't issue error message
//                // not on a dummy cell, issue error message
//                System.out.println("ERROR: Cell "+cell.describe() + ": export " + exportNameList[i] + " is unresolved");
//                continue;
//			}
            String exportName = exportNameList[i];
            int nodeIndex = exportSubNodeList[i];
            if (nodeIndex < 0) {
                System.out.println("ERROR: " + cell + ": cannot find the node on which export " + exportName + " resides");
                continue;
            }
            NodeInst subNodeInst = nodeInstList.theNode[nodeIndex];
            PortProto subPortProto = convertPortProto(exportSubPortList[i]);
//			Object o = exportSubPortList[i];
//			if (exportSubPortList[i] instanceof Integer)
//			{
//				// this was an external reference that couldn't be resolved yet.  Do it now
//				int index = ((Integer)exportSubPortList[i]).intValue();
//				exportSubPortList[i] = convertPortProto(index);
//			}
//			PortProto subPortProto = (PortProto)exportSubPortList[i];

            // null entries happen when there are external cell references
            if (subNodeInst == null || subPortProto == null || subNodeInst.getParent() != cell || subNodeInst.getProto() != subPortProto.getParent()) {
                String msg = "ERROR: " + cell + ": export " + exportNameList[i] + " could not be created";
                System.out.println(msg);
                Input.errorLogger.logError(msg, cell, 1);
                continue;
            }
            if (subNodeInst.getProto() == null) {
                String msg = "ERROR: " + cell + ": export " + exportNameList[i] + " could not be created...proto bad!";
                System.out.println(msg);
                Input.errorLogger.logError(msg, cell, 1);
                continue;
            }

            // convert portproto to portinst
            PortInst pi = subNodeInst.findPortInst(subPortProto.getName());
            boolean alwaysDrawn = ImmutableExport.alwaysDrawnFromElib(exportUserbits[i]);
            boolean bodyOnly = ImmutableExport.bodyOnlyFromElib(exportUserbits[i]);
            PortCharacteristic characteristic = ImmutableExport.portCharacteristicFromElib(exportUserbits[i]);
            ExportId exportId = cellId.newPortId(Name.findName(exportName).toString());
            TextDescriptor nameTextDescriptor = exportNameDescriptors[i];
            if (nameTextDescriptor == null) {
                nameTextDescriptor = ep.getExportTextDescriptor();
            }
            Export pp = Export.newInstanceNoIcon(cell, exportId, null, nameTextDescriptor, pi, alwaysDrawn, bodyOnly, characteristic, errorLogger);
            exportList[i] = pp;
            if (pp == null) {
                continue;
            }
            realizeVariables(pp, exportVariables[i]);
        }

//		// convert "ATTRP_" variables on NodeInsts to be on PortInsts
//		int startNode = firstNodeIndex[cellIndex];
//		int endNode = firstNodeIndex[cellIndex+1];
//		for(int i=startNode; i<endNode; i++)
//		{
//			NodeInst ni = nodeInstList.theNode[i];
//			boolean found = true;
//			while (found)
//			{
//				found = false;
//				for(Iterator<Variable> it = ni.getVariables(); it.hasNext(); )
//				{
//					Variable origVar = it.next();
//					Variable.Key origVarKey = origVar.getKey();
//					String origVarName = origVarKey.getName();
//					if (origVarName.startsWith("ATTRP_"))
//					{
//						// the form is "ATTRP_portName_variableName" with "\" escapes
//						StringBuffer portName = new StringBuffer();
//						String varName = null;
//						int len = origVarName.length();
//						for(int j=6; j<len; j++)
//						{
//							char ch = origVarName.charAt(j);
//							if (ch == '\\')
//							{
//								j++;
//								portName.append(origVarName.charAt(j));
//								continue;
//							}
//							if (ch == '_')
//							{
//								varName = origVarName.substring(j+1);
//								break;
//							}
//							portName.append(ch);
//						}
//						if (varName != null)
//						{
//							String thePortName = portName.toString();
//							PortInst pi = ni.findPortInst(thePortName);
//							if (pi != null)
//							{
//								Variable var = pi.newVar(Variable.newKey(varName), origVar.getObject(), origVar.getTextDescriptor());
////								if (var != null)
////								{
////    								if (origVar.isDisplay()) var.setDisplay(true);
////									var.setCode(origVar.getCode());
////									var.setTextDescriptor(origVar.getTextDescriptor());
////								}
//								ni.delVar(origVarKey);
//								found = true;
//								break;
//							}
//						}
//					}
//				}
//			}
//		}
    }

    /**
     * Method to create the ArcInsts in a given cell and it's index in the global lists.
     */
    private void realizeArcs(Cell cell, int cellIndex, String scaledCellName, double scale) {
        double lambda = cellLambda[cellIndex] / scale;
        int xoff = cellXOff[cellIndex];
        int yoff = cellYOff[cellIndex];
//		boolean arcInfoError = false;
        int startArc = firstArcIndex[cellIndex];
        int endArc = firstArcIndex[cellIndex + 1];
        for (int i = startArc; i < endArc; i++) {
            ArcProto ap = convertArcProto(arcTypeList[i]);
            String name = arcNameList[i];
            long gridExtendOverMin = getSizeCorrector(ap.getTechnology()).getExtendFromDisk(ap, arcWidthList[i] / lambda);
            double headX = (arcHeadXPosList[i] - xoff) / lambda;
            double headY = (arcHeadYPosList[i] - yoff) / lambda;
            double tailX = (arcTailXPosList[i] - xoff) / lambda;
            double tailY = (arcTailYPosList[i] - yoff) / lambda;
            if (arcHeadNodeList[i] < 0) {
                System.out.println("ERROR: head of " + ap + " not known");
                continue;
            }
            NodeInst headNode = nodeInstList.theNode[arcHeadNodeList[i]];
            int headPortIntValue = arcHeadPortList[i];
            PortProto headPort = convertPortProto(headPortIntValue);
//			Object headPort = arcHeadPortList[i];
//			int headPortIntValue = -1;
            String headname = "Port name not found";
//			if (headPort instanceof Integer)
//			{
//				// this was an external reference that couldn't be resolved yet.  Do it now
//				headPortIntValue = ((Integer)headPort).intValue();
//				headPort = convertPortProto(headPortIntValue);
//			}
            if (headPort != null) {
                headname = headPort.getName();
            } else {
                if (headPortIntValue >= 0 && headPortIntValue < exportNameList.length) {
                    headname = exportNameList[headPortIntValue];
                }
            }

            if (arcTailNodeList[i] < 0) {
                System.out.println("ERROR: tail of " + ap + " not known");
                continue;
            }
            NodeInst tailNode = nodeInstList.theNode[arcTailNodeList[i]];
            int tailPortIntValue = arcTailPortList[i];
            PortProto tailPort = convertPortProto(tailPortIntValue);
//			Object tailPort = arcTailPortList[i];
//			int tailPortIntValue = -1;
            String tailname = "Port name not found";
//			if (tailPort instanceof Integer)
//			{
//				// this was an external reference that couldn't be resolved yet.  Do it now
//				tailPortIntValue = ((Integer)tailPort).intValue();
//				tailPort = convertPortProto(tailPortIntValue);
//                if (tailPortIntValue > 0 && tailPortIntValue < exportNameList.length)
//                    tailname = exportNameList[tailPortIntValue];
//			}
            if (tailPort != null) {
                tailname = tailPort.getName();
            } else {
                if (tailPortIntValue >= 0 && tailPortIntValue < exportNameList.length) {
                    tailname = exportNameList[tailPortIntValue];
                }
            }
            /*			if (headNode == null || headPort == null || tailNode == null || tailPort == null)
            {
            if (!arcInfoError)
            {
            System.out.println("ERROR: Missing arc information in cell " + cell.noLibDescribe() +
            " in library " + lib.getName() + " ...");
            if (headNode == null) System.out.println("   Head node not found");
            if (headPort == null) System.out.println("   Head port "+headname+" not found (was "+headPortIntValue+", node="+headNode+")");
            if (tailNode == null) System.out.println("   Tail node not found");
            if (tailPort == null) System.out.println("   Tail port "+tailname+" not found (was "+tailPortIntValue+", node="+tailNode+")");
            arcInfoError = true;
            }
            continue;
            }*/
            //PortInst headPortInst = headNode.findPortInst(((PortProto)headPort).getName());
            //PortInst tailPortInst = tailNode.findPortInst(((PortProto)tailPort).getName());
            PortInst headPortInst = getArcEnd(ap, headNode, headname, headX, headY, cell);
            PortInst tailPortInst = getArcEnd(ap, tailNode, tailname, tailX, tailY, cell);
            if (headPortInst == null || tailPortInst == null) {
                System.out.println("Cannot create arc of type " + ap.getName() + " in cell " + cell.getName()
                        + " because ends are unknown");
                continue;
            }
            TextDescriptor nameDescriptor = arcNameDescriptorList[i];
            if (nameDescriptor == null) {
                nameDescriptor = ep.getArcTextDescriptor();
            }
            ArcInst ai = ArcInst.newInstanceNoCheck(cell, ap, name, nameDescriptor, headPortInst, tailPortInst,
                    EPoint.fromLambda(headX, headY), EPoint.fromLambda(tailX, tailY), gridExtendOverMin,
                    ImmutableArcInst.angleFromElib(arcUserBits[i]), ImmutableArcInst.flagsFromElib(arcUserBits[i]));
            arcList[i] = ai;
            if (ai == null) {
                String msg = "ERROR: " + cell + ": arc " + name + " could not be created";
                System.out.println(msg);
                Input.errorLogger.logError(msg, cell, 1);
                continue;
            }
//            if (gridExtendOverMin < 0) {
//				String msg = "WARNING: "+cell + ": arc " + ai.getName() + " width is less than minimum by " + DBMath.gridToLambda(-2*gridExtendOverMin);
//                System.out.println(msg);
//				Input.errorLogger.logWarning(msg, ai, cell, null, 2);
//            }
            realizeVariables(ai, arcVariables[i]);
        }
    }

    /**
     * Method to build a NodeInst.
     */
    private Cell scaleCell(int i, double lambda, Cell cell, HashSet<Cell> recursiveSetupFlag, HashSet<Cell> patchedCells) {
        Cell subCell = (Cell) nodeInstList.protoType[i];
        Rectangle2D bounds = subCell.tree().getElibBounds(elibCellBounds);
//        Rectangle2D bounds = subCell.getBounds();
        double width = (nodeInstList.highX[i] - nodeInstList.lowX[i]) / lambda;
        double height = (nodeInstList.highY[i] - nodeInstList.lowY[i]) / lambda;
        if (Math.abs(bounds.getWidth() - width) <= 0.5 && Math.abs(bounds.getHeight() - height) <= 0.5) {
            return subCell;
        }
        LibraryFiles reader = this;
        if (subCell.getLibrary() != lib) {
            reader = getReaderForLib(subCell.getLibrary());
        }
        if (reader == null || !reader.canScale() || !cell.isSchematic() || !subCell.isIcon()) {
            return subCell;
        }
        // see if uniform scaling can be done
        double scaleX = width / bounds.getWidth();
        double scaleY = height / bounds.getHeight();
        // don't scale, most likely the size changed, and this is not a lambda problem
        if (!GenMath.doublesClose(scaleX, scaleY)) {
            return subCell;
        }
        double scale = Math.sqrt(scaleX * scaleY);
        String scaledCellName = subCell.getName() + "-SCALED-BY-" + scale
                + subCell.getView().getAbbreviationExtension();
        Cell scaledCell = subCell.getLibrary().findNodeProto(scaledCellName);
        if (scaledCell == null) {
            // create a scaled version of the cell
            if (reader != null) {
                reader.realizeCellsRecursively(subCell, recursiveSetupFlag, patchedCells, scaledCellName, scale);
            }
            scaledCell = subCell.getLibrary().findNodeProto(scaledCellName);
            if (scaledCell == null) {
                System.out.println("Error scaling " + subCell + " by " + scale);
            }
        }
        return scaledCell != null ? scaledCell : subCell;
    }

    // node is node we expect to have port 'portname' at location x,y.
    protected PortInst getArcEnd(ArcProto ap, NodeInst node, String portname, double x, double y, Cell cell) {
        PortInst pi = null;
        String whatHappenedToPort = "not found";
        String nodeName = "missing node";

        if (node != null) {
            pi = node.findPortInst(portname);
            nodeName = node.getName();

            if (pi != null) {
                return pi;
            }
            // check to make sure location is correct
//                Poly portLocation = pi.getPoly();
//                String extra = "";
//
//	            // Forcing rounding here instead of PolyBase.calcBounds()
////	            portLocation.roundPoints();
//                if (portLocation.contains(x, y) || portLocation.polyDistance(x, y) < TINYDISTANCE) {
//                    return pi;
//                }
//                // give extra info to user if didn't contain port
//                Rectangle2D box = portLocation.getBox();
//                if (box != null) {
//                    extra = "...arc end at ("+x+","+y+"), but port runs "+box.getMinX()+"<=X<="+box.getMaxX()+" and "+box.getMinY()+"<=Y<="+box.getMaxY();
//                } else
//				{
//                    extra = "...expected ("+x+","+y+"), polyDistance=" + portLocation.polyDistance(x, y);
//				}
//                whatHappenedToPort = "has moved"+extra;
//                pi = null;
//            } else {
            // name not found, see if any ports exist at location that we can connect to
            for (Iterator<PortInst> it = node.getPortInsts(); it.hasNext();) {
                pi = it.next();
                Poly portLocation = pi.getPoly();
                if (portLocation.contains(x, y)) {
                    if (pi.getPortProto().connectsTo(ap)) {
                        // connect to this port
                        String msg = cell + ": Port '" + portname + "' on '" + nodeName + "' not found, connecting to port '"
                                + pi.getPortProto().getName() + "' at the same location";
                        System.out.println("ERROR: " + msg);
                        Input.errorLogger.logError(msg, cell, 0);
                        return pi;
                    }
                }
                pi = null;
            }
            whatHappenedToPort = "is missing";
//            }

            // if this was a dummy cell, create the export in cell
            Cell c = null;
            if (node.getProto() != null && node.isCellInstance()) {
                if (((Cell) node.getProto()).getVar(IO_DUMMY_OBJECT) != null) {
                    c = (Cell) node.getProto();
                }
            }
            if (c != null) {
                double anchorX = node.getAnchorCenterX();
                double anchorY = node.getAnchorCenterY();
                Point2D expected = new Point2D.Double(x, y);
                FixpTransform trans = node.rotateIn();
                expected = trans.transform(expected, expected);
                Point2D center = new Point2D.Double(expected.getX() - anchorX, expected.getY() - anchorY);
                PrimitiveNode pn = Generic.tech().universalPinNode;
                NodeInst ni = NodeInst.newInstance(pn, ep, center, 0, 0, c, Orientation.IDENT, "");
                Export ex = Export.newInstanceNoIcon(c, ni.getOnlyPortInst(), portname, ep, null);
                if (ex != null) {
                    return node.findPortInst(portname);
                }
            }
        }


        // create pin as new end point of arc
        String msg = cell + ": Port '" + portname + "' on " + node + " " + whatHappenedToPort + ": leaving arc disconnected";
        System.out.println("ERROR: " + msg);

        PrimitiveNode pn = ap.findOverridablePinProto(ep);
        node = NodeInst.newInstance(pn, ep, new Point2D.Double(x, y), pn.getDefWidth(ep), pn.getDefHeight(ep), cell);
        Input.errorLogger.logError(msg, node, cell, null, 0);
        return node.getOnlyPortInst();
    }

    // --------------------------------- HIGH-LEVEL OBJECTS ---------------------------------
    /**
     * Method to read the header information of an "elib" file.
     * The header consists of the "magic" number and the size of various pieces of data.
     * Returns true on error.
     */
    private Header readHeader()
            throws IOException {
        ByteOrder byteOrder = ByteOrder.LITTLE_ENDIAN;
        byte byte1 = readByte();
        byte byte2 = readByte();
        byte byte3 = readByte();
        byte byte4 = readByte();
        int magic = ((byte4 & 0xFF) << 24) | ((byte3 & 0xFF) << 16) | ((byte2 & 0xFF) << 8) | (byte1 & 0xFF);
        if (magic != ELIBConstants.MAGIC1 && magic != ELIBConstants.MAGIC2
                && magic != ELIBConstants.MAGIC3 && magic != ELIBConstants.MAGIC4
                && magic != ELIBConstants.MAGIC5 && magic != ELIBConstants.MAGIC6
                && magic != ELIBConstants.MAGIC7 && magic != ELIBConstants.MAGIC8
                && magic != ELIBConstants.MAGIC9 && magic != ELIBConstants.MAGIC10
                && magic != ELIBConstants.MAGIC11 && magic != ELIBConstants.MAGIC12
                && magic != ELIBConstants.MAGIC13) {
            magic = ((byte1 & 0xFF) << 24) | ((byte2 & 0xFF) << 16) | ((byte3 & 0xFF) << 8) | (byte4 & 0xFF);
            if (magic != ELIBConstants.MAGIC1 && magic != ELIBConstants.MAGIC2
                    && magic != ELIBConstants.MAGIC3 && magic != ELIBConstants.MAGIC4
                    && magic != ELIBConstants.MAGIC5 && magic != ELIBConstants.MAGIC6
                    && magic != ELIBConstants.MAGIC7 && magic != ELIBConstants.MAGIC8
                    && magic != ELIBConstants.MAGIC9 && magic != ELIBConstants.MAGIC10
                    && magic != ELIBConstants.MAGIC11 && magic != ELIBConstants.MAGIC12
                    && magic != ELIBConstants.MAGIC13) {
                System.out.println("Bad file format: does not start with proper magic number");
                return null;
            }
            byteOrder = ByteOrder.BIG_ENDIAN;
        }

        // determine the size of "big" and "small" integers as well as characters on disk
        int sizeOfBig = 4;
        int sizeOfSmall = 2;
        int sizeOfChar = 1;
        if (magic <= ELIBConstants.MAGIC10) {
            sizeOfSmall = readByte();
            sizeOfBig = readByte();
        }
        if (magic <= ELIBConstants.MAGIC11) {
            sizeOfChar = readByte();
        }
        return new Header(magic, byteOrder, sizeOfBig, sizeOfSmall, sizeOfChar);
    }

    /**
     * Method to read a cell.  returns true upon error
     */
    private boolean readNodeProto(int cellIndex)
            throws IOException {
        // read the cell name
        String theProtoName;
        if (header.magic <= ELIBConstants.MAGIC9) {
            int nextInCell = -1;
            // read the cell information (version 9 and later)
            if (header.magic >= ELIBConstants.MAGIC11) {
                // only versions 9 to 11
                int k = readBigInteger();
                theProtoName = fakeCellList[k].cellName;
            } else {
                // version 12 or later
                theProtoName = convertCellName(readString());
                int k = readBigInteger();

                // fix for new cell version library corruption bug
                if (k == -1) {
                    k = cellIndex;
//                    // find self in list
//                    for (int i=0; i<nodeProtoList.length; i++) {
//                        if (cell == nodeProtoList[i]) { k = i; break; }
//                    }
                }

                nextInCell = k; // the "next in cell group" circular pointer
                k = readBigInteger();
//				cell->nextcont = nodeProtoList[k];		// the "next in cell continuation" circular pointer
            }
            View v = getView(readBigInteger());
            if (v == null) {
                v = View.UNKNOWN;
            }
            int version = readBigInteger();
            theProtoName += ";" + version + v.getAbbreviationExtension();
            cellNextInCellGroup[cellIndex] = nextInCell;
            cellCreationDate[cellIndex] = readBigInteger();
            cellRevisionDate[cellIndex] = readBigInteger();
        } else {
            // versions 8 and earlier read a cell name
            theProtoName = readString();
        }
        cellProtoName[cellIndex] = theProtoName;

        // ignore the cell bounding box
        readBigInteger(); // lowX
        readBigInteger(); // highX
        readBigInteger();  // lowY
        readBigInteger(); // highY

        // ignore the linked list pointers (versions 5 or older)
        if (header.magic >= ELIBConstants.MAGIC5) {
            readBigInteger();		// ignore "prev index"
            readBigInteger();		// ignore "next index"
        }

        // read the exports on this nodeproto
        firstPortIndex[cellIndex] = exportIndex;
        int portCount = readBigInteger();
        if (portCount != portCounts[cellIndex]) {
            System.out.println("Error! Cell header lists " + portCounts[cellIndex] + " exports, but body lists " + portCount);
        }
        for (int j = 0; j < portCount; j++) {
            // read the sub-NodeInst for this Export
            exportSubNodeList[exportIndex] = -1;
            int whichNode = readBigInteger();
            if (whichNode >= 0 && whichNode < nodeCount) {
                exportSubNodeList[exportIndex] = whichNode;
            }

            // read the sub-PortProto on the sub-NodeInst
            exportSubPortList[exportIndex] = readBigInteger();
//			exportSubPortList[exportIndex] = null;
//			int whichPort = readBigInteger();
//			if (whichPort < 0 || exportList[whichPort] != null)
//				exportSubPortList[exportIndex] = convertPortProto(whichPort);
//			if (exportSubPortList[exportIndex] == null)
//			{
//				exportSubPortList[exportIndex] = new Integer(whichPort);
//			}

            // read the Export name
            String exportName = readString();
            exportNameList[exportIndex] = exportName;

            if (exportSubNodeList[exportIndex] == -1) {
                System.out.println("Error: Export '" + exportName + "' of cell " + theProtoName
                        + " cannot be read properly");
            }

            // read the portproto text descriptor
            int descript0 = 0, descript1 = 0;
            if (header.magic <= ELIBConstants.MAGIC9) {
                if (convertTextDescriptors) {
                    // conversion is done later
                    descript0 = readBigInteger();
                    descript1 = 0;
                } else {
                    descript0 = readBigInteger();
                    descript1 = readBigInteger();
                }
            }
            exportNameDescriptors[exportIndex] = makeDescriptor(descript0, descript1);

            // ignore the "seen" bits (versions 8 and older)
            if (header.magic > ELIBConstants.MAGIC9) {
                readBigInteger();
            }

            // read the portproto's "user bits"
            exportUserbits[exportIndex] = 0;
            if (header.magic <= ELIBConstants.MAGIC7) {
                // version 7 and later simply read the relevant data
                exportUserbits[exportIndex] = readBigInteger();

                // versions 7 and 8 ignore net number
                if (header.magic >= ELIBConstants.MAGIC8) {
                    readBigInteger();
                }
            } else {
                // version 6 and earlier must sift through the information
                if (toolBCount >= 1) {
                    exportUserbits[exportIndex] = readBigInteger();
                }
                for (int i = 1; i < toolBCount; i++) {
                    readBigInteger();
                }
            }

            // read the export variables
            exportVariables[exportIndex] = readVariables();

            exportIndex++;
        }

        // ignore the cell's geometry information
        if (header.magic > ELIBConstants.MAGIC5) {
            // versions 4 and older have geometry module pointers (ignore it)
            readBigInteger();
            readBigInteger();
            readBigInteger();
            readBigInteger();
            readBigInteger();
        }

        // read tool information
        readBigInteger();		// ignore "dirty"

        // read the "user bits"
        int userBits = 0;
        if (header.magic <= ELIBConstants.MAGIC7) {
            // version 7 and later simply read the relevant data
            userBits = readBigInteger();

            // versions 7 and 8 ignore net number
            if (header.magic >= ELIBConstants.MAGIC8) {
                readBigInteger();
            }
        } else {
            // version 6 and earlier must sift through the information
            if (toolBCount >= 1) {
                userBits = readBigInteger();
            }
            for (int i = 1; i < toolBCount; i++) {
                readBigInteger();
            }
        }
        cellUserBits[cellIndex] = userBits;

        // read variable information
        cellVars[cellIndex] = readVariables();

        // cell read successfully
        return false;
    }

    /**
     * Method to realize a cell.  returns true upon error
     */
    private void realizeNodeProto(int cellIndex, Map<Cell, Variable[]> originalVars) {
        String theProtoName = cellProtoName[cellIndex];
        Cell cell = Cell.newInstance(lib, theProtoName);
        if (header.magic <= ELIBConstants.MAGIC9) {
            cell.lowLevelSetCreationDate(ELIBConstants.secondsToDate(cellCreationDate[cellIndex]));
            cell.lowLevelSetRevisionDate(ELIBConstants.secondsToDate(cellRevisionDate[cellIndex]));
        }
        nodeProtoList[cellIndex] = cell;
        assert cell.getCellName() != null;
        cell.lowLevelSetUserbits(cellUserBits[cellIndex]);
        originalVars.put(cell, cellVars[cellIndex]);
    }

    /** Method to read node prototype for external references */
    private boolean readExternalNodeProto(int cellIndex)
            throws IOException {
        // read the cell information (version 9 and later)
        String theProtoName;
        if (header.magic >= ELIBConstants.MAGIC11) {
            // only versions 9 to 11
            int k = readBigInteger();
            theProtoName = fakeCellList[k].cellName;
        } else {
            // version 12 or later
            theProtoName = convertCellName(readString());
            readBigInteger();
            readBigInteger();
        }
        View v = getView(readBigInteger());
        if (v == null) {
            v = View.UNKNOWN;
        }
        int version = readBigInteger();
        String fullCellName = theProtoName + ";" + version + v.getAbbreviationExtension();
        if (version <= 1) {
            fullCellName = theProtoName + v.getAbbreviationExtension();
        }
        cellProtoName[cellIndex] = fullCellName;
        cellCreationDate[cellIndex] = readBigInteger();
        cellRevisionDate[cellIndex] = readBigInteger();

        // read the nodeproto bounding box
        cellLowX[cellIndex] = readBigInteger();
        cellHighX[cellIndex] = readBigInteger();
        cellLowY[cellIndex] = readBigInteger();
        cellHighY[cellIndex] = readBigInteger();

        // get the external library
        cellLibraryPath[cellIndex] = readString();

        // read the portproto names on this nodeproto
        int portCount = readBigInteger();
        String[] localPortNames = new String[portCount];
        for (int j = 0; j < portCount; j++) {
            localPortNames[j] = readString();
        }

        // read the portprotos on this Cell
        firstPortIndex[cellIndex] = exportIndex;
        if (portCount != portCounts[cellIndex]) {
            System.out.println("Error! Cell header lists " + portCounts[cellIndex] + " exports, but body lists " + portCount);
        }
        for (int j = 0; j < portCount; j++) {
            // read the portproto name
            String protoName = localPortNames[j];
            exportNameList[exportIndex] = protoName;
            exportIndex++;
        }
        return false;
    }

    /** Method to read node prototype for external references */
    private boolean realizeExternalNodeProto(Library lib, int cellIndex) {
        // read the cell information (version 9 and later)
        String fullCellName = cellProtoName[cellIndex];
        Date creationDate = ELIBConstants.secondsToDate(cellCreationDate[cellIndex]);
        Date revisionDate = ELIBConstants.secondsToDate(cellRevisionDate[cellIndex]);

        // read the nodeproto bounding box
        int lowX = cellLowX[cellIndex];
        int highX = cellHighX[cellIndex];
        int lowY = cellLowY[cellIndex];
        int highY = cellHighY[cellIndex];

        // get the external library
        Library elib = readExternalLibraryFromFilename(cellLibraryPath[cellIndex], FileType.ELIB, ep);

        // find this cell in the external library
        Cell c = elib.findNodeProto(fullCellName);

        // if can't find, look for dummy version
        //String dummyName = "DUMMY" + fullCellName;
        String dummyName = fullCellName;
        if (c == null) {
            c = elib.findNodeProto(dummyName);
        }

        if (c == null) {
            // cell not found in library: issue warning
            System.out.println("ERROR: Cannot find cell " + fullCellName + " in " + elib);
        }

        // if cell found, check that size is unchanged (size is unknown at this point)
//		if (c != null)
//		{
//			Rectangle2D bounds = c.getBounds();
//			double lambda = 1;
//			Technology cellTech = Technology.whatTechnology(c);
//			if (cellTech != null) lambda = techScale[cellTech.getIndex()];
//			double cellWidth = (highX - lowX) / lambda;
//			double cellHeight = (highY - lowY) / lambda;
//			if (!DBMath.doublesEqual(bounds.getWidth(), cellWidth) || !DBMath.doublesEqual(bounds.getHeight(), cellHeight))
//			{
//				// bounds differ, but lambda scaling is inaccurate: see if aspect ratio changed
//				if (!DBMath.doublesEqual(bounds.getWidth() * cellHeight, bounds.getHeight() * cellWidth))
//				{
//					System.out.println("Error: cell " + c.noLibDescribe() + " in library " + elib.getName() +
//						" has changed size since its use in library " + lib.getName());
//					System.out.println("  The cell is " + bounds.getWidth() + "x" +  bounds.getHeight() +
//						" but the instances in library " + lib.getName() + " are " + cellWidth + "x" + cellHeight);
//					c = null;
//				}
//			}
//		}

        // if cell found, check that ports match
/*
        if (c != null)
        {
        for(int j=0; j<portCount; j++)
        {
        PortProto pp = c.findExport(localPortNames[j]);
        if (pp == null)
        {
        LibraryFiles reader = getReaderForLib(elib);
        if (reader != null)
        {
        if (reader.readerHasExport(c, localPortNames[j])) continue;
        }

        System.out.println("Error: cell " + c.noLibDescribe() + " in library " + elib.getName() +
        " is missing export '" + localPortNames[j] + "'");
        // 					for (Iterator<PortProto> it = c.getPorts(); it.hasNext(); )
        // 					{
        // 						PortProto ppo = it.next();
        // 						System.out.println("\t"+ppo.getName());
        // 					}
        c = null;
        break;
        }
        }
        }
         */

        // if cell found, warn if minor modification was made
        if (c != null) {
            if (revisionDate.compareTo(c.getRevisionDate()) != 0) {
                System.out.println("Warning: cell " + c.noLibDescribe() + " in " + elib
                        + " has changed since its use in " + lib);
            }
        }

        // make new cell if needed
//		NodeInst fakeNodeInst = null;
        if (c == null) {
            // create a cell that meets these specs
/*
            String dummyCellName = null;
            for(int index=0; ; index++)
            {
            dummyCellName = theProtoName + "FROM" + elib.getName();
            if (index > 0) dummyCellName += "." + index;
            dummyCellName += "{" + v.getAbbreviation() + "}";
            if (lib.findNodeProto(dummyCellName) == null) break;
            }
            c = Cell.newInstance(lib, dummyCellName);
             */
            c = Cell.newInstance(elib, dummyName);
            if (c == null) {
                return true;
            }
            c.lowLevelSetCreationDate(creationDate);
            c.lowLevelSetRevisionDate(revisionDate);

            // create an artwork "Crossed box" to define the cell size
            Technology tech = Technology.getMocmosTechnology();
            if (c.isIcon()) {
                tech = Artwork.tech();
            } else if (c.isSchematic()) {
                tech = Schematics.tech();
            }
            double lambda = getScale(tech);
            int cX = (lowX + highX) / 2;
            int cY = (lowY + highY) / 2;
            double width = (highX - lowX) / lambda;
            double height = (highY - lowY) / lambda;
            Point2D center = new Point2D.Double(cX / lambda, cY / lambda);
            NodeInst.newInstance(Generic.tech().drcNode, ep, center, width, height, c);
            //PrimitiveNode cellCenter = Generic.tech.cellCenterNode;
            //NodeInst.newInstance(cellCenter, new Point2D.Double(0,0), cellCenter.getDefWidth(),
            //        cellCenter.getDefHeight(), 0, c, null);
            //fakeNodeInst = NodeInst.newInstance(Generic.tech.universalPinNode, center, width, height, 0, c, null);

            // note that exports get created in getArcEnd. If it tries to connect to a missing export
            // on a dummy cell, it creates the export site.

            System.out.println("...Creating dummy cell '" + dummyName + "' in " + elib
                    + ". Instances will be logged as Errors.");
            c.newVar(IO_TRUE_LIBRARY, elib.getName(), ep);
            c.newVar(IO_DUMMY_OBJECT, fullCellName, ep);
        }
        nodeProtoList[cellIndex] = c;

        // realize the portprotos on this Cell
        int portCount = portCounts[cellIndex];
        for (int j = 0; j < portCount; j++) {
            // realize the portproto name
            exportList[firstPortIndex[cellIndex] + j] = c;
        }
        return false;
    }

    /**
     * Method to read a node instance.  returns true upon error
     */
    private boolean readNodeInst(int nodeIndex, int cellIndex)
            throws IOException {
        // read the nodeproto index
        int protoIndex = readBigInteger();

        // read the descriptive information
        nodeTypeList[nodeIndex] = protoIndex;
        nodeInstList.lowX[nodeIndex] = readBigInteger();
        nodeInstList.lowY[nodeIndex] = readBigInteger();
        nodeInstList.highX[nodeIndex] = readBigInteger();
        nodeInstList.highY[nodeIndex] = readBigInteger();
        if (header.magic <= ELIBConstants.MAGIC13) {
            // read anchor point for cell references
            if (protoIndex >= 0) {
                nodeInstList.anchorX[nodeIndex] = readBigInteger();
                nodeInstList.anchorY[nodeIndex] = readBigInteger();
            }
        }

        nodeInstList.transpose[nodeIndex] = readBigInteger();
        nodeInstList.rotation[nodeIndex] = (short) readBigInteger();
        nodeInstList.name[nodeIndex] = null;

        // versions 9 and later get text descriptor for cell name
        int descript0 = 0, descript1 = 0;
        if (header.magic <= ELIBConstants.MAGIC9) {
            if (convertTextDescriptors) {
                // conversion is done later
                descript0 = readBigInteger();
            } else {
                descript0 = readBigInteger();
                descript1 = readBigInteger();
            }
        }
        nodeInstList.protoTextDescriptor[nodeIndex] = makeDescriptor(descript0, descript1);
//      mtd.setCBits(descript0, descript1);
//		ni.setTextDescriptor(NodeInst.NODE_PROTO_TD, mtd);
//        mtd.setCBits(descript0, descript1);
//		ni.setTextDescriptor(NodeInst.NODE_PROTO_TD, mtd);

        // read the nodeinst name (versions 1, 2, or 3 only)
        if (header.magic >= ELIBConstants.MAGIC3) {
            String instName = readString();
            if (instName.length() > 0) {
                nodeInstList.name[nodeIndex] = instName;
            }
        }

        // ignore the geometry index (versions 4 or older)
        if (header.magic > ELIBConstants.MAGIC5) {
            readBigInteger();
        }

        // read the arc ports
        int numPorts = readBigInteger();
        for (int j = 0; j < numPorts; j++) {
            // read the arcinst information (and the particular end on the arc)
            int k = readBigInteger();
            int arcIndex = k >> 1;
            if (k < 0 || arcIndex >= arcCount) {
                return true;
            }

            // read the port information
            int portIndex = readBigInteger();
            if ((k & 1) == 0) {
                arcTailPortList[arcIndex] = portIndex;
            } else {
                arcHeadPortList[arcIndex] = portIndex;
            }

            // ignore variables on port instance
            readVariables();
        }

        // ignore the exports
        int numExports = readBigInteger();
        for (int j = 0; j < numExports; j++) {
            readBigInteger();
            readBigInteger();
            readVariables();
        }

        // ignore the "seen" bits (versions 8 and older)
        if (header.magic > ELIBConstants.MAGIC9) {
            readBigInteger();
        }

        // read the tool information
        int userBits = 0;
        if (header.magic <= ELIBConstants.MAGIC7) {
            // version 7 and later simply read the relevant data
            userBits = readBigInteger();
        } else {
            // version 6 and earlier must sift through the information
            if (toolBCount >= 1) {
                userBits = readBigInteger();
            }
            for (int i = 1; i < toolBCount; i++) {
                readBigInteger();
            }
        }
        nodeInstList.userBits[nodeIndex] = userBits;

        // read variable information
        Variable[] vars = readVariables();
        for (int j = 0; j < vars.length; j++) {
            Variable var = vars[j];
            if (var == null || var.getKey() != NodeInst.NODE_NAME) {
                continue;
            }
            Object value = var.getObject();
            if (!(value instanceof String)) {
                continue;
            }
            nodeInstList.name[nodeIndex] = convertGeomName(value, var.isDisplay());
            nodeInstList.nameTextDescriptor[nodeIndex] = var.getTextDescriptor();
            vars[j] = null;
        }
        nodeInstList.vars[nodeIndex] = vars;

        // node read successfully
        return false;
    }

    /**
     * Method to read an arc instance.  returns true upon error
     */
    private boolean readArcInst(int arcIndex)
            throws IOException {
        // read the arcproto pointer
        arcTypeList[arcIndex] = readBigInteger();

        // read the arc length (versions 5 or older)
        if (header.magic >= ELIBConstants.MAGIC5) {
            readBigInteger();
        }

        // read the arc width
        arcWidthList[arcIndex] = readBigInteger();

        // ignore the signals value (versions 6, 7, or 8)
        if (header.magic <= ELIBConstants.MAGIC6 && header.magic >= ELIBConstants.MAGIC8) {
            readBigInteger();
        }

        // read the arcinst name (versions 3 or older)
        if (header.magic >= ELIBConstants.MAGIC3) {
            String instName = readString();
            if (instName.length() > 0) {
                arcNameList[arcIndex] = instName;
            }
        }

        // read the tail information
        arcTailXPosList[arcIndex] = readBigInteger();
        arcTailYPosList[arcIndex] = readBigInteger();
        int tailNodeIndex = readBigInteger();
        if (tailNodeIndex >= 0 && tailNodeIndex < nodeCount) {
            arcTailNodeList[arcIndex] = tailNodeIndex;
        }

        // read the head information
        arcHeadXPosList[arcIndex] = readBigInteger();
        arcHeadYPosList[arcIndex] = readBigInteger();
        int headNodeIndex = readBigInteger();
        if (headNodeIndex >= 0 && headNodeIndex < nodeCount) {
            arcHeadNodeList[arcIndex] = headNodeIndex;
        }

        // ignore the geometry index (versions 4 or older)
        if (header.magic > ELIBConstants.MAGIC5) {
            readBigInteger();
        }

        // ignore the "seen" bits (versions 8 and older)
        if (header.magic > ELIBConstants.MAGIC9) {
            readBigInteger();
        }

        // read the arcinst's tool information
        int userBits = 0;
        if (header.magic <= ELIBConstants.MAGIC7) {
            // version 7 and later simply read the relevant data
            userBits = readBigInteger();

            // versions 7 and 8 ignore net number
            if (header.magic >= ELIBConstants.MAGIC8) {
                readBigInteger();
            }
        } else {
            // version 6 and earlier must sift through the information
            if (toolBCount >= 1) {
                userBits = readBigInteger();
            }
            for (int i = 1; i < toolBCount; i++) {
                readBigInteger();
            }
        }
        arcUserBits[arcIndex] = userBits;

        // read variable information
        Variable[] vars = readVariables();
        for (int i = 0; i < vars.length; i++) {
            Variable var = vars[i];
            if (var == null || var.getKey() != ArcInst.ARC_NAME) {
                continue;
            }
            Object value = var.getObject();
            if (!(value instanceof String)) {
                continue;
            }
            arcNameList[arcIndex] = convertGeomName(value, var.isDisplay());
            arcNameDescriptorList[arcIndex] = var.getTextDescriptor();
            vars[i] = null;
        }
        arcVariables[arcIndex] = vars;

        // arc read successfully
        return false;
    }

    /**
     * Method to read (and mostly ignore) a geometry module
     */
    private void readGeom(boolean[] isNode, int[] moreup, int index)
            throws IOException {
        int type = readBigInteger();		// read entrytype
        if (type != 0) {
            isNode[index] = true;
        } else {
            isNode[index] = false;
        }
        if (isNode[index]) {
            readBigInteger();// skip entryaddr
        }
        readBigInteger();					// skip lowx
        readBigInteger();					// skip highx
        readBigInteger();					// skip lowy
        readBigInteger();					// skip highy
        readBigInteger();					// skip moreleft
        readBigInteger();					// skip ll
        readBigInteger();					// skip moreright
        readBigInteger();					// skip lr
        moreup[index] = readBigInteger();	// read moreup
        readBigInteger();					// skip lu
        readBigInteger();					// skip moredown
        readBigInteger();					// skip ld
        readVariables();					// skip variables
    }

    // --------------------------------- VARIABLES ---------------------------------
    /**
     * Method to read the global namespace.  returns true upon error
     */
    private void readNameSpace()
            throws IOException {
        int nameCount = readBigInteger();
        varNames = new String[nameCount];
        varKeys = new Variable.Key[nameCount];
        for (int i = 0; i < nameCount; i++) {
            varNames[i] = readString();
        }
    }

    /**
     * Method to read a set of variables onto a given object.
     * @return the array of variables read.
     */
    private Variable[] readVariables()
            throws IOException {
        int count = readBigInteger();
        if (count == 0) {
            return Variable.NULL_ARRAY;
        }
        Variable[] vars = new Variable[count];
        for (int i = 0; i < count; i++) {
            short key = readSmallInteger();
            if (key < 0 || key >= varKeys.length) {
                String msg = "Bad variable index (" + key + ", limit is " + varKeys.length + ")";
                System.out.println(msg);
                throw new IOException(msg);
            }
            if (varKeys[key] == null) {
                varKeys[key] = Variable.newKey(varNames[key]);
            }
            int newtype = readBigInteger();

            // version 9 and later reads text description on displayable variables
            int descript0 = 0;
            int descript1 = 0;
            if (header.magic <= ELIBConstants.MAGIC9) {
                if (alwaysTextDescriptors) {
                    descript0 = readBigInteger();
                    descript1 = readBigInteger();
                } else {
                    if ((newtype & ELIBConstants.VDISPLAY) != 0) {
                        if (convertTextDescriptors) {
                            // conversion is done later
                            descript0 = readBigInteger();
                        } else {
                            descript0 = readBigInteger();
                            descript1 = readBigInteger();
                        }
                    }
                }
            }
            TextDescriptor td = makeDescriptor(descript0, descript1, newtype);
            CodeExpression.Code code = CodeExpression.Code.getByCBits(newtype);

            Object newAddr;
            if ((newtype & ELIBConstants.VISARRAY) != 0) {
                int len = readBigInteger();
                int cou = len;
//				if ((newtype&ELIBConstants.VLENGTH) == 0) cou++;
                Object[] newAddrArray = null;
                switch (newtype & ELIBConstants.VTYPE) {
                    case ELIBConstants.VADDRESS:
                    case ELIBConstants.VINTEGER:
                        newAddrArray = new Integer[cou];
                        break;
                    case ELIBConstants.VFRACT:
                    case ELIBConstants.VFLOAT:
                        newAddrArray = new Float[cou];
                        break;
                    case ELIBConstants.VDOUBLE:
                        newAddrArray = new Double[cou];
                        break;
                    case ELIBConstants.VSHORT:
                        newAddrArray = new Short[cou];
                        break;
                    case ELIBConstants.VBOOLEAN:
                        newAddrArray = new Boolean[cou];
                        break;
                    case ELIBConstants.VCHAR:
                        newAddrArray = new Byte[cou];
                        break;
                    case ELIBConstants.VSTRING:
                        newAddrArray = new String[cou];
                        break;
                    case ELIBConstants.VNODEPROTO:
                        newAddrArray = new NodeProtoId[cou];
                        break;
                    case ELIBConstants.VARCPROTO:
                        newAddrArray = new ArcProtoId[cou];
                        break;
                    case ELIBConstants.VPORTPROTO:
                        newAddrArray = new ExportId[cou];
                        break;
                    case ELIBConstants.VTECHNOLOGY:
                        newAddrArray = new TechId[cou];
                        break;
                    case ELIBConstants.VLIBRARY:
                        newAddrArray = new LibId[cou];
                        break;
                    case ELIBConstants.VTOOL:
                        newAddrArray = new Tool[cou];
                        break;
                }
                newAddr = newAddrArray;
                if ((newtype & ELIBConstants.VTYPE) == ELIBConstants.VGENERAL) {
                    for (int j = 0; j < len; j += 2) {
                        readBigInteger();		// ignore type
                        readBigInteger();		// ignore address
                        if (newAddrArray != null) {
                            newAddrArray[j] = null;
                        }
                    }
                } else {
                    for (int j = 0; j < len; j++) {
                        Object ret = getInVar(newtype);
                        if (newAddrArray != null) {
                            newAddrArray[j] = ret;
                        }
                    }
                }

                if (newAddrArray == null) {
                    String msg = "Cannot figure out the type for code " + (newtype & ELIBConstants.VTYPE);
                    System.out.println(msg);
                    continue;
                }
                if (newAddrArray instanceof NodeProtoId[]) {
                    int numCells = 0, numPrims = 0;
                    for (int j = 0; j < newAddrArray.length; j++) {
                        if (newAddrArray[j] == null) {
                            continue;
                        }
                        if (newAddrArray[j] instanceof CellId) {
                            numCells++;
                        }
                        if (newAddrArray[j] instanceof PrimitiveNodeId) {
                            numPrims++;
                        }
                    }
                    if (numCells >= numPrims) {
                        CellId[] cellArray = new CellId[newAddrArray.length];
                        for (int j = 0; j < cellArray.length; j++) {
                            if (newAddrArray[j] instanceof CellId) {
                                cellArray[j] = (CellId) newAddrArray[j];
                            }
                        }
                        newAddr = cellArray;
                    } else {
                        PrimitiveNodeId[] primArray = new PrimitiveNodeId[newAddrArray.length];
                        for (int j = 0; j < primArray.length; j++) {
                            if (newAddrArray[j] instanceof PrimitiveNodeId) {
                                primArray[j] = (PrimitiveNodeId) newAddrArray[j];
                            }
                        }
                        newAddr = primArray;
                    }
                }
            } else {
                newAddr = getInVar(newtype);
            }

            if (newAddr == null) {
                System.out.println("Error reading variable " + varNames[key] + " type " + newtype);
                continue;
            }
            newAddr = Variable.withCode(newAddr, code);
            vars[i] = Variable.newInstance(varKeys[key], newAddr, td);
        }
        return vars;
    }

    /**
     * Helper method to read a variable at address "addr" of type "ty".
     * Returns zero if OK, negative on memory error, positive if there were
     * correctable problems in the read.
     */
    private Object getInVar(int ty)
            throws IOException {
        int i;

        if ((ty & (ELIBConstants.VCODE1 | ELIBConstants.VCODE2)) != 0) {
            ty = ELIBConstants.VSTRING;
        }
        switch (ty & ELIBConstants.VTYPE) {
            case ELIBConstants.VADDRESS:
            case ELIBConstants.VINTEGER:
                return Integer.valueOf(readBigInteger());
            case ELIBConstants.VFRACT:
                return Float.valueOf(readBigInteger() / 120.0f);
            case ELIBConstants.VFLOAT:
                return Float.valueOf(readFloat());
            case ELIBConstants.VDOUBLE:
                return Double.valueOf(readDouble());
            case ELIBConstants.VSHORT:
                return Short.valueOf(readSmallInteger());
            case ELIBConstants.VBOOLEAN:
                return Boolean.valueOf(readByte() != 0);
            case ELIBConstants.VCHAR:
                return Byte.valueOf(readByte());
            case ELIBConstants.VSTRING:
                return readString();
            case ELIBConstants.VNODEINST:
                i = readBigInteger();
                System.out.println("Cannot read variable of type NodeInst");
                return null;
            case ELIBConstants.VNODEPROTO:
                i = readBigInteger();
                NodeProto np = convertNodeProto(i);
                if (np == null) {
                    return np;
                }
                return (np instanceof Cell ? ((Cell) np).getId() : ((PrimitiveNode) np).getId());
            case ELIBConstants.VARCPROTO:
                i = readBigInteger();
                if (i == -1) {
                    System.out.println("Variable of type ArcProto has negative index");
                    return null;
                }
                return convertArcProto(i).getId();
            case ELIBConstants.VPORTPROTO:
                i = readBigInteger();
                PortProto pp = convertPortProto(i);
                if (!(pp instanceof Export)) {
                    return null;
                }
                return ((Export) pp).getId();
            case ELIBConstants.VARCINST:
                i = readBigInteger();
                System.out.println("Cannot read variable of type ArcInst");
                return null;
            case ELIBConstants.VGEOM:
                readBigInteger();
                readBigInteger();
                System.out.println("Cannot read variable of type Geometric");
                return null;
            case ELIBConstants.VTECHNOLOGY:
                i = readBigInteger();
                if (i == -1) {
                    System.out.println("Variable of type Technology has negative index");
                    return null;
                }
                return getTechList(i).getId();
            case ELIBConstants.VPORTARCINST:
                readBigInteger();
                System.out.println("Cannot read variable of type PortArcInst");
                return null;
            case ELIBConstants.VPORTEXPINST:
                readBigInteger();
                System.out.println("Cannot read variable of type PortExpInst");
                return null;
            case ELIBConstants.VLIBRARY:
                String libName = readString();
                if (libName.length() == 0) {
                    return null;
                }
                return idManager.newLibId(libName);
            case ELIBConstants.VTOOL:
                i = readBigInteger();
                if (i < 0 || i >= toolCount) {
                    return null;
                }
                Tool tool = toolList[i];
                if (tool == null) {
                    i = 0;
                    if (toolError[i] != null) {
                        System.out.println("WARNING: no tool called '" + toolError[i] + "', using 'user'");
                        toolError[i] = null;
                    }
                }
                return tool;
            case ELIBConstants.VRTNODE:
                readBigInteger();
                System.out.println("Cannot read variable of type RTNode");
                return null;
        }
        System.out.println("Cannot read variable of type " + (ty & ELIBConstants.VTYPE));
        return null;
    }

    // --------------------------------- OBJECT CONVERSION ---------------------------------
    /**
     * Method to convert the nodeproto index "i" to a true nodeproto pointer.
     */
    private NodeProto convertNodeProto(int i) {
        if (i == -1) {
            return null;
        }
        if (i < 0) {
            // negative values are primitives
            int nindex = -i - 2;
            if (nindex >= primNodeProtoCount) {
                System.out.println("Error: want primitive node index " + nindex + " when limit is " + primNodeProtoCount);
                return null;
            }
            return getPrimNodeProtoList(nindex);
        }

        // see if the cell value is valid
        if (i >= nodeProtoCount) {
            System.out.println("Error: want cell index " + i + " when limit is " + nodeProtoCount);
            return null;
        }
        return nodeProtoList[i];
    }

    /**
     * Method to convert the arcproto index "i" to a true arcproto pointer.
     */
    private ArcProto convertArcProto(int i) {
        int aindex = -i - 2;
        if (aindex >= arcProtoCount || aindex < 0) {
            System.out.println("Want primitive arc index " + aindex + " when range is 0 to " + arcProtoCount);
            aindex = 0;
        }
        return getArcProtoList(aindex);
    }

    /**
     * Method to convert the PortProto index "i" to a true PortProto pointer.
     */
    private PortProto convertPortProto(int i) {
        if (i == -1) {
            return null;
        }
        if (i < 0) {
            int pindex = -i - 2;
            if (pindex >= primPortProtoCount) {
                System.out.println("Error: want primitive port index " + pindex + " when limit is " + primPortProtoCount);
                pindex = 0;
            }
            return getPrimPortProtoList(pindex);
        }

        if (i >= exportCount) {
            System.out.println("Error: want port index " + i + " when limit is " + exportCount);
            i = 0;
        }
        if (exportList[i] instanceof Cell) {
            return null;
        }
        return (Export) exportList[i];
    }

    private NodeProto getPrimNodeProtoList(int i) {
        getTechList(primNodeProtoTech[i]);
        if (primNodeProtoError[i]) {
            System.out.println("Cannot find primitive '" + primNodeProtoOrig[i] + "', using "
                    + primNodeProtoList[i].getName());
            primNodeProtoError[i] = false;
        }
        return (primNodeProtoList[i]);
    }

    private ArcProto getArcProtoList(int i) {
        if (arcProtoError[i] != null) {
            System.out.println("Cannot find arc '" + arcProtoError[i] + "', using " + arcProtoList[i].getName());
            arcProtoError[i] = null;
        }
        return (arcProtoList[i]);
    }

    private PortProto getPrimPortProtoList(int i) {
        if (primPortProtoError[i] != null) {
            System.out.println("WARNING: port " + primPortProtoError[i] + " not found, using "
                    + primPortProtoList[i].getName());
            primPortProtoError[i] = null;
        }
        return (primPortProtoList[i]);
    }

    /**
     * Method to return the Technology associated with index "i".
     */
    private Technology getTechList(int i) {
        if (techError[i] != null) {
            System.out.println("WARNING: technology '" + techError[i] + "' does not exist, using '" + techList[i].getTechName() + "'");
            techError[i] = null;
        }
        return (techList[i]);
    }

    /**
     * Method to return the View associated with index "i".
     */
    private View getView(int i) {
        View v = viewMapping.get(new Integer(i));
        return v;
    }

    // --------------------------------- LOW-LEVEL INPUT ---------------------------------
    /**
     * Method to read a single byte from the input stream and return it.
     */
    private byte readByte()
            throws IOException {
        int value = dataInputStream.read();
        if (value == -1) {
            throw new IOException();
        }
        updateProgressDialog(1);
        return (byte) value;
    }
    static private ByteBuffer bb = ByteBuffer.allocateDirect(8);
    static private byte[] rawData = new byte[8];

    /**
     * Method to read an integer (4 bytes) from the input stream and return it.
     */
    private int readBigInteger()
            throws IOException {
        if (header.sizeOfBig == 4) {
            updateProgressDialog(4);
            int data = dataInputStream.readInt();
            if (!header.bytesSwapped) {
                data = ((data >> 24) & 0xFF) | ((data >> 8) & 0xFF00) | ((data & 0xFF00) << 8) | ((data & 0xFF) << 24);
            }
            return data;
        }
        readBytes(rawData, header.sizeOfBig, 4, true);
        if (header.bytesSwapped) {
            bb.put(0, rawData[0]);
            bb.put(1, rawData[1]);
            bb.put(2, rawData[2]);
            bb.put(3, rawData[3]);
        } else {
            bb.put(0, rawData[3]);
            bb.put(1, rawData[2]);
            bb.put(2, rawData[1]);
            bb.put(3, rawData[0]);
        }
        return bb.getInt(0);
    }

    /**
     * Method to read a float (4 bytes) from the input stream and return it.
     */
    private float readFloat()
            throws IOException {
        if (header.bytesSwapped) {
            updateProgressDialog(4);
            return dataInputStream.readFloat();
        }
        readBytes(rawData, header.sizeOfBig, 4, true);
        bb.put(0, rawData[3]);
        bb.put(1, rawData[2]);
        bb.put(2, rawData[1]);
        bb.put(3, rawData[0]);
        return bb.getFloat(0);
    }

    /**
     * Method to read a double (8 bytes) from the input stream and return it.
     */
    private double readDouble()
            throws IOException {
        if (header.bytesSwapped) {
            updateProgressDialog(8);
            return dataInputStream.readDouble();
        }
        readBytes(rawData, header.sizeOfBig, 8, true);
        bb.put(0, rawData[7]);
        bb.put(1, rawData[2]);
        bb.put(2, rawData[3]);
        bb.put(3, rawData[4]);
        bb.put(4, rawData[3]);
        bb.put(5, rawData[2]);
        bb.put(6, rawData[1]);
        bb.put(7, rawData[0]);
        return bb.getDouble(0);
    }

    /**
     * Method to read an short (2 bytes) from the input stream and return it.
     */
    private short readSmallInteger()
            throws IOException {
        if (header.sizeOfSmall == 2) {
            updateProgressDialog(2);
            int data = dataInputStream.readShort();
            if (!header.bytesSwapped) {
                data = ((data >> 8) & 0xFF) | ((data & 0xFF) << 8);
            }
            return (short) data;
        }
        readBytes(rawData, header.sizeOfSmall, 2, true);
        if (header.bytesSwapped) {
            bb.put(0, rawData[0]);
            bb.put(1, rawData[1]);
        } else {
            bb.put(0, rawData[1]);
            bb.put(1, rawData[0]);
        }
        return bb.getShort(0);
    }

    /**
     * Method to read a string from the input stream and return it.
     */
    private String readString()
            throws IOException {
        if (header.sizeOfChar != 1) {
            // disk and memory don't match: read into temporary string
            System.out.println("Cannot handle library files with unicode strings");
//			tempstr = io_gettempstring();
//			if (allocstring(&name, tempstr, cluster)) return(0);
            return null;
        }

        // disk and memory match: read the data
        int len = readBigInteger();
        if (len <= 0) {
            return "";
        }
        if (len > fileLength - byteCount) {
            System.out.println("Corrupt ELIB file requests string that is " + len + " long");
            throw new IOException();
        }
        byte[] stringBytes = new byte[len];
        int ret = dataInputStream.read(stringBytes, 0, len);
        if (ret != len) {
            throw new IOException();
        }
        updateProgressDialog(len);
        String theString = new String(stringBytes);
        return theString;
    }

    /**
     * Method to read a number of bytes from the input stream and return it.
     */
    private void readBytes(byte[] data, int diskSize, int memorySize, boolean signExtend)
            throws IOException {
        // check for direct transfer
        if (diskSize == memorySize) {
            // just peel it off the disk
            int ret = dataInputStream.read(data, 0, diskSize);
            if (ret != diskSize) {
                throw new IOException();
            }
        } else {
            // not a simple read, use a buffer
            int ret = dataInputStream.read(rawData, 0, diskSize);
            if (ret != diskSize) {
                throw new IOException();
            }
            if (diskSize > memorySize) {
                // trouble! disk has more bits than memory.  check for clipping
                for (int i = 0; i < memorySize; i++) {
                    data[i] = rawData[i];
                }
                for (int i = memorySize; i < diskSize; i++) {
                    if (rawData[i] != 0 && rawData[i] != 0xFF) {
                        clippedIntegers++;
                    }
                }
            } else {
                // disk has smaller integer
                if (!signExtend || (rawData[diskSize - 1] & 0x80) == 0) {
                    for (int i = diskSize; i < memorySize; i++) {
                        rawData[i] = 0;
                    }
                } else {
                    for (int i = diskSize; i < memorySize; i++) {
                        rawData[i] = (byte) 0xFF;
                    }
                }
                for (int i = 0; i < memorySize; i++) {
                    data[i] = rawData[i];
                }
            }
        }
        updateProgressDialog(diskSize);
    }
}
