/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: FillCellGenPanel.java
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
package com.sun.electric.tool.generator.layout.fillCell;

import com.sun.electric.tool.user.dialogs.FillGenDialog;
import com.sun.electric.tool.generator.layout.fill.FillGenConfig;
import com.sun.electric.tool.Job;

import com.sun.electric.tool.extract.LayerCoverageTool;
import com.sun.electric.util.TextUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

/**
 * User: gg151869
 * Date: Sep 15, 2006
 */
public class FillCelllGenPanel extends JPanel {

    private javax.swing.JRadioButton cellButton;
    private javax.swing.JComboBox fillTypeComboBox;
    private javax.swing.JComboBox masterComboBox;
    private javax.swing.JTextField gapTextField;
    private javax.swing.JTextField levelTextField;
    private javax.swing.JCheckBox aroundButton;
    private javax.swing.JComboBox routerTypeComboBox;
    private javax.swing.JLabel gapLabel;
    private FillGenDialog parentDialog;

    private enum CellTypeEnum {CREATE,USE}

    public FillCelllGenPanel(FillGenDialog dialog, JPanel floorplanPanel, ButtonGroup topGroup, JButton okButton,
                             JRadioButton templateButton)
    {
        parentDialog = dialog;
        initComponents(floorplanPanel, topGroup);

        // Add listeners
        okButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                okButtonActionPerformed();
            }
        });

        templateButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                optionActionPerformed(evt);
            }
        });

        FillCellTool.FillCellMode mode = FillCellTool.getFillCellMode();
        fillTypeComboBox.setModel(new DefaultComboBoxModel(FillCellTool.FillCellMode.values()));
        fillTypeComboBox.setSelectedItem(mode);

        FillGenConfig.FillGenType routerMode = FillCellTool.getFillRouterMode();
        routerTypeComboBox.setModel(new DefaultComboBoxModel(FillGenConfig.FillGenType.values()));
        routerTypeComboBox.setSelectedItem(routerMode);

        // master
        boolean createMaster = FillCellTool.isFillCellCreateMasterOn();
        masterComboBox.setModel(new DefaultComboBoxModel(CellTypeEnum.values()));
        if (createMaster)
            masterComboBox.setSelectedItem(CellTypeEnum.CREATE);
        else
            masterComboBox.setSelectedItem(CellTypeEnum.USE);

        optionActionPerformed(null);
    }

    private void okButtonActionPerformed() {
        boolean binary = fillTypeComboBox.getSelectedItem() == FillCellTool.FillCellMode.BINARY;
        FillGenConfig.FillGenType routerType = (FillGenConfig.FillGenType)routerTypeComboBox.getSelectedItem();
        double gap = TextUtils.atof(gapTextField.getText());
        FillGenConfig config = parentDialog.okButtonClick(isFlatSelected(), isCreateOptionSelected(), binary, aroundButton.isSelected(),
                gap, routerType, TextUtils.atoi(levelTextField.getText()));
        if (config != null)
            new FillCellGenJob(Job.getUserInterface().getCurrentCell(), config, false, new LayerCoverageTool.LayerCoveragePreferences(false));
        // Store preferences
        FillCellTool.FillCellMode mode = (FillCellTool.FillCellMode)fillTypeComboBox.getSelectedItem();
        FillCellTool.setFillCellMode(mode);
        FillCellTool.setFillRouterMode(routerType);
        FillCellTool.setFillCellCreateMasterOn(isCreateOptionSelected());
    }

    private void initComponents(JPanel floorplanPanel, ButtonGroup topGroup)
    {
        setBorder(javax.swing.BorderFactory.createTitledBorder("Fill Information"));
        setLayout(new java.awt.GridBagLayout());

        java.awt.GridBagConstraints gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 4;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
        floorplanPanel.add(this, gridBagConstraints);

        cellButton = new javax.swing.JRadioButton();
        cellButton.setText("Fill Cell");
        cellButton.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
        cellButton.setMargin(new java.awt.Insets(0, 0, 0, 0));
        cellButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                optionActionPerformed(evt);
            }
        });
        topGroup.add(cellButton);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 4;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        floorplanPanel.add(cellButton, gridBagConstraints);

        javax.swing.JLabel fillTypeLabel = new javax.swing.JLabel();
        fillTypeLabel.setText("Type");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        add(fillTypeLabel, gridBagConstraints);

        javax.swing.JLabel masterLabel = new javax.swing.JLabel();
        masterLabel.setText("Master");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 2);
        add(masterLabel, gridBagConstraints);

        masterComboBox = new javax.swing.JComboBox();
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(4, 2, 4, 4);
        add(masterComboBox, gridBagConstraints);
        masterComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                generalSetup();
            }
        });

        fillTypeComboBox = new javax.swing.JComboBox();
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        add(fillTypeComboBox, gridBagConstraints);
        fillTypeComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                 generalSetup();
            }
        });

        gapLabel = new javax.swing.JLabel();
        gapLabel.setText("Overlap");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 2);
        add(gapLabel, gridBagConstraints);

        gapTextField = new javax.swing.JTextField();
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(4, 2, 4, 4);
        add(gapTextField, gridBagConstraints);

        javax.swing.JLabel levelLabel = new javax.swing.JLabel();
        levelLabel.setText("Level");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 2);
        add(levelLabel, gridBagConstraints);

        levelTextField = new javax.swing.JTextField();
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(4, 2, 4, 4);
        add(levelTextField, gridBagConstraints);

        aroundButton = new javax.swing.JCheckBox();
        aroundButton.setText("Only Around");
        aroundButton.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
        aroundButton.setMargin(new java.awt.Insets(0, 0, 0, 0));
        aroundButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                aroundButtonActionPerformed(evt);
            }
        });

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        add(aroundButton, gridBagConstraints);

        javax.swing.JLabel routerLabel = new javax.swing.JLabel();
        routerLabel.setText("Router");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        add(routerLabel, gridBagConstraints);

        routerTypeComboBox = new javax.swing.JComboBox();
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        add(routerTypeComboBox, gridBagConstraints);
        routerTypeComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                 generalSetup();
            }
        });
    }

    private boolean isFlatSelected()
    {
        return fillTypeComboBox.getSelectedItem() == FillCellTool.FillCellMode.FLAT;
    }

    private boolean isCreateOptionSelected()
    {
        return masterComboBox.getSelectedItem() == CellTypeEnum.CREATE;
    }

    private void optionActionPerformed(ActionEvent evt) {
        boolean isCellSelected = cellButton.isSelected();
        setEnabledInHierarchy(this, isCellSelected);
        // Calls master select setting
        parentDialog.optionAction(isFlatSelected(), isCreateOptionSelected(), isCellSelected);
    }

    private void aroundButtonActionPerformed(java.awt.event.ActionEvent evt)
    {
        gapTextField.setEnabled(aroundButton.isSelected());
        gapLabel.setEnabled(aroundButton.isSelected());
    }

    private static void setEnabledInHierarchy(Container c, boolean value)
    {
        c.setEnabled(value);
        for (int i = 0; i < c.getComponentCount(); i++)
        {
            Component co = c.getComponent(i);
            co.setEnabled(value);
            if (co instanceof Container)
               setEnabledInHierarchy((Container)co, value);
        }
    }

    private void generalSetup()
    {
        boolean flatSelected = isFlatSelected();
        setEnabledInHierarchy(masterComboBox, !flatSelected);

        parentDialog.generalSetup(flatSelected, isCreateOptionSelected());

        // setting the around toggles
        aroundButtonActionPerformed(null);
    }
}
