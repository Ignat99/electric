/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: DisplayControl.java
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
package com.sun.electric.tool.user.dialogs.options;

import com.sun.electric.tool.Job;
import com.sun.electric.tool.user.User;
import com.sun.electric.tool.user.dialogs.EDialog;
import com.sun.electric.tool.user.dialogs.PreferencesFrame;
import com.sun.electric.tool.user.ui.EditWindow;
import com.sun.electric.tool.user.ui.LayerVisibility;
import com.sun.electric.util.TextUtils;


import javax.swing.JPanel;

/**
 * Class to handle the "Display Control" tab of the Preferences dialog.
 */
public class DisplayControlTab extends PreferencePanel
{
	private boolean resetAllOpacity = false;

	/** Creates new form Display Control */
	public DisplayControlTab(PreferencesFrame parent, boolean modal)
	{
		super(parent, modal);
		initComponents();

		// make all text fields select-all when entered
	    EDialog.makeTextFieldSelectAllOnTab(generalGreekLimit);
	    EDialog.makeTextFieldSelectAllOnTab(generalGreekCellLimit);
	    EDialog.makeTextFieldSelectAllOnTab(patternScaleLimit);
	    EDialog.makeTextFieldSelectAllOnTab(alphaBlendingOvercolorLimit);
	}

	/** return the panel to use for the user preferences. */
    @Override
	public JPanel getUserPreferencesPanel() { return general; }

	/** return the name of this preferences tab. */
    @Override
	public String getName() { return "Display Control"; }

	/**
	 * Method called at the start of the dialog.
	 * Caches current values and displays them in the General tab.
	 */
    @Override
	public void init()
	{
		// top half (miscellaneous controls)
		generalShowCursorCoordinates.setSelected(User.isShowHierarchicalCursorCoordinates());
		sideBarOnRight.setSelected(User.isSideBarOnRight());
		dimUpperWhenDownInPlace.setSelected(User.isDimUpperLevelWhenDownInPlace());
		showCellsInNewWindow.setSelected(User.isShowCellsInNewWindow());
		makeErrorHighlightingPulsate.setSelected(User.isErrorHighlightingPulsate());
		shiftWindowToErrors.setSelected(User.isShiftWindowToErrors());
		keepModelessDialogsOnTop.setSelected(User.isKeepModelessDialogsOnTop());
        measurementCadence.setSelected(User.isCadenceMeasurementStyle());
        dockMessages.setSelected(User.isDockMessagesWindow());
        generalPanningDistance.addItem("Small");
		generalPanningDistance.addItem("Medium");
		generalPanningDistance.addItem("Large");
		generalPanningDistance.setSelectedIndex(User.getPanningDistance());
		displayStyle.addItem("Operating-System default");
		displayStyle.addItem("Multiple Document (MDI)");
		displayStyle.addItem("Single Document (SDI)");
		displayStyle.setSelectedIndex(User.getDisplayStyle());

		// bottom half (display algorithm)
		int da = User.getDisplayAlgorithm();
		switch (da)
		{
			case 0:	pixelDisplay.setSelected(true);   break;
			case 1:	vectorDisplay.setSelected(true);  break;
			case 2:	layerDisplay.setSelected(true);   break;
		}
		generalUseGreekImages.setSelected(User.isUseCellGreekingImages());
		generalGreekLimit.setText(Double.toString(User.getGreekSizeLimit()));
		generalGreekCellLimit.setText(Double.toString(User.getGreekCellSizeLimit() * 100.0));
		patternScaleLimit.setText(Double.toString(User.getPatternedScaleLimit()));
		useNewBlending.setSelected(!User.isLegacyComposite());
		alphaBlendingOvercolorLimit.setText(Double.toString(User.getAlphaBlendingOvercolorLimit()));
	}

	/**
	 * Method called when the "OK" panel is hit.
	 */
    @Override
	public void term()
	{
		// top half (miscellaneous controls)
		boolean currBoolean = generalShowCursorCoordinates.isSelected();
		if (currBoolean != User.isShowHierarchicalCursorCoordinates())
			User.setShowHierarchicalCursorCoordinates(currBoolean);

		currBoolean = sideBarOnRight.isSelected();
		if (currBoolean != User.isSideBarOnRight())
			User.setSideBarOnRight(currBoolean);

		currBoolean = dimUpperWhenDownInPlace.isSelected();
		if (currBoolean != User.isDimUpperLevelWhenDownInPlace())
			User.setDimUpperLevelWhenDownInPlace(currBoolean);

		currBoolean = showCellsInNewWindow.isSelected();
		if (currBoolean != User.isShowCellsInNewWindow())
			User.setShowCellsInNewWindow(currBoolean);

		currBoolean = makeErrorHighlightingPulsate.isSelected();
		if (currBoolean != User.isErrorHighlightingPulsate())
			User.setErrorHighlightingPulsate(currBoolean);

		currBoolean = shiftWindowToErrors.isSelected();
		if (currBoolean != User.isShiftWindowToErrors())
			User.setShiftWindowToErrors(currBoolean);

		currBoolean = keepModelessDialogsOnTop.isSelected();
		if (currBoolean != User.isKeepModelessDialogsOnTop())
			User.setKeepModelessDialogsOnTop(currBoolean);

        currBoolean = measurementCadence.isSelected();
		if (currBoolean != User.isCadenceMeasurementStyle())
			User.setCadenceMeasurementStyle(currBoolean);

        currBoolean = dockMessages.isSelected();
		if (currBoolean != User.isDockMessagesWindow())
			User.setDockMessagesWindow(currBoolean);

        int currInt = generalPanningDistance.getSelectedIndex();
		if (currInt != User.getPanningDistance())
			User.setPanningDistance(currInt);

		currInt = displayStyle.getSelectedIndex();
		if (currInt != User.getDisplayStyle())
		{
			User.setDisplayStyle(currInt);
			Job.getUserInterface().showInformationMessage("Changes to the display style take effect when Electric next starts",
				"Note");
		}

		// bottom half (display algorithm)
        boolean renewDrawing = false;
		if (pixelDisplay.isSelected()) currInt = 0; else
			if (vectorDisplay.isSelected()) currInt = 1; else
				if (layerDisplay.isSelected()) currInt = 2;
		if (currInt != User.getDisplayAlgorithm()) {
			User.setDisplayAlgorithm(currInt);
            renewDrawing = true;
		}

		currBoolean = generalUseGreekImages.isSelected();
		if (currBoolean != User.isUseCellGreekingImages()) {
			User.setUseCellGreekingImages(currBoolean);
            renewDrawing = true;
        }

		double currDouble = TextUtils.atof(generalGreekLimit.getText());
		if (currDouble != User.getGreekSizeLimit()) {
			User.setGreekSizeLimit(currDouble);
            renewDrawing = true;
        }

		currDouble = TextUtils.atof(generalGreekCellLimit.getText()) / 100.0;
		if (currDouble != User.getGreekCellSizeLimit()) {
			User.setGreekCellSizeLimit(currDouble);
            renewDrawing = true;
        }

		currDouble = TextUtils.atof(patternScaleLimit.getText());
		if (currDouble != User.getPatternedScaleLimit()) {
			User.setPatternedScaleLimit(currDouble);
            renewDrawing = true;
        }

		currBoolean = !useNewBlending.isSelected();
		if (currBoolean != User.isLegacyComposite()) {
			User.setLegacyComposite(currBoolean);
			renewDrawing = true;
        }

		currDouble = TextUtils.atof(alphaBlendingOvercolorLimit.getText());
		if (currDouble != User.getAlphaBlendingOvercolorLimit()) {
			User.setAlphaBlendingOvercolorLimit(currDouble);
            renewDrawing = true;
        }

        if (renewDrawing)
			EditWindow.displayAlgorithmChanged();

		if (resetAllOpacity)
		{
            LayerVisibility.setDefaultOpacity();
//			Pref.delayPrefFlushing();
//			for(Iterator<Technology> it = Technology.getTechnologies(); it.hasNext(); )
//			{
//				Technology tech = it.next();
//				EditWindow.setDefaultOpacity(tech);
//			}
//			Pref.resumePrefFlushing();
		}
	}

	/**
	 * Method called when the factory reset is requested.
	 */
    @Override
	public void reset()
	{
		// top half (miscellaneous controls)
		if (User.isFactoryShowHierarchicalCursorCoordinates() != User.isShowHierarchicalCursorCoordinates())
			User.setShowHierarchicalCursorCoordinates(User.isFactoryShowHierarchicalCursorCoordinates());
		if (User.isFactorySideBarOnRight() != User.isSideBarOnRight())
			User.setSideBarOnRight(User.isFactorySideBarOnRight());
		if (User.isFactoryDimUpperLevelWhenDownInPlace() != User.isDimUpperLevelWhenDownInPlace())
			User.setDimUpperLevelWhenDownInPlace(User.isFactoryDimUpperLevelWhenDownInPlace());
		if (User.isFactoryShowCellsInNewWindow() != User.isShowCellsInNewWindow())
			User.setShowCellsInNewWindow(User.isFactoryShowCellsInNewWindow());
		if (User.isFactoryErrorHighlightingPulsate() != User.isErrorHighlightingPulsate())
			User.setErrorHighlightingPulsate(User.isFactoryErrorHighlightingPulsate());
		if (User.isFactoryShiftWindowToErrors() != User.isShiftWindowToErrors())
			User.setShiftWindowToErrors(User.isFactoryShiftWindowToErrors());
		if (User.isFactoryKeepModelessDialogsOnTop() != User.isKeepModelessDialogsOnTop())
			User.setKeepModelessDialogsOnTop(User.isFactoryKeepModelessDialogsOnTop());
        if (User.isFactoryCadenceMeasurementStyle() != User.isCadenceMeasurementStyle())
			User.setCadenceMeasurementStyle(User.isFactoryCadenceMeasurementStyle());
        if (User.getFactoryPanningDistance() != User.getPanningDistance())
			User.setPanningDistance(User.getFactoryPanningDistance());
		if (User.getFactoryDisplayStyle() != User.getDisplayStyle())
			User.setDisplayStyle(User.getFactoryDisplayStyle());

		// bottom half (display algorithm)
		if (User.getFactoryDisplayAlgorithm() != User.getDisplayAlgorithm())
			User.setDisplayAlgorithm(User.getFactoryDisplayAlgorithm());
		if (User.isFactoryUseCellGreekingImages() != User.isUseCellGreekingImages())
			User.setUseCellGreekingImages(User.isFactoryUseCellGreekingImages());
		if (User.getFactoryGreekSizeLimit() != User.getGreekSizeLimit())
			User.setGreekSizeLimit(User.getFactoryGreekSizeLimit());
		if (User.getFactoryGreekCellSizeLimit() != User.getGreekCellSizeLimit())
			User.setGreekCellSizeLimit(User.getFactoryGreekCellSizeLimit());
		if (User.getFactoryPatternedScaleLimit() != User.getPatternedScaleLimit())
			User.setPatternedScaleLimit(User.getFactoryPatternedScaleLimit());
		if (User.isFactoryLegacyComposite() != User.isLegacyComposite())
			User.setLegacyComposite(User.isFactoryLegacyComposite());
		if (User.getFactoryAlphaBlendingOvercolorLimit() != User.getAlphaBlendingOvercolorLimit())
			User.setAlphaBlendingOvercolorLimit(User.getFactoryAlphaBlendingOvercolorLimit());
        LayerVisibility.setDefaultOpacity();
	}

	/** This method is called from within the constructor to
	 * initialize the form.
	 * WARNING: Do NOT modify this code. The content of this method is
	 * always regenerated by the Form Editor.
	 */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        displayAlgorithm = new javax.swing.ButtonGroup();
        general = new javax.swing.JPanel();
        generalShowCursorCoordinates = new javax.swing.JCheckBox();
        sideBarOnRight = new javax.swing.JCheckBox();
        jLabel1 = new javax.swing.JLabel();
        generalPanningDistance = new javax.swing.JComboBox();
        jPanel1 = new javax.swing.JPanel();
        pixelDisplay = new javax.swing.JRadioButton();
        vectorDisplay = new javax.swing.JRadioButton();
        generalUseGreekImages = new javax.swing.JCheckBox();
        jLabel4 = new javax.swing.JLabel();
        jLabel6 = new javax.swing.JLabel();
        layerDisplay = new javax.swing.JRadioButton();
        jLabel8 = new javax.swing.JLabel();
        useNewBlending = new javax.swing.JCheckBox();
        alphaBlendingLimitLabel = new javax.swing.JLabel();
        resetOpacity = new javax.swing.JButton();
        alphaBlendingOvercolorLimit = new javax.swing.JTextField();
        patternScaleLimit = new javax.swing.JTextField();
        generalGreekCellLimit = new javax.swing.JTextField();
        generalGreekLimit = new javax.swing.JTextField();
        jLabel5 = new javax.swing.JLabel();
        jLabel7 = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        displayStyle = new javax.swing.JComboBox();
        makeErrorHighlightingPulsate = new javax.swing.JCheckBox();
        dimUpperWhenDownInPlace = new javax.swing.JCheckBox();
        showCellsInNewWindow = new javax.swing.JCheckBox();
        shiftWindowToErrors = new javax.swing.JCheckBox();
        keepModelessDialogsOnTop = new javax.swing.JCheckBox();
        measurementCadence = new javax.swing.JCheckBox();
        dockMessages = new javax.swing.JCheckBox();

        setTitle("Edit Options");
        setName(""); // NOI18N
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                closeDialog(evt);
            }
        });
        getContentPane().setLayout(new java.awt.GridBagLayout());

        general.setLayout(new java.awt.GridBagLayout());

        generalShowCursorCoordinates.setText("Show hierarchical cursor coordinates in status bar");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 2, 4);
        general.add(generalShowCursorCoordinates, gridBagConstraints);

        sideBarOnRight.setText("Side Bar defaults to the right side");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 4);
        general.add(sideBarOnRight, gridBagConstraints);

        jLabel1.setText("Panning distance:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 10;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 4);
        general.add(jLabel1, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 10;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 4);
        general.add(generalPanningDistance, gridBagConstraints);

        jPanel1.setBorder(javax.swing.BorderFactory.createTitledBorder("Display Algorithm"));
        jPanel1.setLayout(new java.awt.GridBagLayout());

        displayAlgorithm.add(pixelDisplay);
        pixelDisplay.setText("Pixel Display Algorithm (old)");
        pixelDisplay.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        jPanel1.add(pixelDisplay, gridBagConstraints);

        displayAlgorithm.add(vectorDisplay);
        vectorDisplay.setText("Vector Display Algorithm (new)");
        vectorDisplay.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        jPanel1.add(vectorDisplay, gridBagConstraints);

        generalUseGreekImages.setText("Use cell images when simplifying");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 30, 2, 4);
        jPanel1.add(generalUseGreekImages, gridBagConstraints);

        jLabel4.setText("Simplify objects smaller than:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 30, 2, 4);
        jPanel1.add(jLabel4, gridBagConstraints);

        jLabel6.setText("Do not simplify cells greater than:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 30, 4, 4);
        jPanel1.add(jLabel6, gridBagConstraints);

        displayAlgorithm.add(layerDisplay);
        layerDisplay.setText("Layer Display Algorithm (experimental)");
        layerDisplay.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        jPanel1.add(layerDisplay, gridBagConstraints);

        jLabel8.setText("Pattern scale limit");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 30, 2, 4);
        jPanel1.add(jLabel8, gridBagConstraints);

        useNewBlending.setText("Use newer blending algorithm");
        useNewBlending.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
        useNewBlending.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                useNewBlendingActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 30, 2, 4);
        jPanel1.add(useNewBlending, gridBagConstraints);

        alphaBlendingLimitLabel.setText("Alpha blending overcolor limit");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 8;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 40, 2, 4);
        jPanel1.add(alphaBlendingLimitLabel, gridBagConstraints);

        resetOpacity.setText("Reset all Layer Opacity Values");
        resetOpacity.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                resetOpacityActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 9;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 30, 4, 4);
        jPanel1.add(resetOpacity, gridBagConstraints);

        alphaBlendingOvercolorLimit.setColumns(5);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 8;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 4);
        jPanel1.add(alphaBlendingOvercolorLimit, gridBagConstraints);

        patternScaleLimit.setColumns(5);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 4);
        jPanel1.add(patternScaleLimit, gridBagConstraints);

        generalGreekCellLimit.setColumns(5);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 4, 4);
        jPanel1.add(generalGreekCellLimit, gridBagConstraints);

        generalGreekLimit.setColumns(5);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 4);
        jPanel1.add(generalGreekLimit, gridBagConstraints);

        jLabel5.setText("pixels");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 4);
        jPanel1.add(jLabel5, gridBagConstraints);

        jLabel7.setText("percent of screen");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 4, 4);
        jPanel1.add(jLabel7, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 12;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        general.add(jPanel1, gridBagConstraints);

        jLabel2.setText("Display style:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 11;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 4, 4);
        general.add(jLabel2, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 11;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 4, 4);
        general.add(displayStyle, gridBagConstraints);

        makeErrorHighlightingPulsate.setText("Make error highlighting pulsate");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 4);
        general.add(makeErrorHighlightingPulsate, gridBagConstraints);

        dimUpperWhenDownInPlace.setText("Dim upper levels of hierarchy when editing Down-In-Place");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 4);
        general.add(dimUpperWhenDownInPlace, gridBagConstraints);

        showCellsInNewWindow.setText("Show cell results in new window");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 4);
        general.add(showCellsInNewWindow, gridBagConstraints);

        shiftWindowToErrors.setText("Shift window to show errors");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 4);
        general.add(shiftWindowToErrors, gridBagConstraints);

        keepModelessDialogsOnTop.setText("Keep modeless dialogs on top");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 4);
        general.add(keepModelessDialogsOnTop, gridBagConstraints);

        measurementCadence.setText("Cadence measurement style");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 4);
        general.add(measurementCadence, gridBagConstraints);

        dockMessages.setText("Dock messages window to each edit window");
        dockMessages.setToolTipText("If checked, the messages window will appear as a new pane below each edit pane.  If unchecked there will be only one messages window and it will be free-floating.");
        dockMessages.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                dockMessagesActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 8;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 4);
        general.add(dockMessages, gridBagConstraints);

        getContentPane().add(general, new java.awt.GridBagConstraints());

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void useNewBlendingActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_useNewBlendingActionPerformed
    {//GEN-HEADEREND:event_useNewBlendingActionPerformed
		alphaBlendingLimitLabel.setEnabled(useNewBlending.isSelected());
		alphaBlendingOvercolorLimit.setEditable(useNewBlending.isSelected());
    }//GEN-LAST:event_useNewBlendingActionPerformed

    private void resetOpacityActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_resetOpacityActionPerformed
    {//GEN-HEADEREND:event_resetOpacityActionPerformed
		resetAllOpacity = true;
		resetOpacity.setEnabled(false);
    }//GEN-LAST:event_resetOpacityActionPerformed

	/** Closes the dialog */
	private void closeDialog(java.awt.event.WindowEvent evt)//GEN-FIRST:event_closeDialog
	{
		setVisible(false);
		dispose();
	}//GEN-LAST:event_closeDialog

    private void dockMessagesActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_dockMessagesActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_dockMessagesActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel alphaBlendingLimitLabel;
    private javax.swing.JTextField alphaBlendingOvercolorLimit;
    private javax.swing.JCheckBox dimUpperWhenDownInPlace;
    private javax.swing.ButtonGroup displayAlgorithm;
    private javax.swing.JComboBox displayStyle;
    private javax.swing.JCheckBox dockMessages;
    private javax.swing.JPanel general;
    private javax.swing.JTextField generalGreekCellLimit;
    private javax.swing.JTextField generalGreekLimit;
    private javax.swing.JComboBox generalPanningDistance;
    private javax.swing.JCheckBox generalShowCursorCoordinates;
    private javax.swing.JCheckBox generalUseGreekImages;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JCheckBox keepModelessDialogsOnTop;
    private javax.swing.JRadioButton layerDisplay;
    private javax.swing.JCheckBox makeErrorHighlightingPulsate;
    private javax.swing.JCheckBox measurementCadence;
    private javax.swing.JTextField patternScaleLimit;
    private javax.swing.JRadioButton pixelDisplay;
    private javax.swing.JButton resetOpacity;
    private javax.swing.JCheckBox shiftWindowToErrors;
    private javax.swing.JCheckBox showCellsInNewWindow;
    private javax.swing.JCheckBox sideBarOnRight;
    private javax.swing.JCheckBox useNewBlending;
    private javax.swing.JRadioButton vectorDisplay;
    // End of variables declaration//GEN-END:variables

}
