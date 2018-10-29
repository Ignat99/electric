	/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: TechnologyEditTest.java
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
package com.sun.electric.tool.user.tests;

import com.sun.electric.database.EditingPreferences;
import com.sun.electric.database.geometry.EGraphics;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.EDatabase;
import com.sun.electric.database.hierarchy.Library;
import com.sun.electric.database.text.Setting;
import com.sun.electric.technology.ArcProto;
import com.sun.electric.technology.EdgeH;
import com.sun.electric.technology.EdgeV;
import com.sun.electric.technology.Layer;
import com.sun.electric.technology.PrimitiveNode;
import com.sun.electric.technology.PrimitivePort;
import com.sun.electric.technology.SizeOffset;
import com.sun.electric.technology.Technology;
import com.sun.electric.technology.Technology.NodeLayer;
import com.sun.electric.technology.technologies.Generic;
import com.sun.electric.technology.technologies.MoCMOS;
import com.sun.electric.tool.user.GraphicsPreferences;
import com.sun.electric.tool.user.MessagesStream;
import com.sun.electric.tool.user.User;
import com.sun.electric.tool.user.UserInterfaceMain;
import com.sun.electric.tool.user.tecEdit.LibToTech;
import com.sun.electric.tool.user.tecEdit.TechConversionResult;
import com.sun.electric.tool.user.tecEdit.TechToLib;
import com.sun.electric.util.math.MutableBoolean;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Class to test the Technology Edit functions.
 */
public class TechnologyEditTest extends AbstractTest
{
	EditingPreferences ep = UserInterfaceMain.getEditingPreferences();
	GraphicsPreferences gp = UserInterfaceMain.getGraphicsPreferences();

	public TechnologyEditTest(String testName)
	{
		super(testName);
	}

	public static List<AbstractTest> getTests()
	{
		List<AbstractTest> list = new ArrayList<AbstractTest>();
		list.add(new TechnologyEditTest("BICMOS"));
		list.add(new TechnologyEditTest("CMOS"));
		list.add(new TechnologyEditTest("MOCMOS"));
		list.add(new TechnologyEditTest("MOCMOSOLD"));
		list.add(new TechnologyEditTest("MOCMOSSUB"));
		list.add(new TechnologyEditTest("NMOS"));
		return list;
	}

	public static String getOutputDirectory()
	{
		String rootPath = User.getRegressionPath();
		if (rootPath == null) return null;
		return rootPath + "/tools/Database/output/";
	}

	public Boolean BICMOS() { return runTest("bicmos"); }
	public Boolean CMOS() { return runTest("cmos"); }
	public Boolean MOCMOS() { return runTest("mocmos"); }
	public Boolean MOCMOSOLD() { return runTest("mocmosold"); }
	public Boolean MOCMOSSUB() { return runTest("mocmossub"); }
	public Boolean NMOS() { return runTest("nmos"); }

	private Boolean runTest(String techName)
	{
		String trueRootPath = getValidRootPath(getRegressionPath(), "/tools/Database/", "");
		String outputDir = trueRootPath + "output/";
		ensureOutputDirectory(outputDir);
		MessagesStream.getMessagesStream().save(outputDir + techName + ".log");

		List<String> ignoreNodes = new ArrayList<String>();
		Set<String> ignoreNodeSize = new HashSet<String>();
		Set<String> ignoreTransistorFactors = new HashSet<String>();
		if (techName.equals("mocmos"))
		{
			// mocmos must be set to submicron rules for this to work
			Map<Setting,Object> originalContext = EDatabase.currentDatabase().getSettings();
			Integer initialTechRules = (Integer)originalContext.get(Technology.getMocmosTechnology().getSetting("MOCMOS Rule Set"));
			if (initialTechRules.intValue() != MoCMOS.SUBMRULES)
			{
				System.out.println("ERROR: Cannot test mocmos technology editing unless rules are set to Submicron");
				return Boolean.FALSE;
			}

			// remove scalable transistors
			ignoreNodes.add("P-Transistor-Scalable");
			ignoreNodes.add("N-Transistor-Scalable");

			// ignore inset sizes for contacts
			ignoreNodeSize.add("Metal-1-Metal-2-Con");
			ignoreNodeSize.add("Metal-2-Metal-3-Con");
			ignoreNodeSize.add("Metal-3-Metal-4-Con");
			ignoreNodeSize.add("Metal-4-Metal-5-Con");
			ignoreNodeSize.add("Metal-5-Metal-6-Con");
			ignoreNodeSize.add("Metal-1-P-Well-Con");
			ignoreNodeSize.add("Metal-1-N-Well-Con");

			// ignore serpentine factors for transistors
			ignoreTransistorFactors.add("P-Transistor");
			ignoreTransistorFactors.add("Thick-P-Transistor");
			ignoreTransistorFactors.add("N-Transistor");
			ignoreTransistorFactors.add("Thick-N-Transistor");

		} else if (techName.equals("bicmos"))
		{
			// for bicmos, remove complex transistors
			ignoreNodes.add("NPN1_transistor");
			ignoreNodes.add("NPN2_transistor");
		}
		boolean good = true;
		try
		{
			// convert the technology to a library
			Technology tech = Technology.findTechnology(techName);
			Library techLib = TechToLib.makeLibFromTech(tech, ep, gp);
			if (techLib == null)
			{
				System.out.println("ERROR: Failed to create library for " + tech.getTechName() + " technology");
				return Boolean.FALSE;
			}

			for(String ignoreName : ignoreNodes)
			{
				Cell ignore = techLib.findNodeProto("node-" + ignoreName + "{lay}");
				if (ignore != null) ignore.kill();
			}

			// convert the library back to a technology
			LibToTech ltt = new LibToTech();
			TechConversionResult tcr = new TechConversionResult();
			Technology libTech = ltt.makeTech(techLib, techName + "NEW", null, tcr, ep);
			if (libTech == null || tcr.failed())
			{
				System.out.println("ERROR: " + tcr.getErrorMessage());
				System.out.println("Failed to create technology from library");
				return Boolean.FALSE;
			}
			good = compareTechnologies(tech, libTech, ignoreNodes, ignoreNodeSize, ignoreTransistorFactors, ep);
		} catch (Exception e)
		{
			// Catching any type of exception
			e.printStackTrace();
			good = false;
		}
		return Boolean.valueOf(good);
	}

	private boolean compareTechnologies(Technology tech1, Technology tech2, List<String> ignoreNodes, Set<String> ignoreNodeSize,
		Set<String> ignoreTransistorFactors, EditingPreferences ep)
	{
		String tech1Name = "technology " + tech1.getTechName();
		String tech2Name = "technology " + tech2.getTechName();
		MutableBoolean mGood = new MutableBoolean(true);

		// compare general technology factors
		doTest(tech1.getScaleSetting().getFactoryValue(), tech2.getScaleSetting().getFactoryValue(), mGood,
			null, "scale", tech1Name, tech2Name);
		doTest(tech1.getNumMetalsSetting().getFactoryValue(), tech2.getNumMetalsSetting().getFactoryValue(), mGood,
			null, "num-metals", tech1Name, tech2Name);
		doTest(tech1.getTechDesc(), tech2.getTechDesc(), mGood,
			null, "description", tech1Name, tech2Name);
		doTest(tech1.getMinResistanceSetting().getFactoryValue(), tech2.getMinResistanceSetting().getFactoryValue(), mGood,
			null, "min-resistance", tech1Name, tech2Name);
		doTest(tech1.getMinCapacitanceSetting().getFactoryValue(), tech2.getMinCapacitanceSetting().getFactoryValue(), mGood,
			null, "min-capacitance", tech1Name, tech2Name);
		doTest(tech1.getMaxSeriesResistanceSetting().getFactoryValue(), tech2.getMaxSeriesResistanceSetting().getFactoryValue(), mGood,
			null, "min-series-resistance", tech1Name, tech2Name);
		doTest(tech1.getGateLengthSubtractionSetting().getFactoryValue(), tech2.getGateLengthSubtractionSetting().getFactoryValue(), mGood,
			null, "gate-shrinkage", tech1Name, tech2Name);
		doTest(tech1.getGateIncludedSetting().getFactoryValue(), tech2.getGateIncludedSetting().getFactoryValue(), mGood,
			null, "gate-inclusion", tech1Name, tech2Name);
		doTest(Boolean.valueOf(tech1.isGroundNetIncluded()), Boolean.valueOf(tech2.isGroundNetIncluded()), mGood,
			null, "parasitics-includes-ground", tech1Name, tech2Name);

		// TODO: compare transparent colors

		// compare layers
		int maxLayers = Math.max(tech1.getNumLayers(), tech2.getNumLayers());
		for(int i=0; i<maxLayers; i++)
		{
			if (i >= tech1.getNumLayers())
			{
				System.out.println("ERROR: Layer " + tech2.getLayer(i).getName() + " exists in technology " + tech2.getTechName() +
					" but not in technology " + tech1.getTechName());
				mGood.setValue(false);
				continue;
			}
			if (i >= tech2.getNumLayers())
			{
				System.out.println("ERROR: Layer " + tech1.getLayer(i).getName() + " exists in technology " + tech1.getTechName() +
					" but not in technology " + tech2.getTechName());
				mGood.setValue(false);
				continue;
			}
			Layer layer1 = tech1.getLayer(i);
			Layer layer2 = tech2.getLayer(i);

			// make sure the names match
			doTest(layer1.getName(), layer2.getName(), mGood, "Layer " + i, "name", tech1Name, tech2Name);
			String layerName = "Layer " + layer1.getName();

			// make sure the functions match
			doTest(layer1.getFunction(), layer2.getFunction(), mGood, layerName, "function", tech1Name, tech2Name);
			doTest(Layer.Function.getExtraName(layer1.getFunctionExtras()),
				Layer.Function.getExtraName(layer2.getFunctionExtras()), mGood,
				layerName, "function-extra", tech1Name, tech2Name);

			// make sure the foreign-file layer information matches
			doTest(layer1.getCIFLayer(), layer2.getCIFLayer(), mGood, layerName, "CIF-layer", tech1Name, tech2Name);
			doTest(layer1.getDXFLayer(), layer2.getDXFLayer(), mGood, layerName, "DXF-layer", tech1Name, tech2Name);

			// make sure the parasitic information matches
			doTest(new Double(layer1.getResistance()), new Double(layer2.getResistance()), mGood,
				layerName, "resistance", tech1Name, tech2Name);
			doTest(new Double(layer1.getCapacitance()), new Double(layer2.getCapacitance()), mGood,
				layerName, "capacitance", tech1Name, tech2Name);
			doTest(new Double(layer1.getEdgeCapacitance()), new Double(layer2.getEdgeCapacitance()), mGood,
				layerName, "edge-capacitance", tech1Name, tech2Name);

			// make sure the graphics matches
			EGraphics gra1 = layer1.getFactoryGraphics();
			EGraphics gra2 = layer2.getFactoryGraphics();
			doTest(gra1.getColor(), gra2.getColor(), mGood,
				layerName, "color", tech1Name, tech2Name);
			doTest(Boolean.valueOf(gra1.getForeground()), Boolean.valueOf(gra2.getForeground()), mGood,
				layerName, "foreground", tech1Name, tech2Name);
			doTest(new Double(gra1.getOpacity()), new Double(gra2.getOpacity()), mGood,
				layerName, "opacity", tech1Name, tech2Name);
			doTest(gra1.getOutlined(), gra2.getOutlined(), mGood,
				layerName, "outline", tech1Name, tech2Name);
			doTest(new Integer(gra1.getTransparentLayer()), new Integer(gra2.getTransparentLayer()), mGood,
				layerName, "transparency", tech1Name, tech2Name);
			boolean patSame = true;
			for(int j=0; j<16; j++) if (gra1.getPattern()[j] != gra2.getPattern()[j]) patSame = false;
			if (!patSame)
			{
				System.out.println("ERROR: " + layerName + " has pattern " + gra1.getPattern() +
					" in " + tech1Name + " but pattern " + gra2.getPattern() + " in " + tech2Name);
				mGood.setValue(false);
			}
			doTest(Boolean.valueOf(gra1.isPatternedOnDisplay()), Boolean.valueOf(gra2.isPatternedOnDisplay()), mGood,
				layerName, "display-pattern", tech1Name, tech2Name);
			doTest(Boolean.valueOf(gra1.isPatternedOnPrinter()), Boolean.valueOf(gra2.isPatternedOnPrinter()), mGood,
				layerName, "printer-pattern", tech1Name, tech2Name);

			// make sure the pure-layer node matches
			String pureName1 = "";
			PrimitiveNode pure1 = layer1.getPureLayerNode();
			if (pure1 != null) pureName1 = pure1.getName();
			String pureName2 = "";
			PrimitiveNode pure2 = layer2.getPureLayerNode();
			if (pure2 != null) pureName2 = pure2.getName();
			doTest(pureName1, pureName2, mGood, layerName, "pure-layer node", tech1Name, tech2Name);

			// make sure the pure-layer node matches
			doTest(Boolean.FALSE, Boolean.FALSE, mGood, layerName, "pseudo", tech1Name, tech2Name);
		}

		// compare arcs
		List<ArcProto> arcs1 = new ArrayList<ArcProto>();
		for(Iterator<ArcProto> it = tech1.getArcs(); it.hasNext(); )
		{
			ArcProto ap = it.next();
			if (!ap.isNotUsed()) arcs1.add(ap);
		}
		List<ArcProto> arcs2 = new ArrayList<ArcProto>();
		for(Iterator<ArcProto> it = tech2.getArcs(); it.hasNext(); )
		{
			ArcProto ap = it.next();
			if (!ap.isNotUsed()) arcs2.add(ap);
		}
		int maxArcs = Math.max(arcs1.size(), arcs2.size());
		for(int i=0; i<maxArcs; i++)
		{
			if (i >= arcs1.size())
			{
				System.out.println("ERROR: Arc " + arcs2.get(i).getName() + " exists in technology " + tech2.getTechName() +
					" but not in technology " + tech1.getTechName());
				mGood.setValue(false);
				continue;
			}
			if (i >= arcs2.size())
			{
				System.out.println("ERROR: Arc " + arcs1.get(i).getName() + " exists in technology " + tech1.getTechName() +
					" but not in technology " + tech2.getTechName());
				mGood.setValue(false);
				continue;
			}

			// make sure the names match
			ArcProto ap1 = arcs1.get(i);
			ArcProto ap2 = arcs2.get(i);
			doTest(ap1.getName(), ap2.getName(), mGood, "Arc " + i, "name", tech1Name, tech2Name);
			String arcName = "Arc " + ap1.getName();

			// make sure the functions match
			doTest(ap1.getFunction(), ap2.getFunction(), mGood, arcName, "function", tech1Name, tech2Name);

			// make sure the other factors match
			doTest(Boolean.valueOf(ap1.getFactoryDefaultInst().isFixedAngle()), Boolean.valueOf(ap2.getFactoryDefaultInst().isFixedAngle()), mGood,
				arcName, "fixed-angle", tech1Name, tech2Name);
			doTest(Boolean.valueOf(ap1.isWipable()), Boolean.valueOf(ap2.isWipable()), mGood,
				arcName, "wipable", tech1Name, tech2Name);
			doTest(Boolean.valueOf(ap1.getFactoryDefaultInst().isTailExtended()), Boolean.valueOf(ap2.getFactoryDefaultInst().isTailExtended()), mGood,
				arcName, "extended", tech1Name, tech2Name);
			doTest(new Integer(ap1.getFactoryAngleIncrement()), new Integer(ap2.getFactoryAngleIncrement()), mGood,
				arcName, "angle-increment", tech1Name, tech2Name);
			doTest(new Double(ap1.getFactoryAntennaRatio()), new Double(ap2.getFactoryAntennaRatio()), mGood,
				arcName, "angle-increment", tech1Name, tech2Name);

			// make sure the layers match
			int al1 = ap1.getNumArcLayers();
			int [] cross1to2 = new int[al1];
			for(int j=0; j<al1; j++) cross1to2[j] = -1;
			int al2 = ap2.getNumArcLayers();
			int [] cross2to1 = new int[al2];
			for(int j=0; j<al2; j++) cross2to1[j] = -1;
			boolean matched = true;
			if (al1 != al2) matched = false; else
			{
				for(int ai1=0; ai1<al2; ai1++)
				{
					String ai1Name = ap1.getLayer(ai1).getName();
					for(int ai2=0; ai2<al2; ai2++)
					{
						if (cross2to1[ai2] >= 0) continue;
						if (ai1Name.equals(ap2.getLayer(ai2).getName()))
						{
							cross1to2[ai1] = ai2;
							cross2to1[ai2] = ai1;
						}
					}
				}
				for(int j=0; j<al2; j++) if (cross1to2[j] < 0 || cross2to1[j] < 0) matched = false;
			}
			if (!matched)
			{
				String errMsg = "ERROR: " + arcName + " has layers";
				for(int j=0; j<al1; j++) errMsg += " " + ap1.getLayer(j).getName();
				errMsg += " in technology " + tech2.getTechName() + " but layers";
				for(int j=0; j<al2; j++) errMsg += " " + ap2.getLayer(j).getName();
				errMsg += " in technology " + tech1.getTechName();
				System.out.println(errMsg);
				mGood.setValue(false);
			} else for(int ai1=0; ai1<al1; ai1++)
			{
				int ai2 = cross1to2[ai1];
				String arcLayerName = arcName + " layer " + ap1.getLayer(ai1).getName();
				doTest(ap1.getLayerStyle(ai1), ap2.getLayerStyle(ai2), mGood,
					arcLayerName, "layer-style", tech1Name, tech2Name);
				doTest(new Double(ap1.getLayerExtend(ai1).getLambda()), new Double(ap2.getLayerExtend(ai2).getLambda()), mGood,
					arcLayerName, "layer-extension", tech1Name, tech2Name);
			}
		}

		// compare nodes
		List<PrimitiveNode> nodes1 = new ArrayList<PrimitiveNode>();
		for(Iterator<PrimitiveNode> it = tech1.getNodes(); it.hasNext(); )
		{
			PrimitiveNode np = it.next();
			boolean ignoreIt = false;
			for(String ignoreName : ignoreNodes)
				if (np.getName().equalsIgnoreCase(ignoreName)) ignoreIt = true;
			if (ignoreIt) continue;
			if (!np.isNotUsed()) nodes1.add(np);
		}
		List<PrimitiveNode> nodes2 = new ArrayList<PrimitiveNode>();
		for(Iterator<PrimitiveNode> it = tech2.getNodes(); it.hasNext(); )
		{
			PrimitiveNode np = it.next();
			boolean ignoreIt = false;
			for(String ignoreName : ignoreNodes)
				if (np.getName().equalsIgnoreCase(ignoreName)) ignoreIt = true;
			if (ignoreIt) continue;
			if (!np.isNotUsed()) nodes2.add(np);
		}
		int maxNodes = Math.max(nodes1.size(), nodes2.size());
		for(int i=0; i<maxNodes; i++)
		{
			if (i >= nodes1.size())
			{
				System.out.println("ERROR: Node " + nodes2.get(i).getName() + " exists in technology " + tech2.getTechName() +
					" but not in technology " + tech1.getTechName());
				mGood.setValue(false);
				continue;
			}
			if (i >= nodes2.size())
			{
				System.out.println("ERROR: Node " + nodes1.get(i).getName() + " exists in technology " + tech1.getTechName() +
					" but not in technology " + tech2.getTechName());
				mGood.setValue(false);
				continue;
			}

			// make sure the names match
			PrimitiveNode np1 = nodes1.get(i);
			PrimitiveNode np2 = nodes2.get(i);
			doTest(np1.getName(), np2.getName(), mGood,
				"Node " + i, "name", tech1Name, tech2Name);
			String nodeName = "Node " + np1.getName();

			// make sure the functions match
			doTest(np1.getFunction(), np2.getFunction(), mGood,
				nodeName, "function", tech1Name, tech2Name);

			// make sure the other factors match
			doTest(Boolean.valueOf(np1.isSquare()), Boolean.valueOf(np2.isSquare()), mGood,
				nodeName, "square", tech1Name, tech2Name);
			doTest(Boolean.valueOf(np1.isWipeOn1or2()), Boolean.valueOf(np2.isWipeOn1or2()), mGood,
				nodeName, "wipes-1-or-2", tech1Name, tech2Name);
			doTest(Boolean.valueOf(np1.isLockedPrim()), Boolean.valueOf(np2.isLockedPrim()), mGood,
				nodeName, "lockable", tech1Name, tech2Name);
			doTest(PrimitiveNode.getSpecialTypeName(np1.getSpecialType()), PrimitiveNode.getSpecialTypeName(np2.getSpecialType()), mGood,
				nodeName, "special-type", tech1Name, tech2Name);
			boolean isSerpentine = np1.getSpecialType() == PrimitiveNode.SERPTRANS && np2.getSpecialType() == PrimitiveNode.SERPTRANS;
			if (isSerpentine) {
				if (!ignoreTransistorFactors.contains(np1.getName()))
				{
					doTest(new Double(np1.getSpecialValues()[0]), new Double(np2.getSpecialValues()[0]), mGood,
						nodeName, "serpentine layer count", tech1Name, tech2Name);
				}
				doTest(new Double(np1.getSpecialValues()[1]), new Double(np2.getSpecialValues()[1]), mGood,
					nodeName, "serpentine active port inset from end of serpentine path", tech1Name, tech2Name);
				doTest(new Double(np1.getSpecialValues()[2]), new Double(np2.getSpecialValues()[2]), mGood,
					nodeName, "serpentine active port inset from poly edge", tech1Name, tech2Name);
				doTest(new Double(np1.getSpecialValues()[3]), new Double(np2.getSpecialValues()[3]), mGood,
					nodeName, "serpentine poly width", tech1Name, tech2Name);
				doTest(new Double(np1.getSpecialValues()[4]), new Double(np2.getSpecialValues()[4]), mGood,
					nodeName, "serpentine poly port inset from poly edge", tech1Name, tech2Name);
				doTest(new Double(np1.getSpecialValues()[5]), new Double(np2.getSpecialValues()[5]), mGood,
					nodeName, "serpentine poly port instet from active edge", tech1Name, tech2Name);
			}
			SizeOffset so1 = np1.getProtoSizeOffset();
			SizeOffset so2 = np2.getProtoSizeOffset();
			doTest(new Double(np1.getDefWidth(ep)-so1.getLowXOffset()-so1.getHighXOffset()), new Double(np2.getDefWidth(ep)-so2.getLowXOffset()-so2.getHighXOffset()), mGood,
				nodeName, "default-width", tech1Name, tech2Name);
			doTest(new Double(np1.getDefHeight(ep)-so1.getLowYOffset()-so1.getHighYOffset()), new Double(np2.getDefHeight(ep)-so2.getLowYOffset()-so2.getHighYOffset()), mGood,
				nodeName, "default-height", tech1Name, tech2Name);
			double sizeX1 = np1.getDefSize(ep).getLambdaX();
			double sizeY1 = np1.getDefSize(ep).getLambdaY();
			double sizeX2 = np2.getDefSize(ep).getLambdaX();
			double sizeY2 = np2.getDefSize(ep).getLambdaY();
			boolean adjustSize = sizeX1 != sizeX2 || sizeY1 != sizeY2;
			if (!adjustSize)
			{
				if (!ignoreNodeSize.contains(np1.getName()) && !ignoreTransistorFactors.contains(np1.getName()))
				{
					doTest(new Double(so1.getLowXOffset()), new Double(so2.getLowXOffset()), mGood,
						nodeName, "low-X-offset", tech1Name, tech2Name);
					doTest(new Double(so1.getHighXOffset()), new Double(so2.getHighXOffset()), mGood,
						nodeName, "high-X-offset", tech1Name, tech2Name);
					doTest(new Double(so1.getLowYOffset()), new Double(so2.getLowYOffset()), mGood,
						nodeName, "low-Y-offset", tech1Name, tech2Name);
					doTest(new Double(so1.getHighYOffset()), new Double(so2.getHighYOffset()), mGood,
						nodeName, "high-Y-offset", tech1Name, tech2Name);
				}
			}

			// make sure the layers match
			Technology.NodeLayer [] nlays1 = np1.getNodeLayers();
			int nl1 = nlays1.length;
			int [] cross1to2 = new int[nl1];
			for(int j=0; j<nl1; j++) cross1to2[j] = -1;
			Technology.NodeLayer [] nlays2 = np2.getNodeLayers();
			int nl2 = nlays2.length;
			int [] cross2to1 = new int[nl2];
			for(int j=0; j<nl2; j++) cross2to1[j] = -1;
			boolean matched = true;
			if (nl1 != nl2) matched = false; else
			{
				for(int ni1=0; ni1<nl2; ni1++)
				{
					String ni1Name = nlays1[ni1].getLayer().getName();
					for(int ni2=0; ni2<nl2; ni2++)
					{
						if (cross2to1[ni2] >= 0) continue;
						if (ni1Name.equals(nlays2[ni2].getLayer().getName()))
						{
							cross1to2[ni1] = ni2;
							cross2to1[ni2] = ni1;
						}
					}
				}
				for(int j=0; j<nl2; j++) if (cross1to2[j] < 0 || cross2to1[j] < 0) matched = false;
			}
			if (!matched)
			{
				String errMsg = "ERROR: " + nodeName + " has layers";
				for(int j=0; j<nl1; j++) errMsg += " " + nlays1[j].getLayer().getName();
				errMsg += " in technology " + tech2.getTechName() + " but layers";
				for(int j=0; j<nl2; j++) errMsg += " " + nlays2[j].getLayer().getName();
				errMsg += " in technology " + tech1.getTechName();
				System.out.println(errMsg);
				mGood.setValue(false);
			} else for(int ni1=0; ni1<nl1; ni1++)
			{
				int ni2 = cross1to2[ni1];
				String nodeLayerName = nodeName + " layer " + nlays1[ni1].getLayer().getName();
				doTest(nlays1[ni1].getStyle(), nlays2[ni2].getStyle(), mGood,
					nodeLayerName, "layer-style", tech1Name, tech2Name);
				if (!ignoreTransistorFactors.contains(np1.getName()))
				{
					doTest(new Integer(nlays1[ni1].getPortNum()), new Integer(nlays2[ni2].getPortNum()), mGood,
						nodeLayerName, "port-num", tech1Name, tech2Name);
				}
				int r1 = nlays1[ni1].getRepresentation();
				int r2 = nlays2[ni2].getRepresentation();
				doTest(Technology.NodeLayer.getRepresentationName(r1), Technology.NodeLayer.getRepresentationName(r2), mGood,
					nodeLayerName, "layer-representation", tech1Name, tech2Name);
				if (adjustSize)
				{
					EdgeV botEdge1 = nlays1[ni1].getBottomEdge();
					EdgeV botEdge2 = nlays2[ni2].getBottomEdge();
					doTest(new Double(botEdge1.getMultiplier() * sizeY1 + botEdge1.getAdder().getLambda()),
						new Double(botEdge2.getMultiplier() * sizeY2 + botEdge2.getAdder().getLambda()), mGood,
						nodeLayerName, "bottom-edge", tech1Name, tech2Name);
					EdgeV topEdge1 = nlays1[ni1].getTopEdge();
					EdgeV topEdge2 = nlays2[ni2].getTopEdge();
					doTest(new Double(topEdge1.getMultiplier() * sizeY1 + topEdge1.getAdder().getLambda()),
						new Double(topEdge2.getMultiplier() * sizeY2 + topEdge2.getAdder().getLambda()), mGood,
						nodeLayerName, "top-edge", tech1Name, tech2Name);

					EdgeH lftEdge1 = nlays1[ni1].getLeftEdge();
					EdgeH lftEdge2 = nlays2[ni2].getLeftEdge();
					doTest(new Double(lftEdge1.getMultiplier() * sizeX1 + lftEdge1.getAdder().getLambda()),
						new Double(lftEdge2.getMultiplier() * sizeX2 + lftEdge2.getAdder().getLambda()), mGood,
						nodeLayerName, "left-edge", tech1Name, tech2Name);
					EdgeH rgtEdge1 = nlays1[ni1].getRightEdge();
					EdgeH rgtEdge2 = nlays2[ni2].getRightEdge();
					doTest(new Double(rgtEdge1.getMultiplier() * sizeX1 + rgtEdge1.getAdder().getLambda()),
						new Double(rgtEdge2.getMultiplier() * sizeX2 + rgtEdge2.getAdder().getLambda()), mGood,
						nodeLayerName, "right-edge", tech1Name, tech2Name);
				} else
				{
					doTest(nlays1[ni1].getBottomEdge(), nlays2[ni2].getBottomEdge(), mGood,
						nodeLayerName, "bottom-edge", tech1Name, tech2Name);
					doTest(nlays1[ni1].getTopEdge(), nlays2[ni2].getTopEdge(), mGood,
						nodeLayerName, "top-edge", tech1Name, tech2Name);
					doTest(nlays1[ni1].getLeftEdge(), nlays2[ni2].getLeftEdge(), mGood,
						nodeLayerName, "left-edge", tech1Name, tech2Name);
					doTest(nlays1[ni1].getRightEdge(), nlays2[ni2].getRightEdge(), mGood,
						nodeLayerName, "right-edge", tech1Name, tech2Name);
				}

				// special case for multi-cut layers
				if (nlays1[ni1].getRepresentation() == NodeLayer.MULTICUTBOX)
				{
					doTest(nlays1[ni1].getMulticutSep1D(), nlays2[ni2].getMulticutSep1D(), mGood,
						nodeLayerName, "multicut-X-separation", tech1Name, tech2Name);
					doTest(nlays1[ni1].getMulticutSep2D(), nlays2[ni2].getMulticutSep2D(), mGood,
						nodeLayerName, "multicut-Y-separation", tech1Name, tech2Name);
					doTest(nlays1[ni1].getMulticutSizeX(), nlays2[ni2].getMulticutSizeX(), mGood,
						nodeLayerName, "multicut-X-size", tech1Name, tech2Name);
					doTest(nlays1[ni1].getMulticutSizeY(), nlays2[ni2].getMulticutSizeY(), mGood,
						nodeLayerName, "multicut-Y-size", tech1Name, tech2Name);
				}
				if (isSerpentine) {
					doTest(nlays1[ni1].getSerpentineExtentB(), nlays2[ni2].getSerpentineExtentB(), mGood,
						nodeLayerName, "serpentine bottom extend", tech1Name, tech2Name);
					doTest(nlays1[ni1].getSerpentineExtentT(), nlays2[ni2].getSerpentineExtentT(), mGood,
						nodeLayerName, "serpentine top extend", tech1Name, tech2Name);
					doTest(nlays1[ni1].getSerpentineLWidth(), nlays2[ni2].getSerpentineLWidth(), mGood,
						nodeLayerName, "serpentine left width", tech1Name, tech2Name);
					doTest(nlays1[ni1].getSerpentineRWidth(), nlays2[ni2].getSerpentineRWidth(), mGood,
						nodeLayerName, "serpentine right width", tech1Name, tech2Name);
				}
			}

			// make sure the ports match
			int npTot1 = np1.getNumPorts();
			cross1to2 = new int[npTot1];
			for(int j=0; j<npTot1; j++) cross1to2[j] = -1;
			int npTot2 = np2.getNumPorts();
			cross2to1 = new int[npTot2];
			for(int j=0; j<npTot2; j++) cross2to1[j] = -1;
			matched = true;
			if (npTot1 != npTot2) matched = false; else
			{
				for(int ni1=0; ni1<npTot2; ni1++)
				{
					String ni1Name = np1.getPort(ni1).getName();
					for(int ni2=0; ni2<npTot2; ni2++)
					{
						if (cross2to1[ni2] >= 0) continue;
						if (ni1Name.equals(np2.getPort(ni2).getName()))
						{
							cross1to2[ni1] = ni2;
							cross2to1[ni2] = ni1;
						}
					}
				}
				for(int j=0; j<npTot2; j++) if (cross1to2[j] < 0 || cross2to1[j] < 0) matched = false;
			}
			if (!matched)
			{
				String errMsg = "ERROR: " + nodeName + " has ports";
				for(int j=0; j<npTot1; j++) errMsg += " " + np1.getPort(j).getName();
				errMsg += " in technology " + tech2.getTechName() + " but ports";
				for(int j=0; j<npTot2; j++) errMsg += " " + np2.getPort(j).getName();
				errMsg += " in technology " + tech1.getTechName();
				System.out.println(errMsg);
				mGood.setValue(false);
			} else for(int ni1=0; ni1<npTot1; ni1++)
			{
				int ni2 = cross1to2[ni1];
				PrimitivePort pp1 = np1.getPort(ni1);
				PrimitivePort pp2 = np2.getPort(ni2);
				String nodePortName = nodeName + " port " + pp1.getName();
				if (adjustSize)
				{
					EdgeV botEdge1 = pp1.getBottom();
					EdgeV botEdge2 = pp2.getBottom();
					doTest(new Double(botEdge1.getMultiplier() * sizeY1 + botEdge1.getAdder().getLambda()),
						new Double(botEdge2.getMultiplier() * sizeY2 + botEdge2.getAdder().getLambda()), mGood,
						nodePortName, "bottom-edge", tech1Name, tech2Name);
					EdgeV topEdge1 = pp1.getTop();
					EdgeV topEdge2 = pp2.getTop();
					doTest(new Double(topEdge1.getMultiplier() * sizeY1 + topEdge1.getAdder().getLambda()),
						new Double(topEdge2.getMultiplier() * sizeY2 + topEdge2.getAdder().getLambda()), mGood,
						nodePortName, "top-edge", tech1Name, tech2Name);

					EdgeH lftEdge1 = pp1.getLeft();
					EdgeH lftEdge2 = pp2.getLeft();
					doTest(new Double(lftEdge1.getMultiplier() * sizeX1 + lftEdge1.getAdder().getLambda()),
						new Double(lftEdge2.getMultiplier() * sizeX2 + lftEdge2.getAdder().getLambda()), mGood,
						nodePortName, "left-edge", tech1Name, tech2Name);
					EdgeH rgtEdge1 = pp1.getRight();
					EdgeH rgtEdge2 = pp2.getRight();
					doTest(new Double(rgtEdge1.getMultiplier() * sizeX1 + rgtEdge1.getAdder().getLambda()),
						new Double(rgtEdge2.getMultiplier() * sizeX2 + rgtEdge2.getAdder().getLambda()), mGood,
						nodePortName, "right-edge", tech1Name, tech2Name);
				} else
				{
					doTest(pp1.getBottom(), pp2.getBottom(), mGood,
						nodePortName, "bottom-edge", tech1Name, tech2Name);
					doTest(pp1.getTop(), pp2.getTop(), mGood,
						nodePortName, "top-edge", tech1Name, tech2Name);
					doTest(pp1.getLeft(), pp2.getLeft(), mGood,
						nodePortName, "left-edge", tech1Name, tech2Name);
					doTest(pp1.getRight(), pp2.getRight(), mGood,
						nodePortName, "right-edge", tech1Name, tech2Name);
				}
				if (!ignoreTransistorFactors.contains(np1.getName()))
				{
					doTest(new Integer(pp1.getTopology()), new Integer(pp2.getTopology()), mGood,
						nodePortName, "topology", tech1Name, tech2Name);
				}
				doTest(new Integer(pp1.getAngle()), new Integer(pp2.getAngle()), mGood,
					nodePortName, "angle", tech1Name, tech2Name);
				doTest(new Integer(pp1.getAngleRange()), new Integer(pp2.getAngleRange()), mGood,
					nodePortName, "angle", tech1Name, tech2Name);

				// compare connectivity
				ArcProto [] pp1Conn = pp1.getConnections();
				ArcProto [] pp2Conn = pp2.getConnections();
				boolean connSame = true;
				for(int i1 = 0; i1 < pp1Conn.length; i1++)
				{
					if (pp1Conn[i1].getTechnology() == Generic.tech()) continue;
					if (pp1Conn[i1].isNotUsed()) continue;
					boolean found = false;
					for(int i2=0; i2<pp2Conn.length; i2++)
						if (pp1Conn[i1].getName().equals(pp2Conn[i2].getName())) found = true;
					if (!found) connSame = false;
				}
				for(int i2 = 0; i2 < pp2Conn.length; i2++)
				{
					if (pp2Conn[i2].isNotUsed()) continue;
					if (pp2Conn[i2].getTechnology() == Generic.tech()) continue;
					boolean found = false;
					for(int i1=0; i1<pp2Conn.length; i1++)
						if (pp2Conn[i2].getName().equals(pp1Conn[i1].getName())) found = true;
					if (!found) connSame = false;
				}
				if (!connSame)
				{
					String errMsg = "ERROR: " + nodePortName + " connects to";
					for(int j=0; j<pp1Conn.length; j++) if (pp1Conn[j].getTechnology() != Generic.tech())
						errMsg += " " + pp1Conn[j].getName();
					errMsg += " in technology " + tech2.getTechName() + " but connects to";
					for(int j=0; j<pp2Conn.length; j++) if (pp2Conn[j].getTechnology() != Generic.tech())
						errMsg += " " + pp2Conn[j].getName();
					errMsg += " in technology " + tech1.getTechName();
					System.out.println(errMsg);
					mGood.setValue(false);
				}
			}
		}
		return mGood.booleanValue();
	}

	private void doTest(Object obj1, Object obj2, MutableBoolean good, String objectName, String attrName,
		String obj1Name, String obj2Name)
	{
		if (!obj1.equals(obj2))
		{
			String errMsg = "ERROR: ";
			if (objectName == null) errMsg += "found"; else
				errMsg += objectName + " has ";
			errMsg += attrName + " '" + obj1 + "' in " + obj1Name +
				" but " + attrName + " '" + obj2 + "' in " + obj2Name;
			System.out.println(errMsg);
			good.setValue(false);
		}
	}
}
