/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: SeaOfGates.java
 * Routing tool: Sea of Gates control
 * Written by: Steven M. Rubin
 *
 * Copyright (c) 2007, Static Free Software. All rights reserved.
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
package com.sun.electric.tool.routing;

import com.sun.electric.database.EditingPreferences;
import com.sun.electric.database.geometry.Poly;
import com.sun.electric.database.geometry.PolyBase;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.text.TextUtils;
import com.sun.electric.database.topology.ArcInst;
import com.sun.electric.database.topology.Geometric;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.variable.EditWindow_;
import com.sun.electric.database.variable.UserInterface;
import com.sun.electric.database.variable.Variable;
import com.sun.electric.technology.ArcProto;
import com.sun.electric.technology.Layer;
import com.sun.electric.technology.PrimitiveNode;
import com.sun.electric.technology.Technology;
import com.sun.electric.technology.technologies.Generic;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.routing.Routing.SoGContactsStrategy;
import com.sun.electric.tool.routing.Routing.SoGNetOrder;
import com.sun.electric.tool.routing.seaOfGates.SeaOfGatesEngine;
import com.sun.electric.tool.routing.seaOfGates.SeaOfGatesEngineFactory.SeaOfGatesEngineType;
import com.sun.electric.tool.routing.seaOfGates.SeaOfGatesHandlers;
import com.sun.electric.util.ElapseTimer;
import com.sun.electric.util.TextUtils.UnitScale;
import com.sun.electric.util.math.DBMath;
import com.sun.electric.util.math.FixpTransform;
import com.sun.electric.util.math.MutableInteger;
import com.sun.electric.util.math.Orientation;

import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.PrintStream;
import java.io.Serializable;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Class to control sea-of-gates routing.
 */
public class SeaOfGates
{
    /**
     * Method to run Sea-of-Gates routing on the current cell.
     * Presumes that it is inside of a Job.
     * @param justSubCells true to run the router on all sub-cells of the current cell
     * (but not on the current cell itself).
     *
     *                  standard entry to routing is seaOfGatesRoute(false)
     */
    public static void seaOfGatesRoute(boolean justSubCells)
    {
    	if (justSubCells)
    	{
            // get cell
            UserInterface ui = Job.getUserInterface();
            Cell cell = ui.needCurrentCell();
            if (cell == null) return;
            Set<Cell> subCells =  new HashSet<Cell>();
            for(Iterator<NodeInst> it = cell.getNodes(); it.hasNext(); )
            {
            	NodeInst ni = it.next();
            	if (ni.isCellInstance()) subCells.add((Cell)ni.getProto());
            }
            for(Cell subCell : subCells)
            {
                List<ArcInst> selected = new ArrayList<ArcInst>();
                for (Iterator<ArcInst> it = subCell.getArcs(); it.hasNext(); )
                {
                	ArcInst ai = it.next();
                    if (ai.getProto() != Generic.tech().unrouted_arc) continue;
                    selected.add(ai);
                }
                if (!selected.isEmpty())
                {
                    // Run seaOfGatesRoute on subcell
                    SeaOfGatesHandlers.startInJob(subCell, selected, SeaOfGatesEngineType.defaultVersion);
                }
            }
    	} else
    	{
    		seaOfGatesRoute(SeaOfGatesEngineType.defaultVersion);
    	}
    }

    /**
     * Method to run Sea-of-Gates routing on the current cell, using a specified routing engine type.
     * Presumes that it is inside of a Job.
     *
     * standard entry to routing is seaOfGatesRoute(SeaOfGatesEngineType.defaultVersion)
     */
    public static void seaOfGatesRoute(SeaOfGatesEngineType version)
    {
        // get cell and network information
        UserInterface ui = Job.getUserInterface();
        Cell cell = ui.needCurrentCell();
        if (cell == null) return;

        // get list of selected nets
        List<ArcInst> selected = getSelected();
        if (selected == null) return;

        // make sure there is something to route
        if (selected.isEmpty())
        {
            ui.showErrorMessage("There are no Unrouted Arcs in this cell", "Routing Error");
            return;
        }

        // Run seaOfGatesRoute on selected unrouted arcs in a separate job
        SeaOfGatesHandlers.startInJob(cell, selected, version);
    }

    /**
     * Method to run Sea-of-Gates routing on the current cell, using a specified routing engine.
     * Presumes that it is inside of a Job.
     */
    public static void seaOfGatesRoute(EditingPreferences ep, SeaOfGatesEngine router)
    {
        if (router == null) {
            throw new NullPointerException();
        }

        // get cell and network information
        UserInterface ui = Job.getUserInterface();
        Cell cell = ui.needCurrentCell();
        if (cell == null) return;

        // get list of selected nets
        List<ArcInst> selected = getSelected();

        // make sure there is something to route
        if (selected.isEmpty())
        {
            ui.showErrorMessage("There are no Unrouted Arcs in this cell", "Routing Error");
            return;
        }

        // Run seaOfGatesRoute on selected unrouted arcs
        Job job = Job.getRunningJob();
		router.routeIt(SeaOfGatesHandlers.getDefault(cell, router.getPrefs().resultCellName,
            router.getPrefs().contactPlacementAction, job, ep), cell, false, selected);
    }

    private static List<ArcInst> getSelected() {
        EditWindow_ wnd = Job.getUserInterface().getCurrentEditWindow_();
        if (wnd == null) return null;
        Cell cell = wnd.getCell();
        if (cell == null) return null;

        // get list of selected nets
        List<ArcInst> selected = new ArrayList<ArcInst>();
        List<Geometric> highlighted = wnd.getHighlightedEObjs(false, true);
        for(Geometric h : highlighted)
        {
            ArcInst ai = (ArcInst)h;
            if (ai.getProto() != Generic.tech().unrouted_arc) continue;
            selected.add(ai);
        }
        if (selected.isEmpty())
        {
            for (Iterator<ArcInst> it = cell.getArcs(); it.hasNext(); )
            {
            	ArcInst ai = it.next();
                if (ai.getProto() != Generic.tech().unrouted_arc) continue;
                selected.add(ai);
            }
        }
        return selected;
    }

    /**
     * Class that defines a track on which layers can be routed.
     * The track has both a coordinate (in X or Y) and a mask number (0 if no mask information is specified)
     */
    public static class SeaOfGatesTrack implements Comparable
    {
    	private double coordinate;
    	private int maskNum;

    	public SeaOfGatesTrack(double v, int c)
    	{
    		coordinate = v;
    		maskNum = c;
    	}

    	public double getCoordinate() { return coordinate; }

    	public int getMaskNum() { return maskNum; }

    	/**
         * Compares this SeaOfGatesTrack with the specified object for order.  Returns a
         * negative integer, zero, or a positive integer as this object is less
         * than, equal to, or greater than the specified object.<p>
         * @param   o the Object to be compared.
         * @return  a negative integer, zero, or a positive integer if this object
         *		is less than, equal to, or greater than the specified object.
         *
         * @throws ClassCastException if the specified object's type prevents it
         *         from being compared to this Object.
         */
        public int compareTo(Object o)
    	{
        	double other = ((SeaOfGatesTrack)o).coordinate;
        	double diff = coordinate - other;
        	if (diff < 0) return -1;
        	if (diff > 0) return 1;
        	return 0;
    	}

	    public static int getSpecificMaskNumber(String layerName)
	    {
			int maskNum = 0;
			char lastChar = layerName.charAt(layerName.length()-1);
			if (!Character.isDigit(lastChar))
				maskNum = Character.toLowerCase(lastChar) - 'a' + 1;
			return maskNum;
	    }
    }

    /**
     * Class to hold preferences during Sea-of-Gates routing run.
     */
    public static class SeaOfGatesOptions implements Serializable
    {
        public boolean useParallelFromToRoutes;
        public boolean useParallelRoutes;
        public boolean runOnConnectedRoutes;
        public SoGContactsStrategy contactPlacementAction;
        public double maxArcWidth;
        public int complexityLimit, rerunComplexityLimit;
        public int maxDistance;
        public boolean useGlobalRouter, reRunFailedRoutes, enableSpineRouting, disableAdvancedSpineRouting;
        public int forcedNumberOfThreads;
        public String resultCellName;
        public ElapseTimer theTimer;
        public PrintStream qualityPrintStream;
        public SoGNetOrder netOrder;

        public SeaOfGatesOptions()
        {
            useParallelFromToRoutes = true;
            useParallelRoutes = false;
            runOnConnectedRoutes = true;
            contactPlacementAction = SoGContactsStrategy.SOGCONTACTSATTOPLEVEL;
            maxArcWidth = 10;
            complexityLimit = 200000;
            rerunComplexityLimit = 400000;
            useGlobalRouter = false;
            reRunFailedRoutes = false;
            enableSpineRouting = false;
            disableAdvancedSpineRouting = false;
            forcedNumberOfThreads = 0;
            resultCellName = null;
            qualityPrintStream = null;
            maxDistance = 2; // original default
            netOrder = SoGNetOrder.SOGNETORDERORIGINAL; // original strategy
        }

        public void getOptionsFromPreferences(boolean factory)
        {
        	if (factory)
        	{
	            useParallelFromToRoutes = Routing.isFactorySeaOfGatesUseParallelFromToRoutes();
	            useParallelRoutes = Routing.isFactorySeaOfGatesUseParallelRoutes();
	            runOnConnectedRoutes = Routing.isFactorySeaOfGatesRunOnConnectedRoutes();
	            contactPlacementAction = SoGContactsStrategy.findByLevel(Routing.getFactorySeaOfGatesContactPlacementAction());
	            maxArcWidth = Routing.getFactorySeaOfGatesMaxWidth();
	            complexityLimit = Routing.getFactorySeaOfGatesComplexityLimit();
	            rerunComplexityLimit = Routing.getFactorySeaOfGatesRerunComplexityLimit();
	            useGlobalRouter = Routing.isFactorySeaOfGatesUseGlobalRouting();
	            reRunFailedRoutes = Routing.isFactorySeaOfGatesRerunFailedRoutes();
	            enableSpineRouting = Routing.isFactorySeaOfGatesEnableSpineRouting();
	            forcedNumberOfThreads = Routing.getFactorySeaOfGatesForcedProcessorCount();
	            maxDistance = Routing.getFactorySeaOfGatesMaxDistance();
	            netOrder = Routing.getFactorySeaOfGatesNetOrder();
        	}
        	else
        	{
	            useParallelFromToRoutes = Routing.isSeaOfGatesUseParallelFromToRoutes();
	            useParallelRoutes = Routing.isSeaOfGatesUseParallelRoutes();
	            runOnConnectedRoutes = Routing.isSeaOfGatesRunOnConnectedRoutes();
	            contactPlacementAction = SoGContactsStrategy.findByLevel(Routing.getSeaOfGatesContactPlacementAction());
	            maxArcWidth = Routing.getSeaOfGatesMaxWidth();
	            complexityLimit = Routing.getSeaOfGatesComplexityLimit();
	            rerunComplexityLimit = Routing.getSeaOfGatesRerunComplexityLimit();
	            useGlobalRouter = Routing.isSeaOfGatesUseGlobalRouting();
	            reRunFailedRoutes = Routing.isSeaOfGatesRerunFailedRoutes();
	            enableSpineRouting = Routing.isSeaOfGatesEnableSpineRouting();
	            forcedNumberOfThreads = Routing.getSeaOfGatesForcedProcessorCount();
	            maxDistance = Routing.getSeaOfGatesMaxDistance();
	            netOrder = Routing.getSeaOfGatesNetOrder();
        	}
        }

        /**
         * Method to print active preferences for debugging purposes
         */
        @Override
        public String toString()
        {
        	return "SoG Preferences = (" +
        			"useParallelFromToRoutes=" + useParallelFromToRoutes + ", " +
        			"useParallelRoutes=" + useParallelRoutes + ", " +
        			"runOnConnectedRoutes=" + runOnConnectedRoutes + ",\n " +
        			"contactPlacementAction=" + contactPlacementAction + ", " +
        			"maxArcWidth=" + maxArcWidth + ", " +
        			"complexityLimit=" + complexityLimit + ",\n " +
        			"rerunComplexityLimit=" + rerunComplexityLimit + ", " +
        			"useGlobalRouter=" + useGlobalRouter + ", " +
        			"reRunFailedRoutes=" + reRunFailedRoutes + ",\n " +
        			"forcedNumberOfThreads=" + forcedNumberOfThreads + ", " +
        			"maxDistance=" + maxDistance + ", " +
        			"netOrder=" + netOrder.getValue() +
        			")";
        }
    }

    public static class SeaOfGatesArcProperties implements Serializable
    {
    	private Double overrideWidth;
    	private Double overrideSpacingX;
    	private Double overrideSpacingY;
    	private Double widthOf2X;
    	private Double taperTo1X;

    	public SeaOfGatesArcProperties()
    	{
    		overrideWidth = null;
    		overrideSpacingX = null;
    		overrideSpacingY = null;
    		widthOf2X = null;
    		taperTo1X = null;
    	}

    	public void setWidthOverride(Double w) { overrideWidth = w; }
    	public Double getWidthOverride() { return overrideWidth; }

    	public void setSpacingOverride(Double s, int axis)
    	{
    		if (axis == 0) overrideSpacingX = s;
    		else overrideSpacingY = s;
    	}

    	public Double getSpacingOverride(int axis)
    	{
    		return (axis == 0) ? overrideSpacingX : overrideSpacingY;
    	}

    	public void setWidthOf2X(Double w) { widthOf2X = w; }
    	public Double getWidthOf2X() { return widthOf2X; }

    	public void setTaperTo1X(Double t) { taperTo1X = t; }
    	public Double getTaperTo1X() { return taperTo1X; }
    }

    public static class SeaOfGatesExtraBlockage implements Serializable
    {
    	double x1, y1, x2, y2;
    	ArcProto met;

    	public SeaOfGatesExtraBlockage(double x1, double y1, double x2, double y2, ArcProto m)
    	{
    		this.x1 = x1;
    		this.y1 = y1;
    		this.x2 = x2;
    		this.y2 = y2;
    		met = m;
    	}

    	/**
    	 * method to get the layer of this blockage.
    	 * @return an ArcProto that is on the blockage layer.
    	 */
    	public ArcProto getLayer() { return met; }

    	/**
    	 * Method to get the low X of the blockage.
    	 * @return the low X of the blockage.
    	 */
    	public double getLX() { return Math.min(x1, x2); }

    	/**
    	 * Method to get the high X of the blockage.
    	 * @return the high X of the blockage.
    	 */
    	public double getHX() { return Math.max(x1, x2); }

    	/**
    	 * Method to get the low Y of the blockage.
    	 * @return the low Y of the blockage.
    	 */
    	public double getLY() { return Math.min(y1, y2); }

    	/**
    	 * Method to get the high Y of the blockage.
    	 * @return the high Y of the blockage.
    	 */
    	public double getHY() { return Math.max(y1, y2); }
    }

    /**
     * Class to define Sea-of-Gates routing parameters that apply only to a specific Cell.
     */
    public static class SeaOfGatesCellParameters implements Serializable
	{
    	private Cell cell;
    	private boolean steinerDone, canPlaceContactDownToAvoidedLayer, canPlaceContactUpToAvoidedLayer;
    	private boolean forceHorVer, favorHorVer, horEven, canRotateContacts;
    	private Map<ArcProto,String> gridSpacing;
    	private Map<ArcProto,String> removeLayers;
    	private Set<ArcProto> preventedArcs, favoredArcs, taperOnlyArcs, forceGridArcs;
    	private Map<ArcProto,SeaOfGatesArcProperties> overrides;
    	private List<String> netsToRoute;
    	private Map<String,List<ArcProto>> netsAndArcsToRoute;
    	private Map<String,Map<ArcProto,SeaOfGatesArcProperties>> netAndArcOverrides;
    	private List<SeaOfGatesExtraBlockage> extraBlockages;
    	private String ignorePrimitives; // regular expression to ignore certain primitives - null by default
    	private String acceptOnly1XPrimitives; // regular expression to accept only certain 1x (square vias) primitives - null by default
    	private String acceptOnly2XPrimitives; // regular expression to accept only certain 1x (square vias) primitives - null by default
    	private String routingBoundsLayerName;

		/** key of Variable holding SOG parameters. */	private static final Variable.Key ROUTING_SOG_PARAMETERS_KEY = Variable.newKey("ATTR_ROUTING_SOG_PARAMETERS");

		public void clear()
		{
			steinerDone = false;
			canPlaceContactDownToAvoidedLayer = true;
			canPlaceContactUpToAvoidedLayer = true;
			forceHorVer = false;
			favorHorVer = true;
			horEven = false;
			canRotateContacts = true;
			gridSpacing = new HashMap<ArcProto,String>();
			removeLayers = new HashMap<ArcProto,String>();
			preventedArcs = new HashSet<ArcProto>();
			favoredArcs = new HashSet<ArcProto>();
			forceGridArcs = new HashSet<ArcProto>();
			taperOnlyArcs = new HashSet<ArcProto>();
			overrides = new HashMap<ArcProto,SeaOfGatesArcProperties>();
			netsToRoute = new ArrayList<String>();
			netsAndArcsToRoute = new TreeMap<String,List<ArcProto>>();
			netAndArcOverrides = new HashMap<String,Map<ArcProto,SeaOfGatesArcProperties>>();
			extraBlockages = new ArrayList<SeaOfGatesExtraBlockage>();
			ignorePrimitives = null;
			acceptOnly1XPrimitives = null;
			acceptOnly2XPrimitives = null;
			routingBoundsLayerName = null;
		}

		/**
		 * Constructor for the SOG parameters specific to a given Cell.
		 * @param cell the Cell in question.
		 */
		public SeaOfGatesCellParameters(Cell cell)
		{
			this.cell = cell;
			clear();

			Variable var = cell.getVar(ROUTING_SOG_PARAMETERS_KEY);
			if (var != null)
			{
				String[] lines = (String[])var.getObject();
				for(int i=0; i<lines.length; i++)
				{
					String[] parts = lines[i].split(" ");
					if (parts.length <= 0) continue;
					if (parts[0].startsWith(";")) continue;
					if (parts[0].equalsIgnoreCase("SteinerTreesDone"))
					{
						steinerDone = true;
						continue;
					}
					if (parts[0].equalsIgnoreCase("PreventContactDownToAvoidedLayer"))
					{
						canPlaceContactDownToAvoidedLayer = false;
						continue;
					}
					if (parts[0].equalsIgnoreCase("PreventContactUpToAvoidedLayer"))
					{
						canPlaceContactUpToAvoidedLayer = false;
						continue;
					}
					if (parts[0].equalsIgnoreCase("NoContactRotation"))
					{
						canRotateContacts = false;
						continue;
					}
					if (parts[0].equalsIgnoreCase("Accept1XContacts"))
					{
						int spacePos = lines[i].indexOf(' ');
						if (spacePos > 0) acceptOnly1XPrimitives = lines[i].substring(spacePos+1);
						continue;
					}
					if (parts[0].equalsIgnoreCase("Accept2XContacts"))
					{
						int spacePos = lines[i].indexOf(' ');
						if (spacePos > 0) acceptOnly2XPrimitives = lines[i].substring(spacePos+1);
						continue;
					}
					if (parts[0].equalsIgnoreCase("RoutingBoundsLayerName"))
					{
						int spacePos = lines[i].indexOf(' ');
						if (spacePos > 0) routingBoundsLayerName = lines[i].substring(spacePos+1);
						continue;
					}
					if (parts[0].equalsIgnoreCase("IgnoreContacts"))
					{
						int spacePos = lines[i].indexOf(' ');
						if (spacePos > 0) ignorePrimitives = lines[i].substring(spacePos+1);
						continue;
					}
					if (parts[0].equalsIgnoreCase("ForceHorVer"))
					{
						forceHorVer = true;
						continue;
					}
					if (parts[0].equalsIgnoreCase("IgnoreHorVer"))
					{
						favorHorVer = false;
						continue;
					}
					if (parts[0].equalsIgnoreCase("HorizontalEven"))
					{
						horEven = true;
						continue;
					}
					if (parts[0].equalsIgnoreCase("ArcGrid") && parts.length >= 3)
					{
						ArcProto ap = parseArcName(parts[1]);
						if (ap != null) gridSpacing.put(ap, parts[2]);
						continue;
					}
					if (parts[0].equalsIgnoreCase("ArcAvoid") && parts.length >= 2)
					{
						ArcProto ap = parseArcName(parts[1]);
						if (ap != null) preventedArcs.add(ap);
						continue;
					}
					if (parts[0].equalsIgnoreCase("ArcFavor") && parts.length >= 2)
					{
						ArcProto ap = parseArcName(parts[1]);
						if (ap != null) favoredArcs.add(ap);
						continue;
					}
					if (parts[0].equalsIgnoreCase("ArcGridForce") && parts.length >= 2)
					{
						ArcProto ap = parseArcName(parts[1]);
						if (ap != null) forceGridArcs.add(ap);
						continue;
					}
					if (parts[0].equalsIgnoreCase("ArcTaperOnly") && parts.length >= 2)
					{
						ArcProto ap = parseArcName(parts[1]);
						if (ap != null) taperOnlyArcs.add(ap);
						continue;
					}
					if (parts[0].equalsIgnoreCase("ArcWidthOverride") && parts.length >= 3)
					{
						ArcProto ap = parseArcName(parts[1]);
						if (ap != null)
						{
							SeaOfGatesArcProperties sogap = overrides.get(ap);
							if (sogap == null) overrides.put(ap, sogap = new SeaOfGatesArcProperties());
							sogap.setWidthOverride(TextUtils.atof(parts[2]));
						}
						continue;
					}
					// Accept ArcSpacingOverrideX and ArcSpacingOverrideY
					if (parts[0].toLowerCase().startsWith("arcspacingoverride") && parts.length >= 3)
					{
						int axis = (parts[0].substring(parts[0].length()-1).equals("X")) ? 0 : 1;
						ArcProto ap = parseArcName(parts[1]);
						if (ap != null)
						{
							SeaOfGatesArcProperties sogap = overrides.get(ap);
							if (sogap == null) overrides.put(ap, sogap = new SeaOfGatesArcProperties());
							sogap.setSpacingOverride(TextUtils.atof(parts[2]), axis);
						}
						continue;
					}
					if (parts[0].equalsIgnoreCase("WidthOf2X") && parts.length >= 3)
					{
						ArcProto ap = parseArcName(parts[1]);
						if (ap != null)
						{
							SeaOfGatesArcProperties sogap = overrides.get(ap);
							if (sogap == null) overrides.put(ap, sogap = new SeaOfGatesArcProperties());
							sogap.setWidthOf2X(TextUtils.atof(parts[2]));
						}
						continue;
					}
					if (parts[0].equalsIgnoreCase("TaperTo1X") && parts.length >= 3)
					{
						ArcProto ap = parseArcName(parts[1]);
						if (ap != null)
						{
							SeaOfGatesArcProperties sogap = overrides.get(ap);
							if (sogap == null) overrides.put(ap, sogap = new SeaOfGatesArcProperties());
							sogap.setTaperTo1X(TextUtils.atof(parts[2]));
						}
						continue;
					}
					if (parts[0].equalsIgnoreCase("RemoveLayer") && parts.length >= 3)
					{
						ArcProto ap = parseArcName(parts[1]);
						if (ap != null) removeLayers.put(ap, parts[2]);
						continue;
					}
					if (parts[0].equalsIgnoreCase("Blockage") && parts.length >= 6)
					{
						ArcProto ap = parseArcName(parts[1]);
						double x1 = TextUtils.atof(parts[2]);
						double y1 = TextUtils.atof(parts[3]);
						double x2 = TextUtils.atof(parts[4]);
						double y2 = TextUtils.atof(parts[5]);
						SeaOfGatesExtraBlockage sogeb = new SeaOfGatesExtraBlockage(x1, y1, x2, y2, ap);
						extraBlockages.add(sogeb);
						continue;
					}
					if (parts[0].equalsIgnoreCase("Network") && parts.length >= 2)
					{
						String netName = parts[1];
						List<ArcProto> arcs = netsAndArcsToRoute.get(netName);
						if (arcs == null) netsAndArcsToRoute.put(netName, arcs = new ArrayList<ArcProto>());
						netsToRoute.add(netName);
						for(int p=2; p<parts.length; p++)
						{
							ArcProto ap = parseArcName(parts[p]);
							arcs.add(ap);
						}
						continue;
					}
					if (parts[0].equalsIgnoreCase("NetworkOverride") && parts.length >= 2)
					{
						String netName = parts[1];
						Map<ArcProto,SeaOfGatesArcProperties> arcs = netAndArcOverrides.get(netName);
						if (arcs == null) netAndArcOverrides.put(netName, arcs = new HashMap<ArcProto,SeaOfGatesArcProperties>());
						SeaOfGatesArcProperties curOverrides = null;
						for(int p=2; p<parts.length; p++)
						{
							if (parts[p].startsWith("W="))
							{
								if (curOverrides != null)
									curOverrides.setWidthOverride(TextUtils.atof(parts[p].substring(2)));
								continue;
							}
							if (parts[p].startsWith("S="))
							{
								if (curOverrides != null)
								{
									curOverrides.setSpacingOverride(TextUtils.atof(parts[p].substring(2)), 0);
									curOverrides.setSpacingOverride(TextUtils.atof(parts[p].substring(2)), 1);
								}
								continue;
							}
							ArcProto ap = parseArcName(parts[p]);
							curOverrides = arcs.get(ap);
							if (curOverrides == null) arcs.put(ap, curOverrides = new SeaOfGatesArcProperties());
							continue;
						}
						continue;
					}
				}
			}
		}

	    /**
	     * Method to import the control file for routing.
	     * @param fileName the name of the control file.
	     * @param curTech the current technology to use for this.
	     */
	    public void importData(String fileName, Cell cell, Technology curTech)
	    {
	    	URL url = TextUtils.makeURLToFile(fileName);
	    	try
	    	{
	    		URLConnection urlCon = url.openConnection();
	    		InputStreamReader is = new InputStreamReader(urlCon.getInputStream());
	    		LineNumberReader lineReader = new LineNumberReader(is);
	    		clear();
	    		List<ArcProto> layersToOverride = new ArrayList<ArcProto>();
	    		Map<ArcProto,Double> widthsToOverride = new HashMap<ArcProto,Double>();
	    		Map<ArcProto,Double> spacingsToOverride = new HashMap<ArcProto,Double>();
	    		boolean figuredOutAlternation = false;
	    		forceHorVer = true;
	    		canRotateContacts = false;
	    		canPlaceContactDownToAvoidedLayer = false;
	    		canPlaceContactUpToAvoidedLayer = false;
	    		List<ArcProto> arcDefList = new ArrayList<ArcProto>();
	    		List<ArcProto> tmpList = new ArrayList<ArcProto>(3);
				String[] extraLayers = {"", Layer.DEFAULT_MASK_NAME+"1", Layer.DEFAULT_MASK_NAME+"2"}; // the first one is the typical color
				Map<String,List<SeaOfGatesTrack>> trackLines = new HashMap<String,List<SeaOfGatesTrack>>();

				for(;;)
	    		{
	    			String buf = lineReader.readLine();
	    			if (buf == null) break;
	    			if (buf.length() == 0 || buf.startsWith(";")) continue;

	    			List<String> slist = new ArrayList<String>();
	    			StringTokenizer p = new StringTokenizer(buf, " ", false);
	                while (p.hasMoreTokens())
	                	slist.add(p.nextToken());
	                String[] parts = slist.toArray(new String[slist.size()]);

	                if (parts.length <= 0) continue;

					if (parts[0].equalsIgnoreCase("Project")) continue;
					if (parts[0].equalsIgnoreCase("Library")) continue;
					if (parts[0].equalsIgnoreCase("View")) continue;
					if (parts[0].equalsIgnoreCase("PowerNets")) continue;

					if (parts[0].equalsIgnoreCase("Cell"))
					{
						if (!getCell().getName().equals(parts[1]))
						{
							System.out.println("WARNING: Cell name " + parts[1] + " on line " + lineReader.getLineNumber() +
								" doesn't match current cell (" + getCell().describe(false) + ")");
						}
						continue;
					}

					if (parts[0].equalsIgnoreCase("RoutingBoundsLayer"))
					{
						// define routing bound layer
						assert(parts.length > 1);
						routingBoundsLayerName = parts[1];
						continue;
					}

					if (parts[0].equalsIgnoreCase("HorizontalEven"))
					{
						// default is even metals vertical with
						horEven = true;
						continue;
					}

					if (parts[0].equalsIgnoreCase("AllowViaDown"))
					{
						// can place contacts down to avoided layers
						canPlaceContactDownToAvoidedLayer = true;
						continue;
					}

					if (parts[0].equalsIgnoreCase("AllowViaUp"))
					{
						// can place contacts up to avoided layers
						canPlaceContactUpToAvoidedLayer = true;
						continue;
					}

					if (parts[0].equalsIgnoreCase("ContactInclusion"))
					{
						if (parts.length > 1 && !parts[1].isEmpty())
							acceptOnly1XPrimitives = parts[1];
						else
							System.out.println("WARNING: no regular expression for ContactInclusion on line " + lineReader.getLineNumber());
						continue;
					}

					if (parts[0].equalsIgnoreCase("ContactInclusion2X"))
					{
						if (parts.length > 1 && !parts[1].isEmpty())
							acceptOnly2XPrimitives = parts[1];
						else
							System.out.println("WARNING: no regular expression for ContactInclusion2x on line " + lineReader.getLineNumber());
						continue;
					}

					// HorizontalMetals M<X> <anything> where X is a number
					if (parts[0].equalsIgnoreCase("HorizontalMetals"))
					{
						assert(parts.length > 1);
						// just getting first element
						List<ArcProto> apList = getArcProtoList(parts[1], lineReader.getLineNumber(), curTech, extraLayers);
						if (apList == null)
						{
							System.out.println("ERROR parsing line " + lineReader.getLineNumber() + " of file");
							continue;
						}
						for (ArcProto ap: apList)
						{
							int level = ap.getFunction().getLevel();
							this.horEven = level%2 == 0;
						}
						continue;
					}

					// VerticalMetals M<X> <anything> where X is a number
					if (parts[0].equalsIgnoreCase("VerticalMetals"))
					{
						assert(parts.length > 1);
						// just getting first element
						List<ArcProto> apList = getArcProtoList(parts[1], lineReader.getLineNumber(), curTech, extraLayers);
						if (apList == null)
						{
							System.out.println("ERROR parsing line " + lineReader.getLineNumber() + " of file");
							continue;
						}
						for (ArcProto ap: apList)
						{
							int level = ap.getFunction().getLevel();
							this.horEven = level%2 != 0;
						}
						continue;
					}

					if (parts[0].equalsIgnoreCase("DefaultRouteMetals"))
					{
						Set<ArcProto> allowed = new HashSet<ArcProto>();
						for(int i=1; i<parts.length; i++)
						{
							List<ArcProto> apList = getArcProtoList(parts[i], lineReader.getLineNumber(), curTech, extraLayers);
							if (apList != null) allowed.addAll(apList);
						}
						for(Iterator<ArcProto> it = curTech.getArcs(); it.hasNext(); )
						{
							ArcProto ap = it.next();
							setPrevented(ap, !allowed.contains(ap));
						}
						continue;
					}

					if (parts[0].equalsIgnoreCase("TaperOnlyMetal"))
					{
						Set<ArcProto> allowed = new HashSet<ArcProto>();
						for(int i=1; i<parts.length; i++)
						{
							List<ArcProto> apList = getArcProtoList(parts[i], lineReader.getLineNumber(), curTech, extraLayers);
							if (apList != null) allowed.addAll(apList);
						}
						for(ArcProto ap : allowed)
							setTaperOnly(ap, true);
						continue;
					}

					if (parts[0].equalsIgnoreCase("DefaultMetalWS") && parts.length >= 5)
					{
						tmpList.clear();
						List<ArcProto> apList = getArcProtoList(parts[1], lineReader.getLineNumber(), curTech, extraLayers);
						if (apList == null) continue;

						// Checking there is previous definition of those layers
						for (ArcProto arc : apList)
						{
							if (arcDefList.contains(arc)) // value already defined
							{
								System.out.println("Metal width and spacing already defined for " + arc.getName() + " Skipping second definition");
								continue;
							}
							arcDefList.add(arc);

							double width = TextUtils.atof(parts[2]);
							width = TextUtils.convertFromDistance(width, curTech, UnitScale.MICRO);
							setDefaultWidthOverride(arc, width);

							double spacingX = TextUtils.atof(parts[3]);
							spacingX = TextUtils.convertFromDistance(spacingX, curTech, UnitScale.MICRO);
							setDefaultSpacingOverride(arc, spacingX, 0);

							double spacingY = TextUtils.atof(parts[4]);
							spacingY = TextUtils.convertFromDistance(spacingY, curTech, UnitScale.MICRO);
							setDefaultSpacingOverride(arc, spacingY, 1);

							if (parts.length >= 6)
							{
								double width2X = TextUtils.atof(parts[5]);
								width2X = TextUtils.convertFromDistance(width2X, curTech, UnitScale.MICRO);
								set2XWidth(arc, width2X);
							}
						}
						continue;
					}

					if (parts[0].equalsIgnoreCase("Taper") && parts.length >= 3)
					{
						tmpList.clear();
						List<ArcProto> apList = getArcProtoList(parts[1], lineReader.getLineNumber(), curTech, extraLayers);
						if (apList == null) continue;
						for (ArcProto arc : apList)
						{
							double taperLength = TextUtils.atof(parts[2]);
							taperLength = TextUtils.convertFromDistance(taperLength, curTech, UnitScale.MICRO);
							setTaperLength(arc, taperLength);
						}
						continue;
					}

					if (parts[0].equalsIgnoreCase("CutLayer"))
					{
						if (parts.length < 3)
						{
							System.out.println("ERROR on line " + lineReader.getLineNumber() +
								": RemoveLayer needs two parameters: <layer> <layerWhichCuts>");
							continue;
						}
						List<ArcProto> apList = getArcProtoList(parts[1], lineReader.getLineNumber(), curTech, extraLayers);
						if (apList == null) continue;
						int maskNum = SeaOfGatesTrack.getSpecificMaskNumber(parts[1]);
						for (ArcProto ap : apList)
						{
							if (ap.getMaskLayer() != maskNum) continue;
							removeLayers.put(ap, parts[2]);
						}
						continue;
					}

					if (parts[0].equalsIgnoreCase("Blockage"))
					{
						if (parts.length < 6)
						{
							System.out.println("ERROR: invalid blockage information in line " + lineReader.getLineNumber());
							continue;
						}
						List<ArcProto> apList = getArcProtoList(parts[1], lineReader.getLineNumber(), curTech, extraLayers);
						if (apList == null) continue;
						if (apList.size() < 1)
						{
							System.out.println("ERROR: unknown arc on line " + lineReader.getLineNumber());
							continue;
						}
						ArcProto met = apList.get(0);
						double x1 = TextUtils.atof(parts[2]);
						x1 = TextUtils.convertFromDistance(x1, curTech, UnitScale.MICRO);
						double y1 = TextUtils.atof(parts[3]);
						y1 = TextUtils.convertFromDistance(y1, curTech, UnitScale.MICRO);
						double x2 = TextUtils.atof(parts[4]);
						x2 = TextUtils.convertFromDistance(x2, curTech, UnitScale.MICRO);
						double y2 = TextUtils.atof(parts[5]);
						y2 = TextUtils.convertFromDistance(y2, curTech, UnitScale.MICRO);
						SeaOfGatesExtraBlockage sogeb = new SeaOfGatesExtraBlockage(x1, y1, x2, y2, met);
						extraBlockages.add(sogeb);
						continue;
					}

					if (parts[0].equalsIgnoreCase("Track"))
					{
						if (parts.length < 6)
						{
							System.out.println("ERROR: invalid track information in line " + lineReader.getLineNumber());
							continue;
						}

						List<ArcProto> apList = getArcProtoList(parts[1], lineReader.getLineNumber(), curTech, extraLayers);
						if (apList == null) continue;
						int maskNum = SeaOfGatesTrack.getSpecificMaskNumber(parts[1]);
						for (ArcProto ap : apList)
						{
							// @NOTE: doesn't check if track in same position was defined before
							int arcLevel = ap.getFunction().getLevel();
							boolean wantHorEven;
							if (parts[2].equalsIgnoreCase("t"))
							{
								// this metal is horizontal
								wantHorEven = (arcLevel%2) == 0;
							} else
							{
								// this metal is vertical
								wantHorEven = (arcLevel%2) != 0;
							}
							if (figuredOutAlternation)
							{
								if (wantHorEven != horEven)
									System.out.println("Warning: Horizontal layers are " + (horEven?"":" not") + " even but metal " +
										parts[1] + " has horizontal set to " + parts[2]);
							} else
							{
								horEven = wantHorEven;
								figuredOutAlternation = true;
							}

							// get former grid data
							Set<SeaOfGatesTrack> gridValues = new TreeSet<SeaOfGatesTrack>();
							String formerGrid = gridSpacing.get(ap);
							if (formerGrid != null)
							{
								String[] gridParts = formerGrid.split(",");
								for(int i=0; i<gridParts.length; i++)
								{
									String part = gridParts[i].trim();
									if (part.length() == 0) continue;
									int trackColor = SeaOfGatesTrack.getSpecificMaskNumber(part);
									if (!Character.isDigit(part.charAt(part.length()-1)))
										part = part.substring(0, part.length()-1);
									double val = TextUtils.atof(part);
									gridValues.add(new SeaOfGatesTrack(val, trackColor));
								}
							}

							// add in new grid data
							List<SeaOfGatesTrack> tracksOnThisLine = new ArrayList<SeaOfGatesTrack>();
							trackLines.put(buf, tracksOnThisLine);

							double coord = TextUtils.atof(parts[3]);
							coord = TextUtils.convertFromDistance(coord, curTech, UnitScale.MICRO);
							double spacing = TextUtils.atof(parts[4]);
							spacing = TextUtils.convertFromDistance(spacing, curTech, UnitScale.MICRO);
							int numTracks = TextUtils.atoi(parts[5]);
							for(int i=0; i<numTracks; i++)
							{
								SeaOfGatesTrack sogt = new SeaOfGatesTrack(coord, maskNum);
								tracksOnThisLine.add(sogt);
								gridValues.add(sogt);
								coord += spacing;
							}

							// build new list
							String newGrid = "";
							for(SeaOfGatesTrack sogt : gridValues)
							{
								if (newGrid.length() > 0) newGrid += ",";
								newGrid += TextUtils.formatDouble(sogt.coordinate);
								if (sogt.maskNum != 0) newGrid += (char)('a' + sogt.maskNum - 1);
							}
							gridSpacing.put(ap, newGrid);
							forceGridArcs.add(ap);
						}
						continue;
					}

					if (parts[0].equalsIgnoreCase("Net"))
					{
						if (parts.length < 2)
						{
							System.out.println("ERROR: invalid Net information in line " + lineReader.getLineNumber());
							continue;
						}
						parts[1] = parts[1].replace("<", "[").replace(">", "]");
						addNetToRoute(parts[1]);
						for(ArcProto ap : layersToOverride)
							addArcToNet(parts[1], ap);
						for(ArcProto ap : widthsToOverride.keySet())
						{
							Double width = widthsToOverride.get(ap);
							setWidthOverrideForArcOnNet(parts[1], ap, width);
						}
						for(ArcProto ap : spacingsToOverride.keySet())
						{
							Double spacing = spacingsToOverride.get(ap);
							setSpacingOverrideForArcOnNet(parts[1], ap, spacing);
						}
						layersToOverride.clear();
						widthsToOverride.clear();
						spacingsToOverride.clear();
						continue;
					}

					if (parts[0].equalsIgnoreCase("Layers"))
					{
						for(int i=1; i<parts.length; i++)
						{
							List<ArcProto> apList = getArcProtoList(parts[i], lineReader.getLineNumber(), curTech, extraLayers);
							if (apList == null) continue;
							for (ArcProto ap : apList)
								layersToOverride.add(ap);
						}
						continue;
					}

					if (parts[0].equalsIgnoreCase("Width"))
					{
						for(int i=1; i<parts.length; i += 2)
						{
							List<ArcProto> apList = getArcProtoList(parts[i], lineReader.getLineNumber(), curTech, extraLayers);
							if (apList == null) continue;
							for (ArcProto ap : apList)
							{
								double v = TextUtils.atof(parts[i+1]);
								v = TextUtils.convertFromDistance(v, curTech, UnitScale.MICRO);
								widthsToOverride.put(ap, new Double(v));
							}
						}
						continue;
					}

					if (parts[0].equalsIgnoreCase("Spacing"))
					{
						for(int i=1; i<parts.length; i += 2)
						{
							List<ArcProto> apList = getArcProtoList(parts[i], lineReader.getLineNumber(), curTech, extraLayers);
							if (apList == null) continue;
							for (ArcProto ap : apList)
							{
								double v = TextUtils.atof(parts[i+1]);
								v = TextUtils.convertFromDistance(v, curTech, UnitScale.MICRO);
								spacingsToOverride.put(ap, new Double(v));
							}
						}
						continue;
					}

					System.out.println("WARNING: unknown keyword on line " + lineReader.getLineNumber() + ": " + buf);
	    		}
	    		lineReader.close();

	    		// check validity of tracks
	    		Map<Integer,Map<Double,Map<Integer,MutableInteger>>> cellData = null;
	    		Map<Integer,MutableInteger> cellDataHor = null;
	    		Map<Integer,MutableInteger> cellDataVer = null;
	    		Map<String,List<String>> messages = null;
				for(ArcProto ap : gridSpacing.keySet())
	    		{
					if (cellData == null)
					{
			    		cellData = new HashMap<Integer,Map<Double,Map<Integer,MutableInteger>>>();
			    		cellDataHor = new HashMap<Integer,MutableInteger>();
			    		cellDataVer = new HashMap<Integer,MutableInteger>();
			    		messages = new HashMap<String,List<String>>();
			    		gatherArea(cell, Orientation.IDENT.pureRotate(), cellData, cellDataHor, cellDataVer);
					}

	    			String spacing = gridSpacing.get(ap);
	    			Integer metNo = Integer.valueOf(ap.getFunction().getLevel() - 1);
					String[] gridParts = spacing.split(",");
					for(int i=0; i<gridParts.length; i++)
					{
						String part = gridParts[i].trim();
						if (part.length() == 0) continue;
						int trackColor = SeaOfGatesTrack.getSpecificMaskNumber(part);
						if (!Character.isDigit(part.charAt(part.length()-1)))
							part = part.substring(0, part.length()-1);
						double val = TextUtils.atof(part);

		    			// get the track geometry data
		    			Map<Double,Map<Integer,MutableInteger>> metData = cellData.get(metNo);
		    			if (metData == null)
		    				cellData.put(metNo, metData = new HashMap<Double,Map<Integer,MutableInteger>>());

		    			// get the geometry data for this coordinate
		    			Double coordVal = new Double(val);
		    			Map<Integer,MutableInteger> coordData = metData.get(coordVal);
		    			if (coordData == null)
		    				metData.put(coordVal, coordData = new HashMap<Integer,MutableInteger>());

		    			// get the usage for this mask at this coordinate
		    			int [] found = new int[10];
		    			int totalFound = 0;
		    			for(Integer mi : coordData.keySet())
		    			{
		    				MutableInteger count = coordData.get(mi);
		    				found[mi.intValue()] += count.intValue();
		    				totalFound += count.intValue();
		    			}
		    			if (totalFound > 0 && trackColor > 0 && found[trackColor] == 0)
		    			{
		    				String coord = (horEven == (ap.getFunction().getLevel() % 2 == 0)) ? "Y" : "X";
		    				String msg = "Coordinate " + coord + "=" + TextUtils.formatDistance(val) + " has geometry on";
		    				boolean foundMore = false;
		    				for(int j=1; j<found.length; j++)
		    				{
		    					if (j == trackColor) continue;
		    					if (found[j] != 0) { foundMore = true;  msg +=  " Metal " + (metNo+1) + (char)('A' + j - 1); }
		    				}
		    				if (foundMore)
		    				{
			    				for(String trackLine : trackLines.keySet())
			    				{
			    					List<SeaOfGatesTrack> sogtList = trackLines.get(trackLine);
			    					boolean foundLine = false;
			    					for(SeaOfGatesTrack sogt : sogtList)
			    					{
			    						if (DBMath.areEquals(sogt.getCoordinate(), val) && sogt.getMaskNum() == trackColor)
			    						{
			    							foundLine = true;
			    							break;
			    						}
			    					}
			    					if (foundLine)
			    					{
			    						List<String> msgs = messages.get(trackLine);
			    						if (msgs == null) messages.put(trackLine, msgs = new ArrayList<String>());
			    						msgs.add(msg);
			    						break;
			    					}
			    				}
		    				}
		    			}
					}
	    		}
				if (messages != null)
				{
					for(String trackLine : messages.keySet())
					{
						System.out.println("WARNING: Line '" + trackLine + "' may be wrong because:");
						List<String> msgs = messages.get(trackLine);
						for(String msg : msgs)
							System.out.println("        " + msg);
					}
				}
	    	} catch (IOException e)
	    	{
	    		System.out.println("Error reading " + fileName);
	    		return;
	    	}
	    }

		private void gatherArea(Cell cell, FixpTransform transToTop, Map<Integer,Map<Double,Map<Integer,MutableInteger>>> cellData,
			Map<Integer,MutableInteger> horCount, Map<Integer,MutableInteger> verCount)
		{
			// first add primitive nodes and arcs
			for(Iterator<NodeInst> it = cell.getNodes(); it.hasNext(); )
			{
				NodeInst ni = it.next();
				if (ni.isCellInstance())
				{
					FixpTransform transBack = ni.transformOut(transToTop);
					gatherArea((Cell)ni.getProto(), transBack, cellData, horCount, verCount);
					continue;
				}
				PrimitiveNode pNp = (PrimitiveNode)ni.getProto();
				if (pNp.getFunction() == PrimitiveNode.Function.PIN) continue;
				FixpTransform nodeTrans = ni.rotateOut(transToTop);
				Technology tech = pNp.getTechnology();
				Poly[] nodeInstPolyList = tech.getShapeOfNode(ni, true, false, null);
				for (int i = 0; i < nodeInstPolyList.length; i++)
				{
					PolyBase poly = nodeInstPolyList[i];
					gatherLayer(poly, nodeTrans, cellData, horCount, verCount);
				}
			}
			for(Iterator<ArcInst> it = cell.getArcs(); it.hasNext(); )
			{
				ArcInst ai = it.next();
				if (ai.getProto() == Generic.tech().unrouted_arc) continue;
				Technology tech = ai.getProto().getTechnology();
				PolyBase[] polys = tech.getShapeOfArc(ai);
				for (int i = 0; i < polys.length; i++)
				{
					PolyBase poly = polys[i];
					gatherLayer(poly, transToTop, cellData, horCount, verCount);
				}
			}
		}

		private void gatherLayer(PolyBase poly, FixpTransform trans, Map<Integer,Map<Double,Map<Integer,MutableInteger>>> cellData,
			Map<Integer,MutableInteger> horCount, Map<Integer,MutableInteger> verCount)
		{
			Layer layer = poly.getLayer();
			Layer.Function fun = layer.getFunction();
			if (!fun.isMetal()) return;
			if (poly.getStyle() != Poly.Type.FILLED) return;
			poly.transform(trans);
			Rectangle2D bounds = poly.getBox();
			if (bounds == null) return;

			Integer metNo = Integer.valueOf(fun.getLevel() - 1);
			Map<Double,Map<Integer,MutableInteger>> metData = cellData.get(metNo);
			if (metData == null)
				cellData.put(metNo, metData = new HashMap<Double,Map<Integer,MutableInteger>>());

			// get the geometry data for this coordinate
			Double coordVal = new Double((horEven == ((metNo+1)%2==0))? bounds.getCenterY() : bounds.getCenterX());
			Map<Integer,MutableInteger> coordData = metData.get(coordVal);
			if (coordData == null)
				metData.put(coordVal, coordData = new HashMap<Integer,MutableInteger>());

			Integer maskLayer = Integer.valueOf(layer.getFunction().getMaskColor());
			MutableInteger count = coordData.get(maskLayer);
			if (count == null)
				coordData.put(maskLayer, count = new MutableInteger(0));
			count.increment();
		}

	    private List<ArcProto> getArcProtoList(String rootName, int lineNumber, Technology tech, String[] extra)
	    {
			if (!rootName.startsWith("M"))
			{
				System.out.println("ERROR: Unrecognized layer name on line " + lineNumber + ": " + rootName);
				return null;
			}
			int metNum = TextUtils.atoi(rootName.substring(1));
			if (metNum <= 0 || metNum > tech.getNumArcs())
			{
				System.out.println("ERROR: Unrecognized metal number on line " + lineNumber + ": " + rootName);
				return null;
			}

			List<ArcProto> foundList = new ArrayList<ArcProto>();
			int maskNum = SeaOfGatesTrack.getSpecificMaskNumber(rootName);
			if (maskNum > 0)
			{
				// mask number specified as a letter ('a', 'b', etc.)
				for(Iterator<ArcProto> it = tech.getArcs(); it.hasNext(); )
				{
					ArcProto ap = it.next();
					if (ap.getFunction().getLevel() != metNum) continue;
					if (ap.getMaskLayer() != 0 && ap.getMaskLayer() != maskNum) continue;
					foundList.add(ap);
				}
			} else
			{
				// no mask number specified, find all layers on the given metal
				for (String ex : extra)
				{
					String name = rootName + ex;
					ArcProto ap = tech.findArcProto(name); // @NOTE: check if no ignoring case could be a problem
					if (ap != null && ap.getFunction().getLevel() == metNum)
						foundList.add(ap);
				}
			}
			if (foundList.isEmpty())
			{
				System.out.println("ERROR: Unrecognized metal layer on line " + lineNumber + ": " + rootName);
				return null;
			}
			return foundList;
	    }

		private ArcProto parseArcName(String name)
		{
			int colonPos = name.indexOf(':');
			if (colonPos < 0) return null;
			String techName = name.substring(0, colonPos);
			String layerName = name.substring(colonPos+1);
			Technology tech = Technology.findTechnology(techName);
			ArcProto ap = tech.findArcProto(layerName);
			return ap;
		}

		/**
		 * Function to determine the direction of the primary spacing value
		 * @param ap ArcProto representing the metal arc
		 * @return 1 if axis is Y or 0 in case of X
		 */
		private int getPrimaryDirection(ArcProto ap)
		{
			// Assuming that metal arcs only have 1 layer
			// Horizontal -> layer override is along Y axis
			boolean isHorizontalMetal = (ap.getLayer(0).getFunction().getLevel()%2 == 0) == getHorizontalEvenMetals();
			return (isHorizontalMetal) ? 1 : 0;
		}

		/**
		 * Method to save changes to this SeaOfGatesCellParameters object.
		 * @param ep the EditingPreferences needed for database changes.
		 */
		public void saveParameters(EditingPreferences ep)
		{
			List<String> strings = new ArrayList<String>();

			// header
			strings.add("; Parameters for Cell " + cell.describe(false));

			// steiner trees
			if (steinerDone) strings.add("SteinerTreesDone");

			// contacts on avoided layers
			if (!canPlaceContactDownToAvoidedLayer) strings.add("PreventContactDownToAvoidedLayer");
			if (!canPlaceContactUpToAvoidedLayer) strings.add("PreventContactUpToAvoidedLayer");

			// contacts
			if (!canRotateContacts) strings.add("NoContactRotation");
			if (ignorePrimitives != null && ignorePrimitives.length() > 0) strings.add("IgnoreContacts " + ignorePrimitives);
			if (acceptOnly1XPrimitives != null && acceptOnly1XPrimitives.length() > 0) strings.add("Accept1XContacts " + acceptOnly1XPrimitives);
			if (acceptOnly2XPrimitives != null && acceptOnly2XPrimitives.length() > 0) strings.add("Accept2XContacts " + acceptOnly2XPrimitives);

			// miscellaneous
			if (!favorHorVer) strings.add("IgnoreHorVer");
			if (forceHorVer) strings.add("ForceHorVer");
			if (horEven) strings.add("HorizontalEven");
			if (routingBoundsLayerName != null) strings.add("RoutingBoundsLayerName " + routingBoundsLayerName);

			// ArcProto information
			for(ArcProto ap : gridSpacing.keySet())
			{
				String grid = gridSpacing.get(ap);
				strings.add("ArcGrid " + ap.getTechnology().getTechName() + ":" + ap.getName() + " " + grid);
			}
			for(ArcProto ap : preventedArcs)
			{
				strings.add("ArcAvoid " + ap.getTechnology().getTechName() + ":" + ap.getName());
			}
			for(ArcProto ap : favoredArcs)
			{
				strings.add("ArcFavor " + ap.getTechnology().getTechName() + ":" + ap.getName());
			}
			for(ArcProto ap : forceGridArcs)
			{
				strings.add("ArcGridForce " + ap.getTechnology().getTechName() + ":" + ap.getName());
			}
			for(ArcProto ap : taperOnlyArcs)
			{
				strings.add("ArcTaperOnly " + ap.getTechnology().getTechName() + ":" + ap.getName());
			}
			for(ArcProto ap : overrides.keySet())
			{
				SeaOfGatesArcProperties sogap = overrides.get(ap);
				if (sogap.getWidthOverride() != null)
					strings.add("ArcWidthOverride " + ap.getTechnology().getTechName() + ":" + ap.getName() +
						" " +sogap.getWidthOverride().doubleValue());
				if (sogap.getSpacingOverride(0) != null)
					strings.add("ArcSpacingOverrideX " + ap.getTechnology().getTechName() + ":" + ap.getName() +
						" " +sogap.getSpacingOverride(0).doubleValue());
				if (sogap.getSpacingOverride(1) != null)
					strings.add("ArcSpacingOverrideY " + ap.getTechnology().getTechName() + ":" + ap.getName() +
						" " +sogap.getSpacingOverride(1).doubleValue());
				if (sogap.getWidthOf2X() != null)
					strings.add("WidthOf2X " + ap.getTechnology().getTechName() + ":" + ap.getName() +
						" " +sogap.getWidthOf2X().doubleValue());
				if (sogap.getTaperTo1X() != null)
					strings.add("TaperTo1X " + ap.getTechnology().getTechName() + ":" + ap.getName() +
						" " +sogap.getTaperTo1X().doubleValue());
			}
			for(ArcProto ap : removeLayers.keySet())
			{
				String removeLayer = removeLayers.get(ap);
				if (removeLayer != null)
					strings.add("RemoveLayer " + ap.getTechnology().getTechName() + ":" + ap.getName() + " " + removeLayer);
			}
			for(SeaOfGatesExtraBlockage sogeb : extraBlockages)
			{
				strings.add("Blockage " + sogeb.met.getTechnology().getTechName() + ":" + sogeb.met.getName() + " " +
					sogeb.x1 + " " + sogeb.y1 + " " + sogeb.x2 + " " + sogeb.y2);
			}

			// network information
			for(String netName : netsToRoute)
			{
				String line = "Network " + netName;
				List<ArcProto> arcs = netsAndArcsToRoute.get(netName);
				if (arcs != null)
				{
					for(ArcProto ap : arcs)
						line += " " + ap.getTechnology().getTechName() + ":" + ap.getName();
				}
				strings.add(line);
			}
			for(String netName : netAndArcOverrides.keySet())
			{
				String line = "NetworkOverride " + netName;
				Map<ArcProto,SeaOfGatesArcProperties> arcs = netAndArcOverrides.get(netName);
				if (arcs != null)
				{
					for(ArcProto ap : arcs.keySet())
					{
						SeaOfGatesArcProperties overrides = arcs.get(ap);
						if (overrides != null)
						{
							line += " " + ap.getTechnology().getTechName() + ":" + ap.getName();
							if (overrides.getWidthOverride() != null) line += " W=" + overrides.getWidthOverride().doubleValue();
							int axis = getPrimaryDirection(ap);
							if (overrides.getSpacingOverride(axis) != null) line += " S=" + overrides.getSpacingOverride(axis).doubleValue();
						}
					}
				}
				strings.add(line);
			}

			String[] paramArray = new String[strings.size()];
			for(int i=0; i<strings.size(); i++) paramArray[i] = strings.get(i);
			cell.newVar(ROUTING_SOG_PARAMETERS_KEY, paramArray, ep);
		}

		/**
		 * Method to return the cell that this object describes.
		 * @return the cell that this object describes.
		 */
		public Cell getCell() { return cell; }

		/**
		 * Method to set whether routing should recompute Steiner trees.
		 * Steiner trees are reorganizations of daisy-chained nets so that
		 * the hops are optimal.
		 * @param sd true if routing should recompute Steiner trees.
		 */
		public void setSteinerDone(boolean sd) { steinerDone = sd; }

		/**
		 * Method to tell whether routing should recompute Steiner trees.
		 * Steiner trees are reorganizations of daisy-chained nets so that
		 * the hops are optimal.
		 * @return true if routing should recompute Steiner trees.
		 */
		public boolean isSteinerDone() { return steinerDone; }

		/**
		 * Method to set whether routing allows contacts down to avoided layers.
		 * This happens only at the ends of routes when they are on avoided layers.
		 * The contact must not add any new geometry.
		 * @param a true if routing allows contacts down to avoided layers.
		 */
		public void setContactAllowedDownToAvoidedLayer(boolean a) { canPlaceContactDownToAvoidedLayer = a; }

		/**
		 * Method to tell whether routing allows contacts down to avoided layers.
		 * This happens only at the ends of routes when they are on avoided layers.
		 * The contact must not add any new geometry.
		 * @return true if routing allows contacts down to avoided layers.
		 */
		public boolean isContactAllowedDownToAvoidedLayer() { return canPlaceContactDownToAvoidedLayer; }

		/**
		 * Method to set whether routing allows contacts up to avoided layers.
		 * This happens only at the ends of routes when they are on avoided layers.
		 * The contact must not add any new geometry.
		 * @param a true if routing allows contacts up to avoided layers.
		 */
		public void setContactAllowedUpToAvoidedLayer(boolean a) { canPlaceContactUpToAvoidedLayer = a; }

		/**
		 * Method to tell whether routing allows contacts up to avoided layers.
		 * This happens only at the ends of routes when they are on avoided layers.
		 * The contact must not add any new geometry.
		 * @return true if routing allows contacts up to avoided layers.
		 */
		public boolean isContactAllowedUpToAvoidedLayer() { return canPlaceContactUpToAvoidedLayer; }

		/**
		 * Method to set whether routing the Cell should favor alternating horizontal/vertical metal layers.
		 * Favoring uses weights to encourage the activity, but does not force it.
		 * @param f true if routing the Cell should favor alternating horizontal/vertical metal layers
		 */
		public void setFavorHorVer(boolean f) { favorHorVer = f; }

		/**
		 * Method to tell whether routing the Cell should favor alternating horizontal/vertical metal layers.
		 * Favoring uses weights to encourage the activity, but does not force it.
		 * @return true if routing the Cell should favor alternating horizontal/vertical metal layers
		 */
		public boolean isFavorHorVer() { return favorHorVer; }

		/**
		 * Method to set whether routing the Cell should force alternating horizontal/vertical metal layers.
		 * Forcing ensures that no metal layers will be allowed to run in the wrong direction.
		 * @param f true if routing the Cell should force alternating horizontal/vertical metal layers
		 */
		public void setForceHorVer(boolean f) { forceHorVer = f; }

		/**
		 * Method to tell whether routing the Cell should force alternating horizontal/vertical metal layers.
		 * Forcing ensures that no metal layers will be allowed to run in the wrong direction.
		 * @return true if routing the Cell should force alternating horizontal/vertical metal layers
		 */
		public boolean isForceHorVer() { return forceHorVer; }

		/**
		 * Method to set which way alternating horizontal/vertical metal layers run.
		 * @param he true to make even metal layers run horizontally, odd metal layers run vertically.
		 * False means even metal layers run vertically, odd metal layers run horizontally.
		 */
		public void setHorizontalEven(boolean he) { horEven = he; }

		/**
		 * Method to tell which way alternating horizontal/vertical metal layers run.
		 * @return true to make even metal layers run horizontally, odd metal layers run vertically.
		 * False means even metal layers run vertically, odd metal layers run horizontally.
		 */
		public boolean isHorizontalEven() { return horEven; }

		/**
		 * Method to set whether contact nodes can be rotated when placing them.
		 * @param he true to allow contact nodes to be rotated when placing them.
		 * False means they can only appear in their unrotated orientation.
		 */
		public void setContactsRotate(boolean he) { canRotateContacts = he; }

		/**
		 * Method to tell whether contact nodes can be rotated when placing them.
		 * @return true to allow contact nodes to be rotated during routing.
		 * False means they can only appear in their unrotated orientation.
		 */
		public boolean isContactsRotate() { return canRotateContacts; }

		/**
		 * Method to set whether a given layer is prevented in routing the Cell.
		 * @param ap the ArcProto of the layer in question.
		 * @param prevent true to prevent the layer from being used.
		 */
		public void setPrevented(ArcProto ap, boolean prevent)
		{
			if (prevent) preventedArcs.add(ap); else
				preventedArcs.remove(ap);
		}

		/**
		 * Method to tell whether a given layer is prevented in routing the Cell.
		 * @param ap the ArcProto of the layer in question.
		 * @return true if the layer is prevented from being used.
		 */
		public boolean isPrevented(ArcProto ap) { return preventedArcs.contains(ap); }

		/**
		 * Method to set whether a given layer is favored in routing the Cell.
		 * Favored layers are weighted stronger so they are used more.
		 * @param ap the ArcProto of the layer in question.
		 * @param f true to favor the layer.
		 */
		public void setFavored(ArcProto ap, boolean f)
		{
			if (f) favoredArcs.add(ap); else
				favoredArcs.remove(ap);
		}

		/**
		 * Method to tell whether a given layer is favored in routing the Cell.
		 * Favored layers are weighted stronger so they are used more.
		 * @param ap the ArcProto of the layer in question.
		 * @return true if the layer is favored.
		 */
		public boolean isFavored(ArcProto ap) { return favoredArcs.contains(ap); }

		/**
		 * Method to set whether a given layer is available only for tapers.
		 * Tapers are ends of a route that take the width of the endpoint node.
		 * @param ap the ArcProto of the layer in question.
		 * @param f true to force the layer to run only for tapers.
		 */
		public void setTaperOnly(ArcProto ap, boolean f)
		{
			if (f) taperOnlyArcs.add(ap); else
				taperOnlyArcs.remove(ap);
		}

		/**
		 * Method to tell whether a given layer is forced to be on grid in routing the Cell.
		 * When not forced, the grid is "favored" by weighting.
		 * @param ap the ArcProto of the layer in question.
		 * @return true if the layer is forced to be on grid.
		 */
		public boolean isTaperOnly(ArcProto ap) { return taperOnlyArcs.contains(ap); }

		/**
		 * Method to set whether a given layer is available only for tapers.
		 * Tapers are ends of a route that take the width of the endpoint node.
		 * @param ap the ArcProto of the layer in question.
		 * @param f true to force the layer to run only for tapers.
		 */
		public void setGridForced(ArcProto ap, boolean f)
		{
			if (f) forceGridArcs.add(ap); else
				forceGridArcs.remove(ap);
		}

		/**
		 * Method to tell whether a given layer is forced to be on grid in routing the Cell.
		 * When not forced, the grid is "favored" by weighting.
		 * @param ap the ArcProto of the layer in question.
		 * @return true if the layer is forced to be on grid.
		 */
		public boolean isGridForced(ArcProto ap) { return forceGridArcs.contains(ap); }

		/**
		 * Method to tell whether a given layer should use a different width when routing the Cell.
		 * @param ap the ArcProto of the layer in question.
		 * @return the arc width to use (null to use the arc's default).
		 */
		public Double getDefaultWidthOverride(ArcProto ap)
		{
			SeaOfGatesArcProperties sogap = overrides.get(ap);
			if (sogap == null) return null;
			return sogap.getWidthOverride();
		}

		/**
		 * Method to set whether a given layer should use a different width when routing the Cell.
		 * @param ap the ArcProto of the layer in question.
		 * @param w the new width to use (null to use the default).
		 */
		public void setDefaultWidthOverride(ArcProto ap, Double w)
		{
			SeaOfGatesArcProperties sogap = overrides.get(ap);
			if (sogap == null) overrides.put(ap, sogap = new SeaOfGatesArcProperties());
			sogap.setWidthOverride(w);
		}

		/**
		 * Method to tell whether a given layer should use a different spacing along a given axis
		 * when routing the Cell. 0 for X, 1 for Y.
		 * @param ap the ArcProto of the layer in question.
		 * @return the arc spacing to use (null to use the arc's default).
		 */
		public Double getDefaultSpacingOverride(ArcProto ap, int axis)
		{
			SeaOfGatesArcProperties sogap = overrides.get(ap);
			if (sogap == null) return null;
			return sogap.getSpacingOverride(axis);
		}

		/**
		 * Method to set whether a given layer should use a different spacing along a given axis
		 * when routing the Cell. 0 for X, 1 for Y.
		 * @param ap the ArcProto of the layer in question.
		 * @param s the new spacing to use (null to use the default).
		 */
		public void setDefaultSpacingOverride(ArcProto ap, Double s, int axis)
		{
			SeaOfGatesArcProperties sogap = overrides.get(ap);
			if (sogap == null) overrides.put(ap, sogap = new SeaOfGatesArcProperties());
			sogap.setSpacingOverride(s, axis);
		}

		/**
		 * Method to tell the threshold for 2X arcs when routing the Cell.
		 * Arcs this amount or wider will be treated specially (2X arcs).
		 * @param ap the ArcProto in question.
		 * @return the arc width to use (null if no special 2X treatment applies to this arc).
		 */
		public Double get2XWidth(ArcProto ap)
		{
			SeaOfGatesArcProperties sogap = overrides.get(ap);
			if (sogap == null) return null;
			return sogap.getWidthOf2X();
		}

		/**
		 * Method to set the threshold for 2X arcs when routing the Cell.
		 * Arcs this amount or wider will be treated specially (2X arcs).
		 * @param ap the ArcProto in question.
		 * @param w the arc width to use (null if no special 2X treatment applies to this arc).
		 */
		public void set2XWidth(ArcProto ap, Double w)
		{
			SeaOfGatesArcProperties sogap = overrides.get(ap);
			if (sogap == null) overrides.put(ap, sogap = new SeaOfGatesArcProperties());
			sogap.setWidthOf2X(w);
		}

		/**
		 * Method to tell the maximum taper length for a given layer.
		 * Tapers are the starting and ending segments of a route.
		 * When a taper length is given, the width of those segments is set to the size of the connecting node.
		 * @param ap the ArcProto in question.
		 * @return the maximum taper length for the layer (null if no tapers are done on this layer).
		 */
		public Double getTaperLength(ArcProto ap)
		{
			SeaOfGatesArcProperties sogap = overrides.get(ap);
			if (sogap == null) return null;
			return sogap.getTaperTo1X();
		}

		/**
		 * Method to set the maximum taper length for a given layer.
		 * Tapers are the starting and ending segments of a route.
		 * When a taper length is given, the width of those segments is set to the size of the connecting node.
		 * @param ap the ArcProto in question.
		 * @param t the maximum taper length for the layer (null if no tapers are done on this layer).
		 */
		public void setTaperLength(ArcProto ap, Double t)
		{
			SeaOfGatesArcProperties sogap = overrides.get(ap);
			if (sogap == null) overrides.put(ap, sogap = new SeaOfGatesArcProperties());
			sogap.setTaperTo1X(t);
		}

		/**
		 * Method to return a Set of network names to be routed in this cell.
		 * @return a List of network names (null to route everything).
		 */
		public List<String> getNetsToRoute()
		{
			return netsToRoute;
		}

		/**
		 * Method to add a network name to the list of nets to route.
		 * @param netName the network name to add to the list of nets to route.
		 */
		public void addNetToRoute(String netName)
		{
			List<String> names = new ArrayList<String>();

			int index = netName.indexOf(":"); // checking if name contains bus info
			if (index != -1)
			{
				String[] values = netName.split("\\["); // get root name and bus
				assert (values.length == 2);
				String root = values[0]; // net root name
				values = values[1].replace("]", "").split(":"); // get start/end indices
				assert (values.length == 2);
				int start = Integer.valueOf(values[0]), end = Integer.valueOf(values[1]);
				if (start > end)
				{
					// swap number
					int tmp = start; start = end; end = tmp;
				}
				for (int i = start; i <= end; i++)
				{
					names.add(root+"["+i+"]");
				}
			}
			else
				names.add(netName);
			for (String name: names)
			{
				List<ArcProto> arcs = netsAndArcsToRoute.get(name);
				if (arcs == null) netsAndArcsToRoute.put(name, arcs = new ArrayList<ArcProto>());
				netsToRoute.add(name);
			}
		}

		/**
		 * Method to remove a network name from the list of nets to route.
		 * @param netName the network name to remove from the list of nets to route.
		 */
		public void removeNetToRoute(String netName)
		{
			List<ArcProto> arcs = netsAndArcsToRoute.get(netName);
			if (arcs != null) netsAndArcsToRoute.remove(netName);
			netsToRoute.remove(netName);
		}

		/**
		 * Method to return a List of Arcs that can be used on a network name in this cell.
		 * @param net the name of the network.
		 * @return a List of ArcProtos to be used (null to use the default set).
		 */
		public List<ArcProto> getArcsOnNet(String net)
		{
			List<ArcProto> arcsOnNet = netsAndArcsToRoute.get(net);
			if (arcsOnNet == null) return null;
			return arcsOnNet;
		}

		/**
		 * Method to add a new arc to a net being routed.
		 * Adding an arc to a net forces all arcs not added to be excluded from the route.
		 * @param net the network name.
		 * @param ap the ArcProto that can be used to route the named net.
		 */
		public void addArcToNet(String net, ArcProto ap)
		{
			List<ArcProto> arcsOnNet = netsAndArcsToRoute.get(net);
			if (arcsOnNet == null) netsAndArcsToRoute.put(net, arcsOnNet = new ArrayList<ArcProto>());
			arcsOnNet.add(ap);
		}

		/**
		 * Method to remove an arc from a net being routed.
		 * If all arcs are removed from the net, the default set is used.
		 * @param net the network name.
		 * @param ap the ArcProto that can no longer be used to route the named net.
		 */
		public void removeArcFromNet(String net, ArcProto ap)
		{
			List<ArcProto> arcsOnNet = netsAndArcsToRoute.get(net);
			if (arcsOnNet == null) return;
			arcsOnNet.remove(ap);
		}

		/**
		 * Method to return an object with width and spacing overrides for a given arc on a given network in this cell.
		 * @param net the name of the network.
		 * @param ap the ArcProto being routed on the network.
		 * @return a SeaOfGatesArcProperties object with width and spacing overrides.
		 * A null return, or an object with nulls in the width or spacing, indicates that default values should be used.
		 */
		public SeaOfGatesArcProperties getOverridesForArcsOnNet(String net, ArcProto ap)
		{
			Map<ArcProto,SeaOfGatesArcProperties> arcsOnNet = netAndArcOverrides.get(net);
			if (arcsOnNet == null) return null;
			SeaOfGatesArcProperties sogap = arcsOnNet.get(ap);
			return sogap;
		}

		/**
		 * Method to set the width for a given arc on a given network.
		 * @param net the name of the network.
		 * @param ap the ArcProto being routed on the network.
		 * @param width the arc width to use (null to remove the override).
		 */
		public void setWidthOverrideForArcOnNet(String net, ArcProto ap, Double width)
		{
			Map<ArcProto,SeaOfGatesArcProperties> arcsOnNet = netAndArcOverrides.get(net);
			if (arcsOnNet == null) netAndArcOverrides.put(net, arcsOnNet = new HashMap<ArcProto,SeaOfGatesArcProperties>());
			SeaOfGatesArcProperties sogap = arcsOnNet.get(ap);
			if (sogap == null) arcsOnNet.put(ap, sogap = new SeaOfGatesArcProperties());
			sogap.setWidthOverride(width);
		}

		/**
		 * Method to set the spacing in the primary direction for a given arc on a given network.
		 * @param net the name of the network.
		 * @param ap the ArcProto being routed on the network.
		 * @param spacing the arc spacing to use (null to remove the override).
		 */
		public void setSpacingOverrideForArcOnNet(String net, ArcProto ap, Double spacing)
		{
			Map<ArcProto,SeaOfGatesArcProperties> arcsOnNet = netAndArcOverrides.get(net);
			if (arcsOnNet == null) netAndArcOverrides.put(net, arcsOnNet = new HashMap<ArcProto,SeaOfGatesArcProperties>());
			SeaOfGatesArcProperties sogap = arcsOnNet.get(ap);
			if (sogap == null) arcsOnNet.put(ap, sogap = new SeaOfGatesArcProperties());
			sogap.setSpacingOverride(spacing, getPrimaryDirection(ap));
		}

		/**
		 * Method to get the grid information for a given layer in the Cell.
		 * @return a String with the grid information.
		 */
		public String getGrid(ArcProto ap)
		{
			String v = gridSpacing.get(ap);
			return v;
		}

		/**
		 * Method to set the grid information for a given layer in the Cell.
		 * @param grid a String with the grid information (null to remove).
		 */
		public void setGrid(ArcProto ap, String grid)
		{
			if (grid == null) gridSpacing.remove(ap); else
				gridSpacing.put(ap, grid);
		}

		/**
		 * Method to get the name of the layer which removes a given layer in the Cell.
		 * @return a String with the name of the layer which removes geometry on the given layer.
		 */
		public String getRemoveLayer(ArcProto ap)
		{
			String v = removeLayers.get(ap);
			return v;
		}

		/**
		 * Method to set the layer name which removes a given layer in the Cell.
		 * @param ap the layer which is being removed.
		 * @param layerName a String with the name of the layer which removes geometry from the given layer
		 * (null if no layer removes geometry).
		 */
		public void setRemoveLayer(ArcProto ap, String layerName)
		{
			if (layerName == null) removeLayers.remove(ap); else
				removeLayers.put(ap, layerName);
		}

		/**
		 * Method to return possible regular expression to discard some 1X primitives from routing.
		 * @return String with the regular expression
		 */
		public String getAcceptOnly1XPrimitives() { return acceptOnly1XPrimitives; }

		/**
		 * Method to set the regular expression to accept only certain 1X primitives from routing.
		 * @param s String representing the regular expression
		 */
		public void setAcceptOnly1XPrimitives(String s) {acceptOnly1XPrimitives = s; }

		/**
		 * Method to return possible regular expression to discard some 2X primitives from routing.
		 * @return String with the regular expression
		 */
		public String getAcceptOnly2XPrimitives() { return acceptOnly2XPrimitives; }

		/**
		 * Method to set the regular expression to accept only certain 2X primitives from routing.
		 * @param s String representing the regular expression
		 */
		public void setAcceptOnly2XPrimitives(String s) {acceptOnly2XPrimitives = s; }

		/**
		 * Method to return the name of the layer to be used to limit routing bounds.
		 * @return the name of the layer to be used to limit routing bounds (null if no limiting being done).
		 */
		public String getRoutingBoundsLayerName() { return routingBoundsLayerName; }

		/**
		 * Method to set the name of the layer to be used to limit routing bounds.
		 * @param n the name of the layer to be used to limit routing bounds (null if no limiting being done).
		 */
		public void setRoutingBoundsLayerName(String n) { routingBoundsLayerName = n; }

		/**
		 * Method to return possible regular expression to accept only certain primitives from routing.
		 * @return String with the regular expression
		 */
		public String getIgnorePrimitives() { return ignorePrimitives; }

		/**
		 * Method to set the regular expression to discard some primitives from routing.
		 * @param s String representing the regular expression
		 */
		public void setIgnorePrimitive(String s) {ignorePrimitives = s; }

		/**
		 * Method to return if even metals are horizontal
		 * @return true if even metals are horizontal
		 */
		public boolean getHorizontalEvenMetals() {return horEven; }

		/**
		 * Method to return the list of extra blockages in this cell.
		 * @return the list of extra blockages in this cell.
		 */
		public List<SeaOfGatesExtraBlockage> getBlockages() { return extraBlockages; }

		/**
		 * Method to set the list of extra blockages in this cell.
		 * @param e the list of extra blockages in this cell.
		 */
		public void setBlockages(List<SeaOfGatesExtraBlockage> e) { extraBlockages = e; }
	}
}
