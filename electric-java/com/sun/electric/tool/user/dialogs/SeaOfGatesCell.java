/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: SeaOfGatesCell.java
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
import com.sun.electric.database.change.DatabaseChangeEvent;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.network.Netlist;
import com.sun.electric.database.network.Network;
import com.sun.electric.database.text.TextUtils;
import com.sun.electric.technology.ArcProto;
import com.sun.electric.technology.Layer;
import com.sun.electric.technology.Technology;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.JobException;
import com.sun.electric.tool.io.FileType;
import com.sun.electric.tool.routing.SeaOfGates.SeaOfGatesArcProperties;
import com.sun.electric.tool.routing.SeaOfGates.SeaOfGatesCellParameters;
import com.sun.electric.tool.routing.SeaOfGates.SeaOfGatesExtraBlockage;
import com.sun.electric.tool.routing.SeaOfGates.SeaOfGatesTrack;
import com.sun.electric.tool.user.Highlighter;
import com.sun.electric.tool.user.Resources;
import com.sun.electric.tool.user.User;
import com.sun.electric.tool.user.ui.EditWindow;
import com.sun.electric.tool.user.ui.ToolBar;
import com.sun.electric.tool.user.ui.TopLevel;
import com.sun.electric.tool.user.ui.WindowFrame;
import com.sun.electric.util.math.EDimension;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

/**
 * Class to handle the "Sea-of-Gates Cell Preferences" dialog.
 */
public class SeaOfGatesCell extends EDialog
{
	private SeaOfGatesCellParameters sogp;
	private JList arcsList, gridPointsList, netsList, layersList, blockageList;
	private DefaultListModel arcsModel, gridPointsModel, netsModel, layersModel, blockageModel;
	private Technology curTech;
	private static SeaOfGatesCell theDialog = null;
	private boolean fixedValuesChanging = false, defaultOverridesChanging = false, perLayerOverridesChanging = false,
		removeLayerChanging = false, initializing;

	public static void showSeaOfGatesCellDialog()
	{
		EditWindow wnd = EditWindow.needCurrent();
		Cell cell = wnd.getCell();
		if (cell == null)
		{
			System.out.println("No cell selected for SoG Cell Properties Dialog");
			return;
		}

		if (theDialog == null)
		{
			JFrame jf = null;
			if (TopLevel.isMDIMode())
				jf = TopLevel.getCurrentJFrame();
			theDialog = new SeaOfGatesCell(jf);
		}

		if (cell != null)
		{
			theDialog.jLabel1.setText("Sea-of-Gates Properties for Cell " + cell.describe(false));
			theDialog.sogp = new SeaOfGatesCellParameters(cell);
		}

		// Rebuild technology list
		theDialog.initializing = true;
		theDialog.sogTechList.removeAllItems();
		for(Iterator<Technology> tIt = Technology.getTechnologies(); tIt.hasNext(); )
		{
			Technology tech = tIt.next();
			theDialog.sogTechList.addItem(tech.getTechName());
		}
		theDialog.loadDialog(cell.getTechnology());
		theDialog.setVisible(true);
		theDialog.pack();
		theDialog.initializing = false;
	}

	/** Creates new form Sea-of-Gates Cell Preferences */
	private SeaOfGatesCell(Frame parent)
	{
		super(parent, false);
		initComponents();

		// cannot export yet
		sogExportData.setEnabled(false);

		// initialize the lists
		arcsModel = new DefaultListModel();
		arcsList = new JList(arcsModel);
		arcsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		arcsList.getSelectionModel().addListSelectionListener(new ListSelectionListener () {
            public void valueChanged(ListSelectionEvent evt) { arcClicked(); }
		});
		sogArcList.setViewportView(arcsList);

		netsModel = new DefaultListModel();
		netsList = new JList(netsModel);
		netsList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		netsList.getSelectionModel().addListSelectionListener(new ListSelectionListener () {
            public void valueChanged(ListSelectionEvent evt) { redrawArcOverridesList(); }
		});
		sogNetList.setViewportView(netsList);

		layersModel = new DefaultListModel();
		layersList = new JList(layersModel);
		layersList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		layersList.getSelectionModel().addListSelectionListener(new ListSelectionListener () {
            public void valueChanged(ListSelectionEvent evt) { loadWidthSpacingOverrides(); }
		});
		sogLayerList.setViewportView(layersList);

		blockageModel = new DefaultListModel();
		blockageList = new JList(blockageModel);
		blockageList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		sogBlockageList.setViewportView(blockageList);

		sogGridAllUp.setBorderPainted(false);
        sogGridAllDown.setBorderPainted(false);
        sogGridIndUp.setBorderPainted(false);
        sogGridIndDown.setBorderPainted(false);
        sogGridSpacingUp.setBorderPainted(false);
        sogGridSpacingDown.setBorderPainted(false);
        sogGridOffsetUp.setBorderPainted(false);
        sogGridOffsetDown.setBorderPainted(false);

		sogHorVerUsage.addItem("Favor");
		sogHorVerUsage.addItem("Force");
		sogHorVerUsage.addItem("Ignore");

        gridPointsModel = new DefaultListModel();
		gridPointsList = new JList(gridPointsModel);
		gridPointsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		gridPointsList.addListSelectionListener(new ListSelectionListener()
		{
			public void valueChanged(ListSelectionEvent evt) { gridPointClicked(); }
		});
		sogGridIndScroll.setViewportView(gridPointsList);

		sogRemoveLayer.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent evt) { cutLayerChanged(); }
		});
		sogRoutingBoundsLayer.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent evt) { routingBoundsLayerNameChanged(); }
		});
		sogTechList.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent evt) { techChanged(); }
		});
		sogAddNet.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent evt) { addNetworkName(); }
		});
		sogRemoveNet.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent evt) { removeNetworkName(); }
		});
		sogAddLayerToNet.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent evt) { addLayerToNet(); }
		});
		sogRemoveLayerFromNet.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent evt) { removeLayerFromNet(); }
		});
		sogDrawGrid.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent evt) { drawGrid(); }
		});
		sogHorVerUsage.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent evt) { horVerChanged(); }
		});
		sogVerOddHorEven.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent evt) { horVerChanged(); }
		});
		sogHorOddVerEven.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent evt) { horVerChanged(); }
		});
		sogGridNone.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent evt) { gridTypeChanged(); }
		});
		sogGridFixed.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent evt) { gridTypeChanged(); }
		});
		sogGridArbitrary.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent evt) { gridTypeChanged(); }
		});
		sogFavorLayer.addItemListener(new ItemListener() {
			public void itemStateChanged(ItemEvent evt) { favorClicked(); }
		});
		sogAvoidLayer.addItemListener(new ItemListener() {
			public void itemStateChanged(ItemEvent evt) { preventClicked(); }
		});
		sogTaperOnlyLayer.addItemListener(new ItemListener() {
			public void itemStateChanged(ItemEvent evt) { taperOnlyClicked(); }
		});
		sogForceGrid.addItemListener(new ItemListener() {
			public void itemStateChanged(ItemEvent evt) { forceGridClicked(); }
		});

		ContactDocumentListener contactDocumentListener = new ContactDocumentListener();
		sogContact1XInclusionPat.getDocument().addDocumentListener(contactDocumentListener);
		sogContact2XInclusionPat.getDocument().addDocumentListener(contactDocumentListener);
		sogContactExclusionPat.getDocument().addDocumentListener(contactDocumentListener);

		MainDocumentListener mainDocumentListener = new MainDocumentListener();
		sogDefWidth.getDocument().addDocumentListener(mainDocumentListener);
		sogDefXSpacing.getDocument().addDocumentListener(mainDocumentListener);
		sog2XWidth.getDocument().addDocumentListener(mainDocumentListener);
		sogTaperLength.getDocument().addDocumentListener(mainDocumentListener);

		OverrideDocumentListener overrideDocumentListener = new OverrideDocumentListener();
		sogLayerWidthOverride.getDocument().addDocumentListener(overrideDocumentListener);
		sogLayerSpacingOverride.getDocument().addDocumentListener(overrideDocumentListener);

		sogNoSteinerTrees.addItemListener(new ItemListener() {
			public void itemStateChanged(ItemEvent evt) { steinerTreesChanged(); }
		});
		sogContactDownToAvoidedLayers.addItemListener(new ItemListener() {
			public void itemStateChanged(ItemEvent evt) { contactOnAvoidedLayersChanged(); }
		});
		sogContactUpToAvoidedLayers.addItemListener(new ItemListener() {
			public void itemStateChanged(ItemEvent evt) { contactOnAvoidedLayersChanged(); }
		});
		sogNoRotatedContacts.addItemListener(new ItemListener() {
			public void itemStateChanged(ItemEvent evt) { rotatedContactsChanged(); }
		});
		sogShowGrid.addItemListener(new ItemListener() {
			public void itemStateChanged(ItemEvent evt) { updateGridDisplay(true); }
		});
		sogGridIndUp.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent evt) { moveGridLines(1, false); }
		});
		sogGridIndDown.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent evt) { moveGridLines(-1, false); }
		});
		sogGridAllUp.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent evt) { moveGridLines(1, true); }
		});
		sogGridAllDown.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent evt) { moveGridLines(-1, true); }
		});
		sogGridSpacingUp.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent evt) { shiftGridSpacing(1); }
		});
		sogGridSpacingDown.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent evt) { shiftGridSpacing(-1); }
		});
		sogGridOffsetUp.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent evt) { shiftGridOffset(1); }
		});
		sogGridOffsetDown.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent evt) { shiftGridOffset(-1); }
		});
		sogGridIndDelete.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent evt) { deleteGridLine(); }
		});
		sogGridIndNew.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent evt) { addGridLine(); }
		});
		sogBlockageShow.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent evt) { showBlockages(); }
		});
		sogBlockageNew.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent evt) { addBlockage(); }
		});
		sogBlockageDelete.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent evt) { delBlockage(); }
		});
		sogBlockageEdit.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent evt) { editBlockage(); }
		});
		sogFixedSpacing.getDocument().addDocumentListener(new FixedGridDocumentListener());
		sogFixedOffset.getDocument().addDocumentListener(new FixedGridDocumentListener());
	}

	/**
	 * Class to handle changes to the contact usage patterns.
	 */
	private class ContactDocumentListener implements DocumentListener
	{
		public void changedUpdate(DocumentEvent e) { contactPatternChanged(); }
		public void insertUpdate(DocumentEvent e) { contactPatternChanged(); }
		public void removeUpdate(DocumentEvent e) { contactPatternChanged(); }
	}

	/**
	 * Class to handle changes to the default layer width or spacing.
	 */
	private class MainDocumentListener implements DocumentListener
	{
		public void changedUpdate(DocumentEvent e) { defaultOverridesChanged(); }
		public void insertUpdate(DocumentEvent e) { defaultOverridesChanged(); }
		public void removeUpdate(DocumentEvent e) { defaultOverridesChanged(); }
	}

	/**
	 * Class to handle changes to the per-layer width or spacing overrides.
	 */
	private class OverrideDocumentListener implements DocumentListener
	{
		public void changedUpdate(DocumentEvent e) { layerOverridesChanged(); }
		public void insertUpdate(DocumentEvent e) { layerOverridesChanged(); }
		public void removeUpdate(DocumentEvent e) { layerOverridesChanged(); }
	}

	private void loadDialog(Technology tech)
	{
		initializing = true;

		// see if steiner trees are already done
		sogNoSteinerTrees.setSelected(sogp.isSteinerDone());

		// see if contacts are allowed on avoided layers
		sogContactDownToAvoidedLayers.setSelected(sogp.isContactAllowedDownToAvoidedLayer());
		sogContactUpToAvoidedLayers.setSelected(sogp.isContactAllowedUpToAvoidedLayer());

		// set current technology
		curTech = tech;
		sogTechList.setSelectedItem(curTech.getTechName());

		// set the routing bounds layer name
		removeLayerChanging = true;
		String boundsLayerName = sogp.getRoutingBoundsLayerName();
		if (boundsLayerName != null) sogRoutingBoundsLayer.setSelectedItem(boundsLayerName);
		removeLayerChanging = false;

		// setup layer preferences
		if (sogp.isHorizontalEven()) sogVerOddHorEven.setSelected(true); else
			sogHorOddVerEven.setSelected(true);
		if (sogp.isForceHorVer()) sogHorVerUsage.setSelectedIndex(1); else
			if (sogp.isFavorHorVer()) sogHorVerUsage.setSelectedIndex(0); else
				sogHorVerUsage.setSelectedIndex(2);

		// setup contact control
		sogContact1XInclusionPat.setText(sogp.getAcceptOnly1XPrimitives());
		sogContact2XInclusionPat.setText(sogp.getAcceptOnly2XPrimitives());
		sogContactExclusionPat.setText(sogp.getIgnorePrimitives());
		sogNoRotatedContacts.setSelected(!sogp.isContactsRotate());

		// load networks to route
		loadNets();
		loadWidthSpacingOverrides();
		loadBlockages();
		initializing = false;
	}

	private void loadNets()
	{
		netsModel.clear();
		List<String> nets = sogp.getNetsToRoute();
		for(String netName : nets)
			netsModel.addElement(netName);
		if (nets.size() > 0) netsList.setSelectedIndex(0); else
			netsList.setSelectedIndex(-1);
	}

	private void loadBlockages()
	{
		blockageModel.clear();
		List<SeaOfGatesExtraBlockage> list = sogp.getBlockages();
		for(SeaOfGatesExtraBlockage sogeb : list)
		{
			blockageModel.addElement(sogeb.getLayer().describe() + " " + TextUtils.formatDistance(sogeb.getLX()) +
				"<=X<=" + TextUtils.formatDistance(sogeb.getHX()) + " " + TextUtils.formatDistance(sogeb.getLY()) +
				"<=Y<=" + TextUtils.formatDistance(sogeb.getHY()));
		}
	}

	private void showBlockages()
	{
		EditWindow wnd = EditWindow.getCurrent();
		if (wnd == null) return;
		Cell cell = wnd.getCell();
		Highlighter h = wnd.getRulerHighlighter();
		h.clear();
		if (sogBlockageShow.isSelected())
		{
			List<SeaOfGatesExtraBlockage> list = sogp.getBlockages();
			for(SeaOfGatesExtraBlockage sogeb : list)
			{
				Point2D p1 = new Point2D.Double(sogeb.getLX(), sogeb.getLY());
				Point2D p2 = new Point2D.Double(sogeb.getLX(), sogeb.getHY());
				Point2D p3 = new Point2D.Double(sogeb.getHX(), sogeb.getHY());
				Point2D p4 = new Point2D.Double(sogeb.getHX(), sogeb.getLY());
				Color col = sogeb.getLayer().getArcLayers()[0].getLayer().getGraphics().getColor();
				h.addLine(p1, p2, cell, true, col, false);
				h.addLine(p2, p3, cell, true, col, false);
				h.addLine(p3, p4, cell, true, col, false);
				h.addLine(p4, p1, cell, true, col, false);
			}
		}
		h.finished();
		wnd.repaint();
	}

	private void addBlockage()
	{
		new BlockageDialog(null);
	}

	private void delBlockage()
	{
		int[] indices = blockageList.getSelectedIndices();
		List<Integer> sortedList = new ArrayList<Integer>();
		for(int i=0; i<indices.length; i++) sortedList.add(Integer.valueOf(indices[i]));
		Collections.sort(sortedList);
		List<SeaOfGatesExtraBlockage> list = sogp.getBlockages();
		for(int i=sortedList.size()-1; i>=0; i--)
			list.remove(sortedList.get(i).intValue());
		sogp.setBlockages(list);
		loadBlockages();
		if (sogBlockageShow.isSelected()) showBlockages();
		updateSOGCellData();
	}

	private void editBlockage()
	{
		int[] indices = blockageList.getSelectedIndices();
		if (indices.length != 1) return;
		List<SeaOfGatesExtraBlockage> list = sogp.getBlockages();
		new BlockageDialog(list.get(indices[0]));
	}

	private class BlockageDialog extends EModelessDialog
	{
		private JComboBox layerSelection;
		private JTextField LX, HX, LY, HY;
		private SeaOfGatesExtraBlockage edit;

		/** Creates new form Debug-Routing */
		public BlockageDialog(SeaOfGatesExtraBlockage e)
		{
			super(TopLevel.getCurrentJFrame());
			edit = e;
			getContentPane().setLayout(new GridBagLayout());
			setTitle(edit != null ? "Edit Blockage" : "New Blockage");
			setName("");
			addWindowListener(new WindowAdapter() { public void windowClosing(WindowEvent evt) { doneDialog(); } });
			GridBagConstraints gbc;

			JLabel labLayer = new JLabel("Layer:");
			gbc = new GridBagConstraints();
			gbc.gridx = 0;   gbc.gridy = 0;
			gbc.insets = new Insets(4, 4, 4, 4);
			getContentPane().add(labLayer, gbc);

			layerSelection = new JComboBox();
			gbc = new GridBagConstraints();
			gbc.gridx = 1;   gbc.gridy = 0;
			gbc.weightx = 0.1;
			gbc.anchor = GridBagConstraints.EAST;
			gbc.insets = new Insets(4, 4, 4, 4);
			getContentPane().add(layerSelection, gbc);
			for(Iterator<ArcProto> it = theDialog.curTech.getArcs(); it.hasNext(); )
			{
				ArcProto ap = it.next();
				if (!ap.getFunction().isMetal()) continue;
				layerSelection.addItem(ap.getName());
			}
			if (edit != null) layerSelection.setSelectedItem(edit.getLayer().getName());

			JLabel labLX = new JLabel("Low X:");
			gbc = new GridBagConstraints();
			gbc.gridx = 0;   gbc.gridy = 1;
			gbc.insets = new Insets(4, 4, 4, 4);
			getContentPane().add(labLX, gbc);

			LX = new JTextField(edit != null ? TextUtils.formatDistance(edit.getLX()) : "");
			LX.setColumns(10);
			gbc = new GridBagConstraints();
			gbc.gridx = 1;   gbc.gridy = 1;
			gbc.weightx = 0.5;
			gbc.fill = GridBagConstraints.HORIZONTAL;
			gbc.insets = new Insets(4, 4, 4, 4);
			getContentPane().add(LX, gbc);

			JLabel labHX = new JLabel("High X:");
			gbc = new GridBagConstraints();
			gbc.gridx = 2;   gbc.gridy = 1;
			gbc.insets = new Insets(4, 4, 4, 4);
			getContentPane().add(labHX, gbc);

			HX = new JTextField(edit != null ? TextUtils.formatDistance(edit.getHX()) : "");
			HX.setColumns(10);
			gbc = new GridBagConstraints();
			gbc.gridx = 3;   gbc.gridy = 1;
			gbc.weightx = 0.5;
			gbc.fill = GridBagConstraints.HORIZONTAL;
			gbc.insets = new Insets(4, 4, 4, 4);
			getContentPane().add(HX, gbc);

			JLabel labLY = new JLabel("Low X:");
			gbc = new GridBagConstraints();
			gbc.gridx = 0;   gbc.gridy = 2;
			gbc.insets = new Insets(4, 4, 4, 4);
			getContentPane().add(labLY, gbc);

			LY = new JTextField(edit != null ? TextUtils.formatDistance(edit.getLY()) : "");
			LY.setColumns(10);
			gbc = new GridBagConstraints();
			gbc.gridx = 1;   gbc.gridy = 2;
			gbc.weightx = 0.5;
			gbc.fill = GridBagConstraints.HORIZONTAL;
			gbc.insets = new Insets(4, 4, 4, 4);
			getContentPane().add(LY, gbc);

			JLabel labHY = new JLabel("High X:");
			gbc = new GridBagConstraints();
			gbc.gridx = 2;   gbc.gridy = 2;
			gbc.insets = new Insets(4, 4, 4, 4);
			getContentPane().add(labHY, gbc);

			HY = new JTextField(edit != null ? TextUtils.formatDistance(edit.getHY()) : "");
			HY.setColumns(10);
			gbc = new GridBagConstraints();
			gbc.gridx = 3;   gbc.gridy = 2;
			gbc.weightx = 0.5;
			gbc.fill = GridBagConstraints.HORIZONTAL;
			gbc.insets = new Insets(4, 4, 4, 4);
			getContentPane().add(HY, gbc);

			JButton cancelButton = new JButton("Cancel");
			cancelButton.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent evt) { doneDialog(); } });
			gbc = new GridBagConstraints();
			gbc.gridx = 0;   gbc.gridy = 3;
			gbc.gridwidth = 2;
			gbc.insets = new Insets(4, 4, 4, 4);
			getContentPane().add(cancelButton, gbc);

			JButton okButton = new JButton("OK");
			okButton.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent evt) { ok(); } });
			gbc = new GridBagConstraints();
			gbc.gridx = 2;   gbc.gridy = 3;
			gbc.gridwidth = 2;
			gbc.insets = new Insets(4, 4, 4, 4);
			getContentPane().add(okButton, gbc);

			pack();
			finishInitialization();
			setVisible(true);
		}

		/**
		 * Method to close the blockage editing dialog.
		 */
		private void doneDialog()
		{
			closeDialog();
			dispose();
		}

		/**
		 * Method to complete the blockage editing dialog.
		 */
		private void ok()
		{
			// make new blockage object
			double lX = TextUtils.atofDistance(LX.getText());
			double hX = TextUtils.atofDistance(HX.getText());
			double lY = TextUtils.atofDistance(LY.getText());
			double hY = TextUtils.atofDistance(HY.getText());
			ArcProto met = theDialog.curTech.findArcProto((String)layerSelection.getSelectedItem());
			SeaOfGatesExtraBlockage sogeb = new SeaOfGatesExtraBlockage(lX, lY, hX, hY, met);
			List<SeaOfGatesExtraBlockage> list = sogp.getBlockages();
			if (edit != null) list.remove(edit);
			list.add(sogeb);
			sogp.setBlockages(list);
			loadBlockages();
			if (sogBlockageShow.isSelected()) showBlockages();
			updateSOGCellData();

			doneDialog();
		}
	}

	private ArcProto getCurrentlySelectedArc()
	{
		String str = (String)arcsList.getSelectedValue();
		if (str == null) return null;
		int pos = str.indexOf(' ');
		if (pos >= 0) str = str.substring(0, pos);
		ArcProto ap = curTech.findArcProto(str);
		return ap;
	}

	private void steinerTreesChanged()
	{
		if (sogp.isSteinerDone() == sogNoSteinerTrees.isSelected()) return;
		sogp.setSteinerDone(sogNoSteinerTrees.isSelected());
		updateSOGCellData();
	}

	private void contactOnAvoidedLayersChanged()
	{
		if (sogp.isContactAllowedDownToAvoidedLayer() == sogContactDownToAvoidedLayers.isSelected() ||
			sogp.isContactAllowedUpToAvoidedLayer() == sogContactUpToAvoidedLayers.isSelected()) return;
		sogp.setContactAllowedDownToAvoidedLayer(sogContactDownToAvoidedLayers.isSelected());
		sogp.setContactAllowedUpToAvoidedLayer(sogContactUpToAvoidedLayers.isSelected());
		updateSOGCellData();
	}
	
	private void rotatedContactsChanged()
	{
		if (sogp.isContactsRotate() == !sogNoRotatedContacts.isSelected()) return;
		sogp.setContactsRotate(!sogNoRotatedContacts.isSelected());
		updateSOGCellData();
	}

	private void horVerChanged()
	{
		switch (sogHorVerUsage.getSelectedIndex())
		{
			case 0:		// favor horizontal/vertical usage
				sogp.setForceHorVer(false);
				sogp.setFavorHorVer(true);
				break;
			case 1:		// force horizontal/vertical usage
				sogp.setForceHorVer(true);
				sogp.setFavorHorVer(true);
				break;
			case 2:		// ignore horizontal/vertical usage
				sogp.setForceHorVer(false);
				sogp.setFavorHorVer(false);
				break;
		}
		sogp.setHorizontalEven(sogVerOddHorEven.isSelected());
		updateGridDisplay(false);
		arcClicked();
		updateSOGCellData();
	}

	private void favorClicked()
	{
		ArcProto ap = getCurrentlySelectedArc();
		sogp.setFavored(ap, sogFavorLayer.isSelected());
		setArcLine(ap, makeArcLine(ap));
		updateSOGCellData();
	}

	private void preventClicked()
	{
		ArcProto ap = getCurrentlySelectedArc();
		sogp.setPrevented(ap, sogAvoidLayer.isSelected());
		setArcLine(ap, makeArcLine(ap));
		updateSOGCellData();
	}

	private void taperOnlyClicked()
	{
		ArcProto ap = getCurrentlySelectedArc();
		boolean isTaperOnly = sogTaperOnlyLayer.isSelected();
		sogDefWidth.setEnabled(!isTaperOnly);
		sog2XWidth.setEnabled(!isTaperOnly);
		sogp.setTaperOnly(ap, isTaperOnly);
		setArcLine(ap, makeArcLine(ap));
		updateSOGCellData();
	}

	private void forceGridClicked()
	{
		ArcProto ap = getCurrentlySelectedArc();
		sogp.setGridForced(ap, sogForceGrid.isSelected());
		updateSOGCellData();
	}

	private void contactPatternChanged()
	{
		if (initializing) return;
		sogp.setAcceptOnly1XPrimitives(sogContact1XInclusionPat.getText().trim());
		sogp.setAcceptOnly2XPrimitives(sogContact2XInclusionPat.getText().trim());
		sogp.setIgnorePrimitive(sogContactExclusionPat.getText().trim());
		updateSOGCellData();
	}

	/**
	 * Method called when the user changes any of the default layer width or spacing fields.
	 */
	private void defaultOverridesChanged()
	{
		if (defaultOverridesChanging) return;
		ArcProto ap = getCurrentlySelectedArc();

		// set new default width
		String val = sogDefWidth.getText().trim();
		Double w = null;
		if (val.length() > 0) w = new Double(TextUtils.atofDistance(val));
		sogp.setDefaultWidthOverride(ap, w);

		// set new default spacing
		// X axis
		val = sogDefXSpacing.getText().trim();
		Double s = null;
		if (val.length() > 0) s = new Double(TextUtils.atofDistance(val));
		sogp.setDefaultSpacingOverride(ap, s, 0);
		
		// Y axis
		val = sogDefYSpacing.getText().trim();
		if (val.length() > 0) s = new Double(TextUtils.atofDistance(val));
		sogp.setDefaultSpacingOverride(ap, s, 1);

		// set new 2X width threshold
		val = sog2XWidth.getText().trim();
		w = null;
		if (val.length() > 0) w = new Double(TextUtils.atofDistance(val));
		sogp.set2XWidth(ap, w);

		// set new taper length
		val = sogTaperLength.getText().trim();
		Double t = null;
		if (val.length() > 0) t = new Double(TextUtils.atofDistance(val));
		sogp.setTaperLength(ap, t);

		setArcLine(ap, makeArcLine(ap));
		updateSOGCellData();
	}

	/**
	 * Method called when the user changes any of the per-network layer width / spacing overrides.
	 */
	private void layerOverridesChanged()
	{
		if (perLayerOverridesChanging) return;
		if (sogp == null) return;
		List<Object> netNames = netsList.getSelectedValuesList();
		if (netNames == null || netNames.size() != 1) return;
		String netName = (String)netNames.get(0);
		if (netName == null || netName.length() == 0) return;
		List<Object> arcNames = layersList.getSelectedValuesList();
		if (arcNames == null || arcNames.size() != 1) return;
		String arcName = (String)arcNames.get(0);
		if (arcName == null) return;
		int spacePos = arcName.indexOf(' ');
		if (spacePos >= 0) arcName = arcName.substring(0, spacePos);
		ArcProto ap = curTech.findArcProto(arcName);

		// set new default width
		String val = sogLayerWidthOverride.getText().trim();
		Double w = null;
		if (val.length() > 0)
			w = new Double(TextUtils.atofDistance(val));
		sogp.setWidthOverrideForArcOnNet(netName, ap, w);

		// set new default spacing
		val = sogLayerSpacingOverride.getText().trim();
		Double s = null;
		if (val.length() > 0) s = new Double(TextUtils.atofDistance(val));
		sogp.setSpacingOverrideForArcOnNet(netName, ap, s);

		setArcOverrideLine(ap, makeArcOnNetworkLine(netName, ap));
		updateSOGCellData();
	}

	private void setArcOverrideLine(ArcProto ap, String line)
	{
		for(int i=0; i<layersModel.getSize(); i++)
		{
			String listLine = (String)layersModel.get(i);
			int pos = listLine.indexOf(' ');
			if (pos >= 0) listLine = listLine.substring(0, pos);
			ArcProto listAP = curTech.findArcProto(listLine);
			if (listAP != ap) continue;
			layersModel.set(i, line);
			break;
		}
	}

	private void setArcLine(ArcProto ap, String line)
	{
		for(int i=0; i<arcsModel.getSize(); i++)
		{
			String listLine = (String)arcsModel.get(i);
			int pos = listLine.indexOf(' ');
			if (pos >= 0) listLine = listLine.substring(0, pos);
			ArcProto listAP = curTech.findArcProto(listLine);
			if (listAP != ap) continue;
			arcsModel.set(i, line);
			break;
		}
	}

	private List<SeaOfGatesTrack> getCoordinates()
	{
		ArcProto ap = getCurrentlySelectedArc();
		String newGrid = sogp.getGrid(ap);
		List<SeaOfGatesTrack> coords = new ArrayList<SeaOfGatesTrack>();
		if (newGrid != null)
		{
			String[] parts = newGrid.split(",");
			for(int i=0; i<parts.length; i++)
			{
				String part = parts[i].trim();
				if (part.length() == 0) continue;
				int trackColor = SeaOfGatesTrack.getSpecificMaskNumber(part);
				if (!Character.isDigit(part.charAt(part.length()-1)))
					part = part.substring(0, part.length()-1);
				double val = TextUtils.atof(part);
				coords.add(new SeaOfGatesTrack(val, trackColor));
			}
		}
		return coords;
	}

	/**
	 * Method to build a line to describe an arc in the "Default Layers" list.
	 * @param ap the ArcProto to describe.
	 * @return the String to use in the dialog.
	 */
	private String makeArcLine(ArcProto ap)
	{
		String line = ap.getName();
		boolean extras = false;
		if (sogp.isPrevented(ap))
		{
			if (extras) line += ", "; else line += " (";
			line += "avoid";
			extras = true;
		}
		if (sogp.isFavored(ap))
		{
			if (extras) line += ", "; else line += " (";
			line += "favor";
			extras = true;
		}
		if (sogp.isTaperOnly(ap))
		{
			if (extras) line += ", "; else line += " (";
			line += "taper-only";
			extras = true;
		}
		if (!sogp.isTaperOnly(ap) && sogp.getDefaultWidthOverride(ap) != null)
		{
			if (extras) line += ", "; else line += " (";
			line += "width=" + TextUtils.formatDistance(sogp.getDefaultWidthOverride(ap).doubleValue());
			extras = true;
		}
		if (sogp.getDefaultSpacingOverride(ap, 0) != null)
		{
			if (extras) line += ", "; else line += " (";
			line += "spacingX=" + TextUtils.formatDistance(sogp.getDefaultSpacingOverride(ap, 0).doubleValue());
			extras = true;
		}
		if (sogp.getDefaultSpacingOverride(ap, 1) != null)
		{
			if (extras) line += ", "; else line += " (";
			line += "spacingY=" + TextUtils.formatDistance(sogp.getDefaultSpacingOverride(ap, 1).doubleValue());
			extras = true;
		}
		if (!sogp.isTaperOnly(ap) && sogp.get2XWidth(ap) != null)
		{
			if (extras) line += ", "; else line += " (";
			line += "2X width=" + TextUtils.formatDistance(sogp.get2XWidth(ap).doubleValue());
			extras = true;
		}
		if (sogp.getTaperLength(ap) != null)
		{
			if (extras) line += ", "; else line += " (";
			line += "Max taper length=" + TextUtils.formatDistance(sogp.getTaperLength(ap).doubleValue());
			extras = true;
		}
		if (extras) line += ")";
		return line;
	}

	/**
	 * Method to build a line to describe an arc in the "Network override" list.
	 * @param netName the name of the Network.
	 * @param ap the ArcProto to describe.
	 * @return the String to use in the dialog.
	 */
	private String makeArcOnNetworkLine(String netName, ArcProto ap)
	{
		String line = ap.getName();
		List<ArcProto> arcs = sogp.getArcsOnNet(netName);
		if (arcs == null || arcs.size() == 0)
		{
			if (sogp.isPrevented(ap)) line += " (excluded by default"; else
				line += " (included by default";
		} else
		{
			if (sogp.getArcsOnNet(netName).contains(ap)) line += " (include"; else
				line += " (excluded";
		}

		SeaOfGatesArcProperties sogap = sogp.getOverridesForArcsOnNet(netName, ap);
		if (sogap != null)
		{
			if (sogap.getWidthOverride() != null) line += ", width=" + TextUtils.formatDistance(sogap.getWidthOverride().doubleValue());
			if (sogap.getSpacingOverride(0) != null) line += ", spacingX=" + TextUtils.formatDistance(sogap.getSpacingOverride(0).doubleValue());
			if (sogap.getSpacingOverride(1) != null) line += ", spacingY=" + TextUtils.formatDistance(sogap.getSpacingOverride(1).doubleValue());
			if (sogap.getWidthOf2X() != null) line += ", width2X=" + TextUtils.formatDistance(sogap.getWidthOf2X().doubleValue());
		}
		line += ")";
		return line;
	}

	private void redrawArcOverridesList()
	{
		if (sogp == null) return;
		layersModel.clear();
		List<Object> netNames = netsList.getSelectedValuesList();
		if (netNames == null || netNames.size() != 1) return;
		String netName = (String)netNames.get(0);
		int index = netsList.getSelectedIndex();
		if (netName != null && netName.length() > 0)
		{
			for(Iterator<ArcProto> it = curTech.getArcs(); it.hasNext(); )
			{
				ArcProto ap = it.next();
				if (!ap.getFunction().isMetal()) continue;
				layersModel.addElement(makeArcOnNetworkLine(netName, ap));
			}
		}
		netsList.setSelectedIndex(index);
	}

	private void loadWidthSpacingOverrides()
	{
		perLayerOverridesChanging = true;
		sogLayerWidthOverride.setText("");
		sogLayerSpacingOverride.setText("");
		sogLayerWidthOverride.setEnabled(false);
		sogLayerSpacingOverride.setEnabled(false);
		perLayerOverridesChanging = false;
		if (sogp == null) return;
		List<Object> netNames = netsList.getSelectedValuesList();
		if (netNames == null || netNames.size() != 1) return;
		String netName = (String)netNames.get(0);
		if (netName == null || netName.length() == 0) return;
		List<Object> arcNames = layersList.getSelectedValuesList();
		if (arcNames == null || arcNames.size() != 1) return;
		String arcName = (String)arcNames.get(0);
		if (arcName == null) return;
		sogLayerWidthOverride.setEnabled(true);
		sogLayerSpacingOverride.setEnabled(true);
		int spacePos = arcName.indexOf(' ');
		if (spacePos >= 0) arcName = arcName.substring(0, spacePos);
		ArcProto ap = curTech.findArcProto(arcName);
		SeaOfGatesArcProperties sogap = sogp.getOverridesForArcsOnNet(netName, ap);
		if (sogap == null) return;
		perLayerOverridesChanging = true;
		if (sogap.getWidthOverride() != null) sogLayerWidthOverride.setText(TextUtils.formatDistance(sogap.getWidthOverride().doubleValue()));
		if (sogap.getSpacingOverride(0) != null) sogLayerSpacingOverride.setText(TextUtils.formatDistance(sogap.getSpacingOverride(0).doubleValue()));
		System.out.println("WARNING: taking only X values for layer spacing overrride");
		//if (sogap.getSpacingXOverride() != null) sogLayerSpacingOverride.setText(TextUtils.formatDistance(sogap.getSpacingXOverride().doubleValue()));
		perLayerOverridesChanging = false;
	}

	/**
	 * Method called when the user clicks on an arc name in the list.
	 */
	private void arcClicked()
	{
		// No cell selected
		if (sogp == null) return;
		ArcProto ap = getCurrentlySelectedArc();
		if (ap == null) return;
		sogAvoidLayer.setSelected(sogp.isPrevented(ap));
		sogFavorLayer.setSelected(sogp.isFavored(ap));
		sogTaperOnlyLayer.setSelected(sogp.isTaperOnly(ap));
		sogForceGrid.setSelected(sogp.isGridForced(ap));
		defaultOverridesChanging = true;
		if (sogp.getDefaultWidthOverride(ap) == null) sogDefWidth.setText(""); else
			sogDefWidth.setText(TextUtils.formatDistance(sogp.getDefaultWidthOverride(ap)));
		
		if (sogp.getDefaultSpacingOverride(ap, 0) == null) sogDefXSpacing.setText(""); else
			sogDefXSpacing.setText(TextUtils.formatDistance(sogp.getDefaultSpacingOverride(ap, 0)));
		if (sogp.getDefaultSpacingOverride(ap, 1) == null) sogDefYSpacing.setText(""); else
			sogDefYSpacing.setText(TextUtils.formatDistance(sogp.getDefaultSpacingOverride(ap, 1)));
		
		if (sogp.get2XWidth(ap) == null) sog2XWidth.setText(""); else
			sog2XWidth.setText(TextUtils.formatDistance(sogp.get2XWidth(ap)));
		if (sogp.getTaperLength(ap) == null) sogTaperLength.setText(""); else
			sogTaperLength.setText(TextUtils.formatDistance(sogp.getTaperLength(ap)));
		defaultOverridesChanging = false;
		setGridLabel(ap);

		removeLayerChanging = true;
		String removeLayer = sogp.getRemoveLayer(ap);
		if (removeLayer == null) sogRemoveLayer.setSelectedIndex(0); else
		{
			for(int i=0; i<sogRemoveLayer.getItemCount(); i++)
			{
				String layerName = (String)sogRemoveLayer.getItemAt(i);
				if (layerName.equalsIgnoreCase(removeLayer))
				{
					sogRemoveLayer.setSelectedIndex(i);
					break;
				}
			}
		}
		removeLayerChanging = false;

		gridPointsModel.clear();
		List<SeaOfGatesTrack> coords = getCoordinates();
		if (coords.size() == 0)
		{
			sogGridNone.setSelected(true);
			setFixedGridEnabled(false);
			setArbitraryGridEnabled(false);
		} else
		{
			if (coords.size() == 2)
			{
				setFixedGridEnabled(true);
				setArbitraryGridEnabled(false);
				sogGridFixed.setSelected(true);
				double v1 = coords.get(0).getCoordinate();
				double v2 = coords.get(1).getCoordinate();
				fixedValuesChanging = true;
				sogFixedSpacing.setText(TextUtils.formatDistance(v2-v1));
				sogFixedOffset.setText(TextUtils.formatDistance(v1));
				fixedValuesChanging = false;
			} else
			{
				setFixedGridEnabled(false);
				setArbitraryGridEnabled(true);
				sogGridArbitrary.setSelected(true);
				fixedValuesChanging = true;
				sogFixedSpacing.setText("");
				sogFixedOffset.setText("");
				fixedValuesChanging = false;
			}
			loadArbitraryPoints(coords);
		}
		if (gridPointsModel.getSize() > 0) gridPointsList.setSelectedIndex(0); else
			gridPointsList.setSelectedIndex(-1);

		redrawArcOverridesList();
		updateGridDisplay(true);
	}

	private void gridTypeChanged()
	{
		if (sogGridNone.isSelected())
		{
			setFixedGridEnabled(false);
			setArbitraryGridEnabled(false);
			fixedValuesChanging = true;
			sogFixedSpacing.setText("");
			sogFixedOffset.setText("");
			fixedValuesChanging = false;
			gridPointsModel.clear();
			gridPointsList.setSelectedIndex(-1);
			saveGridValues();
		} else if (sogGridFixed.isSelected())
		{
			setFixedGridEnabled(true);
			setArbitraryGridEnabled(false);
			List<SeaOfGatesTrack> coords = getCoordinates();
			double v1 = 0, v2 = 0;
			if (coords.size() > 0) v1 = coords.get(0).getCoordinate();
			if (coords.size() > 1) v2 = coords.get(1).getCoordinate(); else v2 = v1+10;
			fixedValuesChanging = true;
			sogFixedSpacing.setText(TextUtils.formatDistance(v2-v1));
			fixedValuesChanging = false;
			sogFixedOffset.setText(TextUtils.formatDistance(v1));
		} else
		{
			setFixedGridEnabled(false);
			setArbitraryGridEnabled(true);
			List<SeaOfGatesTrack> coords = getCoordinates();
			gridPointsModel.clear();
			loadArbitraryPoints(coords);
			if (gridPointsModel.getSize() > 0) gridPointsList.setSelectedIndex(0); else
				gridPointsList.setSelectedIndex(-1);
			if (coords.size() != 2)
			{
				fixedValuesChanging = true;
				sogFixedSpacing.setText("");
				sogFixedOffset.setText("");
				fixedValuesChanging = false;
			}
		}
	}

	private void loadArbitraryPoints(List<SeaOfGatesTrack> coords)
	{
		double lastVal = 0;
		for(int i=0; i<coords.size(); i++)
		{
			double val = coords.get(i).getCoordinate();
			String elVal = TextUtils.formatDistance(val);
			if (i > 0) elVal += " (+" + TextUtils.formatDistance(val - lastVal) + ")";
			if (coords.get(i).getMaskNum() > 0)
				elVal += " Color " + (char)(coords.get(i).getMaskNum() + 'a' - 1);
			gridPointsModel.addElement(elVal);
			lastVal = val;
		}
	}

	private void setFixedGridEnabled(boolean enabled)
	{
		TitledBorder b = (TitledBorder)gridFixed.getBorder();
		b.setTitleColor(enabled ? Color.black : Color.GRAY);
		sogFixedSpacing.setEnabled(enabled);
		sogFixedOffset.setEnabled(enabled);
		jLabel6.setEnabled(enabled);
		jLabel8.setEnabled(enabled);
		sogGridSpacingUp.setEnabled(enabled);
		sogGridSpacingDown.setEnabled(enabled);
		sogGridOffsetUp.setEnabled(enabled);
		sogGridOffsetDown.setEnabled(enabled);
	}

	private void setArbitraryGridEnabled(boolean enabled)
	{
		TitledBorder b = (TitledBorder)gridArbitrary.getBorder();
		b.setTitleColor(enabled ? Color.black : Color.GRAY);
		gridPointsList.setEnabled(enabled);
		jLabel3.setEnabled(enabled);
		jLabel4.setEnabled(enabled);
		sogDrawGrid.setEnabled(enabled);
		sogGridAllUp.setEnabled(enabled);
		sogGridAllDown.setEnabled(enabled);
		sogGridIndUp.setEnabled(enabled);
		sogGridIndDown.setEnabled(enabled);
		sogGridIndDelete.setEnabled(enabled);
		sogGridIndNew.setEnabled(enabled);
	}

	private void fixedGridValuesChanged()
	{
		if (fixedValuesChanging) return;
		double v1 = TextUtils.atofDistance(sogFixedSpacing.getText());
		double v2 = TextUtils.atofDistance(sogFixedOffset.getText());
		gridPointsModel.clear();
		gridPointsModel.addElement(TextUtils.formatDistance(v2));
		gridPointsModel.addElement(TextUtils.formatDistance(v2+v1) + " (+" + TextUtils.formatDistance(v1) + ")");
		saveGridValues();
	}

	/**
	 * Class to handle special changes to changes to fixed-grid values.
	 */
	private class FixedGridDocumentListener implements DocumentListener
	{
		public void changedUpdate(DocumentEvent e) { fixedGridValuesChanged(); }
		public void insertUpdate(DocumentEvent e) { fixedGridValuesChanged(); }
		public void removeUpdate(DocumentEvent e) { fixedGridValuesChanged(); }
	}

	private void shiftGridSpacing(int dir)
	{
		if (!isTechnologyLoaded()) return;

		double v1 = TextUtils.atofDistance(sogFixedSpacing.getText());
		v1 += dir;
		double v2 = TextUtils.atofDistance(sogFixedOffset.getText());
		fixedValuesChanging = true;
		sogFixedSpacing.setText(TextUtils.formatDistance(v1));
		fixedValuesChanging = false;
		gridPointsModel.clear();
		gridPointsModel.addElement(TextUtils.formatDistance(v2));
		gridPointsModel.addElement(TextUtils.formatDistance(v2+v1) + " (+" + TextUtils.formatDistance(v1) + ")");
		saveGridValues();
	}

	private void shiftGridOffset(int dir)
	{
		if (!isTechnologyLoaded()) return;

		double v1 = TextUtils.atofDistance(sogFixedSpacing.getText());
		double v2 = TextUtils.atofDistance(sogFixedOffset.getText());
		v2 += dir;
		fixedValuesChanging = true;
		sogFixedOffset.setText(TextUtils.formatDistance(v2));
		fixedValuesChanging = false;
		gridPointsModel.clear();
		gridPointsModel.addElement(TextUtils.formatDistance(v2));
		gridPointsModel.addElement(TextUtils.formatDistance(v2+v1) + " (+" + TextUtils.formatDistance(v1) + ")");
		saveGridValues();
	}

	/**
	 * Method called when the arc changes or when the horizontal/vertical properties change.
	 * Updates the label on the "grid spacing" field.
	 * @param ap the current ArcProto.
	 */
	private void setGridLabel(ArcProto ap)
	{
		Boolean isHor = getGridHorizontal(ap);
		TitledBorder tb = (TitledBorder)gridControl.getBorder();
		if (isHor == null) tb.setTitle("Grid Control"); else
		{
			if (isHor.booleanValue()) tb.setTitle("Horizontal Grid Control"); else
				tb.setTitle("Vertical Grid Control");
		}
	}

	private Boolean getGridHorizontal(ArcProto ap)
	{
		if (sogHorVerUsage.getSelectedIndex() == 2) return null;
		Boolean isHor = Boolean.TRUE;
		if (sogVerOddHorEven.isSelected())
		{
			if ((ap.getFunction().getLevel()%2) != 0) isHor = Boolean.FALSE;
		} else
		{
			if ((ap.getFunction().getLevel()%2) == 0) isHor = Boolean.FALSE;
		}
		return isHor;
	}

	/**
	 * Method called when the cut layer has changed.
	 */
	private void cutLayerChanged()
	{
		if (removeLayerChanging) return;
		ArcProto ap = getCurrentlySelectedArc();
		if (sogRemoveLayer.getSelectedIndex() == 0) sogp.setRemoveLayer(ap, null); else
			sogp.setRemoveLayer(ap, (String)sogRemoveLayer.getSelectedItem());
		updateSOGCellData();
	}

	/**
	 * Method called when the routing bounds layer has changed.
	 */
	private void routingBoundsLayerNameChanged()
	{
		if (removeLayerChanging) return;
		if (sogRoutingBoundsLayer.getSelectedIndex() == 0) sogp.setRoutingBoundsLayerName(null); else
			sogp.setRoutingBoundsLayerName((String)sogRoutingBoundsLayer.getSelectedItem());
		updateSOGCellData();
	}

	/**
	 * Method called when the technology (for default arcs) has changed.
	 */
	private void techChanged()
	{
		String techName = (String)sogTechList.getSelectedItem();
		Technology tech = Technology.findTechnology(techName);
		if (tech == null) return;
		curTech = tech;

		// Rebuild list of layers in the technology
		removeLayerChanging = true;
		sogRemoveLayer.removeAllItems();
		sogRemoveLayer.addItem("< No Cut Layer >");
		sogRoutingBoundsLayer.removeAllItems();
		sogRoutingBoundsLayer.addItem("< No Bounds Layer >");
		for(Iterator<Layer> lIt = curTech.getLayers(); lIt.hasNext(); )
		{
			Layer layer = lIt.next();
			sogRemoveLayer.addItem(layer.getName());
			sogRoutingBoundsLayer.addItem(layer.getName());
		}
		removeLayerChanging = false;

		arcsModel.clear();
		for(Iterator<ArcProto> it = tech.getArcs(); it.hasNext(); )
		{
			ArcProto ap = it.next();
			if (!ap.getFunction().isMetal()) continue;
			arcsModel.addElement(makeArcLine(ap));
		}
		arcsList.setSelectedIndex(0);
		arcClicked();
	}

	private void addNetworkName()
	{
		String netName = sogNetName.getText().trim();
		if (netName.length() == 0)
		{
			Job.getUserInterface().showErrorMessage("No Network Name Entered", "Type a Net name first.");
			return;
		}
		List<String> nets = sogp.getNetsToRoute();
		if (nets.contains(netName))
		{
			Job.getUserInterface().showErrorMessage("Duplicate Network Name", "The name (" + netName + ") is already in the list.");
			return;
		}
		Set<String> allNetNames = getAllNetNames();
		if (!allNetNames.contains(netName))
		{
			Job.getUserInterface().showErrorMessage("Network name " + netName + " was not found in the current cell. Adding to the list anyway.",
				"Unknown network name");
			return;
		}
		sogp.addNetToRoute(netName);
		loadNets();
		updateSOGCellData();
	}

	private void removeNetworkName()
	{
		List<Object> netNames = netsList.getSelectedValuesList();
		if (netNames == null || netNames.size() == 0)
		{
			Job.getUserInterface().showErrorMessage("No Network Name Selected", "Click on a name from the above list to delete it.");
			return;
		}
		for(int i=0; i<netNames.size(); i++)
			sogp.removeNetToRoute((String)netNames.get(i));
		loadNets();
		updateSOGCellData();
	}

	private void addLayerToNet()
	{
		String netName = null;
		List<Object> netNames = netsList.getSelectedValuesList();
		if (netNames != null && netNames.size() == 1) netName = (String)netNames.get(0);
		if (netName == null)
		{
			Job.getUserInterface().showErrorMessage("Click on a net name from the above list before adding a layer.",
				"No Network Name Selected");
			return;
		}
		List<Object> arcNames = layersList.getSelectedValuesList();
		if (arcNames == null || arcNames.size() == 0)
		{
			Job.getUserInterface().showErrorMessage("Click on a layer name from the above list before adding to the selected network.",
				"No Layer Name Selected");
			return;
		}
		for(int i=0; i<arcNames.size(); i++)
		{
			String arcName = (String)arcNames.get(i);
			int spacePos = arcName.indexOf(' ');
			if (spacePos >= 0) arcName = arcName.substring(0, spacePos);
			ArcProto ap = curTech.findArcProto(arcName);
			sogp.addArcToNet(netName, ap);
		}
		redrawArcOverridesList();
		updateSOGCellData();
	}

	private void removeLayerFromNet()
	{
		String netName = null;
		List<Object> netNames = netsList.getSelectedValuesList();
		if (netNames != null && netNames.size() == 1) netName = (String)netNames.get(0);
		if (netName == null)
		{
			Job.getUserInterface().showErrorMessage("Click on a net name from the above list before removing a layer.",
				"No Network Name Selected");
			return;
		}
		List<Object> arcNames = layersList.getSelectedValuesList();
		if (arcNames == null || arcNames.size() == 0)
		{
			Job.getUserInterface().showErrorMessage("Click on a layer name from the above list to remove it from the selected network.",
				"No Layer Name Selected");
			return;
		}
		for(int i=0; i<arcNames.size(); i++)
		{
			String arcName = (String)arcNames.get(i);
			int spacePos = arcName.indexOf(' ');
			if (spacePos >= 0) arcName = arcName.substring(0, spacePos);
			ArcProto ap = curTech.findArcProto(arcName);
			List<ArcProto> arcs = sogp.getArcsOnNet(netName);
			if (arcs == null || arcs.size() == 0)
			{
				// add all other arcs so that removal works here
				for(Iterator<ArcProto> it = curTech.getArcs(); it.hasNext(); )
				{
					ArcProto apIt = it.next();
					if (apIt == ap) continue;
					sogp.addArcToNet(netName, apIt);
				}
			} else
			{
				sogp.removeArcFromNet(netName, ap);
			}
		}
		redrawArcOverridesList();
		updateSOGCellData();
	}

	private void gridPointClicked()
	{
		updateGridDisplay(true);
	}

	private void moveGridLines(int dir, boolean moveAll)
	{
		if (!isTechnologyLoaded()) return;

		EDimension dim = User.getAlignmentToGrid();
		if (moveAll)
		{
			double lastVal = 0;
			for(int i=0; i<gridPointsModel.getSize(); i++)
			{
				double val = TextUtils.atofDistance((String)gridPointsModel.get(i)) + dim.getHeight() * dir;
				String elVal = TextUtils.formatDistance(val);
				if (i > 0) elVal += " (+" + TextUtils.formatDistance(val - lastVal) + ")";
				gridPointsModel.set(i, elVal);
				lastVal = val;
			}
		} else
		{
			int index = gridPointsList.getSelectedIndex();
			double v = TextUtils.atofDistance((String)gridPointsModel.get(index)) + dim.getHeight() * dir;
			gridPointsModel.set(index, v + "");
			sortGridPoints();
		}
		saveGridValues();
		updateGridDisplay(false);
	}

	private void sortGridPoints()
	{
		List<Double> vals = new ArrayList<Double>();
		for(int i=0; i<gridPointsModel.getSize(); i++)
		{
			String part = (String)gridPointsModel.get(i);
			int spacePos = part.indexOf(' ');
			if (spacePos > 0) part = part.substring(0, spacePos);
			double v = TextUtils.atofDistance(part);
			vals.add(new Double(v));
		}
		Collections.sort(vals);
		double lastVal = 0;
		for(int i=0; i<gridPointsModel.getSize(); i++)
		{
			double val = vals.get(i).doubleValue();
			String elVal = TextUtils.formatDistance(val);
			if (i > 0) elVal += " (+" + TextUtils.formatDistance(val - lastVal) + ")";
			gridPointsModel.set(i, elVal);
			lastVal = val;
		}
	}

	/**
	 * Function to determine if a proper technology has been uploaded
	 * @return true if the technology is valid
	 */
	private boolean isTechnologyLoaded()
	{
		if (curTech == null)
		{
			System.out.println("You must load a cell with a valid technology first");
			return false;
		}
		return true;
	}

	private void addGridLine()
	{
		if (!isTechnologyLoaded()) return;

		String str = Job.getUserInterface().askForInput("Grid Line Coordinate(s):", "Make New Grid Lines (comma-separated list)", "0");
		if (str == null) return;
		String[] parts = str.split(",");
		for(int i=0; i<parts.length; i++)
		{
			String part = parts[i].trim();
			if (part.length() == 0) continue;
			int spacePos = part.indexOf(' ');
			if (spacePos > 0) part = part.substring(0, spacePos);
			double vInsert = TextUtils.atofDistance(part);
			insertGridLine(vInsert);
		}
		saveGridValues();
		updateGridDisplay(false);
	}

	private void insertGridLine(double vInsert)
	{
		boolean inserted = false;
		double lastVal = 0;
		for(int i=0; i<gridPointsModel.getSize(); i++)
		{
			String part = (String)gridPointsModel.get(i);
			int spacePos = part.indexOf(' ');
			if (spacePos > 0) part = part.substring(0, spacePos);
			double v = TextUtils.atofDistance(part);
			if (vInsert <= v)
			{
				String elVal = TextUtils.formatDistance(vInsert);
				if (i > 0) elVal += " (+" + TextUtils.formatDistance(vInsert - lastVal) + ")";
				gridPointsModel.add(i, elVal);
				if (i < gridPointsModel.getSize()-1)
				{
					part = (String)gridPointsModel.get(i+1);
					spacePos = part.indexOf(' ');
					if (spacePos > 0) part = part.substring(0, spacePos);
					double nextV = TextUtils.atofDistance(part);
					elVal = TextUtils.formatDistance(nextV) + " (+" + TextUtils.formatDistance(nextV - vInsert) + ")";
					gridPointsModel.add(i+1, elVal);
				}
				gridPointsList.setSelectedIndex(i);
				inserted = true;
				break;
			}
			lastVal = v;
		}
		if (!inserted)
		{
			String elVal = TextUtils.formatDistance(vInsert);
			if (gridPointsModel.getSize() > 0) elVal += " (+" + TextUtils.formatDistance(vInsert - lastVal) + ")";
			gridPointsModel.addElement(elVal);
			gridPointsList.setSelectedIndex(gridPointsModel.getSize()-1);
		}
	}

	private void deleteGridLine()
	{
		if (!isTechnologyLoaded()) return;

		int index = gridPointsList.getSelectedIndex();
		if (index < 0) return;
		gridPointsModel.remove(index);
		sortGridPoints();
		if (index >= gridPointsModel.getSize()) index--;
		gridPointsList.setSelectedIndex(index);
		saveGridValues();
		updateGridDisplay(false);
	}

	private void drawGrid()
	{
		WindowFrame.ElectricEventListener oldListener = WindowFrame.getListener();
		Cursor oldCursor = TopLevel.getCurrentCursor();
		WindowFrame.ElectricEventListener newListener = oldListener;
		if (newListener == null || !(newListener instanceof PlaceGridLineListener))
		{
			TopLevel.setCurrentCursor(ToolBar.outlineCursor);
			newListener = new PlaceGridLineListener(oldListener, oldCursor, this);
			WindowFrame.setListener(newListener);
		}
	}

	/**
	 * Class to choose a location for new node placement.
	 */
	public static class PlaceGridLineListener
		implements WindowFrame.ElectricEventListener
	{
		private WindowFrame.ElectricEventListener oldListener;
		private Cursor oldCursor;
		private SeaOfGatesCell dialog;

		/**
		 * Places a new grid line.
		 * @param oldListener
		 * @param oldCursor
		 */
		private PlaceGridLineListener(WindowFrame.ElectricEventListener oldListener, Cursor oldCursor, SeaOfGatesCell dialog)
		{
			this.oldListener = oldListener;
			this.oldCursor = oldCursor;
			this.dialog = dialog;
		}

		public void mouseReleased(MouseEvent evt)
		{
			if (!(evt.getSource() instanceof EditWindow)) return;
			EditWindow wnd = (EditWindow)evt.getSource();

			Cell cell = wnd.getCell();
			if (cell == null)
			{
				JOptionPane.showMessageDialog(TopLevel.getCurrentJFrame(),
					"Cannot create node: this window has no cell in it");
				return;
			}

			// restore the former listener to the edit windows
			finished(wnd);
			ArcProto ap = dialog.getCurrentlySelectedArc();
			Boolean isHor = dialog.getGridHorizontal(ap);
			if (isHor != null)
			{
				Point2D pt = wnd.screenToDatabase(evt.getX(), evt.getY());
				EditWindow.gridAlign(pt);
				double val = isHor.booleanValue() ? pt.getY() : pt.getX();

				// shift to be in the range of the grid locations
				if (dialog.gridPointsModel.getSize() >= 2)
				{
					String lowPart = (String)dialog.gridPointsModel.get(0);
					int spacePos = lowPart.indexOf(' ');
					if (spacePos > 0) lowPart = lowPart.substring(0, spacePos);
					double lowest = TextUtils.atofDistance(lowPart);

					String highPart = (String)dialog.gridPointsModel.get(dialog.gridPointsModel.getSize()-1);
					spacePos = highPart.indexOf(' ');
					if (spacePos > 0) highPart = highPart.substring(0, spacePos);
					double highest = TextUtils.atofDistance(highPart);

					double range = highest - lowest;
					if (range > 0)
					{
						Rectangle2D bounds = wnd.getDisplayedBounds();
						if (isHor.booleanValue())
						{
							double low = Math.floor((bounds.getMinY() - lowest) / range) * range;
							double high = Math.ceil((bounds.getMaxY() - lowest) / range) * range;
							for(double v = low; v <= high; v += range)
							{
								if (bounds.getCenterY() < v+lowest || bounds.getCenterY() >= v+lowest+range) continue;
								val -= v;
								break;
							}
						} else
						{
							double low = Math.floor((bounds.getMinX() - lowest) / range) * range;
							double high = Math.ceil((bounds.getMaxX() - lowest) / range) * range;
							for(double v = low; v <= high; v += range)
							{
								if (bounds.getCenterX() < v+lowest || bounds.getCenterX() >= v+lowest+range) continue;
								val -= v;
								break;
							}
						}
					}
				}
				dialog.insertGridLine(val);
			}
		}

		private void finished(EditWindow wnd)
		{
			if (wnd != null)
			{
				Highlighter highlighter = wnd.getHighlighter();
				highlighter.clear();
				highlighter.finished();
			}
			WindowFrame.setListener(oldListener);
			TopLevel.setCurrentCursor(oldCursor);
		}

		public void mousePressed(MouseEvent evt) {}
		public void mouseClicked(MouseEvent evt) {}
		public void mouseEntered(MouseEvent evt) {}
		public void mouseExited(MouseEvent evt) {}
		public void mouseWheelMoved(MouseWheelEvent evt) {}

		public void mouseMoved(MouseEvent evt)
		{
			showGridLine(evt);
		}

		public void mouseDragged(MouseEvent evt)
		{
			showGridLine(evt);
		}

		private void showGridLine(MouseEvent evt)
		{
			if (!(evt.getSource() instanceof EditWindow)) return;
			EditWindow wnd = (EditWindow)evt.getSource();
			Cell cell = wnd.getCell();
			Highlighter highlighter = wnd.getHighlighter();
			highlighter.clear();
			Rectangle2D bounds = wnd.getDisplayedBounds();
			Point2D pt = wnd.screenToDatabase(evt.getX(), evt.getY());
			EditWindow.gridAlign(pt);
			boolean drawHor = true, drawVer = true;
			ArcProto ap = dialog.getCurrentlySelectedArc();
			Boolean isHor = dialog.getGridHorizontal(ap);
			if (isHor != null)
			{
				if (isHor.booleanValue()) drawVer = false; else
					drawHor = false;
			}
			if (drawHor)
				highlighter.addLine(new Point2D.Double(bounds.getMinX(), pt.getY()), new Point2D.Double(bounds.getMaxX(), pt.getY()),
					cell, true, Color.RED, false);
			if (drawVer)
				highlighter.addLine(new Point2D.Double(pt.getX(), bounds.getMinY()), new Point2D.Double(pt.getX(), bounds.getMaxY()),
					cell, true, Color.RED, false);
			highlighter.finished();
		}

		public void keyPressed(KeyEvent evt)
		{
			int chr = evt.getKeyCode();
			if (chr == KeyEvent.VK_A || chr == KeyEvent.VK_ESCAPE)
			{
				// abort
				finished(EditWindow.getCurrent());
			}
		}

		public void keyReleased(KeyEvent evt) {}
		public void keyTyped(KeyEvent evt) {}
        public void databaseChanged(DatabaseChangeEvent e) {}
	}


	/**
	 * Method to save the grid values when they have changed.
	 */
	private void saveGridValues()
	{
		ArcProto ap = getCurrentlySelectedArc();
		String newGrid = "";
		for(int i=0; i<gridPointsModel.getSize(); i++)
		{
			String elVal = (String)gridPointsModel.get(i);
			int spacePos = elVal.indexOf(' ');
			if (spacePos >= 0) elVal = elVal.substring(0, spacePos);
			if (newGrid.length() > 0) newGrid += ",";
			newGrid += TextUtils.formatDouble(TextUtils.atofDistance(elVal));
		}
		sogp.setGrid(ap, newGrid);
		updateSOGCellData();
	}

	/**
	 * Method to redraw the grid lines on the screen (if grid display is selected).
	 */
	private void updateGridDisplay(boolean force)
	{
		EditWindow wnd = EditWindow.getCurrent();
		if (wnd == null) return;
		Highlighter h = wnd.getRulerHighlighter();
		h.clear();
		if (sogShowGrid.isSelected())
		{
			boolean hor = true;
			ArcProto ap = getCurrentlySelectedArc();
			if (ap != null)
			{
				Boolean isHor = getGridHorizontal(ap);
				if (isHor != null)
				{
					if (!isHor.booleanValue()) hor = false;
				}
			}
			else
				System.out.println("can't grid currently selected arc in SeaOfGatesCell:updateGridDisplay");

			List<Double> coords = new ArrayList<Double>();
			for(int i=0; i<gridPointsModel.getSize(); i++)
			{
				String part = (String)gridPointsModel.get(i);
				int spacePos = part.indexOf(' ');
				if (spacePos > 0) part = part.substring(0, spacePos);
				coords.add(new Double(TextUtils.atofDistance(part)));
			}
			showGrid(wnd, h, coords, hor, wnd.getScale(), gridPointsList.getSelectedIndex());
		}
		h.finished();
		if (force) wnd.repaint();
	}

	public static void showGrid(EditWindow wnd, Highlighter h, List<Double> coords, boolean hor, double scale, int thickIndex)
	{
		if (coords.size() >= 2)
		{
			Cell cell = wnd.getCell();
			int prev = (int)(coords.get(0).doubleValue() * scale);
			boolean spreadOut = false;
			for(int i=1; i<coords.size(); i++)
			{
				int val = (int)(coords.get(i).doubleValue() * scale);
				if (Math.abs(val-prev) > 3) { spreadOut = true;   break; }
				prev = val;
			}
			if (spreadOut)
			{
				double offset = coords.get(0).doubleValue();
				double range = coords.get(coords.size()-1).doubleValue() - offset;

				Rectangle2D bounds = wnd.getDisplayedBounds();
				if (range > 0)
				{
					if (hor)
					{
						double low = Math.floor((bounds.getMinY() - offset) / range) * range;
						double high = Math.ceil((bounds.getMaxY() - offset) / range) * range;
						for(double v = low; v <= high; v += range)
						{
							int startInd = 0;
							if (v != low) startInd = 1;
							for(int i=startInd; i<coords.size(); i++)
							{
								double val = coords.get(i).doubleValue();
								double y = v + val;
								Point2D start = new Point2D.Double(bounds.getMinX(), y);
								Point2D end = new Point2D.Double(bounds.getMaxX(), y);
								boolean thick = false;
								Color col = Color.WHITE;
								if (y >= offset && y <= offset+range)
								{
									col = Color.RED;
									if (i == thickIndex) thick = true;
								}
								h.addLine(start, end, cell, thick, col, false);
							}
						}
					} else
					{
						double low = Math.floor((bounds.getMinX() - offset) / range) * range;
						double high = Math.ceil((bounds.getMaxX() - offset) / range) * range;
						for(double v = low; v <= high; v += range)
						{
							int startInd = 0;
							if (v != low) startInd = 1;
							for(int i=startInd; i<coords.size(); i++)
							{
								double val = coords.get(i).doubleValue();
								double x = v + val;
								Point2D start = new Point2D.Double(x, bounds.getMinY());
								Point2D end = new Point2D.Double(x, bounds.getMaxY());
								boolean thick = false;
								Color col = Color.WHITE;
								if (x >= offset && x <= offset+range)
								{
									col = Color.RED;
									if (i == thickIndex) thick = true;
								}
								h.addLine(start, end, cell, thick, col, false);
							}
						}
					}
				}
			}
		}
	}

	/**
	 * Method to export the control file for routing.
	 */
    private void exportData()
    {
		Job.getUserInterface().showErrorMessage("Not yet implemented", "Cannot export data yet.");		// TODO: finish
    }

    /**
     * Method to import the control file for routing.
     */
    private void importData()
    {
    	String fileName = OpenFile.chooseInputFile(FileType.ANY, "Routing control file", null);
    	if (fileName == null) return;
		EditWindow wnd = EditWindow.needCurrent();
		Cell cell = wnd.getCell();
    	sogp.importData(fileName, cell, curTech);
    	
    	List<String> netNames = sogp.getNetsToRoute();
    	if (netNames != null)
    	{
    		Set<String> allNetNames = getAllNetNames();
			String error = null;
    		for(String netName : netNames)
    		{
    			if (allNetNames.contains(netName)) continue;
    			if (error == null) error = ""; else error += ", ";
    			error += netName;
    		}
    		if (error != null)
    			Job.getUserInterface().showErrorMessage("These network names were not found in the current cell: " + error,
    				"Unknown network names");
    	}
    	loadDialog(curTech);
		if (sogBlockageShow.isSelected()) showBlockages();
		updateSOGCellData();
    }

    private Set<String> getAllNetNames()
    {
		EditWindow wnd = EditWindow.getCurrent();
		Cell cell = wnd.getCell();
		Netlist nl = cell.getNetlist();
		Set<String> allNetNames = new HashSet<String>();
		for(Iterator<Network> it = nl.getNetworks(); it.hasNext(); )
		{
			Network net = it.next();
			for(Iterator<String> nIt = net.getNames(); nIt.hasNext(); )
			{
				String name = nIt.next();
				allNetNames.add(name);
			}
		}
		return allNetNames;
    }

    private void done()
    {
    	setVisible(false);
		sogShowGrid.setSelected(false);
		sogBlockageShow.setSelected(false);
	}

	protected void escapePressed() { done(); }

	private void updateSOGCellData()
	{
		if (initializing) return;
		new UpdateSeaOfGatesCell(sogp);
	}

	/**
	 * Class to delete an attribute in a new thread.
	 */
	private static class UpdateSeaOfGatesCell extends Job
	{
		private SeaOfGatesCellParameters sogp;

		private UpdateSeaOfGatesCell(SeaOfGatesCellParameters sogp)
		{
			super("Update Sea-of-Gates Cell Properties", User.getUserTool(), Job.Type.CHANGE, null, null, Job.Priority.USER);
			this.sogp = sogp;
			startJob();
		}

		@Override
		public boolean doIt() throws JobException
		{
			EditingPreferences ep = getEditingPreferences();
			sogp.saveParameters(ep);
			return true;
		}
	}

	/** This method is called from within the constructor to
	 * initialize the form.
	 * WARNING: Do NOT modify this code. The content of this method is
	 * always regenerated by the Form Editor.
	 */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents()
    {
        java.awt.GridBagConstraints gridBagConstraints;

        sogHorVerChoices = new javax.swing.ButtonGroup();
        sogGridType = new javax.swing.ButtonGroup();
        jLabel1 = new javax.swing.JLabel();
        jPanel2 = new javax.swing.JPanel();
        ok = new javax.swing.JButton();
        sogImportData = new javax.swing.JButton();
        sogExportData = new javax.swing.JButton();
        jPanel4 = new javax.swing.JPanel();
        jPanel1 = new javax.swing.JPanel();
        sogNetList = new javax.swing.JScrollPane();
        sogLayerList = new javax.swing.JScrollPane();
        jLabel10 = new javax.swing.JLabel();
        sogNetName = new javax.swing.JTextField();
        jLabel11 = new javax.swing.JLabel();
        sogAddNet = new javax.swing.JButton();
        jLabel12 = new javax.swing.JLabel();
        sogLayerWidthOverride = new javax.swing.JTextField();
        jLabel13 = new javax.swing.JLabel();
        sogLayerSpacingOverride = new javax.swing.JTextField();
        sogAddLayerToNet = new javax.swing.JButton();
        sogRemoveLayerFromNet = new javax.swing.JButton();
        sogRemoveNet = new javax.swing.JButton();
        jLabel14 = new javax.swing.JLabel();
        jPanel3 = new javax.swing.JPanel();
        sogBlockageList = new javax.swing.JScrollPane();
        sogBlockageNew = new javax.swing.JButton();
        sogBlockageDelete = new javax.swing.JButton();
        sogBlockageEdit = new javax.swing.JButton();
        sogBlockageShow = new javax.swing.JCheckBox();
        jPanel5 = new javax.swing.JPanel();
        entireCell = new javax.swing.JPanel();
        sogNoSteinerTrees = new javax.swing.JCheckBox();
        sogHorOddVerEven = new javax.swing.JRadioButton();
        sogVerOddHorEven = new javax.swing.JRadioButton();
        jLabel7 = new javax.swing.JLabel();
        sogHorVerUsage = new javax.swing.JComboBox();
        jLabel2 = new javax.swing.JLabel();
        sogTechList = new javax.swing.JComboBox();
        jLabel18 = new javax.swing.JLabel();
        sogRoutingBoundsLayer = new javax.swing.JComboBox();
        jPanel7 = new javax.swing.JPanel();
        sogContactDownToAvoidedLayers = new javax.swing.JCheckBox();
        sogContactUpToAvoidedLayers = new javax.swing.JCheckBox();
        jPanel8 = new javax.swing.JPanel();
        jLabel19 = new javax.swing.JLabel();
        sogContact2XInclusionPat = new javax.swing.JTextField();
        jLabel15 = new javax.swing.JLabel();
        sogContact1XInclusionPat = new javax.swing.JTextField();
        jLabel23 = new javax.swing.JLabel();
        jPanel9 = new javax.swing.JPanel();
        jLabel16 = new javax.swing.JLabel();
        sogContactExclusionPat = new javax.swing.JTextField();
        sogNoRotatedContacts = new javax.swing.JCheckBox();
        individualLayers = new javax.swing.JPanel();
        sogArcList = new javax.swing.JScrollPane();
        gridControl = new javax.swing.JPanel();
        sogGridNone = new javax.swing.JRadioButton();
        sogGridFixed = new javax.swing.JRadioButton();
        sogGridArbitrary = new javax.swing.JRadioButton();
        sogShowGrid = new javax.swing.JCheckBox();
        sogForceGrid = new javax.swing.JCheckBox();
        gridFixed = new javax.swing.JPanel();
        jLabel6 = new javax.swing.JLabel();
        sogFixedSpacing = new javax.swing.JTextField();
        jLabel8 = new javax.swing.JLabel();
        sogFixedOffset = new javax.swing.JTextField();
        sogGridSpacingUp = new javax.swing.JButton();
        sogGridSpacingDown = new javax.swing.JButton();
        sogGridOffsetUp = new javax.swing.JButton();
        sogGridOffsetDown = new javax.swing.JButton();
        gridArbitrary = new javax.swing.JPanel();
        sogDrawGrid = new javax.swing.JButton();
        sogGridIndScroll = new javax.swing.JScrollPane();
        sogGridAllUp = new javax.swing.JButton();
        sogGridAllDown = new javax.swing.JButton();
        sogGridIndUp = new javax.swing.JButton();
        sogGridIndDown = new javax.swing.JButton();
        sogGridIndDelete = new javax.swing.JButton();
        sogGridIndNew = new javax.swing.JButton();
        jLabel3 = new javax.swing.JLabel();
        jLabel4 = new javax.swing.JLabel();
        jPanel6 = new javax.swing.JPanel();
        sogFavorLayer = new javax.swing.JCheckBox();
        sogAvoidLayer = new javax.swing.JCheckBox();
        jLabel21 = new javax.swing.JLabel();
        sogTaperLength = new javax.swing.JTextField();
        sogTaperOnlyLayer = new javax.swing.JCheckBox();
        jLabel5 = new javax.swing.JLabel();
        sogDefWidth = new javax.swing.JTextField();
        jLabel20 = new javax.swing.JLabel();
        sog2XWidth = new javax.swing.JTextField();
        jLabel9 = new javax.swing.JLabel();
        sogDefXSpacing = new javax.swing.JTextField();
        jLabel22 = new javax.swing.JLabel();
        sogDefYSpacing = new javax.swing.JTextField();
        jLabel17 = new javax.swing.JLabel();
        sogRemoveLayer = new javax.swing.JComboBox();

        setTitle("Sea-of-Gates Cell Properties");
        setName(""); // NOI18N
        addWindowListener(new java.awt.event.WindowAdapter()
        {
            public void windowClosing(java.awt.event.WindowEvent evt)
            {
                closeDialog(evt);
            }
        });
        getContentPane().setLayout(new java.awt.GridBagLayout());

        jLabel1.setText("Sea-of-Gates Properties for Cell XXX");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 4);
        getContentPane().add(jLabel1, gridBagConstraints);

        jPanel2.setLayout(new java.awt.GridBagLayout());

        ok.setText("Done");
        ok.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                ok(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 4);
        jPanel2.add(ok, gridBagConstraints);

        sogImportData.setText("Import...");
        sogImportData.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                sogImportDataActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 4);
        jPanel2.add(sogImportData, gridBagConstraints);

        sogExportData.setText("Export...");
        sogExportData.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                sogExportDataActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 4);
        jPanel2.add(sogExportData, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(jPanel2, gridBagConstraints);

        jPanel4.setLayout(new java.awt.GridBagLayout());

        jPanel1.setBorder(javax.swing.BorderFactory.createTitledBorder("Nets to Route"));
        jPanel1.setLayout(new java.awt.GridBagLayout());
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        jPanel1.add(sogNetList, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.gridwidth = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        jPanel1.add(sogLayerList, gridBagConstraints);

        jLabel10.setText("Layers for Selected Net (if empty, use default):");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.gridwidth = 4;
        gridBagConstraints.insets = new java.awt.Insets(15, 4, 4, 4);
        jPanel1.add(jLabel10, gridBagConstraints);

        sogNetName.setColumns(10);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        jPanel1.add(sogNetName, gridBagConstraints);

        jLabel11.setText("Net name:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        jPanel1.add(jLabel11, gridBagConstraints);

        sogAddNet.setText("Add Net");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        jPanel1.add(sogAddNet, gridBagConstraints);

        jLabel12.setText("Layer width:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 0);
        jPanel1.add(jLabel12, gridBagConstraints);

        sogLayerWidthOverride.setColumns(6);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 0, 4, 4);
        jPanel1.add(sogLayerWidthOverride, gridBagConstraints);

        jLabel13.setText("Layer spacing:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 0);
        jPanel1.add(jLabel13, gridBagConstraints);

        sogLayerSpacingOverride.setColumns(6);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 0, 4, 4);
        jPanel1.add(sogLayerSpacingOverride, gridBagConstraints);

        sogAddLayerToNet.setText("Add Layer to Net");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        jPanel1.add(sogAddLayerToNet, gridBagConstraints);

        sogRemoveLayerFromNet.setText("Remove Layer from Net");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        jPanel1.add(sogRemoveLayerFromNet, gridBagConstraints);

        sogRemoveNet.setText("Remove Net");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        jPanel1.add(sogRemoveNet, gridBagConstraints);

        jLabel14.setText("If this list is empty, route all nets");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 4;
        jPanel1.add(jLabel14, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 0.75;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        jPanel4.add(jPanel1, gridBagConstraints);

        jPanel3.setBorder(javax.swing.BorderFactory.createTitledBorder("Extra Blockages"));
        jPanel3.setLayout(new java.awt.GridBagLayout());
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        jPanel3.add(sogBlockageList, gridBagConstraints);

        sogBlockageNew.setIcon(Resources.getResource(getClass(), "IconNew.gif"));
        sogBlockageNew.setToolTipText("Add new blockage");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.weightx = 0.25;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        jPanel3.add(sogBlockageNew, gridBagConstraints);

        sogBlockageDelete.setIcon(Resources.getResource(getClass(), "IconDelete.gif"));
        sogBlockageDelete.setToolTipText("Delete selected blockage");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.weightx = 0.25;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        jPanel3.add(sogBlockageDelete, gridBagConstraints);

        sogBlockageEdit.setIcon(Resources.getResource(getClass(), "IconDraw.gif"));
        sogBlockageEdit.setToolTipText("Edit selected blockage");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.weightx = 0.25;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        jPanel3.add(sogBlockageEdit, gridBagConstraints);

        sogBlockageShow.setText("Show");
        sogBlockageShow.setToolTipText("Show all blockages");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.weightx = 0.25;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        jPanel3.add(sogBlockageShow, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 0.25;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        jPanel4.add(jPanel3, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.weighty = 1.0;
        getContentPane().add(jPanel4, gridBagConstraints);

        jPanel5.setLayout(new java.awt.GridBagLayout());

        entireCell.setBorder(javax.swing.BorderFactory.createTitledBorder("For Entire Cell"));
        entireCell.setLayout(new java.awt.GridBagLayout());

        sogNoSteinerTrees.setText("Do not make Steiner Trees (already done)");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 0);
        entireCell.add(sogNoSteinerTrees, gridBagConstraints);

        sogHorVerChoices.add(sogHorOddVerEven);
        sogHorOddVerEven.setText("Horizontal: M1,3,5...   Vertical: M2,4,6...");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 20, 0, 4);
        entireCell.add(sogHorOddVerEven, gridBagConstraints);

        sogHorVerChoices.add(sogVerOddHorEven);
        sogVerOddHorEven.setText("Vertical: M1,3,5...   Horizontal: M2,4,6...");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 20, 2, 4);
        entireCell.add(sogVerOddHorEven, gridBagConstraints);

        jLabel7.setText("Alternating Metal Layer Usage:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 0, 4);
        entireCell.add(jLabel7, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 0, 4);
        entireCell.add(sogHorVerUsage, gridBagConstraints);

        jLabel2.setText("Technology:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        gridBagConstraints.insets = new java.awt.Insets(0, 4, 2, 4);
        entireCell.add(jLabel2, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 4, 2, 4);
        entireCell.add(sogTechList, gridBagConstraints);

        jLabel18.setText("Routing bounds layer");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 8;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 4);
        entireCell.add(jLabel18, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 8;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 4);
        entireCell.add(sogRoutingBoundsLayer, gridBagConstraints);

        jPanel7.setLayout(new java.awt.GridBagLayout());

        sogContactDownToAvoidedLayers.setText("Contacts down avoided layers");
        sogContactDownToAvoidedLayers.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                sogContactDownToAvoidedLayersActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 0, 4);
        jPanel7.add(sogContactDownToAvoidedLayers, gridBagConstraints);

        sogContactUpToAvoidedLayers.setText("Contacts up avoided layers");
        sogContactUpToAvoidedLayers.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                sogContactUpToAvoidedLayersActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(0, 4, 0, 0);
        jPanel7.add(sogContactUpToAvoidedLayers, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 4, 0, 0);
        entireCell.add(jPanel7, gridBagConstraints);

        jPanel8.setLayout(new java.awt.GridBagLayout());

        jLabel19.setText("2X:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        gridBagConstraints.insets = new java.awt.Insets(0, 4, 0, 0);
        jPanel8.add(jLabel19, gridBagConstraints);

        sogContact2XInclusionPat.setColumns(10);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 4;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 0.5;
        jPanel8.add(sogContact2XInclusionPat, gridBagConstraints);

        jLabel15.setText("Contact inclusion pattern:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 0, 4);
        jPanel8.add(jLabel15, gridBagConstraints);

        sogContact1XInclusionPat.setColumns(10);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 0.5;
        jPanel8.add(sogContact1XInclusionPat, gridBagConstraints);

        jLabel23.setText("1X:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        jPanel8.add(jLabel23, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 4, 0, 0);
        entireCell.add(jPanel8, gridBagConstraints);

        jPanel9.setLayout(new java.awt.GridBagLayout());

        jLabel16.setText("Contact exclusion pattern:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        jPanel9.add(jLabel16, gridBagConstraints);

        sogContactExclusionPat.setColumns(10);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 0, 4);
        jPanel9.add(sogContactExclusionPat, gridBagConstraints);

        sogNoRotatedContacts.setText("Do not place rotated contacts");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.insets = new java.awt.Insets(0, 4, 0, 0);
        jPanel9.add(sogNoRotatedContacts, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 4, 0, 0);
        entireCell.add(jPanel9, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        jPanel5.add(entireCell, gridBagConstraints);

        individualLayers.setBorder(javax.swing.BorderFactory.createTitledBorder("Default Layer Control"));
        individualLayers.setLayout(new java.awt.GridBagLayout());

        sogArcList.setMinimumSize(new java.awt.Dimension(24, 100));
        sogArcList.setPreferredSize(new java.awt.Dimension(100, 100));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 6;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 0.5;
        individualLayers.add(sogArcList, gridBagConstraints);

        gridControl.setBorder(javax.swing.BorderFactory.createTitledBorder("Grid Control"));
        gridControl.setLayout(new java.awt.GridBagLayout());

        sogGridType.add(sogGridNone);
        sogGridNone.setText("None");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridControl.add(sogGridNone, gridBagConstraints);

        sogGridType.add(sogGridFixed);
        sogGridFixed.setText("Fixed");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridControl.add(sogGridFixed, gridBagConstraints);

        sogGridType.add(sogGridArbitrary);
        sogGridArbitrary.setText("Arbitrary");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 0;
        gridControl.add(sogGridArbitrary, gridBagConstraints);

        sogShowGrid.setText("Show");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 4;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.insets = new java.awt.Insets(0, 4, 0, 4);
        gridControl.add(sogShowGrid, gridBagConstraints);

        sogForceGrid.setText("Force");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.insets = new java.awt.Insets(0, 4, 0, 4);
        gridControl.add(sogForceGrid, gridBagConstraints);

        gridFixed.setBorder(javax.swing.BorderFactory.createTitledBorder("Fixed Grid Control"));
        gridFixed.setLayout(new java.awt.GridBagLayout());

        jLabel6.setText("Spacing:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridheight = 2;
        gridFixed.add(jLabel6, gridBagConstraints);

        sogFixedSpacing.setColumns(7);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridheight = 2;
        gridFixed.add(sogFixedSpacing, gridBagConstraints);

        jLabel8.setText("Offset:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridheight = 2;
        gridBagConstraints.insets = new java.awt.Insets(0, 20, 0, 0);
        gridFixed.add(jLabel8, gridBagConstraints);

        sogFixedOffset.setColumns(7);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 4;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridheight = 2;
        gridFixed.add(sogFixedOffset, gridBagConstraints);

        sogGridSpacingUp.setIcon(Resources.getResource(getClass(), "IconIncrement.gif"));
        sogGridSpacingUp.setToolTipText("Move all grid lines up or right");
        sogGridSpacingUp.setMaximumSize(new java.awt.Dimension(13, 7));
        sogGridSpacingUp.setMinimumSize(new java.awt.Dimension(13, 7));
        sogGridSpacingUp.setPreferredSize(new java.awt.Dimension(13, 7));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.insets = new java.awt.Insets(0, 4, 0, 4);
        gridFixed.add(sogGridSpacingUp, gridBagConstraints);

        sogGridSpacingDown.setIcon(Resources.getResource(getClass(), "IconDecrement.gif"));
        sogGridSpacingDown.setToolTipText("Move all grid lines down or left");
        sogGridSpacingDown.setMaximumSize(new java.awt.Dimension(13, 7));
        sogGridSpacingDown.setMinimumSize(new java.awt.Dimension(13, 7));
        sogGridSpacingDown.setPreferredSize(new java.awt.Dimension(13, 7));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.insets = new java.awt.Insets(0, 4, 0, 4);
        gridFixed.add(sogGridSpacingDown, gridBagConstraints);

        sogGridOffsetUp.setIcon(Resources.getResource(getClass(), "IconIncrement.gif"));
        sogGridOffsetUp.setToolTipText("Move grid line up or right");
        sogGridOffsetUp.setMaximumSize(new java.awt.Dimension(13, 7));
        sogGridOffsetUp.setMinimumSize(new java.awt.Dimension(13, 7));
        sogGridOffsetUp.setPreferredSize(new java.awt.Dimension(13, 7));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 5;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.insets = new java.awt.Insets(0, 4, 0, 4);
        gridFixed.add(sogGridOffsetUp, gridBagConstraints);

        sogGridOffsetDown.setIcon(Resources.getResource(getClass(), "IconDecrement.gif"));
        sogGridOffsetDown.setToolTipText("Move grid line down or left");
        sogGridOffsetDown.setMaximumSize(new java.awt.Dimension(13, 7));
        sogGridOffsetDown.setMinimumSize(new java.awt.Dimension(13, 7));
        sogGridOffsetDown.setPreferredSize(new java.awt.Dimension(13, 7));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 5;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.insets = new java.awt.Insets(0, 4, 0, 4);
        gridFixed.add(sogGridOffsetDown, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 5;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridControl.add(gridFixed, gridBagConstraints);

        gridArbitrary.setBorder(javax.swing.BorderFactory.createTitledBorder("Arbitrary Grid Control"));
        gridArbitrary.setLayout(new java.awt.GridBagLayout());

        sogDrawGrid.setIcon(Resources.getResource(getClass(), "IconDraw.gif"));
        sogDrawGrid.setToolTipText("Draw grid line");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 4);
        gridArbitrary.add(sogDrawGrid, gridBagConstraints);

        sogGridIndScroll.setMinimumSize(new java.awt.Dimension(50, 24));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridheight = 6;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 4, 2, 4);
        gridArbitrary.add(sogGridIndScroll, gridBagConstraints);

        sogGridAllUp.setIcon(Resources.getResource(getClass(), "IconIncrement.gif"));
        sogGridAllUp.setToolTipText("Move all grid lines up or right");
        sogGridAllUp.setMaximumSize(new java.awt.Dimension(13, 7));
        sogGridAllUp.setMinimumSize(new java.awt.Dimension(13, 7));
        sogGridAllUp.setPreferredSize(new java.awt.Dimension(13, 7));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 1, 4);
        gridArbitrary.add(sogGridAllUp, gridBagConstraints);

        sogGridAllDown.setIcon(Resources.getResource(getClass(), "IconDecrement.gif"));
        sogGridAllDown.setToolTipText("Move all grid lines down or left");
        sogGridAllDown.setMaximumSize(new java.awt.Dimension(13, 7));
        sogGridAllDown.setMinimumSize(new java.awt.Dimension(13, 7));
        sogGridAllDown.setOpaque(false);
        sogGridAllDown.setPreferredSize(new java.awt.Dimension(13, 7));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.insets = new java.awt.Insets(1, 4, 4, 4);
        gridArbitrary.add(sogGridAllDown, gridBagConstraints);

        sogGridIndUp.setIcon(Resources.getResource(getClass(), "IconIncrement.gif"));
        sogGridIndUp.setToolTipText("Move grid line up or right");
        sogGridIndUp.setMaximumSize(new java.awt.Dimension(13, 7));
        sogGridIndUp.setMinimumSize(new java.awt.Dimension(13, 7));
        sogGridIndUp.setPreferredSize(new java.awt.Dimension(13, 7));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 0, 4);
        gridArbitrary.add(sogGridIndUp, gridBagConstraints);

        sogGridIndDown.setIcon(Resources.getResource(getClass(), "IconDecrement.gif"));
        sogGridIndDown.setToolTipText("Move grid line down or left");
        sogGridIndDown.setMaximumSize(new java.awt.Dimension(13, 7));
        sogGridIndDown.setMinimumSize(new java.awt.Dimension(13, 7));
        sogGridIndDown.setPreferredSize(new java.awt.Dimension(13, 7));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.insets = new java.awt.Insets(0, 4, 2, 4);
        gridArbitrary.add(sogGridIndDown, gridBagConstraints);

        sogGridIndDelete.setIcon(Resources.getResource(getClass(), "IconDelete.gif"));
        sogGridIndDelete.setToolTipText("Delete grid line");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 4);
        gridArbitrary.add(sogGridIndDelete, gridBagConstraints);

        sogGridIndNew.setIcon(Resources.getResource(getClass(), "IconNew.gif"));
        sogGridIndNew.setToolTipText("Make new grid line");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 4);
        gridArbitrary.add(sogGridIndNew, gridBagConstraints);

        jLabel3.setText("All:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 4, 0);
        gridArbitrary.add(jLabel3, gridBagConstraints);

        jLabel4.setText("Current:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 2, 0);
        gridArbitrary.add(jLabel4, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 5;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridControl.add(gridArbitrary, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.gridwidth = 6;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        individualLayers.add(gridControl, gridBagConstraints);

        jPanel6.setLayout(new java.awt.GridBagLayout());

        sogFavorLayer.setText("Favor");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.weightx = 0.5;
        jPanel6.add(sogFavorLayer, gridBagConstraints);

        sogAvoidLayer.setText("Avoid");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        jPanel6.add(sogAvoidLayer, gridBagConstraints);

        jLabel21.setText("Maximum taper length:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.insets = new java.awt.Insets(0, 4, 0, 0);
        jPanel6.add(jLabel21, gridBagConstraints);

        sogTaperLength.setColumns(6);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 4;
        gridBagConstraints.gridy = 0;
        jPanel6.add(sogTaperLength, gridBagConstraints);

        sogTaperOnlyLayer.setText("Taper only");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 0, 4);
        jPanel6.add(sogTaperOnlyLayer, gridBagConstraints);

        jLabel5.setText("Width:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 0);
        jPanel6.add(jLabel5, gridBagConstraints);

        sogDefWidth.setColumns(6);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(2, 0, 2, 4);
        jPanel6.add(sogDefWidth, gridBagConstraints);

        jLabel20.setText("2X width threshold:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 0);
        jPanel6.add(jLabel20, gridBagConstraints);

        sog2XWidth.setColumns(6);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(2, 0, 2, 4);
        jPanel6.add(sog2XWidth, gridBagConstraints);

        jLabel9.setText("X Spacing:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 0);
        jPanel6.add(jLabel9, gridBagConstraints);

        sogDefXSpacing.setColumns(6);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(2, 0, 2, 4);
        jPanel6.add(sogDefXSpacing, gridBagConstraints);

        jLabel22.setText("Y Spacing:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 0);
        jPanel6.add(jLabel22, gridBagConstraints);

        sogDefYSpacing.setColumns(6);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(2, 0, 2, 4);
        jPanel6.add(sogDefYSpacing, gridBagConstraints);

        jLabel17.setText("Remove Geometry Layer:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        gridBagConstraints.insets = new java.awt.Insets(2, 0, 0, 0);
        jPanel6.add(jLabel17, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 1, 0, 0);
        jPanel6.add(sogRemoveLayer, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 8;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(4, 0, 0, 0);
        individualLayers.add(jPanel6, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridheight = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        jPanel5.add(individualLayers, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.weighty = 1.0;
        getContentPane().add(jPanel5, gridBagConstraints);

        pack();
    }// </editor-fold>//GEN-END:initComponents

	private void ok(java.awt.event.ActionEvent evt)//GEN-FIRST:event_ok
	{//GEN-HEADEREND:event_ok
		done();
		closeDialog(null);
	}//GEN-LAST:event_ok

	/** Closes the dialog */
	private void closeDialog(java.awt.event.WindowEvent evt)//GEN-FIRST:event_closeDialog
	{
		setVisible(false);
		dispose();

	}//GEN-LAST:event_closeDialog

    private void sogExportDataActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_sogExportDataActionPerformed
    {//GEN-HEADEREND:event_sogExportDataActionPerformed
        exportData();
    }//GEN-LAST:event_sogExportDataActionPerformed

    private void sogImportDataActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_sogImportDataActionPerformed
    {//GEN-HEADEREND:event_sogImportDataActionPerformed
        importData();
    }//GEN-LAST:event_sogImportDataActionPerformed

    private void sogContactDownToAvoidedLayersActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_sogContactDownToAvoidedLayersActionPerformed
    {//GEN-HEADEREND:event_sogContactDownToAvoidedLayersActionPerformed
    }//GEN-LAST:event_sogContactDownToAvoidedLayersActionPerformed

    private void sogContactUpToAvoidedLayersActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_sogContactUpToAvoidedLayersActionPerformed
    {//GEN-HEADEREND:event_sogContactUpToAvoidedLayersActionPerformed
    }//GEN-LAST:event_sogContactUpToAvoidedLayersActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel entireCell;
    private javax.swing.JPanel gridArbitrary;
    private javax.swing.JPanel gridControl;
    private javax.swing.JPanel gridFixed;
    private javax.swing.JPanel individualLayers;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel10;
    private javax.swing.JLabel jLabel11;
    private javax.swing.JLabel jLabel12;
    private javax.swing.JLabel jLabel13;
    private javax.swing.JLabel jLabel14;
    private javax.swing.JLabel jLabel15;
    private javax.swing.JLabel jLabel16;
    private javax.swing.JLabel jLabel17;
    private javax.swing.JLabel jLabel18;
    private javax.swing.JLabel jLabel19;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel20;
    private javax.swing.JLabel jLabel21;
    private javax.swing.JLabel jLabel22;
    private javax.swing.JLabel jLabel23;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JPanel jPanel4;
    private javax.swing.JPanel jPanel5;
    private javax.swing.JPanel jPanel6;
    private javax.swing.JPanel jPanel7;
    private javax.swing.JPanel jPanel8;
    private javax.swing.JPanel jPanel9;
    private javax.swing.JButton ok;
    private javax.swing.JTextField sog2XWidth;
    private javax.swing.JButton sogAddLayerToNet;
    private javax.swing.JButton sogAddNet;
    private javax.swing.JScrollPane sogArcList;
    private javax.swing.JCheckBox sogAvoidLayer;
    private javax.swing.JButton sogBlockageDelete;
    private javax.swing.JButton sogBlockageEdit;
    private javax.swing.JScrollPane sogBlockageList;
    private javax.swing.JButton sogBlockageNew;
    private javax.swing.JCheckBox sogBlockageShow;
    private javax.swing.JTextField sogContact1XInclusionPat;
    private javax.swing.JTextField sogContact2XInclusionPat;
    private javax.swing.JCheckBox sogContactDownToAvoidedLayers;
    private javax.swing.JTextField sogContactExclusionPat;
    private javax.swing.JCheckBox sogContactUpToAvoidedLayers;
    private javax.swing.JTextField sogDefWidth;
    private javax.swing.JTextField sogDefXSpacing;
    private javax.swing.JTextField sogDefYSpacing;
    private javax.swing.JButton sogDrawGrid;
    private javax.swing.JButton sogExportData;
    private javax.swing.JCheckBox sogFavorLayer;
    private javax.swing.JTextField sogFixedOffset;
    private javax.swing.JTextField sogFixedSpacing;
    private javax.swing.JCheckBox sogForceGrid;
    private javax.swing.JButton sogGridAllDown;
    private javax.swing.JButton sogGridAllUp;
    private javax.swing.JRadioButton sogGridArbitrary;
    private javax.swing.JRadioButton sogGridFixed;
    private javax.swing.JButton sogGridIndDelete;
    private javax.swing.JButton sogGridIndDown;
    private javax.swing.JButton sogGridIndNew;
    private javax.swing.JScrollPane sogGridIndScroll;
    private javax.swing.JButton sogGridIndUp;
    private javax.swing.JRadioButton sogGridNone;
    private javax.swing.JButton sogGridOffsetDown;
    private javax.swing.JButton sogGridOffsetUp;
    private javax.swing.JButton sogGridSpacingDown;
    private javax.swing.JButton sogGridSpacingUp;
    private javax.swing.ButtonGroup sogGridType;
    private javax.swing.JRadioButton sogHorOddVerEven;
    private javax.swing.ButtonGroup sogHorVerChoices;
    private javax.swing.JComboBox sogHorVerUsage;
    private javax.swing.JButton sogImportData;
    private javax.swing.JScrollPane sogLayerList;
    private javax.swing.JTextField sogLayerSpacingOverride;
    private javax.swing.JTextField sogLayerWidthOverride;
    private javax.swing.JScrollPane sogNetList;
    private javax.swing.JTextField sogNetName;
    private javax.swing.JCheckBox sogNoRotatedContacts;
    private javax.swing.JCheckBox sogNoSteinerTrees;
    private javax.swing.JComboBox sogRemoveLayer;
    private javax.swing.JButton sogRemoveLayerFromNet;
    private javax.swing.JButton sogRemoveNet;
    private javax.swing.JComboBox sogRoutingBoundsLayer;
    private javax.swing.JCheckBox sogShowGrid;
    private javax.swing.JTextField sogTaperLength;
    private javax.swing.JCheckBox sogTaperOnlyLayer;
    private javax.swing.JComboBox sogTechList;
    private javax.swing.JRadioButton sogVerOddHorEven;
    // End of variables declaration//GEN-END:variables

}
