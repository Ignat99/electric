/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: IconTab.java
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
package com.sun.electric.tool.user.dialogs.options;

import com.sun.electric.database.EditingPreferences;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.text.TextUtils;
import com.sun.electric.tool.user.User;
import com.sun.electric.tool.user.ViewChanges;
import com.sun.electric.tool.user.dialogs.EDialog;
import com.sun.electric.tool.user.dialogs.PreferencesFrame;
import com.sun.electric.tool.user.ui.WindowFrame;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JComboBox;
import javax.swing.JPanel;

/**
 * Class to handle the "Icon" tab of the Preferences dialog.
 */
public class IconTab extends PreferencePanel
{
	/** Creates new form IconTab */
	public IconTab(PreferencesFrame parent, boolean modal)
	{
		super(parent, modal);
		initComponents();

		// make all text fields select-all when entered
	    EDialog.makeTextFieldSelectAllOnTab(iconLeadLength);
	    EDialog.makeTextFieldSelectAllOnTab(iconLeadSpacing);
	    EDialog.makeTextFieldSelectAllOnTab(iconTextSize);
	}

	/** return the panel to use for user preferences. */
	public JPanel getUserPreferencesPanel() { return icon; }

	/** return the name of this preferences tab. */
	public String getName() { return "Icon"; }

	/**
	 * Method called at the start of the dialog.
	 * Caches current values and displays them in the Icon tab.
	 */
	public void init()
	{
		// listen for the "Make Icon" button
		iconMakeIcon.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent evt) { makeIconNow(); }
		});

		// show the current cell
		Cell curCell = WindowFrame.getCurrentCell();
		if (curCell == null)
		{
			iconCurrentCell.setText("");
			iconMakeIcon.setEnabled(false);
		} else
		{
			iconCurrentCell.setText(curCell.describe(true));
			iconMakeIcon.setEnabled(true);
		}

		iconPlaceByChar.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent evt) { placementChanged(); }
		});
		iconPlaceByLoc.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent evt) { placementChanged(); }
		});
		useExactSchemLoc.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent evt) { placementChanged(); }
		});

        EditingPreferences ep = getEditingPreferences();
		// set how exports are placed (by characteristic or by location in original cell)
		int how = ep.getIconGenExportPlacement();
		if (how == 0) iconPlaceByChar.setSelected(true); else
		{
			if (how == 1)
			{
				iconPlaceByLoc.setSelected(true);
				useExactSchemLoc.setSelected(ep.isIconGenExportPlacementExact());
			}
		}
		placementChanged();

		// initialize the side for each type of export
		initSide(iconInputPos);
		iconInputPos.setSelectedIndex(User.getIconGenInputSide());
		initSide(iconOutputPos);
		iconOutputPos.setSelectedIndex(User.getIconGenOutputSide());
		initSide(iconBidirPos);
		iconBidirPos.setSelectedIndex(User.getIconGenBidirSide());
		initSide(iconPowerPos);
		iconPowerPos.setSelectedIndex(User.getIconGenPowerSide());
		initSide(iconGroundPos);
		iconGroundPos.setSelectedIndex(User.getIconGenGroundSide());
		initSide(iconClockPos);
		iconClockPos.setSelectedIndex(User.getIconGenClockSide());

		// initialize the text rotation for each type of export
		initRot(iconInputRot);
		iconInputRot.setSelectedIndex(ep.getIconGenInputRot());
		initRot(iconOutputRot);
		iconOutputRot.setSelectedIndex(ep.getIconGenOutputRot());
		initRot(iconBidirRot);
		iconBidirRot.setSelectedIndex(ep.getIconGenBidirRot());
		initRot(iconPowerRot);
		iconPowerRot.setSelectedIndex(ep.getIconGenPowerRot());
		initRot(iconGroundRot);
		iconGroundRot.setSelectedIndex(ep.getIconGenGroundRot());
		initRot(iconClockRot);
		iconClockRot.setSelectedIndex(ep.getIconGenClockRot());

		initRot(iconTopRot);
		iconTopRot.setSelectedIndex(User.getIconGenTopRot());
		initRot(iconBottomRot);
		iconBottomRot.setSelectedIndex(User.getIconGenBottomRot());
		initRot(iconLeftRot);
		iconLeftRot.setSelectedIndex(User.getIconGenLeftRot());
		initRot(iconRightRot);
		iconRightRot.setSelectedIndex(User.getIconGenRightRot());

		iconTopSkip.setSelected(User.isIconGenTopSkip());
		iconBottomSkip.setSelected(User.isIconGenBottomSkip());
		iconLeftSkip.setSelected(User.isIconGenLeftSkip());
		iconRightSkip.setSelected(User.isIconGenRightSkip());

		iconExportPos.addItem("Body");
		iconExportPos.addItem("Lead End");
		iconExportPos.addItem("Lead Middle");
		iconExportPos.setSelectedIndex(ep.getIconGenExportLocation());

		iconExportStyle.addItem("Centered");
		iconExportStyle.addItem("Inward");
		iconExportStyle.addItem("Outward");
		iconExportStyle.setSelectedIndex(ep.getIconGenExportStyle());

		iconExportTechnology.addItem("Universal");
		iconExportTechnology.addItem("Schematic");
		iconExportTechnology.setSelectedIndex(ep.getIconGenExportTech());

		iconInstancePos.addItem("Upper-right");
		iconInstancePos.addItem("Upper-left");
		iconInstancePos.addItem("Lower-right");
		iconInstancePos.addItem("Lower-left");
		iconInstancePos.addItem("No Instance");
		iconInstancePos.setSelectedIndex(ep.getIconGenInstanceLocation());

		iconDrawLeads.setSelected(ep.isIconGenDrawLeads());
		iconDrawBody.setSelected(ep.isIconGenDrawBody());
		iconTextSize.setText(TextUtils.formatDouble(ep.getIconGenBodyTextSize()));
		iconReverseOrder.setSelected(ep.isIconGenReverseExportOrder());
		iconsAlwaysDrawn.setSelected(ep.isIconsAlwaysDrawn());

		iconLeadLength.setText(TextUtils.formatDistance(ep.getIconGenLeadLength()));
		iconLeadSpacing.setText(TextUtils.formatDistance(ep.getIconGenLeadSpacing()));
	}

	private void placementChanged()
	{
		boolean charEnabled = iconPlaceByChar.isSelected();
		iconCharLabel1.setEnabled(charEnabled);
		iconCharLabel2.setEnabled(charEnabled);
		iconCharLabel3.setEnabled(charEnabled);
		iconCharLabel4.setEnabled(charEnabled);
		iconCharLabel5.setEnabled(charEnabled);
		iconCharLabel6.setEnabled(charEnabled);
		iconCharLabel8.setEnabled(charEnabled);
		iconCharLabel9.setEnabled(charEnabled);
		iconCharLabel10.setEnabled(charEnabled);
		iconInputPos.setEnabled(charEnabled);
		iconInputRot.setEnabled(charEnabled);
		iconOutputPos.setEnabled(charEnabled);
		iconOutputRot.setEnabled(charEnabled);
		iconBidirPos.setEnabled(charEnabled);
		iconBidirRot.setEnabled(charEnabled);
		iconPowerPos.setEnabled(charEnabled);
		iconPowerRot.setEnabled(charEnabled);
		iconGroundPos.setEnabled(charEnabled);
		iconGroundRot.setEnabled(charEnabled);
		iconClockPos.setEnabled(charEnabled);
		iconClockRot.setEnabled(charEnabled);
		iconReverseOrder.setEnabled(charEnabled);

		boolean exactPlacementEnabled = useExactSchemLoc.isSelected();
		iconLocLabel1.setEnabled(!charEnabled && !exactPlacementEnabled);
		iconLocLabel2.setEnabled(!charEnabled && !exactPlacementEnabled);
		iconLocLabel3.setEnabled(!charEnabled && !exactPlacementEnabled);
		iconLocLabel4.setEnabled(!charEnabled && !exactPlacementEnabled);
		iconLeftRot.setEnabled(!charEnabled && !exactPlacementEnabled);
		iconRightRot.setEnabled(!charEnabled && !exactPlacementEnabled);
		iconTopRot.setEnabled(!charEnabled && !exactPlacementEnabled);
		iconBottomRot.setEnabled(!charEnabled && !exactPlacementEnabled);
		iconTopSkip.setEnabled(!charEnabled && !exactPlacementEnabled);
		iconBottomSkip.setEnabled(!charEnabled && !exactPlacementEnabled);
		iconLeftSkip.setEnabled(!charEnabled && !exactPlacementEnabled);
		iconRightSkip.setEnabled(!charEnabled && !exactPlacementEnabled);
		iconLocLabel5.setEnabled(!charEnabled);
		iconLocLabel6.setEnabled(!charEnabled);
		iconLocLabel7.setEnabled(!charEnabled);
		useExactSchemLoc.setEnabled(!charEnabled);
	}

	private void initSide(JComboBox box)
	{
		box.addItem("Left");
		box.addItem("Right");
		box.addItem("Top");
		box.addItem("Bottom");
	}

	private void initRot(JComboBox box)
	{
		box.addItem("0");
		box.addItem("90");
		box.addItem("180");
		box.addItem("270");
	}

	private void makeIconNow()
	{
		term();
		ViewChanges.makeIconViewCommand();
	}

	/**
	 * Method called when the "OK" panel is hit.
	 * Updates any changed fields in the Icon tab.
	 */
    @Override
	public void term()
	{
		int currInt;
		boolean currBool;

		// save which side each export type goes on
		currInt = iconInputPos.getSelectedIndex();
		if (currInt != User.getIconGenInputSide()) User.setIconGenInputSide(currInt);
		currInt = iconOutputPos.getSelectedIndex();
		if (currInt != User.getIconGenOutputSide()) User.setIconGenOutputSide(currInt);
		currInt = iconBidirPos.getSelectedIndex();
		if (currInt != User.getIconGenBidirSide()) User.setIconGenBidirSide(currInt);
		currInt = iconPowerPos.getSelectedIndex();
		if (currInt != User.getIconGenPowerSide()) User.setIconGenPowerSide(currInt);
		currInt = iconGroundPos.getSelectedIndex();
		if (currInt != User.getIconGenGroundSide()) User.setIconGenGroundSide(currInt);
		currInt = iconClockPos.getSelectedIndex();
		if (currInt != User.getIconGenClockSide()) User.setIconGenClockSide(currInt);

		// save which angle each export goes on
		currInt = iconTopRot.getSelectedIndex();
		if (currInt != User.getIconGenTopRot()) User.setIconGenTopRot(currInt);
		currInt = iconBottomRot.getSelectedIndex();
		if (currInt != User.getIconGenBottomRot()) User.setIconGenBottomRot(currInt);
		currInt = iconLeftRot.getSelectedIndex();
		if (currInt != User.getIconGenLeftRot()) User.setIconGenLeftRot(currInt);
		currInt = iconRightRot.getSelectedIndex();
		if (currInt != User.getIconGenRightRot()) User.setIconGenRightRot(currInt);

		// save the skip factors for the four sides of the icon
		currBool = iconTopSkip.isSelected();
		if (currBool != User.isIconGenTopSkip()) User.setIconGenTopSkip(currBool);
		currBool = iconBottomSkip.isSelected();
		if (currBool != User.isIconGenBottomSkip()) User.setIconGenBottomSkip(currBool);
		currBool = iconLeftSkip.isSelected();
		if (currBool != User.isIconGenLeftSkip()) User.setIconGenLeftSkip(currBool);
		currBool = iconRightSkip.isSelected();
		if (currBool != User.isIconGenRightSkip()) User.setIconGenRightSkip(currBool);

		EditingPreferences ep = getEditingPreferences()
                .withIconGenExportPlacement(iconPlaceByLoc.isSelected() ? 1 : 0)
                .withIconGenExportPlacementExact(useExactSchemLoc.isSelected())
                .withIconGenDrawLeads(iconDrawLeads.isSelected())
                .withIconsAlwaysDrawn(iconsAlwaysDrawn.isSelected())
                .withIconGenDrawBody(iconDrawBody.isSelected())
                .withIconGenReverseExportOrder(iconReverseOrder.isSelected())
                .withIconGenBodyTextSize(TextUtils.atof(iconTextSize.getText()))
                .withIconGenExportLocation(iconExportPos.getSelectedIndex())
                .withIconGenExportStyle(iconExportStyle.getSelectedIndex())
                .withIconGenExportTech(iconExportTechnology.getSelectedIndex())
                .withIconGenInstanceLocation(iconInstancePos.getSelectedIndex())
                .withIconGenInputRot(iconInputRot.getSelectedIndex())
                .withIconGenOutputRot(iconOutputRot.getSelectedIndex())
                .withIconGenBidirRot(iconBidirRot.getSelectedIndex())
                .withIconGenPowerRot(iconPowerRot.getSelectedIndex())
                .withIconGenGroundRot(iconGroundRot.getSelectedIndex())
                .withIconGenClockRot(iconClockRot.getSelectedIndex())
                .withIconGenLeadLength(TextUtils.atofDistance(iconLeadLength.getText()))
                .withIconGenLeadSpacing(TextUtils.atofDistance(iconLeadSpacing.getText()));

        setEditingPreferences(ep);
	}

	/**
	 * Method called when the factory reset is requested.
	 */
    @Override
	public void reset()
	{
		if (User.getFactoryIconGenInputSide() != User.getIconGenInputSide())
			User.setIconGenInputSide(User.getFactoryIconGenInputSide());
		if (User.getFactoryIconGenOutputSide() != User.getIconGenOutputSide())
			User.setIconGenOutputSide(User.getFactoryIconGenOutputSide());
		if (User.getFactoryIconGenBidirSide() != User.getIconGenBidirSide())
			User.setIconGenBidirSide(User.getFactoryIconGenBidirSide());
		if (User.getFactoryIconGenPowerSide() != User.getIconGenPowerSide())
			User.setIconGenPowerSide(User.getFactoryIconGenPowerSide());
		if (User.getFactoryIconGenGroundSide() != User.getIconGenGroundSide())
			User.setIconGenGroundSide(User.getFactoryIconGenGroundSide());
		if (User.getFactoryIconGenClockSide() != User.getIconGenClockSide())
			User.setIconGenClockSide(User.getFactoryIconGenClockSide());

		if (User.getFactoryIconGenTopRot() != User.getIconGenTopRot())
			User.setIconGenTopRot(User.getFactoryIconGenTopRot());
		if (User.getFactoryIconGenBottomRot() != User.getIconGenBottomRot())
			User.setIconGenBottomRot(User.getFactoryIconGenBottomRot());
		if (User.getFactoryIconGenLeftRot() != User.getIconGenLeftRot())
			User.setIconGenLeftRot(User.getFactoryIconGenLeftRot());
		if (User.getFactoryIconGenRightRot() != User.getIconGenRightRot())
			User.setIconGenRightRot(User.getFactoryIconGenRightRot());

		if (User.isFactoryIconGenTopSkip() != User.isIconGenTopSkip())
			User.setIconGenTopSkip(User.isFactoryIconGenTopSkip());
		if (User.isFactoryIconGenBottomSkip() != User.isIconGenBottomSkip())
			User.setIconGenBottomSkip(User.isFactoryIconGenBottomSkip());
		if (User.isFactoryIconGenLeftSkip() != User.isIconGenLeftSkip())
			User.setIconGenLeftSkip(User.isFactoryIconGenLeftSkip());
		if (User.isFactoryIconGenRightSkip() != User.isIconGenRightSkip())
			User.setIconGenRightSkip(User.isFactoryIconGenRightSkip());

		setEditingPreferences(getEditingPreferences().withIconGenReset());
	}

	/** This method is called from within the constructor to
	 * initialize the form.
	 * WARNING: Do NOT modify this code. The content of this method is
	 * always regenerated by the Form Editor.
	 */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        exportPlace = new javax.swing.ButtonGroup();
        icon = new javax.swing.JPanel();
        jLabel48 = new javax.swing.JLabel();
        jLabel54 = new javax.swing.JLabel();
        iconCurrentCell = new javax.swing.JLabel();
        iconMakeIcon = new javax.swing.JButton();
        jSeparator11 = new javax.swing.JSeparator();
        iconInstancePos = new javax.swing.JComboBox();
        jLabel30 = new javax.swing.JLabel();
        jLabel31 = new javax.swing.JLabel();
        iconExportTechnology = new javax.swing.JComboBox();
        jPanel6 = new javax.swing.JPanel();
        iconDrawLeads = new javax.swing.JCheckBox();
        iconDrawBody = new javax.swing.JCheckBox();
        jLabel26 = new javax.swing.JLabel();
        iconLeadLength = new javax.swing.JTextField();
        iconLeadSpacing = new javax.swing.JTextField();
        jLabel27 = new javax.swing.JLabel();
        jLabel28 = new javax.swing.JLabel();
        iconExportPos = new javax.swing.JComboBox();
        jLabel29 = new javax.swing.JLabel();
        iconExportStyle = new javax.swing.JComboBox();
        iconsAlwaysDrawn = new javax.swing.JCheckBox();
        jLabel11 = new javax.swing.JLabel();
        iconTextSize = new javax.swing.JTextField();
        jPanel1 = new javax.swing.JPanel();
        iconPlaceByChar = new javax.swing.JRadioButton();
        iconPlaceByLoc = new javax.swing.JRadioButton();
        iconCharLabel1 = new javax.swing.JLabel();
        iconInputPos = new javax.swing.JComboBox();
        iconCharLabel4 = new javax.swing.JLabel();
        iconPowerPos = new javax.swing.JComboBox();
        iconGroundPos = new javax.swing.JComboBox();
        iconCharLabel5 = new javax.swing.JLabel();
        iconOutputPos = new javax.swing.JComboBox();
        iconCharLabel2 = new javax.swing.JLabel();
        iconCharLabel3 = new javax.swing.JLabel();
        iconBidirPos = new javax.swing.JComboBox();
        iconCharLabel6 = new javax.swing.JLabel();
        iconClockPos = new javax.swing.JComboBox();
        iconInputRot = new javax.swing.JComboBox();
        iconOutputRot = new javax.swing.JComboBox();
        iconBidirRot = new javax.swing.JComboBox();
        iconPowerRot = new javax.swing.JComboBox();
        iconGroundRot = new javax.swing.JComboBox();
        iconClockRot = new javax.swing.JComboBox();
        jSeparator1 = new javax.swing.JSeparator();
        iconLocLabel1 = new javax.swing.JLabel();
        iconLocLabel2 = new javax.swing.JLabel();
        iconLocLabel3 = new javax.swing.JLabel();
        iconLocLabel4 = new javax.swing.JLabel();
        iconTopRot = new javax.swing.JComboBox();
        iconBottomRot = new javax.swing.JComboBox();
        iconLeftRot = new javax.swing.JComboBox();
        iconRightRot = new javax.swing.JComboBox();
        iconReverseOrder = new javax.swing.JCheckBox();
        useExactSchemLoc = new javax.swing.JCheckBox();
        iconLocLabel5 = new javax.swing.JLabel();
        iconLocLabel6 = new javax.swing.JLabel();
        iconLocLabel7 = new javax.swing.JLabel();
        iconTopSkip = new javax.swing.JCheckBox();
        iconBottomSkip = new javax.swing.JCheckBox();
        iconLeftSkip = new javax.swing.JCheckBox();
        iconRightSkip = new javax.swing.JCheckBox();
        iconCharLabel8 = new javax.swing.JLabel();
        iconCharLabel9 = new javax.swing.JLabel();
        iconCharLabel10 = new javax.swing.JLabel();

        setTitle("Edit Options");
        setName(""); // NOI18N
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                closeDialog(evt);
            }
        });
        getContentPane().setLayout(new java.awt.GridBagLayout());

        icon.setLayout(new java.awt.GridBagLayout());

        jLabel48.setText("Rules for automatic Icon generation");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 4, 4);
        icon.add(jLabel48, gridBagConstraints);

        jLabel54.setText("Current cell:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 4, 4);
        icon.add(jLabel54, gridBagConstraints);

        iconCurrentCell.setText(" ");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        icon.add(iconCurrentCell, gridBagConstraints);

        iconMakeIcon.setText("Make Icon");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 4, 4);
        icon.add(iconMakeIcon, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.gridwidth = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        icon.add(jSeparator11, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(2, 2, 4, 4);
        icon.add(iconInstancePos, gridBagConstraints);

        jLabel30.setText("Export technology:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 2, 2);
        icon.add(jLabel30, gridBagConstraints);

        jLabel31.setText("Instance location:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 4, 2);
        icon.add(jLabel31, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(4, 2, 2, 4);
        icon.add(iconExportTechnology, gridBagConstraints);

        jPanel6.setBorder(javax.swing.BorderFactory.createTitledBorder("Body and Leads"));
        jPanel6.setLayout(new java.awt.GridBagLayout());

        iconDrawLeads.setText("Draw leads");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 4);
        jPanel6.add(iconDrawLeads, gridBagConstraints);

        iconDrawBody.setText("Draw body");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 5;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 4, 4);
        jPanel6.add(iconDrawBody, gridBagConstraints);

        jLabel26.setText("Lead length:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 2);
        jPanel6.add(jLabel26, gridBagConstraints);

        iconLeadLength.setColumns(8);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(2, 2, 2, 4);
        jPanel6.add(iconLeadLength, gridBagConstraints);

        iconLeadSpacing.setColumns(8);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 4;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(2, 2, 2, 4);
        jPanel6.add(iconLeadSpacing, gridBagConstraints);

        jLabel27.setText("Lead spacing:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 2);
        jPanel6.add(jLabel27, gridBagConstraints);

        jLabel28.setText("Export location:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 1, 4);
        jPanel6.add(jLabel28, gridBagConstraints);

        iconExportPos.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                iconExportPosActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 4;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 1, 4);
        jPanel6.add(iconExportPos, gridBagConstraints);

        jLabel29.setText("Export style:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(1, 4, 4, 4);
        jPanel6.add(jLabel29, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 4;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 4;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(1, 4, 4, 4);
        jPanel6.add(iconExportStyle, gridBagConstraints);

        iconsAlwaysDrawn.setText("Make exports \"Always Drawn\"");
        iconsAlwaysDrawn.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
        iconsAlwaysDrawn.setMargin(new java.awt.Insets(0, 0, 0, 0));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 4, 4);
        jPanel6.add(iconsAlwaysDrawn, gridBagConstraints);

        jLabel11.setText("Text size:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 4, 2);
        jPanel6.add(jLabel11, gridBagConstraints);

        iconTextSize.setColumns(8);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(2, 2, 4, 4);
        jPanel6.add(iconTextSize, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        icon.add(jPanel6, gridBagConstraints);

        jPanel1.setBorder(javax.swing.BorderFactory.createTitledBorder("Export location"));
        jPanel1.setLayout(new java.awt.GridBagLayout());

        exportPlace.add(iconPlaceByChar);
        iconPlaceByChar.setText("Place by Characteristic");
        iconPlaceByChar.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
        iconPlaceByChar.setMargin(new java.awt.Insets(0, 0, 0, 0));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 4, 4);
        jPanel1.add(iconPlaceByChar, gridBagConstraints);

        exportPlace.add(iconPlaceByLoc);
        iconPlaceByLoc.setText("Place by Location in Cell");
        iconPlaceByLoc.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
        iconPlaceByLoc.setMargin(new java.awt.Insets(0, 0, 0, 0));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 4;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 4, 4);
        jPanel1.add(iconPlaceByLoc, gridBagConstraints);

        iconCharLabel1.setText("Input:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 1, 2);
        jPanel1.add(iconCharLabel1, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 0.6;
        gridBagConstraints.insets = new java.awt.Insets(4, 2, 1, 4);
        jPanel1.add(iconInputPos, gridBagConstraints);

        iconCharLabel4.setText("Power:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(1, 4, 1, 2);
        jPanel1.add(iconCharLabel4, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 0.6;
        gridBagConstraints.insets = new java.awt.Insets(1, 2, 1, 4);
        jPanel1.add(iconPowerPos, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 0.6;
        gridBagConstraints.insets = new java.awt.Insets(1, 2, 1, 4);
        jPanel1.add(iconGroundPos, gridBagConstraints);

        iconCharLabel5.setText("Ground:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(1, 4, 1, 2);
        jPanel1.add(iconCharLabel5, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 0.6;
        gridBagConstraints.insets = new java.awt.Insets(1, 2, 1, 4);
        jPanel1.add(iconOutputPos, gridBagConstraints);

        iconCharLabel2.setText("Output:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(1, 4, 1, 2);
        jPanel1.add(iconCharLabel2, gridBagConstraints);

        iconCharLabel3.setText("Bidirectional:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(1, 4, 1, 2);
        jPanel1.add(iconCharLabel3, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 0.6;
        gridBagConstraints.insets = new java.awt.Insets(1, 2, 1, 4);
        jPanel1.add(iconBidirPos, gridBagConstraints);

        iconCharLabel6.setText("Clock:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(1, 4, 1, 2);
        jPanel1.add(iconCharLabel6, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 0.6;
        gridBagConstraints.insets = new java.awt.Insets(1, 2, 1, 4);
        jPanel1.add(iconClockPos, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 0.2;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 1, 4);
        jPanel1.add(iconInputRot, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 0.2;
        gridBagConstraints.insets = new java.awt.Insets(1, 4, 1, 4);
        jPanel1.add(iconOutputRot, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 0.2;
        gridBagConstraints.insets = new java.awt.Insets(1, 4, 1, 4);
        jPanel1.add(iconBidirRot, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 0.2;
        gridBagConstraints.insets = new java.awt.Insets(1, 4, 1, 4);
        jPanel1.add(iconPowerRot, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 0.2;
        gridBagConstraints.insets = new java.awt.Insets(1, 4, 1, 4);
        jPanel1.add(iconGroundRot, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 0.2;
        gridBagConstraints.insets = new java.awt.Insets(1, 4, 1, 4);
        jPanel1.add(iconClockRot, gridBagConstraints);

        jSeparator1.setOrientation(javax.swing.SwingConstants.VERTICAL);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridheight = 9;
        gridBagConstraints.fill = java.awt.GridBagConstraints.VERTICAL;
        gridBagConstraints.insets = new java.awt.Insets(0, 4, 0, 4);
        jPanel1.add(jSeparator1, gridBagConstraints);

        iconLocLabel1.setText("Top:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 4;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 1, 2);
        jPanel1.add(iconLocLabel1, gridBagConstraints);

        iconLocLabel2.setText("Bottom:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 4;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(1, 4, 1, 2);
        jPanel1.add(iconLocLabel2, gridBagConstraints);

        iconLocLabel3.setText("Left:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 4;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(1, 4, 1, 2);
        jPanel1.add(iconLocLabel3, gridBagConstraints);

        iconLocLabel4.setText("Right:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 4;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(1, 4, 1, 2);
        jPanel1.add(iconLocLabel4, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 5;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 0.2;
        gridBagConstraints.insets = new java.awt.Insets(4, 2, 1, 4);
        jPanel1.add(iconTopRot, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 5;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 0.2;
        gridBagConstraints.insets = new java.awt.Insets(1, 2, 1, 4);
        jPanel1.add(iconBottomRot, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 5;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 0.2;
        gridBagConstraints.insets = new java.awt.Insets(1, 2, 1, 4);
        jPanel1.add(iconLeftRot, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 5;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 0.2;
        gridBagConstraints.insets = new java.awt.Insets(1, 2, 1, 4);
        jPanel1.add(iconRightRot, gridBagConstraints);

        iconReverseOrder.setText("Place Exports in reverse alphabetical order");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 8;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 2, 4);
        jPanel1.add(iconReverseOrder, gridBagConstraints);

        useExactSchemLoc.setText("Use original cell location");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 4;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 1, 4);
        jPanel1.add(useExactSchemLoc, gridBagConstraints);

        iconLocLabel5.setFont(new java.awt.Font("Tahoma", 1, 11));
        iconLocLabel5.setText("Side");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 4;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 1, 4);
        jPanel1.add(iconLocLabel5, gridBagConstraints);

        iconLocLabel6.setFont(new java.awt.Font("Tahoma", 1, 11));
        iconLocLabel6.setText("Text Rotated");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 5;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 1, 4);
        jPanel1.add(iconLocLabel6, gridBagConstraints);

        iconLocLabel7.setFont(new java.awt.Font("Tahoma", 1, 11));
        iconLocLabel7.setText("Skip");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 6;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 1, 4);
        jPanel1.add(iconLocLabel7, gridBagConstraints);

        iconTopSkip.setText("Skip");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 6;
        gridBagConstraints.gridy = 2;
        jPanel1.add(iconTopSkip, gridBagConstraints);

        iconBottomSkip.setText("Skip");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 6;
        gridBagConstraints.gridy = 3;
        jPanel1.add(iconBottomSkip, gridBagConstraints);

        iconLeftSkip.setText("Skip");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 6;
        gridBagConstraints.gridy = 4;
        jPanel1.add(iconLeftSkip, gridBagConstraints);

        iconRightSkip.setText("Skip");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 6;
        gridBagConstraints.gridy = 5;
        jPanel1.add(iconRightSkip, gridBagConstraints);

        iconCharLabel8.setFont(new java.awt.Font("Tahoma", 1, 11));
        iconCharLabel8.setText("Side");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.insets = new java.awt.Insets(4, 2, 1, 4);
        jPanel1.add(iconCharLabel8, gridBagConstraints);

        iconCharLabel9.setFont(new java.awt.Font("Tahoma", 1, 11));
        iconCharLabel9.setText("Text Rotated");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 1, 4);
        jPanel1.add(iconCharLabel9, gridBagConstraints);

        iconCharLabel10.setFont(new java.awt.Font("Tahoma", 1, 11));
        iconCharLabel10.setText("Characteristic");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 1, 2);
        jPanel1.add(iconCharLabel10, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        icon.add(jPanel1, gridBagConstraints);

        getContentPane().add(icon, new java.awt.GridBagConstraints());

        pack();
    }// </editor-fold>//GEN-END:initComponents

	/** Closes the dialog */
	private void closeDialog(java.awt.event.WindowEvent evt)//GEN-FIRST:event_closeDialog
	{
		setVisible(false);
		dispose();
	}//GEN-LAST:event_closeDialog

    private void iconExportPosActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_iconExportPosActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_iconExportPosActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.ButtonGroup exportPlace;
    private javax.swing.JPanel icon;
    private javax.swing.JComboBox iconBidirPos;
    private javax.swing.JComboBox iconBidirRot;
    private javax.swing.JComboBox iconBottomRot;
    private javax.swing.JCheckBox iconBottomSkip;
    private javax.swing.JLabel iconCharLabel1;
    private javax.swing.JLabel iconCharLabel10;
    private javax.swing.JLabel iconCharLabel2;
    private javax.swing.JLabel iconCharLabel3;
    private javax.swing.JLabel iconCharLabel4;
    private javax.swing.JLabel iconCharLabel5;
    private javax.swing.JLabel iconCharLabel6;
    private javax.swing.JLabel iconCharLabel8;
    private javax.swing.JLabel iconCharLabel9;
    private javax.swing.JComboBox iconClockPos;
    private javax.swing.JComboBox iconClockRot;
    private javax.swing.JLabel iconCurrentCell;
    private javax.swing.JCheckBox iconDrawBody;
    private javax.swing.JCheckBox iconDrawLeads;
    private javax.swing.JComboBox iconExportPos;
    private javax.swing.JComboBox iconExportStyle;
    private javax.swing.JComboBox iconExportTechnology;
    private javax.swing.JComboBox iconGroundPos;
    private javax.swing.JComboBox iconGroundRot;
    private javax.swing.JComboBox iconInputPos;
    private javax.swing.JComboBox iconInputRot;
    private javax.swing.JComboBox iconInstancePos;
    private javax.swing.JTextField iconLeadLength;
    private javax.swing.JTextField iconLeadSpacing;
    private javax.swing.JComboBox iconLeftRot;
    private javax.swing.JCheckBox iconLeftSkip;
    private javax.swing.JLabel iconLocLabel1;
    private javax.swing.JLabel iconLocLabel2;
    private javax.swing.JLabel iconLocLabel3;
    private javax.swing.JLabel iconLocLabel4;
    private javax.swing.JLabel iconLocLabel5;
    private javax.swing.JLabel iconLocLabel6;
    private javax.swing.JLabel iconLocLabel7;
    private javax.swing.JButton iconMakeIcon;
    private javax.swing.JComboBox iconOutputPos;
    private javax.swing.JComboBox iconOutputRot;
    private javax.swing.JRadioButton iconPlaceByChar;
    private javax.swing.JRadioButton iconPlaceByLoc;
    private javax.swing.JComboBox iconPowerPos;
    private javax.swing.JComboBox iconPowerRot;
    private javax.swing.JCheckBox iconReverseOrder;
    private javax.swing.JComboBox iconRightRot;
    private javax.swing.JCheckBox iconRightSkip;
    private javax.swing.JTextField iconTextSize;
    private javax.swing.JComboBox iconTopRot;
    private javax.swing.JCheckBox iconTopSkip;
    private javax.swing.JCheckBox iconsAlwaysDrawn;
    private javax.swing.JLabel jLabel11;
    private javax.swing.JLabel jLabel26;
    private javax.swing.JLabel jLabel27;
    private javax.swing.JLabel jLabel28;
    private javax.swing.JLabel jLabel29;
    private javax.swing.JLabel jLabel30;
    private javax.swing.JLabel jLabel31;
    private javax.swing.JLabel jLabel48;
    private javax.swing.JLabel jLabel54;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel6;
    private javax.swing.JSeparator jSeparator1;
    private javax.swing.JSeparator jSeparator11;
    private javax.swing.JCheckBox useExactSchemLoc;
    // End of variables declaration//GEN-END:variables

}
