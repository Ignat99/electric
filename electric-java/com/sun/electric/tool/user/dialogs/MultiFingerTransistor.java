/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: MultiFingerTransistor.java
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

package com.sun.electric.tool.user.dialogs;

import com.sun.electric.database.EditingPreferences;
import com.sun.electric.database.geometry.EPoint;
import com.sun.electric.database.geometry.Poly;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.Export;
import com.sun.electric.database.hierarchy.Library;
import com.sun.electric.database.prototype.PortProto;
import com.sun.electric.database.text.TextUtils;
import com.sun.electric.database.topology.ArcInst;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.topology.PortInst;
import com.sun.electric.technology.ArcProto;
import com.sun.electric.technology.DRCTemplate;
import com.sun.electric.technology.Layer;
import com.sun.electric.technology.PrimitiveNode;
import com.sun.electric.technology.Technology;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.JobException;
import com.sun.electric.tool.drc.DRC;
import com.sun.electric.tool.user.User;
import com.sun.electric.tool.user.ui.TopLevel;
import com.sun.electric.tool.user.ui.WindowFrame;
import com.sun.electric.util.math.Orientation;

import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Iterator;

/**
 * Class to handle the "Multi-Finger Transistor" dialog.
 */
public class MultiFingerTransistor extends EDialog
{
	private static double lastLength = 3;
	private static double lastWidth = 15;
	private static double lastPitch = 0;
	private static double lastExtraPolyLen = 0;
	private static int lastNumFingers = 2;
	private static int lastNumCuts = 0;
	private static String lastTechnology = null;
	private static String lastTransistor = null;
	private static String lastContact = null;
	private boolean transistorChanging = false;

	/**
	 * Method to display the dialog for building multi-finger transistor cells.
	 */
	public static void showMultiFingerTransistorDialog()
	{
		MultiFingerTransistor dialog = new MultiFingerTransistor(TopLevel.getCurrentJFrame());
		dialog.setVisible(true);
	}

	/** Creates new form MultiFingerTransistor */
	private MultiFingerTransistor(Frame parent)
	{
		super(parent, true);
		initComponents();
		getRootPane().setDefaultButton(ok);

		// make all text fields select-all when entered
		EDialog.makeTextFieldSelectAllOnTab(length);
		EDialog.makeTextFieldSelectAllOnTab(width);
		EDialog.makeTextFieldSelectAllOnTab(pitch);
		EDialog.makeTextFieldSelectAllOnTab(numFingers);
		EDialog.makeTextFieldSelectAllOnTab(numCuts);

		Technology tech = Technology.getCurrent();
		for(Iterator<Technology> it = Technology.getTechnologies(); it.hasNext(); )
		{
			Technology t = it.next();
			technologyChoice.addItem(t.getTechName());
		}
		technologyChoice.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent evt) { technologyChanged(); }
		});
		technologyChoice.setSelectedItem(tech.getTechName());
		if (lastTechnology != null) technologyChoice.setSelectedItem(lastTechnology);
		transistorChoice.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent evt) { transistorChanged(); }
		});
		if (lastTransistor != null) transistorChoice.setSelectedItem(lastTransistor);
		if (lastContact != null) contactChoice.setSelectedItem(lastContact);

		length.setText(TextUtils.formatDistance(lastLength, tech));
		width.setText(TextUtils.formatDistance(lastWidth, tech));
		pitch.setText(TextUtils.formatDistance(lastPitch, tech));
		extraPolyLength.setText(TextUtils.formatDistance(lastExtraPolyLen, tech));
		if (lastNumFingers > 0) numFingers.setText(Integer.toString(lastNumFingers));
		if (lastNumCuts > 0) numCuts.setText(Integer.toString(lastNumCuts));
		finishInitialization();
		pack();
	}

	private void technologyChanged()
	{
		String techName = (String)technologyChoice.getSelectedItem();
		Technology tech = Technology.findTechnology(techName);
		transistorChanging = true;
		transistorChoice.removeAllItems();
		for(Iterator<PrimitiveNode> it = tech.getNodes(); it.hasNext(); )
		{
			PrimitiveNode np = it.next();
			if (!np.getFunction().isTransistor()) continue;
			transistorChoice.addItem(np.getName());
		}
		transistorChanging = false;
		transistorChanged();
		pack();
	}

	private void transistorChanged()
	{
		if (transistorChanging) return;
		String techName = (String)technologyChoice.getSelectedItem();
		Technology tech = Technology.findTechnology(techName);
		String transistorName = (String)transistorChoice.getSelectedItem();
		PrimitiveNode transistorPrim = tech.findNodeProto(transistorName);
		
		if (transistorPrim == null)
		{
			System.out.println("Error in multi finger dialog: no transistor found in technology '" + tech.getTechName() + "'");
			return;
		}
		contactChoice.removeAllItems();

		// figure out the possible contacts
		ArcProto metalArc = getMetal1(tech);
		ArcProto diffArc = null;
		for(Iterator<PortProto> pIt = transistorPrim.getPorts(); pIt.hasNext(); )
		{
			PortProto pp = pIt.next();
			ArcProto[] arcs = pp.getBasePort().getConnections();
			for(int i=0; i<arcs.length; i++)
				if (arcs[i].getFunction().isDiffusion()) { diffArc = arcs[i];   break; }
		}

		for(Iterator<PrimitiveNode> it = tech.getNodes(); it.hasNext(); )
		{
			PrimitiveNode prim = it.next();
			if (!prim.getFunction().isContact()) continue;
			for(Iterator<PortProto> pIt = prim.getPorts(); pIt.hasNext(); )
			{
				PortProto pp = pIt.next();
				ArcProto[] arcs = pp.getBasePort().getConnections();
				boolean diffFound = false, metalFound = false;
				for(int i=0; i<arcs.length; i++)
				{
					if (arcs[i] == metalArc) metalFound = true;
					if (arcs[i] == diffArc) diffFound = true;
				}
				if (diffFound && metalFound)
					contactChoice.addItem(prim.getName());
			}
		}
		pack();
	}

	protected void escapePressed() { cancelActionPerformed(null); }

	private void cacheValues()
	{
		Technology tech = Technology.getCurrent();
		lastLength = TextUtils.atofDistance(length.getText(), tech);
		lastWidth = TextUtils.atofDistance(width.getText(), tech);
		lastPitch = TextUtils.atofDistance(pitch.getText(), tech);
		lastExtraPolyLen = TextUtils.atofDistance(extraPolyLength.getText(), tech);
		lastNumFingers = TextUtils.atoi(numFingers.getText());
		lastNumCuts = TextUtils.atoi(numCuts.getText());
		lastTechnology = (String)technologyChoice.getSelectedItem();
		lastTransistor = (String)transistorChoice.getSelectedItem();
		lastContact = (String)contactChoice.getSelectedItem();
	}

	private void makeTransistorCell()
	{
		cacheValues();

		// figure out what node to use
		Technology tech = Technology.findTechnology(lastTechnology);
		PrimitiveNode transistorPrim = tech.findNodeProto(lastTransistor);
		PrimitiveNode contactPrim = tech.findNodeProto(lastContact);
		if (transistorPrim == null || contactPrim == null) return;

		(new MakeTransistorCell(transistorPrim, contactPrim, lastNumFingers, lastNumCuts, lastLength, lastWidth, lastPitch, lastExtraPolyLen)).startJob();
	}

	/** 
	 * Function to look for the corresponding M1 ArcProto in the technology
	 * @param tech
	 * @return the ArcProto for Metal 1.
	 */
	private static ArcProto getMetal1(Technology tech)
	{
		for(Iterator<PrimitiveNode> it = tech.getNodes(); it.hasNext(); )
		{
			PrimitiveNode prim = it.next();
			if (!prim.getFunction().isPin()) continue;
			for(Iterator<PortProto> pIt = prim.getPorts(); pIt.hasNext(); )
			{
				PortProto pp = pIt.next();
				ArcProto[] arcs = pp.getBasePort().getConnections();
				for(int i=0; i<arcs.length; i++)
					if (arcs[i].getFunction() == ArcProto.Function.METAL1) return arcs[i];
			}
		}
		return null;
	}

	/**
	 * This class finishes the MultiFinger Transistor command by creating the cell.
	 */
	public static class MakeTransistorCell extends Job
	{
		private PrimitiveNode transistorPrim, contactPrim;
		private int numFingers, numCuts;
		private double length, width, pitch, extraPolyLen;
		private String cellName;

		public MakeTransistorCell(PrimitiveNode transistorPrim, PrimitiveNode contactPrim, int numFingers, int numCuts,
			double length, double width, double pitch, double extraPolyLen)
		{
			super("Make MultiFinger Transistor", User.getUserTool(), Job.Type.CHANGE, null, null, Job.Priority.USER);
			this.transistorPrim = transistorPrim;
			this.contactPrim = contactPrim;
			this.numFingers = numFingers;
			this.numCuts = numCuts;
			this.length = length;
			this.width = width;
			this.pitch = pitch;
			this.extraPolyLen = extraPolyLen;
		}

        @Override
		public boolean doIt() throws JobException
		{
            EditingPreferences ep = getEditingPreferences();
			// build the multi-finger transistor cell
			cellName = transistorPrim.getFunction().getShortName() + "_L" + TextUtils.formatDouble(length) + "_W" + TextUtils.formatDouble(width) +
				"_NF" + numFingers;
			if (pitch > 0) cellName += "_P" + TextUtils.formatDouble(pitch);
			if (numCuts > 0) cellName += "_NC" + numCuts;
			cellName += "{lay}";
			Cell cell = Cell.makeInstance(ep, Library.getCurrent(), cellName);
			if (cell == null) return false;
			fieldVariableChanged("cellName");

			Technology tech = transistorPrim.getTechnology();

			// determine the size of the transistor
			double transWid = transistorPrim.getDefWidth(ep);
			double transHei = transistorPrim.getDefHeight(ep);
			NodeInst ni = NodeInst.makeDummyInstance(transistorPrim, ep);
			Poly[] polys = tech.getShapeOfNode(ni);
			double polyHei = 0, diffWid = 0;
			for(int i=0; i<polys.length; i++)
			{
				Poly poly = polys[i];
				if (poly.getLayer().isDiffusionLayer())
					diffWid = poly.getBounds2D().getWidth();
				if (poly.getLayer().getFunction().isPoly())
					polyHei = poly.getBounds2D().getHeight();
			}
			if (diffWid < width) transWid += width - diffWid;
			if (polyHei < length) transHei += length - polyHei;

			// get the metal-1 arc and pin
			ArcProto metalArc = getMetal1(tech);
			PrimitiveNode metalPin = metalArc.findPinProto();

			// find the gate poly layer
			Layer gatePoly = null;
			for(Iterator<Layer> lIt = tech.getLayers(); lIt.hasNext(); )
			{
				Layer lay = lIt.next();
				if (lay.getFunction().isGatePoly()) gatePoly = lay;
			}

			// find the diffusion arc
			ArcProto diffArc = null;
			for(Iterator<PortProto> pIt = transistorPrim.getPorts(); pIt.hasNext(); )
			{
				PortProto pp = pIt.next();
				ArcProto[] arcs = pp.getBasePort().getConnections();
				for(int i=0; i<arcs.length; i++)
					if (arcs[i].getFunction().isDiffusion()) { diffArc = arcs[i];   break; }
			}

			// determine the size of the contact to use
			double contactWid = contactPrim.getDefWidth(ep);
			double contactHei = contactPrim.getDefHeight(ep);
			ni = NodeInst.makeDummyInstance(contactPrim, ep);
			polys = tech.getShapeOfNode(ni);
			diffWid = 0;
			for(int i=0; i<polys.length; i++)
			{
				Poly poly = polys[i];
				if (poly.getLayer().isDiffusionLayer()) diffWid = poly.getBounds2D().getWidth();
			}
			double diffOffset = contactWid - diffWid;
			if (diffWid < width) contactWid = width + diffOffset;

			// if a fixed number of cuts is given, adjust the size
			if (numCuts > 0)
			{
				Technology.NodeLayer nl = contactPrim.findMulticut();
				if (nl != null)
				{
					double separation = nl.getMulticutSep1D().getLambda();
					double size = nl.getMulticutSizeY().getLambda();

					NodeInst dummyContact = NodeInst.makeDummyInstance(contactPrim, ep);
					Poly[] contactPolys = tech.getShapeOfNode(dummyContact);
					double diffSize = 0, cutSize = 0;
					for(int i=0; i<contactPolys.length; i++)
					{
						Poly poly = contactPolys[i];
						if (poly.getLayer().isDiffusionLayer()) diffSize = poly.getBounds2D().getWidth();
						if (poly.getLayer().getFunction().isContact()) cutSize = poly.getBounds2D().getWidth();
					}
					double indent = diffSize - cutSize;
					double requestedDiffSize = numCuts * size + (numCuts-1) * separation + indent;
					contactWid = requestedDiffSize + diffOffset;
				}
			}

			// construct a dummy transistor and contact for DRC spacing
			NodeInst transNI = NodeInst.makeDummyInstance(transistorPrim, ep, EPoint.fromLambda(0, 0), transWid, transHei, Orientation.IDENT);
			Poly[] transPolys = tech.getShapeOfNode(transNI);
			double initialDistance = 1000;
			NodeInst contactNI = NodeInst.makeDummyInstance(contactPrim, ep, EPoint.fromLambda(0, initialDistance), contactWid, contactHei, Orientation.IDENT);
			Poly[] contactPolys = tech.getShapeOfNode(contactNI);
			double smallestExtraSpace = Double.MAX_VALUE;
			for(int t=0; t<transPolys.length; t++)
			{
				for(int c=0; c<contactPolys.length; c++)
				{
					if (transPolys[t].getLayer() == contactPolys[c].getLayer()) continue;
					double drcLen = length;
					double drcWid = width;
					Layer transLayer = transPolys[t].getLayer();
					Layer contactLayer = contactPolys[c].getLayer();
					DRCTemplate rule = DRC.getSpacingRule(transLayer, null, contactLayer, null, true, -1, drcWid, drcLen);
					if (rule == null && gatePoly != null && transLayer != gatePoly && transLayer.getFunction().isPoly())
						rule = DRC.getSpacingRule(gatePoly, null, contactLayer, null, true, -1, drcWid, drcLen);
					double dist = -1;
					if (rule != null) dist = rule.getValue(0);
					if (dist < 0) continue;
					double extraSpace = contactPolys[c].getBounds2D().getMinY() - transPolys[t].getBounds2D().getMaxY() - dist;
					if (extraSpace < smallestExtraSpace) smallestExtraSpace = extraSpace;
				}
			}
			double bestPitch = initialDistance - smallestExtraSpace;
			if (pitch > bestPitch) bestPitch = pitch;
			bestPitch *= 2;

			// make the transistors
			NodeInst [] transistors = new NodeInst[numFingers];
			for(int f=0; f<numFingers; f++)
			{
				EPoint center = EPoint.fromLambda(0, f*bestPitch);
				transistors[f] = NodeInst.makeInstance(transistorPrim, ep, center, transWid, transHei, cell, Orientation.IDENT, null);

				// export the polysilicon connections
				int index = 0;
				for(Iterator<PortInst> it = transistors[f].getPortInsts(); it.hasNext(); )
				{
					PortInst pi = it.next();
					ArcProto[] arcs = pi.getPortProto().getBasePort().getConnections();
					ArcProto polyArc = null;
					for(int i=0; i<arcs.length; i++)
					{
						if (arcs[i].getFunction().isPoly()) { polyArc = arcs[i];   break; }
					}
					if (polyArc != null)
					{
						if (extraPolyLen > 0)
						{
							EPoint ctr = pi.getCenter();
							if (ctr.getX() < 0)
							{
								// on left: extend more left
								EPoint pinCtr = EPoint.fromLambda(ctr.getX() - extraPolyLen, ctr.getY());
								PrimitiveNode polyPin = polyArc.findPinProto();
								NodeInst niPin = NodeInst.makeInstance(polyPin, ep, pinCtr, polyPin.getDefWidth(ep), polyPin.getDefHeight(ep), cell);
								PortInst niPi = niPin.getOnlyPortInst();
								ArcInst ai = ArcInst.makeInstanceBase(polyArc, ep, length, pi, niPi);
								ai.setTailExtended(false);
								pi = niPi;
							} else
							{
								// on right: extend more right
								EPoint pinCtr = EPoint.fromLambda(ctr.getX() + extraPolyLen, ctr.getY());
								PrimitiveNode polyPin = polyArc.findPinProto();
								NodeInst niPin = NodeInst.makeInstance(polyPin, ep, pinCtr, polyPin.getDefWidth(ep), polyPin.getDefHeight(ep), cell);
								PortInst niPi = niPin.getOnlyPortInst();
								ArcInst ai = ArcInst.makeInstanceBase(polyArc, ep, length, pi, niPi);
								ai.setTailExtended(false);
								pi = niPi;
							}
						}
						char suffix = (char)('a' + index++);
						String exportName = "F" + (f+1) + suffix;
						Export.newInstance(cell, pi, exportName, ep);
					}
				}
			}

			// make the contacts
			NodeInst [] contacts = new NodeInst[numFingers+1];
			for(int f=0; f<=numFingers; f++)
			{
				EPoint center = EPoint.fromLambda(0, f*bestPitch-bestPitch/2);
				contacts[f] = NodeInst.makeInstance(contactPrim, ep, center, contactWid, contactHei, cell, Orientation.IDENT, null);

				// export the pin connections
				NodeInst pin = NodeInst.makeInstance(metalPin, ep, center, metalPin.getDefWidth(ep), metalPin.getDefHeight(ep), cell);
				ArcInst.makeInstance(metalArc,ep,  contacts[f].getOnlyPortInst(), pin.getOnlyPortInst());
				String exportName = "Act_" + (f+1);
				Export.newInstance(cell, pin.getOnlyPortInst(), exportName, ep);
			}

			// connect the contacts to the transistors
			for(int f=0; f<numFingers; f++)
			{
				PortInst p1 = null, p2 = null;
				for(Iterator<PortInst> it = transistors[f].getPortInsts(); it.hasNext(); )
				{
					PortInst pi = it.next();
					ArcProto[] arcs = pi.getPortProto().getBasePort().getConnections();
					for(int i=0; i<arcs.length; i++)
					{
						if (arcs[i].getFunction().isDiffusion())
						{
							if (p1 == null) p1 = pi; else
								p2 = pi;
						}
					}
				}

				if (p1.getCenter().getY() > p2.getCenter().getY())
				{
					PortInst swapPi = p1;   p1 = p2;   p2 = swapPi;
				}
				ArcInst.makeInstance(diffArc, ep, contacts[f].getOnlyPortInst(), p1);
				ArcInst.makeInstance(diffArc, ep, p2, contacts[f+1].getOnlyPortInst());
			}
			return true;
		}

        @Override
		public void terminateOK()
		{
			Cell cell = Library.getCurrent().findNodeProto(cellName);
			if (cell != null)
			{
				WindowFrame.createEditWindow(cell);
			}
		}
	}

	/** This method is called from within the constructor to
	 * initialize the form.
	 * WARNING: Do NOT modify this code. The content of this method is
	 * always regenerated by the Form Editor.
	 */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        cancel = new javax.swing.JButton();
        ok = new javax.swing.JButton();
        jLabel2 = new javax.swing.JLabel();
        jLabel3 = new javax.swing.JLabel();
        jLabel4 = new javax.swing.JLabel();
        jLabel5 = new javax.swing.JLabel();
        length = new javax.swing.JTextField();
        width = new javax.swing.JTextField();
        numFingers = new javax.swing.JTextField();
        numCuts = new javax.swing.JTextField();
        jLabel6 = new javax.swing.JLabel();
        transistorChoice = new javax.swing.JComboBox();
        jLabel7 = new javax.swing.JLabel();
        pitch = new javax.swing.JTextField();
        jLabel8 = new javax.swing.JLabel();
        contactChoice = new javax.swing.JComboBox();
        jLabel9 = new javax.swing.JLabel();
        technologyChoice = new javax.swing.JComboBox();
        jLabel10 = new javax.swing.JLabel();
        extraPolyLength = new javax.swing.JTextField();

        setTitle("Multi-Finger Transistors");
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                closeDialog(evt);
            }
        });
        getContentPane().setLayout(new java.awt.GridBagLayout());

        cancel.setText("Cancel");
        cancel.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cancelActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 9;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(cancel, gridBagConstraints);

        ok.setText("OK");
        ok.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                okActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 9;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(ok, gridBagConstraints);

        jLabel2.setText("Length:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(jLabel2, gridBagConstraints);

        jLabel3.setText("Width:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(jLabel3, gridBagConstraints);

        jLabel4.setText("Number of fingers:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(jLabel4, gridBagConstraints);

        jLabel5.setText("Extra Poly length:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 8;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(jLabel5, gridBagConstraints);

        length.setColumns(6);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        getContentPane().add(length, gridBagConstraints);

        width.setColumns(6);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        getContentPane().add(width, gridBagConstraints);

        numFingers.setColumns(6);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        getContentPane().add(numFingers, gridBagConstraints);

        numCuts.setColumns(6);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        getContentPane().add(numCuts, gridBagConstraints);

        jLabel6.setText("Transistor:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(jLabel6, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        getContentPane().add(transistorChoice, gridBagConstraints);

        jLabel7.setText("Pitch:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(jLabel7, gridBagConstraints);

        pitch.setColumns(6);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        getContentPane().add(pitch, gridBagConstraints);

        jLabel8.setText("Contact:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(jLabel8, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        getContentPane().add(contactChoice, gridBagConstraints);

        jLabel9.setText("Technology:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(jLabel9, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        getContentPane().add(technologyChoice, gridBagConstraints);

        jLabel10.setText("Number of cuts:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(jLabel10, gridBagConstraints);

        extraPolyLength.setColumns(6);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 8;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        getContentPane().add(extraPolyLength, gridBagConstraints);

        pack();
    }// </editor-fold>//GEN-END:initComponents

	private void okActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_okActionPerformed
	{//GEN-HEADEREND:event_okActionPerformed
		makeTransistorCell();
		closeDialog(null);
	}//GEN-LAST:event_okActionPerformed

	private void cancelActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_cancelActionPerformed
	{//GEN-HEADEREND:event_cancelActionPerformed
		cacheValues();
		closeDialog(null);
	}//GEN-LAST:event_cancelActionPerformed

    /** Closes the dialog */
    private void closeDialog(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_closeDialog
		setVisible(false);
		dispose();
    }//GEN-LAST:event_closeDialog

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton cancel;
    private javax.swing.JComboBox contactChoice;
    private javax.swing.JTextField extraPolyLength;
    private javax.swing.JLabel jLabel10;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JTextField length;
    private javax.swing.JTextField numCuts;
    private javax.swing.JTextField numFingers;
    private javax.swing.JButton ok;
    private javax.swing.JTextField pitch;
    private javax.swing.JComboBox technologyChoice;
    private javax.swing.JComboBox transistorChoice;
    private javax.swing.JTextField width;
    // End of variables declaration//GEN-END:variables
}
