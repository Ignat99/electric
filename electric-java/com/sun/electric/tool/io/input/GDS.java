/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: GDS.java
 * Input/output tool: GDS input
 * Original C code written by Glen M. Lawson, S-MOS Systems, Inc.
 * Translated into Java by Steven M. Rubin.
 *
 * Copyright (c) 2004, Static Free Software. All rights reserved.
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
import com.sun.electric.database.ImmutableExport;
import com.sun.electric.database.ImmutableNodeInst;
import com.sun.electric.database.geometry.EPoint;
import com.sun.electric.database.geometry.ERectangle;
import com.sun.electric.database.geometry.GeometryHandler;
import com.sun.electric.database.geometry.Poly;
import com.sun.electric.database.geometry.PolyBase;
import com.sun.electric.database.geometry.PolyMerge;
import com.sun.electric.database.geometry.PolySweepMerge;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.EDatabase;
import com.sun.electric.database.hierarchy.Export;
import com.sun.electric.database.hierarchy.Library;
import com.sun.electric.database.id.CellId;
import com.sun.electric.database.id.ExportId;
import com.sun.electric.database.id.PortProtoId;
import com.sun.electric.database.prototype.NodeProto;
import com.sun.electric.database.prototype.PortCharacteristic;
import com.sun.electric.database.prototype.PortProto;
import com.sun.electric.database.text.Name;
import com.sun.electric.database.text.TextUtils;
import com.sun.electric.database.topology.Geometric;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.variable.ElectricObject;
import com.sun.electric.database.variable.MutableTextDescriptor;
import com.sun.electric.database.variable.TextDescriptor;
import com.sun.electric.database.variable.Variable;
import com.sun.electric.technology.ArcProto;
import com.sun.electric.technology.Layer;
import com.sun.electric.technology.PrimitiveNode;
import com.sun.electric.technology.SizeOffset;
import com.sun.electric.technology.Technology;
import com.sun.electric.technology.Technology.NodeLayer;
import com.sun.electric.technology.technologies.Artwork;
import com.sun.electric.technology.technologies.Generic;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.io.FileType;
import com.sun.electric.tool.io.GDSReader;
import com.sun.electric.tool.io.GDSReader.GSymbol;
import com.sun.electric.tool.io.IOTool;
import com.sun.electric.tool.ncc.basic.NccCellAnnotations;
import com.sun.electric.tool.user.dialogs.OpenFile;
import com.sun.electric.tool.user.ui.LayerVisibility;
import com.sun.electric.util.math.DBMath;
import com.sun.electric.util.math.FixpCoord;
import com.sun.electric.util.math.GenMath;
import com.sun.electric.util.math.MutableInteger;
import com.sun.electric.util.math.Orientation;

import java.awt.Point;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * This class reads files in GDS files.
 * <BR>
 * Notes:
 * <UL>
 * <LI>Case sensitive.</LI>
 * <LI>NODEs, TEXTNODEs, BOXs - don't have an example.</LI>
 * <LI>PATHTYPE 1 - rounded ends on paths, not supported.</LI>
 * <LI>Path dogears - no cleanup yet, simpler to map paths into arcs.</LI>
 * <LI>Absolute angle - ???</LI>
 * <LI>SUBLAYERS or XXXTYPE fields are not supported.</LI>
 * <LI>PROPERTIES are not supported.</LI>
 * <LI>REFLIBS are not supported.</LI>
 * <LI>PATH-ARC mapping - should be done, problem is that any layer can be a path, only connection layers in Electric are arcs.
 *	Someone could make a GDS mapping technology for this purpose, defaults could be taken from this technology.</LI>
 * <LI>Miscellaneous fields mapped to variables - should be done.</LI>
 * <LI>MAG - no scaling is possible, must create a separate object for each value, don't scale.  (TEXT does scale.)</LI>
 * </UL>
 */
public class GDS extends Input<Object>
{
	private static final boolean SHOWPROGRESS = false;			/* true for debugging */
	private static final boolean TALLYCONTENTS = false;			/* true for debugging */

	/** key of Variable holding original GDS file. */		public static final Variable.Key SKELETON_ORIGIN = Variable.newKey("ATTR_GDS_original");
	/** key of Variable holding original export name. */	public static final Variable.Key ORIGINAL_EXPORT_NAME = Variable.newKey("GDS_original_export_name");

    public static final boolean CADENCE_GROWS_SIZE = true;
    public static final boolean INSTANTIATE_ARRAYS_VIA_BISECTION = true;

	// data declarations
	private static final int MAXPOINTS     = 4096;
	private static final int MINFONTWIDTH  =  130;
	private static final int MINFONTHEIGHT =  190;

	private static class ShapeType {}
	private static final ShapeType SHAPEPOLY      = new ShapeType();
	private static final ShapeType SHAPERECTANGLE = new ShapeType();
	private static final ShapeType SHAPEOBLIQUE   = new ShapeType();
	private static final ShapeType SHAPELINE      = new ShapeType();
	private static final ShapeType SHAPECLOSED    = new ShapeType();

	private GDSReader        gdsRead;
	private int              countBox, countText, countNode, countPath, countShape, countSRef, countARef, countATotal;
	private Library          theLibrary;
    private CellArrayBuilder  cellArrayBuilder;
    private Map<Library,Cell> currentCells;
	private CellBuilder      theCell;
	private NodeProto        theNodeProto;
	private PrimitiveNode    layerNodeProto;
	private UnknownLayerMessage currentUnknownLayerMessage;
    private PrimitiveNode    pinNodeProto;
	private int              randomLayerSelection;
	private boolean          layerIsPin;
	private Technology       curTech;
	private int              curLayerNum, curLayerType;
	private Point2D []       theVertices;
	private int              numVertices;
	private double           theScale;
	private Map<Integer,List<Layer>> layerNames; // can be a list of layers for example Diff layers 
	private Map<Integer,UnknownLayerMessage> layerErrorMessages;
	private static Map<Integer,UnknownLayerMessage> layerWarningMessages;
	private static Map<UnknownLayerMessage,Set<Cell>> cellLayerErrors;
	private Set<Integer>     pinLayers;
	private PolyMerge        merge;
	private static boolean   arraySimplificationUseful;
	private Set<Cell>        missingCells;
	private MakeInstance     lastExportInstance = null;
	private PrintWriter      printWriter;

	private static GSymbol [] optionSet = {GDSReader.GDS_ATTRTABLE, GDSReader.GDS_REFLIBS, GDSReader.GDS_FONTS, GDSReader.GDS_GENERATIONS};
	private static GSymbol [] shapeSet = {GDSReader.GDS_AREF, GDSReader.GDS_SREF, GDSReader.GDS_BOUNDARY, GDSReader.GDS_PATH,
		GDSReader.GDS_NODE, GDSReader.GDS_TEXTSYM, GDSReader.GDS_BOX};
	private static GSymbol [] goodOpSet = {GDSReader.GDS_HEADER, GDSReader.GDS_BGNLIB, GDSReader.GDS_LIBNAME, GDSReader.GDS_UNITS,
		GDSReader.GDS_ENDLIB, GDSReader.GDS_BGNSTR, GDSReader.GDS_STRNAME, GDSReader.GDS_ENDSTR, GDSReader.GDS_BOUNDARY,
		GDSReader.GDS_PATH, GDSReader.GDS_SREF, GDSReader.GDS_AREF, GDSReader.GDS_TEXTSYM, GDSReader.GDS_LAYER, GDSReader.GDS_DATATYPSYM,
		GDSReader.GDS_WIDTH, GDSReader.GDS_XY, GDSReader.GDS_ENDEL, GDSReader.GDS_SNAME, GDSReader.GDS_COLROW, GDSReader.GDS_TEXTNODE,
		GDSReader.GDS_NODE, GDSReader.GDS_TEXTTYPE, GDSReader.GDS_PRESENTATION, GDSReader.GDS_STRING, GDSReader.GDS_STRANS,
		GDSReader.GDS_MAG, GDSReader.GDS_ANGLE, GDSReader.GDS_REFLIBS, GDSReader.GDS_FONTS, GDSReader.GDS_PATHTYPE,
		GDSReader.GDS_GENERATIONS, GDSReader.GDS_ATTRTABLE, GDSReader.GDS_NODETYPE, GDSReader.GDS_PROPATTR, GDSReader.GDS_PROPVALUE,
		GDSReader.GDS_BOX, GDSReader.GDS_BOXTYPE, GDSReader.GDS_FORMAT, GDSReader.GDS_MASK, GDSReader.GDS_ENDMASKS};
	private static GSymbol [] maskSet = {GDSReader.GDS_DATATYPSYM, GDSReader.GDS_TEXTTYPE, GDSReader.GDS_BOXTYPE, GDSReader.GDS_NODETYPE};
	private static GSymbol [] unsupportedSet = {GDSReader.GDS_ELFLAGS, GDSReader.GDS_PLEX};

	private GDSPreferences localPrefs;

	public static class GDSPreferences extends InputPreferences
    {
		public double inputScale;
		public boolean simplifyCells;
		public int arraySimplification;
		public boolean instantiateArrays;
		public boolean expandCells;
		public boolean mergeBoxes;
		public boolean includeText;
		public int unknownLayerHandling;
		public boolean cadenceCompatibility;
		public boolean dumpReadable;
		public boolean skeletonize;
        boolean onlyVisibleLayers;
        boolean[] visibility;
        boolean[][] techVisibility;
        int defaultTextLayer;

		public GDSPreferences(boolean factory)
		{
			super(factory);
			skeletonize = false;
            defaultTextLayer = IOTool.getGDSDefaultTextLayer();
			if (factory)
			{
				inputScale = IOTool.getFactoryGDSInputScale();
				simplifyCells = IOTool.isFactoryGDSInSimplifyCells();
				arraySimplification = IOTool.getFactoryGDSArraySimplification();
				instantiateArrays = IOTool.isFactoryGDSInInstantiatesArrays();
				expandCells = IOTool.isFactoryGDSInExpandsCells();
				mergeBoxes = IOTool.isFactoryGDSInMergesBoxes();
				includeText = IOTool.isFactoryGDSIncludesText();
				unknownLayerHandling = IOTool.getFactoryGDSInUnknownLayerHandling();
				cadenceCompatibility = IOTool.isFactoryGDSCadenceCompatibility();
				dumpReadable = IOTool.isFactoryGDSDumpReadable();
				onlyVisibleLayers = IOTool.isFactoryGDSOnlyInvisibleLayers();
			} else
            {
                inputScale = IOTool.getGDSInputScale();
                simplifyCells = IOTool.isGDSInSimplifyCells();
                arraySimplification = IOTool.getGDSArraySimplification();
                instantiateArrays = IOTool.isGDSInInstantiatesArrays();
                expandCells = IOTool.isGDSInExpandsCells();
                mergeBoxes = IOTool.isGDSInMergesBoxes();
                includeText = IOTool.isGDSIncludesText();
                unknownLayerHandling = IOTool.getGDSInUnknownLayerHandling();
                cadenceCompatibility = IOTool.isGDSCadenceCompatibility();
				dumpReadable = IOTool.isGDSDumpReadable();
                onlyVisibleLayers = IOTool.isGDSOnlyInvisibleLayers();
            }
			if (onlyVisibleLayers)
				techVisibility = LayerVisibility.getLayerVisibility().getTechDataArray();
		}

        @Override
		public void setSkeleton(boolean sk)
		{
        	skeletonize = sk;
		}

        @Override
        public Library doInput(URL fileURL, Library lib, Technology tech, EditingPreferences ep, Map<Library,Cell> currentCells,
        	Map<CellId,BitSet> nodesToExpand, Job job)
        {
        	GDS in = new GDS(ep, this);
			if (in.openBinaryInput(fileURL)) return null;

			// cache visibility only when it knows the tech
			if (techVisibility != null)
				visibility = techVisibility[tech.getId().techIndex];
			
			// create a low-level GDS reader
			in.gdsRead = new GDSReader(in.filePath, in.dataInputStream, in.fileLength);

            // Libraries before loading
            Set<Library> oldLibs = new HashSet<Library>();
            for (Iterator<Library> it = Library.getLibraries(); it.hasNext(); )
                oldLibs.add(it.next());
            oldLibs.remove(lib);

			lib = in.importALibrary(lib, tech, currentCells);
			in.closeInput();

            if (expandCells) {
                // Expand subCells
                EDatabase database = EDatabase.currentDatabase();
                for (Iterator<Library> it = Library.getLibraries(); it.hasNext(); ) {
                    Library l = it.next();
                    if (oldLibs.contains(l)) continue;
                    for (Iterator<Cell> cit = l.getCells(); cit.hasNext(); ) {
                        Cell cell =cit.next();
                        for (Iterator<NodeInst> nit = cell.getNodes(); nit.hasNext(); ) {
                            NodeInst ni = nit.next();
                            if (ni.isCellInstance())
                                database.addToNodes(nodesToExpand, ni);
                        }
                    }
                }
            }

			return lib;
        }
    }

	/**
	 * Creates a new instance of GDS.
	 */
	public GDS(EditingPreferences ep, GDSPreferences ap) {
        super(ep);
        localPrefs = ap;
    }

	/**
	 * Method to import a library from disk.
	 * @param lib the library to fill
     * @param currentCells this map will be filled with currentCells in Libraries found in library file
	 * @return the created library (null on error).
	 */
    @Override
	protected Library importALibrary(Library lib, Technology tech, Map<Library,Cell> currentCells)
	{
		// initialize
        this.currentCells = currentCells;
		arraySimplificationUseful = false;
		init();
		theLibrary = lib;
        cellArrayBuilder = new CellArrayBuilder(theLibrary);
		curTech = tech;
		initialize();

		try
		{
			loadFile();
        } catch (IllegalArgumentException e)
        {
            System.out.println("ERROR reading GDS file: " + e.getMessage());
            if (Job.getDebug())
                e.printStackTrace();
            return null;
        }
		catch (GDSReader.GDSException e)
        {
	        Cell cell = theCell != null ? theCell.cell : null;
	        String message = e.getMessage();
	        System.out.println(message);
	        errorLogger.logError(message, cell, 0);
            return null;
        }
	    catch (Exception e)
		{
            System.out.println("ERROR reading GDS file: check input file, " + e.getMessage());
            if (Job.getDebug())
                e.printStackTrace();
            return null;
        }

        // fix references to unknown cells that may be in other libraries
        Map<Cell,Cell> foundCellMap = substituteExternalCells(missingCells, theLibrary);
        if (foundCellMap.size() > 0)
        {
        	System.out.println("Note: these cells from other libraries were referenced in the GDS:");
        	for(Cell mCell : foundCellMap.keySet())
        	{
        		Cell found = foundCellMap.get(mCell);
        		System.out.println("    " + found.libDescribe());
        		missingCells.remove(mCell);
        	}
        }
        if (missingCells.size() > 0)
        {
        	System.out.println("Note: these cells are missing in the GDS and were created with no contents:");
        	for(Cell cell : missingCells)
        		System.out.println("    " + cell.noLibDescribe());
        }

		// now build all instances recursively
		buildInstances();
		term();

		// show unknown error messages
		printUnknownLayersInCell(cellLayerErrors);
		
		// show warning messages
		for (UnknownLayerMessage message : layerWarningMessages.values())
		{
			if (message == null) continue;
			System.out.println(message.message);
        	errorLogger.logWarning(message.message, null, -1);
		}

		if (arraySimplificationUseful)
		{
			System.out.println("NOTE: Found array references that could be simplified to save space and time");
			System.out.println("   To simplify arrays, set the 'Input array simplification' in GDS Preferences");
		}
		return lib;
	}

    private void printUnknownLayersInCell(Map<UnknownLayerMessage,Set<Cell>> map)
    {
    	if (map == null) return;
    	
    	for(UnknownLayerMessage message : map.keySet())
		{
			Set<Cell> cellList = map.get(message);
			System.out.println(message.message + " in cells:");
			String prev = "    ";
            int count = 0;
            for(Cell cell : cellList)
			{
				System.out.print(prev + cell.describe(false));
				prev = ", ";
                // break into lines otherwise the message line is too long
                if (count > 10)
                {
                    count = 0;
                    System.out.print("\n\t");
                }
                count++;
            }
			System.out.println();
		}
    }
    
	private void initialize()
	{
		layerNodeProto = Generic.tech().drcNode;
		missingCells = new HashSet<Cell>();

		theVertices = new Point2D[MAXPOINTS];
		for(int i=0; i<MAXPOINTS; i++) theVertices[i] = new Point2D.Double();

		// get the array of GDS names
		layerErrorMessages = new HashMap<Integer,UnknownLayerMessage>();
		layerWarningMessages = new HashMap<Integer,UnknownLayerMessage>();
		cellLayerErrors = new HashMap<UnknownLayerMessage,Set<Cell>>();
		pinLayers = new HashSet<Integer>();
		randomLayerSelection = 0;
		layerNames = curTech.getLayersPerGDSNumber(pinLayers);
		boolean valid = !layerNames.isEmpty();
		
		if (!valid)
		{
			System.out.println("There are no GDS layer names assigned in the " + curTech.getTechName() + " technology");
		}
	}

	private Map<CellId,CellBuilder> allBuilders;

    private void init()
    {
        allBuilders = new HashMap<CellId,CellBuilder>();
//			cellsTooComplex = new HashSet<CellId>();
    }

    private void term()
    {
        allBuilders = null;
//			if (cellsTooComplex.size() > 0)
//			{
//				System.out.print("THESE CELLS WERE TOO COMPLEX AND NOT FULLY READ:");
//				for(CellId cellId : cellsTooComplex) System.out.print(" " + cellId/*.describe(false)*/);
//				System.out.println();
//			}
    }

    private static class SkeletonCellInstance
    {
    	NodeProto proto;
    	Point2D loc;
    	Orientation orient;
    	double wid, hei;

    	public SkeletonCellInstance(NodeProto proto, Point2D loc, Orientation orient, double wid, double hei)
    	{
    		this.proto = proto;
    		this.loc = loc;
    		this.orient = orient;
    		this.wid = wid;
    		this.hei = hei;
    	}
    }

    private class CellBuilder
    {
//		private static Set<CellId> cellsTooComplex;
		private GDSPreferences localPrefs;
		private Technology tech;
		private Cell cell;
        private List<MakeInstance> insts = new ArrayList<MakeInstance>();
        private Map<UnknownLayerMessage,List<MakeInstance>> allErrorInsts = new LinkedHashMap<UnknownLayerMessage,List<MakeInstance>>();
        private List<MakeInstanceArray> instArrays = new ArrayList<MakeInstanceArray>();
//        public GDS gds;

        private boolean topLevel;
        private int nodeId;

        private List<ImmutableNodeInst> nodesToCreate = new ArrayList<ImmutableNodeInst>();
        private Map<String,ImmutableExport> exportsByName = new HashMap<String,ImmutableExport>();
		private Set<String> alreadyExports = exportsByName.keySet();
		private Map<String, MutableInteger> nextExportPlainIndex = new HashMap<String, MutableInteger>();

        private Map<String, MutableInteger> maxSuffixes = new HashMap<String, MutableInteger>();
        private Set<String> userNames = new HashSet<String>();
        private MutableInteger count = new MutableInteger(0);

    	private boolean skeletonDefined;
    	private double skeletonLX, skeletonHX, skeletonLY, skeletonHY;
    	private List<SkeletonCellInstance> skeletonCellInstances;

        private CellBuilder(Cell cell, Technology tech, GDSPreferences localPrefs) {
            this.cell = cell;
            this.tech = tech;
            this.localPrefs = localPrefs;
            allBuilders.put(cell.getId(), this);
            skeletonDefined = false;
            skeletonLX = skeletonHX = skeletonLY = skeletonHY = 0;
            skeletonCellInstances = new ArrayList<SkeletonCellInstance>();
            topLevel = true;

            // sanity
            Set<Export> exportsToKill = new HashSet<Export>();
            for (Iterator<Export> it = cell.getExports(); it.hasNext(); )
                exportsToKill.add(it.next());
            cell.killExports(exportsToKill);
            allBuilders.put(cell.getId(), this);
        }

		private void makeInstance(NodeProto proto, Point2D loc, Orientation orient, double wid, double hei,
			EPoint[] points, UnknownLayerMessage ulm)
		{
			if (proto == null) return;
			if (localPrefs.skeletonize)
			{
				if (proto instanceof Cell)
				{
					skeletonCellInstances.add(new SkeletonCellInstance(proto, loc, orient, wid, hei));
				} else
				{
					doSkeleton(proto, loc, orient, wid, hei, points);
				}
    			return;
			}

			String name = (ulm != null) ? ulm.nodeName: null;
			MakeInstance mi = new MakeInstance(this, proto, loc, orient, wid, hei, points, null, Name.findName(name), false);
			insts.add(mi);
			if (ulm != null)
			{
                List<MakeInstance> errorList = allErrorInsts.get(ulm);
                if (errorList == null) allErrorInsts.put(ulm, errorList = new ArrayList<MakeInstance>());
                errorList.add(mi);
			}
        }

		private void makeInstanceArray(NodeProto proto, int nCols, int nRows, Orientation orient,
			Point2D startLoc, Point2D rowOffset, Point2D colOffset)
		{
			if (localPrefs.skeletonize)
			{
	    		// generate an array
	    		double ptcX = startLoc.getX();
	    		double ptcY = startLoc.getY();
	    		for (int ic = 0; ic < nCols; ic++)
	    		{
	    			double ptX = ptcX;
	    			double ptY = ptcY;
	    			for (int ir = 0; ir < nRows; ir++)
	    			{
	    				// create the node
    					Point2D loc = new Point2D.Double(ptX, ptY);
    					double wid = proto.getDefWidth(ep);
    					double hei = proto.getDefHeight(ep);
    					if (proto instanceof Cell)
    					{
        					skeletonCellInstances.add(new SkeletonCellInstance(proto, loc, orient, wid, hei));
    					} else
    					{
    						doSkeleton(proto, loc, orient, wid, hei, null);
    					}

	    				// add the row displacement
	    				ptX += rowOffset.getX();   ptY += rowOffset.getY();
	    			}

	    			// add displacement
	    			ptcX += colOffset.getX();   ptcY += colOffset.getY();
	    		}
	    		return;
			}
            MakeInstanceArray mia =
                new MakeInstanceArray(proto, nCols, nRows, orient, new Point2D.Double(startLoc.getX(), startLoc.getY()),
                                      rowOffset, colOffset, localPrefs);
            instArrays.add(mia);
        }

		private void makeExport(NodeProto proto, Point2D loc, Orientation orient,
			String exportName, UnknownLayerMessage ulm)
		{
			if (localPrefs.cadenceCompatibility && exportName.contains("<") && exportName.contains("<"))
			{
				exportName = exportName.replace("<",  "[");
				exportName = exportName.replace(">",  "]");
			}
			
			if (proto != null && proto.getNumPorts() > 0)
			{
				double wid = proto.getDefWidth(ep);
				double hei = proto.getDefHeight(ep);
				if (localPrefs.skeletonize)
				{
					doSkeleton(proto, loc, orient, wid, hei, null);
	    			if (!topLevel) return;
				}
				lastExportInstance = new MakeInstance(this, proto, loc, orient, wid, hei, null, exportName, null, false);
		        insts.add(lastExportInstance);
				if (ulm != null)
				{
        			List<MakeInstance> errorList = allErrorInsts.get(ulm);
                	if (errorList == null) allErrorInsts.put(ulm, errorList = new ArrayList<MakeInstance>());
                    errorList.add(lastExportInstance);
				}
			}
        }

		private void makeText(NodeProto proto, Point2D loc, String text,
			TextDescriptor textDescriptor, UnknownLayerMessage ulm)
		{
			if (proto == null) return;
			if (localPrefs.skeletonize)
			{
				doSkeleton(proto, loc, Orientation.IDENT, 0, 0, null);
    			return;
			}

			MakeInstance mi = new MakeInstance(this, proto, loc, Orientation.IDENT, 0, 0, null, text, null, true);
			insts.add(mi);
			if (ulm != null)
			{
                List<MakeInstance> errorList = allErrorInsts.get(ulm);
                if (errorList == null) allErrorInsts.put(ulm, errorList = new ArrayList<MakeInstance>());
                errorList.add(mi);
			}
        }

		private void makeInstances(Set<CellId> builtCells)
		{
            if (builtCells.contains(cell.getId())) return;
            builtCells.add(cell.getId());

            // Traverse all subcells
			for(MakeInstance mi : insts)
			{
                if (mi.proto instanceof Cell) {
                    Cell subCell = (Cell)mi.proto;
                    CellBuilder cellBuilder = allBuilders.get(subCell.getId());
                    if (cellBuilder != null)
                    {
                        cellBuilder.makeInstances(builtCells);
                        cellBuilder.topLevel = false;
                    }
                }
			}
			for(MakeInstanceArray mia : instArrays)
			{
                if (mia.proto instanceof Cell) {
                    Cell subCell = (Cell)mia.proto;
                    CellBuilder cellBuilder = allBuilders.get(subCell.getId());
                    if (cellBuilder != null)
                    {
                        cellBuilder.makeInstances(builtCells);
                        cellBuilder.topLevel = false;
                    }
                }
			}

            makeInstances();
            
            if (localPrefs.skeletonize)
            {
	        	// add essential bounds nodes in top cell
	        	PrimitiveNode corner = Generic.tech().essentialBoundsNode;
	        	double width = corner.getDefWidth(ep);
	        	double height = corner.getDefHeight(ep);
	        	NodeInst.makeInstance(corner, ep, new Point2D.Double(skeletonHX, skeletonHY), width, height, cell, Orientation.IDENT, null);
	        	NodeInst.makeInstance(corner, ep, new Point2D.Double(skeletonLX, skeletonHY), width, height, cell, Orientation.R, null);
	        	NodeInst.makeInstance(corner, ep, new Point2D.Double(skeletonLX, skeletonLY), width, height, cell, Orientation.RR, null);
	        	NodeInst.makeInstance(corner, ep, new Point2D.Double(skeletonHX, skeletonLY), width, height, cell, Orientation.RRR, null);
            }

			assert builtCells.contains(this.cell.getId());
        }

        private void makeInstances()
        {
			boolean countOff = false;
			if (SHOWPROGRESS)
			{
				int size = insts.size();
				int arraySize = instArrays.size();
				System.out.println("Building cell " + this.cell.describe(false) +
					" with " + size + " single instances and " + arraySize + " arrayed instances");
				if (size+arraySize >= 100000)
					countOff = true;
			}

			int count = 0;
			Map<String,String> exportUnify = new HashMap<String,String>();

			// first make the geometry and instances
			for(MakeInstance mi : insts)
			{
				if (mi.exportOrTextName != null) continue;
				if (countOff && ((++count % 1000) == 0))
					System.out.println("        Made " + count + " instances");

				// make the instance
                mi.instantiate(this, exportUnify, null);
			}
            createNodes();

			// next make the exports
			for(MakeInstance mi : insts)
			{
				if (mi.exportOrTextName == null) continue;
				if (countOff && ((++count % 1000) == 0))
					System.out.println("        Made " + count + " instances");

                if (localPrefs.cadenceCompatibility)
                {
	                // center the export if possible
	                ArcProto theArc = mi.proto.getPort(0).getBasePort().getConnections()[0];
	                Layer theLayer = theArc.getLayer(0);
	                Set<NodeProto> possibleProtos = new HashSet<NodeProto>();
	                PrimitiveNode pNp = theLayer.getPureLayerNode();
	                if (pNp != null)
	                {
	                	possibleProtos.add(pNp);
		                for(Iterator<Layer> it = pNp.getTechnology().getLayers(); it.hasNext(); )
		                {
		                	Layer poss = it.next();
		                	if (poss == theLayer) continue;
		                	if (poss.getFunction().isMetal())
		                	{
			                	if (poss.getFunction().getLevel() == theLayer.getFunction().getLevel())
			                		possibleProtos.add(poss.getPureLayerNode());
		                	}
		                }
		                Rectangle2D search = new Rectangle2D.Double(mi.loc.getX(), mi.loc.getY(), 0, 0);
		        		for(Iterator<Geometric> it = cell.searchIterator(search); it.hasNext(); )
		        		{
		        			Geometric geom = it.next();
		        			if (geom instanceof NodeInst)
		        			{
		        				NodeInst ni = (NodeInst)geom;
		        				if (!possibleProtos.contains(ni.getProto())) continue;
								Rectangle2D pointBounds = ni.getBounds();
								double cX = pointBounds.getCenterX();
								double cY = pointBounds.getCenterY();
		        				EPoint [] trace = ni.getTrace();
		        				if (trace != null)
		        				{
									PolyBase.Point [] newPoints = new PolyBase.Point[trace.length];
									for(int i=0; i<trace.length; i++)
									{
										if (trace[i] != null)
											newPoints[i] = PolyBase.fromLambda(trace[i].getX()+cX, trace[i].getY()+cY);
									}
									PolyBase poly = new PolyBase(newPoints);
									poly.transform(ni.rotateOut());
		        					if (poly.contains(mi.loc))
		        					{
		        						GeometryHandler thisMerge = GeometryHandler.createGeometryHandler(GeometryHandler.GHMode.ALGO_SWEEP, 1);
		        						thisMerge.add(theLayer, poly);
		        						thisMerge.postProcess(true);
		        			            Collection<PolyBase> set = ((PolySweepMerge)thisMerge).getPolyPartition(theLayer);
		        						for(PolyBase simplePoly : set)
		        						{
		        							Rectangle2D polyBounds = simplePoly.getBounds2D();
		        							if (polyBounds.contains(mi.loc))
		        							{
		    	        						mi.loc.setLocation(polyBounds.getCenterX(), polyBounds.getCenterY());
		    	        						break;
		        							}
		        						}
		        						break;
		        					}
		        				} else
		        				{
		        					if (pointBounds.contains(mi.loc))
		        					{
		        						mi.loc.setLocation(cX, cY);
		        						if (CADENCE_GROWS_SIZE)
		        						{
											double minSize = Math.min(pointBounds.getWidth(), pointBounds.getHeight());
											if (mi.wid < minSize) mi.wid = minSize;
											if (mi.hei < minSize) mi.hei = minSize;
		        						}
		        						break;
		        					}
		        				}
		        			}
		        		}
	                }

	        		// grid-align the export location
	        		double scaledResolution = tech.getFactoryResolution().getLambda();
	        		if (scaledResolution > 0)
	        		{
						double x = Math.round(mi.loc.getX() / scaledResolution) * scaledResolution;
						double y = Math.round(mi.loc.getY() / scaledResolution) * scaledResolution;
						mi.loc.setLocation(x, y);
	        		}
                }

                // make the instance
                mi.instantiate(this, exportUnify, null);
			}

			for(UnknownLayerMessage ulm : allErrorInsts.keySet())
			{
        		List<ImmutableNodeInst> instantiated = new ArrayList<ImmutableNodeInst>();
				List<MakeInstance> errorList = allErrorInsts.get(ulm);
				for(MakeInstance mi : errorList)
				{
					if (mi == null) continue;
					if (countOff && ((++count % 1000) == 0))
						System.out.println("        Made " + count + " instances");

					// make the instance
                    if (mi.n != null) instantiated.add(mi.n);
				}
				String msg = "Cell " + this.cell.noLibDescribe() + ": " + ulm.message;
				Set<Cell> cellsWithError = cellLayerErrors.get(ulm);
				if (cellsWithError == null) cellLayerErrors.put(ulm, cellsWithError = new TreeSet<Cell>());
				cellsWithError.add(cell);
				errorLogger.logMessage(msg, instantiated, cell, -1, true);
			}
            createNodes();
            
			Map<NodeProto,List<EPoint>> massiveMerge = new HashMap<NodeProto,List<EPoint>>();
			for(MakeInstanceArray mia : instArrays)
			{
				if (countOff && ((++count % 1000) == 0))
					System.out.println("        Made " + count + " instances");

				// make the instance array
                mia.instantiate(this, massiveMerge);
			}
			List<NodeProto> mergeNodeSet = new ArrayList<NodeProto>();
			for(NodeProto np : massiveMerge.keySet()) mergeNodeSet.add(np);
			for(NodeProto np : mergeNodeSet)
			{
				// place a pure-layer node that embodies all arrays for the whole cell
				List<EPoint> points = massiveMerge.get(np);
				buildComplexNode(this, points, np, this.cell, ep);
			}
            cell.addExports(exportsByName.values());

			if (!exportUnify.isEmpty())
			{
				System.out.println("Cell " + this.cell.describe(false) + ": Renamed and NCC-unified " + exportUnify.size() +
					" exports with duplicate names");
				Map<String,String> unifyStrings = new HashMap<String,String>();
				Set<String> finalNames = exportUnify.keySet();
				for(String finalName : finalNames)
				{
					String singleName = exportUnify.get(finalName);
					String us = unifyStrings.get(singleName);
					if (us == null) us = singleName;
					us += " " + finalName;
					unifyStrings.put(singleName, us);
				}
				List<String> annotations = new ArrayList<String>();
				for(String us : unifyStrings.keySet())
					annotations.add("exportsConnectedByParent " + unifyStrings.get(us));
				if (annotations.size() > 0)
				{
					String [] anArr = new String[annotations.size()];
					for(int i=0; i<annotations.size(); i++) anArr[i] = annotations.get(i);
					TextDescriptor td = ep.getCellTextDescriptor().withInterior(true).withDispPart(TextDescriptor.DispPos.NAMEVALUE);
					this.cell.newVar(NccCellAnnotations.NCC_ANNOTATION_KEY, anArr, td);
				}
			}
            if (localPrefs.simplifyCells && !localPrefs.skeletonize)
                simplifyNodes(this.cell, tech);
		}

        private void createNodes() {
            cell.addNodes(nodesToCreate);
            nodesToCreate.clear();
        }

        private void doSkeleton(NodeProto proto, Point2D loc, Orientation orient, double wid, double hei, EPoint[] points)
        {
			double lX=0, hX=0, lY=0, hY=0;
            if (proto instanceof PrimitiveNode)
            {
            	if (points != null)
            	{
            		lX = hX = points[0].getX();
            		lY = hY = points[0].getY();
            		for(int i=1; i<points.length; i++)
            		{
            			if (points[i].getX() < lX) lX = points[i].getX();
            			if (points[i].getX() > hX) hX = points[i].getX();
            			if (points[i].getY() < lY) lY = points[i].getY();
            			if (points[i].getY() > hY) hY = points[i].getY();
            		}
            	} else
            	{
		            double sX = DBMath.round(wid);
		            double sY = DBMath.round(hei);
	    			if (orient.getAngle() == 900 || orient.getAngle() == 2700)
	    			{
	    				double swap = sX;   sX = sY;   sY = swap;
	    			}
	    			lX = loc.getX() - sX/2;
	    			hX = loc.getX() + sX/2;
	    			lY = loc.getY() - sY/2;
	    			hY = loc.getY() + sY/2;
            	}
            } else
			{
                CellBuilder subCB = allBuilders.get(proto.getId());
                if (subCB != null)
                {
    				lX = subCB.skeletonLX;
    				hX = subCB.skeletonHX;
    				lY = subCB.skeletonLY;
    				hY = subCB.skeletonHY;
    				if (orient != Orientation.IDENT)
    				{
	    				Point2D p1 = orient.transformPoint(new Point2D.Double(lX, lY));
	    				Point2D p2 = orient.transformPoint(new Point2D.Double(lX, hY));
	    				Point2D p3 = orient.transformPoint(new Point2D.Double(hX, hY));
	    				Point2D p4 = orient.transformPoint(new Point2D.Double(hX, lY));
	    				lX = Math.min(Math.min(p1.getX(), p2.getX()), Math.min(p3.getX(), p4.getX()));
	    				hX = Math.max(Math.max(p1.getX(), p2.getX()), Math.max(p3.getX(), p4.getX()));
	    				lY = Math.min(Math.min(p1.getY(), p2.getY()), Math.min(p3.getY(), p4.getY()));
	    				hY = Math.max(Math.max(p1.getY(), p2.getY()), Math.max(p3.getY(), p4.getY()));
    				}
    				lX += loc.getX();
    				hX += loc.getX();
    				lY += loc.getY();
    				hY += loc.getY();  
    				subCB.topLevel = false;
                }
			}
			if (skeletonDefined)
			{
				skeletonLX = Math.min(skeletonLX, lX);
				skeletonHX = Math.max(skeletonHX, hX);
				skeletonLY = Math.min(skeletonLY, lY);
				skeletonHY = Math.max(skeletonHY, hY);
			} else
			{
				skeletonLX = lX;
				skeletonHX = hX;
				skeletonLY = lY;
				skeletonHY = hY;
				skeletonDefined = true;
			}
        }

        /**
         * Method to see if existing primitive nodesToCreate could be merged and define more complex nodesToCreate
         * such as contacts
         */
        private void simplifyNodes(Cell cell, Technology tech)
        {
            Map<Layer, List<NodeInst>> map = new HashMap<Layer, List<NodeInst>>();

            for (Iterator<NodeInst> itNi = cell.getNodes(); itNi.hasNext();)
            {
                NodeInst ni = itNi.next();
                if (!(ni.getProto() instanceof PrimitiveNode)) continue; // not primitive
                PrimitiveNode pn = (PrimitiveNode)ni.getProto();
                if (pn.getFunction() != PrimitiveNode.Function.NODE) continue; // not pure layer node.
                Layer layer = pn.getLayerIterator().next(); // they are supposed to have only 1
                List<NodeInst> list = map.get(layer);

                if (list == null) // first time
                {
                    list = new ArrayList<NodeInst>();
                    map.put(layer, list);
                }
                list.add(ni);
            }

            Set<NodeInst> toDelete = new HashSet<NodeInst>();
            Set<NodeInst> viaToDelete = new HashSet<NodeInst>();
            List<Geometric> geomList = new ArrayList<Geometric>();

            for (Iterator<PrimitiveNode> itPn = tech.getNodes(); itPn.hasNext();)
            {
                PrimitiveNode pn = itPn.next();
                boolean allFound = true;
                if (!pn.getFunction().isContact()) continue; // only dealing with metal contacts for now.

                Layer m1Layer = null, m2Layer = null;
                Layer viaLayer = null;
                SizeOffset so = pn.getProtoSizeOffset();

                for (Iterator<Layer> itLa = pn.getLayerIterator(); itLa.hasNext();)
                {
                    Layer l = itLa.next();
                    if (map.get(l) == null)
                    {
                        allFound = false;
                        break;
                    }
                    if (l.getFunction().isMetal())
                    {
                        if (m1Layer == null)
                            m1Layer = l;
                        else
                            m2Layer = l;
                    }
                    else if (l.getFunction().isContact())
                        viaLayer = l;
                }
                if (!allFound) continue; // not all layers for this particular node found
                if (viaLayer == null) continue; // not metal contact
                assert(m1Layer != null);
                List<NodeInst> list = map.get(m1Layer);
                assert(list != null);
                Layer.Function.Set thisLayer = new Layer.Function.Set(viaLayer.getFunction());
                List<NodeInst> viasList = map.get(viaLayer);

                for (NodeInst ni : list)
                {
                    Poly[] polys = tech.getShapeOfNode(ni, true, false, null);
                    assert(polys.length == 1); // it must be only 1
                    Poly m1P = polys[0];
                    List<NodeInst> nList = map.get(m2Layer);
                    if (nList == null) continue; // nothing found in m2Layer
                    for (NodeInst n : nList)
                    {
                        Poly[] otherPolys = tech.getShapeOfNode(n, true, false, null);
                        assert(otherPolys.length == 1); // it must be only 1
                        Poly m2P = otherPolys[0];
                        if (!m2P.getBounds2D().equals(m1P.getBounds2D())) continue; // no match

                        ImmutableNodeInst d = ni.getD();
                        String name = ni.getName();
                        int atIndex = name.indexOf('@');
                        if (atIndex < 0) name += "tmp"; else
                        	name = name.substring(0, atIndex) + "tmp" + name.substring(atIndex);
                        double wid = m2P.getBounds2D().getWidth() + so.getLowXOffset() + so.getHighXOffset();
                        double hei = m2P.getBounds2D().getHeight() + so.getLowYOffset() + so.getHighYOffset();
                        NodeInst newNi = NodeInst.makeInstance(pn, ep, d.anchor, wid, hei, ni.getParent(), ni.getOrient(), name);
                        if (newNi == null) continue;

                        // Searching for vias to delete
                        assert(viasList != null);
                        Poly[] viaPolys = tech.getShapeOfNode(newNi, true, false, thisLayer);
                        boolean found = false;

                        // Can be more than 1 due to MxN cuts
                        viaToDelete.clear();
                        for (int i = 0; i < viaPolys.length; i++)
                        {
                            Poly poly = viaPolys[i];
                            Rectangle2D bb = poly.getBounds2D();
                            bb.setRect(ERectangle.fromLambda(bb));
                            found = false;

                            for (NodeInst viaNi : viasList)
                            {
                                Poly[] thisViaList = tech.getShapeOfNode(viaNi, true, false, thisLayer);
                                assert(thisViaList.length == 1);
                                // hack to get rid of the resolution issue
                                Poly p = thisViaList[0];
                                Rectangle2D b = p.getBounds2D();
                                b.setRect(ERectangle.fromLambda(b));
                                if (thisViaList[0].polySame(poly))
                                {
                                    viaToDelete.add(viaNi);
                                    assert(!found);
                                    found = true;
                                }
                            }
                            if (!found)
                            {
                                break; // fail to find all nodesToCreate
                            }
                        }
                        if (!found) // rolling back new node
                        {
                            newNi.kill();
                        }
                        else
                        {
                            if (SHOWPROGRESS)
                                System.out.println("Adding " + newNi.getName());
                            toDelete.clear();
                            geomList.clear();
                            toDelete.add(ni);
                            toDelete.add(n);
                            toDelete.addAll(viaToDelete);
                            String message = toDelete.size() + " nodes were replaced for more complex primitives in cell '" + cell.getName() + "'";
                            geomList.add(newNi);
                            errorLogger.logMessage(message, geomList, cell, -1, false);
                            // Deleting now replaced pure primitives
                            cell.killNodes(toDelete);
                        }
                    }
                }
            }
        }
    }

    /**
     * Method to find missing cells in the GDS and substitute references to cells in other libraries.
     * @param missingCells
     * @param theLibrary
     * @return Map of missing Cells.
     */
    private Map<Cell,Cell> substituteExternalCells(Set<Cell> missingCells, Library theLibrary)
    {
        Map<Cell,Cell> missingCellMap = new HashMap<Cell,Cell>();

        // make a list of other libraries to scan
        List<Library> otherLibraries = new ArrayList<Library>();
        for(Library lib : Library.getVisibleLibraries())
        {
            if (lib != theLibrary) otherLibraries.add(lib);
        }
        if (otherLibraries.size() == 0) return missingCellMap;

        // first map missing cells to found ones
        for(Cell mCell : missingCells)
        {
            for(Library lib : otherLibraries)
            {
                Cell found = lib.findNodeProto(mCell.getName());
                if (found != null)
                {
                    missingCellMap.put(mCell, found);
                    mCell.kill();
                    break;
                }
            }
        }

        for(CellBuilder cellBuilder : allBuilders.values())
        {
            for(MakeInstance mi : cellBuilder.insts)
            {
                if (mi.proto instanceof Cell)
                {
                    Cell found = missingCellMap.get(mi.proto);
                    if (found != null) mi.proto = found;
                }
            }
            for(MakeInstanceArray mia : cellBuilder.instArrays)
            {
                if (mia.proto instanceof Cell)
                {
                    Cell found = missingCellMap.get(mia.proto);
                    if (found != null) mia.proto = found;
                }
            }
        }
        return missingCellMap;
    }

    private void buildInstances()
    {
        if (localPrefs.skeletonize)
		{
        	// recursively gather geometry on all cell instances
			for (CellBuilder cellBuilder : allBuilders.values())
				gatherSubCellSkeletonData(cellBuilder);
		}

        Set<CellId> builtCells = new HashSet<CellId>();
        for(CellBuilder cellBuilder : allBuilders.values())
            cellBuilder.makeInstances(builtCells);

        if (localPrefs.skeletonize)
		{
        	// delete lower-level cells
        	CellBuilder lastTopCell = null;
			for (CellBuilder cellBuilder : allBuilders.values())
			{
				if (cellBuilder.topLevel)
				{
					lastTopCell = cellBuilder;
					continue;
				}
				cellBuilder.cell.kill();
				cellBuilder.cell = null;
			}
			if (lastTopCell == null)
			{
				String msg = "No topcell found while building GDS Skeleton for '" + filePath + "'";
				errorLogger.logMessage(msg, null, null, -1, true);
				return;
			}
			currentCells.put(theLibrary, lastTopCell.cell);

        	// add pointer back to original GDS file
        	String origFile = filePath;
        	lastTopCell.cell.newDisplayVar(SKELETON_ORIGIN, origFile, ep);
		}
    }

    private void gatherSubCellSkeletonData(CellBuilder cb)
    {
    	// ignore if this cell hasn't got any subcells
    	if (cb.skeletonCellInstances.size() == 0) return;

    	// handle skeleton data gathering of all subcells
    	for(SkeletonCellInstance sci : cb.skeletonCellInstances)
    	{
            CellBuilder subCB = allBuilders.get(sci.proto.getId());
            if (subCB != null) gatherSubCellSkeletonData(subCB);
    	}

    	// now gather dimensions
    	for(SkeletonCellInstance sci : cb.skeletonCellInstances)
    	{
            CellBuilder subCB = allBuilders.get(sci.proto.getId());
            if (subCB != null)
            {
            	cb.doSkeleton(subCB.cell, sci.loc, sci.orient, sci.wid, sci.hei, null);
            }
    	}
    	cb.skeletonCellInstances.clear();
    }

    /**
     * Class to save instance array information.
     */
    private class MakeInstanceArray
    {
    	private NodeProto proto;
    	private int nCols, nRows;
    	private Orientation orient;
    	private Point2D startLoc, rowOffset, colOffset;
    	private GDSPreferences localPrefs;
//        private GDS gds;

    	private MakeInstanceArray(NodeProto proto, int nCols, int nRows, Orientation orient, Point2D startLoc,
    		Point2D rowOffset, Point2D colOffset, GDSPreferences localPrefs)
    	{
    		this.proto = proto;
    		this.nCols = nCols;
    		this.nRows = nRows;
    		this.orient = orient;
    		this.startLoc = startLoc;
    		this.rowOffset = rowOffset;
    		this.colOffset = colOffset;
    		this.localPrefs = localPrefs;
    	}

    	/**
         * Method to instantiate an array of cell instances.
         * @param parent the Cell in which to create the geometry.
         */
        private void instantiate(CellBuilder cb, Map<NodeProto,List<EPoint>> massiveMerge)
        {
            Cell parent = cb.cell;
        	int arraySimplification = localPrefs.arraySimplification;
            NodeInst subNi = null;
            Cell subCell = (Cell)proto;
            int numArcs = subCell.getNumArcs();
            int numNodes = subCell.getNumNodes();
            int numExports = subCell.getNumPorts();
            if (numArcs == 0 && numExports == 0 && numNodes == 1)
            {
                subNi = subCell.getNode(0);
                if (subNi.getProto().getFunction() != PrimitiveNode.Function.NODE)
                    subNi = null;
            }
            if (subNi != null && subNi.getTrace() != null) subNi = null;
            if (subNi != null)
            {
                if (arraySimplification > 0)
                {
                    List<EPoint> points = buildArray();
                    if (arraySimplification == 2)
                    {
                        // add the array's geometry the layer's outline
                        List<EPoint> soFar = massiveMerge.get(subNi.getProto());
                        if (soFar == null)
                        {
                            soFar = new ArrayList<EPoint>();
                            massiveMerge.put(subNi.getProto(), soFar);
                        }
                        if (soFar.size() > 0) soFar.add(null);
                        for(EPoint ep : points) soFar.add(ep);
                    } else
                    {
                        // place a pure-layer node that embodies the array
                        buildComplexNode(cb, points, subNi.getProto(), parent, ep);
                    }
                    return;
                } else
                {
                    // remember that array simplification would have helped
                    arraySimplificationUseful = true;
                }
            }

            if (INSTANTIATE_ARRAYS_VIA_BISECTION)
            {
                double colspace;
                double rowspace;
                int rows;
                int cols;
                if (orient.getAngle() % 1800 == 0)
                {
                    if (rowOffset.getX() == 0 && colOffset.getY() == 0)
                    {
                        colspace = colOffset.getX();
                        rowspace = rowOffset.getY();
                        cols = nCols;
                        rows = nRows;
                    } else
                    {
                        throw new Error("jagged arrays not allowed");
                    }
                } else if (orient.getAngle() % 1800 == 900)
                {
                    if (colOffset.getX() == 0 && rowOffset.getY() == 0)
                    {
                        colspace = rowOffset.getX();
                        rowspace = colOffset.getY();
                        cols = nRows;
                        rows = nCols;
                    } else
                    {
                        throw new Error("jagged arrays not allowed");
                    }
                } else
                {
                    throw new Error("nonrectangular arrays not allowed");
                }
                cellArrayBuilder.buildArray(proto,
                                            parent,
                                            EPoint.fromLambda(startLoc.getX(), startLoc.getY()),
                                            orient,
                                            cols,
                                            rows,
                                            FixpCoord.fromLambda(colspace),
                                            FixpCoord.fromLambda(rowspace),
                                            ep);
                return;
            }

    		// generate an array
    		double ptcX = startLoc.getX();
    		double ptcY = startLoc.getY();
    		for (int ic = 0; ic < nCols; ic++)
    		{
    			double ptX = ptcX;
    			double ptY = ptcY;
    			for (int ir = 0; ir < nRows; ir++)
    			{
    				// create the node
    				if (localPrefs.instantiateArrays ||
    					(ir == 0 && ic == 0) ||
    						(ir == (nRows-1) && ic == (nCols-1)))
    				{
    					Point2D loc = new Point2D.Double(ptX, ptY);
    					double wid = proto.getDefWidth(ep);
    					double hei = proto.getDefHeight(ep);
    					NodeInst.makeInstance(proto, ep, loc, wid, hei, parent, orient, null);
    				}

    				// add the row displacement
    				ptX += rowOffset.getX();   ptY += rowOffset.getY();
    			}

    			// add displacement
    			ptcX += colOffset.getX();   ptcY += colOffset.getY();
    		}
        }

        private List<EPoint> buildArray()
        {
			List<EPoint> points = new ArrayList<EPoint>();
			Rectangle2D bounds = ((Cell)proto).getBounds();
			Rectangle2D boundCopy = new Rectangle2D.Double(bounds.getMinX(), bounds.getMinY(), bounds.getWidth(), bounds.getHeight());
			DBMath.transformRect(boundCopy, orient.pureRotate());
    		double ptcX = startLoc.getX();
    		double ptcY = startLoc.getY();
    		for (int ic = 0; ic < nCols; ic++)
    		{
    			double ptX = ptcX;
    			double ptY = ptcY;
    			for (int ir = 0; ir < nRows; ir++)
    			{
    	    		points.add(EPoint.fromLambda(ptX+boundCopy.getMinX(), ptY+boundCopy.getMinY()));
    	    		points.add(EPoint.fromLambda(ptX+boundCopy.getMaxX(), ptY+boundCopy.getMinY()));
    	    		points.add(EPoint.fromLambda(ptX+boundCopy.getMaxX(), ptY+boundCopy.getMaxY()));
    	    		points.add(EPoint.fromLambda(ptX+boundCopy.getMinX(), ptY+boundCopy.getMaxY()));

    	    		// insert a "break" marker to start a new polygon
    	    		if (ic < nCols-1 || ir < nRows-1) points.add(null);

    				// add the row displacement
    				ptX += rowOffset.getX();   ptY += rowOffset.getY();
    			}

    			// add displacement
    			ptcX += colOffset.getX();   ptcY += colOffset.getY();
    		}
			return points;
        }
    }

    private class MakeInstance
	{
		private NodeProto proto;
		private final Point2D loc;
		private final Orientation orient;
        private double wid, hei;
        private final EPoint[] points; // trace
        private String exportOrTextName; // export
        private boolean isVariableText; // for annotation text
        private final Name nodeName; // text
        private String origNodeName; // original text with invalid name
        private PortCharacteristic pc;
        private ImmutableNodeInst n;

        private MakeInstance(CellBuilder cb, NodeProto proto, Point2D loc, Orientation orient, double wid, double hei, EPoint[] points,
        	String exportOrTextName, Name nodeName, boolean isVar)
		{
			this.proto = proto;
			this.loc = loc;
            this.orient = orient;
            this.wid = DBMath.round(wid);
            this.hei = DBMath.round(hei);
            this.points = points;
            this.exportOrTextName = exportOrTextName;
            this.isVariableText = isVar;
            this.pc = PortCharacteristic.UNKNOWN;
            if (nodeName != null && !nodeName.isValid())
            {
                origNodeName = nodeName.toString();
            	if (origNodeName.equals("[@instanceName]"))
            	{
            		origNodeName = null;
            		nodeName = null;
            	} else if (origNodeName.endsWith(":"))
            	{
            		nodeName = Name.findName(origNodeName.substring(0, origNodeName.length()-1));
            		if (nodeName.isValid()) origNodeName = null;
            	} else
            	{
            		nodeName = null;
            	}
            }
            cb.count.increment();
            if (nodeName != null)
            {
                if (!validGdsNodeName(nodeName))
                {
                    System.out.println("  Warning: Node name '" + nodeName + "' in cell " + cb.cell.describe(false) +
                        " is bad (" + Name.checkName(nodeName.toString()) + ")...ignoring the name");
                } else if (!cb.userNames.contains(nodeName.toString()))
                {
                    cb.userNames.add(nodeName.toString());
                    this.nodeName = nodeName;
                    return;
                }
            }
            Name baseName;
            if (proto instanceof Cell) baseName = ((Cell)proto).getBasename(); else
            {
                PrimitiveNode np = (PrimitiveNode)proto;
                baseName = np.getFunction().getBasename();
            }
            String basenameString = baseName.toString();
            MutableInteger maxSuffix = cb.maxSuffixes.get(basenameString);
            if (maxSuffix == null)
            {
                maxSuffix = new MutableInteger(-1);
                cb.maxSuffixes.put(basenameString, maxSuffix);
            }
            maxSuffix.increment();
            this.nodeName = baseName.findSuffixed(maxSuffix.intValue());
		}

        private boolean validGdsNodeName(Name name)
        {
            return name.isValid() && !name.hasEmptySubnames() && !name.isBus() || !name.isTempname();
        }

        /**
         * Method to instantiate a node/export in a Cell.
         * @param parent the Cell in which to create the geometry.
         * @param exportUnify a map that shows how renamed exports connect.
         * @param saveHere a list of ImmutableNodeInst to save this instance in.
         * @return true if the export had to be renamed.
         */
        private void instantiate(CellBuilder cb, Map<String,String> exportUnify, List<ImmutableNodeInst> saveHere)
        {
            Cell parent = cb.cell;
            assert parent.isLinked();
            // search for spare nodeId
            int nodeId = cb.nodeId++;
            assert nodeName != null;
        	String name = nodeName.toString();
            assert parent.findNode(name) == null;
            assert !NodeInst.checkNameKey(nodeName, parent) && !nodeName.isBus();
            TextDescriptor nameDescriptor = ep.getNodeTextDescriptor();
            EPoint anchor = EPoint.snap(loc);
            EPoint size = EPoint.ORIGIN;
            if (proto instanceof PrimitiveNode) {
                ERectangle full = ((PrimitiveNode) proto).getFullRectangle();
                long gridWidth = DBMath.lambdaToSizeGrid(wid - full.getLambdaWidth());
                long gridHeight = DBMath.lambdaToSizeGrid(hei - full.getLambdaHeight());
                try
                {
                	size = EPoint.fromGrid(gridWidth, gridHeight);
                } catch (IllegalArgumentException e)
                {
                	String errorMsg = "Coordinate (" + gridWidth + "," + gridHeight + ") too large to store in 32-bits";
                    errorLogger.logError(errorMsg, parent, -1);
                    System.out.println("ERROR: " + errorMsg);
                }
            } else {
                assert ((Cell)proto).isLinked();
            }
            int flags = 0;
            int techBits = 0;
            TextDescriptor protoDescriptor = ep.getInstanceTextDescriptor();
            n = ImmutableNodeInst.newInstance(nodeId, proto.getId(), nodeName, nameDescriptor,
                orient, anchor, size, flags, techBits, protoDescriptor);
            if (points != null && GenMath.getAreaOfPoints(points) != wid*hei) {
                n = n.withTrace(points, null);
            }

            // if it is an annotation text
            if (isVariableText)
            {
            	TextDescriptor td = ep.getExportTextDescriptor().withDisplay(true);
            	Variable var = Variable.newInstance(Artwork.ART_MESSAGE, exportOrTextName, td);
            	n = n.withVariable(var); 
            }
            
            cb.nodesToCreate.add(n);
            if (saveHere != null) saveHere.add(n);

            String errorMsg = null;
            if (origNodeName != null)
            {
           		errorMsg = "Cell " + parent.describe(false) + ": Original GDS name of '" + name + "' was '" + origNodeName + "'";
            }

            if (errorMsg != null)
            {
                errorLogger.logMessage(errorMsg, Collections.singleton(n), parent, -1, false);
                System.out.println(errorMsg);
            }

            if (exportOrTextName != null && !isVariableText)
            {
            	if (exportOrTextName.endsWith(":"))
            		exportOrTextName = exportOrTextName.substring(0, exportOrTextName.length()-1);
                exportOrTextName = exportOrTextName.replace(':', '_');
                String trueName = null;
        		if (cb.alreadyExports.contains(exportOrTextName))
        		{
                    String newName = ElectricObject.uniqueObjectName(exportOrTextName, parent, Export.class,
                        cb.alreadyExports, cb.nextExportPlainIndex, true, true);
                    exportUnify.put(newName, exportOrTextName);
                    trueName = exportOrTextName;
                    exportOrTextName = newName;
        		}

                // Create ImmutableExport
                ExportId exportId = parent.getD().cellId.newPortId(exportOrTextName);
                boolean busNamesAllowed = false;
                Name exportNameKey = ImmutableExport.validExportName(exportOrTextName, busNamesAllowed);
                if (exportNameKey == null)
                {
                    String newName = Export.repairExportName(parent, exportOrTextName);
                    if (newName == null) newName = Export.repairExportName(parent, "X");
                    if (newName != null)
                    	exportNameKey = ImmutableExport.validExportName(newName, busNamesAllowed);
                    if (exportNameKey == null)
                    {
                    	System.out.print("Error: Pin '" + exportOrTextName + "' is invalid");
                    	if (newName != null)
                    		System.out.print(" and alternate name, '" + newName + "' cannot be used");
                    	System.out.println(".  Pin ignored.");
                    	return;
                    }
                	System.out.println("Warning: Pin '" + exportOrTextName + "' is invalid and was renamed to '" + newName + "'");
                	exportOrTextName = newName;
                }
                TextDescriptor nameTextDescriptor = ep.getExportTextDescriptor();
                PortProtoId portProtoId = proto.getPort(0).getId();
                boolean alwaysDrawn = false;
                boolean bodyOnly = false;
                assert parent.findExport(exportOrTextName) == null;
                ImmutableExport d = ImmutableExport.newInstance(exportId, exportNameKey, nameTextDescriptor,
                    nodeId, portProtoId, alwaysDrawn, bodyOnly, pc);

                if (trueName != null)
                {
	            	TextDescriptor td = ep.getExportTextDescriptor();
	            	Variable var = Variable.newInstance(ORIGINAL_EXPORT_NAME, trueName, td);
	            	d = d.withVariable(var);
                }
            	
                // Put ImmutableExport in the CellBuiler.
                // This also modifies the cb.alreadyExports
                cb.exportsByName.put(exportOrTextName, d);
            }
        }
	}

    public void loadFile()
		throws Exception
	{
    	// prepare to do readable dump of GDS file
    	printWriter = null;
    	if (localPrefs.dumpReadable)
    	{
    		String dumpFileName = filePath;
    		int lastDot = dumpFileName.lastIndexOf('.');
    		if (lastDot > 0) dumpFileName = dumpFileName.substring(0, lastDot);
    		String fileName = OpenFile.chooseOutputFile(FileType.TEXT, "GDS readable dump file", dumpFileName+".txt");
    		if (fileName != null)
    		{
	    		try
	    		{
	    			printWriter = new PrintWriter(new BufferedWriter(new FileWriter(fileName)));
	    		} catch (IOException e) {}
    		}
    		if (printWriter == null) localPrefs.dumpReadable = false; else
    		{
    			printWriter.println("Readable dump of file: " + filePath);
    			printWriter.println();
    		}
    	}

    	gdsRead.getToken();
		readHeader();
		gdsRead.getToken();
		readLibrary();
		gdsRead.getToken();
		while (isMember(gdsRead.getTokenType(), optionSet))
		{
			if (gdsRead.getTokenType() == GDSReader.GDS_REFLIBS) readRefLibs(); else
				if (gdsRead.getTokenType() == GDSReader.GDS_FONTS) readFonts(); else
					if (gdsRead.getTokenType() == GDSReader.GDS_ATTRTABLE) readAttrTable(); else
						if (gdsRead.getTokenType() == GDSReader.GDS_GENERATIONS) readGenerations();
		}
		while (gdsRead.getTokenType() != GDSReader.GDS_UNITS)
			gdsRead.getToken();
		readUnits();
		gdsRead.getToken();

		while (gdsRead.getTokenType() != GDSReader.GDS_ENDLIB)
		{
			readStructure();
			gdsRead.getToken();
		}

    	if (localPrefs.dumpReadable)
    	{
    		printWriter.println();
    		printWriter.println("- End library");
    		printWriter.close();
    	}
	}

	private void readHeader()
		throws Exception
	{
		if (gdsRead.getTokenType() != GDSReader.GDS_HEADER) gdsRead.handleError("GDS II header statement is missing");

		gdsRead.getToken();
		if (gdsRead.getTokenType() != GDSReader.GDS_SHORT_NUMBER) gdsRead.handleError("GDS II version number is not decipherable");
    	if (localPrefs.dumpReadable) printWriter.println("- File header");
		// version "getShortValue()"
	}

	private void readLibrary()
		throws Exception
	{
		if (gdsRead.getTokenType() != GDSReader.GDS_BGNLIB) gdsRead.handleError("Begin library statement is missing");

		gdsRead.getToken();
		gdsRead.determineTime();		// creation time
		gdsRead.determineTime();		// modification time
		if (gdsRead.getTokenType() == GDSReader.GDS_LIBNAME)
		{
			gdsRead.getToken();
			if (gdsRead.getTokenType() != GDSReader.GDS_IDENT) gdsRead.handleError("Library name is missing");
		}
		if (localPrefs.dumpReadable) printWriter.println("- Library: " + gdsRead.getStringValue());
	}

	private void readRefLibs()
		throws Exception
	{
		gdsRead.getToken();
		gdsRead.getToken();
		if (localPrefs.dumpReadable) printWriter.println("- Reference libraries");
	}

	private void readFonts()
		throws Exception
	{
		gdsRead.getToken();
		gdsRead.getToken();
		if (localPrefs.dumpReadable) printWriter.println("- Fonts");
	}

	private void readAttrTable()
		throws Exception
	{
		gdsRead.getToken();
		if (gdsRead.getTokenType() == GDSReader.GDS_IDENT)
		{
			gdsRead.getToken();
		}
		if (localPrefs.dumpReadable) printWriter.println("- Attribute table");
	}

	private void readUnits()
		throws Exception
	{
		if (gdsRead.getTokenType() != GDSReader.GDS_UNITS) gdsRead.handleError("Units statement is missing");

		gdsRead.getToken();
		if (gdsRead.getTokenType() != GDSReader.GDS_REALNUM) gdsRead.handleError("Units statement has invalid number format");
		double precision = gdsRead.getDoubleValue();

		// get the meter unit
		gdsRead.getToken();
		double meterUnit = gdsRead.getDoubleValue(); // * (precision * 1000.0);

		// round the meter unit
		double shift = 1;
		double roundedScale = meterUnit;
		while (roundedScale < 1)
		{
			roundedScale *= 10;
			shift *= 10;
		}
		roundedScale = DBMath.round(roundedScale) / shift;

		// compute the scale
		double microScale = TextUtils.convertFromDistance(1, curTech, TextUtils.UnitScale.MICRO);
		theScale = roundedScale * 1000000.0 * microScale * localPrefs.inputScale;

		if (localPrefs.dumpReadable) printWriter.println("- Units: precision=" + TextUtils.formatDouble(precision, 0) +
			" meter=" + TextUtils.formatDouble(meterUnit, 0) + " scale=" + TextUtils.formatDouble(theScale, 0));
	}

	private double scaleValue(double value)
	{
		double result = value * theScale;
		return result;
	}

	private void showResultsOfCell()
	{
		System.out.print("**** Cell "+theCell.cell.describe(false)+" has");
		if (countBox > 0) System.out.print(" "+countBox+" boxes");
		if (countText > 0) System.out.print(" "+countText+" texts");
		if (countNode > 0) System.out.print(" "+countNode+" nodes");
		if (countPath > 0) System.out.print(" "+countPath+" paths");
		if (countShape > 0) System.out.print(" "+countShape+" shapes");
		if (countSRef > 0) System.out.print(" "+countSRef+" instances");
		if (countARef > 0)
			System.out.print(" "+countARef+" arrays with "+countATotal+" elements");
		System.out.println();
	}

	private void readStructure()
		throws Exception
	{
		beginStructure();
		gdsRead.getToken();
		if (localPrefs.mergeBoxes)
		{
			// initialize merge if merging this cell
    		merge = new PolyMerge();
		}

		// read the cell
		countBox = countText = countNode = countPath = countShape = countSRef = countARef = countATotal = 0;
		while (gdsRead.getTokenType() != GDSReader.GDS_ENDSTR)
		{
            getElement();
            gdsRead.getToken();
		}
		if (localPrefs.dumpReadable) printWriter.println("- Done reading cell");
		if (TALLYCONTENTS) showResultsOfCell();
		if (localPrefs.mergeBoxes)
		{
			// extract merge information for this cell
    		for(Layer layer : merge.getKeySet())
    		{
    			Layer primLayer = layer;
				PrimitiveNode pnp = primLayer.getPureLayerNode();
    			List<PolyBase> polys = merge.getMergedPoints(layer, false);
    			for(PolyBase poly : polys)
    			{
    				Rectangle2D box = poly.getBox();
    				if (box == null)
    				{
        				box = poly.getBounds2D();
    					Point2D ctr = EPoint.fromLambda(box.getCenterX(), box.getCenterY());

    					// store the trace information
    					Point2D [] pPoints = poly.getPoints();
    					EPoint [] points = new EPoint[pPoints.length];
    					for(int i=0; i<pPoints.length; i++)
    					{
    						points[i] = EPoint.fromLambda(pPoints[i].getX(), pPoints[i].getY());
    					}

    					// store the trace information
                        theCell.makeInstance(pnp, ctr, Orientation.IDENT, box.getWidth(), box.getHeight(), points, null);
    				} else
    				{
    					Point2D ctr = EPoint.fromLambda(box.getCenterX(), box.getCenterY());
                        theCell.makeInstance(pnp, ctr, Orientation.IDENT, box.getWidth(), box.getHeight(), null, null);
    				}
    			}
    		}
		}
	}

	private void beginStructure()
		throws Exception
	{
		if (gdsRead.getTokenType() != GDSReader.GDS_BGNSTR) gdsRead.handleError("Begin structure statement is missing");

		gdsRead.getToken();
		gdsRead.determineTime();	// creation time
		gdsRead.determineTime();	// modification time
		if (gdsRead.getTokenType() != GDSReader.GDS_STRNAME) gdsRead.handleError("Strname statement is missing");

		gdsRead.getToken();
		if (gdsRead.getTokenType() != GDSReader.GDS_IDENT) gdsRead.handleError("Structure name is missing");

		// look for this nodeproto
		String name = gdsRead.getStringValue();
		if (localPrefs.dumpReadable)
		{
			printWriter.println();
			printWriter.println("- Cell: " + name);
		}
		if (localPrefs.skeletonize) name += "{lay.sk}"; else
			name += "{lay}";
		Cell cell = findCell(name);
		if (cell == null)
		{
			// create the prototype
			cell = Cell.newInstance(theLibrary, name);
			if (curTech != null)
				cell.setTechnology(curTech);
			if (cell == null) gdsRead.handleError("Failed to create structure");
			if (!currentCells.containsKey(theLibrary))
				currentCells.put(theLibrary, cell);
		} else
		{
			missingCells.remove(cell);
		}
        theCell = new CellBuilder(cell, curTech, localPrefs);
	}

	private Cell findCell(String name)
	{
		return theLibrary.findNodeProto(name);
	}

	/**
	 * Method to create a pure-layer node with a complex outline.
	 * @param points the outline description.
	 * @param pureType the type of the pure-layer node.
	 * @param parent the Cell in which to create the node.
     * @param ep EditingPreferences
	 */
	private void buildComplexNode(CellBuilder cb, List<EPoint> points, NodeProto pureType, Cell parent, EditingPreferences ep)
	{
		EPoint [] pointArray = new EPoint[points.size()];
		double lX=0, hX=0, lY=0, hY=0;
		for(int i=0; i<points.size(); i++)
		{
			pointArray[i] = points.get(i);
			if (pointArray[i] == null) continue;
			if (i == 0)
			{
				lX = hX = pointArray[i].getX();
				lY = hY = pointArray[i].getY();
			} else
			{
				if (pointArray[i].getX() < lX) lX = pointArray[i].getX();
				if (pointArray[i].getX() > hX) hX = pointArray[i].getX();
				if (pointArray[i].getY() < lY) lY = pointArray[i].getY();
				if (pointArray[i].getY() > hY) hY = pointArray[i].getY();
			}
		}
		Point2D loc = new Point2D.Double((lX+hX)/2, (lY+hY)/2);
        NodeInst ni = NodeInst.makeInstance(pureType, ep, loc, hX-lX, hY-lY,
        	parent, Orientation.IDENT, null);
        if (ni != null && GenMath.getAreaOfPoints(pointArray) != (hX-lX)*(hY-lY))
        	ni.setTrace(pointArray);
	}

	private void getElement()
		throws Exception
	{
		while (isMember(gdsRead.getTokenType(), shapeSet))
		{
			if (gdsRead.getTokenType() == GDSReader.GDS_AREF)
			{
				determineARef();
			} else if (gdsRead.getTokenType() == GDSReader.GDS_SREF)
			{
				determineSRef();
			} else if (gdsRead.getTokenType() == GDSReader.GDS_BOUNDARY)
			{
				determineShape();
			} else if (gdsRead.getTokenType() == GDSReader.GDS_PATH)
			{
				determinePath();
			} else if (gdsRead.getTokenType() == GDSReader.GDS_NODE)
			{
				determineNode();
			} else if (gdsRead.getTokenType() == GDSReader.GDS_TEXTSYM)
			{
				determineText();
			} else if (gdsRead.getTokenType() == GDSReader.GDS_BOX)
			{
				determineBox();
			}
		}

		while (gdsRead.getTokenType() == GDSReader.GDS_PROPATTR)
			determineProperty();
		if (gdsRead.getTokenType() != GDSReader.GDS_ENDEL)
		{
			showResultsOfCell();
			gdsRead.handleError("Element end statement is missing");
		}
	}

	private void determineARef()
		throws Exception
	{
		gdsRead.getToken();
		readUnsupported(unsupportedSet);
		if (gdsRead.getTokenType() != GDSReader.GDS_SNAME) gdsRead.handleError("Array reference name is missing");
		gdsRead.getToken();

		// get this nodeproto
		getPrototype(gdsRead.getStringValue());
		gdsRead.getToken();
		int angle = 0;
		boolean trans = false;
		if (gdsRead.getTokenType() == GDSReader.GDS_STRANS)
		{
			ReadOrientation ro = new ReadOrientation();
			ro.doIt();
			angle = ro.angle;
			trans = ro.trans;
		}
		int nCols = 0, nRows = 0;
		if (gdsRead.getTokenType() == GDSReader.GDS_COLROW)
		{
			gdsRead.getToken();
			nCols = gdsRead.getShortValue();
			gdsRead.getToken();
			nRows = gdsRead.getShortValue();
			gdsRead.getToken();
		}
		if (gdsRead.getTokenType() != GDSReader.GDS_XY) gdsRead.handleError("Array reference has no parameters");
		gdsRead.getToken();
		determinePoints(3, 3);

		// see if the instance is a single object
		if (TALLYCONTENTS)
		{
			countARef++;
			countATotal += nCols*nRows;
			return;
		}
		boolean mY = false;
		boolean mX = false;
		if (trans)
		{
			mY = true;
			angle = (angle + 900) % 3600;
		}

		Point2D colInterval = new Point2D.Double(0, 0);
		if (nCols != 1)
		{
			colInterval.setLocation((theVertices[1].getX() - theVertices[0].getX()) / nCols,
				(theVertices[1].getY() - theVertices[0].getY()) / nCols);
		}
		Point2D rowInterval = new Point2D.Double(0, 0);
		if (nRows != 1)
		{
			rowInterval.setLocation((theVertices[2].getX() - theVertices[0].getX()) / nRows,
				(theVertices[2].getY() - theVertices[0].getY()) / nRows);
		}

		theCell.makeInstanceArray(theNodeProto, nCols, nRows, Orientation.fromJava(angle, mX, mY),
			theVertices[0], rowInterval, colInterval);
		if (localPrefs.dumpReadable) printWriter.println("-- Array Reference: " + nCols + "x" + nRows + " of " + theNodeProto.describe(false));
	}

	private class ReadOrientation
	{
		private int angle;
		private boolean trans;
		private double scale;

		private void doIt()
			throws Exception
		{
			double anglevalue = 0.0;
			scale = 1.0;
			boolean mirror_x = false;
			gdsRead.getToken();
			if (gdsRead.getTokenType() != GDSReader.GDS_FLAGSYM) gdsRead.handleError("Structure reference is missing its flags field");
			if ((gdsRead.getFlagsValue()&0100000) != 0) mirror_x = true;
			gdsRead.getToken();
			if (gdsRead.getTokenType() == GDSReader.GDS_MAG)
			{
				gdsRead.getToken();
				scale = gdsRead.getDoubleValue();
				gdsRead.getToken();
			}
			if (gdsRead.getTokenType() == GDSReader.GDS_ANGLE)
			{
				gdsRead.getToken();
				anglevalue = gdsRead.getDoubleValue() * 10;
				gdsRead.getToken();
			}
			angle = ((int)anglevalue) % 3600;
			trans = mirror_x;
			if (trans)
				angle = (2700 - angle) % 3600;

			// should not happen...*/
			if (angle < 0) angle = angle + 3600;
		}
	}

	private void determineSRef()
		throws Exception
	{
		gdsRead.getToken();
		readUnsupported(unsupportedSet);
		if (gdsRead.getTokenType() != GDSReader.GDS_SNAME) gdsRead.handleError("Structure reference name is missing");

		gdsRead.getToken();
		getPrototype(gdsRead.getStringValue());
		gdsRead.getToken();
		int angle = 0;
		boolean trans = false;
		if (gdsRead.getTokenType() == GDSReader.GDS_STRANS)
		{
			ReadOrientation ro = new ReadOrientation();
			ro.doIt();
			angle = ro.angle;
			trans = ro.trans;
		}
		if (gdsRead.getTokenType() != GDSReader.GDS_XY) gdsRead.handleError("Structure reference has no translation value");
		gdsRead.getToken();
		determinePoints(1, 1);

		if (TALLYCONTENTS)
		{
			countSRef++;
			return;
		}

		Point2D loc = new Point2D.Double(theVertices[0].getX(), theVertices[0].getY());
		boolean mY = false;
		if (trans)
		{
			mY = true;
			angle = (angle + 900) % 3600;
		}
		theCell.makeInstance(theNodeProto, loc, Orientation.fromJava(angle, false, mY), 0, 0, null, null);
		if (localPrefs.dumpReadable) printWriter.println("-- Instance of " + theNodeProto.noLibDescribe() +
        	" at (" + TextUtils.formatDistance(loc.getX()) + "," + TextUtils.formatDistance(loc.getY()) + ")");
	}

	private void determineShape()
		throws Exception
	{
		gdsRead.getToken();
		readUnsupported(unsupportedSet);
		determineLayer(true);
		gdsRead.getToken();
		if (gdsRead.getTokenType() != GDSReader.GDS_XY) gdsRead.handleError("Boundary has no points");

		gdsRead.getToken();
		determinePoints(3, MAXPOINTS);
		if (TALLYCONTENTS)
		{
			countShape++;
			return;
		}
		determineBoundary();
		if (localPrefs.dumpReadable)
        {
			double lx = theVertices[0].getX();
			double hx = theVertices[0].getX();
			double ly = theVertices[0].getY();
			double hy = theVertices[0].getY();
			for (int i=1; i<numVertices;i++)
			{
				if (lx > theVertices[i].getX()) lx = theVertices[i].getX();
				if (hx < theVertices[i].getX()) hx = theVertices[i].getX();
				if (ly > theVertices[i].getY()) ly = theVertices[i].getY();
				if (hy < theVertices[i].getY()) hy = theVertices[i].getY();
			}
			double cX = (lx + hx) / 2, cY = (ly + hy) / 2;
			printWriter.print("-- Shape on " + (layerIsPin ? "pin " : "") + "layer " + curLayerNum + "/" + curLayerType +
        		" (" + layerNodeProto.describe(false) + ") at (" + TextUtils.formatDistance(cX) + "," + TextUtils.formatDistance(cY) +
        		") has " + numVertices + " points:");
	        for(int i=0; i<numVertices; i++) printWriter.print(" (" + TextUtils.formatDistance(theVertices[i].getX()) + "," +
	        	TextUtils.formatDistance(theVertices[i].getX()) + ")");
	        printWriter.println();        	
        }
	}

	private void determineBoundary()
	{
		boolean is90 = true;
		boolean is45 = true;
		for (int i=0; i<numVertices-1 && i<MAXPOINTS-1; i++)
		{
			double dx = theVertices[i+1].getX() - theVertices[i].getX();
			double dy = theVertices[i+1].getY() - theVertices[i].getY();
			if (dx != 0 && dy != 0)
			{
				is90 = false;
				if (Math.abs(dx) != Math.abs(dy)) is45 = false;
			}
		}

		ShapeType perimeter = SHAPELINE;
		if (theVertices[0].getX() == theVertices[numVertices-1].getX() &&
			theVertices[0].getY() == theVertices[numVertices-1].getY())
				perimeter = SHAPECLOSED;
		ShapeType oclass = SHAPEOBLIQUE;
		if (perimeter == SHAPECLOSED && (is90 || is45))
			oclass = SHAPEPOLY;
		if (numVertices == 5 && is90 && perimeter == SHAPECLOSED)
			oclass = SHAPERECTANGLE;

		if (oclass == SHAPERECTANGLE)
		{
			readBox();

			// create the rectangle
			Point2D ctr = new Point2D.Double((theVertices[0].getX()+theVertices[1].getX())/2,
				(theVertices[0].getY()+theVertices[1].getY())/2);
			double sX = Math.abs(theVertices[1].getX() - theVertices[0].getX());
			double sY = Math.abs(theVertices[1].getY() - theVertices[0].getY());
			if (localPrefs.mergeBoxes)
			{
				if (layerNodeProto != null)
				{
					PrimitiveNode plnp = layerNodeProto;
					NodeLayer [] layers = plnp.getNodeLayers();
					merge.addPolygon(layers[0].getLayer(), new Poly(ctr.getX(), ctr.getY(), sX, sY));
				}
			} else
			{
                theCell.makeInstance(layerNodeProto, ctr, Orientation.IDENT, sX, sY, null, currentUnknownLayerMessage);
			}
			return;
		}

		if (oclass == SHAPEOBLIQUE || oclass == SHAPEPOLY)
		{
			if (localPrefs.mergeBoxes)
			{
				NodeLayer [] layers = layerNodeProto.getNodeLayers();
				if (layerNodeProto != null) {
                    Poly.Point[] points = new Poly.Point[theVertices.length];
                    for (int i = 0; i < theVertices.length; i++) {
                        points[i] = Poly.from(theVertices[i]);
                    }
					merge.addPolygon(layers[0].getLayer(), new Poly(points));
                }
			} else
			{
				// determine the bounds of the polygon
				double lx = theVertices[0].getX();
				double hx = theVertices[0].getX();
				double ly = theVertices[0].getY();
				double hy = theVertices[0].getY();
				for (int i=1; i<numVertices;i++)
				{
					if (lx > theVertices[i].getX()) lx = theVertices[i].getX();
					if (hx < theVertices[i].getX()) hx = theVertices[i].getX();
					if (ly > theVertices[i].getY()) ly = theVertices[i].getY();
					if (hy < theVertices[i].getY()) hy = theVertices[i].getY();
				}

				// store the trace information
				EPoint [] points = new EPoint[numVertices];
				for(int i=0; i<numVertices; i++)
				{
					points[i] = EPoint.fromLambda(theVertices[i].getX(), theVertices[i].getY());
				}

				// now create the node
                theCell.makeInstance(layerNodeProto, EPoint.fromLambda((lx+hx)/2, (ly+hy)/2),
                	Orientation.IDENT, hx-lx, hy-ly, points, currentUnknownLayerMessage);
			}
			return;
		}
	}

	private void readBox()
	{
		double pxm = theVertices[4].getX();
		double pxs = theVertices[4].getX();
		double pym = theVertices[4].getY();
		double pys = theVertices[4].getY();
		for (int i = 0; i<4; i++)
		{
			if (theVertices[i].getX() > pxm) pxm = theVertices[i].getX();
			if (theVertices[i].getX() < pxs) pxs = theVertices[i].getX();
			if (theVertices[i].getY() > pym) pym = theVertices[i].getY();
			if (theVertices[i].getY() < pys) pys = theVertices[i].getY();
		}
		theVertices[0].setLocation(pxs, pys);
		theVertices[1].setLocation(pxm, pym);
	}

	private void determinePath()
		throws Exception
	{
		int endcode = 0;
		gdsRead.getToken();
		readUnsupported(unsupportedSet);
		determineLayer(false);
		gdsRead.getToken();
		if (gdsRead.getTokenType() == GDSReader.GDS_PATHTYPE)
		{
			gdsRead.getToken();
			endcode = gdsRead.getShortValue();
			gdsRead.getToken();
		}
		double width = 0;
		if (gdsRead.getTokenType() == GDSReader.GDS_WIDTH)
		{
			gdsRead.getToken();
			width = scaleValue(gdsRead.getIntValue());
			gdsRead.getToken();
		}
		double bgnextend = (endcode == 0 || endcode == 4 ? 0 : width/2);
		double endextend = bgnextend;
		if (gdsRead.getTokenType() == GDSReader.GDS_BGNEXTN)
		{
			gdsRead.getToken();
			if (endcode == 4)
				bgnextend = scaleValue(gdsRead.getIntValue());
			gdsRead.getToken();
		}
		if (gdsRead.getTokenType() == GDSReader.GDS_ENDEXTN)
		{
			gdsRead.getToken();
			if (endcode == 4)
				endextend = scaleValue(gdsRead.getIntValue());
			gdsRead.getToken();
		}
		if (gdsRead.getTokenType() == GDSReader.GDS_XY)
		{
			gdsRead.getToken();
			determinePoints(2, MAXPOINTS);

			if (TALLYCONTENTS)
			{
				countPath++;
				return;
			}

			// construct the path
			for (int i=0; i < numVertices-1; i++)
			{
				Point2D fromPt = theVertices[i];
				Point2D toPt = theVertices[i+1];

				// determine whether either end needs to be shrunk
				double fextend = width / 2;
				double textend = fextend;
				int thisAngle = GenMath.figureAngle(fromPt, toPt);
				if (i > 0)
				{
					Point2D prevPoint = theVertices[i-1];
					int lastAngle = GenMath.figureAngle(prevPoint, fromPt);
					if (Math.abs(thisAngle-lastAngle) % 900 != 0)
					{
						int ang = Math.abs(thisAngle-lastAngle) / 10;
						if (ang > 180) ang = 360 - ang;
						if (ang > 90) ang = 180 - ang;
						fextend = Poly.getExtendFactor(width, ang);
					}
				} else
				{
					fextend = bgnextend;
				}
				if (i+1 < numVertices-1)
				{
					Point2D nextPoint = theVertices[i+2];
					int nextAngle = GenMath.figureAngle(toPt, nextPoint);
					if (Math.abs(thisAngle-nextAngle) % 900 != 0)
					{
						int ang = Math.abs(thisAngle-nextAngle) / 10;
						if (ang > 180) ang = 360 - ang;
						if (ang > 90) ang = 180 - ang;
						textend = Poly.getExtendFactor(width, ang);
					}
				} else
				{
					textend = endextend;
				}

				// handle arbitrary angle path segment
				double length = fromPt.distance(toPt);
				Poly poly = Poly.makeEndPointPoly(length, width, GenMath.figureAngle(toPt, fromPt),
					fromPt, fextend, toPt, textend, Poly.Type.FILLED);

				if (localPrefs.mergeBoxes)
				{
					if (layerNodeProto != null)
					{
						NodeLayer [] layers = layerNodeProto.getNodeLayers();
						merge.addPolygon(layers[0].getLayer(), poly);
					}
				} else
				{
					// make the node for this segment
					Rectangle2D polyBox = poly.getBox();
					if (polyBox != null)
					{
                        theCell.makeInstance(layerNodeProto, EPoint.fromLambda(polyBox.getCenterX(),
                        	polyBox.getCenterY()), Orientation.IDENT, polyBox.getWidth(), polyBox.getHeight(), null, currentUnknownLayerMessage);
					} else
					{
						polyBox = poly.getBounds2D();
						double cx = polyBox.getCenterX();
						double cy = polyBox.getCenterY();

						// store the trace information
						Point2D [] polyPoints = poly.getPoints();
						EPoint [] points = new EPoint[polyPoints.length];
						for(int j=0; j<polyPoints.length; j++)
						{
							points[j] = EPoint.fromLambda(polyPoints[j].getX(), polyPoints[j].getY());
						}

						// store the trace information
                        theCell.makeInstance(layerNodeProto, EPoint.fromLambda(cx, cy), Orientation.IDENT,
                        	polyBox.getWidth(), polyBox.getHeight(), points, currentUnknownLayerMessage);
					}
				}
			}
		} else
		{
			gdsRead.handleError("Path element has no points");
		}

        if (localPrefs.dumpReadable)
        {
        	printWriter.print("-- Path on " + (layerIsPin ? "pin " : "") + "layer " + curLayerNum + "/" + curLayerType +
        		" (" + layerNodeProto.describe(false) + ") has " + numVertices + " points:");
	        for(int i=0; i<numVertices; i++) printWriter.print(" (" + TextUtils.formatDistance(theVertices[i].getX()) + "," +
	        	TextUtils.formatDistance(theVertices[i].getX()) + ")");
	        printWriter.println();
        }
	}

	private void determineNode()
		throws Exception
	{
		gdsRead.getToken();
		readUnsupported(unsupportedSet);
		if (gdsRead.getTokenType() != GDSReader.GDS_LAYER) gdsRead.handleError("Boundary has no points");
		gdsRead.getToken();
		int layerNum = gdsRead.getShortValue();
		if (gdsRead.getTokenType() == GDSReader.GDS_SHORT_NUMBER)
		{
			gdsRead.getToken();
		}

		// also get node type
		int layerType = gdsRead.getShortValue();
		if (gdsRead.getTokenType() == GDSReader.GDS_NODETYPE)
		{
			gdsRead.getToken();
			gdsRead.getToken();
		}
		setLayer(layerNum, layerType, false);

		// make a dot
		if (gdsRead.getTokenType() != GDSReader.GDS_XY) gdsRead.handleError("Boundary has no points");

		gdsRead.getToken();
		determinePoints(1, 1);

		if (TALLYCONTENTS)
		{
			countNode++;
			return;
		}

		// create the node
		if (!localPrefs.mergeBoxes)
		{
            theCell.makeInstance(layerNodeProto, new Point2D.Double(theVertices[0].getX(), theVertices[0].getY()),
            	Orientation.IDENT, 0, 0, null, currentUnknownLayerMessage);
		}
		if (localPrefs.dumpReadable) printWriter.println("-- Node on " + (layerIsPin ? "pin " : "") + "layer " + curLayerNum + "/" + curLayerType +
        	" (" + layerNodeProto.describe(false) + ") at (" + TextUtils.formatDistance(theVertices[0].getX()) + "," + TextUtils.formatDistance(theVertices[0].getY()) + ")");
	}

	private void determineText()
		throws Exception
	{
		gdsRead.getToken();
		readUnsupported(unsupportedSet);
		determineLayer(true);
		gdsRead.getToken();
		int vert_just = -1;
		int horiz_just = -1;
		if (gdsRead.getTokenType() == GDSReader.GDS_PRESENTATION)
		{
			Point just = determineJustification();
			vert_just = just.x;
			horiz_just = just.y;
		}
		if (gdsRead.getTokenType() == GDSReader.GDS_PATHTYPE)
		{
			gdsRead.getToken();
			// code = getShortValue();
			gdsRead.getToken();
		}
		if (gdsRead.getTokenType() == GDSReader.GDS_WIDTH)
		{
			gdsRead.getToken();
			// width = getIntValue() * theScale;
			gdsRead.getToken();
		}
		int angle = 0;
		boolean trans = false;
		double scale = 1.0;
		String textString = "";
		for(;;)
		{
			if (gdsRead.getTokenType() == GDSReader.GDS_STRANS)
			{
				ReadOrientation ro = new ReadOrientation();
				ro.doIt();
				angle = ro.angle;
				trans = ro.trans;
				scale = ro.scale;
				continue;
			}
			if (gdsRead.getTokenType() == GDSReader.GDS_XY)
			{
				gdsRead.getToken();
				determinePoints(1, 1);
				continue;
			}
			if (gdsRead.getTokenType() == GDSReader.GDS_ANGLE)
			{
				gdsRead.getToken();
				angle = (int)(gdsRead.getDoubleValue() * 10.0);
				gdsRead.getToken();
				continue;
			}
			if (gdsRead.getTokenType() == GDSReader.GDS_STRING)
			{
				if (gdsRead.getRemainingDataCount() != 0)
				{
					gdsRead.getToken();
					textString = gdsRead.getStringValue();
				}
				gdsRead.getToken();
				break;
			}
			if (gdsRead.getTokenType() == GDSReader.GDS_MAG)
			{
				gdsRead.getToken();
				gdsRead.getToken();
				continue;
			}
			gdsRead.handleError("Text element has no reference point");
			break;
		}
		if (TALLYCONTENTS)
		{
			countText++;
			return;
		}
		readText(textString, vert_just, horiz_just, angle, trans, scale);
		if (localPrefs.dumpReadable) printWriter.println("-- " + (layerIsPin ? "Pin" : "Text") + " '" + textString.replace('\n', '/') +
        	"' on layer " + curLayerNum + " at (" + TextUtils.formatDistance(theVertices[0].getX()) + "," + TextUtils.formatDistance(theVertices[0].getY()) + ")");
	}

	private void readText(String charstring, int vjust, int hjust, int angle, boolean trans, double scale)
	{
		// handle pins specially
		if (layerIsPin)
		{
            theCell.makeExport(pinNodeProto, new Point2D.Double(theVertices[0].getX(), theVertices[0].getY()),
            	Orientation.IDENT, charstring, currentUnknownLayerMessage);
			return;
		}

		// stop if not handling text in GDS
		if (!localPrefs.includeText) return;
		double x = theVertices[0].getX() + MINFONTWIDTH * charstring.length();
		double y = theVertices[0].getY() + MINFONTHEIGHT;
		theVertices[1].setLocation(x, y);

		// set the text size and orientation
		MutableTextDescriptor td = new MutableTextDescriptor(ep.getNodeTextDescriptor());
		double size = scale;
		if (size <= 0) size = 2;
		if (size > TextDescriptor.Size.TXTMAXQGRID) size = TextDescriptor.Size.TXTMAXQGRID;
		if (size < TextDescriptor.Size.TXTMINQGRID) size = TextDescriptor.Size.TXTMINQGRID;
		td.setRelSize(size);

		// determine the presentation
		td.setPos(TextDescriptor.Position.CENT);
		switch (vjust)
		{
			case 1:		// top
				switch (hjust)
				{
					case 1:  td.setPos(TextDescriptor.Position.UPRIGHT); break;
					case 2:  td.setPos(TextDescriptor.Position.UPLEFT);  break;
					default: td.setPos(TextDescriptor.Position.UP);      break;
				}
				break;

			case 2:		// bottom
				switch (hjust)
				{
					case 1:  td.setPos(TextDescriptor.Position.DOWNRIGHT); break;
					case 2:  td.setPos(TextDescriptor.Position.DOWNLEFT);  break;
					default: td.setPos(TextDescriptor.Position.DOWN);      break;
				}
				break;

			default:	// centered
				switch (hjust)
				{
					case 1:  td.setPos(TextDescriptor.Position.RIGHT); break;
					case 2:  td.setPos(TextDescriptor.Position.LEFT);  break;
					default: td.setPos(TextDescriptor.Position.CENT);  break;
				}
		}
        theCell.makeText(layerNodeProto, new Point2D.Double(theVertices[0].getX(), theVertices[0].getY()),
        	charstring, TextDescriptor.newTextDescriptor(td), currentUnknownLayerMessage);
	}

	/**
	 * untested feature, I don't have a box type
	 */
	private void determineBox()
		throws Exception
	{
		gdsRead.getToken();
		readUnsupported(unsupportedSet);
		determineLayer(false);
		if (gdsRead.getTokenType() != GDSReader.GDS_XY) gdsRead.handleError("Boundary has no points");

		gdsRead.getToken();
		determinePoints(2, MAXPOINTS);
		if (TALLYCONTENTS)
		{
			countBox++;
			return;
		}

		// create the box
		if (localPrefs.mergeBoxes)
		{
			if (layerNodeProto != null)
			{
			}
		} else
		{
            theCell.makeInstance(layerNodeProto, new Point2D.Double(theVertices[0].getX(), theVertices[0].getY()),
            	Orientation.IDENT, 0, 0, null, currentUnknownLayerMessage);
		}

        if (localPrefs.dumpReadable)
        {
        	printWriter.print("-- Box on " + (layerIsPin ? "pin " : "") + "layer " + curLayerNum + "/" + curLayerType +
        		" (" + layerNodeProto.describe(false) + ") has " + numVertices + " points");
	        for(int i=0; i<numVertices; i++) printWriter.print(" (" + TextUtils.formatDistance(theVertices[i].getX()) + "," +
	        	TextUtils.formatDistance(theVertices[i].getX()) + ")");
	        printWriter.println();
        }
	}

	private static class UnknownLayerMessage
	{
		String message; // message in error logger
		String nodeName; // in most cases, it should be original GDS number and type not found

		UnknownLayerMessage(String message, String nodeName)
		{
			this.message = message;
			this.nodeName = nodeName;
		}
	}

	private void setLayer(int layerNum, int layerType, boolean textCase)
	{
		curLayerNum = layerNum;
		curLayerType = layerType;
		layerIsPin = false;
		currentUnknownLayerMessage = null;
		Integer layerInt = new Integer(layerNum + (layerType<<16));
		List<Layer> list = layerNames.get(layerInt);
		Layer layer = null;
		
        int unknownLayerHandling = localPrefs.unknownLayerHandling; // original given value;
		// checking if layer is not visible when filtering option is on
		// In visibility, all types of layerNum should be invisible to get this working!
		String condition = "unknown";
		if (localPrefs.onlyVisibleLayers && list != null && !localPrefs.visibility[list.get(0).getIndex()])
		{
			unknownLayerHandling = IOTool.GDSUNKNOWNLAYERIGNORE; // force ignore
			condition = "invisible";
			list = null;
		}

		boolean chosenText = localPrefs.includeText && localPrefs.defaultTextLayer != 0 && 
				localPrefs.defaultTextLayer == layerNum && textCase;
		if (list == null)
		{
			// Checking if text is being imported
			if (chosenText)
			{
				// assuming text
				layer = Generic.tech().invisiblePinNode.getLayerIterator().next(); // invisible layer
			}
			else
			{
				layer = Generic.tech().drcLay;
				if (unknownLayerHandling == IOTool.GDSUNKNOWNLAYERUSERANDOM)
				{
					// assign an unused layer here
					for(Iterator<Layer> it = curTech.getLayers(); it.hasNext(); )
					{
						Layer l = it.next();
						if (layerNames.values().contains(l)) continue;
						layer = l;
						break;
					}
					if (layer == null)
					{
						// no unused layers: start picking at random
						if (randomLayerSelection >= curTech.getNumLayers()) randomLayerSelection = 0;
						layer = curTech.getLayer(randomLayerSelection);
						randomLayerSelection++;
					}
				}
			}
			list = new ArrayList<Layer>();
			list.add(layer);
			layerNames.put(layerInt, list);
						
			if (!chosenText && !localPrefs.skeletonize)
			{
				String message = "GDS layer " + layerNum + ", type " + layerType + " " + condition + ", ";
				switch (unknownLayerHandling)
				{
					case IOTool.GDSUNKNOWNLAYERIGNORE:    message += "ignoring it";                    break;
					case IOTool.GDSUNKNOWNLAYERUSEDRC:    message += "using Generic:DRC layer";        break;
					case IOTool.GDSUNKNOWNLAYERUSERANDOM: message += "using layer " + layer.getName(); break;
				}
				currentUnknownLayerMessage = layerErrorMessages.get(layerInt);
				if (currentUnknownLayerMessage == null)
				{
					currentUnknownLayerMessage = new UnknownLayerMessage(message, "Orig._layer_" + layerNum + "/" + layerType);
					layerErrorMessages.put(layerInt, currentUnknownLayerMessage);
				}
			}
		} else
		{
			layer = list.get(0);
		}
		if (layer != null)
		{
			if (chosenText)
			{
				pinNodeProto = Generic.tech().universalPinNode;
				layerNodeProto = pinNodeProto;
				return;
			}
			
			currentUnknownLayerMessage = layerErrorMessages.get(layerInt);
			if (layer == Generic.tech().drcLay && unknownLayerHandling == IOTool.GDSUNKNOWNLAYERIGNORE)
			{
				layerNodeProto = null;
				pinNodeProto = null;
				if (layerWarningMessages.get(layerInt) == null)
					layerWarningMessages.put(layerInt, currentUnknownLayerMessage);
				return;
			}
			layerNodeProto = layer.getPureLayerNode();
			pinNodeProto = Generic.tech().universalPinNode;
			if (pinLayers.contains(layerInt))
			{
				layerIsPin = true;
				if (layerNodeProto != null && layerNodeProto.getNumPorts() > 0)
				{
					PortProto pp = layerNodeProto.getPort(0);
					for (Iterator<ArcProto> it = layer.getTechnology().getArcs(); it.hasNext(); )
					{
						ArcProto arc = it.next();
						if (pp.connectsTo(arc))
						{
							pinNodeProto = arc.findOverridablePinProto(ep);
							break;
						}
					}
				}
			}
			if (layerNodeProto == null)
			{
				String message = "Error: no pure layer node for layer '" + layer.getName() + "', ignoring it";
				list.clear();
				list.add(Generic.tech().drcLay);
				layerNames.put(layerInt, list);
				if (!localPrefs.skeletonize)
				{
					currentUnknownLayerMessage = new UnknownLayerMessage(message, "No_pure_layer_for_" + layer.getName());
					layerErrorMessages.put(layerInt, currentUnknownLayerMessage);
				}
			}
		}
	}

	private void determineLayer(boolean textCase)
		throws Exception
	{
		if (gdsRead.getTokenType() != GDSReader.GDS_LAYER) gdsRead.handleError("Layer statement is missing");

		gdsRead.getToken();
		if (gdsRead.getTokenType() != GDSReader.GDS_SHORT_NUMBER) gdsRead.handleError("Invalid layer number");

		int layerNum = gdsRead.getShortValue();
		gdsRead.getToken();
		if (!isMember(gdsRead.getTokenType(), maskSet)) gdsRead.handleError("No datatype field");

		gdsRead.getToken();
		setLayer(layerNum, gdsRead.getShortValue(), textCase);
	}

	/**
	 * Method to get the justification information into a Point.
	 * @return a point whose "x" is the vertical justification and whose
	 * "y" is the horizontal justification.
	 */
	private Point determineJustification()
		throws Exception
	{
		Point just = new Point();
		gdsRead.getToken();
		if (gdsRead.getTokenType() != GDSReader.GDS_FLAGSYM) gdsRead.handleError("Array reference has no parameters");

		int font_libno = gdsRead.getFlagsValue() & 0x0030;
		font_libno = font_libno >> 4;
		just.x = gdsRead.getFlagsValue() & 0x000C;
		just.x = just.x >> 2;
		just.y = gdsRead.getFlagsValue() & 0x0003;
		gdsRead.getToken();
		return just;
	}

	private void determineProperty()
		throws Exception
	{
		gdsRead.getToken();
		gdsRead.getToken();
		if (gdsRead.getTokenType() != GDSReader.GDS_PROPVALUE) gdsRead.handleError("Property has no value");

		gdsRead.getToken();
		String property = gdsRead.getStringValue();

		// the TBLR property gives export details and has this format:
		// access_dir pinName terminalName direction
		// where "access_dir" can be any of the letters "TBLR"
		// where "pinName" Name of the pin (can be a number)
		// where "terminalName" Name of the export
		// where "direction" one of: input, output, inputOutput, switch, jumper, unused, unknown
		if (lastExportInstance != null)
		{
			String[] parts = property.split(" ");
			if (parts.length >= 4)
			{
				String portName = parts[2];
				String direction = parts[3];
				if (portName.equals(lastExportInstance.exportOrTextName))
				{
					if (direction.equals("input")) lastExportInstance.pc = PortCharacteristic.IN; else
						if (direction.equals("output")) lastExportInstance.pc = PortCharacteristic.OUT; else
							if (direction.equals("inputOutput")) lastExportInstance.pc = PortCharacteristic.BIDIR;
				}
				lastExportInstance = null;
			}
		}

		// add to the current structure as a variable?
		gdsRead.getToken();
	}

	private void getPrototype(String name)
		throws Exception
	{
		// scan for this prototype
		if (localPrefs.skeletonize) name += "{lay.sk}"; else
			name += "{lay}";
		Cell np = findCell(name);
		if (np == null)
		{
			// FILO order, create this nodeproto
			np = Cell.newInstance(theLibrary, name);
			if (np == null) gdsRead.handleError("Failed to create SREF proto");
			setProgressValue(0);
			setProgressNote("Reading " + name);
			missingCells.add(np);
		}

		// set the reference node prototype
		theNodeProto = np;
	}

	private void readGenerations()
		throws Exception
	{
		gdsRead.getToken();
		if (gdsRead.getTokenType() != GDSReader.GDS_SHORT_NUMBER) gdsRead.handleError("Generations value is invalid");
		gdsRead.getToken();
		if (localPrefs.dumpReadable) printWriter.println("- Generations");
	}

	private boolean isMember(GSymbol tok, GSymbol [] set)
	{
		for(int i=0; i<set.length; i++)
			if (set[i] == tok) return true;
		return false;
	}

	private void readUnsupported(GSymbol bad_op_set[])
		throws Exception
	{
		if (isMember(gdsRead.getTokenType(), bad_op_set))
		{
			do
			{
				gdsRead.getToken();
			} while (!isMember(gdsRead.getTokenType(), goodOpSet));
		}
	}

	private void determinePoints(int min_points, int max_points)
		throws Exception
	{
		numVertices = 0;
		while (gdsRead.getTokenType() == GDSReader.GDS_NUMBER)
		{
			double x = scaleValue(gdsRead.getIntValue());
			gdsRead.getToken();
			double y = scaleValue(gdsRead.getIntValue());
			theVertices[numVertices].setLocation(x, y);
			numVertices++;
			if (numVertices > max_points)
			{
				System.out.println("Found " + numVertices + " points (too many)");
				gdsRead.handleError("Too many points in the shape");
			}
			gdsRead.getToken();
		}
		if (numVertices < min_points)
		{
			System.out.println("Found " + numVertices + " points (too few)");
			gdsRead.handleError("Not enough points in the shape");
		}
	}
}
