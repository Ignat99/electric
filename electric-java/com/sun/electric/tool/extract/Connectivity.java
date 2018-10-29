/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Connectivity.java
 * Module to do node extraction (extract connectivity from a pure-layout cell)
 * Written by Steven M. Rubin.
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
package com.sun.electric.tool.extract;

import com.sun.electric.database.EditingPreferences;
import com.sun.electric.database.geometry.EPoint;
import com.sun.electric.database.geometry.ERectangle;
import com.sun.electric.database.geometry.GeometryHandler;
import com.sun.electric.database.geometry.Poly;
import com.sun.electric.database.geometry.PolyBase;
import com.sun.electric.database.geometry.PolyMerge;
import com.sun.electric.database.geometry.PolySweepMerge;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.Export;
import com.sun.electric.database.network.Netlist;
import com.sun.electric.database.network.Network;
import com.sun.electric.database.prototype.NodeProto;
import com.sun.electric.database.prototype.PortProto;
import com.sun.electric.database.text.Name;
import com.sun.electric.database.text.TextUtils;
import com.sun.electric.database.topology.ArcInst;
import com.sun.electric.database.topology.Connection;
import com.sun.electric.database.topology.Geometric;
import com.sun.electric.database.topology.HeadConnection;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.topology.PortInst;
import com.sun.electric.database.topology.RTBounds;
import com.sun.electric.database.topology.RTNode;
import com.sun.electric.database.variable.DisplayedText;
import com.sun.electric.database.variable.EditWindow_;
import com.sun.electric.database.variable.ElectricObject;
import com.sun.electric.database.variable.UserInterface;
import com.sun.electric.database.variable.Variable;
import com.sun.electric.technology.ArcProto;
import com.sun.electric.technology.Layer;
import com.sun.electric.technology.PrimitiveNode;
import com.sun.electric.technology.PrimitivePort;
import com.sun.electric.technology.SizeOffset;
import com.sun.electric.technology.Technology;
import com.sun.electric.technology.Technology.NodeLayer;
import com.sun.electric.technology.Technology.TechPoint;
import com.sun.electric.technology.technologies.Generic;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.JobException;
import com.sun.electric.tool.routing.AutoStitch;
import com.sun.electric.tool.routing.AutoStitch.AutoOptions;
import com.sun.electric.tool.user.ErrorLogger;
import com.sun.electric.tool.user.Highlight;
import com.sun.electric.tool.user.User;
import com.sun.electric.tool.user.dialogs.EDialog;
import com.sun.electric.tool.user.ui.TopLevel;
import com.sun.electric.util.math.DBMath;
import com.sun.electric.util.math.ECoord;
import com.sun.electric.util.math.EDimension;
import com.sun.electric.util.math.FixpRectangle;
import com.sun.electric.util.math.FixpTransform;
import com.sun.electric.util.math.GenMath;
import com.sun.electric.util.math.MutableBoolean;
import com.sun.electric.util.math.MutableInteger;
import com.sun.electric.util.math.Orientation;

import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;

/**
 * This is the Connectivity extractor.
 *
 * Still need to handle non-Manhattan contacts
 */
public class Connectivity
{
	/** true to prevent objects smaller than minimum size */	private static final boolean ENFORCEMINIMUMSIZE = false;
	/** true to debug centerline determination */				private static final boolean DEBUGCENTERLINES = false;
	/** true to debug object creation */						private static final boolean DEBUGSTEPS = false;
	/** true to debug contact extraction */						private static final boolean DEBUGCONTACTS = false;
	/** amount to scale values before merging */				private static final double SCALEFACTOR = DBMath.GRID;

	/** the current technology for extraction */				private Technology tech;
	/** layers to use for given arc functions */				private Map<Layer.Function,Layer> layerForFunction;
	/** the layer to use for "polysilicon" geometry */			private Layer polyLayer;
	/** temporary layers to use for geometric manipulation */	private Layer tempLayer1;
	/** the layers to use for "active" geometry */				private Layer activeLayer, pActiveLayer, nActiveLayer;
	/** the layers to use for "select" geometry */				private Layer pSelectLayer, nSelectLayer;
	/** the real "active" layers */								private Layer realPActiveLayer, realNActiveLayer;
    /** the well and substrate layers */                        private Layer wellLayer, substrateLayer;
    /** associates arc prototypes with layers */				private Map<Layer,ArcProto> arcsForLayer;
	/** map of extracted cells */								private Map<Cell,Cell> convertedCells;
	/** map of cut layers to lists of polygons on that layer */	private Map<Layer,CutInfo> allCutLayers;
	/** set of pure-layer nodes that are not processed */		private Set<PrimitiveNode> ignoreNodes;
	/** set of contacts that are not used for extraction */		private Set<PrimitiveNode> bogusContacts;
    /** PrimitiveNodes for p-diffusion and n-diffusion */       private PrimitiveNode diffNode, pDiffNode, nDiffNode;
    /** list of Exports to restore after extraction */			private List<Export> exportsToRestore;
	/** auto-generated exports that may need better names */	private List<Export> generatedExports;
	/** true if this is a P-well process (presume P-well) */	private boolean pSubstrateProcess;
	/** true if this is a N-well process (presume N-well) */	private boolean nSubstrateProcess;
	/** helper variables for computing N/P process factors */	private boolean hasWell, hasPWell, hasNWell;
	/** true to unify N and P active layers */					private boolean unifyActive;
	/** helper variables for computing N and P active unify */	private boolean haveNActive, havePActive;
	/** true to ignore select/well around active layers */		private boolean ignoreActiveSelectWell;
	/** true to approximate cut placement */					private boolean approximateCuts;
	/** true if extracting hierarchically */					private boolean recursive;
	/** the smallest polygon acceptable for merging */			private double smallestPoly;
	/** debugging: list of objects created */					private List<ERectangle> addedRectangles;
	/** debugging: list of objects created */					private List<ERectangle> addedLines;
	/** list of exported pins to realize at the end */			private List<ExportedPin> pinsForLater;
	/** ErrorLogger to keep up with errors during extraction */ private ErrorLogger errorLogger;
	/** total number of cells to extract when recursing */		private int totalCells;
	/** total number of cells extracted when recursing */		private int cellsExtracted;
	/** Job that is holding the process */						private Job job;
    /** EditingPreferences */                                   private EditingPreferences ep;
	/** Grid alignment for edges */								private EDimension alignment;

	/**
	 * Method to examine the current cell and extract it's connectivity in a new one.
	 * @param recursive true to recursively extract the hierarchy below this cell.
	 */
	public static void extractCurCell(boolean recursive)
	{
		Cell curCell = Job.getUserInterface().needCurrentCell();
		if (curCell == null)
		{
			System.out.println("Must be editing a cell with pure layer nodes.");
			return;
		}
		if (!curCell.isLayout())
		{
			System.out.println("Cell '" + curCell.getCellName() + "' is not a layout cell.");
			return;
		}
		new ExtractJob(curCell, recursive);
	}

	private static class ExtractJob extends Job
	{
		private Cell cell, newCell;
		private boolean recursive;
		private double smallestPolygonSize;
		private int activeHandling;
		private String expansionPattern;
		private boolean gridAlignExtraction;
        private final ECoord scaledResolution;
		private boolean approximateCuts;
		private boolean flattenPcells;
		private boolean usePureLayerNodes;
		/** debugging: list of objects created */	private List<List<ERectangle>> addedBatchRectangles;
		/** debugging: list of objects created */	private List<List<ERectangle>> addedBatchLines;
		/** debugging: list of objects created */	private List<String> addedBatchNames;
		/** */ private ErrorLogger errorLogger;

		private ExtractJob(Cell cell, boolean recursive)
		{
			super("Extract Connectivity from " + cell, Extract.getExtractTool(), Job.Type.CHANGE, null, null, Job.Priority.USER);
			this.cell = cell;
			this.recursive = recursive;
			this.errorLogger = ErrorLogger.newInstance("Extraction Tool on cell " + cell.getName());
			smallestPolygonSize = Extract.isIgnoreTinyPolygons() ? Extract.getSmallestPolygonSize() : 0;
			activeHandling = Extract.getActiveHandling();
			expansionPattern = Extract.getCellExpandPattern().trim();
			gridAlignExtraction = Extract.isGridAlignExtraction();
            Technology tech = cell.getTechnology();
            scaledResolution = tech.getFactoryResolution();
//            scaledResolution = tech.getFactoryScaledResolution();
//            scaledResolution = new DRC.DRCPreferences(false).getResolution(tech);
			approximateCuts = Extract.isApproximateCuts();
			flattenPcells = Extract.isFlattenPcells();
			usePureLayerNodes = Extract.isUsePureLayerNodes();
			startJob();
		}

		public boolean doIt() throws JobException
		{
			// get pattern for matching cells to expand
			List<Pattern> pats = new ArrayList<Pattern>();
			if (expansionPattern.length() > 0)
			{
				String [] patParts = expansionPattern.split(",");
				for(int i=0; i<patParts.length; i++)
				{
					String part = patParts[i].trim();
					if (part.length() == 0) continue;
					try
					{
						pats.add(Pattern.compile(part, Pattern.CASE_INSENSITIVE));
					} catch(PatternSyntaxException e)
					{
						System.out.println("Pattern syntax error on '" + part + "': " + e.getMessage());
					}
				}
			}

			if (DEBUGSTEPS)
			{
				addedBatchRectangles = new ArrayList<List<ERectangle>>();
				addedBatchLines = new ArrayList<List<ERectangle>>();
				addedBatchNames = new ArrayList<String>();
			}
			Job.getUserInterface().startProgressDialog("Extracting", null);

			Connectivity c = new Connectivity(cell, this, getEditingPreferences(), errorLogger, smallestPolygonSize, activeHandling,
				gridAlignExtraction, scaledResolution, approximateCuts, recursive, pats);

			if (recursive) c.totalCells = c.countExtracted(cell, pats, flattenPcells);

			newCell = c.doExtract(cell, recursive, pats, flattenPcells, usePureLayerNodes,
				true, this, addedBatchRectangles, addedBatchLines, addedBatchNames);
			if (newCell == null)
				System.out.println("ERROR: Extraction of cell " + cell.describe(false) + " failed");

			Job.getUserInterface().stopProgressDialog();
			fieldVariableChanged("addedBatchRectangles");
			fieldVariableChanged("addedBatchLines");
			fieldVariableChanged("addedBatchNames");
			fieldVariableChanged("newCell");
			fieldVariableChanged("errorLogger");
			return true;
		}

		public void terminateOK()
		{
			UserInterface ui = Job.getUserInterface();
			EditWindow_ wnd = ui.displayCell(newCell);
			Job.getUserInterface().termLogging(errorLogger, false, false);

			if (DEBUGSTEPS)
			{
				// show results of each step
				JFrame jf = null;
				if (!TopLevel.isMDIMode()) jf = TopLevel.getCurrentJFrame();
				ShowExtraction theDialog = new ShowExtraction(jf, addedBatchRectangles, addedBatchLines, addedBatchNames);
				theDialog.setVisible(true);
			} else
			{
				// highlight pure layer nodes
				if (newCell != null) // cell is null if job was aborted
				{
					for(Iterator<NodeInst> it = newCell.getNodes(); it.hasNext(); )
					{
						NodeInst ni = it.next();
						PrimitiveNode.Function fun = ni.getFunction();
						if (fun == PrimitiveNode.Function.NODE)
							wnd.addElectricObject(ni, newCell);
					}
				}
				else
					System.out.println("Extraction job was aborted");
			}
		}
	}

    /**
     * Constructor to initialize connectivity extraction.
     * @param cell the cell
     * @param j the job
     * @param eLog the error logger
     * @param smallestPolygonSize the smallest polygon size
     * @param activeHandling
	 * 0: Insist on two different active layers (N and P) and also proper select/well surrounds (the default).
	 * 1: Ignore active distinctions and use select/well surrounds to distinguish N from P.
	 * 2: Insist on two different active layers (N and P) but ignore select/well surrounds.
     * @param gridAlignExtraction true to align extraction to some the technology grid
     * @param approximateCuts approximate cuts
     * @param recursive run recursively
     * @param pats a List of cell name patterns that will be flattened.
     */
	public Connectivity(Cell cell, Job j, EditingPreferences ep, ErrorLogger eLog, double smallestPolygonSize, int activeHandling,
		boolean gridAlignExtraction, ECoord scaledResolution, boolean approximateCuts, boolean recursive,
		List<Pattern> pats)
	{
	    this.approximateCuts = approximateCuts;
		this.recursive = recursive;
		tech = cell.getTechnology();
		convertedCells = new HashMap<Cell,Cell>();
		smallestPoly = (SCALEFACTOR * SCALEFACTOR) * smallestPolygonSize;
		bogusContacts = new HashSet<PrimitiveNode>();
		errorLogger = eLog;
		cellsExtracted = 0;
		job = j;
        this.ep = ep;

        alignment = null;
        if (gridAlignExtraction && scaledResolution.getLambda() > 0)
        	alignment = new EDimension(scaledResolution, scaledResolution);

        diffNode = pDiffNode = nDiffNode = null;
        // find pure-layer nodes that are never involved in higher-level components, and should be ignored
		ignoreNodes = new HashSet<PrimitiveNode>();
		for(Iterator<PrimitiveNode> pIt = tech.getNodes(); pIt.hasNext(); )
		{
			PrimitiveNode np = pIt.next();
			if (np.getFunction() != PrimitiveNode.Function.NODE) continue;
			Technology.NodeLayer [] nLays = np.getNodeLayers();
			boolean validLayers = false;
			for(int i=0; i<nLays.length; i++)
			{
				Technology.NodeLayer nLay = nLays[i];
				Layer.Function fun = nLay.getLayer().getFunction();
				if (fun == Layer.Function.UNKNOWN || fun == Layer.Function.OVERGLASS ||
					fun == Layer.Function.GUARD || fun == Layer.Function.ISOLATION ||
					fun == Layer.Function.BUS ||
					fun == Layer.Function.CONTROL || fun == Layer.Function.TILENOT) continue;
				validLayers = true;
			}
			if (!validLayers) ignoreNodes.add(np);

            // determine diffusion nodes
            Layer layer = np.getLayerIterator().next();
            if (layer.getFunction() == Layer.Function.DIFF)
                diffNode = np;
            if (layer.getFunction() == Layer.Function.DIFFN)
                nDiffNode = np;
            if (layer.getFunction() == Layer.Function.DIFFP)
                pDiffNode = np;
        }

		// determine if this is a "P-well" or "N-well" process
		findMissingWells(cell, recursive, pats, activeHandling);

		// find important layers
		polyLayer = null;
		activeLayer = pActiveLayer = nActiveLayer = null;
		realPActiveLayer = realNActiveLayer = null;
        pSelectLayer = nSelectLayer = null;
        wellLayer = substrateLayer = null;
        for(Iterator<Layer> it = tech.getLayers(); it.hasNext(); )
		{
			Layer layer = it.next();
			Layer.Function fun = layer.getFunction();
			if (polyLayer == null && fun == Layer.Function.POLY1) polyLayer = layer;
            if (activeLayer == null && fun == Layer.Function.DIFF) activeLayer = layer;
            if (pActiveLayer == null && fun == Layer.Function.DIFFP) pActiveLayer = layer;
            if (nActiveLayer == null && fun == Layer.Function.DIFFN) nActiveLayer = layer;
			if (realPActiveLayer == null && fun == Layer.Function.DIFFP) realPActiveLayer = layer;
			if (realNActiveLayer == null && fun == Layer.Function.DIFFN) realNActiveLayer = layer;
            if (pSelectLayer == null && fun == Layer.Function.IMPLANTP) pSelectLayer = layer;
            if (nSelectLayer == null && fun == Layer.Function.IMPLANTN) nSelectLayer = layer;
            if (pSubstrateProcess) // p-substrate
            {
                if (wellLayer == null && fun == Layer.Function.WELLN) wellLayer = layer;
                if (substrateLayer == null && fun == Layer.Function.WELLP) substrateLayer = layer;
            }
            if (nSubstrateProcess) // n-substrate
            {
                if (wellLayer == null && fun == Layer.Function.WELLP) wellLayer = layer;
                if (substrateLayer == null && fun == Layer.Function.WELLN) substrateLayer = layer;
            }
        }
		tempLayer1 = Generic.tech().drcLay;
		if (havePActive != haveNActive)
		{
			// only one active layer found: suggest ignorance of the distinction
			if (activeHandling != 1)
				System.out.println("Found only one type of active layer. It may help to set 'Ignore N vs. P active' in Network Preferences.");
//			activeHandling = 1;
            if (!haveNActive) nActiveLayer = pActiveLayer;
            if (!havePActive) pActiveLayer = nActiveLayer;
		}
        unifyActive = false;
        if (activeHandling == 1)
        {
        	// ignoring n/p distinction in active handling
            unifyActive = true;
        }
        if ((pActiveLayer == null || nActiveLayer == null) && activeLayer != null)
        {
        	// technology has only one active layer: unify them
            unifyActive = true;
            pActiveLayer = nActiveLayer = activeLayer;
        }

		// figure out which arcs to use for a layer
		arcsForLayer = new HashMap<Layer,ArcProto>();
		for(Iterator<Layer> it = tech.getLayers(); it.hasNext(); )
		{
			Layer layer = it.next();
			if (layer.getFunctionExtras() == Layer.Function.INTERCONNECT) continue;
			Layer.Function fun = layer.getFunction();
			if (fun.isDiff() || fun.isPoly() || fun.isMetal())
			{
				ArcProto.Function oFun = null;
				if (fun.isMetal()) oFun = ArcProto.Function.getMetal(fun.getLevel());
				if (fun.isPoly()) oFun = ArcProto.Function.getPoly(fun.getLevel());
				if (oFun == null) continue;
				ArcProto type = null;
				for(Iterator<ArcProto> aIt = tech.getArcs(); aIt.hasNext(); )
				{
					ArcProto ap = aIt.next();
					if (ap.getFunction() == oFun) { type = ap;   break; }
				}
				if (type != null) arcsForLayer.put(layer, type);
			}
		}

		// build the mapping from any layer to the proper ones for the geometric database
		layerForFunction = new HashMap<Layer.Function,Layer>();
		for(Iterator<Layer> it = tech.getLayers(); it.hasNext(); )
		{
			Layer layer = it.next();
			if (layer.getFunctionExtras() == Layer.Function.INTERCONNECT) continue;
			Layer.Function fun = layer.getFunction();
			if (unifyActive)
			{
				if (fun == Layer.Function.DIFFP || fun == Layer.Function.DIFFN)
					fun = Layer.Function.DIFF;
			}
			if (layerForFunction.get(fun) == null)
				layerForFunction.put(fun, layer);
		}
	}

	/**
	 * Method to log errors during node extraction.
	 */
	private void addErrorLog(Cell cell, String msg, EPoint... pList)
	{
		List<EPoint> pointList = new ArrayList<EPoint>();
		for(EPoint p : pList)
			pointList.add(p);
		errorLogger.logMessage(msg, pointList, cell, -1, true);
		System.out.println(msg);
	}

	public int countExtracted(Cell oldCell, List<Pattern> pats, boolean flattenPcells)
	{
		int numExtracted = 1;
		for(Iterator<NodeInst> it = oldCell.getNodes(); it.hasNext(); )
		{
			NodeInst ni = it.next();
			if (ni.isCellInstance())
			{
				Cell subCell = (Cell)ni.getProto();

				// do not recurse if this subcell will be expanded
				if (isCellFlattened(subCell, pats, flattenPcells)) continue;

				Cell convertedCell = convertedCells.get(subCell);
				if (convertedCell == null)
				{
					numExtracted += countExtracted(subCell, pats, flattenPcells);
				}
			}
		}
		return numExtracted;
	}

	/**
	 * Method to determine whether to flatten a cell.
	 * @param cell the cell in question.
	 * @param pats patterns of cells to be flattened.
	 * @param flattenPcells true if Cadence Pcells are to be flattened.
	 * @return true if the cell should be flattened.
	 */
	private boolean isCellFlattened(Cell cell, List<Pattern> pats, boolean flattenPcells)
	{
		// do not recurse if this subcell will be expanded
		for(Pattern pat : pats)
		{
			Matcher mat = pat.matcher(cell.noLibDescribe());
			if (mat.find()) return true;
		}

		if (flattenPcells)
		{
			String cellName = cell.noLibDescribe();
			int twoDollar = cellName.lastIndexOf("$$");
			if (twoDollar > 0)
			{
				String endPart = cellName.substring(twoDollar+2);
				for(int i=0; i<endPart.length(); i++)
				{
					char ch = endPart.charAt(i);
					if (ch == '{') break;
					if (!TextUtils.isDigit(ch)) return false;
				}
				return true;
			}
		}
		return false;
	}

	/**
	 * Top-level method in extracting connectivity from a Cell.
	 * A new version of the cell is created that has real nodes (transistors, contacts) and arcs.
	 * This cell should have pure-layer nodes which will be converted.
	 */
	public Cell doExtract(Cell oldCell, boolean recursive, List<Pattern> pats, boolean flattenPcells, boolean usePureLayerNodes,
		boolean top, Job job, List<List<ERectangle>> addedBatchRectangles, List<List<ERectangle>> addedBatchLines, List<String> addedBatchNames)
	{
		if (recursive)
		{
			// first see if subcells need to be converted
			for(Iterator<NodeInst> it = oldCell.getNodes(); it.hasNext(); )
			{
				NodeInst ni = it.next();
				if (ni.isCellInstance())
				{
					Cell subCell = (Cell)ni.getProto();

					// do not recurse if this subcell will be expanded
					if (isCellFlattened(subCell, pats, flattenPcells)) continue;

					Cell convertedCell = convertedCells.get(subCell);
					if (convertedCell == null)
					{
						Cell result = doExtract(subCell, recursive, pats, flattenPcells, usePureLayerNodes,
							false, job, addedBatchRectangles, addedBatchLines, addedBatchNames);
						if (result == null)
							System.out.println("ERROR: Extraction of cell " + subCell.describe(false) + " failed");
					}
				}
			}
		}

		// create the new version of the cell
		String newCellName = oldCell.getName() + oldCell.getView().getAbbreviationExtension();
		Cell newCell = Cell.makeInstance(ep, oldCell.getLibrary(), newCellName);
		if (newCell == null)
		{
			System.out.println("Cannot create new cell: " + newCellName);
			return null;
		}
		convertedCells.put(oldCell, newCell);

		// create a merge for the geometry in the cell
		PolyMerge merge = new PolyMerge();
        PolyMerge selectMerge = new PolyMerge();

        // convert the nodes
		if (!startSection(oldCell, "Gathering geometry in " + oldCell + "..."))		// HAS PROGRESS IN IT
			return null; // aborted
		Set<Cell> expandedCells = new HashSet<Cell>();
		exportsToRestore = new ArrayList<Export>();
		generatedExports = new ArrayList<Export>();
		pinsForLater = new ArrayList<ExportedPin>();
		allCutLayers = new TreeMap<Layer,CutInfo>();
		extractCell(oldCell, newCell, pats, flattenPcells, expandedCells, merge, selectMerge, GenMath.MATID, Orientation.IDENT);
		if (expandedCells.size() > 0)
		{
			System.out.print("These cells were expanded:");
			for(Cell c : expandedCells)
				System.out.print(" " + c.describe(false));
			System.out.println();
		}

		// now remember the original merge
		PolyMerge originalMerge = new PolyMerge();
		originalMerge.addMerge(merge, new FixpTransform());

		// start by extracting vias
		initDebugging();
		if (!startSection(oldCell, "Extracting vias...")) return null; // aborted
		if (!extractVias(merge, originalMerge, oldCell, newCell, usePureLayerNodes)) return null; // aborted
		termDebugging(addedBatchRectangles, addedBatchLines, addedBatchNames, "Vias");

		// now extract transistors
		initDebugging();
		if (!startSection(oldCell, "Extracting transistors...")) return null; // aborted
		extractTransistors(merge, originalMerge, newCell, usePureLayerNodes);
		termDebugging(addedBatchRectangles, addedBatchLines, addedBatchNames, "Transistors");

        if (usePureLayerNodes) {
            // dump back in original routing layers
            if (!startSection(oldCell, "Adding in original routing layers...")) return null;
            addInRoutingLayers(oldCell, newCell, merge, originalMerge, usePureLayerNodes);

        } else {

            // extend geometry that sticks out in space
/*
            initDebugging();
            if (!startSection(oldCell, "Extracting extensions...")) return null; // aborted
            extendGeometry(merge, originalMerge, newCell, true, usePureLayerNodes);
            termDebugging(addedBatchRectangles, addedBatchLines, addedBatchNames, "StickOuts");
 */

            // look for wires and pins
            initDebugging();
            if (!startSection(oldCell, "Extracting wires...")) return null; // aborted
            if (makeWires(merge, originalMerge, newCell, usePureLayerNodes)) return newCell;
            termDebugging(addedBatchRectangles, addedBatchLines, addedBatchNames, "Wires");

            // convert any geometry that connects two networks
            initDebugging();
            if (!startSection(oldCell, "Extracting connections...")) return null; // aborted
            extendGeometry(merge, originalMerge, newCell, false, usePureLayerNodes);
            termDebugging(addedBatchRectangles, addedBatchLines, addedBatchNames, "Bridges");
        }

        // dump any remaining layers back in as extra pure layer nodes
        initDebugging();
        if (!startSection(oldCell, "Extracting leftover geometry...")) return null; // aborted
        convertAllGeometry(merge, originalMerge, newCell, usePureLayerNodes);
        termDebugging(addedBatchRectangles, addedBatchLines, addedBatchNames, "Pures");

        // reexport any that were there before
		if (!startSection(oldCell, "Adding connecting wires...")) return null; // aborted
		cleanupExports(oldCell, newCell);

		// cleanup by auto-stitching
		PolyMerge originalUnscaledMerge = new PolyMerge();
		double shrinkage = 1.0 / SCALEFACTOR;
		FixpTransform shrink = new FixpTransform(shrinkage, 0, 0, shrinkage, 0, 0);
		originalUnscaledMerge.addMerge(originalMerge, shrink);
		Set<ArcInst> allArcs = null;

        allArcs = new HashSet<ArcInst>();
        for(Iterator<ArcInst> it = newCell.getArcs(); it.hasNext(); )
            allArcs.add(it.next());

        // make sure current arc is not universal arc, otherwise it makes the InteractiveRouter (used by AutoStitch) prefer that arc
        if (User.getUserTool().getCurrentArcProto() == Generic.tech().universal_arc) {
            User.getUserTool().setCurrentArcProto(newCell.getTechnology().getArcs().next());
        }

        // TODO: originalMerge passed to auto stitcher really needs to include subcell geometry too, in order
        // for the auto-stitcher to know where it can place arcs. However, building and maintaining such a hash map
        // might take up a lot of memory.
        AutoOptions prefs = new AutoOptions(true);
		prefs.createExports = true;
		AutoStitch.runAutoStitch(newCell, null, null, job, originalUnscaledMerge, null, false, true, ep, prefs, !recursive, alignment);

        // check all the arcs that auto-stitching added, and replace them by universal arcs if they are off-grid
        if (alignment != null)
        {
            for(Iterator<ArcInst> it = newCell.getArcs(); it.hasNext(); )
            {
                ArcInst ai = it.next();
                if (allArcs.contains(ai)) continue;
                Rectangle2D bounds = ai.getBounds();
                long lX = (long)(bounds.getMinX() / alignment.getWidth());
                long hX = (long)(bounds.getMaxX() / alignment.getWidth());
                long lY = (long)(bounds.getMinY() / alignment.getHeight());
                long hY = (long)(bounds.getMaxY() / alignment.getHeight());
                if (lX * alignment.getWidth() != bounds.getMinX() ||
                	lY * alignment.getHeight() != bounds.getMinY() ||
                	hX * alignment.getWidth() != bounds.getMaxX() ||
                	hY * alignment.getHeight() != bounds.getMaxY())
                {
                    // replace
                    Connection head = ai.getHead();
                    Connection tail = ai.getTail();
                    ArcInst newAi = ArcInst.makeInstanceBase(Generic.tech().universal_arc, ep, 0, head.getPortInst(), tail.getPortInst(),
                            head.getLocation(), tail.getLocation(), null);
                    if (newAi != null)
                    {
                        newAi.setHeadExtended(false);
                        newAi.setTailExtended(false);
                        ai.kill();
                    }
                }
            }
        }

        if (DEBUGSTEPS)
		{
			initDebugging();
			for(Iterator<ArcInst> it = newCell.getArcs(); it.hasNext(); )
			{
				ArcInst ai = it.next();
				if (allArcs.contains(ai)) continue;
				Poly arcPoly = ai.makeLambdaPoly(ai.getGridBaseWidth(), Poly.Type.CLOSED);
				addedRectangles.add(ERectangle.fromLambda(arcPoly.getBounds2D()));
			}
			termDebugging(addedBatchRectangles, addedBatchLines, addedBatchNames, "Stitches");
		}
		System.out.println("Extraction done.");

		cellsExtracted++;
		if (recursive) Job.getUserInterface().setProgressValue(cellsExtracted * 100 / totalCells);
		return newCell;
	}

	/**
	 * Method to start a new connection section.
	 * @param msg message to display in progress window
	 * @return False if the job is scheduled for abort or was aborted
	 */
	private boolean startSection(Cell cell, String msg)
	{
		System.out.println(msg);
		if (job != null && job.checkAbort())
			return false;
		if (recursive) msg = cell.getName() + " - " + msg;
		Job.getUserInterface().setProgressNote(msg);
		if (!recursive) Job.getUserInterface().setProgressValue(0);
		return true;
	}

	private void initDebugging()
	{
		if (DEBUGSTEPS)
		{
			addedRectangles = new ArrayList<ERectangle>();
			addedLines = new ArrayList<ERectangle>();
		}
	}

	private void termDebugging(List<List<ERectangle>> addedBatchRectangles,
		List<List<ERectangle>> addedBatchLines, List<String> addedBatchNames, String descr)
	{
		if (DEBUGSTEPS)
		{
			addedBatchRectangles.add(addedRectangles);
			addedBatchLines.add(addedLines);
			addedBatchNames.add(descr);
		}
	}

	private static class ExportedPin
	{
		Point2D location;
		NodeInst ni;
		FixpTransform trans;

		ExportedPin(NodeInst ni, Point2D location, FixpTransform trans)
		{
			this.ni = ni;
			this.location = location;
			this.trans = trans;
		}
	}

	/**
	 * Method to extract a cell's contents into the merge.
	 * @param oldCell the cell being extracted.
	 * @param newCell the new cell being created.
	 * @param pats patterns of subcell names that will be expanded.
	 * @param flattenPcells true to expand Cadence Pcells (which end with $$number).
	 * @param expandedCells a set of cells that matched the pattern and were expanded.
	 * @param merge the merge to be filled.
	 * @param prevTrans the transformation coming into this cell.
	 */
	private void extractCell(Cell oldCell, Cell newCell, List<Pattern> pats, boolean flattenPcells, Set<Cell> expandedCells,
		PolyMerge merge, PolyMerge selectMerge, FixpTransform prevTrans, Orientation orient)
	{
		Map<NodeInst,NodeInst> newNodes = new HashMap<NodeInst,NodeInst>();
		int totalNodes = oldCell.getNumNodes();

        // first get select, so we can determine proper active type
        if (!unifyActive && !ignoreActiveSelectWell)
        {
            for (Iterator<NodeInst> nIt = oldCell.getNodes(); nIt.hasNext(); )
            {
                NodeInst ni = nIt.next();
                if (ni.isCellInstance()) continue;
                Poly [] polys = tech.getShapeOfNode(ni);
                for(int j=0; j<polys.length; j++)
                {
                    Poly poly = polys[j];

                    // get the layer for the geometry
                    Layer layer = poly.getLayer();
                    if (layer == null) continue;

                    // make sure the geometric database is made up of proper layers
                    layer = geometricLayer(layer);
                    if (layer.getFunction() != Layer.Function.IMPLANTN && layer.getFunction() != Layer.Function.IMPLANTP) continue;

                    // selectMerge has non-scaled-up coordinates, and has all geometric coordinates relative to top level
                    FixpTransform trans = ni.rotateOut(prevTrans);
                    poly.transform(trans);
                    selectMerge.add(layer, poly);
                }
            }
        }

        int soFar = 0;
		for(Iterator<NodeInst> nIt = oldCell.getNodes(); nIt.hasNext(); )
		{
			NodeInst ni = nIt.next();
			soFar++;
			if (!recursive && (soFar % 100) == 0) Job.getUserInterface().setProgressValue(soFar * 100 / totalNodes);
			if (Generic.isCellCenter(ni)) continue;

			// see if the node can be copied or must be extracted
			NodeProto copyType = null;
			if (ni.isCellInstance())
			{
				Cell subCell = (Cell)ni.getProto();

				// if subcell is expanded, do it now
				boolean flatIt = isCellFlattened(subCell, pats, flattenPcells);
				if (flatIt)
				{
					// expanding the subcell
					expandedCells.add(subCell);
					FixpTransform subTrans = ni.translateOut(ni.rotateOut(prevTrans));
					Orientation or = orient.concatenate(ni.getOrient());
					extractCell(subCell, newCell, pats, flattenPcells, expandedCells, merge, selectMerge, subTrans, or);
					continue;
				}

				// subcell not expanded, figure out what gets placed in the new cell
				copyType = convertedCells.get(subCell);
				if (copyType == null) copyType = subCell;
			} else
			{
				PrimitiveNode np = (PrimitiveNode)ni.getProto();

				// special case for exported but unconnected pins: save for later
				if (np.getFunction().isPin())
				{
					if (ni.hasExports() && !ni.hasConnections())
					{
						ExportedPin ep = new ExportedPin(ni, ni.getTrueCenter(), prevTrans);
						pinsForLater.add(ep);
						continue;
					}
				}
				if (np.getFunction() != PrimitiveNode.Function.NODE) copyType = ni.getProto(); else
				{
					if (ignoreNodes.contains(np)) copyType = ni.getProto();
				}
			}

			// copy it now if requested
			if (copyType != null)
			{
				double sX = ni.getXSize();
				double sY = ni.getYSize();
				if (copyType instanceof Cell)
				{
					Rectangle2D cellBounds = ((Cell)copyType).getBounds();
					sX = cellBounds.getWidth();
					sY = cellBounds.getHeight();
				}
				Point2D instanceAnchor = new Point2D.Double(0, 0);
				prevTrans.transform(ni.getAnchorCenter(), instanceAnchor);

				String name = null;
				Name nameKey = ni.getNameKey();
				if (!nameKey.isTempname()) name = ni.getName();
				Orientation or = orient.concatenate(ni.getOrient());
				if (name != null && newCell.findNode(name) != null) name = null;
				NodeInst newNi = NodeInst.makeInstance(copyType, ep, instanceAnchor, sX, sY,
					newCell, or, name, ni.getTechSpecific());
				if (newNi == null)
				{
					addErrorLog(newCell, "Problem creating new instance of " + ni.getProto(), EPoint.fromLambda(sX, sY));
					continue;
				}
				newNodes.put(ni, newNi);

				// copy exports too
				for(Iterator<Export> it = ni.getExports(); it.hasNext(); )
				{
					Export e = it.next();
					PortInst pi = newNi.findPortInstFromEquivalentProto(e.getOriginalPort().getPortProto());
					Export.newInstance(newCell, pi, e.getName(), ep);
				}
				continue;
			}

			// see if the size is at an odd coordinate (and may suffer rounding problems)
			double alignX = 1, alignY = 1;
			if (alignment != null)
			{
				alignX = alignment.getWidth();
				alignY = alignment.getHeight();
			}
			boolean growABit = false;
			int growXSize = (int)Math.round(ni.getXSize() / alignX * DBMath.GRID);
			int growYSize = (int)Math.round(ni.getYSize() / alignY * DBMath.GRID);
			if ((growXSize % 2) != 0 || (growYSize % 2) != 0) growABit = true;

			// extract the geometry from the pure-layer node
			FixpTransform trans = ni.rotateOut(prevTrans);
			Poly [] polys = tech.getShapeOfNode(ni);

			for(int j=0; j<polys.length; j++)
			{
				Poly poly = polys[j];

				// get the layer for the geometry
				Layer layer = poly.getLayer();
				if (layer == null) continue;

				// make sure the geometric database is made up of proper layers
				layer = geometricLayer(layer);

				// finally add the geometry to the merge
				poly.transform(trans);
				Point2D [] points = poly.getPoints();
				if (alignment != null)
				{
					Point2D hold = new Point2D.Double();
					for(int i=0; i<points.length; i++)
					{
						hold.setLocation(points[i]);
                        DBMath.gridAlign(hold, alignment);
						poly.setPoint(i, hold.getX(), hold.getY());
					}
				} else
				{
					// grow the polygon to account for rounding problems
					if (growABit)
					{
						double growth = DBMath.getEpsilon()/2;
						Point2D polyCtr = poly.getCenter();
						for(int i=0; i<points.length; i++)
						{
							double x = points[i].getX();
							double y = points[i].getY();
							if (x < polyCtr.getX()) x -= growth; else x += growth;
							if (y < polyCtr.getY()) y -= growth; else y += growth;
							poly.setPoint(i, x, y);
						}
					}
				}

                // check overlap with selectMerge here, after poly has been rotated up to top level, but before scaling
                if (layer.getFunction() == Layer.Function.DIFFN) {
                    // make sure n-diffusion is in n-select
                    if (selectMerge.contains(pSelectLayer, poly)) {
                        // switch it p-active
                        layer = pActiveLayer;
                    }
                }
                if (layer.getFunction() == Layer.Function.DIFFP) {
                    // make sure p-diffusion is in p-select
                    if (selectMerge.contains(nSelectLayer, poly)) {
                        // switch it n-active
                        layer = nActiveLayer;
                    }
                }

				for(int i=0; i<points.length; i++)
					poly.setPoint(i, scaleUp(points[i].getX()), scaleUp(points[i].getY()));

				if (layer.getFunction().isContact())
				{
					// cut layers are stored in lists because merging them is too expensive and pointless
					CutInfo cInfo = allCutLayers.get(layer);
					if (cInfo == null) allCutLayers.put(layer, cInfo = new CutInfo());

					// see if the cut is already there
					boolean found = false;
					for(Iterator<CutBound> sea = new RTNode.Search<CutBound>(poly.getBounds2D(), cInfo.getRTree(), true); sea.hasNext(); )
					{
						CutBound cBound = sea.next();
						if (cBound.getBounds().equals(poly.getBounds2D())) { found = true;   break; }
					}
					if (!found)
					{
						cInfo.addCut(poly);
					}
				} else
				{
					Rectangle2D box = poly.getBox();
					if (box == null) merge.addPolygon(layer, poly); else
					{
						if (box.getWidth() > 0 && box.getHeight() > 0)
							merge.addRectangle(layer, box);
					}
				}
			}

			// save exports on pure-layer nodes for restoration later
			for(Iterator<Export> it = ni.getExports(); it.hasNext(); )
			{
				Export e = it.next();
				exportsToRestore.add(e);
			}
		}

		// throw all arcs into the new cell, too
		for(Iterator<ArcInst> aIt = oldCell.getArcs(); aIt.hasNext(); )
		{
			ArcInst ai = aIt.next();
			NodeInst end1 = newNodes.get(ai.getHeadPortInst().getNodeInst());
			NodeInst end2 = newNodes.get(ai.getTailPortInst().getNodeInst());
			if (end1 == null || end2 == null) continue;
			PortInst pi1 = end1.findPortInstFromEquivalentProto(ai.getHeadPortInst().getPortProto());
			PortInst pi2 = end2.findPortInstFromEquivalentProto(ai.getTailPortInst().getPortProto());
			Point2D headLocation = new Point2D.Double(0, 0);
			Point2D tailLocation = new Point2D.Double(0, 0);
			prevTrans.transform(ai.getHeadLocation(), headLocation);
			prevTrans.transform(ai.getTailLocation(), tailLocation);
			ArcInst.makeInstanceBase(ai.getProto(), ep, ai.getLambdaBaseWidth(), pi1, pi2,
				headLocation, tailLocation, ai.getName());
		}

		// throw all cell text into the new cell, too
		for(Iterator<Variable> vIt = oldCell.getParametersAndVariables(); vIt.hasNext(); )
		{
			Variable var = vIt.next();
            Variable newVar = Variable.newInstance(var.getKey(), var.getObject(), var.getTextDescriptor());
            if (var.getTextDescriptor().isParam())
            {
            	if (newCell.getCellGroup() != null)
            		newCell.getCellGroup().addParam(newVar);
            } else
            	newCell.addVar(newVar);
			new DisplayedText(newCell, var.getKey());
		}
	}

	/**
	 * Method to determine if this is a "p-well" or "n-well" process.
	 * Examines the top-level cell to see which well layers are found.
	 * @param cell the top-level Cell.
	 * @param recursive true to examine recursively.
	 * @param pats exclusion pattern for cell names.
	 * @param activeHandling
	 * 0: Insist on two different active layers (N and P) and also proper select/well surrounds (the default).
	 * 1: Ignore active distinctions and use select/well surrounds to distinguish N from P.
	 * 2: Insist on two different active layers (N and P) but ignore select/well surrounds.
	 */
	private void findMissingWells(Cell cell, boolean recursive, List<Pattern> pats, int activeHandling)
	{
		hasWell = hasPWell = hasNWell = false;
		haveNActive = havePActive = false;
		recurseMissingWells(cell, recursive, pats);
		if (!hasPWell)
		{
			pSubstrateProcess = true;
			System.out.println("Presuming a P-substrate process");
		} else if (!hasNWell && !hasWell)
		{
			nSubstrateProcess = true;
			System.out.println("Presuming an N-substrate process");
		}

		// see how active layers should be handled
		ignoreActiveSelectWell = (activeHandling == 2);
	}

	/**
	 * Method to recursively invoke "examineCellForMissingWells".
	 * @param cell the top-level Cell.
	 * @param recursive true to examine recursively.
	 * @param pats exclusion patterns for cell names.
	 */
	private void recurseMissingWells(Cell cell, boolean recursive, List<Pattern> pats)
	{
		examineCellForMissingWells(cell);
		if (recursive)
		{
			// now see if subcells need to be converted
			for(Iterator<NodeInst> it = cell.getNodes(); it.hasNext(); )
			{
				NodeInst ni = it.next();
				if (ni.isCellInstance())
				{
					Cell subCell = (Cell)ni.getProto();

					// do not recurse if this subcell will be expanded
					boolean matches = false;
					for(Pattern pat : pats)
					{
						Matcher mat = pat.matcher(subCell.noLibDescribe());
						if (mat.find()) { matches = true;   break; }
					}
					if (matches) continue;

					Cell convertedCell = convertedCells.get(subCell);
					if (convertedCell == null)
					{
						recurseMissingWells(subCell, recursive, pats);
					}
				}
			}
		}
	}

	/**
	 * Method to scan a cell for implant layers that would indicate a default N or P process.
	 * @param cell the Cell to examine.
	 * Sets the field variables "hasWell", "hasPWell", and "hasNWell".
	 */
	private void examineCellForMissingWells(Cell cell)
	{
		for(Iterator<NodeInst> it = cell.getNodes(); it.hasNext(); )
		{
			NodeInst ni = it.next();
			if (ni.isCellInstance()) continue;
			Poly[] polys = ni.getProto().getTechnology().getShapeOfNode(ni);
			for(int i=0; i<polys.length; i++)
			{
				Layer layer = polys[i].getLayer();
				if (layer == null) continue;
				if (!layer.getFunction().isDiff()) continue;
				if (layer.getFunction() == Layer.Function.DIFFN) haveNActive = true;
				if (layer.getFunction() == Layer.Function.DIFFP) havePActive = true;
			}
			if (haveNActive && havePActive) break;
		}
		for(Iterator<NodeInst> nIt = cell.getNodes(); nIt.hasNext(); )
		{
			NodeInst ni = nIt.next();
			if (ni.isCellInstance()) continue;
			if (Generic.isCellCenter(ni)) continue;
			PrimitiveNode np = (PrimitiveNode)ni.getProto();

			// see if the node will be extracted
			PrimitiveNode copyType = null;
			if (np.getFunction() != PrimitiveNode.Function.NODE) copyType = np; else
			{
				if (ignoreNodes.contains(np)) copyType = np;
			}

			// examine it if so
			if (copyType != null)
			{
				NodeLayer [] nLayers = copyType.getNodeLayers();
				for(int i=0; i<nLayers.length; i++)
				{
					NodeLayer nLay = nLayers[i];
					Layer layer = nLay.getLayer();
					Layer.Function fun = layer.getFunction();
					if (fun == Layer.Function.WELL) hasWell = true;
					if (fun == Layer.Function.WELLP) hasPWell = true;
					if (fun == Layer.Function.WELLN) hasNWell = true;
					if (layer.getFunction().isDiff())
					{
						if (layer.getFunction() == Layer.Function.DIFFN) haveNActive = true;
						if (layer.getFunction() == Layer.Function.DIFFP) havePActive = true;
						continue;
					}
				}
				continue;
			}
		}

		// examine all arcs
		for(Iterator<ArcInst> aIt = cell.getArcs(); aIt.hasNext(); )
		{
			ArcInst ai = aIt.next();
			for(int i=0; i<ai.getProto().getNumArcLayers(); i++)
			{
				Layer layer = ai.getProto().getLayer(i);
				Layer.Function fun = layer.getFunction();
				if (fun == Layer.Function.WELL) hasWell = true;
				if (fun == Layer.Function.WELLP) hasPWell = true;
				if (fun == Layer.Function.WELLN) hasNWell = true;
				if (layer.getFunction().isDiff())
				{
					if (layer.getFunction() == Layer.Function.DIFFN) haveNActive = true;
					if (layer.getFunction() == Layer.Function.DIFFP) havePActive = true;
					continue;
				}
			}
		}
	}

	/********************************************** WIRE EXTRACTION **********************************************/

	private boolean isOnGrid(double value, double grid)
	{
		if (grid == 0) return true;
		long x = Math.round(value / grid);
		return x * grid == value;
	}

	/**
	 * Method to extract wires from the merge.
	 * @param merge the merged geometry that remains (after contacts and transistors are extracted).
	 * @param originalMerge the original merge with all geometry.
	 * @param newCell the new Cell in which wires are placed.
	 * @return true on error.
	 */
	private boolean makeWires(PolyMerge merge, PolyMerge originalMerge, Cell newCell, boolean usePureLayerNodes)
	{
		// make a list of polygons that could be turned into wires
		int totPolys = 0;
		Map<Layer,List<PolyBase>> geomToWire = new TreeMap<Layer,List<PolyBase>>();
		for (Layer layer : merge.getKeySet())
		{
			Layer.Function fun = layer.getFunction();
			if (fun.isDiff() || fun.isPoly() || fun.isMetal())
			{
				List<PolyBase> polyList = getMergePolys(merge, layer, null);
				totPolys += polyList.size();
				geomToWire.put(layer, polyList);
			}
		}

		// examine each wire layer, looking for a skeletal structure that approximates it
		int soFar = 0;
		Set<Layer> allLayers = geomToWire.keySet();
		for (Layer layer : allLayers)
		{
			// examine the geometry on the layer
			List<PolyBase> polyList = geomToWire.get(layer);
			for(PolyBase poly : polyList)
			{
				if (!recursive) Job.getUserInterface().setProgressValue(soFar * 100 / totPolys);
				soFar++;

				// figure out which arcproto to use here
				ArcProto ap = findArcProtoForPoly(layer, poly, originalMerge);
				if (ap == null) continue;

				// reduce the geometry to a skeleton of centerlines
				double minWidth = 1;
				if (ENFORCEMINIMUMSIZE) minWidth = scaleUp(ap.getDefaultLambdaBaseWidth(ep));
				List<Centerline> lines = findCenterlines(poly, layer, minWidth, merge, originalMerge);

				// now realize the wires
				for(Centerline cl : lines)
				{
                    ap = findArcProtoForPoly(layer, poly, originalMerge);
                    Point2D loc1Unscaled = new Point2D.Double();
					Point2D loc2Unscaled = new Point2D.Double();
					PortInst pi1 = locatePortOnCenterline(cl, loc1Unscaled, layer, ap, true, newCell);
					Point2D loc1 = new Point2D.Double(scaleUp(loc1Unscaled.getX()), scaleUp(loc1Unscaled.getY()));
					PortInst pi2 = locatePortOnCenterline(cl, loc2Unscaled, layer, ap, false, newCell);
					Point2D loc2 = new Point2D.Double(scaleUp(loc2Unscaled.getX()), scaleUp(loc2Unscaled.getY()));

					// make sure the wire fits
					MutableBoolean headExtend = new MutableBoolean(true), tailExtend = new MutableBoolean(true);

					// adjust extension to get alignment right
					if (alignment != null)
					{
						if (loc1.getX() == loc2.getX())
						{
							// vertical arc: adjust extension to make sure top and bottom are on grid
							double loc1Y = loc1Unscaled.getY();
							double loc2Y = loc2Unscaled.getY();
							double halfWidth = cl.width/2/SCALEFACTOR;
							double loc1YExtend = loc1Y + (loc1Y < loc2Y ? -halfWidth : halfWidth);
							double loc2YExtend = loc2Y + (loc2Y < loc1Y ? -halfWidth : halfWidth);
							if (!isOnGrid(loc1YExtend, alignment.getHeight()) && isOnGrid(loc1Y, alignment.getHeight()))
								headExtend.setValue(false);
							if (!isOnGrid(loc2YExtend, alignment.getHeight()) && isOnGrid(loc2Y, alignment.getHeight()))
								tailExtend.setValue(false);
						} else if (loc1.getY() == loc2.getY())
						{
							// horizontal arc: adjust extension to make sure left and right are on grid
							double loc1X = loc1Unscaled.getX();
							double loc2X = loc2Unscaled.getX();
							double halfWidth = cl.width/2/SCALEFACTOR;
							double loc1XExtend = loc1X + (loc1X < loc2X ? -halfWidth : halfWidth);
							double loc2XExtend = loc2X + (loc2X < loc1X ? -halfWidth : halfWidth);
							if (!isOnGrid(loc1XExtend, alignment.getWidth()) && isOnGrid(loc1X, alignment.getWidth()))
								headExtend.setValue(false);
							if (!isOnGrid(loc2XExtend, alignment.getWidth()) && isOnGrid(loc2X, alignment.getWidth()))
								tailExtend.setValue(false);
						}
					}

					boolean fits = originalMerge.arcPolyFits(layer, loc1, loc2, cl.width, headExtend, tailExtend);
					if (DEBUGCENTERLINES) System.out.println("FIT="+fits+" "+cl);
					if (!fits && alignment != null)
					{
						// arc does not fit, try reducing width
						double wid = cl.width / SCALEFACTOR;
						long x = Math.round(wid / alignment.getWidth());
						double gridWid = x * alignment.getWidth();
						if (gridWid < wid)
						{
							// grid-aligning the width results in a smaller value...try it
							cl.width = scaleUp(gridWid);
							fits = originalMerge.arcPolyFits(layer, loc1, loc2, cl.width, headExtend, tailExtend);
							if (DEBUGCENTERLINES) System.out.println("   WID="+(cl.width/SCALEFACTOR)+" FIT="+fits);
						} else
						{
							// see if width can be reduced by a small amount and still fit
							cl.width--;
							fits = originalMerge.arcPolyFits(layer, loc1, loc2, cl.width, headExtend, tailExtend);
							if (DEBUGCENTERLINES) System.out.println("   WID="+(cl.width/SCALEFACTOR)+" FIT="+fits);
						}
					}
					while (!fits)
					{
						double wid = cl.width - SCALEFACTOR;
						if (wid < 0) break;
						cl.width = wid;
						fits = originalMerge.arcPolyFits(layer, loc1, loc2, cl.width, headExtend, tailExtend);
						if (DEBUGCENTERLINES) System.out.println("   WID="+(cl.width/SCALEFACTOR)+" FIT="+fits);
					}
					if (!fits || (loc1Unscaled.distance(loc2Unscaled) == 0 && !headExtend.booleanValue() && !tailExtend.booleanValue()))
					{
						cl.width = 0;
						ap = Generic.tech().universal_arc;
					}
                    if (loc1Unscaled.distance(loc2Unscaled) == 0 && !headExtend.booleanValue() && !tailExtend.booleanValue()) {
                        //System.out.println("zero length arc in make wires");
                    }

                    // create the wire
                    double width = cl.width / SCALEFACTOR;
					ArcInst ai = realizeArc(ap, pi1, pi2, loc1Unscaled, loc2Unscaled, width,
						!headExtend.booleanValue(), !tailExtend.booleanValue(), usePureLayerNodes, merge);
					if (ai == null)
					{
						String msg = "Cell " + newCell.describe(false) + ": Failed to run arc " + ap.getName() +
							" from (" + loc1Unscaled.getX() + "," +
							loc1Unscaled.getY() + ") on node " + pi1.getNodeInst().describe(false) + " to (" +
							loc2Unscaled.getX() + "," + loc2Unscaled.getY() + ") on node " + pi2.getNodeInst().describe(false);
						addErrorLog(newCell, msg, EPoint.fromLambda(loc1Unscaled.getX(), loc1Unscaled.getY()),
								EPoint.fromLambda(loc2Unscaled.getX(), loc2Unscaled.getY()));
					}
				}
			}
		}

        // add in pure layer node for remaining geometrics
        for (Layer layer : allLayers)
        {
            List<PolyBase> polyList = getMergePolys(merge, layer, null);
            for(PolyBase poly : polyList)
            {
                ArcProto ap = findArcProtoForPoly(layer, poly, originalMerge);
                if (ap == null) continue;
                PrimitiveNode pin = ap.findPinProto();

                List<NodeInst> niList = makePureLayerNodeFromPoly(poly, newCell, merge);
                merge.subtract(layer, poly);
                // connect up to enclosed pins
                for (NodeInst ni : niList)
                {
                    PortInst fPi = ni.getOnlyPortInst();
                    Rectangle2D polyBounds = ni.getBounds();
                    Rectangle2D searchBound = new Rectangle2D.Double(polyBounds.getMinX(), polyBounds.getMinY(),
                        polyBounds.getWidth(), polyBounds.getHeight());
                    for(Iterator<Geometric> it = newCell.searchIterator(searchBound); it.hasNext(); )
                    {
                        Geometric geom = it.next();
                        if (!(geom instanceof NodeInst)) continue;
                        NodeInst oNi = (NodeInst)geom;
                        if (oNi == ni) continue;
                        if (oNi.getProto() != pin) continue;

                        // make sure center of pin is in bounds
                        if (!DBMath.pointInsideRect(oNi.getAnchorCenter(), searchBound)) continue;

                        // replace arcs that end on pin to end on pure layer node
                        for (Iterator<Connection> cit = oNi.getConnections(); cit.hasNext(); ) {
                            Connection conn = cit.next();
                            Connection oConn;
                            ArcInst ai = conn.getArc();
                            if (ai.getProto() == Generic.tech().universal_arc) continue;
                            ArcInst newAi;
                            if (conn instanceof HeadConnection) {
                                oConn = ai.getTail();
                                newAi = ArcInst.makeInstanceBase(ap, ep, ai.getLambdaBaseWidth(), fPi, oConn.getPortInst(),
                                    conn.getLocation(), oConn.getLocation(), null);
                            } else {
                                oConn = ai.getHead();
                                newAi = ArcInst.makeInstanceBase(ap, ep, ai.getLambdaBaseWidth(), oConn.getPortInst(), fPi,
                                    oConn.getLocation(), conn.getLocation(), null);
                            }
                            if (newAi != null) {
                                newAi.setHeadExtended(ai.isHeadExtended());
                                newAi.setTailExtended(ai.isTailExtended());
                                if (newAi.getLambdaLength() == 0)
                                    System.out.println("arc inst of zero length connecting pure layer nodes");
                                ai.kill();
                            } else {
                                String msg = "Cell " + newCell.describe(false) + ": Failed to replace arc " + ai.describe(false) +
                                    " from (" + conn.getLocation().getX() + "," +
                                    conn.getLocation().getY() + ") on node " + ni.describe(false) + " to (" +
                                    oConn.getLocation().getX() + "," + oConn.getLocation().getY() + ")";
                                addErrorLog(newCell, msg, EPoint.fromLambda(conn.getLocation().getX(), conn.getLocation().getY()),
                                	EPoint.fromLambda(oConn.getLocation().getX(), oConn.getLocation().getY()));
                            }
                        }
                    }
                }
            }
        }
		return false;
	}

    /**
	 * Method to figure out which ArcProto to use for a polygon on a layer.
	 * In the case of Active layers, it examines the well and select layers to figure out
	 * which active arc to use.
	 * @param layer the layer of the polygon.
	 * @param poly the polygon
	 * @param originalMerge the merged data for the cell
	 * @return the ArcProto to use (null if none can be found).
	 */
	private ArcProto findArcProtoForPoly(Layer layer, PolyBase poly, PolyMerge originalMerge)
	{
		if (layer.getFunctionExtras() == Layer.Function.INTERCONNECT) return null;
        Layer.Function fun = layer.getFunction();
        if (fun.isPoly() || fun.isMetal()) return arcsForLayer.get(layer);
        if (!fun.isDiff()) return null;

        ArrayList<Layer> requiredLayers = new ArrayList<Layer>();
        ArrayList<Layer> requiredAbsentLayers = new ArrayList<Layer>();

        // must further differentiate the active arcs...find implants
        Layer wellP = null, wellN = null;
        for(Layer l : originalMerge.getKeySet())
        {
            if (l.getFunction() == Layer.Function.WELLP) wellP = l;
            if (l.getFunction() == Layer.Function.WELLN) wellN = l;
        }

        // Active must have P-Select or N-Select
        if (pSelectLayer != null && originalMerge.intersects(pSelectLayer, poly)) {
            if (unifyActive)
                requiredLayers.add(activeLayer);
            else
                requiredLayers.add(realPActiveLayer);
            requiredLayers.add(pSelectLayer);
        }
        if (nSelectLayer != null && originalMerge.intersects(nSelectLayer, poly)) {
            if (unifyActive)
                requiredLayers.add(activeLayer);
            else
                requiredLayers.add(realNActiveLayer);
            requiredLayers.add(nSelectLayer);
        }

        // Active could either be an Active arc or a Well arc, depending on well type
        if (wellN == null || !originalMerge.intersects(wellN, poly))
            requiredAbsentLayers.add(wellN);
        if (wellN != null && originalMerge.intersects(wellN, poly))
            requiredLayers.add(wellN);

        // Active could either be an Active arc or a Well arc, depending on well type
        if (wellP == null || !originalMerge.intersects(wellP, poly))
            requiredAbsentLayers.add(wellP);
        if (wellP != null && originalMerge.intersects(wellP, poly))
            requiredLayers.add(wellP);

        // now find the arc with the desired function
        for(Iterator<ArcProto> aIt = tech.getArcs(); aIt.hasNext(); )
        {
            ArcProto ap = aIt.next();
            List<Layer> apLayers = new ArrayList<Layer>();
            for (Iterator<Layer> layit = ap.getLayerIterator(); layit.hasNext(); )
                apLayers.add(layit.next());
            // make sure required layers exist
            boolean failed = false;
            for (Layer l : requiredLayers) {
                if (!apLayers.contains(l)) { failed = true; break; }
            }
            if (failed) continue;
            for (Layer l : requiredAbsentLayers) {
                if (apLayers.contains(l)) { failed = true; break; }
            }
            if (failed) continue;
            return ap;
        }
        return null;
	}

//	/**
//	 * Method to locate a port on a node at a specific point with a specific connectivity.
//	 * @param pt the center location of the desired node.
//	 * @param ap the type of the arc that must connect.
//	 * @param size the size of the node (if it must be created).
//	 * @param newCell the cell in which to locate or place the node.
//	 * @return the port on the node that is at the proper point.
//	 * If there is none there, a node is created.
//	 */
//	private PortInst wantConnectingNodeAt(Point2D pt, ArcProto ap, double size, Cell newCell)
//	{
//		Rectangle2D bounds = new Rectangle2D.Double(pt.getX(), pt.getY(), 0, 0);
//		for(Iterator<Geometric> it = newCell.searchIterator(bounds); it.hasNext(); )
//		{
//			Geometric geom = it.next();
//			if (!(geom instanceof NodeInst)) continue;
//			NodeInst ni = (NodeInst)geom;
//			for(Iterator<PortInst> pIt = ni.getPortInsts(); pIt.hasNext(); )
//			{
//				PortInst pi = pIt.next();
//				PortProto pp = pi.getPortProto();
//				if (!pp.connectsTo(ap)) continue;
//				Poly poly = pi.getPoly();
//				if (poly.contains(pt)) return pi;
//			}
//		}
//		NodeInst ni = createNode(ap.findPinProto(), pt, size, size, null, newCell);
//		return ni.getOnlyPortInst();
//	}

	/**
	 * Method to locate a node at a specific point with a specific type.
	 * @param pt the center location of the desired node.
	 * @param pin the type of the desired node.
	 * @param size the size of the node (if it must be created).
	 * @param newCell the cell in which to locate or place the node.
	 * @return a node of that type at that location.
	 * If there is none there, it is created.
	 */
	private NodeInst wantNodeAt(Point2D pt, NodeProto pin, double size, Cell newCell)
	{
		Rectangle2D bounds = new Rectangle2D.Double(pt.getX(), pt.getY(), 0, 0);
		for(Iterator<Geometric> it = newCell.searchIterator(bounds); it.hasNext(); )
		{
			Geometric geom = it.next();
			if (!(geom instanceof NodeInst)) continue;
			NodeInst ni = (NodeInst)geom;
			if (ni.getProto() != pin) continue;
			if (ni.getAnchorCenter().equals(pt)) return ni;
		}
		NodeInst ni = createNode(pin, pt, size, size, null, newCell);
		return ni;
	}

	/**
	 * Method to find the PortInst on a NodeInst that connects to a given PortProto and is closest to a given point.
	 * Because some primitive nodes (transistors) may have multiple ports that connect to each other
	 * (the poly ports) and because the system returns only one of those ports when describing the topology of
	 * a piece of geometry, it is necessary to find the closest port.
	 * @param ni the primitive NodeInst being examined.
	 * @param pp the primitive port on that node which defines the connection to the node.
	 * @param pt a point close to the desired port.
	 * @return the PortInst on the node that is electrically connected to the given primitive port, and closest to the point.
	 */
	private PortInst findPortInstClosestToPoly(NodeInst ni, PrimitivePort pp, Point2D pt)
	{
		PortInst touchingPi = ni.findPortInstFromEquivalentProto(pp);
		PrimitiveNode pnp = (PrimitiveNode)ni.getProto();
		Poly touchingPoly = touchingPi.getPoly();
		double bestDist = pt.distance(new Point2D.Double(touchingPoly.getCenterX(), touchingPoly.getCenterY()));
		for(Iterator<PortProto> pIt = pnp.getPorts(); pIt.hasNext(); )
		{
			PrimitivePort prP = (PrimitivePort)pIt.next();
			if (prP.getTopology() == pp.getTopology())
			{
				PortInst testPi = ni.findPortInstFromEquivalentProto(prP);
				Poly testPoly = testPi.getPoly();
				double dist = pt.distance(new Point2D.Double(testPoly.getCenterX(), testPoly.getCenterY()));
				if (dist < bestDist)
				{
					bestDist = dist;
					touchingPi = testPi;
				}
			}
		}
		return touchingPi;
	}

	private static class Centerline
	{
		Point2D start, end;
		EPoint startUnscaled, endUnscaled;
		boolean startHub, endHub;
		double width;
		boolean handled;
		int angle;

		Centerline(double width, Point2D start, Point2D end)
		{
			this.width = width;
			this.start = start;
			this.startUnscaled = EPoint.fromLambda(start.getX() / SCALEFACTOR, start.getY() / SCALEFACTOR);
			this.endUnscaled = EPoint.fromLambda(end.getX() / SCALEFACTOR, end.getY() / SCALEFACTOR);
			this.end = end;
			startHub = endHub = false;
			if (start.equals(end)) angle = -1; else
				angle = GenMath.figureAngle(end, start);
		}

		void setStart(double x, double y)
		{
			start.setLocation(x, y);
			startUnscaled = EPoint.fromLambda(x / SCALEFACTOR, y / SCALEFACTOR);
		}

		void setEnd(double x, double y)
		{
			end.setLocation(x, y);
			endUnscaled = EPoint.fromLambda(x / SCALEFACTOR, y / SCALEFACTOR);
		}

        Rectangle2D getBounds() {
            if (start.getX() == end.getX()) {
                // vertical
                double minX = (start.getX() < end.getX() ? start.getX() : end.getX()) - width/2.0;
                double minY = start.getY() < end.getY() ? start.getY() : end.getY();
                double maxY = start.getY() > end.getY() ? start.getY() : end.getY();
                return new Rectangle2D.Double(minX, minY, width, maxY-minY);
            }
            if (start.getY() == end.getY()) {
                // horizontal
                double minY = (start.getY() < end.getY() ? start.getY() : end.getY()) - width/2.0;
                double minX = start.getX() < end.getX() ? start.getX() : end.getX();
                double maxX = start.getX() > end.getX() ? start.getX() : end.getX();
                return new Rectangle2D.Double(minX, minY, maxX-minX, width);
            }
            return null; // non-Manhattan
        }

        public String toString()
		{
			return "CENTERLINE from (" + TextUtils.formatDouble(start.getX()/SCALEFACTOR) + "," +
				TextUtils.formatDouble(start.getY()/SCALEFACTOR) + ") to (" +
				TextUtils.formatDouble(end.getX()/SCALEFACTOR) + "," +
				TextUtils.formatDouble(end.getY()/SCALEFACTOR) + ") wid=" +
				TextUtils.formatDouble(width/SCALEFACTOR) + ", len=" +
				TextUtils.formatDouble((start.distance(end)/SCALEFACTOR));
		}
	}

	/**
	 * Method to return the port and location to use for one end of a Centerline.
	 * @param cl the Centerline to connect
	 * @param loc1 it's location (values returned through this object!)
	 * @param layer the layer associated with the Centerline.
	 * @param ap the type of arc to create.
	 * @param startSide true to consider the "start" end of the Centerline, false for the "end" end.
	 * @param newCell the Cell in which to find ports.
	 * @return the PortInst on the Centerline.
	 */
	private PortInst locatePortOnCenterline(Centerline cl, Point2D loc1, Layer layer,
		ArcProto ap, boolean startSide, Cell newCell)
	{
		PortInst piRet = null;
		boolean isHub = cl.endHub;
		EPoint startPoint = cl.endUnscaled;
		if (startSide)
		{
			isHub = cl.startHub;
			startPoint = cl.startUnscaled;
		}
		if (!isHub)
		{
			List<PortInst> possiblePorts = findPortInstsTouchingPoint(startPoint, layer, newCell, ap);
			for(PortInst pi : possiblePorts)
			{
				Poly portPoly = pi.getPoly();
				Point2D [] points = portPoly.getPoints();
				if (points.length == 1)
				{
					Point2D iPt = GenMath.intersect(cl.startUnscaled, cl.angle, points[0], (cl.angle+900)%3600);
					if (iPt != null)
					{
						loc1.setLocation(iPt.getX(), iPt.getY());
						piRet = pi;
						break;
					}
				} else
				{
					if (portPoly.contains(startPoint))
					{
						loc1.setLocation(startPoint);
						piRet = pi;
						break;
					}
					for(int i=0; i<points.length; i++)
					{
						int last = i-1;
						if (last < 0) last = points.length-1;
						Point2D portLineFrom = points[last];
						Point2D portLineTo = points[i];
						Point2D interPt = null;
						if (portLineFrom.equals(portLineTo))
						{
							interPt = GenMath.intersect(cl.startUnscaled, cl.angle, portLineFrom, (cl.angle+900)%3600);
						} else
						{
							int angPortLine = GenMath.figureAngle(portLineFrom, portLineTo);
							interPt = GenMath.intersect(portLineFrom, angPortLine, cl.startUnscaled, cl.angle);
							if (interPt != null)
							{
								if (interPt.getX() < Math.min(portLineFrom.getX(), portLineTo.getX()) ||
									interPt.getX() > Math.max(portLineFrom.getX(), portLineTo.getX()) ||
									interPt.getY() < Math.min(portLineFrom.getY(), portLineTo.getY()) ||
									interPt.getY() > Math.max(portLineFrom.getY(), portLineTo.getY())) interPt = null;
							}
						}
						if (interPt == null) continue;
						loc1.setLocation(interPt.getX(), interPt.getY());
						if (!portPoly.contains(loc1)) continue;
						piRet = pi;
						break;
					}
					if (piRet != null) break;
				}
			}
		}
		if (piRet == null)
		{
			// shrink the end inward by half-width
			PrimitiveNode pin = ap.findPinProto();
			int ang = GenMath.figureAngle(cl.start, cl.end);
			double xOff = GenMath.cos(ang) * cl.width/2;
			double yOff = GenMath.sin(ang) * cl.width/2;
//			double aliX = 1, aliY = 1;
//			if (alignment != null)
//			{
//				if (alignment.getWidth() > 0) aliX = scaleUp(alignment.getWidth());
//				if (alignment.getHeight() > 0) aliY = scaleUp(alignment.getHeight());
//			}
			if (startSide)
			{
				if (!isHub && cl.start.distance(cl.end) > cl.width)
				{
					//xOff = Math.floor(xOff / aliX) * aliX;
					//yOff = Math.floor(yOff / aliY) * aliY;
                    // if shortening to allow ends extend will put the arc off-grid, do not do it
					if (alignment != null)
					{
	                    if (xOff > 0 && (xOff % scaleUp(alignment.getWidth())) != 0) xOff = 0;
	                    if (yOff > 0 && (yOff % scaleUp(alignment.getHeight())) != 0) yOff = 0;
					}
                    cl.setStart(cl.start.getX() + xOff, cl.start.getY() + yOff);
				}
                double size = pin.getFactoryDefaultBaseDimension().getLambdaWidth();
                NodeInst ni = wantNodeAt(cl.startUnscaled, pin, size, newCell);
				loc1.setLocation(cl.startUnscaled.getX(), cl.startUnscaled.getY());
				piRet = ni.getOnlyPortInst();
			} else
			{
				if (!isHub && cl.start.distance(cl.end) > cl.width)
				{
					//xOff = Math.ceil(xOff / aliX) * aliX;
					//yOff = Math.ceil(yOff / aliY) * aliY;
                    // if shortening to allow ends extend will put the arc off-grid, do not do it
					if (alignment != null)
					{
	                    if (xOff > 0 && (xOff % scaleUp(alignment.getWidth())) != 0) xOff = 0;
	                    if (yOff > 0 && (yOff % scaleUp(alignment.getHeight())) != 0) yOff = 0;
					}
					cl.setEnd(cl.end.getX() - xOff, cl.end.getY() - yOff);
				}
				double width = cl.width / SCALEFACTOR;
				double length = cl.start.distance(cl.end) / SCALEFACTOR;
				if (length < width) width = length;
				NodeInst ni = wantNodeAt(cl.endUnscaled, pin, width, newCell);
				loc1.setLocation(cl.endUnscaled.getX(), cl.endUnscaled.getY());
				piRet = ni.getOnlyPortInst();
			}
		}
		return piRet;
	}

	private List<PortInst> findPortInstsTouchingPoint(Point2D pt, Layer layer, Cell newCell, ArcProto ap)
	{
		List<PortInst> touchingNodes = new ArrayList<PortInst>();
		boolean mightCreateExports = false;
		Rectangle2D checkBounds = new Rectangle2D.Double(pt.getX(), pt.getY(), 0, 0);
		for(Iterator<Geometric> it = newCell.searchIterator(checkBounds); it.hasNext(); )
		{
			Geometric geom = it.next();
			if (!(geom instanceof NodeInst)) continue;
			NodeInst ni = (NodeInst)geom;
			if (ni.isCellInstance())
			{
				boolean found = false;
				for(Iterator<PortInst> pIt = ni.getPortInsts(); pIt.hasNext(); )
				{
					PortInst pi = pIt.next();
					Poly portPoly = pi.getPoly();
					if (portPoly.contains(pt))
					{
						touchingNodes.add(pi);
						found = true;
						break;
					}
				}
				if (found) continue;

				// remember that a cell was found...might have to create exports on it
				mightCreateExports = true;
				continue;
			}

			// for pins, must be centered over the desired point
			if (ni.getFunction().isPin())
			{
				if (!ni.getOnlyPortInst().getPortProto().connectsTo(ap)) continue;
				if (ni.getAnchorCenter().equals(pt))
				{
					touchingNodes.add(ni.getOnlyPortInst());
				}
			} else
			{
				// non-pins can have any touching and connecting layer
				Poly [] polys = tech.getShapeOfNode(ni, true, true, null);
				FixpTransform trans = ni.rotateOut();
				for(int i=0; i<polys.length; i++)
				{
					Poly oPoly = polys[i];
					Layer oLayer = geometricLayer(oPoly.getLayer());
					if (layer != oLayer) continue;
					oPoly.transform(trans);

					// do the polys touch?
					if (oPoly.contains(pt))
					{
						PortInst touchingPi = findPortInstClosestToPoly(ni, (PrimitivePort)oPoly.getPort(), pt);
						if (touchingPi == null)
						{
							addErrorLog(newCell, "Can't find port for "+ni+" and "+oPoly.getPort(),
									EPoint.fromLambda(pt.getX(), pt.getY()));
							continue;
						}
						touchingNodes.add(touchingPi);
						break;
					}
				}
			}
		}

		// if no ports were found but there were cells, should create new exports in the cells
		if (touchingNodes.size() == 0 && mightCreateExports)
		{
			// can we create an export on the cell?
			PortInst pi = makePort(newCell, layer, pt);
			if (pi != null)
				touchingNodes.add(pi);
		}
		return touchingNodes;
	}

	/**
	 * Method to connect to a given location and layer.
	 * @param cell the cell in which this location/layer exists.
	 * @param layer the layer on which to connect.
	 * @param pt the location in the Cell for the connection.
	 * @return a PortInst on a node in the Cell (null if it cannot be found).
	 */
	private PortInst makePort(Cell cell, Layer layer, Point2D pt)
	{
		Rectangle2D checkBounds = new Rectangle2D.Double(pt.getX(), pt.getY(), 0, 0);
        // first look for port on primitive geometry in cell
        for (Iterator<Geometric> it = cell.searchIterator(checkBounds); it.hasNext();)
        {
            Geometric geom = it.next();
            if (!(geom instanceof NodeInst)) continue;
            NodeInst subNi = (NodeInst)geom;
            if (subNi.isCellInstance()) continue;

            Technology tech = subNi.getProto().getTechnology();
            FixpTransform trans = subNi.rotateOut();
            Poly [] polyList = tech.getShapeOfNode(subNi, true, true, null);
            for(int i=0; i<polyList.length; i++)
            {
                Poly poly = polyList[i];
                if (poly.getPort() == null) continue;
                if (geometricLayer(poly.getLayer()) != layer) continue;
                poly.transform(trans);
                if (poly.contains(pt))
                {
                    // found polygon that touches the point.  Make the export
                    PortInst foundPi = findPortInstClosestToPoly(subNi, (PrimitivePort)poly.getPort(), pt);
                    if (foundPi != null) return foundPi;
                }
            }
        }
        // nothing found, now push down into subcells
        for(Iterator<Geometric> it = cell.searchIterator(checkBounds); it.hasNext(); )
		{
			Geometric geom = it.next();
			if (!(geom instanceof NodeInst)) continue;
			NodeInst subNi = (NodeInst)geom;
			PortInst foundPi = null;
			if (subNi.isCellInstance())
			{
				FixpTransform transIn = subNi.rotateIn(subNi.translateIn());
				Point2D inside = new Point2D.Double();
				transIn.transform(pt, inside);
				Cell subCell = (Cell)subNi.getProto();
				PortInst pi = makePort(subCell, layer, inside);
				if (pi != null)
				{
					// already exported?
					for(Iterator<Export> eIt = pi.getNodeInst().getExports(); eIt.hasNext(); )
					{
						Export e = eIt.next();
						if (e.getOriginalPort() == pi)
						{
							foundPi = subNi.findPortInstFromEquivalentProto(e);
							return foundPi;
						}
					}

					// if not already exported, make the export now
					if (foundPi == null)
					{
						Netlist nl = subCell.getNetlist();
						Network net = nl.getNetwork(pi);
						String exportName = null;
						for(Iterator<String> nIt = net.getExportedNames(); nIt.hasNext(); )
						{
							String eName = nIt.next();
							if (eName.startsWith("E"))
							{
								boolean isFake = false;
								for(Export e : generatedExports)
								{
									if (e.getParent() == subCell && e.getName().equals(eName)) { isFake = true;   break; }
								}
								if (isFake) continue;
							}
							if (exportName == null || exportName.length() < eName.length()) exportName = eName;
						}
						boolean genFakeName = (exportName == null);
						if (genFakeName) exportName = "E";
						exportName = ElectricObject.uniqueObjectName(exportName, subCell, Export.class, true, true);
						Export e = Export.newInstance(subCell, pi, exportName, ep);
						if (genFakeName)
                            generatedExports.add(e);
						foundPi = subNi.findPortInstFromEquivalentProto(e);
						return foundPi;
					}
				}
			}
		}
		return null;
	}

	/********************************************** VIA/CONTACT EXTRACTION **********************************************/

	private static class PossibleVia
	{
		PrimitiveNode pNp;
		int rotation;
		double minWidth, minHeight;
		double multicutSep1D, multicutSep2D, multicutSizeX, multicutSizeY;
		Layer [] layers;
		double [] shrinkL, shrinkR, shrinkT, shrinkB;
		double cutSizeX, cutSizeY;

		PossibleVia(PrimitiveNode pNp, int numLayers, double sizeX, double sizeY)
		{
			this.pNp = pNp;
			rotation = 0;
			layers = new Layer[numLayers];
			shrinkL = new double[numLayers];
			shrinkR = new double[numLayers];
			shrinkT = new double[numLayers];
			shrinkB = new double[numLayers];
			cutSizeX = sizeX;
			cutSizeY = sizeY;
		}
	}

	/**
	 * Method to scan the geometric information for possible contacts and vias.
	 * Any vias found are created in the new cell and removed from the geometric information.
	 * @param merge the current geometry being extracted.
	 * @param originalMerge the original geometry.
	 * @param newCell the Cell where new geometry is being created.
	 * @return false if the job was aborted.
	 */
	private boolean extractVias(PolyMerge merge, PolyMerge originalMerge, Cell oldCell, Cell newCell, boolean usePureLayerNodes)
	{
		// make a list of all via/cut layers in the technology and count the number of vias/cuts
		int totalCutLayers = 0;
		List<Layer> cutLayers = new ArrayList<Layer>();
		for (Layer layer : allCutLayers.keySet())
		{
			cutLayers.add(layer);
			CutInfo cInfo = allCutLayers.get(layer);
			totalCutLayers += cInfo.getNumCuts();
		}

		// initialize list of nodes that have been created
		List<NodeInst> contactNodes = new ArrayList<NodeInst>();

		// examine all vias/cuts for possible contacts
		int soFar = 0;
		for (Layer layer : cutLayers)
		{
			// compute the possible via nodes that this layer could become
			List<PossibleVia> possibleVias = findPossibleVias(layer);
//System.out.print("FOR LAYER "+layer.getName()+", POSSIBLE VIAS ARE:");
//for(PossibleVia pv : possibleVias) System.out.print(" "+pv.pNp.describe(false));
//System.out.println();
			// make a list of all necessary layers
			Set<Layer> layersToExamine = new TreeSet<Layer>();
			for(PossibleVia pv : possibleVias)
			{
				for(int i=0; i<pv.layers.length; i++)
					layersToExamine.add(pv.layers[i]);
			}

			// get all of the geometry on the cut/via layer
			CutInfo cInfo = allCutLayers.get(layer);
			cInfo.sortCuts();

			// the new way to extract vias
			List<PolyBase> cutsNotExtracted = new ArrayList<PolyBase>();
			while (cInfo.getNumCuts() > 0)
			{
				PolyBase cut = cInfo.getFirstCut();
				soFar++;
				if (!recursive && (soFar % 100) == 0) Job.getUserInterface().setProgressValue(soFar * 100 / totalCutLayers);

				// figure out which of the layers is present at this cut point
				Rectangle2D cutBox = cut.getBox();
				if (cutBox == null)
				{
					cutBox = cut.getBounds2D();
					double centerX = cutBox.getCenterX()/SCALEFACTOR;
					double centerY = cutBox.getCenterY()/SCALEFACTOR;
					String msg = "Cannot extract nonManhattan contact cut at (" + TextUtils.formatDistance(centerX) +
						"," + TextUtils.formatDistance(centerY) + ")";
					addErrorLog(newCell, msg, EPoint.fromLambda(centerX, centerY));
					cInfo.removeCut(cut);
					cutsNotExtracted.add(cut);
					continue;
				}
				Point2D ctr = new Point2D.Double(cutBox.getCenterX(), cutBox.getCenterY());
				Set<Layer> layersPresent = new TreeSet<Layer>();
				for(Layer l : layersToExamine)
				{
					boolean layerAtPoint = originalMerge.contains(l, ctr);
					if (layerAtPoint) layersPresent.add(geometricLayer(l));
				}
				boolean ignorePWell = false, ignoreNWell = false;
				if (pSubstrateProcess)
				{
					// P-substrate process (P-well is presumed where there is no N-Well)
					boolean foundNWell = false;
					for(Layer l : layersPresent)
					{
						if (l.getFunction() == Layer.Function.WELLN) { foundNWell = true;   break; }
					}
					if (!foundNWell) ignorePWell = true;
				}
				if (nSubstrateProcess)
				{
					// N-Substrate process (N-well is presumed where there is no P-Well)
					boolean foundPWell = false;
					for(Layer l : layersPresent)
					{
						if (l.getFunction() == Layer.Function.WELLP) { foundPWell = true;   break; }
					}
					if (!foundPWell) ignoreNWell = true;
				}

				boolean foundCut = false;
				Map<PossibleVia,String> reasons = new HashMap<PossibleVia,String>();
				for(PossibleVia pv : possibleVias)
				{

					// quick test to see if this via could possibly exist
					List<Layer> missingLayers = null;
					for(int i=0; i<pv.layers.length; i++)
					{
						if (!layersPresent.contains(pv.layers[i]))
						{
							if (ignorePWell && pv.layers[i].getFunction() == Layer.Function.WELLP) continue;
							if (ignoreNWell && pv.layers[i].getFunction() == Layer.Function.WELLN) continue;
							if (missingLayers == null) missingLayers = new ArrayList<Layer>();
							missingLayers.add(pv.layers[i]);
							break;
						}
					}
					if (missingLayers != null)
					{
						String reason = "layers are missing:";
						for(Layer l : missingLayers) reason += " " + l.getName();
						reasons.put(pv, reason);
						continue;
					}
					if (DEBUGCONTACTS) System.out.println("CONSIDERING "+pv.pNp.describe(false)+" ROTATED "+pv.rotation+" AT ("+
						TextUtils.formatDouble(cutBox.getCenterX()/SCALEFACTOR)+","+
						TextUtils.formatDouble(cutBox.getCenterY()/SCALEFACTOR)+")...");

					// see if the cut is the right size
					boolean rightSize = false;
					Rectangle2D cutBounds = cutBox.getBounds2D();
					double thisCutSizeX = cutBounds.getWidth() / SCALEFACTOR;
					double thisCutSizeY = cutBounds.getHeight() / SCALEFACTOR;
					if (thisCutSizeX == pv.cutSizeX && thisCutSizeY == pv.cutSizeY) rightSize = true;
					if (thisCutSizeX == pv.cutSizeY && thisCutSizeY == pv.cutSizeX) rightSize = true;
					if (!rightSize)
					{
						String reason = "cut size is " + TextUtils.formatDistance(thisCutSizeX) + "x" + TextUtils.formatDistance(thisCutSizeY) +
							" but wants " + TextUtils.formatDistance(pv.cutSizeX) + "x" + TextUtils.formatDistance(pv.cutSizeY);
						reasons.put(pv, reason);
						continue;
					}

					// see if this is an active/poly layer (in which case, there can be no poly/active in the area)
					boolean activeCut = false;
					boolean polyCut = false;
					NodeLayer [] primLayers = pv.pNp.getNodeLayers();
					for(int i=0; i<primLayers.length; i++)
					{
						if (primLayers[i].getLayer().getFunction().isDiff()) activeCut = true;
						if (primLayers[i].getLayer().getFunction().isPoly()) polyCut = true;
					}
					if (activeCut && polyCut) activeCut = polyCut = false;

					// look for other cuts in the vicinity
					Set<PolyBase> cutsInArea = new HashSet<PolyBase>();
					cutsInArea.add(cut);
					Rectangle2D multiCutBounds = (Rectangle2D)cutBox.clone();
					double cutLimit = Math.ceil(Math.max(pv.multicutSep1D, pv.multicutSep2D) +
						Math.max(pv.multicutSizeX, pv.multicutSizeY)) * SCALEFACTOR;
					boolean foundMore = true;
                    double xspacing = 0, yspacing = 0;
                    while (foundMore)
					{
						foundMore = false;
                        Rectangle2D searchArea = new Rectangle2D.Double(multiCutBounds.getMinX()-cutLimit, multiCutBounds.getMinY()-cutLimit,
							multiCutBounds.getWidth() + cutLimit*2, multiCutBounds.getHeight() + cutLimit*2);
                        for(Iterator<CutBound> sea = new RTNode.Search<CutBound>(searchArea, cInfo.getRTree(), true); sea.hasNext(); )
						{
							CutBound cBound = sea.next();
							if (cutsInArea.contains(cBound.cut)) continue;
							Rectangle2D bound = cBound.getBounds();
							if (!searchArea.contains(bound.getCenterX(), bound.getCenterY())) continue;

                            // use only cuts at a spacing that matches (a multiple of) the nearest contact spacing
                            double distX = cut.getCenterX() - bound.getCenterX();
                            double distY = cut.getCenterY() - bound.getCenterY();

                            if (xspacing == 0) xspacing = distX;
                            else if (distX % xspacing != 0) continue;
                            if (yspacing == 0) yspacing = distY;
                            else if (distY % yspacing != 0) continue;

/*
                            // make sure cuts are in a contiguous array at the initial spacing
                            if (furthestX == 0) furthestX = distX;
                            if (furthestY == 0) furthestY = distY;
                            if (distX > furthestX) {
                                if (distX == furthestX + xspacing)
                                    furthestX = distX; // this is the next one on grid
                                else
                                    continue; // on grid, but not contiguous
                            }
                            if (distY > furthestY) {
                                if (distY == furthestY + yspacing)
                                    furthestY = distY; // this is the next one on grid
                                else
                                    continue; // on grid, but not contiguous
                            }

                            // record height of first column
                            if (maxColumnHeight == 0 && distX != 0) {
                                // first contact of the second column
                                maxColumnHeight = furthestY;
                            }
*/

                            double lX = Math.min(multiCutBounds.getMinX(), bound.getMinX());
							double hX = Math.max(multiCutBounds.getMaxX(), bound.getMaxX());
							double lY = Math.min(multiCutBounds.getMinY(), bound.getMinY());
							double hY = Math.max(multiCutBounds.getMaxY(), bound.getMaxY());
							Rectangle2D newMultiCutBounds = new Rectangle2D.Double(lX, lY, hX-lX, hY-lY);

							// make sure the expanded area has both metal layers in it
							boolean fits = true;
							PolyBase layerPoly = new PolyBase(newMultiCutBounds);
							for(int i=0; i<pv.layers.length; i++)
							{
								Layer lay = pv.layers[i];
								if (lay.getFunction().isMetal())
								{
									if (!originalMerge.contains(lay, layerPoly)) { fits = false;   break; }
								}
							}
							if (!fits) continue;

							// accumulate this cut
							multiCutBounds = newMultiCutBounds;
							cutsInArea.add(cBound.cut);
							foundMore = true;
						}
					}

                    // reduce via array to rectangle
                    if (xspacing == 0) xspacing = cutLimit;
                    if (yspacing == 0) yspacing = cutLimit;
                    Set<PolyBase> rectVias = getLargestRectangleOfVias(cutsInArea, cut, xspacing, yspacing, multiCutBounds);
                    cutsInArea.clear();
                    cutsInArea.addAll(rectVias);
                    multiCutBounds = (Rectangle2D)cutBox.clone();
                    for (PolyBase via : rectVias) {
                        Rectangle2D bound = via.getBounds2D();
                        double lX = Math.min(multiCutBounds.getMinX(), bound.getMinX());
                        double hX = Math.max(multiCutBounds.getMaxX(), bound.getMaxX());
                        double lY = Math.min(multiCutBounds.getMinY(), bound.getMinY());
                        double hY = Math.max(multiCutBounds.getMaxY(), bound.getMaxY());
                        multiCutBounds = new Rectangle2D.Double(lX, lY, hX-lX, hY-lY);
                    }

                    if (DEBUGCONTACTS) System.out.println("   FOUND LARGE CONTACT WITH "+cutsInArea.size()+" CUTS, IN "+
						TextUtils.formatDouble(multiCutBounds.getMinX()/SCALEFACTOR)+"<=X<="+TextUtils.formatDouble(multiCutBounds.getMaxX()/SCALEFACTOR)+" AND "+
						TextUtils.formatDouble(multiCutBounds.getMinY()/SCALEFACTOR)+"<=Y<="+TextUtils.formatDouble(multiCutBounds.getMaxY()/SCALEFACTOR));

					// determine size of possible multi-cut contact
					double trueWidth = pv.minWidth;
					double trueHeight = pv.minHeight;
					if (pv.rotation == 90 || pv.rotation == 270)
					{
						trueWidth = pv.minHeight;
						trueHeight = pv.minWidth;
					}
					double lX = cutBox.getCenterX(), hX = cutBox.getCenterX();
					double lY = cutBox.getCenterY(), hY = cutBox.getCenterY();
					for(PolyBase cutBound : cutsInArea)
					{
						if (cutBound.getCenterX() < lX) lX = cutBound.getCenterX();
						if (cutBound.getCenterX() > hX) hX = cutBound.getCenterX();
						if (cutBound.getCenterY() < lY) lY = cutBound.getCenterY();
						if (cutBound.getCenterY() > hY) hY = cutBound.getCenterY();
					}
					lX -= trueWidth/2;    hX += trueWidth/2;
					lY -= trueHeight/2;   hY += trueHeight/2;
					Rectangle2D contactBound = new Rectangle2D.Double(lX, lY, hX-lX, hY-lY);

					// see if largest multi-cut contact fits
					Layer badLayer = doesNodeFit(pv, multiCutBounds, originalMerge, ignorePWell, ignoreNWell);
					if (badLayer == null)
					{
						// it fits: see if a the cuts fit
						double mw = contactBound.getWidth();
						double mh = contactBound.getHeight();
						if (pv.rotation == 90 || pv.rotation == 270)
						{
							mw = contactBound.getHeight();
							mh = contactBound.getWidth();
						}
						if (DEBUGCONTACTS) System.out.println("   METAL LAYERS OF LARGE CONTACT FITS...NOW CHECKING CUTS ON "+
							TextUtils.formatDouble(mw/SCALEFACTOR)+"x"+TextUtils.formatDouble(mh/SCALEFACTOR)+" NODE");
						badLayer = realizeBiggestContact(pv.pNp, Technology.NodeLayer.MULTICUT_CENTERED,
							contactBound.getCenterX(), contactBound.getCenterY(), mw, mh,
							pv.rotation*10, originalMerge, newCell, contactNodes, activeCut, polyCut, usePureLayerNodes, cutsInArea);
						if (badLayer == null)
						{
							for(PolyBase cutFound : cutsInArea)
								cInfo.removeCut(cutFound);
							soFar += cutsInArea.size() - 1;
							foundCut = true;
							break;
						}
						if (cutsInArea.size() > 1 && pv.pNp.findMulticut() != null)
						{
							Layer spreadBadLayer = realizeBiggestContact(pv.pNp, Technology.NodeLayer.MULTICUT_SPREAD,
								contactBound.getCenterX(), contactBound.getCenterY(), mw, mh,
								pv.rotation*10, originalMerge, newCell, contactNodes, activeCut, polyCut, usePureLayerNodes, cutsInArea);
							if (spreadBadLayer == null)
							{
								for(PolyBase cutFound : cutsInArea)
									cInfo.removeCut(cutFound);
								soFar += cutsInArea.size() - 1;
								foundCut = true;
								break;
							}

							spreadBadLayer = realizeBiggestContact(pv.pNp, Technology.NodeLayer.MULTICUT_CORNER,
								contactBound.getCenterX(), contactBound.getCenterY(), mw, mh,
								pv.rotation*10, originalMerge, newCell, contactNodes, activeCut, polyCut, usePureLayerNodes, cutsInArea);
							if (spreadBadLayer == null)
							{
								for(PolyBase cutFound : cutsInArea)
									cInfo.removeCut(cutFound);
								soFar += cutsInArea.size() - 1;
								foundCut = true;
								break;
							}
						}
						if (DEBUGCONTACTS) System.out.println("      LARGE CONTACT CUTS DO NOT FIT (LAYER "+badLayer.getName()+")");
					}

					// try for exact cut placement with a single contact
					if (DEBUGCONTACTS) System.out.println("   CONSIDER SMALL CONTACT IN "+
						TextUtils.formatDouble(cutBox.getMinX()/SCALEFACTOR)+"<=X<="+
						TextUtils.formatDouble(cutBox.getMaxX()/SCALEFACTOR)+" AND "+
						TextUtils.formatDouble(cutBox.getMinY()/SCALEFACTOR)+"<=Y<="+
						TextUtils.formatDouble(cutBox.getMaxY()/SCALEFACTOR));
					badLayer = doesNodeFit(pv, cutBox, originalMerge, ignorePWell, ignoreNWell);
					if (badLayer == null)
					{
						// it fits: create it
						realizeNode(pv.pNp, Technology.NodeLayer.MULTICUT_CENTERED, cutBox.getCenterX(), cutBox.getCenterY(),
							pv.minWidth, pv.minHeight, pv.rotation*10, null, originalMerge, newCell, contactNodes, usePureLayerNodes);
						cInfo.removeCut(cut);
						foundCut = true;
						break;
					}

					String reason = "not enough of layer " + badLayer.getName();
					if (pv.rotation != 0) reason += " (when rotated " + pv.rotation + ")";
					reasons.put(pv, reason);
				}
				if (!foundCut)
				{
					double centerX = cutBox.getCenterX()/SCALEFACTOR;
					double centerY = cutBox.getCenterY()/SCALEFACTOR;
					String msg = "Cell " + newCell.describe(false) + ": Did not extract contact " +
						cut.getLayer().getName() + " cut at (" + TextUtils.formatDouble(centerX) + "," +
						TextUtils.formatDouble(centerY) + "). Here is why:";
					for(PossibleVia pv : reasons.keySet())
						msg += "\n    For " + pv.pNp.describe(false) + ", " + reasons.get(pv);
					addErrorLog(newCell, msg, EPoint.fromLambda(centerX, centerY));
					cInfo.removeCut(cut);
					cutsNotExtracted.add(cut);
				}
			}
			for(PolyBase pb : cutsNotExtracted)
				cInfo.addCut(pb);
		}

		// now remove all created contacts from the original merge
		if (!startSection(oldCell, "Finished extracting " + contactNodes.size() + " vias..."))
			 return false; // aborted

		// recursively scan the R-Tree, merging geometry on created nodes and removing them from the main merge
        if (!usePureLayerNodes)
        {
    		// build an R-Tree of all created nodes
    		RTNode<NodeInst> root = RTNode.makeTopLevel();
    		for(NodeInst ni : contactNodes)
    			root = RTNode.linkGeom(null, root, ni);

    		PolyMerge subtractMerge = new PolyMerge();
		    extractContactNodes(root, merge, subtractMerge, 0, contactNodes.size());
            merge.subtractMerge(subtractMerge);
        }
        return true;
	}

    /**
     * Multi-cut vias must be rectangular - get the largest rectangle that fits in the set of vias,
     * that includes the initial via
     * @param vias set of vias
     * @param initialVia the initial via
     * @param xspacing x spacing between vias
     * @param yspacing y spacing between vias
     * @param bounds bounds of all the vias
     * @return the subset of vias that form a contiguous rectangle, containing the initial via
     */
    private Set<PolyBase> getLargestRectangleOfVias(Set<PolyBase> vias, PolyBase initialVia, double xspacing, double yspacing, Rectangle2D bounds)
    {
        xspacing = Math.abs(xspacing);
        yspacing = Math.abs(yspacing);
        if (xspacing == 0) xspacing = SCALEFACTOR;
        if (yspacing == 0) yspacing = SCALEFACTOR;
        int numViasWide = (int)Math.ceil(bounds.getWidth() / xspacing);
        int numViasHigh = (int)Math.ceil(bounds.getHeight() / yspacing);
        PolyBase [][] viaMap = new PolyBase[numViasWide][numViasHigh];
        int nomX = 0, nomY = 0;

        for (int x=0; x<numViasWide; x++) {
            Arrays.fill(viaMap[x], null);
        }

        // populate map
        for (PolyBase via : vias) {
            int x = (int)((via.getCenterX() - bounds.getMinX()) / xspacing);
            int y = (int)((via.getCenterY() - bounds.getMinY()) / yspacing);
            viaMap[x][y] = via;
            if (via == initialVia) {
                nomX = x; nomY = y;
            }
        }
        // find maxY and minY from initial via
        int maxY = nomY, minY = nomY;
        boolean initial = true;
        for (int x=0; x<numViasWide; x++) {
            if (viaMap[x][nomY] == null) continue; // skip this column, will be taken into account in X max/min
            // max
            int colMaxY = nomY;
            for (int y=nomY; y<numViasHigh; y++) {
                if (viaMap[x][y] == null) break;
                colMaxY = y;
            }
            if (initial) maxY = colMaxY; // initial column
            else if (colMaxY < maxY) maxY = colMaxY;
            // min
            int colMinY = nomY;
            for (int y=nomY; y>=0; y--) {
                if (viaMap[x][y] == null) break;
                colMinY = y;
            }
            if (initial) minY = colMinY; // initial column
            else if (colMinY > minY) minY = colMinY;
            initial = false;
        }

        // find maxX and minX from initial via
        int maxX = nomX, minX = nomX;
        initial = true;
        for (int y=0; y<numViasHigh; y++) {
            if (viaMap[nomX][y] == null) continue; // skip this row, will be taken into account in Y max/min
            // max
            int colMaxX = nomX;
            for (int x=nomX; x<numViasWide; x++) {
                if (viaMap[x][y] == null) break;
                colMaxX = x;
            }
            if (initial) maxX = colMaxX; // initial row
            else if (colMaxX < maxX) maxX = colMaxX;
            // min
            int colMinX = nomX;
            for (int x=nomX; x>=0; x--) {
                if (viaMap[x][y] == null) break;
                colMinX = x;
            }
            if (initial) minX = colMinX; // initial row
            else if (colMinX > minX) minX = colMinX;
            initial = false;
        }

        // get rectangle
        Set<PolyBase> rectVias = new HashSet<PolyBase>();
        for (int x=minX; x<=maxX; x++) {
            for (int y=minY; y<=maxY; y++) {
                rectVias.add(viaMap[x][y]);
            }
        }
        return rectVias;
    }

    /**
	 * Method to create the biggest contact node in a given location.
	 * @param pNp the type of node to create.
	 * @param cutVariation the contact cut spacing rule.
	 * @param x the center X coordinate of the node.
	 * @param y the center Y coordinate of the node.
	 * @param sX the initial width of the node.
	 * @param sY the initial height of the node.
	 * @param rot the rotation of the node.
	 * @param merge the merge in which this node must reside.
	 * @param newCell the Cell in which the node will be created.
	 * @param contactNodes a list of nodes that were created.
	 * @param activeCut true if this is an active cut and must not have poly in the area.
	 * @param polyCut true if this is an poly cut and must not have active in the area.
	 * @param cutsInArea the cut polygons in the area (for exact matching).
	 * @return null if successful, otherwise the polygon that could not be matched.
	 */
	private Layer realizeBiggestContact(PrimitiveNode pNp, int cutVariation, double x, double y, double sX, double sY, int rot,
		PolyMerge merge, Cell newCell, List<NodeInst> contactNodes, boolean activeCut, boolean polyCut, boolean usePureLayerNodes,
		Set<PolyBase> cutsInArea)
	{
//boolean debug = false;
//if (debug) System.out.println("LOOKING FOR LARGEST CUT...");
		Orientation orient = Orientation.fromAngle(rot);
		if (alignment != null)
		{
			double scale = scaleUp(alignment.getWidth());
			x = Math.round(x / scale) * scale;
			scale = scaleUp(alignment.getHeight());
			y = Math.round(y / scale) * scale;
		}
		EPoint ctr = EPoint.fromLambda(x / SCALEFACTOR, y / SCALEFACTOR);
//		SizeOffset so = pNp.getProtoSizeOffset();
//		double widthReduction = so.getLowXOffset() + so.getHighXOffset();
//		double heightReduction = so.getLowYOffset() + so.getHighYOffset();

		// first find an X size that does not fit
		double lowXInc = 0;
		double highXInc = SCALEFACTOR*2;
		for(;;)
		{
			NodeInst ni = makeDummyNodeInst(pNp, ctr, sX+highXInc, sY, orient, cutVariation);
			PolyBase error = dummyNodeFits(ni, merge, activeCut, polyCut, cutsInArea);
//if (debug) System.out.println("  GROWING X..."+TextUtils.formatDouble((sX+highXInc)/SCALEFACTOR-widthReduction)+"x"+TextUtils.formatDouble(sY/SCALEFACTOR-heightReduction)+(error==null?" FITS":" DOES NOT FIT ("+error.getLayer().getName()+" is "+TextUtils.formatDouble(error.getBounds2D().getWidth()/SCALEFACTOR)+"x"+TextUtils.formatDouble(error.getBounds2D().getHeight()/SCALEFACTOR)+")"));
			if (error != null) break;
			lowXInc = highXInc;
			highXInc *= 2;
		}

		// now iterate to find the precise node X size
		for(;;)
		{
			if (highXInc - lowXInc <= 1) { lowXInc = Math.floor(lowXInc);   break; }
			double medInc = (lowXInc + highXInc) / 2;
			NodeInst ni = makeDummyNodeInst(pNp, ctr, sX+medInc, sY, orient, cutVariation);
			PolyBase error = dummyNodeFits(ni, merge, activeCut, polyCut, cutsInArea);
//if (debug) System.out.println("  ITERATING X..."+TextUtils.formatDouble((sX+medInc)/SCALEFACTOR-widthReduction)+"x"+TextUtils.formatDouble(sY/SCALEFACTOR-heightReduction)+(error==null?" FITS":" DOES NOT FIT ("+error.getLayer().getName()+" is "+TextUtils.formatDouble(error.getBounds2D().getWidth()/SCALEFACTOR)+"x"+TextUtils.formatDouble(error.getBounds2D().getHeight()/SCALEFACTOR)+")"));
			if (error == null) lowXInc = medInc; else
				highXInc = medInc;
		}

		// first find an Y size that does not fit
		double lowYInc = 0;
		double highYInc = SCALEFACTOR*2;
		for(;;)
		{
			NodeInst ni = makeDummyNodeInst(pNp, ctr, sX, sY+highYInc, orient, cutVariation);
			PolyBase error = dummyNodeFits(ni, merge, activeCut, polyCut, cutsInArea);
//if (debug) System.out.println("  GROWING Y..."+TextUtils.formatDouble(sX/SCALEFACTOR-widthReduction)+"x"+TextUtils.formatDouble((sY+highYInc)/SCALEFACTOR-heightReduction)+(error==null?" FITS":" DOES NOT FIT ("+error.getLayer().getName()+" is "+TextUtils.formatDouble(error.getBounds2D().getWidth()/SCALEFACTOR)+"x"+TextUtils.formatDouble(error.getBounds2D().getHeight()/SCALEFACTOR)+")"));
			if (error != null) break;
			lowYInc = highYInc;
			highYInc *= 2;
		}

		// now iterate to find the precise node Y size
		for(;;)
		{
			if (highYInc - lowYInc <= 1) { lowYInc = Math.floor(lowYInc);   break; }
			double medInc = (lowYInc + highYInc) / 2;
			NodeInst ni = makeDummyNodeInst(pNp, ctr, sX, sY+medInc, orient, cutVariation);
			PolyBase error = dummyNodeFits(ni, merge, activeCut, polyCut, cutsInArea);
//if (debug) System.out.println("  ITERATING Y..."+TextUtils.formatDouble(sX/SCALEFACTOR-widthReduction)+"x"+TextUtils.formatDouble((sY+medInc)/SCALEFACTOR-heightReduction)+(error==null?" FITS":" DOES NOT FIT ("+error.getLayer().getName()+" is "+TextUtils.formatDouble(error.getBounds2D().getWidth()/SCALEFACTOR)+"x"+TextUtils.formatDouble(error.getBounds2D().getHeight()/SCALEFACTOR)+")"));
			if (error == null) lowYInc = medInc; else
				highYInc = medInc;
		}
		if (!approximateCuts)
		{
			// make sure the basic node fits
			NodeInst ni = makeDummyNodeInst(pNp, ctr, sX+lowXInc, sY+lowYInc, orient, cutVariation);
			PolyBase error = dummyNodeFits(ni, merge, activeCut, polyCut, cutsInArea);
//if (debug) System.out.println("  CHECK FINAL..."+TextUtils.formatDouble((sX+lowXInc)/SCALEFACTOR-widthReduction)+"x"+TextUtils.formatDouble((sY+lowYInc)/SCALEFACTOR-heightReduction)+(error==null?" FITS":" DOES NOT FIT ("+error.getLayer().getName()+" is "+TextUtils.formatDouble(error.getBounds2D().getWidth()/SCALEFACTOR)+"x"+TextUtils.formatDouble(error.getBounds2D().getHeight()/SCALEFACTOR)+")"));
			if (error != null)
			{
				// incrementing both axes fails: try one at a time
				if (lowXInc > lowYInc)
				{
					ni = makeDummyNodeInst(pNp, ctr, sX+lowXInc, sY, orient, cutVariation);
					error = dummyNodeFits(ni, merge, activeCut, polyCut, cutsInArea);
//if (debug) System.out.println("  FINAL, JUST X..."+TextUtils.formatDouble((sX+lowXInc)/SCALEFACTOR-widthReduction)+"x"+TextUtils.formatDouble((sY)/SCALEFACTOR-heightReduction)+(error==null?" FITS":" DOES NOT FIT ("+error.getLayer().getName()+" is "+TextUtils.formatDouble(error.getBounds2D().getWidth()/SCALEFACTOR)+"x"+TextUtils.formatDouble(error.getBounds2D().getHeight()/SCALEFACTOR)+")"));
					if (error == null) sX += lowXInc;
				} else
				{
					ni = makeDummyNodeInst(pNp, ctr, sX, sY+lowYInc, orient, cutVariation);
					error = dummyNodeFits(ni, merge, activeCut, polyCut, cutsInArea);
//if (debug) System.out.println("  FINAL, JUST Y..."+TextUtils.formatDouble((sX)/SCALEFACTOR-widthReduction)+"x"+TextUtils.formatDouble((sY+lowYInc)/SCALEFACTOR-heightReduction)+(error==null?" FITS":" DOES NOT FIT ("+error.getLayer().getName()+" is "+TextUtils.formatDouble(error.getBounds2D().getWidth()/SCALEFACTOR)+"x"+TextUtils.formatDouble(error.getBounds2D().getHeight()/SCALEFACTOR)+")"));
					if (error == null) sY += lowYInc;
				}
			} else { sX += lowXInc;   sY += lowYInc; }
			if (error != null) return error.getLayer();
		}
		sX = Math.round(sX);
		sY = Math.round(sY);
		if (alignment != null)
		{
			double scale = scaleUp(alignment.getWidth()) * 2;
			sX = Math.floor(sX / scale) * scale;
//if (debug) System.out.println("  ALIGNING FINAL IN X TO "+TextUtils.formatDouble(sX/SCALEFACTOR-widthReduction)+"x"+TextUtils.formatDouble(sY/SCALEFACTOR-heightReduction)+" (X alignment is "+TextUtils.formatDouble(scale/SCALEFACTOR)+")");
			scale = scaleUp(alignment.getHeight()) * 2;
			sY = Math.floor(sY / scale) * scale;
//if (debug) System.out.println("  ALIGNING FINAL IN Y TO "+TextUtils.formatDouble(sX/SCALEFACTOR-widthReduction)+"x"+TextUtils.formatDouble(sY/SCALEFACTOR-heightReduction)+" (Y alignment is "+TextUtils.formatDouble(scale/SCALEFACTOR)+")");
		}
		realizeNode(pNp, cutVariation, x, y, sX, sY, rot, null, merge, newCell, contactNodes, usePureLayerNodes);
		return null;
	}

	private NodeInst makeDummyNodeInst(PrimitiveNode pNp, EPoint ctr, double sX, double sY, Orientation orient, int cutVariation)
	{
		NodeInst ni = NodeInst.makeDummyInstance(pNp, ep, ctr, sX / SCALEFACTOR, sY / SCALEFACTOR, orient);
		if (ni == null) return null;
		if (cutVariation != Technology.NodeLayer.MULTICUT_CENTERED)
			ni.newVar(NodeLayer.CUT_ALIGNMENT, Integer.valueOf(cutVariation), ep);
		return ni;
	}

	private PolyBase dummyNodeFits(NodeInst ni, PolyMerge merge, boolean activeCut, boolean polyCut, Set<PolyBase> cutsInArea)
	{
		FixpTransform trans = ni.rotateOut();
		Technology tech = ni.getProto().getTechnology();
		Poly [] polys = tech.getShapeOfNode(ni);
		double biggestArea = 0;
		Poly biggestPoly = null;
		List<PolyBase> cutsFound = null;
		if (!approximateCuts) cutsFound = new ArrayList<PolyBase>();
        boolean hasPplus = false;
        boolean hasNplus = false;
        boolean hasActive = false;
        for(Poly poly : polys)
		{
			Layer l = poly.getLayer();
			if (l == null) continue;
			l = geometricLayer(l);

            // ignore well layers if the process doesn't have them (in this case they are substrate layers)
            if (l.getFunction() == Layer.Function.WELLP && pSubstrateProcess) continue;
            if (l.getFunction() == Layer.Function.WELLN && nSubstrateProcess) continue;

            if (l.isDiffusionLayer()) hasActive = true;
            if (l.getFunction() == Layer.Function.IMPLANTN) hasNplus = true;
            if (l.getFunction() == Layer.Function.IMPLANTP) hasPplus = true;

            poly.setLayer(l);
			poly.transform(trans);
			if (l.getFunction().isContact())
			{
				if (!approximateCuts) cutsFound.add(poly);
				continue;
			}
			double area = poly.getArea();
			if (area > biggestArea)
			{
				biggestArea = area;
				biggestPoly = poly;
			}
			Point2D[] points = poly.getPoints();
			for(int i=0; i<points.length; i++)
				poly.setPoint(i, scaleUp(points[i].getX()), scaleUp(points[i].getY()));
			if (!merge.contains(l, poly))
				return poly;
		}

        // special case: some substrate contacts in some technologies do not have the substrate layer
        // (since often the original geometric also does not have the substrate layer).
        // To prevent these from being used as regular contacts, do an extra check here
        PrimitiveNode pn = (PrimitiveNode)ni.getProto();
        PolyBase testPoly = new PolyBase(
                PolyBase.fromLambda(scaleUp(ni.getAnchorCenterX())-1, scaleUp(ni.getAnchorCenterY())-1),
                PolyBase.fromLambda(scaleUp(ni.getAnchorCenterX())-1, scaleUp(ni.getAnchorCenterY())+1),
                PolyBase.fromLambda(scaleUp(ni.getAnchorCenterX())+1, scaleUp(ni.getAnchorCenterY())+1),
                PolyBase.fromLambda(scaleUp(ni.getAnchorCenterX())+1, scaleUp(ni.getAnchorCenterY())-1)
        );
        testPoly.setLayer(wellLayer);
        if (pn.getFunction() == PrimitiveNode.Function.SUBSTRATE) // defines a substrate contact
        {
            // make sure that substrate contact is not being placed on top of Well (N-well for P-substrate process)
            if (wellLayer != null)
            {
                if (merge.contains(wellLayer, testPoly))
                    return testPoly;
            }
        }
        // special case: active contact without substrate layer looks the same as a well/substrate tap.
        // Make sure we are using the correct one.
        if (pn.getFunction() == PrimitiveNode.Function.CONTACT && hasActive)
        {
            // active contact.
        	// If P-substrate process, make sure N-diffusion contact is not being placed on top of N-well.
            // If N-substrate process, make sure P-diffusion contact is not being placed on top of P-well.
            if ((pSubstrateProcess && hasNplus) || (nSubstrateProcess && hasPplus))
            {
                if (merge.contains(wellLayer, testPoly))
                    return testPoly;
            }
        }

        if (!approximateCuts && cutsInArea != null)
		{
			// make sure all cuts in area are found in the node
			for(PolyBase pb : cutsInArea)
			{
				boolean foundIt = false;
				for(int i=0; i<cutsFound.size(); i++)
				{
					PolyBase pb2 = cutsFound.get(i);
					if (DBMath.doublesClose(pb.getCenterX()/SCALEFACTOR, pb2.getCenterX()) &&
						DBMath.doublesClose(pb.getCenterY()/SCALEFACTOR, pb2.getCenterY()))
					{
						cutsFound.remove(i);
						foundIt = true;
						break;
					}
				}
				if (!foundIt) return pb;
			}
			if (cutsFound.size() > 0) return cutsFound.get(0);
		}

		// contact fits, now check for active or poly problems
		if (activeCut && biggestPoly != null)
		{
			// disallow poly in the contact area
			if (merge.intersects(polyLayer, biggestPoly)) return biggestPoly;
		}
		if (polyCut && biggestPoly != null)
		{
			// disallow active in the contact area
			if (pActiveLayer != null && merge.intersects(pActiveLayer, biggestPoly)) return biggestPoly;
			if (nActiveLayer != null && nActiveLayer != pActiveLayer && merge.intersects(nActiveLayer, biggestPoly)) return biggestPoly;
		}
		return null;
	}

	/**
	 * Class to define all of the cuts on a given layer.
	 */
	private static class CutInfo
	{
		private Map<PolyBase,CutBound> cutMap;
		private List<PolyBase> justCuts;
		private RTNode<CutBound> cutOrg;

		public CutInfo()
		{
			cutMap = new HashMap<PolyBase,CutBound>();
			justCuts = new ArrayList<PolyBase>();
			cutOrg = RTNode.makeTopLevel();
		}

		public List<PolyBase> getCuts() { return justCuts; }

		public int getNumCuts() { return justCuts.size(); }

		public PolyBase getFirstCut() { return justCuts.get(0); }

		public RTNode<CutBound> getRTree() { return cutOrg; }

		public void sortCuts()
		{
			Collections.sort(justCuts, new CutsByLocation());
		}

		public void addCut(PolyBase cut)
		{
			CutBound cb = new CutBound(cut);
			cutOrg = RTNode.linkGeom(null, cutOrg, cb);
			cutMap.put(cut, cb);
			justCuts.add(cut);
		}

		public void removeCut(PolyBase cut)
		{
			CutBound cb = cutMap.get(cut);
			cutOrg = RTNode.unLinkGeom(null, cutOrg, cb);
			cutMap.remove(cut);
			justCuts.remove(cut);
		}
	}

	/**
	 * Class to sort CutsByLocation objects by their location.
	 */
	private static class CutsByLocation implements Comparator<PolyBase>
	{
		public int compare(PolyBase pb1, PolyBase pb2)
		{
			double pb1x = pb1.getCenterX();
			double pb1y = pb1.getCenterY();
			double pb2x = pb2.getCenterX();
			double pb2y = pb2.getCenterY();

			if (pb1x < pb2x) return 1;
			if (pb1x > pb2x) return -1;
			if (pb1y < pb2y) return 1;
			if (pb1y > pb2y) return -1;
			return 0;
		}
	}

	/**
	 * Class to define an R-Tree leaf node for geometry in the via cuts.
	 */
	private static class CutBound implements RTBounds
	{
		private PolyBase cut;

		CutBound(PolyBase cut)
		{
			this.cut = cut;
		}

        @Override
		public FixpRectangle getBounds() { return cut.getBounds2D(); }
	}

	/**
	 * Method to recursively scan the R-Tree of created contact nodes to remove their geometry from the main merge.
	 * @param root the current position in the scan of the R-Tree.
	 * @param merge the main merge.
	 * @param subtractMerge a merge of created contact nodes that will be subtracted from the main merge.
	 * @param soFar the number of contacts handled so far.
	 * @param totalContacts the total number of contacts to handle.
	 * @return the number of contacts handled so far.
	 */
	private int extractContactNodes(RTNode<NodeInst> root, PolyMerge merge, PolyMerge subtractMerge, int soFar, int totalContacts)
	{
		for(int j=0; j<root.getTotal(); j++)
		{
			if (root.getFlag())
			{
    			NodeInst ni = root.getChildLeaf(j);
				soFar++;
				if (!recursive && (soFar % 100) == 0) Job.getUserInterface().setProgressValue(soFar * 100 / totalContacts);
				FixpTransform trans = ni.rotateOut();
				Poly [] polys = tech.getShapeOfNode(ni);
				for(int i=0; i<polys.length; i++)
				{
					Poly poly = polys[i];
					Layer layer = poly.getLayer();

					// make sure the geometric database is made up of proper layers
					layer = geometricLayer(layer);

                    poly.transform(trans);
					Point2D [] points = poly.getPoints();
					for(int k=0; k<points.length; k++)
						poly.setPoint(k, scaleUp(points[k].getX()), scaleUp(points[k].getY()));
					poly.roundPoints();
					subtractMerge.add(layer, poly);
				}

				// every 500 nodes, apply the merge to the original
				if ((soFar % 500) == 0)
				{
					merge.subtractMerge(subtractMerge);
					List<Layer> allLayers = new ArrayList<Layer>();
					for(Layer lay : subtractMerge.getKeySet())
						allLayers.add(lay);
					for(Layer lay : allLayers)
						subtractMerge.deleteLayer(lay);
				}
			} else
			{
                RTNode<NodeInst> child = root.getChildTree(j);
				soFar = extractContactNodes(child, merge, subtractMerge, soFar, totalContacts);
			}
		}
		return soFar;
	}

	/**
	 * Method to return a list of PossibleVia objects for a given cut/via layer.
	 * @param lay the cut/via layer to find.
	 * @return a List of PossibleVia objects that use the layer as a contact.
	 */
	private List<PossibleVia> findPossibleVias(Layer lay)
	{
		List<PossibleVia> possibleVias = new ArrayList<PossibleVia>();
		for(Iterator<PrimitiveNode> nIt = tech.getNodes(); nIt.hasNext(); )
		{
			PrimitiveNode pNp = nIt.next();
			PrimitiveNode.Function fun = pNp.getFunction();
			if (!fun.isContact() && fun != PrimitiveNode.Function.WELL &&
				fun != PrimitiveNode.Function.SUBSTRATE) continue;

			// TODO do we really need to check each contact?
			// For some reason, the MOCMOS technology with MOCMOS foundry shows the "A-" nodes (A-Metal-1-Metal-2-Contact)
			// but these primitives are not fully in existence, and crash here
			boolean bogus = false;
			Technology.NodeLayer [] nLs = pNp.getNodeLayers();
			for(int i=0; i<nLs.length; i++)
			{
				Technology.NodeLayer nLay = nLs[i];
				TechPoint [] debugPoints = nLay.getPoints();
				if (debugPoints == null || debugPoints.length <= 0)
				{
					bogus = true;
					break;
				}
			}
			if (bogus) continue;

			// load the layer information
			NodeInst dummyNI = NodeInst.makeDummyInstance(pNp, ep);
			Poly[] layerPolys = tech.getShapeOfNode(dummyNI);
			int cutNodeLayer = -1;
			int m1Layer = -1;
			int m2Layer = -1;
			boolean hasPolyActive = false;
			double cutSizeX = 0, cutSizeY = 0;
			List<Integer> pvLayers = new ArrayList<Integer>();
			for(int i=0; i<layerPolys.length; i++)
			{
				// examine one layer of the primitive node
				Poly poly = layerPolys[i];
				Layer nLayer = poly.getLayer();
				Layer.Function lFun = nLayer.getFunction();
				if (lFun.isMetal())
				{
					if (m1Layer < 0) m1Layer = i; else
						if (m2Layer < 0) m2Layer = i;
				} else if (lFun.isDiff() || lFun.isPoly()) hasPolyActive = true;

				// ignore well/select layers if requested
				if (ignoreActiveSelectWell && fun.isContact())
				{
					if (lFun.isImplant() || lFun.isSubstrate() || lFun.isWell()) continue;
				}

				// ignore well layers if the process doesn't have them
//				if (lFun == Layer.Function.WELLP && pSubstrateProcess) continue;
//				if (lFun == Layer.Function.WELLN && nSubstrateProcess) continue;

				boolean cutLayer = false;
				if (nLayer == lay)
				{
					// this is the target cut layer: mark it
					cutLayer = true;
				} else
				{
					// special case for active/poly cut confusion
					if (nLayer.getFunction() == lay.getFunction()) cutLayer = true;
				}
				if (cutLayer)
				{
					cutNodeLayer = i;
					cutSizeX = poly.getBounds2D().getWidth();
					cutSizeY = poly.getBounds2D().getHeight();
				} else
				{
					pvLayers.add(new Integer(i));
				}
			}
			if (cutNodeLayer < 0) continue;

			// make sure via contacts connect exactly two layers of metal, next to each other
			if (!hasPolyActive && fun.isContact())
			{
				boolean badContact = false;
				if (m1Layer < 0 || m2Layer < 0) badContact = true; else
				{
					int lowMetal = layerPolys[m1Layer].getLayer().getFunction().getLevel();
					int highMetal = layerPolys[m2Layer].getLayer().getFunction().getLevel();
					if (lowMetal > highMetal) { int s = lowMetal;   lowMetal = highMetal;   highMetal = s; }
					if (lowMetal != highMetal-1) badContact = true;
				}
				if (badContact)
				{
					if (!bogusContacts.contains(pNp))
					{
						bogusContacts.add(pNp);
						System.out.println("Not extracting unusual via contact: " + pNp.describe(false));
					}
					continue;
				}
			}

			// create the PossibleVia to describe the geometry
			PossibleVia pv = new PossibleVia(pNp, pvLayers.size(), cutSizeX, cutSizeY);
			for(int i=0; i<nLs.length; i++)
			{
				Technology.NodeLayer nLay = nLs[i];
				if (nLay.getLayer() == layerPolys[cutNodeLayer].getLayer())
				{
					pv.multicutSep1D = nLay.getMulticutSep1D().getLambda();
					pv.multicutSep2D = nLay.getMulticutSep2D().getLambda();
					pv.multicutSizeX = nLay.getMulticutSizeX().getLambda();
					pv.multicutSizeX = nLay.getMulticutSizeY().getLambda();
				}
			}
			pv.minWidth = scaleUp(pNp.getDefWidth(ep));
			pv.minHeight = scaleUp(pNp.getDefHeight(ep));
			int fill = 0;
			double cutLX = layerPolys[cutNodeLayer].getBounds2D().getMinX();
			double cutHX = layerPolys[cutNodeLayer].getBounds2D().getMaxX();
			double cutLY = layerPolys[cutNodeLayer].getBounds2D().getMinY();
			double cutHY = layerPolys[cutNodeLayer].getBounds2D().getMaxY();
			for(Integer layIndex : pvLayers)
			{
				int index = layIndex.intValue();
				pv.layers[fill] = geometricLayer(layerPolys[index].getLayer());
				double layLX = layerPolys[index].getBounds2D().getMinX();
				double layHX = layerPolys[index].getBounds2D().getMaxX();
				double layLY = layerPolys[index].getBounds2D().getMinY();
				double layHY = layerPolys[index].getBounds2D().getMaxY();
				pv.shrinkL[fill] = scaleUp(cutLX - layLX);
				pv.shrinkR[fill] = scaleUp(layHX - cutHX);
				pv.shrinkT[fill] = scaleUp(layHY - cutHY);
				pv.shrinkB[fill] = scaleUp(cutLY - layLY);
				fill++;
			}

			// add this to the list of possible vias
			possibleVias.add(pv);

			// if this via is asymmetric, add others with different rotation
			boolean hvSymmetry = true, rotSymmetry = true;
			for(int i=0; i<pv.layers.length; i++)
			{
				if (pv.shrinkL[i] != pv.shrinkR[i] || pv.shrinkT[i] != pv.shrinkB[i])
				{
					rotSymmetry = false;
					break;
				}
				if (pv.shrinkL[i] != pv.shrinkT[i] || pNp.getDefWidth(ep) != pNp.getDefHeight(ep))
					hvSymmetry = false;
			}
			if (!hvSymmetry || !rotSymmetry)
			{
				// horizontal/vertical or rotational symmetry missing: add a 90-degree rotation
				PossibleVia newPV = new PossibleVia(pv.pNp, pv.layers.length, cutSizeY, cutSizeX);
				newPV.rotation = 90;
				newPV.multicutSep1D = pv.multicutSep1D;   newPV.multicutSep2D = pv.multicutSep2D;
				newPV.multicutSizeX = pv.multicutSizeX;   newPV.multicutSizeY = pv.multicutSizeY;
				newPV.minWidth = pv.minWidth;
				newPV.minHeight = pv.minHeight;
				for(int i=0; i<pv.layers.length; i++)
				{
					newPV.layers[i] = pv.layers[i];
					newPV.shrinkL[i] = pv.shrinkT[i];
					newPV.shrinkR[i] = pv.shrinkB[i];
					newPV.shrinkT[i] = pv.shrinkR[i];
					newPV.shrinkB[i] = pv.shrinkL[i];
				}
				possibleVias.add(newPV);
			}
			if (!rotSymmetry)
			{
				// rotational symmetry missing: add a 180-degree and 270-degree rotation
				PossibleVia newPV = new PossibleVia(pv.pNp, pv.layers.length, cutSizeX, cutSizeY);
				newPV.rotation = 180;
				newPV.multicutSep1D = pv.multicutSep1D;   newPV.multicutSep2D = pv.multicutSep2D;
				newPV.multicutSizeX = pv.multicutSizeX;   newPV.multicutSizeY = pv.multicutSizeY;
				newPV.minWidth = pv.minWidth;
				newPV.minHeight = pv.minHeight;
				for(int i=0; i<pv.layers.length; i++)
				{
					newPV.layers[i] = pv.layers[i];
					newPV.shrinkL[i] = pv.shrinkR[i];
					newPV.shrinkR[i] = pv.shrinkL[i];
					newPV.shrinkT[i] = pv.shrinkB[i];
					newPV.shrinkB[i] = pv.shrinkT[i];
				}
				possibleVias.add(newPV);

				newPV = new PossibleVia(pv.pNp, pv.layers.length, cutSizeY, cutSizeX);
				newPV.rotation = 270;
				newPV.multicutSep1D = pv.multicutSep1D;   newPV.multicutSep2D = pv.multicutSep2D;
				newPV.multicutSizeX = pv.multicutSizeX;   newPV.multicutSizeY = pv.multicutSizeY;
				newPV.minWidth = pv.minWidth;
				newPV.minHeight = pv.minHeight;
				for(int i=0; i<pv.layers.length; i++)
				{
					newPV.layers[i] = pv.layers[i];
					newPV.shrinkL[i] = pv.shrinkB[i];
					newPV.shrinkR[i] = pv.shrinkT[i];
					newPV.shrinkT[i] = pv.shrinkL[i];
					newPV.shrinkB[i] = pv.shrinkR[i];
				}
				possibleVias.add(newPV);
			}
		}

		Collections.sort(possibleVias, new ViasBySize(ep));
		return possibleVias;
	}

	/**
	 * Class to sort PossibleVia objects by their size.
	 */
	private static class ViasBySize implements Comparator<PossibleVia>
	{
        private final EditingPreferences ep;

        private ViasBySize(EditingPreferences ep) {
            this.ep = ep;
        }

		public int compare(PossibleVia pv1, PossibleVia pv2)
		{
			double area1 = 0;
			Technology.NodeLayer [] layers1 = pv1.pNp.getNodeLayers();
			double sizeX = pv1.pNp.getDefSize(ep).getLambdaX();
			double sizeY = pv1.pNp.getDefSize(ep).getLambdaY();
			for(int i=0; i<layers1.length; i++)
			{
				Technology.NodeLayer nl = layers1[i];
				if (nl.getLayer().getFunction().isSubstrate()) continue;
				double lowX = nl.getLeftEdge().getMultiplier() * sizeX + nl.getLeftEdge().getAdder().getLambda();
				double highX = nl.getRightEdge().getMultiplier() * sizeX + nl.getRightEdge().getAdder().getLambda();
				double lowY = nl.getBottomEdge().getMultiplier() * sizeY + nl.getBottomEdge().getAdder().getLambda();
				double highY = nl.getTopEdge().getMultiplier() * sizeY + nl.getTopEdge().getAdder().getLambda();
				area1 += (highX-lowX) * (highY-lowY);
			}

			double area2 = 0;
			Technology.NodeLayer [] layers2 = pv2.pNp.getNodeLayers();
			sizeX = pv2.pNp.getDefSize(ep).getLambdaX();
			sizeY = pv2.pNp.getDefSize(ep).getLambdaY();
			for(int i=0; i<layers2.length; i++)
			{
				Technology.NodeLayer nl = layers2[i];
				if (nl.getLayer().getFunction().isSubstrate()) continue;
				double lowX = nl.getLeftEdge().getMultiplier() * sizeX + nl.getLeftEdge().getAdder().getLambda();
				double highX = nl.getRightEdge().getMultiplier() * sizeX + nl.getRightEdge().getAdder().getLambda();
				double lowY = nl.getBottomEdge().getMultiplier() * sizeY + nl.getBottomEdge().getAdder().getLambda();
				double highY = nl.getTopEdge().getMultiplier() * sizeY + nl.getTopEdge().getAdder().getLambda();
				area2 += (highX-lowX) * (highY-lowY);
			}

			if (area1 == area2) return 0;
			if (area1 < area2) return 1;
			return -1;
		}
	}

	/**
	 * Method to see if a via can be placed.
	 * @param pv the possible via.
	 * @param cutBox the bounding box of the contact cuts.
	 * @param originalMerge the original merge data.
	 * @param ignorePWell true to ignore p-well layers.
	 * @param ignoreNWell true to ignore n-well layers.
	 * @return null if a contact can be created.
	 * If contact cannot be created, returns the offending layer.
	 */
	private Layer doesNodeFit(PossibleVia pv, Rectangle2D cutBox, PolyMerge originalMerge,
		boolean ignorePWell, boolean ignoreNWell)
	{
        boolean hasPplus = false;
        boolean hasNplus = false;
        boolean hasActive = false;
		for(int i=0; i<pv.layers.length; i++)
		{
			Layer l = pv.layers[i];
			if (ignorePWell && l.getFunction() == Layer.Function.WELLP) continue;
			if (ignoreNWell && l.getFunction() == Layer.Function.WELLN) continue;
            if (l.isDiffusionLayer()) hasActive = true;
            if (l.getFunction() == Layer.Function.IMPLANTN) hasNplus = true;
            if (l.getFunction() == Layer.Function.IMPLANTP) hasPplus = true;

			double lX = cutBox.getMinX() - pv.shrinkL[i];
			double hX = cutBox.getMaxX() + pv.shrinkR[i];
			double lY = cutBox.getMinY() - pv.shrinkB[i];
			double hY = cutBox.getMaxY() + pv.shrinkT[i];
			double layerCX = (lX + hX) / 2;
			double layerCY = (lY + hY) / 2;
			PolyBase layerPoly = new PolyBase(layerCX, layerCY, hX-lX, hY-lY);
			if (!originalMerge.contains(l, layerPoly))
			{
				if (DEBUGCONTACTS) System.out.println("      CONTACT FAILS ON LAYER "+l.getName()+" WHICH MUST BE "+
					TextUtils.formatDouble(lX/SCALEFACTOR)+"<=X<="+TextUtils.formatDouble(hX/SCALEFACTOR)+" AND "+
					TextUtils.formatDouble(lY/SCALEFACTOR)+"<=Y<="+TextUtils.formatDouble(hY/SCALEFACTOR));
				return l;
			}
		}
        // special case: some substrate contacts in some technologies do not have the substrate layer
		// (since often the original geometric also does not have the substrate layer).
		// To prevent these from being used as regular contacts, do an extra check here
        PolyBase testPoly = new PolyBase(
                PolyBase.fromLambda(cutBox.getCenterX()-1, cutBox.getCenterY()-1),
                PolyBase.fromLambda(cutBox.getCenterX()-1, cutBox.getCenterY()+1),
                PolyBase.fromLambda(cutBox.getCenterX()+1, cutBox.getCenterY()+1),
                PolyBase.fromLambda(cutBox.getCenterX()+1, cutBox.getCenterY()-1)
        );
        testPoly.setLayer(wellLayer);
        if (pv.pNp.getFunction() == PrimitiveNode.Function.SUBSTRATE) // defines a substrate contact
        {
            // make sure that substrate contact is not being placed on top of Well (N-well for p-substrate process)
            if (wellLayer != null)
            {
                if (originalMerge.contains(wellLayer, testPoly)) {
                    if (DEBUGCONTACTS) System.out.println("      CONTACT FAILS ON LAYER "+wellLayer.getName()+" WHICH MUST NOT BE PRESENT AT "+
                        TextUtils.formatDouble(cutBox.getCenterX()/SCALEFACTOR)+","+TextUtils.formatDouble(cutBox.getCenterY()/SCALEFACTOR));
                    return wellLayer;
                }
            }
        }
        // special case: active contact without substrate layer looks the same as a well/substrate tap.
        // Make sure we are using the correct one.
        if (pv.pNp.getFunction() == PrimitiveNode.Function.CONTACT && hasActive)
        {
            // active contact.
        	// If P-substrate process, make sure N-diffusion contact is not being placed on top of N-well.
            // If N-substrate process, make sure P-diffusion contact is not being placed on top of P-well.
            if ((pSubstrateProcess && hasNplus) || (nSubstrateProcess && hasPplus))
            {
                if (originalMerge.contains(wellLayer, testPoly)) {
                    if (DEBUGCONTACTS) System.out.println("      CONTACT FAILS ON LAYER "+wellLayer.getName()+" WHICH MUST NOT BE PRESENT AT "+
                        TextUtils.formatDouble(cutBox.getCenterX()/SCALEFACTOR)+","+TextUtils.formatDouble(cutBox.getCenterY()/SCALEFACTOR));
                    return wellLayer;
                }
            }
        }
        // all layers fit: can create the node
		return null;
	}

	/********************************************** TRANSISTOR EXTRACTION **********************************************/

	private void extractTransistors(PolyMerge merge, PolyMerge originalMerge, Cell newCell, boolean usePureLayerNodes)
	{
		// must have a poly layer
		if (polyLayer == null || tempLayer1 == null) return;

		// find the transistors to create
		List<PrimitiveNode> pTransistors = new ArrayList<PrimitiveNode>();
		List<PrimitiveNode> nTransistors = new ArrayList<PrimitiveNode>();
		for(Iterator<PrimitiveNode> it = tech.getNodes(); it.hasNext(); )
		{
			PrimitiveNode pNp = it.next();
			if (pNp.getFunction().isPTypeTransistor()) pTransistors.add(pNp); else
				if (pNp.getFunction().isNTypeTransistor()) nTransistors.add(pNp);
		}

		if (nActiveLayer == pActiveLayer)
		{
			for(PrimitiveNode pNp : nTransistors) pTransistors.add(pNp);
			Collections.sort(pTransistors, new TransistorsByComplexity());
			if (pTransistors.size() > 0)
				findTransistors(pTransistors, pActiveLayer, merge, originalMerge, newCell, usePureLayerNodes);
		} else
		{
			if (nTransistors.size() > 0)
			{
				Collections.sort(nTransistors, new TransistorsByComplexity());
				findTransistors(nTransistors, nActiveLayer, merge, originalMerge, newCell, usePureLayerNodes);
			}
				
			if (pTransistors.size() > 0)
			{
				Collections.sort(pTransistors, new TransistorsByComplexity());
				findTransistors(pTransistors, pActiveLayer, merge, originalMerge, newCell, usePureLayerNodes);
			}
		}
	}

	/**
	 * Class to sort transistors by their complexity (number of layers used)
	 */
	private class TransistorsByComplexity implements Comparator<PrimitiveNode>
	{
		public int compare(PrimitiveNode pn1, PrimitiveNode pn2)
		{
			Set<Layer> numLayers1 = new HashSet<Layer>();
			Technology.NodeLayer [] layers1 = pn1.getNodeLayers();
			for(int i=0; i<layers1.length; i++)
			{
				Layer lay = geometricLayer(layers1[i].getLayer());
				numLayers1.add(lay);
			}

			Set<Layer> numLayers2 = new HashSet<Layer>();
			Technology.NodeLayer [] layers2 = pn2.getNodeLayers();
			for(int i=0; i<layers2.length; i++)
			{
				Layer lay = geometricLayer(layers2[i].getLayer());
				numLayers2.add(lay);
			}
			if (numLayers2.size() != numLayers1.size())
				return numLayers2.size() - numLayers1.size();
			if (layers2.length != layers1.length)
				return layers2.length - layers1.length;

			// compute largest area
			double area1 = 0;
			NodeInst ni1 = NodeInst.makeDummyInstance(pn1, ep);
			Poly[] polys1 = pn1.getTechnology().getShapeOfNode(ni1);
			for(Poly poly : polys1) area1 += poly.getArea();

			double area2 = 0;
			NodeInst ni2 = NodeInst.makeDummyInstance(pn2, ep);
			Poly[] polys2 = pn1.getTechnology().getShapeOfNode(ni2);
			for(Poly poly : polys2) area2 += poly.getArea();

			if (area1 < area2) return 1;
			if (area1 > area2) return -1;
			return 0;
		}
	}

	private void findTransistors(List<PrimitiveNode> transistors, Layer activeLayer, PolyMerge merge,
		PolyMerge originalMerge, Cell newCell, boolean usePureLayerNodes)
	{
		originalMerge.intersectLayers(polyLayer, activeLayer, tempLayer1);
		List<PolyBase> polyList = getMergePolys(originalMerge, tempLayer1, null);
		if (polyList != null)
		{
			for(PolyBase poly : polyList)
			{
				// look at all of the pieces of this layer
				Rectangle2D transBox = poly.getBox();
				double cX = poly.getCenterX(), cY = poly.getCenterY();
				if (alignment != null)
				{
                    // centers can be off-grid by a factor of 2, because only the edges matter for off-grid
					cX = Math.round(cX / scaleUp(alignment.getWidth()/2)) * scaleUp(alignment.getWidth()/2);
					cY = Math.round(cY / scaleUp(alignment.getHeight()/2)) * scaleUp(alignment.getHeight()/2);
				}
				if (transBox == null)
				{
					// complex polygon: extract angled or serpentine transistor
					for(PrimitiveNode transistor : transistors)
					{
						if (extractNonManhattanTransistor(poly, transistor, merge, originalMerge, newCell, usePureLayerNodes)) break;
					}
				} else
				{
					EPoint ctr = EPoint.fromLambda(cX/SCALEFACTOR, cY/SCALEFACTOR);
					Map<PrimitiveNode,Map<Layer,Rectangle2D>> errorInfo = new HashMap<PrimitiveNode,Map<Layer,Rectangle2D>>();
					for(PrimitiveNode transistor : transistors)
					{
						// figure out which way the poly runs in the desired transistor
						NodeInst dni = NodeInst.makeDummyInstance(transistor, ep);
						Poly [] polys = transistor.getTechnology().getShapeOfNode(dni);
						double widestPoly = 0, widestActive = 0;
						for(int i=0; i<polys.length; i++)
						{
							Poly p = polys[i];
							Rectangle2D bounds = p.getBounds2D();
							if (p.getLayer().getFunction().isPoly()) widestPoly = Math.max(widestPoly, bounds.getWidth());
							if (p.getLayer().getFunction().isDiff()) widestActive = Math.max(widestActive, bounds.getWidth());
						}
						boolean polyVertical = widestPoly < widestActive;

						// found a Manhattan transistor, determine orientation
						Rectangle2D left = new Rectangle2D.Double(transBox.getMinX() - 1, transBox.getMinY(), 1, transBox.getHeight());
						Rectangle2D right = new Rectangle2D.Double(transBox.getMaxX(), transBox.getMinY(), 1, transBox.getHeight());
						Rectangle2D bottom = new Rectangle2D.Double(transBox.getMinX(), transBox.getMinY() - 1, transBox.getWidth(), 1);
						Rectangle2D top = new Rectangle2D.Double(transBox.getMinX(), transBox.getMaxY(), transBox.getWidth(), 1);
						if (polyVertical)
						{
							Rectangle2D swap = left;   left = top;   top = right;   right = bottom;   bottom = swap;
						}
						int angle = 0;
						double wid = transBox.getWidth();
						double hei = transBox.getHeight();
						boolean unrotLeft = originalMerge.contains(polyLayer, left);
						boolean unrotRight = originalMerge.contains(polyLayer, right);
						boolean unrotTop = originalMerge.contains(activeLayer, top);
						boolean unrotBottom = originalMerge.contains(activeLayer, bottom);

						boolean rotLeft = originalMerge.contains(activeLayer, left);
						boolean rotRight = originalMerge.contains(activeLayer, right);
						boolean rotTop = originalMerge.contains(polyLayer, top);
						boolean rotBottom = originalMerge.contains(polyLayer, bottom);
						if (unrotLeft && unrotRight && unrotTop && unrotBottom)
						{
						} else if (rotLeft && rotRight && rotTop && rotBottom)
						{
							angle = 900;
							wid = transBox.getHeight();
							hei = transBox.getWidth();
						} else
						{
							int unrotGood = (unrotLeft?1:0) + (unrotRight?1:0) + (unrotTop?1:0) + (unrotBottom?1:0);
							int rotGood = (rotLeft?1:0) + (rotRight?1:0) + (rotTop?1:0) + (rotBottom?1:0);
							Map<Layer,Rectangle2D> transistorErrors = errorInfo.get(transistor);
							if (transistorErrors == null) errorInfo.put(transistor, transistorErrors =  new HashMap<Layer,Rectangle2D>());
							if (unrotGood <= rotGood)
							{
								if (unrotLeft) transistorErrors.put(polyLayer, left);
								if (unrotRight) transistorErrors.put(polyLayer, right);
								if (unrotTop) transistorErrors.put(activeLayer, top);
								if (unrotBottom) transistorErrors.put(activeLayer, bottom);
							} else
							{
								if (rotLeft) transistorErrors.put(activeLayer, left);
								if (rotRight) transistorErrors.put(activeLayer, right);
								if (rotTop) transistorErrors.put(polyLayer, top);
								if (rotBottom) transistorErrors.put(polyLayer, bottom);
							}
							continue;
						}

						/*
						 * Make sure all layers fit.
						 * This calculation presumes that the highlight box of the transistor primitive
						 * is exactly the area where polysilicon and active cross.
						 * If that is not the case, then the wrong size transistor will be proposed for this geometry
						 * and it will not fit.
						 */
						SizeOffset so = transistor.getProtoSizeOffset();
                        double width = wid + scaleUp(so.getLowXOffset() + so.getHighXOffset());
                        double height = hei + scaleUp(so.getLowYOffset() + so.getHighYOffset());

                        // if transistor fits, use it
                        if (doesTransistorFit(transistor, ctr, width, height, angle, errorInfo, originalMerge))
                        {
    						realizeNode(transistor, Technology.NodeLayer.MULTICUT_CENTERED, cX, cY,
    							width, height, angle, null, merge, newCell, null, usePureLayerNodes);
    						errorInfo.clear();
    						break;
                        }

                        // try the transistor flipped
                        if (doesTransistorFit(transistor, ctr, width, height, angle+1800, errorInfo, originalMerge))
                        {
    						realizeNode(transistor, Technology.NodeLayer.MULTICUT_CENTERED, cX, cY,
    							width, height, angle+1800, null, merge, newCell, null, usePureLayerNodes);
    						errorInfo.clear();
    						break;
                        }
					}
					explainTransistorFailure("Transistor", errorInfo, newCell, ctr);
				}
			}
		}
		originalMerge.deleteLayer(tempLayer1);
	}

	private void explainTransistorFailure(String what, Map<PrimitiveNode,Map<Layer,Rectangle2D>> errorInfo, Cell newCell, EPoint ctr)
	{
		if (errorInfo.size() > 0)
		{
			String eMsg = "Cell " + newCell.describe(false) + ": Did not extract " + what + " at (" +
				TextUtils.formatDistance(ctr.getX()) + "," + TextUtils.formatDistance(ctr.getY()) + "). Here is why:";

			// see if select/well layers failed universally
			boolean missingSubstrate = true;
			for(PrimitiveNode pnp : errorInfo.keySet())
			{
				Map<Layer,Rectangle2D> primErrorInfo = errorInfo.get(pnp);
				boolean substrateHere = false;
				for(Layer lay : primErrorInfo.keySet())
				{
					if (lay.getFunction().isSubstrate()) substrateHere = true;
				}
				if (!substrateHere) missingSubstrate = false;
			}
			if (missingSubstrate)
			{
				eMsg += "\n    There are no well or select layers (if they are in lower-levels of hierarchy, use Network Preferences to expand them)";
                addErrorLog(newCell, eMsg, EPoint.fromLambda(ctr.getX(), ctr.getY()));
			} else
			{
				// not a well/select problem: look for smallest failure
				double smallestArea = Double.MAX_VALUE;
				for(PrimitiveNode pnp : errorInfo.keySet())
				{
					Map<Layer,Rectangle2D> primErrorInfo = errorInfo.get(pnp);
					eMsg += "\n    For " + pnp.describe(false) + ", needs:";
					boolean saidOne = false;
					for(Layer lay : primErrorInfo.keySet())
					{
						Rectangle2D errRect = primErrorInfo.get(lay);
						if (saidOne) eMsg += " and";
						eMsg += " layer " + lay.getName() + " at " +
							TextUtils.formatDistance(errRect.getMinX()/SCALEFACTOR) + "<=X<=" +
							TextUtils.formatDistance(errRect.getMaxX()/SCALEFACTOR) + " / " +
							TextUtils.formatDistance(errRect.getMinY()/SCALEFACTOR) + "<=Y<=" +
							TextUtils.formatDistance(errRect.getMaxY()/SCALEFACTOR);
						saidOne = true;
						double rectSize = errRect.getWidth() * errRect.getHeight();
						if (rectSize < smallestArea)
							smallestArea = rectSize;
					}
				}
                addErrorLog(newCell, eMsg, EPoint.fromLambda(ctr.getX(), ctr.getY()));
			}
		}
	}

	/**
	 * Method to tell whether a proposed transistor fits in the merge.
	 * @param transistor the transistor prototype.
	 * @param ctr the center coordinate of the transistor.
	 * @param width the transistor width.
	 * @param height the transistor height.
	 * @param angle the angle of the transistor placement (in tenths of a degree).
	 * @param errorInfo place to store error information when a layer doesn't fit.
	 * @param originalMerge the geometry that is being extracted.
	 * @return true if the transistor fits in the geometry.
	 */
	private boolean doesTransistorFit(PrimitiveNode transistor, EPoint ctr, double width, double height, int angle,
		Map<PrimitiveNode,Map<Layer,Rectangle2D>> errorInfo, PolyMerge originalMerge)
	{
		// make the transistor
		NodeInst ni = makeDummyNodeInst(transistor, ctr, width, height,
			Orientation.fromAngle(angle), Technology.NodeLayer.MULTICUT_CENTERED);

		// see if transistor fits here
		Map<Layer,Rectangle2D> primErrorInfo = new HashMap<Layer,Rectangle2D>();
		errorInfo.put(transistor, primErrorInfo);

		FixpTransform trans = ni.rotateOut();
		Technology tech = ni.getProto().getTechnology();
		Poly [] tPolys = tech.getShapeOfNode(ni);
		for(Poly tPoly : tPolys)
		{
			Layer l = tPoly.getLayer();
			if (l == null) continue;
			l = geometricLayer(l);
			Layer.Function fun = l.getFunction();
			if (fun == Layer.Function.WELLP && pSubstrateProcess) continue;
			if (fun == Layer.Function.WELLN && nSubstrateProcess) continue;
			tPoly.setLayer(l);
			tPoly.transform(trans);
//FixpRectangle fpr = tPoly.getBounds2D();
//System.out.println("TRANSISTOR "+transistor.describe(false)+" HAS LAYER "+l.getName()+" FROM "+
//fpr.getMinX()+"<=X<="+fpr.getMaxX()+" AND "+fpr.getMinY()+"<=Y<="+fpr.getMaxY());
			Point2D[] points = tPoly.getPoints();
			for(int i=0; i<points.length; i++)
				tPoly.setPoint(i, scaleUp(points[i].getX()), scaleUp(points[i].getY()));
			if (!originalMerge.contains(l, tPoly))
			{
				if (tPoly != null)
				{
					Rectangle2D bounds = tPoly.getBounds2D();
					primErrorInfo.put(l, bounds);
				}
			}
		}
		if (primErrorInfo.size() > 0) return false;
		return true;
	}

	/**
	 * Method to extract a transistor from a non-Manhattan polygon that defines the intersection of poly and active.
	 * @param poly the outline of poly/active to extract.
	 * @param transistor the type of transistor to create.
	 * @param merge the geometry collection to adjust when a transistor is extracted.
	 * @param originalMerge the original geometry collection (for examination).
	 * @param newCell the cell in which to create the extracted transistor
	 * @return true if it did extract a transistor.
	 */
	private boolean extractNonManhattanTransistor(PolyBase poly, PrimitiveNode transistor, PolyMerge merge,
		PolyMerge originalMerge, Cell newCell, boolean usePureLayerNodes)
	{
		// determine minimum width of polysilicon
		SizeOffset so = transistor.getProtoSizeOffset();
		double minWidth = transistor.getDefHeight(ep) - so.getLowYOffset() - so.getHighYOffset();

		// reduce the geometry to a skeleton of centerlines
		List<Centerline> lines = findCenterlines(poly, tempLayer1, minWidth, merge, originalMerge);
		if (lines.size() == 0) return false;

		// if just one line, it is simply an angled transistor
		if (lines.size() == 1)
		{
			Centerline cl = lines.get(0);
			double polySize = cl.start.distance(cl.end);
			double activeSize = cl.width;
			double cX = (cl.start.getX() + cl.end.getX()) / 2;
			double cY = (cl.start.getY() + cl.end.getY()) / 2;
			double sX = polySize + scaleUp(so.getLowXOffset() + so.getHighXOffset());
			double sY = activeSize + scaleUp(so.getLowYOffset() + so.getHighYOffset());
			realizeNode(transistor, Technology.NodeLayer.MULTICUT_CENTERED, cX, cY, sX, sY,
				cl.angle, null, merge, newCell, null, usePureLayerNodes);
			return true;
		}

		// serpentine transistor: organize the lines into an array of points
		EPoint [] points = new EPoint[lines.size()+1];
		for(Centerline cl : lines)
		{
			cl.handled = false;
		}
		Centerline firstCL = lines.get(0);
		firstCL.handled = true;
		points[0] = EPoint.fromLambda(firstCL.start.getX(), firstCL.start.getY());
		points[1] = EPoint.fromLambda(firstCL.end.getX(), firstCL.end.getY());
		int pointsSeen = 2;
		while (pointsSeen < points.length)
		{
			boolean added = false;
			for(Centerline cl : lines)
			{
				if (cl.handled) continue;
				EPoint start = EPoint.fromLambda(cl.start.getX(), cl.start.getY());
				EPoint end = EPoint.fromLambda(cl.end.getX(), cl.end.getY());
				if (start.equals(points[0]))
				{
					// insert "end" point at start
					for(int i=pointsSeen; i>0; i--)
						points[i] = points[i-1];
					points[0] = end;
					pointsSeen++;
					cl.handled = true;
					added = true;
					break;
				}
				if (end.equals(points[0]))
				{
					// insert "start" point at start
					for(int i=pointsSeen; i>0; i--)
						points[i] = points[i-1];
					points[0] = start;
					pointsSeen++;
					cl.handled = true;
					added = true;
					break;
				}
				if (start.equals(points[pointsSeen-1]))
				{
					// add "end" at the end
					points[pointsSeen++] = end;
					cl.handled = true;
					added = true;
					break;
				}
				if (end.equals(points[pointsSeen-1]))
				{
					// add "start" at the end
					points[pointsSeen++] = start;
					cl.handled = true;
					added = true;
					break;
				}
			}
			if (!added) break;
		}

		// make sure all points are handled
		if (pointsSeen != points.length) return false;

		// compute information about the transistor and create it
		double lX = points[0].getX(), hX = points[0].getX();
		double lY = points[0].getY(), hY = points[0].getY();
		for(int i=1; i<points.length; i++)
		{
			if (points[i].getX() < lX) lX = points[i].getX();
			if (points[i].getX() > hX) hX = points[i].getX();
			if (points[i].getY() < lY) lY = points[i].getY();
			if (points[i].getY() > hY) hY = points[i].getY();
		}
		double cX = (lX + hX) / 2;
		double cY = (lY + hY) / 2;
		for(int i=0; i<points.length; i++)
			points[i] = EPoint.fromLambda((points[i].getX()) / SCALEFACTOR, (points[i].getY()) / SCALEFACTOR);

		// make sure the transistor fits
		boolean fits = true;
		NodeInst ni = makeDummyNodeInst(transistor, EPoint.fromLambda(cX, cY), hX - lX, hY - lY,
			Orientation.IDENT, 0);
		ni.setTrace(points);
		FixpTransform trans = ni.rotateOut();
		Technology tech = ni.getProto().getTechnology();
		Poly [] tPolys = tech.getShapeOfNode(ni);
		for(Poly tPoly : tPolys)
		{
			Layer l = tPoly.getLayer();
			if (l == null) continue;
			l = geometricLayer(l);
			Layer.Function fun = l.getFunction();
			if (fun == Layer.Function.WELLP && pSubstrateProcess) continue;
			if (fun == Layer.Function.WELLN && nSubstrateProcess) continue;
			tPoly.setLayer(l);
			tPoly.transform(trans);
			Point2D[] pts = tPoly.getPoints();
			for(int i=0; i<pts.length; i++)
				tPoly.setPoint(i, scaleUp(pts[i].getX()), scaleUp(pts[i].getY()));
			if (!originalMerge.contains(l, tPoly))
				fits = false;
		}
		if (fits)
		{
			realizeNode(transistor, Technology.NodeLayer.MULTICUT_CENTERED, cX, cY, hX - lX, hY - lY, 0,
				points, merge, newCell, null, usePureLayerNodes);
			return true;
		}
		return false;
	}

	/********************************************** CONVERT CONNECTING GEOMETRY **********************************************/

	/**
	 * Method to look for opportunities to place arcs that connect to existing geometry.
	 */
	private void extendGeometry(PolyMerge merge, PolyMerge originalMerge, Cell newCell, boolean justExtend, boolean usePureLayerNodes)
	{
		// make a list of layers that can be extended
		List<Layer> extendableLayers = new ArrayList<Layer>();
		for (Layer layer : merge.getKeySet())
		{
			if (layer.getFunctionExtras() == Layer.Function.INTERCONNECT) continue;
			ArcProto ap = arcsForLayer.get(layer);
			if (ap == null) continue;
			extendableLayers.add(layer);
		}

		// gather everything to extend
		int totExtensions = 0;
		Map<Layer,List<PolyBase>> geomToExtend = new HashMap<Layer,List<PolyBase>>();
		int soFar = 0;
		for (Layer layer : extendableLayers)
		{
			List<PolyBase> polyList = getMergePolys(merge, layer, null);
			geomToExtend.put(layer, polyList);
			totExtensions += polyList.size();
			soFar++;
			if (!recursive) Job.getUserInterface().setProgressValue(soFar * 100 / extendableLayers.size());
		}
		if (!recursive) Job.getUserInterface().setProgressValue(0);

		soFar = 0;
		for (Layer layer : extendableLayers)
		{
			ArcProto ap = arcsForLayer.get(layer);
			if (ap == null) continue;
			double wid = ap.getDefaultLambdaBaseWidth(ep);
			int index = ap.indexOf(layer);
			if (index == -1) continue; // something is wrong with the layer.
			
			double arcLayerWidth = ap.getDefaultInst(ep).getCoordExtendOverMin().add(ap.getLayerExtend(layer)).multiply(2).getLambda();
			List<PolyBase> polyList = geomToExtend.get(layer);
			for(PolyBase poly : polyList)
			{
				soFar++;
				if (!recursive) Job.getUserInterface().setProgressValue(soFar * 100 / totExtensions);

				// find out what this polygon touches
				Map<Network,Object> netsThatTouch = getNetsThatTouch(poly, newCell, justExtend);
				if (netsThatTouch == null) continue;

				// make a list of port/arc ends that touch this polygon
				List<Object> objectsToConnect = new ArrayList<Object>();
				for(Network net : netsThatTouch.keySet())
				{
					Object entry = netsThatTouch.get(net);
					if (entry != null) objectsToConnect.add(entry);
				}

				// if only 1 object touches the polygon, see if it can be "wired" to cover
				if (objectsToConnect.size() == 1)
				{
					// touches just 1 object: see if that object can be extended to cover the polygon
					extendObject((ElectricObject)objectsToConnect.get(0), poly, layer, ap, merge, originalMerge, newCell, usePureLayerNodes);
					continue;
				}

				// if two objects touch the polygon, see if an arc can connect them
				if (!justExtend && objectsToConnect.size() == 2)
				{
					ElectricObject obj1 = (ElectricObject)objectsToConnect.get(0);
					ElectricObject obj2 = (ElectricObject)objectsToConnect.get(1);
					if (obj1 instanceof ArcInst)
					{
						PortInst pi = findArcEnd((ArcInst)obj1, poly);
						if (pi == null)
						{
							findArcEnd((ArcInst)obj1, poly);
							continue;
						}
						obj1 = pi;
					}
					if (obj2 instanceof ArcInst)
					{
						PortInst pi = findArcEnd((ArcInst)obj2, poly);
						if (pi == null)
						{
							findArcEnd((ArcInst)obj2, poly);
							continue;
						}
						obj2 = pi;
					}
					PortInst pi1 = (PortInst)obj1;
					PortInst pi2 = (PortInst)obj2;

					// see if the ports can connect in a line
					Poly poly1 = pi1.getPoly();
					Poly poly2 = pi2.getPoly();
					Rectangle2D polyBounds1 = poly1.getBounds2D();
					Rectangle2D polyBounds2 = poly2.getBounds2D();
					if (polyBounds1.getMinX() <= polyBounds2.getMaxX() && polyBounds1.getMaxX() >= polyBounds2.getMinX())
					{
						// vertical connection
						double xpos = polyBounds1.getCenterX();
						if (xpos < polyBounds2.getMinX()) xpos = polyBounds2.getMinX();
						if (xpos > polyBounds2.getMaxX()) xpos = polyBounds2.getMaxX();
						if (alignment != null)
						{
							xpos = Math.round(xpos / scaleUp(alignment.getWidth())) * scaleUp(alignment.getWidth());
							if (xpos < polyBounds2.getMinX() || xpos > polyBounds2.getMaxX()) continue;
						}
						Point2D pt1 = new Point2D.Double(xpos, polyBounds1.getCenterY());
						Point2D pt2 = new Point2D.Double(xpos, polyBounds2.getCenterY());

						MutableBoolean headExtend = new MutableBoolean(true), tailExtend = new MutableBoolean(true);
						originalMerge.arcPolyFits(layer, pt1, pt2, arcLayerWidth, headExtend, tailExtend);
						realizeArc(ap, pi1, pi2, pt1, pt2, wid, !headExtend.booleanValue(), !tailExtend.booleanValue(), usePureLayerNodes, merge);
						continue;
					}
					if (polyBounds1.getMinY() <= polyBounds2.getMaxY() && polyBounds1.getMaxY() >= polyBounds2.getMinY())
					{
						// horizontal connection
						double ypos = polyBounds1.getCenterY();
						if (ypos < polyBounds2.getMinY()) ypos = polyBounds2.getMinY();
						if (ypos > polyBounds2.getMaxY()) ypos = polyBounds2.getMaxY();
						if (alignment != null)
						{
							ypos = Math.round(ypos / scaleUp(alignment.getHeight())) * scaleUp(alignment.getHeight());
							if (ypos < polyBounds2.getMinY() || ypos > polyBounds2.getMaxY()) continue;
						}
						Point2D pt1 = new Point2D.Double(polyBounds1.getCenterX(), ypos);
						Point2D pt2 = new Point2D.Double(polyBounds2.getCenterX(), ypos);

						MutableBoolean headExtend = new MutableBoolean(true), tailExtend = new MutableBoolean(true);
						originalMerge.arcPolyFits(layer, pt1, pt2, arcLayerWidth, headExtend, tailExtend);
						realizeArc(ap, pi1, pi2, pt1, pt2, wid, !headExtend.booleanValue(), !tailExtend.booleanValue(), usePureLayerNodes, merge);
						continue;
					}

					// see if a bend can be made through the polygon
					Point2D pt1 = new Point2D.Double(polyBounds1.getCenterX(), polyBounds1.getCenterY());
					Point2D pt2 = new Point2D.Double(polyBounds2.getCenterX(), polyBounds2.getCenterY());
					Point2D corner1 = new Point2D.Double(polyBounds1.getCenterX(), polyBounds2.getCenterY());
					Point2D corner2 = new Point2D.Double(polyBounds2.getCenterX(), polyBounds1.getCenterY());
					Point2D containsIt = null;
					if (poly.contains(corner1)) containsIt = corner1; else
						if (poly.contains(corner2)) containsIt = corner2;
					if (containsIt != null)
					{
						PrimitiveNode np = ap.findPinProto();
						NodeInst ni = createNode(np, containsIt, np.getDefWidth(ep), np.getDefHeight(ep), null, newCell);
						PortInst pi = ni.getOnlyPortInst();

						MutableBoolean headExtend = new MutableBoolean(true), tailExtend = new MutableBoolean(true);
						originalMerge.arcPolyFits(layer, pt1, containsIt, arcLayerWidth, headExtend, tailExtend);
						realizeArc(ap, pi1, pi, pt1, containsIt, wid, !headExtend.booleanValue(), !tailExtend.booleanValue(), usePureLayerNodes, merge);

						headExtend.setValue(true);   tailExtend.setValue(true);
						originalMerge.arcPolyFits(layer, pt2, containsIt, arcLayerWidth, headExtend, tailExtend);
						realizeArc(ap, pi2, pi, pt2, containsIt, wid, !headExtend.booleanValue(), !tailExtend.booleanValue(), usePureLayerNodes, merge);
					}
				}
			}
		}
	}

	/**
	 * Method to see if a polygon can be covered by adding an arc to an Arc or Port.
	 * @param obj the object that is being extended (either an ArcInst or a PortInst).
	 * @param poly the polygon that is being covered.
	 * @param layer the layer of the polygon.
	 * @param ap the ArcProto to use when covering the polygon.
	 * @param merge the merge area being replaced.
	 * @param originalMerge the original merge area being covered.
	 * @param newCell the Cell in which to place the new arc.
	 */
	private void extendObject(ElectricObject obj, PolyBase poly, Layer layer, ArcProto ap, PolyMerge merge,
		PolyMerge originalMerge, Cell newCell, boolean usePureLayerNodes)
	{
		// can only handle rectangles now
		Rectangle2D polyBounds = poly.getBox();
		if (polyBounds == null)
		{
			Rectangle2D totalBounds = poly.getBounds2D();
			if (originalMerge.contains(layer, totalBounds))
				polyBounds = totalBounds;
		}
		if (polyBounds == null) return;

		// find the port that is being extended
		Point2D polyCtr = new Point2D.Double(polyBounds.getCenterX(), polyBounds.getCenterY());
		if (alignment != null)
		{
			double x = polyCtr.getX();
			double y = polyCtr.getY();
			x = Math.round(x / scaleUp(alignment.getWidth())) * scaleUp(alignment.getWidth());
			y = Math.round(y / scaleUp(alignment.getHeight())) * scaleUp(alignment.getHeight());
			polyCtr.setLocation(x, y);
		}
		if (obj instanceof ArcInst)
		{
			ArcInst ai = (ArcInst)obj;
			double headDist = polyCtr.distance(ai.getHeadLocation());
			double tailDist = polyCtr.distance(ai.getTailLocation());
			if (headDist < tailDist) obj = ai.getHeadPortInst(); else
				obj = ai.getTailPortInst();
		}
		PortInst pi = (PortInst)obj;

		// prepare to cover the polygon
		Poly portPoly = pi.getPoly();
		Rectangle2D portRect = portPoly.getBounds2D();
		portRect.setRect(scaleUp(portRect.getMinX()), scaleUp(portRect.getMinY()),
			scaleUp(portRect.getWidth()), scaleUp(portRect.getHeight()));
		PrimitiveNode np = ap.findPinProto();

		// is the port inside of the area to be covered?
		if (polyCtr.getY() >= portRect.getMinY() && polyCtr.getY() <= portRect.getMaxY() &&
			polyCtr.getX() >= portRect.getMinX() && polyCtr.getX() <= portRect.getMaxX())
		{
			// decide whether to extend horizontally or vertically
			if (polyBounds.getWidth() > polyBounds.getHeight())
			{
				// wider area, make horizontal arcs
				double endExtension = polyBounds.getHeight() / 2;
				Point2D pinPt1 = new Point2D.Double((polyBounds.getMaxX() - endExtension) / SCALEFACTOR,
					polyCtr.getY() / SCALEFACTOR);
				Point2D pinPt2 = new Point2D.Double((polyBounds.getMinX() + endExtension) / SCALEFACTOR,
					polyCtr.getY() / SCALEFACTOR);
				Point2D objPt = new Point2D.Double(portRect.getCenterX() / SCALEFACTOR, polyCtr.getY() / SCALEFACTOR);
				double size = Math.min(polyBounds.getWidth(), polyBounds.getHeight()) / SCALEFACTOR;
				NodeInst ni1 = createNode(np, pinPt1, size, size, null, newCell);
				NodeInst ni2 = createNode(np, pinPt2, size, size, null, newCell);
				realizeArc(ap, ni1.getOnlyPortInst(), pi, pinPt1, objPt,
					polyBounds.getHeight() / SCALEFACTOR, false, false, usePureLayerNodes, merge);
				realizeArc(ap, ni2.getOnlyPortInst(), pi, pinPt2, objPt,
					polyBounds.getHeight() / SCALEFACTOR, false, false, usePureLayerNodes, merge);
			} else
			{
				// taller area, make vertical arcs
				double endExtension = polyBounds.getWidth() / 2;
				Point2D pinPt1 = new Point2D.Double(polyCtr.getX() / SCALEFACTOR,
					(polyBounds.getMaxY() - endExtension) / SCALEFACTOR);
				Point2D pinPt2 = new Point2D.Double(polyCtr.getX() / SCALEFACTOR,
					(polyBounds.getMinY() + endExtension) / SCALEFACTOR);
				Point2D objPt = new Point2D.Double(polyCtr.getX() / SCALEFACTOR, portRect.getCenterY() / SCALEFACTOR);
				double size = Math.min(polyBounds.getWidth(), polyBounds.getHeight()) / SCALEFACTOR;
				NodeInst ni1 = createNode(np, pinPt1, size, size, null, newCell);
				NodeInst ni2 = createNode(np, pinPt2, size, size, null, newCell);
				realizeArc(ap, ni1.getOnlyPortInst(), pi, pinPt1, objPt,
					polyBounds.getWidth() / SCALEFACTOR, false, false, usePureLayerNodes, merge);
				realizeArc(ap, ni2.getOnlyPortInst(), pi, pinPt2, objPt,
					polyBounds.getWidth() / SCALEFACTOR, false, false, usePureLayerNodes, merge);
			}
			return;
		}

		// can we extend vertically
		if (polyCtr.getX() >= portRect.getMinX() && polyCtr.getX() <= portRect.getMaxX())
		{
			// going up to the poly or down?
			Point2D objPt = new Point2D.Double(polyCtr.getX(), portRect.getCenterY());
			Point2D objPtNormal = new Point2D.Double(polyCtr.getX() / SCALEFACTOR, portRect.getCenterY() / SCALEFACTOR);
			Point2D pinPt = null;
			Point2D pinPtNormal = null;
			boolean endExtend = true;
			double endExtension = polyBounds.getWidth() / 2;
			if (polyBounds.getHeight() < polyBounds.getWidth())
			{
				// arc is so short that it will stick out with end extension
				endExtend = false;
				endExtension = 0;
			}
			if (polyCtr.getY() > portRect.getCenterY())
			{
				// going up to the poly
				pinPt = new Point2D.Double(polyCtr.getX(), polyBounds.getMaxY() - endExtension);
				pinPtNormal = new Point2D.Double(polyCtr.getX() / SCALEFACTOR,
					(polyBounds.getMaxY() - endExtension) / SCALEFACTOR);
			} else
			{
				// going down to the poly
				pinPt = new Point2D.Double(polyCtr.getX(), polyBounds.getMinY() + endExtension);
				pinPtNormal = new Point2D.Double(polyCtr.getX() / SCALEFACTOR,
					(polyBounds.getMinY() + endExtension) / SCALEFACTOR);
			}
			MutableBoolean headExtend = new MutableBoolean(endExtend), tailExtend = new MutableBoolean(endExtend);
			double wid = polyBounds.getWidth();
			if (originalMerge.arcPolyFits(layer, pinPt, objPt, wid, headExtend, tailExtend))
			{
				double size = Math.min(polyBounds.getWidth(), polyBounds.getHeight()) / SCALEFACTOR;
				NodeInst ni1 = createNode(np, pinPtNormal, size, size, null, newCell);
				realizeArc(ap, ni1.getOnlyPortInst(), pi, pinPtNormal, objPtNormal, wid / SCALEFACTOR,
					!headExtend.booleanValue(), !tailExtend.booleanValue(), usePureLayerNodes, merge);
			}
			return;
		}

		// can we extend horizontally
		if (polyCtr.getY() >= portRect.getMinY() && polyCtr.getY() <= portRect.getMaxY())
		{
			// going left to the poly or right?
			Point2D objPt = new Point2D.Double(portRect.getCenterX(), polyCtr.getY());
			Point2D objPtNormal = new Point2D.Double(portRect.getCenterX() / SCALEFACTOR, polyCtr.getY() / SCALEFACTOR);
			Point2D pinPt = null;
			Point2D pinPtNormal = null;
			boolean endExtend = true;
			double endExtension = polyBounds.getHeight() / 2;
			if (polyBounds.getWidth() < polyBounds.getHeight())
			{
				// arc is so short that it will stick out with end extension
				endExtend = false;
				endExtension = 0;
			}
			if (polyCtr.getX() > portRect.getCenterX())
			{
				// going right to the poly
				pinPt = new Point2D.Double(polyBounds.getMaxX() - endExtension, polyCtr.getY());
				pinPtNormal = new Point2D.Double((polyBounds.getMaxX() - endExtension) / SCALEFACTOR,
					polyCtr.getY() / SCALEFACTOR);
			} else
			{
				// going left to the poly
				pinPt = new Point2D.Double(polyBounds.getMinX() + endExtension, polyCtr.getY());
				pinPtNormal = new Point2D.Double((polyBounds.getMinX() + endExtension) / SCALEFACTOR,
					polyCtr.getY() / SCALEFACTOR);
			}
			MutableBoolean headExtend = new MutableBoolean(endExtend), tailExtend = new MutableBoolean(endExtend);
			double wid = polyBounds.getHeight();
			if (originalMerge.arcPolyFits(layer, pinPt, objPt, wid, headExtend, tailExtend))
			{
				double size = Math.min(polyBounds.getWidth(), polyBounds.getHeight()) / SCALEFACTOR;
				NodeInst ni1 = createNode(np, pinPtNormal, size, size, null, newCell);
				realizeArc(ap, ni1.getOnlyPortInst(), pi, pinPtNormal, objPtNormal,
					wid / SCALEFACTOR, !headExtend.booleanValue(), !tailExtend.booleanValue(), usePureLayerNodes, merge);
			}
		}
	}

	private PortInst findArcEnd(ArcInst ai, PolyBase poly)
	{
		// see if one end of the arc touches the poly
		Point2D head = ai.getHeadLocation();
		Point2D tail = ai.getTailLocation();
		int ang = GenMath.figureAngle(tail, head);
		int angPlus = (ang + 900) % 3600;
		double width = ai.getLambdaBaseWidth() / 2;

		// see if the head end touches
		Point2D headButFarther = new Point2D.Double(head.getX() + width * GenMath.cos(ang), head.getY() + width * GenMath.sin(ang));
		if (poly.contains(headButFarther)) return ai.getHeadPortInst();
		Point2D headOneSide = new Point2D.Double(head.getX() + width * GenMath.cos(angPlus), head.getY() + width * GenMath.sin(angPlus));
		if (poly.contains(headOneSide)) return ai.getHeadPortInst();
		Point2D headOtherSide = new Point2D.Double(head.getX() + width * GenMath.cos(angPlus), head.getY() + width * GenMath.sin(angPlus));
		if (poly.contains(headOtherSide)) return ai.getHeadPortInst();

		// see if the tail end touches
		Point2D tailButFarther = new Point2D.Double(tail.getX() - width * GenMath.cos(ang), tail.getY() - width * GenMath.sin(ang));
		if (poly.contains(tailButFarther)) return ai.getTailPortInst();
		Point2D tailOneSide = new Point2D.Double(tail.getX() - width * GenMath.cos(angPlus), tail.getY() - width * GenMath.sin(angPlus));
		if (poly.contains(tailOneSide)) return ai.getTailPortInst();
		Point2D tailOtherSide = new Point2D.Double(tail.getX() - width * GenMath.cos(angPlus), tail.getY() - width * GenMath.sin(angPlus));
		if (poly.contains(tailOtherSide)) return ai.getTailPortInst();

		return null;
	}

	private boolean polysTouch(PolyBase poly1, PolyBase poly2)
	{
		Point2D [] points1 = poly1.getPoints();
		Point2D [] points2 = poly2.getPoints();
		if (points1.length > points2.length)
		{
			Point2D [] swapPts = points1;   points1 = points2;   points2 = swapPts;
			PolyBase swapPoly = poly1;   poly1 = poly2;   poly2 = swapPoly;
		}

		// check every vertex in poly1 to see if any are in poly2
		for(int i=0; i<points1.length; i++)
			if (poly2.contains(points1[i])) return true;

		// check every midpoint in poly1 to see if any are in poly2
		for(int i=0; i<points1.length; i++)
		{
			int last = i-1;
			if (last < 0) last = points1.length-1;
			Point2D midPoint = new Point2D.Double((points1[last].getX() + points1[i].getX()) / 2,
				(points1[last].getY() + points1[i].getY()) / 2);
			if (poly2.contains(midPoint)) return true;
		}
		return false;
	}

	/**
	 * Method to build a map of networks and objects that touch a polygon.
	 * @param poly the polygon to analyze
	 * @param newCell the cell in which to search.
	 * @param justExtend true to insist on just 1 net; false to allow 2 nets also.
	 * @return the map of networks (null if too many nets).
	 */
	private Map<Network,Object> getNetsThatTouch(PolyBase poly, Cell newCell, boolean justExtend)
	{
		// scale the polygon back
		Point2D [] points = poly.getPoints();
		PolyBase.Point [] newPoints = new PolyBase.Point[points.length];
		for(int i=0; i<points.length; i++)
			newPoints[i] = PolyBase.fromLambda(points[i].getX() / SCALEFACTOR, points[i].getY() / SCALEFACTOR);
		PolyBase newPoly = new PolyBase(newPoints);
		Layer layer = poly.getLayer();

		// make a map of networks that touch the polygon, and the objects on them
		TreeMap<Network,Object> netsThatTouch = new TreeMap<Network,Object>();

		// find nodes that touch
		Rectangle2D bounds = newPoly.getBounds2D();
		Point2D centerPoint = new Point2D.Double(bounds.getCenterX(), bounds.getCenterY());
		Netlist nl = newCell.getNetlist();
		for(Iterator<Geometric> it = newCell.searchIterator(bounds); it.hasNext(); )
		{
			Geometric geom = it.next();
			if (!(geom instanceof NodeInst)) continue;
			NodeInst ni = (NodeInst)geom;
			if (ni.isCellInstance()) continue;
			FixpTransform trans = ni.rotateOut();
			Technology tech = ni.getProto().getTechnology();
			Poly [] nodePolys = tech.getShapeOfNode(ni, true, true, null);
			for(int i=0; i<nodePolys.length; i++)
			{
				Poly nodePoly = nodePolys[i];
				if (geometricLayer(nodePoly.getLayer()) != layer) continue;
				nodePoly.transform(trans);
				if (polysTouch(nodePoly, newPoly))
				{
					// node touches the unconnected poly: get network information
					PrimitivePort pp = (PrimitivePort)nodePoly.getPort();
					if (pp == null) continue;
					PortInst pi = findPortInstClosestToPoly(ni, pp, centerPoint);
					Network net = nl.getNetwork(pi);
					if (net != null)
					{
						netsThatTouch.put(net, pi);
						int numNets = netsThatTouch.size();
						if (numNets > 2 || (numNets > 1 && justExtend)) return null;
						break;
					}
				}
			}
		}

		// find arcs that touch (only include if no nodes are on the network)
		for(Iterator<Geometric> it = newCell.searchIterator(bounds); it.hasNext(); )
		{
			Geometric geom = it.next();
			if (!(geom instanceof ArcInst)) continue;
			ArcInst ai = (ArcInst)geom;
			Technology tech = ai.getProto().getTechnology();
			Poly [] polys = tech.getShapeOfArc(ai);
			for(int i=0; i<polys.length; i++)
			{
				Poly arcPoly = polys[i];
				if (geometricLayer(arcPoly.getLayer()) != layer) continue;
				if (polysTouch(arcPoly, newPoly))
				{
					Network net = nl.getNetwork(ai, 0);
					if (net != null)
					{
						if (netsThatTouch.get(net) == null) netsThatTouch.put(net, ai);
						int numNets = netsThatTouch.size();
						if (numNets > 2 || (numNets > 1 && justExtend)) return null;
						break;
					}
				}
			}
		}
		return netsThatTouch;
	}

	/********************************************** MISCELLANEOUS EXTRACTION HELPERS **********************************************/

	/**
	 * Method to find a list of centerlines that skeletonize a polygon.
	 * @param poly the Poly to skeletonize.
	 * @param layer the layer on which the polygon resides.
	 * @param minWidth the minimum width of geometry on the layer.
	 * @param merge
	 * @param originalMerge
	 * @return a List of Centerline objects that describe a single "bone" of the skeleton.
	 */
	private List<Centerline> findCenterlines(PolyBase poly, Layer layer, double minWidth, PolyMerge merge, PolyMerge originalMerge)
	{
		// the list of centerlines
		List<Centerline> validCenterlines = new ArrayList<Centerline>();

		// make a layer that describes the polygon
		merge.deleteLayer(tempLayer1);
		merge.addPolygon(tempLayer1, poly);

		List<PolyBase> polysToAnalyze = new ArrayList<PolyBase>();
		polysToAnalyze.add(poly);
		for(;;)
		{
			// decompose all polygons
			boolean foundNew = false;
			for(PolyBase aPoly : polysToAnalyze)
			{
				// first make a list of all parallel wires in the polygon
				aPoly.setLayer(layer);
				List<Centerline> centerlines = gatherCenterlines(aPoly, merge, originalMerge);
				if (centerlines == null)
				{
					merge.subtract(tempLayer1, aPoly);
					continue;
				}

				// now pull out the relevant ones
				double lastWidth = -1;
				boolean lastWidthNonManhattan = false;
				for(Centerline cl : centerlines)
				{
					if (cl.width < minWidth) continue;

//					if (alignment != null)
//					{
//						if (cl.start.getX() == cl.end.getX())
//						{
//							// vertical line: align in X
//							if (alignment.getWidth() > 0)
//							{
//								double aliX = scaleUp(alignment.getWidth());
//								double newX = Math.round(cl.start.getX() / aliX) * aliX;
//								cl.width -= Math.abs(newX - cl.start.getX());
//								cl.width = Math.floor(cl.width / aliX) * aliX;
//								cl.setStart(newX, cl.start.getY());
//								cl.setEnd(newX, cl.end.getY());
//							}
//						} else if (cl.start.getY() == cl.end.getY())
//						{
//							// horizontal line: align in Y
//							if (alignment.getHeight() > 0)
//							{
//								double aliY = scaleUp(alignment.getHeight());
//								double newY = Math.round(cl.start.getY() / aliY) * aliY;
//								cl.width -= Math.abs(newY - cl.start.getY());
//								cl.width = Math.floor(cl.width / aliY) * aliY;
//								cl.setStart(cl.start.getX(), newY);
//								cl.setEnd(cl.end.getX(), newY);
//							}
//						}
//					}

					// make the polygon to describe the centerline
					double length = cl.start.distance(cl.end);
					if (length < DBMath.getEpsilon()) continue;
					Poly clPoly = Poly.makeEndPointPoly(length, cl.width, cl.angle,
						cl.start, 0, cl.end, 0, Poly.Type.FILLED);

					// see if this centerline actually covers new area
					if (!merge.intersects(tempLayer1, clPoly)) continue;

					// if wider than long, this cannot be the first centerline
					if (validCenterlines.size() == 0)
					{
						//double len = cl.start.distance(cl.end);
						//if (cl.width > len) continue;
					}

					// for non-Manhattan centerlines, do extra work to ensure uniqueness
					boolean isNonManhattan = false;
					if (cl.startUnscaled.getX() != cl.endUnscaled.getX() &&
						cl.startUnscaled.getY() != cl.endUnscaled.getY())
					{
						boolean duplicate = false;
						for(Centerline oCl : validCenterlines)
						{
							if (cl.startUnscaled.equals(oCl.startUnscaled) && cl.endUnscaled.equals(oCl.endUnscaled))
							{
								duplicate = true;   break;
							}
							if (cl.startUnscaled.equals(oCl.endUnscaled) && cl.endUnscaled.equals(oCl.startUnscaled))
							{
								duplicate = true;   break;
							}
						}
						if (duplicate) continue;
						isNonManhattan = true;
					}

					// if narrower centerlines have already been added, stop now
					if (lastWidth < 0)
					{
						lastWidth = cl.width;
						lastWidthNonManhattan = isNonManhattan;
					}
					if (Math.abs(cl.width - lastWidth) > 1)
					{
						if (lastWidthNonManhattan != isNonManhattan) break;
						double smallest = Math.min(cl.width, lastWidth);
						if (smallest != 0 && Math.max(cl.width, lastWidth) / smallest > 1.2) break;
					}

					// add this to the list of valid centerlines
					validCenterlines.add(cl);
					merge.subtract(tempLayer1, clPoly);
					foundNew = true;
				}
			}
			if (!foundNew) break;

			// now analyze the remaining geometry in the polygon
			polysToAnalyze = getMergePolys(merge, tempLayer1, null);
			if (polysToAnalyze == null) break;
		}
		merge.deleteLayer(tempLayer1);

		if (DEBUGCENTERLINES)
		{
			System.out.println("MERGED:");
			for(int i=0; i<validCenterlines.size(); i++)
				System.out.println("    "+validCenterlines.get(i));
		}

        List<Centerline> extraCenterlines = new ArrayList<Centerline>();

        // now extend centerlines so they meet
		Centerline [] both = new Centerline[2];
		for(int i=0; i<validCenterlines.size(); i++)
		{
			Centerline cl = validCenterlines.get(i);
			double minCLX = Math.min(cl.start.getX(), cl.end.getX()) - cl.width;
			double maxCLX = Math.max(cl.start.getX(), cl.end.getX()) + cl.width;
			double minCLY = Math.min(cl.start.getY(), cl.end.getY()) - cl.width;
			double maxCLY = Math.max(cl.start.getY(), cl.end.getY()) + cl.width;
			for(int j=i+1; j<validCenterlines.size(); j++)
			{
				Centerline oCl = validCenterlines.get(j);
				double minOCLX = Math.min(oCl.start.getX(), oCl.end.getX()) - oCl.width;
				double maxOCLX = Math.max(oCl.start.getX(), oCl.end.getX()) + oCl.width;
				double minOCLY = Math.min(oCl.start.getY(), oCl.end.getY()) - oCl.width;
				double maxOCLY = Math.max(oCl.start.getY(), oCl.end.getY()) + oCl.width;
				if (minOCLX > maxCLX || maxOCLX < minCLX || minOCLY > maxCLY || maxOCLY < minCLY) continue;

				Point2D intersect = GenMath.intersect(cl.start, cl.angle, oCl.start, oCl.angle);
				if (intersect == null) continue;
				both[0] = cl;   both[1] = oCl;
				for(int b=0; b<2; b++)
				{
					Point2D newStart = both[b].start, newEnd = both[b].end;
					double distToStart = newStart.distance(intersect);
					double distToEnd = newEnd.distance(intersect);

					// see if intersection is deeply inside of the segment
					boolean makeT = insideSegment(newStart, newEnd, intersect);
					if (makeT)
					{
						int minDistToEnd = (int)Math.min(distToStart, distToEnd);
						if (minDistToEnd <= both[b].width/2) makeT = false;
					}

                    boolean internalIntersect = false;
                    Line2D lineSeg = new Line2D.Double(newStart, newEnd);
                    if (lineSeg.ptSegDist(intersect) == 0)
                        internalIntersect = true;

                    // if intersection is off-grid, rather than having two arcs join at this point,
                    // which can cause off-grid errors, make a pure layer node that is the intersection area,
                    // and connect to that on the edges. The important difference is a pure-layer node has
                    // the entire node as the port, rather than an arc pin, so the connection points can be on grid.
                    boolean offgrid = false;
                    if (alignment != null && (intersect.getX() % scaleUp(alignment.getWidth()) != 0)) offgrid = true;
                    if (alignment != null && (intersect.getY() % scaleUp(alignment.getHeight()) != 0)) offgrid = true;

					// adjust the centerline to end at the intersection point
					double extendStart = 0, extendEnd = 0, extendAltStart = 0, extendAltEnd = 0, betterExtension = 0;
					Point2D altNewStart = new Point2D.Double(0,0), altNewEnd = new Point2D.Double(0,0);
					if (distToStart < distToEnd)
					{
						betterExtension = newStart.distance(intersect);
						altNewStart.setLocation(newStart);   altNewEnd.setLocation(intersect);
						newStart = intersect;
						extendAltEnd = extendStart = both[b].width / 2;
                        if (offgrid && !makeT) {
                            if (internalIntersect) {
                                // adjust closer end point out of intersection area
                                double diffX = altNewStart.getX() - intersect.getX();
                                double diffY = altNewStart.getY() - intersect.getY();
                                newStart = new Point2D.Double(intersect.getX() - diffX, intersect.getY() - diffY);
                                altNewEnd.setLocation(intersect.getX() + diffX, intersect.getY() + diffY);
                            } else {
                                newStart.setLocation(altNewStart);
                            }
                            extendAltEnd = extendStart = Math.abs(betterExtension);
                        }
                        else if (!makeT && betterExtension < extendStart) {
                            // wire will not have ends extended, add in extra wire to account for non-end extension
                            Centerline newCl = new Centerline(both[b].width, altNewStart, intersect);
                            newCl.startHub = false;
                            newCl.endHub = true;
                            if (newCl.start.distance(newCl.end) > 0)
                                extraCenterlines.add(newCl);
                        }
                    } else // case: distToEnd <= distToStart
					{
						betterExtension = newEnd.distance(intersect);
						altNewStart.setLocation(intersect);   altNewEnd.setLocation(newEnd);
						newEnd = intersect;
						extendAltStart = extendEnd = both[b].width / 2;
                        if (offgrid && !makeT) {
                            if (internalIntersect) {
                                double diffX = altNewEnd.getX() - intersect.getX();
                                double diffY = altNewEnd.getY() - intersect.getY();
                                newEnd = new Point2D.Double(intersect.getX() - diffX, intersect.getY() - diffY);
                                altNewStart.setLocation(intersect.getX() + diffX, intersect.getY() + diffY);
                            } else {
                                newEnd.setLocation(altNewEnd);
                            }
                            extendAltStart = extendEnd = Math.abs(betterExtension);
                        }
                        else if (!makeT && betterExtension < extendEnd) {
                            // wire will not have ends extended, add in extra wire to account for non-end extension
                            Centerline newCl = new Centerline(both[b].width, intersect, altNewEnd);
                            newCl.startHub = true;
                            newCl.endHub = false;
                            if (newCl.start.distance(newCl.end) > 0)
                                extraCenterlines.add(newCl);
                        }
					}
					Poly extended = Poly.makeEndPointPoly(newStart.distance(newEnd), both[b].width, both[b].angle,
						newStart, extendStart, newEnd, extendEnd, Poly.Type.FILLED);
					if (!originalMerge.contains(layer, extended))
					{
						if (extendStart > 0) extendStart = betterExtension;
						if (extendEnd > 0) extendEnd = betterExtension;
						extended = Poly.makeEndPointPoly(newStart.distance(newEnd), both[b].width, both[b].angle,
							newStart, extendStart, newEnd, extendEnd, Poly.Type.FILLED);
                    }
					if (originalMerge.contains(layer, extended))
					{
						both[b].setStart(newStart.getX(), newStart.getY());
						both[b].setEnd(newEnd.getX(), newEnd.getY());
						if (extendStart != 0) both[b].startHub = true;
						if (extendEnd != 0) both[b].endHub = true;
						if (makeT)
						{
							// too much shrinkage: split the centerline
							Centerline newCL = new Centerline(both[b].width, altNewStart, altNewEnd);
							if (extendAltStart != 0) newCL.startHub = true;
							if (extendAltEnd != 0) newCL.endHub = true;
							validCenterlines.add(newCL);
							continue;
						}
					} else {
                        //System.out.println("Merge does not contain arc");
                    }
				}
			}
        }
        validCenterlines.addAll(extraCenterlines);

        if (DEBUGCENTERLINES)
		{
			System.out.println("FINAL: ");
			for(Centerline cl : validCenterlines) System.out.println("    "+cl);
		}
		return validCenterlines;
	}

	/**
	 * Method to tell whether a point is inside of a line segment.
	 * It is assumed that the point is on the line defined by the segment.
	 * @param start one point on the segment.
	 * @param end another point on the segment.
	 * @param pt the point in question.
	 * @return true if the point in question is inside of the line segment.
	 */
	private boolean insideSegment(Point2D start, Point2D end, Point2D pt)
	{
		if (pt.getX() < Math.min(start.getX(),end.getX()) || pt.getX() > Math.max(start.getX(),end.getX()) ||
			pt.getY() < Math.min(start.getY(),end.getY()) || pt.getY() > Math.max(start.getY(),end.getY()))
				return false;
		return true;
	}

	/**
	 * Method to gather all of the Centerlines in a polygon.
	 * @param poly the Poly to analyze.
	 * @param merge the merge at this point (with extracted geometry removed).
	 * @param originalMerge the original collection of geometry.
	 * @return a List of Centerlines in the polygon.
	 */
	private List<Centerline> gatherCenterlines(PolyBase poly, PolyMerge merge, PolyMerge originalMerge)
	{
		// first make a list of all parallel wires in the polygon
		List<Centerline> centerlines = new ArrayList<Centerline>();
		Point2D [] points = poly.getPoints();
		if (DEBUGCENTERLINES)
		{
			System.out.print("POLYGON ON LAYER "+poly.getLayer().getName()+":");
			for(int i=0; i<points.length; i++)
				System.out.print(" "+i+"("+TextUtils.formatDouble(points[i].getX()/SCALEFACTOR)+","+
					TextUtils.formatDouble(points[i].getY()/SCALEFACTOR)+")");
			System.out.println();
		}

		// make a list of line segments that are parallel
		Map<Integer,List<Integer>> linesAtAngle = new TreeMap<Integer,List<Integer>>();
		for(int i=0; i<points.length; i++)
		{
			int lastI = i-1;
			if (lastI < 0) lastI = points.length-1;
			Point2D lastPt = points[lastI];
			Point2D thisPt = points[i];
			if (lastPt.equals(thisPt)) continue;
			int angle = GenMath.figureAngle(thisPt, lastPt);
			while (angle < 0) angle += 1800;
			while (angle >= 1800) angle -= 1800;
			Integer iAngle = new Integer(angle);
			List<Integer> linesSoFar = linesAtAngle.get(iAngle);
			if (linesSoFar == null)
			{
				linesSoFar = new ArrayList<Integer>();
				linesAtAngle.put(iAngle, linesSoFar);
			}
			linesSoFar.add(new Integer(i));
		}

		// now see how many holes there are (by counting colinear segments)
		int colinearSegs = 0;
		for(Integer iAangle : linesAtAngle.keySet())
		{
			// get list of all line segments that are parallel to each other
			List<Integer> linesAtThisAngle = linesAtAngle.get(iAangle);
			if (linesAtThisAngle == null) continue;
			for(int ai=0; ai<linesAtThisAngle.size(); ai++)
			{
				int i = linesAtThisAngle.get(ai).intValue();
				int lastI = i-1;
				if (lastI < 0) lastI = points.length-1;
				Point2D lastPt = points[lastI];
				Point2D thisPt = points[i];

				for(int aj = ai+2; aj<linesAtThisAngle.size()-1; aj++)
				{
					int j = linesAtThisAngle.get(aj).intValue();
					if (GenMath.isOnLine(lastPt, thisPt, points[j])) colinearSegs++;
				}
			}
		}
//		boolean tooManyHoles = colinearSegs*7 > points.length;
//System.out.println("   so have "+points.length+" points with "+colinearSegs+" colinear segments"+ (tooManyHoles?" TOO MANY":""));
//		if (tooManyHoles) return null;

		// preallocate
		Point2D [] corners = new Point2D[4];
		corners[0] = new Point2D.Double(0, 0);
		corners[1] = new Point2D.Double(0, 0);
		corners[2] = new Point2D.Double(0, 0);
		corners[3] = new Point2D.Double(0, 0);
		Point2D [] possibleStart = new Point2D[4];
		Point2D [] possibleEnd = new Point2D[4];

		// now scan all sets of parallel lines
		for(Integer iAangle : linesAtAngle.keySet())
		{
			// get list of all line segments that are parallel to each other
			List<Integer> linesAtThisAngle = linesAtAngle.get(iAangle);
			if (linesAtThisAngle == null) continue;
			int angle = iAangle.intValue();
			for(int ai=0; ai<linesAtThisAngle.size(); ai++)
			{
				int i = linesAtThisAngle.get(ai).intValue();
				int lastI = i-1;
				if (lastI < 0) lastI = points.length-1;
				Point2D lastPt = points[lastI];
				Point2D thisPt = points[i];

				for(int aj = ai+1; aj<linesAtThisAngle.size(); aj++)
				{
					int j = linesAtThisAngle.get(aj).intValue();
					Point2D oLastPt = points[j-1];
					Point2D oThisPt = points[j];

					// parallel lines: find point on the center line
					int perpAngle = angle + 900;
					Point2D oneSide = thisPt;
					Point2D otherSide = GenMath.intersect(thisPt, perpAngle, oThisPt, angle);
					Point2D centerPt = new Point2D.Double((oneSide.getX()+otherSide.getX()) / 2, (oneSide.getY()+otherSide.getY()) / 2);

					// now determine range along that centerline
					Point2D lastPtCL = GenMath.intersect(lastPt, perpAngle, centerPt, angle);
					Point2D thisPtCL = GenMath.intersect(thisPt, perpAngle, centerPt, angle);
					Point2D oLastPtCL = GenMath.intersect(oLastPt, perpAngle, centerPt, angle);
					Point2D oThisPtCL = GenMath.intersect(oThisPt, perpAngle, centerPt, angle);

					// find the bounding box of the range lines
					double minX = Math.min(Math.min(lastPtCL.getX(), thisPtCL.getX()), Math.min(oLastPtCL.getX(), oThisPtCL.getX()));
					double maxX = Math.max(Math.max(lastPtCL.getX(), thisPtCL.getX()), Math.max(oLastPtCL.getX(), oThisPtCL.getX()));
					double minY = Math.min(Math.min(lastPtCL.getY(), thisPtCL.getY()), Math.min(oLastPtCL.getY(), oThisPtCL.getY()));
					double maxY = Math.max(Math.max(lastPtCL.getY(), thisPtCL.getY()), Math.max(oLastPtCL.getY(), oThisPtCL.getY()));

					// determine an extreme point along the centerline
					corners[0].setLocation(minX, minY);
					corners[1].setLocation(minX, maxY);
					corners[2].setLocation(maxX, maxY);
					corners[3].setLocation(maxX, minY);
					Point2D aCorner = null;
					for(int k=0; k<4; k++)
					{
						if (lastPtCL.equals(corners[k])) aCorner = lastPtCL;
						if (thisPtCL.equals(corners[k])) aCorner = thisPtCL;
						if (oLastPtCL.equals(corners[k])) aCorner = oLastPtCL;
						if (oThisPtCL.equals(corners[k])) aCorner = oThisPtCL;
					}

					// determine distance from the extreme corner
					double lastDist = aCorner.distance(lastPtCL);
					double thisDist = aCorner.distance(thisPtCL);
					double oLastDist = aCorner.distance(oLastPtCL);
					double oThisDist = aCorner.distance(oThisPtCL);

					// make sure the ranges overlap
					if (Math.min(lastDist, thisDist) >= Math.max(oLastDist, oThisDist) ||
						Math.min(oLastDist, oThisDist) >= Math.max(lastDist, thisDist)) continue;
					if (DEBUGCENTERLINES)
						System.out.println("PARALLEL LINES ("+TextUtils.formatDouble(lastPt.getX()/SCALEFACTOR)+","+TextUtils.formatDouble(lastPt.getY()/SCALEFACTOR)+
							") to ("+TextUtils.formatDouble(thisPt.getX()/SCALEFACTOR)+","+TextUtils.formatDouble(thisPt.getY()/SCALEFACTOR)+")"+
							" and ("+TextUtils.formatDouble(oLastPt.getX()/SCALEFACTOR)+","+TextUtils.formatDouble(oLastPt.getY()/SCALEFACTOR)+
							") to ("+TextUtils.formatDouble(oThisPt.getX()/SCALEFACTOR)+","+TextUtils.formatDouble(oThisPt.getY()/SCALEFACTOR)+")");

					// find the overlap
					if (lastDist > thisDist)
					{
						double swap = lastDist;    lastDist = thisDist;   thisDist = swap;
						Point2D swapPt = lastPtCL; lastPtCL = thisPtCL;   thisPtCL = swapPt;
					}
					if (oLastDist > oThisDist)
					{
						double swap = oLastDist;    oLastDist = oThisDist;   oThisDist = swap;
						Point2D swapPt = oLastPtCL; oLastPtCL = oThisPtCL;   oThisPtCL = swapPt;
					}
					Point2D start, finish, oStart, oFinish;
					if (lastDist < oLastDist)
					{
						start = oLastPtCL;
						oStart = lastPtCL;
					} else
					{
						start = lastPtCL;
						oStart = oLastPtCL;
					}
					if (thisDist > oThisDist)
					{
						finish = oThisPtCL;
						oFinish = thisPtCL;
					} else
					{
						finish = thisPtCL;
						oFinish = oThisPtCL;
					}

					// make a list of the centerline extent possibilities
					possibleStart[0] = oStart;   possibleEnd[0] = oFinish;
					if (start.distance(oStart) < finish.distance(oFinish))
					{
						possibleStart[1] = oStart;   possibleEnd[1] = finish;
						possibleStart[2] = start;   possibleEnd[2] = oFinish;
					} else
					{
						possibleStart[1] = start;   possibleEnd[1] = oFinish;
						possibleStart[2] = oStart;   possibleEnd[2] = finish;
					}
					possibleStart[3] = start;   possibleEnd[3] = finish;

					// try all possible spans
					double width = oneSide.distance(otherSide);
					for(int p=0; p<4; p++)
					{
						double length = possibleStart[p].distance(possibleEnd[p]);
						Poly clPoly = Poly.makeEndPointPoly(length, width, angle,
							possibleStart[p], 0, possibleEnd[p], 0, Poly.Type.FILLED);
						if (originalMerge.contains(poly.getLayer(), clPoly))
						{
							// get the centerline points
							double psX = possibleStart[p].getX();
							double psY = possibleStart[p].getY();
							double peX = possibleEnd[p].getX();
							double peY = possibleEnd[p].getY();

/*
							// grid-align the centerline points
							double xGrid = scaleUp(alignment.getWidth());
							double yGrid = scaleUp(alignment.getHeight());
							if (!isOnGrid(psX, xGrid))
							{
								if (psX > peX) psX = Math.floor(psX / xGrid) * xGrid; else
									psX = Math.ceil(psX / xGrid) * xGrid;
							}
							if (!isOnGrid(psY, yGrid))
							{
								if (psY > peY) psY = Math.floor(psY / yGrid) * yGrid; else
									psY = Math.ceil(psY / yGrid) * yGrid;
							}
							if (!isOnGrid(peX, xGrid))
							{
								if (peX > psX) peX = Math.floor(peX / xGrid) * xGrid; else
									peX = Math.ceil(peX / xGrid) * xGrid;
							}
							if (!isOnGrid(peY, yGrid))
							{
								if (peY > psY) peY = Math.floor(peY / yGrid) * yGrid; else
									peY = Math.ceil(peY / yGrid) * yGrid;
							}
*/

							// create the centerline
							Point2D ps = new Point2D.Double(psX, psY);
							Point2D pe = new Point2D.Double(peX, peY);
							Centerline newCL = new Centerline(width, ps, pe);
                            if (newCL.angle >= 0) {
                                // check for redundant centerlines, favor centerlines that extend to external geometry
                                boolean comparisonDone = false;
                                for (int ci=0; ci < centerlines.size(); ci++) {
                                    Centerline acl = centerlines.get(ci);
                                    // same bounds
                                    Rectangle2D aclBounds = acl.getBounds();
                                    Rectangle2D newCLBounds = newCL.getBounds();
                                    if (aclBounds != null && newCLBounds != null && aclBounds.equals(newCLBounds))
                                    {
                                        // check if they are perpendicular - if not, they are exactly redundant
                                        Centerline horCL = null, verCL = null;
                                        if (acl.start.getX() == acl.end.getX()) verCL = acl;
                                        if (acl.start.getY() == acl.end.getY()) horCL = acl;
                                        if (newCL.start.getX() == newCL.end.getX()) verCL = newCL;
                                        if (newCL.start.getY() == newCL.end.getY()) horCL = newCL;
                                        if (horCL == null || verCL == null) {
                                            comparisonDone = true;
                                            break; // not perpendicular
                                        }

                                        Rectangle2D bounds = acl.getBounds();
                                        int favorHor = 0, favorVer = 0;

                                        // check horizontal extensions
                                        Rectangle2D boundsE = new Rectangle2D.Double(bounds.getX(), bounds.getY(),
                                                bounds.getWidth()+1.0/SCALEFACTOR, bounds.getHeight());
                                        Rectangle2D boundsW = new Rectangle2D.Double(bounds.getX()-1.0/SCALEFACTOR, bounds.getY(),
                                                bounds.getWidth()+1.0/SCALEFACTOR, bounds.getHeight());
                                        if (originalMerge.contains(poly.getLayer(), new PolyBase(boundsE)))
                                            favorHor++;
                                        if (originalMerge.contains(poly.getLayer(), new PolyBase(boundsW)))
                                            favorHor++;

                                        // check vertical extensions
                                        Rectangle2D boundsN = new Rectangle2D.Double(bounds.getX(), bounds.getY(),
                                                bounds.getWidth(), bounds.getHeight()+1.0/SCALEFACTOR);
                                        Rectangle2D boundsS = new Rectangle2D.Double(bounds.getX(), bounds.getY()-1.0/SCALEFACTOR,
                                                bounds.getWidth(), bounds.getHeight()+1.0/SCALEFACTOR);
                                        if (originalMerge.contains(poly.getLayer(), new PolyBase(boundsN)))
                                            favorVer++;
                                        if (originalMerge.contains(poly.getLayer(), new PolyBase(boundsS)))
                                            favorVer++;

                                        if (favorHor > favorVer) {
                                            if (centerlines.contains(verCL)) {
                                                if (DEBUGCENTERLINES)
                                                    System.out.println("***REMOVE "+verCL.toString()+" WHICH IS SUBOPTIMAL");
                                                centerlines.remove(verCL);
                                            }
                                            if (!centerlines.contains(horCL)) {
                                                if (DEBUGCENTERLINES) System.out.println("  MAKE "+newCL.toString());
                                                centerlines.add(horCL);
                                            }
                                            comparisonDone = true;
                                        }
                                        if (favorVer > favorHor) {
                                            if (centerlines.contains(horCL)) {
                                                if (DEBUGCENTERLINES)
                                                    System.out.println("***REMOVE "+horCL.toString()+" WHICH IS SUBOPTIMAL");
                                                centerlines.remove(horCL);
                                            }
                                            if (!centerlines.contains(verCL)) {
                                                if (DEBUGCENTERLINES) System.out.println("  MAKE "+newCL.toString());
                                                centerlines.add(verCL);
                                            }
                                            comparisonDone = true;
                                        }
                                        break;
                                    }
                                }
                                if (!comparisonDone) {
                                    centerlines.add(newCL);
                                    if (DEBUGCENTERLINES) System.out.println("  MAKE "+newCL.toString());
                                }
                            }
							break;
						}
					}
				}
			}
		}

		// sort the parallel wires by length
		Collections.sort(centerlines, new ParallelWiresByLength());
		if (DEBUGCENTERLINES)
		{
			System.out.println("SORTED BY LENGTH:");
			for(Centerline cl : centerlines)
				System.out.println("    "+cl.toString());
		}

		// remove redundant centerlines
		PolyMerge reCheck = new PolyMerge();
		for(int i=0; i<centerlines.size(); i++)
		{
			Centerline cl = centerlines.get(i);
			Poly clPoly = Poly.makeEndPointPoly(cl.start.distance(cl.end), cl.width, cl.angle,
				cl.start, 0, cl.end, 0, Poly.Type.FILLED);
			if (reCheck.contains(tempLayer1, clPoly))
			{
				if (DEBUGCENTERLINES)
					System.out.println("***REMOVE "+cl.toString()+" WHICH IS REDUNDANT");
				centerlines.remove(i);
				i--;
				continue;
			}
			reCheck.addPolygon(tempLayer1, clPoly);
		}

		// sort the parallel wires by width
		Collections.sort(centerlines, new ParallelWiresByWidth());
		if (DEBUGCENTERLINES)
		{
			System.out.println("FINALLY:");
			for(Centerline cl : centerlines)
				System.out.println("    "+cl.toString());
		}
		return centerlines;
	}

	/**
	 * Class to sort Centerline objects by their width (and within that, by their length).
	 */
	private static class ParallelWiresByWidth implements Comparator<Centerline>
	{
		public int compare(Centerline cl1, Centerline cl2)
		{
			if (cl1.width < cl2.width) return 1;
			if (cl1.width > cl2.width) return -1;
			double cll1 = cl1.start.distance(cl1.end);
			double cll2 = cl2.start.distance(cl2.end);
			if (cll1 > cll2) return -1;
			if (cll1 < cll2) return 1;
			return 0;
		}
	}

	/**
	 * Class to sort Centerline objects by their length (and within that, by their width).
	 */
	private static class ParallelWiresByLength implements Comparator<Centerline>
	{
		public int compare(Centerline cl1, Centerline cl2)
		{
			double cll1 = cl1.start.distance(cl1.end);
			double cll2 = cl2.start.distance(cl2.end);
			if (cll1 > cll2) return -1;
			if (cll1 < cll2) return 1;
			if (cl1.width < cl2.width) return -1;
			if (cl1.width > cl2.width) return 1;
			return 0;
		}
	}

	/**
	 * Method to scan the geometric information and convert it all to pure layer nodes in the new cell.
	 * When all other extractions are done, this is done to preserve any geometry that is unaccounted-for.
	 */
	private void convertAllGeometry(PolyMerge merge, PolyMerge originalMerge, Cell newCell, boolean usePureLayerNodes)
	{
		MutableInteger numIgnored = new MutableInteger(0);
		for (Layer layer : merge.getKeySet())
		{
			ArcProto ap = arcsForLayer.get(layer);
			List<PolyBase> polyList = getMergePolys(merge, layer, numIgnored);

            boolean implants = layer.getFunction().isSubstrate() || layer.getFunction().isImplant();
//            if (implants) {
//                // just dump back in original layer, as it doesn't connect to anything anyway
//                polyList = new ArrayList<PolyBase>(originalMerge.getObjects(layer, false, false));
//            }

            if (usePureLayerNodes | implants) ap = null;
            for(PolyBase poly : polyList)
			{
				// special case: a rectangle on a routable layer: make it an arc
                if (ap != null)
				{
					Rectangle2D polyBounds = poly.getBox();
					if (polyBounds == null)
					{
						Rectangle2D totalBounds = poly.getBounds2D();
						if (originalMerge.contains(layer, totalBounds))
							polyBounds = totalBounds;
					}
					if (polyBounds != null)
					{
						double width = polyBounds.getWidth();
						double height = polyBounds.getHeight();
						double actualWidth = 0;
						if (ENFORCEMINIMUMSIZE) actualWidth = ap.getDefaultLambdaBaseWidth(ep) * SCALEFACTOR;
						if (width >= actualWidth && height >= actualWidth)
						{
							PrimitiveNode np = ap.findPinProto();
							Point2D end1 = null, end2 = null;
							double size = 0;
							if (width > height)
							{
								// make a horizontal arc
								end1 = new Point2D.Double((polyBounds.getMinX()), polyBounds.getCenterY());
								end2 = new Point2D.Double((polyBounds.getMaxX()), polyBounds.getCenterY());
								size = height;
							} else
							{
								// make a vertical arc
								end1 = new Point2D.Double(polyBounds.getCenterX(), (polyBounds.getMinY()));
								end2 = new Point2D.Double(polyBounds.getCenterX(), (polyBounds.getMaxY()));
								size = width;
							}
							MutableBoolean headExtend = new MutableBoolean(false), tailExtend = new MutableBoolean(false);
							if (originalMerge.arcPolyFits(layer, end1, end2, size, headExtend, tailExtend))
							{
                                // scale everything
                                end1 = new Point2D.Double(end1.getX() / SCALEFACTOR, end1.getY() / SCALEFACTOR);
                                end2 = new Point2D.Double(end2.getX() / SCALEFACTOR, end2.getY() / SCALEFACTOR);
                                size = size / SCALEFACTOR;
                                // make arc
                                NodeInst ni1 = createNode(np, end1, size, size, null, newCell);
								NodeInst ni2 = createNode(np, end2, size, size, null, newCell);
								realizeArc(ap, ni1.getOnlyPortInst(), ni2.getOnlyPortInst(), end1, end2,
									size, !headExtend.booleanValue(), !tailExtend.booleanValue(), usePureLayerNodes, merge);
							} else {
                                System.out.println("Arc "+layer.getName()+" did not fit at "+polyBounds.getMinX()/SCALEFACTOR+","+polyBounds.getMinY()/SCALEFACTOR);
                            }
							continue;
						}
					}
				}

				// just generate a pure-layer node
				List<NodeInst> niList = makePureLayerNodeFromPoly(poly, newCell, originalMerge);
                // make well or implant hard to select
                if (poly.getLayer().getFunction().isSubstrate() || poly.getLayer().getFunction().isImplant()) {
                    for (NodeInst ni : niList) ni.setHardSelect();
                }

                if (niList == null) continue;

				// connect to the rest if possible
				if (ap != null) for(NodeInst ni : niList)
				{
					PortInst fPi = ni.getOnlyPortInst();
					Poly fPortPoly = fPi.getPoly();
					Rectangle2D polyBounds = poly.getBounds2D();
					Rectangle2D searchBound = new Rectangle2D.Double(polyBounds.getMinX()/SCALEFACTOR, polyBounds.getMinY()/SCALEFACTOR,
						polyBounds.getWidth()/SCALEFACTOR, polyBounds.getHeight()/SCALEFACTOR);
					PortInst bestTPi = null;
					double bestLen = Double.MAX_VALUE;
					Point2D bestFLoc = null;
					for(Iterator<Geometric> it = newCell.searchIterator(searchBound); it.hasNext(); )
					{
						Geometric geom = it.next();
						if (!(geom instanceof NodeInst)) continue;
						NodeInst oNi = (NodeInst)geom;
						if (oNi == ni) continue;
						if (oNi.isCellInstance()) continue;
						if (oNi.getProto().getTechnology() != tech) continue;
						for(Iterator<PortInst> pIt = oNi.getPortInsts(); pIt.hasNext(); )
						{
							PortInst tPi = pIt.next();
							PortProto pp = tPi.getPortProto();
							if (!pp.connectsTo(ap)) continue;
							EPoint tLoc = tPi.getPoly().getCenter();
							EPoint tLocScaled = EPoint.fromLambda(scaleUp(tLoc.getX()), scaleUp(tLoc.getY()));
							Point2D fLoc = fPortPoly.closestPoint(tLoc);
							EPoint fLocScaled = EPoint.fromLambda(scaleUp(fLoc.getX()), scaleUp(fLoc.getY()));
							double len = scaleUp(fLoc.distance(tLoc));
							int angle = GenMath.figureAngle(tLoc, fLoc);
							Poly conPoly = Poly.makeEndPointPoly(len, 1, angle, fLocScaled, 0, tLocScaled, 0, Poly.Type.FILLED);
							if (originalMerge.contains(layer, conPoly))
							{
								if (len < bestLen)
								{
									bestLen = len;
									bestTPi = tPi;
									bestFLoc = fLoc;
								}
							}
						}
					}
					if (bestTPi != null)
					{
						Poly tPortPoly = bestTPi.getPoly();
						Point2D tLoc = tPortPoly.closestPoint(tPortPoly.getCenter());
						ArcInst ai = realizeArc(ap, fPi, bestTPi, bestFLoc, tLoc, 0, false, false, usePureLayerNodes, merge);
						if (ai != null) ai.setFixedAngle(false);
					} else
					{
						// Approximating the error with center
						addErrorLog(newCell, "Unable to connect unextracted polygon",
								EPoint.fromLambda(searchBound.getCenterX(), searchBound.getCenterY()));
					}
				}
			}
		}

		// also throw in unextracted contact cuts
		for (Layer layer : allCutLayers.keySet())
		{
			CutInfo cInfo = allCutLayers.get(layer);
			for(PolyBase poly : cInfo.getCuts())
				makePureLayerNodeFromPoly(poly, newCell, originalMerge);
		}

		if (numIgnored.intValue() > 0)
			System.out.println("WARNING: Ignored " + numIgnored.intValue() +
				" tiny polygons (use Network Preferences to control tiny polygon limit)");
	}

    /**
     * Method to add back in original routing layers as pure layer nodes
     * @param oldCell the original imported cell
     * @param newCell the new cell
     * @param merge the remaining layers to create
     * @param originalMerge the original set of layers
     */
    private void addInRoutingLayers(Cell oldCell, Cell newCell, PolyMerge merge, PolyMerge originalMerge, boolean usePureLayerNodes)
    {
        Map<Layer,List<NodeInst>> newNodes = new HashMap<Layer,List<NodeInst>>();

        // create new nodes as copy of old nodes
        for (Iterator<NodeInst> nit = oldCell.getNodes(); nit.hasNext(); )
        {
            NodeInst ni = nit.next();
            NodeProto np = ni.getProto();
            if (!(np instanceof PrimitiveNode)) continue;
            PrimitiveNode pn = (PrimitiveNode)np;
            if (pn.getFunction() == PrimitiveNode.Function.NODE)
            {
                Layer layer = pn.getLayerIterator().next();
                Layer.Function fun = layer.getFunction();
                if (fun.isPoly() || fun.isMetal() || fun.isDiff())
                {
                    List<NodeInst> newNis = new ArrayList<NodeInst>();
                    // create same node in new cell
                    if (ni.getTrace() != null && ni.getTrace().length > 0)
                    {
                        EPoint [] origPoints = ni.getTrace();
                        PolyBase.Point [] points = new PolyBase.Point[origPoints.length];
                        // for some reason getTrace returns points relative to center, but setTrace expects absolute coordinates
                        for (int i=0; i<origPoints.length; i++) {
                            points[i] = PolyBase.fromLambda(origPoints[i].getX()+ni.getAnchorCenterX(), origPoints[i].getY()+ni.getAnchorCenterY());
                        }

                        boolean BREAKUPTRACE = true;

                        PolyBase poly = new PolyBase(points);
                        poly.setLayer(layer);

                        if (BREAKUPTRACE)
                        {
                            // irregular shape: break it up with simpler polygon merging algorithm
                            GeometryHandler thisMerge = GeometryHandler.createGeometryHandler(GeometryHandler.GHMode.ALGO_SWEEP, 1);
                            thisMerge.add(layer, poly);
                            thisMerge.postProcess(true);

                            Collection<PolyBase> set = ((PolySweepMerge)thisMerge).getPolyPartition(layer);
                            for(PolyBase simplePoly : set)
                            {
                                PolyBase simplePolyScaledUp = scaleUpPoly(simplePoly);
                                pn = getActiveNodeType(layer, simplePolyScaledUp, originalMerge, pn);
                                layer = pn.getLayerIterator().next();Rectangle2D polyBounds = simplePoly.getBounds2D();
                                NodeInst newNi = NodeInst.makeInstance(pn, ep, simplePoly.getCenter(), polyBounds.getWidth(), polyBounds.getHeight(),
                                                                       newCell);
                                if (newNi == null) continue;
                                newNis.add(newNi);
                            }
                        } else {
                            PolyBase polyScaledUp = scaleUpPoly(poly);
                            pn = getActiveNodeType(layer, polyScaledUp, originalMerge, pn);
                            NodeInst newNi = NodeInst.makeInstance(pn, ep, ni.getAnchorCenter(), ni.getXSize(), ni.getYSize(), newCell);
                            if (newNi != null) {
                                newNis.add(newNi);
                                newNi.setTrace(points);
                            }
                        }
                    } else {
                        PolyBase poly = new PolyBase(ni.getBounds());
                        PolyBase polyScaledUp = scaleUpPoly(poly);
                        pn = getActiveNodeType(layer, polyScaledUp, originalMerge, pn);
                        NodeInst newNi = NodeInst.makeInstance(pn, ep, ni.getAnchorCenter(), ni.getXSize(), ni.getYSize(),
                                                               newCell, ni.getOrient(), null);
                        if (newNi != null)
                            newNis.add(newNi);
                    }
                    // subtract out layers
                    layer = pn.getLayerIterator().next();
                    for (NodeInst newNi : newNis) {
                        Poly [] polys = tech.getShapeOfNode(newNi);
                        for (Poly p : polys)
                        {
                            if (p.getLayer() == layer)
                                removePolyFromMerge(merge, layer, p);
                        }
                    }
                    // add to map of created nodeinsts
                    List<NodeInst> list = newNodes.get(layer);
                    if (list == null)
                    {
                        list = new ArrayList<NodeInst>();
                        newNodes.put(layer, list);
                    }
                    list.addAll(newNis);
                }
            }
        }
        // now stitch new nodes together
        for (Layer layer : newNodes.keySet())
        {
            Layer.Function fun = layer.getFunction();
            ArcProto ap = Generic.tech().universal_arc;
            if (fun.isPoly() || fun.isMetal() || fun.isDiff()) ap = arcsForLayer.get(layer);

            List<NodeInst> nodes = newNodes.get(layer);
            int i=0, j=1;
            // check nodes against each other
            for (i=0; i<nodes.size(); i++)
            {
                NodeInst ni1 = nodes.get(i);
                for (j=i+1; j<nodes.size(); j++)
                {
                    NodeInst ni2 = nodes.get(j);
                    if (ni1 == ni2) continue;
                    // see if shapes intersect. If so, connect them
                    Poly poly1 = tech.getShapeOfNode(ni1)[0];
                    Poly poly2 = tech.getShapeOfNode(ni2)[0];
                    List<Line2D> overlappingEdges = new ArrayList<Line2D>();
                    List<PolyBase> intersection = poly1.getIntersection(poly2, overlappingEdges);

                    Point2D connectionPoint = null;
                    if (intersection.size() > 0)
                    {
                        // areas intersect, use center point of first common area
                        PolyBase pint = intersection.get(0);
                        connectionPoint = pint.getCenter();
                        // round center point
                        if (alignment != null)
                        {
                            double x = connectionPoint.getX();
                            double y = connectionPoint.getY();
                            x = Math.round(x/alignment.getWidth()) * alignment.getWidth();
                            y = Math.round(y/alignment.getHeight()) * alignment.getHeight();
                            if (pint.contains(x, y))
                                connectionPoint = new Point2D.Double(x, y);
                        }
                    }
                    else if (overlappingEdges.size() > 0)
                    {
                        // areas do not intersect, but share common edges. Use center point of first edge
                        Line2D line = overlappingEdges.get(0);
                        double x = (line.getX1()+line.getX2())/2.0;
                        double y = (line.getY1()+line.getY2())/2.0;
                        if (alignment != null) {
                            double newx = x, newy = y;
                            if ((line.getY1() == line.getY2()) && !isOnGrid(x, alignment.getWidth())) {
                                newx = Math.round(x/alignment.getWidth()) * alignment.getWidth();
                            }
                            if ((line.getX1() == line.getX2()) && !isOnGrid(y, alignment.getHeight())) {
                                newy = Math.round(y/alignment.getHeight()) * alignment.getHeight();
                            }
                            if (line.ptSegDist(newx, newy) == 0) {
                                x = newx;
                                y = newy;
                            }
                        }
                        connectionPoint = new Point2D.Double(x, y);
                    }
                    if (connectionPoint != null)
                    {
                        // check on arc, we need to decide if diffusion arc is really diffusion arc or well arc
                        // scale up poly so units match originalMerge units
                        PolyBase poly1Scaled = scaleUpPoly(poly1);

                        if (fun.isDiff()) {
                            ap = findArcProtoForPoly(layer, poly1Scaled, originalMerge);
                        }
                        if (ap == null)
                        	ap = Generic.tech().universal_arc;

                        double width = ap.getDefaultLambdaBaseWidth(ep);
                        ArcProto arcProto = ap;
                        if (arcProto != Generic.tech().universal_arc)
                        {
                            // scale up to check merge
                            Point2D connPointScaled = new Point2D.Double(scaleUp(connectionPoint.getX()), scaleUp(connectionPoint.getY()));
                            Poly test1 = Poly.makeEndPointPoly(0.0, scaleUp(width), 0, connPointScaled, scaleUp(width/2.0), connPointScaled,
                                    scaleUp(width/2.0), Poly.Type.FILLED);
                            // if on grid and merge contains, make arc
                            if (isOnGrid(test1) && originalMerge.contains(layer, test1))
                            {
                                realizeArc(arcProto, ni1.getOnlyPortInst(), ni2.getOnlyPortInst(), connectionPoint,
                                        connectionPoint, width, false, false, usePureLayerNodes, merge);
                                continue;
                            }
                            arcProto = Generic.tech().universal_arc;
                            width = 0;
                            //System.out.println("Using universal arc to connect "+ni1.describe(false)+" at "+connectionPoint+" to "+ni2.describe(false));
                        }
                        realizeArc(arcProto, ni1.getOnlyPortInst(), ni2.getOnlyPortInst(), connectionPoint,
                                connectionPoint, width, false, false, usePureLayerNodes, merge);
                    }
                }
            }
        }
    }

    private PrimitiveNode getActiveNodeType(Layer activeLayer, PolyBase poly, PolyMerge merge, PrimitiveNode defaultNode)
    {
        if (unifyActive || ignoreActiveSelectWell) return defaultNode;
        if (defaultNode != pDiffNode && defaultNode != nDiffNode && defaultNode != diffNode) return defaultNode;

        if (activeLayer.getFunction() == Layer.Function.DIFFN) {
            // make sure n-diffusion is in n-select
            if (merge.contains(pSelectLayer, poly)) {
                // switch it p-active
                return pDiffNode;
            }
        }
        if (activeLayer.getFunction() == Layer.Function.DIFFP) {
            // make sure p-diffusion is in p-select
            if (merge.contains(nSelectLayer, poly)) {
                // switch it n-active
                return nDiffNode;
            }
        }
        return defaultNode;
    }

    private boolean isOnGrid(Poly poly)
    {
        if (alignment != null)
        {
            for (Point2D p : poly.getPoints())
            {
                if (!isOnGrid(p.getX(), alignment.getWidth())) return false;
                if (!isOnGrid(p.getY(), alignment.getHeight())) return false;
            }
        }
        return true;
    }

    /**
     * Returns a new poly that is a scaled up version of the given poly
     * @param poly the poly
     * @return a new, scaled up poly
     */
    private PolyBase scaleUpPoly(PolyBase poly) {
        PolyBase.Point [] points = new PolyBase.Point[poly.getPoints().length];
        for(int p=0; p<points.length; p++)
            points[p] = PolyBase.fromLambda(scaleUp(poly.getPoints()[p].getX()), scaleUp(poly.getPoints()[p].getY()));
        PolyBase newpoly = new PolyBase(points);
        newpoly.setStyle(poly.getStyle());
        return newpoly;
    }


    /**
	 * Method to convert a Poly to one or more pure-layer nodes.
	 * @param poly the PolyBase to convert.
	 * @param cell the Cell in which to place the node.
     * @param originalMerge the original set of layers in the unextracted cell
	 * @return a List of NodeInsts that were created (null on error).
	 */
	private List<NodeInst> makePureLayerNodeFromPoly(PolyBase poly, Cell cell, PolyMerge originalMerge)
	{
		Layer layer = poly.getLayer();

		// if an active layer, make sure correct N/P is used
		if (unifyActive && (layer == pActiveLayer || layer == nActiveLayer))
		{
			Rectangle2D rect = poly.getBounds2D();
			rect = new Rectangle2D.Double(rect.getMinX()/SCALEFACTOR-1, rect.getMinY()/SCALEFACTOR-1,
				rect.getWidth()/SCALEFACTOR+2, rect.getHeight()/SCALEFACTOR+2);
			int nType = 0, pType = 0;
			for(Iterator<Geometric> it = cell.searchIterator(rect); it.hasNext(); )
			{
				Geometric geom = it.next();
				if (geom instanceof NodeInst)
				{
					NodeInst ni = (NodeInst)geom;
					if (ni.isCellInstance()) continue;
					Poly [] polys = ni.getProto().getTechnology().getShapeOfNode(ni);
					for(int i=0; i<polys.length; i++)
					{
						Layer.Function fun = polys[i].getLayer().getFunction();
						if (!fun.isDiff()) continue;
						if (fun == Layer.Function.DIFFP) pType++;
						if (fun == Layer.Function.DIFFN) nType++;
					}
				} else
				{
					ArcInst ai = (ArcInst)geom;
					Poly [] polys = ai.getProto().getTechnology().getShapeOfArc(ai);
					for(int i=0; i<polys.length; i++)
					{
						Layer.Function fun = polys[i].getLayer().getFunction();
						if (!fun.isDiff()) continue;
						if (fun == Layer.Function.DIFFP) pType++;
						if (fun == Layer.Function.DIFFN) nType++;
					}
				}
			}
			if (pType > nType) layer = realPActiveLayer; else
				layer = realNActiveLayer;
			if (layer.getPureLayerNode() == null) layer = poly.getLayer();
		}

		PrimitiveNode pNp = layer.getPureLayerNode();
		if (pNp == null)
		{
			System.out.println("CANNOT FIND PURE LAYER NODE FOR LAYER " + layer.getName());
			return null;
		}
		List<NodeInst> createdNodes = new ArrayList<NodeInst>();
		if (poly.getBox() == null)
		{
			// irregular shape: break it up with simpler polygon merging algorithm
			GeometryHandler thisMerge = GeometryHandler.createGeometryHandler(GeometryHandler.GHMode.ALGO_SWEEP, 1);
			thisMerge.add(layer, poly);
			thisMerge.postProcess(true);

            Collection<PolyBase> set = ((PolySweepMerge)thisMerge).getPolyPartition(layer);
			for(PolyBase simplePoly : set)
			{
				Rectangle2D polyBounds = simplePoly.getBounds2D();
				NodeInst ni = makeAlignedPoly(polyBounds, layer, originalMerge, pNp, cell);
				if (ni == null) continue;
				createdNodes.add(ni);
			}
			return createdNodes;
		}
		Rectangle2D polyBounds = poly.getBounds2D();
		NodeInst ni = makeAlignedPoly(polyBounds, layer, originalMerge, pNp, cell);
		if (ni != null)
			createdNodes.add(ni);
//		double centerX = polyBounds.getCenterX() / SCALEFACTOR;
//		double centerY = polyBounds.getCenterY() / SCALEFACTOR;
//		Point2D center = new Point2D.Double(centerX, centerY);
//
//		// compute any trace information if the shape is non-Manhattan
//		EPoint [] newPoints = null;
//		NodeInst ni = createNode(pNp, center, polyBounds.getWidth() / SCALEFACTOR,
//			polyBounds.getHeight() / SCALEFACTOR, newPoints, cell);
//		createdNodes.add(ni);
		return createdNodes;
	}

	private NodeInst makeAlignedPoly(Rectangle2D polyBounds, Layer layer, PolyMerge originalMerge, PrimitiveNode pNp, Cell cell)
	{
		double centerX = polyBounds.getCenterX() / SCALEFACTOR;
		double centerY = polyBounds.getCenterY() / SCALEFACTOR;
		double width = polyBounds.getWidth() / SCALEFACTOR;
		double height = polyBounds.getHeight() / SCALEFACTOR;
		if (alignment != null)
		{
            // centers can be off-grid, only edges matter
            double aliX = Math.round(centerX / (alignment.getWidth()/2)) * (alignment.getWidth()/2);
			if (aliX != centerX)
			{
				double newWidth = width + Math.abs(aliX-centerX)*2;
				Poly rectPoly = new Poly(scaleUp(aliX), scaleUp(centerY), scaleUp(newWidth), scaleUp(height));
				if (!originalMerge.contains(layer, rectPoly))
				{
					if (aliX > centerX) aliX -= alignment.getWidth(); else
						aliX += alignment.getWidth();
					newWidth = width + Math.abs(aliX-centerX)*2;
					rectPoly = new Poly(scaleUp(aliX), scaleUp(centerY), scaleUp(newWidth), scaleUp(height));
					if (!originalMerge.contains(layer, rectPoly)) return null;
				}
				centerX = aliX;
				width = newWidth;
			}

            // centers can be off-grid, only edges matter
			double aliY = Math.round(centerY / (alignment.getHeight()/2)) * (alignment.getHeight()/2);
			if (aliY != centerY)
			{
				double newHeight = height + Math.abs(aliY-centerY)*2;
				Poly rectPoly = new Poly(scaleUp(centerX), scaleUp(aliY), scaleUp(width), scaleUp(newHeight));
				if (!originalMerge.contains(layer, rectPoly))
				{
					if (aliY > centerY) aliY -= alignment.getHeight(); else
						aliY += alignment.getHeight();
					newHeight = height + Math.abs(aliY-centerY)*2;
					rectPoly = new Poly(scaleUp(centerX), scaleUp(aliY), scaleUp(width), scaleUp(newHeight));
					if (!originalMerge.contains(layer, rectPoly)) return null;
				}
				centerY = aliY;
				height = newHeight;
			}
		}
		Point2D center = new Point2D.Double(centerX, centerY);
		NodeInst ni = createNode(pNp, center, width, height, null, cell);
		return ni;
	}

	/**
	 * Method to clean-up exports.
     * @param oldCell the original cell (unextracted)
     * @param newCell the new cell
	 */
	private void cleanupExports(Cell oldCell, Cell newCell)
	{
		// first restore original exports (which were on pure-layer nodes and must now be placed back)
		for(Export e : exportsToRestore)
		{
			EPoint loc = e.getPoly().getCenter();
			boolean found = false;
			Rectangle2D bounds = new Rectangle2D.Double(loc.getX(), loc.getY(), 0, 0);
			for(Iterator<Geometric> it = newCell.searchIterator(bounds); it.hasNext(); )
			{
				Geometric geom = it.next();
				if (!(geom instanceof NodeInst)) continue;
				NodeInst ni = (NodeInst)geom;
				for(Iterator<PortInst> pIt = ni.getPortInsts(); pIt.hasNext(); )
				{
					PortInst pi = pIt.next();
					PortProto pp = pi.getPortProto();
					if (!sameConnection(e, pp)) continue;
					EPoint pLoc = pi.getPoly().getCenter();
					if (loc.equals(pLoc))
					{
						Export.newInstance(newCell, pi, e.getName(), ep);
						found = true;
						break;
					}
				}
			}
			if (!found)
			{
				// did not reexport: create the pin and export...let it get auto-routed in
				PrimitiveNode pnUse = null;
				for(Iterator<PrimitiveNode> it = tech.getNodes(); it.hasNext(); )
				{
					PrimitiveNode pn = it.next();
					if (!pn.getFunction().isPin()) continue;
					if (!sameConnection(e, pn.getPort(0))) continue;
					pnUse = pn;
					break;
				}
				if (pnUse == null) continue;
				NodeInst ni = NodeInst.makeInstance(pnUse, ep, loc, pnUse.getDefWidth(ep), pnUse.getDefHeight(ep), newCell);
				Export.newInstance(newCell, ni.getOnlyPortInst(), e.getName(), ep);
			}
		}

		// now handle exported pins
		for(ExportedPin exportedPin : pinsForLater)
		{
			for(Iterator<Export> eIt = exportedPin.ni.getExports(); eIt.hasNext(); )
			{
				Export e = eIt.next();
				ArcProto[] possibleCons = e.getBasePort().getConnections();
				ArcProto ap = possibleCons[0];
				Layer layer = ap.getLayer(0);
				PortInst pi = makePort(newCell, layer, exportedPin.location);
				if (pi != null)
				{
					// found location of export: create export if not already done
					Export found = newCell.findExport(e.getName());
					if (found != null) continue;
				}

				// must create export stand-alone (not really a good idea, but cannot find anything to connect to it)
				double sX = exportedPin.ni.getXSize();
				double sY = exportedPin.ni.getYSize();
				Point2D instanceAnchor = new Point2D.Double(0, 0);
				exportedPin.trans.transform(exportedPin.ni.getAnchorCenter(), instanceAnchor);
				NodeInst newNi = NodeInst.makeInstance(exportedPin.ni.getProto(), ep, instanceAnchor, sX, sY, newCell);
				if (newNi == null)
				{
					addErrorLog(newCell, "Problem creating new instance of " + exportedPin.ni.getProto() + " for export",
							EPoint.fromLambda(sX, sY));
					continue;
				}
				PortInst newPi = newNi.findPortInstFromEquivalentProto(e.getOriginalPort().getPortProto());
				Export.newInstance(newCell, newPi, e.getName(), ep);
			}
		}

		// finally rename auto-generated export names to be more sensible
		for(Export e : generatedExports)
		{
			Cell cell = e.getParent();
			Netlist nl = cell.getNetlist();
			Network net = nl.getNetwork(e, 0);
			String exportName = null;
			for(Iterator<String> nIt = net.getExportedNames(); nIt.hasNext(); )
			{
				String eName = nIt.next();
				if (eName.startsWith("E"))
				{
					boolean isTemp = false;
					for(Export e2 : generatedExports)
					{
						if (e2.getParent() == cell && e2.getName().equals(eName)) { isTemp = true;   break; }
					}
					if (isTemp) continue;
					exportName = eName;
					break;
				}
			}
			if (exportName != null)
			{
				exportName = ElectricObject.uniqueObjectName(exportName, cell, Export.class, true, true);
				e.rename(exportName);
			}
		}
	}

	/**
	 * Method to compare two ports to see that they connect to the same set of arcs.
	 * @param pp1 the first port.
	 * @param pp2 the second port.
	 * @return true if they connect to the same arcs.
	 */
	private boolean sameConnection(PortProto pp1, PortProto pp2)
	{
		ArcProto [] arcs1 = pp1.getBasePort().getConnections();
		ArcProto [] arcs2 = pp2.getBasePort().getConnections();
		if (arcs1 == arcs2) return true;
		boolean [] found = new boolean[arcs2.length];
		for(int i=0; i<arcs1.length; i++)
		{
			if (arcs1[i].getTechnology() == Generic.tech()) continue;
			int j = 0;
			for( ; j<arcs2.length; j++)
			{
				if (arcs1[i] == arcs2[j])
				{
					found[j] = true;
					break;
				}
			}
			if (j >= arcs2.length) return false;
		}
		for(int j=0; j<arcs2.length; j++)
		{
			if (found[j]) continue;
			if (arcs2[j].getTechnology() != Generic.tech()) return false;
		}
		return true;
	}

	private NodeInst createNode(NodeProto np, Point2D loc, double wid, double hei, EPoint [] points, Cell cell)
	{
		// pins cannot be smaller than their default size
		if (np.getFunction().isPin())
		{
			if (wid < np.getDefWidth(ep)) wid = np.getDefWidth(ep);
			if (hei < np.getDefHeight(ep)) hei = np.getDefHeight(ep);
		}
		NodeInst ni = NodeInst.makeInstance(np, ep, loc, wid, hei, cell);
		if (ni != null && points != null)
			ni.setTrace(points);
		if (DEBUGSTEPS)
		{
			if (!np.getFunction().isPin())
			{
				Poly niPoly = Highlight.getNodeInstOutline(ni);
				addedRectangles.add(ERectangle.fromLambda(niPoly.getBounds2D()));
			}
		}
		return ni;
	}

	/**
	 * Method to create a node and remove its geometry from the database.
	 * @param pNp the node to create.
	 * @param cutVariation the cut spacing rule to use.
	 * @param centerX the new node's X location.
	 * @param centerY the new node's Y location.
	 * @param width the new node's width.
	 * @param height the new node's height.
	 * @param angle the rotation of the new node.
	 * @param points an array of Point2D objects that define the new node's outline (null if there is no outline).
	 * @param merge the geometry collection.  The new node will be removed from the merge.
	 * @param newCell the cell in which to create the new node.
	 * @param realizedNodes a list of nodes to which this one will be added.  If null, do not add to the list.
	 * If not null, add to the list but DO NOT remove the realized node's geometry from the merge.
	 */
	private void realizeNode(PrimitiveNode pNp, int cutVariation, double centerX, double centerY, double width, double height,
		int angle, Point2D [] points, PolyMerge merge, Cell newCell, List<NodeInst> realizedNodes, boolean usePureLayerNodes)
	{
		Orientation orient = Orientation.fromAngle(angle);
		double cX = centerX / SCALEFACTOR;
		double cY = centerY / SCALEFACTOR;
		NodeInst ni = NodeInst.makeInstance(pNp, ep, new Point2D.Double(cX, cY),
			width / SCALEFACTOR, height / SCALEFACTOR, newCell, orient, null);
		if (ni == null) return;
		if (cutVariation != Technology.NodeLayer.MULTICUT_CENTERED)
			ni.newVar(NodeLayer.CUT_ALIGNMENT, Integer.valueOf(cutVariation), ep);
		if (points != null)
		{
			ni.setTrace(points);
			if (DEBUGSTEPS)
			{
				for(int i=1; i<points.length; i++)
				{
					double sx = points[i-1].getX();
					double sy = points[i-1].getY();
					double ex = points[i].getX();
					double ey = points[i].getY();
					addedLines.add(ERectangle.fromLambda(sx, sy, ex-sx, ey-sy));
				}
			}
		} else
		{
			if (DEBUGSTEPS)
			{
				if (!pNp.getFunction().isPin())
				{
					Poly niPoly = Highlight.getNodeInstOutline(ni);
					addedRectangles.add(ERectangle.fromLambda(niPoly.getBounds2D()));
				}
			}
		}

		if (realizedNodes != null)
		{
			realizedNodes.add(ni);
		} else
		{
			// now remove the generated layers from the Merge
			FixpTransform trans = ni.rotateOut();
			Poly [] polys = tech.getShapeOfNode(ni);
			for(int i=0; i<polys.length; i++)
			{
				Poly poly = polys[i];
				Layer layer = poly.getLayer();

				// make sure the geometric database is made up of proper layers
				layer = geometricLayer(layer);

				poly.transform(trans);
				if (usePureLayerNodes && (layer.getFunction().isPoly() || layer.getFunction().isMetal())) continue;
//				Rectangle2D bds = poly.getBounds2D();
//System.out.println("REMOVING LAYER "+layer.getName()+" AT "+bds.getMinX()+"<=X<="+bds.getMaxX()+" AND "+bds.getMinY()+"<=Y<="+bds.getMaxY());
                removePolyFromMerge(merge, layer, poly);
			}
		}
	}

	/**
	 * Method to create an arc and remove its geometry from the database.
	 * @param ap the arc to create.
	 * @param pi1 the first side of the arc.
	 * @param pi2 the second side of the arc
	 * @param pt1 the first connection point
     * @param pt2 the second connection point
     * @param width the width of the arc
     * @param noHeadExtend do not extend the head
     * @param noTailExtend do not extend the tail
     * @param merge the merge to subtract the new arc from
     * @return the arc instance created
	 */
	private ArcInst realizeArc(ArcProto ap, PortInst pi1, PortInst pi2, Point2D pt1, Point2D pt2, double width,
		boolean noHeadExtend, boolean noTailExtend, boolean usePureLayerNodes, PolyMerge merge)
	{
		if (alignment != null)
			width = Math.floor(width / alignment.getWidth()) * alignment.getWidth();
		if (width == 0) ap = Generic.tech().universal_arc;
		ArcInst ai = ArcInst.makeInstanceBase(ap, ep, width, pi1, pi2, pt1, pt2, null);
		if (ai == null) return null;

		// special case: wide diffusion arcs on transistors should be shortened so they don't extend to other side
		if (ap.getFunction().isDiffusion() && ai.getLambdaBaseWidth() > ap.getDefaultLambdaBaseWidth(ep))
		{
			if (ai.getHeadPortInst().getNodeInst().getFunction().isTransistor()) noHeadExtend = true;
			if (ai.getTailPortInst().getNodeInst().getFunction().isTransistor()) noTailExtend = true;
		}

		if (noHeadExtend) ai.setHeadExtended(false);
		if (noTailExtend) ai.setTailExtended(false);
		EPoint head = ai.getHeadLocation();
		EPoint tail = ai.getTailLocation();
		if (head.getX() != tail.getX() && head.getY() != tail.getY())
			ai.setFixedAngle(false);

		// remember this arc for debugging
		if (DEBUGSTEPS)
		{
			Poly arcPoly = ai.makeLambdaPoly(ai.getGridBaseWidth(), Poly.Type.CLOSED);
			addedRectangles.add(ERectangle.fromLambda(arcPoly.getBounds2D()));
		}

		// now remove the generated layers from the Merge
		Poly [] polys = tech.getShapeOfArc(ai);
		for(int i=0; i<polys.length; i++)
		{
			Poly poly = polys[i];
			Layer layer = poly.getLayer();

			// make sure the geometric database is made up of proper layers
			layer = geometricLayer(layer);

			if (!usePureLayerNodes)
                removePolyFromMerge(merge, layer, poly);
		}
		return ai;
	}

	private void removePolyFromMerge(PolyMerge merge, Layer layer, Poly poly)
	{
		Point2D [] points = poly.getPoints();
		for(int i=0; i<points.length; i++)
			poly.setPoint(i, scaleUp(points[i].getX()), scaleUp(points[i].getY()));
		poly.roundPoints();
		merge.subtract(layer, poly);
	}

	private double scaleUp(double v)
	{
		return (v * SCALEFACTOR);
	}

	/**
	 * Method to compute the proper layer to use for a given other layer.
	 * Because pure-layer geometry may confuse similar layers that appear
	 * in Electric, all common layers are converted to a single one that will
	 * be unambiguous.  For example, all gate-poly is converted to poly-1.
	 * All diffusion layers are combined into one.  All layers that multiply-
	 * define a layer function are reduced to just one with that function.
	 * @param layer the layer found in the circuit.
	 * @return the layer to use instead.
	 */
	private Layer geometricLayer(Layer layer)
	{
		// layers from an alternate technology are accepted as-is
		if (layer.getTechnology() != tech) return layer;

		Layer.Function fun = layer.getFunction();

		// convert gate to poly1
		if (fun == Layer.Function.GATE)
		{
			Layer polyLayer = layerForFunction.get(Layer.Function.POLY1);
			if (polyLayer != null) return polyLayer;
		}

		// all active is one layer
		if (unifyActive)
		{
			if (fun == Layer.Function.DIFFP || fun == Layer.Function.DIFFN)
				fun = Layer.Function.DIFF;
		}

		// ensure the first one for the given function
		if (fun != Layer.Function.ART && layer.getFunctionExtras() != Layer.Function.INTERCONNECT)
		{
			Layer properLayer = layerForFunction.get(fun);
			if (properLayer != null) return properLayer;
		}
		return layer;

	}

	private static final double CLOSEDIST = SCALEFACTOR/50;

	private List<PolyBase> getMergePolys(PolyMerge merge, Layer layer, MutableInteger numIgnored)
	{
		List<PolyBase> polyList = merge.getMergedPoints(layer, true);
		if (polyList == null) return polyList;
		List<PolyBase> properPolyList = new ArrayList<PolyBase>();
		for(PolyBase poly : polyList)
		{
			// reduce common points
			Point2D [] origPoints = poly.getPoints();
			Point2D [] points = new Point2D[origPoints.length];
			int len = origPoints.length;
			for (int i=0; i<len; i++) points[i] = origPoints[i];
			for (int i=1; i<len; i++)
			{
				if (points[i].distance(points[i-1]) < CLOSEDIST)
				{
					for(int j=i; j<len; j++) points[j-1] = points[j];
					len--;
					i--;
				}
			}
			if (len > 1 && points[0].distance(points[len-1]) < CLOSEDIST) len--;

			// ignore polygons with no size
			if (len <= 2) continue;

			// anything smaller than the minimum number of grid units is ignored
			double area = poly.getArea();
			if (area < smallestPoly)
			{
				if (numIgnored != null) numIgnored.increment();
				continue;
			}

			properPolyList.add(poly);
		}
		return properPolyList;
	}

	/**
	 * Class for showing progress of extraction in a modeless dialog
	 */
	private static class ShowExtraction extends EDialog
	{
		private List<List<ERectangle>> addedBatchRectangles;
		private List<List<ERectangle>> addedBatchLines;
		private List<String> addedBatchNames;
		private int batchPosition;
		private JLabel comingUp;

		private ShowExtraction(Frame parent, List<List<ERectangle>> addedBatchRectangles,
			List<List<ERectangle>> addedBatchLines, List<String> addedBatchNames)
		{
			super(parent, false);
			this.addedBatchRectangles = addedBatchRectangles;
			this.addedBatchLines = addedBatchLines;
			this.addedBatchNames = addedBatchNames;

			getContentPane().setLayout(new GridBagLayout());
			setTitle("Extraction Progress");
			setName("");
			addWindowListener(new WindowAdapter()
			{
				public void windowClosing(WindowEvent evt) { closeDialog(); }
			});

			GridBagConstraints gbc;
			comingUp = new JLabel("Next step:");
			gbc = new GridBagConstraints();
			gbc.gridx = 0;   gbc.gridy = 0;
			gbc.gridwidth = 2;
			gbc.anchor = GridBagConstraints.WEST;
			gbc.insets = new Insets(4, 4, 4, 4);
			getContentPane().add(comingUp, gbc);

			JButton prev = new JButton("Prev");
			gbc = new GridBagConstraints();
			gbc.gridx = 0;   gbc.gridy = 1;
			gbc.anchor = GridBagConstraints.WEST;
			gbc.insets = new Insets(4, 4, 4, 4);
			prev.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent evt) { advanceDisplay(false); }
			});
			getContentPane().add(prev, gbc);

			JButton next = new JButton("Next");
			gbc = new GridBagConstraints();
			gbc.gridx = 1;   gbc.gridy = 1;
			gbc.anchor = GridBagConstraints.WEST;
			gbc.insets = new Insets(4, 4, 4, 4);
			next.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent evt) { advanceDisplay(true); }
			});
			getContentPane().add(next, gbc);

			batchPosition = -1;
			advanceDisplay(true);

			getRootPane().setDefaultButton(next);
			finishInitialization();
		}

		protected void escapePressed() { closeDialog(); }

		private void advanceDisplay(boolean forward)
		{
			if (forward)
			{
				batchPosition++;
				if (batchPosition >= addedBatchNames.size()) batchPosition = addedBatchNames.size()-1;
			} else
			{
				batchPosition--;
				if (batchPosition < 0) batchPosition = 0;
			}
			comingUp.setText("Batch " + (batchPosition+1) + ": " + addedBatchNames.get(batchPosition));
			pack();

			UserInterface ui = Job.getUserInterface();
			EditWindow_ wnd = ui.getCurrentEditWindow_();
			wnd.clearHighlighting();
			Cell cell = wnd.getCell();

			// highlight
			List<ERectangle> rects = addedBatchRectangles.get(batchPosition);
			for(ERectangle er : rects)
				wnd.addHighlightArea(er, cell);
			List<ERectangle> lines = addedBatchLines.get(batchPosition);
			for(ERectangle er : lines)
				wnd.addHighlightLine(new Point2D.Double(er.getMinX(), er.getMinY()),
                                     new Point2D.Double(er.getMaxX(), er.getMaxY()), cell, false, false);
			wnd.finishedHighlighting();
		}
	}
}
