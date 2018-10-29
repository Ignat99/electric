/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: FastHenry.java
 * Input/output tool: FashHenry Netlist output
 * Written by Steven M. Rubin.
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
package com.sun.electric.tool.io.output;

import com.sun.electric.database.geometry.Poly;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.Export;
import com.sun.electric.database.prototype.PortProto;
import com.sun.electric.database.text.TextUtils;
import com.sun.electric.database.text.Version;
import com.sun.electric.database.topology.ArcInst;
import com.sun.electric.database.topology.Connection;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.variable.VarContext;
import com.sun.electric.database.variable.Variable;
import com.sun.electric.technology.Layer;
import com.sun.electric.technology.Technology;
import com.sun.electric.tool.simulation.SimulationTool;

import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * This is the netlister for FastHenry.
 */
public class FastHenry extends Output
{
	/** key of Variable holding group name. */			public static final Variable.Key GROUP_NAME_KEY = Variable.newKey("SIM_fasthenry_group_name");
	/** key of Variable holding thickness. */			public static final Variable.Key THICKNESS_KEY = Variable.newKey("SIM_fasthenry_thickness");
	/** key of Variable holding width subdivisions. */	public static final Variable.Key WIDTH_SUBDIVS_KEY = Variable.newKey("SIM_fasthenry_width_subdivs");
	/** key of Variable holding height subdivisions. */	public static final Variable.Key HEIGHT_SUBDIVS_KEY = Variable.newKey("SIM_fasthenry_height_subdivs");
	/** key of Variable holding the head Z value. */	public static final Variable.Key ZHEAD_KEY = Variable.newKey("SIM_fasthenry_z_head");
	/** key of Variable holding the tail Z value. */	public static final Variable.Key ZTAIL_KEY = Variable.newKey("SIM_fasthenry_z_tail");

	private FastHenryPreferences localPrefs;

	/**
	 * Class for managing FastHenry information on arcs.
	 */
	public static class FastHenryArcInfo
	{
		private String groupName;
		private double thickness;
		private int widthSubdivisions, heightSubdivisions;
		private double zHead, zTail;
		private double zDefault;

		public String getGroupName() { return groupName; }
		public double getThickness() { return thickness; }
		public int getWidthSubdivisions() { return widthSubdivisions; }
		public int getHeightSubdivisions() { return heightSubdivisions; }
		public double getZHead() { return zHead; }
		public double getZTail() { return zTail; }
		public double getZDefault() { return zDefault; }

		public FastHenryArcInfo(ArcInst ai, FastHenryPreferences prefs)
		{
			Technology tech = ai.getProto().getTechnology();

			// get the group membership
			groupName = null;
			Variable var = ai.getVar(GROUP_NAME_KEY);
			if (var != null) groupName = var.getPureValue(-1);

			// get the arc thickness
			thickness = -1;
			var = ai.getVar(THICKNESS_KEY);
			if (var != null)
			{
				if (var.getObject() instanceof Integer) thickness = ((Integer)var.getObject()).intValue() / tech.getScale(); else
					thickness = TextUtils.atof(var.getPureValue(-1));
			}

			// get the width subdivisions
			widthSubdivisions = -1;
			var = ai.getVar(WIDTH_SUBDIVS_KEY);
			if (var != null) widthSubdivisions = TextUtils.atoi(var.getPureValue(-1));

			// get the height subdivisions
			heightSubdivisions = -1;
			var = ai.getVar(HEIGHT_SUBDIVS_KEY);
			if (var != null) heightSubdivisions = TextUtils.atoi(var.getPureValue(-1));

			// get the Z height at the head of the arc
			zHead = -1;
			var = ai.getVar(ZHEAD_KEY);
			if (var != null)
			{
				if (var.getObject() instanceof Integer) zHead = ((Integer)var.getObject()).intValue() / tech.getScale(); else
					zHead = TextUtils.atof(var.getPureValue(-1));
			}

			// get the Z height at the tail of the arc
			zTail = -1;
			var = ai.getVar(ZTAIL_KEY);
			if (var != null)
			{
				if (var.getObject() instanceof Integer) zTail = ((Integer)var.getObject()).intValue() / tech.getScale(); else
					zTail = TextUtils.atof(var.getPureValue(-1));
			}

			// get the default Z height
			zDefault = -1;
			Poly [] polys = tech.getShapeOfArc(ai);
			for(int i=0; i<polys.length; i++)
			{
				Poly poly = polys[i];
				Layer layer = poly.getLayer();
				if (layer == null) continue;
				zDefault = layer.getDepth();
				break;
			}
		}
	}

	public static class FastHenryPreferences extends OutputPreferences
    {
		int widthSubdivisions = SimulationTool.getFactoryFastHenryWidthSubdivisions();
		int heightSubdivisions = SimulationTool.getFactoryFastHenryHeightSubdivisions();
		double defThickness = SimulationTool.getFactoryFastHenryDefThickness();
		boolean useSingleFrequency = SimulationTool.isFactoryFastHenryUseSingleFrequency();
		double startFrequency = SimulationTool.getFactoryFastHenryStartFrequency();
		double endFrequency = SimulationTool.getFactoryFastHenryEndFrequency();
		int runsPerDecade = SimulationTool.getFactoryFastHenryRunsPerDecade();

        public FastHenryPreferences(boolean factory) {
            super(factory);
            if (!factory)
                fillPrefs();
        }

		private void fillPrefs()
        {
			widthSubdivisions = SimulationTool.getFastHenryWidthSubdivisions();
			heightSubdivisions = SimulationTool.getFastHenryHeightSubdivisions();
			defThickness = SimulationTool.getFastHenryDefThickness();
			useSingleFrequency = SimulationTool.isFastHenryUseSingleFrequency();
			startFrequency = SimulationTool.getFastHenryStartFrequency();
			endFrequency = SimulationTool.getFastHenryEndFrequency();
			runsPerDecade = SimulationTool.getFastHenryRunsPerDecade();
		}

        public Output doOutput(Cell cell, VarContext context, String filePath)
        {
    		FastHenry out = new FastHenry(this);
    		if (out.openTextOutputStream(filePath)) return out.finishWrite();
    		out.writeFH(cell, context);
    		if (out.closeTextOutputStream()) return out.finishWrite();
    		System.out.println(filePath + " written");
            return out.finishWrite();
        }
    }

	/**
	 * Creates a new instance of the FastHenry netlister.
	 */
	FastHenry(FastHenryPreferences fp) { localPrefs = fp; }

	protected void writeFH(Cell cell, VarContext context)
	{
		printWriter.println("* FastHenry for " + cell);
		emitCopyright("* ", "");
		if (localPrefs.includeDateAndVersionInOutput)
		{
			printWriter.println("* Cell created on " + TextUtils.formatDate(cell.getCreationDate()));
			printWriter.println("* Cell last modified on " + TextUtils.formatDate(cell.getRevisionDate()));
			printWriter.println("* Netlist written on " + TextUtils.formatDate(new Date()));
			printWriter.println("* Written by Electric VLSI Design System, version " + Version.getVersion());
		} else
		{
			printWriter.println("* Written by Electric VLSI Design System");
		}

		printWriter.println("\n* Units are microns");
		printWriter.println(".units um");

		// write default width and height subdivisions
		printWriter.println("");
		printWriter.println("* Default number of subdivisions");
		printWriter.println(".Default nwinc=" + localPrefs.widthSubdivisions +
			" nhinc=" + localPrefs.heightSubdivisions +
			" h=" + TextUtils.formatDouble(localPrefs.defThickness));

		// reset flags for cells that have been written
		sim_writefhcell(cell);

		// write frequency range
		printWriter.println("");
		if (!localPrefs.useSingleFrequency)
		{
			printWriter.println(".freq fmin=" + TextUtils.formatDouble(localPrefs.startFrequency) +
				" fmax=" + TextUtils.formatDouble(localPrefs.endFrequency) +
				" ndec=" + Integer.toString(localPrefs.runsPerDecade));
		} else
		{
			printWriter.println(".freq fmin=" + TextUtils.formatDouble(localPrefs.startFrequency) +
				" fmax=" + TextUtils.formatDouble(localPrefs.endFrequency) + " ndec=1");
		}

		// clean up
		printWriter.println("");
		printWriter.println(".end");
	}

	/**
	 * Method to print the FastHenry description of cell "cell".
	 */
	private void sim_writefhcell(Cell cell)
	{
		// look at every node in the cell
		printWriter.println("");
		printWriter.println("* Traces");
		for(Iterator<NodeInst> it = cell.getNodes(); it.hasNext(); )
		{
			NodeInst ni = it.next();
			// see if this node has a FastHenry arc on it
			boolean found = false;
			double nodeZVal = 0;
			for(Iterator<Connection> cIt = ni.getConnections(); cIt.hasNext(); )
			{
				Connection con = cIt.next();
				ArcInst ai = con.getArc();
				FastHenryArcInfo fhai = new FastHenryArcInfo(ai, localPrefs);
				if (fhai.getGroupName() == null) continue;
				double zVal = fhai.getZDefault();
				if (con.getEndIndex() == ArcInst.HEADEND && fhai.getZHead() >= 0) zVal = fhai.getZHead();
				if (con.getEndIndex() == ArcInst.TAILEND && fhai.getZTail() >= 0) zVal = fhai.getZTail();
				if (found)
				{
					// "nodeZVal" used in proper order
					if (zVal != nodeZVal)
						reportWarning("Warning: inconsistent z value at " + ni);
				}
				nodeZVal = zVal;
				found = true;
			}
			if (!found) continue;

			// node is an end point: get its name
			String nname = ni.getName();
			if (ni.hasExports())
			{
				Export e = ni.getExports().next();
				nname = e.getName();
			}

			// write the "N" line
			double x = TextUtils.convertDistance(ni.getTrueCenterX(), cell.getTechnology(), TextUtils.UnitScale.MICRO);
			double y = TextUtils.convertDistance(ni.getTrueCenterY(), cell.getTechnology(), TextUtils.UnitScale.MICRO);
			double z = TextUtils.convertDistance(nodeZVal, cell.getTechnology(), TextUtils.UnitScale.MICRO);
			printWriter.println("N_" + nname + " x=" + TextUtils.formatDouble(x) +
				" y=" + TextUtils.formatDouble(y) + " z=" + TextUtils.formatDouble(z));
		}

		// look at every arc in the cell
		for(Iterator<ArcInst> it = cell.getArcs(); it.hasNext(); )
		{
			ArcInst ai = it.next();
			// get info about this arc, stop if not part of the FastHenry output
			FastHenryArcInfo fhai = new FastHenryArcInfo(ai, localPrefs);
			if (fhai.getGroupName() == null) continue;

			// get size
			double wid = ai.getLambdaBaseWidth();

			// get the name of the nodes on each end
			NodeInst n1 = ai.getHeadPortInst().getNodeInst();
			String n1Name = n1.getName();
			if (n1.hasExports())
				n1Name = n1.getExports().next().getName();
			NodeInst n2 = ai.getTailPortInst().getNodeInst();
			String n2Name = n2.getName();
			if (n2.hasExports())
				n2Name = n2.getExports().next().getName();

			// write the "E" line
			double w = TextUtils.convertDistance(wid, cell.getTechnology(), TextUtils.UnitScale.MICRO);
			StringBuffer sb = new StringBuffer();
			sb.append("E_" + n1Name + "_" + n2Name + " N_" + n1Name + " N_" + n2Name + " w=" + TextUtils.formatDouble(w));
			if (fhai.getThickness() > 0)
			{
				double h = TextUtils.convertDistance(fhai.getThickness(), cell.getTechnology(), TextUtils.UnitScale.MICRO);
				sb.append(" h=" + TextUtils.formatDouble(h));
			}
			if (fhai.getWidthSubdivisions() > 0) sb.append(" nwinc=" + Integer.toString(fhai.getWidthSubdivisions()));
			if (fhai.getHeightSubdivisions() > 0) sb.append(" nhinc=" + Integer.toString(fhai.getHeightSubdivisions()));
			printWriter.println(sb.toString());
		}

		// find external connections
		printWriter.println("");
		printWriter.println("* External connections");

		// look at every export in the cell
		Set<ArcInst> arcsSeen = new HashSet<ArcInst>();
		Set<Export> portsSeen = new HashSet<Export>();
		for(Iterator<PortProto> it = cell.getPorts(); it.hasNext(); )
		{
			Export e = (Export)it.next();
			if (portsSeen.contains(e)) continue;
			portsSeen.add(e);
			NodeInst ni = e.getOriginalPort().getNodeInst();
			Connection con = null;
			for(Iterator<Connection> cIt = ni.getConnections(); cIt.hasNext(); )
			{
				con = cIt.next();
				if (con.getArc().getVar(GROUP_NAME_KEY) != null) break;
				con = null;
			}
			if (con == null) continue;

			// port "pp" is one end, now find the other
            int thatEnd = 1 - con.getEndIndex();
			Export oE = sim_fasthenryfindotherport(con.getArc(), thatEnd, arcsSeen);
			if (oE == null)
			{
				reportWarning("Warning: trace on export " + e.getName() + " has no other end that is an export");
				continue;
			}

			// found two ports: write the ".external" line
			portsSeen.add(oE);
			printWriter.println(".external N_" + e.getName() + " N_" + oE.getName());
		}

		// warn about arcs that aren't connected to ".external" lines
		for(Iterator<ArcInst> it = cell.getArcs(); it.hasNext(); )
		{
			ArcInst ai = it.next();
			if (arcsSeen.contains(ai)) continue;
			if (ai.getVar(GROUP_NAME_KEY) == null) continue;
			reportWarning("Warning: " + ai + " is not connected to an export");
		}
	}

	private Export sim_fasthenryfindotherport(ArcInst ai, int end, Set<ArcInst> arcsSeen)
	{
		arcsSeen.add(ai);
		NodeInst ni = ai.getPortInst(end).getNodeInst();
		if (ni.hasExports()) return ni.getExports().next();

		for(Iterator<Connection> it = ni.getConnections(); it.hasNext(); )
		{
			Connection con = it.next();
			ArcInst oAi = con.getArc();
			if (oAi == ai) continue;
			Variable var = ai.getVar(GROUP_NAME_KEY);
			if (var == null) continue;
            int thatEnd = 1 - con.getEndIndex();
			Export oE = sim_fasthenryfindotherport(oAi, thatEnd, arcsSeen);
			if (oE != null) return oE;
		}
		return null;
	}

}
