	/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: MakeFakeCircuitry.java
 *
 * Copyright (c) 2006, Static Free Software. All rights reserved.
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
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.EDatabase;
import com.sun.electric.database.hierarchy.Export;
import com.sun.electric.database.hierarchy.Library;
import com.sun.electric.database.id.CellId;
import com.sun.electric.database.prototype.NodeProto;
import com.sun.electric.database.prototype.PortCharacteristic;
import com.sun.electric.database.topology.ArcInst;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.topology.PortInst;
import com.sun.electric.database.variable.TextDescriptor;
import com.sun.electric.technology.ArcProto;
import com.sun.electric.technology.Technology;
import com.sun.electric.technology.technologies.Artwork;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.JobException;
import com.sun.electric.tool.user.User;
import com.sun.electric.util.math.Orientation;

import java.awt.geom.Point2D;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;

/**
 * Class to read a library in a new thread.
 */
public class MakeFakeCircuitry extends Job
{
	private String theTechnology;
	private String theLibrary;
	private Cell myCell;
	private Map<CellId,BitSet> nodesToExpand = new HashMap<CellId,BitSet>();

	/**
	 * Used by regressions and GUI
	 * @param lib
	 * @param tech
	 * @param asJob
	 */
	public static void makeFakeCircuitryCommand(String lib, String tech, Boolean asJob, EditingPreferences ep)
	{
		// test code to make and show something
		if (asJob.booleanValue())
		{
			new MakeFakeCircuitry(lib, tech);
		} else
		{
			doItInternal(lib, tech, new HashMap<CellId,BitSet>(), ep);
		}
	}

	protected MakeFakeCircuitry(String lib, String tech)
	{
		super("Make fake circuitry", User.getUserTool(), Job.Type.CHANGE, null, null, Job.Priority.USER);
		theLibrary = lib;
		theTechnology = tech;
		startJob();
	}

	@Override
	public boolean doIt() throws JobException
	{
		myCell = doItInternal(theLibrary, theTechnology, nodesToExpand, getEditingPreferences());
		fieldVariableChanged("myCell");
		fieldVariableChanged("nodesToExpand");
		return true;
	}

	@Override
	public void terminateOK()
	{
		getDatabase().expandNodes(nodesToExpand);
		Job.getUserInterface().displayCell(myCell);
	}

	/**
	 * External static call for regressions
	 * @param library
	 * @param technology
	 * @return the generated Cell.
	 */
	private static Cell doItInternal(String library, String technology, Map<CellId,BitSet> nodesToExpand, EditingPreferences ep)
	{
		EDatabase database = EDatabase.currentDatabase();
		// get information about the nodes
		Technology tech = Technology.findTechnology(technology);
		if (tech == null)
		{
			System.out.println("Technology not found in MakeFakeCircuitry");
			return null;
		}

		StringBuffer polyName = new StringBuffer("Polysilicon");
		String lateral = "top";
		int traRot = 0;
		int rotTraRot = 3150;

		if (technology.equals("mocmos"))
		{
			polyName.append("-1");
			lateral = "right";
		} else
		{
			traRot = 2700;
			rotTraRot = 2250;
		}

		NodeProto m1m2Proto = Cell.findNodeProto(technology+":Metal-1-Metal-2-Con");
		NodeProto m2PinProto = Cell.findNodeProto(technology+":Metal-2-Pin");
		NodeProto p1PinProto = Cell.findNodeProto(technology+":" + polyName + "-Pin");
		NodeProto m1PolyConProto = Cell.findNodeProto(technology+":Metal-1-" + polyName + "-Con");
		NodeProto pTransProto = Cell.findNodeProto(technology+":P-Transistor");
		NodeProto nTransProto = Cell.findNodeProto(technology+":N-Transistor");
		NodeProto invisiblePinProto = Cell.findNodeProto("generic:Invisible-Pin");

		// get information about the arcs
		ArcProto m1Proto = ArcProto.findArcProto(technology+":Metal-1");
		if (m1Proto == null)
			m1Proto = ArcProto.findArcProto(technology+":metal-1");
		ArcProto m2Proto = ArcProto.findArcProto(technology+":Metal-2");
		if (m2Proto == null)
			m2Proto = ArcProto.findArcProto(technology+":metal-2");
		ArcProto p1Proto = ArcProto.findArcProto(technology+":"+polyName);

		// create the test library
		Library mainLib = Library.newInstance(library, null);

		// create a layout cell in the library
		Cell myCell = Cell.makeInstance(ep, mainLib, technology+"test{lay}");
		NodeInst metal12Via = NodeInst.newInstance(m1m2Proto, ep, new Point2D.Double(-20.0, 20.0), m1m2Proto.getDefWidth(ep), m1m2Proto.getDefHeight(ep), myCell);
		NodeInst contactNode = NodeInst.newInstance(m1PolyConProto, ep, new Point2D.Double(20.0, 20.0), m1PolyConProto.getDefWidth(ep), m1PolyConProto.getDefHeight(ep), myCell);
		NodeInst metal2Pin = NodeInst.newInstance(m2PinProto, ep, new Point2D.Double(-20.0, 10.0), m2PinProto.getDefWidth(ep), m2PinProto.getDefHeight(ep), myCell);
		NodeInst poly1PinA = NodeInst.newInstance(p1PinProto, ep, new Point2D.Double(20.0, -20.0), p1PinProto.getDefWidth(ep), p1PinProto.getDefHeight(ep), myCell);
		NodeInst poly1PinB = NodeInst.newInstance(p1PinProto, ep, new Point2D.Double(20.0, -10.0), p1PinProto.getDefWidth(ep), p1PinProto.getDefHeight(ep), myCell);
		NodeInst transistor = NodeInst.newInstance(pTransProto, ep, new Point2D.Double(0.0, -20.0), pTransProto.getDefWidth(ep), pTransProto.getDefHeight(ep), myCell, Orientation.fromAngle(traRot), null);
		NodeInst rotTrans = NodeInst.newInstance(nTransProto, ep, new Point2D.Double(0.0, 10.0), nTransProto.getDefWidth(ep), nTransProto.getDefHeight(ep), myCell, Orientation.fromAngle(rotTraRot), "rotated");
		if (metal12Via == null || contactNode == null || metal2Pin == null || poly1PinA == null ||
			poly1PinB == null || transistor == null || rotTrans == null) return myCell;

		// make arcs to connect them
		PortInst m1m2Port = metal12Via.getOnlyPortInst();
		PortInst contactPort = contactNode.getOnlyPortInst();
		PortInst m2Port = metal2Pin.getOnlyPortInst();
		PortInst p1PortA = poly1PinA.getOnlyPortInst();
		PortInst p1PortB = poly1PinB.getOnlyPortInst();
		PortInst transPortR = transistor.findPortInst("poly-" + lateral);

		// Old style
		if (transPortR == null) transPortR = transistor.findPortInst("p-trans-poly-" + lateral);
		PortInst transRPortR = rotTrans.findPortInst("poly-" + lateral);

		// Old style
		if (transRPortR == null) transRPortR = rotTrans.findPortInst("n-trans-poly-" + lateral);
		ArcInst metal2Arc = ArcInst.makeInstance(m2Proto, ep, m2Port, m1m2Port);
		if (metal2Arc == null) return myCell;
		metal2Arc.setRigid(true);
		ArcInst metal1Arc = ArcInst.makeInstance(m1Proto, ep, contactPort, m1m2Port);
		if (metal1Arc == null) return myCell;
		ArcInst polyArc1 = ArcInst.makeInstance(p1Proto, ep, contactPort, p1PortB);
		if (polyArc1 == null) return myCell;
		ArcInst polyArc3 = ArcInst.makeInstance(p1Proto, ep, p1PortB, p1PortA);
		if (polyArc3 == null) return myCell;
		ArcInst polyArc2 = ArcInst.makeInstance(p1Proto, ep, transPortR, p1PortA);
		if (polyArc2 == null) return myCell;
		ArcInst polyArc4 = ArcInst.makeInstance(p1Proto, ep, transRPortR, p1PortB);
		if (polyArc4 == null) return myCell;

		// export the two pins
		Export.newInstance(myCell, m1m2Port, "in", ep, PortCharacteristic.IN);
		Export.newInstance(myCell, p1PortA, "out", ep, PortCharacteristic.OUT);
		System.out.println("Created " + myCell);

		// now up the hierarchy
		Cell higherCell = Cell.makeInstance(ep, mainLib, "higher{lay}");
		double myWidth = myCell.getDefWidth();
		double myHeight = myCell.getDefHeight();
		for (int iX = 0; iX < 2; iX++)
		{
			boolean flipX = iX != 0;
			for (int i = 0; i < 4; i++)
			{
				Orientation orient = Orientation.fromJava(i*900, flipX, false);
				NodeInst instanceNode = NodeInst.newInstance(myCell, ep, new Point2D.Double(i*100, iX*200), myWidth, myHeight, higherCell, orient, null);
				database.addToNodes(nodesToExpand, instanceNode);
				NodeInst instanceUNode = NodeInst.newInstance(myCell, ep, new Point2D.Double(i*100, iX*200 + 100), myWidth, myHeight, higherCell, orient, null);
				if (iX == 0 && i == 0)
				{
					PortInst instance1Port = instanceNode.findPortInst("in");
					PortInst instance2Port = instanceUNode.findPortInst("in");
					ArcInst.makeInstance(m1Proto, ep, instance1Port, instance2Port);
				}
			}
		}
		System.out.println("Created " + higherCell);

		// now a rotation test
		Cell rotTestCell = Cell.makeInstance(ep, mainLib, "rotationTest{lay}");
		TextDescriptor td = ep.getNodeTextDescriptor().withRelSize(10);
		for (int iY = 0; iY < 2; iY++)
		{
			boolean flipY = iY != 0;
			for (int iX = 0; iX < 2; iX++)
			{
				boolean flipX = iX != 0;
				for (int i = 0; i < 4; i++)
				{
					int angle = i*900;
					Orientation orient = Orientation.fromJava(angle, flipX, flipY);
					int x = i*100;
					int y = iX*100 + iY*200;
					NodeInst ni = NodeInst.newInstance(myCell, ep, new Point2D.Double(x, y), myWidth, myHeight, rotTestCell, orient, null);
					database.addToNodes(nodesToExpand, ni);
					NodeInst nodeLabel = NodeInst.newInstance(invisiblePinProto, ep, new Point2D.Double(x, y - 35), 0, 0, rotTestCell);
					String message = "Rotated " + (orient == Orientation.IDENT ? "0" : orient.toString());
					nodeLabel.newVar(Artwork.ART_MESSAGE, message,td);
				}
			}
		}
		System.out.println("Created " + rotTestCell);

		// now up the hierarchy even farther
		Cell bigCell = Cell.makeInstance(ep, mainLib, "big{lay}");
		int arraySize = 20;
		for(int y=0; y<arraySize; y++)
		{
			for(int x=0; x<arraySize; x++)
			{
				String theName = "arr["+ x + "][" + y + "]";
				NodeInst instanceNode = NodeInst.newInstance(myCell, ep, new Point2D.Double(x*(myWidth+2), y*(myHeight+2)),
					myWidth, myHeight, bigCell, Orientation.IDENT, theName);
				instanceNode.setOff(NodeInst.NODE_NAME, 0, 8);
				if ((x%2) == (y%2)) database.addToNodes(nodesToExpand, instanceNode);
			}
		}
		System.out.println("Created " + bigCell);
		return myCell;
	}
}
